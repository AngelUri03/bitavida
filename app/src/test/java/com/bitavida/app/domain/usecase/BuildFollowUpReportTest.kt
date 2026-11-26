package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class BuildFollowUpReportTest {
    private val day = LocalDate.of(2026, 1, 15)
    private val overview = MedicalCareOverview(emptyList(), emptyList())

    @Test fun includesOnlyRealMeasurementsInInclusivePeriodWithoutInterpretingThem() {
        val inside = MeasurementRecord("fictional-1", MeasurementValue.Glucose("HI"), day.atTime(10, 0), day.atTime(11, 0))
        val outside = MeasurementRecord("fictional-2", MeasurementValue.Glucose("90"), day.minusDays(1).atStartOfDay(), day.atStartOfDay())
        val report = buildFollowUpReport(day, day, "patient", overview, listOf(inside, inside, outside), emptyList())
        assertEquals(1, report.rows.size)
        assertEquals("HI", report.rows.single().detail)
        assertEquals(inside.occurredAt, report.rows.single().occurredAt)
        assertEquals(1, report.summaries.single { it.label == "Lecturas de glucosa" }.count)
        assertEquals(0, report.summaries.single { it.label == "Lecturas de presión" }.count)
    }

    @Test fun observationWithoutTimeRemainsUndatedByHourAndOtherPatientIsExcluded() {
        fun sample(id: String, patient: String) = ObservationRecord(
            id = id, observationId = "fictional", personUnderCareId = patient, occurredOn = day,
            occurredAt = null, timePrecision = ObservationTimePrecision.UNKNOWN,
            result = ObservationResult.NO_CHANGES, answers = emptyMap(), description = "",
            createdAt = day.atStartOfDay(), actorId = "caregiver",
        )
        val report = buildFollowUpReport(day, day, "patient", overview, emptyList(),
            listOf(sample("one", "patient"), sample("two", "other")))
        assertEquals(1, report.rows.size)
        assertFalse(report.rows.single().timeKnown)
        assertTrue(report.rows.single().detail.contains("Hora no indicada"))
    }

    @Test fun unknownTimeIgnoresResidualClockButExplicitMidnightRemainsKnown() {
        val base = ObservationRecord("residual", "fictional", "patient", day, LocalTime.MIDNIGHT,
            ObservationTimePrecision.UNKNOWN, ObservationResult.NO_CHANGES, emptyMap(), "",
            createdAt = day.atStartOfDay(), actorId = "caregiver")
        val midnight = base.copy(id = "midnight", timePrecision = ObservationTimePrecision.EXACT)
        val report = buildFollowUpReport(day, day, "patient", overview, emptyList(), listOf(base, midnight),
            now = day.atTime(23, 0))
        val unknown = report.rows.single { it.sourceId == "observation:residual" }
        val known = report.rows.single { it.sourceId == "observation:midnight" }
        assertFalse(unknown.timeKnown)
        assertTrue(unknown.detail.contains("Hora no indicada"))
        assertTrue(known.timeKnown)
        assertEquals(LocalTime.MIDNIGHT, known.occurredAt.toLocalTime())
        assertFalse(known.detail.contains("Hora no indicada"))
    }

    @Test fun notPerformedReviewIsVisibleButDoesNotCountAsPerformedObservation() {
        fun observation(id: String, result: ObservationResult) = ObservationRecord(
            id = id, observationId = "fictional", personUnderCareId = "patient", occurredOn = day,
            occurredAt = null, timePrecision = ObservationTimePrecision.UNKNOWN,
            result = result, answers = emptyMap(), description = "",
            createdAt = day.atStartOfDay(), actorId = "caregiver",
        )
        val report = buildFollowUpReport(day, day, "patient", overview, emptyList(),
            listOf(observation("performed", ObservationResult.CHANGES),
                observation("not-performed", ObservationResult.NOT_PERFORMED)))
        assertEquals(1, report.summaries.single { it.label == "Registros de observación" }.count)
        assertTrue(report.rows.any { it.section == "Observaciones" && it.detail.contains("No se realizó") })
    }

    @Test fun longProjectionCanBeCancelledBetweenDates() {
        var checks = 0
        val failure = runCatching {
            buildFollowUpReport(day, day.plusYears(10), "patient", overview, emptyList(), emptyList(),
                measurementSchedules = listOf(ReportMeasurementSchedule(MeasurementKind.WEIGHT, null, emptyList())),
                checkCancellation = {
                    checks++
                    if (checks == 3) throw java.util.concurrent.CancellationException()
                })
        }.exceptionOrNull()
        assertTrue(failure is java.util.concurrent.CancellationException)
        assertEquals(3, checks)
    }

    @Test fun suggestedReviewRemainsUnconfirmedAndKeepsInstructionOriginsSeparate() {
        fun event(id: String, patient: String) = CareEvent(id, patient, CareEventDraft(
            kind = CareEventKind.REVIEW_TO_SCHEDULE, title = "Revisión ficticia",
            transcribedIndication = "Indicación ficticia", suggestedDate = day,
            instructions = "Apunte adicional ficticio", status = CareEventStatus.TO_SCHEDULE,
        ), day.minusDays(1).atStartOfDay(), day.minusDays(1).atStartOfDay(), 1)
        val report = buildFollowUpReport(day, day, "patient", overview, emptyList(), emptyList(),
            listOf(event("one", "patient"), event("two", "other")))
        assertEquals(2, report.rows.size)
        assertTrue(report.rows.any { it.detail.contains("Fecha sugerida, no es una cita confirmada") })
        assertTrue(report.rows.any { it.detail == "Indicación ficticia" && it.provenance == "Indicación médica transcrita" })
        assertTrue(report.rows.any { it.section == "Citas" && it.detail.contains("Indicaciones adicionales: Apunte adicional ficticio") })
        assertFalse(report.rows.any { it.timeKnown })
    }

    @Test fun versionedMedicationChangesAreIncludedWithoutTurningPastFactsIntoCurrentTakes() {
        val earlier = revision("one", 1, day.minusDays(2), MedicationPlanChangeKind.CREATED)
        val later = revision("one", 2, day, MedicationPlanChangeKind.SUSPENDED, "Motivo ficticio")
            .copy(lifecycleEffectiveAtEpochMillis = day.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        val realPlan = samplePlan("one", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(earlier, later))
        val foreignPlan = samplePlan("other", ClinicalContentMode.REAL_CLINICAL_DATA, "other", listOf(
            revision("other", 1, day, MedicationPlanChangeKind.CREATED)))
        val samplePlan = samplePlan("demo", ClinicalContentMode.FICTITIOUS_DEVELOPMENT, "patient", listOf(
            revision("demo", 1, day, MedicationPlanChangeKind.CREATED)))
        val originalAt = day.minusDays(1).atTime(8, 0)
        val revisions = listOf(
            MedicationIntakeRevision("intake-r1", "intake", 1, MedicationIntakeOutcome.COMPLETED,
                originalAt, originalAt, null, null, MedicationIntakeChangeKind.REGISTERED, null,
                day.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), "actor"),
            MedicationIntakeRevision("intake-r2", "intake", 2, MedicationIntakeOutcome.OMITTED,
                originalAt, null, null, null, MedicationIntakeChangeKind.CORRECTED, "Corrección ficticia",
                day.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), "actor"),
        )
        val intake = MedicationIntakeRecord("intake", "one", 0, "actor", revisions)
        val data = MedicalCareOverview(emptyList(), listOf(realPlan, foreignPlan, samplePlan), listOf(intake))
        val report = buildFollowUpReport(day, day, "patient", data, emptyList(), emptyList(), zoneId = ZoneOffset.UTC)
        assertTrue(report.rows.any { it.section == "Cambios de medicamentos" && it.detail.contains("Suspensión registrada") })
        assertTrue(report.rows.any { it.section == "Cambios de medicamentos" && it.detail.contains("Corrección ficticia") })
        assertFalse(report.rows.any { it.section == "Medicamentos" && it.sourceId == "medication-intake:intake" })
        assertTrue(report.rows.any { it.section == "Indicaciones médicas" && it.detail.contains("Cantidad indicada") })
        assertFalse(report.rows.any { it.title.contains("Medicamento other") || it.title.contains("Medicamento demo") })
        assertEquals(0, report.summaries.single { it.label == "Registros de toma" }.count)
    }

    @Test fun correctedIntakeKeepsPriorDeclarationAndReasonWithSameIdentity() {
        val plan = samplePlan("prior", ClinicalContentMode.REAL_CLINICAL_DATA, "patient",
            listOf(revision("prior", 1, day.minusDays(1), MedicationPlanChangeKind.CREATED)))
        val moment = day.atTime(8, 0)
        val intake = MedicationIntakeRecord("fact", "prior", 0, "actor", listOf(
            MedicationIntakeRevision("r1", "fact", 1, MedicationIntakeOutcome.COMPLETED, moment, moment,
                MedicationAmount("2", "tabletas"), "Nota original ficticia", MedicationIntakeChangeKind.REGISTERED, null,
                moment.toInstant(ZoneOffset.UTC).toEpochMilli(), "actor"),
            MedicationIntakeRevision("r2", "fact", 2, MedicationIntakeOutcome.OMITTED, moment, null,
                null, null, MedicationIntakeChangeKind.CORRECTED, "Motivo ficticio",
                day.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), "actor"),
        ))
        val report = buildFollowUpReport(day, day, "patient", MedicalCareOverview(emptyList(), listOf(plan), listOf(intake)),
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC, now = day.atTime(23, 0))
        val fact = report.rows.single { it.section == "Medicamentos" }
        val correction = report.rows.single { it.section == "Cambios de medicamentos" && it.sourceId == fact.sourceId }
        assertEquals("medication-intake:fact", fact.sourceId)
        assertTrue(correction.detail.contains("Motivo: Motivo ficticio"))
        assertTrue(correction.detail.contains("Estado: Se tomó → No se tomó"))
        assertTrue(correction.detail.contains("Cantidad declarada anterior: 2 tabletas"))
        assertTrue(correction.detail.contains("Nota anterior: Nota original ficticia"))
        assertEquals(0, report.summaries.single { it.label == "Registros de toma" }.count)
    }

    @Test fun linkedResponseCrossingMidnightStaysWithItsScheduledOccurrence() {
        val plan = samplePlan("cross", ClinicalContentMode.REAL_CLINICAL_DATA, "patient",
            listOf(revision("cross", 1, day.minusDays(1), MedicationPlanChangeKind.CREATED)))
        val scheduled = day.atTime(23, 30)
        val actual = day.plusDays(1).atTime(0, 10)
        val declaration = MedicationIntakeRevision("r-cross", "intake-cross", 1,
            MedicationIntakeOutcome.COMPLETED, scheduled, actual, null, null,
            MedicationIntakeChangeKind.REGISTERED, null,
            actual.toInstant(ZoneOffset.UTC).toEpochMilli(), "actor",
            scheduledOccurrenceId = "occurrence-cross")
        val intake = MedicationIntakeRecord("intake-cross", "cross", declaration.capturedAtEpochMillis,
            "actor", listOf(declaration))
        val data = MedicalCareOverview(emptyList(), listOf(plan), listOf(intake))
        val report = buildFollowUpReport(day, day.plusDays(1), "patient", data, emptyList(), emptyList(),
            zoneId = ZoneOffset.UTC, now = day.plusDays(1).atTime(23, 0))
        val row = report.rows.single { it.sourceId == "medication-intake:intake-cross" }
        assertEquals(scheduled, row.occurredAt)
        assertEquals(scheduled, row.scheduledAt)
        assertEquals(actual, row.actualAt)
        assertEquals(1, report.summaries.single { it.label == "Tomas completas" }.count)

        val nextDay = buildFollowUpReport(day.plusDays(1), day.plusDays(1), "patient", data,
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC, now = day.plusDays(1).atTime(23, 0))
        assertFalse(nextDay.rows.any { it.sourceId == "medication-intake:intake-cross" })
        assertEquals(0, nextDay.summaries.single { it.label == "Tomas completas" }.count)
    }

    @Test fun unlinkedResponseUsesFactTimeRatherThanGuessingASchedule() {
        val scheduled = day.atTime(23, 30)
        val actual = day.plusDays(1).atTime(0, 10)
        val revision = MedicationIntakeRevision("r-extra", "extra", 1,
            MedicationIntakeOutcome.COMPLETED, scheduled, actual, null, null,
            MedicationIntakeChangeKind.REGISTERED, null,
            actual.toInstant(ZoneOffset.UTC).toEpochMilli(), "actor")
        assertEquals(actual, medicationReportMoment(revision))
    }

    @Test fun captureTimeUsesExplicitLocalZoneForPeriod() {
        val plan = samplePlan("zone", ClinicalContentMode.REAL_CLINICAL_DATA, "patient",
            listOf(revision("zone", 1, day, MedicationPlanChangeKind.CREATED)))
        val data = MedicalCareOverview(emptyList(), listOf(plan))
        val utc = buildFollowUpReport(day, day, "patient", data, emptyList(), emptyList(), zoneId = ZoneOffset.UTC)
        val west = buildFollowUpReport(day, day, "patient", data, emptyList(), emptyList(), zoneId = ZoneOffset.ofHours(-6))
        assertTrue(utc.rows.any { it.section == "Cambios de medicamentos" })
        assertTrue(west.rows.none { it.section == "Cambios de medicamentos" })
    }

    @Test fun descriptiveStatisticsKeepEveryReadingAndExcludeNonNumericGlucose() {
        val values = listOf(
            MeasurementRecord("g1", MeasurementValue.Glucose("90"), day.atTime(8, 0), day.atTime(8, 0)),
            MeasurementRecord("g2", MeasurementValue.Glucose("110"), day.atTime(9, 0), day.atTime(9, 0)),
            MeasurementRecord("g3", MeasurementValue.Glucose("HI"), day.atTime(10, 0), day.atTime(10, 0)),
            MeasurementRecord("p", MeasurementValue.BloodPressure("120", "80"), day.atTime(11, 0), day.atTime(11, 0)),
            MeasurementRecord("w1", MeasurementValue.Weight(java.math.BigDecimal("70.0")), day.atTime(7, 0), day.atTime(7, 0)),
            MeasurementRecord("w2", MeasurementValue.Weight(java.math.BigDecimal("71.5")), day.atTime(12, 0), day.atTime(12, 0)),
        )
        val report = buildFollowUpReport(day, day, "patient", overview, values + values.first(), emptyList())
        assertEquals(6, report.measurements.size)
        assertEquals(3, report.summaries.single { it.label == "Lecturas de glucosa" }.count)
        assertEquals(100.0, report.statistics!!.glucose!!.average, 0.001)
        assertEquals(2, report.statistics!!.glucose!!.count)
        assertEquals(120.0, report.statistics!!.systolic!!.minimum, 0.001)
        assertEquals(80.0, report.statistics!!.diastolic!!.maximum, 0.001)
        assertEquals(1.5, report.statistics!!.weightDifference!!, 0.001)
    }

    @Test fun reportRowsRetainMeasurementUnitsContextNotesAndDistinctIdentities() {
        val at = day.atTime(9, 0)
        val glucose = MeasurementRecord("same", MeasurementValue.Glucose("110", GlucoseContext.FASTING), at, at,
            glucoseMetadata = GlucoseRecordMetadata(GlucoseEntryKind.INDEPENDENT, note = "Nota de glucosa ficticia"))
        val pressure = MeasurementRecord("same", MeasurementValue.BloodPressure("120", "80", "72"), at, at,
            bloodPressureMetadata = BloodPressureRecordMetadata(BloodPressureEntryKind.INDEPENDENT,
                actualContext = GlucoseContext.RESTING, note = "Nota de presión ficticia"))
        val weight = MeasurementRecord("weight", MeasurementValue.Weight(java.math.BigDecimal("70.2")), at, at,
            weightMetadata = WeightRecordMetadata(WeightEntryKind.INDEPENDENT, note = "Nota de peso ficticia"))
        val report = buildFollowUpReport(day, day, "patient", overview, listOf(glucose, glucose, pressure, weight),
            emptyList(), now = day.atTime(23, 0))
        val rows = report.rows.filter { it.section == "Mediciones" }
        assertEquals(3, rows.size)
        assertEquals(3, rows.map { it.sourceId }.toSet().size)
        assertTrue(rows.single { it.title == "Glucosa capilar" }.detail.contains("110 mg/dL · En ayunas"))
        assertTrue(rows.single { it.title == "Glucosa capilar" }.detail.contains("Nota de glucosa ficticia"))
        assertTrue(rows.single { it.title == "Presión arterial" }.detail.contains("120/80 mmHg · Pulso: 72 lpm · En reposo"))
        assertTrue(rows.single { it.title == "Presión arterial" }.detail.contains("Nota de presión ficticia"))
        assertTrue(rows.single { it.title == "Peso" }.detail.contains("70.2 kg · Nota: Nota de peso ficticia"))
    }

    @Test fun observationPhotosAndCorrectionReasonAreDescribedWithoutExposingIdentifiers() {
        val original = ObservationRecord("obs", "fictional", "patient", day, null,
            ObservationTimePrecision.UNKNOWN, ObservationResult.CHANGES, emptyMap(), "Descripción inicial ficticia",
            localPhotoIds = listOf("ruta-privada-ficticia"), createdAt = day.atTime(8, 0), actorId = "actor")
        val corrected = original.copy(description = "Descripción corregida ficticia")
        val revisions = listOf(
            ObservationRecordRevision("r1", "obs", 1, original, null, day.atTime(8, 0), "actor"),
            ObservationRecordRevision("r2", "obs", 2, corrected, "Motivo ficticio", day.atTime(9, 0), "actor"),
        )
        val report = buildFollowUpReport(day, day, "patient", overview, emptyList(), listOf(corrected),
            now = day.atTime(23, 0), observationRevisions = revisions)
        val row = report.rows.single { it.section == "Observaciones" }
        assertEquals("observation:obs", row.sourceId)
        assertTrue(row.detail.contains("Descripción corregida ficticia"))
        assertTrue(row.detail.contains("Fotografías adjuntas: 1"))
        assertFalse(row.detail.contains("Versión anterior"))
        assertFalse(row.detail.contains("Motivo ficticio"))
        assertFalse(row.detail.contains("ruta-privada-ficticia"))
        val audit = report.rows.single { it.section == "Auditoría y correcciones" }
        assertEquals("observation-revision:r2", audit.sourceId)
        assertEquals(day.atTime(9, 0), audit.occurredAt)
        assertTrue(audit.detail.contains("Motivo: Motivo ficticio"))
        assertTrue(audit.detail.contains("Versión anterior: Fecha: 15/01/2026 · Hora no indicada · Cambios declarados · Descripción inicial ficticia"))
        assertTrue(audit.detail.contains("Versión corregida: Fecha: 15/01/2026 · Hora no indicada · Cambios declarados · Descripción corregida ficticia"))
        assertFalse(audit.detail.contains("ruta-privada-ficticia"))
        assertEquals(1, report.summaries.single { it.label == "Registros de observación" }.count)
    }

    @Test fun reportEventsPreserveExplicitTypeAndLinkTranscribedIndication() {
        val event = CareEvent("event", "patient", CareEventDraft(
            kind = CareEventKind.APPOINTMENT, purpose = CareEventPurpose.PROCEDURE, title = "Atención ficticia",
            scheduledAt = day.atTime(10, 0), status = CareEventStatus.CONFIRMED,
            transcribedIndication = "Indicación ficticia", institution = "Unidad ficticia",
            instructions = "Apunte ficticio"), day.atStartOfDay(), day.atStartOfDay(), 1)
        val report = buildFollowUpReport(day, day, "patient", overview, emptyList(), emptyList(), listOf(event),
            now = day.atTime(23, 0))
        val row = report.rows.single { it.section == "Citas" }
        val indication = report.rows.single { it.section == "Indicaciones médicas" }
        assertEquals("Procedimiento: Atención ficticia", row.title)
        assertTrue(row.detail.contains("Confirmada"))
        assertTrue(row.detail.contains("Unidad ficticia"))
        assertTrue(row.detail.contains("Apunte ficticio"))
        assertEquals(row.sourceId, indication.sourceId)
    }

    @Test fun scheduledMeasurementStatesRemainSeparateFromNumericReadings() {
        val date = LocalDate.of(2026, 11, 17)
        val (followUp, records, decisions) = measurementHistoryIncidentFixture(MeasurementKind.CAPILLARY_GLUCOSE)
        val report = buildFollowUpReport(date, date, "patient", overview, records, emptyList(),
            measurementSchedules = listOf(ReportMeasurementSchedule(MeasurementKind.CAPILLARY_GLUCOSE, followUp, decisions)),
            now = date.atTime(18, 0))
        val scheduled = report.rows.filter { it.section == "Mediciones programadas" }
        assertEquals(1, scheduled.count { it.detail == "No realizada, declarado" })
        assertEquals(2, scheduled.count { it.detail == "Sin confirmar" })
        assertEquals(3, report.measurements.size)
        assertEquals(3, report.summaries.single { it.label == "Lecturas de glucosa" }.count)
    }

    @Test fun onlyCompletedIntakesCountAsTaken() {
        val plan = samplePlan("count", ClinicalContentMode.REAL_CLINICAL_DATA, "patient",
            listOf(revision("count", 1, day, MedicationPlanChangeKind.CREATED)))
        fun intake(id: String, outcome: MedicationIntakeOutcome): MedicationIntakeRecord {
            val moment = day.atTime(8, 0)
            return MedicationIntakeRecord(id, "count", 0, "actor", listOf(
                MedicationIntakeRevision("$id-r1", id, 1, outcome, moment,
                    if (outcome == MedicationIntakeOutcome.COMPLETED) moment else null,
                    null, null, MedicationIntakeChangeKind.REGISTERED, null,
                    moment.toInstant(ZoneOffset.UTC).toEpochMilli(), "actor"),
            ))
        }
        val intakes = listOf(intake("taken", MedicationIntakeOutcome.COMPLETED),
            intake("omitted", MedicationIntakeOutcome.OMITTED),
            intake("unconfirmed", MedicationIntakeOutcome.UNCONFIRMED))
        val report = buildFollowUpReport(day, day, "patient", MedicalCareOverview(emptyList(), listOf(plan), intakes),
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC)
        assertEquals(1, report.summaries.single { it.label == "Registros de toma" }.count)
        assertTrue(report.rows.any { it.section == "Medicamentos" && it.detail.contains("No se tomó") })
        assertTrue(report.rows.any { it.section == "Medicamentos" && it.detail.contains("Sin confirmar") })
    }

    @Test fun earlierPlanIndicationIsIncludedWithoutInventingAnAdministration() {
        val plan = samplePlan("prior", ClinicalContentMode.REAL_CLINICAL_DATA, "patient",
            listOf(revision("prior", 1, day.minusDays(2), MedicationPlanChangeKind.CREATED)))
        val report = buildFollowUpReport(day, day, "patient", MedicalCareOverview(emptyList(), listOf(plan)),
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC)
        assertTrue(report.rows.any { it.section == "Indicaciones médicas" && it.detail.contains("antes del periodo") })
        assertEquals(0, report.summaries.single { it.label == "Registros de toma" }.count)
    }

    @Test fun observationUsesVersionKnownAtReportEndWithoutDuplicatingCorrection() {
        val original = ObservationRecord(
            id = "observation", observationId = "fictional", personUnderCareId = "patient",
            occurredOn = day, occurredAt = null, timePrecision = ObservationTimePrecision.UNKNOWN,
            result = ObservationResult.CHANGES, answers = emptyMap(), description = "Descripción original ficticia",
            createdAt = day.atTime(9, 0), actorId = "caregiver",
        )
        val corrected = original.copy(description = "Descripción corregida ficticia")
        val revisions = listOf(
            ObservationRecordRevision("obs-r1", original.id, 1, original, null, day.atTime(9, 0), "caregiver"),
            ObservationRecordRevision("obs-r2", original.id, 2, corrected, "Corrección ficticia", day.plusDays(1).atTime(9, 0), "caregiver"),
        )
        val report = buildFollowUpReport(day, day, "patient", overview, emptyList(), listOf(corrected),
            now = day.plusDays(2).atStartOfDay(), observationRevisions = revisions)
        assertEquals(listOf(original), report.observations)
        assertEquals(1, report.summaries.single { it.label == "Registros de observación" }.count)
        assertTrue(report.rows.single().detail.contains("Descripción original ficticia"))
        assertFalse(report.rows.single().detail.contains("Descripción corregida ficticia"))
    }

    @Test fun stoppedPlanIsExcludedAfterItsLifecycleEndsButIncludedWhenItEndsInPeriod() {
        fun stopped(id: String, stoppedOn: LocalDate) = samplePlan(id, ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(
            revision(id, 1, day.minusDays(10), MedicationPlanChangeKind.CREATED),
            revision(id, 2, stoppedOn, MedicationPlanChangeKind.SUSPENDED, "Suspensión ficticia"),
        ))
        val past = stopped("past", day.minusDays(1))
        val during = stopped("during", day)
        val report = buildFollowUpReport(day, day, "patient", MedicalCareOverview(emptyList(), listOf(past, during)),
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC, now = day.atTime(23, 0))
        assertFalse("past" in report.medicationPlanIds)
        assertTrue("during" in report.medicationPlanIds)
        assertFalse(report.rows.any { it.title.contains("Medicamento past") })
        assertTrue(report.rows.any { it.title.contains("Medicamento during") })
    }

    @Test fun retroactiveSuspensionKnownAtCloseDoesNotCreateAnActiveIndication() {
        val prior = revision("retroactive", 1, day.minusDays(10), MedicationPlanChangeKind.CREATED)
        val suspended = revision("retroactive", 2, day, MedicationPlanChangeKind.SUSPENDED, "Suspensión ficticia")
            .copy(lifecycleEffectiveAtEpochMillis = day.minusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        val plan = samplePlan("retroactive", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(prior, suspended))
        val report = buildFollowUpReport(day, day, "patient", MedicalCareOverview(emptyList(), listOf(plan)),
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC, now = day.atTime(23, 0))
        assertTrue(report.rows.any { it.section == "Cambios de medicamentos" && it.detail.contains("Suspensión registrada") })
        assertFalse(report.rows.any { it.section == "Indicaciones médicas" && it.title.contains("Medicamento retroactive") })
    }

    @Test fun futureSuspensionDoesNotRewriteEarlierPeriod() {
        val plan = samplePlan("later", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(
            revision("later", 1, day.minusDays(10), MedicationPlanChangeKind.CREATED),
            revision("later", 2, day.plusDays(1), MedicationPlanChangeKind.REMOVED, "Retiro ficticio"),
        ))
        val report = buildFollowUpReport(day, day, "patient", MedicalCareOverview(emptyList(), listOf(plan)),
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC, now = day.plusDays(5).atStartOfDay())
        assertTrue("later" in report.medicationPlanIds)
        assertTrue(report.rows.any { it.section == "Indicaciones médicas" && it.title.contains("Medicamento later") })
    }

    @Test fun recordedFactKeepsPlanVisibleEvenWhenFollowUpAlreadyEnded() {
        val plan = samplePlan("fact", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(
            revision("fact", 1, day.minusDays(10), MedicationPlanChangeKind.CREATED),
            revision("fact", 2, day.minusDays(1), MedicationPlanChangeKind.SUSPENDED, "Suspensión ficticia"),
        ))
        val time = day.atTime(8, 0)
        val intake = MedicationIntakeRecord("fact-intake", "fact", 0, "actor", listOf(
            MedicationIntakeRevision("fact-r1", "fact-intake", 1, MedicationIntakeOutcome.COMPLETED,
                time, time, null, null, MedicationIntakeChangeKind.REGISTERED, null,
                time.toInstant(ZoneOffset.UTC).toEpochMilli(), "actor"),
        ))
        val report = buildFollowUpReport(day, day, "patient", MedicalCareOverview(emptyList(), listOf(plan), listOf(intake)),
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC, now = day.atTime(10, 0))
        assertTrue("fact" in report.medicationPlanIds)
        assertEquals(1, report.summaries.single { it.label == "Registros de toma" }.count)
        assertEquals(1, report.rows.count { it.section == "Medicamentos" })
        assertFalse(report.rows.any { it.section == "Indicaciones médicas" })
    }

    @Test fun glucoseStatisticsAreSeparatedByDeclaredContextWithoutAssumingMealTiming() {
        val now = day.atTime(17, 0)
        fun reading(id: String, value: String, context: GlucoseContext?, hour: Int) =
            MeasurementRecord(id, MeasurementValue.Glucose(value, context), day.atTime(hour, 0), day.atTime(hour, 0))
        val records = listOf(
            reading("fasting-1", "90", GlucoseContext.FASTING, 7),
            reading("fasting-2", "110", GlucoseContext.FASTING, 8),
            reading("after-meal", "130", GlucoseContext.AFTER_MEAL, 10),
            reading("unspecified", "100", null, 11),
            reading("unspecified-2", "120", GlucoseContext.UNSPECIFIED, 12),
            reading("non-numeric", "HI", GlucoseContext.FASTING, 13),
            reading("future", "150", GlucoseContext.FASTING, 18),
        )
        val report = buildFollowUpReport(day, day, "patient", overview, records, emptyList(),
            zoneId = ZoneOffset.UTC, now = now)
        val stats = requireNotNull(report.statistics).glucoseByContext
        assertEquals(2, stats.getValue(GlucoseContext.FASTING).count)
        assertEquals(100.0, stats.getValue(GlucoseContext.FASTING).average, 0.001)
        assertEquals(1, stats.getValue(GlucoseContext.AFTER_MEAL).count)
        assertEquals(130.0, stats.getValue(GlucoseContext.AFTER_MEAL).average, 0.001)
        assertEquals(2, stats.getValue(GlucoseContext.UNSPECIFIED).count)
        assertEquals(110.0, stats.getValue(GlucoseContext.UNSPECIFIED).average, 0.001)
        assertEquals(6, report.measurements.size)
        assertEquals(now, report.snapshotAt)
        assertEquals(now, report.cutoffAt)
    }

    @Test fun currentTreatmentUsesHistoricalReportCutoffRatherThanExportTime() {
        val before = samplePlan("before", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(
            revision("before", 1, day.minusDays(2), MedicationPlanChangeKind.CREATED),
            revision("before", 2, day.plusDays(1), MedicationPlanChangeKind.SUSPENDED, "Motivo ficticio"),
        ))
        val current = samplePlan("current", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(
            revision("current", 1, day.plusDays(1), MedicationPlanChangeKind.CREATED)
                .copy(startsOn = day.plusDays(2)),
        ))
        val future = samplePlan("future", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(
            revision("future", 1, day.plusDays(1), MedicationPlanChangeKind.CREATED)
                .copy(startsOn = day.plusDays(10)),
        ))
        val captured = day.plusDays(5).atTime(10, 0)
        val report = buildFollowUpReport(day, day, "patient",
            MedicalCareOverview(emptyList(), listOf(before, current, future)), emptyList(), emptyList(),
            zoneId = ZoneOffset.UTC, now = captured)
        assertTrue("before" in report.medicationPlanIds)
        assertFalse("current" in report.medicationPlanIds)
        assertEquals(listOf("before"), report.currentMedications.map { it.planId })
        assertEquals(captured, report.snapshotAt)
        assertEquals(day.atTime(LocalTime.MAX), report.cutoffAt)
    }

    @Test fun treatmentPresentationAndActualTimeKeepTheirDeclaredMeaning() {
        val prior = revision("format", 1, day.minusDays(1), MedicationPlanChangeKind.CREATED)
            .copy(unitOrPresentation = "TABLET")
        val plan = samplePlan("format", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(prior))
        val scheduled = day.atTime(8, 0)
        val actual = day.atTime(8, 15)
        val intake = MedicationIntakeRecord("fact", "format", 0, "actor", listOf(
            MedicationIntakeRevision("fact-r1", "fact", 1, MedicationIntakeOutcome.COMPLETED,
                scheduled, actual, MedicationAmount("1", "CAPSULE"), null,
                MedicationIntakeChangeKind.REGISTERED, null,
                actual.toInstant(ZoneOffset.UTC).toEpochMilli(), "actor"),
        ))
        val report = buildFollowUpReport(day, day, "patient",
            MedicalCareOverview(emptyList(), listOf(plan), listOf(intake)), emptyList(), emptyList(),
            zoneId = ZoneOffset.UTC, now = day.atTime(12, 0))
        val indication = report.rows.single { it.section == "Indicaciones médicas" }
        val fact = report.rows.single { it.section == "Medicamentos" }
        assertTrue(indication.detail.contains("Presentación: Tableta"))
        assertFalse(indication.detail.contains("TABLET"))
        assertTrue(fact.detail.contains("Cantidad declarada: 1 Cápsula"))
        assertEquals(scheduled, fact.scheduledAt)
        assertEquals(actual, fact.actualAt)
        assertEquals("TABLET", plan.currentRevision.unitOrPresentation)
    }

    @Test fun freeTextPresentationIsNotInterpretedAsAProductCode() {
        val prior = revision("custom", 1, day.minusDays(1), MedicationPlanChangeKind.CREATED)
            .copy(unitOrPresentation = "Tableta especial ficticia")
        val plan = samplePlan("custom", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(prior))
        val report = buildFollowUpReport(day, day, "patient", MedicalCareOverview(emptyList(), listOf(plan)),
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC, now = day.atTime(12, 0))
        assertTrue(report.rows.any { it.detail.contains("Presentación: Tableta especial ficticia") })
    }

    @Test fun laterIndicationCorrectionIsNotShownBeforeSnapshotEvenWhenPeriodEndsLater() {
        val first = MedicalIndicationRevision("i1", "indication", 1, "Texto inicial ficticio", day,
            day.atTime(8, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), null, null, null,
            MedicalIndicationStatus.ACTIVE, MedicalIndicationChangeKind.CREATED, null, "actor")
        val later = first.copy(id = "i2", revisionNumber = 2, originalText = "Texto posterior ficticio",
            capturedAtEpochMillis = day.atTime(16, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            changeKind = MedicalIndicationChangeKind.CORRECTED, changeReason = "Motivo ficticio")
        val indication = MedicalIndication("indication", "patient", InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            first.capturedAtEpochMillis, "actor", ClinicalContentMode.REAL_CLINICAL_DATA, listOf(first, later))
        val report = buildFollowUpReport(day, day.plusDays(2), "patient", MedicalCareOverview(listOf(indication), emptyList()),
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC, now = day.atTime(12, 0))
        assertTrue(report.rows.any { it.detail == "Texto inicial ficticio" })
        assertFalse(report.rows.any { it.detail.contains("Texto posterior ficticio") || it.detail.contains("Motivo ficticio") })
    }

    @Test fun medicationSeriesAndIntakeRevisionsKeepDistinctPersistedIdentities() {
        val first = samplePlan("period-one", ClinicalContentMode.REAL_CLINICAL_DATA, "patient",
            listOf(revision("period-one", 1, day, MedicationPlanChangeKind.CREATED)))
            .copy(medicationSeriesId = "series-a")
        val second = samplePlan("period-two", ClinicalContentMode.REAL_CLINICAL_DATA, "patient",
            listOf(revision("period-two", 1, day, MedicationPlanChangeKind.CREATED)))
            .copy(medicationSeriesId = "series-a")
        val separate = samplePlan("other", ClinicalContentMode.REAL_CLINICAL_DATA, "patient",
            listOf(revision("other", 1, day, MedicationPlanChangeKind.CREATED)
                .copy(medicationName = first.currentRevision.medicationName)))
            .copy(medicationSeriesId = "series-b")
        val moment = day.atTime(8, 0)
        fun intake(id: String, planId: String, outcome: MedicationIntakeOutcome) =
            MedicationIntakeRecord(id, planId, 0, "actor", listOf(
                MedicationIntakeRevision("$id-r1", id, 1, outcome, moment,
                    if (outcome == MedicationIntakeOutcome.COMPLETED || outcome == MedicationIntakeOutcome.PARTIAL) moment else null,
                    null, null, MedicationIntakeChangeKind.REGISTERED, null,
                    moment.toInstant(ZoneOffset.UTC).toEpochMilli(), "actor", "slot-$id")
            ))
        val original = intake("corrected", first.id, MedicationIntakeOutcome.PARTIAL)
        val corrected = original.copy(revisions = original.revisions + original.currentRevision.copy(
            id = "corrected-r2", revisionNumber = 2, outcome = MedicationIntakeOutcome.COMPLETED,
            changeKind = MedicationIntakeChangeKind.CORRECTED, changeReason = "Corrección ficticia",
            capturedAtEpochMillis = moment.plusHours(1).toInstant(ZoneOffset.UTC).toEpochMilli(),
        ))
        val partial = intake("partial", second.id, MedicationIntakeOutcome.PARTIAL)
        val done = intake("done", separate.id, MedicationIntakeOutcome.COMPLETED)
        val omitted = intake("omitted", second.id, MedicationIntakeOutcome.OMITTED)
        val unconfirmed = intake("unconfirmed", second.id, MedicationIntakeOutcome.UNCONFIRMED)
        val report = buildFollowUpReport(day, day, "patient",
            MedicalCareOverview(emptyList(), listOf(first, second, separate),
                listOf(corrected, corrected, partial, done, omitted, unconfirmed)),
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC, now = day.atTime(12, 0))

        assertEquals(setOf("series-a", "series-b"), report.medicationSeriesIds)
        val rows = report.rows.filter { it.section == "Medicamentos" }
        assertEquals(5, rows.size)
        assertEquals(4, rows.count { it.groupId == "medication-series:series-a" })
        assertEquals(1, rows.count { it.groupId == "medication-series:series-b" })
        assertEquals(1, rows.count { it.sourceId == "medication-intake:corrected" })
        assertTrue(rows.single { it.sourceId == "medication-intake:corrected" }.detail.startsWith("Se tomó"))
        val counts = report.summaries.associate { it.label to it.count }
        assertEquals(2, counts["Tomas completas"] ?: -1)
        assertEquals(1, counts["Tomas parciales"] ?: -1)
        assertEquals(1, counts["Tomas no realizadas"] ?: -1)
        assertEquals(1, counts["Tomas sin confirmar"] ?: -1)
        assertEquals(1, report.rows.count {
            it.section == "Cambios de medicamentos" && it.sourceId == "medication-intake:corrected"
        })
    }

    @Test fun futureMedicationScheduleIsOnePlannedIntervalAndNeverAnAdministration() {
        val startRevision = revision("planned", 1, day, MedicationPlanChangeKind.CREATED)
        val scheduled = samplePlan("planned", ClinicalContentMode.REAL_CLINICAL_DATA, "patient",
            listOf(startRevision))
        val report = buildFollowUpReport(day, day.plusDays(5), "patient",
            MedicalCareOverview(emptyList(), listOf(scheduled)), emptyList(), emptyList(),
            zoneId = ZoneOffset.UTC, now = day.atTime(12, 0))

        val planned = report.rows.filter { it.section == "Programación de medicamentos" }
        assertEquals(1, planned.size)
        assertTrue(planned.single().detail.contains("08:00"))
        assertTrue(planned.single().detail.contains("La programación no confirma una administración"))
        assertTrue(report.rows.none { it.section == "Medicamentos" })
        assertEquals(0, report.summaries.single { it.label == "Tomas no realizadas" }.count)
        assertEquals(0, report.summaries.single { it.label == "Tomas sin confirmar" }.count)
    }

    @Test fun unconfirmedUsesExpectedOccurrencesThroughCutoffAndDoesNotAssumeLegacyMatches() {
        val zone = java.time.ZoneId.systemDefault()
        val config = MedicationConfiguration(
            form = MedicationProductForm.TABLET, concentrationValue = null, concentrationUnit = null,
            baseAmount = MedicationAmount("1", "tableta"), frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = day, administrations = listOf(8, 9, 14).map { hour ->
                MedicationAdministration("slot-$hour", MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(hour, 0))
            },
        )
        val initial = revision("scheduled", 1, day, MedicationPlanChangeKind.CREATED)
            .copy(configuration = config, capturedAtEpochMillis = day.atStartOfDay(zone).toInstant().toEpochMilli())
        val plan = samplePlan("scheduled", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(initial))
        val occurrence = projectMedicationAdministrations(listOf(plan), day.atStartOfDay(), day.atTime(12, 0))
            .single { it.startsAt.hour == 8 }
        val declared = MedicationIntakeRecord("record", plan.id, 0, "actor", listOf(
            MedicationIntakeRevision("r1", "record", 1, MedicationIntakeOutcome.COMPLETED,
                occurrence.startsAt, occurrence.startsAt, null, null, MedicationIntakeChangeKind.REGISTERED,
                null, occurrence.startsAt.atZone(zone).toInstant().toEpochMilli(), "actor", occurrence.id),
        ))
        val report = buildFollowUpReport(day, day, "patient",
            MedicalCareOverview(emptyList(), listOf(plan), listOf(declared)), emptyList(), emptyList(),
            zoneId = zone, now = day.atTime(12, 0))
        val pending = report.rows.filter { it.sourceId?.startsWith("medication-expected:") == true }
        assertEquals(1, pending.size)
        assertEquals(day.atTime(9, 0), pending.single().scheduledAt)
        assertEquals(1, report.summaries.single { it.label == "Tomas sin confirmar" }.count)
        assertFalse(report.rows.any { it.sourceId?.startsWith("medication-expected:") == true && it.scheduledAt?.hour == 14 })

        val legacy = declared.copy(revisions = declared.revisions.map { it.copy(scheduledOccurrenceId = null) })
        val ambiguous = buildFollowUpReport(day, day, "patient",
            MedicalCareOverview(emptyList(), listOf(plan), listOf(legacy)), emptyList(), emptyList(),
            zoneId = zone, now = day.atTime(12, 0))
        assertEquals(2, ambiguous.rows.count { it.sourceId?.startsWith("medication-expected:") == true })
        assertTrue(ambiguous.rows.any { it.sourceId == "medication-intake:record" })
        assertEquals(0, ambiguous.summaries.single { it.label == "Tomas sin confirmar" }.count)
        assertEquals(2, ambiguous.summaries.single { it.label == "Ocasiones con asociación no verificable" }.count)
    }

    @Test fun distinctDeclarationsKeepTheirIdentityWhileUnconfirmedIsCountedPerOccasion() {
        val zone = ZoneOffset.UTC
        val config = MedicationConfiguration(
            form = MedicationProductForm.TABLET, concentrationValue = null, concentrationUnit = null,
            baseAmount = MedicationAmount("1", "tableta"), frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = day, administrations = listOf(8, 9).map { hour ->
                MedicationAdministration("slot-$hour", MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(hour, 0))
            },
        )
        val initial = revision("multi-plan", 1, day, MedicationPlanChangeKind.CREATED)
            .copy(configuration = config, capturedAtEpochMillis = day.atStartOfDay().toInstant(zone).toEpochMilli())
        val plan = samplePlan("multi-plan", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(initial))
        val slots = projectMedicationAdministrations(listOf(plan), day.atStartOfDay(), day.atTime(12, 0),
            zoneId = zone).sortedBy { it.startsAt }
        fun record(id: String, slot: UpcomingMedicationAdministration, outcome: MedicationIntakeOutcome) =
            MedicationIntakeRecord(id, plan.id, 0, "actor", listOf(
                MedicationIntakeRevision("$id-r1", id, 1, outcome, slot.startsAt,
                    if (outcome == MedicationIntakeOutcome.UNCONFIRMED) null else slot.startsAt,
                    null, null, MedicationIntakeChangeKind.REGISTERED, null,
                    slot.startsAt.plusMinutes(1).toInstant(zone).toEpochMilli(), "actor", slot.id),
            ))
        val declaration = record("responded", slots[0], MedicationIntakeOutcome.COMPLETED)
        val withoutResponse = record("unknown-same-occasion", slots[0], MedicationIntakeOutcome.UNCONFIRMED)
        val unresolved1 = record("unknown-a", slots[1], MedicationIntakeOutcome.UNCONFIRMED)
        val unresolved2 = record("unknown-b", slots[1], MedicationIntakeOutcome.UNCONFIRMED)
        val report = buildFollowUpReport(day, day, "patient",
            MedicalCareOverview(emptyList(), listOf(plan), listOf(declaration, withoutResponse, unresolved1, unresolved2)),
            emptyList(), emptyList(), zoneId = zone, now = day.atTime(12, 0))

        val rows = report.rows.filter { it.section == "Medicamentos" && it.sourceId?.startsWith("medication-intake:") == true }
        assertEquals(4, rows.size) // All persisted declarations remain visible.
        assertEquals(1, report.summaries.single { it.label == "Tomas completas" }.count)
        assertEquals(1, report.summaries.single { it.label == "Tomas sin confirmar" }.count)
        val answeredUnknown = rows.single { it.sourceId == "medication-intake:unknown-same-occasion" }
        assertEquals(false, answeredUnknown.medicationCountsAsUnconfirmed)
        assertTrue(answeredUnknown.detail.contains("Otra declaración de esta ocasión registra una respuesta"))
        assertEquals(1, rows.count { it.medicationCountsAsUnconfirmed == true })
        assertEquals(2, rows.count { it.medicationOutcome == MedicationIntakeOutcome.UNCONFIRMED &&
            it.sourceId in setOf("medication-intake:unknown-a", "medication-intake:unknown-b") })
    }

    @Test fun activityNoticeIsNotPresentedAsClinicalScheduledTime() {
        val zone = ZoneOffset.UTC
        val config = MedicationConfiguration(
            form = MedicationProductForm.PATCH, concentrationValue = null, concentrationUnit = null,
            baseAmount = MedicationAmount("1", "tableta"), frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = day, administrations = listOf(
                MedicationAdministration("bedtime", MedicationAdministrationTiming.ACTIVITY_ONLY,
                    activityRelation = MedicationActivityRelation.BEFORE_SLEEP,
                    activityNoticeTime = LocalTime.of(23, 59))),
        )
        val initial = revision("bedtime-plan", 1, day, MedicationPlanChangeKind.CREATED)
            .copy(configuration = config, capturedAtEpochMillis = day.atStartOfDay().toInstant(zone).toEpochMilli())
        val plan = samplePlan("bedtime-plan", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(initial))
        val occurrence = projectMedicationAdministrations(listOf(plan), day.atStartOfDay(), day.atTime(23, 59),
            zoneId = zone).single()
        val empty = buildFollowUpReport(day, day, "patient", MedicalCareOverview(emptyList(), listOf(plan)),
            emptyList(), emptyList(), zoneId = zone, now = day.plusDays(1).atStartOfDay())
        val expected = empty.rows.single { it.sourceId?.startsWith("medication-expected:") == true }
        assertEquals(ReportScheduledTimeMeaning.ACTIVITY_NOTICE, expected.scheduledTimeMeaning)
        assertEquals(day.atTime(23, 59), expected.scheduledAt)

        val record = MedicationIntakeRecord("bedtime-record", plan.id, 0, "actor", listOf(
            MedicationIntakeRevision("bedtime-r1", "bedtime-record", 1, MedicationIntakeOutcome.COMPLETED,
                occurrence.startsAt, day.atTime(22, 0), null, null, MedicationIntakeChangeKind.REGISTERED,
                null, occurrence.startsAt.toInstant(zone).toEpochMilli(), "actor", occurrence.id),
        ))
        fun report(fact: MedicationIntakeRecord) = buildFollowUpReport(day, day, "patient",
            MedicalCareOverview(emptyList(), listOf(plan), listOf(fact)), emptyList(), emptyList(),
            zoneId = zone, now = day.plusDays(1).atStartOfDay())
        val linked = report(record).rows.single { it.sourceId == "medication-intake:bedtime-record" }
        assertEquals(ReportScheduledTimeMeaning.ACTIVITY_NOTICE, linked.scheduledTimeMeaning)
        assertEquals(MedicationProductForm.PATCH, linked.medicationForm)
        assertEquals(MedicationIntakeOutcome.COMPLETED, linked.medicationOutcome)
        assertEquals(day.atTime(22, 0), linked.actualAt)
        val missingLink = record.copy(revisions = record.revisions.map { it.copy(scheduledOccurrenceId = null) })
        val unlinked = report(missingLink).rows.single { it.sourceId == "medication-intake:bedtime-record" }
        assertEquals(ReportScheduledTimeMeaning.UNKNOWN, unlinked.scheduledTimeMeaning)
        assertNull(unlinked.medicationForm)
        assertEquals(MedicationIntakeOutcome.COMPLETED, unlinked.medicationOutcome)

        val corrected = record.copy(revisions = record.revisions + record.currentRevision.copy(
            id = "bedtime-r2", revisionNumber = 2, outcome = MedicationIntakeOutcome.PARTIAL,
            changeKind = MedicationIntakeChangeKind.CORRECTED, changeReason = "Corrección ficticia",
            capturedAtEpochMillis = occurrence.startsAt.plusSeconds(10).toInstant(zone).toEpochMilli(),
        ))
        val correction = report(corrected).rows.single {
            it.section == "Cambios de medicamentos" && it.sourceId == "medication-intake:bedtime-record"
        }
        assertTrue(correction.detail.contains("Aviso de actividad"))
        assertTrue(correction.detail.contains("Se aplicó → Aplicación parcial"))
    }

    @Test fun anOpenAdministrationWindowIsNotCountedAsUnconfirmed() {
        val zone = ZoneOffset.UTC
        val config = MedicationConfiguration(
            form = MedicationProductForm.TABLET, concentrationValue = null, concentrationUnit = null,
            baseAmount = MedicationAmount("1", "tableta"), frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = day, administrations = listOf(
                MedicationAdministration("window", MedicationAdministrationTiming.TIME_WINDOW,
                    windowStart = LocalTime.of(8, 0), windowEnd = LocalTime.of(10, 0))),
        )
        val initial = revision("window-plan", 1, day, MedicationPlanChangeKind.CREATED)
            .copy(configuration = config, capturedAtEpochMillis = day.atStartOfDay().toInstant(zone).toEpochMilli())
        val plan = samplePlan("window-plan", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(initial))
        fun report(now: LocalDateTime) = buildFollowUpReport(day, day, "patient",
            MedicalCareOverview(emptyList(), listOf(plan)), emptyList(), emptyList(), zoneId = zone, now = now)

        val during = report(day.atTime(9, 0))
        assertEquals(0, during.summaries.single { it.label == "Tomas sin confirmar" }.count)
        assertTrue(during.rows.any { it.sourceId?.startsWith("medication-expected:") == true &&
            it.detail.contains("Ventana en curso") })
        val after = report(day.atTime(10, 1))
        assertEquals(1, after.summaries.single { it.label == "Tomas sin confirmar" }.count)
        assertTrue(after.rows.any { it.sourceId?.startsWith("medication-expected:") == true &&
            it.detail.startsWith("Sin confirmar") })
    }

    @Test fun migratedSlotIdentityChangeKeepsAnUnmatchedOccasionUncertain() {
        val zone = ZoneOffset.UTC
        val initialConfig = MedicationConfiguration(
            form = MedicationProductForm.TABLET, concentrationValue = null, concentrationUnit = null,
            baseAmount = MedicationAmount("1", "tableta"), frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = day, administrations = listOf(
                MedicationAdministration("old-physical-id", MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(8, 0))),
        )
        val original = revision("migration", 1, day, MedicationPlanChangeKind.CREATED)
            .copy(configuration = initialConfig, capturedAtEpochMillis = day.atStartOfDay().toInstant(zone).toEpochMilli())
        val firstPlan = samplePlan("migration", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(original))
        val historical = projectMedicationAdministrations(listOf(firstPlan), day.atStartOfDay(), day.atTime(12, 0),
            zoneId = zone).single()
        val updated = revision("migration", 2, day, MedicationPlanChangeKind.SCHEDULE_CORRECTED, "Cambio ficticio")
            .copy(configuration = initialConfig.copy(administrations = listOf(
                MedicationAdministration("new-physical-id", MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(8, 0)))),
                capturedAtEpochMillis = day.atTime(6, 0).toInstant(zone).toEpochMilli())
        val current = firstPlan.copy(revisions = listOf(original, updated))
        val fact = MedicationIntakeRecord("legacy-linked", current.id, 0, "actor", listOf(
            MedicationIntakeRevision("legacy-linked-r1", "legacy-linked", 1, MedicationIntakeOutcome.COMPLETED,
                historical.startsAt, historical.startsAt, null, null, MedicationIntakeChangeKind.REGISTERED,
                null, historical.startsAt.toInstant(zone).toEpochMilli(), "actor", historical.id),
        ))
        val result = buildFollowUpReport(day, day, "patient",
            MedicalCareOverview(emptyList(), listOf(current), listOf(fact)), emptyList(), emptyList(),
            zoneId = zone, now = day.atTime(12, 0))
        assertEquals(1, result.summaries.single { it.label == "Tomas completas" }.count)
        assertEquals(0, result.summaries.single { it.label == "Tomas sin confirmar" }.count)
        assertEquals(1, result.summaries.single { it.label == "Ocasiones con asociación no verificable" }.count)
        assertTrue(result.rows.any { it.sourceId == "medication-intake:legacy-linked" })
        assertTrue(result.rows.any { it.sourceId?.startsWith("medication-expected:") == true &&
            it.detail.contains("no verificable") })
    }

    @Test fun neverProjectsAnOccasionBeforeTheFirstCapturedConfiguration() {
        val zone = java.time.ZoneId.systemDefault()
        val config = MedicationConfiguration(
            form = MedicationProductForm.TABLET, concentrationValue = null, concentrationUnit = null,
            baseAmount = MedicationAmount("1", "tableta"), frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = day.minusDays(2), administrations = listOf(
                MedicationAdministration("slot", MedicationAdministrationTiming.EXACT_TIME, exactTime = LocalTime.of(8, 0))),
        )
        val created = revision("future-known", 1, day, MedicationPlanChangeKind.CREATED)
            .copy(configuration = config, capturedAtEpochMillis = day.atTime(10, 0).atZone(zone).toInstant().toEpochMilli())
        val plan = samplePlan("future-known", ClinicalContentMode.REAL_CLINICAL_DATA, "patient", listOf(created))
        val report = buildFollowUpReport(day.minusDays(2), day, "patient",
            MedicalCareOverview(emptyList(), listOf(plan)), emptyList(), emptyList(), zoneId = zone, now = day.atTime(12, 0))
        assertTrue(report.rows.none { it.sourceId?.startsWith("medication-expected:") == true })
    }

    @Test fun legacyScheduleWithoutStructuredConfigurationIsReportedAsIncomplete() {
        val plan = samplePlan("legacy", ClinicalContentMode.REAL_CLINICAL_DATA, "patient",
            listOf(revision("legacy", 1, day.minusDays(1), MedicationPlanChangeKind.CREATED)))
        val report = buildFollowUpReport(day, day, "patient", MedicalCareOverview(emptyList(), listOf(plan)),
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC, now = day.atTime(12, 0))
        assertEquals(1, report.summaries.single { it.label == "Pautas sin programación reconstruible" }.count)
    }

    @Test fun separateDeclarationsStayVisibleWhileRevisionsShareOneClinicalRow() {
        val plan = samplePlan("identity", ClinicalContentMode.REAL_CLINICAL_DATA, "patient",
            listOf(revision("identity", 1, day.plusDays(1), MedicationPlanChangeKind.CREATED)))
        val moment = day.atTime(8, 0)
        fun fact(id: String) = MedicationIntakeRecord(id, plan.id, 0, "actor", listOf(
            MedicationIntakeRevision("${id}-r1", id, 1, MedicationIntakeOutcome.PARTIAL, moment, moment,
                MedicationAmount("2", "gotas"), null, MedicationIntakeChangeKind.REGISTERED, null,
                moment.toInstant(ZoneOffset.UTC).toEpochMilli(), "actor", "same-occurrence"),
        ))
        val first = fact("first")
        val revised = first.copy(revisions = first.revisions + first.currentRevision.copy(
            id = "first-r2", revisionNumber = 2, outcome = MedicationIntakeOutcome.COMPLETED,
            actualAmount = MedicationAmount("3", "gotas"), changeKind = MedicationIntakeChangeKind.CORRECTED,
            changeReason = "Motivo ficticio", capturedAtEpochMillis = moment.plusMinutes(5).toInstant(ZoneOffset.UTC).toEpochMilli(),
        ))
        val report = buildFollowUpReport(day, day, "patient",
            MedicalCareOverview(emptyList(), listOf(plan), listOf(revised, fact("second"))),
            emptyList(), emptyList(), zoneId = ZoneOffset.UTC, now = day.atTime(12, 0))
        val rows = report.rows.filter { it.section == "Medicamentos" }
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.detail.contains("relación entre registros no determinada") })
        assertTrue(rows.single { it.sourceId == "medication-intake:first" }.detail.contains("3 gotas"))
        val audit = report.rows.single { it.section == "Cambios de medicamentos" }
        assertTrue(audit.title.contains("Medicamento identity"))
        assertTrue(audit.title.contains("nombre histórico no disponible"))
        assertTrue(audit.detail.contains("2 gotas") && audit.detail.contains("3 gotas"))
        assertTrue(audit.detail.contains("Hora guardada (tipo no verificable)"))
    }

    private fun revision(planId: String, number: Int, date: LocalDate, kind: MedicationPlanChangeKind, reason: String? = null): MedicationPlanRevision {
        val revisionId = "$planId-r$number"
        val captured = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return MedicationPlanRevision(
            id = revisionId, medicationPlanId = planId, revisionNumber = number,
            medicationName = "Medicamento $planId", doseAsIndicated = "Cantidad ficticia",
            unitOrPresentation = null, frequencyAsIndicated = null, startsOn = day.minusDays(10),
            endsOn = null, originalAdditionalInstructions = null,
            scheduledDoses = listOf(ScheduledDose("$revisionId-dose", revisionId, LocalTime.of(8, 0), captured,
                "actor", InformationOrigin.CAREGIVER_RECORD)), capturedAtEpochMillis = captured,
            authorActorId = "actor", changeKind = kind, changeReason = reason,
        )
    }

    private fun samplePlan(id: String, mode: ClinicalContentMode, person: String,
        revisions: List<MedicationPlanRevision>): MedicationPlan = MedicationPlan(
        id = id, personUnderCareId = person, medicalIndicationId = null,
        medicalContentOrigin = InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
        scheduleOrigin = InformationOrigin.CAREGIVER_RECORD, createdAtEpochMillis = 0,
        createdByActorId = "actor", contentMode = mode, revisions = revisions,
    )

    @Test fun previousConsultationRequiresExplicitCompletionAndSamePatient() {
        val now = day.atTime(12, 0)
        fun appointment(id: String, patient: String, date: LocalDate, status: CareEventStatus) = CareEvent(
            id, patient, CareEventDraft(CareEventKind.APPOINTMENT, "Cita ficticia",
                scheduledAt = date.atTime(8, 0), status = status),
            date.minusDays(3).atStartOfDay(), date.minusDays(1).atStartOfDay(), 1,
        )
        val events = listOf(
            appointment("done", "patient", day.minusDays(8), CareEventStatus.COMPLETED),
            appointment("pending", "patient", day.minusDays(1), CareEventStatus.CONFIRMED),
            appointment("other", "other", day.minusDays(2), CareEventStatus.COMPLETED),
            appointment("future", "patient", day.plusDays(1), CareEventStatus.COMPLETED),
        )
        assertEquals(day.minusDays(8), previousCompletedAppointmentDate(events, "patient", now))
        assertNull(previousCompletedAppointmentDate(events, "unrelated", now))
    }

    @Test fun emptyPeriodDoesNotInventAbsenceOfSymptoms() {
        val report = buildFollowUpReport(day, day, "patient", overview, emptyList(), emptyList())
        assertTrue(report.rows.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReversedPeriod() {
        buildFollowUpReport(day, day.minusDays(1), "patient", overview, emptyList(), emptyList())
    }
}
