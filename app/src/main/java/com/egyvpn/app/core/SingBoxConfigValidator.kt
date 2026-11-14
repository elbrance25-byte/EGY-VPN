package com.egyvpn.app.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Performs local, fail-closed validation before a profile is handed to libbox.
 *
 * This is intentionally not a substitute for sing-box core validation: the core remains
 * responsible for validating the complete schema and starting the actual tunnel.
 */
object SingBoxConfigValidator {
    private const val MAX_CONFIG_BYTES = 1_048_576

    private val supportedOutboundTypes = setOf(
        "vless",
        "vmess",
        "trojan",
        "shadowsocks",
        "hysteria2"
    )

    data class Result(
        val valid: Boolean,
        val outboundTypes: Set<String> = emptySet(),
        val error: String? = null
    )

    fun validate(profile: TunnelProfile): Result {
        if (profile.engineId != EngineId.SING_BOX) {
            return Result(false, error = "Profile is not assigned to sing-box")
        }
        if (profile.configuration.isBlank()) {
            return Result(false, error = "Configuration is empty")
        }
        if (profile.configuration.toByteArray(Charsets.UTF_8).size > MAX_CONFIG_BYTES) {
            return Result(false, error = "Configuration exceeds 1 MiB")
        }

        return try {
            val root = JSONObject(profile.configuration)
            val outbounds = root.optJSONArray("outbounds")
                ?: return Result(false, error = "Configuration must contain an outbounds array")

            val found = readSupportedOutbounds(outbounds)
            if (found.isEmpty()) {
                return Result(false, error = "No supported outbound found")
            }

            val requested = profile.protocol.trim().lowercase()
            if (requested != "sing-box-json" && requested !in found) {
                return Result(
                    false,
                    outboundTypes = found,
                    error = "Selected protocol does not match a supported outbound in this profile"
                )
            }

            Result(true, outboundTypes = found)
        } catch (_: JSONException) {
            Result(false, error = "Configuration is not valid JSON")
        } catch (_: RuntimeException) {
            Result(false, error = "Configuration could not be inspected")
        }
    }

    private fun readSupportedOutbounds(outbounds: JSONArray): Set<String> {
        val found = linkedSetOf<String>()
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            val type = outbound.optString("type").trim().lowercase()
            if (type in supportedOutboundTypes) {
                found += type
            }
        }
        return found
    }
}
