package com.bitavida.app.ui.medical

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.bitavida.app.domain.validation.MedicationValueRules
import com.bitavida.app.ui.theme.BitavidaSpacing

@Composable
internal fun MedicationValueField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    var editor by remember { mutableStateOf(TextFieldValue(value)) }
    var rejectedInput by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (editor.text != value) {
            editor = TextFieldValue(value, TextRange(value.length))
            rejectedInput = false
        }
    }

    fun accept(proposed: TextFieldValue) {
        // Selection changes must also work for a long historical value.
        if (proposed.text == editor.text) {
            editor = proposed
        } else if (MedicationValueRules.acceptsInput(proposed.text)) {
            editor = proposed
            rejectedInput = false
            onValueChange(proposed.text)
        } else {
            rejectedInput = true
        }
    }

    OutlinedTextField(
        value = editor,
        onValueChange = ::accept,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = rejectedInput || isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        trailingIcon = {
            TextButton(
                onClick = {
                    val selection = editor.selection
                    val inserted = editor.text.replaceRange(
                        selection.min, selection.max, "/",
                    )
                    accept(
                        TextFieldValue(
                            inserted,
                            TextRange(selection.min + 1),
                        ),
                    )
                },
                modifier = Modifier
                    .sizeIn(
                        minWidth = BitavidaSpacing.MinimumTouchTarget,
                        minHeight = BitavidaSpacing.MinimumTouchTarget,
                    )
                    .semantics {
                        contentDescription = "Insertar barra para una fracción"
                    },
            ) {
                Text("/")
            }
        },
        supportingText = {
            Text(
                text = when {
                    rejectedInput -> "Hasta 8 caracteres: solo 0–9 y una /."
                    isError -> "Escribe un entero o una fracción con ambos valores mayores que cero."
                    else -> "Hasta 8 caracteres. Ejemplos: 2 o 1/2."
                } + if (rejectedInput || isError || value.length >= 7) {
                    " · ${value.length}/${MedicationValueRules.MAX_LENGTH}"
                } else "",

                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
            )
        },
    )
}
