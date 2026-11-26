package com.bitavida.app.ui.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import kotlinx.coroutines.delay

/** Select the final activity window before animating an outgoing committed occurrence. */
internal fun activityTargetKeys(
    current: List<String>, previous: List<String>, expanded: Boolean,
    previousExpanded: Boolean, exitingKeys: Set<String>,
): List<String> {
    val remainingCurrent = current.filterNot { it in exitingKeys }
    val remainingPrevious = previous.filterNot { it in exitingKeys }
    return (if (expanded) remainingCurrent else remainingCurrent.take(5)) +
        if (previousExpanded) remainingPrevious else emptyList()
}

/**
 * Retain only existing occurrences while their presentation exits. Rows removed from the
 * authoritative projection are never resurrected, and reduced motion removes old rows at once.
 * Ordering follows the current real projection rather than a stale visual snapshot.
 */
internal fun reconcileActivityTransitionKeys(
    available: List<String>,
    target: List<String>,
    retained: List<String>,
    motion: Boolean,
): List<String> {
    val wanted = (if (motion) retained + target else target).toHashSet()
    return available.asSequence().filter { it in wanted }.distinct().toList()
}

/**
 * A measured row that retains its slot through the exit. The parent must keep the item and its
 * thread key until [onHidden] fires. Limit [animate] to rows in or near the viewport so large
 * expansions do not schedule hundreds of simultaneous animations.
 */
@Composable
internal fun TodayActivityReveal(
    visible: Boolean,
    ordinal: Int,
    motion: Boolean,
    animate: Boolean,
    onHidden: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transition = remember { MutableTransitionState(visible && (!motion || !animate)) }
    var hasAppeared by remember { mutableStateOf(transition.currentState) }
    var hadVisibleIntent by remember { mutableStateOf(visible) }
    val hiddenCallback by rememberUpdatedState(onHidden)
    LaunchedEffect(visible, motion, animate) {
        if (visible) {
            hadVisibleIntent = true
            if (motion && animate) delay(ordinal.coerceIn(0, 6) * 24L)
            hasAppeared = true
        } else if (hadVisibleIntent && !hasAppeared && !transition.currentState) {
            hadVisibleIntent = false
            hiddenCallback()
        }
        transition.targetState = visible
    }
    LaunchedEffect(transition.currentState, transition.targetState, transition.isIdle) {
        if (hasAppeared && transition.isIdle && !transition.currentState && !transition.targetState) {
            hasAppeared = false
            hadVisibleIntent = false
            hiddenCallback()
        }
    }
    val duration = if (motion && animate) BitavidaFluidMotion.StateTransition else 0
    AnimatedVisibility(
        visibleState = transition,
        modifier = modifier,
        enter = expandVertically(tween(duration)) + fadeIn(tween(duration)) +
            slideInVertically(tween(duration)) { it / 10 },
        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
    ) {
        // Room for soft elevation during animated height changes.
        Box(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

/** Same official outlined action and tactile feedback as the other care controls. */
@Composable
internal fun TodayExpansionButton(
    expanded: Boolean,
    extraCount: Int,
    onToggle: () -> Unit,
    motion: Boolean,
    modifier: Modifier = Modifier,
    previous: Boolean = false,
) {
    val angle by animateFloatAsState(
        targetValue = if (expanded) -90f else 90f,
        animationSpec = tween(if (motion) BitavidaFluidMotion.Selection else 0),
        label = "estado de expansión",
    )
    CurrentDetailButton(
        DetailButtonKind.OUTLINED, onToggle,
        modifier = modifier.fillMaxWidth().semantics {
            stateDescription = if (expanded) "Actividades expandidas" else "Actividades contraídas"
        },
        motionAllowedOverride = motion,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val label = when {
            previous && expanded -> "Ocultar pendientes anteriores"
            previous -> "Ver $extraCount " + if (extraCount == 1) "pendiente anterior" else "pendientes anteriores"
            expanded -> "Mostrar menos actividades"
            else -> "Ver $extraCount " + if (extraCount == 1) "actividad más" else "actividades más"
        }
        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.graphicsLayer { rotationZ = angle }) {
            MeasurementIcon(R.drawable.ic_v2_chevron, 18, MaterialTheme.colorScheme.primary)
        }
    }
}
