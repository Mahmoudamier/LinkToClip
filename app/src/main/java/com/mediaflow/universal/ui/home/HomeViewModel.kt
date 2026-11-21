package com.mediaflow.universal.ui.home

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediaflow.universal.R
import com.mediaflow.universal.core.AppException
import com.mediaflow.universal.core.ErrorKind
import com.mediaflow.universal.core.UrlDetector
import com.mediaflow.universal.data.DefaultFormat
import com.mediaflow.universal.data.DefaultQuality
import com.mediaflow.universal.data.UserPreferences
import com.mediaflow.universal.domain.FormatSelector
import com.mediaflow.universal.domain.MediaInfo
import com.mediaflow.universal.domain.MediaKind
import com.mediaflow.universal.domain.MediaOption
import com.mediaflow.universal.domain.PlaylistInfo
import com.mediaflow.universal.download.DownloadCoordinator
import com.mediaflow.universal.extraction.MediaAnalyzer
import com.mediaflow.universal.extraction.PlaylistExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber

data class HomeUiState(
    val url: String = "",
    val platform: UrlDetector.Platform? = null,
    val media: MediaInfo? = null,
    val playlist: PlaylistInfo? = null,
    val playlistKind: MediaKind = MediaKind.VIDEO,
    val playlistExisting: Int = 0,
    val playlistAdded: Int = 0,
    val playlistTotal: Int = 0,
    val loading: Boolean = false,
    val errorRes: Int? = null,
    val errorDetail: String? = null,
    val clipboardSuggestion: String? = null,
    val selectedKind: MediaKind = MediaKind.VIDEO,
    val selectedOptionId: String? = null,
    val busy: Boolean = false,
    val messageRes: Int? = null,
    val messageDetail: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val analyzer = MediaAnalyzer()
    private val coordinator = DownloadCoordinator(application)
    private val preferences = UserPreferences(application)
    private val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    fun setUrl(value: String) {
        val trimmed = value.trim()
        val detection = UrlDetector.detect(trimmed)
        _state.update {
            it.copy(
                url = value,
                platform = (detection as? UrlDetector.Result.Detected)?.platform,
                media = null,
                playlist = null,
                playlistKind = MediaKind.VIDEO,
                playlistExisting = 0,
                playlistAdded = 0,
                playlistTotal = 0,
                errorRes = null,
                errorDetail = null,
                clipboardSuggestion = null
            )
        }
    }

    fun checkClipboard() {
        val text = runCatching { clipboard?.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
        val candidate = text?.takeIf { UrlDetector.isSupportedLink(it) && it.trim() != _state.value.url.trim() }
        _state.update { it.copy(clipboardSuggestion = candidate) }
    }

    fun clearClipboardSuggestion() = _state.update { it.copy(clipboardSuggestion = null) }

    fun clearError() = _state.update { it.copy(errorRes = null, errorDetail = null) }

    fun clearMessage() = _state.update { it.copy(messageRes = null, messageDetail = null) }

    fun clear() = _state.update {
        HomeUiState(
            url = "",
            clipboardSuggestion = it.clipboardSuggestion,
            playlistKind = it.playlistKind
        )
    }

    /** Playlists can be downloaded as video or audio; the user decides, like a single item. */
    fun selectPlaylistKind(kind: MediaKind) = viewModelScope.launch {
        val playlist = _state.value.playlist ?: return@launch
        val existing = coordinator.findExistingUrls(
            playlist.items.map { it.url },
            kind
        )
        _state.update { it.copy(playlistKind = kind, playlistExisting = existing.size) }
    }

    /**
     * Adds the playlist's missing items using the saved default quality/format.
     * Analysis and enqueue run in small parallel batches so a long playlist is queued in
     * seconds instead of minutes, and already-added/active/deleted rows are never duplicated.
     */
    fun downloadPlaylist(onEnqueued: () -> Unit = {}) = viewModelScope.launch {
        val playlist = _state.value.playlist ?: return@launch
        if (_state.value.busy) return@launch
        val prefs = preferences.flow.first()
        val requestedKind = _state.value.playlistKind
        val allUrls = playlist.items.map { it.url }
        val existing = coordinator.findExistingUrls(allUrls, requestedKind)
        val candidates = playlist.items.filterNot { it.url in existing }.take(50)
        if (candidates.isEmpty()) {
            _state.update { it.copy(messageRes = R.string.playlist_all_added) }
            return@launch
        }

        _state.update {
            it.copy(
                busy = true, errorRes = null, errorDetail = null,
                messageRes = null, messageDetail = null,
                playlistAdded = 0, playlistTotal = candidates.size,
                playlistExisting = existing.size
            )
        }

        val permits = Semaphore(3)
        val results = withContext(Dispatchers.IO) {
            candidates.map { item ->
                async {
                    permits.withPermit {
                        val info = runCatching { analyzer.analyze(item.url, playlist.platform) }.getOrNull()
                            ?: return@withPermit false
                        // Honour the user's choice, but fall back to whichever type this
                        // particular item actually offers (e.g. audio-only SoundCloud sets).
                        val kind = when {
                            optionsFor(info, requestedKind).isNotEmpty() -> requestedKind
                            info.videoOptions.isNotEmpty() -> MediaKind.VIDEO
                            else -> MediaKind.AUDIO
                        }
                        val options = optionsFor(info, kind)
                        val selected = chooseDefault(options, prefs.defaultQuality, prefs.defaultFormat, kind)
                            ?: options.firstOrNull() ?: return@withPermit false
                        val queued = runCatching { coordinator.enqueue(info, selected) }.isSuccess
                        _state.update { it.copy(playlistAdded = it.playlistAdded + 1) }
                        queued
                    }
                }
            }.awaitAll()
        }

        val added = results.count { it }
        val skipped = results.size - added
        val stillExisting = coordinator.findExistingUrls(allUrls, requestedKind).size
        val detail = buildString {
            if (skipped > 0) {
                append(getApplication<Application>().getString(R.string.playlist_skipped, skipped))
            }
            if (stillExisting > 0 && added == 0) {
                if (isNotEmpty()) append(" · ")
                append(getApplication<Application>().getString(R.string.playlist_already_added, stillExisting))
            }
        }.takeIf { it.isNotBlank() }
        _state.update {
            it.copy(
                busy = false,
                messageRes = if (added > 0) R.string.playlist_added else R.string.playlist_all_added,
                messageDetail = detail,
                playlistExisting = stillExisting,
                playlistAdded = 0,
                playlistTotal = 0
            )
        }
        if (added > 0) onEnqueued()
    }

    fun analyze() = viewModelScope.launch {
        val detection = UrlDetector.detect(_state.value.url)
        if (detection !is UrlDetector.Result.Detected) {
            val res = if (detection is UrlDetector.Result.Invalid) ErrorKind.INVALID_LINK.messageRes
            else ErrorKind.UNSUPPORTED_LINK.messageRes
            _state.update { it.copy(errorRes = res, errorDetail = null, media = null) }
            return@launch
        }

        _state.update {
            it.copy(
                loading = true, errorRes = null, errorDetail = null,
                platform = detection.platform, media = null, playlist = null
            )
        }
        if (detection.isPlaylist) {
            try {
                val playlist = PlaylistExtractor.analyze(detection.url, detection.platform)
                val kind = _state.value.playlistKind
                val existing = coordinator.findExistingUrls(playlist.items.map { it.url }, kind).size
                _state.update {
                    it.copy(
                        loading = false, playlist = playlist, playlistExisting = existing,
                        playlistAdded = 0, playlistTotal = 0
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Playlist analysis failed")
                val kind = (e as? AppException)?.kind ?: ErrorKind.EXTRACT
                _state.update {
                    it.copy(loading = false, errorRes = kind.messageRes, errorDetail = e.message?.take(240))
                }
            }
            return@launch
        }
        try {
            val info = analyzer.analyze(detection.url, detection.platform)
            val prefs = preferences.flow.first()
            val kind = _state.value.selectedKind
            val options = optionsFor(info, kind)
            val selected = chooseDefault(options, prefs.defaultQuality, prefs.defaultFormat, kind)
            _state.update {
                it.copy(
                    loading = false,
                    media = info,
                    platform = detection.platform,
                    selectedOptionId = selected?.id ?: options.firstOrNull()?.id
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Analysis failed")
            val kind = (e as? AppException)?.kind ?: ErrorKind.EXTRACT
            _state.update {
                it.copy(
                    loading = false,
                    media = null,
                    playlist = null,
                    errorRes = kind.messageRes,
                    errorDetail = e.message?.take(240)
                )
            }
        }
    }

    fun selectKind(kind: MediaKind) = viewModelScope.launch {
        val media = _state.value.media ?: return@launch
        val prefs = preferences.flow.first()
        val options = optionsFor(media, kind)
        val selected = chooseDefault(options, prefs.defaultQuality, prefs.defaultFormat, kind)
        _state.update { it.copy(selectedKind = kind, selectedOptionId = selected?.id ?: options.firstOrNull()?.id) }
    }

    fun selectOption(id: String) = _state.update { it.copy(selectedOptionId = id) }

    fun download(onEnqueued: () -> Unit = {}) = viewModelScope.launch {
        val stateValue = _state.value
        val media = stateValue.media ?: return@launch
        val options = optionsFor(media, stateValue.selectedKind)
        val option = options.firstOrNull { it.id == stateValue.selectedOptionId }
        if (option == null) {
            _state.update { it.copy(errorRes = R.string.select_format) }
            return@launch
        }
        _state.update { it.copy(busy = true, errorRes = null) }
        try {
            coordinator.enqueue(media, option)
            _state.update { it.copy(busy = false, messageRes = R.string.added_to_queue) }
            onEnqueued()
        } catch (e: Exception) {
            val kind = (e as? AppException)?.kind ?: ErrorKind.DOWNLOAD
            _state.update { it.copy(busy = false, errorRes = kind.messageRes, errorDetail = null) }
        }
    }

    private fun optionsFor(media: MediaInfo, kind: MediaKind): List<MediaOption> =
        if (kind == MediaKind.VIDEO) media.videoOptions else media.audioOptions

    private fun chooseDefault(
        options: List<MediaOption>,
        quality: DefaultQuality,
        format: DefaultFormat,
        kind: MediaKind
    ): MediaOption? = FormatSelector.choose(options, quality, format, kind)
}
