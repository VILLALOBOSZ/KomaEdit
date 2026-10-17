package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlignmentTest {
    private static final double EPSILON = 1.0E-9;

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-6, () -> "x de " + actual);
        assertEquals(expected.y(), actual.y(), 1.0E-6, () -> "y de " + actual);
        assertEquals(expected.z(), actual.z(), 1.0E-6, () -> "z de " + actual);
    }

    private static Alignment.Context context(Vec3 aim) {
        return Alignment.Context.at(new Vec3(10, 64, 10), new Vec3(0, 65.6, 0), aim, 3L);
    }

    @Test
    void defaultFrameIsIdentity() {
        Mat3 frame = AlignmentFrame.frame(Alignment.UP, Alignment.EAST, AlignmentFrame.Snap.NONE, false);
        assertVector(new Vec3(1, 0, 0), frame.column(0));
        assertVector(new Vec3(0, 1, 0), frame.column(1));
        assertVector(new Vec3(0, 0, 1), frame.column(2));
        assertEquals(1.0, frame.determinant(), EPSILON);
    }

    @Test
    void primaryDefinesUpAndSecondaryIsMadePerpendicular() {
        Mat3 south = AlignmentFrame.frame(new Vec3(0, 0, 1), Alignment.EAST, AlignmentFrame.Snap.NONE, false);
        assertVector(new Vec3(0, 0, 1), south.column(1));
        assertVector(new Vec3(1, 0, 0), south.column(0));
        assertVector(new Vec3(0, -1, 0), south.column(2));

        Mat3 tilted = AlignmentFrame.frame(new Vec3(1, 1, 0), Alignment.EAST, AlignmentFrame.Snap.NONE, false);
        double root = Math.sqrt(0.5);
        assertVector(new Vec3(root, root, 0), tilted.column(1));
        assertVector(new Vec3(root, -root, 0), tilted.column(0), "este hacia abajo, perpendicular a la primaria");
        assertEquals(0.0, tilted.column(0).dot(tilted.column(1)), EPSILON);
        assertEquals(1.0, tilted.determinant(), EPSILON);

        Mat3 northwest = AlignmentFrame.frame(new Vec3(-1, 2, -1), new Vec3(-1, 0, 0), AlignmentFrame.Snap.NONE,
                false);
        assertTrue(northwest.column(0).x() < -0.8, "el +x mira al oeste todo lo que puede");
        assertEquals(0.0, northwest.column(0).dot(northwest.column(1)), EPSILON);
    }

    private static void assertVector(Vec3 expected, Vec3 actual, String message) {
        assertEquals(0.0, expected.subtract(actual).length(), 1.0E-6, message + ": " + actual);
    }

    @Test
    void collinearDirectionsFailUnlessPerturbed() {
        assertThrows(IllegalArgumentException.class,
                () -> AlignmentFrame.frame(Alignment.UP, new Vec3(0, 3, 0), AlignmentFrame.Snap.NONE, false));
        Mat3 frame = AlignmentFrame.frame(Alignment.UP, new Vec3(0, 3, 0), AlignmentFrame.Snap.NONE, true);
        assertVector(Alignment.UP, frame.column(1));
        assertEquals(1.0, frame.determinant(), EPSILON);
        assertEquals(0.0, frame.column(0).dot(Alignment.UP), EPSILON);
    }

    @Test
    void snapsRestrictDirections() {
        Vec3 skewed = new Vec3(0.9, 0.3, 0.1).normalized();
        assertVector(new Vec3(1, 0, 0), AlignmentFrame.Snap.MULTIPLES_90.apply(skewed));
        Vec3 diagonal = AlignmentFrame.Snap.MULTIPLES_45.apply(new Vec3(1, 0.9, 0.05).normalized());
        assertVector(new Vec3(1, 1, 0).normalized(), diagonal);
        Vec3 twoOne = AlignmentFrame.Snap.DIAGONALS_2_1.apply(new Vec3(2, 1.1, 0).normalized());
        assertVector(new Vec3(2, 1, 0).normalized(), twoOne);
        Vec3 stepped = AlignmentFrame.Snap.MULTIPLES_22_5.apply(new Vec3(Math.cos(0.42), 0, Math.sin(0.42)));
        assertVector(new Vec3(Math.cos(Math.PI / 8), 0, Math.sin(Math.PI / 8)), stepped);
        assertEquals(AlignmentFrame.Snap.MULTIPLES_22_5, AlignmentFrame.Snap.byId("multiples_22.5"));
        assertThrows(IllegalArgumentException.class, () -> AlignmentFrame.Snap.byId("MULTIPLES_7"));
    }

    @Test
    void directionsAcceptAxesCardinalsVectorsAndSums() {
        DirectionExpr.Context view = new DirectionExpr.Context(new Vec3(0, 0, -1));
        assertVector(new Vec3(1, 0.5, -1), DirectionExpr.parse("east-z+(0,0.5,0)").evaluate(view));
        assertVector(new Vec3(0, 2, 0), DirectionExpr.parse("2*up").evaluate(view));
        assertVector(new Vec3(0, 2, 0), DirectionExpr.parse("2up=").evaluate(view));
        assertVector(new Vec3(-1, 2, -1), DirectionExpr.parse("(-1,2,-1)").evaluate(view));
        assertVector(new Vec3(-1, 0, 0), DirectionExpr.parse("left").evaluate(view), "mirando al norte, la izquierda es el oeste");
        assertVector(new Vec3(1, 0, 0), DirectionExpr.parse("derecha").evaluate(view));
        assertVector(new Vec3(0, 0, 1), DirectionExpr.parse("-aim").evaluate(view));
        assertThrows(IllegalArgumentException.class, () -> DirectionExpr.parse("up*east"));
        assertThrows(IllegalArgumentException.class, () -> DirectionExpr.parse("arribaa"));
        assertThrows(IllegalArgumentException.class, () -> DirectionExpr.parse("3"));
    }

    @Test
    void modesComputeTheirDirections() {
        Vec3 aim = new Vec3(1, -1, 0).normalized();
        Alignment.Context context = context(aim).withNormal(new Vec3(0, 0, 1));
        assertVector(aim, Alignment.parse("Aim", true).direction(context).normalized());
        assertVector(new Vec3(0, 0, 1), Alignment.parse("S", true).direction(context));
        assertVector(new Vec3(-10, 1.6, -10), Alignment.parse("PlayerRelative", true).direction(context));
        assertVector(new Vec3(0, 1, 0), Alignment.parse("C", true).direction(context));
        assertVector(new Vec3(1, 0, 0), Alignment.parse("C", false).direction(context));
        assertVector(new Vec3(0, 0, 3), Alignment.parse("Constant(Direction:3*south)", true).direction(context));
        Vec3 upward = Alignment.parse("Upward", true).direction(context).normalized();
        assertEquals(0.0, upward.dot(aim), 1.0E-9, "perpendicular a la mirada");
        assertTrue(upward.y() > 0.7, "y lo más hacia arriba posible");
    }

    @Test
    void randomAndNoiseAreDeterministicPerPlacement() {
        Alignment random = Alignment.parse("Random", true);
        Vec3 first = random.direction(Alignment.Context.at(new Vec3(0, 0, 0), new Vec3(0, 0, 0), Alignment.EAST, 9L));
        Vec3 again = random.direction(Alignment.Context.at(new Vec3(0, 0, 0), new Vec3(0, 0, 0), Alignment.EAST, 9L));
        assertEquals(first, again);
        assertEquals(1.0, first.length(), 1.0E-9);
        Alignment noise = Alignment.parse("Noise(N:Perlin(Freq:0.05))", false);
        Vec3 here = noise.direction(context(Alignment.EAST));
        assertEquals(here, noise.direction(context(Alignment.EAST)));
        Vec3 there = noise.direction(Alignment.Context.at(new Vec3(80, 64, -30), new Vec3(0, 0, 0), Alignment.EAST, 1));
        assertNotEquals(here, there);
    }

    @Test
    void mixedSumsWeightedDirections() {
        Alignment mixed = Alignment.parse("10%C,10%C(D:east)", true);
        assertVector(new Vec3(10, 10, 0), mixed.direction(context(Alignment.EAST)));
        assertThrows(IllegalArgumentException.class, () -> Alignment.parse("C,R", true));
    }

    @Test
    void expressionAlignmentReadsTheChosenSpace() {
        Alignment expression = Alignment.parse("Ex(E:\"=rx=z;rz=-x\",S:N)", false);
        Alignment.Context context = context(Alignment.EAST)
                .withSpaces(new Vec3(5, 0, 3), new Vec3(0.5, 0.0, -0.25));
        assertVector(new Vec3(-0.25, 0, -0.5), expression.direction(context));
        Alignment world = Alignment.parse("E(E:=a=y*2;ry=a)", true);
        assertVector(new Vec3(0, 128, 0), world.direction(context));
        assertThrows(IllegalArgumentException.class, () -> Alignment.parse("E(E:=q=1)", true));
    }

    @Test
    void pathModesNeedAPathAndOrthogonalTurnsAroundTheTangent() {
        Alignment orthogonal = Alignment.parse("O(A:90)", true);
        Alignment.Context onPath = context(Alignment.EAST).withPath(new Vec3(0, 0, -1), Alignment.UP);
        // Avanzando al norte con la normal arriba, 90° es la izquierda (oeste).
        assertVector(new Vec3(-1, 0, 0), orthogonal.direction(onPath));
        assertVector(new Vec3(0, 0, -1), Alignment.parse("Tangential", true).direction(onPath));
        assertThrows(IllegalArgumentException.class, () -> orthogonal.direction(context(Alignment.EAST)));
        assertTrue(orthogonal.needsPath());
    }

    @Test
    void builderAppliesThePipelineInOrder() {
        PlacementSettings settings = PlacementSettings.parse(PlacementSettings.Kind.PLACE,
                "Sh(S:Cuboid,P:stone) C(D:south) C -s 10,20,30");
        PlacementBuilder builder = new PlacementBuilder(settings, java.util.List.of(
                StructureSpec.parse(settings.structure()).procedural(1)), PlacementBuilder.Offsets.NONE);
        Placement placement = builder.build(new Vec3(0.5, 0.5, 0.5),
                Alignment.Context.at(new Vec3(0.5, 0.5, 0.5), new Vec3(0, 0, 0), Alignment.EAST, 1), 1.0);
        assertVector(new Vec3(5, 10, 15), placement.scale(), "bloques por unidad: la mitad de -s");
        // El +y local (alto 20) apunta al sur.
        assertVector(new Vec3(0.5, 0.5, 10.5), placement.toWorld(new Vec3(0, 1, 0)), "la punta de arriba");
        PlacementVoxelizer.Result cells = PlacementVoxelizer.voxelize(java.util.List.of(placement),
                PlacementVoxelizer.Options.defaults());
        assertEquals(10 * 20 * 30, cells.size(), "un bloque por cada bloque de -s");
        long depth = cells.cells().keySet().longStream()
                .map(com.komainu.moxia.edit.PackedBlockPlan::unpackZ).distinct().count();
        assertEquals(20, depth, "el alto queda a lo largo de z");

        PlacementSettings flipped = PlacementSettings.parse(PlacementSettings.Kind.PLACE,
                "Sh(S:Cuboid,P:stone) -k x -c 90 -f Y -r Y -i 5");
        PlacementBuilder second = new PlacementBuilder(flipped, java.util.List.of(
                StructureSpec.parse(flipped.structure()).procedural(1)), PlacementBuilder.Offsets.NONE);
        Placement rotated = second.build(new Vec3(0, 0, 0),
                new Alignment.Context(new Vec3(0, 0, 0), new Vec3(0, 0, 0), DirectionExpr.Context.NONE, null, null,
                        null, new Vec3(0, 0, 0), new Vec3(0, 0, 0), new SplittableRandom(5), null), 1.0);
        assertEquals(1.0, Math.abs(rotated.rotation().determinant()), 1.0E-9);
    }
}
