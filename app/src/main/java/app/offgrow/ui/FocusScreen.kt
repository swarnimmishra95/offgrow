package app.offgrow.ui

import app.offgrow.garden.FocusConfig
import app.offgrow.garden.AppClock
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.offgrow.R
import app.offgrow.garden.GardenState
import app.offgrow.garden.Rules
import kotlinx.coroutines.delay
import java.time.LocalDate

private val DURATION_MS: Long get() = FocusConfig.durationMs

@Composable
fun FocusScreen(state: GardenState, onComplete: () -> Unit, onExit: () -> Unit, onRunningChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var outcome by rememberSaveable { mutableStateOf("") } // "", "done", "failed"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val running = startedAt > 0L && outcome.isEmpty()
    val kind = state.aliveFlowers.lastOrNull()?.kind ?: "sunflower"
    val flowerLabel = Rules.plantable(kind).label
    val doneToday = state.focusToday(AppClock.today())
    val complete by rememberUpdatedState(onComplete)

    LaunchedEffect(running) { onRunningChange(running) }

    LaunchedEffect(startedAt, outcome) {
        while (startedAt > 0L && outcome.isEmpty()) {
            now = System.currentTimeMillis()
            if (now - startedAt >= DURATION_MS) {
                outcome = "done"
                complete()
                break
            }
            delay(500)
        }
    }

    // Leaving Offgrow while the screen is on ends the session. Locking the phone is fine.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, running) {
        val handler = Handler(Looper.getMainLooper())
        val check = Runnable {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val stillAway = !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (stillAway && pm.isInteractive && startedAt > 0L && outcome.isEmpty() &&
                System.currentTimeMillis() - startedAt < DURATION_MS
            ) {
                outcome = "failed"
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (!running) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_STOP -> handler.postDelayed(check, 1500)
                Lifecycle.Event.ON_START -> handler.removeCallbacks(check)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            handler.removeCallbacks(check)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val remaining = if (running) (DURATION_MS - (now - startedAt)).coerceAtLeast(0L) else if (outcome == "done") 0L else DURATION_MS
    val progress = 1f - remaining.toFloat() / DURATION_MS
    val mm = (remaining / 60_000L).toInt()
    val ss = ((remaining / 1000L) % 60L).toInt()

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Night)
            .statusBarsPadding()
            .then(if (running) Modifier.navigationBarsPadding() else Modifier)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Focus", style = display(20, FontWeight.Bold, Palette.Paper, tracking = 0.0))
            Pill("${Rules.FOCUS_MINUTES} min · $flowerLabel", Palette.NightChip, Palette.Sun)
        }

        Box(Modifier.padding(top = 12.dp).size(280.dp), contentAlignment = Alignment.Center) {
            Image(
                painterResource(tileFor(kind)),
                contentDescription = "Your $flowerLabel growing while you focus",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(236.dp).clip(CircleShape),
            )
            Canvas(Modifier.size(280.dp)) {
                val stroke = 8.dp.toPx()
                val inset = stroke / 2f
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(Palette.NightChip, -90f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                val sweep = 360f * (if (outcome == "failed") 0f else progress)
                if (sweep > 0f) {
                    drawArc(Palette.Leaf, -90f, sweep, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "%d:%02d".format(mm, ss),
                style = display(64, FontWeight.Bold, Palette.Paper, lineHeight = 1.0),
            )
            val rewardLeft = doneToday < Rules.FOCUS_MAX_SESSIONS
            val line = when {
                outcome == "done" -> if (doneToday <= Rules.FOCUS_MAX_SESSIONS) "Done. Your garden grew +${Rules.FOCUS}." else "Done. Today's focus rewards are used up, but that was still time well spent."
                outcome == "failed" -> "The session ended when you left Offgrow."
                running -> "${mm + 1} min until your garden gets +${Rules.FOCUS}"
                rewardLeft -> "Stay with it for ${Rules.FOCUS_MINUTES} minutes. Each session adds +${Rules.FOCUS}, up to ${Rules.FOCUS_MAX_SESSIONS} a day."
                else -> "You've had today's ${Rules.FOCUS_MAX_SESSIONS} rewarded sessions. You can still focus."
            }
            Text(line, style = body(15, color = Palette.NightText), textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(4.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.NightCard)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = Palette.Sun, modifier = Modifier.size(20.dp))
            Text(
                "Leave Offgrow and the session ends. Locking your phone is fine, and so is putting it face down.",
                style = body(14, color = Palette.NightText, lineHeight = 1.45),
            )
        }

        when {
            running -> OutlineButton(
                "Give up",
                { outcome = "failed" },
                Modifier.fillMaxWidth(),
                height = 52.dp,
                color = Palette.Paper,
                border = Palette.NightLine,
            )
            outcome.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Back to garden", {
                    startedAt = 0L
                    outcome = ""
                    onExit()
                }, container = Palette.Paper, content = Palette.Ink)
                OutlineButton(
                    "Start another",
                    {
                        outcome = ""
                        now = System.currentTimeMillis()
                        startedAt = now
                    },
                    Modifier.fillMaxWidth(),
                    height = 52.dp,
                    color = Palette.Paper,
                    border = Palette.NightLine,
                )
            }
            else -> PrimaryButton("Start focus", {
                now = System.currentTimeMillis()
                startedAt = now
            }, container = Palette.Paper, content = Palette.Ink)
        }
    }
}
