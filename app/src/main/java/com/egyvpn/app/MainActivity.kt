package com.egyvpn.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egyvpn.app.core.EngineId

private val Navy = Color(0xFF171329)
private val Panel = Color(0xFF24203A)
private val Purple = Color(0xFF8B5CF6)
private val Gold = Color(0xFFFFC857)
private val Muted = Color(0xFFB9B4C9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EgyVpnHome() }
    }
}

@Composable
private fun EgyVpnHome() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("egy_vpn_private", 0) }
    var selectedEngine by remember {
        mutableStateOf(prefs.getString("engine", EngineId.DNSTT.name) ?: EngineId.DNSTT.name)
    }
    var profile by remember { mutableStateOf(prefs.getString("profile", "") ?: "") }
    var profileName by remember { mutableStateOf(prefs.getString("profile_name", "") ?: "") }

    val importProfile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("تعذر قراءة الملف")
            }.onSuccess {
                profile = it
                if (profileName.isBlank()) profileName = "إعداد مستورد"
                Toast.makeText(context, "تم استيراد الإعداد — احفظه للاحتفاظ به", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "فشل قراءة الملف", Toast.LENGTH_LONG).show()
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("EGY VPN", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                        Text("اتصالك، بطريقتك", color = Muted, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier.background(Color(0xFF332A4B), CircleShape).padding(horizontal = 13.dp, vertical = 8.dp)
                    ) {
                        Text("BETA", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.background(Color(0xFF34294F), CircleShape).padding(20.dp)
                        ) {
                            Text("VPN", color = Gold, fontSize = 25.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("غير متصل", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("اختر محركًا وأضف ملف إعداد الاتصال", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                Toast.makeText(context, "لم يتم تفعيل الاتصال بعد: يجري استكمال ربط محرك النفق الحقيقي.", Toast.LENGTH_LONG).show()
                            },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFF51476B))
                        ) {
                            Text("الاتصال غير متاح حتى اكتمال المحرك", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                SectionTitle("محرك الاتصال")
                Spacer(Modifier.height(10.dp))
                EngineId.values().forEach { engine ->
                    val title = when (engine) {
                        EngineId.SING_BOX -> "sing-box"
                        EngineId.DNSTT -> "DNSTT / Slipstream"
                        EngineId.MASTER_DNS -> "MasterDnsVPN"
                    }
                    val subtitle = when (engine) {
                        EngineId.SING_BOX -> "VLESS • VMess • Trojan • Shadowsocks • Hysteria2"
                        EngineId.DNSTT -> "نفق DNS مشفر"
                        EngineId.MASTER_DNS -> "محرك DNS مستقل"
                    }
                    val selected = selectedEngine == engine.name
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable {
                            selectedEngine = engine.name
                            prefs.edit().putString("engine", engine.name).apply()
                        },
                        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF382B55) else Panel),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.background(if (selected) Gold else Color(0xFF514A60), CircleShape).padding(5.dp)
                            ) { Text(if (selected) "✓" else " ", color = Navy, fontSize = 12.sp) }
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(subtitle, color = Muted, fontSize = 11.sp)
                            }
                            if (selected) Text("محدد", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                SectionTitle("إعداد الاتصال")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("اسم الإعداد") },
                    placeholder = { Text("مثال: السيرفر الأول") },
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = profile,
                    onValueChange = { profile = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 9,
                    label = { Text("الصق رابط أو ملف الإعداد") },
                    placeholder = { Text("VLESS / VMess / Trojan / JSON أو إعداد DNS المدعوم") },
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { importProfile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("استيراد ملف") }
                    Button(
                        onClick = {
                            if (profile.isBlank()) {
                                Toast.makeText(context, "أضف إعداد الاتصال أولًا", Toast.LENGTH_SHORT).show()
                            } else {
                                prefs.edit()
                                    .putString("profile", profile)
                                    .putString("profile_name", profileName.ifBlank { "إعداد محفوظ" })
                                    .apply()
                                Toast.makeText(context, "تم حفظ الإعداد على الجهاز", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple)
                    ) { Text("حفظ الإعداد") }
                }
                TextButton(
                    onClick = {
                        profile = ""
                        profileName = ""
                        prefs.edit().remove("profile").remove("profile_name").apply()
                    }
                ) { Text("مسح الإعداد المحفوظ", color = Muted) }

                Spacer(Modifier.height(18.dp))
                Text(
                    "تنبيه: واجهة التطبيق وحفظ الإعدادات يعملان. الاتصال الفعلي سيظل مقفولًا حتى اكتمال ربط المحرك واختبار مرور الإنترنت؛ لن نعرض حالة اتصال وهمية.",
                    color = Gold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF30283B), RoundedCornerShape(14.dp)).padding(14.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text("EGY VPN • إصدار تجريبي", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(
        value,
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold
    )
}
