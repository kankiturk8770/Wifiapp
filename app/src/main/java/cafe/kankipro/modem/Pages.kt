package cafe.kankipro.modem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.Sms
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun JSONObject.s(k: String): String = optString(k)

private val IP = Regex("""^(25[0-5]|2[0-4]\d|1?\d?\d)(\.(25[0-5]|2[0-4]\d|1?\d?\d)){3}$""")
private val MAC = Regex("""^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$""")
private fun port(v: String) = v.toIntOrNull()?.let { it in 1..65535 } == true

private val PROTO = listOf("TCP&UDP" to "TCP + UDP", "TCP" to "TCP", "UDP" to "UDP")

/** Every sub-page by name (AppState.open). */
@Composable
fun PageHost(st: AppState, name: String) {
    val back = { st.back() }
    when (name) {
        "conn" -> ConnectionPage(st, back)
        "net" -> NetworkPage(st, back)
        "apn" -> ApnPage(st, back)
        "limit" -> DataLimitPage(st, back)
        "wifiadv" -> WifiAdvancedPage(st, back)
        "sleep" -> SleepPage(st, back)
        "macfilter" -> MacFilterPage(st, back)
        "wps" -> WpsPage(st, back)
        "lan" -> LanPage(st, back)
        "dns" -> DnsPage(st, back)
        "forward" -> ForwardPage(st, back)
        "filter" -> FilterPage(st, back)
        "map" -> MapPage(st, back)
        "rate" -> RatePage(st, back)
        "dmz" -> DmzPage(st, back)
        "upnp" -> UpnpPage(st, back)
        "security" -> SecurityPage(st, back)
        "pin" -> PinPage(st, back)
        "smsset" -> SmsSettingsPage(st, back)
        "ussd" -> UssdPage(st, back)
        "contacts" -> ContactsPage(st, back)
        "about" -> AboutPage(st, back)
        "password" -> PasswordPage(st, back)
        "time" -> TimePage(st, back)
        "fastboot" -> FastBootPage(st, back)
        "keepalive" -> KeepAlivePage(st, back)
        "ttl" -> TtlPage(st, back)
        "simswitch" -> SimSwitchPage(st, back)
        "update" -> UpdatePage(st, back)
        "lang" -> LanguagePage(st, back)
        "urlfilter" -> UrlFilterPage(st, back)
        "guest" -> GuestPage(st, back)
        "bands" -> BandPage(st, back)
        "vpn" -> VpnPage(st, back)
        "ddns" -> DdnsPage(st, back)
        "tr069" -> Tr069Page(st, back)
        "at" -> AtPage(st, back)
        "usbmac" -> UsbMacPage(st, back)
        "apstation" -> ApStationPage(st, back)
        "parental" -> ParentalPage(st, back)
        "sd" -> SdPage(st, back)
        "quick" -> QuickSetupPage(st, back)
        "simlock" -> SimLockPage(st, back)
        else -> SubPage("—", back) { Empty("این صفحه پیدا نشد.") }
    }
}

// ---------------- internet ----------------

@Composable
private fun ConnectionPage(st: AppState, back: () -> Unit) = SubPage(
    "حالت اتصال", back, "خودکار یعنی مودم بعد از روشن شدن خودش به اینترنت وصل می‌شود.",
) {
    NeedsOffline(st)
    Load(st, 0, { connectionMode() }) { d, reload ->
        var mode by remember(d) { mutableStateOf(d.s("connectionMode").ifEmpty { "auto_dial" }) }
        var roam by remember(d) { mutableStateOf(d.s("autoConnectWhenRoaming").ifEmpty { "off" }) }
        Choice("وصل شدن", listOf("auto_dial" to "خودکار", "manual_dial" to "دستی (با دکمهٔ خانه)"), mode, enabled = !st.connected) { mode = it }
        if (mode == "auto_dial") Group {
            SwitchRow("در رومینگ هم خودکار وصل شود", "بیرون از شبکهٔ اپراتور خودت، هزینه می‌تواند بالا باشد", roam == "on", enabled = !st.connected) { roam = if (it) "on" else "off" }
        }
        MainButton("ذخیره", enabled = !st.connected) { st.run("ذخیره شد", then = reload) { setConnectionMode(mode, roam) } }
    }
}

@Composable
private fun NetworkPage(st: AppState, back: () -> Unit) {
    var scan by remember { mutableStateOf<List<Carrier>?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SubPage("شبکه", back, "نوع شبکه را انتخاب کن، یا اپراتور را دستی پیدا و ثبت کن.") {
        NeedsOffline(st)
        Load(st, 0, { netSelect() }) { d, reload ->
            var pref by remember(d) { mutableStateOf(d.s("net_select").ifEmpty { "NETWORK_auto" }) }
            Choice(
                "نوع شبکه",
                listOf("NETWORK_auto" to "خودکار (بهترین)", "Only_LTE" to "فقط 4G", "Only_WCDMA" to "فقط 3G"),
                pref, enabled = !st.connected,
            ) { pref = it }
            MainButton("ذخیره", enabled = !st.connected) { st.run("ذخیره شد", then = { scan = null; reload() }) { setBearer(pref) } }
            Group(title = "انتخاب دستی اپراتور", footer = if (d.s("net_select_mode") == "manual_select") "الان اپراتور دستی انتخاب شده است. برای برگشت به خودکار، بالا «ذخیره» را بزن." else null) {
                NavRow(Icons.Filled.Search, K.cyan, if (scanning) "در حال جستجو… (تا یک دقیقه)" else "جستجوی اپراتورها", enabled = !st.connected && !scanning) {
                    scanning = true
                    scope.launch {
                        val r = st.io { scanNetworks() }
                        scanning = false
                        r.onSuccess { if (it == null) st.message = "جستجو نشد" else scan = it }.onFailure { st.message = st.netError(it) }
                    }
                }
            }
            scan?.let { list ->
                if (list.isEmpty()) Empty("اپراتوری پیدا نشد.")
                else Group(title = "اپراتورهای پیدا شده") {
                    list.forEachIndexed { i, c ->
                        if (i > 0) Divider()
                        val state = when (c.state) { 2 -> "الان"; 3 -> "ممنوع"; 1 -> "در دسترس"; else -> "" }
                        NavRow(
                            Icons.Filled.SettingsInputAntenna,
                            when (c.state) { 2 -> K.good; 3 -> K.bad; else -> K.blue },
                            "${c.name.ifEmpty { c.numeric }}  ${c.tech}", state, enabled = c.state != 3 && !st.connected,
                        ) { st.run("ثبت شد", "ثبت نشد", then = reload) { register(c) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApnPage(st: AppState, back: () -> Unit) {
    var edit by remember { mutableStateOf<Pair<Int, Apn>?>(null) }
    var del by remember { mutableStateOf<Int?>(null) }
    var ver by remember { mutableIntStateOf(0) }
    SubPage("APN", back, "تنظیم اتصال اپراتور. معمولاً «خودکار» درست است؛ فقط اگر اپراتور APN خاص داده دستی کن.") {
        NeedsOffline(st)
        Load(st, ver, { apns() }) { info, _ ->
            val off = !st.connected
            Segments(listOf("خودکار", "دستی"), if (info.mode == "manual") 1 else 0) { i ->
                val m = if (i == 1) "manual" else "auto"
                if (m != info.mode && off) {
                    val p = info.profiles.getOrNull(info.defaultIndex)
                    st.run("ذخیره شد", then = { ver++ }) { useApn(m, info.defaultIndex, p?.pdp ?: "IP") }
                } else if (!off) st.message = "اول اینترنت را قطع کن"
            }
            if (info.mode == "auto") {
                if (info.auto.isNotEmpty()) Group(title = "تنظیم خودکار اپراتور") {
                    info.auto.forEachIndexed { i, a -> if (i > 0) Divider(); InfoRow(a.name, a.apn) }
                }
            } else {
                Group(title = "پروفایل‌ها") {
                    info.profiles.forEachIndexed { i, a ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(if (i == info.defaultIndex) Icons.Filled.CheckCircle else Icons.Filled.Language, if (i == info.defaultIndex) K.good else K.violet)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, color = K.text, fontWeight = FontWeight.Bold)
                                Ltr { Text("${a.apn}  ${a.pdp}", color = K.dim, fontSize = 12.sp) }
                            }
                            if (i != info.defaultIndex) TextButton(enabled = off, onClick = { st.run("این پروفایل استفاده می‌شود", then = { ver++ }) { useApn("manual", i, a.pdp) } }) { Text("استفاده", color = K.ice) }
                            IconButton(enabled = off, onClick = { edit = i to a }) { Icon(Icons.Filled.Edit, "ویرایش", tint = K.ice) }
                            if (i >= 1 && i != info.defaultIndex) IconButton(enabled = off, onClick = { del = i }) { Icon(Icons.Filled.Delete, "پاک کردن", tint = K.bad) }
                        }
                    }
                }
                if (info.profiles.size < info.max) GhostButton("پروفایل تازه", Icons.Filled.Add, enabled = off) {
                    edit = info.profiles.size to Apn("", "", "none", "", "", "IP")
                }
            }
        }
    }
    edit?.let { (i, a) ->
        var name by remember(i) { mutableStateOf(a.name) }
        var apn by remember(i) { mutableStateOf(a.apn) }
        var pdp by remember(i) { mutableStateOf(a.pdp.ifEmpty { "IP" }) }
        var auth by remember(i) { mutableStateOf(a.auth.ifEmpty { "none" }) }
        var user by remember(i) { mutableStateOf(a.user) }
        var pass by remember(i) { mutableStateOf(a.pass) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (a.name.isEmpty()) "پروفایل تازه" else "ویرایش ${a.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("اسم پروفایل", name, { name = it })
                    Field("APN", apn, { apn = it.trim() }, ltr = true)
                    Choice("نوع IP", listOf("IP" to "IPv4", "IPv6" to "IPv6", "IPv4v6" to "IPv4 و IPv6"), pdp) { pdp = it }
                    Choice("احراز هویت", listOf("none" to "هیچ", "chap" to "CHAP", "pap" to "PAP", "pap_chap" to "PAP یا CHAP"), auth) { auth = it }
                    if (auth != "none") {
                        Field("نام کاربری", user, { user = it }, ltr = true)
                        Field("رمز", pass, { pass = it }, ltr = true, password = true)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && apn.isNotBlank(), onClick = {
                    edit = null
                    st.run("ذخیره شد", then = { ver++ }) { saveApn(i, Apn(name.trim(), apn, auth, user, pass, pdp)) }
                }) { Text("ذخیره", color = K.ice) }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("لغو", color = K.dim) } },
            containerColor = K.raised,
        )
    }
    del?.let { i -> Ask("پروفایل پاک شود؟", "این پروفایل APN پاک می‌شود.", "پاک کن", danger = true, onYes = { st.run("پاک شد", then = { ver++ }) { deleteApn(i) } }, onNo = { del = null }) }
}

@Composable
private fun DataLimitPage(st: AppState, back: () -> Unit) {
    var reset by remember { mutableStateOf(false) }
    SubPage("سقف مصرف ماهانه", back, "وقتی مصرف ماه به درصد هشدار رسید، مودم خبر می‌دهد.") {
        Load(st, 0, { dataLimit() }) { d, reload ->
            val used = (d.s("monthly_rx_bytes").toDoubleOrNull() ?: 0.0) + (d.s("monthly_tx_bytes").toDoubleOrNull() ?: 0.0)
            Group {
                InfoRow("مصرف این ماه", Codec.bytes(used.toString()))
                Divider()
                InfoRow("زمان اتصال این ماه", Codec.duration(d.s("monthly_time")))
            }
            var on by remember(d) { mutableStateOf(d.s("data_volume_limit_switch") == "1") }
            var byData by remember(d) { mutableStateOf(d.s("data_volume_limit_unit") != "time") }
            val parts = d.s("data_volume_limit_size").split("_")
            var amount by remember(d) { mutableStateOf(if (byData) parts.getOrElse(0) { "" } else d.s("data_volume_limit_size")) }
            var unit by remember(d) { mutableStateOf(parts.getOrElse(1) { "1024" }.let { if (it == "1" || it == "1024" || it == "1048576") it else "1024" }) }
            var alert by remember(d) { mutableStateOf(d.s("data_volume_alert_percent").ifEmpty { "90" }) }
            Group { SwitchRow("سقف مصرف روشن", null, on) { on = it } }
            if (on) {
                Segments(listOf("بر اساس حجم", "بر اساس زمان"), if (byData) 0 else 1) { byData = it == 0 }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(if (byData) "مقدار" else "ساعت در ماه", amount, { v -> amount = v.filter { it.isDigit() || it == '.' } }, number = true, ltr = true, modifier = Modifier.weight(1f))
                    if (byData) Column(Modifier.weight(1f)) { Choice("واحد", listOf("1" to "MB", "1024" to "GB", "1048576" to "TB"), unit) { unit = it } }
                }
                Field("هشدار در چند درصد", alert, { v -> alert = v.filter { it.isDigit() }.take(3) }, number = true, ltr = true, hint = "۱ تا ۱۰۰")
            }
            val valid = !on || ((amount.toDoubleOrNull() ?: 0.0) > 0 && (alert.toIntOrNull() ?: 0) in 1..100)
            MainButton("ذخیره", enabled = valid) {
                st.run("ذخیره شد", then = reload) { setDataLimit(on, byData, if (byData) "${amount}_$unit" else amount, alert) }
            }
            Group(title = "تنظیم دستی مصرف این ماه", footer = "اگر شمارندهٔ مودم با حساب اپراتور جور نیست، عدد درست را بزن.") {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    var usedIn by remember(d) { mutableStateOf("") }
                    var usedUnit by remember(d) { mutableStateOf("1024") }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(if (byData) "مصرف درست" else "ساعت درست", usedIn, { v -> usedIn = v.filter { it.isDigit() || it == '.' }.take(8) }, number = true, ltr = true, modifier = Modifier.weight(1f))
                        if (byData) Column(Modifier.weight(1f)) { Choice("واحد", listOf("1" to "MB", "1024" to "GB"), usedUnit) { usedUnit = it } }
                    }
                    GhostButton("ثبت مصرف", enabled = usedIn.toDoubleOrNull() != null) {
                        // The modem takes MB for data and hours for time.
                        val v = if (byData) ((usedIn.toDouble() * usedUnit.toDouble())).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } else usedIn
                        st.run("ثبت شد", then = reload) { calibrate(byData, v) }
                    }
                }
            }
            GhostButton("صفر کردن مصرف این ماه", Icons.Filled.Refresh, K.warn) { reset = true }
            if (reset) Ask("مصرف ماه صفر شود؟", "شمارندهٔ مصرف این ماه مودم از صفر شروع می‌شود.", "صفر کن",
                onYes = { st.run("صفر شد", then = reload) { calibrate(byData, "0") } }, onNo = { reset = false })
        }
    }
}

