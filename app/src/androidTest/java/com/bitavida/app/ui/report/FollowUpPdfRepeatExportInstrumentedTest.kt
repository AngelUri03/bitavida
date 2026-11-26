package com.bitavida.app.ui.report

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.res.ResourcesCompat
import androidx.room3.executeSQL
import androidx.room3.withWriteTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bitavida.app.R
import com.bitavida.app.data.local.secure.SecureDatabaseInitializer
import com.bitavida.app.data.local.secure.SecureStorageConfig
import com.bitavida.app.diagnostic.buildDiagnosticSnapshot
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.domain.usecase.FollowUpReportRow
import com.bitavida.app.domain.usecase.buildFollowUpReport
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses the real Android writer and PdfRenderer with only fictitious information. The encrypted
 * database is disposable and unrelated to the application's real repository.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 35)
class FollowUpPdfRepeatExportInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = SecureStorageConfig(
        databaseName = "pdf-repeat-export-test.db",
        wrappedKeyFileName = "pdf-repeat-export-test-key.bin",
        wrappingKeyAlias = "com.bitavida.app.test.pdf-repeat-export",
    )
    private var initializer = SecureDatabaseInitializer(config)

    @Before fun before() = clearTestDatabase()

    @After fun after() {
        runBlocking { initializer.close() }
        clearTestDatabase()
    }

    @Test fun sameSnapshotExportsEquivalentPagesAndLinksWithoutDatabaseWrites() = runBlocking {
        val database = initializer.open(context)
        database.withWriteTransaction {
            executeSQL("INSERT INTO caregivers (id, displayName, declarationSource, createdAtEpochMillis) VALUES ('caregiver-test', 'Ficticia', 'SELF', 1)")
        }
        val before = JSONObject(buildDiagnosticSnapshot(database)).getJSONObject("tables").toString()
        val report = syntheticSnapshot()
        val first = export(report)
        val second = export(report)
        val after = JSONObject(buildDiagnosticSnapshot(database)).getJSONObject("tables").toString()
        assertEquals(before, after)
        assertEquals(first.links, second.links)
        assertTrue(first.links.isNotEmpty())
        assertTrue(first.bytes.toString(StandardCharsets.ISO_8859_1).contains("/Type /Outlines"))
        val pagesA = render(first.bytes)
        val pagesB = render(second.bytes)
        assertTrue(pagesA.size > 1)
        assertEquals(pagesA, pagesB)
        assertTrue(pagesA.all { it.width == 595 && it.height == 842 })
        assertTrue(pagesA.flatMap { it.targets }.all { it in pagesA.indices })
        assertTrue(pagesA.any { it.text.contains("después de comer", ignoreCase = true) })
        assertTrue(pagesA.any { it.text.contains("antes de dormir", ignoreCase = true) })
        listOf("Mediciones y seguimiento", "Medicamentos y tomas", "Observaciones y atención").forEach { title ->
            assertTrue("El título ficticio debe extraerse completo: $title", pagesA.any {
                it.text.contains(title, ignoreCase = true)
            })
        }
        assertTrue("El resumen ficticio contiene las 30 tomas completas definidas en el escenario",
            pagesA.any { it.text.contains("30 completas", ignoreCase = true) })
        val observations = pagesA.firstOrNull {
            it.text.contains("Observaciones y atención", ignoreCase = true) &&
                !it.text.contains("Observaciones y atención · continuación", ignoreCase = true)
        }
        val diagnostic = pagesA.mapIndexed { index, page ->
            "Página ${index + 1}: ${page.text}"
        }.joinToString("\n")
        assertTrue(
            "El título de Observaciones debe compartir página con su primera fila ficticia. Texto ficticio extraído:\n$diagnostic",
            observations != null && observations.text.contains("Observación ficticia", ignoreCase = true),
        )
        // Only this hard-coded fictitious snapshot can be copied, and only at an explicit
        // instrumentation request. It remains in the test application's scoped external files.
        if (InstrumentationRegistry.getArguments().getString("bitavida_export_fake_pdf") == "true") {
            val directory = requireNotNull(context.getExternalFilesDir("test-evidence"))
            check(directory.exists() || directory.mkdirs())
            File(directory, "informe-ficticio-prueba.pdf").writeBytes(first.bytes)
        }
    }

    @Test fun differentPlansAndDeclarationsRemainDistinguishableInTheActualPdf() {
        val day = LocalDate.of(2026, 10, 12)
        val scheduled = day.atTime(21, 30)
        val series = "medication-series:fictional"
        fun plan(id: String, minute: Int) = FollowUpReportRow("Indicaciones médicas",
            day.atTime(10, minute), "Medicamento ficticio", "1 tableta · Todos los días",
            "Indicación médica transcrita", sourceId = "medication-plan:$id", groupId = series)
        fun intake(id: String, planId: String, detail: String) = FollowUpReportRow("Medicamentos",
            scheduled, "Medicamento ficticio", detail, "Registro declarado por el cuidador",
            sourceId = "medication-intake:$id", groupId = series,
            medicationPlanId = planId, scheduledAt = scheduled)
        val rows = listOf(plan("first", 0), plan("second", 1),
            intake("one", "first", "Se tomó · Cantidad declarada: 3 gotas"),
            intake("two", "first", "Toma parcial · Cantidad declarada: 2 gotas"),
            intake("three", "second", "Se tomó · Cantidad declarada: 4 gotas"))
        val report = FollowUpReport(day, day, rows, snapshotAt = day.atTime(23, 0))
        val text = render(export(report).bytes).joinToString(" ") { it.text }
        assertTrue(text.contains("Pauta 1"))
        assertTrue(text.contains("Pauta 2"))
        assertTrue(text.contains("Registro 1 de 2"))
        assertTrue(text.contains("Registro 2 de 2"))
        assertTrue(text.contains("3 gotas"))
        assertTrue(text.contains("2 gotas"))
        assertTrue(text.contains("4 gotas"))
    }

    private data class Export(val bytes: ByteArray, val links: List<FollowUpPdfLink>)
    private data class PageSignature(
        val width: Int,
        val height: Int,
        val text: String,
        val targets: List<Int>,
        val pixelsHash: Int,
    )

    private fun export(report: FollowUpReport): Export {
        val bytes = ByteArrayOutputStream()
        val regular = requireNotNull(ResourcesCompat.getFont(context, R.font.kodchasan_regular))
        val bold = requireNotNull(ResourcesCompat.getFont(context, R.font.kodchasan_bold))
        var links = emptyList<FollowUpPdfLink>()
        writeFollowUpPdf(bytes, report, "Cuidadora ficticia", "Paciente ficticio",
            regular, bold, report.end, onPageLinks = { links = it })
        return Export(bytes.toByteArray(), links)
    }

    private fun render(bytes: ByteArray): List<PageSignature> {
        val file = File.createTempFile("pdf-ficticio-", ".pdf", context.cacheDir)
        return try {
            file.writeBytes(bytes)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor).use { renderer ->
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pixels = IntArray(page.width * page.height)
                            bitmap.getPixels(pixels, 0, page.width, 0, 0, page.width, page.height)
                            PageSignature(
                                page.width, page.height,
                                page.textContents.joinToString(" ") { it.text },
                                page.gotoLinks.map { it.destination.pageNumber },
                                pixels.contentHashCode(),
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun syntheticSnapshot(): FollowUpReport {
        val day = LocalDate.of(2026, 10, 1)
        fun measurement(id: String, value: MeasurementValue, hour: Int): MeasurementRecord =
            MeasurementRecord(id, value, day.atTime(hour, 0), day.atTime(hour, 0))
        val records = listOf(
            measurement("g-fasting", MeasurementValue.Glucose("105", GlucoseContext.FASTING), 7),
            measurement("g-after", MeasurementValue.Glucose("132", GlucoseContext.AFTER_MEAL), 10),
            measurement("g-before-sleep-1", MeasurementValue.Glucose("100", GlucoseContext.BEFORE_SLEEP), 20),
            measurement("g-before-sleep-2", MeasurementValue.Glucose("101", GlucoseContext.BEFORE_SLEEP), 21),
            measurement("pressure", MeasurementValue.BloodPressure("120", "80"), 11),
            measurement("weight", MeasurementValue.Weight(BigDecimal("70.2")), 12),
        )
        val base = buildFollowUpReport(day, day.plusDays(30), "ficticio",
            MedicalCareOverview(emptyList(), emptyList()), records, emptyList(),
            zoneId = ZoneOffset.UTC, now = day.plusDays(30).atTime(23, 0))
        val extra = buildList {
            repeat(30) { index ->
                add(FollowUpReportRow(
                    "Medicamentos", day.plusDays(index.toLong()).atTime(8, 0),
                    "Medicamento ficticio", "Se tomó · 1 tableta", "Declaración ficticia",
                    sourceId = "medication-intake:fake-$index", groupId = "medication-series:fake",
                    medicationPlanId = "fake-plan",
                    scheduledAt = day.plusDays(index.toLong()).atTime(8, 0),
                    actualAt = day.plusDays(index.toLong()).atTime(8, 5),
                ))
            }
            add(FollowUpReportRow("Observaciones", day.atTime(13, 0),
                "Observación ficticia", "Nota ficticia para comprobar la primera fila.", "Declaración ficticia"))
        }
        // This is a pagination fixture, not an independent test of repository aggregation.
        // Keep its explicitly constructed rows and declared totals consistent.
        val summaries = base.summaries.map { summary ->
            when (summary.label) {
                "Registros de toma", "Tomas completas" -> summary.copy(count = 30)
                "Registros de observación" -> summary.copy(count = 1)
                else -> summary
            }
        }
        return base.copy(rows = base.rows + extra, summaries = summaries, medicationSeriesIds = setOf("fake"))
    }

    private fun clearTestDatabase() {
        context.deleteDatabase(config.databaseName)
        File(context.noBackupFilesDir, config.wrappedKeyFileName).delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(config.wrappingKeyAlias)
    }
}
