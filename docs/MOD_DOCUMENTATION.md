# Documentación del MOD

Este documento es el catálogo funcional oficial de KomaEdit. Cada función nueva o cambio visible para el jugador debe añadirse aquí en el mismo Pull Request que introduce el código.

## Procedural Studio

Procedural Studio se abre con `K` y permite construir mientras el mundo continúa visible y activo. La interfaz ofrece categorías para trayectorias, figuras, estructuras, modificadores, materiales, recetas e instancias.

- Los paneles son desplazables y se adaptan a resoluciones pequeñas.
- El catálogo de materiales utiliza todos los bloques registrados por Minecraft y los mods cargados.
- La cámara puede desplazarse mientras el Studio permanece abierto.
- `Ctrl+Z`, `Ctrl+Y` y `Ctrl+Shift+Z` utilizan el historial de WorldEdit.
- La navegación principal, estados de trayectoria y acciones esenciales están disponibles en español e inglés mediante el idioma seleccionado en Minecraft.

## Trayectorias y hologramas

Las trayectorias se crean con puntos colocados mediante `P` o desde Procedural Studio. Los nodos pueden moverse con el gizmo XYZ y la geometría se recalcula sin modificar el mundo.

- El raycast alcanza hasta 4096 bloques cargados.
- El holograma representa las posiciones exactas de la futura construcción.
- La previsualización permanece visible al cerrar la interfaz.
- El cálculo se ejecuta fuera del hilo de renderizado, admite cancelación y conserva temporalmente el último resultado válido.

## Figuras procedurales

Las figuras convierten una trayectoria tridimensional en superficies o volúmenes repetibles. Actualmente se incluyen:

- Chainlink
- Scales
- Spiked Scales
- Beads
- Coil
- Rayos ramificados
- Crowns
- Honeycomb
- Red (fishnet)
- Oscillate
- Ribloops
- Rings
- Tiles
- Hexarmor
- Voronoi
- Knots
- DNA
- Tendrils
- Mandalas tridimensionales
- Nubes orgánicas
- Telarañas adaptativas
- Perfil 2D (espacio de tubo)
- Trenza (espacio de tubo)
- Tubo erosionado (espacio de tubo)

Cada figura conserva su gramática propia y puede combinar radio, espaciado, grosor, giro y materiales sin reutilizar estado residual de una figura anterior.

