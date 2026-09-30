package app.offgrow.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.offgrow.R
import app.offgrow.garden.CareItem

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    container: Color = Palette.Ink,
    content: Color = Palette.Paper,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(if (enabled) container else container.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = body(17, FontWeight.SemiBold, content))
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 50.dp,
    color: Color = Palette.Ink,
    border: Color = Palette.Ink,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .border(1.5.dp, border, RoundedCornerShape(height / 2))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = body(15, FontWeight.SemiBold, color))
    }
}

@Composable
fun IconButton44(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Palette.Ink, background: Color = Color.Transparent) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp, track: Color = Palette.Track) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = tween(700), label = "bar")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track),
    ) {
        if (f > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(f)
                    .clip(RoundedCornerShape(height / 2))
                    .background(color),
            )
        }
    }
}

@Composable
fun StatCard(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Card)
            .border(1.dp, Palette.Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(label, style = body(12, FontWeight.SemiBold, Palette.Muted), maxLines = 1)
        Text(value, style = display(21, FontWeight.Bold, tracking = -0.02), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, style = body(12, color = Palette.Muted), maxLines = 1)
    }
}

@Composable
fun Pill(text: String, background: Color, color: Color, modifier: Modifier = Modifier, displayFont: Boolean = false) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = if (displayFont) display(18, color = color) else body(13, FontWeight.Bold, color),
            maxLines = 1,
        )
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Palette.Ink else Palette.Card)
            .border(1.dp, if (selected) Palette.Ink else Palette.Border, RoundedCornerShape(22.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = body(15, FontWeight.SemiBold, if (selected) Palette.Paper else Palette.Ink), maxLines = 1)
    }
}

@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = body(15),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (singleLine) 48.dp else 96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Card)
                    .border(1.dp, Palette.Track, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = body(15, color = Palette.Muted))
                inner()
            }
        },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.Muted) {
    Text(text.uppercase(), style = body(13, FontWeight.Bold, color), modifier = modifier)
}

fun signed(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "−${-delta}"
    else -> "0"
}

