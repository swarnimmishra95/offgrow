package app.offgrow.qa

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Rotation, big text, a small phone, and dark mode. */
@RunWith(AndroidJUnit4::class)
class A9_DeviceConditionsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val today = LocalDate.of(2026, 10, 14)

    private fun home() {
        Qa.seed(Qa.onboardedState(today.minusDays(12), vitality = 76, flowers = 7, streak = 4, best = 6, seeds = 1).copy(seedDay = today.minusDays(1).toString()))
        Qa.fake.days[today] = Qa.stats(41)
    }

    private fun displayed(text: String): Boolean = try {
        compose.onNodeWithText(text).assertIsDisplayed()
        true
    } catch (_: Throwable) {
        false
    }

    @Test
    fun conditions() {
        Qa.section("A9 · Rotation, large text, small screen, dark mode")

        // Rotation keeps state and doesn't crash.
        Qa.reset(today, 12)
        home()
        Qa.launchApp()
        Qa.waitText(compose, "Today's care", 20_000)
        Qa.settle(compose)
        compose.onAllNodesWithText("Plant").onFirst().performClick()
        Qa.waitText(compose, "You earned a seed")
        compose.onNodeWithText("Cosmos").performClick()
        Qa.device.setOrientationLeft()
        SystemClock.sleep(2500)
        Qa.check("Still on the planting screen after rotating", Qa.waitText(compose, "You earned a seed"))
        Qa.check("Choice kept after rotating", Qa.exists(compose, "Plant cosmos"))
        Qa.shot("landscape_plant", "Planting screen, rotated")
        Qa.device.pressBack()
        Qa.waitText(compose, "Today's care")
        Qa.settle(compose)
        Qa.shot("landscape_home", "Home, rotated")
        Qa.device.setOrientationNatural()
        Qa.device.unfreezeRotation()
        SystemClock.sleep(1500)
        Qa.closeApp()

        // Large text: the welcome button must stay reachable.
        Qa.shell("settings put system font_scale 1.3")
        SystemClock.sleep(1000)
        Qa.reset(today, 12)
        Qa.launchApp()
        Qa.waitText(compose, "Grow what you don't scroll.")
        Qa.check("Welcome button visible with large text", displayed("Plant my garden"))
        Qa.shot("large_text_welcome", "Welcome at 130% text size")
        Qa.closeApp()
        Qa.reset(today, 12)
        home()
        Qa.launchApp()
        Qa.waitText(compose, "Today's care", 20_000)
        Qa.settle(compose)
        Qa.shot("large_text_home", "Home at 130% text size")
        Qa.closeApp()
        Qa.shell("settings put system font_scale 1.0")

        // A small phone (720×1280 at 320dpi, like older budget phones).
        Qa.shell("wm size 720x1280")
        Qa.shell("wm density 320")
        SystemClock.sleep(2000)
        Qa.reset(today, 12)
        Qa.launchApp()
        Qa.waitText(compose, "Grow what you don't scroll.")
        Qa.check("Welcome button visible on a small screen", displayed("Plant my garden"))
        Qa.shot("small_welcome", "Welcome on a small phone")
        compose.onNodeWithText("Plant my garden").performClick()
        Qa.waitText(compose, "Make it yours.")
        Qa.check("Setup button visible on a small screen", displayed("Plant my first flower"))
        Qa.shot("small_setup", "Setup on a small phone")
        Qa.closeApp()
        Qa.reset(today, 12)
        home()
        Qa.launchApp()
        Qa.waitText(compose, "Today's care", 20_000)
        Qa.settle(compose)
        Qa.shot("small_home", "Home on a small phone")
        compose.onAllNodesWithText("Focus").onFirst().performClick()
        Qa.waitText(compose, "Start focus")
        Qa.check("Focus button visible on a small screen", displayed("Start focus"))
        Qa.shot("small_focus", "Focus on a small phone")
        Qa.closeApp()
        Qa.shell("wm size reset")
        Qa.shell("wm density reset")
        SystemClock.sleep(2000)

        // Dark mode: the app keeps its own light look and readable status bar.
        Qa.shell("cmd uimode night yes")
        SystemClock.sleep(1500)
        Qa.reset(today, 12)
        home()
        Qa.launchApp()
        Qa.waitText(compose, "Today's care", 20_000)
        Qa.settle(compose)
        Qa.shot("dark_mode_home", "Home with the phone in dark mode")
        compose.onAllNodesWithText("Journal").onFirst().performClick()
        Qa.waitText(compose, "Garden journal")
        Qa.shot("dark_mode_journal", "Journal with the phone in dark mode")
        Qa.closeApp()
        Qa.shell("cmd uimode night no")
        Qa.finish()
    }
}
