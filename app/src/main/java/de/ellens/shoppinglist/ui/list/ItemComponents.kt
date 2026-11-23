@file:OptIn(ExperimentalMaterial3Api::class)

package de.ellens.shoppinglist.ui.list

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.ellens.shoppinglist.core.SuggestionFilter
import de.ellens.shoppinglist.data.db.ShoppingItemEntity
import de.ellens.shoppinglist.data.db.ShoppingListEntity
import de.ellens.shoppinglist.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch

/** Test-Tags für automatisierte UI-Tests. */
object ListTags {
    const val NAME_FIELD = "input_name"
    const val QUANTITY_FIELD = "input_quantity"
    const val ADD_BUTTON = "input_add"
    const val KEEP_SCREEN_ON = "keep_screen_on"
    fun item(id: Long) = "item_$id"
    fun edit(id: Long) = "edit_$id"
}

// ------------------------------------------------------------------ Bildschirm-Dauerlicht

/** Großer, mittiger Schalter. Eingeschaltet deutlich dunkel/hell invertiert hervorgehoben. */
@Composable
fun KeepScreenOnToggle(enabled: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val extra = LocalExtraColors.current
    val container = if (enabled) extra.keepScreenOnActive else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (enabled) extra.onKeepScreenOnActive else MaterialTheme.colorScheme.onSurface
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = container,
            contentColor = content,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .widthIn(min = 260.dp)
                .clip(RoundedCornerShape(28.dp))
                .testTag(ListTags.KEEP_SCREEN_ON)
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange),
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text("Bildschirm bleibt an", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (enabled) "EIN – Display schaltet nicht ab" else "AUS – normale Abschaltung",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                // Der Schalter selbst ist nur Anzeige; bedient wird die ganze Fläche.
                Switch(checked = enabled, onCheckedChange = null)
            }
        }
    }
}

// ------------------------------------------------------------------ Artikelzeile

@Composable
fun SwipeToDeleteItem(
    item: ShoppingItemEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val currentOnDelete by rememberUpdatedState(onDelete)
    // Bewusst remember statt rememberSaveable: ein per Rückgängig wiederhergestellter Artikel
    // (gleiche ID) muss wieder im Ruhezustand erscheinen.
    val state = remember(item.id) {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            density = density,
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    currentOnDelete()
                    true
                } else {
                    false
                }
            },
            positionalThreshold = { total -> total * 0.4f },
        )
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        // Nur von rechts nach links wischen löscht (versehentliches Löschen vermeiden).
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = { DeleteBackground(state) },
    ) {
        content()
    }
}

@Composable
private fun DeleteBackground(state: SwipeToDismissBoxState) {
    val direction = state.dismissDirection
    val alignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (direction == SwipeToDismissBoxValue.Settled) Color.Transparent else MaterialTheme.colorScheme.errorContainer
    Box(
        Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(Icons.Filled.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/**
 * Kompakte Artikelzeile. [containerColor] färbt die Zeile (Listenfarbe); [listLabel] zeigt in der
 * Gesamtansicht zusätzlich den Listennamen (Farbe allein wäre nicht barrierefrei).
 * Antippen der Text-Fläche schaltet erledigt/offen um; Stift öffnet die
 * Bearbeitung; der Griff links dient ausschließlich dem Verschieben.
 */
@Composable
fun ItemRow(
    item: ShoppingItemEntity,
    isDragging: Boolean,
    dragHandleModifier: Modifier?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    showLeadingSlot: Boolean = true,
    listLabel: String? = null,
) {
    val extra = LocalExtraColors.current
    val scheme = MaterialTheme.colorScheme
    val textColor by animateColorAsState(if (item.isDone) extra.doneRed else scheme.onSurface, label = "itemColor")
    val quantityColor by animateColorAsState(
        if (item.isDone) extra.doneRed.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
        label = "quantityColor",
    )
    val decoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                when {
                    isDragging -> scheme.surfaceContainerHighest
                    containerColor != Color.Unspecified -> containerColor
                    else -> scheme.surface
                }
            )
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!showLeadingSlot) {
            Spacer(Modifier.width(12.dp))
        } else if (dragHandleModifier != null) {
            Box(
                modifier = dragHandleModifier
                    .size(44.dp)
                    .semantics { contentDescription = "Verschieben: ${item.name}" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Menu, contentDescription = null, tint = scheme.outline, modifier = Modifier.size(20.dp))
            }
        } else {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = extra.doneRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag(ListTags.item(item.id))
                .clickable(
                    onClickLabel = if (item.isDone) "Wieder aktivieren" else "Als erledigt markieren",
                    onClick = onToggle,
                )
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = textColor,
                    textDecoration = decoration,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (listLabel != null) {
                    Text(
                        text = listLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.quantity.isNotBlank()) {
                Text(
                    text = item.quantity,
                    color = quantityColor,
                    textDecoration = decoration,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).widthIn(max = 140.dp),
                )
            }
        }
        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(44.dp).testTag(ListTags.edit(item.id)),
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Bearbeiten: ${item.name}",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun DoneHeader(count: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "Erledigt ($count)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
        )
    }
}