// ---------------- Wi-Fi ----------------

/** Same channel list as the modem page (wifi_advance), from the country's group. */
private fun channels(st: AppState, country: String): List<Pair<String, String>> {
    val codes = st.options.optJSONObject("countryCode")
    var group = ""
    codes?.keys()?.forEach { k ->
        val arr = codes.optJSONArray(k)
        if (arr != null) for (i in 0 until arr.length()) if (arr.optString(i) == country) group = k
    }
    val type = st.options.optJSONObject("countryCodeType")?.optInt(group, 0) ?: 0
    val out = mutableListOf("0" to "خودکار")
    fun add(base: Int, n: Int) { for (k in 1..n) { val ch = out.size; out += "$ch" to "کانال $ch (${base + k * 5} MHz)" } }
    when (type) {
        9 -> add(2307, 13)
        7 -> { add(2307, 13); add(2407, 11); add(2462, 2) }
        3 -> { add(2407, 11); add(2462, 2) }
        2 -> { add(2307, 13); add(2407, 11) }
        else -> add(2407, 11)
    }
    return out
}

private val RATES = listOf(0.0, 1.0, 2.0, 5.5, 6.0, 6.5, 9.0, 11.0, 12.0, 13.0, 18.0, 19.5, 24.0, 26.0, 36.0, 39.0, 48.0, 52.0, 54.0, 58.5, 65.0)

/** Rate list for a Wi-Fi mode (index into RATES), as wifi_advance builds it. */
private fun rates(mode: String): List<Pair<String, String>> {
    val n = listOf(0, 5, 9, 11, 13, 15, 17, 19, 20)
    val g = listOf(0, 4, 6, 8, 10, 12, 14, 16, 18)
    val b = listOf(0, 1, 2, 3, 7)
    val sets = when (mode) { "5", "4" -> listOf(n, g, b); "3" -> listOf(g, b); "1" -> listOf(g); "0" -> listOf(b); else -> listOf(n) }
    return sets.flatten().distinct().sortedBy { RATES[it] }.map { i ->
        val r = RATES[i]
        "$i" to if (r == 0.0) "خودکار" else (if (r % 1.0 == 0.0) "${r.toInt()}" else "$r") + " Mbps"
    }
}

private fun rateText(i: String): String = i.toIntOrNull()?.let { RATES.getOrNull(it) }?.let { if (it % 1.0 == 0.0) "${it.toInt()}" else "$it" } ?: "0"

@Composable
private fun WifiAdvancedPage(st: AppState, back: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    SubPage("وای‌فای پیشرفته", back, "اگر وای‌فای اطراف شلوغ است، یک کانال خلوت‌تر انتخاب کن.") {
        Load(st, 0, { wifiAdvance() }) { d, reload ->
            var mode by remember(d) { mutableStateOf(d.s("WirelessMode").ifEmpty { "6" }) }
            var country by remember(d) { mutableStateOf(d.s("CountryCode")) }
            var channel by remember(d) { mutableStateOf(d.s("Channel").ifEmpty { "0" }) }
            var rate by remember(d) { mutableStateOf(d.s("HT_MCS").ifEmpty { "0" }) }
            val countries = st.options.optJSONObject("countries")
            val cList = buildList { countries?.keys()?.forEach { k -> if (k != "NONE") add(k to "${countries.optString(k)} ($k)") } }.sortedBy { it.second }
            val ch = channels(st, country)
            val rs = rates(mode)
            var band by remember(d) { mutableStateOf(d.s("wifi_band")) }
            var width by remember(d) { mutableStateOf(d.s("wifi_11n_cap")) }
            if (band.isNotEmpty()) Segments(listOf("2.4 GHz", "5 GHz"), if (band == "a") 1 else 0) { band = if (it == 1) "a" else "b"; mode = if (band == "a") "4" else "6"; channel = "0"; rate = "0" }
            if (band == "a") Text("روی ۵ گیگاهرتز سرعت بیشتر ولی برد کمتر است؛ کانال را خود مودم انتخاب می‌کند.", color = K.dim, fontSize = 12.sp)
            Choice(
                "حالت",
                (if (band == "a") listOf("5" to "فقط 802.11 a", "2" to "فقط 802.11 n", "4" to "802.11 a/n") else listOf("6" to "802.11 b/g/n/ax"))
                    .let { l -> if (l.none { it.first == mode }) l + (mode to "حالت $mode") else l },
                mode,
            ) { mode = it; rate = "0" }
            Choice("کشور", cList, country) { country = it; channel = "0" }
            if (band != "a") {
                Choice("کانال", ch, if (ch.any { it.first == channel }) channel else "0") { channel = it }
                Choice("سرعت", rs, if (rs.any { it.first == rate }) rate else "0") { rate = it }
            }
            if (width.isNotEmpty()) Choice("پهنای کانال", listOf("0" to "20 MHz", "1" to "20/40 MHz"), width) { width = it }
            if (d.s("WscModeOption") == "1") Text("WPS روشن است؛ مودم تا تمام شدن WPS این بخش را عوض نمی‌کند.", color = K.warn, fontSize = 13.sp)
            MainButton("ذخیره", enabled = d.s("WscModeOption") != "1") { ask = true }
            if (ask) Ask("ذخیره شود؟", "وای‌فای چند ثانیه قطع و دوباره وصل می‌شود.", "ذخیره کن",
                onYes = {
                    st.run("ذخیره شد", then = reload) {
                        setWifiAdvance(mode, country, channel, rateText(rate), d.s("MAX_Access_num"), d.s("m_MAX_Access_num").ifEmpty { "0" }, band, width)
                    }
                },
                onNo = { ask = false })
        }
    }
}

@Composable
private fun SleepPage(st: AppState, back: () -> Unit) = SubPage(
    "خواب و برد وای‌فای", back, "اگر مدتی هیچ دستگاهی وصل نباشد، وای‌فای می‌خوابد تا باتری بماند.",
) {
    Load(st, 0, { sleepAndRange() }) { d, reload ->
        var sleep by remember(d) { mutableStateOf(d.s("Sleep_interval").ifEmpty { "-1" }) }
        Choice(
            "خوابیدن وای‌فای بعد از",
            listOf("-1" to "هیچ‌وقت (همیشه روشن)", "5" to "۵ دقیقه", "10" to "۱۰ دقیقه", "20" to "۲۰ دقیقه", "30" to "۳۰ دقیقه", "60" to "۱ ساعت", "120" to "۲ ساعت"),
            sleep,
        ) { sleep = it }
        MainButton("ذخیره خواب") { st.run("ذخیره شد", then = reload) { setSleep(sleep) } }
        // Values from the modem's own page (subpg/wifi_sleep_mode.html).
        var range by remember(d) { mutableStateOf(d.s("wifi_coverage").ifEmpty { "long_mode" }) }
        Choice("برد وای‌فای", listOf("short_mode" to "کم (کم‌مصرف‌ترین)", "medium_mode" to "متوسط", "long_mode" to "زیاد (دورترین)"), range) { range = it }
        MainButton("ذخیره برد") { st.run("ذخیره شد", then = reload) { setRange(range) } }
    }
}

@Composable
private fun MacFilterPage(st: AppState, back: () -> Unit) {
    SubPage("فیلتر دستگاه‌ها", back, "با MAC (شناسهٔ کارت شبکه) تعیین کن چه دستگاه‌هایی می‌توانند به وای‌فای وصل شوند. حداکثر ۱۰ دستگاه.") {
        Load(st, 0, { macFilter() }) { d, reload ->
            var mode by remember(d) { mutableStateOf(d.s("ACL_mode").ifEmpty { "0" }) }
            val listFor = { m: String -> d.s(if (m == "1") "wifi_mac_white_list" else "wifi_mac_black_list").split(";").filter { it.isNotBlank() } }
            var macs by remember(d, mode) { mutableStateOf(listFor(mode)) }
            var add by remember { mutableStateOf("") }
            val me = d.s("client_mac_address")
            Choice("حالت", listOf("0" to "خاموش (همه می‌توانند وصل شوند)", "1" to "فقط این دستگاه‌ها وصل شوند", "2" to "این دستگاه‌ها مسدود شوند"), mode) { mode = it }
            if (mode != "0") {
                Group(title = if (mode == "1") "دستگاه‌های مجاز" else "دستگاه‌های مسدود") {
                    if (macs.isEmpty()) Text("فهرست خالی است.", color = K.dim, modifier = Modifier.padding(16.dp))
                    macs.forEachIndexed { i, m ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Ltr { Text(m.uppercase(), color = K.text, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f)) }
                            if (m.equals(me, true)) Pill("همین گوشی", K.good)
                            IconButton(onClick = { macs = macs - m }) { Icon(Icons.Filled.Delete, "پاک کردن", tint = K.bad) }
                        }
                    }
                }
                if (macs.size < 10) Row(verticalAlignment = Alignment.CenterVertically) {
                    Field("MAC تازه", add, { add = it.trim().take(17) }, ltr = true, error = add.isNotEmpty() && !MAC.matches(add), hint = "مثل 00:11:22:AA:BB:CC", modifier = Modifier.weight(1f))
                    IconButton(enabled = MAC.matches(add) && macs.none { it.equals(add, true) }, onClick = { macs = macs + add.uppercase(); add = "" }) {
                        Icon(Icons.Filled.Add, "افزودن", tint = K.ice)
                    }
                }
                if (mode == "1" && me.isNotEmpty() && macs.none { it.equals(me, true) }) {
                    Text("این گوشی در فهرست مجاز نیست؛ بعد از ذخیره قطع می‌شود.", color = K.warn, fontSize = 13.sp)
                    GhostButton("افزودن همین گوشی", Icons.Filled.Add) { macs = macs + me.uppercase() }
                }
            }
            val blocksMe = mode == "2" && macs.any { it.equals(me, true) }
            if (blocksMe) Text("نمی‌توانی همین گوشی را مسدود کنی.", color = K.bad, fontSize = 13.sp)
            MainButton("ذخیره", enabled = !blocksMe && (mode == "0" || macs.isNotEmpty())) {
                st.run("ذخیره شد", then = reload) { setMacFilter(mode, macs) }
            }
        }
    }
}

@Composable
private fun WpsPage(st: AppState, back: () -> Unit) = SubPage(
    "اتصال با دکمه (WPS)", back, "بدون زدن رمز وصل شو: اینجا شروع کن و در دو دقیقه دکمهٔ WPS دستگاه دیگر را بزن، یا کد PIN آن را اینجا بنویس.",
) {
    Load(st, 0, { wps() }) { d, reload ->
        var pin by remember { mutableStateOf("") }
        if (d.s("wifi_cur_state") == "0") { Empty("وای‌فای خاموش است."); return@Load }
        if (d.s("AuthMode") == "OPEN" || d.s("HideSSID") == "1") {
            Empty("WPS فقط وقتی وای‌فای رمز دارد و اسمش مخفی نیست کار می‌کند.")
            return@Load
        }
        if (d.s("WscModeOption") == "1") Pill("WPS الان روشن است", K.good)
        MainButton("شروع با دکمه", icon = Icons.Filled.TouchApp) { st.run("WPS شروع شد؛ دکمهٔ WPS دستگاه را بزن", then = reload) { startWps(d.s("SSID1"), null) } }
        Field("کد PIN دستگاه", pin, { v -> pin = v.filter { it.isDigit() }.take(8) }, number = true, ltr = true, hint = "۴ یا ۸ رقم")
        GhostButton("شروع با PIN", enabled = pin.length == 4 || pin.length == 8) { st.run("WPS با PIN شروع شد", then = reload) { startWps(d.s("SSID1"), pin) } }
    }
}

// ---------------- router ----------------

