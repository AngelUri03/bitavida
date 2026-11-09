# Sistema de diseño Bitavida

## Baseline oficial

**BITAVIDA UI BASELINE 0.5.4** es la referencia visual vigente. El código Compose actual es la fuente ejecutable. Figma V2, informes de iteraciones, AGSL, Liquid Glass, refracción, ondas radiales, lentes y canicas son antecedentes rechazados o históricos; no forman parte del sistema oficial.

## Identidad

“El hilo de la vida” expresa continuidad del cuidado sin convertir el recorrido en progreso clínico. El monograma B y su geometría normalizada son recursos de marca. Los nodos organizan secciones y no representan cumplimiento, diagnóstico ni severidad.

## Color

Familia principal petróleo/turquesa y superficies pastel. Tokens claros principales:

- primary `#086A7D`, background `#F5FAFA`, onBackground `#102B38`;
- surface `#FFFFFF`, surfaceVariant `#EDF5F5`;
- texto secundario `#45616D`;
- outline `#C9DCDC`;
- acentos: `#1596A5`, `#E4F5F7`, `#DFF4EA`, `#EDEAF8`, `#FFF0EC`.

El tema oscuro usa fondo tinta `#071820`, superficies `#0D252E` y texto claro `#E5F2F2`. Los estados siempre incluyen texto o semántica; el color no es la única señal.

## Tipografía, espacio y forma

Kodchasan es la familia oficial y se distribuye bajo OFL. Se usan estilos Material 3 y texto escalable. El espaciado, tamaños táctiles y formas provienen de tokens compartidos. Los contenedores de texto evitan alturas rígidas. Las superficies conservan esquinas y sombras redondeadas de la baseline.

## Componentes vigentes

- Encabezado editorial con monograma, saludo y curva integrada.
- Hilo vertical Bézier con nodos circulares.
- Superficies conectadas, acciones del hilo y estados textuales.
- Acción de confirmación con transformación visual.
- Cita expandible con sombra redondeada.
- Navegación inferior accesible.
- Fondo fluido claro/oscuro.
- Estados normal y vacío, además de previews claro, oscuro, 320 dp, texto 200 % y movimiento reducido.

## Motion oficial

### Curva superior

Ocupa aproximadamente 92 % del ancho disponible. Es fina, translúcida e integrada con los planos del fondo. Una iluminación suave viaja sobre el trazo; no hay AGSL, refracción ni deformación del contenido.

### Hilo y estela

La geometría estructural permanece limpia. Durante el scroll, una única cabeza luminosa con estela suave sigue el Path Bézier. Su posición global es continua entre secciones, invierte sin reiniciar, limita el retraso visual y se alinea en extremos. La intensidad responde a la velocidad y se desvanece al detenerse.

Los nodos reaccionan por proximidad real a esa misma estela: aumenta sutilmente su luminosidad y halo, sin temporizadores independientes ni significado clínico.

### Microinteracciones

Presión, liberación, selección, confirmación, revelado y expansión usan las duraciones definidas en `BitavidaFluidMotion`. No deben bloquear callbacks ni alterar el scroll nativo. La animación de confirmación y la expansión de citas forman parte de la baseline.

## Movimiento reducido y ciclo de vida

Con movimiento reducido, encabezado, hilo y nodos muestran su estado estático completo; las funciones siguen disponibles. Las animaciones ambientales se suspenden fuera del viewport o en segundo plano según los componentes vigentes. No se crearán corrutinas por evento de scroll ni efectos de pantalla completa.

## Reglas de cambio

No reinterpretar ni “mejorar” esta baseline sin autorización explícita. Toda propuesta visual debe proteger primero geometría, legibilidad, continuidad, accesibilidad y semántica sanitaria.
