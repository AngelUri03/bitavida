# Seguridad y privacidad

## Compartir informe PDF

Compartir exige una acción expresa y una advertencia de sensibilidad. El documento preparado se guarda temporalmente en el directorio privado de caché `shared_reports`; esta copia **no está cifrada por SQLCipher**. El FileProvider específico no está exportado y solo expone ese subdirectorio mediante URI `content://` y una concesión temporal de lectura al destinatario elegido. Cada preparación utiliza un directorio independiente para no sobrescribir archivos en lectura; los temporales de al menos siete días se retiran de forma oportunista al preparar otro informe, sin borrarlos inmediatamente al abrir o cerrar el selector. Android puede retirar la caché antes. No se guardan destinatarios, ni se registran datos clínicos o rutas, ni se concede escritura o acceso persistente. Bitavida no puede confirmar que una aplicación receptora haya enviado el archivo ni controlar lo que esa aplicación haga con él.

## Diagnóstico local para desarrollo de Informe

La herramienta de diagnóstico existe únicamente en variantes depurables. Su actividad no se exporta y se abre desde Configuración después de acceder al perfil y superar el bloqueo configurado. La autorización solo existe en memoria, caduca como máximo al cumplirse el tiempo de bloqueo por segundo plano de Bitavida y se revoca al regresar a la pantalla principal o cerrar el diagnóstico. Antes de leer y durante la escritura se comprueba también que el dispositivo no esté bloqueado; si se supera el plazo o se bloquea, se interrumpe el intento y se procura retirar el documento incompleto. Una advertencia identifica que el archivo contiene información personal y de salud; solo una acción explícita abre el selector del sistema para elegir destino. No se escribe automáticamente una copia pública ni se incorpora contenido sensible o rutas a logs o Intents.

La instantánea JSON se construye en memoria mediante una transacción Room de lectura, con una lista explícita de tablas y la versión del esquema, momento UTC y zona horaria local. Conserva identidades, relaciones, versiones y metadatos de adjuntos; excluye BLOB, claves y campos de rutas o URI locales. El JSON seleccionado por la persona **no está cifrado por esta función**: debe guardarse y compartirse únicamente en un destino de confianza. La aplicación no añade red ni carga el archivo. Si falla la escritura se intenta eliminar el documento incompleto; si el proveedor no permite eliminarlo, se informa para su retiro manual.

El mecanismo es diagnóstico, no respaldo ni restauración. No modifica datos, no intenta reparar registros ni cambia las reglas de acceso del producto. La consistencia transaccional, cancelación, exclusión de bytes y apertura en las variantes de desarrollo requieren pruebas instrumentadas específicas antes de confiar en una extracción.

## Cierre de Plan · política vigente de Notas y Recordatorios

**Retiro de avisos de Recordatorios.** Esta sección sustituye las excepciones y mecanismos de avisos accionables descritos históricamente abajo. No se publican títulos ni avisos de este módulo, no se solicita permiso ni existe acción de completar desde Android. El adaptador solo cancela su espacio previo, con PendingIntent explícitas/inmutables y tokens históricos; no cancela canales ni alarmas de otros módulos. La limpieza no consulta datos sensibles ni cambia el estado de un recordatorio. Datos e historiales permanecen en SQLCipher.

**Notas.** Títulos, contenido e imágenes viven en la misma base privada SQLCipher y se revalida relación/perfil/paciente y autorización vigente en cada operación. Ningún texto, imagen, identificador médico ni ruta se incorpora a logs, Intents, SavedState o servicios. No hay IA, red, exportación ni permisos nuevos. Editar y eliminar conserva instantáneas anteriores; eliminar retira la nota de la vista sin destruir historia ni imágenes vinculadas. La migración aditiva copia una vez los textos previamente visibles y conserva el origen médico transcrito y todos los originales clínicos.

Galería utiliza Photo Picker y copia bytes optimizados, sin depender de la URI de origen. Cámara externa reutiliza el FileProvider privado ya acotado a cache y las concesiones temporales existentes; el temporal se revoca/elimina al terminar, cancelar o fallar y no se presenta como cifrado. Tras recodificar se eliminan metadatos EXIF. Entrada limitada a 16 MiB/100 MP, salida de hasta 1280 px y 512 KiB. Notas muestra explícitamente miniaturas dentro de su mosaico, a diferencia de las fotografías clínicas de Observaciones cuyo comportamiento se conserva. La lectura sigue requiriendo acceso protegido y autorización; decodificación limitada fuera del hilo principal.

