package com.vladisx.youtubedownloader.data

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 1080p MP4 focused manager using the `android_vr` client.
 *
 * Why this works: `android_vr` yields direct 1080p H.264 streams and AAC audio
 * that merge cleanly into a standard .mp4 container without complex DASH logic
 * or PO tokens.
 */
class YtDlpManager(private val appContext: Context) {

    private val initMutex = Mutex()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    val downloadDir: File by lazy {
        val public = File("/storage/emulated/0/Download/YouTubeDownloader")
        val fallback = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "YouTubeDownloader")
        if (public.exists() || public.mkdirs()) public else fallback.also { it.mkdirs() }
    }

    suspend fun ensureInitialized() {
        if (_ready.value) return
        initMutex.withLock {
            if (_ready.value) return
            withContext(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().init(appContext)
                    FFmpeg.getInstance().init(appContext)
                    downloadDir.mkdirs()
                    _ready.value = true
                    _initError.value = null
                } catch (t: Throwable) {
                    Log.e(TAG, "init failed", t)
                    _initError.value = t.message ?: t.javaClass.simpleName
                }
            }
        }
    }

    suspend fun fetchInfo(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialized()
            check(_ready.value) { _initError.value ?: "Не вдалося ініціалізувати yt-dlp" }

            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-warnings")
                addOption("--no-playlist")
                addOption("--socket-timeout", "20")
                addOption("--no-check-certificates")
                CookieStore.cookiesFile(appContext)?.let { addOption("--cookies", it.absolutePath) }
            }

            val response = YoutubeDL.getInstance().execute(request)
            if (response.exitCode != 0 || response.out.isNullOrBlank()) {
                error(response.err?.takeIf { it.isNotBlank() } ?: "yt-dlp завершився з кодом ${response.exitCode}")
            }
            parseInfo(response.out)
        }
    }

    private fun parseInfo(raw: String): MediaInfo {
        val json = raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)
        val root = JSONObject(json)

        val formats = mutableListOf<MediaFormat>()
        root.optJSONArray("formats")?.let { array ->
            for (i in 0 until array.length()) {
                val f = array.optJSONObject(i) ?: continue
                val note = f.optString("format_note", "")
                val ext = f.optString("ext", "")
                if (note.contains("storyboard", true) || ext == "mhtml") continue

                val vcodec = f.optString("vcodec", "none").takeIf { it.isNotBlank() }
                val acodec = f.optString("acodec", "none").takeIf { it.isNotBlank() }
                val height = f.optInt("height", 0).takeIf { it > 0 }

                formats += MediaFormat(
                    formatId = f.optString("format_id"),
                    extension = ext,
                    resolutionLabel = height?.let { "${it}p" },
                    width = f.optInt("width", 0).takeIf { it > 0 },
                    height = height,
                    fps = f.optInt("fps", 0).takeIf { it > 0 },
                    vcodec = vcodec,
                    acodec = acodec,
                    filesizeBytes = f.optLong("filesize", 0L).takeIf { it > 0 }
                        ?: f.optLong("filesize_approx", 0L).takeIf { it > 0 },
                    tbr = f.optDouble("tbr", 0.0).takeIf { it > 0.0 },
                    note = note.takeIf { it.isNotBlank() },
                )
            }
        }

        return MediaInfo(
            id = root.optString("id"),
            title = root.optString("title", "Без назви"),
            uploader = root.optString("uploader", "").takeIf { it.isNotBlank() },
            durationSeconds = root.optDouble("duration", 0.0).takeIf { it > 0.0 },
            thumbnailUrl = root.optString("thumbnail", "").takeIf { it.isNotBlank() },
            webpageUrl = root.optString("webpage_url", ""),
            isLive = root.optBoolean("is_live", false),
            formats = formats,
        )
    }

    fun downloadBlocking(
        request: DownloadRequest,
        cancelled: AtomicBoolean,
        onState: (DownloadState) -> Unit,
    ): File {
        ensureInitializedBlocking()
        check(_ready.value) { _initError.value ?: "yt-dlp не готовий" }

        val outputTemplate = File(downloadDir, "%(title).180B [%(id)s].%(ext)s").absolutePath

        val ytRequest = YoutubeDLRequest(request.url).apply {
            addOption("--no-mtime")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--newline")
            addOption("--no-check-certificates")
            addOption("--retries", "10")
            addOption("--fragment-retries", "10")
            addOption("--socket-timeout", "20")
            addOption("-o", outputTemplate)
            CookieStore.cookiesFile(appContext)?.let { addOption("--cookies", it.absolutePath) }
            addOption("--ffmpeg-location", ffmpegLocation())

            // Request the absolute highest quality video + audio streams available
            addOption("-f", "bestvideo+bestaudio/best")
            addOption("--merge-output-format", "mp4")
            addOption("--remux-video", "mp4")
        }

        onState(DownloadState.Running(0.0, 0L, 0L, null, null, "Завантаження…"))
        Log.i(TAG, "cmd: ${ytRequest.buildCommand().joinToString(" ")}")

        var lastEmitted = 0L
        var totalBytes = 0L
        var writtenBytes = 0L
        var reportedPath: String? = null
        var mergerSeen = false

        val response = YoutubeDL.getInstance().execute(ytRequest, null) { _, etaSeconds, line ->
            if (cancelled.get()) throw InterruptedException("Скасовано")

            if (!line.isNullOrBlank()) {
                if (line.contains("[Merger]") || line.contains("[ffmpeg]")) mergerSeen = true

                val pct = DOWNLOAD_PERCENT.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                parseHumanSize(line)?.let { totalBytes = it }
                if (pct != null && totalBytes > 0) {
                    writtenBytes = (totalBytes * pct / 100.0).toLong()
                }

                reportedPath = when {
                    line.contains("[Merger] Merging formats into") ->
                        line.substringAfter("into \"").substringBefore("\"")
                    line.contains("[ExtractAudio] Destination:") ||
                        line.contains("[VideoRemuxer] Destination:") ->
                        line.substringAfter("Destination:").trim()
                    line.contains("[download] Destination:") ->
                        line.substringAfter("Destination:").trim()
                    else -> reportedPath
                }
            }

            val now = System.currentTimeMillis()
            if (now - lastEmitted >= 250L) {
                lastEmitted = now
                val percent = DOWNLOAD_PERCENT.find(line.orEmpty())
                    ?.groupValues?.get(1)?.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0

                val stage = when {
                    mergerSeen -> "Злиття відео та аудіо…"
                    percent >= 99.5 -> "Фіналізація…"
                    else -> "Завантаження…"
                }

                onState(
                    DownloadState.Running(
                        percent = percent,
                        downloadedBytes = writtenBytes,
                        totalBytes = totalBytes,
                        speed = extractSpeed(line),
                        eta = etaSeconds?.takeIf { it > 0 }?.let(::formatEta),
                        stage = stage,
                    )
                )
            }
        }

        onState(DownloadState.Merging())

        if (response.exitCode != 0) {
            Log.e(TAG, "exit=${response.exitCode} err=${response.err}")
            error(response.err?.takeIf { it.isNotBlank() } ?: "yt-dlp завершився з кодом ${response.exitCode}")
        }

        return resolveFinalFile(request, reportedPath)
            ?: error("Не вдалося знайти завантажений файл")
    }

    private fun ensureInitializedBlocking() {
        if (_ready.value) return
        try {
            YoutubeDL.getInstance().init(appContext)
            FFmpeg.getInstance().init(appContext)
            downloadDir.mkdirs()
            _ready.value = true
        } catch (t: Throwable) {
            Log.e(TAG, "init failed", t)
            _initError.value = t.message ?: t.javaClass.simpleName
        }
    }

    private fun resolveFinalFile(request: DownloadRequest, reportedPath: String?): File? {
        val preferredExt = when (request.kind) {
            DownloadKind.AUDIO_ONLY -> if (request.audioCodec == AudioCodec.MP3) "mp3" else "m4a"
            else -> "mp4"
        }

        reportedPath?.let { path ->
            val f = File(path)
            if (f.exists() && f.isFile) return f
        }

        val candidates = downloadDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        return candidates.firstOrNull { it.extension.equals(preferredExt, true) }
            ?: candidates.firstOrNull()
    }

    private fun extractSpeed(line: String?): String? {
        if (line == null) return null
        return Regex("""at\s+([\d.]+\s*[KMGT]?i?B/s)""").find(line)?.groupValues?.get(1)
    }

    private fun parseHumanSize(line: String): Long? {
        val raw = Regex("""of\s+([\d.]+\s*[KMGT]?i?B)""").find(line)?.groupValues?.get(1) ?: return null
        val m = Regex("""([\d.]+)\s*([KMGT]?)i?B""").find(raw) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (m.groupValues[2]) {
            "K" -> 1024.0
            "M" -> 1024.0 * 1024
            "G" -> 1024.0 * 1024 * 1024
            "T" -> 1024.0 * 1024 * 1024 * 1024
            else -> 1.0
        }
        return (value * multiplier).toLong()
    }

    private fun formatEta(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun ffmpegLocation(): String = appContext.filesDir.absolutePath

    private companion object {
        const val TAG = "YtDlpManager"
        val DOWNLOAD_PERCENT = Regex("""\[download]\s+([\d.]+)%""")
    }
}
