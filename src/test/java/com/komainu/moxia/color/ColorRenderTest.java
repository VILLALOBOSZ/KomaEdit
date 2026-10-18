package com.komainu.moxia.color;

import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.texture.PaletteRemap;
import com.komainu.moxia.texture.ShadeField;
import com.komainu.moxia.texture.TextureContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders de comprobación fuera del juego. Solo se ejecuta con {@code KOMAEDIT_COLOR_RENDER=<carpeta>}; con
 * {@code KOMAEDIT_MC_JAR=<minecraft-client.jar>} (está en la caché de Loom) usa las texturas de verdad.
 *
 * <ul>
 *     <li>{@code degradados.png}: degradados de {@code /komaedit color gradient} con el color ideal de cada paso
 *     encima, una alternativa ({@code -v 1}) y las rampas que usa el sombreado.</li>
 *     <li>{@code sombreado_colina.png} y {@code sombreado_busto.png}: antes y después de {@code /komaedit shade}
 *     con luces distintas; el busto tiene escaleras y losas, que conservan su forma.</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_COLOR_RENDER", matches = ".+")
class ColorRenderTest {
    private static final BlockColorCatalog CATALOG = TestColors.VANILLA;
    private static final ColorFilter BUILD = ColorFilter.of(true, true, true, false);
    private static final int TILE = 64;
    private static final int AIR = 0;

