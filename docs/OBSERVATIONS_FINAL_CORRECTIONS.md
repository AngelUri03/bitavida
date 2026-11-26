# Correcciones finales de Observaciones y navegación global

Fecha de verificación: 19 de noviembre de 2026.

## Resultado

Se corrigieron la navegación raíz de Plan, el estado inicial del catálogo de Observaciones, la eliminación auditable de seguimientos, la actualización reactiva de Registro de hoy, la captura temporal conocida/desconocida, la experiencia privada de fotografías y el último paso visual de Configurar seguimiento.

No se añadieron dependencias, permisos de Internet, migraciones destructivas ni repositorios paralelos.

## Archivos creados

- `app/src/test/java/com/bitavida/app/ui/observations/ObservationFinalUxPolicyTest.kt`: contratos estáticos para ausencia de Toast/Snackbar, navegación raíz, horario, fotografía y revisión final.
- `docs/OBSERVATIONS_FINAL_CORRECTIONS.md`: este reporte.

## Archivos principales modificados

- `app/src/main/java/com/bitavida/app/ui/observations/ObservationsScreen.kt`
- `app/src/main/java/com/bitavida/app/ui/observations/ObservationsViewModel.kt`
- `app/src/main/java/com/bitavida/app/ui/observations/ObservationRegistrationSheet.kt`
- `app/src/main/java/com/bitavida/app/ui/observations/ObservationDetailContent.kt`
- `app/src/main/java/com/bitavida/app/ui/observations/ObservationSuspensionSheet.kt`
- `app/src/main/java/com/bitavida/app/ui/observations/ObservationScheduleContent.kt`
- `app/src/main/java/com/bitavida/app/ui/observations/ObservationScheduling.kt`
- `app/src/main/java/com/bitavida/app/ui/plan/MedicalPlanScreen.kt`
- `app/src/main/java/com/bitavida/app/ui/measurements/MeasurementsScreen.kt`
- `app/src/main/java/com/bitavida/app/MainActivity.kt`
- pruebas de ViewModel, programación, navegación y presentación de Observaciones.
- `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/DOMAIN_MODEL.md` y `docs/SECURITY.md`.

## Implementación

### Mensajes y errores

La aplicación ya no contiene llamadas a Toast ni hosts/emisiones de Snackbar en código de producción. Las operaciones exitosas se comunican mediante el estado actualizado de la pantalla. Los errores reales permanecen asociados al estado o formulario correspondiente.

### Catálogo de Observaciones

La entrada real al catálogo ejecuta una acción explícita que contrae las ocho categorías y limpia estados transitorios. La recomposición simple no reinicia el acordeón. Solo una categoría puede permanecer abierta y su apertura/cierre conserva animación.

### Navegación de Plan

Seleccionar o volver a seleccionar Plan restablece el destino raíz de Plan de cuidado y limpia la navegación transitoria de Observaciones. Cambiar a Mi día y volver a Plan también regresa al inicio, sin crear otra instancia visible del flujo. La navegación Atrás dentro de un flujo conserva su comportamiento normal.

### Eliminación del seguimiento

La acción visible es **Eliminar seguimiento**, separada y con tratamiento destructivo. La confirmación conserva el motivo obligatorio y crea una revisión histórica `REMOVED`.

Después de persistir:

- el seguimiento deja de proyectarse como activo;
- se cancelan alarmas y avisos futuros mediante la reconciliación existente;
- desaparecen Registro de hoy, Indicación vigente y acciones operativas;
- se conserva todo registro, evento anterior e historial de cambios;
- queda disponible Configurar seguimiento.

Al volver a configurar, la última indicación, frecuencia, horarios y contexto de avisos se usan como plantilla editable. La plantilla no reactiva ni programa nada hasta la confirmación explícita.

### Actualización reactiva

El ViewModel mantiene un único `StateFlow` de pantalla. La confirmación, modificación, eliminación, registro y corrección reemplazan el estado después de la persistencia exitosa. Registro de hoy e Indicación vigente se recalculan en la misma pantalla, sin depender de `onResume` ni de volver a navegar. Las identidades de ocurrencia evitan duplicar eventos al refrescar.

### Hora del registro

La captura nueva solo ofrece:

- hora conocida, editable con el selector oficial;
- **No sé la hora**, que guarda el instante observado sin una hora inventada y conserva la fecha.

Los registros históricos con precisión aproximada siguen siendo legibles para compatibilidad, pero esa opción ya no se ofrece en nuevas capturas.

### Fotografías

La miniatura usa una única acción de edición. Sus opciones se presentan en una vista media del Design System: tomar otra fotografía, elegir otra de galería y quitar, esta última con color destructivo semántico. Cancelar cámara o galería conserva la imagen anterior.

El visor utiliza fondo negro uniforme, imagen proporcional con zoom y desplazamiento, cierre X de 48 dp en la esquina superior derecha, insets del sistema y transición que respeta la preferencia de movimiento. No se muestran automáticamente imágenes sensibles en Última observación ni en el historial.

Las fotografías continúan en almacenamiento privado y offline. No se afirma cifrado de archivos ni se realiza análisis clínico.

### Revisión final de Configurar seguimiento

Solo se homologó el paso final de revisión con el patrón aprobado de Medicamentos: superficie conectada, encabezado con icono, filas editables, jerarquía, separación y CTA integrado. La frecuencia, horarios, avisos, validaciones y pasos anteriores no cambiaron. Observaciones no incorpora vigencia configurable.

## Arquitectura y seguridad

Se conservó la dependencia UI → casos de uso → repositorio → persistencia. La eliminación es una revisión auditable, no una eliminación física. No se borran observaciones clínicas ni se reactivan programaciones implícitamente. No se registran datos sensibles en logs y no se añadió comunicación de red.

## Pruebas ejecutadas

Pruebas focalizadas: 52 casos de Observaciones aprobados.

Suite JVM completa: 371 pruebas aprobadas.

Invocación final obligatoria:

`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

Resultado: **BUILD SUCCESSFUL** en 1 min 3 s; 54 tareas, 11 ejecutadas y 43 actualizadas.

La búsqueda estática final en `app/src/main` no encontró `Toast`, `makeText`, `showSnackbar`, `SnackbarHostState` ni `SnackbarHost`.

## Verificación no realizada

No se ejecutaron pruebas instrumentadas, validación visual automática ni pruebas manuales en Samsung, de acuerdo con el alcance solicitado. Los previews existentes de claro, oscuro, compacto y texto ampliado compilaron como parte del módulo, pero no se presentan como inspección visual manual.

## Restricciones conocidas

Los nombres internos históricos relacionados con “suspensión” y la compatibilidad de precisión aproximada se conservan donde son necesarios para leer auditorías y registros anteriores. No se exponen como acciones nuevas ni mantienen seguimientos activos ocultos.
