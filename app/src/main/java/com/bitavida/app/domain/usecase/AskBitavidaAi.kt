package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.education.*
import com.bitavida.app.domain.validation.fitsTextLimit

/**
 * The shared learned MNB engine selects an educational intention with contextual parameters.
 * Curated entity resolution
 * selects a documented ingredient/formulation; neither step evaluates a person's treatment.
 */
class AskBitavidaAi(private val repository: EducationRepository) {
    private val resolver by lazy { DrugNameResolver(repository.drugs()) }

    suspend operator fun invoke(question: String, context: EducationGroup? = null): EducationResult {
        val withinLimit = if (context == EducationGroup.MEDICATIONS) question.length <= EducationTokenizer.MAX_QUESTION_LENGTH
            else fitsTextLimit(question, EducationTokenizer.MAX_QUESTION_LENGTH)
        if (question.isBlank() || !withinLimit) {
            return EducationResult.Fallback(EducationFallback.UNCERTAIN)
        }
        if (context == EducationGroup.REMINDERS) return askReminders(question)
        val drug = resolver.resolve(question)
        if (outsideEducationalScope(question, drug)) {
            return EducationResult.Fallback(EducationFallback.OUTSIDE_SCOPE)
        }
        if (drug.unresolvedSimilarName) {
            return EducationResult.DrugOptions(emptyList(),
                "No identifiqué un principio activo documentado. Busca el nombre genérico que aparece en el envase.")
        }
        val classifiedQuestion = if (drug.exact.isNotEmpty() &&
            EducationTokenizer.words(drug.intentQuestion).all { it == "medicamento" }) {
            "identificar medicamento"
        } else drug.intentQuestion
        val prediction = repository.classify(classifiedQuestion)
        val intent = prediction.topic?.drugIntentOrNull()
        if (drug.exact.size > 1 || drug.exact.isNotEmpty() && drug.requiresConfirmation) {
            return EducationResult.DrugOptions(drug.exact,
                "Confirma el principio activo y la presentación del envase antes de elegir.",
                intent ?: DrugIntent.IDENTIFY)
        }
        if (drug.exact.size == 1) {
            return if (intent != null && drug.exact.single().sections.any { it.intent == intent }) {
                EducationResult.DrugAnswer(drug.exact.single(), intent)
            } else {
                // Confirmed ingredient, but no adequate learned intention: the person chooses
                // its documented overview rather than being sent to an unrelated guide.
                EducationResult.DrugOptions(drug.exact,
                    "Identifiqué el principio activo. Abre su ficha para elegir la información que necesitas.")
            }
        }
        return prediction.topic?.let { topic ->
            if (topic.group == EducationGroup.MEDICATIONS && containsUnresolvedName(question)) {
                EducationResult.DrugOptions(emptyList(),
                    "No identifiqué un principio activo documentado. Busca el nombre genérico que aparece en el envase.")
            } else EducationResult.Answer(repository.entry(topic))
        } ?: EducationResult.Fallback(EducationFallback.UNCERTAIN, prediction.suggestions)
    }

    private suspend fun askReminders(question: String): EducationResult {
        val related = listOf(EducationTopic.REMINDER_IDEAS, EducationTopic.REMINDER_CLEAR_TITLE)
        if (outsideReminderOrganizationScope(question)) {
            return EducationResult.Fallback(EducationFallback.OUTSIDE_SCOPE,
                listOf(EducationTopic.REMINDER_MODULES))
        }
        val prediction = repository.classify(question, EducationGroup.REMINDERS)
        val topic = prediction.topic?.takeIf { it.group == EducationGroup.REMINDERS }
        return topic?.let { EducationResult.Answer(repository.entry(it)) }
            ?: EducationResult.Fallback(EducationFallback.UNCERTAIN,
                prediction.suggestions.filter { it.group == EducationGroup.REMINDERS }.ifEmpty { related })
    }

    /** Guard for the voluntary educational request only; never validates or blocks saved titles. */
    private fun outsideReminderOrganizationScope(question: String): Boolean {
        val words = EducationTokenizer.words(question).toSet()
        val normalized = EducationTokenizer.normalize(question)
        val explainsModules = words.any { it in setOf("diferencia", "diferencias", "modulo", "modulos", "aqui") } &&
            words.any { it in setOf("recordatorios", "registrar", "registro", "bitavida") }
        if (explainsModules) return false
        if (words.any { it in blockedRequests || acuteSignalRoots.any(it::startsWith) }) return true
        if (words.any { it in symptoms } && words.any { it in personalWords }) return true
        if (words.any { it in medicineWords } &&
            words.any { it in treatmentSelection || it in setOf("horario", "horarios", "programar", "recordar") }) return true
        if (words.any { it in measurementWords } &&
            (words.any { it in interpretationWords || it.startsWith("interpret") || it in setOf("medir", "mediciones") } ||
                normalized.any(Char::isDigit))) return true
        return words.any { it in setOf("procedimiento", "procedimientos", "inyectar", "inyecto", "recetar") } ||
            Regex("\\b[0-9]+\\s*(mg|ml|ui|mmhg)\\b").containsMatchIn(normalized)
    }

