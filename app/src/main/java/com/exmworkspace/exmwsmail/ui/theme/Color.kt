package com.exmworkspace.exmwsmail.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset

// Palette lifted from the webmail's own stylesheet (webmail.exmworkspace.com/login) so the
// app and the web client read as one product. It is Tailwind's indigo/slate ramp.

private val Indigo950 = Color(0xFF1E1B4B)
private val Indigo900 = Color(0xFF312E81)
private val Indigo800 = Color(0xFF3730A3)
private val Indigo700 = Color(0xFF4338CA)
private val Indigo600 = Color(0xFF4F46E5)
private val Indigo500 = Color(0xFF6366F1)
private val Indigo300 = Color(0xFFA5B4FC)
private val Indigo200 = Color(0xFFC7D2FE)
private val Indigo100 = Color(0xFFE0E7FF)
private val Indigo50 = Color(0xFFEEF2FF)

private val Slate900 = Color(0xFF0F172A)
private val Slate800 = Color(0xFF1E293B)
private val Slate700 = Color(0xFF334155)
private val Slate600 = Color(0xFF475569)
private val Slate500 = Color(0xFF64748B)
private val Slate400 = Color(0xFF94A3B8)
private val Slate300 = Color(0xFFCBD5E1)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate50 = Color(0xFFF8FAFC)

private val Teal600 = Color(0xFF0D9488)
private val Teal300 = Color(0xFF5EEAD4)
private val Teal900 = Color(0xFF134E4A)
private val Teal50 = Color(0xFFCCFBF1)

private val ErrorRed = Color(0xFFDC2626)
private val ErrorRedSoft = Color(0xFFFEE2E2)
private val DarkErrorRed = Color(0xFFFCA5A5)

/**
 * Brand surfaces the Material scheme has no slot for: the gradients the webmail paints its
 * hero panel and primary button with, and the blue-tinted input fill it uses on forms.
 */
object ExmBrand {
    val heroStart = Indigo950
    val heroMid = Indigo900
    val heroEnd = Indigo700

    /** 135° in CSS — top-left to bottom-right. */
    fun heroGradient(widthPx: Float, heightPx: Float) = Brush.linearGradient(
        colorStops = arrayOf(0f to heroStart, 0.4f to heroMid, 1f to heroEnd),
        start = Offset.Zero,
        end = Offset(widthPx, heightPx),
    )

    val buttonGradient = Brush.linearGradient(listOf(Indigo500, Indigo600))
    val buttonDisabled = Brush.linearGradient(listOf(Slate300, Slate400))

    /** The webmail's form fields sit on a light blue, not on plain white. */
    val fieldFill = Color(0xFFE8F0FE)
    val fieldFillDark = Color(0xFF1E2438)
    val fieldBorder = Slate200

    /**
     * The field fill for the scheme actually in effect.
     *
     * Read off the scheme's own luminance rather than [isSystemInDarkTheme] so it stays
     * correct if the app ever pins a theme regardless of the device. The dark variant existed
     * from the start but nothing ever selected it, so in dark mode the login drew near-white
     * text on a pale blue field — invisible.
     */
    val fieldFillFor: Color
        @Composable get() =
            if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) fieldFillDark else fieldFill

    val onHero = Color.White
    val onHeroMuted = Indigo200
    val heroCard = Color.White.copy(alpha = 0.08f)
    val heroCardBorder = Color.White.copy(alpha = 0.14f)
}

val LightExmColors = lightColorScheme(
    primary = Indigo600,
    onPrimary = Color.White,
    // The containers back avatars, chips and selected rows — things that repeat down a whole
    // screen. Tinted, they turn a list violet; indigo is kept for what actually acts:
    // buttons, the compose button, links, unread dots, the pin.
    primaryContainer = Slate200,
    onPrimaryContainer = Slate700,
    secondary = Slate700,
    onSecondary = Color.White,
    secondaryContainer = Slate200,
    onSecondaryContainer = Slate900,
    tertiary = Teal600,
    onTertiary = Color.White,
    tertiaryContainer = Teal50,
    onTertiaryContainer = Teal900,
    background = Slate100,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    // Material blends surfaceTint over every elevated surface in proportion to its elevation,
    // so a coloured tint here washes every card in the app violet. Setting it to the surface
    // colour makes that blend a no-op: cards stay white and read their depth from the shadow.
    // It must NOT be Color.Transparent — that is black with alpha 0, and Material re-applies
    // its own alpha to it, which paints translucent black and darkens the card to grey.
    surfaceTint = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Slate50,
    surfaceContainer = Slate100,
    surfaceContainerHigh = Color(0xFFE9EEF6),
    surfaceContainerHighest = Slate200,
    outline = Slate300,
    outlineVariant = Slate200,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedSoft,
    onErrorContainer = Color(0xFF7F1D1D),
)

val DarkExmColors = darkColorScheme(
    primary = Indigo300,
    onPrimary = Indigo950,
    primaryContainer = Color(0xFF2F3750),
    onPrimaryContainer = Slate100,
    secondary = Slate300,
    onSecondary = Slate900,
    secondaryContainer = Color(0xFF283047),
    onSecondaryContainer = Indigo100,
    tertiary = Teal300,
    onTertiary = Teal900,
    tertiaryContainer = Teal900,
    onTertiaryContainer = Teal50,
    background = Color(0xFF0B1020),
    onBackground = Slate100,
    surface = Color(0xFF141A2E),
    onSurface = Slate100,
    surfaceVariant = Color(0xFF1E2438),
    onSurfaceVariant = Slate400,
    // Same no-op trick as the light scheme: match the surface, never Color.Transparent.
    surfaceTint = Color(0xFF141A2E),
    surfaceContainerLowest = Color(0xFF0B1020),
    surfaceContainerLow = Color(0xFF161C31),
    surfaceContainer = Color(0xFF1E2438),
    surfaceContainerHigh = Color(0xFF262D44),
    surfaceContainerHighest = Color(0xFF2F3750),
    outline = Color(0xFF475069),
    outlineVariant = Color(0xFF2A3149),
    error = DarkErrorRed,
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = ErrorRedSoft,
)
