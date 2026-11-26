package com.bitavida.app.ui.medical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.bitavida.app.ui.theme.BitavidaSpacing

/** The unchanged consent surface shared by Plan and a quick action from Mi día. */
@Composable
internal fun MedicationSensitiveConsentDialog(
    state: MedicalPlanUiState,
    onAction: (MedicalPlanAction) -> Unit,
) {
    if (state.sensitiveConsentDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                onAction(MedicalPlanAction.DeclineSensitiveConsent)
            },
            title = { Text("Guardar información de salud") },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    Text(
                        "Bitavida guardará localmente y de forma cifrada los datos de este medicamento para organizar el plan de cuidado.",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAction(
                                    MedicalPlanAction.SensitiveConsentChanged(
                                        !state.sensitiveConsentAccepted,
                                    ),
                                )
                            }
                            .semantics { },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = state.sensitiveConsentAccepted,
                            onCheckedChange = {
                                onAction(
                                    MedicalPlanAction.SensitiveConsentChanged(
                                        it,
                                    ),
                                )
                            },
                        )
                        Text(
                            "Acepto guardar esta información sanitaria en este dispositivo.",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        "Esta aceptación no declara representación legal ni comparte datos por Internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(MedicalPlanAction.ConfirmSensitiveConsent)
                    },
                    enabled = state.sensitiveConsentAccepted &&
                        !state.isSaving,
                    modifier = Modifier.heightIn(
                        min = BitavidaSpacing.MinimumTouchTarget,
                    ),
                ) {
                    Text(if (state.isSaving) "Guardando…" else "Aceptar y guardar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onAction(MedicalPlanAction.DeclineSensitiveConsent)
                    },
                    enabled = !state.isSaving,
                    modifier = Modifier.heightIn(
                        min = BitavidaSpacing.MinimumTouchTarget,
                    ),
                ) {
                    Text("No guardar")
                }
            },
        )
    }
}
