package com.komainu.moxia.selection;

import com.komainu.moxia.gizmo.PerspectiveRaster;
import com.komainu.moxia.preview.PreviewOutline;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.studio.ui.StudioTheme;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders del lazo y del pincel de selección sobre un terreno con lomas, un estanque, flores y un árbol, con la misma
 * cámara que el lazo ({@link LassoCamera}) y el mismo contorno que dibuja el cliente ({@link SelectionOutline}).
 * Solo con {@code KOMAEDIT_LASSO_RENDER} (la carpeta de salida); con {@code KOMAEDIT_MC_JAR}, con las texturas del
 * juego.
 */
class LassoRenderTest {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 760;
    private static final double FOV = 70.0;
    private static final int RANGE = 48;
    private static final int BOTTOM = 50;
    private static final int WATER = 62;
    private static final int HIGHLIGHT = 0x4FB8FF;
    private static final int OUTLINE = StudioTheme.WORLD_SELECTION;
    private static final int ADD = 0x66BB6A;
    private static final int SUBTRACT = 0xEF5350;

    @Test
    void lassoAndBrushOnTerrain() throws IOException {
        String output = System.getenv("KOMAEDIT_LASSO_RENDER");
        Assumptions.assumeTrue(output != null && !output.isBlank(), "KOMAEDIT_LASSO_RENDER sin definir");
        Path folder = Path.of(output);
        Files.createDirectories(folder);
        Scene scene = Scene.build();
        List<String> report = new ArrayList<>();

        Vec3 eye = new Vec3(-2.5, 84.0, -34.5);
        double yaw = 0.0;
        double pitch = 32.0;
        LassoCamera camera = LassoCamera.looking(eye, yaw, pitch, FOV, WIDTH, HEIGHT);
        List<float[]> blob = blob(600, 430, 250, 150, 0.0);
        LassoPolygon lasso = LassoPolygon.of(blob);

        // 1. Dibujando el lazo.
        try (PerspectiveRaster raster = new PerspectiveRaster(WIDTH, HEIGHT, eye, yaw, pitch, FOV)) {
            world(raster, scene, null, Integer.MAX_VALUE);
            raster.actionBar("Arrastra: rodear con el lazo · Mayús: sumar · Ctrl: restar · Alt: intersecar");
            write(raster, folder.resolve("lazo_1_dibujando.png"), List.of(new Stroke(blob, 0xFFFFFF, false)));
        }

        // 2. Al soltar: la superficie que se ve dentro, con su contorno y su número de bloques.
        long start = System.nanoTime();
        LassoProjection projection = new LassoProjection(camera, lasso, LassoSettings.DEFAULT, scene, 256, 1_000_000);
        BlockSet surface = projection.run();
        double millis = (System.nanoTime() - start) / 1.0E6;
        assertFalse(surface.isEmpty());
        report.add(String.format(Locale.ROOT, "superficie: %d bloques, %d rayos, %d pasos, %.0f ms", surface.size(),
                projection.rays(), projection.steps(), millis));
        selection(folder.resolve("lazo_2_superficie.png"), scene, surface, eye, yaw, pitch, Integer.MAX_VALUE,
                "KE · Lazo: " + group(surface.size()) + " bloques seleccionados; la selección tiene "
                        + group(surface.size()), List.of());

        // 3 y 4. Profundidad, vista en un corte por la mitad de la selección.
        int[] box = surface.bounds();
        int cut = (box[0] + box[3]) / 2;
        Vec3 side = new Vec3(cut + 26.5, 74.0, (box[2] + box[5]) / 2.0 - 4.0);
        BlockSet layers = new LassoProjection(camera, lasso, new LassoSettings(LassoSettings.Depth.LAYERS, 4, false),
                scene, 256, 1_000_000).run();
        BlockSet through = new LassoProjection(camera, lasso,
                new LassoSettings(LassoSettings.Depth.THROUGH, 1, false), scene, 256, 1_000_000).run();
        report.add("cuatro capas: " + layers.size() + " bloques; hasta el fondo: " + through.size());
        selection(folder.resolve("lazo_3_cuatro_capas_corte.png"), scene, layers, side, 90.0, 16.0, cut,
                "KE · Lazo (4 capas): " + group(layers.size()) + " bloques · corte en x = " + cut, List.of());
        selection(folder.resolve("lazo_4_hasta_el_fondo_corte.png"), scene, through, side, 90.0, 16.0, cut,
                "KE · Lazo (hasta el fondo): " + group(through.size()) + " bloques · corte en x = " + cut, List.of());

        // 5. Sumar con Mayús y restar con Ctrl.
        List<float[]> plus = blob(930, 330, 150, 95, 1.7);
        List<float[]> minus = blob(560, 450, 120, 70, 3.1);
        BlockSet added = SelectionCombine.ADD.apply(surface, new LassoProjection(camera, LassoPolygon.of(plus),
                LassoSettings.DEFAULT, scene, 256, 1_000_000).run());
        BlockSet combined = SelectionCombine.SUBTRACT.apply(added, new LassoProjection(camera, LassoPolygon.of(minus),
                LassoSettings.DEFAULT, scene, 256, 1_000_000).run());
        report.add("sumar y restar: " + added.size() + " → " + combined.size());
        selection(folder.resolve("lazo_5_sumar_y_restar.png"), scene, combined, eye, yaw, pitch, Integer.MAX_VALUE,
                "KE · Lazo: la selección tiene " + group(combined.size()) + " bloques (Mayús sumó, Ctrl restó)",
                List.of(new Stroke(plus, ADD, true), new Stroke(minus, SUBTRACT, true)));

        // 6 y 7. Lo no sólido: el estanque y las flores.
        Vec3 pond = new Vec3(-17.5, 73.0, -16.5);
        LassoCamera close = LassoCamera.looking(pond, 20.0, 38.0, FOV, WIDTH, HEIGHT);
        List<float[]> around = blob(640, 420, 330, 210, 0.6);
        BlockSet solidOnly = new LassoProjection(close, LassoPolygon.of(around), LassoSettings.DEFAULT, scene, 256,
                1_000_000).run();
        BlockSet withPlants = new LassoProjection(close, LassoPolygon.of(around),
                new LassoSettings(LassoSettings.Depth.SURFACE, 1, true), scene, 256, 1_000_000).run();
        long water = withPlants.size() - count(withPlants, scene, false);
        report.add("sin lo no sólido: " + solidOnly.size() + "; con lo no sólido: " + withPlants.size() + " (" + water
                + " de agua y flores)");
        assertTrue(water > 0);
        selection(folder.resolve("lazo_6_sin_lo_no_solido.png"), scene, solidOnly, pond, 20.0, 38.0,
                Integer.MAX_VALUE, "KE · Lazo: " + group(solidOnly.size()) + " bloques · el fondo del estanque y la "
                        + "hierba bajo las flores", List.of(new Stroke(around, 0xFFFFFF, true)));
        selection(folder.resolve("lazo_7_con_lo_no_solido.png"), scene, withPlants, pond, 20.0, 38.0,
                Integer.MAX_VALUE, "KE · Lazo (con lo no sólido): " + group(withPlants.size()) + " bloques · el agua y "
                        + "las flores", List.of(new Stroke(around, 0xFFFFFF, true)));

        // 8. El pincel de selección: un trazo que suma y otro con Ctrl que borra por en medio.
        BlockSet painted = new BlockSet();
        for (int step = 0; step <= 40; step++) {
            int x = (int) Math.round(-14 + step * 0.7);
            int z = (int) Math.round(-6 + 5 * Math.sin(step * 0.18));
            painted.addAll(BrushShape.SPHERE.stamp(x, scene.height(x, z), z, 2, scene, false));
        }
        BlockSet erased = new BlockSet();
        for (int step = 0; step <= 12; step++) {
            int x = 0;
            int z = -12 + step;
            erased.addAll(BrushShape.CUBE.stamp(x, scene.height(x, z), z, 1, scene, false));
        }
        BlockSet brushed = SelectionCombine.SUBTRACT.apply(painted, erased);
        report.add("pincel: " + painted.size() + " pintados, " + (painted.size() - brushed.size()) + " borrados");
        selection(folder.resolve("pincel_8_pintar_y_borrar.png"), scene, brushed, eye, yaw, pitch, Integer.MAX_VALUE,
                "KE · Pincel: " + group(brushed.size()) + " bloques seleccionados · Ctrl borró por en medio",
                List.of());

        // 9 a 11. Ampliar, reducir e invertir por la superficie.
        BlockSet grown = SelectionMorphology.growSurface(surface, 3, BrushShape.SPHERE, scene, false, 1_000_000);
        BlockSet shrunk = SelectionMorphology.shrinkSurface(surface, 3, BrushShape.SPHERE, scene, false);
        BlockSet inverted = SelectionMorphology.invertSurface(surface, scene, false, 1_000_000);
        report.add("ampliar 3: " + grown.size() + "; reducir 3: " + shrunk.size() + "; invertir: " + inverted.size());
        selection(folder.resolve("superficie_9_ampliada_3.png"), scene, grown, eye, yaw, pitch, Integer.MAX_VALUE,
                "KE · Selección ampliada: " + group(grown.size()) + " bloques", List.of(new Stroke(blob, 0xFFFFFF,
                        true)));
        selection(folder.resolve("superficie_10_reducida_3.png"), scene, shrunk, eye, yaw, pitch, Integer.MAX_VALUE,
                "KE · Selección reducida: " + group(shrunk.size()) + " bloques", List.of(new Stroke(blob, 0xFFFFFF,
                        true)));
        selection(folder.resolve("superficie_11_invertida.png"), scene, inverted, eye, yaw, pitch, Integer.MAX_VALUE,
                "KE · Selección invertida: " + group(inverted.size()) + " bloques", List.of());

        report.add("Texturas de Minecraft: " + (System.getenv("KOMAEDIT_MC_JAR") == null ? "no" : "sí"));
        Files.writeString(folder.resolve("lazo.txt"), String.join("\n", report) + "\n");
    }

