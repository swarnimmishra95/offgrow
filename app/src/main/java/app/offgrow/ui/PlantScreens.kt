package app.offgrow.ui

import app.offgrow.garden.AppClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.offgrow.R
import app.offgrow.garden.DayRecord
import app.offgrow.garden.Flower
import app.offgrow.garden.GardenState
import app.offgrow.garden.Rules
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun tileFor(kind: String): Int = when (kind) {
    "sunflower" -> R.drawable.tile_sunflower
    "poppy" -> R.drawable.tile_poppy
    "cornflower" -> R.drawable.tile_cornflower
    "daisy" -> R.drawable.tile_daisy
    "cosmos" -> R.drawable.tile_cosmos
    "marigold" -> R.drawable.tile_marigold
    "lavender" -> R.drawable.tile_lavender
    "tulip" -> R.drawable.tile_tulip
    "rosebush" -> R.drawable.tile_rosebush
    "hydrangea" -> R.drawable.tile_hydrangea
    else -> R.drawable.tile_daisy
}

private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())

fun longDay(day: String): String = try {
    LocalDate.parse(day).format(LONG_DAY)
} catch (_: Exception) {
    day
}

@Composable
fun PlantScreen(state: GardenState, onClose: () -> Unit, onPlant: (kind: String, note: String) -> Unit) {
    val first = state.flowers.isEmpty()
    var selected by rememberSaveable { mutableStateOf("sunflower") }
    var note by rememberSaveable { mutableStateOf("") }
    var planting by remember { mutableStateOf(false) }
    val seedRecord: DayRecord? = state.seedDay?.let { d -> state.days.lastOrNull { it.day == d } }
    val growing = state.aliveFlowers.map { it.kind }.toSet()

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton44(R.drawable.ic_close, "Close", onClose)
            Text(
                AppClock.today().format(LONG_DAY),
                style = body(13, FontWeight.Bold, Palette.Muted),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(
                    painterResource(tileFor(selected)),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(76.dp).clip(CircleShape),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (first) "Plant your first flower" else "You earned a seed",
                        style = display(28, lineHeight = 1.05),
                    )
                    Text(
                        when {
                            first -> "Every good day earns a seed. Here's one to start your garden."
                            seedRecord != null -> "${dayName(seedRecord.day)} was a good day. Plant a flower to remember it by."
                            else -> "A good day away from your phone. Plant a flower to remember it by."
                        },
                        style = body(14, color = Palette.Body),
                    )
                }
            }

            if (seedRecord != null && !first) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Palette.Sage)
                        .padding(12.dp),
                ) {
                    SeedStat(Rules.fmtMin(seedRecord.phoneFreeMin), "phone-free", Modifier.weight(1f))
                    SeedStat(Rules.fmtMin(seedRecord.socialMin), "social (of ${Rules.fmtMin(seedRecord.limitMin)})", Modifier.weight(1f))
                    SeedStat(if (seedRecord.pickups >= 0) seedRecord.pickups.toString() else "—", "pickups", Modifier.weight(1f))
                }
            }

            Rules.PLANTABLES.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { p ->
                        val locked = state.bestStreak < p.unlockStreak
                        val status = when {
                            locked -> "${p.unlockStreak}-day streak"
                            p.kind == selected -> "Selected"
                            p.kind in growing -> "Growing"
                            else -> "New"
                        }
                        FlowerCard(
                            label = p.label,
                            tile = tileFor(p.kind),
                            status = status,
                            selected = p.kind == selected,
                            locked = locked,
                            onClick = { if (!locked) selected = p.kind },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
        // Kept outside the scrolling list so the note stays in view above the keyboard.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) {
            Text("What did you do instead of scrolling?", style = body(14, FontWeight.Bold))
            TextInput(note, { note = it.take(160) }, "A walk, a book, a long lunch…")
        }
        PrimaryButton(
            if (planting) "Planting…" else "Plant ${Rules.plantable(selected).label.lowercase()}",
            {
                if (!planting) {
                    planting = true
                    onPlant(selected, note)
                }
            },
            modifier = Modifier.padding(top = 12.dp),
            enabled = !planting && state.pendingSeeds > 0,
        )
    }
}

private fun dayName(day: String): String = try {
    val d = LocalDate.parse(day)
    if (d == AppClock.today().minusDays(1)) "Yesterday" else d.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
} catch (_: Exception) {
    "Yesterday"
}

