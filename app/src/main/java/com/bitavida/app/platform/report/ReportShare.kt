package com.bitavida.app.platform.report

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Dedicated, non-exported provider; only the shared_reports cache directory is exposed. */
class ReportShareProvider : FileProvider()

internal data class PreparedReportShare(val uri: Uri)

private const val REPORT_SHARE_DIRECTORY = "shared_reports"
private const val REPORT_DIRECTORY_PREFIX = "report-"
private const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000

/**
 * Each request gets its own file, so a recipient can keep reading while another
 * report is prepared. No file is deleted when the chooser closes. Old cache
 * directories are removed opportunistically after a conservative retention period.
 */
internal suspend fun prepareReportShare(
    context: Context,
    fileName: String,
    writePdf: (OutputStream) -> Unit,
): PreparedReportShare = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val safeName = fileName.takeIf {
        it.endsWith(".pdf") && it.length <= 180 &&
            it.matches(Regex("[A-Za-z0-9_.-]+")) && it != "." && it != ".."
    } ?: throw IllegalArgumentException("Invalid report filename")
    val root = File(appContext.cacheDir, REPORT_SHARE_DIRECTORY)
    check(root.isDirectory || root.mkdirs())
    cleanupOldReports(root, System.currentTimeMillis())
    val directory = File(root, REPORT_DIRECTORY_PREFIX + UUID.randomUUID().toString())
    check(directory.mkdir())
    val partial = File(directory, "preparing.tmp")
    val ready = File(directory, safeName)
    try {
        currentCoroutineContext().ensureActive()
        FileOutputStream(partial).use { stream ->
            writePdf(stream)
            stream.flush()
            stream.fd.sync()
        }
        currentCoroutineContext().ensureActive()
        check(partial.isFile && partial.length() > 0)
        ParcelFileDescriptor.open(partial, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> check(renderer.pageCount > 0) }
        }
        currentCoroutineContext().ensureActive()
        check(partial.renameTo(ready))
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.reports", ready)
        check(uri.scheme == "content")
        PreparedReportShare(uri)
    } catch (failure: Throwable) {
        directory.deleteRecursively()
        throw failure
    }
}

private fun cleanupOldReports(root: File, now: Long) {
    root.listFiles()?.forEach { directory ->
        if (!directory.isDirectory || !directory.name.startsWith(REPORT_DIRECTORY_PREFIX)) return@forEach
        val lastModified = directory.lastModified()
        if (lastModified > 0 && lastModified <= now && now - lastModified >= RETENTION_MILLIS) {
            directory.deleteRecursively()
        }
    }
}

/** Launch from an explicit user action; the recipient receives read-only transient access. */
internal fun openReportShareChooser(context: Context, prepared: PreparedReportShare) {
    val stream = prepared.uri
    require(stream.scheme == "content")
    val clip = ClipData.newUri(context.contentResolver, "Informe PDF", stream)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, stream)
        clipData = clip
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Compartir informe").apply {
        clipData = clip
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
