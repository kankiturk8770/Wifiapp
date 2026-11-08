package cafe.kankipro.modem

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun JSONObject.s(k: String): String = optString(k)

@Composable
fun App(st: AppState) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(st.message) {
        val m = st.message ?: return@LaunchedEffect
        snack.showSnackbar(m)
        st.message = null
    }
    Scaffold(
        containerColor = K.bg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { if (st.loggedIn) BottomBar(st) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!st.loggedIn) LoginScreen(st)
            else when (st.tab) {
                Tab.HOME -> HomeScreen(st)
                Tab.WIFI -> WifiScreen(st)
                Tab.DEVICES -> DevicesScreen(st)
                Tab.SMS -> SmsScreen(st)
                Tab.MORE -> MoreScreen(st)
            }
        }
    }
}

@Composable
private fun BottomBar(st: AppState) {
    val unread = st.status.s("sms_unread_num").toIntOrNull() ?: 0
    val items = listOf(
        Triple(Tab.HOME, Icons.Filled.Home, "خانه"),
        Triple(Tab.WIFI, Icons.Filled.Wifi, "وای‌فای"),
        Triple(Tab.DEVICES, Icons.Filled.Devices, "دستگاه‌ها"),
        Triple(Tab.SMS, Icons.AutoMirrored.Filled.Message, if (unread > 0) "پیامک ($unread)" else "پیامک"),
        Triple(Tab.MORE, Icons.Filled.MoreHoriz, "بیشتر"),
    )
    NavigationBar(containerColor = K.card) {
        items.forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = st.tab == tab,
                onClick = { st.tab = tab },
                icon = { Icon(icon, null) },
                label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = K.good, selectedTextColor = K.good,
                    unselectedIconColor = K.dim, unselectedTextColor = K.dim, indicatorColor = K.line,
                ),
            )
        }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

// ---------------- login ----------------

@Composable
private fun LoginScreen(st: AppState) {
    var pass by remember { mutableStateOf(st.savedPassword) }
    var show by remember { mutableStateOf(false) }
    var remember by remember { mutableStateOf(true) }
    Page {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(K.card).align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Wifi, null, tint = K.good, modifier = Modifier.size(44.dp)) }
        Text(
            "مودم من", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = K.text,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "اول گوشی را به وای‌فای مودم وصل کن. رمز همان رمز صفحهٔ مدیریت مودم است.",
            color = K.dim, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = st.host, onValueChange = { st.host = it.trim() },
            label = { Text("آدرس مودم") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("رمز مدیریت") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { remember = !remember }) {
            Checkbox(checked = remember, onCheckedChange = { remember = it })
            Text("رمز یادم بماند", color = K.text)
        }
        Button(
            onClick = { st.login(pass, remember) },
            enabled = !st.busy && pass.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (st.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("ورود", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------- home ----------------

@Composable
private fun HomeScreen(st: AppState) {
    val s = st.status
    val connected = s.s("ppp_status") == "ppp_connected"
    val connecting = s.s("ppp_status").contains("connecting")
    val level = s.s("signalbar").toIntOrNull() ?: 0
    Page {
        if (st.offline) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WifiOff, null, tint = K.bad)
                    Spacer(Modifier.width(10.dp))
                    Text("ارتباط با مودم قطع است. به وای‌فای مودم وصلی؟", color = K.text)
                }
            }
        }
        Card {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.s("network_provider").ifEmpty { s.s("spn_name_data").let(Codec::ucs2Decode) }.ifEmpty { "اپراتور" },
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = K.text,
                        )
                        Text(netName(s.s("network_type")), color = K.dim, fontSize = 13.sp)
                    }
                    Bars(level, if (level <= 1) K.bad else if (level <= 2) K.warn else K.good)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(12.dp).clip(CircleShape)
                            .background(if (connected) K.good else if (connecting) K.warn else K.bad),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            connected -> "اینترنت وصل است"
                            connecting -> "در حال وصل شدن…"
                            else -> "اینترنت قطع است"
                        },
                        color = K.text, fontSize = 16.sp, modifier = Modifier.weight(1f),
                    )
                    if (connected) {
                        OutlinedButton(onClick = { st.connect(false) }, enabled = !st.busy) { Text("قطع کن") }
                    } else {
                        Button(onClick = { st.connect(true) }, enabled = !st.busy && !connecting) { Text("وصل کن") }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(Icons.Filled.ArrowDownward, "دانلود الان", Codec.speed(s.s("realtime_rx_thrpt")), Modifier.weight(1f))
            Stat(Icons.Filled.ArrowUpward, "آپلود الان", Codec.speed(s.s("realtime_tx_thrpt")), Modifier.weight(1f), K.blue)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val charging = s.s("battery_charging") == "1"
            Stat(
                if (charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                if (charging) "باتری (در حال شارژ)" else "باتری",
                battery(s), Modifier.weight(1f),
                tint = (s.s("battery_vol_percent").toIntOrNull() ?: 100).let { if (it < 20) K.bad else K.good },
            )
            Stat(Icons.Filled.Devices, "دستگاه وصل", s.s("sta_count").ifEmpty { "—" }, Modifier.weight(1f), K.blue)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val session = sum(s.s("realtime_rx_bytes"), s.s("realtime_tx_bytes"))
            val month = sum(s.s("monthly_rx_bytes"), s.s("monthly_tx_bytes"))
            Stat(Icons.Filled.DataUsage, "مصرف این اتصال", Codec.bytes(session), Modifier.weight(1f), K.warn)
            Stat(Icons.Filled.DataUsage, "مصرف این ماه", Codec.bytes(month), Modifier.weight(1f), K.warn)
        }
        Stat(Icons.Filled.Timer, "مدت این اتصال", Codec.duration(s.s("realtime_time")))
    }
}