No cambian backup/transferencias excluidos, claves Keystore, permisos clínicos ni dependencia alguna. La verificación JVM/compilación no acredita migración real en SQLCipher, concesiones de cámara, accesibilidad ni comportamiento de Android en un dispositivo.


## Avisos accionables de Recordatorios · ampliación autorizada

La petición explícita de mostrar el título en la notificación autoriza una excepción limitada al texto privado de los avisos de Recordatorios: el cuerpo privado puede contener ese título; la versión pública es genérica y la visibilidad es PRIVATE. La preferencia de privacidad del sistema/canal sigue siendo autoridad y no se promete ocultación absoluta si la persona decide mostrar contenido en bloqueo. No se incluyen detalles, nombres del paciente ni datos de otros módulos; no se escriben textos o identificadores crudos en Intents, preferencias, logs ni registros técnicos.

Las PendingIntent son explícitas e inmutables, con token opaco vinculado a la versión/programación. El receptor de completar no se exporta, valida acción/token, relación vigente y estado cifrado antes de una operación idempotente. La acción exige dispositivo desbloqueado; desde API31 solicita autenticación mediante la acción nativa. El acceso al detalle por MainActivity usa la misma validación en onCreate/onNewIntent y espera al desbloqueo de Bitavida. No se reenvían Intents anidados ni destinos externos proporcionados por extras. Para conservar una apertura pendiente durante recreación se admite únicamente su token opaco en el Bundle; se consume al abrir y nunca incorpora títulos, detalles, borradores ni identificadores crudos.

Entregar, cerrar o descartar una notificación no modifica el recordatorio. Los recibos opacos existentes evitan relanzamientos tras descarte; completar conserva la hora real de cierre, cancela aviso y alarma y no vuelve a cerrar revisiones posteriores por un token obsoleto. No hay FGS permanente, permiso exacto nuevo ni pantalla de alarma. Esta sección sustituye exclusivamente la prohibición previa de títulos en el cuerpo privado de avisos de Recordatorios; el resto de políticas y módulos permanece vigente.

## Asistencia contextual de Recordatorios

La clasificación se ejecuta solo sobre el título voluntario al terminar de editar o solicitar ayuda. Las comparaciones de duplicados se limitan a títulos activos del mismo paciente, excluyendo el propio registro y todos los cerrados; las palabras genéricas no bastan para proponer semejanza aproximada. El texto original permanece hasta una aceptación explícita. Propuestas ignoradas y títulos ya consultados solo se recuerdan en memoria de la sesión.

El selector reutiliza un catálogo estático sin acceder a otros módulos. Elegir una idea conserva la programación y el aviso existentes: nunca los infiere ni crea una operación persistente. Guías y ayuda ya no dispone de callback para crear, y no incorpora un segundo formulario. Se mantienen los mismos permisos, cifrado, revisiones, avisos y ausencia de red, sin nuevos almacenamientos ni modelos.


## Ampliación de IA organizativa de Recordatorios

La ayuda explícita recibe únicamente el texto voluntario de su campo. La detección local de posibles duplicados compara títulos de recordatorios activos del paciente actual; no consulta medicamentos, mediciones, observaciones, fotografías ni otros repositorios clínicos. El asistente educativo no recibe esos títulos ni la identidad del perfil. Sus respuestas provienen de un catálogo editorial empaquetado y del mismo clasificador local, sin red, entrenamiento con preguntas ni contenido generativo.

Las propuestas nunca se aplican, programan ni guardan por sí solas. La selección abre un borrador y la persona confirma título, programación y aviso. Las semejanzas no bloquean, fusionan ni eliminan registros. Consultas, propuestas y advertencias viven en memoria y se invalidan al cerrar o cambiar el texto; no se registran en logs, preferencias ni SavedState. Las categorías educativas no se persisten en recordatorios.

Se conserva cifrado, autorización por relación, revisiones, backup excluido, permisos y notificaciones existentes. No hay nueva migración, servicio, dependencia ni exportación. La cobertura sintética del clasificador no se presenta como garantía para consultas libres, validación clínica ni revisión de seguridad en dispositivo.