@Composable
private fun SeedStat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = display(19))
        Text(label, style = body(12, color = Palette.Body), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FlowerCard(
    label: String,
    tile: Int,
    status: String,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Card)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Palette.Moss else Palette.Border, RoundedCornerShape(20.dp))
            .clickable(enabled = !locked, role = Role.RadioButton, onClick = onClick)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Image(
                painterResource(tile),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .alpha(if (locked) 0.5f else 1f),
            )
            if (locked) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Palette.Paper.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_lock), contentDescription = "Locked", tint = Palette.Ink, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(label, style = body(14, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            status,
            style = body(11, FontWeight.Bold, if (selected) Color(0xFF7A5200) else if (locked) Palette.Muted else Palette.Moss),
            maxLines = 1,
        )
    }
}

@Composable
fun FlowerScreen(
    state: GardenState,
    flowerId: String,
    garden: androidx.compose.ui.graphics.ImageBitmap?,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
) {
    val found: Flower? = state.flowers.firstOrNull { it.id == flowerId }
    if (found == null) {
        LaunchedEffect(flowerId) { onBack() }
        return
    }
    val flower: Flower = found
    var renaming by remember { mutableStateOf(false) }
    val planted = try {
        LocalDate.parse(flower.plantedDay)
    } catch (_: Exception) {
        AppClock.today()
    }
    val record = state.days.lastOrNull { it.day == planted.minusDays(1).toString() }

    Box(Modifier.fillMaxSize().background(Palette.Paper)) {
        Box(Modifier.fillMaxWidth().height(380.dp)) {
            if (garden != null) {
                Image(
                    bitmap = garden,
                    contentDescription = "Close-up of your garden",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.7f
                            scaleY = 1.7f
                            transformOrigin = TransformOrigin(0.5f, 0.8f)
                        },
                )
            } else {
                Image(
                    painterResource(tileFor(flower.kind)),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(356.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Palette.Paper)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Palette.Track),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Image(
                        painterResource(tileFor(flower.kind)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .alpha(if (flower.alive) 1f else 0.5f),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Planted ${planted.format(LONG_DAY)}", style = body(13, FontWeight.Bold, Palette.Muted))
                        Text(flower.name, style = display(26, lineHeight = 1.1))
                    }
                }
                if (flower.note.isNotBlank()) {
                    Text(
                        "“${flower.note}”",
                        style = display(18, FontWeight.SemiBold, tracking = 0.0, lineHeight = 1.35),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Palette.Butter)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
                if (record != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Phone-free", Rules.fmtMin(record.phoneFreeMin), "that day", Modifier.weight(1f))
                        StatCard("Social", Rules.fmtMin(record.socialMin), "of ${Rules.fmtMin(record.limitMin)}", Modifier.weight(1f))
                        StatCard("Pickups", if (record.pickups >= 0) record.pickups.toString() else "—", "that day", Modifier.weight(1f))
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.Card)
                        .border(1.dp, Palette.Border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = Palette.Moss, modifier = Modifier.size(20.dp))
                    val days = ChronoUnit.DAYS.between(planted, AppClock.today()).coerceAtLeast(0)
                    val text = if (flower.alive) {
                        val span = if (days == 0L) "Planted today" else "Blooming for $days ${if (days == 1L) "day" else "days"}"
                        "$span. Flowers droop when you go over your limit. After 3 rough days in a row, your newest flower is lost."
                    } else {
                        "Lost on ${longDay(flower.lostDay ?: "")}. The garden let it go, but the memory stays here."
                    }
                    Text(text, style = body(14, color = Palette.InkSoft, lineHeight = 1.45))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlineButton("Rename", { renaming = true }, Modifier.weight(1f))
                    PrimaryButton("Back to garden", onBack, Modifier.weight(1f), height = 50.dp)
                }
            }
        }
        IconButton44(
            R.drawable.ic_back,
            "Back to garden",
            onBack,
            Modifier
                .statusBarsPadding()
                .padding(16.dp),
            background = Palette.Paper.copy(alpha = 0.92f),
        )
    }

    if (renaming) {
        var text by remember { mutableStateOf(flower.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Name this flower", style = display(22)) },
            text = { TextInput(text, { text = it.take(60) }, "The lake-day sunflowers") },
            confirmButton = {
                TextButton(onClick = {
                    onRename(text)
                    renaming = false
                }) { Text("Save", style = body(15, FontWeight.Bold, Palette.Moss)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text("Cancel", style = body(15, FontWeight.SemiBold, Palette.Muted)) }
            },
            containerColor = Palette.Paper,
        )
    }
}
