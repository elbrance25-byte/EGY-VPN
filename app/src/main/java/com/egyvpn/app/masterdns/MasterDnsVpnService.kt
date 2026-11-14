package com.egyvpn.app.masterdns

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.egyvpn.masterdns.gomobile.mobile.Mobile
import com.egyvpn.masterdns.gomobile.mobile.MobileProtectCallback
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.InetAddress
import java.net.Socket

/**
 * Android TUN adapter for the MasterDnsVPN Go engine.
 * Uses the upstream gVisor tun2socks bridge rather than a hand-written TCP stack.
 */
class MasterDnsVpnService : VpnService() {
    companion object {
        const val ACTION_CONNECT = "com.egyvpn.app.masterdns.CONNECT"
        const val ACTION_STOP = "com.egyvpn.app.masterdns.STOP"
        const val EXTRA_CONFIG = "com.egyvpn.app.masterdns.CONFIG"
        private const val CHANNEL_ID = "egy_masterdns_vpn"
        private const val NOTIFICATION_ID = 4202
        private const val TAG = "MasterDnsVpnService"
        private const val PROFILE_ID = "egy-masterdns-active"
        private const val MTU = 1500
    }

    private var tun: ParcelFileDescriptor? = null
    private var activeProfileId: String? = null
    @Volatile private var stopping = false
    private var bridgeThread: Thread? = null
    private var connectThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val raw = intent.getStringExtra(EXTRA_CONFIG)?.takeIf { it.isNotBlank() }
                if (raw == null) {
                    fail("ملف إعداد MasterDnsVPN فارغ")
                    return START_NOT_STICKY
                }
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, notification("جارٍ بدء نفق MasterDnsVPN"))
                stopping = false
                connectThread?.interrupt()
                connectThread = Thread {
                    try {
                        connect(raw)
                    } catch (error: Exception) {
                        Log.e(TAG, "MasterDnsVPN startup failed", error)
                        fail(error.message ?: "فشل تشغيل محرك MasterDnsVPN")
                    }
                }.also { it.name = "MasterDns-Connect"; it.start() }
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun connect(raw: String) {
        check(VpnService.prepare(this) == null) { "لم يتم منح إذن VPN" }
        val input = JSONObject(raw)
        val domains = input.optString("domains").trim()
        val encryptionKey = input.optString("encryption_key").trim()
        val resolvers = input.optString("resolvers").trim().ifBlank {
            "1.1.1.1\n8.8.8.8"
        }
        val dnsServer = input.optString("dns_server", "1.1.1.1").trim()
        val listenPort = input.optInt("listen_port", 18000).coerceIn(1024, 65535)
        check(domains.isNotBlank()) { "domains مطلوب" }
        check(encryptionKey.isNotBlank()) { "encryption_key مطلوب" }
        check(resolvers.isNotBlank()) { "قائمة resolvers فارغة" }

        val builder = Builder()
            .setSession("EGY VPN - MasterDnsVPN")
            .addAddress("10.23.0.2", 32)
            .addAddress("fd00::1", 128)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(InetAddress.getByName(dnsServer))
            .setMtu(MTU)
            .setBlocking(true)
        runCatching { builder.addRoute("::", 0) }
        runCatching { builder.addDisallowedApplication(packageName) }
        tun = builder.establish() ?: error("تعذر إنشاء واجهة VPN في Android")

        Mobile.setTunDisableIPv6(input.optBoolean("disable_ipv6", true))
        Mobile.setProtectCallback(object : MobileProtectCallback {
            override fun protect(fd: Int): Boolean = this@MasterDnsVpnService.protect(fd)
        })

        val profileDirFile = File(filesDir, "masterdns/$PROFILE_ID").apply { mkdirs() }
        val config = Mobile.newDefaultConfig().apply {
            profileDir = profileDirFile.absolutePath
            resolversInline = resolvers
            this.domains = domains
            dataEncryptionMethod = input.optInt("data_encryption_method", 1).toLong()
            this.encryptionKey = encryptionKey
            protocolType = input.optString("protocol_type", "SOCKS5")
            listenIP = "127.0.0.1"
            this.listenPort = listenPort.toLong()
            setSOCKS5Auth(input.optBoolean("socks5_auth", false))
            setSOCKS5User(input.optString("socks5_user", "master_dns_vpn"))
            setSOCKS5Pass(input.optString("socks5_pass", "master_dns_vpn"))
        }

        activeProfileId = PROFILE_ID
        Mobile.startInstance(PROFILE_ID, config)
        waitForProxy("127.0.0.1", listenPort, 45_000)

        val tunFd = tun?.fd ?: error("واجهة VPN غير متاحة")
        val bridgeAddress = "127.0.0.1:$listenPort"
        bridgeThread = Thread {
            try {
                Mobile.startTunBridge(PROFILE_ID, tunFd, MTU, bridgeAddress)
                if (!stopping) fail("توقف جسر TUN بشكل غير متوقع")
            } catch (error: Exception) {
                if (!stopping) fail(error.message ?: "تعذر تشغيل جسر TUN")
            }
        }.also { it.name = "MasterDns-TUN"; it.start() }

        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!stopping && SystemClock.elapsedRealtime() < deadline && !Mobile.isTunBridgeRunning()) {
            Thread.sleep(100)
        }
        check(Mobile.isTunBridgeRunning()) {
            Mobile.getLastError(PROFILE_ID).ifBlank { "محرك MasterDnsVPN لم يبدأ جسر TUN" }
        }
        publishState(true)
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification("اتصال MasterDnsVPN نشط"))
    }

    private fun waitForProxy(host: String, port: Int, timeoutMs: Long) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var lastError = ""
        while (!stopping && SystemClock.elapsedRealtime() < deadline) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 400)
                }
                return
            } catch (error: Exception) {
                lastError = error.message.orEmpty()
                Thread.sleep(200)
            }
        }
        error(Mobile.getLastError(PROFILE_ID).ifBlank { "لم يبدأ SOCKS5 المحلي: $lastError" })
    }

    private fun fail(message: String) {
        Log.e(TAG, message)
        publishState(false)
        stopTunnel()
        stopSelf()
    }

    private fun publishState(connected: Boolean) {
        sendBroadcast(
            Intent(com.egyvpn.app.vpn.EgyVpnService.ACTION_STATE)
                .setPackage(packageName)
                .putExtra("connected", connected)
                .putExtra("engine", "MASTER_DNS")
        )
    }

    private fun stopTunnel() {
        stopping = true
        runCatching { Mobile.stopTunBridge() }
        activeProfileId?.let { id -> runCatching { Mobile.stopInstance(id) } }
        activeProfileId = null
        runCatching { Mobile.setProtectCallback(null) }
        runCatching { tun?.close() }
        tun = null
        bridgeThread?.interrupt()
        bridgeThread = null
        getSharedPreferences("egy_vpn_private", MODE_PRIVATE).edit()
            .putBoolean("connected", false).apply()
        publishState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "EGY VPN MasterDnsVPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(status: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("EGY VPN")
            .setContentText(status)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    override fun onRevoke() {
        stopTunnel()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        connectThread?.interrupt()
        connectThread = null
        super.onDestroy()
    }
}
