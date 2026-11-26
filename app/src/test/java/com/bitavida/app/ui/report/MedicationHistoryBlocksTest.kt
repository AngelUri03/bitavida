package com.bitavida.app.ui.report

import com.bitavida.app.domain.usecase.FollowUpReportRow
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationHistoryBlocksTest {
    private val at = LocalDateTime.of(2026, 9, 1, 6, 0)
    private fun row(section: String, plan: String = "a", revision: Int? = 1,
        time: LocalDateTime = at, detail: String = section) =
        FollowUpReportRow(section, time, "Fármaco ficticio", detail, "Declarado",
            sourceId = "medication-plan:$plan", groupId = "medication-series:fictional",
            revisionNumber = revision)

    @Test fun changeAndIndicationOfOneIdentifiedRevisionShareOneHeadingWithoutLosingDetails() {
        val blocks = medicationHistoryBlocks(listOf(
            row("Cambios de medicamentos", detail = "Plan registrado"),
            row("Indicaciones médicas", detail = "Una tableta"),
        ))
        assertEquals(1, blocks.size)
        assertTrue(blocks.single().detail.contains("Plan registrado"))
        assertTrue(blocks.single().detail.contains("Una tableta"))
        assertTrue(blocks.single().detail.contains("Indicación médica transcrita"))
    }

    @Test fun coincidentNamesTimesOrUnidentifiedRevisionsNeverMerge() {
        val rows = listOf(
            row("Cambios de medicamentos", plan = "a"),
            row("Indicaciones médicas", plan = "b"),
            row("Cambios de medicamentos", plan = "c", revision = null),
            row("Indicaciones médicas", plan = "c", revision = null),
            row("Cambios de medicamentos", plan = "d", revision = 1),
            row("Indicaciones médicas", plan = "d", revision = 2),
            row("Cambios de medicamentos", plan = "e", time = at),
            row("Indicaciones médicas", plan = "e", time = at.plusMinutes(1)),
        )
        assertEquals(rows.size, medicationHistoryBlocks(rows).size)
    }

    @Test fun ambiguousMultiplicityPreservesEverySourceRow() {
        val rows = listOf(row("Cambios de medicamentos"), row("Indicaciones médicas"),
            row("Indicaciones médicas", detail = "Segunda declaración"))
        assertEquals(3, medicationHistoryBlocks(rows).size)
    }
}
