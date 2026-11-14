package com.egyvpn.app.vpn

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.NetworkInterface as BoxNetworkInterface
import java.net.Inet6Address
import java.net.NetworkInterface as JavaNetworkInterface

/**
 * Minimal, unprivileged Android platform bridge for sing-box.
 * Unsupported optional root/SSH/bridge integrations fail closed.
 */
internal class EgyVpnPlatform(private val service: VpnService) : PlatformInterface {
    private var tunDescriptor: android.os.ParcelFileDescriptor? = null

    override fun usePlatformAutoDetectInterfaceControl() = true
    override fun autoDetectInterfaceControl(fd: Int) {
        check(service.protect(fd)) { "Unable to protect sing-box transport socket" }
    }

    override fun openTun(options: TunOptions): Int {
        check(VpnService.prepare(service) == null) { "VPN permission has not been granted" }
        tunDescriptor?.close()
        val builder = service.Builder().setSession("EGY VPN").setMtu(options.mtu)
        val v4 = options.inet4Address
        while (v4.hasNext()) {
            val item = v4.next()
            builder.addAddress(item.address(), item.prefix())
        }
        val v6 = options.inet6Address
        while (v6.hasNext()) {
            val item = v6.next()
            builder.addAddress(item.address(), item.prefix())
        }
        if (options.autoRoute) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            if (options.dnsMode.value != Libbox.DNSModeDisabled) {
                val dns = options.dnsServerAddress
                while (dns.hasNext()) builder.addDnsServer(dns.next())
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val r4 = options.inet4RouteAddress
                if (r4.hasNext()) {
                    while (r4.hasNext()) { val route = r4.next(); builder.addRoute(route.address(), route.prefix()) }
                } else if (options.inet4Address.hasNext()) {
                    builder.addRoute("0.0.0.0", 0)
                }
                val r6 = options.inet6RouteAddress
                if (r6.hasNext()) {
                    while (r6.hasNext()) { val route = r6.next(); builder.addRoute(route.address(), route.prefix()) }
                } else if (options.inet6Address.hasNext()) {
                    builder.addRoute("::", 0)
                }
            } else {
                val r4 = options.inet4RouteRange
                while (r4.hasNext()) {
                    val route = r4.next()
                    builder.addRoute(route.address(), route.prefix())
                }
                val r6 = options.inet6RouteRange
                while (r6.hasNext()) {
                    val route = r6.next()
                    builder.addRoute(route.address(), route.prefix())
                }
            }
        }
        val pfd = builder.establish() ?: error("Android refused to establish the VPN interface")
        tunDescriptor = pfd
        return pfd.fd
    }

    override fun findConnectionOwner(ipProtocol: Int, sourceAddress: String?, sourcePort: Int,
        destinationAddress: String?, destinationPort: Int): ConnectionOwner {
        return Libbox.findConnectionOwner(ipProtocol, sourceAddress, sourcePort, destinationAddress, destinationPort)
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        val connectivity = service.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return
        val properties = connectivity.getLinkProperties(network) ?: return
        val caps = connectivity.getNetworkCapabilities(network)
        val name = properties.interfaceName ?: return
        val index = runCatching { JavaNetworkInterface.getByName(name)?.index ?: 0 }.getOrDefault(0)
        val metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        listener?.updateDefaultInterface(name, index, metered, false)
    }
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {}
    override fun getInterfaces(): NetworkInterfaceIterator {
        val values = runCatching {
            JavaNetworkInterface.getNetworkInterfaces()?.toList().orEmpty().mapNotNull { network ->
                runCatching {
                    BoxNetworkInterface().apply {
                        name = network.name
                        index = network.index
                        mtu = network.mtu
                        addresses = StringValues(network.interfaceAddresses.map { address ->
                            val host = if (address.address is Inet6Address) address.address.hostAddress.substringBefore('%') else address.address.hostAddress
                            "$host/${address.networkPrefixLength}"
                        })
                        type = Libbox.InterfaceTypeOther
                    }
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
        return NetworkValues(values)
    }
    override fun underNetworkExtension() = false
    override fun includeAllNetworks() = false
    override fun readWIFIState(): WIFIState? = null
    override fun clearDNSCache() {}
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun sendNotification(notification: Notification?) {}
    override fun cancelNotification(identifier: String?, typeID: Int) {}
    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {}
    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {}
    override fun registerMyInterface(name: String?) {}
    override fun usePlatformShell() = false
    override fun checkPlatformShell() { error("Platform shell is not supported") }
    override fun openShellSession(user: PlatformUser?, command: String?, environ: StringIterator?,
        term: String?, rows: Int, cols: Int): ShellSession = error("Platform shell is not supported")
    override fun lookupUser(username: String?): PlatformUser = error("Platform users are not supported")
    override fun lookupSFTPServer(): String = error("SFTP is not supported")
    override fun readSystemSSHHostKey(): String = error("SSH is not supported")
    override fun tailscaleHostname(): String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    override fun usePlatformBridge() = false
    override fun createBridge(options: BridgeOptions?): BridgeSession = error("Platform bridge is not supported")

    fun closeTun() {
        runCatching { tunDescriptor?.close() }
        tunDescriptor = null
    }

    private class StringValues(private val values: List<String>) : StringIterator {
        private var index = 0
        override fun len() = values.size
        override fun hasNext() = index < values.size
        override fun next() = values[index++]
    }
    private class NetworkValues(private val values: List<BoxNetworkInterface>) : NetworkInterfaceIterator {
        private var index = 0
        override fun hasNext() = index < values.size
        override fun next() = values[index++]
    }
}
