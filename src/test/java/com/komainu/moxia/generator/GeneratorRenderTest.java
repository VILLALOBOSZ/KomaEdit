package com.komainu.moxia.generator;

import com.komainu.moxia.edit.PackedBlockPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders isométricos de comprobación fuera del juego. Solo se ejecuta con {@code KOMAEDIT_GENERATOR_RENDER=<carpeta>}:
 * deja allí un PNG por forma, terrenos de noisegen y campos de flujo (en planta y en isométrico).
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_GENERATOR_RENDER", matches = ".+")
class GeneratorRenderTest {
    private static final Color[] PALETTE = {
            new Color(0x3A6EA5), new Color(0xD9C38A), new Color(0x5E9E3A), new Color(0x3F7A2A),
            new Color(0x7A7A7A), new Color(0x9A9A9A), new Color(0xF2F2F2), new Color(0xC0504D)
    };

    @Test
    void rendersEverything() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_GENERATOR_RENDER"));
        Files.createDirectories(folder);
        int[] origin = {0, 0, 0};
        double[] look = {0.6, -0.3, 0.74};
        String[][] shapes = {
                {"cone", "stone 6,10 12 30"}, {"cone", "stone 6 10 -h"}, {"cone", "stone 5 9 -d"},
                {"polydome", "stone 8 12 1 -v gold_block"}, {"polydome", "stone 6 10 0.6 -v gold_block"},
                {"polygon", "stone 6 10 3 up"}, {"polygon", "stone 5 8 1 northeast"},
                {"polygon", "stone 3 9 2 upeast"},
                {"square", "stone 6 5"}, {"square", "stone 6 5 -w"}, {"square", "stone 6 5 -f"},
                {"tetrahedron", "stone 10 20"}, {"tetrahedron", "stone 10 0 -h"},
                {"torus", "stone 10 3 circle"}, {"torus", "stone 10 3 diamond"},
                {"torus", "stone 10 3 rounded_square -h"}, {"torus", "stone 10 3 square -o"}
        };
        StringBuilder timings = new StringBuilder();
        for (String[] shape : shapes) {
            ShapeRequest request = ShapeRequest.parse(ShapeRequest.Form.byId(shape[0]), shape[1]);
            long start = System.nanoTime();
            GeneratedBlocks blocks = ShapeVoxelizer.generate(request, origin, look, 37.0, 5_000_000);
            timings.append(shape[0]).append(' ').append(shape[1]).append(" -> ").append(blocks.size())
                    .append(" bloques, ").append((System.nanoTime() - start) / 1_000_000).append(" ms\n");
            String name = "forma_" + shape[0] + "_" + shape[1].replaceAll("[^a-z0-9]+", "_");
            write(folder, name, iso(blocks, shape[0] + " " + shape[1] + " · " + blocks.size() + " bloques",
                    request.hollow() && request.form() != ShapeRequest.Form.POLYDOME));
        }

        PaletteTraits terrainPalette = PaletteTraits.of(List.of("water", "sand", "grass_block", "moss_block",
                "stone", "andesite", "snow"));
        Volume volume = Volume.box(0, 0, 0, 79, 31, 79);
        String[][] noises = {
                {"heightmap", "x Perlin(F:0.03,fT:F,fO:4) 0 -t"},
                {"heightmap", "x Si(F:0.02,fT:R,fO:5) 24"},
                {"terrain", "x Perlin(F:0.05,fT:F,fO:3) 0 1,0.5,0"},
                {"terrain", "x Si(F:0.04) 0 0,1,0 -l 2"},
                {"advanced", "x Perlin(F:0.06) 0.55 1"},
                {"advanced", "x Ce(F:0.05) 0 \"0.3+0.2*t\" -h -t"}
        };
        for (String[] noise : noises) {
            NoiseGenerator.Mode mode = NoiseGenerator.Mode.valueOf(noise[0].toUpperCase());
            GeneratorRequests.NoiseRequest request = GeneratorRequests.noise(mode, noise[1], () -> 7L);
            GeneratedBlocks blocks = new GeneratedBlocks(5_000_000);
            long start = System.nanoTime();
            NoiseGenerator.generate(request.settings(), volume, null, terrainPalette, -64, 319, blocks);
            timings.append("noisegen ").append(noise[0]).append(' ').append(noise[1]).append(" -> ")
                    .append(blocks.size()).append(" bloques, ").append((System.nanoTime() - start) / 1_000_000)
                    .append(" ms\n");
            String name = "noisegen_" + noise[0] + "_" + noise[1].replaceAll("[^a-zA-Z0-9]+", "_");
            write(folder, name, iso(blocks, noise[0] + " " + noise[1] + " · " + blocks.size() + " bloques", false));
        }

