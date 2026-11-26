package com.bitavida.app.ui.measurements

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.math.BigDecimal

@Composable
internal fun WeightTargetSheet(
    state: WeightUiState,
    motionAllowed: Boolean,
    onAction: (WeightAction) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val duration = if (motionAllowed) BitavidaFluidMotion.Selection else 0
    val borderColor by animateColorAsState(
        targetValue = when {
            state.targetError != null -> MaterialTheme.colorScheme.error
            focused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(duration),
        label = "weight target field border",
    )
    val focusManager = LocalFocusManager.current
    val parsedValue = state.targetDraft.toBigDecimalOrNull()
    val canSave = (parsedValue != null) &&
        (parsedValue > BigDecimal.ZERO) &&
        !state.targetSaving

    MedicationLifecycleSheetFrame(
        medication = null,
        title = "Configurar peso objetivo",
        subtitle = "Referencia indicada por el médico",
        saving = state.targetSaving,
        motionAllowed = motionAllowed,
        onDismiss = { onAction(WeightAction.DismissTarget) },
    ) {
        SecondaryText(
            "Registra el valor transcrito por la persona cuidadora. Es una referencia visual opcional.",
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = BitavidaSpacing.Medium,
                        vertical = BitavidaSpacing.Small,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                BasicTextField(
                    value = state.targetDraft,
                    onValueChange = {
                        onAction(WeightAction.TargetKilograms(it))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "Peso objetivo en kilogramos"
                        },
                    enabled = !state.targetSaving,
                    interactionSource = interaction,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (canSave) {
                                onAction(WeightAction.SaveTarget)
                            }
                        },
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (state.targetDraft.isEmpty()) {
                                Text(
                                    "Peso objetivo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Text(
                    "kg",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.targetError?.let { CaptureError(it) }
        MedicationLifecyclePrimaryAction(
            label = if (state.target == null) {
                "Guardar objetivo"
            } else {
                "Guardar cambios"
            },
            icon = R.drawable.ic_measurement_weight,
            enabled = canSave,
            saving = state.targetSaving,
            destructive = false,
            motionAllowed = motionAllowed,
        ) {
            focusManager.clearFocus()
            onAction(WeightAction.SaveTarget)
        }
        MedicationLifecycleCancelAction(
            enabled = !state.targetSaving,
            label = if (state.target == null) {
                "Cancelar"
            } else {
                "Quitar peso objetivo"
            },
        ) {
            if (state.target == null) {
                onAction(WeightAction.DismissTarget)
            } else {
                onAction(WeightAction.RemoveTarget)
            }
        }
        WeightConsentDialog(state, onAction)
    }
}
