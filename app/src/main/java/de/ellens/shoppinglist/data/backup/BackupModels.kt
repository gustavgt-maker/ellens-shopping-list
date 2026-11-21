package de.ellens.shoppinglist.data.backup

import kotlinx.serialization.Serializable

/**
 * Format der Sicherungsdatei (JSON). Jede inkompatible Änderung erhöht [BackupFormat.VERSION].
 * Ältere Versionen müssen beim Import weiterhin gelesen werden können.
 */
object BackupFormat {
    const val FORMAT_ID = "ellens-shopping-list-backup"
    const val VERSION = 1
    const val MIME_TYPE = "application/json"
    /** Obergrenze gegen versehentlich gewählte Riesendateien. */
    const val MAX_FILE_BYTES = 20L * 1024 * 1024
}

@Serializable
data class BackupFile(
    val format: String,
    val formatVersion: Int,
    val createdAt: Long,
    val appVersionName: String,
    val appVersionCode: Int,
    val databaseVersion: Int,
    val lists: List<BackupList>,
    val items: List<BackupItem>,
    val catalog: List<BackupCatalogItem>,
    val settings: BackupSettings,
)

@Serializable
data class BackupList(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val lastUsedAt: Long,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

@Serializable
data class BackupItem(
    val id: Long,
    val listId: Long,
    val name: String,
    val quantity: String = "",
    val isDone: Boolean = false,
    val position: Long,
    val doneAt: Long? = null,
    val createdAt: Long,
)

@Serializable
data class BackupCatalogItem(
    val id: Long,
    val name: String,
    val createdAt: Long,
)

@Serializable
data class BackupSettings(
    val themeMode: String = "SYSTEM",
    val font: String = "ROBOTO",
    val fontSize: String = "NORMAL",
    val hapticsEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val lastOpenedListId: Long? = null,
)
