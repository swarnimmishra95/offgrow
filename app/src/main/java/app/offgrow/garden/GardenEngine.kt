package app.offgrow.garden

import android.content.Context
import android.util.Log
import app.offgrow.data.Store
import app.offgrow.data.WidgetInfo
import app.offgrow.usage.DayStats
import app.offgrow.usage.UsageReader
import app.offgrow.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Everything the screens need about "now". */
data class Snapshot(
    val state: GardenState,
    val hasAccess: Boolean,
    val today: DayStats?,
    val todayItems: List<CareItem>,
    val live: Int,
    val band: Band,
    val imageFile: File?,
    val imageVersion: Long,
)

object GardenEngine {
    private const val TAG = "GardenEngine"
    const val IMAGE_SIZE = 1080
    private val mutex = Mutex()

    fun imageFile(context: Context) = File(context.filesDir, "garden.jpg")

    /**
     * Close finished days, work out today's live vitality, redraw the garden if anything
     * visible changed, and refresh the widgets.
     */
    suspend fun refresh(context: Context, render: Boolean = true): Snapshot = mutex.withLock {
        val app = context.applicationContext
        val store = Store(app)
        val reader = UsageReader(app)

        val snap = withContext(Dispatchers.IO) {
            val access = reader.hasAccess()
            val today = LocalDate.now()
            var state = store.load()
            if (state.onboarded && access) {
                state = Rules.catchUp(state, today) { d ->
                    try {
                        reader.readDay(d).toUsage(closed = true)
                    } catch (e: Exception) {
                        Log.w(TAG, "read $d failed", e)
                        null
                    }
                }
            }
            val stats = if (access) reader.readDay(today) else null
            val items = if (stats != null && state.onboarded) {
                Rules.careItems(
                    usage = stats.toUsage(closed = false),
                    limitMin = state.limitMin,
                    focusSessions = state.focusToday(today),
                    closed = false,
                    graceDay = today.toString() == state.createdDay,
                    previousDayGood = state.lastDayGood,
                )
            } else {
                emptyList()
            }
            store.save(state)
            val live = Rules.liveVitality(state, items)
            store.saveWidgetInfo(widgetInfo(state, stats, live, access))
            val file = imageFile(app)
            Snapshot(state, access, stats, items, live, Band.of(live), file.takeIf { it.exists() }, file.lastModified())
        }

        var result = snap
        if (render) {
            val cfg = config(snap.state, snap.live, LocalTime.now())
            val key = renderKey(cfg)
            val file = imageFile(app)
            if (key != store.renderKey || !file.exists()) {
                val bytes = GardenRenderer.renderJpeg(app, cfg, IMAGE_SIZE)
                if (bytes != null) {
                    withContext(Dispatchers.IO) {
                        val tmp = File(app.filesDir, "garden.tmp")
                        tmp.writeBytes(bytes)
                        tmp.renameTo(file)
                    }
                    store.renderKey = key
                    result = snap.copy(imageFile = file, imageVersion = file.lastModified())
                } else {
                    Log.w(TAG, "garden render failed; keeping the last image")
                }
            }
        }
        WidgetUpdater.updateAll(app)
        result
    }

    /** Change the saved garden safely (never at the same time as a refresh). */
    suspend fun update(context: Context, change: (GardenState) -> GardenState): GardenState = mutex.withLock {
        withContext(Dispatchers.IO) {
            val store = Store(context.applicationContext)
            val next = change(store.load())
            store.save(next)
            next
        }
    }

    /** Draw a one-off garden (used by the mood preview in Settings). */
    suspend fun renderPreview(context: Context, cfg: JSONObject, size: Int): android.graphics.Bitmap? =
        GardenRenderer.renderBitmap(context, cfg, size)

    /** Settings for the garden engine. Same inputs always draw the same garden. */
    fun config(state: GardenState, vitality: Int, time: LocalTime, overrideTod: String? = null, overrideAge: Double? = null): JSONObject {
        val kinds = state.aliveFlowers.map { it.kind }.toSet()
        val base = listOf("poppy", "marigold", "rudbeckia", "foxglove", "allium", "daisy", "cosmos", "lavender")
        val species = (STARTERS + kinds.filter { it in base }).distinct()
        val features = JSONObject()
            .put("sunflowers", "sunflower" in kinds)
            .put("tulips", "tulip" in kinds)
            .put("cornflowers", "cornflower" in kinds)
            .put("roses", "rosebush" in kinds)
            .put("hydrangea", "hydrangea" in kinds)
            .put("arch", true)
            .put("birdbath", true)
            .put("pot", true)
            .put("fence", state.fence)
        return JSONObject()
            .put("seed", state.seed)
            .put("vitality", vitality)
            .put("tod", overrideTod ?: timeOfDay(time))
            .put("age", overrideAge ?: Rules.age(state))
            .put("species", JSONArray(species))
            .put("features", features)
            .put("uid", "g")
            .put("label", "Your garden")
    }

    fun timeOfDay(t: LocalTime): String {
        val m = t.hour * 60 + t.minute
        return when {
            m in (5 * 60) until (8 * 60) -> "dawn"
            m in (8 * 60) until (17 * 60) -> "day"
            m in (17 * 60) until (19 * 60 + 30) -> "dusk"
            else -> "night"
        }
    }

    private fun renderKey(cfg: JSONObject): String = "v1:" + cfg.toString()

    private val STARTERS = listOf("daisy", "cosmos", "lavender")

    fun widgetInfo(state: GardenState, stats: DayStats?, live: Int, access: Boolean): WidgetInfo {
        val band = Band.of(live)
        val status = when {
            !state.onboarded -> "Tap to plant your garden"
            !access -> "Usage access is off"
            stats == null -> ""
            stats.socialMin <= state.limitMin -> "${Rules.fmtMin(state.limitMin - stats.socialMin)} of social left"
            else -> "${Rules.fmtMin(stats.socialMin - state.limitMin)} over today"
        }
        val footer = when {
            !state.onboarded -> ""
            state.pendingSeeds > 0 -> "A seed is waiting"
            band == Band.WILTING -> "Flowers can be saved"
            state.streak > 0 -> "${state.streak}-day streak"
            band == Band.HOLDING -> "A snail on the path"
            else -> "Day ${dayNumber(state)}"
        }
        return WidgetInfo(live, band.title, status, footer, band.name, state.onboarded)
    }

    fun dayNumber(state: GardenState): Long {
        val created = try {
            LocalDate.parse(state.createdDay)
        } catch (_: Exception) {
            LocalDate.now()
        }
        return java.time.temporal.ChronoUnit.DAYS.between(created, LocalDate.now()) + 1
    }
}
