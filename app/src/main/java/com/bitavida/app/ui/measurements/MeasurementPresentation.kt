package com.bitavida.app.ui.measurements

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.usecase.bodyMassIndex
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val measurementLocale: Locale = Locale.forLanguageTag("es-MX")
internal val measurementDateTime: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM uuuu · HH:mm", measurementLocale)
internal val measurementFullDate: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' uuuu", measurementLocale)
internal val measurementShortDate: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", measurementLocale)
internal val measurementWeekday: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE", measurementLocale)

internal fun MeasurementKind.label(): String = when (this) {
    MeasurementKind.CAPILLARY_GLUCOSE -> "Glucosa capilar"
    MeasurementKind.BLOOD_PRESSURE -> "Presión arterial"
    MeasurementKind.WEIGHT -> "Peso"
}

internal fun MeasurementKind.unit(): String = when (this) {
    MeasurementKind.CAPILLARY_GLUCOSE -> "mg/dL"
    MeasurementKind.BLOOD_PRESSURE -> "mmHg"
    MeasurementKind.WEIGHT -> "kg"
}

internal fun MeasurementKind.icon(): Int = when (this) {
    MeasurementKind.CAPILLARY_GLUCOSE -> R.drawable.ic_v2_droplet
    MeasurementKind.BLOOD_PRESSURE -> R.drawable.ic_measurement_pressure
    MeasurementKind.WEIGHT -> R.drawable.ic_measurement_weight
}

internal fun MeasurementRecord.valueLabel(): String = when (val value = value) {
    is MeasurementValue.Glucose -> value.reading
    is MeasurementValue.BloodPressure -> "${value.systolic} / ${value.diastolic}"
    is MeasurementValue.Weight -> value.kilograms.toPlainString()
}

internal fun MeasurementRecord.complement(): String? = when (val value = value) {
    is MeasurementValue.Glucose -> when (value.context) {
        GlucoseContext.FASTING -> "En ayuno"
        GlucoseContext.BEFORE_MEAL -> "Antes de comer"
        GlucoseContext.AFTER_MEAL -> "Después de comer"
        GlucoseContext.BEFORE_SLEEP -> "Antes de dormir"
        GlucoseContext.OTHER -> "Otro momento"
        GlucoseContext.RESTING -> "En reposo"
        GlucoseContext.AFTER_PHYSICAL_ACTIVITY ->
            "Después de actividad física"
        GlucoseContext.UPON_WAKING -> "Al despertar"
        GlucoseContext.UNSPECIFIED -> null
        null -> null
    }
    is MeasurementValue.BloodPressure -> value.pulse?.takeIf { it.isNotBlank() }?.let { "Pulso: $it lpm" }
    is MeasurementValue.Weight -> value.bodyMassIndex()?.let { "IMC: ${it.toPlainString()}" }
}

@Composable
internal fun MeasurementReading(
    record: MeasurementRecord,
    prominent: Boolean = false,
    compact: Boolean = false,
) {
    val unitStyle = MaterialTheme.typography.bodyMedium
    val accessible = when (val value = record.value) {
        is MeasurementValue.BloodPressure ->
            "Sistólica: ${value.systolic}, diastólica: ${value.diastolic}, milímetros de mercurio"
        else -> "${record.valueLabel()} ${record.kind.unit()}"
    }
    Text(
        text = buildAnnotatedString {
            append(record.valueLabel())
            withStyle(SpanStyle(fontSize = unitStyle.fontSize, fontWeight = unitStyle.fontWeight)) {
                append(" ${record.kind.unit()}")
            }
        },
        modifier = Modifier.semantics { contentDescription = accessible },
        style = when {
            prominent -> MaterialTheme.typography.displaySmall
            compact -> MaterialTheme.typography.titleLarge
            else -> MaterialTheme.typography.headlineSmall
        },
        color = MaterialTheme.colorScheme.onSurface,
    )
}
