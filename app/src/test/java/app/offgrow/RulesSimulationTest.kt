package app.offgrow

import app.offgrow.garden.Band
import app.offgrow.garden.DayUsage
import app.offgrow.garden.Flower
import app.offgrow.garden.GardenState
import app.offgrow.garden.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-day and multi-week simulations of the garden rules.
 *
 * Expected values come from [Spec], an independent re-statement of the product rules,
 * so a bug in Rules.kt can't hide by agreeing with itself.
 */
class RulesSimulationTest {

    /** The product rules, written out plainly. */
    object Spec {
        fun penalty(over: Int) = when {
            over <= 0 -> 0
            over <= 30 -> -5
            over <= 60 -> -10
            else -> -20
        }

        fun delta(u: DayUsage, limit: Int, focus: Int, prevGood: Boolean?, grace: Boolean): Int {
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
    }

    /** One simulated user living through days. */
    class Sim(val start: LocalDate, val limit: Int = 60, val plantSeeds: Boolean = true) {
        var state = GardenState(
            installId = "sim", seed = 7, createdDay = start.toString(), onboarded = true,
            lastClosedDay = start.minusDays(1).toString(), pendingSeeds = 1, limitMin = limit,
        )
        var expV = Rules.START_VITALITY
        var expStreak = 0
        var expBest = 0
        var expSeeds = 1
        var expLow = 0
        var prevGood: Boolean? = null
        var planted = 0
        var lost = 0
        val log = StringBuilder()

        fun plantIfAny(day: LocalDate) {
            while (plantSeeds && state.pendingSeeds > 0) {
                val kind = Rules.PLANTABLES[planted % 7].kind
                state = Rules.plant(state, Flower("f$planted", kind, "F$planted", "", day.toString()))
                planted++
                expSeeds--
            }
        }

        /** Live the day [day] with [u] (null = usage unreadable), then close it the next morning. */
        fun live(day: LocalDate, u: DayUsage?, focus: Int = 0) {
            plantIfAny(day)
            repeat(focus) { state = Rules.completeFocus(state, day) }
            // Next morning: the app catches up and closes the day.
            state = Rules.catchUp(state, day.plusDays(1)) { d -> if (d == day) u else null }
            if (u != null) {
                val grace = day == start
                val d = Spec.delta(u, state.limitMin, focus, prevGood, grace)
                expV = max(0, min(100, expV + d))
                val good = u.socialMin <= state.limitMin
                expStreak = if (good) expStreak + 1 else 0
                expBest = max(expBest, expStreak)
                if (good) expSeeds = min(3, expSeeds + 1)
                expLow = if (expV < 35 && !good) expLow + 1 else 0
                if (expLow >= 3) {
                    if (planted - lost > 0) lost++
                    expLow = 0
                }
                prevGood = good
            }
            val rec = state.days.lastOrNull()
            log.append("${day.dayOfWeek.toString().take(3)} ${day} social=${u?.socialMin ?: "-"} delta=${rec?.delta ?: 0} v=${state.vitality} streak=${state.streak} seeds=${state.pendingSeeds} flowers=${state.aliveFlowers.size}/${state.flowers.size}\n")
            assertEquals("vitality on $day", expV, state.vitality)
            assertEquals("streak on $day", expStreak, state.streak)
            assertEquals("best streak on $day", expBest, state.bestStreak)
            assertEquals("seeds on $day", expSeeds, state.pendingSeeds)
            assertEquals("lost flowers on $day", lost, state.flowers.count { !it.alive })
            assertEquals("last closed on $day", day.toString(), state.lastClosedDay)
            assertTrue("vitality in range", state.vitality in 0..100)
        }
    }

    private val monday = LocalDate.of(2026, 10, 5)

    private fun good(social: Int = 30) = DayUsage(social, social + 120, 600, 40, sunlight = true, nightClean = true)
    private fun bad(social: Int = 180) = DayUsage(social, social + 200, 300, 110, sunlight = false, nightClean = false)

