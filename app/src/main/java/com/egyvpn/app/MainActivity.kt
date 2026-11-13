
package com.egyvpn.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var status by remember {
                mutableStateOf("غير متصل")
            }

            val vpnPermissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        val serviceIntent = Intent(
                            this@MainActivity,
                            VpnTunnelService::class.java
                        )
                        startService(serviceIntent)
                        status = "تم منح الإذن وبدأت الخدمة المبدئية"
                    } else {
                        status = "لم يتم منح الإذن"
                    }
                }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF171329)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "EGY VPN",
                            color = Color.White,
                            fontSize = 34.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "اتصال آمن وسهل",
                            color = Color.LightGray,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        Text(
                            text = status,
                            color = Color(0xFFFFD166),
                            fontSize = 20.sp
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Button(
                            onClick = {
                                val intent = VpnService.prepare(
                                    this@MainActivity
                                )

                                if (intent == null) {
                                    val serviceIntent = Intent(
                                        this@MainActivity,
                                        VpnTunnelService::class.java
                                    )
                                    startService(serviceIntent)
                                    status = "بدأت الخدمة المبدئية"
                                } else {
                                    vpnPermissionLauncher.launch(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("تشغيل VPN")
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "الإصدار 1.0",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
