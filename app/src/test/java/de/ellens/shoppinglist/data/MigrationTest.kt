package de.ellens.shoppinglist.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import de.ellens.shoppinglist.TestSupport
import de.ellens.shoppinglist.data.db.AppDatabase
import de.ellens.shoppinglist.data.db.Migrations
import de.ellens.shoppinglist.data.repo.AddItemResult
import de.ellens.shoppinglist.data.repo.ShoppingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Update von App-Version 1.0 (Datenbank v1) auf 1.1 (v2, Listenfarbe):
 * vorhandene Listen, Artikel und Vorschläge müssen vollständig erhalten bleiben.
 *
 * Die v1-Datenbank wird mit genau den Tabellen angelegt, die Room 2.6 für das Schema v1 erzeugt.
 * Room prüft beim Öffnen nach der Migration das komplette Schema gegen die aktuellen Entities;
 * jede Abweichung würde hier eine Exception auslösen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `shopping_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `last_used_at` INTEGER NOT NULL, `scroll_index` INTEGER NOT NULL DEFAULT 0, `scroll_offset` INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS `shopping_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `list_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `normalized_name` TEXT NOT NULL, `quantity` TEXT NOT NULL, `is_done` INTEGER NOT NULL, `position` INTEGER NOT NULL, `done_at` INTEGER, `created_at` INTEGER NOT NULL, FOREIGN KEY(`list_id`) REFERENCES `shopping_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_shopping_items_list_id` ON `shopping_items` (`list_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_shopping_items_list_id_normalized_name` ON `shopping_items` (`list_id`, `normalized_name`)",
        "CREATE TABLE IF NOT EXISTS `catalog_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `normalized_name` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_catalog_items_normalized_name` ON `catalog_items` (`normalized_name`)",
    )

    private fun createV1Database(name: String) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(TestSupport.context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    v1Schema.forEach(db::execSQL)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO shopping_lists (id, name, created_at, last_used_at, scroll_index, scroll_offset) VALUES (1, 'Privat', 100, 300, 4, 12)")
        db.execSQL("INSERT INTO shopping_lists (id, name, created_at, last_used_at) VALUES (2, 'Arbeit', 200, 200)")
        db.execSQL("INSERT INTO shopping_lists (id, name, created_at, last_used_at) VALUES (5, 'Drogerie', 250, 250)")
        db.execSQL("INSERT INTO shopping_items (id, list_id, name, normalized_name, quantity, is_done, position, done_at, created_at) VALUES (10, 1, 'Milch', 'milch', '1', 0, 1024, NULL, 110)")
        db.execSQL("INSERT INTO shopping_items (id, list_id, name, normalized_name, quantity, is_done, position, done_at, created_at) VALUES (11, 1, 'Brot', 'brot', '', 1, 2048, 400, 120)")
        db.execSQL("INSERT INTO shopping_items (id, list_id, name, normalized_name, quantity, is_done, position, done_at, created_at) VALUES (12, 2, 'Milch', 'milch', '3', 0, 1024, NULL, 210)")
        db.execSQL("INSERT INTO catalog_items (id, name, normalized_name, created_at) VALUES (1, 'Milch', 'milch', 110)")
        db.execSQL("INSERT INTO catalog_items (id, name, normalized_name, created_at) VALUES (2, 'Brot', 'brot', 120)")
        helper.close()
    }

    @Test fun migrate1To2_keepsAllDataAndAssignsColors() = runBlocking {
        val name = "migration-test.db"
        TestSupport.context.deleteDatabase(name)
        createV1Database(name)

        val db = Room.databaseBuilder(TestSupport.context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        val repo = ShoppingRepository(db)

        val lists = db.listDao().getAllLists()
        assertEquals(listOf("Privat", "Arbeit", "Drogerie"), lists.map { it.name })
        assertEquals("Listen bekommen nacheinander unterschiedliche Farben", listOf(1, 2, 3), lists.map { it.colorIndex })
        assertEquals(4, lists[0].scrollIndex)
        assertEquals(12, lists[0].scrollOffset)

        val items = db.itemDao().getAllItems()
        assertEquals(3, items.size)
        val brot = items.first { it.id == 11L }
        assertTrue(brot.isDone)
        assertEquals(400L, brot.doneAt)
        assertEquals("3", items.first { it.id == 12L }.quantity)
        assertEquals(2, db.catalogDao().getAll().size)

        // Regeln greifen weiterhin (eindeutiger Index übernommen)
        assertEquals(AddItemResult.Duplicate, repo.addItem(1, "MILCH", ""))
        assertEquals(2, repo.observeItems(1).first().size)
        db.close()
        TestSupport.context.deleteDatabase(name)
        Unit
    }
}
