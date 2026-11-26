package com.bitavida.app.ui.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.UnplacedAwareModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.threadNodePassage
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

internal enum class TodayThreadAnchorKind { Header, Section, Surface, End }
internal data class TodayThreadAnchor(val key: Any, val kind: TodayThreadAnchorKind)

/** References only: coordinates are pulled at draw time, including animateItem graphics layers. */
internal class TodayThreadState {
    internal val anchors = LinkedHashMap<Any, TodayThreadAnchorNode>()
    internal var drawing: TodayThreadDrawNode? = null

    /** Call only for a confirmed module action; decoration never changes persisted state. */
    fun confirmSection(key: String) { drawing?.confirmSection(key) }
}

@Composable
internal fun rememberTodayThreadState(): TodayThreadState = remember { TodayThreadState() }

internal fun Modifier.todayThreadAnchor(state: TodayThreadState, key: Any): Modifier =
    this then TodayThreadAnchorElement(state, key)

private data class TodayThreadAnchorElement(
    val state: TodayThreadState,
    val key: Any,
) : ModifierNodeElement<TodayThreadAnchorNode>() {
    override fun create() = TodayThreadAnchorNode(state, key)
    override fun update(node: TodayThreadAnchorNode) = node.update(state, key)
    override fun InspectorInfo.inspectableProperties() { name = "todayThreadAnchor"; properties["key"] = key }
}

internal class TodayThreadAnchorNode(
    private var state: TodayThreadState,
    private var key: Any,
) : Modifier.Node(), LayoutAwareModifierNode, UnplacedAwareModifierNode {
    private var placed = false

    override fun onAttach() {
        state.anchors[key] = this
        state.drawing?.requestFrames()
    }

    override fun onDetach() {
        if (state.anchors[key] === this) state.anchors.remove(key)
        state.drawing?.anchorUnavailable(key)
        placed = false
        state.drawing?.requestFrames()
    }

    override fun onReset() { placed = false }

    fun update(nextState: TodayThreadState, nextKey: Any) {
        if (state === nextState && key == nextKey) return
        if (isAttached && state.anchors[key] === this) state.anchors.remove(key)
        state.drawing?.requestFrames()
        state = nextState
        key = nextKey
        placed = false
        if (isAttached) state.anchors[key] = this
        state.drawing?.requestFrames()
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        placed = true
        state.drawing?.redraw()
    }

    override fun onUnplaced() {
        placed = false
        state.drawing?.anchorUnavailable(key)
        state.drawing?.redraw()
    }

    override fun onRemeasured(size: IntSize) { state.drawing?.requestFrames() }

    fun positionIn(destination: LayoutCoordinates): Offset {
        if (!isAttached || !placed) return Offset.Unspecified
        val coordinates = requireLayoutCoordinates()
        return destination.localPositionOf(
            coordinates,
            Offset(coordinates.size.width / 2f, coordinates.size.height / 2f),
        )
    }
}

/** One thin, continuous wave in the visible corridor. Module icons and surfaces own no branches. */
@Composable
internal fun TodayCareThread(
    listState: LazyListState,
    anchors: List<TodayThreadAnchor>,
    state: TodayThreadState,
    modifier: Modifier = Modifier,
    motionAllowed: Boolean = rememberBitavidaMotionAllowed(null),
) {
    val colors = MaterialTheme.bitavidaColors
    Box(modifier.fillMaxSize().clearAndSetSemantics { }.then(
        TodayThreadDrawElement(listState, anchors, state, motionAllowed,
            MaterialTheme.colorScheme.outline, colors.gradientStart, colors.gradientEnd),
    ))
}

private data class TodayThreadDrawElement(
    val listState: LazyListState,
    val anchors: List<TodayThreadAnchor>,
    val state: TodayThreadState,
    val motion: Boolean,
    val outline: Color,
    val accent: Color,
    val highlight: Color,
) : ModifierNodeElement<TodayThreadDrawNode>() {
    override fun create() = TodayThreadDrawNode(listState, anchors, state, motion, outline, accent, highlight)
    override fun update(node: TodayThreadDrawNode) =
        node.update(listState, anchors, state, motion, outline, accent, highlight)
    override fun InspectorInfo.inspectableProperties() { name = "todayCareThread" }
}

