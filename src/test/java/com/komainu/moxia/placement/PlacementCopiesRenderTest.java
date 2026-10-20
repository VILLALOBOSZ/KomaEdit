package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.smooth.PieceRender;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Renders de {@code place -copias}: escalera de caracol, corona de columnas y hélice. Solo se ejecuta con
 * {@code KOMAEDIT_CLIPBOARD_RENDER=<carpeta>}; en el CI no hace nada.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_CLIPBOARD_RENDER", matches = ".+")
class PlacementCopiesRenderTest {
    private static final int[] COLORS = {0x9C9C9C, 0xB0744A, 0x6E4B30};

    @Test
    void renderCopies() throws IOException {
        VoxelStructure step = VoxelStructure.builder(5, 1, 2, 0, 0, 0)
                .set(0, 0, 0, 2).set(1, 0, 0, 1).set(2, 0, 0, 1).set(3, 0, 0, 1).set(4, 0, 0, 1)
                .set(0, 0, 1, 2).set(1, 0, 1, 1).set(2, 0, 1, 1).set(3, 0, 1, 1).set(4, 0, 1, 1)
                .build(false, 1, 0, "peldaño");
        VoxelStructure.Builder columnBuilder = VoxelStructure.builder(3, 10, 3, -1, 0, -1);
        for (int y = 0; y < 10; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (y == 0 || y == 9 || x == 0 || z == 0) {
                        columnBuilder.set(x, y, z, y == 0 || y == 9 ? 2 : 0);
                    }
                }
            }
        }
        VoxelStructure column = columnBuilder.build(false, 1, 0, "columna");
        VoxelStructure bar = VoxelStructure.builder(4, 1, 1, 0, 0, 0)
                .set(0, 0, 0, 0).set(1, 0, 0, 0).set(2, 0, 0, 0).set(3, 0, 0, 1).build(false, 1, 0, "barra");

        BufferedImage spiral = render(new PlacementCopies(23, new Vec3(0, 1, 0), 0, new Vec3(0, 0, 0), 22.5)
                .expand(base(step)));
        BufferedImage crown = render(new PlacementCopies(7, new Vec3(0, 0, 0), 45, new Vec3(10, 0, 0), 0)
                .expand(base(column)));
        BufferedImage helix = render(new PlacementCopies(31, new Vec3(0, 0.5, 0), 15, new Vec3(6, 0, 0), 0)
                .expand(base(bar)));
        write(PieceRender.sheet(List.of(spiral, crown, helix), List.of(
                "-copias 23 -desplazar 0,1,0 -torcer 22.5",
                "-copias 7 -revolver 45 -pivote 10,0,0",
                "-copias 31 -desplazar 0,0.5,0 -revolver 15 -pivote 6,0,0"), 3), "copias_place.png");
    }

    private static Placement base(Structure structure) {
        return new Placement(structure, new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, new Vec3(1, 1, 1), 1L);
    }

    private static BufferedImage render(List<Placement> placements) {
        PlacementVoxelizer.Result result = PlacementVoxelizer.voxelize(placements,
                PlacementVoxelizer.Options.defaults());
        Long2ObjectOpenHashMap<PieceRender.Cell> cells = new Long2ObjectOpenHashMap<>();
        for (Long2LongMap.Entry entry : Long2LongMaps.fastIterable(result.cells())) {
            int palette = PlacementVoxelizer.Result.palette(entry.getLongValue());
            cells.put(entry.getLongKey(), new PieceRender.Cell(SmoothPiece.FULL,
                    COLORS[Math.floorMod(palette, COLORS.length)]));
        }
        return PieceRender.render(cells, 8);
    }

    private static void write(BufferedImage image, String name) throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_CLIPBOARD_RENDER"));
        Files.createDirectories(folder);
        ImageIO.write(image, "png", folder.resolve(name).toFile());
    }
}
