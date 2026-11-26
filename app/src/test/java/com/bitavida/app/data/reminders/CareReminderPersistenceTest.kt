package com.bitavida.app.data.reminders

import com.bitavida.app.data.local.secure.*
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.*
import com.bitavida.app.domain.usecase.*
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Exercises production DAO transaction bodies with a query double, not a device SQLCipher database. */
class CareReminderPersistenceTest {
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val actor = CareReminderActor("fictional-profile", "fictional-caregiver", "fictional-person", true)
    private fun create(id: String = "reminder") = CareReminderCommand.Create("create-$id", id,
        CareReminderDraft("Recoger documentos ficticios", "Información de ejemplo", now.plusHours(1), true))

    @Test fun mappingRoundTripPreservesEveryFieldAfterReopenProjection() {
        val record = applyCareReminderCommand(emptyList(), actor.personUnderCareId, create(), now)
        assertEquals(record, record.toEntity(actor).toDomain())
        assertEquals(record.copy(details = null, status = CareReminderStatus.DONE, closedAt = now.plusHours(2)),
            record.copy(details = null, status = CareReminderStatus.DONE, closedAt = now.plusHours(2)).toEntity(actor).toDomain())
    }

    @Test fun exactRetriesDoNotDuplicateEvenAfterAnotherVersionWasCommitted() = runBlocking {
        val dao = QueryDouble()
        val command = create()
        val original = dao.apply(actor, command, careReminderRequestFingerprint(command), now)
        val postpone = CareReminderCommand.Postpone("postpone", original.id, 1, now.plusDays(1))
        val updated = dao.apply(actor, postpone, careReminderRequestFingerprint(postpone), now.plusMinutes(1))
        assertEquals(original, dao.apply(actor, command, careReminderRequestFingerprint(command), now.plusDays(2)))
        assertEquals(updated, dao.load(actor).single())
        assertEquals(2, dao.revisions(original.id).size)
        assertEquals(original, dao.revisions(original.id).first().snapshot)
    }

    @Test fun reportHistoryIsAuthorizedAndDoesNotWrite() = runBlocking {
        val dao = QueryDouble()
        val command = create()
        val original = dao.apply(actor, command, careReminderRequestFingerprint(command), now)
        val postpone = CareReminderCommand.Postpone("postpone-report", original.id, 1, now.plusDays(1))
        val updated = dao.apply(actor, postpone, careReminderRequestFingerprint(postpone), now.plusMinutes(1))
        val result = dao.loadReportHistory(actor)
        assertEquals(listOf(updated), result.current)
        assertEquals(listOf(original, updated), result.revisions.map { it.snapshot })
        assertEquals(2, dao.history.size)
        dao.authorized = false
        try { dao.loadReportHistory(actor); fail("Expected authorization") }
        catch (_: CareReminderAuthorizationException) { }
    }

    @Test fun reusedOperationKeyWithDifferentInputIsRejectedWithoutChangingStoredRecord() = runBlocking {
        val dao = QueryDouble()
        val command = create()
        val saved = dao.apply(actor, command, careReminderRequestFingerprint(command), now)
        val changed = command.copy(draft = command.draft.copy(title = "Otro pendiente ficticio"))
        try { dao.apply(actor, changed, careReminderRequestFingerprint(changed), now); fail("Expected conflict") }
        catch (error: CareReminderValidationException) { assertEquals(CareReminderProblem.CONFLICT, error.problem) }
        assertEquals(saved, dao.load(actor).single())
        assertEquals(1, dao.history.size)
    }