internal class TodayThreadDrawNode(
    private var listState: LazyListState,
    private var metadata: List<TodayThreadAnchor>,
    private var state: TodayThreadState,
    private var motion: Boolean,
    private var outline: Color,
    private var accent: Color,
    private var highlight: Color,
) : Modifier.Node(), DrawModifierNode, ObserverModifierNode, LayoutAwareModifierNode {
    private val path = Path()
    private val revealPath = Path()
    private val trailPath = Path()
    private val mediumTrailPath = Path()
    private val brightTrailPath = Path()
    private val measure = PathMeasure()
    private val reveal = Animatable(0f)
    private var introComplete = !motion
    private val redrawClock = Animatable(0f)
    private var revealJob: Job? = null
    private var redrawJob: Job? = null
    private var trailJob: Job? = null
    private var confirmationJob: Job? = null
    private var focusJob: Job? = null
    private val focusProgress = Animatable(0f)
    private val focusGate = TodayThreadFocusGate()
    private var focusActive = false
    private var focusKey: Any? = null
    private var viewportHeight = 0f
    private val trailOpacity = Animatable(0f)
    private val confirmationProgress = Animatable(0f)
    private var trailVisible = false
    private var confirmationActive = false
    private var confirmationKey: String? = null
    private val travel = TodayThreadTravel()
    private var measuredLength = 0f
    private val phase = TodayThreadScrollPhase()
    private val wave = TodayThreadWave()
    private val keys = ArrayList<Any>()
    private var offsets = FloatArray(24)
    private var knots = FloatArray(128)
    private var scrolling = false
    private var firstVisibleIndex = 0
    private var strokeDensity = 0f
    private var lineStroke = Stroke(2.3f, cap = StrokeCap.Round)
    private var softStroke = Stroke(7f, cap = StrokeCap.Round)
    private var glowStroke = Stroke(10f, cap = StrokeCap.Round)
    private var trailStroke = Stroke(5.5f, cap = StrokeCap.Round)
    private var headStroke = Stroke(3.3f, cap = StrokeCap.Round)
    private var pulseStroke = Stroke(1.4f, cap = StrokeCap.Round)

    override fun onAttach() {
        state.drawing = this
        observeLayout()
        if (motion && !introComplete) revealJob = coroutineScope.launch {
            reveal.animateTo(1f, tween(BitavidaFluidMotion.ThreadReveal,
                easing = FastOutSlowInEasing)) { invalidateDraw() }
            introComplete = true
            invalidateDraw()
        }
        requestFrames()
    }

    override fun onDetach() {
        if (state.drawing === this) state.drawing = null
        revealJob?.cancel()
        redrawJob?.cancel()
        trailJob?.cancel()
        confirmationJob?.cancel()
        focusJob?.cancel()
        trailVisible = false
        confirmationActive = false
        focusActive = false
    }

    override fun onObservedReadsChanged() {
        observeLayout()
        if (scrolling) redraw() else requestFrames()
    }

    private fun observeLayout() {
        val wasScrolling = scrolling
        observeReads {
            val layout = listState.layoutInfo
            scrolling = listState.isScrollInProgress
            firstVisibleIndex = listState.firstVisibleItemIndex
            val items = layout.visibleItemsInfo
            if (offsets.size < items.size) offsets = FloatArray(items.size * 2)
            keys.clear()
            for (index in items.indices) {
                keys.add(items[index].key)
                offsets[index] = items[index].offset.toFloat()
            }
            phase.update(keys, offsets, items.size, scrolling)
        }
        if (motion) {
            travel.advance(phase.lastDelta, measuredLength)
            focusGate.advance(phase.lastDelta)
            evaluateFocus()
            if (scrolling && (!wasScrolling || !trailVisible)) showTrail()
            else if (!scrolling && wasScrolling) fadeTrail()
        }
    }

    fun update(
        nextListState: LazyListState, anchors: List<TodayThreadAnchor>,
        nextState: TodayThreadState, nextMotion: Boolean, nextOutline: Color, nextAccent: Color,
        nextHighlight: Color,
    ) {
        if (state !== nextState) {
            if (state.drawing === this) state.drawing = null
            state = nextState
            if (isAttached) state.drawing = this
        }
        listState = nextListState
        metadata = anchors
        if (motion != nextMotion) {
            revealJob?.cancel()
            revealJob = null
            trailJob?.cancel()
            confirmationJob?.cancel()
            focusJob?.cancel()
            focusActive = false
            focusGate.requireScroll()
            trailVisible = false
            confirmationActive = false
            // A changed accessibility preference ends the introduction for this session.
            introComplete = true
        }
        motion = nextMotion
        outline = nextOutline
        accent = nextAccent
        highlight = nextHighlight
        if (isAttached) {
            observeLayout()
            requestFrames()
        }
    }

    private fun showTrail() {
        trailJob?.cancel()
        trailVisible = true
        trailJob = coroutineScope.launch {
            trailOpacity.animateTo(1f, tween(BitavidaFluidMotion.Press)) { invalidateDraw() }
        }
    }

    private fun fadeTrail() {
        trailJob?.cancel()
        trailJob = coroutineScope.launch {
            trailOpacity.animateTo(0f, tween(BitavidaFluidMotion.Release)) { invalidateDraw() }
            trailVisible = false
            invalidateDraw()
        }
    }

    private fun sectionY(key: Any): Float =
        state.anchors[key]?.positionIn(requireLayoutCoordinates())?.y ?: Float.NaN

    fun anchorUnavailable(key: Any) {
        if (focusActive && focusKey == key) {
            focusJob?.cancel()
            focusActive = false
            redraw()
        }
        if (confirmationActive && confirmationKey == key) {
            confirmationJob?.cancel()
            confirmationActive = false
            redraw()
        }
    }

    fun confirmSection(key: String) {
        if (!isAttached || !motion ||
            metadata.none { it.key == key && it.kind == TodayThreadAnchorKind.Section }) return
        val y = sectionY(key)
        if (!y.isFinite() || y !in wave.rangeStart..wave.rangeEnd) return
        confirmationJob?.cancel()
        focusJob?.cancel()
        focusActive = false
        confirmationKey = key
        confirmationActive = true
        confirmationJob = coroutineScope.launch {
            confirmationProgress.snapTo(0f)
            confirmationProgress.animateTo(1f,
                tween(BitavidaFluidMotion.ConfirmationTransition, easing = FastOutSlowInEasing)) {
                invalidateDraw()
            }
            confirmationActive = false
            invalidateDraw()
        }
    }

    private fun evaluateFocus() {
        if (!isAttached || !motion || viewportHeight <= 0f || strokeDensity <= 0f ||
            confirmationActive) return
        val focusY = viewportHeight * 0.30f
        val headY = if (scrolling && trailVisible && measuredLength > 0f) {
            measure.getPosition(measuredLength * travel.fraction).y
        } else Float.NaN
        var candidate: Any? = null
        var strongest = 0.55f
        for (index in metadata.indices) {
            val anchor = metadata[index]
            if (anchor.kind != TodayThreadAnchorKind.Section) continue
            val y = sectionY(anchor.key)
            if (!y.isFinite() || y !in wave.rangeStart..wave.rangeEnd) continue
            val focus = threadNodePassage(y - focusY, 1f, 40f * strokeDensity)
            val passage = if (headY.isFinite()) {
                threadNodePassage(y - headY, 1f, 24f * strokeDensity)
            } else 0f
            val strength = maxOf(focus, passage)
            if (strength > strongest) {
                strongest = strength
                candidate = anchor.key
            }
        }
        if (!focusGate.consider(candidate, 32f * strokeDensity) || candidate == null) return
        focusJob?.cancel()
        focusKey = candidate
        focusActive = true
        focusJob = coroutineScope.launch {
            focusProgress.snapTo(0f)
            focusProgress.animateTo(1f,
                tween(BitavidaFluidMotion.Selection, easing = FastOutSlowInEasing)) {
                invalidateDraw()
            }
            focusActive = false
            invalidateDraw()
        }
    }

    private fun distanceAtY(y: Float): Float {
        var lower = 0f
        var upper = measure.length
        repeat(12) {
            val middle = (lower + upper) / 2f
            if (measure.getPosition(middle).y < y) lower = middle else upper = middle
        }
        return (lower + upper) / 2f
    }

    private fun DrawScope.drawFlow(head: Float, direction: Int, opacity: Float) {
        fun extract(destination: Path, length: Float) {
            destination.rewind()
            val bounds = todayThreadTrailBounds(head, measure.length, length, direction)
            if (bounds.y > bounds.x) measure.getSegment(bounds.x, bounds.y, destination, true)
        }
        extract(trailPath, 80f * density)
        extract(mediumTrailPath, 48f * density)
        extract(brightTrailPath, 12f * density)
        drawPath(trailPath, accent.copy(alpha = 0.18f * opacity), style = glowStroke)
        drawPath(mediumTrailPath, accent.copy(alpha = 0.50f * opacity), style = trailStroke)
        drawPath(brightTrailPath, highlight.copy(alpha = 0.98f * opacity), style = headStroke)
        val point = measure.getPosition(head.coerceIn(0f, measure.length))
        drawCircle(highlight.copy(alpha = 0.96f * opacity), 2.5f * density, point)
    }

    fun redraw() { if (isAttached) invalidateDraw() }

    /** Finite draw invalidation follows actual graphics-layer positions; no coordinate interpolation. */
    fun requestFrames() {
        if (!isAttached) return
        invalidateDraw()
        redrawJob?.cancel()
        if (!motion || scrolling) return
        redrawJob = coroutineScope.launch {
            redrawClock.snapTo(0f)
            redrawClock.animateTo(1f, tween(BitavidaFluidMotion.ThreadReveal)) {
                evaluateFocus()
                invalidateDraw()
            }
        }
    }

    override fun onRemeasured(size: IntSize) { requestFrames() }

    override fun ContentDrawScope.draw() {
        val coordinates = requireLayoutCoordinates()
        val firstSectionKey = todayThreadFirstSectionKey(metadata)
        if (firstSectionKey == null) {
            drawContent()
            return
        }
        viewportHeight = size.height
        val density = density
        if (strokeDensity != density) {
            strokeDensity = density
            lineStroke = Stroke(2.3f * density, cap = StrokeCap.Round)
            softStroke = Stroke(7f * density, cap = StrokeCap.Round)
            glowStroke = Stroke(10f * density, cap = StrokeCap.Round)
            trailStroke = Stroke(5.5f * density, cap = StrokeCap.Round)
            headStroke = Stroke(3.3f * density, cap = StrokeCap.Round)
            pulseStroke = Stroke(1.4f * density, cap = StrokeCap.Round)
        }
        val corridor = BitavidaSpacing.ScreenHorizontal.toPx() + BitavidaSpacing.Medium.toPx()
        val base = if (layoutDirection == LayoutDirection.Rtl) size.width - corridor else corridor
        wave.configure(base, density, phase.worldOffset, layoutDirection == LayoutDirection.Rtl)
        wave.clearNodes()
        var actualStart = Float.NaN
        var actualEnd = Float.NaN
        for (index in metadata.indices) {
            val anchor = metadata[index]
            if (anchor.kind == TodayThreadAnchorKind.Surface || anchor.kind == TodayThreadAnchorKind.Header) continue
            val node = state.anchors[anchor.key] ?: continue
            val position = node.positionIn(coordinates)
            if (!position.x.isFinite() || !position.y.isFinite()) continue
            if (anchor.key == firstSectionKey) actualStart = position.y
            if (anchor.kind == TodayThreadAnchorKind.End) actualEnd = position.y
            wave.addNode(position.x, position.y)
        }
        if (wave.visibleRange(size.height, actualStart, actualEnd, firstVisibleIndex)) {
            val expected = ceil((wave.rangeEnd - wave.rangeStart) / (16f * density)).toInt() +
                wave.nodeCount + 4
            if (knots.size < expected) knots = FloatArray(expected * 2)
            val count = wave.fillKnots(knots)
            path.rewind()
            wave.sample(knots[0])
            var previousY = knots[0]
            var previousX = wave.x
            var previousSlope = wave.derivative
            path.moveTo(previousX, previousY)
            for (index in 1 until count) {
                val y = knots[index]
                wave.sample(y)
                val span = y - previousY
                path.cubicTo(previousX + previousSlope * span / 3f, previousY + span / 3f,
                    wave.x - wave.derivative * span / 3f, y - span / 3f, wave.x, y)
                previousY = y
                previousX = wave.x
                previousSlope = wave.derivative
            }
            measure.setPath(path, false)
            measuredLength = measure.length
            clipRect {
                drawPath(path, outline.copy(alpha = 0.22f), style = softStroke)
                drawPath(path, outline.copy(alpha = 0.78f), style = lineStroke)
                revealPath.rewind()
                val revealFraction = if (introComplete || !motion) 1f else reveal.value
                measure.getSegment(0f, measure.length * revealFraction, revealPath, true)
                drawPath(revealPath, accent.copy(alpha = 0.56f),
                    style = lineStroke)
                if (motion && trailVisible && measuredLength > 0f) {
                    drawFlow(measuredLength * travel.fraction, travel.direction, trailOpacity.value)
                }
                val focused = focusKey
                if (motion && focusActive && focused != null && !confirmationActive) {
                    val y = sectionY(focused)
                    if (y in wave.rangeStart..wave.rangeEnd) {
                        val progress = focusProgress.value
                        val envelope = sin(progress * PI).toFloat().coerceAtLeast(0f)
                        val center = distanceAtY(y)
                        drawFlow((center + (progress - 0.5f) * 20f * density * travel.direction)
                            .coerceIn(0f, measuredLength), travel.direction, 0.45f * envelope)
                        drawCircle(accent.copy(alpha = 0.18f * envelope),
                            (8f + 2f * envelope) * density, measure.getPosition(center), style = pulseStroke)
                    }
                }
                val confirmed = confirmationKey
                if (motion && confirmationActive && confirmed != null) {
                    val y = sectionY(confirmed)
                    if (y in wave.rangeStart..wave.rangeEnd) {
                        val progress = confirmationProgress.value
                        val envelope = sin(progress * PI).toFloat().coerceAtLeast(0f)
                        val center = distanceAtY(y)
                        drawFlow((center + (progress - 0.5f) * 48f * density)
                            .coerceIn(0f, measuredLength), 1, envelope)
                        val point = measure.getPosition(center)
                        drawCircle(accent.copy(alpha = 0.28f * envelope),
                            (8f + 3f * envelope) * density, point, style = pulseStroke)
                    }
                }
            }
        }
        drawContent()
    }
}

