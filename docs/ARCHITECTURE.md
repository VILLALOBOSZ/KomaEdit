# Arquitectura de KomaEdit

## Principios

KomaEdit separa el dominio de edición del acceso a Minecraft. La regla central es: **ninguna lectura ni escritura de mundo se ejecuta en un worker**.

Los workers reciben datos inmutables (`WorldSnapshot`, parámetros, trayectorias) y producen planes compactos. WorldEdit consume esos planes exclusivamente desde el hilo del servidor.

## Módulos actuales

| Paquete | Responsabilidad |
| --- | --- |
| `shape` | Muestreo Catmull–Rom y voxelización de figuras 3D |
| `path` | Trayectorias por jugador |
| `brush` | Estampado orientado de figuras existentes |
| `sculpt` | Brochas de volumen/altura, máscaras, perfiles y previews |
| `noise` | Perlin, fBM, ridged y Worley deterministas |
| `placement` | Colocación de estructuras (place, array, scatter): alineación, formas procedurales, dispersión y voxelizado |
| `edit` | Plan compacto y aplicación WorldEdit por lotes |
| `performance` | Pool acotado, cancelación, cuotas y aislamiento de cálculos |
| `preview` | Hologramas de trayectoria y vista previa universal de los planes (bandera `-v`, displays, confirmación) |
| `progress` | Barra de progreso de las operaciones largas |
| `gradient` | Paletas y selección streaming por eje |
| `color` | Colores de bloque en OKLab (`BlockColorCatalog`), parecidos, degradados (`ColorGradient`) y sombreado (`ShadeOperation`, con `texture.ShadeField`) |
| `generator` | Generadores de ezEdits (formas, noisegen, flowfield, flowline): lógica pura sobre `Volume` y `GeneratedBlocks` |
| `image` | Imagen en bloques (`/komaedit image`): lectura y descarga con límites, reescalado, cuantización en CIELAB y tramado, probados sin Minecraft; `ImageService` lo une al mundo y a la cola de edición |
| `text` | Texto en bloques (`/komaedit text`): fuentes TrueType (de serie con OFL y propias), rasterizado en muestras, borde por distancia exacta y colocación según la mirada; `TextModel` es un `DeformField` para las piezas de `-w` |
| `analysis` | Lista de materiales (`/komaedit analyze`): recuento sobre la foto y coste en survival (`SurvivalCost`, lógica pura con un registro inyectado), pilas, cofres y CSV |
| `utility` | Gravedad, nieve, drenar e inundar: motores puros sobre `RegionSnapshot` con `BlockKinds` (las reglas de Minecraft en `MinecraftKinds`) y sus `RegionOperation` en `UtilityOperations` |
| `biome` | Pintar biomas: celdas de 4×4×4 (`BiomeCells`, pura) y una cola por ticks con `EditSession.setBiome` para el //undo y el reenvío de chunks de WorldEdit |
| `persistence` | Proyecto por mundo, migraciones, journal, backups y escritura atómica |
| `permission` | Permisos granulares y excepciones de cuota |
| `symmetry` | Modo simetría: geometría pura (centros, espejos, radial, estados), copias a mano y extensión de WorldEdit |
| `config` | Límites del servidor y política de compatibilidad |
| `worldedit` | Adaptación de jugador, sesión, selección y patrones |
| `command` | API Brigadier y validación de entrada |
| `capability` | Capacidades de construcción a mano: reglas puras, red, servidor y aplicación con WorldEdit |
| `client.gui` | Pantalla no bloqueante y controles del editor |

## Flujo de una brocha

1. `SculptManager` resuelve el objetivo y captura un `WorldSnapshot` en el servidor.
2. `AsyncEngine` ejecuta `SculptPlanner` en un worker cancelable.
3. `SculptPlanner` genera un `PackedBlockPlan`.
4. El cliente conserva un holograma seccionado de caras expuestas; no usa partículas por bloque.
5. La confirmación transfiere el plan a `BatchEditService`.
6. `BatchEditService` mantiene un `EditSession` y aplica slices con presupuesto temporal.
7. Al terminar o cancelar, la sesión se cierra y se registra una vez en el historial WorldEdit.

