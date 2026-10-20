package com.komainu.moxia.shape.tube;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.GeometryLimitException;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;
import com.komainu.moxia.shape.Vec3;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rama o tronco para los generadores de vegetación: un tubo redondo que pasa por sus puntos de control con un radio
 * en cada uno. Usa el mismo espacio de tubo que {@code sweep} (curva de Kochanek–Bartels, marcos de mínima rotación
 * y voxelizado inverso con supermuestreo), así que el grosor cambia sin escalones ni huecos.
 *
 * <p>Además de los bloques dice hacia dónde va la rama en cada uno (el eje del mundo más alineado con la tangente),
 * para orientar los troncos, y garantiza que el eje queda unido cara con cara aunque la rama sea de un bloque de
 * ancho y vaya en diagonal: una rama nunca se queda pegada solo por una arista o un vértice.</p>
 */
public final class TubeLimb {
    /** Radio mínimo del espacio de tubo: por debajo la sección sigue siendo de un bloque. */
    public static final double MINIMUM_RADIUS = 0.5;
    private static final double SAME_POINT = 1.0E-3;
    /** Por debajo de esta longitud la rama es un muñón: el eje no llega a tener dos muestras. */
    private static final double SHORTEST = 0.25;
    private static final int ROUND_END = TubeSettings.EndMode.ROUND.ordinal();

    private TubeLimb() {
    }

    /** Recibe cada bloque de la rama. */
    @FunctionalInterface
    public interface Sink {
        /**
         * @param axis     0 = x, 1 = y, 2 = z: el eje del mundo más alineado con la rama en ese bloque
         * @param progress de 0 en el primer punto de control a 1 en el último
         */
        void accept(int x, int y, int z, int axis, double progress);
    }

    /**
     * Voxeliza la rama con extremos redondeados y entrega cada bloque una sola vez.
     *
     * @param controls puntos de control en coordenadas de mundo (el centro de un bloque es {@code x + 0.5})
     * @param radii    radio en bloques en cada punto de control
     * @param limit    máximo de bloques antes de cortar con {@link GeometryLimitException}
     * @return cuántos bloques se han entregado
     */
    public static int voxelize(List<Vec3> controls, double[] radii, int limit, Sink sink) {
        if (controls.size() != radii.length) {
            throw new IllegalArgumentException("Hace falta un radio por punto de control");
        }
        List<Vec3> points = new ArrayList<>(controls.size());
        List<Double> sizes = new ArrayList<>(controls.size());
        for (int index = 0; index < controls.size(); index++) {
            Vec3 control = controls.get(index);
            double radius = Math.max(MINIMUM_RADIUS, radii[index]);
            if (!points.isEmpty() && points.getLast().distance(control) < SAME_POINT) {
                sizes.set(sizes.size() - 1, Math.max(sizes.getLast(), radius));
                continue;
            }
            points.add(control);
            sizes.add(radius);
        }
        double maximum = MINIMUM_RADIUS;
        for (double size : sizes) {
            maximum = Math.max(maximum, size);
        }
        double travel = 0.0;
        for (int index = 1; index < points.size(); index++) {
            travel += points.get(index).distance(points.get(index - 1));
        }
        if (travel < SHORTEST) {
            // Un muñón más corto que una muestra del eje: una bola del radio mayor.
            return knob(controls.getFirst(), maximum, limit, sink);
        }
        int base = (int) Math.clamp(Math.ceil(maximum), 1.0, 128.0);
        ShapeParameters parameters = new ShapeParameters(PathShape.SWEEP, base, 2, 1, 0, new ShapeOptions(Map.of(
                "profile", (double) TubeSettings.TubeProfile.CIRCLE.ordinal(),
                "end", (double) ROUND_END,
                "quality", TubeSettings.Quality.BALANCED.value()
        )));
        TubeSettings plain = TubeSettings.from(parameters);
        double[] factors = new double[sizes.size()];
        for (int index = 0; index < factors.length; index++) {
            factors[index] = sizes.get(index) / base;
        }
        TubeSettings settings = plain.withRadii(new TubeSettings.RadiusProgression(
                positions(points, plain), factors));
        TubeCenterline line = TubeCenterline.through(points, settings);
        TubeShapes.TubeShape shape = TubeShapes.create(settings, parameters, line, 0L);
        LongOpenHashSet blocks = TubeVoxelizer.voxelize(line, settings, shape, limit);
        addSpine(line, blocks);
        if (blocks.size() > limit) {
            throw new GeometryLimitException(limit);
        }
        LongArrayList ordered = new LongArrayList(blocks);
        ordered.sort(null);
        TubeCenterline.TubePoint point = new TubeCenterline.TubePoint();
        for (int index = 0; index < ordered.size(); index++) {
            long key = ordered.getLong(index);
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            line.locate(x + 0.5, y + 0.5, z + 0.5, point);
            int segment = point.segment;
            int next = Math.min(line.count - 1, segment + 1);
            double tangentX = Math.abs(line.tx[segment] + (line.tx[next] - line.tx[segment]) * point.amount);
            double tangentY = Math.abs(line.ty[segment] + (line.ty[next] - line.ty[segment]) * point.amount);
            double tangentZ = Math.abs(line.tz[segment] + (line.tz[next] - line.tz[segment]) * point.amount);
            int axis = tangentY >= tangentX && tangentY >= tangentZ ? 1 : tangentX >= tangentZ ? 0 : 2;
            sink.accept(x, y, z, axis, point.progress);
        }
        return ordered.size();
    }

