# Sistema de diseño Bitavida

## Mi día · curva viva, nodos reactivos y metadata homogénea (vigente)

El hilo del encabezado utiliza la altura nativa de BitavidaHeaderFlow y activa organic de forma optativa. Tiene dos puntos fijos visibles y seis segmentos cúbicos suaves con ondulaciones irregulares. Su propia forma se deforma de manera acotada junto a la estela de ida/vuelta, con desaceleración y un pequeño gesto al invertir. Fuera de vista, con lifecycle detenido o movimiento reducido, conserva una forma orgánica estática. Solo se emplean los azules y turquesas semánticos existentes.

El hilo vertical comienza exactamente en el primer nodo real de sección: no hay marcador ni segmento adicional bajo el encabezado. La estela parte de ese inicio y conserva respuesta bidireccional al desplazamiento. Los nodos de 16 dp reciben un acento breve al paso/foco visual del scroll, sin indicar cumplimiento clínico; no se reactivan por recomposición ni por cambios de altura. Los títulos de módulo usan primary y los contadores conservan su tratamiento secundario. El recorrido mantiene grosor, corredor seguro y final medido.

Cada medicamento conserva un único botón completo. Una misma composición contiene icono de medicamento y título, cantidad y metadata de horario con icono, y una banda primaria integrada para Registrar toma. La metadata refluye con el ancho y el texto ampliado, sin diseños separados para hora fija, rango o contexto. Los iconos de contexto proceden del tipo de programación existente. No hay botones anidados ni nuevos estados; confirmación, pulsación larga, teclado y Deshacer se mantienen.

## Mi día · hilos de Plan y toma como botón (histórico; presentación sustituida por la revisión anterior)

Mi día retira únicamente el resumen general de pendientes y conserva marca, fecha, saludo y paciente. BitavidaHeaderFlow activa de forma optativa una estela continua de ida/vuelta, con desaceleración en extremos y cola continua sobre el mismo trazado. La animación existe únicamente mientras el encabezado es visible, el lifecycle está iniciado y el movimiento está permitido; el comportamiento de sus otros consumidores conserva el valor predeterminado.

El hilo vertical reutiliza el lenguaje de PlanStationThread: núcleo de 2,3 dp, capa suave de 7 dp y estela direccional, con los colores semánticos existentes. Sus curvas se ajustan a anclajes reales; los nodos de sección son de 16 dp en una columna de 24 dp y los extremos medidos son puntos de 12 dp. Los iconos permanecen separados, el contador se alinea con la primera línea y los títulos conservan su espacio. Las superficies empiezan después del corredor del hilo.

Cada toma es una única superficie pulsable con forma oficial 10/28/28/22, contorno y sombra discretos. Muestra nombre, cantidad, horario y Registrar toma; conserva compresión táctil, pulsación larga, acción accesible y teclado. Tras el recibo persistido, su superficie cambia de tono semántico, se traza una verificación, el hilo recibe un acento finito y la toma se contrae en altura. Deshacer durante la transición restaura la presentación del pendiente. Movimiento reducido omite la secuencia sin perder información ni feedback.

Los controles Ver pendientes más/Mostrar menos ocupan el ancho del bloque, mantienen el botón delineado oficial y reservan espacio para el icono y texto ampliado. No se cambian los formularios ni los controles internos de Mediciones, Observaciones, Recordatorios o Notas.

## Mi día · hilo delicado y nodos mínimos (histórico; presentación sustituida por la revisión anterior)

El recorrido recupera la línea fina y las ondulaciones suaves de la baseline 0.5.4. Se eliminan los giros a bordes de superficies, tramos horizontales de conexión y círculos dobles con iconos. El hilo permanece visible en un corredor propio anterior a las superficies; los puntos turquesa son de 8 dp y se alinean con la primera línea del título. Los iconos vectoriales del módulo se muestran una sola vez en la cabecera, separados del punto. La altura tipográfica real determina esa alineación en texto ampliado.

