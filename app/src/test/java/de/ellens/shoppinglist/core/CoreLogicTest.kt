package de.ellens.shoppinglist.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine Logik ohne Android – läuft als normaler JVM-Test. */
class CoreLogicTest {

    private fun cand(vararg names: String) =
        names.mapIndexed { i, n -> SuggestionFilter.Candidate(i.toLong(), n, NameNormalizer.normalize(n)) }

    @Test fun normalizer_trimsCollapsesAndLowercases() {
        assertEquals("Milch fettarm", NameNormalizer.clean("  Milch   fettarm  "))
        assertEquals("milch fettarm", NameNormalizer.normalize("  MILCH   Fettarm "))
        assertFalse(NameNormalizer.isValidName("   "))
        assertFalse(NameNormalizer.isValidName(""))
        assertTrue(NameNormalizer.isValidName(" Brot "))
    }

    // Anforderung 5: alphabetische Vorschläge, gefiltert nach Eingabe
    @Test fun suggestions_areFilteredAndAlphabetical() {
        val catalog = cand("Zucker", "Apfelsaft", "butter", "Apfelmus", "Brot", "Erdbeeren")
        assertEquals(listOf("Apfelmus", "Apfelsaft"), SuggestionFilter.filter("apf", catalog).map { it.name })
        assertEquals(listOf("Brot", "butter", "Erdbeeren"), SuggestionFilter.filter("b", catalog).map { it.name })
        assertTrue(SuggestionFilter.filter("  ", catalog).isEmpty())
    }

    @Test fun suggestions_sortGermanUmlautsWithBaseLetter() {
        val sorted = SuggestionFilter.sortAlphabetically(cand("Zwiebeln", "Öl", "Oliven", "Äpfel", "Bananen")).map { it.name }
        assertEquals(listOf("Äpfel", "Bananen", "Öl", "Oliven", "Zwiebeln"), sorted)
    }

    @Test fun suggestions_caseInsensitiveMatching() {
        assertEquals(listOf("Milch"), SuggestionFilter.filter("MIL", cand("Milch", "Mehl")).map { it.name })
    }

    @Test fun ordering_betweenAndNewPositions() {
        assertEquals(1024L, ItemOrdering.positionForNewItem(null))
        assertEquals(3072L, ItemOrdering.positionForNewItem(2048L))
        assertEquals(1536L, ItemOrdering.between(1024L, 2048L))
        assertEquals(0L, ItemOrdering.between(null, 1024L))
        assertEquals(2048L, ItemOrdering.between(1024L, null))
        assertNull(ItemOrdering.between(5L, 6L))
    }

    @Test fun ordering_orderAfterMoveAndRenumber() {
        assertEquals(listOf(1L, 4L, 2L, 3L), ItemOrdering.orderAfterMove(listOf(1L, 2L, 3L, 4L), 4L, 1L, 2L))
        assertEquals(listOf(2L, 1L, 3L), ItemOrdering.orderAfterMove(listOf(1L, 2L, 3L), 1L, null, 3L))
        assertEquals(mapOf(7L to 1024L, 3L to 2048L), ItemOrdering.renumber(listOf(7L, 3L)))
    }

    @Test fun ordering_mergeLocalOrderNeverDuplicates() {
        assertEquals(listOf(3L, 2L, 1L), ItemOrdering.mergeLocalOrder(listOf(3L, 2L, 1L), listOf(1L, 2L, 3L)) { it })
        // 3 wurde inzwischen erledigt (fehlt), 4 ist neu
        assertEquals(listOf(2L, 1L, 4L), ItemOrdering.mergeLocalOrder(listOf(3L, 2L, 1L), listOf(1L, 2L, 4L)) { it })
        assertEquals(listOf(1L), ItemOrdering.mergeLocalOrder(listOf(1L, 1L), listOf(1L)) { it })
    }
}
