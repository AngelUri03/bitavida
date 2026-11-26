package com.bitavida.app.ui.medical

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ripple
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.domain.model.MedicationIntakeRecord
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationReminderPreference
import com.bitavida.app.domain.model.MedicationReminderRuntimeState
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.model.currentSuspension
import com.bitavida.app.domain.model.lastTreatmentDate
import com.bitavida.app.domain.model.pendingReactivation
import com.bitavida.app.domain.usecase.FirstMedicationAdministration
import com.bitavida.app.domain.usecase.MedicalCareField
import com.bitavida.app.domain.usecase.MedicationOccurrenceTiming
import com.bitavida.app.domain.usecase.UpcomingMedicationAdministration
import com.bitavida.app.domain.usecase.projectFirstMedicationAdministration
import com.bitavida.app.domain.usecase.projectNextMedicationAdministration
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.components.BitavidaStatusPill
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
internal fun CurrentMedicationDetail(
    state: MedicalPlanUiState,
    plan: MedicationPlan,
    now: LocalDateTime,
    reminderRuntimeState: MedicationReminderRuntimeState,
    motionAllowed: Boolean,
    modifier: Modifier,
    onRequestReminderAccess: () -> Unit,
    onAction: (MedicalPlanAction) -> Unit,
) {
    val stateTransitionModifier = modifier
    val configuration = plan.currentRevision.configuration
    val today = now.toLocalDate()
    val records = state.overview.intakesFor(plan.id)
    val asNeeded = configuration?.frequency == MedicationFrequencyKind.AS_NEEDED
    val occurrences = remember(plan, today) { medicationOccurrencesForDay(plan, today) }
    val resolved = remember(plan, today, records, occurrences) {
        occurrences.associate { it.id to intakeForOccurrence(records, it, occurrences, plan) }
    }
    val attachedIds = resolved.values.mapNotNull { it?.id }.toSet()
    val previousRecords = records.filter {
        it.id !in attachedIds && it.hasDeclaredResponse() &&
            (it.currentRevision.actualAt ?: it.currentRevision.scheduledAt).toLocalDate() == today
    }.sortedBy { it.currentRevision.scheduledAt }
    val todayRecords = records.filter {
        (it.currentRevision.actualAt ?: it.currentRevision.scheduledAt).toLocalDate() == today &&
            it.hasDeclaredResponse()
    }.sortedBy { it.currentRevision.actualAt ?: it.currentRevision.scheduledAt }
    val initialKey = if (asNeeded) todayRecords.lastOrNull()?.let { "record:${it.id}" }
        else initialMedicationTodayKey(occurrences, records, now, resolved)
    val selectionInitialized = state.medicationTodayDate == today
    val selectedKey = if (selectionInitialized) state.selectedMedicationTodayKey else initialKey
    LaunchedEffect(plan.id, today, initialKey) {
        if (initialKey != null && !selectionInitialized) {
            onAction(MedicalPlanAction.SelectMedicationTodayRow(initialKey, today))
        }
    }
    val choose: (String?) -> Unit = { key ->
        onAction(
            MedicalPlanAction.SelectMedicationTodayRow(
                key = key,
                day = today,
            ),
        )
    }
    val count = if (asNeeded) todayRecords.size else occurrences.count {
        resolved[it.id]?.hasDeclaredResponse() == true
    } + previousRecords.size
    CompositionLocalProvider(LocalCurrentDetailMotion provides motionAllowed) {

        MedicationDetailIdentity(
            plan = plan,
            statusLabel = "Vigente",
            statusIsPositive = true,
            modifier = stateTransitionModifier,
        )

        CurrentMedicationTodayHeader(
            date = today,
            counter = if (asNeeded) {
                if (count == 1) "1 toma registrada" else "$count tomas registradas"
            } else if (previousRecords.isNotEmpty()) {
                "$count registradas · ${occurrences.size} programadas"
            } else "$count de ${occurrences.size} registradas",
            motionAllowed = motionAllowed,
            onCalendar = { onAction(MedicalPlanAction.OpenMedicationHistory) },
            modifier = stateTransitionModifier,
        )

        MedicationIntakeDayPanel(
            state = state,
            medication = plan,
            date = today,
            now = now,
            motionAllowed = motionAllowed,
            selectedKey = selectedKey,
            onSelectedKeyChange = choose,
            historicalSchedule = false,
            allowAsNeededRegistration = true,
            onAction = onAction,
            modifier = stateTransitionModifier,
        )

        CurrentIndicationCard(
            plan = plan,
            runtime = reminderRuntimeState,
            motionAllowed = motionAllowed,
            onRequestReminderAccess = onRequestReminderAccess,
            modifier = stateTransitionModifier,
        )
        CurrentMedicationOperations(
            state.isSaving,
            onAction,
            modifier = stateTransitionModifier,
        )
    }
}

