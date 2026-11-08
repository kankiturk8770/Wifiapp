package cafe.kankipro.modem

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Look: true black for AMOLED, one cobalt blue for everything you can press,
 * green / amber / red only for state (signal, battery, errors).
 */
object K {
    val bg = Color(0xFF000000)      // true black (AMOLED): pixels off
    val card = Color(0xFF15181F)    // graphite cards, clearly lifted off the black
    val raised = Color(0xFF1F232C)
    val line = Color(0xFF2E3440)
    val text = Color(0xFFF4F6FA)    // near white: main text
    val dim = Color(0xFFB4BCCB)     // second text, still easy to read (about 10:1 on cards)
    val blue = Color(0xFF5B8CFF)
    val ice = Color(0xFFAFC6FF)
    val good = Color(0xFF3DDC97)
    val warn = Color(0xFFFFC24D)
    val bad = Color(0xFFFF6B7F)
    val violet = Color(0xFFB39BFF)
    val cyan = Color(0xFF5BD3F5)
    val pink = Color(0xFFFF85C0)
}

val Vazir = FontFamily(
    Font(R.font.vazir_regular, FontWeight.Normal),
    Font(R.font.vazir_medium, FontWeight.Medium),
    Font(R.font.vazir_semibold, FontWeight.SemiBold),
    Font(R.font.vazir_bold, FontWeight.Bold),
    Font(R.font.vazir_extrabold, FontWeight.ExtraBold),
    Font(R.font.vazir_black, FontWeight.Black),
)

private val KScheme = darkColorScheme(
    primary = K.blue,
    onPrimary = Color.White,
    secondary = K.ice,
    background = K.bg,
    surface = K.card,
    surfaceVariant = K.raised,
    surfaceContainerHigh = K.raised,
    onBackground = K.text,
    onSurface = K.text,
    onSurfaceVariant = K.dim,
    outline = K.line,
    error = K.bad,
)

private val KType = Typography().let { t ->
    fun TextStyle.v() = copy(fontFamily = Vazir)
    Typography(
        displayLarge = t.displayLarge.v(), displayMedium = t.displayMedium.v(), displaySmall = t.displaySmall.v(),
        headlineLarge = t.headlineLarge.v(), headlineMedium = t.headlineMedium.v(), headlineSmall = t.headlineSmall.v(),
        titleLarge = t.titleLarge.v(), titleMedium = t.titleMedium.v(), titleSmall = t.titleSmall.v(),
        bodyLarge = t.bodyLarge.v(), bodyMedium = t.bodyMedium.v(), bodySmall = t.bodySmall.v(),
        labelLarge = t.labelLarge.v(), labelMedium = t.labelMedium.v(), labelSmall = t.labelSmall.v(),
    )
}

@Composable
fun Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KScheme, typography = KType) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
    }
}

@Composable
fun Ltr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { content() }
}

// ---------------- page frame ----------------

/** A sub-page: back arrow, title, one line of help, then the content. */
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    help: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(K.bg)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت", tint = K.text, modifier = Modifier.size(22.dp)) }
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = K.text, maxLines = 1, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        HorizontalDivider(color = K.line, thickness = 1.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (help != null) Text(help, color = K.dim, fontSize = 13.sp, lineHeight = 21.sp)
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Loads something from the modem; shows a spinner, an error with retry, or the content.
 * What was loaded last time is shown at once while the fresh copy comes, so going back to a page
 * does not flash a spinner and the page stays where it was scrolled.
 */
@Composable
fun <T> Load(st: AppState, key: Any, load: ModemApi.() -> T, content: @Composable (T, () -> Unit) -> Unit) {
    val ck = "${currentCompositeKeyHash}:$key"
    var version by remember { mutableIntStateOf(0) }
    val first = remember(ck, version) { if (st.eager) runCatching { st.api.load() } else null }
    var got by remember { mutableStateOf<Pair<String, Result<T>>?>(null) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(ck, version) {
        if (first != null) return@LaunchedEffect
        running = true
        val now = st.io(load)
        if (now.isSuccess) st.loadCache[ck] = now
        got = ck to now
        running = false
    }
    @Suppress("UNCHECKED_CAST")
    val shown: Result<T>? = first ?: got?.takeIf { it.first == ck }?.second ?: st.loadCache[ck] as Result<T>?
    when {
        shown == null || (shown.isFailure && running) -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = K.blue, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
        }
        else -> shown.fold(
            { content(it) { version++ } },
            {
                Group {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(st.netError(it), color = K.text, fontSize = 14.sp)
                        SmallButton("دوباره امتحان کن", Icons.Filled.Refresh) { version++ }
                    }
                }
            },
        )
    }
}

// ---------------- grouped lists ----------------

@Composable
fun Group(title: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (title != null) Text(
            title, color = K.dim, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(K.card)
                .border(BorderStroke(1.dp, K.line), RoundedCornerShape(16.dp)),
        ) { content() }
        if (footer != null) Text(
            footer, color = K.dim, fontSize = 13.sp, lineHeight = 21.sp,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
        )
    }
}

@Composable
fun Divider() = HorizontalDivider(color = K.line, thickness = 1.dp, modifier = Modifier.padding(start = 54.dp))

