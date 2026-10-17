package com.komainu.moxia.ivy;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IvyGrowerTest {
    private static final List<IvyGrower.Point> HOUSE_SEEDS = List.of(
            new IvyGrower.Point(2, 0, -1),
            new IvyGrower.Point(13, 0, -1),
            new IvyGrower.Point(17, 0, 4)
    );

    @Test
    void everyBlockHangsFromRealSupport() {
        for (IvyStyle style : IvyStyle.values()) {
            for (long seed = 1; seed <= 6; seed++) {
                FakeTerrain terrain = FakeTerrain.house();
                IvySettings settings = IvySettings.defaults().withStyle(style).withLength(40.0);
                IvyResult result = IvyGrower.grow(terrain, HOUSE_SEEDS, settings, seed, 50_000, () -> false);
                assertFalse(result.blocks().isEmpty(), style + " no creció");
                assertSupported(terrain, result, style + " semilla " + seed);
            }
        }
    }

    @Test
    void sameSeedGrowsTheSameIvy() {
        IvySettings settings = IvySettings.defaults().withStyle(IvyStyle.SELVATICO).withLength(36.0);
        IvyResult first = IvyGrower.grow(FakeTerrain.house(), HOUSE_SEEDS, settings, 42L, 50_000, () -> false);
        IvyResult second = IvyGrower.grow(FakeTerrain.house(), HOUSE_SEEDS, settings, 42L, 50_000, () -> false);
        IvyResult other = IvyGrower.grow(FakeTerrain.house(), HOUSE_SEEDS, settings, 43L, 50_000, () -> false);
        assertEquals(first.blocks(), second.blocks());
        assertNotEquals(first.blocks(), other.blocks());
    }

    @Test
    void neverGrowsInsideSolids() {
        FakeTerrain terrain = new FakeTerrain(-20, -1, -20, 20, 30, 20)
                .fill(-20, -1, -20, 20, -1, 20, FakeTerrain.SOLID)
                .fill(-3, 0, -3, 3, 12, 3, FakeTerrain.SOLID)
                .fill(-1, 4, -4, 1, 6, -4, FakeTerrain.SOLID);
        IvySettings settings = IvySettings.parse("selvatico -l 60 -b 1 -g 1 -n 4");
        IvyResult result = IvyGrower.grow(terrain, List.of(new IvyGrower.Point(0, 0, -4)), settings, 7L, 50_000,
                () -> false);
        assertTrue(result.blocks().size() > 40, "creció poco: " + result.blocks().size());
        Map<Long, IvyBlock> seen = new HashMap<>();
        for (IvyBlock block : result.blocks()) {
            assertEquals(0, terrain.kind(block.x(), block.y(), block.z()), "dentro de un sólido: " + block);
            assertEquals(null, seen.put(IvyGrower.key(block.x(), block.y(), block.z()), block), "repetido: " + block);
        }
    }

    @Test
    void climbsTheWallAndCrossesTheRoof() {
        IvySettings settings = IvySettings.parse("hiedra -l 60 -b 0.5");
        IvyResult result = IvyGrower.grow(FakeTerrain.house(), HOUSE_SEEDS, settings, 3L, 50_000, () -> false);
        int top = result.blocks().stream().mapToInt(IvyBlock::y).max().orElseThrow();
        assertTrue(top >= 12, "no llegó al tejado: " + top);
        assertTrue(result.count(IvyMaterial.VINE) > 30);
        assertTrue(result.count(IvyMaterial.OAK_LEAVES) + result.count(IvyMaterial.AZALEA_LEAVES) > 0);
    }

    @Test
    void hangsFromBottomEdgesAndCeilings() {
        // Losa voladiza: una tabla a 8 de altura que sale 6 bloques de un muro.
        FakeTerrain terrain = new FakeTerrain(-10, -1, -10, 10, 20, 10)
                .fill(-10, -1, -10, 10, -1, 10, FakeTerrain.SOLID)
                .fill(-6, 0, 0, 6, 9, 0, FakeTerrain.SOLID)
                .fill(-6, 8, -6, 6, 8, -1, FakeTerrain.SOLID);
        IvySettings settings = IvySettings.parse("cueva -l 50 -g 1 -b 0.8 -n 4");
        int strands = 0;
        int hanging = 0;
        for (long seed = 1; seed <= 8; seed++) {
            IvyResult result = IvyGrower.grow(terrain, List.of(new IvyGrower.Point(0, 0, -1)), settings, seed,
                    50_000, () -> false);
            assertSupported(terrain, result, "voladizo " + seed);
            strands += (int) result.count(IvyMaterial.CAVE_VINES);
            for (IvyBlock block : result.blocks()) {
                if (block.material() == IvyMaterial.VINE && ownSupport(terrain, block) != block.faces()) {
                    hanging++;
                }
            }
        }
        assertTrue(strands > 0, "no colgó ninguna tira de bayas del voladizo");
        assertTrue(hanging > 0, "ninguna enredadera colgó de un borde");
    }

    @Test
    void respectsTheBlockLimit() {
        IvySettings settings = IvySettings.parse("-l 120 -b 1 -n 8");
        IvyResult result = IvyGrower.grow(FakeTerrain.house(), HOUSE_SEEDS, settings, 5L, 60, () -> false);
        assertTrue(result.truncated());
        assertTrue(result.blocks().size() <= 60);
        assertEquals(60, result.blocks().size());
    }

    @Test
    void staysInsideTheKnownWorld() {
        FakeTerrain house = FakeTerrain.house();
        FakeTerrain clipped = new FakeTerrain(-2, -1, -3, 9, 7, 3);
        clipped.blocks.putAll(house.blocks);
        IvyResult result = IvyGrower.grow(clipped, List.of(new IvyGrower.Point(2, 0, -1)),
                IvySettings.parse("-l 80 -b 1"), 9L, 50_000, () -> false);
        assertFalse(result.blocks().isEmpty());
        for (IvyBlock block : result.blocks()) {
            assertTrue(clipped.inside(block.x(), block.y(), block.z()), "fuera de la captura: " + block);
        }
    }

    @Test
    void cancels() {
        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> IvyGrower.grow(FakeTerrain.house(), HOUSE_SEEDS,
                IvySettings.parse("-l 200 -n 8 -b 1"), 1L, 50_000, () -> checks.incrementAndGet() > 3));
        assertEquals(4, checks.get());
    }

    @Test
    void seedsWithoutSurfaceAreSkipped() {
        FakeTerrain terrain = FakeTerrain.house();
        IvyResult result = IvyGrower.grow(terrain, List.of(new IvyGrower.Point(-10, 20, -12),
                new IvyGrower.Point(2, 0, -1)), IvySettings.defaults(), 1L, 50_000, () -> false);
        assertEquals(1, result.seedsUsed());
        assertEquals(1, result.seedsSkipped());
        IvyResult none = IvyGrower.grow(terrain, List.of(new IvyGrower.Point(-10, 20, -12)), IvySettings.defaults(),
                1L, 50_000, () -> false);
        assertTrue(none.blocks().isEmpty());
    }

    @Test
    void parsesSettings() {
        IvySettings settings = IvySettings.parse("selvático -l 40 -b 0,7 -g 0.2 -n 3 -seed 99");
        assertEquals(IvyStyle.SELVATICO, settings.style());
        assertEquals(40.0, settings.length());
        assertEquals(0.7, settings.branching(), 1.0E-9);
        assertEquals(0.2, settings.hang(), 1.0E-9);
        assertEquals(3, settings.stems());
        assertEquals(99L, settings.seed().orElseThrow());
        assertEquals(IvyStyle.FLORIDO, IvySettings.parse("-estilo flores").style());
        assertThrows(IllegalArgumentException.class, () -> IvySettings.parse("-l 2"));
        assertThrows(IllegalArgumentException.class, () -> IvySettings.parse("-b"));
        assertThrows(IllegalArgumentException.class, () -> IvySettings.parse("hiedra cueva"));
        assertThrows(IllegalArgumentException.class, () -> IvySettings.parse("-x 3"));
        assertThrows(IllegalArgumentException.class, () -> IvySettings.parse("musgo"));
    }

    @Test
    void writesMinecraftStates() {
        IvyBlock vine = new IvyBlock(0, 0, 0, IvyMaterial.VINE, (1 << IvyGrower.NORTH) | (1 << IvyGrower.UP), false);
        assertEquals("minecraft:vine[up=true,north=true,south=false,west=false,east=false]", vine.stateString());
        IvyBlock leaves = new IvyBlock(0, 0, 0, IvyMaterial.AZALEA_LEAVES, 0, false);
        assertEquals("minecraft:azalea_leaves[distance=7,persistent=true,waterlogged=false]", leaves.stateString());
        IvyBlock tip = new IvyBlock(0, 0, 0, IvyMaterial.CAVE_VINES, 0, true);
        assertEquals("minecraft:cave_vines[age=25,berries=true]", tip.stateString());
    }

    /** Comprueba las reglas de Minecraft de cada bloque contra el mundo original y los demás bloques de la hiedra. */
    static void assertSupported(FakeTerrain terrain, IvyResult result, String context) {
        Map<Long, IvyBlock> placed = new HashMap<>();
        for (IvyBlock block : result.blocks()) {
            placed.put(IvyGrower.key(block.x(), block.y(), block.z()), block);
        }
        for (IvyBlock block : result.blocks()) {
            int x = block.x();
            int y = block.y();
            int z = block.z();
            String where = context + " · " + block.stateString() + " en " + x + "," + y + "," + z;
            assertTrue(terrain.free(x, y, z), "ocupa una celda llena: " + where);
            IvyBlock above = placed.get(IvyGrower.key(x, y + 1, z));
            switch (block.material()) {
                case VINE, GLOW_LICHEN -> {
                    assertNotEquals(0, block.faces(), "sin caras: " + where);
                    if (block.material() == IvyMaterial.VINE) {
                        assertFalse(block.hasFace(IvyGrower.DOWN), "la enredadera no tiene cara de abajo: " + where);
                    }
                    for (int face = 0; face < 6; face++) {
                        if (!block.hasFace(face)) {
                            continue;
                        }
                        boolean sturdy = terrain.sturdy(x + IvyGrower.DX[face], y + IvyGrower.DY[face],
                                z + IvyGrower.DZ[face], face ^ 1);
                        boolean inherited = block.material() == IvyMaterial.VINE && face != IvyGrower.UP
                                && above != null && above.material() == IvyMaterial.VINE && above.hasFace(face);
                        assertTrue(sturdy || inherited, "cara " + IvyGrower.FACE_NAMES[face] + " sin apoyo: " + where);
                    }
                }
                case MOSS_CARPET -> assertTrue(terrain.rests(x, y - 1, z), "alfombra en el aire: " + where);
                case CAVE_VINES, CAVE_VINES_PLANT -> {
                    boolean ceiling = terrain.sturdy(x, y + 1, z, IvyGrower.DOWN);
                    boolean chain = above != null && above.material() == IvyMaterial.CAVE_VINES_PLANT;
                    assertTrue(ceiling || chain, "baya sin techo: " + where);
                    IvyBlock below = placed.get(IvyGrower.key(x, y - 1, z));
                    boolean continues = below != null && below.material().caveVines();
                    assertEquals(block.material() == IvyMaterial.CAVE_VINES_PLANT, continues,
                            "tramo y punta mal puestos: " + where);
                }
                case SPORE_BLOSSOM -> assertTrue(terrain.sturdy(x, y + 1, z, IvyGrower.DOWN),
                        "flor de esporas sin techo: " + where);
                default -> {
                    assertTrue(block.material().leaves(), "material inesperado: " + where);
                    boolean touches = false;
                    for (int face = 0; face < 6; face++) {
                        int nx = x + IvyGrower.DX[face];
                        int ny = y + IvyGrower.DY[face];
                        int nz = z + IvyGrower.DZ[face];
                        touches |= terrain.kind(nx, ny, nz) != 0 || placed.containsKey(IvyGrower.key(nx, ny, nz));
                    }
                    assertTrue(touches, "hojas sueltas: " + where);
                }
            }
        }
    }

    private static int ownSupport(FakeTerrain terrain, IvyBlock block) {
        int mask = 0;
        for (int face = 1; face < 6; face++) {
            if (terrain.sturdy(block.x() + IvyGrower.DX[face], block.y() + IvyGrower.DY[face],
                    block.z() + IvyGrower.DZ[face], face ^ 1)) {
                mask |= 1 << face;
            }
        }
        return mask;
    }
}
