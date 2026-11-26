package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CareRemindersTest {
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val person = "fictional-person"
    private fun draft(time: LocalDateTime = now.plusHours(1), notification: Boolean = false) =
        CareReminderDraft("Comprar pilas de ejemplo", "Pedido ficticio", time, notification)
    private fun create(id: String = "reminder", rows: List<CareReminder> = emptyList(), time: LocalDateTime = now.plusHours(1)) =
        applyCareReminderCommand(rows, person, CareReminderCommand.Create("create-$id", id, draft(time)), now)
    private fun problem(expected: CareReminderProblem, block: () -> Unit) {
        try { block(); fail("Expected $expected") }
        catch (error: CareReminderValidationException) { assertEquals(expected, error.problem) }
    }

    @Test fun createsFreeTextWithoutClinicalKeywordClassificationAndTrimsOnlyOnSave() {
        val result = applyCareReminderCommand(emptyList(), person, CareReminderCommand.Create("op", "id",
            draft().copy(title = "  Preparar documentos para consulta  ", details = "  ")), now)
        assertEquals("Preparar documentos para consulta", result.title)
        assertNull(result.details)
        assertEquals(CareReminderStatus.PENDING, result.status)
        assertEquals(now, result.createdAt)
        assertEquals(result.createdAt, result.modifiedAt)
        assertEquals(result.scheduledAt, result.originallyScheduledAt)
        assertNull(result.closedAt)
    }

    @Test fun creationRequiresTitleAndStrictlyFutureDateTime() {
        problem(CareReminderProblem.TITLE_REQUIRED) {
            applyCareReminderCommand(emptyList(), person, CareReminderCommand.Create("op", "id", draft().copy(title = "  ")), now)
        }
        listOf(now, now.minusSeconds(1)).forEach { time ->
            problem(CareReminderProblem.FUTURE_REQUIRED) { create(time = time) }
        }
    }

    @Test fun limitsActiveRowsPerPatientAndClosedRowsNeverConsumeSlots() {
        val rows = (1..10).map { create("$it") }
        problem(CareReminderProblem.ACTIVE_LIMIT) { create("eleven", rows) }
        val otherPatient = rows.map { it.copy(personUnderCareId = "other") }
        assertEquals("eleven", create("eleven", otherPatient).id)
        val closed = rows.map { it.copy(status = CareReminderStatus.DONE, closedAt = now) }
        assertEquals("new", create("new", closed).id)
    }

    @Test fun completeAndDiscardRecordExplicitCloseAndBothReleaseCapacity() {
        listOf(CareReminderStatus.DONE, CareReminderStatus.DISCARDED).forEach { status ->
            val rows = (1..10).map { create("$it") }
            val target = rows.first()
            val closed = applyCareReminderCommand(rows, person,
                CareReminderCommand.Close("close-$status", target.id, target.revision, status), now.plusMinutes(2))
            assertEquals(status, closed.status)
            assertEquals(now.plusMinutes(2), closed.closedAt)
            assertEquals(2, closed.revision)
            assertEquals(9, activeCareReminders(rows.drop(1) + closed, person).size)
            assertEquals("replacement", create("replacement", rows.drop(1) + closed).id)
        }
    }

    @Test fun editsAndPostponesKeepIdentityCreationAndOriginalSchedule() {
        val original = create()
        val edited = applyCareReminderCommand(listOf(original), person,
            CareReminderCommand.Edit("edit", original.id, 1, draft(notification = true).copy(title = "Recoger pedido ficticio")), now)
        assertEquals("Recoger pedido ficticio", edited.title)
        assertTrue(edited.notificationsEnabled)
        val postponed = applyCareReminderCommand(listOf(edited), person,
            CareReminderCommand.Postpone("postpone", edited.id, 2, now.plusDays(1)), now.plusMinutes(1))
        assertEquals(original.id, postponed.id)
        assertEquals(original.createdAt, postponed.createdAt)
        assertEquals(original.scheduledAt, postponed.originallyScheduledAt)
        assertEquals(now.plusDays(1), postponed.scheduledAt)
        assertEquals(3, postponed.revision)
    }

    @Test fun pastScheduleStaysPendingAndCanBeEditedWithoutInventingFailure() {
        val past = create().copy(scheduledAt = now.minusHours(1))
        assertTrue(past.hasPastSchedule(now))
        assertEquals(CareReminderStatus.PENDING, activeCareReminders(listOf(past), person).single().status)
        val edited = applyCareReminderCommand(listOf(past), person,
            CareReminderCommand.Edit("edit", past.id, 1, draft(past.scheduledAt).copy(details = "Más información ficticia")), now)
        assertEquals(CareReminderStatus.PENDING, edited.status)
        assertNull(edited.closedAt)
        problem(CareReminderProblem.FUTURE_REQUIRED) {
            applyCareReminderCommand(listOf(past), person,
                CareReminderCommand.Postpone("bad", past.id, 1, now), now)
        }
        problem(CareReminderProblem.FUTURE_REQUIRED) {
            applyCareReminderCommand(listOf(past), person,
                CareReminderCommand.Edit("bad-edit", past.id, 1, draft(now.minusMinutes(1))), now)
        }
    }

    @Test fun rejectsStaleVersionsAndChangesToClosedReminders() {
        val original = create()
        problem(CareReminderProblem.CONFLICT) {
            applyCareReminderCommand(listOf(original), person, CareReminderCommand.Postpone("op", original.id, 5, now.plusDays(1)), now)
        }
        val closed = original.copy(status = CareReminderStatus.DONE, closedAt = now)
        problem(CareReminderProblem.CLOSED) {
            applyCareReminderCommand(listOf(closed), person, CareReminderCommand.Edit("edit", closed.id, 1, draft()), now)
        }
        problem(CareReminderProblem.INVALID_OPERATION) {
            applyCareReminderCommand(listOf(original), person, CareReminderCommand.Close("close", original.id, 1, CareReminderStatus.PENDING), now)
        }
    }

    @Test fun activeCarouselAndPreviousListUseIndependentOrderingAndPatientScope() {
        val later = create("later", time = now.plusDays(1))
        val sooner = create("sooner")
        val done = create("done").copy(status = CareReminderStatus.DONE, closedAt = now.plusMinutes(1))
        val discarded = create("discarded").copy(status = CareReminderStatus.DISCARDED, closedAt = now.plusMinutes(2))
        val foreign = create("foreign").copy(personUnderCareId = "another-person")
        val rows = listOf(later, done, sooner, discarded, foreign, sooner)
        assertEquals(listOf("sooner", "later"), activeCareReminders(rows, person).map { it.id })
        assertEquals(listOf("discarded", "done"), previousCareReminders(rows, person).map { it.id })
    }

    @Test fun explicitNotificationPreferenceNeverChangesReminderScheduleOrState() {
        listOf(false, true).forEach { enabled ->
            val saved = applyCareReminderCommand(emptyList(), person,
                CareReminderCommand.Create("op", "id", draft(notification = enabled)), now)
            assertEquals(enabled, saved.notificationsEnabled)
            assertEquals(draft().scheduledAt, saved.scheduledAt)
            assertEquals(CareReminderStatus.PENDING, saved.status)
        }
    }
}
