package com.bitavida.app.ui.demo

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.MedicalCareRepository
import com.bitavida.app.domain.repository.MedicalCareConcurrentChangeException
import com.bitavida.app.domain.repository.MedicalCareStorageUnavailableException
import com.bitavida.app.ui.medical.MedicalPlanAction
import com.bitavida.app.ui.medical.TodayMedicationFeedbackStatus
import com.bitavida.app.ui.medical.MedicalPlanViewModel
import com.bitavida.app.ui.medical.medicationOccurrencesForDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TodayMedicationQuickFormsTest {
    @Test
    fun takenUsesExactAdministrationAndCaregiverWithoutDuplicatingTheFact() =
        withMedication { viewModel, repository, plan ->
            val event = medicationOccurrencesForDay(plan, LocalDate.now()).single()

            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)
            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)

            val record = repository.intakes.single()
            assertEquals(plan.id, record.medicationPlanId)
            assertEquals("caregiver", record.createdByActorId)
            assertEquals(event.id, record.currentRevision.scheduledOccurrenceId)
            assertEquals(event.startsAt, record.currentRevision.scheduledAt)
            assertEquals("caregiver", record.currentRevision.authorActorId)
            assertEquals(MedicationIntakeOutcome.COMPLETED, record.currentRevision.outcome)
            assertEquals(event.amount, record.currentRevision.actualAmount)
            assertEquals(repository.tapTime, record.currentRevision.actualAt)
            assertNotEquals(event.startsAt, record.currentRevision.actualAt)
            val receipt = requireNotNull(viewModel.state.value.todayMedicationFeedback)
            assertEquals(TodayMedicationFeedbackStatus.STORED, receipt.status)
            assertEquals(record.id, receipt.recordId)
            assertEquals(record.currentRevision.actualAt, receipt.actualAt)
            assertEquals(1, record.revisions.size)
            assertNull(viewModel.state.value.medicationIntakeDraft)
            assertTrue(viewModel.state.value.fieldErrors.isEmpty())
        }

    @Test
    fun notTakenPreservesOmissionAndCannotBeChangedByAStalePendingCard() =
        withMedication { viewModel, repository, plan ->
            val event = medicationOccurrencesForDay(plan, LocalDate.now()).single()

            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.OMITTED)
            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.OMITTED)
            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)

            val record = repository.intakes.single()
            assertEquals(event.id, record.currentRevision.scheduledOccurrenceId)
            assertEquals(MedicationIntakeOutcome.OMITTED, record.currentRevision.outcome)
            assertEquals("caregiver", record.currentRevision.authorActorId)
            assertNull(record.currentRevision.actualAmount)
            assertEquals(1, record.revisions.size)
            assertEquals(1, viewModel.state.value.overview.medicationIntakes.size)
        }

    @Test
    fun undoTargetsItsExactReceiptAndAppendsAnAuditRevisionInsteadOfRemovingHistory() =
        withMedication(twoAdministrations = true) { viewModel, repository, plan ->
            val events = medicationOccurrencesForDay(plan, LocalDate.now())
            registerTodayMedication(viewModel, events[0], MedicationIntakeOutcome.COMPLETED)
            val firstReceipt = requireNotNull(viewModel.state.value.todayMedicationFeedback)
            registerTodayMedication(viewModel, events[1], MedicationIntakeOutcome.COMPLETED)
            val secondReceipt = requireNotNull(viewModel.state.value.todayMedicationFeedback)

            undoTodayMedication(viewModel, firstReceipt)
            assertTrue(repository.intakes.all { it.revisions.size == 1 })
            undoTodayMedication(viewModel, secondReceipt)
            undoTodayMedication(viewModel, secondReceipt)

            val first = repository.intakes.single { it.id == firstReceipt.recordId }
            assertEquals(MedicationIntakeOutcome.COMPLETED, first.currentRevision.outcome)
            assertEquals(1, first.revisions.size)
            val second = repository.intakes.single { it.id == secondReceipt.recordId }
            assertEquals(2, second.revisions.size)
            assertEquals(MedicationIntakeOutcome.COMPLETED, second.revisions.first().outcome)
            assertEquals(MedicationIntakeOutcome.UNCONFIRMED, second.currentRevision.outcome)
            assertEquals(MedicationIntakeChangeKind.JUSTIFIED, second.currentRevision.changeKind)
            assertTrue(requireNotNull(second.currentRevision.changeReason).isNotBlank())
            assertEquals("caregiver", second.currentRevision.authorActorId)
            assertEquals(TodayMedicationFeedbackStatus.UNDONE, viewModel.state.value.todayMedicationFeedback?.status)
            assertEquals(events[1].id, viewModel.state.value.todayMedicationFeedback?.occurrenceId)
        }

    @Test
    fun staleUndoCannotReplaceANewerPersistedCorrection() =
        withMedication { viewModel, repository, plan ->
            val event = medicationOccurrencesForDay(plan, LocalDate.now()).single()
            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)
            val receipt = requireNotNull(viewModel.state.value.todayMedicationFeedback)
            val original = repository.intakes.single()
            val correction = original.currentRevision.copy(
                id = "fictional-concurrent-revision",
                revisionNumber = 2,
                changeKind = MedicationIntakeChangeKind.CORRECTED,
                changeReason = "Corrección ficticia conservada",
                observation = "Nota posterior ficticia",
            )
            repository.intakes[0] = original.copy(revisions = original.revisions + correction)

            undoTodayMedication(viewModel, receipt)

            assertEquals(correction, repository.intakes.single().currentRevision)
            assertEquals(2, repository.intakes.single().revisions.size)
            assertEquals(TodayMedicationFeedbackStatus.FAILED, viewModel.state.value.todayMedicationFeedback?.status)
            assertNull(viewModel.state.value.globalError)
            assertNull(viewModel.state.value.medicationIntakeDraft)
        }

    @Test
    fun quickWriteIsNotConfirmedBeforePersistenceAndStorageFailureRemainsRetryable() =
        withMedication { viewModel, repository, plan ->
            val event = medicationOccurrencesForDay(plan, LocalDate.now()).single()
            repository.failWrites = true
            repository.beforeWrite = {
                assertTrue(viewModel.state.value.isSaving)
                assertEquals(TodayMedicationFeedbackStatus.SAVING, viewModel.state.value.todayMedicationFeedback?.status)
                assertTrue(viewModel.state.value.overview.medicationIntakes.isEmpty())
                registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)
            }

            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)

            assertTrue(repository.intakes.isEmpty())
            val failedRequest = requireNotNull(viewModel.state.value.todayMedicationFeedback).requestId
            assertEquals(TodayMedicationFeedbackStatus.FAILED, viewModel.state.value.todayMedicationFeedback?.status)
            assertNull(viewModel.state.value.globalError)
            assertNull(viewModel.state.value.medicationIntakeDraft)
            repository.beforeWrite = null
            repository.failWrites = false
            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)
            assertEquals(1, repository.intakes.size)
            assertEquals(TodayMedicationFeedbackStatus.STORED, viewModel.state.value.todayMedicationFeedback?.status)
            assertNotEquals(failedRequest, viewModel.state.value.todayMedicationFeedback?.requestId)
        }

    @Test
    fun explicitConsentResumesTheSameTapAndKeepsItsRealTime() =
        withMedication { viewModel, repository, plan ->
            val event = medicationOccurrencesForDay(plan, LocalDate.now()).single()
            repository.consent = false
            val tappedAt = repository.tapTime
            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)
            val waiting = requireNotNull(viewModel.state.value.todayMedicationFeedback)
            assertEquals(TodayMedicationFeedbackStatus.AWAITING_CONSENT, waiting.status)
            assertTrue(repository.intakes.isEmpty())
            viewModel.onAction(MedicalPlanAction.DismissTodayMedicationFeedback(waiting.requestId))
            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)
            assertEquals(waiting, viewModel.state.value.todayMedicationFeedback)

            repository.tapTime = repository.tapTime.plusMinutes(5)
            viewModel.onAction(MedicalPlanAction.SensitiveConsentChanged(true))
            viewModel.onAction(MedicalPlanAction.ConfirmSensitiveConsent)

            assertEquals(tappedAt, repository.intakes.single().currentRevision.actualAt)
            assertEquals(waiting.requestId, viewModel.state.value.todayMedicationFeedback?.requestId)
            assertEquals(TodayMedicationFeedbackStatus.STORED, viewModel.state.value.todayMedicationFeedback?.status)
        }

    @Test
    fun declinedConsentNeverCreatesOrLaterResumesAHiddenRegistration() =
        withMedication { viewModel, repository, plan ->
            val event = medicationOccurrencesForDay(plan, LocalDate.now()).single()
            repository.consent = false
            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)

            viewModel.onAction(MedicalPlanAction.DeclineSensitiveConsent)
            viewModel.onAction(MedicalPlanAction.ConfirmSensitiveConsent)

            assertTrue(repository.intakes.isEmpty())
            assertNull(viewModel.state.value.medicationIntakeDraft)
            assertNull(viewModel.state.value.globalError)
            assertFalse(viewModel.state.value.sensitiveConsentDialogVisible)
            assertEquals(TodayMedicationFeedbackStatus.FAILED, viewModel.state.value.todayMedicationFeedback?.status)
        }

    @Test
    fun anotherTimeAndNoteUseTheOfficialDraftWithoutCreatingUntilSave() =
        withMedication { viewModel, repository, plan ->
            val event = medicationOccurrencesForDay(plan, LocalDate.now()).single()

            openTodayMedicationRegistration(viewModel, event, expandNote = true)

            val draft = requireNotNull(viewModel.state.value.medicationIntakeDraft)
            assertTrue(repository.intakes.isEmpty())
            assertEquals(plan.id, viewModel.state.value.selectedMedicationPlanId)
            assertEquals(event.id, draft.scheduledOccurrenceId)
            assertTrue(draft.fromToday)
            assertTrue(draft.expandNoteOnOpen)
            assertTrue(draft.outcomeExplicitlySelected)
            viewModel.onAction(MedicalPlanAction.MedicationIntakeDraftChanged(
                draft.copy(actualTime = "07:45:23", observation = "Nota ficticia de la persona cuidadora"),
            ))
            viewModel.onAction(MedicalPlanAction.SaveMedicationIntake)
            viewModel.onAction(MedicalPlanAction.SaveMedicationIntake)

            val record = repository.intakes.single()
            assertEquals(LocalDate.now().atTime(7, 45, 23), record.currentRevision.actualAt)
            assertEquals("Nota ficticia de la persona cuidadora", record.currentRevision.observation)
            assertEquals(event.id, record.currentRevision.scheduledOccurrenceId)
        }

    @Test
    fun notTakenFromOptionsRequiresSavingAndPreservesItsOwnOutcome() =
        withMedication { viewModel, repository, plan ->
            val event = medicationOccurrencesForDay(plan, LocalDate.now()).single()
            openTodayMedicationRegistration(viewModel, event, MedicationIntakeOutcome.OMITTED)
            assertTrue(repository.intakes.isEmpty())
            assertEquals(MedicationIntakeOutcome.OMITTED, viewModel.state.value.medicationIntakeDraft?.outcome)

            viewModel.onAction(MedicalPlanAction.SaveMedicationIntake)

            assertEquals(MedicationIntakeOutcome.OMITTED, repository.intakes.single().currentRevision.outcome)
            assertNull(repository.intakes.single().currentRevision.actualAt)
            assertNull(repository.intakes.single().currentRevision.actualAmount)
        }

    @Test
    fun dismissingTheExactTerminalReceiptDoesNotAcknowledgeANewerRequest() =
        withMedication { viewModel, repository, plan ->
            val event = medicationOccurrencesForDay(plan, LocalDate.now()).single()
            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)
            val receipt = requireNotNull(viewModel.state.value.todayMedicationFeedback)
            assertTrue(receipt.createdAtEpochMillis > 0L)
            viewModel.onAction(MedicalPlanAction.DismissTodayMedicationFeedback("another-request"))
            assertEquals(receipt, viewModel.state.value.todayMedicationFeedback)
            viewModel.onAction(MedicalPlanAction.DismissTodayMedicationFeedback(receipt.requestId))
            assertNull(viewModel.state.value.todayMedicationFeedback)

            undoTodayMedication(viewModel, receipt)

            assertEquals(1, repository.intakes.single().revisions.size)
            assertEquals(MedicationIntakeOutcome.COMPLETED, repository.intakes.single().currentRevision.outcome)
        }

    @Test
    fun overnightWindowFromYesterdayCanBeRegisteredTodayWithItsOriginalIdentity() =
        withMedication(overnight = true) { viewModel, repository, plan ->
            val event = medicationOccurrencesForDay(plan, LocalDate.now())
                .single { it.startsAt.toLocalDate() == LocalDate.now().minusDays(1) }

            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)

            val record = repository.intakes.single()
            assertEquals(event.id, record.currentRevision.scheduledOccurrenceId)
            assertEquals(event.startsAt, record.currentRevision.scheduledAt)
            assertEquals(repository.tapTime, record.currentRevision.actualAt)
            assertEquals(TodayMedicationFeedbackStatus.STORED, viewModel.state.value.todayMedicationFeedback?.status)
        }

    @Test
    fun staleQuantityCannotBeSavedThroughTheQuickAction() =
        withMedication { viewModel, repository, plan ->
            val event = medicationOccurrencesForDay(plan, LocalDate.now()).single()

            registerTodayMedication(viewModel, event.copy(amount = MedicationAmount("2", "tabletas")), MedicationIntakeOutcome.COMPLETED)

            assertTrue(repository.intakes.isEmpty())
            assertNull(viewModel.state.value.medicationIntakeDraft)
            assertNull(viewModel.state.value.globalError)
        }

    @Test
    fun lifecycleChangeIsRevalidatedAgainstPersistenceBeforeTheTapIsSaved() =
        withMedication { viewModel, repository, plan ->
            val event = medicationOccurrencesForDay(plan, LocalDate.now()).single()
            val suspended = plan.currentRevision.copy(
                id = "fictional-suspension",
                revisionNumber = 2,
                changeKind = MedicationPlanChangeKind.SUSPENDED,
                changeReason = "Pausa ficticia declarada",
                lifecycleEffectiveAtEpochMillis = LocalDate.now().atStartOfDay()
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                configuration = requireNotNull(plan.currentRevision.configuration).copy(status = MedicationTreatmentStatus.SUSPENDED),
            )
            repository.plan = plan.copy(revisions = plan.revisions + suspended)

            registerTodayMedication(viewModel, event, MedicationIntakeOutcome.COMPLETED)

            assertTrue(repository.intakes.isEmpty())
            assertEquals(TodayMedicationFeedbackStatus.FAILED, viewModel.state.value.todayMedicationFeedback?.status)
            assertNull(viewModel.state.value.globalError)
            assertNull(viewModel.state.value.medicationIntakeDraft)
            assertEquals(suspended, viewModel.state.value.overview.medicationPlans.single().currentRevision)
        }

    private fun withMedication(
        twoAdministrations: Boolean = false,
        overnight: Boolean = false,
        block: (MedicalPlanViewModel, TodayMedicationRepository, MedicationPlan) -> Unit,
    ) {
        val day = LocalDate.now()
        val configuration = MedicationConfiguration(
            form = MedicationProductForm.TABLET,
            concentrationValue = "500",
            concentrationUnit = "mg",
            baseAmount = MedicationAmount("1", "tableta"),
            frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = day.minusDays(1),
            endsOn = null,
            status = MedicationTreatmentStatus.ACTIVE,
            administrations = List(if (twoAdministrations) 2 else 1) { index ->
                MedicationAdministration(
                    id = "administration-$index",
                    timing = if (overnight) MedicationAdministrationTiming.TIME_WINDOW else MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(8 + index, 0).takeUnless { overnight },
                    windowStart = LocalTime.of(22, 0).takeIf { overnight },
                    windowEnd = LocalTime.of(2, 0).takeIf { overnight },
                    windowEndsNextDay = overnight,
                )
            },
        )
        val plan = MedicationPlan(
            id = "plan",
            medicationSeriesId = "series",
            personUnderCareId = "person",
            medicalIndicationId = null,
            medicalContentOrigin = InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            scheduleOrigin = InformationOrigin.CAREGIVER_RECORD,
            createdAtEpochMillis = 1L,
            createdByActorId = "caregiver",
            contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
            revisions = listOf(
                MedicationPlanRevision(
                    id = "revision",
                    medicationPlanId = "plan",
                    revisionNumber = 1,
                    medicationName = "Medicamento ficticio",
                    doseAsIndicated = "1 tableta",
                    unitOrPresentation = "tableta",
                    frequencyAsIndicated = "Todos los días",
                    startsOn = day.minusDays(1),
                    endsOn = null,
                    originalAdditionalInstructions = null,
                    scheduledDoses = emptyList(),
                    capturedAtEpochMillis = 1L,
                    authorActorId = "caregiver",
                    changeKind = MedicationPlanChangeKind.CREATED,
                    changeReason = null,
                    configuration = configuration,
                ),
            ),
        )
        val caregiver = Caregiver("caregiver", "Persona cuidadora ficticia")
        val person = PersonUnderCare("person", "Persona ficticia", caregiver.id)
        val profile = CareProfile(
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = TodayMedicationRepository(plan)
        try {
            val viewModel = MedicalPlanViewModel(
                profile = profile,
                repository = repository,
                coroutineScopeOverride = scope,
                todayMedicationNow = { repository.tapTime },
            )
            assertFalse(viewModel.state.value.isLoading)
            block(viewModel, repository, plan)
        } finally {
            scope.cancel()
        }
    }
}

