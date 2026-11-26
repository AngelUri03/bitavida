package com.bitavida.app.ui.report

import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareEventDraft
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.model.CareEventCategory
import com.bitavida.app.domain.model.CareEventPurpose
import com.bitavida.app.domain.model.CareEventStatus
import com.bitavida.app.domain.usecase.FollowUpReport
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportCareEventsSelectionTest {
    private val day = LocalDate.of(2026, 11, 20)
    private val now = day.atTime(12, 0)
    private val report = FollowUpReport(day.minusDays(5), day.plusDays(5), emptyList())

    private fun event(id: String, owner: String, status: CareEventStatus, date: LocalDate) =
        CareEvent(id, owner, CareEventDraft(CareEventKind.APPOINTMENT, "Consulta ficticia",
            scheduledDate = date, status = status, instructions = "Indicación ficticia"),
            day.minusDays(4).atStartOfDay(), now, 1)

    @Test fun distinctIdentitiesRemainAndSameIdentityIsCountedOnce() {
        val first = event("one", "person", CareEventStatus.CONFIRMED, day)
        val second = event("two", "person", CareEventStatus.CONFIRMED, day)
        val foreign = event("foreign", "other", CareEventStatus.CONFIRMED, day)
        val result = reportCareEventsForPeriod(report, listOf(first, first, second, foreign), "person", now)
        assertEquals(listOf("one", "two"), result.map { it.id })
    }

    @Test fun categoriesFollowSavedPurposeWithoutPromotingLegacySpecialty() {
        val legacy = event("legacy", "person", CareEventStatus.CONFIRMED, day).draft
        assertEquals(ReportEventCategory.CONSULTATIONS,
            reportEventCategory(legacy.copy(category = CareEventCategory.SPECIALTY)))
        assertEquals(ReportEventCategory.STUDIES, reportEventCategory(legacy.copy(kind = CareEventKind.STUDY)))
        assertEquals(ReportEventCategory.STUDIES, reportEventCategory(legacy.copy(category = CareEventCategory.IMAGING)))
        assertEquals(ReportEventCategory.PROCEDURES, reportEventCategory(legacy.copy(purpose = CareEventPurpose.PROCEDURE)))
        assertEquals(ReportEventCategory.OTHER, reportEventCategory(legacy.copy(purpose = CareEventPurpose.ADMINISTRATIVE)))
    }

    @Test fun futureConfirmedRemainsPlannedButFutureCompletedIsExcluded() {
        val planned = event("planned", "person", CareEventStatus.CONFIRMED, day.plusDays(1))
        val impossible = event("future-done", "person", CareEventStatus.COMPLETED, day.plusDays(1))
        val result = reportCareEventsForPeriod(report, listOf(planned, impossible), "person", now)
        assertEquals(listOf("planned"), result.map { it.id })
    }
}
