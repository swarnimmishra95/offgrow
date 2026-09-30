package app.offgrow.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import app.offgrow.data.Store
import app.offgrow.garden.DayUsage
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

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
        nightClean = if (nightDone || closed) nightScreenMin < app.offgrow.garden.Rules.NIGHT_LIMIT_MIN else null,
    )
}

class UsageReader(private val context: Context) {
    private val store = Store(context)
    private val pm: PackageManager = context.packageManager
    private val socialCache = HashMap<String, Boolean>()

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun isSocial(pkg: String): Boolean {
        socialCache[pkg]?.let { return it }
        val included = store.includedApps
        val excluded = store.excludedApps
        val v = when {
            pkg in excluded -> false
            pkg in included -> true
            else -> isSocialByDefault(pkg)
        }
        socialCache[pkg] = v
        return v
    }

    fun isSocialByDefault(pkg: String): Boolean {
        if (pkg in SocialApps.KNOWN) return true
        return try {
            val info = pm.getApplicationInfo(pkg, 0)
            info.category == ApplicationInfo.CATEGORY_SOCIAL
        } catch (_: Exception) {
            false
        }
    }

    /** Launchable apps on the phone, for the "apps that count" setting. */
    fun launchableApps(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = try {
            pm.queryIntentActivities(intent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        return list
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != context.packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    /** Read one calendar day. For today, figures are "so far". */
    fun readDay(date: LocalDate, now: Long = System.currentTimeMillis()): DayStats {
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val nightStart = date.minusDays(1).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val nightEnd = date.atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        val wakeFloor = date.atTime(4, 0).atZone(zone).toInstant().toEpochMilli()
        val end = min(dayEnd, now)
        val queryStart = nightStart - 3 * HOUR

        var socialMs = 0L
        var screenMs = 0L
        var nightMs = 0L
        var pickups = 0
        var sawKeyguardEvent = false
        var firstUnlockAfterFloor: Long? = null
        var firstUseAfterFloor: Long? = null
        val socialSpans = ArrayList<LongArray>()

        fun overlap(a: Long, b: Long, lo: Long, hi: Long): Long = max(0L, min(b, hi) - max(a, lo))

        fun addSpan(pkg: String, a: Long, b: Long) {
            if (b <= a) return
            val inDay = overlap(a, b, dayStart, end)
            if (inDay > 0) {
                screenMs += inDay
                if (isSocial(pkg)) {
                    socialMs += inDay
                    socialSpans += longArrayOf(max(a, dayStart), min(b, end))
                }
            }
            nightMs += overlap(a, b, nightStart, min(nightEnd, end))
            if (b > wakeFloor && a < end) {
                val s = max(a, wakeFloor)
                if (firstUseAfterFloor == null || s < firstUseAfterFloor!!) firstUseAfterFloor = s
            }
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        var curPkg: String? = null
        var curStart = 0L
        fun closeCurrent(t: Long) {
            val p = curPkg
            if (p != null) addSpan(p, curStart, t)
            curPkg = null
        }

        try {
            val events = usm.queryEvents(queryStart, end)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val t = e.timeStamp
                val pkg = e.packageName ?: continue
                when (e.eventType) {
                    EVENT_RESUMED -> {
                        if (curPkg != pkg) {
                            closeCurrent(t)
                            curPkg = pkg
                            curStart = t
                        }
                    }
                    EVENT_PAUSED, EVENT_STOPPED -> if (curPkg == pkg) closeCurrent(t)
                    EVENT_SCREEN_OFF, EVENT_SHUTDOWN -> closeCurrent(t)
                    EVENT_KEYGUARD_HIDDEN -> {
                        sawKeyguardEvent = true
                        if (t in dayStart until end) pickups++
                        if (t >= wakeFloor && t < end && firstUnlockAfterFloor == null) firstUnlockAfterFloor = t
                    }
                }
            }
            closeCurrent(end)
        } catch (_: Exception) {
            // Permission revoked mid-read or a platform quirk: return what we have.
        }

        val wakeAt = firstUnlockAfterFloor ?: firstUseAfterFloor
        val wakeHourEnd = wakeAt?.plus(HOUR)
        val socialInWakeHour = wakeAt != null && socialSpans.any { it[1] > wakeAt && it[0] < wakeHourEnd!! }
        val screenMin = (screenMs / MINUTE).toInt()
        val phoneFreeMin = if (wakeAt == null) 0 else max(0L, (end - wakeAt) / MINUTE - screenMin).toInt()

        return DayStats(
            socialMin = (socialMs / MINUTE).toInt(),
            screenMin = screenMin,
            phoneFreeMin = phoneFreeMin,
            pickups = if (sawKeyguardEvent || Build.VERSION.SDK_INT >= 28) pickups else -1,
            wakeAt = wakeAt,
            socialInWakeHour = socialInWakeHour,
            nightScreenMin = (nightMs / MINUTE).toInt(),
            nightDone = end >= nightEnd,
            wakeHourDone = wakeHourEnd != null && end >= wakeHourEnd,
        )
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE

        // UsageEvents.Event types, as plain numbers so they work on every Android version we support.
        private const val EVENT_RESUMED = 1       // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
        private const val EVENT_PAUSED = 2        // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
        private const val EVENT_SCREEN_OFF = 16   // SCREEN_NON_INTERACTIVE (API 28)
        private const val EVENT_KEYGUARD_HIDDEN = 18 // phone unlocked (API 28)
        private const val EVENT_STOPPED = 23      // ACTIVITY_STOPPED (API 29)
        private const val EVENT_SHUTDOWN = 26     // DEVICE_SHUTDOWN (API 29)
    }
}

object SocialApps {
    /** Well-known social and short-video apps. Anything the phone labels "social" also counts. */
    val KNOWN = setOf(
        "com.instagram.android",
        "com.instagram.barcelona",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.twitter.android",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.pinterest",
        "com.linkedin.android",
        "com.google.android.youtube",
        "com.tumblr",
        "com.bereal.ft",
        "xyz.blueskyweb.app",
        "in.mohalla.sharechat",
        "in.mohalla.video",
        "com.eterno.shortvideos",
        "com.roposo.android",
        "com.ninegag.android.app",
        "com.quora.android",
        "tv.twitch.android.app",
        "com.vkontakte.android",
        "com.kwai.video",
        "com.lemon8.android",
    )
}
