# Bitavida IA · corrección de contraste

## Causa y corrección

La revisión no encontró Color.Black, #000000 ni colores oscuros fijos en los textos o estilos tipográficos de IA. La causa era la herencia del negro predeterminado de LocalContentColor: el fondo fluido y los contenedores transparentes no establecen un primer plano Material.

BitavidaAiContentColors proporciona MaterialTheme.colorScheme.onSurface sin introducir nodos de layout. Lo utilizan el host de páginas y las entradas reutilizables de Inicio, Respuesta, Detalle, ambigüedad y Cómo funciona. Así se corrigen también los textos de pregunta enviada, títulos, explicaciones, conceptos, información complementaria y referencias desplegadas.

AiPanel y las superficies base de IA declaran contentColor = onSurface. En particular, mint y paleBlue son tokens extendidos; contentColorFor no podía resolverlos por sí solo y heredaba el ambiente incorrecto. Los textos secundarios conservan onSurfaceVariant y los acentos primary. Botones y chips conservan sus propios pares onPrimary/onPrimaryContainer.

No se modifican Theme.kt, Type.kt, la familia Kodchasan, colores de la paleta, geometría, espacios, tamaños o animaciones. Tampoco se alteran otras funcionalidades, el clasificador ni las 30 guías educativas.

## Comprobación visual solicitada

Se capturó Respuesta antes de la corrección en claro y oscuro. Tras compilar, se revisaron 20 renders correctos en Android Studio:

| Preview | Configuraciones revisadas |
| --- | --- |
| AiDestinationPreview | Inicio, Respuesta, Biblioteca, Detalle y Cómo funciona; cada una en claro y oscuro |
| AiReadingContrastPreview | Respuesta expandida, Detalle expandido y Detalle colapsado; cada uno en claro y oscuro |
| AiSecondaryContrastPreview | Ambigüedad, sugerencias y tarjeta de estado vacío; claro y oscuro |
| AiBusyContrastPreview | Pregunta escrita, botones y análisis activo; claro y oscuro |

Los previews expandidos muestran información complementaria, conceptos, preguntas relacionadas y Fuente y revisión abierta. Sus datos son ficticios. Se inspeccionaron títulos, cuerpo y textos secundarios sobre fondo, superficies normales y tintadas. La semántica y las coordenadas del preview de Respuesta fueron idénticas antes y después en ambos temas: la corrección de primer plano no altera el layout.

Un primer intento de render tras la compilación externa encontró clases incrementales antiguas en layoutlib. Se actualizó la compilación mediante la herramienta Gradle del IDE y se repitieron los renders correctamente; los intentos fallidos no se cuentan entre los 20 anteriores.

## Contraste WCAG AA

BitavidaAiContrastTest evalúa 40 pares con los tokens reales, sin duplicar hexadecimales. Exige al menos 4.5:1, también en textos que por tamaño podrían admitir 3:1.

| Comprobación | Claro | Oscuro |
| --- | ---: | ---: |
| Mínimo de texto principal/secundario/acento sobre superficies usadas | 5.26:1, primary/lavender | 6.81:1, onSurfaceVariant/mint |
| Mínimo de pares de acción | 6.24:1, onPrimary/primary | 7.71:1, onPrimary/primary |

Se comprueban background, surface, surfaceVariant, paleBlue, mint y lavender, además de los pares de botones y chips seleccionados. Es una comprobación de contraste de los tokens; no se presenta como certificación integral de accesibilidad.

## Validación de entrega

La invocación conjunta :app:assembleDebug :app:testDebugUnitTest :app:lintDebug terminó correctamente. Se confirmaron 245 pruebas JVM aprobadas, cero fallos, cero omitidas y lint sin incidencias. git diff --check no reportó problemas.

La validación visual se limitó a los previews solicitados. No se realizaron pruebas instrumentadas ni pruebas manuales en Samsung.
