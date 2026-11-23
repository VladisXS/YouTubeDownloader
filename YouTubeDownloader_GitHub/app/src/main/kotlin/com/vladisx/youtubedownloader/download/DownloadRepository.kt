package com.vladisx.youtubedownloader.download

import com.vladisx.youtubedownloader.YtDlApp
import com.vladisx.youtubedownloader.data.DownloadRequest
import com.vladisx.youtubedownloader.data.DownloadState
import com.vladisx.youtubedownloader.data.YtDlpManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single source of truth for the *current* download job.
 * Survives screen rotations because it lives in the Application scope.
 */
object DownloadRepository {

    val manager: YtDlpManager get() = YtDlApp.instance.ytDlp

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val _request = MutableStateFlow<DownloadRequest?>(null)
    val request: StateFlow<DownloadRequest?> = _request.asStateFlow()

    private val cancelFlag = AtomicBoolean(false)

    val isCancelled: Boolean get() = cancelFlag.get()

    fun start(request: DownloadRequest) {
        cancelFlag.set(false)
        _request.value = request
        _state.value = DownloadState.Preparing()
    }

    fun cancel() {
        cancelFlag.set(true)
        _state.value = DownloadState.Failed("Скасовано")
    }

    internal fun publish(state: DownloadState) {
        _state.value = state
    }

    internal fun finishWith(state: DownloadState) {
        _state.value = state
        _request.value = null
    }

    fun reset() {
        if (_state.value is DownloadState.Running || _state.value is DownloadState.Preparing) return
        _state.value = DownloadState.Idle
        _request.value = null
    }
}
