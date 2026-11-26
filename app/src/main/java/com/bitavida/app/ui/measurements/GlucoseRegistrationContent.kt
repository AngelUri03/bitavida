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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.stateDescription
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseRegistrationContext
import com.bitavida.app.domain.model.glucoseMeasurementContexts
import com.bitavida.app.domain.usecase.GlucoseField
import com.bitavida.app.domain.usecase.validateGlucoseDraft
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal fun GlucoseContext.captureLabel(): String = when (this) {
    GlucoseContext.FASTING -> "En ayunas"
    GlucoseContext.BEFORE_MEAL -> "Antes de comer"
    GlucoseContext.AFTER_MEAL -> "Después de comer"
    GlucoseContext.BEFORE_SLEEP -> "Antes de dormir"
    GlucoseContext.OTHER -> "Otro momento"
    GlucoseContext.RESTING -> "En reposo"
    GlucoseContext.AFTER_PHYSICAL_ACTIVITY ->
        "Después de actividad física"
    GlucoseContext.UPON_WAKING -> "Al despertar"
    GlucoseContext.UNSPECIFIED -> "Sin contexto"
}

@Composable
internal fun GlucoseRegistrationContent(
    state: GlucoseUiState,
    onAction: (GlucoseAction) -> Unit,
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
    val momentFocus = remember { FocusRequester() }
    val momentListState = rememberLazyListState()
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val valueControlHeight = if (largeText) 72.dp else 56.dp
    val chipHeight = if (largeText) 64.dp else BitavidaSpacing.MinimumTouchTarget
    val scheduledExpectedMoment =
        (draft.context as? GlucoseRegistrationContext.Scheduled)
            ?.occurrence
            ?.expectedContext
    var momentSelectorExpanded by remember(draft.id) {
        mutableStateOf(
            (scheduledExpectedMoment == null) || (draft.moment != scheduledExpectedMoment),
        )
    }
    var noteExpanded by remember(draft.id) { mutableStateOf(draft.note.isNotEmpty()) }
    val ready = validateGlucoseDraft(draft, LocalDateTime.now(),
        originalNote = state.records.singleOrNull { it.id == state.editingRecordId }?.glucoseMetadata?.note).isEmpty() &&
        !state.occurrenceAlreadyRecorded
    LaunchedEffect(draft.moment, motionAllowed) {
        val index = draft.moment?.let(glucoseMeasurementContexts::indexOf) ?: return@LaunchedEffect
        if (motionAllowed) momentListState.animateScrollToItem(index) else {
            momentListState.scrollToItem(index)
        }
    }

    MeasurementPanel(
        modifier = Modifier.glucoseActiveOutline(valueFocused, motionAllowed),
        color = MaterialTheme.bitavidaColors.paleBlue,
    ) {
        SectionTitle("Valor de glucosa", small = true)
        val readingError = GlucoseField.READING in state.errors
        val fieldBorder by animateColorAsState(
            targetValue = when {
                readingError -> MaterialTheme.colorScheme.error
                valueFocused -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            },
            animationSpec = tween(duration),
            label = "glucose_field_border",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.weight(1f).height(valueControlHeight),
                shape = MaterialTheme.shapes.medium,
                color = if (state.saving) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, fieldBorder),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = BitavidaSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                ) {
                    BasicTextField(
                        value = draft.reading,
                        onValueChange = {
                            if (it.length <= 40) onAction(GlucoseAction.Reading(it))
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused &&
                                    (draft.reading.equals("HI", ignoreCase = true) ||
                                        draft.reading.equals("LO", ignoreCase = true))
                                ) {
                                    onAction(GlucoseAction.Reading(""))
                                }
                            }
                            .semantics {
                                contentDescription =
                                    "Valor de glucosa en miligramos por decilitro"
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
                                if (momentSelectorExpanded) {
                                    momentFocus.requestFocus()
                                } else {
                                    focusManager.clearFocus()
                                }
                            },
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (draft.reading.isEmpty()) {
                                    Text(
                                        "Valor",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Text(
                        "mg/dL",
                        style = if (largeText) MaterialTheme.typography.labelSmall
                            else MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            listOf("HI", "LO").forEach { flag ->
                GlucoseCaptureChip(
                    label = flag,
                    selected = draft.reading.equals(flag, ignoreCase = true),
                    enabled = !state.saving,
                    motionAllowed = motionAllowed,
                    modifier = Modifier.height(valueControlHeight).widthIn(min = 48.dp),
                    onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        onAction(GlucoseAction.Reading(flag))
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = GlucoseField.READING in state.errors,
            enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
            exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration)),
        ) {
            CaptureError("Escribe el valor del glucómetro o selecciona HI / LO.")
        }
        GlucoseContextHint(draft, motionAllowed)
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
            enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
            exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration)),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                if (scheduledExpectedMoment != null) {
                    SecondaryText(
                        "Elige el contexto real si fue distinto al previsto.",
                    )
                }
                LazyRow(
                    state = momentListState,
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    itemsIndexed(
                        items = glucoseMeasurementContexts,
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
                                onAction(GlucoseAction.Moment(moment))
                            },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = GlucoseField.MOMENT in state.errors,
            enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
            exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration)),
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
            visible = GlucoseField.ACTUAL_TIME in state.errors,
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
            visible = noteExpanded || GlucoseField.NOTE in state.errors,
            enter = fadeIn(tween(duration)) +
                expandVertically(tween(duration), expandFrom = Alignment.Top),
            exit = fadeOut(tween(duration)) +
                shrinkVertically(tween(duration), shrinkTowards = Alignment.Top),
        ) {
            BitavidaLimitedTextField(
                value = draft.note,
                onValueChange = { onAction(GlucoseAction.Note(it)) },
                limit = CaptureLimit.MEASUREMENT_NOTE,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nota o motivo opcional") },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                isError = GlucoseField.NOTE in state.errors,
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
            "Conservar como adicional",
            { onAction(GlucoseAction.MakeAdditional) },
            Modifier.fillMaxWidth(),
            style = MeasurementButtonStyle.OUTLINED,
        )
    }
    GlucoseCaptureReview(draft, ready && !state.saving, motionAllowed)
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
            onAction(GlucoseAction.Save)
        },
        modifier = Modifier.fillMaxWidth().glucoseActiveOutline(
            active = ready && !state.saving,
            motionAllowed = motionAllowed,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f),
        ).semantics {
            stateDescription = when {
                state.saving -> "Guardando medición"
                state.occurrenceAlreadyRecorded -> "Horario ya registrado"
                ready -> "Listo para guardar"
                else -> "Completa la medición"
            }
        },
        enabled = !state.saving && !state.occurrenceAlreadyRecorded,
    )
    GlucoseCaptureDialogs(state, onAction)
}

