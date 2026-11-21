package de.ellens.shoppinglist.core

import java.text.Collator
import java.util.Locale

/**
 * Filtert die Artikeldatenbank anhand der bisherigen Eingabe und sortiert alphabetisch
 * (deutsche Sortierung: Groß-/Kleinschreibung und Umlaute werden sinnvoll eingeordnet).
 *
 * Treffer: Der normalisierte Name enthält die normalisierte Eingabe. Leere Eingabe ergibt
 * keine Vorschläge, damit die Liste beim Einkaufen nicht verdeckt wird.
 */
object SuggestionFilter {

    data class Candidate(val id: Long, val name: String, val normalizedName: String)

    private val collator: Collator = Collator.getInstance(Locale.GERMAN).apply {
        strength = Collator.SECONDARY
    }

    fun sortAlphabetically(names: List<Candidate>): List<Candidate> =
        names.sortedWith { a, b ->
            val c = collator.compare(a.name, b.name)
            if (c != 0) c else a.name.compareTo(b.name)
        }

    fun filter(input: String, catalog: List<Candidate>, limit: Int = 50): List<Candidate> {
        val query = NameNormalizer.normalize(input)
        if (query.isEmpty()) return emptyList()
        val matches = catalog.filter { it.normalizedName.contains(query) }
        // Exakter Treffer bringt nichts Neues, wenn er bereits genau so eingegeben wurde.
        return sortAlphabetically(matches)
            .filterNot { it.normalizedName == query && it.name == NameNormalizer.clean(input) }
            .take(limit)
    }
}
