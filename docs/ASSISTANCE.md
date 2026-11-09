# Asistencia prevista

## Estado

Las capacidades descritas aquí son estrategia de producto y **no están implementadas**. No implican cuenta, nube ni acceso a Internet. Toda asistencia opera bajo el principio “propone la herramienta, confirma la persona”.

## Principios

- Procesamiento local y minimización de datos.
- Ningún guardado, corrección, fusión o eliminación automática.
- Explicación breve de por qué aparece una sugerencia.
- Alternativa manual siempre disponible.
- Datos ficticios en pruebas y ejemplos.
- Modelos, catálogos, fuentes y licencias versionados.
- Límites de memoria, batería y disponibilidad con degradación segura.

## Autocompletado de medicamentos

Solo puede sugerir nombres registrados previamente por la misma persona usuaria. Nunca completa dosis, unidad, frecuencia u horario. La selección requiere confirmación y conserva procedencia; no normaliza nombres para inferir equivalencias farmacológicas.

## Posibles duplicados

La primera capa será determinista y local. Una segunda capa local solo podrá advertir similitud y mostrar los registros implicados. No fusiona, descarta ni corrige. Los falsos positivos deben ser reversibles y no bloquear el registro.

## OCR administrativo

Se limita a comprobantes administrativos. Quedan excluidos recetas, resultados clínicos, lesiones y documentos cuyo contenido exija interpretación médica. El texto extraído se presenta para revisión y confirmación; no se registra ni comparte automáticamente.

## Información educativa farmacológica

No se generará con LLM. Se usará únicamente un catálogo curado con:

- identificador verificado y enlace exacto;
- fuente, jurisdicción, versión y fecha;
- licencia compatible y atribución;
- revisión profesional y caducidad editorial;
- separación clara de la indicación transcrita y del registro declarado.

No se enlaza por semejanza nominal. Si el identificador no es verificable, no se muestra ficha. El contenido es educativo general, no consejo personalizado.

## Privacidad y seguridad

No se envían datos personales, texto, imágenes, OCR ni telemetría a servicios externos. Los artefactos locales deben verificarse, almacenarse de forma protegida y poder deshabilitarse. Los logs nunca incluyen entradas de usuario ni contenido sensible.

## Validación pendiente

Evaluación de utilidad y riesgo, revisión clínica/profesional, licencias, sesgos, accesibilidad, rendimiento en dispositivos objetivo, pruebas adversariales y criterios de retirada. Ninguna capacidad pasará a producción solo por existir un prototipo.
