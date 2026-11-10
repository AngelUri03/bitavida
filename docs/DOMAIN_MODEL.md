# Modelo de dominio

## Estado

La procedencia de información y la primera rebanada de perfiles están implementadas en Kotlin puro. El flujo crea en memoria un único `CareProfile` con una persona cuidadora, una persona atendida y un registro de consentimiento; no persiste datos ni habilita información clínica real. También existe el modelo futuro `LegalAcceptanceRecord`, pero el onboarding actual no lo crea ni persiste. El resto de este documento define el modelo previsto y sus invariantes.

## Procedencia obligatoria

Toda entrada debe distinguir una de estas fuentes:

- **Indicación médica transcrita**: reproducción fiel de una instrucción externa, con referencia y revisión humana.
- **Registro declarado por cuidador**: hecho u observación capturada por una persona.
- **Información educativa verificada**: contenido general curado, con fuente, versión, licencia y revisión.

La procedencia no se infiere por el texto ni puede cambiarse automáticamente.

## Agregados previstos

- **CareProfile**: relación local entre el cuidador, la persona atendida y su consentimiento; mientras no se conecte a persistencia protegida su uso es `TRANSIENT_LOCAL`.
- **PersonUnderCare**: identidad ficticia declarada por el cuidador.
- **Caregiver**: identidad ficticia de quien realiza la declaración.
- **ConsentRecord**: conserva por separado la autorización declarada —no verificada legalmente por Bitavida— y el estado de consentimiento `ACTIVE` o `REVOKED`.
- **LegalAcceptanceRecord**: modelo futuro para registrar actor, documento, versión, decisión diferenciada y momento; aceptar términos y reconocer el aviso no equivalen a consentimiento sensible.
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

El consentimiento de perfiles distingue `ACTIVE` y `REVOKED`; un registro revocado debe conservar el instante de revocación y uno vigente no lo presenta. La creación inicial produce consentimiento vigente solo después de una declaración explícita del cuidador. Los demás catálogos de estados aún no están implementados. Cada transición futura deberá ser explícita, validada por un caso de uso y auditable. “Pendiente”, “registrado” o “corregido” describirán el dato; no valorarán la salud ni el desempeño de la persona cuidadora.

## Identidad, tiempo y versiones

Las entidades usarán identificadores locales estables. Los hechos conservarán tiempo declarado y tiempo de captura cuando difieran. Las revisiones serán inmutables y se relacionarán con su predecesora; eliminar una vista no eliminará el historial auditable.

## Implementado frente a previsto

| Área | Estado |
|---|---|
| Procedencia en Kotlin puro | Implementada |
| “Mi día” con datos ficticios | Implementada en UI; perfiles transitorios en memoria |
| `Caregiver`, `PersonUnderCare`, `CareProfile` y `ConsentRecord` | Implementados |
| `LegalAcceptanceRecord` | Modelo implementado; creación y persistencia pendientes |
| Creación/consulta y repositorio de perfiles | Implementados de forma transitoria en memoria |
| Auditoría y revisiones | Pendientes |
| Infraestructura cifrada | Integrada y aislada; perfiles aún no conectados |
| Exportación y respaldo | Pendientes |
| Asistencia local | Pendiente |
