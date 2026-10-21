package com.komainu.moxia.placement;

import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.shape.Vec3;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El visualizador de la alineación de los pinceles: enseña lo mismo que sale al colocar, sigue a la superficie, espera
 * el clic de {@code ViewDiff}, avisa de lo que no vale y dibuja las tres flechas y la secundaria pedida.
 */
class AlignmentGuideTest {
    private static final Vec3 EYE = new Vec3(0.5, 70.6, -12.5);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final BlockVector3 GROUND = BlockVector3.at(0, 64, 0);
    /** Suelo plano a la altura 64: aire por encima. */
    private static final ScatterPlanner.AirTest FLAT = (x, y, z) -> y > 64;
    /** Una ladera a 45° que sube hacia el este: aire por encima de y = 64 + x. */
    private static final ScatterPlanner.AirTest SLOPE = (x, y, z) -> y > 64 + x;

    private static PlacementSettings brush(PlacementSettings.Kind kind, String input) {
        return PlacementSettings.parse(kind, input, true);
    }

    private static AlignmentGuide.Probe look(PlacementSettings settings, BlockVector3 surface, Vec3 aim,
                                             ScatterPlanner.AirTest air) {
        return AlignmentGuide.probe(settings, surface, UP, EYE, aim, null, List.of(), air);
    }

    private static void assertClose(Vec3 expected, Vec3 actual, String what) {
        assertNotNull(actual, what);
        assertEquals(expected.x(), actual.x(), 1.0E-6, what + " x: " + actual);
        assertEquals(expected.y(), actual.y(), 1.0E-6, what + " y: " + actual);
        assertEquals(expected.z(), actual.z(), 1.0E-6, what + " z: " + actual);
    }

    /** Ejes de vóxeles de colores, como en la documentación de ezEdits. */
    private static VoxelStructure axes() {
        VoxelStructure.Builder builder = VoxelStructure.builder(4, 4, 4, 0, 0, 0);
        builder.set(0, 0, 0, 3);
        for (int step = 1; step < 4; step++) {
            builder.set(step, 0, 0, 0);
            builder.set(0, step, 0, 1);
            builder.set(0, 0, step, 2);
        }
        return builder.build(false, 1, 0, "ejes");
    }

    @Test
    void showsWhatThePlacementWillDo() {
        String[] cases = {"Cl", "Cl C(D:south) C", "Cl C(D:(-1,2,-1)) C(D:west)",
                "Cl C(D:(-1,2,-1)) C(D:west) -j MULTIPLES_45", "Cl Aim Upward", "Cl PlayerRelative C(D:east)",
                "Cl SurfaceNormal C", "Cl Random Random", "Cl 3%C(D:up),1%Random C -x", "Cl Noise C(D:north)",
                "Cl E(E:\"=rx=1;ry=y\") C(D:up) -x"};
        Vec3 aim = new Vec3(0.3, -0.6, 0.74).normalized();
        for (String input : cases) {
            PlacementSettings settings = brush(PlacementSettings.Kind.PLACE, input);
            AlignmentGuide.Probe probe = look(settings, BlockVector3.at(3, 67, 2), aim, SLOPE);
            AlignmentGuide.Result guide = AlignmentGuide.compute(settings, probe, 99L);
            assertEquals(AlignmentGuide.State.READY, guide.state(), input);

            // La colocación de verdad, con lo mismo que recibe al hacer clic en ese bloque.
            Alignment.Context context = new Alignment.Context(probe.origin(), probe.eye(),
                    new DirectionExpr.Context(probe.aim()), probe.normal(), null, null, new Vec3(0, 0, 0),
                    new Vec3(0, 0, 0), new SplittableRandom(99L), null);
            Placement placement = new PlacementBuilder(settings, List.of(axes()), PlacementBuilder.Offsets.NONE)
                    .build(probe.origin(), context, 1.0);
            assertClose(placement.rotation().column(0), guide.x(), input + " (rojo, +x)");
            assertClose(placement.rotation().column(1), guide.y(), input + " (verde, +y)");
            assertClose(placement.rotation().column(2), guide.z(), input + " (azul, +z)");
            assertEquals(new Vec3(3.5, 68.5, 2.5), guide.origin(), input);
        }
    }

