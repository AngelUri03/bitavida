package com.bitavida.app.domain.education

enum class EducationGroup(val label: String) {
    GLUCOSE("Glucosa"),
    PRESSURE("Presión"),
    WEIGHT("Peso"),
    MEASUREMENTS("Uso de Bitavida"),
    MEDICATIONS("Medicamentos"),
    OBSERVATIONS("Observaciones"),
    REMINDERS("Recordatorios"),
}

enum class EducationTopic(val group: EducationGroup, val question: String) {
    GLUCOSE_FASTING(EducationGroup.GLUCOSE, "¿Por qué importa medir la glucosa en ayunas?"),
    GLUCOSE_CONTEXT(EducationGroup.GLUCOSE, "¿Por qué anotar cuándo medí mi glucosa?"),
    GLUCOSE_UNITS(EducationGroup.GLUCOSE, "¿Qué significa mg/dL en el glucómetro?"),
    PRESSURE_NUMBERS(EducationGroup.PRESSURE, "¿Qué significan los números de la presión?"),
    PRESSURE_RECORDING(EducationGroup.PRESSURE, "¿Cómo se guarda una lectura de presión?"),
    WEIGHT_TRACKING(EducationGroup.WEIGHT, "¿Por qué debo registrar mi peso?"),
    WEIGHT_CONDITIONS(EducationGroup.WEIGHT, "¿Por qué usar la misma báscula?"),
    MEASUREMENT_REMINDERS(EducationGroup.MEASUREMENTS, "¿Para qué sirven los recordatorios?"),
    MEASUREMENT_HISTORY(EducationGroup.MEASUREMENTS, "¿Cómo consulto el historial de mediciones?"),
    MEASUREMENT_CORRECTIONS(EducationGroup.MEASUREMENTS, "¿Cómo corrijo una medición registrada?"),
    GLUCOSE_WHAT(EducationGroup.GLUCOSE, "¿Qué es la glucosa?"),
    GLUCOSE_PURPOSE(EducationGroup.GLUCOSE, "¿Para qué sirve medir el azúcar?"),
    GLUCOSE_VARIATIONS(EducationGroup.GLUCOSE, "¿Por qué puede variar la glucosa?"),
    GLUCOSE_INDICATORS(EducationGroup.GLUCOSE, "¿Qué significan HI y LO en el glucómetro?"),
    GLUCOSE_PLANNED(EducationGroup.GLUCOSE, "¿Qué diferencia una medición adicional de una programada?"),
    PRESSURE_WHAT(EducationGroup.PRESSURE, "¿Qué es la presión arterial?"),
    PRESSURE_PURPOSE(EducationGroup.PRESSURE, "¿Por qué medir la presión?"),
    PRESSURE_REST(EducationGroup.PRESSURE, "¿Por qué importa el reposo al medir la presión?"),
    PRESSURE_ACTIVITY(EducationGroup.PRESSURE, "¿Cómo se relacionan actividad física y presión?"),
    PRESSURE_VARIATIONS(EducationGroup.PRESSURE, "¿Por qué cambia la presión entre lecturas?"),
    WEIGHT_VARIATIONS(EducationGroup.WEIGHT, "¿Por qué cambia el peso de un día a otro?"),
    WEIGHT_BMI(EducationGroup.WEIGHT, "¿Qué representa el IMC?"),
    WEIGHT_TARGET(EducationGroup.WEIGHT, "¿Qué es el peso objetivo indicado por el médico?"),
    WEIGHT_COMPARISON(EducationGroup.WEIGHT, "¿Cómo comparar mis registros de peso?"),
    MEASUREMENT_REGISTER(EducationGroup.MEASUREMENTS, "¿Cómo registrar una medición en Bitavida?"),
    MEASUREMENT_FOLLOWUPS(EducationGroup.MEASUREMENTS, "¿Para qué sirven los seguimientos?"),
    MEASUREMENT_STATES(EducationGroup.MEASUREMENTS, "¿Qué significan registrada, sin confirmar y no realizada?"),
    MEASUREMENT_CHARTS(EducationGroup.MEASUREMENTS, "¿Cómo consultar las gráficas?"),
    MEASUREMENT_CONSULTATION(EducationGroup.MEASUREMENTS, "¿Qué información puedo llevar a consulta?"),
    MEASUREMENT_FORGOTTEN(EducationGroup.MEASUREMENTS, "¿Qué pasa si olvidé registrar una medición?"),
    MEDICATION_IDENTIFY(EducationGroup.MEDICATIONS, "¿Cómo identificar el principio activo?"),
    MEDICATION_PURPOSE(EducationGroup.MEDICATIONS, "¿Para qué se utiliza un medicamento?"),
    MEDICATION_MECHANISM(EducationGroup.MEDICATIONS, "¿Cómo funciona un medicamento?"),
    MEDICATION_SIDE_EFFECTS(EducationGroup.MEDICATIONS, "¿Cómo consultar efectos secundarios?"),
    MEDICATION_CONTRAINDICATIONS(EducationGroup.MEDICATIONS, "¿Qué significa contraindicación?"),
    MEDICATION_PRECAUTIONS(EducationGroup.MEDICATIONS, "¿Qué precauciones conviene consultar?"),
    MEDICATION_INTERACTIONS(EducationGroup.MEDICATIONS, "¿Qué es una interacción entre medicamentos?"),
    MEDICATION_FORMULATIONS(EducationGroup.MEDICATIONS, "¿Por qué importa conocer la presentación?"),
    MEDICATION_CONSULTATION(EducationGroup.MEDICATIONS, "¿Qué preguntarle al médico sobre un medicamento?"),
    MEDICATION_APP_USE(EducationGroup.MEDICATIONS, "¿Cómo consultar medicamentos en Bitavida?"),
    OBSERVATION_GENERAL(EducationGroup.OBSERVATIONS, "¿Por qué conviene observar cambios en la salud?"),
    OBSERVATION_APP_USE(EducationGroup.OBSERVATIONS, "¿Cómo usar Observaciones en Bitavida?"),
    OBS_001_FEET_REVIEW(EducationGroup.OBSERVATIONS, "¿Por qué revisar los pies?"),
    OBS_002_WOUNDS(EducationGroup.OBSERVATIONS, "¿Por qué observar heridas, ampollas o rozaduras?"),
    OBS_003_CALLOUSES_NAILS(EducationGroup.OBSERVATIONS, "¿Por qué observar callos, grietas y uñas?"),
    OBS_004_SWELLING(EducationGroup.OBSERVATIONS, "¿Qué puede significar la hinchazón de pies o piernas?"),
    OBS_005_COLOR_TEMPERATURE(EducationGroup.OBSERVATIONS, "¿Por qué observar color y temperatura de la piel?"),
    OBS_006_DIZZINESS(EducationGroup.OBSERVATIONS, "¿Por qué registrar mareos o sensación de desmayo?"),
    OBS_007_TREMOR_SWEATING(EducationGroup.OBSERVATIONS, "¿Por qué observar temblor o sudoración repentina?"),
    OBS_008_WEAKNESS_FATIGUE(EducationGroup.OBSERVATIONS, "¿Por qué observar debilidad o cansancio inusual?"),
    OBS_009_PAIN(EducationGroup.OBSERVATIONS, "¿Por qué registrar dolor nuevo o persistente?"),
    OBS_010_THIRST_DRY_MOUTH(EducationGroup.OBSERVATIONS, "¿Qué puede significar mucha sed o boca seca?"),
    OBS_011_FREQUENT_URINATION(EducationGroup.OBSERVATIONS, "¿Por qué observar si orina más de lo habitual?"),
    OBS_012_URINATION_DIFFICULTY(EducationGroup.OBSERVATIONS, "¿Por qué registrar escapes o dificultad para orinar?"),
    OBS_013_PAINFUL_URINATION(EducationGroup.OBSERVATIONS, "¿Por qué observar ardor o molestias al orinar?"),
    OBS_014_NAUSEA_VOMITING(EducationGroup.OBSERVATIONS, "¿Por qué registrar náuseas o vómitos?"),
    OBS_015_BOWEL_CHANGES(EducationGroup.OBSERVATIONS, "¿Por qué observar cambios en las evacuaciones?"),
    OBS_016_VISION(EducationGroup.OBSERVATIONS, "¿Por qué observar cambios en la visión?"),
    OBS_017_TINGLING_NUMBNESS(EducationGroup.OBSERVATIONS, "¿Por qué registrar hormigueo, ardor o entumecimiento?"),
    OBS_018_APPETITE(EducationGroup.OBSERVATIONS, "¿Por qué observar cambios en el apetito?"),
    OBS_019_SLEEP(EducationGroup.OBSERVATIONS, "¿Por qué observar cambios en el sueño?"),
    OBS_020_CONFUSION_MEMORY(EducationGroup.OBSERVATIONS, "¿Por qué registrar confusión o problemas de memoria?"),
    OBS_021_MOOD_BEHAVIOR(EducationGroup.OBSERVATIONS, "¿Por qué observar cambios de ánimo o comportamiento?"),
    OBS_022_FALL(EducationGroup.OBSERVATIONS, "¿Por qué registrar una caída o golpe?"),
    OBS_023_WALKING_BALANCE(EducationGroup.OBSERVATIONS, "¿Por qué observar dificultad para caminar o mantener el equilibrio?"),
    OBS_024_FEVER_CHILLS(EducationGroup.OBSERVATIONS, "¿Por qué registrar fiebre o escalofríos?"),
    OBS_025_SKIN_CHANGES(EducationGroup.OBSERVATIONS, "¿Por qué observar cambios en la piel fuera de los pies?"),
    OBS_026_MOUTH_GUMS(EducationGroup.OBSERVATIONS, "¿Por qué observar cambios en boca o encías?"),
    OBS_027_OTHER_CHANGE(EducationGroup.OBSERVATIONS, "¿Cómo describir otro cambio observado?"),
    REMINDER_IDEAS(EducationGroup.REMINDERS, "¿Qué pendientes del cuidado puedo recordar?"),
    REMINDER_ROUTINE(EducationGroup.REMINDERS, "¿Cómo organizar los pendientes cotidianos?"),
    REMINDER_PREPARATION(EducationGroup.REMINDERS, "¿Cómo evitar olvidar preparativos importantes?"),
    REMINDER_SHOPPING(EducationGroup.REMINDERS, "¿Cómo organizar las compras del cuidado?"),
    REMINDER_SUPPLIES(EducationGroup.REMINDERS, "¿Cómo revisar los artículos que faltan?"),
    REMINDER_BATTERIES(EducationGroup.REMINDERS, "¿Cómo recordar reponer pilas de un dispositivo?"),
    REMINDER_DOCUMENTS(EducationGroup.REMINDERS, "¿Cómo preparar documentos y papeles?"),
    REMINDER_FOLDER(EducationGroup.REMINDERS, "¿Cómo organizar una carpeta de documentos?"),
    REMINDER_PICKUP(EducationGroup.REMINDERS, "¿Cómo recordar recoger documentos o un pedido?"),
    REMINDER_FAMILY(EducationGroup.REMINDERS, "¿Cómo coordinar ayuda con mi familia?"),
    REMINDER_CALL(EducationGroup.REMINDERS, "¿Cómo recordar una llamada acordada?"),
    REMINDER_HANDOFF(EducationGroup.REMINDERS, "¿Cómo comunicar un pendiente a quien ayudará?"),
    REMINDER_OUTING(EducationGroup.REMINDERS, "¿Qué puedo preparar antes de salir con mi familiar?"),
    REMINDER_TRANSPORT(EducationGroup.REMINDERS, "¿Cómo confirmar la logística de un traslado?"),
    REMINDER_OBJECTS(EducationGroup.REMINDERS, "¿Cómo preparar los objetos necesarios para una salida?"),
    REMINDER_SMALL_TASKS(EducationGroup.REMINDERS, "¿Cómo organizar pendientes sin llenar la aplicación?"),
    REMINDER_FORGOTTEN(EducationGroup.REMINDERS, "¿Qué hago si olvidé un recordatorio?"),
    REMINDER_REVIEW(EducationGroup.REMINDERS, "¿Cómo revisar qué pendientes siguen siendo útiles?"),
    REMINDER_APP_USE(EducationGroup.REMINDERS, "¿Cómo crear y resolver recordatorios en Bitavida?"),
    REMINDER_POSTPONE(EducationGroup.REMINDERS, "¿Cómo puedo posponer un recordatorio?"),
    REMINDER_MODULES(EducationGroup.REMINDERS, "¿Cuál es la diferencia entre Recordatorios y Medicamentos?"),
    REMINDER_CLEAR_TITLE(EducationGroup.REMINDERS, "¿Cómo escribir un recordatorio más claro?"),
}

