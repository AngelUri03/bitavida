# Bitavida IA · modelo local V2

## Qué existe

La inferencia sigue siendo un clasificador Multinomial Naive Bayes real, entrenado durante el desarrollo. Identifica uno de 30 temas educativos y enlaza una ficha exacta; no genera texto. Una clase adicional aprendida, __UNKNOWN__, es fondo estadístico de rechazo y nunca corresponde a una ficha educativa.

El APK incluye un artefacto de 132,443 bytes (aprox. 129 KiB), 1,735 características y 31 clases internas. No incorpora bibliotecas nuevas, descargas, servicios, red ni motores generativos. La biblioteca educativa se mantiene separada del modelo. Los contratos siguen siendo Kotlin puro; la carga e inferencia se ejecutan en Dispatchers.Default.

## Datos y reproducción

- tools/bitavida-ai/training.tsv: 1,263 preguntas ficticias en español, derivadas de 421 semillas y tres formas de entrada (original, «Oye», «Me explicas»). Las variantes prefijadas no son 1,263 ideas independientes. Hay sinónimos, expresiones coloquiales y formulaciones específicas de cada tema; ninguna pregunta proviene de usuarios reales.
- tools/bitavida-ai/calibration.tsv: 60 preguntas de temas conocidos y diez desconocidas, distintas del entrenamiento. Se usa para elegir rechazo y ponderación.
- app/src/test/resources/education/evaluation.tsv: 90 preguntas (paráfrasis, coloquial y error ortográfico por tema) y 20 desconocidas/ambiguas. La comprobación JVM de separación detectó una entrada vaga («mediciones») repetida en entrenamiento; se sustituyó exclusivamente en diagnóstico por «Necesito orientación general», elegida antes de observar su predicción. No se cambió el modelo por esa corrección de higiene. Comenzó como conjunto final; tras inspeccionar sus fallos se reclasificó honestamente como diagnóstico de desarrollo.
- app/src/test/resources/education/audit.tsv: 30 preguntas escritas por un colaborador distinto, una por tema, ocultas al autor del clasificador hasta congelar corpus y parámetros. No se ajustó el modelo según ellas.
- app/src/test/resources/education/acceptance.tsv: siete ejemplos proporcionados por el usuario. Son comprobaciones funcionales, no una medición independiente de generalización.

Comandos sin paquetes externos ni escrituras de archivos:

```powershell
node tools/bitavida-ai/train.mjs --check
node tools/bitavida-ai/train.mjs --calibrate
node tools/bitavida-ai/train.mjs --evaluate
node tools/bitavida-ai/train.mjs --audit
node tools/bitavida-ai/train.mjs --acceptance
```

El entrenador reconstruye el modelo en memoria. --check compara exactamente el recurso empaquetado y muestra sus dimensiones. Sin flags imprime el artefacto por stdout; cualquier actualización se aplica con la herramienta de escritura del IDE, conservando sus comprobaciones de concurrencia. No se entrena en el teléfono.

## Procesamiento español e inferencia

Se normalizan minúsculas con Locale.ROOT, acentos mediante NFD y palabras alfabéticas. Se conservan términos interrogativos que distinguen intenciones. Un algoritmo ligero elimina ciertos enclíticos y sufijos españoles de forma general; no existe una tabla de preguntas o condiciones por tema.

Las características aprendidas son unigramas y bigramas ordenados. El modelo usa suavizado aditivo alpha=0.1 y priors a partir del número de documentos. En inferencia se suman probabilidades logarítmicas; los unigramas tienen peso 1 y los bigramas 0.1, elegido con calibración para evitar que fórmulas interrogativas genéricas dominen el tema. La repetición de una característica se limita a dos.

Una palabra desconocida de cuatro o más caracteres puede repararse con una inserción, eliminación, sustitución o transposición adyacente respecto al vocabulario aprendido. Si hay varias candidatas solo se elige la más frecuente cuando supera por un factor de dos a la siguiente; en caso contrario no se corrige. No se utiliza un diccionario externo, búsqueda nominal de fichas ni reglas que traduzcan una palabra directamente a un tema.

