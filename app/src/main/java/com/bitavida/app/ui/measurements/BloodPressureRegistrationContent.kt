package com.bitavida.app.ui.measurements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.model.BloodPressureRegistrationContext
import com.bitavida.app.domain.model.bloodPressureMeasurementContexts
import com.bitavida.app.domain.usecase.BloodPressureField
import com.bitavida.app.domain.usecase.validateBloodPressureDraft
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDateTime

@Composable
internal fun BloodPressureRegistrationContent(
    state: BloodPressureUiState,
    onAction: (BloodPressureAction) -> Unit,
    onSelectDate: () -> Unit,
    onSelectTime: () -> Unit,
    motionEnabledOverride: Boolean? = null,
) {
    val draft = state.draft ?: return
    val motionAllowed = rememberBitavidaMotionAllowed(motionEnabledOverride)
    val duration = if (motionAllowed) BitavidaFluidMotion.Selection else 0
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val systolicInteraction = remember { MutableInteractionSource() }
    val diastolicInteraction = remember { MutableInteractionSource() }
    val systolicFocused by systolicInteraction.collectIsFocusedAsState()
    val diastolicFocused by diastolicInteraction.collectIsFocusedAsState()
    val momentFocus = remember { FocusRequester() }
    val momentListState = rememberLazyListState()
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val fieldHeight = if (largeText) 80.dp else 64.dp
    val chipHeight = if (largeText) 64.dp else BitavidaSpacing.MinimumTouchTarget
    val scheduledExpectedMoment =
        (draft.context as? BloodPressureRegistrationContext.Scheduled)
            ?.occurrence
            ?.expectedContext
    var momentSelectorExpanded by remember(draft.id) {
        mutableStateOf(
            (scheduledExpectedMoment == null) ||
                (draft.moment != scheduledExpectedMoment),
        )
    }
    var noteExpanded by remember(draft.id) {
        mutableStateOf(draft.note.isNotEmpty())
    }
    val ready = validateBloodPressureDraft(
        draft,
        LocalDateTime.now(),
        originalNote = state.records.singleOrNull { it.id == state.editingRecordId }?.bloodPressureMetadata?.note,
    ).isEmpty() && !state.occurrenceAlreadyRecorded

    LaunchedEffect(draft.moment, motionAllowed) {
        val index = draft.moment?.let(bloodPressureMeasurementContexts::indexOf)
            ?: return@LaunchedEffect
        if (motionAllowed) {
            momentListState.animateScrollToItem(index)
        } else {
            momentListState.scrollToItem(index)
        }
    }

    MeasurementPanel(
        modifier = Modifier.glucoseActiveOutline(
            systolicFocused || diastolicFocused,
            motionAllowed,
        ),
        color = MaterialTheme.bitavidaColors.paleBlue,
    ) {
        SectionTitle("Presión arterial", small = true)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressureValueField(
                label = "Sistólica",
                value = draft.systolic,
                error = BloodPressureField.SYSTOLIC in state.errors,
                focused = systolicFocused,
                enabled = !state.saving,
                interactionSource = systolicInteraction,
                height = fieldHeight,
                imeAction = ImeAction.Next,
                onNext = {},
                onValueChange = {
                    onAction(BloodPressureAction.Systolic(it))
                },
                modifier = Modifier.weight(1f),
            )
            PressureValueField(
                label = "Diastólica",
                value = draft.diastolic,
                error = BloodPressureField.DIASTOLIC in state.errors,
                focused = diastolicFocused,
                enabled = !state.saving,
                interactionSource = diastolicInteraction,
                height = fieldHeight,
                imeAction = ImeAction.Next,
                onNext = {
                    keyboard?.hide()
                    if (momentSelectorExpanded) {
                        momentFocus.requestFocus()
                    } else {
                        focusManager.clearFocus()
                    }
                },
                onValueChange = {
                    onAction(BloodPressureAction.Diastolic(it))
                },
                modifier = Modifier.weight(1f),
            )
        }
        AnimatedVisibility(
            visible =
                BloodPressureField.SYSTOLIC in state.errors ||
                    BloodPressureField.DIASTOLIC in state.errors,
            enter = fadeIn(tween(duration)) +
                expandVertically(tween(duration)),
            exit = fadeOut(tween(duration)) +
                shrinkVertically(tween(duration)),
        ) {
            CaptureError(
                "Escribe los valores sistólico y diastólico en mmHg.",
            )
        }
        BloodPressureContextHint(draft.context, draft.firstMeasurement)
    }

    MeasurementPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("Momento de la medición", small = true)
            if (scheduledExpectedMoment != null && !momentSelectorExpanded) {
                TextButton(
                    onClick = { momentSelectorExpanded = true },
                    enabled = !state.saving,
                    modifier = Modifier.heightIn(
                        min = BitavidaSpacing.MinimumTouchTarget,
                    ),
                ) {
                    Text("Cambiar")
                }
            }
        }
        if (scheduledExpectedMoment != null && !momentSelectorExpanded) {
            GlucoseCaptureChip(
                label = requireNotNull(draft.moment).captureLabel(),
                selected = true,
                enabled = !state.saving,
                motionAllowed = motionAllowed,
                modifier = Modifier.height(chipHeight),
                onClick = { momentSelectorExpanded = true },
            )
            SecondaryText("Momento sugerido para esta programación.")
        }
        AnimatedVisibility(
            visible = momentSelectorExpanded,
            enter = fadeIn(tween(duration)) +
                expandVertically(tween(duration)),
            exit = fadeOut(tween(duration)) +
                shrinkVertically(tween(duration)),
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                if (scheduledExpectedMoment != null) {
                    SecondaryText(
                        "Elige el contexto real si fue distinto al previsto.",
                    )
                }
                LazyRow(
                    state = momentListState,
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement =
                        Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    itemsIndexed(
                        items = bloodPressureMeasurementContexts,
                        key = { _, moment -> moment.name },
                    ) { index, moment ->
                        GlucoseCaptureChip(
                            label = moment.captureLabel(),
                            selected = draft.moment == moment,
                            enabled = !state.saving,
                            motionAllowed = motionAllowed,
                            modifier = Modifier.height(chipHeight).then(
                                if (index == 0) {
                                    Modifier.focusRequester(momentFocus)
                                } else {
                                    Modifier
                                },
                            ),
                            onClick = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                onAction(
                                    BloodPressureAction.Moment(moment),
                                )
                            },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = BloodPressureField.MOMENT in state.errors,
            enter = fadeIn(tween(duration)) +
                expandVertically(tween(duration)),
            exit = fadeOut(tween(duration)) +
                shrinkVertically(tween(duration)),
        ) {
            CaptureError("Selecciona cuándo se tomó la medición.")
        }
        ActionDivider()
        SectionTitle("Fecha y hora reales", small = true)
        GlucoseActualTime(
            value = draft.actualAt,
            enabled = !state.saving,
            onSelectDate = onSelectDate,
            onSelectTime = onSelectTime,
        )
        AnimatedVisibility(
            visible = BloodPressureField.ACTUAL_TIME in state.errors,
            enter = fadeIn(tween(duration)) +
                expandVertically(tween(duration)),
            exit = fadeOut(tween(duration)) +
                shrinkVertically(tween(duration)),
        ) {
            CaptureError(
                "La medición no puede tener una fecha u hora futura.",
            )
        }
        ActionDivider()
        GlucoseNoteHeader(
            expanded = noteExpanded,
            hasNote = draft.note.isNotBlank(),
            enabled = !state.saving,
            motionAllowed = motionAllowed,
        ) {
            noteExpanded = !noteExpanded
        }
        AnimatedVisibility(
            visible =
                noteExpanded ||
                    BloodPressureField.NOTE in state.errors,
            enter = fadeIn(tween(duration)) +
                expandVertically(
                    tween(duration),
                    expandFrom = Alignment.Top,
                ),
            exit = fadeOut(tween(duration)) +
                shrinkVertically(
                    tween(duration),
                    shrinkTowards = Alignment.Top,
                ),
        ) {
            BitavidaLimitedTextField(
                value = draft.note,
                onValueChange = {
                    onAction(BloodPressureAction.Note(it))
                },
                limit = CaptureLimit.MEASUREMENT_NOTE,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nota o motivo opcional") },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                isError = BloodPressureField.NOTE in state.errors,
            )
        }
    }

    AnimatedVisibility(
        visible = state.saveError != null,
        enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
        exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration)),
    ) {
        state.saveError?.let { CaptureError(it) }
    }
    if (state.occurrenceAlreadyRecorded) {
        MeasurementButton(
            label = "Conservar como adicional",
            onClick = {
                onAction(BloodPressureAction.MakeAdditional)
            },
            modifier = Modifier.fillMaxWidth(),
            style = MeasurementButtonStyle.OUTLINED,
        )
    }
    MeasurementButton(
        label = when {
            state.saving -> "Guardando…"
            state.editingRecordId != null -> "Guardar corrección"
            draft.firstMeasurement -> "Guardar primera medición"
            else -> "Guardar medición"
        },
        onClick = {
            focusManager.clearFocus()
            keyboard?.hide()
            onAction(BloodPressureAction.Save)
        },
        modifier = Modifier.fillMaxWidth().glucoseActiveOutline(
            active = ready && !state.saving,
            motionAllowed = motionAllowed,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f),
        ).semantics {
            stateDescription = when {
                state.saving -> "Guardando medición"
                state.occurrenceAlreadyRecorded ->
                    "Horario ya registrado"
                ready -> "Listo para guardar"
                else -> "Completa la medición"
            }
        },
        enabled = !state.saving && !state.occurrenceAlreadyRecorded,
    )
    BloodPressureConsentDialog(state, onAction)
}

