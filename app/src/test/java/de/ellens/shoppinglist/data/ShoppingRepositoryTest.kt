package de.ellens.shoppinglist.data

import de.ellens.shoppinglist.TestSupport
import de.ellens.shoppinglist.data.db.AppDatabase
import de.ellens.shoppinglist.data.db.ShoppingItemEntity
import de.ellens.shoppinglist.data.repo.AddItemResult
import de.ellens.shoppinglist.data.repo.CatalogRepository
import de.ellens.shoppinglist.data.repo.CatalogResult
import de.ellens.shoppinglist.data.repo.EditItemResult
import de.ellens.shoppinglist.data.repo.ShoppingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShoppingRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ShoppingRepository
    private lateinit var catalog: CatalogRepository
    private val clock = TestSupport.FakeClock()

    @Before fun setUp() {
        db = TestSupport.inMemoryDb()
        repo = ShoppingRepository(db, clock)
        catalog = CatalogRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun items(listId: Long) = repo.observeItems(listId).first()
    private suspend fun openNames(listId: Long) =
        items(listId).filter { !it.isDone }.sortedBy { it.position }.map { it.name }
    private suspend fun add(listId: Long, name: String, q: String = ""): Long =
        (repo.addItem(listId, name, q) as AddItemResult.Added).itemId

    // 1. Listen erstellen, umbenennen, leeren, löschen
    @Test fun lists_createRenameClearDelete() = runBlocking {
        assertNull(repo.createList("   "))
        val id = repo.createList("  Wocheneinkauf ")!!
        assertEquals("Wocheneinkauf", repo.getList(id)!!.name)

        assertTrue(repo.renameList(id, "Samstag"))
        assertFalse(repo.renameList(id, " "))
        assertEquals("Samstag", repo.getList(id)!!.name)

        add(id, "Milch"); add(id, "Brot")
        repo.toggleDone(items(id).first().id)
        assertEquals(2, repo.clearList(id))
        assertTrue(items(id).isEmpty())
        assertNotNull("Liste bleibt nach dem Leeren bestehen", repo.getList(id))

        add(id, "Butter")
        assertTrue(repo.deleteList(id))
        assertNull(repo.getList(id))
        assertTrue("Artikel werden mitgelöscht", db.itemDao().getAllItems().isEmpty())
    }

    @Test fun overview_sortedByLastUseWithCounts() = runBlocking {
        val a = repo.createList("A")!!
        val b = repo.createList("B")!!
        add(a, "Milch"); add(a, "Brot")
        repo.toggleDone(items(a).first { it.name == "Milch" }.id)
        repo.markListOpened(b)
        val overview = repo.observeOverview().first()
        assertEquals(listOf(b, a), overview.map { it.list.id })
        val entryA = overview.first { it.list.id == a }
        assertEquals(2, entryA.itemCount)
        assertEquals(1, entryA.openCount)
    }

    // 2. Artikel hinzufügen und bearbeiten
    @Test fun items_addAndEdit() = runBlocking {
        val list = repo.createList("L")!!
        assertEquals(AddItemResult.InvalidName, repo.addItem(list, "   ", "2"))
        val id = add(list, "  Milch  ", " 2 Packungen ")
        val item = db.itemDao().getItem(id)!!
        assertEquals("Milch", item.name)
        assertEquals("2 Packungen", item.quantity)

        add(list, "Brot")
        assertEquals("Brot ohne Menge", "", db.itemDao().getItem(items(list).first { it.name == "Brot" }.id)!!.quantity)

        repo.toggleDone(id)
        assertEquals(EditItemResult.Saved, repo.editItem(id, "Vollmilch", "3"))
        val edited = db.itemDao().getItem(id)!!
        assertEquals("Vollmilch", edited.name)
        assertEquals("3", edited.quantity)
        assertTrue("Bearbeiten ändert den Erledigt-Status nicht", edited.isDone)
        assertEquals(item.position, edited.position)
        assertEquals(EditItemResult.InvalidName, repo.editItem(id, " ", ""))
        assertEquals(EditItemResult.Duplicate, repo.editItem(id, " BROT ", ""))
    }

    @Test fun newItems_appendAtEndOfOpenItems() = runBlocking {
        val list = repo.createList("L")!!
        add(list, "A"); val b = add(list, "B"); add(list, "C")
        repo.toggleDone(b)
        add(list, "D")
        assertEquals(listOf("A", "C", "D"), openNames(list))
    }

    // 3. Dublettenprüfung
    @Test fun duplicates_areRejectedIgnoringCaseAndSpaces() = runBlocking {
        val list = repo.createList("L")!!
        add(list, "Milch")
        assertEquals(AddItemResult.Duplicate, repo.addItem(list, "milch", ""))
        assertEquals(AddItemResult.Duplicate, repo.addItem(list, "  MILCH ", "1"))
        assertEquals(1, items(list).size)
        // Gleicher Name in anderer Liste ist erlaubt
        val other = repo.createList("Andere")!!
        assertTrue(repo.addItem(other, "Milch", "") is AddItemResult.Added)
        // Auch erledigte Artikel zählen als vorhanden
        repo.toggleDone(items(list).first().id)
        assertEquals(AddItemResult.Duplicate, repo.addItem(list, "Milch", ""))
    }

    // 4. Automatische Aufnahme in die Vorschlagsdatenbank
    @Test fun catalog_learnsNewItemsAndSurvivesListDeletion() = runBlocking {
        val list = repo.createList("L")!!
        add(list, "Milch"); add(list, "Brot")
        val other = repo.createList("X")!!
        add(other, "milch") // bereits bekannt -> kein zweiter Eintrag
        assertEquals(setOf("Milch", "Brot"), catalog.observeAll().first().map { it.name }.toSet())

        repo.clearList(list)
        repo.deleteList(other)
        assertEquals("Leeren/Löschen entfernt keine Vorschläge", 2, catalog.observeAll().first().size)
    }

    @Test fun catalog_manageWithoutTouchingListItems() = runBlocking {
        val list = repo.createList("L")!!
        add(list, "Milch")
        val milch = catalog.observeAll().first().first()
        assertEquals(CatalogResult.OK, catalog.rename(milch.id, "Hafermilch"))
        assertEquals(CatalogResult.OK, catalog.add("Käse"))
        assertEquals(CatalogResult.DUPLICATE, catalog.add(" käse "))
        assertEquals(CatalogResult.INVALID, catalog.add(""))
        assertEquals(CatalogResult.DUPLICATE, catalog.rename(milch.id, "KÄSE"))
        assertTrue(catalog.delete(milch.id))
        assertEquals("Listeneintrag bleibt unverändert", listOf("Milch"), items(list).map { it.name })
    }

    // 6. + 7. Abhaken, erneut aktivieren, ursprüngliche Position
    @Test fun toggleDone_restoresOriginalPosition() = runBlocking {
        val list = repo.createList("L")!!
        val ids = listOf("A", "B", "C", "D").map { add(list, it) }
        repo.toggleDone(ids[1]) // B erledigt
        assertEquals(listOf("A", "C", "D"), openNames(list))
        val b = db.itemDao().getItem(ids[1])!!
        assertTrue(b.isDone)
        assertNotNull(b.doneAt)

        repo.toggleDone(ids[1]) // B wieder aktiv
        assertEquals(listOf("A", "B", "C", "D"), openNames(list))
        assertNull(db.itemDao().getItem(ids[1])!!.doneAt)
    }

    @Test fun toggleDone_positionStaysConsistentAfterReordering() = runBlocking {
        val list = repo.createList("L")!!
        val ids = listOf("A", "B", "C", "D", "E").map { add(list, it) }
        repo.toggleDone(ids[2])               // C erledigt: A B D E
        repo.moveOpenItem(list, ids[4], 0)    // E nach oben: E A B D
        repo.moveOpenItem(list, ids[0], 3)    // A ans Ende: E B D A
        assertEquals(listOf("E", "B", "D", "A"), openNames(list))
        repo.toggleDone(ids[2])               // C kehrt zwischen B und D zurück
        assertEquals(listOf("E", "B", "C", "D", "A"), openNames(list))
    }

    // 8. Manuelles Umordnen
    @Test fun reorder_movesAndPersistsOrder() = runBlocking {
        val list = repo.createList("L")!!
        val ids = listOf("A", "B", "C", "D").map { add(list, it) }
        repo.moveOpenItem(list, ids[3], 0)
        assertEquals(listOf("D", "A", "B", "C"), openNames(list))
        repo.moveOpenItem(list, ids[3], 2)
        assertEquals(listOf("A", "B", "D", "C"), openNames(list))
        repo.moveOpenItem(list, ids[0], 99) // wird auf Ende begrenzt
        assertEquals(listOf("B", "D", "C", "A"), openNames(list))
    }

    @Test fun reorder_manyMovesTriggerRenumberingAndStayConsistent() = runBlocking {
        val list = repo.createList("L")!!
        val ids = (1..6).map { add(list, "Artikel $it") }
        repo.toggleDone(ids[1])
        // Wiederholt zwischen dieselben Nachbarn schieben, bis die Lücken aufgebraucht sind.
        repeat(30) { i -> repo.moveOpenItem(list, if (i % 2 == 0) ids[5] else ids[4], 1) }
        val all = items(list)
        assertEquals("Positionen bleiben eindeutig", all.size, all.map { it.position }.toSet().size)
        val open = openNames(list)
        assertEquals(5, open.size)
        assertEquals(open.toSet().size, open.size)
        repo.toggleDone(ids[1])
        assertEquals(6, openNames(list).size)
    }

    // 9. Löschen und Rückgängig
    @Test fun deleteAndUndo_restoresCompleteItem() = runBlocking {
        val list = repo.createList("L")!!
        val ids = listOf("A", "B", "C").map { add(list, it, "1 kg") }
        repo.toggleDone(ids[1])
        val before = db.itemDao().getItem(ids[1])!!
        val deleted: ShoppingItemEntity = repo.deleteItem(ids[1])!!
        assertNull(db.itemDao().getItem(ids[1]))

        assertTrue(repo.restoreItem(deleted))
        assertEquals("Menge, Status, Position und ID identisch", before, db.itemDao().getItem(ids[1]))

        // Wiederherstellen scheitert sauber, wenn inzwischen gleichnamiger Artikel existiert
        val again = repo.deleteItem(ids[1])!!
        add(list, "b")
        assertFalse(repo.restoreItem(again))
    }

    @Test fun deleteItem_twiceIsHarmless() = runBlocking {
        val list = repo.createList("L")!!
        val id = add(list, "A")
        assertNotNull(repo.deleteItem(id))
        assertNull(repo.deleteItem(id))
    }

    @Test fun scrollPosition_isStored() = runBlocking {
        val list = repo.createList("L")!!
        repo.saveScrollPosition(list, 12, 34)
        val l = repo.getList(list)!!
        assertEquals(12, l.scrollIndex)
        assertEquals(34, l.scrollOffset)
    }
}