Los pinceles de `/komaedit pincel` (paquete `brush.ez`, `brush.place`) siguen el mismo camino con `SculptManager.run(jugador, operación, vistaPrevia)`. Cada clic crea una `BrushOperation` inmutable: dice qué zona capturar (`capture()`, o `null` si no lee el mundo) y calcula el plan sobre la foto en el worker (`plan(snapshot)`). Paletas, ruido y portapapeles se resuelven antes, en el hilo del servidor. La lógica de cada pincel (`PaletteShift`, `GradientPainter`, `PetalPainter`, `Alignment`) trabaja sobre `BrushWorld`, una vista mínima de la foto, y se prueba sin Minecraft.

Las herramientas (pinceles y supervarita) se marcan en el `custom_data` del objeto. El cliente intercepta con ellas el clic izquierdo, el derecho y la tecla de cambiar de mano y los manda como `BrushToolPayload`; el servidor apunta con su propio rayo. Para clientes sin el mod quedan los eventos de uso y ataque de Fabric en el servidor.

## Capacidades de construcción a mano

Paquete `capability` (y `client.capability`). La lógica pura no toca Minecraft y tiene pruebas: `ClickRouting` decide qué capacidad se queda cada clic, `PropertyCycle` recorre las propiedades de «Retocar», `StateTransfer` y `BlockFamilyIndex` calculan el reemplazo, `LayerFinder` busca la capa de «Extruir», `SmearPlanner` la fila, el eje y el final de «Arrastrar», y `CapabilityRules` y `CapabilitySettings` guardan las reglas del servidor y lo que tiene activado cada jugador. `BlockStates` traduce entre los estados de Minecraft y esas clases (propiedades en texto, familias de `BlockFamilies`).

1. El servidor manda `CapabilityStatePayload` al entrar y en cada cambio (y lo recomprueba cada 2 s por si cambian el modo de juego o los permisos). Sin ese estado el cliente no intercepta nada.
2. El cliente intercepta el clic derecho con un mixin en `MinecraftClient.doItemUse`, el izquierdo con `ClientPreAttackCallback` y la `F` al empezar el tick, y manda la intención (`CapabilityActionPayload`) con el bloque, la cara y el punto pulsados, como el paquete de uso de Minecraft.
3. `CapabilityService` comprueba permiso, modo de juego, que la capacidad esté activa, distancia y freno, y convierte la intención en una lista de `HandEdit`.
4. `HandEditApplier` deja que `HandEditEvents.COLLECT` añada bloques (simetría), descarta las posiciones donde el jugador no puede construir y escribe con una `EditSession` (sin los efectos `NEIGHBORS` y `UPDATE` con «Sin actualizaciones»). Si cambia más de un bloque, la sesión va al historial de WorldEdit.

«Alcance largo» es un modificador temporal del atributo `block_interaction_range`, y la velocidad de vuelo, las habilidades del jugador: los dos los sincroniza Minecraft y no necesitan el mod en el cliente.

Los pinceles de colocación (`place`, `scatter`, `array`) no usan `BrushOperation`: cada clic llama a `PlacementService.startBrush` con un `BrushTarget` (bloque mirado, normal de la cara, trazo y dirección de `ViewDiff`) y siguen el camino de `/komaedit place`. Solo `place -p` usa aún la colocación de 90° con holograma, que pide sus estructuras a `StructureSources` y las gira con la base de `brush.place.Alignment`.

Los permisos finos y sus paquetes viven en `permission.PermissionNodes`, lógica pura que recibe qué nodos tiene el jugador; `KomaPermissions` le pasa el actor de WorldEdit. Una orden con varios modos se oculta con `KomaPermissions.canAny(familia)` y cada modo se comprueba al ejecutarse con `requireBuild`.

## Representación compacta

