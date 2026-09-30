package app.offgrow.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.offgrow.R
import app.offgrow.garden.Band

object Palette {
    val Paper = Color(0xFFF3EEE3)
    val Ink = Color(0xFF1D2B22)
    val InkSoft = Color(0xFF3A443C)
    val Body = Color(0xFF4A5249)
    val Moss = Color(0xFF2F6B45)
    val Muted = Color(0xFF5C6159)
    val Card = Color(0xFFFBF8F2)
    val Border = Color(0xFFE3DCCD)
    val Track = Color(0xFFDDD5C6)
    val Rule = Color(0xFFCFC6B5)
    val Amber = Color(0xFFD99A1E)
    val Sun = Color(0xFFF2B632)
    val Red = Color(0xFFC2412B)
    val Coral = Color(0xFFE8553D)
    val Wilt = Color(0xFFB8894A)
    val Leaf = Color(0xFF9CC47F)
    val Sage = Color(0xFFE3EDD5)
    val Butter = Color(0xFFFBE6B0)
    val Night = Color(0xFF1D2B22)
    val NightCard = Color(0xFF26382C)
    val NightChip = Color(0xFF2E4235)
    val NightLine = Color(0xFF4A5E50)
    val NightText = Color(0xFFC9D1C4)

    fun band(b: Band): Color = when (b) {
        Band.THRIVING, Band.HEALTHY -> Moss
        Band.HOLDING -> Amber
        Band.WILTING -> Wilt
    }
}

val Display = FontFamily(
    Font(R.font.bricolage_600, FontWeight.SemiBold),
    Font(R.font.bricolage_700, FontWeight.Bold),
    Font(R.font.bricolage_800, FontWeight.ExtraBold),
)

val Sans = FontFamily(
    Font(R.font.instrument_400, FontWeight.Normal),
    Font(R.font.instrument_600, FontWeight.SemiBold),
    Font(R.font.instrument_700, FontWeight.Bold),
)

fun display(size: Int, weight: FontWeight = FontWeight.ExtraBold, color: Color = Palette.Ink, tracking: Double = -0.03, lineHeight: Double = 1.08): TextStyle =
    TextStyle(
        fontFamily = Display,
        fontWeight = weight,
        fontSize = size.sp,
        letterSpacing = tracking.em,
        lineHeight = (size * lineHeight).sp,
        color = color,
    )

fun body(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Palette.Ink, lineHeight: Double = 1.4): TextStyle =
    TextStyle(
        fontFamily = Sans,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * lineHeight).sp,
        color = color,
    )

@Composable
fun OffgrowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Moss,
            onPrimary = Palette.Paper,
            background = Palette.Paper,
            onBackground = Palette.Ink,
            surface = Palette.Card,
            onSurface = Palette.Ink,
        ),
        content = content,
    )
}
