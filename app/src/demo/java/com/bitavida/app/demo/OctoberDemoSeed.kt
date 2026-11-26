package com.bitavida.app.demo

import android.content.Context
import com.bitavida.app.data.appointments.EncryptedCareEventRepository
import com.bitavida.app.data.careprofile.EncryptedCareProfileRepository
import com.bitavida.app.data.medical.EncryptedMedicalCareRepository
import com.bitavida.app.data.measurements.EncryptedBloodPressureRepository
import com.bitavida.app.data.measurements.EncryptedGlucoseRepository
import com.bitavida.app.data.measurements.EncryptedWeightRepository
import com.bitavida.app.data.observations.EncryptedObservationRepository
import com.bitavida.app.data.notes.EncryptedNoteRepository
import com.bitavida.app.data.reminders.EncryptedCareReminderRepository
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.CareEventActor
import com.bitavida.app.domain.repository.CareEventCommand
import com.bitavida.app.domain.repository.GLUCOSE_SENSITIVE_CONSENT_VERSION
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.NoteActor
import com.bitavida.app.domain.repository.CareReminderActor
import com.bitavida.app.domain.usecase.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val demoZone = ZoneId.of("America/Mexico_City")
private val october = LocalDate.of(2026, 10, 1)
private fun at(day: Int, hour: Int, minute: Int = 0) = october.withDayOfMonth(day).atTime(hour, minute)
private fun epoch(value: LocalDateTime) = value.atZone(demoZone).toInstant().toEpochMilli()
private fun ids(vararg values: String): () -> String {
    val iterator = values.iterator()
    return { iterator.next() }
}

/**
 * Uses the same encrypted repositories and capture use cases as the application.
 * This entry point is present only in the separately installed demo package.
 */
