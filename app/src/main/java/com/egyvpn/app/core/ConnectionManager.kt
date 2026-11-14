package com.egyvpn.app.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes engine lifecycle: only one transport can own the app VPN/TUN session.
 */
class ConnectionManager(
    private val engines: Map<EngineId, VpnEngine>
) {
    private val mutex = Mutex()
    private var activeEngine: VpnEngine? = null

    suspend fun connect(profile: TunnelProfile) = mutex.withLock {
        require(profile.id.isNotBlank()) { "Profile id is required" }
        val target = requireNotNull(engines[profile.engineId]) {
            "Selected engine is not installed: ${profile.engineId}"
        }
        if (activeEngine === target && target.currentState() == EngineState.CONNECTED) return@withLock
        activeEngine?.stop()
        activeEngine = null
        target.start(profile)
        activeEngine = target
    }

    suspend fun disconnect() = mutex.withLock {
        activeEngine?.stop()
        activeEngine = null
    }

    fun activeEngineId(): EngineId? = activeEngine?.id
}
