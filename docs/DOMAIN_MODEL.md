# Modelo de dominio

## Consultas declaradas y seguimiento

CareEventDraft conserva `consultationType` (sin especificar, medicina familiar, medicina general, especialidad u otra consulta) e `isFollowUp` como declaración explícita. El subtipo sin especificar representa citas anteriores ambiguas y nunca determina un intervalo. Estudios y procedimientos no se convierten en consultas; únicamente consultas declaradas y marcadas para seguimiento pueden delimitar periodos. La aclaración explícita de una cita histórica preserva el acontecimiento y sus revisiones anteriores.

## Límites de texto de cuidado (vigente)

La captura nueva usa grafemas Unicode y los máximos compartidos de CaptureLimit: títulos 100; detalles de Recordatorios, descripciones de Observaciones y notas de Mediciones 500; contenido de Notas 2000; motivos 300; respuestas cortas y búsquedas 100. Las indicaciones transcritas mantienen 1000 por su propósito. Las preguntas educativas mantienen su máximo propio de 500. Fechas, horarios, catálogos y números conservan sus reglas.

Las lecturas históricas no se limitan ni transforman. Crear, reemplazar o duplicar exige la política actual; editar otro dato puede conservar íntegro un texto anterior idéntico, contrastado contra el registro autorizado. Las revisiones previas se conservan. Observaciones valida además la capacidad del payload existente antes de codificar. Inventario completo en TEXT_INPUT_LIMITS.md.


## Notas y contadores de Plan (vigente)

`Note` conserva ID, paciente, título obligatorio (hasta 100 grafemas), contenido opcional (hasta 2000), ID de una imagen opcional, creación, modificación, revisión y eliminación lógica. No tiene programación, cumplimiento, prioridad, recurrencia ni asistencia de IA. `NoteDraft` no persiste hasta Guardar; editar conserva la identidad y eliminar exige la confirmación de la persona. Operación idempotente, revisión esperada y autorización de la relación se validan en transacción; las instantáneas anteriores se conservan.

`legacyIndicationId` identifica copias de la migración, nunca vincula una nota a tratamiento ni modifica la indicación original. El texto conservado mantiene su procedencia de indicación médica transcrita; apuntes nuevos son texto declarado libre. El registro original y sus revisiones clínicas permanecen intactos.

Los cuatro contadores de Plan son proyecciones de vigencia: identidades de medicamento actuales; tipos de medición con seguimiento vigente; tipos de observación con revisión programada vigente; pendientes de Recordatorios del paciente, incluidos los pasados. No son estadísticas de cumplimiento ni cuentan resultados individuales.

Recordatorios conserva Pendiente/Hecho/Descartado, límite de diez, fecha/hora única, operaciones explícitas e historial. La preferencia de aviso queda únicamente por compatibilidad de datos; el módulo ya no genera avisos Android ni cambios de estado desde notificaciones.


## Presentación y asistencia de Recordatorios

`CareReminderTemporalGroup` distingue TODAY, UPCOMING y PAST solo como proyección de pendientes y reloj: pasar una hora nunca completa ni descarta. Los grupos vacíos se omiten y cada grupo conserva orden cronológico. El carrusel prioriza hoy, fechas futuras y pendientes anteriores. Las opciones de posposición añaden quince minutos, una hora o un día al momento real de la acción; elegir fecha utiliza la misma validación futura.

`similarActiveCareReminders` compara únicamente títulos declarados del mismo paciente, excluye el propio registro editado y todos los cerrados. La aproximación produce una advertencia, nunca una identidad, fusión o bloqueo definitivo. Duplicar crea un borrador con otro ID, título/detalles copiados y aviso desactivado; solo Guardar persiste.

`ReminderEducationCategory` organiza la biblioteca en siete áreas, sin categorizar los registros. `ReminderSuggestion` conserva título y explicación editorial. `ReminderTitleAssistance` representa una redacción opcional, hasta tres ideas y temas relacionados; ningún campo incluye fecha, paciente, orden médica ni una operación de guardado. La persona elige una propuesta y confirma los datos del nuevo recordatorio.


## Recordatorios del cuidado

