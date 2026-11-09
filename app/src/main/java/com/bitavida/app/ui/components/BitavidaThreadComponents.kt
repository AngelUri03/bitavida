package com.bitavida.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.brand.BitavidaMark
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors

private class ThreadNodeGeometry {
    var pathFraction = 0f
}

private val LocalThreadOrder = staticCompositionLocalOf { 0 }
private val LocalThreadReveal = staticCompositionLocalOf<() -> Float> { { 1f } }
private val LocalThreadFlow = staticCompositionLocalOf<BitavidaThreadFlowState?> { null }
private val LocalThreadNodeFraction = staticCompositionLocalOf<() -> Float> { { 0f } }

enum class BitavidaNodeStyle {
    Filled,
    Open,
    Quiet,
}

/**
 * Structural journey for Bitavida. The line owns no state or clinical meaning:
 * sections and their text remain the source of truth.
 */
@Composable
fun BitavidaThreadLayout(
    modifier: Modifier = Modifier,
    flowState: BitavidaThreadFlowState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalThreadFlow provides flowState) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Section),
            content = content,
        )
    }
}

@Composable
fun BitavidaThreadSection(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    nodeStyle: BitavidaNodeStyle = BitavidaNodeStyle.Filled,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            BitavidaTimelineNode(
                style = nodeStyle,
                modifier = Modifier.padding(top = 4.dp).testTag("thread_node_${LocalThreadOrder.current}"),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = BitavidaSpacing.Small),
        ) {
            eyebrow?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            content()
        }
    }
}

@Composable
fun BitavidaTimelineNode(
    style: BitavidaNodeStyle,
    modifier: Modifier = Modifier,
) {
    val reveal = LocalThreadReveal.current
    val flow = LocalThreadFlow.current
    val order = LocalThreadOrder.current
    val nodeFraction = LocalThreadNodeFraction.current
    val colors = MaterialTheme.bitavidaColors
    val highlight = MaterialTheme.colorScheme.surface
    val centerColor = when (style) {
        BitavidaNodeStyle.Filled -> colors.gradientStart
        BitavidaNodeStyle.Open -> MaterialTheme.colorScheme.background
        BitavidaNodeStyle.Quiet -> MaterialTheme.colorScheme.outline
    }
    val border = if (style == BitavidaNodeStyle.Open) {
        BorderStroke(2.dp, colors.gradientStart)
    } else {
        null
    }

    Box(
        modifier = modifier
            .size(24.dp)
            .graphicsLayer {
                alpha = 0.30f + 0.70f * reveal()
                scaleX = 0.82f + 0.18f * reveal()
                scaleY = scaleX
            }
            .clearAndSetSemantics { }
            .drawWithCache {
                val haloStroke = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round)
                val reflectionStroke = 2.2.dp.toPx()
                onDrawWithContent {
                    drawContent()
                    val currentFlow = flow
                    val passage = if (currentFlow == null) {
                        0f
                    } else {
                        val sectionPosition = threadFlowSectionPosition(
                            currentFlow.normalizedPosition,
                            order,
                            BitavidaThreadSectionCount,
                        )
                        threadNodePassage(
                            sectionPosition = sectionPosition - nodeFraction(),
                            visibility = currentFlow.intensity,
                            radius = 0.12f,
                        )
                    }
                    if (passage > 0f) {
                        drawCircle(
                            color = colors.gradientStart.copy(alpha = 0.18f * passage),
                            radius = (7.dp + 3.5.dp * passage).toPx(),
                            style = haloStroke,
                        )
                        drawLine(
                            color = colors.gradientEnd.copy(alpha = 0.72f * passage),
                            start = Offset(center.x, center.y - 7.dp.toPx()),
                            end = Offset(center.x, center.y + 7.dp.toPx()),
                            strokeWidth = reflectionStroke,
                            cap = StrokeCap.Round,
                        )
                        drawCircle(
                            color = highlight.copy(alpha = 0.24f * passage),
                            radius = 5.2.dp.toPx(),
                        )
                    }
                }
            },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = colors.paleBlue,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = CircleShape,
                    color = centerColor,
                    border = border,
                    content = {},
                )
            }
        }
    }
}

@Composable
fun BitavidaConnectedSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.bitavidaColors.paleBlue,
    onClick: (() -> Unit)? = null,
    motionEnabledOverride: Boolean? = null,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(10.dp, 28.dp, 28.dp, 22.dp)
    val source = remember { MutableInteractionSource() }
    val allowed = rememberBitavidaMotionAllowed(motionEnabledOverride)
    val response = Modifier.bitavidaFluidInteraction(
        source = source,
        enabled = onClick != null,
        motionAllowed = allowed,
        shape = shape,
    )
    val visualModifier = if (onClick != null) {
        response
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClick = onClick,
            )
    } else {
        Modifier
            .shadow(elevation = 1.dp, shape = shape, clip = false)
            .clip(shape)
    }

    Surface(
        modifier = modifier.then(visualModifier),
        color = containerColor,
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(BitavidaSpacing.Default)
                .then(contentModifier),
            content = content,
        )
    }
}

