package com.komainu.moxia.selection;

import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El lazo llevado al mundo con una cámara inventada: lo que se ve dentro del contorno, sin huecos aunque se mire de
 * lado o de lejos, sin lo que está tapado y con la profundidad y lo no sólido como se pidan.
 */
class LassoProjectionTest {
    private static final double WIDTH = 480.0;
    private static final double HEIGHT = 270.0;
    private static final int GROUND = 63;

    @Test
    void cameraRaysAndProjectionAgree() {
        LassoCamera camera = LassoCamera.looking(new Vec3(0.5, 80.0, 0.5), 30.0, 25.0, 70.0, WIDTH, HEIGHT);
        Vec3 center = camera.ray(WIDTH / 2, HEIGHT / 2);
        assertEquals(1.0, center.dot(camera.forward()), 1.0E-9, "el centro de la pantalla mira hacia delante");
        for (double[] point : new double[][]{{10, 20}, {470, 260}, {240, 5}, {123.5, 77.25}}) {
            Vec3 direction = camera.ray(point[0], point[1]);
            Vec3 far = camera.position().add(direction.multiply(37.0));
            double[] back = camera.project(far.x(), far.y(), far.z());
            assertNotNull(back);
            assertEquals(point[0], back[0], 1.0E-6);
            assertEquals(point[1], back[1], 1.0E-6);
        }
        assertNull(camera.project(0.5 - camera.forward().x(), 80.0 - camera.forward().y(),
                0.5 - camera.forward().z()), "lo de detrás no cae en la pantalla");
        LassoCamera down = LassoCamera.looking(new Vec3(0.0, 0.0, 0.0), 0.0, 90.0, 70.0, WIDTH, HEIGHT);
        assertEquals(-1.0, down.forward().y(), 1.0E-9, "cabeceo 90: mirando al suelo");
        LassoCamera south = LassoCamera.looking(new Vec3(0.0, 0.0, 0.0), 0.0, 0.0, 70.0, WIDTH, HEIGHT);
        assertEquals(1.0, south.forward().z(), 1.0E-9, "guiñada 0: hacia el sur (+Z), como Minecraft");
        assertEquals(-1.0, south.right().x(), 1.0E-9, "mirando al sur, el este queda a la izquierda");
    }

    @Test
    void topDownLassoSelectsTheSurfaceInsideTheOutline() {
        TestTerrain terrain = TestTerrain.flat(GROUND);
        LassoCamera camera = topDown(new Vec3(0.5, 100.0, 0.5));
        LassoPolygon square = rectangle(140, 60, 340, 210);
        BlockSet selected = new LassoProjection(camera, square, LassoSettings.DEFAULT, terrain, 256, 100_000).run();

        assertFalse(selected.isEmpty());
        selected.forEach(block -> assertEquals(GROUND, BlockSet.y(block), "solo la superficie"));
        assertCovers(camera, square, selected, -40, 40, -40, 40);
    }

    @Test
    void obliqueAndFarViewsLeaveNoHoles() {
        TestTerrain terrain = TestTerrain.flat(GROUND);
        // Mirando de lejos y casi rasante: cada bloque ocupa muy poco en la pantalla.
        LassoCamera camera = LassoCamera.looking(new Vec3(0.5, GROUND + 12.0, 0.5), 0.0, 12.0, 70.0, WIDTH, HEIGHT);
        LassoPolygon square = rectangle(60, 140, 420, 200);
        LassoProjection projection = new LassoProjection(camera, square, LassoSettings.DEFAULT, terrain, 256,
                400_000);
        BlockSet selected = projection.run();
        assertCovers(camera, square, selected, -160, 160, 0, 200);
        assertTrue(projection.rays() < LassoProjection.MAX_RAYS / 2, "afina solo donde hace falta: "
                + projection.rays() + " rayos");
    }

    @Test
    void hiddenBlocksAreNotSelected() {
        // Un muro de piedra delante tapa el suelo de detrás.
        TestTerrain terrain = TestTerrain.flat(GROUND).fill(-20, GROUND + 1, 10, 20, GROUND + 12, 10, Voxels.SOLID);
        LassoCamera camera = LassoCamera.looking(new Vec3(0.5, GROUND + 4.0, 0.5), 0.0, 10.0, 70.0, WIDTH, HEIGHT);
        BlockSet selected = new LassoProjection(camera, rectangle(0, 0, WIDTH, HEIGHT), LassoSettings.DEFAULT,
                terrain, 256, 400_000).run();
        assertTrue(selected.contains(0, GROUND + 3, 10), "la cara del muro que se ve");
        selected.forEach(block -> assertTrue(BlockSet.z(block) <= 10, "nada de lo que hay detrás del muro: "
                + BlockSet.x(block) + " " + BlockSet.y(block) + " " + BlockSet.z(block)));
        assertFalse(selected.contains(0, GROUND + 3, 9 - 1), "ni el aire de delante");
    }

