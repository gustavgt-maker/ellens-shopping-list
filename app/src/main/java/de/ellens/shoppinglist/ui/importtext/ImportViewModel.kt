package de.ellens.shoppinglist.ui.importtext

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.ellens.shoppinglist.AppContainer
import de.ellens.shoppinglist.core.ItemTextParser
import de.ellens.shoppinglist.core.NameNormalizer
import de.ellens.shoppinglist.data.db.ShoppingListEntity
import de.ellens.shoppinglist.data.repo.CatalogRepository
import de.ellens.shoppinglist.data.repo.ShoppingRepository
import de.ellens.shoppinglist.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Eine erkannte Zeile in der Vorschau. */
data class ImportRow(
    val key: String,
    val name: String,
    val quantity: String,
    val alreadyOnList: Boolean,
    val selected: Boolean,
)

data class ImportUiState(
    val lists: List<ShoppingListEntity>,
    val targetListId: Long,
    val rows: List<ImportRow>,
) {
    val selectedCount: Int get() = rows.count { it.selected && !it.alreadyOnList }
}

/** Ergebnis für die Rückmeldung nach dem Übernehmen. */
data class ImportDone(val listId: Long, val added: Int, val skipped: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModel(
    private val savedState: SavedStateHandle,
    private val repo: ShoppingRepository,
    catalogRepo: CatalogRepository,
    private val settings: SettingsRepository,
    shareInbox: MutableStateFlow<String?>,
) : ViewModel() {

    companion object {
        const val KEY_LIST_ID = "listId"
        private const val KEY_TEXT = "text"
        private const val KEY_TARGET = "target"
        private const val KEY_DESELECTED = "deselected"

        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ImportViewModel(
                    createSavedStateHandle(), c.shoppingRepository, c.catalogRepository,
                    c.settingsRepository, c.shareInbox,
                )
            }
        }
    }

    init {
        // Geteilten Text übernehmen – auch, wenn der Bildschirm schon offen ist.
        viewModelScope.launch {
            shareInbox.collect { shared ->
                if (shared != null) {
                    shareInbox.value = null
                    val current = savedState.get<String>(KEY_TEXT).orEmpty()
                    savedState[KEY_TEXT] = (if (current.isBlank()) shared else current + "\n" + shared).take(20_000)
                }
            }
        }
        if (!savedState.contains(KEY_TARGET)) {
            val fromRoute = savedState.get<Long>(KEY_LIST_ID) ?: -1L
            savedState[KEY_TARGET] = fromRoute
        }
    }

    val text: StateFlow<String> = savedState.getStateFlow(KEY_TEXT, "")
    private val targetRaw: StateFlow<Long> = savedState.getStateFlow(KEY_TARGET, -1L)
    private val deselected: StateFlow<ArrayList<String>> = savedState.getStateFlow(KEY_DESELECTED, ArrayList())

    private val lists = repo.observeListsStable()

    /** Ziel-Liste: gewählt, sonst zuletzt geöffnete, sonst die erste. */
    private val target = combine(targetRaw, lists, settings.settings) { raw, all, s ->
        when {
            all.any { it.id == raw } -> raw
            s.lastOpenedListId != null && all.any { it.id == s.lastOpenedListId } -> s.lastOpenedListId!!
            else -> all.firstOrNull()?.id ?: -1L
        }
    }

    private val existingKeys = target.flatMapLatest { id ->
        if (id <= 0) flowOf(emptySet()) else repo.observeItems(id).map { items -> items.map { it.normalizedName }.toSet() }
    }

    /** Schreibweise aus der Artikeldatenbank bevorzugen („milch“ -> bekanntes „Milch“). */
    private val catalogNames = catalogRepo.observeAll().map { list -> list.associate { it.normalizedName to it.name } }

    val state: StateFlow<ImportUiState?> = combine(
        combine(text, deselected, catalogNames) { t, d, c -> Triple(t, d, c) },
        lists, target, existingKeys,
    ) { (t, d, catalog), all, targetId, existing ->
        val rows = ItemTextParser.parse(t).map { p ->
            val key = NameNormalizer.normalize(p.name)
            val already = key in existing
            ImportRow(
                key = key,
                name = catalog[key] ?: p.name,
                quantity = p.quantity,
                alreadyOnList = already,
                selected = !already && key !in d,
            )
        }
        ImportUiState(all, targetId, rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setText(value: String) { savedState[KEY_TEXT] = value.take(20_000) }

    fun setTarget(id: Long) { savedState[KEY_TARGET] = id }

    fun toggle(key: String) {
        val d = ArrayList(deselected.value)
        if (!d.remove(key)) d.add(key)
        savedState[KEY_DESELECTED] = d
    }

    fun importItems(onDone: (ImportDone) -> Unit) {
        val s = state.value ?: return
        if (s.targetListId <= 0) return
        val chosen = s.rows.filter { it.selected && !it.alreadyOnList }.map { it.name to it.quantity }
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            val r = repo.addItems(s.targetListId, chosen)
            savedState[KEY_TEXT] = ""
            onDone(ImportDone(s.targetListId, r.added, r.duplicates + r.invalid))
        }
    }
}
