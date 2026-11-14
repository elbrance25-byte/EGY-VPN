package com.egyvpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.egyvpn.app.R
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus
import java.util.Locale

/**
 * Owns the Android VPN permission, sing-box command server and TUN lifecycle.
 * A connected state is persisted only after sing-box accepts and starts the profile.
 */
class EgyVpnService : VpnService(), CommandServerHandler {
    companion object {
        const val ACTION_CONNECT = "com.egyvpn.app.vpn.CONNECT"
        const val ACTION_STOP = "com.egyvpn.app.vpn.STOP"
        const val EXTRA_CONFIG = "com.egyvpn.app.vpn.CONFIG"
        private const val CHANNEL_ID = "egy_vpn_connection"
        private const val NOTIFICATION_ID = 4101
        private const val TAG = "EgyVpnService"
    }

    private val platform by lazy { EgyVpnPlatform(this) }
    private var commandServer: CommandServer? = null
    private var activeConfig: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_CONNECT) return START_NOT_STICKY

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("جارٍ بدء الاتصال..."))
        val config = intent.getStringExtra(EXTRA_CONFIG)?.takeIf { it.isNotBlank() }
        if (config == null) {
            failConnection("ملف الإعداد فارغ")
            return START_NOT_STICKY
        }
        Thread {
            try {
                startSingBox(config)
                activeConfig = config
                getSharedPreferences("egy_vpn_private", MODE_PRIVATE)
                    .edit().putBoolean("connected", true).apply()
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification("الاتصال نشط عبر sing-box"))
            } catch (error: Exception) {
                Log.e(TAG, "sing-box startup failed", error)
                failConnection(error.message ?: "فشل تشغيل محرك الاتصال")
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun startSingBox(config: String) {
        check(VpnService.prepare(this) == null) { "لم يتم منح إذن VPN" }
        val base = filesDir
        val work = getExternalFilesDir(null) ?: filesDir
        work.mkdirs()
        Libbox.setLocale(Locale.getDefault().toLanguageTag())
        Libbox.setup(SetupOptions().apply {
            basePath = base.absolutePath
            workingPath = work.absolutePath
            tempPath = cacheDir.absolutePath
            logMaxLines = 1000
            appVersion = packageManager.getPackageInfo(packageName, 0).longVersionCode.toString()
            appMarketingVersion = "1.1"
        })
        val server = CommandServer(this, platform)
        commandServer = server
        server.start()
        server.startOrReloadService(config, OverrideOptions())
    }

    override fun serviceStop() {
        stopTunnel()
        stopSelf()
    }

    override fun serviceReload() {
        val config = activeConfig ?: return
        runCatching {
            commandServer?.startOrReloadService(config, OverrideOptions())
        }.onFailure { Log.e(TAG, "sing-box reload failed", it) }
    }

    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus().apply {
        available = false
        enabled = false
    }

    override fun setSystemProxyEnabled(isEnabled: Boolean) {}
    override fun triggerNativeCrash() {}
    override fun writeDebugMessage(message: String?) {
        Log.d("sing-box", message.orEmpty())
    }
    override fun connectSSHAgent(): Int = -1

    override fun onRevoke() {
        stopTunnel()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    private fun failConnection(message: String) {
        getSharedPreferences("egy_vpn_private", MODE_PRIVATE)
            .edit().putBoolean("connected", false).apply()
        Log.e(TAG, message)
        stopTunnel()
        stopSelf()
    }

    private fun stopTunnel() {
        getSharedPreferences("egy_vpn_private", MODE_PRIVATE)
            .edit().putBoolean("connected", false).apply()
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        activeConfig = null
        platform.closeTun()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "EGY VPN", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "حالة اتصال EGY VPN" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("EGY VPN")
            .setContentText(status)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
}
