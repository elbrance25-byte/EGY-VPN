
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
        // سيتم إضافة محرك الاتصال الحقيقي في المرحلة التالية.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
