# Changelog

## Unreleased

### Barras guardadas, como el Hotbar Swapper de Axiom

- **Guarda tu barra rápida con un nombre y un icono y cambia de una a otra al momento.** Se guardan los nueve huecos con todo lo que llevan (encantamientos, nombres, durabilidad, libros, cajas con contenido…), hasta 64 barras por jugador y mundo.
- **Mantén Alt izquierdo** (como en Axiom; se cambia en Controles) y sale la tira con tus barras sobre la de verdad: la rueda elige otra y al soltar cambias a ella. **Tócala** y se abre el **menú circular**: tus barras con su icono y su nombre, sus nueve huecos al pasar el ratón, clic para usarla, clic derecho para copiar su código y, abajo, guardar la que llevas, pegar un código y copiar todas.
- **Creativo: al instante.** El cliente la pone en ese momento y el servidor la confirma con su copia guardada (el cliente solo dice cuál: nadie puede colar objetos). La barra que dejas se guarda sola en la que estabas usando, como en Axiom (`/komaedit hotbar autosave off` lo apaga), y `/komaedit hotbar undo` vuelve a la de antes.
- **Supervivencia: solo reordena lo que llevas, nunca crea objetos.** Busca cada objeto de la barra en el inventario (el mismo con sus componentes y, si no, el mismo objeto) y lo cambia de sitio; lo que no llevas se queda como estaba y se avisa. La tira y el menú lo dicen en amarillo. Con `komaedit.admin.hotbar.give` (administradores; los operadores lo tienen) se pone tal cual en cualquier modo, y se avisa en naranja de que crea objetos.
- **En el Studio**, la tarjeta **Barras rápidas** (Utilidades): tus barras con sus nueve huecos (clic para usarla, clic derecho para copiar su código), guardar la que llevas, pegar y copiar códigos, el guardado al cambiar, volver a la de antes y **Crear barra** desde una paleta, un degradado, la selección, un sello o un tejado. Todo menos Crear va por el paquete de las barras, no por el chat.
- **Barras hechas de otra cosa:** de una paleta (`##Nombre`, `#guardada`, bloques o `kp1:`), del degradado de color en nueve pasos, de los nueve bloques más usados de la selección, de la paleta de un sello de relieve o del pincel de pintura y de un tejado (el material con sus escaleras, losas y demás piezas). Los bloques con estado salen con su `block_state`. `/komaedit color gradient` y `color palette` llevan **[guardar barra]**.
- **Compartir:** `/komaedit hotbar export` da un código `kh1:…` (una barra o todas) e `import` lo guarda; una barra de bloques cabe en el chat, y los códigos largos se pegan desde el menú (hasta 32 000 letras).
- Órdenes: `/komaedit hotbar` (alias `barra`) `save`, `load`, `list`, `palette`, `gradient`, `selection`, `stamp`, `paint`, `roof`, `export`, `import`, `undo`, `autosave`, `rename`, `icon`, `move` y `delete`. Permisos `komaedit.hotbar` (básico) y `komaedit.admin.hotbar.give` (administrador).
- Código: paquete `hotbar` (modelo, almacén, códec `kh1:`, reglas por modo de juego, reordenación de supervivencia y fuentes, en Java puro), `HotbarStacks` con el códec de `ItemStack`, `HotbarService` y tres paquetes de red (`hotbar_action`, `hotbar_sync`, `hotbar_text`); la tira y el menú (`hotbar.ui`) se dibujan sobre `StudioCanvas`, así que las maquetas son el mismo código que el juego. Pruebas: guardar y cargar con componentes, límites, la decisión del servidor en creativo, supervivencia, espectador y con el permiso de administrador, la reordenación con miles de inventarios al azar (nunca crea ni borra), crear desde paletas, degradados, selección y tejados, exportar e importar (con códigos dañados y bombas de compresión), la tecla (mantener, rueda, toque, Alt+Tab) la tira y el menú en pantallas pequeñas, y la tarjeta del Studio (la orden de cada fuente, usar con un clic, sustituir y borrar con un segundo clic). Maquetas en `docs/renders/hotbars/` (1080p con GUI 3 y 854×480 con GUI 2, español e inglés). Nada probado aún dentro de Minecraft.

## 1.9.1 (estable, 2026-10-21)

### Bienvenida guiada: lo primero que ve quien abre el Studio

- **Cinco pasos sobre el mundo de verdad, en un par de minutos.** La primera vez que alguien abre el Studio sale **Primeros pasos en el Studio**: plantar un árbol con un clic donde apunta el cursor, moverlo con el gizmo y colocarlo, levantar una montaña con el sello de relieve y cambiarle la semilla, pintarla con el preset **Cumbres** del pincel de pintura y deshacer desde el historial. Cada paso enseña algo que se usa siempre: elegir, ver antes de colocar, confirmar y deshacer.
- **Una burbuja corta junto a lo que hay que tocar**, que lleva un borde de acento con una luz que le da la vuelta: en qué paso va, una frase de qué hacer y por qué, y **Saltar paso** y **Saltar bienvenida**. Va sobre el mundo, al lado del panel, sin tapar controles. Cada paso avanza solo cuando el jugador lo hace (el fantasma sale, se mueve, se coloca, el pincel pinta, se deshace…); si se va a otra herramienta, espera y sigue donde iba.
- **Segura en cualquier servidor.** Nada se coloca sin confirmar: durante la bienvenida Construir va con vista previa y los pinceles con la vista previa de cada clic, que se aplica o descarta desde la burbuja; al acabar, saltarla o cerrar el Studio vuelve lo que tenías. Sin el permiso de un paso, ese paso se salta solo y se dice; en supervivencia o sin permisos de construir solo enseña, con **Siguiente**, sin mandar nada. La entrada y el paso de colocar avisan de que `Ctrl+Z` lo deshace todo.
- **Sale una vez por jugador** (queda en `config/komaedit/studio.json`) y se vuelve a lanzar desde la chuleta (`F1` o `?`, **Bienvenida guiada**) y con **`/komaedit tutorial`** o **`/komaedit bienvenida`**, que abren el Studio con ella. En español e inglés.
- Código: `WelcomeTour` (máquina de estados sin Minecraft), `WelcomeGuide` (enganche con `StudioView`) y `WelcomeBubble` (dibujo); anclas con nombre en `StudioUi` para señalar controles; paquete `studio_welcome` del servidor al cliente para la orden. Pruebas: `WelcomeTourTest` (avanzar con la acción real, saltar, sin permisos, supervivencia, relanzar), `StudioWelcomeTest` (el recorrido entero con los controles, que nada se manda sin vista previa ni sin Aplicar, que se guarda que ya se vio y que al cerrar vuelve lo de antes) y su sitio junto al panel; contraste en los cuatro temas. Maquetas de cada paso a 1080p con GUI 3 y a 854×480 con GUI 2, en español e inglés, en `docs/renders/studio/bienvenida/`; la chuleta, regenerada. Nada probado aún dentro de Minecraft.

### Pasada de calidad sobre lo que entró desde la 1.5.0

- **El clic del Studio en el mundo llega al servidor.** El paquete «donde haces clic» (`studio_aim`) nunca se había registrado: el cliente se lo callaba, así que colocar un árbol, una roca, un texto o un plano con un clic, o pintar arrastrando con el Studio abierto, iba al centro de la pantalla y no al cursor. Ahora se registra, se apunta antes que la orden que va detrás (otro `execute` lo dejaba para después de ella) y se olvida al salir y al parar el servidor.
- **Sin expulsiones por spam.** Minecraft echa a quien no es OP si manda más de una orden por segundo de media, y el pincel que sigue a su tarjeta del Studio mandaba `pincel actualizar` a cada cambio (pulsando «+» cada 0,35 s, unos 16 clics bastaban); la vista previa en vivo, una cada 0,7 s. Ahora las dos dejan al menos 1 s entre órdenes y el último cambio sigue llegando.
- **La documentación se lee entera.** Un bloque de ejemplos sin cerrar en «Superficie» ponía del revés, en GitHub, todo lo que venía después (desde Texturizado). El índice tiene las tres secciones que faltaban (Escalar y girar el portapapeles, Pinceles de modelado y Biblioteca de planos) y ya no habla de las etapas Modificadores, Material ni Envejecer.
- **Fuera lo que dejaron los rediseños:** 15 claves de idioma sin uso (Fusión y Kitbash, «Al chat», «Actualizar» de Planos, la etapa Estructuras, el rango de los números de antes…), los iconos y colores de las etapas Modificadores y Material, métodos y constantes sin uso y las maquetas viejas de `docs/renders/studio/antes/`.
- **Build más rápido** (`./gradlew build --rerun-tasks`, de unos 3 min a poco más de 1 min, medido en un PC de 16 hilos): las pruebas se reparten entre varias JVM; los benchmarks del pincel y de la pintura solo corren con su variable (`KOMAEDIT_BRUSH_BENCHMARK`, `KOMAEDIT_PAINT_BENCHMARK`), y las exhaustivas (formas de `ezspline`, especies y semillas de árboles, los 80 PNG de sellos frente a sus recetas) pasan en cada build por una muestra fija y enteras con `KOMAEDIT_FULL_TESTS=1`.
- **Pruebas que no dependen del PC:** los nombres de bloque en español del caché de Loom solo se leen al pintar las maquetas. La foto del terreno de los pinceles reparte su presupuesto por tick con un reloj que se le pasa, y sus pruebas usan uno falso (en el CI fallaban con la máquina cargada); los límites de tiempo de reloj de las pruebas de rendimiento solo se comprueban con `KOMAEDIT_FULL_TESTS=1`. `NetworkRegistrationTest` exige que cada paquete esté registrado una vez y tenga su receptor al otro lado.
- Código: los rasterizadores de los renders de prueba comparten la lectura de texturas (`preview.TextureTiles`). Nada probado dentro de Minecraft.

### Ediciones enormes en servidores con gente

- **Ningún tick se va de las manos.** Todo lo que KomaEdit hace en el hilo del servidor (leer selecciones, colocar, pintar biomas, preparar vistas previas, pinceles…) sale de un mismo presupuesto por tick: `tickBudgetMillis`, 6 ms de serie. Baja solo si el servidor ya gasta casi todo el tick, y por debajo de 20 TPS se queda en el mínimo (`tickBudgetMinMillis`, 1 ms) hasta que se recupera. Antes cada parte tenía el suyo y podían sumarse (12 ms la colocación, 4 la lectura, 5 los pinceles…).
- **La colocación ya no lo escribe todo al final.** WorldEdit guarda en un búfer todo lo que se coloca hasta cerrar la sesión, y deja la luz, los vecinos y el envío a los clientes para ese momento; KomaEdit no lo vaciaba, así que una edición enorme caía entera en el último tick (con un millón de bloques, varios segundos de servidor parado) y nadie veía nada hasta entonces. Ahora coloca por ventanas que pasan al mundo con su luz, vecinos y envío dentro del presupuesto, agrupadas por chunk y sección: los bloques aparecen poco a poco y sigue siendo un solo //undo.
- **Cola justa.** El trabajo pesado se reparte por turnos entre jugadores en cada tick: una edición de millones de bloques no bloquea las pequeñas de los demás. Si ya colocan `maxConcurrentJobs` ediciones, la tuya espera su turno en vez de rechazarse («la cola de KomaEdit está llena»): la barra y el pie del Studio dicen el puesto y cuándo empezará, y `/komaedit cancel` la saca de la cola.
- **El plan de una edición enorme ya no ocupa gigas.** Por encima de 131 072 bloques (`planCompressAboveBlocks`) se guarda comprimido por páginas con paleta, como las secciones de un chunk: un relleno de 20 millones de bloques pasa de 310 MB a 4 MB, y montarlo, de unos 2 GB de pico a menos de 100 MB. Si aun así pasa de `planMemoryMegabytes` (128 MB), el resto va a un archivo temporal que se borra solo.
- **Límites claros antes de empezar.** Si una edición pasa del límite del servidor, de lo que KomaEdit lee de una vez o de tu `//limit` de WorldEdit, se dice antes de leer nada: cuántos bloques tiene, cuál es el límite y en cuántos trozos partirla. Con una selección en caja, los trozos vienen listos: **[trozo 1] [trozo 2] …** o `/komaedit trozo <n>`. Antes, el `//limit` paraba la edición a medias.
- **`/komaedit jobs` para administradores** (permiso `komaedit.admin.jobs`, en el paquete admin): TPS, milisegundos por tick, lo que usa KomaEdit y una línea por jugador con su fase, bloques y tiempo, con botones para **pausar**, **reanudar** y **cancelar**. Una edición en pausa termina la ventana que vaciaba, deja su sitio en la cola y sigue donde iba al reanudarla.
- **Arreglado:** las instancias del Studio (Construir) apuntaban el bloque de antes de colocar en vez del colocado, porque se leía el mundo con lo escrito aún en el búfer de WorldEdit; ahora se lee al acabar cada ventana.
- Las operaciones sobre la selección ya no se cortan en 1,5 millones de cambios para quien no tiene límite (`komaedit.limit.unrestricted`), y su plan se monta en el hilo del cálculo, no en el del servidor. La lectura de la selección cuesta un tercio menos por bloque.
- Código: `performance.TickBudget`, `FairShare`, `JobQueue`, `QueueEstimate`, `RateMeter` y `ServerWork`; `edit.PlacementRun`, `PlanPages`, `PlanSpill` y `EditLimits`; `region.RegionReader` y `SelectionParts`; `command.JobCommands`. Explicado en `docs/ARCHITECTURE.md` («Presupuesto por tick, cola y memoria»). Pruebas con reloj de mentira: el presupuesto se respeta (también por debajo de 20 TPS), el reparto es justo y la pequeña no espera a la grande, la cancelación en cada fase, el plan comprimido y el volcado a disco dan exactamente las mismas entradas, el //undo cubre todo lo colocado (también cancelando a media ventana) y los avisos de límite. `./gradlew benchmark` (o `KOMAEDIT_FULL_TESTS=1`) mide las fases con 1, 5 y 20 millones de bloques; en cada build solo corre lo que no depende del reloj (tablas en `docs/MOD_DOCUMENTATION.md`, «Rendimiento»). Nada probado aún dentro de Minecraft.

## 1.9.0 (estable, 2026-10-21)

### Temas de color del Studio (el rojo ya solo es peligro)

- **Cuatro temas, Pizarra de serie.** El acento rosa sobre gris muy oscuro deja paso a **Pizarra** (grises azulados y acento azul lapislázuli). Se pueden elegir también **Prismarina** (azul de mar y turquesa), **Bosque** (musgo y verde salvia) y **Clásico** (claro, con la piedra y el texto del inventario y el morado de los encantamientos). Pizarra va de serie porque el azul no se confunde con ningún estado, se aleja de la madera, la tierra y la hierba del mundo de al lado y es el acento que se espera en un editor.
- **Se elige en el Studio y en los ajustes.** En la chuleta (`F1` o `?`) hay una fila **Tema** que lo cambia al momento; también desde `Ctrl+K` («tema») y en `/kegui ajustes` → **Tema del Studio**. Se recuerda por jugador en `config/komaedit/studio.json`.
- **Colores con reglas.** Todo el texto llega a WCAG AA (4,5:1) en los cuatro temas, también la fuente de píxel a GUI 2; las marcas de selección, a 3:1. El rojo queda para peligro (borrar, cancelar, errores), el ámbar para aviso y el verde o turquesa para éxito, lejos del acento. El punto del icono de vista previa en vivo pasa a verde y el holograma oculto se tacha en el color del icono, sin rojo.
- **Estados por la forma.** Lo elegido lleva su marca de acento (también las pastillas, con una barra debajo); lo que está bajo el ratón, un borde en botones y pastillas; un botón desactivado va hueco, solo con su contorno, en vez de parecer uno normal con el texto gris.
- **Miniaturas más legibles.** Las de especies, tejados, rocas, sellos, planos y variaciones van sobre un fondo propio, más claro y neutro que la tarjeta: el abeto ya no se pierde en los temas oscuros. La huella de **Pintar terreno** pasa de una mancha clara sobre un cuadro casi negro a una mancha de hierba que se transparenta, sobre ese mismo fondo.
- **Lo nuevo del Studio, con fichas.** Las tarjetas de variación (encendidas: tintadas, con barra de acento y el pulsador a la derecha), la leyenda del mundo, los extremos que salen al arrastrar un número, la galería de sellos y las muestras de Pintar terreno usan las fichas y pasan el contraste en los cuatro temas. Los colores de las guías del ratón en el mundo (las caras de la caja de selección) y del resto de lo que se dibuja en el mundo quedan como fichas fijas `WORLD_…`: no siguen al tema, porque su fondo es el mundo.
- De paso: las teclas de las etapas en la navegación iban en el gris de lo desactivado (no llegaban al contraste); ahora van en el de las notas.
- Código: los colores del Studio son **fichas** con nombre en `StudioTheme` (fondo, superficie, tarjeta, borde, foco, acento, acento suave, texto sobre el acento, texto, texto secundario, notas, desactivado, éxito, aviso, peligro, fondo de miniatura…), rellenas por el tema elegido (`StudioPalette`) con `StudioTheme.use`. `WHITE` desaparece: `TEXT_STRONG` sobre fondos y `ON_ACCENT` sobre el acento; `ACCENT_TEXT` es el acento como texto o icono. Ningún color se escribe a mano fuera de las fichas: lo vigila `StudioThemeTokensTest` (los colores del mundo de las miniaturas van en constantes con nombre). `StudioThemeContrastTest` mide cada pareja texto/fondo de cada tema, la forma de cada estado (también la tarjeta de variación encendida y apagada) y, además, cada texto del Studio pintado de verdad con cada tema en 31 pantallas a 1080p y a 854×480 (variaciones, sellos, Pintar terreno, la ayuda de un número, el arrastre y la leyenda del mundo incluidos); `StudioThemeSwitchTest` prueba el cambio desde la chuleta, la paleta y las preferencias. Maquetas de Herramientas, Árbol, Tejado, Planos, Variaciones, Sello y Pintar terreno con cada tema, cinco comparativas de los cuatro temas y la hoja de fichas en `docs/renders/studio/temas/`; las demás maquetas, regeneradas con Pizarra. Nada probado aún dentro de Minecraft.

### Biblioteca de sellos de relieve: la forma fácil de hacer terreno

