package com.bitavida.app.diagnostic

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSnapshotPolicyTest {
    @Test fun excludesAllBinaryValuesAndPrivatePathFieldsRegardlessOfCase() {
        assertFalse(diagnosticColumnAllowed("photo", SQLITE_DATA_BLOB))
        assertFalse(diagnosticColumnAllowed("JPEG", SQLITE_DATA_TEXT))
        assertFalse(diagnosticColumnAllowed("localPath", SQLITE_DATA_NULL))
        assertFalse(diagnosticColumnAllowed("CONTENTURI", SQLITE_DATA_TEXT))
        assertFalse(diagnosticColumnAllowed("snapshot_localPath", SQLITE_DATA_TEXT))
        assertFalse(diagnosticColumnAllowed("originalUri", SQLITE_DATA_TEXT))
        assertTrue(diagnosticColumnAllowed("photoId", SQLITE_DATA_TEXT))
        assertTrue(diagnosticColumnAllowed("payload", SQLITE_DATA_TEXT))
    }

    @Test fun whitelistKeepsRelationshipTablesAndExcludesUnrelatedTables() {
        assertEquals(diagnosticTables.size, diagnosticTables.distinct().size)
        assertTrue(diagnosticTables.contains("medication_intake_revisions"))
        assertTrue(diagnosticTables.contains("medication_plan_revisions"))
        assertTrue(diagnosticTables.contains("observation_record_photos"))
        assertTrue(diagnosticTables.contains("care_profile_name_revisions"))
        assertFalse(diagnosticTables.contains("technical_probe"))
        assertFalse(diagnosticTables.any { it.contains("key", ignoreCase = true) })
    }
}
