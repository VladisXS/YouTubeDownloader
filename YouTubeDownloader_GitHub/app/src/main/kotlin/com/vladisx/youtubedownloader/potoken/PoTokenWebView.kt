package com.vladisx.youtubedownloader.potoken

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Generates YouTube PO tokens using the device's own WebView.
 *
 * Why a WebView: YouTube's "BotGuard" is an obfuscated JS VM that only runs in a
 * real browser environment. Running it here means we get a valid integrity token
 * without shipping Node.js/Deno inside the APK.
 *
 * Flow:
 *   1. load `po_token.html` (BotGuard helpers) into an off-screen WebView
 *   2. POST `/api/jnn/v1/Create`  -> VM program + interpreter script
 *   3. run the VM in the page     -> botguard response
 *   4. POST `/api/jnn/v1/GenerateIT` -> integrity token (valid ~12h)
 *   5. mint a per-video `poToken` from the integrity token
 *
 * The resulting token is passed to yt-dlp as
 * `--extractor-args "youtube:po_token=web.gvs+<token>;visitor_data=<visitorData>"`.
 */
class PoTokenWebView private constructor(
    context: Context,
    private val generatorContinuation: Continuation<PoTokenWebView>,
) {

    private val webView = WebView(context)
    private val poTokenContinuations = mutableMapOf<String, Continuation<String>>()
    private lateinit var expirationInstant: Instant

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        onInitializationError(exception)
    }

    init {
        @SuppressLint("SetJavaScriptEnabled")
        webView.settings.apply {
            javaScriptEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
            userAgentString = USER_AGENT
            // BotGuard does not need network access: all requests are proxied
            // through OkHttp so the page itself stays offline.
            blockNetworkLoads = true
        }
        webView.addJavascriptInterface(this, JS_INTERFACE)
    }

    /** Loads the helper page and kicks off the BotGuard handshake. */
    private fun loadHtmlAndObtainBotguard(context: Context) {
        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            try {
                val html = context.assets.open("po_token.html")
                    .bufferedReader()
                    .use { it.readText() }

                withContext(Dispatchers.Main) {
                    webView.loadDataWithBaseURL(
                        "https://www.youtube.com",
                        html.replaceFirst(
                            "</script>",
                            "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"
                        ),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            } catch (e: Exception) {
                onInitializationError(e)
            }
        }
    }

    /** Called from the helper page once the BotGuard script is ready. */
    @JavascriptInterface
    fun downloadAndRunBotguard() {
        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            val responseBody = postBotguardService(
                "https://www.youtube.com/api/jnn/v1/Create",
                listOf(REQUEST_KEY),
            )
            val parsedChallengeData = JavascriptUtil.parseChallengeData(responseBody)

            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(
                    """
                    try {
                        data = $parsedChallengeData
                        runBotGuard(data).then(function (result) {
                            this.webPoSignalOutput = result.webPoSignalOutput
                            $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                        }, function (error) {
                            $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                        })
                    } catch (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    }
                    """.trimIndent(),
                    null,
                )
            }
        }
    }

    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        Log.e(TAG, "JS init error: $error")
        onInitializationError(Exception(error))
    }

    /** BotGuard finished; exchange its response for an integrity token. */
    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            val response = postBotguardService(
                "https://www.youtube.com/api/jnn/v1/GenerateIT",
                listOf(REQUEST_KEY, botguardResponse),
            )
            val (integrityToken, ttlSeconds) = JavascriptUtil.parseIntegrityTokenData(response)

            // Refresh 10 minutes before YouTube expires it.
            expirationInstant = Instant.now().plusSeconds(ttlSeconds - 600)

            withContext(Dispatchers.Main) {
                webView.evaluateJavascript("this.integrityToken = $integrityToken") {
                    Log.i(TAG, "BotGuard ready, ttl=${ttlSeconds}s")
                    generatorContinuation.resume(this@PoTokenWebView)
                }
            }
        }
    }

    /**
     * Mints a PO token for [identifier].
     *
     * The "streaming" token is minted with the visitor data as identifier and
     * must be generated once before any per-video token.
     */
    suspend fun generatePoToken(identifier: String): String =
        suspendCancellableCoroutine { continuation ->
            poTokenContinuations[identifier] = continuation
            val u8Identifier = JavascriptUtil.stringToU8(identifier)

            Handler(Looper.getMainLooper()).post {
                webView.evaluateJavascript(
                    """
                    try {
                        identifier = "$identifier"
                        u8Identifier = $u8Identifier
                        poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                        poTokenU8String = ""
                        for (i = 0; i < poTokenU8.length; i++) {
                            if (i != 0) poTokenU8String += ","
                            poTokenU8String += poTokenU8[i]
                        }
                        $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                    } catch (error) {
                        $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                    }
                    """.trimIndent(),
                    null,
                )
            }
        }

    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        Log.e(TAG, "obtainPoToken error for $identifier: $error")
        poTokenContinuations.remove(identifier)?.resumeWithException(Exception(error))
    }

    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        val poToken = runCatching { JavascriptUtil.u8ToBase64(poTokenU8) }
            .getOrElse { t ->
                poTokenContinuations.remove(identifier)?.resumeWithException(t)
                return
            }
        Log.i(TAG, "minted poToken for ${identifier.take(12)}… (${poToken.length} chars)")
        poTokenContinuations.remove(identifier)?.resume(poToken)
    }

    fun isExpired(): Boolean = Instant.now().isAfter(expirationInstant)

    private fun onInitializationError(error: Throwable) {
        CoroutineScope(Dispatchers.Main).launch {
            close()
            generatorContinuation.resumeWithException(error)
        }
    }

    @MainThread
    fun close() = with(webView) {
        clearHistory()
        loadUrl("about:blank")
        onPause()
        removeAllViews()
        destroy()
    }

    private suspend fun postBotguardService(url: String, data: List<String>): String =
        withContext(Dispatchers.IO) {
            val body = JsonArraySerializer.encode(data)
                .toRequestBody("application/json+protobuf".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("x-goog-api-key", GOOGLE_API_KEY)
                .header("x-user-agent", "grpc-web-javascript/0.1")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("BotGuard endpoint returned HTTP ${response.code}")
                }
                response.body.string()
            }
        }

    companion object {
        private const val TAG = "PoTokenWebView"

        // Public key used by Innertube web clients.
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val JS_INTERFACE = "PoTokenWebView"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        /** Creates and initialises a generator. Must be called from the main thread. */
        suspend fun create(context: Context): PoTokenWebView =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val view = PoTokenWebView(context, cont)
                    view.loadHtmlAndObtainBotguard(context)
                }
            }
    }
}

/** Tiny helper so we do not pull in a JSON encoder for two strings. */
private object JsonArraySerializer {
    fun encode(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { value ->
            "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\""
        }
}
