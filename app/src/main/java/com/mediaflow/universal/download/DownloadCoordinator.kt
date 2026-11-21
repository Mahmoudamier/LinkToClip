package com.mediaflow.universal.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.farimarwat.library.YoutubeDL
import com.mediaflow.universal.core.AppException
import com.mediaflow.universal.core.ErrorKind
import com.mediaflow.universal.data.AppDatabase
import com.mediaflow.universal.data.DownloadEntity
import com.mediaflow.universal.data.DownloadStatus
import com.mediaflow.universal.data.UserPreferences
import com.mediaflow.universal.domain.MediaInfo
import com.mediaflow.universal.domain.MediaKind
import com.mediaflow.universal.domain.MediaOption
import com.mediaflow.universal.extraction.MediaAnalyzer
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** UI-facing entry point for all download actions. */
class DownloadCoordinator(private val context: Context) {

    private val dao = AppDatabase.get(context).downloadDao()
    private val prefs = UserPreferences(context)
    private val workManager = WorkManager.getInstance(context)
    private val fileStore = FileStore(context)
    private val analyzer = MediaAnalyzer()

    /** Adds a selected format to the queue. Returns an AppException for duplicates. */
    suspend fun enqueue(info: MediaInfo, option: MediaOption): DownloadEntity {
        val duplicate = dao.findActiveDuplicate(info.sourceUrl, option.id)
        if (duplicate != null) throw AppException(ErrorKind.DUPLICATE)

        val entity = DownloadEntity(
            id = UUID.randomUUID().toString(),
            sourceUrl = info.sourceUrl,
            platform = info.platform.name,
            kind = option.kind.name,
            title = info.title,
            thumbnailUrl = info.thumbnailUrl,
            optionId = option.id,
            optionLabel = option.label,
            qualityRank = option.qualityRank,
            extension = option.extension,
            engine = option.engine.name,
            downloadKey = option.downloadKey,
            directUrl = option.directUrl,
            headersJson = if (option.headers.isEmpty()) null else JSONObject(option.headers).toString(),
            requiresAudioMux = option.requiresAudioMux,
            totalBytes = option.sizeBytes
        )
        dao.insert(entity)
        enqueueEntity(entity, resume = false)
        return entity
    }

