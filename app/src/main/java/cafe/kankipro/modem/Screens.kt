package cafe.kankipro.modem

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    BackHandler(enabled = st.loggedIn && st.pages.isNotEmpty()) { st.back() }
    BackHandler(enabled = st.loggedIn && st.pages.isEmpty() && st.tab != Tab.HOME) { st.tab = Tab.HOME }
    Scaffold(
        containerColor = K.bg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { if (st.loggedIn && st.pages.isEmpty()) BottomBar(st) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(K.bg)) {
            when {
                !st.loggedIn -> LoginScreen(st)
                st.pages.isNotEmpty() -> PageHost(st, st.pages.last())
                else -> when (st.tab) {
                    Tab.HOME -> HomeScreen(st)
                    Tab.WIFI -> WifiScreen(st)
                    Tab.DEVICES -> DevicesScreen(st)
                    Tab.SMS -> SmsScreen(st)
                    Tab.SETTINGS -> SettingsScreen(st)
                }
            }
            if (st.busy && st.loggedIn) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(enabled = true) {}, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = K.blue)
                }
            }
        }
    }
}

// Same as the modem page: the modem's own inbox count when given, else the general one.
private fun unread(s: JSONObject): Int =
    s.optString("sms_dev_unread_num").toIntOrNull() ?: s.optString("sms_unread_num").toIntOrNull() ?: 0

@Composable
private fun BottomBar(st: AppState) {
    val n = unread(st.status)
    val items = listOf(
        Triple(Tab.HOME, Icons.Filled.Home, "خانه"),
        Triple(Tab.WIFI, Icons.Filled.Wifi, "وای‌فای"),
        Triple(Tab.DEVICES, Icons.Filled.Devices, "دستگاه‌ها"),
        Triple(Tab.SMS, Icons.AutoMirrored.Filled.Message, if (n > 0) "پیامک $n" else "پیامک"),
        Triple(Tab.SETTINGS, Icons.Filled.Tune, "تنظیمات"),
    )
    NavigationBar(containerColor = K.card, tonalElevation = 0.dp) {
        items.forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = st.tab == tab,
                onClick = { st.tab = tab },
                icon = { Icon(icon, null) },
                label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White, selectedTextColor = K.ice,
                    unselectedIconColor = K.dim, unselectedTextColor = K.dim, indicatorColor = K.blue,
                ),
            )
        }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content(); Spacer(Modifier.height(16.dp)) }
}

@Composable
private fun Heading(title: String, sub: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = K.text)
            if (sub != null) Text(sub, fontSize = 13.sp, color = K.dim)
        }
        action?.invoke()
    }
}

// ---------------- login ----------------

@Composable
private fun LoginScreen(st: AppState) {
    var pass by remember { mutableStateOf(st.savedPassword) }
    var remember by remember { mutableStateOf(true) }
    Page {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
            WaveDish(level = 5, color = K.blue, modifier = Modifier.fillMaxSize())
            Box(Modifier.size(78.dp).clip(CircleShape).background(K.blue), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Router, null, tint = Color.White, modifier = Modifier.size(38.dp))
            }
        }
        Text("مودم من", fontSize = 34.sp, fontWeight = FontWeight.Black, color = K.text, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            "گوشی را به وای‌فای مودم وصل کن و رمز صفحهٔ مدیریت مودم را بزن.",
            color = K.dim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Field("آدرس مودم", st.host, { st.host = it.trim() }, ltr = true)
        Field("رمز مدیریت", pass, { pass = it }, password = true)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { remember = !remember }) {
            Checkbox(checked = remember, onCheckedChange = { remember = it }, colors = CheckboxDefaults.colors(checkedColor = K.blue))
            Text("رمز در این گوشی بماند", color = K.text)
        }
        if (st.busy) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = K.blue) }
        else MainButton("ورود", enabled = pass.isNotEmpty(), icon = Icons.Filled.Lock) { st.login(pass, remember) }
    }
}

// ---------------- home ----------------