## Recordatorios cotidianos · esquema 21

Los títulos y detalles libres se protegen en la base SQLCipher existente y nunca se escriben en logs, preferencias, Intents ni notificaciones. La relación cuidador/paciente y su autorización vigente se revalidan al leer/escribir. Las revisiones conservan los cambios sin eliminación física ni migración destructiva. El módulo no introduce consentimiento clínico, red, IA, adjuntos, exportación ni permisos nuevos.

Los avisos usan texto genérico y un digest opaco por recordatorio/revisión/programación. Los receptores no exportados validan la acción y el token, vuelven a consultar el estado cifrado y nunca modifican registros por entregar o ignorar un aviso. El registro técnico de alarmas/recibos contiene solo tokens opacos y tiempos, sin texto ni identificadores crudos. Se emplean alarmas inexactas; denegar POST_NOTIFICATIONS o el canal no impide guardar. Backup y transferencia permanecen excluidos. La reapertura/migración cifrada y la entrega tras reinicio se verifican en dispositivo por separado; no se atribuyen a pruebas JVM.


## Ampliación Observaciones · esquema 20

Guías y ayuda amplía el modelo MNB empaquetado y una biblioteca Kotlin incluida en la aplicación. No añade permiso `INTERNET`, descarga, telemetría, modelo generativo ni persistencia de preguntas. El motor recibe únicamente el texto que la persona escribe en ese campo, con límite local; no accede a registros, nombres, mediciones, historiales ni fotografías. Las respuestas son fichas cerradas pendientes de revisión clínica humana, y la confianza insuficiente produce incertidumbre en vez de una explicación inventada. Las referencias web son metadatos locales; abrir una dirección exige acción explícita y no es necesario para leer la ficha offline.


Observaciones mantiene la base única Room/SQLCipher con claves Keystore, sin Internet, cuentas ni servicios. Las escrituras revalidan en transacción relación vigente y consentimiento sensible local existente. Los registros originales son inmutables; las correcciones de hechos y los cambios de seguimiento crean revisiones append-only con actor, motivo y captura; claves e índices únicos evitan doble guardado y más de un resultado por ocurrencia. No hay eliminación de registros ni correcciones silenciosas.

Las imágenes elegidas explícitamente mediante Photo Picker o capturadas con la cámara externa se copian a BLOB en SQLCipher, con identidad opaca, límites de tamaño, reducción de dimensiones y recodificación JPEG que elimina EXIF. La cámara recibe una URI temporal de un FileProvider no exportado y limitado a `cache/observation_camera`; no se solicitan permisos de cámara ni almacenamiento. Ese JPEG temporal está en almacenamiento privado de la app, no en SQLCipher: se revoca y elimina al completar, cancelar o fallar, por lo que la UI no lo presenta como cifrado. No se conserva la URI de origen. Solo se descartan fotografías definitivas sin vínculo; una fotografía vinculada a un hecho o corrección no se elimina desde el borrador. No se añaden exportación, respaldo ni recuperación: las limitaciones generales existentes continúan. Las superficies de resumen e historial no decodifican ni renderizan imágenes sensibles automáticamente; muestran únicamente un indicador. La lectura del BLOB ocurre después de una acción explícita Abrir fotografía en el detalle, dentro de un visor opaco.

Búsqueda, respuestas y borradores viven en memoria, fuera de SavedState y logs. Las notificaciones usan texto genérico sin nombres, tipos clínicos ni imágenes y no actualizan resultados. Eliminar un seguimiento anexa una revisión auditable, cancela avisos y ocurrencias futuras y conserva hechos e historia; reutilizar su configuración exige una nueva confirmación explícita. Las migraciones aditivas 18→19 y 19→20 deben validarse también en dispositivo antes de publicación; los resultados de JVM/compilación no acreditan cifrado, migración ni accesibilidad clínica. Los mensajes de seguridad son texto fijo general sin análisis clínico; revisión profesional pendiente.


## Estado actual

La baseline 0.5.4 permanece protegida. El perfil y los registros ficticios de desarrollo de Plan se almacenan en Room cifrado mediante SQLCipher y una clave envuelta por Android Keystore. No hay cuenta, backend, sincronización, analítica, telemetría ni permiso `INTERNET`; `allowBackup=false` y las exclusiones de transferencia continúan vigentes.

