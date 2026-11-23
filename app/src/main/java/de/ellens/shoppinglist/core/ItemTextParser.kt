package de.ellens.shoppinglist.core

/**
 * Erkennt Artikel und Mengen in frei geschriebenem Text, z. B. aus einer WhatsApp- oder
 * iMessage-Nachricht:
 *
 *     Milch 2, Brot
 *     - 500 g Mehl
 *     2x Butter
 *     Äpfel (1 kg)
 *
 * Trennzeichen: Zeilenumbruch, Komma, Semikolon, Aufzählungszeichen. Ein Komma zwischen zwei
 * Ziffern („3,5 %“) trennt nicht. „und“ trennt bewusst nicht („Salz und Pfeffer“).
 * Das Ergebnis ist ein Vorschlag – die App zeigt ihn vor dem Übernehmen zur Kontrolle an.
 */
object ItemTextParser {

    data class ParsedItem(val name: String, val quantity: String)

    /** Einheiten, die als Teil einer Mengenangabe erkannt werden (klein geschrieben, ohne Punkt). */
    private val units = listOf(
        "kg", "g", "gr", "mg", "l", "ltr", "liter", "ml", "cl", "dl",
        "stk", "stück", "st", "pck", "pckg", "pkg", "packung", "packungen", "pack", "päckchen",
        "dose", "dosen", "flasche", "flaschen", "fl", "glas", "gläser", "becher", "bund",
        "beutel", "tüte", "tüten", "tafel", "tafeln", "kiste", "kisten", "kasten", "rolle", "rollen",
        "netz", "schale", "schalen", "scheibe", "scheiben", "kopf", "köpfe", "paar", "karton", "kartons",
    )

    private const val NUM = """\d+(?:[.,]\d+)?(?:\s*[-–/]\s*\d+(?:[.,]\d+)?)?|½|¼|¾|ein(?:e|en)?|zwei|drei|vier|fünf|sechs"""
    private val unitAlt = units.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    /** Menge vorne: „2 Milch“, „2x Milch“, „500 g Mehl“, „500g Mehl“, „2 Packungen Milch“. */
    private val leading = Regex(
        """^(?<q>(?:$NUM)\s*(?:x|×|mal)?\s*(?:(?:$unitAlt)\.?(?=\s|$))?)\s+(?<n>.+)$""",
        RegexOption.IGNORE_CASE,
    )

    /** Menge in Klammern hinten: „Äpfel (1 kg)“. */
    private val trailingParen = Regex("""^(?<n>.+?)\s*\((?<q>[^()]{1,40})\)$""")

    /** Menge hinten nach Trenner: „Milch: 2“, „Milch - 2 Packungen“, „Milch x2“, „Milch 2x“. */
    private val trailing = Regex(
        """^(?<n>.+?)(?:\s*[:–-]\s*|\s+)(?<q>(?:x|×)\s*\d+|(?:$NUM)\s*(?:x|×|mal)?\s*(?:(?:$unitAlt)\.?)?)$""",
        RegexOption.IGNORE_CASE,
    )

    /** Zeitstempel/Absender kopierter WhatsApp-Nachrichten: „[23.11.26, 11:20] Anna: …“. */
    private val whatsappPrefix = Regex("""^\[?\d{1,2}\.\d{1,2}\.\d{2,4},?\s+\d{1,2}:\d{2}(?::\d{2})?\]?\s*(?:-\s*)?[^:]{1,40}:\s*""")

    /** Aufzählungszeichen, Nummerierung, Checkboxen am Zeilenanfang. */
    private val bullet = Regex("""^(?:[-*•·–—+>]|\d{1,2}[.)]|\[\s?[xX]?\s?]|[☐☑✅✔️✓▢□○●◦])+\s*""")

    /** Trennt an Zeilenumbruch, Semikolon und Komma (aber nicht zwischen Ziffern). */
    private val separators = Regex("""\r?\n|;|,(?!\d)|(?<!\d),""")

    fun parse(text: String, maxItems: Int = 200): List<ParsedItem> {
        val result = ArrayList<ParsedItem>()
        val seen = HashSet<String>()
        for (rawLine in text.lines()) {
            val line = whatsappPrefix.replace(rawLine.trim(), "")
            for (part in line.split(separators)) {
                val item = parsePart(part) ?: continue
                val key = NameNormalizer.normalize(item.name)
                if (seen.add(key)) result += item
                if (result.size >= maxItems) return result
            }
        }
        return result
    }

    internal fun parsePart(raw: String): ParsedItem? {
        var s = NameNormalizer.clean(raw)
        s = bullet.replace(s, "")
        s = s.trim(' ', '.', ':', '!', '?')
        if (s.isEmpty() || s.none { it.isLetter() }) return null

        trailingParen.matchEntire(s)?.let { m ->
            val name = m.groups["n"]!!.value
            if (name.any { it.isLetter() }) return item(name, m.groups["q"]!!.value)
        }
        leading.matchEntire(s)?.let { m ->
            val name = m.groups["n"]!!.value
            // „1 kg“ allein ist kein Artikel; der Name muss Buchstaben enthalten.
            if (name.any { it.isLetter() } && !isOnlyUnit(name)) return item(name, m.groups["q"]!!.value)
        }
        trailing.matchEntire(s)?.let { m ->
            val name = m.groups["n"]!!.value
            if (name.any { it.isLetter() } && !isOnlyUnit(name)) return item(name, m.groups["q"]!!.value)
        }
        return item(s, "")
    }

    private fun isOnlyUnit(text: String) = units.contains(text.trim().trimEnd('.').lowercase())

    private fun item(name: String, quantity: String): ParsedItem? {
        val n = NameNormalizer.clean(name).trim(' ', '-', ':', '–').take(NameNormalizer.MAX_NAME_LENGTH)
        if (n.isEmpty()) return null
        val q = NameNormalizer.cleanQuantity(quantity.replace(Regex("""^(?:x|×)\s*(\d)""", RegexOption.IGNORE_CASE), "$1"))
        // „milch“ -> „Milch“; Schreibweisen wie „iPhone-Kabel“ bleiben unverändert.
        val capitalized = if (n.length > 1 && n[0].isLowerCase() && !n[1].isUpperCase()) n.replaceFirstChar { it.uppercaseChar() } else n
        return ParsedItem(capitalized, q)
    }
}