    @Test
    fun idealMonth() {
        val sim = Sim(monday)
        for (i in 0 until 28) sim.live(monday.plusDays(i.toLong()), good(20 + i % 20))
        assertEquals(100, sim.state.vitality)
        assertEquals(28, sim.state.streak)
        assertEquals(Band.THRIVING, Band.of(sim.state.vitality))
        assertEquals(1.0, Rules.age(sim.state), 0.0001)
        println("IDEAL MONTH\n" + sim.log)
    }

    @Test
    fun doomscrollerThreeWeeks() {
        val sim = Sim(monday)
        sim.live(monday, good()) // plants a couple of flowers first
        sim.live(monday.plusDays(1), good())
        sim.live(monday.plusDays(2), good())
        for (i in 3 until 21) sim.live(monday.plusDays(i.toLong()), bad(150 + i * 5))
        assertEquals(0, sim.state.vitality)
        assertEquals(0, sim.state.streak)
        assertEquals(Band.WILTING, Band.of(sim.state.vitality))
        assertTrue("flowers were lost", sim.state.flowers.any { !it.alive })
        println("DOOMSCROLLER\n" + sim.log)
    }

    @Test
    fun weekdaysGoodWeekendsBinge() {
        val sim = Sim(monday)
        for (i in 0 until 28) {
            val d = monday.plusDays(i.toLong())
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            sim.live(d, if (weekend) bad(200) else good(35))
        }
        // Every Monday after a binge gets the comeback bonus.
        val mondays = sim.state.days.filter { LocalDate.parse(it.day).dayOfWeek == DayOfWeek.MONDAY && it.day != monday.toString() }
        assertTrue(mondays.isNotEmpty())
        mondays.forEach { d -> assertTrue("comeback on ${d.day}", d.items.any { it.key == "comeback" }) }
        println("WEEKEND BINGE\n" + sim.log)
    }

    @Test
    fun borderlineLimits() {
        val sim = Sim(monday.minusDays(1), limit = 60)
        sim.live(monday.minusDays(1), good()) // grace day out of the way
        sim.live(monday, DayUsage(60, 200, 300, 50, true, true)) // exactly at the limit is fine
        assertTrue(sim.state.days.last().good)
        sim.live(monday.plusDays(1), DayUsage(61, 200, 300, 50, true, true)) // 1 over
        assertEquals(-5 + 5 + 5, sim.state.days.last().delta)
        sim.live(monday.plusDays(2), DayUsage(90, 200, 300, 50, null, null)) // 30 over, nothing else known
        assertEquals(-5, sim.state.days.last().delta)
        sim.live(monday.plusDays(3), DayUsage(91, 200, 300, 50, false, true)) // 31 over
        assertEquals(-10 + 5, sim.state.days.last().delta)
        sim.live(monday.plusDays(4), DayUsage(121, 200, 300, 50, false, true)) // 61 over
        assertEquals(-20 + 5, sim.state.days.last().delta)
    }

    @Test
    fun nightOwlAndMorningScroller() {
        val owl = Sim(monday)
        for (i in 0 until 14) owl.live(monday.plusDays(i.toLong()), DayUsage(30, 200, 300, 60, true, false))
        // +10 water +5 sun -3 weeds = +12 a day (grace day has no weeds)
        assertEquals(100, owl.state.vitality)
        assertTrue(owl.state.days.drop(1).all { d -> d.items.any { it.key == "weeds" && it.delta == -3 } })

        val morning = Sim(monday)
        for (i in 0 until 14) morning.live(monday.plusDays(i.toLong()), DayUsage(45, 200, 300, 60, false, true))
        assertTrue(morning.state.days.all { d -> d.items.none { it.key == "sun" } && d.items.any { it.key == "shade" } })
    }

