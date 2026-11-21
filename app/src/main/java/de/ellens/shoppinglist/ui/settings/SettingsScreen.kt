@file:OptIn(ExperimentalMaterial3Api::class)

package de.ellens.shoppinglist.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ellens.shoppinglist.BuildConfig
import de.ellens.shoppinglist.data.backup.BackupFormat
import de.ellens.shoppinglist.data.backup.StoredBackup
import de.ellens.shoppinglist.data.settings.AppFont
import de.ellens.shoppinglist.data.settings.AppSettings
import de.ellens.shoppinglist.data.settings.FontSizeStep
import de.ellens.shoppinglist.data.settings.ThemeMode
import de.ellens.shoppinglist.ui.theme.fontFamilyFor
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
) {
    val context = LocalContext.current
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pending by viewModel.pendingImport.collectAsStateWithLifecycle()
    val internal by viewModel.internalBackups.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE)
    ) { uri -> if (uri != null) viewModel.exportTo(context.contentResolver, uri) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.checkImport(context.contentResolver, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            SectionTitle("Darstellung")
            SubTitle("Design")
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    RadioRow(mode.label, settings.themeMode == mode) { viewModel.setTheme(mode) }
                }
            }
            SubTitle("Schriftart")
            Column(Modifier.selectableGroup()) {
                AppFont.entries.forEach { font ->
                    RadioRow(
                        label = font.label,
                        selected = settings.font == font,
                        preview = { Text("Milch, Äpfel, Brot", fontFamily = fontFamilyFor(font), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    ) { viewModel.setFont(font) }
                }
            }
            SubTitle("Schriftgröße")
            Text(
                "Wirkt zusätzlich zur Schriftgröße in den Android-Einstellungen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.selectableGroup()) {
                FontSizeStep.entries.forEach { step ->
                    RadioRow(step.label, settings.fontSize == step) { viewModel.setFontSize(step) }
                }
            }

            Divider()
            SectionTitle("Bedienung")
            SwitchRow(
                label = "Haptische Rückmeldung",
                description = "Kurze Vibration beim Abhaken, Verschieben und Löschen",
                checked = settings.hapticsEnabled,
                onChange = viewModel::setHaptics,
            )
            SwitchRow(
                label = "Bildschirm bleibt an",
                description = if (settings.keepScreenOn) "Aktiv – gilt nur, solange die App sichtbar ist"
                else "Aus – normale Bildschirmabschaltung",
                checked = settings.keepScreenOn,
                onChange = viewModel::setKeepScreenOn,
            )

            Divider()
            SectionTitle("Artikel")
            NavRow("Artikeldatenbank verwalten", "Vorschläge anzeigen, suchen, hinzufügen, umbenennen, löschen", onOpenCatalog)

            Divider()
            SectionTitle("Datensicherung")
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    "Wichtig: Interne Sicherungen liegen im privaten App-Speicher und gehen bei einer " +
                        "Deinstallation der App verloren. Exportiere regelmäßig eine Sicherung in einen eigenen Ordner.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Text(
                "Automatisch: höchstens einmal täglich beim Öffnen der App, die letzten 3 Stände werden aufbewahrt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = viewModel::createManualBackup, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Backup jetzt erstellen (intern)")
                }
                OutlinedButton(
                    onClick = { exportLauncher.launch("einkaufsliste-backup-${LocalDate.now()}.json") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Backup exportieren …") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Backup importieren …") }
            }
            SubTitle("Interne Sicherungen")
            if (internal.isEmpty()) {
                Text(
                    "Noch keine vorhanden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            internal.forEach { b -> InternalBackupRow(b, enabled = !busy) { viewModel.checkInternal(b) } }

            Divider()
            SectionTitle("Über die App")
            Text(
                "Ellen's Shopping List\nVersion ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                    "Alle Daten bleiben ausschließlich auf diesem Gerät. Keine Internetverbindung, keine Werbung, kein Tracking.\n" +
                    "Schriften: Roboto, Open Sans, Noto Sans, Atkinson Hyperlegible – SIL Open Font License 1.1.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

    pending?.let { p ->
        val created = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.summary.createdAt))
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("Sicherung wiederherstellen?") },
            text = {
                Text(
                    "Quelle: ${p.sourceLabel}\nErstellt: $created\n" +
                        "${p.summary.lists} Listen · ${p.summary.items} Artikel · ${p.summary.catalog} Vorschläge\n\n" +
                        "ALLE aktuellen Listen, Artikel, Vorschläge und Einstellungen werden dadurch ersetzt. " +
                        "Der aktuelle Stand wird vorher automatisch als Sicherheitskopie gesichert."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport) {
                    Text("Daten ersetzen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SubTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun Divider() = HorizontalDivider(
    color = MaterialTheme.colorScheme.outlineVariant,
    modifier = Modifier.padding(top = 12.dp),
)

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    preview: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            preview?.invoke()
        }
    }
}

@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NavRow(label: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun InternalBackupRow(backup: StoredBackup, enabled: Boolean, onRestore: () -> Unit) {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(backup.modifiedAt))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${backup.kind.label} · $date", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${(backup.sizeBytes + 1023) / 1024} KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRestore, enabled = enabled) { Text("Wiederherstellen") }
    }
}
