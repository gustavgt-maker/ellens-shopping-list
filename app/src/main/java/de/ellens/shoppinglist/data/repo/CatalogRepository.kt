package de.ellens.shoppinglist.data.repo

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import de.ellens.shoppinglist.core.NameNormalizer
import de.ellens.shoppinglist.data.db.AppDatabase
import de.ellens.shoppinglist.data.db.CatalogItemEntity
import kotlinx.coroutines.flow.Flow

enum class CatalogResult { OK, DUPLICATE, INVALID, NOT_FOUND }

/**
 * Verwaltung der Artikeldatenbank (Vorschläge). Änderungen hier wirken sich bewusst NICHT
 * auf vorhandene Listeneinträge aus – Listeneinträge speichern ihren Namen eigenständig.
 */
class CatalogRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.catalogDao()

    fun observeAll(): Flow<List<CatalogItemEntity>> = dao.observeAll()

    suspend fun add(rawName: String): CatalogResult {
        if (!NameNormalizer.isValidName(rawName)) return CatalogResult.INVALID
        val id = dao.insertIfAbsent(
            CatalogItemEntity(
                name = NameNormalizer.clean(rawName),
                normalizedName = NameNormalizer.normalize(rawName),
                createdAt = clock(),
            )
        )
        return if (id == -1L) CatalogResult.DUPLICATE else CatalogResult.OK
    }

    suspend fun rename(id: Long, rawName: String): CatalogResult {
        if (!NameNormalizer.isValidName(rawName)) return CatalogResult.INVALID
        val normalized = NameNormalizer.normalize(rawName)
        return try {
            db.withTransaction {
                val clash = dao.findByNormalizedName(normalized)
                if (clash != null && clash.id != id) return@withTransaction CatalogResult.DUPLICATE
                if (dao.rename(id, NameNormalizer.clean(rawName), normalized) > 0) CatalogResult.OK
                else CatalogResult.NOT_FOUND
            }
        } catch (e: SQLiteConstraintException) {
            CatalogResult.DUPLICATE
        }
    }

    suspend fun delete(id: Long): Boolean = dao.delete(id) > 0
}