private fun sum(a: String, b: String): String? {
    val x = a.toDoubleOrNull() ?: return null
    val y = b.toDoubleOrNull() ?: return null
    return (x + y).toString()
}

private fun battery(s: JSONObject): String {
    s.optString("battery_vol_percent").toIntOrNull()?.let { return "$it٪" }
    // Some firmware only gives a level 0..4.
    s.optString("battery_pers").toIntOrNull()?.let { return "${it * 25}٪" }
    return "—"
}

private fun netName(t: String): String = when (t.uppercase()) {
    "LTE", "LTE_A", "LTE-A", "4G", "E-UTRAN" -> "4G (LTE)"
    "", "NO_SERVICE", "LIMITED_SERVICE" -> "بدون شبکه"
    "WCDMA", "HSPA", "HSDPA", "HSUPA", "HSPA+", "DC-HSPA+", "UMTS" -> "3G ($t)"
    "GSM", "GPRS", "EDGE" -> "2G ($t)"
    "NR", "5G", "ENDC" -> "5G"
    else -> t
}

// ---------------- wifi ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiScreen(st: AppState) {
    LaunchedEffect(Unit) { st.loadWifi() }
    val w = st.wifi
    Page {
        Title("وای‌فای", "اسم، رمز و تعداد کاربر وای‌فای مودم")
        if (w == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Page
        }
        val oldPass = w.s("WPAPSK1").ifEmpty {
            runCatching { String(java.util.Base64.getDecoder().decode(w.s("WPAPSK1_encode"))) }.getOrDefault("")
        }
        var ssid by remember(w) { mutableStateOf(w.s("SSID1")) }
        var pass by remember(w) { mutableStateOf(oldPass) }
        var mode by remember(w) { mutableStateOf(w.s("AuthMode").ifEmpty { "WPA2PSK" }) }
        var hidden by remember(w) { mutableStateOf(w.s("HideSSID") == "1") }
        var max by remember(w) { mutableStateOf(w.s("MAX_Access_num").ifEmpty { "10" }) }
        var show by remember { mutableStateOf(false) }
        var ask by remember { mutableStateOf(false) }
        var open by remember { mutableStateOf(false) }
        val modes = listOf(
            "OPEN" to "بدون رمز (همه وصل می‌شوند)",
            "WPA2PSK" to "WPA2 (پیشنهادی)",
            "WPAPSKWPA2PSK" to "WPA/WPA2",
            "WPA3Personal" to "WPA3",
            "WPA2WPA3" to "WPA2/WPA3",
        )
        val ssidOk = ssid.toByteArray().size in 1..32
        val passOk = mode == "OPEN" || pass.length in 8..63
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    ssid, { ssid = it }, label = { Text("اسم وای‌فای") }, singleLine = true,
                    isError = !ssidOk, modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                    OutlinedTextField(
                        value = modes.firstOrNull { it.first == mode }?.second ?: mode,
                        onValueChange = {}, readOnly = true, label = { Text("نوع امنیت") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        modes.forEach { (k, v) ->
                            DropdownMenuItem(text = { Text(v) }, onClick = { mode = k; open = false })
                        }
                    }
                }
                if (mode != "OPEN") {
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("رمز وای‌فای (۸ تا ۶۳ حرف)") }, singleLine = true,
                        isError = !passOk, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { show = !show }) {
                                Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                            }
                        },
                    )
                }
                OutlinedTextField(
                    max, { v -> max = v.filter { it.isDigit() }.take(2) }, label = { Text("حداکثر دستگاه هم‌زمان") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("مخفی کردن اسم", color = K.text)
                        Text("در فهرست وای‌فای گوشی‌ها دیده نمی‌شود", color = K.dim, fontSize = 12.sp)
                    }
                    Switch(checked = hidden, onCheckedChange = { hidden = it })
                }
                Button(
                    onClick = { ask = true }, enabled = !st.busy && ssidOk && passOk && max.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("ذخیره") }
            }
        }
        if (ask) {
            AlertDialog(
                onDismissRequest = { ask = false },
                title = { Text("ذخیره شود؟") },
                text = { Text("وای‌فای مودم چند ثانیه قطع می‌شود و همهٔ دستگاه‌ها (این گوشی هم) باید دوباره وصل شوند.") },
                confirmButton = {
                    TextButton(onClick = { ask = false; st.saveWifi(ssid, pass, mode, hidden, max) }) { Text("ذخیره کن") }
                },
                dismissButton = { TextButton(onClick = { ask = false }) { Text("نه") } },
            )
        }
    }
}