/** A colored square with an icon, the same tile on every row so each place is easy to spot. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Int = 28) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.56f).dp)) }
}

@Composable
fun NavRow(icon: ImageVector, tint: Color, title: String, value: String? = null, enabled: Boolean = true, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(enabled = enabled && onClick != null) { onClick?.invoke() }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, if (enabled) tint else K.dim)
        Spacer(Modifier.width(12.dp))
        Text(title, color = if (enabled) K.text else K.dim, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = if (onClick == null) K.text else K.dim, fontSize = 13.sp, fontWeight = if (onClick == null) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = K.dim.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SwitchRow(title: String, sub: String? = null, checked: Boolean, enabled: Boolean = true, icon: ImageVector? = null, tint: Color = K.blue, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { IconTile(icon, tint); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) K.text else K.dim, fontSize = 15.sp)
            if (sub != null) Text(sub, color = K.dim, fontSize = 13.sp, lineHeight = 21.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            modifier = Modifier.scale(0.82f),
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = K.blue, checkedThumbColor = Color.White,
                uncheckedTrackColor = K.raised, uncheckedThumbColor = K.dim, uncheckedBorderColor = K.line,
                disabledCheckedTrackColor = K.blue.copy(alpha = 0.35f), disabledCheckedThumbColor = K.dim,
                disabledUncheckedTrackColor = K.raised, disabledUncheckedThumbColor = K.line,
            ),
        )
    }
}

/** Label and a value (numbers and addresses stay left to right). */
@Composable
fun InfoRow(label: String, value: String, valueColor: Color = K.text) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = K.dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        val v = value.ifEmpty { "—" }
        val t = @Composable { Text(v, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        // Numbers and addresses read left to right; Persian words keep their own direction.
        if (v.any { it in '\u0600'..'\u06FF' }) t() else Ltr(t)
    }
}

// ---------------- inputs ----------------

/** The label sits above a slim box, the same for every form in the app. */
@Composable
private fun FormLabel(text: String, error: Boolean = false) =
    Text(text, color = if (error) K.bad else K.dim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    ltr: Boolean = false,
    number: Boolean = false,
    password: Boolean = false,
    error: Boolean = false,
    enabled: Boolean = true,
    hint: String? = null,
    singleLine: Boolean = true,
) {
    var show by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        FormLabel(label, error)
        val box = @Composable {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp)).background(K.card)
                    .border(BorderStroke(if (focused) 1.5.dp else 1.dp, when { error -> K.bad; focused -> K.blue; else -> K.line }), RoundedCornerShape(12.dp))
                    .padding(start = 14.dp, end = if (password) 2.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = value, onValueChange = onChange, singleLine = singleLine, enabled = enabled,
                    textStyle = TextStyle(color = if (enabled) K.text else K.dim, fontSize = 15.sp, fontFamily = Vazir),
                    cursorBrush = SolidColor(K.blue),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when { password -> KeyboardType.Password; number -> KeyboardType.Number; ltr -> KeyboardType.Uri; else -> KeyboardType.Text },
                    ),
                    visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp).onFocusChanged { focused = it.isFocused },
                )
                if (password) IconButton(onClick = { show = !show }, modifier = Modifier.size(40.dp)) {
                    Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = K.dim, modifier = Modifier.size(19.dp))
                }
            }
        }
        if (ltr) Ltr(box) else box()
        if (hint != null) Text(hint, color = if (error) K.bad else K.dim, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    }
}

/** Pick one of a few values; opens a list. options = value to label. */
@Composable
fun Choice(label: String, options: List<Pair<String, String>>, value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shown = options.firstOrNull { it.first == value }?.second ?: value.ifEmpty { "—" }
    Column(Modifier.fillMaxWidth()) {
        FormLabel(label)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp)).background(K.card)
                .border(BorderStroke(1.dp, K.line), RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { open = true }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(shown, color = if (enabled) K.text else K.dim, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.UnfoldMore, null, tint = K.dim, modifier = Modifier.size(18.dp))
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(options) { (k, v) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(k); open = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = k == value, onClick = { onChange(k); open = false }, colors = RadioButtonDefaults.colors(selectedColor = K.blue))
                            Text(v, color = K.text)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("بستن") } },
            containerColor = K.raised,
        )
    }
}

@Composable
fun MainButton(text: String, enabled: Boolean = true, color: Color = K.blue, icon: ImageVector? = Icons.Filled.Check, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White, disabledContainerColor = K.raised, disabledContentColor = K.dim),
    ) {
        if (icon != null) { Icon(icon, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun GhostButton(text: String, icon: ImageVector? = null, color: Color = K.ice, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = K.raised, contentColor = color, disabledContainerColor = K.card, disabledContentColor = K.dim),
    ) {
        if (icon != null) { Icon(icon, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
    }
}

/** A small button that only takes the room it needs (inside cards). */
@Composable
fun SmallButton(text: String, icon: ImageVector? = null, color: Color = K.ice, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(K.raised).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) color else K.dim, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = if (enabled) color else K.dim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** A yes / no question before anything that cuts the connection or deletes. */
@Composable
fun Ask(title: String, text: String, yes: String, danger: Boolean = false, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(title) },
        text = { Text(text, fontSize = 14.sp, lineHeight = 22.sp, color = K.text) },
        confirmButton = { TextButton(onClick = { onNo(); onYes() }) { Text(yes, color = if (danger) K.bad else K.ice, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onNo) { Text("نه", color = K.dim) } },
        containerColor = K.raised,
    )
}

/** Shown on pages the modem only lets you change while the internet is off. */
@Composable
fun NeedsOffline(st: AppState) {
    if (!st.connected) return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(K.warn.copy(alpha = 0.10f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("مودم این بخش را فقط وقتی اینترنت قطع است عوض می‌کند. اول از خانه اینترنت را قطع کن.", color = K.warn, fontSize = 13.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Empty(text: String) {
    Group { Text(text, color = K.dim, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }
}
