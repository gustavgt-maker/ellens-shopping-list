package de.ellens.shoppinglist.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemTextParserTest {

    private fun parse(text: String) = ItemTextParser.parse(text).map { it.name + "|" + it.quantity }

    @Test fun commaSeparatedWithTrailingQuantity() =
        assertEquals(listOf("Milch|2", "Brot|"), parse("Milch 2, Brot"))

    @Test fun bulletsAndLeadingQuantities() =
        assertEquals(listOf("Mehl|500 g", "Butter|2x", "Äpfel|1 kg"), parse("- 500 g Mehl\n- 2x Butter\n* Äpfel (1 kg)"))

    @Test fun quantityFormats() {
        assertEquals(listOf("Mehl|500g"), parse("500g Mehl"))
        assertEquals(listOf("Milch|2 Packungen"), parse("2 Packungen Milch"))
        assertEquals(listOf("Milch|2"), parse("Milch: 2"))
        assertEquals(listOf("Milch|2 Packungen"), parse("Milch - 2 Packungen"))
        assertEquals(listOf("Milch|2"), parse("Milch x2"))
        assertEquals(listOf("Bananen|1-2"), parse("Bananen 1-2"))
        assertEquals(listOf("Coca Cola|1,5 l"), parse("Coca Cola 1,5 l"))
        assertEquals(listOf("Wasser|2 Kisten"), parse("2 Kisten Wasser"))
    }

    @Test fun commaInsideNumberDoesNotSplit() =
        assertEquals(listOf("Milch 3,5 %|"), parse("Milch 3,5 %"))

    @Test fun undDoesNotSplit() =
        assertEquals(listOf("Salz und Pfeffer|"), parse("Salz und Pfeffer"))

    @Test fun whatsappCopyPrefixIsRemoved() =
        assertEquals(listOf("Milch|", "Eier|10"), parse("[23.11.26, 11:20] Anna: Milch, Eier 10"))

    @Test fun numberedListsAndCheckboxes() {
        assertEquals(listOf("Brot|", "Käse|", "Wurst|"), parse("1. Brot\n2. Käse\n3. Wurst"))
        assertEquals(listOf("Tomaten|", "Gurke|"), parse("☐ Tomaten\n✅ Gurke"))
    }

    @Test fun duplicatesAndEmptyPartsAreDropped() =
        assertEquals(listOf("Milch|"), parse("milch\nMILCH\n  \n..."))

    @Test fun keepsSpecialSpellingAndNamesWithDigits() {
        assertEquals(listOf("iPhone-Kabel|"), parse("iPhone-Kabel"))
        assertEquals(listOf("7up|"), parse("7up"))
        assertEquals(listOf("Kiste Wasser|"), parse("Kiste Wasser"))
    }

    @Test fun limitsNumberOfItems() {
        val many = (1..500).joinToString("\n") { "Artikel $it a" }
        assertTrue(ItemTextParser.parse(many).size <= 200)
    }
}
