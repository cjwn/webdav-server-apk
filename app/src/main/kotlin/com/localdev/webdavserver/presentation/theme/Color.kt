package com.localdev.webdavserver.presentation.theme

import androidx.compose.ui.graphics.Color

// ---- iOS system palette ----
val IosBlue = Color(0xFF0A84FF)
val IosBlueLight = Color(0xFF007AFF)
val IosGreen = Color(0xFF34C759)
val IosRed = Color(0xFFFF3B30)
val IosOrange = Color(0xFFFF9500)
val IosGray = Color(0xFF8E8E93)

// Backgrounds / surfaces
val GroupedBgLight = Color(0xFFF2F2F7)
val SurfaceLight = Color(0xFFFFFFFF)
val SeparatorLight = Color(0x2E3C3C43) // rgba(60,60,67,0.18)

val BgDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF1C1C1E)
val SeparatorDark = Color(0xA6545458) // rgba(84,84,88,0.65)

val SurfaceAmoled = Color(0xFF000000)
val SeparatorAmoled = Color(0x73545458) // rgba(84,84,88,0.45)

// Text
val LabelLight = Color(0xFF000000)
val SecondaryLabelLight = Color(0x993C3C43)
val LabelDark = Color(0xFFFFFFFF)
val SecondaryLabelDark = Color(0x99EBEBF5)

// Status tints
val RunningTintLight = Color(0xFFF0FFF4)
val StoppedTintLight = Color(0xFFF2F2F7)
val RunningTintDark = Color(0xFF0E2A16)
val StoppedTintDark = Color(0xFF1C1C1E)

// Avatar pastel palette (deterministic by username hash)
val AvatarPalette = listOf(
    Color(0xFFFF9AA2), Color(0xFFFFB7B2), Color(0xFFFFDAC1),
    Color(0xFFE2F0CB), Color(0xFFB5EAD7), Color(0xFFC7CEEA),
    Color(0xFFA0C4FF), Color(0xFFBDB2FF)
)