El detalle horizontal del encabezado reutiliza BitavidaHeaderFlow como ornamento independiente terminado, con movimiento ambiental desactivado. El hilo vertical nace después mediante un anclaje real y termina en un marcador real al pie del contenido, antes de la navegación. No se fuerza una unión horizontal con el recorrido vertical ni se oculta deliberadamente la línea detrás de tarjetas.

Las superficies conectadas conservan los radios aprobados 10/28/28/22 y todas sus acciones, con contorno outline discreto y sin sombra por cada pieza lazy. El contenido empieza después del corredor del hilo, con márgenes uniformes. Los horarios largos de Mediciones se presentan completos en una composición apilada cuando corresponde; botones, familia Kodchasan y objetivos de 48 dp permanecen.

El movimiento sigue a las posiciones dibujadas realmente: entrada finita, trazo durante desplazamiento y reorganización de elementos, sin retrasar artificialmente la posición del hilo respecto al nodo. La confirmación de una toma utiliza la finalización de su animación visible, sin temporizador independiente. El movimiento reducido conserva el recorrido, los puntos y toda la información. Cambiar esta preferencia cancela la introducción y deja el trazo completo, sin repetirla al reactivar animaciones. Los puntos no indican cumplimiento clínico.

## Mi día · composición conectada y acciones explícitas (histórico; geometría sustituida por la revisión anterior)

La corrección recupera el lenguaje de la baseline 0.5.4 mediante un único recorrido orgánico de fondo, nodos de sección integrados e iconos/halos oficiales. Se elimina el gutter de 16 dp y los pequeños tramos laterales independientes. TodayCareThread deriva su recorrido de las posiciones y tamaños reales visibles de LazyColumn; las cabeceras disponen de un corredor seguro junto al nodo y las curvas amplias pasan por espacio libre o detrás de superficies opacas. No atraviesan texto ni representan urgencia o cumplimiento.

TodaySectionPiece recompone la superficie conectada de forma 10/28/28/22 de BitavidaConnectedSurface en piezas lazy contiguas, con contorno exterior y sombra discreta. Las actividades comparten agrupación, pero conservan identidad, interacción y clave propias. Medicamentos ofrece nombre/cantidad/horario y Registrar toma visible; las dos filas independientes aprovechan el ancho sin reservar toda una columna al CTA. Mediciones y Observaciones agrupan datos y pares de botones oficiales. Recordatorios usa Marcar como hecho/Posponer y un control reconocido de opciones; Notas integra el acceso secundario en su cabecera y conserva el carrusel.

Las cabeceras utilizan nodo40, título sin Pendientes repetido y contador neutro sin apariencia de botón. Con texto ampliado, el contador baja de línea para no cortar palabras. Las acciones conservan labelMedium/Kodchasan y objetivos mínimos48 dp. Fecha/hora, formularios, tipografía local y temas no cambian sus reglas.

La entrada revela el trazo mediante PathMeasure; el desplazamiento produce una estela solo durante un gesto real. Colocación, expansión y retirada se animan sin bucles en reposo. Una toma confirmada puede conservarse durante la microtransición como imagen efímera Se tomó, exclusivamente si el recibo persistido coincide con su ID y el pendiente ya salió de la fuente; el contador sigue el estado real y nunca espera la animación. Movimiento reducido omite esta fase sin perder feedback accesible ni Deshacer.

La revisión visual incluye renders reales de Compose de pantalla completa por sección, ambos temas, 320 dp y texto200 %. Las previews utilizan datos ficticios y locale español; no se restaura la antigua demo ni sus datos o navegación.

## Mi día · hilo orgánico y acciones compactas (histórico; composición sustituida por la revisión anterior)

Mi día recupera el hilo Bézier vertical como conexión ornamental entre módulos independientes. TodayThreadRow reserva el token Default (16 dp) fuera del texto, usa nodos circulares y tramos continuos entre elementos lazy. Su geometría y colores proceden del lenguaje oficial; no indica urgencia, cumplimiento ni cronología común. El revelado es breve; el desplazamiento solo actualiza el dibujo y no mantiene animaciones en reposo. Movimiento reducido conserva toda la información sin transiciones.

