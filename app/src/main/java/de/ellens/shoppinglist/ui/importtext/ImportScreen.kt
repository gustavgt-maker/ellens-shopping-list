@file:OptIn(ExperimentalMaterial3Api::class)

package de.ellens.shoppinglist.ui.importtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ellens.shoppinglist.ui.list.TargetListSelector

/**
 * Artikel aus einer Nachricht übernehmen: Text einfügen oder per „Teilen“ empfangen,
 * erkannte Artikel prüfen, Ziel-Liste wählen, übernehmen. Läuft vollständig offline.
 */
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onBack: () -> Unit,
    onImported: (ImportDone) -> Unit,
) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aus Nachricht übernehmen") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
            )
        },
        bottomBar = {
            val s = state
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(12.dp)) {
                    Button(
                        onClick = { viewModel.importItems(onImported) },
                        enabled = s != null && s.selectedCount > 0 && s.targetListId > 0,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        val n = s?.selectedCount ?: 0
                        Text(if (n == 1) "1 Artikel übernehmen" else "$n Artikel übernehmen")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "input") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Nachricht einfügen, z. B. „Milch 2, Brot, 500 g Mehl“. Jede Zeile bzw. jeder durch Komma getrennte Teil wird ein Artikel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = viewModel::setText,
                        placeholder = { Text("Nachricht hier einfügen …") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            clipboard.getText()?.text?.let { pasted ->
                                viewModel.setText(if (text.isBlank()) pasted else text + "\n" + pasted)
                            }
                        }) { Text("Aus Zwischenablage einfügen") }
                        if (text.isNotEmpty()) {
                            OutlinedButton(onClick = { viewModel.setText("") }) { Text("Leeren") }
                        }
                    }
                }
            }
            val s = state
            if (s != null) {
                item(key = "target") {
                    if (s.lists.isEmpty()) {
                        Text(
                            "Es gibt noch keine Einkaufsliste. Bitte zuerst eine Liste anlegen.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        TargetListSelector(
                            lists = s.lists,
                            selectedId = s.targetListId,
                            onSelect = viewModel::setTarget,
                            label = "In Liste:",
                        )
                    }
                }
                if (s.rows.isNotEmpty()) {
                    item(key = "header") {
                        Text(
                            "Erkannte Artikel (${s.rows.size})",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                }
                items(s.rows, key = { "r:" + it.key }) { row ->
                    PreviewRow(row = row, onToggle = { viewModel.toggle(row.key) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(row: ImportRow, onToggle: () -> Unit) {
    val muted = row.alreadyOnList
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = row.selected, enabled = !muted, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.selected, onCheckedChange = null, enabled = !muted)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (muted) TextDecoration.LineThrough else TextDecoration.None,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (muted) {
                Text("schon auf der Liste", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (row.quantity.isNotBlank()) {
            Text(
                row.quantity,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp),
            )
        }
    }
}
