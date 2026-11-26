# Entrega técnica · Observaciones (implementación inicial)

Este documento conserva la entrega inicial del esquema 19 y sus resultados de verificación. El estado vigente del rediseño, incluido el detalle y las correcciones auditadas del esquema 20, se documenta en [OBSERVATIONS_REDESIGN.md](OBSERVATIONS_REDESIGN.md).

Fecha: 18 de noviembre de 2026. Alcance aprobado en PRODUCT.md. Fuente funcional consultada: reporte maestro PDF, especialmente 5.2.4–5.2.9. Fuente visual: baseline 0.5.4 y componentes ejecutables.

## Funciones implementadas

| Área | Comportamiento |
|---|---|
| Biblioteca | 27 definiciones fijas OBS-001 a OBS-027, ocho categorías expandibles, disponibles desde la primera visita. No crea categorías ni requiere activar seguimiento. |
| Búsqueda | Comparación local determinista de nombre, descripción, categoría y sinónimos. Normalización Unicode, acentos, mayúsculas, puntuación y coincidencias parciales. Sin resultados ofrece Otro cambio observado. |
| Captura | Esquema distinto por observación: selecciones únicas/múltiples y textos específicos, contexto propio, campos obligatorios/opcionales, descripción complementaria y resultado de revisión cuando corresponde. Una caída se registra como acontecimiento. |
| Tiempo | Fecha real y captura separadas; hora exacta/aproximada/desconocida, selección oficial de fechas y horas, rechazo de hechos futuros. No se reemplaza la hora real por el aviso. |
| Guardado | Consentimiento sensible existente y autorización vigente por perfil. Confirmación explícita, bloqueo del doble toque, identificadores estables, reintento conservando borrador y sin duplicar una ocurrencia. |
| Fotografías | Selector seguro del sistema, sin permisos nuevos. Importación privada en SQLCipher, reducción a 1280 px y hasta 512 KiB por JPEG, eliminación de EXIF y URI original. Consulta informativa en historial. |
| Seguimientos | Indicación transcrita, frecuencia diaria/días específicos/intervalo, varios horarios exactos, inicio y fin opcional, elección explícita de avisos. Suspender/reactivar/configurar anexa revisiones con actor y captura. Conserva ventanas horarias si ya existen en el modelo compartido. |
| Mi día | Combina revisiones próximas y medicamentos en la lista existente, ordena y muestra hasta cuatro actividades de hoy/mañana. Tocar una revisión abre su formulario con asociación exacta. |
| Historial global | Un calendario semanal compartido con Mediciones, selección de día, acceso por fecha, filtros horizontales de categoría y resultados exclusivamente del día real seleccionado. Muestra horas conocidas/aproximadas/desconocidas y origen. |
| Incertidumbre | Una revisión pasada sin respuesta permanece Sin registro. Ausencia de datos nunca significa ausencia de síntomas. Registrar algo espontáneo no completa una revisión programada. |
| Navegación | Plan retira el acceso independiente a Tareas de cuidado; conserva el enum histórico y los datos existentes. No cambia la navegación general. |

## Archivos creados

Bajo `app/src/main/java/com/bitavida/app/`:

- `domain/model/Observation.kt`, `ObservationCatalog.kt`.
- `domain/repository/ObservationRepository.kt`.
- `domain/usecase/ObservationUseCases.kt`.
- `data/local/secure/ObservationEntities.kt`, `ObservationDao.kt`, `ObservationMigration.kt`.
- `data/observations/EncryptedObservationRepository.kt`, `ObservationPayloadCodec.kt`, `ObservationPhotoStore.kt`.
- `platform/reminder/ObservationReminderScheduler.kt`.
- `ui/observations/ObservationsViewModel.kt`, `ObservationsScreen.kt`, `ObservationScheduleContent.kt`, `ObservationHistoryContent.kt`, `ObservationsPreviews.kt`.

Pruebas en `app/src/test/java/com/bitavida/app/`:

