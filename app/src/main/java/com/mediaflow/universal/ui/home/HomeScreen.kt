package com.mediaflow.universal.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mediaflow.universal.R
import com.mediaflow.universal.domain.MediaInfo
import com.mediaflow.universal.domain.MediaKind
import com.mediaflow.universal.domain.MediaOption
import com.mediaflow.universal.ui.components.EmptyState
import com.mediaflow.universal.ui.components.PlatformBadge
import com.mediaflow.universal.ui.components.formatDuration
import com.mediaflow.universal.ui.components.formatSize

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    sharedLink: String? = null,
    onGoToDownloads: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(sharedLink) {
        if (!sharedLink.isNullOrBlank()) viewModel.setUrl(sharedLink)
        viewModel.checkClipboard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::setUrl,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    minLines = 2,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.paste_hint)) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val text = runCatching {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                                    ?.primaryClip?.getItemAt(0)?.text?.toString()
                            }.getOrNull()
                            if (!text.isNullOrBlank()) viewModel.setUrl(text)
                        }) {
                            Icon(
                                Icons.Filled.ContentPaste,
                                contentDescription = stringResource(R.string.paste)
                            )
                        }
                    }
                )

                state.platform?.let { platform ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlatformBadge(platform)
                        Text(platform.displayName, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Button(
                    onClick = viewModel::analyze,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    enabled = state.url.isNotBlank() && !state.loading
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(stringResource(if (state.loading) R.string.analyzing else R.string.analyze))
                }
            }
        }

        AnimatedVisibility(visible = !state.clipboardSuggestion.isNullOrBlank()) {
            Surface(
                onClick = {
                    state.clipboardSuggestion?.let { viewModel.setUrl(it) }
                    viewModel.clearClipboardSuggestion()
                },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null)
                    Text(
                        stringResource(R.string.paste_from_clipboard),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        state.errorRes?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null)
                    Text(
                        stringResource(error),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::clearError, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            }
        }

        state.messageRes?.let { message ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Text(
                        listOfNotNull(stringResource(message), state.messageDetail).joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::clearMessage, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            }
        }

        when {
            state.playlist != null -> PlaylistSection(state, viewModel, onGoToDownloads)
            state.media != null -> MediaSection(state, viewModel, onGoToDownloads)
            !state.loading -> EmptyState(
                icon = Icons.Filled.PlayCircle,
                title = stringResource(R.string.home_title),
                subtitle = stringResource(R.string.paste_hint)
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MediaSection(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onGoToDownloads: () -> Unit
) {
    val media: MediaInfo = state.media ?: return
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!media.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = media.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        media.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        media.author ?: stringResource(R.string.unknown_author),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlatformBadge(media.platform, compact = true)
                        formatDuration(media.durationSeconds)?.let {
                            Text(
                                "${stringResource(R.string.duration)}: $it",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.available_formats), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.formats_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TabRow(
            selectedTabIndex = if (state.selectedKind == MediaKind.VIDEO) 0 else 1,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = state.selectedKind == MediaKind.VIDEO,
                onClick = { viewModel.selectKind(MediaKind.VIDEO) },
                text = { Text(stringResource(R.string.video_quality)) }
            )
            Tab(
                selected = state.selectedKind == MediaKind.AUDIO,
                onClick = { viewModel.selectKind(MediaKind.AUDIO) },
                text = { Text(stringResource(R.string.audio_quality)) }
            )
        }

        val options = if (state.selectedKind == MediaKind.VIDEO) {
            media.videoOptions
        } else {
            media.audioOptions
        }
        if (options.isEmpty()) {
            Text(
                stringResource(R.string.error_no_formats),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    OptionRow(option, option.id == state.selectedOptionId) {
                        viewModel.selectOption(option.id)
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.download(onGoToDownloads) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.large,
            enabled = options.any { it.id == state.selectedOptionId } && !state.busy
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(stringResource(R.string.download_now))
        }
    }
}

@Composable
private fun PlaylistSection(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onGoToDownloads: () -> Unit
) {
    val playlist = state.playlist ?: return
    val remaining = (playlist.items.size - state.playlistExisting).coerceAtLeast(0)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlatformBadge(playlist.platform, compact = true)
                Text(
                    stringResource(R.string.playlist_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                playlist.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(stringResource(R.string.playlist_items, playlist.items.size))
                if (state.playlistExisting > 0) {
                    InfoChip(
                        stringResource(
                            R.string.playlist_existing,
                            state.playlistExisting,
                            playlist.items.size
                        )
                    )
                }
            }

            // Playlists honour the same video/audio choice as a single link.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.choose_playlist_type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TabRow(
                    selectedTabIndex = if (state.playlistKind == MediaKind.VIDEO) 0 else 1,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = state.playlistKind == MediaKind.VIDEO,
                        onClick = { viewModel.selectPlaylistKind(MediaKind.VIDEO) },
                        text = { Text(stringResource(R.string.tab_video)) }
                    )
                    Tab(
                        selected = state.playlistKind == MediaKind.AUDIO,
                        onClick = { viewModel.selectPlaylistKind(MediaKind.AUDIO) },
                        text = { Text(stringResource(R.string.tab_audio)) }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                playlist.items.take(5).forEachIndexed { index, item ->
                    Text(
                        "${index + 1}. ${item.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (playlist.items.size > 5) {
                    Text(
                        stringResource(R.string.more_items, playlist.items.size - 5),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.busy && state.playlistTotal > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.playlist_adding, state.playlistAdded, state.playlistTotal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = {
                            (state.playlistAdded.toFloat() / state.playlistTotal.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Button(
                onClick = { viewModel.downloadPlaylist(onGoToDownloads) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large,
                enabled = !state.busy
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    if (state.playlistExisting > 0 && remaining > 0) {
                        stringResource(R.string.playlist_resume, remaining)
                    } else {
                        stringResource(R.string.download_playlist)
                    }
                )
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun OptionRow(option: MediaOption, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(option.label, style = MaterialTheme.typography.titleSmall)
                val format = option.notes ?: option.extension.uppercase()
                val details = listOfNotNull(
                    format,
                    formatSize(option.sizeBytes)?.let {
                        "${stringResource(R.string.estimated_size)}: $it"
                    }
                ).joinToString(" · ")
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
