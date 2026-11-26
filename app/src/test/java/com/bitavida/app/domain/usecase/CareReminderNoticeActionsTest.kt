package com.bitavida.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CareReminderNoticeActionsTest {
    @Test fun retirementCollectsPendingAlarmsDeliveredReceiptsAndOnlyReminderChannelTags() {
        assertEquals(
            setOf("scheduled-example", "delivered-example", "visible-example"),
            careReminderTokensForRetirement(
                setOf("scheduled-example|1795107600000"),
                setOf("delivered-example"),
                setOf("care:visible-example", "medication:untouched-example", "measurement:untouched-example"),
            ),
        )
    }

    @Test fun aDeliveredCapabilityIsCancelledOnlyOnceWhenAlsoScheduledAndVisible() {
        assertEquals(setOf("fictitious-token"), careReminderTokensForRetirement(
            setOf("fictitious-token|100"), setOf("fictitious-token"), setOf("care:fictitious-token")))
    }

    @Test fun retirementDoesNotMutateItsRegistriesAndCanBeRepeated() {
        val scheduled = mutableSetOf("scheduled-example|100")
        val delivered = mutableSetOf("delivered-example")
        val visible = mutableSetOf("care:visible-example")
        val expected = careReminderTokensForRetirement(scheduled, delivered, visible)
        assertEquals(expected, careReminderTokensForRetirement(scheduled, delivered, visible))
        assertEquals(setOf("scheduled-example|100"), scheduled)
        assertEquals(setOf("delivered-example"), delivered)
        assertEquals(setOf("care:visible-example"), visible)
    }

    @Test fun emptyReceiptsAndUnrelatedTagsAreIgnored() {
        assertTrue(careReminderTokensForRetirement(
            setOf("", "|100"), setOf("", " "), setOf("care:", "care: ", "another:token"),
        ).isEmpty())
    }
}
