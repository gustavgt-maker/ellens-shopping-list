package de.ellens.shoppinglist.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Zuletzt geöffnet oder verändert; bestimmt die Sortierung der Übersicht. */
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
    @ColumnInfo(name = "scroll_index", defaultValue = "0") val scrollIndex: Int = 0,
    @ColumnInfo(name = "scroll_offset", defaultValue = "0") val scrollOffset: Int = 0,
    /** Dezente Hintergrundfarbe der Liste, Index in [de.ellens.shoppinglist.core.ListColors]. 0 = keine. */
    @ColumnInfo(name = "color_index", defaultValue = "0") val colorIndex: Int = 0,
)

@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["list_id"]),
        // Verhindert auf Datenbankebene doppelte Artikel in derselben Liste.
        Index(value = ["list_id", "normalized_name"], unique = true),
    ],
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "list_id") val listId: Long,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    val quantity: String = "",
    @ColumnInfo(name = "is_done") val isDone: Boolean = false,
    /** Dauerhafte Sortierposition, bleibt auch beim Abhaken erhalten. */
    val position: Long,
    /** Zeitpunkt des Abhakens; bestimmt die Reihenfolge im Erledigt-Bereich. */
    @ColumnInfo(name = "done_at") val doneAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Artikeldatenbank für Vorschläge – unabhängig von den Listen. */
@Entity(
    tableName = "catalog_items",
    indices = [Index(value = ["normalized_name"], unique = true)],
)
data class CatalogItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class ListWithCounts(
    @Embedded val list: ShoppingListEntity,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "open_count") val openCount: Int,
)
