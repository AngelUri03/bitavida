package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.BloodPressureEntryKind
import com.bitavida.app.domain.model.BloodPressureRecordMetadata
import com.bitavida.app.domain.model.BloodPressureRegistrationContext
import com.bitavida.app.domain.model.BloodPressureRevision
import com.bitavida.app.domain.model.BloodPressureRevisionOperation
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseFollowUpFrequency
import com.bitavida.app.domain.model.GlucoseFollowUpRevision
import com.bitavida.app.domain.model.GlucoseFollowUpTimingType
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevisionOperation
import com.bitavida.app.domain.model.GlucoseOccurrenceState
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.bloodPressureMeasurementContexts
import com.bitavida.app.domain.model.confirmedBloodPressure
import com.bitavida.app.domain.model.occurrencesOn
import com.bitavida.app.domain.model.projectBloodPressureToday
import com.bitavida.app.domain.repository.BloodPressureRepository
import com.bitavida.app.domain.repository.GlucoseActor
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BloodPressureModuleTest {
    private val now = LocalDateTime.of(2026, 11, 17, 14, 4)
    private val actor = GlucoseActor(
        "profile-test",
        "caregiver-test",
        "person-test",
        authorizationCurrent = true,
    )

    @Test
    fun registrationStoresBothValuesAndActualContextWithoutClinicalRules() =
        runBlocking {
            val repository = BloodPressureFakeRepository()
            val (record) = SaveBloodPressureMeasurementUseCase(
                repository,
            ) { now }(
                actor,
                draft(
                    BloodPressureRegistrationContext.Independent,
                ),
            ) as BloodPressureSaveResult.Stored
            val (systolic, diastolic) =
                record.value as MeasurementValue.BloodPressure

            assertEquals("128", systolic)
            assertEquals("78", diastolic)
            assertEquals(
                GlucoseContext.BEFORE_MEAL,
                record.bloodPressureMetadata?.actualContext,
            )
            assertEquals(
                now.minusMinutes(10),
                record.occurredAt,
            )
            assertTrue(
                validateBloodPressureDraft(
                    draft(
                        BloodPressureRegistrationContext.Independent,
                    ).copy(
                        systolic = "999",
                        diastolic = "1",
                    ),
                    now,
                ).isEmpty(),
            )
        }

    @Test
    fun pressureSpecificMomentsAreValidAndPersistedLiterally() =
        runBlocking {
            val repository = BloodPressureFakeRepository()
            bloodPressureMeasurementContexts
                .takeLast(2)
                .forEachIndexed { index, moment ->
                    val (record) = SaveBloodPressureMeasurementUseCase(
                        repository,
                    ) { now }(
                        actor,
                        draft(
                            BloodPressureRegistrationContext.Independent,
                        ).copy(
                            id = "pressure-context-$index",
                            moment = moment,
                        ),
                    ) as BloodPressureSaveResult.Stored
                    assertEquals(
                        moment,
                        record.bloodPressureMetadata?.actualContext,
                    )
                }
            val followUp = createFollowUp(repository)
            assertEquals(
                listOf(
                    GlucoseContext.FASTING,
                    GlucoseContext.RESTING,
                    GlucoseContext.AFTER_PHYSICAL_ACTIVITY,
                ),
                followUp.currentRevision.slots.map {
                    it.suggestedContext
                },
            )
        }

    @Test
    fun validationRequiresBothNumericValuesAndRejectsFutureTime() {
        val base = draft(
            BloodPressureRegistrationContext.Independent,
        )
        assertEquals(
            setOf(
                BloodPressureField.SYSTOLIC,
                BloodPressureField.DIASTOLIC,
                BloodPressureField.MOMENT,
                BloodPressureField.ACTUAL_TIME,
            ),
            validateBloodPressureDraft(
                base.copy(
                    systolic = "",
                    diastolic = "abc",
                    moment = null,
                    actualAt = now.plusMinutes(1),
                ),
                now,
            ),
        )
    }

    @Test
    fun scheduledAndAdditionalRecordsRemainDistinctAndIdempotent() =
        runBlocking {
            val repository = BloodPressureFakeRepository()
            val followUp = createFollowUp(repository)
            val occurrence = followUp.occurrencesOn(
                now.toLocalDate(),
            ).first()
            val scheduledContext =
                BloodPressureRegistrationContext.Scheduled(
                    occurrence.confirmedBloodPressure(),
                )
            val save =
                SaveBloodPressureMeasurementUseCase(repository) { now }

            assertTrue(
                save(actor, draft(scheduledContext)) is
                    BloodPressureSaveResult.Stored,
            )
            assertTrue(
                save(
                    actor,
                    draft(scheduledContext).copy(id = "duplicate"),
                ) is BloodPressureSaveResult.AlreadyRecorded,
            )
            val additional = save(
                actor,
                draft(
                    BloodPressureRegistrationContext.Additional(
                        followUp.id,
                    ),
                ).copy(id = "additional"),
            ) as BloodPressureSaveResult.Stored
            val today = projectBloodPressureToday(
                now.toLocalDate(),
                followUp,
                repository.records,
                emptyList(),
            )

            assertEquals(
                GlucoseOccurrenceState.RECORDED,
                today.scheduled.first().state,
            )
            assertEquals(
                listOf(additional.record),
                today.additionalRecords,
            )
            assertEquals(2, repository.records.size)
        }

    @Test
    fun notPerformedCanBeCorrectedToARealPairWithAudit() =
        runBlocking {
            val repository = BloodPressureFakeRepository()
            val followUp = createFollowUp(repository)
            val occurrence = followUp.occurrencesOn(
                now.toLocalDate(),
            )[1]
            val decision = ConfirmBloodPressureNotPerformedUseCase(
                repository,
                { now },
            ) { "decision-corrected" }(
                actor,
                occurrence,
                "Motivo ficticio",
            )
            val correction = draft(
                BloodPressureRegistrationContext.Scheduled(
                    occurrence.confirmedBloodPressure(),
                ),
            ).copy(
                id = "corrected-pressure",
                correctingDecisionId = decision.id,
            )
            val save =
                SaveBloodPressureMeasurementUseCase(repository) { now }

            assertTrue(
                save(actor, correction) is
                    BloodPressureSaveResult.Stored,
            )
            assertTrue(
                save(actor, correction) is
                    BloodPressureSaveResult.Stored,
            )

            val stored = repository.records.single()
            assertEquals(
                occurrence.id,
                stored.bloodPressureMetadata?.scheduledOccurrenceId,
            )
            assertEquals(
                listOf(
                    GlucoseOccurrenceDecisionRevisionOperation.CONFIRMED,
                    GlucoseOccurrenceDecisionRevisionOperation
                        .CORRECTED_TO_RECORDED,
                ),
                repository.loadOccurrenceDecisionRevisions(
                    actor,
                    occurrence.id,
                ).map(
                    GlucoseOccurrenceDecisionRevision::operation,
                ),
            )
            val period = projectBloodPressureHistoryPeriod(
                records = repository.records,
                anchor = now.toLocalDate(),
                kind = GlucoseHistoryPeriodKind.WEEK,
                today = now.toLocalDate(),
                followUp = followUp,
                decisions = listOf(decision),
                now = now,
            )
            val events = period.days.first {
                it.date == now.toLocalDate()
            }.events
            assertTrue(
                events.any {
                    (it as? GlucoseHistoryEvent.Registered)
                        ?.record
                        ?.bloodPressureMetadata
                        ?.scheduledOccurrenceId == occurrence.id
                },
            )
            assertFalse(
                events.any {
                    (it as? GlucoseHistoryEvent.NotPerformed)
                        ?.occurrence?.id == occurrence.id
                },
            )
            assertEquals(1, period.statistics.totalMeasurements)
            assertEquals(1, repository.records.size)
        }

    @Test
    fun weeklyAndMonthlyPlotsKeepEveryPairAndDescriptiveStatistics() {
        val records = listOf(
            record("one", now.withHour(8), "128", "78"),
            record("two", now.withHour(12), "140", "82"),
            record("three", now.plusDays(1), "120", "74"),
        )
        val week = projectMeasurementHistoryWeek(
            records,
            MeasurementKind.BLOOD_PRESSURE,
            now.toLocalDate(),
            now.toLocalDate(),
        )
        val plot = projectBloodPressurePlot(week)
        val statistics = calculateBloodPressureStatistics(records)
        val month = projectBloodPressureHistoryPeriod(
            records,
            now.toLocalDate(),
            GlucoseHistoryPeriodKind.MONTH,
            now.toLocalDate(),
        )

        assertEquals(
            2,
            plot.days.first {
                it.day.date == now.toLocalDate()
            }.readings.size,
        )
        assertEquals(6, week.points.size)
        assertEquals(BigDecimal("129.3"), statistics.averageSystolic)
        assertEquals(BigDecimal("78.0"), statistics.averageDiastolic)
        assertEquals(BigDecimal("120"), statistics.minimumSystolic)
        assertEquals(BigDecimal("140"), statistics.maximumSystolic)
        assertEquals(30, month.history.days.size)
    }

    @Test
    fun historyIncludesExplicitAndUnconfirmedEventsButNotInStatistics() =
        runBlocking {
            val repository = BloodPressureFakeRepository()
            val followUp = createFollowUp(repository, capturedAt = now.withHour(6).withMinute(0))
            val occurrences = followUp.occurrencesOn(now.toLocalDate())
            val registered = record(
                "registered",
                occurrences.first().scheduledAt.plusMinutes(2),
                "125",
                "76",
                occurrenceId = occurrences.first().id,
                followUpId = followUp.id,
            )
            repository.records += registered
            val decision = ConfirmBloodPressureNotPerformedUseCase(
                repository,
                { now },
            ) { "decision" }(
                actor,
                occurrences[1],
                "Motivo ficticio",
            )
            val period = projectBloodPressureHistoryPeriod(
                records = repository.records,
                anchor = now.toLocalDate(),
                kind = GlucoseHistoryPeriodKind.WEEK,
                today = now.toLocalDate(),
                followUp = followUp,
                decisions = listOf(decision),
                now = now.plusHours(2),
            )
            val events = period.days.first {
                it.date == now.toLocalDate()
            }.events

            assertTrue(
                events.any {
                    it is GlucoseHistoryEvent.Registered
                },
            )
            assertTrue(
                events.any {
                    it is GlucoseHistoryEvent.NotPerformed
                },
            )
            assertTrue(
                events.any {
                    it is GlucoseHistoryEvent.Unconfirmed
                },
            )
            assertEquals(1, period.statistics.totalMeasurements)
        }

    @Test
    fun correctionsDeletionAndFollowUpDeactivationRemainAuditable() =
        runBlocking {
            val repository = BloodPressureFakeRepository()
            val saved = (
                SaveBloodPressureMeasurementUseCase(
                    repository,
                ) { now }(
                    actor,
                    draft(
                        BloodPressureRegistrationContext.Independent,
                    ),
                ) as BloodPressureSaveResult.Stored
                ).record
            CorrectBloodPressureMeasurementUseCase(
                repository,
            ) { now.plusMinutes(1) }(
                actor,
                draft(
                    BloodPressureRegistrationContext.Independent,
                ).copy(
                    id = saved.id,
                    systolic = "130",
                    diastolic = "80",
                ),
            )
            DeleteBloodPressureMeasurementUseCase(
                repository,
            ) { now.plusMinutes(2) }(actor, saved.id)

            val followUp = createFollowUp(repository)
            val removedDraft = followUp.toDraft(
                GlucoseFollowUpChangeKind.REMOVED,
                now.toLocalDate(),
            )
            val removed = SaveBloodPressureFollowUpUseCase(
                repository,
                { now.plusMinutes(3) },
                ids("remove"),
            )(
                actor,
                removedDraft,
            ) as GlucoseFollowUpSaveResult.Stored

            assertEquals(
                listOf(
                    BloodPressureRevisionOperation.CREATED,
                    BloodPressureRevisionOperation.CORRECTED,
                    BloodPressureRevisionOperation.DELETED,
                ),
                repository.loadRevisions(
                    actor,
                    saved.id,
                ).map(BloodPressureRevision::operation),
            )
            assertTrue(repository.load(actor).isEmpty())
            assertFalse(removed.followUp.currentRevision.active)
            assertEquals(2, removed.followUp.revisions.size)
            assertEquals(
                GlucoseFollowUpChangeKind.REMOVED,
                removed.followUp.currentRevision.changeKind,
            )
        }

    private suspend fun createFollowUp(
        repository: BloodPressureFakeRepository,
        capturedAt: LocalDateTime = now,
    ): GlucoseFollowUp = (
        SaveBloodPressureFollowUpUseCase(
            repository,
            { capturedAt },
            ids("follow-up"),
        )(
            actor,
            GlucoseFollowUpDraft(
                followUpId = "pressure-follow-up",
                indication =
                    "Indicación médica ficticia transcrita.",
                frequency = GlucoseFollowUpFrequency.DAILY,
                effectiveFrom = now.toLocalDate(),
                remindersEnabled = false,
                slots = listOf(
                    GlucoseFollowUpSlotDraft(
                        id = "morning",
                        timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                        time = LocalTime.of(8, 0),
                        context = GlucoseContext.FASTING,
                        reminderEnabled = false,
                        reminderTime = LocalTime.of(8, 0),
                        useSameTimeForReminder = true,
                    ),
                    GlucoseFollowUpSlotDraft(
                        id = "midday",
                        timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                        time = LocalTime.of(13, 0),
                        context = GlucoseContext.RESTING,
                        reminderEnabled = false,
                        reminderTime = LocalTime.of(13, 0),
                        useSameTimeForReminder = true,
                    ),
                    GlucoseFollowUpSlotDraft(
                        id = "afternoon",
                        timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                        time = LocalTime.of(14, 0),
                        context = GlucoseContext.AFTER_PHYSICAL_ACTIVITY,
                        reminderEnabled = false,
                        reminderTime = LocalTime.of(14, 0),
                        useSameTimeForReminder = true,
                    ),
                ),
            ),
        ) as GlucoseFollowUpSaveResult.Stored
        ).followUp

    private fun draft(context: BloodPressureRegistrationContext) =
        BloodPressureDraft(
            id = "pressure-record",
            context = context,
            actualAt = now.minusMinutes(10),
            systolic = "128",
            diastolic = "78",
            moment = GlucoseContext.BEFORE_MEAL,
            note = "Nota ficticia",
        )

    private fun record(
        id: String,
        at: LocalDateTime,
        systolic: String,
        diastolic: String,
        occurrenceId: String? = null,
        followUpId: String? = null,
    ) = MeasurementRecord(
        id = id,
        value = MeasurementValue.BloodPressure(
            systolic,
            diastolic,
        ),
        occurredAt = at,
        capturedAt = at,
        bloodPressureMetadata = BloodPressureRecordMetadata(
            entryKind = if (occurrenceId == null) {
                BloodPressureEntryKind.INDEPENDENT
            } else {
                BloodPressureEntryKind.SCHEDULED
            },
            followUpId = followUpId,
            scheduledOccurrenceId = occurrenceId,
            scheduledAt = at,
            actualContext = GlucoseContext.BEFORE_MEAL,
        ),
    )

    private fun ids(prefix: String): () -> String {
        var value = 0
        return { prefix + "-" + (++value) }
    }
}

