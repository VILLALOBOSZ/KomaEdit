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
| `edit` | Plan compacto y aplicación WorldEdit por lotes |
| `performance` | Pool acotado, cancelación, cuotas y aislamiento de cálculos |
| `preview` | Estado de preview y generación de hologramas de trayectoria |
| `gradient` | Paletas y selección streaming por eje |
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

Para una operación nueva: escribe la lógica sobre `RegionSnapshot` (se puede montar a mano en tests con `RegionSnapshot.builder`), implementa `RegionOperation` y lánzala desde un comando con `RegionOperationService.submit`. `TweakOperation` es el ejemplo más pequeño.

Piezas relacionadas:

- `noise.NoiseSpec`: ruido con la notación de ezEdits sobre FastNoiseLite. Es inmutable y se puede usar desde varios hilos tras `build(semilla)`.
- `gradient.PaletteSpec`: paletas `##Nombre`, `-`, `(a:b)`, `*n` y `[…]`. Se resuelven a texto en el hilo del servidor, y los patrones de WorldEdit se parsean ahí antes de pasar al worker.

## Extensiones previstas

Los nuevos tipos de brocha se incorporan en tres puntos:

1. Descriptor en `SculptBrushType`.
2. Planificador puro en `SculptPlanner` o un futuro `BrushOperation` registrable.
3. Control opcional en GUI/comandos.

El siguiente refactor de extensibilidad extraerá cada algoritmo a implementaciones de una interfaz `BrushOperation`, manteniendo estable `SculptManager` y el pipeline de preview/aplicación.

Para schematics y clipboard se prevé otro tipo de plan que transporte índices de paleta/NBT, sin forzar esos datos dentro del plan simple de brochas.
