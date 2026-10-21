# Paridad con ezEdits

Comparación, página a página, de la wiki pública de ezEdits (<https://ezedits.gitbook.io/ezedits>, revisada el 18/10/2026 y de nuevo el 21/10/2026 en su versión en inglés, con sus notas de versión públicas en GitHub y las gráficas Desmos que enlaza) con lo que hace KomaEdit. Todo se ha reimplementado a partir de esa documentación: no se ha descompilado el plugin ni copiado su código, y las listas (paletas, ruidos de serie, formas) son propias.

**Estados:** **Completo** (hace lo mismo, con los mismos nombres, parámetros y valores por defecto, aunque los números no salgan idénticos bloque a bloque), **Parcial** (existe, pero falta algo que se indica), **Falta** (no existe) y **En curso** (lo está haciendo otra rama).

**Diferencia general:** ezEdits es un plugin de Bukkit/Paper y sus órdenes van con `//ez…`; KomaEdit es un mod de Fabric y sus órdenes cuelgan de `/komaedit` (alias `/ke`, `/mx`, `/moxia`). Así, `//ezdeform noise` es `/komaedit deform noise`, `//ezbr gradient` es `/komaedit pincel gradient` y `//ezsel save` es `/komaedit sel save`. Las máscaras y los patrones (`#palette[…]`, `#near[…]`…) sí se escriben igual, dentro de cualquier orden de WorldEdit.

## ezEdits Docs (portada)

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| Extensión de WorldEdit/FAWE con órdenes, patrones, máscaras y herramientas | Mod de Fabric sobre WorldEdit 7.3.8 (`KomaEditMod`) | Completo |

## Installing

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| Necesita WorldEdit o FastAsyncWorldEdit, Paper, Java 21, Minecraft 1.21+ | Necesita WorldEdit para Fabric, Fabric 1.21.1, Java 21. FAWE no existe para Fabric | Completo (con WorldEdit; FAWE no aplica) |
| Carpeta `plugins/ezEdits/config.yml` | `config/komaedit/config.json` y `config/komaedit/palettes.json` (`KomaEditConfig`, `ServerPalettes`) | Completo |

## Permissions

KomaEdit tiene un nodo fino por orden o modo y cuatro paquetes (`komaedit.permpack.basic`, `intermediate`, `advanced` y `admin`) en `permission/PermissionNodes`; `/komaedit permisos` los lista con cuáles tienes. Los permisos amplios de siempre (`komaedit.build`, `komaedit.select`, `komaedit.use`…) siguen dándolo todo, así que nadie pierde acceso.

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| Permpacks `ezedits.permpack.*` (Basic, Intermediate, Advanced, Experimental, Admin) | `komaedit.permpack.basic`, `intermediate`, `advanced` (acumulativos) y `admin` | Completo (Experimental no hace falta: no hay funciones experimentales) |
| `ezedits.general.wiki`, `info`, `quiet` | `/komaedit info`, `/komaedit quiet [on\|off]` (alias `silencio`; se guarda por jugador entre sesiones, `persistence/QuietMode`) y `/komaedit wiki` (lleva a `/komaedit help`); sin permiso propio, como la ayuda | Completo |
| `ezedits.admin.reload`, `ezedits.admin.debug` | `komaedit.admin.reload` → `/komaedit reload`; `komaedit.admin.debug` → `/komaedit debugprint` (versiones, memoria, límites, visualizador y jugadores con el mod, con **[copiar]**) | Completo |
| `ezedits.brush.*` | `komaedit.build.brush.<tipo>` | Completo |
| `ezedits.deform.*` | `komaedit.build.deform.<modo>` | Completo |
| `ezedits.flow.*`, `ezedits.region.*`, `ezedits.smooth.*`, `ezedits.surface.*`, `ezedits.texture.*`, `ezedits.survival.*`, `ezedits.stainedglass.gradient` | `komaedit.build.<orden>[.<modo>]` | Completo |
| `ezedits.gen.*` | `komaedit.build.shape.<forma>` | Completo |
| `ezedits.palettes.*`, `ezedits.noise.*` | `komaedit.palette.<sub>`, `komaedit.noise.<sub>`, `komaedit.build.palette.place/swap` | Completo |
| `ezedits.selection.*`, `ezedits.tools.superwand` | `komaedit.select.<sub>`, `komaedit.select.superwand` | Completo |
| `ezedits.place`, `ezedits.array`, `ezedits.scatter` | `komaedit.build.place`, `.array`, `.scatter` | Completo |
| `ezedits.mask.*`, `ezedits.pattern.*` | Las máscaras y patrones no piden permiso propio: se usan dentro de órdenes de WorldEdit, que ya tienen los suyos | Parcial, a propósito: los permisos de KomaEdit solo suman, y un nodo por máscara dejaría sin ellas a quien hoy las usa con los permisos de WorldEdit y sin los de KomaEdit |
| `ezedits.debug`, `ezedits.debug.loadingbar` (`//ezdebug clearLoadingBars`, `removeVisualiserEntities`) | `/komaedit debug clearloadingbars` (`komaedit.debug.loadingbar`, paquete básico) y `/komaedit debug removevisualiserentities` (`komaedit.debug.visualiser`, admin: retira los block displays del visualizador y borra los de la vista previa que hubieran quedado en el mundo) | Completo |
| `ezedits.symmetry.reflectional`, `ezedits.symmetry.rotational` (`//ezsymmetry`, experimental) | `komaedit.build.symmetry.reflectional`, `.rotational` y `.worldedit` → `/komaedit symmetry` | Parcial: faltan `translational` y `update`, de los que la wiki solo da el nombre del permiso (ver [Symmetry](#symmetry-solo-en-la-página-de-permisos)) |
| `ezedits.stainedglass.image` (`//stainedglassimage`, experimental) | `komaedit.build.image` (paquete avanzado); las URL, además, con `komaedit.admin.image.url` si el admin lo pide | Completo |

## Palettes Explained

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `#nombre` (guardada) y `##Nombre` (de serie) | `gradient/PaletteSpec`, `SavedPalettes`, `GradientPalette`, `ServerPalettes` | Completo |
| `,` concatenar · `-` invertir · `(a:b)` tramo · `*n` repetir · `[…]` agrupar | `PaletteSpec.Parser` | Completo |
| `=` final para autocompletar | `command/ResultSuggestions`: con `=` al final, la sugerencia del chat (Brigadier) es la paleta desplegada en bloques, también dentro de `P:…`, de `#palette[…]` en WorldEdit y en las direcciones de `C(D:…)`; al ejecutar, el `=` se ignora | Completo (si la orden no cupiera en los 256 caracteres del chat, no se propone) |

## Default Palettes

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| 38 paletas de serie (`##Grayscale`, `##Glow*`, `##Fade*`, `##Magma`…) | Las mismas 38 con listas de bloques propias (`GradientPalette`) | Completo |
| `DefaultPalettes.json` del servidor para todos los jugadores | `config/komaedit/palettes.json` con `##nombre` (`ServerPalettes`), recargable con `/komaedit reload` | Completo |

## Palette Commands

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `fetch <WORLD\|SELECTION\|HOTBAR> <nombre> [largo] [-d] [-f]` | `/komaedit palette fetch` (`PaletteCommands`) | Completo |
| `save`, `delete`, `list [ALL\|DEFAULT\|MINE]`, `place`, `print [-v]` | `/komaedit palette …` | Completo (`list default` incluye las del servidor) |
| `swap <origen> <destino> [-a] [-s] [-f] [-b]` | `/komaedit palette swap` (`PaletteSwapOperation`) | Completo |
| `encode`, `decode` | `/komaedit palette encode` y `decode` (`PaletteCodec`) | Parcial, sin arreglo fiable: el código (`kp1:…`) es propio y admite bloques de mods. Los de ezEdits no se leen: la wiki no documenta su formato (solo que es de bloques vanilla), sus notas de versión públicas dicen que lo cambiaron para 1.21.5 y otra vez para 1.21.11 (va atado a la versión de Minecraft) y no hay ejemplos públicos; averiguarlo exigiría descompilar el plugin. `decode` lo explica y pide pegar la lista de bloques de su `//ezpalette decode` |

## Noise Explained

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| Tipos Perlin, OpenSimplex2, OpenSimplex2S, Value, ValueCubic, White, Cellular, Gabor | `noise/NoiseSpec` sobre FastNoiseLite | Completo |
| Tipo `Shard` con `Sharpness (h)` | `ExtendedNoise.Shard` | Completo (implementación propia: celdas planas con nitidez de 0 a 1) |
| Básicos `s`, `f`, `i`, `m` (Default/None/Override con `l`, `u`, `Mn`, `Mx`), `x`, `y`, `z` | `NoiseSpec` | Completo |
| Celular `cJ`; `cD` Euclidean, EuclideanSq, Manhattan, Hybrid | FastNoiseLite | Completo |
| `cD` Minkowski1, Minkowski4, Minkowski99 y Rounded | `ExtendedNoise.Cellular` | Completo (implementación propia) |
| `cR` CellValue, Distance…Distance2Exp, Edge | FastNoiseLite y `NoiseSpec.derivedCellular` | Completo |
| `cR` Rounded y NoiseLookup (`cN`, `cF`) | `ExtendedNoise.Cellular` | Completo (implementación propia; `cF` es la frecuencia en celdas, 0,1 por defecto) |
| Gabor `gR`, `gF`, `gFs`, `gO`, `gJ`, `gN` (máx. 8), `gM` | `NoiseSpec.GaborField` | Completo |
| Fractal `fT` None/FBm/Ridged/PingPong, `fP`, `fO`, `fL`, `fG`, `fW` | FastNoiseLite; `ExtendedNoise.fractal` para Shard y el celular propio | Completo |
| Distorsión `wT` BasicGrid, OpenSimplex2, OpenSimplex2Reduced con `wF`, `wO`, `wG`, `wA`, `wC`, `wl` | FastNoiseLite | Completo |
| `wT` Flow y Turbulence | `ExtendedNoise.warp` | Completo (implementación propia: rotacional de tres ruidos y fBm en valor absoluto) |

## Noise Commands

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//eznoise list [ALL\|DEFAULT\|MINE]`, `save [-f]`, `delete`, `print` | `/komaedit noise …` (`NoiseCommands`, `SavedNoises`) | Completo |
| Ruidos de serie | `ridged`, `smoothcells`, `voronoiedge` y seis propios (`NoiseSpec.PRESETS`) | Completo |

## Masks

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `#aim[<hitboxes>]`, `#attached[<dirs>]`, `#blocklight`, `#truelight`, `#lightsource` | `worldedit/ez/EzMaskParser`, `EzMasks` | Completo |
| `#clipblocks`/`#cb`, `#schemblocks`/`#sb`, `#current`/`#c` | `EzMaskParser` | Completo |
| `#eznoisemask`/`#eznm`, `#fullblock`, `#fuzzypalette`/`#fpalette`, `#infested`, `#near`, `#palette` | `EzMaskParser` | Completo |
| `#radialgradientmask`/`#rgradientm`, `#vectorgradientmask`/`#vgradientm` | `EzMaskParser`, `GradientMath` | Completo |
| `#hotbar` (solo en la página de permisos) | `EzMaskParser` (bloques de la barra rápida, por tipo) | Completo |
| `#savedselection` (solo en la página de permisos: «bloques dentro de la región de una selección guardada») | `#savedselection[nombre]` (alias `#savedsel`) en `EzMaskParser`: la forma de `/komaedit sel save` (caja, polígono, esfera, cilindro, convexa…) con la `RegionMask` de WorldEdit | Completo |

## Patterns

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `#aim`, `#palette`, `#eznoisepattern`/`#eznp`, `#ridged`, `#smoothcells`, `#voronoiedge` | `worldedit/ez/EzPatternParser` | Completo |
| `#radialgradientpattern`/`#rgradientp`, `#vectorgradientpattern`/`#vgradientp` | `EzPatternParser` | Completo |
| `#selection`/`#sel` | `EzPatternParser.selection` | Completo |
| `#hotbar` (solo en la página de permisos) | `EzPatternParser` (bloque al azar de la barra rápida) | Completo |

## Brushes

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezbrush` (`//ezbr`) con paletteshift, gradient, gradientstroke, petalpainter, place, scatter, array | `/komaedit pincel <tipo>` (`brush/ez/EzBrush`, `EzBrushType`, `BrushCommands`) | Completo |
| `//ezbr none` | `/komaedit pincel none` (también `quitar`, `off`) | Completo |

## Petal Painter Brush

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `petalpainter <tipo> [radio=8] [densidad=0.3] [-n White()] [-z 0.03] [-m #fullblock]` | `PetalPainter`, `PaintOperations.Petals`; `-m` con cualquier máscara de WorldEdit (`BrushMasks`) | Parcial: `wildflowers` y `leaf_litter` necesitan Minecraft 1.21.5 (el mod está en 1.21.1) |

## Palette Shift Brush

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `paletteshift <paleta> [radio=8] [desplazamiento=1]`, clic derecho/izquierdo en sentidos opuestos | `PaletteShift`, `PaintOperations.Shift` | Completo |

## Palette Gradient Brushes

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `gradient <paleta> [radio=8] [interp=NONE] [bleed=0.5] [-a] [-v] [-w] [-n] [-z 1] [-d]` | `GradientPainter`, `PaintOperations.Gradient` | Completo |
| `gradientstroke … [interp=LINEAR] … [-a] [-d] [-v] [-w] [-x] [-n] [-z]` | `PaintOperations.Stroke`, `StrokePath` | Completo |
| Interpolaciones NONE, LINEAR, TAPERED, BEZIER, SIN | `GradientInterpolation` | Completo |
| Integración con WorldEditCUI | Partículas propias en los puntos | Parcial, sustituida: los puntos del trazo salen con partículas de KomaEdit y no se mandan a WorldEditCUI (KomaEdit no usa su protocolo; la selección de WorldEdit sí la sigue viendo) |

## Placement Brushes

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezbr place` con todas las estructuras, alineaciones y parámetros | `/komaedit pincel place` → `PlacementService.startBrush` | Completo (con `-p` se usa la colocación de giros de 90° con holograma de antes) |
| `//ezbr scatter` (región `-h`, por defecto `Box` de 40³ en el clic) | `/komaedit pincel scatter` | Completo |
| `//ezbr array` a lo largo del trazo | `/komaedit pincel array` (clic izquierdo marca el trazo, derecho coloca) | Completo |
| `ViewDiff` (segundo clic) | Pincel `place` | Completo |
| `Constant(D:aim)` = mirada al configurar | `PlacementSettings.bindAim`: al configurar el pincel, las direcciones fijas que dependen de la mirada (`C(D:aim)`, `C(D:left)`, `-k aim`…) se calculan una vez; `Aim` y `Upward` siguen la de cada clic. `-mirada clic` (alias `-aim click`) vuelve a lo de antes | Completo |
| Visualizador de la alineación con BlockDisplay y su configuración | `AlignmentGuide` (el mismo cálculo que el clic): con KomaEdit en el cliente, flechas como las del gizmo (verde la primaria, roja la secundaria, azul +z, a trazos la secundaria pedida) con sus nombres; sin el mod, block displays de hormigón solo para quien pincela. `config/komaedit/visualisations.json` (`enabled`, `updateTicks`, `vanillaDisplays`) y un interruptor por jugador en `/kegui ajustes` | Completo |

## Superwand

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| Modos TRACE, POSITION, RELATIVE, GRAB y sus controles (clics, Mayús, F, Mayús+F) | `/komaedit superwand` (`brush/superwand/SuperwandManager`) | Completo |

## Smoothblocks

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| Perfiles `Slabs`, `SlabsAndStairs`, `SlabsAndStairs2D`, `Layers`, `SSW`, `Panes`; Coverage `C:`; materiales por variante (`Slab:`, `Stair:`, `Pane:`…); color más cercano | `-w <perfil>(C:…,Slab:…)` (`deform/SmoothProfile`, `shape/smooth/PieceFit`, `PieceSolver`, `SmoothMaterials`) | Completo (`Panes` solo sale en un ejemplo de la wiki: paneles de vidrio y barrotes unidos como en Minecraft) |

## Deformation

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `hexagonalize [12] [0] [0] [0] [60] [-w]` | `/komaedit deform hexagonalize` (`Deformations`) | Completo |
| `noise <ruido> [2] [-z 1] [-s -1] [-h] [-v] [-w]` | `/komaedit deform noise` | Completo |
| `rotate <ángulo> [-o] [-w]` | `/komaedit deform rotate` | Completo |
| `voronoialize [12] [0] [-s] [-w]`, `voronoialize2 <n> [0] [-s] [-r 15] [-n 5] [-w]` | `/komaedit deform voronoialize` y `voronoialize2` | Completo |
| `voxelize [3] [0] [0] [-i y] [-j -x] [-s] [-h] [-v] [-w]` | `/komaedit deform voxelize` | Completo |

## Cylindrify & Spherify

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `cylindrify [-afost] [-r] [-d] [-x] [-i y] [-j x] [-w]` | `/komaedit deform cylindrify` (`deform/Projection`) | Completo |
| `spherify [-aflost] …` (Mercator o LAEA con `-l`) | `/komaedit deform spherify` | Completo |

## Flowfields

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezflowfield <paleta> <líneas\|n%> [32] [1] [1.0] [Perlin()] [-m] [-h] [-i] [-g] [-j] [-b] [-x/-y/-z] [-p 1:1] [-s] [-c] [-f] [-t]` | `/komaedit flowfield` (`generator/FlowField`; `-m` con `region/RegionMasks`) | Completo |
| `//ezflowline <patrón> <largo> [-1] [Perlin()] [-i] [-c] [-l 1] [-r 50] [-s]` | `/komaedit flowline` (`generator/FlowLine`) | Completo |

## Noisegen

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `heightmap <paleta> <ruido> [0] [-z] [-s] [-o] [-m] [-cty]` | `/komaedit noisegen heightmap` (`generator/NoiseGenerator`; `-m` con `region/RegionMasks`) | Completo |
| `terrain … [0] [1,0.5,0] [-z] [-s] [-l] [-o] [-c]` | `/komaedit noisegen terrain` | Completo |
| `advanced … [0] [0.5] [-chnty]` con umbrales como expresión | `/komaedit noisegen advanced` | Completo (las fórmulas usan el compilador de KomaEdit, no el de WorldEdit) |

## Region

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezvines <máscara> <patrón> [10%] [2] [5]` | `/komaedit vines` (`region/VinesOperation`) | Completo |
| `//ezmoss <patrón> [2.0] [1] [5]` | `/komaedit moss` (`MossOperation`) | Completo |
| `//ezslabmerge <máscara> [-b] [-t]` | `/komaedit slabmerge` (`SlabMergeOperation`) | Completo |
| `//ezstatecycle <máscara> <estado>` | `/komaedit statecycle` (`StateCycleOperation`) | Completo |

## Placement

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezplace`, `//ezscatter`, `//ezarray` y sus pinceles | `/komaedit place`, `scatter`, `array` (`PlacementCommands`, `PlacementService`) y `/komaedit pincel place\|scatter\|array` | Completo |
| `-w <smoothblocks>` en las tres | `-w <perfil>(C:…,Slab:…)` | Completo |
| `//ezspline structure` | `/komaedit ezspline structure` (`EzSplineCommands`, `TubeBender.Section`): radios por fotogramas, `-t` y `-r` | Completo |

## Available Structures

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `Clipboard` (`O`, `PM` FAST/SMOOTHED, `FB`, `R`) y `Schematic` (`N` regex, `%p`, `F`, `O`, `PM`, `R`) | `placement/StructureSpec`, `VoxelStructure` | Completo |
| `Shape` con las 17 formas y `=fórmula`, `Expression`, `TexturedShape` | `ProceduralShapes`, `ProceduralStructure` | Completo (fórmulas con el compilador de KomaEdit, sin bucles de WorldEdit) |
| `Icosphere` (`P`, `R`, `S`, `PL`) | `placement/Icosphere` | Completo (sin depender de Arceon) |
| Opciones comunes `RO`, `WO`, `OJ` | `StructureSpec.offsets` | Completo |

## Primary+Secondary Alignment

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| Constant, Random, Noise, Aim, Upward, PlayerRelative, SurfaceNormal, Expression, Tangential, Orthogonal, mezclas `n%modo` | `placement/Alignment`, `AlignmentFrame`, `DirectionExpr` | Completo |
| ViewDiff (solo pinceles) | `Alignment.ViewDiff` en `/komaedit pincel place` | Completo |
| `-j` MULTIPLES_* y DIAGONALS_*, `-x` | `AlignmentFrame.Snap` | Completo |

## Placement Parameters

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `-s`, `-o 1,1`, `-k y` + `-c 0`, `-f`, `-r`, `-a` | `PlacementSettings`, `PlacementBuilder` | Completo |

## Scatter Parameters

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `-h` Box/Ellipsoid/Cylinder (`D`), Saved, Active (`UseOriginalPosition`), `Offset` | `PlacementService.region` | Completo |
| `-d`/`-e 0.5`, `-m`, `-n 2%`, `-i -1`, `-u 15`, `-l`, `-t` | `ScatterPlanner` | Completo |

## Array Parameters

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `-g 0`, `-y`, `-q`, `-p t:b:c`, `-n CONSISTENT\|HORIZONTAL\|UPRIGHT` | `ArrayPlanner`, `PlacementPath` | Completo |
| `-b` hasta 96 bloques (configurable en ezEdits) | `-b` hasta `arraySnapRadius` bloques de `config.json` (96 por defecto, 1–256; `PlacementService.snapRadius`), menos si la caja no cabe en el límite de lectura | Completo |

## Selections

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//selload [-h]`, `//next`, `//selhere [POS1\|POS2\|CENTER]`, `//ezselinvert`, `//delpos2` | `/komaedit sel load\|next\|here\|invert\|delpos2` (`SelectionCommands`, `SelectionBridge`) | Completo |
| `//encapsulate`, `//encapsulatenear` | `/komaedit sel encapsulate\|encapsulatenear` | Completo |
| `//selcatenary <puntos> [0.1] [down]` | `/komaedit sel catenary` (`PathGenerators.catenarySelection`) | Completo (`sagDirection` admite `down`, `up`, `left` y `right`) |
| `//ezsel list [-g]`, `load`, `save [-f]`, `delete` | `/komaedit sel …` (`SavedSelections`) | Completo |

## Shapes

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `cone`, `polydome`, `polygon`, `square`, `tetrahedron`, `torus` con sus banderas | `/komaedit shape …` (`generator/ShapeRequest`, `ShapeVoxelizer`) | Completo |

## Smoothing

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezsmooth <radios> <iteraciones> <sesgo> [-w]` | `/komaedit smooth` (`deform/SmoothingOps`) | Completo |
| `//ezinflate`, `//ezdeflate <radios> [-w]` | `/komaedit inflate\|deflate` | Completo |
| `//ezsmoothblocks <perfil> <radio> <sesgo>` | `/komaedit smoothblocks` | Completo |

## Spline (y Common Parameters, 2D, 3D y Advanced Spline Shapes)

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezspline` (`//ezsp`) con la sintaxis exacta de subcomandos, formas y parámetros largos y abreviados | `/komaedit ezspline` (alias `ezsp`, `spline`; `EzSpline`, `EzSplineCommands`) sobre las figuras de espacio de tubo | Completo |
| Parámetros comunes `<radii>`, `-p`, `-q FAST\|BALANCED\|HIGH\|EXACT` (y el `-q <muestras>` antiguo), `-r a[,b]`, `-s`, `-t`, `-n`, `-e`, `-h` | Los mismos (`TubeCommandParser`; `-q EXACT` mide el volumen con 64 muestras) | Completo |
| `-w <smoothblocks>` | Los perfiles de `-w` de KomaEdit (`SmoothProfile`), `Panes` incluido | Completo |
| 2D: Circle, Square, Diamond, RoundedSquare, SuperCircle, CirclesCircle, Strands, Pipe, Polygon, Rectangle, Star, Flower; `basic` y `rope` | Perfiles de `sweep` con sus parámetros (`TubeShapes.Profile`) | Completo |
| 3D: Beads, Cubes, Chainlink, Fishnet, Honeycomb, Oscillate, Rings, Scales, Noodles | Formas implícitas propias (`EzShapes`) | Completo |
| 3D: Braids | `EzShapes.Braids` con la fórmula de la gráfica Desmos pública que enlaza la wiki: tres hebras de radio `T/(1+T)` desfasadas 2π/3, `Thickness` (0,5) y `Gap` (`Br(G:1)` sale en un ejemplo de la wiki: aquí separa los cruces) | Completo |
| `//ezspline noise <paleta> <radios> [ruido] [profundidad] [-i expresión]` | `EzShapes.NoiseCut` con cualquier ruido y expresión de corte (`n`, `d`) | Completo |
| `//ezspline expression <paleta> <radios> [-z] [-o] <expresión>` | Figura Expresión con `-z` y `-o` | Completo |

## Stained Glass

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezstainedglassgradient <inicio> [fin] <capas> [1] [6] [dirección] [-c #000000] [-b] [-s]` | `/komaedit glassgradient` (`glass/GlassGradientSolver`) | Completo |
| `//stainedglassimage` (experimental, solo en permisos: «genera una imagen con capas de cristal tintado») | `/komaedit image` (alias `imagen`, `stainedglassimage`; `image/ImageService`, `ImageConverter`, `ImageQuantizer`): archivo de `config/komaedit/images/` o URL `https` permitida; PNG, JPG y GIF; en el plano que miras o ajustada a la selección; vidrio, paneles, lana, hormigón, terracota, bloques enteros o `##Nombre`; color en CIELAB; tramado Floyd–Steinberg u ordenado | Completo, con parámetros propios (la wiki no los documenta). Una capa de vidrio por píxel: no apila capas como `glassgradient` |

## Surface

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `fuzzify`, `rockify [10] [1] [1] [4]`, `voronoify [12]`, `noisify <ruido> [1] [1] [4]` con `-c`, `-e`, `-t`, `-w` | `/komaedit surface …` (`deform/SurfaceOps`) | Completo |
| `-m <máscara>` | Cualquier máscara de WorldEdit sobre la foto (`region/RegionMasks`); la lista de bloques y etiquetas con `!` de antes sigue valiendo | Completo |

## Tweak (Survival)

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//eztweak` (`//eztw`, antes `//ezsurvival`) `deinfest`, `persist [ON\|OFF]`, `waterlog [True\|False]`, `wax [ON\|OFF]` | `/komaedit retocar desinfestar\|hojas\|anegar\|encerar [on\|off]` (los nombres de antes, `tweak deinfest\|persist\|waterlog\|wax` y `survival`, siguen valiendo y avisan del nuevo; `region/TweakOperation`, `TweakRules`; los carteles, con sus datos en `region/SignWax`) | Completo |

## Texturing

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `ambient`, `axisgradient`, `blend`, `blocklight`, `cells`, `curvature`, `flow`, `noise`, `pointlight`, `shift`, `sunlight` con sus valores por defecto | `/komaedit texture …` (`texture/TextureFields`, `TextureOperation`) | Completo |
| `advanced` con `Add`, `Subtract`, `Multiply`, `Divide`, `WeightedAverage`, `Darken`, `Lighten`, `Difference`, `Screen`, `Adjust`, `Invert`, `Blend` y `@@ruido(…)` | `texture/TextureSpec` | Completo |

## Symmetry (solo en la página de permisos)

La wiki solo nombra `//ezsymmetry` y sus cuatro permisos, sin parámetros ni comportamiento; el modo de KomaEdit tiene un diseño propio.

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezsymmetry reflectional` y `reflectional3d` | `/komaedit symmetry mode x\|z\|xz\|y\|xzy…` (alias `simetria`, `sym`; `symmetry/SymmetryGeometry`, `SymmetryService`): espejos en cualquier combinación de ejes, centro en bloque o entre bloques, en tu posición, en un punto o en el centro de la selección | Completo (diseño propio) |
| `//ezsymmetry rotational` | `/komaedit symmetry mode radial <2-16> [y]` alrededor de un eje vertical | Completo (diseño propio) |
| — | Estados girados y reflejados (`StateTransform`), copias de lo que se pone y rompe a mano, partículas en los planos y `worldedit on` para reflejar ediciones de WorldEdit y KomaEdit en un solo `//undo` (`SymmetryExtent`) | Propio |
| `//ezsymmetry translational` | — | Falta: la wiki solo nombra el permiso («Starts translational symmetry mode»), sin parámetros ni comportamiento |
| `//ezsymmetry update` («actualiza los bloques según la simetría») | — | Falta: la wiki solo nombra el permiso, sin decir qué bloques toma de origen ni sobre qué región |

## Lo que queda fuera

- **Simetría traslacional y `update`** (`//ezsymmetry translational|update`): la wiki no dice qué hacen más allá del nombre.
- **Capas apiladas en la vidriera de imagen**: `/komaedit image` pone una capa de vidrio por píxel; mezclar varias capas tintadas por píxel, como hace `glassgradient`, queda para más adelante.
- **Código de `palette encode`** compatible con el de ezEdits: no está documentado, va atado a la versión de Minecraft y no hay ejemplos públicos; sin descompilar no hay forma fiable de leerlo.
- **Permiso por máscara y patrón** (`ezedits.mask.*`, `ezedits.pattern.*`): a propósito, para no quitar las máscaras a quien hoy las usa con los permisos de WorldEdit.
