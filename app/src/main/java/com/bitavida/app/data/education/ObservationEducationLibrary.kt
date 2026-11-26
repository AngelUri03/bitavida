package com.bitavida.app.data.education

import com.bitavida.app.domain.education.EducationEntry
import com.bitavida.app.domain.education.EducationSource
import com.bitavida.app.domain.education.EducationTopic

/**
 * Verified, offline educational drafts for the fixed Observation catalogue.
 *
 * These texts explain why a fact can be useful to document. They never infer a diagnosis,
 * assess a person or turn absence of a record into absence of symptoms. Every entry remains
 * pending human clinical review before publication.
 */
object ObservationEducationLibrary {
    private val niddkUrineTests2026 = EducationSource(
        "NIDDK · Diagnóstico de infección de la vejiga en adultos",
        "https://www.niddk.nih.gov/health-information/urologic-diseases/bladder-infection-uti-in-adults/diagnosis",
        consultedOn = "22 de noviembre de 2026",
    )
    private val niamsSkin2026 = EducationSource(
        "NIAMS · Dermatitis atópica: síntomas y causas",
        "https://www.niams.nih.gov/health-topics/atopic-dermatitis",
        consultedOn = "22 de noviembre de 2026",
    )
    private val niddkBowelDiagnosis2026 = EducationSource(
        "NIDDK · Diagnóstico de diarrea",
        "https://www.niddk.nih.gov/health-information/digestive-diseases/diarrhea/diagnosis",
        consultedOn = "22 de noviembre de 2026",
    )
    private val nidcdBalanceTests2026 = EducationSource(
        "NIDCD · Trastornos del equilibrio",
        "https://www.nidcd.nih.gov/health/balance-disorders",
        consultedOn = "22 de noviembre de 2026",
    )
    private val niaSleep2026 = EducationSource(
        "National Institute on Aging · El sueño y los adultos mayores",
        "https://www.nia.nih.gov/health/sleep/sleep-and-older-adults",
        consultedOn = "22 de noviembre de 2026",
    )

    private val niddkBladderSymptoms2026 = EducationSource(
        "NIDDK · Síntomas de infección de la vejiga",
        "https://www.niddk.nih.gov/health-information/urologic-diseases/bladder-infection-uti-in-adults/symptoms-causes",
        consultedOn = "22 de noviembre de 2026",
    )
    private val nhsUrethritis2026 = EducationSource(
        "NHS · Inflamación de la uretra",
        "https://www.nhs.uk/conditions/urethritis/",
        consultedOn = "22 de noviembre de 2026",
    )
    private val nhsHives2026 = EducationSource(
        "NHS · Ronchas o urticaria",
        "https://www.nhs.uk/conditions/hives/",
        consultedOn = "22 de noviembre de 2026",
    )
    private val nhsSevereRash2026 = EducationSource(
        "NHS · Reacciones graves con erupción y ampollas",
        "https://www.nhs.uk/conditions/Stevens-Johnson-syndrome/",
        consultedOn = "22 de noviembre de 2026",
    )
    private val nhsEmergency2026 = EducationSource(
        "North East Ambulance Service · Cuándo llamar a emergencias",
        "https://www.neas.nhs.uk/first-aid-community/learning-disability-zone/learning-zone/emergency-care999/when-call-999",
        consultedOn = "22 de noviembre de 2026",
    )