Chainlink, Rings, Beads, Oscillate, Red, Honeycomb, Knots y Coil pueden generarse además con el [motor exacto](#motor-exacto-de-las-figuras-clásicas), que las evalúa en espacio de tubo sin huecos.

## Red

### Objetivo y acceso

La figura **Red** (`fishnet`) envuelve el recorrido con dos familias de hilos helicoidales que giran en sentidos contrarios a ±45°, de modo que las celdas salen cuadradas (rombos vistos de lado) a cualquier escala. Se elige en **Figuras** del Studio o con el comando general:

```text
/komaedit build fishnet <patrón> <escala> <espaciado 2–8> <grosor 1–4> <giro -180–180>
```

También se acepta `net`, `red` o `malla`.

### Parámetros y comportamiento

- **Escala:** radio del cilindro sobre el que corren los hilos (`6` por defecto).
- **Espaciado:** ancho de celda; hay un hilo por familia cada `1,2 × espaciado` bloques de perímetro, entre `3` y `24` hilos.
- **Grosor:** diámetro de los hilos, como en el resto de figuras de alambre.
- **Giro:** desplaza el arranque de los hilos alrededor del eje.
- Los hilos dan una vuelta completa cada perímetro recorrido; con el motor clásico se trazan con pinceles y con el exacto salen en una sola pieza.

## Rayos ramificados

### Objetivo y acceso

La figura **Rayos** crea descargas tridimensionales quebradas entre los puntos asignados. Puede seleccionarse desde **Figuras** en Procedural Studio o ejecutarse mediante:

```text
/komaedit lightning <amplitud 2–32> <ramificación 2–8> [patrón]
/komaedit rayos <amplitud 2–32> <ramificación 2–8> [patrón]
```

El comando general también está disponible:

```text
/komaedit build lightning <patrón> <amplitud> <ramificación 2–8> <grosor 1–4> <caos -180–180>
```

### Comportamiento

- Cada par de puntos consecutivos se conecta con un núcleo quebrado y continuo.
- El desplazamiento se obtiene de una semilla formada por las coordenadas y el orden de los puntos. No utiliza aleatoriedad temporal: holograma, Build y actualizaciones producen el mismo resultado.
- Puntos adicionales crean nuevos tramos con una distribución propia, pero todos los anclajes permanecen exactamente en su posición.
- Las ramas nacen sobre el núcleo y se alejan lateralmente. Con ramificación alta algunas ramas producen una segunda bifurcación.
- Una trayectoria cerrada conecta el último punto con el primero y convierte las ramas en una corona exterior. El centro no se rellena.
- El modo circular orienta parte de las ramas fuera del plano para obtener lenguas similares a electricidad o fuego.

### Parámetros

- **Amplitud:** `2–32`; controla cuánto se separan los quiebres del segmento recto.
- **Ramificación:** `2–8`; controla densidad de ramas y posibilidad de bifurcaciones secundarias.
- **Grosor núcleo:** `1–4`; modifica el diámetro del rayo principal. Las ramas conservan un grosor menor.
- **Caos 3D:** `-180–180`; su magnitud aumenta la profundidad tridimensional y su signo invierte el lado principal del desplazamiento.

### Círculo hueco automático

```text
/komaedit lightningcircle <radio 4–128> <ramificación 2–8> [patrón]
/komaedit circulorayos <radio 4–128> <ramificación 2–8> [patrón]
```

- El círculo se centra en la posición actual del jugador y utiliza hasta `32` anclajes.
- El radio del comando define el tamaño del aro; la amplitud eléctrica se calcula proporcionalmente y se limita a un rango seguro.
- El recorrido queda guardado como trayectoria cerrada para poder editar sus puntos, cambiar materiales o reconstruirlo desde el Studio.

### Materiales

- El patrón predeterminado es `45%light_blue_concrete,25%white_concrete,20%sea_lantern,10%verdant_froglight`.
- Acepta cualquier bloque, patrón WorldEdit o degradado del Studio.
- Para fuego puede utilizarse `40%magma_block,30%orange_concrete,20%shroomlight,10%yellow_concrete`.
- **Patrón figura** concentra los extremos de la paleta en ramas, zonas expuestas y descargas alejadas del núcleo.

### Rendimiento y limitaciones

- La cantidad de vértices depende de la longitud y la amplitud; cada segmento se limita a `512` vértices.
- Cada tramo admite hasta `128` ramas antes del límite global de geometría.
- El generador respeta los límites generales de `32` puntos, `4096` bloques de trayectoria y `1 500 000` bloques generados.
- Toda la geometría se calcula fuera del hilo principal, admite cancelación y se deduplica mientras se crea.
- El aro automático es horizontal. Para un portal vertical, marque manualmente los puntos en un plano vertical y cierre la trayectoria.

## Telarañas adaptativas

### Objetivo y acceso

La figura **Telaraña** genera tejidos tensados directamente desde los puntos de la trayectoria. Se selecciona desde **Figuras** en Procedural Studio o mediante:

```text
/komaedit web <elasticidad> <separación 2–8> [patrón]
```

También está disponible con el comando general:

```text
/komaedit build web <patrón> <elasticidad> <separación 2–8> <grosor 1–4> <giro -180–180>
```

### Comportamiento

- Con exactamente dos puntos, P1 y P2 forman dos nudos gruesos conectados por una hebra fina. El grosor se reduce progresivamente desde ambos extremos y recupera volumen al acercarse al otro anclaje.
- El puente central conserva una comba y una ondulación ligera para evitar una línea completamente rígida.
- El modo de dos puntos nunca cierra una elipse ni genera anillos alrededor del eje.
- Con tres o más puntos únicos, cada punto actúa como anclaje exterior y crea un radio recto desde el centro.
- Entre radios consecutivos se generan curvas cuadráticas hundidas hacia el centro, reproduciendo las vueltas cóncavas de una telaraña clásica.
- Los anclajes se ordenan alrededor de su centro geométrico antes de conectar los anillos, evitando cruces producidos por un orden irregular de captura.
- Los radios interiores pueden retorcerse suavemente; los anclajes finales siempre permanecen en las coordenadas marcadas.
- El holograma y la construcción usan el mismo generador de vóxeles.

### Parámetros y materiales

- **Elasticidad:** controla la comba del puente fino con dos puntos y la profundidad de los arcos cóncavos con múltiples anclajes.
- **Separación hilos:** `2` produce vueltas próximas y `8` las distribuye a mayor distancia.
- **Grosor:** controla el diámetro del puente central, radios y vueltas; los nudos de P1 y P2 se engrosan automáticamente según la elasticidad.
- **Relieve 3D:** entre `-180` y `180` grados; orienta la comba del puente o levanta progresivamente los arcos fuera del plano sin desplazar los puntos exteriores.
- El patrón predeterminado es `white_wool`; se acepta cualquier bloque, patrón WorldEdit o degradado disponible en el Studio.

### Rendimiento y limitaciones

- La cantidad de anillos se calcula a partir del radio medio y se limita a 64 para mantener un coste predecible.
- Los puntos repetidos a menos de un cuarto de bloque se consolidan en un único anclaje.
- Si tres o más puntos son prácticamente colineales, el generador utiliza los dos extremos para desplegar automáticamente una malla radial en un plano estable.
- Una distribución muy irregular o no coplanar produce una telaraña tridimensional tensada, no una circunferencia plana.

## Figuras de espacio de tubo

### Objetivo y acceso

**Perfil 2D** (`sweep`), **Trenza** (`braid`), **Tubo erosionado** (`noisetube`) y **Expresión** (`expression`, en su propia sección) se generan con un motor distinto al de las demás figuras: en lugar de estampar pinceles a lo largo de la curva, recorre los bloques cercanos al eje y evalúa en cada uno una función implícita de la figura. El resultado es estanco por construcción, sin puentes ni reparación posterior. Ocho figuras clásicas pueden usar también este motor con la opción `engine:exact` (ver [Motor exacto de las figuras clásicas](#motor-exacto-de-las-figuras-clásicas)).

- Studio: aparecen en **Figuras**. Las tres añaden filas de **Extremos**, **Normales** y **Piezas** (con **Cobertura** cuando hay piezas); **Perfil 2D** añade además **Perfil** y **Relleno**.
- Comandos rápidos:

```text
/komaedit sweep <perfil> <radios> [patrón] [banderas]
/komaedit perfil <perfil> <radios> [patrón] [banderas]
/komaedit braid <radios> [patrón] [banderas]
/komaedit trenza <radios> [patrón] [banderas]
/komaedit noisetube <radios> [patrón] [banderas]
/komaedit erosion <radios> [patrón] [banderas]
```

### Comportamiento

- **Espacio de tubo:** cada bloque se localiza por su punto más cercano del eje. De ahí salen `x` e `y` sobre la sección, en radios (de `-1` a `1`), y `z` a lo largo del recorrido, que avanza un radio por cada radio de longitud. Las figuras se definen en ese espacio, así que crecen, giran y se estiran con el tubo.
- **Curva Kochanek–Bartels:** la trayectoria pasa exactamente por todos los puntos. Con tensión, sesgo y continuidad a `0` equivale a un Catmull-Rom; las tangentes se corrigen por longitud de cuerda para no formar bucles con puntos desigualmente separados.
- **Marcos sin giro espurio:** la sección se transporta con marcos de mínima rotación (doble reflexión). En trayectorias cerradas el desfase de cierre se reparte por todo el lazo para que el empalme no se note.
- **Normales:** `consistent` rueda con suavidad en las curvas; `horizontal` mantiene la cara superior mirando al cielo; `upright` alinea el eje vertical de la sección con el eje Y del mundo.
- **Extremos** (solo trayectorias abiertas): `flat` corta en seco, `soft` afila el último radio hacia dentro, `round` añade una cúpula, `spike` un cono de dos radios y `cube` un remate cuadrado.
- **Hueco:** la pared se obtiene por morfología (bloque dentro con algún vecino fuera a la distancia de la pared), así que queda cerrada aunque la superficie vaya en diagonal. Con extremo `flat` el tubo queda abierto como una tubería.
- **Modificadores exactos:** escala, espaciado y torsión de la sección Deformar se aplican dentro del espacio de tubo al evaluar cada bloque, sin deformar bloques ya generados ni puentear huecos.
- **Determinismo:** el ruido del tubo erosionado usa una semilla derivada de los puntos; holograma y Build coinciden.
- **Losas y escaleras:** opcionalmente la superficie se termina con piezas del mismo material; ver [Losas y escaleras en la superficie](#losas-y-escaleras-en-la-superficie).

### Parámetros

| Control / bandera | Opción de receta | Efecto |
| --- | --- | --- |
| Radio / `<radios>` | radio + `k0`…`k5`, `p1`…`p4` | Radio base. Con varios valores (`2,10,2`) el grosor sigue esos fotogramas con interpolación monótona; `2,0.3:12,2` fija la posición del intermedio. Sin comillas: `2_10_2`. |
| Lados / puntas | `sides` | Lados del polígono, puntas de la estrella, pétalos de la flor o hebras de la cuerda (Studio 2–8, comando hasta 32 con `star12`). |
| Pared (hueco), `-hollow <n>` | `hollow`, `wall` | Grosor de pared en bloques cuando el relleno es **Hueco**. |
| Giro, `-r <a>[,<b>]` | `angle`, `rollend` | Giro inicial de la sección; con dos valores gira de `a` a `b` a lo largo del recorrido. |
| `-t <grados>` | `twist` | Torsión por diámetro, como una cuerda: tras recorrer un diámetro la sección ha girado esos grados. La cuerda usa 90 por defecto. |
| `-e <modo>` / Extremos | `end` | `flat`, `soft`, `spike`, `round`, `cube`. |
| `-n <modo>` / Normales | `normal` | `consistent`, `horizontal`, `upright`. |
| `-p t:b:c` | `tension`, `bias`, `continuity` | Kochanek–Bartels, cada valor entre `-1` y `1`. |
| `-q <calidad>` | `quality` | `fast` (centro del bloque), `balanced` (rescata detalles finos con seis muestras), `high` (27 muestras, cobertura ≥ 25 %). |
| `-s <factor>` | `stretch` | Estira o comprime el patrón a lo largo del recorrido. |
| `-d <0–1>` | `depthmultiplier` | Profundidad de las estrellas y flores, o de la erosión. |
| `-a <0.1–1>` | `aspect` | Proporción de elipse y rectángulo. |
| `-x <exponente>` | `exponent` | Forma del perfil redondeado (4 = squircle). |
| `-k <2–8>` | `strands` | Hebras de la trenza. |
| `-f <frecuencia>` | `frequency` | Tamaño de las vetas del tubo erosionado. |
| `-fill` | `filled` | Rellena el alma de la cuerda. |
| `-w <modo>` / Piezas | `smooth` | `none` (bloques enteros), `slabs` (losas) o `stairs` (losas y escaleras). |
| `-c <0–1>` / Cobertura | `coverage` | Cuánta superficie se cubre con piezas: `0` solo bloques enteros, `0.5` (por defecto) la pieza que mejor encaja, `1` toda la superficie que no esté entera. `-c` sin `-w` activa `stairs`. |

Perfiles: `circle`, `square`, `diamond`, `rounded`, `polygon`, `star`, `flower`, `rope`, `ellipse` y `rectangle` (también en español: `circulo`, `estrella`, `cuerda`…). Un número al final fija los lados: `polygon6`, `star5`, `rope4`.

En recetas y expresiones también se aceptan nombres: `sweep(profile:star,sides:7,end:round,normal:horizontal,hollow:true)` o `braid(smooth:stairs,coverage:0.7)`. En el comando general la expresión va entre comillas: `/komaedit build "sweep(profile:star,end:round)" stone 8`.

Ejemplos:

```text
/komaedit sweep circle 8
/komaedit sweep star5 "2,0.3:12,2" polished_andesite -e spike -t 30
/komaedit sweep rope3 6 oak_planks
/komaedit sweep polygon6 10 deepslate_tiles -hollow 2 -n horizontal
/komaedit braid 7 stripped_birch_wood -k 4
/komaedit noisetube 2_12_4 60%stone,25%andesite,15%cobblestone -e round
/komaedit sweep circle 9 stone_bricks -w stairs
/komaedit sweep ellipse 12 smooth_stone -a 0.4 -n horizontal -w slabs -c 0.7
```

### Losas y escaleras en la superficie

Los tubos hechos solo con bloques enteros dejan escalones en la superficie. Con **Piezas** (`-w`) los bloques del borde que la figura solo ocupa en parte se sustituyen por losas o escaleras orientadas del mismo material, y los huecos vacíos que la figura casi llena reciben una pieza.

- **Medición:** cada bloque de la superficie, y cada hueco vacío pegado a ella, se divide en 8 octantes. La misma función implícita que decide la figura mide qué fracción de cada octante queda dentro: el centro de cada octante y, si el bloque está a medias, cuatro muestras más en damero (con calidad `fast`, solo el centro). Para no localizar el eje en cada muestra, se localiza el centro del bloque y tres puntos a un cuarto de bloque y el resto se interpola; cerca de los extremos, en la costura de los lazos o donde el eje salta, cada muestra se localiza por separado.
- **Elección:** las piezas candidatas son las dos losas y, con `stairs`, las escaleras rectas en las cuatro orientaciones, apoyadas o colgadas. Gana la que menos se aleja de la ocupación medida, y sustituye al bloque (o rellena el hueco) si mejora el encaje en más que el umbral de la cobertura. Con `0.5` basta con encajar mejor; hacia `0` hace falta tanta ventaja que no se usa ninguna; con `1` se aceptan piezas hasta dos octantes peores.
- **Esquinas como en vanilla:** al colocar bloques, Minecraft recalcula la forma de cada escalera según las escaleras de alrededor. El plan ya trae esa forma (esquinas interiores y exteriores) calculada con la misma regla, así el mundo queda igual que el holograma.
- **Material:** la pieza sale del bloque que elige el patrón en esa posición, también con patrones aleatorios, degradados y **Patrón figura**. La familia se busca por nombre (`stone` → `stone_slab`/`stone_stairs`, `stone_bricks` → `stone_brick_*`, `oak_planks` o `oak_log` → `oak_*`, `quartz_pillar` → `quartz_*`, cobre → cobre cortado) con una tabla para los casos sin convención (`deepslate` → `cobbled_deepslate`, `smooth_stone` → losa propia y escalera de `stone`, `end_stone` → `end_stone_brick`…). Funciona igual con bloques de otros mods que sigan la convención.
- **Sin familia:** si el bloque no tiene losas o escaleras (hormigón, lana, basalto…), se usa la losa o escalera registrada con el color de mapa más parecido, solo si el color se parece de verdad. Si tampoco hay, el bloque de la figura se queda entero y la pieza que iba en un hueco vacío no se coloca.
- **No se convierten:** cristal, hojas y demás bloques no opacos, bloques con entidad (cofres, hornos…) y patrones que ya son losas o escaleras (estos solo se orientan).
- **Holograma:** dibuja las losas y escaleras con su forma real, por medios bloques, y el estado indica cuántas hay.

Limitaciones:

- En tubos huecos no se añaden piezas fuera y la capa que toca el interior se queda entera, para que ninguna losa abra la pared.
- Solo se tocan bloques que coloca la propia figura de tubo: la figura de acento del ritmo y los bloques que añade **Cerrar huecos** se quedan enteros.
- Las escaleras son de una sola pieza por bloque: superficies muy finas (hebras de trenza de 1–2 bloques) pueden quedar con escalones de media altura.
- Si junto al tubo ya había escaleras en el mundo, Minecraft puede doblar las del borde hacia ellas.

### Materiales

Con **Patrón figura** las figuras de tubo usan su propio valor implícito: en los perfiles va del núcleo (`0`) al borde (`1`), y en cuerdas y trenzas cada hebra ocupa su tramo de la paleta, de modo que una paleta de tres colores pinta tres hebras distintas. **Relieve**, **Frente / reverso** y **Filas alternas** funcionan igual que en el resto de figuras.

### Rendimiento y limitaciones

- Solo se evalúan ladrillos de 8×8×8 bloques que tocan el tubo; un árbol de esferas sobre el eje localiza el punto más cercano en tiempo logarítmico y parte del resultado del bloque anterior.
- Un tubo de radio 14 y 360 bloques de recorrido (~220 000 bloques) se calcula en menos de un segundo en un worker.
- El volumen candidato se limita a 64 millones de bloques y el resultado al límite general de 1 500 000.
- Con normales `upright` las secciones se inclinan en tramos empinados y pueden solaparse; en tramos verticales conviene `consistent`.
- Si una curva se cierra más que el propio radio, el tubo se pliega por dentro como en cualquier barrido: separa más los puntos o reduce el radio.
- El tubo erosionado conserva solo la pieza conectada al núcleo, sin fragmentos flotantes.
- Las losas y escaleras solo miden la superficie. En un tubo de radio 14 y unos 360 bloques de recorrido (~230 000 bloques) añaden unos 0,3 s a los 0,6 s de la figura en calidad `balanced`; en radio 6, menos de 0,1 s. Solo cuentan como bloques nuevos las piezas que rellenan huecos.

## Motor exacto de las figuras clásicas

### Objetivo y acceso

Las figuras **Chainlink**, **Rings**, **Beads**, **Oscillate**, **Red**, **Honeycomb**, **Knots** y **Coil** se generan por defecto estampando pinceles a lo largo de la curva (motor **clásico**). El motor **exacto** reimplementa cada una como función implícita en [espacio de tubo](#figuras-de-espacio-de-tubo) y la evalúa con el mismo voxelizador inverso que `sweep`, `braid` y `noisetube`: mide la distancia de cada bloque al hilo, al anillo o a la cinta, así que el resultado no tiene huecos aunque el recorrido vaya en diagonal.

- **Studio:** estas figuras muestran la fila **Motor** (`Clásico` / `Exacto`). Con **Exacto** aparecen también **Extremos**, **Normales** y **Piezas** (con **Cobertura** cuando hay piezas).
- **Recetas y expresiones:** opción `engine` (`0` clásico, `1` exacto) o su nombre: `engine:exact`, `engine:classic` (también `motor:exacto`).
- **Comando general:** la figura admite una expresión entre comillas:

```text
/komaedit build "coil(engine:exact)" stone 8 5 2 0
/komaedit build "chainlink(engine:exact,end:round,twist:45)" iron_block 6
/komaedit build "beads(engine:exact,k0:40,k1:100,k2:40)" glass 5
/komaedit build "fishnet(engine:exact,normal:horizontal)" dark_oak_fence 10 6 1
```

El motor por defecto es el clásico: las recetas, instancias e instantáneas existentes no cambian de aspecto.

### Comportamiento

- **Mismas proporciones:** radio, espaciado, grosor y giro se leen con las mismas fórmulas que el motor clásico, igual que `extrusion`, `gap`, `stretch` (longitud del eslabón), `majorexponent`, `minorexponent` y `thickness`. El hilo de grosor `n` mide lo mismo que el pincel clásico (`n − 0,65` de radio).
- **Sin huecos:** el alma de cada hilo queda cubierta en cualquier dirección y la cinta de Oscillate no se puede atravesar. Por eso hilos y cintas tienen un radio (o semigrosor) mínimo de `0,87` bloques: con grosor `1` salen algo más gruesos que en el motor clásico.
- **Hereda el espacio de tubo:** extremos (`end`), normales (`normal`), radios por fotogramas (`k0`…`k5`, `p1`…`p4`), torsión (`twist`, `rollend`), curva Kochanek–Bartels (`tension`, `bias`, `continuity`), calidad (`quality`), losas y escaleras en la superficie (`smooth`, `coverage`) y la escala, espaciado y torsión de la sección Deformar, que se aplican al evaluar cada bloque.
- **Espaciado sin aplastar:** el espaciado de la curva de modificadores y `stretch` separan los motivos (eslabones, anillos, cuentas, nudos) o abren la hélice sin aplastar el alambre. En Chainlink `stretch` conserva su significado clásico y alarga cada eslabón.
- **Extremos:** con `flat` el primer y el último motivo salen enteros, como en el motor clásico; las figuras continuas se cortan en el plano del extremo. `round`, `spike`, `soft` y `cube` actúan sobre la figura como en `sweep`.
- **Lazos cerrados:** el paso se ajusta para que quepa un número entero de motivos; en Chainlink y Honeycomb el número es par para que la alternancia empalme sin costura.
- **Por figura:**
  - **Chainlink:** eslabones superelípticos alternados 90°; `minorexponent` da además la sección del alambre (`2` redonda, más alto casi cuadrada).
  - **Rings:** supertoros con la corona y el fondo del motor clásico.
  - **Beads:** cáscaras de `grosor + 0,45` bloques como las clásicas; `hollow:false` las rellena.
  - **Oscillate:** cinta de un diámetro que gira una vuelta por longitud de onda mientras su centro oscila `0,42` radios.
  - **Red, Honeycomb, Knots y Coil:** hilos con la misma trayectoria que en el motor clásico (hélices, hexágonos con puntales, tréboles 2:3 girados `0,47 rad` entre estaciones y una hélice).
- **Determinismo:** holograma y Build producen los mismos bloques.

### Materiales

Con **Patrón figura** el motor exacto usa el valor implícito de cada figura: en Chainlink cada orientación de eslabón ocupa una mitad de la paleta, en Beads se alternan las mitades por cuenta, en Red cada familia de hilos tiene su mitad, en Honeycomb los anillos usan una mitad y los puntales la otra, y dentro de cada franja el color va del alma (`0`) a la superficie (`1`). En Rings, Knots y Coil va del alma a la superficie del hilo y en Oscillate del centro al borde de la cinta.

### Rendimiento y limitaciones

- El coste depende del volumen del tubo que envuelve la figura, no del número de estampados. Las figuras miden su holgura hasta la superficie y el voxelizador no supermuestrea los bloques claramente vacíos.
- Con radio `16` y 480 bloques de recorrido cada figura exacta tarda entre `0,5` y `1,4` s en un worker; la clásica, milisegundos. Con radio `8` rondan `0,3`–`0,6` s.
- El volumen suele quedar entre `0,9` y `2,5` veces el del clásico: los hilos de grosor `1` engordan para no dejar huecos y las figuras clásicas que no cerraban (Red, Honeycomb, Chainlink) quedan ahora unidas.
- Como en cualquier barrido, si la curva se cierra más que el alcance de la figura, el lado interior se pliega: separa más los puntos o reduce la escala.
- `cube` añade un remate macizo en los extremos de cualquier figura.

## Doblar estructuras del portapapeles

### Objetivo y acceso

Curva un módulo copiado con WorldEdit (un tramo de muro, puente, calzada, vía o valla) a lo largo de la trayectoria: o lo repite copia tras copia, o estira una sola copia por todo el recorrido. A diferencia de los sockets Kitbash normales, que pegan el módulo entero en puntos sueltos, aquí el módulo se dobla con la curva.

- Comando rápido, solo la estructura:

```text
/komaedit bend [repeat|stretch] [banderas]
/komaedit doblar [repetir|estirar] [banderas]
```

- En recetas: en **Modificadores → Kitbash**, la fila **Colocar** tiene dos modos nuevos, **Doblar** (repetir) y **Estirar**, que se suman a la figura de la receta y quedan dentro de la instancia, así que se pueden actualizar o retirar.

### Comportamiento

- Se usa el portapapeles de WorldEdit (`//copy`). Su origen, el bloque donde estabas al copiar, es el que recorre la trayectoria.
- **Repetir:** en un recorrido abierto caben tantas copias enteras como permita la longitud, centradas, sin trozos cortados. En uno cerrado, las copias se ajustan un poco para que el lazo empalme sin costura.
- **Estirar:** una sola copia ocupa todo el recorrido.
- Igual que las figuras de tubo, se trabaja al revés: cada bloque del mundo cercano al eje pregunta qué bloque del módulo le toca. Por fuera de las curvas el módulo se estira sin abrir huecos; por dentro se comprime y puede perder algún detalle fino.
- **Normales `upright` (por defecto):** el módulo se asocia a la vertical de cada punto del recorrido, así que en una cuesta los postes y muros quedan a plomo y la altura se mide en bloques del mundo. Con `horizontal` o `consistent` la sección se inclina con la pendiente.
- Escaleras, troncos, vallas y demás bloques con dirección se giran en cuartos de vuelta para seguir la curva.

### Parámetros

| Bandera | Efecto |
| --- | --- |
| `repeat` / `stretch` | Repetir copias (por defecto) o estirar una sola. |
| `-a <x\|z\|-x\|-z>` | Dirección del portapapeles que avanza con el recorrido. Por defecto, el lado horizontal más largo; el comando dice cuál ha usado. Con módulos más anchos que largos (un tramo de puente, por ejemplo) hay que indicarla. |
| `-g <bloques>` | Hueco entre copias al repetir; admite valores negativos para solaparlas. |
| `-y <bloques>` | Sube o baja el módulo respecto al recorrido. |
| `-n <modo>` | `upright`, `horizontal` o `consistent`. |
| `-p t:b:c` | Tensión, sesgo y continuidad de la curva Kochanek–Bartels. |

Ejemplos:

```text
/komaedit bend
/komaedit bend -a x -g 2
/komaedit bend stretch -n horizontal
/komaedit doblar repetir -a z -y -1
```

### Rendimiento y limitaciones

- El portapapeles admite hasta 100 000 bloques y como mucho 128 bloques de alto o de lado desde el origen.
- Un tramo de puente de 7×3×6 doblado sobre 85 bloques de curva (unos 850 bloques) se calcula en menos de 0,1 s en un worker.
- Los giros de bloques van en cuartos de vuelta: una escalera no puede quedar a 30°.
- El holograma del Studio no muestra el módulo doblado (igual que el resto de Kitbash): se calcula en el servidor al pulsar Build.

## Figura Expresión

### Objetivo y acceso

**Expresión** (`expression`) construye la figura a partir de una fórmula escrita por el jugador y evaluada en el espacio de tubo: la misma curva, el mismo voxelizado estanco y las mismas opciones de extremos, normales, torsión, radios por fotogramas, hueco y calidad que **Perfil 2D**, pero la sección (y su evolución a lo largo del recorrido) la decide la fórmula.

- Studio: en **Figuras**, botón **Expresión**. La fila **Fórmula** recorre las fórmulas de serie; debajo, un campo muestra la fórmula activa (en gris si es de serie). Al pulsarlo se edita partiendo de la fórmula activa: **Intro** la aplica, **Esc** cancela, **Ctrl+V** pega y **Ctrl+Retroceso** la borra; pulsar fuera también la aplica. Mientras se escribe, una línea amarilla y el estado señalan el error y la posición; si la fórmula no compila no se aplica, y al volver a abrir el campo aparece lo escrito para corregirlo. Vaciar el campo, o elegir otra con la fila, vuelve a la de serie. **Repeticiones (n)** da valor a `n` y **Pared (hueco)** con **Relleno** vacía la figura como en Perfil 2D; **Extremos**, **Normales**, **Piezas** y **Cobertura** funcionan igual que en las demás figuras de tubo, también las losas y escaleras de la superficie.
- Comando rápido (también `/komaedit expresion`):

```text
/komaedit expr <radios> [banderas] [patrón] <fórmula…>
```

Las banderas van delante (`-e -n -t -r -p -q -s -hollow -seed -w -c`, las mismas de los tubos), después el patrón opcional y al final la fórmula, que puede llevar espacios. El patrón se reconoce compilando: si todo el texto es una fórmula válida no hay patrón; si no, la primera palabra es el patrón y el resto la fórmula. En lugar de una fórmula vale el nombre de una de serie, con `n` pegado si se quiere cambiar (`ventanas8`). Si la fórmula empieza por un signo menos, envuélvela en paréntesis para que no se lea como bandera.

En expresiones de figura (`/komaedit build`) la de serie se elige con `preset`: `expression(preset:lattice,end:round)`.

### Variables y lenguaje

| Variable | Significado |
| --- | --- |
| `x`, `y` | Posición en la sección, en radios, de `-1` a `1`. Fuera del cuadrado `[-1, 1]` no se evalúa nada. |
| `z` | Avance a lo largo del recorrido, en radios (empieza en `0`; con `-s` se estira o comprime). |
| `t` | Progreso a lo largo del recorrido, de `0` al principio a `1` al final. |
| `r`, `a` | Distancia al eje (`sqrt(x^2+y^2)`) y ángulo en radianes (`atan2(y, x)`, de `-pi` a `pi`). |
| `n` | Repeticiones: **Repeticiones (n)** del Studio o el número pegado a la fórmula de serie (2–32). |
| `pi`, `e` | Constantes. |

- **Salida:** un valor mayor que `0` coloca bloque; `0`, negativo o no numérico (`NaN`) lo deja vacío. Con **Patrón figura** el valor, limitado a `(0, 1]`, elige el tramo de la paleta, así que una fórmula puede pintar piezas distintas (`riel ? 1 : traviesa ? 0.4 : 0`).
- **Operadores:** `+ - * / % ^`, paréntesis, comparaciones `< <= > >= == !=` (devuelven `1` o `0`), lógicos `&& || !` (verdadero es mayor que `0`) y el ternario `condición ? sí : no`. `-x^2` es `-(x^2)` y `2^3^2` es `2^9`. `%` conserva el signo del dividendo, como en WorldEdit; `mod(a, b)` es siempre positivo.
- **Funciones:** `sin cos tan asin acos atan atan2 abs sqrt cbrt exp ln log log10 pow min max floor ceil round frac mod sign clamp hypot noise`. `min` y `max` admiten de 2 a 8 argumentos; `clamp(v, mín, máx)`; `noise(x, y, z)` es ruido de Perlin en `[0, 1]` con la semilla de la figura (`-seed`, o una derivada de los puntos).
- **Definiciones:** al principio se pueden fijar valores con `nombre = valor;` y usarlos después, como en `R=0.2; w=0.7; sqrt((abs(x)-w)^2+y^2) < R`. Las que solo dependen de números se calculan una vez al compilar. `r`, `a` y `n` se pueden redefinir; `x`, `y`, `z`, `t`, `pi`, `e` y las funciones no.
- **Mayúsculas:** los nombres las distinguen, como en WorldEdit: `R` y `r` son variables distintas, y `SIN(x)` no existe (el error sugiere `sin`).

Fórmulas de serie:

| Nombre | Alias | Figura |
| --- | --- | --- |
| `windows` | `ventanas` | Tubo con `n` ventanas por vuelta y suelo corrido. |
| `rails` | `railes`, `via` | Dos raíles sobre traviesas, como una vía. |
| `stripes` | `espiral`, `franjas` | Núcleo macizo con `n` franjas en espiral en relieve. |
| `lattice` | `celosia`, `rejilla` | Piel de barras cruzadas en diagonal, `n` por vuelta. |
| `ribs` | `costillas`, `anillos` | Anillos cada `6/n` radios unidos por una quilla. |

Ejemplos:

```text
/komaedit expr 8 ventanas
/komaedit expr 10 stone_bricks ventanas8
/komaedit expr 6 -e round polished_andesite r<1 && abs(sin(a*3+z)) > 0.4
/komaedit expr 2_9_2 -t 45 deepslate_tiles abs(x) + abs(y) < 1 && frac(z/2) < 0.7
/komaedit expr 12 R=0.2; w=0.7; sqrt((abs(x)-w)^2+y^2) < R || abs(y+0.6) < 0.1 && abs(x) < w
/komaedit expr 9 mossy_cobblestone r < 0.7 + 0.3*noise(x*2, y*2, z)
```

### Seguridad y errores

- La fórmula admite como máximo 256 caracteres, 16 definiciones, 40 niveles de anidamiento y 400 operaciones.
- No hay bucles, asignaciones fuera de las definiciones iniciales, funciones con efectos ni estado compartido: cada punto cuesta un número fijo de pasos y la misma fórmula da siempre la misma figura, en el holograma y en el Build.
- Los errores llegan en español y señalan la posición: `Fórmula, posición 10: Variable desconocida «foo»…`. Una fórmula inválida se rechaza al escribir el comando, al aplicarla en el Studio y al leer la receta; nunca llega al voxelizado.

### Recetas

La fórmula propia se guarda como texto en la receta (campo `formula`, formato de receta 4) y viaja por red con ella; la de serie se guarda como la opción numérica `preset`, así que una receta sin fórmula propia sigue usando solo opciones numéricas. Las recetas, instancias y paquetes `.krecipe` de formatos 1–3 se leen sin cambios (con la fórmula de serie), y no hace falta migrar nada en disco. Las versiones anteriores del mod no abren recetas de formato 4.

### Rendimiento y limitaciones

- La fórmula se compila una vez a un árbol con las constantes plegadas y cada worker la evalúa con su propio marco de variables: entre 10 y 60 ns por punto en las fórmulas habituales, poco frente a localizar el punto en el eje.
- **Alcance:** si la fórmula se limita a un radio (`r <= 1 && …`, `c - r`, `(r < c) * …`, también a través de definiciones y ternarios), el motor solo recorre hasta ese radio; si no, recorre hasta las esquinas del cuadrado (√2 radios), que cuesta más del doble. Empezar por `r <= 1 &&` cuando la figura cabe en el círculo la acelera sin cambiarla.
- Medido en un tubo de radio 10 sobre 360 bloques de recorrido: `r <= 1` tarda lo mismo que el perfil círculo (~0,36 s, 117 000 bloques); ventanas ~0,5 s y celosía ~0,6 s.
- No se usa el motor de expresiones de WorldEdit: en 7.3.8 cada evaluación consulta el reloj, busca variables en un mapa y crea objetos (150–335 ns por punto medidos, frente a 12–63 ns aquí con las mismas fórmulas), y su lenguaje permite bucles y búferes globales.
- Las piezas muy finas (menos de un bloque en el radio elegido) pueden perder continuidad; sube el radio o usa `-q high`.
- `x` e `y` se miden en radios: con radios por fotogramas la figura entera escala con el tubo.

## Hiedra viva

### Objetivo y acceso

Una enredadera que **crece de verdad sobre lo que ya está construido**: se pega a las caras de los bloques, trepa muros, entra en las jambas de las ventanas, cruza tejados, cuelga de aleros y dinteles, se ramifica y echa matas de hojas y flores. No se dibuja una figura: la forma sale de la construcción que tenga delante.

```text
/komaedit ivy [estilo] [banderas]
/komaedit hiedra [estilo] [banderas]
/komaedit ivy cancel
```

- **Semillas:** los puntos de trayectoria marcados con **P** (uno o varios, hasta 32). Si no hay ninguno, la celda delante de la cara del bloque que miras. Cada semilla busca la superficie más cercana en 4 bloques; las que no encuentran ninguna se saltan y se avisa.
- Permiso: `komaedit.build` o `komaedit.build.ivy`.
- Todo va a una sola edición por lotes: **//undo** la quita entera y `/komaedit job status|cancel` funciona como en el resto de figuras.

### Comportamiento

- Se captura el entorno de las semillas repartido por ticks (sin congelar el servidor) y el crecimiento se calcula en un worker.
- Cada semilla lanza varios tallos. Cada punta avanza bloque a bloque por las celdas de aire que tienen al lado una cara entera de un bloque (o, por encima, algo donde apoyar una alfombra). Elige el paso por su rumbo, con querencia a subir, una deriva aleatoria y menos ganas de pisar por donde ya pasó otra punta; nunca atraviesa un bloque ni se cuela entre dos que se tocan por la arista.
- Por el suelo se cansa el doble de rápido: busca muros, no alfombra el terreno.
- **Ramas:** en cada paso puede salir una rama de lado sobre la misma superficie, con parte de la energía que le queda a la punta, hasta cinco niveles.
- **Gravedad:** donde una enredadera llega al borde de abajo de una pared (un dintel, un alero, una cornisa) puede colgar una tira que hereda sus caras, como hace la enredadera de Minecraft al crecer hacia abajo. Si una punta se queda sin sitio al que agarrarse en un borde, cuelga lo que le queda. De los techos caen tiras de bayas brillantes en los estilos que las tienen.
- **Huecos cortos:** una punta puede cruzar por el aire hasta dos bloques de lado si al otro lado hay donde agarrarse; esos tramos son hojas.
- Al final cada celda se viste según sus apoyos: **enredadera** con las caras exactas de los bloques que la sostienen (incluido `up` bajo un techo), **alfombra de musgo** en lo horizontal y **matas de hojas** por ruido, más tupidas cerca de la raíz y donde se cruzan varias puntas. Las hojas van con `persistent=true`, así que no se secan.
- Solo ocupa celdas que eran aire y lo comprueba otra vez al colocar: si alguien ha construido ahí mientras crecía, esa celda se respeta.
- Con la misma semilla, el mismo sitio y los mismos parámetros sale la misma hiedra. Si no se fija `-seed`, se elige una y el chat la dice para poder repetirla.

### Estilos y materiales

| Estilo | Bloques |
| --- | --- |
| `hiedra` (por defecto) | Enredadera, hojas de roble y de azalea, alfombra de musgo. Bajo los techos, enredadera pegada arriba. |
| `florido` (`flores`) | Enredadera, hojas de azalea, mitad en flor, tiras de bayas brillantes con muchas bayas y alguna flor de esporas. |
| `selvatico` (`selva`) | Mucha hoja de jungla y de azalea, algo de flor, tiras colgantes más largas y pocas bayas. |
| `cueva` (`gruta`) | Liquen brillante mezclado con la enredadera y por el suelo, apenas hojas, tiras de bayas y flores de esporas bajo los techos. |

### Parámetros

| Bandera | Efecto |
| --- | --- |
| `[estilo]` o `-s <estilo>` | Paleta de bloques. |
| `-l <bloques>` | Largo de cada tallo principal, el vigor (4–200, por defecto 28). Las ramas salen con parte de él. |
| `-b <0–1>` | Cuánto se ramifica (por defecto 0,4). |
| `-g <0–1>` | Cuánto cuelga de bordes y techos y lo largas que son las tiras (por defecto 0,5; 0 no cuelga nada). |
| `-n <tallos>` | Tallos por semilla (1–8, por defecto 2). |
| `-seed <n>` | Semilla del crecimiento. |

También valen los nombres largos `-largo`, `-ramas`, `-cuelga`, `-tallos`, `-semilla` y `-estilo`.

Ejemplos:

```text
/komaedit ivy
/komaedit ivy florido -l 40 -g 0.8
/komaedit hiedra selvatico -l 80 -n 4 -b 0.7 -seed 12
/komaedit ivy cueva -g 1
```

### Rendimiento y limitaciones

- El crecimiento es rápido: en una casa de pruebas, 28 bloques de largo dan unos 250 bloques en 1 ms y el máximo (200 de largo, 8 tallos, ramas y cuelgue a 1) unos 3 700 bloques en 75 ms.
- Lo que más tarda es capturar el entorno: alrededor de 16 000 bloques por tick. Con el largo por defecto son unos 2 s; con largos grandes la captura llega al tope de 72 bloques de radio en horizontal y 48 en vertical (unos 6 s).
- Las semillas de una misma hiedra deben caber en unos 120 bloques en horizontal y 80 en vertical; para cubrir un pueblo, por partes.
- Tope de 200 000 bloques por hiedra (o el límite de operación del servidor, si es menor); al llegar se para y el chat lo avisa.
- Solo reconoce como apoyo las caras enteras, igual que la enredadera de vanilla: no trepa por el frente de escaleras, losas ni vallas, aunque sí tiende alfombra encima.
- La enredadera de vanilla se sigue extendiendo sola con los ticks aleatorios del mundo, como cualquier enredadera puesta a mano.
- No hay holograma previo ni entrada en el Studio: se coloca directamente y se deshace con //undo.

## Modificadores

Los modificadores transforman de manera continua la figura seleccionada:

- Escala inicial y final.
- Espaciado inicial y final.
- Curvas de transición.
- Torsión progresiva.
- Fusión con una figura secundaria.
- Conformado a suelo, techo o superficie.
- Sockets Kitbash, también doblados a lo largo del recorrido (modos **Doblar** y **Estirar**).
- Reparación de microhuecos y componentes aislados.

En las figuras de espacio de tubo y en las clásicas con motor exacto, la escala, el espaciado y la torsión se aplican al evaluar cada bloque, sin deformación posterior ni puentes entre bloques separados. La figura de **Fusión** hereda el motor exacto de la receta cuando también lo tiene.

## Estructuras

La categoría Estructuras contiene generadores arquitectónicos orientados por puntos. Sus herramientas no aparecen duplicadas en la cuadrícula de Figuras:

- Pilares monumentales configurables.
- Pares de pilares monumentales conectados mediante arcos.
- Puentes arqueados entre apoyos.
- Torii japoneses gigantes que respetan diferencias verticales del terreno sin ondular sus vigas.

## Materiales y degradados

KomaEdit admite bloques individuales, patrones de WorldEdit y paletas de degradado. La distribución puede seguir el eje, exposición, cavidades, bordes, curvatura o propiedades específicas de la figura.

### Paletas con nombre

Donde KomaEdit espera una paleta (por ejemplo, `/komaedit gradient` o el material de una receta) se puede usar la notación de paletas de ezEdits, además de la lista `a;b;c` de siempre:

| Escritura | Resultado |
| --- | --- |
| `##Grayscale`, `##Magma`, `##GlowBlue`… | Paleta de serie de KomaEdit; no distingue mayúsculas. |
| `stone,andesite` o `stone;andesite` | Concatena bloques o paletas. |
| `-##Grayscale` | Invierte el orden. |
| `##Grayscale(1:8)` | Tramo de la posición 1 a la 8, ambas incluidas. |
| `gold_block*3,diamond_block` | Repite el tramo anterior. |
| `-[##Grayscale,gold_block]` | Agrupa para aplicar un modificador a todo el grupo. |
| `oak_stairs[facing=east]` | Bloques con estado: los corchetes pegados al nombre son parte del bloque. |

Una paleta admite hasta 256 entradas.

### Ruido configurable

Las herramientas que usan ruido aceptan la notación de ezEdits `Tipo(clave:valor,…)`, con nombres largos o abreviados y sin distinguir mayúsculas. Por defecto el valor va de 0 a 1.

- **Tipos:** `Perlin` (`Pe`), `OpenSimplex2` (`Si`), `OpenSimplex2S` (`Sm`), `Value` (`Va`), `ValueCubic` (`VC`), `White` (`Wh`), `Cellular` (`Ce`) y `Gabor` (`Gb`).
- **Básicos:** `Seed` (`s`, `-1` = semilla del contexto), `Frequency` (`f`), `Inverted` (`i`), escalas `x`, `y` y `z`, y `ValueMapping` (`m`): `Default`, `None` (valor crudo de −1 a 1) u `Override` (`OR`) con `l`, `u`, `Mn` y `Mx`.
- **Celular:** `cJ` (jitter), `cD` (`e`, `sq`, `man`, `h`) y `cR`: `cell`, `distance`, `distance2`, `add`, `sub`, `mul`, `div`, `sq`, `inv`, `log`, `exp`, `2sq`, `2inv`, `2log`, `2exp` y `edge`.
- **Fractal:** `fT` (`N`, `F`, `R`, `P`), `fO` (octavas), `fL` (lacunaridad), `fG` (ganancia), `fW` (fuerza ponderada) y `fP` (ping-pong).
- **Distorsión de dominio:** `wT` (`G`, `S`, `R`), `wF`, `wO`, `wG`, `wA`, `wC` (`N`, `I`, `P`) y `wl`.
- **Gabor:** `gR` (radio), `gF` (frecuencia de vetas), `gFs` (dispersión), `gO` (orientación `1;0;0`), `gJ` (variación), `gN` (impulsos por celda, máx. 8) y `gM` (contraste).

Ejemplos: `Perlin(f:0.04)`, `Ce(cD:e,cR:sub,F:.1)`, `Si(fT:R,fO:4,wT:S,wA:30)`.

Limitaciones: `Shard`, las distancias Minkowski y los retornos celulares `rounded` y `noiseLookup` aún no están disponibles y dan un error claro. El ruido se calcula con FastNoiseLite (licencia MIT), con posiciones en doble precisión.

## Retoques sobre la selección

### Objetivo y acceso

Ajustes rápidos de supervivencia sobre la selección de WorldEdit, como `//eztweak` de ezEdits:

```text
/komaedit tweak deinfest
/komaedit tweak persist [on|off]
/komaedit tweak waterlog [on|off]
/komaedit tweak wax [on|off]
```

Alias: `/komaedit retocar` y `desinfestar`, `persistir`, `anegar`, `encerar`.

### Comportamiento

- `deinfest` cambia cada bloque infestado por su versión normal, conservando las propiedades (por ejemplo, el eje de la pizarra profunda).
- `persist` activa o desactiva la persistencia de las hojas para que no se caigan.
- `waterlog` anega o seca todo lo que admita agua (escaleras, losas, vallas…).
- `wax` encera o desencera el cobre, incluidas escaleras, losas, puertas, trampillas, rejillas y bombillas. El mineral, el bloque de cobre en bruto y el pararrayos no tienen versión encerada y se dejan igual.
- La selección se lee repartiendo el trabajo entre ticks, se calcula en un worker y se escribe por lotes. Cada bloque solo se cambia si sigue igual que al leerlo, así no se pisa lo que alguien construya mientras tanto. Se deshace con `//undo`.

### Limitaciones

- Los carteles encerados guardan el encerado en sus datos, no en el estado del bloque; `wax` no los toca.
- La selección más su margen admite hasta 16 millones de bloques y 1 500 000 cambios.

## Pinceles

### Objetivo y acceso

Los pinceles de ezEdits (`//ezbrush`) dentro del sistema de brochas de KomaEdit: cada clic se captura, se calcula en un worker y se aplica como **una sola operación de //undo**, igual que las brochas de esculpido.

```text
/komaedit pincel <tipo> [parámetros] [banderas]
/komaedit ezbrush <tipo> [parámetros] [banderas]
/komaedit pincel aplicar | cancelar | quitar | estado
```

| Tipo | Alias | Pincel |
| --- | --- | --- |
| `paletteshift` | `desplazar`, `ps` | [Desplazar paleta](#pincel-desplazar-paleta) |
| `gradient` | `degradado`, `gr` | [Degradado](#pinceles-de-degradado) |
| `gradientstroke` | `trazo`, `gs` | [Trazo de degradado](#pinceles-de-degradado) |
| `petalpainter` | `petalos`, `pp` | [Pintor de pétalos](#pintor-de-pétalos) |
| `place` | `colocar`, `pl` | [Colocación](#pincel-de-colocación) |

- El pincel se liga al **objeto de la mano principal**, como los de WorldEdit: cada objeto guarda el suyo y se pueden llevar varios a la vez. Con la mano vacía se entrega una pluma llamada «Pincel de KomaEdit». Volver a configurar un objeto que ya tiene pincel lo sustituye.
- `quitar` (`off`) suelta el pincel del objeto de la mano; `estado` (`status`) dice qué pincel lleva y sus controles.
- Los pinceles apuntan al bloque que miras hasta a **192 bloques**.
- Permiso: `komaedit.build`.

### Comportamiento común

- **Controles:** con un pincel en la mano, el clic izquierdo no rompe bloques, el derecho no usa el objeto y la tecla de cambiar de mano (F) no cambia de mano: todo se lo queda el pincel. Mantener el clic derecho repite la pasada, como un pincel de verdad.
- **Vista previa:** con `-p` cada clic calcula la pasada y la deja en el holograma (una caja con el color del pincel) en vez de aplicarla; `/komaedit pincel aplicar` la coloca y `/komaedit pincel cancelar` la descarta. Sin `-p` se aplica en el acto.
- **Historial:** cada pasada es una edición por lotes de `BatchEditService` y queda en el historial de WorldEdit; `//undo` la quita entera y `/komaedit job status|cancel` funcionan como en el resto del mod.
- **Paletas:** donde un pincel pide una paleta vale la notación de ezEdits (`##Magma`, `-##Grayscale(1:8)`, `stone*3,andesite`…, ver [Paletas con nombre](#paletas-con-nombre)). Las entradas tienen que ser bloques sueltos, con estado si hace falta (`oak_log[axis=x]`); los patrones aleatorios de WorldEdit no valen dentro de un pincel.
- **Ruido:** `-n` acepta el [ruido configurable](#ruido-configurable) y `-z` multiplica las coordenadas antes de muestrearlo. Sin `-n` el ruido es blanco por bloque: cada bloque tira su propio dado.
- **Piezas (`-w`):** con `-w` los pinceles de paleta también pintan losas y escaleras, cambiándolas por la losa o escalera del material de la paleta (la misma que usa el modo [losas y escaleras](#losas-y-escaleras-en-la-superficie): por nombre o del color más parecido) y conservando su forma, orientación y anegado. Sin `-w` las piezas no se tocan.
- Lo que no es un cubo entero (flores, vallas, antorchas, carteles…) nunca se pinta.
- Con KomaEdit solo en el servidor (cliente vanilla) el pincel responde al clic derecho y al izquierdo sobre bloques al alcance; la F y el clic izquierdo a distancia necesitan el mod en el cliente.

### Rendimiento y limitaciones

- El cálculo es lo de menos: con radio 32 (unos 140 000 bloques) tarda menos de 20 ms en un worker. Lo que cuesta es leer la zona, que se reparte entre ticks (unos 16 000 bloques por tramo): con el radio por defecto (8) es instantáneo y con radio 32, alrededor de un segundo.
- Radio de 1 a 32. Una pasada lee como mucho 4 millones de bloques y cambia como mucho 1 000 000 (o el límite de operación del servidor, si es menor).
- Una pasada nueva cancela la anterior si aún se estaba calculando; si la anterior ya se está escribiendo, la nueva se descarta y el chat lo avisa.
- La vista previa es la caja que ocupa la pasada, no los bloques uno a uno.
- Los pinceles viven en la sesión: se pierden al salir del servidor o reiniciarlo (la marca del objeto se queda y basta con volver a configurarlo).

## Pincel Desplazar paleta

### Objetivo y acceso

Recorre una paleta bloque a bloque: cada bloque del pincel que esté en la paleta pasa al que tiene unas posiciones más allá. Sirve para oscurecer, aclarar, envejecer o «subir de nivel» una zona sin perder su textura (el Palette Shift de ezEdits).

```text
/komaedit pincel paletteshift <paleta> [radio] [desplazamiento] [-r] [-p]
```

### Comportamiento

- **Clic derecho** desplaza hacia delante; **clic izquierdo**, hacia atrás. Con desplazamiento 2, el primer bloque de la paleta pasa a ser el tercero.
- Se mira el id del bloque, no su estado: una escalera de la paleta sigue siendo escalera y conserva su orientación.
- Las **losas y escaleras** del material de cada entrada cuentan como esa entrada y pasan a la losa o escalera del material de destino. Si ese material no tiene, se quedan como estaban.
- Si un bloque se repite en la paleta (`stone*3,andesite`), hacia delante se cuenta desde su última aparición y hacia atrás desde la primera: un desplazamiento de 1 siempre cambia algo.
- En los extremos el bloque se queda en el primero o el último de la paleta; con `-r` da la vuelta (el último pasa al primero).
- Los bloques que no están en la paleta no se tocan.

### Parámetros

| Parámetro | Efecto |
| --- | --- |
| `<paleta>` | Al menos dos bloques, en orden. |
| `[radio]` | Radio de la esfera (1–32, por defecto 8). |
| `[desplazamiento]` | Posiciones que avanza cada clic (−255 a 255, por defecto 1). |
| `-r` | Da la vuelta en los extremos. |
| `-p` | Vista previa antes de aplicar. |

Ejemplos:

```text
/komaedit pincel paletteshift ##Grayscale
/komaedit pincel paletteshift stone,andesite,cobblestone,mossy_cobblestone 6 1
/komaedit pincel desplazar -##Magma 12 2 -r
```

### Rendimiento y limitaciones

- Ver [Pinceles](#pinceles). Las paletas de serie mezclan materiales de familias distintas: para envejecer una pared conviene una paleta propia del mismo material.

## Pinceles de degradado

### Objetivo y acceso

Pintan un degradado de paleta sobre lo que ya hay construido (los Gradient y Gradient Stroke de ezEdits): el color de cada bloque sale de su posición entre dos puntos, o a lo largo de un trazo, mezclado con ruido.

```text
/komaedit pincel gradient <paleta> [radio] [interpolación] [sangrado] [-a] [-v] [-w] [-n <ruido>] [-z <escala>] [-d <distancia>] [-p]
/komaedit pincel gradientstroke <paleta> [radio] [interpolación] [sangrado] [-a] [-d] [-v] [-w] [-x] [-n <ruido>] [-z <escala>] [-p]
```

### Comportamiento

**Degradado (`gradient`):**

- **Clic izquierdo** marca el inicio en el bloque que miras (**Mayús**: en tus pies). **Clic derecho** marca el final (**Mayús**: en tus pies); con los dos puestos, cada **clic derecho** pinta la esfera del pincel donde miras.
- El degradado va del primer punto (primera entrada de la paleta) al segundo (última), por planos perpendiculares a la recta que los une: lo que queda antes del inicio es la primera entrada y lo que queda después del final, la última.
- Con `-d <distancia>` el degradado se mide desde el primer punto hacia fuera, con la distancia `euclidean`, `manhattan` o `chebyshev`; el segundo punto marca dónde termina.
- **F** cambia entre puntos **PER_ITEM** (de esta herramienta, por defecto) y **GLOBAL** (compartidos por todas tus herramientas de degradado).

**Trazo (`gradientstroke`):**

- **Clic izquierdo** añade un punto al trazo; **Mayús + izquierdo** quita el último. **Clic derecho** pinta el trazo entero; **Mayús + derecho** lo borra.
- Pinta un tubo de radio `[radio]` alrededor de la línea que une los puntos, con el degradado a lo largo del recorrido. Con `-d` el degradado va del eje del tubo (primera entrada) a su borde (última).
- `-x` borra el trazo después de cada pasada; sin él se puede repintar con otra paleta.
- **F** cambia entre trazo PER_ITEM y GLOBAL, como en `gradient`.

**En los dos:**

- Solo se pintan bloques enteros que ya existen; con `-a` también el aire (el trazo con `-a` construye el tubo) y con `-w` las losas y escaleras.
- Los puntos se marcan con partículas que solo ves tú; `-v` las quita.
- El **sangrado** y la **interpolación** deciden cómo se mezclan los colores vecinos. El ruido (de 0 a 1) se convierte en un empujón de −sangrado a +sangrado a lo largo del degradado:

| Interpolación | Efecto |
| --- | --- |
| `NONE` | Bandas limpias; el ruido no se usa. Por defecto en `gradient`. |
| `LINEAR` | El mismo empujón en todo el degradado: cerca de los extremos se sale de los dos puntos. Por defecto en `gradientstroke`. |
| `TAPERED` | Fuerte en el centro y nulo en los extremos (4·t·(1−t)): no ensucia el principio ni el final. |
| `BEZIER` | Curva de Bézier con el punto de control movido por el ruido: mezcla más suave. Con sangrado mayor que 1 deja de ser monótona y salpica. |
| `SIN` | Como `TAPERED`, con un seno: reparte el sangrado más ancho por el centro. |

### Parámetros

| Parámetro | Efecto |
| --- | --- |
| `<paleta>` | Al menos dos bloques; la primera entrada es el inicio. |
| `[radio]` | Radio de la esfera o del tubo (1–32, por defecto 8). |
| `[interpolación]` | `NONE`, `LINEAR`, `TAPERED`, `BEZIER` o `SIN` (también `ninguna`, `lineal`, `seno`). |
| `[sangrado]` | Cuánto se mezclan los colores (0–4, por defecto 0,5; lo normal, de 0 a 1). |
| `-n <ruido>` | Ruido de la mezcla, por ejemplo `Perlin(F:0.25)`. Sin él, blanco por bloque. |
| `-z <escala>` | Multiplica las coordenadas del ruido (por defecto 1). |
| `-d <distancia>` | (`gradient`) Degradado por distancia al inicio: `euclidean`, `manhattan`, `chebyshev`. |
| `-d` | (`gradientstroke`) Degradado del eje al borde del tubo. |
| `-a` | Pinta también el aire. |
| `-w` | Pinta también losas y escaleras con su pieza del material. |
| `-v` | Sin partículas en los puntos. |
| `-x` | (`gradientstroke`) Borra el trazo tras cada pasada. |
| `-p` | Vista previa antes de aplicar. |

Ejemplos:

```text
/komaedit pincel gradient ##Grayscale
/komaedit pincel gradient ##Magma 10 tapered 0.6 -n Perlin(F:0.25)
/komaedit pincel gradient stone,andesite,tuff,deepslate 8 linear 0.4 -w -n Ce(F:0.15)
/komaedit pincel gradient -##GlowBlue 12 sin 0.5 -d euclidean
/komaedit pincel gradientstroke ##Grayscale 3 linear 0.5 -a -x
/komaedit pincel trazo moss_block,mossy_cobblestone,cobblestone 4 tapered 0.8 -d
```

### Rendimiento y limitaciones

- Ver [Pinceles](#pinceles). El trazo lee la caja que lo contiene más el radio: un trazo de 100 bloques en diagonal con radio 4 son unos 170 000 bloques (alrededor de medio segundo).
- El trazo admite hasta 256 puntos y es una polilínea: no se suaviza en curva.
- La integración con WorldEditCUI de ezEdits se sustituye por las partículas de los puntos.

## Pintor de pétalos

### Objetivo y acceso

Esparce follaje en capas sobre el terreno (el Petal Painter de ezEdits): pétalos rosas, flores silvestres u hojarasca, con más o menos piezas por bloque y orientación al azar.

```text
/komaedit pincel petalpainter <tipo> [radio] [densidad] [-n <ruido>] [-z <escala>] [-m <máscara>] [-p]
```

### Comportamiento

- **Clic derecho** esparce el follaje en la esfera del pincel, justo encima del bloque que miras; **clic izquierdo** quita el follaje de ese tipo en la esfera.
- Cada hueco de aire con un bloque de la máscara debajo toma un valor del ruido. Si queda por debajo de la densidad recibe follaje, y cuanto más bajo, más piezas (de 1 a 4). Con el ruido blanco de serie, la densidad es la fracción de suelo cubierta.
- Donde ya hay follaje del mismo tipo, se conserva su orientación y solo se suben las piezas; como el ruido blanco cambia en cada clic, repasar una zona la va espesando. Con un ruido propio (`-n`) el dibujo se mantiene de un clic a otro.
- Las piezas nuevas se orientan al azar hacia los cuatro puntos cardinales.

### Parámetros

| Parámetro | Efecto |
| --- | --- |
| `<tipo>` | `petals` (`pink_petals`), `wildflowers` (`minecraft:wildflowers`) o `leaves` (`minecraft:leaf_litter`); también cualquier bloque con `flower_amount` o `segment_amount`. |
| `[radio]` | Radio de la esfera (1–32, por defecto 8). |
| `[densidad]` | Cuánto follaje (0–1, por defecto 0,3). |
| `-n <ruido>` | Ruido que reparte el follaje, por ejemplo `Perlin(F:0.05)` para manchas. |
| `-z <escala>` | Multiplica las coordenadas del ruido (por defecto 0,03, como en ezEdits; con `Perlin(F:1)` da manchas de unos 30 bloques). |
| `-m <máscara>` | Bloques sobre los que se pone (por defecto `#fullblock`). Lista separada por comas de ids y de `#fullblock`, `#solid` (cubo, losa o escalera) y `#existing`; `!` excluye. |
| `-p` | Vista previa antes de aplicar. |

Ejemplos:

```text
/komaedit pincel petalpainter petals
/komaedit pincel petalpainter petals 10 0.6 -m grass_block,moss_block
/komaedit pincel petalos petals 16 0.5 -n Perlin(F:1) -z 0.05
/komaedit pincel petalpainter petals 6 1 -m #fullblock,!sand,!gravel
```

### Rendimiento y limitaciones

- Ver [Pinceles](#pinceles).
- Minecraft 1.21.1 solo tiene `pink_petals`: `wildflowers` y `leaf_litter` llegaron en 1.21.5 y dan un error claro hasta que KomaEdit se actualice. Los bloques de mods con `flower_amount` o `segment_amount` funcionan.
- La máscara es la propia de los pinceles, sobre la foto capturada: no admite etiquetas (`#minecraft:logs`) ni las máscaras de expresión de WorldEdit.

## Pincel de colocación

### Objetivo y acceso

Coloca una estructura en cada clic (el `//ezbrush place` de ezEdits), orientada con el sistema de **primaria + secundaria**: la primaria fija hacia dónde mira el «arriba» de la estructura y la secundaria, su «delante».

```text
/komaedit pincel place <estructura> [primaria] [secundaria] [-a] [-x] [-p]
```

### Comportamiento

- **Clic derecho** coloca la estructura en el bloque de aire delante de la cara que miras.
- **Estructuras:** de momento `Clipboard` (`Cl`), el portapapeles de WorldEdit (`//copy`):
  - `Origin`/`O`: `INHERENT` (`I`, por defecto: el punto desde el que se copió) o `CENTER` (`C`, el centro de la copia).
  - `Rotation`/`R`: giro previo alrededor del «arriba», en múltiplos de 90° (sentido horario visto desde arriba).
  - `PasteMethod`/`PM`: solo `FAST`.
- **Alineación:** `primaria` y `secundaria` aceptan `Constant` (`C`, con `Direction`/`D`: `up`, `down`, `north`, `south`, `east`, `west`, `x`, `-z`, un vector `(x,y,z)` o `aim`, la mirada al configurar el pincel), `Aim` (`A`, la mirada en cada clic), `Upward` (`U`, la perpendicular a la mirada que más sube), `SurfaceNormal` (`S`, la cara que miras), `PlayerRelative` (`P`, hacia ti) y `Random` (`R`). Por defecto, primaria `Constant` (arriba) y secundaria `Constant` (este): la estructura sale como se copió.
- De todas las direcciones perpendiculares a la primaria se elige la más parecida a la secundaria. Si las dos van en la misma línea la colocación falla; con `-x` se elige una perpendicular cualquiera.
- Los bloques se giran con sus estados (escaleras, troncos, vallas) y el aire de la copia no se pega salvo con `-a`.

### Parámetros

| Parámetro | Efecto |
| --- | --- |
| `<estructura>` | `Clipboard`/`Cl` con sus opciones, por ejemplo `Cl(O:C,R:90)`. |
| `[primaria]` | Hacia dónde mira el «arriba» (por defecto `Constant`, arriba). |
| `[secundaria]` | Hacia dónde mira el «delante» (por defecto `Constant`, este). |
| `-a` | Pega también el aire de la copia. |
| `-x` | Si primaria y secundaria son colineales, toma una perpendicular cualquiera. |
| `-p` | Vista previa antes de aplicar. |

Ejemplos:

```text
/komaedit pincel place Clipboard
/komaedit pincel place Cl(O:C) SurfaceNormal Aim
/komaedit pincel place Cl Constant Random
/komaedit pincel place Cl(R:90) C(D:south) C(D:east)
/komaedit pincel colocar Cl PlayerRelative Constant -x
```

### Rendimiento y limitaciones

- No lee el mundo: el portapapeles se copia en cada clic y la colocación se calcula en un worker. Hasta 1 000 000 bloques por estructura.
- Las direcciones se ajustan al eje más cercano (giros de 90°, como `-j MULTIPLES_90` de ezEdits): así cada bloque se gira con su estado exacto y no hay remuestreo. Los giros libres, el `PasteMethod:SMOOTHED`, `ViewDiff`, las direcciones `Noise`, `Expression` y `Mixed`, las opciones `RelativeOffset`/`WorldOffset` y los parámetros de colocación (`-s`, `-o`, `-c`, `-f`, `-r`, `-j`) llegarán con el motor común de colocación (`place`, `array` y `scatter`); el pincel ya admite cualquier estructura que se registre en `StructureSources`.
- Se pegan bloques y estados, sin los datos de los bloques con inventario (cofres, carteles) ni entidades.

## Supervarita

### Objetivo y acceso

Herramienta de selección avanzada (la Superwand de ezEdits) que escribe directamente en la **selección de WorldEdit**: lo que marques lo ven al momento `//set`, `//copy`, WorldEditCUI y las operaciones de KomaEdit.

```text
/komaedit superwand [modo] [-d <distancia>]
/komaedit supervarita [modo] [-d <distancia>]
/komaedit superwand off
```

- Se liga al objeto de la mano principal; con la mano vacía se entrega una vara de blaze llamada «Supervarita». `off` la desactiva y quita la marca del objeto.
- Al activarla lee los puntos de tu selección actual.
- Permiso: `komaedit.select`.

### Comportamiento

| Modo | Clic derecho |
| --- | --- |
| `trace` (`apuntar`, por defecto) | Marca el bloque que miras (hasta 192 bloques). |
| `position` (`posicion`) | Marca el bloque en el que estás. |
| `relative` (`relativo`) | Marca un punto a partir del último, en la dirección en la que miras: a la distancia de `-d` o, sin ella, a la que estás del último punto. |
| `grab` (`agarrar`) | Agarra el punto que miras (a menos de 1,5 bloques del rayo) o, si no miras ninguno, la selección entera, y la lleva a la misma distancia delante de ti mientras te mueves y giras. Otro clic, o el izquierdo, la suelta. |

| Control | Efecto |
| --- | --- |
| Clic derecho | Punto nuevo; si la selección ya está llena (dos puntos en un cuboide, una esfera o un cilindro) sustituye el último. En `grab`, agarra o suelta. |
| Clic izquierdo | Quita el último punto. En `grab`, suelta. |
| Mayús + clic izquierdo | Pasa al modo siguiente (`trace` → `position` → `relative` → `grab`). |
| F (cambiar de mano) | Invierte el orden de los puntos. |
| Mayús + F | Desplaza todos los puntos una posición en la lista (el primero pasa al final). |

- La selección se reconstruye con el **selector que tengas en WorldEdit** (`//sel cuboid`, `sphere`, `cyl`, `poly`, `convex`…): el primer punto es el primario y los demás, secundarios. Con `//sel convex` o `//sel poly` no hay límite práctico de puntos (256).
- Si cambias la selección por otro camino (`//pos1`, la varita de WorldEdit), la supervarita vuelve a leerla antes del siguiente clic.
- Cada cambio marca los puntos con partículas y avisa a WorldEditCUI.

### Parámetros y ejemplos

| Parámetro | Efecto |
| --- | --- |
| `[modo]` | `trace`, `position`, `relative` o `grab`. |
| `-d <distancia>` | Distancia fija del modo relativo (0–512; 0 = la del último punto). |

```text
/komaedit superwand
/komaedit superwand relative -d 8
//sel convex
/komaedit superwand trace
/komaedit supervarita grab
```

### Rendimiento y limitaciones

- No escribe bloques, solo la selección: no pasa por el historial ni tiene vista previa (la selección ya se ve con WorldEditCUI o con las partículas).
- Al leer una esfera, un elipsoide o un cilindro se toman el centro y un punto en el borde a lo largo del eje x: el radio en las otras direcciones se pierde si era distinto.
- La F y el clic izquierdo a distancia necesitan KomaEdit en el cliente; sin él, la supervarita responde al clic derecho y al izquierdo sobre bloques al alcance.
- La selección agarrada se mueve por bloques enteros y se actualiza en cada tick en que cambia.

## Recetas, instancias y paquetes

- Las recetas guardan parámetros reutilizables de forma persistente dentro del mundo.
- Las instancias conservan receta, trayectoria, estados colocados y checksum; permiten actualizar o retirar una construcción sin borrar bloques modificados manualmente.
- Seleccionar una instancia vuelve a cargar sus puntos en el editor antes de actualizarla.
- Los paquetes exportables conservan configuraciones compartibles en `config/komaedit/packages/`.
- Los datos se aíslan por mundo y UUID de jugador para evitar contaminación entre proyectos.

## Proyecto persistente y recuperación

### Objetivo y acceso

El proyecto persistente evita perder recetas e instancias al desconectarse y permite auditar operaciones interrumpidas. Se administra con:

```text
/komaedit project status
/komaedit project backup
```

### Almacenamiento y migración

- Ruta: `<mundo>/komaedit/players/<uuid>/` con namespaces `recipes`, `instances` y `jobs`.
- La primera entrada copia de forma no destructiva las recetas e instancias heredadas de `config/komaedit/`; un marcador impide repetir la migración.
- Cada reemplazo conserva el archivo anterior como `.bak` y utiliza un `.tmp` recuperable si el proceso se interrumpe.
- La geometría comprimida de una instancia incluye SHA-256 en sus metadatos. Una discrepancia detiene la carga en vez de aplicar datos dañados.
- `project backup` crea un ZIP en `<mundo>/komaedit/backups/` y conserva las copias más recientes según `backupRetention`.

### Comportamiento y limitaciones

- Los jobs activos escriben progreso como máximo una vez por segundo para no convertir el disco en un cuello de botella.
- Tras un cierre inesperado, los jobs `RUNNING` pasan a `INTERRUPTED`; no se reanudan automáticamente porque el mundo pudo cambiar mientras el servidor estaba apagado.
- Las operaciones ya confirmadas siguen usando el historial de WorldEdit para `//undo`; la bitácora persistente es de diagnóstico, no una sustitución del historial.
- Los backups son manuales y por jugador. Los administradores deben incluir la carpeta `komaedit` del mundo en su estrategia normal de respaldo.
- Una receta o instancia que no se pueda leer (dañada, o de una versión que usaba una figura ya retirada) se omite del catálogo y se registra en `latest.log` con su nombre de archivo; el archivo se queda en disco y el resto del proyecto sigue funcionando.

## Permisos, cuotas y configuración

### Permisos

Los operadores poseen acceso total. Para otros jugadores, KomaEdit consulta permisos a través del actor de WorldEdit:

- Base: `komaedit.use`.
- Trayectorias y construcción: `komaedit.path`, `komaedit.build`, `komaedit.build.<figura>`.
- Contenido: `komaedit.recipe`, `komaedit.instance`.
- Edición: `komaedit.select` (también la supervarita), `komaedit.paint`, `komaedit.clipboard`, `komaedit.history`.
- Pinceles (`/komaedit pincel`): `komaedit.build`.
- Administración de proyecto: `komaedit.project.backup`.
- Excepción de volumen: `komaedit.limit.unrestricted`.

### Configuración

`config/komaedit/config.json` se crea automáticamente con:

- `maxBlocksPerOperation`: `1500000` de forma predeterminada; rango válido `10000–10000000`.
- `maxConcurrentJobs`: `4` de forma predeterminada; rango válido `1–64`.
- `backupRetention`: `5` de forma predeterminada; rango válido `1–50`.
- `rootPermissionGrantsAll`: `false` de forma predeterminada. Si se activa, `komaedit.use` concede todos los subpermisos como compatibilidad con instalaciones antiguas.

Una configuración inválida se rechaza completa y el servidor utiliza límites seguros. Los cambios requieren reiniciar el servidor.

## Rendimiento

- La geometría se calcula de forma asíncrona.
- La colocación se agrupa por chunks y lotes.
- Los hologramas utilizan secciones persistentes, descarte por cámara y caras expuestas.
- Los generadores poseen límites de seguridad y cancelación cooperativa.
- Las figuras de espacio de tubo recorren solo los ladrillos de 8³ bloques cercanos al eje y localizan el eje con un árbol de esferas.
- El límite de volumen y la cuota global de jobs se aplican antes de abrir una sesión de edición.
- Cada job persistente registra avance con frecuencia limitada y su finalización o cancelación.

## Versiones descargables

Todas las compilaciones de integración quedan visibles en GitHub Releases además de los artefactos temporales de GitHub Actions.

### Canal staging

- La versión interna del mod incluye la versión de desarrollo y el SHA corto: `komaedit-4.8.0-alpha.1.c0d75e5.jar`.
- La prerelease se muestra con el formato `komaedit-staging-4.8.0-c0d75e5`.
- Cada merge genera una prerelease independiente apuntando al commit exacto compilado.
- Estas versiones son de prueba y no sustituyen una publicación estable.

### Canal main

- Utiliza únicamente `release_version`.
- El nombre estable sigue el formato `komaedit-1.0.0`.
- No incluye `staging`, sufijos alpha ni SHA.
- Antes de publicar otro commit en `main` se incrementa la versión, por ejemplo de `1.0.0` a `1.0.1`.
- GitHub Actions rechaza una publicación si intenta reutilizar una versión estable asociada a otro commit.

## Política de actualización

Cada nueva función debe documentar:

1. Qué problema resuelve.
2. Cómo se abre o ejecuta.
3. Sus parámetros y materiales.
4. Su interacción con puntos, hologramas y modificadores.
5. Consideraciones de rendimiento.
6. Comandos de ejemplo cuando correspondan.
