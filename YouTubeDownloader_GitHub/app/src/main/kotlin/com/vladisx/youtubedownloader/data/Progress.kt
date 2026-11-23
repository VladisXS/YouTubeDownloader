package com.vladisx.youtubedownloader.data

/** State machine for a single download job. */
sealed interface DownloadState {
    data object Idle : DownloadState

    data class Preparing(val message: String = "Аналіз посилання…") : DownloadState

    data class Running(
        val percent: Double,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speed: String?,
        val eta: String?,
        val stage: String,
    ) : DownloadState

    data class Merging(val message: String = "Обробка ffmpeg…") : DownloadState

    data class Completed(val filePath: String, val displayName: String) : DownloadState {
        /** "Download" style path derived from the real absolute path. */
        val publicPath: String get() = filePath.substringAfter("/Download/", filePath)
    }

    data class Failed(val message: String) : DownloadState
}