/** The first actual module node owns the start; a legacy header or a footer cannot create it. */
internal fun todayThreadFirstSectionKey(anchors: List<TodayThreadAnchor>): Any? =
    anchors.firstOrNull { it.kind == TodayThreadAnchorKind.Section }?.key

/** A focus can fire once initially, then only after real scrolling leaves and re-enters it. */
internal class TodayThreadFocusGate {
    private var firedKey: Any? = null
    private var direction = 1
    private var firedDirection = 1
    private var scrollRevision = 0L
    private var firedRevision = 0L
    private var consideredRevision = -1L
    private var sinceFire = 0f
    private var leftFocus = false
    private var movementRequired = false

    fun advance(delta: Float) {
        if (!delta.isFinite() || delta == 0f) return
        scrollRevision++
        sinceFire += abs(delta)
        direction = if (delta > 0f) 1 else -1
    }

    fun requireScroll() {
        movementRequired = true
        sinceFire = 0f
        firedRevision = scrollRevision
        consideredRevision = scrollRevision
    }

    fun consider(candidate: Any?, minimumTravel: Float): Boolean {
        val freshScroll = scrollRevision != consideredRevision
        consideredRevision = scrollRevision
        val moved = scrollRevision > firedRevision
        if (freshScroll && moved && candidate != firedKey) leftFocus = true
        if (candidate == null) return false
        val enoughScroll = freshScroll && moved && sinceFire >= minimumTravel.coerceAtLeast(0f)
        if (movementRequired && !enoughScroll) return false
        if (firedKey != null && !(enoughScroll &&
                (candidate != firedKey || leftFocus && direction != firedDirection))) return false
        firedKey = candidate
        firedDirection = direction
        firedRevision = scrollRevision
        sinceFire = 0f
        leftFocus = false
        movementRequired = false
        return true
    }
}

