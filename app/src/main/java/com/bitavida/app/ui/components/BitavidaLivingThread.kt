package com.bitavida.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.packInts
import androidx.compose.ui.util.unpackInt1
import androidx.compose.ui.util.unpackInt2
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.bitavida.app.ui.theme.bitavidaColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.round
import kotlin.math.sin

internal const val BitavidaThreadSectionCount = 4
private const val ThreadMaximumLag = 0.018f

/** Error threshold that activates maximum catch-up, scaled to the current viewport. */
internal fun threadViewportLag(viewport: Int, range: Int): Float =
    if (range <= 0) 0f else (viewport.coerceAtLeast(1) * 0.025f / range)
        .coerceIn(0.0005f, ThreadMaximumLag)
private const val ThreadBaseMaximumSpeed = 0.82f
private const val ThreadCatchUpMaximumSpeed = 4.2f
private const val ThreadBaseResponse = 10f
private const val ThreadAdaptiveResponse = 24f
private const val ThreadVelocityResponse = 15f

/**
 * Logical position follows scroll exactly. normalizedPosition is integrated frame by frame;
 * target changes never overwrite the coordinate already visible on screen.
 */
class BitavidaThreadFlowState internal constructor() {
    internal var normalizedPosition by mutableFloatStateOf(0f)
    internal var direction by mutableIntStateOf(1)
    internal var intensity by mutableFloatStateOf(0f)
    internal var flowSpeed = 0f
    internal var logicalPosition = 0f
    internal var targetPosition = 0f
    internal var velocity = 0f
}

/** Frame-rate independent response; phase is never reset by a gesture. */
internal fun smoothCurrentValue(current: Float, target: Float, dt: Float, response: Float): Float =
    current + (target - current) * (1f - exp(-response * dt.coerceIn(0f, 0.05f)))

internal fun threadFlowVisualDirection(velocity: Float, previousDirection: Int): Int = when {
    velocity > 0.0001f -> 1
    velocity < -0.0001f -> -1
    else -> previousDirection
}

internal fun currentIntensityTarget(viewportsPerSecond: Float): Float =
    kotlin.math.sqrt((abs(viewportsPerSecond) / 1.2f).coerceIn(0f, 1f))

/** Local, unbounded section coordinate derived from the one global journey position. */
internal fun threadFlowSectionPosition(
    normalizedPosition: Float,
    order: Int,
    sectionCount: Int,
): Float {
    if (sectionCount <= 0 || order !in 0 until sectionCount) return Float.NaN
    return normalizedPosition.coerceIn(0f, 1f) * sectionCount - order
}

internal fun threadFlowLogicalPosition(
    scrollPixels: Int,
    scrollRange: Int,
): Float {
    if (scrollRange <= 0) return 0f
    return scrollPixels.toFloat().div(scrollRange.toFloat()).coerceIn(0f, 1f)
}

/** Exact node response for the same global current, independent of scroll direction. */
internal fun threadNodePassage(
    sectionPosition: Float,
    visibility: Float,
    radius: Float = 0.14f,
): Float {
    if (!sectionPosition.isFinite() || radius <= 0f) return 0f
    val proximity = (1f - abs(sectionPosition) / radius).coerceIn(0f, 1f)
    val smooth = proximity * proximity * (3f - 2f * proximity)
    return smooth * visibility.coerceIn(0f, 1f)
}

private fun nodeApproachFactor(position: Float): Float {
    val scaled = position.coerceIn(0f, 1f) * BitavidaThreadSectionCount
    val nearest = round(scaled)
    if (nearest <= 0f || nearest >= BitavidaThreadSectionCount.toFloat()) return 1f
    val distance = abs(scaled - nearest)
    val awayFromNode = (distance / 0.18f).coerceIn(0f, 1f)
    return 0.52f + 0.48f * awayFromNode * awayFromNode
}

