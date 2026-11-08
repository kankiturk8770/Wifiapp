package cafe.kankipro.modem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object K {
    val bg = Color(0xFF0D1015)
    val card = Color(0xFF161B23)
    val line = Color(0xFF232A35)
    val text = Color(0xFFE9EDF3)
    val dim = Color(0xFF8A94A6)
    val good = Color(0xFF3DDC97)
    val warn = Color(0xFFF5B83D)
    val bad = Color(0xFFFF5D6C)
    val blue = Color(0xFF5AA9FF)
}

val KScheme = darkColorScheme(
    primary = K.good,
    onPrimary = Color(0xFF00281A),
    secondary = K.blue,
    background = K.bg,
    surface = K.card,
    surfaceVariant = K.line,
    onBackground = K.text,
    onSurface = K.text,
    onSurfaceVariant = K.dim,
    error = K.bad,
)

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(K.card)
            .padding(16.dp),
    ) { content() }
}

@Composable
fun Title(text: String, sub: String? = null) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = K.text)
        if (sub != null) Text(sub, fontSize = 13.sp, color = K.dim)
    }
}

/** A small tile: icon, label and value. */
@Composable
fun Stat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier, tint: Color = K.good) {
    Card(modifier) {
        Column {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = K.text, maxLines = 1)
            Text(label, fontSize = 12.sp, color = K.dim, maxLines = 1)
        }
    }
}

/** One line: label on one side, value (left to right, for numbers and addresses) on the other. */
@Composable
fun Line(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = K.dim, fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Ltr { Text(value.ifEmpty { "—" }, color = K.text, fontSize = 14.sp) }
    }
}

/** Signal bars, 0..5 filled. */
@Composable
fun Bars(level: Int, color: Color = K.good) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..5) {
            Box(
                Modifier
                    .width(6.dp)
                    .height((6 + i * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= level) color else K.line),
            )
        }
    }
}

@Composable
fun Ltr(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) { content() }
}

@Composable
fun Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KScheme) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
        ) { content() }
    }
}
