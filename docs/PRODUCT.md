# Producto Bitavida

## Fuentes y estado

El reporte maestro de `docs/reference` define el problema y el alcance funcional. La referencia visual oficial es **BITAVIDA UI BASELINE 0.5.4**. Bitavida incorpora una primera experiencia funcional de configuración de perfil con estado local transitorio; todavía no debe describirse como producto clínicamente completo ni como persistencia protegida de datos de cuidado.

## Propósito y público

Bitavida ayuda a personas cuidadoras y familias a ordenar información cotidiana de cuidado entre consultas, reducir omisiones administrativas y preparar conversaciones con profesionales. Organiza información sin diagnosticar, interpretar mediciones, calcular dosis ni recomendar tratamientos.

## Distinciones obligatorias

Toda información futura debe indicar su procedencia:

1. indicación médica transcrita;
2. registro declarado por la persona cuidadora;
3. información educativa general verificada.

Una alerta ignorada no demuestra omisión ni cumplimiento. Una confirmación dentro de la aplicación no constituye evidencia clínica.

## Alcance contemplado

- Perfiles de cuidado y personas atendidas.
- Transcripción versionada de indicaciones médicas.
- Horarios y recordatorios configurados por la persona.
- Registro declarado de tomas, alimentación, eliminación, observaciones y mediciones.
- Citas, estudios, documentos administrativos y notas.
- Resúmenes revisables, exportación explícita y respaldos portables cifrados.
- Ayudas locales descritas en [ASSISTANCE.md](ASSISTANCE.md).

Estas funciones son planificación, salvo lo marcado como implementado.

## Alcance aprobado para la fase 3.1

Se incorpora la experiencia de producto para crear y consultar, durante la vida del proceso, un solo perfil compuesto por una persona cuidadora y una persona atendida. El repositorio permanece en memoria y no se conecta todavía a Room ni a la infraestructura cifrada; por ello el perfil se pierde al finalizar el proceso de la aplicación. La información corresponde a la declaración de la persona cuidadora. Bitavida registra la autorización declarada, pero no verifica ni afirma representación legal. El consentimiento se modela explícitamente como vigente o revocado y este flujo no captura información clínica.

Los términos de uso y el aviso de privacidad son consultables sin conexión y muestran su versión. El flujo exige por separado la aceptación de términos y el reconocimiento de disponibilidad del aviso; la declaración de autorización del cuidador y el consentimiento expreso para datos sensibles permanecen como actos distintos. Las selecciones legales actuales viven solo en el estado transitorio y no constituyen todavía un registro jurídico persistente. La identidad, domicilio y canales reales del responsable, la política de conservación y los procedimientos ARCO son bloqueantes de publicación. El acceso local mediante credencial segura del dispositivo y biometría fuerte opcional se implementará en la fase 3.2; no se simula en la interfaz actual.

## Implementado

- Una aplicación Android Compose y un módulo `:app`.
- Demostración editorial “Mi día” con datos ficticios.
- Estado efímero de confirmación y expansión de cita.
- Temas claro/oscuro, navegación inferior demostrativa, accesibilidad y movimiento reducido.
- Sistema visual y motion de la baseline 0.5.4.
- Modelo mínimo de procedencia en Kotlin puro.
- Infraestructura técnica de persistencia cifrada integrada y todavía aislada de los flujos funcionales.
- Modelos puros de cuidador, persona atendida, perfil y consentimiento.
- Creación y consulta de un único perfil mediante casos de uso y repositorio transitorio en memoria.
- Flujo final de bienvenida, privacidad, autorización declarada, identificación, revisión, corrección, cancelación y confirmación.
- Navegación contextual integrada al hilo, compatible con IME e insets, y documentos legales versionados consultables sin conexión.
- Aceptación de términos y reconocimiento del aviso diferenciados, obligatorios y transitorios, sin confundirse con autorización del cuidador ni consentimiento sensible.
- Captura de nombres con mínimo de 2 letras significativas, máximo de 80 grafemas, soporte Unicode, normalización diferida y rechazo de controles o caracteres ajenos a un nombre.

## Pendiente

Conexión protegida de perfiles a persistencia, acceso local efectivo, registro persistente y auditable de versiones, momentos, aceptaciones y consentimiento, repositorios y casos de uso para las demás áreas, auditoría, revocación operable, derechos ARCO, retención y eliminación, permisos por perfil, exportación, respaldo, restauración, revisión legal profesional, pruebas de seguridad restantes y todas las funciones reales de asistencia. No se ha aprobado backend, cuenta ni sincronización.

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
