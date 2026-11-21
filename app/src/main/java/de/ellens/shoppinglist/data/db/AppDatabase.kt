package de.ellens.shoppinglist.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [ShoppingListEntity::class, ShoppingItemEntity::class, CatalogItemEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ShoppingListDao
    abstract fun itemDao(): ShoppingItemDao
    abstract fun catalogDao(): CatalogDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "shopping.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .addMigrations(*Migrations.ALL)
                // Bewusst KEIN fallbackToDestructiveMigration(): Datenverlust bei Updates ist
                // ausgeschlossen. Fehlt eine Migration, schlägt der Start sichtbar fehl, statt
                // stillschweigend Daten zu löschen.
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

/**
 * Sammelstelle für Schema-Migrationen. Version 1 ist das Ausgangsschema.
 *
 * Vorgehen für Version 2:
 *  1. Entity ändern, [AppDatabase.VERSION] erhöhen.
 *  2. Hier `val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { ... } }`
 *     ergänzen und in [ALL] eintragen.
 *  3. Das exportierte Schema unter app/schemas/ einchecken und mit MigrationTestHelper testen.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
