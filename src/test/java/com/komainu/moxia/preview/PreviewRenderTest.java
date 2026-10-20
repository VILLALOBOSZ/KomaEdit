package com.komainu.moxia.preview;

import com.komainu.moxia.edit.PackedBlockPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders de antes y después de la vista previa en el mundo, fuera del juego. Solo con
 * {@code KOMAEDIT_PREVIEW_RENDER} (carpeta de salida); con {@code KOMAEDIT_MC_JAR}, con las texturas de Minecraft.
 *
 * <p>La escena es una obra de verdad a medias: un muro de piedra en L sobre hierba, al que la orden le abre una
 * puerta y una ventana (se quita), le cambia la última hilada por tablones (se sustituye) y le levanta al lado una
 * torre redonda (se pone). Los cambios pasan por el camino del juego: {@link PreviewSurface}, el plan troceado de
 * {@link PreviewPlanCodec} y el contorno de {@link PreviewOutline}.</p>
 */
class PreviewRenderTest {
    private static final int ADD = 0x5CFF7A;
    private static final int REPLACE = 0x7FD7FF;
    private static final int REMOVE = 0xFF4D4D;
    private static final int BOX = 0xFFD84A;
    /** El tinte leve de los bloques fantasma, el mismo que el cliente: verdoso lo nuevo, azulado lo sustituido. */
    private static final int GHOST_ADD = 0xD9FFD9;
    private static final int GHOST_REPLACE = 0xD9EBFF;
    private static final List<String> PALETTE = List.of("air", "grass_block", "dirt", "stone_bricks", "oak_planks",
            "cobblestone");

    private record Scene(Map<Long, String> world, Map<Long, String> changes) {
    }

    private static Scene scene() {
        Map<Long, String> world = new LinkedHashMap<>();
        for (int x = -2; x < 24; x++) {
            for (int z = -2; z < 24; z++) {
                world.put(PackedBlockPlan.pack(x, 0, z), "grass_block");
                world.put(PackedBlockPlan.pack(x, -1, z), "dirt");
            }
        }
        for (int y = 1; y <= 5; y++) {
            for (int z = 4; z < 20; z++) {
                world.put(PackedBlockPlan.pack(6, y, z), "stone_bricks");
            }
            for (int x = 7; x < 16; x++) {
                world.put(PackedBlockPlan.pack(x, y, 4), "stone_bricks");
            }
        }
        Map<Long, String> changes = new LinkedHashMap<>();
        // Puerta en el muro largo y ventana en el corto: se quitan.
        for (int y = 1; y <= 3; y++) {
            for (int z = 15; z <= 17; z++) {
                changes.put(PackedBlockPlan.pack(6, y, z), null);
            }
        }
        for (int y = 2; y <= 3; y++) {
            for (int x = 8; x <= 9; x++) {
                changes.put(PackedBlockPlan.pack(x, y, 4), null);
            }
        }
        // La última hilada del muro largo, de tablones: se sustituye.
        for (int z = 4; z < 20; z++) {
            changes.put(PackedBlockPlan.pack(6, 5, z), "oak_planks");
        }
        // Una torre redonda de piedra, hueca: se pone.
        for (int y = 1; y <= 8; y++) {
            for (int x = 10; x <= 18; x++) {
                for (int z = 10; z <= 18; z++) {
                    double distance = Math.hypot(x - 14, z - 14);
                    if (distance <= 4.2 && distance > 3.0) {
                        changes.put(PackedBlockPlan.pack(x, y, z), "cobblestone");
                    }
                }
            }
        }
        return new Scene(world, changes);
    }

