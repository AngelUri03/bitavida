# Demostración aislada de Informe (desarrollo)

La variante `demo` tiene el identificador `com.bitavida.app.demo` y utiliza su propio almacenamiento cifrado y su propio sandbox. No abre ni modifica la base de `com.bitavida.app`. La variante está identificada como **datos simulados**; el perfil y los documentos incluyen «Persona simulada» y «Cuidadora simulada».

Compilar: `gradlew.bat :app:assembleDemo`. Instalar exclusivamente en un emulador elegido de forma explícita. Abrir «Bitavida · datos simulados», seleccionar el corte del 15 o del 31 de octubre y pulsar «Preparar y abrir demostración». En Informe seleccionar octubre de 2026; las fechas del informe se controlan desde el selector de la demo, sin modificar el reloj del dispositivo.

La carga utiliza los repositorios cifrados y casos de uso de la aplicación. Los identificadores de la semilla son estables y cada operación consulta su identidad antes de repetirse. Para restablecerla, borrar los datos **solo** de la aplicación `com.bitavida.app.demo` desde los ajustes del emulador; esto no afecta a la aplicación personal. No activar notificaciones para la demo.

## Manifiesto definido antes de comprobar el informe

Zona horaria: `America/Mexico_City`. Periodo: 1–31 de octubre de 2026. Los cortes disponibles son 15 y 31 de octubre a las 23:59:59.

- Paciente `demo-patient`; cuidadora `demo-caregiver`.
- Cuatro medicamentos lógicos, cinco periodos: Alfa, Beta, Gamma con dos periodos, y Delta. Beta cambia su cantidad el 7, se suspende el 10 a las 12:00 y se reactiva el 16 a las 12:00. Gamma finaliza el 14 a las 12:00 y tiene otro periodo desde el 18.
- Control Alfa: una ocasión diaria a las 08:00. El corte final tiene 31 ocasiones: 27 completas (días 1–27), una parcial (día 28, corregida de completa), una no realizada (día 29) y dos sin confirmar (días 30–31). La corrección es una revisión, no otra toma. Al corte del día 15 hay 15 completas.
- Beta: 25 ocasiones previstas a las 09:00 (días 1–10 y 17–31), tres completas (8, 17 y 30) y 22 sin confirmar. Al corte del 15: diez ocasiones, una completa y nueve sin confirmar.
- Gamma: 28 ocasiones previstas a las 10:00 (días 1–14 y 18–31), dos completas (8 y 20) y 26 sin confirmar. Al corte del 15: catorce ocasiones, una completa y trece sin confirmar.
- Delta: 31 ocasiones previstas a las 11:00, una completa (25) y 30 sin confirmar. Al corte del 15: quince ocasiones sin confirmar.
- Total de las cuatro fichas: 115 ocasiones al corte final: 33 completas, una parcial, una no realizada y 80 sin confirmar. Al corte del 15: 54 ocasiones, 17 completas y 37 sin confirmar. Las seis administraciones adicionales conservan la cantidad de la pauta vigente.
- Glucosa independiente: días 1, 4, 7, 10, 13, 16, 19, 22, 25, 28 y 31. Valor en mg/dL: 100 más el día. Contextos en ciclo: ayuno, después de comer, antes de dormir. Corte final: ayuno 4 lecturas, promedio 114.5; después de comer 4, promedio 117.5; antes de dormir 3, promedio 116.0. Corte del 15: ayuno 2, promedio 105.5; después de comer 2, promedio 108.5; antes de dormir 1, promedio 107.0.
- Presión independiente: días 3, 9, 15, 21 y 27. Pares 120/78, 121/79, 122/80, 123/81 y 124/82 mmHg. Promedio final: 122/80, cinco pares. Corte del 15: 121/79, tres pares.
- Peso independiente: días 1, 11, 21 y 31: 74.0, 73.9, 73.8 y 73.7 kg. Cuatro registros; cambio final −0.3 kg. Corte del 15: 74.0 y 73.9 kg, cambio −0.1 kg.
- Tres observaciones espontáneas ficticias (días 6, 18 y 25), con corrección de la del día 18 capturada el día 20. Una revisión de pies cada jueves (días 1, 8, 15, 22 y 29): el día 8 se declaró sin cambios, el 22 no realizada y las otras tres quedan sin confirmar. Al corte del 15: una sin cambios y dos sin confirmar.
- Una consulta confirmada (día 12) y un estudio programado (día 24). Sus estados no afirman que se hayan realizado.
- Dos notas creadas los días 9 y 22; dos recordatorios, uno realizado el día 5 y otro pendiente el día 23, sin alarmas. Al corte del 15: una nota y un recordatorio realizado.

El escenario no cuenta todavía con una comprobación ejecutada de carga, exportación, migraciones o gestos. La compilación no demuestra que la carga haya funcionado en un dispositivo. Esos casos requieren verificaciones adicionales antes de declarar completo el escenario.
