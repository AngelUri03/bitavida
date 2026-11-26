package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.model.CareEventPurpose
import java.text.Normalizer
import java.util.Locale

/** Vocabulary for optional, explicit selection; it never recommends care or asserts availability. */
internal data class AppointmentSuggestion(val label: String, val aliases: String = "")

internal val appointmentTitles: Map<CareEventPurpose, List<AppointmentSuggestion>> = mapOf(
    CareEventPurpose.MEDICAL_CONSULTATION to listOf(
        "Consulta con medicina familiar", "Consulta de seguimiento de diabetes",
        "Consulta con endocrinología", "Consulta con oftalmología", "Consulta de medicina interna",
        "Consulta con nefrología", "Consulta con cardiología", "Consulta de nutrición",
        "Consulta de geriatría", "Revisión médica de pies", "Consulta odontológica",
        "Consulta de seguimiento", "Consulta con ginecología y obstetricia",
    ).map { AppointmentSuggestion(it) },
    CareEventPurpose.STUDY to listOf(
        AppointmentSuggestion("Hemoglobina glucosilada (HbA1c)", "a1c hemoglobina glicosilada"),
        AppointmentSuggestion("Glucosa en sangre", "glucemia"),
        AppointmentSuggestion("Química sanguínea", "qs"),
        AppointmentSuggestion("Biometría hemática", "bh hemograma"),
        AppointmentSuggestion("Perfil de lípidos", "colesterol triglicéridos"),
        AppointmentSuggestion("Creatinina sérica"),
        AppointmentSuggestion("Examen general de orina", "ego"),
        AppointmentSuggestion("Relación albúmina/creatinina urinaria", "albuminuria"),
        AppointmentSuggestion("Estudios de función renal"),
        AppointmentSuggestion("Fondo de ojo"),
        AppointmentSuggestion("Electrocardiograma", "ecg ekg"),
        AppointmentSuggestion("Ultrasonido", "ecografía"),
        AppointmentSuggestion("Estudios de sangre", "análisis laboratorio"),
        AppointmentSuggestion("Creatinina"),
        AppointmentSuggestion("Radiografía", "rayos x"),
    ),
    CareEventPurpose.PROCEDURE to listOf(
        "Aplicación de vacuna", "Vacunación contra influenza", "Vacunación contra neumococo",
        "Vacunación contra COVID-19", "Curación programada", "Procedimiento ambulatorio",
        "Revisión especializada de pies", "Aplicación de tratamiento indicado",
    ).map { AppointmentSuggestion(it) },
    CareEventPurpose.ADMINISTRATIVE to listOf(
        "Entrega de resultados", "Solicitud de referencia médica", "Trámite de especialidad",
        "Entrega de documentos", "Renovación de documentación médica", "Programación de estudios",
        "Recoger órdenes médicas", "Trámite de atención médica", "Solicitud de referencia",
        "Renovación de documentación", "Trámite de consulta", "Entrega de documentos médicos",
    ).map { AppointmentSuggestion(it) },
    CareEventPurpose.OTHER to emptyList(),
)

internal val appointmentSpecialties = listOf(
    AppointmentSuggestion("Medicina familiar", "mf medico familiar"),
    AppointmentSuggestion("Medicina interna", "internista"),
    AppointmentSuggestion("Endocrinología", "endocrino"),
    AppointmentSuggestion("Cardiología", "cardio"),
    AppointmentSuggestion("Oftalmología", "oftalmo ojos"),
    AppointmentSuggestion("Nefrología", "nefro"),
    AppointmentSuggestion("Geriatría", "geriatra"),
    AppointmentSuggestion("Nutrición", "nutriologo"),
    AppointmentSuggestion("Ginecología y obstetricia", "gineco obstetra"),
    AppointmentSuggestion("Neurología", "neuro"),
    AppointmentSuggestion("Dermatología", "derma"),
    AppointmentSuggestion("Traumatología y ortopedia", "trauma ortopedia"),
    AppointmentSuggestion("Rehabilitación", "fisioterapia"),
    AppointmentSuggestion("Laboratorio clínico", "analisis"),
    AppointmentSuggestion("Imagenología", "radiologia"),
    AppointmentSuggestion("Trabajo social", "trabajador social"),
)

private fun normalizeSuggestion(value: String): String =
    Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

internal fun findAppointmentSuggestions(query: String, entries: List<AppointmentSuggestion>): List<AppointmentSuggestion> {
    val terms = normalizeSuggestion(query).split(' ').filter(String::isNotBlank)
    if (terms.isEmpty()) return emptyList()
    return entries.filter { entry ->
        val text = normalizeSuggestion("${entry.label} ${entry.aliases}")
        terms.all { it in text }
    }.take(12)
}
