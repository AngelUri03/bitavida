package com.bitavida.app.ui.observations

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.validateObservation
import com.bitavida.app.domain.usecase.fitsObservationTextCapture
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.*
import com.bitavida.app.ui.measurements.*
import com.bitavida.app.ui.medical.CurrentMedicationBack
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val formLocale = Locale.forLanguageTag("es-MX")
private val formDate = DateTimeFormatter.ofPattern("d MMM uuuu", formLocale)
private val formTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(formLocale)
private val compactFormDate = DateTimeFormatter.ofPattern("d/MM/uuuu", formLocale)
private val compactFormTime = DateTimeFormatter.ofPattern("HH:mm", formLocale)

/** Dedicated navigation destination. Only the official date/time selectors open a sheet. */
@Composable
internal fun ObservationRegistrationContent(
    state: ObservationsUiState,
    motionAllowed: Boolean,
    onAction: (ObservationsAction) -> Unit,
    photoContent: @Composable () -> Unit = {},
    onBack: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    backLabel: String = "Observaciones",
) {
    val draft = state.draft ?: return
    BitavidaDateTimePickerHost {
        val picker = LocalBitavidaDateTimePickerController.current
        BackHandler(enabled = picker.isActive) { picker.dismiss() }
        Column(Modifier.fillMaxSize().imePadding()) {
            Column(Modifier.fillMaxWidth().padding(
                horizontal = BitavidaSpacing.ScreenHorizontal,
                vertical = BitavidaSpacing.Small)) {
                CurrentMedicationBack(onBack, motionAllowed, enabled = !state.saving,
                    label = backLabel)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stackHeader = maxWidth.value / LocalDensity.current.fontScale < 300f
                    val title: @Composable (Modifier) -> Unit = { modifier ->
                        Text(
                            ObservationCatalog.get(draft.observationId).name,
                            modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    val guide: @Composable (Modifier) -> Unit = { modifier ->
                        MeasurementButton(
                            "Cómo observar",
                            { onAction(ObservationsAction.OpenGuide) },
                            modifier,
                            style = MeasurementButtonStyle.TEXT,
                            icon = R.drawable.ic_v2_file,
                            enabled = !state.saving,
                        )
                    }
                    if (stackHeader) {
                        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                            title(Modifier.fillMaxWidth())
                            guide(Modifier)
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            title(Modifier.weight(1f))
                            guide(Modifier)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState)
                .padding(horizontal = BitavidaSpacing.ScreenHorizontal,
                    vertical = BitavidaSpacing.Small)) {
                ObservationFormContent(state, motionAllowed, onAction, photoContent, showSave = false)
            }
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                MeasurementButton(
                    if (state.saving) "Guardando…" else if (state.correctionOriginalId != null)
                        "Guardar corrección" else "Guardar observación",
                    { onAction(ObservationsAction.Save) },
                    Modifier.fillMaxWidth().padding(
                        horizontal = BitavidaSpacing.ScreenHorizontal,
                        vertical = BitavidaSpacing.Small,
                    ),
                    enabled = observationCanSubmit(state) && !state.saving && !state.photoPreparing,
                )
            }
        }
    }
}

/** Compatibility for previews; production navigates to ObservationRegistrationContent. */
@Composable
internal fun ObservationCaptureBody(
    state: ObservationsUiState,
    motionAllowed: Boolean,
    onAction: (ObservationsAction) -> Unit,
    photoContent: @Composable () -> Unit = {},
) = ObservationRegistrationContent(
    state, motionAllowed, onAction, photoContent,
    onBack = { onAction(ObservationsAction.CloseDraft) },
)

@Composable
internal fun ObservationFormContent(
    state: ObservationsUiState,
    motionAllowed: Boolean,
    onAction: (ObservationsAction) -> Unit,
    photoContent: @Composable () -> Unit = {},
    showSave: Boolean = true,
) {
    val draft = state.draft ?: return
    val definition = ObservationCatalog.get(draft.observationId)
    val duration = if (motionAllowed) BitavidaFluidMotion.Selection else 0
    val hasPrevious = state.snapshot.records.any {
        it.observationId == draft.observationId && it.id != draft.id
    }
    var details by remember(draft.id) {
        mutableStateOf(
            draft.description.isNotBlank() ||
                draft.answers.keys.any { it in definition.fields.filterNot(ObservationField::required).map(ObservationField::id) },
        )
    }
    val closed = draft.result == ObservationResult.NO_CHANGES ||
        draft.result == ObservationResult.NOT_PERFORMED
    val questions = observationCaptureQuestions(draft, hasPrevious)
    val essential = questions.essential
    val complementary = questions.additional
    val visibleFields = essential + complementary
    val descriptionRequired = definition.id == "OBS-027" ||
        (!closed && draft.answers.values.flatten().any { it.startsWith("Otro") })
    val descriptionVisible = descriptionRequired || details || "description" in state.errors ||
        draft.result == ObservationResult.NOT_PERFORMED
    LaunchedEffect(state.errors) {
        if (state.errors.keys.any { key ->
                key == "description" || complementary.any { it.id == key }
            }
        ) details = true
    }
    fun change(value: ObservationRecord) = onAction(ObservationsAction.ChangeDraft(value))

    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        if (showSave) SectionTitle(definition.name)
        if (state.correctionOriginalId != null) {
            InformationCard(
                "Corrección auditada",
                "La declaración anterior se conservará en el historial.",
            )
        }
        draft.scheduledAt?.let {
            InformationCard(
                "Revisión programada",
                it.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", formLocale)) +
                    " · El resultado quedará asociado a esta revisión.",
            )
        }

        ObservationFormSection("Resultado de la observación") {
            if (!definition.eventOnly && (draft.occurrenceId != null || definition.id == "OBS-001")) {
                val results = if (draft.occurrenceId == null) {
                    listOf(ObservationResult.CHANGES, ObservationResult.NO_CHANGES)
                } else {
                    listOf(
                        ObservationResult.CHANGES,
                        ObservationResult.NO_CHANGES,
                        ObservationResult.NOT_PERFORMED,
                    )
                }
                ObservationChoiceOptions(
                    options = results.map(ObservationResult::label),
                    selected = { label -> results.any { it.label() == label && draft.result == it } },
                    enabled = !state.saving,
                    motionAllowed = motionAllowed,
                ) { label ->
                    val result = results.first { it.label() == label }
                    change(
                        draft.copy(
                            result = result,
                            answers = when (result) {
                                ObservationResult.NO_CHANGES -> draft.answers.filterKeys {
                                    definition.id == "OBS-001" && it == "foot"
                                }
                                ObservationResult.NOT_PERFORMED -> emptyMap()
                                else -> draft.answers
                            },
                        ),
                    )
                }
            } else if (definition.eventOnly && draft.occurrenceId != null) {
                val labels = listOf("Registrar lo ocurrido", "No se realizó la revisión")
                val results = listOf(ObservationResult.EVENT, ObservationResult.NOT_PERFORMED)
                ObservationChoiceOptions(
                    options = labels,
                    selected = { draft.result == results[labels.indexOf(it)] },
                    enabled = !state.saving,
                    motionAllowed = motionAllowed,
                ) { label ->
                    val result = results[labels.indexOf(label)]
                    change(
                        draft.copy(
                            result = result,
                            answers = if (result == ObservationResult.NOT_PERFORMED) emptyMap()
                            else draft.answers,
                        ),
                    )
                }
            }
            essential.forEach { field ->
                ObservationQuestion(field, draft, state, motionAllowed, ::change)
            }
            if (essential.isEmpty() && draft.result == ObservationResult.NOT_PERFORMED) {
                SecondaryText("Esta declaración no registra una revisión realizada ni supone ausencia de cambios.")
            }
            state.errors["result"]?.let { CaptureError(it) }
        }

        ObservationFormSection("Fecha y hora reales") {
            ObservationActualTime(draft, state.saving, motionAllowed, ::change)
            state.errors["date"]?.let { CaptureError(it) }
            state.errors["time"]?.let { CaptureError(it) }
        }

        ObservationFormSection("Información complementaria") {
            if (descriptionRequired || draft.result == ObservationResult.NOT_PERFORMED) {
                ObservationDescriptionField(
                    draft,
                    state,
                    ::change,
                    required = descriptionRequired,
                    label = if (draft.result == ObservationResult.NOT_PERFORMED)
                        "Motivo o detalle (opcional)" else null,
                )
            }
            if (!closed && draft.answers.values.any { values -> values.any(String::isNotBlank) }) {
                MeasurementButton(
                    "Ayúdame a describirlo",
                    { onAction(ObservationsAction.HelpDescribe) },
                    style = MeasurementButtonStyle.OUTLINED,
                    icon = R.drawable.ic_v2_edit,
                    enabled = !state.saving,
                )
            }
            ObservationDetailsHeader(details, !state.saving, motionAllowed) { details = !details }
            AnimatedVisibility(
                details,
                enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    complementary.forEach { field ->
                        ObservationQuestion(field, draft, state, motionAllowed, ::change)
                    }
                    if (!descriptionVisible) {
                        ObservationDescriptionField(draft, state, ::change, required = false)
                    } else if (!descriptionRequired && draft.result != ObservationResult.NOT_PERFORMED) {
                        ObservationDescriptionField(draft, state, ::change, required = false)
                    }
                }
            }
        }

        if (definition.photoAllowed && draft.result != ObservationResult.NOT_PERFORMED) photoContent()

        if (state.correctionOriginalId != null) {
            ObservationFormSection("Motivo de la corrección") {
                BitavidaLimitedTextField(
                    state.correctionReason,
                    { if (!state.saving) onAction(ObservationsAction.CorrectionReason(it)) },
                    CaptureLimit.CHANGE_REASON,
                    Modifier.fillMaxWidth(),
                    additionalInputValidation = { fitsObservationTextCapture(it, Int.MAX_VALUE, state.correctionReason) },
                    label = { Text("¿Por qué corriges esta declaración? *") },
                    minLines = 2,
                    isError = "correctionReason" in state.errors,
                    shape = MaterialTheme.shapes.medium,
                )
                state.errors["correctionReason"]?.let { CaptureError(it) }
            }
        }
        if (definition.id in setOf("OBS-001", "OBS-002", "OBS-009", "OBS-016", "OBS-017", "OBS-020", "OBS-022", "OBS-027")) {
            SecondaryText("Si se trata de una emergencia, busca atención de urgencia o llama al 911. No esperes a completar este registro.")
        }
        state.errors.filterKeys { key ->
            key !in visibleFields.map(ObservationField::id) &&
                key !in setOf("description", "correctionReason", "result", "date", "time")
        }.values.distinct().forEach { CaptureError(it) }
        if (descriptionVisible) state.errors["description"]?.let { CaptureError(it) }
        state.error?.let { CaptureError(it) }
        if (showSave) {
            MeasurementButton(
                if (state.saving) "Guardando…" else "Guardar observación",
                { onAction(ObservationsAction.Save) },
                Modifier.fillMaxWidth(),
                enabled = observationCanSubmit(state) && !state.saving && !state.photoPreparing,
            )
        }
    }
}