@Composable
fun BitavidaThreadAction(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    motionEnabledOverride: Boolean? = null,
    pressedOverride: Boolean? = null,
) {
    val motionEnabled = rememberBitavidaMotionAllowed(motionEnabledOverride)
    val interactionSource = remember { MutableInteractionSource() }
    val response = Modifier.bitavidaFluidInteraction(
        interactionSource, enabled, motionEnabled, pressedOverride,
    )
    val background = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        emphasized -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val foreground = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        emphasized -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .then(response)
            .heightIn(min = 64.dp)
            .semantics {
                if (!enabled) disabled()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(18.dp, 28.dp, 28.dp, 18.dp),
        color = background,
        border = BorderStroke(
            1.dp,
            if (emphasized) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = BitavidaSpacing.Medium,
                vertical = BitavidaSpacing.Small,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        emphasized -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                        else -> MaterialTheme.bitavidaColors.paleBlue
                    },
                    content = {},
                )
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    colorFilter = ColorFilter.tint(
                        when {
                            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                            emphasized -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.primary
                        },
                    ),
                )
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
            )
        }
    }
}

@Composable
fun BitavidaProgressiveReveal(
    modifier: Modifier = Modifier,
    order: Int = 0,
    motionEnabledOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val motionEnabled = rememberBitavidaMotionAllowed(motionEnabledOverride)
    var revealed by rememberSaveable { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(motionEnabled, visible) {
        if (!motionEnabled || revealed) {
            progress.snapTo(1f)
        } else if (visible) {
            // Only decoration reveals; text and actions are available on the first frame.
            revealed = true
            progress.snapTo(0f)
            progress.animateTo(1f, tween(BitavidaFluidMotion.ThreadReveal,
                easing = FastOutSlowInEasing))
        } else {
            progress.snapTo(1f)
        }
    }
    val nodeGeometry = remember { ThreadNodeGeometry() }
    val colors = MaterialTheme.bitavidaColors
    val accent = colors.gradientStart
    val muted = MaterialTheme.colorScheme.outline
    val flow = LocalThreadFlow.current
    Box(modifier.onVisibilityChanged(minFractionVisible = 0.01f) { visible = it }
        .drawWithCache {
            val x = 20.dp.toPx()
            val nodeY = 16.dp.toPx()
            val end = size.height + BitavidaSpacing.Section.toPx()
            val middle = (nodeY + end) * 0.5f
            val bend = 8.dp.toPx()
            val path = Path().apply {
                moveTo(x, 0f)
                lineTo(x, nodeY)
                cubicTo(x + bend, nodeY + (middle - nodeY) * 0.33f,
                    x + bend, middle - (middle - nodeY) * 0.33f, x, middle)
                cubicTo(x - bend, middle + (end - middle) * 0.33f,
                    x - bend, end - (end - middle) * 0.33f, x, end)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            nodeGeometry.pathFraction = (nodeY / measure.length).coerceIn(0f, 1f)
            val revealedPath = Path()
            val longFlow = Path()
            val mediumFlow = Path()
            val brightFlow = Path()
            val soft = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
            val core = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            val flowGlow = Stroke(9.dp.toPx(), cap = StrokeCap.Round)
            val flowTrail = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
            val flowHead = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            onDrawBehind {
                drawPath(path, muted.copy(alpha = 0.18f), style = soft)
                drawPath(path, muted, style = core)
                revealedPath.rewind()
                val fraction = if (motionEnabled) progress.value else 1f
                measure.getSegment(0f, measure.length * fraction, revealedPath, true)
                drawPath(revealedPath, accent.copy(alpha = 0.50f), style = core)

                val currentFlow = flow
                val flowAlpha =
                    if (motionEnabled) currentFlow?.intensity ?: 0f else 0f
                if (flowAlpha > 0f && currentFlow != null) {
                    val headFraction = threadFlowSectionPosition(
                        currentFlow.normalizedPosition,
                        order,
                        BitavidaThreadSectionCount,
                    )
                    val forward = currentFlow.direction > 0

                    fun extract(target: Path, trailFraction: Float) {
                        target.rewind()
                        val rawStart =
                            if (forward) headFraction - trailFraction else headFraction
                        val rawStop =
                            if (forward) headFraction else headFraction + trailFraction
                        val startFraction = rawStart.coerceIn(0f, 1f)
                        val stopFraction = rawStop.coerceIn(0f, 1f)
                        if (stopFraction > startFraction) {
                            measure.getSegment(
                                measure.length * startFraction,
                                measure.length * stopFraction,
                                target,
                                true,
                            )
                        }
                    }

                    extract(longFlow, 0.34f)
                    extract(mediumFlow, 0.21f)
                    extract(brightFlow, 0.075f)
                    drawPath(
                        longFlow,
                        accent.copy(alpha = 0.13f * flowAlpha),
                        style = flowGlow,
                    )
                    drawPath(
                        mediumFlow,
                        accent.copy(alpha = 0.42f * flowAlpha),
                        style = flowTrail,
                    )
                    drawPath(
                        brightFlow,
                        accent.copy(alpha = 0.98f * flowAlpha),
                        style = flowHead,
                    )
                }
            }
        }) {
        CompositionLocalProvider(
            LocalThreadOrder provides order,
            LocalThreadReveal provides { if (motionEnabled) progress.value else 1f },
            LocalThreadNodeFraction provides { nodeGeometry.pathFraction },
        ) {
            content()
        }
    }
}

@Composable
fun BitavidaEmptyJourney(
    title: String,
    body: String,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryModifier: Modifier = Modifier,
    motionEnabledOverride: Boolean? = null,
) {
    val background = MaterialTheme.colorScheme.background
    val outline = MaterialTheme.colorScheme.outline
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 210.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics { },
            ) {
                val x = 24.dp.toPx()
                val path = Path().apply {
                    moveTo(x, 42.dp.toPx())
                    cubicTo(
                        x + 20.dp.toPx(),
                        82.dp.toPx(),
                        x - 10.dp.toPx(),
                        142.dp.toPx(),
                        x + 8.dp.toPx(),
                        size.height,
                    )
                }
                drawPath(
                    path,
                    outline,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
                listOf(86.dp, 142.dp, 196.dp).forEach { y ->
                    val center = Offset(
                        x + if (y == 142.dp) (-3).dp.toPx() else 3.dp.toPx(),
                        y.toPx(),
                    )
                    drawCircle(background, radius = 6.dp.toPx(), center = center)
                    drawCircle(
                        color = outline,
                        radius = 6.dp.toPx(),
                        center = center,
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
            BitavidaMark(
                modifier = Modifier
                    .size(40.dp)
                    .offset(x = 4.dp, y = 4.dp),
                strokeWidth = 3.2.dp,
                eventRadius = 2.5.dp,
            )
            Column(
                modifier = Modifier.padding(start = 64.dp, top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "El primer acontecimiento aparecerá aquí.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        BitavidaThreadAction(
            label = primaryLabel,
            iconRes = com.bitavida.app.R.drawable.ic_v2_plus,
            onClick = onPrimary,
            modifier = primaryModifier.fillMaxWidth(),
            emphasized = true,
            motionEnabledOverride = motionEnabledOverride,
        )
        Text(
            text = secondaryLabel,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                .clickable(role = Role.Button, onClick = onSecondary)
                .padding(BitavidaSpacing.Medium),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun BitavidaThreadNavigation(
    items: List<BitavidaNavigationItem>,
    selectedIndex: Int,
    currentSectionLabel: String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    motionEnabledOverride: Boolean? = null,
) {
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
    val colors = MaterialTheme.bitavidaColors
    val trackColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.navigationSurface)
            .drawBehind {
                drawLine(
                    color = trackColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .navigationBarsPadding()
            .padding(
                horizontal = BitavidaSpacing.Small,
                vertical = BitavidaSpacing.Small,
            ),
    ) {
        if (largeText) {
            Text(
                text = currentSectionLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = BitavidaSpacing.ExtraSmall),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clearAndSetSemantics { },
            ) {
                val segment = size.width / items.size
                drawLine(
                    color = trackColor,
                    start = Offset(segment / 2f, 20.dp.toPx()),
                    end = Offset(size.width - (segment / 2f), 20.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                items.forEachIndexed { index, item ->
                    ThreadNavigationItem(
                        item = item,
                        selected = index == selectedIndex,
                        showLabel = !largeText,
                        motionEnabledOverride = motionEnabledOverride,
                        onClick = { onSelected(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.ThreadNavigationItem(
    item: BitavidaNavigationItem,
    selected: Boolean,
    showLabel: Boolean,
    motionEnabledOverride: Boolean?,
    onClick: () -> Unit,
) {
    val allowed = rememberBitavidaMotionAllowed(motionEnabledOverride)
    val selection by animateFloatAsState(
        if (selected) 1f else 0f,
        if (allowed) tween(BitavidaFluidMotion.Selection) else snap(),
        label = "realDestinationSelection",
    )
    val source = remember { MutableInteractionSource() }
    val response = Modifier.bitavidaFluidInteraction(
        source = source,
        enabled = true,
        motionAllowed = allowed,
        shape = CircleShape,
        restingElevationDp = 0f,
    )
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .widthIn(min = BitavidaSpacing.MinimumTouchTarget)
            .heightIn(min = 60.dp)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = item.label
                this.selected = selected
            }
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(response)
                .clip(CircleShape)
                .indication(
                    interactionSource = source,
                    indication = androidx.compose.material3.ripple(
                        color = MaterialTheme.colorScheme.primary,
                        radius = 20.dp,
                    ),
                )
                .background(
                    if (selected) MaterialTheme.bitavidaColors.paleBlue
                    else MaterialTheme.bitavidaColors.navigationSurface,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { alpha = selection }
                        .align(Alignment.TopCenter)
                        .background(MaterialTheme.bitavidaColors.gradientStart, CircleShape),
                )
            }
            Image(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(color),
            )
        }
        if (showLabel) {
            Text(
                text = item.label,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
