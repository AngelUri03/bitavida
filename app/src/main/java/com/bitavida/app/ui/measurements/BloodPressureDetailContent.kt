package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.BitavidaLimitedTextField

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.projectBloodPressureToday
import com.bitavida.app.domain.usecase.MeasurementSummary
import com.bitavida.app.domain.usecase.projectMeasurementHistoryWeek
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun BloodPressureDetailContent(
    summary: MeasurementSummary,
    records: List<MeasurementRecord>,
    today: LocalDate,
    onRegister: ((MeasurementKind) -> Unit)?,
    onFollowUpAction:
        ((MeasurementKind, MeasurementFollowUpAction) -> Unit)?,
    onHelp: (() -> Unit)?,
    onHistory: (() -> Unit)? = null,
    followUp: GlucoseFollowUp? = null,
    decisions: List<GlucoseOccurrenceDecision> = emptyList(),
    onRegisterOccurrence:
        ((GlucoseScheduledOccurrence) -> Unit)? = null,
    onNotPerformed:
        ((GlucoseScheduledOccurrence, String?) -> Unit)? = null,
) {
    val presentation = remember(summary, followUp) {
        glucoseDetailPresentation(
            summary = summary,
            hasFollowUpHistory = true,
        )
    }
    var selectedDateText by rememberSaveable {
        mutableStateOf(today.toString())
    }
    val selectedDate = LocalDate.parse(selectedDateText)
    val weekStart = selectedDate.minusDays(
        (selectedDate.dayOfWeek.value - 1).toLong(),
    )
    val week = remember(records, weekStart, today) {
        projectMeasurementHistoryWeek(
            records,
            MeasurementKind.BLOOD_PRESSURE,
            weekStart,
            today,
        )
    }

    BloodPressureLatestMeasurement(
        summary = summary,
        presentation = presentation,
        today = today,
        records = records,
        followUp = followUp,
        decisions = decisions,
        onRegister = onRegister,
        onRegisterOccurrence = onRegisterOccurrence,
        onNotPerformed = onNotPerformed,
    )
    BloodPressureWeekEvaluation(
        week = week,
        selectedDate = selectedDate,
        onSelectDate = {
            selectedDateText = it.toString()
        },
        onHistory = onHistory,
    )
    if (presentation.showIndication) {
        summary.followUp?.let {
            GlucoseCurrentIndication(
                followUp = it,
                storedFollowUp = followUp,
                expansionKeyFallback =
                    "blood-pressure-follow-up",
                testTag =
                    "blood_pressure_current_indication",
            )
        }
    }
    GlucoseManagement(
        presentation = presentation,
        onAction = onFollowUpAction,
        onHelp = onHelp,
        measurementKind = MeasurementKind.BLOOD_PRESSURE,
        testTag = "blood_pressure_management",
    )
}

