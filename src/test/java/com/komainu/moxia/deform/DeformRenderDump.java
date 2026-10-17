package com.komainu.moxia.deform;

import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.region.RegionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Vuelca a disco cada modo aplicado a formas de ejemplo (montaña, toro, terreno) para revisarlos con renders.
 * Solo se ejecuta con {@code KOMAEDIT_RENDER_DIR} definido; en el CI no hace nada.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_RENDER_DIR", matches = ".+")
class DeformRenderDump {
    private static final int SIZE = 48;

    @Test
    void dumpEveryMode() throws IOException {
        Path directory = Path.of(System.getenv("KOMAEDIT_RENDER_DIR"));
        Files.createDirectories(directory);
        RegionSnapshot mountain = Shapes.snapshot(SIZE, 32, SIZE, 2, DeformRenderDump::mountain);
        RegionSnapshot torus = Shapes.snapshot(SIZE, 20, SIZE, 2, DeformRenderDump::torus);
        Volume mountainVolume = Volume.of(mountain);
        Volume torusVolume = Volume.of(torus);

        dump(directory, "montaña", mountainVolume, null, null);
        dump(directory, "toro", torusVolume, null, null);
        dump(directory, "deform_hexagonalize", torusVolume, Deformations.hexagonalize(torusVolume, 8, 0, 0, 0, 60), null);
        dump(directory, "deform_hexagonalize_hueco_giro", torusVolume,
                Deformations.hexagonalize(torusVolume, 8, 1.5, 30, 0, 60), null);
        dump(directory, "deform_hexagonalize_montaña_w", mountainVolume,
                Deformations.hexagonalize(mountainVolume, 7, 0, 0, 0, 60), "SlabsAndStairs");
        dump(directory, "deform_noise", mountainVolume, Deformations.noise(mountainVolume,
                NoiseSpec.parse("Perlin(F:0.08)"), 4, 1, 3, false, false), null);
        dump(directory, "deform_noise_w", mountainVolume, Deformations.noise(mountainVolume,
                NoiseSpec.parse("Perlin(F:0.08)"), 4, 1, 3, false, false), "SlabsAndStairs");
        dump(directory, "deform_voronoialize", torusVolume, Deformations.voronoialize(torusVolume, 9, 0, 69_420), null);
        dump(directory, "deform_voronoialize_hueco", torusVolume,
                Deformations.voronoialize(torusVolume, 9, 2, 69_420), null);
        dump(directory, "deform_voronoialize2_150", torusVolume, Deformations.voronoialize2(torusVolume, 150, 0,
                69_420, 15, 5, Shapes.NO_CANCEL), null);
        dump(directory, "deform_voronoialize2_15", torusVolume, Deformations.voronoialize2(torusVolume, 15, 0,
                69_420, 15, 5, Shapes.NO_CANCEL), null);
        dump(directory, "deform_voxelize", mountainVolume, Deformations.voxelize(mountainVolume,
                new double[]{4, 4, 4}, 0, 0.6, Deformations.Axis.Y, Deformations.Axis.NEG_X, 5, false, false,
                Shapes.NO_CANCEL), null);

        RegionSnapshot rotateCapture = Shapes.snapshot(SIZE, 32, SIZE, 14, (x, y, z) -> x < 0 || z < 0 || x >= SIZE
                || z >= SIZE || y < 0 || y >= 32 ? Shapes.AIR : mountain(x, y, z));
        Volume rotateVolume = Volume.of(rotateCapture);
        double[] center = {SIZE / 2.0, 16, SIZE / 2.0};
        dump(directory, "deform_rotate", rotateVolume, Deformations.rotate(rotateVolume, new double[]{0, 0, 1}, 30,
                center), null, Deformations.rotatedBounds(rotateVolume.selectionBounds(), new double[]{0, 0, 1}, 30,
                center));

        RegionSnapshot terrain = terrainCapture();
        Volume terrainVolume = Volume.of(terrain);
        int[] mapBounds = {0, 0, 0, 63, 3, 31};
        dump(directory, "terreno", terrainVolume, null, null);
        Projection cylinder = new Projection(new Projection.Settings(false, false, null, Double.NaN,
                Projection.AxisMap.DEFAULT, Deformations.Axis.Y, Deformations.Axis.X, false, false, false, false,
                null), mapBounds, 320);
        dump(directory, "deform_cylindrify", terrainVolume, cylinder.field(terrainVolume), null, cylinder.box());
        Projection sphere = new Projection(new Projection.Settings(true, false, null, Double.NaN,
                Projection.AxisMap.DEFAULT, Deformations.Axis.Y, Deformations.Axis.X, false, true, false, false,
                null), mapBounds, 320);
        dump(directory, "deform_spherify", terrainVolume, sphere.field(terrainVolume), null, sphere.box());
        Projection lambert = new Projection(new Projection.Settings(true, true, null, Double.NaN,
                Projection.AxisMap.DEFAULT, Deformations.Axis.Y, Deformations.Axis.X, false, true, false, false,
                null), mapBounds, 320);
        dump(directory, "deform_spherify_laea", terrainVolume, lambert.field(terrainVolume), null, lambert.box());

        RegionSnapshot surfaceCapture = Shapes.snapshot(SIZE, 32, SIZE, 8, (x, y, z) -> y < 0 ? Shapes.STONE
                : mountain(Math.clamp(x, 0, SIZE - 1), y, Math.clamp(z, 0, SIZE - 1)));
        Volume surfaceVolume = Volume.of(surfaceCapture);
        surface(directory, "surface_fuzzify", surfaceVolume, 2, SurfaceOps.Displacement.white(1), 0, 0, false, false,
                false);
        surface(directory, "surface_rockify", surfaceVolume, 4, SurfaceOps.Displacement.perlin(10, 1, 1), 1, 4,
                false, false, false);
        surface(directory, "surface_voronoify", surfaceVolume, 4, SurfaceOps.Displacement.cells(12, 1), 0, 0,
                false, false, false);
        surface(directory, "surface_noisify", surfaceVolume, 3, SurfaceOps.Displacement.of(
                NoiseSpec.parse("Ce(F:0.1,cR:sub)"), 1, 1), 1, 4, false, false, false);
        surface(directory, "surface_rockify_c", surfaceVolume, 4, SurfaceOps.Displacement.perlin(10, 1, 1), 1, 4,
                true, false, false);
        surface(directory, "surface_fuzzify_t", surfaceVolume, 3, SurfaceOps.Displacement.white(1), 0, 0, false,
                false, true);

        Volume roughVolume = Volume.of(Shapes.snapshot(SIZE, 32, SIZE, 8, (x, y, z) -> y < 0 ? Shapes.STONE
                : rough(Math.clamp(x, 0, SIZE - 1), y, Math.clamp(z, 0, SIZE - 1))));
        dump(directory, "suavizar_antes", roughVolume, null, null);
        dump(directory, "smooth", roughVolume, SmoothingOps.smooth(roughVolume, new double[]{2, 2, 2}, 2, 0,
                Shapes.NO_CANCEL), null);
        dump(directory, "smooth_w", roughVolume, SmoothingOps.smooth(roughVolume, new double[]{2, 2, 2}, 2, 0,
                Shapes.NO_CANCEL), "SlabsAndStairs");
        dump(directory, "smoothblocks_slabs", roughVolume, SmoothingOps.smooth(roughVolume, new double[]{2, 2, 2}, 1,
                0, Shapes.NO_CANCEL), "Slabs");
        Volume torusMargin = Volume.of(Shapes.snapshot(SIZE, 20, SIZE, 4, (x, y, z) -> x < 0 || z < 0 || x >= SIZE
                || z >= SIZE || y < 0 || y >= 20 ? Shapes.AIR : torus(x, y, z)));
        dump(directory, "inflate", torusMargin, SmoothingOps.inflate(torusMargin, new double[]{2, 2, 2},
                Shapes.NO_CANCEL), null);
        dump(directory, "deflate", torusMargin, SmoothingOps.deflate(torusMargin, new double[]{2, 1, 2},
                Shapes.NO_CANCEL), null);
    }