@Composable
fun rememberBitavidaThreadFlowState(
    scrollState: ScrollState,
    motionAllowed: Boolean,
): BitavidaThreadFlowState {
    val state = remember { BitavidaThreadFlowState() }
    val lifecycleStarted = rememberLifecycleStarted()

    LaunchedEffect(scrollState, motionAllowed, lifecycleStarted) {
        val initialLogical = threadFlowLogicalPosition(
            scrollPixels = scrollState.value,
            scrollRange = scrollState.maxValue,
        )
        state.logicalPosition = initialLogical
        state.targetPosition = initialLogical
        if (!scrollState.isScrollInProgress) {
            state.normalizedPosition = initialLogical
        }

        if (!motionAllowed || !lifecycleStarted) {
            state.velocity = 0f
            state.normalizedPosition = initialLogical
            state.intensity = 0f
            state.flowSpeed = 0f
            return@LaunchedEffect
        }

        coroutineScope {
            launch {
                snapshotFlow {
                    packInts(scrollState.value, scrollState.maxValue)
                }.collect { packedScroll ->
                    val logical = threadFlowLogicalPosition(
                        scrollPixels = unpackInt1(packedScroll),
                        scrollRange = unpackInt2(packedScroll),
                    )
                    state.logicalPosition = logical
                    state.targetPosition = logical
                }
            }

            launch {
                fun advanceCurrent(deltaPixels: Int, dt: Float) {
                    val measuredSpeed = (deltaPixels / dt /
                        scrollState.viewportSize.coerceAtLeast(1)).coerceIn(-6f, 6f)
                    state.flowSpeed = smoothCurrentValue(
                        state.flowSpeed, measuredSpeed, dt, 11f)
                    val target = currentIntensityTarget(measuredSpeed)
                    state.intensity = smoothCurrentValue(
                        state.intensity, target, dt, if (target > state.intensity) 12f else 7f)
                }
                snapshotFlow { scrollState.isScrollInProgress }.collectLatest { scrolling ->
                    if (!scrolling) {
                        state.velocity = 0f
                        val logical = state.logicalPosition
                        val startPosition = state.normalizedPosition
                        var elapsed = 0f
                        var previousFrameNanos = 0L
                        while (currentCoroutineContext().isActive &&
                            (elapsed < 0.16f || state.intensity > 0.002f ||
                                abs(state.flowSpeed) > 0.002f)) {
                            withFrameNanos { frame ->
                                val dt = if (previousFrameNanos == 0L) 1f / 60f else
                                    ((frame - previousFrameNanos) / 1_000_000_000f)
                                        .coerceIn(1f / 240f, 0.05f)
                                previousFrameNanos = frame
                                elapsed += dt
                                advanceCurrent(0, dt)
                                val t = (elapsed / 0.16f).coerceIn(0f, 1f)
                                val blend = t * t * (3f - 2f * t)
                                state.normalizedPosition =
                                    startPosition + (logical - startPosition) * blend
                            }
                        }
                        state.intensity = 0f
                        state.flowSpeed = 0f
                        return@collectLatest
                    }

                    var previousScrollPixels = scrollState.value
                    var previousFrameNanos = 0L
                    while (
                        currentCoroutineContext().isActive &&
                        scrollState.isScrollInProgress
                    ) {
                        withFrameNanos { frameNanos ->
                            val deltaSeconds = if (previousFrameNanos == 0L) {
                                1f / 60f
                            } else {
                                ((frameNanos - previousFrameNanos) / 1_000_000_000f)
                                    .coerceIn(1f / 240f, 1f / 20f)
                            }
                            previousFrameNanos = frameNanos
                            val currentPixels = scrollState.value
                            advanceCurrent(currentPixels - previousScrollPixels, deltaSeconds)
                            previousScrollPixels = currentPixels

                            val target = state.targetPosition
                            val error = target - state.normalizedPosition
                            val urgency =
                                (abs(error) / threadViewportLag(scrollState.viewportSize,
                                    scrollState.maxValue).coerceAtLeast(0.0005f)).coerceIn(0f, 1f)
                            val response =
                                ThreadBaseResponse + ThreadAdaptiveResponse * urgency
                            val maximumSpeed =
                                ThreadBaseMaximumSpeed +
                                    ThreadCatchUpMaximumSpeed * urgency
                            val nodeFactor = if (urgency < 0.55f) {
                                nodeApproachFactor(state.normalizedPosition)
                            } else {
                                1f
                            }
                            val desiredVelocity = (error * response)
                                .coerceIn(-maximumSpeed, maximumSpeed) * nodeFactor
                            val velocityBlend = 1f - exp(
                                -(ThreadVelocityResponse + response * 0.45f) *
                                    deltaSeconds,
                            )
                            state.velocity +=
                                (desiredVelocity - state.velocity) * velocityBlend
                            state.direction = threadFlowVisualDirection(
                                velocity = state.velocity,
                                previousDirection = state.direction,
                            )

                            val candidate =
                                state.normalizedPosition + state.velocity * deltaSeconds
                            val crossedTarget =
                                (error > 0f && candidate >= target) ||
                                    (error < 0f && candidate <= target)
                            val integrated = if (crossedTarget) {
                                state.velocity = 0f
                                target
                            } else {
                                candidate
                            }
                            state.normalizedPosition = integrated.coerceIn(0f, 1f)
                        }
                    }
                }
            }
        }
    }
    return state
}

@Composable
private fun rememberLifecycleStarted(): Boolean {
    val view = LocalView.current
    val owner = remember(view) { view.findViewTreeLifecycleOwner() }
    var started by remember(owner) {
        mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != false)
    }

    if (owner != null) {
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, _ ->
                started = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
            owner.lifecycle.addObserver(observer)
            started = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
    }
    return started
}

