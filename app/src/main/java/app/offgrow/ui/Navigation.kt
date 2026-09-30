package app.offgrow.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private object Screen {
    const val WELCOME = "welcome"
    const val PERMISSION = "permission"
    const val SETUP = "setup"
    const val HOME = "home"
    const val FOCUS = "focus"
    const val JOURNAL = "journal"
    const val SETTINGS = "settings"
    const val PLANT = "plant"
    const val FLOWER = "flower"
    const val APPS = "apps"
    const val PREVIEW = "preview"
}

@Composable
fun OffgrowRoot(vm: AppViewModel, openUsageSettings: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val state = ui.state
    if (!ui.ready || state == null) {
        Box(Modifier.fillMaxSize().background(Palette.Paper))
        return
    }

    var screen by rememberSaveable { mutableStateOf(if (state.onboarded) Screen.HOME else Screen.WELCOME) }
    var flowerId by rememberSaveable { mutableStateOf("") }
    var flowerBack by rememberSaveable { mutableStateOf(Screen.HOME) }
    var focusRunning by remember { mutableStateOf(false) }

    // Dark status-bar icons everywhere except the dark focus screen.
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = screen != Screen.FOCUS
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = screen != Screen.FOCUS
    }

    // Once usage access is granted, move on from the permission step by itself.
    LaunchedEffect(ui.hasAccess, screen) {
        if (screen == Screen.PERMISSION && ui.hasAccess) screen = Screen.SETUP
    }

    fun openFlower(id: String, from: String) {
        flowerId = id
        flowerBack = from
        screen = Screen.FLOWER
    }

    val tab = when (screen) {
        Screen.HOME -> Tab.GARDEN
        Screen.FOCUS -> Tab.FOCUS
        Screen.JOURNAL -> Tab.JOURNAL
        Screen.SETTINGS -> Tab.SETTINGS
        else -> null
    }

    BackHandler(enabled = screen != Screen.HOME && screen != Screen.WELCOME) {
        screen = when (screen) {
            Screen.PERMISSION -> Screen.WELCOME
            Screen.SETUP -> if (ui.hasAccess) Screen.WELCOME else Screen.PERMISSION
            Screen.FLOWER -> flowerBack
            Screen.APPS, Screen.PREVIEW -> Screen.SETTINGS
            Screen.FOCUS -> if (focusRunning) Screen.FOCUS else Screen.HOME
            else -> Screen.HOME
        }
    }

    Column(Modifier.fillMaxSize().background(if (screen == Screen.FOCUS) Palette.Night else Palette.Paper)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (screen) {
                Screen.WELCOME -> WelcomeScreen(onStart = {
                    screen = if (ui.hasAccess) Screen.SETUP else Screen.PERMISSION
                })
                Screen.PERMISSION -> PermissionScreen(
                    onBack = { screen = Screen.WELCOME },
                    onGrant = openUsageSettings,
                    granted = ui.hasAccess,
                    onContinue = { screen = Screen.SETUP },
                )
                Screen.SETUP -> SetupScreen(
                    onBack = { screen = if (ui.hasAccess) Screen.WELCOME else Screen.PERMISSION },
                    onDone = { name, limit ->
                        vm.startGarden(name, limit)
                        screen = Screen.PLANT
                    },
                )
                Screen.HOME -> HomeScreen(
                    ui = ui,
                    onPlant = { screen = Screen.PLANT },
                    onGrantAccess = openUsageSettings,
                    onFocus = { screen = Screen.FOCUS },
                )
                Screen.PLANT -> PlantScreen(
                    state = state,
                    onClose = { screen = Screen.HOME },
                    onPlant = { kind, note ->
                        val first = state.flowers.isEmpty()
                        vm.plant(kind, note) { id ->
                            if (first) screen = Screen.HOME else openFlower(id, Screen.HOME)
                        }
                    },
                )
                Screen.FLOWER -> FlowerScreen(
                    state = state,
                    flowerId = flowerId,
                    garden = ui.garden,
                    onBack = { screen = flowerBack },
                    onRename = { vm.renameFlower(flowerId, it) },
                )
                Screen.FOCUS -> FocusScreen(
                    state = state,
                    onComplete = { vm.completeFocus() },
                    onExit = { screen = Screen.HOME },
                    onRunningChange = { focusRunning = it },
                )
                Screen.JOURNAL -> JournalScreen(ui = ui, onOpenFlower = { openFlower(it, Screen.JOURNAL) })
                Screen.SETTINGS -> SettingsScreen(
                    ui = ui,
                    vm = vm,
                    onApps = { screen = Screen.APPS },
                    onPreview = { screen = Screen.PREVIEW },
                    onGrantAccess = openUsageSettings,
                )
                Screen.APPS -> AppsScreen(vm = vm, onBack = { screen = Screen.SETTINGS })
                Screen.PREVIEW -> PreviewScreen(vm = vm, onBack = { screen = Screen.SETTINGS })
                else -> LaunchedEffect(screen) { screen = Screen.HOME }
            }
        }
        if (tab != null && !(screen == Screen.FOCUS && focusRunning)) {
            BottomNav(current = tab, dark = screen == Screen.FOCUS, onSelect = { t ->
                screen = when (t) {
                    Tab.GARDEN -> Screen.HOME
                    Tab.FOCUS -> Screen.FOCUS
                    Tab.JOURNAL -> Screen.JOURNAL
                    Tab.SETTINGS -> Screen.SETTINGS
                }
            })
        }
    }
}
