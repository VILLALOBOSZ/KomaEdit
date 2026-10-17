package com.komainu.moxia.brush.ez;

import com.sk89q.worldedit.math.BlockVector3;

/**
 * Pincel Pintor de pétalos (Petal Painter de ezEdits): esparce follaje en capas (pétalos rosas, flores silvestres,
 * hojarasca) sobre los bloques de la máscara dentro de la esfera del pincel.
 *
 * <p>Cada hueco de aire con suelo válido debajo toma un valor del ruido. Si queda por debajo de la densidad, recibe
 * follaje, y cuanto más bajo, más piezas (de 1 al máximo del bloque). Donde ya hay follaje del mismo tipo se
 * conserva la orientación y solo se sube el número de piezas, así pasar el pincel varias veces lo espesa.</p>
 */
public final class PetalPainter {
    private PetalPainter() {
    }

    /**
     * @param blockId   bloque de follaje ({@code minecraft:pink_petals})
     * @param maxAmount piezas máximas del bloque (4 en los de vanilla)
     * @param seed      orientación de las piezas nuevas
     */
    public record Settings(String blockId, int maxAmount, double density, BrushNoise noise, BlockFilter ground,
                           long seed) {
        public Settings {
            if (maxAmount < 1) {
                throw new IllegalArgumentException("El follaje necesita al menos una pieza");
            }
            if (density < 0.0 || density > 1.0) {
                throw new IllegalArgumentException("La densidad debe estar entre 0 y 1");
            }
            blockId = PaletteShift.normalize(blockId);
        }
    }

    /**
     * @param amount  piezas finales; 0 quita el follaje
     * @param facing  orientación horizontal 0–3 (sur, oeste, norte, este) o -1 para conservar la que había
     */
    @FunctionalInterface
    public interface Sink {
        void accept(int x, int y, int z, int amount, int facing);
    }

    /** Piezas para un valor de ruido: 0 si no supera la densidad; más cuanto más bajo es el valor. */
    public static int amount(double density, double noise, int maxAmount) {
        if (density <= 0.0 || noise >= density) {
            return 0;
        }
        double strength = 1.0 - Math.max(0.0, noise) / density;
        return Math.clamp(1 + (int) Math.floor(strength * maxAmount), 1, maxAmount);
    }

    public static int paint(BrushWorld world, BlockVector3 center, int radius, Settings settings, boolean erase,
                            Sink sink) {
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
                    if (!world.contains(x, y, z) || !world.contains(x, y - 1, z)) {
                        continue;
                    }
                    boolean same = settings.blockId().equals(PaletteShift.normalize(world.blockId(x, y, z)));
                    if (!same && world.cell(x, y, z) != BrushWorld.Cell.AIR) {
                        continue;
                    }
                    if (!settings.ground().matches(world.blockId(x, y - 1, z), world.cell(x, y - 1, z))) {
                        continue;
                    }
                    int existing = same ? Math.max(1, world.intProperty(x, y, z, "amount")) : 0;
                    if (erase) {
                        if (existing > 0) {
                            sink.accept(x, y, z, 0, -1);
                            changes++;
                        }
                        continue;
                    }
                    int wanted = amount(settings.density(), settings.noise().sample(x, y, z), settings.maxAmount());
                    int result = Math.max(existing, wanted);
                    if (result == existing) {
                        continue;
                    }
                    int facing = existing > 0 ? -1
                            : (int) Math.floor(BrushNoise.unit(x, y, z, settings.seed() ^ 0x5EED_FACEL) * 4.0);
                    sink.accept(x, y, z, result, facing);
                    changes++;
                }
            }
        }
        return changes;
    }
}
