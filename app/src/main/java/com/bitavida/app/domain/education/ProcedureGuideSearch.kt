package com.bitavida.app.domain.education

/** A documented guide is returned only when the topic is unambiguous; this is not generated text. */
sealed interface ProcedureQuestionResult {
    data class Match(val guide: ProcedureGuide) : ProcedureQuestionResult
    data class Choices(val guides: List<ProcedureGuide>) : ProcedureQuestionResult
    data object Urgent : ProcedureQuestionResult
    data object OutsideScope : ProcedureQuestionResult
    data object Unknown : ProcedureQuestionResult
}

private val procedureStopWords = setOf(
    "a", "al", "de", "del", "el", "en", "es", "la", "las", "lo", "los", "o", "para",
    "por", "que", "se", "un", "una", "y", "como", "cual", "sirve", "hacen", "hace",
    "sucede", "revisan", "durante", "estudio", "prueba", "consulta", "procedimiento", "sobre",
)

/**
 * OCT is an explicitly curated three-letter name for this one guide. Other short aliases
 * stay ambiguous, and matching always uses whole normalized words rather than substrings.
 */
internal fun isSpecificProcedureName(question: String, guide: ProcedureGuide, name: String): Boolean {
    val normalizedQuestion = EducationTokenizer.normalize(question)
    val key = EducationTokenizer.normalize(name)
    val admissible = key.length >= 4 || (guide.id == "oct" && key == "oct")
    return admissible && key !in ambiguousProcedureNames &&
        (" $normalizedQuestion ").contains(" $key ")
}

/** Shares the assistant's Unicode/accent normalization; a blank query exposes the local catalogue. */
fun searchProcedureGuides(query: String, guides: List<ProcedureGuide>): List<ProcedureGuide> {
    val normalized = EducationTokenizer.normalize(query.take(EducationTokenizer.MAX_QUESTION_LENGTH))
    if (normalized.isBlank()) return guides
    val terms = normalized.split(' ').filter { it.isNotBlank() && it !in procedureStopWords }
    if (terms.isEmpty()) return emptyList()
    return guides.mapNotNull { guide ->
        val phrases = (listOf(guide.title) + guide.aliases).map(EducationTokenizer::normalize)
        val score = phrases.maxOfOrNull { phrase ->
            when {
                phrase == normalized -> 1000 + phrase.length
                phrase.startsWith(normalized) -> 800 + normalized.length
                normalized.contains(phrase) && phrase.length >= 4 -> 700 + phrase.length
                phrase == "oct" && isSpecificProcedureName(normalized, guide, phrase) -> 703
                terms.all { it in phrase } -> 400 + terms.sumOf { it.length }
                else -> 0
            }
        } ?: 0
        if (score > 0) guide to score else null
    }.sortedWith(compareByDescending<Pair<ProcedureGuide, Int>> { it.second }.thenBy { it.first.title })
        .map { it.first }
}

/**
 * Terms that name a broad activity cannot establish which documented procedure was ordered.
 * They remain searchable as suggestions; the caregiver must choose an exact guide.
 */
private val ambiguousProcedureNames = setOf(
    "consulta de seguimiento", "consulta de diabetes", "funcion renal", "ver como estan los rinones", "creatinina",
    "proteina en orina", "circulacion pies", "sensibilidad del pie", "prueba de vibracion",
    "examen de retina", "foto del fondo del ojo", "revision de retina", "cirugia de retina",
    "destapar venas de las piernas", "destapar arterias de las piernas", "acceso vascular",
    "vacuna neumonia", "vitamina b12", "vitamina b 12", "b12", "b 12",
)

/** Registered events only link on complete, specific equality; approximate and broad aliases are excluded. */
fun exactRegisteredProcedureGuides(name: String, guides: List<ProcedureGuide>): List<ProcedureGuide> {
    val normalized = EducationTokenizer.normalize(name)
    if (normalized.isBlank() || normalized in ambiguousProcedureNames) return emptyList()
    return guides.filter { guide ->
        (listOf(guide.title) + guide.aliases).any { EducationTokenizer.normalize(it) == normalized }
    }
}

