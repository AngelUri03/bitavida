package com.bitavida.app.ui.observations

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bitavida.app.R
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.domain.usecase.fitsObservationTextCapture
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.measurements.SecondaryText

@Composable
internal fun ObservationDescriptionSheet(
    state: ObservationsUiState,
    motionAllowed: Boolean,
    onAction: (ObservationsAction) -> Unit,
) {
    val proposal = state.descriptionProposal ?: return
    MedicationLifecycleSheetFrame(
        medication = null,
        title = "Ayúdame a describirlo",
        saving = state.saving,
        motionAllowed = motionAllowed,
        onDismiss = { onAction(ObservationsAction.DismissDescription) },
    ) {
        ObservationDescriptionSheetContent(
            proposal = proposal,
            enabled = !state.saving,
            motionAllowed = motionAllowed,
            onChange = { onAction(ObservationsAction.DescriptionProposal(it)) },
            onApply = { onAction(ObservationsAction.ApplyDescription) },
            onDismiss = { onAction(ObservationsAction.DismissDescription) },
        )
    }
}

@Composable
private fun ColumnScope.ObservationDescriptionSheetContent(
    proposal: String,
    enabled: Boolean,
    motionAllowed: Boolean,
    onChange: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    SecondaryText("La propuesta usa solamente las respuestas que seleccionaste. Revísala antes de usarla.")
    BitavidaLimitedTextField(
        value = proposal,
        onValueChange = { if (enabled) onChange(it) },
        limit = CaptureLimit.OBSERVATION_DESCRIPTION,
        additionalInputValidation = { fitsObservationTextCapture(it, Int.MAX_VALUE, proposal) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Descripción propuesta") },
        minLines = 3,
        maxLines = 7,
        shape = MaterialTheme.shapes.medium,
    )
    MedicationLifecyclePrimaryAction(
        label = "Usar descripción",
        icon = R.drawable.ic_v2_edit,
        enabled = enabled && proposal.isNotBlank() &&
            fitsObservationTextCapture(proposal, CaptureLimit.OBSERVATION_DESCRIPTION.maximum),
        saving = false,
        destructive = false,
        motionAllowed = motionAllowed,
        onClick = onApply,
    )
    MedicationLifecycleCancelAction(enabled = enabled, onClick = onDismiss)
}
