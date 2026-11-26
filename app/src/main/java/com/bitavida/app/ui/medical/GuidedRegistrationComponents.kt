package com.bitavida.app.ui.medical

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.ui.components.BitavidaConnectedSurface
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaIconContainer
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun GuidedRegistrationHeader(
    title: String,
    personLabel: String,
    icon: Painter,
    iconScale: Float,
    focusManager: FocusManager,
    onBack: () -> Unit,
    onContinue: (() -> Unit)?,
    continueLabel: String,
    continueEnabled: Boolean,
    backEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            enabled = backEnabled,
            modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        ) {
            Text("Atrás")
        }
        onContinue?.let { continueAction ->
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    continueAction()
                },
                enabled = continueEnabled,
                modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            ) {
                Text(continueLabel)
                Image(
                    painter = painterResource(R.drawable.ic_v2_chevron),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BitavidaIconContainer(
            painter = icon,
            contentDescription = null,
            containerColor = MaterialTheme.bitavidaColors.paleBlue,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
                alpha = iconScale
            },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = personLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
@Composable
internal fun GuidedRegistrationStepProgress(
    currentStep: Int,
    totalSteps: Int,
    motionAllowed: Boolean,
    completed: Boolean = false,
) {
    val targetProgress = (currentStep + 1f) / totalSteps
    val nodeProgress = remember { Animatable(targetProgress) }
    val fillProgress = remember { Animatable(targetProgress) }
    val progressGlow = remember { Animatable(0f) }
    val gradientStart = MaterialTheme.bitavidaColors.gradientStart
    val gradientEnd = MaterialTheme.bitavidaColors.gradientEnd
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    LaunchedEffect(targetProgress, motionAllowed, completed) {
        if (completed) {
            nodeProgress.animateTo(
                1f,
                tween(
                    if (motionAllowed) BitavidaFluidMotion.Selection else 0,
                ),
            )
            fillProgress.animateTo(
                1f,
                tween(
                    if (motionAllowed) {
                        BitavidaFluidMotion.StateTransition
                    } else {
                        0
                    },
                ),
            )
            progressGlow.snapTo(1f)
            progressGlow.animateTo(
                0f,
                tween(
                    if (motionAllowed) {
                        BitavidaFluidMotion.StateTransition
                    } else {
                        0
                    },
                ),
            )
        } else if (!motionAllowed) {
            nodeProgress.snapTo(targetProgress)
            fillProgress.snapTo(targetProgress)
            progressGlow.snapTo(0f)
        } else if (targetProgress > nodeProgress.value) {
            nodeProgress.animateTo(
                targetProgress,
                tween(BitavidaFluidMotion.Selection),
            )
            fillProgress.animateTo(
                targetProgress,
                tween(BitavidaFluidMotion.StateTransition),
            )
            progressGlow.snapTo(1f)
            progressGlow.animateTo(
                0f,
                tween(BitavidaFluidMotion.StateTransition),
            )
        } else if (targetProgress < nodeProgress.value) {
            coroutineScope {
                launch {
                    nodeProgress.animateTo(
                        targetProgress,
                        tween(BitavidaFluidMotion.StateTransition),
                    )
                }
                launch {
                    fillProgress.animateTo(
                        targetProgress,
                        tween(BitavidaFluidMotion.StateTransition),
                    )
                }
            }
            progressGlow.snapTo(0f)
        } else {
            fillProgress.snapTo(targetProgress)
            progressGlow.snapTo(0f)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = targetProgress,
                    range = 0f..1f,
                    steps = totalSteps - 1,
                )
            },
    ) {
        val inset = 6.dp.toPx()
        val start = Offset(inset, size.height / 2f)
        val end = Offset(size.width - inset, size.height / 2f)
        val trackLength = end.x - start.x
        val fillEnd = Offset(
            start.x + (trackLength * fillProgress.value),
            start.y,
        )
        val nodeCenter = Offset(
            start.x + (trackLength * nodeProgress.value),
            start.y,
        )
        val stroke = 5.dp.toPx()
        drawLine(trackColor, start, end, stroke, cap = StrokeCap.Round)
        drawLine(
            Brush.horizontalGradient(listOf(gradientStart, gradientEnd)),
            start,
            fillEnd,
            stroke,
            cap = StrokeCap.Round,
        )
        if (progressGlow.value > 0f) {
            drawLine(
                gradientEnd.copy(alpha = progressGlow.value * 0.22f),
                start,
                fillEnd,
                stroke,
                cap = StrokeCap.Round,
            )
            drawCircle(
                gradientStart.copy(alpha = progressGlow.value * 0.18f),
                (7.dp + (3.dp * progressGlow.value)).toPx(),
                nodeCenter,
            )
        }
        drawCircle(gradientEnd, 5.dp.toPx(), nodeCenter)
    }
}


@Composable
internal fun GuidedRegistrationStepTransition(
    currentStep: Int,
    motionAllowed: Boolean,
    content: @Composable (Int) -> Unit,
) {
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            val direction = if (targetState >= initialState) 1 else -1
            (
                fadeIn(tween(duration)) +
                    slideInHorizontally(tween(duration)) { direction * (it / 12) }
                ).togetherWith(
                fadeOut(tween(duration / 2)) +
                    slideOutHorizontally(tween(duration)) { -direction * (it / 14) },
            )
        },
        label = "guided_registration_step",
        content = { content(it) },
    )
}

