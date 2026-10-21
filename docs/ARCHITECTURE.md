# Arquitectura de KomaEdit

## Principios

KomaEdit separa el dominio de edición del acceso a Minecraft. La regla central es: **ninguna lectura ni escritura de mundo se ejecuta en un worker**.

Los workers reciben datos inmutables (`WorldSnapshot`, parámetros, trayectorias) y producen planes compactos. WorldEdit consume esos planes exclusivamente desde el hilo del servidor.

## Módulos actuales

| Paquete | Responsabilidad |
| --- | --- |
| `shape` | Muestreo Catmull–Rom y voxelización de figuras 3D |
| `path` | Trayectorias por jugador |
| `brush` | Estampado orientado de figuras existentes; `brush.paint`: el pincel de pintura (huella, degradados, trazo y pasadas en Java puro; `PaintStrokes` lleva cada trazo a una sola edición) |
| `sculpt` | Brochas de volumen/altura, máscaras, perfiles y previews; `sculpt.model`: pinceles de modelado (esculpir, roca, soldar, fundir, rugosidad, grietas) como lógica pura sobre `ModelGrid` y su `ModelOperation` |
| `clipboard` | Escalar y girar el portapapeles: Scale2x, Scale3x y RotSprite en 3D sobre `VoxelVolume` (puros) y `ClipboardScaleService`, que lee y sustituye el portapapeles de WorldEdit |
| `noise` | Perlin, fBM, ridged y Worley deterministas |
| `terrain` | Terraformado sobre mapas de alturas: sellos, rampas, ríos, erosión y paleta de superficie |
| `placement` | Colocación de estructuras (place, array, scatter): alineación, formas procedurales, dispersión y voxelizado |
| `edit` | Plan compacto (comprimido por páginas y volcado a disco si es enorme: `PlanPages`, `PlanSpill`), la cola de colocación (`BatchEditService`), la colocación por ventanas (`PlacementRun`) y los avisos de límite (`EditLimits`) |
| `performance` | Pool acotado y cancelación de cálculos (`AsyncEngine`), cuotas (`OperationPolicy`) y el trabajo en el hilo del servidor: presupuesto por tick (`TickBudget`), reparto por turnos (`FairShare`), cola (`JobQueue`, `QueueEstimate`, `RateMeter`) y su unión con Minecraft (`ServerWork`) |
| `preview` | Hologramas de trayectoria y vista previa universal de los planes (bandera `-v`, plan compacto para el cliente o displays, confirmación, `repeat`) |
| `gizmo` | Fantasma con gizmo: colocar moviendo, girando y volteando lo calculado. Lógica pura con pruebas (`Orientation`, `GhostTransform`, `GhostModel`, `GhostPlacement`, `OrientedStates`, `GizmoMath`, `GizmoLayout`, `GizmoGeometry`, `GhostSession`, `GroundSnap`, `GhostValidation`, `GhostScope`, `GizmoKeys`, `GizmoSettings`), su adaptador a los estados de Minecraft (`GhostStates`), la red (`GhostPayloads`, `GhostNetwork`) y el servidor (`GhostService`); el cliente está en `client.gizmo` (`GhostClient`) y lo dibujan `client.world.GhostRenderer` y `GizmoRenderer` |
| `reference` | Imágenes de referencia, solo del cliente: lógica pura con pruebas (`ReferenceImage` con su `ScreenFrame` y su `WorldFrame`, el giro como cuaternión `ReferenceRotation`, `ReferenceGrid`, el gizmo con esquinas `ReferenceGizmo` y su arrastre `ReferenceDrag`, `ReferenceSet` por mundo o servidor y la carpeta `ReferenceFiles`); el cliente está en `client.reference` (`ReferenceClient`, `ReferenceWindows`, `ReferenceScreen`, `ReferenceTextures`) y el plano lo dibuja `client.world.ReferenceWorldRenderer` con el `GizmoRenderer` del fantasma |
| `feedback` | Lo que se ve, suena y se escribe en el mundo: estilo común de los mensajes (`Feedback`), avisos de sonido, selección y simetría para el cliente (`WorldOverlaySync`) y los ajustes del cliente (`WorldFeedbackSettings`, Java puro); el cliente lo dibuja en `client.world` |
| `progress` | Barra de progreso de las operaciones largas |
| `gradient` | Paletas y selección streaming por eje |
| `color` | Colores de bloque en OKLab (`BlockColorCatalog`), parecidos, degradados (`ColorGradient`) y sombreado (`ShadeOperation`, con `texture.ShadeField`) |
| `generator` | Generadores de ezEdits (formas, noisegen, flowfield, flowline): lógica pura sobre `Volume` y `GeneratedBlocks` |
| `nature` | Árboles y rocas: especies, colonización del espacio, copas, raíces y rocas como lógica pura sobre `NatureTerrain` y `NatureVoxels`; `NatureOperation` y `NatureService` los llevan al worker y al motor común |
| `roof` | Tejados: planta (selección o muros), campos de distancia a medio bloque, piezas y paredes; Java puro sobre `RoofWorld` |
| `loft` | Superficies entre curvas: malla, interpolación (lineal, Catmull–Rom, catenaria) y voxelizado inverso estanco |
| `image` | Imagen en bloques (`/komaedit image`): lectura y descarga con límites, reescalado, cuantización en CIELAB y tramado, probados sin Minecraft; `ImageService` lo une al mundo y a la cola de edición |
| `text` | Texto en bloques (`/komaedit text`): fuentes TrueType (de serie con OFL y propias), rasterizado en muestras, borde por distancia exacta y colocación según la mirada; `TextModel` es un `DeformField` para las piezas de `-w` |
| `analysis` | Lista de materiales (`/komaedit analyze`): recuento sobre la foto y coste en survival (`SurvivalCost`, lógica pura con un registro inyectado), pilas, cofres y CSV |
| `utility` | Gravedad, nieve, drenar e inundar: motores puros sobre `RegionSnapshot` con `BlockKinds` (las reglas de Minecraft en `MinecraftKinds`) y sus `RegionOperation` en `UtilityOperations` |
| `biome` | Pintar biomas: celdas de 4×4×4 (`BiomeCells`, pura) y una cola por ticks con `EditSession.setBiome` para el //undo y el reenvío de chunks de WorldEdit |
| `blueprint` | Biblioteca de planos: almacén en disco (`BlueprintLibrary`, esquema + JSON + PNG), búsqueda por etiqueta, miniatura isométrica por trazado de vóxeles (`BlueprintThumbnail`), troceado de miniaturas por red (`ThumbnailChunks`) y el pincel estampar (`StampPlanner`), todo probado sin Minecraft; `BlueprintService` lo une a WorldEdit y a la colocación (`Bp(…)`) |
| `persistence` | Proyecto por mundo, migraciones, journal, backups y escritura atómica |
| `permission` | Permisos granulares y excepciones de cuota |
| `symmetry` | Modo simetría: geometría pura (centros, espejos, radial, estados), copias a mano y extensión de WorldEdit |
| `config` | Límites del servidor y política de compatibilidad |
| `worldedit` | Adaptación de jugador, sesión, selección y patrones |
| `command` | API Brigadier y validación de entrada |
| `capability` | Capacidades de construcción a mano: reglas puras, red, servidor y aplicación con WorldEdit |
| `client.gui` | Pantalla no bloqueante del Studio (`ProceduralStudioScreen`: el mundo, la cámara, los clics sobre la trayectoria y el teclado; el panel es `studio.ui`) |
| `studio.ui` | El Procedural Studio entero sin Minecraft: `StudioView` (estado, ratón y teclado de barra, etapas, contenido, pie, paleta `Ctrl+K`, historial y biblioteca de bloques), las etapas (`FigureStages`, `ColorStage`, `BlueprintStage`), los controles de `StudioUi` (con `NumberView`/`NumberEdit` para los números) y las miniaturas que dibujan los generadores (`ChoiceThumbnails`). Dibuja sobre un `StudioCanvas`: en el juego, `client.gui.studio.DrawContextCanvas`; en las pruebas, una imagen con la fuente y los iconos del juego, de donde salen las maquetas. Lo que pide al juego pasa por `StudioHost` (`ClientStudioHost` en el cliente) |
| `studio.world` | El ratón en el mundo con el Studio abierto, en Java puro: la caja de selección con sus caras (`BoxSelect`), los puntos que se arrastran (`PathDrag`) y el trazo de pintar manteniendo (`BrushStroke`) |
| `studio.tools` | Etapa Herramientas del Studio: cada tarjeta (`ToolCard`) declara sus ajustes (con su unidad, `ValueUnit`), arma la orden de chat que manda Construir, sus nodos de permiso y su validación; `ToolsPanel` dibuja la etapa con `StudioUi` |
| `studio.net` | Red de la etapa: estado de herramientas (permisos, selección, fuentes, presets, simetría y el historial de //undo con el nombre de cada operación, de `worldedit.EditHistory`), progreso para el pie del Studio y lista de materiales, solo hacia clientes con KomaEdit; y «donde haces clic» (`StudioAim`), el bloque pulsado con el Studio abierto, que el servidor solo acepta en la dimensión del jugador y a su alcance. `NetworkRegistrationTest` comprueba en las fuentes que cada paquete está registrado una vez y tiene receptor |

## Flujo de una brocha

1. `SculptManager` resuelve el objetivo y captura un `WorldSnapshot` en el servidor.
2. `AsyncEngine` ejecuta `SculptPlanner` en un worker cancelable.
3. `SculptPlanner` genera un `PackedBlockPlan`.
4. El cliente conserva un holograma seccionado de caras expuestas; no usa partículas por bloque.
5. La confirmación transfiere el plan a `BatchEditService`.
6. `BatchEditService` mantiene un `EditSession` y aplica el plan por ventanas (`PlacementRun`) con el presupuesto común de cada tick (ver [Presupuesto por tick, cola y memoria](#presupuesto-por-tick-cola-y-memoria)).
7. Al terminar o cancelar, la sesión se cierra y se registra una vez en el historial WorldEdit.

Los pinceles de `/komaedit pincel` (paquete `brush.ez`, `brush.place`) siguen el mismo camino con `SculptManager.run(jugador, operación, vistaPrevia)`. Cada clic crea una `BrushOperation` inmutable: dice qué zona capturar (`capture()`, o `null` si no lee el mundo) y calcula el plan sobre la foto en el worker (`plan(snapshot)`). Paletas, ruido y portapapeles se resuelven antes, en el hilo del servidor. La lógica de cada pincel (`PaletteShift`, `GradientPainter`, `PetalPainter`, `Alignment`) trabaja sobre `BrushWorld`, una vista mínima de la foto, y se prueba sin Minecraft.

Las herramientas (pinceles y supervarita) se marcan en el `custom_data` del objeto. El cliente intercepta con ellas el clic izquierdo, el derecho y la tecla de cambiar de mano y los manda como `BrushToolPayload`; el servidor apunta con su propio rayo. Para clientes sin el mod quedan los eventos de uso y ataque de Fabric en el servidor.

## Pincel de pintura

Paquete `brush.paint`. La lógica es pura y se prueba con un mundo falso: `PaintFootprint` (círculo, cuadrado o un sello como máscara de intensidad) sale de `FootprintLibrary`, la única puerta a la biblioteca de sellos; `PaintStroke` convierte los puntos del trazo en toques con espaciado, giro y escala de la semilla; `PaintPainter` pinta una tanda de toques sobre un `PaintWorld` con la `Memory` del trazo (el tono de cada bloque al empezar y el alfa más fuerte que recibió).

1. **Clic.** `EzBrush.Paint` recibe el clic derecho (Minecraft lo repite cada 4 ticks mientras se mantiene) y lo pasa a `PaintStrokes.input`. El pincel (`PaintBrush`) ya tiene resueltas la paleta (`PaintPalette` sobre `ResolvedPalette`), la huella, las máscaras y los ruidos.
2. **Trazo.** `PaintStrokes` guarda un trazo por jugador: cada tick mira hacia dónde apunta y lo confirma con la siguiente repetición del clic. Los toques pendientes van en tandas: foto con `WorldSnapshot.begin` repartida entre ticks, cálculo en `AsyncEngine` con un dueño propio (no cancela ni lo cancelan los demás pinceles del jugador) y escritura con presupuesto por tick en una `EditSession` que dura todo el trazo y va al historial al cerrarlo (un solo //undo). Cada escritura comprueba que el bloque sigue como en la foto.
3. **Vista previa.** Con `-p`, `PaintOperation` es una `BrushOperation` de un toque por el camino común (`SculptManager.run`).

## Capacidades de construcción a mano

Paquete `capability` (y `client.capability`). La lógica pura no toca Minecraft y tiene pruebas: `ClickRouting` decide qué capacidad se queda cada clic, `PropertyCycle` recorre las propiedades de «Retocar», `StateTransfer` y `BlockFamilyIndex` calculan el reemplazo, `LayerFinder` busca la capa de «Extruir», `SmearPlanner` la fila, el eje y el final de «Arrastrar», y `CapabilityRules` y `CapabilitySettings` guardan las reglas del servidor y lo que tiene activado cada jugador. `BlockStates` traduce entre los estados de Minecraft y esas clases (propiedades en texto, familias de `BlockFamilies`).

1. El servidor manda `CapabilityStatePayload` al entrar y en cada cambio (y lo recomprueba cada 2 s por si cambian el modo de juego o los permisos). Sin ese estado el cliente no intercepta nada.
2. El cliente intercepta el clic derecho con un mixin en `MinecraftClient.doItemUse`, el izquierdo con `ClientPreAttackCallback` y la `F` al empezar el tick, y manda la intención (`CapabilityActionPayload`) con el bloque, la cara y el punto pulsados, como el paquete de uso de Minecraft.
3. `CapabilityService` comprueba permiso, modo de juego, que la capacidad esté activa, distancia y freno, y convierte la intención en una lista de `HandEdit`.
4. `HandEditApplier` deja que `HandEditEvents.COLLECT` añada bloques (simetría), descarta las posiciones donde el jugador no puede construir y escribe con una `EditSession` (sin los efectos `NEIGHBORS` y `UPDATE` con «Sin actualizaciones»). Si cambia más de un bloque, la sesión va al historial de WorldEdit.

«Alcance largo» es un modificador temporal del atributo `block_interaction_range`, y la velocidad de vuelo, las habilidades del jugador: los dos los sincroniza Minecraft y no necesitan el mod en el cliente.

Los pinceles de colocación (`place`, `scatter`, `array`) no usan `BrushOperation`: cada clic llama a `PlacementService.startBrush` con un `BrushTarget` (bloque mirado, normal de la cara, trazo y dirección de `ViewDiff`) y siguen el camino de `/komaedit place`. Solo `place -p` usa aún la colocación de 90° con holograma, que pide sus estructuras a `StructureSources` y las gira con la base de `brush.place.Alignment`.

Los permisos finos y sus paquetes viven en `permission.PermissionNodes`, lógica pura que recibe qué nodos tiene el jugador; `KomaPermissions` le pasa el actor de WorldEdit. Una orden con varios modos se oculta con `KomaPermissions.canAny(familia)` y cada modo se comprueba al ejecutarse con `requireBuild`.

## Biblioteca de planos

Paquete `blueprint` (y `client.blueprint`, `studio.ui.BlueprintStage`).

1. **Guardar.** `BlueprintService.save` toma el portapapeles en el hilo del servidor y hace el resto en su propio hilo («KomaEdit planos»), como `//schem save`: aplica el giro del portapapeles, escribe el esquema `sponge.3`, monta la rejilla de colores (`ThumbnailColors` sobre `BlockColorCatalogs`) y pinta la miniatura. `BlueprintLibrary` (Java puro) guarda el esquema, el PNG y el JSON con `AtomicStorage`, el JSON el último, y comprueba los `BlueprintLimits` de `blueprints.json`.
2. **Usar.** `load` lee el esquema en ese hilo y lo deja en la sesión de WorldEdit desde el hilo del servidor. La colocación trata los planos como una estructura más (`StructureSpec.Type.BLUEPRINT`): `PlacementService.resolve` pide los esquemas a `BlueprintService.structures` (con caché por archivo y fecha) y sigue el camino de `Clipboard` y `Schematic`, así que `place`, `scatter`, `array`, sus pinceles y la vista previa `-v` funcionan sin más.
3. **Estampar.** `BlueprintStamps` resuelve la lista en cada clic y prepara las `BlueprintTemplate` (bloques con el origen en el centro de la base y su `StampShape`); `StampOperation` es una `BrushOperation`: captura el terreno y, en el worker, `StampPlanner` (puro) elige puntos de Poisson, probabilidad, plano por peso, giro, volteo y altura sobre `BrushWorld`, y escribe con las matrices enteras de `StampPlanner.Stamp` (también para el estado de los bloques).
4. **Studio.** El cliente pide el catálogo (`BlueprintPayloads.Request`) al entrar en la etapa; el servidor responde con `Catalog` y, para las huellas que el cliente no tiene, manda la miniatura en `Thumbnail` de 4 KiB con un presupuesto por tick (`ThumbnailChunks.Queue`). `ClientBlueprintCatalog` la junta (`ThumbnailChunks.Assembler`, que comprueba el SHA-256) y la registra como textura por su huella.

## Representación compacta

`PackedBlockPlan` usa el mismo reparto lógico de bits que una posición Minecraft:

- 26 bits para X.
- 12 bits para Y con signo.
- 26 bits para Z.

Un plan de patrón necesita 8 bytes por posición, sin contar el array, frente a múltiples objetos Java por bloque. Las operaciones que bajan terreno añaden un array opcional de referencias a estados concretos para representar aire sin perder la paleta de las elevaciones.

Por encima de `PackedBlockPlan.Storage.compressAbove()` entradas (131 072 de serie, `planCompressAboveBlocks`) el `Builder` deja los arrays y sigue en páginas (`PlanPages`): 4 096 entradas por página, en el mismo orden, con una paleta por página para el bloque fijo, el estado esperado y la pieza (índices de los bits justos, 0 si toda la página repite valor), las capturas como secuencia o en crudo y las posiciones en tramos que avanzan en X (o en crudo si los tramos no ahorran). Los estados de WorldEdit van en una paleta común del plan, así las páginas son solo números y, si el plan pasa de `spillAbove()` bytes (`planMemoryMegabytes`), las siguientes se escriben en un archivo temporal (`PlanSpill`, borrado al cerrarse con un `Cleaner`; `PackedBlockPlan.sweepSpills` borra los restos al arrancar). La API es la misma; la última página leída se guarda descomprimida, así que leer en orden cuesta casi lo mismo que con arrays. `setPiece` solo vale para entradas de la página abierta (se pone siempre justo al añadir). El plan es inmutable y se puede leer desde varios hilos.

## Invariantes de concurrencia

- `WorldSnapshot.capture`, análisis de selección, parseo de patrones y `EditSession.setBlock` ocurren en el hilo del servidor.
- `SculptPlanner`, `ShapeGenerator` y los algoritmos de ruido solo consumen datos capturados.
- Un nuevo cálculo del mismo jugador cancela el anterior.
- Una edición activa no se sustituye silenciosamente; debe terminar o cancelarse.
- El pool tiene cola finita y rechaza trabajo cuando está saturado.
- Todo lo que se hace en el hilo del servidor sale de un presupuesto común por tick (`ServerWork`, `TickBudget`) que se reparte entre jugadores por turnos (`FairShare`); ningún servicio fija su propio plazo con `System.nanoTime() + X`: pide `ServerWork.deadline(máximo)` o es un `ServerWork.Lane`.
- Lo calculado sobre la foto de un mundo solo se aplica en ese mundo: si el jugador cambia de dimensión antes de que termine, se descarta con `RegionOperationService.OTHER_WORLD` (lo hacen `RegionOperationService`, `SnapshotJobs.run`, `TerrainService`, los tejados y `PlacementService`; una orden nueva que lea con `capture` y aplique después debe comprobarlo igual).
- Al desconectarse un jugador se cancelan su captura, su cálculo en `AsyncEngine`, su colocación y su vista previa (`KomaEditMod` y el `register()` de cada servicio).

## Historial

Una `EditSession` permanece abierta durante las ventanas. Al finalizar se cierra y se entrega a `LocalSession.remember`. Esto conserva una sola operación lógica en `//undo` aunque haya requerido cientos de ticks. Vaciar la sesión entre ventanas (`commit`) no toca el historial: WorldEdit apunta cada cambio al hacer `setBlock`, antes de su búfer.

Si se desconecta el jugador, se detiene el servidor o se cancela el job, los cambios parciales también se registran para poder deshacerlos.

## Proyecto persistente

`ProjectStorage` enlaza cada UUID con `<mundo>/komaedit/players/<uuid>` al entrar. Ningún store utiliza una ruta global una vez enlazado el proyecto.

- `AtomicStorage` escribe primero en `.tmp`, conserva el archivo anterior como `.bak` y recupera residuos al abrir el mundo.
- `RecipeStore` conserva configuraciones JSON versionadas.
- `ProceduralInstanceStore` separa metadata JSON y geometría GZIP; el SHA-256 enlaza ambos archivos y permite recuperar el par anterior si un cierre ocurre entre reemplazos.
- `OperationLedger` registra el progreso con frecuencia limitada y convierte trabajos sin cierre en `INTERRUPTED`.
- `ProjectStorage.backup` crea ZIPs fuera del árbol del jugador para evitar que un backup se incluya a sí mismo.

La bitácora no intenta reanudar automáticamente una edición. Después de un reinicio, el estado del mundo puede haber cambiado; una reanudación ciega violaría la protección de ediciones manuales.

## Operaciones sobre región

Base común de las operaciones sobre la selección de WorldEdit (texturizar, deformar, suavizar, retoques…), en `com.komainu.moxia.region`:

1. `RegionOperationService.submit(jugador, margen, descripción, operación)` comprueba los límites (`EditLimits`; si no cabe, avisa con los trozos de `SelectionParts` y no lee nada) y lee la selección más un margen. La lectura (`RegionReader`, Java puro sobre un `BlockSource`) va repartida entre ticks con el presupuesto común y se guarda en un `RegionSnapshot` inmutable con el id crudo de cada estado, una tabla de estados (`StateInfo`: estado de WorldEdit, id, aire, líquido, sólido y propiedades) y qué posiciones están dentro de la selección.
2. La operación (`RegionOperation`) se ejecuta en un worker de `AsyncEngine`. Solo lee la foto y escribe en `RegionEdits`. Debe llamar a `RegionEdits.checkCancelled()` en bucles largos.
3. `RegionEdits` produce un `PackedBlockPlan` de escrituras condicionales: cada cambio lleva el id del estado leído y `BatchEditService` lo descarta si el bloque cambió mientras tanto. El plan se cierra (`seal`, y se comprime si es grande) en el worker y entra en el historial de WorldEdit. Los cambios llegan hasta el límite del jugador (`OperationPolicy.limit`), o a la foto entera sin límite.

Si la operación tiene que escribir fuera de lo leído (la dispersión de estructuras, que asoma de la región), `RegionOperationService.capture(jugador, región, margen, descripción, alTerminar)` solo lee y entrega la foto en el hilo del servidor; la operación monta su propio `PackedBlockPlan`, condicional dentro de la foto. Así funcionan `place`, `array` y `scatter` (paquete `placement`).

Si el resultado no son cambios de bloque sueltos (un plan con patrones y piezas, un informe, celdas de bioma), `SnapshotJobs.run(jugador, región, margen, descripción, cálculo, aplicar)` lee la región, ejecuta el cálculo en un worker y entrega el resultado en el hilo del servidor (si el jugador sigue en el mismo mundo); `SnapshotJobs.read` hace lo mismo para lo que solo informa y `SnapshotJobs.compute`, sin leer el mundo. Así funcionan `text` y `biome` (`run`) y `analyze` (`read`).

Para una operación nueva: escribe la lógica sobre `RegionSnapshot` (se puede montar a mano en tests con `RegionSnapshot.builder`), implementa `RegionOperation` y lánzala desde un comando con `RegionOperationService.submit`. `TweakOperation` es el ejemplo más pequeño. `weathering.WeatheringService` (Envejecer) adapta la foto a texto para `WeatheringEngine`, que es Java puro y se prueba con un mundo falso de texto.

Piezas relacionadas:

- `noise.NoiseSpec`: ruido con la notación de ezEdits sobre FastNoiseLite. Es inmutable y se puede usar desde varios hilos tras `build(semilla)`.
- `gradient.PaletteSpec`: paletas `##Nombre`, `-`, `(a:b)`, `*n` y `[…]`. Se resuelven a texto en el hilo del servidor, y los patrones de WorldEdit se parsean ahí antes de pasar al worker.
- `region.RegionMasks` y `region.SnapshotExtent`: las máscaras de WorldEdit se parsean en el hilo del servidor con un `Extent` de solo lectura sin foto; la operación llama a `bind(snapshot)` al empezar y desde ahí la máscara lee la foto en el worker. Fuera de la foto todo es aire y los biomas dan error.
- Luz: si `RegionOperation.needsLight()` devuelve `true`, la captura guarda también la luz de bloque y del cielo de cada posición (`RegionSnapshot.blockLight`, `skyLight`); `StateInfo.luminance` es la luz que emite cada estado.
- `texture.TextureContext` y `texture.TextureField`: los modos de `/komaedit texture` son campos puros (un valor de 0 a 1 o `NaN` por bloque) sobre la foto, combinables; `TextureOperation` los convierte en bloques de la paleta. Sirven de ejemplo para medir sobre la foto (sumas acumuladas de sólidos, normales, suavizado).
- `gradient.SavedPalettes` y `noise.SavedNoises`: paletas (`#nombre`) y ruidos guardados por jugador y mundo sobre `persistence.NamedDefinitions`. Para aceptarlos, pasa `SavedPalettes.resolver(uuid)` a `PaletteSpec.parse` o `SavedNoises.resolver(uuid)` a `NoiseSpec.parse`.

## Vista previa, progreso y cancelación

Piezas en `com.komainu.moxia.preview` y `com.komainu.moxia.progress`. Una operación nueva que encole con `BatchEditService.submit`/`submitStyled` (directamente o a través de `RegionOperationService`) tiene vista previa, barra de progreso y `/komaedit cancel` sin escribir nada más.

1. **La petición de la orden.** `PreviewFlags.install` (al final de `MoxiaCommands.register`) envuelve cada nodo ejecutable de `/komaedit`: le cuelga los literales `-v`/`--preview` o, en los de `greedyString`, quita la bandera del texto y vuelve a ejecutar la orden sin ella. La orden corre con una `EditRequest` (`DEFAULT`, `PREVIEW`, `CONFIRMED`, `TOOL`) en `EditRequests.current()`. `command.PreviewScope` dice qué familias y subórdenes no la llevan (gestión y consultas, ayuda, pinceles, y lo que no pasa por el plan, como `gradient` y `biome`) y dónde `-v` ya significa otra cosa (`deform noise`, `weather`, `text`…).
2. **El viaje.** `RegionOperationService` y `SnapshotCaptureService` recogen la petición al empezar y la restauran (`EditRequests.within`) al terminar la captura; `AsyncEngine.submit` la recoge al lanzar y la deja en el relevo del jugador (`EditRequests.handOff`) justo antes de entregar el resultado. Si una cadena la pierde, la última orden con `-v` queda armada unos minutos y se usa una vez. Un clic de herramienta envuelve su envío en `EditRequest.TOOL`; una confirmación, en `CONFIRMED`.
3. **La puerta.** `BatchEditService.submit`/`submitStyled` (sin `JobListener`) llaman a `PlanPreviews.intercept`. Si la petición es `PREVIEW`, o `DEFAULT` y el plan llega al umbral de `PreviewPreferences`, el plan no se encola: se prepara repartido entre ticks (bloque final de cada entrada con `patternFor`/`shapeFor`, los mismos que al aplicar, y estado actual del mundo) en un plan congelado de escrituras condicionales, apuntando de cada entrada el id crudo del estado final y si antes había aire. En un hilo aparte, si el cliente tiene KomaEdit (`PreviewPlanSender.supported`), `PreviewSurface` elige la superficie (con las caras al descubierto calculadas con todos los cambios) y `PreviewPlanCodec` la trocea; `PreviewPlanSender` manda las partes unas pocas por tick y el cliente (`client.world.PlanPreviewRenderer`) las junta, arma la malla en su propio hilo con los modelos de bloque y el contorno de `PreviewOutline`, y la dibuja. Si no, `DisplaySampler` elige los displays y `PreviewDisplays` los manda por paquetes solo a ese jugador. `PlanPreviews.holds(jugador, plan)` dice al llamador si su plan quedó en vista previa, para no anunciar «en cola». Toda orden que llega a `intercept` desde lo escrito queda en `LastEdits` para `/komaedit repeat`.
4. **Confirmar.** `PreviewFlow` (lógica pura, con pruebas) guarda la vista previa, la caduca y, al confirmar, comprueba con `ZoneCheck` las posiciones vigiladas antes de encolar el mismo plan congelado. Si algo cambió, recalcula: con `PlanPreviews.Extras.recompute` si la operación lo dio (`RegionOperationService` relanza la misma selección y vigila también toda ella), si no repitiendo la orden, y si no hay orden, volviendo a preparar el plan. Cada salida pasa una vez por `hide`, que retira los displays.
5. **Progreso.** `progress.ProgressBars` pregunta cada cinco ticks a `BatchEditService.progress`, `BiomeEditService.progress`, `PlanPreviews.progress`, `RegionOperationService.progress` y `AsyncEngine.status`, y pinta una bossbar por jugador con `ProgressLine`. Un cálculo puede informar de su avance con `AsyncEngine.progress(hecho, total)` desde el worker; si no, la barra cuenta el tiempo. En la cola, `ProgressLine.queued` lleva el puesto, cuántas esperan y lo que falta para empezar (fase `QUEUED`); con un admin parándola, fase `PAUSED`. Las fases nuevas van al final del enum, porque el Studio recibe su ordinal (`JobStatusPayload`).
6. **Cancelar.** `/komaedit cancel` (`command.PreviewCommands.stopAll`, que también usa `/komaedit jobs cancel`) corta la captura, el worker, la espera en la cola, la colocación y el pintado de biomas. `edit.PlanApplication` recorre los planes por tramos y se para siempre en una frontera de entrada, así que la `EditSession` que se cierra y se registra en `LocalSession` cubre exactamente lo colocado; al cerrarla, WorldEdit vacía lo que quede de la última ventana.

Para un comando nuevo: si es de gestión (no coloca bloques) o ya usa `-v` con otro sentido, añádelo a `PreviewScope`; si su plan no pasa por `BatchEditService.submit`, no tendrá vista previa. Si después de encolar anuncia «en cola», compruébalo antes con `PlanPreviews.holds` y mándalo a la barra de acción con `QuietMode.status` (o `EzCommandSupport.queued`, o `Feedback.status`), para que `/komaedit quiet` lo silencie.

## Presupuesto por tick, cola y memoria

Piezas en `com.komainu.moxia.performance` y `com.komainu.moxia.edit`. Todo lo puro tiene pruebas con un reloj de mentira; `HugeEditBenchmarkTest` comprueba en cada build un plan de un millón (comprimido y exacto) y el modelo de colocación con reloj de mentira; con `KOMAEDIT_FULL_TESTS=1` (o `./gradlew benchmark`, que además le da la memoria de los 20 millones) mide las fases con 1, 5 y 20 millones de bloques.

1. **Presupuesto.** `TickBudget` (puro) mide en `START_SERVER_TICK` y al final de `END_SERVER_TICK` (fases propias, la última después de las de los demás mods) cuánto dura cada tick, cada cuánto llega y cuánto gastó KomaEdit, y da el presupuesto del tick: `tickBudgetMillis`, menos si el resto del servidor deja menos de eso hasta 45 ms, y `tickBudgetMinMillis` si los ticks llegan tarde (por debajo de ~19,4 TPS).
2. **El tick de KomaEdit.** `ServerWork.tick` (un solo `END_SERVER_TICK`) abre la ventana del presupuesto y corre primero lo ligero (`ServerWork.light`: fantasma, pintura, pintura rápida, foto de los pinceles, vista previa), que pide su plazo con `ServerWork.deadline(máximo)`; si hay trabajo pesado, lo ligero usa como mucho la mitad. Luego lo pesado: cada servicio es un `ServerWork.Lane` (`BatchEditService`, `RegionOperationService`, `BiomeEditService`) que dice qué jugadores tienen trabajo y avanza el de uno hasta un plazo. `FairShare` reparte lo que queda por turnos entre jugadores (cada uno, lo que queda entre los que faltan; lo que no gasta pasa a los siguientes; si sobra, otra vuelta; el primero rota cada tick) y, dentro de un jugador, los servicios rotan también. `ServerWork.pause` (admin) hace que los carriles se salten a ese jugador.
3. **Cola.** `BatchEditService` mete cada edición en una `JobQueue`: como mucho `maxConcurrentJobs` colocan; las demás esperan por orden de llegada, una pausada no ocupa sitio ni entra. `QueueEstimate` simula el reparto a partes iguales con el ritmo medido (`RateMeter`) para decir cuándo empezará cada una.
4. **Ventanas.** WorldEdit 7.3 guarda todo `setBlock` en el `BatchingExtent` de la sesión hasta `commit`/`close`, y en su modo rápido (`ReorderMode.FAST`, el de serie) aplica luz, vecinos y envío (`SideEffectExtent`) en el `commit`. `PlacementRun` (puro) alterna: escribe una ventana (`Source`: `PlanApplication` para los planes, el iterador de la región o del portapapeles), la vacía (`Session.flush`, que en el juego es `EditSession.commit()` reanudado con un `RunContext` que se para en el plazo: el búfer pasa al mundo de una vez y los efectos, bloque a bloque y agrupados por sección, se cortan y siguen en el tick siguiente) y no escribe la siguiente hasta acabar. `Pacer` mide lo que cuesta escribir y vaciar cada entrada y dimensiona la ventana para que quepa en lo que queda del tick. Al acabar cada ventana, `BatchEditService` lee las posiciones capturadas de las instancias (antes las leía con lo escrito aún en el búfer).
5. **Límites.** `EditLimits` (puro) dice si un tamaño pasa de un límite (`SERVER`, `CAPTURE`, `WORLDEDIT`), en cuántos trozos partirlo y parte una caja por su lado horizontal más largo en bordes de chunk. `RegionOperationService` lo comprueba antes de leer y deja los trozos en `SelectionParts` para `/komaedit trozo`; `OperationPolicy.validate` y `BatchEditService` (con el `//limit` de la sesión), antes de encolar. `EzCommandSupport.fail` añade los botones de los trozos al aviso.
6. **Admin.** `command.JobCommands`: `/komaedit jobs` lista lo de `ProgressBars.current` de cada jugador con el estado de `TickBudget`; `pause`/`resume` usan `ServerWork.pause` y `cancel`, `PreviewCommands.stopAll`.

Para un servicio nuevo que trabaje en el hilo del servidor: si es interactivo y pequeño, regístralo con `ServerWork.light` y pide el plazo con `ServerWork.deadline`; si puede ser grande, hazlo `ServerWork.Lane` (un trabajo por jugador, `step` hasta el plazo, respetando `ServerWork.paused`).

## Fantasma con gizmo

Paquete `gizmo` (y `client.gizmo`). Un objeto que no depende del sitio se calcula una vez y se coloca con una transformación que el jugador elige moviéndolo; el servidor nunca recalcula.

1. **La puerta.** `PlanPreviews.intercept`, después de resolver la `EditRequest`, pregunta a `GhostService.offer`. Si la orden es de las de `GhostScope` (por su texto: `paste`, `place`, `blueprint place`, las figuras y estructuras de la trayectoria, `tree`, `rock`, `roof`, `text`, `image`, `loft`, `bend`…), el jugador tiene el modo que toca (`GizmoSettings.Mode`, que el cliente manda en `ghost_mode` al entrar; sin el mod no hay modo y todo sigue igual) y la petición es `DEFAULT` o `PREVIEW`, el plan se queda en el fantasma y no se encola. `PlanPreviews.wouldPreview` y `holds` lo tienen en cuenta, así `PendingBuilds` no pregunta antes y quien llama no anuncia «en cola». Construir del Studio (`RecipeBuildService`, que no pasa por `intercept`) llama a `GhostService.offer` con su propio `Applier`, que al colocar mueve también las posiciones de la instancia y su trayectoria (`GhostModel.carry`) y encola con su captura de dueño.
2. **Preparar** (repartido entre ticks, unos 4 ms): el bloque final de cada entrada con `PlanBlocks` (`BatchEditService.blocks`, el mismo cálculo que al aplicar; los bloques con NBT se guardan aparte) y un `GhostModel`: posiciones relativas al pivote (el bloque del centro de la caja), el id crudo de cada estado, qué es aire y el índice de captura. En el hilo `KomaEdit-Ghost` se elige la superficie (`PreviewSurface`) y se trocea con `PreviewPlanCodec` en posiciones locales.
3. **Enseñar.** `ghost_open` (caja local, pivote, opciones y la paleta de estados para saber qué giros admite) y `ghost_part` con la superficie, dos partes por tick. `GhostClient` junta las partes, y `GhostRenderer` arma la malla con el código de la vista previa (`PlanPreviewRenderer.mesh`) en posiciones locales y la dibuja con un ancla (`PlanPreviewRenderer.Anchor`): moverla es cambiar el ancla; girarla rehace la malla en otro hilo con `GhostPlacement.orientPositions` / `orientStates` y las caras con `Orientation.faces`, y mientras tanto dibuja la anterior con el giro rígido que falta.
4. **Mover.** `GhostSession` es la máquina de estados (mover, arrastrar, colocando, cerrado; copias de una en una) y recibe acciones ya traducidas: pasos de bloque, rayos (`GizmoMath`: rayo contra eje, contra plano y ángulo de anillo; `GizmoLayout.pick` decide la pieza) y giros que solo acepta si `GhostStates.admitsAll` los admite. El rayo es el del punto de mira en el mundo (el clic izquierdo con `ClientPreAttackCallback`) o el del cursor en el Studio (`ProceduralStudioScreen`). Las teclas entran por `KeyboardMixin` antes que Minecraft (`GizmoKeys` las traduce: sus entradas de Controles vienen sin asignar para no quitar teclas a nadie y valen los atajos de serie), la rueda por `MouseMixin` y el clic derecho por `MinecraftClientMixin.doItemUse`. «Apoyar en el suelo» es del cliente (`GroundSnap` sobre su mundo): manda la posición ya apoyada.
5. **Confirmar.** `ghost_confirm` lleva solo la orientación (una de las 48 de `Orientation`), la posición del pivote, las opciones y si es copia. `GhostService.confirm` comprueba permisos, edición en curso, que el objeto admita la orientación y `GhostValidation` (altura, borde, 512 bloques, chunks cargados). Después `GhostPlacement.Planner` recorre el modelo entre ticks: lleva cada posición al mundo, para en la primera donde el jugador no puede construir (`canPlayerModifyAt`), lee lo que hay (conservar lo que hay, aire o no) y apunta cada escritura con la condición de ese estado. El plan va a `BatchEditService` ya confirmado (`EditRequest.CONFIRMED`): un solo //undo, su barra y `/komaedit cancel`. `ghost_result` dice al cliente si se colocó, si fue una copia (el fantasma sigue) o por qué no.

Lo que dibuja el cliente y lo que coloca el servidor salen de las mismas funciones (`GhostPlacement`, `GhostStates`), y las pruebas lo comparan para las 48 orientaciones. Los estados giran con `OrientedStates`: de pie, `StateTransform` de la simetría con la orientación pasada a `SymmetryTransform` (`Orientation.symmetry`); tumbados, direcciones de seis valores, ejes y conexiones de seis lados con la matriz, y lo que no tiene versión tumbada no se admite. `GizmoGeometry` es lo que se dibuja del gizmo (el cliente con `GizmoRenderer`, sin profundidad; las pruebas, en `GhostRenderTest` con un rasterizador en perspectiva).

## Mensajes y lo que se ve en el mundo

`feedback.Feedback` es el estilo común: `ok`, `reply`, `info`, `warn` y `error` al chat con el prefijo `KE ·` y el color de su nivel, `status` a la barra de acción, `format("%s completada: %s bloques", …)` para los números agrupados y en blanco, `fix(etiqueta, orden)` para un botón que escribe una orden en el chat y `fail(source, …)` para los errores de las órdenes. `Feedback.route` decide el canal según el nivel, si es efímero y `QuietMode` (lógica pura, con pruebas); los errores mandan además `FeedbackEventPayload` para el sonido del cliente. Los `fail` de las órdenes pasan por `EzCommandSupport.fail`, que añade la propuesta de `CommandHints` y, si la orden escrita está en `EditRequests.current()`, el botón **[corregir]** con la palabra cambiada (`CommandHints.corrected`).

`feedback.WorldFeedback` registra los paquetes que solo usan los clientes con el mod: `preview_plan`, `selection`, `symmetry_guide` y `feedback_event`. `WorldOverlaySync` mira cada medio segundo la selección de WorldEdit (solo la del mundo del jugador) y la simetría y manda lo que cambie; `SymmetryService` deja de lanzar partículas a quien las dibuja. En el cliente, `client.world.WorldFeedbackClient` recibe todo, registra las teclas (aplicar, descartar, repetir, cuentagotas, ajustes) y los sonidos; `PlanPreviewRenderer` dibuja la vista previa (`WorldRenderEvents.LAST`: profundidad primero y color después, con polygon offset) y `WorldOverlayRenderer` y `WorldLabels`, la selección, las medidas, el radio del pincel y la simetría. `ClientWorldSettings` guarda `WorldFeedbackSettings` en `config/komaedit-client.json` y `WorldSettingsScreen` lo enseña. Todo lo que se dibuja va dentro de un `try` que lo oculta si falla, en lugar de cerrar el juego.

## Pruebas sin el juego

Todo se prueba con JUnit sin arrancar Minecraft. Dos pruebas de humo vigilan lo que se rompe al fusionar ramas hechas en paralelo:

- `command.CommandTreeSmokeTest` monta el árbol entero con `MoxiaCommands.register(dispatcher)` en un `CommandDispatcher` de Brigadier (no hace falta el servidor: los permisos se miran al ejecutar). Brigadier fusiona en silencio dos hijos con el mismo nombre, así que la prueba apunta cada `addChild` que repite nombre bajo `/komaedit`. Comprueba también los alias `/ke`, `/mx`, `/moxia` y `ruta`, que cada orden tenga su tema en `HelpTopics` (por id, alias o entrada), que la ayuda solo enseñe órdenes que existen y dónde va la bandera `-v`.
- `config.KomaEditConfigTest` lee `config.json` de serie, uno de la 1.4.0 sin los campos nuevos y uno roto con `KomaEditConfig.load(Path)`.
- `studio.net.NetworkRegistrationTest` lee las fuentes: cada paquete (`implements CustomPayload`) registrado una sola vez y en un sentido, con su receptor al otro lado. Un paquete del cliente sin registrar no da error: `canSend` dice que no y el cliente se calla.

Una orden nueva necesita, por tanto, su entrada en `HelpTopics` y, si no coloca bloques, su sitio en `PreviewScope`.

Las pruebas no dependen del PC: ni de la hora, ni del idioma del sistema, ni de los recursos que Loom descarga (los nombres de bloque en español solo se leen al pintar las maquetas), ni del orden en que corren (Gradle las reparte entre varias JVM), ni de lo rápida o cargada que esté la máquina. Lo que reparte trabajo entre ticks con un presupuesto en nanosegundos recibe el reloj (`SnapshotCaptureService.tick(online, clock)`, `WorldSnapshot.Capture.step(…, clock)`, `EditRequests.clock`) y sus pruebas le pasan uno falso que avanza lo mismo por lectura. Lo que tarda y no aporta en cada build va detrás de una variable de entorno:

- **Renders y maquetas**: `KOMAEDIT_<TEMA>_RENDER=<carpeta>` (o `KOMAEDIT_RENDER_DIR`, `KOMAEDIT_STUDIO_MOCKUPS`) escribe los PNG; `KOMAEDIT_MC_JAR=<jar del cliente>` les pone las texturas del juego (`preview.TextureTiles`, común a los rasterizadores).
- **Benchmarks**: `KOMAEDIT_BRUSH_BENCHMARK=<archivo>` y `KOMAEDIT_PAINT_BENCHMARK=<archivo>` miden y escriben la tabla.
- **Pasada entera** (`KOMAEDIT_FULL_TESTS=1`, `FullTests`): las que recorren todas las combinaciones (formas de `ezspline`, especies y semillas de árboles, los 80 PNG de sellos frente a sus recetas) pasan en cada build por una muestra fija y aquí por todo; y los límites de tiempo de reloj («figuras grandes en menos de 8 s», `FullTests.assertFasterThan`) solo se comprueban aquí: en una máquina cargada fallan sin que nada esté mal. Pásala al tocar esos generadores o su rendimiento.

## Pinceles de modelado

`sculpt.model` es Java puro salvo `ModelOperation`. `ModelOperation` es una `BrushOperation`: pide a `SculptManager.run` la bola del radio más el margen que necesita cada modo (`ModelSettings.reach`), traduce la foto a una `ModelGrid` (un número por estado con masa, `0` para aire, líquidos y reemplazables, y la máscara evaluada por celda) y llama a `ModelSculptor.apply`. El resultado son los materiales de antes y de después; el plan escribe solo lo que cambia (aire, el estado del material o el patrón de `/komaedit sculpt`).

- `ModelFields`: desenfoque gaussiano separable con la ventana recortada en el borde de la captura, lectura trilineal, distancia con signo exacta (Felzenszwalb y Huttenlocher) y ruido de valor 3D determinista.
- `SurfaceNormals`: normal por vecinos (el aire tira, la masa empuja) y su media pesada bajo el pincel.
- `Voronoi`: distancia a la arista más cercana en la norma que da planos digitales sin huecos, en 3D o en columnas.
- `MaterialChooser`: qué material lleva cada celda nueva; recibe el de alrededor (el más votado entre los 26 vecinos de antes o, si no hay, el más cercano por anchura) y si queda tapada, para enterrar la hierba.

Los tipos de `SculptBrushType` de la categoría `MODEL` van por el mismo camino (`ModelOperation.fromSculpt`), y `BrushOperation.pattern()` deja que el plan use el patrón de la brocha. Las pruebas montan rejillas a mano (`ModelGrids`) y los renders usan `PieceRender`.

## Portapapeles: escalar y girar

`clipboard.PixelArtScaler` amplía una `VoxelVolume` bajo demanda: cada nivel (Scale2x o Scale3x) pide a su padre el bloque y sus vecinos y decide cada trocito con la regla 2D en los planos que lo contienen (los tres al escalar, solo el horizontal al girar). El primer nivel se guarda en un array si cabe. `ClipboardTransform` lee cada bloque de la salida en su posición transformada al revés sobre la ampliación (1×, 2×, 3× u 8× para RotSprite), votando entre los dos trocitos cuando cae justo en un centro para no favorecer un lado. `ClipboardStates` reutiliza `symmetry.StateTransform` para girar estados. `ClipboardScaleService` lee el portapapeles (con su `//rotate` pendiente) en el hilo del servidor, calcula y monta el `BlockArrayClipboard` nuevo en un worker y lo cambia en el hilo del servidor.

## Terraformado

`terrain` trabaja sobre `TerrainView`, una vista mínima de la zona capturada: `TerrainColumns` lee el suelo de cada columna, las herramientas (`StampBrush`, `RampPlanner`, `RiverPlanner`, `HydraulicErosion`, `ThermalErosion`) cambian un `HeightField` de alturas con decimales y `TerrainWriter` lo vuelve a pasar a bloques con `SurfacePalette`. Todo eso es Java puro y se prueba con un mundo de texto. En el juego, `TerrainService` lee con `RegionOperationService.capture`, calcula en `AsyncEngine` sobre `RegionTerrainView`, escribe en `RegionEdits` y pasa por `BuildEstimate` y `PendingBuilds`; el pincel de relieve es una `BrushOperation` (`HeightmapOperation`) sobre `BrushTerrainView`.

### Biblioteca de sellos

`terrain.stamp` genera y coloca los sellos de serie; `terrain.StampLibrary` es la fachada.

- **Recetas.** `StampRecipe` (nombre, `StampCategory`, descripción, gris neutro, inicio del borde, forma y `Bake`) genera un sello con una semilla: la forma (`ReliefShapes`, `CarvedShapes` y `SurfaceShapes`, sobre `StampNoise` y `StampMath`), la erosión horneada (`HydraulicErosion` y `ThermalErosion` sobre un `HeightField` a escala de bloques, con una rampa hacia fuera para que los derrubios salgan del círculo y, en los que hunden, el terreno de alrededor protegido), el borde y la normalización. Las formas solo usan `StrictMath` y ruido entero; la erosión es caótica y usa `Math`, que puede variar en el último bit entre Java distintas: por eso los sellos van ya hechos en el jar. `StampRecipes` las junta por categoría.
- **PNG del jar.** `StampLibrary.get` lee `assets/komaedit/stamps/<nombre>.png` (16 bits con 12 de precisión) la primera vez y lo guarda en memoria; si falta, lo genera. `StampResourcesDump` (prueba con `KOMAEDIT_STAMPS_DIR`) los vuelve a escribir y `StampLibraryTest` comprueba que coinciden con las recetas. `StampCatalog` mira antes `config/komaedit/heightmaps` y no deja que una plantilla de la 1.8 sin tocar tape a un sello nuevo (huella de 8×8).
- **Colocación.** `StampPlacement` (radio, giro, escala, espejo, semilla y fuerza del ruido fino) da el relieve de un punto (`relief`) y la huella (`footprint`); `StampVariation.place(radio, giro, alAzar, semilla, clic)` la saca de la semilla y el número de clic. `StampBrush.Settings` lleva una colocación y `StampBrush.apply` mezcla (sumar, restar, máximo, aplanar, suavizar) sobre el `HeightField`. `HeightmapBrush` lee la orden y el pincel (`EzBrush.Relief`) cuenta los clics y cambia la semilla con Mayús+F.

**API para otras herramientas** (el pincel de pintura con sellos, por ejemplo):

```java
List<StampInfo> sellos = StampLibrary.all();               // nombre, categoría, descripción, neutro, altura natural, paleta
List<StampInfo> dunas = StampLibrary.of(StampCategory.DUNES);
int[] argb = StampLibrary.thumbnail("volcan", 64);           // miniatura en color con sombreado, por filas
HeightStamp sello = StampLibrary.get("volcan");              // o TerrainService.catalog().load(nombre) con los PNG propios
StampPlacement donde = StampVariation.DEFAULT.place(radio, Math.toRadians(yaw + 180), false, semilla, clic);
double r = donde.relief(sello, dx, dz);                      // -1..1 respecto al neutro; 0 fuera del sello
double w = donde.footprint(dx, dz);                          // 1 dentro, 0 en el círculo del sello
int alcance = donde.reach();                                 // bloques desde el centro que puede tocar
```

`relief` es puro y no guarda estado: se puede llamar desde un worker. Con `StampVariation.NONE` sale el sello tal cual; con la misma semilla y el mismo clic, la misma colocación. `StampThumbnails.argb(sello, categoría, alturaNatural, lado)` pinta también un PNG propio. `StampLibrary.render(nombre, lado, semilla)` genera una variante entera con otra semilla (erosión incluida: centenares de milisegundos, solo para herramientas que lo necesiten).

## Máscaras y patrones en WorldEdit

`worldedit.EzExtensions` registra `EzMaskParser` y `EzPatternParser` (paquete `worldedit.ez`) en las factorías de WorldEdit al arrancar el servidor, así que valen en cualquier orden de WorldEdit. Cada parser devuelve `null` si la entrada no es suya y deja seguir a los demás.

- `EzArguments` lee `#nombre[a][b]…` con corchetes anidados; `NAMES` de cada parser asocia alias y nombre canónico.
- Lo que depende del jugador (mirada, posición, portapapeles, máscara global) se fija al parsear, en el hilo de la orden.
- Las máscaras leen bloques del `Extent` que da WorldEdit y la luz del `LightingProvider`, que no carga chunks. Guardan la respuesta por estado de bloque.
- La lógica pura (`GradientMath`, `NearSearch`, `VoxelRay`, `EzDirections`, `SelectionShape`) no toca Minecraft y tiene pruebas.

## Herramientas del Studio

La etapa Herramientas no ejecuta nada en el cliente: cada `ToolCard` convierte sus valores (`ToolValues`) en la misma orden que escribiría el jugador y el Studio la manda con `sendChatCommand`. El servidor la analiza con su analizador de siempre y comprueba los permisos. Para enseñar las tarjetas apagadas, el cliente pide cada segundo `tool_state_request` y el servidor contesta `tool_state` con los nodos de `ToolCatalog.permissionNodes()` que el jugador tiene (`KomaPermissions.can`), su selección, fuentes propias, presets guardados y simetría. `ProgressBars` manda además `job_status` cada 5 ticks a quien tiene el mod, y `AnalyzeService` manda `material_list` al terminar. Las pruebas leen cada orden con el analizador del servidor (`TreeSettings`, `RoofSettings`, `TextRequest`…), así que si cambia una orden y la tarjeta no, falla una prueba.

Para añadir una herramienta: una clase que extienda `ToolCard` (ajustes, `command`, `permissions` y, si hace falta, `validate`, `actions` y `presets`), una línea en `ToolCatalog.ALL` y sus textos en los idiomas (`ToolTranslationsTest` dice cuáles faltan).

## Extensiones previstas

Los nuevos tipos de brocha se incorporan en tres puntos:

1. Descriptor en `SculptBrushType`.
2. Planificador puro en `SculptPlanner` o un futuro `BrushOperation` registrable.
3. Control opcional en GUI/comandos.

Los pinceles nuevos ya usan la interfaz `BrushOperation`; las brochas de `SculptPlanner` pueden pasarse a ella sin tocar `SculptManager` ni el pipeline de preview/aplicación.

Para schematics y clipboard se prevé otro tipo de plan que transporte índices de paleta/NBT, sin forzar esos datos dentro del plan simple de brochas.
