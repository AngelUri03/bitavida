# Observaciones · registro en pantalla completa y fotografía

## Resultado implementado

Esta entrega aplica el alcance aprobado en `PRODUCT.md` sin cambiar el catálogo OBS-001…OBS-027, el esquema Room 20, los casos de uso de seguimiento ni las implementaciones de Medicamentos y Mediciones.

### Navegación y captura

Registrar observación es ahora el destino `REGISTER` del estado local de Observaciones. Abre una pantalla completa con Atrás oficial, título del tipo seleccionado, contenido vertical desplazable y Guardar observación fuera del scroll, protegido por `imePadding` y barras del sistema. Atrás o un guardado confirmado regresan al padre real —detalle o registro corregido— y conservan el scroll del detalle.

Las transiciones usan las duraciones de `BitavidaFluidMotion`, desplazamiento corto y fundido reversible. La identidad del destino depende del borrador, registro o tipo, por lo que editar texto no remonta la pantalla. Configurar seguimiento queda excluido expresamente de estas transiciones y conserva su contenido, pasos y navegación existentes.

Los 27 esquemas continúan procediendo de `ObservationCatalog`. El formulario ya no usa una tarjeta contenedora única: separa resultado, fecha/hora, información complementaria y fotografía sobre el fondo oficial. Las preguntas obligatorias aparecen primero; Detalles adicionales conserva preguntas secundarias y narrativas históricas. Una primera captura oculta la comparación `continues`; cuando ya existe un registro, la comparación aparece solo como opción secundaria y nunca obligatoria. Las opciones cortas forman cuadrículas completas y simétricas; tres opciones que no caben, etiquetas largas y conjuntos numerosos usan un carril horizontal identificado. Fecha y hora reutilizan exclusivamente `BitavidaDatePicker` y `BitavidaTimePicker`. Hora aproximada y desconocida conservan su semántica sin inventar un valor.

`ObservationGuideCatalog` aporta una guía local específica para cada OBS-001…OBS-027: qué revisar, ejemplos cotidianos y datos útiles, sin causas ni diagnósticos. Cómo observar abre un destino secundario y Atrás conserva el mismo borrador. Ayúdame a describirlo es independiente: abre un editor compacto con una sola propuesta determinista basada en respuestas declaradas; la persona puede modificarla, usarla o descartarla. OBS-027 conserva descripción obligatoria.

### Fotografías

Los 27 tipos del catálogo muestran Fotografía opcional, con Tomar foto y Elegir de galería. En ancho compacto o texto ampliado las acciones se apilan a ancho completo; en ancho suficiente comparten altura y ancho. La captura usa `ActivityResultContracts.TakePicture`; la selección usa `PickVisualMedia`. No se añadió una galería propia, dependencia ni permiso de cámara/almacenamiento.

La cámara recibe una URI temporal mediante un `FileProvider` no exportado, limitado a `cache/observation_camera`. El archivo temporal no se declara cifrado y se revoca/elimina al terminar, cancelar o fallar. La copia definitiva reutiliza `ObservationPhotoStore`: limita tamaño, corrige orientación, recodifica sin EXIF y guarda el JPEG como BLOB en la base SQLCipher existente.

Durante la captura, la UI muestra una miniatura acotada, una acción explícita para verla y un único menú contextual para tomar otra, elegir otra o quitarla. El visor usa fondo negro opaco, respeta proporciones y permite ampliar y desplazar. Última observación e historial no decodifican ni muestran la imagen automáticamente: solo indican que existe un adjunto; el detalle histórico exige pulsar Abrir fotografía. Los callbacks conservan identificadores opacos de borrador/reemplazo, mantienen la imagen anterior hasta importar correctamente la nueva y rechazan resultados tardíos. Las correcciones mantienen las referencias de la versión anterior y solo descartan fotografías definitivas sin vínculo.

### Detalle e historial

Última observación muestra resultado, fecha/hora, resumen acotado, indicador discreto de adjunto y Registrar observación. Se eliminó Consultar observación. La actualización de la tarjeta anima su altura con los tokens oficiales.

Indicación vigente usa `CurrentIndicationCardFrame`, el mismo armazón aprobado: frecuencia, número de revisiones, avisos, indicación transcrita agrupada, divisores y horarios numerados. No se alteró la configuración ni lógica de seguimiento.

