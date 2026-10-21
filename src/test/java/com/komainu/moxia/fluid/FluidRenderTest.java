package com.komainu.moxia.fluid;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.preview.SoftRaster;
import com.komainu.moxia.utility.TestWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders de comprobación, fuera del juego, de las herramientas de agua: un lago rellenado hasta el borde, el mismo
 * lago con un canal en la orilla (la fuga señalada), una cascada con su poza y un río corto por una ladera. Solo con
 * {@code KOMAEDIT_FLUID_RENDER=<carpeta>}; con {@code KOMAEDIT_MC_JAR} lleva las texturas del juego.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_FLUID_RENDER", matches = ".+")
class FluidRenderTest {
    private static final FlowSim.Rules WATER = FlowSim.Rules.of(FluidKind.WATER, false);
    private static final int[] OFFSET_X = {-1, 1, 0, 0, 0, 0};
    private static final int[] OFFSET_Y = {0, 0, -1, 1, 0, 0};
    private static final int[] OFFSET_Z = {0, 0, 0, 0, -1, 1};

    @Test
    void rendersLakeWaterfallAndRiver() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_FLUID_RENDER"));
        Files.createDirectories(folder);

        // Lago: un cuenco en la ladera, clic en el fondo, «hasta el borde».
        TestWorld lake = lake();
        int[] bottom = lowest(lake, 24, 24, 6);
        BufferedImage lakeBefore = render(lake, "Antes: un cuenco en la ladera", "Clic en el fondo, en "
                + bottom[0] + ", " + bottom[1] + ", " + bottom[2], new long[0], new long[0]);
        FloodFill.Result filled = FloodFill.plan(lake.snapshot(), lake.kinds(), bottom[0], bottom[1], bottom[2],
                new FloodFill.Settings(FluidKind.WATER, FloodFill.Level.BRIM, FloodFill.Directions.DEFAULT, 50_000,
                        false, -64), FluidScenes.NOTHING);
        assertTrue(filled.closed());
        TestWorld lakeAfter = copy(lake);
        FluidScenes.apply(lakeAfter, FluidScenes.writes(filled, FluidKind.WATER));
        BufferedImage lakeFilled = render(lakeAfter, "fluido rellenar agua borde", "Cierra hasta y=" + filled.level()
                + ": " + filled.filled() + " fuentes, ninguna fuera del cuenco", new long[0], new long[0]);
        write(side(lakeBefore, lakeFilled), folder.resolve("lago.png"));

        // El mismo lago con un canal abierto en la orilla: no se llena y señala por dónde se va.
        TestWorld leaky = copy(lake);
        int[] notch = notch(leaky, filled.level());
        FloodFill.Result leak = FloodFill.plan(leaky.snapshot(), leaky.kinds(), bottom[0], bottom[1], bottom[2],
                new FloodFill.Settings(FluidKind.WATER, FloodFill.Level.fixed(filled.level()),
                        FloodFill.Directions.DEFAULT, 50_000, false, -64), FluidScenes.NOTHING);
        assertFalse(leak.closed());
        FloodFill.Leak found = leak.leak();
        String hole = FluidService.coordinates(found.hole().length > 0 ? found.hole()[0] : found.exit());
        write(render(leaky, "Con un canal en la orilla: no pone nada", "Se escaparía por " + hole + " (el canal abierto"
                + " en " + notch[0] + ", " + notch[1] + ", " + notch[2] + ")\nRojo: el hueco · naranja: el camino del"
                + " agua · cierra hasta y=" + found.highestClosed(), found.hole(), found.path()),
                folder.resolve("lago_fuga.png"));

        // Cascada: clic en el borde del acantilado.
        TestWorld cliff = cliff();
        int[] edge = edge(cliff, 15);
        BufferedImage cliffBefore = render(cliff, "Antes: el acantilado", "Clic en el borde, en " + edge[0] + ", "
                + edge[1] + ", " + edge[2], new long[]{PackedBlockPlan.pack(edge[0], edge[1], edge[2])}, new long[0]);
        Cascade.Result fall = Cascade.fromPoint(cliff.snapshot(), cliff.kinds(), edge[0], edge[1], edge[2],
                new Cascade.Settings(FluidKind.WATER, true, 3, 2, 1, true), WATER, 20_000, FluidScenes.NOTHING);
        assertNull(fall.flood());
        TestWorld cliffAfter = copy(cliff);
        FluidScenes.apply(cliffAfter, fall.writes());
        write(side(cliffBefore, render(cliffAfter, "fluido cascada agua -o 3", "Caída de " + fall.fall() + " bloques, poza de "
                + fall.pools() + " · " + fall.flows() + " corrientes, ninguna fuera de la caída", new long[0],
                new long[0])), folder.resolve("cascada.png"));

