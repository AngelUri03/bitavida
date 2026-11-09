# Accesibilidad

## Objetivo

Bitavida debe ser operable y comprensible con TalkBack, teclado, texto al 200 %, pantallas compactas y movimiento reducido. La accesibilidad no depende del color ni de una animación.

## Requisitos vigentes

- Objetivos táctiles de al menos 48 dp.
- Texto escalable al 200 % sin recorte ni superposición.
- Soporte mínimo de 320 dp y desplazamiento cuando el contenido crece.
- `adjustResize` para convivencia con teclado.
- Orden de foco coherente y controles con roles, estados y nombres accesibles.
- Imágenes decorativas sin descripción redundante.
- Estados expresados con texto/semántica, no solo color.
- Contraste mediante tokens compartidos; en tema claro el texto secundario usa `#45616D`.
- No usar alturas fijas para contenedores con texto.

## Baseline 0.5.4

La demo incluye descripciones para navegación y estados, regiones vivas para confirmación, áreas táctiles mínimas, layouts adaptativos para fuente grande y previews de tema claro, oscuro, compacto, texto 200 %, estado vacío y movimiento reducido. La selección de navegación permanece semánticamente en “Mi día” para destinos no implementados.

El contenido clínico ficticio sigue siendo legible por encima de la decoración. El hilo, fondo y nodos son ornamentales y no comunican cumplimiento.

## Movimiento

Cuando el sistema solicita reducir movimiento o se fuerza el modo de prueba:

- no corre la estela ni la animación ambiental;
- el hilo, nodos y curva permanecen visibles;
- no se pierde contenido, estado ni acción;
- la interacción táctil conserva sus límites y callback.

Las transiciones nunca deben retrasar una acción ni capturar el foco.

## Pruebas

Se conservan pruebas Compose de tamaño táctil, acción deshabilitada, restauración, visibilidad de nodos, navegación demostrativa y recorrido con tema, texto grande y movimiento reducido. Las unitarias protegen geometría y continuidad matemática.

Pendientes antes de una versión funcional:

- recorrido manual completo con TalkBack y teclado;
- Accessibility Scanner y controles de contraste sobre dispositivo;
- validación con tamaños de fuente y display extremos;
- pruebas de servicios reales y mensajes de error;
- revisión con personas cuidadoras y usuarios con discapacidad.

Las pruebas pendientes no deben describirse como certificación ni como ya ejecutadas.
