# Producto Bitavida

## Fuentes y estado

El reporte maestro de `docs/reference` define el problema y el alcance funcional. La referencia visual oficial es **BITAVIDA UI BASELINE 0.5.4**. Bitavida está actualmente en estado de **demostración de interfaz**: presenta datos ficticios y no debe describirse como producto clínicamente funcional.

## Propósito y público

Bitavida ayuda a personas cuidadoras y familias a ordenar información cotidiana de cuidado entre consultas, reducir omisiones administrativas y preparar conversaciones con profesionales. Organiza información sin diagnosticar, interpretar mediciones, calcular dosis ni recomendar tratamientos.

## Distinciones obligatorias

Toda información futura debe indicar su procedencia:

1. indicación médica transcrita;
2. registro declarado por la persona cuidadora;
3. información educativa general verificada.

Una alerta ignorada no demuestra omisión ni cumplimiento. Una confirmación en la demo no constituye evidencia clínica.

## Alcance contemplado

- Perfiles de cuidado y personas atendidas.
- Transcripción versionada de indicaciones médicas.
- Horarios y recordatorios configurados por la persona.
- Registro declarado de tomas, alimentación, eliminación, observaciones y mediciones.
- Citas, estudios, documentos administrativos y notas.
- Resúmenes revisables, exportación explícita y respaldos portables cifrados.
- Ayudas locales descritas en [ASSISTANCE.md](ASSISTANCE.md).

Estas funciones son planificación, salvo lo marcado como implementado.

## Implementado

- Una aplicación Android Compose y un módulo `:app`.
- Demostración editorial “Mi día” con datos ficticios.
- Estado efímero de confirmación y expansión de cita.
- Temas claro/oscuro, navegación inferior demostrativa, accesibilidad y movimiento reducido.
- Sistema visual y motion de la baseline 0.5.4.
- Modelo mínimo de procedencia en Kotlin puro.

## Pendiente

Persistencia segura, repositorios, casos de uso, ViewModel, auditoría, permisos por perfil, exportación, respaldo, restauración, pruebas de seguridad y todas las funciones reales de asistencia. No se ha aprobado backend, cuenta ni sincronización.

## Decisiones vigentes posteriores al reporte maestro

- Identidad oficial: **El hilo de la vida**.
- Kodchasan y paleta petróleo/turquesa con superficies pastel.
- Composición editorial de “Mi día” y baseline visual 0.5.4.
- Operación esencial offline y sin cuenta.
- Educación farmacológica futura solo mediante catálogo curado, licencia compatible, revisión profesional e identificador verificado.

## Decisiones rechazadas o no vigentes

- Usar Cuida como especificación.
- Tratar Figma V2 o iteraciones antiguas como fuente obligatoria.
- AGSL, Liquid Glass, lentes, refracción, ondas radiales, canicas y deformaciones experimentales.
- LLM para información farmacológica, OCR clínico o automatización sin confirmación.
- Envío de datos personales o telemetría a servicios externos.

Las decisiones ambiguas permanecen pendientes; no se completan por inferencia. Todo cambio de alcance debe registrarse aquí antes de implementarse.
