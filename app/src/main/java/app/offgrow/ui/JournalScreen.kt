package app.offgrow.ui

import app.offgrow.garden.AppClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.offgrow.garden.CareItem
import app.offgrow.garden.Rules
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private data class ChartDay(val label: String, val delta: Int, val today: Boolean)

@Composable
fun JournalScreen(ui: UiState, onOpenFlower: (String) -> Unit) {
    val state = ui.state ?: return
    val today = AppClock.today()
    val weekStart = today.minusDays(6)
    val recent = state.days.filter { d ->
        try {
            !LocalDate.parse(d.day).isBefore(weekStart)
        } catch (_: Exception) {
            false
        }
    }
    val todayDelta = Rules.liveDelta(ui.todayItems)
    // Net change this week: where vitality is now versus where it stood before the week began.
    val baseline = state.days.lastOrNull { d ->
        try {
            LocalDate.parse(d.day).isBefore(weekStart)
        } catch (_: Exception) {
            false
        }
    }?.vitalityEnd ?: Rules.START_VITALITY
    val weekDelta = ui.live - baseline
    val newPlants = state.flowers.count { f ->
        try {
            !LocalDate.parse(f.plantedDay).isBefore(weekStart)
        } catch (_: Exception) {
            false
        }
    }
    val rough = recent.count { !it.good }

    val byDay = state.days.associateBy { it.day }
    val chart = (6 downTo 0).map { back ->
        val d = today.minusDays(back.toLong())
        val letter = d.format(DateTimeFormatter.ofPattern("EEEEE", Locale.getDefault()))
        if (back == 0) ChartDay(letter, todayDelta, true) else ChartDay(letter, byDay[d.toString()]?.delta ?: 0, false)
    }
    val fmtShort = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Paper)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Garden journal", style = display(30))
            Text(
                "This week · ${weekStart.format(fmtShort)} to ${today.format(fmtShort)}",
                style = body(14, FontWeight.SemiBold, Palette.Muted),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Card)
                .border(1.dp, Palette.Border, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row {
                WeekStat(signed(weekDelta), "vitality", if (weekDelta >= 0) Palette.Moss else Palette.Red, Modifier.weight(1f))
                WeekStat(newPlants.toString(), if (newPlants == 1) "new plant" else "new plants", Palette.Ink, Modifier.weight(1f))
                WeekStat(rough.toString(), if (rough == 1) "rough day" else "rough days", if (rough > 0) Palette.Red else Palette.Ink, Modifier.weight(1f))
            }
            WeekChart(chart)
        }

        if (state.flowers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Your flowers")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.flowers.asReversed().forEach { f ->
                        Column(
                            Modifier
                                .width(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(role = Role.Button) { onOpenFlower(f.id) }
                                .padding(bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Image(
                                painterResource(tileFor(f.kind)),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .alpha(if (f.alive) 1f else 0.4f),
                            )
                            Text(f.name, style = body(13, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!f.alive) Text("Lost", style = body(12, FontWeight.Bold, Palette.Red))
                        }
                    }
                }
            }
        }

        DaySection("Today", ui.todayItems.filter { !it.pending })
        state.days.asReversed().take(14).forEach { d ->
            val label = try {
                val date = LocalDate.parse(d.day)
                if (date == today.minusDays(1)) "Yesterday" else date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
            } catch (_: Exception) {
                d.day
            }
            DaySection(label, d.items, summary = "${Rules.fmtMin(d.socialMin)} social · ${signed(d.delta)}")
        }
        if (state.days.isEmpty()) {
            Text(
                "Each night your day is closed and written here: what helped your garden, and what didn't.",
                style = body(14, color = Palette.Muted),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WeekStat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = display(26, FontWeight.Bold, color))
        Text(label, style = body(12, FontWeight.SemiBold, Palette.Muted))
    }
}

@Composable
private fun WeekChart(days: List<ChartDay>) {
    val scale = max(10, days.maxOf { abs(it.delta) })
    val desc = days.joinToString(", ") { "${it.label} ${signed(it.delta)}" }
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Daily vitality change: $desc" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.BottomCenter) {
                    if (d.delta > 0) {
                        Box(
                            Modifier
                                .width(22.dp)
                                .height((58f * d.delta / scale).coerceAtLeast(3f).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(if (d.today) Palette.Leaf else Palette.Moss),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Rule))
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.TopCenter) {
                    if (d.delta < 0) {
                        Box(
                            Modifier
                                .width(22.dp)
                                .height((52f * -d.delta / scale).coerceAtLeast(3f).dp)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                                .background(if (d.today) Color(0xFFF2A08F) else Palette.Coral),
                        )
                    }
                }
                Text(
                    d.label,
                    style = body(12, if (d.today) FontWeight.Bold else FontWeight.SemiBold, if (d.today) Palette.Ink else Palette.Muted),
                )
            }
        }
    }
}

@Composable
private fun DaySection(label: String, items: List<CareItem>, summary: String? = null) {
    if (items.isEmpty() && summary == null) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = body(13, FontWeight.Bold, Palette.Muted))
            if (summary != null) Text(summary, style = body(13, FontWeight.SemiBold, Palette.Muted))
        }
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(careDot(item.key)))
                Text(item.label, style = body(15), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    signed(item.delta),
                    style = body(15, FontWeight.Bold, if (item.delta > 0) Palette.Moss else if (item.delta < 0) Palette.Red else Palette.Muted),
                )
            }
        }
    }
}
