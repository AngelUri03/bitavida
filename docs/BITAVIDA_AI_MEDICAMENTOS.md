# Bitavida IA · Medicamentos

## Alcance implementado

Medicamentos reutiliza el mismo asistente offline, la misma clase MultinomialNaiveBayes y un único artefacto de estadísticas incluido en el APK. No se añade un motor paralelo ni dependencias. Las 30 guías de Mediciones permanecen; Medicamentos incorpora diez guías conceptuales y 64 fichas farmacológicas documentales.

La botonera agrupada es MeasurementGuideActions, compartida con Mediciones: Agregar medicamento conserva OpenMedicationRegistration y Guías y ayuda abre la ruta educativa contextualizada. La biblioteca puede consultarse sin registrar tratamientos. Inicio, Respuesta, Biblioteca, Detalle y Cómo funciona comparten la pila educativa existente. Las familias usan chips oficiales horizontales, las tarjetas resumen el principio activo y los apartados de cada ficha se despliegan a demanda. El apartado preguntado se presenta primero; efectos frecuentes y señales importantes permanecen diferenciados.

Se conserva Kodchasan, colores semánticos claros/oscuros, objetivos mínimos de 48 dp, texto sin alturas fijas, movimiento reducido y convivencia con el teclado. Búsqueda, familia, scroll y expansión se conservan por sesión/página en el ViewModel; no se serializan consultas en SavedState. Los previews nuevos usan datos ficticios y cubren cinco estados en claro, oscuro, 320 dp y texto 200 %.

## Cobertura documental

| Bloque | Fichas | Alcance |
| --- | ---: | --- |
| Antidiabéticos principales | 34 | Biguanidas, sulfonilureas, SGLT2, DPP-4, GLP-1, duales, tiazolidinedionas, meglitinidas, alfa-glucosidasa y otros |
| Insulinas | 11 | Ocho principios/presentaciones individuales y tres premezclas |
| Combinaciones insulina/GLP-1 | 2 | Glargina/lixisenatida y degludec/liraglutida |
| Combinaciones orales | 11 | Los once pares solicitados con documentos específicos |
| Cardiovascular y renal | 4 | Lisinopril, losartán, atorvastatina y finerenona |
| Dolor neuropático | 2 | Duloxetina y pregabalina |

Las matrices de [fuentes principales](BITAVIDA_PHARMACOLOGY_CORE_SOURCES.md) y [fuentes extendidas](BITAVIDA_PHARMACOLOGY_EXTENDED_SOURCES.md) identifican cada documento, enlace y versión. Los textos son resúmenes originales breves; no reproducen fichas completas. La fecha de consulta es 18 de noviembre de 2026. Las versiones antiguas permanecen identificadas y no se confunden con una revisión reciente del documento.

Cada DrugEducation conserva identidad, familia, alias genéricos verificados, resumen, usos, mecanismo, efectos frecuentes y señales importantes, contraindicaciones, precauciones en mayores, interacciones no exhaustivas, formulaciones/vía, preguntas para consulta, fuentes, estado documental y disponibilidad mexicana no verificada. Las advertencias proceden de cada producto/formulación; los constructores comunes sólo organizan campos. Por ejemplo, exenatida inmediata y prolongada no heredan automáticamente las mismas contraindicaciones.

No se presume disponibilidad ni autorización en México. Rosiglitazona se identifica como documentación histórica; detemir distingue referencia comercial histórica estadounidense de retirada por seguridad. Pramlintida conserva la fecha de su documento y vigencia comercial no verificada. Todas las fichas siguen pendientes de revisión clínica humana: citar una institución no equivale a que esa institución haya revisado Bitavida.

## Identidad e intención

La clasificación farmacológica aprende nueve intenciones (identidad, usos, mecanismo, efectos, contraindicaciones, precauciones, interacciones, formulaciones y preguntas para consulta) y una décima sobre uso de Medicamentos en Bitavida, junto a los 30 temas anteriores.

