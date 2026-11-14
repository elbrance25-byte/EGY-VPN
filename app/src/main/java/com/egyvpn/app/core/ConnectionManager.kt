package com.egyvpn.app.core

/**
 * Serializes engine lifecycle: only one transport can own the app VPN/TUN session.
 * The Android VpnService/TUN coordinator is supplied by the platform integration.
 */
class ConnectionManager(
    private val engines: Map<EngineId, VpnEngine>
) {
    private var activeEngine: VpnEngine? = null

    suspend fun connect(profile: TunnelProfile) {
        require(profile.id.isNotBlank()) { "Profile id is required" }
        val target = requireNotNull(engines[profile.engineId]) {
            "Selected engine is not installed: ${profile.engineId}"
        }
        if (activeEngine === target && target.currentState() == EngineState.CONNECTED) return
        activeEngine?.stop()
        activeEngine = null
        target.start(profile)
        activeEngine = target
    }

    suspend fun disconnect() {
        activeEngine?.stop()
        activeEngine = null
    }

    fun activeEngineId(): EngineId? = activeEngine?.id
}
