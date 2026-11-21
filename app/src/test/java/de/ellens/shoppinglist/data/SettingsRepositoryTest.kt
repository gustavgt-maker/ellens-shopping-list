package de.ellens.shoppinglist.data

import de.ellens.shoppinglist.TestSupport
import de.ellens.shoppinglist.data.settings.AppFont
import de.ellens.shoppinglist.data.settings.AppSettings
import de.ellens.shoppinglist.data.settings.FontSizeStep
import de.ellens.shoppinglist.data.settings.SettingsRepository
import de.ellens.shoppinglist.data.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** 11. Einstellungen werden dauerhaft gespeichert (reiner JVM-Test mit echter Datei). */
class SettingsRepositoryTest {

    @Test fun defaultsWhenEmpty() = runBlocking {
        val dir = TestSupport.tempDir("settings")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = SettingsRepository(TestSupport.newDataStore(File(dir, "s.preferences_pb"), scope))
        assertEquals(AppSettings(), repo.current())
        scope.cancel()
    }

    @Test fun settingsSurviveRestart() = runBlocking {
        val file = File(TestSupport.tempDir("settings"), "s.preferences_pb")

        val job1 = SupervisorJob()
        val scope1 = CoroutineScope(Dispatchers.IO + job1)
        val repo1 = SettingsRepository(TestSupport.newDataStore(file, scope1))
        repo1.setThemeMode(ThemeMode.DARK)
        repo1.setFont(AppFont.ATKINSON)
        repo1.setFontSize(FontSizeStep.XLARGE)
        repo1.setHapticsEnabled(false)
        repo1.setKeepScreenOn(true)
        repo1.setLastOpenedList(42L)
        // Simuliert App-Ende: DataStore-Instanz vollständig schließen, bevor die Datei neu geöffnet wird.
        job1.cancelAndJoin()

        val scope2 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo2 = SettingsRepository(TestSupport.newDataStore(file, scope2))
        assertEquals(
            AppSettings(ThemeMode.DARK, AppFont.ATKINSON, FontSizeStep.XLARGE, false, true, 42L),
            repo2.current(),
        )
        repo2.setLastOpenedList(null)
        assertEquals(null, repo2.current().lastOpenedListId)
        scope2.cancel()
    }
}