Las tomas son una única superficie horizontal pulsable con radio medium, borde outline, nombre titleSmall semibold, cantidad bodySmall y horario labelLarge. No incluyen estados ni dos botones permanentemente visibles. El toque confirma Se tomó mediante el flujo persistido; pulsación larga, acción semántica y Menú/Shift+F10 abren opciones en el marco inferior oficial. Mediciones y Observaciones tienen filas específicas compactas con dos acciones homologadas; Recordatorios es más ligero y conserva consulta, Hecho/Posponer. Notas mantiene tarjetas de lectura en carrusel con siguiente tarjeta parcialmente visible.

TodayTypography limita a Mi día y sus formularios una copia de los estilos oficiales con Kodchasan explícita en todos los roles; los roles Material no definidos reutilizan estilos existentes. No cambia el tema global. Los contadores son discretos por sección; tres elementos iniciales pueden expandirse y contraerse. El feedback persistido y Deshacer se alojan sobre la navegación para seguir accesibles al desplazar una lista larga; no son Toast, no toman el foco ni se muestran como éxito antes de guardar. La duración respeta el ajuste de accesibilidad de Android.

Las áreas siguen siendo de al menos 48 dp. Los renglones, pares de acciones y cabeceras se adaptan a 320 dp/texto 200 %, sin ocultar los formularios específicos ni alterar sus selectores e insets. El cierre usa el hilo y un mensaje neutral; no hay historial en Mi día ni colores nuevos por módulo. Las entradas, colocaciones y salidas usan transiciones finitas del sistema de movimiento.

## Mi día operativo y Plan definitivo (histórico; composición sustituida por la revisión anterior)

La nueva composición autorizada de Mi día conserva marca, Kodchasan, fondos, tokens y navegación. Encabezado compacto con fecha, saludo/cuidador/paciente y total textual discreto. Lista lazy con secciones independientes: Medicamentos, Mediciones, Observaciones, Recordatorios y Notas. Se retiran el recorrido/hilo protagonista, registros, resultados y resueltos de esta superficie.

Medicamentos destaca nombre, cantidad e icono de presentación y reutiliza Se tomó/No se tomó de CurrentDetailButton. Mediciones usa bloque horario lateral neutro en ancho normal y composición apilada con fuente grande; Observaciones prioriza nombres multilínea y el acontecimiento específico; Recordatorios tiene menor densidad/énfasis y acciones Hecho/Posponer. Los colores no representan urgencia. Tres elementos iniciales por categoría, ampliables con botón delineado, sin límite global. Todas las acciones mantienen al menos 48 dp y texto/semántica accesibles.

Sin pendientes, la superficie distingue programación resuelta («¡Día al corriente!») de ausencia de programación («Hoy no tienes actividades programadas»). El cierre es neutral incluso cuando hubo declaraciones negativas; no celebra adherencia. Notas permanece visible como carrusel horizontal con siguiente tarjeta parcial, título/fragmento/icono y consulta real, sin estados de cumplimiento. No hay citas ficticias.

Animaciones breves de colocación/salida, expansión y cierre respetan movimiento reducido. Los formularios conservan sus marcos y selectores oficiales; la captura específica de Observaciones reutiliza su pantalla completa. Previews cubren mezcla de categorías, claro/oscuro, 320 dp y texto 200 %.

En la raíz de Plan se eliminan solo las flechas decorativas. Las tarjetas completas conservan clic, microinteracción, bordes, colores, tipografía, chips y distribución existentes; no se sustituyen por otros iconos.


## Captura de texto en cuidado (vigente)

BitavidaLimitedTextField conserva el campo delineado, Kodchasan, shape y colores del consumidor. Los propósitos de Mediciones, Observaciones, Recordatorios y Notas definidos en CaptureLimit usan grafemas Unicode y un contador de labelSmall/onSurfaceVariant alineado a la derecha debajo del campo, con espacios alrededor de la barra y miles según la configuración regional. El contador se muestra desde el inicio; supportingText conserva un área separada para validación, sin superposición ni chips.

Al superar el máximo al escribir/pegar se mantiene el valor actual y no se activa un error por límite. Los textos anteriores se presentan íntegros, incluido su contador superior al máximo; permiten borrado progresivo. La expansión multilínea es acotada y preserva scroll interno, teclado e insets. Los editores numéricos y los consumidores de otros módulos mantienen sus reglas y presentación previas. Véase TEXT_INPUT_LIMITS.md para el inventario por campo.


