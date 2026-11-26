package com.bitavida.app.ui.observations

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bitavida.app.R
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.domain.usecase.fitsObservationTextCapture
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame

/** Uses the approved lifecycle sheet; deletion appends a REMOVED revision and preserves history. */
@Composable
internal fun ObservationSuspensionSheet(
    state: ObservationsUiState,
    motionAllowed: Boolean,
    onAction: (ObservationsAction) -> Unit,
) {
    MedicationLifecycleSheetFrame(
        medication = null,
        title = "¿Eliminar seguimiento?",
        saving = state.saving,
        motionAllowed = motionAllowed,
        onDismiss = { onAction(ObservationsAction.DismissSuspend) },
    ) {
        ObservationSuspensionContent(
            reason = state.suspensionReason,
            error = state.suspensionError,
            saving = state.saving,
            motionAllowed = motionAllowed,
            onReasonChange = { onAction(ObservationsAction.SuspendReason(it)) },
            onConfirm = { onAction(ObservationsAction.ConfirmSuspend) },
            onDismiss = { onAction(ObservationsAction.DismissSuspend) },
        )
    }
}

@Composable
internal fun ColumnScope.ObservationSuspensionContent(
    reason: String,
    error: String?,
    saving: Boolean,
    motionAllowed: Boolean,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Text(
        "Se eliminarán las próximas revisiones y avisos. Tus observaciones e historial permanecerán guardados.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BitavidaLimitedTextField(
        value = reason,
        limit = CaptureLimit.CHANGE_REASON,
        additionalInputValidation = { fitsObservationTextCapture(it, Int.MAX_VALUE, reason) },
        onValueChange = { if (!saving) onReasonChange(it) },
        label = { Text("¿Por qué deseas eliminarlo?") },
        placeholder = { Text("Escribe un motivo breve") },
        supportingText = { Text(error ?: "Obligatorio") },
        isError = error != null,
        minLines = 2,
        maxLines = 5,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("observation_delete_reason"),
    )
    MedicationLifecyclePrimaryAction(
        label = "Eliminar seguimiento",
        icon = R.drawable.ic_medication_delete,
        enabled = reason.isNotBlank() && !saving,
        saving = saving,
        destructive = true,
        motionAllowed = motionAllowed,
        onClick = onConfirm,
    )
    MedicationLifecycleCancelAction(
        enabled = !saving,
        onClick = onDismiss,
    )
}
