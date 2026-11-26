package com.bitavida.app.ui.appointments

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.domain.model.*
import com.bitavida.app.ui.components.BitavidaDateTimePickerHost
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime

/** Fictitious values confined to previews. */
@Composable
private fun WizardStepPreview(step: Int) {
    val date = LocalDateTime.of(2026, 11, 24, 10, 30)
    val sample = CareEventDraft(
        kind = CareEventKind.APPOINTMENT,
        title = "Consulta de ejemplo",
        status = CareEventStatus.CONFIRMED,
        purpose = CareEventPurpose.MEDICAL_CONSULTATION,
        timeMode = CareEventTimeMode.EXACT,
        scheduledAt = date,
        scheduledDate = date.toLocalDate(),
        duration = CareEventDuration.ONE_HOUR,
        institution = "Unidad de ejemplo",
        specialty = "Medicina familiar",
        preparations = setOf(CareEventPreparation.PREVIOUS_STUDIES),
        reminderEnabled = true,
        reminderOffsetsMinutes = setOf(120),
    )
    BitavidaTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            com.bitavida.app.ui.components.BitavidaFluidBackground(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            ) {
                BitavidaDateTimePickerHost {
                    CareEventWizard(CareEventsState(loading = false, draft = sample),
                        {}, "Persona de ejemplo", {}, initialStep = step)
                }
            }
        }
    }
}

@Preview(name = "Paso 1 claro", showBackground = true)
@Preview(name = "Paso 1 oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Paso 1 compacto", showBackground = true, widthDp = 320)
@Preview(name = "Paso 1 texto 200%", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun WizardStep1Preview() { WizardStepPreview(0) }

@Preview(name = "Paso 2 claro", showBackground = true)
@Preview(name = "Paso 2 oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Paso 2 compacto", showBackground = true, widthDp = 320)
@Preview(name = "Paso 2 texto 200%", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun WizardStep2Preview() { WizardStepPreview(1) }

@Preview(name = "Paso 3 claro", showBackground = true)
@Preview(name = "Paso 3 oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Paso 3 compacto", showBackground = true, widthDp = 320)
@Preview(name = "Paso 3 texto 200%", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun WizardStep3Preview() { WizardStepPreview(2) }

@Preview(name = "Paso 4 claro", showBackground = true)
@Preview(name = "Paso 4 oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Paso 4 compacto", showBackground = true, widthDp = 320)
@Preview(name = "Paso 4 texto 200%", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun WizardStep4Preview() { WizardStepPreview(3) }

@Preview(name = "Paso 5 claro", showBackground = true)
@Preview(name = "Paso 5 oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Paso 5 compacto", showBackground = true, widthDp = 320)
@Preview(name = "Paso 5 texto 200%", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun WizardStep5Preview() { WizardStepPreview(4) }

@Preview(name = "Paso 6 claro", showBackground = true)
@Preview(name = "Paso 6 oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Paso 6 compacto", showBackground = true, widthDp = 320)
@Preview(name = "Paso 6 texto 200%", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun WizardStep6Preview() { WizardStepPreview(5) }

@Preview(name = "Descartar cita claro", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Descartar cita oscuro", showBackground = true, widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Descartar cita compacto", showBackground = true, widthDp = 320, heightDp = 720)
@Preview(name = "Descartar cita texto 200%", showBackground = true, widthDp = 320, heightDp = 720, fontScale = 2f)
@Composable private fun WizardDiscardPreview() {
    BitavidaTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            com.bitavida.app.ui.components.BitavidaFluidBackground(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                BitavidaDateTimePickerHost {
                    val initial = CareEventDraft(kind = CareEventKind.REVIEW_TO_SCHEDULE, title = "", status = CareEventStatus.TO_SCHEDULE)
                    CareEventWizard(
                        CareEventsState(loading = false, initialDraft = initial, draft = initial.copy(title = "Consulta de ejemplo")),
                        {}, "Persona de ejemplo", {},
                    )
                    CareEventDiscardChangesSheet(saving = false, closing = false, motionAllowed = false, onDismiss = {}, onDiscard = {})
                }
            }
        }
    }
}
