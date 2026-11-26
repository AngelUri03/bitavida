package com.bitavida.app.data.education

import com.bitavida.app.domain.education.EducationSource
import com.bitavida.app.domain.education.ProcedureCategory
import com.bitavida.app.domain.education.ProcedureGuide
import com.bitavida.app.domain.education.ProcedureGuideSection
import com.bitavida.app.domain.education.ProcedureRelatedQuestion

/** Specific institutional sources, consulted locally for the educational draft.
 * All entries remain pending clinical review before product publication.
 */
object ProcedureGuideSurgeryCatalog {
    private val abscess = EducationSource("Guy's and St Thomas' NHS · Tratamiento de absceso",
        "https://www.guysandstthomas.nhs.uk/health-information/abscess-treatment", "22 de noviembre de 2026")
    private val bone = EducationSource("NHS · Osteomielitis",
        "https://www.nhs.uk/conditions/osteomyelitis/", "22 de noviembre de 2026")
    private val amputation = EducationSource("NHS · Amputación",
        "https://www.nhs.uk/tests-and-treatments/amputation/", "22 de noviembre de 2026")
    private val ulcers = EducationSource("Lancashire and South Cumbria NHS · Úlceras del pie",
        "https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble", "22 de noviembre de 2026")
    private val bariatric = EducationSource("NIDDK · Tipos de cirugía para perder peso",
        "https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types", "22 de noviembre de 2026")
    private val vascular = EducationSource("NHLBI · Diagnóstico de la enfermedad arterial periférica",
        "https://www.nhlbi.nih.gov/health/peripheral-artery-disease/diagnosis", "22 de noviembre de 2026")

    private const val PREPARATION = "La preparación depende del procedimiento y de las indicaciones recibidas. Confírmala con la unidad médica; no cambies medicamentos por cuenta propia."

    private fun entry(
        id: String, title: String, aliases: List<String>, categories: List<ProcedureCategory>,
        source: EducationSource, description: String, purpose: String, during: String,
        method: String, after: String, important: String, questions: List<String>,
        context: String = "Procedimiento o cirugía",
    ) = ProcedureGuide(id, title, aliases, categories, context, description, purpose, during, method,
        PREPARATION, after, questions, important, source)

