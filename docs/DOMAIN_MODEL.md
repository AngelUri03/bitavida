# Modelo de dominio

## Estado

El único tipo de dominio implementado actualmente es la procedencia de información en Kotlin puro. La pantalla “Mi día” usa datos ficticios de presentación; no crea ni persiste entidades clínicas. El resto de este documento define el modelo previsto y sus invariantes.

## Procedencia obligatoria

Toda entrada debe distinguir una de estas fuentes:

- **Indicación médica transcrita**: reproducción fiel de una instrucción externa, con referencia y revisión humana.
- **Registro declarado por cuidador**: hecho u observación capturada por una persona.
- **Información educativa verificada**: contenido general curado, con fuente, versión, licencia y revisión.

La procedencia no se infiere por el texto ni puede cambiarse automáticamente.

## Agregados previstos

- **CareProfile**: contexto local y preferencias no sensibles.
- **PersonUnderCare**: persona atendida y relaciones autorizadas.
- **Caregiver**: persona que registra o revisa información.
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
- Autocompletar medicamentos solo desde nombres previamente registrados y nunca completar dosis, unidad u horario.
- Las correcciones crean una nueva revisión y conservan la anterior.
- Las operaciones sensibles requieren autorización y auditoría.
- La asistencia propone; una persona confirma antes de cualquier cambio.
- Las fichas educativas se enlazan por identificador exacto verificado, no por similitud nominal.
- OCR se limita a comprobantes administrativos.
- Exportación y compartición requieren acción explícita y advertencia de sensibilidad.

## Estados y transiciones

Los catálogos definitivos de estados aún no están implementados. Como regla, cada transición deberá ser explícita, validada por un caso de uso y auditable. “Pendiente”, “registrado” o “corregido” describirán el dato; no valorarán la salud ni el desempeño de la persona cuidadora.

## Identidad, tiempo y versiones

Las entidades usarán identificadores locales estables. Los hechos conservarán tiempo declarado y tiempo de captura cuando difieran. Las revisiones serán inmutables y se relacionarán con su predecesora; eliminar una vista no eliminará el historial auditable.

## Implementado frente a previsto

| Área | Estado |
|---|---|
| Procedencia en Kotlin puro | Implementada |
| Demo con datos ficticios | Implementada en UI, sin dominio persistido |
| Entidades, casos de uso y repositorios | Pendientes |
| Auditoría y revisiones | Pendientes |
| Persistencia cifrada | Pendiente |
| Exportación y respaldo | Pendientes |
| Asistencia local | Pendiente |
