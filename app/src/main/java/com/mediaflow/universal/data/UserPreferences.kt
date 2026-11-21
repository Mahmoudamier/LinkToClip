package com.mediaflow.universal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("mediaflow_preferences")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class DownloadLocation { DOWNLOADS_APP_FOLDER, PUBLIC_MEDIA_FOLDERS }
enum class DefaultFormat { AUTO, MP4, M4A, MP3 }
enum class DefaultQuality { AUTO, HIGHEST, P720, P480, LOWEST }

data class UserPrefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultFormat: DefaultFormat = DefaultFormat.AUTO,
    val defaultQuality: DefaultQuality = DefaultQuality.HIGHEST,
    val location: DownloadLocation = DownloadLocation.DOWNLOADS_APP_FOLDER,
    val wifiOnly: Boolean = false
)

class UserPreferences(private val context: Context) {

    val flow: Flow<UserPrefs> = context.dataStore.data.map { prefs ->
        UserPrefs(
            theme = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            defaultFormat = prefs[KEY_FORMAT]?.let { runCatching { DefaultFormat.valueOf(it) }.getOrNull() }
                ?: DefaultFormat.AUTO,
            defaultQuality = prefs[KEY_QUALITY]?.let { runCatching { DefaultQuality.valueOf(it) }.getOrNull() }
                ?: DefaultQuality.HIGHEST,
            location = prefs[KEY_LOCATION]?.let { runCatching { DownloadLocation.valueOf(it) }.getOrNull() }
                ?: DownloadLocation.DOWNLOADS_APP_FOLDER,
            wifiOnly = prefs[KEY_WIFI_ONLY] ?: false
        )
    }

    suspend fun setTheme(value: ThemeMode) = edit(KEY_THEME, value.name)
    suspend fun setFormat(value: DefaultFormat) = edit(KEY_FORMAT, value.name)
    suspend fun setQuality(value: DefaultQuality) = edit(KEY_QUALITY, value.name)
    suspend fun setLocation(value: DownloadLocation) = edit(KEY_LOCATION, value.name)
    suspend fun setWifiOnly(value: Boolean) = edit(KEY_WIFI_ONLY, value)

    private suspend fun edit(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun edit(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_FORMAT = stringPreferencesKey("default_format")
        private val KEY_QUALITY = stringPreferencesKey("default_quality")
        private val KEY_LOCATION = stringPreferencesKey("download_location")
        private val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
    }
}
