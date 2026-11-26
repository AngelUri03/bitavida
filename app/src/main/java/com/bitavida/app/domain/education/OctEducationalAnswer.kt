package com.bitavida.app.domain.education

/**
 * Selects a documented OCT section for an educational question, without generating medical text.
 * Callers must apply the normal clinical-safety guard and establish the OCT context first.
 */
fun octFollowupSection(question: String): String? {
    val value = EducationTokenizer.normalize(question)
    val words = value.split(' ').filter(String::isNotBlank).toSet()
    // Only a deliberately small vocabulary may inherit context. An unrecognised
    // subject must not be silently treated as OCT, even if it asks about pain.
    val allowed = setOf(
        "a", "al", "de", "del", "el", "en", "es", "esta", "este", "la", "las", "le",
        "lo", "los", "o", "para", "por", "que", "se", "su", "un", "una", "y", "como",
        "cual", "cuando", "cuanto", "cuantos", "puede", "pueden", "piden",
        "prueba", "estudio", "examen", "oct", "tomografia", "coherencia", "optica",
        "duele", "dolor", "dolorosa", "molesta", "gotas", "necesita", "necesitan",
        "dilatar", "dilatacion", "dilatan", "dilatada", "dilatado", "preparacion",
        "ayuno", "despues", "terminar", "termina", "salir", "pasa", "ocurre",
        "dura", "duracion", "minutos", "tiempo", "tarda", "retinografia", "angiografia",
        "comparacion", "diferencia", "diferencias", "misma", "mismo", "igual", "entre",
        "encuentran", "encontrar", "detecta", "detectan", "observan", "resultados",
        "observar", "observa", "ver", "ven", "hay", "diabetes", "liquido", "edema",
        "aparato", "maquina", "equipo", "camara", "funciona", "hacen", "sucede",
        "realiza", "realizan", "procedimiento", "persona", "paciente", "ojo", "ojos",
        "revision", "hacer", "requiere", "requieren", "hace", "ve", "muestra",
        "normalmente", "suele", "toman", "toca", "tocar", "siente", "sentir", "siempre",
    )
    if (words.isEmpty() || words.any { it !in allowed }) return null
    return when {
        words.any { it in setOf("duele", "dolor", "dolorosa", "molesta") } -> "during"
        words.any { it in setOf("gotas", "dilatar", "dilatacion", "dilatan", "dilatada", "dilatado") } ||
            "preparacion" in words || "ayuno" in words -> "preparation"
        words.any { it in setOf("despues", "terminar", "termina", "salir") } -> "after"
        words.any { it in setOf("dura", "duracion", "minutos", "tiempo") } ||
            "cuanto tarda" in value -> "method"
        words.any { it in setOf("retinografia", "angiografia", "comparacion", "diferencia", "diferencias") } -> "comparison"
        words.any { it in setOf("encuentran", "encontrar", "detecta", "detectan", "observan", "resultados") } -> "findings"
        "diabetes" in words || "liquido" in words || "edema" in words -> "purpose"
        words.any { it in setOf("aparato", "maquina", "equipo", "camara", "funciona") } -> "method"
        words.any { it in setOf("hacen", "sucede", "realiza", "realizan", "procedimiento", "toca", "tocar", "sentir", "siente") } -> "during"
        else -> null
    }
}

/** The section text is always taken verbatim from the locally curated guide. */
fun procedureSectionText(guide: ProcedureGuide, section: String): String? = when (section) {
    "what" -> guide.description
    "purpose" -> guide.purpose
    "during" -> guide.during
    "method" -> guide.method
    "preparation" -> guide.preparation
    "after" -> guide.after
    "important" -> guide.important
    "questions" -> guide.questions.joinToString("\n") { "• $it" }
    else -> guide.extraSections.firstOrNull { it.id == section }?.body
}?.takeIf { it.isNotBlank() }
