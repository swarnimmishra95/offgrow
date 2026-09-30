package app.offgrow.usage

import app.offgrow.garden.DayUsage
import app.offgrow.garden.Rules
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

// Pure maths over usage events. No Android imports, so every scenario can be tested off-device.

/** Raw measurements for one day. All of it stays on the phone. */
data class DayStats(
    val socialMin: Int,
    val screenMin: Int,
    val phoneFreeMin: Int,
    val pickups: Int,
    val wakeAt: Long?,
    val socialInWakeHour: Boolean,
    val nightScreenMin: Int,
    val nightDone: Boolean,
    val wakeHourDone: Boolean,
) {
    fun toUsage(closed: Boolean): DayUsage = DayUsage(
        socialMin = socialMin,
        screenMin = screenMin,
        phoneFreeMin = phoneFreeMin,
        pickups = pickups,
        sunlight = when {
            socialInWakeHour -> false
            wakeHourDone || closed -> true
            else -> null
        },
        nightClean = if (nightDone || closed) nightScreenMin < Rules.NIGHT_LIMIT_MIN else null,
    )
}

/** One usage event, as Android reports it. */
data class UsageEvent(val time: Long, val type: Int, val pkg: String, val cls: String? = null)

object UsageMath {
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE

    // UsageEvents.Event types, as plain numbers so they work on every Android version we support.
    const val RESUMED = 1          // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    const val PAUSED = 2           // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    const val SCREEN_ON = 15       // SCREEN_INTERACTIVE (API 28)
    const val SCREEN_OFF = 16      // SCREEN_NON_INTERACTIVE (API 28)
    const val KEYGUARD_SHOWN = 17  // lock screen showing (API 28)
    const val KEYGUARD_HIDDEN = 18 // phone unlocked (API 28)
    const val STOPPED = 23         // ACTIVITY_STOPPED (API 29)
    const val SHUTDOWN = 26        // DEVICE_SHUTDOWN (API 29)

    /** Where to start reading events for [date], so apps already open are caught. */
    fun queryStart(date: LocalDate, zone: ZoneId): Long =
        date.minusDays(1).atTime(23, 0).atZone(zone).toInstant().toEpochMilli() - 3 * HOUR

    fun compute(
        events: List<UsageEvent>,
        date: LocalDate,
        now: Long,
        zone: ZoneId,
        sdk: Int,
        isSocial: (String) -> Boolean,
    ): DayStats {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val nightStart = date.minusDays(1).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val nightEnd = date.atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        val wakeFloor = date.atTime(4, 0).atZone(zone).toInstant().toEpochMilli()
        val end = min(dayEnd, now)

        var socialMs = 0L
        var screenMs = 0L
        var nightMs = 0L
        var pickups = 0
        var sawKeyguardEvent = false
        var firstUnlockAfterFloor: Long? = null
        var firstUseAfterFloor: Long? = null
        val socialSpans = ArrayList<LongArray>()

        fun overlap(a: Long, b: Long, lo: Long, hi: Long): Long = max(0L, min(b, hi) - max(a, lo))

        fun addSpan(pkg: String, a0: Long, b0: Long, unlocked: Boolean) {
            val a = a0
            val b = min(b0, end)
            if (b <= a) return
            val inDay = overlap(a, b, dayStart, end)
            if (inDay > 0) {
                screenMs += inDay
                if (isSocial(pkg)) {
                    socialMs += inDay
                    socialSpans += longArrayOf(max(a, dayStart), min(b, end))
                }
            }
            // Alarms and incoming calls show over the lock screen; only count night use after an unlock.
            if (unlocked) nightMs += overlap(a, b, nightStart, min(nightEnd, end))
            if (b > wakeFloor && a < end) {
                val s = max(a, wakeFloor)
                val prev = firstUseAfterFloor
                if (prev == null || s < prev) firstUseAfterFloor = s
            }
        }

        var curPkg: String? = null
        var curCls: String? = null
        var curStart = 0L
        var curUnlocked = true
        var unlocked = true

        fun closeCurrent(t: Long) {
            val p = curPkg
            if (p != null) addSpan(p, curStart, t, curUnlocked)
            curPkg = null
            curCls = null
        }

        for (e in events.sortedBy { it.time }) {
            val t = e.time
            if (t > end) break
            when (e.type) {
                RESUMED -> {
                    if (curPkg != e.pkg) {
                        closeCurrent(t)
                        curPkg = e.pkg
                        curStart = t
                        curUnlocked = unlocked
                    }
                    curCls = e.cls
                }
                // Within one app, screen A pauses, screen B resumes, then A stops.
                // Only the pause of the screen that's actually showing ends the span.
                PAUSED -> if (curPkg == e.pkg && (curCls == null || e.cls == null || e.cls == curCls)) closeCurrent(t)
                SCREEN_OFF, SHUTDOWN -> closeCurrent(t)
                KEYGUARD_SHOWN -> {
                    sawKeyguardEvent = true
                    unlocked = false
                }
                KEYGUARD_HIDDEN -> {
                    sawKeyguardEvent = true
                    unlocked = true
                    if (curPkg != null) curUnlocked = true
                    if (t in dayStart until end) pickups++
                    if (t >= wakeFloor && t < end && firstUnlockAfterFloor == null) firstUnlockAfterFloor = t
                }
            }
        }
        closeCurrent(end)

        val wakeAt = firstUnlockAfterFloor ?: firstUseAfterFloor
        val wakeHourEnd = wakeAt?.plus(HOUR)
        val socialInWakeHour = wakeAt != null && wakeHourEnd != null &&
            socialSpans.any { it[1] > wakeAt && it[0] < wakeHourEnd }
        val screenMin = (screenMs / MINUTE).toInt()
        val phoneFreeMin = if (wakeAt == null) 0 else max(0L, (end - wakeAt) / MINUTE - screenMin).toInt()

        return DayStats(
            socialMin = (socialMs / MINUTE).toInt(),
            screenMin = screenMin,
            phoneFreeMin = phoneFreeMin,
            pickups = if (sawKeyguardEvent || sdk >= 28) pickups else -1,
            wakeAt = wakeAt,
            socialInWakeHour = socialInWakeHour,
            nightScreenMin = (nightMs / MINUTE).toInt(),
            nightDone = end >= nightEnd,
            wakeHourDone = wakeHourEnd != null && end >= wakeHourEnd,
        )
    }
}
