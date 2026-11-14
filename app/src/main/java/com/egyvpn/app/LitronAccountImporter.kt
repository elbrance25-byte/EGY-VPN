package com.egyvpn.app

import org.json.JSONObject

/**
 * Converts the human-readable "Copy all details" TXT export from LitronSSH
 * SlowDNS/FastDNS into the app's internal DNSTT profile.
 *
 * Passwords are kept only in the local profile; never log the imported text.
 */
object LitronAccountImporter {
    fun toDnsttJson(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty() || text.startsWith("{")) return null

        val values = linkedMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val match = Regex("""^\\s*([^:=]{2,60})\\s*[:=]\\s*(.*?)\\s*$""").find(line)
                ?: return@forEach
            val key = normalize(match.groupValues[1])
            val value = match.groupValues[2].trim().trim('"', '\'', '`')
            if (value.isNotBlank()) values[key] = value
        }

        fun find(vararg aliases: String): String? {
            val wanted = aliases.map(::normalize)
            return values.entries.firstOrNull { (key, _) ->
                wanted.any { alias -> key == alias || key.endsWith(" $alias") }
            }?.value
        }

        val nameserver = find(
            "nameserver", "slowdns nameserver", "fastdns nameserver",
            "slowdns ns", "fastdns ns", "ns", "tunnel domain"
        ) ?: return null
        val publicKey = find("public key", "dnstt public key", "slowdns public key", "fastdns public key")
            ?: return null
        val username = find("username", "ssh username", "user") ?: return null
        val password = find("password", "ssh password", "pass") ?: return null

        val host = find("host", "ssh host", "server", "server host").orEmpty()
        val sshPort = find("ssh port", "port ssh", "port")?.toIntOrNull() ?: 22
        val validity = find("validity", "expires", "duration", "account validity").orEmpty()

        // Basic structural checks catch truncated or unrelated TXT files.
        if (!nameserver.contains('.') || publicKey.length < 20 || username.isBlank() || password.isBlank()) return null

        return JSONObject().apply {
            put("transport", "dnstt")
            put("dns_server", "1.1.1.1")
            put("tunnel_domain", nameserver)
            put("public_key", publicKey)
            put("ssh_host", host)
            put("ssh_port", sshPort)
            put("username", username)
            put("password", password)
            put("account_validity", validity)
            put("provider", "LitronSSH")
        }.toString(2)
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
}