internal class BloodPressureFakeRepository :
    BloodPressureRepository {
    val records = mutableListOf<MeasurementRecord>()
    private val revisions =
        mutableMapOf<String, MutableList<BloodPressureRevision>>()
    private var followUp: GlucoseFollowUp? = null
    private val decisions =
        mutableListOf<GlucoseOccurrenceDecision>()
    private val decisionRevisions =
        mutableMapOf<
            String,
            MutableList<GlucoseOccurrenceDecisionRevision>,
            >()
    var consent = true

    override suspend fun load(actor: GlucoseActor) =
        records.toList()

    override suspend fun loadRevisions(
        actor: GlucoseActor,
        recordId: String,
    ) = revisions[recordId].orEmpty().toList()

    override suspend fun hasSensitiveConsent(
        actor: GlucoseActor,
    ) = consent

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
        val occurrenceId =
            record.bloodPressureMetadata?.scheduledOccurrenceId
        records.firstOrNull {
            (it.id == record.id) ||
                (
                    (occurrenceId != null) &&
                        (
                            it.bloodPressureMetadata
                                ?.scheduledOccurrenceId == occurrenceId
                            )
                    )
        }?.let { return it }
        records += record
        appendRevision(
            actor,
            record,
            BloodPressureRevisionOperation.CREATED,
        )
        return record
    }

    override suspend fun createCorrectingNotPerformed(
        actor: GlucoseActor,
        record: MeasurementRecord,
        decisionRevision: GlucoseOccurrenceDecisionRevision,
    ): MeasurementRecord {
        val stored = create(actor, record)
        if (stored.id != record.id) return stored
        val decision = decisions.single {
            it.occurrenceId == decisionRevision.occurrenceId
        }
        val target = decisionRevisions.getOrPut(
            decisionRevision.occurrenceId,
        ) { mutableListOf() }
        if (target.none { it.id == decisionRevision.id }) {
            target += decisionRevision.copy(
                decisionId = decision.id,
                revisionNumber = target.size + 1,
            )
        }
        return stored
    }

    override suspend fun revise(
        actor: GlucoseActor,
        record: MeasurementRecord,
    ): MeasurementRecord {
        val index = records.indexOfFirst { it.id == record.id }
        check(index >= 0)
        records[index] = record
        appendRevision(
            actor,
            record,
            BloodPressureRevisionOperation.CORRECTED,
        )
        return record
    }

    override suspend fun softDelete(
        actor: GlucoseActor,
        recordId: String,
        deletedAt: LocalDateTime,
    ) {
        val index = records.indexOfFirst { it.id == recordId }
        check(index >= 0)
        val record = records.removeAt(index).copy(
            capturedAt = deletedAt,
        )
        appendRevision(
            actor,
            record,
            BloodPressureRevisionOperation.DELETED,
        )
    }

    override suspend fun loadFollowUp(
        actor: GlucoseActor,
    ) = followUp

    override suspend fun createFollowUp(
        actor: GlucoseActor,
        followUp: GlucoseFollowUp,
    ): GlucoseFollowUp {
        check(this.followUp == null)
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
        check(
            current.currentRevision.revisionNumber ==
                expectedRevisionNumber,
        )
        return current.copy(
            revisions = current.revisions + revision,
        ).also { followUp = it }
    }

    override suspend fun loadOccurrenceDecisions(
        actor: GlucoseActor,
        followUpId: String,
    ) = decisions.filter { it.followUpId == followUpId }

    override suspend fun confirmOccurrenceDecision(
        actor: GlucoseActor,
        decision: GlucoseOccurrenceDecision,
    ): GlucoseOccurrenceDecision {
        decisions.firstOrNull {
            it.occurrenceId == decision.occurrenceId
        }?.let { return it }
        decisions += decision
        decisionRevisions[decision.occurrenceId] =
            mutableListOf(
                GlucoseOccurrenceDecisionRevision(
                    id = decision.id + ":revision:1",
                    decisionId = decision.id,
                    occurrenceId = decision.occurrenceId,
                    revisionNumber = 1,
                    operation =
                        GlucoseOccurrenceDecisionRevisionOperation
                            .CONFIRMED,
                    reason = decision.reason,
                    capturedAt = decision.capturedAt,
                    actorId = decision.actorId,
                ),
            )
        return decision
    }

    override suspend fun correctOccurrenceDecision(
        actor: GlucoseActor,
        revision: GlucoseOccurrenceDecisionRevision,
    ): GlucoseOccurrenceDecision {
        val index = decisions.indexOfFirst {
            it.occurrenceId == revision.occurrenceId
        }
        check(index >= 0)
        val current = decisions[index]
        val target = decisionRevisions.getOrPut(
            revision.occurrenceId,
        ) { mutableListOf() }
        target += revision.copy(
            decisionId = current.id,
            revisionNumber = target.size + 1,
        )
        return current.copy(
            reason = revision.reason,
            capturedAt = revision.capturedAt,
        ).also { decisions[index] = it }
    }

    override suspend fun loadOccurrenceDecisionRevisions(
        actor: GlucoseActor,
        occurrenceId: String,
    ) = decisionRevisions[occurrenceId].orEmpty().toList()

    private fun appendRevision(
        actor: GlucoseActor,
        record: MeasurementRecord,
        operation: BloodPressureRevisionOperation,
    ) {
        val value =
            record.value as MeasurementValue.BloodPressure
        val metadata = requireNotNull(
            record.bloodPressureMetadata,
        )
        val target = revisions.getOrPut(record.id) {
            mutableListOf()
        }
        target += BloodPressureRevision(
            id = record.id + ":revision:" + (target.size + 1),
            recordId = record.id,
            revisionNumber = target.size + 1,
            operation = operation,
            systolic = value.systolic,
            diastolic = value.diastolic,
            context = metadata.actualContext,
            actualAt = record.occurredAt,
            note = metadata.note,
            actorId = actor.caregiverId,
            capturedAt = record.capturedAt,
        )
    }
}