        // Río corto por una ladera con escalones, de tres bloques de ancho.
        TestWorld hill = hillside();
        List<int[]> points = List.of(top(hill, 2, 10), top(hill, 20, 15), top(hill, 38, 10));
        Cascade.Result river = Cascade.alongPath(hill.snapshot(), hill.kinds(), points,
                new Cascade.Settings(FluidKind.WATER, true, 2, 2, 3, true), WATER, 20_000, FluidScenes.NOTHING);
        assertNull(river.flood());
        TestWorld riverAfter = copy(hill);
        FluidScenes.apply(riverAfter, river.writes());
        long[] marks = new long[points.size()];
        for (int index = 0; index < points.size(); index++) {
            marks[index] = PackedBlockPlan.pack(points.get(index)[0], points.get(index)[1] + 1, points.get(index)[2]);
        }
        write(render(riverAfter, "fluido cascada agua -a 3 (tres puntos, en amarillo)", "Río corto: "
                + river.sources() + " fuentes, " + river.flows() + " corrientes en los saltos, " + river.banks()
                + " de orilla, " + river.cuts() + " cortados\nEl agua nunca sube; en cada escalón salta al tramo de"
                + " abajo", marks, new long[0], 0xFDD835), folder.resolve("rio.png"));
    }

    // ---------------------------------------------------------------- escenas

    private static TestWorld lake() {
        TestWorld world = new TestWorld(0, 0, 0, 49, 26, 49);
        for (int x = 0; x <= 48; x++) {
            for (int z = 0; z <= 48; z++) {
                double dx = x - 24;
                double dz = z - 24;
                double distance = Math.sqrt(dx * dx + dz * dz);
                double bowl = -8.0 * Math.exp(-distance * distance / (2 * 8.0 * 8.0));
                double rim = 2.5 * Math.exp(-Math.pow(distance - 14, 2) / (2 * 4.0 * 4.0));
                double noise = 1.1 * Math.sin(x * 0.31 + z * 0.17) + 0.7 * Math.cos(x * 0.13 - z * 0.29);
                int top = (int) Math.round(13 + bowl + rim + noise - 0.08 * (x - 24));
                column(world, x, z, top, top <= 12 && distance < 16 ? "sand" : "grass_block[snowy=false]");
            }
        }
        return world;
    }

    /** Abre un canal de un bloque desde el centro hacia el este hasta salir del cuenco, a la altura del agua. */
    private static int[] notch(TestWorld world, int level) {
        int[] first = null;
        for (int x = 24; x <= 48; x++) {
            int top = surface(world, x, 24);
            if (top >= level) {
                for (int y = level; y <= top; y++) {
                    world.set(x, y, 24, TestWorld.AIR);
                }
                if (first == null) {
                    first = new int[]{x, level, 24};
                }
            } else if (first != null) {
                break;
            }
        }
        return first;
    }

    private static TestWorld cliff() {
        TestWorld world = new TestWorld(0, 0, 0, 41, 30, 31);
        for (int x = 0; x <= 40; x++) {
            for (int z = 0; z <= 30; z++) {
                int face = 16 + (int) Math.round(1.5 * Math.sin(z * 0.4));
                double noise = Math.sin(x * 0.4 + z * 0.3) + 0.6 * Math.cos(z * 0.5 - x * 0.2);
                int top = x <= face ? 22 + (int) Math.round(0.6 * noise) : 6 + (int) Math.round(noise * 0.3);
                column(world, x, z, top, "grass_block[snowy=false]");
                if (x <= face && x >= face - 1) {
                    for (int y = 7; y < top; y++) {
                        world.set(x, y, z, "stone");
                    }
                }
            }
        }
        return world;
    }

    private static TestWorld hillside() {
        TestWorld world = new TestWorld(0, 0, 0, 41, 30, 27);
        for (int x = 0; x <= 40; x++) {
            for (int z = 0; z <= 26; z++) {
                double noise = 0.35 * Math.sin(x * 0.35 + z * 0.45) + 0.25 * Math.cos(z * 0.6 + x * 0.1);
                int step = x / 5;
                int top = (int) Math.round(22 - step * 1.6 + noise + 0.18 * Math.abs(z - 13));
                column(world, x, z, top, "grass_block[snowy=false]");
            }
        }
        return world;
    }

    private static void column(TestWorld world, int x, int z, int top, String surface) {
        for (int y = 0; y <= top; y++) {
            world.set(x, y, z, y == top ? surface : y >= top - 3 ? "dirt" : "stone");
        }
    }

    private static int surface(TestWorld world, int x, int z) {
        for (int y = world.maxY(); y >= world.minY; y--) {
            if (!world.at(x, y, z).equals(TestWorld.AIR)) {
                return y;
            }
        }
        return world.minY;
    }

    private static int[] top(TestWorld world, int x, int z) {
        return new int[]{x, surface(world, x, z), z};
    }

    /** El hueco más bajo cerca del centro (la celda de aire encima del suelo). */
    private static int[] lowest(TestWorld world, int x, int z, int radius) {
        int[] best = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int y = surface(world, x + dx, z + dz) + 1;
                if (best == null || y < best[1]) {
                    best = new int[]{x + dx, y, z + dz};
                }
            }
        }
        return best;
    }

    /** El bloque de lo alto del acantilado en el borde, en la fila {@code z}. */
    private static int[] edge(TestWorld world, int z) {
        for (int x = 0; x < 40; x++) {
            int top = surface(world, x, z);
            if (surface(world, x + 1, z) < top - 4) {
                return new int[]{x, top, z};
            }
        }
        throw new IllegalStateException("sin borde");
    }

    private static TestWorld copy(TestWorld source) {
        TestWorld copy = new TestWorld(source.minX, source.minY, source.minZ, source.sizeX, source.sizeY,
                source.sizeZ);
        for (int y = source.minY; y <= source.maxY(); y++) {
            for (int z = source.minZ; z <= source.maxZ(); z++) {
                for (int x = source.minX; x <= source.maxX(); x++) {
                    copy.set(x, y, z, source.at(x, y, z));
                }
            }
        }
        copy.selectInside(0);
        return copy;
    }

    // ---------------------------------------------------------------- dibujo

    private static BufferedImage render(TestWorld world, String title, String note, long[] marks, long[] path)
            throws IOException {
        return render(world, title, note, marks, path, 0xE53935);
    }

    /** {@code marks}: cubos de color {@code markColor}; {@code path}: el camino del agua, en naranja. */
    private static BufferedImage render(TestWorld world, String title, String note, long[] marks, long[] path,
                                        int markColor) throws IOException {
        double centerX = world.minX + world.sizeX / 2.0;
        double centerZ = world.minZ + world.sizeZ / 2.0;
        try (SoftRaster raster = new SoftRaster(1100, 800, 15.0, centerX, world.minY + 10.0, centerZ)) {
            raster.clear(0x9CC8F0, 0xDCEBF7);
            List<double[]> water = new ArrayList<>();
            List<int[]> waterTextures = new ArrayList<>();
            List<Integer> tints = new ArrayList<>();
            int[] still = raster.texture("water_still", 0x3F76E4, raster.real() ? 0x3F76E4 : 0xFFFFFF);
            for (int y = world.minY; y <= world.maxY(); y++) {
                for (int z = world.minZ; z <= world.maxZ(); z++) {
                    for (int x = world.minX; x <= world.maxX(); x++) {
                        String block = world.at(x, y, z);
                        if (block.equals(TestWorld.AIR)) {
                            continue;
                        }
                        if (FluidScenes.water(world, x, y, z)) {
                            double height = height(world, x, y, z);
                            for (int face = 0; face < 6; face++) {
                                String next = neighbour(world, x, y, z, face);
                                if (next.startsWith("minecraft:water") || !next.equals(TestWorld.AIR)
                                        && face != 3) {
                                    continue;
                                }
                                water.add(new double[]{x, y, z, face, height});
                                waterTextures.add(still);
                                tints.add(0xFFFFFF);
                            }
                            continue;
                        }
                        int[][] textures = textures(raster, block);
                        for (int face = 0; face < 6; face++) {
                            String next = neighbour(world, x, y, z, face);
                            if (!next.equals(TestWorld.AIR) && !next.startsWith("minecraft:water")) {
                                continue;
                            }
                            raster.face(x, y, z, x + 1, y + 1, z + 1, face, textures[face], SoftRaster.SHADE[face],
                                    1.0, 0.0, null, false);
                        }
                    }
                }
            }
            raster.singleLayer(water, waterTextures, tints, raster.real() ? 0.86 : 0.78, 0.0);
            int[] red = flat(markColor);
            for (long mark : marks) {
                int x = PackedBlockPlan.unpackX(mark);
                int y = PackedBlockPlan.unpackY(mark);
                int z = PackedBlockPlan.unpackZ(mark);
                for (int face = 0; face < 6; face++) {
                    raster.face(x - 0.04, y - 0.04, z - 0.04, x + 1.04, y + 1.04, z + 1.04, face, red,
                            SoftRaster.SHADE[face], 0.85, 0.02, null, false);
                }
            }
            for (int index = 0; index + 1 < path.length; index++) {
                raster.line(center(path[index]), center(path[index + 1]), 0xF0FB8C00, 3.0, true, 0.05);
            }
            if (marks.length > 0 && path.length > 0) {
                // Un poste rojo que se ve a través del terreno, para encontrar el hueco.
                double[] hole = center(marks[0]);
                raster.line(hole, new double[]{hole[0], hole[1] + 5.0, hole[2]}, 0xF0E53935, 3.0, true, 0.05);
            }
            BufferedImage image = raster.image();
            Graphics2D graphics = image.createGraphics();
            SoftRaster.caption(graphics, title, note);
            if (marks.length > 0 && path.length > 0) {
                double[] hole = center(marks[0]);
                SoftRaster.label(graphics, raster, new double[]{hole[0], hole[1] + 5.8, hole[2]}, "se escapa aquí",
                        new Color(0xFFCDD2), 14);
            }
            graphics.dispose();
            return image;
        }
    }

    /** Alto del agua como en el juego: la fuente 8/9, la corriente según su nivel, llena si hay agua encima. */
    private static double height(TestWorld world, int x, int y, int z) {
        if (y < world.maxY() && FluidScenes.water(world, x, y + 1, z)) {
            return 1.0;
        }
        String block = world.at(x, y, z);
        int level = block.contains("level=") ? Integer.parseInt(block.replaceAll("[^0-9]", "")) : 0;
        if (level >= 8) {
            return 1.0;
        }
        return level == 0 ? 8.0 / 9.0 : (8.0 - level) / 9.0;
    }

    private static String neighbour(TestWorld world, int x, int y, int z, int face) {
        int nx = x + OFFSET_X[face];
        int ny = y + OFFSET_Y[face];
        int nz = z + OFFSET_Z[face];
        if (nx < world.minX || ny < world.minY || nz < world.minZ || nx > world.maxX() || ny > world.maxY()
                || nz > world.maxZ()) {
            return ny < world.minY ? "minecraft:stone" : TestWorld.AIR;
        }
        return world.at(nx, ny, nz);
    }

    private static int[][] textures(SoftRaster raster, String block) {
        String id = block.contains("[") ? block.substring(0, block.indexOf('[')) : block;
        return switch (id) {
            case "minecraft:grass_block" -> {
                int[] side = raster.texture("grass_block_side", 0x7A9A4A, 0xFFFFFF);
                int[] top = raster.texture("grass_block_top", 0x91BD59, raster.real() ? 0x91BD59 : 0xFFFFFF);
                int[] bottom = raster.texture("dirt", 0x8B5E3C, 0xFFFFFF);
                yield new int[][]{side, side, bottom, top, side, side};
            }
            case "minecraft:dirt" -> all(raster.texture("dirt", 0x8B5E3C, 0xFFFFFF));
            case "minecraft:sand" -> all(raster.texture("sand", 0xDBCF9C, 0xFFFFFF));
            case "minecraft:stone" -> all(raster.texture("stone", 0x7F7F7F, 0xFFFFFF));
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

    private static double[] center(long packed) {
        return new double[]{PackedBlockPlan.unpackX(packed) + 0.5, PackedBlockPlan.unpackY(packed) + 0.5,
                PackedBlockPlan.unpackZ(packed) + 0.5};
    }

    /** Dos imágenes lado a lado. */
    private static BufferedImage side(BufferedImage left, BufferedImage right) {
        BufferedImage sheet = new BufferedImage(left.getWidth() + right.getWidth() + 8,
                Math.max(left.getHeight(), right.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(0x26323B));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        graphics.drawImage(left, 0, 0, null);
        graphics.drawImage(right, left.getWidth() + 8, 0, null);
        graphics.dispose();
        return sheet;
    }

    private static void write(BufferedImage image, Path path) throws IOException {
        ImageIO.write(image, "png", path.toFile());
    }
}
