package com.komainu.moxia.fluid;

import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.utility.BlockKinds;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Bola de fluido, como la Fluid Ball de Axiom: una esfera de agua, lava o nieve en polvo alrededor de un punto,
 * sin Minecraft.
 *
 * <ul>
 *     <li>De serie la bola sustituye todo lo que hay dentro (menos cofres y demás bloques con datos) y, con agua o
 *     lava, deja también las corrientes que salen de ella, ya quietas: se ven en la vista previa y se van con
 *     //undo.</li>
 *     <li>Solo fuentes: pone las fuentes y nada más (el juego hará las corrientes cuando algo las toque).</li>
 *     <li>Solo aire: llena el aire y lo que el agua se lleva (hierba, flores); con agua anega lo que lo admite.</li>
 *     <li>Cerrar bordes: quita de la bola cada fuente que se derramaría (con un hueco al lado o debajo fuera de la
 *     bola), una y otra vez, hasta que todo lo que queda está contenido: ningún flujo suelto.</li>
 * </ul>
 */
public final class FluidBall {
    private static final int SOURCE = 1;
    private static final int WATERLOG = 2;
    private static final int ALREADY = 3;
    private static final int[][] OUTLETS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

    private FluidBall() {
    }

    public record Settings(FluidKind fluid, double radius, boolean sourcesOnly, boolean airOnly, boolean closeEdges) {
        public static final double MAX_RADIUS = 32.0;

        public Settings {
            if (!(radius >= 0.0) || radius > MAX_RADIUS) {
                throw new IllegalArgumentException("El radio va de 0 a " + (int) MAX_RADIUS);
            }
        }
    }

    /**
     * @param sources     fuentes (o nieve) que se ponen
     * @param waterlogged bloques que se anegan
     * @param flows       corrientes que salen de la bola
     * @param trimmed     posiciones de la bola que se quitaron al cerrar los bordes
     * @param kept        bloques con datos que la bola respeta
     * @param overflow    si el líquido seguiría corriendo fuera de la zona leída o más de la cuenta
     */
    public record Result(FluidWrites writes, int sources, int waterlogged, int flows, int trimmed, int kept,
                         boolean overflow) {
    }

    public static Result plan(RegionSnapshot snapshot, BlockKinds kinds, int cx, int cy, int cz, Settings settings,
                              FlowSim.Rules rules, int maxFlows, Runnable checkCancelled) {
        FluidKind fluid = settings.fluid();
        FluidCells cells = new FluidCells(snapshot, kinds, fluid, settings.airOnly());
        double radius = settings.radius();
        double limit = radius * radius + radius;
        int reach = (int) Math.ceil(radius);
        Int2IntOpenHashMap ball = new Int2IntOpenHashMap();
        IntArrayList order = new IntArrayList();
        int kept = 0;
        for (int dy = -reach; dy <= reach; dy++) {
            checkCancelled.run();
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    if (dx * dx + dy * dy + dz * dz > limit || !cells.contains(cx + dx, cy + dy, cz + dz)) {
                        continue;
                    }
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    int kind = cells.at(x, y, z);
                    int action;
                    if (settings.airOnly()) {
                        action = switch (kind) {
                            case FluidCells.EMPTY, FluidCells.FLOW -> SOURCE;
                            case FluidCells.LOGGABLE -> WATERLOG;
                            case FluidCells.FULL -> ALREADY;
                            default -> 0;
                        };
                    } else if (kinds.blockEntity(snapshot.rawId(x, y, z))) {
                        kept++;
                        action = 0;
                    } else {
                        action = kind == FluidCells.FULL ? ALREADY : SOURCE;
                    }
                    if (action != 0) {
                        int index = cells.index(x, y, z);
                        ball.put(index, action);
                        order.add(index);
                    }
                }
            }
        }
        int trimmed = settings.closeEdges() && fluid.flows() ? trim(cells, ball, order) : 0;
        FluidWrites writes = new FluidWrites(fluid);
        int sources = 0;
        int waterlogged = 0;
        for (int index : order) {
            int action = ball.get(index);
            if (action == SOURCE) {
                writes.fluid(cells.x(index), cells.y(index), cells.z(index), 0);
                sources++;
            } else if (action == WATERLOG) {
                writes.waterlog(cells.x(index), cells.y(index), cells.z(index));
                waterlogged++;
            }
        }
        int flows = 0;
        boolean overflow = false;
        if (fluid.flows() && !settings.sourcesOnly() && sources + waterlogged > 0) {
            FlowSim sim = new FlowSim(snapshot, kinds, fluid, rules, checkCancelled);
            for (int index : order) {
                int action = ball.get(index);
                if (action == SOURCE || action == WATERLOG) {
                    sim.source(cells.x(index), cells.y(index), cells.z(index));
                }
            }
            FlowSim.Result result = sim.run(maxFlows);
            overflow = result.overflow();
            for (int entry = 0; entry < result.size(); entry++) {
                int index = result.positions()[entry];
                if (ball.containsKey(index)) {
                    continue;
                }
                int level = result.level(entry);
                if (level < 0) {
                    writes.air(result.x(entry), result.y(entry), result.z(entry));
                } else {
                    writes.fluid(result.x(entry), result.y(entry), result.z(entry), level);
                    flows++;
                }
            }
        }
        return new Result(writes, sources, waterlogged, flows, trimmed, kept, overflow);
    }

    /**
     * Cerrar bordes: quita las posiciones que se derramarían, hasta que no quede ninguna. Una que se quita vuelve a
     * ser lo que era: si era roca no abre nada, si era aire puede dejar al aire a sus vecinas, que se miran otra vez.
     */
    private static int trim(FluidCells cells, Int2IntOpenHashMap ball, IntArrayList order) {
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        for (int index : order) {
            queue.enqueue(index);
        }
        int trimmed = 0;
        while (!queue.isEmpty()) {
            int index = queue.dequeueInt();
            int action = ball.get(index);
            if (action != SOURCE && action != WATERLOG || !leaks(cells, ball, index)) {
                continue;
            }
            ball.remove(index);
            trimmed++;
            int kind = cells.at(index);
            if (kind != FluidCells.EMPTY && kind != FluidCells.FLOW) {
                continue;
            }
            int x = cells.x(index);
            int y = cells.y(index);
            int z = cells.z(index);
            int[][] around = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] offset : around) {
                if (cells.contains(x + offset[0], y + offset[1], z + offset[2])) {
                    int neighbor = cells.index(x + offset[0], y + offset[1], z + offset[2]);
                    if (ball.containsKey(neighbor)) {
                        queue.enqueue(neighbor);
                    }
                }
            }
        }
        order.removeIf(index -> !ball.containsKey(index));
        return trimmed;
    }

    /** Si el líquido de esa posición saldría de la bola por un lado o por debajo. */
    private static boolean leaks(FluidCells cells, Int2IntOpenHashMap ball, int index) {
        int x = cells.x(index);
        int y = cells.y(index);
        int z = cells.z(index);
        for (int[] outlet : OUTLETS) {
            int nx = x + outlet[0];
            int ny = y + outlet[1];
            int nz = z + outlet[2];
            if (!cells.contains(nx, ny, nz)) {
                return true;
            }
            if (ball.containsKey(cells.index(nx, ny, nz))) {
                continue;
            }
            int kind = cells.at(nx, ny, nz);
            if (kind == FluidCells.EMPTY || kind == FluidCells.FLOW) {
                return true;
            }
        }
        return false;
    }
}
