package com.bitavida.app.ui.appointments

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaConnectedSurface
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.components.BitavidaDatePickerConstraints
import com.bitavida.app.ui.components.BitavidaTimePickerConstraints
import java.time.temporal.ChronoUnit
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.medical.GuidedRegistrationStepProgress
import com.bitavida.app.ui.medical.GuidedRegistrationStepTransition
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.measurements.GlucoseCaptureChip
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.measurements.MeasurementPanel
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val wizardLocale = Locale.forLanguageTag("es-MX")
private val wizardDate = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", wizardLocale)
private val wizardTime = DateTimeFormatter.ofPattern("h:mm a", wizardLocale)

private val purposeOptions = listOf(
    CareEventPurpose.MEDICAL_CONSULTATION to "Consulta",
    CareEventPurpose.STUDY to "Estudio",
    CareEventPurpose.PROCEDURE to "Procedimiento",
    CareEventPurpose.ADMINISTRATIVE to "Trámite o gestión",
    CareEventPurpose.OTHER to "Otra cita",
)
private fun purposeText(purpose: CareEventPurpose): String =
    purposeOptions.firstOrNull { it.first == purpose }?.second ?: "Consulta médica"

private fun purposeIcon(purpose: CareEventPurpose): Int = when (purpose) {
    CareEventPurpose.MEDICAL_CONSULTATION, CareEventPurpose.LEGACY -> R.drawable.ic_v2_stethoscope
    CareEventPurpose.STUDY -> R.drawable.ic_v2_clipboard
    CareEventPurpose.PROCEDURE -> R.drawable.ic_v2_pill
    CareEventPurpose.ADMINISTRATIVE -> R.drawable.ic_v2_file
    CareEventPurpose.OTHER -> R.drawable.ic_v2_plus
}

private fun preparationIcon(preparation: CareEventPreparation): Int = when (preparation) {
    CareEventPreparation.FASTING -> R.drawable.ic_v2_utensils
    CareEventPreparation.PREVIOUS_STUDIES, CareEventPreparation.PRESCRIPTION_OR_CARD -> R.drawable.ic_v2_file
    CareEventPreparation.ARRIVE_EARLY -> R.drawable.ic_v2_clock
    CareEventPreparation.ACCOMPANIED -> R.drawable.ic_v2_home
    CareEventPreparation.OTHER -> R.drawable.ic_v2_plus
}

private fun inferredPurpose(draft: CareEventDraft): CareEventPurpose = when {
    draft.purpose != CareEventPurpose.LEGACY -> draft.purpose
    draft.category == CareEventCategory.ADMINISTRATIVE -> CareEventPurpose.ADMINISTRATIVE
    draft.kind == CareEventKind.STUDY || draft.category in setOf(CareEventCategory.LABORATORY, CareEventCategory.IMAGING) -> CareEventPurpose.STUDY
    else -> CareEventPurpose.MEDICAL_CONSULTATION
}

private fun scheduledKind(purpose: CareEventPurpose): CareEventKind =
    if (purpose == CareEventPurpose.STUDY) CareEventKind.STUDY else CareEventKind.APPOINTMENT

private fun preparationText(value: CareEventPreparation): String = when (value) {
    CareEventPreparation.FASTING -> "Ayuno"
    CareEventPreparation.PREVIOUS_STUDIES -> "Llevar estudios previos"
    CareEventPreparation.PRESCRIPTION_OR_CARD -> "Llevar receta o carnet"
    CareEventPreparation.ARRIVE_EARLY -> "Llegar 15 minutos antes"
    CareEventPreparation.ACCOMPANIED -> "Ir acompañado"
    CareEventPreparation.OTHER -> "Otro"
}
private fun reminderText(minutes: Int) = when (minutes) {
    1440 -> "1 día antes"
    120 -> "2 horas antes"
    30 -> "30 minutos antes"
    else -> "$minutes minutos antes"
}

private fun effectiveTimeMode(draft: CareEventDraft): CareEventTimeMode =
    if (draft.timeMode != CareEventTimeMode.LEGACY) draft.timeMode
    else when {
        draft.scheduledAt != null -> CareEventTimeMode.EXACT
        draft.scheduledDate != null -> CareEventTimeMode.TIME_UNCONFIRMED
        else -> CareEventTimeMode.TO_SCHEDULE
    }

private fun selectedOffsets(draft: CareEventDraft): Set<Int> =
    if (draft.reminderOffsetsMinutes.isNotEmpty()) draft.reminderOffsetsMinutes
    else if (draft.reminderEnabled) setOf(draft.reminderMinutesBefore) else emptySet()

