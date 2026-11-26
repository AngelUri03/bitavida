# Bitavida IA · comprensión española V3

## Problema reproducido

La versión anterior ya eliminaba acentos y signos al tokenizar. El rechazo de «¿Por qué medir la presión?» y «Por que medir la presion» no se debía a los signos: ambas obtenían la misma intención superior con posterior 0.736, por debajo del umbral 0.80. Las formas «porque» y «porq» se consideraban palabras distintas/no conocidas, reduciendo la cobertura y el posterior a aproximadamente 0.385. El predominio de ciertas construcciones genéricas y la falta de representación subléxica agravaban las dudas.

No se introdujo una tabla de frases que devuelva temas ni condiciones especiales por intención. La recuperación continúa usando estadísticas Multinomial Naive Bayes aprendidas, con una clase adicional de rechazo.

## Cambios generales

- La normalización usa minúsculas con Locale.ROOT y NFKD: unifica acentos compuestos/descompuestos, ligaduras y letras de ancho completo. Elimina marcas combinantes y caracteres de formato, ignora puntuación irrelevante y normaliza espacios.
- La expansión de palabras funcionales aplica a todos los temas: porque/porq/pq/xq/pk/xk/porke/porqe/porqu → por que; q/k/ke → que; pa → para; x → por. No contiene términos médicos ni asignaciones a fichas.
- Se conservan la reducción general de sufijos/enclíticos y la reparación de una edición/transposición contra el vocabulario aprendido. Ante empate sin dominio claro de frecuencia no se repara.
- Se añaden n-gramas de cuatro caracteres delimitados por palabra a los unigramas y bigramas. Las características de palabras pesan 1; los bigramas y caracteres 0.1. Los caracteres aportan evidencia estadística compartida, sin sustituir la clasificación por búsqueda aproximada.
- Se eliminan los duplicados de entrenamiento que solo anteponían «Oye» o «Me explicas». Esos prefacios se tratan como cortesía, no como información de un tema.
- El corpus final tiene 562 formulaciones distintas: conserva 420 de las 421 semillas previas y añade 142 paráfrasis originales, no solo prefijos. La pregunta canónica exacta de presión se retiró del corpus; la equivalencia no necesita localizarla.
- Hay 30 temas educativos y una clase aprendida __UNKNOWN__. Las 30 guías y sus textos no se modificaron.

El recurso con esquema 3 ocupa **290,602 bytes** (aproximadamente 284 KiB), contiene 3,985 características y se incluye íntegro en el APK. No hay dependencias nuevas, motores nativos ni descargas. La matriz de probabilidades tiene 123,535 doubles, aproximadamente 965 KiB más cadenas, mapas y objetos JVM. La carga sigue siendo diferida y la inferencia ocurre fuera del hilo de UI. No se ha medido el consumo real en un teléfono.

## Selección y rechazo

Se compararon posteriores de 0.65 a 0.85 y coberturas léxicas de 0.60 a 0.85 usando la calibración existente. Se fijaron posterior mínimo **0.75**, diferencia con el segundo tema **0.20** y cobertura mínima **0.80**, manteniendo al menos dos palabras conocidas diferentes.

En el conjunto de calibración, una cobertura 0.80 conservaba las 59 respuestas correctas obtenidas con 0.60 y mantenía el rechazo de las diez desconocidas. Exigir 0.85 perdía seis preguntas conocidas más. Posteriores 0.80 o superiores volvían a rechazar innecesariamente la pregunta breve de presión; 0.75 conservó los rechazos y la ausencia de asignaciones erróneas en calibración. No se eligieron parámetros mirando las preguntas externas ocultas.

Al faltar certeza se devuelven como máximo dos temas con evidencia. No se inventa texto. Si gana el fondo de rechazo o falta cobertura no se fuerza una explicación. Los posteriores no se muestran como certeza humana ni clínica.

## Evaluación y separación

Los conjuntos de entrenamiento, calibración y diagnóstico conservan preguntas distintas. El diagnóstico V2 y la auditoría V2 ya eran conocidos durante este trabajo: sus cifras son de desarrollo, no una nueva prueba independiente. Las pruebas de invariancia comprueban características equivalentes; compartir una intención después de normalizar es el comportamiento buscado, no una búsqueda literal.

La raíz preparó por separado audit_v3.tsv, con 60 preguntas conocidas y 20 ajenas a la biblioteca. El autor del modelo no leyó ese archivo. Hubo una congelación inicial y después una corrección de una regresión descubierta por la aceptación anterior («Me explicas las dos cifras de tensión»), con cinco formulaciones generales sobre significado de cifras. La raíz repitió la evaluación externa sin compartir frases ni resultados hasta la congelación definitiva. Esa repetición se informa como descriptiva, no como otra auditoría ciega nueva.

Resultados Node del modelo definitivo:

| Conjunto | Conocidas | Correctas | Erróneas aceptadas | Rechazadas | Desconocidas rechazadas |
| --- | ---: | ---: | ---: | ---: | ---: |
| Calibración existente | 60 | 59 | 0 | 1 | 10/10 |
| Diagnóstico V2 conocido | 90 | 73 | 1 | 16 | 20/20 |
| Repetición externa V3, informada por raíz | 60 | 50 | 0 | 10 | 20/20 |

Las seis formas de presión solicitadas por el usuario recuperan directamente PRESSURE_PURPOSE, incluyendo porque/porq, falta de acentos y puntuación. Se comprueban también por el caso de uso y la ficha exacta, no solo por el tema del clasificador.

La calibración conserva una duda sobre variaciones de peso («El peso fluctúa por agua y circunstancias»). El diagnóstico conocido todavía confunde «Para qué sirve el ayuno al revisar glucemia» con el propósito general de medir glucosa. También rechaza formulaciones como «Si cierro el aviso del cel se registra algo», «Dónde busco una lectura viejita por fecha» y «Glucemia qué es eso». Las frases muy cortas, términos no vistos, negaciones y combinaciones de temas siguen siendo límites reales; no se promete comprensión abierta.

## Reproducción y pruebas

```powershell
node tools/bitavida-ai/train.mjs --check
node tools/bitavida-ai/train.mjs --calibrate
node tools/bitavida-ai/train.mjs --evaluate
node tools/bitavida-ai/train.mjs --dataset app/src/test/resources/education/audit_v3.tsv
```

El entrenador no escribe archivos; la actualización del recurso se hace mediante las herramientas del IDE. --check reconstruye exactamente los conteos de palabras y caracteres. El agregador separa errores aceptados entre conocidas de unknownAccepted para que los totales no mezclen grupos.

SpanishComprehensionV3Test añade aceptación directa de las seis variantes, igualdad de características ante puntuación/Unicode/abreviaturas, errores leves, orden diferente, lenguaje coloquial, fragmentos, ambigüedad y desconocidos. Las paráfrasis declaradas nuevas se comprueban contra el texto normalizado del entrenamiento. Las pruebas existentes siguen verificando selección de contenido, alcance clínico, reproducción e integridad. El presupuesto del recurso pasa de 256 a 384 KiB para cubrir la nueva representación de caracteres; no se relajaron los criterios de selección/rechazo.

La validación conjunta assembleDebug, testDebugUnitTest y lintDebug terminó correctamente: 243 pruebas JVM sin fallos ni omitidas y lint sin incidencias. La evaluación JVM confirmó los seis ejemplos y las variantes Unicode, además de 50/60 correctas, diez rechazos y cero errores aceptados en la repetición V3; rechazó las 20 desconocidas. No hubo instrumentadas, renders automáticos ni pruebas manuales en Samsung.
