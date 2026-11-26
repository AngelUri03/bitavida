package com.bitavida.app.ui.measurements

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.WeightEntryKind
import com.bitavida.app.domain.model.WeightRecordMetadata
import com.bitavida.app.domain.model.WeightRegistrationContext
import com.bitavida.app.domain.model.WeightTarget
import com.bitavida.app.domain.usecase.GlucoseHistoryPeriodKind
import com.bitavida.app.domain.usecase.MeasurementSummary
import com.bitavida.app.domain.usecase.WeightDraft
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

private val weightPreviewToday = LocalDate.of(2026, 11, 18)

private fun weightPreviewRecords(): List<MeasurementRecord> =
    listOf("74.2", "74.4", "74.1", "74.3").mapIndexed { index, value ->
        val occurredAt = weightPreviewToday
            .minusDays((3 - index).toLong())
            .atTime(7 + index, 20)
        MeasurementRecord(
            id = "weight-preview-$index",
            value = MeasurementValue.Weight(
                kilograms = BigDecimal(value),
                heightMeters = BigDecimal("1.68"),
            ),
            occurredAt = occurredAt,
            capturedAt = occurredAt.plusMinutes(2),
            weightMetadata = WeightRecordMetadata(
                entryKind = WeightEntryKind.INDEPENDENT,
                note = if (index == 3) "Dato ficticio de preview." else null,
            ),
        )
    }

@Preview(name = "Peso detalle · claro", showBackground = true)
@Preview(
    name = "Peso detalle · oscuro",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(
    name = "Peso detalle · compacto",
    widthDp = 320,
    heightDp = 900,
    showBackground = true,
)
@Preview(
    name = "Peso detalle · texto 200 %",
    widthDp = 320,
    heightDp = 1100,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun WeightDetailPreview() {
    val records = weightPreviewRecords()
    BitavidaTheme {
        Surface {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Default),
            ) {
                WeightDetailContent(
                    summary = MeasurementSummary(
                        kind = MeasurementKind.WEIGHT,
                        latest = records.maxBy(MeasurementRecord::occurredAt),
                        followUp = null,
                    ),
                    records = records,
                    today = weightPreviewToday,
                    onRegister = {},
                    onFollowUpAction = { _, _ -> },
                    onHelp = null,
                    onHistory = {},
                    target = WeightTarget(BigDecimal("70")),
                    onConfigureTarget = {},
                )
            }
        }
    }
}

@Preview(name = "Peso registro · claro", showBackground = true)
@Preview(
    name = "Peso registro · oscuro",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(
    name = "Peso registro · compacto",
    widthDp = 320,
    heightDp = 900,
    showBackground = true,
)
@Preview(
    name = "Peso registro · texto 200 %",
    widthDp = 320,
    heightDp = 1100,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun WeightRegistrationPreview() {
    BitavidaTheme {
        Surface {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Default),
            ) {
                WeightRegistrationContent(
                    state = WeightUiState(
                        loading = false,
                        draft = WeightDraft(
                            id = "weight-draft-preview",
                            context = WeightRegistrationContext.Independent,
                            actualAt = LocalDateTime.of(2026, 11, 18, 11, 27),
                            kilograms = "74.3",
                        ),
                    ),
                    onAction = {},
                    onSelectDate = {},
                    onSelectTime = {},
                )
            }
        }
    }
}

@Preview(name = "Peso historial · claro", showBackground = true)
@Preview(
    name = "Peso historial · oscuro",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(
    name = "Peso historial · compacto",
    widthDp = 320,
    heightDp = 900,
    showBackground = true,
)
@Preview(
    name = "Peso historial · texto 200 %",
    widthDp = 320,
    heightDp = 1100,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun WeightHistoryPreview() {
    BitavidaTheme {
        Surface {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Default),
            ) {
                GlucoseHistoryContent(
                    records = weightPreviewRecords(),
                    today = weightPreviewToday,
                    initialPeriodKind = GlucoseHistoryPeriodKind.WEEK,
                    measurementKind = MeasurementKind.WEIGHT,
                    unit = "kg",
                    measurementNoun = "peso",
                    referenceValue = BigDecimal("70"),
                    referenceLabel = "Objetivo actual",
                )
            }
        }
    }
}

@Preview(name = "Peso objetivo · claro", showBackground = true)
@Preview(
    name = "Peso objetivo · oscuro",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(
    name = "Peso objetivo · compacto",
    widthDp = 320,
    heightDp = 700,
    showBackground = true,
)
@Preview(
    name = "Peso objetivo · texto 200 %",
    widthDp = 320,
    heightDp = 900,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun WeightTargetSheetPreview() {
    BitavidaTheme {
        WeightTargetSheet(
            state = WeightUiState(
                loading = false,
                target = WeightTarget(BigDecimal("70")),
                targetSheetVisible = true,
                targetDraft = "70",
            ),
            motionAllowed = true,
            onAction = {},
        )
    }
}
