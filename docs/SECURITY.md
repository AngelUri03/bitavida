# Seguridad y privacidad

## Estado actual

La baseline 0.5.4 es una demo con datos ficticios y estado efímero. No tiene cuenta, backend, sincronización, analítica, telemetría ni permiso `INTERNET`. El manifiesto mantiene `allowBackup=false` y reglas que excluyen copias y transferencias. No se persisten datos de cuidado.

Estas protecciones actuales no sustituyen la arquitectura de seguridad necesaria antes de almacenar información sensible.

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

## Persistencia prevista

Room no se considera cifrado por defecto. Antes de persistir datos reales deben definirse biblioteca/mecanismo de cifrado, derivación y rotación de claves, bloqueo, recuperación, migraciones y borrado seguro. DataStore se limita a preferencias no sensibles. Los adjuntos requieren nombres opacos, almacenamiento privado y cifrado autenticado.

## Respaldos y transferencias

Android Auto Backup y device transfer permanecen excluidos. Los respaldos del producto serán explícitos, portables, cifrados, autenticados y versionados. Restaurar exigirá validación de integridad, esquema y autorización. No se implementará recuperación que debilite el cifrado.

## Red y servicios externos

No se añadirá `INTERNET` sin cambio de alcance aprobado, diagrama de datos, base legal/consentimiento y threat model actualizado. Ningún dato personal, texto, imagen, OCR o telemetría se enviará a servicios externos por defecto.

## IPC, archivos y exportación

Los componentes permanecerán no exportados salvo necesidad documentada. Las entradas externas se validarán. Los URI compartidos serán temporales, de mínimo privilegio y revocables. La exportación evitará cachés públicas y mostrará sensibilidad antes de confirmar.

## Asistencia local

Las funciones previstas en [ASSISTANCE.md](ASSISTANCE.md) operarán localmente, con límites de recursos y confirmación humana. No se usarán modelos para farmacología ni OCR clínico. Los modelos, catálogos y licencias deberán versionarse y verificarse.

## Auditoría y respuesta

La auditoría no contendrá el dato clínico completo cuando baste un identificador y metadatos de operación. Antes de una versión funcional se requieren pruebas de autorización, migración, respaldo/restauración, manipulación, pérdida de claves, exportación e incident response.

## Pendientes obligatorios

Selección del cifrado, gestión de claves, sesión/bloqueo, perfiles y autorización, política de retención/borrado, formato de respaldo, revisión legal y profesional, y pruebas de seguridad. Ninguno se considera implementado por esta documentación.
