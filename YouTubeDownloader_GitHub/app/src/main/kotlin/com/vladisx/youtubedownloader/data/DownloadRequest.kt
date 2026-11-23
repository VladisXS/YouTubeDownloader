package com.vladisx.youtubedownloader.data

/** What the user wants to pull out of a video. */
enum class DownloadKind {
    AUDIO_ONLY,      // mp3 / m4a
    VIDEO_NO_AUDIO,  // raw video stream, no audio track
    VIDEO_WITH_AUDIO // video + audio muxed by ffmpeg
}

/** Container/codec preference for audio downloads. */
enum class AudioCodec(val label: String) {
    MP3("MP3"),
    M4A("M4A")
}

/**
 * Everything needed to start one download job.
 */
data class DownloadRequest(
    val url: String,
    val title: String,
    val kind: DownloadKind,
    /**
     * yt-dlp format id of the *video* stream chosen in the UI.
     * Null means "let yt-dlp pick the best" (`bv*+ba/b`).
     */
    val videoFormatId: String? = null,
    /** Human label shown in the UI ("1080p · MP4"). */
    val qualityLabel: String? = null,
    val audioCodec: AudioCodec = AudioCodec.M4A,
    /** Height cap used when only a progressive stream is available. */
    val targetHeight: Int? = null,
    /**
     * True when the chosen video stream has no audio and must be merged
     * with the best audio-only stream (DASH).
     */
    val mergeWithAudio: Boolean = true,
    /**
     * `player_client` list used when the metadata was fetched.
     * The download reuses it first so the DASH URL signatures match.
     */
    val sourceClients: String? = null,
    /**
     * Resolutions the metadata says are downloadable, best first.
     * Used to degrade gracefully when the top quality answers 403.
     */
    val availableHeights: List<Int>? = null,
    /** YouTube video id, needed to mint a per-video PO token. */
    val videoId: String? = null,
)
