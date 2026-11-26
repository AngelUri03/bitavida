package com.bitavida.app.data.observations

import com.bitavida.app.data.local.secure.*
import com.bitavida.app.domain.model.*
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class ObservationCorrectionPersistenceTest {
    private val now = LocalDateTime.of(2026, 11, 19, 12, 0)
    private val original = ObservationRecord("fictional-record", "OBS-027", "fictional-person",
        now.toLocalDate(), null, ObservationTimePrecision.UNKNOWN, ObservationResult.CHANGES,
        emptyMap(), "Hecho ficticio original", listOf("fictional-original-photo"),
        createdAt = now.minusDays(1), actorId = "fictional-actor")

    @Test fun reloadingVersionedPayloadsRecoversLatestAndKeepsOriginalDatesAndPhotographs() {
        val changed = original.copy(description = "Hecho ficticio corregido", occurredOn = now.toLocalDate().minusDays(1),
            localPhotoIds = listOf("fictional-new-photo"))
        val row = ObservationRecordEntity(original.id, "fictional-profile", original.actorId,
            original.personUnderCareId, original.observationId, original.occurredOn.toEpochDay(),
            null, null, original.createdAt.toString(), ObservationPayloadCodec.encode(original))
        val correction = ObservationRecordRevisionEntity("fictional-correction", original.id, 2,
            original.actorId, now.toString(), "Motivo ficticio explícito", ObservationPayloadCodec.encode(changed))
        val restored = PersistedObservations(listOf(row), emptyList(), emptyList(), listOf(correction)).toDomain()
        assertEquals(changed, restored.records.single())
        assertEquals(listOf(original, changed), restored.recordRevisions.map { it.snapshot })
        assertEquals(listOf("fictional-original-photo"), restored.recordRevisions.first().snapshot.localPhotoIds)
        assertEquals(listOf("fictional-new-photo"), restored.records.single().localPhotoIds)
        assertNull(restored.recordRevisions.first().reason)
        assertEquals("Motivo ficticio explícito", restored.recordRevisions.last().reason)
    }

    @Test fun malformedRevisionSequenceOrReassignedIdentityCannotBeLoadedAsNormalHistory() {
        val row = ObservationRecordEntity(original.id, "fictional-profile", original.actorId,
            original.personUnderCareId, original.observationId, original.occurredOn.toEpochDay(),
            null, null, original.createdAt.toString(), ObservationPayloadCodec.encode(original))
        val correction = ObservationRecordRevisionEntity("fictional-correction", original.id, 3,
            original.actorId, now.toString(), "Motivo ficticio", ObservationPayloadCodec.encode(original))
        try {
            PersistedObservations(listOf(row), emptyList(), emptyList(), listOf(correction)).toDomain()
            fail("Sequence must be contiguous")
        } catch (_: IllegalStateException) { }
        try {
            PersistedObservations(listOf(row), emptyList(), emptyList(), listOf(correction.copy(revisionNumber = 2,
                payload = ObservationPayloadCodec.encode(original.copy(occurrenceId = "reassigned"))))).toDomain()
            fail("Association must remain pinned")
        } catch (_: IllegalStateException) { }
    }

    @Test fun additiveCorrectionMigrationMatchesExportedSchemaWithoutRewritingExistingFacts() {
        val path = "schemas/com.bitavida.app.data.local.secure.SecureSpikeDatabase/20.json"
        val schema = listOf(File(path), File("app/$path")).first { it.isFile }.readText()
        val quote = 34.toChar()
        val marker = "$quote" + "tableName" + "$quote: $quote" + "observation_record_revisions" + "$quote"
        val start = schema.indexOf(marker)
        require(start >= 0)
        val next = schema.indexOf("$quote" + "tableName" + "$quote:", start + marker.length)
            .takeIf { it >= 0 } ?: schema.length
        val sql = Regex("$quote" + "createSql" + "$quote: $quote([^$quote]+)$quote")
            .findAll(schema.substring(start, next)).map { it.groupValues[1]
                .replace("$" + "{TABLE_NAME}", "observation_record_revisions") }.toList()
        fun normalize(value: String) = value.replace("id TEXT NOT NULL PRIMARY KEY", "id TEXT NOT NULL")
            .replace(96.toChar().toString(), "").replace(Regex("\\s+"), "").replace("PRIMARYKEY(id),", "")
        assertEquals(3, sql.size)
        assertEquals(sql.map(::normalize).toSet(), ObservationCorrectionSchemaStatements.map(::normalize).toSet())
        assertTrue(ObservationCorrectionSchemaStatements.all { it.startsWith("CREATE ") })
    }
}
