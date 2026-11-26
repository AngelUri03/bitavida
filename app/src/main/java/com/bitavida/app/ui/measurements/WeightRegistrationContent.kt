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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.bitavida.app.domain.usecase.WeightField
import com.bitavida.app.domain.usecase.validateWeightDraft
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDateTime

@Composable
internal fun WeightRegistrationContent(
    state: WeightUiState,
    onAction: (WeightAction) -> Unit,
    onSelectDate: () -> Unit,
    onSelectTime: () -> Unit,
    motionEnabledOverride: Boolean? = null,
) {
    val draft = state.draft ?: return
    val motionAllowed = rememberBitavidaMotionAllowed(motionEnabledOverride)
    val duration = if (motionAllowed) BitavidaFluidMotion.Selection else 0
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val valueInteraction = remember { MutableInteractionSource() }
    val valueFocused by valueInteraction.collectIsFocusedAsState()
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val valueControlHeight = if (largeText) 72.dp else 56.dp
    var noteExpanded by remember(draft.id) {
        mutableStateOf(draft.note.isNotEmpty())
    }
    val ready = validateWeightDraft(draft, LocalDateTime.now(),
        originalNote = state.records.singleOrNull { it.id == state.editingRecordId }?.weightMetadata?.note).isEmpty() &&
        !state.occurrenceAlreadyRecorded


    MeasurementPanel(
        modifier = Modifier.glucoseActiveOutline(valueFocused, motionAllowed),
        color = MaterialTheme.bitavidaColors.paleBlue,
    ) {
        SectionTitle("Peso", small = true)
        val valueError = WeightField.KILOGRAMS in state.errors
        val fieldBorder by animateColorAsState(
            targetValue = when {
                valueError -> MaterialTheme.colorScheme.error
                valueFocused -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            },
            animationSpec = tween(duration),
            label = "weight_field_border",
        )
        Surface(
            modifier = Modifier.fillMaxWidth().height(valueControlHeight),
            shape = MaterialTheme.shapes.medium,
            color = if (state.saving) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(1.dp, fieldBorder),
        ) {
            Row(
                modifier = Modifier.fillMaxSize()
                    .padding(horizontal = BitavidaSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                BasicTextField(
                    value = draft.kilograms,
                    onValueChange = {
                        if (it.length <= 8) {
                            onAction(WeightAction.Kilograms(it))
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .semantics {
                            contentDescription = "Peso en kilogramos"
                        },
                    enabled = !state.saving,
                    interactionSource = valueInteraction,
                    textStyle = (
                        if (largeText) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.headlineSmall
                        ).copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (draft.kilograms.isEmpty()) {
                                Text(
                                    "Valor",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Text(
                    "kg",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(
            visible = valueError,
            enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
            exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration)),
        ) {
            CaptureError("Escribe un peso válido mayor que cero.")
        }
    }

    MeasurementPanel {
        SectionTitle("Fecha y hora reales", small = true)
        GlucoseActualTime(
            value = draft.actualAt,
            enabled = !state.saving,
            onSelectDate = onSelectDate,
            onSelectTime = onSelectTime,
        )
        AnimatedVisibility(
            visible = WeightField.ACTUAL_TIME in state.errors,
            enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
            exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration)),
        ) {
            CaptureError("La medición no puede tener una fecha u hora futura.")
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
            visible = noteExpanded || WeightField.NOTE in state.errors,
            enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
            exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration)),
        ) {
            BitavidaLimitedTextField(
                value = draft.note,
                onValueChange = { onAction(WeightAction.Note(it)) },
                limit = CaptureLimit.MEASUREMENT_NOTE,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nota o motivo opcional") },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                isError = WeightField.NOTE in state.errors,
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
            onClick = { onAction(WeightAction.MakeAdditional) },
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
            onAction(WeightAction.Save)
        },
        modifier = Modifier.fillMaxWidth()
            .glucoseActiveOutline(
                active = ready && !state.saving,
                motionAllowed = motionAllowed,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f),
            )
            .semantics {
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
    WeightConsentDialog(state, onAction)
}

@Composable
internal fun WeightConsentDialog(
    state: WeightUiState,
    onAction: (WeightAction) -> Unit,
) {
    if (!state.consentVisible) return
    AlertDialog(
        onDismissRequest = { onAction(WeightAction.DismissConsent) },
        title = { Text("Protección de datos sensibles") },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                SecondaryText(
                    "El peso se guardará cifrado únicamente en este dispositivo.",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = state.consentAccepted,
                            role = Role.Checkbox,
                            onValueChange = {
                                onAction(WeightAction.AcceptConsent(it))
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.consentAccepted,
                        onCheckedChange = null,
                    )
                    Text("Acepto guardar esta información sensible.")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(WeightAction.ConfirmConsent) },
                enabled = state.consentAccepted,
            ) {
                Text("Continuar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(WeightAction.DismissConsent) },
            ) {
                Text("Ahora no")
            }
        },
    )
}