`CareReminder` es un pendiente cotidiano libre, asociado a una persona atendida, independiente de actividades médicas. Conserva identidad estable, título, detalles opcionales, programación única, preferencia de aviso, estado PENDING/DONE/DISCARDED, creación, modificación, cierre y revisión. La fecha originalmente programada se conserva para anteriores y cada modificación anexa una instantánea técnica con actor e identidad de operación. No hay recurrencias, clasificación automática ni estado persistente de atraso.

Crear exige título y fecha/hora futura, con máximo diez pendientes por paciente. Posponer conserva identidad y exige futuro. Editar conserva el horario pasado si no se cambia; cambiarlo exige futuro. Completar o descartar exige acción explícita, registra cierre y libera espacio. Ningún reloj, aviso o ausencia cambia el estado. Los anteriores se ordenan por cierre descendente; crear otro igual inicia identidad y programación nuevas. El límite, autorización, revisión e idempotencia se validan dentro de la transacción de persistencia.


## Observaciones unificadas

`EducationGroup.OBSERVATIONS` incorpora dos temas generales y 27 temas vinculados mediante `EducationEntry.observationId`. Cada ficha conserva importancia, posibles significados generales, riesgo de retrasar la identificación, datos observables, orientación profesional, relaciones y una o más fuentes. Es información educativa verificada pendiente de revisión clínica humana: no consume `ObservationRecord`, fotografías, perfil ni mediciones; tampoco determina gravedad, diagnóstico o tratamiento. El clasificador local resuelve intención y la recuperación muestra contenido estático versionado.


La biblioteca abre un detalle por identificador. Los formularios especializados distinguen campos esenciales y complementarios, sin modificar las reglas de dominio. `ObservationGuideCatalog` asocia una guía educativa local distinta a cada OBS-001…OBS-027: hechos observables, ejemplos y datos útiles, sin causas ni diagnósticos. `buildObservationDescription` solo utiliza opciones válidas y texto declarado: propone una redacción sin inferir causas ni datos faltantes, y no modifica ni guarda el registro por sí sola. La comparación con la captura anterior es opcional, solo aparece si existe un registro previo y nunca establece que se trate del mismo episodio.

El alcance aprobado posterior incorpora `ObservationDefinition` (catálogo cerrado OBS-001…OBS-027), `ObservationField`, ocho `ObservationCategory`, `ObservationRecord` (declaración de la persona cuidadora), `ObservationFollowUp`, `ObservationOccurrence` y `ObservationSnapshot` en Kotlin puro. Catálogo y registros son independientes: todas las opciones están disponibles sin activar seguimiento. `OBS-027` conserva su identidad y nunca crea una categoría.

Los hechos distinguen fecha real, hora exacta/aproximada/desconocida y captura, además de respuestas estructuradas, descripción, fotografías opacas y asociación opcional explícita con seguimiento/ocurrencia/fecha programada. Una revisión distingue cambios, ausencia declarada de cambios y no realizada; una caída es un acontecimiento y no ofrece ausencia de caída. La validación exige información concreta o descripción, rechaza hechos futuros y no fabrica tiempos ni resultados. Guardar un hecho espontáneo no crea programación. Los hechos originales son inmutables. El historial abre un detalle informativo; la acción específica Corregir exige motivo y anexa `ObservationRecordRevision` con instantánea completa, actor, captura e identidad de operación idempotente. La asociación con paciente/tipo/ocurrencia y el momento de creación original permanecen fijos. `ObservationSnapshot.records` contiene solo la versión vigente y `recordRevisions` conserva original y correcciones. No hay eliminación silenciosa.

Los seguimientos reutilizan el agregado puro versionado de Mediciones con frecuencia, días, intervalo, horarios, ventanas y preferencia de avisos. En Observaciones no hay fechas configurables: la confirmación activa la nueva revisión desde su captura y permanece vigente hasta una eliminación explícita con motivo obligatorio. Eliminar anexa una revisión `REMOVED`, conserva todas las revisiones y hechos anteriores y excluye programación y avisos futuros. Una configuración posterior parte de la última indicación, frecuencia, horarios y avisos como plantilla editable, pero solo crea actividad al confirmarse; no existe reactivación implícita. Las fechas efectivas internas y las fechas de inicio/finalización históricas permanecen en sus revisiones inmutables; capturas tardías no generan actividades retroactivas. Cambiar solo texto o avisos conserva el ancla de intervalo ya establecida y las identidades de horarios. Los eventos se identifican por seguimiento, horario y geometría temporal. `projectObservationTimeline` usa la fecha real y un filtro opcional por identificador exacto, además de categoría; la ocurrencia asociada desaparece de su fecha programada incluso si el hecho se declaró en otra fecha. El historial desde el detalle conserva ese identificador y comienza en la fecha de su último registro; desde la biblioteca comienza con todas las observaciones. Sin registro conserva incertidumbre y nunca se presenta como sin síntomas ni como incumplimiento.