/** Radio waves rising from the modem; [level] of 5 arcs are lit. */
@Composable
fun WaveDish(level: Int, color: Color, modifier: Modifier = Modifier) {
    val lit by animateFloatAsState(level.toFloat(), tween(600), label = "lit")
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height * 0.5f)
        val step = size.width * 0.07f
        val stroke = size.width * 0.022f
        for (i in 1..5) {
            val r = size.width * 0.12f + i * step
            val on = (lit - (i - 1)).coerceIn(0f, 1f)
            val col = if (on > 0f) color.copy(alpha = 0.35f + 0.65f * on * (1f - (i - 1) * 0.1f)) else K.line
            drawArc(
                color = col, startAngle = 140f, sweepAngle = 80f, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = col, startAngle = -40f, sweepAngle = 80f, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun HomeScreen(st: AppState) {
    val s = st.status
    val ppp = s.s("ppp_status")
    val connected = ppp == "ppp_connected"
    val busy = ppp.contains("connecting")
    val level = s.s("signalbar").toIntOrNull() ?: 0
    val signalColor = when { level <= 1 -> K.bad; level <= 2 -> K.warn; else -> K.good }
    var askOff by remember { mutableStateOf(false) }
    var askOn by remember { mutableStateOf<String?>(null) }
    val roaming = s.s("simcard_roam") == "Internal" || s.s("simcard_roam") == "International"
    Page {
        if (st.offline) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(K.bad.copy(alpha = 0.12f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.WifiOff, null, tint = K.bad)
                Spacer(Modifier.width(10.dp))
                Text("ارتباط با مودم قطع است. به وای‌فای مودم وصلی؟", color = K.text, fontSize = 13.sp)
            }
        }
        SimCard(st, s)
        // The one big moment of the app: signal as waves, the connect button in the middle.
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            WaveDish(level, if (connected) signalColor else K.dim, Modifier.fillMaxSize())
            val ring = if (connected) K.good else if (busy) K.warn else K.line
            Box(
                Modifier.size(104.dp).clip(CircleShape).background(if (connected) K.blue else K.card)
                    .border(BorderStroke(3.dp, ring), CircleShape)
                    .clickable(enabled = !st.busy && !busy) {
                        when {
                            connected -> askOff = true
                            // Same two questions the modem page asks before it connects.
                            roaming -> askOn = "roam"
                            overLimit(s) -> askOn = "limit"
                            else -> st.run("دستور وصل شدن رفت") { connect(true) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PowerSettingsNew, "وصل یا قطع اینترنت", tint = Color.White, modifier = Modifier.size(36.dp))
                    Text(
                        when { connected -> "وصل"; busy -> "صبر کن"; else -> "قطع" },
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                s.s("network_provider_spn").ifEmpty { s.s("network_provider") }.ifEmpty { Codec.ucs2Decode(s.s("spn_name_data")) }.ifEmpty { "اپراتور" },
                fontSize = 26.sp, fontWeight = FontWeight.Black, color = K.text,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(netName(s.s("sub_network_type").ifEmpty { s.s("network_type") }), K.blue)
                if (s.s("simcard_roam") == "Internal" || s.s("simcard_roam") == "International") { Spacer(Modifier.width(6.dp)); Pill("رومینگ", K.warn) }
                Spacer(Modifier.width(6.dp))
                Pill("آنتن $level از ۵", signalColor)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Speed(Icons.Filled.ArrowDownward, "دانلود", s.s("realtime_rx_thrpt"), K.good, Modifier.weight(1f))
            Speed(Icons.Filled.ArrowUpward, "آپلود", s.s("realtime_tx_thrpt"), K.cyan, Modifier.weight(1f))
        }
        val charging = s.s("battery_charging") == "1"
        val bat = s.s("battery_vol_percent").toIntOrNull()
        Group {
            NavRow(if (charging) Icons.Filled.BatteryChargingFull else Icons.Filled.Battery5Bar, if ((bat ?: 100) < 20) K.bad else K.good,
                if (charging) "باتری (در حال شارژ)" else "باتری", bat?.let { "$it٪" } ?: battery(s), onClick = null)
            Divider()
            val users = (s.s("sta_count").toIntOrNull() ?: 0) + (s.s("m_sta_count").toIntOrNull() ?: 0)
            NavRow(Icons.Filled.Devices, K.violet, "دستگاه‌های وصل", "$users") { st.tab = Tab.DEVICES }
            Divider()
            NavRow(Icons.Filled.MarkEmailUnread, K.pink, "پیامک تازه", "${unread(s)}") { st.tab = Tab.SMS }
        }
        if (s.s("fota_new_version_state").let { it == "has_critical" || it == "has_optional" || it == "already_has_pkg" }) {
            Group { NavRow(Icons.Filled.SystemUpdate, K.good, "نسخهٔ تازهٔ نرم‌افزار مودم آمده", "ببین") { st.open("update") } }
        }
        LimitBar(st, s)
        Group(title = "مصرف") {
            InfoRow("این اتصال", Codec.bytes(sum(s.s("realtime_rx_bytes"), s.s("realtime_tx_bytes"))))
            Divider()
            InfoRow("مدت این اتصال", Codec.duration(s.s("realtime_time")))
            Divider()
            InfoRow("این ماه", Codec.bytes(sum(s.s("monthly_rx_bytes"), s.s("monthly_tx_bytes"))))
            Divider()
            NavRow(Icons.Filled.DataUsage, K.warn, "سقف مصرف ماهانه") { st.open("limit") }
        }
    }
    askOn?.let { why ->
        Ask(
            if (why == "roam") "در رومینگ وصل شود؟" else "سقف ماهانه تمام شده؛ وصل شود؟",
            if (why == "roam") "سیم‌کارت بیرون از شبکهٔ اپراتور خودش است و اینترنت می‌تواند خیلی گران باشد." else "مصرف این ماه از سقفی که گذاشته‌ای گذشته است.",
            "وصل شو", danger = true,
            onYes = { st.run("دستور وصل شدن رفت") { connect(true) } }, onNo = { askOn = null },
        )
    }
    if (askOff) Ask("اینترنت قطع شود؟", "همهٔ دستگاه‌های وصل به مودم بی‌اینترنت می‌شوند.", "قطع کن", danger = true,
        onYes = { st.run("اینترنت مودم قطع شد") { connect(false) } }, onNo = { askOff = false })
}

/** SIM needs a PIN / PUK, or is missing: the same checks as the modem's "sim_abnormal" page. */
@Composable
private fun SimCard(st: AppState, s: JSONObject) {
    val state = s.s("modem_main_state")
    if (state.isEmpty() || state == "modem_init_complete") return
    var code by remember { mutableStateOf("") }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val digits = { v: String, n: Int -> v.filter { it.isDigit() }.take(n) }
    Group(title = "سیم‌کارت") {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state) {
                "modem_waitpin" -> {
                    Text("سیم‌کارت PIN می‌خواهد. ${s.s("pinnumber")} فرصت باقی است.", color = K.warn, lineHeight = 22.sp)
                    Field("PIN", code, { code = digits(it, 8) }, password = true, number = true)
                    MainButton("باز کن", enabled = code.length in 4..8, icon = Icons.Filled.Lock) { st.run("سیم‌کارت باز شد", "PIN اشتباه است") { enterPin(code) }; code = "" }
                }
                "modem_waitpuk" -> {
                    Text("سیم‌کارت قفل شده و کد PUK (روی کارت سیم‌کارت) می‌خواهد. ${s.s("puknumber")} فرصت باقی است.", color = K.bad, lineHeight = 22.sp)
                    Field("PUK", code, { code = digits(it, 8) }, password = true, number = true)
                    Field("PIN تازه", pin1, { pin1 = digits(it, 8) }, password = true, number = true)
                    Field("تکرار PIN تازه", pin2, { pin2 = digits(it, 8) }, password = true, number = true, error = pin2.isNotEmpty() && pin2 != pin1)
                    MainButton("باز کن", enabled = code.length == 8 && pin1.length in 4..8 && pin1 == pin2, icon = Icons.Filled.Lock) {
                        st.run("سیم‌کارت باز شد", "PUK اشتباه است") { enterPuk(code, pin1) }; code = ""
                    }
                }
                "modem_sim_undetected", "modem_undetected" -> Text("سیم‌کارت پیدا نشد. مودم را خاموش کن و سیم‌کارت را درست بگذار.", color = K.bad, lineHeight = 22.sp)
                "modem_sim_destroy", "modem_destroy" -> Text("سیم‌کارت خراب یا برای همیشه قفل است (PUK تمام شد). باید از اپراتور سیم‌کارت تازه بگیری.", color = K.bad, lineHeight = 22.sp)
                "modem_imsi_waitnck" -> Text("سیم‌کارت با این مودم قفل شبکه دارد.", color = K.bad)
                else -> Text("سیم‌کارت در حال آماده شدن است…", color = K.dim)
            }
        }
    }
}

/** Monthly limit, when the owner set one (statusBar / main page of the modem do the same). */
@Composable
private fun LimitBar(st: AppState, s: JSONObject) {
    if (s.s("data_volume_limit_switch") != "1") return
    val byData = s.s("data_volume_limit_unit") != "time"
    val (used, total) = if (byData) {
        val p = s.s("data_volume_limit_size").split("_")
        val mb = (p.getOrNull(0)?.toDoubleOrNull() ?: 0.0) * (p.getOrNull(1)?.toDoubleOrNull() ?: 1.0)
        ((s.s("monthly_rx_bytes").toDoubleOrNull() ?: 0.0) + (s.s("monthly_tx_bytes").toDoubleOrNull() ?: 0.0)) to mb * 1048576
    } else (s.s("monthly_time").toDoubleOrNull() ?: 0.0) to (s.s("data_volume_limit_size").toDoubleOrNull() ?: 0.0) * 3600
    if (total <= 0) return
    val f = (used / total).toFloat().coerceIn(0f, 1f)
    val alert = (s.s("data_volume_alert_percent").toFloatOrNull() ?: 100f) / 100f
    val color = when { used >= total -> K.bad; f >= alert -> K.warn; else -> K.good }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(K.card).border(BorderStroke(1.dp, K.line), RoundedCornerShape(22.dp))
            .clickable { st.open("limit") }.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("سقف ماهانه", color = K.dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                if (byData) "${Codec.iso(Codec.bytes(used.toString()))} از ${Codec.iso(Codec.bytes(total.toString()))}"
                else "${Codec.duration(used.toLong().toString())} از ${Codec.duration(total.toLong().toString())}",
                color = K.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(K.line)) {
            Box(Modifier.fillMaxWidth(f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        }
        if (f >= alert) Text(if (used >= total) "سقف ماهانه تمام شد." else "به ${(alert * 100).toInt()}٪ سقف رسیدی.", color = color, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** True when this month's use is past the cap (data or time). */
private fun overLimit(s: JSONObject): Boolean {
    if (s.s("data_volume_limit_switch") != "1") return false
    return if (s.s("data_volume_limit_unit") != "time") {
        val p = s.s("data_volume_limit_size").split("_")
        val total = (p.getOrNull(0)?.toDoubleOrNull() ?: 0.0) * (p.getOrNull(1)?.toDoubleOrNull() ?: 1.0) * 1048576
        total > 0 && (s.s("monthly_rx_bytes").toDoubleOrNull() ?: 0.0) + (s.s("monthly_tx_bytes").toDoubleOrNull() ?: 0.0) >= total
    } else {
        val total = (s.s("data_volume_limit_size").toDoubleOrNull() ?: 0.0) * 3600
        total > 0 && (s.s("monthly_time").toDoubleOrNull() ?: 0.0) >= total
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Speed(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, raw: String, color: Color, modifier: Modifier) {
    val txt = Codec.speed(raw)
    val num = txt.substringBefore(' ')
    val unit = txt.substringAfter(' ', "")
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(K.card).border(BorderStroke(1.dp, K.line), RoundedCornerShape(22.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = K.dim, fontSize = 13.sp)
        }
        Ltr {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(num, color = K.text, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(4.dp))
                Text(unit, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

private fun sum(a: String, b: String): String? {
    val x = a.toDoubleOrNull() ?: return null
    val y = b.toDoubleOrNull() ?: return null
    return (x + y).toString()
}

private fun battery(s: JSONObject): String =
    s.optString("battery_pers").toIntOrNull()?.let { "${it * 25}٪" } ?: "—"

fun netName(t: String): String = when (t.uppercase()) {
    "LTE", "LTE_A", "LTE-A", "LTE+", "4G", "4G+", "E-UTRAN" -> "4G $t".trim()
    "", "NO_SERVICE", "NO SERVICE" -> "بدون شبکه"
    "LIMITED_SERVICE", "LIMITED SERVICE" -> "شبکهٔ محدود"
    "SEARCHING" -> "جستجوی شبکه"
    "WCDMA", "HSPA", "HSDPA", "HSUPA", "HSPA+", "DC-HSPA+", "UMTS" -> "3G $t"
    "GSM", "GPRS", "EDGE" -> "2G $t"
    "NR", "5G", "ENDC" -> "5G"
    else -> t
}

// ---------------- Wi-Fi ----------------

@Composable
private fun WifiScreen(st: AppState) {
    var ver by remember { mutableIntStateOf(0) }
    Page {
        Heading("وای‌فای", "اسم، رمز و هر چیزی که به وای‌فای مودم مربوط است")
        Load(st, ver, { wifi() }) { w, reload ->
            WifiMain(st, w) { ver++; reload() }
        }
        Group(title = "بیشتر") {
            NavRow(Icons.Filled.Groups, K.violet, "شبکهٔ مهمان") { st.open("guest") }
            Divider()
            NavRow(Icons.Filled.Speed, K.cyan, "پیشرفته (فرکانس، کانال، کشور، سرعت)") { st.open("wifiadv") }
            Divider()
            NavRow(Icons.Filled.Bedtime, K.violet, "خواب و برد وای‌فای") { st.open("sleep") }
            Divider()
            NavRow(Icons.Filled.FilterAlt, K.warn, "فیلتر دستگاه‌ها (MAC)") { st.open("macfilter") }
            Divider()
            NavRow(Icons.Filled.TouchApp, K.good, "اتصال با دکمه (WPS)") { st.open("wps") }
        }
    }
}

@Composable
private fun WifiMain(st: AppState, w: JSONObject, reload: () -> Unit) {
    val on = w.s("wifi_cur_state") != "0"
    var askOff by remember { mutableStateOf(false) }
    var ssid by remember(w) { mutableStateOf(w.s("SSID1")) }
    var pass by remember(w) { mutableStateOf(w.s("password")) }
    var mode by remember(w) { mutableStateOf(w.s("AuthMode").ifEmpty { "WPA2PSK" }) }
    var hidden by remember(w) { mutableStateOf(w.s("HideSSID") == "1") }
    val maxAll = w.s("MAX_Station_num").toIntOrNull()?.coerceIn(1, 32) ?: 32
    var max by remember(w) { mutableStateOf(w.s("MAX_Access_num").ifEmpty { "10" }) }
    var ask by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(false) }
    if (qr) WifiQrDialog(w.s("SSID1"), w.s("password"), w.s("AuthMode"), w.s("HideSSID") == "1") { qr = false }
    val modes = listOf(
        "OPEN" to "بدون رمز (همه وصل می‌شوند)",
        "WPA2PSK" to "WPA2 (پیشنهادی)",
        "WPAPSKWPA2PSK" to "WPA / WPA2",
    ).let { if (it.none { m -> m.first == mode }) it + (mode to mode) else it }
    val ssidOk = ssid.toByteArray().size in 1..32
    val passOk = mode == "OPEN" || pass.length in 8..63
    Group {
        SwitchRow("وای‌فای روشن", if (on) "دستگاه‌ها می‌توانند وصل شوند" else "خاموش است", on, icon = Icons.Filled.Wifi, tint = K.blue) {
            if (w.s("WscModeOption") == "1") st.message = "WPS روشن است؛ صبر کن تمام شود"
            else if (!it) askOff = true else st.run("وای‌فای روشن شد", then = reload) { setWifiOn(true, w.s("m_ssid_enable")) }
        }
    }
    if (on) {
        Field("اسم وای‌فای", ssid, { ssid = it }, error = !ssidOk)
        Choice("نوع امنیت", modes, mode) { mode = it }
        if (mode != "OPEN") Field("رمز وای‌فای", pass, { pass = it }, password = true, ltr = true, error = !passOk, hint = "۸ تا ۶۳ حرف")
        Choice("حداکثر دستگاه هم‌زمان", (1..maxAll).map { "$it" to "$it" }, max) { max = it }
        Group { SwitchRow("مخفی کردن اسم", "در فهرست وای‌فای گوشی‌ها دیده نمی‌شود", hidden) { hidden = it } }
        val wpsOn = w.s("WscModeOption") == "1"
        if (wpsOn) Text("WPS الان روشن است؛ مودم تا تمام شدنش تنظیم وای‌فای را عوض نمی‌کند.", color = K.warn, fontSize = 13.sp)
        MainButton("ذخیره", enabled = ssidOk && passOk && !wpsOn) { ask = true }
        GhostButton("کد QR وای‌فای", Icons.Filled.QrCode2) { qr = true }
    }
    if (ask) Ask("ذخیره شود؟", "وای‌فای چند ثانیه قطع می‌شود و همهٔ دستگاه‌ها، این گوشی هم، باید دوباره وصل شوند.", "ذخیره کن",
        onYes = { st.run("ذخیره شد. اگر رمز را عوض کردی، با رمز تازه دوباره وصل شو", then = reload) { saveWifi(ssid, pass, mode, hidden, max, w) } },
        onNo = { ask = false })
    if (askOff) Ask("وای‌فای خاموش شود؟", "همهٔ دستگاه‌ها قطع می‌شوند و این برنامه هم دیگر به مودم نمی‌رسد تا وای‌فای را با دکمهٔ خود مودم یا کابل روشن کنی.", "خاموش کن", danger = true,
        onYes = { st.run("وای‌فای خاموش شد", then = reload) { setWifiOn(false, w.s("m_ssid_enable")) } }, onNo = { askOff = false })
}

/** The standard Wi-Fi QR text that phone cameras understand. */
fun wifiQrText(ssid: String, pass: String, auth: String, hidden: Boolean): String {
    fun esc(v: String) = v.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
    val t = if (auth == "OPEN") "nopass" else "WPA"
    return "WIFI:T:$t;S:${esc(ssid)};" + (if (t != "nopass") "P:${esc(pass)};" else "") + (if (hidden) "H:true;" else "") + ";"
}

@Composable
private fun WifiQrDialog(ssid: String, pass: String, auth: String, hidden: Boolean, onClose: () -> Unit) {
    val text = wifiQrText(ssid, pass, auth, hidden)
    val matrix = remember(text) {
        runCatching {
            com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8", com.google.zxing.EncodeHintType.MARGIN to 1))
        }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("وصل شدن با دوربین") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (matrix != null) Canvas(Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
                    val cell = size.width / matrix.width
                    for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix.get(x, y)) {
                        drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(ssid, color = K.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("با دوربین گوشی دیگر این کد را اسکن کن تا بدون زدن رمز وصل شود.", color = K.dim, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("بستن", color = K.ice) } },
        containerColor = K.raised,
    )
}

// ---------------- devices ----------------

@Composable
private fun DevicesScreen(st: AppState) {
    var ver by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<Device?>(null) }
    var block by remember { mutableStateOf<Device?>(null) }
    Page {
        Heading("دستگاه‌ها", "هر چیزی که الان به وای‌فای مودم وصل است") {
            IconButton(onClick = { ver++ }) { Icon(Icons.Filled.Refresh, "تازه کن", tint = K.text) }
        }
        Load(st, ver, { devices() to macFilter() }) { (list, filter), _ ->
            val me = filter.s("client_mac_address")
            if (list.isEmpty()) Empty("هیچ دستگاهی وصل نیست.")
            else Group(title = "${list.size} دستگاه وصل") {
                list.forEachIndexed { i, d ->
                    if (i > 0) Divider()
                    DeviceRow(d, isMe = d.mac.equals(me, true), onEdit = { edit = d }, onBlock = { block = d })
                }
            }
            val blocked = filter.s("wifi_mac_black_list").split(";").filter { it.isNotBlank() }
            val names = filter.s("wifi_hostname_black_list").split(";")
            if (filter.s("ACL_mode") == "2" && blocked.isNotEmpty()) {
                Group(title = "مسدود شده‌ها", footer = "این دستگاه‌ها نمی‌توانند به وای‌فای وصل شوند.") {
                    blocked.forEachIndexed { i, mac ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Filled.Block, K.bad)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(names.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "دستگاه", color = K.text)
                                Ltr { Text(mac.uppercase(), color = K.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                            }
                            TextButton(onClick = { st.run("آزاد شد", then = { ver++ }) { blockDevice(Device("", "", mac), false) } }) { Text("آزاد کن", color = K.ice) }
                        }
                    }
                }
            }
        }
    }
    edit?.let { d ->
        var name by remember(d) { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text("اسم دستگاه") },
            text = { Field("اسم", name, { name = it.take(32) }, error = name.isBlank() || Regex("[*\$\\[&:,;<>'\"\\\\`\\]￥]").containsMatchIn(name)) },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !Regex("[*\$\\[&:,;<>'\"\\\\`\\]￥]").containsMatchIn(name),
                    onClick = { edit = null; st.run("اسم عوض شد", then = { ver++ }) { renameDevice(d.mac, name.trim()) } },
                ) { Text("ذخیره", color = K.ice) }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("لغو", color = K.dim) } },
            containerColor = K.raised,
        )
    }
    block?.let { d ->
        Ask("«${d.name.ifEmpty { d.mac }}» مسدود شود؟", "این دستگاه از وای‌فای بیرون می‌رود و دیگر نمی‌تواند وصل شود تا آزادش کنی.", "مسدود کن", danger = true,
            onYes = { st.run("مسدود شد", then = { ver++ }) { blockDevice(d, true) } }, onNo = { block = null })
    }
}

@Composable
private fun DeviceRow(d: Device, isMe: Boolean, onEdit: () -> Unit, onBlock: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(Icons.Filled.PhoneAndroid, if (isMe) K.good else K.violet)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.name.ifEmpty { "دستگاه بی‌نام" }, color = K.text, fontWeight = FontWeight.Bold, maxLines = 1)
                if (isMe) { Spacer(Modifier.width(6.dp)); Pill("همین گوشی", K.good) }
            }
            Ltr { Text("${d.ip}   ${d.mac.uppercase()}", color = K.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            if (d.duration.isNotEmpty() || d.rx.isNotEmpty()) {
                Text(
                    listOfNotNull(
                        d.duration.takeIf { it.isNotEmpty() }?.let { "وصل از ${Codec.duration(it)}" },
                        if (d.rx.isNotEmpty()) "دانلود ${Codec.iso(Codec.bytes(d.rx))}" else null,
                    ).joinToString("، "),
                    color = K.dim, fontSize = 12.sp,
                )
            }
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "تغییر اسم", tint = K.ice) }
        if (!isMe) IconButton(onClick = onBlock) { Icon(Icons.Filled.Block, "مسدود کردن", tint = K.bad) }
    }
}

// ---------------- SMS ----------------

/** What the compose window starts with (reply, forward, a draft, or a contact's number). */
data class Compose(val to: String = "", val text: String = "", val draftId: String? = null)

@Composable
private fun SmsScreen(st: AppState) {
    var ver by remember { mutableIntStateOf(0) }
    var box by remember { mutableIntStateOf(0) } // 0 inbox, 1 sent, 2 drafts, 3 SIM
    var open by remember { mutableStateOf<String?>(null) }
    var del by remember { mutableStateOf<Sms?>(null) }
    var delAll by remember { mutableStateOf<List<String>?>(null) }
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Page {
            Heading("پیامک", null) {
                IconButton(onClick = { ver++ }) { Icon(Icons.Filled.Refresh, "تازه کن", tint = K.text) }
            }
            Segments(listOf("دریافتی", "ارسالی", "پیش‌نویس", "سیم‌کارت"), box) { box = it; open = null }
            Load(st, ver to box, { (if (box == 3) sms(0) else sms(1)) to smsCapacity() }) { (all, cap), _ ->
                val list = when (box) {
                    0 -> all.filter { !it.sent && !it.draft }
                    1 -> all.filter { it.sent }
                    2 -> all.filter { it.draft }
                    else -> all.filter { !it.draft }
                }
                val total = cap.optString(if (box == 3) "sms_sim_total" else "sms_nv_total").toIntOrNull() ?: 0
                val used = if (box == 3) cap.optString("sms_sim_rev_total").toIntOrNull()?.plus(cap.optString("sms_sim_send_total").toIntOrNull() ?: 0) ?: 0
                else listOf("sms_nv_rev_total", "sms_nv_send_total", "sms_nv_draftbox_total").sumOf { cap.optString(it).toIntOrNull() ?: 0 }
                if (total > 0) Room(used, total, if (box == 3) "حافظهٔ سیم‌کارت" else "حافظهٔ پیامک مودم")
                if (list.isEmpty()) Empty(when (box) { 1 -> "هنوز پیامکی نفرستاده‌ای."; 2 -> "پیش‌نویسی نیست."; else -> "پیامکی نیست." })
                else {
                    Group {
                        list.forEachIndexed { i, m ->
                            if (i > 0) Divider()
                            SmsRow(
                                m, open == m.id,
                                onClick = {
                                    if (m.draft) st.compose = Compose(m.number, m.text, m.id)
                                    else {
                                        open = if (open == m.id) null else m.id
                                        if (m.unread) st.quiet { markRead(m.id) }
                                    }
                                },
                                onReply = { st.compose = Compose(m.number) },
                                onForward = { st.compose = Compose(text = m.text) },
                                onCopy = {
                                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("sms", m.text))
                                    st.message = "متن کپی شد"
                                },
                                onDelete = { del = m },
                            )
                        }
                    }
                    GhostButton("پاک کردن همهٔ این بخش (${list.size})", Icons.Filled.Delete, K.bad) { delAll = list.map { it.id } }
                }
            }
            Spacer(Modifier.height(70.dp))
        }
        androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = { st.compose = Compose() },
            icon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
            text = { Text("پیامک تازه", fontWeight = FontWeight.Bold) },
            containerColor = K.blue, contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        )
    }
    st.compose?.let { c -> ComposeDialog(st, c) { sentOrSaved -> st.compose = null; if (sentOrSaved != null) { box = sentOrSaved; ver++ } } }
    del?.let { m ->
        Ask("پیامک پاک شود؟", m.text.take(120), "پاک کن", danger = true,
            onYes = { st.run("پاک شد", "پاک نشد", then = { ver++ }) { deleteSms(m.id) } }, onNo = { del = null })
    }
    delAll?.let { ids ->
        Ask("همه پاک شوند؟", "${ids.size} پیامک این بخش برای همیشه پاک می‌شود.", "همه را پاک کن", danger = true,
            onYes = { st.run("پاک شد", "پاک نشد", then = { ver++ }) { deleteSmsMany(ids) } }, onNo = { delAll = null })
    }
}