@Composable
private fun ObservationFormSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        SectionTitle(title, small = true)
        content()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
}

/** The glucose capture controls, wrapping only when larger text needs more space. */
@Composable
private fun ObservationActualTime(
    draft: ObservationRecord,
    saving: Boolean,
    motionAllowed: Boolean,
    change: (ObservationRecord) -> Unit,
) {
    val picker = LocalBitavidaDateTimePickerController.current
    val fontScale = LocalDensity.current.fontScale
    fun selectTime(precision: ObservationTimePrecision = draft.timePrecision) {
        picker.showTime(draft.occurredAt ?: LocalTime.now(),
            BitavidaTimePickerConstraints(maxTime = LocalTime.now().takeIf { draft.occurredOn == LocalDate.now() })) {
            change(draft.copy(occurredAt = it,
                timePrecision = if (precision == ObservationTimePrecision.UNKNOWN)
                    ObservationTimePrecision.EXACT else precision))
        }
    }
    val dateControl: @Composable (Modifier, Boolean) -> Unit = { modifier, compact ->
        MeasurementButton(draft.occurredOn.format(if (compact) compactFormDate else formDate), {
            val today = LocalDate.now()
            picker.showDate(draft.occurredOn,
                BitavidaDatePickerConstraints(maxDate = today, yearRange = 1900..today.year)) {
                change(draft.copy(occurredOn = it))
            }
        }, modifier, MeasurementButtonStyle.OUTLINED, enabled = !saving,
            icon = R.drawable.ic_v2_calendar.takeUnless { compact },
            accessibleLabel = "Cambiar fecha real: " + draft.occurredOn.format(formDate))
    }
    val timeControl: @Composable (Modifier, Boolean) -> Unit = { modifier, compact ->
        val label = draft.occurredAt?.format(if (compact) compactFormTime else formTime) ?: "Sin hora"
        val accessibleTime = draft.occurredAt?.format(formTime) ?: "Sin hora"
        MeasurementButton(label, { selectTime(ObservationTimePrecision.EXACT) }, modifier, MeasurementButtonStyle.OUTLINED,
            enabled = !saving,
            accessibleLabel = "Cambiar hora real: $accessibleTime")
    }
    BoxWithConstraints {
        val stacked = maxWidth.value / fontScale < 230f
        val compact = !stacked && maxWidth < 300.dp
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                dateControl(Modifier.fillMaxWidth(), false)
                timeControl(Modifier.fillMaxWidth(), false)
            }
        } else Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            dateControl(Modifier.weight(1.45f), compact)
            timeControl(Modifier.weight(1f), compact)
        }
    }
    GlucoseCaptureChip(
        label = "No sé la hora",
        selected = draft.timePrecision == ObservationTimePrecision.UNKNOWN,
        enabled = !saving,
        motionAllowed = motionAllowed,
        onClick = {
            if (draft.timePrecision == ObservationTimePrecision.UNKNOWN) {
                selectTime(ObservationTimePrecision.EXACT)
            } else {
                observationWithTimePrecision(draft, ObservationTimePrecision.UNKNOWN)?.let(change)
            }
        },
    )
}