    @Test
    void theOutlineShapeIsKept() {
        TestTerrain terrain = TestTerrain.flat(GROUND);
        LassoCamera camera = topDown(new Vec3(0.5, 100.0, 0.5));
        // Un triángulo: su esquina de abajo a la derecha queda fuera aunque esté dentro de su caja.
        LassoPolygon triangle = new LassoPolygon(new float[]{140, 340, 140}, new float[]{40, 230, 230});
        BlockSet selected = new LassoProjection(camera, triangle, LassoSettings.DEFAULT, terrain, 256, 100_000)
                .run();
        int[] inside = column(camera, 170, 200);
        int[] outside = column(camera, 320, 60);
        assertTrue(selected.contains(inside[0], GROUND, inside[1]));
        assertFalse(selected.contains(outside[0], GROUND, outside[1]));
    }

    @Test
    void depthGoesInwardFromTheVisibleFace() {
        TestTerrain terrain = new TestTerrain(50, GROUND);
        LassoCamera camera = topDown(new Vec3(0.5, 100.0, 0.5));
        LassoPolygon square = rectangle(200, 100, 280, 170);
        BlockSet surface = new LassoProjection(camera, square, LassoSettings.DEFAULT, terrain, 256, 100_000).run();
        BlockSet layers = new LassoProjection(camera, square, new LassoSettings(LassoSettings.Depth.LAYERS, 3, false),
                terrain, 256, 100_000).run();
        BlockSet through = new LassoProjection(camera, square,
                new LassoSettings(LassoSettings.Depth.THROUGH, 3, false), terrain, 256, 100_000).run();

        assertEquals(surface.size() * 3, layers.size(), "tres capas por columna");
        assertEquals(surface.size() * (GROUND - 50 + 1), through.size(), "hasta el fondo: el suelo entero");
        surface.forEach(block -> {
            assertTrue(layers.contains(BlockSet.x(block), GROUND - 2, BlockSet.z(block)));
            assertFalse(layers.contains(BlockSet.x(block), GROUND - 3, BlockSet.z(block)));
            assertTrue(through.contains(BlockSet.x(block), 50, BlockSet.z(block)));
        });
    }

    @Test
    void depthStopsAtHolesAndGoesSidewaysOnWalls() {
        // Un hueco bajo la primera capa: «3 capas» se queda en una.
        TestTerrain terrain = TestTerrain.flat(GROUND).fill(-30, GROUND - 1, -30, 30, GROUND - 1, 30, Voxels.AIR);
        LassoCamera camera = topDown(new Vec3(0.5, 100.0, 0.5));
        BlockSet layers = new LassoProjection(camera, rectangle(200, 100, 280, 170),
                new LassoSettings(LassoSettings.Depth.LAYERS, 3, false), terrain, 256, 100_000).run();
        layers.forEach(block -> assertEquals(GROUND, BlockSet.y(block)));

        // Una pared vista de frente: la profundidad entra hacia +Z, no hacia abajo.
        TestTerrain wall = TestTerrain.flat(GROUND).fill(-20, GROUND + 1, 10, 20, GROUND + 20, 15, Voxels.SOLID);
        LassoCamera front = LassoCamera.looking(new Vec3(0.5, GROUND + 10.5, 0.5), 0.0, 0.0, 70.0, WIDTH, HEIGHT);
        BlockSet facade = new LassoProjection(front, rectangle(220, 115, 260, 155),
                new LassoSettings(LassoSettings.Depth.LAYERS, 4, false), wall, 256, 100_000).run();
        assertTrue(facade.contains(0, GROUND + 10, 10));
        assertTrue(facade.contains(0, GROUND + 10, 13));
        assertFalse(facade.contains(0, GROUND + 10, 14), "cuatro bloques hacia dentro del muro");
    }

