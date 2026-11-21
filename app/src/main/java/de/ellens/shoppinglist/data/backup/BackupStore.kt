package de.ellens.shoppinglist.data.backup

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class BackupKind(val prefix: String, val label: String) {
    AUTO("auto_", "Automatisch"),
    MANUAL("manual_", "Manuell"),
    PRE_IMPORT("preimport_", "Vor Import"),
}

data class StoredBackup(val file: File, val kind: BackupKind, val sizeBytes: Long, val modifiedAt: Long)

/**
 * Dateiablage für interne Sicherungen im privaten App-Speicher.
 *
 * Sicherheitsprinzip: Es wird zuerst in eine temporäre Datei geschrieben, auf den Datenträger
 * synchronisiert, zurückgelesen und geprüft und erst dann per atomarem Umbenennen
 * freigegeben. Ältere Stände werden erst danach rotiert. Eine fehlgeschlagene Sicherung
 * verdrängt also nie eine funktionierende.
 */
class BackupStore(private val dir: File) {

    companion object {
        const val KEEP_AUTO = 3
        const val KEEP_MANUAL = 3
        const val KEEP_PRE_IMPORT = 2
        private const val TMP_SUFFIX = ".tmp"
        private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

        fun autoName(date: LocalDate) = "${BackupKind.AUTO.prefix}$date.json"
        fun timedName(kind: BackupKind, now: LocalDateTime) = "${kind.prefix}${now.format(DATE_TIME)}.json"
    }

    private fun ensureDir() {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Sicherungsordner konnte nicht angelegt werden.")
    }

    fun hasAutoBackupFor(date: LocalDate): Boolean = File(dir, autoName(date)).isFile

    fun list(kind: BackupKind? = null): List<StoredBackup> {
        val files = dir.listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.name.endsWith(".json") }
            .mapNotNull { f ->
                val k = BackupKind.entries.firstOrNull { f.name.startsWith(it.prefix) } ?: return@mapNotNull null
                if (kind != null && k != kind) null else StoredBackup(f, k, f.length(), f.lastModified())
            }
            // Namen enthalten Datum/Uhrzeit in sortierbarer Form: neueste zuerst.
            .sortedByDescending { it.file.name }
    }

    /**
     * Schreibt [content] sicher unter [fileName]. [verify] erhält den zurückgelesenen Inhalt
     * und muss true liefern, sonst wird die neue Datei verworfen.
     */
    fun writeVerified(fileName: String, content: String, verify: (String) -> Boolean): File {
        ensureDir()
        val target = File(dir, fileName)
        val tmp = File(dir, fileName + TMP_SUFFIX)
        try {
            FileOutputStream(tmp).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            val readBack = tmp.readText(Charsets.UTF_8)
            if (readBack != content || !verify(readBack)) {
                throw IOException("Sicherung konnte nicht verifiziert werden.")
            }
            if (target.exists() && !target.delete()) throw IOException("Alte Datei konnte nicht ersetzt werden.")
            if (!tmp.renameTo(target)) throw IOException("Sicherung konnte nicht abgeschlossen werden.")
            return target
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Behält die neuesten [keep] Sicherungen der Art [kind] und löscht ältere. */
    fun rotate(kind: BackupKind, keep: Int) {
        list(kind).drop(keep).forEach { it.file.delete() }
    }

    /** Entfernt Reste abgebrochener Schreibvorgänge. */
    fun cleanupTemporaryFiles() {
        dir.listFiles()?.filter { it.name.endsWith(TMP_SUFFIX) }?.forEach { it.delete() }
    }
}
