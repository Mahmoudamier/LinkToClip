package com.mediaflow.universal.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query(
        "SELECT * FROM downloads WHERE status IN ('QUEUED','DOWNLOADING','PAUSED') ORDER BY createdAt DESC"
    )
    fun observeActive(): Flow<List<DownloadEntity>>

    @Query(
        "SELECT * FROM downloads WHERE status IN ('COMPLETED','FAILED','CANCELED') ORDER BY updatedAt DESC"
    )
    fun observeHistory(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DownloadEntity?

    @Query(
        "SELECT * FROM downloads WHERE sourceUrl = :url AND optionId = :optionId " +
            "AND status IN ('QUEUED','DOWNLOADING','PAUSED') LIMIT 1"
    )
    suspend fun findActiveDuplicate(url: String, optionId: String): DownloadEntity?

    @Query("SELECT sourceUrl FROM downloads WHERE sourceUrl IN (:urls) AND status IN (:statuses)")
    suspend fun findExistingUrls(urls: List<String>, statuses: List<String>): List<String>

    @Query(
        "SELECT sourceUrl FROM downloads WHERE sourceUrl IN (:urls) AND kind = :kind " +
            "AND status IN (:statuses)"
    )
    suspend fun findExistingUrlsOfKind(
        urls: List<String>,
        kind: String,
        statuses: List<String>
    ): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadEntity)

    @Update
    suspend fun update(item: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long = System.currentTimeMillis())

    @Query(
        "UPDATE downloads SET status = :status, progress = :progress, bytesDownloaded = :bytes, " +
            "totalBytes = :total, speedBps = :speed, updatedAt = :now WHERE id = :id"
    )
    suspend fun updateProgress(
        id: String,
        status: String,
        progress: Int,
        bytes: Long,
        total: Long?,
        speed: Long,
        now: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED','FAILED','CANCELED')")
    suspend fun clearHistory()

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    /** Only really running/queued rows keep the foreground service alive. */
    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED','DOWNLOADING')")
    suspend fun countActive(): Int
}
