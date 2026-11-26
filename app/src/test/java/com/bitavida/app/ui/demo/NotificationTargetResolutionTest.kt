package com.bitavida.app.ui.demo

import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.repository.NotificationHistoryItem
import com.bitavida.app.domain.repository.NotificationPublicationResult
import java.security.MessageDigest
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class NotificationTargetResolutionTest {
    private val time = LocalDateTime.of(2026, 11, 21, 8, 0)
    private fun notice(module: String, token: String?) =
        NotificationHistoryItem("notice", module, "Título ficticio", "Mensaje ficticio", 1L,
            NotificationPublicationResult.PUBLISHED, token, null, null)
    private fun event(kind: TodayActivityKind, id: String, measurementKind: MeasurementKind? = null,
        spontaneous: Boolean = false, recordId: String = "record") = TodayResolvedActivity(
        id = id, kind = kind, title = "Ejemplo ficticio", scheduledAt = time,
        outcome = TodayActivityOutcome.PERFORMED, recordId = recordId,
        sourceId = "source", measurementKind = measurementKind, spontaneous = spontaneous,
    ).let { value -> CareCalendarEvent("resolved:${kind.name}:$id", kind, value.title, null, time,
        "Registrada", value, time, spontaneous) }
    private fun candidate(event: CareCalendarEvent, person: String = "patient") =
        NotificationEventCandidate(person, event)

    @Test fun medicationMatchesOnlyExactSchedulerHashForSamePatient() {
        val token = MessageDigest.getInstance("SHA-256").digest("occurrence:primary".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val real = event(TodayActivityKind.MEDICATION, "occurrence")
        assertEquals(real, resolveNotificationTarget(notice("MEDICATIONS", token), "patient", listOf(candidate(real))))
        assertNull(resolveNotificationTarget(notice("MEDICATIONS", "occurrence"), "patient", listOf(candidate(real))))
        assertNull(resolveNotificationTarget(notice("MEDICATIONS", token), "different", listOf(candidate(real))))
        assertNull(resolveNotificationTarget(notice("MEASUREMENTS", token), "patient", listOf(candidate(real))))
    }

    @Test fun exactMeasurementIdDoesNotConfuseTypeAndAmbiguityIsRejected() {
        val glucose = event(TodayActivityKind.MEASUREMENT, "CAPILLARY_GLUCOSE:occurrence", MeasurementKind.CAPILLARY_GLUCOSE)
        val pressure = event(TodayActivityKind.MEASUREMENT, "BLOOD_PRESSURE:occurrence", MeasurementKind.BLOOD_PRESSURE)
        assertEquals(glucose, resolveNotificationTarget(notice("MEASUREMENTS", "occurrence"), "patient", listOf(candidate(glucose), candidate(glucose))))
        assertNull(resolveNotificationTarget(notice("MEASUREMENTS", "occurrence"), "patient", listOf(candidate(glucose), candidate(pressure))))
        assertNull(resolveNotificationTarget(notice("MEASUREMENTS", "different"), "patient", listOf(candidate(glucose))))
    }

    @Test fun observationsRequireExactIdentityAndSpontaneousRecordsNeverMatch() {
        val scheduled = event(TodayActivityKind.OBSERVATION, "obs-occurrence")
        val independent = event(TodayActivityKind.OBSERVATION, "obs-occurrence", spontaneous = true)
        assertEquals(scheduled, resolveNotificationTarget(notice("OBSERVATIONS", "obs-occurrence"), "patient", listOf(candidate(scheduled))))
        assertNull(resolveNotificationTarget(notice("OBSERVATIONS", "obs-occurrence"), "patient", listOf(candidate(independent))))
        assertNull(resolveNotificationTarget(notice("OBSERVATIONS", null), "patient", listOf(candidate(scheduled))))
        assertNull(resolveNotificationTarget(notice("OBSERVATIONS", "obs-occurrence"), "", listOf(candidate(scheduled))))
    }

    @Test fun conflictingSnapshotsAreNotSilentlyChosen() {
        val first = event(TodayActivityKind.OBSERVATION, "obs", recordId = "one")
        val second = event(TodayActivityKind.OBSERVATION, "obs", recordId = "two")
        assertNull(resolveNotificationTarget(notice("OBSERVATIONS", "obs"), "patient", listOf(candidate(first), candidate(second))))
    }
}
