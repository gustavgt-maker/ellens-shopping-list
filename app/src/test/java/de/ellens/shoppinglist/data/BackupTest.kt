package de.ellens.shoppinglist.data

import de.ellens.shoppinglist.TestSupport
import de.ellens.shoppinglist.data.backup.BackupCodec
import de.ellens.shoppinglist.data.backup.BackupFile
import de.ellens.shoppinglist.data.backup.BackupFormat
import de.ellens.shoppinglist.data.backup.BackupKind
import de.ellens.shoppinglist.data.backup.BackupManager
import de.ellens.shoppinglist.data.backup.BackupStore
import de.ellens.shoppinglist.data.backup.DecodeResult
import de.ellens.shoppinglist.data.backup.ImportCheck
import de.ellens.shoppinglist.data.db.AppDatabase
import de.ellens.shoppinglist.data.repo.AddItemResult
import de.ellens.shoppinglist.data.repo.ShoppingRepository
import de.ellens.shoppinglist.data.settings.AppFont
import de.ellens.shoppinglist.data.settings.SettingsRepository
import de.ellens.shoppinglist.data.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

/** 12.–14. Backup erstellen/wiederherstellen, beschädigte Dateien, Rotation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ShoppingRepository
    private lateinit var settings: SettingsRepository
    private lateinit var backupDir: File
    private lateinit var manager: BackupManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before fun setUp() {
        db = TestSupport.inMemoryDb()
        repo = ShoppingRepository(db)
        val dir = TestSupport.tempDir("backup")
        settings = SettingsRepository(TestSupport.newDataStore(File(dir, "s.preferences_pb"), scope))
        backupDir = File(dir, "backups")
        manager = BackupManager(db, settings, BackupStore(backupDir), "test", 1)
    }

    @After fun tearDown() {
        db.close()
        scope.cancel()
    }

    private suspend fun seed(): Long {
        val list = repo.createList("Wocheneinkauf")!!
        repo.addItem(list, "Milch", "2 Packungen")
        repo.addItem(list, "Brot", "")
        val butter = (repo.addItem(list, "Butter", "1") as AddItemResult.Added).itemId
        repo.toggleDone(butter)
        repo.saveScrollPosition(list, 2, 5)
        repo.createList("Drogerie")
        settings.setThemeMode(ThemeMode.DARK)
        settings.setFont(AppFont.NOTO_SANS)
        settings.setLastOpenedList(list)
        return list
    }

    private fun bytesOf(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

    // 12. Backup erstellen und wiederherstellen
    @Test fun exportAndRestore_roundTripIsComplete() = runBlocking {
        val list = seed()
        val before = manager.createSnapshot()
        val out = ByteArrayOutputStream()
        manager.exportTo(out)

        // Alles verändern
        repo.deleteList(list)
        repo.createList("Neu")
        settings.setThemeMode(ThemeMode.LIGHT)

        val check = manager.check(ByteArrayInputStream(out.toByteArray()))
        assertTrue(check is ImportCheck.Ready)
        manager.restore((check as ImportCheck.Ready).backup)

        val after = manager.createSnapshot()
        assertEquals(before.lists, after.lists)
        assertEquals(before.items, after.items)
        assertEquals(before.catalog.map { it.name }.toSet(), after.catalog.map { it.name }.toSet())
        assertEquals(before.settings, after.settings)
        assertEquals(ThemeMode.DARK, settings.current().themeMode)
        assertEquals(list, settings.current().lastOpenedListId)
        assertTrue("Erledigt-Status bleibt erhalten", repo.observeItems(list).first().first { it.name == "Butter" }.isDone)
        // Vor dem Import wurde eine Sicherheitskopie angelegt
        assertEquals(1, BackupStore(backupDir).list(BackupKind.PRE_IMPORT).size)
    }

    @Test fun restoredDatabaseStillEnforcesRules() = runBlocking {
        val list = seed()
        val snapshot = manager.createSnapshot()
        manager.restore(snapshot)
        assertEquals(AddItemResult.Duplicate, repo.addItem(list, "milch", ""))
        assertTrue(repo.addItem(list, "Käse", "") is AddItemResult.Added)
    }

    // 13. Beschädigte Backup-Dateien
    @Test fun corruptedFiles_areRejectedWithoutTouchingData() = runBlocking {
        val list = seed()
        val before = manager.createSnapshot()
        val valid = BackupCodec.encode(before)

        val broken = listOf(
            "",
            "   ",
            "kein json",
            valid.substring(0, valid.length / 2),                         // abgeschnitten
            valid.replace(BackupFormat.FORMAT_ID, "fremdes-format"),        // falsches Format
            valid.replace("\"formatVersion\": 1", "\"formatVersion\": 99"), // zu neu
            valid.replace("\"name\": \"Milch\"", "\"name\": \"   \""),     // leerer Name
            "{\"format\":\"${BackupFormat.FORMAT_ID}\"}",                   // Pflichtfelder fehlen
        )
        for (text in broken) {
            val result = manager.check(bytesOf(text))
            assertTrue("Sollte abgelehnt werden: ${text.take(40)}", result is ImportCheck.Rejected)
        }
        // Binärmüll (kein gültiges UTF-8)
        val garbage = manager.check(ByteArrayInputStream(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x13)))
        assertTrue(garbage is ImportCheck.Rejected)

        // Ungültige Querverweise
        val dangling = before.copy(items = before.items.map { it.copy(listId = 9999) })
        assertFalse(BackupCodec.validate(dangling).isEmpty())
        try {
            manager.restore(dangling)
            fail("Ungültige Sicherung darf nicht eingespielt werden")
        } catch (expected: IllegalArgumentException) {
        }
        val duplicate = before.copy(items = before.items + before.items.first().copy(id = 777))
        assertTrue(BackupCodec.decode(BackupCodec.encode(duplicate)) is DecodeResult.Invalid)

        assertEquals("Vorhandene Daten unverändert", before.items, manager.createSnapshot().items)
        assertEquals(before.lists, manager.createSnapshot().lists)
        assertEquals(3, repo.observeItems(list).first().size)
    }

    @Test fun oversizedFile_isRejected() = runBlocking {
        val huge = ByteArray((BackupFormat.MAX_FILE_BYTES + 10).toInt()) { ' '.code.toByte() }
        assertTrue(manager.check(ByteArrayInputStream(huge)) is ImportCheck.Rejected)
    }

    // 14. Rotation: höchstens eine pro Tag, maximal drei automatische Sicherungen
    @Test fun dailyBackup_onceperDayAndKeepsThree() = runBlocking {
        seed()
        assertTrue(manager.runDailyBackupIfDue(LocalDate.of(2026, 11, 1)))
        assertFalse("Zweiter Lauf am selben Tag erzeugt nichts", manager.runDailyBackupIfDue(LocalDate.of(2026, 11, 1)))
        assertTrue(manager.runDailyBackupIfDue(LocalDate.of(2026, 11, 2)))
        assertTrue(manager.runDailyBackupIfDue(LocalDate.of(2026, 11, 3)))
        assertTrue(manager.runDailyBackupIfDue(LocalDate.of(2026, 11, 5)))

        val autos = BackupStore(backupDir).list(BackupKind.AUTO).map { it.file.name }
        assertEquals(listOf("auto_2026-11-05.json", "auto_2026-11-03.json", "auto_2026-11-02.json"), autos)

        // Jede aufbewahrte Sicherung ist gültig und wiederherstellbar
        BackupStore(backupDir).list(BackupKind.AUTO).forEach { b ->
            assertTrue(manager.checkInternal(b.file) is ImportCheck.Ready)
        }
        assertTrue(backupDir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun failedWrite_neverReplacesOlderBackups() {
        val store = BackupStore(backupDir)
        store.writeVerified(BackupStore.autoName(LocalDate.of(2026, 1, 1)), "ok") { true }
        store.writeVerified(BackupStore.autoName(LocalDate.of(2026, 1, 2)), "ok") { true }
        store.writeVerified(BackupStore.autoName(LocalDate.of(2026, 1, 3)), "ok") { true }
        try {
            store.writeVerified(BackupStore.autoName(LocalDate.of(2026, 1, 4)), "defekt") { false }
            fail("Verifikation hätte fehlschlagen müssen")
        } catch (expected: java.io.IOException) {
        }
        store.rotate(BackupKind.AUTO, BackupStore.KEEP_AUTO)
        assertEquals(3, store.list(BackupKind.AUTO).size)
        assertFalse(store.hasAutoBackupFor(LocalDate.of(2026, 1, 4)))
    }

    @Test fun manualBackups_areRotatedSeparately() = runBlocking {
        seed()
        manager.runDailyBackupIfDue(LocalDate.of(2026, 11, 1))
        repeat(5) { i -> manager.createManualBackup(java.time.LocalDateTime.of(2026, 11, 1, 10, i)) }
        val store = BackupStore(backupDir)
        assertEquals(3, store.list(BackupKind.MANUAL).size)
        assertEquals("Automatische Sicherungen unberührt", 1, store.list(BackupKind.AUTO).size)
    }

    @Test fun backupFile_containsEverything() = runBlocking {
        seed()
        val b: BackupFile = manager.createSnapshot()
        assertEquals(2, b.lists.size)
        assertEquals(3, b.items.size)
        assertEquals(3, b.catalog.size)
        assertEquals("NOTO_SANS", b.settings.font)
        assertTrue(b.lists.any { it.scrollIndex == 2 && it.scrollOffset == 5 })
        assertTrue(b.items.any { it.isDone && it.doneAt != null })
        assertEquals(BackupFormat.VERSION, b.formatVersion)
    }
}
