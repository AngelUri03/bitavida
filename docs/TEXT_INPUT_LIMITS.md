# Inventario de captura de texto de cuidado

Este inventario se realizó antes de aplicar los límites. La política vigente sustituye únicamente los límites de los cuatro módulos indicados; Medicamentos conserva sus controles y máximos.

| Área y campo editable | Restricción encontrada | Política vigente |
|---|---|---|
| Glucosa, Presión y Peso: nota de lectura, crear/corregir | 300 unidades UTF-16 | 500 grafemas |
| Glucosa, Presión y Peso: motivo de no realizada y corrección | 300 unidades UTF-16; recorte take | 300 grafemas |
| Seguimiento de Mediciones: indicación médica transcrita | 1000 unidades UTF-16 | 1000 grafemas, por su finalidad de transcripción |
| Seguimiento de Mediciones: motivo de cambio/eliminación | 500 unidades UTF-16 | 300 grafemas |
| Observaciones: descripción de registro/corrección | UI 1000 y validación 2000, discrepantes | 500 grafemas |
| Observaciones: propuesta de descripción editable | 1000 en UI | 500 grafemas |
| Observaciones: respuestas TEXT del catálogo (zona, desde cuándo, etc.) | 1000 | 100 grafemas |
| Observaciones: motivo de corrección y de eliminación de seguimiento | 500 | 300 grafemas |
| Observaciones: indicación médica transcrita de seguimiento | 1000 | 1000 grafemas |
| Observaciones: búsqueda del catálogo | 300, recorte take | 100 grafemas |
| Recordatorios: título en nuevo/editar/duplicar | UI usaba transcripción 4000; sin máximo de dominio | 100 grafemas |
| Recordatorios: detalles en nuevo/editar/duplicar | UI 2000; sin máximo de dominio | 500 grafemas |
| Recordatorios: búsqueda de ideas | UI 300, ViewModel take100 | 100 grafemas |
| Notas: título en nuevo/editar | 240 | 100 grafemas |
| Notas: contenido en nuevo/editar | 12000 | 2000 grafemas |
| Bibliotecas educativas de Mediciones/Observaciones/Recordatorios: búsqueda | take100, sin contador compartido | 100 grafemas |
| Ayuda educativa de estos módulos: pregunta | 500 unidades UTF-16 | 500 grafemas, con la finalidad y máximo propios del motor |

Observaciones no tiene título libre independiente: el nombre procede del catálogo. Notas no incorpora buscador actualmente. Se mantienen sin cambios los editores numéricos (lecturas, objetivos de peso, intervalos de días), fechas/horas, selectores de contexto y respuestas de catálogo.

## Regla compartida y persistencia

TextCharacters define conteo y recorte transitorio por grafemas extendidos. CaptureLimit vincula cada propósito con su máximo. BitavidaLimitedTextField reutiliza la entrada existente, agrega contador secundario alineado a la derecha y conserva mensajes de validación en una línea/área separada. Los campos nuevos de cuidado muestran el contador desde el inicio, permiten ocultarlo opcionalmente y limitan la expansión multilínea; las entradas numéricas conservan sus reglas anteriores.

Una propuesta de escritura o pegado que excede el límite se rechaza entera, manteniendo el texto actual y sin activar error de máximo. No se recortan cadenas UTF-16 a mitad de una secuencia. El contador usa la configuración regional para separar miles. No se añade región viva por pulsación que interrumpa TalkBack.

Los casos de uso y las transacciones de persistencia revalidan los mismos máximos. Al cargar datos no se aplica ninguna transformación. Campos históricos que exceden el máximo pueden conservarse idénticos al modificar otros datos; el editor permite borrarlos progresivamente. Un reemplazo nuevo y la duplicación deben cumplir la política actual. Las instantáneas anteriores conservan el texto completo. No hay cambio de esquema ni migración de contenido.

Observaciones conserva su codificador writeUTF: además del máximo visible se comprueba la capacidad técnica de 65535 bytes UTF-8 modificado por cadena, antes de codificar. Esta comprobación afecta solo a texto nuevo/reemplazado de ese módulo y evita excepciones con secuencias Unicode extremas; no impone ese límite a los textos SQL de Notas.

La implementación usa el patrón Unicode de grafema extendido documentado por [Android Pattern](https://developer.android.com/reference/java/util/regex/Pattern#unicodesupport), sin dependencias adicionales.

## Validación

Se preparan pruebas JVM de límites exactos, creación/edición, Unicode compuesto, pegado, textos históricos, persistencia y aislamiento de las reglas numéricas, además de contratos Compose de contador, mensajes, tema y texto ampliado. Los resultados reales de la ejecución conjunta se registran en PRODUCT.md. Las pruebas Android se compilan; su ejecución, teclado/TalkBack y validación visual en hardware no se acreditan mediante las pruebas JVM.