// ---------------- devices ----------------

@Composable
private fun DevicesScreen(st: AppState) {
    LaunchedEffect(Unit) { st.loadDevices() }
    val list = st.devices
    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Title("دستگاه‌های وصل", list?.let { "${it.size} دستگاه" }) }
            IconButton(onClick = { st.loadDevices() }) { Icon(Icons.Filled.Refresh, null, tint = K.text) }
        }
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Card { Text("هیچ دستگاهی وصل نیست.", color = K.dim) }
            else -> list.forEach { d ->
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(K.line),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.PhoneAndroid, null, tint = K.blue) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(d.name.ifEmpty { "دستگاه بی‌نام" }, color = K.text, fontWeight = FontWeight.Bold)
                            Ltr {
                                Text("${d.ip}   ${d.mac.uppercase()}", color = K.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- sms ----------------

@Composable
private fun SmsScreen(st: AppState) {
    LaunchedEffect(Unit) { st.loadSms() }
    var writing by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    var del by remember { mutableStateOf<Sms?>(null) }
    val list = st.sms
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                    Box(Modifier.weight(1f)) { Title("پیامک‌ها", list?.let { "${it.size} پیام" }) }
                    IconButton(onClick = { st.loadSms() }) { Icon(Icons.Filled.Refresh, null, tint = K.text) }
                }
            }
            if (list == null) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else if (list.isEmpty()) item { Card { Text("پیامکی نیست.", color = K.dim) } }
            else items(list, key = { it.id }) { m ->
                val expanded = open == m.id
                Card(Modifier.clickable { open = if (expanded) null else m.id; st.readSms(m) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (m.unread) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(K.good))
                                Spacer(Modifier.width(8.dp))
                            }
                            Ltr { Text(m.number, color = K.text, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.weight(1f))
                            if (m.sent) Text("فرستاده  ", color = K.blue, fontSize = 12.sp)
                            Ltr { Text(m.date, color = K.dim, fontSize = 12.sp) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(m.text, color = if (m.unread) K.text else K.dim, maxLines = if (expanded) Int.MAX_VALUE else 2)
                        if (expanded) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { del = m }) {
                                    Icon(Icons.Filled.Delete, null, tint = K.bad)
                                    Spacer(Modifier.width(4.dp))
                                    Text("پاک کن", color = K.bad)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = { writing = true },
            icon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
            text = { Text("پیامک تازه") },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        )
    }
    if (writing) {
        var number by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { writing = false },
            title = { Text("پیامک تازه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        number, { v -> number = v.filter { it.isDigit() || it == '+' } }, label = { Text("شماره") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    OutlinedTextField(text, { text = it }, label = { Text("متن") }, modifier = Modifier.heightIn(min = 100.dp))
                    Text("${text.length} حرف", color = K.dim, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { writing = false; st.sendSms(number, text) },
                    enabled = number.length >= 3 && text.isNotBlank(),
                ) { Text("بفرست") }
            },
            dismissButton = { TextButton(onClick = { writing = false }) { Text("لغو") } },
        )
    }
    del?.let { m ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("پیامک پاک شود؟") },
            text = { Text(m.text, maxLines = 3) },
            confirmButton = { TextButton(onClick = { del = null; st.deleteSms(m) }) { Text("پاک کن", color = K.bad) } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("نه") } },
        )
    }
}

