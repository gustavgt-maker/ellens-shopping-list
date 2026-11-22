package de.ellens.shoppinglist.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.ellens.shoppinglist.core.ListColors
import de.ellens.shoppinglist.ui.theme.LocalExtraColors

/** Dialog zum Anlegen oder Bearbeiten einer Liste: Name und dezente Hintergrundfarbe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListEditDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    confirmLabel: String,
    onConfirm: (name: String, colorIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }
    var color by rememberSaveable { mutableIntStateOf(initialColor) }
    val focus = remember { FocusRequester() }
    val valid = name.text.isNotBlank()
    val extra = LocalExtraColors.current
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name.text, color) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Text("Hintergrundfarbe", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (index in 0 until ListColors.COUNT) {
                        val selected = index == color
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(extra.listTint(index, scheme.surface))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) scheme.primary else scheme.outline,
                                    shape = CircleShape,
                                )
                                .selectable(selected = selected, role = Role.RadioButton) { color = index }
                                .semantics { contentDescription = "Farbe ${ListColors.names[index]}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onSurface)
                                index == 0 -> Icon(Icons.Filled.Close, contentDescription = null, tint = scheme.outline, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Text(
                    ListColors.names.getOrElse(color) { "" },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.text, color) }, enabled = valid) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}
