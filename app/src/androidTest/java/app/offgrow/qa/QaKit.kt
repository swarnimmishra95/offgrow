package app.offgrow.qa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import androidx.work.WorkManager
import app.offgrow.MainActivity
import app.offgrow.data.Store
import app.offgrow.garden.AppClock
import app.offgrow.garden.DayUsage
import app.offgrow.garden.FocusConfig
import app.offgrow.garden.Flower
import app.offgrow.garden.GardenEngine
import app.offgrow.garden.GardenRenderer
import app.offgrow.garden.GardenState
import app.offgrow.garden.Rules
import app.offgrow.usage.DayStats
import app.offgrow.usage.Usage
import app.offgrow.usage.UsageSource
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/** Scripted usage data: what "the phone" reports for each day. */
class FakeUsage : UsageSource {
    @Volatile
    var access = true
    val days = HashMap<LocalDate, DayStats>()

    override fun hasAccess(): Boolean = access

    override fun readDay(date: LocalDate, now: Long): DayStats = days[date] ?: Qa.stats(0, sunlight = true, wake = null)
}

/** Shared helpers for the on-device QA suite. Results go to files/qa (report.md + screenshots). */
object Qa {
    const val TAG = "OffgrowQA"
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val ctx: Context get() = instrumentation.targetContext
    val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    val fake = FakeUsage()

    private val dir: File get() = File(ctx.filesDir, "qa").apply { mkdirs() }
    private val shots: File get() = File(dir, "shots").apply { mkdirs() }
    private var shotNo = -1
    private val failures = mutableListOf<String>()
    private var checks = 0

    // ---------- report ----------

    fun log(line: String) {
        Log.i(TAG, line)
        File(dir, "report.md").appendText(line + "\n")
    }

    fun section(title: String) = log("\n## $title\n")

    fun check(label: String, ok: Boolean, detail: String = "") {
        checks++
        log((if (ok) "- PASS " else "- FAIL ") + label + if (detail.isNotEmpty()) " — $detail" else "")
        if (!ok) failures += label
    }

    fun <T> checkEq(label: String, expected: T, actual: T) =
        check(label, expected == actual, "expected $expected, got $actual")

    /** Fail the current test if any soft check failed, after recording everything. */
    fun finish() {
        val f = failures.toList()
        failures.clear()
        log("\n_${checks} checks so far_")
        if (f.isNotEmpty()) throw AssertionError("QA failures: " + f.joinToString("; "))
    }

    // ---------- screenshots ----------

    /** The current test's Compose rule, so screenshots wait for the latest frame. */
    @Volatile
    var compose: ComposeTestRule? = null

