package de.ellens.shoppinglist.core

/**
 * Dezente Pastell-Hintergründe für Listen (wie farbige Notizblöcke). Index 0 = keine Farbe.
 * Die konkreten Farbwerte für hell/dunkel stehen im Theme; hier nur Namen und Auswahl-Logik.
 */
object ListColors {
    val names = listOf("Keine", "Gelb", "Grün", "Rosa", "Blau", "Flieder", "Pfirsich")
    const val COUNT = 7

    fun isValid(index: Int) = index in 0 until COUNT

    /** Für eine neue Liste: die am seltensten verwendete Farbe (ohne „Keine“), bei Gleichstand die erste. */
    fun suggestFor(usedColors: List<Int>): Int =
        (1 until COUNT).minBy { c -> usedColors.count { it == c } }
}
