package com.bitavida.app.domain.model

import java.text.Normalizer
import java.util.Locale

/** Single versioned source for names, guided questions, validation and offline search. */
object ObservationCatalog {
    val all: List<ObservationDefinition> = listOf(
        definition(1, ObservationCategory.FEET, "Revisión general de pies",
            "Revisa si hay cambios en los pies, como heridas, ampollas, grietas o cambios en la piel.",
            "Observa ambos pies y describe únicamente lo que puedes ver o lo que la persona refiere.",
            listOf("revisar pies", "checar pies", "cuidado de pies"),
            listOf(foot(), multiple("changes", "¿Qué cambios observaste?", "Herida", "Ampolla", "Grieta", "Cambio de color", "Cambio en uñas", "Otro cambio"), text("finding", "Detalles de lo encontrado")), photo = true),
        definition(2, ObservationCategory.FEET, "Heridas, ampollas o rozaduras",
            "Cortes, ampollas, rozaduras o heridas que aparecen o tardan en cerrar.",
            "Describe dónde está la lesión y los cambios visibles, sin intentar establecer su causa.",
            listOf("le salió una ampolla", "cortada", "llaga", "herida que no cierra", "raspon"),
            listOf(zone(true), side(), single("lesion", "¿Qué observaste?", "Corte", "Ampolla", "Rozadura", "Herida", "Otro cambio"), onset(), multiple("appearance", "Aspecto o cambios visibles", "Se ve diferente", "Cambió de tamaño", "Sale líquido", "No ha cerrado", "Otro detalle")), photo = true),
        definition(3, ObservationCategory.FEET, "Callos, grietas o cambios en uñas",
            "Piel endurecida, reseca, agrietada o cambios en las uñas de los pies.",
            "Observa la piel y las uñas del pie; describe el cambio sin nombrarlo como una enfermedad.",
            listOf("callosidad", "piel reseca", "uña diferente", "talon agrietado"),
            listOf(foot(), zone(), multiple("change", "¿Qué cambio observaste?", "Callo", "Resequedad", "Grieta", "Cambio en uñas", "Otro cambio", required = true), onset()), photo = true),
        definition(4, ObservationCategory.FEET, "Hinchazón de pies y piernas",
            "Pies, tobillos o piernas que se ven más hinchados de lo habitual.",
            "Compara con lo habitual y describe la zona hinchada. No necesitas conocer la causa.",
            listOf("tiene los pies hinchados", "piernas hinchadas", "tobillos inflamados", "hinchazon"),
            listOf(single("zone", "¿Dónde lo notaste?", "Pies", "Tobillos", "Piernas", "Más de una zona"), side(true), onset(), continues())),
        definition(5, ObservationCategory.FEET, "Cambios de color o temperatura",
            "Piel que se ve más roja, pálida, morada o se siente diferente al tocarla.",
            "Describe el color o cómo se siente la piel al tocarla, sin interpretar qué lo causó.",
            listOf("pie frio", "piel morada", "pie rojo", "piel caliente", "palida"),
            listOf(zone(true), side(), multiple("change", "¿Qué cambio observaste?", "Más roja", "Más pálida", "Morada", "Más fría", "Más caliente", "Otro cambio", required = true), onset()), photo = true),
        definition(6, ObservationCategory.PHYSICAL, "Mareos o sensación de desmayo",
            "La persona siente que todo le da vueltas, pierde estabilidad o cree que puede desmayarse.",
            "La persona dice que todo le da vueltas, parece inestable o siente que podría desmayarse. No necesitas conocer la causa para registrar lo ocurrido.",
            listOf("se marea", "todo le da vueltas", "se va a desmayar", "perdio equilibrio"),
            listOf(multiple("event", "¿Qué sucedió?", "Se sintió mareado", "Perdió el equilibrio", "Sintió que iba a desmayarse", "Otro detalle", required = true), text("activity", "¿Qué estaba haciendo?"), duration(), repeated())),
        definition(7, ObservationCategory.PHYSICAL, "Temblor o sudoración repentina",
            "Temblor, sudor frío u otros cambios físicos que aparecen repentinamente.",
            "Registra el temblor o sudor que observaste y en qué circunstancia apareció, sin suponer una causa ni una medición.",
            listOf("sudor frio", "esta temblando", "suda de repente", "tembladera"),
            listOf(multiple("symptom", "¿Qué observaste?", "Temblor", "Sudor frío", "Sudoración repentina", "Otro cambio", required = true), duration(), text("circumstance", "¿En qué circunstancia ocurrió?"))),
        definition(8, ObservationCategory.PHYSICAL, "Debilidad o cansancio inusual",
            "Se encuentra más débil o cansado de lo acostumbrado.",
            "Describe en qué actividades lo notaste más débil o cansado respecto de lo habitual.",
            listOf("sin fuerzas", "muy cansado", "agotado", "debil"),
            listOf(multiple("change", "¿Qué cambió?", "Menos fuerza", "Más cansancio", "Necesitó descansar", "Otro cambio", required = true), onset(), continues(), text("activities", "¿En qué actividades lo notaste?"))),
        definition(9, ObservationCategory.PHYSICAL, "Dolor nuevo o persistente",
            "Dolor reciente, diferente al habitual o que permanece durante varios días.",
            "Registra dónde le duele y lo que la persona te cuenta, sin intentar diagnosticar la causa.",
            listOf("le duele", "dolor de cuerpo", "molestia persistente", "dolor de pecho"),
            listOf(zone(true), onset(), single("pattern", "¿Cómo aparece?", "Todo el tiempo", "Por momentos", "No se conoce"), text("details", "Otros detalles observables"))),
        definition(10, ObservationCategory.DIGESTION, "Sed excesiva o boca seca",
            "Tiene más sed de lo habitual o refiere mucha resequedad en la boca.",
            "Describe lo que la persona refiere y si bebe o pide agua más de lo acostumbrado.",
            listOf("mucha sed", "boca reseca", "toma mucha agua", "sed"),
            listOf(multiple("change", "¿Qué observaste?", "Más sed", "Boca seca", "Pide agua con frecuencia", "Otro cambio", required = true), onset(), continues())),
        definition(11, ObservationCategory.DIGESTION, "Orinar más de lo habitual",
            "Va al baño con más frecuencia o necesita levantarse varias veces durante la noche.",
            "Describe el cambio respecto de su rutina y si ocurre de día o de noche.",
            listOf("va mucho al baño", "orina mucho", "se levanta al baño", "hace mucha pipi"),
            listOf(multiple("change", "¿Qué cambió?", "Va más veces al baño", "Se levanta por la noche", "Orina más cantidad", "Otro cambio", required = true), single("period", "¿En qué momento?", "Durante el día", "Durante la noche", "Día y noche", "No se conoce"), onset())),
        definition(12, ObservationCategory.DIGESTION, "Escapes o dificultad para orinar",
            "Tiene escapes de orina o le cuesta comenzar o completar la micción.",
            "Registra los escapes o la dificultad que la persona te cuenta con palabras cotidianas.",
            listOf("se hace pipi", "no puede orinar", "se le sale la orina", "goteo"),
            listOf(multiple("situation", "¿Qué ocurrió?", "Escape de orina", "Le costó comenzar", "No pudo terminar", "Otro detalle", required = true), onset(), text("frequency", "Frecuencia aproximada, si la conoces"))),
        definition(13, ObservationCategory.DIGESTION, "Ardor o molestias al orinar",
            "Refiere ardor, dolor o incomodidad cuando va al baño.",
            "Describe la molestia que refiere al orinar. Este registro no identifica una infección.",
            listOf("le arde al hacer pipi", "dolor al orinar", "molestia al baño"),
            listOf(multiple("symptom", "¿Qué molestia refiere?", "Ardor", "Dolor", "Incomodidad", "Otro detalle", required = true), onset(), continues())),
        definition(14, ObservationCategory.DIGESTION, "Náuseas o vómitos",
            "Tiene ganas de vomitar o ha presentado vómitos.",
            "Distingue las ganas de vomitar de los vómitos que realmente se presentaron.",
            listOf("ganas de vomitar", "vomito", "asco", "devolvio"),
            listOf(single("symptom", "¿Qué presentó?", "Náuseas", "Vómitos", "Ambos"), onset(), text("episodes", "Número aproximado de episodios, si lo conoces"))),
        definition(15, ObservationCategory.DIGESTION, "Cambios en las evacuaciones",
            "Evacúa de forma diferente a lo habitual, con estreñimiento, diarrea u otros cambios.",
            "Describe cómo cambió su manera habitual de evacuar, sin atribuirlo a una enfermedad.",
            listOf("no puede hacer del baño", "diarrea", "estreñimiento", "hace popo diferente"),
            listOf(multiple("change", "¿Qué cambió?", "Le costó evacuar", "Evacuaciones más líquidas", "Más veces", "Menos veces", "Otro cambio", required = true), onset(), continues())),
        definition(16, ObservationCategory.VISION, "Cambios en la visión",
            "Ve borroso, tiene dificultad para enfocar o nota algún cambio en su manera de ver.",
            "Registra lo que la persona cuenta sobre su visión, cuándo empezó y si ocurrió de golpe.",
            listOf("ve borroso", "no ve bien", "no puede enfocar", "perdio la vista"),
            listOf(multiple("change", "¿Qué cambió?", "Ve borroso", "Le cuesta enfocar", "Ve menos", "Otro cambio", required = true), single("eye", "¿En qué ojo?", "Izquierdo", "Derecho", "Ambos", "No se conoce"), single("appearance", "¿Cómo apareció?", "Repentinamente", "Poco a poco", "No se conoce"), onset())),
        definition(17, ObservationCategory.VISION, "Hormigueo, ardor o entumecimiento",
            "Sensaciones extrañas o pérdida de sensibilidad en pies, piernas, manos u otras zonas.",
            "Describe la sensación que refiere y la zona, sin atribuirla a una causa.",
            listOf("se le duermen los pies", "pies dormidos", "manos dormidas", "piquetes", "no siente", "ardor pies"),
            listOf(multiple("sensation", "¿Qué sensación refiere?", "Hormigueo", "Ardor", "Entumecimiento", "Menos sensibilidad", "Otro cambio", required = true), zone(true), side(), onset(), continues())),
        definition(18, ObservationCategory.WELLBEING, "Cambios en el apetito",
            "Come menos, come más o ha cambiado su interés habitual por los alimentos.",
            "Compara con su forma habitual de comer y registra lo que has notado.",
            listOf("no quiere comer", "no tiene hambre", "come mucho", "no come"),
            listOf(single("change", "¿Qué cambió?", "Come menos", "Come más", "Menos interés por comer", "Otro cambio"), onset(), continues())),
        definition(19, ObservationCategory.WELLBEING, "Cambios en el sueño",
            "Duerme menos, duerme más, despierta frecuentemente o descansa de manera diferente.",
            "Describe el cambio de descanso respecto de su rutina habitual.",
            listOf("no duerme", "duerme mucho", "se despierta", "desvelo", "insomnio"),
            listOf(multiple("change", "¿Qué cambió?", "Duerme menos", "Duerme más", "Despierta seguido", "Descansa diferente", "Otro cambio", required = true), onset(), text("frequency", "Frecuencia aproximada, si la conoces"))),
        definition(20, ObservationCategory.WELLBEING, "Confusión o problemas de memoria",
            "Olvida cosas importantes, parece desorientado o muestra cambios en su claridad habitual.",
            "Describe un ejemplo concreto de lo sucedido y si el cambio apareció repentinamente.",
            listOf("esta muy confundido", "no recuerda", "desorientado", "olvida todo"),
            listOf(multiple("event", "¿Qué ocurrió?", "Olvidó algo importante", "Parecía desorientado", "Le costó entender", "Otro cambio", required = true), onset(), single("sudden", "¿El cambio fue repentino?", "Sí", "No", "No se conoce"), repeated())),
        definition(21, ObservationCategory.WELLBEING, "Cambios de ánimo o comportamiento",
            "Está más irritable, preocupado, triste o se comporta de forma diferente a lo habitual.",
            "Registra conductas o expresiones concretas; no necesitas nombrar un trastorno.",
            listOf("esta triste", "irritable", "enojado", "preocupado", "se comporta diferente"),
            listOf(multiple("change", "¿Qué observaste?", "Más irritable", "Más preocupado", "Más triste", "Conducta diferente", "Otro cambio", required = true), onset(), text("situations", "¿En qué situaciones ocurre?"))),
        definition(22, ObservationCategory.MOBILITY, "Caída o golpe",
            "Se cayó, sufrió un golpe o tuvo un accidente al desplazarse.",
            "Describe el hecho, el lugar y los golpes conocidos. Si requiere atención inmediata, no esperes a terminar este registro.",
            listOf("se cayo", "se cayó", "golpazo", "accidente", "tropezo", "resbalo"),
            listOf(single("event", "¿Qué sucedió?", "Se cayó", "Sufrió un golpe", "Otro accidente"), text("place", "¿Dónde ocurrió?", true), single("discomfort", "¿Presentó golpes o molestias?", "Sí", "No", "No se conoce"), zone(), single("head", "¿Se golpeó la cabeza?", "Sí", "No", "No se conoce")), event = true),
        definition(23, ObservationCategory.MOBILITY, "Dificultad para caminar o mantener el equilibrio",
            "Tiene problemas para desplazarse, caminar o mantener la estabilidad.",
            "Describe qué desplazamiento le costó realizar y cómo fue diferente de lo habitual.",
            listOf("no puede caminar", "pierde equilibrio", "camina mal", "inestable"),
            listOf(multiple("difficulty", "¿Qué observaste?", "Le costó caminar", "Le costó levantarse", "Perdió estabilidad", "Otro cambio", required = true), onset(), text("activities", "Actividades afectadas"), continues())),
        definition(24, ObservationCategory.GENERAL, "Fiebre o escalofríos",
            "Se siente caliente, presenta escalofríos o refiere sensación de fiebre.",
            "Registra lo que observaste o lo que la persona refiere. No necesitas inventar una temperatura ni establecer una causa.",
            listOf("calentura", "se siente caliente", "tiembla de frio", "escalofrio"),
            listOf(multiple("symptom", "¿Qué observaste?", "Se siente caliente", "Escalofríos", "Refiere sensación de fiebre", "Otro cambio", required = true), onset(), text("accompanying", "Otros cambios que notaste"))),
        definition(25, ObservationCategory.GENERAL, "Cambios en la piel fuera de los pies",
            "Enrojecimiento, irritación, secreciones u otros cambios en la piel del cuerpo.",
            "Describe el lugar y los cambios visibles de la piel sin intentar identificarlos como enfermedad.",
            listOf("piel irritada", "sarpullido", "ronchas", "sale liquido de piel"),
            listOf(zone(true), multiple("change", "¿Qué observaste?", "Enrojecimiento", "Irritación", "Sale líquido", "Otro cambio", required = true), onset()), photo = true),
        definition(26, ObservationCategory.GENERAL, "Cambios en boca o encías",
            "Sangrado, inflamación, dolor u otros cambios en la boca o las encías.",
            "Registra dónde se encuentra el cambio y lo que la persona refiere sobre su boca.",
            listOf("encias sangran", "boca duele", "encia inflamada", "sangrado boca"),
            listOf(single("zone", "¿Dónde lo notaste?", "Encías", "Lengua", "Labios", "Dentro de la boca", "Otra zona"), multiple("change", "¿Qué observaste?", "Sangrado", "Inflamación", "Dolor", "Otro cambio", required = true), onset())),
        definition(27, ObservationCategory.OTHER, "Otro cambio observado",
            "Registra algo que notaste y que no se parece a ninguna de las opciones anteriores.",
            "Describe hechos con tus palabras: qué ocurrió y qué cambio notaste. Este registro no crea categorías ni declara un diagnóstico.",
            listOf("otros", "otro sintoma", "otro cambio", "algo diferente", "dificultad para respirar", "problemas del habla"),
            listOf(zone(), text("additional", "Detalles adicionales")), photo = true),
    )