    fun shot(name: String, caption: String = "") {
        // Compose tests drive frames from a test clock; sync it so the screen shows the latest state.
        try {
            compose?.waitForIdle()
        } catch (_: Throwable) {
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(700)
        val bmp = instrumentation.uiAutomation.takeScreenshot()
        if (bmp == null) {
            log("  (screenshot $name failed)")
            return
        }
        saveBitmap(bmp, name, caption, 540)
    }

    fun saveBitmap(bmp: Bitmap, name: String, caption: String = "", width: Int = 540) {
        // Numbering continues across test classes, which run in separate processes.
        if (shotNo < 0) shotNo = shots.listFiles()?.size ?: 0
        shotNo++
        val scaled = if (bmp.width > width) Bitmap.createScaledBitmap(bmp, width, bmp.height * width / bmp.width, true) else bmp
        val file = File(shots, "%03d_%s.jpg".format(shotNo, name))
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        log("  [shot] ${file.name}" + if (caption.isNotEmpty()) " — $caption" else "")
    }

    // ---------- time and data ----------

    fun at(date: LocalDate, h: Int, m: Int = 0): Long = date.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    fun setNow(date: LocalDate, h: Int, m: Int = 0) {
        AppClock.fixedNow = at(date, h, m)
    }

    /** A day's usage as the phone would report it. */
    fun stats(
        social: Int,
        sunlight: Boolean = true,
        nightMin: Int = 0,
        pickups: Int = 40,
        screen: Int = social + 110,
        free: Int = 420,
        wake: Long? = 0L,
        nightDone: Boolean = true,
        wakeHourDone: Boolean = true,
    ) = DayStats(
        socialMin = social,
        screenMin = screen,
        phoneFreeMin = free,
        pickups = pickups,
        wakeAt = wake,
        socialInWakeHour = !sunlight,
        nightScreenMin = nightMin,
        nightDone = nightDone,
        wakeHourDone = wakeHourDone,
    )

    /** Wipe the app back to a fresh install and plug in the fakes. */
    fun reset(now: LocalDate = LocalDate.of(2026, 10, 7), hour: Int = 10) {
        closeApp()
        try {
            WorkManager.getInstance(ctx).cancelAllWork().result.get()
        } catch (e: Exception) {
            Log.w(TAG, "cancel work", e)
        }
        waitEngineIdle(20_000)
        AppClock.zoneOverride = zone
        setNow(now, hour)
        fake.access = true
        fake.days.clear()
        Usage.override = fake
        FocusConfig.durationMs = Rules.FOCUS_MINUTES * 60_000L
        ctx.getSharedPreferences("offgrow", Context.MODE_PRIVATE).edit().clear().commit()
        File(ctx.filesDir, "garden.jpg").delete()
        File(ctx.filesDir, "garden.tmp").delete()
    }

    /** Save a garden state directly, as if the user had lived through it. */
    fun seed(state: GardenState) {
        Store(ctx).save(state)
    }

    fun state(): GardenState = Store(ctx).load()

    fun onboardedState(
        created: LocalDate,
        vitality: Int = 70,
        flowers: Int = 1,
        streak: Int = 0,
        best: Int = streak,
        seeds: Int = 0,
        lowDays: Int = 0,
        name: String = "Swarnim",
        limit: Int = 60,
        lastClosed: LocalDate = AppClock.today().minusDays(1),
    ): GardenState {
        val kinds = listOf("poppy", "sunflower", "cornflower", "daisy", "cosmos", "marigold", "lavender", "tulip", "rosebush", "hydrangea")
        return Store.newState().copy(
            seed = 424242,
            createdDay = created.toString(),
            name = name,
            limitMin = limit,
            vitality = vitality,
            lastClosedDay = lastClosed.toString(),
            streak = streak,
            bestStreak = best,
            pendingSeeds = seeds,
            lowDays = lowDays,
            onboarded = true,
            fence = "white",
            flowers = (0 until flowers).map { i ->
                Flower("f$i", kinds[i % kinds.size], Rules.plantable(kinds[i % kinds.size]).label + " #${i + 1}", if (i == 0) "Lodhi garden walk with Riya" else "", created.plusDays(i.toLong()).toString())
            },
        )
    }

    // ---------- app control ----------

    fun launchApp() {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(i)
        waitFor(15_000, "app resumed") { resumed() is MainActivity }
    }

    /** Bring the running app back to the front without restarting it. */
    fun bringToFront() {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        waitFor(15_000, "app resumed") { resumed() is MainActivity }
    }

    fun resumed(): Activity? {
        var a: Activity? = null
        instrumentation.runOnMainSync {
            a = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return a
    }

    fun closeApp() {
        instrumentation.runOnMainSync {
            val reg = ActivityLifecycleMonitorRegistry.getInstance()
            for (stage in listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.CREATED, Stage.STARTED)) {
                reg.getActivitiesInStage(stage).forEach { if (!it.isFinishing) it.finish() }
            }
        }
        waitFor(10_000, "activities closed") {
            var alive = 0
            instrumentation.runOnMainSync {
                val reg = ActivityLifecycleMonitorRegistry.getInstance()
                alive = listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.CREATED, Stage.STARTED)
                    .sumOf { reg.getActivitiesInStage(it).size }
            }
            alive == 0
        }
        GardenRenderer.host = null
    }

