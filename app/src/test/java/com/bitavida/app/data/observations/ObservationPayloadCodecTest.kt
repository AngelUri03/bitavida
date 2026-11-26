package com.bitavida.app.data.observations

import com.bitavida.app.domain.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ObservationPayloadCodecTest {
    @Test fun textLimitsDoNotTruncateUnicodeOrHistoricalPayloads() {
        val now = java.time.LocalDateTime.of(2026, 11, 20, 12, 0)
        val sample = ObservationRecord("fictional-extended", "OBS-027", "fictional-person",
            now.toLocalDate(), null, ObservationTimePrecision.UNKNOWN, ObservationResult.CHANGES,
            mapOf("zone" to listOf("Dato histórico ".repeat(40))),
            "👩‍👩‍👧‍👦".repeat(500) + " Texto histórico ".repeat(100),
            createdAt = now, actorId = "fictional-caregiver")
        assertEquals(sample, ObservationPayloadCodec.decodeRecord(ObservationPayloadCodec.encode(sample)))
    }

    private val date = LocalDate.of(2026, 11, 18)
    private val now = date.atTime(13, 25)

    @Test fun recordRoundTripPreservesDeclaredTimeUnknownTimePhotosAndAssociation() {
        val base = ObservationRecord(
            id = "fictional-record", observationId = "OBS-002", personUnderCareId = "fictional-person",
            occurredOn = date.minusDays(1), occurredAt = LocalTime.of(8, 17),
            timePrecision = ObservationTimePrecision.APPROXIMATE, result = ObservationResult.CHANGES,
            answers = mapOf("zone" to listOf("Pie izquierdo"), "change" to listOf("Ampolla", "Rozadura")),
            description = "Se observó una pequeña ampolla.\nDescripción ficticia.",
            localPhotoIds = listOf("opaque-photo-one", "opaque-photo-two"),
            followUpId = "fictional-followup", occurrenceId = "fictional-event",
            scheduledAt = date.minusDays(1).atTime(8, 0), createdAt = now, actorId = "fictional-caregiver",
        )
        assertEquals(base, ObservationPayloadCodec.decodeRecord(ObservationPayloadCodec.encode(base)))
        val unknown = base.copy(occurredAt = null, timePrecision = ObservationTimePrecision.UNKNOWN,
            followUpId = null, occurrenceId = null, scheduledAt = null, localPhotoIds = emptyList())
        assertEquals(unknown, ObservationPayloadCodec.decodeRecord(ObservationPayloadCodec.encode(unknown)))
    }

    @Test fun scheduleRoundTripPreservesWindowCrossingDayRecurrenceAndSuspensionAudit() {
        val first = GlucoseFollowUpRevision(
            id = "fictional-revision-one", followUpId = "fictional-followup", revisionNumber = 1,
            indication = "Revisión ficticia transcrita", frequency = GlucoseFollowUpFrequency.EVERY_N_DAYS,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), everyNDaysInterval = 3,
            everyNDaysReferenceDate = date, startsOn = date, endsOn = date.plusMonths(1),
            effectiveFrom = date,
            slots = listOf(GlucoseFollowUpSlot(
                id = "fictional-slot", timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
                time = LocalTime.of(23, 0), windowEnd = LocalTime.of(1, 0), endsNextDay = true,
                suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = true,
                reminderTime = LocalTime.of(0, 30), reminderNextDay = true,
            )),
            remindersEnabled = true, changeKind = GlucoseFollowUpChangeKind.CREATED,
            capturedAt = now, authorActorId = "fictional-caregiver",
        )
        val suspended = first.copy(id = "fictional-revision-two", revisionNumber = 2,
            changeKind = GlucoseFollowUpChangeKind.REMOVED, changeReason = "Pausa declarada ficticia",
            capturedAt = now.plusDays(1), effectiveFrom = date.plusDays(1))
        listOf(first, suspended).forEach {
            assertEquals(it, ObservationPayloadCodec.decodeRevision(ObservationPayloadCodec.encode(it)))
        }
    }

    @Test fun rejectsTruncatedVersionUnknownAndTrailingPayloadWithoutInventingFields() {
        val record = ObservationRecord("fictional-record", "OBS-027", "fictional-person", date, null,
            ObservationTimePrecision.UNKNOWN, ObservationResult.EVENT, emptyMap(), "Dato ficticio",
            createdAt = now, actorId = "fictional-caregiver")
        val bytes = Base64.getDecoder().decode(ObservationPayloadCodec.encode(record))
        assertThrows(Exception::class.java) {
            ObservationPayloadCodec.decodeRecord(Base64.getEncoder().encodeToString(bytes.copyOf(bytes.size - 4)))
        }
        val futureVersion = bytes.copyOf().apply { this[3] = 2 }
        assertThrows(IllegalArgumentException::class.java) {
            ObservationPayloadCodec.decodeRecord(Base64.getEncoder().encodeToString(futureVersion))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ObservationPayloadCodec.decodeRecord(Base64.getEncoder().encodeToString(bytes + byteArrayOf(1)))
        }
    }
}
