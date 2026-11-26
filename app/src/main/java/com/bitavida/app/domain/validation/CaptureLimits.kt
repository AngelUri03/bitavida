package com.bitavida.app.domain.validation

/** Capture limits do not constrain loading historical revisions. */
internal enum class CaptureLimit(val maximum: Int, val message: String, val unicodeText: Boolean = false) {
    MEDICATION_NAME(80, "El nombre admite hasta 80 caracteres"),
    SHORT_LABEL(60, "La etiqueta admite hasta 60 caracteres"),
    BRIEF_NOTE(300, "El texto admite hasta 300 caracteres"),
    REMINDER_TITLE(100, "El título admite hasta 100 caracteres", true),
    REMINDER_DETAILS(500, "Los detalles admiten hasta 500 caracteres", true),
    NOTE_TITLE(100, "El título admite hasta 100 caracteres", true),
    NOTE_CONTENT(2_000, "El contenido admite hasta 2000 caracteres", true),
    MEASUREMENT_NOTE(500, "La nota admite hasta 500 caracteres", true),
    CHANGE_REASON(300, "El motivo admite hasta 300 caracteres", true),
    OBSERVATION_DESCRIPTION(500, "La descripción admite hasta 500 caracteres", true),
    OBSERVATION_TEXT(100, "El texto admite hasta 100 caracteres", true),
    CARE_SHORT_TEXT(100, "El texto admite hasta 100 caracteres", true),
    CARE_QUESTION(500, "La pregunta admite hasta 500 caracteres", true),
    SEARCH(100, "La búsqueda admite hasta 100 caracteres", true),
    CARE_INSTRUCTION(1_000, "La indicación admite hasta 1000 caracteres", true),
    UNIT(80, "La unidad admite hasta 80 caracteres"),
    INSTRUCTION(500, "La indicación admite hasta 500 caracteres"),
    REASON(500, "El motivo admite hasta 500 caracteres"),
    OBSERVATION(1_000, "La observación admite hasta 1000 caracteres"),
    ADDITIONAL_INSTRUCTIONS(2_000, "Las notas admiten hasta 2000 caracteres"),
    TRANSCRIPTION(4_000, "La transcripción admite hasta 4000 caracteres"),
    SOURCE(200, "La fuente admite hasta 200 caracteres"),
    TRANSCRIBED_DOSE(240, "La dosis transcrita admite hasta 240 caracteres"),
    INTAKE_AMOUNT(40, "La cantidad registrada admite hasta 40 caracteres"),
    INTEGER(10, "El número admite hasta 10 caracteres"),
    CYCLE_COUNT(4, "El número de ciclos admite hasta 4 dígitos"),
    DATE(10, "La fecha admite hasta 10 caracteres");

    fun characterCount(value: String): Int = if (unicodeText) textCharacterCount(value) else value.length

    fun acceptsStoredOrNew(value: String, original: String? = null): Boolean =
        if (unicodeText) fitsTextLimit(value, maximum, original)
        else value.length <= maximum || (original != null && value == original)

    fun acceptsEdit(previous: String, proposed: String): Boolean =
        if (unicodeText) fitsTextLimit(proposed, maximum) || proposed == previous ||
            isHistoricalTextDeletion(previous, proposed)
        else proposed.length <= maximum || isHistoricalDeletion(previous, proposed)
}

/** Allows progressive deletion of historical text without accepting an oversized replacement. */
internal fun isHistoricalDeletion(previous: String, proposed: String): Boolean {
    if (proposed.length >= previous.length) return false
    var cursor = 0
    for (character in previous) {
        if (cursor < proposed.length && proposed[cursor] == character) cursor++
    }
    return cursor == proposed.length
}