    fun waitFor(timeoutMs: Long, what: String, cond: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (try { cond() } catch (_: Throwable) { false }) return true
            SystemClock.sleep(150)
        }
        log("  (timed out waiting for $what)")
        return false
    }

    /** Wait until no scoring or drawing is running, and it has stayed that way briefly. */
    fun waitEngineIdle(timeoutMs: Long = 60_000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var quietSince = -1L
        while (SystemClock.uptimeMillis() < end) {
            if (GardenEngine.busy.get() == 0) {
                if (quietSince < 0) quietSince = SystemClock.uptimeMillis()
                if (SystemClock.uptimeMillis() - quietSince > 1200) return true
            } else {
                quietSince = -1
            }
            SystemClock.sleep(100)
        }
        log("  (engine still busy after ${timeoutMs}ms)")
        return false
    }

    /** Wait for the app to finish scoring and drawing, and the garden picture to be on screen. */
    fun settle(compose: ComposeTestRule, timeoutMs: Long = 60_000) {
        waitEngineIdle(timeoutMs)
        waitFor(timeoutMs, "garden picture") {
            compose.onAllNodesWithText("Growing your garden…").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
        SystemClock.sleep(400)
    }

    fun shell(cmd: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(cmd)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    fun exists(compose: ComposeTestRule, text: String, substring: Boolean = false): Boolean =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    fun waitText(compose: ComposeTestRule, text: String, timeoutMs: Long = 15_000, substring: Boolean = false): Boolean =
        waitFor(timeoutMs, "text \"$text\"") { exists(compose, text, substring) }
}

/**
 * Independent statement of the product rules, used to predict what the app should show.
 * Kept separate from Rules.kt on purpose.
 */
class Oracle(val start: LocalDate, var limit: Int = 60) {
    var v = Rules.START_VITALITY
    var streak = 0
    var best = 0
    var seeds = 1
    var low = 0
    var prevGood: Boolean? = null
    var planted = 0
    var lost = 0

    private fun penalty(over: Int) = when {
        over <= 0 -> 0
        over <= 30 -> -5
        over <= 60 -> -10
        else -> -20
    }

    fun delta(u: DayUsage, focus: Int, grace: Boolean): Int {
        val over = u.socialMin - limit
        var d = if (over <= 0) 10 else if (grace) 0 else penalty(over)
        if (u.sunlight == true) d += 5
        d += when (u.nightClean) {
            true -> 5
            false -> if (grace) 0 else -3
            null -> 0
        }
        d += 3 * min(focus, 3)
        if (over <= 0 && prevGood == false) d += 10
        return d
    }

    /** Close [day] with [u]. Returns the expected change. */
    fun close(day: LocalDate, u: DayUsage, focus: Int): Int {
        val d = delta(u, focus, day == start)
        v = max(0, min(100, v + d))
        val good = u.socialMin <= limit
        streak = if (good) streak + 1 else 0
        best = max(best, streak)
        if (good) seeds = min(3, seeds + 1)
        low = if (v < 35 && !good) low + 1 else 0
        if (low >= 3) {
            if (planted - lost > 0) lost++
            low = 0
        }
        prevGood = good
        return d
    }

    /** What today's live vitality should be, before the day closes. */
    fun live(u: DayUsage, focus: Int, grace: Boolean): Int {
        val over = u.socialMin - limit
        var d = if (over <= 0) 0 else if (grace) 0 else penalty(over)
        if (u.sunlight == true) d += 5
        d += when (u.nightClean) {
            true -> 5
            false -> if (grace) 0 else -3
            null -> 0
        }
        d += 3 * min(focus, 3)
        return max(0, min(100, v + d))
    }
}
