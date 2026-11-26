package com.bitavida.app.data.medical

import com.bitavida.app.data.local.secure.MedicationIntakeRecordEntity
import com.bitavida.app.data.local.secure.MedicationIntakeRevisionEntity
import com.bitavida.app.data.local.secure.sameMedicationIntakeOperation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the predicate used inside the DAO transaction, not Room concurrency on a device. */
class MedicationIntakeOperationIdentityTest {
    private val record = MedicationIntakeRecordEntity("operation:one", "plan", 10, "actor", 1)
    private val original = MedicationIntakeRevisionEntity(
        id = "revision-1", intakeRecordId = record.id, revisionNumber = 1,
        outcome = "COMPLETED", scheduledEpochDay = 20000, scheduledMinute = 480,
        actualEpochDay = 20000, actualMinute = 490, actualAmountValue = "1", actualAmountUnit = "tableta",
        scheduledOccurrenceId = "occurrence", observation = null, changeKind = "REGISTERED",
        changeReason = null, capturedAtEpochMillis = 10, authorActorId = "actor",
    )

    @Test fun onlyExactRequestMayRetryAnOperationKey() {
        val retry = original.copy(id = "another-generated-revision", capturedAtEpochMillis = 20)
        assertTrue(sameMedicationIntakeOperation(record.copy(currentRevisionNumber = 2), original,
            record.copy(createdAtEpochMillis = 20), retry))
        assertFalse(sameMedicationIntakeOperation(record, original, record, retry.copy(outcome = "PARTIAL")))
        assertFalse(sameMedicationIntakeOperation(record, original, record, retry.copy(actualAmountValue = "2")))
        assertFalse(sameMedicationIntakeOperation(record, original, record.copy(medicationPlanId = "different-plan"), retry))
        assertFalse(sameMedicationIntakeOperation(record, original, record.copy(createdByActorId = "other"), retry))
        assertFalse(sameMedicationIntakeOperation(record, original, record, retry.copy(scheduledOccurrenceId = "other")))
        assertFalse(sameMedicationIntakeOperation(record, null, record, retry))
        assertFalse(sameMedicationIntakeOperation(record, original, record.copy(id = "operation:other"), retry))
    }
}
