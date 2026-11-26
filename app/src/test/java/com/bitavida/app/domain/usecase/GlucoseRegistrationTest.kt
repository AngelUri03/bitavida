package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class GlucoseRegistrationTest {
    private val now = LocalDateTime.of(2026, 11, 16, 9, 30)
    private val actor = GlucoseActor(
        "profile-test",
        "caregiver-test",
        "person-test",
        authorizationCurrent = true,
    )
    private val followUp = MeasurementFollowUp(MeasurementKind.CAPILLARY_GLUCOSE, "Configurado", id = "plan-test")
    private val occurrence = ConfirmedGlucoseOccurrence("occurrence-test", "plan-test", now.minusHours(2), GlucoseContext.FASTING)

    @Test fun contextsRequireAnExplicitOccurrenceAndNeverInferOne() {
        assertEquals(GlucoseRegistrationContext.Independent, glucoseRegistrationContext(null))
        assertEquals(GlucoseRegistrationContext.Independent, glucoseRegistrationContext(followUp.copy(suspended = true)))
        assertTrue(glucoseRegistrationContext(followUp) is GlucoseRegistrationContext.Additional)
        assertEquals(GlucoseRegistrationContext.Scheduled(occurrence), glucoseRegistrationContext(followUp, occurrence))
        assertThrows(IllegalArgumentException::class.java) { glucoseRegistrationContext(null, occurrence) }
        assertThrows(IllegalArgumentException::class.java) { glucoseRegistrationContext(followUp.copy(id = "different"), occurrence) }
    }

    @Test fun scheduledExpectedContextDoesNotReplaceRealContextOrTime() = runBlocking {
        val repository = GlucoseFakeRepository()
        val (record) = SaveGlucoseMeasurementUseCase(repository) { now }(
            actor,
            draft(GlucoseRegistrationContext.Scheduled(occurrence))
                .copy(moment = GlucoseContext.AFTER_MEAL),
        ) as GlucoseSaveResult.Stored
        assertEquals(now.minusMinutes(15), record.occurredAt)
        assertEquals(now, record.capturedAt)
        assertEquals(
            GlucoseContext.AFTER_MEAL,
            (record.value as MeasurementValue.Glucose).context,
        )
        val metadata = requireNotNull(record.glucoseMetadata)
        assertEquals(GlucoseContext.FASTING, metadata.expectedContext)
        assertEquals(occurrence.scheduledAt, metadata.scheduledAt)
        assertEquals(occurrence.id, metadata.scheduledOccurrenceId)
    }

    @Test fun additionalKeepsPlanButNeverOccupiesScheduledSlot() = runBlocking {
        val repository = GlucoseFakeRepository()
        val record = (SaveGlucoseMeasurementUseCase(repository) { now }(
            actor, draft(glucoseRegistrationContext(followUp)),
        ) as GlucoseSaveResult.Stored).record
        val metadata = requireNotNull(record.glucoseMetadata)
        assertEquals(GlucoseEntryKind.ADDITIONAL, metadata.entryKind)
        assertEquals(followUp.id, metadata.followUpId)
        assertNull(metadata.scheduledOccurrenceId)
        assertNull(metadata.scheduledAt)
    }

    @Test fun validatesFormatAndActualTimeWithoutClinicalThresholds() {
        val valid = draft(GlucoseRegistrationContext.Independent)
        listOf("0", "128", "128,5", "128.5", "HI", "lo").forEach { reading ->
            assertTrue(reading, validateGlucoseDraft(valid.copy(reading = reading), now).isEmpty())
        }
        listOf("", "-5", "NaN", "1e3", "12 mg/dL", "12/15", "1.2.3").forEach { reading ->
            assertTrue(reading, GlucoseField.READING in validateGlucoseDraft(valid.copy(reading = reading), now))
        }
        val invalid = valid.copy(moment = null, actualAt = now.plusMinutes(1), note = "x".repeat(501))
        assertEquals(
            setOf(
                GlucoseField.MOMENT,
                GlucoseField.ACTUAL_TIME,
                GlucoseField.NOTE,
            ),
            validateGlucoseDraft(invalid, now),
        )
    }

    @Test fun sensitiveConsentAndCurrentAuthorizationGateWrites() = runBlocking {
        val repository = GlucoseFakeRepository().apply { consent = false }
        val save = SaveGlucoseMeasurementUseCase(repository) { now }
        assertEquals(GlucoseSaveResult.ConsentRequired, save(actor, draft(GlucoseRegistrationContext.Independent)))
        assertEquals(0, repository.creates)
        assertEquals(GlucoseSaveResult.AuthorizationRequired,
            save(actor.copy(authorizationCurrent = false), draft(GlucoseRegistrationContext.Independent)))
        assertEquals(0, repository.creates)
    }

    @Test fun retransmissionIsIdempotentAndOccupiedScheduleIsNeverOverwritten() = runBlocking {
        val repository = GlucoseFakeRepository()
        val save = SaveGlucoseMeasurementUseCase(repository) { now }
        val first = draft(GlucoseRegistrationContext.Scheduled(occurrence))
        assertTrue(save(actor, first) is GlucoseSaveResult.Stored)
        assertTrue(save(actor, first) is GlucoseSaveResult.Stored)
        assertTrue(save(actor, first.copy(id = "second", reading = "139")) is GlucoseSaveResult.AlreadyRecorded)
        assertEquals(1, repository.records.size)
        assertEquals("128,5", (repository.records.single().value as MeasurementValue.Glucose).reading)
    }

    @Test
    fun notPerformedCanBeCorrectedToARealReadingWithAudit() =
        runBlocking {
            val repository = GlucoseFakeRepository()
            val decision = repository.confirmOccurrenceDecision(
                actor,
                GlucoseOccurrenceDecision(
                    id = "decision-test",
                    followUpId = occurrence.followUpId,
                    followUpRevisionId = "follow-up-revision-test",
                    occurrenceId = occurrence.id,
                    scheduledAt = occurrence.scheduledAt,
                    kind =
                        GlucoseOccurrenceDecisionKind.NOT_PERFORMED,
                    reason = "Motivo ficticio",
                    capturedAt = now.minusMinutes(5),
                    actorId = actor.caregiverId,
                ),
            )
            val save = SaveGlucoseMeasurementUseCase(repository) { now }
            val correction = draft(
                GlucoseRegistrationContext.Scheduled(occurrence),
            ).copy(
                id = "corrected-reading",
                correctingDecisionId = decision.id,
            )

            assertTrue(
                save(actor, correction) is GlucoseSaveResult.Stored,
            )
            assertTrue(
                save(actor, correction) is GlucoseSaveResult.Stored,
            )

            val stored = repository.records.single()
            assertEquals(
                occurrence.id,
                stored.glucoseMetadata?.scheduledOccurrenceId,
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
                ).map(GlucoseOccurrenceDecisionRevision::operation),
            )
            assertEquals(1, repository.records.size)
        }

    @Test fun everyMomentAndNonNumericDeviceReadingCanBeTranscribed() = runBlocking {
        val repository = GlucoseFakeRepository()
        val save = SaveGlucoseMeasurementUseCase(repository) { now }
        glucoseMeasurementContexts.forEachIndexed { index, moment ->
            val result = save(actor, draft(GlucoseRegistrationContext.Independent)
                .copy(id = "reading-$index", reading = "lo", moment = moment)) as GlucoseSaveResult.Stored
            val glucose = result.record.value as MeasurementValue.Glucose
            assertEquals("LO", glucose.reading)
            assertEquals(moment, glucose.context)
        }
        val week = projectMeasurementHistoryWeek(repository.records, MeasurementKind.CAPILLARY_GLUCOSE,
            now.toLocalDate(), now.toLocalDate())
        assertEquals(glucoseMeasurementContexts.size, week.records.size)
        assertTrue(week.points.isEmpty())
        assertTrue(
            GlucoseField.MOMENT in validateGlucoseDraft(
                draft(GlucoseRegistrationContext.Independent).copy(
                    moment = GlucoseContext.RESTING,
                ),
                now,
            ),
        )
    }

    @Test
    fun correctionAndLogicalDeletionPreserveAppendOnlyRevisions() = runBlocking {
        val repository = GlucoseFakeRepository()
        val original = (
            SaveGlucoseMeasurementUseCase(repository) { now }(
                actor,
                draft(GlucoseRegistrationContext.Independent),
            ) as GlucoseSaveResult.Stored
            ).record
        val corrected = CorrectGlucoseMeasurementUseCase(repository) {
            now.plusMinutes(5)
        }(
            actor,
            draft(GlucoseRegistrationContext.Independent).copy(
                id = original.id,
                reading = "132",
                note = "Corrección ficticia",
            ),
        ) as GlucoseSaveResult.Stored

        assertEquals("132", (corrected.record.value as MeasurementValue.Glucose).reading)
        assertEquals(
            listOf(
                GlucoseRevisionOperation.CREATED,
                GlucoseRevisionOperation.CORRECTED,
            ),
            repository.loadRevisions(actor, original.id).map(GlucoseRevision::operation),
        )

        assertEquals(
            GlucoseDeleteResult.Deleted,
            DeleteGlucoseMeasurementUseCase(repository) {
                now.plusMinutes(10)
            }(actor, original.id),
        )
        assertTrue(repository.load(actor).isEmpty())
        val revisions = repository.loadRevisions(actor, original.id)
        assertEquals(3, revisions.size)
        assertEquals(GlucoseRevisionOperation.DELETED, revisions.last().operation)
        assertEquals("132", revisions.last().reading)
    }

    private fun draft(context: GlucoseRegistrationContext) = GlucoseDraft(
        "test-reading", context, now.minusMinutes(15), "128,5", GlucoseContext.BEFORE_MEAL, "Nota ficticia",
    )
}

