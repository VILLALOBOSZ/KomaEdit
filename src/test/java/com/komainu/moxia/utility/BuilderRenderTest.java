package com.komainu.moxia.utility;

import com.komainu.moxia.deform.SmoothProfile;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.shape.smooth.PieceRender;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.text.TextFonts;
import com.komainu.moxia.text.TextModel;
import com.komainu.moxia.text.TextPlacement;
import com.komainu.moxia.text.TextRaster;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders isométricos de comprobación, fuera del juego, del texto en bloques y de la nieve. Solo se ejecuta con
 * {@code KOMAEDIT_BUILDER_RENDER=<carpeta>}: deja allí {@code texto.png}, {@code nieve.png} y {@code gravedad.png}.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_BUILDER_RENDER", matches = ".+")
class BuilderRenderTest {
    private static final int[] MAGMA = {0x3B0F0F, 0x8C1C13, 0xD3541A, 0xF2A33A};

    @Test
    void rendersTextSnowAndGravity() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_BUILDER_RENDER"));
        Files.createDirectories(folder);
        List<BufferedImage> texts = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        // De pie, con degradado de 4 bloques, borde negro y losas y escaleras: de frente y en perspectiva.
        Long2ObjectOpenHashMap<PieceRender.Cell> smooth = text("Año Ñu", "sans", 9.0, 2, 1.0,
                SmoothProfile.parse("SlabsAndStairs"), TextPlacement.Mode.WALL);
        texts.add(front(smooth, 14));
        labels.add("De frente: text \"Año Ñu\" ##Magma -s 9 -d 2 -b 1 -o black_concrete -w SlabsAndStairs");
        texts.add(PieceRender.render(smooth, 16));
        labels.add("La misma, en perspectiva (grosor 2)");
        Long2ObjectOpenHashMap<PieceRender.Cell> blocks = text("Año Ñu", "sans", 9.0, 2, 1.0, SmoothProfile.NONE,
                TextPlacement.Mode.WALL);
        texts.add(front(blocks, 14));
        labels.add("Sin -w: solo bloques enteros");
        Long2ObjectOpenHashMap<PieceRender.Cell> lying = text("¿Qué tal?", "mono", 7.0, 1, 0.0, SmoothProfile.NONE,
                TextPlacement.Mode.FLOOR);
        texts.add(top(lying, 14));
        labels.add("Desde arriba: text \"¿Qué tal?\" ##Magma -f mono -s 7, mirando al suelo (tumbado)");
        ImageIO.write(PieceRender.sheet(texts, labels, 1), "png", folder.resolve("texto.png").toFile());

        TestWorld hill = snowyHill();
        BufferedImage before = render(hill);
        SnowEngine.Result result = SnowEngineTest.snow(hill, new SnowEngine.Settings(2, 3, 1.0, 3, 2, true));
        BufferedImage after = render(hill);
        TestWorld dome = dome();
        BufferedImage domeBefore = render(dome);
        SnowEngine.Result domeResult = SnowEngineTest.snow(dome, new SnowEngine.Settings(2, 3, 1.0, 3, 0, false));
        BufferedImage domeAfter = render(dome);
        ImageIO.write(PieceRender.sheet(List.of(before, after, domeBefore, domeAfter), List.of("Colina antes",
                "snow 2 -r 3 -s 3 -h 2 -p · " + result.placed() + " columnas nevadas, " + result.skipped()
                        + " sin nieve (hielo, losas)", "Cúpula escalonada con un hoyo y un muro",
                "snow 2 -r 3 -s 3 · " + domeResult.placed() + " columnas: rampas en los escalones"), 2), "png",
                folder.resolve("nieve.png").toFile());

