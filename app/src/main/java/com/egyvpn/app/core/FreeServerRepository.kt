package com.egyvpn.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Fetches public community subscriptions, parses supported sing-box share links,
 * and checks TCP reachability before offering a candidate. Reachability is not
 * proof of a working VPN tunnel; the core performs the real protocol handshake.
 */
object FreeServerRepository {
    private val feeds = listOf(
        "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/best.txt",
        "https://raw.githubusercontent.com/chaishpish/free_server_v2ray/main/@TFMTProto"
    )

    data class Candidate(val name: String, val config: String, val protocol: String, val host: String, val port: Int)

    fun fetchFirstReachable(): Candidate =
        fetchReachableCandidates(1).firstOrNull()
            ?: throw IllegalStateException("لم يتم العثور على خادم مناسب")

    fun fetchReachableCandidates(maxCount: Int = 25): List<Candidate> {
        val found = linkedMapOf<String, Candidate>()
        val failures = mutableListOf<String>()
        for (feed in feeds) {
            try {
                val body = download(feed)
                val candidates = decodeSubscription(body).lineSequence()
                    .map { it.trim() }
                    .filter { listOf("vless://", "trojan://", "vmess://", "ss://", "hysteria2://", "hy2://").any { prefix -> it.startsWith(prefix, true) } }
                    .distinct()
                    .take(120)
                    .mapNotNull { runCatching { toSingBox(it) }.getOrNull() }
                    .distinctBy { "${it.host}:${it.port}" }
                    .filterNot { "${it.host}:${it.port}" in found }
                val pool = Executors.newFixedThreadPool(12)
                try {
                    val checks = candidates.map { candidate ->
                        pool.submit<Candidate?> {
                            if (tcpReachable(candidate.host, candidate.port)) candidate else null
                        }
                    }
                    for (check in checks) {
                        val candidate = runCatching { check.get(2500, TimeUnit.MILLISECONDS) }.getOrNull() ?: continue
                        found["${candidate.host}:${candidate.port}"] = candidate
                        if (found.size >= maxCount) return found.values.toList()
                    }
                } finally {
                    pool.shutdownNow()
                }
            } catch (error: Exception) {
                failures += (error.message ?: "تعذر قراءة المصدر")
            }
        }
        if (found.isEmpty()) {
            throw IllegalStateException(failures.firstOrNull() ?: "لم يتم العثور على سيرفر قابل للوصول")
        }
        return found.values.toList()
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
            Socket().use { it.connect(java.net.InetSocketAddress(host, port), 1400) }
            true
        }.getOrDefault(false)