- **80 sellos de serie en 14 categorías** (antes 20): montañas y picos, cordilleras, colinas suaves, mesetas y mesas, acantilados y escarpes, cañones y gargantas, cráteres y calderas, volcanes, dunas, islas y atolones, terrazas y bancales, glaciares y morrenas, rocas y peñascos, y detalle (arrugas, grietas, cárcavas, arroyos…). Generados por código con ruido por capas, crestas y valles y con la **erosión hidráulica y térmica del terraformado horneada**: tienen cauces, crestas y faldas de derrubios en vez de conos lisos, y las dunas su cara de sotavento a 33°. Cada uno baja suave a su gris neutro antes del borde. Los 20 nombres de antes siguen valiendo.
- **Van ya hechos dentro del jar** como PNG de 16 bits (unos 1,6 MB): se leen en milisegundos y el cliente y el servidor ven exactamente el mismo sello. Generarlos al arrancar costaría unos 12 s de CPU la primera vez y la erosión podría salir algo distinta en otra Java. Una prueba comprueba que los PNG son los de las recetas.
- **Cada clic, distinto:** de la semilla del pincel y el número de clic salen a la vez giro (±25°), escala (±20 %), espejo y un ruido fino que mueve las crestas, según la variación (`-x`, 60 % de serie). **Mayús+F** da una semilla nueva; `-s` la fija y `-k` la bloquea para repetir exacto. En el Studio, **Azar** y **Bloquear semilla**.
- **Modos de mezcla:** sumar, restar, **máximo** (no hunde lo que ya está más alto: las montañas que se pisan no se apilan), **aplanar a la altura del sello** (antes aplanaba hacia la altura del clic) y **suavizar** (funde bordes y costuras). `elevar`, `bajar` y `aplanar` siguen valiendo.
- **Materiales por categoría:** sin paleta (o con `auto`), la de la categoría del sello, y se puede cambiar. Paletas nuevas `alpino`, `costa`, `rocoso`, `isla`, `bancal`, `ceniza` y `glaciar`, y la cumbre relativa `cumbre@70%`: la nieve sale en el tercio de arriba de la montaña estampada, esté a la altura que esté, y nunca en un llano ni en un hoyo.
- **Altura natural:** sin escribir la altura, la del sello para ese radio (una colina sale baja, un pico alto).
- **Galería del Studio:** miniaturas en color con sombreado (nieve, arena, estratos, agua alrededor de las islas), una categoría a la vista, buscador en todas a la vez (Intro elige el primero) y favoritos con la estrella o el clic derecho, guardados en `studio.json`. El tooltip de cada sello es corto y sale debajo de la miniatura, y el pie dice el sello, el modo y la semilla en vez de repetir «Al pincel». Presets nuevos Cordillera y Cañón; Lomas en modo máximo.
- `/komaedit terreno sellos` los lista por categoría; `sellos <categoría>` los explica; `sellos exportar <sello>` (administración) deja una copia en `config/komaedit/heightmaps` para retocarla. Los de serie ya no se copian a esa carpeta (se crea con un `LEEME.txt`), y las plantillas que escribía la 1.8, si siguen sin tocar, ya no tapan a los sellos nuevos del mismo nombre.
- **API** para otras herramientas (el pincel de pintura con sellos): `StampLibrary.all()`, `thumbnail`, `get`, y `StampVariation`/`StampPlacement` para muestrear con giro, escala, espejo y semilla. Explicada en `docs/ARCHITECTURE.md`.
- Pruebas: cada sello carga del jar con el borde en su gris neutro, coincide con su receta y es determinista por semilla; ningún par se parece más de 0,97 (con giros cada 15° y espejo); los cinco modos; la variación por clic y la semilla bloqueada; la colocación (giro, escala, espejo, ruido fino); paletas por categoría y cumbre relativa; plantillas de la 1.8; galería con buscador y favoritos. Renders en `docs/renders/terreno/` (hoja de contacto y tres paisajes hechos solo estampando) y maquetas en `docs/renders/studio/`. Nada probado aún dentro de Minecraft.

### Lo que pidió el autor tras probar la 1.8.0: que el Studio se entienda y todo se haga con el ratón

- **Modificadores y Material, dentro de Figuras.** Solo valían para la figura y, como etapas aparte, obligaban a saltar entre tres pantallas y parecían afectar a todo. Figuras tiene ahora, de arriba abajo, **Tamaño**, **Material** (el bloque con su cuentagotas y un interruptor **Degradado**) y **Variaciones**, con lo poco usado en **Más ajustes**. Las etapas pasan a ser siete: `1` Herramientas, `2` Trayectoria, `3` Figuras, `4` Color, `5` Planos, `6` Recetas y `7` Instancias; quien las tenía guardadas abre en Figuras.
- **Variaciones con nombre de lo que hacen.** Lo que era «escala inicial y final, espaciado, torsión total, curva, fusión, conformado, kitbash» es ahora **Afinar**, **Retorcer**, **Otra figura encima**, **Pegar al terreno** y **Repetir lo copiado**: cada una es una fila con un dibujo hecho con el generador de verdad, una frase de lo que hace y un interruptor; se pone de un clic con un valor que se nota (40 % al final, una vuelta…) y sus controles salen debajo. Las estructuras solo ofrecen pegar al terreno. «Pegar al terreno» al suelo o al techo se ve ya en el holograma. Las recetas de antes se leen igual.
- **Cada control se explica solo.** Al pasar el ratón, todos los números de todas las etapas dicen qué le pasa a lo que construyes («Lo ancha que se abre la copa», «Cuántas ramas salen del tronco»), sin banderas ni nombres internos, y sus extremos en palabras («de estrecha a ancha»); sobre el número, la versión corta. Al arrastrar, la fila enseña los extremos a cada lado. Los gestos los dice el pie. Una prueba exige que cada ajuste nuevo traiga su explicación.
- **El pie dice qué hace cada clic.** Con el ratón sobre el mundo, la línea de debajo del pie dice qué hace un clic en esta etapa, y al dejarlo quieto sale arriba a la izquierda la leyenda entera. La chuleta (`F1`) lista los gestos del ratón.
- **Todo con el ratón, sin cerrar el Studio.** Seleccionar arrastrando una caja y ajustarla tirando de sus caras, con medidas (como la Box Select de Axiom), y doble clic para la selección mágica; poner, arrastrar y meter puntos de la trayectoria también desde Figuras; pintar con el pincel de la mano manteniendo el clic; **Alt+clic** en un bloque lo pone de material (figura, herramienta o degradado de Color). Antes la selección solo se hacía fuera del Studio con //pos1 y //pos2 y los pinceles solo con el Studio cerrado.
- **Remates del #65.** Los planos se colocan con un clic, como los árboles; el texto colocado con clic se orienta según la cara pulsada (pared, suelo o techo); el pincel de estampar sigue a la etapa Planos (otro plano u otro radio le llegan solos), y al rehacerse el fantasma en vivo el de antes sigue a la vista hasta que el nuevo está listo, sin el parpadeo de un fotograma.
- **Clics de las tareas típicas** (`TaskClicksTest`, antes → ahora): plantar un árbol, 4 → 4 (2 la siguiente vez); tejado sobre una casa seleccionada, 4 → 4, y sin selección previa ya no hay que cerrar el Studio (un arrastre); un río con una trayectoria, 7 → 7; estampar planos, 3 → 3, y ahora se estampa en el mundo con el Studio abierto; **hacer una figura y variarla, 9 clics y 2 arrastres → 8 clics**.
- De paso: textos que contestan a quien abre el Studio por primera vez (Figuras sin puntos dice cómo ponerlos; Recetas e Instancias dicen para qué son; «Cerrar anillo» y «Exportar» ya no se cortan), dos ayudas que solo repetían su etiqueta y las cifras de bloques de las instancias con separador. La tarjeta **Pintar terreno** también dice qué le pasa al terreno con cada número y sus extremos, y deja **Bloquear semilla** en Más ajustes (a la vista: paleta, huella, degradado, radio, fuerza y semilla).
- Código: `FigureVariation`, `VariationThumbnails`, `ControlHelp`, `SurfaceConform` (sin Minecraft, para el servidor, el cliente y las pruebas), `StudioView.worldAction`, `BoxSelect`, `PathDrag`, `BrushStroke` y `MeshHandover`; los pinceles y la colocación apuntan con `StudioAim`. Pruebas JUnit de todo ello (variaciones, ayudas de cada control, caja y caras, puntos, trazo, relevo de mallas, orientación del texto, pincel de estampar, cuentagotas, qué hace cada clic en cada etapa) y maquetas nuevas en `docs/renders/studio/`. Nada probado aún dentro de Minecraft.

### Pintar el terreno con un pincel

Lo pidió el autor pensando en los que empiezan: un pincel como el de los sellos de relieve, pero para pintar, con semilla como en Axiom y un degradado dentro de la pincelada; hasta ahora pintar bonito solo se podía sobre una selección.

- **Nuevo `/komaedit pincel paint` (alias `pintar`)**: recolorea la superficie con un degradado de la paleta sin cambiar alturas. Mantener el clic derecho y arrastrar pinta a lo largo del trazo, y **todo el trazo es un solo //undo**. Sin escribir nada más pinta hierba en lo llano y piedra en lo empinado.
- **Huella:** un círculo, un cuadrado o cualquier sello de relieve (de serie o un PNG propio) como máscara de intensidad, con la dureza del borde (`-u`).
- **Cuatro degradados:** por pendiente (de serie), por altura (la franja se mide sola en el terreno de alrededor al empezar el trazo, o con `-y`), por ruido (manchas con la semilla) y por la huella (del centro al borde). Mezcla tramada entre tonos vecinos y fronteras movidas con ruido (`-j`): sin bandas.
- **Pasadas que acumulan:** la fuerza es la de un trazo, como la opacidad de un pincel de dibujo; los toques que se solapan no se suman y cada pasada empuja un poco más hacia el tono, sin saltar del primero al último. Lo que no es de la paleta se cubre poco a poco y el borde se funde.
- **Semilla a la vista, como en Axiom:** F pone otra (🎲) y Mayús + F la bloquea. Libre, cada clic gira la huella y cambia un poco su tamaño; bloqueada, el mismo trazo pinta lo mismo.
- **Qué toca:** solo la superficie (`-d` capas, `-t` todo el volumen), también escaleras y losas con su forma (`-c` para no), solo terreno natural y la paleta para no repintar casas (`-x` cualquier bloque), `-b` una lista de bloques y `-m` una máscara de WorldEdit.
- **Paletas de serie del pincel:** `hierba_piedra`, `bosque`, `musgo`, `nevado`, `cumbres`, `costa`, `desierto`, `mesa` y `volcanico`; vale cualquier paleta de KomaEdit (`##Nombre`, `#guardada`, una lista).
- **Studio:** tarjeta **Pintar terreno** en Herramientas, junto al sello de relieve, con la muestra del degradado de cada paleta, la miniatura de cada huella, presets (Ladera, Cumbres, Manchas, Desierto), **Azar** y **Bloquear semilla**; lo demás, en Más ajustes. El pincel sigue los cambios de la tarjeta y, si la semilla está en automático, conserva la suya. Una tarjeta puede acortar sus galerías (`ToolCard.galleryRows`) para que sus ajustes quepan a la vista.
- **Rendimiento:** paleta, huella, máscaras y ruidos se preparan una vez por pincel; ningún clic lee del disco. Tandas de toques con foto repartida entre ticks, cálculo en un worker propio y escritura con presupuesto por tick. Un toque cuesta en el worker unos 0,4 ms con radio 8 y 2 ms con radio 16.
- Código: paquete `brush.paint` (lógica pura con pruebas: `PaintFootprint`, `FootprintLibrary`, `PaintPainter`, `PaintStroke`, `PaintConfig`; en el juego `PaintBrush`, `PaintStrokes` y `PaintOperation`). Pruebas JUnit de la huella y la máscara de intensidad, de cada degradado, de la acumulación por pasadas y de que un trazo no se suma, de la trama, de las máscaras y las capas, de escaleras y losas, del determinismo con la semilla bloqueada, de BrushSync con cada ajuste de la tarjeta y del tiempo de un trazo largo. Renders en `docs/renders/paint/` (`KOMAEDIT_RENDER_DIR=<carpeta> ./gradlew test --tests "*PaintRenderDump"`) y maquetas de la tarjeta en `docs/renders/studio/`. Nada probado aún dentro de Minecraft.

## 1.8.0 (estable, 2026-10-20)

### Lo que pidió el autor tras probar la 1.7.0: trabajar más deprisa

- **Presets que no rompen la copa.** Cada preset (Joven, Viejo, Azotado, Pelado; los de rocas, tejados, sello de relieve, texto y nieve) deja la herramienta en un estado completo y conocido: lo que no define vuelve al de serie de la especie o el tipo. Antes solo tocaban algunos valores y el resto se quedaba con lo del preset anterior: tras «Pelado» la densidad de copa seguía en 0 aunque se eligiera «Joven». Se conservan la especie o el tipo, los materiales, los textos, la semilla y los interruptores de cómo se coloca (vista previa en cada clic, reemplazar…). La pastilla del preset puesto sale marcada, y un preset del sello quita el sello propio escrito (si no, la orden seguía usándolo).
- **Vista previa en vivo, encendida de serie.** Construir enseña la vista previa (el fantasma del gizmo en árboles, rocas, texto…) y, mientras se ve, cada cambio de un ajuste la rehace sola a los 0,35 s (como mucho una vez cada 0,7 s y nunca mientras el servidor calcula la anterior). El fantasma nuevo sale donde dejaste el anterior, con su giro y sus opciones. En Figuras, el fantasma de lo construido sigue también a la figura (hasta 250 000 bloques). Antes estaba apagada de serie y, encendida, abría vistas previas por su cuenta. Quien tenía guardado «sin vista previa» de la 1.7.0 (lo de serie) pasa una vez a en vivo.
- **El pincel se entera de los cambios del Studio.** Lo que cambies en la tarjeta del pincel que hiciste (Al pincel de árbol, roca y bioma, y el sello de relieve) le llega al momento con la nueva `/komaedit pincel actualizar`, que solo cambia el pincel que ya está en la mano y avisa en la barra de acción. Antes había que volver a pulsar Al pincel: quitar «Vista previa en cada clic» no se lo quitaba.
- **Pinceles más ágiles con cualquier paleta.** Medido antes de tocar nada (`BrushPaletteBenchmarkTest`, radio 10): lo que dependía de la paleta crecía con su tamaño en las máscaras (1,4 ms por clic con `##Magma` frente a 0,3 con un bloque) y las paletas guardadas se leían del disco en cada uso. Ahora cada pincel recuerda su paleta resuelta, lo que pone cada entrada sobre cada estado y sus máscaras sencillas (se rehacen solo al reconfigurarlo, también desde el Studio); los ids del mundo no se copian para compararlos; las máscaras con muchos bloques los buscan en un conjunto, y las paletas guardadas no se releen mientras el archivo no cambie. Pintar con una máscara de `##Magma` baja de 1,38 a 0,35 ms, desplazar paleta de 0,36 a 0,19 ms y leer una paleta guardada anidada de 0,44 a 0,17 ms. Además, la foto del terreno de cada clic aprovecha todo el presupuesto del tick: un pincel de radio 12 empieza a calcular en un tick en vez de en dos o tres. El ruido (`-n`) y la luz de los bloques que alumbran siguen costando lo mismo: es el cálculo del ruido y la iluminación de Minecraft.
- **Colocar con el ratón.** Con el Studio abierto, un clic en el mundo pone ahí el árbol, la roca o el texto (rayo desde el cursor, no desde el centro de la pantalla) y el pie dice «Donde haces clic» y luego «Clic en x, y, z». Con un fantasma a la vista, un clic lo lleva a donde apunta el cursor y arrastrando se mueve por el terreno; el gizmo sigue sirviendo para afinar. Fuera del Studio, todo como antes.
- **Sin perder herramientas con el inventario lleno.** El pincel de Minecraft del esculpido (`/komaedit sculpt`) y del brush de figuras ya no se tira al suelo: va a la mano si está vacía o a un hueco, y si no cabe, un aviso dice que hagas sitio. Los pinceles de KomaEdit y la supervarita ya iban siempre en la mano.
- **Fuera la figura Mandala** (del Studio, `/komaedit mandala`, la ayuda, los permisos y la documentación). Una receta guardada con ella se salta con el aviso de siempre de receta ilegible y las demás se siguen cargando.
- **Fuera la etapa Envejecer del Studio.** Queda la orden `/komaedit weather` (`envejecer`), documentada como avanzada. Las teclas de las etapas pasan a ser del `1` al `9`: `7` Planos, `8` Recetas y `9` Instancias.
- De paso: en una herramienta de trayectoria o selección sin ninguna de las dos, el pie decía «Donde miras»; ahora dice «Sin selección».
- Código: `ToolCard.apply` parte de los valores de serie; `LivePreview` rehace solo la vista previa pedida (`arm`) y `GhostSession.follow` deja el fantasma nuevo donde estaba el anterior; `BrushSync` sigue al pincel de la mano por su id; `StudioAimPayload` y `StudioAim` llevan el clic al servidor (árbol, roca y texto lo usan con el Studio abierto); `ToolHandout` entrega herramientas sin tirarlas. Pruebas JUnit de cada arreglo (presets encadenados, preferencias, vista en vivo, pincel con cada opción de cada tarjeta comprobada con el analizador del servidor, clic en el mundo, entrega con el inventario lleno, receta con Mandala) y maquetas nuevas en `docs/renders/studio/`. Nada probado aún dentro de Minecraft.

## 1.7.0 (estable, 2026-10-20)

### Colocar con gizmo, como en Axiom

