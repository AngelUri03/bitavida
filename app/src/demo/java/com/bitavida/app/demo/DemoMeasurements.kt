package com.bitavida.app.demo

import android.content.Context
import com.bitavida.app.data.measurements.EncryptedBloodPressureRepository
import com.bitavida.app.data.measurements.EncryptedGlucoseRepository
import com.bitavida.app.data.measurements.EncryptedWeightRepository
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Lecturas independientes ficticias con identidad estable y captura no posterior
 * a la referencia. Devuelve las identidades previstas para validación de carga.
 */
internal suspend fun seedDemoMeasurements(
    context: Context,
    actor: GlucoseActor,
    reference: DemoReference,
): Set<String> {
    val glucose = EncryptedGlucoseRepository(context)
    val pressure = EncryptedBloodPressureRepository(context)
    val weight = EncryptedWeightRepository(context)
    val glucoseIds = glucose.load(actor).mapTo(mutableSetOf()) { it.id }
    val pressureIds = pressure.load(actor).mapTo(mutableSetOf()) { it.id }
    val weightIds = weight.load(actor).mapTo(mutableSetOf()) { it.id }
    val expected = mutableSetOf<String>()
    var day: LocalDate = reference.firstDate
    var index = 0
    while (!day.isAfter(reference.lastDate)) {
        val dateKey = day.toString()
        suspend fun glucoseAt(hour: Int, moment: GlucoseContext, suffix: String) {
            val captured = day.atTime(hour, 5)
            if (!reference.includesOccurredAt(captured)) return
            val id = "demo-glucose-$dateKey-$suffix"
            expected += id
            if (id !in glucoseIds) {
                val value = 94 + ((index * 7 + hour) % 33)
                val result = SaveGlucoseMeasurementUseCase(glucose, { captured })(actor,
                    GlucoseDraft(id, GlucoseRegistrationContext.Independent, day.atTime(hour, 0),
                        reading = value.toString(), moment = moment))
                check(result is GlucoseSaveResult.Stored)
                glucoseIds += id
            }
        }
        if (index % 7 != 0) {
            glucoseAt(7, GlucoseContext.FASTING, "morning")
            glucoseAt(19, GlucoseContext.AFTER_MEAL, "evening")
        }
        val pressureCaptured = day.atTime(9, 5)
        if (index % 6 != 0 && reference.includesOccurredAt(pressureCaptured)) {
            val id = "demo-pressure-$dateKey"
            expected += id
            if (id !in pressureIds) {
                val result = SaveBloodPressureMeasurementUseCase(pressure, { pressureCaptured })(actor,
                    BloodPressureDraft(id, BloodPressureRegistrationContext.Independent, day.atTime(9, 0),
                        systolic = (116 + index % 12).toString(),
                        diastolic = (73 + index % 8).toString(), moment = GlucoseContext.RESTING))
                check(result is BloodPressureSaveResult.Stored)
                pressureIds += id
            }
        }
        val weightCaptured = day.atTime(8, 5)
        if (index % 4 == 0 && reference.includesOccurredAt(weightCaptured)) {
            val id = "demo-weight-$dateKey"
            expected += id
            if (id !in weightIds) {
                val tenths = 738 + ((index / 4) % 9)
                val result = SaveWeightMeasurementUseCase(weight, { weightCaptured })(actor,
                    WeightDraft(id, WeightRegistrationContext.Independent, day.atTime(8, 0),
                        kilograms = "${tenths / 10}.${tenths % 10}"))
                check(result is WeightSaveResult.Stored)
                weightIds += id
            }
        }
        day = day.plusDays(1)
        index++
    }
    check(glucose.load(actor).map { it.id }.containsAll(expected.filter { it.startsWith("demo-glucose-") }))
    check(pressure.load(actor).map { it.id }.containsAll(expected.filter { it.startsWith("demo-pressure-") }))
    check(weight.load(actor).map { it.id }.containsAll(expected.filter { it.startsWith("demo-weight-") }))
    return expected
}
