package de.ellens.shoppinglist.data.backup

import androidx.room.withTransaction
import de.ellens.shoppinglist.core.ListColors
import de.ellens.shoppinglist.core.NameNormalizer
import de.ellens.shoppinglist.data.db.AppDatabase
import de.ellens.shoppinglist.data.db.CatalogItemEntity
import de.ellens.shoppinglist.data.db.ShoppingItemEntity
import de.ellens.shoppinglist.data.db.ShoppingListEntity
import de.ellens.shoppinglist.data.settings.AppFont
import de.ellens.shoppinglist.data.settings.AppSettings
import de.ellens.shoppinglist.data.settings.FontSizeStep
import de.ellens.shoppinglist.data.settings.SettingsRepository
import de.ellens.shoppinglist.data.settings.ThemeMode
import de.ellens.shoppinglist.data.settings.enumOrDefault
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime

data class BackupSummary(val lists: Int, val items: Int, val catalog: Int, val createdAt: Long)

fun BackupFile.summary() = BackupSummary(lists.size, items.size, catalog.size, createdAt)

sealed interface ImportCheck {
    data class Ready(val backup: BackupFile, val summary: BackupSummary) : ImportCheck
    data class Rejected(val reason: String) : ImportCheck
}

/**
 * Erstellt, rotiert, exportiert und importiert Sicherungen.
 * Alle Vorgänge sind über [mutex] serialisiert, damit sich z. B. die Tagessicherung und ein
 * Import nicht überschneiden.
 */
