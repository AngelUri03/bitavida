# Arquitectura de Bitavida

## Estado actual

El proyecto contiene una sola aplicación Android y el módulo `:app`. La implementación vigente es una demo Compose offline sin capa de datos, persistencia, red, inyección de dependencias ni navegación por rutas.

Estructura real:

- `com.bitavida.app.MainActivity`: punto de entrada.
- `ui.demo`: pantalla demostrativa y estado visual.
- `ui.components`: componentes, hilo, microinteracciones y superficies.
- `ui.brand`: monograma, geometría y política de movimiento.
- `ui.theme`: color, tipografía, formas y espaciado.
- `domain.model`: tipos de dominio Kotlin puros ya introducidos.

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

## Persistencia prevista

Room se contempla para datos estructurados cuando exista una estrategia de cifrado aprobada. DataStore solo almacenará preferencias no sensibles. Adjuntos, claves y respaldos requerirán almacenamiento y criptografía definidos en [SECURITY.md](SECURITY.md). Android Auto Backup permanece excluido.

## Navegación

La barra inferior actual pertenece a la demo. La navegación real, rutas, back stack, deep links y autorización siguen pendientes. No se añadirá una biblioteca de navegación hasta definir esos flujos.

## Offline y concurrencia

Las funciones esenciales operarán localmente. No se añadirá `INTERNET` sin cambio de alcance y threat model. Si en el futuro existe concurrencia, las revisiones y conflictos se resolverán explícitamente; nunca se fusionarán datos clínicos automáticamente.

## Pruebas

- Unitarias: geometría de marca y continuidad/sincronización del hilo.
- Android: interacción, restauración de estado y recorrido accesible de la demo.
- Pendientes: casos de uso, repositorios, migraciones, seguridad, accesibilidad con dispositivo y pruebas end-to-end funcionales.

## Dependencias y evolución

Toda dependencia nueva exige necesidad, alternativa, compatibilidad, licencia y costo de tamaño. Se mantiene un solo módulo hasta que métricas reales justifiquen modularizar. Las decisiones todavía no implementadas se documentan como previstas, no como capacidades disponibles.
