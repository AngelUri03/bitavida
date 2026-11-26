# Manifiesto independiente del escenario Demo, versión 1

Este documento especifica conjuntos y resultados esperados a partir de las reglas de
la semilla, sin llamar al exportador ni utilizar sus cálculos. Es una referencia para
contrastar datos e informes; **no acredita que la carga o el PDF se hayan ejecutado
correctamente**. Todas las personas, eventos y datos son ficticios.

## Referencia y ventanas

Sea R el instante guardado antes de iniciar la carga y Z la zona horaria guardada en
esa misma operación. Sea D la fecha local de R, F el primer día del mes de D menos
dos meses naturales, e i el número de días desde F. El escenario abarca F hasta D;
una captura solo se inserta si su instante local convertido con Z no es posterior a R.
Las reglas sobre fechas son fijas para esa instalación. Las modificaciones posteriores
del usuario pueden cambiar legítimamente los conjuntos.

Para una primera apertura el 23 de noviembre de 2026, las ventanas cerradas son
septiembre y octubre de 2026 y la abierta es noviembre hasta R. El ejemplo de conteos
de abajo aplica a esos meses completos, sin depender de la hora del 23.

## Perfil y medicación

Perfil `demo-profile`, persona `demo-patient`, cuidadora `demo-caregiver`.
Las siete pautas `demo-medication-1` a `demo-medication-7` corresponden en orden
a metformina, losartán, pantoprazol, atorvastatina, paracetamol, omeprazol y
un segundo periodo de pantoprazol. El segundo periodo referencia la pauta 3.
Son seis nombres y siete periodos, no siete medicamentos lógicos.

Todas las pautas tienen una tableta diaria. Horas: 08:00 a 13:00 para pautas 1–6
y 10:00 para la 7. Las pautas 1–5 empiezan en F; la 6 comienza D + 5 días; la 7
comienza F + 25 días. La pauta 3 finaliza en F + 20 días, la 4 se suspende en
F + 30 y la 2 se suspende en F + 8 y reactiva en F + 12, a las 12:00. No se
deben proyectar ocasiones en pausas ni entre periodos. La pauta 5 termina en F + 20.

Para cada ocasión proyectada válida, si su captura cinco minutos después no supera R
y i no es múltiplo de 9 se espera `demo-intake-<plan>-<fecha ISO>`, excepto que las
ocasiones del día D de las pautas 2 y 7 quedan sin confirmar. Si i es múltiplo de 17
la toma esperada se declara no realizada; en los demás casos se declara completa,
salvo la corrección de `demo-intake-demo-medication-1-<F+1>`, cuyo estado vigente es
parcial y cuyo historial conserva la declaración anterior completa. Las ocasiones
restantes no tienen respuesta.

El ID de cada ocasión incorpora la pauta, la revisión de pauta vigente en el momento
programado, el ID del horario y la fecha. Las suspensiones y reactivaciones producen
revisiones nuevas: una toma anterior debe conservar el ID de la revisión histórica,
no el de una revisión futura. Comparar el ID de pauta, el ID de ocasión y la fecha
programada, además del total; una revisión no constituye otra toma. El número exacto
de ocasiones depende de los cortes temporales y de los límites de suspensión y
finalización del dominio. Los informes generados antes de corregir esta siembra no
demuestran las asociaciones de una instalación nueva.

## Mediciones espontáneas

Los IDs contienen fecha ISO. Con i como arriba, y siempre que la captura no supere R:

| Tipo | Conjunto esperado | Valor y contexto |
|---|---|---|
| Glucosa | `demo-glucose-<fecha>-morning` y `-evening` cuando i no es múltiplo de 7 | 07:00 ayuno y 19:00 después de comer; valor = 94 + ((7i + hora) módulo 33), mg/dL; captura cinco minutos después |
| Presión | `demo-pressure-<fecha>` cuando i no es múltiplo de 6 | 09:00, reposo; par (116 + i módulo 12)/(73 + i módulo 8) mmHg; captura 09:05 |
| Peso | `demo-weight-<fecha>` cuando i es múltiplo de 4 | 08:00; décimas de kg = 738 + ((i / 4) módulo 9), división entera; captura 08:05 |

