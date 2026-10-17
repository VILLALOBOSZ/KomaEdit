package com.komainu.moxia.generator;

import com.komainu.moxia.edit.PackedBlockPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowLineTest {
    private static FlowLine.Result trace(String args, FlowLine.Terrain terrain) {
        return FlowLine.trace(GeneratorRequests.flowLine(args, () -> 6L).settings(), new double[]{0.5, 70.5, 0.5},
                new double[]{1, 0, 0}, terrain);
    }

    @Test
    void lineHeadsWhereYouLookAndHasNoGaps() {
        FlowLine.Result result = trace("stone 120 0 Perlin(F:0.05)", null);
        long last = 0;
        int index = 0;
        double sumX = 0;
        for (long key : result.blocks()) {
            sumX += PackedBlockPlan.unpackX(key);
            if (index++ > 0) {
                int dx = Math.abs(PackedBlockPlan.unpackX(key) - PackedBlockPlan.unpackX(last));
                int dy = Math.abs(PackedBlockPlan.unpackY(key) - PackedBlockPlan.unpackY(last));
                int dz = Math.abs(PackedBlockPlan.unpackZ(key) - PackedBlockPlan.unpackZ(last));
                assertTrue(dx <= 1 && dy <= 1 && dz <= 1, "bloques contiguos");
            }
            last = key;
        }
        assertTrue(sumX / result.blocks().size() > 10, "avanza hacia +x, donde mira");
    }

    @Test
    void gravityPullsTheLineDown() {
        FlowLine.Result falling = trace("stone 150 -2 Perlin(F:0.05)", null);
        FlowLine.Result rising = trace("stone 150 2 Perlin(F:0.05)", null);
        assertTrue(PackedBlockPlan.unpackY(lastOf(falling)) < 70);
        assertTrue(PackedBlockPlan.unpackY(lastOf(rising)) > 71);
    }

    private static long lastOf(FlowLine.Result result) {
        long last = 0;
        for (long key : result.blocks()) {
            last = key;
        }
        return last;
    }

    @Test
    void snapKeepsTheLineOnTheGround() {
        FlowLine.Result result = trace("stone 100 0 Perlin(F:0.05) -s", (x, y, z) -> y <= 66);
        for (long key : result.blocks()) {
            assertEquals(66, PackedBlockPlan.unpackY(key), "pegada al suelo, sobre el bloque de arriba");
        }
    }

    @Test
    void severalLinesAndConvexPoints() {
        FlowLine.Result one = trace("stone 80 0 Perlin(F:0.05)", null);
        FlowLine.Result many = trace("stone 80 0 Perlin(F:0.05) -l 4 -r 50 -c 12", null);
        assertTrue(many.blocks().size() > one.blocks().size() * 2);
        assertEquals(12, many.convex().size());
        assertEquals(0, one.convex().size());
    }

    @Test
    void parsingLimits() {
        assertThrows(IllegalArgumentException.class, () -> GeneratorRequests.flowLine("stone", () -> 1L));
        assertThrows(IllegalArgumentException.class, () -> GeneratorRequests.flowLine("stone 3000", () -> 1L));
        assertThrows(IllegalArgumentException.class, () -> GeneratorRequests.flowLine("stone 2000 -l 32",
                () -> 1L));
        FlowLine.Settings settings = GeneratorRequests.flowLine("stone 50", () -> 1L).settings();
        assertEquals(-1.0, settings.gravity());
        assertEquals(1, settings.lines());
        assertEquals(50.0, settings.randomness());
    }

    @Test
    void tiltKeepsUnitLength() {
        double[] tilted = FlowLine.tilt(new double[]{0, 0, 1}, Math.toRadians(30), 1.0);
        double length = Math.sqrt(tilted[0] * tilted[0] + tilted[1] * tilted[1] + tilted[2] * tilted[2]);
        assertEquals(1.0, length, 1e-9);
        assertEquals(Math.cos(Math.toRadians(30)), tilted[2], 1e-9);
    }
}