    private static int knob(Vec3 center, double radius, int limit, Sink sink) {
        int reach = (int) Math.ceil(radius);
        int count = 0;
        int baseX = (int) Math.floor(center.x());
        int baseY = (int) Math.floor(center.y());
        int baseZ = (int) Math.floor(center.z());
        for (int x = baseX - reach; x <= baseX + reach; x++) {
            for (int y = baseY - reach; y <= baseY + reach; y++) {
                for (int z = baseZ - reach; z <= baseZ + reach; z++) {
                    boolean own = x == baseX && y == baseY && z == baseZ;
                    double dx = x + 0.5 - center.x();
                    double dy = y + 0.5 - center.y();
                    double dz = z + 0.5 - center.z();
                    if (own || dx * dx + dy * dy + dz * dz <= radius * radius) {
                        if (++count > limit) {
                            throw new GeometryLimitException(limit);
                        }
                        sink.accept(x, y, z, 1, 0.0);
                    }
                }
            }
        }
        return count;
    }

    /**
     * Posición (0 a 1, por longitud de arco) de cada punto de control sobre la curva que pasa por ellos. La curva
     * densa empieza cada tramo exactamente en su punto de control.
     */
    private static double[] positions(List<Vec3> points, TubeSettings settings) {
        List<Vec3> dense = TubeCenterline.kochanekBartels(points, false, settings.tension(), settings.bias(),
                settings.continuity());
        double[] positions = new double[points.size()];
        double travelled = 0.0;
        int control = 1;
        for (int index = 1; index < dense.size() && control < points.size(); index++) {
            travelled += dense.get(index).distance(dense.get(index - 1));
            if (dense.get(index).distance(points.get(control)) < 1.0E-9) {
                positions[control++] = travelled;
            }
        }
        double total = positions[positions.length - 1];
        for (int index = 1; index < positions.length; index++) {
            positions[index] = index == positions.length - 1 ? 1.0 : positions[index] / total;
            if (positions[index] <= positions[index - 1]) {
                positions[index] = Math.nextUp(positions[index - 1]);
            }
        }
        return positions;
    }

    /**
     * Añade las celdas por las que pasa el eje, unidas cara con cara: entre dos celdas que solo se tocan por una
     * arista o un vértice se intercala la vecina más cercana al eje.
     */
    private static void addSpine(TubeCenterline line, LongOpenHashSet blocks) {
        int previousX = (int) Math.floor(line.px[0]);
        int previousY = (int) Math.floor(line.py[0]);
        int previousZ = (int) Math.floor(line.pz[0]);
        blocks.add(PackedBlockPlan.pack(previousX, previousY, previousZ));
        for (int index = 1; index < line.count; index++) {
            int x = (int) Math.floor(line.px[index]);
            int y = (int) Math.floor(line.py[index]);
            int z = (int) Math.floor(line.pz[index]);
            while (previousX != x || previousY != y || previousZ != z) {
                double best = Double.MAX_VALUE;
                int stepX = previousX;
                int stepY = previousY;
                int stepZ = previousZ;
                for (int axis = 0; axis < 3; axis++) {
                    int candidateX = previousX + (axis == 0 ? Integer.signum(x - previousX) : 0);
                    int candidateY = previousY + (axis == 1 ? Integer.signum(y - previousY) : 0);
                    int candidateZ = previousZ + (axis == 2 ? Integer.signum(z - previousZ) : 0);
                    if (candidateX == previousX && candidateY == previousY && candidateZ == previousZ) {
                        continue;
                    }
                    double distance = segmentDistance(line, index, candidateX + 0.5, candidateY + 0.5,
                            candidateZ + 0.5);
                    if (distance < best) {
                        best = distance;
                        stepX = candidateX;
                        stepY = candidateY;
                        stepZ = candidateZ;
                    }
                }
                previousX = stepX;
                previousY = stepY;
                previousZ = stepZ;
                blocks.add(PackedBlockPlan.pack(previousX, previousY, previousZ));
            }
        }
    }

    /** Distancia al cuadrado del punto al tramo del eje que acaba en la muestra {@code index}. */
    private static double segmentDistance(TubeCenterline line, int index, double x, double y, double z) {
        double startX = line.px[index - 1];
        double startY = line.py[index - 1];
        double startZ = line.pz[index - 1];
        double deltaX = line.px[index] - startX;
        double deltaY = line.py[index] - startY;
        double deltaZ = line.pz[index] - startZ;
        double lengthSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        double amount = lengthSquared < 1.0E-12 ? 0.0 : Math.clamp(((x - startX) * deltaX + (y - startY) * deltaY
                + (z - startZ) * deltaZ) / lengthSquared, 0.0, 1.0);
        double offsetX = startX + deltaX * amount - x;
        double offsetY = startY + deltaY * amount - y;
        double offsetZ = startZ + deltaZ * amount - z;
        return offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
    }
}