La integración técnica probada no equivale a auditoría criptográfica ni autoriza datos clínicos reales. Falta consentimiento explícito aplicable a datos sensibles, representación/autorización suficiente y control de acceso por operación; por eso el caso de uso rechaza el modo clínico real y la UI exige reconocer el uso de datos ficticios. No se declara cumplimiento jurídico definitivo.

## Activos y amenazas

Activos previstos: datos personales y de cuidado, indicaciones transcritas, registros declarados, adjuntos, auditoría, claves y respaldos. Amenazas relevantes:

- pérdida, robo o acceso físico al dispositivo;
- malware, componentes exportados o Intents no confiables;
- copias automáticas o archivos compartidos sin control;
- registros, reportes de fallos o telemetría con datos sensibles;
- manipulación o pérdida del historial;
- claves mal administradas;
- acceso indebido entre perfiles;
- servicios externos o modelos que reciban contenido privado.

## Principios

- Minimización, operación offline y privilegio mínimo.
- Cifrado autenticado para datos sensibles en reposo y para respaldos.
- Claves protegidas por Android Keystore; nunca embebidas ni registradas.
- Autorización por operación y perfil.
- Revisiones append-only y auditoría para correcciones.
- Datos sensibles fuera de logs, analytics, excepciones y nombres de archivos.
- Compartición solo mediante acción explícita, destino acotado y advertencia.

## Acceso local implementado en la fase 3.2

“Proteger Bitavida” es opcional y permanece desactivado por defecto. Se conserva únicamente su valor booleano en `SharedPreferences` privados; no contiene identidad, datos de cuidado, claves ni credenciales. Si la opción está desactivada, el controlador permanece en `UNLOCKED`, no invoca `BiometricPrompt` y no aplica bloqueo por segundo plano. No se añadió una dependencia de persistencia para este único valor no sensible.

Al activarla, Bitavida usa AndroidX `BiometricPrompt` y `BiometricManager` para `BIOMETRIC_STRONG` —incluido rostro solo cuando Android lo clasifica como fuerte— y `KeyguardManager` para PIN, patrón o contraseña. En API 30 a 37 permite `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`; en API 26–29 ofrece biometría fuerte y confirmación de credencial en flujos nativos separados. Si `isDeviceSecure()` y la comprobación biométrica no proporcionan un método viable, la preferencia no se activa y se ofrece abrir la configuración de seguridad de Android.

Con protección activa, `AppAccessSessionController` mantiene `LOCKED`, `AUTHENTICATING` y `UNLOCKED`. Solo el callback exitoso desbloquea; cancelación o error mantiene el contenido protegido y habilita un reintento explícito sin ciclos de prompts. `SystemClock.elapsedRealtime()` aplica el umbral de 60 segundos en segundo plano; una rotación no cuenta como ausencia. Atrás conserva la navegación Android normal y no funciona como control especial de bloqueo. El ViewModel y el borrador se conservan durante el bloqueo; un perfil confirmado permanece en el repositorio cifrado.

Bitavida no establece `FLAG_SECURE` ni restricciones generales de captura o grabación. La exposición en recientes queda fuera de esta decisión y deberá resolverse posteriormente con un mecanismo que no interfiera con capturas cotidianas. El control opcional de acceso no cambia SQLCipher, Android Keystore, `allowBackup=false`, autorizaciones por operación ni validaciones de dominio. La clave Keystore del prototipo todavía no exige autenticación del usuario; vincularla requerirá migración o rotación explícita y una política de recuperación.

## Infraestructura técnica de persistencia cifrada

La prueba selecciona Room 3.0.3 con el driver de SQLCipher for Android 4.20.0 Community. La base usa una clave aleatoria de 32 bytes; no hay contraseña fija ni derivación desde datos de usuario. Android Keystore crea una clave AES-256 no exportable, restringida a GCM sin padding y con IV aleatorio, que envuelve la clave de SQLCipher. El almacenamiento privado conserva únicamente una envoltura versionada con IV y ciphertext autenticado dentro de `noBackupFilesDir`.

