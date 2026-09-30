package app.offgrow.qa

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.offgrow.data.Store
import app.offgrow.garden.GardenEngine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Flow: fresh install → welcome → permission (denied, then granted) → setup → first flower → home. */
@RunWith(AndroidJUnit4::class)
class A1_OnboardingTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    init {
        Qa.compose = compose
    }

    @Test
    fun firstRunFlow() {
        val today = LocalDate.of(2026, 10, 7)
        Qa.reset(today, 9)
        Qa.section("A1 · First run: welcome to home")
        Qa.fake.access = false
        Qa.fake.days[today] = Qa.stats(18, sunlight = true, nightMin = 0, pickups = 22)

        Qa.launchApp()
        Qa.check("Welcome screen shows", Qa.waitText(compose, "Grow what you don't scroll."))
        Qa.check("Welcome CTA visible", Qa.exists(compose, "Plant my garden"))
        Qa.shot("welcome", "Fresh install")

        compose.onNodeWithText("Plant my garden").performClick()
        Qa.check("Permission screen shows", Qa.waitText(compose, "Zero spying.", substring = true))
        Qa.check("Says step 1 of 2", Qa.exists(compose, "Step 1 of 2"))
        Qa.shot("permission", "Usage access explained")

        compose.onNodeWithText("Grant usage access").performClick()
        SystemClock.sleep(2500)
        val pkg = Qa.device.currentPackageName
        Qa.check("Grant opens Android's usage access settings", pkg == "com.android.settings", "foreground: $pkg")
        Qa.shot("system_usage_access", "Android Settings opened by the app")

        // Come back without turning it on.
        Qa.device.pressBack()
        SystemClock.sleep(1500)
        if (Qa.device.currentPackageName != "app.offgrow") Qa.bringToFront()
        Qa.check("Back in the app on the permission step", Qa.waitText(compose, "Grant usage access"))
        Qa.check("Help for greyed-out switch appears after a try", Qa.waitText(compose, "Switch greyed out?"))
        Qa.check("Help is on screen without scrolling", try {
            compose.onNodeWithText("Switch greyed out?").assertIsDisplayed()
            true
        } catch (_: Throwable) {
            false
        })
        Qa.shot("permission_help", "After returning without access")

        // Now the user turns it on.
        compose.onNodeWithText("Grant usage access").performClick()
        SystemClock.sleep(2000)
        Qa.fake.access = true
        Qa.device.pressBack()
        SystemClock.sleep(1500)
        if (Qa.device.currentPackageName != "app.offgrow") Qa.bringToFront()
        Qa.check("Moves on to setup once access is on", Qa.waitText(compose, "Make it yours."))
        Qa.check("Says step 2 of 2", Qa.exists(compose, "Step 2 of 2"))
        Qa.shot("setup", "Setup")

        compose.onNode(hasSetTextAction()).performTextInput("Swarnim")
        compose.onNodeWithText("45m").performClick()
        compose.waitForIdle()
        Qa.shot("setup_filled", "Name and 45m limit chosen")
        compose.onNodeWithText("Plant my first flower").performClick()

        Qa.check("First-flower screen shows", Qa.waitText(compose, "Plant your first flower"))
        Qa.shot("plant_first", "First seed")
        compose.onNodeWithText("Poppy").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Walked to the lake instead")
        compose.waitForIdle()
        Qa.check("Button follows the chosen flower", Qa.exists(compose, "Plant poppy"))
        Qa.shot("plant_first_filled", "Poppy chosen with a note")
        compose.onNodeWithText("Plant poppy").performClick()

        Qa.check("Lands on home", Qa.waitText(compose, "Today's care", 20_000))
        Qa.settle(compose)
        val s = Store(Qa.ctx).load()
        Qa.checkEq("Onboarded", true, s.onboarded)
        Qa.checkEq("Name saved", "Swarnim", s.name)
        Qa.checkEq("Limit saved", 45, s.limitMin)
        Qa.checkEq("One flower planted", 1, s.flowers.size)
        Qa.checkEq("It's a poppy", "poppy", s.flowers.firstOrNull()?.kind)
        Qa.checkEq("Note saved", "Walked to the lake instead", s.flowers.firstOrNull()?.note)
        Qa.checkEq("Seed used up", 0, s.pendingSeeds)
        Qa.checkEq("Created today", today.toString(), s.createdDay)
        Qa.checkEq("Nothing to close yet", today.minusDays(1).toString(), s.lastClosedDay)
        Qa.check("Title uses the name", Qa.exists(compose, "Swarnim's garden"))
        Qa.check("Day 1 badge", Qa.exists(compose, "Day 1"))
        // 70 + sunlight 5 + roots 5 so far (watering is paid at midnight)
        Qa.check("Live vitality 80", Qa.exists(compose, "80"))
        Qa.check("Healthy headline", Qa.exists(compose, "Your garden is healthy"))
        Qa.check("Social today shows 18m", Qa.exists(compose, "18m"))
        Qa.check("Limit shows of 45m", Qa.exists(compose, "of 45m"))
        Qa.check("Garden picture drawn", GardenEngine.imageFile(Qa.ctx).exists())
        Qa.shot("home_first", "Home on day 1")
        compose.onNodeWithText("Today's care").performScrollTo()
        Qa.check("Watering pending row", Qa.exists(compose, "Watering"))
        Qa.check("Morning sunlight row", Qa.exists(compose, "Morning sunlight"))
        Qa.check("Deep roots row", Qa.exists(compose, "Deep roots"))
        Qa.check("Next seed row", Qa.exists(compose, "Next seed"))
        Qa.shot("home_first_care", "Today's care on day 1")

        val w = Store(Qa.ctx).widgetInfo()
        Qa.checkEq("Widget title", "Healthy", w?.title)
        Qa.checkEq("Widget status", "27m of social left", w?.status)

        Qa.closeApp()
        Qa.finish()
    }
}
