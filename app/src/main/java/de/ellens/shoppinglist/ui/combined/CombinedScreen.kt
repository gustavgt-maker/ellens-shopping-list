@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.ellens.shoppinglist.ui.combined

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ellens.shoppinglist.core.CombinedOrdering
import de.ellens.shoppinglist.data.db.ShoppingListEntity
import de.ellens.shoppinglist.ui.common.Haptics
import de.ellens.shoppinglist.ui.common.dismissKeyboardOnTouch
import de.ellens.shoppinglist.ui.common.rememberHaptics
import de.ellens.shoppinglist.ui.list.DoneHeader
import de.ellens.shoppinglist.ui.list.EditItemDialog
import de.ellens.shoppinglist.ui.list.ItemInputBar
import de.ellens.shoppinglist.ui.list.ItemRow
import de.ellens.shoppinglist.ui.list.KeepScreenOnToggle
import de.ellens.shoppinglist.ui.list.ListEvent
import de.ellens.shoppinglist.ui.list.SuggestionPanel
import de.ellens.shoppinglist.ui.list.SwipeToDeleteItem
import de.ellens.shoppinglist.ui.list.TargetListSelector
import de.ellens.shoppinglist.ui.theme.LocalExtraColors
import de.ellens.shoppinglist.ui.theme.LocalReduceMotion
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

object CombinedTags {
    fun block(key: String) = "block_$key"
}