## Estado

La procedencia, el perfil y el primer agregado de Plan están implementados en Kotlin puro. El flujo conserva un único `CareProfile` cifrado y permite trabajar únicamente con indicaciones y medicamentos ficticios de desarrollo mientras falten consentimiento sensible y autorización por operación. La captura clínica real continúa bloqueada.

## Procedencia obligatoria

Toda entrada debe distinguir una de estas fuentes:

- **Indicación médica transcrita**: reproducción fiel de una instrucción externa, con referencia y revisión humana.
- **Registro declarado por cuidador**: hecho u observación capturada por una persona.
- **Información educativa verificada**: contenido general curado, con fuente, versión, licencia y revisión.

La procedencia no se infiere por el texto ni puede cambiarse automáticamente.

## Agregados previstos

- **CareProfile**: agregado local cifrado entre cuidador, persona atendida, relación, autorización declarada y decisiones legales; su uso es `PERSISTED_ENCRYPTED_LOCAL`.
- **PersonUnderCare**: identidad declarada por el cuidador, con identificador local estable y nombre normalizado.
- **Caregiver**: identidad de quien realiza la declaración, con identificador local estable y nombre normalizado.
- **ConsentRecord**: distingue el tipo `CAREGIVER_AUTHORIZATION_DECLARATION`, su estado `ACTIVE` o `REVOKED` y el instante correspondiente; no acredita representación legal.
- **LegalAcceptanceRecord**: registra actor, documento, versión, decisión, estado y momento. Aceptar términos, reconocer el aviso, declarar autorización y consentir datos sensibles son actos diferentes; esta fase no crea consentimiento sensible.
- **MedicalIndication**: identificador estable, persona atendida, procedencia médica transcrita, autor, fecha de creación y revisión vigente.
- **MedicalIndicationRevision**: instantánea append-only con texto original, fecha indicada por el cuidador, captura real, fuente opcional, vigencia, estado, autor, motivo y tipo de cambio.
- **MedicationPlan**: un periodo de tratamiento versionado vinculado a una indicación y a una identidad de serie estable, con nombre, dosis textual, unidad/presentación, frecuencia, vigencia e instrucciones originales; nunca calcula ni completa valores. Un nuevo periodo recibe otro identificador y conserva intactos calendario, revisiones y tomas de los periodos anteriores de la misma serie. La identidad operativa es única por persona y nombre normalizado exacto (Unicode, mayúsculas y espacios); no usa semejanza nominal. Los duplicados históricos inequívocos se agrupan al leer sin reescribir sus identificadores, mientras que cualquier correspondencia ambigua exige revisión humana.
- **ScheduledDose**: horario local configurado expresamente por la persona cuidadora para una revisión del plan; no constituye recordatorio ni evidencia de toma. Cada fila versionada tiene una clave propia y conserva por separado la identidad lógica de la administración para poder compararla y enlazarla entre revisiones sin colisiones.
- **MedicationIntakeRecord**: toma declarada vinculada a un medicamento, con identidad estable y revisiones append-only; conserva resultado, horario programado, hora real opcional, cantidad real opcional, observación, autor y captura. Cada revisión puede conservar la identidad de su ocurrencia programada; NULL indica que el registro anterior no tenía ese vínculo y no permite inventar asociaciones entre administraciones coincidentes.
- **CareRecord**: alimento, eliminación, observación o medición no modelada como toma de medicamento.
- **Appointment** y **Study**: agenda y seguimiento administrativo.
- **Attachment**: metadatos de un archivo protegido.
- **AuditEntry**: actor, operación, fecha, motivo y relación con versiones.
- **ReportDraft**: selección revisable antes de exportar.
- **EducationalEntry**: ficha curada enlazada por identificador verificado.

