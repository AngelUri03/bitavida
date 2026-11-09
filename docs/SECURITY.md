# Seguridad y privacidad

## Estado actual

La baseline 0.5.4 sigue siendo una demo con datos ficticios. La rama `feature/secure-persistence-spike` añade infraestructura técnica aislada para evaluar una base cifrada, sin conectarla a la UI ni persistir información de cuidado. No hay cuenta, backend, sincronización, analítica, telemetría ni permiso `INTERNET`. El manifiesto conserva `allowBackup=false` y las reglas que excluyen copias y transferencias.

Esta prueba no equivale a una validación criptográfica ni autoriza almacenar información sensible hasta completar las comprobaciones Android y definir las políticas pendientes.

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

## Prueba técnica de persistencia cifrada

La prueba selecciona Room 3.0.3 con el driver de SQLCipher for Android 4.20.0 Community. La base usa una clave aleatoria de 32 bytes; no hay contraseña fija ni derivación desde datos de usuario. Android Keystore crea una clave AES-256 no exportable, restringida a GCM sin padding y con IV aleatorio, que envuelve la clave de SQLCipher. El almacenamiento privado conserva únicamente una envoltura versionada con IV y ciphertext autenticado dentro de `noBackupFilesDir`.

La apertura se serializa y ocurre fuera del hilo principal. SQLCipher deshabilita su salida Java mediante `NoopTarget`; el código no registra claves, contenido ni rutas. Las copias temporales controladas del sobre y del material cifrado se sobrescriben cuando dejan de ser necesarias. El driver necesita conservar la clave de base durante la vida de la conexión; la eliminación determinista de todas las copias administradas por JVM/driver no puede garantizarse y es una limitación explícita.

No se ejecutan migraciones destructivas ni `fallbackToDestructiveMigration`. Si falta la base, la envoltura o el alias Keystore de forma incoherente, si falla la autenticación GCM o si SQLCipher rechaza la apertura, se devuelve un error controlado. La prueba nunca reemplaza silenciosamente una clave, recrea la base ni borra datos para recuperarse. Todavía no existe rotación, recuperación, autenticación biométrica ni política de restablecimiento; no se inventará ninguna hasta definir sus consecuencias de pérdida de datos.

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

Validación Android de la combinación Room/SQLCipher/Keystore, rotación y recuperación de claves, sesión/bloqueo, perfiles y autorización, política de retención/borrado, formato de respaldo, revisión legal y profesional, y pruebas de seguridad. La prueba técnica implementa solo el mecanismo mínimo descrito; ninguno de los controles funcionales pendientes se considera resuelto por esta documentación.
