package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.BitavidaLimitedTextField

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.WeightTarget
import com.bitavida.app.domain.model.projectWeightToday
import com.bitavida.app.domain.usecase.bodyMassIndex
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
internal fun WeightDetailContent(
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
    target: WeightTarget? = null,
    onConfigureTarget: (() -> Unit)? = null,
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
            MeasurementKind.WEIGHT,
            weekStart,
            today,
        )
    }

    WeightLatestMeasurement(
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
    GlucoseWeekEvaluation(
        week = week,
        selectedDate = selectedDate,
        onSelectDate = {
            selectedDateText = it.toString()
        },
        onHistory = onHistory,
        unit = "kg",
        referenceValue = target?.kilograms,
        referenceLabel = "Objetivo",
        historyContentDescription = "peso",
        testTagPrefix = "weight",
    )
    if (presentation.showIndication) {
        summary.followUp?.let {
            GlucoseCurrentIndication(
                followUp = it,
                storedFollowUp = followUp,
                expansionKeyFallback =
                    "weight-follow-up",
                testTag =
                    "weight_current_indication",
                showMeasurementContext = false,
            )
        }
    }
    GlucoseManagement(
        presentation = presentation,
        onAction = onFollowUpAction,
        onHelp = onHelp,
        measurementKind = MeasurementKind.WEIGHT,
        testTag = "weight_management",
        extraActionIcon = R.drawable.ic_measurement_weight,
        extraActionLabel = "Configurar peso objetivo",
        onExtraAction = onConfigureTarget,
    )
}

@Composable
private fun WeightLatestMeasurement(
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
        projectWeightToday(
            today,
            followUp,
            records,
            decisions,
        )
    }

    MeasurementPanel(
        modifier =
            Modifier.testTag("weight_latest_measurement"),
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
                            WeightReading(
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
                        WeightReading(
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
                    MeasurementKind.WEIGHT,
                )
            },
            modifier = Modifier.fillMaxWidth()
                .testTag("weight_register"),
            enabled = onRegister != null,
            icon = R.drawable.ic_v2_plus,
            accessibleLabel =
                presentation.registerLabel +
                    ", peso",
        )
    }

    if (presentation.showTodayRegister) {
        GlucoseTodayRegister(
            summary = todaySummary,
            onRegisterOccurrence = onRegisterOccurrence,
            showMeasurementContext = false,
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
private fun WeightReading(
    record: MeasurementRecord,
    prominent: Boolean,
) {
    MeasurementReading(record = record, prominent = prominent)
    val weight = record.value as MeasurementValue.Weight
    weight.bodyMassIndex()?.let { bmi ->
        SecondaryText("IMC: " + bmi.stripTrailingZeros().toPlainString())
    }
}

