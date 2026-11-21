package com.mediaflow.universal.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediaflow.universal.data.DefaultFormat
import com.mediaflow.universal.data.DefaultQuality
import com.mediaflow.universal.data.DownloadLocation
import com.mediaflow.universal.data.ThemeMode
import com.mediaflow.universal.data.UserPreferences
import com.mediaflow.universal.download.DownloadCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = UserPreferences(application)
    private val coordinator = DownloadCoordinator(application)

    val prefs = preferences.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTheme(value: ThemeMode) = viewModelScope.launch { preferences.setTheme(value) }
    fun setFormat(value: DefaultFormat) = viewModelScope.launch { preferences.setFormat(value) }
    fun setQuality(value: DefaultQuality) = viewModelScope.launch { preferences.setQuality(value) }
    fun setLocation(value: DownloadLocation) = viewModelScope.launch { preferences.setLocation(value) }
    fun setWifiOnly(value: Boolean) = viewModelScope.launch { preferences.setWifiOnly(value) }
    fun clearHistory() = viewModelScope.launch { coordinator.clearHistory() }
}
