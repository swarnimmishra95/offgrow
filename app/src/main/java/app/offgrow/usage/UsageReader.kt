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
import android.util.Log
import app.offgrow.data.Store
import app.offgrow.garden.AppClock
import java.time.LocalDate

/** Where usage numbers come from. The real phone, or a scripted source in tests. */
interface UsageSource {
    fun hasAccess(): Boolean
    fun readDay(date: LocalDate, now: Long = AppClock.now()): DayStats
}

object Usage {
    /** Tests set this to feed scripted days. */
    @Volatile
    var override: UsageSource? = null

    fun source(context: Context): UsageSource = override ?: UsageReader(context.applicationContext)
}

class UsageReader(private val context: Context) : UsageSource {
    private val store = Store(context)
    private val pm: PackageManager = context.packageManager
    private val socialCache = HashMap<String, Boolean>()

    override fun hasAccess(): Boolean {
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
        val v = when (pkg) {
            in store.excludedApps -> false
            in store.includedApps -> true
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
    override fun readDay(date: LocalDate, now: Long): DayStats {
        val zone = AppClock.zone()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(dayEnd, now)
        val events = ArrayList<UsageEvent>(2048)
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stream = usm.queryEvents(UsageMath.queryStart(date, zone), end)
            val e = UsageEvents.Event()
            while (stream.hasNextEvent()) {
                stream.getNextEvent(e)
                val type = e.eventType
                if (type == UsageMath.RESUMED || type == UsageMath.PAUSED || type == UsageMath.SCREEN_OFF ||
                    type == UsageMath.KEYGUARD_SHOWN || type == UsageMath.KEYGUARD_HIDDEN || type == UsageMath.SHUTDOWN
                ) {
                    events += UsageEvent(e.timeStamp, type, e.packageName ?: "", e.className)
                }
            }
        } catch (ex: Exception) {
            // Permission revoked mid-read or a platform quirk: work with what we have.
            Log.w("UsageReader", "queryEvents failed", ex)
        }
        return UsageMath.compute(events, date, now, zone, Build.VERSION.SDK_INT) { isSocial(it) }
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
