package com.komainu.moxia.generator;

import com.komainu.moxia.noise.NoiseSpec;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowFieldTest {
    private static final Volume LAYER = Volume.box(0, 0, 0, 63, 0, 63);

    private static GeneratedBlocks run(String args, Volume volume, FlowField.Terrain terrain, int palette) {
        GeneratorRequests.FlowFieldRequest request = GeneratorRequests.flowField(args, () -> 11L);
        GeneratedBlocks blocks = new GeneratedBlocks(2_000_000);
        FlowField.generate(request.settings(), volume, terrain, palette, blocks);
        return blocks;
    }

    @Test
    void linesStayInsideAndAreDeterministic() {
        GeneratedBlocks first = run("x 200 48 1 1 Perlin(F:0.03) -s 5", LAYER, null, 4);
        GeneratedBlocks second = run("x 200 48 1 1 Perlin(F:0.03) -s 5", LAYER, null, 4);
        assertTrue(first.size() > 200);
        Set<Long> a = new HashSet<>();
        Set<Long> b = new HashSet<>();
        for (int index = 0; index < first.size(); index++) {
            assertTrue(LAYER.contains(first.x(index), first.y(index), first.z(index)));
            a.add(first.packed(index) * 31 + first.material(index));
        }
        for (int index = 0; index < second.size(); index++) {
            b.add(second.packed(index) * 31 + second.material(index));
        }
        assertEquals(a, b);
    }

    @Test
    void overlapsClimbThePalette() {
        GeneratedBlocks few = run("x 20 32 1 1 Perlin(F:0.03)", LAYER, null, 256);
        GeneratedBlocks many = run("x 2000 32 1 1 Perlin(F:0.03)", LAYER, null, 256);
        assertTrue(highest(many) > highest(few), "con más líneas cruzadas salen entradas más altas");
        GeneratedBlocks scaled = run("x 2000 32 1 0.1 Perlin(F:0.03)", LAYER, null, 256);
        assertTrue(highest(scaled) < highest(many), "la escala de paleta frena la subida");
    }

    private static int highest(GeneratedBlocks blocks) {
        int highest = 0;
        for (int index = 0; index < blocks.size(); index++) {
            highest = Math.max(highest, blocks.material(index));
        }
        return highest;
    }

    @Test
    void negativeProgressionRemovesAndFillCoversEverything() {
        GeneratedBlocks negative = run("x 300 32 1 1 Perlin(F:0.03) -p -1:-1", LAYER, null, 4);
        assertEquals(0, negative.size(), "solo fuerza negativa no deja bloques");
        GeneratedBlocks filled = run("x 30 32 1 1 Perlin(F:0.03) -f", LAYER, null, 4);
        assertEquals(64 * 64, filled.size(), "-f rellena el resto con la primera entrada");
    }

    @Test
    void percentLinesScaleWithTheArea() {
        GeneratorRequests.FlowFieldRequest request = GeneratorRequests.flowField("x 10% 1 1", () -> 1L);
        assertTrue(request.settings().percent());
        GeneratedBlocks blocks = new GeneratedBlocks(100_000);
        FlowField.generate(request.settings(), LAYER, null, 2, blocks);
        // 10 % de 4096 bloques: unas 410 salidas de un paso, de dos o tres bloques cada una.
        assertTrue(blocks.size() > 250 && blocks.size() <= 1_300, "bloques: " + blocks.size());
    }

    @Test
    void heightmapFollowsTheSurface() {
        FlowField.Terrain terrain = new FlowField.Terrain() {
            @Override
            public int surface(int x, int z) {
                return 5 + (x / 8);
            }

            @Override
            public boolean source(int x, int y, int z) {
                return true;
            }
        };
        Volume volume = Volume.box(0, 0, 0, 63, 20, 63);
        GeneratedBlocks blocks = run("x 100 32 1 1 Perlin(F:0.03) -h", volume, terrain, 3);
        assertTrue(blocks.size() > 0);
        for (int index = 0; index < blocks.size(); index++) {
            assertEquals(terrain.surface(blocks.x(index), blocks.z(index)), blocks.y(index));
        }
        GeneratedBlocks above = run("x 100 32 1 1 Perlin(F:0.03) -h above", volume, terrain, 3);
        for (int index = 0; index < above.size(); index++) {
            assertEquals(terrain.surface(above.x(index), above.z(index)) + 1, above.y(index));
        }
    }

    @Test
    void maskLimitsTheStartPoints() {
        FlowField.Terrain terrain = new FlowField.Terrain() {
            @Override
            public int surface(int x, int z) {
                return 0;
            }

            @Override
            public boolean source(int x, int y, int z) {
                return x == 10 && z == 10;
            }
        };
        GeneratedBlocks blocks = run("x 50 1 0.1 1 Perlin(F:0.03) -m stone", LAYER, terrain, 3);
        assertEquals(1, blocks.size(), "todas las líneas salen del único bloque de la máscara");
        assertEquals(10, blocks.x(0));
        assertThrows(IllegalArgumentException.class, () -> run("x 5 4 1 1 Perlin() -m stone", LAYER,
                new FlowField.Terrain() {
                    @Override
                    public int surface(int x, int z) {
                        return 0;
                    }

                    @Override
                    public boolean source(int x, int y, int z) {
                        return false;
                    }
                }, 3));
    }

    @Test
    void curlFieldHasNoDivergence() {
        FlowField.Settings settings = GeneratorRequests.flowField("x 1 1 1 1 Perlin(F:0.05) -c", () -> 3L)
                .settings();
        FlowField.Field field = new FlowField.Field(settings);
        double[] left = new double[3];
        double[] right = new double[3];
        double[] back = new double[3];
        double[] front = new double[3];
        double total = 0;
        double magnitude = 0;
        for (int sample = 0; sample < 50; sample++) {
            double x = sample * 3.7;
            double z = sample * 1.3;
            // Divergencia del campo sin normalizar: se compara la del rotacional contra la de un campo cualquiera.
            field.direction(x - 0.01, 0, z, false, left);
            field.direction(x + 0.01, 0, z, false, right);
            field.direction(x, 0, z - 0.01, false, back);
            field.direction(x, 0, z + 0.01, false, front);
            total += Math.abs((right[0] - left[0]) + (front[2] - back[2]));
            magnitude += Math.abs(right[0] - left[0]) + Math.abs(front[2] - back[2]);
        }
        assertTrue(total < magnitude * 0.9, "el rotacional tiende a no tener fuentes ni sumideros");
    }

    @Test
    void threeDimensionalFlowMovesVertically() {
        GeneratedBlocks blocks = run("x 60 48 1 1 Perlin(F:0.04) -t", Volume.box(0, 0, 0, 31, 31, 31), null, 3);
        Set<Integer> heights = new HashSet<>();
        for (int index = 0; index < blocks.size(); index++) {
            heights.add(blocks.y(index));
        }
        assertTrue(heights.size() > 10);
    }

    @Test
    void requestParsing() {
        GeneratorRequests.FlowFieldRequest request = GeneratorRequests.flowField(
                "##Grayscale 500 64 2 0.5 Si(F:0.02) -m grass_block -h above -i 0.4 -g 0,-0.1,0 -j 2 -b 8"
                        + " -x *10 -p 1:-1 -s 99 -cf", () -> 1L);
        FlowField.Settings settings = request.settings();
        assertEquals("##Grayscale", request.palette());
        assertEquals(500, settings.lines());
        assertEquals(64, settings.iterations());
        assertEquals(2.0, settings.velocity());
        assertEquals(0.5, settings.paletteScalar());
        assertEquals(FlowField.Distribution.ABOVE, settings.heightmap());
        assertTrue(settings.masked() && settings.curl() && settings.fill());
        assertEquals(0.4, settings.inertia());
        assertEquals(-0.1, settings.gravity()[1]);
        assertEquals(2.0, settings.jitter()[2]);
        assertEquals(8, settings.boundary());
        assertNotNull(settings.modX());
        assertNull(settings.modY());
        assertEquals(1.0, settings.progressionStart());
        assertEquals(-1.0, settings.progressionEnd());
        assertEquals(99L, settings.seed());
        assertEquals(20.0, settings.modX().runner(1, 1).evaluate(2, 0, 0, 0), 1e-9, "-x *10 es x*10");
        assertEquals(FlowField.Distribution.SURFACE,
                GeneratorRequests.flowField("x 5 -h", () -> 1L).settings().heightmap());
        assertThrows(IllegalArgumentException.class, () -> GeneratorRequests.flowField("x 5 -h -t", () -> 1L));
        assertThrows(IllegalArgumentException.class, () -> GeneratorRequests.flowField("x", () -> 1L));
        assertThrows(IllegalArgumentException.class, () -> GeneratorRequests.flowField("x 5 -x )", () -> 1L));
        assertTrue(NoiseSpec.parse(GeneratorRequests.DEFAULT_NOISE) != null);
    }
}