/**
 * Static header geometry with a short travelling highlight. The loop only exists while the
 * header is visible, the host lifecycle is started and motion is permitted.
 */
@Composable
fun BitavidaHeaderFlow(
    modifier: Modifier = Modifier,
    motionAllowed: Boolean,
) {
    val progress = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }
    val lifecycleStarted = rememberLifecycleStarted()

    LaunchedEffect(motionAllowed, visible, lifecycleStarted) {
        if (!motionAllowed || !visible || !lifecycleStarted) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                tween(
                    durationMillis = BitavidaFluidMotion.HeaderFlowTravel,
                    easing = FastOutSlowInEasing,
                ),
            )
            delay(BitavidaFluidMotion.HeaderFlowPause.toLong())
        }
    }

    val colors = androidx.compose.material3.MaterialTheme.bitavidaColors
    val muted = androidx.compose.material3.MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .onVisibilityChanged(minFractionVisible = 0.10f) { visible = it }
            .clearAndSetSemantics { }
            .drawWithCache {
                val xStart = size.width * 0.04f
                val xEnd = size.width * 0.96f
                val startY = size.height * 0.58f
                val endY = size.height * 0.52f
                val node = Offset(xEnd, endY)
                val path = Path().apply {
                    moveTo(xStart, startY)
                    cubicTo(
                        size.width * 0.30f,
                        size.height * 0.68f,
                        size.width * 0.68f,
                        size.height * 0.34f,
                        node.x,
                        node.y,
                    )
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val longTail = Path()
                val shortTail = Path()
                val head = Path()
                val baseStroke = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round)
                val integrationStroke = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
                val softStroke = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
                val tailStroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                val headStroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                val integrationBrush = Brush.horizontalGradient(
                    listOf(
                        colors.paleBlue.copy(alpha = 0.03f),
                        colors.gradientStart.copy(alpha = 0.10f),
                        colors.mint.copy(alpha = 0.08f),
                        colors.paleBlue.copy(alpha = 0.03f),
                    ),
                    startX = xStart,
                    endX = xEnd,
                )
                val lineBrush = Brush.horizontalGradient(
                    listOf(
                        muted.copy(alpha = 0.18f),
                        colors.gradientEnd.copy(alpha = 0.38f),
                        colors.gradientStart.copy(alpha = 0.28f),
                        muted.copy(alpha = 0.16f),
                    ),
                    startX = xStart,
                    endX = xEnd,
                )

                onDrawBehind {
                    drawPath(path, integrationBrush, style = integrationStroke)
                    drawPath(path, lineBrush, style = baseStroke)
                    val fraction = progress.value.coerceIn(0f, 1f)
                    val envelope = when {
                        fraction <= 0.04f -> fraction / 0.04f
                        fraction >= 0.96f -> (1f - fraction) / 0.04f
                        else -> 1f
                    }.coerceIn(0f, 1f)
                    if (motionAllowed && envelope > 0f) {
                        val distance = measure.length * fraction
                        longTail.rewind()
                        shortTail.rewind()
                        head.rewind()
                        measure.getSegment(
                            (distance - measure.length * 0.28f).coerceAtLeast(0f),
                            distance,
                            longTail,
                            true,
                        )
                        measure.getSegment(
                            (distance - measure.length * 0.14f).coerceAtLeast(0f),
                            distance,
                            shortTail,
                            true,
                        )
                        measure.getSegment(
                            (distance - 3.dp.toPx()).coerceAtLeast(0f),
                            distance,
                            head,
                            true,
                        )
                        drawPath(
                            longTail,
                            colors.gradientStart.copy(alpha = 0.14f * envelope),
                            style = softStroke,
                        )
                        drawPath(
                            shortTail,
                            colors.gradientStart.copy(alpha = 0.48f * envelope),
                            style = tailStroke,
                        )
                        drawPath(
                            head,
                            colors.gradientEnd.copy(alpha = 0.90f * envelope),
                            style = headStroke,
                        )
                    }
                    val pulse = if (fraction > 0.88f) {
                        sin(((fraction - 0.88f) / 0.12f) * Math.PI)
                            .toFloat()
                            .coerceAtLeast(0f)
                    } else {
                        0f
                    }
                    drawCircle(
                        color = colors.gradientEnd.copy(alpha = 0.44f + 0.12f * pulse),
                        radius = 3.2.dp.toPx() * (1f + pulse * 0.06f),
                        center = node,
                    )
                    if (pulse > 0f) {
                        drawCircle(
                            color = colors.gradientStart.copy(alpha = 0.07f * pulse),
                            radius = 7.dp.toPx(),
                            center = node,
                        )
                    }
                }
            },
    )
}