Los nombres y campos finales permanecen sujetos a validación funcional y profesional.

## Educación en Bitavida IA

`EducationTopic` identifica 30 temas cerrados y `EducationGroup` organiza Glucosa, Presión, Peso y Uso de Bitavida. `EducationEntry` conserva título, explicación breve, ejemplo opcional, importancia, desarrollo complementario, conceptos, temas relacionados por identificador exacto, referencia, versión y estado de revisión. Las fichas mantienen `PENDING_HUMAN_REVIEW` y se muestran como pendientes de revisión clínica; no constituyen información médicamente verificada ni indicaciones transcritas.

`IntentPrediction` representa una clasificación estadística con rechazo y posibles temas cercanos. Sus probabilidades no son una valoración clínica. `EducationResult` distingue la ficha elegida de un fallback por incertidumbre, alcance o indisponibilidad; el fallback puede ofrecer identificadores educativos para que la persona elija. La guardia de alcance se evalúa antes de inferir y no constituye un detector clínico exhaustivo.

La biblioteca y las preguntas relacionadas se abren mediante una acción explícita y un identificador exacto, sin requerir otra inferencia. La clasificación y la consulta directa permanecen diferenciadas en la presentación. Ningún resultado contiene datos de pacientes, modifica registros, infiere cumplimiento ni crea propuestas de tratamiento. La pila de vistas, búsquedas, filtros, desplazamientos y apartados expandidos pertenecen al estado efímero de UI; no añaden entidades persistidas ni alteran los agregados de Mediciones.

`MEDICATIONS` añade diez intenciones generales al mismo clasificador, sin reemplazar los 30 temas originales. `DrugEducation` identifica un principio activo o una formulación documentada; conserva familia, alias genéricos verificados, resumen, secciones por `DrugIntent`, referencias con institución/documento/URL/versión/consulta, estado documental y disponibilidad mexicana no verificada. Efectos frecuentes y señales importantes permanecen diferenciados; ninguna ficha implica validación clínica humana ni valoración individual.

`DrugNameResolver` produce identidades exactas u opciones por confirmar, nunca asociaciones clínicas por semejanza nominal. La consulta de un nombre registrado utiliza igualdad normalizada de un alias curado; las formulaciones ambiguas exigen selección. `DrugAnswer` y `DrugOptions` son resultados educativos sin cambios de tratamiento ni evaluación automática de interacciones. El catálogo no introduce entidades persistidas ni relaciones nuevas con planes clínicos.

## Invariantes

- No diagnosticar, interpretar mediciones, calcular dosis ni recomendar tratamientos.
- Nunca deducir cumplimiento a partir de una notificación.
- Una toma es un registro declarado; debe conservar autor y fecha.
- Los nombres requieren al menos 2 letras significativas y como máximo 80 grafemas; admiten letras Unicode, espacios, apóstrofos y guiones. Los espacios se normalizan solo al confirmar y nunca se aceptan controles, saltos de línea o formatos invisibles.
- Autocompletar medicamentos solo desde nombres previamente registrados y nunca completar dosis, unidad u horario.
- Las correcciones crean una nueva revisión y conservan la anterior.
- Las operaciones sensibles requieren autorización y auditoría.
- La asistencia propone; una persona confirma antes de cualquier cambio.
- Las fichas educativas se enlazan por identificador exacto verificado, no por similitud nominal.
- OCR se limita a comprobantes administrativos.
- Exportación y compartición requieren acción explícita y advertencia de sensibilidad.

## Estados y transiciones

