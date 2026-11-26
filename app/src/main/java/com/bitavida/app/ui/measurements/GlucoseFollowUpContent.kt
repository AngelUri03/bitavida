package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.BitavidaLimitedTextField

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseFollowUpFrequency
import com.bitavida.app.domain.model.GlucoseFollowUpRevision
import com.bitavida.app.domain.model.GlucoseFollowUpSlot
import com.bitavida.app.domain.model.GlucoseFollowUpTimingType
import com.bitavida.app.domain.model.glucoseMeasurementContexts
import com.bitavida.app.domain.model.isWithinGlucoseWindow
import com.bitavida.app.domain.usecase.GlucoseFollowUpDraft
import com.bitavida.app.domain.usecase.GlucoseFollowUpSlotDraft
import com.bitavida.app.domain.usecase.glucoseFollowUpSchedulesConflict
import com.bitavida.app.domain.usecase.glucoseFollowUpSlotIsComplete
import com.bitavida.app.domain.usecase.toDraft
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaConnectedSurface
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaThreadAction
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.medical.GuidedAdministrationEditorAction
import com.bitavida.app.ui.medical.GuidedAdministrationEditorSurface
import com.bitavida.app.ui.medical.GuidedAdministrationHeader
import com.bitavida.app.ui.medical.HistoryKindLabel
import com.bitavida.app.ui.medical.HistoryMarker
import com.bitavida.app.ui.medical.MedicationFieldChange
import com.bitavida.app.ui.medical.RevisionChanges
import com.bitavida.app.ui.medical.GuidedRegistrationChoice
import com.bitavida.app.ui.medical.GuidedRegistrationChoiceRow
import com.bitavida.app.ui.medical.GuidedRegistrationHeader
import com.bitavida.app.ui.medical.GuidedRegistrationReviewRow
import com.bitavida.app.ui.medical.GuidedRegistrationStepHeading
import com.bitavida.app.ui.medical.GuidedRegistrationStepProgress
import com.bitavida.app.ui.medical.GuidedRegistrationStepTransition
import com.bitavida.app.ui.medical.GuidedTimeSelectionRow
import com.bitavida.app.ui.medical.GuidedTimeWindowEditorContent
import com.bitavida.app.ui.medical.UnitSelector
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

private enum class GlucoseFollowUpStep {
    INDICATION,
    FREQUENCY,
    SCHEDULES,
    REMINDERS,
    REVIEW,
}

private val followUpLocale = Locale.forLanguageTag("es-MX")
private val followUpDateFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(followUpLocale)
private val followUpTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(followUpLocale)

