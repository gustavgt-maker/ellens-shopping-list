package de.ellens.shoppinglist.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.ellens.shoppinglist.AppContainer
import de.ellens.shoppinglist.data.db.ListWithCounts
import de.ellens.shoppinglist.data.repo.ShoppingRepository
import de.ellens.shoppinglist.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OverviewViewModel(
    private val repo: ShoppingRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** null = wird noch geladen. */
    val lists: StateFlow<List<ListWithCounts>?> = repo.observeOverview()
        .map<List<ListWithCounts>, List<ListWithCounts>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createList(name: String, colorIndex: Int, onCreated: (Long) -> Unit) = viewModelScope.launch {
        repo.createList(name, colorIndex)?.let(onCreated)
    }

    fun updateList(id: Long, name: String, colorIndex: Int) = viewModelScope.launch {
        repo.updateList(id, name, colorIndex)
    }

    suspend fun suggestColor(): Int = repo.suggestColorForNewList()

    fun deleteList(id: Long) = viewModelScope.launch { repo.deleteList(id) }

    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { settings.setKeepScreenOn(enabled) }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { OverviewViewModel(c.shoppingRepository, c.settingsRepository) }
        }
    }
}
