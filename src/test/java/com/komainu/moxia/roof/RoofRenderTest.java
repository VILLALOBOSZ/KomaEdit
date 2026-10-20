package com.komainu.moxia.roof;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.smooth.PieceRender;
import com.komainu.moxia.shape.smooth.SmoothPiece;
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
 * Renders de los tejados fuera del juego: cada tipo sobre una casa, visto desde dos esquinas opuestas y desde
 * abajo (los aleros), y los de dos y cuatro aguas sobre plantas en L, T y U. Solo se ejecuta con
 * {@code KOMAEDIT_ROOF_RENDER=<carpeta>}; en el CI no hace nada.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_ROOF_RENDER", matches = ".+")
class RoofRenderTest {
    static final int ROOF_FULL = 0x8A5634;
    static final int ROOF_PIECE = 0xB0744A;
    static final int RIDGE = 0x4F4A52;
    static final int GABLE = 0xE8DEC9;
    static final int WINDOW = 0x9ED3EA;
    static final int HOUSE = 0xC9BFAE;
    static final int CORNER = 0x6E4B30;
    private static final int SCALE = 9;

    @Test
    void renderEveryType() throws IOException {
        List<BufferedImage> overview = new ArrayList<>();
        List<String> overviewLabels = new ArrayList<>();
        String[][] cases = {
                {"gable", "gable spruce_planks"},
                {"hip", "hip spruce_planks"},
                {"mansard", "mansard spruce_planks -b 2"},
                {"shed", "shed spruce_planks -lado s"},
                {"gablet", "gablet spruce_planks"},
                {"dome", "dome spruce_planks"},
                {"cone", "cone spruce_planks"},
                {"spire", "spire spruce_planks -lados 8"},
                {"pagoda", "pagoda spruce_planks -a 2"},
                {"flat", "flat spruce_planks -pretil 1"},
                {"gable_dormers", "gable spruce_planks -b 2 -c stone_bricks"},
                {"hip_1to2", "hip spruce_planks -p 1:2"},
                {"gable_2to1", "gable spruce_planks -p 2:1"},
                {"gable_30deg", "gable spruce_planks -p 30"},
        };
        for (String[] entry : cases) {
            boolean round = entry[0].equals("dome") || entry[0].equals("cone") || entry[0].equals("spire");
            Footprint footprint = round ? Footprint.rectangle(0, 0, 8, 8, 0)
                    : Footprint.rectangle(0, 0, 16, 10, 0);
            RoofResult result = RoofGenerator.generate(footprint, RoofSettings.parse(entry[1]), RoofWorld.AIR);
            Long2ObjectOpenHashMap<PieceRender.Cell> cells = cells(result, footprint, 5);
            List<BufferedImage> views = List.of(
                    PieceRender.render(cells, SCALE),
                    PieceRender.render(rotate(cells, 2), SCALE),
                    PieceRender.render(flip(cells), SCALE));
            String label = "/komaedit roof " + entry[1] + " · " + result.size() + " bloques, " + result.pieceCount()
                    + " piezas" + (result.mismatches() > 0 ? " · " + result.mismatches() + " desajustes" : "");
            write(PieceRender.sheet(views, List.of(label, "desde el lado contrario", "desde abajo (aleros)"), 3),
                    "tejado_" + entry[0] + ".png");
            overview.add(views.getFirst());
            overviewLabels.add(entry[1]);
        }
        write(PieceRender.sheet(overview, overviewLabels, 4), "tejados_todos.png");
    }

    @Test
    void renderDetails() throws IOException {
        for (String[] entry : new String[][]{
                {"detalle_buhardillas", "gable spruce_planks -b 2 -c stone_bricks"},
                {"detalle_pagoda", "pagoda spruce_planks -a 2"},
                {"detalle_holandes", "gablet spruce_planks -q 3"},
                {"detalle_unagua", "shed spruce_planks -lado s -b 2"},
                {"detalle_mansarda", "mansard spruce_planks -b 2"}}) {
            Footprint footprint = Footprint.rectangle(0, 0, 16, 10, 0);
            RoofResult result = RoofGenerator.generate(footprint, RoofSettings.parse(entry[1]), RoofWorld.AIR);
            Long2ObjectOpenHashMap<PieceRender.Cell> cells = cells(result, footprint, 5);
            write(PieceRender.sheet(List.of(PieceRender.render(cells, 18), PieceRender.render(rotate(cells, 1), 18)),
                    List.of("/komaedit roof " + entry[1] + " · " + result.mismatches() + " desajustes",
                            "girado"), 2), entry[0] + ".png");
        }
    }