    @Test fun limitAndReleasedSlotUseSameProductionTransactionBody() = runBlocking {
        val dao = QueryDouble()
        (1..10).forEach { index ->
            val command = create("$index")
            dao.apply(actor, command, careReminderRequestFingerprint(command), now)
        }
        val extra = create("eleven")
        try { dao.apply(actor, extra, careReminderRequestFingerprint(extra), now); fail("Expected limit") }
        catch (error: CareReminderValidationException) { assertEquals(CareReminderProblem.ACTIVE_LIMIT, error.problem) }
        val close = CareReminderCommand.Close("close", "1", 1, CareReminderStatus.DISCARDED)
        dao.apply(actor, close, careReminderRequestFingerprint(close), now.plusMinutes(1))
        dao.apply(actor, extra, careReminderRequestFingerprint(extra), now.plusMinutes(2))
        assertEquals(11, dao.load(actor).size)
        assertEquals(10, dao.load(actor).count { it.status == "PENDING" })
        assertEquals("DISCARDED", dao.revisions("1").last().snapshot.status)
    }

    @Test fun currentAuthorizationIsRecheckedEvenForAnIdempotentRetry() = runBlocking {
        val dao = QueryDouble()
        val command = create()
        dao.apply(actor, command, careReminderRequestFingerprint(command), now)
        dao.authorized = false
        try { dao.apply(actor, command, careReminderRequestFingerprint(command), now); fail("Expected authorization") }
        catch (_: CareReminderAuthorizationException) { }
        try { dao.load(actor); fail("Expected authorization") }
        catch (_: CareReminderAuthorizationException) { }
        assertEquals(1, dao.history.size)
    }

    @Test fun textLimitsRejectBypassedInputsBeforeRecordOrHistoryWrites() = runBlocking {
        val dao = QueryDouble()
        val oversized = create().copy(draft = create().draft.copy(title = "x".repeat(101)))
        try {
            dao.apply(actor, oversized, careReminderRequestFingerprint(oversized), now)
            fail("Expected title limit")
        } catch (error: CareReminderValidationException) {
            assertEquals(CareReminderProblem.TITLE_TOO_LONG, error.problem)
        }
        assertTrue(dao.current.isEmpty())
        assertTrue(dao.history.isEmpty())
        val command = create()
        val original = dao.apply(actor, command, careReminderRequestFingerprint(command), now)
        val edit = CareReminderCommand.Edit("oversized-details", original.id, original.revision,
            command.draft.copy(details = "x".repeat(501)))
        try {
            dao.apply(actor, edit, careReminderRequestFingerprint(edit), now)
            fail("Expected details limit")
        } catch (error: CareReminderValidationException) {
            assertEquals(CareReminderProblem.DETAILS_TOO_LONG, error.problem)
        }
        assertEquals(original, dao.load(actor).single())
        assertEquals(1, dao.history.size)
    }

    @Test fun requestFingerprintSeparatesNullsDelimitersAndChangedNoticeSettings() {
        val original = create()
        val variants = listOf(original, original.copy(draft = original.draft.copy(details = null)),
            original.copy(draft = original.draft.copy(details = "")),
            original.copy(draft = original.draft.copy(title = "x, details=y")),
            original.copy(draft = original.draft.copy(notificationsEnabled = false)))
        assertEquals(variants.size, variants.map(::careReminderRequestFingerprint).distinct().size)
    }

    private class QueryDouble : CareReminderDao() {
        val current = linkedMapOf<String, CareReminderEntity>()
        val history = linkedMapOf<String, CareReminderRevisionEntity>()
        var authorized = true
        override suspend fun currentRelationship(profile: String, actor: String, person: String) = if (authorized) 1 else 0
        override suspend fun records(profile: String, person: String) =
            current.values.filter { it.profileId == profile && it.personUnderCareId == person }
        override suspend fun operation(operationId: String) = history[operationId]
        override suspend fun revisions(id: String) = history.values.filter { it.reminderId == id }.sortedBy { it.snapshot.revision }
        override suspend fun insert(entity: CareReminderEntity) { check(entity.id !in current); current[entity.id] = entity }
        override suspend fun update(entity: CareReminderEntity): Int {
            if (entity.id !in current) return 0
            current[entity.id] = entity
            return 1
        }
        override suspend fun insertRevision(entity: CareReminderRevisionEntity) {
            check(entity.operationId !in history)
            history[entity.operationId] = entity
        }
    }
}
