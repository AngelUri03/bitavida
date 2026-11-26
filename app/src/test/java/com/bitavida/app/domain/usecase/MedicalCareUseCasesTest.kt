package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.MedicalIndication
import com.bitavida.app.domain.model.MedicalIndicationRevision
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.repository.MedicalCareConcurrentChangeException
import com.bitavida.app.domain.repository.MedicalCareRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedicalCareUseCasesTest {
    @Test
    fun savesTranscribedIndicationWithStableIdentityAndRealCaptureTime() = runBlocking {
        val repository = FakeMedicalCareRepository()
        val useCase = RegisterMedicalIndicationUseCase(
            repository = repository,
            nowEpochMillis = { 1_000L },
            newId = idSequence(),
        )

        val result = useCase(validIndicationCommand())

        assertTrue(result is MedicalCareMutationResult.Stored)
        val stored = repository.load(PERSON_ID).indications.single()
        assertEquals("id-1", stored.id)
        assertEquals(PERSON_ID, stored.personUnderCareId)
        assertEquals(1_000L, stored.createdAtEpochMillis)
        assertEquals("Texto ficticio original", stored.currentRevision.originalText)
        assertEquals(LocalDate.of(2026, 11, 10), stored.currentRevision.indicatedOn)
        assertEquals(1_000L, stored.currentRevision.capturedAtEpochMillis)
        assertEquals(ACTOR_ID, stored.currentRevision.authorActorId)
    }

    @Test
    fun correctionAppendsRevisionAndPreservesOriginalVersion() = runBlocking {
        val repository = FakeMedicalCareRepository()
        val create = RegisterMedicalIndicationUseCase(
            repository,
            nowEpochMillis = { 1_000L },
            newId = idSequence(),
        )
        val created = create(validIndicationCommand())
        val indication = (created as MedicalCareMutationResult.Stored).value
        val revise = ReviseMedicalIndicationUseCase(
            repository,
            nowEpochMillis = { 2_000L },
            newId = { "revision-2" },
        )

        val result = revise(
            ReviseMedicalIndicationCommand(
                indicationId = indication.id,
                personUnderCareId = PERSON_ID,
                authorActorId = ACTOR_ID,
                originalText = "Texto ficticio corregido",
                indicatedOn = LocalDate.of(2026, 11, 11),
                declaredProfessionalOrSource = "Fuente ficticia",
                validFrom = null,
                validUntil = null,
                changeReason = "Corrección declarada para prueba",
                contentMode = ClinicalContentMode.FICTITIOUS_DEVELOPMENT,
                fictitiousDataAcknowledged = true,
            ),
        )

        assertTrue(result is MedicalCareMutationResult.Stored)
        val stored = repository.load(PERSON_ID).indications.single()
        assertEquals(indication.id, stored.id)
        assertEquals(2, stored.revisions.size)
        assertEquals("Texto ficticio original", stored.revisions[0].originalText)
        assertEquals("Texto ficticio corregido", stored.revisions[1].originalText)
        assertEquals("Corrección declarada para prueba", stored.revisions[1].changeReason)
        assertEquals(listOf(1, 2), stored.revisions.map { it.revisionNumber })
    }

    @Test
    fun blocksRealClinicalCaptureAndRequiresExplicitMedicationSchedules() = runBlocking {
        val repository = FakeMedicalCareRepository()
        val registerIndication = RegisterMedicalIndicationUseCase(repository)

        val realResult = registerIndication(
            validIndicationCommand().copy(
                contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
            ),
        )

        assertTrue(realResult is MedicalCareMutationResult.Invalid)
        assertTrue(repository.load(PERSON_ID).indications.isEmpty())

        val created = registerIndication(validIndicationCommand())
        val indication = (created as MedicalCareMutationResult.Stored).value
        val medicationResult = RegisterMedicationPlanUseCase(repository)(
            RegisterMedicationPlanCommand(
                medicalIndicationId = indication.id,
                personUnderCareId = PERSON_ID,
                authorActorId = ACTOR_ID,
                medicationName = "Medicamento ficticio",
                doseAsIndicated = "Dosis ficticia transcrita",
                unitOrPresentation = null,
                frequencyAsIndicated = null,
                startsOn = LocalDate.of(2026, 11, 10),
                endsOn = null,
                originalAdditionalInstructions = null,
                explicitSchedules = emptyList(),
                contentMode = ClinicalContentMode.FICTITIOUS_DEVELOPMENT,
                fictitiousDataAcknowledged = true,
            ),
        )

        assertTrue(medicationResult is MedicalCareMutationResult.Invalid)
        val errors = (medicationResult as MedicalCareMutationResult.Invalid).errors
        assertTrue(errors.any { it.field == MedicalCareField.EXPLICIT_SCHEDULES })
        assertTrue(repository.load(PERSON_ID).medicationPlans.isEmpty())
    }

    private fun validIndicationCommand() = RegisterMedicalIndicationCommand(
        personUnderCareId = PERSON_ID,
        authorActorId = ACTOR_ID,
        originalText = "Texto ficticio original",
        indicatedOn = LocalDate.of(2026, 11, 10),
        declaredProfessionalOrSource = "Fuente ficticia",
        validFrom = null,
        validUntil = null,
        contentMode = ClinicalContentMode.FICTITIOUS_DEVELOPMENT,
        fictitiousDataAcknowledged = true,
    )

    private fun idSequence(): () -> String {
        var value = 0
        return { "id-${++value}" }
    }

    private companion object {
        const val PERSON_ID = "person-test"
        const val ACTOR_ID = "caregiver-test"
    }
}

