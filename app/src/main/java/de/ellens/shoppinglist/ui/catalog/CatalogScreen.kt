@file:OptIn(ExperimentalMaterial3Api::class)

package de.ellens.shoppinglist.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ellens.shoppinglist.ui.common.ConfirmDestructiveDialog
import de.ellens.shoppinglist.ui.common.NameInputDialog
import kotlinx.coroutines.launch

@Composable
fun CatalogScreen(viewModel: CatalogViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var renameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artikeldatenbank") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addError = null; showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Artikel hinzufügen")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                placeholder = { Text("Artikel suchen") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.query.value = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Suche löschen")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                "Änderungen hier betreffen nur die Vorschläge – vorhandene Listeneinträge bleiben unverändert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            val list = items
            when {
                list == null -> Box(Modifier.fillMaxSize())
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "Noch keine Artikel gespeichert." else "Keine Treffer.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(list, key = { it.id }) { entry ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(entry.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { renameError = null; renameId = entry.id }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Umbenennen: ${entry.name}")
                            }
                            IconButton(onClick = { deleteId = entry.id }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Löschen: ${entry.name}",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (showAdd) {
        NameInputDialog(
            title = "Artikel hinzufügen",
            label = "Artikelname",
            confirmLabel = "Hinzufügen",
            errorText = addError,
            onConfirm = { name ->
                scope.launch {
                    val err = viewModel.add(name)
                    if (err == null) showAdd = false else addError = err
                }
            },
            onDismiss = { showAdd = false },
        )
    }
    renameId?.let { id ->
        val entry = items?.firstOrNull { it.id == id }
        if (entry != null) {
            NameInputDialog(
                title = "Artikel umbenennen",
                initial = entry.name,
                errorText = renameError,
                onConfirm = { name ->
                    scope.launch {
                        val err = viewModel.rename(id, name)
                        if (err == null) renameId = null else renameError = err
                    }
                },
                onDismiss = { renameId = null },
            )
        }
    }
    deleteId?.let { id ->
        val entry = items?.firstOrNull { it.id == id }
        if (entry != null) {
            ConfirmDestructiveDialog(
                title = "Vorschlag löschen?",
                message = "„${entry.name}“ wird aus der Artikeldatenbank entfernt. Einträge auf deinen Listen bleiben erhalten.",
                confirmLabel = "Löschen",
                onConfirm = {
                    deleteId = null
                    scope.launch { viewModel.delete(id); snackbar.showSnackbar("„${entry.name}“ entfernt") }
                },
                onDismiss = { deleteId = null },
            )
        }
    }
}