        Volume flat = Volume.box(0, 0, 0, 119, 0, 119);
        String[] flows = {
                "x 600 64 1 0.5 Perlin(F:0.02)",
                "x 400 80 1 0.34 Perlin(F:0.015) -c",
                "x 300 48 1 1 Si(F:0.03) -i 0.8 -p 1:0",
                "x 50% 24 1 0.5 Perlin(F:0.02) -f",
                "x 300 64 1 0.5 Perlin(F:0.02) -x *2 -g 0.05,0,0"
        };
        for (String flow : flows) {
            GeneratorRequests.FlowFieldRequest request = GeneratorRequests.flowField(flow, () -> 3L);
            GeneratedBlocks blocks = new GeneratedBlocks(5_000_000);
            long start = System.nanoTime();
            FlowField.generate(request.settings(), flat, null, 7, blocks);
            timings.append("flowfield ").append(flow).append(" -> ").append(blocks.size()).append(" bloques, ")
                    .append((System.nanoTime() - start) / 1_000_000).append(" ms\n");
            write(folder, "flujo_" + flow.replaceAll("[^a-zA-Z0-9]+", "_"), plan(blocks, flat, flow));
        }
        Volume cube = Volume.box(0, 0, 0, 47, 47, 47);
        GeneratorRequests.FlowFieldRequest threeD = GeneratorRequests.flowField(
                "x 120 96 1 1 Perlin(F:0.03) -t -c", () -> 5L);
        GeneratedBlocks flow3d = new GeneratedBlocks(5_000_000);
        FlowField.generate(threeD.settings(), cube, null, 7, flow3d);
        write(folder, "flujo_3d", iso(flow3d, "flowfield 3D con rotacional · " + flow3d.size() + " bloques", false));

        // Flowfield sobre un terreno de colinas (modo mapa de alturas).
        GeneratedBlocks hills = new GeneratedBlocks(5_000_000);
        NoiseGenerator.generate(GeneratorRequests.noise(NoiseGenerator.Mode.HEIGHTMAP,
                        "x Perlin(F:0.03) 12", () -> 9L).settings(), Volume.box(0, 0, 0, 63, 15, 63), null,
                PaletteTraits.of(List.of("stone")), -64, 319, hills);
        Map<Long, Integer> tops = new HashMap<>();
        for (int index = 0; index < hills.size(); index++) {
            tops.merge((long) hills.x(index) << 32 | (hills.z(index) & 0xFFFFFFFFL), hills.y(index), Math::max);
        }
        FlowField.Terrain terrain = new FlowField.Terrain() {
            @Override
            public int surface(int x, int z) {
                Integer top = tops.get((long) x << 32 | (z & 0xFFFFFFFFL));
                return top == null ? NONE : top;
            }

            @Override
            public boolean source(int x, int y, int z) {
                return true;
            }
        };
        GeneratedBlocks surfaceFlow = new GeneratedBlocks(5_000_000);
        FlowField.generate(GeneratorRequests.flowField("x 200 48 1 0.5 Perlin(F:0.03) -h", () -> 2L).settings(),
                Volume.box(0, 0, 0, 63, 15, 63), terrain, 7, surfaceFlow);
        GeneratedBlocks merged = new GeneratedBlocks(5_000_000);
        Map<Long, Integer> painted = new HashMap<>();
        for (int index = 0; index < surfaceFlow.size(); index++) {
            painted.put(surfaceFlow.packed(index), surfaceFlow.material(index) + 1);
        }
        for (int index = 0; index < hills.size(); index++) {
            Integer material = painted.get(hills.packed(index));
            merged.add(hills.x(index), hills.y(index), hills.z(index), material == null ? 4 : Math.min(7,
                    material + 4));
        }
        write(folder, "flujo_mapa_alturas", iso(merged, "flowfield -h sobre colinas", false));

