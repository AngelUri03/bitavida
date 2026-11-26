package com.bitavida.app.data.local.secure

import android.database.Cursor
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.JsonWriter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Explicit development-only analysis of a returned encrypted copy.
 * Never opens the live database, Room, a repository, or the original key.
 * Result data and IDs are written only inside the authenticated BVA2 stream.
 */
@RunWith(AndroidJUnit4::class)
class OriginalSnapshotIdentityAnalysisTest {
    private val receiverAlias = "com.bitavida.app.test.returned-snapshot-analysis-v1"

    @Test
    fun prepareReturnDirectory() {
        requireFlag("prepare-return-v1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.bitavida.app")
        val external = requireNotNull(context.getExternalFilesDir(null))
        check(Files.isDirectory(external.toPath(), LinkOption.NOFOLLOW_LINKS))
        val input = File(external, "bitavida-original-analysis-return-v1")
        val publicFile = File(input, "receiver-public.der")
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(!store.containsAlias(receiverAlias))
        var createdDirectory = false
        var createdKey = false
        try {
            // Fail if it already exists; never overwrite a prior return.
            Files.createDirectory(input.toPath())
            createdDirectory = true
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(receiverAlias, KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .build(),
            )
            val publicKey = generator.generateKeyPair().public
            createdKey = true
            // SubjectPublicKeyInfo DER is public; private key stays in Keystore.
            Files.newOutputStream(publicFile.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                .use { it.write(publicKey.encoded) }
        } catch (_: Exception) {
            if (createdKey) store.deleteEntry(receiverAlias)
            if (createdDirectory) {
                publicFile.delete()
                input.delete()
            }
            throw AssertionError("Returned snapshot receiver preparation failed.")
        }
    }

    @Test
    fun cleanupReceiverKey() {
        requireFlag("cleanup-receiver-v1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.bitavida.app")
        val external = requireNotNull(context.getExternalFilesDir(null))
        val input = File(external, "bitavida-original-analysis-return-v1")
        check(Files.isDirectory(input.toPath(), LinkOption.NOFOLLOW_LINKS))
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(receiverAlias)) store.deleteEntry(receiverAlias)
        // Only this public key is test-owned; retain the returned copy and key.
        File(input, "receiver-public.der").delete()
    }