Para septiembre y octubre de 2026 se esperan respectivamente 50 y 54 glucosas,
25 y 25 pares de presión, y 8 y 8 pesos espontáneos. En septiembre se suma una
lectura vinculada por tipo descrita abajo; por tanto, antes de modificaciones, los
totales esperados son 51 glucosas, 26 pares y 9 pesos en septiembre. Contar cada
presión como un par completo, no como dos mediciones. Las glucosas se agrupan por
su contexto; el peso no tiene momento de medición.

## Seguimientos de mediciones

`demo-followup-glucose`, `demo-followup-pressure` y `demo-followup-weight`
son diarios desde F a las 07:00, 08:00 y 09:00 respectivamente, sin avisos.
En F + 2 cada uno tiene una lectura vinculada de ID
`demo-followup-glucose-reading`, `demo-followup-pressure-reading` o
`demo-followup-weight-reading`: 108 mg/dL en ayuno, 120/78 mmHg en reposo y
73.8 kg, respectivamente. En F + 3 cada uno tiene una decisión explícita
`demo-followup-<tipo>-missed` de no realizada. Las demás ocasiones carecen de
respuesta, incluidas las del día de referencia; el paso del tiempo no las convierte
en omisiones. Contrastar la referencia de cada respuesta con la identidad de su
ocasión programada, no con coincidencia de hora.

## Observaciones

El seguimiento `demo-observation-followup` corresponde a OBS-001, lunes a las
18:00 desde F. Las revisiones de lunes con día del mes no divisible por 3 y cuya
hora no supere R tienen ID `demo-observation-review-<fecha>`. Son no realizadas
si el día del mes es divisible por 4; las demás se declaran sin cambios. Los lunes
no incluidos no tienen respuesta.

Los días pares a las 14:00 tienen observación espontánea
`demo-observation-spontaneous-<fecha>`, si no supera R. La categoría rota según
el día módulo 8: 0 OBS-008, 2 OBS-010, 4 OBS-019 y 6 OBS-027. Los días 6 y 22
incluyen imagen local `<id>-photo`. La observación del día 4 del primer mes tiene
una corrección de descripción al día siguiente; es la misma observación, con
historial separado.

Ejemplo septiembre/octubre de 2026: 15 observaciones espontáneas en cada mes.
En septiembre hay dos revisiones sin cambios y una no realizada; el lunes 21 queda
sin confirmar. En octubre hay tres revisiones sin cambios; el lunes 12 queda sin
confirmar. Esto no contabiliza la corrección como observación adicional.

## Notas, recordatorios y agenda

Notas: `demo-note-supplies`, `demo-note-questions`, `demo-note-routine` y
`demo-note-walk`. Las dos intermedias tienen imágenes locales cifradas. La rutina
tiene una edición posterior a su creación.

Recordatorios activos: `demo-reminder-active-1` a `-7`; el primero está previsto
para R, los demás entre D + 1 y D + 7, con reprogramación del segundo. Existen
`demo-reminder-done-1` y `demo-reminder-done-2` como antecedentes realizados.
No se solicitan notificaciones. El modelo de recordatorios actual no expresa
frecuencias periódicas: no se debe presentar esta semilla como si las cubriera.

Citas/estudios: `demo-event-family-1`, `demo-event-family-2`,
`demo-event-specialty`, `demo-event-study`, `demo-event-next`,
`demo-event-rescheduled`, `demo-event-cancelled`, `demo-event-next-study`.
Las tres primeras son consultas de seguimiento; las dos de tipo estudio son
programadas. La consulta reprogramada conserva una revisión; la cancelada conserva
la declaración de cancelación. Las citas pasadas se conservan confirmadas o
programadas: no se ha declarado que fueran atendidas.

## Contraste pendiente

Para cada PDF mensual deben compararse IDs y estados vigentes con estos conjuntos,
sus revisiones por separado, pares de presión, contextos de glucosa y primer y
último peso del periodo. Las pautas históricas y la programación posterior al corte
deben distinguirse. Revisar además todas las páginas, índice, enlaces y visor.

Este documento no demuestra que los repositorios hayan aceptado los datos, que los
adjuntos se hayan reabierto, ni que los PDF o sus cifras coincidan. Tampoco cubre
todavía el volumen y variedad completos solicitados para todos los módulos; las
comprobaciones de dispositivo, exportación e interrupción deben documentarse con
sus resultados reales.
