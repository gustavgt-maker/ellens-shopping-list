package de.ellens.shoppinglist.data

import androidx.room.Room
import de.ellens.shoppinglist.TestSupport
import de.ellens.shoppinglist.data.db.AppDatabase
import de.ellens.shoppinglist.data.db.Migrations
import de.ellens.shoppinglist.data.repo.ShoppingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 10. Persistenz: Daten überstehen Schließen und erneutes Öffnen der Datenbankdatei. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceTest {

    private fun openFileDb(name: String) =
        Room.databaseBuilder(TestSupport.context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()

    @Test fun dataSurvivesRestart() = runBlocking {
        val name = "persist-test.db"
        TestSupport.context.deleteDatabase(name)

        val db1 = openFileDb(name)
        val repo1 = ShoppingRepository(db1)
        val list = repo1.createList("Wocheneinkauf")!!
        repo1.addItem(list, "Milch", "2")
        repo1.addItem(list, "Brot", "")
        val brot = repo1.observeItems(list).first().first { it.name == "Brot" }
        repo1.toggleDone(brot.id)
        repo1.saveScrollPosition(list, 3, 10)
        db1.close()

        val db2 = openFileDb(name)
        val repo2 = ShoppingRepository(db2)
        val items = repo2.observeItems(list).first()
        assertEquals(setOf("Milch", "Brot"), items.map { it.name }.toSet())
        assertEquals("2", items.first { it.name == "Milch" }.quantity)
        assertTrue(items.first { it.name == "Brot" }.isDone)
        assertEquals(3, repo2.getList(list)!!.scrollIndex)
        assertEquals(2, db2.catalogDao().getAll().size)
        db2.close()
        TestSupport.context.deleteDatabase(name)
    }
}
