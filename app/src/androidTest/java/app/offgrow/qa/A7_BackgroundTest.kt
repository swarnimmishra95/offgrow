package app.offgrow.qa

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.offgrow.data.Store
import app.offgrow.garden.GardenEngine
import app.offgrow.garden.GardenRenderer
import app.offgrow.work.RefreshWorker
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The 15-minute background refresh, with the app closed: scoring, drawing and widgets. */
@RunWith(AndroidJUnit4::class)
class A7_BackgroundTest {

    @Test
    fun workerWithAppClosed() {
        val today = LocalDate.of(2026, 10, 14)
        Qa.reset(today, 7)
        Qa.section("A7 · Background refresh with the app closed")
        Qa.seed(Qa.onboardedState(today.minusDays(6), vitality = 66, flowers = 5, lastClosed = today.minusDays(2)))
        Qa.fake.days[today.minusDays(1)] = Qa.stats(35)
        Qa.fake.days[today] = Qa.stats(10, wakeHourDone = false)
        Qa.check("No app window to borrow", GardenRenderer.host?.get() == null)

        val worker = TestListenableWorkerBuilder<RefreshWorker>(Qa.ctx).build()
        val t0 = SystemClock.uptimeMillis()
        val result = runBlocking { worker.doWork() }
        val ms = SystemClock.uptimeMillis() - t0
        Qa.check("Worker succeeds", result is ListenableWorker.Result.Success, "$result")
        Qa.log("  background refresh took ${ms}ms")
        val s = Qa.state()
        Qa.checkEq("Worker closed yesterday", today.minusDays(1).toString(), s.lastClosedDay)
        Qa.checkEq("Yesterday scored (+10 +5 +5)", 86, s.vitality)
        Qa.checkEq("Seed earned overnight", 1, s.pendingSeeds)
        val f = GardenEngine.imageFile(Qa.ctx)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, opts)
        Qa.check("Garden drawn in the background (hidden WebView)", f.exists() && opts.outWidth == GardenEngine.IMAGE_SIZE, "size ${opts.outWidth}x${opts.outHeight}")
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.let { Qa.saveBitmap(it, "background_render", "Drawn by the background refresh at 7am (dawn)", 540) }

        // Time moves to evening: the picture changes to dusk without the app.
        val key1 = Store(Qa.ctx).renderKey
        Qa.setNow(today, 18)
        runBlocking { TestListenableWorkerBuilder<RefreshWorker>(Qa.ctx).build().doWork() }
        val key2 = Store(Qa.ctx).renderKey
        Qa.check("Evening refresh redraws for dusk", key1 != key2 && key2?.contains("\"tod\":\"dusk\"") == true)
        BitmapFactory.decodeFile(f.absolutePath)?.let { Qa.saveBitmap(it, "background_render_dusk", "Background refresh at 6pm (dusk)", 540) }

        // Nothing changed: no redraw.
        val mod = f.lastModified()
        SystemClock.sleep(1100)
        runBlocking { TestListenableWorkerBuilder<RefreshWorker>(Qa.ctx).build().doWork() }
        Qa.checkEq("No redraw when nothing changed", mod, f.lastModified())

        // Access revoked in the background: no crash, nothing judged.
        Qa.fake.access = false
        Qa.setNow(today.plusDays(1), 8)
        val r = runBlocking { TestListenableWorkerBuilder<RefreshWorker>(Qa.ctx).build().doWork() }
        Qa.check("Worker copes without access", r is ListenableWorker.Result.Success)
        Qa.checkEq("Nothing closed without access", today.minusDays(1).toString(), Qa.state().lastClosedDay)
        Qa.checkEq("Widget says access is off", "Usage access is off", Store(Qa.ctx).widgetInfo()?.status)
        Qa.finish()
    }
}
