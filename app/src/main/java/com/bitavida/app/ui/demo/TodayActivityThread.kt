package com.bitavida.app.ui.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bitavida.app.ui.components.PlanCareThreadCurve
import com.bitavida.app.ui.components.PlanCareThreadPainter
import com.bitavida.app.ui.components.BitavidaFluidMotion
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import com.bitavida.app.ui.theme.bitavidaColors
import kotlin.math.abs

/** Visual state only; each activity's accessible text describes its actual outcome. */
internal enum class TodayActivityNodeOutcome { PENDING, PERFORMED, NOT_PERFORMED }

internal class TodayActivityThreadState {
    private var container: LayoutCoordinates? = null
    private val coordinates = mutableMapOf<Any, LayoutCoordinates>()
    var bounds by mutableStateOf<Map<Any, Rect>>(emptyMap())
        private set
    var width by mutableFloatStateOf(0f)
        private set

    fun positionContainer(value: LayoutCoordinates) {
        container = value
        width = value.size.width.toFloat()
        update()
    }

    fun positionNode(key: Any, value: LayoutCoordinates) {
        coordinates[key] = value
        update()
    }

    private fun update() {
        val parent = container?.takeIf { it.isAttached } ?: return
        val updated = coordinates.filterValues { it.isAttached }.mapValues { (_, node) ->
            parent.localBoundingBoxOf(node, clipBounds = false)
        }
        if (updated != bounds) bounds = updated
    }
}

internal fun Modifier.todayActivityNode(state: TodayActivityThreadState, key: Any): Modifier =
    onGloballyPositioned { state.positionNode(key, it) }

private data class ActivityThreadGeometry(
    val path: Path,
    val length: Float,
    val stations: List<Pair<Offset, Float>>,
)

private fun activityThreadGeometry(keys: List<Any>, bounds: Map<Any, Rect>, amplitude: Float): ActivityThreadGeometry? {
    // Newly inserted rows may not have coordinates during their first layout pass.
    // Keep the existing path alive until those real anchors become available.
    val measuredKeys = keys.filter(bounds::containsKey)
    if (measuredKeys.size < 2) return null
    val points = measuredKeys.map { bounds.getValue(it).center }
    val path = Path().apply { moveTo(points.first().x, points.first().y) }
    val distances = mutableListOf(0f)
    val curve = PlanCareThreadCurve()
    var length = 0f
    points.zipWithNext().forEachIndexed { index, (from, to) ->
        val bend = minOf(amplitude, abs(to.y - from.y) * 0.12f)
        val segment = Path().apply {
            moveTo(from.x, from.y)
            curve.appendOrganicActivitySegment(this, from, to, bend, index)
        }
        length += PathMeasure().apply { setPath(segment, false) }.length
        curve.appendOrganicActivitySegment(path, from, to, bend, index)
        distances += length
    }
    if (length <= 0f) return null
    return ActivityThreadGeometry(path, length, points.zip(distances.map { it / length }))
}

/** Advances a physical distance along the current path, reflecting at each endpoint. */
internal fun advanceActivityThread(
    fraction: Float, direction: Int, distance: Float, pathLength: Float,
): Pair<Float, Int> {
    if (!pathLength.isFinite() || pathLength <= 0f || !distance.isFinite() || distance <= 0f) {
        return fraction.coerceIn(0f, 1f) to if (direction < 0) -1 else 1
    }
    val position = fraction.coerceIn(0f, 1f)
    val phase = if (direction < 0) 2f - position else position
    val next = (phase + distance / pathLength) % 2f
    return if (next < 1f) next to 1 else (2f - next) to -1
}

/**
 * Draws the same curve, painter and travelling light as Plan over measured activity anchors.
 * Place only the activity rows in [content]; headers, expansion controls and notes stay outside.
 */
@Composable
internal fun TodayActivityThread(
    keys: List<Any>,
    motion: Boolean,
    modifier: Modifier = Modifier,
    outcomes: Map<Any, TodayActivityNodeOutcome> = emptyMap(),
    content: @Composable (TodayActivityThreadState) -> Unit,
) {
    val state = remember { TodayActivityThreadState() }
    val density = LocalDensity.current.density
    val geometry = remember(keys, state.bounds, density) {
        activityThreadGeometry(keys, state.bounds, 8f * density)
    }
    val geometryState = rememberUpdatedState(geometry)
    val widthState = rememberUpdatedState(state.width)
    var progress by remember { mutableFloatStateOf(0f) }
    var direction by remember { mutableIntStateOf(1) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(motion, started) {
        if (motion && started) {
            var previousFrame = 0L
            while (currentCoroutineContext().isActive) {
                withFrameNanos { frame ->
                    val elapsed = if (previousFrame == 0L) 0f else
                        ((frame - previousFrame).coerceIn(0L, 100_000_000L) / 1_000_000_000f)
                    previousFrame = frame
                    val length = geometryState.value?.length ?: 0f
                    // Plan's width-based speed, recalculated from the actual path on every frame.
                    val speed = widthState.value * 0.94f * 1000f / BitavidaFluidMotion.HeaderFlowTravel
                    val (next, nextDirection) = advanceActivityThread(progress, direction, speed * elapsed, length)
                    progress = next
                    direction = nextDirection
                }
            }
        }
    }
    val colors = MaterialTheme.bitavidaColors
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier.onGloballyPositioned(state::positionContainer).drawWithCache {
        val current = geometry
        val painter = current?.let { PlanCareThreadPainter(density).apply { setPath(it.path) } }
        val positions = keys.mapNotNull { key -> state.bounds[key]?.center?.let { key to it } }
        onDrawBehind {
            if (current != null && painter != null) {
                painter.drawBase(this, 1f, outline, colors.gradientStart, current.length)
                if (motion && started) painter.drawFlow(this, progress, direction, keys.size,
                    colors.gradientStart, colors.gradientEnd, length = current.length)
            }
            positions.forEachIndexed { index, (key, point) ->
                val outcome = outcomes[key] ?: TodayActivityNodeOutcome.PENDING
                val filled = outcome == TodayActivityNodeOutcome.PERFORMED
                drawCircle(if (filled) primary else surface, 5f * density, point)
                drawCircle(primary, 5f * density, point, style = Stroke(2f * density))
                if (outcome == TodayActivityNodeOutcome.NOT_PERFORMED)
                    drawCircle(primary, 2f * density, point)
                val station = current?.stations?.getOrNull(index)?.second
                if (motion && started && station != null) {
                    val proximity = (1f - abs(progress - station) * (keys.size - 1).coerceAtLeast(1) * 4f)
                        .coerceIn(0f, 1f)
                    if (proximity > 0f) painter?.drawNodeReaction(this, point, proximity / 2f, colors.gradientStart)
                }
            }
        }
    }) { content(state) }
}
