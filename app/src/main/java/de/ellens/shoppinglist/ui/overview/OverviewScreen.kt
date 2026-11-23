@file:OptIn(ExperimentalMaterial3Api::class)

package de.ellens.shoppinglist.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ellens.shoppinglist.data.db.ListWithCounts
import de.ellens.shoppinglist.ui.common.ConfirmDestructiveDialog
import de.ellens.shoppinglist.ui.common.ListEditDialog
import de.ellens.shoppinglist.ui.common.rememberHaptics
import de.ellens.shoppinglist.ui.list.KeepScreenOnToggle
import de.ellens.shoppinglist.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch

@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    keepScreenOn: Boolean,
    onOpenList: (Long) -> Unit,
    onOpenCombined: (List<Long>) -> Unit,
    onOpenImport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var createColor by rememberSaveable { mutableIntStateOf(-1) } // -1 = Dialog geschlossen
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCombine by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            // Undurchsichtig, damit gescrollte Einträge nicht unter der Kopfzeile durchscheinen.
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    TopAppBar(
                        title = { Text("Einkaufslisten", fontWeight = FontWeight.SemiBold) },
                        actions = {
                            if ((lists?.size ?: 0) >= 2) {
                                IconButton(onClick = { showCombine = true }) {
                                    Icon(Icons.Filled.ShoppingCart, contentDescription = "Gemeinsam einkaufen")
                                }
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                            }
                        },
                    )
                    KeepScreenOnToggle(enabled = keepScreenOn, onChange = { haptics.tick(); viewModel.setKeepScreenOn(it) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { scope.launch { createColor = viewModel.suggestColor() } },
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
                item(key = "import_hint") {
                    TextButton(
                        onClick = onOpenImport,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Artikel aus Nachricht übernehmen")
                    }
                }
                if (current.size >= 2) {
                    item(key = "combine_hint") {
                        TextButton(
                            onClick = { showCombine = true },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Mehrere Listen gemeinsam einkaufen")
                        }
                    }
                }
                items(current, key = { it.list.id }) { entry ->
                    OverviewRow(
                        entry = entry,
                        onClick = { onOpenList(entry.list.id) },
                        onEdit = { editId = entry.list.id },
                        onDelete = { deleteId = entry.list.id },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (createColor >= 0) {
        ListEditDialog(
            title = "Neue Einkaufsliste",
            initialName = "",
            initialColor = createColor,
            confirmLabel = "Anlegen",
            onConfirm = { name, color ->
                createColor = -1
                viewModel.createList(name, color) { id -> onOpenList(id) }
            },
            onDismiss = { createColor = -1 },
        )
    }
    editId?.let { id ->
        val entry = lists?.firstOrNull { it.list.id == id }
        if (entry != null) {
            ListEditDialog(
                title = "Liste bearbeiten",
                initialName = entry.list.name,
                initialColor = entry.list.colorIndex,
                confirmLabel = "Speichern",
                onConfirm = { name, color -> viewModel.updateList(id, name, color); editId = null },
                onDismiss = { editId = null },
            )
        }
    }
    deleteId?.let { id ->
        val entry = lists?.firstOrNull { it.list.id == id }
        if (entry != null) {
            ConfirmDestructiveDialog(
                title = "Liste löschen?",
                message = "„${entry.list.name}“ wird mit allen ${entry.itemCount} Artikeln endgültig gelöscht. " +
                    "Die gespeicherten Artikelvorschläge bleiben erhalten.",
                confirmLabel = "Liste löschen",
                onConfirm = { viewModel.deleteList(id); deleteId = null },
                onDismiss = { deleteId = null },
            )
        }
    }
    if (showCombine) {
        CombineDialog(
            lists = lists.orEmpty(),
            onConfirm = { ids -> showCombine = false; onOpenCombined(ids) },
            onDismiss = { showCombine = false },
        )
    }
}

/** Auswahl der Listen für „Gemeinsam einkaufen“ (wird nicht gespeichert). */
@Composable
private fun CombineDialog(
    lists: List<ListWithCounts>,
    onConfirm: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(listOf<Long>()) }
    val extra = LocalExtraColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemeinsam einkaufen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Listen auswählen. Die Artikel werden zusammen angezeigt; jeder Artikel bleibt in seiner Liste und behält deren Farbe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lists.forEach { entry ->
                    val id = entry.list.id
                    val checked = id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = checked, role = Role.Checkbox) {
                                selected = if (it) selected + id else selected - id
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        ColorDot(extra.listTint(entry.list.colorIndex, MaterialTheme.colorScheme.surface))
                        Text(
                            "${entry.list.name}  (${entry.openCount} offen)",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            // Reihenfolge der Listen wie in der Übersicht, unabhängig von der Klick-Reihenfolge.
            TextButton(
                onClick = { onConfirm(lists.map { it.list.id }.filter { it in selected }) },
                enabled = selected.size >= 2,
            ) { Text("Einkaufen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun ColorDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(18.dp)
            .background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}

@Composable
private fun OverviewRow(
    entry: ListWithCounts,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val extra = LocalExtraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(extra.listTint(entry.list.colorIndex, MaterialTheme.colorScheme.surface))
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
                DropdownMenuItem(text = { Text("Name und Farbe ändern") }, onClick = { menuOpen = false; onEdit() })
                DropdownMenuItem(
                    text = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