@Composable
private fun BloodPressureLatestMeasurement(
    summary: MeasurementSummary,
    presentation: GlucoseDetailPresentation,
    today: LocalDate,
    records: List<MeasurementRecord>,
    followUp: GlucoseFollowUp?,
    decisions: List<GlucoseOccurrenceDecision>,
    onRegister: ((MeasurementKind) -> Unit)?,
    onRegisterOccurrence:
        ((GlucoseScheduledOccurrence) -> Unit)?,
    onNotPerformed:
        ((GlucoseScheduledOccurrence, String?) -> Unit)?,
) {
    var pendingNotPerformed by remember {
        mutableStateOf<GlucoseScheduledOccurrence?>(null)
    }
    var reason by rememberSaveable { mutableStateOf("") }
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val todaySummary = remember(
        today,
        followUp,
        records,
        decisions,
    ) {
        projectBloodPressureToday(
            today,
            followUp,
            records,
            decisions,
        )
    }

    MeasurementPanel(
        modifier =
            Modifier.testTag("blood_pressure_latest_measurement"),
        color = MaterialTheme.bitavidaColors.paleBlue,
    ) {
        SectionTitle("Última medición", small = true)
        val record = summary.latest
        if (record == null) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                Text(
                    "Sin registros",
                    style = MaterialTheme.typography.headlineSmall,
                )
                SecondaryText(
                    "Tu primera lectura iniciará este historial.",
                )
            }
        } else {
            val date = if (
                record.occurredAt.toLocalDate() == today
            ) {
                "Hoy, " + record.occurredAt.format(
                    DateTimeFormatter.ofPattern(
                        "HH:mm",
                        measurementLocale,
                    ),
                )
            } else {
                record.occurredAt.format(measurementDateTime)
            }
            val moment = record.bloodPressureMetadata
                ?.actualContext
                ?.captureLabel()
            val timestamp: @Composable () -> Unit = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        BitavidaSpacing.ExtraSmall,
                    ),
                ) {
                    MeasurementIcon(R.drawable.ic_v2_clock, 18)
                    SecondaryText(date)
                }
            }
            val largeText =
                LocalDensity.current.fontScale >= 1.5f
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val sideBySide =
                    !largeText && (maxWidth >= 250.dp)
                val useProminentReading = maxWidth >= 320.dp
                if (sideBySide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                BitavidaSpacing.Small,
                            ),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1.7f)) {
                            MeasurementReading(
                                record = record,
                                prominent = useProminentReading,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    BitavidaSpacing.ExtraSmall,
                                ),
                            horizontalAlignment = Alignment.End,
                        ) {
                            moment?.let { PressureMomentChip(it) }
                            timestamp()
                        }
                    }
                } else {
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(
                                BitavidaSpacing.Small,
                            ),
                    ) {
                        MeasurementReading(
                            record = record,
                            prominent = !largeText,
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    BitavidaSpacing.ExtraSmall,
                                ),
                            horizontalAlignment = Alignment.End,
                        ) {
                            moment?.let { PressureMomentChip(it) }
                            timestamp()
                        }
                    }
                }
            }
        }
        MeasurementButton(
            label = presentation.registerLabel,
            onClick = {
                onRegister?.invoke(
                    MeasurementKind.BLOOD_PRESSURE,
                )
            },
            modifier = Modifier.fillMaxWidth()
                .testTag("blood_pressure_register"),
            enabled = onRegister != null,
            icon = R.drawable.ic_v2_plus,
            accessibleLabel =
                presentation.registerLabel +
                    ", presión arterial",
        )
    }

    if (presentation.showTodayRegister) {
        GlucoseTodayRegister(
            summary = todaySummary,
            onRegisterOccurrence = onRegisterOccurrence,
        ) {
            pendingNotPerformed = it
        }
    }
    pendingNotPerformed?.let { occurrence ->
        val dismiss = {
            pendingNotPerformed = null
            reason = ""
        }
        MedicationLifecycleSheetFrame(
            medication = null,
            title = "Confirmar que no se realizó",
            subtitle =
                "Esta decisión solo corresponde a este horario.",
            saving = false,
            motionAllowed = motionAllowed,
            onDismiss = dismiss,
        ) {
            SecondaryText(
                "La programación se conservará y ninguna ausencia se " +
                    "registrará sin esta confirmación.",
            )
            BitavidaLimitedTextField(
                value = reason,
                limit = CaptureLimit.CHANGE_REASON,
                onValueChange = { reason = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Motivo opcional") },
                minLines = 2,
                maxLines = 4,
            )
            MedicationLifecyclePrimaryAction(
                label = "Confirmar no realizada",
                icon = R.drawable.ic_v2_close,
                enabled = onNotPerformed != null,
                saving = false,
                destructive = false,
                motionAllowed = motionAllowed,
            ) {
                onNotPerformed?.invoke(
                    occurrence,
                    reason.trim().takeIf(String::isNotEmpty),
                )
                dismiss()
            }
            MedicationLifecycleCancelAction(
                enabled = true,
                label = "Conservar sin confirmar",
                onClick = dismiss,
            )
        }
    }
}

@Composable
private fun PressureMomentChip(label: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = BitavidaSpacing.Medium,
                vertical = BitavidaSpacing.ExtraSmall,
            ),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