/** Pure scroll history: changing the first visible key does not reset the global wave's phase. */
internal class TodayThreadScrollPhase {
    var worldOffset = 0.0
        private set
    var lastDelta = 0f
        private set
    private val previousKeys = ArrayList<Any>()
    private var previousOffsets = FloatArray(24)

    fun update(keys: List<Any>, offsets: FloatArray, count: Int, scrolling: Boolean) {
        lastDelta = 0f
        if (scrolling) {
            var found = false
            for (index in 0 until count) {
                for (previous in previousKeys.indices) {
                    if (keys[index] == previousKeys[previous]) {
                        val delta = previousOffsets[previous] - offsets[index]
                        if (delta.isFinite()) {
                            worldOffset += delta
                            lastDelta = delta
                        }
                        found = true
                        break
                    }
                }
                if (found) break
            }
        }
        if (previousOffsets.size < count) previousOffsets = FloatArray(count * 2)
        previousKeys.clear()
        for (index in 0 until count) {
            previousKeys.add(keys[index])
            previousOffsets[index] = offsets[index]
        }
    }
}

/**
 * Global sine + compact C2 correction windows. Actual marker centers are exact knots with
 * vertical tangents. Losing an unrelated lazy heading never changes the sine period or phase.
 */
internal class TodayThreadWave {
    private var base = 32f
    private var amplitude = 6f
    private var period = 240f
    private var influence = 48f
    private var spacing = 16f
    private var worldOffset = 0.0
    private var nodeX = FloatArray(12)
    private var nodeY = FloatArray(12)
    var nodeCount = 0
        private set
    var x = 0f
        private set
    var derivative = 0f
        private set
    var rangeStart = 0f
        private set
    var rangeEnd = 0f
        private set

