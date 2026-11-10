# Modelo de dominio

## Estado

La procedencia de información y la primera rebanada de perfiles están implementadas en Kotlin puro. El flujo crea y persiste de forma cifrada un único `CareProfile` con una persona cuidadora, una persona atendida, su relación, una declaración de autorización y decisiones legales versionadas. No captura ni habilita información clínica real. El resto de este documento define el modelo previsto y sus invariantes.

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
- **MedicalIndication** y sus revisiones: texto transcrito, profesional/fuente declarados y vigencia.
- **MedicationPlan** y **ScheduledDose**: organización de una indicación, nunca cálculo de dosis.
- **CareRecord**: toma declarada, alimento, eliminación, observación o medición.
- **Appointment** y **Study**: agenda y seguimiento administrativo.
- **Attachment**: metadatos de un archivo protegido.
- **AuditEntry**: actor, operación, fecha, motivo y relación con versiones.
- **ReportDraft**: selección revisable antes de exportar.
- **EducationalEntry**: ficha curada enlazada por identificador verificado.

Los nombres y campos finales permanecen sujetos a validación funcional y profesional.

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

El consentimiento y las aceptaciones legales distinguen `ACTIVE` y `REVOKED`; un registro revocado conserva el instante de revocación y uno vigente no lo presenta. La creación inicial produce autorización declarada vigente, aceptación de términos y reconocimiento del aviso solo después de decisiones explícitas. La revocación todavía no tiene interfaz operable; si se encuentra en almacenamiento, las operaciones que la requieran se restringen sin invalidar todo el perfil. Los demás catálogos de estados aún no están implementados. Cada transición futura deberá ser explícita, validada por un caso de uso y auditable. “Pendiente”, “registrado” o “corregido” describirán el dato; no valorarán la salud ni el desempeño de la persona cuidadora.

## Identidad, tiempo y versiones

Las entidades usarán identificadores locales estables. Los hechos conservarán tiempo declarado y tiempo de captura cuando difieran. Las revisiones serán inmutables y se relacionarán con su predecesora; eliminar una vista no eliminará el historial auditable.

## Implementado frente a previsto

| Área | Estado |
|---|---|
| Procedencia en Kotlin puro | Implementada |
| “Mi día” con datos ficticios | Implementada en UI; solo nombres y fecha provienen del perfil/localidad |
| `Caregiver`, `PersonUnderCare`, `CareProfile` y `ConsentRecord` | Implementados y persistidos con mapeo explícito |
| `LegalAcceptanceRecord` | Implementado y persistido con versión, decisión, estado y fecha |
| Creación/consulta y repositorio de perfiles | Implementados con transacción cifrada y límite de uno por instalación |
| Auditoría y revisiones | Pendientes; no se capturan datos clínicos |
| Infraestructura cifrada | Integrada al perfil mediante esquema Room 2 y migración explícita 1→2 |
| Exportación y respaldo | Pendientes |
| Asistencia local | Pendiente |
