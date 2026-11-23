package com.vladisx.youtubedownloader.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vladisx.youtubedownloader.MainActivity
import com.vladisx.youtubedownloader.R
import com.vladisx.youtubedownloader.data.DownloadRequest
import com.vladisx.youtubedownloader.data.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the download alive when the app is backgrounded
 * and mirrors progress into the notification shade.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var lastPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelWatcher?.set(true)
                DownloadRepository.cancel()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> Unit
        }

        val request = DownloadRepository.request.value
        Logger.i("onStartCommand action=${intent?.action} request=${request?.title}")
        if (request == null) {
            Logger.i("no pending request -> stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(request, DownloadState.Preparing()))

        scope.launch { runDownload(request) }
        return START_NOT_STICKY
    }

    private fun runDownload(request: DownloadRequest) {
        val cancelFlag = java.util.concurrent.atomic.AtomicBoolean(false)
        cancelWatcher = cancelFlag

        val result = runCatching {
            Logger.i("download start: kind=${request.kind} format=${request.videoFormatId}")
            DownloadRepository.manager.downloadBlocking(request, cancelFlag) { state ->
                if (state is DownloadState.Running) {
                    Logger.i("progress ${state.percent.toInt()}% ${state.downloadedBytes}/${state.totalBytes}")
                } else {
                    Logger.i("state ${state::class.simpleName}")
                }
                DownloadRepository.publish(state)
                updateNotification(request, state)
            }
        }

        result
            .onSuccess { file ->
                Logger.i("download OK: ${file.absolutePath}")
                val done = DownloadState.Completed(file.absolutePath, file.name)
                DownloadRepository.finishWith(done)
                updateNotification(request, done, ongoing = false)
            }
            .onFailure { t ->
                Logger.e("download failed", t)
                val failed = DownloadState.Failed(
                    if (DownloadRepository.isCancelled) "Скасовано"
                    else t.message ?: t.javaClass.simpleName
                )
                DownloadRepository.finishWith(failed)
                updateNotification(request, failed, ongoing = false)
            }

        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private var cancelWatcher: java.util.concurrent.atomic.AtomicBoolean? = null

    private fun updateNotification(
        request: DownloadRequest,
        state: DownloadState,
        ongoing: Boolean = true,
    ) {
        when (state) {
            is DownloadState.Running -> {
                val pct = state.percent.toInt()
                if (pct == lastPercent) return
                lastPercent = pct
            }
            else -> Unit
        }
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(request, state, ongoing),
        )
    }

    private fun buildNotification(
        request: DownloadRequest,
        state: DownloadState,
        ongoing: Boolean = true,
    ): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(request.title.take(60))
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        when (state) {
            is DownloadState.Running -> {
                builder.setContentText("${state.stage} ${state.percent.toInt()}%")
                builder.setProgress(100, state.percent.toInt(), false)
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Скасувати", cancel)
            }
            is DownloadState.Preparing -> builder.setContentText(state.message)
            is DownloadState.Merging -> {
                builder.setContentText(state.message)
                builder.setProgress(0, 0, true)
            }
            is DownloadState.Completed -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                builder.setContentText("Готово: ${state.displayName.take(40)}")
                builder.setAutoCancel(true)
            }
            is DownloadState.Failed -> {
                builder.setSmallIcon(android.R.drawable.stat_notify_error)
                builder.setContentText(state.message.take(80))
                builder.setAutoCancel(true)
            }
            DownloadState.Idle -> builder.setContentText("Очікування…")
        }

        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Завантаження",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Прогрес завантаження відео та аудіо"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 42
        const val ACTION_CANCEL = "com.vladisx.youtubedownloader.CANCEL"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL)
            )
        }
    }
}
