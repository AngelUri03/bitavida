package com.bitavida.app.ui.medical

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.model.MedicalIndicationStatus
import com.bitavida.app.domain.model.MedicationActivityRelation
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.model.groupByMedicationIdentity
import com.bitavida.app.domain.model.pendingReactivation
import com.bitavida.app.domain.model.treatmentStatusAt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

internal enum class MedicationPlanDisplayState(
    val groupTitle: String,
    val statusLabel: String,
) {
    UPCOMING("Por iniciar", "Por iniciar"),
    CURRENT("Vigentes", "Vigente"),
    SUSPENDED("Suspendidos", "Suspendido"),
    COMPLETED("Finalizados", "Finalizado"),
    REMOVED("Retirados", "Retirado"),
    OUTSIDE_RECORDED_PERIOD(
        "Fuera de vigencia registrada",
        "Fuera de vigencia registrada",
    ),
}

internal fun currentMedicationPeriodsForList(
    plans: List<MedicationPlan>,
    at: LocalDateTime,
    indicationStatus: (MedicationPlan) -> MedicalIndicationStatus?,
): List<Pair<MedicationPlan, MedicationPlanDisplayState>> =
    plans.groupByMedicationIdentity().mapNotNull { identityPeriods ->
        identityPeriods.mapNotNull { plan ->
            if (plan.contentMode != com.bitavida.app.domain.model.ClinicalContentMode.REAL_CLINICAL_DATA) {
                return@mapNotNull null
            }
            val state = plan.displayState(at, indicationStatus(plan))
            if (state !in setOf(
                    MedicationPlanDisplayState.CURRENT,
                    MedicationPlanDisplayState.UPCOMING,
                    MedicationPlanDisplayState.SUSPENDED,
                    MedicationPlanDisplayState.COMPLETED,
                )
            ) {
                return@mapNotNull null
            }
            plan to state
        }.maxWithOrNull(
            compareBy<Pair<MedicationPlan, MedicationPlanDisplayState>> {
                when (it.second) {
                    MedicationPlanDisplayState.CURRENT -> 4
                    MedicationPlanDisplayState.SUSPENDED -> 3
                    MedicationPlanDisplayState.UPCOMING -> 2
                    MedicationPlanDisplayState.COMPLETED -> 1
                    else -> 0
                }
            }.thenBy {
                it.first.currentRevision.configuration?.startsOn
                    ?: it.first.currentRevision.startsOn
            }.thenBy { it.first.createdAtEpochMillis },
        )
    }

internal fun MedicationPlan.displayState(
    onDate: LocalDate,
    indicationStatus: MedicalIndicationStatus?,
): MedicationPlanDisplayState =
    displayState(onDate.atStartOfDay(), indicationStatus)

internal fun MedicationPlan.displayState(
    at: LocalDateTime,
    indicationStatus: MedicalIndicationStatus?,
): MedicationPlanDisplayState {
    val revision = currentRevision
    val zoneId = ZoneId.systemDefault()
    val atEpochMillis = at.atZone(zoneId).toInstant().toEpochMilli()
    val effectiveStatus = treatmentStatusAt(atEpochMillis, zoneId)
    val scheduledReactivation = pendingReactivation(atEpochMillis, zoneId)
    return when {
        indicationStatus == MedicalIndicationStatus.SUSPENDED ->
            MedicationPlanDisplayState.SUSPENDED
        effectiveStatus == MedicationTreatmentStatus.SUSPENDED &&
            scheduledReactivation != null ->
            MedicationPlanDisplayState.UPCOMING
        effectiveStatus == MedicationTreatmentStatus.SUSPENDED ->
            MedicationPlanDisplayState.SUSPENDED
        effectiveStatus == MedicationTreatmentStatus.COMPLETED ||
            indicationStatus == MedicalIndicationStatus.COMPLETED ->
            MedicationPlanDisplayState.COMPLETED
        effectiveStatus == MedicationTreatmentStatus.REMOVED ->
            MedicationPlanDisplayState.REMOVED
        revision.startsOn.isAfter(at.toLocalDate()) ->
            MedicationPlanDisplayState.UPCOMING
        revision.endsOn?.isBefore(at.toLocalDate()) == true ->
            MedicationPlanDisplayState.COMPLETED
        else -> MedicationPlanDisplayState.CURRENT
    }
}

