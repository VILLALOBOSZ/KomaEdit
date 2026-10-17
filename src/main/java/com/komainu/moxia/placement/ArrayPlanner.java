package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Coloca estructuras una tras otra a lo largo de la trayectoria. Cada colocación avanza lo que mide su caja a lo
 * largo de la tangente más el hueco {@code -g} (negativo, se solapan). {@code -q} escala de forma progresiva,
 * {@code -y} limita cuánto puede subir cada colocación respecto a la anterior y {@code -b} lleva cada una a la
 * superficie más cercana.
 */
public final class ArrayPlanner {
    public static final int MAX_PLACEMENTS = 5_000;

    private ArrayPlanner() {
    }

    /** Busca la superficie más cercana a un punto del recorrido; {@code null} si no la encuentra. */
    @FunctionalInterface
    public interface SurfaceSnapper {
        Snapped snap(Vec3 position);
    }

    /** Bloque de aire sobre la superficie encontrada y la normal de esa superficie. */
    public record Snapped(Vec3 anchor, Vec3 normal) {
    }

    public static List<Placement> plan(
            PlacementPath path,
            PlacementBuilder builder,
            Vec3 player,
            DirectionExpr.Context view,
            SurfaceSnapper snapper
    ) {
        PlacementSettings settings = builder.settings();
        PlacementSettings.Array array = settings.array();
        List<Placement> placements = new ArrayList<>();
        double length = path.length();
        double distance = 0.0;
        Double previousY = null;
        int index = 0;
        while (distance <= length + 1.0E-6) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException();
            }
            if (placements.size() >= MAX_PLACEMENTS) {
                throw new IllegalArgumentException("Saldrían más de " + MAX_PLACEMENTS
                        + " estructuras; aumenta el hueco con -g");
            }
            PlacementPath.Frame frame = path.at(distance);
            double progress = length <= 0 ? 0.0 : distance / length;
            Vec3 anchor = blockCenter(frame.position());
            Vec3 normal = null;
            if (snapper != null) {
                Snapped snapped = snapper.snap(frame.position());
                if (snapped != null) {
                    anchor = snapped.anchor();
                    normal = snapped.normal();
                }
            }
            if (!Double.isNaN(array.maxOffset()) && previousY != null
                    && anchor.y() > previousY + array.maxOffset()) {
                anchor = new Vec3(anchor.x(), Math.floor(previousY + array.maxOffset() - 0.5) + 0.5, anchor.z());
            }
            previousY = anchor.y();
            Alignment.Context context = new Alignment.Context(anchor, player, view, normal, frame.tangent(),
                    frame.normal(), new Vec3(0, 0, distance), new Vec3(0, 0, progress),
                    new SplittableRandom(settings.seed() * 0x9E3779B97F4A7C15L + index), null);
            Placement placement = builder.build(anchor, context, array.progression().at(progress));
            placements.add(placement);
            double[] extent = placement.extentAlong(frame.tangent());
            double step = Math.max(0.5, extent[1] - extent[0] + array.gap());
            distance += step;
            if (path.closed() && distance > length - step * 0.5) {
                break;
            }
            index++;
        }
        return placements;
    }

    /**
     * Superficie (bloque que no es aire con una cara al aire) más cercana dentro de {@code radius} bloques, buscada
     * por capas cúbicas crecientes: en cuanto la capa queda más lejos que la mejor encontrada, no hay otra mejor.
     */
    public static SurfaceSnapper snapper(com.komainu.moxia.region.RegionSnapshot snapshot, int radius) {
        return position -> {
            int cx = (int) Math.floor(position.x());
            int cy = (int) Math.floor(position.y());
            int cz = (int) Math.floor(position.z());
            int[] best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int shell = 0; shell <= radius && shell <= Math.ceil(Math.sqrt(bestDistance)); shell++) {
                for (int dy = -shell; dy <= shell; dy++) {
                    for (int dz = -shell; dz <= shell; dz++) {
                        boolean face = Math.abs(dy) == shell || Math.abs(dz) == shell;
                        for (int dx = -shell; dx <= shell; dx += face ? 1 : Math.max(1, 2 * shell)) {
                            int x = cx + dx;
                            int y = cy + dy;
                            int z = cz + dz;
                            if (!snapshot.contains(x, y, z) || snapshot.isAir(x, y, z)
                                    || !ScatterPlanner.touchesAir(snapshot, x, y, z)) {
                                continue;
                            }
                            double distance = dx * dx + dy * dy + dz * dz;
                            if (distance < bestDistance) {
                                bestDistance = distance;
                                best = new int[]{x, y, z};
                            }
                        }
                    }
                }
            }
            if (best == null) {
                return null;
            }
            Vec3 normal = ScatterPlanner.normal(snapshot, best[0], best[1], best[2]);
            int[] anchor = ScatterPlanner.anchor(snapshot, best[0], best[1], best[2], normal);
            return new Snapped(new Vec3(anchor[0] + 0.5, anchor[1] + 0.5, anchor[2] + 0.5), normal);
        };
    }

    static Vec3 blockCenter(Vec3 position) {
        return new Vec3(Math.floor(position.x()) + 0.5, Math.floor(position.y()) + 0.5, Math.floor(position.z()) + 0.5);
    }
}
