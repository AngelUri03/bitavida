# Arquitectura de Bitavida

## Estado actual

El proyecto contiene una sola aplicación Android y el módulo `:app`. La implementación vigente mantiene la demo Compose offline, integra la infraestructura técnica de persistencia cifrada sin conectarla a datos funcionales, añade el primer flujo transitorio de perfiles e incorpora un control de acceso local antes de mostrar contenido protegido. El acceso usa exclusivamente autenticadores nativos del dispositivo; no almacena credenciales, no requiere cuenta y no incorpora navegación por rutas.

Estructura real:

- `com.bitavida.app.MainActivity`: punto de entrada.
- `ui.demo`: pantalla demostrativa y estado visual.
- `ui.components`: componentes, hilo, microinteracciones y superficies.
- `ui.brand`: monograma, geometría y política de movimiento.
- `ui.theme`: color, tipografía, formas y espaciado.
- `domain.model` y `domain.validation`: perfiles, consentimiento, procedencia y reglas en Kotlin puro.
- `domain.repository` y `domain.usecase`: contrato de perfiles y casos de uso de creación/consulta.
- `data.careprofile`: repositorio transitorio en memoria, limitado a un perfil.
- `ui.careprofile`: estado inmutable, acciones, efectos, ViewModel y scaffold Compose continuo para la configuración del perfil.
- `domain.access`: política de 60 segundos y controlador puro de los estados `LOCKED`, `AUTHENTICATING` y `UNLOCKED`.
- `platform.access`: adaptador Android de `BiometricPrompt`, `BiometricManager` y `KeyguardManager`.
- `ui.access`: contrato, ViewModel y pantalla Compose de acceso; no conoce detalles de autenticación Android.
- `data.local.secure`: infraestructura técnica integrada de Room/SQLCipher, gestión de la clave envuelta y una entidad exclusivamente ficticia.

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

La rebanada de perfiles implementa esta dirección de dependencias sin inyección de dependencias externa. Las demás áreas continúan como arquitectura objetivo y no deben presentarse como existentes.

## Estado, sesión, escritura y auditoría

`AppAccessSessionController` es la única autoridad de la sesión protegida. Comienza en `LOCKED`, solo una devolución exitosa del autenticador cambia `AUTHENTICATING` a `UNLOCKED`, y cancelaciones o errores regresan a `LOCKED`. `MainActivity` aporta tiempo monotónico de Android, eventos de segundo plano y el adaptador; el ViewModel expone `StateFlow` inmutable. Rotar no recrea el ViewModel ni relanza el prompt, y bloquear no destruye el ViewModel del onboarding ni su repositorio en memoria.

La demo usa estado Compose efímero y restaurable para interacciones visuales. Los datos reales requerirán ViewModel y casos de uso. Las correcciones clínicas o administrativas serán append-only: conservarán la versión previa, actor, motivo y fecha. No habrá sobrescritura destructiva silenciosa.

## Infraestructura técnica de persistencia cifrada

La integración incorporada en `main` mediante `fa5f9ff` valida el mecanismo mínimo, todavía no adoptado para perfiles ni datos reales:

- Room 3.0.3 estable con generación Kotlin mediante KSP 2.3.12 y API `SQLiteDriver`.
- AndroidX SQLite 2.7.0 y `net.zetetic:sqlcipher-android:4.20.0` Community mediante `SQLCipherDriver`; no se usa el artefacto deprecado `android-database-sqlcipher`.
- Android Keystore envuelve una clave aleatoria de 256 bits con AES-256-GCM. Solo el sobre versionado (IV y ciphertext autenticado) se guarda en `noBackupFilesDir`.
- `SecureDatabaseProvider` centraliza la apertura en `Dispatchers.IO`; el DAO suspendido fuerza una primera consulta para detectar una clave o archivo inválidos.
- La única tabla, `technical_probe`, contiene datos ficticios y no modela perfiles, salud ni cuidado.

