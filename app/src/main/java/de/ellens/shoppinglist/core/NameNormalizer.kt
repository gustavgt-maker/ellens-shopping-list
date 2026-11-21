package de.ellens.shoppinglist.core

import java.util.Locale

/**
 * Einheitliche Aufbereitung von Artikel- und Listennamen.
 *
 * - [clean]: entfernt führende/nachfolgende Leerzeichen und fasst mehrfache Leerzeichen zusammen.
 *   So wird der Name angezeigt und gespeichert.
 * - [normalize]: zusätzlich Kleinschreibung. Grundlage für Dublettenprüfung und Suche.
 */
object NameNormalizer {
    private val whitespace = Regex("\\s+")

    /** Maximale Länge eines Namens; schützt vor versehentlich eingefügten Riesentexten. */
    const val MAX_NAME_LENGTH = 200
    const val MAX_QUANTITY_LENGTH = 60

    fun clean(raw: String): String = raw.trim().replace(whitespace, " ")

    fun normalize(raw: String): String = clean(raw).lowercase(Locale.ROOT)

    fun cleanQuantity(raw: String): String = clean(raw).take(MAX_QUANTITY_LENGTH)

    fun isValidName(raw: String): Boolean {
        val c = clean(raw)
        return c.isNotEmpty() && c.length <= MAX_NAME_LENGTH
    }
}
