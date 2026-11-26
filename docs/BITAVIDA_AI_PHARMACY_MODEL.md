# Bitavida IA · ampliación farmacológica del modelo local

Estado técnico: candidato congelado el 18 de noviembre de 2026, antes de abrir la segunda auditoría farmacológica independiente. Las fichas educativas permanecen pendientes de revisión clínica humana.

## Un solo modelo, dos tareas complementarias

Se conserva el clasificador Multinomial Naive Bayes de Mediciones. El artefacto empaquetado incorpora sus 30 temas y 10 intenciones farmacológicas: identificar principio activo/familia, usos, mecanismo, efectos secundarios, contraindicaciones, precauciones, interacciones, formulaciones, preguntas para consulta y uso de Medicamentos en Bitavida. No hay un clasificador por medicamento ni un segundo motor.

El reconocimiento de entidades es una tarea distinta: DrugNameResolver busca nombres genéricos y alias documentados en el catálogo. Los alias completos de combinaciones y formulaciones tienen precedencia sobre los nombres que contienen. Cuando un nombre está confirmado, se sustituye por el token neutro «medicamento» únicamente en el texto enviado al clasificador de intención. El texto del usuario nunca se interpola en las fichas.

Las opciones por un error de escritura de una edición, por nombres compartidos entre formulaciones o por alias configurados como insuficientes requieren selección explícita. No abren automáticamente una ficha por semejanza nominal. Un nombre comercial desconocido no se convierte en un principio activo. La integración desde registros exige igualdad normalizada de un nombre genérico o alias verificado; no elimina cantidades ni presentaciones para forzar coincidencias.

Una intención con confianza suficiente y un principio activo confirmado recuperan directamente su sección documental. Una entidad confirmada con intención insuficiente permite abrir su ficha; no presenta contenido de otro medicamento. Las selecciones ambiguas conservan la intención aprendida cuando existe.

## Representación, entrenamiento y tamaño

- Corpus final: 922 preguntas ficticias distintas, normalizadas sin duplicados.
- Ampliación: 260 preguntas para las 10 intenciones, 40 paráfrasis adicionales de identidad/familia y finalidad/indicaciones, y 60 ejemplos variados de fondo ajeno al catálogo.
- Clase de fondo: 102 ejemplos. Su ampliación evita reducir excesivamente su peso al incorporar categorías nuevas.
- Clases: 40 educativas y una de fondo.
- Características: 6 222 unigramas de palabras, bigramas y tetragramas de caracteres.
- Artefacto: 580 978 bytes, aproximadamente 567,36 KiB sin comprimir.
- Presupuesto verificado por prueba: menos de 768 KiB. Mantiene un único modelo menor de 1 MiB y no añade dependencias, descargas ni ejecución generativa.
- Reproducción: `node tools/bitavida-ai/train.mjs --check` confirmó identidad exacta con el recurso empaquetado.

La normalización NFKD, eliminación de marcas y puntuación, minúsculas, abreviaturas funcionales y tratamiento de flexiones se comparten entre Kotlin y el entrenador. La reparación ortográfica del vocabulario aprendido no determina por sí sola la identidad de un medicamento.

La inferencia y la carga del modelo se ejecutan fuera del hilo de UI mediante el repositorio y el caso de uso existentes. El catálogo se materializa una vez de forma diferida. La consulta directa de fichas y la búsqueda de nombres registrados funcionan sin cargar el modelo.

## Calibración y abstención

El posterior del modelo no expresa certeza médica. Se requiere:

- Al menos dos unigramas distintos conocidos.
- Cobertura léxica de 0,80.
- Posterior mínimo de 0,75 para los 30 temas de Mediciones y 0,90 para las intenciones farmacológicas.
- Diferencia mínima de 0,20 entre las dos clases principales.

La cobertura ahora mide masa de caracteres de unigramas distintos, limitando cada palabra a 12 caracteres. Un término desconocido largo no debe quedar oculto detrás de varias palabras cortas conocidas. Esta regla general se aplica a todos los temas; no introduce listas de frases ajenas ni excepciones de consultas.