Se eligió Room 3 frente a Room 2.8.5 porque ambos son estables, pero Room 3 ofrece la integración moderna basada en driver, operaciones suspendidas y generación Kotlin que necesita esta prueba. El proyecto existente usa Kotlin 2.2.10, AGP 9.5.1, compileSdk 37 y minSdk 26; KSP 2.3.12 es compatible con esa combinación. SQLCipher Android soporta API 23+ y entrega bibliotecas nativas para los ABI Android habituales. La publicación 4.20.0 incorpora la preparación moderna para páginas nativas de 16 KB, que aún debe comprobarse en dispositivo/paquete final.

Room/AndroidX SQLite usan Apache-2.0; SQLCipher Community y sus componentes comunitarios usan licencias permisivas tipo BSD. El APK debug pasó de 12,623,950 a 20,147,301 bytes: +7,523,351 bytes (aprox. 7.18 MiB), principalmente por las bibliotecas nativas incluidas para varios ABI; una distribución release/AAB puede tener otro costo y deberá medirse antes de adoptar la solución. DataStore seguirá limitado a preferencias no sensibles. Adjuntos, respaldos, rotación y recuperación de claves permanecen pendientes según [SECURITY.md](SECURITY.md).

## Navegación

La barra inferior actual pertenece a la demo y permanece intacta. `MainActivity` muestra primero el flujo de perfiles y, al cancelar o terminar, inicia una sesión protegida bloqueada. Solo después de la confirmación nativa presenta “Mi día” sin modificar su composición ni destinos. Atrás en el área protegida bloquea inmediatamente y no atraviesa la pantalla de acceso. La navegación real, rutas, back stack y deep links siguen pendientes; no se añade una biblioteca de navegación.

## Offline y concurrencia

Las funciones esenciales operarán localmente. No se añadirá `INTERNET` sin cambio de alcance y threat model. Si en el futuro existe concurrencia, las revisiones y conflictos se resolverán explícitamente; nunca se fusionarán datos clínicos automáticamente.

## Pruebas

- JVM: geometría de marca, continuidad del hilo y formato versionado del sobre criptográfico (round trip y rechazo de entradas truncadas/versiones desconocidas).
- Android ejecutada el 9 de noviembre de 2026 en Android 16: creación cifrada, lectura/escritura, cierre/reapertura, recuperación mediante Keystore, rechazo de clave incorrecta y conservación del archivo original.
- Android pendientes: pérdida del alias Keystore, API 26, revisión de secretos en logs y alineación/carga nativa con páginas de 16 KB.
- Perfiles: pruebas JVM puntuales de validación, autorización declarada, consentimiento vigente, normalización y límite de un perfil en memoria.
- Acceso: pruebas JVM de transiciones, rechazo de desbloqueo sin éxito, bloqueo manual y umbral exacto de 60 segundos.
- Pendientes funcionales: persistencia protegida de perfiles, migraciones, validación de autenticadores en API 26 y Samsung, accesibilidad con dispositivo y pruebas end-to-end.

## Dependencias y evolución

Toda dependencia nueva exige necesidad, alternativa, compatibilidad, licencia y costo de tamaño. La infraestructura incorporada usa Room 3, KSP, AndroidX SQLite y SQLCipher únicamente para evaluar persistencia cifrada; no constituye aprobación para datos clínicos hasta completar la validación Android y la política de claves. El flujo de perfiles no usa esa infraestructura.

La fase 3.2 añade `androidx.biometric:biometric:1.1.0`, versión estable compatible con minSdk 26 y licencia Apache-2.0. Es necesaria para unificar `BiometricPrompt` y las diferencias de compatibilidad; la alternativa de combinar directamente APIs framework y `FingerprintManager` exigiría ramas legadas y una API deprecada. El artefacto no incorpora servicios, cuenta, red ni bibliotecas nativas; su costo final depende del recorte de release y debe medirse en el paquete de distribución, por lo que no se atribuye un tamaño ficticio a partir del APK debug.

Se mantiene un solo módulo hasta que métricas reales justifiquen modularizar. Las decisiones todavía no implementadas se documentan como previstas, no como capacidades disponibles.
