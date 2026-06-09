package com.exmworkspace.exmwsmail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun EXMWSMailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkExmColors else LightExmColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ExmTypography,
        shapes = ExmShapes,
        content = content,
    )
}