La política de seguridad está separada del modelo. Excluye cantidades personales, cambios de tratamiento, valoraciones de seguridad personal y señales agudas. Las raíces lingüísticas de conceptos agudos cubren plurales, gerundios y participios. Los números contenidos dentro de un alias completo de formulación documentada se neutralizan antes del control numérico; cantidades adicionales permanecen fuera del alcance educativo.

## Resultados reales de desarrollo

Estos conjuntos son sintéticos y no demuestran validación clínica ni desempeño con cuidadores reales.

| Conjunto | Correctas | Abstenciones conocidas | Aceptaciones erróneas conocidas | Desconocidas rechazadas |
|---|---:|---:|---:|---:|
| Calibración original, 60 conocidas y 10 desconocidas | 53/60 | 7 | 0 | 10/10 |
| Calibración farmacológica separada, 18 conocidas | 13/18 | 4 | 1 | No incluye desconocidas |
| Diagnóstico histórico, 90 conocidas y 20 desconocidas | 73/90 | 16 | 1 | 20/20 |
| Auditoría V3 histórica, 60 conocidas y 20 desconocidas | 53/60 | 7 | 0 | 20/20 |

La calibración farmacológica se ejecuta con `--dataset tools/bitavida-ai/pharmacy_calibration.tsv`. La aceptación errónea bruta restante corresponde a consulta sobre fármacos clasificada como consulta de Mediciones. Con un principio activo confirmado, el caso de uso no acepta ese tema como respuesta farmacológica. Sigue siendo una limitación del clasificador bruto para preguntas generales sin entidad.

Con posterior farmacológico 0,75, esa calibración tenía 14 correctas, 2 abstenciones y 2 aceptaciones erróneas. Elevarlo a 0,90 reduce una aceptación errónea farmacológica, a cambio de una respuesta correcta directa menos. El otro error pertenece al grupo de Mediciones, cuyo umbral se conserva para no degradar innecesariamente las preguntas aprobadas.

En la calibración original, la cobertura por conteo daba 59 correctas y una abstención; la masa de caracteres da 53 correctas y siete abstenciones, sin errores aceptados en ambas. Es una decisión conservadora explícita. En el diagnóstico histórico de 90 preguntas, el resultado final conserva los conteos 73 correctas, un error aceptado y 16 abstenciones de la versión V3 anterior.

La primera auditoría farmacológica de 46 preguntas se abrió antes de la corrección: 21 de 27 respuestas directas correctas, cinco abstenciones y una intención equivocada; 11 selecciones correctas; tres desconocidas rechazadas; cuatro de cinco solicitudes fuera de alcance rechazadas. Expuso una confusión identidad/finalidad y una flexión de señal aguda no cubierta. Tras observarla, este conjunto es regresión y diagnóstico; no debe anunciarse como una evaluación ciega nueva.

La segunda auditoría independiente se ejecuta por separado después de esta congelación; sus resultados y los de compilación, suite y lint deben registrarse en la documentación de integración al completarse.

## Límites persistentes y mantenimiento

- No se calculan dosis, cambios, sustituciones ni compatibilidad individual.
- Una abstención no significa que el contenido no exista; la biblioteca sigue disponible para una selección explícita.
- Un alias genérico puede no identificar una formulación. La persona debe comprobar el envase.
- Interacciones y advertencias educativas no forman una evaluación completa ni personalizada.
- El catálogo no se actualiza por Internet. Actualizar documentación exige revisar la formulación, las fuentes, su versión y su disponibilidad; una autorización extranjera no verifica disponibilidad en México.
- No se entrenó con datos médicos ni consultas de usuarios. La sesión no persiste preguntas ni resultados en disco o registros de diagnóstico.
- Los conjuntos abiertos se mantienen como diagnósticos. Cualquier ajuste posterior requiere una nueva evaluación independiente y debe conservar las métricas de los intentos anteriores.
