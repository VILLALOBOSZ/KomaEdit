package com.komainu.moxia.loft;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.smooth.PieceRender;
import com.komainu.moxia.shape.smooth.SmoothPiece;
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
 * Renders de lofts fuera del juego: un toldo colgante entre cuatro postes, una vela entre tres curvas, un puente
 * colgante y una pared curva. Solo se ejecuta con {@code KOMAEDIT_ROOF_RENDER=<carpeta>}; en el CI no hace nada.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_ROOF_RENDER", matches = ".+")
class LoftRenderTest {
    private static final int POST = 0x5B3A22;
    private static final int CABLE = 0x3C3C44;

    @Test
    void renderLofts() throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        // Toldo: dos vigas de 16 bloques a 6 de alto, separadas 10, con la lona colgando entre ellas.
        List<List<BlockVector3>> beams = List.of(line(0, 6, 0, 16, 6, 0), line(0, 6, 10, 16, 6, 10));
        for (String flags : List.of("-i catenaria -comba 3", "-i catenaria -comba 3 -w SlabsAndStairs")) {
            Long2ObjectOpenHashMap<PieceRender.Cell> cells = render(beams, flags, 0xE9E4D6);
            posts(cells, new int[][]{{0, 0}, {16, 0}, {0, 10}, {16, 10}}, 6);
            images.add(PieceRender.render(cells, 12));
            labels.add("/komaedit loft white_wool " + flags);
        }

        // Vela: tres curvas, la del centro más alta y adelantada.
        List<List<BlockVector3>> sail = List.of(
                line(0, 2, 0, 18, 4, 0),
                List.of(BlockVector3.at(0, 9, 6), BlockVector3.at(9, 12, 8), BlockVector3.at(18, 10, 6)),
                line(0, 3, 14, 18, 2, 14));
        images.add(PieceRender.render(render(sail, "-i catmull -w SlabsAndStairs", 0xD9C7A3), 12));
        labels.add("vela: loft a b c -i catmull -w SlabsAndStairs");

        // Puente colgante: dos cables que cuelgan (como path catenary) y el tablero lineal entre ellos.
        List<BlockVector3> left = new ArrayList<>();
        List<BlockVector3> right = new ArrayList<>();
        for (int x = 0; x <= 28; x += 2) {
            double t = x / 28.0;
            int y = (int) Math.round(12 - 6 * 4 * t * (1 - t));
            left.add(BlockVector3.at(x, y, 0));
            right.add(BlockVector3.at(x, y, 4));
        }
        Long2ObjectOpenHashMap<PieceRender.Cell> bridge = render(List.of(left, right), "-i lineal -w SlabsAndStairs",
                0x9C6B3E);
        posts(bridge, new int[][]{{0, -1}, {28, -1}, {0, 5}, {28, 5}}, 15);
        for (BlockVector3 point : left) {
            bridge.put(PackedBlockPlan.pack(point.x(), point.y() + 2, -1), new PieceRender.Cell(SmoothPiece.wall(),
                    CABLE));
            bridge.put(PackedBlockPlan.pack(point.x(), point.y() + 2, 5), new PieceRender.Cell(SmoothPiece.wall(),
                    CABLE));
        }
        images.add(PieceRender.render(bridge, 10));
        labels.add("puente: loft cable1 cable2 -i lineal -w SlabsAndStairs");

        // Pared curva: la misma curva en el suelo y nueve bloques más arriba.
        List<BlockVector3> ground = List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(8, 0, 5),
                BlockVector3.at(16, 0, 0), BlockVector3.at(24, 0, 5));
        List<BlockVector3> top = new ArrayList<>();
        for (BlockVector3 point : ground) {
            top.add(point.add(0, 9, 0));
        }
        images.add(PieceRender.render(render(List.of(ground, top), "-i catmull -w SlabsAndStairs", 0xB9B2A6), 10));
        labels.add("pared curva: loft suelo arriba -i catmull -w SlabsAndStairs");

        write(PieceRender.sheet(images, labels, 2), "lofts.png");
    }

    private static Long2ObjectOpenHashMap<PieceRender.Cell> render(List<List<BlockVector3>> curves, String flags,
                                                                  int color) {
        LoftResult result = LoftGenerator.generate(curves, LoftSettings.parse(flags, name -> false));
        Long2ObjectOpenHashMap<PieceRender.Cell> cells = new Long2ObjectOpenHashMap<>();
        for (int index = 0; index < result.size(); index++) {
            byte piece = result.pieces()[index];
            int shade = piece == SmoothPiece.FULL ? color : lighter(color);
            cells.put(result.positions()[index], new PieceRender.Cell(piece, shade));
        }
        return cells;
    }

    private static void posts(Long2ObjectOpenHashMap<PieceRender.Cell> cells, int[][] places, int height) {
        for (int[] place : places) {
            for (int y = -1; y <= height; y++) {
                cells.putIfAbsent(PackedBlockPlan.pack(place[0], y, place[1]),
                        new PieceRender.Cell(SmoothPiece.FULL, POST));
            }
        }
    }

    private static int lighter(int color) {
        int r = Math.min(255, (color >> 16 & 0xFF) + 18);
        int g = Math.min(255, (color >> 8 & 0xFF) + 18);
        int b = Math.min(255, (color & 0xFF) + 18);
        return r << 16 | g << 8 | b;
    }

    private static List<BlockVector3> line(int x1, int y1, int z1, int x2, int y2, int z2) {
        return List.of(BlockVector3.at(x1, y1, z1), BlockVector3.at(x2, y2, z2));
    }

    private static void write(BufferedImage image, String name) throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_ROOF_RENDER"));
        Files.createDirectories(folder);
        ImageIO.write(image, "png", folder.resolve(name).toFile());
    }
}
