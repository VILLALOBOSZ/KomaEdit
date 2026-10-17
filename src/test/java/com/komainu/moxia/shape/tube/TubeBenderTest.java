package com.komainu.moxia.shape.tube;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.SampledPath;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TubeBenderTest {
    private static final int LIMIT = 1_500_000;

    @Test
    void repeatsWholeCopiesCenteredOnAStraightPath() {
        // Muro de 5 de largo (X), 3 de alto, con un bloque distinto (índice 1) al principio de cada copia.
        TubeBender.Template wall = wall(5, 3, 1);
        SampledPath path = straight(0, 0, 0, 22, 0, 0);
        Long2IntOpenHashMap bent = TubeBender.bend(path, wall, TubeBender.Settings.defaults(TubeBender.Mode.REPEAT),
                LIMIT);
        // 22 bloques de recorrido caben 4 copias de 5 (20), centradas: 1 bloque libre a cada lado.
        assertFalse(bent.containsKey(PackedBlockPlan.pack(0, 0, 0)));
        for (int copy = 0; copy < 4; copy++) {
            int x = 1 + copy * 5;
            assertEquals(1, TubeBender.paletteIndex(bent.get(PackedBlockPlan.pack(x, 0, 0))), "inicio de copia en " + x);
            for (int height = 0; height < 3; height++) {
                assertTrue(bent.containsKey(PackedBlockPlan.pack(x + 2, height, 0)));
            }
        }
        assertFalse(bent.containsKey(PackedBlockPlan.pack(22, 0, 0)));
        assertEquals(4 * 5 * 3, bent.size());
        bent.values().forEach(value -> assertEquals(0, TubeBender.quarterTurns(value)));
    }

    @Test
    void turnsBlocksAQuarterWhenThePathRunsAlongZ() {
        TubeBender.Template wall = wall(5, 2, 1);
        SampledPath path = straight(0, 0, 0, 0, 0, 20);
        Long2IntOpenHashMap bent = TubeBender.bend(path, wall, TubeBender.Settings.defaults(TubeBender.Mode.REPEAT),
                LIMIT);
        assertEquals(4 * 5 * 2, bent.size());
        // rotateY(270) lleva el +X de la plantilla al +Z del recorrido (en WorldEdit rotateY(90) lleva +X a -Z).
        bent.values().forEach(value -> assertEquals(3, TubeBender.quarterTurns(value)));
        assertTrue(bent.containsKey(PackedBlockPlan.pack(0, 1, 7)));
    }

    @Test
    void solidModulesStayWatertightAroundCurves() {
        TubeBender.Template block = new TubeBender.Template();
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    block.add(x, y, z, 0);
                }
            }
        }
        List<BlockVector3> arc = new ArrayList<>();
        for (int step = 0; step <= 8; step++) {
            double angle = Math.PI * 0.5 * step / 8.0;
            arc.add(BlockVector3.at((int) Math.round(Math.cos(angle) * 30), 0, (int) Math.round(Math.sin(angle) * 30)));
        }
        SampledPath path = SampledPath.createSmooth(arc, false);
        TubeBender.Settings alongX = new TubeBender.Settings(TubeBender.Mode.STRETCH, TubeBender.Axis.X,
                TubeSettings.NormalMode.UPRIGHT, 0, 0, 0.0, 0.0, 0.0);
        Long2IntOpenHashMap bent = TubeBender.bend(path, block, alongX, LIMIT);
        // Toda la franja central del arco (radio 28–32, y 0–1) tiene que estar llena: sin huecos por fuera de la curva.
        for (int degrees = 5; degrees <= 85; degrees++) {
            double angle = Math.toRadians(degrees);
            for (double radius = 28.5; radius <= 31.5; radius += 1.0) {
                int x = (int) Math.floor(Math.cos(angle) * radius + 0.5);
                int z = (int) Math.floor(Math.sin(angle) * radius + 0.5);
                assertTrue(bent.containsKey(PackedBlockPlan.pack(x, 0, z)), "hueco en " + x + ",0," + z);
            }
        }
    }

    @Test
    void stretchSpreadsOneCopyOverTheWholePath() {
        TubeBender.Template wall = wall(4, 1, 1);
        SampledPath path = straight(0, 0, 0, 40, 0, 0);
        Long2IntOpenHashMap bent = TubeBender.bend(path, wall, TubeBender.Settings.defaults(TubeBender.Mode.STRETCH),
                LIMIT);
        int marked = 0;
        for (long key : bent.keySet()) {
            if (TubeBender.paletteIndex(bent.get(key)) == 1) {
                marked++;
            }
        }
        // El primer bloque de la plantilla (1 de 4) ocupa la primera cuarta parte del recorrido.
        assertEquals(10, marked, 1);
        assertTrue(bent.size() >= 40);
    }

    @Test
    void closedLoopsFitAWholeNumberOfCopies() {
        List<BlockVector3> ring = new ArrayList<>();
        for (int step = 0; step < 12; step++) {
            double angle = Math.PI * 2.0 * step / 12.0;
            ring.add(BlockVector3.at((int) Math.round(Math.cos(angle) * 20), 0, (int) Math.round(Math.sin(angle) * 20)));
        }
        SampledPath path = SampledPath.createSmooth(ring, true);
        Long2IntOpenHashMap bent = TubeBender.bend(path, wall(6, 2, 1),
                TubeBender.Settings.defaults(TubeBender.Mode.REPEAT), LIMIT);
        int starts = 0;
        for (long key : bent.keySet()) {
            if (PackedBlockPlan.unpackY(key) == 0 && TubeBender.paletteIndex(bent.get(key)) == 1) {
                starts++;
            }
        }
        int copies = (int) Math.round(path.length() / 6.0);
        assertEquals(copies, starts, copies * 0.5, "cada copia aporta su bloque inicial");
    }

    @Test
    void uprightModeKeepsWallsPlumbOnSlopes() {
        TubeBender.Template wall = wall(1, 4, 0);
        SampledPath path = straight(0, 0, 0, 40, 20, 0);
        Long2IntOpenHashMap bent = TubeBender.bend(path, wall, TubeBender.Settings.defaults(TubeBender.Mode.REPEAT),
                LIMIT);
        for (int x = 4; x <= 36; x++) {
            int lowest = Integer.MAX_VALUE;
            int highest = Integer.MIN_VALUE;
            for (int y = -10; y < 40; y++) {
                if (bent.containsKey(PackedBlockPlan.pack(x, y, 0))) {
                    lowest = Math.min(lowest, y);
                    highest = Math.max(highest, y);
                }
            }
            assertEquals(4, highest - lowest + 1, "columna de 4 a plomo en x=" + x);
            assertEquals(x / 2.0, lowest, 1.0, "la base sigue la cuesta en x=" + x);
        }
    }

    @Test
    void parsesCommandOptionsAndRejectsShortPaths() {
        TubeBender.Settings settings = TubeBender.Settings.parse("stretch -n horizontal -g 2 -a -z -y -1 -p 0.5:0:0");
        assertEquals(TubeBender.Mode.STRETCH, settings.mode());
        assertEquals(TubeSettings.NormalMode.HORIZONTAL, settings.normalMode());
        assertEquals(TubeBender.Axis.NEGATIVE_Z, settings.axis());
        assertEquals(2, settings.gap());
        assertEquals(-1, settings.offsetUp());
        assertEquals(0.5, settings.tension(), 0.0);
        assertEquals(TubeBender.Mode.REPEAT, TubeBender.Settings.parse("").mode());
        assertThrows(IllegalArgumentException.class, () -> TubeBender.Settings.parse("-q fast"));
        assertThrows(IllegalArgumentException.class, () -> TubeBender.Settings.parse("repeat stretch"));
        assertThrows(IllegalArgumentException.class, () -> TubeBender.bend(straight(0, 0, 0, 3, 0, 0), wall(8, 1, 0),
                TubeBender.Settings.defaults(TubeBender.Mode.REPEAT), LIMIT));
    }

    /** Muro a lo largo de X; si {@code marker} es 1, el primer bloque de cada fila usa el índice 1. */
    private static TubeBender.Template wall(int length, int height, int marker) {
        TubeBender.Template template = new TubeBender.Template();
        for (int x = 0; x < length; x++) {
            for (int y = 0; y < height; y++) {
                template.add(x, y, 0, x == 0 && marker == 1 ? 1 : 0);
            }
        }
        return template;
    }

    private static SampledPath straight(int x1, int y1, int z1, int x2, int y2, int z2) {
        return SampledPath.createSmooth(List.of(BlockVector3.at(x1, y1, z1), BlockVector3.at(x2, y2, z2)), false);
    }
}