    fun configure(baseX: Float, density: Float = 1f, scroll: Double = 0.0, rtl: Boolean = false) {
        val scale = if (density.isFinite() && density > 0f && (240f * density).isFinite()) density else 1f
        base = if (baseX.isFinite()) baseX else 32f * scale
        amplitude = 6f * scale * if (rtl) -1f else 1f
        period = 240f * scale
        influence = 48f * scale
        spacing = 16f * scale
        // Both the wave and 16 dp knot grid repeat after 240 dp; normalization avoids drift.
        worldOffset = if (scroll.isFinite()) scroll % period else 0.0
    }

    fun clearNodes() { nodeCount = 0 }

    fun addNode(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        if (nodeCount == nodeX.size) {
            nodeX = nodeX.copyOf(nodeCount * 2)
            nodeY = nodeY.copyOf(nodeCount * 2)
        }
        var insertion = nodeCount
        while (insertion > 0 && nodeY[insertion - 1] > y) {
            nodeX[insertion] = nodeX[insertion - 1]
            nodeY[insertion] = nodeY[insertion - 1]
            insertion--
        }
        nodeX[insertion] = x
        nodeY[insertion] = y
        nodeCount++
    }

    fun visibleRange(height: Float, start: Float, end: Float, firstVisibleIndex: Int): Boolean {
        if (!height.isFinite() || height <= 0f || (!start.isFinite() && firstVisibleIndex == 0)) return false
        rangeStart = if (start.isFinite()) start.coerceAtLeast(0f) else 0f
        rangeEnd = if (end.isFinite()) end.coerceAtMost(height) else height
        return rangeEnd > rangeStart
    }