    val entries = listOf(
        entry(
            "drenaje-absceso-pie", "Drenaje quirúrgico de un absceso del pie",
            listOf("drenaje de absceso del pie", "drenar absceso en el pie"),
            listOf(ProcedureCategory.FEET, ProcedureCategory.SURGERY), abscess,
            "Un absceso es una acumulación de pus, un líquido que puede formarse cuando hay una infección. El drenaje consiste en abrir la zona para permitir que salga esa acumulación. En el pie de una persona con diabetes, el equipo también valora la profundidad y la circulación: la atención depende de cada situación.",
            "Drenar una colección puede ayudar a controlar la infección. Algunas infecciones profundas del pie requieren valoración quirúrgica urgente; otras situaciones tienen un manejo diferente. Que una persona tenga diabetes o una herida no significa que necesite este procedimiento.",
            "Antes se explica la intervención y cómo se aliviará el dolor. Según el tamaño y la localización puede utilizarse anestesia local, que adormece la zona mientras la persona está despierta, o anestesia general, con la persona dormida. Después de abrir y drenar la colección, el equipo limpia el área y coloca un apósito; en ciertos casos toma una muestra para analizarla.",
            "El procedimiento lo realiza personal sanitario. Algunas heridas se dejan abiertas para que puedan seguir drenando y cicatricen; el equipo decide cómo proteger y revisar la zona. Un apósito es el material que cubre la herida: no todos los apósitos ni formas de cuidado son iguales.",
            "Puede haber molestias después de la atención. El equipo explicará cómo se vigilará la herida, quién cambiará el apósito y si se necesita otra revisión o tratamiento de la infección. La estancia y la recuperación dependen de la profundidad y del estado general, por lo que no puede asegurarse un plazo universal.",
            "No intentes abrir, exprimir ni drenar una lesión en casa. Si aparece fiebre, malestar general, o aumentan el enrojecimiento o la hinchazón, busca valoración médica urgente. El sangrado, la cicatrización lenta y la reaparición del absceso son complicaciones posibles que conviene conversar con el equipo.",
            listOf("¿Dónde se encuentra la acumulación y qué tan profunda es?", "¿Qué tipo de anestesia se propone y qué puede sentir?", "¿Se tomará una muestra para buscar la causa de la infección?", "¿Quién revisará la herida y cuándo?", "¿Qué cambios requieren atención urgente y a dónde debemos acudir?")
        ).copy(
            summary = "El drenaje permite evacuar una acumulación de pus del pie cuando el equipo considera necesario tratarla.",
            contentVersion = "2.2-drenaje-absceso",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("abscess-what", "¿Qué es un absceso?", "what"),
                ProcedureRelatedQuestion("abscess-anesthesia", "¿Qué anestesia podría utilizarse?", "during"),
                ProcedureRelatedQuestion("abscess-after", "¿Qué sucede después del drenaje?", "after"),
            ),
            preparation = "Si hay tiempo para preparar la atención, informa al equipo sobre los medicamentos y antecedentes de la persona. La unidad explicará las indicaciones según la anestesia y la urgencia; no cambies tratamientos por cuenta propia.",
            additionalSources = listOf(
                EducationSource("IWGDF/IDSA · Infecciones del pie relacionadas con diabetes", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/", "22 de noviembre de 2026"),
                EducationSource("NHS · Absceso de la piel", "https://www.nhs.uk/conditions/skin-abscess/", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = mapOf(
                "what" to listOf("https://www.nhs.uk/conditions/skin-abscess/"),
                "purpose" to listOf("https://www.idsociety.org/practice-guideline/diabetic-foot-infections/"),
                "during" to listOf("https://www.guysandstthomas.nhs.uk/health-information/abscess-treatment", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/"),
                "method" to listOf("https://www.guysandstthomas.nhs.uk/health-information/abscess-treatment"),
                "preparation" to listOf("https://www.guysandstthomas.nhs.uk/health-information/abscess-treatment"),
                "after" to listOf("https://www.guysandstthomas.nhs.uk/health-information/abscess-treatment"),
                "important" to listOf("https://www.guysandstthomas.nhs.uk/health-information/abscess-treatment", "https://www.nhs.uk/conditions/skin-abscess/"),
                "questions" to listOf("https://www.guysandstthomas.nhs.uk/health-information/abscess-treatment", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/")
            ),
            extraSections = listOf(
                ProcedureGuideSection("terms", "Palabras que puedes escuchar",
                    "Absceso: acumulación de pus en los tejidos. Drenaje: salida de ese líquido mediante un procedimiento. Apósito: material que cubre y protege una herida. Cultivo: análisis de una muestra para buscar microorganismos y orientar al equipo sobre la infección.",
                    listOf("https://www.nhs.uk/conditions/skin-abscess/", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/", "https://www.guysandstthomas.nhs.uk/health-information/abscess-treatment")),
                ProcedureGuideSection("why-drain", "¿Por qué se considera abrir y drenar?",
                    "El pus puede acumularse dentro de los tejidos en vez de salir por sí solo. Drenar permite evacuar esa acumulación y limpiar la zona. Algunos abscesos pequeños pueden tratarse de otra manera, pero una infección profunda del pie en una persona con diabetes puede necesitar valoración quirúrgica urgente. El equipo decide según la extensión de la infección y la circulación; no se puede determinar la profundidad solo por el aspecto exterior. Los antibióticos, cuando se indican, y el drenaje cumplen funciones distintas: pregunta si se necesitan ambos en este caso.",
                    listOf("https://www.guysandstthomas.nhs.uk/health-information/abscess-treatment", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/")),
                ProcedureGuideSection("experience", "¿Qué puede experimentar la persona?",
                    "Con anestesia local la persona permanece despierta y la zona se adormece; con anestesia general está dormida. El equipo elige la opción según la ubicación y el tamaño del absceso y explica qué esperar. Es importante avisar si aparece dolor o si la posición resulta incómoda. Tras el drenaje pueden sentirse molestias durante unos días o al cambiar el apósito. Pregunta cómo se aliviarán y comunica cualquier dolor que preocupe.",
                    listOf("https://www.guysandstthomas.nhs.uk/health-information/abscess-treatment")),
                ProcedureGuideSection("wound-followup", "¿Por qué la herida puede quedar abierta?",
                    "En algunas atenciones se deja abierta la zona tratada para que continúe drenando, en lugar de cerrarla con puntos. Un apósito la cubre y protege mientras se revisa su evolución. La persona cuidadora puede preguntar quién lo cambiará, cuándo se revisará la herida y qué hacer si el apósito presenta problemas. La forma de cuidado y la frecuencia de las revisiones dependen del plan del equipo y de la herida; no intentes drenar ni retirar tejido en casa.",
                    listOf("https://www.guysandstthomas.nhs.uk/health-information/abscess-treatment"))
            )
        ),
        entry(
            "cirugia-osteomielitis-pie", "Cirugía por infección del hueso del pie",
            listOf("cirugía por osteomielitis del pie", "operación por infección del hueso del pie"),
            listOf(ProcedureCategory.FEET, ProcedureCategory.SURGERY), bone,
            "La osteomielitis es una infección del hueso. En el pie puede presentarse cuando una infección de la piel o una herida se extiende a tejidos profundos. En algunas situaciones se propone una operación para retirar hueso o tejido afectado; en otras, el equipo puede considerar tratamiento sin cirugía.",
            "La operación busca ayudar a controlar la infección retirando tejido infectado o dañado. Cuando una parte del hueso ha perdido su aporte de sangre, puede ser más difícil que los antibióticos lleguen a esa zona. La decisión considera, entre otras cosas, la infección, los tejidos afectados y la circulación.",
            "Antes, el equipo explica la zona que se tratará, las alternativas y la anestesia. Puede revisar estudios de imagen y muestras para comprender la infección. Durante la operación se accede al área afectada y se retira tejido dañado o infectado; también pueden tomarse muestras para que un laboratorio ayude a identificar microorganismos.",
            "La cirugía se realiza en una unidad hospitalaria por un equipo especializado. Según la extensión, pueden participar profesionales de cirugía, infecciones y otras áreas. La anestesia y la forma de cerrar o reconstruir la zona se deciden para cada caso; pregunta qué experiencia espera el equipo para la persona que acompañas.",
            "La atención posterior puede incluir revisión de la herida, apoyo para caminar y antibióticos indicados por el equipo. Algunas personas necesitan otra intervención o reconstrucción. El tiempo de recuperación varía y depende de la herida y de la circulación; pregunta cómo se vigilará la evolución y quién coordina las revisiones.",
            "No toda infección del hueso requiere la misma operación y, en circunstancias seleccionadas, el equipo puede valorar antibióticos sin cirugía. Una herida en el pie tampoco significa por sí sola que el hueso esté infectado. No modifiques los antibióticos por cuenta propia. Comunica al equipo cualquier cambio de la herida o empeoramiento general.",
            listOf("¿Qué hueso y qué tejidos están afectados?", "¿Qué estudios o muestras ayudan a confirmar la infección?", "¿Por qué se propone cirugía y qué alternativas existen en este caso?", "¿Qué tipo de anestesia, recuperación y apoyo para caminar esperan?", "¿Quién revisará la herida y a quién llamamos si empeora?")
        ).copy(
            summary = "La osteomielitis es una infección del hueso; algunas situaciones pueden requerir retirar tejido afectado.",
            contentVersion = "2.2-osteomielitis-pie",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("bone-infection-what", "¿Qué es la osteomielitis?", "what"),
                ProcedureRelatedQuestion("bone-infection-tests", "¿Qué pruebas pueden ayudar a valorar la infección?", "tests"),
                ProcedureRelatedQuestion("bone-infection-after", "¿Qué puede suceder después de la operación?", "after"),
            ),
            preparation = "El equipo explicará qué preparación corresponde, considerando el estado de la infección y la anestesia. Informa sobre medicamentos y antecedentes sin suspender ni modificar tratamientos por iniciativa propia.",
            additionalSources = listOf(
                EducationSource("IWGDF/IDSA · Infecciones del pie relacionadas con diabetes", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/", "22 de noviembre de 2026"),
                EducationSource("Oxford University Hospitals · Infección del hueso", "https://www.ouh.nhs.uk/limbreconstruction/information/conditions/bone-infection/", "22 de noviembre de 2026"),
                EducationSource("Royal National Orthopaedic Hospital · Cirugía por infección del hueso", "https://www.rnoh.nhs.uk/patients-and-visitors/patient-information-guides/bone-infection-surgery", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = mapOf(
                "what" to listOf("https://www.nhs.uk/conditions/osteomyelitis/", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/"),
                "purpose" to listOf("https://www.ouh.nhs.uk/limbreconstruction/information/conditions/bone-infection/"),
                "during" to listOf("https://www.idsociety.org/practice-guideline/diabetic-foot-infections/", "https://www.rnoh.nhs.uk/patients-and-visitors/patient-information-guides/bone-infection-surgery"),
                "method" to listOf("https://www.rnoh.nhs.uk/patients-and-visitors/patient-information-guides/bone-infection-surgery"),
                "preparation" to listOf("https://www.rnoh.nhs.uk/patients-and-visitors/patient-information-guides/bone-infection-surgery"),
                "after" to listOf("https://www.nhs.uk/conditions/osteomyelitis/", "https://www.rnoh.nhs.uk/patients-and-visitors/patient-information-guides/bone-infection-surgery"),
                "important" to listOf("https://www.idsociety.org/practice-guideline/diabetic-foot-infections/", "https://www.rnoh.nhs.uk/patients-and-visitors/patient-information-guides/bone-infection-surgery"),
                "questions" to listOf("https://www.idsociety.org/practice-guideline/diabetic-foot-infections/", "https://www.rnoh.nhs.uk/patients-and-visitors/patient-information-guides/bone-infection-surgery")
            ),
            extraSections = listOf(
                ProcedureGuideSection("tests", "¿Qué información pueden aportar los estudios?",
                    "Las radiografías, otros estudios de imagen y los análisis ayudan al equipo a investigar si hay infección y qué zona está afectada. En algunas situaciones se estudia una muestra de tejido o hueso para buscar microorganismos. Ningún resultado debe interpretarse de manera aislada por el cuidador: el profesional lo relaciona con la exploración y los demás hallazgos.",
                    listOf("https://www.idsociety.org/practice-guideline/diabetic-foot-infections/")),
                ProcedureGuideSection("terms", "Palabras que puedes escuchar",
                    "Osteomielitis significa infección del hueso. Desbridamiento es la retirada de tejido dañado o infectado por personal sanitario. Un cultivo es el análisis de una muestra para identificar microorganismos. Reconstrucción significa reparar o dar cobertura a una zona cuando es necesario.",
                    listOf("https://www.nhs.uk/conditions/osteomyelitis/", "https://www.rnoh.nhs.uk/patients-and-visitors/patient-information-guides/bone-infection-surgery")),
                ProcedureGuideSection("bone-sample", "¿Para qué pueden tomar una muestra?",
                    "Si el equipo sospecha que la infección ha llegado al hueso, puede valorar una muestra para buscar los microorganismos implicados. En determinadas situaciones se obtiene durante una operación; en otras, mediante una toma realizada por personal sanitario. Un cultivo puede ayudar a orientar el tratamiento, pero debe interpretarse junto con la exploración y los demás estudios. Pregunta si se tomará una muestra, cuándo se espera el resultado y quién explicará su significado.",
                    listOf("https://www.idsociety.org/practice-guideline/diabetic-foot-infections/", "https://www.nhs.uk/conditions/osteomyelitis/")),
                ProcedureGuideSection("repair-followup", "¿Qué puede necesitar el pie después?",
                    "Al retirar hueso dañado puede quedar un espacio o una zona que necesita cobertura y, en algunos casos, reconstrucción. No todas las personas necesitan el mismo procedimiento: algunas pueden requerir más de una intervención. Después, el equipo explica cómo se revisará la herida, qué apoyo puede recibir el pie y si hay antibióticos indicados. Conviene preguntar quién coordina las revisiones y qué cambios de la herida o del estado general requieren atención sin demora.",
                    listOf("https://www.nhs.uk/conditions/osteomyelitis/", "https://www.rnoh.nhs.uk/patients-and-visitors/patient-information-guides/bone-infection-surgery"))
            )
        ),
        entry(
            "amputacion-menor-pie", "Amputación menor de dedo o parte del pie",
            listOf("amputación de dedo del pie", "amputación parcial del pie", "amputación menor"),
            listOf(ProcedureCategory.FEET, ProcedureCategory.SURGERY), amputation,
            "Una amputación es una operación para retirar una parte del cuerpo. En una amputación menor del pie puede retirarse un dedo o una porción del pie; el nivel indica qué parte se retira. Es distinta de una amputación de pierna por encima o por debajo de la rodilla.",
            "Puede plantearse ante daños graves como una infección que no se controla o tejido sin circulación suficiente. El propósito depende de la situación: por ejemplo, tratar el tejido afectado o evitar que empeore. Tener diabetes o una herida no significa que la persona vaya a necesitar esta operación; el equipo explica por qué se considera y qué alternativas existen.",
            "Antes de una operación programada el equipo revisa la salud general, la herida, la circulación y los medicamentos, y conversa sobre el nivel propuesto. Si la atención es urgente, algunas valoraciones pueden hacerse en menos tiempo. Puede usarse anestesia general, con la persona dormida, o anestesia que adormece una región; el equipo explica la opción prevista.",
            "Durante la cirugía se retira la porción acordada y se atienden los tejidos restantes. Al terminar la herida puede cubrirse con un vendaje. El plan de cierre, protección y control del dolor depende de la infección, la circulación y la zona intervenida.",
            "Al recuperarse se revisan la herida y el dolor. Algunas personas necesitan un zapato adaptado o apoyos para caminar mientras sana el pie. La rehabilitación puede ayudar a recuperar actividades; el equipo explica cuánto peso es apropiado apoyar y cuándo revisará la herida, sin que exista un plazo igual para todas las personas.",
            "Pueden presentarse sangrado, infección o dificultad para cicatrizar; en ocasiones se necesita más atención. Algunas personas sienten como si el dedo retirado siguiera presente: se llama sensación fantasma y puede o no ser dolorosa. Ese dolor es real y debe comunicarse al equipo. Pregunta qué cambios de la herida requieren atención urgente.",
            listOf("¿Qué parte del pie se propone retirar y por qué?", "¿Qué alternativas y qué posibilidades de cicatrización se han valorado?", "¿Cómo aliviarán el dolor y qué anestesia se propone?", "¿Qué calzado o apoyo necesitará y cuándo podremos apoyar el pie?", "¿Quién revisará la herida y qué cambios requieren atención urgente?")
        ).copy(
            summary = "Una amputación menor retira un dedo o una parte del pie cuando el equipo considera que existe una razón médica para ello.",
            contentVersion = "2.2-amputacion-menor",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("minor-what", "¿Qué parte puede retirarse en una amputación menor?", "what"),
                ProcedureRelatedQuestion("minor-walking", "¿Qué apoyo puede necesitar para caminar?", "daily-life"),
                ProcedureRelatedQuestion("minor-sensation", "¿Qué es la sensación fantasma?", "important"),
            ),
            preparation = "Si la cirugía se programa, el equipo explica las revisiones y los preparativos. Informa sobre medicamentos y antecedentes y confirma las instrucciones de la unidad; no suspendas ni cambies tratamientos por tu cuenta.",
            additionalSources = listOf(
                EducationSource("University Hospital Southampton · Retirada de tejido y amputación de dedo y pie", "https://www.uhs.nhs.uk/Media/UHS-website-2019/Patientinformation/Cardiovascular-and-thoracic/Tissue-removal-and-amputation-of-the-toe-and-the-foot-1660-PIL.pdf", "22 de noviembre de 2026"),
                EducationSource("University Hospitals Plymouth · Retirada de tejido y amputación de dedo", "https://www.plymouthhospitals.nhs.uk/display-pil/pil-tissue-removal-and-toe-amputation-7183/", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = mapOf(
                "what" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/"),
                "purpose" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/", "https://www.plymouthhospitals.nhs.uk/display-pil/pil-tissue-removal-and-toe-amputation-7183/"),
                "during" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/"),
                "method" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/"),
                "preparation" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/"),
                "after" to listOf("https://www.plymouthhospitals.nhs.uk/display-pil/pil-tissue-removal-and-toe-amputation-7183/"),
                "important" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/", "https://www.uhs.nhs.uk/Media/UHS-website-2019/Patientinformation/Cardiovascular-and-thoracic/Tissue-removal-and-amputation-of-the-toe-and-the-foot-1660-PIL.pdf"),
                "questions" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/", "https://www.plymouthhospitals.nhs.uk/display-pil/pil-tissue-removal-and-toe-amputation-7183/")
            ),
            extraSections = listOf(
                ProcedureGuideSection("daily-life", "Caminar y actividades después",
                    "El equilibrio, el calzado y la forma de apoyar pueden necesitar valoración después de retirar parte del pie. El equipo puede indicar un zapato para reducir presión sobre la herida y valorar adaptaciones posteriores. Pregunta por ayudas y por la forma segura de desplazarse en casa; las indicaciones dependen de la zona operada y de cómo esté sanando.",
                    listOf("https://www.plymouthhospitals.nhs.uk/display-pil/pil-tissue-removal-and-toe-amputation-7183/")),
                ProcedureGuideSection("wound-and-support", "¿Cómo se acompaña la recuperación de la herida?",
                    "Después de la operación el equipo vigila la herida, el vendaje y las molestias. La herida puede necesitar curaciones posteriores y, si se indicó por una infección, tratamiento adicional. Un profesional de rehabilitación puede ayudar a valorar el movimiento y el apoyo; en algunos casos se utiliza calzado especial mientras cicatriza o se estudia una adaptación posterior. Antes del alta, pregunta quién hará las curaciones, cómo contactar al equipo y qué indicaciones de apoyo corresponden a esa persona.",
                    listOf("https://www.plymouthhospitals.nhs.uk/display-pil/pil-tissue-removal-and-toe-amputation-7183/", "https://www.uhs.nhs.uk/Media/UHS-website-2019/Patientinformation/Cardiovascular-and-thoracic/Tissue-removal-and-amputation-of-the-toe-and-the-foot-1660-PIL.pdf")),
                ProcedureGuideSection("phantom-sensation", "Sensaciones en el dedo que se retiró",
                    "Algunas personas sienten que el dedo retirado todavía está allí. Esa sensación se llama fantasma y no siempre duele; cuando sí duele, el dolor es real y merece atención. También puede haber dolor en la zona operada por otros motivos. Contar al equipo dónde se siente, cómo se describe y cuándo aparece puede ayudarle a valorar el problema. La adaptación emocional también puede necesitar acompañamiento; es válido pedir apoyo.",
                    listOf("https://www.nhs.uk/tests-and-treatments/amputation/", "https://www.plymouthhospitals.nhs.uk/display-pil/pil-tissue-removal-and-toe-amputation-7183/"))
            )
        ),
        entry(
            "amputacion-mayor-pierna", "Amputación mayor de una extremidad inferior",
            listOf("amputación de pierna", "amputación mayor", "amputación debajo de la rodilla"),
            listOf(ProcedureCategory.FEET, ProcedureCategory.SURGERY), amputation,
            "Esta operación retira una parte de la pierna. El nivel puede estar por debajo o por encima de la rodilla, entre otras posibilidades. La parte que permanece se llama extremidad residual. La elección del nivel depende de la circulación y del estado de los tejidos, entre otros factores.",
            "Puede considerarse ante una infección grave que no se controla, una lesión extensa o tejido sin circulación suficiente. El objetivo y las alternativas deben explicarse para la situación concreta. La diabetes por sí sola no significa que se necesite una amputación; tampoco todas las heridas o infecciones llevan a esta cirugía.",
            "Si no se trata de una emergencia, antes suelen revisarse la salud general, los medicamentos, la circulación y las necesidades de rehabilitación. El equipo explica por qué propone un nivel, qué alternativas se valoraron y cómo controlará el dolor. La operación puede realizarse con anestesia general, que mantiene dormida a la persona, o con anestesia que adormece la parte inferior del cuerpo.",
            "Durante la operación el cirujano retira la parte afectada y prepara los tejidos que permanecen. Al terminar, la herida se cubre con un vendaje y en ocasiones se utiliza un tubo para drenar líquido. El equipo explica los detalles de la técnica y cuánto tiempo se vigilará a la persona en el hospital.",
            "Después se revisan la herida, el dolor y la recuperación general. Profesionales de rehabilitación pueden trabajar con la persona para desplazarse y realizar actividades cotidianas. Una silla de ruedas, otras ayudas o una prótesis pueden valorarse según las condiciones, posibilidades y preferencias; no todas las personas utilizan prótesis.",
            "Es posible experimentar sangrado, infección, coágulos o cicatrización lenta. Algunas personas sienten la parte que ya no está; esa sensación fantasma puede o no doler. Si hay dolor, es real y el equipo puede ofrecer atención. La adaptación emocional puede llevar tiempo: preguntar por apoyo psicológico o social también forma parte del cuidado.",
            listOf("¿Por qué se propone este nivel y qué alternativas se consideraron?", "¿Qué anestesia y opciones para controlar el dolor se utilizarán?", "¿Cómo se revisará la herida y cuáles son los signos de alarma?", "¿Qué apoyos para movernos y adaptar la casa podrían necesitarse?", "¿Con quién podemos hablar sobre rehabilitación y apoyo emocional?")
        ).copy(
            summary = "Una amputación mayor retira una parte de la pierna; el nivel y la rehabilitación dependen de cada situación.",
            contentVersion = "2.1-amputacion-mayor",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("major-level", "¿Qué significa el nivel de amputación?", "what"),
                ProcedureRelatedQuestion("major-rehab", "¿Qué puede incluir la rehabilitación?", "rehabilitation"),
                ProcedureRelatedQuestion("major-phantom", "¿Qué es la sensación fantasma?", "phantom"),
            ),
            preparation = "En una cirugía programada, el equipo revisa la salud general y explica cómo prepararse. Comunica los medicamentos y antecedentes y confirma las indicaciones; no suspendas tratamientos sin instrucciones del profesional.",
            additionalSources = listOf(
                EducationSource("Cambridge University Hospitals · Amputación de pierna", "https://www.cuh.nhs.uk/patient-information/leg-amputation/", "22 de noviembre de 2026"),
                EducationSource("Guy's and St Thomas' NHS · Recuperación después de amputación de pierna", "https://www.guysandstthomas.nhs.uk/leg-amputation/recovery-after-surgery", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = mapOf(
                "what" to listOf("https://www.cuh.nhs.uk/patient-information/leg-amputation/"),
                "purpose" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/"),
                "during" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/", "https://www.cuh.nhs.uk/patient-information/leg-amputation/"),
                "method" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/"),
                "preparation" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/"),
                "after" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/", "https://www.guysandstthomas.nhs.uk/leg-amputation/recovery-after-surgery"),
                "important" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/"),
                "questions" to listOf("https://www.nhs.uk/tests-and-treatments/amputation/")
            ),
            extraSections = listOf(
                ProcedureGuideSection("rehabilitation", "Rehabilitación y vida cotidiana",
                    "La rehabilitación se adapta a las metas y capacidades de la persona. Puede incluir aprender a pasar de la cama a una silla, utilizar apoyos y valorar modificaciones en casa. Una prótesis es un dispositivo que reemplaza parte de la extremidad; antes de decidir si es útil se valoran la herida, la movilidad y otros aspectos de salud. La persona puede preguntar por distintas formas de conservar su independencia.",
                    listOf("https://www.nhs.uk/tests-and-treatments/amputation/", "https://www.guysandstthomas.nhs.uk/leg-amputation/recovery-after-surgery")),
                ProcedureGuideSection("phantom", "¿Qué es la sensación fantasma?",
                    "Después de la amputación algunas personas notan sensaciones en la parte retirada, como si siguiera presente. Puede tratarse de una sensación sin dolor o de dolor fantasma. No significa que la persona lo imagine. Comunicarlo ayuda al equipo a valorar maneras de aliviarlo y a diferenciarlo del dolor de la herida.",
                    listOf("https://www.nhs.uk/tests-and-treatments/amputation/"))
            )
        ),
        entry(
            "manga-gastrica", "Cirugía metabólica tipo manga gástrica",
            listOf("manga gástrica", "gastrectomía en manga", "cirugía de manga gástrica"),
            listOf(ProcedureCategory.SURGERY), bariatric,
            "El estómago recibe los alimentos y participa en su digestión. En la manga gástrica, el cirujano retira una gran parte de este órgano y deja un tubo o manga más pequeño. Los alimentos siguen pasando hacia el intestino por su recorrido habitual. La parte retirada no puede volver a colocarse.",
            "Al quedar menos espacio en el estómago, la persona puede sentirse satisfecha con menos alimento. Es una de las operaciones que un equipo especializado puede valorar para algunas personas con obesidad. Tener diabetes por sí solo no significa necesitarla: la decisión depende de una valoración individual y de conversar alternativas, beneficios y riesgos.",
            "Antes de la intervención, un equipo que puede incluir cirugía, medicina interna, nutrición y salud mental revisa la historia de salud y explica el seguimiento. Durante la operación se utiliza anestesia general: la persona está dormida y el equipo de anestesia vigila sus funciones. Al despertar puede sentirse somnolienta o tener molestias en las heridas; el personal ayuda a controlarlas.",
            "Con frecuencia se realiza por laparoscopia: el cirujano utiliza instrumentos y una cámara a través de pequeñas aberturas en el abdomen. En algunas situaciones puede necesitarse una operación abierta, con una abertura mayor. El equipo explica cuál técnica propone y por qué.",
            "Después se vigilan la recuperación, las molestias y la capacidad de beber y comer según las indicaciones de la unidad. La alimentación cambia de manera progresiva y el equipo de nutrición explica cómo hacerlo en cada caso. Se requieren controles a largo plazo para revisar la nutrición y la evolución; la duración de la estancia y la recuperación varían.",
            "Como en otras cirugías pueden ocurrir sangrado, infección, coágulos o fuga en la línea donde se cerró el estómago. También puede haber reflujo o déficit de vitaminas y hierro. No todas las personas presentan estas complicaciones. Antes del alta, pregunta cuáles señales requieren atención urgente y a quién llamar; no ajustes tratamientos por tu cuenta.",
            listOf("¿Por qué se considera una manga y qué alternativas existen?", "¿Cómo cambia el tamaño del estómago y qué parte se retira?", "¿Qué preparación y revisión de medicamentos indicará el equipo?", "¿Cómo avanzará la alimentación y qué controles nutricionales necesitaremos?", "¿Qué síntomas después del alta requieren atención urgente y dónde debemos acudir?")
        ).copy(
            summary = "La manga gástrica reduce el tamaño del estómago mediante una operación; requiere valoración y seguimiento especializados.",
            contentVersion = "2.2-manga-gastrica",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("sleeve-what", "¿Qué cambia en el estómago?", "what"),
                ProcedureRelatedQuestion("sleeve-bypass", "¿En qué se diferencia del bypass gástrico?", "difference"),
                ProcedureRelatedQuestion("sleeve-recovery", "¿Qué ocurre después de la operación?", "after"),
            ),
            preparation = "El equipo dará instrucciones individuales sobre la preparación, la alimentación y los medicamentos. Lleva una lista de lo que utiliza la persona y confirma las indicaciones con la unidad; no suspendas ni cambies tratamientos por tu cuenta.",
            additionalSources = listOf(
                EducationSource("NHS · Cómo se realiza la cirugía para bajar de peso", "https://www.nhs.uk/tests-and-treatments/weight-loss-surgery/how-its-done/", "22 de noviembre de 2026"),
                EducationSource("NIDDK · Efectos secundarios de la cirugía bariátrica", "https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/side-effects", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = mapOf(
                "what" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types"),
                "purpose" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types"),
                "during" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types", "https://www.nhs.uk/tests-and-treatments/weight-loss-surgery/how-its-done/"),
                "method" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types"),
                "preparation" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types"),
                "after" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types"),
                "important" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/side-effects", "https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types"),
                "questions" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types")
            ),
            extraSections = listOf(
                ProcedureGuideSection("difference", "¿En qué se diferencia del bypass gástrico?",
                    "En la manga se retira una parte del estómago, pero no se modifica el trayecto del intestino. En el bypass se forma una bolsa pequeña y se conecta a una parte más alejada del intestino delgado, por lo que el alimento sigue otro camino. Son operaciones distintas, con riesgos y seguimiento diferentes; esta comparación no indica cuál necesita una persona.",
                    listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types")),
                ProcedureGuideSection("stomach-change", "¿Cómo cambia el estómago?",
                    "El cirujano retira una gran parte del estómago y cierra la parte restante, que queda como un tubo estrecho. Al poder recibir menos alimento, la sensación de saciedad puede aparecer antes. Retirar parte del estómago también puede cambiar señales del organismo relacionadas con el apetito; la respuesta no es idéntica en todas las personas. La porción retirada no puede volver a colocarse, por lo que es una decisión que debe conversarse con el equipo.",
                    listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types")),
                ProcedureGuideSection("food-followup", "¿Cómo puede cambiar la alimentación después?",
                    "La alimentación se reintroduce por etapas. Una secuencia habitual comienza con líquidos, pasa después a alimentos blandos y más adelante a sólidos; el equipo indica qué es adecuado y cuándo avanzar para cada persona. El seguimiento también puede incluir suplementos de vitaminas y minerales indicados por profesionales y controles para revisar la nutrición. Conviene preguntar quién dará estas indicaciones, cómo reconocer dificultades para comer o beber y con quién comunicarse si aparecen.",
                    listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types", "https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/side-effects"))
            )
        ),
        entry(
            "bypass-gastrico", "Bypass gástrico",
            listOf("derivación gástrica", "bypass gástrico", "cirugía de bypass gástrico"),
            listOf(ProcedureCategory.SURGERY), bariatric,
            "El bypass gástrico es una operación del aparato digestivo. El cirujano forma una bolsa pequeña en la parte superior del estómago y la conecta con una porción del intestino delgado. Así, los alimentos toman un recorrido distinto y pasan por menos estómago y una parte menor del intestino. Es diferente de un bypass coronario, que se realiza en vasos del corazón.",
            "La bolsa recibe menos alimento y el cambio de recorrido modifica la digestión y absorción. Esta operación puede valorarse para algunas personas con obesidad dentro de una atención especializada. No se indica automáticamente por tener diabetes; el equipo explica para cada persona los objetivos, alternativas, beneficios y riesgos.",
            "Antes, el equipo puede incluir especialistas en cirugía, medicina interna, nutrición y salud mental para revisar antecedentes y preparar el seguimiento. La operación se realiza con anestesia general, por lo que la persona permanece dormida mientras anestesia vigila sus funciones. Al despertar puede tener sueño o molestias, que debe comunicar al personal.",
            "A menudo se utiliza laparoscopia, mediante pequeñas aberturas por las que se introducen una cámara e instrumentos. En ciertas circunstancias se elige cirugía abierta. Durante la intervención el cirujano forma la pequeña bolsa y la une al intestino; el equipo explicará la técnica prevista.",
            "Tras la operación el personal vigila la recuperación y explica cuándo y cómo se reinician los líquidos y alimentos. Después se necesitan consultas de seguimiento y vigilancia nutricional prolongada. La absorción de algunos nutrientes puede disminuir, por lo que el equipo puede indicar análisis y suplementos; las indicaciones se adaptan a cada persona.",
            "Entre los riesgos están sangrado, infección, coágulos y fuga en las uniones del aparato digestivo. A largo plazo pueden aparecer deficiencias de vitaminas o hierro. El bypass es difícil de revertir. Pregunta antes del alta qué síntomas requieren valoración urgente, a dónde acudir y qué controles necesitará la persona.",
            listOf("¿Qué cambia en el recorrido del alimento?", "¿Por qué se considera este procedimiento frente a la manga u otras alternativas?", "¿Qué revisión de medicamentos y preparación se necesitan antes?", "¿Cómo será la alimentación después y quién nos orientará?", "¿Qué análisis, suplementos y señales de alarma debemos conversar con el equipo?")
        ).copy(
            summary = "El bypass gástrico crea una bolsa pequeña y modifica el trayecto del alimento hacia el intestino.",
            contentVersion = "2.2-bypass-gastrico",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("gastric-route", "¿Cómo cambia el recorrido de los alimentos?", "what"),
                ProcedureRelatedQuestion("gastric-sleeve", "¿En qué se diferencia de una manga gástrica?", "difference"),
                ProcedureRelatedQuestion("gastric-followup", "¿Qué seguimiento nutricional puede necesitarse?", "after"),
            ),
            preparation = "La unidad debe indicar la preparación individual y revisar los medicamentos y antecedentes. Lleva una lista de tratamientos y pregunta qué hacer con cada uno; no los modifiques por cuenta propia.",
            additionalSources = listOf(
                EducationSource("NHS · Cómo se realiza la cirugía para bajar de peso", "https://www.nhs.uk/tests-and-treatments/weight-loss-surgery/how-its-done/", "22 de noviembre de 2026"),
                EducationSource("NIDDK · Efectos secundarios de la cirugía bariátrica", "https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/side-effects", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = mapOf(
                "what" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types"),
                "purpose" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types"),
                "during" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types", "https://www.nhs.uk/tests-and-treatments/weight-loss-surgery/how-its-done/"),
                "method" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types"),
                "preparation" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types"),
                "after" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types", "https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/side-effects"),
                "important" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types", "https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/side-effects"),
                "questions" to listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types")
            ),
            extraSections = listOf(
                ProcedureGuideSection("difference", "Bypass gástrico y manga: ¿son iguales?",
                    "No. En el bypass se crea una bolsa y se cambia la conexión con el intestino delgado. En la manga se retira parte del estómago, sin cambiar el recorrido intestinal. El NIDDK señala diferencias en los riesgos de deficiencias de vitaminas y hierro. La comparación ayuda a formular preguntas, pero no sustituye la valoración del equipo.",
                    listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types")),
                ProcedureGuideSection("terms", "Palabras que puedes escuchar",
                    "Intestino delgado: parte del aparato digestivo que recibe el alimento después del estómago y absorbe nutrientes. Laparoscopia: técnica que usa una cámara e instrumentos a través de pequeñas aberturas. Fuga: salida de contenido por una zona de unión o cierre que no ha sellado correctamente; es una posible complicación que necesita valoración.",
                    listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types", "https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/side-effects")),
                ProcedureGuideSection("digestive-route", "¿Qué recorrido siguen los alimentos?",
                    "Tras el bypass, los alimentos llegan primero a una pequeña bolsa formada con la parte superior del estómago. De allí pasan a una parte más alejada del intestino delgado, evitando el trayecto por gran parte del estómago y el primer tramo intestinal. La parte desviada del aparato digestivo permanece conectada para que los jugos digestivos se reúnan con los alimentos más adelante. Este cambio influye en cuánto alimento cabe y en la absorción; no es la misma operación que la manga gástrica.",
                    listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types")),
                ProcedureGuideSection("long-term-followup", "¿Por qué continúa el seguimiento nutricional?",
                    "El nuevo trayecto puede reducir la absorción de algunos nutrientes. Con el tiempo, una falta de vitaminas o minerales puede afectar la salud; por ejemplo, contribuir a anemia o problemas de los huesos. El equipo revisa la alimentación y puede indicar análisis y suplementos apropiados para la persona. Pregunta cómo serán los controles, quién los coordina y qué hacer si aparecen dificultades para comer o tomar los suplementos; no los ajustes por cuenta propia.",
                    listOf("https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/side-effects", "https://www.niddk.nih.gov/health-information/weight-management/bariatric-surgery/types"))
            )
        ),
        entry(
            "curacion-ulcera-pie", "Curación de una úlcera del pie",
            listOf("curación de úlcera del pie", "curación de herida del pie"),
            listOf(ProcedureCategory.FEET), ulcers,
            "Una úlcera es una herida abierta en la piel. La curación del pie comprende la valoración y los cuidados que el personal sanitario adapta a esa herida. En personas con diabetes una menor sensibilidad puede hacer que se note menos una lesión; una circulación reducida también puede dificultar que sane. Esto no ocurre de la misma manera en todas las personas.",
            "La atención busca proteger la herida y favorecer su cicatrización. El equipo examina sus características y puede considerar la circulación, el apoyo sobre el pie y señales de infección. Una úlcera puede tardar semanas o meses en sanar, por lo que varias revisiones permiten observar su evolución.",
            "En la visita el profesional revisa la herida y la piel alrededor. Puede retirar un apósito anterior, valorar si necesita limpieza o retirada profesional de tejido y elegir una nueva cubierta. Según lo observado también puede solicitar pruebas de circulación o muestras si sospecha infección. La persona puede explicar si la posición o el contacto le causan molestias.",
            "El apósito es el material que cubre la herida; existen distintos tipos y no son intercambiables para cualquier lesión. El equipo puede combinar las curaciones con un dispositivo para reducir la presión al caminar. Las instrucciones sobre apósitos y desplazamiento se dan para la herida y las capacidades de cada persona.",
            "Antes de salir conviene conocer quién revisará la herida, cuándo será la próxima visita y qué cuidados concretos se indicaron para casa. Si el apósito se mueve o hay dudas sobre él, consulta a la unidad. No hay una fecha universal de recuperación y el cuidado puede cambiar conforme evoluciona la herida.",
            "Si aumentan la hinchazón, el enrojecimiento, el dolor o el líquido de la herida, o aparecen fiebre o malestar, busca valoración médica sin demora. No toda úlcera está infectada ni necesita antibióticos; el profesional determina si hay infección. No intentes retirar tejido ni cambiar tratamientos por cuenta propia.",
            listOf("¿Qué parte del pie está afectada y cómo observaremos si sana?", "¿Qué tipo de apósito se usará y quién lo cambiará?", "¿Necesita el pie un apoyo que reduzca la presión?", "¿Qué cambios requieren avisar de inmediato y a quién?", "¿Cuándo se revisará nuevamente la herida?")
        ).copy(
            summary = "La curación es la atención de una herida abierta del pie con cuidados ajustados a su evolución.",
            contentVersion = "2.2-curacion-ulcera",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("ulcer-care", "¿Qué sucede durante una curación?", "during"),
                ProcedureRelatedQuestion("ulcer-difference", "¿Qué diferencia hay entre curación y desbridamiento?", "difference"),
                ProcedureRelatedQuestion("ulcer-urgent", "¿Qué cambios necesitan valoración sin demora?", "important"),
            ),
            preparation = "Lleva a la cita las indicaciones recibidas y comenta si cambió la herida o el apósito. La unidad indicará si se necesita algún preparativo específico.",
            additionalSources = listOf(
                EducationSource("NIDDK · Diabetes y problemas de los pies", "https://www.niddk.nih.gov/health-information/diabetes/overview/preventing-problems/foot-problems", "22 de noviembre de 2026"),
                EducationSource("IWGDF/IDSA · Infecciones del pie relacionadas con diabetes", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/", "22 de noviembre de 2026"),
                EducationSource("Kent Community Health NHS · Apósitos para heridas y úlceras", "https://www.kentcht.nhs.uk/leaflet/wound-or-ulcer-dressings/", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = mapOf(
                "what" to listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble", "https://www.niddk.nih.gov/health-information/diabetes/overview/preventing-problems/foot-problems"),
                "purpose" to listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble"),
                "during" to listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble"),
                "method" to listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble"),
                "preparation" to listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble"),
                "after" to listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble"),
                "important" to listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble", "https://www.idsociety.org/practice-guideline/diabetic-foot-infections/"),
                "questions" to listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble")
            ),
            extraSections = listOf(
                ProcedureGuideSection("difference", "Curación, desbridamiento y descarga",
                    "La curación es el conjunto de cuidados de la herida y su cubierta. El desbridamiento es la retirada profesional de tejido que dificulta su atención cuando resulta apropiado. La descarga busca reducir la presión sobre la lesión mediante un dispositivo. Pueden formar parte de un mismo plan, pero no significan lo mismo ni son necesarios de manera idéntica para toda herida.",
                    listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble")),
                ProcedureGuideSection("dressing-purpose", "¿Para qué sirve el apósito?",
                    "El apósito es una cubierta que ayuda a proteger la herida mientras cicatriza. Según el material elegido, puede absorber sangre u otros líquidos y ayudar a proteger la zona de nuevos daños. Hay varios tipos, y el equipo elige uno según las características de la lesión. Antes de salir, pregunta quién hará los cambios y qué hacer si se moja, se ensucia o se desplaza: las instrucciones pueden ser diferentes para cada persona.",
                    listOf("https://www.kentcht.nhs.uk/leaflet/wound-or-ulcer-dressings/")),
                ProcedureGuideSection("sensation-circulation", "Sensibilidad, circulación y curación",
                    "En algunas personas con diabetes, el pie siente menos el roce o una lesión. Una herida puede pasar inadvertida o seguir recibiendo presión al caminar; no sentir dolor no demuestra que esté sanando. La circulación aporta sangre a los tejidos y también influye en su capacidad de sanar. Por eso el equipo puede revisar la sensibilidad y el flujo sanguíneo, además de observar la herida y el apoyo del pie.",
                    listOf("https://www.niddk.nih.gov/health-information/diabetes/overview/preventing-problems/foot-problems", "https://www.kentcht.nhs.uk/leaflet/wound-or-ulcer-dressings/")),
                ProcedureGuideSection("infection", "¿Toda úlcera necesita antibióticos?",
                    "No. Una herida abierta puede contener microorganismos sin que por ello haya una infección clínica. El equipo examina la lesión y sus cambios para valorar si existe infección y qué atención corresponde. Los antibióticos no sustituyen por sí solos la protección de la herida ni la reducción de presión cuando se indican. No inicies ni suspendas un tratamiento por cuenta propia; consulta sin demora si aumentan el dolor, el enrojecimiento, la hinchazón o el líquido, o si aparece fiebre o malestar.",
                    listOf("https://www.idsociety.org/practice-guideline/diabetic-foot-infections/", "https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble"))
            )
        ),
        entry(
            "desbridamiento-herida", "Desbridamiento de una herida",
            listOf("desbridamiento de herida", "desbridamiento de úlcera", "retirar tejido de una herida"),
            listOf(ProcedureCategory.FEET, ProcedureCategory.SURGERY), ulcers,
            "Desbridamiento significa retirar tejido muerto, dañado o infectado, o piel endurecida, de una herida cuando el profesional considera que es apropiado. No es simplemente cambiar el apósito: es una intervención sobre el tejido que rodea o cubre la lesión.",
            "Retirar tejido que dificulta valorar la herida puede ayudar a que el profesional observe mejor su tamaño y atienda la lesión. Esta intervención es una parte posible del plan de curación; puede combinarse con apósitos y medidas para reducir presión. No toda herida necesita el mismo método ni requiere desbridamiento.",
            "Antes de comenzar, el profesional explica por qué lo propone, qué método utilizará y qué molestias podrían aparecer. Durante la atención retira el tejido que considera necesario; luego protege la zona con el apósito adecuado. Si duele o la posición resulta incómoda, es importante comunicarlo: el equipo puede valorar cómo aliviar la molestia.",
            "Existen diferentes métodos profesionales. Algunos consisten en retirar tejido con instrumentos y otros utilizan apósitos especiales para favorecer su eliminación. La elección depende de la herida y de la valoración del equipo. Esta información describe la atención: no enseña a realizar el procedimiento en casa.",
            "Tras retirar tejido, la herida puede verse más amplia o sangrar un poco porque ahora se aprecia una zona que antes estaba cubierta. El profesional explica cómo se protegerá y cuándo necesita una nueva revisión. La apariencia por sí sola no permite saber si está sanando o si hay infección.",
            "No intentes cortar, raspar ni retirar tejido de una herida en casa. Si aumentan el enrojecimiento, la hinchazón, el dolor o el líquido, o aparece fiebre o malestar, busca atención médica sin demora. Consulta al equipo si el apósito presenta problemas o si no comprendes las indicaciones.",
            listOf("¿Qué tejido se necesita retirar y para qué?", "¿Qué método se propone y qué molestias puede sentir?", "¿Cómo se aliviará el dolor si aparece?", "¿Cómo puede verse la herida después y quién cambiará el apósito?", "¿Qué cambios requieren atención urgente?")
        ).copy(
            summary = "El desbridamiento es la retirada profesional de tejido que puede dificultar la atención de una herida.",
            contentVersion = "2.2-desbridamiento",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("wound-debridement", "¿Qué es el desbridamiento?", "what"),
                ProcedureRelatedQuestion("wound-appearance", "¿Por qué puede parecer más grande la herida?", "expectation"),
                ProcedureRelatedQuestion("wound-warning", "¿Qué cambios requieren atención sin demora?", "important"),
            ),
            preparation = "La unidad explicará cómo preparar la visita y revisará los medicamentos y antecedentes que sean pertinentes. Pregunta cómo se controlará cualquier molestia y no cambies tratamientos por tu cuenta.",
            additionalSources = listOf(
                EducationSource("Kent Community Health NHS · Apósitos para heridas y úlceras", "https://www.kentcht.nhs.uk/leaflet/wound-or-ulcer-dressings/", "22 de noviembre de 2026"),
                EducationSource("NIDDK · Diabetes y problemas de los pies", "https://www.niddk.nih.gov/health-information/diabetes/overview/preventing-problems/foot-problems", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble")
            },
            extraSections = listOf(
                ProcedureGuideSection("expectation", "¿Por qué puede parecer más grande la herida?",
                    "A veces la piel endurecida o el tejido dañado cubren parte de la lesión. Cuando el profesional los retira puede verse un área mayor que antes. El equipo revisará su evolución y explicará qué aspecto es esperable para esa herida y qué cambios deben comunicarse.",
                    listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble")),
                ProcedureGuideSection("assessment", "¿Qué se valora antes de retirar tejido?",
                    "La piel endurecida puede ocultar parte de una úlcera y el tejido sin vida puede dificultar su cicatrización. El equipo observa la herida y la piel que la rodea; también puede revisar la sensibilidad y la circulación del pie, porque la sangre ayuda a que los tejidos sanen. La elección del método depende de esa valoración: retirar tejido con instrumentos no es apropiado en todas las situaciones y, en algunos casos, se considera un apósito especial. El cuidador puede preguntar qué se observó y por qué se propone ese método.",
                    listOf("https://www.kentcht.nhs.uk/leaflet/wound-or-ulcer-dressings/", "https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble")),
                ProcedureGuideSection("comfort", "Sensibilidad, molestias y seguimiento",
                    "Algunas personas con diabetes sienten menos una lesión del pie por cambios en los nervios; otras pueden notar dolor o incomodidad durante la atención. La falta de dolor no demuestra que una herida esté sanando. Conviene comunicar cualquier molestia y preguntar cómo se protegerá la zona y quién la revisará de nuevo. El apósito ayuda a cubrir la herida, pero el plan y el momento de cambiarlo dependen de las indicaciones recibidas.",
                    listOf("https://www.niddk.nih.gov/health-information/diabetes/overview/preventing-problems/foot-problems", "https://www.kentcht.nhs.uk/leaflet/wound-or-ulcer-dressings/"))
            )
        ),
        entry(
            "descarga-presion-pie", "Descarga de presión de una lesión del pie",
            listOf("descarga de presión del pie", "dispositivo de descarga del pie"),
            listOf(ProcedureCategory.FEET), ulcers,
            "Descargar presión significa reducir la fuerza o el roce que recibe una zona del pie, especialmente al apoyar o caminar. Se puede utilizar un dispositivo elegido por el equipo. No significa drenar líquido ni retirar tejido: son conceptos diferentes.",
            "La presión repetida del peso o del calzado sobre una herida puede dificultar su cicatrización. Un dispositivo puede repartir el apoyo de otro modo y proteger la zona lesionada mientras el equipo continúa la atención de la herida.",
            "El profesional examina el pie y pregunta cómo se desplaza la persona. Puede valorar una plantilla, calzado adaptado, sandalia o bota especial, entre otras opciones. Explicará cómo se usa el dispositivo elegido y revisará su ajuste; no todos son adecuados para todas las lesiones ni para todas las personas.",
            "El dispositivo se integra al plan de atención junto con las curaciones y revisiones indicadas. Puede requerir cambios o ajustes conforme sana la herida. La selección toma en cuenta la localización de la lesión y la situación de movilidad, por lo que no conviene improvisar soportes o inmovilizaciones.",
            "El equipo revisa tanto la lesión como el ajuste del dispositivo. Pregunta cuándo acudir, cómo usarlo en las actividades diarias y qué hacer si roza, incomoda o dificulta desplazarse. Si no es posible usarlo como se explicó, comunicarlo permite buscar una solución con el profesional.",
            "Si aparece una nueva zona de roce o aumentan hinchazón, enrojecimiento, dolor o líquido de la herida, busca valoración sin demora. No supongas que un dispositivo reemplaza las curaciones o el seguimiento. El cuidador puede ayudar a registrar problemas de ajuste para comentarlos en la revisión.",
            listOf("¿Qué zona necesita reducir la presión y por qué?", "¿Cómo se coloca y cuándo debe usarse el dispositivo indicado?", "¿Qué hacemos si roza o dificulta caminar?", "¿Cuándo revisarán el ajuste y la herida?", "¿Qué cambios requieren llamar a la unidad de inmediato?")
        ).copy(
            summary = "La descarga reduce la presión sobre una lesión del pie mediante un dispositivo indicado por el equipo.",
            contentVersion = "2.2-descarga-pie",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("offloading-purpose", "¿Para qué sirve reducir la presión?", "purpose"),
                ProcedureRelatedQuestion("offloading-device", "¿Qué dispositivos puede valorar el equipo?", "during"),
                ProcedureRelatedQuestion("offloading-infection", "¿La descarga trata una infección?", "difference"),
            ),
            preparation = "Comenta al equipo cómo se desplaza la persona, qué calzado usa y si necesita ayuda para caminar. La unidad indicará cómo preparar la valoración.",
            additionalSources = listOf(
                EducationSource("Kent Community Health NHS · Apósitos para heridas y úlceras", "https://www.kentcht.nhs.uk/leaflet/wound-or-ulcer-dressings/", "22 de noviembre de 2026"),
                EducationSource("Cambridge University Hospitals NHS · Yeso de contacto total", "https://www.cuh.nhs.uk/patient-information/total-contact-casting-information-and-advice/", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble")
            },
            extraSections = listOf(
                ProcedureGuideSection("difference", "Presión no es infección",
                    "El dispositivo busca disminuir la presión y el roce sobre la herida; por sí solo no dice si hay una infección ni la trata. El equipo puede indicar otras atenciones tras examinar la lesión. Pregunta cómo se combinan el dispositivo y las curaciones en el plan recibido.",
                    listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble")),
                ProcedureGuideSection("pressure", "¿Qué ocurre al apoyar el pie?",
                    "Al caminar, el peso se distribuye sobre distintas zonas del pie. Si una herida recibe presión o roce repetidos, puede seguir lesionándose y tardar más en sanar; la pérdida de sensibilidad puede hacer que la persona no note ese daño. La descarga intenta repartir o reducir el apoyo sobre la zona afectada. No equivale a un apósito: este cubre y protege la herida, mientras el dispositivo actúa sobre la presión.",
                    listOf("https://www.kentcht.nhs.uk/leaflet/wound-or-ulcer-dressings/", "https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble")),
                ProcedureGuideSection("devices", "¿Cómo puede ser un dispositivo?",
                    "Según la lesión y la valoración profesional, puede tratarse de una plantilla, calzado o sandalia especial, una bota o un yeso. Algunos se retiran y otros requieren atención del equipo para modificarlos o retirarlos; no todos se usan de la misma manera. Una bota o un yeso pueden cambiar la forma de caminar y necesitar comprobaciones de ajuste. Comenta si la persona necesita ayuda para desplazarse, si el dispositivo roza o si resulta difícil seguir las instrucciones: el equipo puede revisar el plan y explicar cuándo volver.",
                    listOf("https://www.lscft.nhs.uk/patient-information-leaflets/foot-ulcers-chorley-and-south-ribble", "https://www.cuh.nhs.uk/patient-information/total-contact-casting-information-and-advice/"))
            )
        ),
        entry(
            "indice-tobillo-brazo", "Índice tobillo-brazo",
            listOf("índice tobillo brazo", "prueba de circulación tobillo brazo"),
            listOf(ProcedureCategory.FEET, ProcedureCategory.HEART), vascular,
            "El índice tobillo-brazo es una comparación entre la presión arterial medida en los tobillos y en los brazos. La presión arterial es la fuerza que ejerce la sangre al circular. El resultado ayuda al equipo a estudiar la circulación en las arterias de las piernas, los vasos que llevan sangre desde el corazón.",
            "La comparación puede aportar información cuando se investigan problemas de circulación, por ejemplo ante ciertas molestias o heridas de las piernas. Una presión del tobillo menor en relación con la del brazo puede ser una señal que el profesional necesita valorar. Que se solicite el estudio no demuestra por sí solo una obstrucción ni indica que se necesite una operación.",
            "Habitualmente la persona se recuesta en una camilla. El profesional coloca manguitos, como los usados para medir la presión, alrededor de los brazos y los tobillos. Puede aplicar un poco de gel y apoyar una pequeña sonda Doppler sobre la piel para escuchar el flujo. Al inflarse, los manguitos aprietan unos momentos; si esto causa dolor o resulta difícil permanecer acostado, conviene avisar.",
            "El aparato Doppler utiliza ultrasonido para detectar señales del paso de sangre; el equipo registra las presiones y calcula una relación entre tobillo y brazo. No se introduce una aguja, no hay incisión y este examen no usa rayos X. A veces se combina con una prueba de caminata si el equipo la solicitó.",
            "Al terminar se retiran los manguitos y se limpia el gel. El resultado se comunica al profesional que pidió la prueba y puede explicarse durante la visita o después, según la unidad. Pregunta cómo y cuándo se revisará junto con los antecedentes, síntomas y otras exploraciones.",
            "El resultado necesita interpretación profesional. En algunas situaciones los vasos pueden no comprimirse como se espera y la medición tiene limitaciones; el equipo puede considerar otras pruebas si necesita más información. Bitavida no interpreta cifras ni determina un tratamiento.",
            listOf("¿Qué comparación se hará entre tobillos y brazos?", "¿Qué puede sentir al inflarse los manguitos?", "¿Se requiere también una prueba al caminar?", "¿Hay factores que puedan dificultar la medición?", "¿Cuándo y con quién revisaremos el resultado?"),
            context = "Según indicación médica"
        ).copy(
            summary = "El índice compara presiones del tobillo y del brazo para aportar información sobre la circulación de las piernas.",
            contentVersion = "2.1-indice-tobillo-brazo",
            reviewedOn = "22 de noviembre de 2026",
            preparation = "La unidad explicará cómo acudir. La ropa que permita descubrir brazos y tobillos facilita la medición; comenta antes de la cita si existen apósitos, dolor o dificultades para acostarse. No cambies medicamentos por cuenta propia.",
            additionalSources = listOf(
                EducationSource("Cambridge University Hospitals · Índice de presión tobillo-brazo", "https://www.cuh.nhs.uk/patient-information/ankle-brachial-pressure-indices-test/", "22 de noviembre de 2026"),
                EducationSource("American Heart Association · Diagnóstico de enfermedad arterial periférica", "https://www.heart.org/en/health-topics/peripheral-artery-disease/diagnosing-pad", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith {
                listOf("https://www.nhlbi.nih.gov/health/peripheral-artery-disease/diagnosis", "https://www.cuh.nhs.uk/patient-information/ankle-brachial-pressure-indices-test/", "https://www.heart.org/en/health-topics/peripheral-artery-disease/diagnosing-pad")
            },
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("abi-measurement", "¿Qué presiones compara la prueba?", "what"),
                ProcedureRelatedQuestion("abi-experience", "¿Qué se siente durante la medición?", "during"),
                ProcedureRelatedQuestion("abi-results", "¿Cuándo se explicará el resultado?", "after"),
            ),
            extraSections = listOf(
                ProcedureGuideSection("doppler", "Índice tobillo-brazo y Doppler arterial",
                    "En el índice se comparan presiones tomadas con manguitos; puede usarse una sonda Doppler para escuchar el flujo mientras se miden. Una ecografía Doppler arterial también puede producir imágenes y mediciones de los vasos de la pierna. Aunque comparten tecnología, no son exactamente la misma evaluación.",
                    listOf("https://www.nhlbi.nih.gov/health/peripheral-artery-disease/diagnosis", "https://www.cuh.nhs.uk/patient-information/ankle-brachial-pressure-indices-test/")),
                ProcedureGuideSection("measurement-experience", "¿Qué se escucha y se siente?",
                    "La persona suele acostarse mientras el profesional mide la presión en brazos y tobillos con manguitos que se inflan y aprietan por momentos. La pequeña sonda Doppler se apoya con gel sobre la piel y ayuda a escuchar el paso de la sangre; el aparato puede producir un sonido semejante a un soplo. La exploración no requiere agujas ni cortes. Si el manguito duele, hay una herida sensible o acostarse es difícil, avisa para que el personal valore cómo continuar.",
                    listOf("https://www.cuh.nhs.uk/patient-information/ankle-brachial-pressure-indices-test/")),
                ProcedureGuideSection("walking-test", "¿Por qué a veces piden caminar?",
                    "En algunas personas el equipo necesita estudiar cómo cambia la medición después de caminar, además del registro en reposo. Puede pedir una caminata supervisada en una banda y volver a tomar las presiones; no forma parte obligatoria de todas las pruebas. Si existen dificultades para respirar, problemas de equilibrio o limitaciones para caminar, conviene comunicarlos antes para que el equipo valore la prueba y cualquier adaptación.",
                    listOf("https://www.nhlbi.nih.gov/health/peripheral-artery-disease/diagnosis", "https://www.cuh.nhs.uk/patient-information/ankle-brachial-pressure-indices-test/")),
                ProcedureGuideSection("result-limits", "¿Qué aporta y qué no determina el resultado?",
                    "El índice compara presiones para ayudar a estudiar la circulación arterial de la pierna. El profesional considera también los síntomas, los pulsos, la exploración y los antecedentes. Algunas arterias no se comprimen como se espera y pueden dificultar la interpretación; en ciertas situaciones se mide la presión de un dedo del pie o se consideran otras pruebas. Una cifra por sí sola no explica toda la situación ni permite decidir una cirugía.",
                    listOf("https://www.heart.org/en/health-topics/peripheral-artery-disease/diagnosing-pad", "https://www.nhlbi.nih.gov/health/peripheral-artery-disease/diagnosis"))
            )
        ),
        entry(
            "doppler-arterial-pierna", "Ultrasonido Doppler arterial de las piernas",
            listOf("doppler arterial", "ultrasonido doppler de piernas", "doppler de circulación arterial"),
            listOf(ProcedureCategory.FEET, ProcedureCategory.HEART),
            EducationSource("NHLBI · Diagnóstico de enfermedad arterial periférica", "https://www.nhlbi.nih.gov/health/peripheral-artery-disease/diagnosis", "22 de noviembre de 2026"),
            "Esta exploración usa ultrasonido para producir imágenes y mediciones del paso de sangre por las arterias de las piernas. Las arterias llevan sangre del corazón a los tejidos. El equipo mueve un dispositivo sobre la piel: no se trata de una cirugía.",
            "Las imágenes pueden ayudar a localizar zonas donde el flujo es menor o está bloqueado y a estudiar la velocidad de la sangre. También pueden servir para observar cómo ha evolucionado una atención previa. La solicitud no demuestra una obstrucción ni implica que hará falta una operación.",
            "Habitualmente la persona se recuesta mientras el profesional coloca gel y mueve lentamente una pequeña sonda sobre la piel. Se necesita acceso a la pierna y a veces a la zona cercana a la ingle. La exploración suele tolerarse bien; comenta si la postura o el contacto sobre una zona sensible resultan molestos.",
            "El aparato convierte ecos de ondas sonoras en información e imágenes del flujo arterial. Es una prueba diferente del índice tobillo-brazo, que compara mediciones de presión. Algunas valoraciones de circulación combinan estas técnicas según la pregunta clínica.",
            "Al terminar se limpia el gel. Las imágenes y mediciones pueden requerir revisión antes de emitir un informe. Pregunta cómo y cuándo lo recibirá el profesional que solicitó la prueba; el tiempo de entrega depende de la unidad.",
            "El personal puede necesitar descubrir una parte de la pierna para explorarla; si preocupa la intimidad o hay dificultades de movilidad, conviene comentarlo antes. El resultado se integra con la valoración clínica y no determina por sí solo una intervención.",
            listOf("¿Qué arterias de la pierna se van a observar?", "¿También medirán la presión en el tobillo?", "¿Cómo se complementa con otras pruebas de circulación?", "¿Cuándo conoceremos el informe?", "¿A quién avisamos si la posición o la presión resultan dolorosas?"),
            context = "Según indicación médica"
        ).copy(
            summary = "El Doppler usa ondas sonoras para estudiar cómo circula la sangre por las arterias de las piernas.",
            contentVersion = "2.3-doppler-arterial",
            reviewedOn = "22 de noviembre de 2026",
            relatedQuestions = listOf(
                ProcedureRelatedQuestion("arterial-doppler-experience", "¿Qué sucede durante el Doppler arterial?", "experience"),
                ProcedureRelatedQuestion("arterial-doppler-comparison", "¿En qué se diferencia del índice tobillo-brazo?", "comparison"),
                ProcedureRelatedQuestion("arterial-doppler-result", "¿Qué sucede al terminar la exploración?", "after"),
            ),
            preparation = "La zona que se examinará debe quedar accesible. Pregunta a la unidad qué ropa facilita el estudio y si esta orden necesita alguna preparación particular.",
            additionalSources = listOf(
                EducationSource("University Hospitals Dorset · Ecografía dúplex arterial de piernas", "https://www.uhd.nhs.uk/uploads/about/docs/our_publications/patient_information_leaflets/radiology/Arterial-leg-duplex-Doppler-ultrasound-scan.pdf", "22 de noviembre de 2026"),
                EducationSource("Cambridge University Hospitals · Ecografía de arterias de las piernas", "https://www.cuh.nhs.uk/patient-information/ultrasound-scan-of-your-leg-arteries/", "22 de noviembre de 2026"),
                EducationSource("Cambridge University Hospitals · Ecografía de venas de las piernas", "https://www.cuh.nhs.uk/patient-information/ultrasound-scan-of-your-leg-veins/", "22 de noviembre de 2026")
            ),
            sectionSourceReferences = listOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions").associateWith { listOf("https://www.nhlbi.nih.gov/health/peripheral-artery-disease/diagnosis", "https://www.uhd.nhs.uk/uploads/about/docs/our_publications/patient_information_leaflets/radiology/Arterial-leg-duplex-Doppler-ultrasound-scan.pdf") },
            extraSections = listOf(
                ProcedureGuideSection("experience", "Durante la exploración",
                    "Un profesional desplaza el transductor desde diferentes puntos de la pierna mientras observa la pantalla. El gel puede estar templado o sentirse fresco según la unidad. El folleto del hospital británico describe presión ligera y una duración aproximada de media hora por pierna; estos tiempos no garantizan la duración de una cita en otra institución. Puedes preguntar con antelación cómo facilitar la posición y proteger la intimidad.",
                    listOf("https://www.uhd.nhs.uk/uploads/about/docs/our_publications/patient_information_leaflets/radiology/Arterial-leg-duplex-Doppler-ultrasound-scan.pdf")),
                ProcedureGuideSection("comparison", "Doppler e índice tobillo-brazo",
                    "El Doppler aporta imágenes y mediciones del flujo en vasos examinados. El índice tobillo-brazo compara las presiones medidas en el tobillo y el brazo para ayudar a estudiar la circulación. Son herramientas relacionadas pero no idénticas; el equipo explica si una o ambas ayudan a responder la pregunta de esa cita.",
                    listOf("https://www.nhlbi.nih.gov/health/peripheral-artery-disease/diagnosis")),
                ProcedureGuideSection("artery-vein", "¿Es igual revisar arterias y venas?",
                    "Las arterias llevan la sangre desde el corazón hacia las piernas y los pies. Las venas ayudan a devolverla hacia el corazón. Un Doppler arterial estudia las arterias; un estudio venoso se centra en las venas y puede responder otras preguntas. Ambos usan ultrasonido, pero no son automáticamente intercambiables. Conviene confirmar qué vasos pidió revisar el equipo y por qué.",
                    listOf("https://www.cuh.nhs.uk/patient-information/ultrasound-scan-of-your-leg-arteries/", "https://www.cuh.nhs.uk/patient-information/ultrasound-scan-of-your-leg-veins/")),
                ProcedureGuideSection("flow-information", "¿Qué información obtiene el equipo?",
                    "El profesional recorre distintos puntos con la sonda para observar las arterias y medir características del paso de la sangre, incluida su velocidad. Las imágenes pueden mostrar zonas donde el flujo está reducido o interrumpido; también pueden usarse para vigilar una atención previa. El hallazgo se considera con los síntomas y otras exploraciones: la imagen por sí sola no permite al cuidador decidir si se necesita una intervención.",
                    listOf("https://www.nhlbi.nih.gov/health/peripheral-artery-disease/diagnosis", "https://www.cuh.nhs.uk/patient-information/ultrasound-scan-of-your-leg-arteries/")),
            ),
        )
    )
}