@Composable
fun CombinedScreen(
    viewModel: CombinedViewModel,
    keepScreenOn: Boolean,
    onBack: () -> Unit,
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val targetId by viewModel.targetListId.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var nameFocused by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val quantityFocus = remember { FocusRequester() }
    var editItemId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                when (event) {
                    is ListEvent.Message -> snackbar.showSnackbar(event.text, duration = SnackbarDuration.Short)
                    is ListEvent.ItemDeleted -> {
                        val result = snackbar.showSnackbar(
                            message = "„${event.item.name}“ gelöscht",
                            actionLabel = "Rückgängig",
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.item)
                    }
                }
            }
        }
    }

    if (screenState is CombinedScreenState.Missing) {
        LaunchedEffect(Unit) { onBack() }
    }
    val ready = (screenState as? CombinedScreenState.Ready)?.state

    val submit: () -> Unit = {
        if (name.text.isNotBlank()) {
            val n = name.text
            val q = quantity
            scope.launch {
                if (viewModel.addItem(n, q) != null) {
                    haptics.tick()
                    name = TextFieldValue("")
                    quantity = ""
                    runCatching { nameFocus.requestFocus() }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    KeepScreenOnToggle(enabled = keepScreenOn, onChange = { haptics.tick(); dismissKeyboard(); viewModel.setKeepScreenOn(it) })
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { dismissKeyboard(); onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zur Listenübersicht")
                        }
                        Column(Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                "Gemeinsam einkaufen",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                ready?.lists?.joinToString(" + ") { it.name } ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (nameFocused && suggestions.isNotEmpty()) {
                        SuggestionPanel(
                            suggestions = suggestions,
                            onPick = { s ->
                                name = TextFieldValue(s.name, TextRange(s.name.length))
                                viewModel.onNameInputChanged(s.name)
                                runCatching { quantityFocus.requestFocus() }
                            },
                        )
                    }
                    if (ready != null) {
                        TargetListSelector(lists = ready.lists, selectedId = targetId, onSelect = viewModel::setTarget)
                    }
                    ItemInputBar(
                        name = name,
                        quantity = quantity,
                        onNameChange = { name = it; viewModel.onNameInputChanged(it.text) },
                        onQuantityChange = { quantity = it },
                        onSubmit = submit,
                        nameFocus = nameFocus,
                        quantityFocus = quantityFocus,
                        onNameFocusChanged = { nameFocused = it },
                    )
                }
            }
        },
    ) { padding ->
        if (ready == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else {
            CombinedContent(
                state = ready,
                padding = padding,
                haptics = haptics,
                onToggle = { id -> haptics.tick(); dismissKeyboard(); viewModel.toggleDone(id) },
                onEdit = { id -> dismissKeyboard(); editItemId = id },
                onDelete = { id -> haptics.strong(); dismissKeyboard(); viewModel.deleteItem(id) },
                onReorder = viewModel::setBlockOrder,
                onUserInteraction = dismissKeyboard,
            )
        }
    }

    editItemId?.let { id ->
        val item = ready?.let { st -> (st.blocks.flatMap { it.items } + st.done).firstOrNull { it.id == id } }
        if (item != null) {
            EditItemDialog(
                item = item,
                onSave = { n, q -> viewModel.editItem(id, n, q) },
                onDelete = {
                    editItemId = null
                    haptics.strong()
                    viewModel.deleteItem(id)
                },
                onDismiss = { editItemId = null },
            )
        } else if (ready != null) {
            LaunchedEffect(id) { editItemId = null }
        }
    }
}

@Composable
private fun CombinedContent(
    state: CombinedState,
    padding: PaddingValues,
    haptics: Haptics,
    onToggle: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (List<String>) -> Unit,
    onUserInteraction: () -> Unit,
) {
    val extra = LocalExtraColors.current
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val latestOnUserInteraction by rememberUpdatedState(onUserInteraction)
    val listState = rememberLazyListState()

    fun tintOf(list: ShoppingListEntity?) = extra.listTint(list?.colorIndex ?: 0, scheme.surface)
    fun labelOf(listId: Long) = state.listsById[listId]?.name ?: ""

    // Lokale Block-Reihenfolge während Drag & Drop; wird beim Loslassen übernommen.
    val freshKeys = state.blocks.map { it.key }
    var localKeys by remember { mutableStateOf(freshKeys) }
    var useLocal by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(freshKeys) { if (!dragging) useLocal = false }
    val blocksByKey = state.blocks.associateBy { it.key }
    // Lokale Reihenfolge immer mit dem aktuellen Stand zusammenführen: jeder Block genau einmal.
    val displayedBlocks = if (useLocal) {
        (localKeys.filter { it in blocksByKey } + freshKeys.filter { it !in localKeys }).mapNotNull { blocksByKey[it] }
    } else {
        state.blocks
    }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (!useLocal) {
            localKeys = displayedBlocks.map { it.key }
            useLocal = true
        }
        // Nur Blöcke untereinander tauschen (nicht mit Überschrift oder erledigten Artikeln).
        val fromRaw = from.key as? String
        val toRaw = to.key as? String
        if (fromRaw != null && toRaw != null && fromRaw.startsWith("b:") && toRaw.startsWith("b:") && fromRaw != toRaw) {
            val fromKey = fromRaw.removePrefix("b:")
            val toIndex = localKeys.indexOf(toRaw.removePrefix("b:"))
            if (toIndex >= 0 && fromKey in localKeys) {
                localKeys = CombinedOrdering.move(localKeys, fromKey, toIndex)
                haptics.tick()
            }
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .dismissKeyboardOnTouch { latestOnUserInteraction() },
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            end = padding.calculateEndPadding(layoutDirection),
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 8.dp,
        ),
    ) {
        if (displayedBlocks.isEmpty() && state.done.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Keine Artikel in den ausgewählten Listen.",
                        textAlign = TextAlign.Center,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(displayedBlocks, key = { "b:" + it.key }) { block ->
            ReorderableItem(reorderState, key = "b:" + block.key) { isDragging ->
                val handle = Modifier.draggableHandle(
                    onDragStarted = {
                        latestOnUserInteraction()
                        if (!useLocal) {
                            localKeys = displayedBlocks.map { it.key }
                            useLocal = true
                        }
                        dragging = true
                        haptics.strong()
                    },
                    onDragStopped = {
                        dragging = false
                        onReorder(localKeys)
                    },
                )
                // Ein Block = alle gleichnamigen Artikel; verschoben wird immer der ganze Block.
                Column {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(
                            modifier = handle
                                .width(44.dp)
                                .fillMaxHeight()
                                .background(if (isDragging) scheme.surfaceContainerHighest else scheme.surface)
                                .semantics { contentDescription = "Verschieben: ${block.items.firstOrNull()?.name ?: ""}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = null, tint = scheme.outline, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            block.items.forEach { item ->
                                ItemRow(
                                    item = item,
                                    isDragging = isDragging,
                                    dragHandleModifier = null,
                                    onToggle = { onToggle(item.id) },
                                    onEdit = { onEdit(item.id) },
                                    containerColor = tintOf(state.listsById[item.listId]),
                                    showLeadingSlot = false,
                                    listLabel = labelOf(item.listId),
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
        if (state.done.isNotEmpty()) {
            item(key = "done_header") {
                DoneHeader(count = state.done.size, modifier = if (reduceMotion) Modifier else Modifier.animateItem())
            }
            items(state.done, key = { it.id }) { item ->
                SwipeToDeleteItem(
                    item = item,
                    onDelete = { onDelete(item.id) },
                    modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
                ) {
                    ItemRow(
                        item = item,
                        isDragging = false,
                        dragHandleModifier = null,
                        onToggle = { onToggle(item.id) },
                        onEdit = { onEdit(item.id) },
                        containerColor = tintOf(state.listsById[item.listId]),
                        listLabel = labelOf(item.listId),
                    )
                }
            }
        }
    }
}
