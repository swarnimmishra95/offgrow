package app.offgrow.qa

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.offgrow.data.Store
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Home in every mood, time of day, and edge state. */
@RunWith(AndroidJUnit4::class)
class A2_HomeStatesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    init {
        Qa.compose = compose
    }

    private val today = LocalDate.of(2026, 10, 14)

    private fun open(caption: String, name: String) {
        Qa.launchApp()
        Qa.waitText(compose, "Today's care", 20_000)
        Qa.settle(compose)
        Qa.shot(name, caption)
    }

    @Test
    fun moods() {
        Qa.section("A2 · Home in each mood")

        // Thriving: long streak, full garden, well under the limit.
        Qa.reset(today, 13)
        Qa.seed(Qa.onboardedState(today.minusDays(40), vitality = 82, flowers = 12, streak = 12, best = 15))
        Qa.fake.days[today] = Qa.stats(38)
        open("Thriving, 12 flowers, 38m of 60m", "home_thriving")
        Qa.check("Thriving headline", Qa.exists(compose, "Your garden is blooming"))
        Qa.check("Vitality 92 (82 + sun 5 + roots 5)", Qa.exists(compose, "92"))
        Qa.check("Streak card", Qa.exists(compose, "12 days"))
        Qa.check("Best streak", Qa.exists(compose, "best 15"))
        Qa.check("Day 41 badge", Qa.exists(compose, "Day 41"))
        compose.onNodeWithText("Today's care").performScrollTo()
        Qa.check("Next seed promised tonight", Qa.exists(compose, "tonight"))
        Qa.shot("home_thriving_care", "Thriving, care list")
        Qa.closeApp()

        // Healthy with a seed waiting.
        Qa.reset(today, 13)
        Qa.seed(Qa.onboardedState(today.minusDays(10), vitality = 62, flowers = 5, streak = 3, seeds = 1).copy(seedDay = today.minusDays(1).toString()))
        Qa.fake.days[today] = Qa.stats(25)
        open("Healthy with a seed waiting", "home_seed")
        Qa.check("Seed banner", Qa.exists(compose, "You earned a seed"))
        Qa.check("Healthy headline", Qa.exists(compose, "Your garden is healthy"))
        compose.onAllNodesWithText("Plant").onFirst().performClick()
        Qa.check("Seed banner opens planting", Qa.waitText(compose, "What did you do instead of scrolling?"))
        Qa.closeApp()

        // Holding on: over the limit today.
        Qa.reset(today, 18)
        Qa.seed(Qa.onboardedState(today.minusDays(20), vitality = 48, flowers = 6, streak = 0, best = 9))
        Qa.fake.days[today] = Qa.stats(72, sunlight = false)
        open("Holding on, 12m over the limit, dusk", "home_holding")
        Qa.check("Holding headline", Qa.exists(compose, "Your garden is holding on"))
        // 48 − 5 (12m over) + 0 sun + 5 roots = 48
        Qa.check("Vitality 48", Qa.exists(compose, "48"))
        compose.onNodeWithText("Today's care").performScrollTo()
        Qa.check("Wilting row shows the overage", Qa.exists(compose, "12m over your 1h limit"))
        Qa.check("No morning sun row", Qa.exists(compose, "No morning sun"))
        Qa.check("Seed pushed to tomorrow", Qa.exists(compose, "tomorrow"))
        Qa.shot("home_holding_care", "Holding on, care list")
        Qa.closeApp()

        // Wilting: rough days in a row, far over the limit.
        Qa.reset(today, 21)
        Qa.seed(Qa.onboardedState(today.minusDays(30), vitality = 40, flowers = 8, lowDays = 2, best = 15))
        Qa.fake.days[today] = Qa.stats(102, sunlight = false, nightMin = 25)
        open("Wilting, 2 rough days, 42m over, night", "home_wilting")
        Qa.check("Wilting headline", Qa.exists(compose, "Your garden is wilting"))
        Qa.check("Rough days kicker", Qa.exists(compose, "2 rough days in a row"))
        // 40 − 10 (42m over) − 3 (weeds) = 27
        Qa.check("Vitality 27", Qa.exists(compose, "27"))
        Qa.check("Can be saved notice", Qa.exists(compose, "Your flowers can still be saved"))
        Qa.check("Tells how many days are left", Qa.exists(compose, "After 1 more wilted day, one is lost.", substring = true))
        compose.onNodeWithText("Today's care").performScrollTo()
        Qa.check("Weeds row", Qa.exists(compose, "Weeds crept in"))
        Qa.shot("home_wilting_care", "Wilting, care list")
        compose.onNodeWithText("Start a focus session").performScrollTo().performClick()
        Qa.check("Notice opens focus", Qa.waitText(compose, "Start focus"))
        Qa.closeApp()

        // Usage access switched off after setup.
        Qa.reset(today, 11)
        Qa.seed(Qa.onboardedState(today.minusDays(5), vitality = 75, flowers = 3))
        Qa.fake.access = false
        open("Usage access turned off", "home_no_access")
        Qa.check("Access-off notice", Qa.exists(compose, "Usage access is off"))
        Qa.check("Social shows a dash", Qa.exists(compose, "—"))
        Qa.check("Care list explains", Qa.exists(compose, "Turn on usage access to see today's care."))
        Qa.closeApp()

        // Early morning: nothing decided yet.
        Qa.reset(today, 5)
        Qa.seed(Qa.onboardedState(today.minusDays(5), vitality = 70, flowers = 3))
        Qa.fake.days[today] = Qa.stats(0, nightDone = false, wakeHourDone = false, wake = null)
        open("5am, before anything is decided (dawn)", "home_dawn")
        Qa.check("Vitality unchanged at 70", Qa.exists(compose, "70"))
        compose.onNodeWithText("Today's care").performScrollTo()
        Qa.check("Sunlight still pending", Qa.exists(compose, "Skip social apps for an hour after waking"))
        Qa.check("No roots row before 6am", !Qa.exists(compose, "Deep roots"))
        Qa.closeApp()

        // Brand-new day 1 at night, heavy use before install: grace day.
        Qa.reset(today, 22)
        Qa.seed(Qa.onboardedState(today, vitality = 70, flowers = 1, lastClosed = today.minusDays(1)))
        Qa.fake.days[today] = Qa.stats(190, sunlight = false, nightMin = 0)
        open("Install day with heavy use earlier (grace day)", "home_grace")
        Qa.check("No penalty on install day", Qa.exists(compose, "75"))
        Qa.check("Grace note", Qa.exists(compose, "(first day is free)", substring = true))
        Qa.closeApp()

        val w = Store(Qa.ctx).widgetInfo()
        Qa.check("Widget info written", w != null)
        Qa.finish()
    }
}
