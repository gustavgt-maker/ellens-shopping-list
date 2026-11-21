package de.ellens.shoppinglist.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.ellens.shoppinglist.AppContainer
import de.ellens.shoppinglist.core.SuggestionFilter
import de.ellens.shoppinglist.data.db.ShoppingItemEntity
import de.ellens.shoppinglist.data.db.ShoppingListEntity
import de.ellens.shoppinglist.data.repo.AddItemResult
import de.ellens.shoppinglist.data.repo.CatalogRepository
import de.ellens.shoppinglist.data.repo.EditItemResult
import de.ellens.shoppinglist.data.repo.ShoppingRepository
import de.ellens.shoppinglist.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Vollständiger Zustand einer geöffneten Liste – Metadaten und Artikel passen immer zusammen. */
data class ListContentState(
    val list: ShoppingListEntity,
    val open: List<ShoppingItemEntity>,
    val done: List<ShoppingItemEntity>,
)

sealed interface ListScreenState {
    data object Loading : ListScreenState
    data object Missing : ListScreenState
    data class Ready(val content: ListContentState) : ListScreenState
}

sealed interface ListEvent {
    data class Message(val text: String) : ListEvent
    data class ItemDeleted(val item: ShoppingItemEntity) : ListEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModel(
    private val savedState: SavedStateHandle,
    private val repo: ShoppingRepository,
    catalogRepo: CatalogRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Aktuell angezeigte Liste. Wird im SavedState gehalten und überlebt Prozessende. */
    val currentListId: StateFlow<Long> = savedState.getStateFlow(KEY_LIST_ID, -1L)

    val screenState: StateFlow<ListScreenState> = currentListId
        .flatMapLatest { id -> contentFor(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListScreenState.Loading)

    /** Listen in stabiler Reihenfolge für Wischen und untere Auswahlleiste. */
    val allLists: StateFlow<List<ShoppingListEntity>> = repo.observeListsStable()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val catalog: Flow<List<SuggestionFilter.Candidate>> = catalogRepo.observeAll()
        .map { list -> list.map { SuggestionFilter.Candidate(it.id, it.name, it.normalizedName) } }

    private val nameInput = MutableStateFlow("")

    val suggestions: StateFlow<List<SuggestionFilter.Candidate>> =
        combine(nameInput, catalog) { input, cat -> SuggestionFilter.filter(input, cat, limit = 30) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<ListEvent>(Channel.BUFFERED)
    val events: Flow<ListEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            currentListId.collect { id ->
                if (id > 0) {
                    repo.markListOpened(id)
                    settings.setLastOpenedList(id)
                }
            }
        }
    }

    private fun contentFor(id: Long): Flow<ListScreenState> =
        combine(repo.observeList(id), repo.observeItems(id)) { list, items ->
            if (list == null) ListScreenState.Missing
            else ListScreenState.Ready(
                ListContentState(
                    list = list,
                    open = items.filter { !it.isDone }.sortedWith(compareBy({ it.position }, { it.id })),
                    // Zuletzt erledigte Artikel stehen direkt unter den offenen.
                    done = items.filter { it.isDone }
                        .sortedWith(compareByDescending<ShoppingItemEntity> { it.doneAt ?: 0L }.thenBy { it.id }),
                )
            )
        }.distinctUntilChanged()

    fun switchTo(listId: Long) {
        if (listId != currentListId.value) savedState[KEY_LIST_ID] = listId
    }

    fun onNameInputChanged(text: String) { nameInput.value = text }

    /** @return ID des neuen Artikels (Eingabefelder leeren) oder null, wenn nicht hinzugefügt. */
    suspend fun addItem(name: String, quantity: String): Long? {
        val listId = currentListId.value
        return when (val r = repo.addItem(listId, name, quantity)) {
            is AddItemResult.Added -> { nameInput.value = ""; r.itemId }
            AddItemResult.Duplicate -> { _events.send(ListEvent.Message("Artikel bereits auf der Liste")); null }
            AddItemResult.InvalidName -> { _events.send(ListEvent.Message("Bitte einen Artikelnamen eingeben")); null }
            AddItemResult.ListMissing -> { _events.send(ListEvent.Message("Die Liste existiert nicht mehr")); null }
        }
    }

    /** @return null bei Erfolg, sonst Fehlermeldung für den Dialog. */
    suspend fun editItem(itemId: Long, name: String, quantity: String): String? =
        when (repo.editItem(itemId, name, quantity)) {
            EditItemResult.Saved -> null
            EditItemResult.Duplicate -> "Artikel bereits auf der Liste"
            EditItemResult.InvalidName -> "Der Name darf nicht leer sein"
            EditItemResult.NotFound -> "Der Artikel existiert nicht mehr"
        }

    fun toggleDone(itemId: Long) = viewModelScope.launch { repo.toggleDone(itemId) }

    fun moveOpenItem(itemId: Long, toIndex: Int) = viewModelScope.launch {
        repo.moveOpenItem(currentListId.value, itemId, toIndex)
    }

    fun deleteItem(itemId: Long) = viewModelScope.launch {
        repo.deleteItem(itemId)?.let { _events.send(ListEvent.ItemDeleted(it)) }
    }

    fun undoDelete(item: ShoppingItemEntity) = viewModelScope.launch {
        if (!repo.restoreItem(item)) {
            _events.send(ListEvent.Message("Wiederherstellen nicht möglich – Artikel ist bereits auf der Liste"))
        }
    }

    fun renameCurrentList(name: String) = viewModelScope.launch { repo.renameList(currentListId.value, name) }
    fun clearCurrentList() = viewModelScope.launch { repo.clearList(currentListId.value) }

    /** Löscht die aktuelle Liste; [onDeleted] wird danach aufgerufen (Rückkehr zur Übersicht). */
    fun deleteCurrentList(onDeleted: () -> Unit) = viewModelScope.launch {
        repo.deleteList(currentListId.value)
        onDeleted()
    }

    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { settings.setKeepScreenOn(enabled) }

    /** Wird auch beim Verlassen aufgerufen; darf daher nicht abgebrochen werden. */
    fun saveScroll(listId: Long, index: Int, offset: Int) {
        if (listId <= 0) return
        viewModelScope.launch { withContext(NonCancellable) { repo.saveScrollPosition(listId, index, offset) } }
    }

    companion object {
        /** Identisch mit dem Navigationsargument, damit der Startwert automatisch übernommen wird. */
        const val KEY_LIST_ID = "listId"

        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ListViewModel(createSavedStateHandle(), c.shoppingRepository, c.catalogRepository, c.settingsRepository)
            }
        }
    }
}
