package app.offgrow.garden

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The app's idea of "now". Tests can pin it to simulate days and weeks. */
object AppClock {
    @Volatile
    var fixedNow: Long? = null

    @Volatile
    var zoneOverride: ZoneId? = null

    fun now(): Long = fixedNow ?: System.currentTimeMillis()

    fun zone(): ZoneId = zoneOverride ?: ZoneId.systemDefault()

    fun today(): LocalDate = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate()

    fun time(): LocalTime = Instant.ofEpochMilli(now()).atZone(zone()).toLocalTime()
}

/** How long a focus session lasts. Tests shorten it. */
object FocusConfig {
    @Volatile
    var durationMs: Long = Rules.FOCUS_MINUTES * 60_000L
}
