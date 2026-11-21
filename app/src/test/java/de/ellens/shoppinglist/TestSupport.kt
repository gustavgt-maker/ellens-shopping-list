package de.ellens.shoppinglist

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.ellens.shoppinglist.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

object TestSupport {
    val context: Context get() = ApplicationProvider.getApplicationContext()

    fun inMemoryDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    /** Steuerbare Uhr, damit Zeitstempel in Tests eindeutig und reproduzierbar sind. */
    class FakeClock(var now: Long = 1_000_000L) : () -> Long {
        override fun invoke(): Long = ++now
    }

    fun newDataStore(file: File, scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    fun tempDir(prefix: String): File =
        File.createTempFile(prefix, "").apply { delete(); mkdirs(); deleteOnExit() }
}
