package com.bitavida.app.ui.demo

import androidx.annotation.DrawableRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.lerp
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.theme.bitavidaColors

/** A secondary action stays explicitly reachable by touch, keyboard and accessibility services. */
internal data class TodayCompactAction(val label: String, val onClick: () -> Unit)

/** Presentation of an existing occurrence; this component never creates or resolves one itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayCompactActivity(
    title: String,
    detail: String,
    @DrawableRes iconRes: Int,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit,
    secondaryActions: List<TodayCompactAction>,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    confirmed: Boolean = false,
    confirmationProgress: () -> Float = { if (confirmed) 1f else 0f },
    confirmedLabel: String = "Se tomó",
) {
    val haptics = LocalHapticFeedback.current
    val motion = com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed(override = null)
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var sheetVisible by remember { mutableStateOf(false) }
    val active = enabled && !confirmed
    val visualProgress: () -> Float = { if (!confirmed) 0f else if (motion) confirmationProgress().coerceIn(0f, 1f) else 1f }
    val hasOptions = onLongPress != null || secondaryActions.isNotEmpty()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val confirmedColor = MaterialTheme.bitavidaColors.paleBlue
    val openOptions = {
        if (onLongPress != null) onLongPress() else if (secondaryActions.isNotEmpty()) sheetVisible = true
    }
    val longPress = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        openOptions()
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().drawBehind {
            drawRect(lerp(surfaceColor, confirmedColor, (visualProgress() / 0.25f).coerceIn(0f, 1f)))
        }.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            val stacked = maxWidth < 290.dp || LocalDensity.current.fontScale >= 1.5f
            val identity: @Composable (Modifier) -> Unit = { identityModifier ->
                Row(identityModifier, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.bitavidaColors.paleBlue) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            MeasurementIcon(iconRes, 24, MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val action: @Composable () -> Unit = {
                val shape = MaterialTheme.shapes.medium
                Surface(
                    modifier = Modifier.heightIn(min = 48.dp)
                        .bitavidaFluidInteraction(source, active, motion, shape = shape, restingElevationDp = 0f)
                        .semantics {
                            contentDescription = "${if (confirmed) confirmedLabel else actionLabel}: $title"
                            customActions = if (!active) emptyList() else if (secondaryActions.isNotEmpty())
                                secondaryActions.map { option ->
                                    CustomAccessibilityAction(option.label) { option.onClick(); true }
                                }
                            else if (onLongPress != null) listOf(CustomAccessibilityAction("Opciones de $title") {
                                openOptions(); true
                            }) else emptyList()
                        }
                        .onPreviewKeyEvent { event ->
                            if (active && hasOptions && event.type == KeyEventType.KeyUp &&
                                (event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed))) {
                                openOptions()
                                true
                            } else false
                        }
                        .combinedClickable(
                            interactionSource = source,
                            indication = ripple(),
                            enabled = active,
                            role = Role.Button,
                            hapticFeedbackEnabled = false,
                            onLongClickLabel = if (hasOptions) "Opciones de $title" else null,
                            onLongClick = if (hasOptions) longPress else null,
                            onClick = onAction,
                        ),
                    shape = shape,
                    color = if (active || confirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (active || confirmed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Box(Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center) {
                        // Keep the original action's measured footprint while its confirmation replaces it.
                        // This prevents text reflow and a separate layout jump before the row contracts.
                        if (confirmed) Text(actionLabel, Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {},
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (confirmed) TodayMedicationConfirmationCheck(visualProgress, MaterialTheme.colorScheme.onPrimary)
                            Text(if (confirmed) confirmedLabel else actionLabel, style = MaterialTheme.typography.labelLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
            if (stacked) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    identity(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { action() }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    identity(Modifier.weight(1f))
                    action()
                }
            }
        }
    }
    if (sheetVisible && secondaryActions.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                secondaryActions.forEach { option ->
                    com.bitavida.app.ui.medical.CurrentDetailButton(
                        com.bitavida.app.ui.medical.DetailButtonKind.OUTLINED,
                        onClick = { sheetVisible = false; option.onClick() },
                        modifier = Modifier.fillMaxWidth(),
                        strongOutline = true,
                        motionAllowedOverride = motion,
                    ) { Text(option.label) }
                }
            }
        }
    }
}
