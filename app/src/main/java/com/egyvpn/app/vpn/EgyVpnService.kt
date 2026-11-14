package com.egyvpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.egyvpn.app.R

/**
 * The single Android VPN entry point for EGY VPN.
 *
 * The TUN descriptor is deliberately not established until a verified engine
 * adapter supplies valid sing-box TunOptions. This prevents a blank TUN from
 * silently black-holing device traffic or displaying a false connected state.
 */
class EgyVpnService : VpnService() {
    companion object {
        const val ACTION_STOP = "com.egyvpn.app.vpn.STOP"
        private const val CHANNEL_ID = "egy_vpn_connection"
        private const val NOTIFICATION_ID = 4101
    }

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        // No TUN is opened here: libbox must first provide validated TunOptions.
        // Connection state remains non-connected until an engine is attached.
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        stopTunnel()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    private fun stopTunnel() {
        runCatching { tun?.close() }
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EGY VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "حالة خدمة اتصال EGY VPN"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("EGY VPN")
            .setContentText("خدمة VPN جاهزة — محرك الاتصال لم يُرفق بعد")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
}
