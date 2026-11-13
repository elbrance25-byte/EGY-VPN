
package com.egyvpn.app

import android.net.VpnService
import android.content.Intent
import android.os.ParcelFileDescriptor

class VpnTunnelService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        // VPN engine will be integrated in a later stage.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
