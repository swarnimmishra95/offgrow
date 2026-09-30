package app.offgrow.qa

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.offgrow.R
import app.offgrow.data.Store
import app.offgrow.data.WidgetInfo
import app.offgrow.garden.GardenEngine
import app.offgrow.widget.GardenWideWidgetProvider
import app.offgrow.widget.GardenWidgetProvider
import app.offgrow.widget.WidgetArt
import app.offgrow.widget.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Widgets placed for real through a widget host, at several sizes and in every state. */
@RunWith(AndroidJUnit4::class)
class A6_WidgetTest {

    private fun findImage(v: View): ImageView? {
        if (v is ImageView && v.id == R.id.widget_image) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findImage(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun render(view: View, wDp: Int, hDp: Int): Bitmap {
        val d = Qa.ctx.resources.displayMetrics.density
        val w = (wDp * d).toInt()
        val h = (hDp * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFF2A3D31.toInt())
        view.draw(c)
        return b
    }

    private fun sheet(name: String, caption: String, tiles: List<Bitmap>) {
        val gap = 24
        val out = Bitmap.createBitmap(tiles.sumOf { it.width } + gap * (tiles.size + 1), tiles.maxOf { it.height } + gap * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFF2A3D31.toInt())
        var x = gap
        for (t in tiles) {
            c.drawBitmap(t, x.toFloat(), gap.toFloat(), null)
            x += t.width + gap
        }
        Qa.saveBitmap(out, name, caption, 1000)
    }

    @Test
    fun placedWidgets() {
        val today = LocalDate.of(2026, 10, 14)
        Qa.reset(today, 12)
        Qa.section("A6 · Home-screen widgets")
        Qa.seed(Qa.onboardedState(today.minusDays(20), vitality = 82, flowers = 10, streak = 12, best = 12))
        Qa.fake.days[today] = Qa.stats(38)
        runBlocking { GardenEngine.refresh(Qa.ctx, render = true) }
        Qa.check("Garden picture exists for widgets", GardenEngine.imageFile(Qa.ctx).exists())

        val grant = Qa.shell("appwidget grantbind --package app.offgrow")
        Qa.log("  grantbind: ${grant.trim()}")
        val mgr = AppWidgetManager.getInstance(Qa.ctx)
        var host: AppWidgetHost? = null
        Qa.instrumentation.runOnMainSync {
            host = AppWidgetHost(Qa.ctx, 7301).also { it.startListening() }
        }
        val h = host!!
        val cases = listOf(
            Triple(GardenWidgetProvider::class.java, 150 to 150, "square 2×2"),
            Triple(GardenWidgetProvider::class.java, 250 to 250, "square 3×3"),
            Triple(GardenWidgetProvider::class.java, 330 to 330, "square 4×4"),
            Triple(GardenWideWidgetProvider::class.java, 330 to 150, "wide 4×2"),
            Triple(GardenWideWidgetProvider::class.java, 410 to 150, "wide 5×2"),
            Triple(GardenWidgetProvider::class.java, 330 to 150, "square resized wide"),
        )
        val tiles = ArrayList<Bitmap>()
        val ids = ArrayList<Int>()
        for ((cls, size, label) in cases) {
            val id = h.allocateAppWidgetId()
            ids += id
            val opts = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size.first)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size.first)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size.second)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size.second)
            }
            val bound = mgr.bindAppWidgetIdIfAllowed(id, ComponentName(Qa.ctx, cls), opts)
            Qa.check("Widget $label can be placed", bound)
            if (!bound) continue
            mgr.updateAppWidgetOptions(id, opts)
        }
        runBlocking { WidgetUpdater.updateAll(Qa.ctx) }
        SystemClock.sleep(1500)
        for ((i, c) in cases.withIndex()) {
            val id = ids.getOrNull(i) ?: continue
            val info = mgr.getAppWidgetInfo(id) ?: continue
            var view: AppWidgetHostView? = null
            Qa.instrumentation.runOnMainSync { view = h.createView(Qa.ctx, id, info) }
            SystemClock.sleep(600)
            var bmp: Bitmap? = null
            var hasPicture = false
            Qa.instrumentation.runOnMainSync {
                val v = view!!
                val img = findImage(v)
                hasPicture = (img?.drawable as? BitmapDrawable)?.bitmap?.let { it.width > 100 } == true
                bmp = render(v, c.second.first, c.second.second)
            }
            Qa.check("Widget ${c.third} shows the garden", hasPicture)
            bmp?.let { tiles += it }
        }
        if (tiles.isNotEmpty()) {
            sheet("widgets_placed_small", "Placed widgets: 2×2, 3×3, 4×4", tiles.take(3))
            sheet("widgets_placed_wide", "Placed widgets: wide 4×2, wide 5×2, square resized wide", tiles.drop(3))
        }

        // Tapping the widget opens the app.
        Qa.device.pressHome()
        SystemClock.sleep(1000)
        val firstId = ids.firstOrNull()
        if (firstId != null) {
            var clicked = false
            Qa.instrumentation.runOnMainSync {
                val v = h.createView(Qa.ctx, firstId, mgr.getAppWidgetInfo(firstId))
                val parent = FrameLayout(Qa.ctx)
                parent.addView(v)
                render(parent, 150, 150)
                clicked = findImage(v)?.performClick() == true
            }
            SystemClock.sleep(2500)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                Qa.check("Tapping the widget opens Offgrow", clicked && Qa.device.currentPackageName == "app.offgrow", "foreground ${Qa.device.currentPackageName}")
            } else {
                // Before Android 12 a test can't send the tap with a launcher's rights; a real launcher can.
                Qa.log("  (widget tap not checked on Android ${android.os.Build.VERSION.RELEASE}: needs a real launcher)")
            }
            Qa.closeApp()
        }

        // Every state, drawn directly.
        val garden = WidgetUpdater.loadGardenForWidget(Qa.ctx)
        val d = Qa.ctx.resources.displayMetrics.density
        val states = listOf(
            WidgetInfo(92, "In full bloom", "22m of social left", "12-day streak", "THRIVING", true),
            WidgetInfo(71, "Healthy", "5m of social left", "A seed is waiting", "HEALTHY", true),
            WidgetInfo(48, "Holding on", "12m over today", "A snail on the path", "HOLDING", true),
            WidgetInfo(22, "Wilting", "1h 42m over today", "Flowers can be saved", "WILTING", true),
        )
        for (st in states) {
            val big = WidgetArt.draw(Qa.ctx, (330 * d).toInt(), (330 * d).toInt(), d, st, garden, false)
            val wide = WidgetArt.draw(Qa.ctx, (330 * d).toInt(), (150 * d).toInt(), d, st, garden, true)
            val small = WidgetArt.draw(Qa.ctx, (150 * d).toInt(), (150 * d).toInt(), d, st, garden, false)
            sheet("widget_state_${st.band.lowercase()}", "Widget states: ${st.title} (${st.vitality})", listOf(big, wide, small))
        }
        val notYet = WidgetInfo(70, "Healthy", "Tap to plant your garden", "", "HEALTHY", false)
        val noPicture = WidgetInfo(64, "Healthy", "31m of social left", "Day 3", "HEALTHY", true)
        sheet(
            "widget_state_edge", "Before setup; set up but no picture yet",
            listOf(
                WidgetArt.draw(Qa.ctx, (150 * d).toInt(), (150 * d).toInt(), d, notYet, null, false),
                WidgetArt.draw(Qa.ctx, (330 * d).toInt(), (150 * d).toInt(), d, notYet, null, true),
                WidgetArt.draw(Qa.ctx, (150 * d).toInt(), (150 * d).toInt(), d, noPicture, null, false),
                WidgetArt.draw(Qa.ctx, (330 * d).toInt(), (150 * d).toInt(), d, noPicture, null, true),
            ),
        )
        val w = Store(Qa.ctx).widgetInfo()
        Qa.checkEq("Widget text for 92 vitality", "In full bloom", w?.title)
        Qa.checkEq("Widget status", "22m of social left", w?.status)
        Qa.checkEq("Widget footer shows the streak", "12-day streak", w?.footer)

        Qa.instrumentation.runOnMainSync {
            ids.forEach { h.deleteAppWidgetId(it) }
            h.stopListening()
        }
        Qa.finish()
    }
}