    @Test
    fun recoveryArcKeepsLostFlowersLost() {
        val sim = Sim(monday)
        for (i in 0 until 3) sim.live(monday.plusDays(i.toLong()), good())
        val before = sim.state.aliveFlowers.size
        for (i in 3 until 10) sim.live(monday.plusDays(i.toLong()), bad(240))
        val lost = sim.state.flowers.count { !it.alive }
        assertTrue("lost at least one", lost >= 1)
        for (i in 10 until 17) sim.live(monday.plusDays(i.toLong()), good())
        assertEquals("lost flowers stay lost", lost, sim.state.flowers.count { !it.alive })
        assertTrue("new flowers planted", sim.state.aliveFlowers.size > before - lost)
        val comebacks = sim.state.days.count { d -> d.items.any { it.key == "comeback" } }
        assertEquals("one comeback bonus", 1, comebacks)
        println("RECOVERY\n" + sim.log)
    }

    @Test
    fun revokedAccessDaysAreSkippedNotPunished() {
        val sim = Sim(monday)
        sim.live(monday, good())
        sim.live(monday.plusDays(1), good())
        val v = sim.state.vitality
        val streak = sim.state.streak
        for (i in 2 until 5) sim.live(monday.plusDays(i.toLong()), null)
        assertEquals(v, sim.state.vitality)
        assertEquals(streak, sim.state.streak)
        assertEquals(2, sim.state.days.size)
        sim.live(monday.plusDays(5), good())
        assertEquals(streak + 1, sim.state.streak)
    }

    @Test
    fun longAbsenceOnlyCatchesUpSevenDays() {
        var s = GardenState(installId = "x", seed = 1, createdDay = "2026-09-01", onboarded = true, lastClosedDay = "2026-09-10")
        val today = LocalDate.of(2026, 9, 23) // 12 unclosed days
        val read = mutableListOf<LocalDate>()
        s = Rules.catchUp(s, today) { d -> read += d; good() }
        assertEquals(7, read.size)
        assertEquals(LocalDate.of(2026, 9, 16), read.first())
        assertEquals("2026-09-22", s.lastClosedDay)
        // Running it again the same day changes nothing.
        val again = Rules.catchUp(s, today) { error("should not read") }
        assertEquals(s, again)
    }

    @Test
    fun graceDayForgivesInstallDay() {
        val sim = Sim(monday)
        sim.live(monday, bad(400))
        assertEquals(Rules.START_VITALITY, sim.state.vitality) // no penalty, no weeds, no sun
        sim.live(monday.plusDays(1), bad(400))
        assertEquals(Rules.START_VITALITY - 20 - 3, sim.state.vitality)
    }

    @Test
    fun focusCountsOnlyThreeSessionsADay() {
        val sim = Sim(monday)
        sim.live(monday, good(), focus = 5)
        assertEquals(Rules.START_VITALITY + 10 + 5 + 5 + 9, sim.state.vitality)
        val live = Rules.careItems(good(), 60, 5, closed = false, graceDay = false, previousDayGood = true)
        assertEquals(9, live.first { it.key == "focus" }.delta)
    }

    @Test
    fun seedsCapAtThreeWithoutPlanting() {
        val sim = Sim(monday, plantSeeds = false)
        for (i in 0 until 6) sim.live(monday.plusDays(i.toLong()), good())
        assertEquals(3, sim.state.pendingSeeds)
        // Planting with no seed does nothing.
        var s = sim.state.copy(pendingSeeds = 0)
        s = Rules.plant(s, Flower("z", "poppy", "P", "", monday.toString()))
        assertEquals(0, s.flowers.size)
    }

    @Test
    fun limitChangeAppliesFromTheNextClose() {
        val sim = Sim(monday, limit = 60)
        sim.live(monday, good(45))
        sim.state = sim.state.copy(limitMin = 30)
        sim.live(monday.plusDays(1), DayUsage(45, 100, 300, 30, true, true))
        assertEquals(false, sim.state.days.last().good)
        assertEquals(30, sim.state.days.last().limitMin)
    }