    @Test
    fun verifyReturnedSnapshotReadOnly() {
        requireFlag("verify-returned-v1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.bitavida.app")
        val expectedHash = InstrumentationRegistry.getArguments().getString("snapshot_sha256")
        check(expectedHash != null && expectedHash.matches(Regex("[0-9a-f]{64}")))
        val external = requireNotNull(context.getExternalFilesDir(null))
        val input = File(external, "bitavida-original-analysis-return-v1")
        val copy = File(input, "snapshot.db")
        val encryptedKey = File(input, "analysis-key.rsa")
        var backupKey: ByteArray? = null
        try {
            check(Files.isDirectory(input.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(Files.isRegularFile(copy.toPath(), LinkOption.NOFOLLOW_LINKS))
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(!Files.exists(File(copy.path + suffix).toPath(), LinkOption.NOFOLLOW_LINKS))
            }
            check(Files.isRegularFile(encryptedKey.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(encryptedKey.length() in 128..1024)
            check(sha256(copy) == expectedHash)
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val privateKey = store.getKey(receiverAlias, null) as? PrivateKey ?: error("Missing receiver")
            val unwrap = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            unwrap.init(Cipher.DECRYPT_MODE, privateKey)
            backupKey = unwrap.doFinal(encryptedKey.readBytes())
            check(backupKey.size == 32)
            Logger.setTarget(NoopTarget())
            System.loadLibrary("sqlcipher")
            SQLiteDatabase.openDatabase(
                copy.absolutePath,
                backupKey,
                null,
                SQLiteDatabase.OPEN_READONLY,
                DatabaseErrorHandler { _, _ -> },
                null,
            ).use { database -> verifyDatabase(database) }
            check(sha256(copy) == expectedHash)
        } catch (_: Exception) {
            throw AssertionError("Returned snapshot read-only verification failed.")
        } finally {
            backupKey?.fill(0)
        }
    }

    @Test
    fun analyzeReturnedSnapshot() {
        requireFlag("analyze-returned-v1")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "com.bitavida.app")
        val expectedHash = InstrumentationRegistry.getArguments().getString("snapshot_sha256")
        check(expectedHash != null && expectedHash.matches(Regex("[0-9a-f]{64}")))
        val external = requireNotNull(context.getExternalFilesDir(null))
        val input = File(external, "bitavida-original-analysis-return-v1")
        val copy = File(input, "snapshot.db")
        val encryptedKey = File(input, "analysis-key.rsa")
        val output = File(external, "bitavida-original-analysis-output-v1")
        var backupKey: ByteArray? = null
        var diagnosticKey: ByteArray? = null
        var madeOutput = false
        var success = false
        try {
            check(Files.isDirectory(external.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(Files.isDirectory(input.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(Files.isRegularFile(copy.toPath(), LinkOption.NOFOLLOW_LINKS))
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(!Files.exists(File(copy.path + suffix).toPath(), LinkOption.NOFOLLOW_LINKS))
            }
            check(Files.isRegularFile(encryptedKey.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(encryptedKey.length() in 128..1024)
            check(sha256(copy) == expectedHash)
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val privateKey = store.getKey(receiverAlias, null) as? PrivateKey ?: error("Missing receiver")
            val unwrap = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            unwrap.init(Cipher.DECRYPT_MODE, privateKey)
            backupKey = unwrap.doFinal(encryptedKey.readBytes())
            check(backupKey.size == 32)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(backupKey, "HmacSHA256"))
            diagnosticKey = mac.doFinal("bitavida-identity-diagnostics-v1".toByteArray(Charsets.US_ASCII))

            Files.createDirectory(output.toPath())
            madeOutput = true
            val report = File(output, "identity-diagnostics.bva2")
            Logger.setTarget(NoopTarget())
            System.loadLibrary("sqlcipher")
            SQLiteDatabase.openDatabase(
                copy.absolutePath,
                backupKey,
                null,
                SQLiteDatabase.OPEN_READONLY,
                DatabaseErrorHandler { _, _ -> },
                null,
            ).use { database ->
                verifyDatabase(database)
                val outputStream = Files.newOutputStream(
                    report.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                )
                JsonWriter(OutputStreamWriter(
                    ChunkedEncryptedArchive.encryptTo(outputStream, diagnosticKey), Charsets.UTF_8,
                )).use { json ->
                    writeAnalysis(database, json, expectedHash)
                }
            }
            // Validate every authentication tag and end marker before publishing.
            ChunkedEncryptedArchive.decryptTo(report, diagnosticKey, object : java.io.OutputStream() {
                override fun write(value: Int) = Unit
                override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
            })
            check(sha256(copy) == expectedHash)
            val marker = File(output, "diagnostic.sha256")
            Files.newOutputStream(marker.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use {
                it.write((sha256(report) + "  identity-diagnostics.bva2\n").toByteArray(Charsets.US_ASCII))
            }
            success = true
        } catch (_: Exception) {
            // No exception details, IDs, names, values or SQL are emitted.
            throw AssertionError("Returned snapshot analysis failed.")
        } finally {
            backupKey?.fill(0)
            diagnosticKey?.fill(0)
            if (madeOutput && !success) {
                File(output, "identity-diagnostics.bva2").delete()
                File(output, "diagnostic.sha256").delete()
                output.delete()
            }
        }
    }

    private fun verifyDatabase(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA integrity_check", emptyArray()).use {
            check(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext())
        }
        db.rawQuery("PRAGMA cipher_integrity_check", emptyArray()).use { check(!it.moveToFirst()) }
        for (name in listOf(
            "medication_plans", "medication_plan_revisions",
            "medication_intake_records", "medication_intake_revisions",
        )) {
            check(tableExists(db, name))
        }
        check(setOf("id", "medicationPlanId", "medicationName").all {
            it in columns(db, "medication_plan_revisions")
        })
    }

    private fun writeAnalysis(db: SQLiteDatabase, json: JsonWriter, sourceHash: String) {
        json.beginObject()
        json.name("format").value("bitavida-identity-diagnostics-v1")
        json.name("snapshot_sha256").value(sourceHash)
        json.name("schema_version").value(db.version.toLong())
        json.name("integrity").value("ok")
        var foreignKeyViolations = 0L
        db.rawQuery("PRAGMA foreign_key_check", emptyArray()).use {
            while (it.moveToNext()) foreignKeyViolations++
        }
        json.name("foreign_key_violations").value(foreignKeyViolations)
        json.name("limitations").beginArray()
        json.value("Expected occasions are derived by domain logic and are not stored as a table.")
        json.value("Coinciding names or times do not establish identity or prove an association.")
        json.value("Missing historical fields are marked unavailable; no history is inferred.")
        json.endArray()
        json.name("requested_targets").beginArray()
        for (label in listOf("Test", "Pantoprazol", "Loperamkda")) {
            json.beginObject()
            json.name("requested_label").value(label)
            json.name("plans").beginArray()
            for (id in matchingPlanIds(db, label)) {
                json.beginObject()
                writeRows(db, json, "plan", "medication_plans",
                    listOf("id", "medicationSeriesId", "personUnderCareId", "createdAtEpochMillis", "currentRevisionNumber"),
                    "id = ?", id, "id")
                writeRows(db, json, "plan_revisions", "medication_plan_revisions",
                    listOf("id", "medicationPlanId", "revisionNumber", "medicationName", "doseAsIndicated",
                        "unitOrPresentation", "frequencyAsIndicated", "startsOnEpochDay", "endsOnEpochDay",
                        "capturedAtEpochMillis", "lifecycleEffectiveAtEpochMillis", "changeKind", "changeReason"),
                    "medicationPlanId = ?", id, "revisionNumber")
                val revisionFilter = "medicationPlanRevisionId IN " +
                    "(SELECT id FROM medication_plan_revisions WHERE medicationPlanId = ?)"
                writeRows(db, json, "scheduled_doses", "scheduled_doses",
                    listOf("id", "medicationPlanRevisionId", "localTimeMinutes", "configuredAtEpochMillis", "origin"),
                    revisionFilter, id, "localTimeMinutes")
                writeRows(db, json, "administration_schedules", "medication_administrations",
                    listOf("id", "logicalId", "medicationPlanRevisionId", "timing", "exactTimeMinute",
                        "windowStartMinute", "windowEndMinute", "windowEndsNextDay", "noticeTimeMinute",
                        "noticeNextDay", "amountOverrideValue", "amountOverrideUnit"),
                    revisionFilter, id, "medicationPlanRevisionId")
                writeRows(db, json, "configurations", "medication_configurations",
                    listOf("id", "medicationPlanRevisionId", "form", "baseAmountValue", "baseAmountUnit",
                        "frequency", "weekdaysMask", "everyNDaysInterval", "everyNDaysReferenceEpochDay",
                        "hourlyInterval", "hourlyMode", "hourlyReferenceEpochDay", "hourlyReferenceMinute",
                        "hourlyWindowStartMinute", "hourlyWindowEndMinute", "hourlyWindowEndsNextDay",
                        "hourlyFirstAdministrationMinute", "cycleActiveDays", "cycleRestDays",
                        "cycleReferenceEpochDay", "cycleCount", "asNeededInstruction", "asNeededRestrictions",
                        "startsOnEpochDay", "endsOnEpochDay", "status"),
                    revisionFilter, id, "medicationPlanRevisionId")
                writeRows(db, json, "intake_records", "medication_intake_records",
                    listOf("id", "medicationPlanId", "createdAtEpochMillis", "currentRevisionNumber"),
                    "medicationPlanId = ?", id, "createdAtEpochMillis")
                writeRows(db, json, "intake_revisions", "medication_intake_revisions",
                    listOf("id", "intakeRecordId", "revisionNumber", "outcome", "scheduledEpochDay",
                        "scheduledMinute", "actualEpochDay", "actualMinute", "actualAmountValue",
                        "actualAmountUnit", "scheduledOccurrenceId", "capturedAtEpochMillis",
                        "changeKind", "changeReason"),
                    "intakeRecordId IN (SELECT id FROM medication_intake_records WHERE medicationPlanId = ?)",
                    id, "intakeRecordId, revisionNumber")
                json.endObject()
            }
            json.endArray()
            json.endObject()
        }
        json.endArray()
        json.endObject()
    }

    private fun matchingPlanIds(db: SQLiteDatabase, label: String): List<String> {
        val hasSeries = "medicationSeriesId" in columns(db, "medication_plans")
        val direct = "EXISTS (SELECT 1 FROM medication_plan_revisions r " +
            "WHERE r.medicationPlanId = p.id AND lower(trim(r.medicationName)) = lower(?))"
        val related = if (hasSeries) {
            " OR (p.medicationSeriesId IS NOT NULL AND trim(p.medicationSeriesId) <> '' AND EXISTS " +
                "(SELECT 1 FROM medication_plans q JOIN medication_plan_revisions r " +
                "ON r.medicationPlanId = q.id WHERE q.medicationSeriesId = p.medicationSeriesId " +
                "AND lower(trim(r.medicationName)) = lower(?)))"
        } else ""
        val arguments = if (hasSeries) arrayOf(label, label) else arrayOf(label)
        val result = mutableListOf<String>()
        db.rawQuery("SELECT DISTINCT p.id FROM medication_plans p WHERE $direct$related ORDER BY p.id", arguments)
            .use { cursor -> while (cursor.moveToNext()) result.add(cursor.getString(0)) }
        return result
    }

    private fun writeRows(
        db: SQLiteDatabase,
        json: JsonWriter,
        label: String,
        table: String,
        wanted: List<String>,
        where: String,
        argument: String,
        order: String,
    ) {
        json.name(label).beginObject()
        val available = if (tableExists(db, table)) columns(db, table) else emptySet()
        val selected = wanted.filter { it in available }
        json.name("unavailable_columns").beginArray()
        wanted.filterNot { it in available }.forEach(json::value)
        json.endArray()
        val filterColumnAvailable = where.substringBefore(' ') in available
        val dependenciesAvailable = when {
            where.contains("FROM medication_plan_revisions") ->
                setOf("id", "medicationPlanId").all { it in columns(db, "medication_plan_revisions") }
            where.contains("FROM medication_intake_records") ->
                setOf("id", "medicationPlanId").all { it in columns(db, "medication_intake_records") }
            else -> true
        }
        val filterAvailable = filterColumnAvailable && dependenciesAvailable
        json.name("filter_available").value(filterAvailable)
        json.name("rows").beginArray()
        if (selected.isNotEmpty() && filterAvailable) {
            val orderColumns = order.split(", ").filter { it in available }
            val orderClause = if (orderColumns.isEmpty()) "" else " ORDER BY " +
                orderColumns.joinToString(", ") { quote(it) }
            val projection = selected.joinToString(", ") { quote(it) }
            db.rawQuery("SELECT $projection FROM ${quote(table)} WHERE $where$orderClause", arrayOf(argument))
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        json.beginObject()
                        selected.forEachIndexed { index, column ->
                            json.name(column)
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> json.nullValue()
                                Cursor.FIELD_TYPE_INTEGER -> json.value(cursor.getLong(index))
                                Cursor.FIELD_TYPE_FLOAT -> json.value(cursor.getDouble(index))
                                Cursor.FIELD_TYPE_STRING -> json.value(cursor.getString(index))
                                else -> error("Unsupported diagnostic column type")
                            }
                        }
                        json.endObject()
                    }
                }
        }
        json.endArray()
        json.endObject()
    }

    private fun columns(db: SQLiteDatabase, table: String): Set<String> {
        val result = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(${quote(table)})", emptyArray()).use {
            val index = it.getColumnIndexOrThrow("name")
            while (it.moveToNext()) result.add(it.getString(index))
        }
        return result
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use {
            it.moveToFirst()
        }

    private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        try {
            Files.newInputStream(file.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } finally {
            buffer.fill(0)
        }
    }

    private fun requireFlag(expected: String) {
        check(InstrumentationRegistry.getArguments().getString("bitavida_analysis") == expected)
    }
}
