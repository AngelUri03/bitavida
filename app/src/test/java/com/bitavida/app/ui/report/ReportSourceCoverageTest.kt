package com.bitavida.app.ui.report

import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.domain.usecase.ReportSourceAttribution
import com.bitavida.app.domain.usecase.ReportSourceCoverage
import com.bitavida.app.domain.usecase.ReportSourceIdentity
import com.bitavida.app.domain.usecase.ReportSourceOrigin
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSourceCoverageTest {
    private val day = LocalDate.of(2026, 10, 31)
    private val medication = ReportSourceIdentity("medication", "same-id")
    private val measurement = ReportSourceIdentity("measurement", "same-id")
    private val simulated = ReportSourceAttribution(ReportSourceOrigin.SIMULATED, "octubre-2026")
    private val real = ReportSourceAttribution(ReportSourceOrigin.REAL)

    private fun document(coverage: ReportSourceCoverage?) = buildFollowUpDocument(
        FollowUpReport(day, day, emptyList(), sourceCoverage = coverage), "Paciente", "Cuidadora", day,
    )

    @Test fun missingAttributionNeverBecomesRealAndEqualIdsInDifferentModulesStayDistinct() {
        val coverage = ReportSourceCoverage(setOf(medication, measurement), mapOf(medication to simulated))
        assertEquals(setOf(measurement), coverage.missingSources)
        assertFalse(coverage.isComplete)
        assertFalse(coverage.hasReal)
        assertTrue(coverage.hasSimulated)
        val document = document(coverage)
        assertTrue(document.containsSimulatedData)
        val warning = requireNotNull(document.sourceWarning)
        assertTrue(warning.contains("simulados"))
        assertTrue(warning.contains("no está verificada"))
    }

    @Test fun completeMixedAndSimulatedCoverageProduceDifferentWarnings() {
        val mixed = document(ReportSourceCoverage(setOf(medication, measurement),
            mapOf(medication to simulated, measurement to real)))
        assertTrue(mixed.containsSimulatedData)
        assertTrue(mixed.sourceWarning!!.contains("reales y simulados"))
        val onlySimulated = document(ReportSourceCoverage(setOf(medication), mapOf(medication to simulated)))
        assertTrue(onlySimulated.sourceWarning!!.contains("No utilizar para atención médica"))
    }

    @Test fun unknownEmptyAndLegacyCoverageAreNotDeclaredReal() {
        assertTrue(document(ReportSourceCoverage(emptySet(), emptyMap())).sourceWarning!!.contains("No se ha verificado"))
        assertTrue(document(ReportSourceCoverage(setOf(medication), emptyMap())).sourceWarning!!.contains("No se ha verificado"))
        assertNull(document(null).sourceWarning)
        assertFalse(document(null).containsSimulatedData)
    }

    @Test fun globalDemoScenarioWarnsWithoutClaimingIndividualAttribution() {
        val report = FollowUpReport(day, day, emptyList(), containsSimulatedScenario = true)
        val document = buildFollowUpDocument(report, "Paciente", "Cuidadora", day)
        assertNull(report.sourceCoverage)
        assertTrue(document.containsSimulatedData)
        val warning = requireNotNull(document.sourceWarning)
        assertTrue(warning.contains("datos simulados"))
        assertFalse(warning.contains("reales y simulados"))
    }

    @Test fun snapshotCopiesInputsAndIgnoresUnrelatedAttribution() {
        val required = mutableSetOf(medication)
        val input = mutableMapOf(medication to real, measurement to simulated)
        val coverage = ReportSourceCoverage(required, input)
        required.add(measurement)
        input[medication] = simulated
        assertTrue(coverage.isComplete)
        assertTrue(coverage.hasReal)
        assertFalse(coverage.hasSimulated)
        assertNull(document(coverage).sourceWarning)
    }

    @Test(expected = IllegalArgumentException::class)
    fun simulatedAttributionRequiresScenario() {
        ReportSourceAttribution(ReportSourceOrigin.SIMULATED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun realAttributionCannotClaimScenario() {
        ReportSourceAttribution(ReportSourceOrigin.REAL, "octubre-2026")
    }
}