@Composable
internal fun CaptureError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
internal fun GlucoseActualTime(
    value: LocalDateTime,
    enabled: Boolean,
    onSelectDate: () -> Unit,
    onSelectTime: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val locale = LocalLocale.current.platformLocale
    val dateLabel = value.toLocalDate().format(
        DateTimeFormatter.ofPattern("d MMM uuuu", locale),
    )
    val timeLabel = value.toLocalTime().format(
        DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
            .withLocale(locale),
    )
    val controlHeight = if (largeText) 72.dp else 56.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeasurementButton(
            label = dateLabel,
            onClick = onSelectDate,
            modifier = Modifier.weight(1.3f).height(controlHeight),
            style = MeasurementButtonStyle.OUTLINED,
            enabled = enabled,
            icon = R.drawable.ic_v2_calendar,
            accessibleLabel = "Cambiar fecha real: " +
                value.toLocalDate().format(measurementFullDate),
        )
        MeasurementButton(
            label = timeLabel,
            onClick = onSelectTime,
            modifier = Modifier.weight(0.9f).height(controlHeight),
            style = MeasurementButtonStyle.OUTLINED,
            enabled = enabled,
            accessibleLabel = "Cambiar hora real: $timeLabel",
        )
    }
}

@Composable
private fun GlucoseCaptureDialogs(state: GlucoseUiState, onAction: (GlucoseAction) -> Unit) {
    if (state.consentVisible) AlertDialog(
        onDismissRequest = { if (!state.saving) onAction(GlucoseAction.DismissConsent) },
        title = { Text("Guardar información de salud") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                Text("Esta medición se guardará cifrada en este dispositivo. Es información sanitaria sensible.")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                        .toggleable(
                            value = state.consentAccepted,
                            enabled = !state.saving,
                            role = Role.Checkbox,
                        ) {
                            onAction(GlucoseAction.AcceptConsent(it))
                        }
                        .padding(vertical = BitavidaSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(state.consentAccepted, onCheckedChange = null)
                    Text("Acepto guardar esta información sanitaria en este dispositivo.",
                        modifier = Modifier.weight(1f))
                }
                Text("Esta aceptación no declara representación legal ni comparte datos por Internet.",
                    style = MaterialTheme.typography.bodySmall)
                state.saveError?.let { CaptureError(it) }
            }
        },
        confirmButton = { TextButton(
            enabled = state.consentAccepted && !state.saving,
            onClick = { onAction(GlucoseAction.ConfirmConsent) },
        ) { Text(if (state.saving) "Guardando…" else "Aceptar y guardar") } },
        dismissButton = { TextButton(enabled = !state.saving,
            onClick = { onAction(GlucoseAction.DismissConsent) }) { Text("No guardar") } },
    )
}

