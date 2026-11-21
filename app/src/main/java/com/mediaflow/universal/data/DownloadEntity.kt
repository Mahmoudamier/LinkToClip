package com.mediaflow.universal.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELED;

    val isActive: Boolean get() = this == QUEUED || this == DOWNLOADING || this == PAUSED
}

@Entity(
    tableName = "downloads",
    indices = [Index(value = ["sourceUrl", "optionId"]), Index(value = ["status"])]
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    val sourceUrl: String,
    val platform: String,
    val kind: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val optionId: String,
    val optionLabel: String,
    val qualityRank: Int = 0,
    val extension: String,
    val engine: String,
    val downloadKey: String,
    val directUrl: String? = null,
    val headersJson: String? = null,
    val requiresAudioMux: Boolean = false,
    val status: String = DownloadStatus.QUEUED.name,
    val progress: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val speedBps: Long = 0L,
    val workId: String? = null,
    val processId: String? = null,
    val partialPath: String? = null,
    val outputPath: String? = null,
    val mediaUri: String? = null,
    val fileName: String? = null,
    val errorRes: Int? = null,
    val errorDetail: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val state: DownloadStatus get() = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED)
}
