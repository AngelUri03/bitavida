package com.bitavida.app.ui.measurements

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.MeasurementFollowUp
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementReminderInfo
import com.bitavida.app.domain.model.MeasurementReminderStatus
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.usecase.projectMeasurementOverview
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.medical.CurrentMedicationBack
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDate

data class GlucosePreviewScenario(val hasRecords: Boolean, val hasFollowUp: Boolean)

class GlucoseScenarioProvider : PreviewParameterProvider<GlucosePreviewScenario> {
    override val values = sequenceOf(
        GlucosePreviewScenario(hasRecords = false, hasFollowUp = false),
        GlucosePreviewScenario(hasRecords = false, hasFollowUp = true),
        GlucosePreviewScenario(hasRecords = true, hasFollowUp = false),
        GlucosePreviewScenario(hasRecords = true, hasFollowUp = true),
    )
}

@Preview(name = "Glucosa A–D · claro", showBackground = true)
@Preview(name = "Glucosa A–D · movimiento reducido", widthDp = 390, heightDp = 900, showBackground = true)
@Preview(name = "Glucosa A–D · oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Glucosa A–D · 320 dp", widthDp = 320, heightDp = 900, showBackground = true)
@Preview(name = "Glucosa A–D · texto 200 %", widthDp = 320, heightDp = 1100, fontScale = 2f, showBackground = true)
@Composable
private fun GlucoseStatesPreview(@PreviewParameter(GlucoseScenarioProvider::class) scenario: GlucosePreviewScenario) {
    val today = LocalDate.of(2026, 11, 18)
    // All readings and configuration below are fictitious and never reach the application route.
    val records = if (scenario.hasRecords) listOf(
        glucosePreviewRecord("monday", today.minusDays(2), 8, "132"),
        glucosePreviewRecord("tuesday-hi", today.minusDays(1), 9, "HI"),
        glucosePreviewRecord(
            "wednesday-fasting",
            today,
            7,
            "104",
            GlucoseContext.FASTING,
        ),
        glucosePreviewRecord(
            "wednesday-before-meal",
            today,
            11,
            "118",
            GlucoseContext.BEFORE_MEAL,
        ),
        glucosePreviewRecord(
            "wednesday-after-meal",
            today,
            14,
            "146",
            GlucoseContext.AFTER_MEAL,
        ),
        glucosePreviewRecord(
            "wednesday-evening",
            today,
            18,
            "126",
            GlucoseContext.OTHER,
        ),
        glucosePreviewRecord(
            "wednesday-sleep",
            today,
            22,
            "112",
            GlucoseContext.BEFORE_SLEEP,
        ),
    ) else emptyList()
    val followUp = if (scenario.hasFollowUp) MeasurementFollowUp(
        kind = MeasurementKind.CAPILLARY_GLUCOSE,
        summary = "2 mediciones al día · Todos los días",
        frequency = "2 mediciones al día",
        periodicity = "Todos los días",
        moments = listOf("Antes de desayunar", "Antes de dormir"),
        reminders = MeasurementReminderInfo(MeasurementReminderStatus.SYSTEM_LIMITED),
    ) else null
    val summary = projectMeasurementOverview(records, today, listOfNotNull(followUp)).primary.first()
    BitavidaTheme {
        BitavidaFluidBackground {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
            ) {
                CurrentMedicationBack(
                    onBack = {},
                    motionAllowed = false,
                    enabled = true,
                    label = "Mediciones",
                )
                MeasurementHeader(
                    title = "Glucosa capilar",
                    person = "Persona de ejemplo",
                    showSubtitle = false,
                    kind = MeasurementKind.CAPILLARY_GLUCOSE,
                    followUp = followUp,
                )
                GlucoseDetailContent(
                    summary = summary,
                    records = records,
                    today = today,
                    onRegister = null,
                    onFollowUpAction = null,
                    onHelp = {},
                    onHistory = {},
                )
            }
        }
    }
}

private fun glucosePreviewRecord(
    id: String,
    date: LocalDate,
    hour: Int,
    reading: String,
    context: GlucoseContext = GlucoseContext.AFTER_MEAL,
): MeasurementRecord {
    val occurredAt = date.atTime(hour, 15)
    return MeasurementRecord(
        id,
        MeasurementValue.Glucose(reading, context),
        occurredAt,
        occurredAt,
    )
}
