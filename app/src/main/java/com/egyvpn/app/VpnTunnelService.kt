package com.egyvpn.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.FileInputStream
import java.util.concurrent.Executors

class VpnTunnelService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var backend: GoBackend

    private val tunnel = object : Tunnel {
        override fun getName(): String = "EGYVPN"

        override fun onStateChange(newState: Tunnel.State) {
            sendStatus(
                if (newState == Tunnel.State.UP) STATUS_CONNECTED else STATUS_DISCONNECTED,
                if (newState == Tunnel.State.UP) "النفق متصل" else "النفق غير متصل"
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        backend = GoBackend(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != ACTION_CONNECT && action != ACTION_DISCONNECT) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("EGY VPN يعمل"))

        executor.execute {
            try {
                if (action == ACTION_CONNECT) {
                    val configFile = getFileStreamPath(CONFIG_FILE)
                    if (!configFile.isFile || configFile.length() == 0L) {
                        throw IllegalStateException("لم يتم استيراد ملف إعداد WireGuard")
                    }
                    val config = FileInputStream(configFile).use { Config.parse(it) }
                    if (config.peers.isEmpty()) {
                        throw IllegalArgumentException("ملف الإعداد لا يحتوي على Peer")
                    }
                    backend.setState(tunnel, Tunnel.State.UP, config)
                    sendStatus(STATUS_CONNECTED, "تم تشغيل نفق WireGuard")
                } else {
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                    sendStatus(STATUS_DISCONNECTED, "تم فصل الاتصال")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            } catch (error: Exception) {
                val safeMessage = error.message?.take(160) ?: "تعذر تشغيل اتصال VPN"
                sendStatus(STATUS_ERROR, safeMessage)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun sendStatus(status: String, message: String) {
        sendBroadcast(
            Intent(ACTION_STATUS).setPackage(packageName)
                .putExtra(EXTRA_STATUS, status)
                .putExtra(EXTRA_MESSAGE, message)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EGY VPN connection",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("EGY VPN")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_CONNECT = "com.egyvpn.app.CONNECT"
        const val ACTION_DISCONNECT = "com.egyvpn.app.DISCONNECT"
        const val ACTION_STATUS = "com.egyvpn.app.STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_MESSAGE = "message"
        const val CONFIG_FILE = "wireguard.conf"

        const val STATUS_CONNECTED = "connected"
        const val STATUS_DISCONNECTED = "disconnected"
        const val STATUS_ERROR = "error"

        private const val CHANNEL_ID = "egy_vpn_connection"
        private const val NOTIFICATION_ID = 4101
    }
}