    private static void surface(Path directory, String name, Volume volume, double radius,
                                SurfaceOps.Displacement displacement, int smoothRadius, int smoothIterations,
                                boolean carve, boolean topology, boolean keepTopology) throws IOException {
        dump(directory, name, volume, SurfaceOps.apply(volume, new SurfaceOps.Settings(radius, displacement,
                smoothRadius, smoothIterations, carve, topology, null, keepTopology), Shapes.NO_CANCEL), null);
    }

    private static void dump(Path directory, String name, Volume volume, DeformField field, String profile)
            throws IOException {
        dump(directory, name, volume, field, profile, volume.selectionBounds());
    }

    private static void dump(Path directory, String name, Volume volume, DeformField field, String profile,
                             int[] box) throws IOException {
        Shapes.Result result = field == null
                ? new Shapes.Result(volume)
                : Shapes.apply(volume, field, box, SmoothProfile.parse(profile));
        try (OutputStream file = Files.newOutputStream(directory.resolve(name + ".vox"));
             DataOutputStream out = new DataOutputStream(file)) {
            out.writeInt(volume.sizeX);
            out.writeInt(volume.sizeY);
            out.writeInt(volume.sizeZ);
            for (int index = 0; index < result.ids.length; index++) {
                out.writeByte(result.ids[index]);
            }
            out.write(result.pieces);
        }
    }