internal suspend fun seedOctoberDemo(context: Context) {
    require(context.packageName == "com.bitavida.app.demo") { "La carga está limitada al paquete de demostración." }
    val profiles = EncryptedCareProfileRepository(context)
    if (profiles.get() == null) {
        val result = CreateCareProfileUseCase(
            profiles, { epoch(at(1, 5)) },
            ids("demo-caregiver", "demo-patient", "demo-profile", "demo-consent", "demo-terms", "demo-privacy"),
        )(CreateCareProfileCommand("Cuidadora simulada", "Persona simulada", true, true, true))
        check(result is CreateCareProfileResult.Created)
    }
    val profile = requireNotNull(profiles.get())
    check(profile.id == "demo-profile" && profile.personUnderCare.id == "demo-patient")
    val person = profile.personUnderCare.id
    val caregiver = profile.caregiver.id
    val actor = GlucoseActor(profile.id, caregiver, person, true)
    val medical = EncryptedMedicalCareRepository(context)
    if (!medical.hasActiveSensitiveDataConsent(profile.id, caregiver, person, GLUCOSE_SENSITIVE_CONSENT_VERSION)) {
        medical.recordSensitiveDataConsent(profile.id, caregiver, person, GLUCOSE_SENSITIVE_CONSENT_VERSION, epoch(at(1, 5, 5)))
    }
    val glucose = EncryptedGlucoseRepository(context)
    val pressure = EncryptedBloodPressureRepository(context)
    val weight = EncryptedWeightRepository(context)

    suspend fun plan(index: Int, name: String, hour: Int, startDay: Int = 1, sourceId: String? = null): MedicationPlan {
        val id = "demo-plan-$index"
        medical.load(person).medicationPlans.firstOrNull { it.id == id }?.let { return it }
        val configuration = MedicationConfiguration(
            form = MedicationProductForm.TABLET,
            concentrationValue = null, concentrationUnit = null,
            baseAmount = MedicationAmount("1", "tableta"),
            frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = october.withDayOfMonth(startDay), endsOn = october.withDayOfMonth(31),
            administrations = listOf(MedicationAdministration(
                id = "demo-slot-$index", timing = MedicationAdministrationTiming.EXACT_TIME,
                exactTime = LocalTime.of(hour, 0), reminderEnabled = false,
            )),
        )
        val result = RegisterMedicationPlanUseCase(medical, { epoch(at(startDay, 6)) },
            ids(id, "demo-plan-revision-$index"), demoZone)(
            RegisterMedicationPlanCommand(
                medicalIndicationId = null, personUnderCareId = person, authorActorId = caregiver,
                medicationName = name, doseAsIndicated = "1 tableta", unitOrPresentation = "TABLET",
                frequencyAsIndicated = "Una vez al día", startsOn = october.withDayOfMonth(startDay),
                endsOn = october.withDayOfMonth(31), originalAdditionalInstructions = null,
                explicitSchedules = emptyList(), contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
                fictitiousDataAcknowledged = false, configuration = configuration,
                sourceMedicationPlanId = sourceId, careProfileId = profile.id, sensitiveConsentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
            )
        )
        check(result is MedicalCareMutationResult.Stored)
        return result.value
    }
    val control = plan(1, "Medicamento Alfa simulado", 8)
    val beta = plan(2, "Medicamento Beta simulado", 9)
    val gamma = plan(3, "Medicamento Gamma simulado", 10)
    plan(4, "Medicamento Delta simulado", 11)

    val betaCurrent = medical.load(person).medicationPlans.single { it.id == beta.id }
    if (betaCurrent.revisions.none { it.id == "demo-beta-change" }) {
        val revision = betaCurrent.currentRevision
        val result = ReviseMedicationPlanUseCase(medical, { epoch(at(7, 6)) },
            ids("demo-beta-change"), demoZone)(ReviseMedicationPlanCommand(
            medicationPlanId = beta.id, personUnderCareId = person, authorActorId = caregiver,
            medicationName = revision.medicationName, doseAsIndicated = "2 tabletas",
            unitOrPresentation = "TABLET", frequencyAsIndicated = revision.frequencyAsIndicated,
            startsOn = revision.startsOn, endsOn = revision.endsOn, originalAdditionalInstructions = null,
            explicitSchedules = emptyList(), changeReason = "Cambio de indicación simulado.",
            contentMode = ClinicalContentMode.REAL_CLINICAL_DATA, fictitiousDataAcknowledged = false,
            configuration = requireNotNull(revision.configuration).copy(baseAmount = MedicationAmount("2", "tabletas")),
            careProfileId = profile.id, sensitiveConsentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
        ))
        check(result is MedicalCareMutationResult.Stored)
    }
    suspend fun lifecycle(planId: String, day: Int, target: MedicationTreatmentStatus, revisionId: String) {
        val existing = medical.load(person).medicationPlans.single { it.id == planId }
        if (existing.revisions.any { it.id == revisionId }) return
        val result = ChangeMedicationLifecycleUseCase(medical, { epoch(at(day, 12)) }, ids(revisionId), demoZone)(
            ChangeMedicationLifecycleCommand(
                medicationPlanId = planId, personUnderCareId = person, authorActorId = caregiver,
                targetStatus = target, effectiveAt = at(day, 12), reason = "Cambio simulado.",
                careProfileId = profile.id, sensitiveConsentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
            )
        )
        check(result is MedicalCareMutationResult.Stored)
    }
    lifecycle(beta.id, 10, MedicationTreatmentStatus.SUSPENDED, "demo-beta-pause")
    lifecycle(beta.id, 16, MedicationTreatmentStatus.ACTIVE, "demo-beta-resume")
    lifecycle(gamma.id, 14, MedicationTreatmentStatus.COMPLETED, "demo-gamma-end")
    plan(5, "Medicamento Gamma simulado", 10, startDay = 18, sourceId = gamma.id)

    // 31 occasions: 27 complete, one partial, one explicitly not taken and two unconfirmed.
    val occurrences = projectMedicationAdministrations(
        listOf(control), at(1, 0), at(31, 23, 59), zoneId = demoZone,
    )
    check(occurrences.size == 31)
    for (day in 1..29) {
        val occurrence = occurrences.single { it.startsAt.toLocalDate().dayOfMonth == day }
        val id = "demo-intake-$day"
        if (medical.load(person).medicationIntakes.none { it.id == id }) {
            val amount = if (day == 29) null else MedicationAmount("1", "tableta")
            val result = RegisterMedicationIntakeUseCase(medical,
                { epoch(at(day, 8, 5)) }, ids(id, "demo-intake-revision-$day"))(
                RegisterMedicationIntakeCommand(
                    medicationPlanId = control.id, personUnderCareId = person, authorActorId = caregiver,
                    outcome = if (day == 29) MedicationIntakeOutcome.OMITTED else MedicationIntakeOutcome.COMPLETED,
                    scheduledAt = occurrence.startsAt, actualAt = if (day == 29) null else at(day, 8, 3),
                    actualAmount = amount, observation = if (day == 29) "No se tomó; motivo simulado." else null,
                    careProfileId = profile.id, sensitiveConsentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
                    scheduledOccurrenceId = occurrence.id,
                )
            )
            check(result is MedicalCareMutationResult.Stored)
        }
    }
    val revised = medical.load(person).medicationIntakes.single { it.id == "demo-intake-28" }
    if (revised.revisions.size == 1) {
        val current = revised.currentRevision
        val result = ReviseMedicationIntakeUseCase(medical, { epoch(at(28, 9)) }, ids("demo-intake-correction-28"))(
            ReviseMedicationIntakeCommand(
                intakeRecordId = revised.id, personUnderCareId = person, authorActorId = caregiver,
                outcome = MedicationIntakeOutcome.PARTIAL, scheduledAt = current.scheduledAt,
                actualAt = current.actualAt, actualAmount = MedicationAmount("0.5", "tableta"),
                observation = "Cantidad declarada parcialmente.", reason = "Corrección simulada.",
                careProfileId = profile.id, sensitiveConsentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
                scheduledOccurrenceId = current.scheduledOccurrenceId,
            ), expectedRevisionNumber = 1,
        )
        check(result is MedicalCareMutationResult.Stored)
    }

    // Additional administrations use the version known at each scheduled time.
    val additional = listOf(Triple(2, 8, 9), Triple(2, 17, 9), Triple(2, 30, 9),
        Triple(3, 8, 10), Triple(5, 20, 10), Triple(4, 25, 11))
    for ((planIndex, day, hour) in additional) {
        val id = "demo-extra-$planIndex-$day"
        if (medical.load(person).medicationIntakes.none { it.id == id }) {
            val plan = medical.load(person).medicationPlans.single { it.id == "demo-plan-$planIndex" }
            val known = plan.revisions.filter { it.capturedAtEpochMillis <= epoch(at(day, hour)) }
            val historical = plan.copy(revisions = known)
            val occurrence = projectMedicationAdministrations(
                listOf(historical), at(day, 0), at(day, 23, 59), zoneId = demoZone,
            ).single()
            val result = RegisterMedicationIntakeUseCase(medical, { epoch(at(day, hour, 5)) },
                ids(id, "$id-revision"))(RegisterMedicationIntakeCommand(
                medicationPlanId = plan.id, personUnderCareId = person, authorActorId = caregiver,
                outcome = MedicationIntakeOutcome.COMPLETED, scheduledAt = occurrence.startsAt,
                actualAt = at(day, hour, 2), actualAmount = occurrence.amount,
                observation = null, careProfileId = profile.id,
                sensitiveConsentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
                scheduledOccurrenceId = occurrence.id,
            ))
            check(result is MedicalCareMutationResult.Stored)
        }
    }

    val contexts = listOf(GlucoseContext.FASTING, GlucoseContext.AFTER_MEAL, GlucoseContext.BEFORE_SLEEP)
    val glucoseExisting = glucose.load(actor).mapTo(mutableSetOf()) { it.id }
    for ((index, day) in (1..31 step 3).withIndex()) {
        val id = "demo-glucose-$day"
        if (id !in glucoseExisting) {
            val result = SaveGlucoseMeasurementUseCase(glucose, { at(day, 7, 5) })(actor,
                GlucoseDraft(id, GlucoseRegistrationContext.Independent, at(day, 7),
                    reading = (100 + day).toString(), moment = contexts[index % contexts.size]))
            check(result is GlucoseSaveResult.Stored)
        }
    }
    val pressureExisting = pressure.load(actor).mapTo(mutableSetOf()) { it.id }
    for ((index, day) in listOf(3, 9, 15, 21, 27).withIndex()) {
        val id = "demo-pressure-$day"
        if (id !in pressureExisting) {
            val result = SaveBloodPressureMeasurementUseCase(pressure, { at(day, 8, 5) })(actor,
                BloodPressureDraft(id, BloodPressureRegistrationContext.Independent, at(day, 8),
                    systolic = (120 + index).toString(), diastolic = (78 + index).toString(),
                    moment = GlucoseContext.RESTING))
            check(result is BloodPressureSaveResult.Stored)
        }
    }
    val weightExisting = weight.load(actor).mapTo(mutableSetOf()) { it.id }
    for ((index, day) in listOf(1, 11, 21, 31).withIndex()) {
        val id = "demo-weight-$day"
        if (id !in weightExisting) {
            val result = SaveWeightMeasurementUseCase(weight, { at(day, 7, 35) })(actor,
                WeightDraft(id, WeightRegistrationContext.Independent, at(day, 7, 30),
                    kilograms = listOf("74.0", "73.9", "73.8", "73.7")[index]))
            check(result is WeightSaveResult.Stored)
        }
    }

    val observations = EncryptedObservationRepository(context)
    if (observations.load(actor).followUps.none { it.schedule.id == "demo-observation-followup" }) {
        val revision = GlucoseFollowUpRevision(
            id = "demo-observation-followup-r1", followUpId = "demo-observation-followup",
            revisionNumber = 1, indication = "Revisión semanal simulada.",
            frequency = GlucoseFollowUpFrequency.SPECIFIC_DAYS, weekdays = setOf(java.time.DayOfWeek.THURSDAY),
            startsOn = october, endsOn = october.withDayOfMonth(31), effectiveFrom = october,
            slots = listOf(GlucoseFollowUpSlot(id = "demo-observation-slot", time = LocalTime.of(18, 0),
                suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = false)),
            remindersEnabled = false, changeKind = GlucoseFollowUpChangeKind.CREATED,
            capturedAt = at(1, 6), authorActorId = caregiver,
        )
        SaveObservationFollowUpUseCase(observations)(actor,
            ObservationFollowUp("OBS-001", GlucoseFollowUp(
                id = "demo-observation-followup", personUnderCareId = person,
                createdAt = at(1, 6), createdByActorId = caregiver, revisions = listOf(revision))), 0)
    }
    for (day in listOf(8, 22)) {
        val snapshot = observations.load(actor)
        val id = "demo-observation-review-$day"
        if (snapshot.records.none { it.id == id }) {
            val occurrence = projectObservationOccurrences(october.withDayOfMonth(day),
                snapshot.followUps, snapshot.records, at(day, 18, 5))
                .single { it.followUpId == "demo-observation-followup" }
            val record = ObservationRecord(
                id = id, observationId = "OBS-001", personUnderCareId = person,
                occurredOn = october.withDayOfMonth(day), occurredAt = LocalTime.of(18, 0),
                timePrecision = ObservationTimePrecision.EXACT,
                result = if (day == 8) ObservationResult.NO_CHANGES else ObservationResult.NOT_PERFORMED,
                answers = if (day == 8) mapOf("foot" to listOf("Ambos")) else emptyMap(),
                description = if (day == 22) "Revisión no realizada; motivo simulado." else "",
                followUpId = occurrence.followUpId, occurrenceId = occurrence.id,
                scheduledAt = occurrence.scheduledAt, createdAt = at(day, 18, 5), actorId = caregiver,
            )
            SaveObservationUseCase(observations)(actor, record, at(day, 18, 5))
        }
    }
    for (day in listOf(6, 18, 25)) {
        val record = ObservationRecord(
            id = "demo-observation-$day", observationId = "OBS-027", personUnderCareId = person,
            occurredOn = october.withDayOfMonth(day), occurredAt = LocalTime.of(18, 0),
            timePrecision = ObservationTimePrecision.EXACT, result = ObservationResult.CHANGES,
            answers = emptyMap(), description = "Cambio ficticio documentado para la demostración.",
            createdAt = at(day, 18, 5), actorId = caregiver,
        )
        SaveObservationUseCase(observations)(actor, record, at(day, 18, 5))
    }
    val observationSnapshot = observations.load(actor)
    if (observationSnapshot.recordRevisions.none { it.id == "demo-observation-correction-18" }) {
        val original = observationSnapshot.records.single { it.id == "demo-observation-18" }
        CorrectObservationUseCase(observations)(actor, ObservationRecordRevision(
            id = "demo-observation-correction-18", recordId = original.id, revisionNumber = 2,
            snapshot = original.copy(description = "Cambio ficticio descrito con una corrección."),
            reason = "Corrección simulada.", correctedAt = at(20, 10), actorId = caregiver,
        ), expectedRevisionNumber = 1, now = at(20, 10))
    }
    val noteActor = NoteActor(profile.id, caregiver, person, true)
    val notes = EncryptedNoteRepository(context)
    val existingNotes = notes.load(noteActor).mapTo(mutableSetOf()) { it.id }
    if ("demo-note-1" !in existingNotes) {
        ManageNotes(notes)(noteActor, NoteCommand.Create("demo-note-create-1", "demo-note-1",
            NoteDraft("Apuntes simulados", "Anotación ficticia del seguimiento.")), at(9, 12))
    }
    if ("demo-note-2" !in existingNotes) {
        ManageNotes(notes)(noteActor, NoteCommand.Create("demo-note-create-2", "demo-note-2",
            NoteDraft("Preparar consulta simulada", "Llevar el registro ficticio.")), at(22, 12))
    }
    val reminderActor = CareReminderActor(profile.id, caregiver, person, true)
    val reminders = EncryptedCareReminderRepository(context)
    val existingReminders = reminders.load(reminderActor).mapTo(mutableSetOf()) { it.id }
    if ("demo-reminder-1" !in existingReminders) {
        ManageCareReminders(reminders)(reminderActor,
            CareReminderCommand.Create("demo-reminder-create-1", "demo-reminder-1",
                CareReminderDraft("Organizar documentos simulados", null, at(5, 17), false)), at(4, 12))
    }
    val firstReminder = reminders.load(reminderActor).single { it.id == "demo-reminder-1" }
    if (firstReminder.status == CareReminderStatus.PENDING) {
        ManageCareReminders(reminders)(reminderActor,
            CareReminderCommand.Close("demo-reminder-close-1", firstReminder.id, firstReminder.revision,
                CareReminderStatus.DONE), at(5, 17, 10))
    }
    if ("demo-reminder-2" !in existingReminders) {
        ManageCareReminders(reminders)(reminderActor,
            CareReminderCommand.Create("demo-reminder-create-2", "demo-reminder-2",
                CareReminderDraft("Revisar materiales simulados", null, at(23, 16), false)), at(20, 12))
    }

    val careEvents = EncryptedCareEventRepository(context)
    val careActor = CareEventActor(profile.id, caregiver, person, true)
    val existingEvents = careEvents.load(careActor).mapTo(mutableSetOf()) { it.id }
    val events = listOf(
        Triple("demo-appointment", CareEventKind.APPOINTMENT, CareEventStatus.CONFIRMED),
        Triple("demo-study", CareEventKind.STUDY, CareEventStatus.SCHEDULED),
    )
    events.forEachIndexed { index, (id, kind, status) ->
        if (id !in existingEvents) {
            careEvents.save(careActor, CareEventCommand.Create("$id-create", id,
                CareEventDraft(kind, if (index == 0) "Consulta simulada" else "Estudio simulado",
                    scheduledAt = at(if (index == 0) 12 else 24, 12), status = status,
                    reminderEnabled = false)), at(1, 6))
        }
    }
}