@Composable
internal fun GuidedRegistrationStepHeading(title: String, body: String) {
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
}

@Composable
internal fun GuidedRegistrationChoiceRow(
    firstLabel: String,
    secondLabel: String,
    firstSelected: Boolean,
    secondSelected: Boolean,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GuidedRegistrationChoice(
            firstLabel,
            firstSelected,
            onFirst,
            Modifier.weight(1f),
        )
        GuidedRegistrationChoice(
            secondLabel,
            secondSelected,
            onSecond,
            Modifier.weight(1f),
        )
    }
}

@Composable
internal fun GuidedRegistrationChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                this.selected = selected
                stateDescription = if (selected) "Seleccionada" else "No seleccionada"
            },
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.bitavidaColors.paleBlue
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                .padding(
                    horizontal = BitavidaSpacing.Small,
                    vertical = BitavidaSpacing.ExtraSmall,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun GuidedAdministrationHeader(
    title: String,
    subtitle: String,
    supportingText: String? = null,
    editDescription: String,
    removeDescription: String,
    onEdit: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            supportingText?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    softWrap = true,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        onEdit?.let { editAction ->
            IconButton(
                onClick = editAction,
                modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget),
            ) {
                Image(
                    painter = painterResource(android.R.drawable.ic_menu_edit),
                    contentDescription = editDescription,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget),
        ) {
            Image(
                painter = painterResource(android.R.drawable.ic_menu_delete),
                contentDescription = removeDescription,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
            )
        }
    }
}

