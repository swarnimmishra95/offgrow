package app.offgrow.qa

import android.os.SystemClock
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.offgrow.data.Store
import app.offgrow.garden.Flower
import app.offgrow.garden.GardenEngine
import app.offgrow.garden.Rules
import app.offgrow.usage.UsageReader
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Planting from an earned seed, flower detail, journal, settings, apps list and mood preview. */
@RunWith(AndroidJUnit4::class)
class A5_ScreensTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    init {
        Qa.compose = compose
    }

    private val today = LocalDate.of(2026, 10, 14)

    /** Ten real days through the engine so the journal has history. */
    private fun liveTenDays() {
        val start = today.minusDays(10)
        Qa.reset(start, 9)
        Qa.seed(Qa.onboardedState(start, vitality = 70, flowers = 1, lastClosed = start.minusDays(1)).copy(flowers = listOf(
            Flower("first", "poppy", "Poppy, ${start.dayOfMonth} Oct", "Walked to the lake instead", start.toString()),
        )))
        val usage = listOf(25, 40, 95, 20, 30, 150, 45, 35, 20, 28)
        for (i in 0 until 10) {
            val d = start.plusDays(i.toLong())
            Qa.fake.days[d] = if (usage[i] > 60) Qa.stats(usage[i], sunlight = false, nightMin = 20) else Qa.stats(usage[i])
            Qa.setNow(d.plusDays(1), 7)
            runBlocking { GardenEngine.refresh(Qa.ctx, render = false) }
            if (i < 6) {
                // Plant most seeds, leave the last ones for the planting screen.
                while (Qa.state().pendingSeeds > 0) {
                    val n = Qa.state().flowers.size
                    val kind = listOf("sunflower", "cornflower", "daisy", "cosmos", "lavender", "marigold")[n % 6]
                    runBlocking { GardenEngine.update(Qa.ctx) { s -> Rules.plant(s, Flower("p$n", kind, "${Rules.plantable(kind).label}, ${d.plusDays(1).dayOfMonth} Oct", "", d.plusDays(1).toString())) } }
                }
            }
        }
        Qa.fake.days[today] = Qa.stats(22)
        Qa.setNow(today, 12)
    }

    @Test
    fun screens() {
        liveTenDays()
        Qa.section("A5 · Planting, flower detail, journal, settings")
        val before = Qa.state()
        Qa.check("Has seeds to plant", before.pendingSeeds > 0, "seeds ${before.pendingSeeds}")

        Qa.launchApp()
        Qa.waitText(compose, "Today's care", 20_000)
        Qa.settle(compose)
        Qa.shot("home_ten_days", "After ten mixed days, seeds waiting")

        // Plant from an earned seed.
        compose.onAllNodesWithText("Plant").onFirst().performClick()
        Qa.check("Planting screen", Qa.waitText(compose, "You earned a seed"))
        Qa.check("Shows the good day's stats", Qa.exists(compose, "phone-free"))
        Qa.shot("plant_seed", "Earned seed, with the good day's stats")
        val best = Qa.state().bestStreak
        val tulipLocked = best < 3
        compose.onNodeWithText("Tulip").performScrollTo().performClick()
        Qa.check(
            "Tulip locked below a 3-day streak",
            if (tulipLocked) !Qa.exists(compose, "Plant tulip") else Qa.exists(compose, "Plant tulip"),
            "best streak $best",
        )
        compose.onNodeWithText("Hydrangea").performScrollTo()
        Qa.check("Hydrangea shows its unlock", Qa.exists(compose, "14-day streak"))
        Qa.shot("plant_locked", "Locked flowers show the streak needed")
        compose.onNodeWithText("Lavender").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Board games with the cousins")
        compose.onNodeWithText("Plant lavender").performClick()
        Qa.check("Opens the new flower", Qa.waitText(compose, "Back to garden", 15_000))
        Qa.waitEngineIdle()
        Qa.settle(compose)
        Qa.check("Shows the note", Qa.exists(compose, "“Board games with the cousins”"))
        Qa.check("Planted today", Qa.exists(compose, "Planted today", substring = true))
        Qa.shot("flower_new", "New flower's page")
        val after = Qa.state()
        Qa.checkEq("One seed used", before.pendingSeeds - 1, after.pendingSeeds)
        Qa.checkEq("Lavender added", "lavender", after.flowers.last().kind)

        // Rename it.
        compose.onNodeWithText("Rename").performClick()
        Qa.waitText(compose, "Name this flower")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("Cousins' lavender")
        Qa.check("Typed name shows in the field", Qa.waitText(compose, "Cousins' lavender"))
        Qa.shot("flower_rename", "Rename dialog")
        compose.onNodeWithText("Save").performClick()
        Qa.check("New name on screen", Qa.waitText(compose, "Cousins' lavender"))
        SystemClock.sleep(800)
        Qa.checkEq("New name saved", "Cousins' lavender", Qa.state().flowers.last().name)
        compose.onNodeWithText("Back to garden").performClick()
        Qa.waitText(compose, "Today's care")

        // Journal.
        compose.onAllNodesWithText("Journal").onFirst().performClick()
        Qa.check("Journal", Qa.waitText(compose, "Garden journal"))
        Qa.check("Week summary", Qa.exists(compose, "vitality"))
        Qa.check("Flowers row", Qa.exists(compose, "Your flowers".uppercase()))
        Qa.check("Yesterday listed", Qa.exists(compose, "Yesterday"))
        run {
            val st = Qa.state()
            val weekStart = today.minusDays(6)
            val baseline = st.days.lastOrNull { LocalDate.parse(it.day).isBefore(weekStart) }?.vitalityEnd ?: Rules.START_VITALITY
            val live = Store(Qa.ctx).widgetInfo()?.vitality ?: 0
            val net = live - baseline
            val shown = if (net > 0) "+$net" else if (net < 0) "−${-net}" else "0"
            Qa.check("Week change is the real net change ($shown)", Qa.exists(compose, shown), "live $live, before the week $baseline")
        }
        Qa.shot("journal", "Journal")
        compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp(durationMillis = 300) }
        Qa.shot("journal_days", "Journal, day by day")
        compose.onNodeWithText("Poppy, ${today.minusDays(10).dayOfMonth} Oct").performScrollTo().performClick()
        Qa.check("Opens an older flower", Qa.waitText(compose, "“Walked to the lake instead”"))
        Qa.shot("flower_old", "The first flower, opened from the journal")
        Qa.device.pressBack()
        Qa.check("Back returns to the journal", Qa.waitText(compose, "Garden journal"))

        // Settings.
        compose.onAllNodesWithText("Settings").onFirst().performClick()
        Qa.check("Settings", Qa.waitText(compose, "How your garden grows".uppercase()))
        Qa.shot("settings", "Settings")
        compose.onNodeWithText("90m").performClick()
        SystemClock.sleep(800)
        Qa.checkEq("Limit changed to 90", 90, Qa.state().limitMin)
        compose.onNodeWithText("Name").performClick()
        Qa.waitText(compose, "Your name")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("Swarnim M")
        compose.onNodeWithText("Save").performClick()
        SystemClock.sleep(800)
        Qa.checkEq("Name changed", "Swarnim M", Qa.state().name)
        val keyBefore = Store(Qa.ctx).renderKey
        compose.onNodeWithContentDescription("Sage fence").performClick()
        SystemClock.sleep(800)
        Qa.checkEq("Fence changed", "sage", Qa.state().fence)
        Qa.waitEngineIdle()
        Qa.check("Garden redrawn with the new fence", Store(Qa.ctx).renderKey != keyBefore && Store(Qa.ctx).renderKey?.contains("sage") == true)
        compose.onNodeWithText("Privacy".uppercase()).performScrollTo()
        Qa.shot("settings_bottom", "Settings, rules and privacy")

        // Apps that count.
        compose.onNodeWithText("Apps that count").performScrollTo().performClick()
        Qa.check("Apps list loads", Qa.waitFor(15_000, "apps") { compose.onAllNodes(isToggleable()).fetchSemanticsNodes().isNotEmpty() })
        Qa.shot("apps", "Apps that count")
        val reader = UsageReader(Qa.ctx)
        val apps = reader.launchableApps()
        val target = apps.firstOrNull { it.second in listOf("Clock", "Calendar", "Chrome", "Contacts", "Files", "Camera") && !reader.isSocialByDefault(it.first) }
            ?: apps.firstOrNull { !reader.isSocialByDefault(it.first) }
        Qa.check("Found a non-social app to toggle", target != null, "${apps.size} apps listed")
        Qa.check("Messaging apps aren't counted by default", !reader.isSocialByDefault("com.google.android.apps.messaging") && !reader.isSocialByDefault("com.whatsapp"))
        Qa.check("YouTube is counted by default", reader.isSocialByDefault("com.google.android.youtube"))
        if (target != null) {
            compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(target.second))
            compose.onAllNodesWithText(target.second).onFirst().performClick()
            SystemClock.sleep(800)
            Qa.check("Turning ${target.second} on makes it count", target.first in Store(Qa.ctx).includedApps)
            Qa.shot("apps_toggled", "${target.second} switched on")
            compose.onAllNodesWithText(target.second).onFirst().performClick()
            SystemClock.sleep(800)
            Qa.check("Turning it off again removes it", target.first !in Store(Qa.ctx).includedApps)
        }
        Qa.device.pressBack()
        Qa.check("Back to settings", Qa.waitText(compose, "Apps that count"))

        // Mood preview.
        compose.onNodeWithText("Preview moods").performScrollTo().performClick()
        Qa.check("Preview screen", Qa.waitText(compose, "Preview moods"))
        for ((mood, tod) in listOf("Thriving" to "Day", "Healthy" to "Dawn", "Holding" to "Dusk", "Wilting" to "Night", "Wilting" to "Day", "Thriving" to "Night")) {
            compose.onNodeWithText(mood).performClick()
            compose.onNodeWithText(tod).performClick()
            Qa.waitFor(30_000, "preview") { !Qa.exists(compose, "Drawing…") }
            SystemClock.sleep(500)
            Qa.shot("preview_${mood.lowercase()}_${tod.lowercase()}", "Preview: $mood at $tod")
        }
        Qa.closeApp()
        Qa.finish()
    }
}