## Cierre de Plan y Notas (vigente)

Recordatorios conserva la composición aprobada y sus botones oficiales. Se retiran exclusivamente campana, interruptor y toda información de avisos Android; no se reemplazan por alertas, banners ni popups. La fecha/hora sigue siendo organizativa. Los apartados históricos que describen campanas o switches ya no aplican a este módulo.

Plan reutiliza el mismo chip neutro de activos en Medicamentos, Mediciones, Observaciones y Recordatorios: shapes.small, surfaceVariant/onSurfaceVariant, labelMedium y padding Small/ExtraSmall; singular/plural y alineación derecha. Con ancho compacto o texto ampliado se permite una línea adicional para no comprimir el título. No se añade contador a las pantallas individuales ni a Notas.

Notas reutiliza CareSectionHeader, MeasurementPanel, CurrentDetailButton, BitavidaLimitedTextField y MedicationLifecycleSheetFrame; conserva Kodchasan y todos los colores, bordes, radios e insets del tema. El mosaico usa alturas naturales, separaciones Medium y dos columnas donde caben tarjetas legibles; se reduce a una columna en 320 dp o texto ampliado. Título titleSmall, vista previa acotada y miniatura proporcional con esquinas del sistema. El detalle contiene todo el texto y la imagen.

Nueva/Editar usa el mismo formulario con título, contenido multilínea e imagen opcional. Las acciones de galería, cámara, reemplazar/quitar, guardar, editar y eliminar tienen contenedor real, mínimo 48 dp y los estilos primario, delineado y destructivo existentes. Un solo host modal evita hojas anidadas, conserva IME/insets/scroll y confirma eliminar con el marco oficial. Previews cubren claro, oscuro, compacto y texto 200 %; render o compilación no prueban interacción real con cámara, teclado o TalkBack.


## Recordatorios · acciones homologadas con Medicamentos (vigente)

Las acciones reutilizan directamente `CurrentDetailButton`, el componente de Se tomó/No se tomó de `CurrentMedicationDetail.kt`, sin otra implementación de colores, bordes o tipografía. Confirmar/Completar usa `DetailButtonKind.FILLED` con `primary/onPrimary`; las secundarias usan `OUTLINED, strongOutline = true` con borde primary de 1 dp. Descartar añade `destructive = true` y utiliza exclusivamente error. Todos conservan radio medium, `labelLarge`, mínimo 48 dp y microinteracción del mismo componente. Mint/successText no se utiliza para estos botones. Este apartado sustituye los tratamientos de acciones descritos en revisiones anteriores de Recordatorios.

La principal conserva encabezado, contadores por grupo, campana y chevrón, sin contador total. Completar y Posponer mantienen proporciones equilibradas. Nuevo/Editar ofrece únicamente Hoy y Mañana como atajos delineados de dimensiones iguales; selección con ✓ y semántica selected, como las opciones de toma aprobadas. El campo Fecha conserva el calendario oficial. No se presenta ¿Necesitas ideas? ni entrada equivalente en vacío.

Posponer muestra cuatro botones secundarios en cuadrícula 2 × 2 con altura natural común y nombre contextual de menor jerarquía. Detalle separa información mediante divisor y muestra Editar como primario completo, Crear otro igual delineado completo (plus centrado, sin chevrón) y Descartar delineado semántico separado; no repite completar/posponer. Los cerrados muestran únicamente Crear otro igual, sin revisión del título ni acciones de un pendiente. Plan general muestra un chip neutro de activos junto a Recordatorios, con singular/plural; la principal conserva su ausencia de contador total. La Biblioteca de Recordatorios reutiliza AiNavigationRow delimitada, icono contextual, titleSmall y chevrón, espaciado Small entre temas y separación de categorías. Las fichas organizan su explicación, ejemplos y fuentes en AiPanel con divisores neutros, conservando textos y referencias. Se conserva adaptación a texto ampliado, desplazamiento, márgenes de 24 dp, estados, teclado e insets. Fecha/hora, switch y superficies evitan roles Material heredados lila/rosa mediante colores definidos del tema local; no cambian otros módulos, componentes compartidos ni la lógica funcional.