@Composable
internal fun GlucoseFollowUpContent(
    existing: GlucoseFollowUp?,
    person: String,
    today: LocalDate,
    saving: Boolean,
    error: String?,
    changeKind: GlucoseFollowUpChangeKind,
    onBack: () -> Unit,
    measurementTitle: String = "Glucosa capilar",
    measurementIcon: Int = R.drawable.ic_v2_droplet,
    contextOptions: List<GlucoseContext> = glucoseMeasurementContexts,
    showMeasurementContext: Boolean = true,
    scheduleRecommendation: String? = null,
    onRequestNotificationPermission: () -> Unit = {},
    onSave: (GlucoseFollowUpDraft) -> Unit,
) {
    var stepIndex by rememberSaveable(existing?.id, changeKind) {
        mutableIntStateOf(0)
    }
    var attemptedStep by rememberSaveable { mutableStateOf(value = false) }
    var entered by remember { mutableStateOf(value = false) }
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val focusManager = LocalFocusManager.current
    val step = GlucoseFollowUpStep.entries[stepIndex]
    var draft by remember(existing?.id, changeKind) {
        mutableStateOf(
            existing?.toDraft(changeKind, today) ?: GlucoseFollowUpDraft(
                followUpId = UUID.randomUUID().toString(),
                effectiveFrom = today,
                slots = emptyList(),
                changeKind = changeKind,
            ),
        )
    }
    LaunchedEffect(Unit) { entered = true }
    val iconScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.9f,
        animationSpec = tween(
            if (motionAllowed) BitavidaFluidMotion.StateTransition else 0,
        ),
        label = "glucose_follow_up_icon",
    )
    val valid = stepIsValid(step, draft)
    val moveBack: () -> Unit = {
        attemptedStep = false
        if (stepIndex == 0) onBack() else stepIndex--
    }
    BackHandler(enabled = (stepIndex > 0) && !saving) {
        attemptedStep = false
        stepIndex--
    }
    val moveForward: () -> Unit = {
        focusManager.clearFocus()
        if (!valid) {
            attemptedStep = true
        } else if (step == GlucoseFollowUpStep.REVIEW) {
            onSave(draft)
        } else {
            attemptedStep = false
            stepIndex++
        }
    }

    GuidedRegistrationHeader(
        title = when (changeKind) {
            GlucoseFollowUpChangeKind.CREATED -> "Configurar seguimiento"
            GlucoseFollowUpChangeKind.CORRECTED -> "Corregir seguimiento"
            GlucoseFollowUpChangeKind.INDICATION_CHANGED -> "Cambiar indicación"
            GlucoseFollowUpChangeKind.REMOVED -> "Desactivar seguimiento"
        },
        personLabel = "Acompañando a $person",
        icon = painterResource(measurementIcon),
        iconScale = iconScale,
        focusManager = focusManager,
        onBack = moveBack,
        onContinue = if (step == GlucoseFollowUpStep.REVIEW) null else moveForward,
        continueLabel = if (step == GlucoseFollowUpStep.REMINDERS) {
            "Revisar"
        } else {
            "Continuar"
        },
        continueEnabled = !saving,
        backEnabled = !saving,
    )
    Text(
        text = "Paso " + (stepIndex + 1) + " de " +
            GlucoseFollowUpStep.entries.size,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
    )
    GuidedRegistrationStepProgress(
        currentStep = stepIndex,
        totalSteps = GlucoseFollowUpStep.entries.size,
        motionAllowed = motionAllowed,
    )
    GuidedRegistrationStepTransition(stepIndex, motionAllowed) { shownIndex ->
        Column(
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        ) {
            when (GlucoseFollowUpStep.entries[shownIndex]) {
                GlucoseFollowUpStep.INDICATION ->
                    IndicationStep(draft) { draft = it }
                GlucoseFollowUpStep.FREQUENCY ->
                    FrequencyStep(draft) { draft = it }
                GlucoseFollowUpStep.SCHEDULES ->
                    SchedulesStep(
                        draft = draft,
                        motionAllowed = motionAllowed,
                        contextOptions = contextOptions,
                        showMeasurementContext = showMeasurementContext,
                        recommendation = scheduleRecommendation,
                    ) { draft = it }
                GlucoseFollowUpStep.REMINDERS ->
                    RemindersStep(
                        draft = draft,
                        onRequestPermission = onRequestNotificationPermission,
                    ) { draft = it }
                GlucoseFollowUpStep.REVIEW ->
                    ReviewStep(
                        draft = draft,
                        saving = saving,
                        error = error,
                        measurementTitle = measurementTitle,
                        measurementIcon = measurementIcon,
                        showMeasurementContext = showMeasurementContext,
                        onEdit = {
                            attemptedStep = false
                            stepIndex = it.ordinal
                        },
                        onConfirm = moveForward,
                    )
            }
            if (attemptedStep && !valid) {
                Text(
                    text = stepError(step, showMeasurementContext),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun IndicationStep(
    draft: GlucoseFollowUpDraft,
    onChange: (GlucoseFollowUpDraft) -> Unit,
) {
    GuidedRegistrationStepHeading(
        "Indicación médica",
        "Transcribe la indicación recibida sin completar ni interpretar información.",
    )
    BitavidaLimitedTextField(
        value = draft.indication,
        onValueChange = { onChange(draft.copy(indication = it)) },
        limit = CaptureLimit.CARE_INSTRUCTION,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Indicación registrada") },
        placeholder = { Text("Escribe la indicación tal como fue recibida") },
        minLines = 4,
        maxLines = 8,
        supportingText = { Text("Declarado por la persona cuidadora") },
    )
}

@Composable
private fun FrequencyStep(
    draft: GlucoseFollowUpDraft,
    onChange: (GlucoseFollowUpDraft) -> Unit,
) {
    GuidedRegistrationStepHeading(
        "¿Con qué frecuencia corresponde?",
        "Elige únicamente la modalidad que aparece en la indicación recibida.",
    )
    Text(
        "POR DÍAS",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    GuidedRegistrationChoiceRow(
        firstLabel = "Todos los días",
        secondLabel = "Días específicos",
        firstSelected = draft.frequency == GlucoseFollowUpFrequency.DAILY,
        secondSelected =
            draft.frequency == GlucoseFollowUpFrequency.SPECIFIC_DAYS,
        onFirst = {
            onChange(
                draft.copy(
                    frequency = GlucoseFollowUpFrequency.DAILY,
                    weekdays = emptySet(),
                ),
            )
        },
    ) {
        onChange(
            draft.copy(
                frequency = GlucoseFollowUpFrequency.SPECIFIC_DAYS,
            ),
        )
    }
    GuidedRegistrationChoice(
        label = "Cada cierto número de días",
        selected = draft.frequency == GlucoseFollowUpFrequency.EVERY_N_DAYS,
        onClick = {
            onChange(
                draft.copy(
                    frequency = GlucoseFollowUpFrequency.EVERY_N_DAYS,
                    weekdays = emptySet(),
                ),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    AnimatedVisibility(
        visible = draft.frequency == GlucoseFollowUpFrequency.SPECIFIC_DAYS,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in draft.weekdays,
                    onClick = {
                        onChange(
                            draft.copy(
                                weekdays = if (day in draft.weekdays) {
                                    draft.weekdays - day
                                } else {
                                    draft.weekdays + day
                                },
                            ),
                        )
                    },
                    label = { Text(day.shortLabel()) },
                )
            }
        }
    }
    AnimatedVisibility(
        visible = draft.frequency == GlucoseFollowUpFrequency.EVERY_N_DAYS,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        OutlinedTextField(
            value = draft.everyNDaysInterval
                .takeIf { it > 0 }
                ?.toString()
                .orEmpty(),
            onValueChange = {
                onChange(
                    draft.copy(everyNDaysInterval = it.toIntOrNull() ?: 0),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cada cuántos días") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
    }
}

@Composable
private fun SchedulesStep(
    draft: GlucoseFollowUpDraft,
    motionAllowed: Boolean,
    contextOptions: List<GlucoseContext>,
    showMeasurementContext: Boolean,
    recommendation: String?,
    onChange: (GlucoseFollowUpDraft) -> Unit,
) {
    var editor by remember(draft.followUpId) {
        mutableStateOf<GlucoseFollowUpSlotDraft?>(null)
    }
    val editing = editor
    val candidateSlots = editing?.let { candidate ->
        draft.slots.filterNot { (id) -> id == candidate.id } + candidate
    }.orEmpty()
    val scheduleConflict = (editing != null) &&
        glucoseFollowUpSchedulesConflict(candidateSlots)
    val editorComplete =
        (editing?.let(::glucoseFollowUpSlotIsComplete) == true) &&
            !scheduleConflict

    GuidedRegistrationStepHeading(
        "Horarios de medición",
        if (showMeasurementContext) {
            "Agrega cada horario indicado y configura su momento y aviso."
        } else {
            "Agrega cada horario indicado y configura su aviso."
        },
    )
    recommendation?.let { SecondaryText(it) }
    if (draft.slots.isEmpty()) {
        Text(
            "Aún no hay horarios configurados.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            draft.slots.forEachIndexed { index, slot ->
                GlucoseScheduleCard(
                    number = index + 1,
                    slot = slot,
                    showMeasurementContext = showMeasurementContext,
                    onEdit = { editor = slot },
                ) {
                    onChange(draft.copy(slots = draft.slots - slot))
                    if (editor?.id == slot.id) editor = null
                }
            }
        }
    }

    if ((draft.slots.size < 12) || (editor != null)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            GuidedAdministrationEditorAction(
                expanded = editor != null,
                onToggle = {
                    editor = if (editor == null) {
                        GlucoseFollowUpSlotDraft(
                            id = UUID.randomUUID().toString(),
                            context = if (showMeasurementContext) {
                                null
                            } else {
                                GlucoseContext.UNSPECIFIED
                            },
                        )
                    } else {
                        null
                    }
                },
                addLabel = "Agregar horario",
            )
        }
    }

    GuidedAdministrationEditorSurface(
        visible = editor != null,
        motionAllowed = motionAllowed,
    ) {
        editing?.let { slot ->
            GlucoseScheduleEditor(
                number = draft.slots.indexOfFirst { (id) -> id == slot.id }
                    .takeIf { it >= 0 }
                    ?.plus(1),
                slot = slot,
                scheduleConflict = scheduleConflict,
                contextOptions = contextOptions,
                showMeasurementContext = showMeasurementContext,
                onChange = { editor = it },
                onCancel = { editor = null },
            ) {
                if (!editorComplete) return@GlucoseScheduleEditor
                val stored = slot.copy(reminderEnabled = true)
                val existingIndex = draft.slots.indexOfFirst { (id) ->
                    id == stored.id
                }
                val next = draft.slots.toMutableList().also { slots ->
                    if (existingIndex >= 0) {
                        slots[existingIndex] = stored
                    } else {
                        slots += stored
                    }
                }.sortedBy { (_, time) -> time }
                onChange(draft.copy(slots = next))
                editor = null
            }
        }
    }
}

@Composable
private fun GlucoseScheduleCard(
    number: Int,
    slot: GlucoseFollowUpSlotDraft,
    showMeasurementContext: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val scheduleLabel = slot.scheduleLabel()
    val typeLabel = when (slot.timingType) {
        GlucoseFollowUpTimingType.EXACT_TIME -> "Hora exacta"
        GlucoseFollowUpTimingType.TIME_WINDOW -> "Ventana horaria"
        null -> "Horario incompleto"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = BitavidaSpacing.Medium,
                vertical = BitavidaSpacing.Small,
            ),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            GuidedAdministrationHeader(
                title = scheduleLabel,
                subtitle = listOfNotNull(
                    typeLabel,
                    "Medición $number",
                    slot.context?.label()?.takeIf { showMeasurementContext },
                ).joinToString(" · "),
                supportingText = slot.reminderTime?.let {
                    "Aviso: " + it.format(followUpTimeFormatter) +
                        if (slot.reminderNextDay) " · día siguiente" else ""
                },
                editDescription = "Editar horario $scheduleLabel",
                removeDescription = "Quitar horario $scheduleLabel",
                onEdit = onEdit,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun GlucoseScheduleEditor(
    number: Int?,
    slot: GlucoseFollowUpSlotDraft,
    scheduleConflict: Boolean,
    contextOptions: List<GlucoseContext>,
    showMeasurementContext: Boolean,
    onChange: (GlucoseFollowUpSlotDraft) -> Unit,
    onCancel: () -> Unit,
    onCommit: () -> Unit,
) {
    val picker = LocalBitavidaDateTimePickerController.current
    var commitAttempted by remember(slot.id) {
        mutableStateOf(value = false)
    }
    LaunchedEffect(slot.timingType) {
        commitAttempted = false
    }

    Text(
        text = "Tipo de horario",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    GuidedRegistrationChoiceRow(
        firstLabel = "Hora exacta",
        secondLabel = "Ventana horaria",
        firstSelected = slot.timingType == GlucoseFollowUpTimingType.EXACT_TIME,
        secondSelected = slot.timingType == GlucoseFollowUpTimingType.TIME_WINDOW,
        onFirst = {
            if (slot.timingType != GlucoseFollowUpTimingType.EXACT_TIME) {
                val sameTime = (slot.reminderTime != null) &&
                    (slot.reminderTime == slot.time)
                onChange(
                    slot.copy(
                        timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                        windowEnd = null,
                        endsNextDay = false,
                        reminderNextDay = false,
                        useSameTimeForReminder = when {
                            slot.reminderTime == null -> null
                            sameTime -> true
                            else -> false
                        },
                    ),
                )
            }
        },
    ) {
        if (slot.timingType != GlucoseFollowUpTimingType.TIME_WINDOW) {
            onChange(
                slot.copy(
                    timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
                    windowEnd = null,
                    endsNextDay = false,
                    reminderNextDay = false,
                    useSameTimeForReminder = null,
                ),
            )
        }
    }

    AnimatedVisibility(
        visible = slot.timingType == GlucoseFollowUpTimingType.EXACT_TIME,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        GlucoseExactScheduleContent(
            editing = number != null,
            slot = slot,
            commitAttempted = commitAttempted,
            scheduleConflict = scheduleConflict,
            contextOptions = contextOptions,
            showMeasurementContext = showMeasurementContext,
            onSelectTime = { initial, selected ->
                picker.showTime(
                    initial ?: LocalTime.now(),
                    onConfirm = selected,
                )
            },
            onChange = onChange,
            onCancel = onCancel,
            onCommit = {
                commitAttempted = true
                if (
                    glucoseFollowUpSlotIsComplete(slot) &&
                    !scheduleConflict
                ) {
                    onCommit()
                }
            },
        )
    }

    AnimatedVisibility(
        visible = slot.timingType == GlucoseFollowUpTimingType.TIME_WINDOW,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val start = slot.time
        val end = slot.windowEnd
        val reminder = slot.reminderTime
        val windowError = when {
            (start == null) || (end == null) ->
                "Selecciona el inicio y el final de la ventana."
            !slot.endsNextDay && end < start ->
                "Indica que termina al día siguiente si la ventana cruza la medianoche."
            end == start ->
                "El inicio y el final deben ser diferentes."
            else -> null
        }
        val reminderNextDay = slot.endsNextDay &&
            reminder != null &&
            start != null &&
            reminder < start
        val reminderError = when {
            reminder == null -> "Selecciona la hora del aviso."
            start != null &&
                end != null &&
                !reminder.isWithinGlucoseWindow(
                    start = start,
                    end = end,
                    endsNextDay = slot.endsNextDay,
                    reminderNextDay = reminderNextDay,
                ) -> "El momento del aviso debe estar dentro de la ventana indicada."
            else -> null
        }
        GuidedTimeWindowEditorContent(
            editing = number != null,
            start = start,
            end = end,
            reminder = reminder,
            endsNextDay = slot.endsNextDay,
            formatTime = { it.format(followUpTimeFormatter) },
            onSelectTime = { initial, selected ->
                picker.showTime(
                    initial ?: LocalTime.now(),
                    onConfirm = selected,
                )
            },
            onStartChange = { next ->
                onChange(slot.copy(time = next))
            },
            onEndChange = { next ->
                onChange(slot.copy(windowEnd = next))
            },
            onReminderChange = { next ->
                val nextDay = slot.endsNextDay &&
                    slot.time != null &&
                    next < slot.time
                onChange(
                    slot.copy(
                        reminderTime = next,
                        reminderEnabled = true,
                        reminderNextDay = nextDay,
                    ),
                )
            },
            onEndsNextDayChange = { next ->
                val nextReminderDay = next &&
                    slot.reminderTime != null &&
                    slot.time != null &&
                    slot.reminderTime < slot.time
                onChange(
                    slot.copy(
                        endsNextDay = next,
                        reminderNextDay = nextReminderDay,
                    ),
                )
            },
            windowError = windowError.takeIf { commitAttempted },
            reminderError = reminderError.takeIf { commitAttempted },
            reminderRequired = true,
            onClearReminder = {},
            additionalContent = {
                if (showMeasurementContext) {
                    GlucoseSuggestedMomentSelector(
                        selected = slot.context,
                        showError = commitAttempted && slot.context == null,
                        options = contextOptions,
                        onSelected = {
                            onChange(slot.copy(context = it))
                        },
                    )
                }
                if (scheduleConflict && commitAttempted) {
                    GlucoseScheduleError(
                        "Ese horario se duplica o se superpone con otro.",
                    )
                }
            },
            onCommit = {
                commitAttempted = true
                if (
                    glucoseFollowUpSlotIsComplete(slot) &&
                    !scheduleConflict
                ) {
                    onCommit()
                }
            },
            onCancel = onCancel,
        )
    }
}

@Composable
private fun GlucoseExactScheduleContent(
    editing: Boolean,
    slot: GlucoseFollowUpSlotDraft,
    commitAttempted: Boolean,
    scheduleConflict: Boolean,
    contextOptions: List<GlucoseContext>,
    showMeasurementContext: Boolean,
    onSelectTime: (LocalTime?, (LocalTime) -> Unit) -> Unit,
    onChange: (GlucoseFollowUpSlotDraft) -> Unit,
    onCancel: () -> Unit,
    onCommit: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        GuidedTimeSelectionRow(
            label = "Hora exacta",
            time = slot.time,
            formatTime = { it.format(followUpTimeFormatter) },
            onClick = {
                onSelectTime(slot.time) { selected ->
                    onChange(
                        slot.copy(
                            time = selected,
                            reminderTime = if (
                                slot.useSameTimeForReminder == true
                            ) {
                                selected
                            } else {
                                slot.reminderTime
                            },
                        ),
                    )
                }
            },
        )
        Text(
            text = "Momento del aviso",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        GuidedRegistrationChoiceRow(
            firstLabel = "Usar la misma hora",
            secondLabel = "Elegir otra hora",
            firstSelected = slot.useSameTimeForReminder == true,
            secondSelected = slot.useSameTimeForReminder == false,
            onFirst = {
                onChange(
                    slot.copy(
                        useSameTimeForReminder = true,
                        reminderTime = slot.time,
                        reminderNextDay = false,
                        reminderEnabled = true,
                    ),
                )
            },
            onSecond = {
                onChange(
                    slot.copy(
                        useSameTimeForReminder = false,
                        reminderTime = null,
                        reminderNextDay = false,
                        reminderEnabled = true,
                    ),
                )
            },
        )
        AnimatedVisibility(
            visible = slot.useSameTimeForReminder == false,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            GuidedTimeSelectionRow(
                label = "Aviso",
                time = slot.reminderTime,
                formatTime = { it.format(followUpTimeFormatter) },
                onClick = {
                    onSelectTime(slot.reminderTime ?: slot.time) { selected ->
                        onChange(
                            slot.copy(
                                reminderTime = selected,
                                reminderEnabled = true,
                                reminderNextDay = false,
                            ),
                        )
                    }
                },
            )
        }
        if (commitAttempted) {
            when {
                slot.time == null ->
                    GlucoseScheduleError("Selecciona la hora exacta.")
                slot.useSameTimeForReminder == null ->
                    GlucoseScheduleError("Elige cómo se programará el aviso.")
                slot.reminderTime == null ->
                    GlucoseScheduleError("Selecciona la hora del aviso.")
            }
        }
        if (showMeasurementContext) {
            GlucoseSuggestedMomentSelector(
                selected = slot.context,
                showError = commitAttempted && slot.context == null,
                options = contextOptions,
                onSelected = {
                    onChange(slot.copy(context = it))
                },
            )
        }
        if (scheduleConflict && commitAttempted) {
            GlucoseScheduleError(
                "Ese horario se duplica o se superpone con otro.",
            )
        }
        GlucoseScheduleEditorActions(
            editing = editing,
            onCancel = onCancel,
            onCommit = onCommit,
        )
    }
}

@Composable
private fun GlucoseSuggestedMomentSelector(
    selected: GlucoseContext?,
    showError: Boolean,
    options: List<GlucoseContext>,
    onSelected: (GlucoseContext) -> Unit,
) {
    UnitSelector(
        title = "Momento sugerido",
        options = options.map(GlucoseContext::label),
        selected = selected?.label().orEmpty(),
        onSelected = { label ->
            options.firstOrNull { it.label() == label }
                ?.let(onSelected)
        },
        hasError = showError,
        allowClearSelection = false,
    )
    if (showError) {
        GlucoseScheduleError("Selecciona el momento sugerido.")
    }
}

@Composable
private fun GlucoseScheduleEditorActions(
    editing: Boolean,
    onCancel: () -> Unit,
    onCommit: () -> Unit,
) {
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
            Text(if (editing) "Guardar cambios" else "Agregar horario")
        }
    }
}

@Composable
private fun GlucoseScheduleError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

private fun GlucoseFollowUpSlotDraft.scheduleLabel(): String = when (timingType) {
    GlucoseFollowUpTimingType.EXACT_TIME ->
        time?.format(followUpTimeFormatter).orEmpty()
    GlucoseFollowUpTimingType.TIME_WINDOW ->
        listOfNotNull(
            time?.format(followUpTimeFormatter),
            windowEnd?.format(followUpTimeFormatter),
        ).joinToString(" – ") + if (endsNextDay) " · día siguiente" else ""
    null -> "Horario incompleto"
}

@Composable
private fun RemindersStep(
    draft: GlucoseFollowUpDraft,
    onRequestPermission: () -> Unit,
    onChange: (GlucoseFollowUpDraft) -> Unit,
) {
    GuidedRegistrationStepHeading(
        "Avisos del celular",
        "Decide si Android enviará notificaciones para los avisos configurados.",
    )
    GuidedRegistrationChoiceRow(
        firstLabel = "No recibir avisos",
        secondLabel = "Sí, recibir avisos",
        firstSelected = draft.remindersEnabled == false,
        secondSelected = draft.remindersEnabled == true,
        onFirst = { onChange(draft.copy(remindersEnabled = false)) },
        onSecond = {
            onChange(draft.copy(remindersEnabled = true))
            onRequestPermission()
        },
    )
    Text(
        text = if (draft.remindersEnabled == true) {
            "Bitavida solicitará el permiso del sistema cuando corresponda. Puedes guardar aunque lo rechaces."
        } else {
            "La programación seguirá visible en Bitavida aunque no haya notificaciones."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "La falta de confirmación nunca se convertirá automáticamente en una omisión.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReviewStep(
    draft: GlucoseFollowUpDraft,
    saving: Boolean,
    error: String?,
    measurementTitle: String,
    measurementIcon: Int,
    showMeasurementContext: Boolean,
    onEdit: (GlucoseFollowUpStep) -> Unit,
    onConfirm: () -> Unit,
) {
    GuidedRegistrationStepHeading(
        "Revisar seguimiento",
        "Confirma la indicación, programación y avisos antes de guardar.",
    )
    BitavidaConnectedSurface(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.bitavidaColors.mint,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeasurementIcon(measurementIcon, 30)
            Text(
                measurementTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        GuidedRegistrationReviewRow(
            "Indicación",
            draft.indication,
            "Editar indicación",
        ) { onEdit(GlucoseFollowUpStep.INDICATION) }
        GuidedRegistrationReviewRow(
            "Frecuencia",
            draft.frequency.reviewLabel(draft),
            "Editar frecuencia",
        ) { onEdit(GlucoseFollowUpStep.FREQUENCY) }
        GuidedRegistrationReviewRow(
            "Horarios",
            draft.slots.joinToString("\n") {
                it.reviewLabel(showMeasurementContext)
            },
            "Editar horarios",
        ) { onEdit(GlucoseFollowUpStep.SCHEDULES) }
        GuidedRegistrationReviewRow(
            "Avisos del celular",
            draft.remindersLabel(),
            "Editar avisos del celular",
        ) { onEdit(GlucoseFollowUpStep.REMINDERS) }
    }
    error?.let {
        BitavidaConnectedSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.bitavidaColors.roseStrong,
        ) {
            Text(it, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    BitavidaThreadAction(
        label = if (saving) "Guardando seguimiento…" else "Confirmar seguimiento",
        iconRes = measurementIcon,
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth(),
        emphasized = true,
        enabled = !saving,
    )
}

@Composable
internal fun GlucoseFollowUpHistoryContent(
    followUp: GlucoseFollowUp?,
    showMeasurementContext: Boolean = true,
    removedLabel: String = "Desactivado",
) {
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    val revisions = followUp?.revisions.orEmpty()
    val entries = revisions.indices.asSequence()
        .map { index ->
            revisions[index] to revisions.getOrNull(index - 1)
        }
        .sortedByDescending { (revision, _) -> revision.capturedAt }
        .toList()
    val grouped = entries.groupBy { (revision, _) -> revision.capturedAt.toLocalDate() }

    Text(
        "Historial de cambios",
        modifier = Modifier.fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
    )
    if (entries.isEmpty()) {
        MeasurementPanel {
            SecondaryText("Sin cambios registrados")
        }
    }
    grouped.forEach { (day, revisions) ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                day.format(followUpDateFormatter),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            revisions.forEachIndexed { index, (revision, previous) ->
                GlucoseFollowUpHistoryEvent(
                    revision = revision,
                    previous = previous,
                    isLastOfDay = index == revisions.lastIndex,
                    duration = duration,
                    showMeasurementContext = showMeasurementContext,
                    removedLabel = removedLabel,
                )
            }
        }
    }
}

@Composable
private fun GlucoseFollowUpHistoryEvent(
    revision: GlucoseFollowUpRevision,
    previous: GlucoseFollowUpRevision?,
    isLastOfDay: Boolean,
    duration: Int,
    showMeasurementContext: Boolean,
    removedLabel: String,
) {
    val changes = glucoseFollowUpFieldChanges(
        previous = previous,
        current = revision,
        showMeasurementContext = showMeasurementContext,
    )
    var expanded by rememberSaveable(revision.id) {
        mutableStateOf(previous != null && changes.size <= 1)
    }
    val threadColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier.fillMaxWidth().drawBehind {
            if (!isLastOfDay) {
                drawLine(
                    color = threadColor,
                    start = androidx.compose.ui.geometry.Offset(
                        18.dp.toPx(),
                        30.dp.toPx(),
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        18.dp.toPx(),
                        size.height,
                    ),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        },
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        verticalAlignment = Alignment.Top,
    ) {
        HistoryMarker(revision.changeKind.historyIcon())
        Column(
            modifier = Modifier.weight(1f).padding(
                bottom = if (isLastOfDay) {
                    BitavidaSpacing.Default
                } else {
                    BitavidaSpacing.Large
                },
            ),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                Text(
                    revision.capturedAt.toLocalTime().format(followUpTimeFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HistoryKindLabel(
                    if (revision.changeKind == GlucoseFollowUpChangeKind.REMOVED) {
                        removedLabel
                    } else {
                        revision.changeKind.historyLabel()
                    },
                )
            }
            Text(
                if (previous == null) {
                    "Configuración inicial del seguimiento."
                } else {
                    "Revisión " + revision.revisionNumber
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RevisionChanges(
                changes = changes,
                expanded = expanded,
                duration = duration,
                onToggle = { expanded = !expanded },
            )
            revision.changeReason?.takeIf(String::isNotBlank)?.let {
                Text(
                    "Motivo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun glucoseFollowUpFieldChanges(
    previous: GlucoseFollowUpRevision?,
    current: GlucoseFollowUpRevision,
    showMeasurementContext: Boolean,
): List<MedicationFieldChange> {
    val before = previous?.historyFields(showMeasurementContext).orEmpty()
    val after = current.historyFields(showMeasurementContext)
    return (before.keys + after.keys)
        .asSequence()
        .distinct()
        .mapNotNull { field ->
            val old = before[field]
            val new = after[field]
            when {
                previous == null -> MedicationFieldChange(field, null, new)
                old == new -> null
                else -> MedicationFieldChange(field, old, new)
            }
        }
        .toList()
}

private fun GlucoseFollowUpRevision.historyFields(
    showMeasurementContext: Boolean,
): Map<String, String> = linkedMapOf(
    "Indicación médica" to indication,
    "Frecuencia" to historyFrequencyLabel(),
    "Horarios" to slots.joinToString(separator = "\n") {
        it.historyLabel(showMeasurementContext)
    },
    "Avisos del celular" to if (remindersEnabled) "Activados" else "Desactivados",
    "Estado" to if (active) "Activo" else "Desactivado",
)

private fun GlucoseFollowUpRevision.historyFrequencyLabel(): String = when (frequency) {
    GlucoseFollowUpFrequency.DAILY -> "Todos los días"
    GlucoseFollowUpFrequency.SPECIFIC_DAYS ->
        weekdays.asSequence().sortedBy(DayOfWeek::getValue)
            .joinToString { it.shortLabel() }
    GlucoseFollowUpFrequency.EVERY_N_DAYS ->
        "Cada $everyNDaysInterval días"
}

private fun GlucoseFollowUpSlot.historyLabel(
    showMeasurementContext: Boolean,
): String = listOfNotNull(
    scheduleHistoryLabel(),
    suggestedContext.label().takeIf { showMeasurementContext },
    "aviso " + (reminderTime?.format(followUpTimeFormatter) ?: "sin hora") +
        if (reminderNextDay) " del día siguiente" else "",
).joinToString(" · ")

private fun GlucoseFollowUpSlot.scheduleHistoryLabel(): String = when (timingType) {
    GlucoseFollowUpTimingType.EXACT_TIME -> time.format(followUpTimeFormatter)
    GlucoseFollowUpTimingType.TIME_WINDOW ->
        time.format(followUpTimeFormatter) + " – " +
            requireNotNull(windowEnd).format(followUpTimeFormatter) +
            if (endsNextDay) " · día siguiente" else ""
}

private fun GlucoseFollowUpChangeKind.historyIcon(): Int = when (this) {
    GlucoseFollowUpChangeKind.CREATED -> R.drawable.ic_v2_plus
    GlucoseFollowUpChangeKind.INDICATION_CHANGED -> R.drawable.ic_v2_stethoscope
    GlucoseFollowUpChangeKind.REMOVED -> R.drawable.ic_v2_pause
    GlucoseFollowUpChangeKind.CORRECTED -> R.drawable.ic_v2_edit
}


@Composable
internal fun GlucoseDeleteFollowUpContent(
    saving: Boolean,
    error: String?,
    onConfirm: () -> Unit,
) {
    MeasurementPanel {
        SectionTitle("Desactivar seguimiento", small = true)
        SecondaryText(
            "Se detendrán horarios y avisos futuros. Las mediciones, " +
                "indicaciones anteriores y auditoría se conservarán.",
        )
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        MeasurementButton(
            label = "Desactivar seguimiento",
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            style = MeasurementButtonStyle.OUTLINED,
            enabled = !saving,
        )
    }
}

private fun stepIsValid(
    step: GlucoseFollowUpStep,
    draft: GlucoseFollowUpDraft,
) = when (step) {
    GlucoseFollowUpStep.INDICATION ->
        draft.indication.isNotBlank()
    GlucoseFollowUpStep.FREQUENCY ->
        draft.frequency != null &&
            (
                draft.frequency != GlucoseFollowUpFrequency.SPECIFIC_DAYS ||
                    draft.weekdays.isNotEmpty()
                ) &&
            (
                draft.frequency != GlucoseFollowUpFrequency.EVERY_N_DAYS ||
                    draft.everyNDaysInterval in 1..365
                )
    GlucoseFollowUpStep.SCHEDULES ->
        draft.slots.isNotEmpty() &&
            draft.slots.all {
                it.time != null && it.context != null && it.reminderTime != null
            } &&
            draft.slots.map { it.time }.distinct().size == draft.slots.size
    GlucoseFollowUpStep.REMINDERS ->
        draft.remindersEnabled != null
    GlucoseFollowUpStep.REVIEW -> true
}

private fun stepError(
    step: GlucoseFollowUpStep,
    showMeasurementContext: Boolean,
) = when (step) {
    GlucoseFollowUpStep.INDICATION ->
        "Escribe la indicación médica transcrita."
    GlucoseFollowUpStep.FREQUENCY ->
        "Selecciona una frecuencia y completa sus datos."
    GlucoseFollowUpStep.SCHEDULES -> if (showMeasurementContext) {
        "Agrega al menos un horario con hora, momento y aviso, sin duplicados."
    } else {
        "Agrega al menos un horario con hora y aviso, sin duplicados."
    }
    GlucoseFollowUpStep.REMINDERS ->
        "Elige si deseas recibir notificaciones en el celular."
    GlucoseFollowUpStep.REVIEW -> ""
}

private fun DayOfWeek.shortLabel() =
    listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")[ordinal]

private fun GlucoseContext.label() = when (this) {
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

private fun GlucoseFollowUpFrequency?.reviewLabel(
    draft: GlucoseFollowUpDraft,
) = when (this) {
    GlucoseFollowUpFrequency.DAILY -> "Todos los días"
    GlucoseFollowUpFrequency.SPECIFIC_DAYS ->
        draft.weekdays.asSequence()
            .sortedBy(DayOfWeek::getValue)
            .joinToString { it.shortLabel() }
    GlucoseFollowUpFrequency.EVERY_N_DAYS ->
        "Cada " + draft.everyNDaysInterval + " días"
    null -> "Sin seleccionar"
}

private fun GlucoseFollowUpSlotDraft.reviewLabel(
    showMeasurementContext: Boolean,
): String = listOfNotNull(
        scheduleLabel(),
        context?.label()?.takeIf { showMeasurementContext },
        reminderTime?.let {
            "aviso " + it.format(followUpTimeFormatter) +
                if (reminderNextDay) " del día siguiente" else ""
        },
    ).joinToString(" · ")

private fun GlucoseFollowUpDraft.remindersLabel(): String = when (remindersEnabled) {
    false -> "Notificaciones del celular desactivadas"
    true -> "Notificaciones del celular activadas"
    null -> "Sin seleccionar"
}

private fun GlucoseFollowUpChangeKind.historyLabel() = when (this) {
    GlucoseFollowUpChangeKind.CREATED -> "Creado"
    GlucoseFollowUpChangeKind.CORRECTED -> "Corrección"
    GlucoseFollowUpChangeKind.INDICATION_CHANGED -> "Nueva indicación"
    GlucoseFollowUpChangeKind.REMOVED -> "Desactivado"
}
