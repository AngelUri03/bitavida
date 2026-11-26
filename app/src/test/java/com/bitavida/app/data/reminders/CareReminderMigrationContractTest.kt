package com.bitavida.app.data.reminders

import com.bitavida.app.data.local.secure.CareReminderSchemaStatements
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Checks schema/DDL parity. SQLCipher migration and encrypted reopen remain device verification. */
class CareReminderMigrationContractTest {
    @Test fun additiveMigrationMatchesExportedTablesIndexesAndForeignKeys() {
        val path = "schemas/com.bitavida.app.data.local.secure.SecureSpikeDatabase/21.json"
        val schema = listOf(File(path), File("app/$path")).first { it.isFile }.readText()
        val quote = 34.toChar()
        val createSql = Regex("$quote" + "createSql" + "$quote: $quote([^$quote]+)$quote")
        val expected = listOf("care_reminders", "care_reminder_revisions").flatMap { table ->
            val marker = "$quote" + "tableName" + "$quote: $quote$table$quote"
            val start = schema.indexOf(marker)
            assertTrue(start >= 0)
            val next = schema.indexOf("$quote" + "tableName" + "$quote:", start + marker.length)
                .takeIf { it >= 0 } ?: schema.length
            createSql.findAll(schema.substring(start, next))
                .map { it.groupValues[1].replace("$" + "{TABLE_NAME}", table) }.toList()
        }
        assertEquals(7, expected.size)
        assertEquals(expected.map(::normalized).toSet(), CareReminderSchemaStatements.map(::normalized).toSet())
        assertTrue(CareReminderSchemaStatements.all { it.startsWith("CREATE ") })
    }

    private fun normalized(sql: String): String =
        sql.replace(96.toChar().toString(), "").replace(Regex("\\s+"), "")
}
