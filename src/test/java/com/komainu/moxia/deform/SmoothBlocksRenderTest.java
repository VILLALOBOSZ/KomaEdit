package com.komainu.moxia.deform;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeGenerator;
import com.komainu.moxia.shape.smooth.PieceMaterials;
import com.komainu.moxia.shape.smooth.PieceRender;
import com.komainu.moxia.shape.smooth.PieceVariant;
import com.komainu.moxia.shape.smooth.SmoothMaterials;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.shape.smooth.VanillaCatalog;
import com.komainu.moxia.shape.tube.TubeCommandParser;
import com.sk89q.worldedit.math.BlockVector3;
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
 * Renders de smoothblocks fuera del juego, como las comparativas de la wiki de ezEdits: cada perfil en un tubo y
 * en un monte suavizado, la cobertura de 0 a 1 y los materiales por defecto y fijados. Solo se ejecuta con
 * {@code KOMAEDIT_SMOOTH_RENDER=<carpeta>}; en el CI no hace nada.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_SMOOTH_RENDER", matches = ".+")
class SmoothBlocksRenderTest {
    private static final VanillaCatalog CATALOG = VanillaCatalog.INSTANCE;
    private static final int SIZE = 28;

    @Test
    void renderProfilesOnASpline() throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String profile : List.of("", "Slabs", "SlabsAndStairs", "SlabsAndStairs2D", "Layers", "SSW", "Panes")) {
            TubeCommandParser.Result parsed = TubeCommandParser.parse(PathShape.SWEEP, "circle", "5",
                    profile.isEmpty() ? "" : "-w " + profile, "minecraft:smooth_sandstone");
            PackedBlockPlan plan = ShapeGenerator.generatePlan(spline(), parsed.parameters());
            images.add(PieceRender.render(cells(plan, "minecraft:smooth_sandstone", parsed.materials()), 9));
            labels.add((profile.isEmpty() ? "sin -w" : "-w " + profile) + " · " + plan.pieceCount() + " piezas");
        }
        write(PieceRender.sheet(images, labels, 3), "smoothblocks_perfiles_spline.png");
    }

    @Test
    void renderCoverage() throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (double coverage : new double[]{0.0, 0.25, 0.5, 0.75, 1.0}) {
            String profile = "SSW(C:" + coverage + ")";
            TubeCommandParser.Result parsed = TubeCommandParser.parse(PathShape.SWEEP, "circle", "7",
                    "-w " + profile, "minecraft:clay");
            PackedBlockPlan plan = ShapeGenerator.generatePlan(straight(), parsed.parameters());
            images.add(PieceRender.render(cells(plan, "minecraft:clay", parsed.materials()), 9));
            labels.add("-w " + profile + " · " + plan.pieceCount() + " piezas");
        }
        write(PieceRender.sheet(images, labels, 5), "smoothblocks_cobertura.png");
    }

    @Test
    void renderSmoothedHill() throws IOException {
        Volume hill = Volume.of(Shapes.snapshot(SIZE, 16, SIZE, 3, (x, y, z) -> y < 0 ? Shapes.STONE
                : rough(Math.clamp(x, 0, SIZE - 1), y, Math.clamp(z, 0, SIZE - 1))));
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        images.add(PieceRender.render(cells(new Shapes.Result(hill), PieceMaterials.NONE), 8));
        labels.add("monte antes");
        for (String profile : List.of("Slabs", "SlabsAndStairs", "SlabsAndStairs2D", "Layers", "SSW",
                "SlabsAndStairs(Slab:acacia,Stair:spruce)", "Layers(C:1)", "Slabs(C:1)")) {
            SmoothProfile parsed = SmoothProfile.parse(profile);
            Shapes.Result result = Shapes.apply(hill, SmoothingOps.smooth(hill, new double[]{2, 2, 2}, 1, 0,
                    Shapes.NO_CANCEL), hill.selectionBounds(), parsed);
            images.add(PieceRender.render(cells(result, parsed.materials()), 8));
            labels.add("smooth 2 1 0 -w " + profile + " · " + (result.stats.slabs() + result.stats.stairs())
                    + " piezas");
        }
        write(PieceRender.sheet(images, labels, 3), "smoothblocks_perfiles_monte.png");
    }

    @Test
    void renderCornersCloseUp() throws IOException {
        // Cúpula: las esquinas de las escaleras se ven bien. 2D las elige rectas; 3D mira a los vecinos.
        Volume dome = Volume.of(Shapes.snapshot(16, 10, 16, 3, (x, y, z) -> {
            if (y < 0) {
                return Shapes.STONE;
            }
            double dx = x + 0.5 - 8;
            double dz = z + 0.5 - 8;
            return dx * dx + y * y * 2.2 + dz * dz <= 42 ? Shapes.STONE : Shapes.AIR;
        }));
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String profile : List.of("SlabsAndStairs2D", "SlabsAndStairs", "SSW(C:1)", "Layers")) {
            SmoothProfile parsed = SmoothProfile.parse(profile);
            Shapes.Result result = Shapes.apply(dome, SmoothingOps.smooth(dome, new double[]{1, 1, 1}, 1, 0,
                    Shapes.NO_CANCEL), dome.selectionBounds(), parsed);
            Long2ObjectOpenHashMap<PieceRender.Cell> cells = cells(result, parsed.materials());
            int corners = 0;
            for (PieceRender.Cell cell : cells.values()) {
                if (SmoothPiece.isStairs(cell.piece()) && SmoothPiece.shape(cell.piece()) != SmoothPiece.STRAIGHT) {
                    corners++;
                }
            }
            images.add(PieceRender.render(cells, 22));
            labels.add("-w " + profile + " · " + corners + " esquinas");
        }
        write(PieceRender.sheet(images, labels, 2), "smoothblocks_esquinas.png");
    }

    @Test
    void renderPanes() throws IOException {
        // Vidrio con -w Panes: un tubo macizo y cintas finas de pie, con cobertura 0,5 y 1. Los paneles van en azul
        // para distinguirlos del vidrio entero.
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        String glass = "minecraft:light_gray_stained_glass";
        record Variant(String profile, String radius, String flags) {
        }
        for (Variant variant : List.of(
                new Variant("circle", "5", "-w Panes"),
                new Variant("circle", "5", "-w Panes(C:1)"),
                new Variant("rectangle", "6", "-a 0.12 -r 90 -w Panes"),
                new Variant("rectangle", "6", "-a 0.2 -r 90 -w Panes(C:1)"),
                new Variant("rectangle", "6", "-a 0.12 -r 90 -t 60 -w Panes(Pane:iron_bars)"),
                new Variant("rectangle", "6", "-a 0.12 -r 90"))) {
            TubeCommandParser.Result parsed = TubeCommandParser.parse(PathShape.SWEEP, variant.profile(),
                    variant.radius(), variant.flags(), glass);
            PackedBlockPlan plan = ShapeGenerator.generatePlan(spline(), parsed.parameters());
            Long2ObjectOpenHashMap<PieceRender.Cell> cells = cells(plan, glass, parsed.materials());
            int panes = 0;
            for (var entry : cells.long2ObjectEntrySet()) {
                if (SmoothPiece.isPane(entry.getValue().piece())) {
                    panes++;
                    entry.setValue(new PieceRender.Cell(entry.getValue().piece(), 0x2E86C1));
                }
            }
            images.add(PieceRender.render(cells, 9));
            labels.add(variant.profile() + " " + variant.radius() + " " + variant.flags() + " · " + panes
                    + " paneles");
        }
        write(PieceRender.sheet(images, labels, 2), "smoothblocks_paneles.png");
    }

    @Test
    void renderDefaultMaterials() throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String block : List.of("minecraft:stone_bricks", "minecraft:gray_concrete", "minecraft:white_wool",
                "minecraft:clay", "minecraft:red_terracotta", "minecraft:oak_log", "minecraft:lime_concrete",
                "minecraft:blue_wool")) {
            TubeCommandParser.Result parsed = TubeCommandParser.parse(PathShape.SWEEP, "circle", "4",
                    "-w SSW", block);
            PackedBlockPlan plan = ShapeGenerator.generatePlan(straightShort(), parsed.parameters());
            images.add(PieceRender.render(cells(plan, block, PieceMaterials.NONE), 9));
            labels.add(short_(block) + " → " + short_(SmoothMaterials.resolve(block, PieceVariant.SLAB, CATALOG))
                    + " / " + short_(SmoothMaterials.resolve(block, PieceVariant.STAIR, CATALOG)) + " / "
                    + short_(SmoothMaterials.resolve(block, PieceVariant.WALL, CATALOG)));
        }
        write(PieceRender.sheet(images, labels, 2), "smoothblocks_materiales.png");
        // Tabla de la elección por color de los bloques sin losa propia, para revisarla a mano.
        StringBuilder table = new StringBuilder();
        for (String block : new java.util.TreeSet<>(SmoothMaterials.textureColors().keySet())) {
            String slab = SmoothMaterials.resolve(block, PieceVariant.SLAB, CATALOG);
            if (CATALOG.is(block, PieceVariant.SLAB) || SmoothMaterials.candidates(block, PieceVariant.SLAB)
                    .contains(slab)) {
                continue;
            }
            table.append(short_(block)).append(" -> ").append(short_(slab)).append(" / ")
                    .append(short_(SmoothMaterials.resolve(block, PieceVariant.STAIR, CATALOG))).append(" / ")
                    .append(short_(SmoothMaterials.resolve(block, PieceVariant.WALL, CATALOG)))
                    .append(System.lineSeparator());
        }
        Files.writeString(Path.of(System.getenv("KOMAEDIT_SMOOTH_RENDER")).resolve("smoothblocks_colores.txt"),
                table.toString());
    }

    // ------------------------------------------------------------------ ayudas

    private static Long2ObjectOpenHashMap<PieceRender.Cell> cells(PackedBlockPlan plan, String block,
                                                                  PieceMaterials materials) {
        Long2ObjectOpenHashMap<PieceRender.Cell> cells = new Long2ObjectOpenHashMap<>();
        for (int index = 0; index < plan.size(); index++) {
            BlockVector3 position = plan.position(index);
            byte piece = plan.piece(index);
            cells.put(PackedBlockPlan.pack(position.x(), position.y(), position.z()),
                    new PieceRender.Cell(piece, color(block, piece, materials)));
        }
        return cells;
    }

    private static Long2ObjectOpenHashMap<PieceRender.Cell> cells(Shapes.Result result, PieceMaterials materials) {
        Long2ObjectOpenHashMap<PieceRender.Cell> cells = new Long2ObjectOpenHashMap<>();
        Volume volume = result.volume;
        for (int x = 0; x < volume.sizeX - 6; x++) {
            for (int y = 0; y < volume.sizeY - 6; y++) {
                for (int z = 0; z < volume.sizeZ - 6; z++) {
                    int id = result.at(x, y, z);
                    if (id == Shapes.AIR) {
                        continue;
                    }
                    byte piece = result.piece(x, y, z);
                    String block = switch (id) {
                        case Shapes.GRASS -> "minecraft:moss_block";
                        case Shapes.DIRT -> "minecraft:dirt";
                        default -> "minecraft:stone";
                    };
                    cells.put(PackedBlockPlan.pack(x, y, z), new PieceRender.Cell(piece, color(block, piece,
                            materials)));
                }
            }
        }
        return cells;
    }

    private static int color(String block, byte piece, PieceMaterials materials) {
        PieceVariant variant = SmoothPiece.variant(piece);
        if (variant == null) {
            return CATALOG.color(block);
        }
        String override = materials.get(variant);
        String target = override != null
                ? SmoothMaterials.override(override, variant, CATALOG)
                : SmoothMaterials.resolve(block, variant, CATALOG);
        return target == null ? 0xFF00FF : CATALOG.color(target);
    }

    private static int rough(int x, int y, int z) {
        double height = 3 + 8 * Math.exp(-((x - 14) * (x - 14) + (z - 14) * (z - 14)) / 60.0)
                + 1.5 * Math.sin(x * 0.7) * Math.cos(z * 0.5);
        return y < height - 1 ? Shapes.STONE : y < height ? Shapes.GRASS : Shapes.AIR;
    }

    private static SampledPath spline() {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(14, 8, 6),
                BlockVector3.at(28, 2, 2), BlockVector3.at(40, 10, 16)), false);
    }

    private static SampledPath straight() {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(16, 0, 0)), false);
    }

    private static SampledPath straightShort() {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(10, 3, 0)), false);
    }

    private static String short_(String id) {
        return id == null ? "—" : id.substring(id.indexOf(':') + 1);
    }

    private static void write(BufferedImage image, String name) throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_SMOOTH_RENDER"));
        Files.createDirectories(folder);
        ImageIO.write(image, "png", folder.resolve(name).toFile());
    }
}
