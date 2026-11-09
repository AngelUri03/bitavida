package com.bitavida.app.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.theme.bitavidaColors
import kotlin.math.min

private const val MarkAnimationDurationMillis = 900

/**
 * Static official mark for Bitavida. It is decorative by default and therefore
 * contributes no TalkBack node; pair it with the visible word “Bitavida”.
 */
@Composable
fun BitavidaMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.bitavidaColors.brand,
    eventColor: Color = MaterialTheme.bitavidaColors.gradientStart,
    strokeWidth: Dp = 5.dp,
    eventRadius: Dp = 4.dp,
) {
    BitavidaThread(
        modifier = modifier,
        progress = 1f,
        nodeProgress = listOf(1f, 1f, 1f),
        color = color,
        eventColor = eventColor,
        strokeWidth = strokeWidth,
        eventRadius = eventRadius,
    )
}

/**
 * Animated presentation of [BitavidaMark]. Geometry remains in [BitavidaThread];
 * this component owns only timing and reduced-motion behavior.
 */
@Composable
fun AnimatedBitavidaMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.bitavidaColors.brand,
    eventColor: Color = MaterialTheme.bitavidaColors.gradientStart,
    strokeWidth: Dp = 5.dp,
    eventRadius: Dp = 4.dp,
    motionEnabledOverride: Boolean? = null,
    onAnimationFinished: () -> Unit = {},
) {
    val motionEnabled = rememberBitavidaMotionAllowed(motionEnabledOverride)
    var hasPlayed by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (motionEnabled && !hasPlayed) 0f else 1f) }

    LaunchedEffect(motionEnabled) {
        if (!motionEnabled || hasPlayed) {
            progress.snapTo(1f)
            hasPlayed = true
            return@LaunchedEffect
        }

        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = MarkAnimationDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
        progress.snapTo(1f)
        hasPlayed = true
        onAnimationFinished()
    }

    val value = progress.value
    val traceProgress = (value / 0.70f).coerceIn(0f, 1f)
    val nodes = listOf(
        ((value - 0.62f) / 0.12f).coerceIn(0f, 1f),
        ((value - 0.72f) / 0.12f).coerceIn(0f, 1f),
        ((value - 0.82f) / 0.12f).coerceIn(0f, 1f),
    )

    BitavidaThread(
        modifier = modifier,
        progress = traceProgress,
        nodeProgress = nodes,
        color = color,
        eventColor = eventColor,
        strokeWidth = strokeWidth,
        eventRadius = eventRadius,
    )
}

/**
 * Draws the normalized continuous B contour and its three event nodes.
 * [progress] and [nodeProgress] are visual inputs only; this component has no
 * timing, persistence or clinical meaning.
 */
@Composable
fun BitavidaThread(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    nodeProgress: List<Float> = listOf(1f, 1f, 1f),
    color: Color = MaterialTheme.bitavidaColors.brand,
    eventColor: Color = MaterialTheme.bitavidaColors.gradientStart,
    strokeWidth: Dp = 5.dp,
    eventRadius: Dp = 4.dp,
) {
    val contour = remember { BitavidaMarkGeometry.createPath() }

    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        if (size.minDimension <= 0f) return@Canvas

        val viewport = BitavidaMarkGeometry.VIEWPORT
        val scaleFactor = min(size.width, size.height) / viewport
        val left = (size.width - viewport * scaleFactor) / 2f
        val top = (size.height - viewport * scaleFactor) / 2f
        val visiblePath = Path()
        val measure = PathMeasure().apply { setPath(contour, false) }
        measure.getSegment(
            startDistance = 0f,
            stopDistance = measure.length * progress.coerceIn(0f, 1f),
            destination = visiblePath,
            startWithMoveTo = true,
        )

        withTransform({
            translate(left = left, top = top)
            scale(
                scaleX = scaleFactor,
                scaleY = scaleFactor,
                pivot = Offset.Zero,
            )
        }) {
            drawPath(
                path = visiblePath,
                color = color,
                style = Stroke(
                    width = strokeWidth.toPx() / scaleFactor,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            BitavidaMarkGeometry.eventNodes.forEachIndexed { index, node ->
                val nodeScale = nodeProgress.getOrElse(index) { 0f }.coerceIn(0f, 1f)
                if (nodeScale > 0f) {
                    drawCircle(
                        color = eventColor.copy(alpha = nodeScale),
                        radius = eventRadius.toPx() / scaleFactor * nodeScale,
                        center = node,
                    )
                }
            }
        }
    }
}