@Composable
private fun LanPage(st: AppState, back: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    SubPage("شبکهٔ داخلی", back, "آدرس مودم و آدرس‌هایی که به دستگاه‌ها می‌دهد. بعد از ذخیره، مودم ری‌استارت می‌شود.") {
        NeedsOffline(st)
        Load(st, 0, { lan() }) { d, _ ->
            var ip by remember(d) { mutableStateOf(d.s("lan_ipaddr")) }
            var mask by remember(d) { mutableStateOf(d.s("lan_netmask")) }
            var dhcp by remember(d) { mutableStateOf(d.s("dhcpEnabled") == "1") }
            var start by remember(d) { mutableStateOf(d.s("dhcpStart")) }
            var end by remember(d) { mutableStateOf(d.s("dhcpEnd")) }
            var lease by remember(d) { mutableStateOf(d.s("dhcpLease_hour").filter { it.isDigit() }) }
            val off = !st.connected
            Field("آدرس مودم", ip, { ip = it.trim() }, ltr = true, error = !IP.matches(ip), enabled = off)
            Field("ماسک شبکه", mask, { mask = it.trim() }, ltr = true, error = !IP.matches(mask), enabled = off)
            Group { SwitchRow("سرور DHCP", "به دستگاه‌ها خودکار آدرس بده", dhcp, enabled = off) { dhcp = it } }
            if (dhcp) {
                Field("شروع آدرس‌ها", start, { start = it.trim() }, ltr = true, error = !IP.matches(start), enabled = off)
                Field("پایان آدرس‌ها", end, { end = it.trim() }, ltr = true, error = !IP.matches(end), enabled = off)
                Field("مدت اجاره (ساعت)", lease, { v -> lease = v.filter { it.isDigit() }.take(5) }, ltr = true, number = true, enabled = off,
                    error = (lease.toIntOrNull() ?: 0) !in 1..65535)
            }
            val ok = IP.matches(ip) && IP.matches(mask) && (!dhcp || (IP.matches(start) && IP.matches(end) && (lease.toIntOrNull() ?: 0) in 1..65535))
            MainButton("ذخیره و ری‌استارت", enabled = ok && off) { ask = true }
            if (ask) Ask("ذخیره شود؟", "مودم ری‌استارت می‌شود. اگر آدرس مودم را عوض کردی، دفعهٔ بعد در صفحهٔ ورود آدرس تازه را بزن.", "ذخیره کن",
                onYes = { st.run("ذخیره شد؛ مودم ری‌استارت می‌شود") { setLan(ip, mask, dhcp, start, end, lease) } }, onNo = { ask = false })
        }
    }
}

@Composable
private fun DnsPage(st: AppState, back: () -> Unit) = SubPage("DNS", back, "DNS دستی برای همهٔ دستگاه‌های وصل به مودم.") {
    Load(st, 0, { dns() }) { d, reload ->
        val known = d.s("dns_manual_enable") == "0" || d.s("dns_manual_enable") == "1"
        if (!known) { Empty("این مودم DNS را به شکلی که برنامه می‌شناسد نمی‌دهد (${d.s("dns_manual_enable")})."); return@Load }
        var manual by remember(d) { mutableStateOf(d.s("dns_manual_enable") == "1") }
        var dns by remember(d) { mutableStateOf(d.s("dhcpDns")) }
        Group { SwitchRow("DNS دستی", null, manual) { manual = it } }
        if (manual) Field("آدرس DNS", dns, { dns = it.trim() }, ltr = true, error = !IP.matches(dns), hint = "مثل 1.1.1.1")
        MainButton("ذخیره", enabled = !manual || IP.matches(dns)) { st.run("ذخیره شد", then = reload) { setDns(if (manual) "1" else "0", dns) } }
    }
}

/** Shared frame for the four rule lists: on/off switch, rules with delete, and an add form. */
@Composable
private fun RulesFrame(
    st: AppState,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    rules: List<Rule>,
    line1: (Rule) -> String,
    line2: (Rule) -> String,
    onDelete: (Rule) -> Unit,
    addForm: @Composable () -> Unit,
) {
    Group { SwitchRow("روشن", null, enabled) { onToggle(it) } }
    if (rules.isEmpty()) Empty("قانونی نیست.")
    else Group(title = "${rules.size} قانون (حداکثر ۱۰)") {
        rules.forEachIndexed { i, r ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Ltr { Text(line1(r), color = K.text, fontWeight = FontWeight.Medium) }
                    Text(line2(r), color = K.dim, fontSize = 12.sp)
                }
                IconButton(onClick = { onDelete(r) }) { Icon(Icons.Filled.Delete, "پاک کردن", tint = K.bad) }
            }
        }
    }
    if (rules.size < 10 && enabled) Group(title = "قانون تازه") { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { addForm() } }
}

private fun protoName(v: String) = when (v.uppercase()) { "TCP&UDP", "3", "TCP+UDP" -> "TCP + UDP"; "TCP", "1" -> "TCP"; "UDP", "2" -> "UDP"; "ICMP", "4" -> "ICMP"; "NONE", "0" -> "همه"; else -> v }

@Composable
private fun ForwardPage(st: AppState, back: () -> Unit) {
    var ver by remember { mutableIntStateOf(0) }
    SubPage("فوروارد پورت", back, "درخواست‌هایی که از اینترنت به این پورت‌ها می‌آید به یک دستگاه داخل شبکه برود.") {
        Load(st, ver, { portForward() }) { (on, rules), _ ->
            var ip by remember { mutableStateOf("") }
            var a by remember { mutableStateOf("") }
            var b by remember { mutableStateOf("") }
            var proto by remember { mutableStateOf("TCP&UDP") }
            var note by remember { mutableStateOf("") }
            RulesFrame(st, on == "1", { v -> st.run("ذخیره شد", then = { ver++ }) { setPortForwardOn(v) } }, rules,
                { "${it.at(0)}  :${it.at(1)}-${it.at(2)}" }, { listOf(protoName(it.at(3)), it.at(4)).filter { s -> s.isNotBlank() }.joinToString("، ") },
                { r -> st.run("پاک شد", then = { ver++ }) { deletePortForward(listOf(r.index)) } }) {
                Field("IP دستگاه", ip, { ip = it.trim() }, ltr = true, error = ip.isNotEmpty() && !IP.matches(ip))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("از پورت", a, { v -> a = v.filter { it.isDigit() }.take(5) }, number = true, ltr = true, modifier = Modifier.weight(1f))
                    Field("تا پورت", b, { v -> b = v.filter { it.isDigit() }.take(5) }, number = true, ltr = true, modifier = Modifier.weight(1f))
                }
                Choice("پروتکل", PROTO, proto) { proto = it }
                Field("توضیح (اختیاری)", note, { note = it.filter { c -> c != ',' && c != ';' }.take(32) })
                MainButton("افزودن", enabled = IP.matches(ip) && port(a) && port(b) && a.toInt() <= b.toInt(), icon = Icons.Filled.Add) {
                    st.run("اضافه شد", then = { ver++ }) { addPortForward(ip, a, b, proto, note) }
                }
            }
        }
    }
}

