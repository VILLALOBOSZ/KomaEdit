package com.komainu.moxia.brush.ez;

import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.placement.AlignmentGuide;
import com.komainu.moxia.shape.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las barras de block displays de los clientes sin el mod caen justo sobre las flechas del visualizador. */
class AlignmentGuideDisplaysTest {
    private static final Vec3 ORIGIN = new Vec3(4.5, 65.5, -2.5);

    /** Dónde acaba en el mundo el punto {@code (x, y, z)} del bloque de un display con la entidad en ORIGIN. */
    private static Vec3 world(AlignmentGuideDisplays.Transform transform, float x, float y, float z) {
        Vector3f point = new Vector3f(x, y, z).mul(transform.scale());
        transform.rotation().transform(point);
        point.add(transform.translation());
        return ORIGIN.add(new Vec3(point.x, point.y, point.z));
    }

    private static void assertClose(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distance(actual) < 1.0E-4, "esperaba " + expected + " y salió " + actual);
    }

    @Test
    void eachBeamRunsAlongItsAxis() {
        Vec3 primary = new Vec3(-1, 2, -1).normalized();
        Vec3 x = new Vec3(-5, -2, 1).normalized();
        Vec3 z = x.cross(primary).normalized();
        AlignmentGuide.Result result = new AlignmentGuide.Result(AlignmentGuide.State.READY, ORIGIN, x, primary, z,
                primary, new Vec3(-1, 0, 0), false);
        List<AlignmentGuideDisplays.Beam> beams = AlignmentGuideDisplays.beams(result, 3.0);

        assertEquals(7, beams.size(), "asta y punta por eje y el centro");
        assertEquals(GizmoGeometry.GREEN, beams.getFirst().argb());
        for (AlignmentGuideDisplays.Beam beam : beams) {
            AlignmentGuideDisplays.Transform transform = AlignmentGuideDisplays.transform(beam, ORIGIN);
            if (beam.part() == AlignmentGuideDisplays.Part.CENTER) {
                assertClose(ORIGIN, world(transform, 0.5f, 0.5f, 0.5f));
                continue;
            }
            assertClose(beam.from(), world(transform, 0.5f, 0.0f, 0.5f));
            assertClose(beam.from().add(beam.direction().multiply(beam.length())),
                    world(transform, 0.5f, 1.0f, 0.5f));
            // El grosor queda repartido a los dos lados del eje.
            Vec3 side = world(transform, 0.0f, 0.5f, 0.5f).subtract(world(transform, 1.0f, 0.5f, 0.5f));
            assertEquals(beam.width(), side.length(), 1.0E-4);
            assertEquals(0.0, side.dot(beam.direction()), 1.0E-4);
        }
        Vec3 greenTip = ORIGIN.add(primary.multiply(3.0));
        AlignmentGuideDisplays.Beam tip = beams.get(1);
        assertClose(greenTip, tip.from().add(tip.direction().multiply(tip.length())));
    }

    @Test
    void waitingShowsOnlyWhatIsKnownAndGrowsWithDistance() {
        AlignmentGuide.Result waiting = new AlignmentGuide.Result(AlignmentGuide.State.WAITING, ORIGIN, null, null,
                null, new Vec3(0, 1, 0), null, false);
        assertEquals(3, AlignmentGuideDisplays.beams(waiting, 2.0).size());
        AlignmentGuide.Result invalid = new AlignmentGuide.Result(AlignmentGuide.State.INVALID, ORIGIN, null, null,
                null, new Vec3(0, 1, 0), new Vec3(0, 1, 0), false);
        assertEquals(AlignmentGuide.INVALID_MARK, AlignmentGuideDisplays.beams(invalid, 2.0).getLast().argb());

        assertTrue(AlignmentGuideDisplays.length(40.0) > AlignmentGuideDisplays.length(10.0));
        assertEquals(1.2, AlignmentGuideDisplays.length(1.0), 1.0E-9);
        assertEquals(16.0, AlignmentGuideDisplays.length(1_000.0), 1.0E-9);
    }
}