// ------------------------------------------------------------------ Eingabebereich

@Composable
fun SuggestionPanel(
    suggestions: List<SuggestionFilter.Candidate>,
    onPick: (SuggestionFilter.Candidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn(Modifier.heightIn(max = 200.dp)) {
            items(suggestions, key = { it.id }) { s ->
                Text(
                    text = s.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(s) }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * Eingabezeile: Artikelname, optionale Menge, Hinzufügen-Knopf.
 * Bei sehr großer Schrift werden die Felder untereinander angeordnet, damit nichts abgeschnitten wird.
 */
@Composable
fun ItemInputBar(
    name: TextFieldValue,
    quantity: String,
    onNameChange: (TextFieldValue) -> Unit,
    onQuantityChange: (String) -> Unit,
    onSubmit: () -> Unit,
    nameFocus: FocusRequester,
    quantityFocus: FocusRequester,
    onNameFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stacked = LocalDensity.current.fontScale >= 1.6f
    val canSubmit = name.text.isNotBlank()

    val nameField = @Composable { m: Modifier ->
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("Artikel") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = m
                .focusRequester(nameFocus)
                .onFocusChanged { onNameFocusChanged(it.isFocused) }
                .testTag(ListTags.NAME_FIELD),
        )
    }
    val quantityField = @Composable { m: Modifier ->
        OutlinedTextField(
            value = quantity,
            onValueChange = onQuantityChange,
            placeholder = { Text("Menge", maxLines = 1) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = m.focusRequester(quantityFocus).testTag(ListTags.QUANTITY_FIELD),
        )
    }
    val addButton = @Composable {
        FilledIconButton(
            onClick = onSubmit,
            enabled = canSubmit,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(52.dp).testTag(ListTags.ADD_BUTTON),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Hinzufügen")
        }
    }

    if (stacked) {
        Column(modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            nameField(Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quantityField(Modifier.weight(1f))
                addButton()
            }
        }
    } else {
        Row(
            modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            nameField(Modifier.weight(0.63f))
            quantityField(Modifier.weight(0.37f))
            addButton()
        }
    }
}

// ------------------------------------------------------------------ Untere Listenauswahl

@Composable
fun ListSwitcherBar(
    lists: List<ShoppingListEntity>,
    currentId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    val selectedIndex = lists.indexOfFirst { it.id == currentId }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) state.animateScrollToItem(selectedIndex)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(lists, key = { _, l -> l.id }) { _, l ->
                FilterChip(
                    selected = l.id == currentId,
                    onClick = { onSelect(l.id) },
                    label = { Text(l.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Bearbeiten-Dialog

@Composable
fun EditItemDialog(
    item: ShoppingItemEntity,
    onSave: suspend (name: String, quantity: String) -> String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(item.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(item.name, TextRange(item.name.length)))
    }
    var quantity by rememberSaveable(item.id) { mutableStateOf(item.quantity) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val save: () -> Unit = {
        scope.launch {
            val result = onSave(name.text, quantity)
            if (result == null) onDismiss() else error = result
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Artikel bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Artikel") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { msg -> @Composable { Text(msg) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Menge (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.text.isNotBlank()) save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = save, enabled = name.text.isNotBlank()) { Text("Speichern") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
fun EmptyListHint(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "Die Liste ist leer.\nUnten einen Artikel eingeben.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun HorizontalGap(width: Int) = Spacer(Modifier.width(width.dp))

/** Auswahl, in welche Liste ein neuer Artikel in der Gesamtansicht kommt. */
@Composable
fun TargetListSelector(
    lists: List<ShoppingListEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Neu in:",
) {
    val extra = LocalExtraColors.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(lists, key = { it.id }) { l ->
                val selected = l.id == selectedId
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(l.id) },
                    label = { Text(l.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp)) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = extra.listTint(l.colorIndex, scheme.surface),
                        selectedContainerColor = extra.listTint(l.colorIndex, scheme.secondaryContainer),
                        selectedLabelColor = scheme.onSurface,
                        selectedLeadingIconColor = scheme.onSurface,
                    ),
                    border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
                )
            }
        }
    }
}