- `domain/usecase/ObservationUseCasesTest.kt`.
- `ui/observations/ObservationsViewModelTest.kt`.
- `data/observations/ObservationPayloadCodecTest.kt`, `ObservationMigrationContractTest.kt`.

Otros: justificación de la dependencia en `docs/OBSERVATIONS_IMAGE_DEPENDENCY.md`, esquema Room exportado `app/schemas/com.bitavida.app.data.local.secure.SecureSpikeDatabase/19.json`, este informe y `docs/OBSERVATIONS_SAFETY.md`.

## Archivos modificados

- `MainActivity.kt`: ViewModel retenido, carga de Observaciones, conciliación de avisos y apertura desde Mi día.
- `ui/demo/TodayDemoScreen.kt`: actividades mixtas con la composición existente.
- `ui/medical/MedicalPlanScreen.kt`: destino Observaciones y retiro del acceso separado a Tareas.
- `ui/measurements/MeasurementCalendarContent.kt`: extracción mínima de la presentación semanal compartida; Mediciones conserva su adaptación y geometría.
- `data/local/secure/SecureSpikeDatabase.kt`, `SecureDatabaseProvider.kt`: esquema 19, DAO y migración.
- `platform/reminder/MeasurementReminderScheduler.kt`: adaptador común de avisos proyectados y texto público configurable.
- `app/src/main/AndroidManifest.xml`: receptores internos de avisos y restauración. Sin permisos nuevos.
- `app/build.gradle.kts`, `gradle/libs.versions.toml`: una dependencia documentada, AndroidX ExifInterface 1.4.2. No se actualizan versiones existentes.
- `docs/PRODUCT.md`, `ARCHITECTURE.md`, `DOMAIN_MODEL.md`, `SECURITY.md`: alcance, arquitectura, contratos y privacidad.

El repositorio tenía cambios previos sin confirmar. Esta lista describe la intervención de Observaciones y no atribuye todos los cambios del árbol de trabajo a esta entrega.

## Arquitectura y reutilización

Se mantiene UI → ViewModel → casos de uso → interfaz de repositorio ← datos Room/SQLCipher. El dominio sigue siendo Kotlin puro. El catálogo centraliza lenguaje, esquema y reglas; ningún texto de búsqueda es procesado mediante IA. StateFlow es inmutable hacia la UI y los efectos únicos usan canales. Búsqueda y borradores sensibles permanecen únicamente en memoria y no se serializan en SavedState.

Se reutilizan CareSectionHeader, MeasurementPanel/Button/Action, GlucoseCaptureChip, GuidedAdministrationEditorSurface, GuidedTimeSelectionRow, MedicationLifecycleSheetFrame, BitavidaDatePicker/TimePicker mediante su host oficial, BitavidaWeekCalendar, fondo, navegación, tokens Kodchasan, colores semánticos y BitavidaFluidMotion. No se añaden módulos. La única dependencia nueva es AndroidX ExifInterface 1.4.2 para orientación segura de fotografías; justificación, compatibilidad, licencia, alternativa y costo medido se documentan en OBSERVATIONS_IMAGE_DEPENDENCY.md.

La adaptación mínima respecto del alcance consiste en envolver el modelo puro de programación existente GlucoseFollowUp, con tablas y registros de Observaciones independientes. No se replica el motor de frecuencia ni se mezclan lecturas con síntomas. El editor nuevo ofrece horarios exactos; mantiene sin convertir las ventanas presentes en un seguimiento compartido.

## Persistencia, trazabilidad y avisos

La migración 18→19 es aditiva: cinco tablas y catorce índices, con claves foráneas y unicidad por paciente/tipo de seguimiento y por paciente/ocurrencia. Conserva las tablas previas. Los registros son inmutables; el historial no ofrece editar ni eliminar. Los cambios de programación son append-only con control optimista e idempotencia. Los payloads binarios tienen versión, límites y rechazo de datos truncados o excedentes; no se usa serialización Java.