        TestWorld rubble = rubble();
        BufferedImage fallBefore = render(rubble);
        GravityEngine.apply(rubble.snapshot(), rubble.kinds(), null, true, (x, y, z, content, waterlogged) -> {
            String block = switch (content) {
                case GravityEngine.AIR -> TestWorld.AIR;
                case GravityEngine.WATER -> TestWorld.WATER;
                case GravityEngine.LAVA -> TestWorld.LAVA;
                default -> rubble.state(content);
            };
            pending.add(new Object[]{x, y, z, block});
        }, () -> {
        });
        for (Object[] write : pending) {
            rubble.set((int) write[0], (int) write[1], (int) write[2], (String) write[3]);
        }
        ImageIO.write(PieceRender.sheet(List.of(fallBefore, render(rubble)), List.of("antes",
                "gravity -a: cae solo lo que no se apoya"), 2), "png", folder.resolve("gravedad.png").toFile());
    }

    private final List<Object[]> pending = new ArrayList<>();

    private static Long2ObjectOpenHashMap<PieceRender.Cell> text(String content, String font, double size, int depth,
                                                                 double border, SmoothProfile profile,
                                                                 TextPlacement.Mode mode) {
        TextRaster letters = TextRaster.render(TextFonts.load(font, null), content,
                new TextRaster.Settings(size, 0.0, 1.0, TextRaster.Align.CENTER));
        TextRaster ring = border > 0.0 ? letters.border(border) : null;
        TextPlacement placement = TextPlacement.of(mode, new int[]{0, 0, 0}, new int[]{0, 0, -1});
        TextModel model = new TextModel(letters, ring, placement, depth, MAGMA.length, TextModel.Gradient.VERTICAL,
                ring != null, null);
        int[] box = model.worldBox();
        RegionSnapshot.Builder builder = RegionSnapshot.builder(box[0] - 1, box[1] - 1, box[2] - 1,
                        box[3] - box[0] + 3, box[4] - box[1] + 3, box[5] - box[2] + 3)
                .state(new RegionSnapshot.StateInfo(0, null, "minecraft:air", true, false, false));
        for (int y = box[1] - 1; y <= box[4] + 1; y++) {
            for (int z = box[2] - 1; z <= box[5] + 1; z++) {
                for (int x = box[0] - 1; x <= box[3] + 1; x++) {
                    builder.set(x, y, z, 0, true);
                }
            }
        }
        TextModel.Result result = model.plan(builder.build(), profile, () -> {
        });
        Long2ObjectOpenHashMap<PieceRender.Cell> cells = new Long2ObjectOpenHashMap<>();
        for (int index = 0; index < result.size(); index++) {
            int layer = result.layers().getInt(index);
            int color = layer >= MAGMA.length ? 0x1E1E22 : MAGMA[layer];
            cells.put(result.positions().getLong(index), new PieceRender.Cell(result.pieces().getByte(index), color));
        }
        return cells;
    }

    /** Vista desde arriba con el norte hacia arriba: el texto tumbado se lee como lo ve el jugador. */
    private static BufferedImage top(Long2ObjectOpenHashMap<PieceRender.Cell> cells, int scale) {
        Long2ObjectOpenHashMap<PieceRender.Cell> turned = new Long2ObjectOpenHashMap<>();
        for (var entry : cells.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            // Girado para la vista de frente: la x sigue igual, el norte (-z) pasa a ser arriba (+y).
            turned.put(PackedBlockPlan.pack(PackedBlockPlan.unpackX(key), -PackedBlockPlan.unpackZ(key),
                    PackedBlockPlan.unpackY(key)), new PieceRender.Cell(SmoothPiece.FULL, entry.getValue().color()));
        }
        return front(turned, scale);
    }

    /**
     * Vista de frente (desde el sur, mirando al norte): de cada columna x/y se ve la pieza más cercana; las losas y
     * escaleras se dibujan por cuartos según los octantes que ocupan.
     */
    private static BufferedImage front(Long2ObjectOpenHashMap<PieceRender.Cell> cells, int scale) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        java.util.Map<Long, Integer> nearest = new java.util.HashMap<>();
        for (var entry : cells.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            nearest.merge(PackedBlockPlan.pack(x, y, 0), z, Math::max);
        }
        int width = (maxX - minX + 3) * scale;
        int height = (maxY - minY + 3) * scale;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setColor(new java.awt.Color(0xE9EEF2));
        graphics.fillRect(0, 0, width, height);
        int half = scale / 2;
        for (var entry : nearest.entrySet()) {
            int x = PackedBlockPlan.unpackX(entry.getKey());
            int y = PackedBlockPlan.unpackY(entry.getKey());
            PieceRender.Cell cell = cells.get(PackedBlockPlan.pack(x, y, entry.getValue()));
            int left = (x - minX + 1) * scale;
            int top = (maxY - y + 1) * scale;
            graphics.setColor(new java.awt.Color(cell.color()));
            if (cell.piece() == SmoothPiece.FULL) {
                graphics.fillRect(left, top, scale, scale);
            } else {
                int mask = SmoothPiece.mask(cell.piece());
                for (int quarter = 0; quarter < 4; quarter++) {
                    int qx = quarter & 1;
                    int qy = quarter >> 1;
                    if ((mask & (1 << SmoothPiece.octant(qx, qy, 0) | 1 << SmoothPiece.octant(qx, qy, 1))) != 0) {
                        graphics.fillRect(left + qx * half, top + (1 - qy) * half, half, half);
                    }
                }
            }
            graphics.setColor(new java.awt.Color(0, 0, 0, 40));
            graphics.drawRect(left, top, scale, scale);
        }
        graphics.dispose();
        return image;
    }

    /** Ladera con un muro de piedra (la sombra), hielo, losas y un estanque, y hierba alta por encima. */
    private static TestWorld snowyHill() {
        TestWorld world = SnowEngineTest.hills(5L);
        for (int x = 2; x < 30; x += 7) {
            for (int z = 4; z < 28; z += 9) {
                int top = top(world, x, z);
                if (top + 1 < world.maxY() && world.at(x, top + 1, z).equals(TestWorld.AIR)
                        && world.id(x, top, z).equals("minecraft:grass_block")) {
                    world.set(x, top + 1, z, "short_grass");
                }
            }
        }
        for (int z = 6; z <= 24; z++) {
            int top = top(world, 14, z);
            world.fill(14, top + 1, z, 14, Math.min(world.maxY() - 2, top + 6), z, "stone_bricks");
        }
        return world;
    }

    /** Cúpula de escalones de un bloque, un hoyo en la ladera y un muro al norte que da sombra. */
    private static TestWorld dome() {
        TestWorld world = new TestWorld(0, 0, 0, 30, 16, 30);
        for (int x = 0; x < 30; x++) {
            for (int z = 0; z < 30; z++) {
                double distance = Math.hypot(x - 15.5, z - 17.5);
                int top = (int) Math.max(1, Math.round(9 - distance * 0.55));
                world.fill(x, 0, z, x, top - 1, z, "stone");
                world.set(x, top, z, "grass_block[snowy=false]");
            }
        }
        world.fill(19, 4, 19, 21, 7, 21, "air");
        world.fill(19, 3, 19, 21, 3, 21, "grass_block[snowy=false]");
        for (int x = 4; x <= 26; x++) {
            int top = top(world, x, 3);
            world.fill(x, top + 1, 3, x, top + 7, 3, "stone_bricks");
        }
        world.select(1, 1, 1, 28, 15, 28);
        return world;
    }

    private static TestWorld rubble() {
        TestWorld world = new TestWorld(0, 0, 0, 14, 14, 10);
        world.fill(0, 0, 0, 13, 0, 9, "grass_block[snowy=false]");
        world.select(0, 1, 0, 13, 13, 9);
        // Torre con un voladizo que se sostiene y un trozo suelto que no.
        world.fill(2, 1, 2, 4, 9, 4, "stone_bricks");
        world.fill(5, 7, 2, 8, 7, 4, "stone_bricks");
        world.fill(9, 10, 5, 11, 12, 7, "mossy_cobblestone");
        world.fill(6, 4, 6, 7, 5, 7, "oak_planks");
        return world;
    }

    private static int top(TestWorld world, int x, int z) {
        for (int y = world.maxY(); y >= world.minY; y--) {
            if (!world.at(x, y, z).equals(TestWorld.AIR)) {
                return y;
            }
        }
        return world.minY;
    }

    private static BufferedImage render(TestWorld world) {
        Long2ObjectOpenHashMap<PieceRender.Cell> cells = new Long2ObjectOpenHashMap<>();
        for (int y = world.minY; y <= world.maxY(); y++) {
            for (int z = world.minZ; z <= world.maxZ(); z++) {
                for (int x = world.minX; x <= world.maxX(); x++) {
                    String block = world.at(x, y, z);
                    String id = world.id(x, y, z);
                    if (id.equals(TestWorld.AIR)) {
                        continue;
                    }
                    byte piece = SmoothPiece.FULL;
                    int color;
                    switch (id) {
                        case "minecraft:snow" -> {
                            int layers = Integer.parseInt(block.substring(block.indexOf("layers=") + 7,
                                    block.length() - 1));
                            piece = layers >= 8 ? SmoothPiece.FULL : SmoothPiece.layers(layers);
                            color = 0xF4F8FC;
                        }
                        case "minecraft:grass_block" -> color = world.at(x, y + 1, z).startsWith("minecraft:snow")
                                ? 0x8A9A6A : 0x6E9E44;
                        case "minecraft:oak_slab" -> {
                            piece = SmoothPiece.slab(false);
                            color = 0xB08850;
                        }
                        case "minecraft:ice" -> color = 0x9CC4F0;
                        case "minecraft:water" -> color = 0x3F6FD8;
                        case "minecraft:short_grass" -> {
                            piece = SmoothPiece.slab(false);
                            color = 0x7DBF4A;
                        }
                        case "minecraft:stone_bricks" -> color = 0x8E8E8E;
                        case "minecraft:mossy_cobblestone" -> color = 0x6F8060;
                        case "minecraft:oak_planks" -> color = 0xB8945A;
                        default -> color = 0x777777;
                    }
                    cells.put(PackedBlockPlan.pack(x, y, z), new PieceRender.Cell(piece, color));
                }
            }
        }
        return PieceRender.render(cells, 12);
    }
}
