package com.mediaflow.universal.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mediaflow.universal.core.ErrorKind
import com.mediaflow.universal.data.AppDatabase
import com.mediaflow.universal.data.DownloadEntity
import com.mediaflow.universal.data.DownloadStatus
import com.mediaflow.universal.data.UserPreferences
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Downloads a direct public media stream with HTTP Range resume support. */
class DirectDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val dao = AppDatabase.get(applicationContext).downloadDao()
    private val prefs = UserPreferences(applicationContext)
    private val fileStore = FileStore(applicationContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val id = inputData.getString(DownloadWork.KEY_ID).orEmpty()
    private val notificationId = id.hashCode().takeIf { it != Int.MIN_VALUE } ?: 1000

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
        if (entity.state !in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)) {
            return Result.success()
        }

        val preferences = prefs.flow.first()
        if (preferences.wifiOnly && !isWifi()) {
            dao.updateStatus(id, DownloadStatus.PAUSED.name)
            return Result.success()
        }

        runCatching { setForegroundAsync(getForegroundInfo()) }
            .onFailure { Timber.w(it, "Could not start background download service") }
        dao.updateStatus(id, DownloadStatus.DOWNLOADING.name)
        DownloadNotifications.postProgress(applicationContext, notificationId, entity.title, 0, true)

        val directUrl = inputData.getString(DownloadWork.KEY_DIRECT_URL) ?: entity.directUrl
        if (directUrl.isNullOrBlank()) {
            fail(entity, ErrorKind.EXPIRED)
            return Result.success()
        }

        val file = fileStore.workingFile(id, entity.extension)
        // On a WorkManager retry the partial file must be kept and resumed via Range.
        val resume = inputData.getBoolean(DownloadWork.KEY_RESUME, false) || runAttemptCount > 0
        if (!resume && file.exists()) file.delete()
        var downloaded = if (resume && file.exists()) file.length() else 0L
        val totalFromInput = inputData.getLong(DownloadWork.KEY_TOTAL, entity.totalBytes ?: -1L)
            .takeIf { it > 0 } ?: entity.totalBytes

        var lastReport = 0L
        var lastBytes = downloaded
        var lastNotification = 0L

        try {
            val headers = runCatching {
                val json = JSONObject(inputData.getString(DownloadWork.KEY_HEADERS).orEmpty())
                json.keys().asSequence().associateWith { json.getString(it) }
            }.getOrDefault(emptyMap())

            val request = Request.Builder().url(directUrl).apply {
                headers.forEach { (key, value) -> addHeader(key, value) }
                if (downloaded > 0) addHeader("Range", "bytes=$downloaded-")
            }.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty response")
                val length = body.contentLength().takeIf { it > 0 }
                val total = if (response.code == 206 && length != null) downloaded + length
                else length ?: totalFromInput ?: 0L

                if (downloaded > 0 && response.code != 206) {
                    downloaded = 0
                }
                val append = downloaded > 0
                body.byteStream().use { input ->
                    FileOutputStream(file, append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (!isStopped) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastReport >= 350) {
                                val speed = if (now > lastReport) (downloaded - lastBytes) * 1000 / (now - lastReport) else 0
                                val progress = if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 99) else -1
                                dao.updateProgress(
                                    id, DownloadStatus.DOWNLOADING.name,
                                    if (progress < 0) 0 else progress, downloaded,
                                    total.takeIf { it > 0 }, speed
                                )
                                setProgressAsync(
                                    androidx.work.Data.Builder()
                                        .putInt(DownloadWork.KEY_PROGRESS, progress)
                                        .build()
                                )
                                lastReport = now
                                lastBytes = downloaded
                            }
                            if (now - lastNotification >= 2000) {
                                val percent = if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 99) else 0
                                DownloadNotifications.postProgress(
                                    applicationContext, notificationId, entity.title, percent
                                )
                                lastNotification = now
                            }
                        }
                    }
                }

                if (isStopped) {
                    dao.updateProgress(
                        id, DownloadStatus.PAUSED.name,
                        if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 99) else 0,
                        downloaded, total.takeIf { it > 0 }, 0
                    )
                    DownloadNotifications.cancel(applicationContext, notificationId)
                    return Result.success()
                }

                // The row may have been deleted or cancelled while bytes were streaming in.
                // Never publish in that case, or the file would reappear after deletion.
                val live = dao.findById(id)
                if (live == null || live.state !in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED)) {
                    fileStore.deleteWorkingFiles(id)
                    DownloadNotifications.cancel(applicationContext, notificationId)
                    return Result.success()
                }
                val published = fileStore.publish(file, live, preferences.location)
                dao.update(
                    live.copy(
                        status = DownloadStatus.COMPLETED.name,
                        progress = 100,
                        bytesDownloaded = downloaded,
                        totalBytes = total.takeIf { it > 0 },
                        speedBps = 0,
                        mediaUri = published.uri.toString(),
                        fileName = published.displayName,
                        outputPath = published.path,
                        partialPath = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                fileStore.deleteWorkingFiles(id)
                DownloadNotifications.postProgress(applicationContext, notificationId, entity.title, 100)
            }
        } catch (e: IOException) {
            Timber.w(e, "Direct download failed: $id (attempt $runAttemptCount)")
            if (isTransient(e) && runAttemptCount < DownloadWork.MAX_NETWORK_RETRIES) {
                queueForRetry(entity, downloaded, e.message)
                return Result.retry()
            }
            val detail = listOfNotNull(e.message, e.cause?.message).joinToString(" ")
            fail(entity, DownloadWork.classifyFailure(detail), downloaded, totalFromInput, detail)
        } catch (e: Exception) {
            Timber.w(e, "Direct download failed: $id")
            val detail = listOfNotNull(e.message, e.cause?.message).joinToString(" ")
            if (DownloadWork.isNetworkFailure(detail) &&
                runAttemptCount < DownloadWork.MAX_NETWORK_RETRIES
            ) {
                queueForRetry(entity, downloaded, detail)
                return Result.retry()
            }
            fail(entity, DownloadWork.classifyFailure(detail), downloaded, totalFromInput, detail)
        }
        return Result.success()
    }

    /** DNS/timeout/5xx failures are worth retrying; 4xx (expired URL) is not. */
    private fun isTransient(e: IOException): Boolean {
        if (e is java.net.UnknownHostException || e is java.net.SocketTimeoutException ||
            e is java.net.ConnectException
        ) return true
        val text = listOfNotNull(e.message, e.cause?.message).joinToString(" ")
        return DownloadWork.isNetworkFailure(text) ||
            e.message.orEmpty().matches(Regex("HTTP (429|[5-9]\\d\\d)"))
    }

    /** Re-queues the row so WorkManager retries after backoff, keeping the partial file. */
    private suspend fun queueForRetry(entity: DownloadEntity, bytes: Long, detail: String?) {
        DownloadNotifications.cancel(applicationContext, notificationId)
        val current = dao.findById(id) ?: entity
        dao.update(
            current.copy(
                status = DownloadStatus.QUEUED.name,
                bytesDownloaded = bytes,
                speedBps = 0,
                errorRes = null,
                errorDetail = detail?.take(360),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun fail(
        entity: DownloadEntity,
        kind: ErrorKind,
        bytes: Long = 0,
        total: Long? = null,
        detail: String? = null
    ) {
        DownloadNotifications.cancel(applicationContext, notificationId)
        val current = dao.findById(id) ?: entity
        dao.update(
            current.copy(
                status = DownloadStatus.FAILED.name,
                bytesDownloaded = bytes,
                totalBytes = total ?: current.totalBytes,
                speedBps = 0,
                errorRes = kind.messageRes,
                errorDetail = detail,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun isWifi(): Boolean {
        val manager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