## Recordatorios · detalle único y ajuste tipográfico

El cuerpo y los tres puntos de la tarjeta abren el mismo detalle. El encabezado utiliza el nombre del recordatorio con `headlineSmall` semibold de Kodchasan; estado en superficie neutra pequeña, etiquetas `labelSmall`, programación `bodyMedium` y datos secundarios `bodySmall/onSurfaceVariant`. Un divisor discreto separa información y acciones. Solo completar recibe énfasis primario; Posponer/Editar comparten fila adaptable, duplicar usa la fila administrativa existente y descarte conserva separación y color semántico.

Todas las vistas medias de Recordatorios solicitan el token Large (24 dp) para margen horizontal, preservando el marco oficial y sus insets. Sus consumidores anteriores conservan ScreenHorizontal (20 dp). El contenido usa separaciones por jerarquía, sin alturas rígidas para texto, y se desplaza cuando crece.

Las tarjetas conservan título `titleSmall` semibold y dos acciones de 48 dp con `labelMedium`, de menor peso que el nombre. El chevrón se omite cuando ya existe menú de tres puntos. Los metadatos se alinean bajo el título; el historial diferencia estado y cierre, y las separaciones entre grupos y la botonera inferior usan tokens existentes.


## Recordatorios · revisión de densidad y jerarquía

Las tarjetas activas reducen bordes y relleno mediante opciones locales del panel compartido: título, acceso al detalle/Más opciones, fecha/hora y campana semántica solo cuando el aviso está activo. Se suprimen Pendiente/Próximo/Para hoy redundantes. Completar y Posponer son dos controles tonales simétricos de mínimo 48 dp, neutrales en oscuro; con texto ampliado se apilan. El historial conserva estado, programación original y cierre sin controles de actividad.

El formulario mantiene una sola acción de ayuda junto al título. La propuesta breve ofrece Usar/Ignorar; las aclaraciones son opciones explícitas. Ideas reemplaza temporalmente el contenido de la misma vista media y muestra un tema, búsqueda y filas breves. No hay tarjetas educativas dentro del formulario. Fecha/hora, detalles, switch y Guardar conservan tokens e insets oficiales. Posponer usa una cuadrícula simétrica de cuatro opciones y Detalle una sola acción primaria.

Los iconos de campana y Más opciones extienden el trazo vectorial de 21 dp existente y reciben tintes semánticos, sin colores fijos. Las previews de esta revisión permiten comparar densidad, claro/oscuro y texto 200 %; el renderizador no acredita teclado, lector de pantalla ni comportamiento real de Android.


## Evolución autorizada de Recordatorios

La principal organiza grupos dinámicos con tarjetas compactas, título flexible, fecha/hora y aviso textual, sin colores de incumplimiento. Las acciones visibles Hecho, Posponer y Opciones mantienen 48 dp y son independientes de la apertura del detalle. Los cambios de grupo y las salidas usan las transiciones existentes y movimiento reducido.

Nuevo/Editar comparte la entrada oficial y un contenido más compacto dentro de la vista media vigente. Fecha/hora se alinean en fila cuando ancho/fuente lo permiten; en compacto o texto ampliado se apilan. Hoy/Mañana/Elegir fecha usan los chips oficiales. Detalles comienza contraído; el aviso usa un interruptor semántico y una única orientación breve. La vista conserva IME, insets, scroll y los selectores oficiales sin apilar modales.

La botonera compartida admite una tercera fila opcional; los consumidores anteriores mantienen dos filas. En Recordatorios se presentan Nuevo, Anteriores y Guías y ayuda. La portada, respuesta y biblioteca organizativa usan la misma identidad y superficies de Bitavida IA, con categorías horizontales, ejemplos accionables y contenido editorial distinguible de una indicación médica. Las sugerencias aparecen solo al solicitarlas y nunca ocupan Mi día.


## Baseline oficial

