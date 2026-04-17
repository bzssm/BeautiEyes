package com.roy.beautieyes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ActivityManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.roy.beautieyes.VideoPlayerActivity
import com.roy.beautieyes.signal.HttpSignalSource
import com.roy.beautieyes.signal.SignalSource
import com.roy.beautieyes.signal.SignalSourceStatus

class SignalService : Service() {

    companion object {
        const val CHANNEL_ID = "beautieyes_signal"
        const val ALERT_CHANNEL_ID = "beautieyes_alert"
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
        const val DEFAULT_PORT = 8080
    }

    private lateinit var signalSource: SignalSource
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SignalService = this@SignalService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        signalSource = HttpSignalSource(this, DEFAULT_PORT)
        signalSource.start { command ->
            if (command.action == "play") {
                launchVideoPlayer()
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 服务被系统杀死后自动重启
        return START_STICKY
    }

    override fun onDestroy() {
        signalSource.stop()
        super.onDestroy()
    }

    fun getSignalSourceStatus(): SignalSourceStatus = signalSource.getStatus()

    /**
     * 前台时直接启动 Activity，后台时用 Full-Screen Intent 弹出。
     */
    private fun launchVideoPlayer() {
        val foreground = isAppInForeground()
        Log.d("BeautiEyes", "launchVideoPlayer called, isAppInForeground=$foreground")

        val intent = Intent(this, VideoPlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }

        // 始终尝试直接启动
        try {
            startActivity(intent)
            Log.d("BeautiEyes", "startActivity called")
        } catch (e: Exception) {
            Log.e("BeautiEyes", "startActivity failed", e)
        }

        // 后台时同时发全屏通知作为备选
        if (!foreground) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = Notification.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle("BeautiEyes")
                .setContentText("正在播放视频")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setFullScreenIntent(pendingIntent, true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(ALERT_NOTIFICATION_ID, notification)
            Log.d("BeautiEyes", "fullscreen notification sent")
        }
    }

    private fun isAppInForeground(): Boolean {
        val am = getSystemService(ActivityManager::class.java)
        val appProcesses = am.runningAppProcesses ?: return false
        return appProcesses.any {
            it.processName == packageName &&
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // 常驻服务通知通道（低优先级）
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "BeautiEyes 信号服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持信号监听服务运行"
        }
        manager.createNotificationChannel(serviceChannel)

        // 播放提醒通道（高优先级，用于 full-screen intent）
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "BeautiEyes 播放提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "收到播放信号时弹出全屏播放"
        }
        manager.createNotificationChannel(alertChannel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("BeautiEyes")
            .setContentText("信号监听服务运行中")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }
}