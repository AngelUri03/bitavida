# Guía de trabajo para Bitavida

## Fuentes de verdad y alcance

- Leer primero `docs/reference/SegundoParcial_Equipo1_Reporte de investigación.pdf`.
- El PDF es la fuente funcional; **BITAVIDA UI BASELINE 0.5.4** es la fuente visual oficial.
- Cuida es solo referencia competitiva. Figma V2 y los informes de iteraciones son históricos, no especificaciones vigentes.
- Registrar cambios de alcance en `docs/PRODUCT.md` antes de implementarlos.
- Mantener una sola aplicación y el módulo `:app` hasta que una necesidad medida justifique modularizar.
- Las funciones esenciales deben operar sin cuenta, backend, sincronización, servicios externos ni Internet.
- No alterar sin autorización explícita la composición, fondos, Kodchasan, paleta, curva superior, estela, nodos, confirmación, citas, navegación ni accesibilidad de la baseline 0.5.4.

## Arquitectura

- Seguir `docs/ARCHITECTURE.md` y `docs/DOMAIN_MODEL.md`.
- Dependencias permitidas: UI → casos de uso → interfaces de repositorio ← implementaciones de datos.
- El dominio debe ser Kotlin puro: sin Compose, Android, Room, DTO ni APIs de almacenamiento.
- Los ViewModel futuros expondrán `StateFlow` inmutable, recibirán acciones y modelarán efectos únicos explícitamente.
- Room será un detalle de persistencia estructurada; DataStore solo guardará preferencias no sensibles.
- Toda corrección clínica o administrativa conservará la versión anterior y producirá auditoría.
- No actualizar versiones ni añadir dependencias sin documentar necesidad, compatibilidad, licencia, tamaño y alternativa.

## Límites sanitarios y de asistencia

- Bitavida organiza información; no diagnostica, interpreta mediciones, calcula dosis ni recomienda tratamientos.
- Distinguir indicación médica transcrita, registro declarado por el cuidador e información educativa verificada.
- Una notificación ignorada nunca implica omisión ni cumplimiento.
- La asistencia propone y la persona confirma; nunca guarda, corrige, fusiona o elimina automáticamente.
- Autocompletar solo nombres registrados previamente; nunca dosis, unidad u horario.
- OCR solo para comprobantes administrativos; nunca recetas, resultados clínicos o lesiones.
- No usar LLM para información farmacológica ni enlazar fichas por semejanza nominal.
- Usar datos ficticios en tests, previews y ejemplos.

## Seguridad y privacidad

- Seguir `docs/SECURITY.md`; Room no implica cifrado.
- No registrar datos médicos, identificadores, OCR, rutas de adjuntos ni claves.
- Mantener excluidos Android Auto Backup y transferencias.
- No añadir `INTERNET` sin cambio de alcance y análisis de amenazas aprobados.
- Exportar o compartir siempre exige acción explícita y advertencia de sensibilidad.

## UI, accesibilidad y verificación

- Seguir `docs/DESIGN_SYSTEM.md` y `docs/ACCESSIBILITY.md`.
- Mantener objetivos táctiles de 48 dp, texto al 200 %, TalkBack, teclado, 320 dp y estados comprensibles sin color.
- Evitar alturas fijas para texto; conservar desplazamiento y `adjustResize`.
- Toda nueva funcionalidad que seleccione fecha u hora debe usar exclusivamente `BitavidaDatePicker` y `BitavidaTimePicker` dentro de la vista media oficial del Design System; no usar diálogos centrados, `DatePickerDialog`, `TimePickerDialog` ni alternativas sin una necesidad técnica documentada.
- Para nuevas pantallas, añadir previews claro, oscuro, compacto y texto 200 %.
- Antes de entregar, ejecutar en una sola invocación `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, salvo alcance de validación más estricto indicado por el usuario.
- No ejecutar instrumentadas ni validación visual automática sin solicitud. Informar resultados reales.
