package app.offgrow.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.offgrow.data.Store
import app.offgrow.garden.Band
import app.offgrow.garden.CareItem
import app.offgrow.garden.Flower
import app.offgrow.garden.GardenEngine
import app.offgrow.garden.GardenState
import app.offgrow.garden.Rules
import app.offgrow.garden.Snapshot
import app.offgrow.usage.DayStats
import app.offgrow.usage.UsageReader
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class UiState(
    val ready: Boolean = false,
    val state: GardenState? = null,
    val hasAccess: Boolean = false,
    val today: DayStats? = null,
    val todayItems: List<CareItem> = emptyList(),
    val live: Int = Rules.START_VITALITY,
    val band: Band = Band.HEALTHY,
    val garden: ImageBitmap? = null,
    val refreshing: Boolean = false,
)

data class AppChoice(val pkg: String, val label: String, val counted: Boolean)

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var imageVersion = -1L
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            // Show the saved garden straight away, then bring it up to date.
            val saved = withContext(Dispatchers.IO) { Store(app).load() }
            val access = withContext(Dispatchers.IO) { UsageReader(app).hasAccess() }
            val bmp = loadImage()
            _ui.update {
                it.copy(
                    ready = true,
                    state = saved,
                    hasAccess = access,
                    live = saved.vitality,
                    band = Band.of(saved.vitality),
                    garden = bmp ?: it.garden,
                )
            }
            refresh()
        }
    }

    /** Bumped on every change, so results computed from older data are dropped. */
    private var generation = 0
    private var refreshAgain = false

    fun refresh() {
        if (refreshJob?.isActive == true) {
            refreshAgain = true
            return
        }
        refreshJob = viewModelScope.launch {
            _ui.update { it.copy(refreshing = true) }
            try {
                do {
                    refreshAgain = false
                    val g = generation
                    try {
                        val snap = GardenEngine.refresh(app, render = true, onScored = { if (g == generation) show(it) })
                        if (g == generation) show(snap) else refreshAgain = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("AppViewModel", "refresh failed", e)
                    }
                } while (refreshAgain)
            } finally {
                _ui.update { it.copy(refreshing = false) }
            }
        }
    }

    private suspend fun show(snap: Snapshot) {
        val bmp = if (snap.imageVersion != imageVersion) loadImage() else null
        _ui.update {
            it.copy(
                ready = true,
                state = snap.state,
                hasAccess = snap.hasAccess,
                today = snap.today,
                todayItems = snap.todayItems,
                live = snap.live,
                band = snap.band,
                garden = bmp ?: it.garden,
            )
        }
    }

    private suspend fun loadImage(): ImageBitmap? = withContext(Dispatchers.IO) {
        val f = GardenEngine.imageFile(app)
        if (!f.exists()) return@withContext null
        try {
            imageVersion = f.lastModified()
            BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun change(then: ((GardenState) -> Unit)? = null, f: (GardenState) -> GardenState) {
        viewModelScope.launch {
            val next = try {
                GardenEngine.update(app, f)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AppViewModel", "change failed", e)
                return@launch
            }
            generation++
            _ui.update { it.copy(state = next) }
            then?.invoke(next)
            refresh()
        }
    }

    fun startGarden(name: String, limitMin: Int) = change { s ->
        val today = LocalDate.now()
        s.copy(
            onboarded = true,
            name = name.trim().take(40),
            limitMin = limitMin,
            createdDay = today.toString(),
            lastClosedDay = today.minusDays(1).toString(),
            pendingSeeds = maxOf(1, s.pendingSeeds),
            seedDay = null,
        )
    }

    /** Plant a flower from a pending seed. Calls back with the new flower's id, or null if there was no seed. */
    fun plant(kind: String, note: String, onPlanted: (String?) -> Unit) {
        val id = UUID.randomUUID().toString()
        val today = LocalDate.now()
        val label = Rules.plantable(kind).label
        val flower = Flower(
            id = id,
            kind = kind,
            name = "$label, ${today.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}",
            note = note.trim().take(160),
            plantedDay = today.toString(),
        )
        change(then = { next -> onPlanted(if (next.flowers.any { it.id == id }) id else null) }) { s -> Rules.plant(s, flower) }
    }

    fun renameFlower(id: String, name: String) = change { s ->
        s.copy(flowers = s.flowers.map { if (it.id == id) it.copy(name = name.trim().take(60).ifEmpty { it.name }) else it })
    }

    fun completeFocus() = change { s -> Rules.completeFocus(s, LocalDate.now()) }

    fun setLimit(min: Int) = change { s -> s.copy(limitMin = min) }

    fun setName(name: String) = change { s -> s.copy(name = name.trim().take(40)) }

    fun setFence(fence: String) = change { s -> s.copy(fence = fence) }

    fun onAccessMaybeChanged() {
        viewModelScope.launch {
            val access = withContext(Dispatchers.IO) { UsageReader(app).hasAccess() }
            _ui.update { it.copy(hasAccess = access) }
            refresh()
        }
    }

    suspend fun appChoices(): List<AppChoice> = withContext(Dispatchers.IO) {
        val reader = UsageReader(app)
        reader.launchableApps().map { (pkg, label) -> AppChoice(pkg, label, reader.isSocial(pkg)) }
            .sortedWith(compareByDescending<AppChoice> { it.counted }.thenBy { it.label.lowercase() })
    }

    fun setCounted(pkg: String, counted: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val store = Store(app)
                val reader = UsageReader(app)
                val byDefault = reader.isSocialByDefault(pkg)
                var inc = store.includedApps
                var exc = store.excludedApps
                inc = inc - pkg
                exc = exc - pkg
                if (counted && !byDefault) inc = inc + pkg
                if (!counted && byDefault) exc = exc + pkg
                store.includedApps = inc
                store.excludedApps = exc
            }
            refresh()
        }
    }

    suspend fun preview(vitality: Int, tod: String): ImageBitmap? {
        val s = _ui.value.state ?: return null
        val cfg = GardenEngine.config(s, vitality, LocalTime.now(), overrideTod = tod)
        return GardenEngine.renderPreview(app, cfg, 900)?.asImageBitmap()
    }
}
