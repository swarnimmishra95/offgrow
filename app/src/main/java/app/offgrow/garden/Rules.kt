package app.offgrow.garden

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

// Pure garden logic. No Android imports, so it can be tested on its own.

data class Flower(
    val id: String,
    val kind: String,
    val name: String,
    val note: String,
    val plantedDay: String,
    val lostDay: String? = null,
) {
    val alive: Boolean get() = lostDay == null
}

/** One line of "care" for a day: a reward, a penalty, or something still pending. */
data class CareItem(
    val key: String,
    val label: String,
    val detail: String,
    val delta: Int,
    val pending: Boolean = false,
)

data class DayRecord(
    val day: String,
    val socialMin: Int,
    val limitMin: Int,
    val screenMin: Int,
    val phoneFreeMin: Int,
    val pickups: Int,
    val good: Boolean,
    val delta: Int,
    val vitalityEnd: Int,
    val items: List<CareItem>,
)

data class GardenState(
    val installId: String,
    val seed: Int,
    val createdDay: String,
    val name: String = "",
    val limitMin: Int = 60,
    val vitality: Int = Rules.START_VITALITY,
    val lastClosedDay: String? = null,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val pendingSeeds: Int = 0,
    val seedDay: String? = null,
    val lowDays: Int = 0,
    val lastDayGood: Boolean? = null,
    val flowers: List<Flower> = emptyList(),
    val days: List<DayRecord> = emptyList(),
    val focusDay: String? = null,
    val focusDone: Int = 0,
    val fence: String = "white",
    val onboarded: Boolean = false,
) {
    val aliveFlowers: List<Flower> get() = flowers.filter { it.alive }
    fun focusToday(today: LocalDate): Int = if (focusDay == today.toString()) focusDone else 0
}

/**
 * What we measured for one calendar day.
 * sunlight / nightClean are null while they can't be decided yet (live view) or are unknown.
 */
data class DayUsage(
    val socialMin: Int,
    val screenMin: Int,
    val phoneFreeMin: Int,
    val pickups: Int,
    val sunlight: Boolean?,
    val nightClean: Boolean?,
)

enum class Band(val title: String, val headline: String) {
    THRIVING("In full bloom", "Your garden is blooming"),
    HEALTHY("Healthy", "Your garden is healthy"),
    HOLDING("Holding on", "Your garden is holding on"),
    WILTING("Wilting", "Your garden is wilting");

    companion object {
        fun of(v: Int): Band = when {
            v >= 85 -> THRIVING
            v >= 60 -> HEALTHY
            v >= 35 -> HOLDING
            else -> WILTING
        }
    }
}

/** A flower you can plant. `unlockStreak` is the best streak needed to unlock it. */
data class Plantable(val kind: String, val label: String, val unlockStreak: Int = 0)

object Rules {
    const val START_VITALITY = 70
    const val WATER = 10
    const val SUNLIGHT = 5
    const val ROOTS = 5
    const val WEEDS = -3
    const val COMEBACK = 10
    const val FOCUS = 3
    const val FOCUS_MAX_SESSIONS = 3
    const val FOCUS_MINUTES = 25
    const val NIGHT_LIMIT_MIN = 5
    const val SEED_CAP = 3
    const val LOW_VITALITY = 35
    const val LOST_AFTER_LOW_DAYS = 3
    const val KEEP_DAYS = 90
    const val MAX_CATCH_UP_DAYS = 7

    val PLANTABLES = listOf(
        Plantable("sunflower", "Sunflower"),
        Plantable("poppy", "Poppy"),
        Plantable("cornflower", "Cornflower"),
        Plantable("daisy", "Daisy"),
        Plantable("cosmos", "Cosmos"),
        Plantable("marigold", "Marigold"),
        Plantable("lavender", "Lavender"),
        Plantable("tulip", "Tulip", unlockStreak = 3),
        Plantable("rosebush", "Rose bush", unlockStreak = 7),
        Plantable("hydrangea", "Hydrangea", unlockStreak = 14),
    )

    fun plantable(kind: String): Plantable =
        PLANTABLES.firstOrNull { it.kind == kind } ?: Plantable(kind, kind.replaceFirstChar { it.uppercase() })

    fun clamp(v: Int): Int = max(0, min(100, v))

    fun overPenalty(overMin: Int): Int = when {
        overMin <= 0 -> 0
        overMin <= 30 -> -5
        overMin <= 60 -> -10
        else -> -20
    }