    @Test
    void surfaceNormalFollowsTheSlopeUnderTheCrosshair() {
        PlacementSettings settings = brush(PlacementSettings.Kind.PLACE, "Sh(S:Cone,P:stone) SurfaceNormal C");
        AlignmentGuide.Result slope = AlignmentGuide.compute(settings,
                look(settings, BlockVector3.at(2, 66, 0), SOUTH, SLOPE), 1L);
        AlignmentGuide.Result flat = AlignmentGuide.compute(settings, look(settings, GROUND, SOUTH, FLAT), 1L);

        assertClose(new Vec3(-1, 1, 0).normalized(), slope.y(), "la ladera");
        assertClose(UP, flat.y(), "el llano");
        // Sin SurfaceNormal no se lee el mundo: vale la cara que se mira.
        PlacementSettings constant = brush(PlacementSettings.Kind.PLACE, "Cl C C");
        AlignmentGuide.Probe probe = AlignmentGuide.probe(constant, GROUND, new Vec3(1, 0, 0), EYE, SOUTH, null,
                List.of(), (x, y, z) -> {
                    throw new AssertionError("no debía leer el mundo");
                });
        assertEquals(new Vec3(1.5, 64.5, 0.5), probe.origin());
    }

    @Test
    void viewDiffWaitsForTheRightClickThenAimsAtTheCrosshair() {
        PlacementSettings settings = brush(PlacementSettings.Kind.PLACE, "Cl C ViewDiff");
        AlignmentGuide.Result waiting = AlignmentGuide.compute(settings, look(settings, GROUND, SOUTH, FLAT), 1L);
        assertEquals(AlignmentGuide.State.WAITING, waiting.state());
        assertClose(UP, waiting.primary(), "la primaria ya se sabe");
        assertNull(waiting.secondary());
        assertTrue(AlignmentGuide.labels(waiting, 2.0).stream().anyMatch(label -> label.key().equals("waiting")));

        PlacementService.BrushTarget pending = new PlacementService.BrushTarget(GROUND.add(0, 1, 0), GROUND, UP,
                List.of(), null);
        AlignmentGuide.Result aimed = AlignmentGuide.compute(settings, AlignmentGuide.probe(settings,
                BlockVector3.at(6, 64, 0), UP, EYE, SOUTH, pending, List.of(), FLAT), 1L);
        assertEquals(AlignmentGuide.State.READY, aimed.state());
        assertEquals(new Vec3(0.5, 65.5, 0.5), aimed.origin(), "el ejemplo va donde se hizo clic derecho");
        assertClose(new Vec3(1, 0, 0), aimed.x(), "apunta al bloque que se mira");
    }

    @Test
    void sameLineIsInvalidUnlessPerturbed() {
        PlacementSettings broken = brush(PlacementSettings.Kind.PLACE, "Cl C(D:up) C(D:up)");
        AlignmentGuide.Result invalid = AlignmentGuide.compute(broken, look(broken, GROUND, SOUTH, FLAT), 1L);
        assertEquals(AlignmentGuide.State.INVALID, invalid.state());
        assertTrue(AlignmentGuide.labels(invalid, 2.0).stream().anyMatch(label -> label.key().equals("invalid")));
        assertTrue(AlignmentGuide.shape(invalid, 2.0).triangles().stream()
                .anyMatch(triangle -> triangle.argb() == GizmoGeometry.shade(AlignmentGuide.INVALID_MARK, 1.0)));

        PlacementSettings nudged = brush(PlacementSettings.Kind.PLACE, "Cl C(D:up) C(D:up) -x");
        assertEquals(AlignmentGuide.State.READY,
                AlignmentGuide.compute(nudged, look(nudged, GROUND, SOUTH, FLAT), 1L).state());
    }

    @Test
    void randomShowsAStillExampleAndSaysSo() {
        PlacementSettings settings = brush(PlacementSettings.Kind.SCATTER, "Cl Random C");
        AlignmentGuide.Probe probe = look(settings, GROUND, SOUTH, FLAT);
        AlignmentGuide.Result first = AlignmentGuide.compute(settings, probe, 7L);

        assertTrue(first.random());
        assertEquals(first, AlignmentGuide.compute(settings, probe, 7L));
        assertTrue(AlignmentGuide.labels(first, 2.0).stream().anyMatch(label -> label.key().equals("random")));
        assertFalse(AlignmentGuide.compute(brush(PlacementSettings.Kind.SCATTER, "Cl"), probe, 7L).random());
    }

