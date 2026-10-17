package com.komainu.moxia.brush.ez;

import com.sk89q.worldedit.math.BlockVector3;

/**
 * Pinceles de degradado de paleta (Gradient y Gradient Stroke de ezEdits). Cada bloque recibe una posición
 * {@code t} en el degradado (0 en el inicio, 1 en el final), el ruido la empuja según la interpolación y el
 * sangrado, y el resultado elige la entrada de la paleta.
 *
 * <p>Por defecto solo se pintan cubos enteros que ya existen; las losas y escaleras con {@code shaped} y el aire
 * con {@code replaceAir}.</p>
 */
public final class GradientPainter {
    private GradientPainter() {
    }

    /** Posición en el degradado de cada punto del espacio. */
    @FunctionalInterface
    public interface Ramp {
        double t(double x, double y, double z);
    }

    @FunctionalInterface
    public interface Sink {
        void accept(int x, int y, int z, int paletteIndex);
    }

    public record Settings(
            int paletteSize,
            GradientInterpolation interpolation,
            double bleed,
            BrushNoise noise,
            boolean replaceAir,
            boolean shaped
    ) {
        public Settings {
            if (paletteSize < 1) {
                throw new IllegalArgumentException("La paleta está vacía");
            }
        }
    }

    /** Proyección sobre la recta del primer al segundo punto: el plano perpendicular que pasa por cada uno. */
    public static Ramp plane(BlockVector3 start, BlockVector3 end) {
        double ax = start.x() + 0.5;
        double ay = start.y() + 0.5;
        double az = start.z() + 0.5;
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double dz = end.z() - start.z();
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared < 1.0E-9) {
            throw new IllegalArgumentException("Los dos puntos del degradado coinciden");
        }
        return (x, y, z) -> ((x - ax) * dx + (y - ay) * dy + (z - az) * dz) / lengthSquared;
    }

    /** Distancia al primer punto medida con {@code function}; el segundo punto marca el final (t = 1). */
    public static Ramp distance(BlockVector3 start, BlockVector3 end, DistanceFunction function) {
        double ax = start.x() + 0.5;
        double ay = start.y() + 0.5;
        double az = start.z() + 0.5;
        double reach = function.measure(end.x() - start.x(), end.y() - start.y(), end.z() - start.z());
        if (reach < 1.0E-9) {
            throw new IllegalArgumentException("Los dos puntos del degradado coinciden");
        }
        return (x, y, z) -> function.measure(x - ax, y - ay, z - az) / reach;
    }

    /** Entrada de la paleta para la posición {@code t} del bloque {@code (x, y, z)}. */
    public static int index(Settings settings, double t, int x, int y, int z) {
        double value = t;
        if (settings.interpolation() != GradientInterpolation.NONE && settings.bleed() != 0.0) {
            double offset = settings.bleed() * (settings.noise().sample(x, y, z) * 2.0 - 1.0);
            value = settings.interpolation().apply(t, offset);
        }
        return GradientInterpolation.paletteIndex(value, settings.paletteSize());
    }

    public static boolean paintable(BrushWorld.Cell cell, Settings settings) {
        return switch (cell) {
            case FULL -> true;
            case SHAPED -> settings.shaped();
            case AIR -> settings.replaceAir();
            case LIQUID, OTHER -> false;
        };
    }

    /** Esfera del pincel alrededor de {@code center}. */
    public static int paintSphere(
            BrushWorld world,
            BlockVector3 center,
            int radius,
            Ramp ramp,
            Settings settings,
            Sink sink
    ) {
        int changes = 0;
        double limit = (radius + 0.5) * (radius + 0.5);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dy * dy + dz * dz > limit) {
                        continue;
                    }
                    int x = center.x() + dx;
                    int y = center.y() + dy;
                    int z = center.z() + dz;
                    if (!world.contains(x, y, z) || !paintable(world.cell(x, y, z), settings)) {
                        continue;
                    }
                    double t = ramp.t(x + 0.5, y + 0.5, z + 0.5);
                    sink.accept(x, y, z, index(settings, t, x, y, z));
                    changes++;
                }
            }
        }
        return changes;
    }

    /**
     * Tubo de radio {@code radius} alrededor del trazo. El degradado va a lo largo del trazo o, con
     * {@code distanceToCenter}, del eje (t = 0) al borde del tubo (t = 1).
     */
    public static int paintStroke(
            BrushWorld world,
            StrokePath path,
            int radius,
            boolean distanceToCenter,
            Settings settings,
            Sink sink
    ) {
        int changes = 0;
        double reach = radius + 0.5;
        for (int y = path.minimumY() - radius; y <= path.maximumY() + radius; y++) {
            checkInterrupted();
            for (int z = path.minimumZ() - radius; z <= path.maximumZ() + radius; z++) {
                for (int x = path.minimumX() - radius; x <= path.maximumX() + radius; x++) {
                    if (!world.contains(x, y, z) || !paintable(world.cell(x, y, z), settings)) {
                        continue;
                    }
                    StrokePath.Closest closest = path.closest(x + 0.5, y + 0.5, z + 0.5);
                    if (closest.distance() > reach) {
                        continue;
                    }
                    double t = distanceToCenter ? closest.distance() / Math.max(0.5, reach) : closest.along();
                    sink.accept(x, y, z, index(settings, t, x, y, z));
                    changes++;
                }
            }
        }
        return changes;
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Cálculo cancelado");
        }
    }
}
