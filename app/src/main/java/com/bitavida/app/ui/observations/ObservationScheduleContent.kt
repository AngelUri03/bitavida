package com.bitavida.app.ui.observations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.*
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.*
import com.bitavida.app.ui.measurements.*
import com.bitavida.app.ui.medical.*
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private val scheduleLocale = Locale.forLanguageTag("es-MX")
private val scheduleTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(scheduleLocale)

@Composable
internal fun ObservationScheduleContent(
    state: ObservationsUiState,
    motionAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onAction: (ObservationsAction) -> Unit,
    personName: String = "",
) {
    val draft = state.scheduleDraft ?: return
    val schedule = draft.schedule
    val editor = state.scheduleSlotEditor
    val editingId = state.scheduleEditingSlotId
    var attempted by remember(editor?.id) { mutableStateOf(false) }
    val definition = ObservationCatalog.get(draft.observationId)
    val labels = listOf("Indicación médica", "Frecuencia", "Horarios", "Avisos del celular", "Revisar y confirmar")
    val step = state.scheduleStep.coerceIn(0, 4)
    val change: (ObservationScheduleDraft) -> Unit = { onAction(ObservationsAction.Schedule(it)) }
    GuidedRegistrationHeader(
        title = when (schedule.changeKind) {
            GlucoseFollowUpChangeKind.CREATED -> "Configurar seguimiento"
            GlucoseFollowUpChangeKind.INDICATION_CHANGED -> "Cambiar indicación médica"
            else -> "Corregir seguimiento"
        },
        personLabel = if (personName.isBlank()) definition.name else "Acompañando a " + personName.substringBefore(' '),
        icon = painterResource(R.drawable.ic_v2_stethoscope), iconScale = 1f,
        focusManager = LocalFocusManager.current,
        onBack = { if (editor != null) onAction(ObservationsAction.ScheduleSlotEditor(null)) else onAction(ObservationsAction.PreviousSchedule) },
        onContinue = if (step == 4) null else { { onAction(ObservationsAction.NextSchedule) } },
        continueLabel = if (step == 3) "Revisar" else "Continuar",
        continueEnabled = !state.saving && editor == null, backEnabled = !state.saving,
    )
    Text("Paso " + (step + 1) + " de 5", style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    GuidedRegistrationStepProgress(step, 5, motionAllowed)
    GuidedRegistrationStepTransition(step, motionAllowed) { shown ->
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
            GuidedRegistrationStepHeading(labels[shown], definition.name)
            when (shown) {
                0 -> {
                    SecondaryText("Transcribe la indicación recibida. Bitavida no indica qué revisiones necesita la persona.")
                    BitavidaLimitedTextField(schedule.indication, {
                        change(draft.copy(schedule = schedule.copy(indication = it)))
                    }, CaptureLimit.CARE_INSTRUCTION, Modifier.fillMaxWidth(),
                        additionalInputValidation = { fitsObservationTextCapture(it, Int.MAX_VALUE, schedule.indication) },
                        label = { Text("Indicación médica transcrita *") }, minLines = 3,
                        shape = MaterialTheme.shapes.medium)
                }
                1 -> {
                    GlucoseFollowUpFrequency.entries.forEach { frequency ->
                        GuidedRegistrationChoice(when (frequency) {
                            GlucoseFollowUpFrequency.DAILY -> "Todos los días"
                            GlucoseFollowUpFrequency.SPECIFIC_DAYS -> "Días específicos"
                            GlucoseFollowUpFrequency.EVERY_N_DAYS -> "Cada cierto número de días"
                        }, schedule.frequency == frequency, {
                            change(draft.copy(schedule = schedule.copy(frequency = frequency)))
                        }, Modifier.fillMaxWidth())
                    }
                    if (schedule.frequency == GlucoseFollowUpFrequency.SPECIFIC_DAYS) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                            DayOfWeek.entries.forEach { day ->
                                GlucoseCaptureChip(day.getDisplayName(TextStyle.FULL, scheduleLocale),
                                    day in schedule.weekdays, !state.saving, motionAllowed, {
                                        val days = if (day in schedule.weekdays) schedule.weekdays - day else schedule.weekdays + day
                                        change(draft.copy(schedule = schedule.copy(weekdays = days)))
                                    })
                            }
                        }
                    }
                    if (schedule.frequency == GlucoseFollowUpFrequency.EVERY_N_DAYS) {
                        BitavidaLimitedTextField(schedule.everyNDaysInterval.takeIf { it > 0 }?.toString().orEmpty(), {
                            change(draft.copy(schedule = schedule.copy(everyNDaysInterval = it.toIntOrNull() ?: 0)))
                        }, CaptureLimit.CYCLE_COUNT, Modifier.fillMaxWidth(),
                            label = { Text("Cada cuántos días (1 a 365)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.medium)
                    }
                }
                2 -> {
                    SecondaryText("Configura una o varias horas exactas o ventanas. El aviso no sustituye la hora real del registro.")
                    schedule.slots.forEachIndexed { index, slot ->
                        MeasurementPanel {
                            GuidedAdministrationHeader(
                                title = "Revisión " + (index + 1), subtitle = slot.scheduleLabel(),
                                supportingText = slot.reminderTime?.let { "Aviso: " + it.format(scheduleTime) +
                                    if (slot.reminderNextDay) " del día siguiente" else "" },
                                editDescription = "Editar revisión " + (index + 1),
                                removeDescription = "Quitar revisión " + (index + 1),
                                onEdit = { onAction(ObservationsAction.ScheduleSlotEditor(slot, slot.id)); attempted = false },
                                onRemove = { change(draft.copy(schedule = schedule.copy(slots = schedule.slots.filterNot { it.id == slot.id }))) },
                            )
                        }
                    }
                    if (editor == null && schedule.slots.size < 12) {
                        GuidedAdministrationEditorAction(false, {
                            onAction(ObservationsAction.ScheduleSlotEditor(
                                GlucoseFollowUpSlotDraft(UUID.randomUUID().toString(), context = GlucoseContext.UNSPECIFIED)))
                            attempted = false
                        }, addLabel = "Agregar horario")
                    }
                    val currentEditor = editor
                    GuidedAdministrationEditorSurface(currentEditor != null, motionAllowed) {
                        if (currentEditor != null) {
                            val candidates = schedule.slots.filterNot { it.id == editingId } + currentEditor
                            val conflict = glucoseFollowUpSchedulesConflict(candidates)
                            ObservationScheduleEditor(currentEditor, editingId != null, attempted, conflict,
                                { onAction(ObservationsAction.ScheduleSlotEditor(it, editingId)) },
                                { onAction(ObservationsAction.ScheduleSlotEditor(null)) }, {
                                    attempted = true
                                    if (glucoseFollowUpSlotIsComplete(currentEditor) && !conflict) {
                                        val next = if (editingId == null) schedule.slots + currentEditor
                                            else schedule.slots.map { if (it.id == editingId) currentEditor else it }
                                        change(draft.copy(schedule = schedule.copy(slots = next)))
                                        onAction(ObservationsAction.ScheduleSlotEditor(null))
                                    }
                                })
                        }
                    }
                }
                3 -> {
                    GuidedRegistrationChoiceRow("Con avisos", "Sin avisos",
                        schedule.remindersEnabled == true, schedule.remindersEnabled == false, {
                            change(draft.copy(schedule = schedule.copy(remindersEnabled = true)))
                            onRequestNotificationPermission()
                        }, { change(draft.copy(schedule = schedule.copy(remindersEnabled = false))) })
                    SecondaryText("Los avisos dependen de los permisos del dispositivo. Ignorarlos no significa que se realizó o dejó de realizar una revisión.")
                }
                4 -> {
                    BitavidaConnectedSurface(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.bitavidaColors.mint,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            BitavidaIconContainer(
                                painter = painterResource(observationIcon(definition.category)),
                                contentDescription = null,
                                containerColor = MaterialTheme.bitavidaColors.paleBlue,
                            )
                            Text(
                                text = definition.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        GuidedRegistrationReviewRow(
                            "Indicación médica",
                            schedule.indication,
                            "Editar indicación",
                        ) { onAction(ObservationsAction.ScheduleStep(0)) }
                        GuidedRegistrationReviewRow(
                            "Frecuencia",
                            schedule.frequencyLabel(),
                            "Editar frecuencia",
                        ) { onAction(ObservationsAction.ScheduleStep(1)) }
                        GuidedRegistrationReviewRow(
                            "Horarios",
                            schedule.slots.joinToString("\n") { it.scheduleLabel() },
                            "Editar horarios",
                        ) { onAction(ObservationsAction.ScheduleStep(2)) }
                        GuidedRegistrationReviewRow(
                            "Avisos del celular",
                            if (schedule.remindersEnabled == true) "Configurados" else "Sin avisos",
                            "Editar avisos del celular",
                        ) { onAction(ObservationsAction.ScheduleStep(3)) }
                    }
                    SecondaryText(
                        "El seguimiento se activa al confirmar y continúa hasta que lo elimines. " +
                            "Las versiones anteriores permanecen en el historial.",
                    )
                    BitavidaThreadAction(
                        label = if (state.saving) "Guardando…" else "Confirmar seguimiento",
                        iconRes = R.drawable.ic_v2_plus,
                        onClick = { onAction(ObservationsAction.SaveSchedule) },
                        modifier = Modifier.fillMaxWidth(),
                        emphasized = true,
                        enabled = !state.saving,
                        motionEnabledOverride = motionAllowed,
                    )
                }
            }
            state.error?.let { CaptureError(it) }
        }
    }
}

@Composable
private fun ObservationScheduleEditor(
    slot: GlucoseFollowUpSlotDraft,
    editing: Boolean,
    attempted: Boolean,
    conflict: Boolean,
    onChange: (GlucoseFollowUpSlotDraft) -> Unit,
    onCancel: () -> Unit,
    onCommit: () -> Unit,
) {
    val picker = LocalBitavidaDateTimePickerController.current
    SectionTitle("Tipo de programación", small = true)
    GuidedRegistrationChoiceRow("Hora exacta", "Ventana horaria",
        slot.timingType == GlucoseFollowUpTimingType.EXACT_TIME,
        slot.timingType == GlucoseFollowUpTimingType.TIME_WINDOW, {
            onChange(slot.copy(timingType = GlucoseFollowUpTimingType.EXACT_TIME, windowEnd = null,
                endsNextDay = false, reminderNextDay = false,
                useSameTimeForReminder = null, reminderTime = null))
        }, {
            onChange(slot.copy(timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
                windowEnd = null, endsNextDay = false, reminderNextDay = false,
                useSameTimeForReminder = null, reminderTime = null))
        })
    when (slot.timingType) {
        GlucoseFollowUpTimingType.EXACT_TIME -> {
            GuidedTimeSelectionRow("Hora de revisión", slot.time, { it.format(scheduleTime) }, {
                picker.showTime(slot.time ?: LocalTime.now()) {
                    onChange(slot.copy(time = it,
                        reminderTime = if (slot.useSameTimeForReminder == true) it else slot.reminderTime))
                }
            })
            SectionTitle("Momento del aviso", small = true)
            GuidedRegistrationChoiceRow("A la misma hora", "A otra hora",
                slot.useSameTimeForReminder == true, slot.useSameTimeForReminder == false, {
                    onChange(slot.copy(useSameTimeForReminder = true, reminderEnabled = true, reminderTime = slot.time))
                }, {
                    onChange(slot.copy(useSameTimeForReminder = false, reminderEnabled = true, reminderTime = null))
                })
            if (slot.useSameTimeForReminder == false) GuidedTimeSelectionRow("Aviso", slot.reminderTime,
                { it.format(scheduleTime) }, {
                    picker.showTime(slot.reminderTime ?: slot.time ?: LocalTime.now()) {
                        onChange(slot.copy(reminderEnabled = true, reminderTime = it))
                    }
                })
            if (attempted && !glucoseFollowUpSlotIsComplete(slot)) CaptureError("Selecciona la hora y el momento del aviso.")
            if (attempted && conflict) CaptureError("Este horario se duplica o se superpone con otro.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                MeasurementButton("Cancelar", onCancel, style = MeasurementButtonStyle.TEXT)
                MeasurementButton(if (editing) "Guardar horario" else "Agregar horario", onCommit,
                    style = MeasurementButtonStyle.TEXT)
            }
        }
        GlucoseFollowUpTimingType.TIME_WINDOW -> {
            val start = slot.time
            val end = slot.windowEnd
            val reminder = slot.reminderTime
            val reminderNext = slot.endsNextDay && reminder != null && start != null && reminder < start
            val windowError = when {
                start == null || end == null -> "Selecciona inicio y final."
                start == end -> "El inicio y el final deben ser diferentes."
                !slot.endsNextDay && end < start -> "Indica si termina al día siguiente."
                else -> null
            }
            val reminderError = if (reminder == null) "Selecciona la hora del aviso."
                else if (start != null && end != null && !reminder.isWithinGlucoseWindow(start, end,
                        slot.endsNextDay, reminderNext)) "El aviso debe quedar dentro de la ventana." else null
            GuidedTimeWindowEditorContent(
                editing = editing, start = start, end = end, reminder = reminder, endsNextDay = slot.endsNextDay,
                formatTime = { it.format(scheduleTime) },
                onSelectTime = { initial, selected -> picker.showTime(initial ?: LocalTime.now(), onConfirm = selected) },
                onStartChange = { time -> onChange(slot.copy(time = time,
                    reminderNextDay = slot.endsNextDay && slot.reminderTime?.let { it < time } == true)) },
                onEndChange = { onChange(slot.copy(windowEnd = it)) },
                onReminderChange = { time -> onChange(slot.copy(reminderTime = time, reminderEnabled = true,
                    reminderNextDay = slot.endsNextDay && slot.time?.let { time < it } == true)) },
                onEndsNextDayChange = { next -> onChange(slot.copy(endsNextDay = next,
                    reminderNextDay = next && slot.time != null && slot.reminderTime?.let { it < slot.time } == true)) },
                windowError = windowError.takeIf { attempted }, reminderError = reminderError.takeIf { attempted },
                reminderRequired = true, onClearReminder = {},
                additionalContent = { if (attempted && conflict) CaptureError("Esta ventana se superpone con otro horario.") },
                onCommit = onCommit, onCancel = onCancel,
            )
        }
        null -> SecondaryText("Elige cómo se programará esta revisión.")
    }
}

internal fun GlucoseFollowUpSlotDraft.scheduleLabel(): String = when (timingType) {
    GlucoseFollowUpTimingType.EXACT_TIME -> time?.format(scheduleTime) ?: "Hora pendiente"
    GlucoseFollowUpTimingType.TIME_WINDOW -> (time?.format(scheduleTime) ?: "Inicio pendiente") +
        " – " + (windowEnd?.format(scheduleTime) ?: "Final pendiente") + if (endsNextDay) " del día siguiente" else ""
    null -> "Tipo de programación pendiente"
}

private fun GlucoseFollowUpDraft.frequencyLabel(): String = when (frequency) {
    GlucoseFollowUpFrequency.DAILY -> "Todos los días"
    GlucoseFollowUpFrequency.SPECIFIC_DAYS -> weekdays.sortedBy { it.value }
        .joinToString { it.getDisplayName(TextStyle.FULL, scheduleLocale) }
    GlucoseFollowUpFrequency.EVERY_N_DAYS -> "Cada " + everyNDaysInterval + " días"
    null -> "Frecuencia pendiente"
}

@Composable
internal fun ObservationFollowUpHistory(state: ObservationsUiState) {
    val followUp = state.snapshot.followUps.firstOrNull { it.observationId == state.historyObservationId }
    state.historyObservationId?.let { SectionTitle(ObservationCatalog.get(it).name, small = true) }
    GlucoseFollowUpHistoryContent(
        followUp = followUp?.schedule,
        showMeasurementContext = false,
        removedLabel = "Eliminado",
    )
}
