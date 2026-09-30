package app.offgrow.qa

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.offgrow.data.Store
import app.offgrow.garden.Band
import app.offgrow.garden.Flower
import app.offgrow.garden.GardenEngine
import app.offgrow.garden.Rules
import app.offgrow.usage.DayStats
import app.offgrow.widget.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Lives through weeks of scripted usage with the real engine: each evening checks today's
 * live vitality, each morning checks the closed day against an independent statement of the rules.
 */
@RunWith(AndroidJUnit4::class)
class A3_WeeksSimulationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    init {
        Qa.compose = compose
    }

    class Plan(val stats: DayStats, val focus: Int = 0, val access: Boolean = true)

    private fun persona(
        title: String,
        start: LocalDate,
        days: Int,
        checkpoints: Set<Int>,
        plant: Boolean = true,
        plan: (Int, LocalDate) -> Plan,
    ) {
        Qa.section("A3 · $title ($days days from $start)")
        Qa.reset(start, 8)
        Qa.seed(
            Store.newState().copy(
                seed = title.hashCode() and 0xfffff,
                onboarded = true,
                name = title.substringBefore(' '),
                createdDay = start.toString(),
                lastClosedDay = start.minusDays(1).toString(),
                pendingSeeds = 1,
                fence = listOf("white", "wood", "sage", "blue")[(title.length) % 4],
            ),
        )
        val o = Oracle(start)
        val unclosed = ArrayList<Pair<LocalDate, Plan>>()
        var mismatches = 0
        val table = StringBuilder("| Day | Social | Focus | Change | Vitality | Streak | Seeds | Flowers |\n|---|---|---|---|---|---|---|---|\n")

        for (i in 0 until days) {
            val d = start.plusDays(i.toLong())
            val p = plan(i, d)

            // Morning: the user plants any seeds they've earned.
            Qa.setNow(d, 9)
            if (plant) {
                while (Qa.state().pendingSeeds > 0) {
                    val kind = Rules.PLANTABLES[o.planted % 7].kind
                    runBlocking {
                        GardenEngine.update(Qa.ctx) { s ->
                            Rules.plant(s, Flower("f${o.planted}", kind, "${Rules.plantable(kind).label}, $d", "", d.toString()))
                        }
                    }
                    o.planted++
                    o.seeds--
                }
            }
            repeat(p.focus) { runBlocking { GardenEngine.update(Qa.ctx) { s -> Rules.completeFocus(s, d) } } }

            // Afternoon: check today's live figures.
            Qa.fake.days[d] = p.stats
            Qa.fake.access = p.access
            Qa.setNow(d, 13)
            val shoot = i in checkpoints
            val snap = runBlocking { GardenEngine.refresh(Qa.ctx, render = shoot) }
            if (p.access) {
                val want = o.live(p.stats.toUsage(closed = false), p.focus, grace = d == start)
                if (snap.live != want) mismatches++
                if (shoot || snap.live != want) Qa.checkEq("Day ${i + 1} live vitality", want, snap.live)
                val w = Store(Qa.ctx).widgetInfo()
                if (w?.title != Band.of(snap.live).title) Qa.check("Day ${i + 1} widget title follows vitality", false, "${w?.title} vs ${snap.live}")
            } else {
                if (snap.hasAccess) Qa.check("Day ${i + 1} access off is noticed", false)
            }
            if (shoot) checkpoint(title, i + 1, d)
            unclosed += d to p

            // Next morning: the day closes (only when the app can read usage).
            Qa.setNow(d.plusDays(1), 7, 30)
            val nextAccess = if (i + 1 < days) plan(i + 1, d.plusDays(1)).access else true
            Qa.fake.access = nextAccess
            runBlocking { GardenEngine.refresh(Qa.ctx, render = false) }
            if (nextAccess) {
                val oldest = d.plusDays(1).minusDays(Rules.MAX_CATCH_UP_DAYS.toLong())
                for ((day, plan0) in unclosed) {
                    if (day.isBefore(oldest)) continue
                    val change = o.close(day, plan0.stats.toUsage(closed = true), plan0.focus)
                    val s = Qa.state()
                    val rec = s.days.lastOrNull { it.day == day.toString() }
                    table.append("| ${day.dayOfWeek.toString().take(3)} ${day.dayOfMonth} | ${plan0.stats.socialMin}m | ${plan0.focus} | ${rec?.delta ?: "—"} (exp $change) | ${rec?.vitalityEnd ?: "—"} | | | |\n")
                    if (rec == null) {
                        mismatches++
                        Qa.check("Day $day was closed", false)
                    } else if (rec.delta != change) {
                        mismatches++
                        Qa.checkEq("Day $day change", change, rec.delta)
                    }
                }
                unclosed.clear()
                val s = Qa.state()
                val ok = s.vitality == o.v && s.streak == o.streak && s.bestStreak == o.best &&
                    s.pendingSeeds == o.seeds && s.flowers.count { !it.alive } == o.lost &&
                    s.lastClosedDay == d.toString()
                if (!ok) {
                    mismatches++
                    Qa.check(
                        "Morning after day ${i + 1}: garden matches the rules", false,
                        "v ${s.vitality}/${o.v} streak ${s.streak}/${o.streak} best ${s.bestStreak}/${o.best} seeds ${s.pendingSeeds}/${o.seeds} lost ${s.flowers.count { !it.alive }}/${o.lost} closed ${s.lastClosedDay}",
                    )
                }
                val last = table.lastIndexOf("| | | |\n")
                if (last >= 0) table.replace(last, last + 8, "| ${s.streak} | ${s.pendingSeeds} | ${s.aliveFlowers.size}/${s.flowers.size} |\n")
            }
        }
        Qa.check("$title: every day matched the rules", mismatches == 0, "$mismatches mismatches")
        val end = Qa.state()
        Qa.log("\nFinal: vitality ${end.vitality}, streak ${end.streak} (best ${end.bestStreak}), flowers ${end.aliveFlowers.size} alive / ${end.flowers.size} planted, seeds ${end.pendingSeeds}\n")
        Qa.log(table.toString())
        journal(title)
    }

    private fun checkpoint(title: String, day: Int, date: LocalDate) {
        Qa.launchApp()
        Qa.waitText(compose, "Today's care", 20_000)
        Qa.settle(compose)
        val s = Qa.state()
        Qa.shot("${title.substringBefore(' ').lowercase()}_day$day", "$title · day $day (${date.dayOfWeek.toString().lowercase()}) · base vitality ${s.vitality}")
        Qa.closeApp()
        widgetShots("${title.substringBefore(' ').lowercase()}_day$day")
    }

    private fun widgetShots(name: String) {
        val info = Store(Qa.ctx).widgetInfo()
        val garden = WidgetUpdater.loadGardenForWidget(Qa.ctx)
        val sizes = listOf(Triple(320, 320, false), Triple(320, 150, true))
        val bitmaps = ArrayList<Bitmap>()
        for ((w, h, wide) in sizes) {
            Qa.instrumentation.runOnMainSync {
                val rv = WidgetUpdater.buildViews(Qa.ctx, w, h, wide, info, garden)
                val parent = FrameLayout(Qa.ctx)
                val v = rv.apply(Qa.ctx, parent)
                val density = Qa.ctx.resources.displayMetrics.density
                val pw = (w * density).toInt()
                val ph = (h * density).toInt()
                v.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
                v.layout(0, 0, pw, ph)
                val b = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                c.drawColor(0xFF2A3D31.toInt())
                v.draw(c)
                bitmaps += b
            }
        }
        // Side by side, like a home screen.
        val gap = 24
        val out = Bitmap.createBitmap(bitmaps.sumOf { it.width } + gap * (bitmaps.size + 1), bitmaps.maxOf { it.height } + gap * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFF2A3D31.toInt())
        var x = gap
        for (b in bitmaps) {
            c.drawBitmap(b, x.toFloat(), gap.toFloat(), null)
            x += b.width + gap
        }
        Qa.saveBitmap(out, "${name}_widgets", "Widgets: ${info?.title}, ${info?.status}, ${info?.footer}", 900)
    }

    private fun journal(title: String) {
        Qa.launchApp()
        Qa.waitText(compose, "Today's care", 20_000)
        Qa.settle(compose)
        compose.onAllNodesWithText("Journal").onFirst().performClick()
        Qa.waitText(compose, "Garden journal")
        Qa.shot("${title.substringBefore(' ').lowercase()}_journal", "$title · journal")
        if (Qa.exists(compose, "Yesterday")) {
            compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp(durationMillis = 300) }
            Qa.shot("${title.substringBefore(' ').lowercase()}_journal_days", "$title · journal, day list")
        }
        Qa.closeApp()
    }

    private fun good(social: Int, night: Int = 0) = Qa.stats(social, sunlight = true, nightMin = night, pickups = 35 + social / 3)
    private fun bad(social: Int, night: Int = 30) = Qa.stats(social, sunlight = false, nightMin = night, pickups = 90 + social / 4, free = 120)

    @Test
    fun steadyMonth() {
        persona("Steady month, under the limit every day", LocalDate.of(2026, 10, 5), 28, setOf(0, 6, 13, 27)) { i, d ->
            val weekday = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY
            Plan(good(20 + (i * 7) % 26, night = if (i % 5 == 0) 3 else 0), focus = if (weekday) 1 else 0)
        }
        Qa.finish()
    }

    @Test
    fun doomscroller() {
        persona("Doomscroller who slides after three good days", LocalDate.of(2026, 10, 5), 21, setOf(2, 6, 13, 20)) { i, _ ->
            if (i < 3) Plan(good(35)) else Plan(bad(150 + (i * 13) % 120, night = 20 + i))
        }
        val s = Qa.state()
        Qa.check("Doomscroller ends wilting", Band.of(s.vitality) == Band.WILTING, "vitality ${s.vitality}")
        Qa.check("Doomscroller lost flowers", s.flowers.any { !it.alive })
        Qa.finish()
    }

    @Test
    fun weekendBinger() {
        persona("Weekend binger, good weekdays", LocalDate.of(2026, 10, 5), 28, setOf(5, 7, 27)) { _, d ->
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            if (weekend) Plan(bad(210, night = 45)) else Plan(good(40))
        }
        val comebacks = Qa.state().days.count { d -> d.items.any { it.key == "comeback" } }
        Qa.checkEq("A comeback bonus every Monday after a binge (3 Mondays)", 3, comebacks)
        Qa.finish()
    }

    @Test
    fun comebackAfterCollapse() {
        persona("Comeback after ten rough days", LocalDate.of(2026, 10, 5), 24, setOf(9, 10, 23)) { i, _ ->
            if (i < 10) Plan(bad(240, night = 60)) else Plan(good(30), focus = 2)
        }
        val s = Qa.state()
        Qa.check("Recovered to thriving", s.vitality >= 85, "vitality ${s.vitality}")
        Qa.finish()
    }

    @Test
    fun accessGapsAndPhoneOff() {
        // Usage access switched off on days 5-7: those days are judged once it's back on.
        persona("Access switched off for three days", LocalDate.of(2026, 10, 5), 12, setOf(5, 8)) { i, _ ->
            Plan(if (i in 5..6) bad(130) else good(30), access = i !in 4..6)
        }
        Qa.finish()

        // Phone off for 12 days: only the last 7 are judged when it comes back.
        Qa.section("A3 · Phone off for 12 days")
        val start = LocalDate.of(2026, 11, 2)
        Qa.reset(start, 9)
        Qa.seed(Qa.onboardedState(start.minusDays(3), vitality = 80, flowers = 2, lastClosed = start.minusDays(1)))
        for (k in 0 until 12) Qa.fake.days[start.plusDays(k.toLong())] = good(25)
        Qa.setNow(start.plusDays(12), 8)
        runBlocking { GardenEngine.refresh(Qa.ctx, render = false) }
        val s = Qa.state()
        Qa.checkEq("Only 7 days judged", 7, s.days.size)
        Qa.checkEq("Closed up to yesterday", start.plusDays(11).toString(), s.lastClosedDay)
        Qa.checkEq("First judged day", start.plusDays(5).toString(), s.days.firstOrNull()?.day)
        Qa.finish()
    }
}
