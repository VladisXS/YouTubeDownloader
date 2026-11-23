package com.vladisx.youtubedownloader.potoken

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * App-level cache in front of [PoTokenWebView].
 *
 * Minting a token costs a BotGuard run (a few seconds), so results are cached
 * per identifier. The "streaming" token (bound to the visitor data) is minted
 * once and reused for the lifetime of the WebView.
 */
object PoTokenManager {

    private const val TAG = "PoTokenManager"

    /** What yt-dlp needs to talk to YouTube as a trusted web client. */
    data class PotSession(
        /** `visitor_data` value for `--extractor-args`. */
        val visitorData: String,
        /** GVS token bound to the visitor data. */
        val streamingToken: String,
    ) {
        /**
         * Builds the `--extractor-args youtube:...` value with the PO token.
         *
         * The `player_client` must NOT be repeated here: it is already sent as a
         * separate `--extractor-args` entry, and yt-dlp does not merge duplicate
         * `youtube:` keys, it lets the last one win.
         */
        fun extractorArgs(playerPot: String? = null): String = buildString {
            append("youtube:po_token=web.gvs+").append(streamingToken)
            if (playerPot != null) append(",web.player+").append(playerPot)
            append(";visitor_data=").append(visitorData)
            // Tell yt-dlp to request all available formats (including AV1/VP9)
            // rather than filtering out high-res streams.
            append(";include_live_chat=false")
        }
    }

    private val mutex = Mutex()
    private var webView: PoTokenWebView? = null
    private var session: PotSession? = null

    private val playerTokenCache = ConcurrentHashMap<String, String>()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    sealed interface Status {
        data object Idle : Status
        data object Preparing : Status
        data class Ready(val visitorData: String) : Status
        data class Failed(val message: String) : Status
    }

    /**
     * Returns a usable session, initialising BotGuard on first use.
     * Returns null when the WebView could not produce tokens.
     */
    suspend fun session(context: Context): PotSession? = mutex.withLock {
        val existing = session
        val view = webView
        if (existing != null && view != null && !view.isExpired()) return existing

        _status.value = Status.Preparing
        try {
            view?.let { old -> withContext(Dispatchers.Main) { old.close() } }
            webView = null
            session = null
            playerTokenCache.clear()

            val fresh = PoTokenWebView.create(context)

            // The streaming token must be minted before any player token and is
            // bound to the visitor data reported by yt-dlp.
            val visitorData = fetchVisitorData(context)
            val streaming = withContext(Dispatchers.Main) {
                fresh.generatePoToken(visitorData)
            }

            val result = PotSession(visitorData, streaming)
            webView = fresh
            session = result
            _status.value = Status.Ready(visitorData)
            Log.i(TAG, "session ready, visitor=${visitorData.take(16)}…")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "failed to build PO token session", t)
            _status.value = Status.Failed(t.message ?: t.javaClass.simpleName)
            webView?.let { runCatching { it.close() } }
            webView = null
            session = null
            null
        }
    }

    /**
     * Per-video player token. Used for the `web.player` client binding; cached
     * because it is only valid for the video it was minted for.
     */
    suspend fun playerToken(videoId: String): String? {
        val view = webView ?: return null
        playerTokenCache[videoId]?.let { return it }

        return runCatching {
            val token = withContext(Dispatchers.Main) { view.generatePoToken(videoId) }
            playerTokenCache[videoId] = token
            token
        }.getOrElse { t ->
            Log.w(TAG, "player token failed for $videoId", t)
            null
        }
    }

    /** Drops all cached tokens; call when the user signs out. */
    suspend fun reset() = mutex.withLock {
        webView?.let { withContext(Dispatchers.Main) { it.close() } }
        webView = null
        session = null
        playerTokenCache.clear()
        _status.value = Status.Idle
    }

    /**
     * Returns YouTube's `visitorData` for this device.
     *
     * It normally lives in the `VISITOR_INFO1_LIVE` cookie, which the WebView
     * already holds after any youtube.com visit. Minting a random value also
     * works: YouTube links it to the token on first use.
     */
    private suspend fun fetchVisitorData(context: Context): String {
        val fromCookie = withContext(Dispatchers.IO) {
            runCatching {
                android.webkit.CookieManager.getInstance()
                    .getCookie("https://www.youtube.com")
                    ?.split(";")
                    ?.map { it.trim() }
                    ?.firstOrNull { it.startsWith("VISITOR_INFO1_LIVE=") }
                    ?.substringAfter("=")
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
        if (fromCookie != null) {
            Log.i(TAG, "visitorData from WebView cookie")
            return fromCookie
        }
        Log.i(TAG, "visitorData generated locally")
        return randomVisitorData()
    }

    /** 11 random bytes, base64 — the shape YouTube expects for visitor data. */
    private fun randomVisitorData(): String {
        val bytes = ByteArray(11).also { java.security.SecureRandom().nextBytes(it) }
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
