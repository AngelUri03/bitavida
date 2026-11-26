package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpFrequency
import com.bitavida.app.domain.model.GlucoseFollowUpRevision
import com.bitavida.app.domain.model.GlucoseFollowUpTimingType
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.WeightRegistrationContext
import com.bitavida.app.domain.model.WeightRevision
import com.bitavida.app.domain.model.WeightTarget
import com.bitavida.app.domain.model.confirmedWeight
import com.bitavida.app.domain.model.occurrencesOn
import com.bitavida.app.domain.model.projectWeightToday
import com.bitavida.app.domain.model.toMeasurementFollowUp
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.WeightRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightModuleTest {
    private val now = LocalDateTime.of(2026, 11, 18, 14, 4)
    private val actor = GlucoseActor(
        "profile-weight",
        "caregiver-weight",
        "person-weight",
        authorizationCurrent = true,
    )

    @Test
    fun registrationStoresDecimalWeightWithoutRequiringContextAndOptionalBmi() = runBlocking {
        val repository = WeightFakeRepository()
        val result = SaveWeightMeasurementUseCase(repository) { now }(
            actor,
            WeightDraft(
                id = "weight-1",
                context = WeightRegistrationContext.Independent,
                actualAt = now.minusMinutes(10),
                kilograms = "74,25",
                heightMeters = BigDecimal("1.70"),
                note = "Dato ficticio.",
                firstMeasurement = true,
            ),
        ) as WeightSaveResult.Stored
        val value = result.record.value as MeasurementValue.Weight

        assertEquals(BigDecimal("74.25"), value.kilograms)
        assertEquals(BigDecimal("25.7"), value.bodyMassIndex())
        assertNull(result.record.weightMetadata?.actualContext)
        assertEquals("Dato ficticio.", result.record.weightMetadata?.note)
    }

    @Test
    fun missingHeightDoesNotInventBmiAndFutureOrZeroValuesAreRejected() {
        val value = MeasurementValue.Weight(BigDecimal("72.4"))
        assertNull(value.bodyMassIndex())
        val invalid = validateWeightDraft(
            WeightDraft(
                id = "invalid",
                context = WeightRegistrationContext.Independent,
                actualAt = now.plusMinutes(1),
                kilograms = "0",
            ),
            now,
        )
        assertTrue(WeightField.KILOGRAMS in invalid)
        assertTrue(WeightField.ACTUAL_TIME in invalid)
    }

    @Test
    fun targetCanBeCreatedUpdatedAndRemovedWithoutCreatingMeasurements() =
        runBlocking {
            val repository = WeightFakeRepository()
            val save = SaveWeightTargetUseCase(repository)
            val remove = DeleteWeightTargetUseCase(repository)

            val first = save(actor, "68,5") as
                WeightTargetMutationResult.Stored
            val updated = save(actor, "70.25") as
                WeightTargetMutationResult.Stored

            assertEquals(BigDecimal("68.5"), first.target.kilograms)
            assertEquals(BigDecimal("70.25"), updated.target.kilograms)
            assertEquals(updated.target, repository.loadTarget(actor))
            assertTrue(repository.records.isEmpty())
            assertTrue(repository.revisions.isEmpty())
            assertTrue(repository.decisions.isEmpty())
            assertEquals(
                WeightTargetMutationResult.Invalid,
                save(actor, "70.123"),
            )

            assertEquals(
                WeightTargetMutationResult.Removed,
                remove(actor),
            )
            assertNull(repository.loadTarget(actor))
            assertTrue(repository.records.isEmpty())
            assertTrue(repository.revisions.isEmpty())
            assertTrue(repository.decisions.isEmpty())
        }

    @Test
    fun historyUsesEveryRealWeightAndKeepsSmallDecimalChanges() {
        val records = listOf(
            record("a", "74.2", now.minusDays(2)),
            record("b", "74.4", now.minusDays(1)),
            record("c", "74.3", now),
        )
        val period = projectGlucoseHistoryPeriod(
            records = records,
            anchor = now.toLocalDate(),
            kind = GlucoseHistoryPeriodKind.WEEK,
            today = now.toLocalDate(),
            measurementKind = MeasurementKind.WEIGHT,
        )
        val target = BigDecimal("80")
        val plot = projectGlucoseWeekPlot(
            week = period.history,
            referenceValue = target,
        )

        assertEquals(BigDecimal("74.3"), period.statistics.average)
        assertEquals(BigDecimal("74.2"), period.statistics.minimum)
        assertEquals(BigDecimal("74.4"), period.statistics.maximum)
        assertEquals(3, period.statistics.totalMeasurements)
        assertEquals(3, plot.days.sumOf { it.dots.size })
        assertTrue(requireNotNull(plot.axis).upper > plot.axis.lower)
        assertTrue(plot.axis.upper >= target)
    }

    @Test
    fun followUpSupportsExactAndWindowSlotsWithoutFabricatingOmissions() =
        runBlocking {
            val repository = WeightFakeRepository()
            val stored = SaveWeightFollowUpUseCase(
                repository = repository,
                now = { now },
                newId = ids("weight-follow-up"),
            )(
                actor,
                GlucoseFollowUpDraft(
                    followUpId = "weight-follow-up",
                    indication = "Indicación ficticia transcrita.",
                    frequency = GlucoseFollowUpFrequency.DAILY,
                    effectiveFrom = now.toLocalDate(),
                    remindersEnabled = true,
                    slots = listOf(
                        GlucoseFollowUpSlotDraft(
                            id = "morning",
                            timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                            time = LocalTime.of(8, 0),
                            reminderEnabled = true,
                            reminderTime = LocalTime.of(8, 0),
                            useSameTimeForReminder = true,
                        ),
                        GlucoseFollowUpSlotDraft(
                            id = "night-window",
                            timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
                            time = LocalTime.of(20, 0),
                            windowEnd = LocalTime.of(22, 0),
                            reminderEnabled = true,
                            reminderTime = LocalTime.of(21, 0),
                        ),
                    ),
                ),
            ) as GlucoseFollowUpSaveResult.Stored
            val occurrences = stored.followUp.occurrencesOn(now.toLocalDate())
            val summary = projectWeightToday(
                date = now.toLocalDate(),
                followUp = stored.followUp,
                records = emptyList(),
                decisions = emptyList(),
            )

            assertEquals(2, occurrences.size)
            assertTrue(
                stored.followUp.currentRevision.slots.all {
                    it.suggestedContext == GlucoseContext.UNSPECIFIED
                },
            )
            assertTrue(occurrences.all {
                it.confirmedWeight().expectedContext == null
            })
            assertEquals(
                listOf("08:00", "20:00–22:00"),
                stored.followUp.toMeasurementFollowUp(
                    MeasurementKind.WEIGHT,
                ).moments,
            )
            // Created after 08:00: only the future window is eligible, not a retroactive omission.
            assertEquals(1, summary.scheduled.size)
            assertEquals(LocalTime.of(20, 0), summary.scheduled.single().occurrence.scheduledAt.toLocalTime())
            assertTrue(summary.scheduled.all {
                it.decision == null && it.record == null
            })
            assertTrue(summary.additionalRecords.isEmpty())
        }

    @Test
    fun explicitNotPerformedCanBeCorrectedWithRealAssociatedRecord() =
        runBlocking {
            val repository = WeightFakeRepository()
            val followUp = createFollowUp(repository)
            val occurrence = followUp.occurrencesOn(now.toLocalDate()).single()
            val decision = ConfirmWeightNotPerformedUseCase(
                repository,
                { now },
                ids("decision"),
            )(actor, occurrence, "Motivo ficticio")
            val result = SaveWeightMeasurementUseCase(repository) { now }(
                actor,
                WeightDraft(
                    id = "corrected-weight",
                    context = WeightRegistrationContext.Scheduled(
                        occurrence.confirmedWeight(),
                    ),
                    actualAt = now.minusMinutes(5),
                    kilograms = "73.8",
                    correctingDecisionId = decision.id,
                ),
            ) as WeightSaveResult.Stored

            assertEquals(
                occurrence.id,
                result.record.weightMetadata?.scheduledOccurrenceId,
            )
            assertEquals(1, repository.records.size)
            assertEquals(1, repository.decisionRevisions.size)
            assertEquals(
                occurrence.id,
                repository.decisionRevisions.single().occurrenceId,
            )
        }

    private suspend fun createFollowUp(
        repository: WeightFakeRepository,
    ): GlucoseFollowUp = (
        SaveWeightFollowUpUseCase(
            repository,
            { now },
            ids("follow-up"),
        )(
            actor,
            GlucoseFollowUpDraft(
                followUpId = "weight-follow-up",
                indication = "Indicación ficticia transcrita.",
                frequency = GlucoseFollowUpFrequency.DAILY,
                effectiveFrom = now.toLocalDate(),
                remindersEnabled = false,
                slots = listOf(
                    GlucoseFollowUpSlotDraft(
                        id = "morning",
                        timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                        time = LocalTime.of(8, 0),
                        reminderTime = LocalTime.of(8, 0),
                        useSameTimeForReminder = true,
                    ),
                ),
            ),
        ) as GlucoseFollowUpSaveResult.Stored
        ).followUp

    private fun record(
        id: String,
        kilograms: String,
        at: LocalDateTime,
    ) = MeasurementRecord(
        id = id,
        value = MeasurementValue.Weight(BigDecimal(kilograms)),
        occurredAt = at,
        capturedAt = at,
    )

    private fun ids(prefix: String): () -> String {
        var next = 0
        return { "$prefix-${++next}" }
    }
}