    /** Lo que llega al cliente: el plan de la escena después de la superficie, el troceado y la lectura. */
    private static PreviewPlanCodec.Plan clientPlan(Scene scene, List<byte[]> partsOut) {
        long[] positions = scene.changes().keySet().stream().mapToLong(Long::longValue).toArray();
        PreviewSurface.Selection selection = PreviewSurface.select(positions, positions.length, 250_000);
        int count = selection.shown();
        long[] shown = new long[count];
        int[] states = new int[count];
        byte[] kinds = new byte[count];
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long packed : positions) {
            minX = Math.min(minX, PackedBlockPlan.unpackX(packed));
            minY = Math.min(minY, PackedBlockPlan.unpackY(packed));
            minZ = Math.min(minZ, PackedBlockPlan.unpackZ(packed));
            maxX = Math.max(maxX, PackedBlockPlan.unpackX(packed));
            maxY = Math.max(maxY, PackedBlockPlan.unpackY(packed));
            maxZ = Math.max(maxZ, PackedBlockPlan.unpackZ(packed));
        }
        long removed = 0;
        for (int slot = 0; slot < count; slot++) {
            long packed = positions[selection.indices()[slot]];
            shown[slot] = packed;
            String after = scene.changes().get(packed);
            String before = scene.world().get(packed);
            states[slot] = after == null ? 0 : PALETTE.indexOf(after);
            PreviewDisplays.Kind kind = after == null ? PreviewDisplays.Kind.REMOVE
                    : before == null ? PreviewDisplays.Kind.ADD : PreviewDisplays.Kind.REPLACE;
            kinds[slot] = (byte) kind.ordinal();
        }
        for (String value : scene.changes().values()) {
            removed += value == null ? 1 : 0;
        }
        PreviewPlanCodec.Header header = new PreviewPlanCodec.Header(1, minX, minY, minZ, maxX, maxY, maxZ,
                positions.length, positions.length - removed, removed, selection.mode(), count);
        List<byte[]> parts = PreviewPlanCodec.encode(header, shown, states, kinds, selection.faces(), 4_096);
        partsOut.addAll(parts);
        return PreviewPlanCodec.decode(parts);
    }

    @Test
    void beforeAndAfter() throws IOException {
        String output = System.getenv("KOMAEDIT_PREVIEW_RENDER");
        Assumptions.assumeTrue(output != null && !output.isBlank(), "KOMAEDIT_PREVIEW_RENDER sin definir");
        Path folder = Path.of(output);
        Files.createDirectories(folder);
        Scene scene = scene();
        List<byte[]> parts = new ArrayList<>();
        PreviewPlanCodec.Plan plan = clientPlan(scene, parts);
        List<PreviewOutline.Section> outline = PreviewOutline.build(plan.positions(), plan.kinds(), plan.faces());
        int edges = outline.stream().mapToInt(section -> section.edges().length).sum();
        assertTrue(plan.size() <= scene.changes().size(), "nunca más de lo que cambia");
        assertTrue(edges > 0);
        assertEquals(scene.changes().size(), plan.header().changes());

        try (SoftRaster raster = new SoftRaster(1100, 820, 27.0, 11.0, 3.0, 12.0)) {
            write(before(raster, scene), folder.resolve("vista_previa_antes.png"));
            write(after(raster, scene, plan, outline, false), folder.resolve("vista_previa_despues.png"));
            write(after(raster, scene, plan, outline, true), folder.resolve("vista_previa_solo_contorno.png"));
            int bytes = parts.stream().mapToInt(part -> part.length).sum();
            String report = String.format(Locale.ROOT, """
                    Escena: %d cambios (%d se ponen, %d se sustituyen, %d se quitan)
                    Antes: %d block displays (con la caja) enviados uno a uno, cada uno con su brillo
                    Después: %d entradas de superficie en %d parte(s) (%d bytes), %d aristas de contorno
                    Texturas de Minecraft: %s
                    """, scene.changes().size(), count(scene, PreviewDisplays.Kind.ADD),
                    count(scene, PreviewDisplays.Kind.REPLACE), count(scene, PreviewDisplays.Kind.REMOVE),
                    scene.changes().size() + 12, plan.size(), parts.size(), bytes, edges, raster.real() ? "sí" : "no");
            Files.writeString(folder.resolve("vista_previa.txt"), report);
        }
    }

    private static long count(Scene scene, PreviewDisplays.Kind kind) {
        return scene.changes().entrySet().stream().filter(entry -> kindOf(scene, entry.getKey()) == kind).count();
    }

    private static PreviewDisplays.Kind kindOf(Scene scene, long packed) {
        String after = scene.changes().get(packed);
        return after == null ? PreviewDisplays.Kind.REMOVE
                : scene.world().containsKey(packed) ? PreviewDisplays.Kind.REPLACE : PreviewDisplays.Kind.ADD;
    }

    // ---------------------------------------------------------------- antes: block displays con brillo

    private static BufferedImage before(SoftRaster raster, Scene scene) {
        raster.clear(0x9CC8F0, 0xDCEBF7);
        drawWorld(raster, scene.world());
        boolean[] green = raster.silhouette();
        boolean[] red = raster.silhouette();
        for (Map.Entry<Long, String> entry : scene.changes().entrySet()) {
            long packed = entry.getKey();
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            switch (kindOf(scene, packed)) {
                case ADD -> {
                    raster.cube(x, y, z, 0.82, textures(raster, entry.getValue()), 1.0, 0.0, null, false);
                    raster.mark(green, x, y, z, 0.82);
                }
                case REPLACE -> {
                    raster.cube(x, y, z, 1.004, textures(raster, entry.getValue()), 1.0, 0.0,
                            textures(raster, scene.world().get(packed)), true);
                    raster.mark(green, x, y, z, 1.004);
                }
                case REMOVE -> {
                    int[] glass = raster.texture("red_stained_glass", 0xB03030, 0xFFFFFF);
                    int[][] faces = {glass, glass, glass, glass, glass, glass};
                    raster.cube(x, y, z, 1.012, faces, raster.real() ? 1.0 : 0.5, 0.0, null, false);
                    raster.mark(red, x, y, z, 1.012);
                }
            }
        }
        raster.glowOutline(green, ADD);
        raster.glowOutline(red, REMOVE);
        box(raster, bounds(scene), BOX, 2.6, true);
        BufferedImage image = raster.image();
        Graphics2D graphics = image.createGraphics();
        SoftRaster.caption(graphics, "Antes: block displays opacos con brillo",
                "Lo nuevo tapa lo que hay detrás y el brillo se ve a través de los muros.\n"
                        + "La hilada que se sustituye parpadea (z-fighting: el display mide 1,004).\n"
                        + "Un display por bloque, sin medidas.");
        graphics.dispose();
        return image;
    }

    // ---------------------------------------------------------------- después: dibujado en el cliente

    private record Face(double depth, double x, double y, double z, int face, int[] texture, double alpha) {
    }

    private static BufferedImage after(SoftRaster raster, Scene scene, PreviewPlanCodec.Plan plan,
                                       List<PreviewOutline.Section> outline, boolean outlineOnly) {
        raster.clear(0x9CC8F0, 0xDCEBF7);
        drawWorld(raster, scene.world());
        double opacity = 0.5;
        if (!outlineOnly) {
            List<double[]> ghost = new ArrayList<>();
            List<int[]> ghostTextures = new ArrayList<>();
            List<Integer> ghostTints = new ArrayList<>();
            List<Face> removed = new ArrayList<>();
            int[] red = flat(REMOVE);
            for (int index = 0; index < plan.size(); index++) {
                long packed = plan.positions()[index];
                int x = PackedBlockPlan.unpackX(packed);
                int y = PackedBlockPlan.unpackY(packed);
                int z = PackedBlockPlan.unpackZ(packed);
                PreviewDisplays.Kind kind = plan.kind(index);
                int[][] textures = kind == PreviewDisplays.Kind.REMOVE ? null
                        : textures(raster, PALETTE.get(plan.states()[index]));
                for (int face = 0; face < 6; face++) {
                    if ((plan.faces()[index] & 1 << face) == 0) {
                        continue;
                    }
                    if (textures == null) {
                        removed.add(new Face(0, x, y, z, face, red, opacity * 0.9));
                    } else {
                        ghost.add(new double[]{x, y, z, face});
                        ghostTextures.add(textures[face]);
                        ghostTints.add(kind == PreviewDisplays.Kind.ADD ? GHOST_ADD : GHOST_REPLACE);
                    }
                }
            }
            // Una sola capa: profundidad primero, color después; y encima, el rojo de lo que se quita.
            raster.singleLayer(ghost, ghostTextures, ghostTints, opacity, 0.02);
            for (Face face : removed) {
                raster.face(face.x(), face.y(), face.z(), face.x() + 1, face.y() + 1, face.z() + 1, face.face(),
                        face.texture(), SoftRaster.SHADE[face.face()], face.alpha(), 0.02, null, false);
            }
        }
        for (PreviewOutline.Section section : outline) {
            for (int edge : section.edges()) {
                double x = section.x() * 16 + PreviewOutline.localX(edge);
                double y = section.y() * 16 + PreviewOutline.localY(edge);
                double z = section.z() * 16 + PreviewOutline.localZ(edge);
                int axis = PreviewOutline.axis(edge);
                int length = PreviewOutline.length(edge);
                int kind = PreviewOutline.kind(edge);
                int rgb = kind == PreviewDisplays.Kind.REMOVE.ordinal() ? REMOVE
                        : kind == PreviewDisplays.Kind.ADD.ordinal() ? ADD : REPLACE;
                double[] from = {x, y, z};
                double[] to = {x + (axis == 0 ? length : 0), y + (axis == 1 ? length : 0),
                        z + (axis == 2 ? length : 0)};
                raster.line(from, to, 0xF2000000 | rgb, 2.4, false, 0.06);
            }
        }
        int[] bounds = bounds(scene);
        box(raster, bounds, BOX, 1.8, false);
        BufferedImage image = raster.image();
        Graphics2D graphics = image.createGraphics();
        Color yellow = new Color(BOX);
        // Las medidas en las aristas más cercanas a la cámara, como WorldLabels.dimensions.
        double x0 = bounds[0];
        double y0 = bounds[1];
        double z0 = bounds[2];
        double x1 = bounds[3] + 1.0;
        double y1 = bounds[4] + 1.0;
        double z1 = bounds[5] + 1.0;
        SoftRaster.label(graphics, raster, new double[]{(x0 + x1) / 2, y1, z1}, Integer.toString((int) (x1 - x0)),
                yellow, 15);
        SoftRaster.label(graphics, raster, new double[]{x1, (y0 + y1) / 2, z1}, Integer.toString((int) (y1 - y0)),
                yellow, 15);
        SoftRaster.label(graphics, raster, new double[]{x1, y1, (z0 + z1) / 2}, Integer.toString((int) (z1 - z0)),
                yellow, 15);
        SoftRaster.caption(graphics, outlineOnly ? "Después: solo contorno" : "Después: bloques translúcidos y contorno",
                outlineOnly
                        ? "El interruptor de los ajustes: nada tapa el mundo, solo las aristas.\n"
                        + "Verde lo que se pone, azul claro lo que sustituye, rojo lo que se quita."
                        : "Lo nuevo con su textura al 50 % (una sola capa), lo que se quita de rojo.\n"
                        + "Contorno de la figura sin la rejilla de cada bloque; caja con medidas.\n"
                        + "Sin parpadeo: lo que coincide con el mundo se adelanta con polygon offset.");
        graphics.dispose();
        return image;
    }

    // ---------------------------------------------------------------- piezas

    private static void drawWorld(SoftRaster raster, Map<Long, String> world) {
        for (Map.Entry<Long, String> entry : world.entrySet()) {
            long packed = entry.getKey();
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            int[][] textures = textures(raster, entry.getValue());
            for (int face = 0; face < 6; face++) {
                long neighbour = PackedBlockPlan.pack(x + PreviewSurface.OFFSET_X[face],
                        y + PreviewSurface.OFFSET_Y[face], z + PreviewSurface.OFFSET_Z[face]);
                if (world.containsKey(neighbour)) {
                    continue;
                }
                raster.face(x, y, z, x + 1, y + 1, z + 1, face, textures[face], SoftRaster.SHADE[face], 1.0, 0.0,
                        null, false);
            }
        }
    }

    /** Texturas de las seis caras (oeste, este, abajo, arriba, norte, sur). */
    private static int[][] textures(SoftRaster raster, String block) {
        return switch (block) {
            case "grass_block" -> {
                int[] side = raster.texture("grass_block_side", 0x7A9A4A, 0xFFFFFF);
                int[] top = raster.texture("grass_block_top", 0x91BD59, raster.real() ? 0x91BD59 : 0xFFFFFF);
                int[] bottom = raster.texture("dirt", 0x8B5E3C, 0xFFFFFF);
                yield new int[][]{side, side, bottom, top, side, side};
            }
            case "dirt" -> all(raster.texture("dirt", 0x8B5E3C, 0xFFFFFF));
            case "stone_bricks" -> all(raster.texture("stone_bricks", 0x7D7D7D, 0xFFFFFF));
            case "oak_planks" -> all(raster.texture("oak_planks", 0xA2834F, 0xFFFFFF));
            case "cobblestone" -> all(raster.texture("cobblestone", 0x7A7A7A, 0xFFFFFF));
            default -> all(flat(0xFF00FF));
        };
    }

    private static int[][] all(int[] texture) {
        return new int[][]{texture, texture, texture, texture, texture, texture};
    }

    private static int[] flat(int rgb) {
        int[] pixels = new int[256];
        java.util.Arrays.fill(pixels, 0xFF000000 | rgb);
        return pixels;
    }

    private static int[] bounds(Scene scene) {
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (long packed : scene.changes().keySet()) {
            int[] point = {PackedBlockPlan.unpackX(packed), PackedBlockPlan.unpackY(packed),
                    PackedBlockPlan.unpackZ(packed)};
            for (int axis = 0; axis < 3; axis++) {
                bounds[axis] = Math.min(bounds[axis], point[axis]);
                bounds[axis + 3] = Math.max(bounds[axis + 3], point[axis]);
            }
        }
        return bounds;
    }

    private static void box(SoftRaster raster, int[] bounds, int rgb, double thickness, boolean throughWalls) {
        double x0 = bounds[0] - 0.02;
        double y0 = bounds[1] - 0.02;
        double z0 = bounds[2] - 0.02;
        double x1 = bounds[3] + 1.02;
        double y1 = bounds[4] + 1.02;
        double z1 = bounds[5] + 1.02;
        double[][] corners = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0},
                {x1, y1, z1}, {x0, y1, z1}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6},
                {3, 7}};
        for (int[] edge : edges) {
            raster.line(corners[edge[0]], corners[edge[1]], 0xF0000000 | rgb, thickness, throughWalls, 0.06);
        }
    }

    private static void write(BufferedImage image, Path path) throws IOException {
        ImageIO.write(image, "png", path.toFile());
    }
}
