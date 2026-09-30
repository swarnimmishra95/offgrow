package app.offgrow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.offgrow.R

@Composable
fun Logo(size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 26f
        drawCircle(Palette.Moss)
        drawLine(Palette.Paper, Offset(13f * s, 20f * s), Offset(13f * s, 11f * s), strokeWidth = 2.2f * s, cap = StrokeCap.Round)
        rotate(-30f, pivot = Offset(9.5f * s, 11f * s)) {
            drawOval(Palette.Leaf, topLeft = Offset(5.5f * s, 8.8f * s), size = Size(8f * s, 4.4f * s))
        }
        rotate(30f, pivot = Offset(16.5f * s, 9f * s)) {
            drawOval(Palette.Sun, topLeft = Offset(12.5f * s, 6.8f * s), size = Size(8f * s, 4.4f * s))
        }
    }
}

@Composable
fun Wordmark(size: Int = 22) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Logo((size + 4).dp)
        Text("offgrow", style = display(size, tracking = -0.03))
    }
}

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.height(32.dp), contentAlignment = Alignment.CenterStart) { Wordmark() }
            Image(
                painter = painterResource(R.drawable.hero_garden),
                contentDescription = "A cottage garden in full bloom with a picket fence, rose arch and stepping-stone path",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.9f)
                    .clip(RoundedCornerShape(topStart = 171.dp, topEnd = 171.dp, bottomStart = 28.dp, bottomEnd = 28.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Grow what you don't scroll.", style = display(40, tracking = -0.035, lineHeight = 1.02))
                Text(
                    "A garden that's yours. Every good day away from your phone plants a new flower. Doomscroll and it wilts.",
                    style = body(16, color = Palette.Body, lineHeight = 1.45),
                )
            }
        }
        PrimaryButton("Plant my garden", onStart, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun CheckLine(text: String, good: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(
            painterResource(if (good) R.drawable.ic_check else R.drawable.ic_close),
            contentDescription = null,
            tint = if (good) Palette.Moss else Palette.Red,
            modifier = Modifier.padding(top = 1.dp).size(20.dp),
        )
        Text(text, style = body(15, lineHeight = 1.4))
    }
}

@Composable
fun PermissionScreen(onBack: () -> Unit, onGrant: () -> Unit, onAppInfo: () -> Unit, granted: Boolean, onContinue: () -> Unit) {
    var tried by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton44(R.drawable.ic_back, "Back", onBack, Modifier.padding(end = 0.dp))
            Text("Step 1 of 2", style = body(13, FontWeight.SemiBold, Palette.Muted))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("One permission.\nZero spying.", style = display(38, tracking = -0.035, lineHeight = 1.04))
                Text(
                    "Your garden grows from how long you spend in social apps. It's counted on your phone and never leaves it.",
                    style = body(16, color = Palette.Body, lineHeight = 1.5),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.Sage)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Offgrow counts", style = display(18, FontWeight.Bold, tracking = 0.0))
                CheckLine("Total minutes in social apps, as one number", true)
                CheckLine("Whether your phone was used in your night window", true)
                CheckLine("When you first pick up your phone each morning", true)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.Card)
                    .border(1.dp, Palette.Border, RoundedCornerShape(20.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Offgrow never sees", style = display(18, FontWeight.Bold, tracking = 0.0))
                CheckLine("What you watch, post or chat about", false)
                CheckLine("Your contacts, photos or location", false)
                CheckLine("Your app-by-app history, on any server", false)
            }
            if (tried && !granted) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Palette.Butter)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Switch greyed out?", style = display(18, FontWeight.Bold, tracking = 0.0))
                    Text(
                        "Android may block this for apps installed outside the Play Store. Open App info, tap the ⋮ menu, choose \"Allow restricted settings\", then try again.",
                        style = body(14, color = Palette.InkSoft),
                    )
                    OutlineButton("Open App info", onAppInfo, Modifier.fillMaxWidth(), height = 46.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            if (granted) {
                PrimaryButton("Continue", onContinue, container = Palette.Moss)
                Text(
                    "Usage access is on. Thank you.",
                    style = body(13, color = Palette.Muted),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PrimaryButton("Grant usage access", {
                    tried = true
                    onGrant()
                })
                Text(
                    "Opens Android Settings. Find Offgrow, turn it on, then come back.",
                    style = body(13, color = Palette.Muted),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

val LIMIT_CHOICES = listOf(30, 45, 60, 90, 120)

@Composable
fun SetupScreen(onBack: () -> Unit, onDone: (name: String, limit: Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableIntStateOf(60) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton44(R.drawable.ic_back, "Back", onBack)
            Text("Step 2 of 2", style = body(13, FontWeight.SemiBold, Palette.Muted))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Make it yours.", style = display(38, tracking = -0.035, lineHeight = 1.04))
                Text(
                    "Every garden is different. Yours is drawn from a seed only your phone has.",
                    style = body(16, color = Palette.Body, lineHeight = 1.5),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your name (optional)", style = body(14, FontWeight.Bold))
                TextInput(name, { name = it.take(40) }, "So we can call it your garden")
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Daily social limit", style = body(14, FontWeight.Bold))
                Text(
                    "Stay under it and your garden is watered. Go over and it starts to wilt.",
                    style = body(14, color = Palette.Muted),
                )
                LimitChips(limit) { limit = it }
            }
        }
        PrimaryButton("Plant my first flower", { onDone(name, limit) }, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun LimitChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        LIMIT_CHOICES.forEach { m ->
            ChoiceChip(
                text = if (m % 60 == 0) "${m / 60}h" else "${m}m",
                selected = m == selected,
                onClick = { onSelect(m) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
