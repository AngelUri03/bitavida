package com.bitavida.app.ui.demo

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors

/**
 * Contiguous lazy pieces form one connected surface for a module. Only the first/last piece rounds
 * the outside corners; inner boundaries have no horizontal border or independent card shape.
 * Keep neighboring pieces adjacent and use stable lazy keys so individual activities can animate.
 */
@Composable
internal fun TodaySectionPiece(
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The approved BitavidaConnectedSurface geometry, split only at lazy item boundaries.
    val shape = RoundedCornerShape(
        topStart = if (first) 10.dp else 0.dp,
        topEnd = if (first) 28.dp else 0.dp,
        bottomEnd = if (last) 28.dp else 0.dp,
        bottomStart = if (last) 22.dp else 0.dp,
    )
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Surface(
        modifier = modifier.fillMaxWidth()
            .clip(shape)
            .drawWithCache {
                val edge = shape.createOutline(size, layoutDirection, this)
                // The shared shape clips the outside half, leaving precisely one dp inside.
                val stroke = Stroke(width = 2.dp.toPx())
                val sideWidth = 2.dp.toPx()
                val cornerExtent = 29.dp.toPx()
                onDrawWithContent {
                    drawContent()
                    if (first && last) {
                        drawOutline(edge, outline, style = stroke)
                    } else {
                        clipRect(right = sideWidth) { drawOutline(edge, outline, style = stroke) }
                        clipRect(left = size.width - sideWidth) { drawOutline(edge, outline, style = stroke) }
                        if (first) clipRect(bottom = cornerExtent) { drawOutline(edge, outline, style = stroke) }
                        if (last) clipRect(top = size.height - cornerExtent) { drawOutline(edge, outline, style = stroke) }
                    }
                }
            },
        shape = shape,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(content = content)
    }
}

private val TodayMedicationClockToken = Regex(
    """(?<![\p{L}\p{N}:])(?:0?[1-9]|1[0-2]):[0-5][0-9](?::[0-5][0-9])?[\p{Zs}]*(?:[aApP]\.[\p{Zs}]*[mM]\.|[aApP][mM])(?![\p{L}\p{N}.])""",
)

/** Presentation only: retain line breaks around ranges/context, but keep each clock token together. */
internal fun visualTodayMedicationSchedule(schedule: String): String =
    TodayMedicationClockToken.replace(schedule) { match ->
        buildString(match.value.length) {
            for (character in match.value) {
                append(if (Character.isSpaceChar(character)) '\u00A0' else character)
            }
        }
    }

