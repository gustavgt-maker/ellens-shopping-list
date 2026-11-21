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
