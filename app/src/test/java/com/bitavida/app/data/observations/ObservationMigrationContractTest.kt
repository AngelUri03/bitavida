package com.bitavida.app.data.observations

import com.bitavida.app.data.local.secure.ObservationSchemaStatements
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compares additive DDL with KSP's exported schema to protect type/index/FK drift.
 * Actual SQLCipher migration/reopen remains an instrumented check.
 */
class ObservationMigrationContractTest {
    @Test fun migrationMatchesExportedTablesAndIndexesWithoutChangingExistingTables() {
        val schemaPath = "schemas/com.bitavida.app.data.local.secure.SecureSpikeDatabase/19.json"
        val schema = listOf(File(schemaPath), File("app/$schemaPath"))
            .first { it.isFile }.readText()
        val quote = 34.toChar()
        val createSql = Regex("$quote" + "createSql" + "$quote: $quote([^$quote]+)$quote")
        val tables = listOf("observation_follow_ups", "observation_follow_up_revisions",
            "observation_records", "observation_photos", "observation_record_photos")
        val expected = tables.flatMap { table ->
            val marker = "$quote" + "tableName" + "$quote: $quote$table$quote"
            val start = schema.indexOf(marker)
            val next = schema.indexOf("$quote" + "tableName" + "$quote:", start + marker.length)
                .takeIf { it >= 0 } ?: schema.length
            createSql.findAll(schema.substring(start, next))
                .map { it.groupValues[1].replace("$" + "{TABLE_NAME}", table) }.toList()
        }
        assertEquals(19, expected.size)
        assertEquals(expected.map(::normalized).toSet(),
            ObservationSchemaStatements.map(::normalized).toSet())
        assertTrue(ObservationSchemaStatements.all { it.startsWith("CREATE ") })
    }

    private fun normalized(sql: String): String = sql
        .replace("id TEXT NOT NULL PRIMARY KEY", "id TEXT NOT NULL")
        .replace(96.toChar().toString(), "")
        .replace(Regex("\\s+"), "")
        .replace("PRIMARYKEY(id),", "")
}