/** One explicit registration button; optional actions remain available through native long press. */
@Composable
internal fun TodayMedicationSurface(
    id: String,
    name: String,
    amount: String,
    schedule: String,
    enabled: Boolean,
    motion: Boolean,
    onTaken: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
    confirmed: Boolean = false,
    confirmationProgress: () -> Float = { if (confirmed) 1f else 0f },
    @DrawableRes scheduleIcon: Int = R.drawable.ic_v2_clock,
) {
    val active = enabled && !saving && !confirmed
    val shape = RoundedCornerShape(10.dp, 28.dp, 28.dp, 22.dp)
    val surface = MaterialTheme.colorScheme.surface
    val confirmedSurface = MaterialTheme.colorScheme.primaryContainer
    val primary = MaterialTheme.colorScheme.primary
    val visualProgress: () -> Float = {
        if (!confirmed) 0f else if (!motion) 1f else confirmationProgress().coerceIn(0f, 1f)
    }
    val source = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val identity = "$name, $amount, $schedule"
    val visualSchedule = remember(schedule) { visualTodayMedicationSchedule(schedule) }
    val scheduleStyle = MaterialTheme.typography.bodySmall
    val scheduleIconTopPadding = with(LocalDensity.current) {
        ((scheduleStyle.lineHeight.toDp() - 16.dp) / 2f).coerceAtLeast(0.dp)
    }
    val showOptions = {
        if (active) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onOptions()
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .testTag("today_medication_taken_$id")
            .semantics(mergeDescendants = true) {
                contentDescription = when {
                    confirmed -> "Se tomó: $identity"
                    saving -> "Guardando toma: $identity"
                    else -> "Registrar toma: $identity"
                }
                customActions = if (active) listOf(
                    CustomAccessibilityAction("Opciones de $name, $schedule") { showOptions(); true },
                ) else emptyList()
            }
            .onPreviewKeyEvent { event ->
                if (active && event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed))) {
                    showOptions()
                    true
                } else false
            }
            .bitavidaFluidInteraction(source, active, motion, shape = shape, restingElevationDp = 0f)
            .combinedClickable(
                enabled = active, role = Role.Button, interactionSource = source,
                indication = androidx.compose.material3.ripple(),
                onClickLabel = "Registrar toma: $identity",
                onLongClickLabel = "Opciones de $name", hapticFeedbackEnabled = false,
                onLongClick = showOptions,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTaken()
                },
            ),
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        shadowElevation = 1.dp,
        color = surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.drawBehind {
            val colorFraction = (visualProgress() / 0.25f).coerceIn(0f, 1f)
            drawRect(lerp(surface, confirmedSurface, colorFraction))
        }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = BitavidaSpacing.Default, vertical = BitavidaSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    Surface(shape = CircleShape, color = MaterialTheme.bitavidaColors.paleBlue) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            MeasurementIcon(R.drawable.ic_v2_pill, 18, MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(amount, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.testTag("today_medication_schedule_$id"),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                        Box(Modifier.padding(top = scheduleIconTopPadding)) {
                            MeasurementIcon(scheduleIcon, 16, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(visualSchedule, style = scheduleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // A visual action band inside this one button, without a nested click/focus target.
            Row(
                Modifier.fillMaxWidth().heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                    .testTag("today_medication_action_$id")
                    .drawBehind { drawRect(primary) }
                    .padding(horizontal = BitavidaSpacing.Default, vertical = BitavidaSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small, Alignment.CenterHorizontally),
            ) {
                if (confirmed) TodayMedicationConfirmationCheck(visualProgress, MaterialTheme.colorScheme.onPrimary)
                else if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                else MeasurementIcon(R.drawable.ic_v2_plus, 18, MaterialTheme.colorScheme.onPrimary)
                Text(when { confirmed -> "Se tomó"; saving -> "Guardando…"; else -> "Registrar toma" },
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/** The vector and PathMeasure are allocated only when size or theme changes, never per frame. */
@Composable
internal fun TodayMedicationConfirmationCheck(progress: () -> Float, color: Color) {
    Spacer(
        Modifier.size(16.dp).drawWithCache {
            val check = Path().apply {
                moveTo(size.width * 0.20f, size.height * 0.51f)
                lineTo(size.width * 0.42f, size.height * 0.73f)
                lineTo(size.width * 0.82f, size.height * 0.27f)
            }
            val measure = PathMeasure().apply { setPath(check, false) }
            val segment = Path()
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                val traced = ((progress() - 0.18f) / 0.42f).coerceIn(0f, 1f)
                segment.rewind()
                if (measure.getSegment(0f, measure.length * traced, segment, startWithMoveTo = true)) {
                    drawPath(segment, color, style = stroke)
                }
            }
        },
    )
}

@Composable
internal fun TodayMeasurementSurface(
    id: String,
    title: String,
    schedule: String,
    context: String?,
    @DrawableRes iconRes: Int,
    enabled: Boolean,
    motion: Boolean,
    onRegister: () -> Unit,
    onNotPerformed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().testTag("measurement_activity_$id")
            .padding(horizontal = BitavidaSpacing.Default, vertical = BitavidaSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val identity: @Composable (Modifier) -> Unit = { identityModifier ->
                Column(identityModifier, verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                        MeasurementIcon(iconRes, 20, MaterialTheme.colorScheme.primary)
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    context?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val scheduleNeedsOwnLine = schedule.length > 12 ||
                schedule.any { it == '–' || it == '—' || it == '-' || it == '\n' }
            if (maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f || scheduleNeedsOwnLine) {
                Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                    identity(Modifier.fillMaxWidth())
                    Text(schedule, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
                    Text(schedule, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    identity(Modifier.weight(1f))
                }
            }
        }
        TodaySurfaceActions(
            "Registrar medición", "No se realizó", title + ", " + schedule,
            "today_measurement_register_$id", "today_measurement_not_performed_$id",
            enabled, motion, onRegister, onNotPerformed,
        )
    }
}

@Composable
internal fun TodayObservationSurface(
    id: String,
    title: String,
    schedule: String,
    @DrawableRes iconRes: Int,
    enabled: Boolean,
    motion: Boolean,
    onRegister: () -> Unit,
    onNotPerformed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().testTag("observation_activity_$id")
            .padding(horizontal = BitavidaSpacing.Default, vertical = BitavidaSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        Row(verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
            MeasurementIcon(iconRes, 24, MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(schedule, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TodaySurfaceActions(
            "Registrar revisión", "No se realizó", title + ", " + schedule,
            "today_observation_register_$id", "today_observation_not_performed_$id",
            enabled, motion, onRegister, onNotPerformed,
        )
    }
}

@Composable
internal fun TodayReminderSurface(
    id: String,
    title: String,
    schedule: String,
    enabled: Boolean,
    motion: Boolean,
    onDone: () -> Unit,
    onPostpone: () -> Unit,
    onDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().testTag("reminder_activity_$id")
            .padding(horizontal = BitavidaSpacing.Default, vertical = BitavidaSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(schedule, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CurrentDetailButton(
                DetailButtonKind.OUTLINED, onDetail,
                Modifier.sizeIn(minWidth = BitavidaSpacing.MinimumTouchTarget, minHeight = BitavidaSpacing.MinimumTouchTarget)
                    .testTag("today_reminder_open_$id")
                    .semantics { contentDescription = "Opciones del recordatorio: $title" },
                enabled = enabled, motionAllowedOverride = motion,
                contentPadding = PaddingValues(BitavidaSpacing.Small),
            ) {
                MeasurementIcon(R.drawable.ic_v2_more, 20, MaterialTheme.colorScheme.primary)
            }
        }
        TodaySurfaceActions(
            "Marcar como hecho", "Posponer", title + ", " + schedule,
            "today_reminder_done_$id", "today_reminder_postpone_$id",
            enabled, motion, onDone, onPostpone, primaryFilled = false,
        )
    }
}

/** The same approved action buttons as the module forms; only their compact layout is local. */
@Composable
private fun TodaySurfaceActions(
    primary: String,
    secondary: String,
    identity: String,
    primaryTag: String,
    secondaryTag: String,
    enabled: Boolean,
    motion: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    primaryFilled: Boolean = true,
) {
    val first: @Composable (Modifier) -> Unit = { actionModifier ->
        CurrentDetailButton(
            if (primaryFilled) DetailButtonKind.FILLED else DetailButtonKind.OUTLINED,
            onPrimary, actionModifier.testTag(primaryTag).semantics { contentDescription = "$primary: $identity" },
            enabled = enabled, strongOutline = !primaryFilled, motionAllowedOverride = motion,
            contentPadding = PaddingValues(horizontal = BitavidaSpacing.Small, vertical = BitavidaSpacing.Small),
        ) { Text(primary, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center) }
    }
    val second: @Composable (Modifier) -> Unit = { actionModifier ->
        CurrentDetailButton(
            DetailButtonKind.OUTLINED, onSecondary,
            actionModifier.testTag(secondaryTag).semantics { contentDescription = "$secondary: $identity" },
            enabled = enabled, strongOutline = true, motionAllowedOverride = motion,
            contentPadding = PaddingValues(horizontal = BitavidaSpacing.Small, vertical = BitavidaSpacing.Small),
        ) { Text(secondary, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 220.dp || LocalDensity.current.fontScale >= 1.5f) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                first(Modifier.weight(1f).fillMaxHeight())
                second(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