internal data class ObservationCaptureQuestions(
    val essential: List<ObservationField>,
    val additional: List<ObservationField>,
)

/** Keep the versioned schema intact while opening only the questions relevant to this result. */
internal fun observationCaptureQuestions(
    record: ObservationRecord,
    hasPreviousRecord: Boolean = false,
): ObservationCaptureQuestions {
    val definition = ObservationCatalog.get(record.observationId)
    val visible = definition.fields.filter {
        record.result != ObservationResult.NOT_PERFORMED &&
            (record.result != ObservationResult.NO_CHANGES || (definition.id == "OBS-001" && it.id == "foot")) &&
            (it.id != "continues" || hasPreviousRecord || record.answers[it.id].orEmpty().any(String::isNotBlank))
    }
    val essential = visible.filter {
        it.required || (definition.id == "OBS-001" && it.id == "changes")
    }
    // Legacy narratives remain editable; new entries use the single caregiver description.
    val additional = visible.filterNot { it in essential }.filter {
        it.id !in setOf("finding", "details", "additional") || record.answers[it.id].orEmpty().any(String::isNotBlank)
    }
    return ObservationCaptureQuestions(essential, additional)
}

/** null requests an explicit time selection; a missing hour can never become the current hour here. */
internal fun observationWithTimePrecision(
    record: ObservationRecord,
    precision: ObservationTimePrecision,
): ObservationRecord? = when {
    precision == ObservationTimePrecision.UNKNOWN -> record.copy(timePrecision = precision, occurredAt = null)
    record.occurredAt != null -> record.copy(timePrecision = precision)
    else -> null
}

