package com.bitavida.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.abs

private val activityBendFactors = floatArrayOf(1f, -0.76f, 0.88f, -0.64f, 0.72f, -0.94f)

/** Exact Plan station connector, reusable with a narrower amplitude in a compact activity corridor. */
internal class PlanCareThreadCurve {
    var firstX = 0f
        private set
    var firstY = 0f
        private set
    var secondX = 0f
        private set
    var secondY = 0f
        private set
    var endX = 0f
        private set
    var endY = 0f
        private set

    fun update(fromX: Float, fromY: Float, toX: Float, toY: Float, amplitude: Float, index: Int) {
        val factor = when (index) { 0 -> 1f; 1 -> -0.64f; 2 -> 0.84f; else -> -0.50f }
        val bend = amplitude * factor
        val distance = toY - fromY
        firstX = fromX + bend
        firstY = fromY + distance * 0.28f
        secondX = toX + bend * 0.86f
        secondY = toY - distance * 0.30f
        endX = toX
        endY = toY
    }

    fun appendTo(path: Path) {
        path.cubicTo(firstX, firstY, secondX, secondY, endX, endY)
    }

    /**
     * Compact activity corridor: a restrained offset varies between segments, while vertical
     * tangents at both stations and at the crest keep successive Bézier curves continuous.
     * Existing Plan geometry continues to use [update] and [appendTo].
     */
    fun appendOrganicActivitySegment(
        path: Path, from: Offset, to: Offset, amplitude: Float, index: Int,
    ) {
        val distance = to.y - from.y
        val bend = amplitude * activityBendFactors[index.mod(activityBendFactors.size)]
        val midX = (from.x + to.x) / 2f + bend
        val midY = (from.y + to.y) / 2f
        val control = distance * 0.17f
        path.cubicTo(from.x, from.y + control, midX, midY - control, midX, midY)
        path.cubicTo(midX, midY + control, to.x, to.y - control, to.x, to.y)
    }
}

/** Same reference width, travel speed and partial-leg minimum used by Plan de cuidado. */
internal fun planCareThreadLegDuration(length: Float, width: Float, remaining: Float): Int {
    if (!length.isFinite() || !width.isFinite() || !remaining.isFinite() || length <= 0f || width <= 0f) return 250
    val full = (BitavidaFluidMotion.HeaderFlowTravel * (length / (width * 0.94f).coerceAtLeast(1f)))
        .toInt().coerceAtLeast(1)
    return (full * remaining.coerceIn(0f, 1f)).toInt().coerceAtLeast(250)
}

/** No geometry key or snaps: current position survives resized paths and each leg uses their real length. */
internal suspend fun animatePlanCareThreadPoint(
    progress: Animatable<Float, AnimationVector1D>,
    pathLength: () -> Float,
    referenceWidth: () -> Float,
    onDirection: (Int) -> Unit,
    onFrame: () -> Unit = {},
) {
    var target = if (progress.value < 0.5f) 1f else 0f
    while (currentCoroutineContext().isActive) {
        val distance = abs(target - progress.value)
        onDirection(if (target >= progress.value) 1 else -1)
        progress.animateTo(
            target,
            tween(planCareThreadLegDuration(pathLength(), referenceWidth(), distance), easing = LinearEasing),
        ) { onFrame() }
        target = if (target == 1f) 0f else 1f
    }
}

/** Cached renderer extracted verbatim from PlanStationThread; no per-frame paths, brushes or strokes. */
internal class PlanCareThreadPainter(private val density: Float) {
    val measure = PathMeasure()
    private val revealed = Path()
    private val longFlow = Path()
    private val mediumFlow = Path()
    private val brightFlow = Path()
    private val soft = Stroke(7f * density, cap = StrokeCap.Round)
    private val core = Stroke(2.3f * density, cap = StrokeCap.Round)
    private val glow = Stroke(10f * density, cap = StrokeCap.Round)
    private val trail = Stroke(5.5f * density, cap = StrokeCap.Round)
    private val head = Stroke(3.3f * density, cap = StrokeCap.Round)
    private val reaction = Stroke(1.4f * density, cap = StrokeCap.Round)

    fun setPath(path: Path) { measure.setPath(path, false) }

    fun drawBase(scope: DrawScope, reveal: Float, muted: Color, accent: Color, length: Float = measure.length) = with(scope) {
        revealed.rewind()
        measure.getSegment(0f, length * reveal.coerceIn(0f, 1f), revealed, true)
        drawPath(revealed, muted.copy(alpha = 0.22f), style = soft)
        drawPath(revealed, muted.copy(alpha = 0.78f), style = core)
        drawPath(revealed, accent.copy(alpha = 0.56f), style = core)
    }

    fun drawSelection(scope: DrawScope, segment: Path, progress: Float, highlight: Color) = with(scope) {
        val pulse = 1f - abs(progress * 2f - 1f)
        drawPath(segment, highlight.copy(alpha = 0.28f + pulse * 0.42f), style = trail)
    }

    fun drawFlow(
        scope: DrawScope, fraction: Float, direction: Int, stations: Int,
        accent: Color, highlight: Color, opacity: Float = 1f, length: Float = measure.length,
    ) = with(scope) {
        val position = fraction.coerceIn(0f, 1f)
        val sectionFraction = 1f / (stations - 1).coerceAtLeast(1)
        fun extract(target: Path, tail: Float) {
            target.rewind()
            val from = (if (direction >= 0) position - tail else position).coerceIn(0f, 1f)
            val to = (if (direction >= 0) position else position + tail).coerceIn(0f, 1f)
            if (to > from) measure.getSegment(length * from, length * to, target, true)
        }
        extract(longFlow, sectionFraction * 0.34f)
        extract(mediumFlow, sectionFraction * 0.21f)
        extract(brightFlow, sectionFraction * 0.075f)
        drawPath(longFlow, accent.copy(alpha = 0.18f * opacity), style = glow)
        drawPath(mediumFlow, accent.copy(alpha = 0.50f * opacity), style = trail)
        drawPath(brightFlow, highlight.copy(alpha = 0.98f * opacity), style = head)
        val point = measure.getPosition(length * position)
        drawCircle(accent.copy(alpha = 0.18f * opacity), 6f * density, point)
        drawCircle(highlight.copy(alpha = 0.96f * opacity), 2.5f * density, point)
    }

    fun drawNodeReaction(scope: DrawScope, point: Offset, progress: Float, accent: Color) = with(scope) {
        val pulse = kotlin.math.sin(progress * kotlin.math.PI).toFloat().coerceAtLeast(0f)
        drawCircle(accent.copy(alpha = 0.28f * pulse),
            (8f + 3f * pulse) * density, point, style = reaction)
    }
}
