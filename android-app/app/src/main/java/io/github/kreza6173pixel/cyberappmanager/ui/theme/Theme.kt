package io.github.kreza6173pixel.cyberappmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Neon HUD palette, taken from the Cyber App Manager WebUI (webui/style.css, :root). */
object CyberColors {
    val Bg = Color(0xFF06060A)
    val Card = Color(0xFF0C0E16)
    val Sheet = Color(0xFF0B0E18)
    val Cyan = Color(0xFF00D4FF)
    val CyanDim = Color(0xFF07303A)
    val Green = Color(0xFF00FF88)
    val Red = Color(0xFFFF3366)
    val Magenta = Color(0xFFFF00AA)
    val Yellow = Color(0xFFFFCC00)
    val Text = Color(0xFFC8CDD5)
    val TextDim = Color(0xFF6A7282)

    /** rgba(0,212,255,0.14) */
    val Border = Color(0x2400D4FF)

    /** rgba(0,212,255,0.4) */
    val BorderStrong = Color(0x6600D4FF)
}

/** Always dark: the WebUI had no light mode and the neon palette only works on black. */
private val CyberScheme = darkColorScheme(
    primary = CyberColors.Cyan,
    onPrimary = CyberColors.Bg,
    primaryContainer = CyberColors.CyanDim,
    onPrimaryContainer = CyberColors.Cyan,
    secondary = CyberColors.Magenta,
    onSecondary = CyberColors.Bg,
    tertiary = CyberColors.Green,
    onTertiary = CyberColors.Bg,
    error = CyberColors.Red,
    onError = CyberColors.Bg,
    background = CyberColors.Bg,
    onBackground = CyberColors.Text,
    surface = CyberColors.Bg,
    onSurface = CyberColors.Text,
    surfaceVariant = CyberColors.Card,
    onSurfaceVariant = CyberColors.TextDim,
    surfaceContainerLowest = CyberColors.Bg,
    surfaceContainerLow = CyberColors.Card,
    surfaceContainer = CyberColors.Card,
    surfaceContainerHigh = CyberColors.Sheet,
    surfaceContainerHighest = CyberColors.Card,
    outline = CyberColors.BorderStrong,
    outlineVariant = CyberColors.Border,
)

/** The WebUI is monospace throughout, so every text style is too. */
private val CyberTypography: Typography = Typography().let { t ->
    val mono = FontFamily.Monospace
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = mono),
        displayMedium = t.displayMedium.copy(fontFamily = mono),
        displaySmall = t.displaySmall.copy(fontFamily = mono),
        headlineLarge = t.headlineLarge.copy(fontFamily = mono),
        headlineMedium = t.headlineMedium.copy(fontFamily = mono),
        headlineSmall = t.headlineSmall.copy(fontFamily = mono),
        titleLarge = t.titleLarge.copy(fontFamily = mono),
        titleMedium = t.titleMedium.copy(fontFamily = mono),
        titleSmall = t.titleSmall.copy(fontFamily = mono),
        bodyLarge = t.bodyLarge.copy(fontFamily = mono),
        bodyMedium = t.bodyMedium.copy(fontFamily = mono),
        bodySmall = t.bodySmall.copy(fontFamily = mono),
        labelLarge = t.labelLarge.copy(fontFamily = mono),
        labelMedium = t.labelMedium.copy(fontFamily = mono),
        labelSmall = t.labelSmall.copy(fontFamily = mono),
    )
}

@Composable
fun CyberAppManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberScheme,
        typography = CyberTypography,
        content = content,
    )
}