@Composable
internal fun UpcomingMedicationDetail(
    state: MedicalPlanUiState,
    plan: MedicationPlan,
    now: LocalDateTime,
    reminderRuntimeState: MedicationReminderRuntimeState,
    motionAllowed: Boolean,
    modifier: Modifier,
    onRequestReminderAccess: () -> Unit,
    onAction: (MedicalPlanAction) -> Unit,
) {
    val stateTransitionModifier = modifier
    val configuration = plan.currentRevision.configuration
    val zoneId = ZoneId.systemDefault()
    val nowEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli()
    val scheduledReactivation = remember(plan, nowEpochMillis) {
        plan.pendingReactivation(nowEpochMillis, zoneId)
    }
    val reactivationAt = scheduledReactivation?.effectiveAtEpochMillis?.let {
        Instant.ofEpochMilli(it).atZone(zoneId).toLocalDateTime()
    }
    val startDate = reactivationAt?.toLocalDate()
        ?: configuration?.startsOn
        ?: plan.currentRevision.startsOn
    val firstAdministration = remember(plan, reactivationAt) {
        if (reactivationAt == null) {
            projectFirstMedicationAdministration(plan)
        } else {
            projectNextMedicationAdministration(plan, reactivationAt)
        }
    }
    val daysUntilStart = ChronoUnit.DAYS.between(now.toLocalDate(), startDate)
    val remainingLabel = when (daysUntilStart) {
        0L -> "Hoy"
        1L -> "Mañana"
        else -> "En ${daysUntilStart} días"
    }
    var firstCardVisible by remember(plan.id) { mutableStateOf(!motionAllowed) }
    LaunchedEffect(plan.id) {
        firstCardVisible = true
    }
    CompositionLocalProvider(LocalCurrentDetailMotion provides motionAllowed) {
        MedicationDetailIdentity(
            plan = plan,
            statusLabel = "Por iniciar",
            statusIsPositive = false,
            modifier = stateTransitionModifier,
        )
        MedicationDetailSectionHeader(
            title = if (reactivationAt == null) {
                "Inicio del plan"
            } else {
                "Reactivación programada"
            },
            subtitle = scheduledReactivation?.effectiveAtEpochMillis
                ?.suspensionMomentLabel()
                ?: startDate.fullMedicationDate(),
            trailing = remainingLabel,
            motionAllowed = motionAllowed,
            onCalendar = {
                onAction(MedicalPlanAction.OpenMedicationHistory)
            },
            modifier = stateTransitionModifier,
        )
        AnimatedVisibility(
            visible = firstCardVisible,
            modifier = stateTransitionModifier,
            enter = fadeIn(
                tween(
                    if (motionAllowed) BitavidaFluidMotion.StateTransition else 0,
                ),
            ) + expandVertically(
                tween(
                    if (motionAllowed) BitavidaFluidMotion.StateTransition else 0,
                ),
            ),
        ) {
            UpcomingFirstAdministrationCard(
                configuration = configuration,
                planStart = startDate,
                firstAdministration = firstAdministration,
                administrationLabel = if (reactivationAt == null) {
                    "Primera toma"
                } else {
                    "Próxima toma"
                },
                motionAllowed = motionAllowed,
            )
        }
        if (scheduledReactivation != null) {
            CurrentDetailButton(
                kind = DetailButtonKind.OUTLINED,
                onClick = {
                    onAction(
                        MedicalPlanAction.RequestMedicationLifecycle(
                            MedicationTreatmentStatus.ACTIVE,
                        ),
                    )
                },
                enabled = !state.isSaving,
                modifier = stateTransitionModifier.fillMaxWidth(),
                strongOutline = true,
            ) {
                DetailIcon(R.drawable.ic_v2_clock)
                Spacer(Modifier.size(8.dp))
                Text("Cambiar o cancelar reactivación")
            }
        }
        CurrentIndicationCard(
            plan = plan,
            runtime = reminderRuntimeState,
            motionAllowed = motionAllowed,
            onRequestReminderAccess = onRequestReminderAccess,
            title = "Indicación programada",
            modifier = stateTransitionModifier,
        )
        CurrentMedicationOperations(
            saving = state.isSaving,
            onAction = onAction,
            showSuspend = scheduledReactivation == null,
            modifier = stateTransitionModifier,
        )
    }
}

@Composable
internal fun SuspendedMedicationDetail(
    state: MedicalPlanUiState,
    plan: MedicationPlan,
    now: LocalDateTime,
    reminderRuntimeState: MedicationReminderRuntimeState,
    motionAllowed: Boolean,
    modifier: Modifier,
    onRequestReminderAccess: () -> Unit,
    onAction: (MedicalPlanAction) -> Unit,
) {
    val stateTransitionModifier = modifier
    val zoneId = ZoneId.systemDefault()
    val nowEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli()
    val suspension = remember(plan, nowEpochMillis) {
        plan.currentSuspension(nowEpochMillis, zoneId)
    }
    val pendingReactivation = remember(plan, nowEpochMillis) {
        plan.pendingReactivation(nowEpochMillis, zoneId)
    }
    val indicationRevision = plan.medicalIndicationId?.let { indicationId ->
        state.overview.indications.singleOrNull {
            it.id == indicationId
        }?.currentRevision
    }
    val effectiveAtEpochMillis =
        suspension?.effectiveAtEpochMillis
            ?: indicationRevision?.capturedAtEpochMillis
            ?: plan.currentRevision.capturedAtEpochMillis
    val reason = suspension?.revision?.changeReason
        ?: indicationRevision?.changeReason
    val suspendedReminderLabel = if (
        plan.currentRevision.configuration?.reminderPreference ==
        MedicationReminderPreference.NONE
    ) {
        "No hay avisos configurados."
    } else {
        "Avisos pausados mientras el medicamento siga suspendido."
    }
    CompositionLocalProvider(LocalCurrentDetailMotion provides motionAllowed) {
        MedicationDetailIdentity(
            plan = plan,
            statusLabel = "Suspendido",
            statusIsPositive = false,
            modifier = stateTransitionModifier,
        )
        SuspendedTreatmentCard(
            effectiveAtEpochMillis = effectiveAtEpochMillis,
            reason = reason,
            reminderLabel = suspendedReminderLabel,
            pendingReactivationAtEpochMillis =
                pendingReactivation?.effectiveAtEpochMillis,
            onReactivate = {
                onAction(
                    MedicalPlanAction.RequestMedicationLifecycle(
                        MedicationTreatmentStatus.ACTIVE,
                    ),
                )
            },
            onHistory = {
                onAction(MedicalPlanAction.OpenMedicationHistory)
            },
            modifier = stateTransitionModifier,
        )
        CurrentIndicationCard(
            plan = plan,
            runtime = reminderRuntimeState,
            motionAllowed = motionAllowed,
            onRequestReminderAccess = onRequestReminderAccess,
            title = "Última indicación registrada",
            referenceMessage =
                "Solo de referencia mientras está suspendido.",
            reminderLabelOverride = suspendedReminderLabel,
            modifier = stateTransitionModifier,
        )
        CurrentMedicationOperations(
            saving = state.isSaving,
            onAction = onAction,
            showSuspend = false,
            modifier = stateTransitionModifier,
        )
    }
}

@Composable
internal fun CompletedMedicationDetail(
    state: MedicalPlanUiState,
    plan: MedicationPlan,
    now: LocalDateTime,
    reminderRuntimeState: MedicationReminderRuntimeState,
    motionAllowed: Boolean,
    modifier: Modifier,
    onRequestReminderAccess: () -> Unit,
    onAction: (MedicalPlanAction) -> Unit,
) {
    val zoneId = ZoneId.systemDefault()
    val nowEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli()
    val lastTreatmentDate = remember(plan, nowEpochMillis) {
        plan.lastTreatmentDate(nowEpochMillis, zoneId)
            ?: plan.currentRevision.configuration?.endsOn
            ?: plan.currentRevision.endsOn
            ?: now.toLocalDate()
    }
    val completedRevision = remember(plan) {
        plan.revisions.lastOrNull {
            it.changeKind ==
                com.bitavida.app.domain.model.MedicationPlanChangeKind.COMPLETED
        }
    }
    val reminderLabel = if (
        plan.currentRevision.configuration?.reminderPreference ==
        MedicationReminderPreference.NONE
    ) {
        "No había avisos configurados."
    } else {
        "Los avisos finalizaron con este periodo de tratamiento."
    }
    CompositionLocalProvider(LocalCurrentDetailMotion provides motionAllowed) {
        MedicationDetailIdentity(
            plan = plan,
            statusLabel = "Finalizado",
            statusIsPositive = false,
            modifier = modifier,
        )
        SuspendedTreatmentCard(
            effectiveAtEpochMillis = lastTreatmentDate
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli(),
            reason = completedRevision?.changeReason
                ?: "Vigencia concluida",
            reminderLabel = reminderLabel,
            pendingReactivationAtEpochMillis = null,
            onReactivate = {
                onAction(MedicalPlanAction.StartNewMedicationPeriod)
            },
            onHistory = {
                onAction(MedicalPlanAction.OpenMedicationHistory)
            },
            title = "Tratamiento finalizado",
            statusValue = "Finalizado",
            momentLabel = "Fecha",
            momentValueOverride = lastTreatmentDate.fullMedicationDateWithYear(),
            primaryActionLabel = "Iniciar nuevo periodo",
            primaryActionIcon = R.drawable.ic_v2_stethoscope,
            modifier = modifier,
        )
        CurrentIndicationCard(
            plan = plan,
            runtime = reminderRuntimeState,
            motionAllowed = motionAllowed,
            onRequestReminderAccess = onRequestReminderAccess,
            title = "Última indicación registrada",
            referenceMessage =
                "Solo de referencia: este periodo ya finalizó.",
            reminderLabelOverride = reminderLabel,
            modifier = modifier,
        )
        CurrentMedicationOperations(
            saving = state.isSaving,
            onAction = onAction,
            showSuspend = false,
            showCorrection = false,
            showChangeIndication = false,
            modifier = modifier,
        )
    }
}

