# Observaciones · rediseño integral

Informe de la iteración anterior. El pulido vigente de captura, seguimiento sin fechas configurables, suspensión con motivo e historial enfocado se documenta en [OBSERVATIONS_POLISH.md](OBSERVATIONS_POLISH.md).

## Resultado funcional

La biblioteca conserva los ocho grupos y los 27 identificadores. El encabezado oficial permanece; la tarjeta explicativa se sustituye por «Selecciona lo que observaste». Buscar empieza contraído, conserva el motor local y se despliega con movimiento reducido compatible. Cada categoría comparte una sola superficie para encabezado y contenido; el encabezado no aplica una indicación rectangular. El acceso al historial queda después de todas las categorías. No aparecen configuraciones, horarios ni formularios dentro de la biblioteca.

Cada selección abre su detalle: identidad, estado de seguimiento, último hecho real, resultado, fecha/hora, resumen y fotografía cuando existe. Las declaraciones No realizada se excluyen de Última observación. Sin hechos se ofrece Registrar primera observación. Registro de hoy solo existe cuando hay seguimiento; reutiliza las filas aprobadas y distingue Registrada, Sin confirmar, No realizada y revisión futura. Los cambios y la ausencia declarada de cambios se muestran dentro de las revisiones efectivamente registradas. La ausencia de interacción no genera resultados.

Indicación vigente y acciones administrativas reutilizan las composiciones existentes. Se puede configurar o corregir un seguimiento, cambiar indicación, suspender/reactivar y consultar sus cambios. Ambos historiales están disponibles desde cada detalle incluso vacíos. Mi día conserva su integración y abre la ocurrencia exacta.

## Captura y ayuda descriptiva

La vista media tiene cuerpo con scroll y una acción de guardar separada, accesible al crecer el formulario. Las preguntas esenciales proceden del catálogo especializado. Contexto, preguntas opcionales y fotografías se despliegan como complementos. Otro cambio conserva descripción obligatoria; una corrección exige motivo.

Ayúdame a describirlo produce texto exclusivamente desde las respuestas declaradas. La propuesta se muestra antes de incorporarla, requiere una acción explícita y queda editable. No se infieren síntomas, causas, gravedad, tiempos faltantes ni tratamientos. Tampoco guarda automáticamente.

Se usan únicamente los selectores oficiales de fecha/hora. Dentro de la vista media el selector reemplaza temporalmente el contenido y conserva el borrador; no se apilan modales.

La infraestructura anterior conserva imágenes privadas como BLOB en la base SQLCipher. La nueva UI muestra miniaturas y permite cambiar/quitar la imagen del borrador. Las fotografías vinculadas a cualquier versión anterior se conservan. La interfaz habla de almacenamiento privado; no presenta una nueva auditoría criptográfica como realizada.

## Seguimientos

El flujo se organiza en cinco pasos: indicación, frecuencia, horarios, avisos y revisión/confirmación. Reutiliza GuidedRegistrationHeader/StepProgress/StepTransition/Choice/ReviewRow, los editores de administración y GuidedTimeWindowEditorContent. Admite horas exactas, ventanas, varios horarios, días específicos, intervalo y vigencia opcional. Cada paso valida antes de avanzar. El editor horario incompleto vive en el ViewModel y Atrás lo cierra antes de abandonar su paso.

Cambiar solamente la indicación conserva identidades de horarios y no duplica ocurrencias. Suspender y reactivar conservan versiones y hechos previos. Un registro espontáneo continúa sin crear ni completar una programación.

## Historial y correcciones

El calendario semanal y los filtros reutilizan componentes aprobados. La línea de tiempo presenta exclusivamente el día/filtro elegidos, hora, icono, resultado, origen y miniatura. Cada registro abre un detalle con todas sus respuestas, fotografías y versiones.

ObservationRecord permanece como hecho original inmutable. ObservationRecordRevision añade identidad de operación, número, instantánea, motivo, actor y fecha de corrección. El repositorio único expone registros vigentes y sus revisiones; no existe otro repositorio de historial.

La migración aditiva 19→20 crea una tabla y dos índices, sin reescribir registros ni borrar tablas. Se conservan las asociaciones de paciente, tipo y ocurrencia. La corrección de No realizada a resultado registrado conserva el mismo evento y el historial original. El guardado revalida consentimiento y autorización transaccionalmente. Los reintentos reutilizan la identidad de operación; una recarga tras el commit evita publicar una versión anterior como vigente.