- **Lo que se coloca se mueve antes.** Con KomaEdit en el cliente, pegar (`/komaedit paste`, o **Ctrl+V** en el mundo), `place`, los planos, las figuras y estructuras de la trayectoria (también Construir del Studio), los árboles, las rocas, los tejados, el texto y las imágenes salen como **fantasma** translúcido con un **gizmo**: flechas por eje (rojo X, verde Y, azul Z), anillos de giro y un cubo central, del mismo tamaño en pantalla a cualquier distancia y resaltados al pasar el punto de mira. Lo que lee el terreno o la selección (terraformar, envejecer, texturizar, suavizar, hiedra, `scatter`…) sigue con su vista previa de siempre. Sin el mod nada cambia.
- **Mover:** arrastrando las flechas (de bloque en bloque) o el cubo (por el suelo) con el punto de mira, sin soltar el ratón ni cambiar de modo; con las flechas del teclado y RePág/AvPág (Mayús, de 5 en 5); con la rueda en la dirección de la mirada, y con **G**, a donde miras. En el Studio, con el cursor.
- **Girar y voltear:** Ctrl+R (90° en Y; Mayús al revés, Alt lo tumba), los anillos y Ctrl+F. Escaleras, troncos, puertas, raíles, `facing` y `axis` giran con él con la transformación de la simetría; tumbarlo solo se ofrece si todo el objeto lo admite.
- **Opciones a la vista:** apoyar en el suelo (Ctrl+B), conservar lo que hay (Ctrl+E) y pegar el aire (Ctrl+X), en la barra de acción, en el pie del Studio y en botones del chat.
- **Confirmar:** Intro o clic derecho coloca exactamente lo que se ve, en un solo //undo y con la barra de progreso; **Ctrl+clic** coloca una copia y sigue moviendo; Esc lo quita. El cliente solo manda la transformación: el servidor comprueba permisos, altura, borde, distancia, chunks cargados y dónde puedes construir, y lee el destino al confirmar con escrituras condicionales.
- **Ajustes:** **Colocar con gizmo** (Siempre, Con -v o Nunca) y **Rueda mueve el fantasma** en `/kegui ajustes`; las teclas, en Controles → KomaEdit (sin asignar de serie para no quitar la F ni la R a nadie; valen los atajos de serie).
- Nueva orden `/komaedit paste [-a] [-o]` (alias `pegar`), con el `//rotate` pendiente y los bloques con datos.
- Código: paquete `gizmo` (lógica pura con pruebas, adaptador de estados, red y `GhostService`) y `client.gizmo`; `PlanPreviewRenderer` dibuja también mallas con ancla y giro. Pruebas JUnit de la matemática del gizmo (rayo contra eje y plano, pasos de bloque, tamaño en pantalla, selección de piezas), de las 48 orientaciones y los estados (giros, volteos, combinaciones, idempotencia), de la colocación («lo que se coloca es lo que se veía», en las 48), de la validación del servidor, de la máquina de estados (mover, arrastrar, girar, confirmar, rechazar, cancelar, copias), de apoyar en el suelo, de las teclas y del pie del Studio. Renders fuera del juego en `docs/renders/gizmo/` y maquetas del pie en `docs/renders/studio/`. Nada probado aún dentro de Minecraft.

## 1.6.0 (estable, 2026-10-20)

### Procedural Studio: menos texto, más claro

- **Etapas en tres grupos:** Crear (`1` Herramientas, `2` Trayectoria, `3` Figuras), Ajustar (`4` Modificadores, `5` Material, `6` Color, `7` Envejecer) y Biblioteca (`8` Planos, `9` Recetas, `0` Instancias). **Cambian las teclas:** Herramientas pasa del `0` al `1`, y Estructuras deja de ser una etapa y es una familia de Figuras. La barra de arriba lleva el nombre de la etapa y los botones; ya no hay cabecera con subtítulo. Con la navegación en iconos, al pasar el ratón sale a su lado la lista con el nombre y la tecla de cada etapa.
- **El panel deja sitio al mundo:** de serie el 38 % del ancho (a 1080p con GUI 3 era la mitad), arrastrable desde su borde (con un asa) entre 230 píxeles y el 45 %; se guarda como parte de la pantalla y el clic derecho en el borde vuelve al de serie.
- **Ningún nombre cortado:** bajo las miniaturas, en las cuadrículas de herramientas y figuras y en los planos, el nombre va en dos líneas si no cabe, y la cuadrícula pone menos columnas si hace falta. Con un panel alto las galerías enseñan todas sus opciones.
- **Herramientas:** cuadrícula de iconos por grupos (Naturaleza, Tejados, Terreno, Utilidades y Modos) que se pliega al elegir una, dejando a la vista su nombre, sus recientes, la ★ y copiar la orden. El panel ya no enseña órdenes `/komaedit` ni descripciones largas: la ayuda va en tooltips cortos. Los ajustes esenciales salen a la vista y el resto en **Más ajustes** (plegado, con cuántos has cambiado); los presets, en una fila de pastillas.
- **Miniaturas** con el nombre debajo para lo que se elige viendo: especies de árbol, tipos de roca, tipos de tejado (sobre una casa de 9×7) y sellos de relieve, dibujadas por el propio generador en segundo plano. **Figuras** funciona igual: cuadrícula por familias que se pliega en la elegida.
- **Controles numéricos con unidad** (`18 bloques`, `50 %`, `45°`, `×1,5`, `Y 64`) y una barra con el rango, el valor y una marca en el de serie; un punto rosa dice que ya no es el de serie. Clic derecho lo devuelve, arrastrar lo desliza (recorre el rango en unos 240 píxeles; con Mayús, fino), un clic deja escribirlo (con la unidad o sin ella) y el tooltip dice rango, de serie y gestos.
- **Bloques:** cada fila de bloque lleva su icono, su nombre traducido y un **cuentagotas** que pone el que miras; sin bloque dice qué se usará («Como el tejado», «El de los muros»). Arreglo: «Caballete: De serie» salía con un cuadro rosa. La biblioteca de bloques busca también por el nombre traducido y enseña el que miras, la barra rápida y los recientes. Un bloque sin traducción (algunos de mods) sale con un nombre legible sacado de su id («Mossy Slate Tiles»), no con el id.
- **Pie:** dice sobre qué se trabaja (selección con su tamaño, puntos o donde miras), la estimación y el botón principal de cada etapa (Construir, Colocar plano, Sombrear selección, Envejecer selección; «Elige una herramienta» si no hay ninguna). Sus textos siempre caben: se quita lo menos importante, nunca a media palabra.
- **Modo compacto** con menos de 300 píxeles de alto (854×480 con GUI 2, 1080p con GUI 4): sin descripciones, todo más bajo, galerías en una fila con páginas, botones de opción como ciclo y presets dentro de Más ajustes. Elegir, ajustar y construir se ve sin desplazar en las quince herramientas de uso diario.
- **Paleta de órdenes** (`Ctrl+K`): etapas, herramientas, presets, figuras y planos en un buscador.
- **Historial del mundo** (el reloj de la barra): los últimos 12 cambios de tu sesión de WorldEdit con su nombre (el de la operación, «Trazo», «Pegar»…) y sus bloques; un clic deshace o rehace hasta ahí. El servidor lo manda en el estado del Studio (`EditHistory`).
- **Vista previa en tres modos** (el ojo del pie): sin vista previa, al construir, o **en vivo**, que se rehace sola al dejar de tocar un ajuste (0,7 s de espera, 1,5 s entre envíos, nada mientras otra obra está en marcha y solo hasta 64 000 bloques).
- **Se recuerda** en `studio.json`: la etapa y la herramienta, los ajustes de cada herramienta, el desplazamiento de cada etapa y herramienta y las secciones abiertas. Los archivos de antes se leen igual.
- **Planos:** el plano elegido va en una tarjeta de alto fijo encima de la cuadrícula (Estampar, Radio, Cargar, Compartir; dos filas si no caben) y se coloca con el botón del pie; Mostrar (Todos, Míos, Servidor) va junto al buscador y las etiquetas en una fila.
- Clics para cuatro tareas, contados por `TaskClicksTest`: plantar un abeto donde miras, 4 (2 las siguientes veces, porque abre en el árbol); tejado a cuatro aguas sobre la selección, 4 sin desplazar (cambiar el material por el que miras, +1); río por 4 puntos, 7; estampar un plano, 3 sin desplazar.
- Código: todo el Studio es común y sin Minecraft (`StudioView`, `StudioUi` sobre un `StudioCanvas`, `StudioLayout`); el juego lo pinta con `DrawContextCanvas` y las pruebas con `ImageCanvas` (fuente e iconos del juego), así que las maquetas son la pantalla de verdad. Pruebas JUnit nuevas: controles numéricos (rango, de serie, restablecer, arrastre), paleta, modo compacto, pie, vista previa en vivo, historial, recordar ajustes y clics de las tareas. Maquetas en `docs/renders/studio/` (1080p con GUI 3 y 4 y 854×480 con GUI 2, en español e inglés).

### En el mundo: vista previa, guías, mensajes y teclas

