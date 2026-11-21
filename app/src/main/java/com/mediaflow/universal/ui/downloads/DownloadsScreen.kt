package com.mediaflow.universal.ui.downloads

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mediaflow.universal.R
import com.mediaflow.universal.core.UrlDetector
import com.mediaflow.universal.data.DownloadEntity
import com.mediaflow.universal.data.DownloadStatus
import com.mediaflow.universal.ui.components.EmptyState
import com.mediaflow.universal.ui.components.PlatformBadge
import com.mediaflow.universal.ui.components.formatSize
import com.mediaflow.universal.ui.components.statusLabel

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val active by viewModel.active.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val renameTarget by viewModel.renameTarget.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    var renameText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DownloadsEffect.Open -> runCatching { context.startActivity(effect.intent) }
                is DownloadsEffect.Share -> runCatching {
                    context.startActivity(Intent.createChooser(effect.intent, context.getString(R.string.share)))
                }
                is DownloadsEffect.Message -> message = context.getString(effect.messageRes)
            }
        }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        message = null
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar { Text(data.visuals.message) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("${stringResource(R.string.active_downloads)} (${active.size})") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.history)) }
                )
            }

            if (tab == 1 && history.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = viewModel::clearHistory) {
                        Text(stringResource(R.string.clear_history))
                    }
                }
            }

            val list = if (tab == 0) active else history
            if (list.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Download,
                    title = stringResource(if (tab == 0) R.string.no_active_downloads else R.string.no_history),
                    subtitle = stringResource(R.string.home_subtitle)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(list, key = { it.id }) { item ->
                        DownloadCard(
                            item = item,
                            onPause = { viewModel.pause(item) },
                            onResume = { viewModel.resume(item) },
                            onCancel = { viewModel.cancel(item) },
                            onRetry = { viewModel.retry(item) },
                            onOpen = { viewModel.open(item) },
                            onShare = { viewModel.share(item) },
                            onRename = {
                                renameText = item.fileName ?: item.title
                                viewModel.requestRename(item)
                            },
                            onDelete = { viewModel.delete(item) },
                            onCopyDetails = {
                                if (!item.errorDetail.isNullOrBlank()) {
                                    clipboard.setText(AnnotatedString(item.errorDetail.orEmpty()))
                                    message = context.getString(R.string.error_details_copied)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRename,
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.new_file_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.rename(renameText) }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRename) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun DownloadCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopyDetails: () -> Unit
) {
    val status = item.state
    val platform = runCatching { UrlDetector.Platform.valueOf(item.platform) }.getOrNull()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(54.dp).clip(MaterialTheme.shapes.medium),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    if (!item.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(model = item.thumbnailUrl, contentDescription = null)
                    } else {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.padding(15.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        platform?.let { PlatformBadge(it, compact = true) }
                        Text(
                            item.optionLabel,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StatusChip(status)
                    }
                }
            }

            if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED ||
                status == DownloadStatus.PAUSED
            ) {
                LinearProgressIndicator(
                    progress = { if (item.progress >= 0) item.progress / 100f else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (status == DownloadStatus.PAUSED) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            val details = buildList {
                formatSize(item.bytesDownloaded.takeIf { it > 0 })?.let { add(it) }
                item.totalBytes?.let { formatSize(it)?.let { size -> add("/ $size") } }
                if (status == DownloadStatus.DOWNLOADING && item.speedBps > 0) {
                    add("${formatSize(item.speedBps)}/ث")
                }
                item.errorRes?.let { add(stringResource(it)) }
            }.joinToString(" ")
            if (details.isNotBlank()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!item.errorDetail.isNullOrBlank()) {
                TextButton(
                    onClick = onCopyDetails,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(stringResource(R.string.copy_error_details))
                }
            }

            when (status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(stringResource(R.string.pause), Icons.Filled.Pause, onPause, Modifier.weight(1f))
                    ActionButton(stringResource(R.string.cancel), Icons.Filled.Close, onCancel, Modifier.weight(1f))
                }

                DownloadStatus.PAUSED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(stringResource(R.string.resume), Icons.Filled.PlayArrow, onResume, Modifier.weight(1f))
                    ActionButton(stringResource(R.string.cancel), Icons.Filled.Close, onCancel, Modifier.weight(1f))
                }

                DownloadStatus.COMPLETED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(
                            stringResource(R.string.open), Icons.AutoMirrored.Filled.OpenInNew,
                            onOpen, Modifier.weight(1f)
                        )
                        ActionButton(stringResource(R.string.share), Icons.Filled.Share, onShare, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(stringResource(R.string.rename), Icons.Filled.Edit, onRename, Modifier.weight(1f))
                        ActionButton(stringResource(R.string.delete), Icons.Filled.Delete, onDelete, Modifier.weight(1f))
                    }
                }

                DownloadStatus.FAILED, DownloadStatus.CANCELED -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(stringResource(R.string.retry), Icons.Filled.Replay, onRetry, Modifier.weight(1f))
                    ActionButton(stringResource(R.string.delete), Icons.Filled.Delete, onDelete, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: DownloadStatus) {
    val colors = when (status) {
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.small, color = colors.first, contentColor = colors.second) {
        Text(
            statusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
