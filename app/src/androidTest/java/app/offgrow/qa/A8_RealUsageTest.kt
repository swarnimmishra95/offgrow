package app.offgrow.qa

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.offgrow.data.Store
import app.offgrow.garden.AppClock
import app.offgrow.usage.Usage
import app.offgrow.usage.UsageReader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android usage data (no fakes): does the app measure time in an app it's told to count? */
@RunWith(AndroidJUnit4::class)
class A8_RealUsageTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun realUsageStats() {
        Qa.closeApp()
        Qa.section("A8 · Real usage data from Android")
        Usage.override = null
        AppClock.fixedNow = null
        AppClock.zoneOverride = null
        val ctx = Qa.ctx
        Qa.shell("appops set app.offgrow GET_USAGE_STATS allow")
        SystemClock.sleep(500)
        val reader = UsageReader(ctx)
        Qa.check("Usage access granted", reader.hasAccess())

        // Count Android Settings as a "social" app for this test.
        Store(ctx).includedApps = setOf("com.android.settings")
        val before = UsageReader(ctx).readDay(AppClock.today())
        Qa.log("  before: social ${before.socialMin}m, screen ${before.screenMin}m, pickups ${before.pickups}")

        // 75s on the main Settings screen, then 75s on a sub-page (a different screen in the same app).
        ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        SystemClock.sleep(75_000)
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", "app.offgrow", null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        SystemClock.sleep(75_000)
        Qa.shot("real_usage_settings", "Settings open for the real-usage check")
        Qa.device.pressHome()
        SystemClock.sleep(4000)

        val after = UsageReader(ctx).readDay(AppClock.today())
        Qa.log("  after: social ${after.socialMin}m, screen ${after.screenMin}m, pickups ${after.pickups}, wake ${after.wakeAt}")
        val gained = after.socialMin - before.socialMin
        Qa.check("About 2½ minutes counted across both Settings screens", gained in 2..3, "gained ${gained}m")
        Qa.check("Screen time at least social time", after.screenMin >= after.socialMin)
        Qa.check("Pickups are known on this Android version", after.pickups >= 0)

        // Revoke access: the app notices.
        Qa.shell("appops set app.offgrow GET_USAGE_STATS ignore")
        SystemClock.sleep(500)
        Qa.check("Revoked access detected", !UsageReader(ctx).hasAccess())
        Qa.shell("appops set app.offgrow GET_USAGE_STATS allow")
        Store(ctx).includedApps = emptySet()
        Qa.finish()
    }
}