@Composable
private fun FilterPage(st: AppState, back: () -> Unit) {
    var ver by remember { mutableIntStateOf(0) }
    SubPage("فیلتر پورت و IP", back, "ترافیک را بر اساس آدرس، پورت و پروتکل ببند یا باز کن.") {
        Load(st, ver, { portFilter() }) { (on, policy0, rules), _ ->
            var policy by remember(policy0) { mutableStateOf(policy0.ifEmpty { "0" }) }
            var enabled by remember(on) { mutableStateOf(on == "1") }
            Group { SwitchRow("فیلتر روشن", null, enabled) { enabled = it } }
            Choice("رفتار پیش‌فرض", listOf("0" to "همه باز، قانون‌ها می‌بندند", "1" to "همه بسته، قانون‌ها باز می‌کنند"), policy) { policy = it }
            GhostButton("ذخیره حالت") { st.run("ذخیره شد", then = { ver++ }) { setPortFilterBasic(enabled, policy) } }
            if (rules.isEmpty()) Empty("قانونی نیست.")
            else Group(title = "قانون‌ها") {
                rules.forEachIndexed { i, r ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Ltr { Text("${r.at(0).removeSuffix("/0").ifEmpty { "*" }}:${r.at(2)} → ${r.at(4).removeSuffix("/0").ifEmpty { "*" }}:${r.at(6)}", color = K.text) }
                            Text(listOf(protoName(r.at(8)), if (r.at(9) == "1") "باز" else "بسته", if (r.index >= 10) "IPv6" else "IPv4", r.at(10)).filter { it.isNotBlank() }.joinToString("، "), color = K.dim, fontSize = 12.sp)
                        }
                        IconButton(onClick = { st.run("پاک شد", then = { ver++ }) { deletePortFilter(listOf(r.index)) } }) { Icon(Icons.Filled.Delete, "پاک کردن", tint = K.bad) }
                    }
                }
            }
            if (enabled && rules.size < 20) Group(title = if (policy == "1") "قانون تازه (باز کن)" else "قانون تازه (ببند)") {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    var v6 by remember { mutableStateOf(false) }
                    var mac by remember { mutableStateOf("") }
                    var src by remember { mutableStateOf("") }
                    var dst by remember { mutableStateOf("") }
                    var sa by remember { mutableStateOf("") }; var sb by remember { mutableStateOf("") }
                    var da by remember { mutableStateOf("") }; var db by remember { mutableStateOf("") }
                    var proto by remember { mutableStateOf("TCP") }
                    var note by remember { mutableStateOf("") }
                    Segments(listOf("IPv4", "IPv6"), if (v6) 1 else 0) { v6 = it == 1 }
                    Field("MAC (اختیاری)", mac, { mac = it.trim() }, ltr = true, error = mac.isNotEmpty() && !MAC.matches(mac))
                    Field("IP مبدأ (اختیاری)", src, { src = it.trim() }, ltr = true)
                    Field("IP مقصد (اختیاری)", dst, { dst = it.trim() }, ltr = true)
                    Choice("پروتکل", listOf("None" to "همه", "TCP" to "TCP", "UDP" to "UDP", "ICMP" to "ICMP"), proto) { proto = it }
                    if (proto == "TCP" || proto == "UDP") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Field("پورت مبدأ از", sa, { v -> sa = v.filter { it.isDigit() }.take(5) }, number = true, ltr = true, modifier = Modifier.weight(1f))
                            Field("تا", sb, { v -> sb = v.filter { it.isDigit() }.take(5) }, number = true, ltr = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Field("پورت مقصد از", da, { v -> da = v.filter { it.isDigit() }.take(5) }, number = true, ltr = true, modifier = Modifier.weight(1f))
                            Field("تا", db, { v -> db = v.filter { it.isDigit() }.take(5) }, number = true, ltr = true, modifier = Modifier.weight(1f))
                        }
                    }
                    Field("توضیح (اختیاری)", note, { note = it.filter { c -> c != ',' && c != ';' }.take(32) })
                    val portsOk = listOf(sa to sb, da to db).all { (x, y) -> (x.isEmpty() && y.isEmpty()) || (port(x) && port(y) && x.toInt() <= y.toInt()) }
                    val any = mac.isNotEmpty() || src.isNotEmpty() || dst.isNotEmpty() || sa.isNotEmpty() || da.isNotEmpty()
                    MainButton("افزودن", enabled = any && portsOk && (mac.isEmpty() || MAC.matches(mac)), icon = Icons.Filled.Add) {
                        st.run("اضافه شد", then = { ver++ }) { addPortFilter(v6, mac, dst, src, da, db, sa, sb, policy, proto, note) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapPage(st: AppState, back: () -> Unit) {
    var ver by remember { mutableIntStateOf(0) }
    SubPage("نگاشت پورت", back, "یک پورت بیرونی به پورت دیگری روی یک دستگاه داخل شبکه برود.") {
        Load(st, ver, { portMap() }) { (on, rules), _ ->
            var from by remember { mutableStateOf("") }
            var ip by remember { mutableStateOf("") }
            var to by remember { mutableStateOf("") }
            var proto by remember { mutableStateOf("TCP&UDP") }
            var note by remember { mutableStateOf("") }
            RulesFrame(st, on == "1", { v -> st.run("ذخیره شد", then = { ver++ }) { setPortMapOn(v) } }, rules,
                { ":${it.at(1)} → ${it.at(0)}:${it.at(2)}" }, { listOf(protoName(it.at(3)), it.at(4)).filter { s -> s.isNotBlank() }.joinToString("، ") },
                { r -> st.run("پاک شد", then = { ver++ }) { deletePortMap(listOf(r.index)) } }) {
                Field("پورت بیرونی", from, { v -> from = v.filter { it.isDigit() }.take(5) }, number = true, ltr = true)
                Field("IP دستگاه", ip, { ip = it.trim() }, ltr = true, error = ip.isNotEmpty() && !IP.matches(ip))
                Field("پورت دستگاه", to, { v -> to = v.filter { it.isDigit() }.take(5) }, number = true, ltr = true)
                Choice("پروتکل", PROTO, proto) { proto = it }
                Field("توضیح (اختیاری)", note, { note = it.filter { c -> c != ',' && c != ';' }.take(32) })
                MainButton("افزودن", enabled = port(from) && port(to) && IP.matches(ip), icon = Icons.Filled.Add) {
                    st.run("اضافه شد", then = { ver++ }) { addPortMap(from, ip, to, proto, note) }
                }
            }
        }
    }
}

@Composable
private fun RatePage(st: AppState, back: () -> Unit) {
    var ver by remember { mutableIntStateOf(0) }
    SubPage("محدود کردن سرعت", back, "سرعت دانلود و آپلود یک دستگاه (با IP آن) را محدود کن.") {
        Load(st, ver, { rateLimit() }) { (on, rules), _ ->
            var ip by remember { mutableStateOf("") }
            var down by remember { mutableStateOf("") }
            var up by remember { mutableStateOf("") }
            var note by remember { mutableStateOf("") }
            RulesFrame(st, on == "1", { v -> st.run("ذخیره شد", then = { ver++ }) { setRateLimitOn(v) } }, rules,
                { it.at(0) }, { listOf("دانلود ${it.at(1)} KB", "آپلود ${it.at(2)} KB", it.at(3)).filter { s -> s.isNotBlank() }.joinToString("، ") },
                { r -> st.run("پاک شد", then = { ver++ }) { deleteRateLimit(listOf(r.index)) } }) {
                Field("IP دستگاه", ip, { ip = it.trim() }, ltr = true, error = ip.isNotEmpty() && !IP.matches(ip), hint = "IP هر دستگاه در صفحهٔ «دستگاه‌ها» هست")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("سرعت دانلود", down, { v -> down = v.filter { it.isDigit() }.take(7) }, number = true, ltr = true, modifier = Modifier.weight(1f))
                    Field("سرعت آپلود", up, { v -> up = v.filter { it.isDigit() }.take(7) }, number = true, ltr = true, modifier = Modifier.weight(1f))
                }
                Text("به کیلوبایت (KB)، از ۱ تا ۱٬۰۰۰٬۰۰۰.", color = K.dim, fontSize = 12.sp)
                Field("توضیح (اختیاری)", note, { note = it.filter { c -> c != ',' && c != ';' }.take(32) })
                MainButton("افزودن", enabled = IP.matches(ip) && (down.toIntOrNull() ?: 0) in 1..1000000 && (up.toIntOrNull() ?: 0) in 1..1000000, icon = Icons.Filled.Add) {
                    st.run("اضافه شد", then = { ver++ }) { addRateLimit(ip, down, up, note) }
                }
            }
        }
    }
}

@Composable
private fun DmzPage(st: AppState, back: () -> Unit) = SubPage("DMZ", back, "همهٔ درخواست‌های بیرونی به یک دستگاه داخل شبکه برود. فقط اگر می‌دانی چه می‌کنی.") {
    Load(st, 0, { dmz() }) { d, reload ->
        var on by remember(d) { mutableStateOf(d.s("DMZEnable") == "1") }
        var ip by remember(d) { mutableStateOf(d.s("DMZIPAddress")) }
        Group { SwitchRow("DMZ روشن", null, on) { on = it } }
        if (on) Field("IP دستگاه", ip, { ip = it.trim() }, ltr = true, error = !IP.matches(ip))
        MainButton("ذخیره", enabled = !on || IP.matches(ip)) { st.run("ذخیره شد", then = reload) { setDmz(on, ip) } }
    }
}

@Composable
private fun UpnpPage(st: AppState, back: () -> Unit) = SubPage("UPnP", back, "بازی‌ها و برنامه‌ها خودشان پورت باز کنند (برای بازی آنلاین و تماس تصویری مفید است).") {
    Load(st, 0, { upnp() }) { d, reload ->
        val on = d.s("upnpEnabled") == "1"
        Group { SwitchRow("UPnP روشن", null, on) { v -> st.run("ذخیره شد", then = reload) { setUpnp(v) } } }
    }
}

@Composable
private fun SecurityPage(st: AppState, back: () -> Unit) = SubPage("دسترسی از بیرون", back) {
    Load(st, 0, { sysSecurity() }) { d, reload ->
        var remote by remember(d) { mutableStateOf(d.s("RemoteManagement") == "1") }
        var ping by remember(d) { mutableStateOf(d.s("WANPingFilter") == "1") }
        Group {
            SwitchRow("مدیریت از راه دور", "صفحهٔ مدیریت از اینترنت هم باز شود (خطرناک)", remote) { remote = it }
            Divider()
            SwitchRow("جواب به پینگ از اینترنت", "مودم به پینگ بیرونی جواب بدهد", ping) { ping = it }
        }
        MainButton("ذخیره") { st.run("ذخیره شد", then = reload) { setSysSecurity(remote, ping) } }
    }
}

// ---------------- SIM and messages ----------------

@Composable
private fun PinPage(st: AppState, back: () -> Unit) = SubPage("PIN سیم‌کارت", back, "با PIN، سیم‌کارت در گوشی یا مودم دیگر بدون رمز کار نمی‌کند.") {
    NeedsOffline(st)
    Load(st, 0, { pin() }) { d, reload ->
        val on = d.s("pin_status") == "1"
        val left = d.s("pinnumber")
        var cur by remember(d) { mutableStateOf("") }
        var new1 by remember(d) { mutableStateOf("") }
        var new2 by remember(d) { mutableStateOf("") }
        var change by remember(d) { mutableStateOf(false) }
        val pinOk = { p: String -> p.length in 4..8 && p.all { it.isDigit() } }
        val off = !st.connected
        Group {
            InfoRow("PIN", if (on) "روشن" else "خاموش", if (on) K.good else K.dim)
            Divider()
            InfoRow("فرصت باقی", left, if ((left.toIntOrNull() ?: 3) <= 1) K.bad else K.text)
        }
        if ((left.toIntOrNull() ?: 3) <= 1) Text("فقط یک فرصت مانده؛ اشتباه دوباره سیم‌کارت را قفل می‌کند (PUK لازم می‌شود).", color = K.bad, fontSize = 13.sp)
        Field("PIN فعلی", cur, { v -> cur = v.filter { it.isDigit() }.take(8) }, password = true, number = true, enabled = off)
        if (on) Segments(listOf("خاموش کردن", "عوض کردن"), if (change) 1 else 0) { change = it == 1 }
        if (on && change) {
            Field("PIN تازه", new1, { v -> new1 = v.filter { it.isDigit() }.take(8) }, password = true, number = true, enabled = off)
            Field("تکرار PIN تازه", new2, { v -> new2 = v.filter { it.isDigit() }.take(8) }, password = true, number = true, enabled = off, error = new2.isNotEmpty() && new2 != new1)
            MainButton("عوض کن", enabled = off && pinOk(cur) && pinOk(new1) && new1 == new2) { st.run("PIN عوض شد", "PIN اشتباه است", then = reload) { changePin(cur, new1) } }
        } else {
            MainButton(if (on) "خاموش کن" else "روشن کن", enabled = off && pinOk(cur)) { st.run("ذخیره شد", "PIN اشتباه است", then = reload) { setPinOn(!on, cur) } }
        }
    }
}

@Composable
private fun SmsSettingsPage(st: AppState, back: () -> Unit) = SubPage("تنظیمات پیامک", back) {
    Load(st, 0, { smsSettings() to smsCapacity() }) { (d, cap), reload ->
        var center by remember(d) { mutableStateOf(d.s("sms_para_sca")) }
        var validity by remember(d) { mutableStateOf(d.s("validity")) }
        var report by remember(d) { mutableStateOf(d.s("sms_para_status_report") == "1") }
        Group {
            InfoRow("جای خالی در مودم", "${cap.s("sms_nv_total").toIntOrNull()?.let { t -> t - listOf("sms_nv_rev_total", "sms_nv_send_total", "sms_nv_draftbox_total").sumOf { cap.s(it).toIntOrNull() ?: 0 } } ?: "—"} از ${cap.s("sms_nv_total")}")
            Divider()
            InfoRow("پیام روی سیم‌کارت", "${cap.s("sms_sim_rev_total")} از ${cap.s("sms_sim_total")}")
        }
        Field("شمارهٔ مرکز پیام", center, { v -> center = v.filter { it.isDigit() || it == '+' } }, ltr = true, number = true, hint = "اپراتور خودش می‌گذارد؛ فقط اگر می‌دانی عوض کن")
        Choice("اعتبار پیامک", listOf("twelve_hours" to "۱۲ ساعت", "one_day" to "یک روز", "one_week" to "یک هفته", "largest" to "بیشترین"), validity) { validity = it }
        Group { SwitchRow("گزارش رسیدن پیامک", "وقتی پیامک رسید خبر بده", report) { report = it } }
        MainButton("ذخیره", enabled = center.isNotBlank()) { st.run("ذخیره شد", then = reload) { setSmsSettings(center, validity, if (report) "1" else "0") } }
    }
}

@Composable
private fun UssdPage(st: AppState, back: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<Ussd.Answer?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun send(reply: Boolean) {
        wait = true; err = null
        scope.launch {
            val r = st.io { ussd(code, reply) }
            wait = false
            r.onSuccess { when (it) { is Ussd.Answer -> { answer = it; code = "" }; is Ussd.Error -> err = it.why } }.onFailure { err = st.netError(it) }
        }
    }
    SubPage("کد دستوری (USSD)", back, "مثل زدن کد در گوشی، برای دیدن شارژ یا بسته‌ها. مثلاً ${Codec.iso("*140#")}") {
        answer?.let { a ->
            Group { Text(a.text, color = K.text, lineHeight = 24.sp, modifier = Modifier.padding(16.dp)) }
        }
        err?.let { Text(it, color = K.bad) }
        Field(if (answer?.canReply == true) "جواب (مثلاً شمارهٔ گزینه)" else "کد", code, { code = it.trim() }, ltr = true)
        if (wait) Text("منتظر جواب اپراتور…", color = K.dim)
        if (answer?.canReply == true) {
            MainButton("بفرست", enabled = code.isNotEmpty() && !wait) { send(true) }
            GhostButton("پایان گفت‌وگو", color = K.bad) { scope.launch { st.io { ussdCancel() }; answer = null } }
        } else {
            MainButton("بفرست", enabled = code.isNotEmpty() && !wait) { send(false) }
        }
    }
}

private val GROUPS = listOf("common" to "عمومی", "family" to "خانواده", "friend" to "دوستان", "colleague" to "همکاران")

@Composable
private fun ContactsPage(st: AppState, back: () -> Unit) {
    var ver by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<Contact?>(null) }
    var del by remember { mutableStateOf<Contact?>(null) }
    var delAll by remember { mutableStateOf<Int?>(null) }
    var group by remember { mutableStateOf("") }
    SubPage("دفترچه تلفن", back, action = {
        IconButton(onClick = { edit = Contact("-1", "", "", false, "common") }) { Icon(Icons.Filled.Add, "مخاطب تازه", tint = K.ice) }
    }) {
        Load(st, ver, { Triple(contacts(), runCatching { contactRoom(false) }.getOrDefault(0 to 0), runCatching { contactRoom(true) }.getOrDefault(0 to 0)) }) { (all, dev, sim), _ ->
            if (dev.second > 0) Room(dev.first, dev.second, "جای مخاطب در مودم")
            if (sim.second > 0) Room(sim.first, sim.second, "جای مخاطب در سیم‌کارت")
            Choice("گروه", listOf("" to "همه") + GROUPS + ("sim" to "سیم‌کارت"), group) { group = it }
            val list = when (group) { "" -> all; "sim" -> all.filter { it.onSim }; else -> all.filter { !it.onSim && it.group == group } }
            if (list.isEmpty()) Empty("مخاطبی نیست. با + اضافه کن.")
            else Group(title = "${list.size} مخاطب") {
                list.forEachIndexed { i, c ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.Person, if (c.onSim) K.warn else K.cyan)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = K.text, fontWeight = FontWeight.Bold)
                            Row {
                                Ltr { Text(c.number, color = K.dim, fontSize = 12.sp) }
                                Spacer(Modifier.width(6.dp))
                                Text(if (c.onSim) "سیم‌کارت" else GROUPS.firstOrNull { it.first == c.group }?.second ?: "مودم", color = K.dim, fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = { st.pages.clear(); st.tab = Tab.SMS; st.compose = Compose(c.number) }) { Icon(Icons.Filled.Sms, "پیامک", tint = K.pink) }
                        IconButton(onClick = { edit = c }) { Icon(Icons.Filled.Edit, "ویرایش", tint = K.ice) }
                        IconButton(onClick = { del = c }) { Icon(Icons.Filled.Delete, "پاک کردن", tint = K.bad) }
                    }
                }
            }
            if (all.any { !it.onSim }) GhostButton("پاک کردن همهٔ مخاطب‌های مودم", Icons.Filled.Delete, K.bad) { delAll = 1 }
            if (all.any { it.onSim }) GhostButton("پاک کردن همهٔ مخاطب‌های سیم‌کارت", Icons.Filled.Delete, K.bad) { delAll = 0 }
        }
    }
    edit?.let { c ->
        val isNew = c.id == "-1"
        var name by remember(c) { mutableStateOf(c.name) }
        var num by remember(c) { mutableStateOf(c.number) }
        var sim by remember(c) { mutableStateOf(c.onSim) }
        var grp by remember(c) { mutableStateOf(c.group.ifEmpty { "common" }) }
        var home by remember(c) { mutableStateOf(c.home) }
        var office by remember(c) { mutableStateOf(c.office) }
        var email by remember(c) { mutableStateOf(c.email) }
        val phone = { v: String -> v.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }.take(40) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (isNew) "مخاطب تازه" else "ویرایش مخاطب") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Segments(listOf("در مودم", "در سیم‌کارت"), if (sim) 1 else 0) { sim = it == 1 }
                    Field("اسم", name, { name = it.take(if (sim) 14 else 22) })
                    Field("موبایل", num, { num = phone(it) }, ltr = true, number = true)
                    if (!sim) {
                        Field("تلفن خانه", home, { home = phone(it) }, ltr = true, number = true)
                        Field("تلفن محل کار", office, { office = phone(it) }, ltr = true, number = true)
                        Field("ایمیل", email, { email = it.trim().take(40) }, ltr = true)
                        Choice("گروه", GROUPS, grp) { grp = it }
                    } else Text("روی سیم‌کارت فقط اسم و یک شماره جا می‌شود.", color = K.dim, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && num.isNotBlank(), onClick = {
                    edit = null
                    val n = Contact(c.id, name.trim(), num, sim, grp, home, office, email)
                    st.run("ذخیره شد", then = { ver++ }) { saveContact(n, if (isNew) null else c.onSim) }
                }) { Text("ذخیره", color = K.ice) }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("لغو", color = K.dim) } },
            containerColor = K.raised,
        )
    }
    del?.let { c -> Ask("«${c.name}» پاک شود؟", c.number, "پاک کن", danger = true, onYes = { st.run("پاک شد", then = { ver++ }) { deleteContacts(listOf(c.id)) } }, onNo = { del = null }) }
    delAll?.let { loc ->
        Ask("همه پاک شوند؟", if (loc == 0) "همهٔ مخاطب‌های سیم‌کارت پاک می‌شوند." else "همهٔ مخاطب‌های مودم پاک می‌شوند.", "همه را پاک کن", danger = true,
            onYes = { st.run("پاک شد", then = { ver++ }) { deleteAllContacts(loc) } }, onNo = { delAll = null })
    }
}

