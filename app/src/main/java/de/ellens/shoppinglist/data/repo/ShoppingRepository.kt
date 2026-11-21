package de.ellens.shoppinglist.data.repo

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import de.ellens.shoppinglist.core.ItemOrdering
import de.ellens.shoppinglist.core.NameNormalizer
import de.ellens.shoppinglist.data.db.AppDatabase
import de.ellens.shoppinglist.data.db.CatalogItemEntity
import de.ellens.shoppinglist.data.db.ListWithCounts
import de.ellens.shoppinglist.data.db.ShoppingItemEntity
import de.ellens.shoppinglist.data.db.ShoppingListEntity
import kotlinx.coroutines.flow.Flow

sealed interface AddItemResult {
    data class Added(val itemId: Long) : AddItemResult
    data object Duplicate : AddItemResult
    data object InvalidName : AddItemResult
    data object ListMissing : AddItemResult
}

sealed interface EditItemResult {
    data object Saved : EditItemResult
    data object Duplicate : EditItemResult
    data object InvalidName : EditItemResult
    data object NotFound : EditItemResult
}

/**
 * Zentrale Datenzugriffe für Listen und Artikel. Alle zusammengesetzten Änderungen laufen in
 * Transaktionen, damit die Datenbank auch bei Abbruch konsistent bleibt.
 */
class ShoppingRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val listDao = db.listDao()
    private val itemDao = db.itemDao()
    private val catalogDao = db.catalogDao()

    // ---------------------------------------------------------------- Listen

    fun observeOverview(): Flow<List<ListWithCounts>> = listDao.observeListsWithCounts()
    fun observeListsStable(): Flow<List<ShoppingListEntity>> = listDao.observeListsStable()
    fun observeList(id: Long): Flow<ShoppingListEntity?> = listDao.observeList(id)
    fun observeItems(listId: Long): Flow<List<ShoppingItemEntity>> = itemDao.observeItems(listId)

    suspend fun getList(id: Long): ShoppingListEntity? = listDao.getList(id)

    /** @return ID der neuen Liste oder null bei leerem Namen. */
    suspend fun createList(rawName: String): Long? {
        if (!NameNormalizer.isValidName(rawName)) return null
        val now = clock()
        return listDao.insert(
            ShoppingListEntity(name = NameNormalizer.clean(rawName), createdAt = now, lastUsedAt = now)
        )
    }

    suspend fun renameList(id: Long, rawName: String): Boolean {
        if (!NameNormalizer.isValidName(rawName)) return false
        return listDao.rename(id, NameNormalizer.clean(rawName), clock()) > 0
    }

    /** Entfernt alle Artikel; Liste und Artikeldatenbank bleiben erhalten. */
    suspend fun clearList(id: Long): Int = db.withTransaction {
        val removed = itemDao.deleteAllInList(id)
        listDao.touch(id, clock())
        removed
    }

    /** Löscht Liste samt Artikeln (Fremdschlüssel CASCADE). Artikeldatenbank bleibt unberührt. */
    suspend fun deleteList(id: Long): Boolean = listDao.delete(id) > 0

    suspend fun markListOpened(id: Long) = listDao.touch(id, clock())

    suspend fun saveScrollPosition(id: Long, index: Int, offset: Int) =
        listDao.updateScroll(id, index.coerceAtLeast(0), offset.coerceAtLeast(0))

    // ---------------------------------------------------------------- Artikel

    suspend fun addItem(listId: Long, rawName: String, rawQuantity: String): AddItemResult {
        if (!NameNormalizer.isValidName(rawName)) return AddItemResult.InvalidName
        val name = NameNormalizer.clean(rawName)
        val normalized = NameNormalizer.normalize(rawName)
        val quantity = NameNormalizer.cleanQuantity(rawQuantity)
        return try {
            db.withTransaction {
                if (listDao.getList(listId) == null) return@withTransaction AddItemResult.ListMissing
                if (itemDao.findByNormalizedName(listId, normalized) != null) {
                    return@withTransaction AddItemResult.Duplicate
                }
                val now = clock()
                val id = itemDao.insert(
                    ShoppingItemEntity(
                        listId = listId,
                        name = name,
                        normalizedName = normalized,
                        quantity = quantity,
                        position = ItemOrdering.positionForNewItem(itemDao.maxPosition(listId)),
                        createdAt = now,
                    )
                )
                learnCatalogEntry(name, normalized, now)
                listDao.touch(listId, now)
                AddItemResult.Added(id)
            }
        } catch (e: SQLiteConstraintException) {
            // Absicherung gegen Doppel-Tipp: der eindeutige Index hat gegriffen.
            AddItemResult.Duplicate
        }
    }

    /** Ändert nur Name und Menge – der Erledigt-Status und die Position bleiben unverändert. */
    suspend fun editItem(itemId: Long, rawName: String, rawQuantity: String): EditItemResult {
        if (!NameNormalizer.isValidName(rawName)) return EditItemResult.InvalidName
        val name = NameNormalizer.clean(rawName)
        val normalized = NameNormalizer.normalize(rawName)
        val quantity = NameNormalizer.cleanQuantity(rawQuantity)
        return try {
            db.withTransaction {
                val item = itemDao.getItem(itemId) ?: return@withTransaction EditItemResult.NotFound
                val clash = itemDao.findByNormalizedName(item.listId, normalized)
                if (clash != null && clash.id != itemId) return@withTransaction EditItemResult.Duplicate
                val now = clock()
                itemDao.update(item.copy(name = name, normalizedName = normalized, quantity = quantity))
                learnCatalogEntry(name, normalized, now)
                listDao.touch(item.listId, now)
                EditItemResult.Saved
            }
        } catch (e: SQLiteConstraintException) {
            EditItemResult.Duplicate
        }
    }

    suspend fun setDone(itemId: Long, done: Boolean) = db.withTransaction {
        val item = itemDao.getItem(itemId) ?: return@withTransaction
        if (item.isDone == done) return@withTransaction
        val now = clock()
        itemDao.updateDone(itemId, done, if (done) now else null)
        listDao.touch(item.listId, now)
    }

    suspend fun toggleDone(itemId: Long) = db.withTransaction {
        val item = itemDao.getItem(itemId) ?: return@withTransaction
        val now = clock()
        val done = !item.isDone
        itemDao.updateDone(itemId, done, if (done) now else null)
        listDao.touch(item.listId, now)
    }

    /**
     * Verschiebt einen offenen Artikel an Index [toIndex] innerhalb der offenen Artikel.
     * Erledigte Artikel behalten ihre gespeicherte Position.
     */
    suspend fun moveOpenItem(listId: Long, itemId: Long, toIndex: Int) = db.withTransaction {
        val open = itemDao.getOpenItemsByPosition(listId)
        if (open.none { it.id == itemId }) return@withTransaction
        val others = open.filter { it.id != itemId }
        val index = toIndex.coerceIn(0, others.size)
        val prev = others.getOrNull(index - 1)
        val next = others.getOrNull(index)
        val all = itemDao.getItemsByPosition(listId)
        val takenPositions = all.filter { it.id != itemId }.map { it.position }.toSet()
        val newPosition = ItemOrdering.between(prev?.position, next?.position)
        // Positionen bleiben eindeutig – auch gegenüber erledigten Artikeln, damit deren
        // spätere Rückkehr eindeutig einsortiert wird.
        if (newPosition != null && newPosition !in takenPositions) {
            itemDao.updatePosition(itemId, newPosition)
        } else {
            val order = ItemOrdering.orderAfterMove(all.map { it.id }, itemId, prev?.id, next?.id)
            ItemOrdering.renumber(order).forEach { (id, pos) -> itemDao.updatePosition(id, pos) }
        }
        listDao.touch(listId, clock())
    }

    /** Löscht einen Artikel und liefert ihn vollständig zurück (für Rückgängig). */
    suspend fun deleteItem(itemId: Long): ShoppingItemEntity? = db.withTransaction {
        val item = itemDao.getItem(itemId) ?: return@withTransaction null
        itemDao.delete(itemId)
        listDao.touch(item.listId, clock())
        item
    }

    /**
     * Stellt einen gelöschten Artikel exakt wieder her (gleiche ID, Menge, Status, Position).
     * @return false, wenn inzwischen ein gleichnamiger Artikel existiert oder die Liste fehlt.
     */
    suspend fun restoreItem(item: ShoppingItemEntity): Boolean = try {
        db.withTransaction {
            if (listDao.getList(item.listId) == null) return@withTransaction false
            if (itemDao.findByNormalizedName(item.listId, item.normalizedName) != null) {
                return@withTransaction false
            }
            itemDao.insert(item)
            listDao.touch(item.listId, clock())
            true
        }
    } catch (e: SQLiteConstraintException) {
        false
    }

    private suspend fun learnCatalogEntry(name: String, normalized: String, now: Long) {
        catalogDao.insertIfAbsent(CatalogItemEntity(name = name, normalizedName = normalized, createdAt = now))
    }
}
