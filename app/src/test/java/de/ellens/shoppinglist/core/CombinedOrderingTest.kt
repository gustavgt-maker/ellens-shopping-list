package de.ellens.shoppinglist.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CombinedOrderingTest {

    private fun e(id: Long, list: Int, idx: Int, size: Int, key: String) =
        CombinedOrdering.Entry(id, list, idx, size, key)

    // Privat: Milch, Brot, Käse   Arbeit: Kaffee, Milch
    private val entries = listOf(
        e(1, 0, 0, 3, "milch"), e(2, 0, 1, 3, "brot"), e(3, 0, 2, 3, "käse"),
        e(4, 1, 0, 2, "kaffee"), e(5, 1, 1, 2, "milch"),
    )

    @Test fun sameItemsFormOneBlockInListOrder() {
        val blocks = CombinedOrdering.defaultBlocks(entries)
        val milch = blocks.first { it.key == "milch" }
        assertEquals("Privat-Milch vor Arbeit-Milch", listOf(1L, 5L), milch.entries.map { it.itemId })
        assertEquals(4, blocks.size)
    }

    @Test fun listsAreInterleavedProportionally() {
        val keys = CombinedOrdering.defaultBlocks(entries).map { it.key }
        // Anteile: milch 0.0 (Privat), kaffee 0.0 (Arbeit), brot 0.33, käse 0.67
        assertEquals(listOf("milch", "kaffee", "brot", "käse"), keys)
    }

    @Test fun sessionOrderWinsAndNewBlocksAppend() {
        val ordered = CombinedOrdering.orderedBlocks(entries, listOf("käse", "milch", "gibtsnicht"))
        assertEquals(listOf("käse", "milch", "kaffee", "brot"), ordered.map { it.key })
    }

    @Test fun moveBlock() {
        assertEquals(listOf("b", "c", "a"), CombinedOrdering.move(listOf("a", "b", "c"), "a", 2))
        assertEquals(listOf("c", "a", "b"), CombinedOrdering.move(listOf("a", "b", "c"), "c", 0))
        assertEquals(listOf("a", "b"), CombinedOrdering.move(listOf("a", "b"), "x", 0))
    }

    @Test fun listColorSuggestionPicksLeastUsed() {
        assertEquals(1, ListColors.suggestFor(emptyList()))
        assertEquals(2, ListColors.suggestFor(listOf(1)))
        assertEquals(3, ListColors.suggestFor(listOf(1, 2, 0)))
        assertEquals(1, ListColors.suggestFor(listOf(1, 2, 3, 4, 5, 6)))
    }
}
