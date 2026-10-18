# Paridad con ezEdits

Comparación, página a página, de la wiki pública de ezEdits (<https://ezedits.gitbook.io/ezedits>, revisada el 18/10/2026 en su versión en inglés) con lo que hace KomaEdit. Todo se ha reimplementado a partir de esa documentación: no se ha descompilado el plugin ni copiado su código, y las listas (paletas, ruidos de serie, formas) son propias.

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
| `ezedits.general.wiki`, `info`, `quiet` | `/komaedit info` (sin permiso propio, como la ayuda) | Parcial: falta `quiet` (silenciar mensajes) y `wiki` (la ayuda es `/komaedit help`) |
| `ezedits.admin.reload`, `ezedits.admin.debug` | `komaedit.admin.reload` → `/komaedit reload` | Parcial: falta `debugprint` |
| `ezedits.brush.*` | `komaedit.build.brush.<tipo>` | Completo |
| `ezedits.deform.*` | `komaedit.build.deform.<modo>` | Completo |
| `ezedits.flow.*`, `ezedits.region.*`, `ezedits.smooth.*`, `ezedits.surface.*`, `ezedits.texture.*`, `ezedits.survival.*`, `ezedits.stainedglass.gradient` | `komaedit.build.<orden>[.<modo>]` | Completo |
| `ezedits.gen.*` | `komaedit.build.shape.<forma>` | Completo |
| `ezedits.palettes.*`, `ezedits.noise.*` | `komaedit.palette.<sub>`, `komaedit.noise.<sub>`, `komaedit.build.palette.place/swap` | Completo |
| `ezedits.selection.*`, `ezedits.tools.superwand` | `komaedit.select.<sub>`, `komaedit.select.superwand` | Completo |
| `ezedits.place`, `ezedits.array`, `ezedits.scatter` | `komaedit.build.place`, `.array`, `.scatter` | Completo |
| `ezedits.mask.*`, `ezedits.pattern.*` | Las máscaras y patrones no piden permiso propio: se usan dentro de órdenes de WorldEdit, que ya tienen los suyos | Parcial |
| `ezedits.debug`, `ezedits.debug.loadingbar` (`//ezdebug …`) | No hay barras de progreso ni entidades de visualización que limpiar | Falta (no aplica) |
| `ezedits.symmetry.*` (`//ezsymmetry`, experimental) | — | Falta (grande, fuera de esta rama) |
| `ezedits.stainedglass.image` (`//stainedglassimage`, experimental) | — | Falta (grande, fuera de esta rama) |

## Palettes Explained

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `#nombre` (guardada) y `##Nombre` (de serie) | `gradient/PaletteSpec`, `SavedPalettes`, `GradientPalette`, `ServerPalettes` | Completo |
| `,` concatenar · `-` invertir · `(a:b)` tramo · `*n` repetir · `[…]` agrupar | `PaletteSpec.Parser` | Completo |
| `=` final para autocompletar | Se acepta y se ignora; `/komaedit palette print` da la lista desplegada | Parcial (no se autocompleta en el chat) |

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
| `encode`, `decode` | `/komaedit palette encode/decode` (`PaletteCodec`) | Parcial: el código (`kp1:…`) es propio y no se entiende con el de ezEdits; a cambio admite bloques de mods |

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
| `#savedselection` (solo en la página de permisos) | — (`#region` de WorldEdit con `/komaedit sel load` cubre el caso) | Falta |

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
| `petalpainter <tipo> [radio=8] [densidad=0.3] [-n White()] [-z 0.03] [-m #fullblock]` | `PetalPainter`, `PaintOperations.Petals` | Parcial: `wildflowers` y `leaf_litter` necesitan Minecraft 1.21.5 (el mod está en 1.21.1); `-m` es una lista de bloques, no una máscara de WorldEdit |

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
| Integración con WorldEditCUI | Partículas propias en los puntos | Parcial (sustituida) |

## Placement Brushes

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezbr place` con todas las estructuras, alineaciones y parámetros | `/komaedit pincel place` → `PlacementService.startBrush` | Completo (con `-p` se usa la colocación de giros de 90° con holograma de antes) |
| `//ezbr scatter` (región `-h`, por defecto `Box` de 40³ en el clic) | `/komaedit pincel scatter` | Completo |
| `//ezbr array` a lo largo del trazo | `/komaedit pincel array` (clic izquierdo marca el trazo, derecho coloca) | Completo |
| `ViewDiff` (segundo clic) | Pincel `place` | Completo |
| `Constant(D:aim)` = mirada al configurar | En los pinceles nuevos `aim` es la mirada de cada clic | Parcial |
| Visualizador de la alineación con BlockDisplay y su configuración | — | Falta |

## Superwand

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| Modos TRACE, POSITION, RELATIVE, GRAB y sus controles (clics, Mayús, F, Mayús+F) | `/komaedit superwand` (`brush/superwand/SuperwandManager`) | Completo |

## Smoothblocks

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| Perfiles, materiales y Coverage de `-w` y `//ezsmoothblocks` | `-w Slabs|SlabsAndStairs(C:…)` (`deform/SmoothProfile`) | En curso (`agent/ez-smoothblocks`) |

## Deformation

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `hexagonalize [12] [0] [0] [0] [60] [-w]` | `/komaedit deform hexagonalize` (`Deformations`) | Completo |
| `noise <ruido> [2] [-z 1] [-s -1] [-h] [-v] [-w]` | `/komaedit deform noise` | Completo |
| `rotate <ángulo> [-o] [-w]` | `/komaedit deform rotate` | Completo |
| `voronoialize [12] [0] [-s] [-w]`, `voronoialize2 <n> [0] [-s] [-r 15] [-n 5] [-w]` | `/komaedit deform voronoialize(2)` | Completo |
| `voxelize [3] [0] [0] [-i y] [-j -x] [-s] [-h] [-v] [-w]` | `/komaedit deform voxelize` | Completo |

## Cylindrify & Spherify

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `cylindrify [-afost] [-r] [-d] [-x] [-i y] [-j x] [-w]` | `/komaedit deform cylindrify` (`deform/Projection`) | Completo |
| `spherify [-aflost] …` (Mercator o LAEA con `-l`) | `/komaedit deform spherify` | Completo |

## Flowfields

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezflowfield <paleta> <líneas\|n%> [32] [1] [1.0] [Perlin()] [-m] [-h] [-i] [-g] [-j] [-b] [-x/-y/-z] [-p 1:1] [-s] [-c] [-f] [-t]` | `/komaedit flowfield` (`generator/FlowField`) | Parcial: `-m` es una lista de bloques y estados, no la sintaxis completa de máscaras |
| `//ezflowline <patrón> <largo> [-1] [Perlin()] [-i] [-c] [-l 1] [-r 50] [-s]` | `/komaedit flowline` (`generator/FlowLine`) | Completo |

## Noisegen

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `heightmap <paleta> <ruido> [0] [-z] [-s] [-o] [-m] [-cty]` | `/komaedit noisegen heightmap` (`generator/NoiseGenerator`) | Parcial: `-m` es un subconjunto de máscaras |
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
| `-w <smoothblocks>` en las tres | `-w slabs\|stairs[:cobertura]` | En curso (perfiles completos en `agent/ez-smoothblocks`) |
| `//ezspline structure` | — | En curso (`agent/ez-splines`) |

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
| `-b` hasta 96 bloques (configurable en ezEdits) | `-b` hasta 96 bloques (`PlacementService.SNAP_RADIUS`), menos si la caja no cabe en el límite de lectura | Parcial: el alcance no se puede cambiar en la configuración |

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
| `//ezsmoothblocks <perfil> <radio> <sesgo>` | `/komaedit smoothblocks` | En curso (`agent/ez-smoothblocks`) |

## Spline (y Common Parameters, 2D, 3D y Advanced Spline Shapes)

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezspline basic\|2d\|3d\|noise\|expression\|rope\|structure` con sus formas y parámetros comunes (`-p`, `-q`, `-r`, `-s`, `-t`, `-n`, `-e`, `-w`, `-h`) | Figuras de espacio de tubo (`shape/tube`, `/komaedit build …`) | En curso (`agent/ez-splines`: sintaxis exacta de `//ezspline` y lista de formas) |

## Stained Glass

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//ezstainedglassgradient <inicio> [fin] <capas> [1] [6] [dirección] [-c #000000] [-b] [-s]` | `/komaedit glassgradient` (`glass/GlassGradientSolver`) | Completo |
| `//stainedglassimage` (experimental, solo en permisos) | — | Falta |

## Surface

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `fuzzify`, `rockify [10] [1] [1] [4]`, `voronoify [12]`, `noisify <ruido> [1] [1] [4]` con `-c`, `-e`, `-t`, `-w` | `/komaedit surface …` (`deform/SurfaceOps`) | Completo |
| `-m <máscara>` | Lista de bloques y etiquetas con `!` | Parcial: no admite la sintaxis completa de máscaras de WorldEdit |

## Tweak (Survival)

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `//eztweak` (`//eztw`, antes `//ezsurvival`) `deinfest`, `persist [ON\|OFF]`, `waterlog [True\|False]`, `wax [ON\|OFF]` | `/komaedit tweak …` (alias `retocar` y `survival`; `region/TweakOperation`, `TweakRules`) | Parcial: `wax` no encera carteles (lo guardan en sus datos, no en el estado) |

## Texturing

| ezEdits | KomaEdit | Estado |
| --- | --- | --- |
| `ambient`, `axisgradient`, `blend`, `blocklight`, `cells`, `curvature`, `flow`, `noise`, `pointlight`, `shift`, `sunlight` con sus valores por defecto | `/komaedit texture …` (`texture/TextureFields`, `TextureOperation`) | Completo |
| `advanced` con `Add`, `Subtract`, `Multiply`, `Divide`, `WeightedAverage`, `Darken`, `Lighten`, `Difference`, `Screen`, `Adjust`, `Invert`, `Blend` y `@@ruido(…)` | `texture/TextureSpec` | Completo |

## Lo que queda fuera

- **Simetría** (`//ezsymmetry reflectional|rotational|translational|update`) y **vidriera de imagen** (`//stainedglassimage`): las dos son experimentales en ezEdits y piden un sistema propio (reflejar cada edición en tiempo real; leer y cuantizar imágenes). Son trabajos grandes para ramas aparte.
- **`/ezedits quiet`, `debugprint`, `wiki` y `//ezdebug`**: KomaEdit no tiene barras de progreso ni entidades de visualización que limpiar, y su ayuda está en `/komaedit help`.
- **Visualizador de alineación** de los pinceles de colocación (BlockDisplay por jugador).
- **`#savedselection`** como máscara.
- **Máscaras completas** en `surface -m`, `noisegen -m`, `flowfield -m` y `petalpainter -m`: hoy son listas de bloques.
- **Código de `palette encode`** compatible con el de ezEdits (no está documentado).
- **Carteles en `tweak wax`**.
