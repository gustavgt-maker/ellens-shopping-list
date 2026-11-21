package de.ellens.shoppinglist.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import de.ellens.shoppinglist.R
import de.ellens.shoppinglist.data.settings.AppFont
import de.ellens.shoppinglist.data.settings.AppSettings
import de.ellens.shoppinglist.data.settings.ThemeMode

// Neutrale Grautöne; Rot nur als gezielter Akzent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF3A3A3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDADADA),
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF5E5E5E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E4E4),
    onSecondaryContainer = Color(0xFF1C1C1C),
    tertiary = Color(0xFF5E5E5E),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF151515),
    surface = Color(0xFFF5F5F5),
    onSurface = Color(0xFF151515),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF474747),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F0F0),
    surfaceContainer = Color(0xFFEBEBEB),
    surfaceContainerHigh = Color(0xFFE5E5E5),
    surfaceContainerHighest = Color(0xFFDFDFDF),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFFC8C8C8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFDADADA),
    onPrimary = Color(0xFF1E1E1E),
    primaryContainer = Color(0xFF474747),
    onPrimaryContainer = Color(0xFFF0F0F0),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF262626),
    secondaryContainer = Color(0xFF3D3D3D),
    onSecondaryContainer = Color(0xFFEDEDED),
    tertiary = Color(0xFFBDBDBD),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2E2E2E),
    onSurfaceVariant = Color(0xFFC4C4C4),
    surfaceContainerLowest = Color(0xFF0C0C0C),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1F1F1F),
    surfaceContainerHigh = Color(0xFF292929),
    surfaceContainerHighest = Color(0xFF333333),
    outline = Color(0xFF8F8F8F),
    outlineVariant = Color(0xFF444444),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** Zusätzliche, app-spezifische Farben. */
@Immutable
data class ExtraColors(val doneRed: Color, val keepScreenOnActive: Color, val onKeepScreenOnActive: Color)

private val LightExtra = ExtraColors(
    doneRed = Color(0xFFC62828),
    keepScreenOnActive = Color(0xFF2B2B2B),
    onKeepScreenOnActive = Color(0xFFFFFFFF),
)
private val DarkExtra = ExtraColors(
    doneRed = Color(0xFFFF6B6B),
    keepScreenOnActive = Color(0xFFE6E6E6),
    onKeepScreenOnActive = Color(0xFF121212),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

/** true, wenn Android-Animationen systemweit deaktiviert sind (Barrierefreiheit). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Ob haptische Rückmeldung aktiviert ist. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Variable Schriftdatei: Compose setzt die Achse "wght" passend zum FontWeight
 * (Standardwert von variationSettings), daher genügt eine Datei für alle Stärken.
 */
private fun variableFamily(resId: Int): FontFamily {
    fun font(weight: FontWeight) = Font(resId = resId, weight = weight)
    return FontFamily(
        font(FontWeight.Normal),
        font(FontWeight.Medium),
        font(FontWeight.SemiBold),
        font(FontWeight.Bold),
    )
}

fun fontFamilyFor(font: AppFont): FontFamily = when (font) {
    AppFont.ROBOTO -> variableFamily(R.font.roboto_variable)
    AppFont.OPEN_SANS -> variableFamily(R.font.opensans_variable)
    AppFont.NOTO_SANS -> variableFamily(R.font.notosans_variable)
    AppFont.ATKINSON -> FontFamily(
        Font(R.font.atkinson_regular, FontWeight.Normal),
        Font(R.font.atkinson_bold, FontWeight.Bold),
    )
    AppFont.SYSTEM -> FontFamily.Default
}

private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

@Composable
fun isAppInDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun EllenTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val typography = remember(settings.font) { Typography().withFamily(fontFamilyFor(settings.font)) }

    // Die App-Schriftstufe multipliziert die Android-Schriftgröße. Dadurch skalieren alle
    // sp-Größen (auch der Material-Komponenten) gleichmäßig mit.
    val baseDensity = LocalDensity.current
    val density = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * settings.fontSize.scale,
    )

    CompositionLocalProvider(
        LocalDensity provides density,
        LocalExtraColors provides if (dark) DarkExtra else LightExtra,
        LocalReduceMotion provides reduceMotion,
        LocalHapticsEnabled provides settings.hapticsEnabled,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = typography,
            content = content,
        )
    }
}