**BITAVIDA UI BASELINE 0.5.4** es la referencia visual vigente. El código Compose actual es la fuente ejecutable. Figma V2, informes de iteraciones, AGSL, Liquid Glass, refracción, ondas radiales, lentes y canicas son antecedentes rechazados o históricos; no forman parte del sistema oficial.

## Identidad

“El hilo de la vida” expresa continuidad del cuidado sin convertir el recorrido en progreso clínico. El monograma B y su geometría normalizada son recursos de marca. Los nodos organizan secciones y no representan cumplimiento, diagnóstico ni severidad.

## Color

Familia principal petróleo/turquesa y superficies pastel. Tokens claros principales:

- primary `#086A7D`, background `#F5FAFA`, onBackground `#102B38`;
- surface `#FFFFFF`, surfaceVariant `#EDF5F5`;
- texto secundario `#45616D`;
- outline `#C9DCDC`;
- acentos: `#1596A5`, `#E4F5F7`, `#DFF4EA`, `#EDEAF8`, `#FFF0EC`.

El tema oscuro usa fondo tinta `#071820`, superficies `#0D252E` y texto claro `#E5F2F2`. Los estados siempre incluyen texto o semántica; el color no es la única señal.

## Tipografía, espacio y forma

Kodchasan es la familia oficial y se distribuye bajo OFL. Se usan estilos Material 3 y texto escalable. El espaciado, tamaños táctiles y formas provienen de tokens compartidos. Los contenedores de texto evitan alturas rígidas. Las superficies conservan esquinas y sombras redondeadas de la baseline.

## Componentes vigentes

- Encabezado editorial con monograma, saludo y curva integrada.
- Hilo vertical Bézier con nodos circulares.
- Superficies conectadas, acciones del hilo y estados textuales.
- Acción de confirmación con transformación visual.
- Cita expandible con sombra redondeada.
- Navegación inferior accesible.
- Fondo fluido claro/oscuro.
- Estados normal y vacío, además de previews claro, oscuro, 320 dp, texto 200 % y movimiento reducido.

## Seguimientos diarios e indicaciones vigentes

`TodayRowFrame`, `TodayStatusIcon` y `CurrentDetailButton` forman el patrón compartido para eventos diarios de Medicamentos y Mediciones: tarjeta blanca, hora a la izquierda, estado a la derecha, una sola fila seleccionada y acciones dentro de su expansión. Las lecturas adicionales no se mezclan con ocurrencias programadas. `CurrentIndicationCardFrame` es la tarjeta expandible oficial para Indicación vigente: título y flecha, resumen, estado de avisos y detalle programático numerado. Cada módulo aporta textos y datos propios sin duplicar la composición.

## Flujos guiados de indicaciones

`GuidedRegistrationComponents` define el encabezado con Atrás/Continuar, identidad, progreso, transición entre pasos, opciones seleccionables, encabezados de administración, editor expandible y filas de revisión que comparten Registrar medicamento y Configurar seguimiento de Glucosa. `GuidedTimeWindowEditorContent` contiene la experiencia oficial de ventana —pareja Desde/Hasta adaptable, cruce al día siguiente, aviso contenido en el rango y acciones—; `GuidedTimeWindowEditor` solo añade la superficie cuando el consumidor no está ya dentro del editor expandible. Así se evita anidar o superponer superficies. Los pasos usan el fondo como superficie principal; una tarjeta solo agrupa una entidad editable o el resumen final. Horarios nuevos deben reutilizar la tarjeta, la acción Agregar/Cerrar editor y las filas compartidas de hora exacta o ventana; los módulos adaptan únicamente su contenido específico, pero no duplican ni alteran esta familia visual.

Un flujo nuevo no debe precargar una frecuencia, horario o preferencia que la persona no haya confirmado. La revisión final permite volver directamente a cada apartado y mantiene los datos ya capturados. El movimiento usa `BitavidaFluidMotion` y respeta movimiento reducido.

## Selectores oficiales de fecha y hora

