package com.mediaflow.universal.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.farimarwat.commons.YoutubeDLRequest
import com.farimarwat.commons.YoutubeDLResponse
import com.farimarwat.library.YoutubeDL
import com.mediaflow.universal.core.ErrorKind
import com.mediaflow.universal.data.AppDatabase
import com.mediaflow.universal.data.DownloadEntity
import com.mediaflow.universal.data.DownloadStatus
import com.mediaflow.universal.data.UserPreferences
import com.mediaflow.universal.extraction.engines.YtDlpEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Runs on-device yt-dlp for formats that need extraction, merging or audio conversion. */
class YtDlpDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val dao = AppDatabase.get(applicationContext).downloadDao()
    private val prefs = UserPreferences(applicationContext)
    private val fileStore = FileStore(applicationContext)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val id = inputData.getString(DownloadWork.KEY_ID).orEmpty()
    private val notificationId = id.hashCode().takeIf { it != Int.MIN_VALUE } ?: 1001

    @Volatile
    private var processId: String? = null
    @Volatile
    private var lastBytes = 0L
    @Volatile
    private var lastTick = 0L
    @Volatile
    private var lastNotification = 0L

    override suspend fun doWork(): Result = DownloadWork.withSlot { runDownload() }

    /** Keeps the process alive while this download runs, even after the user leaves the app. */
    override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(
        notificationId,
        DownloadNotifications.foregroundNotification(applicationContext),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    )

    private suspend fun runDownload(): Result {
        if (id.isBlank()) return Result.failure()
        DownloadNotifications.createChannel(applicationContext)
        val entity = dao.findById(id) ?: return Result.failure()
        if (entity.state !in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)) return Result.success()

        val preferences = prefs.flow.first()
        if (preferences.wifiOnly && !isWifi()) {
            dao.updateStatus(id, DownloadStatus.PAUSED.name)
            return Result.success()
        }
        if (!YtDlpEngine.ensureReady(applicationContext)) {
            fail(entity, ErrorKind.DOWNLOAD, "yt-dlp failed to initialise")
            return Result.success()
        }

        runCatching { setForegroundAsync(getForegroundInfo()) }
            .onFailure { Timber.w(it, "Could not start background download service") }
        dao.updateStatus(id, DownloadStatus.DOWNLOADING.name)
        DownloadNotifications.postProgress(applicationContext, notificationId, entity.title, 0, true)

        val outputTemplate = File(fileStore.workingDir(), "$id.%(ext)s").absolutePath
        val request = YoutubeDLRequest(entity.sourceUrl)
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--no-mtime")
            .addOption("--merge-output-format", "mp4/mkv")
            .addOption("-o", outputTemplate)
            // Be patient with flaky mobile DNS/connections instead of giving up after 10
            // rapid retries. One fragment at a time avoids exhausting the network stack.
            .addOption("--retries", "3")
            .addOption("--fragment-retries", "3")
            .addOption("--socket-timeout", "20")
            .addOption("--concurrent-fragments", "1")
            .addCommands(entity.downloadKey.split(' ').filter { it.isNotBlank() })
        val retrying = runAttemptCount > 0
        if (inputData.getBoolean(DownloadWork.KEY_RESUME, false) || retrying) {
            request.addOption("--continue")
        } else {
            fileStore.deleteWorkingFiles(id)
        }

        try {
            var result = runYtDlp(request, entity)
            if (result.first != 0 && entity.kind == "VIDEO") {
                // Facebook/Instagram format IDs can expire between analysis and download.
                // Retry once with yt-dlp's own best-format selection before giving up.
                Timber.w("Format ${entity.downloadKey} failed; retrying with best selection")
                fileStore.deleteWorkingFiles(id)
                val fallbackRequest = YoutubeDLRequest(entity.sourceUrl)
                    .addOption("--no-playlist")
                    .addOption("--no-warnings")
                    .addOption("--no-mtime")
                    .addOption("-o", outputTemplate)
                    .addOption("--retries", "3")
                    .addOption("--fragment-retries", "3")
                    .addOption("--socket-timeout", "20")
                    .addOption("--concurrent-fragments", "1")
                    // Prefer a single file that already contains audio: no ffmpeg merge
                    // needed, so a broken mux step cannot waste an otherwise good download.
                    .addCommands(
                        listOf(
                            "-f",
                            "best[acodec!=none][height<=720]/best[acodec!=none]/best"
                        )
                    )
                result = runYtDlp(fallbackRequest, entity)
            }

            if (isStopped) {
                pauseRow()
                return Result.success()
            }
            if (result.first != 0) {
                val detail = result.second
                if (DownloadWork.isNetworkFailure(detail) &&
                    runAttemptCount < DownloadWork.MAX_NETWORK_RETRIES
                ) {
                    Timber.w("Network error (attempt $runAttemptCount), retrying: $detail")
                    queueForRetry(entity, detail)
                    return Result.retry()
                }
                fail(entity, DownloadWork.classifyFailure(detail), detail)
                return Result.success()
            }

            val output = fileStore.findWorkingOutput(id)
                ?: throw IllegalStateException("No output file was produced")
            // The row may have been deleted or cancelled while yt-dlp was running.
            // Never publish in that case, or the file would reappear after deletion.
            val live = dao.findById(id)
            if (live == null || live.state !in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED)) {
                fileStore.deleteWorkingFiles(id)
                DownloadNotifications.cancel(applicationContext, notificationId)
                return Result.success()
            }
            val published = fileStore.publish(output, live, preferences.location)
            dao.update(
                live.copy(
                    status = DownloadStatus.COMPLETED.name,
                    processId = null,
                    progress = 100,
                    bytesDownloaded = output.length(),
                    totalBytes = output.length(),
                    speedBps = 0,
                    mediaUri = published.uri.toString(),
                    fileName = published.displayName,
                    outputPath = published.path,
                    updatedAt = System.currentTimeMillis()
                )
            )
            fileStore.deleteWorkingFiles(id)
            DownloadNotifications.postProgress(applicationContext, notificationId, entity.title, 100)
        } catch (e: CancellationException) {
            pauseRow()
        } catch (e: Exception) {
            Timber.w(e, "yt-dlp download failed: $id")
            val detail = listOfNotNull(e.message, e.cause?.message).joinToString(" ")
            if (DownloadWork.isNetworkFailure(detail) &&
                runAttemptCount < DownloadWork.MAX_NETWORK_RETRIES
            ) {
                Timber.w("Network exception (attempt $runAttemptCount), retrying: $detail")
                queueForRetry(entity, detail)
                return Result.retry()
            }
            fail(entity, DownloadWork.classifyFailure(detail), detail)
        }
        return Result.success()
    }

    private suspend fun runYtDlp(
        request: YoutubeDLRequest,
        entity: DownloadEntity
    ): Pair<Int, String> = suspendCancellableCoroutine { cont ->
        val job = YoutubeDL.download(
            request = request,
            progressCallBack = { percent, _, line -> reportProgress(entity, percent.toInt(), line) },
            onStartProcess = { pid ->
                processId = pid
                scope.launch {
                    dao.update((dao.findById(id) ?: entity).copy(processId = pid))
                }
            },
            onEndProcess = { response ->
                if (cont.isActive) cont.resume(response.exitCode to errorDetail(response))
            },
            onError = { error ->
                if (cont.isActive) cont.resumeWithException(error)
            }
        )
        cont.invokeOnCancellation {
            runCatching { job.cancel() }
            processId?.let { runCatching { YoutubeDL.destroyProcessById(it) } }
        }
    }

    private fun errorDetail(response: YoutubeDLResponse): String {
        val all = response.err.orEmpty().lines() + response.out.orEmpty().lines()
        val errors = all.filter { it.contains("ERROR", true) }
        val lines = (if (errors.isNotEmpty()) errors else all.takeLast(12))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .takeLast(6)
        return "exit ${response.exitCode}: ${lines.joinToString(" | ")}".take(360)
    }

    private fun reportProgress(entity: DownloadEntity, percent: Int, line: String) {
        if (percent < 0) return
        val bytes = fileStore.findWorkingOutput(id)?.length() ?: 0L
        val now = System.currentTimeMillis()
        val speed = if (lastTick > 0 && now > lastTick && bytes >= lastBytes) {
            (bytes - lastBytes) * 1000 / (now - lastTick)
        } else 0L
        lastBytes = bytes
        lastTick = now
        scope.launch {
            val current = dao.findById(id) ?: return@launch
            if (current.state != DownloadStatus.DOWNLOADING) return@launch
            val total = current.totalBytes ?: if (percent > 0 && bytes > 0) bytes * 100 / percent else null
            dao.updateProgress(
                id, DownloadStatus.DOWNLOADING.name,
                percent.coerceIn(0, 99), bytes.coerceAtLeast(current.bytesDownloaded),
                total, speed
            )
        }
        if (now - lastNotification >= 2000) {
            lastNotification = now
            scope.launch {
                DownloadNotifications.postProgress(
                    applicationContext, notificationId, entity.title, percent.coerceIn(0, 99)
                )
            }
        }
    }

    /** Marks the row paused without leaving a recycled PID that could kill other downloads. */
    private suspend fun pauseRow() {
        val current = dao.findById(id) ?: return
        dao.update(
            current.copy(
                status = DownloadStatus.PAUSED.name,
                processId = null,
                speedBps = 0,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Re-queues the row for WorkManager's backoff retry, keeping any partial file for --continue. */
    private suspend fun queueForRetry(entity: DownloadEntity, detail: String?) {
        DownloadNotifications.cancel(applicationContext, notificationId)
        val current = dao.findById(id) ?: entity
        dao.update(
            current.copy(
                status = DownloadStatus.QUEUED.name,
                processId = null,
                speedBps = 0,
                errorRes = null,
                errorDetail = detail?.take(360),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun fail(entity: DownloadEntity, kind: ErrorKind, detail: String?) {
        DownloadNotifications.cancel(applicationContext, notificationId)
        val current = dao.findById(id) ?: entity
        dao.update(
            current.copy(
                status = DownloadStatus.FAILED.name,
                processId = null,
                speedBps = 0,
                errorRes = kind.messageRes,
                errorDetail = detail?.take(400),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun isWifi(): Boolean {
        val manager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}