        // Línea de flujo sobre un suelo plano.
        FlowLine.Result line = FlowLine.trace(GeneratorRequests.flowLine("stone 200 -1 Perlin(F:0.05) -l 5 -s",
                () -> 4L).settings(), new double[]{0.5, 6.5, 0.5}, new double[]{1, -0.2, 0.3}, (x, y, z) -> y <= 0);
        GeneratedBlocks lineBlocks = new GeneratedBlocks(5_000_000);
        line.blocks().forEach((long packed) -> lineBlocks.add(PackedBlockPlan.unpackX(packed),
                PackedBlockPlan.unpackY(packed), PackedBlockPlan.unpackZ(packed), 7));
        write(folder, "linea_flujo", iso(lineBlocks, "flowline 200 -l 5 -s · " + lineBlocks.size() + " bloques",
                false));
        Files.writeString(folder.resolve("tiempos.txt"), timings);
    }

    private static void write(Path folder, String name, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
    }

    private record Face(Polygon polygon, Color color, double depth) {
    }

    /** Isométrico con las tres caras visibles de cada bloque; las ocultas por un vecino no se dibujan. */
    private static BufferedImage iso(GeneratedBlocks blocks, String title, boolean cutaway) {
        Map<Long, Integer> cells = new HashMap<>();
        for (int index = 0; index < blocks.size(); index++) {
            cells.put(blocks.packed(index), blocks.material(index));
        }
        int[] bounds = blocks.bounds();
        if (bounds == null) {
            bounds = new int[]{0, 0, 0, 1, 1, 1};
        }
        double scale = Math.clamp(640.0 / Math.max(8, (bounds[3] - bounds[0]) + (bounds[5] - bounds[2])), 2.0, 18.0);
        List<Face> faces = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : cells.entrySet()) {
            long key = entry.getKey();
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            if (cutaway && z > (bounds[2] + bounds[5]) / 2) {
                continue;
            }
            Color base = PALETTE[Math.min(PALETTE.length - 1, entry.getValue())];
            double depth = x + y + z;
            if (!cells.containsKey(PackedBlockPlan.pack(x, y + 1, z))) {
                faces.add(new Face(quad(scale, x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1),
                        base, depth + 0.3));
            }
            if (!cells.containsKey(PackedBlockPlan.pack(x + 1, y, z))) {
                faces.add(new Face(quad(scale, x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x + 1, y, z + 1),
                        base.darker(), depth + 0.2));
            }
            if (!cells.containsKey(PackedBlockPlan.pack(x, y, z + 1))) {
                faces.add(new Face(quad(scale, x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1),
                        base.darker().darker(), depth + 0.2));
            }
        }
        faces.sort(Comparator.comparingDouble(Face::depth));
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Face face : faces) {
            var box = face.polygon().getBounds();
            minX = Math.min(minX, box.x);
            minY = Math.min(minY, box.y);
            maxX = Math.max(maxX, box.x + box.width);
            maxY = Math.max(maxY, box.y + box.height);
        }
        if (faces.isEmpty()) {
            minX = minY = 0;
            maxX = maxY = 10;
        }
        BufferedImage image = new BufferedImage(maxX - minX + 40, maxY - minY + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(0xE8EEF2));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.translate(20 - minX, 40 - minY);
        for (Face face : faces) {
            graphics.setColor(face.color());
            graphics.fillPolygon(face.polygon());
            graphics.setColor(face.color().darker());
            graphics.drawPolygon(face.polygon());
        }
        graphics.translate(minX - 20, minY - 40);
        graphics.setColor(Color.BLACK);
        graphics.drawString(title, 8, 16);
        graphics.dispose();
        return image;
    }

    private static Polygon quad(double scale, double... corners) {
        Polygon polygon = new Polygon();
        for (int index = 0; index < corners.length; index += 3) {
            double x = corners[index];
            double y = corners[index + 1];
            double z = corners[index + 2];
            polygon.addPoint((int) Math.round((x - z) * scale * 0.866), (int) Math.round(((x + z) * 0.5 - y) * scale));
        }
        return polygon;
    }

    /** Planta: un píxel por bloque, coloreado por la entrada de la paleta. */
    private static BufferedImage plan(GeneratedBlocks blocks, Volume volume, String title) {
        int scale = 5;
        BufferedImage image = new BufferedImage(volume.sizeX() * scale, volume.sizeZ() * scale + 24,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x20242A));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        Color[] ramp = {new Color(0x2B4C7E), new Color(0x3F7CAC), new Color(0x6FB3D2), new Color(0xA7D7C5),
                new Color(0xF4E285), new Color(0xF4A259), new Color(0xE9724C)};
        for (int index = 0; index < blocks.size(); index++) {
            graphics.setColor(ramp[Math.min(ramp.length - 1, blocks.material(index))]);
            graphics.fillRect((blocks.x(index) - volume.minimumX()) * scale,
                    (blocks.z(index) - volume.minimumZ()) * scale + 24, scale, scale);
        }
        graphics.setColor(Color.WHITE);
        graphics.drawString(title + " · " + blocks.size() + " bloques", 6, 16);
        graphics.dispose();
        return image;
    }
}