    private fun toSingBox(link: String): Candidate {
        val scheme = link.substringBefore("://").lowercase()
        return when (scheme) {
            "vmess" -> toVmess(link)
            "ss" -> toShadowsocks(link)
            "hysteria2", "hy2" -> toHysteria2(link)
            "vless", "trojan" -> toVlessOrTrojan(link, scheme)
            else -> error("البروتوكول غير مدعوم")
        }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> =
        rawQuery.orEmpty().split("&").filter { it.isNotBlank() }.associate { part ->
            val pieces = part.split("=", limit = 2)
            URLDecoder.decode(pieces[0], "UTF-8") to URLDecoder.decode(pieces.getOrElse(1) { "" }, "UTF-8")
        }

    private fun toVlessOrTrojan(link: String, scheme: String): Candidate {
        val uri = java.net.URI(link)
        val host = uri.host ?: error("عنوان الخادم غير صالح")
        val port = uri.port.takeIf { it in 1..65535 } ?: error("منفذ الخادم غير صالح")
        val userInfo = uri.rawUserInfo?.let { URLDecoder.decode(it, "UTF-8") }
        val query = parseQuery(uri.rawQuery)
        val outbound = JSONObject().put("type", scheme).put("tag", "proxy")
            .put("server", host).put("server_port", port)
        if (scheme == "vless") {
            outbound.put("uuid", userInfo?.substringBefore(':')?.takeIf { it.isNotBlank() } ?: error("UUID مفقود"))
            query["flow"]?.takeIf { it.isNotBlank() }?.let { outbound.put("flow", it) }
        } else {
            outbound.put("password", userInfo?.takeIf { it.isNotBlank() } ?: error("كلمة مرور Trojan مفقودة"))
        }
        applyTlsAndTransport(outbound, host, query)
        return buildCandidate("EGY-${scheme.uppercase()}-$host", scheme, host, port, outbound)
    }

    private fun toVmess(link: String): Candidate {
        val encoded = link.removePrefix("vmess://").substringBefore('#').trim()
        val data = JSONObject(String(Base64.getMimeDecoder().decode(encoded), Charsets.UTF_8))
        val host = data.optString("add").takeIf { it.isNotBlank() } ?: error("عنوان VMess مفقود")
        val port = data.optString("port").toIntOrNull()?.takeIf { it in 1..65535 } ?: error("منفذ VMess غير صالح")
        val uuid = data.optString("id").takeIf { it.isNotBlank() } ?: error("UUID الخاص بـ VMess مفقود")
        val outbound = JSONObject().put("type", "vmess").put("tag", "proxy")
            .put("server", host).put("server_port", port).put("uuid", uuid)
            .put("security", data.optString("scy").ifBlank { "auto" })
            .put("alter_id", data.optInt("aid", 0))
        val network = data.optString("net")
        val query = mapOf(
            "security" to if (data.optString("tls").equals("tls", true)) "tls" else "",
            "sni" to data.optString("sni"),
            "fp" to data.optString("fp"),
            "type" to network,
            "path" to data.optString("path"),
            "host" to data.optString("host"),
            "serviceName" to data.optString("path")
        )
        applyTlsAndTransport(outbound, host, query)
        return buildCandidate(data.optString("ps").ifBlank { "VMess-$host" }, "vmess", host, port, outbound)
    }

    private fun toShadowsocks(link: String): Candidate {
        val raw = link.removePrefix("ss://").substringBefore('#').substringBefore('?')
        val at = raw.lastIndexOf('@')
        val methodPassword: String
        val serverPart: String
        if (at >= 0) {
            val encodedUser = raw.substring(0, at)
            methodPassword = runCatching {
                String(Base64.getMimeDecoder().decode(encodedUser), Charsets.UTF_8)
            }.getOrElse { URLDecoder.decode(encodedUser, "UTF-8") }
            serverPart = raw.substring(at + 1)
        } else {
            val decoded = String(Base64.getMimeDecoder().decode(raw), Charsets.UTF_8)
            val decodedAt = decoded.lastIndexOf('@')
            if (decodedAt < 0) error("رابط Shadowsocks غير صالح")
            methodPassword = decoded.substring(0, decodedAt)
            serverPart = decoded.substring(decodedAt + 1)
        }
        val method = methodPassword.substringBefore(':').trim()
        val password = methodPassword.substringAfter(':', "").trim()
        if (method.isBlank() || password.isBlank()) error("بيانات Shadowsocks ناقصة")
        val uri = java.net.URI("ss://${serverPart.removePrefix("//")}")
        val host = uri.host ?: error("عنوان Shadowsocks غير صالح")
        val port = uri.port.takeIf { it in 1..65535 } ?: error("منفذ Shadowsocks غير صالح")
        val outbound = JSONObject().put("type", "shadowsocks").put("tag", "proxy")
            .put("server", host).put("server_port", port)
            .put("method", method).put("password", password)
        return buildCandidate("SS-$host", "shadowsocks", host, port, outbound)
    }

    private fun toHysteria2(link: String): Candidate {
        val normalized = link.replaceFirst("hy2://", "hysteria2://")
        val uri = java.net.URI(normalized)
        val host = uri.host ?: error("عنوان Hysteria2 غير صالح")
        val port = uri.port.takeIf { it in 1..65535 } ?: error("منفذ Hysteria2 غير صالح")
        val password = uri.rawUserInfo?.let { URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() }
            ?: error("كلمة مرور Hysteria2 مفقودة")
        val query = parseQuery(uri.rawQuery)
        val tls = JSONObject().put("enabled", true)
            .put("server_name", query["sni"].orEmpty().ifBlank { host })
            .put("insecure", query["insecure"] == "1" || query["insecure"] == "true")
        val outbound = JSONObject().put("type", "hysteria2").put("tag", "proxy")
            .put("server", host).put("server_port", port).put("password", password).put("tls", tls)
        query["obfs"]?.takeIf { it.isNotBlank() }?.let {
            outbound.put("obfs", JSONObject().put("type", it).put("password", query["obfs-password"].orEmpty()))
        }
        return buildCandidate("HY2-$host", "hysteria2", host, port, outbound)
    }

    private fun applyTlsAndTransport(outbound: JSONObject, host: String, query: Map<String, String>) {
        val security = query["security"].orEmpty().lowercase()
        if (security == "tls" || security == "reality") {
            val tls = JSONObject().put("enabled", true)
                .put("server_name", query["sni"].orEmpty().ifBlank { query["servername"].orEmpty().ifBlank { host } })
                .put("insecure", false)
            query["fp"]?.takeIf { it.isNotBlank() }?.let {
                tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", it))
            }
            if (security == "reality") {
                val publicKey = query["pbk"].orEmpty()
                check(publicKey.isNotBlank()) { "إعداد Reality ينقصه المفتاح العام" }
                tls.put("reality", JSONObject().put("enabled", true)
                    .put("public_key", publicKey).put("short_id", query["sid"].orEmpty()))
            }
            outbound.put("tls", tls)
        }
        when (query["type"].orEmpty().lowercase()) {
            "ws" -> {
                val headers = JSONObject()
                query["host"]?.takeIf { it.isNotBlank() }?.let { headers.put("Host", it) }
                outbound.put("transport", JSONObject().put("type", "ws")
                    .put("path", query["path"].orEmpty().ifBlank { "/" }).put("headers", headers))
            }
            "grpc" -> outbound.put("transport", JSONObject().put("type", "grpc")
                .put("service_name", query["serviceName"].orEmpty()))
        }
    }

    private fun buildCandidate(
        name: String, protocol: String, host: String, port: Int, outbound: JSONObject
    ): Candidate {
        val inbounds = JSONArray().put(
            JSONObject().put("type", "tun").put("tag", "tun-in")
                .put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
                .put("auto_route", true).put("strict_route", true).put("stack", "mixed")
        )
        val outbounds = JSONArray().put(outbound)
            .put(JSONObject().put("type", "direct").put("tag", "direct"))
        val config = JSONObject().put("log", JSONObject().put("level", "warn"))
            .put("inbounds", inbounds).put("outbounds", outbounds)
            .put("route", JSONObject().put("auto_detect_interface", true).put("final", "proxy"))
        return Candidate(name, config.toString(), protocol, host, port)
    }
}
