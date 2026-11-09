package com.bitavida.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.theme.bitavidaColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object BitavidaFluidMotion {
    const val Press = 100
    const val Release = 200
    const val Selection = 220
    const val ThreadReveal = 380
    const val HeaderFlowTravel = 2_400
    const val HeaderFlowPause = 6_000
    const val ConfirmationTransition = 360
    const val StateTransition = 300
    const val PressScale = 0.97f
}

/** One physical source, one callback owner (clickable). A short release retains its visual impulse. */
@Composable
fun Modifier.bitavidaFluidInteraction(
    source: MutableInteractionSource,
    enabled: Boolean,
    motionAllowed: Boolean,
    pressedOverride: Boolean? = null,
    shape: Shape = RoundedCornerShape(18.dp, 28.dp, 28.dp, 18.dp),
    restingElevationDp: Float = 3f,
): Modifier {
    val response = remember { Animatable(0f) }
    val highlight = MaterialTheme.colorScheme.onSurface
    LaunchedEffect(source, enabled, motionAllowed, pressedOverride) {
        response.snapTo(if (enabled && pressedOverride == true) 1f else 0f)
        if (!enabled || pressedOverride != null) return@LaunchedEffect
        var transition: Job? = null
        var contact: PressInteraction.Press? = null
        try {
            source.interactions.collect { event ->
                when (event) {
                    is PressInteraction.Press -> {
                        contact = event
                        transition?.cancel()
                        transition = launch {
                            if (motionAllowed) response.animateTo(1f,
                                tween(BitavidaFluidMotion.Press, easing = FastOutSlowInEasing))
                            else response.snapTo(1f)
                        }
                    }
                    is PressInteraction.Release -> if (event.press === contact) {
                        contact = null
                        transition?.cancel()
                        transition = launch {
                            // A down/up in one frame still produces a finite response.
                            // This coroutine never delays or invokes onClick.
                            if (motionAllowed) {
                                if (response.value < 1f) response.animateTo(1f,
                                    tween(BitavidaFluidMotion.Press, easing = FastOutSlowInEasing))
                                response.animateTo(0f, tween(BitavidaFluidMotion.Release,
                                    easing = FastOutSlowInEasing))
                            } else response.snapTo(0f)
                        }
                    }
                    is PressInteraction.Cancel -> if (event.press === contact) {
                        contact = null
                        transition?.cancel()
                        transition = launch {
                            if (motionAllowed) response.animateTo(0f,
                                tween(BitavidaFluidMotion.Release, easing = FastOutSlowInEasing))
                            else response.snapTo(0f)
                        }
                    }
                }
            }
        } finally {
            transition?.cancel()
        }
    }
    return this.graphicsLayer {
        val amount = if (enabled) response.value else 0f
        val scale = if (motionAllowed) 1f - (1f - BitavidaFluidMotion.PressScale) * amount else 1f
        scaleX = scale
        scaleY = scale
        this.shape = shape
        shadowElevation = if (motionAllowed) (restingElevationDp * (1f - amount)).dp.toPx() else 0f
    }.clip(shape).drawWithCache {
        onDrawWithContent {
            drawContent()
            // Darken in light theme, illuminate in dark theme; visible, bounded and neutral.
            drawRect(highlight.copy(alpha = 0.075f * if (enabled) response.value else 0f))
        }
    }
}

/** Fraction derived directly from pixels travelled. At one viewport: 20dp, capped at 24dp. */
internal fun fluidScrollScreens(scrollPixels: Float, viewportPixels: Float): Float =
    (scrollPixels / viewportPixels.coerceAtLeast(1f)).coerceIn(0f, 1.2f)

/** Cached broad silk planes. Geometry is independent from the scroll and all drawing is clipped. */
@Composable
fun BitavidaFluidBackground(
    modifier: Modifier = Modifier,
    scrollPixels: () -> Float = { 0f },
    motionAllowed: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val base = MaterialTheme.colorScheme.background
    val colors = MaterialTheme.bitavidaColors
    val dark = base.luminance() < 0.15f
    Box(modifier.clipToBounds().drawWithCache {
        val w = size.width
        val h = size.height
        val field = Brush.radialGradient(
            listOf(colors.paleBlue, base),
            Offset(w * 0.95f, h * 0.24f), w * 1.35f,
        )
        val silk = Path().apply {
            moveTo(w * 0.76f, -h * 0.15f)
            cubicTo(w * 0.10f, h * 0.10f, w * 1.27f, h * 0.31f, w * 0.52f, h * 0.55f)
            cubicTo(-w * 0.16f, h * 0.76f, w * 0.48f, h * 0.91f, w * 0.15f, h * 1.15f)
            lineTo(w * 1.3f, h * 1.15f)
            lineTo(w * 1.3f, -h * 0.15f)
            close()
        }
        val veil = Path().apply {
            moveTo(-w * 0.2f, h * 0.29f)
            cubicTo(w * 0.52f, h * 0.40f, -w * 0.08f, h * 0.62f, w * 0.36f, h * 0.81f)
            cubicTo(w * 0.75f, h * 0.98f, w * 0.17f, h * 1.03f, w * 0.34f, h * 1.15f)
            lineTo(-w * 0.2f, h * 1.15f)
            close()
        }
        val silkFill = Brush.verticalGradient(listOf(
            colors.gradientStart.copy(alpha = if (dark) 0.10f else 0.07f),
            colors.paleBlue.copy(alpha = 0.76f),
            colors.mint.copy(alpha = if (dark) 0.35f else 0.60f),
        ))
        val veilFill = Brush.verticalGradient(listOf(
            colors.mint.copy(alpha = 0f),
            colors.mint.copy(alpha = if (dark) 0.30f else 0.72f),
            base.copy(alpha = 0.30f),
        ))
        val edge = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
        onDrawBehind {
            drawRect(base)
            drawRect(field)
            val travel = if (motionAllowed) fluidScrollScreens(scrollPixels(), h) else 0f
            translate(left = travel * 4.dp.toPx(), top = -travel * 20.dp.toPx()) {
                drawPath(silk, silkFill)
                drawPath(silk, colors.gradientStart.copy(alpha = if (dark) 0.10f else 0.09f), style = edge)
            }
            translate(left = -travel * 3.dp.toPx(), top = travel * 12.dp.toPx()) {
                drawPath(veil, veilFill)
            }
        }
    }, content = content)
}
