package com.bitavida.app.ui.appointments

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.measurements.MeasurementPanel
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val detailLocale = Locale.forLanguageTag("es-MX")
private val detailDate = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", detailLocale)
private val detailTime = DateTimeFormatter.ofPattern("h:mm a", detailLocale)

private fun detailType(event: CareEvent): String =
    if (event.draft.consultationType == CareConsultationType.SPECIALIST &&
        event.draft.purpose in setOf(CareEventPurpose.MEDICAL_CONSULTATION, CareEventPurpose.LEGACY))
        "Consulta de especialidad" else careEventTypeLabel(event.draft)

private fun detailIcon(event: CareEvent): Int = when (event.draft.purpose) {
    CareEventPurpose.STUDY -> R.drawable.ic_v2_clipboard
    CareEventPurpose.PROCEDURE -> R.drawable.ic_v2_pill
    CareEventPurpose.ADMINISTRATIVE -> R.drawable.ic_v2_file
    CareEventPurpose.OTHER -> R.drawable.ic_v2_calendar
    else -> R.drawable.ic_v2_stethoscope
}

/** Full-screen detail; historical records are always read-only and never imply attendance. */
@Composable
internal fun CareEventDetailScreen(
    state: CareEventsState,
    onAction: (CareEventsAction) -> Unit,
    attachments: @Composable (CareEvent) -> Unit,
    onGuide: (() -> Unit)? = null,
) {
    val event = state.selected ?: return
    val draft = event.draft
    val motion = rememberBitavidaMotionAllowed(null)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember(event.id) { mutableStateOf(LocalDateTime.now()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, signal ->
            if (signal == Lifecycle.Event.ON_RESUME) now = LocalDateTime.now()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(event.id, draft.scheduledAt) {
        while (true) {
            val untilEvent = draft.scheduledAt?.let {
                Duration.between(LocalDateTime.now(), it).toMillis()
            } ?: Long.MAX_VALUE
            delay(if (untilEvent > 0) untilEvent.coerceAtMost(6 * 60 * 60 * 1000L) + 1 else 6 * 60 * 60 * 1000L)
            now = LocalDateTime.now()
        }
    }
    val editable = isFutureAppointment(event, now)
    val date = draft.scheduledAt?.toLocalDate() ?: draft.scheduledDate
    val preparations = draft.preparations.map {
        when (it) {
            CareEventPreparation.FASTING -> "Ayuno (indicación registrada)"
            CareEventPreparation.PREVIOUS_STUDIES -> "Llevar estudios previos"
            CareEventPreparation.PRESCRIPTION_OR_CARD -> "Llevar receta o carnet"
            CareEventPreparation.ARRIVE_EARLY -> "Llegar 15 minutos antes"
            CareEventPreparation.ACCOMPANIED -> "Ir acompañado"
            CareEventPreparation.OTHER -> draft.otherPreparation.ifBlank { "Otro" }
        }
    }
    val notes = listOf(draft.instructions, draft.transcribedIndication, draft.caregiverNotes)
        .filter(String::isNotBlank).distinct()
    val reminders = if (draft.reminderEnabled) draft.reminderOffsetsMinutes.ifEmpty {
        setOf(draft.reminderMinutesBefore)
    }.sortedDescending() else emptyList()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var mapError by remember(event.id) { mutableStateOf(false) }
    var confirmCancellation by remember(event.id) { mutableStateOf(false) }
    var classifyRevision by remember(event.id) { mutableStateOf<Int?>(null) }
    var selectedType by remember(event.id) { mutableStateOf(CareConsultationType.UNSPECIFIED) }
    var selectedFollowUp by remember(event.id) { mutableStateOf(false) }
    var selectedSpecialty by remember(event.id) { mutableStateOf(draft.specialty) }
    LaunchedEffect(event.revision) {
        if (classifyRevision != null && classifyRevision != event.revision) classifyRevision = null
    }
    LaunchedEffect(editable, state.saving) {
        if (!editable && !state.saving) confirmCancellation = false
    }

    BackHandler(enabled = !state.saving && !confirmCancellation && classifyRevision == null) { onAction(CareEventsAction.Dismiss) }
    BitavidaFluidBackground(Modifier.fillMaxSize(), motionAllowed = motion) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(
                        onClick = { onAction(CareEventsAction.Dismiss) },
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Volver a citas" },
                    ) { MeasurementIcon(R.drawable.ic_v2_arrow_back, 24, MaterialTheme.colorScheme.primary) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.bitavidaColors.paleBlue) {
                            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                                MeasurementIcon(detailIcon(event), 28, MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(draft.title, style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
                            Text(detailType(event), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (draft.isFollowUp) Text("Consulta de seguimiento", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            if (!editable) Text(
                                when {
                                    draft.status == CareEventStatus.CANCELLED -> "Cancelada"
                                    date == null -> "Sin fecha registrada"
                                    draft.scheduledAt == null && !date.isBefore(now.toLocalDate()) -> "Sin hora registrada"
                                    draft.scheduledAt?.isAfter(now) == true -> "Evento registrado"
                                    else -> "Evento pasado"
                                },
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            item("schedule") {
                MeasurementPanel {
                    Text("Fecha y lugar", style = MaterialTheme.typography.titleMedium)
                    DetailValue("Fecha", date?.format(detailDate)?.replaceFirstChar { it.uppercaseChar() }.orEmpty(),
                        R.drawable.ic_v2_calendar, emphasized = true)
                    DetailValue("Hora", draft.scheduledAt?.format(detailTime).orEmpty(),
                        R.drawable.ic_v2_clock, emphasized = true)
                    if (listOf(draft.institution, draft.location, draft.office, draft.specialty, draft.professional).any(String::isNotBlank)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    DetailValue("Institución", draft.institution, R.drawable.ic_v2_home)
                    if (!draft.location.equals(draft.institution, ignoreCase = true))
                        DetailValue("Clínica u hospital", draft.location, R.drawable.ic_v2_home)
                    DetailValue("Consultorio o referencia", draft.office, R.drawable.ic_v2_file)
                    DetailValue(if (draft.consultationType == CareConsultationType.SPECIALIST) "Especialidad médica" else "Área registrada", draft.specialty, R.drawable.ic_v2_stethoscope)
                    DetailValue("Profesional", draft.professional, R.drawable.ic_v2_stethoscope)
                }
            }
            if (draft.reference.isNotBlank()) item("address") {
                MeasurementPanel {
                    Text("Dirección", style = MaterialTheme.typography.titleMedium)
                    Text(draft.reference, style = MaterialTheme.typography.bodyMedium)
                    CurrentDetailButton(DetailButtonKind.OUTLINED, {
                        clipboard.setText(AnnotatedString(draft.reference))
                    }, Modifier.fillMaxWidth()) { Text("Copiar dirección") }
                    CurrentDetailButton(DetailButtonKind.OUTLINED, {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("geo:0,0?q=" + Uri.encode(draft.reference))))
                            mapError = false
                        } catch (_: Exception) { mapError = true }
                    }, Modifier.fillMaxWidth()) { Text("Abrir en mapas") }
                    if (mapError) Text("No hay una aplicación de mapas disponible.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (preparations.isNotEmpty() || notes.isNotEmpty()) item("preparation") {
                MeasurementPanel {
                    Text("Preparación e indicaciones", style = MaterialTheme.typography.titleMedium)
                    preparations.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    notes.filterNot { it in preparations }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (reminders.isNotEmpty()) item("reminders") {
                MeasurementPanel {
                    Text("Recordatorios", style = MaterialTheme.typography.titleMedium)
                    reminders.forEach {
                        Text(when (it) { 1440 -> "1 día antes"; 120 -> "2 horas antes"; 30 -> "30 minutos antes"; else -> "$it minutos antes" },
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item("photos") { attachments(event) }
            if (onGuide != null) item("education") {
                CurrentDetailButton(DetailButtonKind.OUTLINED, onGuide, Modifier.fillMaxWidth(),
                    motionAllowedOverride = motion) {
                    Text(when (draft.purpose) {
                        CareEventPurpose.STUDY -> "¿Qué es este estudio?"
                        CareEventPurpose.MEDICAL_CONSULTATION -> "Conocer esta consulta"
                        else -> "Conocer este procedimiento"
                    })
                }
            }
            state.error?.let { message -> item("error") { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (!editable && draft.kind == CareEventKind.APPOINTMENT &&
                draft.purpose in setOf(CareEventPurpose.LEGACY, CareEventPurpose.MEDICAL_CONSULTATION) &&
                draft.consultationType == CareConsultationType.UNSPECIFIED) item("classify_history") {
                CurrentDetailButton(DetailButtonKind.OUTLINED, {
                    selectedType = CareConsultationType.UNSPECIFIED
                    selectedFollowUp = false
                    selectedSpecialty = draft.specialty
                    classifyRevision = event.revision
                }, Modifier.fillMaxWidth(), enabled = !state.saving, motionAllowedOverride = motion) {
                    Text("Precisar tipo de consulta")
                }
            }
            if (editable) item("edit") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CurrentDetailButton(DetailButtonKind.FILLED, { onAction(CareEventsAction.Edit) },
                        Modifier.fillMaxWidth(), enabled = !state.saving, motionAllowedOverride = motion) {
                        Text("Modificar información")
                    }
                    CurrentDetailButton(DetailButtonKind.OUTLINED, { confirmCancellation = true },
                        Modifier.fillMaxWidth(), enabled = !state.saving, motionAllowedOverride = motion) {
                        Text("Cancelar cita")
                    }
                }
            }
        }
    }
    if (classifyRevision != null) {
        MedicationLifecycleSheetFrame(
            medication = null, title = "Precisar tipo de consulta", saving = state.saving,
            motionAllowed = motion, onDismiss = { classifyRevision = null }, skipPartiallyExpanded = false,
        ) {
            Text("Solo cambia la clasificación declarada. Se conserva la versión anterior.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(
                CareConsultationType.FAMILY_MEDICINE to "Medicina familiar",
                CareConsultationType.GENERAL_MEDICINE to "Medicina general",
                CareConsultationType.SPECIALIST to "Consulta de especialidad",
                CareConsultationType.OTHER to "Otra consulta",
            ).forEach { (type, label) ->
                OutlinedButton(onClick = { selectedType = type },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { selected = selectedType == type },
                    enabled = !state.saving,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedType == type) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp,
                        if (selectedType == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                ) { Text(label) }
            }
            if (selectedType == CareConsultationType.SPECIALIST) {
                BitavidaLimitedTextField(value = selectedSpecialty, onValueChange = { selectedSpecialty = it },
                    limit = CaptureLimit.CARE_SHORT_TEXT, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Especialidad médica") }, enabled = !state.saving, singleLine = true)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = selectedFollowUp, enabled = !state.saving, role = Role.Switch,
                onValueChange = { selectedFollowUp = it }), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Consulta de seguimiento", style = MaterialTheme.typography.titleSmall)
                    Text("Permite organizar los periodos entre consultas en Informe.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = selectedFollowUp, onCheckedChange = null, enabled = !state.saving,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary))
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            MedicationLifecyclePrimaryAction(label = "Guardar clasificación", icon = R.drawable.ic_v2_edit,
                enabled = selectedType != CareConsultationType.UNSPECIFIED && !state.saving,
                saving = state.saving, destructive = false, motionAllowed = motion,
                onClick = {
                    val revision = classifyRevision ?: return@MedicationLifecyclePrimaryAction
                    onAction(CareEventsAction.ClassifyHistorical(event.id, revision, selectedType,
                        selectedFollowUp, selectedSpecialty))
                })
            MedicationLifecycleCancelAction(enabled = !state.saving, label = "Cancelar",
                onClick = { classifyRevision = null })
        }
    }
    if (confirmCancellation) {
        MedicationLifecycleSheetFrame(
            medication = null,
            title = "¿Cancelar esta cita?",
            saving = state.saving,
            motionAllowed = motion,
            onDismiss = { confirmCancellation = false },
            skipPartiallyExpanded = false,
        ) {
            Text(
                "La cita quedará en tu historial y se cancelarán sus avisos. La información y las fotografías se conservarán.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            MedicationLifecyclePrimaryAction(
                label = "Cancelar cita",
                icon = R.drawable.ic_v2_close,
                enabled = editable && !state.saving,
                saving = state.saving,
                destructive = true,
                motionAllowed = motion,
                onClick = { onAction(CareEventsAction.Cancel) },
            )
            MedicationLifecycleCancelAction(
                enabled = !state.saving,
                label = "Conservar cita",
                onClick = { confirmCancellation = false },
            )
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String, icon: Int, emphasized: Boolean = false) {
    if (value.isNotBlank()) Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MeasurementIcon(icon, 22, MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Preview(name = "Detalle claro", showBackground = true)
@Preview(name = "Detalle oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Detalle compacto", showBackground = true, widthDp = 320)
@Preview(name = "Detalle texto 200%", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun CareEventDetailPreview() {
    val date = LocalDateTime.of(2030, 11, 24, 10, 30)
    val event = CareEvent("preview", "persona", CareEventDraft(
        kind = CareEventKind.APPOINTMENT, title = "Consulta de ejemplo",
        status = CareEventStatus.CONFIRMED, scheduledAt = date,
        institution = "Institución de ejemplo", office = "Consultorio 1",
    ), date, date, 1)
    BitavidaTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
        CareEventDetailScreen(CareEventsState(loading = false, selected = event), {}, {})
    }
}