private class TodayMedicationRepository(var plan: MedicationPlan) : MedicalCareRepository {
    val intakes = mutableListOf<MedicationIntakeRecord>()
    var tapTime: LocalDateTime = LocalDate.now().atTime(12, 34, 56)
    var consent = true
    var failWrites = false
    var beforeWrite: (() -> Unit)? = null

    override suspend fun load(personUnderCareId: String) =
        MedicalCareOverview(emptyList(), listOf(plan), intakes.toList())

    override suspend fun hasActiveSensitiveDataConsent(
        profileId: String, caregiverId: String, personUnderCareId: String, documentVersion: String,
    ) = consent

    override suspend fun createMedicationIntake(record: MedicationIntakeRecord) {
        beforeWrite?.invoke()
        if (failWrites) throw MedicalCareStorageUnavailableException()
        check(intakes.none { it.id == record.id })
        intakes += record
    }

    override suspend fun recordSensitiveDataConsent(
        profileId: String, caregiverId: String, personUnderCareId: String,
        documentVersion: String, acceptedAtEpochMillis: Long,
    ) { consent = true }

    override suspend fun createMedicationPlan(plan: MedicationPlan) = error("Not used")
    override suspend fun createIndication(indication: MedicalIndication) = error("Not used")
    override suspend fun appendIndicationRevision(
        indicationId: String, expectedRevisionNumber: Int, revision: MedicalIndicationRevision,
    ) = error("Not used")
    override suspend fun appendMedicationPlanRevision(
        medicationPlanId: String, expectedRevisionNumber: Int, revision: MedicationPlanRevision,
    ) = error("Not used")
    override suspend fun appendMedicationIntakeRevision(
        recordId: String, expectedRevisionNumber: Int, revision: MedicationIntakeRevision,
    ) {
        if (failWrites) throw MedicalCareStorageUnavailableException()
        val index = intakes.indexOfFirst { it.id == recordId }
        val current = intakes[index]
        if (current.currentRevision.revisionNumber != expectedRevisionNumber) {
            throw MedicalCareConcurrentChangeException()
        }
        intakes[index] = current.copy(revisions = current.revisions + revision)
    }
}