Las proyecciones resuelven revisión vigente al comienzo del horario y no generan actividad antes de la captura. Suspender cancela futuros y ventanas abiertas, sin borrar hechos anteriores; reactivar no recrea ventanas canceladas ni revisiones retroactivas. Los registros asociados suprimen exclusivamente su evento exacto aunque la fecha real sea otra.

Los avisos locales comparten la infraestructura actual, un bloqueo de conciliación y recarga de la base confirmada. Antes de notificar se revalida estado y consentimiento; guardar o suspender no deja que una captura anterior de la programación reprograme avisos. Los receptores renuevan el horizonte. Android puede retrasar o impedir notificaciones; la actividad no cambia de resultado por ello.

No se añaden logs clínicos, red, backend, permisos de cámara/almacenamiento, llamadas automáticas, exportación ni transferencia. Auto Backup sigue excluido.

## Verificación ejecutada

La invocación base `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` pasó antes de implementar.

La misma invocación final terminó con **BUILD SUCCESSFUL** (49 s): assembleDebug, testDebugUnitTest y lintDebug. El conjunto completo contiene **295 pruebas, 0 fallos y 0 omitidas**, incluidas **27 pruebas nuevas** (14 de dominio, 9 de ViewModel, 3 de codec y 1 de contrato de migración). El informe final de lint indica **No issues found**. También pasó `git diff --check` y la sincronización de Gradle del IDE.

Se renderizaron correctamente las previews de biblioteca en claro/oscuro y 320 dp con texto al 200 %, formulario en claro y texto al 200 %, historial en oscuro y programación a 320 dp con texto al 200 %. Se inspeccionaron imágenes y jerarquías de contenido. Todas las familias tienen definiciones para claro, oscuro, compacto y texto al 200 % en `ObservationsPreviews.kt`; no se afirma haber renderizado cada variante ni recorrido todos los controles. Son previews de contenido con datos ficticios, no capturas de un flujo real en dispositivo.

Un primer intento intermedio falló por generación KSP simultánea con cambios del DAO; otro por una excepción interna UAST de lint durante edición. Ambos se resolvieron en las ejecuciones posteriores con archivos estables, sin desactivar reglas ni alterar dependencias de Kotlin/AGP. El cambio de lector EXIF resolvió las advertencias reales de imágenes. No quedan fallos en la invocación final.

Las pruebas nuevas cubren catálogo y sinónimos, todos los esquemas, validaciones, fecha/categoría, horarios/intervalos/vigencia, asociación exacta, ventanas canceladas, suspensión/reactivación, consentimiento, errores/reintentos/doble guardado, codec y correspondencia del DDL con el esquema generado. Las pruebas usan exclusivamente datos ficticios.

## Límites y comprobaciones pendientes

- No se ejecutaron instrumentadas ni recorridos manuales en dispositivo. La migración real 18→19, reapertura cifrada tras terminar el proceso, selector/fotografías, notificaciones, reinicio del teléfono y cambio de zona necesitan esas comprobaciones. El test JVM de DDL no reemplaza ejecutar una migración.
- TalkBack, teclado físico, contraste sobre dispositivo, IME, scroll prolongado y escalado real al 200 % requieren recorrido manual. Las previews y semántica no acreditan accesibilidad completa.
- Los avisos específicos de seguridad clínica requieren revisión humana y aprobación antes de publicación. Solo se incluye información general fija, sin diagnósticos, recomendaciones terapéuticas ni interpretación de texto. Fuentes y estado: OBSERVATIONS_SAFETY.md.
- No se incluye un flujo de corrección de observaciones ya guardadas. Son inmutables; no hay edición ni eliminación silenciosa. El historial de cambios implementado corresponde a los seguimientos.
- La programación nueva usa horarios exactos; no incorpora un editor nuevo de ventanas horarias. La persistencia y proyección admiten las ventanas del contrato compartido.
- El historial permanece informativo. Las revisiones de hoy se abren desde biblioteca/Mi día; no se añade una acción de captura a las tarjetas del historial.
- Exportación, respaldo y restauración del producto siguen pendientes en el repositorio y no se presentan como integraciones existentes.