internal class WeightFakeRepository : WeightRepository {
    val records = mutableListOf<MeasurementRecord>()
    val revisions = mutableMapOf<String, MutableList<WeightRevision>>()
    val decisions = mutableListOf<GlucoseOccurrenceDecision>()
    val decisionRevisions =
        mutableListOf<GlucoseOccurrenceDecisionRevision>()
    var followUp: GlucoseFollowUp? = null
    var target: WeightTarget? = null
    var consent = true

    override suspend fun load(actor: GlucoseActor) = records.toList()

    override suspend fun loadTarget(actor: GlucoseActor) = target

    override suspend fun saveTarget(
        actor: GlucoseActor,
        target: WeightTarget,
    ): WeightTarget {
        this.target = target
        return target
    }

    override suspend fun deleteTarget(actor: GlucoseActor) {
        target = null
    }

    override suspend fun hasSensitiveConsent(actor: GlucoseActor) = consent

    override suspend fun acceptSensitiveConsent(
        actor: GlucoseActor,
        acceptedAtEpochMillis: Long,
    ) {
        consent = true
    }

    override suspend fun create(
        actor: GlucoseActor,
        record: MeasurementRecord,
    ): MeasurementRecord {
        val occurrenceId = record.weightMetadata?.scheduledOccurrenceId
        val existing = records.firstOrNull {
            it.id == record.id ||
                (
                    occurrenceId != null &&
                        it.weightMetadata?.scheduledOccurrenceId == occurrenceId
                    )
        }
        if (existing != null) return existing
        records += record
        return record
    }