Se requieren dos palabras diferentes conocidas, cobertura léxica mínima 0.60, posterior mínimo 0.80 y margen mínimo 0.20 respecto a la segunda clase. Si gana el fondo aprendido, se rechaza. El posterior no equivale a certeza humana ni clínica y no se presenta como porcentaje al usuario.

En ambigüedad se pueden proponer hasta tres temas con evidencia estadística, sin mostrarlos como respuestas confirmadas. La persona decide abrir una ficha. La guardia determinista de alcance permanece separada: cifras y solicitudes clínicas explícitas no se interpretan. Es conservadora y no detecta exhaustivamente toda solicitud médica.

## Corrección conceptual de anotación

Después de la congelación inicial se corrigieron exclusivamente dos semillas y sus seis variantes: «medirse antes de comer» y «medir azúcar antes de alimentos» estaban asignadas a ayuno, pero antes de una comida no necesariamente equivale a ayuno. Pasaron a GLUCOSE_CONTEXT. Fue una corrección conceptual, no un ajuste basado en fallos de la auditoría. No se cambiaron después los umbrales, características ni otras preguntas.

Las cifras posteriores a esa corrección son repeticiones descriptivas; no deben presentarse como una nueva auditoría ciega. La auditoría mantuvo sus resultados antes y después de la corrección.

## Resultados reales

Medidos con el entrenador Node.js 22.19.0, sin ejecutar Android ni Samsung:

| Conjunto | Respuestas correctas | Respuestas incorrectas aceptadas | Rechazos |
| --- | ---: | ---: | ---: |
| Calibración de temas (60) | 57 | 0 | 3 |
| Desconocidas de calibración (10) | No aplica | 0 | 10 |
| Diagnóstico de temas (90) | 70 | 5 | 15 |
| Desconocidas/ambiguas diagnósticas (20) | No aplica | 0 | 20 |
| Auditoría adicional de temas (30) | 23 | 2 | 5 |

La cobertura correcta de la auditoría es 76.7%; entre las 25 respuestas aceptadas, 23 eran del tema esperado (92%). La calibración no debe reemplazar esta cifra al comunicar rendimiento. Los dos errores de auditoría fueron cambiar de balanza → variaciones de peso, y variación entre días de la balanza → variaciones de glucosa. No se corrigieron usando ese conjunto.

Los ejemplos del usuario obtuvieron seis respuestas directas correctas y una aclaración con el tema esperado entre las sugerencias: «¿Por qué importa medirse antes de comer?» no especifica una medición y ya no se fuerza a ayuno.

Las pruebas JVM reconstruyen los conteos, comprueban separación de conjuntos, rechazo de desconocidas, acentos, mayúsculas, repetición, entrada larga, artefactos inválidos, clasificación y selección exacta de contenido. La validación de entrega conjunta assembleDebug, testDebugUnitTest y lintDebug terminó correctamente el 18 de noviembre de 2026: 233 pruebas JVM sin fallos y lint sin incidencias. Los resultados JVM confirmaron diagnóstico 70/90, cinco errores aceptados, 15 rechazos y 29/30 formulaciones con error ortográfico; la auditoría confirmó 23/30, dos errores y cinco rechazos. Los comandos Node por sí solos no prueban equivalencia de ejecución Android.

## Límites y rendimiento

El modelo reconoce patrones de un vocabulario pequeño; no comprende lenguaje abierto, conversaciones ni negaciones de forma fiable. Algunas preguntas coloquiales y errores múltiples seguirán sin reconocerse. La reparación ortográfica puede elegir una palabra equivocada; distintos temas pueden compartir términos y producir errores aceptados. Las sugerencias no siempre contienen el tema correcto.

La inferencia está limitada a 500 caracteres y 80 tokens, con búsqueda de reparación solo en el vocabulario empaquetado. La matriz numérica precalculada tiene 53,785 doubles, aproximadamente 420 KiB, además de mapas y cadenas JVM. No se ha medido latencia, batería ni memoria en teléfonos modestos. El tamaño acotado y la ausencia de un runtime pesado no equivalen a una certificación de rendimiento.

Ningún resultado interpreta datos personales ni modifica registros. Las respuestas son contenido local fijo, con revisión clínica pendiente. Ampliar contenido o mejorar un modelo no elimina esa revisión humana.