`PackedBlockPlan` usa el mismo reparto lógico de bits que una posición Minecraft:

- 26 bits para X.
- 12 bits para Y con signo.
- 26 bits para Z.

Un plan de patrón necesita 8 bytes por posición, sin contar el array, frente a múltiples objetos Java por bloque. Las operaciones que bajan terreno añaden un array opcional de referencias a estados concretos para representar aire sin perder la paleta de las elevaciones.

## Invariantes de concurrencia

- `WorldSnapshot.capture`, análisis de selección, parseo de patrones y `EditSession.setBlock` ocurren en el hilo del servidor.
- `SculptPlanner`, `ShapeGenerator` y los algoritmos de ruido solo consumen datos capturados.
- Un nuevo cálculo del mismo jugador cancela el anterior.
- Una edición activa no se sustituye silenciosamente; debe terminar o cancelarse.
- El pool tiene cola finita y rechaza trabajo cuando está saturado.
- El servidor reparte un presupuesto global entre jugadores con round-robin.

## Historial

Una `EditSession` permanece abierta durante los slices. Al finalizar se cierra y se entrega a `LocalSession.remember`. Esto conserva una sola operación lógica en `//undo` aunque haya requerido cientos de ticks.

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

1. `RegionOperationService.submit(jugador, margen, descripción, operación)` lee la selección más un margen. La lectura va repartida entre ticks (unos 4 ms por tick) y se guarda en un `RegionSnapshot` inmutable con el id crudo de cada estado, una tabla de estados (`StateInfo`: estado de WorldEdit, id, aire, líquido, sólido y propiedades) y qué posiciones están dentro de la selección.
2. La operación (`RegionOperation`) se ejecuta en un worker de `AsyncEngine`. Solo lee la foto y escribe en `RegionEdits`. Debe llamar a `RegionEdits.checkCancelled()` en bucles largos.
3. `RegionEdits` produce un `PackedBlockPlan` de escrituras condicionales: cada cambio lleva el id del estado leído y `BatchEditService` lo descarta si el bloque cambió mientras tanto. El plan entra en el historial de WorldEdit.

Si la operación tiene que escribir fuera de lo leído (la dispersión de estructuras, que asoma de la región), `RegionOperationService.capture(jugador, región, margen, descripción, alTerminar)` solo lee y entrega la foto en el hilo del servidor; la operación monta su propio `PackedBlockPlan`, condicional dentro de la foto. Así funcionan `place`, `array` y `scatter` (paquete `placement`).

Si el resultado no son cambios de bloque sueltos (un plan con patrones y piezas, un informe, celdas de bioma), `SnapshotJobs.run(jugador, región, margen, descripción, cálculo, aplicar)` lee la región, ejecuta el cálculo en un worker y entrega el resultado en el hilo del servidor; `SnapshotJobs.compute` hace lo mismo sin leer el mundo. Así funcionan `text`, `analyze` y `biome`.

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

