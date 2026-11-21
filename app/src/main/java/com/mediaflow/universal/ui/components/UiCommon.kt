package com.mediaflow.universal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.universal.R
import com.mediaflow.universal.core.UrlDetector.Platform

@Composable
fun PlatformBadge(platform: Platform, compact: Boolean = false) {
    val (icon, tint, label) = when (platform) {
        Platform.YOUTUBE -> Triple(Icons.Filled.PlayArrow, Color(0xFFE44134), platform.displayName)
        Platform.SOUNDCLOUD -> Triple(Icons.Filled.Cloud, Color(0xFFFF7700), platform.displayName)
        Platform.FACEBOOK -> Triple(Icons.Filled.ThumbUp, Color(0xFF1877F2), platform.displayName)
        Platform.INSTAGRAM -> Triple(Icons.Filled.CameraAlt, Color(0xFFC13584), platform.displayName)
        Platform.TIKTOK -> Triple(Icons.Filled.MusicNote, Color(0xFFFE2C55), platform.displayName)
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = tint.copy(alpha = 0.12f),
        contentColor = tint
    ) {
        if (compact) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 44.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(52.dp).padding(13.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun formatSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val units = listOf("بايت", "كيلوبايت", "ميجابايت", "جيجابايت")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "$bytes بايت" else String.format("%.1f %s", value, units[index])
}

fun formatDuration(seconds: Long?): String? {
    if (seconds == null || seconds <= 0) return null
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, secs)
    else String.format("%d:%02d", minutes, secs)
}

@Composable
fun statusLabel(status: com.mediaflow.universal.data.DownloadStatus): String = when (status) {
    com.mediaflow.universal.data.DownloadStatus.QUEUED -> stringResource(R.string.queued)
    com.mediaflow.universal.data.DownloadStatus.DOWNLOADING -> stringResource(R.string.downloading)
    com.mediaflow.universal.data.DownloadStatus.PAUSED -> stringResource(R.string.paused)
    com.mediaflow.universal.data.DownloadStatus.COMPLETED -> stringResource(R.string.completed)
    com.mediaflow.universal.data.DownloadStatus.FAILED -> stringResource(R.string.failed)
    com.mediaflow.universal.data.DownloadStatus.CANCELED -> stringResource(R.string.canceled)
}
