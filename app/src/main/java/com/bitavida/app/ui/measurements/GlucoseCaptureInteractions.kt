package com.bitavida.app.ui.measurements

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import com.bitavida.app.ui.theme.BitavidaSpacing
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.ConfirmedGlucoseOccurrence
import com.bitavida.app.domain.model.GlucoseRegistrationContext
import com.bitavida.app.domain.usecase.GlucoseDraft
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.bitavidaFluidInteraction

private fun glucoseDuration(motionAllowed: Boolean) =
    if (motionAllowed) BitavidaFluidMotion.Selection else 0

/** Draw-only focus feedback; the surface and its text keep their measured size. */
@Composable
internal fun Modifier.glucoseActiveOutline(
    active: Boolean,
    motionAllowed: Boolean,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.primary,
): Modifier {
    val progress = animateFloatAsState(
        if (active) 1f else 0f, tween(glucoseDuration(motionAllowed)),
        label = "glucose_value_focus",
    )
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            drawOutline(outline, color.copy(alpha = color.alpha * progress.value),
                style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
internal fun GlucoseCaptureChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    motionAllowed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    val scheme = MaterialTheme.colorScheme
    val duration = glucoseDuration(motionAllowed)
    val background by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surface,
        tween(duration), label = "glucose_chip_surface",
    )
    val foreground by animateColorAsState(
        if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        tween(duration), label = "glucose_chip_text",
    )
    val border by animateColorAsState(
        if (selected) scheme.primary else scheme.outline,
        tween(duration), label = "glucose_chip_outline",
    )
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .bitavidaFluidInteraction(source, enabled, motionAllowed, shape = shape, restingElevationDp = 0f),
        interactionSource = source,
        shape = shape,
        border = BorderStroke(1.dp, border),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = background, selectedContainerColor = background,
            labelColor = foreground, selectedLabelColor = foreground,
        ),
    )
}

@Composable
internal fun GlucoseContextHint(draft: GlucoseDraft, motionAllowed: Boolean) {
    val target = draft.context to draft.firstMeasurement
    val duration = glucoseDuration(motionAllowed)
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            val enter = if (motionAllowed) fadeIn(tween(duration)) else EnterTransition.None
            val exit = if (motionAllowed) fadeOut(tween(BitavidaFluidMotion.Press)) else ExitTransition.None
            (enter togetherWith exit).using(SizeTransform { _, _ -> tween(duration) })
        },
        label = "glucose_capture_context",
    ) { (context, first) ->
        Column(
            Modifier.fillMaxWidth().then(if ((context to first) != target)
                Modifier.clearAndSetSemantics {} else Modifier),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            when (context) {
                GlucoseRegistrationContext.Independent -> if (first) {
                    SecondaryText("Puedes comenzar sin configurar un seguimiento.")
                }
                is GlucoseRegistrationContext.Additional -> {
                    Text("Medición adicional", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    SecondaryText("No sustituye una medición programada.")
                }
                is GlucoseRegistrationContext.Scheduled -> {
                    Text("Medición programada", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    SecondaryText(context.occurrence.captureScheduleLabel())
                    context.occurrence.expectedContext?.let { SecondaryText("Momento previsto: " + it.captureLabel()) }
                }
            }
        }
    }
}

private fun ConfirmedGlucoseOccurrence.captureScheduleLabel(): String =
    scheduledAt.format(measurementDateTime) +
        windowEndAt?.let { " – " + it.format(measurementDateTime) }.orEmpty()

@Composable
internal fun GlucoseNoteHeader(
    expanded: Boolean,
    hasNote: Boolean,
    enabled: Boolean,
    motionAllowed: Boolean,
    onToggle: () -> Unit,
) {
    val rotation = animateFloatAsState(
        if (expanded) 90f else 0f,
        tween(glucoseDuration(motionAllowed)), label = "glucose_note_chevron",
    )
    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .semantics { stateDescription = if (expanded) "Expandido" else "Contraído" },
    ) {
        Row(
            Modifier.padding(
                horizontal = BitavidaSpacing.Small,
                vertical = BitavidaSpacing.ExtraSmall,
            ),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Nota o motivo",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            SecondaryText(if (hasNote) "Agregada" else "Opcional")
            androidx.compose.foundation.layout.Box(
                Modifier.graphicsLayer { rotationZ = rotation.value },
            ) {
                MeasurementIcon(R.drawable.ic_v2_chevron, 18)
            }
        }
    }
}

@Composable
internal fun GlucoseCaptureReview(draft: GlucoseDraft, ready: Boolean, motionAllowed: Boolean) {
    val duration = glucoseDuration(motionAllowed)
    AnimatedVisibility(
        visible = ready,
        enter = fadeIn(tween(duration)) + expandVertically(tween(duration, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
        exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                "Listo para guardar · " + draft.reading.trim() + " mg/dL",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            SecondaryText(
                (draft.moment?.captureLabel() ?: "") + " · " +
                    draft.actualAt.format(measurementDateTime),
            )
        }
    }
}