El consentimiento y las aceptaciones legales distinguen `ACTIVE` y `REVOKED`; un registro revocado conserva el instante de revocación y uno vigente no lo presenta. La creación inicial produce autorización declarada vigente, aceptación de términos y reconocimiento del aviso solo después de decisiones explícitas. La revocación todavía no tiene interfaz operable; si se encuentra en almacenamiento, las operaciones que la requieran se restringen sin invalidar todo el perfil. `MedicalIndicationStatus` distingue `ACTIVE`, `SUSPENDED` y `COMPLETED`. Cada alta, corrección o cambio de estado crea una revisión numerada e inmutable; suspender, reactivar, reprogramar o cancelar una reactivación son transiciones explícitas con instante de captura e instante efectivo, por lo que varios periodos activos y suspendidos se reconstruyen sin sobrescribir revisiones. Una reactivación futura mantiene el estado suspendido hasta su instante efectivo y puede sustituirse o cancelarse mediante otra revisión auditable. La vigencia final es inclusiva y el periodo pasa a `COMPLETED` al comenzar el día local posterior, con motivo administrativo «Vigencia concluida». Un periodo finalizado no se reabre: una nueva indicación crea otro `MedicationPlan` dentro de la misma serie. El plan de medicamento se corrige mediante una nueva revisión y nuevos horarios explícitos, conservando todos los anteriores. “Pendiente”, “registrado” o “corregido” describen el dato; no valoran la salud ni el desempeño de la persona cuidadora.

## Identidad, tiempo y versiones

Las entidades usarán identificadores locales estables. Los hechos conservarán tiempo declarado y tiempo de captura cuando difieran. Las revisiones serán inmutables y se relacionarán con su predecesora; eliminar una vista no eliminará el historial auditable.

## Proyección de Mediciones

La pantalla principal incorpora un catálogo fijo con Glucosa capilar, Presión arterial y Peso, independiente del estado del seguimiento. `MeasurementRecord` es el contrato de lectura para presentar valores transcritos, momento del acontecimiento y momento de captura; las capturas de glucosa, presión arterial y peso se integran mediante casos de uso y repositorios cifrados independientes. La proyección selecciona la última lectura por fecha del acontecimiento y cuenta registros semanales sin estados de cumplimiento. Glucosa conserva contexto opcional e indicadores HI/LO; presión conserva sistólica y diastólica como valores relacionados de una misma lectura y admite pulso declarado cuando exista; peso solo presenta IMC cuando dispone de peso y altura positivos, sin categoría clínica.

`projectMeasurementHistoryWeek` selecciona exclusivamente el tipo y la semana elegidos por fecha del acontecimiento, conservando la fecha de captura por separado. Proyecta cada lectura numérica como un punto sin promediar, clasificar ni interpolar; presión conserva dos series explícitas (sistólica/diastólica) y el pulso no se mezcla con ellas. Peso grafica kilogramos, sin mezclar el IMC. HI/LO permanecen como registros visibles sin fabricar coordenadas numéricas. Los estados futuros se comparan con la fecha local actual, no con el día elegido en el calendario.

`MeasurementFollowUp` admite frecuencia, periodicidad, horarios y estado de recordatorios declarados opcionales; los momentos solo se proyectan en los tipos que los utilizan. Los datos ausentes no se reconstruyen desde el resumen ni implican que los recordatorios estén activos. `projectGlucoseWeekPlot` conserva un punto por lectura numérica y posiciones separadas para eventos coincidentes; su escala redondeada se obtiene de datos y, solo si se recibe un `GlucoseTargetRange` explícito, también contiene esas referencias médicas sin interpretarlas. `projectGlucoseHistoryPeriod` proyecta semana o mes, agrupa por fecha real y calcula promedio, mínimo, máximo y total; HI/LO se cuentan pero se excluyen de cálculos numéricos. El porcentaje en meta solo existe con rango explícito.

Glucosa, Presión arterial y Peso cuentan con repositorios cifrados, revisiones append-only y seguimientos persistidos independientes. Las tres reutilizan el modelo versionado de seguimiento: estado activo/desactivado, frecuencia, horarios y hora de aviso. Glucosa y Presión añaden un momento sugerido; Peso no lo solicita ni lo presenta. Cada horario declara explícitamente hora exacta o ventana; una ventana conserva inicio, fin, cruce opcional al día siguiente y un aviso contenido en ese rango. Las ocurrencias se identifican por su inicio y preservan el final de ventana para presentar y asociar el registro sin fabricar mediciones. No existe vigencia configurable; la fecha efectiva interna separa periodos auditables y evita cambios retroactivos. La preferencia de notificaciones no modifica la programación. Las decisiones de ocurrencia registran únicamente declaraciones explícitas y sus revisiones conservan cada corrección de forma append-only. Corregir una declaración «No se realizó» a realizada crea una lectura real vinculada a la misma ocurrencia y anexa `CORRECTED_TO_RECORDED` a la auditoría; ambas escrituras son atómicas e idempotentes, sin borrar la declaración anterior. Las proyecciones de hoy e historial distinguen registrada, sin confirmar y no realizada. Las ocurrencias pasadas sin lectura ni decisión se reconstruyen desde la revisión histórica aplicable como «Sin confirmar», nunca como omisión; las lecturas adicionales permanecen fuera de las ocurrencias programadas y solo los valores reales participan en gráficas y estadísticas. Las secundarias requieren un catálogo cerrado autorizado y selección explícita.

