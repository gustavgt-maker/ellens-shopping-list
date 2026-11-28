package de.ellens.shoppinglist.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.ellens.shoppinglist.AppContainer
import de.ellens.shoppinglist.data.backup.BackupManager
import de.ellens.shoppinglist.data.backup.BackupSummary
import de.ellens.shoppinglist.data.backup.ImportCheck
import de.ellens.shoppinglist.data.backup.StoredBackup
import de.ellens.shoppinglist.data.settings.AppFont
import de.ellens.shoppinglist.data.settings.FontSizeStep
import de.ellens.shoppinglist.data.settings.SettingsRepository
import de.ellens.shoppinglist.data.settings.ShakeSensitivity
import de.ellens.shoppinglist.data.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Ein geprüfter, noch nicht bestätigter Import. */
data class PendingImport(val check: ImportCheck.Ready, val sourceLabel: String) {
    val summary: BackupSummary get() = check.summary
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backups: BackupManager,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    private val _internalBackups = MutableStateFlow<List<StoredBackup>>(emptyList())
    val internalBackups: StateFlow<List<StoredBackup>> = _internalBackups.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init { refreshBackups() }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setFont(font: AppFont) = viewModelScope.launch { settings.setFont(font) }
    fun setFontSize(step: FontSizeStep) = viewModelScope.launch { settings.setFontSize(step) }
    fun setHaptics(enabled: Boolean) = viewModelScope.launch { settings.setHapticsEnabled(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { settings.setKeepScreenOn(enabled) }
    fun setShakeSensitivity(s: ShakeSensitivity) = viewModelScope.launch { settings.setShakeSensitivity(s) }

    fun refreshBackups() = viewModelScope.launch {
        _internalBackups.value = withContext(Dispatchers.IO) { backups.listInternalBackups() }
    }

    private fun runBusy(block: suspend () -> Unit) = viewModelScope.launch {
        if (_busy.value) return@launch
        _busy.value = true
        try {
            block()
        } finally {
            _busy.value = false
        }
    }

    fun createManualBackup() = runBusy {
        try {
            backups.createManualBackup()
            _messages.send("Sicherung erstellt")
        } catch (e: Exception) {
            _messages.send("Sicherung fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}")
        }
        refreshBackups()
    }

    fun exportTo(resolver: ContentResolver, uri: Uri) = runBusy {
        try {
            withContext(Dispatchers.IO) {
                val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("Datei konnte nicht geöffnet werden.")
                out.use { backups.exportTo(it) }
            }
            _messages.send("Sicherung exportiert")
        } catch (e: Exception) {
            _messages.send("Export fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}")
        }
    }

    fun checkImport(resolver: ContentResolver, uri: Uri) = runBusy {
        val result = try {
            withContext(Dispatchers.IO) {
                val input = resolver.openInputStream(uri) ?: throw IOException("Datei konnte nicht geöffnet werden.")
                input.use { backups.check(it) }
            }
        } catch (e: Exception) {
            ImportCheck.Rejected(e.message ?: "Datei konnte nicht gelesen werden.")
        }
        handleCheck(result, "ausgewählte Datei")
    }

    fun checkInternal(backup: StoredBackup) = runBusy {
        val result = try {
            backups.checkInternal(backup.file)
        } catch (e: Exception) {
            ImportCheck.Rejected(e.message ?: "Sicherung konnte nicht gelesen werden.")
        }
        handleCheck(result, "interne Sicherung")
    }

    private suspend fun handleCheck(result: ImportCheck, label: String) {
        when (result) {
            is ImportCheck.Ready -> _pendingImport.value = PendingImport(result, label)
            is ImportCheck.Rejected -> _messages.send("Import nicht möglich: ${result.reason}")
        }
    }

    fun cancelImport() { _pendingImport.value = null }

    fun confirmImport() {
        val pending = _pendingImport.value ?: return
        _pendingImport.value = null
        runBusy {
            try {
                backups.restore(pending.check.backup)
                _messages.send("Sicherung wiederhergestellt")
            } catch (e: Exception) {
                _messages.send(
                    "Wiederherstellung fehlgeschlagen (${e.message ?: "unbekannter Fehler"}). " +
                        "Die Listen wurden nicht verändert; eine Sicherheitskopie liegt unter „Interne Sicherungen“."
                )
            }
            refreshBackups()
        }
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(c.settingsRepository, c.backupManager) }
        }
    }
}
