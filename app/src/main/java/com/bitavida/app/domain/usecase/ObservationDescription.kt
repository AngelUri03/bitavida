package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.domain.validation.limitTextCharacters

import com.bitavida.app.domain.model.ObservationCatalog
import com.bitavida.app.domain.model.ObservationDefinition
import com.bitavida.app.domain.model.ObservationFieldType
import com.bitavida.app.domain.model.ObservationRecord
import com.bitavida.app.domain.model.ObservationResult
import com.bitavida.app.domain.model.normalizeObservationSearch

/**
 * A deterministic, editable proposal, never a saved declaration. Each sentence uses only
 * an accepted answer or the observation selected by the caregiver. Unknown answers do not
 * become negatives; timestamps, causes, severity and diagnoses are never supplied.
 */
fun buildObservationDescription(record: ObservationRecord): String {
    val answers = DeclaredObservationAnswers(ObservationCatalog.get(record.observationId), record.answers)
    if (record.result == ObservationResult.NOT_PERFORMED) return "No se realizó la revisión."
    if (record.result == ObservationResult.NO_CHANGES) {
        val reviewed = when (answers.one("foot")) {
            "Izquierdo" -> "Se revisó el pie izquierdo."
            "Derecho" -> "Se revisó el pie derecho."
            "Ambos" -> "Se revisaron ambos pies."
            else -> ""
        }
        return listOf("No se observaron cambios.", reviewed).filter(String::isNotBlank).joinToString(" ")
    }
    if (answers.isEmpty) return ""
    val sentences = mutableListOf<String>()
    fun add(value: String?) {
        value?.trim()?.takeIf(String::isNotBlank)?.let {
            sentences += it.trimEnd('.', '!', '?') + "."
        }
    }
    fun observed(field: String, vararg wording: Pair<String, String>, location: String = ""): String? {
        val values = answers.mapped(field, *wording)
        return values.takeIf(List<String>::isNotEmpty)?.let {
            (if (it.size == 1) "Se observó " else "Se observaron ") + naturalList(it) + location
        }
    }
    fun person(field: String, vararg wording: Pair<String, String>): String? =
        answers.mapped(field, *wording).takeIf(List<String>::isNotEmpty)?.let {
            "La persona " + naturalList(it)
        }
    val body = observationBodyLocation(answers.one("zone"), answers.one("side"))
    val foot = observationBodyLocation("pies", answers.one("foot"))
    when (record.observationId) {
        "OBS-001" -> {
            add(observed("changes", "Herida" to "una herida", "Ampolla" to "una ampolla",
                "Grieta" to "una grieta", "Cambio de color" to "un cambio de color",
                "Cambio en uñas" to "un cambio en las uñas", location = foot))
            if (sentences.isEmpty()) add(when (answers.one("foot")) {
                "Izquierdo" -> "Se revisó el pie izquierdo"
                "Derecho" -> "Se revisó el pie derecho"
                "Ambos" -> "Se revisaron ambos pies"
                else -> null
            })
            answers.one("finding")?.let { add("La persona cuidadora describió «$it»") }
        }
        "OBS-002" -> {
            add(observed("lesion", "Corte" to "un corte", "Ampolla" to "una ampolla",
                "Rozadura" to "una rozadura", "Herida" to "una herida", location = body))
            add(person("appearance", "Se ve diferente" to "notó que la lesión se veía diferente",
                "Cambió de tamaño" to "notó que la lesión cambió de tamaño",
                "Sale líquido" to "notó que salía líquido", "No ha cerrado" to "notó que la lesión no había cerrado"))
        }
        "OBS-003" -> {
            add(observed("change", "Callo" to "un callo", "Resequedad" to "resequedad",
                "Grieta" to "una grieta", "Cambio en uñas" to "un cambio en las uñas", location = foot))
            answers.one("zone")?.let { add("La zona descrita fue «$it»") }
        }
        "OBS-004" -> add("Se observó hinchazón$body")
        "OBS-005" -> add(observed("change", "Más roja" to "piel más roja", "Más pálida" to "piel más pálida",
            "Morada" to "piel morada", "Más fría" to "piel más fría", "Más caliente" to "piel más caliente",
            location = body))
        "OBS-006" -> {
            add(person("event", "Se sintió mareado" to "se sintió mareada",
                "Perdió el equilibrio" to "perdió el equilibrio",
                "Sintió que iba a desmayarse" to "sintió que iba a desmayarse"))
            answers.one("activity")?.let { add("La actividad que realizaba era «$it»") }
        }
        "OBS-007" -> {
            add(observed("symptom", "Temblor" to "temblor", "Sudor frío" to "sudor frío",
                "Sudoración repentina" to "sudoración repentina"))
            answers.one("circumstance")?.let { add("La circunstancia descrita fue «$it»") }
        }
        "OBS-008" -> {
            add(person("change", "Menos fuerza" to "tuvo menos fuerza", "Más cansancio" to "mostró más cansancio",
                "Necesitó descansar" to "necesitó descansar"))
            answers.one("activities")?.let { add("Se notó durante las actividades descritas como «$it»") }
        }
        "OBS-009" -> {
            add("La persona refirió dolor$body")
            add(when (answers.one("pattern")) {
                "Todo el tiempo" -> "El dolor estuvo presente todo el tiempo"
                "Por momentos" -> "El dolor apareció por momentos"
                else -> null
            })
            answers.one("details")?.let { add("La persona cuidadora añadió «$it»") }
        }
        "OBS-010" -> add(person("change", "Más sed" to "tuvo más sed", "Boca seca" to "refirió boca seca",
            "Pide agua con frecuencia" to "pidió agua con frecuencia"))
        "OBS-011" -> {
            add(person("change", "Va más veces al baño" to "fue más veces al baño",
                "Se levanta por la noche" to "se levantó por la noche para ir al baño",
                "Orina más cantidad" to "orinó más cantidad"))
            add(when (answers.one("period")) {
                "Durante el día" -> "Se notó durante el día"
                "Durante la noche" -> "Se notó durante la noche"
                "Día y noche" -> "Se notó durante el día y la noche"
                else -> null
            })
        }
        "OBS-012" -> add(person("situation", "Escape de orina" to "presentó escapes de orina",
            "Le costó comenzar" to "tuvo dificultad para comenzar a orinar",
            "No pudo terminar" to "no pudo terminar de orinar"))
        "OBS-013" -> answers.mapped("symptom", "Ardor" to "ardor", "Dolor" to "dolor",
            "Incomodidad" to "incomodidad").takeIf(List<String>::isNotEmpty)?.let {
            add("La persona refirió ${naturalList(it)} al orinar")
        }
        "OBS-014" -> {
            add(when (answers.one("symptom")) {
                "Náuseas" -> "La persona presentó náuseas"
                "Vómitos" -> "La persona presentó vómitos"
                "Ambos" -> "La persona presentó náuseas y vómitos"
                else -> null
            })
            answers.one("episodes")?.let { add("Se indicó «$it» como cantidad aproximada de episodios") }
        }
        "OBS-015" -> add(person("change", "Le costó evacuar" to "tuvo dificultad para evacuar",
            "Evacuaciones más líquidas" to "tuvo evacuaciones más líquidas",
            "Más veces" to "evacuó más veces", "Menos veces" to "evacuó menos veces"))
        "OBS-016" -> {
            add(person("change", "Ve borroso" to "refirió que veía borroso",
                "Le cuesta enfocar" to "refirió dificultad para enfocar", "Ve menos" to "refirió que veía menos"))
            add(when (answers.one("eye")) {
                "Izquierdo" -> "El cambio se notó en el ojo izquierdo"
                "Derecho" -> "El cambio se notó en el ojo derecho"
                "Ambos" -> "El cambio se notó en ambos ojos"
                else -> null
            })
            add(when (answers.one("appearance")) {
                "Repentinamente" -> "El cambio apareció repentinamente"
                "Poco a poco" -> "El cambio apareció poco a poco"
                else -> null
            })
        }
        "OBS-017" -> answers.mapped("sensation", "Hormigueo" to "hormigueo", "Ardor" to "ardor",
            "Entumecimiento" to "entumecimiento", "Menos sensibilidad" to "menos sensibilidad")
            .takeIf(List<String>::isNotEmpty)?.let { add("La persona refirió ${naturalList(it)}$body") }
        "OBS-018" -> add(person("change", "Come menos" to "comió menos", "Come más" to "comió más",
            "Menos interés por comer" to "mostró menos interés por comer"))
        "OBS-019" -> add(person("change", "Duerme menos" to "durmió menos", "Duerme más" to "durmió más",
            "Despierta seguido" to "se despertó con frecuencia", "Descansa diferente" to "descansó de forma diferente"))
        "OBS-020" -> {
            add(person("event", "Olvidó algo importante" to "olvidó algo importante",
                "Parecía desorientado" to "parecía desorientada", "Le costó entender" to "tuvo dificultad para entender"))
            add(when (answers.one("sudden")) {
                "Sí" -> "El cambio fue repentino"
                "No" -> "El cambio no fue repentino"
                else -> null
            })
        }
        "OBS-021" -> {
            add(person("change", "Más irritable" to "se mostró más irritable",
                "Más preocupado" to "se mostró más preocupada", "Más triste" to "se mostró más triste",
                "Conducta diferente" to "se comportó de forma diferente"))
            answers.one("situations")?.let { add("Se notó en la situación descrita como «$it»") }
        }
        "OBS-022" -> {
            val event = when (answers.one("event")) {
                "Se cayó" -> "La persona se cayó"
                "Sufrió un golpe" -> "La persona sufrió un golpe"
                "Otro accidente" -> "La persona tuvo otro accidente"
                else -> null
            }
            add(event)
            answers.one("place")?.let { add("Ocurrió en el lugar descrito como «$it»") }
            add(when (answers.one("discomfort")) {
                "Sí" -> "Se declararon golpes o molestias$body"
                "No" -> "No se declararon golpes ni molestias"
                else -> null
            })
            if (answers.one("discomfort") != "Sí" && body.isNotBlank()) add("La zona indicada estaba$body")
            add(when (answers.one("head")) {
                "Sí" -> "La persona se golpeó la cabeza"
                "No" -> "La persona no se golpeó la cabeza"
                else -> null
            })
        }
        "OBS-023" -> {
            add(person("difficulty", "Le costó caminar" to "tuvo dificultad para caminar",
                "Le costó levantarse" to "tuvo dificultad para levantarse", "Perdió estabilidad" to "perdió estabilidad"))
            answers.one("activities")?.let { add("Se notó durante las actividades descritas como «$it»") }
        }
        "OBS-024" -> {
            add(person("symptom", "Se siente caliente" to "se sentía caliente", "Escalofríos" to "tuvo escalofríos",
                "Refiere sensación de fiebre" to "refirió sensación de fiebre"))
            answers.one("accompanying")?.let { add("También se describió «$it»") }
        }
        "OBS-025" -> add(observed("change", "Enrojecimiento" to "enrojecimiento", "Irritación" to "irritación",
            "Sale líquido" to "salida de líquido", location = body))
        "OBS-026" -> {
            val mouthLocation = when (answers.one("zone")) {
                "Encías" -> " en las encías"
                "Lengua" -> " en la lengua"
                "Labios" -> " en los labios"
                "Dentro de la boca" -> " dentro de la boca"
                else -> ""
            }
            add(observed("change", "Sangrado" to "sangrado", "Inflamación" to "inflamación",
                "Dolor" to "dolor", location = mouthLocation))
        }
        "OBS-027" -> {
            if (body.isNotBlank()) add("El cambio se observó$body")
            answers.one("additional")?.let { add("La persona cuidadora describió «$it»") }
        }
    }
    answers.one("onset")?.let { add("El cambio se notó desde $it") }
    answers.one("duration")?.let { add("La duración aproximada indicada fue de $it") }
    answers.one("frequency")?.let { add("La frecuencia aproximada indicada fue «$it»") }
    add(when (answers.one("continues")) {
        "Se mantiene parecido" -> "Se mantiene parecido a la observación anterior"
        "Se ve o se siente diferente" -> "Se ve o se siente diferente a la observación anterior"
        "Continúa" -> "El cambio continúa"
        "Fue temporal" -> "El cambio fue temporal"
        else -> null
    })
    add(when (answers.one("repeated")) {
        "Ocurrió una vez" -> "Ocurrió una vez"
        "Se ha repetido" -> "Se ha repetido"
        else -> null
    })
    return limitTextCharacters(sentences.joinToString(" "), CaptureLimit.OBSERVATION_DESCRIPTION.maximum)
}

