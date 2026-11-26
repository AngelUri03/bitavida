# Bitavida IA · auditoría independiente V3

## Preparación

El conjunto `app/src/test/resources/education/audit_v3.tsv` contiene 80 preguntas ficticias: 60 etiquetadas, dos por cada uno de los 30 temas existentes, y 20 ajenas o ambiguas con etiqueta `__UNKNOWN__`.

Se redactó por un colaborador distinto del autor del clasificador, antes de observar sus predicciones. El autor del modelo recibió la instrucción explícita de no leer este archivo hasta congelar corpus, representación y umbrales. No se seleccionaron ni cambiaron frases según sus resultados.

Se revisaron los identificadores del enum y los resúmenes de la biblioteca para que las etiquetas correspondieran al contenido disponible. Se comprobó, antes de escribir el conjunto, que ninguna consulta coincidiera literalmente con las versiones de entrenamiento y calibración leídas al preparar esta auditoría. La comparación ignoró mayúsculas, acentos, compatibilidad Unicode, puntuación y espacios. No se copiaron las seis frases de aceptación aportadas por el usuario.

El conjunto incluye formulaciones coloquiales mexicanas, abreviaturas generales, acentos omitidos, cambios de orden, preguntas breves y dos contrastes de curación: ayuno no equivale a antes de cualquier comida; los avisos del celular y los estados de una actividad se distinguen. Las entradas desconocidas son temas externos o solicitudes insuficientes, sin pedir interpretación clínica de pacientes.

## Procedimiento posterior a la congelación

1. Verificar nuevamente la ausencia de solapes con el corpus definitivo, sin usar las predicciones para elegir reemplazos.
2. Ejecutar el mismo clasificador Kotlin empaquetado que usa la aplicación. La versión del modelo y sus umbrales se registran antes de esta evaluación.
3. Contar por separado respuestas correctas, respuestas incorrectas aceptadas y rechazos de temas conocidos.
4. Para desconocidas, registrar rechazos y cualquier respuesta educativa aceptada.
5. Revisar si las sugerencias incluyen el tema esperado para una pregunta conocida rechazada; no contarlas como respuestas directas correctas.
6. Publicar todos los resultados y casos fallidos. Si se usan los errores para diseñar otra versión, este conjunto pasa a ser diagnóstico y se necesita una nueva auditoría oculta para medir generalización.

## Resultados

La primera ejecución Node se realizó tras congelar un candidato de 557 ejemplos, 3,955 características y 288,365 bytes (posterior 0.75, margen 0.20 y cobertura 0.60): 54/60 conocidas correctas, seis rechazos y ninguna conocida asignada a otro tema; se rechazaron 18/20 desconocidas y dos se aceptaron erróneamente. El agregador inicial mezclaba esos dos falsos positivos con los errores conocidos; esa contabilidad se corrigió, sin cambiar preguntas.

Después se corrigió una regresión ya detectada por una prueba de aceptación anterior, añadiendo cinco paráfrasis de significado de cifras. Se compararon coberturas y posteriores sobre calibración; se eligió cobertura 0.80, conservando posterior 0.75 y margen 0.20. El autor del modelo no recibió las frases de esta auditoría; sí se comunicó la necesidad de revisar globalmente el rechazo tras los resultados agregados. Por ello, la siguiente medición es una repetición descriptiva y este conjunto ya se considera diagnóstico inspeccionado, no una nueva prueba ciega.

El artefacto definitivo tiene 562 ejemplos distintos, 3,985 características y 290,602 bytes. La repetición Node dio:

| Subconjunto | Correctas | Erróneas aceptadas | Rechazos |
| --- | ---: | ---: | ---: |
| Conocidas (60) | 50 | 0 | 10 |
| Ajena/ambigua (20) | No aplica | 0 | 20 |

Cinco conocidas rechazadas ofrecieron sugerencias que incluían el tema esperado; no se cuentan como respuestas directas. Las demás se rechazaron por cobertura léxica. El archivo audit_v3.tsv no fue modificado tras observar predicciones.

Casos conocidos que todavía se rechazan:

- «para q llevar seguimiento de los kilos en vez de recordar el ultimo pesaje» (cobertura).
- «glucemia y glucosa, de qué están hablando esas palabras» (sugerencia de tema).
- «Una lectura extra de glucosa sustituye la que estaba agendada» (cobertura).
- «Cuando dicen tensión arterial, qué están midiendo» (sugerencia de tema).
- «presion de la sangre sobre las arterias, qué significa eso» (cobertura).
- «checarse la presion aunque uno se sienta bien, qué utilidad tiene» (cobertura).
- «la tension cambia entre una toma y otra, qué puede influir» (sugerencia de tema).
- «Ya tengo una lectura real, cómo la capturo dentro de Mediciones» (sugerencia de tema).
- «las graficas de Mediciones, cómo muestran los registros en el tiempo» (sugerencia de tema).
- «Qué datos del cuidado puedo preparar para enseñarlos durante la consulta» (cobertura).

La ejecución JVM confirmó 50/60 conocidas correctas, diez rechazos, cero errores aceptados y cero aceptaciones de las 20 desconocidas. La invocación conjunta assembleDebug, testDebugUnitTest y lintDebug terminó correctamente: 243 pruebas, cero fallos y cero omitidas; lint informó «No issues found». No se ejecutó evaluación visual ni en Samsung.

Esta auditoría sintética y acotada no equivale a evaluación con cuidadores reales, validación clínica ni certificación de funcionamiento frente a cualquier pregunta.
