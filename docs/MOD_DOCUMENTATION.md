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

## Texturizado

### Objetivo y acceso

Pinta una paleta sobre lo que ya hay construido en la selección de WorldEdit según una medida del sitio de cada bloque: lo ocluido, la curvatura, la luz, un eje, ruido, celdas o líneas de flujo. Es la familia `//eztexture` de ezEdits, con los mismos modos, argumentos y banderas:

```text
/komaedit texture <modo> …
```

Alias: `/komaedit textura`. `/komaedit texture` sin modo muestra la ayuda. Requiere el permiso de construir (`komaedit.build`).

Los argumentos van por posición y las banderas en cualquier sitio. Un argumento con espacios va entre comillas; los paréntesis y corchetes agrupan solos, así que `Ambient(Radius:2, Contrast:0.3)` es un solo argumento.

### Comportamiento

- **Paleta**: cualquier paleta de [Paletas con nombre](#paletas-con-nombre) (`##Grayscale`, `-##Magma(1:8)`, `stone,andesite,cobblestone`…), con al menos dos bloques; cada entrada es un bloque o un patrón de WorldEdit sin comas (`oak_stairs[facing=east]`, `#clipboard`). Cada modo da a cada bloque un valor de 0 a 1 y se coloca el bloque de esa posición de la paleta: 0 es el principio y 1 el final.
- **Convención**: lo ocluido, cóncavo, poco iluminado o de cara a la luz va hacia el **principio** de la paleta. `##Grayscale` empieza en negro, así que las grietas salen oscuras con `ambient`; para que las caras iluminadas salgan claras con `pointlight` o `sunlight` usa `-##Grayscale`.
- **Superficie**: `ambient`, `curvature`, `pointlight`, `sunlight`, `blocklight` y `flow` solo cambian los bloques con alguna cara al aire (el interior no se ve). `axisgradient`, `noise` y `cells` cambian todos los bloques de la máscara. Si la selección no tiene superficie (por ejemplo, solo el interior de un muro) avisa de que hay que ampliarla para que incluya aire; la lectura ya coge un margen alrededor.
- **Máscara**: qué bloques se texturizan. Ver [Máscaras de las operaciones de región](#máscaras-de-las-operaciones-de-región).
- **`-w`**: respeta las piezas con forma (escaleras, losas, muros, vallas, puertas de valla, botones, placas y trampillas). Cada una se cambia por la pieza del mismo tipo del material elegido, con su orientación y propiedades; si ese material no tiene la pieza, se usa el material más cercano de la paleta que sí la tenga, y si ninguno la tiene el bloque se queda como está. Sin `-w` la máscara manda: una escalera que pase la máscara se cambia por el bloque entero.
- **`-d <0–1>`** (propio de KomaEdit): tramado entre dos bloques vecinos de la paleta. Con 0 (por defecto) cada bloque toma el más cercano; con más tramado los cambios se reparten con un umbral fijo por posición y no salen franjas. `blend` usa 0,5 por defecto.
- **Semilla**: `cells`, `noise`, `flow` y `advanced` usan la de `-s` (cuando el modo la tiene) o una al azar que se muestra en el mensaje; con la misma semilla sale lo mismo.
- La lectura va repartida entre ticks, el cálculo en un worker y la escritura por lotes y condicional (no pisa lo que cambie mientras tanto). Todo es una edición: `//undo` la deshace.

### Modos y parámetros

| Modo | Uso | Qué mide |
| --- | --- | --- |
| `ambient` | `<máscara> <paleta> [radio=3] [brillo=0] [contraste=0] [-w]` | Oclusión ambiental: rayos por el hemisferio de cada cara, de largo `2·radio+1`. El campo se suaviza con el radio; el contraste devuelve el detalle local (0 = solo el campo suave, 1 = solo el local). Se estira para usar la paleta entera. |
| `axisgradient` | `<máscara> <paleta> [x\|y\|z=y] [-r] [-w]` | Degradado por un eje de lo más bajo a lo más alto de la máscara. `-r`: relativo, cada columna estira la paleta entera. |
| `blend` | `<paleta> [radio=0,5] [-v] [-w]` | Mezcla los bloques de la paleta que ya hay: cada uno toma la media ponderada de sus vecinos (gaussiana de desviación `radio`). `-v` mezcla también el interior; sin `-w` las piezas con forma no se tocan. |
| `blocklight` | `<máscara> <paleta> [-v] [-s] [-l] [-w]` | Luz de bloque que llega a las caras al aire (0–15). `-v`: solo la de encima. `-s`: cuenta también la luz del cielo. `-l`: no cambia los bloques que emiten luz. |
| `cells` | `<máscara> <paleta> <cantidad> [brillo=0] [contraste=0] [-s semilla] [-r repulsión=5] [-w]` | Celdas de Voronoi con un valor al azar cada una. Cada centro se elige entre `repulsión` candidatos, el más alejado de los demás: más repulsión, celdas más regulares. |
| `curvature` | `<máscara> <paleta> [radio=3] [brillo=0] [contraste=0] [-w]` | Proporción de material en la caja de `radio`: una pared lisa queda en el centro de la paleta, las aristas hacia el final y los rincones hacia el principio. |
| `flow` | `<máscara> <paleta> [exposición=0,6] [iteraciones=32] [velocidad=0,5] [escalar=1] [ruido=Perlin(Freq:0.03)] [-m [peso=0,5]] [-g [dirección=down]] [-f] [-w]` | Líneas que nacen en caras al azar y avanzan pegadas a la superficie en la dirección que marca el ruido. Cada bloque recorrido toma el avance de la línea (0 al nacer, 1 al final) por el escalar. La exposición es la densidad de líneas. `-m`: inercia de la dirección. `-g`: tira de los puntos hacia una dirección. `-f`: lo que ninguna línea pisa toma el primer bloque; sin `-f` se queda como está. |
| `noise` | `<máscara> <paleta> [ruido=Perlin(Freq:0.05)] [-z escala=1] [-s semilla] [-w]` | Un [ruido configurable](#ruido-configurable) en cada bloque. `-z` agranda el dibujo. |
| `pointlight` | `<máscara> <paleta> [alcance=0] [radio=1] [intervalo=0,90] [-l x,y,z] [-o oclusión=0] [-r radio=1] [-f] [-w]` | Ángulo entre la normal de cada cara (aproximada en `radio`) y la dirección a la luz: 0° es el principio del intervalo; por debajo o por encima, el primer o el último bloque. La luz está en los ojos del jugador o en `-l`. El alcance atenúa con la distancia (0 = la distancia al centro de la selección); `-f` la quita. `-o` proyecta sombras (0–1) y `-r` las suaviza. |
| `shift` | `<paleta> [desplazamiento=1]` | Mueve cada bloque de la paleta tantas posiciones como se pida (negativo hacia atrás), sin pasar de los extremos. Conserva escaleras, losas y demás piezas si el material nuevo las tiene. |
| `sunlight` | `<máscara> <paleta> [radio=1] [intervalo=0,180] [-l dirección=down] [-o oclusión=0] [-r radio=1] [-w]` | Como `pointlight` con una luz lejana que alumbra en la dirección `-l` (`down`, `east`, `-1;-1;0.3`…). Las sombras recorren la foto hasta salir de ella. |
| `advanced` | `<máscara> <paleta> <textura> [-w]` | Combina modos (abajo). |

Ejemplos:

```text
/komaedit texture ambient #existing ##Grayscale 3 0 0.4
/komaedit texture axisgradient stone,andesite -##GrayRough y -r -d 0.6
/komaedit texture cells #solid ##Magma 40 0 0.3 -s 7 -r 12
/komaedit texture flow #existing ##GlowCyan 0.8 40 0.5 1 Si(f:0.02) -m 0.6 -g down -f
/komaedit texture noise #existing stone,andesite,cobblestone Ce(cR:cell,f:0.08) -z 2
/komaedit texture pointlight #existing -##Grayscale 40 2 0,100 -o 0.7 -r 2
/komaedit texture sunlight #existing -##GrayWarm 2 0,150 -l -1;-2;0.5 -o 0.8 -w
/komaedit texture blocklight #existing ##Magma -s -l
/komaedit texture blend stone,andesite,cobblestone,gravel 1.5
/komaedit texture shift ##GrayRough 2
```

**Texturas combinadas (`advanced`)**. Una textura se escribe `Tipo(Parámetro:valor,…)` como en ezEdits, sin distinguir mayúsculas; los parámetros que faltan toman su valor por defecto. `/komaedit texture ambient #existing ##Grayscale 2 0.2 0.3` es lo mismo que `/komaedit texture advanced #existing ##Grayscale Ambient(Radius:2,Brightness:0.2,Contrast:0.3)`.

- **Modos**: `Ambient(Radius, Brightness, Contrast)`, `AxisGradient(Axis, Relative)`, `Blocklight(Vertical, Skylight, KeepLights)`, `Cells(Amount, Brightness, Contrast, Seed, Repulsion)`, `Curvature(Radius, Brightness, Contrast)`, `Flow(Exposure, Iterations, Velocity, PaletteScalar, Noise, Momentum, Gravity, Fill, Seed)`, `Noise(Noise, Scale, Seed)`, `Pointlight(FalloffRange, Radius, Interval, Light, Occlusion, OcclusionRadius, NoFalloff)` y `Sunlight` o `Sun(Radius, Interval, Light, Occlusion, OcclusionRadius)`. Las abreviaturas de una letra también valen (`R`, `B`, `C`, `O`…). Las direcciones y posiciones se escriben `x;y;z` y el intervalo `0;90`.
- **Combinar dos** (`T1`/`Texture1` y `T2`/`Texture2`, obligatorias): `Add`, `Subtract`, `Multiply`, `Divide`, `WeightedAverage` (con `Weight`, 0,5 por defecto), `Darken` (mínimo), `Lighten` (máximo), `Difference` y `Screen`. Donde a una de las dos le falta valor (por ejemplo el interior en `ambient`), el bloque no cambia; dentro de una combinación `Flow` rellena sus huecos con 0.
- **Retocar una** (`T`/`Texture`, obligatoria): `Adjust(T, Brightness, Contrast)`, `Invert(T)` y `Blend(T, Radius)`, que suaviza el campo.
- **Ruidos de serie**: `@@nombre` con cualquier tipo de ruido (`@@perlin`) o `@@ridged`, `@@fbm`, `@@billow`, `@@cells`, `@@smoothcells`, `@@edges`, `@@swirl` y `@@grain`; los parámetros entre paréntesis sustituyen a los suyos: `@@ridged(Freq:0.12)`.

```text
/komaedit texture advanced #existing -##Grayscale WeightedAverage(T1:Sun(L:-1;-1;0.3,O:0.8),T2:Ambient(R:3))
/komaedit texture advanced #existing ##GrayRough Blend(T:Flow(Noise:@@ridged(Freq:0.12)),Radius:0.7)
/komaedit texture advanced #existing ##Magma Darken(T1:Noise(Noise:@@smoothcells(freq:0.5)),T2:Flow)
/komaedit texture advanced #existing ##Grayscale Adjust(T:Pointlight,Contrast:0.5)
```

Límites: 1 024 caracteres, 8 niveles de anidado y 32 texturas por expresión.

### Rendimiento y limitaciones

- Medido en un terreno de 192×192×48 (800 000 bloques, 68 000 caras al aire): preparar la foto 0,16 s; `ambient` radio 3 0,35 s y radio 6 0,9 s; `curvature` 0,04 s; `noise` 0,05 s; `cells` con 200 celdas 0,17 s; `flow` 0,1 s; `pointlight` y `sunlight` con sombras 0,13–0,16 s. La lectura del mundo, repartida entre ticks, suele tardar más que el cálculo.
- La selección más su margen admite hasta 8 millones de bloques (las tablas auxiliares ocupan unos 8 bytes por bloque). El margen depende del modo: `2·radio+2` en `ambient`, `radio+1` en `curvature`, unos pocos bloques en los demás, hasta 32.
- `blocklight` lee los niveles de luz del mundo, lo que hace la lectura algo más lenta. Las sombras de `pointlight` y `sunlight` solo ven lo que hay en la foto: lo que quede fuera de la selección y su margen no hace sombra.
- `shift` y `blend` reconocen los bloques por su identificador (sin propiedades); con un material repetido en la paleta, `shift` parte de su última aparición al subir y de la primera al bajar, y `blend` de la central.
- Los patrones aleatorios de la paleta se resuelven bloque a bloque; para reconocer el material de una entrada (`shift`, `blend`, `-w`) se usa lo que el patrón da en el origen.

## Máscaras de las operaciones de región

`texture`, `vines`, `slabmerge` y `statecycle` aceptan cualquier máscara de WorldEdit que se evalúe sobre bloques. La máscara se parsea en el hilo del servidor y se evalúa en el worker sobre la foto capturada (un `Extent` de solo lectura): no toca el mundo ni bloquea el servidor.

- Funcionan: listas de bloques (`stone,andesite`), estados (`oak_stairs[half=top]`, `^[facing=north]`), etiquetas (`##minecraft:logs`), `#existing`, `#solid`, `#air`, `#surface` o `#exposed`, desplazamientos (`>stone` encima de piedra, `<air`), negación (`!`), `#region` y `#dregion`, ruido (`%30`) y expresiones (`=y>64`). Las máscaras que añadan otros mods o la paridad con ezEdits también funcionan si miran bloques a través de WorldEdit.
- `*`, `#all` o `todo` aceptan cualquier bloque que no sea aire.
- Fuera de la foto (la selección más su margen) todo cuenta como aire. Las máscaras de bioma (`$plains`) no funcionan aquí y dan un error claro, porque la foto no guarda biomas.

## Enredaderas, musgo, losas y estados

### Objetivo y acceso

Los comandos de región de ezEdits (`//ezvines`, `//ezmoss`, `//ezslabmerge` y `//ezstatecycle`) sobre la selección de WorldEdit:

```text
/komaedit vines <máscara> <patrón> [porcentaje=10] [mínimo=2] [máximo=5] [-s semilla]
/komaedit moss <patrón> [cantidad=2] [radios=1] [iteraciones=5] [-s semilla]
/komaedit slabmerge [máscara=*] [-b] [-t]
/komaedit statecycle <máscara> <propiedad> [pasos=1]
```

Alias: `enredaderas`, `musgo`, `unirlosas` y `ciclarestado`. Requieren `komaedit.build`. La hiedra que crece y trepa es otra herramienta: [Hiedra viva](#hiedra-viva).

### Comportamiento y parámetros

- **`vines`**: de un `porcentaje` de los bloques de la máscara con aire debajo cuelga una tira del patrón de entre `mínimo` y `máximo` bloques (hasta 32), que se corta al tocar algo y puede salir por debajo de la selección. Con `vine` cada tramo se pega a las paredes vecinas (y hereda las caras del de encima); donde no tendría apoyo la tira se corta, como en vanilla. `weeping_vines` y `cave_vines` llevan tallo (`_plant`) y punta. Cualquier otro patrón (`chain`, `glow_lichen`, `oak_fence`…) se cuelga tal cual. `/komaedit vines #existing vine 15 2 6`, `/komaedit vines stone_bricks weeping_vines 8 3 9 -s 4`.
- **`moss`**: manchas sobre las caras de arriba. Parte de ruido blanco, lo suaviza `iteraciones` veces con la media de sus vecinos en una caja de `radios` (uno o tres, en el orden este/oeste, arriba/abajo, norte/sur) y cubre la parte que pide la `cantidad` (2 cubre la mitad, 4 tres cuartos; 0 nada). Más iteraciones o radios, manchas más grandes y redondas. Un patrón de bloque entero (`moss_block`) cambia la superficie; uno que no ocupa el bloque entero (`moss_carpet`, flores, nieve) se pone encima, en el aire. `/komaedit moss moss_block 3 2,1,2 8`, `/komaedit moss moss_carpet 1.5`.
- **`slabmerge`**: cambia las losas dobles por el bloque entero de su material (`stone_brick_slab` → `stone_bricks`, `oak_slab` → `oak_planks`, `quartz_slab` → `quartz_block`). `-b` también las losas de abajo y `-t` las de arriba.
- **`statecycle`**: avanza una propiedad (`facing`, `half`, `axis`, `age`…) al valor siguiente en cada bloque de la máscara que la tenga, en el orden de valores del bloque y dando la vuelta; con pasos negativos va hacia atrás. `/komaedit statecycle #existing facing`, `/komaedit statecycle oak_stairs half`, `/komaedit statecycle #existing age -1`.
- Igual que las texturas: lectura repartida entre ticks, cálculo en un worker, escritura condicional y `//undo`. `vines` y `moss` con la misma semilla dan lo mismo.

### Rendimiento y limitaciones

- La selección más su margen admite hasta 16 millones de bloques y 1 500 000 cambios. `vines` lee `máximo` bloques de margen para ver dónde acaba cada tira; `moss` uno.
- `moss` solo mira las caras de arriba; para musgo en paredes usa `texture` con una máscara (`texture noise #surface moss_block,stone …`).
- `slabmerge` busca el bloque entero por el nombre (con una tabla para los casos sin convención); si no lo encuentra deja la losa.
- Las enredaderas se colocan con las actualizaciones normales de WorldEdit: si después se rompe el bloque del que cuelgan, se caen como en vanilla.

## Vidriera degradada

### Objetivo y acceso

Busca las capas de cristal tintado que, vistas de frente, más se acercan a cada color de un degradado y las coloca delante del jugador (como `//ezstainedglassgradient` de ezEdits):

```text
/komaedit glassgradient <inicio> [fin] <capas> [longitud=1] [calidad=6] [dirección=up] [-c fondo] [-b] [-s]
```

Alias: `/komaedit vidriera`. Requiere `komaedit.build`.

### Comportamiento

- Los colores van en hexadecimal (`#3A7FD2`, `3A7FD2` o `#38D`). Sin color final, todo el degradado es el inicial. Los pasos intermedios se reparten en OKLab, así el degradado avanza de forma pareja a la vista.
- Las capas se apilan en la dirección en la que miras (la primera es la más cercana) y la vidriera empieza dos bloques delante de tus ojos; si miras muy arriba o muy abajo, las capas van en vertical. El degradado avanza `longitud` bloques hacia `up`, `down`, `left`, `right` o un punto cardinal (no hacia donde miras).
- El color se compone como en Minecraft: cada capa de cristal mezcla su tinte (alfa medio 0,46, medido en las texturas de 1.21.1) sobre lo que hay detrás. El cristal sin tinte cuenta como transparente, así una combinación puede usar menos capas reales.
- **Fondo**: lo que se ve detrás, `-c` (negro por defecto). Con `-b` se elige y se coloca detrás el bloque opaco que mejor completa cada color, entre 183 bloques de una sola textura (hormigón, lana, terracota, piedras, minerales…).
- **Calidad**: la búsqueda va capa a capa desde delante y conserva las `4·2^calidad` pilas más prometedoras (con calidad 6, 256); con dos capas equivale a probarlas todas. Más calidad, colores más exactos y más tiempo. `-s` prueba cada mezcla una sola vez sin importar el orden de las capas: más rápido y casi igual de exacto.
- El mensaje final da el error medio y máximo (distancia OKLab; por debajo de 0,02 apenas se nota). Todo es una edición: `//undo` la deshace.

Ejemplos:

```text
/komaedit glassgradient #E8D44D #5A2FB2 4 24
/komaedit glassgradient #2E7D6A 3 1 8 -b
/komaedit glassgradient FF8800 112266 5 16 6 right -c #FFFFFF -s
```

### Rendimiento y limitaciones

- Capas de 1 a 16, longitud de 1 a 512 y calidad de 1 a 10. El cálculo va en un worker; los colores repetidos se calculan una vez.
- Error medio del degradado #E8D44D → #5A2FB2 en 24 pasos sobre negro: 0,163 con una capa, 0,016 con tres y 0,005 con cinco; con tres capas y `-b`, 0,003.
- El color real depende del brillo, la niebla y el paquete de texturas; se calcula con las texturas de vanilla y la mezcla alfa estándar. Lo que haya en el sitio de la vidriera se sustituye.

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
- Edición: `komaedit.select`, `komaedit.paint`, `komaedit.clipboard`, `komaedit.history`.
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