    @Test
    void arrayShowsTheNextStretchOfTheStroke() {
        PlacementSettings settings = brush(PlacementSettings.Kind.ARRAY, "Cl Tangential C(D:up)");
        AlignmentGuide.Result next = AlignmentGuide.compute(settings, AlignmentGuide.probe(settings,
                BlockVector3.at(8, 64, 0), UP, EYE, SOUTH, null, List.of(GROUND), FLAT), 1L);
        assertEquals(new Vec3(8.5, 65.5, 0.5), next.origin(), "encima del bloque, como las estructuras del trazo");
        assertClose(new Vec3(1, 0, 0), next.y(), "a lo largo del tramo");

        AlignmentGuide.Result empty = AlignmentGuide.compute(settings, AlignmentGuide.probe(settings, GROUND, UP,
                EYE, new Vec3(0, -0.8, 0.6), null, List.of(), FLAT), 1L);
        assertClose(SOUTH, empty.y(), "sin trazo, hacia donde se mira");

        PlacementSettings orthogonal = brush(PlacementSettings.Kind.ARRAY, "Cl Orthogonal Tangential");
        AlignmentGuide.Result up = AlignmentGuide.compute(orthogonal, AlignmentGuide.probe(orthogonal,
                BlockVector3.at(8, 64, 0), UP, EYE, SOUTH, null, List.of(GROUND), FLAT), 1L);
        assertClose(UP, up.y(), "Orthogonal con 0°: arriba");
    }

    @Test
    void boundAimStaysPutWhileTheCrosshairMoves() {
        PlacementSettings settings = brush(PlacementSettings.Kind.PLACE, "Cl C(D:aim) C(D:east)")
                .bindAim(new DirectionExpr.Context(SOUTH));
        AlignmentGuide.Result guide = AlignmentGuide.compute(settings,
                look(settings, GROUND, new Vec3(1, -1, 0).normalized(), FLAT), 1L);
        assertClose(SOUTH, guide.y(), "la mirada al configurar");
    }

    @Test
    void shapeHasTheThreeArrowsAndTheRequestedSecondaryWhenItDiffers() {
        PlacementSettings wiki = brush(PlacementSettings.Kind.PLACE, "Cl C(D:(-1,2,-1)) C(D:west)");
        AlignmentGuide.Result guide = AlignmentGuide.compute(wiki, look(wiki, GROUND, SOUTH, FLAT), 1L);
        double length = 3.0;
        GizmoGeometry.Shape shape = AlignmentGuide.shape(guide, length);

        for (Vec3 axis : new Vec3[]{guide.x(), guide.y()}) {
            Vec3 tip = guide.origin().add(axis.multiply(length));
            assertTrue(shape.triangles().stream().anyMatch(triangle -> triangle.c().distance(tip) < 1.0E-9),
                    "punta en " + tip);
        }
        Vec3 blueTip = guide.origin().add(guide.z().multiply(length * 0.6));
        assertTrue(shape.triangles().stream().anyMatch(triangle -> triangle.c().distance(blueTip) < 1.0E-9));
        assertTrue(shape.lines().stream().filter(line -> line.argb() == AlignmentGuide.HINT).count() >= 5,
                "la secundaria pedida, a trazos");
        assertTrue(AlignmentGuide.angle(guide.secondary(), guide.x()) > 10.0);
        assertEquals(List.of("primary", "secondary", "requested"), AlignmentGuide.labels(guide, length).stream()
                .map(AlignmentGuide.Label::key).toList());

        PlacementSettings square = brush(PlacementSettings.Kind.PLACE, "Cl C C");
        AlignmentGuide.Result plain = AlignmentGuide.compute(square, look(square, GROUND, SOUTH, FLAT), 1L);
        assertFalse(AlignmentGuide.shape(plain, length).lines().stream()
                .anyMatch(line -> line.argb() == AlignmentGuide.HINT), "si coinciden, sin trazos");

        List<AlignmentGuide.Label> labels = AlignmentGuide.labels(plain, length);
        assertEquals(List.of("primary", "secondary"), labels.stream().map(AlignmentGuide.Label::key).toList());
        assertClose(plain.origin().add(UP.multiply(length * 1.18)), labels.getFirst().at(), "nombre de la verde");
    }
}
