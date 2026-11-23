package com.vladisx.youtubedownloader.download

import android.util.Log

/** Very small logging facade so download internals stay easy to trace. */
internal object Logger {
    private const val TAG = "YtDl"

    fun i(message: String) = Log.i(TAG, message)

    fun e(message: String, throwable: Throwable? = null) =
        Log.e(TAG, message, throwable)
}
