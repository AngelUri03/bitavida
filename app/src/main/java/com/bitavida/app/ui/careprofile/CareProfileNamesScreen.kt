package com.bitavida.app.ui.careprofile

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.validation.CARE_PROFILE_NAME_MAX_LENGTH
import com.bitavida.app.domain.validation.CareProfileField
import com.bitavida.app.domain.validation.CareProfileValidationError
import com.bitavida.app.domain.validation.CareProfileValidator
import com.bitavida.app.ui.demo.TodayTypography
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.theme.BitavidaTheme

@Composable
fun CareProfileNamesScreen(
    state: CareProfileNamesState,
    onCaregiverChange: (String) -> Unit,
    onPersonChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) = TodayTypography {
    BackHandler(enabled = !state.saving, onBack = onBack)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !state.saving, modifier = Modifier.size(48.dp)) {
                    MeasurementIcon(R.drawable.ic_v2_arrow_back, 24, MaterialTheme.colorScheme.primary)
                }
                Text("Datos personales", Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            Text("Modifica los nombres sin cambiar los registros de cuidado.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            NameInput("Nombre del cuidador", state.caregiverName, state.errors[CareProfileField.CAREGIVER_NAME],
                !state.saving, onCaregiverChange)
            NameInput("Nombre de la persona acompañada", state.personName, state.errors[CareProfileField.PERSON_UNDER_CARE_NAME],
                !state.saving, onPersonChange)
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onSave, enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Text(if (state.saving) "Guardando…" else "Guardar cambios", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun NameInput(label: String, value: String, error: CareProfileValidationError?,
    enabled: Boolean, onChange: (String) -> Unit) {
    val message = when (error) {
        CareProfileValidationError.REQUIRED -> "Escribe un nombre para continuar."
        CareProfileValidationError.TOO_SHORT -> "Escribe al menos 2 letras."
        CareProfileValidationError.TOO_LONG -> "Usa un máximo de 80 caracteres."
        CareProfileValidationError.INVALID_CHARACTERS -> "Usa letras, espacios, apóstrofos o guiones."
        CareProfileValidationError.CONTROL_CHARACTERS_NOT_ALLOWED -> "El nombre no puede incluir saltos de línea ni caracteres invisibles."
        CareProfileValidationError.AUTHORIZATION_REQUIRED -> "Confirma la autorización."
        null -> null
    }
    OutlinedTextField(value = value, onValueChange = onChange, enabled = enabled,
        modifier = Modifier.fillMaxWidth(), label = { Text(label) }, isError = error != null,
        supportingText = {
            Column {
                if (message != null) Text(message, color = MaterialTheme.colorScheme.error)
                Text("${CareProfileValidator.nameLength(value)} / $CARE_PROFILE_NAME_MAX_LENGTH",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        textStyle = MaterialTheme.typography.bodyLarge)
}

@Preview(showBackground = true)
@Composable private fun NamesLightPreview() {
    BitavidaTheme { CareProfileNamesScreen(CareProfileNamesState(open = true, caregiverName = "Ana", personName = "María"), {}, {}, {}, {}) }
}
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun NamesDarkPreview() {
    BitavidaTheme(darkTheme = true) { CareProfileNamesScreen(CareProfileNamesState(open = true, caregiverName = "Ana", personName = "María"), {}, {}, {}, {}) }
}
@Preview(showBackground = true, widthDp = 320)
@Composable private fun NamesCompactPreview() {
    BitavidaTheme { CareProfileNamesScreen(CareProfileNamesState(open = true, caregiverName = "Ana", personName = "María"), {}, {}, {}, {}) }
}
@Preview(showBackground = true, fontScale = 2f)
@Composable private fun NamesLargeTextPreview() {
    BitavidaTheme { CareProfileNamesScreen(CareProfileNamesState(open = true, caregiverName = "Ana", personName = "María"), {}, {}, {}, {}) }
}
