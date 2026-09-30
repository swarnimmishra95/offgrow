package app.offgrow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.RemoteViews
import app.offgrow.MainActivity
import app.offgrow.R
import app.offgrow.data.Store
import app.offgrow.garden.GardenEngine
import app.offgrow.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Redraw every placed widget from the saved garden image and status. Cheap: no garden render. */
    suspend fun updateAll(context: Context) = withContext(Dispatchers.Default) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val squareIds = mgr.getAppWidgetIds(ComponentName(app, GardenWidgetProvider::class.java))
        val wideIds = mgr.getAppWidgetIds(ComponentName(app, GardenWideWidgetProvider::class.java))
        if (squareIds.isEmpty() && wideIds.isEmpty()) return@withContext

        val info = Store(app).widgetInfo()
        val garden = loadGarden(app)
        for (id in squareIds) update(app, mgr, id, info, garden, wide = false)
        for (id in wideIds) update(app, mgr, id, info, garden, wide = true)
    }

    fun updateAllAsync(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                updateAll(app)
            } catch (e: Exception) {
                Log.w(TAG, "widget update failed", e)
            }
        }
    }

    private fun loadGarden(context: Context): Bitmap? {
        val f = GardenEngine.imageFile(context)
        if (!f.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
            BitmapFactory.decodeFile(f.absolutePath, opts)
        } catch (e: Exception) {
            null
        }
    }

    private fun update(
        context: Context,
        mgr: AppWidgetManager,
        id: Int,
        info: app.offgrow.data.WidgetInfo?,
        garden: Bitmap?,
        wide: Boolean,
    ) {
        try {
            val opts = mgr.getAppWidgetOptions(id)
            // Portrait: widest is min width, tallest is max height.
            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            mgr.updateAppWidget(id, buildViews(context, wDp, hDp, wide, info, garden))
        } catch (e: Exception) {
            Log.w(TAG, "update $id failed", e)
        }
    }

    /** The widget's content for a given size in dp. Public so tests can check every size. */
    fun buildViews(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        wide: Boolean,
        info: app.offgrow.data.WidgetInfo?,
        garden: Bitmap?,
    ): RemoteViews {
        val wDp = if (widthDp > 0) widthDp else if (wide) 250 else 150
        val hDp = if (heightDp > 0) heightDp else if (wide) 120 else 150
        val density = context.resources.displayMetrics.density
        // Cap the bitmap so it fits comfortably through the widget host.
        val longest = max(wDp, hDp) * density
        val scale = min(1f, 720f / longest)
        val wPx = max(120, (wDp * density * scale).roundToInt())
        val hPx = max(120, (hDp * density * scale).roundToInt())

        val art = WidgetArt.draw(context, wPx, hPx, density * scale, info, garden, preferWide = wide)
        val views = RemoteViews(context.packageName, R.layout.widget_garden)
        views.setImageViewBitmap(R.id.widget_image, art)
        val desc = if (info == null || !info.onboarded) "Offgrow garden. Tap to plant your garden."
        else "Offgrow garden: ${info.title}, vitality ${info.vitality}. ${info.status}"
        views.setContentDescription(R.id.widget_image, desc)
        views.setOnClickPendingIntent(R.id.widget_image, openApp(context))
        return views
    }

    fun loadGardenForWidget(context: Context): Bitmap? = loadGarden(context)

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

open class GardenWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.updateAllAsync(context)
        RefreshWorker.runOnce(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        WidgetUpdater.updateAllAsync(context)
    }

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
    }
}

class GardenWideWidgetProvider : GardenWidgetProvider()
