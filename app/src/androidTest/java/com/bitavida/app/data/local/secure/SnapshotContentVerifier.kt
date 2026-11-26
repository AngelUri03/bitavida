package com.bitavida.app.data.local.secure

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Compares the attached `source` and `main` databases without transferring
 * row contents to the cursor, including complete BLOB values. No row values or
 * table names are reported on failure.
 */
internal object SnapshotContentVerifier {
    fun verify(database: SQLiteDatabase) {
        val schemaProjection = "type, name, tbl_name, coalesce(sql, '')"
        val predicate =
            "type IN ('table','index','view','trigger') AND " +
                "(name NOT LIKE 'sqlite_%' OR name = 'sqlite_sequence')"
        val sourceSchema = "SELECT $schemaProjection FROM source.sqlite_master WHERE $predicate"
        val copySchema = "SELECT $schemaProjection FROM main.sqlite_master WHERE $predicate"
        requireEmpty(database, "SELECT 1 FROM ($sourceSchema EXCEPT $copySchema) LIMIT 1")
        requireEmpty(database, "SELECT 1 FROM ($copySchema EXCEPT $sourceSchema) LIMIT 1")

        val tableNames = mutableListOf<String>()
        database.rawQuery(
            "SELECT name FROM source.sqlite_master WHERE type = 'table' AND " +
                "(name NOT LIKE 'sqlite_%' OR name = 'sqlite_sequence') ORDER BY name",
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) tableNames.add(cursor.getString(0))
        }
        tableNames.forEach { table -> compareTable(database, table) }
    }

    private fun compareTable(database: SQLiteDatabase, table: String) {
        val columns = mutableListOf<Pair<String, Int>>()
        database.rawQuery("PRAGMA source.table_xinfo(${quote(table)})", emptyArray()).use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val keyColumn = cursor.getColumnIndexOrThrow("pk")
            val hiddenColumn = cursor.getColumnIndexOrThrow("hidden")
            while (cursor.moveToNext()) {
                // Virtual-table hidden columns are implementation details, not
                // stored row values; generated visible columns remain compared.
                if (cursor.getInt(hiddenColumn) != 1) {
                    columns.add(cursor.getString(nameColumn) to cursor.getInt(keyColumn))
                }
            }
        }
        check(columns.isNotEmpty())
        val source = "source.${quote(table)}"
        val target = "main.${quote(table)}"
        val declaredKeys = columns.filter { it.second > 0 }.sortedBy { it.second }.map { it.first }
        val keys = when {
            table == "sqlite_sequence" -> listOf("name")
            declaredKeys.isNotEmpty() -> declaredKeys
            else -> {
                val names = columns.map { it.first.lowercase() }.toSet()
                listOf(listOf("rowid", "_rowid_", "oid").first { it !in names })
            }
        }
        val grouping = keys.joinToString(", ") { "${quote(it)} COLLATE BINARY" }
        for (qualified in listOf(source, target)) {
            requireEmpty(
                database,
                "SELECT 1 FROM $qualified GROUP BY $grouping HAVING count(*) > 1 LIMIT 1",
            )
        }
        val sourceCount = scalarLong(database, "SELECT count(*) FROM $source")
        val targetCount = scalarLong(database, "SELECT count(*) FROM $target")
        check(sourceCount == targetCount)

        val identities = keys.joinToString(" AND ") {
            "typeof(s.${quote(it)}) = typeof(d.${quote(it)}) AND " +
                "s.${quote(it)} COLLATE BINARY IS d.${quote(it)} COLLATE BINARY"
        }
        val values = columns.joinToString(" AND ") { (name, _) ->
            "typeof(s.${quote(name)}) = typeof(d.${quote(name)}) AND " +
                "s.${quote(name)} COLLATE BINARY IS d.${quote(name)} COLLATE BINARY"
        }
        requireEmpty(
            database,
            "SELECT 1 FROM $source AS s WHERE NOT EXISTS " +
                "(SELECT 1 FROM $target AS d WHERE $identities AND $values) LIMIT 1",
        )
    }

    private fun quote(identifier: String): String =
        "\"" + identifier.replace("\"", "\"\"") + "\""

    private fun requireEmpty(database: SQLiteDatabase, sql: String) {
        database.rawQuery(sql, emptyArray()).use { cursor -> check(!cursor.moveToFirst()) }
    }

    private fun scalarLong(database: SQLiteDatabase, sql: String): Long =
        database.rawQuery(sql, emptyArray()).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