@Composable
private fun SuspendedTreatmentCard(
    effectiveAtEpochMillis: Long,
    reason: String?,
    reminderLabel: String,
    pendingReactivationAtEpochMillis: Long?,
    onReactivate: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Tratamiento suspendido",
    statusValue: String = "Suspendido temporalmente",
    momentLabel: String = "Desde",
    momentValueOverride: String? = null,
    primaryActionLabel: String? = null,
    primaryActionIcon: Int = R.drawable.ic_v2_clock,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(BitavidaSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(
                BitavidaSpacing.Small,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    BitavidaSpacing.Small,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailIcon(R.drawable.ic_v2_pause)
                Text(
                    title,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SuspendedTreatmentFact(
                icon = R.drawable.ic_v2_pause,
                label = "Estado",
                value = statusValue,
                accent = true,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SuspendedTreatmentFact(
                icon = R.drawable.ic_v2_clock,
                label = momentLabel,
                value = momentValueOverride
                    ?: effectiveAtEpochMillis.suspensionMomentLabel(),
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SuspendedTreatmentFact(
                icon = R.drawable.ic_v2_clipboard,
                label = "Motivo",
                value = reason?.takeIf(String::isNotBlank)
                    ?: "Sin motivo disponible en el registro anterior.",
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SuspendedTreatmentFact(
                icon = R.drawable.ic_medication_bell,
                label = "Recordatorios",
                value = reminderLabel,
            )
            pendingReactivationAtEpochMillis?.let {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SuspendedTreatmentFact(
                    icon = R.drawable.ic_v2_clock,
                    label = "Reactivación",
                    value = it.suspensionMomentLabel(),
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            CurrentDetailButton(
                kind = DetailButtonKind.FILLED,
                onClick = onReactivate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                DetailIcon(primaryActionIcon)
                Spacer(Modifier.size(8.dp))
                Text(
                    primaryActionLabel ?: if (
                        pendingReactivationAtEpochMillis == null
                    ) {
                        "Reactivar medicamento"
                    } else {
                        "Cambiar reactivación"
                    },
                )
            }
            CurrentDetailButton(
                kind = DetailButtonKind.OUTLINED,
                onClick = onHistory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                DetailIcon(R.drawable.ic_v2_calendar)
                Spacer(Modifier.size(8.dp))
                Text("Historial y calendario")
            }
        }
    }
}

@Composable
private fun SuspendedTreatmentFact(
    icon: Int,
    label: String,
    value: String,
    accent: Boolean = false,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val stackContent =
            maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                BitavidaSpacing.Small,
            ),
            verticalAlignment = Alignment.Top,
        ) {
            DetailIcon(icon)
            if (stackContent) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SuspendedTreatmentLabel(label)
                    SuspendedTreatmentValue(value, accent)
                }
            } else {
                SuspendedTreatmentLabel(
                    label = label,
                    modifier = Modifier.width(112.dp),
                )
                SuspendedTreatmentValue(
                    value = value,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SuspendedTreatmentLabel(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        label,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SuspendedTreatmentValue(
    value: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        value,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = if (accent) {
            MaterialTheme.bitavidaColors.pendingText
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

@Composable
private fun MedicationDetailIdentity(
    plan: MedicationPlan,
    statusLabel: String,
    statusIsPositive: Boolean,
    modifier: Modifier = Modifier,
) {
    val configuration = plan.currentRevision.configuration
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            MedicationListProductIcon(
                configuration?.form,
                Modifier.medicationIconTransition(plan.id).padding(14.dp).size(42.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    plan.currentRevision.medicationName,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                BitavidaStatusPill(statusLabel, isPositive = statusIsPositive)
            }
            Text(
                listOfNotNull(
                    configuration?.form?.displayName(),
                    configuration?.let {
                        listOfNotNull(it.concentrationValue, it.concentrationUnit)
                            .joinToString(" ").takeIf(String::isNotBlank)
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpcomingFirstAdministrationCard(
    configuration: com.bitavida.app.domain.model.MedicationConfiguration?,
    planStart: LocalDate,
    firstAdministration: FirstMedicationAdministration?,
    administrationLabel: String,
    motionAllowed: Boolean,
) {
    val asNeeded = configuration?.frequency == MedicationFrequencyKind.AS_NEEDED
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        AnimatedContent(
            targetState = Triple(asNeeded, firstAdministration, configuration),
            transitionSpec = {
                val duration = if (motionAllowed) {
                    BitavidaFluidMotion.StateTransition
                } else {
                    0
                }
                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
            },
            label = "upcoming first administration",
        ) { (needed, first, data) ->
            when {
                needed -> UpcomingAsNeededContent(data)
                first != null -> UpcomingFirstAdministrationContent(
                    planStart,
                    first,
                    administrationLabel,
                )
                else -> MedicationCompactUpcomingState(
                    title = administrationLabel,
                    body = "No es posible determinar una primera administración con la programación guardada.",
                )
            }
        }
    }
}

@Composable
private fun UpcomingFirstAdministrationContent(
    planStart: LocalDate,
    first: FirstMedicationAdministration,
    administrationLabel: String,
) {
    val moment = when (first.timing) {
        MedicationOccurrenceTiming.EXACT_TIME,
        MedicationOccurrenceTiming.HOURLY_INTERVAL,
        -> first.exactTime?.upcomingDisplayTime().orEmpty()
        MedicationOccurrenceTiming.TIME_WINDOW ->
            listOfNotNull(
                first.windowStart?.upcomingDisplayTime(),
                first.windowEnd?.upcomingDisplayTime(),
            ).joinToString(" – ") +
                if (first.windowEndsNextDay) " · día siguiente" else ""
        MedicationOccurrenceTiming.ACTIVITY_NOTICE ->
            first.activityRelation?.displayName(first.customActivity)
                ?: "Actividad registrada"
    }
    val reminder = first.reminderTime?.let {
        "Aviso a las ${it.upcomingDisplayTime()}" +
            if (first.reminderNextDay) " · día siguiente" else ""
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(BitavidaSpacing.Medium)) {
        val stacked = maxWidth.value / LocalDensity.current.fontScale < 300f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
                UpcomingFirstAmount(first, administrationLabel)
                UpcomingFirstMoment(moment, reminder, first.date, planStart)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.weight(1f)) {
                    UpcomingFirstAmount(first, administrationLabel)
                }
                Box(Modifier.weight(1f)) {
                    UpcomingFirstMoment(moment, reminder, first.date, planStart)
                }
            }
        }
    }
}

@Composable
private fun UpcomingFirstAmount(
    first: FirstMedicationAdministration,
    administrationLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
        Text(
            administrationLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "${first.amount.value} ${first.amount.unit}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun UpcomingFirstMoment(
    moment: String,
    reminder: String?,
    firstDate: LocalDate,
    planStart: LocalDate,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
        Text(
            moment,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (firstDate.isAfter(planStart)) {
            Text(
                firstDate.fullMedicationDate(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        reminder?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpcomingAsNeededContent(
    configuration: com.bitavida.app.domain.model.MedicationConfiguration?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(BitavidaSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        Text(
            "Cuando sea necesario",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        configuration?.asNeededInstruction?.takeIf(String::isNotBlank)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Sin administración programada",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MedicationCompactUpcomingState(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(BitavidaSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun MedicationIntakeDayPanel(
    state: MedicalPlanUiState,
    medication: MedicationPlan,
    date: LocalDate,
    now: LocalDateTime,
    motionAllowed: Boolean,
    selectedKey: String?,
    onSelectedKeyChange: (String?) -> Unit,
    historicalSchedule: Boolean,
    allowAsNeededRegistration: Boolean,
    onAction: (MedicalPlanAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val schedule = remember(medication, date, historicalSchedule) {
        if (historicalSchedule) medicationPlanSnapshotForDay(medication, date) else medication
    }
    val configuration = schedule.currentRevision.configuration
    val asNeeded = configuration?.frequency == MedicationFrequencyKind.AS_NEEDED
    val records = state.overview.intakesFor(medication.id)
    val occurrences = remember(schedule, date) {
        medicationOccurrencesForDay(schedule, date)
    }
    val resolved = remember(schedule, date, records, occurrences, medication) {
        occurrences.associate { occurrence ->
            occurrence.id to intakeForOccurrence(records, occurrence, occurrences, medication)
        }
    }
    val attachedIds = resolved.values.mapNotNull { it?.id }.toSet()
    val dayRecords = records.filter { record ->
        record.hasDeclaredResponse() &&
            if (historicalSchedule) {
                record.calendarDate(medication) == date
            } else {
                (record.currentRevision.actualAt ?: record.currentRevision.scheduledAt)
                    .toLocalDate() == date
            }
    }.sortedBy { it.currentRevision.actualAt ?: it.currentRevision.scheduledAt }
    val previousRecords = if (asNeeded) {
        emptyList()
    } else {
        dayRecords.filter { it.id !in attachedIds }
    }
    val draft = state.medicationIntakeDraft
        ?.takeIf {
            !historicalSchedule && it.inlineEntry && !it.quickEntry && !it.undoEntry
        }
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    val choose: (String) -> Unit = { key ->
        onSelectedKeyChange(key.takeUnless { selectedKey == key })
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(6.dp)) {
            if (asNeeded) {
                if (dayRecords.isEmpty()) {
                    Text(
                        if (allowAsNeededRegistration) {
                            "No hay tomas registradas hoy. Se registra solo cuando ocurre; no se generan omisiones."
                        } else {
                            "Aún no hay tomas registradas."
                        },
                        Modifier.padding(BitavidaSpacing.Medium),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                dayRecords.forEachIndexed { index, record ->
                    key(record.id) {
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            )
                        }
                        TodayRecordRow(
                            record = record,
                            expanded = selectedKey == "record:${record.id}",
                            duration = duration,
                            onSelect = { choose("record:${record.id}") },
                        ) {
                            if (draft?.recordId == record.id) {
                                InlineMedicationIntakeEditor(
                                    draft,
                                    state.fieldErrors,
                                    state.isSaving,
                                    onAction,
                                )
                            } else {
                                CurrentDetailButton(
                                    kind = DetailButtonKind.TEXT,
                                    onClick = {
                                        onAction(
                                            MedicalPlanAction.OpenEditMedicationIntake(record.id),
                                        )
                                    },
                                    enabled = !state.isSaving,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    DetailIcon(R.drawable.ic_v2_clock)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Corregir hora o motivo")
                                }
                            }
                        }
                    }
                }
                if (allowAsNeededRegistration) {
                    if (
                        draft?.asNeededEntry == true &&
                        draft.recordId == null &&
                        selectedKey == "new"
                    ) {
                        InlineMedicationIntakeEditor(
                            draft,
                            state.fieldErrors,
                            state.isSaving,
                            onAction,
                        )
                    } else {
                        CurrentDetailButton(
                            kind = DetailButtonKind.OUTLINED,
                            onClick = {
                                choose("new")
                                onAction(MedicalPlanAction.OpenRegisterMedicationIntake)
                            },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth()
                                .padding(10.dp)
                                .heightIn(min = 48.dp),
                        ) {
                            DetailIcon(R.drawable.ic_v2_plus)
                            Spacer(Modifier.size(8.dp))
                            Text("Registrar toma")
                        }
                    }
                }
            } else if (occurrences.isEmpty() && previousRecords.isEmpty()) {
                Text(
                    if (historicalSchedule) {
                        "Sin tomas programadas."
                    } else {
                        noMedicationAdministrationsMessage(schedule, date)
                    },
                    Modifier.padding(BitavidaSpacing.Medium),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                occurrences.forEachIndexed { index, occurrence ->
                    key(occurrence.id) {
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            )
                        }
                        val record = resolved[occurrence.id]
                        val rowKey = todaySlotKey(occurrence, occurrences)
                        TodayScheduledRow(
                            occurrence = occurrence,
                            record = record,
                            now = now,
                            expanded = selectedKey == rowKey,
                            duration = duration,
                            onSelect = { choose(rowKey) },
                        ) {
                            val editingThisRow =
                                draft != null &&
                                    !draft.asNeededEntry &&
                                    if (draft.recordId != null) {
                                        draft.recordId == record?.id
                                    } else {
                                        draft.scheduledOccurrenceId == occurrence.id
                                    }
                            if (editingThisRow) {
                                InlineMedicationIntakeEditor(
                                    requireNotNull(draft),
                                    state.fieldErrors,
                                    state.isSaving,
                                    onAction,
                                )
                            } else {
                                ScheduledRowActions(
                                    occurrence,
                                    record,
                                    state,
                                    duration,
                                    onAction,
                                )
                            }
                        }
                    }
                }
            }
            if (!asNeeded && previousRecords.isNotEmpty()) {
                Text(
                    "Registros anteriores",
                    Modifier.padding(BitavidaSpacing.Medium).semantics { heading() },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Se conservan aparte cuando no se puede identificar una única administración.",
                    Modifier.padding(horizontal = BitavidaSpacing.Medium),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                previousRecords.forEach { record ->
                    key(record.id) {
                        TodayRecordRow(
                            record = record,
                            expanded = selectedKey == "record:${record.id}",
                            duration = duration,
                            onSelect = { choose("record:${record.id}") },
                        ) {
                            if (draft?.recordId == record.id) {
                                InlineMedicationIntakeEditor(
                                    draft,
                                    state.fieldErrors,
                                    state.isSaving,
                                    onAction,
                                )
                            } else {
                                CurrentDetailButton(
                                    kind = DetailButtonKind.TEXT,
                                    onClick = {
                                        onAction(
                                            MedicalPlanAction.OpenEditMedicationIntake(record.id),
                                        )
                                    },
                                    enabled = !state.isSaving,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Text("Corregir registro anterior")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailIcon(resource: Int) {
    Image(
        painterResource(resource), null, Modifier.size(24.dp),
        colorFilter = ColorFilter.tint(LocalContentColor.current),
    )
}

private data class TodayScheduledVisual(
    val outcome: MedicationIntakeOutcome?,
    val label: String,
    val detail: String,
    val future: Boolean,
)

@Composable
private fun TodayScheduledRow(
    occurrence: UpcomingMedicationAdministration,
    record: MedicationIntakeRecord?,
    now: LocalDateTime,
    expanded: Boolean,
    duration: Int,
    onSelect: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val status = record?.currentRevision?.outcome
    val future = occurrence.startsAt.isAfter(now)
    val visual = TodayScheduledVisual(
        outcome = status,
        label = when (status) {
            MedicationIntakeOutcome.COMPLETED -> "Se tomó"
            MedicationIntakeOutcome.PARTIAL -> "Se tomó parcialmente"
            MedicationIntakeOutcome.OMITTED, MedicationIntakeOutcome.REFUSED -> "No se tomó"
            else -> if (future) "Programada" else "Sin confirmar"
        },
        detail = record?.currentRevision?.actualAt?.takeIf {
            status == MedicationIntakeOutcome.COMPLETED || status == MedicationIntakeOutcome.PARTIAL
        }?.let { actual ->
            "A las " + actual.toLocalTime().asMedicationTime().displayLabel(context) +
                if (actual.toLocalDate() != now.toLocalDate()) {
                    " · " + actual.toLocalDate().displayLabel()
                } else ""
        } ?: "${occurrence.amount.value} ${occurrence.amount.unit}",
        future = future,
    )
    val timing = when {
        occurrence.activityRelation != null ->
            occurrence.activityRelation.displayName(occurrence.customActivity)
        occurrence.endsAt != null ->
            occurrence.startsAt.toLocalTime().asMedicationTime().displayLabel(context) + " – " +
                occurrence.endsAt.toLocalTime().asMedicationTime().displayLabel(context) +
                if (occurrence.startsAt.toLocalDate() != occurrence.endsAt.toLocalDate()) " · día siguiente" else ""
        else -> occurrence.startsAt.toLocalTime().asMedicationTime().displayLabel(context)
    }
    TodayRowFrame(expanded, duration, onSelect, content) {
        Text(
            timing, Modifier.weight(0.4f),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        AnimatedContent(
            targetState = visual,
            modifier = Modifier.weight(0.6f),
            transitionSpec = {
                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
            },
            label = "scheduled_intake_status",
        ) { shown ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.padding(top = 2.dp)) {
                    ResponseIcon(shown.outcome, shown.future)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        shown.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        shown.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (occurrence.activityRelation != null) {
                        Text(
                            "Aviso · " +
                                occurrence.startsAt.toLocalTime().asMedicationTime().displayLabel(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayRecordRow(
    record: MedicationIntakeRecord,
    expanded: Boolean,
    duration: Int,
    onSelect: () -> Unit,
    content: @Composable () -> Unit,
) {
    val current = record.currentRevision
    val context = LocalContext.current
    TodayRowFrame(expanded, duration, onSelect, content) {
        Text(
            (current.actualAt ?: current.scheduledAt).toLocalTime().asMedicationTime().displayLabel(context),
            Modifier.weight(0.4f), style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            Modifier.weight(0.6f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.padding(top = 2.dp)) { ResponseIcon(current.outcome, future = false) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    if (current.outcome in setOf(MedicationIntakeOutcome.OMITTED, MedicationIntakeOutcome.REFUSED)) {
                        "No se tomó"
                    } else "Se tomó",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                current.actualAmount?.let {
                    Text("${it.value} ${it.unit}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
                current.observation?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
internal fun TodayRowFrame(
    expanded: Boolean,
    duration: Int,
    onSelect: () -> Unit,
    actions: @Composable () -> Unit,
    headingContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val transition = updateTransition(targetState = expanded, label = "selected_intake")
    val color by transition.animateColor(
        transitionSpec = { tween(duration) }, label = "selected_intake_tone",
    ) {
        if (it) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    }
    val shape = MaterialTheme.shapes.medium
    val source = remember { MutableInteractionSource() }
    val focus = LocalFocusManager.current
    Surface(
        color = color,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
            .bitavidaFluidInteraction(
                source, enabled = true, motionAllowed = duration > 0,
                shape = shape, restingElevationDp = 0f,
            ),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(
                        interactionSource = source,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            if (!expanded) focus.clearFocus()
                            onSelect()
                        },
                    )
                    .semantics {
                        selected = expanded
                        stateDescription = if (expanded) "Seleccionada, expandida" else "Contraída"
                    }
                    .padding(BitavidaSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                content = headingContent,
            )
            // One transition owns the full container tone and the visible action height.
            transition.AnimatedVisibility(
                visible = { it },
                enter = expandVertically(tween(duration), expandFrom = Alignment.Top) + fadeIn(tween(duration)),
                exit = shrinkVertically(tween(duration), shrinkTowards = Alignment.Top) + fadeOut(tween(duration)),
            ) {
                CompositionLocalProvider(LocalDoseActionsEnabled provides expanded) {
                    Column(
                        Modifier.fillMaxWidth().animateContentSize(tween(duration)).padding(
                            start = BitavidaSpacing.Medium, end = BitavidaSpacing.Medium,
                            bottom = BitavidaSpacing.Small,
                        ),
                        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                    ) { actions() }
                }
            }
        }
    }
}

internal enum class TodayStatusVisual {
    COMPLETE,
    NEGATIVE,
    PENDING,
    FUTURE,
}

@Composable
internal fun TodayStatusIcon(visual: TodayStatusVisual) {
    if (visual == TodayStatusVisual.FUTURE) {
        DetailIcon(R.drawable.ic_v2_clock)
        return
    }
    val complete = visual == TodayStatusVisual.COMPLETE
    val negative = visual == TodayStatusVisual.NEGATIVE
    val foreground = if (complete) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val background = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(24.dp).clearAndSetSemantics {}) {
        val width = 1.8.dp.toPx()
        val radius = size.minDimension / 2 - width / 2
        if (complete) {
            drawCircle(background, radius = radius)
            val check = Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.51f)
                lineTo(size.width * 0.44f, size.height * 0.69f)
                lineTo(size.width * 0.76f, size.height * 0.34f)
            }
            drawPath(
                check,
                foreground,
                style = Stroke(
                    width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        } else {
            drawCircle(foreground, radius = radius, style = Stroke(width))
            if (negative) {
                drawLine(
                    foreground,
                    Offset(size.width * 0.35f, size.height * 0.35f),
                    Offset(size.width * 0.65f, size.height * 0.65f),
                    width,
                    StrokeCap.Round,
                )
                drawLine(
                    foreground,
                    Offset(size.width * 0.65f, size.height * 0.35f),
                    Offset(size.width * 0.35f, size.height * 0.65f),
                    width,
                    StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun ResponseIcon(outcome: MedicationIntakeOutcome?, future: Boolean) {
    val taken = outcome == MedicationIntakeOutcome.COMPLETED ||
        outcome == MedicationIntakeOutcome.PARTIAL
    val notTaken = outcome == MedicationIntakeOutcome.OMITTED ||
        outcome == MedicationIntakeOutcome.REFUSED
    TodayStatusIcon(
        when {
            taken -> TodayStatusVisual.COMPLETE
            notTaken -> TodayStatusVisual.NEGATIVE
            future -> TodayStatusVisual.FUTURE
            else -> TodayStatusVisual.PENDING
        },
    )
}

private enum class ScheduledActionState {
    PENDING,
    TAKEN,
    NOT_TAKEN,
}

private fun MedicationIntakeOutcome?.scheduledActionState() = when (this) {
    MedicationIntakeOutcome.COMPLETED, MedicationIntakeOutcome.PARTIAL ->
        ScheduledActionState.TAKEN
    MedicationIntakeOutcome.OMITTED, MedicationIntakeOutcome.REFUSED ->
        ScheduledActionState.NOT_TAKEN
    else -> ScheduledActionState.PENDING
}

@Composable
private fun ScheduledRowActions(
    occurrence: UpcomingMedicationAdministration,
    record: MedicationIntakeRecord?,
    state: MedicalPlanUiState,
    duration: Int,
    onAction: (MedicalPlanAction) -> Unit,
) {
    val actionState = record?.currentRevision?.outcome.scheduledActionState()
    val transition = updateTransition(actionState, label = "scheduled_intake_actions")
    val enabled = !state.isSaving && !transition.isRunning
    val yesWeight by transition.animateFloat(
        transitionSpec = { tween(duration) },
        label = "taken_action_width",
    ) { if (it == ScheduledActionState.TAKEN) 0f else 1f }
    val noWeight by transition.animateFloat(
        transitionSpec = { tween(duration) },
        label = "not_taken_action_width",
    ) { if (it == ScheduledActionState.NOT_TAKEN) 0f else 1f }
    val yesAlpha by transition.animateFloat(
        transitionSpec = { tween(duration) },
        label = "taken_action_alpha",
    ) { if (it == ScheduledActionState.TAKEN) 0f else 1f }
    val noAlpha by transition.animateFloat(
        transitionSpec = { tween(duration) },
        label = "not_taken_action_alpha",
    ) { if (it == ScheduledActionState.NOT_TAKEN) 0f else 1f }
    val actionGap by transition.animateDp(
        transitionSpec = { tween(duration) },
        label = "scheduled_action_gap",
    ) { if (it == ScheduledActionState.PENDING) 8.dp else 0.dp }
    val yes: @Composable (Modifier) -> Unit = { modifier ->
        CurrentDetailButton(
            kind = DetailButtonKind.FILLED,
            onClick = {
                onAction(
                    MedicalPlanAction.QuickRegisterMedicationIntake(
                        occurrence,
                        MedicationIntakeOutcome.COMPLETED,
                    ),
                )
            },
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
        ) { Text("Se tomó") }
    }
    val no: @Composable (Modifier) -> Unit = { modifier ->
        CurrentDetailButton(
            kind = DetailButtonKind.OUTLINED,
            strongOutline = true,
            onClick = {
                onAction(
                    MedicalPlanAction.QuickRegisterMedicationIntake(
                        occurrence,
                        MedicationIntakeOutcome.OMITTED,
                    ),
                )
            },
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
        ) { Text("No se tomó") }
    }
    val includesPending =
        transition.currentState == ScheduledActionState.PENDING ||
            transition.targetState == ScheduledActionState.PENDING
    if (includesPending) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 260.dp || LocalDensity.current.fontScale >= 1.5f) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    transition.AnimatedVisibility(
                        visible = { it != ScheduledActionState.TAKEN },
                        enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
                    ) {
                        yes(Modifier.fillMaxWidth())
                    }
                    transition.AnimatedVisibility(
                        visible = { it != ScheduledActionState.NOT_TAKEN },
                        enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
                    ) {
                        no(Modifier.fillMaxWidth())
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.weight(yesWeight.coerceAtLeast(0.001f))
                            .clip(MaterialTheme.shapes.medium)
                            .graphicsLayer { alpha = yesAlpha },
                    ) {
                        yes(Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.width(actionGap))
                    Box(
                        Modifier.weight(noWeight.coerceAtLeast(0.001f))
                            .clip(MaterialTheme.shapes.medium)
                            .graphicsLayer { alpha = noAlpha },
                    ) {
                        no(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    } else {
        AnimatedContent(
            targetState = actionState,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
            },
            label = "registered_intake_action",
        ) { shown ->
            when (shown) {
                ScheduledActionState.TAKEN -> no(Modifier.fillMaxWidth())
                ScheduledActionState.NOT_TAKEN -> yes(Modifier.fillMaxWidth())
                ScheduledActionState.PENDING -> Unit
            }
        }
    }
    CurrentDetailButton(
        kind = DetailButtonKind.TEXT,
        onClick = { onAction(MedicalPlanAction.OpenMedicationIntakeOptions(occurrence)) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        DetailIcon(R.drawable.ic_v2_clock)
        Spacer(Modifier.size(8.dp))
        AnimatedContent(
            targetState = actionState == ScheduledActionState.NOT_TAKEN,
            transitionSpec = {
                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
            },
            label = "intake_note_action",
        ) { editingReason ->
            Text(if (editingReason) "Editar nota o motivo" else "Otra hora o nota")
        }
    }
}

@Composable
internal fun InlineMedicationIntakeEditor(
    draft: MedicationIntakeDraft,
    errors: Set<MedicalCareField>,
    saving: Boolean,
    onAction: (MedicalPlanAction) -> Unit,
) {
    val context = LocalContext.current
    val dateTimePicker = LocalBitavidaDateTimePickerController.current
    fun change(next: MedicationIntakeDraft) = onAction(MedicalPlanAction.MedicationIntakeDraftChanged(next))
    val actualTime = runCatching { LocalTime.parse(draft.actualTime) }.getOrNull()
    Column(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        if (draft.outcome == MedicationIntakeOutcome.COMPLETED || draft.outcome == MedicationIntakeOutcome.PARTIAL) {
            Text("Hora real de la toma", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface)
            CurrentDetailButton(
                kind = DetailButtonKind.OUTLINED,
                onClick = {
                    showMedicationTimePicker(dateTimePicker, actualTime?.asMedicationTime()) {
                        val value = LocalTime.of(it.hour, it.minute).toString()
                        change(draft.copy(
                            actualTime = value,
                            scheduledTime = if (draft.asNeededEntry && draft.recordId == null) value else draft.scheduledTime,
                        ))
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                DetailIcon(R.drawable.ic_v2_clock)
                Spacer(Modifier.size(8.dp))
                Text(actualTime?.asMedicationTime()?.displayLabel(context) ?: "Elegir hora real")
            }
            if (MedicalCareField.INTAKE_ACTUAL_TIME in errors) {
                Text("Selecciona la hora real.", color = MaterialTheme.colorScheme.error)
            }
        }
        BitavidaLimitedTextField(
            value = draft.observation,
            onValueChange = { change(draft.copy(observation = it)) },
            limit = CaptureLimit.OBSERVATION,
            label = { Text(if (draft.asNeededEntry) "¿Por qué fue necesaria la toma?" else "Nota o motivo (opcional)") },
            isError = MedicalCareField.INTAKE_OBSERVATION in errors,
            supportingText = if (draft.asNeededEntry && MedicalCareField.INTAKE_OBSERVATION in errors) {
                { Text("Escribe el motivo de esta toma para confirmar.") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        if (draft.recordId != null) {
            Text("La corrección conservará el registro anterior.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CurrentDetailButton(
            kind = DetailButtonKind.OUTLINED,
            onClick = { onAction(MedicalPlanAction.DismissMedicationIntake) },
            enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Cancelar") }
        CurrentDetailButton(
            kind = DetailButtonKind.FILLED,
            onClick = { onAction(MedicalPlanAction.SaveMedicationIntake) },
            enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(if (draft.recordId == null) "Confirmar toma" else "Guardar corrección") }
    }
}

@Composable
internal fun CurrentIndicationCardFrame(
    expansionKey: String,
    title: String,
    summary: String,
    reminderLabel: String,
    motionAllowed: Boolean,
    modifier: Modifier = Modifier,
    supportingContent: @Composable () -> Unit = {},
    detailContent: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(expansionKey) {
        mutableStateOf(value = false)
    }
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    Surface(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(BitavidaSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DetailIcon(R.drawable.ic_v2_chevron)
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailIcon(R.drawable.ic_medication_bell)
                Text(
                    reminderLabel,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            supportingContent()
            AnimatedVisibility(
                expanded,
                enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    detailContent()
                }
            }
        }
    }
}

@Composable
private fun CurrentIndicationCard(
    plan: MedicationPlan,
    runtime: MedicationReminderRuntimeState,
    motionAllowed: Boolean,
    onRequestReminderAccess: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Indicación vigente",
    referenceMessage: String? = null,
    reminderLabelOverride: String? = null,
) {
    val configuration = plan.currentRevision.configuration
    CurrentIndicationCardFrame(
        expansionKey = plan.id,
        title = title,
        summary = (
            configuration?.baseAmount?.let { "${it.value} ${it.unit} por toma" }
                ?: plan.currentRevision.doseAsIndicated
            ) + " · " +
            (
                configuration?.frequencySummary()
                    ?: plan.currentRevision.frequencyAsIndicated.orEmpty()
                ),
        reminderLabel = reminderLabelOverride
            ?: configuration?.reminderPreference
                ?.reminderRuntimeLabel(runtime)
            ?: "Avisos desactivados",
        motionAllowed = motionAllowed,
        modifier = modifier,
        supportingContent = {
            referenceMessage?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(BitavidaSpacing.Small),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DetailIcon(R.drawable.ic_v2_file)
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        detailContent = {
            MedicationProgrammingDetails(configuration)
            if (
                runtime == MedicationReminderRuntimeState.PERMISSION_REQUIRED &&
                configuration?.reminderPreference !=
                MedicationReminderPreference.NONE
            ) {
                CurrentDetailButton(
                    kind = DetailButtonKind.TEXT,
                    onClick = onRequestReminderAccess,
                ) {
                    Text("Permitir avisos")
                }
            }
        },
    )
}

internal enum class DetailButtonKind { FILLED, OUTLINED, TEXT }

private val LocalCurrentDetailMotion = staticCompositionLocalOf<Boolean?> { null }
private val LocalDoseActionsEnabled = staticCompositionLocalOf { true }

@Composable
internal fun CurrentDetailButton(
    kind: DetailButtonKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    strongOutline: Boolean = false,
    destructive: Boolean = false,
    motionAllowedOverride: Boolean? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val active = enabled && LocalDoseActionsEnabled.current
    val motionAllowed = motionAllowedOverride ?: LocalCurrentDetailMotion.current
        ?: rememberBitavidaMotionAllowed(override = null)
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    val foreground = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val response = modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget)
        .bitavidaFluidInteraction(
            source, active, motionAllowed, shape = shape, restingElevationDp = 0f,
        )
    val buttonContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
    }
    when (kind) {
        DetailButtonKind.FILLED -> Button(
            onClick = onClick, enabled = active, modifier = response,
            interactionSource = source, shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            content = buttonContent,
        )
        DetailButtonKind.OUTLINED -> OutlinedButton(
            onClick = onClick, enabled = active, modifier = response,
            interactionSource = source, shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = foreground),
            border = BorderStroke(
                1.dp,
                when {
                    !active -> MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                    strongOutline || destructive -> foreground
                    else -> MaterialTheme.colorScheme.outline
                },
            ),
            content = buttonContent,
        )
        DetailButtonKind.TEXT -> TextButton(
            onClick = onClick, enabled = active, modifier = response,
            interactionSource = source, shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.textButtonColors(contentColor = foreground),
            content = buttonContent,
        )
    }
}

@Composable
internal fun CurrentMedicationBack(
    onBack: () -> Unit,
    motionAllowed: Boolean,
    enabled: Boolean,
    label: String = "Medicamentos",
) {
    CurrentDetailButton(
        kind = DetailButtonKind.TEXT,
        onClick = onBack,
        enabled = enabled,
        motionAllowedOverride = motionAllowed,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
    ) {
        DetailIcon(R.drawable.ic_v2_arrow_back)
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CurrentMedicationTodayHeader(
    date: LocalDate,
    counter: String,
    motionAllowed: Boolean,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateLabel = remember(date) { date.fullMedicationDate() }
    MedicationDetailSectionHeader(
        title = "Tomas de hoy",
        subtitle = dateLabel,
        trailing = counter,
        motionAllowed = motionAllowed,
        onCalendar = onCalendar,
        modifier = modifier,
    )
}

@Composable
private fun MedicationDetailSectionHeader(
    title: String,
    subtitle: String,
    trailing: String,
    motionAllowed: Boolean,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val compactCalendar = availableWidth.value / LocalDensity.current.fontScale < 300f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                Text(
                    title,
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.widthIn(max = availableWidth * 0.5f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CurrentDetailButton(
                    kind = DetailButtonKind.OUTLINED,
                    onClick = onCalendar,
                    modifier = if (compactCalendar) {
                        Modifier.semantics { contentDescription = "Abrir calendario" }
                    } else Modifier,
                    contentPadding = if (compactCalendar) {
                        PaddingValues(12.dp)
                    } else ButtonDefaults.ContentPadding,
                ) {
                    DetailIcon(R.drawable.ic_v2_calendar)
                    if (!compactCalendar) {
                        Spacer(Modifier.size(8.dp))
                        Text("Calendario")
                    }
                }
                AnimatedContent(
                    targetState = trailing,
                    transitionSpec = {
                        val duration = if (motionAllowed) {
                            BitavidaFluidMotion.StateTransition
                        } else {
                            0
                        }
                        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                    },
                    label = "medication detail header trailing",
                ) { label ->
                    Text(
                        label,
                        modifier = Modifier.fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentMedicationOperations(
    saving: Boolean,
    onAction: (MedicalPlanAction) -> Unit,
    modifier: Modifier = Modifier,
    showSuspend: Boolean = true,
    showCorrection: Boolean = true,
    showChangeIndication: Boolean = true,
    onChangeIndication: (() -> Unit)? = null,
) {
    CurrentManagementActionGroup(
        saving = saving,
        onAction = onAction,
        showCorrection = showCorrection,
        showChangeIndication = showChangeIndication,
        onChangeIndication = onChangeIndication,
        modifier = modifier,
    )
    if (showSuspend) {
        CurrentDetailButton(
            kind = DetailButtonKind.OUTLINED,
        onClick = {
            onAction(
                MedicalPlanAction.RequestMedicationLifecycle(
                    MedicationTreatmentStatus.SUSPENDED,
                ),
            )
        },
        modifier = modifier.fillMaxWidth(),
        enabled = !saving,
        strongOutline = true,
    ) {
        DetailIcon(R.drawable.ic_v2_pause)
        Spacer(Modifier.size(8.dp))
            Text("Suspender")
        }
    }
    CurrentDetailButton(
        kind = DetailButtonKind.OUTLINED,
        onClick = {
            onAction(
                MedicalPlanAction.RequestMedicationLifecycle(
                    MedicationTreatmentStatus.REMOVED,
                ),
            )
        },
        modifier = modifier.fillMaxWidth(),
        enabled = !saving,
        destructive = true,
    ) {
        DetailIcon(R.drawable.ic_medication_delete)
        Spacer(Modifier.size(8.dp))
        Text("Eliminar medicamento")
    }
}

@Composable
private fun CurrentManagementActionGroup(
    saving: Boolean,
    onAction: (MedicalPlanAction) -> Unit,
    showCorrection: Boolean,
    showChangeIndication: Boolean,
    modifier: Modifier = Modifier,
    onChangeIndication: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.large
    val separator = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            if (showCorrection) {
                CurrentManagementRow(
                    R.drawable.ic_v2_edit,
                    "Corregir datos",
                    !saving,
                ) {
                    onAction(MedicalPlanAction.OpenCorrectMedication)
                }
                HorizontalDivider(
                    Modifier.padding(horizontal = BitavidaSpacing.Medium),
                    color = separator,
                )
            }
            if (showChangeIndication) {
                CurrentManagementRow(
                    R.drawable.ic_v2_stethoscope,
                    "Cambiar indicación médica",
                    !saving,
                ) {
                    onChangeIndication?.invoke()
                        ?: onAction(MedicalPlanAction.OpenUpdateMedicationIndication)
                }
                HorizontalDivider(
                    Modifier.padding(horizontal = BitavidaSpacing.Medium),
                    color = separator,
                )
            }
            CurrentManagementRow(
                R.drawable.ic_v2_history,
                "Historial de cambios",
                !saving,
            ) {
                onAction(MedicalPlanAction.OpenMedicationChanges)
            }
        }
    }
}

@Composable
private fun CurrentManagementRow(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val motionAllowed = LocalCurrentDetailMotion.current
        ?: rememberBitavidaMotionAllowed(override = null)
    val shape = MaterialTheme.shapes.medium
    val foreground = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val iconColor = if (enabled) MaterialTheme.colorScheme.primary else foreground
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 64.dp)
            .bitavidaFluidInteraction(
                source, enabled, motionAllowed, shape = shape, restingElevationDp = 0f,
            )
            .clickable(
                interactionSource = source,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = BitavidaSpacing.Medium, vertical = BitavidaSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(iconColor))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = foreground)
        Image(
            painterResource(R.drawable.ic_v2_chevron), null, Modifier.size(16.dp),
            colorFilter = ColorFilter.tint(
                if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else foreground,
            ),
        )
    }
}

private fun Long.suspensionMomentLabel(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(
            DateTimeFormatter.ofPattern(
                "EEEE d 'de' MMMM 'de' uuuu · h:mm a",
                Locale.forLanguageTag("es-MX"),
            ),
        )
        .replaceFirstChar {
            it.titlecase(Locale.forLanguageTag("es-MX"))
        }

private fun LocalDate.fullMedicationDate(): String =
    format(
        DateTimeFormatter.ofPattern(
            "EEEE d 'de' MMMM",
            Locale.forLanguageTag("es-MX"),
        ),
    ).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-MX")) }

private fun LocalDate.fullMedicationDateWithYear(): String =
    format(
        DateTimeFormatter.ofPattern(
            "EEEE d 'de' MMMM 'de' uuuu",
            Locale.forLanguageTag("es-MX"),
        ),
    ).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-MX")) }

private fun LocalTime.upcomingDisplayTime(): String =
    format(DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-MX")))
        .lowercase(Locale.forLanguageTag("es-MX"))
        .replace("am", "a. m.")
        .replace("pm", "p. m.")

private fun LocalTime.asMedicationTime() = MedicationTime(hour, minute)