La identidad normalizada de una ocurrencia incluye seguimiento, identificador lógico del horario, fecha/hora de inicio, modo exacto/ventana y final de ventana. Las referencias físicas anteriores que incluyen el identificador de revisión se conservan y se resuelven exactamente contra sus instantáneas. Cambiar la indicación, el momento sugerido o los avisos sin cambiar el horario no crea otro evento. La programación implícita requiere una revisión vigente al comenzar el horario, después de su fecha efectiva y de su captura; una activación tardía no crea pendientes retrospectivos. Desactivar conserva plazos ya transcurridos y los hechos explícitos, pero cancela horarios futuros y ventanas aún abiertas.

`projectMeasurementOccurrences` aplica una única precedencia: lectura asociada, declaración explícita vigente y programación histórica válida. `projectMeasurementHistoryEvents` alimenta los historiales individuales y el general con los mismos acontecimientos. Todas las lecturas auténticas permanecen individuales, incluso si existen varias referencias heredadas a un mismo horario; esta normalización no elimina datos ni sustituye una revisión humana de posibles duplicados persistidos.

## Captura contextual de Glucosa

GlucoseDraft conserva valor transcrito, momento real obligatorio (en ayunas, antes/después de comer, antes de dormir u otro), fecha/hora del acontecimiento y nota opcional de hasta 300 caracteres. Solo valida formato y fecha no futura; no prescribe límites clínicos. HI/LO se conservan literalmente; el separador decimal coma se interpreta para graficar sin reescribir la transcripción.

GlucoseRegistrationContext distingue Independent, Scheduled y Additional. Scheduled exige una ocurrencia confirmada explícita perteneciente al seguimiento activo; conserva identificador y hora esperada, y contexto esperado separados de los reales, siempre editables. Additional nunca ocupa una ocurrencia programada. Un seguimiento suspendido permite captura independiente. No se generan horarios desde el resumen textual.

GlucoseRecordMetadata conserva el tipo de entrada, referencia de seguimiento/ocurrencia opcional y nota. El repositorio añade actor y procedencia declarada por cuidador. Guardar comprueba autorización declarada y consentimiento sensible vigente local-health-storage-1.0; crea lectura, auditoría y revisión transaccionalmente. Un reintento con el mismo identificador es idempotente; una ocurrencia ya registrada devuelve el hecho existente y requiere decisión explícita para conservar el nuevo borrador como adicional. Corregir crea una nueva `GlucoseRevision` con valor, contexto, fecha/hora y nota completos; eliminar marca lógicamente la lectura, anexa otra revisión y la excluye de proyecciones activas sin destruir trazabilidad.

## Captura contextual de Presión arterial

`BloodPressureDraft` conserva sistólica y diastólica transcritas en una misma operación, momento real obligatorio, fecha/hora del acontecimiento y nota opcional. Su catálogo de momentos incluye los contextos compartidos con Glucosa y añade exclusivamente «En reposo» y «Después de actividad física»; los casos de uso validan el catálogo correspondiente para impedir que estas extensiones se filtren al registro o seguimiento de Glucosa. Solo valida que ambos valores sean numéricos, estén completos y que la fecha no sea futura; no interpreta rangos ni clasifica la lectura. `BloodPressureRegistrationContext` distingue registro independiente, programado y adicional con las mismas reglas explícitas de asociación de Glucosa. `BloodPressureRecordMetadata` conserva literalmente procedencia, seguimiento, ocurrencia, momento real y nota. Cada alta, corrección o eliminación lógica anexa una revisión completa; la eliminación excluye la lectura activa sin destruir su trazabilidad.