/** Test-only records, never used by the production application. */
internal class GlucoseFakeRepository : GlucoseRepository {
    val records = mutableListOf<MeasurementRecord>()
    private val revisions = mutableMapOf<String, MutableList<GlucoseRevision>>()
    var consent = true
    var creates = 0
    var followUpWrites = 0
    var fail = false
    var beforeCreate: suspend () -> Unit = {}
    var beforeFollowUpWrite: suspend () -> Unit = {}

    override suspend fun load(actor: GlucoseActor): List<MeasurementRecord> {
        if (fail) error("Test storage failure")
        return records.toList()
    }

    override suspend fun loadRevisions(
        actor: GlucoseActor,
        recordId: String,
    ): List<GlucoseRevision> = revisions[recordId].orEmpty().toList()

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
        creates++
        beforeCreate()
        if (fail) error("Test storage failure")
        val scheduledOccurrenceId =
            record.glucoseMetadata?.scheduledOccurrenceId
        records.firstOrNull {
            (it.id == record.id) ||
                (
                    (scheduledOccurrenceId != null) &&
                        (
                            it.glucoseMetadata?.scheduledOccurrenceId ==
                                scheduledOccurrenceId
                            )
                    )
        }?.let { return it }
        records.add(record)
        appendRevision(actor, record, GlucoseRevisionOperation.CREATED)
        return record
    }

    override suspend fun createCorrectingNotPerformed(
        actor: GlucoseActor,
        record: MeasurementRecord,
        decisionRevision: GlucoseOccurrenceDecisionRevision,
    ): MeasurementRecord {
        val stored = create(actor, record)
        if (stored.id != record.id) return stored
        val decision = occurrenceDecisions.single {
            it.occurrenceId == decisionRevision.occurrenceId
        }
        val target = occurrenceDecisionRevisions.getOrPut(
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
        if (fail) error("Test storage failure")
        val index = records.indexOfFirst { it.id == record.id }
        check(index >= 0)
        records[index] = record
        appendRevision(actor, record, GlucoseRevisionOperation.CORRECTED)
        return record
    }

    override suspend fun softDelete(
        actor: GlucoseActor,
        recordId: String,
        deletedAt: LocalDateTime,
    ) {
        if (fail) error("Test storage failure")
        val index = records.indexOfFirst { it.id == recordId }
        check(index >= 0)
        val record = records.removeAt(index).copy(capturedAt = deletedAt)
        appendRevision(actor, record, GlucoseRevisionOperation.DELETED)
    }

    var followUp: GlucoseFollowUp? = null
    val occurrenceDecisions = mutableListOf<GlucoseOccurrenceDecision>()
    val occurrenceDecisionRevisions =
        mutableMapOf<String, MutableList<GlucoseOccurrenceDecisionRevision>>()

    override suspend fun loadFollowUp(actor: GlucoseActor) = followUp

    override suspend fun createFollowUp(
        actor: GlucoseActor,
        followUp: GlucoseFollowUp,
    ): GlucoseFollowUp {
        beforeFollowUpWrite()
        followUpWrites += 1
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
        beforeFollowUpWrite()
        followUpWrites += 1
        val current = requireNotNull(followUp)
        check(current.id == followUpId)
        check(current.currentRevision.revisionNumber == expectedRevisionNumber)
        return current.copy(revisions = current.revisions + revision).also {
            followUp = it
        }
    }

    override suspend fun loadOccurrenceDecisions(
        actor: GlucoseActor,
        followUpId: String,
    ) = occurrenceDecisions.filter { it.followUpId == followUpId }

    override suspend fun confirmOccurrenceDecision(
        actor: GlucoseActor,
        decision: GlucoseOccurrenceDecision,
    ): GlucoseOccurrenceDecision {
        return occurrenceDecisions.firstOrNull {
            it.occurrenceId == decision.occurrenceId
        } ?: decision.also {
            occurrenceDecisions += it
            occurrenceDecisionRevisions[it.occurrenceId] = mutableListOf(
                GlucoseOccurrenceDecisionRevision(
                    id = "${it.id}:revision:1",
                    decisionId = it.id,
                    occurrenceId = it.occurrenceId,
                    revisionNumber = 1,
                    operation = GlucoseOccurrenceDecisionRevisionOperation.CONFIRMED,
                    reason = it.reason,
                    capturedAt = it.capturedAt,
                    actorId = it.actorId,
                ),
            )
        }
    }

    override suspend fun correctOccurrenceDecision(
        actor: GlucoseActor,
        revision: GlucoseOccurrenceDecisionRevision,
    ): GlucoseOccurrenceDecision {
        val index = occurrenceDecisions.indexOfFirst {
            it.occurrenceId == revision.occurrenceId
        }
        check(index >= 0)
        val current = occurrenceDecisions[index]
        val target = occurrenceDecisionRevisions.getOrPut(revision.occurrenceId) {
            mutableListOf()
        }
        val storedRevision = revision.copy(
            decisionId = current.id,
            revisionNumber = target.size + 1,
        )
        target += storedRevision
        return current.copy(
            reason = revision.reason,
            capturedAt = revision.capturedAt,
            actorId = revision.actorId,
        ).also { occurrenceDecisions[index] = it }
    }

    override suspend fun loadOccurrenceDecisionRevisions(
        actor: GlucoseActor,
        occurrenceId: String,
    ): List<GlucoseOccurrenceDecisionRevision> =
        occurrenceDecisionRevisions[occurrenceId].orEmpty().toList()

    private fun appendRevision(
        actor: GlucoseActor,
        record: MeasurementRecord,
        operation: GlucoseRevisionOperation,
    ) {
        val glucose = record.value as MeasurementValue.Glucose
        val target = revisions.getOrPut(record.id) { mutableListOf() }
        target += GlucoseRevision(
            id = "revision-${record.id}-${target.size + 1}",
            recordId = record.id,
            revisionNumber = target.size + 1,
            operation = operation,
            reading = glucose.reading,
            context = requireNotNull(glucose.context),
            actualAt = record.occurredAt,
            note = record.glucoseMetadata?.note,
            actorId = actor.caregiverId,
            capturedAt = record.capturedAt,
        )
    }
}
