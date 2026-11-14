package com.egyvpn.app

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.VpnService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
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
import com.egyvpn.app.core.FreeServerRepository
import com.egyvpn.app.core.EngineId
import com.egyvpn.app.core.SingBoxConfigValidator
import com.egyvpn.app.core.TunnelProfile
import com.egyvpn.app.vpn.EgyVpnService
import com.egyvpn.app.dnstt.DnsttVpnService
import com.egyvpn.app.masterdns.MasterDnsVpnService
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Navy = Color(0xFF171329)
private val Panel = Color(0xFF24203A)
private val Purple = Color(0xFF8B5CF6)
private val Gold = Color(0xFFFFC857)
private val Muted = Color(0xFFB9B4C9)

/**
 * Android permits only one active VpnService session per app/device.
 * Stop any previous engine before starting a different backend so stale
 * services cannot race to replace the system VPN interface.
 */
private fun stopOtherVpnEngines(context: android.content.Context, keep: String) {
    // stopService avoids starting a background service solely to send it a stop action.
    // Each service releases its TUN and native engine from onDestroy().
    if (keep != EngineId.SING_BOX.name) {
        context.stopService(Intent(context, EgyVpnService::class.java))
    }
    if (keep != EngineId.DNSTT.name) {
        context.stopService(Intent(context, DnsttVpnService::class.java))
    }
    if (keep != EngineId.MASTER_DNS.name) {
        context.stopService(Intent(context, MasterDnsVpnService::class.java))
    }
}

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
        mutableStateOf(prefs.getString("engine", EngineId.SING_BOX.name) ?: EngineId.SING_BOX.name)
    }
    var profile by remember { mutableStateOf(prefs.getString("profile", "") ?: "") }
    var profileName by remember { mutableStateOf(prefs.getString("profile_name", "") ?: "") }
    var isConnected by remember { mutableStateOf(prefs.getBoolean("connected", false)) }
    var pendingConfig by remember { mutableStateOf<String?>(null) }
    var pendingEngine by remember { mutableStateOf(EngineId.SING_BOX.name) }
    var connectedEngine by remember { mutableStateOf(prefs.getString("connected_engine", EngineId.SING_BOX.name) ?: EngineId.SING_BOX.name) }
    var fetchingServers by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf("مصادر مجتمعية مجانية؛ قد تتوقف الخوادم في أي وقت") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val startVpnService: (String) -> Unit = { config ->
        stopOtherVpnEngines(context, EngineId.SING_BOX.name)
        ContextCompat.startForegroundService(
            context,
            Intent(context, EgyVpnService::class.java).apply {
                action = EgyVpnService.ACTION_CONNECT
                putExtra(EgyVpnService.EXTRA_CONFIG, config)
            }
        )
    }
    val startDnsttService: (String) -> Unit = { raw ->
        stopOtherVpnEngines(context, EngineId.DNSTT.name)
        val config = JSONObject(raw)
        val transport = config.optString("transport", "dnstt").lowercase()
        ContextCompat.startForegroundService(
            context,
            Intent(context, DnsttVpnService::class.java).apply {
                action = DnsttVpnService.ACTION_CONNECT
                putExtra(DnsttVpnService.EXTRA_PROXY_HOST, config.optString("proxy_host", "127.0.0.1"))
                putExtra(DnsttVpnService.EXTRA_PROXY_PORT, config.optInt("proxy_port", 7000))
                putExtra(DnsttVpnService.EXTRA_DNS_SERVER, config.optString("dns_server", ""))
                putExtra(DnsttVpnService.EXTRA_TUNNEL_DOMAIN, config.optString("tunnel_domain", ""))
                putExtra(DnsttVpnService.EXTRA_PUBLIC_KEY, config.optString("public_key", ""))
                putExtra(DnsttVpnService.EXTRA_TRANSPORT_TYPE, transport)
                putExtra(DnsttVpnService.EXTRA_CONGESTION_CONTROL, config.optString("congestion_control", "dcubic"))
                putExtra(DnsttVpnService.EXTRA_KEEP_ALIVE_INTERVAL, config.optInt("keep_alive_interval", 400))
                putExtra(DnsttVpnService.EXTRA_GSO, config.optBoolean("gso", false))
                putExtra(DnsttVpnService.EXTRA_SSH_MODE, false)
            }
        )
    }
    val startMasterDnsService: (String) -> Unit = { raw ->
        stopOtherVpnEngines(context, EngineId.MASTER_DNS.name)
        ContextCompat.startForegroundService(
            context,
            Intent(context, MasterDnsVpnService::class.java).apply {
                action = MasterDnsVpnService.ACTION_CONNECT
                putExtra(MasterDnsVpnService.EXTRA_CONFIG, raw)
            }
        )
    }
    val vpnPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val config = pendingConfig
        if (result.resultCode == Activity.RESULT_OK && config != null) {
            when (pendingEngine) {
                EngineId.DNSTT.name -> startDnsttService(config)
                EngineId.MASTER_DNS.name -> startMasterDnsService(config)
                else -> startVpnService(config)
            }
        } else {
            Toast.makeText(context, "لم يتم منح إذن VPN", Toast.LENGTH_SHORT).show()
        }
        pendingConfig = null
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                if (intent?.action == EgyVpnService.ACTION_STATE) {
                    isConnected = intent.getBooleanExtra("connected", false)
                    connectedEngine = intent.getStringExtra("engine") ?: connectedEngine
                    prefs.edit().putBoolean("connected", isConnected).putString("connected_engine", connectedEngine).apply()
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(EgyVpnService.ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

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
                        Text(if (isConnected) "متصل" else "غير متصل", color = if (isConnected) Gold else Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("اختر المحرك وأضف ملف إعداد JSON", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                if (isConnected) {
                                    when (connectedEngine) {
                                        EngineId.DNSTT.name -> context.startService(Intent(context, DnsttVpnService::class.java).setAction(DnsttVpnService.ACTION_DISCONNECT))
                                        EngineId.MASTER_DNS.name -> context.startService(Intent(context, MasterDnsVpnService::class.java).setAction(MasterDnsVpnService.ACTION_STOP))
                                        else -> context.startService(Intent(context, EgyVpnService::class.java).setAction(EgyVpnService.ACTION_STOP))
                                    }
                                } else {
                                    val candidate = profile.trim()
                                    when (selectedEngine) {
                                        EngineId.SING_BOX.name -> {
                                            val check = SingBoxConfigValidator.validate(
                                                TunnelProfile("default", EngineId.SING_BOX, "sing-box-json", candidate)
                                            )
                                            if (!check.valid) {
                                                Toast.makeText(context, check.error ?: "إعداد sing-box غير صالح", Toast.LENGTH_LONG).show()
                                            } else {
                                                pendingConfig = candidate
                                                pendingEngine = EngineId.SING_BOX.name
                                                val permissionIntent = VpnService.prepare(context)
                                                if (permissionIntent == null) startVpnService(candidate)
                                                else vpnPermission.launch(permissionIntent)
                                            }
                                        }
                                        EngineId.DNSTT.name -> {
                                            val config = runCatching { JSONObject(candidate) }.getOrNull()
                                            if (config == null) {
                                                Toast.makeText(context, "إعداد DNSTT يجب أن يكون JSON صالحًا", Toast.LENGTH_LONG).show()
                                            } else {
                                                val transport = config.optString("transport", "dnstt").lowercase()
                                                val dns = config.optString("dns_server").trim()
                                                val domain = config.optString("tunnel_domain").trim()
                                                val key = config.optString("public_key").trim()
                                                if (transport !in setOf("dnstt", "slipstream") || dns.isBlank() || domain.isBlank() || (transport == "dnstt" && key.isBlank())) {
                                                    Toast.makeText(context, "يلزم dns_server و tunnel_domain، و public_key مع DNSTT", Toast.LENGTH_LONG).show()
                                                } else {
                                                    pendingConfig = candidate
                                                    pendingEngine = EngineId.DNSTT.name
                                                    val permissionIntent = VpnService.prepare(context)
                                                    if (permissionIntent == null) startDnsttService(candidate)
                                                    else vpnPermission.launch(permissionIntent)
                                                }
                                            }
                                        }
                                        EngineId.MASTER_DNS.name -> {
                                            val config = runCatching { JSONObject(candidate) }.getOrNull()
                                            if (config == null) {
                                                Toast.makeText(context, "إعداد MasterDnsVPN يجب أن يكون JSON صالحًا", Toast.LENGTH_LONG).show()
                                            } else {
                                                val domains = config.optString("domains").trim()
                                                val key = config.optString("encryption_key").trim()
                                                val resolvers = config.optString("resolvers").trim()
                                                if (domains.isBlank() || key.isBlank() || resolvers.isBlank()) {
                                                    Toast.makeText(context, "يلزم domains و encryption_key و resolvers", Toast.LENGTH_LONG).show()
                                                } else {
                                                    pendingConfig = candidate
                                                    pendingEngine = EngineId.MASTER_DNS.name
                                                    val permissionIntent = VpnService.prepare(context)
                                                    if (permissionIntent == null) startMasterDnsService(candidate)
                                                    else vpnPermission.launch(permissionIntent)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = isConnected || profile.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) Color(0xFF8B3434) else Purple,
                                disabledContainerColor = Color(0xFF51476B)
                            )
                        ) {
                            Text(if (isConnected) "قطع الاتصال" else when (selectedEngine) { EngineId.DNSTT.name -> "اتصال عبر DNSTT / Slipstream"; EngineId.MASTER_DNS.name -> "اتصال عبر MasterDnsVPN"; else -> "اتصال عبر sing-box" }, color = Color.White, fontSize = 14.sp)
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
                        EngineId.DNSTT -> "DNSTT / Slipstream عبر DNS"
                        EngineId.MASTER_DNS -> "نفق DNS مع تشفير وتوجيه كامل"
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("السيرفرات المجانية", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(serverStatus, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                fetchingServers = true
                                serverStatus = "جارٍ جلب المصادر وفحص إمكانية الوصول..."
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { FreeServerRepository.fetchFirstReachable() }
                                    }
                                    fetchingServers = false
                                    result.onSuccess { server ->
                                        selectedEngine = EngineId.SING_BOX.name
                                        prefs.edit().putString("engine", selectedEngine).apply()
                                        profile = server.config
                                        profileName = "مجاني • ${server.protocol.uppercase()} • ${server.name.substringAfterLast('-')}"
                                        serverStatus = "تم العثور على نقطة يمكن الوصول إلى منفذها. سيجري المحرك التحقق من المصادقة والنفق عند الاتصال."
                                        Toast.makeText(context, "تم تحميل إعداد خادم مجاني", Toast.LENGTH_LONG).show()
                                    }.onFailure { error ->
                                        serverStatus = error.message ?: "لم يتم العثور على خادم متاح حاليًا"
                                        Toast.makeText(context, serverStatus, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !fetchingServers && !isConnected,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple)
                        ) {
                            Text(if (fetchingServers) "جارٍ الفحص..." else "جلب وفحص سيرفر مجاني", color = Color.White)
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
                    placeholder = { Text(when (selectedEngine) { EngineId.DNSTT.name -> "DNSTT / Slipstream JSON"; EngineId.MASTER_DNS.name -> "MasterDnsVPN JSON"; else -> "sing-box JSON" }) },
                    shape = RoundedCornerShape(14.dp)
                )
                Text(
                    when (selectedEngine) {
                        EngineId.DNSTT.name -> "DNSTT يحتاج dns_server و tunnel_domain و public_key. مع Slipstream استخدم transport=slipstream ولا تحتاج public_key. دعم Slipstream داخل نسخة ARM64 فقط."
                        EngineId.MASTER_DNS.name -> "MasterDnsVPN يحتاج سيرفرًا مهيأً، مع domains و encryption_key و resolvers (عنوان IP لكل سطر)."
                        else -> "sing-box يقبل ملف إعداد JSON كاملًا يتضمن outbounds صالحًا."
                    },
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
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
                    "الخوادم المجانية من مصادر عامة مستقلة وليست مملوكة لنا. فحص المنفذ لا يضمن نجاح النفق أو الخصوصية؛ تجنب استخدامها للحسابات الحساسة.",
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
