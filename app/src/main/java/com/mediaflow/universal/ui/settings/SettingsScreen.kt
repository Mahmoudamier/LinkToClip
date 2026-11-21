package com.mediaflow.universal.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediaflow.universal.BuildConfig
import com.mediaflow.universal.R
import com.mediaflow.universal.data.DefaultFormat
import com.mediaflow.universal.data.DefaultQuality
import com.mediaflow.universal.data.DownloadLocation
import com.mediaflow.universal.data.ThemeMode
import com.mediaflow.universal.data.UserPrefs

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val current = prefs ?: UserPrefs()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle(stringResource(R.string.theme)) }
        item {
            SettingsCard {
                SettingsRow(stringResource(R.string.theme), optionLabel(current.theme)) { dialog = SettingsDialog.THEME }
                Divider()
                SettingsSwitch(
                    stringResource(R.string.wifi_only),
                    stringResource(R.string.wifi_only_summary),
                    current.wifiOnly,
                    viewModel::setWifiOnly
                )
            }
        }

        item { SectionTitle(stringResource(R.string.downloads)) }
        item {
            SettingsCard {
                SettingsRow(stringResource(R.string.default_quality), optionLabel(current.defaultQuality)) {
                    dialog = SettingsDialog.QUALITY
                }
                Divider()
                SettingsRow(stringResource(R.string.default_format), optionLabel(current.defaultFormat)) {
                    dialog = SettingsDialog.FORMAT
                }
                Divider()
                SettingsRow(stringResource(R.string.download_location), optionLabel(current.location)) {
                    dialog = SettingsDialog.LOCATION
                }
            }
        }

        item { SectionTitle(stringResource(R.string.history)) }
        item {
            SettingsCard {
                SettingsRow(
                    stringResource(R.string.clear_history),
                    null,
                    Icons.Filled.DeleteForever,
                    onClick = { confirmClear = true }
                )
            }
        }

        item { SectionTitle(stringResource(R.string.about)) }
        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                        Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.legal_notice), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp)) }
    }

    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            stringResource(R.string.theme),
            ThemeMode.entries.map { it.name to optionLabel(it) },
            current.theme.name
        ) { name -> viewModel.setTheme(ThemeMode.valueOf(name)); dialog = null }

        SettingsDialog.QUALITY -> ChoiceDialog(
            stringResource(R.string.default_quality),
            DefaultQuality.entries.map { it.name to optionLabel(it) },
            current.defaultQuality.name
        ) { name -> viewModel.setQuality(DefaultQuality.valueOf(name)); dialog = null }

        SettingsDialog.FORMAT -> ChoiceDialog(
            stringResource(R.string.default_format),
            DefaultFormat.entries.map { it.name to optionLabel(it) },
            current.defaultFormat.name
        ) { name -> viewModel.setFormat(DefaultFormat.valueOf(name)); dialog = null }

        SettingsDialog.LOCATION -> ChoiceDialog(
            stringResource(R.string.download_location),
            DownloadLocation.entries.map { it.name to optionLabel(it) },
            current.location.name
        ) { name -> viewModel.setLocation(DownloadLocation.valueOf(name)); dialog = null }

        null -> Unit
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_history_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory(); confirmClear = false }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private enum class SettingsDialog { THEME, QUALITY, FORMAT, LOCATION }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun Divider() = HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

@Composable
private fun SettingsRow(
    title: String,
    value: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                value?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(selected = key == selected, onClick = { onSelect(key) })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(selected) }) { Text(stringResource(R.string.done)) } }
    )
}

@Composable
private fun optionLabel(value: ThemeMode): String = stringResource(
    when (value) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

@Composable
private fun optionLabel(value: DefaultQuality): String = when (value) {
    DefaultQuality.AUTO -> stringResource(R.string.quality_auto)
    DefaultQuality.HIGHEST -> stringResource(R.string.quality_highest)
    DefaultQuality.P720 -> "720p"
    DefaultQuality.P480 -> "480p"
    DefaultQuality.LOWEST -> stringResource(R.string.quality_lowest)
}

@Composable
private fun optionLabel(value: DefaultFormat): String = when (value) {
    DefaultFormat.AUTO -> stringResource(R.string.format_auto)
    DefaultFormat.MP4 -> "MP4"
    DefaultFormat.M4A -> "M4A"
    DefaultFormat.MP3 -> "MP3"
}

@Composable
private fun optionLabel(value: DownloadLocation): String = stringResource(
    when (value) {
        DownloadLocation.DOWNLOADS_APP_FOLDER -> R.string.location_downloads
        DownloadLocation.PUBLIC_MEDIA_FOLDERS -> R.string.location_movies_music
    }
)