    fun get(id: String): ObservationDefinition = requireNotNull(all.firstOrNull { it.id == id })

    /** Accent/case independent phrase and token matching; never interprets clinical meaning. */
    fun search(query: String): List<ObservationDefinition> {
        val normalized = normalizeObservationSearch(query)
        if (normalized.isBlank()) return all
        return all.filter { definition ->
            val texts = listOf(definition.name, definition.description, definition.category.title) + definition.synonyms
            texts.any { normalizeObservationSearch(it).contains(normalized) } ||
                normalized.split(' ').filter { it.length > 2 }.let { words ->
                    words.isNotEmpty() && words.all { word ->
                        texts.any { normalizeObservationSearch(it).contains(word) }
                    }
                }
        }
    }

    private fun definition(
        number: Int, category: ObservationCategory, name: String, description: String,
        context: String, synonyms: List<String>, fields: List<ObservationField>,
        photo: Boolean = true, event: Boolean = false,
    ) = ObservationDefinition(
        id = "OBS-" + number.toString().padStart(3, '0'), category = category, name = name,
        description = description, context = context, synonyms = synonyms, fields = fields,
        photoAllowed = photo, eventOnly = event,
    )

    private fun single(id: String, label: String, vararg options: String) =
        ObservationField(id, label, ObservationFieldType.SINGLE_CHOICE, options.toList(), required = true)
    private fun multiple(id: String, label: String, vararg options: String, required: Boolean = false) =
        ObservationField(id, label, ObservationFieldType.MULTIPLE_CHOICE, options.toList(), required)
    private fun text(id: String, label: String, required: Boolean = false) =
        ObservationField(id, label, ObservationFieldType.TEXT, required = required)
    private fun foot() = single("foot", "¿Qué pie revisaste?", "Izquierdo", "Derecho", "Ambos")
    private fun zone(required: Boolean = false) = text("zone", "Zona del cuerpo", required)
    private fun side(required: Boolean = false) = ObservationField("side", "¿De qué lado?", ObservationFieldType.SINGLE_CHOICE, listOf("Izquierdo", "Derecho", "Ambos", "No aplica", "No se conoce"), required)
    private fun onset() = text("onset", "¿Desde cuándo lo notas? (si lo sabes)")
    private fun continues() = ObservationField(
        "continues",
        "¿Notaste algún cambio desde la última observación?",
        ObservationFieldType.SINGLE_CHOICE,
        listOf("Se mantiene parecido", "Se ve o se siente diferente", "No deseo comparar", "Continúa", "Fue temporal", "No se conoce"),
        required = false,
    )
    private fun duration() = text("duration", "Duración aproximada, si la conoces")
    private fun repeated() = single("repeated", "¿Se ha repetido?", "Ocurrió una vez", "Se ha repetido", "No se conoce")
}

fun normalizeObservationSearch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
