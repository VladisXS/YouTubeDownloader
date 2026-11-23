package com.vladisx.youtubedownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vladisx.youtubedownloader.data.AudioCodec
import com.vladisx.youtubedownloader.data.CookieStore
import com.vladisx.youtubedownloader.data.DownloadKind
import com.vladisx.youtubedownloader.data.DownloadRequest
import com.vladisx.youtubedownloader.data.DownloadState
import com.vladisx.youtubedownloader.data.MediaInfo
import com.vladisx.youtubedownloader.data.VideoQuality
import com.vladisx.youtubedownloader.download.DownloadRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val url: String = "",
    val isLoadingInfo: Boolean = false,
    val infoError: String? = null,
    val media: MediaInfo? = null,
    val kind: DownloadKind = DownloadKind.VIDEO_WITH_AUDIO,
    val audioCodec: AudioCodec = AudioCodec.M4A,
    val initError: String? = null,
    /** User-picked video quality; null = automatic (best available). */
    val selectedQualityHeight: Int? = null,
) {
    /** Every resolution the current video can be downloaded at, best first. */
    val qualities: List<VideoQuality> get() = media?.videoQualities.orEmpty()

    /** The quality that is going to be downloaded (explicit pick or the best one). */
    val selectedQuality: VideoQuality?
        get() = selectedQualityHeight
            ?.let { h -> qualities.firstOrNull { it.height == h } }
            ?: qualities.firstOrNull()

    /** Highest resolution offered — used for the "up to Xp" badge. */
    val maxHeight: Int? get() = media?.maxHeight

    val canDownload: Boolean get() = media != null && selectedQuality != null
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val downloadState: StateFlow<DownloadState> = DownloadRepository.state

    /** Whether a YouTube cookie jar is present (unlocks 1080p+). */
    val signedIn: StateFlow<Boolean> = CookieStore.signedIn

    private var fetchJob: Job? = null

    init {
        CookieStore.refreshState(getApplication())
        viewModelScope.launch {
            DownloadRepository.manager.ensureInitialized()
            DownloadRepository.manager.initError.collect { err ->
                _ui.value = _ui.value.copy(initError = err)
            }
        }
    }

    /** Called by the login screen once the WebView cookies were captured. */
    fun onLoginFinished(success: Boolean) {
        if (!success) return
        // Metadata has to be re-fetched: with cookies YouTube reports a much
        // bigger format list (including the SABR-free high resolutions).
        val url = _ui.value.url.trim()
        if (url.isNotBlank()) fetchInfo(url)
    }

    fun signOut() {
        viewModelScope.launch {
            CookieStore.signOut(getApplication())
            val url = _ui.value.url.trim()
            if (url.isNotBlank()) fetchInfo(url)
        }
    }

    fun onUrlChange(value: String) {
        val urlChanged = value.trim() != _ui.value.url.trim()
        _ui.value = _ui.value.copy(url = value, infoError = null)

        // Only auto-resolve once the user typed/pasted a *complete* link.
        if (urlChanged) fetchJob?.cancel()
        if (!isCompleteVideoUrl(value)) return

        fetchJob = viewModelScope.launch {
            delay(600)
            fetchInfo(value.trim())
        }
    }

    /** Matches watch?v=ID, youtu.be/ID, shorts/ID and live/ID with a full id. */
    private fun isCompleteVideoUrl(value: String): Boolean {
        val trimmed = value.trim()
        if (!trimmed.startsWith("http", true)) return false
        return VIDEO_ID_REGEX.containsMatchIn(trimmed)
    }

    /** Called from the share intent / clipboard paste. */
    fun submitSharedUrl(value: String) {
        if (value.isBlank()) return
        _ui.value = _ui.value.copy(url = value.trim())
        fetchInfo(value.trim())
    }

    fun pasteAndFetch(value: String) = submitSharedUrl(value)

    fun fetchInfo(url: String) {
        if (url.isBlank()) return
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(
                isLoadingInfo = true,
                infoError = null,
                media = null,
                selectedQualityHeight = null,
            )
            DownloadRepository.manager.fetchInfo(url)
                .onSuccess { media ->
                    _ui.value = _ui.value.copy(
                        isLoadingInfo = false,
                        media = media,
                        infoError = null,
                        selectedQualityHeight = media.videoQualities.firstOrNull()?.height,
                    )
                }
                .onFailure { t ->
                    _ui.value = _ui.value.copy(
                        isLoadingInfo = false,
                        infoError = readableError(t),
                    )
                }
        }
    }

    fun onKindChange(kind: DownloadKind) {
        _ui.value = _ui.value.copy(kind = kind)
    }

    fun onAudioCodecChange(codec: AudioCodec) {
        _ui.value = _ui.value.copy(audioCodec = codec)
    }

    fun onQualityChange(height: Int) {
        _ui.value = _ui.value.copy(selectedQualityHeight = height)
    }

    fun startDownload(onStart: (DownloadRequest) -> Unit) {
        val state = _ui.value
        val media = state.media ?: return
        val quality = state.selectedQuality ?: return

        val request = DownloadRequest(
            url = media.webpageUrl.ifBlank { state.url },
            title = media.title,
            kind = state.kind,
            videoFormatId = if (state.kind == DownloadKind.AUDIO_ONLY) null else quality.formatId,
            qualityLabel = when (state.kind) {
                DownloadKind.AUDIO_ONLY -> state.audioCodec.label
                else -> quality.label
            },
            audioCodec = state.audioCodec,
            targetHeight = quality.height,
            mergeWithAudio = quality.isMergeRequired,
        )

        DownloadRepository.start(request)
        onStart(request)
    }

    fun cancelDownload() = DownloadRepository.cancel()

    fun dismissResult() = DownloadRepository.reset()

    private fun readableError(t: Throwable): String {
        val raw = t.message.orEmpty()
        return when {
            raw.contains("Unsupported URL", true) -> "Це посилання не підтримується"
            raw.contains("Video unavailable", true) -> "Відео недоступне"
            raw.contains("Private video", true) -> "Це приватне відео"
            raw.contains("Sign in", true) -> "YouTube вимагає вхід для цього відео"
            raw.contains("network", true) || raw.contains("timed out", true) ->
                "Проблема з мережею. Перевірте інтернет"
            raw.isBlank() -> t.javaClass.simpleName
            else -> raw.lines().first().take(160)
        }
    }

    private companion object {
        val VIDEO_ID_REGEX = Regex(
            """(?:[?&]v=|youtu\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})"""
        )
    }
}
