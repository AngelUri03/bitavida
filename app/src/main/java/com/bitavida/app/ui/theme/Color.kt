package com.bitavida.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Iteration 0.4: petroleum blue and turquoise remain the brand family.
// Figma V2 is historical; these values form the native "Hilo de la vida" system.
internal val LightPrimary = Color(0xFF086A7D)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFDDF5F5)
internal val LightOnPrimaryContainer = Color(0xFF0D3440)
internal val LightSecondary = Color(0xFF527482)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightBackground = Color(0xFFF5FAFA)
internal val LightOnBackground = Color(0xFF102B38)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightOnSurface = Color(0xFF102B38)
internal val LightSurfaceVariant = Color(0xFFEDF5F5)
internal val LightOnSurfaceVariant = Color(0xFF45616D)
internal val LightOutline = Color(0xFFC9DCDC)
internal val LightError = Color(0xFFAD4854)
internal val LightOnError = Color(0xFFFFFFFF)

// Designed dark variant: ink background, luminous thread and restrained colored surfaces.
internal val DarkPrimary = Color(0xFF70D9D7)
internal val DarkOnPrimary = Color(0xFF00383D)
internal val DarkPrimaryContainer = Color(0xFF123E48)
internal val DarkOnPrimaryContainer = Color(0xFFCEF7F4)
internal val DarkSecondary = Color(0xFFA9C9CF)
internal val DarkOnSecondary = Color(0xFF17363E)
internal val DarkBackground = Color(0xFF071820)
internal val DarkOnBackground = Color(0xFFE5F2F2)
internal val DarkSurface = Color(0xFF0D252E)
internal val DarkOnSurface = Color(0xFFE5F2F2)
internal val DarkSurfaceVariant = Color(0xFF15343D)
internal val DarkOnSurfaceVariant = Color(0xFFABC5CA)
internal val DarkOutline = Color(0xFF31545D)
internal val DarkError = Color(0xFFFFB3BA)
internal val DarkOnError = Color(0xFF680016)

@Immutable
data class BitavidaExtendedColors(
    val brand: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val paleBlue: Color,
    val mint: Color,
    val lavender: Color,
    val rose: Color,
    val roseStrong: Color,
    val pendingText: Color,
    val successText: Color,
    val navigationSurface: Color,
)

internal val LightExtendedColors = BitavidaExtendedColors(
    brand = Color(0xFF086A7D),
    gradientStart = Color(0xFF1596A5),
    gradientEnd = Color(0xFF086A7D),
    paleBlue = Color(0xFFE4F5F7),
    mint = Color(0xFFDFF4EA),
    lavender = Color(0xFFEDEAF8),
    rose = Color(0xFFFFF0EC),
    roseStrong = Color(0xFFFFDEDA),
    pendingText = Color(0xFFA74752),
    successText = Color(0xFF176A53),
    navigationSurface = Color(0xFFFBFEFE),
)

internal val DarkExtendedColors = BitavidaExtendedColors(
    brand = Color(0xFF70D9D7),
    gradientStart = Color(0xFF52C8CA),
    gradientEnd = Color(0xFF25899A),
    paleBlue = Color(0xFF123843),
    mint = Color(0xFF123B33),
    lavender = Color(0xFF2D2945),
    rose = Color(0xFF432A2D),
    roseStrong = Color(0xFF5B3034),
    pendingText = Color(0xFFFFB3BA),
    successText = Color(0xFF85DDBC),
    navigationSurface = Color(0xFF0B222A),
)

val LocalBitavidaExtendedColors = staticCompositionLocalOf { LightExtendedColors }