    @Test
    void rendersGradientsAndShading() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_COLOR_RENDER"));
        Files.createDirectories(folder);
        try (BlockTextures textures = BlockTextures.open()) {
            ImageIO.write(gradients(textures), "png", folder.resolve("degradados.png").toFile());
            ImageIO.write(hill(textures, folder), "png", folder.resolve("sombreado_colina.png").toFile());
            ImageIO.write(bust(textures, folder), "png", folder.resolve("sombreado_busto.png").toFile());
        }
    }

    // ---------------------------------------------------------------- degradados

    private record GradientCase(String label, ColorGradient.Anchor from, ColorGradient.Anchor to, int steps,
                                ColorFilter filter) {
    }

    private static BufferedImage gradients(BlockTextures textures) {
        List<GradientCase> cases = List.of(
                new GradientCase("deepslate → calcite, 9 pasos, -c -o", anchor("deepslate"), anchor("calcite"), 9,
                        BUILD),
                new GradientCase("dark_oak_planks → birch_planks, 7 pasos, -c -o", anchor("dark_oak_planks"),
                        anchor("birch_planks"), 7, BUILD),
                new GradientCase("#2B1B3A → #F2C14E (atardecer), 9 pasos, -c -o -u", ColorGradient.Anchor.of(0x2B1B3A),
                        ColorGradient.Anchor.of(0xF2C14E), 9, ColorFilter.of(true, true, true, true)),
                new GradientCase("moss_block → sand, 7 pasos, -c -o", anchor("moss_block"), anchor("sand"), 7, BUILD),
                new GradientCase("blue_concrete → white_concrete, 8 pasos, -c -o", anchor("blue_concrete"),
                        anchor("white_concrete"), 8, BUILD)
        );
        List<BufferedImage> rows = new ArrayList<>();
        for (GradientCase gradient : cases) {
            List<BlockColor> first = ColorGradient.between(CATALOG, gradient.from(), gradient.to(), gradient.steps(),
                    gradient.filter(), 0);
            List<BlockColor> second = ColorGradient.between(CATALOG, gradient.from(), gradient.to(), gradient.steps(),
                    gradient.filter(), 1);
            List<double[]> targets = new ArrayList<>();
            for (int step = 0; step < gradient.steps(); step++) {
                targets.add(OkLab.lerp(gradient.from().lab(), gradient.to().lab(), step / (gradient.steps() - 1.0)));
            }
            rows.add(gradientRow(gradient.label(), targets, first, second, textures));
        }
        for (String base : List.of("stone", "sandstone", "red_terracotta", "oak_planks")) {
            BlockColor block = TestColors.block(base);
            List<BlockColor> ramp = ColorGradient.ramp(CATALOG, block, 7, ColorFilter.BUILDING);
            List<double[]> targets = ramp.stream().map(BlockColor::lab).toList();
            rows.add(gradientRow("rampa del sombreado alrededor de " + base + " (7)", targets, ramp, null, textures));
        }
        return stack(rows, textures.real() ? "Degradados en OKLab (texturas de Minecraft 1.21.1)"
                : "Degradados en OKLab (colores medios)");
    }

    private static BufferedImage gradientRow(String label, List<double[]> targets, List<BlockColor> blocks,
                                             List<BlockColor> alternative, BlockTextures textures) {
        int steps = blocks.size();
        int width = Math.max(steps * (TILE + 8) + 16, 640);
        int height = 22 + 14 + TILE + 30 + (alternative == null ? 0 : TILE + 30);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas(image);
        graphics.setColor(new Color(0x202020));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        graphics.drawString(label, 8, 15);
        for (int step = 0; step < steps; step++) {
            int x = 8 + step * (TILE + 8);
            graphics.setColor(new Color(OkLab.rgb(targets.get(step))));
            graphics.fillRect(x, 22, TILE, 10);
            tile(graphics, blocks.get(step), textures, x, 36);
            if (alternative != null) {
                tile(graphics, alternative.get(step), textures, x, 36 + TILE + 30);
            }
        }
        if (alternative != null) {
            graphics.setColor(new Color(0x606060));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
            graphics.drawString("variante 1 ([otra])", 8 + steps * (TILE + 8), 36 + TILE + 30 + TILE / 2);
        }
        graphics.dispose();
        return image;
    }

    private static void tile(Graphics2D graphics, BlockColor block, BlockTextures textures, int x, int y) {
        BufferedImage side = textures.faces(block.id(), block.rgb())[1];
        graphics.drawImage(side, x, y, TILE, TILE, null);
        graphics.setColor(new Color(0x303030));
        graphics.drawRect(x, y, TILE - 1, TILE - 1);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        String name = block.shortName();
        int split = name.length() > 12 ? name.lastIndexOf('_', 12) : -1;
        if (split > 0) {
            graphics.drawString(name.substring(0, split), x, y + TILE + 11);
            graphics.drawString(name.substring(split), x, y + TILE + 22);
        } else {
            graphics.drawString(name, x, y + TILE + 11);
        }
    }

    // ---------------------------------------------------------------- escenas

    /** Una escena: la foto, los bloques que no son enteros (con su pieza) y los colores de cada id. */
    private record Scene(RegionSnapshot snapshot, Map<Long, Byte> pieces) {
    }

    private static final class SceneBuilder {
        private final Map<String, Integer> ids = new LinkedHashMap<>();
        private final List<RegionSnapshot.StateInfo> states = new ArrayList<>();
        private final Map<Long, Integer> blocks = new HashMap<>();
        private final Map<Long, Byte> pieces = new HashMap<>();

        SceneBuilder() {
            states.add(new RegionSnapshot.StateInfo(AIR, null, "minecraft:air", true, false, false));
            ids.put("minecraft:air", AIR);
        }

        void set(int x, int y, int z, String id, byte piece) {
            String full = id.contains(":") ? id : "minecraft:" + id;
            int raw = ids.computeIfAbsent(full, key -> {
                int next = states.size();
                states.add(new RegionSnapshot.StateInfo(next, null, key, false, false, piece == SmoothPiece.FULL));
                return next;
            });
            long key = VoxelRender.pack(x, y, z);
            blocks.put(key, raw);
            if (piece == SmoothPiece.FULL) {
                pieces.remove(key);
            } else {
                pieces.put(key, piece);
            }
        }

        boolean has(int x, int y, int z) {
            return blocks.containsKey(VoxelRender.pack(x, y, z));
        }

        Scene build(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
            RegionSnapshot.Builder builder = RegionSnapshot.builder(minX, minY, minZ, sizeX, sizeY, sizeZ);
            states.forEach(builder::state);
            for (int x = minX; x < minX + sizeX; x++) {
                for (int y = minY; y < minY + sizeY; y++) {
                    for (int z = minZ; z < minZ + sizeZ; z++) {
                        builder.set(x, y, z, blocks.getOrDefault(VoxelRender.pack(x, y, z), AIR), true);
                    }
                }
            }
            return new Scene(builder.build(), Map.copyOf(pieces));
        }
    }

    /** Colina de 44×44 con manchas de cinco piedras, como una ladera hecha a mano antes de sombrear. */
    private static BufferedImage hill(BlockTextures textures, Path folder) throws IOException {
        int size = 52;
        int height = 26;
        NoiseSpec.NoiseField hills = NoiseSpec.parse("Perlin(f:0.07,fT:F,fO:3)").build(11L);
        NoiseSpec.NoiseField patches = NoiseSpec.parse("Perlin(f:0.12)").build(5L);
        String[] rocks = {"cobbled_deepslate", "tuff", "stone", "andesite", "calcite"};
        SceneBuilder scene = new SceneBuilder();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                // Tres lomas de distinto tamaño con algo de ruido encima.
                double ground = 16.0 * bump(x, z, 18, 20, 9.0) + 11.0 * bump(x, z, 36, 32, 7.5)
                        + 8.0 * bump(x, z, 34, 12, 6.0) + 6.0 * (hills.sample(x, 0, z) - 0.5);
                int top = 1 + (int) Math.round(Math.max(0.0, ground));
                for (int y = 0; y <= Math.min(top, height - 4); y++) {
                    double value = patches.sample(x, y * 1.5, z);
                    String rock = rocks[Math.clamp((int) ((value - 0.3) / 0.4 * rocks.length), 0, rocks.length - 1)];
                    scene.set(x, y, z, rock, SmoothPiece.FULL);
                }
            }
        }
        Scene built = scene.build(0, 0, 0, size, height, size);
        Map<String, Integer> counts = ShadeOperation.count(built.snapshot(), CATALOG, TextureContext.Filter.ALL,
                () -> { });
        ShadePalette palette = ShadePalette.fromCounts(counts, CATALOG, 7, false);
        TextureContext.Filter target = (x, y, z) -> ShadeOperation.recolorable(built.snapshot(), x, y, z)
                && palette.targets().contains(CATALOG.base(built.snapshot().blockId(x, y, z)));
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        images.add(VoxelRender.render(cells(built, Map.of()), textures, 10));
        labels.add("antes: manchas de " + String.join(", ", rocks));
        StringBuilder report = new StringBuilder("Paleta de la selección: " + palette.describe() + "\n");
        for (String light : List.of("ne", "so")) {
            ShadeLight resolved = ShadeLight.resolve(light, null);
            for (double dither : new double[]{0.0, 0.6}) {
                ShadeField field = ShadeField.sun(resolved.towards(), 0.6, 3, 0.8, target);
                long start = System.nanoTime();
                Map<Long, String> changed = shade(built, palette.ids(), field, dither);
                long millis = (System.nanoTime() - start) / 1_000_000;
                images.add(VoxelRender.render(cells(built, changed), textures, 10));
                labels.add("shade -l " + light + (dither > 0 ? " -d " + dither : "") + " · " + changed.size()
                        + " bloques · " + millis + " ms");
                report.append(labels.getLast()).append('\n');
            }
        }
        Files.writeString(folder.resolve("sombreado_colina.txt"), report);
        return sheet(images, labels, 2, "Colina: paleta sacada de la selección, de oscuro a claro: "
                + palette.describe());
    }

    /** Busto de arenisca con hombros de escaleras y losas en la coronilla, sobre un pedestal de andesita. */
    private static BufferedImage bust(BlockTextures textures, Path folder) throws IOException {
        int size = 30;
        int height = 36;
        double cx = 14.5;
        double cz = 14.5;
        SceneBuilder scene = new SceneBuilder();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = 0; y < height; y++) {
                    double dx = x + 0.5 - cx;
                    double dz = z + 0.5 - cz;
                    if (y < 4 && Math.abs(dx) <= 6 && Math.abs(dz) <= 5) {
                        scene.set(x, y, z, "polished_andesite", SmoothPiece.FULL);
                    } else if (inBust(dx, y + 0.5, dz)) {
                        scene.set(x, y, z, "sandstone", SmoothPiece.FULL);
                    }
                }
            }
        }
        // Escaleras donde la superficie baja hacia un lado (hombros, mentón) y losas en lo alto de los salientes.
        SceneBuilder shaped = new SceneBuilder();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = 0; y < height; y++) {
                    double dx = x + 0.5 - cx;
                    double dz = z + 0.5 - cz;
                    if (y < 4 && Math.abs(dx) <= 6 && Math.abs(dz) <= 5) {
                        shaped.set(x, y, z, "polished_andesite", SmoothPiece.FULL);
                        continue;
                    }
                    if (!inBust(dx, y + 0.5, dz)) {
                        continue;
                    }
                    byte piece = SmoothPiece.FULL;
                    if (!scene.has(x, y + 1, z) && y > 6) {
                        int[][] sides = {{0, 1, SmoothPiece.SOUTH}, {-1, 0, SmoothPiece.WEST}, {0, -1, SmoothPiece.NORTH},
                                {1, 0, SmoothPiece.EAST}};
                        int open = 0;
                        int facing = -1;
                        for (int[] side : sides) {
                            if (!scene.has(x + side[0], y, z + side[1])) {
                                open++;
                                // Sube hacia el lado contrario al hueco.
                                facing = SmoothPiece.opposite(side[2]);
                            }
                        }
                        if (open == 1) {
                            piece = SmoothPiece.stairs(facing, false, SmoothPiece.STRAIGHT);
                        } else if (open >= 3) {
                            piece = SmoothPiece.slab(false);
                        }
                    }
                    String id = piece == SmoothPiece.FULL ? "sandstone"
                            : SmoothPiece.isStairs(piece) ? "sandstone_stairs" : "sandstone_slab";
                    shaped.set(x, y, z, id, piece);
                }
            }
        }
        Scene built = shaped.build(0, 0, 0, size, height, size);
        Map<String, Integer> counts = ShadeOperation.count(built.snapshot(), CATALOG, TextureContext.Filter.ALL,
                () -> { });
        // «/komaedit shade rampa»: rampa del material principal; el pedestal no se toca.
        ShadePalette palette = ShadePalette.fromCounts(counts, CATALOG, 7, true);
        TextureContext.Filter target = (x, y, z) -> ShadeOperation.recolorable(built.snapshot(), x, y, z)
                && palette.targets().contains(CATALOG.base(built.snapshot().blockId(x, y, z)));
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        images.add(VoxelRender.render(cells(built, Map.of()), textures, 12));
        labels.add("antes: arenisca con escaleras y losas");
        StringBuilder report = new StringBuilder("Rampa: " + palette.describe() + "\n");
        for (String light : List.of("ne", "so", "arriba")) {
            ShadeLight resolved = ShadeLight.resolve(light, null);
            ShadeField field = ShadeField.sun(resolved.towards(), 0.7, 3, 0.8, target).withPivot(palette.pivot());
            Map<Long, String> changed = shade(built, palette.ids(), field, 0.4);
            images.add(VoxelRender.render(cells(built, changed), textures, 12));
            labels.add("shade rampa -l " + light + " -d 0.4");
            long stairs = changed.values().stream().filter(id -> id.endsWith("_stairs")).count();
            long slabs = changed.values().stream().filter(id -> id.endsWith("_slab")).count();
            report.append(labels.getLast()).append(": ").append(changed.size()).append(" cambios, ").append(stairs)
                    .append(" escaleras y ").append(slabs).append(" losas\n");
        }
        Files.writeString(folder.resolve("sombreado_busto.txt"), report);
        return sheet(images, labels, 2, "Busto: rampa alrededor de la arenisca: " + palette.describe());
    }

    /** Cabeza, cuello, hombros y pecho; cuencas de los ojos excavadas y nariz. */
    private static boolean inBust(double dx, double y, double dz) {
        // Cabeza: elipsoide.
        double head = sq(dx / 5.2) + sq((y - 26.0) / 6.5) + sq(dz / 5.6);
        boolean inside = head <= 1.0;
        // Nariz hacia el sur (+z).
        if (Math.abs(dx) < 1.0 && y > 23.0 && y < 26.5 && dz > 4.5 && dz < 6.6) {
            inside = true;
        }
        // Cuencas de los ojos.
        if (Math.abs(Math.abs(dx) - 2.2) < 1.1 && y > 26.0 && y < 28.0 && dz > 4.0) {
            inside = false;
        }
        // Cuello.
        if (sq(dx / 2.8) + sq(dz / 2.8) <= 1.0 && y >= 15.0 && y <= 21.0) {
            inside = true;
        }
        // Hombros y pecho: caja redondeada que se estrecha hacia abajo.
        double halfWidth = 10.0 - Math.max(0.0, 12.0 - y) * 0.35;
        double shoulder = Math.max(0.0, Math.abs(dx) - (halfWidth - 3.5));
        double top = 16.0 - sq(shoulder) * 0.35;
        if (y >= 4.0 && y <= top && Math.abs(dx) <= halfWidth && Math.abs(dz) <= 4.5 - Math.max(0.0, y - 13.0) * 0.4) {
            inside = true;
        }
        return inside;
    }

    private static double sq(double value) {
        return value * value;
    }

    private static double bump(int x, int z, double centerX, double centerZ, double radius) {
        return Math.exp(-(sq(x - centerX) + sq(z - centerZ)) / (2.0 * radius * radius));
    }

    /**
     * Lo que haría {@code /komaedit shade} sobre la foto: el valor de luz de cada bloque elige su sitio en la paleta y
     * las piezas buscan la suya del material (o del más cercano de la paleta que la tenga), como
     * {@code TextureOperation}.
     */
    private static Map<Long, String> shade(Scene scene, List<String> palette, ShadeField field, double dither) {
        RegionSnapshot snapshot = scene.snapshot();
        TextureContext context = new TextureContext(snapshot, TextureContext.Filter.ALL,
                TextureContext.Settings.at(0, 0, 0, 1L), () -> false);
        double[] values = field.evaluate(context);
        PaletteRemap remap = new PaletteRemap(palette, id -> CATALOG.get(id) != null);
        Map<Long, String> changed = new HashMap<>();
        for (int index = 0; index < values.length; index++) {
            if (Double.isNaN(values[index])) {
                continue;
            }
            int x = context.x(index);
            int y = context.y(index);
            int z = context.z(index);
            int position = PaletteRemap.index(values[index], palette.size(), dither, x, y, z);
            String current = snapshot.blockId(x, y, z);
            String shape = PaletteRemap.shapeOf(current);
            String chosen = shape == null ? palette.get(position) : remap.variant(position, shape);
            if (chosen == null) {
                int nearest = remap.nearestWithShape(position, shape, 1);
                chosen = nearest < 0 ? null : remap.variant(nearest, shape);
            }
            if (chosen != null && !chosen.equals(current)) {
                changed.put(VoxelRender.pack(x, y, z), chosen);
            }
        }
        return changed;
    }

    private static Map<Long, VoxelRender.Cell> cells(Scene scene, Map<Long, String> changed) {
        RegionSnapshot snapshot = scene.snapshot();
        Map<Long, VoxelRender.Cell> cells = new HashMap<>();
        for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
            for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
                for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                    if (snapshot.isAir(x, y, z)) {
                        continue;
                    }
                    long key = VoxelRender.pack(x, y, z);
                    String id = changed.getOrDefault(key, snapshot.blockId(x, y, z));
                    BlockColor color = CATALOG.get(id);
                    cells.put(key, new VoxelRender.Cell(id, scene.pieces().getOrDefault(key, SmoothPiece.FULL),
                            color == null ? 0xFF00FF : color.rgb()));
                }
            }
        }
        return cells;
    }

    // ---------------------------------------------------------------- composición

    private static ColorGradient.Anchor anchor(String id) {
        return ColorGradient.Anchor.of(TestColors.block(id));
    }

    private static Graphics2D canvas(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xF4F1EA));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        return graphics;
    }

    private static BufferedImage stack(List<BufferedImage> rows, String title) {
        int width = rows.stream().mapToInt(BufferedImage::getWidth).max().orElse(400);
        int height = 30 + rows.stream().mapToInt(image -> image.getHeight() + 6).sum();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas(image);
        graphics.setColor(new Color(0x101010));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        graphics.drawString(title, 8, 20);
        int y = 30;
        for (BufferedImage row : rows) {
            graphics.drawImage(row, 0, y, null);
            graphics.setColor(new Color(0xCCCCCC));
            graphics.drawLine(0, y + row.getHeight() + 2, width, y + row.getHeight() + 2);
            y += row.getHeight() + 6;
        }
        graphics.dispose();
        return image;
    }

    private static BufferedImage sheet(List<BufferedImage> images, List<String> labels, int columns, String title) {
        int cellWidth = images.stream().mapToInt(BufferedImage::getWidth).max().orElse(100);
        int cellHeight = images.stream().mapToInt(BufferedImage::getHeight).max().orElse(100) + 24;
        int rows = (images.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(cellWidth * columns, cellHeight * rows + 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas(sheet);
        graphics.setColor(new Color(0x101010));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        graphics.drawString(title, 8, 20);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        for (int index = 0; index < images.size(); index++) {
            int x = index % columns * cellWidth;
            int y = 30 + index / columns * cellHeight;
            BufferedImage image = images.get(index);
            graphics.drawImage(image, x + (cellWidth - image.getWidth()) / 2, y + 22, null);
            graphics.setColor(new Color(0x303030));
            graphics.drawString(labels.get(index), x + 6, y + 16);
            graphics.setColor(new Color(0xBBBBBB));
            graphics.drawRect(x, y, cellWidth - 1, cellHeight - 1);
        }
        graphics.dispose();
        return sheet;
    }
}