    /** Local catalogue access; no patient information is stored or passed to the model. */
    fun procedureCatalog(): List<ProcedureGuide> = repository.procedures()

    fun registeredProcedures(name: String): List<ProcedureGuide> =
        exactRegisteredProcedureGuides(name, repository.procedures())

    private fun procedureScopeAllowed(question: String): Boolean {
        val safeNames = EducationTokenizer.normalize(question)
            .replace(Regex("\\b(?:hba1c|a1c)\\b"), "hemoglobina glucosilada")
            .replace(Regex("\\bb\\s*12\\b"), "b doce")
            .replace(Regex("\\bcovid\\s*19\\b"), "covid")
            .replace(Regex("\\btipo\\s*2\\b"), "tipo dos")
        return !personalProcedureQuestion(question) && !outsideEducationalScope(safeNames, resolver.resolve(safeNames))
    }

    fun searchProcedures(query: String): List<ProcedureGuide> {
        if (query.length > EducationTokenizer.MAX_QUESTION_LENGTH || procedureUrgentQuestion(query) ||
            (query.isNotBlank() && !procedureScopeAllowed(query))) return emptyList()
        return searchProcedureGuides(query, repository.procedures())
    }

    /** Returns documented guides or an explicit limitation; never generates a clinical response. */
    fun askProcedure(question: String): ProcedureQuestionResult {
        if (question.isBlank() || question.length > EducationTokenizer.MAX_QUESTION_LENGTH) return ProcedureQuestionResult.Unknown
        if (procedureUrgentQuestion(question)) return ProcedureQuestionResult.Urgent
        if (!procedureScopeAllowed(question)) return ProcedureQuestionResult.OutsideScope
        return resolveProcedureQuestion(question, repository.procedures())
    }

    /**
     * Central routing only when the question names a documented procedure, or explicitly
     * asks for a class of interventions with several candidates. The educational guard
     * runs before retrieval; incomplete matches remain an explicit choice.
     */
    fun centralProcedureQuestion(question: String): ProcedureQuestionResult? {
        val guarded = askProcedure(question)
        if (guarded is ProcedureQuestionResult.Urgent || guarded is ProcedureQuestionResult.OutsideScope) return guarded
        val matches = searchProcedureGuides(question, repository.procedures())
        if (matches.isEmpty()) return null
        val normalized = EducationTokenizer.normalize(question)
        val explicitName = matches.any { guide ->
            (listOf(guide.title) + guide.aliases).any { name ->
                val key = EducationTokenizer.normalize(name)
                (key.length >= 4 && (" $normalized ").contains(" $key ")) ||
                    (key == "oct" && isSpecificProcedureName(normalized, guide, name))
            }
        }
        val broadIntervention = matches.size > 1 &&
            EducationTokenizer.words(question).any { it in setOf("cirugia", "operacion", "procedimiento", "estudio", "analisis") }
        if (!explicitName && !broadIntervention) return null
        val drug = resolver.resolve(question)
        if (drug.exact.isNotEmpty() || drug.unresolvedSimilarName) return null
        return guarded
    }

    fun read(topic: EducationTopic): EducationResult.Answer =
        EducationResult.Answer(repository.entry(topic), fromLibrary = true)

    fun drugCatalog(): List<DrugEducation> = repository.drugs()

    fun readDrug(id: String, intent: DrugIntent = DrugIntent.IDENTIFY): EducationResult.DrugAnswer? =
        repository.drugs().firstOrNull { it.id == id }?.let {
            EducationResult.DrugAnswer(it, intent, fromLibrary = true)
        }

    /** A registered label never uses approximate matches or strips strength/presentation. */
    fun resolveRegisteredDrug(name: String): List<DrugEducation> = resolver.registered(name)

    fun requiresRegisteredDrugConfirmation(name: String): Boolean = resolver.requiresRegisteredConfirmation(name)

    private fun containsUnresolvedName(question: String): Boolean {
        val words = EducationTokenizer.words(question)
        // This is an OOV/entity safeguard, not an intention classifier. Generic education
        // remains available; a named unknown substance never receives a different drug's text.
        return words.any { it.length >= 6 && it !in genericDrugVocabulary &&
            (it.endsWith("ina") || it.endsWith("ida") || it.endsWith("ona") || it.endsWith("zol")) }
    }

