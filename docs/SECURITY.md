# Seguridad y privacidad

## Estado actual

La baseline 0.5.4 permanece protegida. La infraestructura Room/SQLCipher/Keystore ya está integrada en el proyecto, pero continúa aislada de los flujos funcionales: el perfil de cuidado de la fase 3.1 reside únicamente en memoria. No hay cuenta, backend, sincronización, analítica, telemetría ni permiso `INTERNET`. El manifiesto conserva `allowBackup=false` y las reglas que excluyen copias y transferencias.

La integración técnica fue probada de forma dirigida en Android, pero no equivale a una auditoría criptográfica ni autoriza almacenar información personal o sensible hasta completar el control de acceso, las políticas y las comprobaciones pendientes.

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

Bitavida usa AndroidX `BiometricPrompt` y `BiometricManager` para biometría `BIOMETRIC_STRONG` —incluido reconocimiento facial únicamente cuando Android lo clasifica como fuerte— y `KeyguardManager` para la credencial segura del dispositivo. No guarda ni recibe PIN, patrón, contraseña o plantillas biométricas. El único permiso añadido es `USE_BIOMETRIC`; no existe `INTERNET`, cuenta, servidor ni credencial propia.

En API 30 a 37 el prompt permite `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`. Android 10 o anterior no soporta esa combinación: en API 26–29 el adaptador ofrece primero biometría fuerte y usa su acción negativa para abrir `createConfirmDeviceCredentialIntent`; si no hay biometría fuerte, abre directamente la confirmación nativa cuando `isDeviceSecure()` es verdadero. Si el dispositivo no tiene bloqueo seguro, la pantalla enlaza a la configuración de seguridad de Android y nunca omite la autenticación.

`AppAccessSessionController` mantiene `LOCKED`, `AUTHENTICATING` y `UNLOCKED`. La sesión protegida inicia bloqueada; solo el callback exitoso puede desbloquear. Cancelar, rechazar o agotar intentos conserva o restaura el bloqueo. El tiempo usa `SystemClock.elapsedRealtime()`: al regresar después de 60 segundos o más en segundo plano se bloquea, mientras una rotación no cuenta como ausencia ni relanza el prompt. Atrás dentro del área protegida ejecuta bloqueo manual inmediato y no atraviesa el acceso. El bloqueo conserva ViewModel, borrador y repositorio en memoria.

Para recientes, la ventana activa `FLAG_SECURE` al perder primer plano y mientras la sesión está bloqueada. Cuando vuelve a estar desbloqueada y enfocada retira la bandera, de modo que el snapshot protegido no revela contenido pero las capturas normales del usuario siguen disponibles durante el uso autorizado. Esta política deberá validarse en el Samsung objetivo porque el momento exacto del snapshot depende del fabricante.

La pérdida de acceso no tiene atajos, credenciales maestras ni sustitución silenciosa de claves. La clave Keystore del prototipo actual todavía no exige autenticación del usuario; esta fase protege la sesión visual y funcional, pero no vincula la autenticación a SQLCipher. Hacerlo requerirá migración o rotación explícita, una política de recuperación y pruebas de cambio de credencial e invalidación biométrica antes de almacenar perfiles.

## Infraestructura técnica de persistencia cifrada

La prueba selecciona Room 3.0.3 con el driver de SQLCipher for Android 4.20.0 Community. La base usa una clave aleatoria de 32 bytes; no hay contraseña fija ni derivación desde datos de usuario. Android Keystore crea una clave AES-256 no exportable, restringida a GCM sin padding y con IV aleatorio, que envuelve la clave de SQLCipher. El almacenamiento privado conserva únicamente una envoltura versionada con IV y ciphertext autenticado dentro de `noBackupFilesDir`.

La apertura se serializa y ocurre fuera del hilo principal. SQLCipher deshabilita su salida Java mediante `NoopTarget`; el código no registra claves, contenido ni rutas. Las copias temporales controladas del sobre y del material cifrado se sobrescriben cuando dejan de ser necesarias. El driver necesita conservar la clave de base durante la vida de la conexión; la eliminación determinista de todas las copias administradas por JVM/driver no puede garantizarse y es una limitación explícita.

No se ejecutan migraciones destructivas ni `fallbackToDestructiveMigration`. Si falta la base, la envoltura o el alias Keystore de forma incoherente, si falla la autenticación GCM o si SQLCipher rechaza la apertura, se devuelve un error controlado. La prueba nunca reemplaza silenciosamente una clave, recrea la base ni borra datos para recuperarse. Todavía no existe rotación, recuperación, autenticación de la clave Keystore ni política de restablecimiento; no se inventará ninguna hasta definir sus consecuencias de pérdida de datos.

Room por sí solo no se considera cifrado. DataStore continúa limitado a preferencias no sensibles. Los adjuntos requerirán nombres opacos, almacenamiento privado y cifrado autenticado separado.

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

Esta ejecución no constituye una auditoría criptográfica completa. Siguen pendientes la prueba preparada de pérdida del alias Keystore, la inspección de secretos/contenido en logs, la carga y alineación con páginas de 16 KB en los ABI distribuidos, y pruebas de manipulación del sobre, concurrencia, migración, actualización de SQLCipher, bloqueo del dispositivo y fallos de almacenamiento.

## Auditoría y respuesta

La auditoría no contendrá el dato clínico completo cuando baste un identificador y metadatos de operación. Antes de una versión funcional se requieren pruebas de autorización, migración, respaldo/restauración, manipulación, pérdida de claves, exportación e incident response.

## Pendientes obligatorios

Validación del acceso con `BiometricPrompt` en API 26 y en el Samsung objetivo, vinculación o rotación de claves autenticadas, recuperación ante pérdida de acceso, pruebas de invalidación y cambio de credencial, conexión autorizada de perfiles, política de retención/borrado, formato de respaldo, revisión legal/profesional y pruebas de seguridad restantes. La infraestructura actual implementa solo el mecanismo criptográfico mínimo descrito; ninguno de los controles funcionales pendientes se considera resuelto por esta documentación.
