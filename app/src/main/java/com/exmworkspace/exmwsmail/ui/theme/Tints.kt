package com.exmworkspace.exmwsmail.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's muted accent language: container / on-container pairs, desaturated on purpose so
 * a screen full of them still reads as a professional tool. Avatars hash into [MutedTints];
 * folder icons and attachment type chips pick theirs by meaning. One palette everywhere is
 * what makes the screens feel related.
 */
data class Tint(val container: Color, val content: Color)

val TintIndigo = Tint(Color(0xFFE3E7F8), Color(0xFF3D4A8C))
val TintTeal = Tint(Color(0xFFDDEEEA), Color(0xFF23615A))
val TintAmber = Tint(Color(0xFFF6ECD9), Color(0xFF7A5A18))
val TintRose = Tint(Color(0xFFF7E3E3), Color(0xFF8C3D46))
val TintGreen = Tint(Color(0xFFE1EFDC), Color(0xFF3F6B33))
val TintCyan = Tint(Color(0xFFDDEBF4), Color(0xFF2A5E77))
val TintPlum = Tint(Color(0xFFEFE3F2), Color(0xFF6D4180))
val TintStone = Tint(Color(0xFFEAE8E4), Color(0xFF5D564C))

/** Hash target for per-sender colours; order is part of the contract (stable slots). */
val MutedTints: List<Tint> = listOf(
    TintIndigo, TintTeal, TintAmber, TintRose, TintGreen, TintCyan, TintPlum, TintStone,
)