internal fun procedureUrgentQuestion(question: String): Boolean {
    val value = EducationTokenizer.normalize(question)
    return listOf(
        "no respira", "no puede respirar", "no logra respirar", "le falta el aire",
        "dificultad para respirar", "dificultad al respirar", "se esta ahogando",
        "dolor de pecho", "dolor en el pecho", "opresion en el pecho",
        "sangrado abundante", "sangrado que no para", "hemorragia", "convulsion",
        "inconscien", "perdio el conocimiento", "confusion repentina", "emergencia", "urgencia",
    ).any { it in value }
}

/**
 * Fail closed on personal symptoms, results and treatment decisions before guide matching.
 * This is a conservative educational guard, not a medical triage system.
 */
internal fun personalProcedureQuestion(question: String): Boolean {
    val normalized = EducationTokenizer.normalize(question)
        .replace(Regex("\\b(?:hba1c|a1c)\\b"), "hemoglobina glucosilada")
            .replace(Regex("\\bb\\s*12\\b"), "b doce")
        .replace(Regex("\\bcovid\\s*19\\b"), "covid")
        .replace(Regex("\\btipo\\s*2\\b"), "tipo dos")
    if (normalized.any(Char::isDigit)) return true
    val words = normalized.split(' ').filter(String::isNotBlank).toSet()
    val personal = words.any { it in setOf(
        "mi", "mis", "me", "le", "su", "sus", "yo", "tengo", "tiene", "siento", "siente",
        "presenta", "estoy", "puedo", "voy", "familiar", "paciente", "mama", "madre",
        "papa", "padre", "abuelo", "abuela", "hermano", "hermana", "esposo", "esposa",
    ) }
    val symptoms = words.any { word -> listOf(
        "dolor", "duele", "fiebr", "sangr", "vomit", "mare", "desmay", "hinch",
        "inflam", "ardor", "arde", "borros", "nubl", "entum", "adormec", "supur",
        "herida", "ulcera", "infect", "convulsion", "respirar", "ahog",
        "empeor", "debil", "confus", "perdiendo", "negr", "necros", "rojiz", "ampolla", "pus",
    ).any(word::startsWith) }
    val decision = words.any { word -> listOf(
        "interpret", "recomend", "diagnostic", "suspend", "cambi", "quit", "dej",
        "oper", "conven", "indic", "recuper", "probabil", "resultado", "riesg",
        "necesit", "deber", "requier", "aconsej", "segur",
    ).any(word::startsWith) || word in setOf("debo", "debe", "normal", "anormal", "alto", "alta", "bajo",
        "baja", "bajita", "bajito", "elevado", "elevada", "hago", "hacerle") }
    val medication = words.any { it in setOf(
        "medicamento", "medicamentos", "medicina", "medicinas", "pastilla", "pastillas",
        "insulina", "tratamiento", "farmaco", "farmacos",
    ) }
    val treatmentAction = words.any { word -> listOf(
        "dej", "quit", "suspend", "cambi", "tom", "pon", "aplic", "inyect",
        "administr", "ajust", "duplic", "reduc", "aument",
    ).any(word::startsWith) }
    val numbersInWords = normalized.replace("tres meses", "").split(' ').any { it in setOf(
        "uno", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho",
        "nueve", "diez", "once", "doce", "cien", "ciento",
    ) }
    return (personal && (symptoms || decision || numbersInWords)) || (medication && treatmentAction)
}

/** Resolve only full documented names or aliases; partial/ambiguous results require an explicit choice. */
internal fun resolveProcedureQuestion(question: String, guides: List<ProcedureGuide>): ProcedureQuestionResult {
    val matches = searchProcedureGuides(question, guides)
    if (matches.isEmpty()) return ProcedureQuestionResult.Unknown
    val normalized = EducationTokenizer.normalize(question)
    val strong = matches.filter { guide ->
        (listOf(guide.title) + guide.aliases).any { name ->
            isSpecificProcedureName(normalized, guide, name)
        }
    }
    return when {
        strong.size == 1 -> ProcedureQuestionResult.Match(strong.single())
        strong.size > 1 -> ProcedureQuestionResult.Choices(strong)
        else -> ProcedureQuestionResult.Choices(matches)
    }
}