    private fun outsideEducationalScope(question: String, drug: DrugResolution): Boolean {
        val normalized = EducationTokenizer.normalize(if (drug.exact.isNotEmpty()) drug.intentQuestion else question)
        val numericScope = normalized.replace(Regex("(?:sglt|dpp|glp) ?[124]"), "")
            .replace("tipo 2", "tipo dos")
        if (numericScope.any(Char::isDigit)) return true
        val words = EducationTokenizer.words(question).toSet()
        if (words.any { it in blockedRequests || acuteSignalRoots.any(it::startsWith) }) return true
        if (words.any { it in symptoms } && words.any { it in personalWords }) return true
        val medicationContext = drug.exact.isNotEmpty() || drug.unresolvedSimilarName || words.any { it in medicineWords }
        if (medicationContext && words.any { word -> medicationAcuteSignalRoots.any(word::startsWith) }) return true
        if (medicationContext && words.any { word -> treatmentManipulationRoots.any(word::startsWith) }) return true
        if (medicationContext && words.any { it in treatmentSelection } && words.any { it in personalWords }) return true
        if (words.any { it in medicineWords } && words.any { it in treatmentSelection } &&
            words.none { it == "bitavida" || it == "biblioteca" || it == "registro" }) return true
        if (medicationContext && words.any { it in personalWords } &&
            words.any { word -> word in personalAssessment || personalAssessmentRoots.any(word::startsWith) }) return true
        // Personal measurement interpretation is outside this educational catalogue.
        if (words.any { it in measurementWords } &&
            (words.any { it in interpretationWords } || words.any { it in setOf("bajar", "subir", "bajarlo", "subirlo") }) &&
            (!medicationContext || words.any { it in personalWords })) {
            return true
        }
        return false
    }

    private companion object {
        val blockedRequests = setOf(
            "dosis", "dosificacion", "ajustar", "ajuste", "suspender", "sustituir", "sustituyo",
            "suspendo", "aumentar", "reducir", "recomiendas", "recomienda", "recetar", "receta",
            "diagnostico", "diagnosticar", "urgencia", "urgencias", "urgente", "emergencia",
            "inconsciente", "convulsion", "convulsiones",
        )
        // Product safety vocabulary, independent of intention training. Prefixes cover Spanish
        // inflections (e.g. gerunds, participles and plurals) of acute warning concepts.
        val acuteSignalRoots = setOf("inconscien", "convulsion", "urgenc", "urgent", "emergenc")
        val medicationAcuteSignalRoots = setOf("desmay")
        val symptoms = setOf(
            "dolor", "duele", "sintomas", "mareo", "mareado", "desmayo", "desmayado",
            "vomito", "sangrado", "respirar", "hipoglucemia",
        )
        val personalWords = setOf("mi", "mis", "me", "tengo", "estoy", "siento", "tuve", "puedo", "papa", "mama", "abuelo", "abuela", "paciente")
        val medicineWords = setOf("medicamento", "medicamentos", "medicina", "medicinas", "pastilla", "pastillas", "tratamiento", "insulina")
        val personalAssessment = setOf("seguro", "segura", "adecuado", "adecuada", "compatible", "conviene", "mejor", "peor")
        val personalAssessmentRoots = setOf("segur", "adecuad", "apropiad", "compatib", "conven", "aconsej", "recomend")
        // Covers treatment replacement, compensatory quantities and physical manipulation,
        // independently of the drug name or the sentence used to request them.
        val treatmentManipulationRoots = setOf(
            "reemplaz", "sustitu", "sustituy", "duplic", "compens", "fraccion", "tritur",
            "moler", "muel", "partir", "partiend", "partas", "partamos", "cortar", "cortand",
        )
        val treatmentSelection = setOf("tomar", "tomo", "usar", "uso", "elegir", "comprar", "inyectar", "inyecto", "cambiar", "cambio")
        val measurementWords = setOf("glucosa", "presion", "peso", "azucar", "glucemia")
        val interpretationWords = setOf("normal", "anormal", "peligroso", "peligrosa", "grave", "bajo", "alta", "alto", "baja")
        val genericDrugVocabulary = setOf(
            "medicina", "medicinas", "medicamento", "medicamentos", "contraindicada", "contraindicado",
            "contraindicaciones", "conocida", "conocido", "familia", "familias", "consulta", "consultas",
            "medida", "medidas", "precaucion", "precauciones", "comida", "comidas", "seguida", "seguido",
            "rapida", "rapido", "prolongada", "prolongado", "liberacion", "metabolismo", "insulina",
        )
    }
}
