# Producto Bitavida

## Fuentes y estado

El reporte maestro de `docs/reference` define el problema y el alcance funcional. La referencia visual oficial es **BITAVIDA UI BASELINE 0.5.4**. Bitavida incorpora configuración y persistencia local cifrada de un perfil de cuidado no clínico; todavía no debe describirse como producto clínicamente completo ni como autorización para capturar datos de salud.

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

## Alcance aprobado para la fase 3.2

El acceso local es una preferencia opcional, desactivada por defecto, elegida en la etapa final del onboarding mediante “Proteger Bitavida”. Solo se conserva este booleano no sensible en preferencias privadas. Desactivada, Bitavida abre directamente, no invoca autenticación y no bloquea al volver. Activada, Android verifica la identidad mediante biometría fuerte compatible o la credencial segura del dispositivo; Bitavida no crea ni conserva credenciales, secretos biométricos, cuentas o datos de red. En API 30 o superior se ofrece la combinación nativa; en API 26–29 se usan flujos nativos separados.

La protección solo puede activarse cuando Android informa un bloqueo viable. Una sesión protegida comienza bloqueada, permanece así ante cancelación o error y se bloquea después de 60 segundos reales en segundo plano. El prompt aparece automáticamente y la vista intermedia se limita a espera, explicación breve y reintento. Atrás conserva el comportamiento normal de Android. No se usa `FLAG_SECURE` ni otra restricción general de capturas o grabación. Esta preferencia no persiste perfiles, no verifica representación legal, no autoriza operaciones sensibles y no modifica SQLCipher, Keystore, copias de seguridad ni validaciones de dominio.

## Alcance aprobado para la fase 3.3

El único perfil por instalación se conserva localmente en la base cifrada existente. Incluye cuidador, persona atendida, relación de cuidado, autorización declarada y registros separados de aceptación de términos y conocimiento del aviso, con identificadores estables, versión, fecha y estado. La creación es transaccional: una escritura incompleta no muestra “Perfil listo” ni se interpreta como instalación nueva.

Al abrir, la ausencia confirmada de perfil conduce al onboarding; un perfil válido se recupera y omite el flujo; un error de base, integridad o clave muestra una explicación y reintento sin borrar datos. “Mi día” conserva la baseline 0.5.4 y solo sustituye los nombres de demostración por primeros nombres derivados del perfil y la fecha fija por la fecha local real. El cifrado es obligatorio y permanece independiente de “Proteger Bitavida”. Esta fase no captura datos clínicos, no acredita representación legal y no resuelve revocación operable, retención, ARCO ni revisión profesional.

## Implementado

- Una aplicación Android Compose y un módulo `:app`.
- Demostración editorial “Mi día” con datos ficticios.
- Estado efímero de confirmación y expansión de cita.
- Temas claro/oscuro, navegación inferior demostrativa, accesibilidad y movimiento reducido.
- Sistema visual y motion de la baseline 0.5.4.
- Modelo mínimo de procedencia en Kotlin puro.
- Infraestructura Room/SQLCipher/Keystore conectada al perfil no clínico mediante esquema 2 y migración explícita 1→2.
- Modelos puros de cuidador, persona atendida, perfil y consentimiento.
- Creación transaccional y consulta de un único perfil mediante repositorio cifrado, con mapeos explícitos Room ↔ dominio.
- Flujo final de bienvenida, privacidad, autorización declarada, identificación, revisión, corrección, cancelación y confirmación.
- Navegación contextual integrada al hilo, compatible con IME e insets, y documentos legales versionados consultables sin conexión.
- Aceptación de términos y reconocimiento del aviso diferenciados y persistidos con versión, fecha y estado, sin confundirse con autorización del cuidador ni consentimiento sensible.
- Captura de nombres con mínimo de 2 letras significativas, máximo de 80 grafemas, soporte Unicode, normalización diferida y rechazo de controles o caracteres ajenos a un nombre.
- Preferencia opcional “Proteger Bitavida”, desactivada por defecto y guardada como un único booleano privado.
- Acceso local con AndroidX Biometric, biometría fuerte y credencial del dispositivo, incluyendo alternativa nativa para API 26–29 cuando la persona activa la protección.
- Sesión central `LOCKED`/`AUTHENTICATING`/`UNLOCKED` y bloqueo al volver tras 60 segundos, únicamente con protección activa.
- Vista mínima de espera y reintento; capturas normales y navegación Atrás estándar.
- Arranque diferenciado para primera instalación, perfil válido y error de almacenamiento/clave, sin reinicio ni borrado silencioso.
- Personalización exclusiva de nombres y fecha local en “Mi día”, manteniendo intactos layout, hilo, tarjetas, navegación, colores y animaciones.

## Pendiente

Vinculación del acceso con claves autenticadas, historial general de auditoría, revocación operable, derechos ARCO, retención y eliminación, permisos por perfil, repositorios y casos de uso para las demás áreas, exportación, respaldo, restauración, revisión legal profesional, pruebas de seguridad restantes y todas las funciones clínicas y reales de asistencia. No se ha aprobado backend, cuenta ni sincronización.

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