@Composable
private fun PressureValueField(
    label: String,
    value: String,
    error: Boolean,
    focused: Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    height: androidx.compose.ui.unit.Dp,
    imeAction: ImeAction,
    onNext: () -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val border by animateColorAsState(
        targetValue = when {
            error -> MaterialTheme.colorScheme.error
            focused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(BitavidaFluidMotion.Selection),
        label = label + "_pressure_border",
    )
    Surface(
        modifier = modifier.height(height),
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(
                horizontal = BitavidaSpacing.Small,
                vertical = BitavidaSpacing.ExtraSmall,
            ),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .semantics {
                            contentDescription = "$label en milímetros de mercurio"
                        },
                    enabled = enabled,
                    interactionSource = interactionSource,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onNext() },
                    ),
                    singleLine = true,
                    cursorBrush =
                        SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    "—",
                                    style =
                                        MaterialTheme.typography.headlineSmall,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
                Text(
                    "mmHg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BloodPressureContextHint(
    context: BloodPressureRegistrationContext,
    firstMeasurement: Boolean,
) {
    when (context) {
        BloodPressureRegistrationContext.Independent ->
            if (firstMeasurement) {
                SecondaryText(
                    "Puedes comenzar sin configurar un seguimiento.",
                )
            }
        is BloodPressureRegistrationContext.Additional -> {
            Text(
                "Medición adicional",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            SecondaryText("No sustituye una medición programada.")
        }
        is BloodPressureRegistrationContext.Scheduled -> {
            Text(
                "Medición programada",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            SecondaryText(
                context.occurrence.scheduledAt
                    .format(measurementDateTime),
            )
        }
    }
}

@Composable
private fun BloodPressureConsentDialog(
    state: BloodPressureUiState,
    onAction: (BloodPressureAction) -> Unit,
) {
    if (!state.consentVisible) return
    AlertDialog(
        onDismissRequest = {
            if (!state.saving) {
                onAction(BloodPressureAction.DismissConsent)
            }
        },
        title = { Text("Guardar información de salud") },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                Text(
                    "Esta medición se guardará cifrada en este dispositivo. " +
                        "Es información sanitaria sensible.",
                )
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(
                            min = BitavidaSpacing.MinimumTouchTarget,
                        )
                        .toggleable(
                            value = state.consentAccepted,
                            enabled = !state.saving,
                            role = Role.Checkbox,
                        ) {
                            onAction(
                                BloodPressureAction.AcceptConsent(it),
                            )
                        }
                        .padding(vertical = BitavidaSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.consentAccepted,
                        onCheckedChange = null,
                    )
                    Text(
                        "Acepto guardar esta información sanitaria en este dispositivo.",
                        modifier = Modifier.weight(1f),
                    )
                }
                state.saveError?.let { CaptureError(it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = state.consentAccepted && !state.saving,
                onClick = {
                    onAction(BloodPressureAction.ConfirmConsent)
                },
            ) {
                Text(
                    if (state.saving) {
                        "Guardando…"
                    } else {
                        "Aceptar y guardar"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !state.saving,
                onClick = {
                    onAction(BloodPressureAction.DismissConsent)
                },
            ) {
                Text("No guardar")
            }
        },
    )
}
