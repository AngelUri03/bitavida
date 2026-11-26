package com.bitavida.app.ui.education

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.bitavida.app.ui.theme.*
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real theme tokens, including the tinted surfaces used by AI examples and fallback cards. */
class BitavidaAiContrastTest {
    private data class Palette(
        val name: String,
        val text: Map<String, Color>,
        val surfaces: Map<String, Color>,
        val actions: Map<String, Pair<Color, Color>>,
    )

    private val palettes = listOf(
        Palette(
            name = "claro",
            text = mapOf(
                "onSurface" to LightOnSurface,
                "onSurfaceVariant" to LightOnSurfaceVariant,
                "primary" to LightPrimary,
            ),
            surfaces = mapOf(
                "background" to LightBackground,
                "surface" to LightSurface,
                "surfaceVariant" to LightSurfaceVariant,
                "paleBlue" to LightExtendedColors.paleBlue,
                "mint" to LightExtendedColors.mint,
                "lavender" to LightExtendedColors.lavender,
            ),
            actions = mapOf(
                "onPrimary/primary" to (LightOnPrimary to LightPrimary),
                "onPrimaryContainer/primaryContainer" to (LightOnPrimaryContainer to LightPrimaryContainer),
            ),
        ),
        Palette(
            name = "oscuro",
            text = mapOf(
                "onSurface" to DarkOnSurface,
                "onSurfaceVariant" to DarkOnSurfaceVariant,
                "primary" to DarkPrimary,
            ),
            surfaces = mapOf(
                "background" to DarkBackground,
                "surface" to DarkSurface,
                "surfaceVariant" to DarkSurfaceVariant,
                "paleBlue" to DarkExtendedColors.paleBlue,
                "mint" to DarkExtendedColors.mint,
                "lavender" to DarkExtendedColors.lavender,
            ),
            actions = mapOf(
                "onPrimary/primary" to (DarkOnPrimary to DarkPrimary),
                "onPrimaryContainer/primaryContainer" to (DarkOnPrimaryContainer to DarkPrimaryContainer),
            ),
        ),
    )

    @Test fun primarySecondaryAndAccentTextMeetAAOnAllAISurfaces() {
        palettes.forEach { palette ->
            val ratios = palette.text.flatMap { (textName, foreground) ->
                palette.surfaces.map { (surfaceName, background) ->
                    val ratio = contrast(foreground, background)
                    assertAA("${palette.name}: $textName/$surfaceName", ratio)
                    "$textName/$surfaceName" to ratio
                }
            }
            val minimum = ratios.minBy { it.second }
            println("Bitavida IA text contrast ${palette.name}: minimum=${format(minimum.second)}:1 (${minimum.first})")
        }
    }

    @Test fun buttonAndSelectedChipPairsMeetAAInBothThemes() {
        palettes.forEach { palette ->
            val ratios = palette.actions.map { (name, colors) ->
                val ratio = contrast(colors.first, colors.second)
                assertAA("${palette.name}: $name", ratio)
                name to ratio
            }
            val minimum = ratios.minBy { it.second }
            println("Bitavida IA action contrast ${palette.name}: minimum=${format(minimum.second)}:1 (${minimum.first})")
        }
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val foregroundLuminance = foreground.luminance().toDouble()
        val backgroundLuminance = background.luminance().toDouble()
        return (max(foregroundLuminance, backgroundLuminance) + 0.05) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05)
    }

    private fun assertAA(pair: String, ratio: Double) {
        // Use the normal-text requirement even for headings and large labels.
        assertTrue("$pair must meet WCAG AA 4.5:1; actual=${format(ratio)}:1", ratio >= 4.5)
    }

    private fun format(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}