@Composable
internal fun GuidedAdministrationEditorAction(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    addLabel: String = "Agregar administración",
) {
    TextButton(
        onClick = onToggle,
        modifier = modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
    ) {
        Image(
            painter = painterResource(android.R.drawable.ic_input_add),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
        Text(if (expanded) "Cerrar editor" else addLabel)
    }
}

@Composable
internal fun GuidedAdministrationEditorSurface(
    visible: Boolean,
    motionAllowed: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            tween(if (motionAllowed) BitavidaFluidMotion.Selection else 0),
        ) + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        BitavidaConnectedSurface {
            Column(
                modifier = Modifier.padding(BitavidaSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun <T : Comparable<T>> GuidedTimeWindowEditor(
    editing: Boolean,
    start: T?,
    end: T?,
    reminder: T?,
    endsNextDay: Boolean,
    formatTime: (T) -> String,
    onSelectTime: (T?, (T) -> Unit) -> Unit,
    onStartChange: (T) -> Unit,
    onEndChange: (T) -> Unit,
    onReminderChange: (T) -> Unit,
    onEndsNextDayChange: (Boolean) -> Unit,
    windowError: String?,
    reminderError: String?,
    reminderRequired: Boolean,
    onClearReminder: () -> Unit,
    additionalContent: @Composable () -> Unit = {},
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    BitavidaConnectedSurface(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        GuidedTimeWindowEditorContent(
            editing = editing,
            start = start,
            end = end,
            reminder = reminder,
            endsNextDay = endsNextDay,
            formatTime = formatTime,
            onSelectTime = onSelectTime,
            onStartChange = onStartChange,
            onEndChange = onEndChange,
            onReminderChange = onReminderChange,
            onEndsNextDayChange = onEndsNextDayChange,
            windowError = windowError,
            reminderError = reminderError,
            reminderRequired = reminderRequired,
            onClearReminder = onClearReminder,
            additionalContent = additionalContent,
            onCommit = onCommit,
            onCancel = onCancel,
        )
    }
}

@Composable
internal fun <T : Comparable<T>> GuidedTimeWindowEditorContent(
    editing: Boolean,
    start: T?,
    end: T?,
    reminder: T?,
    endsNextDay: Boolean,
    formatTime: (T) -> String,
    onSelectTime: (T?, (T) -> Unit) -> Unit,
    onStartChange: (T) -> Unit,
    onEndChange: (T) -> Unit,
    onReminderChange: (T) -> Unit,
    onEndsNextDayChange: (Boolean) -> Unit,
    windowError: String?,
    reminderError: String?,
    reminderRequired: Boolean,
    onClearReminder: () -> Unit,
    additionalContent: @Composable () -> Unit = {},
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        Text(
            text = if (editing) {
                "Editar ventana horaria"
            } else {
                "Ventana horaria"
            },
            style = MaterialTheme.typography.titleSmall,
        )
        GuidedResponsiveTimePair(
            firstTime = start,
            onFirstClick = {
                onSelectTime(start, onStartChange)
            },
            secondTime = end,
            onSecondClick = {
                onSelectTime(end, onEndChange)
            },
            formatTime = formatTime,
        )
        Surface(
            onClick = { onEndsNextDayChange(!endsNextDay) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                .semantics {
                    stateDescription = if (endsNextDay) {
                        "Seleccionado"
                    } else {
                        "No seleccionado"
                    }
                },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = BitavidaSpacing.Small,
                    vertical = BitavidaSpacing.ExtraSmall,
                ),
                horizontalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = endsNextDay, onCheckedChange = null)
                Text(
                    text = "La ventana termina al día siguiente",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        AnimatedVisibility(visible = endsNextDay) {
            Text(
                text = "El día indicado corresponde al inicio de la ventana.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        windowError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Momento del aviso",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            if (!reminderRequired) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.bitavidaColors.paleBlue,
                ) {
                    Text(
                        text = "Opcional",
                        modifier = Modifier.padding(
                            horizontal = BitavidaSpacing.Small,
                            vertical = BitavidaSpacing.ExtraSmall,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        GuidedTimeSelectionRow(
            label = "Aviso",
            time = reminder,
            formatTime = formatTime,
            onClick = {
                onSelectTime(reminder, onReminderChange)
            },
        )
        if (reminder != null && !reminderRequired) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onClearReminder,
                    modifier = Modifier.heightIn(
                        min = BitavidaSpacing.MinimumTouchTarget,
                    ),
                ) {
                    Text("Quitar aviso")
                }
            }
        }
        AnimatedVisibility(
            visible = endsNextDay &&
                reminder != null &&
                start != null &&
                reminder < start,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                text = "El aviso corresponde al día siguiente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        reminderError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        additionalContent()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(
                        min = BitavidaSpacing.MinimumTouchTarget,
                    ),
                ) {
                    Text("Cancelar")
                }
            }
            TextButton(
                onClick = onCommit,
                modifier = Modifier.heightIn(
                    min = BitavidaSpacing.MinimumTouchTarget,
                ),
            ) {
                Text(if (editing) "Guardar cambios" else "Agregar ventana")
            }
        }
    }
}

@Composable
private fun <T> GuidedResponsiveTimePair(
    firstTime: T?,
    onFirstClick: () -> Unit,
    secondTime: T?,
    onSecondClick: () -> Unit,
    formatTime: (T) -> String,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        if (maxWidth >= 320.dp && fontScale <= 1.3f) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                GuidedTimeSelectionRow(
                    "Desde",
                    firstTime,
                    formatTime,
                    onFirstClick,
                    Modifier.weight(1f),
                )
                GuidedTimeSelectionRow(
                    "Hasta",
                    secondTime,
                    formatTime,
                    onSecondClick,
                    Modifier.weight(1f),
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                GuidedTimeSelectionRow(
                    "Desde",
                    firstTime,
                    formatTime,
                    onFirstClick,
                )
                GuidedTimeSelectionRow(
                    "Hasta",
                    secondTime,
                    formatTime,
                    onSecondClick,
                )
            }
        }
    }
}

@Composable
internal fun <T> GuidedTimeSelectionRow(
    label: String,
    time: T?,
    formatTime: (T) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = BitavidaSpacing.Medium,
                vertical = BitavidaSpacing.Small,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = time?.let(formatTime) ?: "Elegir",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun GuidedRegistrationReviewRow(
    title: String,
    value: String,
    editDescription: String,
    onEdit: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        onEdit?.let {
            IconButton(
                onClick = it,
                modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget),
            ) {
                Image(
                    painter = painterResource(android.R.drawable.ic_menu_edit),
                    contentDescription = editDescription,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