    // ── Escena ───────────────────────────────────────────────────────────────────────────────────────────

    /** El terreno de los renders: lomas de hierba, un estanque, flores y un roble. */
    static final class Scene implements Voxels {
        final Long2ObjectOpenHashMap<String> blocks = new Long2ObjectOpenHashMap<>();

        static Scene build() {
            Scene scene = new Scene();
            for (int x = -RANGE; x <= RANGE; x++) {
                for (int z = -RANGE; z <= RANGE; z++) {
                    int top = surfaceHeight(x, z);
                    for (int y = BOTTOM; y <= top; y++) {
                        String name = y == top ? (top < WATER ? "sand" : "grass_block") : y > top - 4 ? "dirt"
                                : "stone";
                        scene.blocks.put(BlockSet.pack(x, y, z), name);
                    }
                    for (int y = top + 1; y <= WATER; y++) {
                        scene.blocks.put(BlockSet.pack(x, y, z), "water");
                    }
                    long hash = (x * 73_856_093L ^ z * 19_349_663L) & 0x7FFFFFFF;
                    if (top >= WATER && hash % 9 == 0) {
                        scene.blocks.put(BlockSet.pack(x, top + 1, z), hash % 2 == 0 ? "poppy" : "dandelion");
                    }
                }
            }
            int baseX = 9;
            int baseZ = -2;
            int ground = surfaceHeight(baseX, baseZ);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 3; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (dx * dx + dy * dy * 1.6 + dz * dz <= 10.5) {
                            scene.blocks.put(BlockSet.pack(baseX + dx, ground + 6 + dy, baseZ + dz), "oak_leaves");
                        }
                    }
                }
            }
            for (int y = 1; y <= 6; y++) {
                scene.blocks.put(BlockSet.pack(baseX, ground + y, baseZ), "oak_log");
            }
            return scene;
        }

        static int surfaceHeight(int x, int z) {
            return 63 + (int) Math.round(3.2 * Math.sin(x * 0.16) + 2.6 * Math.cos(z * 0.21)
                    + 1.6 * Math.sin((x + z) * 0.11 + 1.3));
        }

        /** El bloque más alto que cuenta en la columna (sin el agua ni las flores). */
        int height(int x, int z) {
            for (int y = 90; y >= BOTTOM; y--) {
                if (Voxels.counts(kind(x, y, z), false)) {
                    return y;
                }
            }
            return BOTTOM;
        }

        String name(int x, int y, int z) {
            return blocks.get(BlockSet.pack(x, y, z));
        }

        @Override
        public int kind(int x, int y, int z) {
            if (Math.abs(x) > RANGE || Math.abs(z) > RANGE) {
                return UNLOADED;
            }
            String name = blocks.get(BlockSet.pack(x, y, z));
            if (name == null) {
                return AIR;
            }
            return plant(name) || name.equals("water") ? NON_SOLID : SOLID;
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 319;
        }

        static boolean plant(String name) {
            return name.equals("poppy") || name.equals("dandelion");
        }

        /** Lo que tapa del todo a su vecino: sin hojas, agua ni flores. */
        static boolean opaque(String name) {
            return name != null && !plant(name) && !name.equals("water") && !name.equals("oak_leaves");
        }
    }

    // ── Dibujo ───────────────────────────────────────────────────────────────────────────────────────────

    /** Un contorno de lazo para pintarlo encima: blanco y negro a tramos al dibujarse, o de un color al combinarse. */
    private record Stroke(List<float[]> points, int rgb, boolean closed) {
    }

    private static void selection(Path file, Scene scene, BlockSet selected, Vec3 eye, double yaw, double pitch,
                                  int cut, String bar, List<Stroke> strokes) throws IOException {
        try (PerspectiveRaster raster = new PerspectiveRaster(WIDTH, HEIGHT, eye, yaw, pitch, FOV)) {
            world(raster, scene, selected, cut);
            List<PreviewOutline.Section> sections = SelectionOutline.build(selected);
            if (sections != null) {
                for (PreviewOutline.Section section : sections) {
                    for (int edge : section.edges()) {
                        double x = section.x() * 16 + PreviewOutline.localX(edge);
                        double y = section.y() * 16 + PreviewOutline.localY(edge);
                        double z = section.z() * 16 + PreviewOutline.localZ(edge);
                        int axis = PreviewOutline.axis(edge);
                        int length = PreviewOutline.length(edge);
                        double endX = x + (axis == 0 ? length : 0);
                        if (cut != Integer.MAX_VALUE) {
                            // En el corte, las aristas no pasan de la cara cortada.
                            if (x > cut + 1) {
                                continue;
                            }
                            endX = Math.min(endX, cut + 1);
                        }
                        raster.depthLine(new Vec3(x, y, z), new Vec3(endX, y + (axis == 1 ? length : 0),
                                z + (axis == 2 ? length : 0)), OUTLINE, 1.6);
                    }
                }
            }
            int[] box = selected.bounds();
            if (box != null) {
                raster.label(group(selected.size()) + " bloques", new Vec3((box[0] + box[3] + 1) / 2.0,
                        box[4] + 1.6, (box[2] + box[5] + 1) / 2.0), OUTLINE & 0xFFFFFF);
            }
            raster.actionBar(bar);
            write(raster, file, strokes);
        }
    }

    /** El mundo con lo seleccionado teñido; {@code cut}: sin nada al este de esa x (para ver el corte). */
    private static void world(PerspectiveRaster raster, Scene scene, BlockSet selected, int cut) {
        List<long[]> waterFaces = new ArrayList<>();
        for (Long2ObjectMap.Entry<String> entry : scene.blocks.long2ObjectEntrySet()) {
            long packed = entry.getLongKey();
            int x = BlockSet.x(packed);
            int y = BlockSet.y(packed);
            int z = BlockSet.z(packed);
            if (x > cut) {
                continue;
            }
            String name = entry.getValue();
            boolean picked = selected != null && selected.containsPacked(packed);
            if (name.equals("water")) {
                if (!"water".equals(scene.name(x, y + 1, z))) {
                    waterFaces.add(new long[]{packed, picked ? 1 : 0});
                }
                continue;
            }
            if (Scene.plant(name)) {
                int[] texture = tint(raster.texture(name, name.equals("poppy") ? 0xD33A2E : 0xF2D33C, 0xFFFFFF),
                        picked);
                for (int face = 0; face < 6; face++) {
                    raster.face(x + 0.3, y, z + 0.3, x + 0.7, y + 0.55, z + 0.7, face, texture,
                            PerspectiveRaster.SHADE[face], 1.0, null);
                }
                continue;
            }
            int[] offsets = {-1, 1, 0, 0, 0, 0, 0, 0, -1, 1, 0, 0, 0, 0, 0, 0, -1, 1};
            for (int face = 0; face < 6; face++) {
                int nx = x + offsets[face];
                int ny = y + offsets[6 + face];
                int nz = z + offsets[12 + face];
                String neighbour = nx > cut ? null : scene.name(nx, ny, nz);
                if (Scene.opaque(name) && Scene.opaque(neighbour) || name.equals("oak_leaves")
                        && "oak_leaves".equals(neighbour)) {
                    continue;
                }
                raster.face(x, y, z, x + 1, y + 1, z + 1, face, tint(texture(raster, name, face), picked),
                        PerspectiveRaster.SHADE[face], 1.0, null);
            }
        }
        // El agua, translúcida, encima de lo de debajo (la capa del fantasma del rasterizador).
        double[] layer = raster.layer();
        for (double pass : new double[]{-1.0, 0.62}) {
            for (long[] water : waterFaces) {
                int x = BlockSet.x(water[0]);
                int y = BlockSet.y(water[0]);
                int z = BlockSet.z(water[0]);
                int[] texture = tint(raster.texture("water_still", 0x3F76E4, 0x3F76E4), water[1] == 1);
                raster.face(x, y, z, x + 1, y + 0.875, z + 1, 3, texture, 1.0, pass, layer);
            }
        }
    }

    private static int[] texture(PerspectiveRaster raster, String name, int face) {
        return switch (name) {
            case "grass_block" -> face == 3 ? raster.texture("grass_block_top", 0x7DB04E, 0x7DB860)
                    : face == 2 ? raster.texture("dirt", 0x866043, 0xFFFFFF)
                    : raster.texture("grass_block_side", 0x866043, 0xFFFFFF);
            case "oak_log" -> face == 2 || face == 3 ? raster.texture("oak_log_top", 0xB08A55, 0xFFFFFF)
                    : raster.texture("oak_log", 0x6B5131, 0xFFFFFF);
            case "oak_leaves" -> raster.texture("oak_leaves", 0x4C8F2A, 0x59AE30);
            case "dirt" -> raster.texture("dirt", 0x866043, 0xFFFFFF);
            case "sand" -> raster.texture("sand", 0xDBCFA3, 0xFFFFFF);
            default -> raster.texture("stone", 0x7D7D7D, 0xFFFFFF);
        };
    }

    /** Lo seleccionado, teñido del azul de la selección (sin tocar la transparencia de las hojas). */
    private static int[] tint(int[] texture, boolean picked) {
        if (!picked) {
            return texture;
        }
        int[] result = new int[texture.length];
        for (int index = 0; index < texture.length; index++) {
            int texel = texture[index];
            result[index] = texel & 0xFF000000 | PerspectiveRaster.lerp(texel & 0xFFFFFF, HIGHLIGHT, 0.5);
        }
        return result;
    }

    /** Escribe la imagen con los contornos del lazo encima, como los pinta la pantalla del Studio. */
    private static void write(PerspectiveRaster raster, Path file, List<Stroke> strokes) throws IOException {
        BufferedImage image = raster.image();
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Stroke stroke : strokes) {
            Path2D.Double path = new Path2D.Double();
            path.moveTo(stroke.points().getFirst()[0], stroke.points().getFirst()[1]);
            for (float[] point : stroke.points()) {
                path.lineTo(point[0], point[1]);
            }
            if (stroke.closed()) {
                path.closePath();
                graphics.setColor(new Color(stroke.rgb()));
                graphics.setStroke(new BasicStroke(2.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0F,
                        new float[]{8.0F, 5.0F}, 0.0F));
                graphics.draw(path);
            } else {
                // Como en el juego: hormigas blancas y negras de cuatro puntos y el cierre, más tenue.
                graphics.setStroke(new BasicStroke(2.0F));
                graphics.setColor(new Color(0x1A1A1A));
                graphics.draw(path);
                graphics.setColor(Color.WHITE);
                graphics.setStroke(new BasicStroke(2.0F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0F,
                        new float[]{8.0F, 8.0F}, 0.0F));
                graphics.draw(path);
                float[] first = stroke.points().getFirst();
                float[] last = stroke.points().getLast();
                graphics.setColor(new Color(255, 255, 255, 140));
                graphics.draw(new java.awt.geom.Line2D.Double(last[0], last[1], first[0], first[1]));
            }
        }
        graphics.dispose();
        ImageIO.write(image, "png", file.toFile());
    }

    /** Un contorno de lazo a mano alzada: una elipse con algo de temblor, como la dibuja el ratón. */
    private static List<float[]> blob(double centerX, double centerY, double radiusX, double radiusY, double seed) {
        List<float[]> points = new ArrayList<>();
        for (int index = 0; index < 160; index++) {
            double angle = Math.PI * 2 * index / 160;
            double wobble = 1.0 + 0.09 * Math.sin(angle * 3 + seed) + 0.05 * Math.sin(angle * 7 + seed * 2);
            points.add(new float[]{(float) (centerX + Math.cos(angle) * radiusX * wobble),
                    (float) (centerY + Math.sin(angle) * radiusY * wobble)});
        }
        return points;
    }

    /** Los bloques de la selección que son sólidos ({@code nonSolid} falso) o no. */
    private static long count(BlockSet set, Scene scene, boolean nonSolid) {
        long[] total = {0};
        set.forEach(block -> {
            boolean solid = scene.kind(BlockSet.x(block), BlockSet.y(block), BlockSet.z(block)) == Voxels.SOLID;
            if (solid != nonSolid) {
                total[0]++;
            }
        });
        return total[0];
    }

    private static String group(long value) {
        return ProgressLine.group(value);
    }
}