`BitavidaDatePicker` y `BitavidaTimePicker` son los únicos componentes visuales autorizados para capturar fechas u horas. Fecha, hora y mes/año comparten Kodchasan, tokens semánticos, formas, objetivos táctiles de 48 dp, movimiento reducido, tema oscuro y adaptación a 320 dp y texto ampliado. Todos se presentan exclusivamente en la vista media oficial: una pantalla completa abre el `ModalBottomSheet` compartido y una vista media ya abierta sustituye temporalmente su contenido, conserva el borrador y bloquea el arrastre mientras se usan controles desplazables. No se usan diálogos centrados ni se apilan superficies modales.

Cada operación debe proporcionar el valor vigente y sus restricciones reales —mínimo, máximo, rango de años o paso de minutos— sin trasladar reglas clínicas al componente. La confirmación devuelve `LocalDate` o `LocalTime`; la pantalla conserva su validación y el dominio sigue siendo la autoridad. El formato visible respeta la región y la selección horaria respeta la configuración de 12/24 horas del dispositivo.

Toda nueva funcionalidad que seleccione fecha u hora debe usar exclusivamente estos componentes. No se invocarán directamente `DatePickerDialog`, `TimePickerDialog` ni selectores alternativos desde una pantalla, salvo una necesidad técnica documentada. Los calendarios y gráficas de consulta histórica son visualizaciones y no se sustituyen por este estándar.

## Configuración del perfil

El flujo de configuración mantiene un encabezado y progreso estables dentro de un único recorrido. Atrás permanece junto al indicador de paso y Salir como acción discreta del encabezado; si existe información capturada, la salida requiere confirmación. No existe footer ni botonera fija. La única acción de avance vive al final de la tarjeta y se conecta mediante un nodo discreto al lenguaje de “El hilo de la vida”.

La pantalla consume `safeDrawing` una sola vez y aplica el IME al contenido desplazable, sin alturas ni compensaciones fijas para las barras del sistema. La acción permanece dentro de la tarjeta: responde de inmediato y el punto recorre el enlace; al llegar, una única ola ancha petróleo/turquesa avanza por la superficie recortada del botón, detrás del texto y sin líneas luminosas. Después comienza la transición de tarjeta. El viewport no persigue al CTA; solo los campos enfocados solicitan visibilidad con el teclado. Con movimiento reducido se aplica directamente el color final y se omiten ola, compresión y desplazamientos decorativos.

Las superficies, contornos, sombras e indicaciones de toque comparten exactamente su `Shape`. En particular, la declaración de autorización recorta fondo, borde y estado presionado con la misma forma redondeada. Los documentos legales se presentan en una superficie desplazable, disponible sin conexión, con versión visible, redacción de producto y texto escalable.

## Bitavida IA

La evolución autorizada desde Guías y ayuda conserva el símbolo vectorial de hilo y destello, Kodchasan y los tokens petróleo, paleBlue, mint y lavender. Inicio usa un encabezado compacto con subtítulo e indicador local/offline, campo protagonista, dos preguntas sugeridas y accesos a Biblioteca y Cómo funciona. Inicio no incluye el bloque Explora por tema ni sus botones. No muestra respuestas completas ni listas educativas desplegadas. No se alteran los fondos, geometría del hilo, curvas, navegación inferior ni composición de las mediciones aprobadas.

Todas las páginas y entradas reutilizables de Bitavida IA establecen su primer plano mediante `BitavidaAiContentColors` y `onSurface`; no dependen del `LocalContentColor` heredado de contenedores transparentes. `AiPanel` y las superficies base declaran `contentColor = onSurface`, incluso en mint y paleBlue. Los secundarios mantienen `onSurfaceVariant` y los acentos `primary`; la tipografía deja su color sin fijar. Esta política se limita a IA y no cambia el tema global, la paleta ni la geometría.

Respuesta inteligente y Detalle educativo son vistas dedicadas. La respuesta identifica la pregunta y el tema, presenta una explicación breve y adapta los apartados de importancia, ejemplo y contenido complementario a la ficha. El detalle ofrece el desarrollo del tema. Las preguntas relacionadas navegan directamente por identificador. Fuentes y referencias se consultan en un apartado secundario expandible; no aparecen URLs largas en el cuerpo principal. Una nota discreta y consistente indica que la revisión clínica sigue pendiente. No se usan burbujas de chat ni escritura simulada.

