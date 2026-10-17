package com.komainu.moxia.deform;

import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.shape.smooth.SmoothPiece;

import java.util.function.Predicate;

/** Fotos de prueba montadas a mano y aplicación de campos sin Minecraft. */
final class Shapes {
    static final int AIR = 0;
    static final int STONE = 1;
    static final int DIRT = 2;
    static final int GRASS = 3;
    static final int WATER = 4;

    private Shapes() {
    }

    interface Material {
        int at(int x, int y, int z);
    }

    /**
     * Foto de {@code size} bloques por eje desde el origen con un margen de {@code margin} bloques sin seleccionar
     * alrededor (vacío salvo que {@code material} diga otra cosa).
     */
    static RegionSnapshot snapshot(int sizeX, int sizeY, int sizeZ, int margin, Material material) {
        RegionSnapshot.Builder builder = RegionSnapshot.builder(-margin, -margin, -margin,
                sizeX + 2 * margin, sizeY + 2 * margin, sizeZ + 2 * margin)
                .state(new RegionSnapshot.StateInfo(AIR, null, "minecraft:air", true, false, false))
                .state(new RegionSnapshot.StateInfo(STONE, null, "minecraft:stone", false, false, true))
                .state(new RegionSnapshot.StateInfo(DIRT, null, "minecraft:dirt", false, false, true))
                .state(new RegionSnapshot.StateInfo(GRASS, null, "minecraft:grass_block", false, false, true))
                .state(new RegionSnapshot.StateInfo(WATER, null, "minecraft:water", false, true, false));
        for (int x = -margin; x < sizeX + margin; x++) {
            for (int y = -margin; y < sizeY + margin; y++) {
                for (int z = -margin; z < sizeZ + margin; z++) {
                    boolean inside = x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
                    builder.set(x, y, z, material.at(x, y, z), inside);
                }
            }
        }
        return builder.build();
    }

    static Material solid(Predicate<int[]> shape, int id) {
        return (x, y, z) -> shape.test(new int[]{x, y, z}) ? id : AIR;
    }

    static Material sphere(double cx, double cy, double cz, double radius) {
        return (x, y, z) -> {
            double dx = x + 0.5 - cx;
            double dy = y + 0.5 - cy;
            double dz = z + 0.5 - cz;
            return dx * dx + dy * dy + dz * dz <= radius * radius ? STONE : AIR;
        };
    }

    /** Resultado aplicado: id por posición de la captura y la pieza que quedó. */
    static final class Result {
        final Volume volume;
        final int[] ids;
        final byte[] pieces;
        FieldWriter.Stats stats;

        Result(Volume volume) {
            this.volume = volume;
            this.ids = volume.material.clone();
            this.pieces = new byte[ids.length];
        }

        int at(int x, int y, int z) {
            return volume.contains(x, y, z) ? ids[volume.index(x, y, z)] : AIR;
        }

        byte piece(int x, int y, int z) {
            return pieces[volume.index(x, y, z)];
        }

        int count(int id) {
            int count = 0;
            for (int value : ids) {
                if (value == id) {
                    count++;
                }
            }
            return count;
        }

        int occupied() {
            int count = 0;
            for (int value : ids) {
                if (value != AIR) {
                    count++;
                }
            }
            return count;
        }
    }

    static Result apply(Volume volume, DeformField field, int[] box, SmoothProfile profile) {
        Result result = new Result(volume);
        result.stats = FieldWriter.write(volume, field, box, profile, (x, y, z, rawId, piece) -> {
            int index = volume.index(x, y, z);
            result.ids[index] = rawId;
            result.pieces[index] = piece;
        }, () -> {
        });
        return result;
    }

    static Result apply(Volume volume, DeformField field) {
        return apply(volume, field, volume.selectionBounds(), SmoothProfile.NONE);
    }

    static boolean isPiece(Result result, int x, int y, int z) {
        return result.piece(x, y, z) != SmoothPiece.FULL;
    }

    static final Runnable NO_CANCEL = () -> {
    };
}