// ---------------- device ----------------

@Composable
private fun AboutPage(st: AppState, back: () -> Unit) = SubPage("اطلاعات آنتن و مودم", back) {
    val s = st.status
    Group(title = "آنتن (زنده)", footer = "RSRP بالای ‎-90‎ خوب، ‎-90‎ تا ‎-105‎ متوسط، زیر ‎-105‎ ضعیف. SINR بالای ۱۰ خوب است.") {
        InfoRow("RSRP (قدرت)", s.s("lte_rsrp").let { if (it.isEmpty()) "" else "$it dBm" })
        Divider(); InfoRow("SINR (کیفیت)", s.s("detail_cell_sinr").let { if (it.isEmpty()) "" else "$it dB" })
        Divider(); InfoRow("RSRQ", s.s("detail_cell_rsrq"))
        Divider(); InfoRow("RSSI", s.s("detail_cell_rssi").ifEmpty { s.s("rssi") })
        Divider(); InfoRow("PCI", s.s("detail_cell_pci"))
        Divider(); InfoRow("Cell ID", s.s("detail_cell_id"))
        Divider(); InfoRow("شبکه", netName(s.s("sub_network_type").ifEmpty { s.s("network_type") }))
    }
    Load(st, 0, { deviceInfo().put("band", runCatching { band() }.getOrDefault("")) }) { d, _ ->
        if (d.s("band").isNotEmpty()) Group { InfoRow("باند فعلی", d.s("band")) }
        Group(title = "مودم") {
            InfoRow("IMEI", d.s("imei")); Divider()
            InfoRow("ICCID سیم‌کارت", d.s("ziccid")); Divider()
            InfoRow("IMSI", d.s("imsi").ifEmpty { d.s("sim_imsi") }); Divider()
            InfoRow("شمارهٔ سیم‌کارت", d.s("msisdn")); Divider()
            InfoRow("IP داخلی", d.s("lan_ipaddr")); Divider()
            InfoRow("IP اینترنت", d.s("wan_ipaddr")); Divider()
            if (d.s("ipv6_wan_ipaddr").isNotEmpty()) { InfoRow("IPv6", d.s("ipv6_wan_ipaddr")); Divider() }
            InfoRow("MAC", d.s("mac_address").uppercase()); Divider()
            InfoRow("نسخهٔ نرم‌افزار", d.s("cstm_sw_version").ifEmpty { d.s("cr_version") }); Divider()
            InfoRow("نسخهٔ سخت‌افزار", d.s("hw_version"))
        }
    }
}

@Composable
private fun PasswordPage(st: AppState, back: () -> Unit) = SubPage("رمز مدیریت", back, "رمزی که با آن وارد این برنامه و صفحهٔ مودم می‌شوی.") {
    var old by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    Field("رمز فعلی", old, { old = it }, password = true, ltr = true)
    Field("رمز تازه", new1, { new1 = it.take(32) }, password = true, ltr = true, hint = "حداقل ۸ حرف، با حرف و عدد", error = new1.isNotEmpty() && new1.length < 8)
    Field("تکرار رمز تازه", new2, { new2 = it.take(32) }, password = true, ltr = true, error = new2.isNotEmpty() && new2 != new1)
    MainButton("عوض کن", enabled = old.isNotEmpty() && new1.length >= 8 && new1 == new2) {
        st.run("رمز عوض شد", "رمز فعلی اشتباه است", then = { st.passwordChanged(new1); old = ""; new1 = ""; new2 = "" }) { changeAdminPassword(old, new1) }
    }
}

@Composable
private fun TimePage(st: AppState, back: () -> Unit) = SubPage("ساعت و تاریخ", back, "ساعت مودم برای گزارش مصرف ماهانه و تاریخ پیامک‌ها مهم است.") {
    Load(st, 0, { sntp() }) { d, reload ->
        var auto by remember(d) { mutableStateOf(d.s("sntp_time_set_mode") != "manual") }
        val zones = st.options.optJSONArray("timeZone")
        val zList = buildList { if (zones != null) for (i in 0 until zones.length()) zones.optJSONObject(i)?.let { add(it.optString("value") to it.optString("name")) } }
        val cur = d.s("sntp_timezone") + "_" + d.s("sntp_timezone_index").ifEmpty { "0" }
        var zone by remember(d) { mutableStateOf(cur) }
        var dst by remember(d) { mutableStateOf(d.s("sntp_dst_enable") == "1") }
        val now = java.util.Calendar.getInstance()
        Group {
            InfoRow("ساعت مودم", "${d.s("sntp_year")}/${d.s("sntp_month").padStart(2, '0')}/${d.s("sntp_day").padStart(2, '0')}  ${d.s("sntp_hour").padStart(2, '0')}:${d.s("sntp_minute").padStart(2, '0')}")
        }
        Segments(listOf("خودکار از اینترنت", "از ساعت همین گوشی"), if (auto) 0 else 1) { auto = it == 0 }
        if (auto && !st.connected) Text("برای ساعت خودکار، اینترنت مودم باید وصل باشد.", color = K.warn, fontSize = 13.sp)
        Choice("منطقهٔ زمانی", zList, zone) { zone = it }
        val servers = remember(d) { mutableStateListOf(d.s("sntp_server0"), d.s("sntp_server1"), d.s("sntp_server2")) }
        val others = remember(d) { mutableStateListOf(d.s("sntp_other_server0"), d.s("sntp_other_server1"), d.s("sntp_other_server2")) }
        if (auto) {
            // Same list as the modem page: its fixed servers, "Other" (type one), or none.
            val fixed = (0..2).map { d.s("sntp_static_server$it") }.filter { it.isNotEmpty() }
            val opts = fixed.map { it to it } + listOf("Other" to "دیگر (بنویس)", "" to "هیچ")
            (0..2).forEach { i ->
                Choice("سرور ساعت ${i + 1}", opts.let { o -> if (o.none { it.first == servers[i] }) o + (servers[i] to servers[i]) else o }, servers[i]) { servers[i] = it }
                if (servers[i] == "Other") Field("آدرس سرور ${i + 1}", others[i], { others[i] = it.trim() }, ltr = true)
            }
        }
        Group { SwitchRow("ساعت تابستانی", null, dst) { dst = it } }
        MainButton("ذخیره", enabled = (!auto || st.connected) && (!auto || servers.any { it.isNotEmpty() })) {
            st.run("ذخیره شد", then = reload) {
                setSntp(
                    auto, d, zone, if (dst) "1" else "0",
                    now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH) + 1, now.get(java.util.Calendar.DAY_OF_MONTH),
                    now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE),
                    servers.toList(), others.toList(),
                )
            }
        }
    }
}

@Composable
private fun FastBootPage(st: AppState, back: () -> Unit) = SubPage("روشن شدن سریع", back, "مودم سریع‌تر روشن می‌شود. وقتی PIN سیم‌کارت روشن است کار نمی‌کند.") {
    Load(st, 0, { fastBoot() }) { d, reload ->
        val can = d.s("need_sim_pin") != "yes"
        Group { SwitchRow("روشن شدن سریع", if (can) null else "اول PIN سیم‌کارت را خاموش کن", d.s("mgmt_quicken_power_on") == "1", enabled = can) { v -> st.run("ذخیره شد", then = reload) { setFastBoot(v) } } }
    }
}

@Composable
private fun KeepAlivePage(st: AppState, back: () -> Unit) = SubPage("نگه داشتن اتصال", back, "مودم مدام دو آدرس را پینگ می‌کند و اگر جواب نیامد، اتصال را دوباره برقرار می‌کند.") {
    Load(st, 0, { keepAlive() }) { d, reload ->
        var on by remember(d) { mutableStateOf(d.s("keep_alive_enable") == "1") }
        var u1 by remember(d) { mutableStateOf(d.s("ping_detect_url1")) }
        var u2 by remember(d) { mutableStateOf(d.s("ping_detect_url2")) }
        Group { SwitchRow("روشن", null, on) { on = it } }
        if (on) {
            Field("آدرس اول", u1, { u1 = it.trim() }, ltr = true, hint = "مثل 8.8.8.8")
            Field("آدرس دوم", u2, { u2 = it.trim() }, ltr = true, hint = "مثل 1.1.1.1")
        }
        MainButton("ذخیره", enabled = !on || u1.isNotBlank()) { st.run("ذخیره شد", then = reload) { setKeepAlive(on, u1, u2) } }
    }
}

@Composable
private fun TtlPage(st: AppState, back: () -> Unit) = SubPage("TTL", back, "مقدار TTL بسته‌هایی که از مودم بیرون می‌رود.") {
    Load(st, 0, { ttl() }) { d, reload ->
        var v by remember(d) { mutableStateOf(d.s("ttl_value")) }
        Field("TTL", v, { x -> v = x.filter { it.isDigit() }.take(3) }, number = true, ltr = true, error = (v.toIntOrNull() ?: 0) !in 1..255)
        MainButton("ذخیره", enabled = (v.toIntOrNull() ?: 0) in 1..255) { st.run("ذخیره شد", then = reload) { setTtl(v) } }
    }
}

@Composable
private fun SimSwitchPage(st: AppState, back: () -> Unit) = SubPage("انتخاب سیم‌کارت", back) {
    Load(st, 0, { simSwitch() }) { d, reload ->
        val n = d.s("sim_switch_number").toIntOrNull() ?: 1
        var auto by remember(d) { mutableStateOf(d.s("sim_auto_switch_enable").ifEmpty { "0" }) }
        var card by remember(d) { mutableStateOf(if (d.s("sim_auto_switch_enable") == "0") d.s("sim_current_type") else d.s("sim_default_type")) }
        var running by remember(d) { mutableStateOf(d.s("sim_switch_running_detect").ifEmpty { "0" }) }
        Group { InfoRow("سیم‌کارت فعلی", "سیم ${(d.s("sim_current_type").toIntOrNull() ?: 0) + 1}") }
        Choice("حالت", listOf("0" to "دستی", "1" to "خودکار"), auto) { auto = it }
        Choice(if (auto == "0") "سیم‌کارت" else "سیم‌کارت اول", (0 until n).map { "$it" to "سیم ${it + 1}" }, card) { card = it }
        if (auto == "1") Group { SwitchRow("وقتی سیم فعلی کار نکرد عوض شود", null, running == "1") { running = if (it) "1" else "0" } }
        if (d.s("sim_lock_status") == "lock") Text("سیم‌کارت قفل است.", color = K.warn, fontSize = 13.sp)
        MainButton("ذخیره") { st.run("ذخیره شد", then = reload) { setSimSwitch(auto, card, running) } }
    }
}

@Composable
private fun UpdatePage(st: AppState, back: () -> Unit) {
    var state by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var binMsg by remember { mutableStateOf<String?>(null) }
    var bin by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    val pickBin = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "update.bin"
        val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        binMsg = when {
            !name.lowercase().endsWith(".bin") -> "فقط فایل .bin قبول است"
            bytes == null -> "فایل خوانده نشد"
            bytes.size > 64 * 1024 * 1024 -> "فایل بزرگ‌تر از ۶۴ مگابایت است"
            else -> { bin = name to bytes; null }
        }
    }
    bin?.let { (name, bytes) ->
        Ask("«$name» نصب شود؟", "مودم با این فایل به‌روز و ری‌استارت می‌شود. تا تمام شدن خاموشش نکن.", "نصب کن", danger = true,
            onYes = {
                scope.launch {
                    st.busy = true
                    val r = st.io { uploadFirmware(name, bytes) }.getOrElse { "failure" }
                    st.busy = false
                    binMsg = if (r == "success") "✓ فایل فرستاده شد؛ مودم خودش به‌روز و ری‌استارت می‌شود" else "نصب نشد (${r})"
                }
            },
            onNo = { bin = null })
    }
    SubPage("به‌روزرسانی مودم", back, "نرم‌افزار تازهٔ خود مودم را از سازنده می‌گیرد (اینترنت مودم باید وصل باشد).") {
        Load(st, 0, { deviceInfo() to otaSettings() }) { (d, o), reload ->
            Group {
                InfoRow("نسخهٔ فعلی", d.s("cstm_sw_version").ifEmpty { d.s("cr_version") })
                if (o.s("dm_last_check_time").isNotEmpty()) { Divider(); InfoRow("آخرین بررسی", o.s("dm_last_check_time")) }
            }
            // subpg/ota_update.html: updateMode 1 = on, 0 = off; the interval list is hidden there.
            run {
                var auto by remember(o) { mutableStateOf(o.s("fota_updateMode") == "1") }
                val days = o.s("fota_updateIntervalDay").ifEmpty { "1" }
                var roam by remember(o) { mutableStateOf(o.s("fota_allowRoamingUpdate") == "1") }
                Group(title = "بررسی خودکار") {
                    SwitchRow("مودم خودش نسخهٔ تازه را بررسی کند", null, auto) { auto = it }
                    if (auto) {
                        Divider()
                        SwitchRow("در رومینگ هم بررسی شود", "در رومینگ اینترنت گران‌تر است", roam) { roam = it }
                    }
                }
                GhostButton("ذخیرهٔ بررسی خودکار") { st.run("ذخیره شد", then = reload) { setOtaSettings(if (auto) "1" else "0", days, roam) } }
            }
        }
        val has = state == "has_critical" || state == "has_optional" || state == "already_has_pkg"
        state?.let {
            Group {
                Text(
                    when (it) {
                        "no_new_version" -> "مودم به‌روز است."
                        "check_failed" -> "بررسی نشد. بعداً دوباره امتحان کن."
                        "bad_network" -> "به سرور به‌روزرسانی نرسید."
                        else -> "نسخهٔ تازه هست."
                    },
                    color = if (has) K.good else K.text, modifier = Modifier.padding(16.dp),
                )
            }
        }
        MainButton(if (checking) "در حال بررسی…" else "بررسی نسخهٔ تازه", enabled = !checking && st.connected, icon = Icons.Filled.SystemUpdate) {
            checking = true
            scope.launch { state = st.io { checkUpdate() }.getOrElse { "check_failed" }; checking = false }
        }
        if (!st.connected) Text("اول اینترنت مودم را وصل کن.", color = K.warn, fontSize = 13.sp)
        if (has) GhostButton("دانلود و نصب", color = K.good) { ask = true }
        Group(title = "آپدیت با فایل", footer = "فقط فایل .bin مخصوص همین مودم، حداکثر ۶۴ مگابایت. فایل اشتباه می‌تواند مودم را خراب کند. در حین آپدیت مودم را خاموش نکن.") {
            NavRow(Icons.Filled.Upload, K.warn, "انتخاب فایل و نصب") { pickBin.launch(arrayOf("*/*")) }
        }
        binMsg?.let { Text(it, color = if (it.startsWith("✓")) K.good else K.bad, fontSize = 13.sp) }
        if (ask) Ask("نصب شود؟", "مودم نسخهٔ تازه را دانلود و نصب می‌کند و خودش ری‌استارت می‌شود. باتری باید شارژ داشته باشد و مودم را خاموش نکن.", "نصب کن",
            onYes = { st.run("دانلود شروع شد؛ مودم خودش ری‌استارت می‌شود") { acceptUpdate() } }, onNo = { ask = false })
    }
}