DrugNameResolver utiliza límites de palabras y alias curados. Las combinaciones y formulaciones completas tienen prioridad sobre los componentes contenidos en su nombre. Para clasificar la intención, el principio activo reconocido se sustituye por un token neutro; el modelo no necesita una clase por fármaco. Acentos, mayúsculas, puntuación y Unicode siguen la normalización común en entrenamiento e inferencia.

Las semejanzas de un carácter o transposición sólo ofrecen candidatos para elección. No enlazan fichas por semejanza nominal. Semaglutida y exenatida sin formulación presentan alternativas; los alias genéricos de bromocriptina, glipizida y gliclazida piden confirmar el alcance de la formulación documentada aunque exista una sola ficha correspondiente.

En un medicamento registrado sólo se compara la etiqueta completa con un nombre genérico o alias exacto normalizado. No se eliminan dosis, no se adivinan marcas, no se inspeccionan otras medicinas ni datos del paciente. La acción Conocer este medicamento no cambia el tratamiento. Si la identidad exacta es ambigua se solicita selección.

Las solicitudes de dosis, ajuste, sustitución, valoración de seguridad individual, síntomas actuales o interpretación personal se derivan fuera del alcance educativo mediante una política conservadora. No es un detector clínico exhaustivo. El resultado es una ficha fija u opciones; no se genera contenido ni se evalúan interacciones de un tratamiento particular.

## Funcionamiento offline y mantenimiento

La app no solicita INTERNET. El modelo, las fichas y los datos bibliográficos están incluidos localmente y la inferencia corre en Dispatchers.Default. Abrir una ficha, buscar o resolver un nombre registrado no carga el modelo. Sólo la acción explícita Consultar documento oficial abre un navegador externo, que puede necesitar conexión; no transmite la consulta escrita al asistente.

No hay descargas, servicios, telemetría ni uso de preguntas para entrenamiento. No cambian minSdk 26, dependencias, persistencia cifrada ni flujos clínicos. El catálogo acotado se compone mediante LazyColumn; no se muestran decenas de fichas desarrolladas simultáneamente.

Para actualizar una ficha: comprobar la fuente primaria y formulación; contrastar contraindicaciones, precauciones y versiones; redactar el resumen original; actualizar referencia/consulta y trazabilidad; conservar revisión pendiente hasta revisión humana explícita; ejecutar integridad, pruebas del modelo, navegación, assemble y lint. No actualizar datos clínicos personales desde el catálogo.

## Evaluación y límites

La primera auditoría farmacológica, escrita por un colaborador distinto y oculta durante el entrenamiento inicial, incluyó 46 consultas sintéticas: 27 de intención conocida, 11 de selección, tres desconocidas y cinco fuera de alcance. La primera ejecución obtuvo 21/27 respuestas directas con intención correcta, cinco abstenciones y un error de intención (identidad frente a finalidad, con principio activo correcto); 11/11 selecciones, 3/3 desconocidas y 4/5 rechazos personales. Una flexión de un síntoma actual no activó la política de alcance. La ampliación también reveló una falsa aceptación de una pregunta meteorológica en el benchmark V3 anterior.

Estos hallazgos motivaron correcciones generales de lenguaje y rechazo, sin condiciones para frases concretas. Desde que se observaron, repetir los 46 casos es una prueba de regresión de desarrollo. Se reservó por separado un segundo conjunto de 30 casos, oculto al autor del modelo hasta congelar parámetros, y 18 preguntas farmacológicas de calibración diagnóstica. El modelo final contiene 922 preguntas, 40 clases educativas y una clase de fondo, 6 222 características y 580 978 bytes (567,36 KiB) sin comprimir. La cobertura usa masa de caracteres por palabra (máximo 12), mínimo 0,80; el posterior mínimo es 0,75 en Mediciones y 0,90 en Medicamentos, con margen 0,20. El entrenador reproduce exactamente el recurso. La [metodología y calibración](BITAVIDA_AI_PHARMACY_MODEL.md) detalla las decisiones y el intercambio entre cobertura y abstención. No se presentan datos sintéticos como rendimiento clínico ni como prueba con cuidadores reales.

