# Seguridad y privacidad

## Estado actual

La baseline 0.5.4 permanece protegida. El perfil no clínico, la relación de cuidado y las decisiones legales se almacenan en Room cifrado mediante SQLCipher y una clave envuelta por Android Keystore. No hay cuenta, backend, sincronización, analítica, telemetría ni permiso `INTERNET`. El manifiesto conserva `allowBackup=false` y las reglas que excluyen copias y transferencias.

La integración técnica fue probada previamente de forma dirigida en Android, pero no equivale a una auditoría criptográfica ni autoriza capturar información clínica sensible. La identidad del responsable, los procedimientos legales y las comprobaciones pendientes continúan bloqueando la publicación.

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

La apertura se serializa y ocurre fuera del hilo principal. El esquema Room 2 añade cuidador, persona atendida, relación, consentimiento y aceptaciones legales; la creación del agregado es una única transacción y la migración explícita 1→2 conserva el esquema técnico anterior. SQLCipher deshabilita su salida Java mediante `NoopTarget`; el código no registra claves, contenido ni rutas. Las copias temporales controladas del sobre y del material cifrado se sobrescriben cuando dejan de ser necesarias. El driver necesita conservar la clave de base durante la vida de la conexión; la eliminación determinista de todas las copias administradas por JVM/driver no puede garantizarse y es una limitación explícita.

No se ejecutan migraciones destructivas ni `fallbackToDestructiveMigration`. Un perfil incompleto, una relación ambigua o una escritura inválida se reportan como error de integridad y nunca se interpretan como primera instalación. Si falta la base, la envoltura o el alias Keystore de forma incoherente, si falla la autenticación GCM o si SQLCipher rechaza la apertura, se devuelve un error controlado. La prueba nunca reemplaza silenciosamente una clave, recrea la base ni borra datos para recuperarse. Todavía no existe rotación, recuperación, autenticación de la clave Keystore ni política de restablecimiento; no se inventará ninguna hasta definir sus consecuencias de pérdida de datos.

Room por sí solo no se considera cifrado. La preferencia privada de acceso no sensible se mantiene separada de Room; DataStore continuará reservado para preferencias no sensibles cuando su volumen o flujo justifique incorporarlo. Los adjuntos requerirán nombres opacos, almacenamiento privado y cifrado autenticado separado.

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

La auditoría no contendrá el dato clínico completo cuando baste un identificador y metadatos de operación. Antes de una versión funcional se requieren pruebas de autorización, migración, respaldo/restauración, manipulación, pérdida de claves, exportación e incident response.

## Pendientes obligatorios

Validación del acceso con `BiometricPrompt` en API 26 y en el Samsung objetivo, vinculación o rotación de claves autenticadas, recuperación ante pérdida de acceso, pruebas de invalidación y cambio de credencial, revocación operable, política de retención/borrado, formato de respaldo, revisión legal/profesional y pruebas de seguridad restantes. La persistencia del perfil no resuelve autorización por operación, auditoría clínica ni los controles funcionales pendientes.
