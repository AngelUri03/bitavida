package com.bitavida.app.demo

import android.content.Context
import com.bitavida.app.data.measurements.EncryptedBloodPressureRepository
import com.bitavida.app.data.measurements.EncryptedGlucoseRepository
import com.bitavida.app.data.measurements.EncryptedWeightRepository
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.*
import java.time.LocalTime

/** Recurring examples preserve the actual relationship between a schedule and its response. */
internal suspend fun seedDemoMeasurementFollowUps(
    context: Context,
    profile: CareProfile,
    reference: DemoReference,
): Set<String> {
    require(context.packageName == "com.bitavida.app.demo")
    val actor = GlucoseActor(profile.id, profile.caregiver.id, profile.personUnderCare.id, true)
    val glucose = EncryptedGlucoseRepository(context)
    val pressure = EncryptedBloodPressureRepository(context)
    val weight = EncryptedWeightRepository(context)
    val start = reference.firstDate
    fun draft(id: String, time: LocalTime, moment: GlucoseContext) = GlucoseFollowUpDraft(
        followUpId = id, indication = "Seguimiento ficticio para demostración.",
        frequency = GlucoseFollowUpFrequency.DAILY, effectiveFrom = start, remindersEnabled = false,
        slots = listOf(GlucoseFollowUpSlotDraft(id = "$id-slot", timingType = GlucoseFollowUpTimingType.EXACT_TIME,
            time = time, context = moment, reminderTime = time, useSameTimeForReminder = true)),
    )
    if (glucose.loadFollowUp(actor) == null) {
        check(SaveGlucoseFollowUpUseCase(glucose, { start.atTime(6, 0) }, { "demo-followup-glucose-r1" })(
            actor, draft("demo-followup-glucose", LocalTime.of(7, 0), GlucoseContext.FASTING)) is GlucoseFollowUpSaveResult.Stored)
    }
    if (pressure.loadFollowUp(actor) == null) {
        check(SaveBloodPressureFollowUpUseCase(pressure, { start.atTime(6, 0) }, { "demo-followup-pressure-r1" })(
            actor, draft("demo-followup-pressure", LocalTime.of(8, 0), GlucoseContext.RESTING)) is GlucoseFollowUpSaveResult.Stored)
    }
    if (weight.loadFollowUp(actor) == null) {
        check(SaveWeightFollowUpUseCase(weight, { start.atTime(6, 0) }, { "demo-followup-weight-r1" })(
            actor, draft("demo-followup-weight", LocalTime.of(9, 0), GlucoseContext.UNSPECIFIED)) is GlucoseFollowUpSaveResult.Stored)
    }
    val g = requireNotNull(glucose.loadFollowUp(actor))
    val p = requireNotNull(pressure.loadFollowUp(actor))
    val w = requireNotNull(weight.loadFollowUp(actor))
    check(g.id == "demo-followup-glucose" && p.id == "demo-followup-pressure" && w.id == "demo-followup-weight")
    val doneDate = start.plusDays(2)
    val missedDate = start.plusDays(3)
    val go = g.currentRevision.declaredOccurrencesOn(doneDate).single()
    val po = p.currentRevision.declaredOccurrencesOn(doneDate).single()
    val wo = w.currentRevision.declaredOccurrencesOn(doneDate).single()
    check(reference.includesOccurredAt(wo.scheduledAt.plusMinutes(5)))
    if (glucose.load(actor).none { it.id == "demo-followup-glucose-reading" }) {
        check(SaveGlucoseMeasurementUseCase(glucose, { go.scheduledAt.plusMinutes(5) })(actor,
            GlucoseDraft("demo-followup-glucose-reading", GlucoseRegistrationContext.Scheduled(
                ConfirmedGlucoseOccurrence(go.id, go.followUpId, go.scheduledAt, go.suggestedContext)),
                go.scheduledAt, reading = "108", moment = GlucoseContext.FASTING)) is GlucoseSaveResult.Stored)
    }
    if (pressure.load(actor).none { it.id == "demo-followup-pressure-reading" }) {
        check(SaveBloodPressureMeasurementUseCase(pressure, { po.scheduledAt.plusMinutes(5) })(actor,
            BloodPressureDraft("demo-followup-pressure-reading", BloodPressureRegistrationContext.Scheduled(
                ConfirmedBloodPressureOccurrence(po.id, po.followUpId, po.scheduledAt, po.suggestedContext)),
                po.scheduledAt, systolic = "120", diastolic = "78", moment = GlucoseContext.RESTING)) is BloodPressureSaveResult.Stored)
    }
    if (weight.load(actor).none { it.id == "demo-followup-weight-reading" }) {
        check(SaveWeightMeasurementUseCase(weight, { wo.scheduledAt.plusMinutes(5) })(actor,
            WeightDraft("demo-followup-weight-reading", WeightRegistrationContext.Scheduled(
                ConfirmedWeightOccurrence(wo.id, wo.followUpId, wo.scheduledAt, wo.suggestedContext)),
                wo.scheduledAt, kilograms = "73.8")) is WeightSaveResult.Stored)
    }
    val gm = g.currentRevision.declaredOccurrencesOn(missedDate).single()
    val pm = p.currentRevision.declaredOccurrencesOn(missedDate).single()
    val wm = w.currentRevision.declaredOccurrencesOn(missedDate).single()
    if (glucose.loadOccurrenceDecisions(actor, g.id).none { it.occurrenceId == gm.id })
        ConfirmGlucoseNotPerformedUseCase(glucose, { gm.scheduledAt.plusMinutes(5) }, { "demo-followup-glucose-missed" })(actor, gm, "No realizada; ejemplo ficticio.")
    if (pressure.loadOccurrenceDecisions(actor, p.id).none { it.occurrenceId == pm.id })
        ConfirmBloodPressureNotPerformedUseCase(pressure, { pm.scheduledAt.plusMinutes(5) }, { "demo-followup-pressure-missed" })(actor, pm, "No realizada; ejemplo ficticio.")
    if (weight.loadOccurrenceDecisions(actor, w.id).none { it.occurrenceId == wm.id })
        ConfirmWeightNotPerformedUseCase(weight, { wm.scheduledAt.plusMinutes(5) }, { "demo-followup-weight-missed" })(actor, wm, "No realizada; ejemplo ficticio.")
    val ids = setOf(g.id, p.id, w.id, "demo-followup-glucose-reading", "demo-followup-pressure-reading",
        "demo-followup-weight-reading", "demo-followup-glucose-missed", "demo-followup-pressure-missed",
        "demo-followup-weight-missed")
    check(glucose.load(actor).any { it.id == "demo-followup-glucose-reading" })
    check(pressure.load(actor).any { it.id == "demo-followup-pressure-reading" })
    check(weight.load(actor).any { it.id == "demo-followup-weight-reading" })
    check(glucose.loadOccurrenceDecisions(actor, g.id).any { it.id == "demo-followup-glucose-missed" })
    check(pressure.loadOccurrenceDecisions(actor, p.id).any { it.id == "demo-followup-pressure-missed" })
    check(weight.loadOccurrenceDecisions(actor, w.id).any { it.id == "demo-followup-weight-missed" })
    return ids
}