/** A thin bar: how full a memory is. */
@Composable
fun Room(used: Int, total: Int, label: String) {
    val f = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    val color = when { f >= 0.95f -> K.bad; f >= 0.8f -> K.warn; else -> K.blue }
    Column {
        Row {
            Text(label, color = K.dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("$used از $total", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(K.line)) {
            Box(Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
        if (f >= 0.95f) Text("حافظه پر است؛ پیامک تازه نمی‌رسد تا چند تا را پاک کنی.", color = K.bad, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** done(box) = which tab to show after (1 sent, 2 drafts), or null when closed. */
@Composable
private fun ComposeDialog(st: AppState, c: Compose, done: (Int?) -> Unit) {
    var to by remember(c) { mutableStateOf(c.to) }
    var text by remember(c) { mutableStateOf(c.text) }
    val numbers = splitNumbers(to)
    AlertDialog(
        onDismissRequest = { done(null) },
        title = { Text(if (c.draftId != null) "پیش‌نویس" else "پیامک تازه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("شماره", to, { v -> to = v.filter { it.isDigit() || it in "+*#;، ," } }, ltr = true, number = true,
                    hint = "برای چند نفر، شماره‌ها را با ; جدا کن")
                Field("متن", text, { text = it }, singleLine = false, modifier = Modifier.heightIn(min = 110.dp))
                // UCS-2: 70 letters in one SMS, 67 per part when it is longer.
                val parts = if (text.length <= 70) 1 else (text.length + 66) / 67
                Text("${text.length} حرف، $parts پیامک" + if (numbers.size > 1) "، برای ${numbers.size} نفر" else "", color = K.dim, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = numbers.isNotEmpty() && text.isNotBlank(),
                onClick = {
                    done(null)
                    st.run(if (numbers.size > 1) "به ${numbers.size} نفر فرستاده شد" else "پیامک فرستاده شد", "پیامک فرستاده نشد", then = { done(1) }) {
                        val sent = sendSmsMany(numbers, text)
                        if (sent == numbers.size && c.draftId != null) deleteSms(c.draftId)
                        sent == numbers.size
                    }
                },
            ) { Text("بفرست", color = K.ice, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (c.draftId == null) TextButton(enabled = text.isNotBlank(), onClick = {
                    done(null)
                    st.run("در پیش‌نویس ذخیره شد", "ذخیره نشد", then = { done(2) }) { saveDraft(numbers, text) }
                }) { Text("پیش‌نویس", color = K.ice) }
                TextButton(onClick = { done(null) }) { Text("بستن", color = K.dim) }
            }
        },
        containerColor = K.raised,
    )
}

@Composable
fun Segments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(K.card).border(BorderStroke(1.dp, K.line), RoundedCornerShape(16.dp)).padding(4.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) K.blue else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(l, color = if (on) Color.White else K.dim, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun SmsRow(m: Sms, expanded: Boolean, onClick: () -> Unit, onReply: () -> Unit, onForward: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                when { m.draft -> Icons.Filled.Edit; m.sent -> Icons.AutoMirrored.Filled.Send; else -> Icons.Filled.Sms },
                when { m.failed -> K.bad; m.draft -> K.warn; m.sent -> K.cyan; m.unread -> K.blue; else -> K.dim }, 32,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ltr { Text(m.number.ifEmpty { "بی‌شماره" }, color = K.text, fontWeight = if (m.unread) FontWeight.Black else FontWeight.Bold) }
                    if (m.failed) { Spacer(Modifier.width(6.dp)); Pill("فرستاده نشد", K.bad) }
                    if (m.unread) { Spacer(Modifier.width(6.dp)); Pill("تازه", K.blue) }
                }
                Ltr { Text(m.date, color = K.dim, fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(m.text, color = if (m.unread || expanded) K.text else K.dim, lineHeight = 22.sp, maxLines = if (expanded) Int.MAX_VALUE else 2)
        if (expanded) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!m.sent && m.number.any { it.isDigit() }) SmallAction(Icons.AutoMirrored.Filled.Reply, "پاسخ", K.ice, onReply)
            if (m.failed) SmallAction(Icons.Filled.Refresh, "دوباره", K.ice, onReply)
            SmallAction(Icons.AutoMirrored.Filled.Forward, "بفرست برای…", K.ice, onForward)
            SmallAction(Icons.Filled.ContentCopy, "کپی", K.ice, onCopy)
            Spacer(Modifier.weight(1f))
            SmallAction(Icons.Filled.Delete, "پاک", K.bad, onDelete)
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(text, color = color, fontSize = 13.sp)
    }
}

// ---------------- SMS notifications ----------------

@Composable
private fun NotifyGroup(st: AppState) {
    val ctx = LocalContext.current
    // Re-read the two system permissions each time this screen is drawn (they change in system settings).
    var tick by remember { mutableIntStateOf(0) }
    val pm = runCatching { ctx.getSystemService(android.os.PowerManager::class.java) }.getOrNull()
    val battery = remember(tick) { runCatching { pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true }.getOrDefault(false) }
    val allowed = remember(tick) { runCatching { SmsWatch.canNotify(ctx) }.getOrDefault(true) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    Group(
        title = "اعلان پیامک",
        footer = "هر پیامکی که به سیم‌کارت مودم برسد، در اعلان‌های گوشی می‌آید (هر ۱۵ ثانیه چک می‌شود، وقتی گوشی به وای‌فای مودم وصل است).",
    ) {
        SwitchRow("اعلان پیامک‌های تازه", if (st.savedPassword.isEmpty()) "رمز باید در گوشی بماند" else null, st.notifyOn,
            icon = Icons.Filled.MarkEmailUnread, tint = K.pink) { st.setNotify(it) }
        if (st.notifyOn && !allowed) {
            Divider()
            NavRow(Icons.Filled.Info, K.bad, "اجازهٔ اعلان داده نشده", "درست کن") {
                ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
        }
        if (st.notifyOn) {
            Divider()
            NavRow(Icons.Filled.Bolt, if (battery) K.good else K.warn, "کار در پس‌زمینه (باتری)", if (battery) "آزاد است" else "اجازه بده") {
                runCatching {
                    ctx.startActivity(
                        if (battery) Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        else Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${ctx.packageName}")),
                    )
                }
            }
        }
    }
}

// ---------------- settings hub ----------------

@Composable
private fun SettingsScreen(st: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<String?>(null) }
    val x = st.extra
    Page {
        Heading("تنظیمات", "همهٔ بخش‌های صفحهٔ مدیریت مودم")
        NotifyGroup(st)
        Group(title = "اینترنت") {
            NavRow(Icons.Filled.Sync, K.blue, "حالت اتصال و رومینگ") { st.open("conn") }
            Divider()
            NavRow(Icons.Filled.SettingsInputAntenna, K.cyan, "شبکه و نوع آن (4G / 3G)") { st.open("net") }
            Divider()
            NavRow(Icons.Filled.Language, K.violet, "APN") { st.open("apn") }
            Divider()
            NavRow(Icons.Filled.CellTower, K.good, "انتخاب باند") { st.open("bands") }
            Divider()
            NavRow(Icons.Filled.VpnLock, K.blue, "VPN مودم (L2TP / PPTP)") { st.open("vpn") }
            Divider()
            NavRow(Icons.Filled.Public, K.cyan, "DDNS") { st.open("ddns") }
            Divider()
            NavRow(Icons.Filled.DataUsage, K.warn, "سقف مصرف ماهانه") { st.open("limit") }
        }
        Group(title = "سیم‌کارت و پیام") {
            NavRow(Icons.Filled.Pin, K.blue, "PIN سیم‌کارت") { st.open("pin") }
            Divider()
            NavRow(Icons.Filled.Sms, K.pink, "تنظیمات پیامک") { st.open("smsset") }
            if (x.s("ussd_enable") != "0") { Divider(); NavRow(Icons.Filled.Dialpad, K.good, "کد دستوری (USSD)") { st.open("ussd") } }
            Divider()
            NavRow(Icons.Filled.Contacts, K.cyan, "دفترچه تلفن") { st.open("contacts") }
        }
        Group(title = "روتر و امنیت") {
            NavRow(Icons.Filled.Lan, K.blue, "شبکهٔ داخلی (LAN و DHCP)") { st.open("lan") }
            Divider()
            NavRow(Icons.Filled.Dns, K.cyan, "DNS") { st.open("dns") }
            Divider()
            NavRow(Icons.Filled.Block, K.bad, "فیلتر سایت") { st.open("urlfilter") }
            Divider()
            NavRow(Icons.Filled.SwapHoriz, K.violet, "فوروارد پورت") { st.open("forward") }
            Divider()
            NavRow(Icons.Filled.Shield, K.warn, "فیلتر پورت و IP") { st.open("filter") }
            Divider()
            NavRow(Icons.Filled.Hub, K.pink, "نگاشت پورت") { st.open("map") }
            Divider()
            NavRow(Icons.Filled.Speed, K.good, "محدود کردن سرعت دستگاه‌ها") { st.open("rate") }
            Divider()
            NavRow(Icons.Filled.DeviceHub, K.blue, "DMZ") { st.open("dmz") }
            Divider()
            NavRow(Icons.Filled.SettingsEthernet, K.cyan, "UPnP") { st.open("upnp") }
            Divider()
            NavRow(Icons.Filled.Security, K.bad, "دسترسی از بیرون و پینگ") { st.open("security") }
        }
        Group(title = "مودم") {
            NavRow(Icons.Filled.Info, K.blue, "اطلاعات آنتن و مودم") { st.open("about") }
            Divider()
            NavRow(Icons.Filled.Password, K.violet, "رمز مدیریت") { st.open("password") }
            Divider()
            NavRow(Icons.Filled.Schedule, K.cyan, "ساعت و تاریخ") { st.open("time") }
            Divider()
            NavRow(Icons.Filled.FlashOn, K.warn, "روشن شدن سریع") { st.open("fastboot") }
            Divider()
            NavRow(Icons.Filled.NetworkCheck, K.good, "نگه داشتن اتصال (پینگ)") { st.open("keepalive") }
            Divider()
            NavRow(Icons.Filled.Timer, K.pink, "TTL") { st.open("ttl") }
            if (x.s("cstm_webui_simswitch") == "1" && (x.s("sim_switch_number").toIntOrNull() ?: 0) > 1) {
                Divider(); NavRow(Icons.Filled.SimCard, K.blue, "انتخاب سیم‌کارت") { st.open("simswitch") }
            }
            Divider()
            NavRow(Icons.Filled.Lock, K.bad, "باز کردن قفل سیم‌کارت") { st.open("simlock") }
            Divider()
            NavRow(Icons.Filled.Language, K.violet, "زبان صفحهٔ مودم") { st.open("lang") }
            Divider()
            NavRow(Icons.Filled.SystemUpdate, K.good, "به‌روزرسانی نرم‌افزار مودم") { st.open("update") }
        }
        Group(title = "حرفه‌ای", footer = "این‌ها را فقط اگر می‌دانی چه می‌کنند عوض کن.") {
            NavRow(Icons.Filled.Terminal, K.warn, "دستور AT") { st.open("at") }
            Divider()
            NavRow(Icons.Filled.SettingsRemote, K.violet, "TR-069 (مدیریت از راه دور اپراتور)") { st.open("tr069") }
            Divider()
            NavRow(Icons.Filled.Usb, K.cyan, "MAC کارت USB") { st.open("usbmac") }
        }
        Group {
            NavRow(Icons.Filled.RestartAlt, K.warn, "ری‌استارت مودم") { ask = "reboot" }
            Divider()
            NavRow(Icons.Filled.PowerSettingsNew, K.bad, "خاموش کردن مودم") { ask = "off" }
            Divider()
            NavRow(Icons.Filled.SettingsBackupRestore, K.bad, "بازگشت به تنظیمات کارخانه") { ask = "reset" }
        }
        Group {
            NavRow(Icons.Filled.BugReport, K.dim, "گزارش برای عیب‌یابی") { scope.launch { report = "…"; report = st.io { diagnostics() }.getOrElse { "error: ${it.message}" } } }
            Divider()
            NavRow(Icons.AutoMirrored.Filled.Logout, K.dim, "خروج از حساب مودم") { st.logout() }
        }
        Text("نسخهٔ ${BuildConfig.VERSION_NAME}", color = K.dim, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    when (ask) {
        "reboot" -> Ask("مودم ری‌استارت شود؟", "اینترنت و وای‌فای حدود یک دقیقه قطع می‌شود.", "ری‌استارت",
            onYes = { st.run("مودم دارد دوباره روشن می‌شود (حدود یک دقیقه)") { reboot() } }, onNo = { ask = null })
        "off" -> Ask("مودم خاموش شود؟", "برای روشن کردن دوباره باید دکمهٔ روشن خود مودم را بزنی.", "خاموش کن", danger = true,
            onYes = { st.run("مودم خاموش می‌شود") { turnOff() } }, onNo = { ask = null })
        "reset" -> Ask("همه‌چیز به حالت کارخانه برگردد؟", "اسم و رمز وای‌فای، رمز مدیریت، APN و همهٔ تنظیم‌ها پاک و مثل روز اول می‌شوند و مودم ری‌استارت می‌شود. رمزهای کارخانه روی برچسب مودم است.", "بله، برگردان", danger = true,
            onYes = { st.run("مودم به تنظیمات کارخانه برمی‌گردد") { factoryReset() } }, onNo = { ask = null })
    }
    report?.let { r ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text("گزارش عیب‌یابی") },
            text = {
                Column {
                    Text("اگر چیزی درست کار نکرد، این متن را بفرست. رمز وای‌فای داخلش نیست.", color = K.dim, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Ltr {
                        Text(r, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = K.text,
                            modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, r)
                    ctx.startActivity(Intent.createChooser(send, "فرستادن گزارش"))
                }) { Text("فرستادن", color = K.ice) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("modem", r))
                    st.message = "کپی شد"
                }) { Text("کپی", color = K.dim) }
            },
            containerColor = K.raised,
        )
    }
}