internal fun String?.medicationPresentationDisplayName(): String? {
    val stored = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return MedicationProductForm.entries
        .singleOrNull { it.name == stored }
        ?.displayName()
        ?: stored
}

internal fun MedicationProductForm.displayName(): String = when (this) {
    MedicationProductForm.TABLET -> "Tableta"
    MedicationProductForm.CAPSULE -> "Cápsula"
    MedicationProductForm.ORAL_LIQUID -> "Jarabe o solución"
    MedicationProductForm.DROPS -> "Gotas"
    MedicationProductForm.INJECTABLE -> "Inyectable"
    MedicationProductForm.INHALER -> "Inhalador"
    MedicationProductForm.TOPICAL -> "Crema o ungüento"
    MedicationProductForm.PATCH -> "Parche"
    MedicationProductForm.POWDER -> "Polvo o sobres"
    MedicationProductForm.OTHER -> "Otra presentación"
}

internal fun MedicationConfiguration.frequencySummary(): String = when (frequency) {
    MedicationFrequencyKind.EVERY_DAY -> "Todos los días"
    MedicationFrequencyKind.SPECIFIC_DAYS ->
        weekdays.sortedBy(DayOfWeek::getValue)
            .map(DayOfWeek::displayName)
            .toNaturalLanguageList()
            .ifBlank { "Días específicos" }
    MedicationFrequencyKind.EVERY_N_DAYS ->
        everyNDaysInterval?.let { "Cada $it días" }
            ?: "Cada cierto número de días"
    MedicationFrequencyKind.EVERY_N_HOURS ->
        hourlyInterval?.let { "Cada $it horas" }
            ?: "Cada cierto número de horas"
    MedicationFrequencyKind.CYCLE -> cycle?.let {
        "Por ciclos: ${it.activeDays} días activos y ${it.restDays} de descanso"
    } ?: "Por ciclos"
    MedicationFrequencyKind.AS_NEEDED -> "Cuando sea necesario"
}

internal fun MedicationActivityRelation.displayName(
    customActivity: String?,
): String = when (this) {
    MedicationActivityRelation.BEFORE_BREAKFAST -> "Antes del desayuno"
    MedicationActivityRelation.WITH_BREAKFAST -> "Con el desayuno"
    MedicationActivityRelation.AFTER_BREAKFAST -> "Después del desayuno"
    MedicationActivityRelation.BEFORE_LUNCH -> "Antes de la comida"
    MedicationActivityRelation.WITH_LUNCH -> "Con la comida"
    MedicationActivityRelation.AFTER_LUNCH -> "Después de la comida"
    MedicationActivityRelation.BEFORE_DINNER -> "Antes de la cena"
    MedicationActivityRelation.WITH_DINNER -> "Con la cena"
    MedicationActivityRelation.AFTER_DINNER -> "Después de la cena"
    MedicationActivityRelation.BEFORE_SLEEP -> "Antes de dormir"
    MedicationActivityRelation.OTHER ->
        customActivity?.takeIf(String::isNotBlank) ?: "Otra actividad"
}

private fun DayOfWeek.displayName(): String = when (this) {
    DayOfWeek.MONDAY -> "Lunes"
    DayOfWeek.TUESDAY -> "martes"
    DayOfWeek.WEDNESDAY -> "miércoles"
    DayOfWeek.THURSDAY -> "jueves"
    DayOfWeek.FRIDAY -> "viernes"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}

private fun List<String>.toNaturalLanguageList(): String = when (size) {
    0 -> ""
    1 -> first()
    else -> dropLast(1).joinToString(", ") + " y " + last()
}

