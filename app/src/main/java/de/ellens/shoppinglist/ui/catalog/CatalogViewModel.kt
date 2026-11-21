package de.ellens.shoppinglist.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.ellens.shoppinglist.AppContainer
import de.ellens.shoppinglist.core.NameNormalizer
import de.ellens.shoppinglist.core.SuggestionFilter
import de.ellens.shoppinglist.data.repo.CatalogRepository
import de.ellens.shoppinglist.data.repo.CatalogResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CatalogViewModel(private val repo: CatalogRepository) : ViewModel() {

    val query = MutableStateFlow("")

    /** null = lädt. Alphabetisch, gefiltert nach Suchtext. */
    val items: StateFlow<List<SuggestionFilter.Candidate>?> =
        combine(repo.observeAll(), query) { all, q ->
            val candidates = all.map { SuggestionFilter.Candidate(it.id, it.name, it.normalizedName) }
            val nq = NameNormalizer.normalize(q)
            val filtered = if (nq.isEmpty()) candidates else candidates.filter { it.normalizedName.contains(nq) }
            SuggestionFilter.sortAlphabetically(filtered)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun add(name: String): String? = message(repo.add(name))
    suspend fun rename(id: Long, name: String): String? = message(repo.rename(id, name))
    suspend fun delete(id: Long) { repo.delete(id) }

    private fun message(r: CatalogResult): String? = when (r) {
        CatalogResult.OK -> null
        CatalogResult.DUPLICATE -> "Dieser Artikel ist bereits gespeichert"
        CatalogResult.INVALID -> "Der Name darf nicht leer sein"
        CatalogResult.NOT_FOUND -> "Der Artikel existiert nicht mehr"
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CatalogViewModel(c.catalogRepository) }
        }
    }
}
