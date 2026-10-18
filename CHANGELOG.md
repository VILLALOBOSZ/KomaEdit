# Changelog

## Unreleased

- Añade el **modo simetría**, `/komaedit symmetry` (alias `simetria`, `sym`), como `//ezsymmetry` de ezEdits pero con diseño propio: espejos en X, Z e Y en cualquier combinación (`x`, `xz`, `xzy`…) o radial de 2 a 16 brazos alrededor de un eje vertical (con espejo en Y si se quiere). El centro cae en un bloque o entre bloques en cada eje y se fija en tu posición, en un punto (`center <x> <y> <z> [half [ejes]]`) o en el centro de la selección de WorldEdit. Subórdenes `on`, `off`, `toggle`, `mode`, `center`, `arms`, `worldedit` e `info` con botones, ayuda clicable (`/komaedit help symmetry`) y textos en `es_es` y `en_us`.
- Lo que pones y rompes a mano se repite al momento en las copias, con el estado girado o reflejado (escaleras con su esquina, troncos, puertas con su bisagra y sus dos mitades, camas, cofres dobles, raíles, vallas y muros conectados, carteles y estandartes con su `rotation` de 16 pasos, losas, botones y faroles al revés con el espejo en Y). Las copias nunca pisan bloques, se ajustan a sus vecinos, no se ponen si no se sostienen, solo se rompen si en su sitio está el mismo bloque y no salen del mundo, del borde, de chunks sin cargar, de la protección del spawn ni de zonas que un mod de protección prohíba (se le pregunta con el evento de rotura de Fabric).
- Con `/komaedit symmetry worldedit on` también se reflejan tus ediciones de WorldEdit y KomaEdit (`//set`, `//paste`, Build, pinceles…), dentro de la misma edición: un solo `//undo`, con la máscara global y el límite de bloques de WorldEdit, y como mucho `maxBlocksPerOperation` copias por edición.
- Mientras está activa, partículas que solo ve el jugador marcan los planos (rojo X, azul Z, verde Y), el eje y los brazos del radial.
- Permisos nuevos: `komaedit.build.symmetry.reflectional` y `.rotational` (paquete intermedio) y `.worldedit` (avanzado).
- Código: la geometría (`SymmetryCenter` en medios bloques, `SymmetryGeometry`, `StateTransform`, `SymmetryGuides`) es Java puro con pruebas JUnit de espejos, radiales, centros entre bloques, estados girados e idempotencia. La colocación a mano llega por un mixin en `BlockItem.place` (primer mixin común del mod, en `komaedit.mixins.json`) y las ediciones de WorldEdit, por el `EditSessionEvent` en la etapa `BEFORE_HISTORY`.

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
