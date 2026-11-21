@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.ellens.shoppinglist.ui.list

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ellens.shoppinglist.core.ItemOrdering
import de.ellens.shoppinglist.data.db.ShoppingItemEntity
import de.ellens.shoppinglist.data.db.ShoppingListEntity
import de.ellens.shoppinglist.ui.common.ConfirmDestructiveDialog
import de.ellens.shoppinglist.ui.common.Haptics
import de.ellens.shoppinglist.ui.common.NameInputDialog
import de.ellens.shoppinglist.ui.common.rememberHaptics
import de.ellens.shoppinglist.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

@Composable
fun ListScreen(
    viewModel: ListViewModel,
    keepScreenOn: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val lists by viewModel.allLists.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val currentId by viewModel.currentListId.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var nameFocused by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val quantityFocus = remember { FocusRequester() }
    var pendingScrollToId by remember { mutableStateOf<Long?>(null) }

    var editItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showClear by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }

    // Ereignisse aus dem ViewModel: Hinweise und Rückgängig nach Löschen.
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

    if (screenState is ListScreenState.Missing) {
        LaunchedEffect(Unit) { onBack() }
    }

    val submit: () -> Unit = {
        if (name.text.isNotBlank()) {
            val n = name.text
            val q = quantity
            scope.launch {
                val newId = viewModel.addItem(n, q)
                if (newId != null) {
                    haptics.tick()
                    name = TextFieldValue("")
                    quantity = ""
                    pendingScrollToId = newId
                    // Direkt weiter tippen: Fokus zurück ins Artikelfeld, Tastatur bleibt offen.
                    runCatching { nameFocus.requestFocus() }
                }
            }
        }
    }

    val imeVisible = WindowInsets.isImeVisible
    val currentList = (screenState as? ListScreenState.Ready)?.content?.list

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                KeepScreenOnToggle(enabled = keepScreenOn, onChange = { haptics.tick(); viewModel.setKeepScreenOn(it) })
                ListHeader(
                    title = currentList?.name ?: "",
                    lists = lists,
                    currentId = currentId,
                    onSwitch = { id -> haptics.tick(); viewModel.switchTo(id) },
                    onBack = onBack,
                    onRename = { showRename = true },
                    onClear = { showClear = true },
                    onDelete = { showDelete = true },
                    onOpenSettings = onOpenSettings,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    if (!imeVisible && lists.size > 1) {
                        ListSwitcherBar(
                            lists = lists,
                            currentId = currentId,
                            onSelect = { id -> haptics.tick(); viewModel.switchTo(id) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (val st = screenState) {
            ListScreenState.Loading, ListScreenState.Missing -> Box(Modifier.fillMaxSize().padding(padding))
            is ListScreenState.Ready -> {
                val reduceMotion = LocalReduceMotion.current
                AnimatedContent(
                    targetState = st.content,
                    contentKey = { it.list.id },
                    transitionSpec = {
                        listTransition(
                            from = lists.indexOfFirst { it.id == initialState.list.id },
                            to = lists.indexOfFirst { it.id == targetState.list.id },
                            reduceMotion = reduceMotion,
                        )
                    },
                    label = "listSwitch",
                ) { content ->
                    ListContent(
                        content = content,
                        padding = padding,
                        haptics = haptics,
                        pendingScrollToId = pendingScrollToId,
                        onScrollHandled = { pendingScrollToId = null },
                        onToggle = { id -> haptics.tick(); viewModel.toggleDone(id) },
                        onEdit = { id -> editItemId = id },
                        onDelete = { id -> haptics.strong(); viewModel.deleteItem(id) },
                        onMove = { id, index -> viewModel.moveOpenItem(id, index) },
                        onSaveScroll = viewModel::saveScroll,
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------- Dialoge
    val ready = screenState as? ListScreenState.Ready
    editItemId?.let { id ->
        val item = ready?.content?.let { c -> (c.open + c.done).firstOrNull { it.id == id } }
        if (item != null) {
            EditItemDialog(
                item = item,
                onSave = { n, q -> viewModel.editItem(id, n, q) },
                onDismiss = { editItemId = null },
            )
        } else if (ready != null) {
            LaunchedEffect(id) { editItemId = null }
        }
    }
    if (showRename && currentList != null) {
        NameInputDialog(
            title = "Liste umbenennen",
            initial = currentList.name,
            onConfirm = { n -> viewModel.renameCurrentList(n); showRename = false },
            onDismiss = { showRename = false },
        )
    }
    if (showClear && currentList != null) {
        ConfirmDestructiveDialog(
            title = "Liste leeren?",
            message = "Alle offenen und erledigten Artikel in „${currentList.name}“ werden entfernt. " +
                "Die Liste selbst und die Artikelvorschläge bleiben erhalten.",
            confirmLabel = "Liste leeren",
            onConfirm = { viewModel.clearCurrentList(); showClear = false },
            onDismiss = { showClear = false },
        )
    }
    if (showDelete && currentList != null) {
        ConfirmDestructiveDialog(
            title = "Liste löschen?",
            message = "„${currentList.name}“ wird mit allen Artikeln endgültig gelöscht. " +
                "Die Artikelvorschläge bleiben erhalten.",
            confirmLabel = "Liste löschen",
            onConfirm = {
                showDelete = false
                viewModel.deleteCurrentList(onDeleted = onBack)
            },
            onDismiss = { showDelete = false },
        )
    }
}

private fun listTransition(from: Int, to: Int, reduceMotion: Boolean): ContentTransform {
    if (reduceMotion || from < 0 || to < 0) {
        return EnterTransition.None togetherWith ExitTransition.None
    }
    val forward = to > from
    val duration = 220
    return (slideInHorizontally(tween(duration)) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(tween(duration))) togetherWith
        (slideOutHorizontally(tween(duration)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(duration / 2)))
}

/**
 * Kopfzeile mit Listennamen. Horizontales Wischen über diese Zeile wechselt zur vorherigen/
 * nächsten Liste (stabile Reihenfolge nach Anlage). Die Artikelzeilen reagieren auf
 * horizontales Wischen ausschließlich mit Löschen – die Gesten überschneiden sich nicht.
 */
@Composable
private fun ListHeader(
    title: String,
    lists: List<ShoppingListEntity>,
    currentId: Long,
    onSwitch: (Long) -> Unit,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val index = lists.indexOfFirst { it.id == currentId }
    val prev = lists.getOrNull(index - 1)
    val next = lists.getOrNull(index + 1)
    val latestPrev by rememberUpdatedState(prev)
    val latestNext by rememberUpdatedState(next)
    val latestOnSwitch by rememberUpdatedState(onSwitch)
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zur Listenübersicht")
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .pointerInput(Unit) {
                    val threshold = 56.dp.toPx()
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total <= -threshold) latestNext?.let { latestOnSwitch(it.id) }
                            else if (total >= threshold) latestPrev?.let { latestOnSwitch(it.id) }
                            total = 0f
                        },
                        onDragCancel = { total = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                    }
                }
                .semantics { contentDescription = "Liste $title. Zum Wechseln nach links oder rechts wischen." },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (prev != null) "‹" else " ",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = if (next != null) "›" else " ",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Listenoptionen")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Liste umbenennen") }, onClick = { menuOpen = false; onRename() })
                DropdownMenuItem(
                    text = { Text("Liste leeren", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onClear() },
                )
                DropdownMenuItem(
                    text = { Text("Liste löschen", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Einstellungen") }, onClick = { menuOpen = false; onOpenSettings() })
            }
        }
    }
}

@Composable
private fun ListContent(
    content: ListContentState,
    padding: PaddingValues,
    haptics: Haptics,
    pendingScrollToId: Long?,
    onScrollHandled: () -> Unit,
    onToggle: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (itemId: Long, toIndex: Int) -> Unit,
    onSaveScroll: (listId: Long, index: Int, offset: Int) -> Unit,
) {
    val listId = content.list.id
    val reduceMotion = LocalReduceMotion.current

    // Scrollposition: beim ersten Öffnen aus der Datenbank, danach über den Instanzzustand.
    val listState = rememberSaveable(listId, saver = LazyListState.Saver) {
        LazyListState(content.list.scrollIndex, content.list.scrollOffset)
    }
    LaunchedEffect(listState, listId) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (i, o) ->
                delay(400)
                onSaveScroll(listId, i, o)
            }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        onSaveScroll(listId, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
    }
    DisposableEffect(listId) {
        onDispose { onSaveScroll(listId, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }

    // Während Drag & Drop wird eine lokale Reihenfolge angezeigt (flüssig, ohne Datenbankzugriff
    // pro Schritt). Gespeichert wird beim Loslassen. Die lokale Reihenfolge wird immer mit dem
    // aktuellen Datenbankstand zusammengeführt, damit kein Artikel doppelt erscheinen kann.
    var openLocal by remember(listId) { mutableStateOf(content.open) }
    var useLocal by remember(listId) { mutableStateOf(false) }
    var draggingId by remember(listId) { mutableStateOf<Long?>(null) }
    LaunchedEffect(content.open) {
        if (draggingId == null) useLocal = false
    }
    val displayedOpen = remember(content.open, openLocal, useLocal) {
        if (useLocal) ItemOrdering.mergeLocalOrder(openLocal, content.open) { it.id } else content.open
    }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (!useLocal) {
            openLocal = content.open
            useLocal = true
        }
        val fromIndex = openLocal.indexOfFirst { it.id == from.key }
        val toIndex = openLocal.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
            openLocal = openLocal.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.tick()
        }
    }

    // Neu hinzugefügten Artikel sichtbar machen, falls er außerhalb des Bildschirms liegt.
    LaunchedEffect(pendingScrollToId, displayedOpen) {
        val id = pendingScrollToId ?: return@LaunchedEffect
        val index = displayedOpen.indexOfFirst { it.id == id }
        if (index < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.any { info ->
            info.key == id && info.offset >= 0 &&
                info.offset + info.size <= listState.layoutInfo.viewportEndOffset - listState.layoutInfo.afterContentPadding
        }
        if (!fullyVisible) {
            val target = (index - (visible.size - 2).coerceAtLeast(0)).coerceAtLeast(0)
            if (reduceMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
        onScrollHandled()
    }

    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            end = padding.calculateEndPadding(layoutDirection),
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 8.dp,
        ),
    ) {
        if (displayedOpen.isEmpty() && content.done.isEmpty()) {
            item(key = "empty") { EmptyListHint() }
        }
        items(displayedOpen, key = { it.id }) { item ->
            ReorderableItem(reorderState, key = item.id) { isDragging ->
                val handle = Modifier.draggableHandle(
                    onDragStarted = {
                        if (!useLocal) {
                            openLocal = displayedOpen
                            useLocal = true
                        }
                        draggingId = item.id
                        haptics.strong()
                    },
                    onDragStopped = {
                        val index = openLocal.indexOfFirst { it.id == item.id }
                        draggingId = null
                        if (index >= 0) onMove(item.id, index)
                    },
                )
                SwipeToDeleteItem(item = item, onDelete = { onDelete(item.id) }) {
                    ItemRow(
                        item = item,
                        isDragging = isDragging,
                        dragHandleModifier = handle,
                        onToggle = { onToggle(item.id) },
                        onEdit = { onEdit(item.id) },
                    )
                }
            }
        }
        if (content.done.isNotEmpty()) {
            item(key = "done_header") {
                DoneHeader(
                    count = content.done.size,
                    modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
                )
            }
            items(content.done, key = { it.id }) { item ->
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
                    )
                }
            }
        }
    }
}

/** Nur für Vorschauen/Tests: alle Artikel einer Liste. */
internal fun ListContentState.allItems(): List<ShoppingItemEntity> = open + done