/** One row of today's care: icon, label, detail and the change it makes. */
@Composable
fun CareRow(item: CareItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CareIcon(item.key, pending = item.pending)
        Column(Modifier.weight(1f)) {
            Text(item.label, style = body(15, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.detail, style = body(13, color = Palette.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val txt = when {
            item.pending -> "+${item.delta}"
            item.delta == 0 -> "0"
            else -> signed(item.delta)
        }
        val color = when {
            item.pending -> Palette.Muted
            item.delta > 0 -> Palette.Moss
            item.delta < 0 -> Palette.Red
            else -> Palette.Muted
        }
        Text(txt, style = body(15, FontWeight.Bold, color))
    }
}

fun careDot(key: String): Color = when (key) {
    "sun" -> Palette.Sun
    "roots" -> Color(0xFF8A6A45)
    "water" -> Color(0xFF4A86C8)
    "focus" -> Palette.Leaf
    "comeback" -> Color(0xFF3B6FD1)
    "wilt", "weeds", "lost" -> Palette.Coral
    "seed" -> Palette.Wilt
    else -> Palette.Muted
}

/** Small round illustrations for each kind of care. */
@Composable
fun CareIcon(key: String, pending: Boolean = false, size: Dp = 34.dp) {
    val bg = when (key) {
        "sun" -> Palette.Butter
        "shade" -> Color(0xFFE6E1D6)
        "roots" -> Color(0xFFE8DCC8)
        "weeds" -> Color(0xFFEFE6D2)
        "water" -> Color(0xFFD9E7F2)
        "wilt", "lost" -> Color(0xFFF6DCD2)
        "focus" -> Palette.Sage
        "comeback" -> Color(0xFFDCE4F6)
        else -> Color(0xFFDDEBD0)
    }
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 34f
        drawCircle(if (pending) bg.copy(alpha = 0.55f) else bg)
        val c = Offset(17f * s, 17f * s)
        when (key) {
            "sun" -> drawCircle(Palette.Sun.copy(alpha = if (pending) 0.55f else 1f), radius = 8f * s, center = c)
            "shade" -> {
                drawCircle(Color(0xFFB9B2A3), radius = 7f * s, center = Offset(15f * s, 17f * s))
                drawCircle(Color(0xFFE6E1D6), radius = 6f * s, center = Offset(19f * s, 15f * s))
            }
            "roots" -> {
                val p = Path().apply {
                    moveTo(17f * s, 8f * s); lineTo(17f * s, 17f * s)
                    moveTo(17f * s, 17f * s); lineTo(12f * s, 25f * s)
                    moveTo(17f * s, 17f * s); lineTo(22f * s, 25f * s)
                    moveTo(17f * s, 19f * s); lineTo(17f * s, 27f * s)
                }
                drawPath(p, Color(0xFF8A6A45), style = Stroke(width = 2.2f * s, cap = StrokeCap.Round))
            }
            "weeds" -> {
                val p = Path().apply {
                    moveTo(11f * s, 25f * s); quadraticTo(12f * s, 16f * s, 9f * s, 11f * s)
                    moveTo(17f * s, 25f * s); quadraticTo(17f * s, 15f * s, 19f * s, 9f * s)
                    moveTo(23f * s, 25f * s); quadraticTo(22f * s, 18f * s, 26f * s, 13f * s)
                }
                drawPath(p, Color(0xFF8A7A3A), style = Stroke(width = 2f * s, cap = StrokeCap.Round))
            }
            "water" -> {
                val p = Path().apply {
                    moveTo(17f * s, 8f * s)
                    cubicTo(12f * s, 15f * s, 11f * s, 18f * s, 11f * s, 20f * s)
                    cubicTo(11f * s, 23.5f * s, 13.7f * s, 26f * s, 17f * s, 26f * s)
                    cubicTo(20.3f * s, 26f * s, 23f * s, 23.5f * s, 23f * s, 20f * s)
                    cubicTo(23f * s, 18f * s, 22f * s, 15f * s, 17f * s, 8f * s)
                    close()
                }
                drawPath(p, Color(0xFF4A86C8).copy(alpha = if (pending) 0.55f else 1f))
            }
            "wilt", "lost" -> {
                val stem = Path().apply {
                    moveTo(15f * s, 27f * s); quadraticTo(15f * s, 16f * s, 22f * s, 14f * s)
                }
                drawPath(stem, Palette.Moss, style = Stroke(width = 2f * s, cap = StrokeCap.Round))
                drawCircle(Palette.Red, radius = 4.2f * s, center = Offset(22.5f * s, 17.5f * s))
            }
            "focus" -> {
                drawCircle(Palette.Moss, radius = 8f * s, center = Offset(17f * s, 18f * s), style = Stroke(width = 2f * s))
                drawLine(Palette.Moss, Offset(17f * s, 18f * s), Offset(17f * s, 13.5f * s), strokeWidth = 2f * s, cap = StrokeCap.Round)
                drawLine(Palette.Moss, Offset(14f * s, 7.5f * s), Offset(20f * s, 7.5f * s), strokeWidth = 2f * s, cap = StrokeCap.Round)
            }
            "comeback" -> {
                val p = Path().apply {
                    moveTo(11f * s, 21f * s); lineTo(15f * s, 16f * s); lineTo(18f * s, 19f * s); lineTo(23f * s, 12f * s)
                }
                drawPath(p, Color(0xFF3B6FD1), style = Stroke(width = 2.4f * s, cap = StrokeCap.Round))
                drawCircle(Color(0xFF3B6FD1), radius = 2f * s, center = Offset(23f * s, 12f * s))
            }
            else -> { // seed
                drawOval(Palette.Wilt, topLeft = Offset(12.4f * s, 14f * s), size = Size(9.2f * s, 12f * s))
                val sprout = Path().apply {
                    moveTo(17f * s, 14f * s); quadraticTo(17f * s, 9f * s, 21f * s, 8f * s)
                }
                drawPath(sprout, Palette.Moss, style = Stroke(width = 2f * s, cap = StrokeCap.Round))
                rotate(-20f, pivot = Offset(21f * s, 9f * s)) {
                    drawOval(Palette.Moss, topLeft = Offset(18f * s, 7.4f * s), size = Size(6f * s, 3.2f * s))
                }
            }
        }
    }
}

enum class Tab(val label: String, val icon: Int) {
    GARDEN("Garden", R.drawable.ic_garden),
    FOCUS("Focus", R.drawable.ic_focus),
    JOURNAL("Journal", R.drawable.ic_journal),
    SETTINGS("Settings", R.drawable.ic_settings),
}

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit, dark: Boolean = false) {
    val bg = if (dark) Palette.Night else Palette.Paper
    val line = if (dark) Palette.NightChip else Palette.Track
    val on = if (dark) Palette.Paper else Palette.Ink
    val off = if (dark) Palette.NightText else Palette.Muted
    Column(Modifier.fillMaxWidth().background(bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(line))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val active = tab == current
                Column(
                    modifier = Modifier
                        .width(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Tab) { onSelect(tab) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(painterResource(tab.icon), contentDescription = null, tint = if (active) on else off, modifier = Modifier.size(24.dp))
                    Text(
                        tab.label,
                        style = body(12, if (active) FontWeight.Bold else FontWeight.SemiBold, if (active) on else off),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
