package com.bitavida.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Upgrade-only registry decoding; Recordatorios no longer creates Android notification plans. */
class CareReminderPlanningTest {
    @Test fun cancellationCanDecodeBothOpaqueAndEarlierTechnicalAlarmIdentities() {
        val opaque = "a".repeat(64)
        val earlierIdentity = "fictional-reminder:2:2026-11-19T11:00"
        assertEquals(
            setOf(opaque, earlierIdentity),
            careReminderAlarmTokensForRetirement(setOf(
                "$opaque|1795107600000",
                "$earlierIdentity|1795111200000",
            )),
        )
    }

    @Test fun multipleSchedulesForTheSameCapabilityProduceOneCancellation() {
        assertEquals(setOf("fictional-token"),
            careReminderAlarmTokensForRetirement(setOf("fictional-token|1", "fictional-token|2")))
    }

    @Test fun emptyAndUnidentifiableEntriesNeverProduceCapabilities() {
        assertTrue(careReminderAlarmTokensForRetirement(setOf("", "|100", "missing-delimiter")).isEmpty())
        assertTrue(careReminderAlarmTokensForRetirement(emptySet()).isEmpty())
    }

    @Test fun corruptTimestampStillAllowsCancellationOfItsKnownCapability() {
        assertEquals(setOf("fictional-token"), careReminderAlarmTokensForRetirement(setOf("fictional-token|invalid")))
    }
}