    @Test
    void nonSolidBlocksAreSkippedUnlessAsked() {
        TestTerrain terrain = TestTerrain.flat(GROUND).fill(-6, GROUND + 1, -6, 6, GROUND + 1, 6, Voxels.NON_SOLID);
        LassoCamera camera = topDown(new Vec3(0.5, 100.0, 0.5));
        LassoPolygon square = rectangle(220, 115, 260, 155);
        BlockSet through = new LassoProjection(camera, square, LassoSettings.DEFAULT, terrain, 256, 100_000).run();
        BlockSet plants = new LassoProjection(camera, square, new LassoSettings(LassoSettings.Depth.SURFACE, 1, true),
                terrain, 256, 100_000).run();
        assertFalse(through.isEmpty());
        assertFalse(plants.isEmpty());
        through.forEach(block -> assertEquals(GROUND, BlockSet.y(block), "sin «no sólido», el suelo de debajo"));
        plants.forEach(block -> assertEquals(GROUND + 1, BlockSet.y(block), "con «no sólido», las plantas"));
    }

    @Test
    void unloadedChunksStopTheRays() {
        TestTerrain terrain = TestTerrain.flat(GROUND).unloadedFrom(5);
        LassoCamera camera = topDown(new Vec3(0.5, 100.0, 0.5));
        BlockSet selected = new LassoProjection(camera, rectangle(100, 50, 380, 220), LassoSettings.DEFAULT, terrain,
                256, 100_000).run();
        assertFalse(selected.isEmpty());
        selected.forEach(block -> assertTrue(BlockSet.x(block) < 5, "nada de lo que no está cargado"));
        assertTrue(selected.contains(4, GROUND, 0));
    }

