package com.bitavida.app.data.education

import com.bitavida.app.domain.education.EducationSource
import com.bitavida.app.domain.education.ProcedureCategory
import com.bitavida.app.domain.education.ProcedureGuide
import com.bitavida.app.domain.education.ProcedureGuideSection
import com.bitavida.app.domain.education.ProcedureRelatedQuestion

/** Contenido local informativo; todas las fichas requieren revisión clínica humana. */
object ProcedureGuideAdditionalCatalog {
    private const val preparation = "La preparación depende del estudio y de las indicaciones recibidas. Confírmala con la unidad médica."
    private val retinalPhotos = EducationSource("ASRS · Guía de imágenes diagnósticas de la retina", "https://www.asrs.org/content/documents/retinal-imaging-guide_spanish-final.pdf", "22 de noviembre de 2026")
    private val retinaDiabetes = EducationSource("NEI · Retinopatía diabética", "https://www.nei.nih.gov/espanol/informacion-sobre-la-salud-ocular/enfermedades-y-afecciones-de-los-ojos/retinopatia-diabetica", "22 de noviembre de 2026")
    private val retinalDilation = EducationSource("NEI · Examen de ojos con dilatación", "https://www.nei.nih.gov/espanol/informacion-sobre-la-salud-ocular/vision-saludable/encontrar-un-oculista/hagase-un-examen-de-los-ojos-con-dilatacion-de-las-pupilas", "22 de noviembre de 2026")
    private val retinalPhotosNoDrops = EducationSource("Leeds NHS · Fotografías y exploraciones de ojos", "https://www.leedsth.nhs.uk/patients/resources/naevus-diagnostic-monitoring-clinic/", "22 de noviembre de 2026")
    private val bloodUrea = EducationSource("MedlinePlus · Examen de nitrógeno ureico en sangre", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/examen-de-nitrogeno-ureico-en-sangre-nus/", "22 de noviembre de 2026")
    private val electrolytes = EducationSource("MedlinePlus · Ionograma", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/ionograma/", "22 de noviembre de 2026")
    private val urineExamination = EducationSource("MedlinePlus · Análisis de orina", "https://medlineplus.gov/spanish/urinalysis.html", "22 de noviembre de 2026")
    private val urineBacteria = EducationSource("NIDDK · Diagnóstico de infección de vejiga", "https://www.niddk.nih.gov/health-information/urologic-diseases/bladder-infection-uti-in-adults/diagnosis", "22 de noviembre de 2026")
    private val completeBloodCount = EducationSource("MedlinePlus · Conteo sanguíneo completo", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/conteo-sanguineo-completo/", "22 de noviembre de 2026")
    private val metabolicPanel = EducationSource("MedlinePlus · Panel metabólico completo", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/panel-metabolico-completo-pmc/", "22 de noviembre de 2026")
    private val hemoglobinA1c = EducationSource("NIDDK · Prueba A1C y diabetes", "https://www.niddk.nih.gov/health-information/diagnostic-tests/a1c-test", "22 de noviembre de 2026")
    private val lipidPanel = EducationSource("MedlinePlus · Niveles de colesterol", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/niveles-de-colesterol/", "22 de noviembre de 2026")
    private val vitaminB12 = EducationSource("MedlinePlus · Prueba de vitamina B", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/prueba-de-vitamina-b/", "22 de noviembre de 2026")
    private val liverTests = EducationSource("MedlinePlus · Pruebas funcionales hepáticas", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/pruebas-funcionales-hepaticas/", "22 de noviembre de 2026")
    private val bloodKetones = EducationSource("MedlinePlus · Prueba de cetonas en sangre", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/prueba-de-cetonas-en-sangre/", "22 de noviembre de 2026")
    private val urineKetones = EducationSource("MedlinePlus · Prueba de cetonas en la orina", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/prueba-de-cetonas-en-la-orina/", "22 de noviembre de 2026")

    private fun entry(
        id: String, title: String, category: ProcedureCategory, url: String,
        aliases: List<String>, description: String, purpose: String, during: String,
        method: String, after: String, important: String, questions: List<String>,
        context: String = "Según indicación médica", cross: List<ProcedureCategory> = emptyList(),
    ) = ProcedureGuide(
        id = id, title = title, aliases = aliases, categories = listOf(category) + cross,
        context = context, description = description, purpose = purpose, during = during,
        method = method, preparation = preparation, after = after, questions = questions,
        // The caller may replace this with a verified source and consultation date.
        // Do not infer the organization or date from the URL.
        important = important, source = EducationSource("Fuente · $title", url),
    )

    val entries: List<ProcedureGuide> = listOf(
        entry("panel-metabolico", "Química sanguínea o panel metabólico", ProcedureCategory.LABS,
            metabolicPanel.reference,
            listOf("quimica sanguinea", "panel metabolico", "quimica sanguinea completa"),
            "La química sanguínea o panel metabólico reúne análisis de varias sustancias presentes en una muestra de sangre. No todas las órdenes llamadas química sanguínea incluyen exactamente las mismas pruebas: es importante revisar qué pidió la unidad.",
            "El conjunto puede aportar información sobre glucosa, minerales y sustancias relacionadas con la función de los riñones o el hígado, según el panel. El profesional lo usa como una pieza de la valoración o del seguimiento; solicitarlo no implica que todas esas funciones estén alteradas.",
            "Un profesional obtiene una pequeña muestra de sangre de una vena del brazo y la coloca en un tubo para el laboratorio. La persona puede notar un pinchazo breve y después una pequeña molestia o moretón. Si existe inquietud o dificultad con la extracción, puede comentarlo al personal.",
            "El laboratorio analiza en la muestra las sustancias incluidas en la orden. Por ejemplo, puede medir glucosa, electrolitos como sodio y potasio, o desechos como creatinina y nitrógeno ureico. Un panel más amplio puede añadir proteínas, bilirrubina y enzimas relacionadas con el hígado. El nombre y la composición concretos deben confirmarse en el informe local.",
            "El equipo revisa el conjunto, los antecedentes y, cuando corresponde, resultados anteriores. Cada componente responde a una pregunta diferente: un número fuera del rango no explica por sí solo su causa. Conviene preguntar cuándo estará el informe y con quién se conversará.",
            "La lista y los rangos pueden cambiar según la orden y el laboratorio. La aplicación no interpreta las cifras ni recomienda modificar medicamentos, suplementos o alimentación. Si un resultado genera dudas, el equipo de salud puede explicar su significado en contexto.",
            listOf("¿Qué sustancias incluye esta química sanguínea?", "¿La orden requiere ayuno y por cuánto tiempo?", "¿Se revisarán también creatinina y electrolitos?", "¿Cómo se comparará el informe con pruebas anteriores?", "¿Cuándo podremos conversar los resultados?")
        ).copy(
            source = metabolicPanel,
            summary = "Un panel metabólico agrupa varias mediciones en una muestra de sangre. Las sustancias incluidas dependen de la orden y del laboratorio.",
            contentVersion = "2.1-panel-metabolico",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("metabolic-includes", "¿Qué puede incluir la química sanguínea?", "method"),
                ProcedureRelatedQuestion("metabolic-panels", "¿Qué diferencia hay entre panel básico y completo?", "comparison"),
                ProcedureRelatedQuestion("metabolic-fasting", "¿Se requiere ayuno?", "preparation"),
            ),
            preparation = "Algunos paneles pueden requerir ayuno. La unidad debe confirmar las instrucciones de esta orden y qué hacer con los medicamentos habituales; no los suspendas ni ajustes por cuenta propia.",
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf(metabolicPanel.reference) },
            extraSections = listOf(
                ProcedureGuideSection("comparison", "Panel básico y completo",
                    "MedlinePlus describe un panel metabólico básico y otro completo. El completo añade mediciones como proteínas, bilirrubina y algunas enzimas hepáticas que no forman parte del básico descrito allí. Pregunta al laboratorio cuáles pruebas aparecen en la orden, en lugar de asumir que todos los paneles incluyen exactamente los mismos análisis.",
                    listOf(metabolicPanel.reference)),
                ProcedureGuideSection("terms", "Sustancias que pueden aparecer",
                    "Glucosa: azúcar que el cuerpo utiliza como energía. Electrolitos: minerales con carga eléctrica que participan en el equilibrio de líquidos y otras funciones. Creatinina y nitrógeno ureico: productos de desecho que los riñones ayudan a eliminar. Enzimas: proteínas que participan en reacciones del cuerpo; algunas se usan como parte de la valoración del hígado.",
                    listOf(metabolicPanel.reference)),
            ),
        ),

        entry("biometria-hematica", "Biometría hemática", ProcedureCategory.LABS,
            completeBloodCount.reference,
            listOf("hemograma", "conteo sanguineo completo", "biometria", "bh"),
            "La biometría hemática, también llamada hemograma o conteo sanguíneo completo, reúne mediciones de las células de la sangre. Describe, entre otros componentes, glóbulos rojos, glóbulos blancos y plaquetas; cada grupo cumple funciones distintas.",
            "Puede formar parte de una revisión general o ayudar a dar seguimiento a condiciones que afectan las células sanguíneas. Por ejemplo, sus distintas mediciones aportan información que el equipo puede considerar al estudiar anemia o una infección. Que se solicite no confirma ninguna de esas condiciones.",
            "Un profesional obtiene una muestra pequeña de sangre de una vena del brazo y la coloca en un tubo. Puede sentirse un pinchazo breve; a veces queda una pequeña molestia o moretón. La muestra se envía al laboratorio para contar y describir sus componentes.",
            "Los glóbulos rojos transportan oxígeno; los blancos participan en la defensa frente a infecciones y las plaquetas ayudan a la coagulación. El informe también puede mostrar hemoglobina, proteína de los glóbulos rojos que transporta oxígeno, y hematocrito, proporción de la sangre formada por esos glóbulos. Una fórmula leucocitaria añade información sobre tipos de glóbulos blancos.",
            "El profesional revisa el informe con los antecedentes, síntomas y otras pruebas. Si alguna medición está fuera del rango, existen distintas explicaciones posibles; el equipo puede aclarar qué significa y si se necesita otra evaluación. Pregunta cuándo estará listo el resultado.",
            "Una biometría no es la prueba de hemoglobina glucosilada: aunque ambas mencionan hemoglobina, responden preguntas distintas. No uses una cifra aislada para diagnosticar o cambiar un medicamento; conversa el informe con el equipo.",
            listOf("¿Qué componentes de la sangre se midieron?", "¿Incluye fórmula leucocitaria?", "¿Qué significa hemoglobina en este informe?", "¿En qué se diferencia de la HbA1c?", "¿Cuándo revisaremos los resultados?")
        ).copy(
            source = completeBloodCount,
            summary = "La biometría describe células de la sangre y otras medidas relacionadas, como hemoglobina y hematocrito.",
            contentVersion = "2.2-biometria",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("blood-components", "¿Qué hacen los glóbulos y las plaquetas?", "blood"),
                ProcedureRelatedQuestion("blood-a1c", "¿Es lo mismo que la hemoglobina glucosilada?", "comparison"),
                ProcedureRelatedQuestion("blood-draw", "¿Cómo se obtiene la muestra?", "during"),
            ),
            additionalSources = listOf(hemoglobinA1c, EducationSource("MedlinePlus · Fórmula leucocitaria", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/formula-leucocitaria/", "22 de noviembre de 2026")),
            preparation = "La biometría por sí sola generalmente no requiere preparación especial. Si se tomarán otras pruebas con la misma muestra, confirma sus indicaciones con el laboratorio.",
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf(completeBloodCount.reference) } +
                mapOf("important" to listOf(completeBloodCount.reference, hemoglobinA1c.reference)),
            extraSections = listOf(
                ProcedureGuideSection("blood", "Qué hace cada componente",
                    "Los glóbulos rojos llevan oxígeno desde los pulmones al resto del cuerpo. Los glóbulos blancos ayudan a defenderlo frente a infecciones; existen varios tipos, que pueden describirse con una fórmula leucocitaria. Las plaquetas ayudan a formar coágulos para detener el sangrado. La biometría informa sobre estos componentes, pero necesita interpretación profesional junto con el contexto.",
                    listOf(completeBloodCount.reference)),
                ProcedureGuideSection("comparison", "Hemoglobina y HbA1c no son lo mismo",
                    "En la biometría se mide la hemoglobina como proteína de los glóbulos rojos que transporta oxígeno. La HbA1c, en cambio, estudia la proporción de hemoglobina con glucosa adherida para aportar información sobre un periodo más largo. Compartir la palabra hemoglobina no convierte una prueba en la otra ni significa que la biometría incluya automáticamente HbA1c.",
                    listOf(completeBloodCount.reference, hemoglobinA1c.reference)),
                ProcedureGuideSection("red-cell-measures", "¿Qué describen la hemoglobina y el hematocrito?",
                    "La hemoglobina es una proteína dentro de los glóbulos rojos que transporta oxígeno. El hematocrito expresa qué proporción de la sangre está formada por esos glóbulos. Otra medida que puede aparecer, el volumen corpuscular medio o VCM, describe su tamaño promedio. No son tres nombres de la misma cifra: ofrecen perspectivas distintas sobre los glóbulos rojos. El equipo las considera juntas y con los demás datos al estudiar, por ejemplo, una posible anemia.",
                    listOf(completeBloodCount.reference)),
                ProcedureGuideSection("white-cell-differential", "¿Qué añade la fórmula leucocitaria?",
                    "El conteo total dice cuántos glóbulos blancos hay; la fórmula leucocitaria distingue sus tipos y puede informar cantidades o porcentajes. Entre ellos están neutrófilos y linfocitos, que participan de maneras diferentes en las defensas, y eosinófilos, que intervienen también en las alergias. Una variación puede tener distintas explicaciones y no identifica por sí sola una enfermedad. Pregunta si la orden incluye esta fórmula y cómo interpreta el equipo el conjunto del informe.",
                    listOf("https://medlineplus.gov/spanish/pruebas-de-laboratorio/formula-leucocitaria/")),
            ),
        ),

        entry("perfil-lipidos", "Perfil de lípidos", ProcedureCategory.LABS,
            lipidPanel.reference,
            listOf("perfil lipidico", "colesterol y trigliceridos", "lipidos"),
            "El perfil de lípidos es un grupo de mediciones realizadas en sangre. Puede incluir colesterol total, colesterol transportado por LDL y HDL y triglicéridos. Los lípidos son sustancias semejantes a grasas; el cuerpo necesita colesterol para funciones como producir algunas hormonas.",
            "El profesional puede usar estas mediciones junto con otros antecedentes para conversar sobre salud del corazón y de los vasos sanguíneos. Cada componente aporta información diferente y la solicitud de la prueba no significa que exista una enfermedad cardiovascular.",
            "Un profesional obtiene una muestra de sangre, normalmente de una vena del brazo, y la envía al laboratorio. Puede sentirse un pinchazo breve y quedar una pequeña molestia o moretón. Si existe inquietud por la extracción, se puede comunicar al personal.",
            "En el informe pueden aparecer el colesterol total, LDL, HDL y triglicéridos. LDL y HDL son partículas que transportan colesterol en la sangre: el HDL ayuda a retirar parte del colesterol, mientras la acumulación de colesterol transportado por LDL puede contribuir a depósitos en arterias. Los triglicéridos son otro tipo de grasa.",
            "El equipo revisa el conjunto y puede compararlo con informes anteriores, tomando en cuenta los antecedentes y las condiciones de la muestra. El profesional explicará qué representa cada medición y cuándo se revisarán de nuevo si corresponde.",
            "Una cifra aislada no permite calcular desde esta guía el riesgo individual ni decidir medicamentos. No cambies tratamientos o alimentación a partir de una interpretación automática; consulta el informe con el equipo de salud.",
            listOf("¿Qué componentes incluye este perfil?", "¿Requiere ayuno la orden y cómo debemos prepararnos?", "¿Qué diferencia hay entre LDL, HDL y triglicéridos?", "¿Cómo se comparará con estudios anteriores?", "¿Cuándo revisaremos el informe?")
        ).copy(
            source = lipidPanel,
            summary = "El perfil de lípidos reúne mediciones de colesterol y triglicéridos que el equipo revisa junto con otros antecedentes.",
            contentVersion = "2.2-lipidos",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("lipid-ldl-hdl", "¿Qué diferencia hay entre LDL y HDL?", "transport"),
                ProcedureRelatedQuestion("lipid-calculated", "¿Qué significa LDL calculado?", "report"),
                ProcedureRelatedQuestion("lipid-fasting", "¿Necesita ayuno el perfil de lípidos?", "before"),
            ),
            preparation = "La preparación varía con la prueba y la orden. Pregunta al laboratorio si se solicita ayuno y qué hacer con los medicamentos habituales; no los suspendas por cuenta propia.",
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf(lipidPanel.reference) },
            extraSections = listOf(
                ProcedureGuideSection("transport", "Qué significan LDL y HDL",
                    "El colesterol circula unido a partículas llamadas lipoproteínas. LDL lleva principalmente colesterol y su exceso puede contribuir a que se acumule material en las arterias. HDL ayuda a retirar colesterol de la circulación. Los triglicéridos son otra clase de grasa; no son lo mismo que LDL o HDL. El profesional explica cómo interpretar el conjunto para una persona concreta.",
                    listOf(lipidPanel.reference)),
                ProcedureGuideSection("report", "¿Qué significa LDL calculado?",
                    "El informe puede indicar que el LDL fue calculado: el laboratorio utiliza otras mediciones del perfil para estimarlo. En otras situaciones el LDL se mide directamente. Esto explica por qué el informe puede incluir varios números aunque provengan de una misma muestra. Los valores se revisan junto con los antecedentes y otros factores; esta ficha no calcula un objetivo personal.",
                    listOf(lipidPanel.reference)),
                ProcedureGuideSection("before", "La toma de muestra y el ayuno",
                    "La muestra suele obtenerse de una vena del brazo y se utiliza para medir varios componentes. Si la persona teme al pinchazo o tiene dificultad para una extracción, puede decirlo antes al personal. Algunas órdenes requieren ayuno y otras no; pregunta a la unidad por la orden concreta, especialmente si se tomarán otros análisis. No suspendas medicamentos por tu cuenta.",
                    listOf(lipidPanel.reference)),
            ),
        ),

        entry("urea-sangre", "Nitrógeno ureico en sangre", ProcedureCategory.LABS,
            bloodUrea.reference,
            listOf("urea en sangre", "nitrogeno ureico", "bun", "nus"),
            "El análisis de nitrógeno ureico en sangre, también llamado NUS o BUN, mide una sustancia de desecho en la sangre. Se forma cuando el cuerpo descompone proteínas; viaja por la sangre y los riñones ayudan a eliminarla mediante la orina.",
            "Puede aportar información al revisar la función de los riñones o el seguimiento de una enfermedad renal. En ocasiones forma parte de un grupo de análisis. La solicitud de esta prueba no significa por sí sola que la persona tenga daño renal.",
            "Un profesional obtiene una pequeña muestra de sangre de una vena del brazo y la coloca en un tubo para enviarla al laboratorio. Se puede sentir un pinchazo breve; algunas personas presentan un pequeño moretón. Si preocupa la extracción o es difícil acceder a una vena, el cuidador puede comunicárselo al personal.",
            "El laboratorio analiza la muestra para medir la cantidad de nitrógeno ureico. Esa medición es distinta de la creatinina y no sustituye por sí sola la estimación de filtración de los riñones. El equipo puede revisar varias pruebas juntas para comprender mejor la situación.",
            "El resultado se entrega o comunica según el procedimiento de la unidad. El profesional puede compararlo con la creatinina, otros análisis y los antecedentes. Pregunta cuándo estará disponible y cómo conversar cualquier resultado anterior.",
            "La cantidad puede variar por factores distintos a la función renal, incluidos cambios en líquidos, alimentación o algunos medicamentos. Una cifra aislada no identifica la causa ni indica que deba cambiarse un tratamiento. Bitavida no interpreta resultados personales.",
            listOf("¿Por qué se midió el nitrógeno ureico?", "¿Cómo se revisará junto con creatinina?", "¿Puede algún medicamento o cambio de líquidos influir?", "¿Cuándo y con quién veremos el resultado?"),
            cross = listOf(ProcedureCategory.KIDNEYS)
        ).copy(
            source = bloodUrea,
            summary = "El NUS mide en sangre un desecho que los riñones ayudan a eliminar. El equipo lo revisa junto con otras pruebas y antecedentes.",
            contentVersion = "2.1-nus",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("nus-path", "¿De dónde viene el nitrógeno ureico?", "route"),
                ProcedureRelatedQuestion("nus-creatinine", "¿En qué se diferencia de la creatinina?", "comparison"),
                ProcedureRelatedQuestion("nus-context", "¿Qué puede influir en el resultado?", "context"),
            ),
            preparation = "El NUS por sí solo generalmente no requiere preparación especial. Si se solicitaron otros análisis, confirma sus instrucciones con el laboratorio. No cambies medicamentos por cuenta propia.",
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf(bloodUrea.reference) },
            extraSections = listOf(
                ProcedureGuideSection("comparison", "Urea y creatinina",
                    "El nitrógeno ureico y la creatinina son desechos que pueden medirse en sangre, pero provienen de procesos diferentes. El NUS se relaciona con la descomposición de proteínas; la creatinina procede del metabolismo muscular. El equipo puede utilizar la creatinina para estimar la filtración de los riñones y revisar el NUS junto con el resto de la información.",
                    listOf(bloodUrea.reference)),
                ProcedureGuideSection("route", "Del cuerpo a la muestra",
                    "Cuando el organismo utiliza proteínas se produce nitrógeno ureico, que viaja por la sangre. Los riñones ayudan a retirar este desecho y a eliminarlo por la orina. La prueba mide cuánto hay en una muestra de sangre en ese momento; no mide directamente la cantidad eliminada por la orina ni permite conocer por sí sola la causa de una variación.",
                    listOf(bloodUrea.reference)),
                ProcedureGuideSection("context", "¿Qué información conviene comunicar?",
                    "El resultado puede verse influido por varios factores, como cambios en los líquidos, la alimentación o algunos medicamentos, además de la función renal. Anota las indicaciones recibidas y pregunta si el equipo necesita conocer cambios recientes. No intentes corregir una cifra modificando líquidos, alimentación o tratamientos por cuenta propia.",
                    listOf(bloodUrea.reference)),
            ),
        ),

        entry("electrolitos", "Electrolitos: sodio y potasio", ProcedureCategory.LABS,
            electrolytes.reference,
            listOf("ionograma", "electrolitos", "sodio y potasio"),
            "Los electrolitos son minerales con carga eléctrica presentes en los líquidos del cuerpo. Un ionograma es un análisis de sangre que puede medir varios de ellos, incluidos sodio y potasio; la lista exacta depende de lo que se haya solicitado.",
            "Estos minerales participan en el equilibrio de agua y en el funcionamiento de nervios y músculos. El potasio también interviene en el funcionamiento del corazón. Las mediciones ayudan al equipo a estudiar el equilibrio de líquidos y otras situaciones; por sí solas no dicen por qué cambió un valor.",
            "Un profesional toma una muestra de sangre, habitualmente de una vena del brazo, y la envía al laboratorio. La persona puede sentir un pinchazo breve y después tener una pequeña molestia o moretón en el sitio de extracción.",
            "El laboratorio mide por separado los electrolitos incluidos en la orden. El sodio contribuye al equilibrio de líquidos y a la función de nervios y músculos; el potasio participa en la función muscular y del corazón. No son medidas de glucosa ni pruebas iguales entre sí.",
            "El profesional revisa el informe junto con las demás mediciones, medicamentos, cambios de líquidos y antecedentes. Puede explicar por qué se solicitó cada parte y si necesita compararla con otra prueba.",
            "Los valores pueden cambiar por distintas causas. No intentes establecer una enfermedad o ajustar medicamentos, suplementos o alimentos a partir de una cifra de la aplicación. El equipo indicará cómo interpretar los resultados de esa persona.",
            listOf("¿Qué electrolitos se midieron en esta orden?", "¿Qué aportan el sodio y el potasio por separado?", "¿Se revisarán junto con la función renal?", "¿Qué información sobre medicamentos debemos comentar?", "¿Cuándo se explicará el informe?")
        ).copy(
            source = electrolytes,
            summary = "Un ionograma mide minerales de la sangre, como sodio y potasio, que ayudan a regular líquidos y a mantener funciones de nervios y músculos.",
            contentVersion = "2.1-electrolitos",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("electrolyte-roles", "¿Para qué sirven el sodio y el potasio?", "different"),
                ProcedureRelatedQuestion("electrolyte-context", "¿Qué puede cambiar estos valores?", "variation"),
                ProcedureRelatedQuestion("electrolyte-together", "¿Por qué se revisan varios electrolitos?", "meaning"),
            ),
            preparation = "El panel de electrolitos por sí solo no suele requerir preparación especial. Confirma con la unidad si se tomarán otras pruebas con la misma muestra y qué instrucciones corresponden.",
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf(electrolytes.reference) },
            extraSections = listOf(
                ProcedureGuideSection("meaning", "Por qué se revisan juntos",
                    "Los electrolitos trabajan en los líquidos del cuerpo y participan en varias funciones. El sodio ayuda a controlar la cantidad de líquido; el potasio ayuda a músculos y corazón. El laboratorio también puede medir otros, como cloruro o bicarbonato. El conjunto solicitado ayuda al profesional a entender la situación, pero cada sustancia tiene una función y un resultado propios.",
                    listOf(electrolytes.reference)),
                ProcedureGuideSection("different", "Sodio, potasio y otras mediciones",
                    "El sodio participa en el equilibrio del agua y en la función de nervios y músculos. El potasio también interviene en el funcionamiento muscular y del corazón. Un informe puede incluir otros componentes, como cloruro o bicarbonato; el contenido depende de la orden. El profesional puede explicar qué se pidió y por qué cada medición se revisa por separado.",
                    listOf(electrolytes.reference)),
                ProcedureGuideSection("variation", "Por qué una cifra necesita contexto",
                    "La cantidad de líquidos del cuerpo, los vómitos o la diarrea y algunos medicamentos pueden influir en los resultados. Estar fuera de un rango no indica automáticamente una causa ni significa que se deba cambiar un tratamiento. Lleva el informe al equipo y pregunta qué otras observaciones necesita para entenderlo.",
                    listOf(electrolytes.reference)),
            ),
        ),

        entry("funcion-hepatica", "Pruebas de función hepática", ProcedureCategory.LABS,
            liverTests.reference,
            listOf("perfil hepatico", "pruebas del higado", "funcion hepatica"),
            "Las pruebas hepáticas son mediciones en una muestra de sangre que ayudan a estudiar el hígado. Algunas revisan sustancias que produce o procesa; otras aportan señales que pueden relacionarse con una lesión. La combinación incluida depende de la orden.",
            "Pueden ayudar al profesional a investigar una alteración del hígado, dar seguimiento a una condición conocida o revisar efectos de determinados medicamentos. El equipo puede pedirlas junto con otras pruebas; que estén solicitadas no confirma una enfermedad.",
            "Un profesional suele obtener una muestra pequeña de una vena del brazo y la envía al laboratorio. Puede sentirse un pinchazo al entrar o salir la aguja y, en algunas personas, quedar una molestia o moretón transitorio.",
            "El informe puede incluir enzimas como ALT, AST o fosfatasa alcalina, proteínas como albúmina y sustancias como bilirrubina. Las enzimas son proteínas que participan en reacciones del cuerpo; la bilirrubina es un desecho relacionado con la descomposición de glóbulos rojos que el hígado ayuda a procesar. No todos los paneles incluyen las mismas mediciones.",
            "El profesional revisa las mediciones en conjunto, con antecedentes, medicamentos y otros estudios. Si alguna cambia, puede explicar si se necesita repetirla o realizar otras pruebas para entender la causa. Pregunta cuándo recibirás el informe.",
            "Una alteración aislada no identifica por sí sola una enfermedad concreta. No se deben suspender medicamentos o iniciar remedios a partir del resultado sin conversar con el equipo. La aplicación no interpreta el panel de una persona.",
            listOf("¿Qué incluye este panel?", "¿Alguno de los medicamentos debe comentarse antes de la muestra?", "¿Qué información aporta cada resultado?", "¿Se revisarán estudios anteriores?", "¿Cuándo nos explicarán el informe?")
        ).copy(
            source = liverTests,
            summary = "Un panel hepático reúne mediciones de sangre que aportan información sobre distintas funciones o posibles cambios del hígado.",
            contentVersion = "2.2-hepatico",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("liver-components", "¿Qué sustancias mide el perfil hepático?", "components"),
                ProcedureRelatedQuestion("liver-pattern", "¿Por qué no basta una cifra aislada?", "patterns"),
                ProcedureRelatedQuestion("liver-sample", "¿Cómo se toma la muestra y necesita ayuno?", "sample"),
            ),
            preparation = "Confirma con la unidad si esta orden requiere ayuno u otra preparación. Informa los medicamentos y suplementos usados, sin suspenderlos ni modificarlos por cuenta propia.",
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf(liverTests.reference) },
            extraSections = listOf(
                ProcedureGuideSection("components", "Por qué el informe tiene varias mediciones",
                    "No todas las pruebas describen la misma función. La albúmina es una proteína producida principalmente por el hígado; la bilirrubina es un desecho que el hígado procesa. Algunas enzimas pueden ayudar a estudiar una posible lesión, mientras otras mediciones aportan información sobre funciones diferentes. El profesional las interpreta en conjunto y puede necesitar pruebas adicionales para conocer una causa.",
                    listOf(liverTests.reference)),
                ProcedureGuideSection("patterns", "¿Por qué no basta una cifra?",
                    "El informe reúne sustancias distintas: unas ayudan a observar la producción o el procesamiento del hígado y otras pueden aumentar cuando hay una lesión. Algunas también se relacionan con otros tejidos. Por ejemplo, una fosfatasa alcalina alterada no señala por sí sola dónde está el problema. El equipo busca patrones entre los resultados y los relaciona con antecedentes, medicamentos y síntomas.",
                    listOf(liverTests.reference)),
                ProcedureGuideSection("sample", "Antes y después de la extracción",
                    "La unidad explica si hay que acudir en ayunas y qué información necesita sobre medicamentos o suplementos, pues algunas sustancias pueden influir en el resultado. La toma consiste habitualmente en una pequeña muestra de sangre del brazo y puede dejar una molestia breve o un moretón. Después, pregunta cuándo estará disponible el panel y quién lo revisará; no cambies por cuenta propia ningún tratamiento.",
                    listOf(liverTests.reference)),
            ),
        ),

