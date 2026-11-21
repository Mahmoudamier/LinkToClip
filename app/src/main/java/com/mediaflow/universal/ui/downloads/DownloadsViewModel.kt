package com.mediaflow.universal.ui.downloads

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediaflow.universal.R
import com.mediaflow.universal.data.AppDatabase
import com.mediaflow.universal.data.DownloadEntity
import com.mediaflow.universal.download.DownloadCoordinator
import com.mediaflow.universal.download.FileStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DownloadsEffect {
    data class Open(val intent: Intent) : DownloadsEffect
    data class Share(val intent: Intent) : DownloadsEffect
    data class Message(val messageRes: Int) : DownloadsEffect
}

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).downloadDao()
    private val coordinator = DownloadCoordinator(application)
    private val fileStore = FileStore(application)

    val active = dao.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = dao.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _effects = MutableSharedFlow<DownloadsEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<DownloadsEffect> = _effects.asSharedFlow()

    private val _renameTarget = MutableStateFlow<DownloadEntity?>(null)
    val renameTarget: StateFlow<DownloadEntity?> = _renameTarget

    fun pause(item: DownloadEntity) = viewModelScope.launch { coordinator.pause(item.id) }
    fun resume(item: DownloadEntity) = viewModelScope.launch { coordinator.resume(item.id) }
    fun cancel(item: DownloadEntity) = viewModelScope.launch { coordinator.cancel(item.id) }

    fun retry(item: DownloadEntity) = viewModelScope.launch {
        val ok = coordinator.retry(item.id)
        _effects.tryEmit(DownloadsEffect.Message(if (ok) R.string.added_to_queue else R.string.error_extract))
    }

    fun delete(item: DownloadEntity) = viewModelScope.launch {
        coordinator.delete(item.id)
        _effects.tryEmit(DownloadsEffect.Message(R.string.deleted))
    }

    fun clearHistory() = viewModelScope.launch {
        coordinator.clearHistory()
        _effects.tryEmit(DownloadsEffect.Message(R.string.done))
    }

    fun open(item: DownloadEntity) {
        val intent = fileStore.openIntent(item)
        if (intent != null) _effects.tryEmit(DownloadsEffect.Open(intent))
        else _effects.tryEmit(DownloadsEffect.Message(R.string.error_deleted))
    }

    fun share(item: DownloadEntity) {
        val intent = fileStore.shareIntent(item)
        if (intent != null) _effects.tryEmit(DownloadsEffect.Share(intent))
        else _effects.tryEmit(DownloadsEffect.Message(R.string.error_deleted))
    }

    fun requestRename(item: DownloadEntity) {
        _renameTarget.value = item
    }

    fun dismissRename() {
        _renameTarget.value = null
    }

    fun rename(newName: String) = viewModelScope.launch {
        val target = _renameTarget.value ?: return@launch
        if (newName.isBlank()) return@launch
        val result = fileStore.rename(target, newName.trim())
        result.onSuccess { name ->
            dao.update(target.copy(fileName = name, outputPath = null, updatedAt = System.currentTimeMillis()))
            _effects.tryEmit(DownloadsEffect.Message(R.string.renamed))
        }.onFailure {
            _effects.tryEmit(DownloadsEffect.Message(R.string.error_download))
        }
        _renameTarget.value = null
    }
}