    static int mountain(int x, int y, int z) {
        double dx = (x - SIZE / 2.0) / (SIZE / 2.6);
        double dz = (z - SIZE / 2.0) / (SIZE / 2.6);
        double height = 3 + 24 * Math.exp(-(dx * dx + dz * dz)) + 2 * Math.sin(x * 0.4) * Math.cos(z * 0.3);
        if (y > height) {
            return Shapes.AIR;
        }
        return y > height - 1 ? Shapes.GRASS : y > height - 3 ? Shapes.DIRT : Shapes.STONE;
    }

    static int torus(int x, int y, int z) {
        double dx = x + 0.5 - SIZE / 2.0;
        double dz = z + 0.5 - SIZE / 2.0;
        double ring = Math.sqrt(dx * dx + dz * dz) - 15;
        double dy = y + 0.5 - 10;
        return ring * ring + dy * dy <= 7.5 * 7.5 ? Shapes.STONE : Shapes.AIR;
    }

    /** Montaña escalonada en bloques de 3, para ver el suavizado. */
    static int rough(int x, int y, int z) {
        int coarse = mountain(x / 4 * 4 + 2, y / 3 * 3 + 1, z / 4 * 4 + 2);
        return coarse == Shapes.AIR ? Shapes.AIR : mountain(x, y, z) == Shapes.AIR ? Shapes.STONE : mountain(x, y, z);
    }

    private static RegionSnapshot terrainCapture() {
        Function<int[], Integer> terrain = point -> {
            int x = point[0];
            int y = point[1];
            int z = point[2];
            if (y == 0) {
                return Shapes.DIRT;
            }
            double cell = Math.sin(x * 0.9) * Math.sin(z * 0.9);
            if (y == 1) {
                return cell > -0.2 ? Shapes.STONE : Shapes.DIRT;
            }
            if (y == 2) {
                return cell > 0.3 ? Shapes.STONE : Shapes.AIR;
            }
            return cell > 0.75 ? Shapes.GRASS : Shapes.AIR;
        };
        RegionSnapshot.Builder builder = RegionSnapshot.builder(-6, 0, -6, 76, 40, 44)
                .state(new RegionSnapshot.StateInfo(0, null, "minecraft:air", true, false, false))
                .state(new RegionSnapshot.StateInfo(Shapes.STONE, null, "minecraft:stone", false, false, true))
                .state(new RegionSnapshot.StateInfo(Shapes.DIRT, null, "minecraft:dirt", false, false, true))
                .state(new RegionSnapshot.StateInfo(Shapes.GRASS, null, "minecraft:grass_block", false, false, true));
        for (int x = -6; x < 70; x++) {
            for (int y = 0; y < 40; y++) {
                for (int z = -6; z < 38; z++) {
                    boolean inside = x >= 0 && x < 64 && y < 4 && z >= 0 && z < 32;
                    builder.set(x, y, z, inside ? terrain.apply(new int[]{x, y, z}) : 0, inside);
                }
            }
        }
        return builder.build();
    }
}