        entry("vitamina-b12", "Medición de vitamina B12", ProcedureCategory.LABS,
            vitaminB12.reference,
            listOf("vitamina b12", "nivel b12", "analisis de vitamina b12"),
            "La vitamina B12 es un nutriente que ayuda al funcionamiento de los nervios y a la formación de glóbulos rojos. Una prueba de sangre puede medir su cantidad para aportar información al equipo de salud.",
            "El profesional puede solicitarla cuando investiga ciertas formas de anemia o cambios como hormigueo o adormecimiento. También puede considerar situaciones que dificulten la absorción de vitaminas. La orden no confirma que exista una deficiencia.",
            "El personal extrae una pequeña muestra de una vena del brazo. La persona puede sentir un pinchazo cuando entra o sale la aguja; después podría quedar una molestia leve o un pequeño moretón. Si hubo dificultades o mareos en extracciones anteriores, conviene comentarlo.",
            "El laboratorio mide la vitamina en la sangre. La cantidad informada se revisa junto con otros datos: los intervalos de referencia varían y ciertos medicamentos u otros factores pueden influir. En ocasiones el equipo necesita pruebas adicionales para investigar la causa.",
            "Pregunta cómo se entregará el resultado y quién lo explicará. Puede ayudar llevar una lista de alimentos habituales, medicamentos y suplementos para conversar sobre factores que afectan el aporte o la absorción, sin concluir por cuenta propia cuál es la causa.",
            "No se debe suspender metformina ni ningún otro tratamiento por iniciativa propia. Los suplementos también pueden influir en medicamentos y resultados: consulta al equipo antes de comenzar o cambiar alguno. La aplicación no interpreta cifras individuales.",
            listOf("¿Qué buscan aclarar con la prueba de B12?", "¿Hay preparación específica para esta orden?", "¿Qué medicamentos o suplementos debemos comunicar?", "¿Cómo se relaciona el resultado con otros análisis?", "¿Cuándo revisaremos el informe?")
        ).copy(
            source = vitaminB12,
            summary = "La prueba mide B12 en sangre y aporta información sobre un nutriente importante para la sangre y los nervios.",
            contentVersion = "2.3-vitamina-b12",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("b12-function", "¿Para qué sirve la vitamina B12?", "what"),
                ProcedureRelatedQuestion("b12-absorption", "¿Qué significa absorber una vitamina?", "absorption"),
                ProcedureRelatedQuestion("b12-fasting", "¿La prueba necesita ayuno?", "preparation"),
            ),
            preparation = "Algunas pruebas de vitaminas B en sangre pueden requerir ayuno. Confirma con la unidad las instrucciones de esta orden y comunica los medicamentos y suplementos utilizados.",
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf(vitaminB12.reference) },
            extraSections = listOf(
                ProcedureGuideSection("absorption", "Qué significa absorber una vitamina",
                    "Absorber significa que el organismo incorpora nutrientes de los alimentos. Algunas condiciones del aparato digestivo, ciertas cirugías o una alimentación con poco aporte pueden relacionarse con cantidades bajas de vitaminas B. Hay distintas posibilidades: el profesional puede necesitar más información para comprender la situación de cada persona.",
                    listOf(vitaminB12.reference)),
            ),
        ),

        entry("examen-orina", "Examen general de orina", ProcedureCategory.LABS,
            urineExamination.reference,
            listOf("examen general de orina", "analisis de orina", "ego"),
            "El examen general reúne observaciones sobre una muestra de orina. El informe puede describir su aspecto y buscar sustancias, células u otros componentes; no todas las órdenes contienen exactamente las mismas pruebas.",
            "Puede ayudar al equipo a investigar o dar seguimiento a distintas situaciones, incluidas alteraciones urinarias o renales. Por ejemplo, el laboratorio puede revisar si hay sangre, proteínas o glucosa. Cada dato responde a una pregunta y una orden no confirma una enfermedad.",
            "La unidad proporciona un recipiente y explica qué muestra requiere y cómo entregarla. Si la persona tiene dificultades para obtenerla o necesita ayuda, puede pedir instrucciones accesibles al personal antes de recogerla.",
            "Se puede revisar el color y el aspecto, emplear una tira reactiva para buscar sustancias y observar componentes de la muestra. El informe puede mencionar células u otros elementos. Las pruebas solicitadas y sus resultados deben revisarse con el profesional.",
            "El equipo toma en cuenta los síntomas, antecedentes y otras pruebas al explicar el informe. La unidad puede indicar cuándo estará disponible y con quién comentarlo, especialmente si hay información previa para comparar.",
            "Una observación aislada no identifica por sí sola la causa de un cambio. No ajustes medicamentos por el aspecto de la orina ni por un valor del informe; consulta al equipo qué significa en el contexto de la persona.",
            listOf("¿Qué tipo de muestra pide la orden?", "¿Cuáles sustancias o células se revisarán?", "¿Se solicitó también urocultivo?", "¿La relación albúmina/creatinina está incluida o se pidió aparte?", "¿Quién explicará los resultados?")
        ).copy(
            source = urineExamination,
            summary = "El examen general observa varias características de una muestra de orina; el equipo interpreta cada dato junto con otros antecedentes.",
            contentVersion = "2.3-orina",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("urinalysis-components", "¿Qué se observa en el examen de orina?", "method"),
                ProcedureRelatedQuestion("urinalysis-culture", "¿En qué se diferencia de un urocultivo?", "comparison"),
                ProcedureRelatedQuestion("urinalysis-sample", "¿Cómo se entrega la muestra?", "during"),
            ),
            preparation = "Confirma con la unidad qué recipiente y muestra necesita la orden y cómo entregarla. Si se toman otros análisis, pregunta si tienen indicaciones distintas.",
            additionalSources = listOf(urineBacteria),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf(urineExamination.reference) },
            extraSections = listOf(
                ProcedureGuideSection("comparison", "Examen general y urocultivo",
                    "El examen general describe varias características de la orina, mientras un urocultivo busca e identifica determinadas bacterias de la muestra. Son análisis distintos, aunque el equipo pueda pedir ambos. Entregar una muestra no significa automáticamente que se haya solicitado un cultivo.",
                    listOf(urineExamination.reference, urineBacteria.reference)),
            ),
        ),

        entry("cetonas-sangre", "Cetonas en sangre", ProcedureCategory.LABS,
            bloodKetones.reference,
            listOf("cetonas sangre", "cetonas sanguineas"),
            "Las cetonas son sustancias que produce el hígado cuando el cuerpo descompone grasa para obtener energía. Un análisis de sangre permite conocer su concentración en la muestra. Pueden acumularse cuando falta insulina y el organismo no puede aprovechar la glucosa de forma adecuada.",
            "El estudio ayuda a investigar una acumulación de cetonas, entre otras situaciones. En la diabetes, una acumulación elevada puede acompañarse de cetoacidosis diabética, un problema grave que necesita atención inmediata. Tener diabetes o realizar la prueba no significa que la persona tenga esa complicación.",
            "En el laboratorio, un profesional puede obtener sangre de una vena del brazo con una aguja pequeña; se puede sentir un pinchazo breve. Algunos equipos también miden cetonas con una gota obtenida del dedo. La unidad explicará cuál método se utilizará y cómo se comunicará el resultado.",
            "Es un análisis de sangre: mide cetonas presentes en la muestra, no mide directamente la glucosa. La prueba puede solicitarse junto con otras evaluaciones. El equipo interpreta el resultado con los síntomas, antecedentes y demás pruebas, en lugar de sacar conclusiones a partir de una cifra aislada.",
            "El laboratorio entregará o comunicará el resultado según su procedimiento. Pregunta quién lo revisará y en qué plazo. En una situación urgente, la valoración y el seguimiento dependen del estado de la persona; no es necesario esperar a completar una conversación con la aplicación para buscar ayuda.",
            "Confusión, dificultad para respirar, vómitos o dolor abdominal acompañados de malestar importante pueden ser señales de una emergencia y requieren atención médica inmediata. Estos síntomas no confirman por sí solos la causa. Esta guía no interpreta cifras ni indica cambios de medicamentos.",
            listOf("¿Por qué se solicitó la prueba de cetonas en sangre?", "¿La muestra será venosa o de una gota del dedo?", "¿Cómo se relaciona con otras pruebas de esta visita?", "¿Quién nos explicará el resultado?", "¿Qué señales requieren atención inmediata?"))
            .copy(
                source = bloodKetones,
                summary = "La prueba mide cetonas en sangre: sustancias que se producen al utilizar grasa como fuente de energía y que pueden acumularse en ciertas situaciones.",
                contentVersion = "2.2-cetonas-sangre",
                reviewedOn = "22 de noviembre de 2026",
                relatedQuestions = listOf(
                    ProcedureRelatedQuestion("blood-ketones-what", "¿Qué son las cetonas?", "what"),
                    ProcedureRelatedQuestion("blood-ketones-limits", "¿Un resultado normal siempre descarta una emergencia?", "meaning"),
                    ProcedureRelatedQuestion("blood-ketones-home", "¿Qué conviene preguntar sobre la prueba en casa?", "care"),
                ),
                preparation = "La prueba de cetonas en sangre normalmente no necesita preparación especial. Confirma con la unidad si otras pruebas de la misma visita requieren alguna indicación.",
                additionalSources = listOf(urineKetones, EducationSource("CDC · Cetoacidosis diabética", "https://www.cdc.gov/diabetes/about/diabetic-ketoacidosis.html", "22 de noviembre de 2026")),
                sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf(bloodKetones.reference) } +
                    mapOf("important" to listOf(bloodKetones.reference, "https://www.cdc.gov/diabetes/about/diabetic-ketoacidosis.html")),
                extraSections = listOf(
                    ProcedureGuideSection("comparison", "Sangre y orina: ¿cuál es la diferencia?",
                        "La prueba en sangre mide cetonas presentes en una muestra sanguínea; la de orina busca cetonas eliminadas en la orina. Las muestras y la forma de expresarlas pueden ser diferentes, por lo que sus resultados no deben tratarse como si fueran idénticos. El equipo explica por qué eligió cada prueba.",
                        listOf(bloodKetones.reference, urineKetones.reference)),
                    ProcedureGuideSection("meaning", "¿Qué puede y qué no puede decir la prueba?",
                        "La muestra indica la cantidad de cetonas que detecta el método utilizado en ese momento. Existen diferentes cetonas y los métodos no siempre miden las mismas. Un resultado aislado no explica por qué aumentaron y un resultado normal no siempre descarta una emergencia en etapas iniciales. Por eso, ante síntomas graves se necesita atención inmediata, sin esperar a que la aplicación interprete una cifra.",
                        listOf(bloodKetones.reference)),
                    ProcedureGuideSection("care", "Preguntas si se indicó una medición en casa",
                        "Algunos medidores pueden medir cetonas con una tira específica y una gota de sangre; no basta asumir que cualquier tira de glucosa sirve para esa función. Si el equipo tratante indicó una medición en casa, pregunta qué dispositivo y tira usar, cuándo comunicarse y cómo registrar el resultado. El plan debe ser individual y no se modifica desde esta guía.",
                        listOf(bloodKetones.reference, "https://www.cdc.gov/diabetes/about/diabetic-ketoacidosis.html")),
                ),
            ),

        entry("cetonas-orina", "Cetonas en orina", ProcedureCategory.LABS,
            urineKetones.reference,
            listOf("cetonas orina", "cetonas urinarias"),
            "Esta prueba busca cetonas en una muestra de orina. Las cetonas son sustancias que el cuerpo produce al usar grasa como fuente de energía. Una parte puede eliminarse por la orina, donde es posible detectarla con una prueba.",
            "Ayuda al equipo a investigar si se están eliminando cetonas. En personas con diabetes, la acumulación elevada de cetonas puede relacionarse con cetoacidosis diabética, una complicación grave. La presencia de cetonas o la solicitud del examen, por sí solas, no permiten concluir que exista esa complicación.",
            "La persona entrega una muestra de orina en el recipiente que indique la unidad. El personal explica cómo obtenerla y entregarla; si necesita ayuda para recolectarla, puede pedir orientación antes de comenzar. También existen pruebas con tiras que se usan fuera del laboratorio, de acuerdo con las indicaciones recibidas.",
            "La tira de prueba tiene una zona que cambia de color al entrar en contacto con la muestra. El laboratorio o el equipo de salud explica cómo se obtiene el resultado. El examen de orina y el análisis de sangre utilizan muestras distintas; no se debe suponer que sus cifras son intercambiables.",
            "El resultado se comunica según el procedimiento del laboratorio o las indicaciones del equipo. Pregunta quién lo revisará y cómo se pondrán en contacto. La interpretación considera el motivo de la prueba y la situación de la persona, no únicamente el color de una tira.",
            "Confusión, dificultad para respirar, vómitos o dolor abdominal con malestar importante pueden ser señales de una emergencia y requieren atención médica inmediata. No esperes una respuesta de la aplicación para buscar ayuda. La guía no diagnostica ni sustituye una valoración.",
            listOf("¿Por qué se solicitó esta muestra de orina?", "¿Cómo debemos recogerla y entregarla?", "¿Qué diferencia hay con una prueba de cetonas en sangre?", "¿Quién revisará el resultado?", "¿Qué señales requieren atención inmediata?"))
            .copy(
                source = urineKetones,
                summary = "La prueba busca en la orina cetonas que el cuerpo produce cuando usa grasa como energía; el equipo explica su significado según la situación.",
                contentVersion = "2.2-cetonas-orina",
                reviewedOn = "22 de noviembre de 2026",
                relatedQuestions = listOf(
                    ProcedureRelatedQuestion("urine-ketones-collection", "¿Cómo se recoge la muestra?", "collection"),
                    ProcedureRelatedQuestion("urine-ketones-blood", "¿En qué se diferencia de la prueba de sangre?", "comparison"),
                    ProcedureRelatedQuestion("urine-ketones-emergency", "¿Qué señales requieren atención urgente?", "limits"),
                ),
                preparation = "Confirma con la unidad cómo obtener y entregar la muestra y si esta orden u otros estudios de la visita requieren alguna preparación. No establezcas ayuno ni cambies medicamentos por cuenta propia.",
                additionalSources = listOf(bloodKetones, EducationSource("CDC · Cetoacidosis diabética", "https://www.cdc.gov/diabetes/about/diabetic-ketoacidosis.html", "22 de noviembre de 2026")),
                sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf(urineKetones.reference) } +
                    mapOf("important" to listOf(urineKetones.reference, "https://www.cdc.gov/diabetes/about/diabetic-ketoacidosis.html")),
                extraSections = listOf(
                    ProcedureGuideSection("comparison", "Diferencia con cetonas en sangre",
                        "El análisis de orina detecta cetonas eliminadas en la orina; la prueba de sangre mide las presentes en una muestra sanguínea. Aunque ambas aportan información sobre cetonas, utilizan muestras diferentes y el equipo decide cómo integrar cada resultado con las demás observaciones.",
                        listOf(urineKetones.reference, bloodKetones.reference)),
                    ProcedureGuideSection("collection", "¿Qué hay que saber sobre la muestra?",
                        "En el laboratorio se entrega la orina en un recipiente y el personal explica cómo recogerla de manera que no se contamine. Si la persona necesita ayuda para sostener el recipiente o desplazarse al baño, coméntalo antes de la toma. Algunas unidades pueden entregar instrucciones diferentes para esta orden; pide que te las expliquen y pregunta dónde entregar la muestra.",
                        listOf(urineKetones.reference)),
                    ProcedureGuideSection("limits", "Cómo se comunica un resultado",
                        "El resultado puede expresarse con un número o con palabras que describen la cantidad detectada. La alimentación, la actividad y otras circunstancias influyen en su significado; por ello la persona debe preguntar al profesional cómo interpretar su informe. Si hay vómitos y no puede retener líquidos, dificultad para respirar o confusión, busca atención urgente sin esperar una lectura de la aplicación.",
                        listOf(urineKetones.reference, "https://www.cdc.gov/diabetes/about/diabetic-ketoacidosis.html")),
                ),
            ),

        entry("cultivo-herida", "Cultivo de una herida", ProcedureCategory.LABS,
            "https://medlineplus.gov/spanish/pruebas-de-laboratorio/prueba-de-cultivo-de-bacterias/",
            listOf("cultivo de herida", "cultivo de lesion", "muestra de herida"),
            "Un cultivo de herida estudia una muestra tomada de una lesión para buscar microorganismos, como bacterias. El laboratorio intenta que los microorganismos de la muestra crezcan en condiciones controladas para poder identificarlos.",
            "Puede ayudar al equipo a investigar una posible infección y conocer qué microorganismos se encuentran. Según el caso, se pueden hacer pruebas de sensibilidad para estudiar qué medicamentos actúan sobre las bacterias identificadas. La muestra es solo una parte de la valoración de la herida.",
            "El personal sanitario decide cómo obtener la muestra según la herida; puede usar un hisopo especial sobre la zona o emplear otro método indicado. La persona puede preguntar qué sentirá y avisar si tiene dolor. No se necesita que el cuidador realice una extracción invasiva.",
            "El laboratorio coloca la muestra en un medio que favorece el crecimiento de microorganismos y después la examina. Este proceso necesita tiempo; algunos resultados pueden demorar varios días. Una prueba de sensibilidad, si se solicita, examina la respuesta de los microorganismos a determinados medicamentos.",
            "El equipo combina el resultado con la apariencia de la herida y la situación de la persona, y explica qué significa. Pregunta cuándo esperan el informe y a quién comunicar los cambios observados mientras tanto. No inicies ni cambies antibióticos a partir de la lectura del cultivo.",
            "Encontrar microorganismos no demuestra por sí solo la gravedad ni permite elegir un tratamiento desde esta ficha. Una herida profunda, que empeora o no sana necesita valoración profesional; consulta al equipo qué cambios requieren atención pronta.",
            listOf("¿Cómo obtendrán la muestra de esta herida?", "¿Qué microorganismos esperan identificar?", "¿También se solicitará sensibilidad a antibióticos?", "¿Cuándo estará el informe?", "¿Qué cambios deben comunicarse mientras esperamos?"),
            cross = listOf(ProcedureCategory.FEET)
        ).copy(
            source = EducationSource("MedlinePlus · Cultivo de bacterias", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/prueba-de-cultivo-de-bacterias/", "22 de noviembre de 2026"),
            summary = "El laboratorio estudia una muestra de una lesión para identificar microorganismos cuando el equipo investiga una posible infección.",
            contentVersion = "2.2-cultivo-herida",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("wound-culture-time", "¿Por qué tarda el cultivo de la herida?", "culture"),
                ProcedureRelatedQuestion("wound-culture-bacteria", "¿Qué significa encontrar bacterias?", "meaning"),
                ProcedureRelatedQuestion("wound-culture-collection", "¿Cómo se toma la muestra?", "during"),
            ),
            preparation = "Pregunta a la unidad si hay indicaciones para esta toma y comunica los medicamentos que ya utiliza la persona. No cambies tratamientos ni manipules la herida por cuenta propia para obtener la muestra.",
            additionalSources = listOf(EducationSource("MedlinePlus · Sensibilidad a antibióticos", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/prueba-de-sensibilidad-a-los-antibioticos/", "22 de noviembre de 2026"), EducationSource("MedlinePlus · Heridas y lesiones", "https://medlineplus.gov/spanish/woundsandinjuries.html", "22 de noviembre de 2026")),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf("https://medlineplus.gov/spanish/pruebas-de-laboratorio/prueba-de-cultivo-de-bacterias/", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/prueba-de-sensibilidad-a-los-antibioticos/", "https://medlineplus.gov/spanish/woundsandinjuries.html") },
            extraSections = listOf(
                ProcedureGuideSection("culture", "Por qué el cultivo tarda",
                    "En el laboratorio se coloca la muestra en condiciones que permiten que algunas bacterias se multipliquen. Se necesita una cantidad suficiente para reconocerlas; algunas crecen más lentamente que otras, por eso no todos los resultados llegan al mismo tiempo. El informe puede contener información adicional si se realiza una prueba de sensibilidad. La unidad puede explicar si habrá un resultado preliminar y cuándo se espera el definitivo.",
                    listOf("https://medlineplus.gov/spanish/pruebas-de-laboratorio/prueba-de-cultivo-de-bacterias/")),
                ProcedureGuideSection("meaning", "Qué significa encontrar bacterias",
                    "Las bacterias pueden vivir en la piel sin causar daño, mientras otras pueden producir infección. Por eso el equipo no valora una herida únicamente leyendo el nombre de un microorganismo: también considera la exploración y el contexto. Una prueba de sensibilidad examina la respuesta del germen a medicamentos en el laboratorio; la elección de tratamiento requiere valoración profesional y no debe decidirse desde la aplicación.",
                    listOf("https://medlineplus.gov/spanish/pruebas-de-laboratorio/prueba-de-cultivo-de-bacterias/", "https://medlineplus.gov/spanish/pruebas-de-laboratorio/prueba-de-sensibilidad-a-los-antibioticos/")),
            ),
        ),

        entry("retinografia", "Retinografía", ProcedureCategory.EYES,
            retinalPhotos.reference,
            listOf("foto del fondo del ojo", "fotografia de retina", "foto de retina", "retinografia"),
            "La retinografía es una fotografía de la parte posterior del ojo. La retina es la capa que recibe luz y envía señales al cerebro; la mácula es su zona central, importante para distinguir detalles. Según la imagen y el equipo, se pueden registrar esas estructuras y vasos de la retina para que un profesional las revise.",
            "La fotografía permite conservar una imagen y compararla con otras tomadas en diferentes fechas. En personas con diabetes puede ayudar a buscar cambios en los vasos de la retina, aunque que se solicite no significa que exista una lesión ni confirma un diagnóstico. El especialista decide cómo usarla junto con la exploración y otras pruebas.",
            "El personal explica la posición y pide mirar hacia un punto ante una cámara especial. Se obtienen fotografías de uno o ambos ojos; el equipo puede necesitar varias imágenes para registrar distintas zonas. La luz o el destello pueden deslumbrar brevemente. La cámara no necesita entrar en el ojo y, aunque el personal puede ayudar a mantener abierto un párpado, la fotografía suele ser indolora.",
            "La cámara toma una vista del fondo del ojo, de manera semejante a una fotografía, con un equipo adaptado para mostrar estructuras internas. No hay una incisión ni se trata la retina con láser. La imagen registra el aspecto de la zona fotografiada en ese momento.",
            "Las imágenes quedan disponibles para que el equipo las valore y, si existen estudios previos, pueda compararlas. El resultado puede explicarse durante la visita o después, según la organización de la unidad. Si se usaron gotas para dilatar las pupilas, la visión puede estar borrosa y la luz resultar molesta durante algunas horas; consulta cómo regresar de manera segura.",
            "La imagen es una parte de la valoración. Una fotografía no describe necesariamente todas las capas ni todas las zonas de la retina y no reemplaza automáticamente otras pruebas. Bitavida no interpreta los hallazgos ni determina el seguimiento individual.",
            listOf("¿Necesitarán dilatar las pupilas para estas fotografías?", "¿Qué partes de la retina se ven en las imágenes?", "¿Se pueden comparar con fotografías anteriores?", "¿Cuándo nos explicarán el resultado?", "¿Por qué se solicita además otra exploración?")
        ).copy(
            source = retinalPhotos,
            summary = "Una cámara fotografía el fondo del ojo para que el equipo pueda revisar y comparar la retina y sus vasos. La fotografía se toma desde fuera del ojo.",
            contentVersion = "2.1-retinografia",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("retino-photo", "¿Qué sucede durante las fotografías?", "during"),
                ProcedureRelatedQuestion("retino-dilation", "¿Siempre se necesitan gotas?", "preparation"),
                ProcedureRelatedQuestion("retino-oct", "¿En qué se diferencia de una OCT?", "comparison"),
            ),
            preparation = "Algunas fotografías se realizan con gotas que dilatan la pupila y otras pueden obtenerse sin ellas. Confirma con la unidad cómo será la visita. Si se usan gotas, pregunta por la visión al salir y cómo regresar acompañado si es necesario.",
            additionalSources = listOf(retinaDiabetes, retinalDilation, retinalPhotosNoDrops),
            sectionSourceReferences = mapOf(
                "what" to listOf(retinalPhotos.reference, retinaDiabetes.reference),
                "purpose" to listOf(retinalPhotos.reference, retinaDiabetes.reference),
                "during" to listOf(retinalPhotos.reference, retinalPhotosNoDrops.reference),
                "method" to listOf(retinalPhotos.reference),
                "preparation" to listOf(retinalDilation.reference, retinalPhotosNoDrops.reference),
                "after" to listOf(retinalPhotos.reference, retinalDilation.reference),
                "important" to listOf(retinalPhotos.reference),
                "questions" to listOf(retinalPhotos.reference, retinalDilation.reference),
            ),
            extraSections = listOf(
                ProcedureGuideSection("comparison", "Fotografía, fondo de ojo y OCT",
                    "La retinografía guarda fotografías del fondo del ojo. En una exploración de fondo de ojo, el especialista observa esa zona con luz y lentes; según el caso, puede realizarse con dilatación. La OCT usa luz para crear vistas de las capas de la retina y medir su grosor. Cada una aporta información distinta: realizar una no significa que las demás sean siempre necesarias ni que puedan sustituirse sin valoración profesional.",
                    listOf(retinalPhotos.reference, retinalDilation.reference)),
                ProcedureGuideSection("terms", "Palabras que puedes escuchar",
                    "Retina: capa sensible a la luz en la parte posterior del ojo. Mácula: parte central de la retina que ayuda a ver detalles. Fondo de ojo: zona posterior interna que puede incluir la retina, sus vasos y el nervio óptico. Dilatar la pupila: ensanchar temporalmente la abertura por la que entra la luz, generalmente mediante gotas.",
                    listOf(retinalPhotos.reference, retinaDiabetes.reference, retinalDilation.reference)),
            ),
        ),

        entry("angiografia-retina", "Angiografía de retina con fluoresceína", ProcedureCategory.EYES,
            "https://www.cuh.nhs.uk/patient-information/fluorescein-angiography/",
            listOf("angiografia de retina", "fluoresceina en el ojo", "angiograma de retina"),
            "La retina es la capa del fondo del ojo que capta la luz y envía información para formar imágenes. La angiografía con fluoresceína permite observar sus vasos sanguíneos mediante fotografías tomadas después de inyectar un colorante en una vena del brazo o la mano. El colorante viaja por la sangre hasta los vasos del ojo; no se inyecta dentro del ojo.",
            "La serie de fotografías ayuda al especialista a observar cómo circula el colorante y si existen cambios en los vasos o filtraciones. Puede aportar información para estudiar determinadas enfermedades de la retina y seguir su evolución. Que se solicite esta prueba no confirma una enfermedad ni significa que se necesitará cirugía.",
            "Habitualmente se aplican gotas para ampliar la pupila y permitir una mejor vista del fondo del ojo. La persona se sienta frente a una cámara y apoya la barbilla y la frente. El equipo inyecta fluoresceína en una vena y toma una serie de fotografías mientras el colorante circula; se ven destellos luminosos y se pide mantener la mirada y la cabeza en la posición indicada.",
            "La cámara registra el paso del colorante por los vasos de la retina. Puede sentirse el pinchazo de la inyección y experimentar deslumbramiento temporal por los destellos; el aparato no toca el ojo. Algunas personas sienten náusea o calor al administrar el colorante. Comunica cualquier molestia o dificultad para permanecer en posición.",
            "Después de la prueba la piel y la orina pueden verse amarillentas temporalmente por el colorante. Las gotas de dilatación pueden dejar visión borrosa y sensibilidad a la luz durante varias horas: consulta las indicaciones de la unidad y organiza acompañamiento. El especialista revisará las fotografías y explicará cuándo se comentará el resultado.",
            "Informa antes de la prueba sobre alergias, enfermedades y reacciones previas a inyecciones. La fluoresceína puede causar náusea o una reacción alérgica; en ocasiones muy raras la reacción puede ser grave y necesitar atención inmediata. Avisa al personal si te sientes mal durante o después del estudio. No interpretes las fotografías ni cambies tratamientos por cuenta propia.",
            listOf("¿Qué vasos o cambios de la retina quieren estudiar?", "¿Qué puede sentir durante la inyección y los destellos?", "¿Cuánto durarán los efectos de las gotas y quién debe acompañarlo a casa?", "¿Qué antecedentes o reacciones debemos comunicar antes?", "¿Cuándo podremos revisar las fotografías con el especialista?"),
            context = "Atención especializada"
        ).copy(
            source = EducationSource("Cambridge University Hospitals · Angiografía con fluoresceína", "https://www.cuh.nhs.uk/patient-information/fluorescein-angiography/", "22 de noviembre de 2026"),
            summary = "La angiografía fotografía los vasos de la retina mientras un colorante viaja por la sangre.",
            contentVersion = "2.1-angiografia-retina",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("retina-angio-dye", "¿Dónde se inyecta la fluoresceína?", "what"),
                ProcedureRelatedQuestion("retina-angio-camera", "¿Qué sucede frente a la cámara?", "during"),
                ProcedureRelatedQuestion("retina-angio-comparison", "¿En qué se diferencia de una OCT?", "comparison"),
            ),
            preparation = "Antes de la visita comunica alergias, enfermedades y reacciones previas; confirma las indicaciones específicas de la unidad. Pregunta por el acompañamiento, pues las gotas pueden dejar la visión borrosa durante varias horas.",
            additionalSources = listOf(
                EducationSource("Frimley Health NHS · Angiografía con fluoresceína", "https://www.fhft.nhs.uk/patients-and-visitors/patient-information-library/fluorescein-angiography", "22 de noviembre de 2026"),
                retinalPhotos
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.cuh.nhs.uk/patient-information/fluorescein-angiography/", "https://www.fhft.nhs.uk/patients-and-visitors/patient-information-library/fluorescein-angiography")
            },
            extraSections = listOf(
                ProcedureGuideSection("comparison", "¿Es lo mismo que una OCT o una retinografía?",
                    "La retinografía toma fotografías del fondo del ojo. La OCT utiliza luz para mostrar cortes de las capas de la retina. La angiografía con fluoresceína toma una serie de fotografías mientras el colorante circula por los vasos, para aportar información sobre su circulación. Cada estudio responde preguntas distintas y el especialista explica cuál es pertinente.",
                    listOf("https://www.asrs.org/content/documents/retinal-imaging-guide_spanish-final.pdf"))
            ),
        ),

        entry("holter", "Monitoreo Holter", ProcedureCategory.HEART,
            "https://www.gatesheadhealth.nhs.uk/resources/ambulatory-ecg-monitor/",
            listOf("holter", "monitor del ritmo cardiaco", "electrocardiograma continuo"),
            "Un Holter es un pequeño aparato portátil que registra durante un periodo prolongado las señales eléctricas que coordinan los latidos del corazón. Los electrodos son pequeñas piezas adhesivas colocadas sobre el pecho; según el equipo, se conectan al registrador con cables o forman parte de un parche.",
            "El registro ayuda al profesional a observar la frecuencia y el ritmo cardíacos durante las actividades cotidianas. Puede solicitarse, por ejemplo, cuando hay palpitaciones, mareos o desmayos que no aparecen durante una prueba breve. Llevarlo no significa que ya se conozca la causa de los síntomas.",
            "En la cita el personal prepara la piel del pecho, coloca los electrodos y comprueba que el equipo registre. El dispositivo se lleva cerca del cuerpo, por ejemplo en un cinturón o bolsillo. El registro no introduce agujas ni envía descargas; al retirar los adhesivos algunas personas pueden notar molestias en la piel.",
            "El monitor continúa guardando señales mientras la persona realiza su rutina, durante el tiempo indicado por la unidad. Pueden entregar un diario para anotar síntomas, hora y actividad, de modo que el profesional compare esos momentos con el registro. Algunos aparatos tienen un botón para marcar síntomas: pregunta si el equipo recibido lo utiliza.",
            "Al finalizar se devuelve el dispositivo según las instrucciones, junto con el diario si lo proporcionaron. El personal analiza la grabación y envía un informe al profesional que pidió la prueba; normalmente los resultados no están listos al retirar el aparato. Conviene preguntar cuándo y con quién comentarlos.",
            "El equipo no es una alarma de emergencias ni reemplaza buscar atención urgente ante síntomas intensos o repentinos. Las instrucciones para el baño y para electrodos despegados dependen del modelo; confirma cómo evitar mojarlo y a quién llamar si falla. La aplicación no interpreta por su cuenta el registro.",
            listOf("¿Cuánto tiempo debe llevarse este monitor?", "¿Debemos anotar síntomas, actividad y hora o utilizar un botón?", "¿Qué indicaciones de baño corresponden a este aparato?", "¿Qué hacemos si se despega un electrodo?", "¿Cómo y cuándo devolveremos el equipo y conoceremos el informe?")
        ).copy(
            source = EducationSource("Gateshead Health NHS · Monitor de ECG ambulatorio", "https://www.gatesheadhealth.nhs.uk/resources/ambulatory-ecg-monitor/", "22 de noviembre de 2026"),
            summary = "El Holter registra el ritmo del corazón mientras la persona realiza sus actividades durante el tiempo indicado.",
            contentVersion = "2.1-holter",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("holter-fitting", "¿Cómo se coloca el monitor?", "during"),
                ProcedureRelatedQuestion("holter-diary", "¿Para qué sirve anotar síntomas y actividades?", "method"),
                ProcedureRelatedQuestion("holter-ecg", "¿En qué se diferencia de un ECG breve?", "comparison"),
            ),
            preparation = "La unidad puede pedir ropa que facilite descubrir el pecho para colocar los electrodos. Confirma los preparativos y los medicamentos habituales con el equipo; no suspendas tratamientos por cuenta propia.",
            additionalSources = listOf(
                EducationSource("University Hospitals Sussex · ECG ambulatorio", "https://www.uhsussex.nhs.uk/resources/ambulatory-ecg-monitoring/", "22 de noviembre de 2026"),
                EducationSource("Oxford University Hospitals · Monitoreo ambulatorio", "https://www.ouh.nhs.uk/cardiac/services/cardiology/physiology/ambulatory/", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.gatesheadhealth.nhs.uk/resources/ambulatory-ecg-monitor/", "https://www.uhsussex.nhs.uk/resources/ambulatory-ecg-monitoring/")
            },
            extraSections = listOf(
                ProcedureGuideSection("comparison", "Holter y electrocardiograma breve",
                    "Ambos registran señales eléctricas del corazón. Un electrocardiograma convencional observa un periodo breve durante la visita; un Holter conserva un registro mientras la persona sigue sus actividades durante más tiempo. La duración que se necesita varía según la pregunta clínica y el aparato utilizado.",
                    listOf("https://www.ouh.nhs.uk/cardiac/services/cardiology/physiology/ambulatory/"))
            ),
        ),

        entry("ultrasonido-renal", "Ultrasonido renal", ProcedureCategory.KIDNEYS,
            "https://www.radiologyinfo.org/es/info/ultrasound-renal",
            listOf("ultrasonido renal", "ecografia de riñones", "imagen de riñones"),
            "El ultrasonido renal, o ecografía renal, obtiene imágenes de los riñones con ondas sonoras. Los riñones son órganos que filtran desechos de la sangre y producen orina. El estudio puede incluir la vejiga, donde se almacena la orina.",
            "Ayuda a observar el tamaño, la forma y la ubicación de los riñones, y puede mostrar algunos cálculos, quistes u obstáculos al paso de la orina. Según la orden también puede valorar la vejiga. Solicitarlo no significa que se haya encontrado alguno de estos problemas.",
            "La persona suele acostarse en una camilla. El profesional coloca gel sobre la piel y mueve una pieza pequeña llamada transductor por el abdomen o los costados. Puede pedir que cambie de posición o contenga brevemente la respiración para ver mejor. El gel puede sentirse frío y la presión causar molestia en una zona sensible.",
            "El transductor envía ondas sonoras al cuerpo y recibe los ecos que rebotan. Una computadora convierte esos ecos en imágenes; no se introduce una cámara en el riñón y no se usan rayos X. En ocasiones se añade Doppler para observar el flujo de sangre en los vasos.",
            "El personal retira el gel al finalizar y las imágenes se envían para interpretación. Un profesional comunica el informe según la organización de la unidad. Si se pidió la vejiga llena, es posible que también se tome una imagen después de vaciarla.",
            "La imagen muestra estructuras, pero no sustituye las pruebas que estudian cómo filtran los riñones o si hay albúmina en la orina. El equipo integra las imágenes con otros estudios; Bitavida no interpreta hallazgos individuales.",
            listOf("¿Se revisará también la vejiga?", "¿Necesita acudir con la vejiga llena?", "¿Qué estructuras desean observar?", "¿Cómo complementa los análisis de sangre y orina?", "¿Cuándo conoceremos el informe?")
        ).copy(
            source = EducationSource("RadiologyInfo · Ultrasonido renal", "https://www.radiologyinfo.org/es/info/ultrasound-renal", "22 de noviembre de 2026"),
            summary = "Una ecografía usa ondas sonoras para obtener imágenes de los riñones y, en ocasiones, de la vejiga.",
            contentVersion = "2.2-ultrasonido-renal",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("renal-ultrasound-gel", "¿Qué ocurre durante el ultrasonido renal?", "experience"),
                ProcedureRelatedQuestion("renal-ultrasound-bladder", "¿Se necesita la vejiga llena?", "preparation"),
                ProcedureRelatedQuestion("renal-ultrasound-limits", "¿Puede medir cómo filtran los riñones?", "limitations"),
            ),
            preparation = "Puede requerirse beber agua para llenar la vejiga, pero no todas las órdenes son iguales. Confirma con la unidad si hace falta y cómo prepararse; comunica cualquier restricción de líquidos indicada por el equipo.",
            additionalSources = listOf(EducationSource("MedlinePlus · Pruebas y exámenes para los riñones", "https://medlineplus.gov/spanish/kidneytests.html", "22 de noviembre de 2026")),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf("https://www.radiologyinfo.org/es/info/ultrasound-renal", "https://medlineplus.gov/spanish/kidneytests.html") },
            extraSections = listOf(
                ProcedureGuideSection("experience", "Qué esperar de la exploración",
                    "Se puede solicitar ropa cómoda o proporcionar una bata para acceder al abdomen. El gel permite que las ondas pasen entre la piel y el transductor; la pieza se mueve por varias zonas para conseguir diferentes vistas. La exploración externa no requiere una incisión. Si mantener una posición resulta difícil o una zona está dolorida, avisa al personal para que ayude. La presión o la vejiga llena pueden ser incómodas para algunas personas.",
                    listOf("https://www.radiologyinfo.org/es/info/ultrasound-renal")),
                ProcedureGuideSection("limitations", "Lo que la imagen puede y no puede mostrar",
                    "La ecografía aporta una vista de la forma y otras características de los riñones; no mide por sí sola cuánto filtran. El gas en el abdomen o la dificultad para obtener imágenes claras pueden limitar lo que se observa. Si el equipo necesita aclarar una imagen puede considerar otras vistas o pruebas. Los análisis de sangre y orina responden preguntas distintas sobre la función renal.",
                    listOf("https://www.radiologyinfo.org/es/info/ultrasound-renal", "https://medlineplus.gov/spanish/kidneytests.html")),
            ),
        ),

        entry("radiografia-pie", "Radiografía del pie", ProcedureCategory.FEET,
            "https://www.radiologyinfo.org/es/info/how-to-prepare-your-x-ray-exam",
            listOf("rayos x del pie", "radiografia pie", "placa del pie"),
            "Una radiografía del pie utiliza una pequeña cantidad de radiación para obtener imágenes de sus estructuras. Los huesos absorben más rayos X que muchos tejidos blandos y por eso se distinguen en la imagen. No implica una incisión ni introducir una cámara en el pie.",
            "Puede ayudar al equipo a observar huesos y articulaciones cuando necesita investigar una lesión o un cambio del pie. También puede formar parte de la valoración de una posible infección profunda, junto con la exploración y otras pruebas. Pedir una radiografía no confirma por sí solo una fractura o una infección.",
            "El personal explica cómo colocar el pie frente a un aparato y un detector que registra la imagen. Según la posición necesaria, la persona puede estar sentada, acostada o de pie. Puede pedirse mantener el pie quieto unos segundos; es posible que se tomen imágenes desde distintos ángulos. Los rayos X no suelen producir una sensación, aunque colocar un pie doloroso puede incomodar.",
            "La máquina emite rayos X que atraviesan el pie y llegan al detector en distintas cantidades. El técnico o profesional de imagen acomoda la zona para responder a la pregunta de la orden. El examen de imágenes suele ser breve, pero el tiempo total de la visita depende de la unidad y del número de tomas.",
            "Un profesional especializado revisa las imágenes y prepara un informe para el equipo que solicitó el estudio. Los resultados no siempre están disponibles de inmediato. Pregunta cómo se entregarán y si deben compararse con imágenes anteriores.",
            "La radiografía utiliza radiación ionizante, por lo que el equipo considera su uso al solicitarla. Informa si existe posibilidad de embarazo y si la persona tiene dificultad para moverse o sostener la posición. Una imagen aislada no sustituye la valoración médica y no todas las alteraciones se ven igual con cada técnica.",
            listOf("¿Qué parte del pie se quiere observar?", "¿Cuántas posiciones o imágenes se necesitan?", "¿Cómo pueden ayudarnos si mover el pie causa dolor?", "¿Se comparará con estudios anteriores?", "¿Cuándo y con quién revisaremos el informe?")
        ).copy(
            source = EducationSource("RadiologyInfo · Preparación para rayos X", "https://www.radiologyinfo.org/es/info/how-to-prepare-your-x-ray-exam", "22 de noviembre de 2026"),
            summary = "La radiografía usa rayos X para obtener imágenes del pie, especialmente de sus huesos.",
            contentVersion = "2.1-radiografia-pie",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("foot-xray-sensation", "¿Qué se siente durante la radiografía?", "during"),
                ProcedureRelatedQuestion("foot-xray-preparation", "¿Cómo se prepara el estudio?", "preparation"),
                ProcedureRelatedQuestion("foot-xray-mri", "¿En qué se diferencia de una resonancia?", "comparison"),
            ),
            preparation = "La mayoría de las radiografías no requiere preparación especial. La unidad puede pedir retirar objetos o prendas que interfieran con la imagen. Comunica si existe posibilidad de embarazo o dificultad de movilidad; confirma las instrucciones particulares de la cita.",
            additionalSources = listOf(
                EducationSource("Royal Marsden NHS · Radiografía", "https://patientinfolibrary.royalmarsden.nhs.uk/document/download/1904", "22 de noviembre de 2026"),
                EducationSource("IWGDF/IDSA · Infecciones del pie relacionadas con diabetes", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/", "22 de noviembre de 2026"),
                EducationSource("NHS · Resonancia magnética", "https://www.nhs.uk/tests-and-treatments/mri-scan/", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.radiologyinfo.org/es/info/how-to-prepare-your-x-ray-exam", "https://patientinfolibrary.royalmarsden.nhs.uk/document/download/1904", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/")
            },
            extraSections = listOf(
                ProcedureGuideSection("comparison", "Radiografía y resonancia",
                    "Una radiografía permite observar especialmente los huesos mediante rayos X. La resonancia usa un campo magnético y ondas de radio para producir imágenes detalladas, incluso de tejidos blandos. Son herramientas distintas: el equipo elige las imágenes según lo que necesita estudiar.",
                    listOf("https://www.radiologyinfo.org/es/info/how-to-prepare-your-x-ray-exam", "https://www.nhs.uk/tests-and-treatments/mri-scan/"))
            )
        ),

        entry("resonancia-pie", "Resonancia magnética del pie", ProcedureCategory.FEET,
            "https://www.nhs.uk/tests-and-treatments/mri-scan/",
            listOf("resonancia de pie", "irm pie", "resonancia magnetica pie"),
            "La resonancia magnética obtiene imágenes detalladas utilizando un campo magnético y ondas de radio; no usa rayos X. En el pie puede mostrar huesos y tejidos blandos, como músculos y ligamentos. El aparato suele tener una abertura similar a un túnel y una camilla que se desplaza.",
            "Puede ayudar a investigar un problema del pie cuando se requieren imágenes más detalladas. En determinadas situaciones, si persisten dudas sobre una posible infección del hueso tras la exploración y otras pruebas, el equipo puede considerar una resonancia. Solicitarla no confirma que exista una infección.",
            "El personal verifica la información de seguridad y acomoda a la persona en la camilla, con el pie en la posición necesaria. La máquina produce golpes o zumbidos fuertes durante la obtención de imágenes, por lo que se utilizan tapones o audífonos. Conviene permanecer lo más quieto posible para evitar imágenes borrosas; el personal puede escuchar y hablar con la persona durante el estudio.",
            "El campo magnético y las ondas de radio permiten formar imágenes en distintas secciones de la zona estudiada. Algunas resonancias requieren contraste, una sustancia administrada por una vena para mostrar detalles adicionales; otras no. La máquina no corta ni toca el interior del pie, pero mantener la postura puede resultar incómodo.",
            "Un especialista revisa las imágenes y envía un informe al profesional que solicitó la prueba. Si se utilizó contraste o medicamentos para relajarse, la unidad explicará la observación y los cuidados posteriores que correspondan. Pregunta cuándo se revisarán los resultados.",
            "Antes de entrar al área de resonancia informa de implantes, dispositivos, fragmentos metálicos, enfermedad renal y antecedentes de reacciones al contraste. Algunos dispositivos requieren comprobaciones especiales; no supongas que todos impiden el estudio. Si el espacio cerrado o la postura preocupan, comunícalo antes de la cita. No se deben interpretar las imágenes sin el equipo de salud.",
            listOf("¿Qué parte del pie se desea estudiar y por qué?", "¿Se utilizará contraste y qué información necesitan sobre los riñones?", "¿Cómo comprobamos si un implante o dispositivo es compatible?", "¿Qué apoyo habrá si no puede permanecer quieto o teme al espacio cerrado?", "¿Cuándo estarán disponibles las imágenes y el informe?"),
            context = "Atención especializada"
        ).copy(
            source = EducationSource("NHS · Resonancia magnética", "https://www.nhs.uk/tests-and-treatments/mri-scan/", "22 de noviembre de 2026"),
            summary = "La resonancia produce imágenes detalladas del pie usando un campo magnético y ondas de radio.",
            contentVersion = "2.1-resonancia-pie",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("foot-mri-experience", "¿Qué sucede dentro del aparato de resonancia?", "experience"),
                ProcedureRelatedQuestion("foot-mri-contrast", "¿Siempre se usa contraste?", "method"),
                ProcedureRelatedQuestion("foot-mri-metal", "¿Qué debemos comunicar sobre implantes o metal?", "important"),
            ),
            preparation = "La unidad debe revisar los implantes o metales y confirmar si se usará contraste. Comunica enfermedad renal, reacciones previas y dificultades para permanecer quieto. Sigue las instrucciones específicas recibidas sin suspender tratamientos por cuenta propia.",
            additionalSources = listOf(
                EducationSource("Newcastle Hospitals NHS · Resonancia magnética", "https://www.newcastle-hospitals.nhs.uk/services/radiology/mri-scans/mri-scan/", "22 de noviembre de 2026"),
                EducationSource("IWGDF/IDSA · Infecciones del pie relacionadas con diabetes", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.nhs.uk/tests-and-treatments/mri-scan/", "https://www.newcastle-hospitals.nhs.uk/services/radiology/mri-scans/mri-scan/", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/")
            },
            extraSections = listOf(
                ProcedureGuideSection("experience", "Ruido, posición y comunicación",
                    "Los golpes y zumbidos son parte del funcionamiento del aparato. La parte que se estudia debe colocarse dentro del equipo; la posición exacta depende de la máquina y de la exploración. Si el ruido, el espacio o el dolor preocupan, el personal puede explicar qué apoyos están disponibles y cómo avisar durante la prueba.",
                    listOf("https://www.nhs.uk/tests-and-treatments/mri-scan/", "https://www.newcastle-hospitals.nhs.uk/services/radiology/mri-scans/mri-scan/"))
            ),
        ),

        entry("bypass-coronario", "Cirugía de bypass coronario", ProcedureCategory.HEART,
            "https://www.nhs.uk/tests-and-treatments/coronary-artery-bypass-graft/what-it-is/",
            listOf("bypass coronario", "cirugia de derivacion coronaria", "puente coronario"),
            "Las arterias coronarias llevan sangre al músculo del corazón. Un bypass o puente coronario es una operación que utiliza un vaso sanguíneo de otra parte del cuerpo para crear una ruta alrededor de un estrechamiento o bloqueo de una de esas arterias. Es diferente del bypass gástrico, que modifica el recorrido de los alimentos.",
            "El nuevo camino busca mejorar la llegada de sangre al corazón en determinadas personas con enfermedad de las arterias coronarias. Se valoran la situación, otras alternativas y los riesgos antes de decidir. Tener diabetes no significa que una persona necesite esta operación.",
            "Antes de la cirugía, el equipo conversa las alternativas, los antecedentes, la preparación y la anestesia. Durante la operación se utiliza anestesia general, por lo que la persona está dormida. El cirujano obtiene uno o más vasos del cuerpo, por ejemplo de la pierna o el pecho, y los conecta para rodear las zonas estrechas.",
            "Con frecuencia la operación se realiza mediante una abertura en el centro del pecho; en ciertas personas pueden utilizarse aberturas más pequeñas. A menudo se emplea una máquina que mantiene la circulación mientras el equipo trabaja en el corazón. El cirujano puede explicar la técnica prevista y por qué es adecuada para esa situación.",
            "Después suele haber vigilancia estrecha en cuidados intensivos. Al principio pueden utilizarse equipos para ayudar a respirar o tubos para drenar líquido; el personal explica cuándo pueden retirarse. La recuperación continúa con revisiones, manejo del dolor y rehabilitación; su duración varía y puede tomar meses.",
            "Entre las posibles complicaciones están infección de heridas, sangrado, alteraciones del ritmo, problemas renales, infarto o accidente cerebrovascular. No ocurren en todas las personas; el equipo puede explicar su importancia para cada caso. Pregunta por los signos que requieren atención urgente y no cambies medicamentos sin instrucciones.",
            listOf("¿Qué arterias se van a tratar y por qué se propone un bypass?", "¿Qué alternativas existen y cuáles son sus riesgos?", "¿De dónde se obtendrá el vaso y cómo se controlará el dolor?", "¿Qué podemos esperar en cuidados intensivos y durante la recuperación?", "¿Qué rehabilitación y señales de alarma debemos conocer?"),
            context = "Procedimiento o cirugía", cross = listOf(ProcedureCategory.SURGERY)
        ).copy(
            source = EducationSource("NHS · Qué es el bypass coronario", "https://www.nhs.uk/tests-and-treatments/coronary-artery-bypass-graft/what-it-is/", "22 de noviembre de 2026"),
            summary = "Un bypass coronario crea otra ruta para que la sangre llegue al músculo del corazón.",
            contentVersion = "2.1-bypass-coronario",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("coronary-purpose", "¿Qué es un puente coronario?", "what"),
                ProcedureRelatedQuestion("coronary-anesthesia", "¿La persona permanece despierta durante la operación?", "during"),
                ProcedureRelatedQuestion("coronary-recovery", "¿Qué puede suceder al terminar la cirugía?", "after"),
            ),
            preparation = "El equipo de cirugía dará las instrucciones individuales de preparación y revisará todos los medicamentos y antecedentes. No suspendas ni cambies tratamientos por cuenta propia.",
            additionalSources = listOf(
                EducationSource("NHS · Cómo se realiza el bypass coronario", "https://www.nhs.uk/tests-and-treatments/coronary-artery-bypass-graft/how-its-done/", "22 de noviembre de 2026"),
                EducationSource("NHS · Complicaciones del bypass coronario", "https://www.nhs.uk/tests-and-treatments/coronary-artery-bypass-graft/complications/", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.nhs.uk/tests-and-treatments/coronary-artery-bypass-graft/what-it-is/", "https://www.nhs.uk/tests-and-treatments/coronary-artery-bypass-graft/how-its-done/", "https://www.nhs.uk/tests-and-treatments/coronary-artery-bypass-graft/complications/")
            },
            extraSections = listOf(
                ProcedureGuideSection("terms", "Palabras que puedes escuchar",
                    "Arteria coronaria: vaso que lleva sangre al músculo del corazón. Injerto o puente: vaso utilizado para crear una nueva ruta. Anestesia general: medicamentos que mantienen dormida a la persona durante la operación. Cuidados intensivos: área del hospital donde el equipo vigila de cerca las funciones del cuerpo.",
                    listOf("https://www.nhs.uk/tests-and-treatments/coronary-artery-bypass-graft/what-it-is/", "https://www.nhs.uk/tests-and-treatments/coronary-artery-bypass-graft/how-its-done/"))
            ),
        ),

        entry("consulta-endocrinologia", "Consulta de endocrinología", ProcedureCategory.CONSULTS,
            "https://www.endocrine.org/our-community/advancing-endocrinology-and-public-health/field-of-endocrinology",
            listOf("consulta con endocrinologia", "endocrinologo", "consulta endocrina"),
            "La endocrinología es una especialidad que estudia problemas relacionados con hormonas. Las hormonas son señales químicas que el cuerpo produce para ayudar a regular diferentes funciones. La insulina, producida por el páncreas, ayuda a que la glucosa entre en las células para utilizarse como energía.",
            "Una consulta de endocrinología puede servir para revisar aspectos del seguimiento de diabetes u otras condiciones hormonales, como problemas de tiroides. La referencia depende de la situación de cada persona: no es una consulta obligatoria para todas las personas con diabetes ni confirma una complicación.",
            "El especialista pregunta por antecedentes, cambios notados y medicamentos. Si la visita está relacionada con diabetes, puede revisar los registros de glucosa o análisis disponibles y conversar sobre dificultades para seguir las indicaciones recibidas. Según el motivo y la unidad, puede solicitarse una exploración o alguna prueba; no todas las citas son iguales.",
            "La visita combina conversación, revisión de información y, cuando corresponde, exploración o estudios. El cuidador puede acompañar a la persona si ella lo desea y ayudarle a comunicar sus preguntas y los cambios observados. La persona y el equipo conversan las decisiones que correspondan; Bitavida no decide tratamientos.",
            "El profesional puede explicar el significado general de los estudios que ya tiene, solicitar información adicional o acordar una revisión posterior. Pregunta quién recibirá los resultados y cómo se coordinará el seguimiento con medicina familiar u otros profesionales.",
            "Una lectura aislada de glucosa no describe toda la situación ni permite que la aplicación ajuste un tratamiento. No cambies dosis, medicamentos ni horarios por cuenta propia; consulta cualquier dificultad o duda con el equipo que atiende a la persona.",
            listOf("¿Cuál es el motivo de esta consulta de endocrinología?", "¿Qué información aportan los registros y estudios que llevamos?", "¿Qué dificultades del cuidado conviene conversar hoy?", "¿Se necesita algún estudio adicional y cuándo conoceremos el resultado?", "¿Cómo se coordinará el seguimiento con medicina familiar?"),
            context = "Atención especializada"
        ).copy(
            source = EducationSource("Endocrine Society · Campo de la endocrinología", "https://www.endocrine.org/our-community/advancing-endocrinology-and-public-health/field-of-endocrinology", "22 de noviembre de 2026"),
            summary = "La consulta de endocrinología revisa problemas relacionados con hormonas y puede abordar el seguimiento de diabetes.",
            contentVersion = "2.1-consulta-endocrinologia",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("endocrine-what", "¿Qué estudia la endocrinología?", "what"),
                ProcedureRelatedQuestion("endocrine-insulin", "¿Por qué se habla de insulina?", "insulin"),
                ProcedureRelatedQuestion("endocrine-bring", "¿Qué información conviene llevar?", "preparation"),
            ),
            preparation = "Lleva una lista de medicamentos y, si están disponibles, estudios y registros de glucosa que el equipo haya solicitado. Confirma con la unidad qué información o preparativos necesita para esta cita.",
            additionalSources = listOf(
                EducationSource("NIDDK · Qué es la diabetes", "https://www.niddk.nih.gov/health-information/diabetes/overview/what-is-diabetes", "22 de noviembre de 2026"),
                EducationSource("Hillingdon Hospitals NHS · Qué llevar a la consulta de diabetes y endocrinología", "https://thh.nhs.uk/diabetes-and-endocrine-unit-what-to-bring-to-your-clinic-app", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.endocrine.org/our-community/advancing-endocrinology-and-public-health/field-of-endocrinology", "https://www.niddk.nih.gov/health-information/diabetes/overview/what-is-diabetes", "https://thh.nhs.uk/diabetes-and-endocrine-unit-what-to-bring-to-your-clinic-app")
            },
            extraSections = listOf(
                ProcedureGuideSection("insulin", "¿Por qué se habla de insulina en esta consulta?",
                    "La insulina es una hormona que ayuda a que la glucosa pase de la sangre a las células. En la diabetes tipo 2 el cuerpo no utiliza bien la insulina y el páncreas puede no producir la cantidad suficiente. El equipo explica cómo se relaciona esto con la atención de cada persona; no debe inferirse que toda persona necesita el mismo tratamiento.",
                    listOf("https://www.niddk.nih.gov/health-information/diabetes/overview/what-is-diabetes"))
            ),
        ),

        entry("consulta-nefrologia", "Consulta de nefrología", ProcedureCategory.CONSULTS,
            "https://www.newcastle-hospitals.nhs.uk/services/renal-services/new-renal-patient-clinic/",
            listOf("consulta con nefrologia", "nefrologo", "consulta de riñones"),
            "En una consulta de nefrología, un especialista en los riñones conversa con la persona sobre el motivo de la visita y revisa información de su salud. Los riñones ayudan a eliminar desechos y exceso de agua de la sangre y participan en el equilibrio de sustancias del cuerpo.",
            "La consulta puede ayudar a estudiar cambios observados en pruebas de sangre u orina, revisar antecedentes o dar seguimiento a un problema renal ya conocido. Existen muchos motivos de referencia. Visitar nefrología no significa por sí solo que una persona necesite diálisis o trasplante.",
            "El personal puede preguntar por enfermedades anteriores, cambios que la persona ha notado y medicamentos; también puede revisar la presión arterial, el peso y resultados disponibles. En algunas unidades se solicita una muestra de orina o una prueba de sangre, y en ocasiones una imagen de los riñones. No todas las personas necesitan los mismos estudios.",
            "Es una conversación y una valoración especializada, no un procedimiento de diálisis. El cuidador puede ayudar a presentar una lista de medicamentos, documentos previos y preguntas si la persona desea su acompañamiento. El especialista explica qué información falta y cómo se coordinará con otros profesionales.",
            "El equipo puede comentar los resultados disponibles, solicitar otra prueba o acordar una revisión posterior. Algunos informes pueden llegar después de la consulta. Conviene preguntar cuándo estarán listos, quién los explicará y a quién contactar si existen dudas.",
            "La función renal se evalúa con distintas piezas de información; una sola cifra no explica necesariamente la causa de un cambio. Bitavida no interpreta resultados personales ni decide tratamientos. No modifiques medicamentos o suplementos sin indicación del profesional.",
            listOf("¿Cuál fue el motivo de la referencia a nefrología?", "¿Qué muestran en conjunto las pruebas de sangre y orina que ya tenemos?", "¿Se necesita alguna muestra o estudio adicional y para qué?", "¿Cuándo conoceremos los resultados y quién los explicará?", "¿Cómo se coordinará el seguimiento con el resto del equipo?"),
            context = "Atención especializada", cross = listOf(ProcedureCategory.KIDNEYS)
        ).copy(
            source = EducationSource("Newcastle Hospitals NHS · Primera consulta renal", "https://www.newcastle-hospitals.nhs.uk/services/renal-services/new-renal-patient-clinic/", "22 de noviembre de 2026"),
            summary = "La consulta de nefrología permite revisar la salud de los riñones y conversar sobre su seguimiento.",
            contentVersion = "2.1-consulta-nefrologia",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("nephrology-what", "¿Qué hacen los riñones?", "what"),
                ProcedureRelatedQuestion("nephrology-tests", "¿Por qué pueden pedir sangre y orina?", "context"),
                ProcedureRelatedQuestion("nephrology-dialysis", "¿La consulta significa que necesita diálisis?", "purpose"),
            ),
            preparation = "Lleva la lista de medicamentos y resultados o documentos disponibles. Algunas unidades piden una muestra de orina o estudios previos; confirma con tu unidad si se necesitan y cómo prepararlos.",
            additionalSources = listOf(
                EducationSource("University Hospitals Sussex NHS · Consulta renal", "https://www.uhsussex.nhs.uk/resources/welcome-to-the-sussex-kidney-unit-outpatient-department/", "22 de noviembre de 2026"),
                EducationSource("Newcastle Hospitals NHS · Nefrología general", "https://www.newcastle-hospitals.nhs.uk/services/renal-services/general-nephrology-clinic/", "22 de noviembre de 2026"),
                EducationSource("NIDDK · Cómo funcionan los riñones", "https://www.niddk.nih.gov/health-information/kidney-disease/kidneys-how-they-work", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.newcastle-hospitals.nhs.uk/services/renal-services/new-renal-patient-clinic/", "https://www.uhsussex.nhs.uk/resources/welcome-to-the-sussex-kidney-unit-outpatient-department/")
            } + mapOf("what" to listOf("https://www.newcastle-hospitals.nhs.uk/services/renal-services/new-renal-patient-clinic/", "https://www.niddk.nih.gov/health-information/kidney-disease/kidneys-how-they-work")),
            extraSections = listOf(
                ProcedureGuideSection("context", "¿Por qué pueden pedir sangre y orina?",
                    "Los análisis de sangre y de orina aportan información diferente que el especialista revisa junto con los antecedentes y la exploración. Una consulta puede incluir una muestra el mismo día o una orden para otro momento, según la unidad. Pregunta qué se busca con cada prueba y cómo se comunicarán los resultados.",
                    listOf("https://www.newcastle-hospitals.nhs.uk/services/renal-services/new-renal-patient-clinic/", "https://www.uhsussex.nhs.uk/resources/welcome-to-the-sussex-kidney-unit-outpatient-department/"))
            ),
        ),

        entry("consulta-cardiologia", "Consulta de cardiología", ProcedureCategory.CONSULTS,
            "https://www.blackpoolteachinghospitals.nhs.uk/services/cardiac/departments/outpatients",
            listOf("consulta con cardiologia", "cardiologo", "consulta del corazon"),
            "En la consulta de cardiología, un especialista o integrante del equipo revisa la salud del corazón y conversa sobre el motivo de la visita. El corazón bombea sangre al cuerpo; su ritmo y su funcionamiento pueden estudiarse de distintas maneras.",
            "La visita puede servir para aclarar síntomas, revisar resultados o dar seguimiento a una atención anterior. Hay distintos motivos para acudir y no todas las personas con diabetes necesitan la misma valoración. Recibir una cita no confirma por sí mismo una enfermedad del corazón.",
            "El profesional pregunta por los cambios que la persona ha notado, cuándo aparecen, sus antecedentes y medicamentos. Puede revisar el pulso, la presión o escuchar el corazón. En algunas consultas se realiza o solicita un electrocardiograma, que registra señales eléctricas del corazón; en otras se necesitan estudios distintos o en otra fecha.",
            "Es una conversación y una exploración adaptadas al motivo de la referencia. El cuidador puede ayudar a recordar cuándo comenzaron las molestias y llevar informes previos, si la persona desea su acompañamiento. No todos los pacientes reciben las mismas pruebas ni una intervención durante la consulta.",
            "El equipo explica la información disponible y puede pedir más estudios o acordar una visita de seguimiento. Los informes pueden tardar en estar listos. Antes de salir pregunta quién los explicará, qué quedó pendiente y cómo se coordinará la atención con el médico que hizo la referencia.",
            "La aplicación no interpreta síntomas ni decide si se necesita un tratamiento. Si aparecen síntomas graves como dolor de pecho o dificultad intensa para respirar, busca atención urgente; no esperes a una cita programada. No suspendas ni cambies medicamentos sin indicación médica.",
            listOf("¿Cuál es el motivo de la valoración del corazón?", "¿Qué información de síntomas y antecedentes conviene revisar?", "¿Se realizará un electrocardiograma u otra prueba y para qué?", "¿Cuándo y con quién revisaremos los resultados?", "¿Qué cambios de salud requieren atención urgente?"),
            context = "Atención especializada", cross = listOf(ProcedureCategory.HEART)
        ).copy(
            source = EducationSource("Blackpool Teaching Hospitals NHS · Consultas de cardiología", "https://www.blackpoolteachinghospitals.nhs.uk/services/cardiac/departments/outpatients", "22 de noviembre de 2026"),
            summary = "La consulta de cardiología ayuda al equipo a valorar preguntas sobre el corazón y acordar el seguimiento.",
            contentVersion = "2.1-consulta-cardiologia",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("cardiology-visit", "¿Qué revisan en la consulta de cardiología?", "during"),
                ProcedureRelatedQuestion("cardiology-ecg", "¿Qué es un electrocardiograma?", "tests"),
                ProcedureRelatedQuestion("cardiology-emergency", "¿Qué síntomas requieren atención urgente?", "important"),
            ),
            preparation = "Lleva los documentos de la cita, una lista de medicamentos y estudios anteriores disponibles. Puede ayudar anotar los cambios que se desean comentar; confirma con la unidad si requiere algún preparativo.",
            additionalSources = listOf(
                EducationSource("Norfolk and Norwich NHS · Estudios cardiacos", "https://www.nnuh.nhs.uk/departments/cardiology/outpatients/cardiac-investigations/", "22 de noviembre de 2026"),
                EducationSource("Cambridge University Hospitals · Primera consulta de cardiología", "https://www.cuh.nhs.uk/my-planned-care-a-z/cardiology-first-outpatient-appointment/", "22 de noviembre de 2026"),
                EducationSource("NHLBI · Cómo funciona el corazón", "https://www.nhlbi.nih.gov/health/heart", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.blackpoolteachinghospitals.nhs.uk/services/cardiac/departments/outpatients", "https://www.nnuh.nhs.uk/departments/cardiology/outpatients/cardiac-investigations/")
            } + mapOf(
                "what" to listOf("https://www.blackpoolteachinghospitals.nhs.uk/services/cardiac/departments/outpatients", "https://www.nhlbi.nih.gov/health/heart"),
                "important" to listOf("https://www.cuh.nhs.uk/my-planned-care-a-z/cardiology-first-outpatient-appointment/")
            ),
            extraSections = listOf(
                ProcedureGuideSection("tests", "¿Qué es un electrocardiograma?",
                    "Un electrocardiograma o ECG registra señales eléctricas relacionadas con los latidos mediante sensores que se colocan sobre la piel. Es distinto de un ecocardiograma, que utiliza ultrasonido para producir imágenes del corazón. El equipo explica si alguno de estos estudios responde a la pregunta de la consulta.",
                    listOf("https://www.nnuh.nhs.uk/departments/cardiology/outpatients/cardiac-investigations/"))
            ),
        ),

        entry("consulta-neurologia", "Consulta de neurología", ProcedureCategory.CONSULTS,
            "https://www.cuh.nhs.uk/my-planned-care-a-z/neurology/",
            listOf("consulta con neurologia", "neurologo", "consulta neurologica"),
            "La neurología es la especialidad que estudia el sistema nervioso, formado entre otras partes por el cerebro, la médula espinal y los nervios. En esta consulta el profesional escucha los cambios o dificultades que motivaron la referencia y decide qué información necesita para valorarlos.",
            "La visita puede ayudar a estudiar cambios en el movimiento, la sensibilidad, la memoria u otras funciones, según el motivo de la referencia. Una misma manifestación puede tener explicaciones diferentes: acudir a neurología no confirma un diagnóstico ni implica que toda persona con diabetes necesite esta consulta.",
            "El especialista puede preguntar cuándo comenzaron los cambios, qué partes del cuerpo afectan y cómo han evolucionado. Según la consulta puede observar la marcha y explorar fuerza, equilibrio, reflejos o sensibilidad; por ejemplo, pedir que describa lo que siente al tocar distintas zonas. El examen varía según la pregunta clínica.",
            "La valoración combina una conversación sobre los síntomas y antecedentes con una exploración física cuando corresponde. El cuidador puede ayudar a describir lo que ha observado, respetando la participación y las preferencias de la persona. A veces se piden pruebas adicionales; no forman parte obligatoria de toda visita.",
            "Al terminar, el equipo comenta la información disponible y puede explicar si faltan estudios, cómo se comunicarán los resultados o si habrá seguimiento. Es útil salir sabiendo a quién acudir si surge una duda o los cambios evolucionan antes de la próxima revisión.",
            "No atribuyas automáticamente hormigueo, pérdida de fuerza o cambios de memoria a una causa concreta. Bitavida no diagnostica ni interpreta la exploración. Si ocurre una caída repentina de un lado de la cara, debilidad súbita en un brazo o dificultad repentina para hablar, busca atención de emergencia de inmediato.",
            listOf("¿Qué información sobre los cambios puede ayudar a la consulta?", "¿Qué partes de la exploración se realizarán y qué valoran?", "¿Se necesitan pruebas adicionales y qué información aportan?", "¿Cómo se comunicarán los resultados y el siguiente paso?", "¿Qué cambios requieren atención urgente?"),
            context = "Atención especializada"
        ).copy(
            source = EducationSource("Cambridge University Hospitals · Neurología", "https://www.cuh.nhs.uk/my-planned-care-a-z/neurology/", "22 de noviembre de 2026"),
            summary = "La consulta de neurología permite valorar cambios relacionados con el sistema nervioso.",
            contentVersion = "2.1-consulta-neurologia",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("neurology-visit", "¿Qué puede incluir la exploración neurológica?", "during"),
                ProcedureRelatedQuestion("neurology-reflex", "¿Qué es un reflejo?", "examination"),
                ProcedureRelatedQuestion("neurology-emergency", "¿Qué cambios requieren atención de emergencia?", "important"),
            ),
            preparation = "Lleva una lista de medicamentos, informes previos y una descripción de cuándo comenzaron y cómo cambiaron las dificultades. La ropa cómoda puede facilitar una exploración; confirma con la unidad los requisitos de la cita.",
            additionalSources = listOf(
                EducationSource("University Hospitals Birmingham NHS · Citas de neurología", "https://www.uhb.nhs.uk/services/neurology/referrals-and-appointments/", "22 de noviembre de 2026"),
                EducationSource("Cleveland Clinic · Exploración neurológica", "https://my.clevelandclinic.org/health/diagnostics/22664-neurological-exam", "22 de noviembre de 2026"),
                EducationSource("CDC · Signos de accidente cerebrovascular", "https://www.cdc.gov/stroke/signs-symptoms/index.html", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.cuh.nhs.uk/my-planned-care-a-z/neurology/", "https://www.uhb.nhs.uk/services/neurology/referrals-and-appointments/", "https://my.clevelandclinic.org/health/diagnostics/22664-neurological-exam")
            } + mapOf("important" to listOf("https://www.cuh.nhs.uk/my-planned-care-a-z/neurology/", "https://www.cdc.gov/stroke/signs-symptoms/index.html")),
            extraSections = listOf(
                ProcedureGuideSection("examination", "¿Qué es un reflejo?",
                    "Un reflejo es una respuesta automática del cuerpo ante un estímulo. Por ejemplo, el profesional puede tocar suavemente una zona cercana a la rodilla para observar un movimiento involuntario de la pierna. La exploración de reflejos es solo una parte posible de la valoración y se interpreta junto con los demás hallazgos.",
                    listOf("https://my.clevelandclinic.org/health/diagnostics/22664-neurological-exam"))
            ),
        ),

        entry("consulta-geriatria", "Consulta de geriatría", ProcedureCategory.CONSULTS,
            "https://www.imss.gob.mx/salud-en-linea/geriatrimss",
            listOf("consulta de geriatria", "consulta con geriatra", "medico de personas mayores"),
            "La geriatría se especializa en la atención de personas mayores. Su valoración puede considerar la salud, las actividades cotidianas y el entorno de la persona en conjunto, en lugar de mirar una sola enfermedad. La conversación parte de lo que la persona necesita y considera importante.",
            "La consulta puede ayudar a revisar cómo influyen diferentes problemas de salud en la movilidad, la alimentación, la memoria o la vida diaria. También puede ser una oportunidad para conversar sobre los medicamentos y los apoyos familiares. La atención se adapta a cada persona y no implica que todas las personas mayores tengan las mismas dificultades.",
            "El profesional puede preguntar qué actividades realiza la persona por sí misma, si ha tenido caídas, cómo duerme o come y qué medicamentos utiliza. Según el motivo, puede explorarla u observar cómo camina o se levanta de una silla. También puede preguntar por el apoyo en casa y las inquietudes de quien cuida.",
            "La valoración puede involucrar a profesionales de medicina, enfermería, nutrición, rehabilitación o trabajo social, según la unidad y la necesidad. El cuidador puede aportar información si la persona desea su compañía. Es importante hablar directamente con ella, respetar sus preferencias y permitirle participar en las decisiones.",
            "El equipo conversa los hallazgos, los objetivos de cuidado y los siguientes pasos con la persona y, cuando corresponda, con su familia. Puede coordinar otras revisiones o apoyos. Antes de salir conviene aclarar qué quedó acordado y quién dará seguimiento.",
            "La edad por sí sola no define la autonomía ni confirma una enfermedad. Medicina familiar y medicina interna también atienden a personas mayores; no todas necesitan las mismas consultas. Bitavida no diagnostica problemas de memoria, movilidad o nutrición ni modifica medicamentos.",
            listOf("¿Qué actividades son importantes para la persona y qué le resulta más difícil?", "¿Qué cambios de memoria, sueño, apetito o movilidad conviene conversar?", "¿Cómo revisaremos juntos todos los medicamentos que utiliza?", "¿Qué apoyos existen para la persona y para quien cuida?", "¿Quién coordinará el seguimiento con el resto del equipo?"),
            context = "Atención especializada"
        ).copy(
            source = EducationSource("IMSS · GeriatrIMSS", "https://www.imss.gob.mx/salud-en-linea/geriatrimss", "22 de noviembre de 2026"),
            summary = "La consulta de geriatría considera la salud y la vida cotidiana de la persona mayor de manera integral.",
            contentVersion = "2.1-consulta-geriatria",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("geriatrics-visit", "¿Qué revisan en geriatría?", "during"),
                ProcedureRelatedQuestion("geriatrics-function", "¿Qué significa valorar la vida cotidiana?", "function"),
                ProcedureRelatedQuestion("geriatrics-team", "¿Quiénes pueden participar en la atención?", "method"),
            ),
            preparation = "Lleva una lista de medicamentos, informes disponibles y, si la persona los utiliza, lentes, auxiliares auditivos o ayudas para caminar. Anota las preguntas que la persona y su cuidador deseen conversar.",
            additionalSources = listOf(
                EducationSource("IMSS · Evaluación gerontológica integral", "https://revistamedica.imss.gob.mx/index.php/revista_medica/article/view/2052", "22 de noviembre de 2026"),
                EducationSource("Derby and Burton NHS · Valoración integral de personas mayores", "https://www.uhdb.nhs.uk/service-casop", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.imss.gob.mx/salud-en-linea/geriatrimss", "https://revistamedica.imss.gob.mx/index.php/revista_medica/article/view/2052", "https://www.uhdb.nhs.uk/service-casop")
            },
            extraSections = listOf(
                ProcedureGuideSection("function", "¿Qué significa valorar la vida cotidiana?",
                    "El equipo puede preguntar cómo se desenvuelve la persona al caminar, vestirse o realizar otras actividades importantes para ella. Esto ayuda a comprender qué apoyos podrían ser útiles y qué cambios conviene revisar. Las preguntas se adaptan a la persona; necesitar ayuda en una actividad no define por completo su autonomía.",
                    listOf("https://revistamedica.imss.gob.mx/index.php/revista_medica/article/view/2052", "https://www.uhdb.nhs.uk/service-casop"))
            ),
        ),

        entry("valoracion-preoperatoria", "Valoración médica previa a una cirugía", ProcedureCategory.CONSULTS,
            "https://www.cheshireandmerseyside.nhs.uk/your-health/surgery-school/pre-operative-assessment-and-preparation/",
            listOf("valoracion preoperatoria", "consulta antes de la cirugia", "revision preoperatoria"),
            "La valoración preoperatoria es una revisión de salud realizada antes de una operación programada. Puede ocurrir en persona o por teléfono y sirve para que el equipo conozca antecedentes y prepare la atención y la anestesia. No es la operación misma y no garantiza por sí sola que no habrá complicaciones.",
            "Ayuda a identificar información que puede influir en la cirugía, la anestesia y la recuperación: enfermedades previas, alergias, medicamentos, dificultades de movilidad o apoyos necesarios en casa. Los profesionales pueden decidir si necesitan más información o preparar alguna parte de la atención antes de operar.",
            "Una enfermera u otro profesional pregunta por enfermedades, operaciones anteriores, alergias y todos los medicamentos, incluidos los de venta libre y productos herbales. Puede medir la presión o el pulso y solicitar análisis de sangre, electrocardiograma u otras pruebas cuando correspondan. No se necesitan las mismas pruebas para todas las personas o cirugías.",
            "Es una conversación y exploración adaptada a la operación prevista. Según los antecedentes, también puede participar un anestesiólogo, el profesional que planifica la anestesia y vigila las funciones del cuerpo durante la operación. La persona y el cuidador pueden expresar dudas sobre dolor, transporte, movilidad y recuperación.",
            "El equipo explica los pasos siguientes, a quién contactar y las instrucciones individuales para prepararse para la operación. Puede ser necesario revisar resultados o acudir a otra consulta. Si cambia la salud después de la valoración, conviene comunicarlo a la unidad para que pueda tenerlo en cuenta.",
            "La valoración no autoriza a la aplicación a decidir si alguien está listo para operarse. No suspendas ni ajustes medicamentos por iniciativa propia y no asumas un periodo de ayuno: pregunta al equipo tratante por las instrucciones exactas. La información educativa no reemplaza la explicación de riesgos ni el consentimiento para la cirugía.",
            listOf("¿Qué información de salud y medicamentos debemos llevar?", "¿Qué estudios se necesitan para esta operación y por qué?", "¿Quién explicará la anestesia y cómo se controlará el dolor?", "¿Qué instrucciones individuales recibiremos para medicamentos, alimentos y bebidas?", "¿A quién avisamos si cambia su salud antes de la cirugía?"),
            context = "Atención especializada", cross = listOf(ProcedureCategory.SURGERY)
        ).copy(
            source = EducationSource("NHS Cheshire and Merseyside · Valoración preoperatoria", "https://www.cheshireandmerseyside.nhs.uk/your-health/surgery-school/pre-operative-assessment-and-preparation/", "22 de noviembre de 2026"),
            summary = "La valoración preoperatoria reúne información de salud para ayudar al equipo a preparar una cirugía.",
            contentVersion = "2.1-valoracion-preoperatoria",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("preop-visit", "¿Qué revisan antes de una operación?", "during"),
                ProcedureRelatedQuestion("preop-medications", "¿Debemos cambiar los medicamentos por nuestra cuenta?", "important"),
                ProcedureRelatedQuestion("preop-home", "¿Por qué hablar del regreso a casa?", "planning"),
            ),
            preparation = "Reúne una lista de medicamentos y productos que utiliza la persona, antecedentes de alergias y documentos médicos disponibles. Confirma con la unidad qué información necesita para esta cita.",
            additionalSources = listOf(
                EducationSource("East Sussex Healthcare NHS · Valoración antes de cirugía", "https://www.esht.nhs.uk/service/anaesthetics/pre-operative-assessment/", "22 de noviembre de 2026"),
                EducationSource("University Hospitals Birmingham · Cita de valoración preoperatoria", "https://www.uhb.nhs.uk/services/surgery/before-your-operationsurgery-early-health-screening-and-pre-op-assessment/during-the-pre-op-assessment-appointment/", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.cheshireandmerseyside.nhs.uk/your-health/surgery-school/pre-operative-assessment-and-preparation/", "https://www.esht.nhs.uk/service/anaesthetics/pre-operative-assessment/", "https://www.uhb.nhs.uk/services/surgery/before-your-operationsurgery-early-health-screening-and-pre-op-assessment/during-the-pre-op-assessment-appointment/")
            },
            extraSections = listOf(
                ProcedureGuideSection("planning", "Planear el regreso a casa",
                    "La consulta puede servir para conversar cómo regresará la persona a casa, quién la acompañará y qué ayuda podría necesitar al desplazarse o realizar actividades. Las necesidades cambian según la operación y la recuperación esperada; el equipo puede orientar sobre los apoyos disponibles.",
                    listOf("https://www.uhb.nhs.uk/services/surgery/before-your-operationsurgery-early-health-screening-and-pre-op-assessment/during-the-pre-op-assessment-appointment/"))
            ),
        ),

        entry("consulta-cirugia-vascular", "Consulta de cirugía vascular", ProcedureCategory.CONSULTS,
            "https://www.uhdb.nhs.uk/service-vascular-surgery",
            listOf("consulta con cirujano vascular", "consulta vascular", "valoracion vascular"),
            "La cirugía vascular es una especialidad que valora y atiende problemas de los vasos sanguíneos. Las arterias llevan sangre desde el corazón a los tejidos y las venas la devuelven. Una consulta permite conversar sobre la circulación o un problema de esos vasos; acudir no implica que se realizará una operación.",
            "El equipo puede estudiar, por ejemplo, una posible reducción de circulación en las piernas, una herida o una alteración de las venas. También trabaja con otros especialistas cuando se valora un acceso para hemodiálisis. El motivo y la atención son diferentes en cada persona.",
            "El profesional pregunta por síntomas, antecedentes y medicamentos y revisa estudios disponibles. Si se valoran las piernas puede examinar el pie, la piel o los pulsos. Algunas unidades miden la presión en tobillos o dedos y pueden usar ultrasonido para observar los vasos; no todas las consultas requieren todas estas pruebas.",
            "La visita puede incluir una entrevista, exploración física y, si el equipo lo considera, pruebas de circulación. La medición de presión utiliza un manguito y el ultrasonido usa ondas sonoras para formar imágenes. Pregunta antes de la cita qué se hará y comunica si duele mover o apoyar la pierna.",
            "El equipo explica qué pudo observar y si necesita más información. Puede conversar distintas alternativas, coordinarse con otros profesionales o proponer seguimiento. Si hay resultados que llegan después, pregunta cuándo estarán disponibles y quién los comentará.",
            "Una referencia a cirugía vascular no confirma una obstrucción y no significa que la persona necesite una intervención. Las decisiones se toman con el equipo después de la valoración. No cambies medicamentos ni interpretes una prueba de circulación por cuenta propia.",
            listOf("¿Cuál es el motivo de esta valoración vascular?", "¿Qué partes de la circulación van a revisar y cómo?", "¿Qué información aporta la prueba de presión o el ultrasonido si se realiza?", "¿Qué alternativas se discutirán después de conocer los resultados?", "¿Quién coordinará el seguimiento y cuándo volveremos a conversar?"),
            context = "Atención especializada", cross = listOf(ProcedureCategory.HEART)
        ).copy(
            source = EducationSource("University Hospitals of Derby and Burton NHS · Cirugía vascular", "https://www.uhdb.nhs.uk/service-vascular-surgery", "22 de noviembre de 2026"),
            summary = "La consulta vascular permite estudiar problemas de circulación y conversar sobre su atención.",
            contentVersion = "2.1-consulta-vascular",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("vascular-visit", "¿Qué pueden revisar durante la consulta vascular?", "during"),
                ProcedureRelatedQuestion("vascular-arteries", "¿Qué diferencia hay entre arterias y venas?", "arteries"),
                ProcedureRelatedQuestion("vascular-operation", "¿La consulta significa que necesita una operación?", "important"),
            ),
            preparation = "Lleva la lista de medicamentos, informes anteriores y las ayudas para caminar que utilice la persona. Confirma con la unidad si hay estudios o preparativos específicos.",
            additionalSources = listOf(
                EducationSource("University Hospitals of Leicester NHS · Cirugía vascular", "https://www.uhleicester.nhs.uk/services/vascular-surgery/", "22 de noviembre de 2026"),
                EducationSource("NIDDK · Hemodiálisis y acceso vascular", "https://www.niddk.nih.gov/health-information/kidney-disease/kidney-failure/hemodialysis", "22 de noviembre de 2026"),
                EducationSource("NHLBI · Cómo circula la sangre", "https://www.nhlbi.nih.gov/health/heart/blood-flow", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.uhdb.nhs.uk/service-vascular-surgery", "https://www.uhleicester.nhs.uk/services/vascular-surgery/")
            } + mapOf(
                "what" to listOf("https://www.uhdb.nhs.uk/service-vascular-surgery", "https://www.nhlbi.nih.gov/health/heart/blood-flow"),
                "purpose" to listOf("https://www.uhdb.nhs.uk/service-vascular-surgery", "https://www.niddk.nih.gov/health-information/kidney-disease/kidney-failure/hemodialysis")
            ),
            extraSections = listOf(
                ProcedureGuideSection("arteries", "Arterias y venas no son lo mismo",
                    "Las arterias llevan sangre desde el corazón hacia el cuerpo; las venas la devuelven al corazón. Existen problemas diferentes en cada tipo de vaso. El especialista puede explicar cuáles se están valorando y qué prueba sirve para observarlos.",
                    listOf("https://www.nhlbi.nih.gov/health/heart/blood-flow"))
            ),
        ),

        entry("cateter-hemodialisis", "Colocación de catéter para hemodiálisis", ProcedureCategory.KIDNEYS,
            "https://www.niddk.nih.gov/health-information/kidney-disease/kidney-failure/hemodialysis",
            listOf("cateter de hemodialisis", "cateter venoso para dialisis", "acceso temporal hemodialisis"),
            "Un catéter de hemodiálisis es un tubo flexible que un profesional coloca en una vena grande. Una parte queda accesible fuera del cuerpo para conectarse al equipo de diálisis. La sangre sale hacia un filtro de la máquina y después regresa al organismo; el catéter es el acceso, no un riñón artificial por sí mismo.",
            "Puede utilizarse cuando se necesita hemodiálisis y todavía no hay otro acceso disponible, entre otras situaciones. A menudo es temporal, aunque la duración depende del plan de cada persona. Ni tener diabetes ni someterse a una prueba renal significa necesitar diálisis o un catéter.",
            "El equipo explica la colocación y responde las dudas antes del procedimiento. Generalmente se utiliza anestesia local para adormecer la zona; la persona puede estar despierta y recibir además medicamentos para relajarse. Puede notar un pinchazo al aplicar la anestesia o presión durante la colocación; si siente dolor o incomodidad debe avisar al profesional.",
            "El catéter puede colocarse en una vena del cuello, el pecho o cerca de la ingle. El profesional puede utilizar ultrasonido para localizar la vena; la zona de entrada se cubre y se asegura el tubo. Según la localización, puede tomarse una radiografía después para comprobar la posición y revisar posibles problemas.",
            "La unidad explica cuándo puede utilizarse el acceso, quién revisará el apósito y cómo protegerlo. Es posible notar una pequeña molestia o un moretón tras la colocación. El equipo también conversa si se prevé otro tipo de acceso para el tratamiento; no hay un tiempo universal de permanencia.",
            "El catéter puede infectarse, obstruirse por un coágulo o desplazarse; la colocación también puede causar sangrado o daño cercano. El equipo debe explicar cómo reconocer problemas y a dónde llamar. Si aparece dolor o sangrado en el sitio, contacta de inmediato a la unidad; si el sangrado persiste, busca atención urgente. No manipules el acceso sin instrucciones del equipo.",
            listOf("¿Por qué se propone este acceso y cuánto tiempo se espera usarlo?", "¿En qué vena se colocará y qué puede sentir durante la atención?", "¿Cómo se comprobará que está bien situado?", "¿Quién revisará el apósito y qué señales debemos comunicar?", "¿Qué otras opciones de acceso se valorarán?"),
            context = "Procedimiento o cirugía", cross = listOf(ProcedureCategory.SURGERY)
        ).copy(
            source = EducationSource("NIDDK · Hemodiálisis", "https://www.niddk.nih.gov/health-information/kidney-disease/kidney-failure/hemodialysis", "22 de noviembre de 2026"),
            summary = "Un catéter venoso permite conectar la sangre con el equipo de hemodiálisis cuando se necesita un acceso.",
            contentVersion = "2.1-cateter-hemodialisis",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("hemo-placement", "¿Qué puede sentir al colocar el catéter venoso?", "during"),
                ProcedureRelatedQuestion("hemo-fistula", "¿Qué diferencia hay con una fístula?", "difference"),
                ProcedureRelatedQuestion("hemo-followup", "¿Qué sucede después de colocarlo?", "after"),
            ),
            preparation = "La unidad revisará los antecedentes y medicamentos y dará las indicaciones correspondientes al procedimiento. Comunica si existen problemas de sangrado y no suspendas medicamentos por cuenta propia.",
            additionalSources = listOf(EducationSource("Cambridge University Hospitals · Colocación de línea de diálisis", "https://www.cuh.nhs.uk/patient-information/dialysis-line-insertion-central-venous-access/", "22 de noviembre de 2026")),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.niddk.nih.gov/health-information/kidney-disease/kidney-failure/hemodialysis", "https://www.cuh.nhs.uk/patient-information/dialysis-line-insertion-central-venous-access/")
            },
            extraSections = listOf(
                ProcedureGuideSection("difference", "Catéter venoso y fístula",
                    "El catéter es un tubo colocado en una vena. Una fístula para hemodiálisis es una conexión creada mediante cirugía entre una arteria y una vena, generalmente en el brazo; necesita tiempo para estar lista. Son accesos diferentes y el equipo explica cuál considera apropiado y por qué.",
                    listOf("https://www.niddk.nih.gov/health-information/kidney-disease/kidney-failure/hemodialysis"))
            ),
        ),

        entry("cateter-peritoneal", "Colocación de catéter para diálisis peritoneal", ProcedureCategory.KIDNEYS,
            "https://www.niddk.nih.gov/health-information/kidney-disease/kidney-failure/peritoneal-dialysis",
            listOf("cateter de dialisis peritoneal", "cateter abdominal para dialisis"),
            "El catéter peritoneal es un tubo flexible que se coloca en el abdomen. Un extremo queda dentro de la cavidad abdominal y otro sale por la piel. Permite introducir y retirar el líquido usado en la diálisis peritoneal; no entra en una vena como el catéter de hemodiálisis.",
            "En la diálisis peritoneal se utiliza el peritoneo, una membrana que recubre el interior del abdomen, como parte del proceso para eliminar desechos y exceso de líquido. El catéter permite que entre y salga el líquido de diálisis. Esta modalidad se decide junto con el equipo renal; no todas las personas con diabetes necesitan diálisis.",
            "Antes se revisan antecedentes, medicamentos y posibles cirugías abdominales, y se explica la intervención. El catéter puede colocarse con anestesia local, con la persona despierta, o mediante una operación con anestesia general o sedación, según la técnica. Con anestesia local puede sentirse presión; cualquier dolor o molestia debe comunicarse al equipo.",
            "Un profesional introduce el tubo en el abdomen mediante un procedimiento o cirugía. Una parte permanece dentro de la cavidad y una salida queda en la piel para conectar el líquido de diálisis. El equipo puede comprobar que el líquido circula por el tubo y cubrir el sitio con un apósito. La técnica concreta se explica antes de la atención.",
            "Después se vigilan la herida y el funcionamiento del catéter. La zona puede estar sensible. Habitualmente se deja tiempo para que cicatrice antes de comenzar la diálisis, aunque el momento se adapta a la situación. El equipo indicará las revisiones y proporcionará capacitación sobre el cuidado y el uso.",
            "Pueden ocurrir infección, sangrado, salida de líquido o movimiento del catéter que dificulte el drenaje; también hay riesgos de lesión de órganos cercanos durante la colocación. Busca atención urgente ante fiebre o escalofríos, malestar importante, sangrado o salida excesiva de líquido, pus o dolor intenso o prolongado en la zona. La unidad explicará a quién contactar y cómo cuidar el acceso.",
            listOf("¿Qué tipo de anestesia se propone y qué puede sentir?", "¿Dónde quedará la salida del catéter y cómo se cubrirá?", "¿Cuándo se revisará y cuándo podrá empezar a utilizarse?", "¿Quién enseñará el cuidado del acceso y la diálisis?", "¿Qué cambios requieren atención urgente y a quién debemos llamar?"),
            context = "Procedimiento o cirugía", cross = listOf(ProcedureCategory.SURGERY)
        ).copy(
            source = EducationSource("NIDDK · Diálisis peritoneal", "https://www.niddk.nih.gov/health-information/kidney-disease/kidney-failure/peritoneal-dialysis", "22 de noviembre de 2026"),
            summary = "Un catéter en el abdomen permite la entrada y salida del líquido de diálisis peritoneal.",
            contentVersion = "2.1-cateter-peritoneal",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("peritoneal-placement", "¿Qué anestesia puede utilizarse al colocarlo?", "during"),
                ProcedureRelatedQuestion("peritoneal-venous", "¿En qué se diferencia del catéter venoso?", "difference"),
                ProcedureRelatedQuestion("peritoneal-start", "¿Cuándo puede utilizarse después de colocarlo?", "after"),
            ),
            preparation = "El equipo explicará los preparativos según la técnica y revisará antecedentes y medicamentos. Pregunta qué instrucciones corresponden a esta cita; no modifiques medicamentos por cuenta propia.",
            additionalSources = listOf(
                EducationSource("NHS · Cómo se realiza la diálisis", "https://www.nhs.uk/tests-and-treatments/dialysis/how-its-done/", "22 de noviembre de 2026"),
                EducationSource("East Suffolk and North Essex NHS · Catéter de diálisis peritoneal", "https://www.esneft.nhs.uk/leaflet/peritoneal-dialysis-catheter-insertion/", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.niddk.nih.gov/health-information/kidney-disease/kidney-failure/peritoneal-dialysis", "https://www.esneft.nhs.uk/leaflet/peritoneal-dialysis-catheter-insertion/")
            },
            extraSections = listOf(
                ProcedureGuideSection("difference", "Catéter peritoneal y catéter venoso",
                    "El acceso peritoneal comunica una bolsa de líquido con el interior del abdomen; durante el tratamiento, ese líquido entra y sale para transportar desechos. El acceso para hemodiálisis comunica una vena con una máquina que filtra sangre fuera del cuerpo. Aunque ambos se llamen catéter, se colocan en lugares distintos y se usan de manera diferente.",
                    listOf("https://www.niddk.nih.gov/health-information/kidney-disease/kidney-failure/peritoneal-dialysis", "https://www.nhs.uk/tests-and-treatments/dialysis/how-its-done/"))
            ),
        ),
    )
}