private val LANGS = mapOf("en" to "English", "zh-cn" to "中文", "fre" to "Français", "ar" to "العربية", "fa" to "فارسی")

@Composable
private fun LanguagePage(st: AppState, back: () -> Unit) = SubPage("زبان صفحهٔ مودم", back, "زبان صفحهٔ وب خود مودم (192.168.0.1). این برنامه همیشه فارسی است.") {
    Load(st, 0, { webLanguage() }) { (cur, sup), reload ->
        var lang by remember(cur) { mutableStateOf(cur) }
        val opts = (sup.ifEmpty { LANGS.keys.toList() }).map { it to (LANGS[it] ?: it) }
        Choice("زبان", opts, lang) { lang = it }
        MainButton("ذخیره", enabled = lang != cur) { st.run("ذخیره شد", then = reload) { setWebLanguage(lang) } }
    }
}

@Composable
private fun SimLockPage(st: AppState, back: () -> Unit) = SubPage("باز کردن قفل سیم‌کارت", back, "اگر مودم سیم‌کارت اپراتور دیگر را قفل کرده، کد باز کردن را اینجا بزن.") {
    Load(st, 0, { simLock() }) { d, reload ->
        val locked = d.s("sim_lock_status") == "lock"
        Group {
            InfoRow("وضعیت", if (locked) "قفل" else "باز", if (locked) K.bad else K.good)
            if (d.s("sim_unlock_nck_time").isNotEmpty()) { Divider(); InfoRow("فرصت باقی", d.s("sim_unlock_nck_time")) }
        }
        if (locked) {
            var code by remember { mutableStateOf("") }
            Field("کد باز کردن", code, { code = it.trim().take(32) }, ltr = true)
            MainButton("باز کن", enabled = code.isNotEmpty()) { st.run("قفل باز شد", "کد اشتباه است", then = reload) { unlockSim(code) } }
        }
    }
}

// ---------------- pages turned on by the modem's js/ext/set.js ----------------

@Composable
private fun UrlFilterPage(st: AppState, back: () -> Unit) {
    var ver by remember { mutableIntStateOf(0) }
    var add by remember { mutableStateOf("") }
    SubPage("فیلتر سایت", back, "سایت‌هایی که در این فهرست باشند از هیچ دستگاهی که به مودم وصل است باز نمی‌شوند. حداکثر ۱۰ سایت.") {
        Load(st, ver, { urlFilters() }) { list, _ ->
            if (list.isEmpty()) Empty("فهرست خالی است.")
            else Group(title = "${list.size} سایت بسته") {
                list.forEachIndexed { i, u ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ltr { Text(u, color = K.text, modifier = Modifier.weight(1f)) }
                        IconButton(onClick = { st.run("پاک شد", then = { ver++ }) { deleteUrlFilters(listOf(i)) } }) { Icon(Icons.Filled.Delete, "پاک کردن", tint = K.bad) }
                    }
                }
            }
            if (list.size < 10) {
                Field("آدرس سایت", add, { add = it.trim().take(32) }, ltr = true, hint = "مثل example.com")
                val ok = add.length >= 3 && add.none { it == ';' || it == ' ' } && list.none { it.equals(add, true) }
                MainButton("افزودن", enabled = ok, icon = Icons.Filled.Add) { st.run("اضافه شد", then = { ver++; add = "" }) { addUrlFilter(add) } }
            }
        }
    }
}

@Composable
private fun GuestPage(st: AppState, back: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    SubPage("شبکهٔ مهمان", back, "یک وای‌فای دوم با اسم و رمز جدا برای مهمان‌ها؛ رمز اصلی را لازم ندارند.") {
        Load(st, 0, { guestWifi() }) { w, reload ->
            val on = w.s("m_ssid_enable") == "1"
            Group {
                SwitchRow("شبکهٔ مهمان روشن", null, on, icon = Icons.Filled.Wifi, tint = K.violet) { v ->
                    st.run(if (v) "شبکهٔ مهمان روشن شد" else "شبکهٔ مهمان خاموش شد", then = reload) { setGuestOn(v) }
                }
            }
            if (on) {
                var ssid by remember(w) { mutableStateOf(w.s("m_SSID")) }
                var pass by remember(w) { mutableStateOf(w.s("password")) }
                var mode by remember(w) { mutableStateOf(w.s("m_AuthMode").ifEmpty { "WPA2PSK" }) }
                var hidden by remember(w) { mutableStateOf(w.s("m_HideSSID") == "1") }
                var isolate by remember(w) { mutableStateOf(w.s("m_NoForwarding") == "1") }
                val maxAll = w.s("MAX_Station_num").toIntOrNull()?.coerceIn(1, 32) ?: 8
                var max by remember(w) { mutableStateOf(w.s("m_MAX_Access_num").ifEmpty { "5" }) }
                val ssidOk = ssid.toByteArray().size in 1..32
                val passOk = mode == "OPEN" || pass.length in 8..63
                Field("اسم شبکهٔ مهمان", ssid, { ssid = it }, error = !ssidOk)
                Choice("نوع امنیت", listOf("OPEN" to "بدون رمز", "WPA2PSK" to "WPA2 (پیشنهادی)", "WPAPSKWPA2PSK" to "WPA / WPA2"), mode) { mode = it }
                if (mode != "OPEN") Field("رمز", pass, { pass = it }, password = true, ltr = true, error = !passOk, hint = "۸ تا ۶۳ حرف")
                Choice("حداکثر دستگاه", (1..maxAll).map { "$it" to "$it" }, max) { max = it }
                Group {
                    SwitchRow("مخفی کردن اسم", null, hidden) { hidden = it }
                    Divider()
                    SwitchRow("جدا کردن مهمان‌ها", "دستگاه‌های مهمان همدیگر و شبکهٔ اصلی را نبینند", isolate) { isolate = it }
                }
                MainButton("ذخیره", enabled = ssidOk && passOk) { ask = true }
                if (ask) Ask("ذخیره شود؟", "شبکهٔ مهمان چند ثانیه قطع می‌شود.", "ذخیره کن",
                    onYes = { st.run("ذخیره شد", then = reload) { saveGuest(ssid, pass, mode, hidden, max, isolate, w.s("m_show_qrcode_flag")) } },
                    onNo = { ask = false })
            }
        }
    }
}

@Composable
private fun BandPage(st: AppState, back: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    SubPage("انتخاب باند", back, "مودم فقط روی باندهای تیک‌خورده وصل می‌شود. اگر نمی‌دانی، همه را روشن بگذار.") {
        NeedsOffline(st)
        Load(st, 0, { bands() }) { (all, chosen), reload ->
            if (all.isEmpty()) { Empty("مودم فهرست باند نداد."); return@Load }
            val on = remember(chosen) { mutableStateListOf<String>().apply { addAll(chosen) } }
            Group(title = "${all.size} باند") {
                all.forEachIndexed { i, b ->
                    if (i > 0) Divider()
                    SwitchRow(b, null, b in on) { v -> if (v) on += b else on -= b }
                }
            }
            GhostButton("همه") { on.clear(); on.addAll(all) }
            MainButton("ذخیره", enabled = on.isNotEmpty()) { ask = true }
            if (ask) Ask("ذخیره شود؟", "اتصال مودم چند لحظه قطع و دوباره وصل می‌شود.", "ذخیره کن",
                onYes = { st.run("ذخیره شد", then = reload) { setBands(all.filter { it in on }) } }, onNo = { ask = false })
        }
    }
}

@Composable
private fun VpnPage(st: AppState, back: () -> Unit) = SubPage("VPN مودم", back, "خود مودم به یک سرور L2TP یا PPTP وصل می‌شود و همهٔ دستگاه‌ها از آن رد می‌شوند.") {
    Load(st, 0, { vpn() }) { d, reload ->
        val open = d.s("vpn_state") == "open"
        val cs = d.s("connect_status")
        Group {
            InfoRow(
                "وضعیت",
                when { !open -> "خاموش"; cs == "connected" -> "وصل"; cs == "setting" -> "در حال وصل شدن…"; else -> "وصل نشد" },
                when { !open -> K.dim; cs == "connected" -> K.good; cs == "setting" -> K.warn; else -> K.bad },
            )
        }
        var type by remember(d) { mutableStateOf(d.s("vpn_type").ifEmpty { "l2tp" }) }
        var server by remember(d) { mutableStateOf(d.s("vpn_server_ip")) }
        var user by remember(d) { mutableStateOf(d.s("vpn_name")) }
        var pass by remember(d) { mutableStateOf(d.s("vpn_password")) }
        val busy = cs == "connected" || cs == "setting"
        Choice("نوع", listOf("l2tp" to "L2TP", "pptp" to "PPTP"), type, enabled = !busy) { type = it }
        Field("آدرس سرور", server, { server = it.trim() }, ltr = true, enabled = !busy)
        Field("نام کاربری", user, { user = it.trim() }, ltr = true, enabled = !busy)
        Field("رمز", pass, { pass = it }, ltr = true, password = true, enabled = !busy)
        if (busy) MainButton("قطع VPN", color = K.bad, icon = null) { st.run("VPN قطع شد", then = reload) { closeVpn() } }
        else MainButton("وصل کن", enabled = server.isNotEmpty() && user.isNotEmpty()) { st.run("دستور وصل شدن رفت", then = reload) { openVpn(user, pass, server, type) } }
        GhostButton("تازه کردن وضعیت", Icons.Filled.Refresh) { reload() }
    }
}

@Composable
private fun DdnsPage(st: AppState, back: () -> Unit) = SubPage("DDNS", back, "یک اسم ثابت (مثل myhome.ddns.net) که همیشه به IP اینترنت مودم اشاره کند.") {
    Load(st, 0, { ddns() }) { d, reload ->
        var on by remember(d) { mutableStateOf(d.s("DDNS_Enable") == "1") }
        var mode by remember(d) { mutableStateOf(d.s("DDNS_Mode").ifEmpty { "auto" }) }
        var prov by remember(d) { mutableStateOf(d.s("DDNSProvider").ifEmpty { "dyndns.org" }) }
        var host by remember(d) { mutableStateOf(d.s("DDNS")) }
        var user by remember(d) { mutableStateOf(d.s("DDNSAccount")) }
        var pass by remember(d) { mutableStateOf(d.s("DDNSPassword")) }
        var hash by remember(d) { mutableStateOf(d.s("DDNS_Hash_Value")) }
        val provs = st.options.optJSONArray("DDNSDDP")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("value") to a.getJSONObject(it).optString("name").replace("None", "هیچ") } }
            ?: listOf("dyndns.org" to "dyndns.org", "no-ip.com" to "no-ip.com", "none" to "هیچ")
        Group { SwitchRow("DDNS روشن", null, on) { on = it } }
        if (on) {
            Choice("سرویس", provs, prov) { prov = it }
            Choice("به‌روزرسانی", listOf("auto" to "خودکار", "manual" to "دستی"), mode) { mode = it }
            if (prov != "none") {
                Field("اسم (دامنه)", host, { host = it.trim() }, ltr = true)
                Field("نام کاربری", user, { user = it.trim() }, ltr = true)
                Field("رمز", pass, { pass = it }, ltr = true, password = true)
            }
            if (prov == "freedns.afraid.org") Field("Hash", hash, { hash = it.trim() }, ltr = true)
        }
        MainButton("ذخیره", enabled = !on || prov == "none" || (host.isNotEmpty() && user.isNotEmpty())) {
            st.run("ذخیره شد", then = reload) { setDdns(on, mode, prov, host, user, pass, hash) }
        }
    }
}