    @Test
    void renderRoundRoofs() throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String flags : List.of("cone -p 1:1", "cone", "spire -lados 8", "spire -lados 4 -p 2:1",
                "dome", "dome -h 4")) {
            Footprint footprint = Footprint.rectangle(0, 0, 10, 10, 0);
            RoofResult result = RoofGenerator.generate(footprint, RoofSettings.parse(flags), RoofWorld.AIR);
            Long2ObjectOpenHashMap<PieceRender.Cell> cells = cells(result, footprint, 4);
            images.add(PieceRender.render(cells, 16));
            labels.add(flags + " · " + result.mismatches() + " desajustes");
            images.add(PieceRender.render(flip(cells), 16));
            labels.add("desde abajo");
        }
        write(PieceRender.sheet(images, labels, 4), "tejados_redondos.png");
    }

    @Test
    void renderCompoundPlans() throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String plan : List.of("L", "T", "U")) {
            Footprint footprint = RoofTestPlans.plan(plan, 0);
            for (String flags : List.of("gable", "hip", "gablet")) {
                RoofResult result = RoofGenerator.generate(footprint, RoofSettings.parse(flags), RoofWorld.AIR);
                Long2ObjectOpenHashMap<PieceRender.Cell> cells = cells(result, footprint, 5);
                images.add(PieceRender.render(cells, 7));
                labels.add("planta " + plan + " · " + flags + (result.mismatches() > 0
                        ? " · " + result.mismatches() + " desajustes" : ""));
                images.add(PieceRender.render(rotate(cells, 2), 7));
                labels.add("planta " + plan + " · " + flags + " · al revés");
            }
        }
        write(PieceRender.sheet(images, labels, 4), "tejados_plantas_LTU.png");
    }

    /** El tejado y, debajo, la casa: muros de {@code wallHeight} bloques con las esquinas oscuras. */
    static Long2ObjectOpenHashMap<PieceRender.Cell> cells(RoofResult result, Footprint footprint, int wallHeight) {
        Long2ObjectOpenHashMap<PieceRender.Cell> cells = new Long2ObjectOpenHashMap<>();
        int base = footprint.baseY();
        for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
            for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
                if (!footprint.isBoundary(x, z)) {
                    continue;
                }
                boolean corner = (footprint.contains(x + 1, z) ^ footprint.contains(x - 1, z))
                        && (footprint.contains(x, z + 1) ^ footprint.contains(x, z - 1))
                        || !footprint.contains(x + 1, z) && !footprint.contains(x - 1, z)
                        || !footprint.contains(x, z + 1) && !footprint.contains(x, z - 1);
                for (int y = base - wallHeight; y < base; y++) {
                    cells.put(PackedBlockPlan.pack(x, y, z), new PieceRender.Cell(SmoothPiece.FULL,
                            corner ? CORNER : HOUSE));
                }
            }
        }
        for (int index = 0; index < result.size(); index++) {
            long key = result.positions()[index];
            byte piece = result.pieces()[index];
            int color = switch (result.materials()[index]) {
                case RoofResult.RIDGE -> RIDGE;
                case RoofResult.WALL -> GABLE;
                case RoofResult.WINDOW_X, RoofResult.WINDOW_Z -> WINDOW;
                default -> piece == SmoothPiece.FULL ? ROOF_FULL : ROOF_PIECE;
            };
            cells.put(key, new PieceRender.Cell(piece, color));
        }
        return cells;
    }

    /** Gira el conjunto {@code quarters} cuartos de vuelta sobre el eje vertical, piezas incluidas. */
    static Long2ObjectOpenHashMap<PieceRender.Cell> rotate(Long2ObjectOpenHashMap<PieceRender.Cell> cells,
                                                         int quarters) {
        Long2ObjectOpenHashMap<PieceRender.Cell> rotated = new Long2ObjectOpenHashMap<>(cells.size());
        for (var entry : cells.long2ObjectEntrySet()) {
            int x = PackedBlockPlan.unpackX(entry.getLongKey());
            int y = PackedBlockPlan.unpackY(entry.getLongKey());
            int z = PackedBlockPlan.unpackZ(entry.getLongKey());
            byte piece = entry.getValue().piece();
            for (int turn = 0; turn < quarters; turn++) {
                int previous = x;
                x = -z;
                z = previous;
                if (SmoothPiece.isStairs(piece)) {
                    piece = SmoothPiece.stairs((SmoothPiece.facing(piece) + 1) & 3, SmoothPiece.isTop(piece),
                            SmoothPiece.shape(piece));
                }
            }
            rotated.put(PackedBlockPlan.pack(x, y, z), new PieceRender.Cell(piece, entry.getValue().color()));
        }
        return rotated;
    }

    /** Boca abajo, para ver los aleros por debajo. */
    static Long2ObjectOpenHashMap<PieceRender.Cell> flip(Long2ObjectOpenHashMap<PieceRender.Cell> cells) {
        Long2ObjectOpenHashMap<PieceRender.Cell> flipped = new Long2ObjectOpenHashMap<>(cells.size());
        for (var entry : cells.long2ObjectEntrySet()) {
            int x = PackedBlockPlan.unpackX(entry.getLongKey());
            int y = PackedBlockPlan.unpackY(entry.getLongKey());
            int z = PackedBlockPlan.unpackZ(entry.getLongKey());
            byte piece = entry.getValue().piece();
            if (SmoothPiece.isStairs(piece)) {
                piece = SmoothPiece.stairs(SmoothPiece.facing(piece), !SmoothPiece.isTop(piece),
                        SmoothPiece.shape(piece));
            } else if (SmoothPiece.isSlab(piece)) {
                piece = SmoothPiece.slab(!SmoothPiece.isTop(piece));
            }
            flipped.put(PackedBlockPlan.pack(x, -y, z), new PieceRender.Cell(piece, entry.getValue().color()));
        }
        return flipped;
    }

    static void write(BufferedImage image, String name) throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_ROOF_RENDER"));
        Files.createDirectories(folder);
        ImageIO.write(image, "png", folder.resolve(name).toFile());
    }
}