    suspend fun enqueueEntity(entity: DownloadEntity, resume: Boolean) {
        val preferences = prefs.flow.first()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (preferences.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val request = when {
            entity.engine == "NEW_PIPE" && !entity.directUrl.isNullOrBlank() -> {
                val data = Data.Builder()
                    .putString(DownloadWork.KEY_ID, entity.id)
                    .putString(DownloadWork.KEY_URL, entity.sourceUrl)
                    .putString(DownloadWork.KEY_DIRECT_URL, entity.directUrl)
                    .putString(DownloadWork.KEY_HEADERS, entity.headersJson.orEmpty())
                    .putLong(DownloadWork.KEY_TOTAL, entity.totalBytes ?: -1L)
                    .putBoolean(DownloadWork.KEY_RESUME, resume)
                    .build()
                buildRequest<DirectDownloadWorker>(data, constraints, entity.id)
            }
            else -> {
                val data = Data.Builder()
                    .putString(DownloadWork.KEY_ID, entity.id)
                    .putString(DownloadWork.KEY_URL, entity.sourceUrl)
                    .putString(DownloadWork.KEY_DOWNLOAD_KEY, entity.downloadKey)
                    .putBoolean(DownloadWork.KEY_RESUME, resume)
                    .build()
                buildRequest<YtDlpDownloadWorker>(data, constraints, entity.id)
            }
        }

        dao.update(
            entity.copy(
                status = DownloadStatus.QUEUED.name,
                processId = null,
                errorRes = null,
                errorDetail = null,
                updatedAt = System.currentTimeMillis()
            )
        )
        workManager.enqueueUniqueWork(
            DownloadWork.uniqueName(entity.id),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private inline fun <reified W : androidx.work.ListenableWorker> buildRequest(
        data: Data,
        constraints: Constraints,
        id: String
    ): OneTimeWorkRequest = OneTimeWorkRequestBuilder<W>()
        .setInputData(data)
        .setConstraints(constraints)
        .addTag(DownloadWork.tag(id))
        // Transient DNS/connection errors use Result.retry(); wait before trying again so a
        // short network outage does not burn through all attempts in a few milliseconds.
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            DownloadWork.RETRY_BACKOFF_SECONDS,
            TimeUnit.SECONDS
        )
        .build()

    /**
     * URLs that are already queued, downloading, paused or completed. Failed/cancelled rows
     * are deliberately excluded so a playlist "resume" can re-queue them without duplicating
     * the ones that are actually in progress.
     */
    suspend fun findExistingUrls(
        urls: Collection<String>,
        kind: MediaKind? = null
    ): Set<String> {
        if (urls.isEmpty()) return emptySet()
        val statuses = listOf(
            DownloadStatus.QUEUED.name,
            DownloadStatus.DOWNLOADING.name,
            DownloadStatus.PAUSED.name,
            DownloadStatus.COMPLETED.name
        )
        return if (kind == null) {
            dao.findExistingUrls(urls.toList(), statuses).toSet()
        } else {
            dao.findExistingUrlsOfKind(urls.toList(), kind.name, statuses).toSet()
        }
    }

    suspend fun pause(id: String) {
        val entity = dao.findById(id) ?: return
        if (!entity.state.isActive) return
        workManager.cancelUniqueWork(DownloadWork.uniqueName(id))
        if (entity.state == DownloadStatus.DOWNLOADING) {
            entity.processId?.let { runCatching { YoutubeDL.destroyProcessById(it) } }
        }
        dao.update(
            entity.copy(
                status = DownloadStatus.PAUSED.name,
                speedBps = 0,
                processId = null,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun resume(id: String) {
        val entity = dao.findById(id) ?: return
        if (entity.state != DownloadStatus.PAUSED) return
        enqueueEntity(entity, resume = true)
    }

    suspend fun cancel(id: String) {
        val entity = dao.findById(id) ?: return
        workManager.cancelUniqueWork(DownloadWork.uniqueName(id))
        if (entity.state == DownloadStatus.DOWNLOADING) {
            entity.processId?.let { runCatching { YoutubeDL.destroyProcessById(it) } }
        }
        fileStore.deleteWorkingFiles(id)
        dao.update(
            entity.copy(
                status = DownloadStatus.CANCELED.name,
                processId = null,
                progress = 0,
                bytesDownloaded = 0,
                speedBps = 0,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Re-analyses the source because direct media URLs expire, then restarts the download. */
    suspend fun retry(id: String): Boolean {
        val entity = dao.findById(id) ?: return false
        fileStore.deleteWorkingFiles(id)
        val fresh = try {
            val platform = runCatching { com.mediaflow.universal.core.UrlDetector.Platform.valueOf(entity.platform) }
                .getOrNull() ?: return false
            analyzer.analyze(entity.sourceUrl, platform)
        } catch (e: Exception) {
            val kind = (e as? AppException)?.kind ?: ErrorKind.DOWNLOAD
            dao.update(
                entity.copy(
                    status = DownloadStatus.FAILED.name,
                    errorRes = kind.messageRes,
                    errorDetail = e.message,
                    updatedAt = System.currentTimeMillis()
                )
            )
            return false
        }

        val option = fresh.videoOptions + fresh.audioOptions
        val updated = option.firstOrNull { it.id == entity.optionId }
        val refreshed = entity.copy(
            title = fresh.title,
            thumbnailUrl = fresh.thumbnailUrl,
            directUrl = updated?.directUrl,
            downloadKey = updated?.downloadKey ?: entity.downloadKey,
            engine = (updated?.engine?.name ?: entity.engine),
            progress = 0,
            bytesDownloaded = 0,
            speedBps = 0,
            mediaUri = null,
            fileName = null,
            outputPath = null,
            processId = null,
            partialPath = null,
            errorRes = null,
            errorDetail = null,
            totalBytes = updated?.sizeBytes ?: entity.totalBytes
        )
        dao.update(refreshed)
        enqueueEntity(refreshed, resume = false)
        return true
    }

    suspend fun delete(id: String) {
        val entity = dao.findById(id) ?: return
        // Cancel unconditionally. A row can look finished while a WorkManager job is still
        // queued or running (e.g. after a process restart), which would download it again.
        workManager.cancelUniqueWork(DownloadWork.uniqueName(id))
        runCatching { workManager.cancelAllWorkByTag(DownloadWork.tag(id)) }
        // Only a live yt-dlp process belongs to this row. Completed/failed rows keep stale
        // PIDs that may have been recycled by another download - killing those stops it.
        if (entity.state == DownloadStatus.DOWNLOADING) {
            entity.processId?.let { runCatching { YoutubeDL.destroyProcessById(it) } }
        }
        fileStore.delete(entity)
        dao.delete(id)
    }

    suspend fun clearHistory() = dao.clearHistory()
}
