package com.komainu.moxia.nature;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.gradient.PaletteSpec;
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
 * Renders de comprobación fuera del juego. Solo se ejecuta con {@code KOMAEDIT_NATURE_RENDER=<carpeta>}: deja allí
 * un PNG por especie (isométrico y alzado) sobre una colina, una hoja con todas y los tiempos.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_NATURE_RENDER", matches = ".+")
class NatureRenderTest {
    private static final double SCALE = 9.0;

    @Test
    void rendersEverySpecies() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_NATURE_RENDER"));
        Files.createDirectories(folder);
        String only = System.getenv("KOMAEDIT_NATURE_ONLY");
        List<BufferedImage> sheet = new ArrayList<>();
        StringBuilder report = new StringBuilder();
        for (TreeSpecies species : TreeSpecies.values()) {
            if (only != null && !only.isBlank() && !only.contains(species.id())) {
                continue;
            }
            String options = species.id() + " " + System.getenv().getOrDefault("KOMAEDIT_NATURE_FLAGS", "");
            for (long seed : new long[]{7L, 21L}) {
                TreeSettings settings = TreeSettings.parse(options).withSeed(seed);
                FakeTerrain terrain = FakeTerrain.hill(64, 34.0);
                int baseY = terrain.height(0, 0) + 1;
                int reach = TreeGrower.reach(settings);
                Bounds bounds = Bounds.around(0, baseY, 0, reach, 16, settings.height() + 16);
                long start = System.nanoTime();
                NatureVoxels voxels = TreeGrower.grow(settings, terrain, bounds, 0, baseY, 0, seed, 600_000,
                        () -> false);
                long millis = (System.nanoTime() - start) / 1_000_000;
                VoxelRender render = new VoxelRender();
                ground(render, terrain, voxels, Math.min(reach, 14));
                paint(render, voxels, settings);
                String title = species.displayName() + " · " + settings.height() + " · semilla " + seed + " · "
                        + voxels.count(NatureVoxels.Role.WOOD) + " madera, " + voxels.count(NatureVoxels.Role.ROOT)
                        + " raíz, " + (voxels.count(NatureVoxels.Role.LEAF) + voxels.count(NatureVoxels.Role.HANGING))
                        + " hojas · " + millis + " ms";
                BufferedImage iso = render.iso(SCALE, title);
                BufferedImage front = render.front(SCALE * 0.9, "alzado");
                ImageIO.write(VoxelRender.beside(iso, front), "png",
                        folder.resolve("arbol_" + species.id() + "_" + seed + ".png").toFile());
                if (seed == 7L) {
                    sheet.add(render.iso(5.0, species.displayName()));
                }
                report.append(title).append(System.lineSeparator());
            }
        }
        if (!sheet.isEmpty()) {
            ImageIO.write(VoxelRender.grid(4, sheet.toArray(BufferedImage[]::new)), "png",
                    folder.resolve("arboles.png").toFile());
        }
        Files.writeString(folder.resolve("arboles.txt"), report);
    }

    @Test
    void rendersRocks() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_NATURE_RENDER"));
        Files.createDirectories(folder);
        List<BufferedImage> sheet = new ArrayList<>();
        StringBuilder report = new StringBuilder();
        String[] variants = {"boulder", "boulder 6 -u 0.6", "crag", "crag 10 -u 0.5 -e 2", "spire", "spire 4 -a 4 -i 12",
                "boulder 3 -c 0 -r 0.8", "crag 6 -b granite,diorite,calcite"};
        for (int index = 0; index < variants.length; index++) {
            String options = variants[index];
            RockSettings settings = RockSettings.parse(options).withSeed(5L + index);
            FakeTerrain terrain = FakeTerrain.slope(64, 0.25);
            int baseY = terrain.height(0, 0) + 1;
            int reach = RockShaper.reach(settings);
            int above = (int) Math.ceil(RockShaper.height(settings)) + 4;
            Bounds bounds = Bounds.around(0, baseY, 0, reach, reach + 8, above);
            long start = System.nanoTime();
            NatureVoxels voxels = RockShaper.shape(settings, terrain, bounds, 0, baseY, 0, 5L + index, 400_000,
                    () -> false);
            long millis = (System.nanoTime() - start) / 1_000_000;
            VoxelRender render = new VoxelRender();
            ground(render, terrain, voxels, reach);
            List<String> palette = PaletteSpec.parse(settings.paletteMaterial());
            for (long key : voxels.sortedKeys()) {
                int info = voxels.info(key);
                String block = NatureVoxels.role(info) == NatureVoxels.Role.MOSS ? settings.moss()
                        : palette.get(Math.min(palette.size() - 1, NatureVoxels.shade(info) * palette.size() / 256));
                render.put(PackedBlockPlan.unpackX(key), PackedBlockPlan.unpackY(key), PackedBlockPlan.unpackZ(key),
                        VoxelRender.color(block));
            }
            String title = options + " · " + voxels.size() + " bloques · " + millis + " ms";
            BufferedImage iso = render.iso(10.0, title);
            ImageIO.write(VoxelRender.beside(iso, render.front(9.0, "alzado")), "png",
                    folder.resolve("roca_" + index + ".png").toFile());
            sheet.add(render.iso(7.0, options));
            report.append(title).append(System.lineSeparator());
        }
        ImageIO.write(VoxelRender.grid(4, sheet.toArray(BufferedImage[]::new)), "png",
                folder.resolve("rocas.png").toFile());
        Files.writeString(folder.resolve("rocas.txt"), report);
    }

    static void ground(VoxelRender render, FakeTerrain terrain, NatureVoxels voxels, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                int top = terrain.height(x, z);
                for (int y = top - 2; y <= top; y++) {
                    if (!voxels.contains(x, y, z)) {
                        render.put(x, y, z, VoxelRender.color(y == top ? "grass" : "dirt"));
                    }
                }
            }
        }
    }

    private static void paint(VoxelRender render, NatureVoxels voxels, TreeSettings settings) {
        List<String> trunk = PaletteSpec.parse(settings.trunkMaterial());
        List<String> leaves = settings.leafMaterial() == null ? List.of("oak_leaves")
                : PaletteSpec.parse(settings.leafMaterial());
        List<String> roots = settings.rootMaterial() == null ? trunk : PaletteSpec.parse(settings.rootMaterial());
        for (long key : voxels.sortedKeys()) {
            int info = voxels.info(key);
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            List<String> palette = switch (NatureVoxels.role(info)) {
                case WOOD -> trunk;
                case ROOT -> roots;
                default -> leaves;
            };
            String block = palette.get((int) (TreeBuilder.unit(key, 5L) * palette.size()));
            int rgb = VoxelRender.color(block);
            if (NatureVoxels.role(info) == NatureVoxels.Role.WOOD && NatureVoxels.axis(info) != 1) {
                rgb = VoxelRender.scaleColor(rgb, 1.12);
            }
            render.put(x, y, z, rgb);
        }
    }
}
