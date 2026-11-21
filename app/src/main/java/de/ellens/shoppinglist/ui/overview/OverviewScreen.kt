package de.ellens.shoppinglist.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ellens.shoppinglist.data.db.ListWithCounts
import de.ellens.shoppinglist.ui.common.ConfirmDestructiveDialog
import de.ellens.shoppinglist.ui.common.NameInputDialog
import de.ellens.shoppinglist.ui.common.rememberHaptics
import de.ellens.shoppinglist.ui.list.KeepScreenOnToggle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    keepScreenOn: Boolean,
    onOpenList: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Einkaufslisten", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                        }
                    },
                )
                KeepScreenOnToggle(enabled = keepScreenOn, onChange = { haptics.tick(); viewModel.setKeepScreenOn(it) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Neue Liste") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        val current = lists
        when {
            current == null -> Box(Modifier.fillMaxSize().padding(padding))
            current.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Noch keine Einkaufsliste vorhanden.\nTippe auf „Neue Liste“.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(current, key = { it.list.id }) { entry ->
                    OverviewRow(
                        entry = entry,
                        onClick = { onOpenList(entry.list.id) },
                        onRename = { renameId = entry.list.id },
                        onDelete = { deleteId = entry.list.id },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (showCreate) {
        NameInputDialog(
            title = "Neue Einkaufsliste",
            confirmLabel = "Anlegen",
            onConfirm = { name ->
                showCreate = false
                viewModel.createList(name) { id -> onOpenList(id) }
            },
            onDismiss = { showCreate = false },
        )
    }
    renameId?.let { id ->
        val entry = lists?.firstOrNull { it.list.id == id }
        if (entry == null) renameId = null else NameInputDialog(
            title = "Liste umbenennen",
            initial = entry.list.name,
            onConfirm = { name -> viewModel.renameList(id, name); renameId = null },
            onDismiss = { renameId = null },
        )
    }
    deleteId?.let { id ->
        val entry = lists?.firstOrNull { it.list.id == id }
        if (entry == null) deleteId = null else ConfirmDestructiveDialog(
            title = "Liste löschen?",
            message = "„${entry.list.name}“ wird mit allen ${entry.itemCount} Artikeln endgültig gelöscht. " +
                "Die gespeicherten Artikelvorschläge bleiben erhalten.",
            confirmLabel = "Liste löschen",
            onConfirm = { viewModel.deleteList(id); deleteId = null },
            onDismiss = { deleteId = null },
        )
    }
}

@Composable
private fun OverviewRow(
    entry: ListWithCounts,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.list.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${entry.openCount} offen · ${entry.itemCount} gesamt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Optionen für ${entry.list.name}")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Umbenennen") }, onClick = { menuOpen = false; onRename() })
                DropdownMenuItem(
                    text = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
