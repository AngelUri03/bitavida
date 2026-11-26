# Bitavida IA · experiencia educativa offline

## Ampliación vigente: Medicamentos

Guías y ayuda de Medicamentos abre el mismo asistente, clasificador y navegación. Su biblioteca incluye 64 fichas farmacológicas por principio activo o formulación, más diez guías generales nuevas; Mediciones conserva sus 30 guías. La botonera agrupada se comparte con Mediciones y Agregar medicamento mantiene su flujo real.

Las fichas separan usos, mecanismo, efectos frecuentes y señales importantes, contraindicaciones, precauciones, interacciones no exhaustivas, formulaciones y preguntas para consulta. Contienen referencias oficiales con versión y fecha de consulta, revisión clínica pendiente y disponibilidad mexicana no verificada. Las formas ambiguas exigen una selección. No hay identificación de marcas por suposición, evaluación personal de interacciones, dosis ni cambios de tratamiento. El acceso desde un medicamento registrado requiere identidad genérica exacta y una acción de la persona.

Consúltese [implementación, evaluación y límites farmacológicos](BITAVIDA_AI_MEDICAMENTOS.md), [fuentes de las 34 fichas principales](BITAVIDA_PHARMACOLOGY_CORE_SOURCES.md) y [fuentes de las 30 fichas de insulinas, combinaciones y otros tratamientos](BITAVIDA_PHARMACOLOGY_EXTENDED_SOURCES.md).

## Base de Mediciones conservada · referencia V3

Las secciones y métricas siguientes describen la entrega V3 anterior. Las cifras del modelo ampliado y la validación vigente se registran en el documento de Medicamentos enlazado arriba.

La ampliación autorizada está registrada en PRODUCT.md. Se mantienen el reporte funcional y BITAVIDA UI BASELINE 0.5.4 como fuentes de verdad. No se incorporaron dependencias ni servicios externos.

## Recorrido y conservación de estado

Mediciones → Guías y ayuda abre Inicio. El encabezado compacto conserva iconografía, Kodchasan, paleta y superficies oficiales. El campo de pregunta y dos sugerencias según el origen son protagonistas; los accesos a Biblioteca y Cómo funciona llevan a vistas independientes. Se retira el bloque Explora por tema para compactar Inicio. Inicio no despliega fichas ni una biblioteca completa.

Una consulta abre Respuesta inteligente con la pregunta enviada, tema identificado, resumen, importancia, ejemplo y contexto expandible. La Biblioteca tiene buscador local, filtros oficiales en una sola fila horizontal y tarjetas agrupadas por Glucosa, Presión, Peso y Uso de Bitavida. La selección de una ficha o pregunta relacionada abre Detalle educativo con información desarrollada y conceptos. Cómo funciona es una vista secundaria propia, sin saturar Inicio.

La pila de entradas identificadas vive en BitavidaAiViewModel. Cada vista tiene su LazyListState y conserva índice y desplazamiento en memoria. Borrador, búsqueda, filtros y preferencias de apartados abiertos sobreviven a las transiciones y la rotación mediante el ViewModel retenido. Atrás vuelve exactamente a la entrada anterior. Hacer otra pregunta vuelve al compositor de Inicio; salir de ayuda mediante Atrás descarta la sesión. No se serializan preguntas ni búsquedas en SavedState, preferencias, Room o archivos.

Las preguntas relacionadas abren la ficha exacta mediante acción explícita: no simulan otra inferencia. La clasificación solo se ejecuta al enviar una pregunta o elegir una sugerencia. Una nueva consulta invalida cualquier resultado anterior pendiente. La carga del modelo y la inferencia corren en Dispatchers.Default.

## Modelo y contenido

Hay 30 fichas originales: ocho sobre Glucosa, siete sobre Presión, seis sobre Peso y nueve sobre Uso de Bitavida. Cada una separa explicación breve, importancia, ejemplo, información complementaria, conceptos, relaciones y referencia. La presentación se adapta al contenido; las URLs se reservan al apartado secundario de fuentes.

