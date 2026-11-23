package com.vladisx.youtubedownloader.data

/**
 * A single selectable stream reported by yt-dlp.
 */
data class MediaFormat(
    val formatId: String,
    val extension: String,
    val resolutionLabel: String?,
    val width: Int?,
    val height: Int?,
    val fps: Int?,
    val vcodec: String?,
    val acodec: String?,
    val filesizeBytes: Long?,
    val tbr: Double?,
    val note: String?,
) {
    val hasVideo: Boolean get() = !vcodec.isNullOrBlank() && vcodec != "none"
    val hasAudio: Boolean get() = !acodec.isNullOrBlank() && acodec != "none"
    val isProgressive: Boolean get() = hasVideo && hasAudio

    fun displayTitle(): String {
        val res = resolutionLabel ?: "video"
        return if (isProgressive) "$res · відео+аудіо · ${extension.uppercase()}"
        else "$res · DASH · ${extension.uppercase()}"
    }

    fun estimatedSizeBytes(durationSeconds: Double?): Long? {
        filesizeBytes?.let { return it }
        val bitrate = tbr ?: return null
        val duration = durationSeconds ?: return null
        return ((bitrate * 1000.0) / 8.0 * duration).toLong()
    }
}

/**
 * Full metadata for a fetched URL.
 */
data class MediaInfo(
    val id: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Double?,
    val thumbnailUrl: String?,
    val webpageUrl: String,
    val isLive: Boolean,
    val formats: List<MediaFormat>,
) {
    /**
     * Always offer standard high resolutions so the user can download
     * up to 4K regardless of what yt-dlp's initial JSON dump reported.
     */
    val videoQualities: List<VideoQuality>
        get() {
            val standardHeights = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)
            return standardHeights.map { h ->
                VideoQuality(
                    height = h,
                    fps = if (h >= 1080) 60 else 30,
                    formatId = "bestvideo[height<=$h]+bestaudio/best[height<=$h]/best",
                    isMergeRequired = true,
                    estimatedBytes = durationSeconds?.let { (it * 500_000 * (h / 360.0)).toLong() },
                    extension = "mp4",
                )
            }
        }

    val maxHeight: Int? get() = 2160
    val videoFormats: List<MediaFormat> get() = formats
}

/**
 * A user-selectable video quality ("1080p", "4K", …).
 */
data class VideoQuality(
    val height: Int,
    val fps: Int?,
    val formatId: String,
    val isMergeRequired: Boolean,
    val estimatedBytes: Long?,
    val extension: String,
) {
    val label: String
        get() = when (height) {
            2160 -> "4K (2160p)"
            1440 -> "2K (1440p)"
            else -> "${height}p"
        }
}
