package app.offgrow.qa

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.offgrow.garden.AppClock
import app.offgrow.garden.FocusConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Focus sessions: finish, leave the app (fails), lock the phone (fine), back button, daily cap. */
@RunWith(AndroidJUnit4::class)
class A4_FocusTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun sessions() = Qa.state().focusToday(AppClock.today())

    @Test
    fun focusSessions() {
        val today = LocalDate.of(2026, 10, 14)
        Qa.reset(today, 15)
        Qa.section("A4 · Focus sessions")
        Qa.seed(Qa.onboardedState(today.minusDays(9), vitality = 60, flowers = 4))
        Qa.fake.days[today] = Qa.stats(30)
        FocusConfig.durationMs = 8_000

        Qa.launchApp()
        Qa.waitText(compose, "Today's care", 20_000)
        Qa.settle(compose)
        compose.onAllNodesWithText("Focus").onFirst().performClick()
        Qa.check("Focus screen", Qa.waitText(compose, "Start focus"))
        Qa.check("Explains the reward", Qa.exists(compose, "Each session adds +3", substring = true))
        Qa.shot("focus_idle", "Focus, before starting")

        // 1. Complete a session.
        compose.onNodeWithText("Start focus").performClick()
        Qa.check("Timer running, give-up offered", Qa.waitText(compose, "Give up"))
        Qa.check("Tab bar hidden while running", !Qa.exists(compose, "Journal"))
        Qa.shot("focus_running", "Session running")
        Qa.device.pressBack()
        SystemClock.sleep(500)
        Qa.check("Back button doesn't leave a running session", Qa.exists(compose, "Give up"))
        Qa.check("Session completes", Qa.waitText(compose, "Done. Your garden grew +3.", 20_000))
        Qa.shot("focus_done", "Session complete")
        Qa.waitEngineIdle()
        Qa.checkEq("One session recorded", 1, sessions())

        compose.onNodeWithText("Back to garden").performClick()
        Qa.waitText(compose, "Today's care")
        Qa.settle(compose)
        // 60 + sun 5 + roots 5 + focus 3
        Qa.check("Home vitality includes the session (73)", Qa.exists(compose, "73"))
        compose.onNodeWithText("Today's care").performScrollTo()
        Qa.check("Focus row on home", Qa.exists(compose, "Focus sessions"))
        Qa.shot("home_after_focus", "Home after one focus session")

        // 2. Leaving the app ends the session.
        compose.onAllNodesWithText("Focus").onFirst().performClick()
        Qa.waitText(compose, "Start focus")
        FocusConfig.durationMs = 60_000
        compose.onNodeWithText("Start focus").performClick()
        Qa.waitText(compose, "Give up")
        Qa.device.pressHome()
        SystemClock.sleep(3500)
        Qa.bringToFront()
        Qa.check("Leaving the app fails the session", Qa.waitText(compose, "The session ended when you left Offgrow."))
        Qa.shot("focus_failed", "After switching away")
        Qa.checkEq("Failed session not counted", 1, sessions())

        // 3. Locking the phone is allowed.
        compose.onNodeWithText("Start another").performClick()
        Qa.waitText(compose, "Give up")
        FocusConfig.durationMs = 20_000
        Qa.device.sleep()
        SystemClock.sleep(6000)
        Qa.device.wakeUp()
        Qa.shell("wm dismiss-keyguard")
        SystemClock.sleep(2500)
        if (Qa.device.currentPackageName != "app.offgrow") Qa.bringToFront()
        Qa.check("Locking the phone doesn't fail it", !Qa.exists(compose, "The session ended when you left Offgrow."))
        Qa.check("Session finishes after unlocking", Qa.waitText(compose, "Done. Your garden grew +3.", 30_000))
        Qa.shot("focus_after_lock", "Finished with the phone locked part of the time")
        Qa.waitEngineIdle()
        Qa.checkEq("Two sessions recorded", 2, sessions())

        // 4. Give up.
        compose.onNodeWithText("Start another").performClick()
        Qa.waitText(compose, "Give up")
        compose.onNodeWithText("Give up").performClick()
        Qa.check("Give up ends it", Qa.waitText(compose, "The session ended when you left Offgrow."))
        Qa.checkEq("Given-up session not counted", 2, sessions())

        // 5. Daily cap: sessions 3, 4 and 5; only three count.
        FocusConfig.durationMs = 3_000
        repeat(3) {
            compose.onNodeWithText("Start another").performClick()
            Qa.waitText(compose, "Done.", 15_000, substring = true)
            Qa.waitEngineIdle()
        }
        Qa.checkEq("All five finished sessions recorded", 5, sessions())
        Qa.check("Says the cap is reached", Qa.exists(compose, "Today's focus rewards are used up", substring = true))
        Qa.shot("focus_capped", "After the daily cap")
        compose.onNodeWithText("Back to garden").performClick()
        Qa.waitText(compose, "Today's care")
        Qa.settle(compose)
        // 60 + 5 + 5 + 9 (capped at three sessions)
        Qa.check("Only three sessions pay out (79)", Qa.exists(compose, "79"))
        Qa.closeApp()
        Qa.finish()
    }
}
