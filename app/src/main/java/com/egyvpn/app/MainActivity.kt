package com.egyvpn.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
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
import androidx.core.content.ContextCompat
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

class MainActivity : ComponentActivity() {

    private var statusText by mutableStateOf("غير متصل")
    private var configLoaded by mutableStateOf(false)

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusText = intent?.getStringExtra(VpnTunnelService.EXTRA_MESSAGE)
                ?: "تم تحديث حالة الاتصال"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configLoaded = getFileStreamPath(VpnTunnelService.CONFIG_FILE).isFile

        val filter = IntentFilter(VpnTunnelService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }

        setContent {
            val configPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    try {
                        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("تعذر قراءة الملف")
                        val parsed = Config.parse(ByteArrayInputStream(bytes))
                        require(parsed.peers.isNotEmpty()) {
                            "ملف الإعداد لا يحتوي على بيانات السيرفر"
                        }
                        openFileOutput(VpnTunnelService.CONFIG_FILE, MODE_PRIVATE).use {
                            it.write(bytes)
                        }
                        configLoaded = true
                        statusText = "تم استيراد إعداد WireGuard بنجاح"
                    } catch (error: Exception) {
                        statusText = error.message?.take(140) ?: "ملف الإعداد غير صالح"
                    }
                }
            }

            val vpnPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    startVpnService(VpnTunnelService.ACTION_CONNECT)
                    statusText = "جارٍ إنشاء نفق WireGuard..."
                } else {
                    statusText = "لم يتم منح إذن VPN"
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
                            text = "اتصال WireGuard مشفّر",
                            color = Color.LightGray,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = statusText,
                            color = Color(0xFFFFD166),
                            fontSize = 18.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                configPicker.launch(
                                    arrayOf("text/plain", "application/octet-stream", "*/*")
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (configLoaded) "استبدال ملف إعداد السيرفر" else "استيراد ملف إعداد السيرفر")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (!configLoaded) {
                                    statusText = "استورد ملف إعداد WireGuard أولاً"
                                } else {
                                    val permissionIntent = VpnService.prepare(this@MainActivity)
                                    if (permissionIntent == null) {
                                        startVpnService(VpnTunnelService.ACTION_CONNECT)
                                        statusText = "جارٍ إنشاء نفق WireGuard..."
                                    } else {
                                        vpnPermissionLauncher.launch(permissionIntent)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("تشغيل VPN")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                startVpnService(VpnTunnelService.ACTION_DISCONNECT)
                                statusText = "جارٍ فصل الاتصال..."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("فصل VPN")
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "ملف الإعداد يُحفظ داخل التطبيق ولا يُرسل إلى أي خادم.",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "الإصدار 1.1",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    private fun startVpnService(action: String) {
        val intent = Intent(this, VpnTunnelService::class.java).setAction(action)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onDestroy() {
        unregisterReceiver(statusReceiver)
        super.onDestroy()
    }
}
