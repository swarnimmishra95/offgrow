package app.offgrow.data

import android.content.Context
import android.content.SharedPreferences
import app.offgrow.garden.CareItem
import app.offgrow.garden.DayRecord
import app.offgrow.garden.Flower
import app.offgrow.garden.GardenState
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** What the widget shows. Written after every refresh so widgets can redraw without recomputing. */
data class WidgetInfo(
    val vitality: Int,
    val title: String,
    val status: String,
    val footer: String,
    val band: String,
    val onboarded: Boolean,
)

/** Everything is stored on the phone, in the app's private preferences. */
class Store(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("offgrow", Context.MODE_PRIVATE)

    fun load(): GardenState {
        val raw = prefs.getString(KEY_STATE, null)
        if (raw != null) {
            try {
                return stateFromJson(JSONObject(raw))
            } catch (_: Exception) {
                // Fall through to a fresh garden if the saved data is unreadable.
            }
        }
        val fresh = newState()
        save(fresh)
        return fresh
    }

    fun save(state: GardenState) {
        prefs.edit().putString(KEY_STATE, stateToJson(state).toString()).apply()
    }

    var renderKey: String?
        get() = prefs.getString(KEY_RENDER, null)
        set(v) { prefs.edit().putString(KEY_RENDER, v).apply() }

    var includedApps: Set<String>
        get() = prefs.getStringSet(KEY_INCLUDED, emptySet())?.toSet() ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_INCLUDED, v).apply() }

    var excludedApps: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDED, emptySet())?.toSet() ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_EXCLUDED, v).apply() }

    fun saveWidgetInfo(info: WidgetInfo) {
        val o = JSONObject()
            .put("v", info.vitality).put("title", info.title).put("status", info.status)
            .put("footer", info.footer).put("band", info.band).put("onboarded", info.onboarded)
        prefs.edit().putString(KEY_WIDGET, o.toString()).apply()
    }

    fun widgetInfo(): WidgetInfo? {
        val raw = prefs.getString(KEY_WIDGET, null) ?: return null
        return try {
            val o = JSONObject(raw)
            WidgetInfo(
                vitality = o.optInt("v", 70),
                title = o.optString("title", "Healthy"),
                status = o.optString("status", ""),
                footer = o.optString("footer", ""),
                band = o.optString("band", "HEALTHY"),
                onboarded = o.optBoolean("onboarded", false),
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_STATE = "state"
        private const val KEY_RENDER = "render_key"
        private const val KEY_INCLUDED = "apps_included"
        private const val KEY_EXCLUDED = "apps_excluded"
        private const val KEY_WIDGET = "widget_info"

        fun newState(): GardenState {
            val id = UUID.randomUUID().toString()
            val today = LocalDate.now()
            return GardenState(
                installId = id,
                seed = (id.hashCode() and 0x7fffffff) % 1_000_000,
                createdDay = today.toString(),
                fence = listOf("white", "white", "wood", "sage", "blue")[(id.hashCode() and 0x7fffffff) % 5],
            )
        }

        fun stateToJson(s: GardenState): JSONObject = JSONObject()
            .put("installId", s.installId)
            .put("seed", s.seed)
            .put("createdDay", s.createdDay)
            .put("name", s.name)
            .put("limitMin", s.limitMin)
            .put("vitality", s.vitality)
            .put("lastClosedDay", s.lastClosedDay ?: JSONObject.NULL)
            .put("streak", s.streak)
            .put("bestStreak", s.bestStreak)
            .put("pendingSeeds", s.pendingSeeds)
            .put("seedDay", s.seedDay ?: JSONObject.NULL)
            .put("lowDays", s.lowDays)
            .put("lastDayGood", s.lastDayGood ?: JSONObject.NULL)
            .put("flowers", JSONArray().apply { s.flowers.forEach { put(flowerToJson(it)) } })
            .put("days", JSONArray().apply { s.days.forEach { put(dayToJson(it)) } })
            .put("focusDay", s.focusDay ?: JSONObject.NULL)
            .put("focusDone", s.focusDone)
            .put("fence", s.fence)
            .put("onboarded", s.onboarded)

        fun stateFromJson(o: JSONObject): GardenState = GardenState(
            installId = o.getString("installId"),
            seed = o.optInt("seed", 11),
            createdDay = o.optString("createdDay", LocalDate.now().toString()),
            name = o.optString("name", ""),
            limitMin = o.optInt("limitMin", 60),
            vitality = o.optInt("vitality", 70),
            lastClosedDay = o.optStringOrNull("lastClosedDay"),
            streak = o.optInt("streak", 0),
            bestStreak = o.optInt("bestStreak", 0),
            pendingSeeds = o.optInt("pendingSeeds", 0),
            seedDay = o.optStringOrNull("seedDay"),
            lowDays = o.optInt("lowDays", 0),
            lastDayGood = if (o.isNull("lastDayGood") || !o.has("lastDayGood")) null else o.optBoolean("lastDayGood"),
            flowers = o.optJSONArray("flowers").mapObjects { flowerFromJson(it) },
            days = o.optJSONArray("days").mapObjects { dayFromJson(it) },
            focusDay = o.optStringOrNull("focusDay"),
            focusDone = o.optInt("focusDone", 0),
            fence = o.optString("fence", "white"),
            onboarded = o.optBoolean("onboarded", false),
        )

        private fun flowerToJson(f: Flower) = JSONObject()
            .put("id", f.id).put("kind", f.kind).put("name", f.name).put("note", f.note)
            .put("plantedDay", f.plantedDay).put("lostDay", f.lostDay ?: JSONObject.NULL)

        private fun flowerFromJson(o: JSONObject) = Flower(
            id = o.getString("id"),
            kind = o.getString("kind"),
            name = o.optString("name", ""),
            note = o.optString("note", ""),
            plantedDay = o.optString("plantedDay", ""),
            lostDay = o.optStringOrNull("lostDay"),
        )

        private fun itemToJson(c: CareItem) = JSONObject()
            .put("key", c.key).put("label", c.label).put("detail", c.detail)
            .put("delta", c.delta).put("pending", c.pending)

        private fun itemFromJson(o: JSONObject) = CareItem(
            key = o.optString("key"),
            label = o.optString("label"),
            detail = o.optString("detail"),
            delta = o.optInt("delta", 0),
            pending = o.optBoolean("pending", false),
        )

        private fun dayToJson(d: DayRecord) = JSONObject()
            .put("day", d.day).put("socialMin", d.socialMin).put("limitMin", d.limitMin)
            .put("screenMin", d.screenMin).put("phoneFreeMin", d.phoneFreeMin).put("pickups", d.pickups)
            .put("good", d.good).put("delta", d.delta).put("vitalityEnd", d.vitalityEnd)
            .put("items", JSONArray().apply { d.items.forEach { put(itemToJson(it)) } })

        private fun dayFromJson(o: JSONObject) = DayRecord(
            day = o.getString("day"),
            socialMin = o.optInt("socialMin", 0),
            limitMin = o.optInt("limitMin", 60),
            screenMin = o.optInt("screenMin", 0),
            phoneFreeMin = o.optInt("phoneFreeMin", 0),
            pickups = o.optInt("pickups", -1),
            good = o.optBoolean("good", false),
            delta = o.optInt("delta", 0),
            vitalityEnd = o.optInt("vitalityEnd", 70),
            items = o.optJSONArray("items").mapObjects { itemFromJson(it) },
        )

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

        private fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
            if (this == null) return emptyList()
            val out = ArrayList<T>(length())
            for (i in 0 until length()) {
                val o = optJSONObject(i) ?: continue
                try {
                    out += f(o)
                } catch (_: Exception) {
                }
            }
            return out
        }
    }
}