private class FakeMedicalCareRepository : MedicalCareRepository {
    private val indications = linkedMapOf<String, MedicalIndication>()
    private val plans = linkedMapOf<String, MedicationPlan>()
    private val intakes = linkedMapOf<
        String,
        com.bitavida.app.domain.model.MedicationIntakeRecord
        >()

    override suspend fun load(personUnderCareId: String): MedicalCareOverview =
        MedicalCareOverview(
            indications.values.filter { it.personUnderCareId == personUnderCareId },
            plans.values.filter { it.personUnderCareId == personUnderCareId },
            intakes.values.filter {
                plans[it.medicationPlanId]?.personUnderCareId ==
                    personUnderCareId
            },
        )

    override suspend fun createIndication(indication: MedicalIndication) {
        check(indications.putIfAbsent(indication.id, indication) == null)
    }

    override suspend fun appendIndicationRevision(
        indicationId: String,
        expectedRevisionNumber: Int,
        revision: MedicalIndicationRevision,
    ) {
        val current = indications.getValue(indicationId)
        if (current.currentRevision.revisionNumber != expectedRevisionNumber) {
            throw MedicalCareConcurrentChangeException()
        }
        indications[indicationId] = current.copy(revisions = current.revisions + revision)
    }

    override suspend fun hasActiveSensitiveDataConsent(
        profileId: String,
        caregiverId: String,
        personUnderCareId: String,
        documentVersion: String,
    ): Boolean = false

    override suspend fun recordSensitiveDataConsent(
        profileId: String,
        caregiverId: String,
        personUnderCareId: String,
        documentVersion: String,
        acceptedAtEpochMillis: Long,
    ) = Unit

    override suspend fun createMedicationPlan(plan: MedicationPlan) {
        check(plans.putIfAbsent(plan.id, plan) == null)
    }

    override suspend fun appendMedicationPlanRevision(
        medicationPlanId: String,
        expectedRevisionNumber: Int,
        revision: MedicationPlanRevision,
    ) {
        val current = plans.getValue(medicationPlanId)
        if (current.currentRevision.revisionNumber != expectedRevisionNumber) {
            throw MedicalCareConcurrentChangeException()
        }
        plans[medicationPlanId] =
            current.copy(revisions = current.revisions + revision)
    }

    override suspend fun createMedicationIntake(
        record: com.bitavida.app.domain.model.MedicationIntakeRecord,
    ) {
        check(intakes.putIfAbsent(record.id, record) == null)
    }

    override suspend fun appendMedicationIntakeRevision(
        recordId: String,
        expectedRevisionNumber: Int,
        revision: com.bitavida.app.domain.model.MedicationIntakeRevision,
    ) {
        val current = intakes.getValue(recordId)
        if (
            current.currentRevision.revisionNumber !=
            expectedRevisionNumber
        ) {
            throw MedicalCareConcurrentChangeException()
        }
        intakes[recordId] =
            current.copy(revisions = current.revisions + revision)
    }
}