/** Same expandable row, surface and chevron motion used by GlucoseNoteHeader. */
@Composable
private fun ObservationDetailsHeader(
    expanded: Boolean,
    enabled: Boolean,
    motionAllowed: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f,
        tween(if (motionAllowed) BitavidaFluidMotion.Selection else 0), label = "observation_details_chevron")
    Surface(onClick = onToggle, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .semantics { stateDescription = if (expanded) "Expandido" else "Contraído" }) {
        Row(Modifier.padding(horizontal = BitavidaSpacing.Small, vertical = BitavidaSpacing.ExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (expanded) "Detalles adicionales" else "Añadir detalles", Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall)
            Box(Modifier.graphicsLayer { rotationZ = rotation }) {
                MeasurementIcon(R.drawable.ic_v2_chevron, 18)
            }
        }
    }
}

@Composable
private fun ObservationQuestion(field: ObservationField, draft: ObservationRecord,
    state: ObservationsUiState, motionAllowed: Boolean, change: (ObservationRecord) -> Unit) {
    val errorRequester = remember { BringIntoViewRequester() }
    val error = state.errors[field.id]
    LaunchedEffect(error) { if (error != null) errorRequester.bringIntoView() }
    Text(
        field.label + if (field.required) " *" else "",
        Modifier.bringIntoViewRequester(errorRequester),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val values = draft.answers[field.id].orEmpty()
    if (field.type == ObservationFieldType.TEXT) {
        BitavidaLimitedTextField(values.firstOrNull().orEmpty(), { value ->
            if (!state.saving) change(draft.copy(answers = draft.answers + (field.id to listOf(value))))
        }, CaptureLimit.OBSERVATION_TEXT, Modifier.fillMaxWidth().semantics { contentDescription = field.label },
            additionalInputValidation = { fitsObservationTextCapture(it, Int.MAX_VALUE, values.firstOrNull().orEmpty()) },
            minLines = 1,
            isError = field.id in state.errors, shape = MaterialTheme.shapes.medium)
    } else ObservationChoiceOptions(
        options = field.options,
        selected = { option -> option in values },
        enabled = !state.saving,
        motionAllowed = motionAllowed,
    ) { option ->
        val next = if (field.type == ObservationFieldType.SINGLE_CHOICE) listOf(option)
            else if (option in values) values - option else values + option
        change(draft.copy(answers = draft.answers + (field.id to next)))
    }
    state.errors[field.id]?.let { CaptureError(it) }
}

@Composable
private fun ObservationDescriptionField(
    draft: ObservationRecord,
    state: ObservationsUiState,
    change: (ObservationRecord) -> Unit,
    required: Boolean,
    label: String? = null,
) {
    val errorRequester = remember { BringIntoViewRequester() }
    val error = state.errors["description"]
    LaunchedEffect(error) { if (error != null) errorRequester.bringIntoView() }
    BitavidaLimitedTextField(draft.description, { if (!state.saving) change(draft.copy(description = it)) },
        CaptureLimit.OBSERVATION_DESCRIPTION,
        Modifier.fillMaxWidth().bringIntoViewRequester(errorRequester),
        additionalInputValidation = { fitsObservationTextCapture(it, Int.MAX_VALUE, draft.description) },
        label = { Text(label ?: if (required) "Describe lo que observaste *" else "Descripción (opcional)") },
        minLines = 2,
        isError = "description" in state.errors, shape = MaterialTheme.shapes.medium)
}

internal fun observationCanSubmit(
    state: ObservationsUiState,
    now: LocalDateTime = LocalDateTime.now(),
): Boolean {
    val draft = state.draft ?: return false
    if (state.correctionOriginalId != null && (state.correctionReason.isBlank() ||
        !fitsObservationTextCapture(state.correctionReason, CaptureLimit.CHANGE_REASON.maximum))) return false
    val original = state.snapshot.records.firstOrNull { it.id == state.correctionOriginalId }
    return validateObservation(draft, now, original).isEmpty()
}