1. **La petición de la orden.** `PreviewFlags.install` (al final de `MoxiaCommands.register`) envuelve cada nodo ejecutable de `/komaedit`: le cuelga los literales `-v`/`--preview` o, en los de `greedyString`, quita la bandera del texto y vuelve a ejecutar la orden sin ella. La orden corre con una `EditRequest` (`DEFAULT`, `PREVIEW`, `CONFIRMED`, `TOOL`) en `EditRequests.current()`. `command.PreviewScope` dice qué familias no la llevan (gestión, ayuda, pinceles) y dónde `-v` ya es de ezEdits.
2. **El viaje.** `RegionOperationService` y `SnapshotCaptureService` recogen la petición al empezar y la restauran (`EditRequests.within`) al terminar la captura; `AsyncEngine.submit` la recoge al lanzar y la deja en el relevo del jugador (`EditRequests.handOff`) justo antes de entregar el resultado. Si una cadena la pierde, la última orden con `-v` queda armada unos minutos y se usa una vez. Un clic de herramienta envuelve su envío en `EditRequest.TOOL`; una confirmación, en `CONFIRMED`.
3. **La puerta.** `BatchEditService.submit`/`submitStyled` (sin `JobListener`) llaman a `PlanPreviews.intercept`. Si la petición es `PREVIEW`, o `DEFAULT` y el plan llega al umbral de `PreviewPreferences`, el plan no se encola: se prepara repartido entre ticks (bloque final de cada entrada con `patternFor`/`shapeFor`, los mismos que al aplicar, y estado actual del mundo) en un plan congelado de escrituras condicionales. `DisplaySampler` elige los displays en un hilo aparte y `PreviewDisplays` los manda por paquetes solo a ese jugador. `PlanPreviews.holds(jugador, plan)` dice al llamador si su plan quedó en vista previa, para no anunciar «en cola».
4. **Confirmar.** `PreviewFlow` (lógica pura, con pruebas) guarda la vista previa, la caduca y, al confirmar, comprueba con `ZoneCheck` las posiciones vigiladas antes de encolar el mismo plan congelado. Si algo cambió, recalcula: con `PlanPreviews.Extras.recompute` si la operación lo dio (`RegionOperationService` relanza la misma selección y vigila también toda ella), si no repitiendo la orden, y si no hay orden, volviendo a preparar el plan. Cada salida pasa una vez por `hide`, que retira los displays.
5. **Progreso.** `progress.ProgressBars` pregunta cada cinco ticks a `BatchEditService.status`, `PlanPreviews.progress`, `RegionOperationService.progress` y `AsyncEngine.status`, y pinta una bossbar por jugador con `ProgressLine`. Un cálculo puede informar de su avance con `AsyncEngine.progress(hecho, total)` desde el worker; si no, la barra cuenta el tiempo.
6. **Cancelar.** `/komaedit cancel` (`command.PreviewCommands`) corta la captura, el worker y la colocación. `edit.PlanApplication` recorre los planes por tramos y se para siempre en una frontera de entrada, así que la `EditSession` que se cierra y se registra en `LocalSession` cubre exactamente lo colocado.

Para un comando nuevo: si es de gestión (no coloca bloques) o ya usa `-v` con otro sentido, añádelo a `PreviewScope`; si su plan no pasa por `BatchEditService.submit`, no tendrá vista previa. Si después de encolar anuncia «en cola», compruébalo antes con `PlanPreviews.holds`.

## Máscaras y patrones en WorldEdit

`worldedit.EzExtensions` registra `EzMaskParser` y `EzPatternParser` (paquete `worldedit.ez`) en las factorías de WorldEdit al arrancar el servidor, así que valen en cualquier orden de WorldEdit. Cada parser devuelve `null` si la entrada no es suya y deja seguir a los demás.

- `EzArguments` lee `#nombre[a][b]…` con corchetes anidados; `NAMES` de cada parser asocia alias y nombre canónico.
- Lo que depende del jugador (mirada, posición, portapapeles, máscara global) se fija al parsear, en el hilo de la orden.
- Las máscaras leen bloques del `Extent` que da WorldEdit y la luz del `LightingProvider`, que no carga chunks. Guardan la respuesta por estado de bloque.
- La lógica pura (`GradientMath`, `NearSearch`, `VoxelRay`, `EzDirections`, `SelectionShape`) no toca Minecraft y tiene pruebas.

## Extensiones previstas

Los nuevos tipos de brocha se incorporan en tres puntos:

1. Descriptor en `SculptBrushType`.
2. Planificador puro en `SculptPlanner` o un futuro `BrushOperation` registrable.
3. Control opcional en GUI/comandos.

Los pinceles nuevos ya usan la interfaz `BrushOperation`; las brochas de `SculptPlanner` pueden pasarse a ella sin tocar `SculptManager` ni el pipeline de preview/aplicación.

Para schematics y clipboard se prevé otro tipo de plan que transporte índices de paleta/NBT, sin forzar esos datos dentro del plan simple de brochas.
