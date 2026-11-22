package de.ellens.shoppinglist.ui.combined

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.ellens.shoppinglist.AppContainer
import de.ellens.shoppinglist.core.CombinedOrdering
import de.ellens.shoppinglist.core.SuggestionFilter
import de.ellens.shoppinglist.data.db.ShoppingItemEntity
import de.ellens.shoppinglist.data.db.ShoppingListEntity
import de.ellens.shoppinglist.data.repo.AddItemResult
import de.ellens.shoppinglist.data.repo.CatalogRepository
import de.ellens.shoppinglist.data.repo.EditItemResult
import de.ellens.shoppinglist.data.repo.ShoppingRepository
import de.ellens.shoppinglist.data.settings.SettingsRepository
import de.ellens.shoppinglist.ui.list.ListEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ein Block gleicher Artikel (gleicher Name) aus verschiedenen Listen. */
data class ItemBlock(val key: String, val items: List<ShoppingItemEntity>)

data class CombinedState(
    /** Ausgewählte, noch vorhandene Listen in Auswahl-Reihenfolge. */
    val lists: List<ShoppingListEntity>,
    val blocks: List<ItemBlock>,
    val done: List<ShoppingItemEntity>,
) {
    val listsById: Map<Long, ShoppingListEntity> = lists.associateBy { it.id }
}

sealed interface CombinedScreenState {
    data object Loading : CombinedScreenState
    /** Weniger als eine Liste übrig (z. B. nach Import oder Löschen) -> zurück zur Übersicht. */
    data object Missing : CombinedScreenState
    data class Ready(val state: CombinedState) : CombinedScreenState
}

/**
 * Gemeinsame Einkaufsansicht mehrerer Listen. Die Auswahl wird nicht dauerhaft gespeichert;
 * sie steht im Navigationsargument und übersteht damit App-Wechsel und Prozessende.
 */
class CombinedViewModel(
    private val savedState: SavedStateHandle,
    private val repo: ShoppingRepository,
    catalogRepo: CatalogRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    companion object {
        const val KEY_LIST_IDS = "listIds"
        private const val KEY_BLOCK_ORDER = "blockOrder"
        private const val KEY_TARGET = "targetListId"

        fun parseIds(raw: String?): List<Long> =
            raw.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()

        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CombinedViewModel(createSavedStateHandle(), c.shoppingRepository, c.catalogRepository, c.settingsRepository)
            }
        }
    }

    val selectedIds: List<Long> = parseIds(savedState.get<String>(KEY_LIST_IDS))

    /** Block-Reihenfolge dieser Sitzung (per Drag & Drop festgelegt). */
    private val blockOrder: StateFlow<List<String>> =
        savedState.getStateFlow(KEY_BLOCK_ORDER, ArrayList<String>())

    /** Liste, in die neue Artikel kommen. */
    val targetListId: StateFlow<Long> =
        savedState.getStateFlow(KEY_TARGET, selectedIds.firstOrNull() ?: -1L)

    val screenState: StateFlow<CombinedScreenState> = combine(
        repo.observeListsByIds(selectedIds),
        repo.observeItemsOfLists(selectedIds),
        blockOrder,
    ) { lists, items, order ->
        val ordered = selectedIds.mapNotNull { id -> lists.firstOrNull { it.id == id } }
        if (ordered.isEmpty()) CombinedScreenState.Missing
        else CombinedScreenState.Ready(buildState(ordered, items, order))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CombinedScreenState.Loading)

    private val catalog: Flow<List<SuggestionFilter.Candidate>> = catalogRepo.observeAll()
        .map { list -> list.map { SuggestionFilter.Candidate(it.id, it.name, it.normalizedName) } }

    private val nameInput = MutableStateFlow("")

    val suggestions: StateFlow<List<SuggestionFilter.Candidate>> =
        combine(nameInput, catalog) { input, cat -> SuggestionFilter.filter(input, cat, limit = 30) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<ListEvent>(Channel.BUFFERED)
    val events: Flow<ListEvent> = _events.receiveAsFlow()

    private fun buildState(
        lists: List<ShoppingListEntity>,
        items: List<ShoppingItemEntity>,
        order: List<String>,
    ): CombinedState {
        val listIndex = lists.withIndex().associate { (i, l) -> l.id to i }
        val byId = items.associateBy { it.id }
        val entries = ArrayList<CombinedOrdering.Entry>()
        for (l in lists) {
            val open = items.filter { it.listId == l.id && !it.isDone }
                .sortedWith(compareBy({ it.position }, { it.id }))
            open.forEachIndexed { index, item ->
                entries += CombinedOrdering.Entry(
                    itemId = item.id,
                    listIndex = listIndex.getValue(l.id),
                    indexInList = index,
                    listSize = open.size,
                    key = item.normalizedName,
                )
            }
        }
        val blocks = CombinedOrdering.orderedBlocks(entries, order).map { b ->
            ItemBlock(b.key, b.entries.mapNotNull { byId[it.itemId] })
        }
        val done = items.filter { it.isDone && it.listId in listIndex }
            .sortedWith(compareByDescending<ShoppingItemEntity> { it.doneAt ?: 0L }.thenBy { it.id })
        return CombinedState(lists, blocks, done)
    }

    fun setTarget(listId: Long) { savedState[KEY_TARGET] = listId }

    fun onNameInputChanged(text: String) { nameInput.value = text }

    /** Übernimmt die neue Block-Reihenfolge nach Drag & Drop. */
    fun setBlockOrder(keys: List<String>) { savedState[KEY_BLOCK_ORDER] = ArrayList(keys) }

    suspend fun addItem(name: String, quantity: String): Long? {
        val target = targetListId.value.takeIf { it in selectedIds } ?: selectedIds.firstOrNull() ?: return null
        return when (val r = repo.addItem(target, name, quantity)) {
            is AddItemResult.Added -> { nameInput.value = ""; r.itemId }
            AddItemResult.Duplicate -> {
                val listName = (screenState.value as? CombinedScreenState.Ready)?.state?.listsById?.get(target)?.name
                _events.send(ListEvent.Message("Artikel bereits auf der Liste" + (listName?.let { " „$it“" } ?: "")))
                null
            }
            AddItemResult.InvalidName -> { _events.send(ListEvent.Message("Bitte einen Artikelnamen eingeben")); null }
            AddItemResult.ListMissing -> { _events.send(ListEvent.Message("Die Liste existiert nicht mehr")); null }
        }
    }

    suspend fun editItem(itemId: Long, name: String, quantity: String): String? =
        when (repo.editItem(itemId, name, quantity)) {
            EditItemResult.Saved -> null
            EditItemResult.Duplicate -> "Artikel bereits auf dieser Liste"
            EditItemResult.InvalidName -> "Der Name darf nicht leer sein"
            EditItemResult.NotFound -> "Der Artikel existiert nicht mehr"
        }

    fun toggleDone(itemId: Long) = viewModelScope.launch { repo.toggleDone(itemId) }

    fun deleteItem(itemId: Long) = viewModelScope.launch {
        repo.deleteItem(itemId)?.let { _events.send(ListEvent.ItemDeleted(it)) }
    }

    fun undoDelete(item: ShoppingItemEntity) = viewModelScope.launch {
        if (!repo.restoreItem(item)) {
            _events.send(ListEvent.Message("Wiederherstellen nicht möglich – Artikel ist bereits auf der Liste"))
        }
    }

    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { settings.setKeepScreenOn(enabled) }
}