La apertura se serializa y ocurre fuera del hilo principal. El esquema Room 18 conserva el perfil, indicaciones, periodos, configuraciones, avisos, tomas, lecturas y seguimientos independientes de glucosa, presión arterial y peso con revisiones append-only, incluidas las correcciones de declaraciones explícitas sobre ocurrencias programadas. El peso objetivo vigente se almacena cifrado en una tabla separada y autorizada por perfil/persona; por decisión de producto no genera lecturas, eventos ni historial de cambios. Las altas, correcciones y cambios de estado son transaccionales; las migraciones explícitas hasta 15→16 no eliminan tablas ni historial. Glucosa, Presión arterial y Peso crean una revisión completa y auditoría en la misma transacción que cada alta, corrección o eliminación lógica, con actor y fechas real/de captura. La corrección de una ocurrencia declarada no realizada inserta atómicamente la lectura real y una revisión append-only de la decisión; los reintentos se resuelven por los identificadores estables de lectura y ocurrencia. La eliminación marca la fila activa y la excluye de consultas sin destruir la última instantánea auditable. Todas estas operaciones reutilizan el consentimiento sensible local-health-storage-1.0 y revalidan la relación autorizada vigente. El vínculo opcional de una toma a su ocurrencia, el instante efectivo nullable de las transiciones y la identidad de serie nullable permanecen cifrados; los registros anteriores conservan NULL y se interpretan con su propio identificador como serie sin reasociar ni reescribir historial. SQLCipher deshabilita su salida Java mediante `NoopTarget`; el código no registra claves, contenido clínico, OCR ni rutas. Las copias temporales controladas del sobre y material cifrado se sobrescriben cuando dejan de ser necesarias. El driver conserva la clave durante la conexión; la eliminación determinista de todas las copias JVM/driver no puede garantizarse.

Los borradores de glucosa, presión arterial y peso viven únicamente en ViewModel retenidos durante rotación/bloqueo; no se serializan en SavedState, preferencias ni logs. Cerrar una vista media sin guardar descarta directamente todo borrador temporal y nunca persiste cambios parciales. Los mensajes de error no incluyen valores ni notas. Las referencias programadas solo proceden de entradas internas tipadas y confirmadas; no se aceptan desde Intents ni textos inferidos. Las migraciones de la base cifrada, incluidas 15→16, 16→17 y 17→18, y la reapertura cifrada aún requieren validación instrumentada explícitamente autorizada; la entrega ejecuta únicamente compilación, JVM y lint.

No se ejecutan migraciones destructivas ni `fallbackToDestructiveMigration`. Un perfil incompleto, una relación ambigua o una escritura inválida se reportan como error de integridad y nunca se interpretan como primera instalación. Si falta la base, la envoltura o el alias Keystore de forma incoherente, si falla la autenticación GCM o si SQLCipher rechaza la apertura, se devuelve un error controlado. La prueba nunca reemplaza silenciosamente una clave, recrea la base ni borra datos para recuperarse. Todavía no existe rotación, recuperación, autenticación de la clave Keystore ni política de restablecimiento; no se inventará ninguna hasta definir sus consecuencias de pérdida de datos.

Room por sí solo no se considera cifrado. La preferencia privada de acceso no sensible se mantiene separada de Room; DataStore continuará reservado para preferencias no sensibles cuando su volumen o flujo justifique incorporarlo. Los adjuntos requerirán nombres opacos, almacenamiento privado y cifrado autenticado separado.

## Consultas educativas de Bitavida IA

Bitavida IA no recibe repositorios clínicos, perfiles, lecturas ni identificadores personales. Solo procesa la pregunta escrita y un grupo de navegación. El modelo propio y las 30 fichas se incluyen en el APK; no se añaden red, servicios, telemetría ni dependencias. La carga y clasificación se ejecutan fuera del hilo principal. Ninguna pregunta se persiste, serializa en SavedState, registra en logs o emplea para entrenar.

La sesión educativa permanece únicamente en el ViewModel: pila HOME/ANSWER/LIBRARY/DETAIL/ABOUT, preguntas, resultados, búsqueda y filtros. Por cada entrada se conservan el índice y desplazamiento de LazyList y los estados de información complementaria y fuentes. Rotar no exige escribir estos datos en disco; Atrás entre vistas restaura la sesión y Atrás para abandonar ayuda la descarta. El cierre de sesión educativa invalida consultas pendientes y los resultados tardíos no pueden reemplazar una consulta nueva.

