package com.bitavida.app.data.notes

import com.bitavida.app.data.local.secure.NoteLegacyImportStatements
import com.bitavida.app.data.local.secure.NoteSchemaStatements
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** DDL/export parity and preservation contract. Real SQLCipher migration/reopen needs device verification. */
class NoteMigrationContractTest {
    @Test fun additiveMigrationMatchesExportedTablesIndexesAndForeignKeys() {
        val path = "schemas/com.bitavida.app.data.local.secure.SecureSpikeDatabase/22.json"
        val schema = listOf(File(path), File("app/$path")).first { it.isFile }.readText()
        val quote = 34.toChar()
        val createSql = Regex("$quote" + "createSql" + "$quote: $quote([^$quote]+)$quote")
        val expected = listOf("notes", "note_revisions", "note_images").flatMap { table ->
            val marker = "$quote" + "tableName" + "$quote: $quote$table$quote"
            val start = schema.indexOf(marker)
            assertTrue("Missing exported $table", start >= 0)
            val next = schema.indexOf("$quote" + "tableName" + "$quote:", start + marker.length)
                .takeIf { it >= 0 } ?: schema.length
            createSql.findAll(schema.substring(start, next))
                .map { it.groupValues[1].replace("$" + "{TABLE_NAME}", table) }.toList()
        }
        assertEquals(13, expected.size)
        assertEquals(expected.map(::normalized).toSet(), NoteSchemaStatements.map(::normalized).toSet())
        assertTrue(NoteSchemaStatements.all { it.startsWith("CREATE ") })
    }

    @Test fun legacyCopyRetainsCurrentFullTextProvenanceAndSourceWithoutChangingMedicalTables() {
        assertEquals(2, NoteLegacyImportStatements.size)
        val copy = NoteLegacyImportStatements.first()
        assertTrue(copy.startsWith("INSERT INTO notes "))
        assertTrue(copy.contains("current.originalText ||"))
        assertTrue(copy.contains("current.revisionNumber = indication.currentRevisionNumber"))
        assertTrue(copy.contains("relationship.profileId"))
        assertTrue(copy.contains("indication.id"))
        assertTrue(copy.contains("Indicación médica transcrita"))
        assertTrue(copy.contains("current.declaredProfessionalOrSource"))
        assertTrue(copy.contains("current.validFromEpochDay"))
        assertTrue(copy.contains("current.validUntilEpochDay"))
        assertTrue(copy.contains("current.changeReason"))
        assertTrue(copy.contains("FROM medication_plans"))
        assertTrue(NoteLegacyImportStatements.last().startsWith("INSERT INTO note_revisions "))
        assertTrue(NoteLegacyImportStatements.last().contains("'IMPORTED'"))
        (NoteSchemaStatements + NoteLegacyImportStatements).forEach { sql ->
            assertFalse(Regex("\\b(DROP|DELETE|UPDATE|REPLACE)\\s+(TABLE|INTO|medical_)",
                RegexOption.IGNORE_CASE).containsMatchIn(sql))
        }
    }

    private fun normalized(sql: String): String =
        sql.replace(96.toChar().toString(), "").replace(Regex("\\s+"), "")
}
