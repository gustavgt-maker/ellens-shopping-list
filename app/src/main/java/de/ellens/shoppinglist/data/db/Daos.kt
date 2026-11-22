package de.ellens.shoppinglist.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {

    @Query(
        """
        SELECT l.*,
               COUNT(i.id) AS item_count,
               COALESCE(SUM(CASE WHEN i.is_done = 0 THEN 1 ELSE 0 END), 0) AS open_count
        FROM shopping_lists l
        LEFT JOIN shopping_items i ON i.list_id = l.id
        GROUP BY l.id
        ORDER BY l.last_used_at DESC, l.id DESC
        """
    )
    fun observeListsWithCounts(): Flow<List<ListWithCounts>>

    /** Stabile Reihenfolge (nach Anlage) für Wischnavigation und untere Listenauswahl. */
    @Query("SELECT * FROM shopping_lists ORDER BY created_at ASC, id ASC")
    fun observeListsStable(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    fun observeList(id: Long): Flow<ShoppingListEntity?>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun getList(id: Long): ShoppingListEntity?

    @Query("SELECT * FROM shopping_lists ORDER BY id ASC")
    suspend fun getAllLists(): List<ShoppingListEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(list: ShoppingListEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(lists: List<ShoppingListEntity>)

    @Query("UPDATE shopping_lists SET name = :name, last_used_at = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long): Int

    @Query("UPDATE shopping_lists SET color_index = :colorIndex WHERE id = :id")
    suspend fun setColor(id: Long, colorIndex: Int): Int

    @Query("SELECT color_index FROM shopping_lists")
    suspend fun usedColors(): List<Int>

    @Query("SELECT * FROM shopping_lists WHERE id IN (:ids)")
    fun observeListsByIds(ids: List<Long>): Flow<List<ShoppingListEntity>>

    @Query("UPDATE shopping_lists SET last_used_at = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE shopping_lists SET scroll_index = :index, scroll_offset = :offset WHERE id = :id")
    suspend fun updateScroll(id: Long, index: Int, offset: Int)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM shopping_lists")
    suspend fun deleteAll()
}

@Dao
interface ShoppingItemDao {

    @Query("SELECT * FROM shopping_items WHERE list_id = :listId")
    fun observeItems(listId: Long): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE list_id IN (:listIds)")
    fun observeItemsOfLists(listIds: List<Long>): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE list_id = :listId ORDER BY position ASC, id ASC")
    suspend fun getItemsByPosition(listId: Long): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE list_id = :listId AND is_done = 0 ORDER BY position ASC, id ASC")
    suspend fun getOpenItemsByPosition(listId: Long): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items ORDER BY list_id ASC, position ASC, id ASC")
    suspend fun getAllItems(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun getItem(id: Long): ShoppingItemEntity?

    @Query("SELECT * FROM shopping_items WHERE list_id = :listId AND normalized_name = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(listId: Long, normalizedName: String): ShoppingItemEntity?

    @Query("SELECT MAX(position) FROM shopping_items WHERE list_id = :listId")
    suspend fun maxPosition(listId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ShoppingItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Update
    suspend fun update(item: ShoppingItemEntity): Int

    @Query("UPDATE shopping_items SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Long)

    @Query("UPDATE shopping_items SET is_done = :isDone, done_at = :doneAt WHERE id = :id")
    suspend fun updateDone(id: Long, isDone: Boolean, doneAt: Long?): Int

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM shopping_items WHERE list_id = :listId")
    suspend fun deleteAllInList(listId: Long): Int

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAll()
}

@Dao
interface CatalogDao {

    @Query("SELECT * FROM catalog_items")
    fun observeAll(): Flow<List<CatalogItemEntity>>

    @Query("SELECT * FROM catalog_items ORDER BY id ASC")
    suspend fun getAll(): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): CatalogItemEntity?

    /** Fügt hinzu, falls noch nicht vorhanden (eindeutiger Index). Ergebnis -1 = existierte schon. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(item: CatalogItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(items: List<CatalogItemEntity>)

    @Query("UPDATE catalog_items SET name = :name, normalized_name = :normalizedName WHERE id = :id")
    suspend fun rename(id: Long, name: String, normalizedName: String): Int

    @Query("DELETE FROM catalog_items WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM catalog_items")
    suspend fun deleteAll()
}
