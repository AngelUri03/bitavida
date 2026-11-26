package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.InformationOrigin
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.MedicalIndication
import com.bitavida.app.domain.model.MedicalIndicationChangeKind
import com.bitavida.app.domain.model.MedicalIndicationRevision
import com.bitavida.app.domain.model.MedicalIndicationStatus
import com.bitavida.app.domain.model.MedicationAdministration
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.domain.model.MedicationIntakeRecord
import com.bitavida.app.domain.model.MedicationIntakeRevision
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationPlanChangeKind
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationReactivationIssue
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.model.pendingReactivation
import com.bitavida.app.domain.model.reactivationIssue
import com.bitavida.app.domain.model.treatmentStatusAt
import com.bitavida.app.domain.repository.MedicalCareRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationOperationsUseCasesTest {
    private val date = LocalDate.of(2026, 11, 12)
    private val now = LocalDateTime.of(date, LocalTime.of(9, 0))
    private val zoneId = ZoneId.systemDefault()
    private val nowEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli()

    @Test
    fun suspendAndReactivateAppendRevisionsWithoutLosingHistory() =
        runBlocking {
            val repository = FakeRepository(plan())
            val suspend = ChangeMedicationLifecycleUseCase(
                repository = repository,
                nowEpochMillis = { nowEpochMillis },
                newId = ids("s"),
                zoneId = zoneId,
            )
            val reactivate = ChangeMedicationLifecycleUseCase(
                repository = repository,
                nowEpochMillis = { nowEpochMillis + 60_000L },
                newId = ids("r"),
                zoneId = zoneId,
            )

            suspend(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.SUSPENDED,
                    effectiveAt = now,
                ),
            )
            reactivate(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.ACTIVE,
                    effectiveAt = now.plusMinutes(1),
                ),
            )

            val stored = repository.load("person").medicationPlans.single()
            assertEquals(3, stored.revisions.size)
            assertEquals(
                MedicationPlanChangeKind.SUSPENDED,
                stored.revisions[1].changeKind,
            )
            assertEquals(
                MedicationPlanChangeKind.REACTIVATED,
                stored.currentRevision.changeKind,
            )
            assertEquals(
                MedicationTreatmentStatus.ACTIVE,
                stored.currentRevision.configuration?.status,
            )
            assertEquals(
                now.atZone(zoneId).toInstant().toEpochMilli(),
                stored.revisions[1].lifecycleEffectiveAtEpochMillis,
            )
            assertEquals(
                now.plusMinutes(1).atZone(zoneId).toInstant().toEpochMilli(),
                stored.currentRevision.lifecycleEffectiveAtEpochMillis,
            )
        }

    @Test
    fun finalizationUsesItsOwnAuditableChangeKind() = runBlocking {
        val repository = FakeRepository(plan())
        val result = ChangeMedicationLifecycleUseCase(
            repository = repository,
            nowEpochMillis = { nowEpochMillis },
            newId = ids("complete"),
            zoneId = zoneId,
        )(
            lifecycleCommand(
                status = MedicationTreatmentStatus.COMPLETED,
                effectiveAt = now,
            ),
        )

        assertTrue(result is MedicalCareMutationResult.Stored)
        val stored = repository.load("person").medicationPlans.single()
        assertEquals(
            MedicationPlanChangeKind.COMPLETED,
            stored.currentRevision.changeKind,
        )
        assertEquals(
            MedicationTreatmentStatus.COMPLETED,
            stored.currentRevision.configuration?.status,
        )
        assertEquals(2, stored.revisions.size)
    }

    @Test
    fun removalCutoffKeepsEarlierProjectionAndCancelsCutoffDay() =
        runBlocking {
            val repository = FakeRepository(plan())
            val useCase = ChangeMedicationLifecycleUseCase(
                repository = repository,
                nowEpochMillis = { nowEpochMillis },
                newId = ids("remove"),
                zoneId = zoneId,
            )
            useCase(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.REMOVED,
                    effectiveAt = date.plusDays(1).atStartOfDay(),
                ),
            )
            val stored = repository.load("person").medicationPlans.single()

            assertTrue(
                projectMedicationAdministrations(
                    plans = listOf(stored),
                    rangeStart =
                        LocalDateTime.of(date, LocalTime.of(7, 0)),
                    rangeEnd =
                        LocalDateTime.of(date, LocalTime.of(9, 0)),
                ).isNotEmpty(),
            )
            assertTrue(
                projectMedicationAdministrations(
                    plans = listOf(stored),
                    rangeStart = LocalDateTime.of(
                        date.plusDays(1),
                        LocalTime.of(7, 0),
                    ),
                    rangeEnd = LocalDateTime.of(
                        date.plusDays(1),
                        LocalTime.of(9, 0),
                    ),
                ).isEmpty(),
            )
            assertEquals(2, stored.revisions.size)
        }

    @Test
    fun scheduledReactivationKeepsSuspensionUntilItsEffectiveTime() =
        runBlocking {
            val repository = FakeRepository(plan())
            val suspend = ChangeMedicationLifecycleUseCase(
                repository = repository,
                nowEpochMillis = { nowEpochMillis },
                newId = ids("suspend"),
                zoneId = zoneId,
            )
            val reactivate = ChangeMedicationLifecycleUseCase(
                repository = repository,
                nowEpochMillis = { nowEpochMillis + 60_000L },
                newId = ids("reactivate"),
                zoneId = zoneId,
            )
            val effectiveAt = date.plusDays(1).atTime(9, 0)

            suspend(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.SUSPENDED,
                    effectiveAt = now,
                ),
            )
            val result = reactivate(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.ACTIVE,
                    effectiveAt = effectiveAt,
                ),
            )

            assertTrue(result is MedicalCareMutationResult.Stored)
            val duplicate = reactivate(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.ACTIVE,
                    effectiveAt = effectiveAt,
                ),
            )
            assertTrue(duplicate is MedicalCareMutationResult.Invalid)
            val stored = repository.load("person").medicationPlans.single()
            assertEquals(3, stored.revisions.size)
            val effectiveEpochMillis = effectiveAt.atZone(zoneId)
                .toInstant()
                .toEpochMilli()
            assertEquals(
                MedicationTreatmentStatus.SUSPENDED,
                stored.treatmentStatusAt(effectiveEpochMillis - 1L, zoneId),
            )
            assertEquals(
                MedicationTreatmentStatus.ACTIVE,
                stored.treatmentStatusAt(effectiveEpochMillis, zoneId),
            )
            val nextDayOccurrences = projectMedicationAdministrations(
                plans = listOf(stored),
                rangeStart = date.plusDays(1).atTime(7, 0),
                rangeEnd = date.plusDays(1).atTime(13, 0),
            )
            assertEquals(
                listOf(LocalTime.of(12, 0)),
                nextDayOccurrences.map { it.startsAt.toLocalTime() },
            )
        }

    @Test
    fun embeddedValidityIsInclusiveAndDoesNotTreatAPastDoseAsAnExpiredPlan() =
        runBlocking {
            val testZone = ZoneId.of("America/Mexico_City")
            val start = LocalDate.of(2026, 11, 14)
            val end = LocalDate.of(2026, 11, 30)
            val suspendedAt = start.atTime(8, 24)
            val requestedAt = start.atTime(12, 55)
            val plan = embeddedValidityPlan(start, end)
            val repository = FakeRepository(plan)

            ChangeMedicationLifecycleUseCase(
                repository = repository,
                nowEpochMillis = {
                    suspendedAt.atZone(testZone).toInstant().toEpochMilli()
                },
                newId = ids("validity-suspend"),
                zoneId = testZone,
            )(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.SUSPENDED,
                    effectiveAt = suspendedAt,
                ),
            )

            val reactivate = ChangeMedicationLifecycleUseCase(
                repository = repository,
                nowEpochMillis = {
                    requestedAt.atZone(testZone).toInstant().toEpochMilli()
                },
                newId = ids("validity-reactivate"),
                zoneId = testZone,
            )
            val pastResult = reactivate(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.ACTIVE,
                    effectiveAt = requestedAt.minusMinutes(2),
                ),
            )
            assertTrue(pastResult is MedicalCareMutationResult.Invalid)

            val result = reactivate(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.ACTIVE,
                    effectiveAt = requestedAt,
                ),
            )
            assertTrue(result is MedicalCareMutationResult.Stored)
            assertEquals(
                null,
                plan.reactivationIssue(null, end.atTime(23, 59)),
            )
            assertEquals(
                MedicationReactivationIssue.PLAN_ENDED,
                plan.reactivationIssue(null, end.plusDays(1).atStartOfDay()),
            )

            val stored = repository.load("person").medicationPlans.single()
            val occurrencesAfterReactivation = projectMedicationAdministrations(
                plans = listOf(stored),
                rangeStart = requestedAt,
                rangeEnd = start.plusDays(1).atStartOfDay(),
            )
            assertTrue(occurrencesAfterReactivation.isEmpty())
            assertEquals(
                MedicationPlanChangeKind.REACTIVATED,
                stored.currentRevision.changeKind,
            )
        }

    @Test
    fun scheduledReactivationCanBeRescheduledAndCancelledWithAudit() =
        runBlocking {
            val repository = FakeRepository(plan())
            val firstEffectiveAt = date.plusDays(1).atTime(9, 0)
            val secondEffectiveAt = date.plusDays(2).atTime(10, 0)
            ChangeMedicationLifecycleUseCase(
                repository = repository,
                nowEpochMillis = { nowEpochMillis },
                newId = ids("manage-suspend"),
                zoneId = zoneId,
            )(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.SUSPENDED,
                    effectiveAt = now,
                ),
            )
            ChangeMedicationLifecycleUseCase(
                repository = repository,
                nowEpochMillis = { nowEpochMillis + 60_000L },
                newId = ids("manage-schedule"),
                zoneId = zoneId,
            )(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.ACTIVE,
                    effectiveAt = firstEffectiveAt,
                ),
            )
            val rescheduled = ChangeMedicationLifecycleUseCase(
                repository = repository,
                nowEpochMillis = { nowEpochMillis + 120_000L },
                newId = ids("manage-reschedule"),
                zoneId = zoneId,
            )(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.ACTIVE,
                    effectiveAt = secondEffectiveAt,
                ),
            )
            assertTrue(rescheduled is MedicalCareMutationResult.Stored)
            var stored = repository.load("person").medicationPlans.single()
            assertEquals(
                MedicationPlanChangeKind.REACTIVATION_RESCHEDULED,
                stored.currentRevision.changeKind,
            )
            assertEquals(
                secondEffectiveAt.atZone(zoneId).toInstant().toEpochMilli(),
                stored.pendingReactivation(nowEpochMillis + 120_000L, zoneId)
                    ?.effectiveAtEpochMillis,
            )

            val cancelled = ChangeMedicationLifecycleUseCase(
                repository = repository,
                nowEpochMillis = { nowEpochMillis + 180_000L },
                newId = ids("manage-cancel"),
                zoneId = zoneId,
            )(
                lifecycleCommand(
                    status = MedicationTreatmentStatus.SUSPENDED,
                    effectiveAt = now.plusMinutes(3),
                ).copy(reason = ""),
            )
            assertTrue(cancelled is MedicalCareMutationResult.Stored)
            stored = repository.load("person").medicationPlans.single()
            assertEquals(
                MedicationPlanChangeKind.REACTIVATION_CANCELLED,
                stored.currentRevision.changeKind,
            )
            assertEquals(
                null,
                stored.pendingReactivation(nowEpochMillis + 180_000L, zoneId),
            )
            assertEquals(
                MedicationTreatmentStatus.SUSPENDED,
                stored.treatmentStatusAt(
                    secondEffectiveAt.atZone(zoneId).toInstant().toEpochMilli(),
                    zoneId,
                ),
            )
        }

    @Test
    fun intakeCorrectionPreservesOriginalAndRequiresReason() =
        runBlocking {
            val repository = FakeRepository(plan())
            val register = RegisterMedicationIntakeUseCase(
                repository = repository,
                nowEpochMillis = { 10L },
                newId = ids("take"),
            )
            val result = register(
                RegisterMedicationIntakeCommand(
                    medicationPlanId = "plan",
                    personUnderCareId = "person",
                    authorActorId = "actor",
                    outcome = MedicationIntakeOutcome.COMPLETED,
                    scheduledAt = LocalDateTime.of(
                        date,
                        LocalTime.of(8, 0),
                    ),
                    actualAt = LocalDateTime.of(
                        date,
                        LocalTime.of(8, 15),
                    ),
                    actualAmount = MedicationAmount("1", "tableta"),
                    observation = "Registro inicial",
                    careProfileId = "profile",
                    sensitiveConsentVersion = "v1",
                ),
            ) as MedicalCareMutationResult.Stored
            val record = result.value as MedicationIntakeRecord
            val revise = ReviseMedicationIntakeUseCase(
                repository = repository,
                nowEpochMillis = { 20L },
                newId = ids("revision"),
            )
            val invalid = revise(
                ReviseMedicationIntakeCommand(
                    intakeRecordId = record.id,
                    personUnderCareId = "person",
                    authorActorId = "actor",
                    outcome = MedicationIntakeOutcome.PARTIAL,
                    scheduledAt = record.currentRevision.scheduledAt,
                    actualAt = record.currentRevision.actualAt,
                    actualAmount = MedicationAmount("1/2", "tableta"),
                    observation = "Cantidad corregida",
                    reason = "",
                    careProfileId = "profile",
                    sensitiveConsentVersion = "v1",
                ),
            )
            assertTrue(invalid is MedicalCareMutationResult.Invalid)

            revise(
                ReviseMedicationIntakeCommand(
                    intakeRecordId = record.id,
                    personUnderCareId = "person",
                    authorActorId = "actor",
                    outcome = MedicationIntakeOutcome.PARTIAL,
                    scheduledAt = record.currentRevision.scheduledAt,
                    actualAt = record.currentRevision.actualAt,
                    actualAmount = MedicationAmount("1/2", "tableta"),
                    observation = "Cantidad corregida",
                    reason = "Corrección declarada",
                    careProfileId = "profile",
                    sensitiveConsentVersion = "v1",
                ),
            )

            val stored = repository.load("person")
                .medicationIntakes.single()
            assertEquals(2, stored.revisions.size)
            assertEquals(
                MedicationIntakeOutcome.COMPLETED,
                stored.revisions.first().outcome,
            )
            assertEquals(
                MedicationIntakeOutcome.PARTIAL,
                stored.currentRevision.outcome,
            )
        }

    @Test
    fun operationIdentityMakesRetriesIdempotentWithoutMergingSeparateFacts() = runBlocking {
        val repository = FakeRepository(plan())
        val register = RegisterMedicationIntakeUseCase(repository, nowEpochMillis = { 10L }, newId = ids("version"))
        val moment = LocalDateTime.of(date, LocalTime.of(8, 0))
        val command = RegisterMedicationIntakeCommand(
            medicationPlanId = "plan", personUnderCareId = "person", authorActorId = "actor",
            outcome = MedicationIntakeOutcome.COMPLETED, scheduledAt = moment, actualAt = moment,
            actualAmount = MedicationAmount("1", "tableta"), observation = null,
            careProfileId = "profile", sensitiveConsentVersion = "v1",
            scheduledOccurrenceId = "one-occasion", operationId = "00000000-0000-0000-0000-000000000001",
        )
        val first = register(command) as MedicalCareMutationResult.Stored
        val retry = register(command) as MedicalCareMutationResult.Stored
        assertEquals(first.value.id, retry.value.id)
        assertEquals(1, repository.load("person").medicationIntakes.size)
        assertTrue(register(command.copy(actualAmount = MedicationAmount("2", "tabletas"))) is MedicalCareMutationResult.Invalid)
        assertEquals(1, repository.load("person").medicationIntakes.size)

        // A separate, explicitly identified action is not merged by occasion or minute.
        val other = register(command.copy(operationId = "00000000-0000-0000-0000-000000000002")) as MedicalCareMutationResult.Stored
        assertTrue(first.value.id != other.value.id)
        assertEquals(2, repository.load("person").medicationIntakes.size)

        val revise = ReviseMedicationIntakeUseCase(repository, nowEpochMillis = { 20L }, newId = ids("edit"))
        revise(ReviseMedicationIntakeCommand(first.value.id, "person", "actor", MedicationIntakeOutcome.PARTIAL,
            moment, moment, MedicationAmount("1/2", "tableta"), null, "Motivo ficticio",
            careProfileId = "profile", sensitiveConsentVersion = "v1", scheduledOccurrenceId = "one-occasion"))
        val retryAfterEdit = register(command) as MedicalCareMutationResult.Stored
        assertEquals(2, retryAfterEdit.value.revisions.size)
        assertEquals(MedicationIntakeOutcome.PARTIAL, retryAfterEdit.value.currentRevision.outcome)
        assertEquals(2, repository.load("person").medicationIntakes.size)
    }

    @Test
    fun factsWithoutOperationIdentityAreNeverMergedByMinuteOrOccasion() = runBlocking {
        val repository = FakeRepository(plan())
        val register = RegisterMedicationIntakeUseCase(repository, nowEpochMillis = { 10L }, newId = ids("fact"))
        val moment = LocalDateTime.of(date, LocalTime.of(8, 0))
        val command = RegisterMedicationIntakeCommand(
            medicationPlanId = "plan", personUnderCareId = "person", authorActorId = "actor",
            outcome = MedicationIntakeOutcome.COMPLETED, scheduledAt = moment, actualAt = moment,
            actualAmount = MedicationAmount("1", "tableta"), observation = null,
            careProfileId = "profile", sensitiveConsentVersion = "v1",
        )
        register(command)
        register(command.copy(actualAmount = MedicationAmount("2", "tabletas")))
        assertEquals(2, repository.load("person").medicationIntakes.size)

        val scheduled = command.copy(scheduledOccurrenceId = "scheduled-slot-1")
        val first = register(scheduled) as MedicalCareMutationResult.Stored
        val second = register(scheduled) as MedicalCareMutationResult.Stored
        assertTrue(first.value.id != second.value.id)
        assertEquals(4, repository.load("person").medicationIntakes.size)
    }

    @Test
    fun completedIntakeRequiresActualTime() = runBlocking {
        val repository = FakeRepository(plan())
        val result = RegisterMedicationIntakeUseCase(repository)(
            RegisterMedicationIntakeCommand(
                medicationPlanId = "plan",
                personUnderCareId = "person",
                authorActorId = "actor",
                outcome = MedicationIntakeOutcome.COMPLETED,
                scheduledAt = LocalDateTime.of(date, LocalTime.of(8, 0)),
                actualAt = null,
                actualAmount = null,
                observation = null,
                careProfileId = "profile",
                sensitiveConsentVersion = "v1",
            ),
        )
        assertTrue(result is MedicalCareMutationResult.Invalid)
        assertFalse(repository.load("person").medicationIntakes.isNotEmpty())
    }

    @Test
    fun automaticFinalizationStartsAfterInclusiveEndAndIsIdempotent() =
        runBlocking {
            val endingPlan = embeddedValidityPlan(
                startsOn = date.minusDays(2),
                endsOn = date,
            )
            val repository = FakeRepository(endingPlan)
            val beforeEnd = FinalizeExpiredMedicationPlansUseCase(
                repository = repository,
                nowEpochMillis = {
                    date.atTime(23, 59).atZone(zoneId).toInstant().toEpochMilli()
                },
                newId = ids("before"),
                zoneId = zoneId,
            )

            assertEquals(0, beforeEnd("person"))
            assertEquals(1, repository.load("person").medicationPlans.single().revisions.size)

            val afterEnd = FinalizeExpiredMedicationPlansUseCase(
                repository = repository,
                nowEpochMillis = {
                    date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                },
                newId = ids("finished"),
                zoneId = zoneId,
            )
            assertEquals(1, afterEnd("person"))
            assertEquals(0, afterEnd("person"))

            val stored = repository.load("person").medicationPlans.single()
            assertEquals(2, stored.revisions.size)
            assertEquals(
                MedicationPlanChangeKind.COMPLETED,
                stored.currentRevision.changeKind,
            )
            assertEquals("Vigencia concluida", stored.currentRevision.changeReason)
            assertEquals(
                MedicationTreatmentStatus.COMPLETED,
                stored.currentRevision.configuration?.status,
            )
            assertTrue(
                projectMedicationAdministrations(
                    plans = listOf(stored),
                    rangeStart = date.atStartOfDay(),
                    rangeEnd = date.atTime(23, 59),
                ).isNotEmpty(),
            )
            assertTrue(
                projectMedicationAdministrations(
                    plans = listOf(stored),
                    rangeStart = date.plusDays(1).atStartOfDay(),
                    rangeEnd = date.plusDays(1).atTime(23, 59),
                ).isEmpty(),
            )
        }

    @Test
    fun newPeriodKeepsClosedHistoryAndDoesNotProjectGapDays() =
        runBlocking {
            val closedPlan = embeddedValidityPlan(
                startsOn = date.minusDays(2),
                endsOn = date,
            )
            val repository = FakeRepository(closedPlan)
            FinalizeExpiredMedicationPlansUseCase(
                repository = repository,
                nowEpochMillis = {
                    date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                },
                newId = ids("closed"),
                zoneId = zoneId,
            )("person")
            val closedRevisions = repository.load("person")
                .medicationPlans.single().revisions
            val newStartsOn = date.plusDays(10)
            val newEndsOn = date.plusDays(20)
            val previousConfiguration =
                requireNotNull(closedPlan.currentRevision.configuration)
            val result = RegisterMedicationPlanUseCase(
                repository = repository,
                nowEpochMillis = {
                    date.plusDays(2).atStartOfDay(zoneId).toInstant().toEpochMilli()
                },
                newId = ids("period"),
            )(
                RegisterMedicationPlanCommand(
                    medicalIndicationId = null,
                    personUnderCareId = "person",
                    authorActorId = "actor",
                    medicationName = "Medicamento ficticio",
                    doseAsIndicated = "1 tableta",
                    unitOrPresentation = "TABLET",
                    frequencyAsIndicated = "Todos los días",
                    startsOn = newStartsOn,
                    endsOn = newEndsOn,
                    originalAdditionalInstructions = null,
                    explicitSchedules = listOf(LocalTime.of(0, 30)),
                    contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
                    fictitiousDataAcknowledged = false,
                    configuration = previousConfiguration.copy(
                        startsOn = newStartsOn,
                        endsOn = newEndsOn,
                        status = MedicationTreatmentStatus.ACTIVE,
                    ),
                    sourceMedicationPlanId = closedPlan.id,
                    careProfileId = "profile",
                    sensitiveConsentVersion = "test-version",
                ),
            )

            assertTrue(result is MedicalCareMutationResult.Stored)
            val plans = repository.load("person").medicationPlans
            assertEquals(2, plans.size)
            val previous = plans.single { it.id == closedPlan.id }
            val next = plans.single { it.id != closedPlan.id }
            assertEquals(closedRevisions, previous.revisions)
            assertEquals(previous.medicationSeriesId, next.medicationSeriesId)
            assertTrue(
                projectMedicationAdministrations(
                    plans = plans,
                    rangeStart = date.plusDays(1).atStartOfDay(),
                    rangeEnd = date.plusDays(9).atTime(23, 59),
                ).isEmpty(),
            )
            assertTrue(
                projectMedicationAdministrations(
                    plans = plans,
                    rangeStart = newStartsOn.atStartOfDay(),
                    rangeEnd = newStartsOn.atTime(23, 59),
                ).isNotEmpty(),
            )
        }

    @Test
    fun completedPeriodCannotBeRevisedOrExtended() = runBlocking {
        val original = embeddedValidityPlan(
            startsOn = date.minusDays(2),
            endsOn = date,
        )
        val repository = FakeRepository(original)
        FinalizeExpiredMedicationPlansUseCase(
            repository = repository,
            nowEpochMillis = {
                date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            },
            newId = ids("closed"),
            zoneId = zoneId,
        )("person")
        val closed = repository.load("person").medicationPlans.single()
        val closedRevisions = closed.revisions
        val configuration = requireNotNull(closed.currentRevision.configuration)

        val result = ReviseMedicationPlanUseCase(
            repository = repository,
            nowEpochMillis = {
                date.plusDays(1).atTime(9, 0)
                    .atZone(zoneId).toInstant().toEpochMilli()
            },
            newId = ids("forbidden"),
            zoneId = zoneId,
        )(
            ReviseMedicationPlanCommand(
                medicationPlanId = closed.id,
                personUnderCareId = "person",
                authorActorId = "actor",
                medicationName = closed.currentRevision.medicationName,
                doseAsIndicated = closed.currentRevision.doseAsIndicated,
                unitOrPresentation =
                    closed.currentRevision.unitOrPresentation,
                frequencyAsIndicated =
                    closed.currentRevision.frequencyAsIndicated,
                startsOn = configuration.startsOn,
                endsOn = date.plusDays(30),
                originalAdditionalInstructions =
                    closed.currentRevision.originalAdditionalInstructions,
                explicitSchedules = listOf(LocalTime.of(0, 30)),
                changeReason = "Intento ficticio de reabrir el periodo",
                contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
                fictitiousDataAcknowledged = false,
                configuration = configuration.copy(
                    endsOn = date.plusDays(30),
                    status = MedicationTreatmentStatus.ACTIVE,
                ),
                careProfileId = "profile",
                sensitiveConsentVersion = "test-version",
            ),
        )

        assertTrue(result is MedicalCareMutationResult.Invalid)
        assertEquals(
            closedRevisions,
            repository.load("person").medicationPlans.single().revisions,
        )
    }

    @Test
    fun newPeriodMustKeepMedicationIdentity() = runBlocking {
        val original = embeddedValidityPlan(
            startsOn = date.minusDays(2),
            endsOn = date,
        )
        val repository = FakeRepository(original)
        FinalizeExpiredMedicationPlansUseCase(
            repository = repository,
            nowEpochMillis = {
                date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            },
            newId = ids("closed"),
            zoneId = zoneId,
        )("person")
        val closed = repository.load("person").medicationPlans.single()
        val configuration = requireNotNull(closed.currentRevision.configuration)
        val newStartsOn = date.plusDays(3)

        val result = RegisterMedicationPlanUseCase(
            repository = repository,
            nowEpochMillis = {
                date.plusDays(1).atTime(9, 0)
                    .atZone(zoneId).toInstant().toEpochMilli()
            },
            newId = ids("identity"),
        )(
            RegisterMedicationPlanCommand(
                medicalIndicationId = null,
                personUnderCareId = "person",
                authorActorId = "actor",
                medicationName = "Otro medicamento ficticio",
                doseAsIndicated = "1 tableta",
                unitOrPresentation = closed.currentRevision.unitOrPresentation,
                frequencyAsIndicated = "Todos los días",
                startsOn = newStartsOn,
                endsOn = newStartsOn.plusDays(5),
                originalAdditionalInstructions = null,
                explicitSchedules = listOf(LocalTime.of(0, 30)),
                contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
                fictitiousDataAcknowledged = false,
                configuration = configuration.copy(
                    startsOn = newStartsOn,
                    endsOn = newStartsOn.plusDays(5),
                    status = MedicationTreatmentStatus.ACTIVE,
                ),
                sourceMedicationPlanId = closed.id,
                careProfileId = "profile",
                sensitiveConsentVersion = "test-version",
            ),
        )

        assertTrue(result is MedicalCareMutationResult.Invalid)
        assertEquals(1, repository.load("person").medicationPlans.size)
    }

    @Test
    fun completedEffectiveBoundaryAllowsGrandotolinaNewPeriodWithoutOverlap() =
        runBlocking {
            val original = embeddedValidityPlan(
                startsOn = date.minusDays(20),
                endsOn = date.plusDays(20),
            )
            val completionEffectiveAt = date.minusDays(2)
                .atStartOfDay(zoneId).toInstant().toEpochMilli()
            val completedRevisionId = "grandotolina-completed"
            val completed = original.copy(
                revisions = original.revisions + original.currentRevision.copy(
                    id = completedRevisionId,
                    revisionNumber = 2,
                    scheduledDoses = emptyList(),
                    capturedAtEpochMillis = completionEffectiveAt,
                    changeKind = MedicationPlanChangeKind.COMPLETED,
                    changeReason = "Vigencia concluida",
                    lifecycleEffectiveAtEpochMillis = completionEffectiveAt,
                    configuration = requireNotNull(
                        original.currentRevision.configuration,
                    ).copy(status = MedicationTreatmentStatus.COMPLETED),
                ),
            )
            val repository = FakeRepository(
                completed.copy(
                    revisions = completed.revisions.map {
                        it.copy(medicationName = "Grandotolina")
                    },
                ),
            )
            val startsOn = date.minusDays(1)
            val result = RegisterMedicationPlanUseCase(
                repository = repository,
                nowEpochMillis = { nowEpochMillis },
                newId = ids("grandotolina-period"),
                zoneId = zoneId,
            )(
                newPeriodCommand(
                    name = "Grandotolina",
                    startsOn = startsOn,
                    endsOn = date.plusDays(10),
                    configuration = requireNotNull(
                        completed.currentRevision.configuration,
                    ),
                    sourceId = completed.id,
                ),
            )

            assertTrue(result is MedicalCareMutationResult.Stored)
            assertEquals(2, repository.load("person").medicationPlans.size)
        }

    @Test
    fun normalRegistrationReusesPantoprazolIdentityAndRejectsDuplicateRetry() =
        runBlocking {
            val original = embeddedValidityPlan(
                startsOn = date.minusDays(10),
                endsOn = date.minusDays(2),
            )
            val completed = original.copy(
                medicationSeriesId = "pantoprazol-series",
                revisions = original.revisions.map {
                    it.copy(
                        medicationName = "Pantoprazol",
                        configuration = requireNotNull(it.configuration).copy(
                            status = MedicationTreatmentStatus.COMPLETED,
                        ),
                    )
                },
            )
            val repository = FakeRepository(completed)
            val startsOn = date.plusDays(2)
            val command = newPeriodCommand(
                name = "  PANTOPRAZOL  ",
                startsOn = startsOn,
                endsOn = startsOn.plusDays(10),
                configuration = requireNotNull(
                    completed.currentRevision.configuration,
                ),
                sourceId = null,
            )
            val register = RegisterMedicationPlanUseCase(
                repository = repository,
                nowEpochMillis = { nowEpochMillis },
                newId = ids("pantoprazol-period"),
                zoneId = zoneId,
            )

            val first = register(command)
            val retry = register(command)

            assertTrue(first is MedicalCareMutationResult.Stored)
            assertTrue(retry is MedicalCareMutationResult.Invalid)
            val plans = repository.load("person").medicationPlans
            assertEquals(2, plans.size)
            assertEquals(
                setOf("pantoprazol-series"),
                plans.map(MedicationPlan::medicationSeriesId).toSet(),
            )
            val retryErrors = (retry as MedicalCareMutationResult.Invalid).errors
            assertTrue(
                retryErrors.any {
                    it.issue == MedicalCareValidationIssue.OVERLAPPING_PERIOD
                },
            )
        }

    @Test
    fun normalRegistrationRejectsOverlappingMedicationWithSameNormalizedName() =
        runBlocking {
            val active = plan().copy(
                revisions = plan().revisions.map {
                    it.copy(medicationName = "Pantoprazol")
                },
            )
            val repository = FakeRepository(active)
            val configuration = requireNotNull(
                active.currentRevision.configuration,
            )
            val result = RegisterMedicationPlanUseCase(
                repository = repository,
                nowEpochMillis = { nowEpochMillis },
                newId = ids("duplicate"),
                zoneId = zoneId,
            )(
                newPeriodCommand(
                    name = " pantoprazol ",
                    startsOn = date,
                    endsOn = date.plusDays(5),
                    configuration = configuration,
                    sourceId = null,
                ),
            )

            assertTrue(result is MedicalCareMutationResult.Invalid)
            assertEquals(1, repository.load("person").medicationPlans.size)
            assertTrue(
                (result as MedicalCareMutationResult.Invalid).errors.any {
                    it.issue == MedicalCareValidationIssue.OVERLAPPING_PERIOD
                },
            )
        }

    private fun newPeriodCommand(
        name: String,
        startsOn: LocalDate,
        endsOn: LocalDate?,
        configuration: MedicationConfiguration,
        sourceId: String?,
    ) = RegisterMedicationPlanCommand(
        medicalIndicationId = null,
        personUnderCareId = "person",
        authorActorId = "actor",
        medicationName = name,
        doseAsIndicated = "1 tableta",
        unitOrPresentation = "TABLET",
        frequencyAsIndicated = "Todos los días",
        startsOn = startsOn,
        endsOn = endsOn,
        originalAdditionalInstructions = null,
        explicitSchedules = listOf(LocalTime.of(8, 0)),
        contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
        fictitiousDataAcknowledged = false,
        configuration = configuration.copy(
            startsOn = startsOn,
            endsOn = endsOn,
            status = MedicationTreatmentStatus.ACTIVE,
        ),
        sourceMedicationPlanId = sourceId,
        careProfileId = "profile",
        sensitiveConsentVersion = "test-version",
    )

    private fun lifecycleCommand(
        status: MedicationTreatmentStatus,
        effectiveAt: LocalDateTime = now,
    ) = ChangeMedicationLifecycleCommand(
        medicationPlanId = "plan",
        personUnderCareId = "person",
        authorActorId = "actor",
        targetStatus = status,
        effectiveAt = effectiveAt,
        reason = "Cambio declarado",
        careProfileId = "profile",
        sensitiveConsentVersion = "v1",
    )

    private fun plan(): MedicationPlan {
        val configuration = MedicationConfiguration(
            form = MedicationProductForm.TABLET,
            concentrationValue = null,
            concentrationUnit = null,
            baseAmount = MedicationAmount("1", "tableta"),
            frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = date.minusDays(2),
            administrations = listOf(
                MedicationAdministration(
                    id = "administration-morning",
                    timing = MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(8, 0),
                ),
                MedicationAdministration(
                    id = "administration-evening",
                    timing = MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(12, 0),
                ),
            ),
        )
        return MedicationPlan(
            id = "plan",
            personUnderCareId = "person",
            medicalIndicationId = "indication",
            medicalContentOrigin =
                InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            scheduleOrigin = InformationOrigin.CAREGIVER_RECORD,
            createdAtEpochMillis = 1L,
            createdByActorId = "actor",
            contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
            revisions = listOf(
                MedicationPlanRevision(
                    id = "revision-1",
                    medicationPlanId = "plan",
                    revisionNumber = 1,
                    medicationName = "Medicamento ficticio",
                    doseAsIndicated = "1 tableta",
                    unitOrPresentation = "TABLET",
                    frequencyAsIndicated = "Todos los días",
                    startsOn = configuration.startsOn,
                    endsOn = null,
                    originalAdditionalInstructions = null,
                    scheduledDoses = emptyList(),
                    capturedAtEpochMillis = 1L,
                    authorActorId = "actor",
                    changeKind = MedicationPlanChangeKind.CREATED,
                    changeReason = null,
                    configuration = configuration,
                ),
            ),
        )
    }

    private fun embeddedValidityPlan(
        startsOn: LocalDate,
        endsOn: LocalDate,
    ): MedicationPlan {
        val base = plan()
        val configuration = requireNotNull(base.currentRevision.configuration).copy(
            startsOn = startsOn,
            endsOn = endsOn,
            administrations = listOf(
                MedicationAdministration(
                    id = "administration-0030",
                    timing = MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(0, 30),
                ),
            ),
        )
        return base.copy(
            medicalIndicationId = null,
            revisions = listOf(
                base.currentRevision.copy(
                    startsOn = startsOn,
                    endsOn = endsOn,
                    configuration = configuration,
                ),
            ),
        )
    }

    private fun ids(prefix: String): () -> String {
        var index = 0
        return { "$prefix-${++index}" }
    }
}

