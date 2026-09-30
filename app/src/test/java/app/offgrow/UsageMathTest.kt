package app.offgrow

import app.offgrow.usage.DayStats
import app.offgrow.usage.UsageEvent
import app.offgrow.usage.UsageMath
import app.offgrow.usage.UsageMath.KEYGUARD_HIDDEN
import app.offgrow.usage.UsageMath.KEYGUARD_SHOWN
import app.offgrow.usage.UsageMath.PAUSED
import app.offgrow.usage.UsageMath.RESUMED
import app.offgrow.usage.UsageMath.SCREEN_OFF
import app.offgrow.usage.UsageMath.STOPPED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Raw usage-event scenarios: what Android reports, and what the garden should make of it. */
class UsageMathTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 10, 7)
    private val insta = "com.instagram.android"
    private val maps = "com.google.android.apps.maps"
    private val launcher = "com.android.launcher3"
    private val clock = "com.google.android.deskclock"

    private fun at(h: Int, m: Int, d: LocalDate = day) =
        LocalDateTime.of(d, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private fun ev(h: Int, m: Int, type: Int, pkg: String, cls: String? = "$pkg.Main", d: LocalDate = day) =
        UsageEvent(at(h, m, d), type, pkg, cls)

    private fun use(pkg: String, h1: Int, m1: Int, h2: Int, m2: Int, d: LocalDate = day, cls: String = "$pkg.Main") = listOf(
        UsageEvent(at(h1, m1, d), RESUMED, pkg, cls),
        UsageEvent(at(h2, m2, d), PAUSED, pkg, cls),
    )

    private fun unlock(h: Int, m: Int, d: LocalDate = day) = UsageEvent(at(h, m, d), KEYGUARD_HIDDEN, "android", null)
    private fun lock(h: Int, m: Int, d: LocalDate = day) = UsageEvent(at(h, m, d), KEYGUARD_SHOWN, "android", null)

    private fun compute(events: List<UsageEvent>, now: Long = at(23, 59) + 60_000, sdk: Int = 34): DayStats =
        UsageMath.compute(events, day, now, zone, sdk) { it == insta }

    @Test
    fun simpleSocialSpan() {
        val s = compute(listOf(unlock(9, 0)) + use(insta, 10, 0, 10, 30))
        assertEquals(30, s.socialMin)
        assertEquals(30, s.screenMin)
        assertEquals(1, s.pickups)
    }

    @Test
    fun movingBetweenScreensInsideOneAppKeepsCounting() {
        // Splash screen -> feed -> comments: pause A, resume B, stop A (the order Android 10+ uses).
        val e = listOf(
            ev(10, 0, RESUMED, insta, "Splash"),
            ev(10, 0, PAUSED, insta, "Splash"),
            ev(10, 0, RESUMED, insta, "Feed"),
            ev(10, 1, STOPPED, insta, "Splash"),
            ev(10, 20, PAUSED, insta, "Feed"),
            ev(10, 20, RESUMED, insta, "Comments"),
            ev(10, 21, STOPPED, insta, "Feed"),
            ev(10, 45, PAUSED, insta, "Comments"),
        )
        assertEquals(45, compute(e).socialMin)
    }

    @Test
    fun lateCloseOfPreviousScreenDoesNotEndTheSpan() {
        // Some phones report the new screen resuming before the old one pauses.
        val e = listOf(
            ev(10, 0, RESUMED, insta, "Feed"),
            ev(10, 5, RESUMED, insta, "Story"),
            ev(10, 5, PAUSED, insta, "Feed"),
            ev(10, 30, PAUSED, insta, "Story"),
        )
        assertEquals(30, compute(e).socialMin)
    }

    @Test
    fun switchingAppsSplitsTime() {
        val e = use(insta, 12, 0, 12, 10) + use(maps, 12, 10, 12, 40) + use(insta, 12, 40, 13, 0)
        val s = compute(e)
        assertEquals(30, s.socialMin)
        assertEquals(60, s.screenMin)
    }

    @Test
    fun resumeOfAnotherAppClosesAMissingPause() {
        val e = listOf(ev(14, 0, RESUMED, insta), ev(14, 25, RESUMED, maps), ev(14, 30, PAUSED, maps))
        val s = compute(e)
        assertEquals(25, s.socialMin)
        assertEquals(30, s.screenMin)
    }

    @Test
    fun screenOffEndsTheSpan() {
        val e = listOf(ev(15, 0, RESUMED, insta), UsageEvent(at(15, 12), SCREEN_OFF, "android", null))
        assertEquals(12, compute(e).socialMin)
    }

    @Test
    fun spanAcrossMidnightIsSplit() {
        val prev = day.minusDays(1)
        val e = listOf(ev(23, 50, RESUMED, insta, d = prev), ev(0, 20, PAUSED, insta))
        val s = compute(e)
        assertEquals("only today's 20 minutes", 20, s.socialMin)
        assertEquals("all of it inside the night window", 30, s.nightScreenMin)
    }

    @Test
    fun nightUseAfterUnlockCounts() {
        val prev = day.minusDays(1)
        val e = listOf(lock(22, 30, prev), unlock(1, 10)) + use(maps, 1, 10, 1, 22)
        val s = compute(e)
        assertEquals(12, s.nightScreenMin)
        assertEquals(false, s.toUsage(closed = true).nightClean)
    }

    @Test
    fun alarmOverTheLockScreenIsNotNightUse() {
        val prev = day.minusDays(1)
        val e = listOf(lock(22, 30, prev)) + use(clock, 5, 30, 5, 42) + listOf(unlock(7, 0)) + use(maps, 7, 0, 7, 5)
        val s = compute(e)
        assertEquals(0, s.nightScreenMin)
        assertEquals(true, s.toUsage(closed = true).nightClean)
        assertEquals("wake is the first unlock, not the alarm", at(7, 0), s.wakeAt)
    }

    @Test
    fun briefNightCheckUnderFiveMinutesIsForgiven() {
        val prev = day.minusDays(1)
        val e = listOf(lock(22, 0, prev), unlock(2, 0)) + use(maps, 2, 0, 2, 4) + listOf(lock(2, 4))
        val s = compute(e)
        assertEquals(4, s.nightScreenMin)
        assertEquals(true, s.toUsage(closed = true).nightClean)
    }

    @Test
    fun socialInFirstHourAfterWakingBlocksSunlight() {
        val e = listOf(unlock(7, 0)) + use(insta, 7, 30, 7, 40)
        val s = compute(e)
        assertTrue(s.socialInWakeHour)
        assertEquals(false, s.toUsage(closed = true).sunlight)
    }

    @Test
    fun socialAfterTheFirstHourKeepsSunlight() {
        val e = listOf(unlock(7, 0)) + use(maps, 7, 5, 7, 20) + use(insta, 8, 5, 8, 30)
        val s = compute(e)
        assertFalse(s.socialInWakeHour)
        assertEquals(true, s.toUsage(closed = true).sunlight)
    }

    @Test
    fun sunlightIsPendingUntilTheHourPasses() {
        val e = listOf(unlock(7, 0)) + use(maps, 7, 5, 7, 20)
        val live = compute(e, now = at(7, 30))
        assertNull(live.toUsage(closed = false).sunlight)
        val later = compute(e, now = at(8, 1))
        assertEquals(true, later.toUsage(closed = false).sunlight)
    }

    @Test
    fun nightIsPendingUntilSixAm() {
        val prev = day.minusDays(1)
        val e = listOf(lock(22, 0, prev))
        assertNull(compute(e, now = at(5, 0)).toUsage(closed = false).nightClean)
        assertEquals(true, compute(e, now = at(6, 1)).toUsage(closed = false).nightClean)
    }

    @Test
    fun earlyCheckBeforeFourAmIsNotWaking() {
        val e = listOf(unlock(3, 30)) + use(maps, 3, 30, 3, 33) + listOf(lock(3, 33), unlock(8, 0))
        assertEquals(at(8, 0), compute(e).wakeAt)
    }

    @Test
    fun liveFiguresCountUpToNow() {
        val e = listOf(unlock(9, 0), ev(9, 0, RESUMED, insta))
        val s = compute(e, now = at(9, 42))
        assertEquals(42, s.socialMin)
        assertEquals(0, s.phoneFreeMin)
    }

    @Test
    fun phoneFreeTimeIsTimeSinceWakingMinusScreenTime() {
        val e = listOf(unlock(8, 0)) + use(maps, 8, 0, 8, 30) + use(insta, 12, 0, 12, 30)
        val s = compute(e, now = at(18, 0))
        assertEquals(10 * 60 - 60, s.phoneFreeMin)
    }

    @Test
    fun emptyDay() {
        val s = compute(emptyList())
        assertEquals(0, s.socialMin)
        assertEquals(0, s.screenMin)
        assertNull(s.wakeAt)
        assertEquals(true, s.toUsage(closed = true).sunlight)
        assertEquals(true, s.toUsage(closed = true).nightClean)
    }

    @Test
    fun pickupsUnknownOnOldAndroidWithoutKeyguardEvents() {
        assertEquals(-1, compute(use(maps, 9, 0, 9, 5), sdk = 27).pickups)
        assertEquals(0, compute(use(maps, 9, 0, 9, 5), sdk = 30).pickups)
    }

    @Test
    fun manyPickups() {
        val e = (8..21).flatMap { h -> listOf(unlock(h, 0)) + use(insta, h, 0, h, 3) + listOf(lock(h, 3)) }
        val s = compute(e)
        assertEquals(14, s.pickups)
        assertEquals(42, s.socialMin)
    }

    @Test
    fun eventsAfterNowAreIgnored() {
        val e = use(insta, 10, 0, 10, 30) + use(insta, 20, 0, 21, 0)
        assertEquals(30, compute(e, now = at(12, 0)).socialMin)
    }

    @Test
    fun heavyUserDay() {
        // 6 hours of social in 20-minute chunks.
        val e = (6..23).flatMap { h -> use(insta, h, 0, h, 20) }
        val s = compute(e)
        assertEquals(18 * 20, s.socialMin)
    }

    @Test
    fun daylightSavingDayStillSumsCorrectly() {
        val london = ZoneId.of("Europe/London")
        val dstDay = LocalDate.of(2026, 3, 29) // clocks go forward at 1am
        fun t(h: Int, m: Int) = LocalDateTime.of(dstDay, java.time.LocalTime.of(h, m)).atZone(london).toInstant().toEpochMilli()
        val e = listOf(UsageEvent(t(10, 0), RESUMED, insta, "a"), UsageEvent(t(10, 45), PAUSED, insta, "a"))
        val s = UsageMath.compute(e, dstDay, t(23, 0), london, 34) { it == insta }
        assertEquals(45, s.socialMin)
    }
}