    fun sample(y: Float) {
        var baseline = base
        var baselineSlope = 0f
        if (nodeCount > 0) {
            baseline = if (y <= nodeY[0]) nodeX[0] else nodeX[nodeCount - 1]
            for (index in 0 until nodeCount - 1) {
                if (y >= nodeY[index] && y <= nodeY[index + 1]) {
                    val span = nodeY[index + 1] - nodeY[index]
                    val t = if (span > 0f) (y - nodeY[index]) / span else 0f
                    val delta = nodeX[index + 1] - nodeX[index]
                    baseline = nodeX[index] + delta * t * t * (3f - 2f * t)
                    baselineSlope = if (span > 0f) delta * 6f * t * (1f - t) / span else 0f
                    break
                }
            }
        }
        var envelope = 1f
        var envelopeSlope = 0f
        for (index in 0 until nodeCount) {
            val t = (y - nodeY[index]) / influence
            if (abs(t) < 1f) {
                val square = 1f - t * t
                val factor = 1f - square * square * square
                val factorSlope = 6f * t * square * square / influence
                envelopeSlope = envelopeSlope * factor + envelope * factorSlope
                envelope *= factor
            }
        }
        val frequency = (2.0 * PI / period)
        val angle = (y + worldOffset) * frequency
        val sine = sin(angle).toFloat()
        x = baseline + amplitude * sine * envelope
        derivative = baselineSlope + amplitude *
            (frequency.toFloat() * cos(angle).toFloat() * envelope + sine * envelopeSlope)
    }