data class EducationSource(
    val title: String,
    val reference: String,
    val consultedOn: String = "Fecha no documentada",
)

enum class EducationReviewStatus { PENDING_HUMAN_REVIEW }

data class EducationEntry(
    val topic: EducationTopic,
    val title: String,
    val explanation: String,
    val example: String?,
    val related: List<EducationTopic>,
    val source: EducationSource,
    val version: String = "1.0",
    val reviewStatus: EducationReviewStatus = EducationReviewStatus.PENDING_HUMAN_REVIEW,
    val importance: String? = null,
    val details: List<String> = emptyList(),
    val concepts: List<String> = emptyList(),
    /** Stable functional catalogue id; null for general educational topics. */
    val observationId: String? = null,
    val possibleMeanings: List<String> = emptyList(),
    val delayedAttention: String? = null,
    val whatToObserve: List<String> = emptyList(),
    val medicalGuidance: String? = null,
    val additionalSources: List<EducationSource> = emptyList(),
    val searchTerms: List<String> = emptyList(),
    val reminderCategory: ReminderEducationCategory? = null,
    val reminderExamples: List<ReminderSuggestion> = emptyList(),
)

data class IntentPrediction(
    val topic: EducationTopic?,
    /** Model posterior, deliberately never presented as clinical certainty. */
    val posterior: Double,
    val vocabularyCoverage: Double,
    val suggestions: List<EducationTopic> = emptyList(),
)

enum class EducationFallback { UNCERTAIN, OUTSIDE_SCOPE, UNAVAILABLE }

sealed interface EducationResult {
    data class Answer(val entry: EducationEntry, val fromLibrary: Boolean = false) : EducationResult
    data class Fallback(val reason: EducationFallback, val suggestions: List<EducationTopic> = emptyList()) : EducationResult
    data class DrugAnswer(
        val drug: DrugEducation,
        val intent: DrugIntent = DrugIntent.IDENTIFY,
        val fromLibrary: Boolean = false,
    ) : EducationResult
    data class DrugOptions(
        val candidates: List<DrugEducation>,
        val message: String,
        val intent: DrugIntent = DrugIntent.IDENTIFY,
    ) : EducationResult
}

interface EducationRepository {
    suspend fun classify(question: String): IntentPrediction
    /** Contextual parameters use the same local MNB engine without changing existing module predictions. */
    suspend fun classify(question: String, context: EducationGroup?): IntentPrediction = classify(question)
    fun entry(topic: EducationTopic): EducationEntry
    fun drugs(): List<DrugEducation> = emptyList()
    /** Curated offline procedures; absent when the repository has no such catalogue. */
    fun procedures(): List<ProcedureGuide> = emptyList()
}