El historial conserva una sola proyección. Desde un detalle fija el identificador exacto y la fecha del último registro; desde la biblioteca inicia global. El calendario semanal mantiene el filtro al cambiar día o semana. Volver a hoy es una acción secundaria integrada y solo aparece fuera de hoy; Elegir fecha ocupa todo el ancho y abre el selector oficial. La línea de tiempo anima el cambio de fecha/filtro sin fusionar Registrada, Sin confirmar, No realizada, Se observaron cambios o No se observaron cambios. Un evento Sin confirmar abre su contexto exacto —fecha, horario o ventana e indicación histórica— y permite Registrar revisión o No se realizó. Ambas rutas reutilizan la identidad de la ocurrencia; una corrección posterior conserva revisiones, actor, motivo y captura sin crear otro evento.

## Archivos

Nuevos en esta entrega:

- `ui/observations/ObservationCaptureOptions.kt`
- `ui/observations/ObservationPhotos.kt`
- `ui/observations/ObservationPhotoRequest.kt`
- `ui/observations/ObservationWeekCalendar.kt`
- `platform/observations/ObservationCameraFiles.kt`
- `res/xml/observation_camera_paths.xml`
- `ObservationPhotoRequestTest.kt`
- `ObservationNavigationPresentationTest.kt`
- `domain/model/ObservationGuide.kt`
- `ui/observations/ObservationGuideContent.kt`
- `ui/observations/ObservationDescriptionSheet.kt`
- `ui/observations/ObservationOccurrenceDetailContent.kt`
- `ObservationGuideCatalogTest.kt`

Actualizados:

- `ObservationsScreen.kt`, `ObservationRegistrationSheet.kt`, `ObservationsViewModel.kt`
- `ObservationDetailContent.kt`, `ObservationHistoryContent.kt`
- `ObservationsPreviews.kt`, `ObservationPolishPreviews.kt`
- `AndroidManifest.xml`
- `ObservationsViewModelTest.kt`, `ObservationUseCasesTest.kt`
- `ObservationCapturePresentationTest.kt`, `ObservationNavigationPresentationTest.kt`
- `PRODUCT.md`, `ARCHITECTURE.md`, `DOMAIN_MODEL.md`, `SECURITY.md`

No se modificaron `ObservationScheduleContent.kt`, `ObservationScheduling.kt`, `ObservationReminderScheduler.kt`, Medicamentos ni Mediciones durante este alcance.

## Privacidad y seguridad

La aplicación sigue sin permiso `INTERNET`. El proveedor es no exportado y solo concede temporalmente la URI concreta al contrato de cámara. No se guardan URI externas, rutas, nombres clínicos ni datos de formulario en SavedState o logs. Los datos definitivos conservan autorización/consentimiento, perfil, almacenamiento local cifrado y auditoría append-only existentes.

## Validación

La invocación única obligatoria `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` terminó con `BUILD SUCCESSFUL` en 1 min 7 s. Se ejecutaron 365 pruebas JVM, con 0 fallos y 100 % de éxito; `lintDebug` generó su reporte sin incidencias que impidieran la compilación.

Las pruebas nuevas cubren las 27 guías y los 27 permisos fotográficos del catálogo, primera captura sin comparación, comparación posterior opcional, identidad y restauración opaca de cámara/galería, rechazo de resultados tardíos, reemplazo seguro, contrato de FileProvider sin permisos amplios, destino de pantalla completa, retorno desde guía con el mismo borrador, estabilidad de identidad durante edición, transiciones reversibles, exclusión del flujo de seguimiento, cuadrículas/carriles responsive, privacidad sin miniaturas automáticas, ausencia de Consultar observación, uso del armazón compartido de Indicación vigente y apertura de una ocurrencia Sin confirmar con asociación exacta y sin duplicados. Las pruebas existentes continúan cubriendo los 27 esquemas, registro espontáneo/programado, correcciones, referencias fotográficas en payload y versiones, historial exacto, suspensión, recordatorios, migraciones y ausencia de duplicados.

Se renderizaron e inspeccionaron previews reales de la captura completa en claro y texto al 200 %, incluido el encabezado adaptativo y footer integrado; la guía específica de pies en claro; el detalle Sin confirmar en oscuro; y el detalle con Última observación sin fotografía automática. No se observaron superficies de bajo contraste en esas variantes.

No se realizan pruebas manuales en Samsung ni instrumentadas. Las pruebas JVM y previews no prueban una cámara externa/OEM concreta, Photo Picker OEM, reapertura SQLCipher, IME real, TalkBack ni la entrega de avisos; esos recorridos permanecen para la validación dirigida en dispositivo indicada por la persona usuaria.