    @Test
    void limitsAreChecked() {
        TestTerrain terrain = TestTerrain.flat(GROUND);
        LassoCamera camera = topDown(new Vec3(0.5, 100.0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new LassoProjection(camera, rectangle(0, 0, WIDTH, HEIGHT),
                LassoSettings.DEFAULT, terrain, 256, 50).run(), "más bloques que el máximo");
        assertThrows(IllegalArgumentException.class, () -> new LassoProjection(camera, rectangle(0, 0, WIDTH, HEIGHT),
                new LassoSettings(LassoSettings.Depth.THROUGH, 1, false), terrain, 256, 5_000).run(),
                "hasta el fondo también cuenta");
        assertThrows(IllegalArgumentException.class, () -> new LassoProjection(camera, rectangle(10, 10, 12, 12),
                LassoSettings.DEFAULT, terrain, 256, 5_000), "un clic no es un lazo");
        assertThrows(IllegalArgumentException.class, () -> new LassoSettings(LassoSettings.Depth.LAYERS, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new LassoSettings(LassoSettings.Depth.LAYERS,
                LassoSettings.MAX_LAYERS + 1, false));
        assertThrows(IllegalArgumentException.class, () -> LassoCamera.looking(new Vec3(0.0, 0.0, 0.0), 0, 0, 0.5, WIDTH,
                HEIGHT), "campo de visión imposible");
        float[] many = new float[LassoPolygon.MAX_POINTS + 1];
        assertThrows(IllegalArgumentException.class, () -> new LassoPolygon(many, many.clone()));
        // Una pantalla enorme con un lazo que la cubre entera no puede pedir millones de rayos.
        LassoCamera huge = LassoCamera.looking(new Vec3(0.5, 100.0, 0.5), 0.0, 90.0, 70.0, LassoCamera.MAX_SCREEN,
                LassoCamera.MAX_SCREEN);
        assertThrows(IllegalArgumentException.class, () -> new LassoProjection(huge, rectangle(0, 0,
                LassoCamera.MAX_SCREEN, LassoCamera.MAX_SCREEN), LassoSettings.DEFAULT, terrain, 256, 10_000_000).run());
        // Nada más lejos que el alcance.
        BlockSet near = new LassoProjection(camera, rectangle(0, 0, WIDTH, HEIGHT), LassoSettings.DEFAULT, terrain,
                20, 100_000).run();
        assertTrue(near.isEmpty(), "el suelo está a 36 bloques y el alcance es 20");
    }

    @Test
    void steppingGivesTheSameResult() {
        TestTerrain terrain = TestTerrain.flat(GROUND);
        LassoCamera camera = LassoCamera.looking(new Vec3(0.5, GROUND + 20.0, 0.5), 45.0, 35.0, 70.0, WIDTH, HEIGHT);
        LassoPolygon square = rectangle(50, 30, 430, 250);
        BlockSet whole = new LassoProjection(camera, square, LassoSettings.DEFAULT, terrain, 256, 400_000).run();
        LassoProjection stepped = new LassoProjection(camera, square, LassoSettings.DEFAULT, terrain, 256, 400_000);
        AtomicInteger calls = new AtomicInteger();
        double last = -1.0;
        while (stepped.step(() -> true)) {
            calls.incrementAndGet();
            assertTrue(stepped.progress() >= last);
            last = stepped.progress();
        }
        assertTrue(calls.get() > 5, "va por tramos");
        assertEquals(whole, stepped.result());
        assertEquals(1.0, stepped.progress());
    }

    @Test
    void longOutlinesAreSimplified() {
        List<float[]> circle = new ArrayList<>();
        for (int index = 0; index < 2_000; index++) {
            double angle = Math.PI * 2 * index / 2_000;
            circle.add(new float[]{(float) (240 + 100 * Math.cos(angle)), (float) (135 + 100 * Math.sin(angle))});
        }
        List<float[]> simple = LassoPolygon.simplified(circle, LassoPolygon.MAX_POINTS);
        assertTrue(simple.size() <= LassoPolygon.MAX_POINTS && simple.size() > 16, "" + simple.size());
        LassoPolygon polygon = LassoPolygon.of(simple);
        assertEquals(Math.PI * 100 * 100, polygon.area(), Math.PI * 100 * 100 * 0.01, "la forma se mantiene");
        assertTrue(polygon.contains(240, 135));
        assertFalse(polygon.contains(240 + 101, 135));
    }

    // ── Ayudas ───────────────────────────────────────────────────────────────────────────────────────────

    private static LassoCamera topDown(Vec3 eye) {
        return LassoCamera.looking(eye, 0.0, 90.0, 70.0, WIDTH, HEIGHT);
    }

    static LassoPolygon rectangle(double left, double top, double right, double bottom) {
        return new LassoPolygon(new float[]{(float) left, (float) right, (float) right, (float) left},
                new float[]{(float) top, (float) top, (float) bottom, (float) bottom});
    }

    /** La columna {@code {x, z}} que se ve en ese punto de la pantalla (para la cámara cenital). */
    private static int[] column(LassoCamera camera, double screenX, double screenY) {
        Vec3 direction = camera.ray(screenX, screenY);
        double distance = (GROUND + 1.0 - camera.position().y()) / direction.y();
        Vec3 point = camera.position().add(direction.multiply(distance));
        return new int[]{(int) Math.floor(point.x()), (int) Math.floor(point.z())};
    }

    /**
     * Sin huecos ni bloques de más, mirando la cara de arriba de cada bloque del suelo en la pantalla: si sus cuatro
     * esquinas caen dentro del rectángulo del lazo, tiene que estar; si caen todas fuera por un mismo lado, no. Lo que
     * ocupa menos de un punto de alto en la pantalla (lejísimos y de canto) no se cuenta: no se puede rodear.
     */
    private static void assertCovers(LassoCamera camera, LassoPolygon rectangle, BlockSet selected, int minX,
                                     int maxX, int minZ, int maxZ) {
        double[] bounds = rectangle.bounds();
        int checked = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double[][] corners = new double[4][];
                boolean behind = false;
                for (int corner = 0; corner < 4; corner++) {
                    corners[corner] = camera.project(x + (corner & 1), GROUND + 1.0, z + (corner >> 1));
                    behind |= corners[corner] == null;
                }
                if (behind) {
                    continue;
                }
                double top = Double.POSITIVE_INFINITY;
                double bottom = Double.NEGATIVE_INFINITY;
                int inside = 0;
                boolean left = true;
                boolean right = true;
                boolean above = true;
                boolean below = true;
                for (double[] point : corners) {
                    top = Math.min(top, point[1]);
                    bottom = Math.max(bottom, point[1]);
                    if (point[0] > bounds[0] && point[0] < bounds[2] && point[1] > bounds[1] && point[1] < bounds[3]) {
                        inside++;
                    }
                    left &= point[0] < bounds[0];
                    right &= point[0] > bounds[2];
                    above &= point[1] < bounds[1];
                    below &= point[1] > bounds[3];
                }
                if (inside == 4 && bottom - top >= 1.0) {
                    checked++;
                    assertTrue(selected.contains(x, GROUND, z), "hueco en " + x + ", " + z);
                } else if (left || right || above || below) {
                    assertFalse(selected.contains(x, GROUND, z), "de más en " + x + ", " + z);
                }
            }
        }
        assertTrue(checked > 50, "se comprobaron " + checked + " bloques");
    }

}
