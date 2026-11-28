package de.ellens.shoppinglist.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FONT = stringPreferencesKey("font")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val LAST_LIST = longPreferencesKey("last_opened_list_id")
        val SHAKE = stringPreferencesKey("shake_sensitivity")
    }

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::fromPreferences)

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setFont(font: AppFont) = dataStore.edit { it[Keys.FONT] = font.name }
    suspend fun setFontSize(step: FontSizeStep) = dataStore.edit { it[Keys.FONT_SIZE] = step.name }
    suspend fun setHapticsEnabled(enabled: Boolean) = dataStore.edit { it[Keys.HAPTICS] = enabled }
    suspend fun setKeepScreenOn(enabled: Boolean) = dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }

    suspend fun setShakeSensitivity(s: ShakeSensitivity) = dataStore.edit { it[Keys.SHAKE] = s.name }

    suspend fun setLastOpenedList(id: Long?) = dataStore.edit {
        if (id == null) it.remove(Keys.LAST_LIST) else it[Keys.LAST_LIST] = id
    }

    /** Überschreibt alle Einstellungen in einem Schritt (Backup-Wiederherstellung). */
    suspend fun replaceAll(s: AppSettings) = dataStore.edit {
        it[Keys.THEME] = s.themeMode.name
        it[Keys.FONT] = s.font.name
        it[Keys.FONT_SIZE] = s.fontSize.name
        it[Keys.HAPTICS] = s.hapticsEnabled
        it[Keys.KEEP_SCREEN_ON] = s.keepScreenOn
        it[Keys.SHAKE] = s.shakeSensitivity.name
        if (s.lastOpenedListId == null) it.remove(Keys.LAST_LIST) else it[Keys.LAST_LIST] = s.lastOpenedListId
    }

    private fun fromPreferences(p: Preferences) = AppSettings(
        themeMode = enumOrDefault(p[Keys.THEME], ThemeMode.SYSTEM),
        font = enumOrDefault(p[Keys.FONT], AppFont.ROBOTO),
        fontSize = enumOrDefault(p[Keys.FONT_SIZE], FontSizeStep.NORMAL),
        hapticsEnabled = p[Keys.HAPTICS] ?: true,
        keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: false,
        lastOpenedListId = p[Keys.LAST_LIST],
        shakeSensitivity = enumOrDefault(p[Keys.SHAKE], ShakeSensitivity.MEDIUM),
    )
}
