package com.vladisx.youtubedownloader

import android.app.Application
import com.vladisx.youtubedownloader.data.YtDlpManager

class YtDlApp : Application() {

    lateinit var ytDlp: YtDlpManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        ytDlp = YtDlpManager(this)
    }

    companion object {
        lateinit var instance: YtDlApp
            private set
    }
}
