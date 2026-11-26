package com.bitavida.app.ui.medical

import com.bitavida.app.domain.model.AuthorizationDeclaration
import com.bitavida.app.domain.model.CareProfile
import com.bitavida.app.domain.model.Caregiver
import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.ConsentKind
import com.bitavida.app.domain.model.ConsentRecord
import com.bitavida.app.domain.model.ConsentStatus
import com.bitavida.app.domain.model.InformationOrigin
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.MedicalIndication
import com.bitavida.app.domain.model.MedicalIndicationRevision
import com.bitavida.app.domain.model.MedicationAdministration
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationIntakeRecord
import com.bitavida.app.domain.model.MedicationIntakeRevision
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationPlanChangeKind
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.model.PersonUnderCare
import com.bitavida.app.domain.repository.MedicalCareRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MedicalPlanNewPeriodFlowTest {
    @Test
    fun reviewActionCreatesPersistsAndOpensIndependentPeriod() {
        val today = LocalDate.now()
        val source = completedPlan(today.minusDays(8), today.minusDays(1))
        val repository = NewPeriodFlowRepository(source)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = MedicalPlanViewModel(
            profile = profile(),
            repository = repository,
            coroutineScopeOverride = scope,
        )

        viewModel.onAction(MedicalPlanAction.OpenMedication(source.id))
        viewModel.onAction(MedicalPlanAction.StartNewMedicationPeriod)

        val newStartsOn = today.plusDays(2)
        val newEndsOn = today.plusDays(12)
        val draft = viewModel.state.value.guidedMedicationDraft.copy(
            treatmentStartMode = MedicationTreatmentStartMode.SPECIFIC_DATE,
            treatmentStartDate = newStartsOn,
            treatmentEndDate = newEndsOn,
        )
        viewModel.onAction(MedicalPlanAction.GuidedMedicationDraftChanged(draft))
        viewModel.onAction(
            MedicalPlanAction.EditMedicationRegistrationStep(
                MedicationRegistrationStep.REVIEW,
            ),
        )

        // This is the exact action dispatched by the step 6 primary button.
        viewModel.onAction(MedicalPlanAction.SaveGuidedMedication)

        val storedState = viewModel.state.value
        assertEquals(MedicalPlanEffect.MedicationStored, storedState.effect)
        assertNull(storedState.medicationRegistrationSaveError)
        assertNotEquals(source.id, storedState.selectedMedicationPlanId)
        assertEquals(2, repository.snapshot().size)
        viewModel.onAction(MedicalPlanAction.SaveGuidedMedication)
        assertEquals(2, repository.snapshot().size)
        assertEquals(source, repository.snapshot().single { it.id == source.id })

        val created = repository.snapshot().single { it.id != source.id }
        assertEquals(source.medicationSeriesId, created.medicationSeriesId)
        assertEquals(newStartsOn, created.currentRevision.configuration?.startsOn)
        assertEquals(newEndsOn, created.currentRevision.configuration?.endsOn)

        viewModel.onAction(MedicalPlanAction.CompleteGuidedMedicationSuccess)
        assertEquals(MedicalPlanScreen.MEDICATION_DETAIL, viewModel.state.value.screen)
        assertEquals(created.id, viewModel.state.value.selectedMedicationPlanId)
        assertNull(viewModel.state.value.newMedicationPeriodSourcePlanId)

        // A fresh ViewModel reloads the same repository, matching an app reopen.
        val reopened = MedicalPlanViewModel(
            profile = profile(),
            repository = repository,
            coroutineScopeOverride =
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        reopened.onAction(MedicalPlanAction.OpenMedication(created.id))
        assertEquals(created.id, reopened.state.value.selectedMedicationPlanId)
        assertTrue(
            reopened.state.value.overview.medicationPlans
                .any { it.id == source.id && it.currentRevision.configuration?.status ==
                    MedicationTreatmentStatus.COMPLETED },
        )
    }

    @Test
    fun registrationExitCanContinueOrDiscardWithoutPersistingDraft() {
        val today = LocalDate.now()
        val repository = NewPeriodFlowRepository(
            completedPlan(today.minusDays(8), today.minusDays(1)),
        )
        val viewModel = MedicalPlanViewModel(
            profile = profile(),
            repository = repository,
            coroutineScopeOverride =
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        viewModel.onAction(MedicalPlanAction.OpenMedicationRegistration)
        viewModel.onAction(
            MedicalPlanAction.GuidedMedicationDraftChanged(
                GuidedMedicationDraft(
                    medicationName = "Medicamento ficticio sin guardar",
                ),
            ),
        )

        viewModel.onAction(MedicalPlanAction.Back)
        assertTrue(viewModel.state.value.medicationExitConfirmationVisible)

        viewModel.onAction(
            MedicalPlanAction.ContinueEditingMedicationRegistration,
        )
        assertEquals(
            MedicalPlanScreen.MEDICATION_REGISTRATION,
            viewModel.state.value.screen,
        )
        assertEquals(
            "Medicamento ficticio sin guardar",
            viewModel.state.value.guidedMedicationDraft.medicationName,
        )

        viewModel.onAction(MedicalPlanAction.Back)
        viewModel.onAction(MedicalPlanAction.DiscardMedicationRegistration)

        assertEquals(MedicalPlanScreen.SECTION, viewModel.state.value.screen)
        assertEquals(PlanSection.MEDICATIONS, viewModel.state.value.selectedSection)
        assertEquals(GuidedMedicationDraft(), viewModel.state.value.guidedMedicationDraft)
        assertEquals(1, repository.snapshot().size)
    }

    private fun completedPlan(
        startsOn: LocalDate,
        endsOn: LocalDate,
    ): MedicationPlan {
        val configuration = MedicationConfiguration(
            form = MedicationProductForm.TABLET,
            concentrationValue = "500",
            concentrationUnit = "mg",
            baseAmount = MedicationAmount("1", "tableta"),
            frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = startsOn,
            endsOn = endsOn,
            status = MedicationTreatmentStatus.COMPLETED,
            administrations = listOf(
                MedicationAdministration(
                    id = "administration-source",
                    timing = MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(8, 0),
                ),
            ),
        )
        return MedicationPlan(
            id = "period-source",
            medicationSeriesId = "medication-series",
            personUnderCareId = "person",
            medicalIndicationId = null,
            medicalContentOrigin =
                InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            scheduleOrigin = InformationOrigin.CAREGIVER_RECORD,
            createdAtEpochMillis = 1L,
            createdByActorId = "caregiver",
            contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
            revisions = listOf(
                MedicationPlanRevision(
                    id = "revision-source",
                    medicationPlanId = "period-source",
                    revisionNumber = 1,
                    medicationName = "Medicamento ficticio",
                    doseAsIndicated = "1 tableta",
                    // Older revisions can persist an empty optional presentation.
                    unitOrPresentation = "",
                    frequencyAsIndicated = "Todos los días",
                    startsOn = startsOn,
                    endsOn = endsOn,
                    originalAdditionalInstructions = null,
                    scheduledDoses = emptyList(),
                    capturedAtEpochMillis = 1L,
                    authorActorId = "caregiver",
                    changeKind = MedicationPlanChangeKind.COMPLETED,
                    changeReason = "Vigencia concluida",
                    configuration = configuration,
                ),
            ),
        )
    }

    private fun profile(): CareProfile {
        val caregiver = Caregiver("caregiver", "Persona cuidadora ficticia")
        val person = PersonUnderCare(
            id = "person",
            displayName = "Persona ficticia",
            declaredByCaregiverId = caregiver.id,
        )
        return CareProfile(
            id = "profile",
            caregiver = caregiver,
            personUnderCare = person,
            consent = ConsentRecord(
                id = "consent",
                caregiverId = caregiver.id,
                personUnderCareId = person.id,
                kind = ConsentKind.CAREGIVER_AUTHORIZATION_DECLARATION,
                authorizationDeclaration = AuthorizationDeclaration.CAREGIVER_DECLARED,
                status = ConsentStatus.ACTIVE,
                recordedAtEpochMillis = 1L,
            ),
            legalAcceptances = emptyList(),
            createdAtEpochMillis = 1L,
        )
    }
}

private class NewPeriodFlowRepository(
    initialPlan: MedicationPlan,
) : MedicalCareRepository {
    private val plans = linkedMapOf(initialPlan.id to initialPlan)

    fun snapshot(): List<MedicationPlan> = plans.values.toList()

    override suspend fun load(personUnderCareId: String) = MedicalCareOverview(
        indications = emptyList(),
        medicationPlans = plans.values.toList(),
        medicationIntakes = emptyList(),
    )

    override suspend fun hasActiveSensitiveDataConsent(
        profileId: String,
        caregiverId: String,
        personUnderCareId: String,
        documentVersion: String,
    ) = true

    override suspend fun createMedicationPlan(plan: MedicationPlan) {
        check(plans.putIfAbsent(plan.id, plan) == null)
    }

    override suspend fun appendMedicationPlanRevision(
        medicationPlanId: String,
        expectedRevisionNumber: Int,
        revision: MedicationPlanRevision,
    ) {
        val current = plans.getValue(medicationPlanId)
        check(current.currentRevision.revisionNumber == expectedRevisionNumber)
        plans[medicationPlanId] =
            current.copy(revisions = current.revisions + revision)
    }

    override suspend fun createIndication(indication: MedicalIndication) =
        error("Not used")

    override suspend fun appendIndicationRevision(
        indicationId: String,
        expectedRevisionNumber: Int,
        revision: MedicalIndicationRevision,
    ) = error("Not used")

    override suspend fun recordSensitiveDataConsent(
        profileId: String,
        caregiverId: String,
        personUnderCareId: String,
        documentVersion: String,
        acceptedAtEpochMillis: Long,
    ) = Unit

    override suspend fun createMedicationIntake(record: MedicationIntakeRecord) =
        error("Not used")

    override suspend fun appendMedicationIntakeRevision(
        recordId: String,
        expectedRevisionNumber: Int,
        revision: MedicationIntakeRevision,
    ) = error("Not used")
}
