package com.komainu.moxia.texture;

import com.komainu.moxia.region.RegionSnapshot;

/** Escenas de prueba hechas a mano sobre {@link RegionSnapshot}, sin Minecraft. */
final class TextureScenes {
    static final int AIR = 0;
    static final int STONE = 1;
    static final int TORCH = 2;

    private TextureScenes() {
    }

    static RegionSnapshot.Builder empty(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        return RegionSnapshot.builder(minX, minY, minZ, sizeX, sizeY, sizeZ)
                .state(new RegionSnapshot.StateInfo(AIR, null, "minecraft:air", true, false, false))
                .state(new RegionSnapshot.StateInfo(STONE, null, "minecraft:stone", false, false, true))
                .state(new RegionSnapshot.StateInfo(TORCH, null, "minecraft:glowstone", false, false, true, "", 15));
    }

    /**
     * Pared de piedra de 24 de ancho y 16 de alto (z de 0 a 3) con un saliente, un nicho y una grieta en la cara que
     * da a z negativa, y aire delante. Selección: toda la pared.
     */
    static RegionSnapshot wall() {
        int minX = -4;
        int minY = -4;
        int minZ = -8;
        int sizeX = 32;
        int sizeY = 24;
        int sizeZ = 16;
        RegionSnapshot.Builder builder = empty(minX, minY, minZ, sizeX, sizeY, sizeZ);
        for (int x = minX; x < minX + sizeX; x++) {
            for (int y = minY; y < minY + sizeY; y++) {
                for (int z = minZ; z < minZ + sizeZ; z++) {
                    builder.set(x, y, z, wallSolid(x, y, z) ? STONE : AIR, x >= 0 && x < 24 && y >= 0 && y < 16
                            && z >= -3 && z <= 3);
                }
            }
        }
        return builder.build();
    }

    static boolean wallSolid(int x, int y, int z) {
        if (x < 0 || x >= 24 || y < 0 || y >= 16) {
            return y < 0 && z >= -8; // suelo
        }
        // Saliente (contrafuerte) de 4×16 que sobresale dos bloques.
        if (x >= 4 && x < 8 && z >= -2) {
            return true;
        }
        // Nicho de 4×5 que entra dos bloques.
        if (x >= 14 && x < 18 && y >= 5 && y < 10) {
            return z >= 2 && z <= 3;
        }
        // Grieta vertical de un bloque.
        if (x == 11 && y >= 2 && y < 13) {
            return z >= 1 && z <= 3;
        }
        return z >= 0 && z <= 3;
    }
}
