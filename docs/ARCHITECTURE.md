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
| `performance` | Pool acotado, cancelación y aislamiento de cálculos |
| `preview` | Preview de las figuras de trayectoria |
| `gradient` | Paletas y selección streaming por eje |
| `persistence` | Presets versionados y escritura atómica |
| `worldedit` | Adaptación de jugador, sesión, selección y patrones |
| `command` | API Brigadier y validación de entrada |
| `client.gui` | Pantalla no bloqueante y controles del editor |

## Flujo de una brocha

1. `SculptManager` resuelve el objetivo y captura un `WorldSnapshot` en el servidor.
2. `AsyncEngine` ejecuta `SculptPlanner` en un worker cancelable.
3. `SculptPlanner` genera un `PackedBlockPlan`.
4. `SculptPreviewManager` conserva el plan y muestrea partículas.
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

## Extensiones previstas

Los nuevos tipos de brocha se incorporan en tres puntos:

1. Descriptor en `SculptBrushType`.
2. Planificador puro en `SculptPlanner` o un futuro `BrushOperation` registrable.
3. Control opcional en GUI/comandos.

El siguiente refactor de extensibilidad extraerá cada algoritmo a implementaciones de una interfaz `BrushOperation`, manteniendo estable `SculptManager` y el pipeline de preview/aplicación.

Para schematics y clipboard se prevé otro tipo de plan que transporte índices de paleta/NBT, sin forzar esos datos dentro del plan simple de brochas.
