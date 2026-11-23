package de.ellens.shoppinglist

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import de.ellens.shoppinglist.data.backup.BackupManager
import de.ellens.shoppinglist.data.backup.BackupStore
import de.ellens.shoppinglist.data.db.AppDatabase
import de.ellens.shoppinglist.data.repo.CatalogRepository
import de.ellens.shoppinglist.data.repo.ShoppingRepository
import de.ellens.shoppinglist.data.settings.SettingsRepository
import java.io.File

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Einfache manuelle Abhängigkeitsverwaltung (bewusst ohne DI-Framework). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = AppDatabase.build(appContext)
    val shoppingRepository = ShoppingRepository(database)
    val catalogRepository = CatalogRepository(database)
    val settingsRepository = SettingsRepository(appContext.settingsDataStore)
    /** Per „Teilen“ empfangener Text, bis der Import-Bildschirm ihn übernimmt. */
    val shareInbox = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Ergebnis des letzten Text-Imports; die Listenansicht zeigt es einmal als Hinweis an. */
    val importResult = kotlinx.coroutines.flow.MutableStateFlow<de.ellens.shoppinglist.ui.importtext.ImportDone?>(null)

    val backupManager = BackupManager(
        db = database,
        settings = settingsRepository,
        store = BackupStore(File(appContext.filesDir, "backups")),
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
    )
}

class EllenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
