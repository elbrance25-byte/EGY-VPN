package com.egyvpn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egyvpn.app.core.EngineId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EgyVpnHome() }
    }
}

@Composable
private fun EgyVpnHome() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF171329)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("EGY VPN", color = Color.White, fontSize = 34.sp)
                Spacer(Modifier.height(12.dp))
                Text("اختر محرك الاتصال", color = Color.LightGray, fontSize = 17.sp)
                Spacer(Modifier.height(24.dp))
                EngineId.values().forEach { engine ->
                    OutlinedButton(
                        onClick = { /* Engine adapters are added in the integration stages. */ },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            when (engine) {
                                EngineId.SING_BOX -> "sing-box"
                                EngineId.DNSTT -> "DNSTT / Slipstream"
                                EngineId.MASTER_DNS -> "MasterDnsVPN"
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "المحركات قيد التجهيز. لن يظهر الاتصال كمتصل قبل اكتمال النفق واختبار مرور الإنترنت.",
                    color = Color(0xFFFFD166),
                    fontSize = 13.sp
                )
            }
        }
    }
}