- **La vista previa la dibuja el cliente con KomaEdit**, desde un plan compacto troceado por red (`preview_plan`, unos 5 bytes por bloque, partes de 32 KiB repartidas entre ticks), en lugar de cientos de block displays: cada bloque que quedará, translúcido (50 % de serie) con un tinte leve verdoso o azulado; lo que se quita, cubierto de rojo; el contorno de la figura sin la rejilla de cada bloque (verde, azul claro y rojo) y la caja con sus medidas. Transparencia de una sola capa, a plena luz, sin z-fighting (polygon offset en lugar de tamaños de 1,004) y sin aparecer a trozos. Se aclara al acercarse y tiene un modo **solo contorno**. Solo llega la superficie, con sus caras al descubierto calculadas en el servidor, hasta 250 000 bloques. Los clientes sin el mod siguen viendo block displays (ahora sin parpadeo en lo que sustituye y hasta unos 160 bloques).
- **Selección de WorldEdit con medidas** (ancho×alto×largo en las aristas más cercanas, también la forma si no es un cuboide), y medidas en la caja de la vista previa, de la herramienta Caja del Studio y del holograma de las brochas. Apagado de serie si está WorldEditCUI.
- **Trayectorias numeradas**: el número de cada punto encima, el primero en verde y el último en naranja. Arreglado: las líneas de la curva, del gizmo y de la esfera de pintura llevaban mal la normal y casi desaparecían según el ángulo.
- **Radio del pincel** sobre la superficie que se mira, como en Axiom (el radio viaja en los datos del objeto).
- **Simetría sin partículas** con el mod: marcos con una rejilla tenue cada 4 bloques, eje y brazos con líneas. Sin el mod, las partículas pasan de una rejilla de 81 puntos por plano a un marco con cruz de 45.
- **Mensajes con un estilo común** (`feedback.Feedback`): prefijo corto `KE ·`, colores con sentido (ok, consulta, información, aviso, error), números con separador de miles y una línea por acción (la vista previa con sus botones y el detalle al pasar el ratón, el final de cada edición con lo conservado y la causa si falla, el Build grande en espera). Lo efímero («leyendo…», «calculando…», «en cola», «aplicando…») va a la barra de acción. Los errores de «desconocido» con propuesta llevan **[corregir]**, que escribe la orden corregida, y la falta de selección un **[//wand]**. `/komaedit quiet` silencia también lo de la barra de acción.
- **Teclas sin abrir el Studio**: Intro aplica y Retroceso descarta la vista previa que espera (solo entonces), **repetir la última operación** donde estás (`/komaedit repeat`, alias `repetir`; con Mayús o `-v`, con vista previa) y **cuentagotas** de bloque hasta 192 bloques (con Mayús copia el estado exacto). Las dos últimas, sin asignar de serie.
- **Sonidos sutiles y opcionales** al confirmar, al terminar una edición de tres segundos o más y al fallar.
- **Ajustes del cliente** (`/kegui ajustes` o su tecla): estilo y opacidad de la vista previa, atenuar al acercarse, selección, medidas, números, radio del pincel, simetría y sonidos, en `config/komaedit-client.json`.
- Pruebas JUnit de la superficie y sus caras, del plan por red (ida y vuelta, troceado, partes en cualquier orden, planes sustituidos, datos rotos), del contorno (cubo, lámina, esquina hacia dentro, frontera entre tipos, corte por secciones), del formato de los mensajes y los números, del modo silencioso, de los ajustes del cliente, de `repeat` y de la orden corregida. Renders de antes y después en `docs/renders/vista_previa/` (`KOMAEDIT_PREVIEW_RENDER`).

## 1.5.0 (estable, 2026-10-20)

### Vista previa, progreso y cancelación

- **Vista previa universal.** Cualquier orden que calcule un plan de bloques (figuras, tubos, `ezspline`, `bend`, `place`/`array`/`scatter`, `deform`, `surface`, `smooth`, `inflate`, `smoothblocks`, `texture` y sus modos, la vidriera, `image`, los generadores, `weather`, `ivy`, `tweak`, `palette swap`…) acepta `-v` o `--preview`: calcula, enseña el resultado solo a quien edita y espera a `/komaedit confirm` o `/komaedit cancel`, con botones en el chat. Donde ezEdits ya usa `-v` (`deform noise`/`voxelize`, `texture blend`/`blocklight`, `weather -v <vida>`, `shape polydome -v <patrón>`, `text -v` (de pie), `pincel gradient`, `palette print`) se respeta el suyo y la vista previa es `--preview`.
- Se ve con block displays enviados solo a tu cliente: lo nuevo con su bloque y contorno verde, lo que se quita en cristal rojo, y la caja. Hasta `previewMaxDisplays` (1 500); con más cambios, la superficie o una muestra repartida y la caja.
- Confirmar aplica el mismo plan, sin recalcular: los patrones (también los aleatorios) se resuelven al preparar la vista previa. Si la zona cambió entre medias, avisa dónde y recalcula la operación, que vuelve a enseñarse.
- La vista previa caduca a los `previewTimeoutSeconds` (90 s) y se descarta al salir, al cambiar de dimensión, al reaparecer y al reiniciar; los displays no existen en el mundo, y cualquier entidad con la etiqueta `komaedit_preview` que se cargue se borra.
- Añade `/komaedit preview always <bloques>` (alias `vistaprevia siempre`) para previsualizar siempre a partir de N bloques, y `/komaedit preview off`.
- **Progreso en una bossbar** para las operaciones largas: lectura de la selección, cálculo, preparación y espera de la vista previa, comprobación y colocación, con porcentaje, bloques por segundo y tiempo restante.
- Añade **`/komaedit cancel`** (alias `cancelar`): para la lectura, el cálculo y la colocación y descarta la vista previa o el Build en espera; lo ya colocado queda en una sola entrada de //undo.
- **Estimación antes de empezar:** las operaciones sobre la selección dicen cuántos bloques leerán y cuánto tardarán, las colocaciones largas avisan de su duración y cualquier plan que supere tu `//limit` de WorldEdit lo avisa antes de empezar.
- Código: `preview.PlanPreviews` (la puerta de `BatchEditService`), `PreviewFlags` (la bandera en el árbol de Brigadier), `EditRequests` (la petición de la orden a través de la captura y el worker), `PreviewFlow`, `ZoneCheck`, `DisplaySampler`, `edit.PlanApplication` (recorrido por tramos de los planes) y `progress.ProgressBars`; `AsyncEngine.progress(hecho, total)` permite a un cálculo informar de su porcentaje. Pruebas JUnit de la bandera, del plan aplicado idéntico, de los cambios en la zona, de caducidad y limpieza, de la cancelación a medias con //undo coherente y del límite de displays.

### Construir a mano

- Añade las **capacidades de construcción a mano**, como el modo Builder de Axiom: **Reemplazar** (clic derecho cambia el bloque apuntado por el de la mano y conserva sus propiedades; opción «reemplazar familia», que cambia el material y conserva la forma: planchas de abeto convierten una escalera de roble en escalera de abeto), **Retocar** (clic derecho con la mano vacía recorre la propiedad elegida; `F` cambia de propiedad y `Mayús` va hacia atrás), **Extruir** (con el mismo bloque en la mano, clic derecho copia hacia fuera la capa conectada y el izquierdo la encoge, por tipo o por estado y hasta N bloques), **Arrastrar** (estira un bloque o una fila entre dos clics con el eje de la cara bloqueado), **Colocar en el aire**, **Alcance largo** (atributo de Minecraft, también sin el mod en el cliente), **Colocación rápida** y **Sin actualizaciones** (arena que flota, agua quieta). Cada una se activa por separado y se recuerda por jugador y mundo.
- **Menú rápido** circular al mantener `Z`: las ocho capacidades con sus opciones (clic derecho y rueda), la velocidad de vuelo y el modo de juego. Cada capacidad tiene además su atajo en Controles, y `/komaedit capability` (alias `capacidad`) hace lo mismo desde el chat. Vista previa en el mundo de la capa que se extruye, del bloque en el aire y del arrastre, y las activas a la derecha de la barra rápida.
- Todo lo decide el servidor: permiso por capacidad (`komaedit.capability.<id>` en los paquetes básico e intermedio, `komaedit.admin.gamemode` y `komaedit.admin.anymode` en admin), creativo o `komaedit.admin.anymode`, distancia, que se pueda construir ahí (spawn, borde, aventura, mods de protección por `PlayerBlockBreakEvents.BEFORE`) y un freno por tick. Límites en `config/komaedit/capabilities.json`. Las acciones de más de un bloque son un solo `//undo`.
- Añade el evento `HandEditEvents.COLLECT` para que otros mods (la simetría) añadan bloques a una acción de las capacidades dentro de la misma operación.
- Añade pruebas JUnit del recorrido de propiedades, el reemplazo con propiedades y por familia, la capa de la extrusión, el arrastre (fila, eje y final), el reparto de clics, las reglas del servidor, el guardado y los textos de idioma.

### Simetría

- **Pinceles de modelado** (lo que faltaba de Axiom): `/komaedit pincel sculpt <modo>` (alias `modelar`, o el modo directamente: `/komaedit pincel soldar 8`) y los mismos tipos en `/komaedit sculpt set`.
  - **Esculpir** (`draw`): empuja o hunde la superficie a lo largo de su normal media, calculada con los vecinos, con altura, caída, suavizado (`-s`) y clic izquierdo para invertir. Funciona en suelos, paredes y techos.
  - **Roca** (`rock`): un poliedro de caras al azar con ruido fractal que asoma de la superficie y se funde con ella con una unión suave (`-k`); clic izquierdo, la talla.
  - **Soldar y fundir** (`weld`, `melt`): desenfoque gaussiano del volumen con umbral; soldar solo añade (une piezas sueltas, rellena rincones) y fundir solo quita (redondea aristas).
  - **Rugosidad** (`roughen`): más caras a la vista con casi el mismo volumen.
  - **Grietas** (`shatter`): aristas de Voronoi en 3D o en columnas a lo largo de un eje (`-e`), solo sobre bloques existentes, con ancho, tamaño de celda, profundidad y relleno de aire o de una paleta (`-b`).
  - Lo añadido toma el material de alrededor (la hierba enterrada pasa a tierra) o el de una paleta (`-b`); al hundir, el terreno conserva su piel. Máscara `-m`, vista previa `-p` y una sola entrada de //undo por clic, con el motor común. Permiso `komaedit.build.brush.sculpt` (paquete intermedio); ayuda en `/komaedit help sculpt`.
- **Escalar y girar el portapapeles sin dientes de sierra:** `/komaedit clipboard scale <factor> [nearest|scale2x|scale3x|rotsprite]`, `rotate <grados> [rotsprite|nearest]` y `restore` (alias `portapapeles escalar|girar|restaurar`). Scale2x y Scale3x llevados a 3D (en una capa dan exactamente los 2D) y RotSprite para cualquier factor o ángulo; escaleras, troncos, vallas, raíles y carteles giran con la estructura y los bloques con datos conservan los suyos.
- **Copias en `place`:** `-copias <n>` con `-desplazar x,y,z`, `-revolver <grados>` alrededor de `-pivote x,y,z` y `-torcer <grados>`, como «Modify copies» de Axiom: escaleras de caracol, coronas de columnas o hélices en una sola orden (también en el pincel de colocación).
- Pruebas JUnit: normales en planos, paredes y esferas, distancia con signo exacta, esculpir hacia fuera y hacia dentro, soldar con umbral bajo (más masa) y alto (menos), roca unida al suelo, rugosidad que conserva el volumen, grietas solo sobre bloques existentes y en columnas, máscara respetada y determinismo de los seis modos; Scale2x y Scale3x contra los algoritmos 2D clásicos, volumen proporcional, cuartos de vuelta exactos, muros cerrados y conectados con RotSprite, giro de estados y anclas y giros de las copias. Renders en `docs/renders/modelado/` y `docs/renders/portapapeles/` (`KOMAEDIT_SCULPT_RENDER`, `KOMAEDIT_CLIPBOARD_RENDER`).
- **Biblioteca de planos**, como los Blueprints de Axiom: `/komaedit blueprint` (alias `planos`, `plano`) con `save <nombre> [etiquetas]`, `list`/`search` por nombre y `#etiqueta`, `info`, `load` (al portapapeles), `place` (con la colocación de KomaEdit: alineación, giros, `-w` y la vista previa `-v`), `tags`, `share` y `delete`. Guarda el portapapeles de WorldEdit (con su `//rotate` aplicado) como esquema `sponge.3` más un JSON con autor, fechas, tamaño, bloques y etiquetas, y una **miniatura isométrica** de 128×128 pintada en el servidor con los colores medios de bloque del mod. Planos por jugador y compartidos del servidor (`servidor/<nombre>`) en `config/komaedit/blueprints/`.
- Los planos son una estructura más de `place`, `scatter`, `array` y sus pinceles: **`Blueprint(N:…,T:…)`** (`Bp`), con nombres, etiquetas y pesos (`Bp(N:roca_1:3|roca_2,T:arbustos)`) y origen de serie en el centro de la base.
- **Pincel `stamp`** (`estampar`): coloca al azar planos de una lista o de una etiqueta en el bloque que miras o repartidos en un radio, con probabilidad (`-n`), separación mínima también entre clics (`-d`), giro (`-r`) y volteo (`-f`) al azar, ajuste al suelo (`-g apoyar|centro|no`, `-y`), máscara de suelo (`-m`) y vista previa (`-p`). Una pasada, una entrada de `//undo`.
- **Etapa Planos del Studio** (tecla `0`): cuadrícula de miniaturas, buscador por nombre y etiqueta, filtro de propios o del servidor, pastillas de etiquetas y botones Cargar, Colocar, Estampar (también una etiqueta entera) y Compartir. Las miniaturas viajan en trozos de 4 KiB con un presupuesto por tick y el cliente las guarda por su huella. Las etapas encogen hasta 12 píxeles para que las diez quepan a 854×480.
- Permisos nuevos: `komaedit.blueprint.load`, `.save`, `komaedit.build.blueprint` y `komaedit.build.brush.stamp` (intermedio), `komaedit.blueprint.share` (avanzado), `komaedit.admin.blueprints` y `komaedit.limit.blueprints` (admin). Límites en `config/komaedit/blueprints.json` (planos por jugador, compartidos, tamaño, archivo y si se puede compartir). Ayuda: `/komaedit help blueprint`.
- Pruebas JUnit: ida y vuelta de guardado y carga con los metadatos, compartir y borrar, búsqueda por etiqueta, límites, separación y aleatoriedad determinista del estampado (con apoyo en ladera y matrices de giro y volteo), troceado de miniaturas por red (desorden, mezcla, huella, cola con presupuesto y códecs) y la miniatura. Maqueta de la etapa en `docs/renders/planos/` (`KOMAEDIT_BLUEPRINT_RENDER`).

- **Herramientas en el Procedural Studio.** Nueva etapa **Herramientas** (tecla `0`) con las herramientas que antes solo tenían orden, como tarjetas con buscador, familias, favoritas y recientes: árbol y roca (especie o tipo, altura y forma, materiales, semilla, presets rápidos y los guardados, al pincel y guardar preset), tejado y loft, sello de relieve con galería de miniaturas, rampa, río o cañón, erosión y pintar superficie, texto con selector de fuente (también las propias del servidor), materiales con la lista en la tarjeta y botón para copiarla, gravedad, nieve, drenar, inundar, bioma con buscador entre los del servidor, simetría (modo, brazos, centro, encendido y reflejar WorldEdit, siguiendo el estado del servidor) y capacidades de construcción rápida (interruptores y números, como el menú de la `Z`).
  - Construir manda exactamente la orden que se escribiría (se ve y se copia en la tarjeta): el servidor la analiza y decide con sus permisos. Interruptor **Vista previa (-v)** para pedir la vista previa universal desde el Studio.
  - Mismos controles que el resto del Studio (filas numéricas con rueda, arrastre, escribir y clic derecho; ciclos, botones, interruptores, biblioteca de bloques) con ayuda, rango y valor de serie; valores **auto** que no mandan bandera; deshacer y rehacer de los ajustes de cada tarjeta (`Z`/`Y`).
  - El Studio pregunta al servidor qué permisos, selección, fuentes, presets y simetría tiene el jugador: una herramienta sin permiso sale apagada con un candado y el nodo que falta; si falta la selección o los puntos, Construir lo dice. Antes de mandar se comprueban texto vacío, espacios y caracteres que el chat no admite (`§`).
- **Progreso en el pie del Studio**, en todas las etapas: fase, porcentaje, ritmo y tiempo restante de lo que tengas en marcha, con una barra fina, **Confirmar**/**Descartar** cuando una vista previa espera (`Intro` / `Ctrl+Retroceso`) y **Cancelar** mientras se lee, calcula o coloca. Paquetes nuevos solo para clientes con KomaEdit: estado de herramientas, progreso y lista de materiales.
- Las etapas del Studio encogen su fila hasta 12 px y el hueco entre ellas a 1 px cuando no caben: las diez caben en 854×480 con GUI 2.
- Arreglo: el `-v` de `text` (de pie) y de `biome` (relleno vertical) ya no lo toma la vista previa universal; en esas dos órdenes la vista previa es `--preview`.
- Pruebas JUnit del modelo de cada tarjeta (cada orden se lee con el analizador del propio servidor: árbol, roca, tejado, loft, texto y simetría), de permisos, requisitos, validación, buscador, preferencias y textos en `es_es` y `en_us`, y de la etapa entera sin Minecraft (`ToolsPanel`, con pulsaciones y teclado). Maquetas PNG con la fuente del juego en `docs/renders/studio/` (`KOMAEDIT_STUDIO_MOCKUPS`).
- **Árboles y rocas procedurales.** `/komaedit tree` (alias `arbol`) planta un árbol donde miras o en cada punto P (hasta 16, cada uno distinto) con doce especies: roble, abeto, abedul, jungla, acacia, cerezo, sauce llorón, roble oscuro, árbol muerto, palmera, baobab y uno fantástico curvo y retorcido. Tronco, ramas y raíces salen del espacio de tubo (radio que se estrecha, inclinación y curvatura con ruido); las frondosas se ramifican por colonización del espacio con grosores del modelo de tuberías, el abeto por verticilos, la acacia en guías con copas planas, la palmera con frondas en arco y el baobab en botella. Copas en bultos con ruido, más densas por fuera, raíces que siguen el terreno y se hunden, troncos con el `axis` de su rama y hojas siempre con `persistent=true` y su `distance`. Se ajustan altura, grosor (`-g`), ramas (`-b`), densidad (`-d`), ancho (`-w`), inclinación (`-i`), curvatura (`-c`), semilla (`-s`), materiales de tronco, hojas y raíces (`-t`, `-h`, `-r`, con bloques o paletas `##`) y máscara (`-m`).
- `/komaedit rock` (alias `roca`): rocas, peñascos y agujas con caras talladas, ruido distorsionado, grietas de Voronoi y estratos, hundidas en el terreno y con la base siguiendo la ladera; sombreadas con una paleta de oscuro a claro según la altura, la orientación y lo encajonado de cada bloque, y con musgo opcional por arriba (`-u`).
- Presets guardables por jugador y mundo (`tree save|list|delete`, `rock save|list|delete`) que se usan con `#nombre`, y pinceles `/komaedit pincel tree` y `pincel rock` con una variante nueva en cada clic (`-x`).
- Las dos órdenes calculan en un worker, se colocan en una sola entrada de //undo, respetan `//gmask` y `-m`, y llevan la vista previa universal (`-v`), la barra de progreso y `/komaedit cancel`. Permisos `komaedit.build.tree`, `.rock`, `.brush.tree` y `.brush.rock` en el paquete intermedio; ayuda en `/komaedit help nature`.
- Pruebas JUnit: nada flotando (madera unida al suelo y hojas a 6 pasos o menos de la madera, en llano, colina y ladera y con muchas semillas y ajustes), orientación de los troncos, `persistent=true`, determinismo por semilla, rocas de una pieza y hundidas, y ejemplos de la documentación. Renders de comprobación en `docs/renders/arboles/` (`KOMAEDIT_NATURE_RENDER`).
- Añade **`/komaedit roof <tipo> [material] [banderas]`** (alias `tejado`): tejado completo sobre la planta de la selección o, con `-d`, sobre la que dibujan los muros (también en L, T, U o con patio). Tipos: a dos aguas (`gable`), a cuatro aguas (`hip`), mansarda, a un agua (`shed`), holandés (`gablet`), cúpula, cono, aguja poligonal (`spire`), pagoda con las puntas del alero levantadas y plano con pretil (`flat`).
  - Pendiente como subida:avance (`1:1`, `1:2`, `2:1`) o en grados, alero, grosor, caballete de otro material (`-c`), buhardillas con ventana (`-b`), hastiales cerrados con el material del muro de debajo (o `-m`), escaleras y losas de otro material y cualquier paleta `##` en degradado por altura. `-r` para pisar bloques; sin ella el tejado solo va donde hay aire.
  - Escaleras mirando al caballete, escaleras colgadas (`half=top`) bajo el alero para que no salgan muescas, esquinas interiores y exteriores en limahoyas y limatesas (orientadas para que Minecraft les dé la forma justa) y losas arriba o abajo según la altura. Cúpula, cono y agujas, por anillos de escaleras sin rendijas.
- Añade **`/komaedit loft [curvas…] [material] [banderas]`** (alias `lona`): superficie entre 2 y 6 curvas (recorridos guardados, `actual` o los puntos marcados repartidos con `-n`) para toldos, velas, lonas, puentes colgantes y paredes curvas. Interpolación lineal, Catmull–Rom o catenaria con `-comba`, grosor, `-cerrar`, `-w` de smoothblocks y degradado de paleta de la primera curva a la última. Se voxeliza al revés y es estanca por construcción.
- Los dos calculan en un worker, van en una sola edición de `//undo` y usan la estimación (`-e`), la confirmación de Build grandes y la vista previa (`-v`) del motor común. Ayuda: `/komaedit help roof`. Permisos nuevos: `komaedit.build.roof` y `komaedit.build.loft` (paquete intermedio).
- Añade pruebas JUnit de los tejados (orientación de escaleras y escaleras colgadas, esquinas en limatesas y limahoyas, losas por altura, simetría, plantas en L, T y U sin huecos con el desván cerrado, buhardillas, hastiales, pretil, caballete, aire respetado, detección de muros y determinismo) y de los lofts (estanqueidad en los tres modos, con grosor y con `-w`, comba, orientación de curvas, reparto de puntos y determinismo), y renders de comprobación con `KOMAEDIT_ROOF_RENDER`, guardados en `docs/renders/tejados/`.
- Añade el **modo simetría**, `/komaedit symmetry` (alias `simetria`, `sym`), como `//ezsymmetry` de ezEdits pero con diseño propio: espejos en X, Z e Y en cualquier combinación (`x`, `xz`, `xzy`…) o radial de 2 a 16 brazos alrededor de un eje vertical (con espejo en Y si se quiere). El centro cae en un bloque o entre bloques en cada eje y se fija en tu posición, en un punto (`center <x> <y> <z> [half [ejes]]`) o en el centro de la selección de WorldEdit. Subórdenes `on`, `off`, `toggle`, `mode`, `center`, `arms`, `worldedit` e `info` con botones, ayuda clicable (`/komaedit help symmetry`) y textos en `es_es` y `en_us`.
- Lo que pones y rompes a mano se repite al momento en las copias, con el estado girado o reflejado (escaleras con su esquina, troncos, puertas con su bisagra y sus dos mitades, camas, cofres dobles, raíles, vallas y muros conectados, carteles y estandartes con su `rotation` de 16 pasos, losas, botones y faroles al revés con el espejo en Y). Las copias nunca pisan bloques, se ajustan a sus vecinos, no se ponen si no se sostienen, solo se rompen si en su sitio está el mismo bloque y no salen del mundo, del borde, de chunks sin cargar, de la protección del spawn ni de zonas que un mod de protección prohíba (se le pregunta con el evento de rotura de Fabric).
- Con `/komaedit symmetry worldedit on` también se reflejan tus ediciones de WorldEdit y KomaEdit (`//set`, `//paste`, Build, pinceles…), dentro de la misma edición: un solo `//undo`, con la máscara global y el límite de bloques de WorldEdit, y como mucho `maxBlocksPerOperation` copias por edición.
- Mientras está activa, partículas que solo ve el jugador marcan los planos (rojo X, azul Z, verde Y), el eje y los brazos del radial.
- Permisos nuevos: `komaedit.build.symmetry.reflectional` y `.rotational` (paquete intermedio) y `.worldedit` (avanzado).
- Código: la geometría (`SymmetryCenter` en medios bloques, `SymmetryGeometry`, `StateTransform`, `SymmetryGuides`) es Java puro con pruebas JUnit de espejos, radiales, centros entre bloques, estados girados e idempotencia. La colocación a mano llega por un mixin en `BlockItem.place` (primer mixin común del mod, en `komaedit.mixins.json`) y las ediciones de WorldEdit, por el `EditSessionEvent` en la etapa `BEFORE_HISTORY`.

### Tejados y lofts

- Añade **`/komaedit roof <tipo> [material] [banderas]`** (alias `tejado`): tejado completo sobre la planta de la selección o, con `-d`, sobre la que dibujan los muros (también en L, T, U o con patio). Tipos: a dos aguas (`gable`), a cuatro aguas (`hip`), mansarda, a un agua (`shed`), holandés (`gablet`), cúpula, cono, aguja poligonal (`spire`), pagoda con las puntas del alero levantadas y plano con pretil (`flat`).
  - Pendiente como subida:avance (`1:1`, `1:2`, `2:1`) o en grados, alero, grosor, caballete de otro material (`-c`), buhardillas con ventana (`-b`), hastiales cerrados con el material del muro de debajo (o `-m`), escaleras y losas de otro material y cualquier paleta `##` en degradado por altura. `-r` para pisar bloques; sin ella el tejado solo va donde hay aire.
  - Escaleras mirando al caballete, escaleras colgadas (`half=top`) bajo el alero para que no salgan muescas, esquinas interiores y exteriores en limahoyas y limatesas (orientadas para que Minecraft les dé la forma justa) y losas arriba o abajo según la altura. Cúpula, cono y agujas, por anillos de escaleras sin rendijas.
- Añade **`/komaedit loft [curvas…] [material] [banderas]`** (alias `lona`): superficie entre 2 y 6 curvas (recorridos guardados, `actual` o los puntos marcados repartidos con `-n`) para toldos, velas, lonas, puentes colgantes y paredes curvas. Interpolación lineal, Catmull–Rom o catenaria con `-comba`, grosor, `-cerrar`, `-w` de smoothblocks y degradado de paleta de la primera curva a la última. Se voxeliza al revés y es estanca por construcción.
- Los dos calculan en un worker, van en una sola edición de `//undo` y usan la estimación (`-e`), la confirmación de Build grandes y la vista previa (`-v`) del motor común. Ayuda: `/komaedit help roof`. Permisos nuevos: `komaedit.build.roof` y `komaedit.build.loft` (paquete intermedio).
- Añade pruebas JUnit de los tejados (orientación de escaleras y escaleras colgadas, esquinas en limatesas y limahoyas, losas por altura, simetría, plantas en L, T y U sin huecos con el desván cerrado, buhardillas, hastiales, pretil, caballete, aire respetado, detección de muros y determinismo) y de los lofts (estanqueidad en los tres modos, con grosor y con `-w`, comba, orientación de curvas, reparto de puntos y determinismo), y renders de comprobación con `KOMAEDIT_ROOF_RENDER`, guardados en `docs/renders/tejados/`.

### Árboles y rocas

- **Árboles y rocas procedurales.** `/komaedit tree` (alias `arbol`) planta un árbol donde miras o en cada punto P (hasta 16, cada uno distinto) con doce especies: roble, abeto, abedul, jungla, acacia, cerezo, sauce llorón, roble oscuro, árbol muerto, palmera, baobab y uno fantástico curvo y retorcido. Tronco, ramas y raíces salen del espacio de tubo (radio que se estrecha, inclinación y curvatura con ruido); las frondosas se ramifican por colonización del espacio con grosores del modelo de tuberías, el abeto por verticilos, la acacia en guías con copas planas, la palmera con frondas en arco y el baobab en botella. Copas en bultos con ruido, más densas por fuera, raíces que siguen el terreno y se hunden, troncos con el `axis` de su rama y hojas siempre con `persistent=true` y su `distance`. Se ajustan altura, grosor (`-g`), ramas (`-b`), densidad (`-d`), ancho (`-w`), inclinación (`-i`), curvatura (`-c`), semilla (`-s`), materiales de tronco, hojas y raíces (`-t`, `-h`, `-r`, con bloques o paletas `##`) y máscara (`-m`).
- `/komaedit rock` (alias `roca`): rocas, peñascos y agujas con caras talladas, ruido distorsionado, grietas de Voronoi y estratos, hundidas en el terreno y con la base siguiendo la ladera; sombreadas con una paleta de oscuro a claro según la altura, la orientación y lo encajonado de cada bloque, y con musgo opcional por arriba (`-u`).
- Presets guardables por jugador y mundo (`tree save|list|delete`, `rock save|list|delete`) que se usan con `#nombre`, y pinceles `/komaedit pincel tree` y `pincel rock` con una variante nueva en cada clic (`-x`).
- Las dos órdenes calculan en un worker, se colocan en una sola entrada de //undo, respetan `//gmask` y `-m`, y llevan la vista previa universal (`-v`), la barra de progreso y `/komaedit cancel`. Permisos `komaedit.build.tree`, `.rock`, `.brush.tree` y `.brush.rock` en el paquete intermedio; ayuda en `/komaedit help nature`.
- Pruebas JUnit: nada flotando (madera unida al suelo y hojas a 6 pasos o menos de la madera, en llano, colina y ladera y con muchas semillas y ajustes), orientación de los troncos, `persistent=true`, determinismo por semilla, rocas de una pieza y hundidas, y ejemplos de la documentación. Renders de comprobación en `docs/renders/arboles/` (`KOMAEDIT_NATURE_RENDER`).

### Terraformado

- **Terraformado.** Herramientas de paisaje sobre el mapa de alturas, como las de goBrush, Axiom y Arceon, con una paleta de superficie por pendiente y altura (hierba en lo llano, piedra en las pendientes, nieve en las cumbres, arena en la orilla, grava en el lecho): preajustes `natural`, `pradera`, `bosque`, `desierto`, `mesa` (estratos de terracota), `nevado`, `volcanico`, `playa`, `piedra` y `conservar`, y cambios `clave:bloques@valor`. Todo en un worker, con la estimación del motor común, la confirmación de Build grandes, máscaras `-m` y un solo //undo.
- Añade el **pincel de relieve** `/komaedit pincel heightmap` (alias `relieve`, `sello`): estampa un sello en escala de grises que eleva, baja o aplana, escalado al radio, girado con la mirada, al azar o a unos grados y con intensidad; el gris neutro deja hundir (cráteres, dolinas). Trae 20 sellos de serie generados por código (montaña, pico, colina, loma, cordillera, macizo, volcán, cráter, dolina, valle, duna, dunas, mesa, terrazas, acantilado, barrancos, aguja, domo, atolón y circo) y lee cualquier PNG de `config/komaedit/heightmaps/`, donde escribe los de serie como plantilla.
- Añade `/komaedit terreno rampa [plano|cono]`: rampa del primer al último punto que rellena y recorta, con transición suave o taludes de terraplén, y que nunca deja escalones de más de un bloque (las demasiado empinadas se rechazan).
- Añade `/komaedit terreno rio|arroyo|canon|barranco` por la trayectoria: sección con fondo, orillas en curva y laderas que se funden con el terreno, fondo con su material y agua, lava o seco a su nivel. El fondo y el agua bajan siempre en el sentido del agua y se levantan ribazos para que no se escape.
- Añade `/komaedit terreno erosion hidraulica|termica|completa`: erosión física de la selección por gotas (cauces, sedimento y abanicos) y por talud (canchales), con iteraciones, fuerza, ángulo y semilla; conserva la masa y es determinista. El pincel `erosion` de `sculpt` sigue igual.
- Añade `/komaedit terreno pintar`, que repinta la superficie de la selección con la paleta sin cambiar alturas, y `/komaedit terreno sellos|paletas`.
- Añade pruebas JUnit del terraformado (masa de la erosión, río que no sube, rampa sin escalones, escala y giro de los sellos, determinismo, paleta, PNG y escritura de columnas) y renders de comprobación con `KOMAEDIT_RENDER_DIR`; los de antes y después están en `docs/renders/terreno/`.

### Color y sombreado

- Añade **`/komaedit color`** (alias `colour`) para elegir bloques por su color, como el Colour Picker y el Gradient Helper de Axiom. Todos los bloques tienen color en OKLab: la media de su textura en los vanilla y el color de mapa en los de mods.
  - `color similar [bloque|#hex] [n]` (alias `parecidos`): los n bloques de color más parecido, cada uno con su muestra de color en el chat y botones para llevarlo a la mano, buscar a partir de él o hacer un degradado. Sin argumento usa el bloque de la mano o el que miras.
  - `color gradient <A> <B> [pasos]` (alias `degradado`): degradado en OKLab que no repite bloques ni da marcha atrás en luminosidad; `[otra]` o `-v n` dan alternativas, `-p nombre` lo guarda como paleta y `[a la barra]` lo pone en la barra rápida.
  - `color palette <selection|hotbar|paleta> [nombre]`: los materiales de una selección de cualquier forma, de la barra rápida o de una paleta, de oscuro a claro.
  - `color hotbar <bloques|undo>`: lleva bloques a la barra rápida (solo en creativo) y la recupera.
  - Filtros `-c` cubo entero, `-s` sólido, `-o` opaco y `-u` misma textura en todas las caras. Por defecto no se proponen menas, terracota esmaltada, cincelados, coral vivo, máquinas, bloques de función, texturas con mucho dibujo, bloques que dan luz ni bloques con entidad de bloque; `-x` los deja entrar.
- Añade **`/komaedit shade`** (alias `sombrear`), como el Autoshade de Axiom: recolorea la superficie de la selección por la luz que recibe (Lambert con sombras proyectadas y oclusión ambiental), desde el sol según la hora del mundo, el jugador, la mirada, un foco, arriba, un rumbo o un vector. La paleta sale de la selección (los materiales de más del 2 % de la superficie, de oscuro a claro; los detalles sueltos se quedan), de una rampa del material principal (`rampa`, que conserva el material como el más visto) o de cualquier paleta. Tramado opcional (`-d`), máscara (`-m`) y fuerzas de oclusión y sombras. Escaleras, losas, muros y demás piezas conservan su forma y pasan a la pieza del material nuevo; si no la hay, se quedan.
- Añade la etapa **Color** al Procedural Studio (tecla `9`): degradado entre dos bloques elegidos en el catálogo, con sus muestras, **A la barra**, **Otra** y **Al chat**; los parecidos al primero; y sombrear la selección eligiendo luz, paleta y tramado.
- La tabla de colores de texturas (`assets/komaedit/smooth/colors.txt`) lleva una columna de banderas: `u` (la misma textura en las seis caras) y `p` (no es un material liso). Smoothblocks y `/komaedit color` leen la misma tabla y los colores no cambian.
- Permisos nuevos: `komaedit.color.similar`, `.gradient`, `.palette` y `.hotbar` (paquete básico; los da `komaedit.use`) y `komaedit.build.shade` (paquete intermedio).
- Añade pruebas JUnit (OKLab y CIELAB con valores de referencia, filtros, degradados que no repiten y suben en luminosidad, alternativas, rampas, luz según su dirección, sombras, oclusión, paleta de la selección y familias de piezas) y renders de comprobación con `KOMAEDIT_COLOR_RENDER` (y `KOMAEDIT_MC_JAR` para las texturas); los de degradados y del antes y después de una colina y un busto están en `docs/renders/color/`.

### Imagen en bloques

- Añade **`/komaedit image`** (alias `imagen` y `stainedglassimage`, como `//stainedglassimage` de ezEdits): convierte una imagen PNG, JPG o GIF en bloques, en vertical u horizontal sobre el plano que miras o ajustada a una selección plana, con ancho o alto en bloques (la otra medida respeta la proporción) y anclaje. Paletas de vidrio tintado (por defecto), paneles (unidos a lo largo de cada fila), lana, hormigón, terracota, todos los bloques enteros o cualquier paleta `##Nombre`; el color se compara en CIELAB con la media de la textura vanilla de cada bloque. Tramado Floyd–Steinberg (por defecto), ordenado (Bayer de 8×8 con mezcla de Knoll) o ninguno; brillo, contraste y transparencia (los huecos quedan en aire, se dejan como están o se rellenan con un bloque). Se calcula en un worker y se coloca por ticks como una sola edición de `//undo`.
- Las imágenes salen de `config/komaedit/images/` o, si el admin lo activa en el nuevo `config/komaedit/images.json`, de URL `https` de una lista de dominios, con límites de tamaño, de píxeles y de tiempo, sin seguir redirecciones a otros hosts, sin direcciones internas y descargadas fuera del hilo del servidor. Permisos nuevos: `komaedit.build.image` (paquete avanzado) y `komaedit.admin.image.url` (URL solo para admins, activado por defecto).
- Añade pruebas JUnit de la cuantización, el tramado, las proporciones, la colocación, la transparencia, el decodificador (cabeceras que declaran millones de píxeles) y los límites de la descarga contra un servidor local, y renders de comprobación con `KOMAEDIT_IMAGE_RENDER`, guardados en `docs/renders/imagen/`.

### Utilidades de obra

- Añade **utilidades de obra** de uso diario, todas con lectura repartida entre ticks, cálculo en un worker, escritura condicional, máscara `-m` y una sola entrada de `//undo` (ayuda: `/komaedit help utilidades`):
  - **`/komaedit text "<texto>" [material]`** (alias `texto`): texto en bloques con fuentes TrueType. Trae tres de serie con licencia SIL Open Font License 1.1 (`sans` = Lato Bold, `fina` = Lato Regular, `mono` = JetBrains Mono Bold; sus licencias van en el jar) y usa cualquier `.ttf`/`.otf` de `config/komaedit/fonts/`. Tamaño en bloques (`-s`, altura de una mayúscula), de pie o tumbado según la mirada (`-v`/`-h`), grosor (`-d`), borde con material propio (`-b`, `-o`), losas y escaleras (`-w`), paleta en degradado vertical u horizontal (`-g v|h`, `##Magma`…), varias líneas (`\n`), espaciado, interlineado y alineación. Acentos, ñ, `¿` y `¡`. `/komaedit text fonts` lista las fuentes.
  - **`/komaedit analyze`** (alias `analizar`, `materiales`): lista de materiales de la selección con su coste en survival (losas, escaleras y muros a su bloque base, con el cortapiedras o la mesa de trabajo según el material; puertas y camas una vez; capas, velas y pétalos por unidad; objetos de antorchas de pared, cables, cultivos y macetas), en pilas, huecos y cofres. Botón para copiarla al portapapeles y CSV en `config/komaedit/exports/`. Permiso propio `komaedit.analyze`.
  - **`/komaedit gravity`** (alias `gravedad`): deja caer los bloques por columnas conservando el orden y el número; `-a` solo lo que no se apoya. Atraviesan el agua sin bajar su nivel y quedan anegados o secos según dónde acaben; los cofres, carteles y demás bloques con datos no se mueven.
  - **`/komaedit snow [capas]`** (alias `nieve`): capas de nieve de 1 a 8 que siguen la pendiente (los escalones se vuelven rampas), se acumulan en los huecos y son más gruesas a la sombra (`-s`) o con la altura (`-h`); solo donde la nieve puede estar según la regla de Minecraft. `-p` tapa la hierba corta. La hierba de debajo pasa a nevada.
  - **`/komaedit drain [water|lava|all]`** (alias `drenar`): quita el agua o la lava de la selección y desanega los bloques; `-k` conserva algas y praderas marinas. **`/komaedit waterlog [nivel]`** (alias `inundar`): anega e inunda por debajo de un nivel (`62`, `~`, `~-2`); `-b` solo anega bloques.
  - **`/komaedit biome <bioma>`** (alias `bioma`) y el pincel **`/komaedit pincel biome <bioma> [radio]`**: pintan biomas en celdas de 4×4×4, con relleno vertical opcional (`-v`); el clic izquierdo del pincel toma el bioma del bloque. Se deshacen con `//undo` y los jugadores ven el cambio sin volver a entrar (WorldEdit reenvía los chunks).
- Nodos de permiso nuevos: `komaedit.analyze`, `komaedit.build.gravity`, `.snow`, `.drain` y `.waterlog` en el paquete básico; `komaedit.build.text`, `.biome` y `.brush.biome` en el intermedio.
- Añade pruebas JUnit (rasterizado de texto con tildes y ñ, colocación según la mirada, borde, degradado y piezas; recuento y coste en survival con pilas, cofres y CSV; gravedad que conserva el número de bloques y el nivel del agua; nieve solo donde puede estar y entre 1 y 8 capas; drenaje completo; celdas de bioma) y renders de comprobación con `KOMAEDIT_BUILDER_RENDER`, guardados en `docs/renders/utilidades/`.

### Paridad con ezEdits

- **Máscaras completas en `-m`.** `noisegen`, `flowfield`, `surface` y el pincel `petalpainter` aceptan cualquier máscara de WorldEdit (`#surface`, `>stone`, `%50`, `=y>64`, `##minecraft:logs`…) con las de ezEdits que registra KomaEdit (`#near`, `#fullblock`, `#eznoisemask`, `#palette`…), entre comillas si lleva espacios. Se parsean con el `MaskFactory` de WorldEdit en el hilo del servidor y se evalúan en el worker sobre la foto, nunca sobre el mundo. La lista de bloques que admitía cada orden sigue valiendo con el mismo significado.
- Las máscaras de las operaciones de región ven el mundo real mientras se parsean (`#aim` acierta el bloque mirado) y rechazan `#current` con un mensaje claro, porque la máscara global está atada al mundo y el worker no puede leerla.
- **Perfil `Panes`** de smoothblocks: las partes finas de la superficie pasan a paneles de vidrio o barrotes de hierro, unidos entre sí y a los bloques enteros de al lado como los dejaría Minecraft (las conexiones ya van en el plan). El vidrio se convierte en su panel, `iron_block` en `iron_bars` y el resto en el panel del color más parecido; `Panes(Pane:iron_bars)` fija uno. Funciona en todos los comandos con `-w`, y el holograma lo dibuja con su poste y sus brazos.
- **`//ezspline structure` completo:** radios por fotogramas (`4,12,4`, `0:4,0.3:12,1:4`) que escalan la sección a lo largo del recorrido, `-t` para retorcerla y `-r inicio[,fin]` para girarla, como en los tubos.
- `-b` de `array` lee su alcance de `arraySnapRadius` en `config/komaedit/config.json` (96 por defecto, de 1 a 256).
- Añade **`/komaedit quiet [on|off]`** (alias `silencio`), como `/ezedits quiet`: silencia los mensajes informativos de cada jugador y se guarda entre sesiones. Añade `/komaedit wiki`, que indica dónde está la ayuda.
- `tweak wax` encera y desencera también los carteles (normales y colgantes), como ezEdits; van en su propia edición de `//undo`.
- `surface`, `deform` y el suavizado admiten valores entre comillas en sus banderas.
- Añade pruebas JUnit de las máscaras (listas de antes frente a WorldEdit, `#current`, comillas), de los paneles (codificación, encaje, conexiones y materiales) y de la sección variable de `structure` (radios, giro y torsión), y renders de comprobación de paneles y de `structure`.

### Correcciones

- Corrige `scatter`, `place` con alineaciones que miran la normal, `array -b` y sus pinceles, que fallaban con «error inesperado» desde el texturizado: `RegionOperationService.capture` sin operación leía `needsLight()` de una operación nula (`NullPointerException`).
- Prueba de humo sin el juego (`CommandTreeSmokeTest`): monta el árbol entero de `/komaedit` con Brigadier y comprueba que ninguna orden se registra dos veces, que `/ke`, `/mx`, `/moxia` y `ruta` llevan a donde deben, que cada orden tiene su tema de ayuda y que la ayuda solo enseña órdenes que existen. `KomaEditConfigTest` carga la configuración de serie, una de la 1.4.0 sin los campos nuevos y una rota.
- La bandera de vista previa ya no se come el `-v` propio de `biome` (relleno vertical), de `text` (de pie) ni el `-v <variante>` de `color gradient`. Las órdenes que no colocan bloques (`color`, `analyze`, `symmetry`, `capability`, `quiet`, `wiki`, los presets de `tree` y `rock`, `text fonts`) y `gradient`, que no pasa por el plan de la vista previa, ya no aceptan `-v` en silencio.
- `/komaedit cancel` para también el pintado de biomas, y la bossbar lo enseña.
- Lo calculado sobre la foto de un mundo no se aplica en otro: las operaciones de región, el terraformado, los tejados, el texto, los biomas con máscara y la colocación se descartan con aviso si cambias de dimensión antes de que terminen.
- Al desconectarse se paran también la lectura de los pinceles y el cálculo en marcha.
- `text` ya no anuncia «en cola» cuando la edición se queda en vista previa, y todos los avisos de «N bloques en cola» (también los de figuras, `bend`, `gradient`, `ezspline`, generadores, colocación, vidriera, hiedra, texto, imagen, árboles y rocas, tejados y lofts) respetan `/komaedit quiet`, como ya decía su documentación.
- El pincel `heightmap` admite en `-m` cualquier máscara de WorldEdit, como el resto de pinceles.
- Ayuda: tema nuevo de pinceles (`/komaedit help brushes`), los atajos de figuras (`pillar`, `cloud`, `mandala`, `web`, `lightning`, `noodles`…) y los alias en español que faltaban; `/komaedit help erosion` lleva al tubo erosionado, que es la orden `/komaedit erosion`.
- Documentación: «Árboles y rocas» había quedado partida dentro de «Loft entre curvas», la tabla de paquetes de permisos tenía la fila intermedia dos veces y la paridad con ezEdits aún daba la simetría, la vidriera de imagen y `quiet` por pendientes. Índice al principio de `docs/MOD_DOCUMENTATION.md`.

## 1.4.0 (estable, 2026-10-19)

- Añade **`/komaedit ezspline`** (alias `ezsp`, `spline`) con la sintaxis de `//ezspline` de ezEdits: `2d`, `3d`, `basic`, `rope`, `noise`, `expression` y `structure`, las formas con sus parámetros largos y abreviados (`Chainlink(E:0.2,T:1,G:0,M:3,N:3,P:BOTH)` o `Ch(...)`) y las banderas comunes `-p -q -r -s -t -n -e -w -h`, en cualquier orden. También las formas antiguas como `//ezsp polygon clay 10 4`. Los ejemplos de la wiki funcionan tal cual, con los perfiles de `-w` (salvo `Panes`, que KomaEdit no tiene).
- Formas 2D nuevas o completadas: `SuperCircle(Exponent)` (también por debajo de 1), `CirclesCircle(Count,Filled)` de 1 a 12, `Strands(Count,Size)`, `Pipe(Inner)`, `Polygon(Sides,Depth)`, `Rectangle(X1,Y1,X2,Y2)` por esquinas, `Star(Sides,Depth)` y `Flower(Count,Depth)`.
- Formas 3D propias en espacio de tubo, sin huecos: `Beads`, `Cubes(Gap)`, `Chainlink` con extrusión, grosor, hueco, exponentes y `Place`, `Fishnet(Spacing,Depth,Width)`, `Honeycomb(CellSize,WireWidth,Depth)`, `Oscillate(Depth,Interval)`, `Rings`, `Scales` (columnas, desplazamientos, exponentes, ángulo y profundidad) y `Noodles` (fideos que se enredan sin cortarse, con cantidad, densidad, frecuencia, enredo, ancho y semilla). `Braids(Thickness)` usa la trenza.
- Spline de ruido con cualquier ruido, profundidad y expresión de corte `-i` (con `n` el ruido y `d` la profundidad); spline de expresión con `-z` y `-o`; spline de estructura que dobla cualquier estructura de colocación, a su tamaño o escalada a un radio, repetida o estirada con `-z`.
- Añade la calidad **`exact`** a todas las figuras de tubo (`-q exact`): coloca los bloques que la figura llena al menos a la mitad, medido con 64 muestras. Antes `exact` era un alias de `high`.
- Los radios por fotogramas admiten hasta 8 valores (antes 6) y un radio con decimales (`6.9`) ya no se redondea.
- Añade pruebas JUnit con los ejemplos de la wiki, formas sin huecos (alma de los hilos, piezas enteras, sin cavidades, idénticas a un barrido de fuerza bruta) y deterministas, y renders de comprobación con `KOMAEDIT_SPLINE_RENDER`.
- **Smoothblocks con los perfiles de ezEdits.** `-w` acepta `Slabs`, `SlabsAndStairs`, `SlabsAndStairs2D`, `Layers` (capas de nieve) y `SSW` (losas, escaleras y muros), con la cobertura `C:` de 0 a 1 y materiales fijos por variante: `-w SlabsAndStairs(C:0.7,Slab:oak,Stair:spruce)`. Funciona igual en las figuras de tubo, `deform`, `surface`, `smooth`, `inflate`, `deflate`, `smoothblocks`, `place`, `array` y `scatter` (también en sus pinceles). Siguen valiendo `slabs`, `stairs`, `stairs:0.7` y `-c`.
- `SlabsAndStairs` elige ahora las esquinas de las escaleras en 3D, mirando a las vecinas y a la regla de Minecraft; el comportamiento anterior, escaleras rectas una a una, es `SlabsAndStairs2D`.
- Material por defecto: la pieza del mismo nombre o, si el bloque no la tiene, la del color más parecido según la media de las texturas vanilla (ΔE de CIELAB), en lugar del color de mapa. La tabla de familias cubre más casos (pizarra reforzada, verruga del Nether, basalto liso, cobre cincelado y encerado…); la madera usa su valla como muro.
- Las capas de nieve solo se colocan con apoyo, y los muros solo donde encajan mejor que el bloque.
- El holograma dibuja capas y muros, y el Studio recorre los perfiles nuevos en la fila **Piezas**.
- **Envejecer, rehecho por elementos.** Los muros reciben un perfil de altura arruinada por columna: bajan en el centro de los lienzos, en los extremos sueltos y junto a puertas y ventanas, y aguantan en esquinas, pilares y muros gruesos; el borde roto respeta el talud del material, queda dentado y nunca deja agujeros con muro entero encima. Los tejados y forjados se hunden a manchas desde el centro de la luz (según su distancia de apoyo real) y la madera, la lana y el vidrio se pierden mucho antes que la piedra; del vidrio quedan restos de panel. Sustituye al desgaste por exposición, que pelaba aristas y almenas primero y dejaba muros como un queso.
- Envejecer: lo que se queda sin unión con el suelo cae siempre, también con `-d 0` (antes podía quedar flotando). El escombro de los muros cae a su pie, por dentro y por fuera, y se amontona en los rincones; la madera caída se pudre con la intensidad.
- Envejecer: musgo, grietas, desgaste, podredumbre, corteza y óxido salen en manchas con gradientes con sentido (musgo desde el suelo, al norte, a la sombra y en churretes bajo ventanas y cornisas; grietas junto a lo roto; óxido según la intemperie). El descortezado ya no llega al 50-90 % a intensidad máxima. La vida sigue al daño, apenas hay alfombra de musgo bajo techo y la hiedra también sube desde el suelo por las caras húmedas.
- Añade pruebas JUnit del envejecimiento (esquinas frente a centros, nada flotando, sin huecos bajo muro entero, madera antes que piedra, autocorrelación de las variantes, escombro al pie de los muros, hiedra desde el suelo, determinismo por semilla) y renders de comprobación con `KOMAEDIT_WEATHER_RENDER`; los de antes y después están en `docs/renders/envejecer/`.
- **Rediseño del Procedural Studio.** Todo vive en un único panel opaco a la derecha (barra de herramientas, etapas con icono y nombre, contenido desplazable y un pie fijo con figura, material, estimación, estado y Construir); el mundo queda libre, el chat se ve y el marcador del servidor ya no se transparenta. Fuera la barra inferior, la cabecera sobre el mundo, el lema, la tarjeta de nodos duplicada, el segundo botón «Añadir puntos» y la tarjeta de navegación fija.
- Jerarquía nueva: título de etapa en negrita, secciones en color de acento con una línea, etiquetas en gris y valores en acento; Construir es la única acción con relleno. Sin mayúsculas forzadas, filas de 20 píxeles en lugar de 25–30 e interruptores con pulsador para las opciones de sí o no.
- La etapa Trayectoria enseña la lista de nodos (un clic elige el nodo para moverlo) y el estado abierto o cerrado; el nombre de la receta y Guardar pasan a la etapa Recetas.
- Todo el Studio, el HUD de captura y los estados del holograma salen en el idioma del cliente: nombres de figuras, modos, fórmulas, climas, etiquetas que cambian con la figura y todos los mensajes, en `es_es.json` y `en_us.json`. El buscador de figuras también reconoce el nombre traducido.
- Corrige los `%` sin escapar de varias ayudas, que Minecraft mostraba mal.
- Código: `StudioLayout`, `Column` y `Rect` (lógica pura con pruebas a varias resoluciones y escalas de GUI) reparten la pantalla, y `StudioUi` dibuja cada control y registra en el mismo paso qué hace al pulsarlo, con la rueda o con clic derecho: desaparecen los métodos de clic que repetían las coordenadas del dibujo. Se elimina `LiveBuildScreen`, que no se usaba. Una prueba impide volver a escribir frases a fuego en las clases del Studio.
- Añade `docs/EZEDITS_PARITY.md`: la wiki de ezEdits página a página frente a KomaEdit (orden, clase y estado: completo, parcial, falta o en curso).
- Añade los **pinceles de colocación** `scatter` (alias `dispersar`, `sc`) y `array` (alias `hilera`, `ar`), y el pincel `place` pasa al motor de `/komaedit place`: todas las estructuras, alineaciones (también `ViewDiff`, con un segundo clic) y banderas. `scatter` reparte por defecto en una caja de 40³ centrada en el clic; `array` coloca a lo largo de un trazo marcado con el clic izquierdo. Con `-p` el pincel `place` sigue usando la colocación con holograma de antes.
- Añade a los ruidos el tipo `Shard` (con nitidez `h`), las distancias celulares `m1`, `m4`, `m99` (Minkowski) y `r` (redondeada), los retornos `rounded` y `noiseLookup` (`cN`, `cF`) y las distorsiones de dominio `Flow` y `Turbulence`, con implementación propia.
- Añade la máscara y el patrón `#hotbar` (los bloques de la barra rápida).
- Añade `/komaedit sel catenary <puntos> [comba] [dirección]` (alias `catenaria`): una selección convexa colgando como una cadena entre el principio y el final de la selección, con la comba relativa al vano como `//selcatenary`.
- Añade las **paletas de serie del servidor** en `config/komaedit/palettes.json`, que todos usan con `##nombre` y que salen en `/komaedit palette list default`.
- Añade **permisos finos** por orden y modo (`komaedit.build.deform.noise`, `komaedit.palette.save`, `komaedit.select.catenary`…) y los paquetes `komaedit.permpack.basic`, `intermediate`, `advanced` y `admin`, como la página de permisos de ezEdits. Los permisos amplios de siempre siguen funcionando igual; `/komaedit permisos` muestra cuáles tienes.
- Añade `/komaedit info` y `/komaedit reload` (permiso `komaedit.admin.reload`), que recarga `config.json` y las paletas del servidor sin reiniciar.
- Añade los alias `/komaedit pincel none` y `/komaedit survival` (como `tweak`).
- `-b` de `array` busca la superficie hasta 96 bloques, como ezEdits (antes 48).
- Los avisos de permiso salen en español («Te falta el permiso …»).

## 1.3.0 (estable, 2026-10-18)

- Añade la **colocación de estructuras** de ezEdits: `/komaedit place` (alias `colocar`), `/komaedit array` (alias `hilera`) y `/komaedit scatter` (alias `dispersar`), con la forma `<estructura> [<primaria>] [<secundaria>] [banderas]` y la cadena dimensiones → escala aleatoria → orientación → simetrías → giros de 90° → alineación. Se calcula en un worker, se coloca de abajo arriba en una sola edición deshacible con //undo y, donde se lee el mundo, sin pisar lo que cambie mientras tanto.
- Añade las alineaciones `Constant`, `Random`, `Noise`, `Aim`, `Upward`, `PlayerRelative`, `SurfaceNormal`, `Expression` (con `rx`, `ry`, `rz` y espacios `WORLD`, `LOCAL` y `NORMALIZED`), `Tangential`, `Orthogonal` y las mezclas con pesos (`10%C,2%R`), con direcciones escritas como `east-z+(0,0.5,0)` o `2*up+aim`, el ajuste `-j` (`MULTIPLES_90` … `DIAGONALS_5_1`) y la perturbación `-x`.
- Añade las banderas comunes `-s`, `-o`, `-k`/`-c`, `-f`, `-r`, `-a`, `-w slabs|stairs[:cobertura]` e `-i`, las de dispersión `-h` (`Active`, `Box`, `Ellipsoid`, `Cylinder`, `Saved`), `-d`/`-e`, `-m`, `-n`, `-u`, `-l` y `-t`, y las de array `-g`, `-y`, `-q`, `-p`, `-n` y `-b`.
- Añade las estructuras `Clipboard` y `Schematic` (origen `INHERENT`/`CENTER`, `PasteMethod` `FAST`/`SMOOTHED` con `FillBias`, `Rotation`, schematics por expresión regular con `%p`), `Shape` con 17 formas propias (cono, cristal, cubo, rizo, cilindro, elipsoide, pelo, corazón, judía, hoja, limón, cebolla, polígono, pirámide, superesfera, tetraedro y toro) o una fórmula, `Expression`, `TexturedShape` e `Icosphere` (roca con aleatoriedad, subdivisiones y plataforma), con las opciones comunes `RO`, `WO` y `OJ` y, en las procedurales, `Origin:BOTTOM` para apoyarlas en el suelo.
- La dispersión busca superficies y normales en una foto del mundo, filtra por dirección y por máscara de WorldEdit (comprobada fuera del hilo del servidor), aplica la densidad y reparte los puntos por repulsión con semilla.
- `RegionOperationService.capture` lee una región cualquiera y entrega la foto, para operaciones que montan su propio plan.
- El pincel de colocación acepta también `Schematic`, `Shape`, `Expression`, `TexturedShape` e `Icosphere` (registradas en `StructureSources`), y la dispersión usa las selecciones guardadas (`-h Saved(Name:…)`) y las paletas guardadas (`#nombre`).
- Añade pruebas JUnit de las formas y la icosfera, la alineación, el ajuste de direcciones, la dispersión con semilla, los arrays, el voxelizado (copia exacta, giros, escalas, SMOOTHED, aire, solapes, losas y escaleras), las banderas y los ejemplos de la documentación, y renders de comprobación con `KOMAEDIT_PLACEMENT_RENDER`.
- Añade `/komaedit deform` (alias `deformar`) con los modos de `//ezdeform` de ezEdits: `hexagonalize` (columnas de tapa plana, red de ángulo variable, hueco y giro), `noise` (desplazamiento por ruido, `-z`, `-h`, `-v`), `rotate` (giro según la mirada, `-o`), `voronoialize` (trozos convexos con hueco), `voronoialize2` (facetas planas repartidas por la superficie, `-r` y `-n`) y `voxelize` (cuboides por mayoría con escalas por eje, distorsión, `-i`, `-j`, `-h` y `-v`).
- Añade `cylindrify` y `spherify` (Mercator o LAEA con `-l`): proyectan la selección sobre un cilindro o una esfera nuevos, encima de ella o alrededor del jugador, con `-r`, `-d`, `-x`, `-i`, `-j`, `-a`, `-f`, `-o`, `-s` y `-t`. La caja de salida se lee y se escribe de forma condicional y se avisa de su volumen antes de empezar.
- Añade `/komaedit surface` (alias `superficie`) con `fuzzify`, `rockify`, `voronoify` y `noisify` sobre una distancia exacta a la superficie, con suavizado, `-c` (solo tallar), `-e` (solo crecer), `-m` (lista de bloques y etiquetas) y `-t` (conserva la topología con la prueba de punto simple).
- Añade `/komaedit smooth` (radios por eje, iteraciones y sesgo), `inflate`, `deflate` (elipsoides exactos) y `smoothblocks <perfil> [radio] [sesgo]`, con alias en español.
- Todas estas operaciones admiten `-w <perfil>` con los nombres de ezEdits (`Slabs`, `SlabsAndStairs`, cobertura con `(C:0.7)`), reutilizando las losas y escaleras de las figuras de tubo.
- El motor de operaciones sobre la selección puede leer y escribir una caja extra fuera de la selección.
- Añade los **pinceles de ezEdits** sobre el sistema de brochas: `/komaedit pincel` (alias `ezbrush`) liga a la herramienta de la mano `paletteshift`, `gradient`, `gradientstroke`, `petalpainter` o `place`. Cada clic se captura repartido por ticks, se calcula en un worker y se aplica como una sola edición de //undo; con `-p` queda en vista previa y se aplica con `/komaedit pincel aplicar`.
- **Desplazar paleta** (`paletteshift`): cada bloque de la paleta pasa al que tiene N posiciones más allá (clic derecho hacia delante, izquierdo hacia atrás), con las losas y escaleras de cada material y `-r` para dar la vuelta.
- **Degradado** (`gradient`) entre dos puntos, por planos o por distancia al inicio (`-d euclidean|manhattan|chebyshev`), y **Trazo de degradado** (`gradientstroke`) a lo largo de una polilínea o del eje al borde (`-d`), con interpolaciones `NONE`, `LINEAR`, `TAPERED`, `BEZIER` y `SIN`, sangrado, ruido (`-n`, `-z`), aire (`-a`), losas y escaleras del material (`-w`) y puntos GLOBAL o PER_ITEM con la tecla F.
- **Pintor de pétalos** (`petalpainter`): follaje en capas sobre la máscara (`-m`, por defecto `#fullblock`) con densidad, ruido y piezas de 1 a 4; repasar espesa y el clic izquierdo lo quita.
- **Pincel de colocación** (`place`): pega el portapapeles en cada clic con alineación primaria + secundaria (`Constant`, `Aim`, `Upward`, `SurfaceNormal`, `PlayerRelative`, `Random`) ajustada a giros de 90°, `Origin`, `Rotation`, `-a` y `-x`. Las estructuras salen de un registro (`StructureSources`) preparado para las del motor de colocación.
- Añade la **supervarita**: `/komaedit superwand` (alias `supervarita`) con los modos `trace`, `position`, `relative` y `grab` escribe en la selección de WorldEdit (cuboide, esfera, cilindro, polígono o convexo); clic izquierdo quita el último punto, Mayús + izquierdo cambia de modo, F invierte el orden y Mayús + F desplaza los puntos.
- Con un pincel o la supervarita en la mano, el cliente ya no rompe bloques con el clic izquierdo ni cambia de mano con la F: las acciones viajan al servidor como un paquete propio.
- Registra el holograma de vista previa de las brochas (`preview_hologram`), que el servidor enviaba sin que el cliente lo recibiera.
- Añade pruebas JUnit del desplazamiento de paleta, los degradados y sus interpolaciones, el trazo, la distribución de pétalos, la máscara, la alineación, las opciones de los pinceles y los puntos y modos de la supervarita.
- Añade el **texturizado** `/komaedit texture` (alias `textura`) con los doce modos de `//eztexture` de ezEdits y sus argumentos y banderas: `ambient` (oclusión ambiental por rayos), `axisgradient`, `blend`, `blocklight`, `cells` (Voronoi con repulsión), `curvature`, `flow` (líneas pegadas a la superficie que siguen un ruido, con inercia, gravedad y relleno), `noise`, `pointlight` y `sunlight` (ángulo a la luz, atenuación y sombras suavizadas), `shift` y `advanced`.
- `advanced` combina texturas con la notación de ezEdits: `Add`, `Subtract`, `Multiply`, `Divide`, `WeightedAverage`, `Darken`, `Lighten`, `Difference`, `Screen`, `Adjust`, `Invert` y `Blend`, anidables. Los ruidos y paletas aceptan los de serie y los guardados del jugador, y `@@nombre(…)` cambia parámetros de un ruido con nombre (`@@ridged(Freq:0.12)`).
- `-w` conserva escaleras, losas, muros, vallas, botones, placas y trampillas con el material más cercano de la paleta que tenga esa pieza; `-d` añade tramado entre bloques vecinos de la paleta.
- Añade `/komaedit vines` (`enredaderas`), `moss` (`musgo`), `slabmerge` (`unirlosas`) y `statecycle` (`ciclarestado`), los comandos de región de ezEdits; las enredaderas de vanilla se cuelgan con sus caras, tallo y punta.
- Añade la **vidriera degradada** `/komaedit glassgradient` (`vidriera`): busca en haz las capas de cristal tintado que más se acercan a cada color (OKLab, colores medidos en las texturas de 1.21.1), con fondo fijo (`-c`) o el mejor bloque opaco detrás (`-b`), y la coloca delante del jugador.
- Las operaciones de región aceptan cualquier máscara de WorldEdit que mire bloques: se evalúa en el worker sobre la foto con un `Extent` de solo lectura, sin tocar el mundo.
- La captura de región puede guardar la luz de bloque y del cielo cuando la operación la pide, y la luz que emite cada estado.
- Registra en WorldEdit las máscaras de ezEdits para cualquier orden (`//set`, `//replace`, `//gmask`, pinceles…): `#aim`, `#attached`, `#blocklight`, `#clipblocks`, `#current`, `#eznoisemask`, `#fullblock`, `#fuzzypalette`, `#infested`, `#lightsource`, `#near`, `#palette`, `#radialgradientmask`, `#schemblocks`, `#truelight` y `#vectorgradientmask`, con sus alias.
- Registra los patrones `#aim`, `#palette`, `#eznoisepattern` (y los atajos `#ridged`, `#smoothcells`, `#voronoiedge`), `#radialgradientpattern`, `#vectorgradientpattern` (`#vectorgradient`) y `#selection`.
- Añade las paletas guardadas por jugador y mundo: `/komaedit palette` (alias `paleta`) con `list`, `save`, `delete`, `fetch`, `place`, `swap`, `print`, `encode` y `decode`, y `#nombre` en cualquier paleta, también en los degradados.
- Añade las paletas de serie `##Beige`, `##Brown`, `##DustyBrown`, `##EnchantedDark`, `##EnchantedWarm`, `##EnchantedBright`, `##LegacyWool`, `##Light`, `##Moss`, `##Ice`, `##Rocks`, `##Leaf`, `##LeavesAll`, `##Sand` y `##ShiningGold`, con listas propias.
- Añade los ruidos guardados: `/komaedit noise` (alias `ruido`) con `list`, `save`, `delete` y `print`, y nueve ruidos de serie por nombre (`ridged`, `smoothcells`, `voronoiedge`, `clouds`, `marble`, `veins`, `pebbles`, `dunes`, `static`) donde se acepte un ruido.
- Añade `/komaedit sel` (alias `seleccion`): `list`, `save`, `load [-h]`, `delete`, `next`, `here`, `invert`, `delpos2`, `encapsulate` y `encapsulatenear`, como las órdenes de selección de ezEdits.
- **Studio más cómodo y rápido.** Buscador de figuras sin tildes por nombre, id, alias y descripción (`Ctrl+F` o `/`), familias (Tubo, Eslabones, Pieles, Orgánicas), favoritas con ★ y las cuatro recientes; favoritas y recientes se guardan por jugador en `config/komaedit/studio.json`.
- Los controles numéricos aceptan rueda (sobre botones y valor), `Mayús` para pasos grandes, arrastre horizontal como deslizador, escribir el valor con un clic y clic derecho para volver al de serie, con una barra que enseña el valor dentro de su rango. Las filas ‹ › también responden a la rueda y al clic derecho.
- Ayuda al pasar el ratón: qué hace cada control, su rango, paso y valor de serie; descripción completa de cada figura.
- Deshacer y rehacer de ajustes dentro del Studio (`Z`, `Y`, botones ↶ ↷), con 100 pasos y los cambios seguidos del mismo control fundidos en uno; `Ctrl+Z` sigue deshaciendo en el mundo.
- Presets por figura (Compacto, De serie, Amplio, Robusto) y variante al azar con semilla (`R`).
- Recuento estimado de bloques antes de Construir y confirmación con segunda pulsación por encima de 250 000 bloques; aviso en rojo por encima del límite de serie del servidor.
- Atajos de teclado (`1`–`8`, `Tab`, `B`, `H`, `Ctrl+S`, `Ctrl+Supr`, `RePág`/`AvPág`) con chuleta en `F1` o `?`, y botón para ocultar el holograma.
- Corrige que escribir una `k` en el nombre de la receta o en el buscador de bloques cerrara el Studio, y que el personaje se moviera al escribir el nombre.
- La barra lateral, la barra superior y el pie se adaptan a pantallas bajas y escalas de GUI grandes, y las filas desplazadas bajo la cabecera o el pie ya no responden a clics.

- Añade la edición de puntos de trayectoria por comando: `path insert`, `remove`, `set`, `shift`, `reverse`, `close on|off`/`open`, `mirror` (con `append` para completar una curva simétrica), `rotate`, `snap`, `smooth`, `simplify` y `subdivide`, y `path points` con botones para insertar, quitar o mover cada punto. Ej.: `/komaedit path insert 3`, `/komaedit path mirror x append`.
- Añade deshacer y rehacer de la edición de puntos (`path undo`, `path redo`), con 64 pasos por jugador; arrastrar el mismo nodo cuenta como un paso. `path undo` ya no quita siempre el último punto: deshace el último cambio; el comportamiento anterior pasa a `path pop`.
- Añade generadores de trayectoria: `path polygon`, `path spiral`, `path helix`, `path arc` y `path catenary` (cadena colgante entre el primer y el último punto, como `//selcatenary` de ezEdits, con comba y dirección `down|up|left|right`). Ej.: `/komaedit path catenary 8`.
- Añade recorridos guardados con nombre por mundo y jugador: `path save|load|list|delete`, con escritura atómica en `<mundo>/komaedit/players/<uuid>/paths/` (formato nuevo, documentado).
- Añade `/komaedit estimate` (alias `estimar`): bloques, chunks, caja, memoria y tiempo aproximado sin construir. Ej.: `/komaedit estimate coil stone 8`.
- Los Build de más de `confirmBuildAboveBlocks` bloques (250 000 por defecto, clave nueva y opcional de `config.json`) esperan a `/komaedit confirm` con botones en el chat, también desde el Studio.
- Rehace `/komaedit help [tema]` (alias `ayuda`) por temas, con comandos clicables que se escriben solos en el chat.
- Los errores de figura, perfil, extremo, paleta, bandera, parámetro, tema de ayuda o recorrido desconocidos proponen «¿Quisiste decir…?», y el autocompletado de figuras, perfiles y paletas muestra su nombre.
- Añade el alias `ruta` de `path` y la tecla **Insert** del editor en vivo para insertar un nodo a mitad.
- Añade pruebas JUnit de la edición de puntos, los generadores (catenaria incluida), el historial, «¿quisiste decir…?», las estimaciones, la ayuda y el formato de recorridos guardados.

- Añade `/komaedit shape` (alias `forma`) con las formas de `//ezshapes` de ezEdits: `cone` (base elíptica, giro, `-d` punta abajo), `polydome` (domo facetado con `-v` para pintar las aristas), `polygon` (prisma regular en cualquiera de las 26 direcciones o hacia la mirada), `square` (`-f` caras, `-w` paredes), `tetrahedron` y `torus` (secciones `circle`, `diamond`, `rounded_square` y `square`). Banderas `-h` (hueca, cáscara de un bloque sin huecos por las diagonales), `-o` (orientar a la mirada) y `-s` (mover la selección a la forma).
- Añade `/komaedit noisegen` (alias `ruidogen`) con los modos `heightmap`, `terrain` y `advanced`: paleta por alturas, ruido con la notación de ezEdits, altura, fuerza por alturas, umbrales con fórmulas, zoom (`-z`), semilla (`-s`, se dice en el chat para repetirla), desplazamiento (`-o`), estirado vertical (`-l`), máscara (`-m`, genera encima de lo que ya hay), coordenadas centradas (`-c`) o normalizadas (`-n`), mapa de alturas en advanced (`-h`), modo suave con nieve en capas y lagos planos (`-t`) y ruido 3D en el mapa de alturas (`-y`).
- Añade `/komaedit flowfield` (alias `flujo`): campos de flujo en la selección con paleta por número de pasadas, líneas absolutas o en porcentaje, iteraciones, velocidad, escala de paleta, inercia, gravedad, temblor, borde, transformación de coordenadas con fórmulas (`-x/-y/-z`), progresión de fuerza, rotacional (`-c`), relleno (`-f`), modo 3D (`-t`) y mapa de alturas sobre el terreno (`-h surface|above`) con máscara de salida (`-m`).
- Añade `/komaedit flowline` (alias `lineaflujo`): líneas de flujo desde los ojos hacia la mirada con gravedad, inercia, varias líneas con azar (`-l`, `-r`), pegado a las superficies (`-s`) y selección convexa de WorldEdit a lo largo de la línea (`-c`).
- Los generadores calculan en un worker; lo que necesita leer el mundo (máscaras y superficies) usa la foto de las operaciones sobre región y escribe solo donde el bloque no ha cambiado. Todo queda en una sola edición deshacible con //undo. Las paletas y los ruidos guardados (`#nombre`) también valen en los generadores.
- Añade la **Hiedra viva**: `/komaedit ivy` (alias `hiedra`) hace crecer una enredadera desde los puntos P o desde el bloque que miras sobre lo que ya hay construido. Las puntas se pegan a las caras enteras, trepan muros, cruzan tejados, se ramifican, cuelgan con gravedad de dinteles y aleros y florecen; cada bloque lleva sus caras y propiedades de vanilla (`vine`, `glow_lichen`, `moss_carpet`, hojas persistentes, `cave_vines` con bayas, `spore_blossom`).
- Añade cuatro estilos (`hiedra`, `florido`, `selvatico`, `cueva`) y las banderas `-l` (largo), `-b` (ramas), `-g` (cuelgue), `-n` (tallos) y `-seed`; con la misma semilla sale la misma hiedra. Captura repartida por ticks, cálculo en un worker, una sola edición deshacible con //undo y sin pisar bloques puestos mientras crecía.
- Registra el avance por ticks de la captura del entorno, que no llegaba a ejecutarse.
- Añade paletas con la notación de ezEdits en los degradados: `##Nombre`, `-` (invertir), `(a:b)` (tramo), `*n` (repetir) y `[…]` (agrupar), compatibles con la lista `a;b;c`.
- Añade ruido configurable con la notación de ezEdits (`Perlin(f:0.04)`, `Ce(cD:e,cR:sub)`…) sobre FastNoiseLite (MIT): fractales, distorsión de dominio, mapeo de valores, ruido blanco, Gabor y retornos celulares derivados.
- Añade el motor de operaciones sobre la selección: lectura repartida entre ticks, cálculo en un worker y escritura condicional por lotes, deshacible con //undo.
- Añade `/komaedit tweak` (alias `retocar`): `deinfest`, `persist`, `waterlog` y `wax` sobre la selección.
- Añade **Envejecer** (`/komaedit weather`, alias `envejecer`): convierte la construcción seleccionada con WorldEdit en una ruina creíble. El desgaste sigue la exposición a la intemperie (caras y vecinos al aire, ponderados por la distancia al cielo abierto: lo que está bajo techo apenas se gasta), lo que se queda sin apoyo cae según el vano que aguanta cada material y forma montones de escombro con losas y escaleras, y crecen musgo, hierba, helechos, enredaderas, raíces y telarañas donde tiene sentido.
- Añade una tabla de variantes envejecidas que conserva las propiedades del bloque: musgo, grietas, pérdida del pulido, óxido del cobre por pasos (el encerado no cambia), madera descortezada y tablones podridos.
- Añade cuatro climas (`templado`, `desierto` con arena y terracota, `nevado` con nieve y hielo, `hundido` con agua, algas y kelp), intensidad, derrumbe y vida de 0 a 100, semilla repetible y charcos de agua estancada (`-agua`).
- Añade la categoría **Envejecer** al Studio.
- Añade la figura **Expresión** (`expression`): una fórmula del jugador en espacio de tubo (`x`, `y` de -1 a 1 en la sección, `z` a lo largo en radios, más `t`, `r`, `a` y `n`) decide qué bloques se colocan (salida > 0) y, con **Patrón figura**, su material (valor en `(0, 1]`).
- Añade un compilador de fórmulas propio, sin bucles ni estado compartido: operadores `+ - * / % ^`, comparaciones, `&& || !`, ternario, 26 funciones (con `noise` determinista), definiciones `nombre = valor;`, nombres sensibles a mayúsculas, límites de 256 caracteres, 16 definiciones, 40 niveles y 400 operaciones, y errores en español con la posición. Evalúa 6–12 veces más rápido que el motor de WorldEdit 7.3.8 con las mismas fórmulas.
- Acota el alcance de la fórmula por análisis (`r <= 1 && …`) para no recorrer las esquinas de la sección: `r <= 1` cuesta lo mismo que el perfil círculo.
- Añade cinco fórmulas de serie (ventanas, raíles, espiral, celosía y costillas), una fila **Fórmula** y un campo editable en el Studio, y `preset:` en las expresiones de figura.
- Añade `/komaedit expr` y `/komaedit expresion <radios> [banderas] [patrón] <fórmula…>`, con nombre de serie y `n` pegado (`ventanas8`).
- Guarda la fórmula propia como texto en recetas, instancias y paquetes (formato de receta 4) y la envía por red; los formatos 1–3 se leen sin migración.
- Añade el **motor exacto** opcional (`engine:exact`, fila **Motor** del Studio) para Chainlink, Rings, Beads, Oscillate, Red, Honeycomb, Knots y Coil: cada figura se evalúa como función implícita en espacio de tubo, sin huecos, con las proporciones del motor clásico y heredando extremos, normales, radios por fotogramas, torsión y la curva de modificadores. El motor clásico sigue siendo el predeterminado y no cambia ninguna figura existente.
- Las figuras con motor exacto también admiten **losas y escaleras** (`smooth`, `coverage`, filas **Piezas** y **Cobertura**).
- **Patrón figura** usa el valor implícito de las figuras con motor exacto (eslabones, cuentas, familias de hilos y puntales por franjas de paleta).
- Añade la figura **Red** (`fishnet`): dos familias de hilos helicoidales a ±45° con celdas cuadradas a cualquier escala.
- El comando general acepta expresiones de figura entre comillas, por ejemplo `/komaedit build "coil(engine:exact,end:round)" stone`.
- Añade pruebas JUnit del motor exacto: almas de hilo sin huecos en rectas, diagonales y curvas, cinta sin agujeros, cuentas cerradas, determinismo, volumen frente al clásico y opción por defecto sin cambios.

## 1.2.0 (estable, 2026-10-17)

- Añade **losas y escaleras en la superficie** de las figuras de tubo (`sweep`, `braid`, `noisetube`): cada bloque del borde se mide por octantes con la función implícita de la figura y se sustituye por la losa o escalera orientada que mejor encaja, o se rellena un hueco casi lleno.
- Añade las banderas `-w slabs|stairs` y `-c <cobertura>` a los comandos de tubo, las opciones de receta `smooth` y `coverage`, y las filas **Piezas** y **Cobertura** al Studio.
- Calcula las esquinas de las escaleras con la misma regla que Minecraft para que el mundo construido coincida con el holograma.
- Elige la pieza del mismo material que el patrón (por nombre, con tabla para los casos sin convención y, si no existe, por color de mapa), también con patrones aleatorios y degradados.
- Dibuja las losas y escaleras en el holograma con su forma real, por medios bloques.
- Corrige que una sola receta o instancia ilegible (p. ej. de la 4.7, con una figura retirada) desactivase KomaEdit en todo el mundo con «The KomaEdit project is not initialized for this player»: ahora se omite con un aviso en `latest.log` y el proyecto sigue abierto.
- Añade el doblado de estructuras: `/komaedit bend` (alias `doblar`) curva el portapapeles de WorldEdit a lo largo de la trayectoria, repitiendo copias enteras o estirando una sola, sin huecos por fuera de las curvas y con escaleras, troncos y vallas girados para seguirla.
- Añade los modos de Kitbash **Doblar** y **Estirar**, que meten el módulo doblado en la instancia de la receta.
- En modo `upright` el módulo se asocia a la vertical de cada punto, así que en las cuestas los muros y postes quedan a plomo.

## 1.1.0 (estable, 2026-10-17)

Primera publicación estable desde `1.0.0`; recoge todo lo integrado en `staging` sobre `4.8.0-alpha.1`.

- Añade el motor de **espacio de tubo**: voxelizado inverso por ladrillos de 8³, árbol de esferas para localizar el eje y figuras implícitas estancas por construcción.
- Añade **Perfil 2D** (`sweep`) con diez perfiles (círculo, cuadrado, rombo, redondeado, polígono, estrella, flor, cuerda, elipse y rectángulo), relleno macizo o hueco y pared cerrada por morfología.
- Añade **Trenza** (`braid`) y **Tubo erosionado** (`noisetube`), este último sin fragmentos flotantes.
- Añade trayectoria Kochanek–Bartels con corrección por cuerda, marcos de mínima rotación con cierre de lazos y normales `consistent`, `horizontal` y `upright`.
- Añade progresión de radio por fotogramas (`2,0.3:12,2`), extremos `flat`, `soft`, `spike`, `round` y `cube`, torsión por diámetro y giro inicial/final.
- Aplica escala, espaciado y torsión de los modificadores dentro del espacio de tubo, sin deformación posterior ni puentes.
- Añade `/komaedit sweep`, `/komaedit perfil`, `/komaedit braid`, `/komaedit trenza`, `/komaedit noisetube` y `/komaedit erosion` con banderas `-e -n -t -r -p -q -s -d -a -x -k -f -hollow -fill`.
- Añade filas de Perfil, Relleno, Extremos y Normales al Studio para las figuras de tubo.
- Permite nombres simbólicos en expresiones de figura (`profile:star`, `end:round`, `hollow:true`).
- Amplía de 16 a 32 los parámetros avanzados por receta.
- Persiste recetas, instancias y bitácoras dentro de cada mundo, separadas por UUID, en lugar de eliminarlas al desconectarse.
- Migra de forma no destructiva los datos heredados de `config/komaedit/` al primer acceso al mundo.
- Añade escrituras atómicas, recuperación de temporales, respaldos `.bak` y checksum SHA-256 para la geometría de instancias.
- Añade `/komaedit project status` y `/komaedit project backup`, con retención configurable de backups ZIP.
- Permite reabrir una instancia desde el Studio recuperando también su trayectoria editable.
- Registra el progreso de jobs de larga duración y marca operaciones interrumpidas después de un cierre inesperado.
- Añade permisos granulares para trayectoria, figuras, recetas, instancias, selección, pintura, clipboard, historial y backups.
- Añade límites configurables por operación y para jobs concurrentes en `config/komaedit/config.json`.
- Internacionaliza en español e inglés la navegación, estados y acciones principales de Procedural Studio.
- Añade pruebas JUnit para almacenamiento atómico, recuperación, posiciones empaquetadas y curvas de interpolación.
- Permite recuperar manualmente la publicación de una prerelease de `staging` cuando GitHub no emite el evento automático, sin habilitar publicaciones manuales de `main`.
- Añade la figura procedural **Rayos**, con núcleos quebrados deterministas, anclajes exactos y ramificación configurable de `2` a `8`.
- Añade bifurcaciones secundarias en densidades altas y una corona exterior especial para trayectorias cerradas, manteniendo el centro completamente hueco.
- Añade `/komaedit lightning`, `/komaedit rayos`, `/komaedit lightningcircle` y `/komaedit circulorayos`, con materiales eléctricos predeterminados y compatibilidad con patrones de fuego.
- Añade la figura procedural **Telaraña**, con nudos gruesos en P1 y P2 unidos por un puente fino, ahusado y ligeramente curvado.
- Convierte tres o más puntos en anclajes radiales ordenados, con radios rectos y vueltas cóncavas adaptadas a su distribución.
- Añade controles de apertura, separación, grosor y espiral, holograma exacto, degradados y `/komaedit web`.
- Elimina la categoría, generadores, comandos, materiales secundarios y hologramas de Assets naturales.
- Mueve Pilar monumental y Pilares arqueados exclusivamente a la categoría Estructuras, sin duplicarlos en Figuras.
- Conserva sin cambios las figuras procedurales existentes y su geometría.
- Separa el versionado de desarrollo (`mod_version`) del versionado estable (`release_version`).
- Añade automáticamente el SHA corto a la versión interna y al JAR de cada build de desarrollo.
- Publica cada merge a `staging` como prerelease `komaedit-staging-<versión>-<sha>` en GitHub Releases.
- Publica `main` únicamente como `komaedit-<release_version>`, sin nombres de canal, alpha ni SHA.
- Impide reutilizar una versión estable de `main` para un commit diferente.

## 4.7.0-alpha.1

- Añade la categoría lateral responsive **Assets** al Procedural Studio.
- Añade **Árbol épico**, generado desde un solo P1 con raíces, contrafuertes, tronco ahusado, ramas Bézier, ramificaciones, copa orgánica y lianas.
- Implementa crecimiento inteligente para árboles: analiza 16 sectores cercanos y orienta tronco, ramas y copa hacia el espacio libre.
- Ejecuta la misma adaptación ambiental en cliente y servidor; el holograma usa una caché ligera y el Build vuelve a validar el entorno.
- Añade **Roca monumental** con superelipsoides erosionados, grietas, estratos, musgo y piedras satélite.
- Añade **Tronco ancestral** con longitud ajustable, curvatura, taper, extremos huecos, raíces, ramas rotas y crecimientos.
- Añade **Raíces antiguas** con tocón retorcido, raíces radiales, arcos naturales, vegetación y elementos colgantes.
- Permite construir todos los assets con un único punto P1; P2 es opcional y puede orientar los assets alargados.
- Amplía el formato de receta a v3 con dos materiales independientes: principal y acento.
- Integra ambos materiales con catálogo completo de bloques, red, guardado, paquetes, instancias, actualización y protección de cambios manuales.
- Clasifica cada bloque por zona geométrica; tronco/hojas y piedra/musgo nunca se mezclan aleatoriamente.
- Añade controles y límites contextuales para tamaño, altura, longitud, detalle, relieve y orientación.
- Añade `/komaedit asset tree|boulder|log|roots <altura/largo> <tamaño> <detalle> [principal] [acento]`.
- Hace compacto el rail lateral automáticamente en ventanas de poca altura para conservar visibles todas las categorías.
- Conserva sin cambios las escamas, figuras, estructuras, degradados y torii existentes.

## 4.6.0-alpha.3

- Sustituye la curva central del torii por una interpolación recta entre las alturas de ambos pilares.
- Elimina la ondulación y la elevación artificial del centro cuando P1 y P2 están en terrenos desnivelados.
- Conserva únicamente la curvatura tradicional de los aleros en los extremos de la corona.
- Añade la categoría lateral **Estructuras** con accesos dedicados a Pilar monumental, Puente arqueado y Torii monumental.
- Separa las estructuras de la cuadrícula general de figuras para agilizar su selección.
- Corrige el cálculo del área desplazable del inspector para que controles inferiores como **Altura** no queden ocultos bajo el panel **Receta**.
- Conserva sin cambios la geometría de escamas y las demás figuras.

## 4.6.0-alpha.2

- Conserva la misma altura local en ambos pilares del torii aunque P1 y P2 estén en niveles distintos.
- Sustituye la coronación horizontal común por vigas adaptativas que conectan las dos alturas mediante una curva suave.
- Añade una elevación central proporcional al desnivel para que el techo se arquee de forma natural.
- Mantiene tangentes suaves junto a cada pilar y evita cambios bruscos de dirección.
- Aplica la adaptación al travesaño inferior, viga intermedia y todas las capas de la corona superior.
- Reposiciona ménsulas y placa central según las alturas deformadas.
- Conserva sin cambios el comportamiento del torii sobre terreno nivelado y todas las demás figuras.

## 4.6.0-alpha.1

- Añade **Torii monumental**, un portal japonés gigante construido entre las bases P1 y P2.
- Genera columnas inclinadas, basas escalonadas, collarines, doble sistema de travesaños, corona multicapa, aleros curvos, ménsulas y placa central.
- Mantiene las vigas niveladas cuando P1 y P2 se encuentran a alturas distintas.
- Amplía la altura máxima de recetas monumentales desde 128 hasta 256 bloques.
- Añade controles contextuales **Grosor**, **Detalle**, **Relieve**, **Curvatura** y **Altura**.
- Hace que la altura del torii avance en pasos de cuatro bloques dentro del Studio.
- Añade `/komaedit torii <altura> <grosor> <detalle 2–8> [patrón]`.
- Evita reintentos infinitos del holograma cuando P1/P2 o los parámetros producen un error geométrico determinista.
- Integra el torii con holograma exacto, materiales WorldEdit, degradados locales, recetas y modificadores.
- Conserva sin cambios escamas, mandalas, nubes, pilares, arcos y las demás figuras.

## 4.5.0-alpha.1

- Añade **Mandala radial**, una figura 3D orientada por P1→P2 con núcleo, anillos ondulados, pétalos sólidos, filigrana, radios y corona exterior.
- Añade controles contextuales **Tamaño**, **Simetría**, **Relieve** y **Giro** para mandalas.
- Añade `/komaedit mandala <tamaño> <simetría 2–8> [patrón]`.
- Activa un gizmo XYZ mundial para seleccionar nodos y moverlos de forma restringida sobre X, Y o Z.
- Escala las flechas del gizmo con la distancia de cámara y aplica movimiento local optimista para eliminar retrasos visuales.
- Evita que el holograma quede bloqueado después de una excepción mediante reintentos automáticos con espera progresiva.
- Conserva el último holograma válido mientras se calcula o recupera el siguiente.
- Amplía la distancia del holograma desde 512 hasta 4096 bloques.
- Detecta resultados geométricos vacíos y los trata como un fallo recuperable.
- Conserva sin cambios escamas, pilares, arcos, nubes y las demás figuras.

## 4.4.0-alpha.4

- Alarga los bancos nubosos antes de aplicar cada separación.
- Organicidad baja genera aproximadamente 4–6 masas por banco y Organicidad alta puede producir unas 6–10.
- Conserva el solapamiento, la erosión, la variación determinista y la separación ajustable de `alpha.3`.
- Mantiene sin cambios escamas, pilares, arcos y las demás figuras.

## 4.4.0-alpha.3

- Distribuye las nubes en bancos naturales de 2–5 masas solapadas en lugar de generar una única cadena continua.
- Añade el control independiente **Separación**, medido aproximadamente en bloques libres entre bancos.
- Varía cada hueco alrededor de ±20% y altera el tamaño de cada banco mediante una secuencia determinista para evitar repeticiones mecánicas.
- Mantiene **Organicidad** como control separado para conservar el detalle y los lóbulos de cada nube.
- Actualiza en tiempo real el holograma cuando cambia la separación desde Procedural Studio.
- Amplía `/komaedit cloud` y `/komaedit nube` con la sintaxis opcional `<separación> [patrón]`.
- Conserva sin cambios la geometría de escamas, pilares, arcos y las demás figuras.

## 4.4.0-alpha.2

- Añade **Nube orgánica**, una figura volumétrica continua orientada por el recorrido de puntos.
- Construye masas principales sólidas, lóbulos laterales, coronas superiores y estelas afinadas sin recurrir a formas 2D.
- Añade erosión superficial mediante ruido tridimensional determinista de escala amplia y detalle fino.
- Adapta los controles del Studio a **Tamaño**, **Organicidad**, **Densidad** y **Giro** al seleccionar la nube.
- Integra la figura con recetas, deformadores, patrones WorldEdit, degradados locales y holograma exacto.
- Añade `/komaedit cloud <tamaño> <organicidad 2–8> [patrón]` y el alias `/komaedit nube`.
- Conserva sin cambios los generadores de pilares, arcos, escamas y el resto de figuras existentes.

## 4.4.0-alpha.1

- Añade **Pilar monumental**, una figura sólida orientada por P1→P2 con base escalonada, fuste con éntasis, nervaduras, anillos ornamentales, contrafuertes y capitel.
- Añade **Pilares arqueados**, que levanta dos pilares desde P1 y P2 y los conecta con un arco profundo, dovelas, clave central, vigas superiores y refuerzos diagonales.
- Integra ambas figuras en Procedural Studio, recetas, holograma exacto, deformadores, materiales sólidos y degradados por figura.
- Adapta los controles de Figura a **Ancho**, **Detalle**, **Relieve**, **Giro** y **Altura** según el generador seleccionado.
- Amplía dinámicamente el desplazamiento del panel Figura para que los nuevos controles sigan accesibles en resoluciones pequeñas.
- Añade los comandos rápidos `/komaedit pillar` y `/komaedit pillararches` con patrones WorldEdit opcionales.
- Mantiene intactas la geometría, el muestreo y el sombreado de `scales` y `spiked_scales`.

## 4.3.0-alpha.3

- Elimina el retorno accidental al degradado global en bloques interiores o reconstruidos de **Scales**.
- Asigna las zonas superpuestas a la escama exterior dominante para impedir parches claros y cambios de propietario dentro de una misma pieza.
- Calcula el contorno mediante distancia física en bloques, evitando que el borde ocupe grandes sectores en las puntas estrechas.
- Reduce y endurece la transición del labio y los laterales para conservar un cuerpo oscuro con un borde fino y definido.

## 4.3.0-alpha.2

- Sustituye las bandas longitudinales de **Scales** por coordenadas locales independientes para cada escama.
- Reconstruye la fila, columna, raíz, cuerpo, laterales y labio de la escama sin modificar su geometría.
- Mantiene el cuerpo en la zona oscura de la paleta y concentra los materiales cálidos o luminosos en laterales y borde frontal.
- Compensa escala continua, torsión y espaciado variable para que el material permanezca adherido a cada escama deformada.
- Actualiza **Frente/reverso**, **Relieve** y **Filas alternas** para trabajar con las piezas reales de Scales y no con anillos completos.

## 4.3.0-alpha.1

- Añade métricas locales **Patrón figura**, **Frente/reverso**, **Relieve** y **Filas alternas**.
- Calcula el periodo real de cada figura para reiniciar el degradado en cada motivo, en vez de usar únicamente coordenadas globales.
- En escamas, combina la fase raíz-frente de cada fila con la altura del relieve para separar base, cuerpo y labio.
- Añade distribuciones específicas para paneles, anillos, cadenas, espirales, celdas, costillas, coronas y demás figuras.
- Reduce el coste de Exposición, Cavidades, Bordes y Curvatura mediante un índice hash de posiciones.
- Evita crear el índice geométrico cuando la métrica seleccionada no lo necesita.
- Aumenta la precisión del marco usado para deformaciones continuas mediante muestras cada `0.75` bloques.
- Amplía dinámicamente la reconstrucción entre vóxeles cuando la escala supera `100%`, conservando mejor escamas y detalles.
- Calcula dinámicamente la altura desplazable del panel Material según paletas y métricas disponibles.
- Mantiene el holograma activo al cerrar `K` y continúa actualizándolo al añadir puntos mediante `P`.
- Desplaza las caras del holograma ligeramente fuera del bloque para que siga visible después de construir.
- Conserva sin cambios `ShapeGenerator` y `SampledPath`; las figuras neutrales mantienen sus coordenadas originales.

## 4.2.0-alpha.2

- Fija el holograma al mundo componiendo la matriz de cámara completa antes de dibujar cada sección.
- Evita que el holograma se desplace o rote con la vista del jugador.
- Conserva los puntos después de Build; ahora solo se eliminan mediante **Limpiar** o `/komaedit path clear`.
- Renombra visualmente Ritmo como **Fusión procedural** para describir mejor su función.
- Mantiene completa la figura principal y superpone una figura secundaria cada N tramos.
- Deforma por separado la figura principal y la secundaria antes de fusionarlas.
- Evita conexiones cruzadas entre ambas geometrías cuando se usa giro, torsión, escala o espaciado variable.
- Mantiene intactos los algoritmos y coordenadas de todas las figuras base.

## 4.2.0-alpha.1

- Añade un holograma 3D de bloques que reproduce la geometría futura con el mismo motor usado por Build.
- Actualiza el holograma automáticamente al cambiar puntos, figura, escala, separación, grosor, giro o deformadores.
- Sustituye partículas por mallas persistentes agrupadas en secciones de 16×16×16 bloques.
- Renderiza únicamente caras expuestas, aplica distancia y frustum culling y limita las subidas a GPU por frame.
- Calcula la geometría en un worker de baja prioridad con debounce, generaciones cancelables y descarte de resultados obsoletos.
- Añade cancelación cooperativa a las operaciones geométricas largas sin cambiar las coordenadas generadas.
- Libera mallas y memoria al construir, cerrar el Studio o desconectarse del mundo.
- Evita regenerar el holograma mientras Build consume y reinicia la trayectoria.
- Conserva la colocación asíncrona, compacta y por lotes de las versiones anteriores.
- Surface y Kitbash muestran la figura base y se identifican como ajustes completados por el servidor al construir.

## 4.1.0-alpha.3

- Corrige la reutilización accidental de puntos entre builds consecutivos.
- Cada Build captura y consume su trayectoria antes de comenzar el siguiente brush.
- Evita que una figura nueva conecte su primer punto con el último recorrido de la figura anterior.
- Limpia también el estado abierto/cerrado del path y actualiza inmediatamente el holograma.
- Renombra `Modificar` como `Deformar` y aclara que es una función opcional que no edita los puntos del recorrido.
- Mantiene intactas las fórmulas originales de todas las figuras y escamas.

## 4.1.0-alpha.2

- Impide que una figura nueva herede opciones, ritmo, acento, taper o torsión de la figura anterior.
- Restablece automáticamente los modificadores cuando se cambia de figura.
- Añade el botón `Restablecer modificadores` y una explicación visible dentro del panel Modificar.
- Sustituye la generación por segmentos independientes por una deformación continua de una sola geometría.
- Evita reinicios de fase, nudos gigantes y acumulaciones en uniones de cadenas, anillos y patrones repetidos.
- Convierte el ritmo procedural en una mezcla por ventanas sobre geometrías completas, con transición en los bordes.
- Limpia recetas, instancias y borradores al entrar o salir del mundo.
- Mantiene los paquetes `.krecipe` exportados como la única persistencia intencional.
- Conserva sin cambios el generador original de escamas y todas las figuras base.

## 4.1.0-alpha.1

- Añade curvas independientes de escala y espaciado, easing y torsión acumulada por receta.
- Añade ritmo procedural para alternar una figura de acento a intervalos configurables.
- Añade conformado del path a suelo, techo o superficie cercana con alcance y offset.
- Añade sockets Kitbash basados en el clipboard de WorldEdit, orientados con la trayectoria.
- Añade métricas de material por exposición, cavidad, bordes, curvatura y ruido orgánico.
- Añade QA geométrico opcional para cerrar microhuecos y eliminar fragmentos aislados.
- Añade instancias persistentes que pueden actualizarse o eliminarse desde Procedural Studio.
- Protege los bloques editados manualmente al actualizar o retirar una instancia.
- Captura los estados colocados progresivamente durante el trabajo por lotes para evitar pausas finales.
- Añade exportación e importación de recetas mediante paquetes `.krecipe`.
- Migra el formato de receta a versión 2 manteniendo lectura de recetas versión 1.
- Mantiene intactos `ShapeGenerator`, las escamas originales y el resto de figuras existentes.

## 4.0.0-alpha.3

- Integra las coordenadas del path en una tarjeta compacta específica del Procedural Studio.
- Evita solapamientos con la barra superior, la paleta lateral y el inspector redimensionable.
- Prioriza los nodos recientes y resume los anteriores cuando la trayectoria es larga.

## 4.0.0-alpha.2

- Elimina por completo el shader de desenfoque del Procedural Studio.
- Mantiene el mundo y todos los controles nítidos mientras el panel está abierto.

## 4.0.0-alpha.1

- Añade el modelo versionado `ProceduralRecipe`.
- Añade guardado atómico y catálogo de recetas por jugador.
- Añade protocolo cliente/servidor para construir, guardar, eliminar y sincronizar recetas.
- Añade material sólido o degradado aplicado directamente al plan geométrico.
- Sustituye el acceso de `K` por Procedural Studio, una interfaz flotante, redimensionable y responsive.
- Añade biblioteca completa y buscable de bloques.
- Conserva sin cambios todas las geometrías existentes, incluido el comportamiento especial de escamas.
- Mantiene cálculo asíncrono, colocación por lotes e historial WorldEdit.