class BackupManager(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val store: BackupStore,
    private val appVersionName: String,
    private val appVersionCode: Int,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    // ------------------------------------------------------------ Schnappschuss

    suspend fun createSnapshot(): BackupFile {
        val (lists, items, catalog) = db.withTransaction {
            Triple(db.listDao().getAllLists(), db.itemDao().getAllItems(), db.catalogDao().getAll())
        }
        val s = settings.current()
        return BackupFile(
            format = BackupFormat.FORMAT_ID,
            formatVersion = BackupFormat.VERSION,
            createdAt = now(),
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            databaseVersion = AppDatabase.VERSION,
            lists = lists.map {
                BackupList(it.id, it.name, it.createdAt, it.lastUsedAt, it.scrollIndex, it.scrollOffset, it.colorIndex)
            },
            items = items.map {
                BackupItem(it.id, it.listId, it.name, it.quantity, it.isDone, it.position, it.doneAt, it.createdAt)
            },
            catalog = catalog.map { BackupCatalogItem(it.id, it.name, it.createdAt) },
            settings = BackupSettings(
                themeMode = s.themeMode.name,
                font = s.font.name,
                fontSize = s.fontSize.name,
                hapticsEnabled = s.hapticsEnabled,
                keepScreenOn = s.keepScreenOn,
                lastOpenedListId = s.lastOpenedListId,
            ),
        )
    }

    private fun verifier(): (String) -> Boolean = { text -> BackupCodec.decode(text) is DecodeResult.Valid }

    // ------------------------------------------------------------ Interne Sicherungen

    /**
     * Höchstens eine automatische Sicherung pro Kalendertag. Die drei neuesten erfolgreichen
     * bleiben erhalten; rotiert wird erst nach erfolgreichem Schreiben.
     * @return true, wenn eine neue Sicherung erstellt wurde.
     */
    suspend fun runDailyBackupIfDue(today: LocalDate = LocalDate.now()): Boolean = mutex.withLock {
        withContext(io) {
            store.cleanupTemporaryFiles()
            if (store.hasAutoBackupFor(today)) return@withContext false
            val content = BackupCodec.encode(createSnapshot())
            store.writeVerified(BackupStore.autoName(today), content, verifier())
            store.rotate(BackupKind.AUTO, BackupStore.KEEP_AUTO)
            true
        }
    }

    suspend fun createManualBackup(at: LocalDateTime = LocalDateTime.now()): File = mutex.withLock {
        withContext(io) {
            val file = store.writeVerified(
                BackupStore.timedName(BackupKind.MANUAL, at),
                BackupCodec.encode(createSnapshot()),
                verifier(),
            )
            store.rotate(BackupKind.MANUAL, BackupStore.KEEP_MANUAL)
            file
        }
    }

    fun listInternalBackups(): List<StoredBackup> = store.list()

    // ------------------------------------------------------------ Export / Import

    suspend fun exportTo(output: OutputStream) = mutex.withLock {
        withContext(io) {
            val content = BackupCodec.encode(createSnapshot())
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    /** Liest und prüft eine Sicherung, ohne irgendetwas zu verändern. */
    suspend fun check(input: InputStream): ImportCheck = withContext(io) {
        val bytes = try {
            readLimited(input, BackupFormat.MAX_FILE_BYTES)
        } catch (e: IOException) {
            return@withContext ImportCheck.Rejected(e.message ?: "Datei konnte nicht gelesen werden.")
        }
        val text = try {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            return@withContext ImportCheck.Rejected("Die Datei ist keine Textdatei im erwarteten Format.")
        }
        when (val r = BackupCodec.decode(text)) {
            is DecodeResult.Valid -> ImportCheck.Ready(r.backup, r.backup.summary())
            is DecodeResult.Invalid -> ImportCheck.Rejected(r.reason)
        }
    }

    suspend fun checkInternal(file: File): ImportCheck = file.inputStream().use { check(it) }

    /**
     * Ersetzt alle Daten durch [backup]. Vorher wird der aktuelle Stand als Sicherheitskopie
     * abgelegt. Der Datenbankteil läuft in einer einzigen Transaktion: schlägt etwas fehl,
     * bleibt der bisherige Stand vollständig erhalten.
     */
    suspend fun restore(backup: BackupFile) = mutex.withLock {
        withContext(io) {
            val problems = BackupCodec.validate(backup)
            require(problems.isEmpty()) { problems.first() }

            store.writeVerified(
                BackupStore.timedName(BackupKind.PRE_IMPORT, LocalDateTime.now()),
                BackupCodec.encode(createSnapshot()),
                verifier(),
            )
            store.rotate(BackupKind.PRE_IMPORT, BackupStore.KEEP_PRE_IMPORT)

            db.withTransaction {
                db.itemDao().deleteAll()
                db.listDao().deleteAll()
                db.catalogDao().deleteAll()
                db.listDao().insertAll(backup.lists.map {
                    ShoppingListEntity(
                        id = it.id,
                        name = NameNormalizer.clean(it.name),
                        createdAt = it.createdAt,
                        lastUsedAt = it.lastUsedAt,
                        scrollIndex = it.scrollIndex.coerceAtLeast(0),
                        scrollOffset = it.scrollOffset.coerceAtLeast(0),
                        colorIndex = it.colorIndex.takeIf { c -> ListColors.isValid(c) } ?: 0,
                    )
                })
                db.itemDao().insertAll(backup.items.map {
                    ShoppingItemEntity(
                        id = it.id,
                        listId = it.listId,
                        name = NameNormalizer.clean(it.name),
                        normalizedName = NameNormalizer.normalize(it.name),
                        quantity = NameNormalizer.cleanQuantity(it.quantity),
                        isDone = it.isDone,
                        position = it.position,
                        doneAt = if (it.isDone) (it.doneAt ?: it.createdAt) else null,
                        createdAt = it.createdAt,
                    )
                })
                // Doppelte Katalogeinträge (gleicher normalisierter Name) werden zusammengeführt.
                db.catalogDao().insertAllIfAbsent(backup.catalog.map {
                    CatalogItemEntity(
                        id = it.id,
                        name = NameNormalizer.clean(it.name),
                        normalizedName = NameNormalizer.normalize(it.name),
                        createdAt = it.createdAt,
                    )
                })
            }

            val bs = backup.settings
            val restoredLastList = bs.lastOpenedListId?.takeIf { id -> backup.lists.any { it.id == id } }
            settings.replaceAll(
                AppSettings(
                    themeMode = enumOrDefault(bs.themeMode, ThemeMode.SYSTEM),
                    font = enumOrDefault(bs.font, AppFont.ROBOTO),
                    fontSize = enumOrDefault(bs.fontSize, FontSizeStep.NORMAL),
                    hapticsEnabled = bs.hapticsEnabled,
                    keepScreenOn = bs.keepScreenOn,
                    lastOpenedListId = restoredLastList,
                )
            )
        }
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw IOException("Die Datei ist zu groß für eine Sicherung.")
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }
}