El clasificador puede equivocarse: se aplican rechazo y una guardia conservadora de alcance para cifras y solicitudes clínicas, sin presentarlos como mecanismos exhaustivos de detección médica. Ante incertidumbre puede ofrecer temas cercanos para elección explícita. Las respuestas son texto fijo sin interpolación de la entrada ni acciones de escritura clínica. La lectura directa de biblioteca y relacionados usa identificadores exactos y sigue disponible si falla el modelo. Las fuentes se consultan en un apartado secundario y la revisión clínica pendiente permanece visible; los errores no exponen texto de excepciones. No se modifican el cifrado, el backup ni las transferencias.

La ampliación farmacológica mantiene un modelo único y fichas locales referenciadas. La única integración con un medicamento registrado recibe su etiqueta para comprobar una identidad genérica exacta y ofrecer una acción educativa explícita; no recibe dosis, horarios, diagnósticos, otras medicinas ni datos del paciente. Las semejanzas nominales en preguntas solo proponen opciones; las marcas no se traducen por suposición. No hay evaluación de interacciones de una lista personal ni cambios de tratamiento. Las fuentes externas se abren únicamente al pulsar Consultar documento oficial, mediante el navegador del dispositivo; las respuestas y referencias incluidas continúan disponibles offline y no se añade INTERNET a Bitavida.

## Respaldos y transferencias

Android Auto Backup y device transfer permanecen excluidos. Los respaldos del producto serán explícitos, portables, cifrados, autenticados y versionados. Restaurar exigirá validación de integridad, esquema y autorización. No se implementará recuperación que debilite el cifrado.

## Red y servicios externos

No se añadirá `INTERNET` sin cambio de alcance aprobado, diagrama de datos, base legal/consentimiento y threat model actualizado. Ningún dato personal, texto, imagen, OCR o telemetría se enviará a servicios externos por defecto.

## IPC, archivos y exportación

Los componentes permanecerán no exportados salvo necesidad documentada. Las entradas externas se validarán. Los URI compartidos serán temporales, de mínimo privilegio y revocables. La exportación evitará cachés públicas y mostrará sensibilidad antes de confirmar.

## Asistencia local

Las funciones previstas en [ASSISTANCE.md](ASSISTANCE.md) operarán localmente, con límites de recursos y confirmación humana. No se usarán modelos para farmacología ni OCR clínico. Los modelos, catálogos y licencias deberán versionarse y verificarse.

## Verificación de seguridad

La prueba JVM valida el formato del sobre: codificación/decodificación y rechazo de truncamiento o versión desconocida. El 9 de noviembre de 2026 se ejecutó una prueba Android dirigida en un dispositivo Android 16: creó la base cifrada con datos ficticios, escribió y leyó, cerró y reabrió recuperando la clave mediante Keystore, rechazó una clave SQLCipher incorrecta y conservó el archivo original. La comprobación con SQLite de plataforma se realiza sobre una copia descartable porque el primer intento demostró que ese lector puede truncar incluso un archivo solicitado como solo lectura.

Esta ejecución no constituye una auditoría criptográfica completa. Siguen pendientes la reapertura del perfil y la migración 1→2 en el Samsung objetivo, la prueba preparada de pérdida del alias Keystore, la inspección de secretos/contenido en logs, la carga y alineación con páginas de 16 KB en los ABI distribuidos, y pruebas de manipulación del sobre, concurrencia, actualización de SQLCipher, bloqueo del dispositivo y fallos de almacenamiento.

## Auditoría y respuesta

La auditoría no contendrá el dato clínico completo cuando baste un identificador y metadatos de operación. Las reactivaciones inmediatas, programadas, reprogramadas y canceladas se conservan como revisiones; ninguna actualización destructiva elimina periodos anteriores. Antes de una versión funcional se requieren pruebas de autorización, migración, respaldo/restauración, manipulación, pérdida de claves, exportación e incident response.

## Pendientes obligatorios

Consentimiento sensible aplicable, representación/autorización suficiente y autorización por operación son bloqueantes para salir del modo ficticio. También siguen pendientes validación de acceso en API 26/Samsung, claves autenticadas y rotación, recuperación, invalidación/cambio de credencial, revocación, retención/borrado, respaldos, revisión legal/profesional, auditoría clínica completa y pruebas de seguridad restantes.