// ---------------- more ----------------

@Composable
private fun MoreScreen(st: AppState) {
    val s = st.status
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reboot by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    Page {
        Title("بیشتر")
        Card {
            Column {
                Text("آنتن", color = K.text, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Line("RSRP (قدرت)", s.s("lte_rsrp").ifEmpty { s.s("rsrp") }.let { if (it.isEmpty()) "" else "$it dBm" })
                Line("SINR (کیفیت)", s.s("lte_snr").ifEmpty { s.s("sinr") }.let { if (it.isEmpty()) "" else "$it dB" })
                Line("RSRQ", s.s("lte_rsrq").ifEmpty { s.s("rsrq") })
                Line("RSSI", s.s("rssi"))
                Line("باند", s.s("lte_band"))
                Line("Cell ID", s.s("cell_id"))
                Text(
                    "RSRP بالای ‎-90‎ خوب، ‎-90‎ تا ‎-105‎ متوسط، زیر ‎-105‎ ضعیف. SINR بالای ۱۰ خوب.",
                    color = K.dim, fontSize = 12.sp,
                )
            }
        }
        Card {
            Column {
                Text("مودم", color = K.text, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Line("IP داخلی", s.s("lan_ipaddr"))
                Line("IP اینترنت", s.s("wan_ipaddr"))
                Line("شمارهٔ سیم‌کارت", s.s("msisdn"))
                Line("IMEI", s.s("imei"))
                Line("نسخهٔ نرم‌افزار", s.s("cr_version").ifEmpty { s.s("wa_inner_version") })
                Line("وضعیت سیم‌کارت", s.s("modem_main_state"))
            }
        }
        Button(
            onClick = { reboot = true }, enabled = !st.busy, modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = K.warn, contentColor = K.bg),
        ) {
            Icon(Icons.Filled.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text("ری‌استارت مودم")
        }
        OutlinedButton(
            onClick = { scope.launch { report = "…"; report = st.diagnostics() } },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Icon(Icons.Filled.BugReport, null); Spacer(Modifier.width(8.dp)); Text("گزارش برای عیب‌یابی")
        }
        OutlinedButton(onClick = { st.logout() }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(8.dp)); Text("خروج")
        }
        Text("نسخهٔ ${BuildConfig.VERSION_NAME}", color = K.dim, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    if (reboot) {
        AlertDialog(
            onDismissRequest = { reboot = false },
            icon = { Icon(Icons.Filled.PowerSettingsNew, null) },
            title = { Text("مودم ری‌استارت شود؟") },
            text = { Text("اینترنت و وای‌فای حدود یک دقیقه قطع می‌شود.") },
            confirmButton = { TextButton(onClick = { reboot = false; st.reboot() }) { Text("ری‌استارت") } },
            dismissButton = { TextButton(onClick = { reboot = false }) { Text("نه") } },
        )
    }
    report?.let { r ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text("گزارش عیب‌یابی") },
            text = {
                Column {
                    Text("اگر چیزی درست کار نکرد، این متن را بفرست (رمز داخلش نیست).", color = K.dim, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Ltr {
                        Text(
                            r, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = K.text,
                            modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, r)
                    ctx.startActivity(Intent.createChooser(send, "فرستادن گزارش"))
                }) { Text("فرستادن") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("modem", r))
                    st.message = "کپی شد"
                }) { Text("کپی") }
            },
        )
    }
}
