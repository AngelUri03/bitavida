package com.bitavida.app.data.local.secure

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import javax.crypto.Cipher
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Explicit developer-only audit of the real independently encrypted snapshot.
 * Runs only under the isolated candidate-audit package with a receiver-specific key.
 * Never opens Room on the returned snapshot or invokes the live database provider.
 * The only writable database is a fresh encrypted file in the audit app's private directory.
 * A supplied APK hash binds this test to the exact installed audit candidate; equivalence
 * with the normal candidate must be checked separately on their compiled classes.
 * The test intentionally retains that copy, including on failure, for forensic review.
 */
@RunWith(AndroidJUnit4::class)
class ReturnedSnapshotMigrationAuditTest {
    @Test
    fun migrateIndependentEncryptedCopyAndCompareOriginalContents(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("bitavida_analysis") == "audit-copy-migration-v1")
        val expectedHash = args.getString("snapshot_sha256")
        check(expectedHash != null && expectedHash.matches(Regex("[0-9a-f]{64}")))
        val expectedSchema = args.getString("snapshot_schema")?.toIntOrNull()
        check(expectedSchema != null && expectedSchema in 1..33)
        val apkHash = args.getString("candidate_apk_sha256")
        val receiverHash = args.getString("receiver_sha256")
        check(apkHash != null && apkHash.matches(Regex("[0-9a-f]{64}")))
        check(receiverHash != null && receiverHash.matches(Regex("[0-9a-f]{64}")))
        val context = instrumentation.targetContext
        check(context.packageName == "com.bitavida.app.audit")
        check(sha256(File(context.applicationInfo.sourceDir)) == apkHash)
        val root = File(requireNotNull(context.getExternalFilesDir(null)), "bitavida-candidate-audit-inbox-v1")
        val source = File(root, "snapshot.db")
        val wrapped = File(root, "candidate-key.rsa")
        var backupKey: ByteArray? = null
        var roomKey: ByteArray? = null
        try {
            check(Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(Files.isRegularFile(wrapped.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(wrapped.length() in 128..1024)
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                check(!Files.exists(File(source.path + suffix).toPath(), LinkOption.NOFOLLOW_LINKS))
            }
            check(sha256(source) == expectedHash)
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val receiverAlias = "com.bitavida.app.test.candidate-audit-receiver-v1"
            val receiverPublic = requireNotNull(store.getCertificate(receiverAlias)).publicKey.encoded
            check(sha256(receiverPublic) == receiverHash)
            val key = store.getKey(receiverAlias, null) as? PrivateKey
                ?: error("Missing candidate receiver key")
            backupKey = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.DECRYPT_MODE, key)
                doFinal(wrapped.readBytes())
            }
            check(backupKey.size == 32)
            Logger.setTarget(NoopTarget())
            System.loadLibrary("sqlcipher")
            openReadOnly(source, backupKey).use { before ->
                check(before.version == expectedSchema)
                verifyIntegrity(before)
            }
            // CREATE_NEW prevents overwriting either the real store or a prior audit.
            val directory = Files.createTempDirectory(context.filesDir.toPath(), "test-migration-audit-")
            val copy = directory.resolve("snapshot-working.db")
            check(copy.toFile().canonicalPath != source.canonicalPath)
            Files.copy(source.toPath(), copy)
            check(sha256(copy.toFile()) == expectedHash)
            check(sha256(source) == expectedHash)
            roomKey = backupKey.copyOf()
            val room = Room.databaseBuilder(context, SecureSpikeDatabase::class.java, copy.toString())
                .setDriver(SQLCipherDriver(roomKey, null, null))
                // Same production chain as SecureDatabaseInitializer. Keep in sync.
                .addMigrations(
                    CareProfileMigration1To2, MedicalCareMigration2To3, MedicationCompletionMigration3To4,
                    MedicationReminderParticipationMigration4To5, MedicationOperationsMigration5To6,
                    MedicationOccurrenceLinkMigration6To7, MedicationLifecycleEffectiveAtMigration7To8,
                    MedicationAdministrationLogicalIdMigration8To9, MedicationPeriodSeriesMigration9To10,
                    GlucoseMigration10To11, GlucoseRevisionMigration11To12, GlucoseFollowUpMigration12To13,
                    GlucoseFollowUpWindowMigration13To14, GlucoseOccurrenceDecisionRevisionMigration14To15,
                    BloodPressureMigration15To16, WeightMigration16To17, WeightTargetMigration17To18,
                    ObservationMigration18To19, ObservationCorrectionMigration19To20,
                    CareReminderMigration20To21, NoteMigration21To22, CareEventMigration22To23,
                    NotificationDeliveryMigration23To24, CareProfileNameMigration24To25,
                    CareEventReminderMigration25To26, CareEventPhotoMigration26To27,
                    CareEventDetailsMigration27To28, CareEventWizardMigration28To29,
                    CareEventCatalogMigration29To30, CareEventPhotoRemovalMigration30To31,
                    CareEventPhotoOrderMigration31To32, CareEventConsultationMigration32To33,
                ).build()
            try {
                // Trigger the actual Room open/migration path. Room may trust its
                // identity hash on same-version opens, so also invoke the generated
                // table/index/foreign-key validator on the isolated copy explicitly.
                room.technicalProbeDao().count()
                val createDelegate = room.javaClass.getDeclaredMethod("createOpenDelegate")
                createDelegate.isAccessible = true
                val delegate = requireNotNull(createDelegate.invoke(room))
                SQLCipherDriver(roomKey, null, null).open(copy.toString()).use { connection ->
                    check(validateGeneratedRoomSchema(delegate, connection))
                }
            } finally {
                room.close()
            }
            openReadOnly(copy.toFile(), backupKey).use { after ->
                check(after.version == 33)
                verifyIntegrity(after)
                after.execSQL("PRAGMA temp_store = MEMORY")
                val attachEncrypted = listOf("ATTACH", "DATABASE", "?", "AS", "baseline", "KEY", "?").joinToString(" ")
                after.execSQL(attachEncrypted, arrayOf(source.toURI().toASCIIString() + "?mode=ro", backupKey))
                comparePersistedContents(after)
                after.execSQL("DETACH DATABASE baseline")
            }
            check(sha256(source) == expectedHash)
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                check(!Files.exists(File(source.path + suffix).toPath(), LinkOption.NOFOLLOW_LINKS))
            }
            // This records only schema numbers and outcome, never clinical rows or keys.
            val result = "source_schema=$expectedSchema\ntarget_schema=33\n" +
                "migration_required=${expectedSchema < 33}\n" +
                "old_tables_and_values=verified\nforeign_keys=verified\nintegrity=verified\n" +
                "generated_room_schema=verified\n" +
                "snapshot_sha256=$expectedHash\naudit_apk_sha256=$apkHash\nreceiver_sha256=$receiverHash\n"
            Files.newOutputStream(
                directory.resolve("result.txt"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
            ).use { it.write(result.toByteArray(Charsets.US_ASCII)) }
        } catch (_: Exception) {
            throw AssertionError("Independent encrypted-copy migration audit failed.")
        } finally {
            roomKey?.fill(0)
            backupKey?.fill(0)
        }
    }

    /**
     * The generated validator is the exact schema contract used by Room migration.
     * Reflection is limited to this test because Room marks its delegate as internal API.
     * Never serialize the validator's detailed result, which may include schema text.
     */
    private suspend fun validateGeneratedRoomSchema(delegate: Any, connection: SQLiteConnection): Boolean {
        val method = delegate.javaClass.getDeclaredMethod(
            "onValidateSchema", SQLiteConnection::class.java, Continuation::class.java,
        )
        method.isAccessible = true
        val result = suspendCoroutineUninterceptedOrReturn<Any> { continuation ->
            val value = method.invoke(delegate, connection, continuation)
            if (value === COROUTINE_SUSPENDED) COROUTINE_SUSPENDED else requireNotNull(value)
        }
        val isValid = result.javaClass.getField("isValid")
        return isValid.getBoolean(result)
    }

    private fun openReadOnly(file: File, key: ByteArray): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.absolutePath, key, null, SQLiteDatabase.OPEN_READONLY,
            DatabaseErrorHandler { _, _ -> }, null)

    private fun verifyIntegrity(database: SQLiteDatabase) {
        database.rawQuery("PRAGMA integrity_check", emptyArray()).use {
            check(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext())
        }
        database.rawQuery("PRAGMA cipher_integrity_check", emptyArray()).use { check(!it.moveToFirst()) }
        database.rawQuery("PRAGMA foreign_key_check", emptyArray()).use { check(!it.moveToFirst()) }
    }

    /**
     * Compare every pre-existing application table by its original primary key, including
     * BLOBs and SQLite storage types. New tables/columns may be added by migration.
     * A missing table, original column, changed value, dropped/added row or FK failure fails
     * the audit; Room's own schema metadata is intentionally excluded.
     */
    private fun comparePersistedContents(database: SQLiteDatabase) {
        val tables = mutableListOf<String>()
        database.rawQuery(
            "SELECT name FROM baseline.sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table' ORDER BY name",
            emptyArray(),
        ).use { cursor -> while (cursor.moveToNext()) tables += cursor.getString(0) }
        check(tables.isNotEmpty())
        tables.forEach { table ->
            val oldColumns = columns(database, "baseline", table)
            val newColumns = columns(database, "main", table)
            check(oldColumns.isNotEmpty() && newColumns.isNotEmpty())
            check(oldColumns.map { it.first }.all { name -> newColumns.any { it.first == name } })
            val keys = oldColumns.filter { it.second > 0 }.sortedBy { it.second }.map { it.first }
            // Explicitly fail rather than guess identities for a table without a declared PK.
            check(keys.isNotEmpty())
            val oldTable = "baseline.${quote(table)}"
            val newTable = "main.${quote(table)}"
            check(scalar(database, "SELECT count(*) FROM $oldTable") ==
                scalar(database, "SELECT count(*) FROM $newTable"))
            val sameKey = keys.joinToString(" AND ") {
                "typeof(a.${quote(it)})=typeof(b.${quote(it)}) AND " +
                    "a.${quote(it)} COLLATE BINARY IS b.${quote(it)} COLLATE BINARY"
            }
            val sameValues = oldColumns.joinToString(" AND ") { (name, _) ->
                "typeof(a.${quote(name)})=typeof(b.${quote(name)}) AND " +
                    "a.${quote(name)} COLLATE BINARY IS b.${quote(name)} COLLATE BINARY"
            }
            database.rawQuery(
                "SELECT 1 FROM $oldTable AS a WHERE NOT EXISTS " +
                    "(SELECT 1 FROM $newTable AS b WHERE $sameKey AND $sameValues) LIMIT 1",
                emptyArray(),
            ).use { check(!it.moveToFirst()) }
        }
    }

    private fun columns(database: SQLiteDatabase, schema: String, table: String): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        database.rawQuery("PRAGMA $schema.table_xinfo(${quote(table)})", emptyArray()).use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            val pk = cursor.getColumnIndexOrThrow("pk")
            val hidden = cursor.getColumnIndexOrThrow("hidden")
            while (cursor.moveToNext()) if (cursor.getInt(hidden) != 1) {
                result += cursor.getString(name) to cursor.getInt(pk)
            }
        }
        return result
    }

    private fun scalar(database: SQLiteDatabase, sql: String): Long =
        database.rawQuery(sql, emptyArray()).use { check(it.moveToFirst()); it.getLong(0) }

    private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
