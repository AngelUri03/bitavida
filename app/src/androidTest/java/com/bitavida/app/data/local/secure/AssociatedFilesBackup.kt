package com.bitavida.app.data.local.secure

import android.util.Base64
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Copies associated files to one encrypted archive, never a plaintext archive.
 * Its manifest contains paths, sizes and hashes and is encrypted together with
 * the contents. Fails closed on symlinks or files that change during capture.
 */
internal object AssociatedFilesBackup {
    private data class Entry(val root: String, val relative: String, val file: Path)
    private data class DigestEntry(val root: String, val relative: String, val size: Long, val sha: String)

    fun create(roots: Map<String, File>, output: File, masterKey: ByteArray) {
        val excluded = requireNotNull(output.parentFile).toPath().toAbsolutePath().normalize()
        val before = enumerate(roots, excluded)
        val encryptionKey = deriveKey(masterKey)
        try {
            output.outputStream().use { raw ->
                ZipOutputStream(ChunkedEncryptedArchive.encryptTo(raw, encryptionKey)).use { archive ->
                    val captured = before.map { entry ->
                        val pathToken = Base64.encodeToString(
                            entry.relative.toByteArray(Charsets.UTF_8),
                            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                        )
                        archive.putNextEntry(ZipEntry("files/${entry.root}/$pathToken"))
                        val result = digest(entry) { buffer, count -> archive.write(buffer, 0, count) }
                        archive.closeEntry()
                        result
                    }
                    val after = enumerate(roots, excluded)
                    check(before.map { it.root to it.relative } == after.map { it.root to it.relative })
                    val verified = after.map { digest(it, null) }
                    check(captured == verified)
                    archive.putNextEntry(ZipEntry("bitavida-inventory.tsv"))
                    captured.forEach { item ->
                        val token = Base64.encodeToString(
                            item.relative.toByteArray(Charsets.UTF_8),
                            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                        )
                        archive.write(
                            "${item.root}\t$token\t${item.size}\t${item.sha}\n".toByteArray(Charsets.UTF_8),
                        )
                    }
                    archive.closeEntry()
                }
            }
            // Reopen and consume the ciphertext completely so GCM verifies its
            // authentication tag before this file can be considered usable.
            ChunkedEncryptedArchive.decryptTo(output, encryptionKey, object : java.io.OutputStream() {
                override fun write(value: Int) = Unit
                override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
            })
        } finally {
            encryptionKey.fill(0)
        }
    }

    internal fun deriveKey(masterKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
        return mac.doFinal("bitavida-associated-files-v1".toByteArray(Charsets.US_ASCII))
    }

    private fun enumerate(roots: Map<String, File>, excluded: Path): List<Entry> {
        val entries = mutableListOf<Entry>()
        roots.toSortedMap().forEach { (label, rootFile) ->
            check(label.matches(Regex("[a-z_]+")))
            val root = rootFile.toPath().toAbsolutePath().normalize()
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return@forEach
            check(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir.toAbsolutePath().normalize() == excluded) return FileVisitResult.SKIP_SUBTREE
                    check(!attrs.isSymbolicLink)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    check(attrs.isRegularFile && !attrs.isSymbolicLink)
                    entries.add(Entry(label, root.relativize(file).toString(), file))
                    return FileVisitResult.CONTINUE
                }
            })
        }
        return entries.sortedWith(compareBy(Entry::root, Entry::relative))
    }

    private fun digest(entry: Entry, sink: ((ByteArray, Int) -> Unit)?): DigestEntry {
        val first = Files.readAttributes(entry.file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        check(first.isRegularFile && !first.isSymbolicLink)
        val sha = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        var count = 0L
        try {
            Files.newInputStream(entry.file, LinkOption.NOFOLLOW_LINKS).use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    sha.update(buffer, 0, n)
                    sink?.invoke(buffer, n)
                    count += n
                }
            }
            val last = Files.readAttributes(entry.file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            check(last.isRegularFile && !last.isSymbolicLink)
            check(count == first.size() && first.size() == last.size() && first.lastModifiedTime() == last.lastModifiedTime())
            val hex = sha.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return DigestEntry(entry.root, entry.relative, count, hex)
        } finally {
            buffer.fill(0)
        }
    }
}