    fun fmtMin(totalMin: Int): String {
        val m = max(0, totalMin)
        return if (m < 60) "${m}m" else if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}m"
    }

    /**
     * Care items for a day. When [closed] is false this is the live view of today:
     * watering and the comeback bonus are only granted when the day closes.
     */
    fun careItems(
        usage: DayUsage,
        limitMin: Int,
        focusSessions: Int,
        closed: Boolean,
        graceDay: Boolean,
        previousDayGood: Boolean?,
    ): List<CareItem> {
        val out = mutableListOf<CareItem>()
        val over = usage.socialMin - limitMin

        // Watering: staying under the social limit.
        if (over <= 0) {
            if (closed) {
                out += CareItem("water", "Watered", "Stayed under ${fmtMin(limitMin)} of social", WATER)
            } else {
                out += CareItem("water", "Watering", "Stay under ${fmtMin(limitMin)} of social today", WATER, pending = true)
            }
        } else {
            val p = if (graceDay) 0 else overPenalty(over)
            out += CareItem("wilt", "Flowers wilting", "${fmtMin(over)} over your ${fmtMin(limitMin)} limit" + if (graceDay) " (first day is free)" else "", p)
        }

        // Morning sunlight: no social apps in the first hour after waking.
        when (usage.sunlight) {
            true -> out += CareItem("sun", "Morning sunlight", "First hour after waking, social-free", SUNLIGHT)
            false -> out += CareItem("shade", "No morning sun", "Social apps in the first hour after waking", 0)
            null -> if (!closed) out += CareItem("sun", "Morning sunlight", "Skip social apps for an hour after waking", SUNLIGHT, pending = true)
        }

        // Deep roots: a clean night window.
        when (usage.nightClean) {
            true -> out += CareItem("roots", "Deep roots", "Clean night, 11pm to 6am", ROOTS)
            false -> out += CareItem("weeds", "Weeds crept in", "Phone used in your night window", if (graceDay) 0 else WEEDS)
            null -> {}
        }

        val sessions = min(focusSessions, FOCUS_MAX_SESSIONS)
        if (sessions > 0) {
            out += CareItem("focus", "Focus sessions", "$sessions × ${FOCUS_MINUTES} min", sessions * FOCUS)
        }

        if (closed && over <= 0 && previousDayGood == false) {
            out += CareItem("comeback", "Comeback bonus", "A good day after a rough one", COMEBACK)
        }
        return out
    }

    fun liveDelta(items: List<CareItem>): Int = items.filter { !it.pending }.sumOf { it.delta }

    /** Vitality right now: the settled value plus today's partial changes. */
    fun liveVitality(state: GardenState, todayItems: List<CareItem>): Int =
        clamp(state.vitality + liveDelta(todayItems))

    /** Close one finished day. [usage] is null when we couldn't read usage (permission off). */
    fun closeDay(state: GardenState, day: LocalDate, usage: DayUsage?): GardenState {
        val dayStr = day.toString()
        if (usage == null) return state.copy(lastClosedDay = dayStr)

        val grace = dayStr == state.createdDay
        val good = usage.socialMin <= state.limitMin
        val items = careItems(
            usage = usage,
            limitMin = state.limitMin,
            focusSessions = state.focusToday(day),
            closed = true,
            graceDay = grace,
            previousDayGood = state.lastDayGood,
        ).toMutableList()
        val delta = items.sumOf { it.delta }
        val end = clamp(state.vitality + delta)

        var flowers = state.flowers
        var lowDays = if (end < LOW_VITALITY) state.lowDays + 1 else 0
        if (lowDays >= LOST_AFTER_LOW_DAYS) {
            val victim = flowers.lastOrNull { it.alive }
            if (victim != null) {
                flowers = flowers.map { if (it.id == victim.id) it.copy(lostDay = dayStr) else it }
                items += CareItem("lost", "A flower was lost", "${victim.name} couldn't hold on", 0)
            }
            lowDays = 0
        }

        val streak = if (good) state.streak + 1 else 0
        val record = DayRecord(
            day = dayStr,
            socialMin = usage.socialMin,
            limitMin = state.limitMin,
            screenMin = usage.screenMin,
            phoneFreeMin = usage.phoneFreeMin,
            pickups = usage.pickups,
            good = good,
            delta = delta,
            vitalityEnd = end,
            items = items,
        )
        return state.copy(
            vitality = end,
            lastClosedDay = dayStr,
            streak = streak,
            bestStreak = max(state.bestStreak, streak),
            pendingSeeds = if (good) min(SEED_CAP, state.pendingSeeds + 1) else state.pendingSeeds,
            seedDay = if (good) dayStr else state.seedDay,
            lowDays = lowDays,
            lastDayGood = good,
            flowers = flowers,
            days = (state.days + record).takeLast(KEEP_DAYS),
        )
    }

    /**
     * Close every finished day since the last one we closed, oldest first.
     * [read] returns usage for a day, or null if it can't be read.
     */
    fun catchUp(state: GardenState, today: LocalDate, read: (LocalDate) -> DayUsage?): GardenState {
        if (!state.onboarded) return state
        val last = state.lastClosedDay?.let { LocalDate.parse(it) } ?: today.minusDays(1)
        var s = state
        var d = last.plusDays(1)
        val oldest = today.minusDays(MAX_CATCH_UP_DAYS.toLong())
        if (d.isBefore(oldest)) {
            // Too far back to read reliably: skip ahead without judging those days.
            s = s.copy(lastClosedDay = oldest.minusDays(1).toString())
            d = oldest
        }
        while (d.isBefore(today)) {
            s = closeDay(s, d, read(d))
            d = d.plusDays(1)
        }
        return s
    }

    fun plant(state: GardenState, flower: Flower): GardenState {
        if (state.pendingSeeds <= 0) return state
        return state.copy(flowers = state.flowers + flower, pendingSeeds = state.pendingSeeds - 1)
    }

    fun completeFocus(state: GardenState, today: LocalDate): GardenState {
        val t = today.toString()
        val done = if (state.focusDay == t) state.focusDone else 0
        return state.copy(focusDay = t, focusDone = done + 1)
    }

    /** How grown the garden is (0..1). Starts part-grown so day one already looks like a garden. */
    fun age(state: GardenState): Double = min(1.0, 0.3 + state.aliveFlowers.size * 0.05)
}
