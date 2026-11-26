# Guías y ayuda de Observaciones

## Alcance implementado

La pantalla principal de Observaciones conserva el catálogo fijo OBS-001…OBS-027 y termina con la botonera compartida de Historial de observaciones y Guías y ayuda. El historial global inicia sin filtro; los historiales abiertos desde un detalle conservan su filtro exacto.

Guías y ayuda reutiliza la instancia existente de Bitavida IA:

- MultinomialNaiveBayes y el mismo artefacto local versionado;
- normalización, corrección ortográfica conservadora, umbral de confianza y rechazo;
- AskBitavidaAi, BitavidaAiViewModel y las pantallas de portada, respuesta, detalle y biblioteca;
- operación completamente offline y sin acceso a registros, perfil o fotografías.

ObservationEducationLibrary contiene dos temas generales y 27 fichas ligadas por observationId al catálogo funcional. La biblioteca muestra únicamente esas 27 fichas, agrupadas por las ocho categorías oficiales. Cada ficha conserva importancia, explicaciones generales no diagnósticas, riesgo de retrasar la identificación, datos útiles para registrar, orientación sobre consulta, temas relacionados y fuentes.

## Revisión clínica

Todo el contenido se marca PENDING_HUMAN_REVIEW. La integración técnica y las pruebas no sustituyen la revisión clínica/editorial previa a publicación. Las recomendaciones urgentes son mensajes generales; Bitavida no determina gravedad ni realiza triaje.

Fuentes oficiales consultadas el 19 de noviembre de 2026:

- IMSS — Cuidados generales de los pies en personas con diabetes.
- NIDDK — Síntomas y causas de la diabetes; problemas de los pies.
- MedlinePlus — hipoglucemia, hinchazón, visión, náuseas y vómitos, evacuaciones, micción, confusión, caídas, fiebre, piel, dolor y sueño.
- CDC — signos y síntomas de accidente cerebrovascular.
- National Institute on Aging — equilibrio y caídas.
- NIDCR — diabetes y salud oral.

Las direcciones y fechas quedan incluidas en cada ficha. El texto permanece disponible sin conexión; abrir un documento oficial es una acción explícita y depende de que Android disponga de una aplicación para la dirección.

## Clasificador

El corpus añade 29 intenciones y sus expresiones coloquiales sin copiar preguntas del conjunto de aceptación. El artefacto contiene 69 temas educativos, 1,120 documentos y 7,239 características. La evaluación independiente de Observaciones cubre paráfrasis, ausencia de acentos y errores ortográficos: 29/29 temas aceptados correctamente y 4/4 consultas ajenas rechazadas. En contexto Observaciones, una clasificación segura de otro módulo se convierte en incertidumbre y ofrece temas de Observaciones; nunca muestra una respuesta cruzada como si correspondiera.

## Privacidad

Las preguntas y búsquedas permanecen en memoria de la sesión. No se guardan, registran, transmiten ni se usan para entrenamiento. El clasificador no recibe nombres, historial, mediciones, imágenes ni otros datos del paciente. No se añadieron permisos, servicios, dependencias ni migraciones.
