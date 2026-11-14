package com.egyvpn.app.core

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.util.Base64

/**
 * Fetches public community subscriptions, parses supported sing-box share links,
 * and checks TCP reachability before offering a candidate. Reachability is not
 * proof of a working VPN tunnel; the core performs the real protocol handshake.
 */
object FreeServerRepository {
    private val feeds = listOf(
        "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/mini.txt",
        "https://raw.githubusercontent.com/chaishpish/free_server_v2ray/main/@TFMTProto"
    )

    data class Candidate(val name: String, val config: String, val protocol: String)

    fun fetchFirstReachable(): Candidate {
        val failures = mutableListOf<String>()
        for (feed in feeds) {
            try {
                val body = download(feed)
                val lines = decodeSubscription(body).lineSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("vless://", true) || it.startsWith("trojan://", true) }
                    .distinct()
                    .take(80)
                for (line in lines) {
                    val candidate = runCatching { toSingBox(line) }.getOrNull() ?: continue
                    val endpoint = JSONObject(candidate.config).getJSONArray("outbounds")
                        .getJSONObject(0)
                    val host = endpoint.getString("server")
                    val port = endpoint.getInt("server_port")
                    if (tcpReachable(host, port)) return candidate
                }
                failures += "لا يوجد خادم VLESS/Trojan قابل للوصول في المصدر"
            } catch (error: Exception) {
                failures += (error.message ?: "تعذر قراءة المصدر")
            }
        }
        throw IllegalStateException(failures.firstOrNull() ?: "لم يتم العثور على خادم مناسب")
    }

    private fun download(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 18_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "EGY-VPN-Android/1.1")
        return try {
            check(connection.responseCode in 200..299) { "مصدر السيرفرات أعاد HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(4_000_000) }
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeSubscription(value: String): String {
        if (value.contains("://")) return value
        val compact = value.trim().replace("\\s".toRegex(), "")
        return runCatching {
            String(Base64.getMimeDecoder().decode(compact), Charsets.UTF_8)
        }.getOrDefault(value)
    }

    private fun tcpReachable(host: String, port: Int): Boolean =
        runCatching {
            Socket().use { it.connect(java.net.InetSocketAddress(host, port), 1800) }
            true
        }.getOrDefault(false)

    private fun toSingBox(link: String): Candidate {
        val uri = Uri.parse(link)
        val scheme = uri.scheme?.lowercase() ?: error("بروتوكول غير مدعوم")
        val host = uri.host ?: error("عنوان الخادم غير صالح")
        val port = uri.port.takeIf { it in 1..65535 } ?: error("منفذ الخادم غير صالح")
        val query = uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
        val tag = "EGY-${scheme.uppercase()}-${host}"
        val outbound = JSONObject()
            .put("type", scheme)
            .put("tag", "proxy")
            .put("server", host)
            .put("server_port", port)

        when (scheme) {
            "vless" -> {
                val uuid = uri.userInfo?.substringBefore(':')?.takeIf { it.isNotBlank() }
                    ?: error("UUID مفقود")
                outbound.put("uuid", uuid)
                query["flow"]?.takeIf { it.isNotBlank() }?.let { outbound.put("flow", it) }
            }
            "trojan" -> {
                val password = uri.userInfo?.let { URLDecoder.decode(it, "UTF-8") }
                    ?.takeIf { it.isNotBlank() } ?: error("كلمة مرور Trojan مفقودة")
                outbound.put("password", password)
            }
            else -> error("البروتوكول غير مدعوم")
        }

        val security = query["security"].orEmpty().lowercase()
        if (security == "tls" || security == "reality") {
            val tls = JSONObject()
                .put("enabled", true)
                .put("server_name", query["sni"].orEmpty().ifBlank { query["servername"].orEmpty().ifBlank { host } })
                .put("insecure", false)
            query["fp"]?.takeIf { it.isNotBlank() }?.let {
                tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", it))
            }
            if (security == "reality") {
                val publicKey = query["pbk"].orEmpty()
                check(publicKey.isNotBlank()) { "إعداد Reality ينقصه المفتاح العام" }
                tls.put("reality", JSONObject().put("enabled", true)
                    .put("public_key", publicKey)
                    .put("short_id", query["sid"].orEmpty()))
            }
            outbound.put("tls", tls)
        }

        when (query["type"].orEmpty().lowercase()) {
            "ws" -> {
                val headers = JSONObject()
                query["host"]?.takeIf { it.isNotBlank() }?.let { headers.put("Host", it) }
                outbound.put("transport", JSONObject().put("type", "ws")
                    .put("path", query["path"].orEmpty().ifBlank { "/" })
                    .put("headers", headers))
            }
            "grpc" -> outbound.put("transport", JSONObject().put("type", "grpc")
                .put("service_name", query["serviceName"].orEmpty()))
        }

        val inbounds = JSONArray().put(
            JSONObject().put("type", "tun").put("tag", "tun-in")
                .put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
                .put("auto_route", true).put("strict_route", true).put("stack", "mixed")
        )
        val outbounds = JSONArray()
            .put(outbound)
            .put(JSONObject().put("type", "direct").put("tag", "direct"))
        val config = JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put("inbounds", inbounds)
            .put("outbounds", outbounds)
            .put("route", JSONObject().put("auto_detect_interface", true).put("final", "proxy"))
        return Candidate(tag, config.toString(), scheme)
    }
}
