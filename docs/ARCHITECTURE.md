# Arquitectura de Bitavida

## Estado actual

El proyecto contiene una sola aplicación Android y el módulo `:app`. La implementación vigente mantiene la demo Compose offline y añade una prueba técnica aislada de persistencia cifrada; todavía no existe un repositorio funcional, inyección de dependencias ni navegación por rutas. La prueba no está conectada a las pantallas ni almacena datos clínicos.

Estructura real:

- `com.bitavida.app.MainActivity`: punto de entrada.
- `ui.demo`: pantalla demostrativa y estado visual.
- `ui.components`: componentes, hilo, microinteracciones y superficies.
- `ui.brand`: monograma, geometría y política de movimiento.
- `ui.theme`: color, tipografía, formas y espaciado.
- `domain.model`: tipos de dominio Kotlin puros ya introducidos.
- `data.local.secure`: prueba técnica de Room/SQLCipher, gestión de la clave envuelta y una entidad exclusivamente ficticia.

La selección inferior distinta de “Mi día” es demostrativa y no cambia de destino.

## Arquitectura objetivo

```text
UI Compose → ViewModel → casos de uso → interfaces de repositorio
                                      ← implementaciones de datos
```

- El dominio será Kotlin puro y no dependerá de Android, Compose, Room ni DTO.
- Los ViewModel expondrán `StateFlow` inmutable, recibirán acciones y representarán efectos únicos explícitamente.
- Los casos de uso aplicarán invariantes, autorización y auditoría.
- Las interfaces de repositorio vivirán hacia el dominio; Room será un detalle de datos.
- Los mapeos entre entidades persistidas y dominio serán explícitos.

Esta arquitectura objetivo aún no está implementada y no debe presentarse como existente.

## Estado, escritura y auditoría

La demo usa estado Compose efímero y restaurable para interacciones visuales. Los datos reales requerirán ViewModel y casos de uso. Las correcciones clínicas o administrativas serán append-only: conservarán la versión previa, actor, motivo y fecha. No habrá sobrescritura destructiva silenciosa.

## Prueba técnica de persistencia cifrada

La rama `feature/secure-persistence-spike` valida una integración mínima, todavía no adoptada para datos reales:

- Room 3.0.3 estable con generación Kotlin mediante KSP 2.3.12 y API `SQLiteDriver`.
- AndroidX SQLite 2.7.0 y `net.zetetic:sqlcipher-android:4.20.0` Community mediante `SQLCipherDriver`; no se usa el artefacto deprecado `android-database-sqlcipher`.
- Android Keystore envuelve una clave aleatoria de 256 bits con AES-256-GCM. Solo el sobre versionado (IV y ciphertext autenticado) se guarda en `noBackupFilesDir`.
- `SecureDatabaseProvider` centraliza la apertura en `Dispatchers.IO`; el DAO suspendido fuerza una primera consulta para detectar una clave o archivo inválidos.
- La única tabla, `technical_probe`, contiene datos ficticios y no modela perfiles, salud ni cuidado.

Se eligió Room 3 frente a Room 2.8.5 porque ambos son estables, pero Room 3 ofrece la integración moderna basada en driver, operaciones suspendidas y generación Kotlin que necesita esta prueba. El proyecto existente usa Kotlin 2.2.10, AGP 9.5.1, compileSdk 37 y minSdk 26; KSP 2.3.12 es compatible con esa combinación. SQLCipher Android soporta API 23+ y entrega bibliotecas nativas para los ABI Android habituales. La publicación 4.20.0 incorpora la preparación moderna para páginas nativas de 16 KB, que aún debe comprobarse en dispositivo/paquete final.

Room/AndroidX SQLite usan Apache-2.0; SQLCipher Community y sus componentes comunitarios usan licencias permisivas tipo BSD. El APK debug pasó de 12,623,950 a 20,147,301 bytes: +7,523,351 bytes (aprox. 7.18 MiB), principalmente por las bibliotecas nativas incluidas para varios ABI; una distribución release/AAB puede tener otro costo y deberá medirse antes de adoptar la solución. DataStore seguirá limitado a preferencias no sensibles. Adjuntos, respaldos, rotación y recuperación de claves permanecen pendientes según [SECURITY.md](SECURITY.md).

## Navegación

La barra inferior actual pertenece a la demo. La navegación real, rutas, back stack, deep links y autorización siguen pendientes. No se añadirá una biblioteca de navegación hasta definir esos flujos.

## Offline y concurrencia

Las funciones esenciales operarán localmente. No se añadirá `INTERNET` sin cambio de alcance y threat model. Si en el futuro existe concurrencia, las revisiones y conflictos se resolverán explícitamente; nunca se fusionarán datos clínicos automáticamente.

## Pruebas

- JVM: geometría de marca, continuidad del hilo y formato versionado del sobre criptográfico (round trip y rechazo de entradas truncadas/versiones desconocidas).
- Android preparada, no ejecutada automáticamente: creación cifrada, lectura/escritura, cierre/reapertura, clave incorrecta, rechazo por SQLite sin cifrar, pérdida de clave Keystore y conservación del archivo ante error.
- Android pendientes: ejecutar esas verificaciones en dispositivo API 26 y API 33+, revisar ausencia de secretos en logs y confirmar alineación/carga nativa con páginas de 16 KB.
- Pendientes funcionales: casos de uso, repositorios de dominio, migraciones, autorización, accesibilidad con dispositivo y pruebas end-to-end.

## Dependencias y evolución

Toda dependencia nueva exige necesidad, alternativa, compatibilidad, licencia y costo de tamaño. La prueba incorpora Room 3, KSP, AndroidX SQLite y SQLCipher únicamente para evaluar persistencia cifrada; no constituye aprobación para datos clínicos hasta completar la validación Android y la política de claves. Se mantiene un solo módulo hasta que métricas reales justifiquen modularizar. Las decisiones todavía no implementadas se documentan como previstas, no como capacidades disponibles.
