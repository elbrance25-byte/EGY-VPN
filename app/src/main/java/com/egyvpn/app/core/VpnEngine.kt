package com.egyvpn.app.core

/**
 * Contract shared by the three EGY VPN transport engines.
 * CONNECTED is valid only after the engine is running and Android TUN is attached.
 */
interface VpnEngine {
    val id: EngineId
    val displayName: String
    val supportedProtocols: Set<String>
    suspend fun start(profile: TunnelProfile)
    suspend fun stop()
    fun currentState(): EngineState
}

enum class EngineId { SING_BOX, DNSTT, MASTER_DNS }
enum class EngineState { STOPPED, STARTING, CONNECTED, STOPPING, FAILED }

/** Opaque engine configuration. Never write secrets from this object to logs. */
data class TunnelProfile(
    val id: String,
    val engineId: EngineId,
    val protocol: String,
    val configuration: String
)