@Composable
internal fun MedicationProductIcon(
    form: MedicationProductForm,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val stroke = 3.dp.toPx()
        val left = size.width * 0.18f
        val top = size.height * 0.2f
        val right = size.width * 0.82f
        val bottom = size.height * 0.8f
        when (form) {
            MedicationProductForm.TABLET -> drawRoundRect(
                color = color,
                topLeft = Offset(left, size.height * 0.34f),
                size = Size(right - left, size.height * 0.32f),
                cornerRadius = CornerRadius(size.height * 0.16f),
                style = Stroke(stroke),
            )
            MedicationProductForm.CAPSULE -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, size.height * 0.34f),
                    size = Size(right - left, size.height * 0.32f),
                    cornerRadius = CornerRadius(size.height * 0.16f),
                    style = Stroke(stroke),
                )
                drawLine(
                    color,
                    Offset(size.width / 2f, size.height * 0.34f),
                    Offset(size.width / 2f, size.height * 0.66f),
                    stroke,
                )
            }
            MedicationProductForm.ORAL_LIQUID -> {
                drawRoundRect(
                    color,
                    Offset(size.width * 0.3f, size.height * 0.3f),
                    Size(size.width * 0.4f, size.height * 0.52f),
                    CornerRadius(4.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawLine(
                    color,
                    Offset(size.width * 0.38f, size.height * 0.23f),
                    Offset(size.width * 0.62f, size.height * 0.23f),
                    stroke,
                )
            }
            MedicationProductForm.DROPS -> {
                val path = Path().apply {
                    moveTo(size.width / 2f, top)
                    cubicTo(
                        right,
                        size.height * 0.5f,
                        right,
                        bottom,
                        size.width / 2f,
                        bottom,
                    )
                    cubicTo(
                        left,
                        bottom,
                        left,
                        size.height * 0.5f,
                        size.width / 2f,
                        top,
                    )
                    close()
                }
                drawPath(path, color, style = Stroke(stroke))
            }
            MedicationProductForm.INJECTABLE -> {
                drawLine(color, Offset(left, bottom), Offset(right, top), stroke)
                drawLine(
                    color,
                    Offset(size.width * 0.25f, size.height * 0.68f),
                    Offset(size.width * 0.38f, size.height * 0.81f),
                    stroke,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.58f, size.height * 0.3f),
                    Offset(size.width * 0.72f, size.height * 0.44f),
                    stroke,
                )
            }
            MedicationProductForm.INHALER -> {
                drawRoundRect(
                    color,
                    Offset(size.width * 0.38f, top),
                    Size(size.width * 0.32f, size.height * 0.58f),
                    CornerRadius(4.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawLine(
                    color,
                    Offset(size.width * 0.38f, bottom),
                    Offset(size.width * 0.2f, bottom),
                    stroke,
                )
            }
            MedicationProductForm.TOPICAL -> {
                val path = Path().apply {
                    moveTo(size.width * 0.38f, top)
                    lineTo(size.width * 0.62f, top)
                    lineTo(right, bottom)
                    lineTo(left, bottom)
                    close()
                }
                drawPath(path, color, style = Stroke(stroke))
            }
            MedicationProductForm.PATCH -> drawRoundRect(
                color,
                Offset(size.width * 0.24f, size.height * 0.24f),
                Size(size.width * 0.52f, size.height * 0.52f),
                CornerRadius(5.dp.toPx()),
                style = Stroke(stroke),
            )
            MedicationProductForm.POWDER -> {
                drawRoundRect(
                    color,
                    Offset(size.width * 0.24f, top),
                    Size(size.width * 0.52f, bottom - top),
                    CornerRadius(3.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawLine(
                    color,
                    Offset(size.width * 0.32f, size.height * 0.34f),
                    Offset(size.width * 0.68f, size.height * 0.34f),
                    stroke,
                )
            }
            MedicationProductForm.OTHER -> {
                drawCircle(
                    color,
                    radius = size.minDimension * 0.3f,
                    style = Stroke(stroke),
                )
                drawLine(
                    color,
                    Offset(size.width / 2f, size.height * 0.34f),
                    Offset(size.width / 2f, size.height * 0.66f),
                    stroke,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.34f, size.height / 2f),
                    Offset(size.width * 0.66f, size.height / 2f),
                    stroke,
                )
            }
        }
    }
}
