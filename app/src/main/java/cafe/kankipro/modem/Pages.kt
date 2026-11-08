package cafe.kankipro.modem

import androidx.compose.foundation.layout.Arrangement
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
            var mode by remember(d) { mutableStateOf(d.s("WirelessMode").ifEmpty { "4" }) }
            var country by remember(d) { mutableStateOf(d.s("CountryCode")) }
            var channel by remember(d) { mutableStateOf(d.s("Channel").ifEmpty { "0" }) }
            var rate by remember(d) { mutableStateOf(d.s("HT_MCS").ifEmpty { "0" }) }
            val countries = st.options.optJSONObject("countries")
            val cList = buildList { countries?.keys()?.forEach { k -> if (k != "NONE") add(k to "${countries.optString(k)} ($k)") } }.sortedBy { it.second }
            val ch = channels(st, country)
            val rs = rates(mode)
            Choice("حالت", listOf("4" to "802.11 b/g/n", "2" to "فقط 802.11 n"), mode) { mode = it; rate = "0" }
            Choice("کشور", cList, country) { country = it; channel = "0" }
            Choice("کانال", ch, if (ch.any { it.first == channel }) channel else "0") { channel = it }
            Choice("سرعت", rs, if (rs.any { it.first == rate }) rate else "0") { rate = it }
            if (d.s("WscModeOption") == "1") Text("WPS روشن است؛ مودم تا تمام شدن WPS این بخش را عوض نمی‌کند.", color = K.warn, fontSize = 13.sp)
            MainButton("ذخیره", enabled = d.s("WscModeOption") != "1") { ask = true }
            if (ask) Ask("ذخیره شود؟", "وای‌فای چند ثانیه قطع و دوباره وصل می‌شود.", "ذخیره کن",
                onYes = {
                    st.run("ذخیره شد", then = reload) {
                        setWifiAdvance(mode, country, channel, rateText(rate), d.s("MAX_Access_num"), d.s("m_MAX_Access_num").ifEmpty { "0" })
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
        // Range: only the three values ZTE pages use; anything else is left alone.
        val ranges = listOf("short_mode" to "کم (کم‌مصرف)", "medium_mode" to "متوسط", "long_mode" to "زیاد (دورتر)")
        if (ranges.any { it.first == d.s("wifi_coverage") }) {
            var range by remember(d) { mutableStateOf(d.s("wifi_coverage")) }
            Choice("برد وای‌فای", ranges, range) { range = it }
            MainButton("ذخیره برد") { st.run("ذخیره شد", then = reload) { setRange(range) } }
        }
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
                { it.at(0) }, { listOf("دانلود ${it.at(1)}", "آپلود ${it.at(2)}", it.at(3)).filter { s -> s.isNotBlank() }.joinToString("، ") },
                { r -> st.run("پاک شد", then = { ver++ }) { deleteRateLimit(listOf(r.index)) } }) {
                Field("IP دستگاه", ip, { ip = it.trim() }, ltr = true, error = ip.isNotEmpty() && !IP.matches(ip), hint = "IP هر دستگاه در صفحهٔ «دستگاه‌ها» هست")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("سرعت دانلود", down, { v -> down = v.filter { it.isDigit() }.take(7) }, number = true, ltr = true, modifier = Modifier.weight(1f))
                    Field("سرعت آپلود", up, { v -> up = v.filter { it.isDigit() }.take(7) }, number = true, ltr = true, modifier = Modifier.weight(1f))
                }
                Text("واحد سرعت همان واحد صفحهٔ خود مودم است.", color = K.dim, fontSize = 12.sp)
                Field("توضیح (اختیاری)", note, { note = it.filter { c -> c != ',' && c != ';' }.take(32) })
                MainButton("افزودن", enabled = IP.matches(ip) && down.isNotEmpty() && up.isNotEmpty(), icon = Icons.Filled.Add) {
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
    SubPage("به‌روزرسانی مودم", back, "نرم‌افزار تازهٔ خود مودم را از سازنده می‌گیرد (اینترنت مودم باید وصل باشد).") {
        Load(st, 0, { deviceInfo() to otaSettings() }) { (d, o), reload ->
            Group {
                InfoRow("نسخهٔ فعلی", d.s("cstm_sw_version").ifEmpty { d.s("cr_version") })
                if (o.s("dm_last_check_time").isNotEmpty()) { Divider(); InfoRow("آخرین بررسی", o.s("dm_last_check_time")) }
            }
            // Only the two values ZTE pages use (0 = check by hand, 1 = check by itself); anything else is left alone.
            if (o.s("fota_updateMode") == "0" || o.s("fota_updateMode") == "1") {
                var auto by remember(o) { mutableStateOf(o.s("fota_updateMode") == "1") }
                var days by remember(o) { mutableStateOf(o.s("fota_updateIntervalDay").ifEmpty { "7" }) }
                var roam by remember(o) { mutableStateOf(o.s("fota_allowRoamingUpdate") == "1") }
                Group(title = "بررسی خودکار") {
                    SwitchRow("مودم خودش نسخهٔ تازه را بررسی کند", null, auto) { auto = it }
                    if (auto) {
                        Divider()
                        SwitchRow("در رومینگ هم بررسی شود", "در رومینگ اینترنت گران‌تر است", roam) { roam = it }
                    }
                }
                if (auto) Choice("هر چند روز", listOf("1", "7", "15", "30").map { it to "$it روز" }.let { l -> if (l.none { it.first == days }) l + (days to "$days روز") else l }, days) { days = it }
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