Véase [contenido y referencias V2](BITAVIDA_AI_CONTENT_V2.md) para el catálogo y sus fuentes institucionales. Todas las fichas muestran revisión clínica pendiente: una referencia no constituye aprobación por la institución citada. No se diagnostica, interpreta una cifra personal ni prescribe tratamiento. El contenido fijo no se rellena con datos de la pregunta.

El modelo Multinomial Naive Bayes conserva inferencia CPU ligera, estadísticas aprendidas incluidas en el APK y vocabulario español ampliado. Puede proponer temas cercanos si no hay suficiente certeza. No se usa un modelo generativo ni se fabrican respuestas. Véase [modelo, entrenamiento y evaluación V3](BITAVIDA_AI_MODEL_V3.md) para los parámetros definitivos, corpus separados, tamaño, métricas y límites.

## Privacidad, accesibilidad y rendimiento

El asistente no recibe perfiles ni repositorios clínicos. Las preguntas y búsquedas son efímeras; no se registran, transmiten ni usan para entrenar. El modelo, la biblioteca y las referencias bibliográficas están incluidos en la app. No hay descargas ni permiso INTERNET. El minSdk y las dependencias permanecen sin cambios.

La biblioteca se compone de forma diferida con LazyColumn; la búsqueda opera sobre un catálogo local acotado. Las animaciones de transición y presión usan BitavidaFluidMotion y respetan movimiento reducido. No hay escritura simulada ni esperas artificiales antes de presentar resultados. El indicador circular solo se compone mientras analyzing es verdadero; no queda una barra o línea permanente tras clasificar. Con movimiento reducido se presenta un estado estático.

Se mantienen acciones de al menos 48 dp, texto escalable sin alturas fijas, filtros de altura uniforme con scroll horizontal y selección visible, tarjetas adaptables, encabezados semánticos, anuncios accesibles y convivencia con IME. Se incluyen previews de cada vista en claro, oscuro, 320 dp y texto 200 %, más ambigüedad. No se renderizan automáticamente.

## Validación

Las pruebas JVM cubren paráfrasis, lenguaje coloquial, variantes y errores ortográficos, ambigüedad, desconocidos, selección de contenido, guardia de alcance e integridad del modelo. La calibración está separada del entrenamiento. Las evaluaciones V2 conocidas son diagnósticos de desarrollo en esta iteración. Se preparó una auditoría V3 de 80 preguntas (60 conocidas, dos por cada tema, y 20 ajenas o ambiguas) por un colaborador distinto, oculta al autor del modelo hasta congelar parámetros y corpus. Los resultados reales y el carácter sintético de los datos se documentan en el informe del modelo; no equivalen a una evaluación clínica o con cuidadores reales.

Las pruebas de navegación verifican consulta → respuesta → detalle → regreso, restauración del borrador, búsqueda, categoría, índice/desplazamiento y apartados abiertos, invalidación de consultas tardías y consulta directa sin inferencia. Las fichas se comprueban por identidad y relaciones exactas.

Validación de entrega:

```powershell
node tools/bitavida-ai/train.mjs --check
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

Resultado V3: assembleDebug, testDebugUnitTest y lintDebug terminaron correctamente en una sola invocación; pasaron las 243 pruebas JVM, sin fallos ni omitidas, y lint informó «No issues found». Las seis variantes solicitadas de presión y las variantes de Unicode/puntuación/espacios recuperan directamente la ficha correcta mediante el caso de uso real. La repetición separada en JVM obtuvo 50/60 conocidas correctas, diez rechazos y cero errores aceptados; se rechazaron las 20 desconocidas. --check reprodujo exactamente los 290,602 bytes del modelo. Los resultados y los rechazos pendientes se documentan en el informe del modelo y [la auditoría separada](BITAVIDA_AI_AUDIT_V3.md); no constituyen una garantía de comprensión de cualquier pregunta.

No se realizan instrumentadas, validación visual automática ni pruebas manuales en Samsung. TalkBack, teclado, contraste sobre pantalla y rendimiento real en teléfonos modestos quedan pendientes de validación en dispositivo. La complejidad acotada y un modelo pequeño no prueban por sí solos esos resultados.
