package app.offgrow.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.offgrow.BuildConfig
import app.offgrow.R
import app.offgrow.garden.Rules

private val FENCES = listOf(
    "white" to Color(0xFFF7F4EC),
    "wood" to Color(0xFFB88A5C),
    "sage" to Color(0xFFB8CCB2),
    "blue" to Color(0xFFC4D6E6),
)

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Card)
            .border(1.dp, Palette.Border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun LinkRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = body(15, FontWeight.SemiBold))
            Text(detail, style = body(13, color = Palette.Muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingsScreen(
    ui: UiState,
    vm: AppViewModel,
    onApps: () -> Unit,
    onPreview: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    val state = ui.state ?: return
    var editingName by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Paper)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = display(30), modifier = Modifier.padding(top = 8.dp))

        Card {
            SectionLabel("Your garden")
            LinkRow("Name", state.name.ifBlank { "Not set" }) { editingName = true }
            Text("Daily social limit", style = body(15, FontWeight.SemiBold))
            LimitChips(state.limitMin) { vm.setLimit(it) }
            Text("Fence", style = body(15, FontWeight.SemiBold))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FENCES.forEach { (key, color) ->
                    val selected = state.fence == key
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(if (selected) 3.dp else 1.dp, if (selected) Palette.Moss else Palette.Border, CircleShape)
                            .clickable(role = Role.RadioButton, onClickLabel = "Use the $key fence") { vm.setFence(key) }
                            .semantics { contentDescription = "${key.replaceFirstChar { it.uppercase() }} fence" },
                    )
                }
            }
        }

        Card {
            SectionLabel("Tracking")
            LinkRow("Apps that count", "Choose which apps count as social") { onApps() }
            LinkRow(
                "Usage access",
                if (ui.hasAccess) "On. Counted on this phone only." else "Off. Your garden can't grow without it.",
            ) { onGrantAccess() }
        }

        Card {
            SectionLabel("See your garden")
            LinkRow("Preview moods", "See how your garden looks thriving, holding on or wilting, at any time of day") { onPreview() }
        }

        Card {
            SectionLabel("How your garden grows")
            RuleLine("Stay under your social limit", "+${Rules.WATER}")
            RuleLine("No social apps for an hour after waking", "+${Rules.SUNLIGHT}")
            RuleLine("A clean night, 11pm to 6am", "+${Rules.ROOTS}")
            RuleLine("Each ${Rules.FOCUS_MINUTES}-minute focus session (up to ${Rules.FOCUS_MAX_SESSIONS})", "+${Rules.FOCUS}")
            RuleLine("A good day after a rough one", "+${Rules.COMEBACK}")
            RuleLine("Phone used in your night window", signed(Rules.WEEDS))
            RuleLine("Over your limit (by up to 30m, 60m, more)", "−5 / −10 / −20")
            Text(
                "Every good day earns a seed to plant. After ${Rules.LOST_AFTER_LOW_DAYS} days in a row below ${Rules.LOW_VITALITY} vitality, your newest flower is lost.",
                style = body(13, color = Palette.Muted),
            )
        }

        Card {
            SectionLabel("Privacy")
            Text(
                "Offgrow reads screen-time totals on your phone and keeps everything here. No account, no servers, no ads. Uninstalling removes it all.",
                style = body(14, color = Palette.InkSoft),
            )
        }

        Text("Offgrow ${BuildConfig.VERSION_NAME}", style = body(12, color = Palette.Muted), modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(8.dp))
    }

    if (editingName) {
        var text by remember { mutableStateOf(state.name) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Your name", style = display(22)) },
            text = { TextInput(text, { text = it.take(40) }, "So we can call it your garden") },
            confirmButton = {
                TextButton(onClick = {
                    vm.setName(text)
                    editingName = false
                }) { Text("Save", style = body(15, FontWeight.Bold, Palette.Moss)) }
            },
            dismissButton = {
                TextButton(onClick = { editingName = false }) { Text("Cancel", style = body(15, FontWeight.SemiBold, Palette.Muted)) }
            },
            containerColor = Palette.Paper,
        )
    }
}

@Composable
private fun RuleLine(text: String, delta: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, style = body(14), modifier = Modifier.weight(1f))
        Text(delta, style = body(14, FontWeight.Bold, if (delta.startsWith("+")) Palette.Moss else Palette.Red))
    }
}

@Composable
fun AppsScreen(vm: AppViewModel, onBack: () -> Unit) {
    var apps by remember { mutableStateOf<List<AppChoice>?>(null) }
    LaunchedEffect(Unit) { apps = vm.appChoices() }

    Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 24.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton44(R.drawable.ic_back, "Back", onBack)
            Text("Apps that count", style = display(22), modifier = Modifier.padding(start = 4.dp))
        }
        Text(
            "Time in these apps counts toward your daily social limit. Social apps are on by default.",
            style = body(14, color = Palette.Muted),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        val list = apps
        if (list == null) {
            Text("Loading your apps…", style = body(15, color = Palette.Muted), modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            ) {
                items(list, key = { it.pkg }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .toggleable(value = app.counted, role = Role.Switch) { on ->
                                apps = list.map { if (it.pkg == app.pkg) it.copy(counted = on) else it }
                                vm.setCounted(app.pkg, on)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(app.label, style = body(15, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Switch(
                            checked = app.counted,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Palette.Paper,
                                checkedTrackColor = Palette.Moss,
                                uncheckedThumbColor = Palette.Muted,
                                uncheckedTrackColor = Palette.Track,
                                uncheckedBorderColor = Palette.Track,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private val MOODS = listOf(92 to "Thriving", 72 to "Healthy", 48 to "Holding", 22 to "Wilting")
private val TODS = listOf("dawn" to "Dawn", "day" to "Day", "dusk" to "Dusk", "night" to "Night")

@Composable
fun PreviewScreen(vm: AppViewModel, onBack: () -> Unit) {
    var mood by rememberSaveable { mutableStateOf(92) }
    var tod by rememberSaveable { mutableStateOf("day") }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(mood, tod) {
        loading = true
        val img = vm.preview(mood, tod)
        if (img != null) image = img
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 24.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton44(R.drawable.ic_back, "Back", onBack)
            Text("Preview moods", style = display(22), modifier = Modifier.padding(start = 4.dp))
        }
        Box(
            Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Track),
            contentAlignment = Alignment.Center,
        ) {
            val img = image
            if (img != null) {
                Image(img, contentDescription = "Your garden at vitality $mood, $tod", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (loading) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.Paper.copy(alpha = 0.92f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) { Text("Drawing…", style = body(13, FontWeight.Bold)) }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mood", style = body(14, FontWeight.Bold))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MOODS.forEach { (v, label) -> ChoiceChip(label, mood == v, { mood = v }, Modifier.weight(1f)) }
            }
            Text("Time of day", style = body(14, FontWeight.Bold), modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TODS.forEach { (k, label) -> ChoiceChip(label, tod == k, { tod = k }, Modifier.weight(1f)) }
            }
            Text(
                "This is your own garden, drawn with your flowers. Your real garden follows your days.",
                style = body(13, color = Palette.Muted),
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
            )
        }
    }
}
