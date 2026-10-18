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
| `preview` | Estado de preview y generación de hologramas de trayectoria |
| `gradient` | Paletas y selección streaming por eje |
| `generator` | Generadores de ezEdits (formas, noisegen, flowfield, flowline): lógica pura sobre `Volume` y `GeneratedBlocks` |
| `persistence` | Proyecto por mundo, migraciones, journal, backups y escritura atómica |
| `permission` | Permisos granulares y excepciones de cuota |
| `config` | Límites del servidor y política de compatibilidad |
| `worldedit` | Adaptación de jugador, sesión, selección y patrones |
| `command` | API Brigadier y validación de entrada |
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

Para una operación nueva: escribe la lógica sobre `RegionSnapshot` (se puede montar a mano en tests con `RegionSnapshot.builder`), implementa `RegionOperation` y lánzala desde un comando con `RegionOperationService.submit`. `TweakOperation` es el ejemplo más pequeño. `weathering.WeatheringService` (Envejecer) adapta la foto a texto para `WeatheringEngine`, que es Java puro y se prueba con un mundo falso de texto.

Piezas relacionadas:

- `noise.NoiseSpec`: ruido con la notación de ezEdits sobre FastNoiseLite. Es inmutable y se puede usar desde varios hilos tras `build(semilla)`.
- `gradient.PaletteSpec`: paletas `##Nombre`, `-`, `(a:b)`, `*n` y `[…]`. Se resuelven a texto en el hilo del servidor, y los patrones de WorldEdit se parsean ahí antes de pasar al worker.
- `region.RegionMasks` y `region.SnapshotExtent`: las máscaras de WorldEdit se parsean en el hilo del servidor con un `Extent` de solo lectura sin foto; la operación llama a `bind(snapshot)` al empezar y desde ahí la máscara lee la foto en el worker. Fuera de la foto todo es aire y los biomas dan error.
- Luz: si `RegionOperation.needsLight()` devuelve `true`, la captura guarda también la luz de bloque y del cielo de cada posición (`RegionSnapshot.blockLight`, `skyLight`); `StateInfo.luminance` es la luz que emite cada estado.
- `texture.TextureContext` y `texture.TextureField`: los modos de `/komaedit texture` son campos puros (un valor de 0 a 1 o `NaN` por bloque) sobre la foto, combinables; `TextureOperation` los convierte en bloques de la paleta. Sirven de ejemplo para medir sobre la foto (sumas acumuladas de sólidos, normales, suavizado).
- `gradient.SavedPalettes` y `noise.SavedNoises`: paletas (`#nombre`) y ruidos guardados por jugador y mundo sobre `persistence.NamedDefinitions`. Para aceptarlos, pasa `SavedPalettes.resolver(uuid)` a `PaletteSpec.parse` o `SavedNoises.resolver(uuid)` a `NoiseSpec.parse`.

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