## Archivos

Nuevos:

- ui/observations/ObservationRegistrationSheet.kt: modal, cuerpo compartido y footer fijo.
- ui/observations/ObservationDetailContent.kt: detalle, Registro de hoy, acciones y detalle de registros.
- ui/observations/ObservationDetailPreviews.kt: estados vacíos, activos, suspendidos y corregidos.
- domain/usecase/ObservationDescription.kt: redacción determinista.
- data/local/secure/ObservationCorrectionMigration.kt: migración aditiva.
- Esquema Room exportado 20.json.
- Pruebas ObservationCorrectionTest, ObservationCorrectionPersistenceTest y ObservationDetailPresentationTest.
- Este informe.

Actualizados:

- ObservationsViewModel, ObservationsScreen, ObservationScheduleContent, ObservationHistoryContent y ObservationsPreviews.
- Observation, ObservationRepository, ObservationUseCases.
- ObservationEntities, ObservationDao, EncryptedObservationRepository y documentación de ObservationPhotoStore.
- SecureSpikeDatabase y SecureDatabaseProvider, con registro de la migración.
- ObservationsViewModelTest y contratos existentes de migración/payload para el esquema actual.
- PRODUCT, ARCHITECTURE, DOMAIN_MODEL, SECURITY y la nota histórica de OBSERVATIONS_IMPLEMENTATION.

No se modificaron Medicamentos ni Mediciones en este rediseño. No se añadieron dependencias, módulos, servicios, permisos, red ni cambios en los identificadores del catálogo.

## Adaptación visual mínima

MedicationLifecycleSheetFrame desplaza todo su contenido y no ofrece un footer fijo. Para cumplir Guardar siempre accesible sin alterar Medicamentos, ObservationRegistrationSheet conserva los mismos parámetros de modal, forma, scrim, drag handle, colores y host oficial de selectores, y aporta una distribución de cuerpo desplazable más footer. ObservationCaptureBody es el mismo cuerpo usado por la app y por las previews; no hay una implementación de formulario alternativa.

## Validación

La validación base pasó antes del rediseño. La validación integrada usa una sola invocación:

`gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

La validación integrada pasó con 315 pruebas JVM (20 nuevas respecto de la base de 295), 0 fallos y 0 omitidas. Lint terminó con `No issues found`. Se comprobaron la sincronización de Gradle y los contratos del esquema 20. La última invocación completa terminó con `BUILD SUCCESSFUL` después de los ajustes visuales. `git diff --check` también pasó.

Se renderizaron principal clara, búsqueda/categoría expandida oscura, detalle activo oscuro, cuerpo real de captura a 320 dp y texto al 200 % con Guardar visible fuera del scroll, revisión del seguimiento, historial compacto de 320 dp, registro corregido oscuro y detalle vacío al 200 %. El historial compacto adapta la hora al ancho real disponible y la coloca dentro de la tarjeta para conservar legibilidad. Se inspeccionaron imágenes y jerarquías; las variantes restantes permanecen definidas sin afirmar que todas fueron recorridas.

Las pruebas cubren navegación y retorno al tipo de origen, categorías/búsqueda, los 27 formularios, registro espontáneo/programado, cinco pasos y cambios de indicación, retención del editor, propuesta sin guardado automático, motivo de corrección, versiones originales, reintentos sin duplicados, asociación exacta, reconstrucción de fechas/fotografías, esquema exportado y estados informativos.

## Alcance de la comprobación

No se realizaron pruebas manuales en Samsung ni se ejecutaron instrumentadas. La migración real y reapertura cifrada, Photo Picker, notificaciones, teclado/IME, TalkBack y gestos de la vista media requieren comprobación en dispositivo. Las pruebas JVM de persistencia verifican payloads, referencias/versiones y contrato del esquema; no se presentan como ejecución real de SQLCipher en Android.

Los mensajes de seguridad son información fija general. Su revisión clínica profesional antes de publicación permanece pendiente según OBSERVATIONS_SAFETY.md.

El renderizador del IDE no dibuja la ventana ModalBottomSheet y produjo una imagen vacía al intentar capturarla directamente. Se verifica visualmente el cuerpo compartido real con su footer, además de sus estados de contenido; la apertura y arrastre del modal quedan para el recorrido en dispositivo.
