package com.bitavida.app.platform.report

import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth

/** Produces a stable, readable display name without path separators or control characters. */
internal fun reportShareFileName(start: LocalDate, end: LocalDate, patientName: String): String {
    require(!end.isBefore(start))
    val month = YearMonth.from(start)
    val period = if (start.dayOfMonth == 1 && end == month.atEndOfMonth()) {
        month.toString()
    } else {
        "${start}_${end}"
    }
    val normalized = Normalizer.normalize(patientName, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^A-Za-z0-9]+"), "_")
        .trim('_')
        .take(64)
        .trimEnd('_')
    val patient = if (normalized.isEmpty()) "" else "_$normalized"
    return "Bitavida_Informe_${period}${patient}.pdf"
}