Biblioteca organiza 30 temas por Glucosa, Presión, Peso y Uso de Bitavida, con búsqueda, una única fila horizontal de filtros y tarjetas compactas en LazyColumn. Los filtros reutilizan GlucoseCaptureChip, el mismo componente oficial del calendario de Mediciones: altura natural uniforme con mínimo de 48 dp, separación de tokens, selección con color animado y desplazamiento que mantiene visible el chip seleccionado. Cómo funciona explica por separado la clasificación local y la biblioteca de respuestas. La pila conserva por entrada el desplazamiento y los apartados expandidos; al volver se recuperan también los filtros y la búsqueda de la sesión, sin guardar consultas en SavedState.

Las superficies conservan bordes, formas compartidas y profundidad ligera. El indicador de análisis solo existe mientras se ejecuta una clasificación real; desaparece al completar, cancelar o fallar. No queda una línea ni barra de progreso bajo Encontrar respuesta, ni se retrasa el resultado; las microinteracciones y transiciones usan BitavidaFluidMotion y respetan movimiento reducido. Con texto ampliado, el encabezado apila su identidad y las preguntas se distribuyen en una columna; los filtros conservan una sola fila con scroll horizontal y los textos no tienen alturas fijas. Las acciones mantienen objetivos de al menos 48 dp y estados expresados con texto o semántica. Los previews de las vistas deben cubrir claro, oscuro, 320 dp y texto 200 %, sin ejecutarse automáticamente.

Medicamentos reutiliza la misma experiencia de Bitavida IA, sin una identidad paralela. `MeasurementGuideActions` es la botonera agrupada oficial compartida: icono plus, Agregar medicamento/Agregar mediciones, divisor e icono file para Guías y ayuda, con idénticas superficies, bordes y motion. Biblioteca de medicamentos usa los chips oficiales en una fila horizontal por familia, buscador y tarjetas compactas. Respuesta/Detalle conservan encabezado editorial y apartados farmacológicos desplegables; el apartado preguntado se prioriza, y efectos frecuentes y señales importantes tienen textos diferenciados. Las referencias presentan institución, documento, versión y consulta, con enlace de acción explícita; no muestran URLs largas en el cuerpo. Todos los estados reutilizan `BitavidaAiContentColors`, `onSurface`, `onSurfaceVariant` y `primary`. Se incluyen previews ficticias de Inicio, Biblioteca, Respuesta, Detalle expandido y ambigüedad en claro/oscuro, compacto y texto 200 %.

## Motion oficial

### Curva superior

Ocupa aproximadamente 92 % del ancho disponible. Es fina, translúcida e integrada con los planos del fondo. Una iluminación suave viaja sobre el trazo; no hay AGSL, refracción ni deformación del contenido.

### Hilo y estela

La geometría estructural permanece limpia. Durante el scroll, una única cabeza luminosa con estela suave sigue el Path Bézier. Su posición global es continua entre secciones, invierte sin reiniciar, limita el retraso visual y se alinea en extremos. La intensidad responde a la velocidad y se desvanece al detenerse.

Los nodos reaccionan por proximidad real a esa misma estela: aumenta sutilmente su luminosidad y halo, sin temporizadores independientes ni significado clínico.

### Microinteracciones

Presión, liberación, selección, confirmación, revelado y expansión usan las duraciones definidas en `BitavidaFluidMotion`. No deben bloquear callbacks ni alterar el scroll nativo. La animación de confirmación y la expansión de citas forman parte de la baseline.

## Movimiento reducido y ciclo de vida

Con movimiento reducido, encabezado, hilo y nodos muestran su estado estático completo; las funciones siguen disponibles. Las animaciones ambientales se suspenden fuera del viewport o en segundo plano según los componentes vigentes. No se crearán corrutinas por evento de scroll ni efectos de pantalla completa.

## Reglas de cambio

No reinterpretar ni “mejorar” esta baseline sin autorización explícita. Toda propuesta visual debe proteger primero geometría, legibilidad, continuidad, accesibilidad y semántica sanitaria.
