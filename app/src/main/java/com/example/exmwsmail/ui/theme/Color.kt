package com.example.exmwsmail.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette — indigo / slate / teal accent
private val IndigoDeep = Color(0xFF2E3A6B)
private val IndigoMid = Color(0xFF4A5BA0)
private val IndigoSoft = Color(0xFFE3E8F4)
private val SlateDeep = Color(0xFF38445C)
private val SlateMid = Color(0xFF5C6B86)
private val SlateSoft = Color(0xFFE6EAF1)
private val Teal = Color(0xFF1F8C7E)
private val TealSoft = Color(0xFFD7EFE9)

private val NeutralBg = Color(0xFFFFFFFF)
private val NeutralSurface = Color(0xFFFFFFFF)
private val NeutralSurfaceLow = Color(0xFFFAFBFD)
private val NeutralSurfaceMid = Color(0xFFF4F6FA)
private val NeutralSurfaceHigh = Color(0xFFEDF0F5)
private val NeutralSurfaceHighest = Color(0xFFE3E8EF)
private val NeutralOnSurface = Color(0xFF101522)
private val NeutralOnVariant = Color(0xFF55617A)
private val NeutralOutline = Color(0xFFC8D0DC)
private val NeutralOutlineVariant = Color(0xFFE6EAF1)
private val ErrorRed = Color(0xFFB3261E)
private val ErrorRedSoft = Color(0xFFF9DEDC)

private val IndigoBright = Color(0xFFA8B6E5)
private val IndigoDark = Color(0xFF1A2447)
private val IndigoContainerDark = Color(0xFF2C3866)
private val SlateBright = Color(0xFFB0BBD3)
private val SlateContainerDark = Color(0xFF3A465F)
private val TealBright = Color(0xFF6FD7C7)
private val TealContainerDark = Color(0xFF1F4D45)

private val DarkBg = Color(0xFF0E121A)
private val DarkSurface = Color(0xFF161B26)
private val DarkSurfaceLow = Color(0xFF1A2030)
private val DarkSurfaceMid = Color(0xFF222937)
private val DarkSurfaceHigh = Color(0xFF2A3242)
private val DarkSurfaceHighest = Color(0xFF323B4D)
private val DarkOnSurface = Color(0xFFE3E7EE)
private val DarkOnVariant = Color(0xFFB0BAC9)
private val DarkOutline = Color(0xFF3D475A)
private val DarkOutlineVariant = Color(0xFF252C39)
private val DarkErrorRed = Color(0xFFFFB4AB)

val LightExmColors = lightColorScheme(
    primary = IndigoDeep,
    onPrimary = Color.White,
    primaryContainer = IndigoSoft,
    onPrimaryContainer = IndigoDeep,
    secondary = SlateDeep,
    onSecondary = Color.White,
    secondaryContainer = SlateSoft,
    onSecondaryContainer = SlateDeep,
    tertiary = Teal,
    onTertiary = Color.White,
    tertiaryContainer = TealSoft,
    onTertiaryContainer = Teal,
    background = NeutralBg,
    onBackground = NeutralOnSurface,
    surface = NeutralSurface,
    onSurface = NeutralOnSurface,
    surfaceVariant = NeutralSurfaceMid,
    onSurfaceVariant = NeutralOnVariant,
    surfaceTint = IndigoMid,
    surfaceContainerLowest = NeutralSurface,
    surfaceContainerLow = NeutralSurfaceLow,
    surfaceContainer = NeutralSurfaceMid,
    surfaceContainerHigh = NeutralSurfaceHigh,
    surfaceContainerHighest = NeutralSurfaceHighest,
    outline = NeutralOutline,
    outlineVariant = NeutralOutlineVariant,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedSoft,
    onErrorContainer = ErrorRed,
)

val DarkExmColors = darkColorScheme(
    primary = IndigoBright,
    onPrimary = IndigoDark,
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = IndigoSoft,
    secondary = SlateBright,
    onSecondary = SlateDeep,
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = SlateSoft,
    tertiary = TealBright,
    onTertiary = Color(0xFF093C36),
    tertiaryContainer = TealContainerDark,
    onTertiaryContainer = TealSoft,
    background = DarkBg,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceMid,
    onSurfaceVariant = DarkOnVariant,
    surfaceTint = IndigoBright,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurfaceMid,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkErrorRed,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)