private class DeclaredObservationAnswers(definition: ObservationDefinition, raw: Map<String, List<String>>) {
    private val values = definition.fields.associate { field ->
        val supplied = raw[field.id].orEmpty().map(String::trim).filter(String::isNotBlank)
        field.id to if (field.type == ObservationFieldType.TEXT) supplied.take(1) else
            supplied.filter { it in field.options && it != "No se conoce" && it != "No aplica" }.distinct()
                .let { if (field.type == ObservationFieldType.SINGLE_CHOICE) it.take(1) else it }
    }
    val isEmpty: Boolean get() = values.values.all(List<String>::isEmpty)
    fun one(id: String): String? = values[id]?.firstOrNull()
    fun mapped(id: String, vararg wording: Pair<String, String>): List<String> {
        val choices = wording.toMap()
        return values[id].orEmpty().mapNotNull(choices::get)
    }
}

private fun naturalList(values: List<String>): String = when (values.size) {
    0 -> ""
    1 -> values.single()
    else -> values.dropLast(1).joinToString(", ") +
        (if (normalizeObservationSearch(values.last()).startsWith("i")) " e " else " y ") + values.last()
}

/** Wording agrees with the declared side; unknown sides are never converted into "both". */
private fun observationBodyLocation(zone: String?, side: String?): String {
    if (zone.isNullOrBlank()) return when (side) {
        "Izquierdo" -> " del lado izquierdo"
        "Derecho" -> " del lado derecho"
        "Ambos" -> " de ambos lados"
        else -> ""
    }
    val normalized = normalizeObservationSearch(zone).removePrefix("el ").removePrefix("la ")
        .removePrefix("los ").removePrefix("las ")
    val paired = when (normalized) {
        "pie", "pies" -> Triple("pie", "pies", false)
        "tobillo", "tobillos" -> Triple("tobillo", "tobillos", false)
        "pierna", "piernas" -> Triple("pierna", "piernas", true)
        "mano", "manos" -> Triple("mano", "manos", true)
        "brazo", "brazos" -> Triple("brazo", "brazos", false)
        "ojo", "ojos" -> Triple("ojo", "ojos", false)
        "rodilla", "rodillas" -> Triple("rodilla", "rodillas", true)
        else -> null
    }
    if (paired != null) {
        val (singular, plural, feminine) = paired
        return when (side) {
            "Izquierdo" -> " en ${if (feminine) "la" else "el"} $singular ${if (feminine) "izquierda" else "izquierdo"}"
            "Derecho" -> " en ${if (feminine) "la" else "el"} $singular ${if (feminine) "derecha" else "derecho"}"
            "Ambos" -> " en ${if (feminine) "ambas" else "ambos"} $plural"
            else -> " en " + if (normalized == plural) "${if (feminine) "las" else "los"} $plural"
                else "${if (feminine) "la" else "el"} $singular"
        }
    }
    val location = when {
        normalized == "mas de una zona" -> " en más de una zona"
        normalized in listOf("espalda", "cabeza", "cara", "boca", "piel", "lengua", "cintura") -> " en la $normalized"
        normalized in listOf("pecho", "abdomen", "cuello", "talon") -> " en el ${if (normalized == "talon") "talón" else normalized}"
        zone.startsWith("el ", true) || zone.startsWith("la ", true) ||
            zone.startsWith("los ", true) || zone.startsWith("las ", true) -> " en $zone"
        else -> " en la zona descrita como «$zone»"
    }
    return location + when (side) {
        "Izquierdo" -> " del lado izquierdo"
        "Derecho" -> " del lado derecho"
        "Ambos" -> " de ambos lados"
        else -> ""
    }
}