Resultados del modelo congelado:

| Conjunto | Intención directa correcta | Intención incorrecta aceptada | Ficha correcta ofrecida al abstenerse | Desconocidas rechazadas |
| --- | ---: | ---: | ---: | ---: |
| Primer conjunto, regresión de desarrollo | 21/27 | 0 | 6/6 | 3/3 |
| Segundo conjunto, confirmación independiente de clasificación | 11/18 | 0 | 7/7 | 4/4 |
| Mediciones V3, diagnóstico histórico | 53/60 | 0 | No aplica | 20/20 |

Las opciones que requerían confirmar nombre o formulación fueron correctas en 11/11 casos del primer conjunto y 4/4 del segundo. Todas las preguntas conocidas recuperaron su principio activo mediante respuesta o selección directa: 27/27 y 18/18, sin mandar a buscarlo manualmente. Eso no convierte las selecciones en aciertos de intención: la cobertura directa del segundo conjunto es 61,1 %. Las pruebas exigen recuperación correcta y ausencia de respuesta errónea; la cobertura de intención directa y las abstenciones se registran por separado.

En la primera ejecución del segundo conjunto se rechazó explícitamente sólo 1/4 solicitudes personales: faltaban expresiones de adecuación individual, reemplazo y manipulación para compensar cantidades. Se amplió la política por familias morfológicas generales, con variantes distintas en pruebas; no se alteraron el modelo, el entrenamiento ni los umbrales después de abrir ese conjunto. La repetición final de seguridad obtuvo 4/4 y 5/5 en ambos conjuntos. Estos resultados de seguridad posteriores son regresión, no una nueva evaluación ciega. La política sigue siendo conservadora y no exhaustiva.

Las siete preguntas de confirmación cuyo principio activo se reconoció pero cuya intención aún requiere selección son:

- «¿Qué hace la sitagliptina con las señales intestinales que producen insulina?»
- «¿Cuáles molestias podrían aparecer como reacción a la pioglitazona?»
- «Sobre acarbosa, quisiera leer qué reacciones indeseables están descritas.»
- «¿Qué antecedentes hacen que la ficha prohíba emplear repaglinida?»
- «¿Cuáles son las situaciones de uso prohibido para dulaglutida?»
- «¿Con qué fármacos puede interferir la acarbosa?»
- «Quiero distinguir la liberación convencional y la prolongada de metformina.»

La ficha exacta se ofrece en todos esos casos y permite desplegar el apartado deseado. No se anuncia comprensión universal ni ausencia de errores futuros.

Las pruebas verifican identidad/sinónimos, errores con confirmación, intención y ficha correctas, ambigüedad, rechazo de desconocidos, referencias específicas, nueve apartados por ficha, relaciones válidas, ausencia de INTERNET y acceso a biblioteca sin cargar el modelo. Las pruebas de navegación conservan borrador, búsqueda, familia, desplazamiento y expansiones; los tests de contraste compartidos protegen los tokens de ambos temas.

Validación final ejecutada en una sola invocación:

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

Resultado real: BUILD SUCCESSFUL, 268 pruebas JVM, cero fallos y cero omitidas; lint: «No issues found». El control `node tools/bitavida-ai/train.mjs --check` confirmó 922 documentos, 40 temas, 6 222 características y 580 978 bytes reproducibles. Los tests de contraste de tokens claro/oscuro y de navegación farmacológica pasaron. Esto no constituye una verificación visual o clínica sobre dispositivo.

No se realizan instrumentadas ni pruebas manuales en Samsung. Los previews se incluyen sin renderizado automático. El comportamiento real de TalkBack, teclado, rendimiento y lectura sobre dispositivos modestos permanece pendiente de validación en dispositivo.
