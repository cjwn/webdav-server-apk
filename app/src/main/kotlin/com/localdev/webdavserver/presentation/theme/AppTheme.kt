package com.localdev.webdavserver.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The app uses a single fixed AMOLED (true-black) theme. There is no theme
 * switching — this is intentional and not user-configurable.
 */
private val AmoledColors: ColorScheme = darkColorScheme(
    primary = IosBlue,
    onPrimary = LabelDark,
    secondary = IosGray,
    background = BgDark,
    onBackground = LabelDark,
    surface = SurfaceAmoled,
    onSurface = LabelDark,
    surfaceVariant = SurfaceAmoled,
    onSurfaceVariant = SecondaryLabelDark,
    error = IosRed,
    outline = SeparatorAmoled
)

/**
 * Root theme — always AMOLED, no options.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
