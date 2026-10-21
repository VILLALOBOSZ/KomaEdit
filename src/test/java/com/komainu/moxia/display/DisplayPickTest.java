package com.komainu.moxia.display;

import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Elegir displays sin caja de colisión: el rayo contra el volumen girado y escalado de cada uno. */
class DisplayPickTest {
    private static final Vec3 HALF = new Vec3(0.5, 0.5, 0.5);

    private static GizmoMath.Ray ray(Vec3 from, Vec3 to) {
        return new GizmoMath.Ray(from, to.subtract(from));
    }

    private static DisplayPick.Candidate block(int id, DisplayPose pose) {
        return new DisplayPick.Candidate(id, DisplayVolume.block().world(pose));
    }

    @Test
    void aBlockDisplayIsHitWhereItsCubeIs() {
        DisplayPose pose = DisplayPose.centered(new Vec3(0.5, 64.5, 0.5), Quat.IDENTITY, DisplayPose.ONE, HALF);
        Optional<DisplayPick.Hit> hit = DisplayPick.pick(ray(new Vec3(0.5, 64.5, -5), new Vec3(0.5, 64.5, 0.5)),
                List.of(block(7, pose)), 64);
        assertTrue(hit.isPresent());
        assertEquals(7, hit.get().id());
        assertEquals(5.0, hit.get().distance(), 1.0E-9, "entra por la cara norte, en z = 0");
        assertEquals(0.0, hit.get().point().z(), 1.0E-9);
    }

    @Test
    void aRotatedDisplayIsHitByItsTurnedShapeNotByItsOldBox() {
        // Escalado a 1/4 y girado 45°: un rayo que pasa por la esquina del cubo sin girar ya no lo toca.
        DisplayPose pose = DisplayPose.centered(new Vec3(0.5, 64.5, 0.5), Quat.axis(1, Math.toRadians(45)),
                new Vec3(0.5, 0.5, 0.5), HALF);
        DisplayPick.Candidate candidate = block(1, pose);
        // Sin girar, el cubo de 0.5 iría de 0.25 a 0.75 en X y Z; girado, su planta es un rombo que llega a ±0.354
        // en los ejes y no llena las esquinas.
        GizmoMath.Ray oldCorner = ray(new Vec3(0.74, 70, 0.74), new Vec3(0.74, 60, 0.74));
        assertTrue(Double.isNaN(DisplayPick.distance(oldCorner, candidate.unitToWorld())),
                "la esquina de la caja sin girar ya está vacía");
        GizmoMath.Ray outside = ray(new Vec3(0.5 + 0.36, 64.5, -5), new Vec3(0.5 + 0.36, 64.5, 5));
        assertTrue(Double.isNaN(DisplayPick.distance(outside, candidate.unitToWorld())));
        GizmoMath.Ray diagonalTip = ray(new Vec3(0.5 + 0.35, 64.5, -5), new Vec3(0.5 + 0.35, 64.5, 5));
        assertTrue(Double.isFinite(DisplayPick.distance(diagonalTip, candidate.unitToWorld())),
                "la punta girada llega más lejos que el cubo sin girar (0.25)");
    }

    @Test
    void theNearestWinsAndFarOnesAreIgnored() {
        DisplayPose near = DisplayPose.centered(new Vec3(0.5, 64.5, 3.5), Quat.IDENTITY, DisplayPose.ONE, HALF);
        DisplayPose far = DisplayPose.centered(new Vec3(0.5, 64.5, 9.5), Quat.IDENTITY, DisplayPose.ONE, HALF);
        GizmoMath.Ray ray = ray(new Vec3(0.5, 64.5, 0), new Vec3(0.5, 64.5, 1));
        assertEquals(2, DisplayPick.pick(ray, List.of(block(1, far), block(2, near)), 64).orElseThrow().id());
        assertTrue(DisplayPick.pick(ray, List.of(block(1, far)), 5).isEmpty(), "más allá del alcance");
        // Un bloque delante tapa al display: el cliente pasa la distancia al bloque como alcance.
        assertTrue(DisplayPick.pick(ray, List.of(block(2, near)), 2.5).isEmpty());
    }

    @Test
    void aDisplayWithZeroScaleCannotBePicked() {
        DisplayPose flat = DisplayPose.centered(new Vec3(0.5, 64.5, 0.5), Quat.IDENTITY, new Vec3(1, 0, 1), HALF);
        assertTrue(DisplayPick.pick(ray(new Vec3(0.5, 70, 0.5), new Vec3(0.5, 60, 0.5)), List.of(block(1, flat)),
                64).isEmpty());
    }

    @Test
    void textsAreThinSignsThatGrowUpwards() {
        // «Hola» de 24 píxeles, una línea: 0.6 de ancho y 0.275 de alto.
        DisplayVolume volume = DisplayVolume.text(24, 1);
        DisplayPose pose = DisplayPose.at(new Vec3(0.5, 65, 0.5));
        DisplayPick.Candidate sign = new DisplayPick.Candidate(3, volume.world(pose));
        GizmoMath.Ray front = ray(new Vec3(0.5, 65.1, 5), new Vec3(0.5, 65.1, 0));
        assertTrue(DisplayPick.pick(front, List.of(sign), 64).isPresent());
        GizmoMath.Ray above = ray(new Vec3(0.5, 65.4, 5), new Vec3(0.5, 65.4, 0));
        assertTrue(DisplayPick.pick(above, List.of(sign), 64).isEmpty());
        GizmoMath.Ray below = ray(new Vec3(0.5, 64.95, 5), new Vec3(0.5, 64.95, 0));
        assertTrue(DisplayPick.pick(below, List.of(sign), 64).isEmpty(), "crece hacia arriba desde su origen");
        assertEquals(0.275, volume.size(pose).y(), 1.0E-9);
    }

    @Test
    void anItemIsCenteredOnItsEntity() {
        DisplayVolume volume = DisplayVolume.item(false);
        assertEquals(0.0, volume.center().distance(new Vec3(0, 0, 0)), 1.0E-12);
        // En marco (fixed) un bloque sale a la mitad: el modelo escala 0.5.
        DisplayVolume framed = DisplayVolume.item(new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(0.5, 0.5, 0.5),
                false);
        assertEquals(0.5, framed.size(DisplayPose.at(new Vec3(0, 0, 0))).x(), 1.0E-12);
        // Un objeto plano es una lámina de 1/16.
        DisplayVolume flat = DisplayVolume.item(true);
        assertEquals(1.0 / 16.0, flat.size(DisplayPose.at(new Vec3(0, 0, 0))).z(), 1.0E-12);
    }

    @Test
    void theServerEstimateOfATextUsesItsLength() {
        DisplayVolume one = DisplayVolume.estimatedText("Hola", 200);
        DisplayVolume two = DisplayVolume.estimatedText("Hola\nmundo", 200);
        DisplayPose pose = DisplayPose.at(new Vec3(0, 0, 0));
        assertTrue(two.size(pose).y() > one.size(pose).y());
        DisplayVolume wrapped = DisplayVolume.estimatedText("x".repeat(100), 60);
        assertEquals(60 * DisplayVolume.TEXT_PIXEL + DisplayVolume.TEXT_PIXEL, wrapped.size(pose).x(), 1.0E-9,
                "una línea larga se parte en el ancho");
        assertEquals((10 * DisplayVolume.TEXT_LINE_PIXELS + 1) * DisplayVolume.TEXT_PIXEL, wrapped.size(pose).y(),
                1.0E-9, "600 píxeles en líneas de 60: diez líneas");
        assertFalse(one.size(pose).x() <= 0.0);
    }
}
