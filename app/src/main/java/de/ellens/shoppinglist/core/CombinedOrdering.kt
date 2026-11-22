package de.ellens.shoppinglist.core

/**
 * Logik der gemeinsamen Einkaufsansicht mehrerer Listen.
 *
 * - Offene Artikel aller Listen werden gemischt angezeigt.
 * - Gleiche Artikel (gleicher normalisierter Name) aus verschiedenen Listen bilden einen Block
 *   und stehen direkt untereinander – innerhalb des Blocks in der Reihenfolge der ausgewählten Listen.
 * - Startreihenfolge der Blöcke: Die Listen werden anteilig verzahnt. Ein Artikel, der in seiner
 *   Liste bei 30 % steht, landet auch in der Gesamtansicht ungefähr bei 30 %. Ein Block steht
 *   dort, wo sein am weitesten oben stehender Artikel hingehört.
 * - Per Drag & Drop festgelegte Block-Reihenfolge gilt für die laufende Sitzung; neu
 *   hinzukommende Blöcke werden hinten angehängt.
 */
object CombinedOrdering {

    /**
     * Ein offener Artikel in der Gesamtansicht.
     * @param listIndex Position der Liste in der Auswahl (0 = erste)
     * @param indexInList Position des Artikels unter den offenen Artikeln seiner Liste
     * @param listSize Anzahl offener Artikel in seiner Liste
     */
    data class Entry(
        val itemId: Long,
        val listIndex: Int,
        val indexInList: Int,
        val listSize: Int,
        val key: String,
    ) {
        val fraction: Double get() = if (listSize <= 0) 0.0 else indexInList.toDouble() / listSize
    }

    data class Block(val key: String, val entries: List<Entry>)

    /** Blöcke in Startreihenfolge (ohne Sitzungs-Sortierung). */
    fun defaultBlocks(entries: List<Entry>): List<Block> =
        entries.groupBy { it.key }
            .map { (key, group) -> Block(key, group.sortedWith(compareBy({ it.listIndex }, { it.indexInList }))) }
            .sortedWith(
                compareBy<Block>(
                    { b -> b.entries.minOf { it.fraction } },
                    { b -> b.entries.minOf { it.listIndex } },
                    { b -> b.key },
                )
            )

    /**
     * Blöcke unter Berücksichtigung der Sitzungs-Reihenfolge [sessionOrder] (Block-Schlüssel).
     * Nicht mehr vorhandene Schlüssel entfallen, neue Blöcke folgen in Startreihenfolge am Ende.
     */
    fun orderedBlocks(entries: List<Entry>, sessionOrder: List<String>): List<Block> {
        val defaults = defaultBlocks(entries)
        if (sessionOrder.isEmpty()) return defaults
        val byKey = defaults.associateBy { it.key }
        val seen = HashSet<String>()
        val result = ArrayList<Block>(defaults.size)
        for (k in sessionOrder) {
            val b = byKey[k] ?: continue
            if (seen.add(k)) result += b
        }
        for (b in defaults) if (seen.add(b.key)) result += b
        return result
    }

    /** Verschiebt Block [key] auf Index [toIndex] innerhalb von [order]. */
    fun move(order: List<String>, key: String, toIndex: Int): List<String> {
        val from = order.indexOf(key)
        if (from < 0) return order
        val list = order.toMutableList()
        list.removeAt(from)
        list.add(toIndex.coerceIn(0, list.size), key)
        return list
    }
}
