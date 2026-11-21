package com.mediaflow.universal.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.mediaflow.universal.data.DownloadEntity
import com.mediaflow.universal.data.DownloadLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

data class PublishedFile(val uri: Uri, val displayName: String, val path: String?)

/** Owns all storage access. Download workers only write into the app-private working folder. */
class FileStore(private val context: Context) {

    fun workingDir(): File = File(context.filesDir, "downloads").apply { mkdirs() }

    fun workingFile(id: String, extension: String): File = File(workingDir(), "$id.$extension")

    fun findWorkingOutput(id: String): File? =
        workingDir().listFiles { file -> file.isFile && file.name.startsWith("$id.") }
            ?.maxByOrNull { it.lastModified() }

    fun deleteWorkingFiles(id: String) {
        workingDir().listFiles { file -> file.isFile && file.name.startsWith("$id.") }
            ?.forEach { runCatching { it.delete() } }
    }

    suspend fun publish(source: File, entity: DownloadEntity, location: DownloadLocation): PublishedFile =
        withContext(Dispatchers.IO) {
            val name = safeFileName(entity)
            val primary = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    publishScoped(source, entity, location, name)
                } else {
                    publishLegacy(source, entity, location, name)
                }
            }
            primary.getOrElse { error ->
                // Some devices intermittently reject MediaStore inserts even when there is
                // plenty of free space. The bytes are already on disk — save them into the
                // app's own storage instead of throwing away a completed download.
                Timber.w(error, "MediaStore publish failed; using app-private storage")
                publishPrivate(source, entity, name)
            }
        }

    /** Last-resort publish target that always works and stays shareable via FileProvider. */
    private fun publishPrivate(source: File, entity: DownloadEntity, name: String): PublishedFile {
        val dir = File(workingDir(), "published").apply { mkdirs() }
        val target = File(dir, name)
        source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output, 64 * 1024) } }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target
        )
        return PublishedFile(uri, name, target.absolutePath)
    }

    private fun publishScoped(
        source: File,
        entity: DownloadEntity,
        location: DownloadLocation,
        name: String
    ): PublishedFile {
        val isVideo = entity.kind == "VIDEO"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType(entity.extension, isVideo))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (location == DownloadLocation.DOWNLOADS_APP_FOLDER) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/LinkToClip/${if (isVideo) "Video" else "Audio"}"
                )
            } else {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC}/LinkToClip"
                )
            }
        }

        val collection = if (location == DownloadLocation.DOWNLOADS_APP_FOLDER ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        ) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val uri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create MediaStore entry")
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
            } ?: throw IllegalStateException("Could not open MediaStore output")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }.onFailure {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw it
        }
        return PublishedFile(uri, name, relativePath(location, isVideo))
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(
        source: File,
        entity: DownloadEntity,
        location: DownloadLocation,
        name: String
    ): PublishedFile {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val isVideo = entity.kind == "VIDEO"
        val base = if (hasPermission && location == DownloadLocation.PUBLIC_MEDIA_FOLDERS) {
            File(
                Environment.getExternalStoragePublicDirectory(
                    if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
                ),
                "LinkToClip"
            )
        } else {
            File(
                context.getExternalFilesDir(
                    if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
                ),
                "LinkToClip"
            )
        }
        base.mkdirs()
        val target = File(base, name)
        source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output, 64 * 1024) } }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, target.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType(entity.extension, isVideo))
        }
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val uri = runCatching {
            context.contentResolver.insert(collection, values)
                ?: target.toUri()
        }.getOrDefault(target.toUri())
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
        return PublishedFile(uri, name, target.absolutePath)
    }

    suspend fun rename(item: DownloadEntity, newName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = if (newName.endsWith(".${item.extension}", true)) newName else "$newName.${item.extension}"
            val uri = item.mediaUri?.toUri()
            if (uri != null && uri.scheme == "content") {
                val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
                context.contentResolver.update(uri, values, null, null)
            } else {
                val old = File(item.outputPath ?: return@runCatching name)
                val target = File(old.parentFile, name)
                if (old.renameTo(target)) target.absolutePath else old.absolutePath
            }
            name
        }
    }

    suspend fun delete(item: DownloadEntity) = withContext(Dispatchers.IO) {
        // Files saved through the private fallback have an absolute path and a FileProvider
        // URI (which does not support ContentResolver.delete). Real MediaStore rows only have
        // a relative path here, so distinguish them by whether the path is absolute.
        val path = item.outputPath
        if (!path.isNullOrBlank() && path.startsWith("/")) {
            runCatching { File(path).delete() }
        } else {
            val uri = item.mediaUri?.toUri()
            if (uri != null && uri.scheme == "content") {
                runCatching { context.contentResolver.delete(uri, null, null) }
                    .onFailure { Timber.w(it, "MediaStore delete failed") }
            }
        }
        deleteWorkingFiles(item.id)
    }

    fun openIntent(item: DownloadEntity): Intent? {
        val uri = item.mediaUri?.toUri() ?: return null
        val mime = mimeType(item.extension, item.kind == "VIDEO")
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shareIntent(item: DownloadEntity): Intent? {
        val uri = item.mediaUri?.toUri() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType(item.extension, item.kind == "VIDEO")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun relativePath(location: DownloadLocation, isVideo: Boolean): String =
        if (location == DownloadLocation.DOWNLOADS_APP_FOLDER) {
            "Download/LinkToClip/${if (isVideo) "Video" else "Audio"}"
        } else {
            "${if (isVideo) "Movies" else "Music"}/LinkToClip"
        }

    private fun safeFileName(entity: DownloadEntity): String {
        val base = entity.title
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(90)
            .ifBlank { "media" }
        val quality = entity.optionLabel.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')
        return "${base} (${quality}).${entity.extension}"
    }

    private fun mimeType(extension: String, isVideo: Boolean): String = when (extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/ogg"
        "ogg" -> "audio/ogg"
        "webm" -> if (isVideo) "video/webm" else "audio/webm"
        "mkv" -> "video/x-matroska"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> if (isVideo) "video/mp4" else "audio/mpeg"
    }
}
