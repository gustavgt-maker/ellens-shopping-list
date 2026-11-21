package de.ellens.shoppinglist.core

/**
 * Sortierlogik für Artikel einer Liste.
 *
 * Jeder Artikel besitzt eine dauerhafte Position (Long) in einer gemeinsamen Ordnung aller
 * Artikel der Liste – offen und erledigt. Offene Artikel werden nach dieser Position angezeigt.
 * Beim Abhaken bleibt die Position unverändert erhalten; beim erneuten Aktivieren kehrt der
 * Artikel dadurch automatisch an seinen Platz relativ zu den übrigen Artikeln zurück.
 *
 * Positionen werden mit Abstand [GAP] vergeben, damit beim Verschieben meist nur ein einziger
 * Datensatz geändert werden muss. Ist zwischen zwei Nachbarn kein Platz mehr, wird die gesamte
 * Liste unter Beibehaltung der Reihenfolge neu durchnummeriert.
 */
object ItemOrdering {
    const val GAP = 1024L

    /** Position für einen neuen Artikel: hinter allen vorhandenen Artikeln. */
    fun positionForNewItem(maxExistingPosition: Long?): Long =
        if (maxExistingPosition == null) GAP else maxExistingPosition + GAP

    /**
     * Position zwischen [prev] und [next] (jeweils null, wenn Anfang/Ende).
     * Liefert null, wenn kein ganzzahliger Zwischenwert frei ist -> Neu-Nummerierung nötig.
     */
    fun between(prev: Long?, next: Long?): Long? = when {
        prev == null && next == null -> GAP
        prev == null -> next!! - GAP
        next == null -> prev + GAP
        next - prev >= 2 -> prev + (next - prev) / 2
        else -> null
    }

    /**
     * Neu-Nummerierung: [orderedIds] ist die vollständige gewünschte Reihenfolge aller Artikel.
     * Ergebnis: id -> neue Position (GAP, 2*GAP, ...).
     */
    fun renumber(orderedIds: List<Long>): Map<Long, Long> =
        orderedIds.mapIndexed { index, id -> id to (index + 1) * GAP }.toMap()

    /**
     * Berechnet die vollständige Zielreihenfolge, wenn [movingId] im globalen Verlauf direkt
     * hinter [prevId] bzw. (falls [prevId] null) direkt vor [nextId] einsortiert werden soll.
     * [allIdsByPosition] enthält alle Artikel der Liste nach aktueller Position, inklusive
     * des zu verschiebenden.
     */
    fun orderAfterMove(
        allIdsByPosition: List<Long>,
        movingId: Long,
        prevId: Long?,
        nextId: Long?,
    ): List<Long> {
        val rest = allIdsByPosition.filter { it != movingId }.toMutableList()
        val insertAt = when {
            prevId != null -> rest.indexOf(prevId).let { if (it < 0) rest.size else it + 1 }
            nextId != null -> rest.indexOf(nextId).let { if (it < 0) 0 else it }
            else -> rest.size
        }
        rest.add(insertAt, movingId)
        return rest
    }

    /**
     * Führt eine lokal (z. B. während Drag & Drop) gehaltene Reihenfolge mit dem aktuellen
     * Datenstand zusammen: Reihenfolge aus [local], Inhalte aus [fresh]. Nicht mehr vorhandene
     * Elemente entfallen, neue werden hinten angehängt. Ergebnis enthält jede ID genau einmal.
     */
    fun <T> mergeLocalOrder(local: List<T>, fresh: List<T>, id: (T) -> Long): List<T> {
        val freshById = LinkedHashMap<Long, T>()
        fresh.forEach { freshById[id(it)] = it }
        val result = ArrayList<T>(fresh.size)
        val seen = HashSet<Long>()
        for (l in local) {
            val key = id(l)
            val f = freshById[key] ?: continue
            if (seen.add(key)) result += f
        }
        for ((key, f) in freshById) if (seen.add(key)) result += f
        return result
    }
}
