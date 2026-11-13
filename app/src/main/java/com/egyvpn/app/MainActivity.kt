
package com.egyvpn.app

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
                    status = if (result.resultCode == RESULT_OK) {
                        "تم منح إذن VPN"
                    } else {
                        "لم يتم منح الإذن"
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
                                    status = "تم منح إذن VPN مسبقًا"
                                } else {
                                    vpnPermissionLauncher.launch(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("طلب إذن VPN")
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
