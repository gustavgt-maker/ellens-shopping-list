package de.ellens.shoppinglist.data.settings

enum class ThemeMode(val label: String) {
    LIGHT("Hell"), DARK("Dunkel"), SYSTEM("Automatisch (System)");
}

enum class AppFont(val label: String) {
    ROBOTO("Roboto"),
    OPEN_SANS("Open Sans"),
    NOTO_SANS("Noto Sans"),
    ATKINSON("Atkinson Hyperlegible"),
    SYSTEM("Systemschrift");
}

/** Schriftgrößenstufen; wirken zusätzlich zur Android-Schriftgröße (Barrierefreiheit). */
enum class FontSizeStep(val label: String, val scale: Float) {
    SMALL("Klein", 0.9f),
    NORMAL("Normal", 1.0f),
    LARGE("Groß", 1.15f),
    XLARGE("Sehr groß", 1.3f),
    XXLARGE("Riesig", 1.5f);
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val font: AppFont = AppFont.ROBOTO,
    val fontSize: FontSizeStep = FontSizeStep.NORMAL,
    val hapticsEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val lastOpenedListId: Long? = null,
)

/** Robustes Einlesen gespeicherter Enum-Namen; Unbekanntes fällt auf den Standard zurück. */
inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
