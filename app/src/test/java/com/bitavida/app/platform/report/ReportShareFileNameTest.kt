package com.bitavida.app.platform.report

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportShareFileNameTest {
    @Test fun fullMonthUsesYearMonthAndNormalizesFictionalName() {
        assertEquals(
            "Bitavida_Informe_2026-11_Juan_Cruz.pdf",
            reportShareFileName(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "Juán Cruz"),
        )
    }

    @Test fun customPeriodAndPathCharactersCannotEscapeTheName() {
        val result = reportShareFileName(
            LocalDate.of(2026, 9, 3), LocalDate.of(2026, 10, 9), "../Ana / Pérez\n",
        )
        assertEquals("Bitavida_Informe_2026-09-03_2026-10-09_Ana_Perez.pdf", result)
        assertFalse(result.contains('/'))
        assertFalse(result.contains('\\'))
    }

    @Test fun emptyOrLongNameStillProducesBoundedPdfName() {
        assertEquals("Bitavida_Informe_2026-02.pdf",
            reportShareFileName(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), " // "))
        assertTrue(reportShareFileName(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "A".repeat(1000)).length < 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun endBeforeStartIsRejected() {
        reportShareFileName(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31), "Ficticia")
    }
}
