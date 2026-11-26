package com.bitavida.app.data.local.secure

import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Test-APK-only helper. The caller must create an empty, private output directory.
 * A caller must treat every failure as an unusable snapshot.
 */
internal object IndependentSnapshotExporter {
    private val preserveSource = DatabaseErrorHandler { _, _ -> }

    fun create(
        source: File,
        sourceKey: ByteArray,
        output: File,
        recoveryKey: PublicKey,
        copyAssociated: ((ByteArray) -> Unit)? = null,
    ) {
        check(source.isFile && sourceKey.size == 32)
        check(output.isDirectory && output.listFiles()?.isEmpty() == true)
        check((recoveryKey as? RSAPublicKey)?.modulus?.bitLength()?.let { it >= 2048 } == true)
        val backupKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val encrypted = File(output, "snapshot.db")
        try {
            val wrap = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            wrap.init(
                Cipher.ENCRYPT_MODE,
                recoveryKey,
                OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT),
            )
            val wrappedBackupKey = wrap.doFinal(backupKey)
            try {
                File(output, "recovery-key.rsa").writeBytes(wrappedBackupKey)
            } finally {
                wrappedBackupKey.fill(0)
            }

            // No WAL flag: this wrapper uses one connection, so data_version
            // remains tied to the same underlying connection throughout.
            open(source, sourceKey, SQLiteDatabase.OPEN_READONLY).use { observer ->
                val before = scalarInt(observer, "PRAGMA data_version")
                val version = scalarInt(observer, "PRAGMA user_version")
                val autoVacuum = scalarInt(observer, "PRAGMA auto_vacuum")
                open(encrypted, backupKey, SQLiteDatabase.CREATE_IF_NECESSARY).use { destination ->
                    // Prevent SQLite from writing unencrypted temp data to disk.
                    destination.execSQL("PRAGMA temp_store = MEMORY")
                    destination.execSQL(
                        "ATTACH DATABASE ? AS source KEY ?",
                        arrayOf(source.toURI().toASCIIString() + "?mode=ro", sourceKey),
                    )
                    check(scalarInt(destination, "PRAGMA source.user_version") == version)
                    check(scalarInt(destination, "PRAGMA source.auto_vacuum") == autoVacuum)
                    destination.execSQL("PRAGMA main.auto_vacuum = $autoVacuum")
                    destination.rawQuery("SELECT sqlcipher_export('main', 'source')", emptyArray()).use {
                        check(it.moveToFirst())
                    }
                    destination.execSQL("PRAGMA main.user_version = $version")
                    SnapshotContentVerifier.verify(destination)
                    check(scalarInt(destination, "PRAGMA source.user_version") == version)
                    check(scalarInt(destination, "PRAGMA source.auto_vacuum") == autoVacuum)
                    destination.execSQL("DETACH DATABASE source")
                }
                open(encrypted, backupKey, SQLiteDatabase.OPEN_READONLY).use { copy ->
                    check(copy.version == version)
                    check(scalarInt(copy, "PRAGMA auto_vacuum") == autoVacuum)
                    copy.rawQuery("PRAGMA integrity_check", emptyArray()).use {
                        check(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext())
                    }
                    copy.rawQuery("PRAGMA cipher_integrity_check", emptyArray()).use {
                        check(!it.moveToFirst())
                    }
                }
                // Capture associated files before the last source change check.
                // Their own capture also verifies that their contents stay stable.
                copyAssociated?.invoke(backupKey)
                // Reject any committed source change observed during the full
                // export and verification interval. This never modifies source.
                check(scalarInt(observer, "PRAGMA data_version") == before)
            }
        } finally {
            backupKey.fill(0)
        }
    }

    private fun open(file: File, key: ByteArray, flags: Int): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.absolutePath, key, null, flags, preserveSource, null)

    private fun scalarInt(database: SQLiteDatabase, sql: String): Int =
        database.rawQuery(sql, emptyArray()).use {
            check(it.moveToFirst())
            it.getInt(0)
        }
}
