package com.bitavida.app.ui.brand

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * Normalized, resolution-independent geometry for “El hilo de la vida”.
 *
 * The single contour starts at the upper terminal, travels down the spine,
 * draws the lower and upper bowls, and returns to the origin. It intentionally
 * avoids ECG-like angles and medical symbols.
 */
@Immutable
internal object BitavidaMarkGeometry {
    const val VIEWPORT = 100f

    val eventNodes: List<Offset> = listOf(
        Offset(28f, 10f),
        Offset(43f, 51f),
        Offset(78f, 68f),
    )

    fun createPath(): Path = Path().apply {
        moveTo(28f, 10f)
        lineTo(28f, 90f)
        cubicTo(58f, 90f, 78f, 82f, 78f, 68f)
        cubicTo(78f, 56f, 64f, 51f, 43f, 51f)
        cubicTo(65f, 51f, 78f, 43f, 78f, 29f)
        cubicTo(78f, 17f, 60f, 10f, 28f, 10f)
    }
}
