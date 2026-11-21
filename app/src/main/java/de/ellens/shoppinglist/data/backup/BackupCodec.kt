package de.ellens.shoppinglist.data.backup

import de.ellens.shoppinglist.core.NameNormalizer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface DecodeResult {
    data class Valid(val backup: BackupFile) : DecodeResult
    data class Invalid(val reason: String) : DecodeResult
}

/** Serialisierung und strenge Prüfung von Sicherungsdateien. */
object BackupCodec {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        // Neuere Zusatzfelder einer gleichen Formatversion stören nicht.
        ignoreUnknownKeys = true
    }

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    fun decode(text: String): DecodeResult {
        if (text.isBlank()) return DecodeResult.Invalid("Die Datei ist leer.")
        val parsed = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            return DecodeResult.Invalid("Die Datei ist keine gültige Sicherung (Formatfehler).")
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Invalid("Die Datei ist keine gültige Sicherung (Formatfehler).")
        }
        val problems = validate(parsed)
        return if (problems.isEmpty()) DecodeResult.Valid(parsed)
        else DecodeResult.Invalid(problems.first())
    }

    /** Plausibilitätsprüfung. Liefert eine Liste verständlicher Fehlermeldungen. */
    fun validate(b: BackupFile): List<String> {
        val problems = mutableListOf<String>()
        if (b.format != BackupFormat.FORMAT_ID) {
            problems += "Die Datei stammt nicht aus Ellen's Shopping List."
            return problems
        }
        if (b.formatVersion < 1) problems += "Unbekannte Sicherungsversion ${b.formatVersion}."
        if (b.formatVersion > BackupFormat.VERSION) {
            problems += "Die Sicherung stammt aus einer neueren App-Version (Format ${b.formatVersion}). Bitte App aktualisieren."
        }

        val listIds = b.lists.map { it.id }
        if (listIds.size != listIds.toSet().size) problems += "Doppelte Listen-IDs in der Sicherung."
        if (b.lists.any { !NameNormalizer.isValidName(it.name) }) problems += "Eine Liste hat einen ungültigen Namen."

        val itemIds = b.items.map { it.id }
        if (itemIds.size != itemIds.toSet().size) problems += "Doppelte Artikel-IDs in der Sicherung."
        val listIdSet = listIds.toSet()
        if (b.items.any { it.listId !in listIdSet }) problems += "Artikel verweisen auf nicht vorhandene Listen."
        if (b.items.any { !NameNormalizer.isValidName(it.name) }) problems += "Ein Artikel hat einen ungültigen Namen."
        if (b.items.any { it.quantity.length > NameNormalizer.MAX_QUANTITY_LENGTH * 4 }) {
            problems += "Eine Mengenangabe ist unplausibel lang."
        }
        val duplicateInList = b.items
            .groupBy { it.listId to NameNormalizer.normalize(it.name) }
            .any { it.value.size > 1 }
        if (duplicateInList) problems += "Eine Liste enthält doppelte Artikel."

        if (b.catalog.any { !NameNormalizer.isValidName(it.name) }) problems += "Die Artikeldatenbank enthält ungültige Namen."
        val catalogIds = b.catalog.map { it.id }
        if (catalogIds.size != catalogIds.toSet().size) problems += "Doppelte IDs in der Artikeldatenbank."
        return problems
    }
}
