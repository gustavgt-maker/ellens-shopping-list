package de.ellens.shoppinglist.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import de.ellens.shoppinglist.data.db.ShoppingItemEntity
import de.ellens.shoppinglist.ui.list.ItemInputBar
import de.ellens.shoppinglist.ui.list.ItemRow
import de.ellens.shoppinglist.ui.list.KeepScreenOnToggle
import de.ellens.shoppinglist.ui.list.ListTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 15. Verhalten bei großen Schriftgrößen: Eingabefelder, Hinzufügen-Knopf und Bearbeiten-Stift
 * bleiben auf einem schmalen Smartphone-Bildschirm sichtbar und bedienbar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class LargeFontTest {

    @get:Rule val compose = createComposeRule()

    private val item = ShoppingItemEntity(
        id = 1, listId = 1,
        name = "Sehr langer Artikelname mit vielen Wörtern zum Testen",
        normalizedName = "x", quantity = "2 Packungen", position = 1024, createdAt = 0,
    )

    private fun setContentWithFontScale(fontScale: Float) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                MaterialTheme {
                    Column(Modifier.width(360.dp)) {
                        KeepScreenOnToggle(enabled = true, onChange = {})
                        ItemRow(item = item, isDragging = false, dragHandleModifier = Modifier, onToggle = {}, onEdit = {})
                        ItemInputBar(
                            name = TextFieldValue("Milch"),
                            quantity = "1 l",
                            onNameChange = {},
                            onQuantityChange = {},
                            onSubmit = {},
                            nameFocus = remember { FocusRequester() },
                            quantityFocus = remember { FocusRequester() },
                            onNameFocusChanged = {},
                        )
                    }
                }
            }
        }
    }

    private fun assertControlsUsable() {
        compose.onNodeWithTag(ListTags.NAME_FIELD).assertIsDisplayed()
        compose.onNodeWithTag(ListTags.QUANTITY_FIELD).assertIsDisplayed()
        compose.onNodeWithTag(ListTags.ADD_BUTTON).assertIsDisplayed().assertIsEnabled().assertHasClickAction()
        compose.onNodeWithTag(ListTags.edit(1)).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag(ListTags.item(1)).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag(ListTags.KEEP_SCREEN_ON).assertIsDisplayed()

        val screenRight = 360f
        listOf(ListTags.NAME_FIELD, ListTags.QUANTITY_FIELD, ListTags.ADD_BUTTON, ListTags.edit(1)).forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).getBoundsInRoot()
            assertTrue("$tag ragt über den Bildschirmrand", bounds.right.value <= screenRight + 0.5f)
            assertTrue("$tag hat keine Breite", (bounds.right - bounds.left).value >= 40f)
        }
    }

    @Test fun normalFont_controlsUsable() {
        setContentWithFontScale(1.0f)
        assertControlsUsable()
    }

    @Test fun largeFont_controlsUsable() {
        setContentWithFontScale(1.5f)
        assertControlsUsable()
    }

    @Test fun hugeFont_switchesToStackedInputAndStaysUsable() {
        // Android "Größte" Schrift (≈1.3–2.0) kombiniert mit App-Stufe "Riesig" (1.5)
        setContentWithFontScale(2.0f)
        assertControlsUsable()
        val name = compose.onNodeWithTag(ListTags.NAME_FIELD).getBoundsInRoot()
        val quantity = compose.onNodeWithTag(ListTags.QUANTITY_FIELD).getBoundsInRoot()
        assertTrue("Bei großer Schrift steht die Menge unter dem Artikelnamen", quantity.top >= name.bottom)
    }
}
