# Procedencia del escenario simulado: infraestructura preparada

**Estado:** infraestructura incompleta e inactiva. La aplicación sigue usando la
versión 33 de su base. No hay carga del mes, borrado, reparación ni cambio del
esquema registrados por este diseño.

La autorización actual permite añadir datos simulados identificables a la
aplicación principal. Antes de habilitar esa carga se actualizará
`docs/PRODUCT.md` para reflejar ese alcance y se validará en una copia.

## Identidad y transacción

La migración aditiva preparada 33 → 34 crea dos tablas nuevas: escenario con ID
estable y hash de manifiesto, y correspondencias entre escenario, espacio de
nombres, clave del fixture e ID persistido. El espacio de nombres identifica la
entidad física, por ejemplo `medication_plans` o `glucose_measurements`;
nombres, fechas y valores editables nunca son identificadores. La clave
cualificada `(namespace, entityId)` no puede pertenecer a dos escenarios. El
diseño no asigna procedencia a registros anteriores ni modifica sus valores.

El coordinador preparado crea escenario, registros y correspondencias dentro de
una transacción de escritura de la **misma instancia Room**. Si falla la carga,
todos sus cambios SQL deben revertirse; repetir el mismo escenario y hash no
vuelve a llamar al creador. Si el hash difiere, se rechaza. Las operaciones no
SQL, notificaciones, archivos y alarmas deben posponerse hasta después del
commit: la transacción no puede revertirlas. Se requiere verificar en pruebas
que los repositorios anidados usan efectivamente la misma conexión transaccional.

Este diseño no comprueba que un callback declare todos los registros que creó,
ni verifica automáticamente que una identidad señalada le pertenezca. No debe
usarse para borrar o restablecer datos sin una comprobación adicional de
identidades, relaciones, efectos secundarios y dependencias de cada módulo.
Tampoco puede determinar si ya existen registros simulados previos sin marcar.

## Trabajo indispensable antes de conectar la carga

1. Registrar entidades, DAO y migración en Room; generar y revisar el esquema
   34. Probar migración real 33 → 34, instalación nueva y rutas antiguas
   soportadas con el validador generado de Room, además de comparar tablas,
   BLOB, índices, claves foráneas y contenido anterior en una copia cifrada.
2. Conectar la creación a los casos de uso y repositorios de perfil, medicamento,
   periodos, indicaciones, horarios, ocasiones, tomas, revisiones, glucosa,
   presión, peso, observaciones, fotografías solo como referencias de prueba,
   citas, notas y recordatorios. Registrar la identidad cualificada de cada
   entidad y revisión realmente creada; documentar para cada una su tabla,
   clave y relación con el escenario.
3. Comprobar de forma independiente que cada identidad existe, pertenece al
   paciente ficticio, conserva su origen y no está asociada con datos originales.
   Exigir un manifiesto previo inmutable con claves y estados esperados; comparar
   conjuntos de identidades, no solo contadores.
4. Asegurar operaciones de dominio repetibles, generación estable de IDs y
   semántica transaccional para llamadas anidadas y cancelaciones. Diferir
   alarmas, notificaciones, fotos y otros efectos no transaccionales, o
   implementar compensación explícita verificable; no duplicarlos en reintentos.
5. Integrar el indicador de procedencia en la instantánea del informe, vista y
   PDF sin clasificar datos originales por coincidencia textual. Probar cortes,
   horarios, pausas, correcciones y escenarios ambiguos conforme al manifiesto.
6. Diseñar y verificar aparte un eventual restablecimiento selectivo y auditable
   del escenario. La infraestructura preparada no ofrece dicha operación y no
   autoriza borrar entidades o revisiones existentes.

## Pruebas preparadas

`SimulationProvenanceMigrationTest` utiliza una base SQLCipher ficticia y
descartable y comprueba preservación de un BLOB anterior, rollback, clave
foránea, exclusividad de identidades cualificadas y ausencia de tablas tras
revertir la migración. Esta prueba aislada no valida el esquema Room completo,
el historial real, los repositorios ni una carga de octubre. Sus resultados
solo pueden afirmarse después de compilarla y ejecutarla en el destino elegido.
