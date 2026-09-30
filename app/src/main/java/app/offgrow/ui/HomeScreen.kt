package app.offgrow.ui

import app.offgrow.garden.AppClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.offgrow.R
import app.offgrow.garden.Band
import app.offgrow.garden.GardenEngine
import app.offgrow.garden.Rules
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GardenPicture(garden: ImageBitmap?, description: String, modifier: Modifier = Modifier) {
    Crossfade(targetState = garden, animationSpec = tween(900), label = "garden", modifier = modifier) { img ->
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFFE7EBDD), Color(0xFFC9D8B8), Color(0xFF9DB98A)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("Growing your garden…", style = body(15, FontWeight.SemiBold, Palette.InkSoft))
            }
        }
    }
}

@Composable
fun HomeScreen(
    ui: UiState,
    onPlant: () -> Unit,
    onGrantAccess: () -> Unit,
    onFocus: () -> Unit,
) {
    val state = ui.state ?: return
    val today = ui.today
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Paper)) {
        val imageH = min(maxWidth * 1.05f, maxHeight * 0.5f)
        GardenPicture(
            garden = ui.garden,
            description = "${ui.band.headline}. Your garden illustration.",
            modifier = Modifier.fillMaxWidth().height(imageH),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(imageH - 22.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Palette.Paper)
                    .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val kicker = if (state.lowDays >= 2) "${state.lowDays} rough days in a row"
                        else AppClock.today().format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
                        Text(kicker, style = body(13, FontWeight.SemiBold, Palette.Muted))
                        Text(ui.band.headline, style = display(24, lineHeight = 1.1))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(ui.live.toString(), style = display(28, lineHeight = 1.0))
                        Text("vitality", style = body(11, FontWeight.SemiBold, Palette.Muted))
                    }
                }
                Bar(ui.live / 100f, Palette.band(ui.band))

                if (!ui.hasAccess) {
                    Notice(
                        title = "Usage access is off",
                        text = "Your garden can't grow or be scored without it. Turn it back on in Android Settings.",
                        action = "Turn on",
                        onClick = onGrantAccess,
                        tone = Palette.Red,
                    )
                }

                if (state.pendingSeeds > 0) SeedBanner(state.pendingSeeds, onPlant)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                    StatCard(
                        "Social today",
                        if (today != null) Rules.fmtMin(today.socialMin) else "—",
                        "of ${Rules.fmtMin(state.limitMin)}",
                        Modifier.weight(1f),
                    )
                    StatCard(
                        "Streak",
                        if (state.streak == 1) "1 day" else "${state.streak} days",
                        "best ${state.bestStreak}",
                        Modifier.weight(1f),
                    )
                    StatCard(
                        "Phone-free",
                        if (today != null) Rules.fmtMin(today.phoneFreeMin) else "—",
                        "since waking",
                        Modifier.weight(1f),
                    )
                }

                if (ui.band == Band.WILTING) {
                    val left = Rules.LOST_AFTER_LOW_DAYS - state.lowDays.coerceIn(0, Rules.LOST_AFTER_LOW_DAYS - 1)
                    Notice(
                        title = "Your flowers can still be saved",
                        text = "One good day revives them. After $left more wilted ${if (left == 1) "day" else "days"}, one is lost.",
                        action = "Start a focus session",
                        onClick = onFocus,
                        tone = Palette.Wilt,
                    )
                }

                Column(Modifier.padding(top = 2.dp)) {
                    val total = Rules.liveDelta(ui.todayItems)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text("Today's care", style = display(17, FontWeight.Bold, tracking = 0.0))
                        Text(
                            if (total >= 0) "${signed(total)} so far" else "${signed(total)} today",
                            style = body(13, FontWeight.Bold, if (total >= 0) Palette.Moss else Palette.Red),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    if (ui.todayItems.isEmpty()) {
                        Text(
                            if (ui.hasAccess) "Counting today's care…" else "Turn on usage access to see today's care.",
                            style = body(14, color = Palette.Muted),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    ui.todayItems.forEach { CareRow(it) }
                    NextSeedRow(state.pendingSeeds, today?.socialMin, state.limitMin, onPlant)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val title = if (state.name.isNotBlank()) "${state.name}'s garden" else "offgrow"
            Pill(title, Palette.Paper.copy(alpha = 0.9f), Palette.Ink, displayFont = true)
            Pill("Day ${GardenEngine.dayNumber(state)}", Palette.Ink, Palette.Paper)
        }
    }
}

@Composable
private fun SeedBanner(count: Int, onPlant: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Butter)
            .clickable(role = Role.Button, onClick = onPlant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painterResource(R.drawable.tile_sunflower),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(if (count == 1) "You earned a seed" else "You have $count seeds", style = display(17, FontWeight.Bold, tracking = -0.01))
            Text("Plant a flower to remember a good day.", style = body(13, color = Palette.InkSoft))
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.Ink)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("Plant", style = body(14, FontWeight.Bold, Palette.Paper))
        }
    }
}

@Composable
fun Notice(title: String, text: String, action: String?, onClick: () -> Unit, tone: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Card)
            .border(1.5.dp, tone.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = body(15, FontWeight.Bold))
            Text(text, style = body(13, color = Palette.InkSoft))
            if (action != null) Text(action, style = body(13, FontWeight.Bold, tone), modifier = Modifier.padding(top = 4.dp))
        }
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NextSeedRow(pending: Int, socialMin: Int?, limit: Int, onPlant: () -> Unit) {
    val (label, detail, right) = when {
        pending > 0 -> Triple(
            if (pending == 1) "Seed waiting" else "$pending seeds waiting",
            "Plant it to remember a good day",
            "Plant",
        )
        socialMin == null -> Triple("Next seed", "Stay under ${Rules.fmtMin(limit)} today to earn one", "")
        socialMin <= limit -> Triple("Next seed", "Stay under ${Rules.fmtMin(limit)} today to earn one", "tonight")
        else -> Triple("Next seed", "Over the limit today. Fresh start tomorrow.", "tomorrow")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = pending > 0, role = Role.Button, onClick = onPlant)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CareIcon("seed")
        Column(Modifier.weight(1f)) {
            Text(label, style = body(15, FontWeight.SemiBold), maxLines = 1)
            Text(detail, style = body(13, color = Palette.Muted, lineHeight = 1.3), maxLines = 2)
        }
        Text(right, style = body(15, FontWeight.Bold, if (pending > 0) Palette.Moss else Palette.Muted))
    }
}