internal fun toggleWizardReminder(draft: CareEventDraft, minutes: Int): CareEventDraft {
    require(minutes in setOf(30, 120, 1440))
    val supported = if (draft.reminderEnabled) selectedOffsets(draft).filter { it in setOf(30, 120, 1440) }.toSet() else emptySet()
    val next = if (minutes in supported) supported - minutes else supported + minutes
    return draft.copy(reminderOffsetsMinutes = next, reminderEnabled = next.isNotEmpty(),
        reminderMinutesBefore = next.firstOrNull() ?: draft.reminderMinutesBefore)
}

internal fun clearWizardReminders(draft: CareEventDraft): CareEventDraft =
    draft.copy(reminderEnabled = false, reminderOffsetsMinutes = emptySet())

internal fun reminderIsPast(dateTime: LocalDateTime, minutes: Int, now: LocalDateTime): Boolean =
    !dateTime.minusMinutes(minutes.toLong()).isAfter(now)

/** One draft in the existing ViewModel: steps and confirmation do not persist partial events. */
@Composable
internal fun CareEventWizard(
    state: CareEventsState,
    onAction: (CareEventsAction) -> Unit,
    person: String,
    onClose: () -> Unit,
    initialStep: Int = 0,
) {
    val draft = state.draft ?: return
    val motion = rememberBitavidaMotionAllowed(null)
    val picker = LocalBitavidaDateTimePickerController.current
    val focusManager = LocalFocusManager.current
    var step by rememberSaveable(state.draftId) { mutableIntStateOf(initialStep.coerceIn(0, 5)) }
    var fromReview by rememberSaveable(state.draftId) { mutableStateOf(false) }
    var attempted by rememberSaveable(state.draftId) { mutableStateOf(false) }
    var confirmExit by remember(state.draftId) { mutableStateOf(false) }
    var closing by remember(state.draftId) { mutableStateOf(false) }
    var duplicateAccepted by remember(draft.title, draft.category, draft.scheduledDate, draft.scheduledAt) { mutableStateOf(false) }
    var searchPlaces by remember(state.draftId) { mutableStateOf(false) }
    var searchTitle by remember(state.draftId) { mutableStateOf(false) }
    var searchSpecialty by remember(state.draftId) { mutableStateOf(false) }
    var previousUnitId by rememberSaveable(state.draftId) { mutableStateOf(draft.verifiedUnitId) }
    var pendingUnit by remember(state.draftId) { mutableStateOf<ImssUnit?>(null) }
    val purpose = if (state.editing != null || draft.purpose != CareEventPurpose.LEGACY) inferredPurpose(draft) else null
    val mode = effectiveTimeMode(draft)
    val date = draft.scheduledAt?.toLocalDate() ?: draft.scheduledDate
    val time = draft.scheduledAt?.toLocalTime()
    val duplicate = findPossibleDuplicate(draft, state.events, state.editing?.id)
    val terminal = state.editing?.draft?.status in setOf(CareEventStatus.CANCELLED, CareEventStatus.COMPLETED,
        CareEventStatus.PERFORMED, CareEventStatus.RESULT_PENDING, CareEventStatus.RESULT_AVAILABLE, CareEventStatus.REVIEWED)
    fun update(value: CareEventDraft) = onAction(CareEventsAction.Change(value))
    fun closeSuggestions() {
        focusManager.clearFocus()
        searchPlaces = false
        searchTitle = false
        searchSpecialty = false
    }
    fun exitOnce() {
        if (!closing) {
            closing = true
            onClose()
        }
    }
    fun back() {
        when (careEventBackDecision(state.saving || state.photoPreparing || closing, picker.isActive, confirmExit, step,
            hasCareEventDraftChanges(draft, state.initialDraft) || state.pendingPhotos.isNotEmpty() ||
                state.removedPhotoIds.isNotEmpty())) {
            CareEventBackDecision.IGNORE -> Unit
            CareEventBackDecision.DISMISS_PICKER -> picker.dismiss()
            CareEventBackDecision.CLOSE_CONFIRMATION -> confirmExit = false
            CareEventBackDecision.PREVIOUS_STEP -> { closeSuggestions(); step -= 1; attempted = false; fromReview = false }
            CareEventBackDecision.ASK_DISCARD -> confirmExit = true
            CareEventBackDecision.EXIT -> exitOnce()
        }
    }
    val legacyUnspecified = state.editing?.draft?.let {
        it.consultationType == CareConsultationType.UNSPECIFIED &&
            it.purpose in setOf(CareEventPurpose.LEGACY, CareEventPurpose.MEDICAL_CONSULTATION)
    } == true
    val consultationValid = purpose != CareEventPurpose.MEDICAL_CONSULTATION ||
        (draft.consultationType != CareConsultationType.UNSPECIFIED || (legacyUnspecified && !draft.isFollowUp))
    fun validStep(): Boolean = when (step) {
        0 -> purpose != null && draft.title.isNotBlank() && consultationValid
        1 -> validWizardDate(draft)
        else -> true
    }
    fun next() {
        attempted = true
        if (validStep()) {
            closeSuggestions()
            step = if (fromReview) 5 else (step + 1).coerceAtMost(5)
            fromReview = false
            attempted = false
        }
    }
    fun editAt(index: Int) { closeSuggestions(); step = index; fromReview = true; attempted = false }
    BackHandler(enabled = !state.consentRequested) { back() }
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::back, enabled = !state.saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Atrás") }
            if (step < 5) TextButton(onClick = ::next, enabled = !state.saving, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Continuar")
                MeasurementIcon(R.drawable.ic_v2_chevron, 18, MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.bitavidaColors.paleBlue) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        MeasurementIcon(R.drawable.ic_v2_calendar, 28, MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(if (state.editing == null) "Agregar cita" else "Modificar cita",
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text("Acompañando a $person", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Paso ${step + 1} de 6", Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GuidedRegistrationStepProgress(currentStep = step, totalSteps = 6, motionAllowed = motion)
            GuidedRegistrationStepTransition(currentStep = step, motionAllowed = motion) { visible ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (visible) {
                        0 -> {
                            WizardHeading("¿Qué tipo de atención vas a registrar?", "Elige el tipo y escribe cómo lo identificas.")
                            purposeOptions.chunked(if (LocalDensity.current.fontScale >= 1.5f) 1 else 2).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { (value, label) ->
                                        val typeAllowed = state.editing == null || state.editing.draft.kind == CareEventKind.REVIEW_TO_SCHEDULE ||
                                            scheduledKind(value) == state.editing.draft.kind
                                        WizardChoice(label, value == purpose, {
                                            val category = when (value) {
                                                CareEventPurpose.STUDY -> if (draft.category == CareEventCategory.IMAGING) CareEventCategory.IMAGING else CareEventCategory.LABORATORY
                                                CareEventPurpose.ADMINISTRATIVE -> CareEventCategory.ADMINISTRATIVE
                                                CareEventPurpose.MEDICAL_CONSULTATION ->
                                                    if (draft.consultationType == CareConsultationType.SPECIALIST) CareEventCategory.SPECIALTY else CareEventCategory.GENERAL
                                                else -> CareEventCategory.GENERAL
                                            }
                                            val targetKind = if (mode == CareEventTimeMode.TO_SCHEDULE) CareEventKind.REVIEW_TO_SCHEDULE else scheduledKind(value)
                                            val nextStatus = if (targetKind == draft.kind) draft.status else when (targetKind) {
                                                CareEventKind.REVIEW_TO_SCHEDULE -> CareEventStatus.TO_SCHEDULE
                                                CareEventKind.APPOINTMENT -> CareEventStatus.CONFIRMED
                                                CareEventKind.STUDY -> CareEventStatus.SCHEDULED
                                            }
                                            update(draft.copy(purpose = value, category = category, kind = targetKind, status = nextStatus,
                                                consultationType = if (value == CareEventPurpose.MEDICAL_CONSULTATION) draft.consultationType else CareConsultationType.UNSPECIFIED,
                                                isFollowUp = value == CareEventPurpose.MEDICAL_CONSULTATION && draft.isFollowUp))
                                        }, Modifier.weight(1f), enabled = typeAllowed && !state.saving, icon = purposeIcon(value))
                                    }
                                }
                            }
                            AnimatedVisibility(visible = purpose == CareEventPurpose.MEDICAL_CONSULTATION,
                                enter = if (motion) fadeIn(tween(180)) else EnterTransition.None,
                                exit = if (motion) fadeOut(tween(120)) else ExitTransition.None) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("¿Con quién será la consulta?", style = MaterialTheme.typography.titleMedium)
                                    listOf(
                                        CareConsultationType.FAMILY_MEDICINE to "Medicina familiar",
                                        CareConsultationType.GENERAL_MEDICINE to "Medicina general",
                                        CareConsultationType.SPECIALIST to "Consulta de especialidad",
                                        CareConsultationType.OTHER to "Otra consulta",
                                    ).forEach { (type, label) ->
                                        WizardChoice(label, draft.consultationType == type, {
                                            update(draft.copy(consultationType = type,
                                                purpose = CareEventPurpose.MEDICAL_CONSULTATION,
                                                category = if (type == CareConsultationType.SPECIALIST)
                                                    CareEventCategory.SPECIALTY else CareEventCategory.GENERAL))
                                        }, Modifier.fillMaxWidth(), enabled = !state.saving, icon = R.drawable.ic_v2_stethoscope)
                                    }
                                    if (legacyUnspecified && draft.consultationType == CareConsultationType.UNSPECIFIED) {
                                        Text("Tipo anterior sin especificar; puedes precisarlo si lo conoces.",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .toggleable(value = draft.isFollowUp, enabled = !state.saving,
                                            role = Role.Switch, onValueChange = { update(draft.copy(isFollowUp = it)) }),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Consulta de seguimiento", style = MaterialTheme.typography.titleSmall)
                                            Text("Permite organizar los periodos entre consultas en Informe.",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = draft.isFollowUp, onCheckedChange = null, enabled = !state.saving,
                                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary))
                                    }
                                }
                            }
                            AnimatedContent(
                                targetState = purpose,
                                transitionSpec = {
                                    if (motion) fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                                    else EnterTransition.None togetherWith ExitTransition.None
                                },
                                label = "Opciones frecuentes",
                            ) { currentPurpose ->
                                val options = currentPurpose?.let { appointmentTitles[it] }.orEmpty()
                                if (options.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Opciones frecuentes", style = MaterialTheme.typography.titleSmall)
                                        key(currentPurpose) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                options.forEach { suggestion ->
                                                    GlucoseCaptureChip(
                                                        label = suggestion.label,
                                                        selected = draft.title == suggestion.label,
                                                        enabled = !state.saving,
                                                        motionAllowed = motion,
                                                        onClick = {
                                                            searchTitle = false
                                                            focusManager.clearFocus()
                                                            update(draft.copy(title = suggestion.label))
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Column(Modifier.fillMaxWidth().onFocusChanged { searchTitle = it.hasFocus }.focusGroup()) {
                                WizardField("Nombre o motivo", draft.title, CaptureLimit.CARE_SHORT_TEXT, !state.saving) {
                                    update(draft.copy(title = it))
                                }
                                if (searchTitle && purpose != null) {
                                    val entries = appointmentTitles[purpose].orEmpty()
                                    val matches = if (draft.title.isBlank()) entries.take(12)
                                        else findAppointmentSuggestions(draft.title, entries)
                                    WizardSuggestions(matches, motion, { it.label }) { suggestion ->
                                        searchTitle = false
                                        focusManager.clearFocus()
                                        update(draft.copy(title = suggestion.label))
                                    }
                                    if (matches.isEmpty() && draft.title.isNotBlank()) {
                                        Text("Sin coincidencias. Puedes usar el nombre escrito.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (attempted && (purpose == null || draft.title.isBlank() || !consultationValid)) WizardError("Elige el tipo de atención, el tipo de consulta y escribe un nombre.")
                        }
                        1 -> {
                            WizardHeading("¿Cuándo será?", "Define la fecha y la hora del evento.")
                            MeasurementPanel {
                                WizardSelectorRow("Fecha de la cita",
                                    date?.format(wizardDate)?.replaceFirstChar { it.uppercaseChar() } ?: "Elegir fecha",
                                    R.drawable.ic_v2_calendar, enabled = !state.saving) {
                                    picker.showDate(date ?: LocalDate.now(), BitavidaDatePickerConstraints(minDate = LocalDate.now())) { selected ->
                                        update(draft.copy(scheduledDate = selected,
                                            scheduledAt = time?.let { LocalDateTime.of(selected, it) }))
                                    }
                                }
                                HorizontalDivider()
                                WizardSelectorRow("Hora de la cita",
                                    time?.format(wizardTime) ?: "Elegir hora",
                                    R.drawable.ic_v2_clock, enabled = date != null && !state.saving) {
                                    val nextMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
                                    val minTime = if (date == LocalDate.now())
                                        if (nextMinute.toLocalDate() == date) nextMinute.toLocalTime() else LocalTime.MAX
                                    else null
                                    picker.showTime(time ?: minTime ?: LocalTime.NOON,
                                        BitavidaTimePickerConstraints(minTime = minTime,
                                            invalidSelectionMessage = "Elige una hora futura.")) { selected ->
                                        val kind = if (draft.kind == CareEventKind.REVIEW_TO_SCHEDULE)
                                            scheduledKind(purpose ?: CareEventPurpose.MEDICAL_CONSULTATION) else draft.kind
                                        val status = when {
                                            draft.kind == CareEventKind.REVIEW_TO_SCHEDULE -> if (kind == CareEventKind.STUDY)
                                                CareEventStatus.SCHEDULED else CareEventStatus.CONFIRMED
                                            draft.kind == CareEventKind.STUDY && draft.status in setOf(
                                                CareEventStatus.REQUESTED, CareEventStatus.STUDY_TO_SCHEDULE) -> CareEventStatus.SCHEDULED
                                            else -> draft.status
                                        }
                                        update(draft.copy(scheduledAt = date?.let { LocalDateTime.of(it, selected) },
                                            scheduledDate = date, timeMode = CareEventTimeMode.EXACT, kind = kind, status = status))
                                    }
                                }
                            }
                            if (attempted && !validStep()) WizardError("Elige una fecha y una hora futuras.")
                        }
                        2 -> {
                            WizardHeading("¿Dónde será?", "Busca una unidad del IMSS o escribe otro lugar.")
                            val context = LocalContext.current
                            val units by produceState(emptyList<ImssUnit>(), context) {
                                value = withContext(Dispatchers.IO) { loadImssCatalog(context.applicationContext) }
                            }
                            val place = draft.location.ifBlank { draft.institution }
                            val results = remember(units, place, searchPlaces) {
                                when {
                                    !searchPlaces -> emptyList()
                                    place.isBlank() -> units.take(50)
                                    else -> searchImssUnits(units, place)
                                }
                            }
                            MeasurementPanel {
                                Column(Modifier.fillMaxWidth().onFocusChanged { searchPlaces = it.hasFocus }.focusGroup()) {
                                    WizardFormRow("Clínica, hospital o institución", place, CaptureLimit.SOURCE,
                                        R.drawable.ic_v2_home, !state.saving) {
                                        update(draft.copy(institution = it, location = "", verifiedUnitId = ""))
                                    }
                                    if (searchPlaces) {
                                        WizardSuggestions(results, motion, { it.name }, {
                                            listOf("${it.type} · ${it.municipality} · ${it.state}", it.address)
                                                .filterNotNull().joinToString("\n")
                                        }) { unit ->
                                            val oldAddress = units.firstOrNull { it.id == previousUnitId }?.address
                                            val choice = resolveImssAddressChoice(draft.reference, previousUnitId, oldAddress, unit)
                                            searchPlaces = false
                                            focusManager.clearFocus()
                                            when (choice) {
                                                ImssAddressChoice.ASK -> pendingUnit = unit
                                                ImssAddressChoice.USE_UNIT, ImssAddressChoice.KEEP_WRITTEN -> {
                                                    previousUnitId = unit.id
                                                    update(draftWithImssUnit(draft, unit,
                                                        keepAddress = choice == ImssAddressChoice.KEEP_WRITTEN))
                                                }
                                            }
                                        }
                                        if (results.isEmpty() && place.isNotBlank()) {
                                            Text("Sin coincidencias. Puedes usar el lugar escrito.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (place.isNotBlank()) CurrentDetailButton(DetailButtonKind.OUTLINED, {
                                            searchPlaces = false
                                            focusManager.clearFocus()
                                        }, Modifier.fillMaxWidth(), enabled = !state.saving) { Text("Usar otro lugar") }
                                    }
                                }
                                HorizontalDivider()
                                if (purpose != CareEventPurpose.MEDICAL_CONSULTATION ||
                                    draft.consultationType == CareConsultationType.SPECIALIST || draft.specialty.isNotBlank()) {
                                    Column(Modifier.fillMaxWidth().onFocusChanged { searchSpecialty = it.hasFocus }.focusGroup()) {
                                        WizardFormRow(if (purpose == CareEventPurpose.MEDICAL_CONSULTATION &&
                                                draft.consultationType == CareConsultationType.SPECIALIST) "Especialidad médica" else "Área registrada (opcional)",
                                            draft.specialty, CaptureLimit.CARE_SHORT_TEXT, R.drawable.ic_v2_stethoscope, !state.saving) {
                                            update(draft.copy(specialty = it))
                                        }
                                        if (searchSpecialty) {
                                            val matches = if (draft.specialty.isBlank()) appointmentSpecialties.take(12)
                                                else findAppointmentSuggestions(draft.specialty, appointmentSpecialties)
                                            WizardSuggestions(matches, motion, { it.label }) { suggestion ->
                                                searchSpecialty = false
                                                focusManager.clearFocus()
                                                update(draft.copy(specialty = suggestion.label))
                                            }
                                            if (matches.isEmpty() && draft.specialty.isNotBlank()) {
                                                Text("Sin coincidencias. Puedes usar el área escrita.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider()
                                WizardFormRow("Profesional (opcional)", draft.professional, CaptureLimit.CARE_SHORT_TEXT,
                                    R.drawable.ic_v2_stethoscope, !state.saving) { update(draft.copy(professional = it)) }
                                HorizontalDivider()
                                WizardFormRow("Consultorio o referencia (opcional)", draft.office, CaptureLimit.CARE_SHORT_TEXT,
                                    R.drawable.ic_v2_file, !state.saving) { update(draft.copy(office = it)) }
                                HorizontalDivider()
                                WizardFormRow("Dirección (opcional)", draft.reference, CaptureLimit.SOURCE,
                                    R.drawable.ic_v2_home, !state.saving) { update(draft.copy(reference = it)) }
                            }
                            Text("Si no conoces todos los datos, puedes dejar los campos opcionales vacíos.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
                        }
                        3 -> {
                            WizardHeading("¿Qué necesitas preparar?", "Registra únicamente las indicaciones que recibiste o los preparativos que elegiste.")
                            CareEventPreparation.entries.chunked(2).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { value ->
                                        WizardChoice(preparationText(value), value in draft.preparations, {
                                            val next = if (value in draft.preparations) draft.preparations - value else draft.preparations + value
                                            update(draft.copy(preparations = next))
                                        }, Modifier.weight(1f), enabled = !state.saving, icon = preparationIcon(value))
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            BitavidaLimitedTextField(
                                draft.instructions, { update(draft.copy(instructions = it)) },
                                CaptureLimit.CARE_INSTRUCTION, modifier = Modifier.fillMaxWidth(),
                                label = { Text("Preparación e indicaciones") },
                                placeholder = { Text("Escribe las indicaciones que recibiste o lo que necesitas preparar.") },
                                minLines = 3, maxLines = 6, showCounter = true, enabled = !state.saving,
                            )
                        }
                        4 -> {
                            WizardHeading("Recordatorios", "Elige cómo deseas que Bitavida te avise de esta cita.")
                            val offsets = selectedOffsets(draft)
                            val eligible = mode == CareEventTimeMode.EXACT && draft.scheduledAt != null && !terminal
                            if (!eligible) Text("Los avisos requieren fecha y hora confirmadas.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                            MeasurementPanel(color = MaterialTheme.bitavidaColors.paleBlue) {
                                WizardOptionRow("Sin recordatorios", R.drawable.ic_v2_bell,
                                    offsets.isEmpty() || !draft.reminderEnabled, !state.saving) {
                                    update(clearWizardReminders(draft))
                                }
                                listOf(1440, 120, 30).forEach { minutes ->
                                    val selected = draft.reminderEnabled && minutes in offsets
                                    val alreadyPast = draft.scheduledAt
                                        ?.let { reminderIsPast(it, minutes, LocalDateTime.now()) } ?: false
                                    WizardOptionRow(reminderText(minutes), R.drawable.ic_v2_bell,
                                        selected, eligible && !state.saving && (!alreadyPast || selected)) {
                                        update(toggleWizardReminder(draft, minutes))
                                    }
                                    if (alreadyPast && eligible) Text(
                                        "Este aviso ya no puede programarse para esta cita.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        5 -> {
                            WizardHeading("Revisa tu cita", "Confirma la información antes de guardarla.")
                            MeasurementPanel(color = MaterialTheme.bitavidaColors.paleBlue) {
                                ReviewLine("Nombre", draft.title, { editAt(0) })
                                ReviewLine("Tipo", purpose?.let(::purposeText).orEmpty(), { editAt(0) })
                                if (purpose == CareEventPurpose.MEDICAL_CONSULTATION) {
                                    ReviewLine("Consulta", careEventTypeLabel(draft), { editAt(0) })
                                    ReviewLine("Seguimiento", if (draft.isFollowUp) "Sí" else "No", { editAt(0) })
                                }
                                ReviewLine("Fecha", date?.format(wizardDate).orEmpty(), { editAt(1) })
                                ReviewLine("Hora", time?.format(wizardTime).orEmpty(), { editAt(1) })
                                ReviewLine("Institución", draft.institution, { editAt(2) })
                                ReviewLine("Lugar", draft.location, { editAt(2) })
                                ReviewLine("Área", draft.specialty, { editAt(2) })
                                ReviewLine("Profesional", draft.professional, { editAt(2) })
                                ReviewLine("Consultorio", draft.office, { editAt(2) })
                                ReviewLine("Referencia", draft.reference, { editAt(2) })
                                ReviewLine("Preparación", draft.preparations.joinToString(" · ", transform = ::preparationText), { editAt(3) })
                                ReviewLine("Preparación e indicaciones", draft.instructions, { editAt(3) })
                                ReviewLine("Recordatorios", if (draft.reminderEnabled) selectedOffsets(draft).sortedDescending().joinToString(" · ", transform = ::reminderText) else "Sin recordatorios", { editAt(4) })
                            }
                            if (state.editing != null) {
                                WizardField("Motivo del cambio (opcional)", draft.changeReason, CaptureLimit.CHANGE_REASON, !state.saving) { update(draft.copy(changeReason = it)) }
                            }
                            if (duplicate != null && !duplicateAccepted) {
                                Text("Ya existe un evento con el mismo nombre y fecha. Revisa antes de guardar.", style = MaterialTheme.typography.bodyMedium)
                                CurrentDetailButton(DetailButtonKind.OUTLINED, { duplicateAccepted = true }, Modifier.fillMaxWidth()) { Text("Guardar otro de todas maneras") }
                            }
                            CareEventDraftPhotos(state, onAction)
                            state.error?.let { WizardError(it) }
                            CurrentDetailButton(DetailButtonKind.FILLED, {
                                if (draft.title.isNotBlank() && purpose != null && consultationValid && validWizardDate(draft)) {
                                    update(draft.copy(title = draft.title.trim().replace(Regex("\\s+"), " ")))
                                    onAction(CareEventsAction.Save)
                                } else {
                                    step = if (draft.title.isBlank() || purpose == null || !consultationValid) 0 else 1
                                    attempted = true
                                }
                            }, Modifier.fillMaxWidth(), enabled = !state.saving && !state.photoPreparing && state.photoListReady && (duplicate == null || duplicateAccepted),
                                motionAllowedOverride = motion) {
                                Text(if (state.editing == null) "Agregar cita" else "Guardar cambios")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
    pendingUnit?.let { unit ->
        MedicationLifecycleSheetFrame(
            medication = null, title = "Cambiar institución",
            saving = false, motionAllowed = motion, onDismiss = { pendingUnit = null },
            skipPartiallyExpanded = false,
        ) {
            Text("Ya escribiste una dirección. Elige si deseas conservarla para la nueva institución.",
                style = MaterialTheme.typography.bodyLarge)
            MedicationLifecyclePrimaryAction(
                label = if (unit.address == null) "Usar sin dirección" else "Usar dirección de la unidad",
                icon = R.drawable.ic_v2_home, enabled = true, saving = false,
                destructive = false, motionAllowed = motion, onClick = {
                    update(draftWithImssUnit(draft, unit, keepAddress = false))
                    previousUnitId = unit.id
                    pendingUnit = null
                    searchPlaces = false
                },
            )
            MedicationLifecycleCancelAction(enabled = true, label = "Conservar dirección escrita", onClick = {
                update(draftWithImssUnit(draft, unit, keepAddress = true))
                previousUnitId = unit.id
                pendingUnit = null
                searchPlaces = false
            })
        }
    }
    if (confirmExit) {
        CareEventDiscardChangesSheet(
            saving = state.saving,
            closing = closing,
            motionAllowed = motion,
            onDismiss = { confirmExit = false },
            onDiscard = { confirmExit = false; exitOnce() },
        )
    }
}

@Composable
internal fun CareEventDiscardChangesSheet(
    saving: Boolean,
    closing: Boolean,
    motionAllowed: Boolean,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
) {
    MedicationLifecycleSheetFrame(
        medication = null,
        title = "¿Descartar cambios?",
        saving = saving,
        motionAllowed = motionAllowed,
        onDismiss = onDismiss,
        skipPartiallyExpanded = false,
    ) {
        Text(
            "La información que no guardaste se perderá.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MedicationLifecyclePrimaryAction(
            label = "Descartar cambios",
            icon = R.drawable.ic_v2_close,
            enabled = !saving && !closing,
            saving = false,
            destructive = true,
            motionAllowed = motionAllowed,
            onClick = onDiscard,
        )
        MedicationLifecycleCancelAction(
            enabled = !saving && !closing,
            label = "Seguir editando",
            onClick = onDismiss,
        )
    }
}

/** Both snapshots stay in the ViewModel, never in saved instance state. */
internal fun hasCareEventDraftChanges(draft: CareEventDraft, initial: CareEventDraft?): Boolean =
    initial != null && draft != initial

internal enum class CareEventBackDecision {
    IGNORE, DISMISS_PICKER, CLOSE_CONFIRMATION, PREVIOUS_STEP, ASK_DISCARD, EXIT,
}

internal fun careEventBackDecision(
    saving: Boolean,
    pickerActive: Boolean,
    confirmationOpen: Boolean,
    step: Int,
    hasChanges: Boolean,
): CareEventBackDecision = when {
    saving -> CareEventBackDecision.IGNORE
    pickerActive -> CareEventBackDecision.DISMISS_PICKER
    confirmationOpen -> CareEventBackDecision.CLOSE_CONFIRMATION
    step > 0 -> CareEventBackDecision.PREVIOUS_STEP
    hasChanges -> CareEventBackDecision.ASK_DISCARD
    else -> CareEventBackDecision.EXIT
}

internal fun validWizardDate(draft: CareEventDraft, now: LocalDateTime = LocalDateTime.now()): Boolean =
    draft.scheduledAt?.isAfter(now) == true

internal enum class ImssAddressChoice { USE_UNIT, KEEP_WRITTEN, ASK }

/** Never overwrite a caregiver-edited address when the same unit is selected again. */
internal fun resolveImssAddressChoice(
    currentAddress: String, previousUnitId: String, previousOfficialAddress: String?, next: ImssUnit,
): ImssAddressChoice {
    val hasManualAddress = currentAddress.isNotBlank() && currentAddress != previousOfficialAddress
    return when {
        !hasManualAddress || currentAddress == next.address -> ImssAddressChoice.USE_UNIT
        previousUnitId == next.id -> ImssAddressChoice.KEEP_WRITTEN
        else -> ImssAddressChoice.ASK
    }
}

/** Preserve a manually edited address for the same unit or after explicit confirmation. */
internal fun draftWithImssUnit(draft: CareEventDraft, unit: ImssUnit, keepAddress: Boolean): CareEventDraft =
    draft.copy(
        institution = "IMSS",
        location = unit.name,
        reference = if (keepAddress) draft.reference else unit.address.orEmpty(),
        verifiedUnitId = unit.id,
    )

@Composable
private fun WizardHeading(title: String, description: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WizardError(message: String) {
    Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun WizardChoice(label: String, isSelected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, icon: Int? = null) {
    val motion = rememberBitavidaMotionAllowed(null)
    val container by animateColorAsState(
        if (isSelected) MaterialTheme.bitavidaColors.mint else MaterialTheme.colorScheme.surface,
        animationSpec = if (motion) tween(160) else snap(), label = "Superficie seleccionada",
    )
    val outline by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .7f),
        animationSpec = if (motion) tween(160) else snap(), label = "Borde seleccionado",
    )
    BitavidaConnectedSurface(
        modifier = modifier.fillMaxWidth().heightIn(min = if (icon == null) 56.dp else 96.dp)
            .border(1.dp, outline, RoundedCornerShape(10.dp, 28.dp, 28.dp, 22.dp)).semantics {
                selected = isSelected
                stateDescription = if (isSelected) "Seleccionado" else "No seleccionado"
            },
        containerColor = container,
        onClick = if (enabled) onClick else null,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) MeasurementIcon(icon, 28, MaterialTheme.colorScheme.primary)
            Text(label, modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun <T> WizardSuggestions(
    items: List<T>,
    motion: Boolean,
    title: (T) -> String,
    supporting: (T) -> String? = { null },
    onSelect: (T) -> Unit,
) {
    AnimatedVisibility(
        visible = items.isNotEmpty(),
        enter = if (motion) fadeIn(tween(140)) else EnterTransition.None,
        exit = if (motion) fadeOut(tween(140)) else ExitTransition.None,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, item ->
                    Surface(onClick = { onSelect(item) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title(item), style = MaterialTheme.typography.bodyMedium)
                            supporting(item)?.takeIf(String::isNotBlank)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (index < items.lastIndex) HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardSelectorRow(label: String, value: String, icon: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.bitavidaColors.paleBlue) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                MeasurementIcon(icon, 24, MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled) MeasurementIcon(R.drawable.ic_v2_chevron, 18, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun WizardFormRow(label: String, value: String, limit: CaptureLimit, icon: Int,
    enabled: Boolean, onValueChange: (String) -> Unit) {
    val large = LocalDensity.current.fontScale >= 1.5f
    if (large) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MeasurementIcon(icon, 24, MaterialTheme.colorScheme.primary)
            WizardField(label, value, limit, enabled, onValueChange)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.bitavidaColors.paleBlue) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    MeasurementIcon(icon, 24, MaterialTheme.colorScheme.primary)
                }
            }
            Box(Modifier.weight(1f)) { WizardField(label, value, limit, enabled, onValueChange) }
        }
    }
}

@Composable
private fun WizardOptionRow(label: String, icon: Int, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val motion = rememberBitavidaMotionAllowed(null)
    val container by animateColorAsState(
        if (checked) MaterialTheme.bitavidaColors.mint else MaterialTheme.colorScheme.surface,
        animationSpec = if (motion) tween(160) else snap(), label = "Superficie del recordatorio",
    )
    val outline by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .6f),
        animationSpec = if (motion) tween(160) else snap(), label = "Borde del recordatorio",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
            selected = checked
            stateDescription = if (checked) "Seleccionado" else "No seleccionado"
        },
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, outline),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MeasurementIcon(icon, 22, MaterialTheme.colorScheme.primary)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun WizardField(label: String, value: String, limit: CaptureLimit, enabled: Boolean, onValueChange: (String) -> Unit) {
    BitavidaLimitedTextField(value, onValueChange, limit, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, enabled = enabled)
}

@Composable
private fun ReviewLine(label: String, value: String, edit: () -> Unit) {
    if (value.isNotBlank()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = edit, modifier = Modifier.size(48.dp)) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_v2_edit),
                contentDescription = "Editar $label", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