private class FakeRepository(initialPlan: MedicationPlan) :
    MedicalCareRepository {
    private val plans = linkedMapOf(initialPlan.id to initialPlan)
    private val indication = MedicalIndication(
        id = "indication",
        personUnderCareId = initialPlan.personUnderCareId,
        origin = InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
        createdAtEpochMillis = 1L,
        createdByActorId = "actor",
        contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
        revisions = listOf(
            MedicalIndicationRevision(
                id = "indication-revision",
                indicationId = "indication",
                revisionNumber = 1,
                originalText = "Indicación ficticia de prueba",
                indicatedOn = initialPlan.currentRevision.startsOn,
                capturedAtEpochMillis = 1L,
                declaredProfessionalOrSource = null,
                validFrom = initialPlan.currentRevision.startsOn,
                validUntil = null,
                status = MedicalIndicationStatus.ACTIVE,
                changeKind = MedicalIndicationChangeKind.CREATED,
                changeReason = null,
                authorActorId = "actor",
            ),
        ),
    )
    private val intakes =
        linkedMapOf<String, MedicationIntakeRecord>()

    override suspend fun load(personUnderCareId: String) =
        MedicalCareOverview(
            indications = listOf(indication),
            medicationPlans = plans.values.toList(),
            medicationIntakes = intakes.values.toList(),
        )

    override suspend fun hasActiveSensitiveDataConsent(
        profileId: String,
        caregiverId: String,
        personUnderCareId: String,
        documentVersion: String,
    ) = true

    override suspend fun appendMedicationPlanRevision(
        medicationPlanId: String,
        expectedRevisionNumber: Int,
        revision: MedicationPlanRevision,
    ) {
        val plan = plans.getValue(medicationPlanId)
        check(plan.currentRevision.revisionNumber == expectedRevisionNumber)
        plans[medicationPlanId] =
            plan.copy(revisions = plan.revisions + revision)
    }

    override suspend fun createMedicationIntake(
        record: MedicationIntakeRecord,
    ) {
        check(intakes.putIfAbsent(record.id, record) == null)
    }

    override suspend fun appendMedicationIntakeRevision(
        recordId: String,
        expectedRevisionNumber: Int,
        revision: MedicationIntakeRevision,
    ) {
        val current = intakes.getValue(recordId)
        check(
            current.currentRevision.revisionNumber ==
                expectedRevisionNumber,
        )
        intakes[recordId] =
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

    override suspend fun createMedicationPlan(plan: MedicationPlan) {
        check(plans.putIfAbsent(plan.id, plan) == null)
    }
}