`projectBloodPressurePlot` conserva cada pareja y calcula una escala dinámica en mmHg; la evaluación semanal puede unir discretamente sistólica y diastólica de la misma lectura sin inventar rangos clínicos. `projectBloodPressureHistoryPeriod` ofrece semana y mes, estadísticas descriptivas separadas para ambas series y eventos programados registrados, sin confirmar o no realizados. Su historial presenta sistólica y diastólica como dos líneas cronológicas independientes con escala compartida, y corta cada línea cuando un día no contiene lecturas para no interpolar periodos vacíos. Solo las parejas reales participan en promedios, mínimos, máximos y gráficas; cada pareja cuenta como una medición.

## Captura de Peso

`WeightDraft` conserva kg decimales positivos, fecha/hora real y nota opcional; no contiene ni valida un momento de medición. `WeightRegistrationContext` distingue registro independiente, programado y adicional con las mismas reglas explícitas de asociación de las otras mediciones. La estatura es opcional y procede únicamente de una fuente previamente válida; no se solicita ni inventa por registro. `bodyMassIndex` devuelve una proyección numérica solo cuando peso y estatura son positivos, sin diagnóstico ni clasificación.

`WeightRecordMetadata` conserva procedencia, seguimiento, ocurrencia y nota. Sus campos contextuales son opcionales exclusivamente para leer revisiones históricas anteriores a esta simplificación; las altas nuevas escriben un marcador técnico sin exponerlo al dominio ni a la UI. Los horarios de Peso conservan hora exacta o ventana y aviso, sin actividad asociada. Cada alta, corrección o eliminación lógica anexa una revisión completa. El historial semanal y mensual utiliza kg como serie única, conserva cambios decimales pequeños y combina lecturas reales con estados programados sin incluir «Sin confirmar» o «No se realizó» en cálculos ni gráficas.

`WeightTarget` representa un único peso objetivo vigente y opcional por persona, transcrito por la persona cuidadora desde la indicación médica. Se persiste como configuración separada sin generar mediciones, eventos ni historial de cambios. Solo amplía la escala y dibuja una referencia visual; queda excluido de promedios, extremos y recuentos.

## Implementado frente a previsto

| Área | Estado |
|---|---|
| Procedencia en Kotlin puro | Implementada |
| “Mi día” con datos ficticios | Implementada en UI; solo nombres y fecha provienen del perfil/localidad |
| `Caregiver`, `PersonUnderCare`, `CareProfile` y `ConsentRecord` | Implementados y persistidos con mapeo explícito |
| `LegalAcceptanceRecord` | Implementado y persistido con versión, decisión, estado y fecha |
| Creación/consulta y repositorio de perfiles | Implementados con transacción cifrada y límite de uno por instalación |
| Indicaciones, revisiones y estados | Implementados solo para contenido ficticio de desarrollo; captura clínica real bloqueada |
| Planes de medicamento y horarios explícitos | Implementados y versionados; sin cálculos ni inferencias de cumplimiento |
| Tomas declaradas | Implementadas con resultado, correcciones justificadas, historial y persistencia cifrada append-only |
| Glucosa capilar | Captura contextual, historial, seguimiento versionado, avisos locales, ocurrencias programadas y decisiones explícitas con persistencia cifrada y auditoría |
| Presión arterial | Captura sistólica/diastólica pareada, evaluación e historial, seguimiento versionado, avisos locales, ocurrencias y decisiones explícitas con persistencia cifrada y auditoría |
| Peso | Captura decimal en kg, IMC opcional con estatura válida, peso objetivo vigente opcional, evaluación e historial, seguimiento versionado, avisos locales, ocurrencias y decisiones explícitas con persistencia cifrada y auditoría de lecturas |
| Auditoría general | Pendiente; las revisiones funcionales conservan autor, motivo y fecha sin borrar versiones |
| Infraestructura cifrada | Integrada hasta esquema Room 18 mediante migraciones explícitas aditivas |
| Exportación y respaldo | Pendientes |
| Asistencia local | Educación offline con Bitavida IA y 30 temas; asistencia de captura y duplicados pendiente |