@Composable
private fun Tr069Page(st: AppState, back: () -> Unit) = SubPage("TR-069", back, "مدیریت مودم از راه دور توسط اپراتور (ACS). اگر اپراتور چیزی نگفته، خاموش بماند.") {
    Load(st, 0, { tr069() }) { d, reload ->
        var on by remember(d) { mutableStateOf(d.s("tr069_enable") == "1") }
        var url by remember(d) { mutableStateOf(d.s("tr069_acs_url")) }
        var au by remember(d) { mutableStateOf(d.s("tr069_acs_username")) }
        var ap by remember(d) { mutableStateOf(d.s("tr069_acs_password")) }
        var inform by remember(d) { mutableStateOf(d.s("tr069_inform_enable") == "1") }
        var every by remember(d) { mutableStateOf(d.s("tr069_inform_interval")) }
        var auth by remember(d) { mutableStateOf(d.s("tr069_cpe_auth_enable") == "1") }
        var cu by remember(d) { mutableStateOf(d.s("tr069_cpe_username")) }
        var cp by remember(d) { mutableStateOf(d.s("tr069_cpe_password")) }
        Group { SwitchRow("TR-069 روشن", null, on) { on = it } }
        if (on) {
            Field("آدرس ACS", url, { url = it.trim() }, ltr = true)
            Field("نام کاربری ACS", au, { au = it.trim() }, ltr = true)
            Field("رمز ACS", ap, { ap = it }, ltr = true, password = true)
            Group { SwitchRow("گزارش دوره‌ای", null, inform) { inform = it } }
            if (inform) Field("هر چند ثانیه", every, { v -> every = v.filter { it.isDigit() }.take(7) }, ltr = true, number = true)
            Group { SwitchRow("درخواست وصل شدن با رمز", null, auth) { auth = it } }
            if (auth) {
                Field("نام کاربری مودم", cu, { cu = it.trim() }, ltr = true)
                Field("رمز مودم", cp, { cp = it }, ltr = true, password = true)
            }
        }
        MainButton("ذخیره") { st.run("ذخیره شد", then = reload) { setTr069(on, url, au, ap, inform, every, auth, cu, cp) } }
    }
}

@Composable
private fun AtPage(st: AppState, back: () -> Unit) {
    var cmd by remember { mutableStateOf("") }
    val log = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    SubPage("دستور AT", back, "دستور مستقیم به تراشهٔ 4G، برای کاربر حرفه‌ای. مثلاً AT+CSQ (قدرت آنتن) یا AT+COPS? (اپراتور). دستور اشتباه می‌تواند مودم را از کار بیندازد.") {
        Field("دستور", cmd, { cmd = it.take(100) }, ltr = true, hint = "باید با AT شروع شود")
        MainButton("بفرست", enabled = cmd.trim().uppercase().startsWith("AT")) {
            val c = cmd
            scope.launch { val r = st.io { atCommand(c) }; log.add(0, "> $c\n" + r.getOrElse { st.netError(it) }) }
            cmd = ""
        }
        if (log.isNotEmpty()) Group {
            Ltr { Text(log.joinToString("\n\n"), color = K.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(14.dp)) }
        }
    }
}

@Composable
private fun UsbMacPage(st: AppState, back: () -> Unit) = SubPage("MAC کارت USB", back, "شناسهٔ کارت شبکه‌ای که مودم وقتی با کابل USB به کامپیوتر وصل است نشان می‌دهد. بعد از ذخیره، مودم ری‌استارت می‌شود.") {
    Load(st, 0, { usbMac() }) { cur, _ ->
        val shown = cur.takeIf { it != "FFFFFFFFFFFF" && it != "00000000000" && it.isNotEmpty() } ?: "00A0C6000000"
        var mac by remember(cur) { mutableStateOf(shown) }
        Field("MAC (۱۲ حرف، بدون :)", mac, { v -> mac = v.filter { it.isLetterOrDigit() }.uppercase().take(12) }, ltr = true,
            error = !Regex("^[0-9A-F]{12}$").matches(mac))
        MainButton("ذخیره و ری‌استارت", enabled = Regex("^[0-9A-F]{12}$").matches(mac) && mac != shown) {
            st.run("ذخیره شد؛ مودم ری‌استارت می‌شود") { setUsbMac(mac) }
        }
    }
}

// ---------------- AP Station ----------------

private val STA_AUTH = listOf("OPEN" to "باز (بدون رمز یا WEP)", "SHARED" to "WEP اشتراکی", "WPAPSK" to "WPA", "WPA2PSK" to "WPA2", "WPAPSKWPA2PSK" to "WPA / WPA2")
private val STA_CIPHER = listOf("TKIP" to "TKIP", "CCMP" to "AES (CCMP)", "TKIPCCMP" to "TKIP + AES")

@Composable
private fun ApStationPage(st: AppState, back: () -> Unit) {
    var ver by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<Hotspot?>(null) }
    var del by remember { mutableStateOf<Hotspot?>(null) }
    var found by remember { mutableStateOf<List<FoundWifi>?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SubPage("اینترنت از وای‌فای دیگر", back, "مودم به جای سیم‌کارت به یک وای‌فای دیگر (مثل وای‌فای خانه یا هتل) وصل می‌شود و همان را به دستگاه‌ها می‌دهد.") {
        Load(st, ver, { apStation() to hotspots() }) { (d, list), _ ->
            val on = d.s("wifi_sta_connection") == "1"
            Group {
                SwitchRow("این قابلیت روشن", if (d.s("m_ssid_enable") == "1") "با روشن شدنش، شبکهٔ مهمان خاموش می‌شود" else null, on, icon = Icons.Filled.Wifi, tint = K.cyan) { v ->
                    st.run("ذخیره شد", then = { ver++ }) { setApStation(v) }
                }
            }
            if (!on) return@Load
            val cur = d.s("EX_wifi_profile")
            val stat = d.s("sta_ip_status")
            Group {
                InfoRow(
                    "اتصال", when (stat) { "connect" -> "وصل به ${d.s("EX_SSID1")}"; "connecting", "dhcping" -> "در حال وصل شدن…"; else -> "وصل نیست" },
                    when (stat) { "connect" -> K.good; "connecting", "dhcping" -> K.warn; else -> K.dim },
                )
                InfoRow("اولویت", if ((d.s("wifiwan_priority").toIntOrNull() ?: 0) > (d.s("pswan_priority").toIntOrNull() ?: 0)) "اول وای‌فای، بعد سیم‌کارت" else "اول سیم‌کارت، بعد وای‌فای")
            }
            if (list.isEmpty()) Empty("هنوز وای‌فایی ذخیره نشده. جستجو کن یا با + اضافه کن.")
            else Group(title = "وای‌فای‌های ذخیره‌شده (حداکثر ۱۰)") {
                list.forEachIndexed { i, h ->
                    if (i > 0) Divider()
                    val isCur = h.profileName == cur && stat == "connect"
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.Wifi, if (isCur) K.good else K.cyan)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.ssid, color = K.text, fontWeight = FontWeight.Bold)
                            Text((STA_AUTH.firstOrNull { it.first == h.authMode }?.second ?: h.authMode) + if (isCur) "، وصل" else "", color = K.dim, fontSize = 12.sp)
                        }
                        if (isCur) TextButton(onClick = { st.run("قطع شد", then = { ver++ }) { disconnectHotspot() } }) { Text("قطع", color = K.bad) }
                        else TextButton(onClick = { st.run("دستور وصل شدن رفت", then = { ver++ }) { connectHotspot(h) } }) { Text("وصل", color = K.ice) }
                        IconButton(onClick = { edit = h }) { Icon(Icons.Filled.Edit, "ویرایش", tint = K.ice) }
                        if (h.fromProvider != "1") IconButton(onClick = { del = h }) { Icon(Icons.Filled.Delete, "پاک کردن", tint = K.bad) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    GhostButton(if (scanning) "جستجو…" else "جستجو", Icons.Filled.Search, enabled = !scanning) {
                        scanning = true
                        scope.launch { found = st.io { scanWifi() }.getOrNull() ?: emptyList(); scanning = false }
                    }
                }
                if (list.size < 10) Column(Modifier.weight(1f)) { GhostButton("افزودن دستی", Icons.Filled.Add) { edit = Hotspot("", ssid = "", authMode = "WPA2PSK", encryptType = "TKIPCCMP", password = "") } }
            }
            found?.let { f ->
                if (f.isEmpty()) Empty("وای‌فایی پیدا نشد.")
                else Group(title = "پیدا شده") {
                    f.forEachIndexed { i, w ->
                        if (i > 0) Divider()
                        NavRow(Icons.Filled.Wifi, K.blue, w.ssid.ifEmpty { "(بی‌اسم)" }, "آنتن ${w.signal}") {
                            edit = Hotspot("", ssid = w.ssid, authMode = w.authMode, encryptType = w.encryptType.ifEmpty { "TKIPCCMP" }, password = "", signal = w.signal, mac = w.mac)
                        }
                    }
                }
            }
        }
    }
    edit?.let { h ->
        var ssid by remember(h) { mutableStateOf(h.ssid) }
        var auth by remember(h) { mutableStateOf(h.authMode.ifEmpty { "WPA2PSK" }) }
        var pass by remember(h) { mutableStateOf(h.password) }
        var cipher by remember(h) { mutableStateOf(h.encryptType.takeIf { it in STA_CIPHER.map { c -> c.first } } ?: "TKIPCCMP") }
        var wep by remember(h) { mutableStateOf(h.encryptType == "WEP") }
        var key by remember(h) { mutableStateOf(h.keyID.ifEmpty { "0" }) }
        val wpa = auth == "WPAPSK" || auth == "WPA2PSK" || auth == "WPAPSKWPA2PSK"
        val usesWep = auth == "SHARED" || (auth == "OPEN" && wep)
        // WEP keys are 5 or 13 letters (or 10 / 26 hex); WPA is 8 to 63.
        val passOk = when { wpa -> pass.length in 8..63; usesWep -> pass.length in listOf(5, 10, 13, 26); else -> true }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (h.profileName.isEmpty()) "وای‌فای تازه" else "ویرایش ${h.ssid}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("اسم وای‌فای", ssid, { ssid = it.take(32) })
                    Choice("امنیت", STA_AUTH.let { l -> if (l.none { it.first == auth }) l + (auth to auth) else l }, auth) { auth = it }
                    if (auth == "OPEN") Choice("رمزنگاری", listOf("NONE" to "هیچ", "WEP" to "WEP"), if (wep) "WEP" else "NONE") { wep = it == "WEP" }
                    if (wpa) Choice("رمزنگاری", STA_CIPHER, cipher) { cipher = it }
                    if (usesWep) Choice("کلید WEP", (0..3).map { "$it" to "کلید ${it + 1}" }, key) { key = it }
                    if (wpa || usesWep) Field("رمز", pass, { pass = it.take(63) }, password = true, ltr = true, error = pass.isNotEmpty() && !passOk,
                        hint = if (usesWep) "۵ یا ۱۳ حرف" else "۸ تا ۶۳ حرف")
                }
            },
            confirmButton = {
                TextButton(enabled = ssid.isNotEmpty() && passOk, onClick = {
                    edit = null
                    val enc = when { wpa -> cipher; usesWep -> "WEP"; else -> "NONE" }
                    val n = h.copy(ssid = ssid, authMode = auth, password = if (wpa || usesWep) pass else "", encryptType = enc, keyID = if (usesWep) key else "0",
                        mac = if (ssid != h.ssid) "0F:00:00:00:00:00" else h.mac)
                    st.run("ذخیره شد", then = { ver++ }) { saveHotspot(n, hotspots()) }
                }) { Text("ذخیره", color = K.ice) }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("لغو", color = K.dim) } },
            containerColor = K.raised,
        )
    }
    del?.let { h -> Ask("«${h.ssid}» پاک شود؟", "از فهرست وای‌فای‌های ذخیره‌شده پاک می‌شود.", "پاک کن", danger = true, onYes = { st.run("پاک شد", then = { ver++ }) { deleteHotspot(h, hotspots()) } }, onNo = { del = null }) }
}

// ---------------- parental control ----------------

private val WEEK = listOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")

