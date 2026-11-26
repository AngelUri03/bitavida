package com.bitavida.app.ui.report

import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bitavida.app.data.careprofile.EncryptedCareProfileRepository
import com.bitavida.app.data.observations.EncryptedObservationRepository
import com.bitavida.app.data.observations.ObservationPhotoStore
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.buildFollowUpReport
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Read-only check of the initialized, isolated Demo. The expected identities come from
 * docs/DEMO_MANIFIESTO.md, not from the report exporter. This never seeds or changes data.
 *
 * Requires -PbitavidaDemoTest=true and an already initialized com.bitavida.app.demo.
 */
@RunWith(AndroidJUnit4::class)
class DemoObservationPhotoSnapshotInstrumentedTest {
    @Test fun persistedPhotoIdentitiesMatchHistoricalSnapshotsAndRenderedCounts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.bitavida.app.demo", context.packageName)
        val settings = context.getSharedPreferences("demo_initialization", 0)
        assertTrue("El escenario Demo debe estar inicializado", settings.getInt("completed_version", 0) > 0)
        val instant = Instant.parse(requireNotNull(settings.getString("reference_instant", null)))
        val zone = ZoneId.of(requireNotNull(settings.getString("reference_zone", null)))
        val reference = instant.atZone(zone).toLocalDateTime()
        val firstMonth = reference.toLocalDate().withDayOfMonth(1).minusMonths(2)

        val profile = requireNotNull(EncryptedCareProfileRepository(context).get())
        assertEquals("demo-profile", profile.id)
        assertEquals("demo-patient", profile.personUnderCare.id)
        val actor = GlucoseActor(profile.id, profile.caregiver.id, profile.personUnderCare.id, true)
        val snapshot = EncryptedObservationRepository(context).load(actor)
        val store = ObservationPhotoStore(context)

        fun id(date: LocalDate) = "demo-observation-spontaneous-$date"
        fun expected(date: LocalDate, corrected: Boolean): Set<ReportPhotoKey> {
            val record = id(date)
            return if (date.dayOfMonth == 6) setOf(
                ReportPhotoKey(record, "$record-photo"),
                ReportPhotoKey(record, if (corrected) "$record-photo-replacement-vertical" else "$record-photo-vertical"),
            ) else setOf(ReportPhotoKey(record, "$record-photo-vertical"))
        }

        suspend fun verify(end: LocalDate, expectedKeys: Set<ReportPhotoKey>) {
            val ids = expectedKeys.mapTo(mutableSetOf()) { it.recordId }
            val originalsAndCurrent = snapshot.records.filter { it.id in ids }
            assertEquals(ids, originalsAndCurrent.map { it.id }.toSet())
            val report = buildFollowUpReport(
                start = firstMonth, end = end, personUnderCareId = actor.personUnderCareId,
                overview = MedicalCareOverview(emptyList(), emptyList()), measurements = emptyList(),
                observations = originalsAndCurrent, observationRevisions = snapshot.recordRevisions.filter { it.recordId in ids },
                zoneId = zone, now = end.atTime(LocalTime.MAX),
            )
            val selected = report.observations.flatMap { record ->
                record.localPhotoIds.map { ReportPhotoKey(record.id, it) }
            }.toSet()
            assertEquals(expectedKeys, selected)
            expectedKeys.forEach { key ->
                val bytes = requireNotNull(store.readForReport(actor, key.recordId, key.photoId))
                try { assertTrue("El adjunto persistido debe contener datos", bytes.isNotEmpty()) }
                finally { bytes.fill(0) }
            }
            val prepared = prepareReportObservationPhotos(report, true) { recordId, photoId ->
                store.readForReport(actor, recordId, photoId)
            }
            assertEquals(expectedKeys, prepared.dimensions.keys)
            assertTrue(prepared.dimensions.values.all { it != null })
            var counts: ReportPhotoCounts? = null
            val output = ByteArrayOutputStream()
            writeFollowUpPdf(output, report, "Cuidadora ficticia", "Persona ficticia",
                Typeface.DEFAULT, Typeface.DEFAULT_BOLD, generatedOn = end,
                photos = prepared, onPhotoCounts = { counts = it })
            assertEquals(ReportPhotoCounts(expectedKeys.size, expectedKeys.size, 0, 0), counts)
            assertTrue(output.size() > 0)
        }

        val firstSixth = firstMonth.withDayOfMonth(6)
        val firstTwentySecond = firstMonth.withDayOfMonth(22)
        val secondMonth = firstMonth.plusMonths(1)
        val secondSixth = secondMonth.withDayOfMonth(6)
        val secondTwentySecond = secondMonth.withDayOfMonth(22)
        // Before and after the persisted photo-replacement correction.
        verify(firstSixth, expected(firstSixth, corrected = false))
        verify(firstSixth.plusDays(1), expected(firstSixth, corrected = true))
        // Full months: both versions contain exactly two photos on day 6 and one on day 22.
        verify(firstMonth.withDayOfMonth(firstMonth.lengthOfMonth()),
            expected(firstSixth, corrected = true) + expected(firstTwentySecond, corrected = false))
        verify(secondMonth.withDayOfMonth(secondMonth.lengthOfMonth()),
            expected(secondSixth, corrected = false) + expected(secondTwentySecond, corrected = false))

        // A valid photo ID cannot be read through another observation's relationship.
        val wrongRecord = id(firstTwentySecond)
        val foreignPhoto = "${id(firstSixth)}-photo"
        assertNull(store.readForReport(actor, wrongRecord, foreignPhoto))
    }
}
