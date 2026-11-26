package com.bitavida.app.demo

import android.content.Context
import com.bitavida.app.data.careprofile.EncryptedCareProfileRepository
import com.bitavida.app.data.medical.EncryptedMedicalCareRepository
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GLUCOSE_SENSITIVE_CONSENT_VERSION
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.*
import java.time.LocalTime

private fun demoIds(vararg ids: String): () -> String {
    val iterator = ids.iterator()
    return { iterator.next() }
}

/** Construye el escenario exclusivamente en el sandbox de la variante Demo. */
internal suspend fun seedDemoScenario(context: Context, reference: DemoReference) {
    check(context.packageName == "com.bitavida.app.demo")
    val base = reference.firstDate.atTime(5, 0)
    val epoch = base.atZone(reference.zone).toInstant().toEpochMilli()
    val profiles = EncryptedCareProfileRepository(context)
    if (profiles.get() == null) {
        val created = CreateCareProfileUseCase(profiles, { epoch },
            demoIds("demo-caregiver", "demo-patient", "demo-profile", "demo-consent",
                "demo-terms", "demo-privacy"))(
            CreateCareProfileCommand("Elena Ríos", "Marina Vega", true, true, true))
        check(created is CreateCareProfileResult.Created)
    }
    val profile = requireNotNull(profiles.get())
    check(profile.id == "demo-profile" && profile.personUnderCare.id == "demo-patient")
    val person = profile.personUnderCare.id
    val caregiver = profile.caregiver.id
    val actor = GlucoseActor(profile.id, caregiver, person, true)
    val medical = EncryptedMedicalCareRepository(context)
    if (!medical.hasActiveSensitiveDataConsent(profile.id, caregiver, person, GLUCOSE_SENSITIVE_CONSENT_VERSION)) {
        medical.recordSensitiveDataConsent(profile.id, caregiver, person,
            GLUCOSE_SENSITIVE_CONSENT_VERSION, epoch)
    }

    suspend fun lifecycle(planId: String, offset: Long, status: MedicationTreatmentStatus, id: String) {
        val plan = medical.load(person).medicationPlans.single { it.id == planId }
        if (plan.revisions.any { it.id == id }) return
        val effective = reference.firstDate.plusDays(offset).atTime(12, 0)
        val result = ChangeMedicationLifecycleUseCase(medical,
            { effective.atZone(reference.zone).toInstant().toEpochMilli() }, demoIds(id), reference.zone)(
            ChangeMedicationLifecycleCommand(
                medicationPlanId = planId, personUnderCareId = person, authorActorId = caregiver,
                targetStatus = status, effectiveAt = effective, reason = "Cambio de ejemplo.",
                careProfileId = profile.id, sensitiveConsentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
            ))
        check(result is MedicalCareMutationResult.Stored)
    }
    val medications = listOf("Metformina", "Losartán", "Pantoprazol", "Atorvastatina", "Paracetamol", "Omeprazol", "Pantoprazol")
    val existingPlans = medical.load(person).medicationPlans.mapTo(mutableSetOf()) { it.id }
    medications.forEachIndexed { index, name ->
        if (index == 6) {
            lifecycle("demo-medication-2", 8, MedicationTreatmentStatus.SUSPENDED, "demo-medication-2-pause")
            lifecycle("demo-medication-2", 12, MedicationTreatmentStatus.ACTIVE, "demo-medication-2-resume")
            lifecycle("demo-medication-3", 20, MedicationTreatmentStatus.COMPLETED, "demo-medication-3-end")
            lifecycle("demo-medication-4", 30, MedicationTreatmentStatus.SUSPENDED, "demo-medication-4-pause")
        }
        val id = "demo-medication-${index + 1}"
        if (id !in existingPlans) {
            val starts = when (index) {
                5 -> reference.lastDate.plusDays(5)
                6 -> reference.firstDate.plusDays(25)
                else -> reference.firstDate
            }
            val ends = if (index == 4) reference.firstDate.plusDays(20) else reference.lastDate.plusMonths(2)
            val config = MedicationConfiguration(
                form = MedicationProductForm.TABLET,
                concentrationValue = null, concentrationUnit = null,
                baseAmount = MedicationAmount("1", "tableta"),
                frequency = MedicationFrequencyKind.EVERY_DAY,
                startsOn = starts, endsOn = ends,
                administrations = listOf(MedicationAdministration(
                    id = "$id-slot", timing = MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(if (index == 6) 10 else 8 + index, 0), reminderEnabled = false,
                )),
            )
            val recordedAt = if (index == 6) starts.atTime(6, 0).atZone(reference.zone).toInstant().toEpochMilli() else epoch
            val result = RegisterMedicationPlanUseCase(medical, { recordedAt },
                demoIds(id, "$id-revision"), reference.zone)(
                RegisterMedicationPlanCommand(
                    medicalIndicationId = null, personUnderCareId = person, authorActorId = caregiver,
                    medicationName = name, doseAsIndicated = "1 tableta", unitOrPresentation = "TABLET",
                    frequencyAsIndicated = "Una vez al día", startsOn = starts, endsOn = ends,
                    originalAdditionalInstructions = null, explicitSchedules = emptyList(),
                    contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
                    fictitiousDataAcknowledged = false, configuration = config,
                    careProfileId = profile.id, sensitiveConsentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
                    sourceMedicationPlanId = if (index == 6) "demo-medication-3" else null,
                ))
            check(result is MedicalCareMutationResult.Stored)
        }
    }
    val snapshot = medical.load(person)
    val plans = snapshot.medicationPlans.filter { it.id.startsWith("demo-medication-") }
    check((1..7).all { number -> plans.count { it.id == "demo-medication-$number" } == 1 })
    val intakeIds = snapshot.medicationIntakes.mapTo(mutableSetOf()) { it.id }
    val expectedIntakes = mutableMapOf<String, UpcomingMedicationAdministration>()
    // Each occasion must refer to the plan revision that was known at its
    // scheduled time; a later lifecycle revision is not evidence at an earlier cut.
    val occurrences = plans.flatMap { plan ->
        val revisions = plan.revisions.filter {
            it.capturedAtEpochMillis <= reference.instant.toEpochMilli()
        }
        revisions.indices.flatMap { index ->
            val revisionAt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(revisions[index].capturedAtEpochMillis), reference.zone)
            val from = maxOf(reference.firstDate.atStartOfDay(), revisionAt)
            val until = revisions.getOrNull(index + 1)?.let {
                minOf(reference.local.toLocalDateTime(), java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(it.capturedAtEpochMillis), reference.zone).minusNanos(1))
            } ?: reference.local.toLocalDateTime()
            if (until.isBefore(from)) emptyList() else {
                projectMedicationAdministrations(
                    listOf(plan.copy(revisions = revisions.take(index + 1))),
                    from, until, limit = 2048, zoneId = reference.zone,
                ).filter { !it.startsAt.isBefore(from) && !it.startsAt.isAfter(until) }
            }
        }
    }
    occurrences.forEach { occurrence ->
        val scheduled = occurrence.startsAt
        val captured = scheduled.plusMinutes(5)
        val dayIndex = java.time.temporal.ChronoUnit.DAYS.between(reference.firstDate, scheduled.toLocalDate()).toInt()
        val leaveTodayUnconfirmed = scheduled.toLocalDate() == reference.lastDate &&
            occurrence.medicationPlanId in setOf("demo-medication-2", "demo-medication-7")
        if (reference.includesOccurredAt(captured) && dayIndex % 9 != 0 && !leaveTodayUnconfirmed) {
            val id = "demo-intake-${occurrence.medicationPlanId}-${scheduled.toLocalDate()}"
            check(expectedIntakes.put(id, occurrence) == null)
            if (id !in intakeIds) {
                val omitted = dayIndex % 17 == 0
                val result = RegisterMedicationIntakeUseCase(medical,
                    { captured.atZone(reference.zone).toInstant().toEpochMilli() }, demoIds(id, "$id-revision"))(
                    RegisterMedicationIntakeCommand(
                        medicationPlanId = occurrence.medicationPlanId, personUnderCareId = person,
                        authorActorId = caregiver,
                        outcome = if (omitted) MedicationIntakeOutcome.OMITTED else MedicationIntakeOutcome.COMPLETED,
                        scheduledAt = scheduled, actualAt = if (omitted) null else scheduled.plusMinutes(2),
                        actualAmount = if (omitted) null else occurrence.amount,
                        observation = if (omitted) "Registro ficticio no realizado." else null,
                        careProfileId = profile.id, sensitiveConsentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
                        scheduledOccurrenceId = occurrence.id,
                    ))
                check(result is MedicalCareMutationResult.Stored)
                intakeIds += id
            }
        }
    }
    val correctionDate = reference.firstDate.plusDays(1)
    val correctionId = "demo-intake-demo-medication-1-$correctionDate"
    val correctionRevisionId = "$correctionId-correction"
    val beforeCorrection = medical.load(person).medicationIntakes.single { it.id == correctionId }
    if (beforeCorrection.revisions.none { it.id == correctionRevisionId }) {
        val current = beforeCorrection.currentRevision
        check(current.revisionNumber == 1)
        val correctionTime = correctionDate.plusDays(1).atTime(9, 0)
        val result = ReviseMedicationIntakeUseCase(medical,
            { correctionTime.atZone(reference.zone).toInstant().toEpochMilli() }, demoIds(correctionRevisionId))(
            ReviseMedicationIntakeCommand(
                intakeRecordId = correctionId, personUnderCareId = person, authorActorId = caregiver,
                outcome = MedicationIntakeOutcome.PARTIAL, scheduledAt = current.scheduledAt,
                actualAt = current.actualAt, actualAmount = MedicationAmount("0.5", "tableta"),
                observation = "Cantidad declarada parcialmente.",
                reason = "Se aclaró la cantidad registrada en este ejemplo.",
                careProfileId = profile.id, sensitiveConsentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
                scheduledOccurrenceId = current.scheduledOccurrenceId,
            ), expectedRevisionNumber = 1)
        check(result is MedicalCareMutationResult.Stored)
    }

    val verified = medical.load(person)
    val actualById = verified.medicationIntakes.associateBy { it.id }
    check(expectedIntakes.all { (id, occurrence) ->
        actualById[id]?.let { intake ->
            intake.medicationPlanId == occurrence.medicationPlanId &&
                intake.currentRevision.scheduledOccurrenceId == occurrence.id &&
                intake.currentRevision.scheduledAt == occurrence.startsAt
        } == true
    })
    check(actualById.getValue(correctionId).revisions.any { it.id == correctionRevisionId })
    // Verify the persisted IDs against the same historical cuts the reports will use.
    // The expected identity set comes from the seed's generated occurrences.
    repeat(3) { monthOffset ->
        val month = java.time.YearMonth.from(reference.firstDate).plusMonths(monthOffset.toLong())
        val start = month.atDay(1).atStartOfDay()
        val end = minOf(month.atEndOfMonth().atTime(23, 59, 59, 999_999_999),
            reference.local.toLocalDateTime())
        val result = reportUnconfirmedMedicationOccurrences(
            plans, verified.medicationIntakes, start, end, reference.zone)
        expectedIntakes.filter { (_, occurrence) ->
            !occurrence.startsAt.isBefore(start) && !occurrence.startsAt.plusMinutes(5).isAfter(end)
        }.forEach { (recordId, expected) ->
            check(result.resolvedByRecordId[recordId]?.id == expected.id)
        }
    }
    seedDemoMeasurements(context, actor, reference)
    seedDemoMeasurementFollowUps(context, profile, reference)
    seedDemoObservations(context, actor, reference)
    seedDemoSupplementary(context, profile, reference)
}