@Composable
private fun ParentalPage(st: AppState, back: () -> Unit) {
    var ver by remember { mutableIntStateOf(0) }
    var addSite by remember { mutableStateOf(false) }
    SubPage("کنترل والدین", back, "برای دستگاه بچه‌ها: در ساعت‌های بسته اینترنت ندارند و فقط سایت‌های فهرست سفید باز می‌شوند.") {
        Load(st, ver, { Triple(childDevices(), devices(), timeLimits()) to whiteSites() }) { (t, sites), _ ->
            val (kids, all, times) = t
            val me = st.status.s("client_mac_address")
            Group(title = "دستگاه بچه‌ها") {
                if (kids.isEmpty()) Text("هنوز دستگاهی نیست.", color = K.dim, modifier = Modifier.padding(16.dp))
                kids.forEachIndexed { i, (mac, name) ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.Person, K.warn)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name.ifEmpty { "دستگاه" }, color = K.text, fontWeight = FontWeight.Bold)
                            Ltr { Text(mac.uppercase(), color = K.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                        }
                        TextButton(onClick = { st.run("برداشته شد", then = { ver++ }) { removeChild(mac) } }) { Text("بردار", color = K.bad) }
                    }
                }
            }
            val others = all.filter { d -> kids.none { it.first.equals(d.mac, true) } }
            if (others.isNotEmpty()) Group(title = "افزودن از دستگاه‌های وصل") {
                others.forEachIndexed { i, d ->
                    if (i > 0) Divider()
                    NavRow(Icons.Filled.Add, K.good, d.name.ifEmpty { d.mac }, d.ip) {
                        st.run(if (d.mac.equals(me, true)) "اضافه شد؛ این گوشی هم محدود شد" else "اضافه شد", then = { ver++ }) { addChild(d.mac) }
                    }
                }
            }
            val grid = remember(times) { mutableStateMapOf<Int, Set<Int>>().apply { putAll(times) } }
            Group(title = "ساعت‌های بسته", footer = "خانه‌های قرمز یعنی آن ساعت اینترنت بسته است؛ بقیه آزاد. روی هر خانه بزن تا عوض شود؛ روی اسم روز بزن تا کل روز عوض شود.") {
                Column(Modifier.padding(10.dp)) {
                    Ltr {
                        Row(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.width(54.dp))
                            listOf(0, 6, 12, 18).forEach { h -> Text("$h", color = K.dim, fontSize = 10.sp, modifier = Modifier.weight(6f)) }
                        }
                    }
                    (0..6).forEach { day ->
                        Ltr {
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(WEEK[day], color = K.text, fontSize = 11.sp, maxLines = 1,
                                    modifier = Modifier.width(54.dp).clickable { grid[day] = if ((grid[day]?.size ?: 0) == 24) emptySet() else (0..23).toSet() })
                                (0..23).forEach { h ->
                                    val on = grid[day]?.contains(h) == true
                                    Box(
                                        Modifier.weight(1f).height(22.dp).padding(1.dp).clip(RoundedCornerShape(3.dp))
                                            .background(if (on) K.bad else K.line)
                                            .clickable { grid[day] = (grid[day] ?: emptySet()).let { if (on) it - h else it + h } },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            MainButton("ذخیرهٔ ساعت‌های بسته") { st.run("ذخیره شد", then = { ver++ }) { saveTimeLimits(grid.toMap()) } }
            Group(title = "سایت‌های مجاز (فهرست سفید)") {
                if (sites.isEmpty()) Text("فهرست خالی است.", color = K.dim, modifier = Modifier.padding(16.dp))
                sites.forEachIndexed { i, w ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.name, color = K.text)
                            Ltr { Text(w.site, color = K.dim, fontSize = 12.sp) }
                        }
                        IconButton(onClick = { st.run("پاک شد", then = { ver++ }) { removeWhiteSites(listOf(w.id)) } }) { Icon(Icons.Filled.Delete, "پاک کردن", tint = K.bad) }
                    }
                }
            }
            GhostButton("افزودن سایت", Icons.Filled.Add) { addSite = true }
        }
    }
    if (addSite) {
        var name by remember { mutableStateOf("") }
        var site by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addSite = false },
            title = { Text("سایت مجاز") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("اسم", name, { name = it.take(32) })
                    Field("آدرس", site, { site = it.trim().take(64) }, ltr = true, hint = "مثل www.example.com")
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && site.length >= 3, onClick = {
                    addSite = false
                    st.run("اضافه شد", then = { ver++ }) { addWhiteSite(name.trim(), site) }
                }) { Text("افزودن", color = K.ice) }
            },
            dismissButton = { TextButton(onClick = { addSite = false }) { Text("لغو", color = K.dim) } },
            containerColor = K.raised,
        )
    }
}

// ---------------- memory card ----------------

@Composable
private fun SdPage(st: AppState, back: () -> Unit) {
    var ver by remember { mutableIntStateOf(0) }
    var path by remember { mutableStateOf(SD_BASE) }
    var page by remember { mutableIntStateOf(1) }
    var newFolder by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<SdFile?>(null) }
    var del by remember { mutableStateOf<SdFile?>(null) }
    var saving by remember { mutableStateOf<SdFile?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "file"
            val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) st.message = "فایل خوانده نشد"
            else scope.launch {
                st.busy = true
                val r = st.io { sdUpload(path, name, bytes) }
                st.busy = false
                st.message = r.fold({ when (it) { null -> "فرستاده شد"; "exist" -> "فایلی با همین اسم هست؛ اول اسمش را عوض کن"; "no_sdcard" -> "کارت حافظه نیست"; else -> "فرستاده نشد" } }, { st.netError(it) })
                if (r.getOrNull() == null && r.isSuccess) ver++
            }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val f = saving ?: return@rememberLauncherForActivityResult
        saving = null
        if (uri != null) scope.launch {
            st.busy = true
            val r = st.io { (net as Uploader).download(host, sdUrlPath(path, f.name)) }
            st.busy = false
            r.onSuccess { b -> runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(b) } }; st.message = "ذخیره شد" }
                .onFailure { st.message = st.netError(it) }
        }
    }
    SubPage("کارت حافظه", back, "کارت حافظهٔ داخل مودم: فایل‌ها را ببین، بفرست یا بگیر، و اشتراک‌گذاری را تنظیم کن.") {
        Load(st, ver, { sdConfig() to sdSpace() }) { (c, space), reload ->
            val state = c.s("sd_card_state")
            if (state != "1" || space == null) { Empty("کارت حافظه در مودم نیست یا آماده نیست."); return@Load }
            Room(((space.first - space.second) / 1048576).toInt(), (space.first / 1048576).toInt(), "فضای کارت (مگابایت)")
            val httpMode = c.s("sdcard_mode_option") == "1"
            Segments(listOf("اشتراک در شبکه", "حافظهٔ USB"), if (httpMode) 0 else 1) { i ->
                st.run("ذخیره شد", then = reload) { setSdMode(i == 0) }
            }
            if (!httpMode) { Text("در حالت USB، کارت فقط وقتی مودم با کابل به کامپیوتر وصل است دیده می‌شود.", color = K.dim, fontSize = 13.sp); return@Load }
            var share by remember(c) { mutableStateOf(c.s("HTTP_SHARE_STATUS") == "Enabled") }
            var rw by remember(c) { mutableStateOf(c.s("HTTP_SHARE_WR_AUTH") != "readOnly") }
            Group(title = "اشتراک برای مهمان‌ها") {
                SwitchRow("دیگران هم ببینند", "دستگاه‌های وصل بدون رمز مدیریت فایل‌ها را ببینند", share) { share = it }
                if (share) { Divider(); SwitchRow("بتوانند فایل بفرستند و پاک کنند", null, rw) { rw = it } }
            }
            GhostButton("ذخیرهٔ اشتراک") { st.run("ذخیره شد", then = reload) { setSdSharing(share, rw, c.s("HTTP_SHARE_FILE").ifEmpty { SD_BASE }) } }
        }
        Load(st, Triple(ver, path, page), { sdList(path, page) }) { res, _ ->
            if (res == null) return@Load
            val (total, files) = res
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ltr { Text(path.removePrefix(SD_BASE).ifEmpty { "/" }, color = K.ice, fontSize = 13.sp, modifier = Modifier.weight(1f)) }
                if (path != SD_BASE) TextButton(onClick = { path = path.substringBeforeLast('/'); page = 1 }) { Text("بالاتر", color = K.ice) }
            }
            if (files.isEmpty()) Empty("این پوشه خالی است.")
            else Group(title = "$total مورد") {
                files.forEachIndexed { i, f ->
                    if (i > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = f.folder) { path = "$path/${f.name}"; page = 1 }.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(if (f.folder) Icons.Filled.Folder else Icons.Filled.Description, if (f.folder) K.warn else K.cyan)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, color = K.text, maxLines = 1)
                            Text(listOfNotNull(if (f.folder) null else Codec.iso(Codec.bytes(f.size)), f.time.ifEmpty { null }).joinToString("، "), color = K.dim, fontSize = 12.sp)
                        }
                        if (!f.folder) IconButton(onClick = { saving = f; save.launch(f.name) }) { Icon(Icons.Filled.Download, "گرفتن", tint = K.good) }
                        IconButton(onClick = { rename = f }) { Icon(Icons.Filled.Edit, "تغییر اسم", tint = K.ice) }
                        IconButton(onClick = { del = f }) { Icon(Icons.Filled.Delete, "پاک کردن", tint = K.bad) }
                    }
                }
            }
            val pages = (total + 9) / 10
            if (pages > 1) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = page > 1, onClick = { page-- }) { Text("قبلی", color = K.ice) }
                Text("$page از $pages", color = K.dim)
                TextButton(enabled = page < pages, onClick = { page++ }) { Text("بعدی", color = K.ice) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) { GhostButton("پوشهٔ تازه", Icons.Filled.CreateNewFolder) { newFolder = true } }
                Column(Modifier.weight(1f)) { GhostButton("فرستادن فایل", Icons.Filled.Upload) { pick.launch(arrayOf("*/*")) } }
            }
        }
    }
    if (newFolder) NameDialog("پوشهٔ تازه", "") { n -> newFolder = false; if (n != null) st.run("ساخته شد", "ساخته نشد", then = { ver++ }) { sdNewFolder("$path/$n") } }
    rename?.let { f -> NameDialog("اسم تازه", f.name) { n -> rename = null; if (n != null && n != f.name) st.run("اسم عوض شد", "عوض نشد", then = { ver++ }) { sdRename(path, f.name, n) } } }
    del?.let { f -> Ask("«${f.name}» پاک شود؟", if (f.folder) "پوشه و هر چه داخلش است پاک می‌شود." else "فایل برای همیشه پاک می‌شود.", "پاک کن", danger = true,
        onYes = { st.run("پاک شد", "پاک نشد", then = { ver++ }) { sdDelete(path, listOf(f.name)) } }, onNo = { del = null }) }
}

/** Asks for a file or folder name (the modem refuses * and a few other characters). */
@Composable
private fun NameDialog(title: String, start: String, done: (String?) -> Unit) {
    var n by remember { mutableStateOf(start) }
    val bad = n.isBlank() || n.any { it in "*/\\:?\"<>|" }
    AlertDialog(
        onDismissRequest = { done(null) },
        title = { Text(title) },
        text = { Field("اسم", n, { n = it.take(64) }, error = n.isNotEmpty() && bad) },
        confirmButton = { TextButton(enabled = !bad, onClick = { done(n.trim()) }) { Text("ذخیره", color = K.ice) } },
        dismissButton = { TextButton(onClick = { done(null) }) { Text("لغو", color = K.dim) } },
        containerColor = K.raised,
    )
}

// ---------------- quick setup ----------------

@Composable
private fun QuickSetupPage(st: AppState, back: () -> Unit) = SubPage("راه‌اندازی سریع", back, "تنظیم اینترنت (APN) و وای‌فای در یک جا، مثل راه‌اندازی سریع صفحهٔ مودم.") {
    NeedsOffline(st)
    Load(st, 0, { quickInfo() }) { d, reload ->
        val idx = d.s("apn_index").ifEmpty { "0" }
        val cfg = d.s("APN_configtmp$idx").split("(\$)")
        var mode by remember(d) { mutableStateOf(d.s("apn_mode").ifEmpty { "auto" }) }
        var name by remember(d) { mutableStateOf(cfg.getOrElse(0) { "" }) }
        var apn by remember(d) { mutableStateOf(cfg.getOrElse(1) { "" }) }
        var auth by remember(d) { mutableStateOf(cfg.getOrElse(4) { "none" }.lowercase().ifEmpty { "none" }) }
        var user by remember(d) { mutableStateOf(cfg.getOrElse(5) { "" }) }
        var apass by remember(d) { mutableStateOf(cfg.getOrElse(6) { "" }) }
        var ssid by remember(d) { mutableStateOf(d.s("SSID1")) }
        var hidden by remember(d) { mutableStateOf(d.s("HideSSID") == "1") }
        var wauth by remember(d) { mutableStateOf(d.s("AuthMode").ifEmpty { "WPA2PSK" }) }
        var wpass by remember(d) { mutableStateOf(runCatching { String(java.util.Base64.getDecoder().decode(d.s("WPAPSK1_encode"))) }.getOrDefault("")) }
        val off = !st.connected
        Group(title = "۱. اینترنت") {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segments(listOf("خودکار", "دستی"), if (mode == "manual") 1 else 0) { mode = if (it == 1) "manual" else "auto" }
                if (mode == "manual") {
                    Field("اسم پروفایل", name, { name = it })
                    Field("APN", apn, { apn = it.trim() }, ltr = true)
                    Choice("احراز هویت", listOf("none" to "هیچ", "chap" to "CHAP", "pap" to "PAP", "pap_chap" to "PAP یا CHAP"), auth) { auth = it }
                    if (auth != "none") { Field("نام کاربری", user, { user = it }, ltr = true); Field("رمز", apass, { apass = it }, ltr = true, password = true) }
                }
            }
        }
        Group(title = "۲. وای‌فای") {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("اسم وای‌فای", ssid, { ssid = it })
                Choice("امنیت", listOf("OPEN" to "بدون رمز", "WPA2PSK" to "WPA2", "WPAPSKWPA2PSK" to "WPA / WPA2"), wauth) { wauth = it }
                if (wauth != "OPEN") Field("رمز وای‌فای", wpass, { wpass = it }, ltr = true, password = true, hint = "۸ تا ۶۳ حرف")
                SwitchRow("مخفی کردن اسم", null, hidden) { hidden = it }
            }
        }
        val ok = ssid.toByteArray().size in 1..32 && (wauth == "OPEN" || wpass.length in 8..63) && (mode == "auto" || (name.isNotBlank() && apn.isNotBlank()))
        MainButton("ذخیرهٔ همه", enabled = ok && off) {
            st.run("ذخیره شد؛ وای‌فای چند ثانیه قطع می‌شود", then = reload) {
                quickSetup(idx, mode, Apn(name, apn, auth, user, apass, d.s("pdp_type").ifEmpty { "IP" }), ssid, hidden, wauth, wpass)
            }
        }
    }
}
