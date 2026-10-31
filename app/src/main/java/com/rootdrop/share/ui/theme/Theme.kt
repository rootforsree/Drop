package com.rootdrop.share.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = DropBlueDark,
    onPrimary = androidx.compose.ui.graphics.Color.White,
)

private val LightColors = lightColorScheme(
    primary = DropBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
)

/**
 * Dark-first theme: the dark scheme is the primary design target, and the
 * light scheme exists for devices that ask for it. No dynamic color in v1,
 * so the accent stays exactly DropBlue on every device.
 */
@Composable
fun DropTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DropTypography,
        content = content,
    )
}