    @Test
    fun losingWithNoFlowersIsSafe() {
        var s = GardenState(installId = "x", seed = 1, createdDay = "2026-09-01", onboarded = true, vitality = 10)
        for (i in 0 until 6) s = Rules.closeDay(s, monday.plusDays(i.toLong()), bad())
        assertEquals(0, s.vitality)
        assertEquals(0, s.flowers.size)
        assertTrue(s.days.none { d -> d.items.any { it.key == "lost" } })
    }

    @Test
    fun liveViewMatchesClosedDayExceptWateringAndComeback() {
        val u = DayUsage(40, 100, 300, 30, true, true)
        val live = Rules.careItems(u, 60, 1, closed = false, graceDay = false, previousDayGood = false)
        val closed = Rules.careItems(u, 60, 1, closed = true, graceDay = false, previousDayGood = false)
        assertEquals(5 + 5 + 3, Rules.liveDelta(live))
        assertEquals(10 + 5 + 5 + 3 + 10, closed.sumOf { it.delta })
        assertTrue(live.first { it.key == "water" }.pending)
        // Live sunlight not yet decided shows as pending, not as a reward.
        val early = Rules.careItems(u.copy(sunlight = null, nightClean = null), 60, 0, closed = false, graceDay = false, previousDayGood = true)
        assertEquals(0, Rules.liveDelta(early))
        assertTrue(early.first { it.key == "sun" }.pending)
    }

    @Test
    fun growthCurve() {
        var s = GardenState(installId = "x", seed = 1, createdDay = "2026-09-01")
        assertEquals(0.3, Rules.age(s), 1e-9)
        s = s.copy(flowers = (0 until 14).map { Flower("$it", "daisy", "", "", "2026-09-01") })
        assertEquals(1.0, Rules.age(s), 1e-9)
        s = s.copy(flowers = s.flowers.map { it.copy(lostDay = "2026-09-09") })
        assertEquals(0.3, Rules.age(s), 1e-9)
    }

    @Test
    fun bandsAndFormatting() {
        assertEquals(Band.THRIVING, Band.of(85))
        assertEquals(Band.HEALTHY, Band.of(84))
        assertEquals(Band.HEALTHY, Band.of(60))
        assertEquals(Band.HOLDING, Band.of(59))
        assertEquals(Band.HOLDING, Band.of(35))
        assertEquals(Band.WILTING, Band.of(34))
        assertEquals("0m", Rules.fmtMin(0))
        assertEquals("59m", Rules.fmtMin(59))
        assertEquals("1h", Rules.fmtMin(60))
        assertEquals("1h 1m", Rules.fmtMin(61))
        assertEquals("0m", Rules.fmtMin(-5))
    }

    @Test
    fun roughRunCountsEveryRoughDayEvenAfterALoss() {
        val start = LocalDate.of(2026, 10, 1)
        var s = GardenState(installId = "r", seed = 1, createdDay = "2026-09-01", onboarded = true, vitality = 40,
            lastClosedDay = start.minusDays(1).toString(),
            flowers = (1..3).map { Flower("f$it", "poppy", "Poppy", "", "2026-09-0$it") })
        s = Rules.closeDay(s, start, good())
        assertEquals(0, Rules.roughRun(s))
        for (i in 1..8) s = Rules.closeDay(s, start.plusDays(i.toLong()), bad())
        // The low-days counter resets each time a flower is lost; the rough run keeps counting.
        assertTrue("a flower was lost", s.flowers.any { !it.alive })
        assertTrue("low-day counter reset", s.lowDays < 3)
        assertEquals(8, Rules.roughRun(s))
        s = Rules.closeDay(s, start.plusDays(9), good())
        assertEquals(0, Rules.roughRun(s))
    }

    @Test
    fun defaultFlowerNameUsesDayAndMonth() {
        val name = Rules.defaultFlowerName("rosebush", LocalDate.of(2026, 10, 7))
        assertTrue(name, name.startsWith("Rose bush, 7 "))
        assertTrue(name, !name.contains("2026"))
    }
}