    fun fillKnots(destination: FloatArray): Int {
        var count = 0
        destination[count++] = rangeStart
        var world = ceil((rangeStart + worldOffset) / spacing) * spacing
        if (world - worldOffset <= rangeStart + 0.001f) world += spacing
        while (world - worldOffset < rangeEnd) {
            destination[count++] = (world - worldOffset).toFloat()
            world += spacing
        }
        for (index in 0 until nodeCount) {
            if (nodeY[index] > rangeStart && nodeY[index] < rangeEnd) destination[count++] = nodeY[index]
        }
        destination[count++] = rangeEnd
        java.util.Arrays.sort(destination, 0, count)
        var unique = 1
        for (index in 1 until count) {
            if (destination[index] - destination[unique - 1] > 0.001f) destination[unique++] = destination[index]
        }
        return unique
    }
}

/** Persistent scroll highlight: bounded movement, no wrap or reset when a new gesture starts. */
internal class TodayThreadTravel {
    var fraction = 0f
        private set
    var direction = 1
        private set

    fun advance(delta: Float, pathLength: Float) {
        if (!delta.isFinite() || !pathLength.isFinite() || pathLength <= 0f || delta == 0f) return
        fraction = (fraction + delta / pathLength * 0.60f).coerceIn(0f, 1f)
        direction = if (delta > 0f) 1 else -1
    }
}

/** Packed start/stop distances on the measured path. The tail always follows behind its head. */
internal fun todayThreadTrailBounds(
    headDistance: Float,
    pathLength: Float,
    tailLength: Float,
    direction: Int,
): Offset {
    if (!headDistance.isFinite() || !pathLength.isFinite() || !tailLength.isFinite() ||
        pathLength <= 0f || tailLength <= 0f) return Offset.Zero
    val head = headDistance.coerceIn(0f, pathLength)
    return if (direction >= 0) Offset((head - tailLength).coerceAtLeast(0f), head)
    else Offset(head, (head + tailLength).coerceAtMost(pathLength))
}
