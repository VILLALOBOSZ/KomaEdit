package com.komainu.moxia.feedback;

import com.komainu.moxia.gizmo.PerspectiveRaster;
import com.komainu.moxia.shape.Vec3;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Un rincón de mundo para los renders de la regla y las notas: hierba, un camino de piedra y una casa de ladrillo
 * con su puerta, ventanas y tejado plano, y dos troncos. Se pinta con las texturas del jar si {@code KOMAEDIT_MC_JAR}
 * lo indica.
 */
public final class RenderScene {
    private static final int[][] OFFSETS = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
    private final Map<Long, String> blocks = new HashMap<>();

    public static RenderScene village() {
        RenderScene scene = new RenderScene();
        for (int x = -16; x <= 24; x++) {
            for (int z = -16; z <= 24; z++) {
                scene.set(x, 62, z, "dirt");
                scene.set(x, 63, z, "grass_block");
            }
        }
        for (int z = -16; z <= 24; z++) {
            scene.set(-2, 63, z, "cobblestone");
            scene.set(-3, 63, z, "cobblestone");
        }
        // La casa: de (0, 64, 0) a (11, 68, 7), con puerta al oeste y ventanas.
        for (int x = 0; x <= 11; x++) {
            for (int z = 0; z <= 7; z++) {
                for (int y = 64; y <= 68; y++) {
                    boolean wall = x == 0 || x == 11 || z == 0 || z == 7;
                    if (wall) {
                        boolean corner = (x == 0 || x == 11) && (z == 0 || z == 7);
                        scene.set(x, y, z, corner ? "oak_log" : "stone_bricks");
                    }
                }
                scene.set(x, 69, z, "oak_planks");
            }
        }
        scene.clear(0, 64, 3);
        scene.clear(0, 65, 3);
        scene.clear(0, 64, 4);
        scene.clear(0, 65, 4);
        for (int x : new int[]{3, 4, 7, 8}) {
            scene.set(x, 66, 0, "glass");
        }
        // Dos troncos sueltos para medir entre ellos.
        for (int y = 64; y <= 70; y++) {
            scene.set(16, y, 2, "oak_log");
            scene.set(16, y, 12, "oak_log");
        }
        return scene;
    }

    public void set(int x, int y, int z, String block) {
        blocks.put(key(x, y, z), block);
    }

    public void clear(int x, int y, int z) {
        blocks.remove(key(x, y, z));
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) y & 0xFFF;
    }

    private boolean solid(int x, int y, int z) {
        String block = blocks.get(key(x, y, z));
        return block != null && !block.equals("glass");
    }

    /** Un raster con la cámara en {@code eye} mirando a {@code target}. */
    public static PerspectiveRaster camera(int width, int height, Vec3 eye, Vec3 target) throws IOException {
        Vec3 look = target.subtract(eye);
        double yaw = Math.toDegrees(Math.atan2(-look.x(), look.z()));
        double pitch = Math.toDegrees(-Math.asin(look.y() / look.length()));
        return new PerspectiveRaster(width, height, eye, yaw, pitch, 70.0);
    }

    public void draw(PerspectiveRaster raster) {
        for (Map.Entry<Long, String> entry : blocks.entrySet()) {
            long packed = entry.getKey();
            int x = (int) (packed >> 38);
            int z = (int) (packed << 26 >> 38);
            int y = (int) (packed << 52 >> 52);
            for (int face = 0; face < 6; face++) {
                int[] offset = OFFSETS[face];
                if (solid(x + offset[0], y + offset[1], z + offset[2])) {
                    continue;
                }
                raster.face(x, y, z, x + 1, y + 1, z + 1, face, texture(raster, entry.getValue(), face),
                        PerspectiveRaster.SHADE[face], 1.0, null);
            }
        }
    }

    private static int[] texture(PerspectiveRaster raster, String block, int face) {
        return switch (block) {
            case "grass_block" -> face == 3 ? raster.texture("grass_block_top", 0x7DB04E, 0x7DB860)
                    : face == 2 ? raster.texture("dirt", 0x866043, 0xFFFFFF)
                    : raster.texture("grass_block_side", 0x866043, 0xFFFFFF);
            case "oak_log" -> face == 2 || face == 3 ? raster.texture("oak_log_top", 0xB08A55, 0xFFFFFF)
                    : raster.texture("oak_log", 0x6B5131, 0xFFFFFF);
            case "glass" -> raster.texture("glass", 0xC8E4F0, 0xFFFFFF);
            case "stone_bricks" -> raster.texture("stone_bricks", 0x7A7A7A, 0xFFFFFF);
            case "oak_planks" -> raster.texture("oak_planks", 0xB8945F, 0xFFFFFF);
            case "cobblestone" -> raster.texture("cobblestone", 0x7F7F7F, 0xFFFFFF);
            case "dirt" -> raster.texture("dirt", 0x866043, 0xFFFFFF);
            default -> raster.texture("stone", 0x7D7D7D, 0xFFFFFF);
        };
    }
}