    override suspend fun createCorrectingNotPerformed(
        actor: GlucoseActor,
        record: MeasurementRecord,
        decisionRevision: GlucoseOccurrenceDecisionRevision,
    ): MeasurementRecord {
        val stored = create(actor, record)
        if (stored.id == record.id) decisionRevisions += decisionRevision
        return stored
    }

    override suspend fun revise(
        actor: GlucoseActor,
        record: MeasurementRecord,
    ): MeasurementRecord {
        val index = records.indexOfFirst { it.id == record.id }
        require(index >= 0)
        records[index] = record
        return record
    }

    override suspend fun softDelete(
        actor: GlucoseActor,
        recordId: String,
        deletedAt: LocalDateTime,
    ) {
        records.removeAll { it.id == recordId }
    }

    override suspend fun loadRevisions(
        actor: GlucoseActor,
        recordId: String,
    ) = revisions[recordId].orEmpty()

    override suspend fun loadFollowUp(actor: GlucoseActor) = followUp

    override suspend fun createFollowUp(
        actor: GlucoseActor,
        followUp: GlucoseFollowUp,
    ): GlucoseFollowUp {
        this.followUp = followUp
        return followUp
    }

    override suspend fun appendFollowUpRevision(
        actor: GlucoseActor,
        followUpId: String,
        expectedRevisionNumber: Int,
        revision: GlucoseFollowUpRevision,
    ): GlucoseFollowUp {
        val current = requireNotNull(followUp)
        require(current.currentRevision.revisionNumber == expectedRevisionNumber)
        return current.copy(revisions = current.revisions + revision)
            .also { followUp = it }
    }

    override suspend fun loadOccurrenceDecisions(
        actor: GlucoseActor,
        followUpId: String,
    ) = decisions.filter { it.followUpId == followUpId }

    override suspend fun confirmOccurrenceDecision(
        actor: GlucoseActor,
        decision: GlucoseOccurrenceDecision,
    ): GlucoseOccurrenceDecision {
        decisions += decision
        return decision
    }

    override suspend fun correctOccurrenceDecision(
        actor: GlucoseActor,
        revision: GlucoseOccurrenceDecisionRevision,
    ): GlucoseOccurrenceDecision {
        decisionRevisions += revision
        return decisions.single { it.id == revision.decisionId }
    }

    override suspend fun loadOccurrenceDecisionRevisions(
        actor: GlucoseActor,
        occurrenceId: String,
    ) = decisionRevisions.filter { it.occurrenceId == occurrenceId }
}