    private val bitavida = source(
        "Bitavida · Guía de Observaciones",
        "Biblioteca local · PRODUCT.md, DOMAIN_MODEL.md y OBSERVATIONS_SAFETY.md",
    )
    private val imssFeet = source(
        "IMSS · Cuidados generales de los pies en personas con diabetes",
        "https://www.imss.gob.mx/salud-en-linea/pie-diabetico",
    )
    private val niddkSymptoms = source(
        "NIDDK · Síntomas y causas de la diabetes",
        "https://www.niddk.nih.gov/health-information/informacion-de-la-salud/diabetes/informacion-general/sintomas-causas",
    )
    private val niddkFeet = EducationSource(
        "NIDDK · La diabetes y los problemas de los pies",
        "https://www.niddk.nih.gov/health-information/informacion-de-la-salud/diabetes/informacion-general/prevenir-problemas/pies-piel",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineLegCirculation2026 = EducationSource(
        "MedlinePlus · Arteriopatía periférica en las piernas",
        "https://medlineplus.gov/spanish/ency/article/000170.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineHypoglycemia = EducationSource(
        "MedlinePlus · Hipoglucemia",
        "https://medlineplus.gov/spanish/hypoglycemia.html",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineDizziness2026 = EducationSource(
        "MedlinePlus · Mareo",
        "https://medlineplus.gov/spanish/ency/article/003093.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val neiFloaters2026 = EducationSource(
        "National Eye Institute · Moscas volantes",
        "https://www.nei.nih.gov/espanol/informacion-sobre-la-salud-ocular/enfermedades-y-afecciones-de-los-ojos/miodesopsias",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineSwelling = EducationSource(
        "MedlinePlus · Hinchazón de tobillos, pies y piernas",
        "https://medlineplus.gov/spanish/ency/article/003104.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val urinaryRetention2026 = EducationSource(
        "NIDDK · Retención urinaria",
        "https://www.niddk.nih.gov/health-information/urologic-diseases/urinary-retention/definition-facts",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineTingling = EducationSource(
        "MedlinePlus · Entumecimiento y hormigueo",
        "https://medlineplus.gov/spanish/ency/article/003206.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineVision = EducationSource(
        "MedlinePlus · Ceguera y pérdida de la visión",
        "https://medlineplus.gov/spanish/ency/article/003040.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineNausea = source(
        "MedlinePlus · Náusea y vómitos",
        "https://medlineplus.gov/spanish/nauseaandvomiting.html",
    )
    private val medlineBowel = source(
        "MedlinePlus · Gastroenteritis",
        "https://medlineplus.gov/spanish/gastroenteritis.html",
    )
    private val medlineUrination = EducationSource(
        "MedlinePlus · Dificultad con el flujo urinario",
        "https://medlineplus.gov/spanish/ency/article/003143.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineIncontinence = EducationSource(
        "MedlinePlus · Incontinencia urinaria",
        "https://medlineplus.gov/spanish/ency/article/003142.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineVisit2026 = EducationSource(
        "MedlinePlus · Llevar a una persona a la consulta",
        "https://medlineplus.gov/spanish/ency/patientinstructions/000953.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineConfusion = EducationSource(
        "MedlinePlus · Confusión",
        "https://medlineplus.gov/spanish/ency/article/003205.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineMentalHealth = source(
        "MedlinePlus · Salud mental de las personas mayores",
        "https://medlineplus.gov/spanish/olderadultmentalhealth.html",
    )
    private val medlineFalls = source(
        "MedlinePlus · Prevención de caídas",
        "https://medlineplus.gov/spanish/ency/patientinstructions/000052.htm",
    )
    private val niaBalance = source(
        "National Institute on Aging · Los adultos mayores y los problemas de equilibrio",
        "https://www.nia.nih.gov/espanol/caidas/adultos-mayores-problemas-equilibrio",
    )
    private val cdcStroke = EducationSource(
        "CDC · Signos y síntomas de accidente cerebrovascular",
        "https://www.cdc.gov/stroke/es/signs-symptoms/signos-y-sintomas-de-accidente-cerebrovascular.html",
        consultedOn = "22 de noviembre de 2026",
    )
    private val nidcrOral = source(
        "NIDCR · La diabetes y la salud oral",
        "https://www.nidcr.nih.gov/espanol/temas-de-salud/la-diabetes-y-la-salud-oral",
    )
    private val medlineFever = source(
        "MedlinePlus · Fiebre",
        "https://medlineplus.gov/spanish/fever.html",
    )
    private val medlinePain = source(
        "MedlinePlus · Dolor",
        "https://medlineplus.gov/spanish/pain.html",
    )
    private val medlineFatigue2026 = EducationSource(
        "MedlinePlus · Fatiga",
        "https://medlineplus.gov/spanish/ency/article/003088.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineWeakness2026 = EducationSource(
        "MedlinePlus · Debilidad",
        "https://medlineplus.gov/spanish/ency/article/003174.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineChronicPain2026 = EducationSource(
        "MedlinePlus · Dolor crónico",
        "https://medlineplus.gov/spanish/chronicpain.html",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineNocturia2026 = EducationSource(
        "MedlinePlus · Orinar más durante la noche",
        "https://medlineplus.gov/spanish/ency/article/003141.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineVomiting2026 = EducationSource(
        "MedlinePlus · Náuseas y vómitos en adultos",
        "https://medlineplus.gov/spanish/ency/article/003117.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineDiarrhea2026 = EducationSource(
        "MedlinePlus · Diarrea",
        "https://medlineplus.gov/spanish/diarrhea.html",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineConstipation2026 = EducationSource(
        "MedlinePlus · Estreñimiento",
        "https://medlineplus.gov/spanish/constipation.html",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineAppetite2026 = EducationSource(
        "MedlinePlus · Disminución del apetito",
        "https://medlineplus.gov/spanish/ency/article/003121.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineHeadInjury2026 = EducationSource(
        "MedlinePlus · Lesión cerebral traumática",
        "https://medlineplus.gov/spanish/ency/article/000799.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineEmergency2026 = EducationSource(
        "MedlinePlus · Reconocimiento de emergencias médicas",
        "https://medlineplus.gov/spanish/ency/article/001927.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineChills2026 = EducationSource(
        "MedlinePlus · Escalofríos",
        "https://medlineplus.gov/spanish/ency/article/003091.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val nidcrOral2026 = EducationSource(
        "NIDCR · La diabetes y la salud oral",
        "https://www.nidcr.nih.gov/espanol/temas-de-salud/la-diabetes-y-la-salud-oral",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineOlderMental2026 = EducationSource(
        "MedlinePlus · Salud mental de las personas mayores",
        "https://medlineplus.gov/spanish/olderadultmentalhealth.html",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineAnaphylaxis2026 = EducationSource(
        "MedlinePlus · Anafilaxia",
        "https://medlineplus.gov/spanish/anaphylaxis.html",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineDryMouth2026 = EducationSource(
        "MedlinePlus · Boca seca",
        "https://medlineplus.gov/spanish/ency/article/007760.htm",
        consultedOn = "22 de noviembre de 2026",
    )
    private val medlineDehydration2026 = EducationSource(
        "MedlinePlus · Deshidratación",
        "https://medlineplus.gov/spanish/dehydration.html",
        consultedOn = "22 de noviembre de 2026",
    )

    val entries: Map<EducationTopic, EducationEntry> = listOf(
        general(),
        appUse(),
        guide(
            EducationTopic.OBS_001_FEET_REVIEW, "OBS-001", "Revisar los pies con atención",
            "Una revisión breve ayuda a notar heridas, ampollas, grietas, cambios de color o alteraciones en las uñas que podrían pasar inadvertidas.",
            "La diabetes puede relacionarse con menor sensibilidad o circulación; por eso una lesión pequeña puede no causar molestias claras.",
            listOf("Una marca puede deberse a roce, calzado, resequedad, golpe u otras causas.", "No todo cambio en el pie se debe a la diabetes; el personal de salud necesita contexto para valorarlo."),
            "Si una lesión relevante no se identifica, puede retrasarse su valoración y empeorar. Detectarla no sustituye una revisión profesional.",
            listOf("Revisa ambos pies, plantas, talones, entre los dedos y uñas.", "Anota lado, zona, aspecto, fecha y molestias referidas.", "Una fotografía puede documentar el aspecto sin intentar diagnosticarlo."),
            "Comenta cualquier anomalía con el equipo de salud. Busca atención oportuna ante herida, ampolla, cambio de color, falta de sensibilidad o dolor; una lesión importante requiere valoración sin esperar a completar el registro.",
            listOf(EducationTopic.OBS_002_WOUNDS, EducationTopic.OBS_003_CALLOUSES_NAILS, EducationTopic.OBS_017_TINGLING_NUMBNESS),
            imssFeet, listOf(niddkFeet), "revisar checar pies planta talon dedos", contentVersion = "1.1",
            details = listOf(
                "Mira la piel de ambos pies, incluidas las plantas, los talones y los espacios entre los dedos, para reconocer cambios respecto a su aspecto habitual. Una zona roja, una grieta, una ampolla o un cambio en una uña son observaciones distintas; describe lo que ves sin adjudicarle una causa.",
                "Algunas personas con diabetes pierden sensibilidad y podrían no notar la presión de una costura o una pequeña lesión. La circulación también puede verse afectada y dificultar que una herida sane. No significa que todas las personas con diabetes tengan estos problemas ni que la falta de dolor garantice que la piel esté sana.",
                "Para conversar con el equipo, anota qué pie y qué zona cambiaron, cuándo lo notaste y si se ha modificado el aspecto. Si la persona tiene dificultad para ver la planta, puede pedir ayuda a alguien de confianza. Una fotografía es opcional y no sustituye una revisión profesional.",
            ),
        ),
        guide(
            EducationTopic.OBS_002_WOUNDS, "OBS-002", "Heridas, ampollas y rozaduras",
            "Una rozadura, una cortada o una ampolla son cambios distintos de la piel. Pueden aparecer por presión, calzado o un golpe, entre otras causas; observar su aspecto no basta para conocer su origen.",
            "Si hay pérdida de sensibilidad, la persona podría no sentir una lesión en el pie. Por eso la ausencia de dolor no demuestra que una herida sea leve ni sustituye la valoración profesional.",
            listOf("Una ampolla puede verse como una bolsa pequeña bajo la piel; una rozadura puede mostrar una zona de piel irritada o abierta.", "Enrojecimiento que aumenta, calor o secreción son cambios importantes que deben comunicarse, sin concluir por cuenta propia que existe una infección."),
            "Una lesión puede empeorar o complicarse si se retrasa la atención necesaria; reconocerla temprano facilita que el personal de salud la evalúe.",
            listOf("Ubicación, lado, aspecto y fecha en que se observó.", "Si cambió de tamaño o color, si hay secreción y qué molestias refiere la persona.", "Fotografía opcional con buena luz, sin manipular ni abrir la lesión."),
            "Comunica pronto al equipo de salud cualquier herida o ampolla en el pie. Busca atención sin demora si aparece enrojecimiento, calor, secreción, fiebre o empeora; no retrases la atención para completar el registro.",
            listOf(EducationTopic.OBS_001_FEET_REVIEW, EducationTopic.OBS_005_COLOR_TEMPERATURE, EducationTopic.OBS_025_SKIN_CHANGES),
            niddkFeet, listOf(imssFeet), "herida ampolla rozadura llaga cortada no cierra", contentVersion = "1.2",
            details = listOf(
                "Una ampolla puede parecer una pequeña bolsa de líquido bajo la piel; una rozadura puede verse como un área irritada o abierta. Anota en qué pie y zona está, cuándo se notó y si ha cambiado de tamaño, color o aspecto. Si la persona refiere dolor u otra molestia, usa sus propias palabras.",
                "En algunas personas con diabetes disminuye la sensibilidad de los pies: una piedra dentro del zapato o una lesión pueden pasar inadvertidas. La ausencia de dolor no demuestra que la piel esté sana, y una imagen por sí sola no determina la gravedad ni la causa de una herida.",
                "Para conversar con el equipo, describe si observaste enrojecimiento, calor, secreción o cambios desde la última revisión. Una fotografía es opcional y no requiere manipular la lesión. Pregunta a quién avisar y cuándo necesita valoración; buscar atención no debe esperar a completar el registro.",
            ),
        ),
        guide(
            EducationTopic.OBS_003_CALLOUSES_NAILS, "OBS-003", "Callos, grietas y cambios en uñas",
            "Observar la piel y las uñas ayuda a documentar resequedad, endurecimiento, grietas o cambios que pueden favorecer molestias o lesiones.",
            "Estos cambios pueden relacionarse con presión, calzado, resequedad, golpes u otras situaciones; no permiten identificar una enfermedad por sí solos.",
            listOf("Una grieta puede romper la barrera de la piel.", "Un cambio de uña puede tener distintas causas y requiere valoración si persiste o empeora."),
            "Si un cambio progresa sin atención, puede aumentar la molestia o facilitar una lesión de la piel.",
            listOf("Pie, dedo o zona afectados.", "Color, forma, grosor, grietas o piel endurecida.", "Desde cuándo se nota y si el calzado produce presión."),
            "Coméntalo con el equipo de salud o podología. No cortes callos ni uñas lesionadas por cuenta propia; busca orientación si hay dolor, sangrado, herida o cambio marcado.",
            listOf(EducationTopic.OBS_001_FEET_REVIEW, EducationTopic.OBS_002_WOUNDS),
            imssFeet, listOf(niddkFeet), "callo dureza resequedad grieta una uña enterrada", contentVersion = "1.1",
            details = listOf(
                "Un callo es una zona de piel más gruesa; una grieta es una abertura en la piel. Una uña puede cambiar de grosor, forma o color. No son el mismo cambio y observarlos no permite determinar si existe una infección o por qué aparecieron.",
                "La presión del calzado puede contribuir a zonas endurecidas. Si hay menos sensibilidad en el pie, la persona podría no notar una lesión bajo un callo o alrededor de una uña. Describe pie, dedo o zona, desde cuándo cambió y si observas dolor, sangrado o herida.",
                "El personal de salud o podología puede revisar el cambio y explicar cómo cuidarlo. No cortes callos ni uses productos para retirarlos por cuenta propia; pueden lesionar la piel. Ante una herida, enrojecimiento, calor o sangrado bajo un callo, comunica el cambio sin esperar a completar el registro.",
            ),
        ),
        guide(
            EducationTopic.OBS_004_SWELLING, "OBS-004", "Hinchazón de pies y piernas",
            "La hinchazón es un aumento de volumen que puede hacer que un pie, tobillo o pierna se vea diferente de lo habitual. A veces se debe a acumulación de líquido, llamada edema, pero el aspecto no identifica su causa.",
            "Puede notarse en una sola pierna o en ambas; también importa si apareció de repente o aumentó poco a poco. Permanecer de pie mucho tiempo, algunos medicamentos y distintos problemas de salud pueden relacionarse con ella.",
            listOf("Una pierna nueva o más hinchada que la otra merece atención profesional pronta; no permite saber por sí sola qué ocurre.", "El color rojo, calor o dolor son datos que conviene comunicar, sin atribuirlos a una causa por cuenta propia."),
            "Retrasar la consulta puede demorar la identificación de una causa que requiera atención.",
            listOf("Ubica el cambio: pie, tobillo o pierna; uno o ambos lados.", "Anota cuándo se notó, si apareció de forma repentina y si aumenta, disminuye o permanece.", "Describe dolor, enrojecimiento, calor, fiebre u otros cambios observados."),
            "Contacta al equipo de salud de inmediato si la hinchazón es nueva o empeora en una sola pierna, o si hay enrojecimiento, calor o fiebre. Si además hay dificultad para respirar o dolor de pecho, busca atención de urgencia sin esperar a completar un registro.",
            listOf(EducationTopic.OBS_005_COLOR_TEMPERATURE, EducationTopic.OBS_023_WALKING_BALANCE),
            medlineSwelling, emptyList(), "hinchado hinchada edema tobillo pierna retencion", contentVersion = "1.2",
            details = listOf(
                "La hinchazón puede verse como un cambio en el contorno del tobillo o una diferencia en cómo queda el calzado. A veces es más evidente en pies y tobillos que en otras partes de la pierna. La palabra «edema» describe la hinchazón causada por acumulación de líquido; mirar una zona no permite confirmar por qué ocurrió.",
                "Comparar lo que es habitual para esa persona con lo que observas ahora ayuda a describir el cambio. Indica si afecta un solo lado o ambos, cuándo empezó y si avanza; también si hay dolor, calor o cambio de color. Una fotografía opcional puede complementar tus palabras, pero no permite a Bitavida determinar la causa.",
                "Permanecer mucho tiempo de pie, una lesión, algunos medicamentos y distintas condiciones de salud son posibilidades generales, no una explicación para un caso concreto. Si sospechas que un medicamento interviene, comunica la duda al profesional y no lo suspendas por tu cuenta.",
            ),
        ),
        guide(
            EducationTopic.OBS_005_COLOR_TEMPERATURE, "OBS-005", "Cambios de color o temperatura",
            "Comparar lados y registrar un cambio visible o al tacto puede ayudar al personal de salud a comprender cuándo apareció y cómo evolucionó.",
            "Enrojecimiento, palidez, tono morado, calor o frialdad pueden presentarse por presión, ambiente, circulación, inflamación u otras causas.",
            listOf("El aspecto por sí solo no identifica la causa.", "La combinación con dolor, hinchazón o una herida aporta contexto importante."),
            "Ignorar un cambio persistente puede retrasar la valoración de la piel o la circulación.",
            listOf("Zona y lado.", "Color observado y diferencia de temperatura al tacto.", "Fecha, duración, herida, dolor o hinchazón acompañante."),
            "Pide orientación sin demora ante un cambio nuevo, zona roja o caliente, hinchazón o herida. Un cambio repentino con dolor intenso, pérdida de fuerza o de sensibilidad requiere atención urgente; no esperes a registrar.",
            listOf(EducationTopic.OBS_004_SWELLING, EducationTopic.OBS_002_WOUNDS, EducationTopic.OBS_017_TINGLING_NUMBNESS),
            niddkFeet, listOf(imssFeet, medlineLegCirculation2026), "rojo palido morado frio caliente temperatura piel", contentVersion = "1.1",
            details = listOf(
                "La piel puede verse más roja, pálida, morada u oscura que de costumbre, o una zona puede sentirse más caliente o fría al tacto. Observa qué pie y qué parte cambiaron y, cuando sea posible, compáralos con su aspecto habitual y con el otro lado.",
                "La temperatura del ambiente, el roce y diferentes problemas de salud pueden relacionarse con estos cambios. Un área caliente, roja o hinchada puede requerir valoración, pero su aspecto no permite a Bitavida diagnosticar infección ni identificar cómo circula la sangre.",
                "Comenta cuándo apareció, si sigue cambiando y si hay dolor, hinchazón, una herida o menos sensibilidad. La ausencia de dolor no garantiza que no exista una lesión. Un cambio repentino con dolor intenso, pérdida de fuerza o de sensibilidad requiere atención urgente; no esperes a fotografiarlo o registrarlo.",
            ),
        ),
        guide(
            EducationTopic.OBS_006_DIZZINESS, "OBS-006", "Mareos o sensación de desmayo",
            "Registrar qué sintió la persona, cuánto duró y qué estaba haciendo ayuda a distinguir el episodio sin suponer su causa.",
            "El mareo o desequilibrio puede relacionarse con medicamentos, oído interno, cambios de postura, problemas de salud u otras situaciones.",
            listOf("Sentir que todo gira no es lo mismo que sentirse a punto de desmayar.", "Un episodio aislado y uno repetido necesitan contexto diferente."),
            "Si se repite sin valoración, puede persistir una causa no identificada y aumentar el riesgo de caída.",
            listOf("Sensación descrita, duración y actividad.", "Si ocurrió al levantarse y si se repitió.", "Caída, debilidad, visión, habla o confusión acompañantes."),
            "Consulta si se repite o afecta actividades. La pérdida del conocimiento, la dificultad repentina para caminar o mantener el equilibrio, hablar o ver, y la debilidad repentina requieren atención urgente, incluso si aparecen sin otros síntomas.",
            listOf(EducationTopic.OBS_022_FALL, EducationTopic.OBS_023_WALKING_BALANCE, EducationTopic.OBS_020_CONFUSION_MEMORY),
            niaBalance, listOf(cdcStroke, medlineDizziness2026), "mareo vertigo vueltas inestable desmayo equilibrio", contentVersion = "1.1",
            details = listOf(
                "La palabra «mareo» puede referirse a sentirse aturdido, inestable o a punto de perder el conocimiento. «Vértigo» describe la sensación de que la persona o la habitación giran aunque estén quietas. Preguntar cómo lo siente la persona comunica más que anotar solo «mareo».",
                "Observa si comenzó al levantarse, caminar o mover la cabeza, cuánto duró y si ha ocurrido otras veces. También es útil comunicar si hubo una caída, náusea, cambios de audición o dificultad para mantener el equilibrio. La coincidencia con una actividad no demuestra su causa.",
                "Existen causas diversas, por ejemplo problemas del oído interno, cambios de postura o efectos de medicamentos. El equipo de salud puede preguntar por la evolución y revisar el equilibrio; Bitavida no identifica cuál causa está presente ni indica cambios de medicamentos.",
            ),
        ),
        guide(
            EducationTopic.OBS_007_TREMOR_SWEATING, "OBS-007", "Temblor o sudoración repentina",
            "Documentar el episodio permite comunicar qué se observó y cuándo ocurrió, sin asumir automáticamente una alteración de glucosa.",
            "Temblor y sudoración pueden aparecer por distintas causas. En personas que usan ciertos tratamientos para diabetes también pueden acompañar una glucosa baja, pero solo una valoración adecuada aporta contexto.",
            listOf("La observación no reemplaza una medición indicada.", "No debe inventarse una cifra de glucosa ni modificar tratamiento a partir del registro."),
            "No atender un episodio importante puede retrasar ayuda ante una situación que requiera valoración.",
            listOf("Síntoma, hora, duración y actividad.", "Si hubo hambre, mareo, debilidad o confusión.", "Si existe una medición real indicada, consérvala en Mediciones, no la inventes aquí."),
            "Sigue el plan indicado por el equipo de salud. La pérdida del conocimiento, las convulsiones, la incapacidad para responder o una confusión repentina requieren atención de urgencia; no esperes a terminar de registrar.",
            listOf(EducationTopic.OBS_006_DIZZINESS, EducationTopic.OBS_008_WEAKNESS_FATIGUE, EducationTopic.OBS_020_CONFUSION_MEMORY),
            medlineHypoglycemia, listOf(cdcStroke), "temblor sudor frio sudando repentino hipoglucemia", contentVersion = "1.1",
            details = listOf(
                "El temblor puede notarse como movimientos que la persona no controla; la sudoración puede ser un cambio repentino respecto de lo habitual. Pueden presentarse por separado o juntos. Pregunta qué siente la persona y observa si también refiere hambre, mareo o debilidad, sin concluir una causa.",
                "La glucosa baja es una posibilidad entre otras, especialmente con algunos tratamientos para diabetes; los síntomas varían y algunas personas no los perciben. Un episodio de sudoración o temblor por sí solo no confirma un valor de glucosa ni una hipoglucemia.",
                "Anota cuándo comenzó, cuánto duró y qué estaba ocurriendo, si puedes hacerlo sin retrasar la atención. Si se obtuvo una lectura real, consérvala en Mediciones con su hora. Consulta con el equipo qué plan personal les indicó; la aplicación no cambia dosis ni determina el tratamiento.",
            ),
        ),
        guide(
            EducationTopic.OBS_008_WEAKNESS_FATIGUE, "OBS-008", "Debilidad o cansancio inusual",
            "Comparar con el funcionamiento habitual ayuda a explicar qué actividades se volvieron difíciles y desde cuándo.",
            "El cansancio puede relacionarse con sueño, actividad, alimentación, medicamentos, infecciones u otros problemas de salud.",
            listOf("No siempre está relacionado con la diabetes.", "La aparición repentina o acompañada de otros cambios merece atención especial."),
            "Si persiste sin comentarse, puede retrasarse la identificación de un problema que afecta la movilidad o el bienestar.",
            listOf("Cuándo comenzó y si continúa.", "Actividades afectadas y necesidad de ayuda.", "Fiebre, dolor, falta de aire, confusión o debilidad de un lado."),
            "Consulta si es nuevo, persiste o limita actividades. Debilidad repentina de cara, brazo o pierna, sobre todo de un lado, requiere atención de urgencia.",
            listOf(EducationTopic.OBS_023_WALKING_BALANCE, EducationTopic.OBS_020_CONFUSION_MEMORY, EducationTopic.OBS_024_FEVER_CHILLS),
            niddkSymptoms, listOf(cdcStroke, medlineFatigue2026, medlineWeakness2026), "debil cansado agotado fatiga sin fuerza energia", contentVersion = "1.1",
            details = listOf(
                "El cansancio o fatiga se puede describir como falta de energía; la somnolencia es sentir necesidad de dormir. La debilidad puede referirse a que cuesta hacer fuerza con uno o varios músculos. Una persona puede usar estas palabras de maneras distintas: pregúntale qué siente y qué actividad se volvió más difícil.",
                "Describe si apareció de repente o de manera gradual, si afecta todo el cuerpo o una zona, si se repite y qué ayuda necesita para caminar, levantarse o sostener objetos. Una observación no permite distinguir por sí sola una pérdida real de fuerza de otras causas de cansancio.",
                "El sueño, la actividad, algunas enfermedades y los medicamentos son posibles factores generales, pero ninguno explica automáticamente este episodio. Comenta al equipo qué cambió respecto a lo habitual y cualquier fiebre, falta de aire o mareo observado. Una debilidad repentina de cara, brazo o pierna necesita atención urgente.",
            ),
        ),
        guide(
            EducationTopic.OBS_009_PAIN, "OBS-009", "Dolor nuevo o persistente",
            "Registrar ubicación, inicio y patrón ayuda a comunicar un dolor sin intentar explicar su causa.",
            "El dolor puede originarse en músculos, articulaciones, nervios, piel u órganos, entre muchas posibilidades.",
            listOf("La intensidad es la que refiere la persona; Bitavida no la interpreta.", "Importan los cambios respecto a su dolor habitual y los síntomas acompañantes."),
            "Retrasar la comunicación puede prolongar molestias o demorar la evaluación de una causa relevante.",
            listOf("Zona, inicio y duración.", "Constante o por momentos y actividades relacionadas.", "Golpe, hinchazón, fiebre, falta de aire u otros cambios observados."),
            "Consulta si es nuevo, persiste o empeora. Dolor torácico intenso, dificultad para respirar, pérdida de conocimiento o dolor tras una caída importante requieren atención urgente.",
            listOf(EducationTopic.OBS_022_FALL, EducationTopic.OBS_004_SWELLING),
            medlinePain, listOf(medlineChronicPain2026), "dolor duele molestia punzada persistente nuevo", contentVersion = "1.1",
            details = listOf(
                "El dolor es una experiencia que cada persona puede describir de forma diferente: como ardor, punzada, presión o una molestia sorda. Escuchar sus palabras y preguntar dónde lo siente aporta más información que suponer que dos dolores tienen la misma causa por parecerse.",
                "Un dolor puede comenzar de pronto o persistir durante mucho tiempo; algunos aparecen por episodios. Para comunicarlo, describe cuándo empezó, dónde se siente, si cambió y qué actividades, descanso o sueño afecta. Si la persona usa una escala para describir su intensidad, registra lo que ella indicó.",
                "Una nota o fotografía no puede revelar por qué duele una zona ni demostrar que una lesión sea leve. Comenta el dolor nuevo, persistente o que empeora con el equipo y pregunta cómo reconocer cambios que requieren atención para esa persona; Bitavida no prescribe ni modifica tratamientos.",
            ),
        ),
        guide(
            EducationTopic.OBS_010_THIRST_DRY_MOUTH, "OBS-010", "Sed excesiva o boca seca",
            "Anotar un aumento respecto a lo habitual ayuda a relacionarlo temporalmente con otros cambios sin atribuirle una causa.",
            "La sed o boca seca puede aparecer por calor, menor consumo de líquidos, medicamentos, problemas de boca, glucosa elevada u otras situaciones.",
            listOf("Por sí sola no confirma una alteración de glucosa.", "Sed junto con orina frecuente, debilidad o vómitos aporta contexto para el equipo de salud."),
            "Si persiste, no comunicarla puede retrasar la valoración de deshidratación u otra causa.",
            listOf("Desde cuándo, si continúa y cuánto difiere de lo habitual.", "Boca seca, orina más frecuente, vómitos o cambios de apetito.", "Cualquier medición real debe registrarse en Mediciones."),
            "Coméntala si es nueva o persistente, o si hay dificultad para tragar. Busca orientación pronta si no puede retener líquidos u orina mucho menos; ante confusión repentina, desmayo o ausencia de orina busca atención urgente.",
            listOf(EducationTopic.OBS_011_FREQUENT_URINATION, EducationTopic.OBS_014_NAUSEA_VOMITING, EducationTopic.OBS_026_MOUTH_GUMS),
            niddkSymptoms, listOf(medlineNausea, medlineDryMouth2026, medlineDehydration2026), "sed mucha agua reseca boca seca deshidratacion", contentVersion = "1.1",
            details = listOf(
                "La sed es la sensación de querer beber. La boca seca es una sensación de sequedad o de tener poca saliva; pueden aparecer juntas, pero no son exactamente lo mismo. La saliva ayuda a mantener la boca húmeda y participa en la masticación y la deglución, es decir, al tragar.",
                "Una persona puede notar labios o lengua secos, sensación pegajosa, dificultad al hablar o masticar, o sed más frecuente que antes. Describe qué refiere, desde cuándo cambió y si hay vómitos, dificultad para tragar o diferencias en la frecuencia de la orina.",
                "Medicamentos, respiración por la boca, pérdida de líquidos y otras condiciones pueden relacionarse con sequedad o sed. No es correcto concluir que hay una glucosa determinada o deshidratación solo por observar uno de estos cambios. El equipo puede revisar el contexto y orientar qué necesita valoración.",
            ),
        ),
        guide(
            EducationTopic.OBS_011_FREQUENT_URINATION, "OBS-011", "Orinar más de lo habitual",
            "Registrar un cambio de frecuencia ayuda a diferenciar día, noche o ambos y a explicar desde cuándo ocurre.",
            "Puede relacionarse con mayor consumo de líquidos, medicamentos, vías urinarias, glucosa u otras causas.",
            listOf("No confirma por sí solo una infección ni una alteración de glucosa.", "Levantarse repetidamente de noche también puede aumentar el riesgo de caída."),
            "Si el cambio persiste sin comunicarse, puede retrasarse la valoración de su causa y afectar sueño o hidratación.",
            listOf("Frecuencia aproximada y momento del día.", "Urgencia, escapes, ardor, sed o cambios de color de la orina.", "Desde cuándo y si interrumpe el sueño."),
            "Consulta si es nuevo, persiste o se acompaña de dolor, fiebre, sangre o dificultad para orinar.",
            listOf(EducationTopic.OBS_010_THIRST_DRY_MOUTH, EducationTopic.OBS_012_URINATION_DIFFICULTY, EducationTopic.OBS_013_PAINFUL_URINATION),
            niddkSymptoms, listOf(medlineIncontinence, medlineNocturia2026), "orina mucho baño frecuente noche nocturia", contentVersion = "1.1",
            details = listOf(
                "Orinar con mayor frecuencia significa necesitar ir al baño más veces que es habitual para esa persona. Puede suceder de día, de noche o en ambos momentos. Despertarse repetidamente durante la noche interrumpe el sueño y puede hacer más frecuentes los desplazamientos al baño.",
                "Pregúntale qué ha cambiado y desde cuándo: si necesita ir con urgencia, si son muchas visitas con poca orina o si hay sed, ardor, escapes o molestias. Describe la frecuencia aproximada solo si la conoces; más visitas no demuestran por sí solas la cantidad total de orina.",
                "Los líquidos, algunos medicamentos y diferentes problemas de salud pueden relacionarse con el cambio. La observación no permite decidir cuál es la causa ni confirmar una alteración de glucosa. Comenta con el equipo si persiste o aparece junto a otros síntomas.",
            ),
        ),
        guide(
            EducationTopic.OBS_012_URINATION_DIFFICULTY, "OBS-012", "Escapes o dificultad para orinar",
            "Un escape es la salida involuntaria de orina; puede ocurrir, por ejemplo, al toser o tras una necesidad repentina de ir al baño. La dificultad para orinar puede notarse como problemas para comenzar, chorro débil o goteo.",
            "Son situaciones diferentes que pueden presentarse por distintas causas. Algunas personas consiguen orinar pero no vacían completamente la vejiga; otras dejan de poder orinar de forma repentina. Ninguna descripción confirma por sí sola una infección.",
            listOf("Describe lo que sucede con respeto y las palabras de la persona, sin suponer que el cambio forma parte inevitable de la edad.", "Fiebre, sangre o dolor de espalda son datos que el equipo de salud debe conocer."),
            "Retrasar la valoración puede prolongar molestias o demorar la atención de una retención u otro problema.",
            listOf("Si hay escape, urgencia, goteo, chorro débil o dificultad para empezar; cuándo ocurre y desde cuándo.", "Frecuencia aproximada y molestias descritas, sin inventar cantidades.", "Fiebre, sangre, dolor en la parte baja del abdomen o espalda y cambios neurológicos acompañantes."),
            "Comenta los cambios al equipo de salud. Si de repente no puede orinar, busca atención de urgencia de inmediato. Sangre, fiebre con dolor de espalda o cambios neurológicos súbitos también requieren atención sin demora.",
            listOf(EducationTopic.OBS_013_PAINFUL_URINATION, EducationTopic.OBS_011_FREQUENT_URINATION),
            medlineUrination, listOf(medlineIncontinence, urinaryRetention2026), "escape goteo incontinencia retencion cuesta orinar", contentVersion = "1.2",
            details = listOf(
                "Un escape es la salida involuntaria de orina; puede pasar al toser o después de sentir una necesidad repentina de ir al baño. Dificultad para iniciar, chorro débil y goteo describen otros cambios. Una persona puede experimentar más de uno, y conviene preguntarle cómo lo vive sin avergonzarla.",
                "La vejiga es el órgano que almacena la orina. Algunas personas pueden orinar pero sentir que no se vació por completo; en otras, aparece de repente una incapacidad para orinar, que requiere atención urgente. No intentes concluir a partir del registro qué está causando el problema.",
                "Para la consulta, describe qué ocurre, cuándo comenzó, si hay dolor, sangre o fiebre y cómo afecta el sueño o las actividades. No inventes volúmenes. Si la persona de repente no puede orinar, busca ayuda urgente antes de registrar los detalles.",
            ),
        ),
        guide(
            EducationTopic.OBS_013_PAINFUL_URINATION, "OBS-013", "Ardor o molestias al orinar",
            "Registrar el inicio y si continúa ayuda a comunicar una molestia urinaria sin asignarle automáticamente una infección.",
            "Ardor, dolor o incomodidad pueden relacionarse con irritación, problemas urinarios u otras causas que requieren contexto.",
            listOf("La descripción no sustituye una evaluación ni un análisis.", "Fiebre, dolor de espalda o sangre son datos importantes para comunicar."),
            "Si persiste sin valoración, puede prolongarse la molestia o retrasarse la atención de su causa.",
            listOf("Ardor, dolor o presión y fecha de inicio.", "Frecuencia, urgencia, color visible y olor inusual.", "Fiebre, escalofríos o dolor de costado/espalda."),
            "Consulta oportunamente si continúa. Fiebre, vómitos, escalofríos, dolor de espalda o sangre requieren atención pronta; si de repente no puede orinar, busca atención urgente.",
            listOf(EducationTopic.OBS_012_URINATION_DIFFICULTY, EducationTopic.OBS_024_FEVER_CHILLS),
            niddkBladderSymptoms2026, listOf(urinaryRetention2026, niddkUrineTests2026, nhsUrethritis2026), "ardor dolor molestia orinar infeccion urinaria", contentVersion = "1.1",
            details = listOf(
                "La molestia puede describirse como ardor, dolor o presión al orinar. Pregunta cuándo comenzó, si ocurre durante toda la micción o solo en algún momento y si desaparece después. Escuchar la descripción de la persona ayuda a comunicar lo que vive sin etiquetarlo.",
                "La irritación, inflamación y distintas enfermedades pueden causar molestias al orinar. Aunque una infección es una posibilidad, el síntoma por sí solo no la confirma ni indica qué tratamiento se necesita. No cambies medicamentos por cuenta propia.",
                "El ardor al orinar y una molestia en la parte baja del abdomen son experiencias que conviene describir por separado. Por ejemplo, contar que el malestar aparece al ir al baño comunica algo distinto a decir que continúa después. Si la persona no puede precisar el sitio o el momento, registra solamente lo que sí sabe.",
                "En la consulta pueden preguntar cuándo empezó el dolor, si hay fiebre, secreción o cambios al orinar y qué medicamentos utiliza. El profesional puede solicitar una muestra de orina y, en algunas situaciones, un cultivo para buscar microbios; la elección depende de la valoración, no de la nota de Bitavida. Puedes preguntar: ¿qué información necesitan de la molestia?, ¿es útil una muestra? y ¿a quién debemos avisar si empeora?", 
                "Anota si hay necesidad frecuente o urgente, sangre visible, fiebre, escalofríos o dolor en la espalda; comunica esos cambios al equipo. Si de repente la persona no puede orinar, busca atención urgente antes de completar un registro.",
            ),
        ),
        guide(
            EducationTopic.OBS_014_NAUSEA_VOMITING, "OBS-014", "Náuseas o vómitos",
            "Anotar momento y número aproximado de episodios ayuda a comunicar la evolución y posibles dificultades para retener líquidos.",
            "Náuseas y vómitos pueden aparecer por infecciones, alimentos, medicamentos, problemas digestivos y muchas otras causas.",
            listOf("No todo episodio tiene el mismo origen.", "En adultos mayores, la pérdida de líquidos puede requerir atención más temprana."),
            "Vómitos repetidos pueden favorecer deshidratación; retrasar la consulta puede demorar atención necesaria.",
            listOf("Náusea, vómito o ambos; inicio y episodios.", "Capacidad para retener líquidos y frecuencia de orina.", "Dolor, fiebre, diarrea, sangre o confusión acompañantes."),
            "Consulta si persiste o se repite. Si hay sangre o material oscuro parecido al café en el vómito, o aparece confusión repentina, busca atención urgente. Dolor abdominal fuerte, incapacidad para retener líquidos o poca orina requieren valoración pronta.",
            listOf(EducationTopic.OBS_015_BOWEL_CHANGES, EducationTopic.OBS_010_THIRST_DRY_MOUTH),
            medlineNausea, listOf(medlineBowel, medlineVomiting2026, medlineDehydration2026, medlineConfusion), "nausea vomito devolver estomago no retiene comida", contentVersion = "1.1",
            details = listOf(
                "La náusea es la sensación de querer vomitar; el vómito ocurre cuando el contenido del estómago sale por la boca. Una persona puede tener náusea sin vomitar o presentar ambos. Pregúntale cómo se siente y cuándo comenzó.",
                "Si ocurrió más de una vez, anota el número aproximado de episodios que conoces y si puede retener líquidos. Comunica también dolor abdominal, fiebre, diarrea o si ha orinado menos; pueden ayudar al equipo a valorar la situación sin indicar por sí solos una causa.",
                "Los vómitos repetidos pueden acompañarse de pérdida de líquidos. Si aparece sangre o material oscuro parecido al café en el vómito, busca atención urgente; no retrases esa atención para tomar una foto o terminar el formulario.",
            ),
        ),
        guide(
            EducationTopic.OBS_015_BOWEL_CHANGES, "OBS-015", "Cambios en las evacuaciones",
            "Comparar con el patrón habitual ayuda a describir estreñimiento, diarrea u otros cambios sin diagnosticar su causa.",
            "Alimentación, líquidos, medicamentos, actividad, infecciones y otros problemas pueden modificar las evacuaciones.",
            listOf("Importan duración, frecuencia y cambios visibles.", "Adultos mayores pueden deshidratarse con mayor facilidad ante diarrea o vómitos."),
            "Un cambio persistente puede producir molestias o pérdida de líquidos y retrasar la valoración de una causa relevante.",
            listOf("Tipo de cambio, inicio y si continúa.", "Frecuencia aproximada, dolor y distensión.", "Sangre visible, heces negras, fiebre, vómitos o deshidratación."),
            "Consulta si persiste, empeora o se acompaña de dolor. Sangre, heces negras, dolor intenso, vómitos frecuentes, fiebre alta o deshidratación justifican atención pronta.",
            listOf(EducationTopic.OBS_014_NAUSEA_VOMITING, EducationTopic.OBS_018_APPETITE),
            medlineBowel, listOf(medlineDiarrhea2026, medlineConstipation2026, niddkBowelDiagnosis2026), "diarrea estreñimiento evacuacion heces baño vientre", contentVersion = "1.1",
            details = listOf(
                "La diarrea describe evacuaciones sueltas o líquidas más frecuentes; el estreñimiento puede incluir evacuaciones menos frecuentes de lo habitual, heces duras o dificultad al evacuarlas. El patrón normal varía entre personas, de modo que importa qué cambió respecto a su costumbre.",
                "Con respeto y usando las palabras de la persona, anota cuándo comenzó el cambio, cuántas veces ocurrió si lo sabes y si hay dolor, distensión, fiebre o cambios visibles como sangre. No es necesario adivinar una causa ni una cantidad.",
                "Por ejemplo, si habitualmente evacúa cada dos días y ahora presenta varias evacuaciones líquidas en un día, la diferencia ayuda a entender el cambio. En otra persona, menos evacuaciones junto con heces duras y esfuerzo pueden ser la novedad. Un número aislado no describe toda la situación.",
                "En la consulta pueden preguntar por medicamentos, duración, molestias y cambios recientes; según la situación, el profesional podría explorar el abdomen o solicitar muestras. Pregunta: ¿qué señales debemos comunicar pronto?, ¿qué información del patrón habitual les ayuda? y ¿necesita revisar algún medicamento? No cambies el tratamiento por cuenta propia.",
                "Con diarrea o vómitos puede perderse líquido. Comunica al equipo si la persona no logra retener líquidos u orina menos, y pregunta cuándo necesita valoración. Una nota en Bitavida describe lo observado; no interpreta las evacuaciones ni sustituye atención médica.",
            ),
        ),
        guide(
            EducationTopic.OBS_016_VISION, "OBS-016", "Cambios en la visión",
            "Un cambio visual puede describirse como dificultad para enfocar, visión borrosa, ver doble o dejar de ver una parte de lo que antes se veía. Puede afectar uno o ambos ojos y aparecer de repente o de forma gradual.",
            "La visión puede cambiar por distintos problemas del ojo u otras situaciones de salud. Un registro no permite saber cuál es la causa ni confirma una complicación de la diabetes.",
            listOf("La pérdida de visión puede ser parcial o completa; no todos los cambios se manifiestan igual.", "Un cambio repentino necesita atención urgente aunque no haya dolor ni otros síntomas."),
            "Retrasar la atención ante una pérdida súbita puede reducir oportunidades de evaluación y tratamiento oportunos.",
            listOf("Qué describe la persona: borroso, doble, zona que no alcanza a ver u otro cambio; uno o ambos ojos.", "Si empezó de repente o gradualmente y la hora aproximada si se conoce.", "Si hay dolor, destellos, debilidad, confusión o dificultad para hablar."),
            "Comenta los cambios graduales con el equipo de salud. La pérdida o alteración repentina de visión requiere atención urgente, incluso sin debilidad ni dificultad para hablar. La aparición repentina de muchas manchas nuevas, destellos nuevos o una sombra como cortina también requiere valoración ocular inmediata o acudir a urgencias.",
            listOf(EducationTopic.OBS_020_CONFUSION_MEMORY, EducationTopic.OBS_023_WALKING_BALANCE),
            medlineVision, listOf(cdcStroke, niddkSymptoms, neiFloaters2026), "borroso vista vision enfocar ojo doble no ve", contentVersion = "1.2",
            details = listOf(
                "La visión borrosa puede sentirse como si costara enfocar; ver doble significa percibir dos imágenes. Algunas personas describen una zona oscura, destellos o pequeñas manchas que flotan. Estas descripciones no son equivalentes y no permiten conocer el origen sin una exploración.",
                "Pregunta qué nota la persona y, si lo sabe, si ocurre en un ojo o en ambos, si comenzó de repente o poco a poco y cuándo lo advirtió por primera vez. Describe si dificulta leer, caminar o reconocer objetos y si hay dolor u otros cambios. No supongas que una alteración visual se debe a la diabetes.",
                "El equipo puede necesitar saber si aparecieron de pronto muchas manchas flotantes, destellos nuevos o una sombra parecida a una cortina: son motivos de valoración ocular inmediata. Anotar la hora conocida puede ayudar, pero no debe retrasar la atención urgente.",
            ),
        ),
        guide(
            EducationTopic.OBS_017_TINGLING_NUMBNESS, "OBS-017", "Hormigueo, ardor o entumecimiento",
            "El hormigueo puede sentirse como piquetes; el ardor, como una sensación de quemazón; y el entumecimiento, como sentir menos el tacto. Pueden aparecer en dedos, manos, pies, brazos o piernas.",
            "Estos cambios pueden relacionarse con una postura, presión sobre un nervio, circulación u otras causas. En la diabetes algunas personas presentan alteraciones de nervios, pero registrar esta sensación no confirma neuropatía.",
            listOf("Tener menos sensibilidad no significa necesariamente tener dolor: una rozadura, piedra dentro del zapato o herida puede pasar inadvertida.", "La descripción y la evolución ayudan al profesional; no identifican por sí solas la causa."),
            "Si la sensibilidad disminuye y no se reconoce, una herida o presión pueden pasar inadvertidas.",
            listOf("Qué siente la persona, en qué zona y en uno o ambos lados.", "Cuándo empezó, cuánto dura, si es continuo o aparece por episodios y cómo afecta caminar o usar las manos.", "Si hay una herida, cambio de color, debilidad o pérdida de equilibrio."),
            "Consulta si el cambio persiste, se extiende o afecta actividades. El entumecimiento o debilidad repentinos de la cara, brazo o pierna, especialmente de un lado, requieren atención de urgencia incluso si no hay otros síntomas; lo mismo ocurre con dificultad repentina para hablar, ver o caminar.",
            listOf(EducationTopic.OBS_001_FEET_REVIEW, EducationTopic.OBS_005_COLOR_TEMPERATURE, EducationTopic.OBS_023_WALKING_BALANCE),
            medlineTingling, listOf(cdcStroke, niddkFeet), "hormigueo ardor entumido duermen pies sensibilidad", contentVersion = "1.2",
            details = listOf(
                "El hormigueo puede describirse como pequeños piquetes o cosquilleo; el ardor se parece a una quemazón. En cambio, el entumecimiento implica notar menos el tacto o tener una zona «dormida». Una misma persona puede describir más de una sensación, y es útil conservar sus propias palabras.",
                "Si la sensibilidad del pie disminuye, una costura, una piedrita en el zapato o una ampolla podrían no sentirse. Eso explica por qué observar la piel puede aportar información aunque no haya dolor. No significa que toda persona con diabetes tenga pérdida de sensibilidad ni que toda sensación se deba a la diabetes.",
                "Para comunicar el cambio, señala la zona, el lado y si empezó de forma súbita o gradual. Explica si aparece por momentos, cuánto dura y si dificulta caminar o usar las manos. Estos detalles ayudan a orientar la conversación, pero no permiten identificar por sí solos un nervio afectado o una enfermedad.",
            ),
        ),
        guide(
            EducationTopic.OBS_018_APPETITE, "OBS-018", "Cambios en el apetito",
            "Registrar si come más, menos o pierde interés ayuda a mostrar un cambio respecto a su patrón habitual.",
            "El apetito puede variar por estado de ánimo, boca, digestión, infecciones, medicamentos u otros problemas.",
            listOf("No permite conocer una causa por sí solo.", "Importan la duración y los cambios acompañantes."),
            "Si el cambio persiste, puede afectar la alimentación o retrasar la identificación de una causa que requiera apoyo.",
            listOf("Come menos, más o rechaza alimentos.", "Desde cuándo y si continúa.", "Náusea, dolor, boca seca, problemas para masticar, ánimo o peso observado."),
            "Coméntalo si persiste o afecta la alimentación. Incapacidad para comer o beber, vómitos repetidos o debilidad marcada justifican orientación pronta; ante confusión repentina o pérdida de conciencia busca atención urgente.",
            listOf(EducationTopic.OBS_014_NAUSEA_VOMITING, EducationTopic.OBS_026_MOUTH_GUMS, EducationTopic.OBS_021_MOOD_BEHAVIOR),
            niddkSymptoms, listOf(nidcrOral, medlineAppetite2026, medlineConfusion), "apetito no quiere comer hambre come menos mas alimento", contentVersion = "1.1",
            details = listOf(
                "El apetito es el deseo de comer. Que la persona tenga menos ganas de comer no es exactamente lo mismo que tener dificultad para masticar, dolor en la boca o problemas para tragar, aunque esas situaciones pueden coincidir. Pregunta qué nota ella y escucha su explicación sin juzgarla.",
                "Describe qué ha cambiado respecto a sus costumbres, desde cuándo y si ocurre en todas las comidas o solo en algunos momentos. Comenta también si refiere náusea, dolor o sequedad en la boca, o si se ha observado un cambio de peso; registra una cifra solo si realmente se midió.",
                "Hay causas posibles muy distintas, desde molestias bucales hasta cambios de salud, ánimo o medicamentos. La aplicación no elige una causa ni prescribe una dieta. Si sospechas que un medicamento influye, plantea la duda al equipo sin suspenderlo por cuenta propia.",
            ),
        ),
        guide(
            EducationTopic.OBS_019_SLEEP, "OBS-019", "Cambios en el sueño",
            "Observar duración, despertares y descanso ayuda a comunicar cómo cambió el patrón habitual.",
            "El sueño puede modificarse por dolor, ánimo, medicamentos, necesidad de orinar, ambiente u otros problemas de salud.",
            listOf("Una noche aislada y un cambio repetido no significan lo mismo.", "Somnolencia marcada y confusión necesitan contexto."),
            "Un cambio persistente puede afectar energía, atención, equilibrio y bienestar.",
            listOf("Duerme más o menos y cuántas veces despierta.", "Desde cuándo y con qué frecuencia.", "Dolor, orina nocturna, ronquido nuevo, ánimo o somnolencia diurna."),
            "Consulta si persiste o afecta actividades. La dificultad para despertar a la persona, la confusión repentina o una respiración anormal requieren atención urgente.",
            listOf(EducationTopic.OBS_008_WEAKNESS_FATIGUE, EducationTopic.OBS_011_FREQUENT_URINATION, EducationTopic.OBS_021_MOOD_BEHAVIOR),
            niaSleep2026, listOf(medlineMentalHealth, nhsEmergency2026, cdcStroke), "sueño duerme insomnio despierta descanso somnolencia", contentVersion = "1.1",
            details = listOf(
                "Mientras dormimos, el cerebro y el cuerpo continúan trabajando. Dificultad para quedarse dormido, varios despertares y sentirse somnoliento durante el día son experiencias distintas. Anotar cuál ocurre ayuda a conversarlo; una noche diferente no demuestra por sí sola un trastorno del sueño.",
                "Describe qué cambió respecto al patrón habitual: si tarda en dormir, despierta, duerme más o se muestra somnoliento de día, desde cuándo y con qué frecuencia. Si lo sabes, comunica dolor, ronquidos, necesidad de orinar o preocupaciones que la persona haya mencionado.",
                "El dolor, el ambiente, algunos medicamentos y distintos problemas de salud pueden afectar el descanso. No deduzcas una causa ni cambies tratamientos. Si cuesta despertar a la persona o su respiración parece anormal, busca ayuda urgente sin esperar a completar el registro.",
                "Un registro puede diferenciar «tardó en dormirse» de «se durmió pronto, pero despertó varias veces». Si la persona está de acuerdo, anota la hora aproximada de acostarse y levantarse, despertares conocidos y si duerme durante el día. No es necesario vigilarla toda la noche ni inventar horarios que no recuerdas.",
                "El profesional puede preguntar cómo afecta el descanso a las actividades, si hay dolor, ronquidos o cambios de rutina y qué medicamentos utiliza. Pregunta: ¿qué datos del sueño conviene anotar?, ¿los despertares o la somnolencia necesitan valoración? y ¿qué opciones son adecuadas para esta persona? No añadas ni retires medicamentos para dormir por cuenta propia.",
            ),
        ),
        guide(
            EducationTopic.OBS_020_CONFUSION_MEMORY, "OBS-020", "Confusión o problemas de memoria",
            "La confusión puede notarse cuando la persona tiene más dificultad de la habitual para prestar atención, pensar con claridad, recordar o ubicarse en el tiempo y el lugar. Algunas personas actúan de una forma distinta a la acostumbrada.",
            "Un cambio que aparece de repente no es lo mismo que olvidos que aumentaron lentamente. Existen muchas posibles causas, como infecciones, medicamentos, falta de sueño u otros problemas; el registro no determina cuál es.",
            listOf("Una dificultad de memoria no confirma demencia ni una cifra de glucosa.", "La persona que conoce su forma habitual de comunicarse puede describir qué cambió y cuándo lo notó."),
            "La confusión que aparece de repente necesita atención urgente; completar el registro no debe retrasarla.",
            listOf("Describe qué hizo o dijo, desde cuándo y cuál era su estado habitual.", "Anota si apareció de repente o gradualmente, si continúa y la hora aproximada de inicio si se conoce.", "Comunica dificultad para hablar, debilidad, cambios de visión, fiebre, caída o golpe que hayas observado."),
            "Comenta los cambios graduales con el equipo de salud. Si la confusión aparece de repente, busca atención de urgencia de inmediato; también ante dificultad repentina para hablar, debilidad de un lado o pérdida súbita de visión, aunque después mejoren.",
            listOf(EducationTopic.OBS_021_MOOD_BEHAVIOR, EducationTopic.OBS_022_FALL, EducationTopic.OBS_016_VISION),
            medlineConfusion, listOf(cdcStroke), "confundido desorientado memoria olvida raro no reconoce", contentVersion = "1.2",
            details = listOf(
                "Una persona puede perder el hilo de una conversación, tener dificultad para concentrarse, no ubicarse en un lugar conocido o responder de forma distinta a lo habitual. No todas las personas presentan los mismos cambios. Describe lo que observaste y escucha cómo lo explica la propia persona, sin usar una etiqueta como diagnóstico.",
                "Olvidar algo y presentar una confusión repentina no son equivalentes. La atención puede cambiar durante el día y las causas posibles son diversas, entre ellas enfermedad, medicamentos o falta de sueño. Solo una valoración profesional puede aclarar qué ocurre; no es correcto concluir que se trata de demencia o de un valor de glucosa concreto.",
                "Si el cambio apareció repentinamente, busca ayuda urgente primero, aunque mejore después. Si conoces la hora en que comenzó o la última vez que viste a la persona en su estado habitual, comunícalo al personal; no deduzcas esa hora cuando no la sabes ni retrases la llamada para registrarla.",
            ),
        ),
        guide(
            EducationTopic.OBS_021_MOOD_BEHAVIOR, "OBS-021", "Cambios de ánimo o comportamiento",
            "Registrar hechos observables y situaciones ayuda a comunicar un cambio sin etiquetar a la persona ni afirmar un trastorno.",
            "Irritabilidad, preocupación, tristeza, aislamiento o conducta diferente pueden relacionarse con experiencias, sueño, dolor, medicamentos o problemas de salud.",
            listOf("El registro no diagnostica depresión, ansiedad ni demencia.", "Importa comparar con el comportamiento habitual y respetar las palabras de la persona."),
            "Si un cambio importante no se comunica, puede retrasarse apoyo para una necesidad emocional, social o médica.",
            listOf("Conducta concreta, fecha y situaciones en que ocurre.", "Duración, repetición y efecto en actividades.", "Sueño, apetito, dolor, confusión o aislamiento acompañantes."),
            "Coméntalo si persiste, causa sufrimiento o dificulta el cuidado. Confusión repentina, riesgo de hacerse daño, comportamiento peligroso o pérdida del conocimiento requieren ayuda inmediata.",
            listOf(EducationTopic.OBS_020_CONFUSION_MEMORY, EducationTopic.OBS_019_SLEEP, EducationTopic.OBS_018_APPETITE),
            medlineMentalHealth, listOf(medlineConfusion, medlineOlderMental2026), "animo comportamiento irritable triste preocupado agresivo diferente", contentVersion = "1.1",
            details = listOf(
                "Un cambio de ánimo puede expresarse como tristeza, irritabilidad, preocupación o menor interés en actividades que la persona solía disfrutar. El comportamiento también puede cambiar sin que la persona lo describa de esa manera. Pregunta cómo se siente y escucha sus palabras sin imponer una etiqueta.",
                "Compara con lo habitual y explica desde cuándo ocurre, si se repite, en qué situaciones lo notas y cómo afecta sus relaciones, sueño, apetito o actividades. La tristeza o el aislamiento pueden tener distintas explicaciones: el registro no diagnostica depresión, ansiedad ni demencia.",
                "Hablar con un profesional puede ayudar cuando el cambio persiste o causa sufrimiento. Si aparece confusión de repente o existe riesgo de que la persona se haga daño o dañe a alguien, busca ayuda urgente antes de completar el registro.",
            ),
        ),
        guide(
            EducationTopic.OBS_022_FALL, "OBS-022", "Caída o golpe",
            "Documentar qué ocurrió, lugar, hora y zona golpeada ayuda a valorar lesiones y a identificar circunstancias que podrían prevenir otra caída.",
            "Una caída puede relacionarse con obstáculos, equilibrio, visión, debilidad, medicamentos u otros factores; el registro no establece la causa.",
            listOf("Incluso sin una lesión visible puede ser útil comunicarla.", "Golpe en la cabeza, dolor intenso o cambios neurológicos requieren atención especial."),
            "No valorar una lesión puede retrasar la atención de fracturas, traumatismos u otros problemas; una caída también puede aumentar el temor a moverse.",
            listOf("Cómo, dónde y cuándo ocurrió.", "Zona golpeada, dolor, sangrado y posibilidad de golpe en la cabeza.", "Mareo, pérdida de conocimiento, confusión, vómitos o dificultad para caminar."),
            "Comenta toda caída relevante con el equipo de salud. Busca atención urgente ante pérdida del conocimiento, golpe importante en la cabeza, sangrado difícil de controlar, deformidad, dolor intenso, vómitos repetidos o confusión.",
            listOf(EducationTopic.OBS_023_WALKING_BALANCE, EducationTopic.OBS_006_DIZZINESS, EducationTopic.OBS_020_CONFUSION_MEMORY),
            medlineFalls, listOf(niaBalance, cdcStroke, medlineHeadInjury2026), "caida cayo golpe accidente tropiezo resbalo cabeza", contentVersion = "1.1",
            details = listOf(
                "Una caída puede ocurrir por un tropiezo o acompañarse de mareo, dificultad para caminar u otros cambios. Incluso cuando no se ve una herida, comunicar lo ocurrido puede ayudar al equipo a preguntar por lesiones y por circunstancias relacionadas.",
                "Si se conoce, describe dónde y cuándo ocurrió, cómo cayó y si pudo golpearse la cabeza. Comunica qué zona duele y si hubo pérdida del conocimiento, vómitos, confusión o dificultad para moverse. No inventes detalles que nadie observó.",
                "Tras una caída con posible lesión en cabeza o cuello, pide ayuda y evita mover a la persona innecesariamente. Pérdida del conocimiento, confusión, golpe importante en la cabeza o dolor intenso requieren atención urgente; no la retrases para registrar o fotografiar.",
            ),
        ),
        guide(
            EducationTopic.OBS_023_WALKING_BALANCE, "OBS-023", "Dificultad para caminar o mantener el equilibrio",
            "Describir la actividad afectada ayuda a comunicar un cambio de movilidad y a prevenir caídas.",
            "Puede relacionarse con visión, oído interno, fuerza, dolor, medicamentos, nervios, circulación u otras condiciones.",
            listOf("No indica por sí sola una causa.", "El inicio repentino con otros cambios neurológicos es diferente de una dificultad gradual."),
            "Si no se reconoce, puede aumentar el riesgo de caída y limitar actividades cotidianas.",
            listOf("Inestabilidad, pasos, apoyo necesario y actividad.", "Inicio, duración y si continúa.", "Mareo, visión, fuerza, sensibilidad, dolor o caída."),
            "Consulta si es nueva, se repite o limita actividades. La dificultad repentina para caminar o mantener el equilibrio requiere atención urgente, incluso si no se observan otros síntomas.",
            listOf(EducationTopic.OBS_006_DIZZINESS, EducationTopic.OBS_022_FALL, EducationTopic.OBS_016_VISION),
            niaBalance, listOf(cdcStroke, nidcdBalanceTests2026), "caminar equilibrio inestable tambalea movilidad marcha", contentVersion = "1.1",
            details = listOf(
                "Una persona puede notar inseguridad al estar de pie, tambalearse al dar pasos o sentir que todo gira aunque esté quieta. Pregunta qué experimenta y en qué situaciones sucede; necesitar más apoyo para caminar es un cambio que merece describirse sin atribuirlo automáticamente a la edad.",
                "Indica desde cuándo sucede, si comenzó de repente, si se repite y qué actividades afecta. También comunica si hubo una caída, mareo, dolor o cambios en la visión o la fuerza. Es información para la consulta y no determina la causa del problema.",
                "El equilibrio depende de la colaboración entre visión, oído interno, nervios y músculos. Muchas situaciones pueden afectar esa coordinación. Si aparece de manera repentina dificultad para caminar o mantener el equilibrio, busca atención urgente aunque no haya otras señales.",
                "Por ejemplo, necesitar agarrarse al levantarse de una silla describe una situación distinta a desviarse al caminar o sentir que el entorno gira. Si usa bastón o andadera, anota si ya los utilizaba y si ahora requiere más apoyo. No le pidas repetir una actividad insegura para completar la nota.",
                "Durante la valoración, el profesional puede preguntar por caídas, medicamentos y situaciones que provocan la inestabilidad; también podría revisar la forma de caminar, la vista o el oído según el caso. Puedes preguntar: ¿qué actividades son seguras mientras lo valoran?, ¿necesita apoyo para desplazarse? y ¿qué cambios deben comunicarse de inmediato?",
            ),
        ),
        guide(
            EducationTopic.OBS_024_FEVER_CHILLS, "OBS-024", "Fiebre o escalofríos",
            "Registrar qué se observó y desde cuándo ayuda a comunicar un cambio general sin inventar una temperatura no medida.",
            "La sensación de fiebre o los escalofríos pueden acompañar infecciones, inflamación, exposición al ambiente u otras causas.",
            listOf("Sentirse caliente no equivale a una temperatura numérica.", "Síntomas acompañantes y evolución orientan la valoración profesional."),
            "Si un cambio importante progresa sin atención, puede retrasarse la identificación de una enfermedad que requiera tratamiento.",
            listOf("Sensación de calor, escalofríos y momento de inicio.", "Temperatura solo si realmente fue medida.", "Tos, dificultad respiratoria, dolor, cambios urinarios, herida o confusión."),
            "Consulta si persiste o se acompaña de otros síntomas. Dificultad para respirar, confusión, rigidez de cuello, desmayo o deterioro marcado requieren atención urgente.",
            listOf(EducationTopic.OBS_013_PAINFUL_URINATION, EducationTopic.OBS_025_SKIN_CHANGES, EducationTopic.OBS_020_CONFUSION_MEMORY),
            medlineFever, listOf(medlineConfusion, medlineChills2026, medlineEmergency2026), "fiebre calentura escalofrio tiembla temperatura caliente", contentVersion = "1.1",
            details = listOf(
                "La fiebre es una elevación de la temperatura corporal. Los escalofríos pueden sentirse como frío con temblores producidos por contracciones rápidas de los músculos; pueden acompañar una fiebre, pero sentir escalofríos no aporta por sí solo una cifra de temperatura.",
                "Si se tomó una lectura real con termómetro, conserva el valor, la unidad y la hora; si no se midió, describe que la persona se sentía caliente o tenía escalofríos sin inventar un número. Comunica cuándo comenzó y si hay tos, dolor, cambios urinarios o una herida.",
                "Existen distintas causas posibles, incluidas infecciones y otras condiciones. Una sensación o una cifra aislada no permite a Bitavida establecer su origen. La dificultad para respirar, la confusión repentina o la rigidez de cuello requieren atención urgente, sin retrasarla para registrar.",
            ),
        ),
        guide(
            EducationTopic.OBS_025_SKIN_CHANGES, "OBS-025", "Cambios en la piel fuera de los pies",
            "Registrar zona, aspecto y evolución ayuda a comunicar irritación, enrojecimiento, secreción u otros cambios visibles.",
            "Los cambios de piel pueden relacionarse con roce, humedad, alergias, lesiones, infecciones u otras causas.",
            listOf("Una fotografía documenta el aspecto, pero no permite diagnosticarlo.", "Dolor, calor, secreción, extensión o fiebre aportan contexto."),
            "Una lesión puede extenderse o complicarse si se retrasa la atención que necesita.",
            listOf("Zona, tamaño aproximado, color y fecha.", "Dolor, picazón, calor, secreción o herida.", "Si se extiende, reaparece o coincide con fiebre."),
            "Consulta si persiste, empeora, duele o presenta secreción. Extensión rápida, fiebre, ampollas extensas, dificultad para respirar o hinchazón de cara requieren atención urgente.",
            listOf(EducationTopic.OBS_002_WOUNDS, EducationTopic.OBS_005_COLOR_TEMPERATURE, EducationTopic.OBS_024_FEVER_CHILLS),
            niamsSkin2026, listOf(nhsHives2026, nhsSevereRash2026), "piel roncha irritacion rojo secrecion sarpullido lesion cuerpo", contentVersion = "1.1",
            details = listOf(
                "Una roncha elevada, piel reseca o descamada y una lesión abierta pueden tener aspectos distintos. Describe qué nota la persona y en qué zona: si pica o duele, si hay ampollas o secreción y desde cuándo aparece. El aspecto visible por sí solo no identifica el problema.",
                "El roce, la humedad, el contacto con productos y diferentes problemas de salud pueden asociarse con cambios de piel. No basta una fotografía para determinar la causa ni para afirmar que hay una infección. La foto es opcional y no debe retrasar la atención.",
                "Una roncha puede verse elevada; una ampolla parece una pequeña bolsa de líquido y la piel reseca puede mostrar grietas o descamación. El cambio de color puede verse distinto según el tono de piel: describe cómo se compara con la piel habitual de la persona, sin depender solo de la palabra «rojo».",
                "Al hablar con el profesional, puedes mencionar si empezó en un lugar donde hubo roce o contacto con un producto y si apareció de inmediato o con el paso de los días. Pregunta: ¿qué características conviene vigilar?, ¿necesita revisarse presencialmente? y ¿a quién avisamos si cambia? Estas preguntas no indican que ya conozcamos la causa.",
                "Comenta al equipo si el cambio se extiende, reaparece o coincide con fiebre. Si además hay dificultad para respirar, estrechez de garganta o hinchazón de la cara, busca atención urgente. No cambies ni suspendas medicamentos por cuenta propia.",
            ),
        ),
        guide(
            EducationTopic.OBS_026_MOUTH_GUMS, "OBS-026", "Cambios en boca o encías",
            "Observar sangrado, inflamación, dolor, sequedad o cambios al masticar ayuda a comunicar problemas que pueden afectar alimentación y bienestar.",
            "Las personas con diabetes pueden tener mayor probabilidad de problemas de encías; también existen muchas otras causas de dolor, sangrado o boca seca.",
            listOf("No todo sangrado significa la misma enfermedad.", "Una prótesis incómoda, sequedad o lesión también son datos útiles."),
            "Si un problema persiste, puede aumentar dolor, dificultar la alimentación o retrasar atención dental.",
            listOf("Zona, sangrado, inflamación, dolor o manchas.", "Fecha de inicio, mal sabor, sequedad y dificultad para masticar.", "Prótesis, lesión visible y cambios en apetito."),
            "Coméntalo con el equipo de salud o dentista. La hinchazón rápida de cara, labios o lengua, la dificultad para respirar o tragar, o un sangrado importante requieren atención urgente.",
            listOf(EducationTopic.OBS_018_APPETITE, EducationTopic.OBS_010_THIRST_DRY_MOUTH),
            nidcrOral2026, listOf(medlineDryMouth2026, medlineAnaphylaxis2026), "boca encia sangra diente dolor inflamado masticar reseca", contentVersion = "1.1",
            details = listOf(
                "La boca seca, una llaga, encías que sangran y dolor al masticar son cambios diferentes. La saliva mantiene húmeda la boca y ayuda a masticar y tragar; si falta, la persona puede notar sequedad o incomodidad. Pregúntale qué siente sin asumir que todos los cambios tienen el mismo origen.",
                "Describe la zona, cuándo comenzó, si aparece sangrado o alguna mancha y si dificulta comer, hablar o usar una prótesis. La diabetes puede relacionarse con problemas de boca y encías, pero una observación no confirma una infección ni permite conocer la causa.",
                "El equipo de salud o dentista puede revisar el cambio y las molestias de una prótesis. Si hay dificultad para respirar o tragar o hinchazón rápida de boca o cara, busca atención urgente sin esperar a completar un registro.",
            ),
        ),
        guide(
            EducationTopic.OBS_027_OTHER_CHANGE, "OBS-027", "Otro cambio observado",
            "Esta opción permite documentar un hecho que no coincide con el catálogo, sin convertirlo en una categoría nueva ni en un diagnóstico.",
            "Un cambio general puede tener muchas explicaciones. Describir hechos, tiempo y contexto ayuda al personal de salud a decidir qué información es relevante.",
            listOf("Bitavida no interpreta el texto ni determina gravedad.", "Si existe una opción específica del catálogo, suele ofrecer preguntas más útiles."),
            "No comunicar un cambio relevante puede retrasar su valoración; no registrarlo, por sí mismo, no causa una enfermedad.",
            listOf("Qué viste, escuchaste o refirió la persona.", "Fecha, periodo aproximado, zona y repetición.", "Cambios acompañantes y fotografía solo si aporta contexto."),
            "Comenta cambios nuevos, persistentes o preocupantes. Ante dificultad para respirar, dolor torácico intenso, pérdida del conocimiento, confusión o debilidad repentinas, busca atención urgente sin esperar a registrar.",
            listOf(EducationTopic.OBSERVATION_GENERAL, EducationTopic.OBSERVATION_APP_USE),
            bitavida, listOf(cdcStroke, medlineEmergency2026), "otro cambio no encuentro categoria describir observacion general", contentVersion = "1.1",
            details = listOf(
                "«Otro cambio» sirve cuando ninguna opción del catálogo describe bien lo que notaste. Puedes contar qué dijo la persona o qué viste, con sus propias palabras, sin forzar una categoría ni convertir una sospecha en diagnóstico.",
                "Si lo sabes, anota cuándo comenzó, dónde ocurre, si se repite y qué actividad afecta. Distingue una hora observada de una aproximada y deja sin completar lo que no conoces. Una foto es opcional y documenta el aspecto, pero Bitavida no la interpreta.",
                "Comenta al equipo los cambios nuevos o persistentes y pregunta cuándo necesitan valoración. Si hay dificultad respiratoria, pérdida de conocimiento o cambios neurológicos repentinos, busca ayuda urgente primero: el formulario no es un servicio de emergencias.",
            ),
        ),
    ).associateBy(EducationEntry::topic)

    private fun general() = EducationEntry(
        topic = EducationTopic.OBSERVATION_GENERAL,
        title = "Observar pequeños cambios ayuda a comunicar mejor",
        explanation = "Registrar hechos cotidianos permite recordar qué ocurrió, cuándo y cómo cambió. No observar algo no causa una enfermedad; el riesgo es que una señal relevante tarde más en identificarse y comentarse.",
        example = "Puedes anotar «Hoy necesitó apoyo para caminar» sin afirmar por qué ocurrió.",
        related = listOf(EducationTopic.OBS_001_FEET_REVIEW, EducationTopic.OBS_022_FALL, EducationTopic.OBS_020_CONFUSION_MEMORY),
        source = bitavida,
        importance = "Una descripción clara puede ayudar a preparar la siguiente consulta y conservar la versión declarada por la persona cuidadora.",
        version = "1.1",
        details = listOf(
            "Antes de una consulta, conversa con la persona sobre qué cambios quiere comentar y cuáles son sus preguntas. Puedes revisar el historial para recordar qué notaste, cuándo ocurrió, si se repitió y cómo afectó las actividades habituales. La persona puede explicar su propia experiencia; acompáñala de la manera que acuerden.",
            "Puedes consultar las observaciones registradas en su historial y revisar en Informe la información disponible del periodo elegido. El informe describe registros; no interpreta su causa ni sustituye la conversación médica. Compartir un archivo requiere una acción explícita y atención a la información sensible.",
            "Es útil distinguir lo que viste o escuchaste de lo que alguien te explicó: «noté una hinchazón ayer» describe un hecho, mientras que atribuirle una causa requiere valoración. Si no conoces la hora o el origen, comunícalo así en vez de completarlo con una suposición.",
            "Ante una señal urgente, busca atención primero; no esperes a terminar de escribir, preparar un informe o conseguir una fotografía.",
        ),
        additionalSources = listOf(medlineVisit2026),
        concepts = listOf("Describe hechos, no diagnósticos.", "Registra el momento real cuando se conozca.", "La ausencia de registro no significa ausencia de síntomas."),
        possibleMeanings = listOf("El mismo cambio puede relacionarse con situaciones distintas; Bitavida no elige una causa."),
        delayedAttention = "El riesgo educativo es retrasar la identificación y atención de una situación relevante, no causar directamente una enfermedad.",
        whatToObserve = listOf("Qué cambió.", "Cuándo ocurrió.", "Si se repitió y qué actividades afectó."),
        medicalGuidance = "Consulta al equipo de salud ante cambios nuevos, persistentes o preocupantes. Usa los mensajes de seguridad fijos ante señales urgentes.",
        searchTerms = listOf("importancia observar ignorar cambios salud riesgos señales"),
    )

    private fun appUse() = EducationEntry(
        topic = EducationTopic.OBSERVATION_APP_USE,
        title = "Observaciones organiza hechos declarados",
        explanation = "El catálogo ofrece 27 formas guiadas de registrar cambios espontáneos o revisiones programadas. Todas alimentan un historial global.",
        example = "Una revisión adicional no reemplaza automáticamente una revisión programada sin confirmar.",
        related = listOf(EducationTopic.OBSERVATION_GENERAL, EducationTopic.OBS_027_OTHER_CHANGE),
        source = bitavida,
        importance = "Mantiene separados los hechos observados, la indicación transcrita y el contenido educativo.",
        version = "1.1",
        details = listOf(
            "Una observación espontánea documenta un cambio que decides registrar cuando lo notas; no necesitas configurar primero un seguimiento. Selecciona el tipo correspondiente, describe lo observado y conserva la fecha y hora reales que conozcas antes de confirmar el registro.",
            "Una revisión programada pertenece a un seguimiento configurado para un horario. Al abrir esa revisión, puedes declarar lo que observaste o indicar que no se realizó. Una observación espontánea cercana conserva su propia identidad y no resuelve automáticamente la revisión programada.",
            "«Sin confirmar» significa que aún no se ha declarado qué pasó con esa revisión; no demuestra que hubiera o no un cambio. «No realizada» es una declaración explícita y tampoco significa que la persona estuviera sin cambios. Si corriges un registro, se conserva la versión anterior.",
            "Al confirmar «Eliminar seguimiento» dejan de aparecer sus próximas revisiones y avisos; las observaciones y el historial permanecen guardados. Puedes consultar el historial para preparar preguntas y revisar la información disponible en Informe.",
            "Guías y ayuda permite aprender sin crear un registro. La IA local clasifica la pregunta y recupera contenido educativo; no consulta datos personales ni fotografías para decidir qué sucede con la persona.",
        ),
        concepts = listOf("Sin confirmar no significa realizada.", "No realizada no significa sin cambios.", "Las correcciones conservan la versión anterior."),
        searchTerms = listOf("usar modulo observaciones historial seguimiento bitavida"),
    )

    private fun guide(
        topic: EducationTopic,
        observationId: String,
        title: String,
        explanation: String,
        importance: String,
        possibleMeanings: List<String>,
        delayedAttention: String,
        whatToObserve: List<String>,
        medicalGuidance: String,
        related: List<EducationTopic>,
        source: EducationSource,
        additionalSources: List<EducationSource>,
        searchTerms: String,
        contentVersion: String = "1.0",
        details: List<String> = emptyList(),
    ) = EducationEntry(
        topic = topic,
        title = title,
        explanation = explanation,
        example = null,
        related = related,
        source = source,
        version = contentVersion,
        importance = importance,
        observationId = observationId,
        possibleMeanings = possibleMeanings,
        delayedAttention = delayedAttention,
        details = details,
        whatToObserve = whatToObserve,
        medicalGuidance = medicalGuidance,
        additionalSources = additionalSources,
        searchTerms = searchTerms.split(" "),
    )

    // Legacy references lack a documented, individual consultation date.
    private fun source(title: String, reference: String) =
        EducationSource(title, reference)
}
