package com.komainu.moxia.studio.world;

import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seleccionar con el ratón: dibujar la caja arrastrando, tirar de sus caras y el doble clic de la mágica. */
class BoxSelectTest {
    /** Un rayo desde arriba y algo de lado hacia un punto. */
    private static GizmoMath.Ray from(Vec3 eye, Vec3 target) {
        return new GizmoMath.Ray(eye, target.subtract(eye));
    }

    private static final Vec3 EYE = new Vec3(-20.5, 30.5, -20.5);

    @Test
    void draggingFromABlockToAnotherDrawsTheBoxBetweenThem() {
        BoxSelect select = new BoxSelect();
        BoxSelect.Box first = select.press(from(EYE, new Vec3(0.5, 64.5, 0.5)), new int[]{0, 64, 0}, 100.0, 0);
        assertEquals(new BoxSelect.Box(0, 64, 0, 0, 64, 0), first, "el primer clic es una caja de un bloque");
        assertTrue(select.active());
        assertNull(select.drag(from(EYE, new Vec3(0.5, 64.5, 0.5)), new int[]{0, 64, 0}),
                "sin cambiar de bloque no hay nada que mandar");
        BoxSelect.Box drawn = select.drag(from(EYE, new Vec3(9.5, 64.5, 6.5)), new int[]{9, 64, 6});
        assertEquals(new BoxSelect.Box(0, 64, 0, 9, 64, 6), drawn);
        assertEquals(10, drawn.sizeX());
        assertEquals(7, drawn.sizeZ());
        // Hacia atrás también: las esquinas se ordenan solas.
        assertEquals(new BoxSelect.Box(-3, 64, -2, 0, 64, 0),
                select.drag(from(EYE, new Vec3(-2.5, 64.5, -1.5)), new int[]{-3, 64, -2}));
        BoxSelect.Result result = select.release(new int[]{-3, 64, -2}, 1_000);
        assertFalse(result.magic());
        assertFalse(select.active());
    }

    @Test
    void pullingTheTopFaceRaisesTheBoxBlockByBlock() {
        BoxSelect select = new BoxSelect();
        select.known(new BoxSelect.Box(0, 64, 0, 9, 64, 6));
        // Desde un lado, a la altura de la caja, mirando a su cara de arriba desde arriba.
        Vec3 eye = new Vec3(5.0, 80.0, -15.0);
        GizmoMath.Ray atTop = from(eye, new Vec3(5.0, 65.0, 3.0));
        assertEquals(BoxSelect.Face.UP, select.faceUnder(atTop, 100.0));
        select.press(atTop, new int[]{5, 64, 3}, 100.0, 0);
        assertEquals(BoxSelect.Face.UP, select.pulling());
        // El cursor sube por la recta vertical de la cara: la caja crece hacia arriba.
        BoxSelect.Box taller = select.drag(from(eye, new Vec3(5.0, 70.0, 3.0)), null);
        assertEquals(new BoxSelect.Box(0, 64, 0, 9, 69, 6), taller);
        // Bajar más allá del suelo de la caja no la da la vuelta: se queda en una capa.
        BoxSelect.Box flat = select.drag(from(eye, new Vec3(5.0, 40.0, 3.0)), null);
        assertEquals(64, flat.minY());
        assertEquals(64, flat.maxY());
        select.release(null, 0);
    }

    @Test
    void pullingASideFaceMovesOnlyThatSide() {
        BoxSelect select = new BoxSelect();
        select.known(new BoxSelect.Box(0, 64, 0, 9, 67, 6));
        // Mirando la cara este desde arriba y de lado (de frente, el rayo iría por el mismo eje y no se podría tirar).
        Vec3 side = new Vec3(25.0, 75.0, 3.5);
        GizmoMath.Ray grab = from(side, new Vec3(10.0, 65.5, 3.5));
        assertEquals(BoxSelect.Face.EAST, select.faceUnder(grab, 100.0));
        select.press(grab, null, 100.0, 0);
        BoxSelect.Box longer = select.drag(from(side, new Vec3(14.0, 65.5, 3.5)), null);
        assertEquals(new BoxSelect.Box(0, 64, 0, 13, 67, 6), longer, "solo se mueve el lado este");
    }

    @Test
    void aFaceBehindTheTerrainIsNotGrabbed() {
        BoxSelect select = new BoxSelect();
        select.known(new BoxSelect.Box(20, 64, 0, 29, 66, 6));
        GizmoMath.Ray ray = from(new Vec3(0.5, 70.0, 3.5), new Vec3(25.0, 65.0, 3.5));
        assertEquals(BoxSelect.Face.WEST, select.faceUnder(ray, 100.0), "a la vista, se agarra");
        assertNull(select.faceUnder(ray, 8.0), "con una loma delante (a 8 bloques), está tapada");
        BoxSelect.Box started = select.press(ray, new int[]{8, 66, 3}, 8.0, 0);
        assertNull(select.pulling(), "el clic empieza otra caja en la loma");
        assertEquals(new BoxSelect.Box(8, 66, 3, 8, 66, 3), started);
    }

    @Test
    void aDoubleClickOnTheSameBlockAsksForTheMagicSelection() {
        GizmoMath.Ray ray = from(EYE, new Vec3(3.5, 64.5, 3.5));
        int[] block = {3, 64, 3};
        long twice = BoxSelect.DOUBLE_CLICK_MILLIS;
        BoxSelect select = new BoxSelect();
        select.press(ray, block, 100.0, 1_000);
        assertFalse(select.release(block, 1_000).magic(), "un clic solo");
        // El segundo cae sobre la caja de un bloque que dejó el primero: es el doble clic, no tirar de su cara.
        assertEquals(new BoxSelect.Box(3, 64, 3, 3, 64, 3), select.press(ray, block, 100.0, 1_000 + twice));
        assertNull(select.pulling());
        assertTrue(select.release(block, 1_000 + twice).magic(), "el segundo, a tiempo");

        BoxSelect late = new BoxSelect();
        late.press(ray, block, 100.0, 0);
        late.release(block, 0);
        late.press(ray, block, 100.0, twice + 1);
        assertFalse(late.release(block, twice + 1).magic(), "demasiado tarde: se tira de la cara");

        BoxSelect dragged = new BoxSelect();
        dragged.press(ray, block, 100.0, 0);
        dragged.drag(from(EYE, new Vec3(6.5, 64.5, 6.5)), new int[]{6, 64, 6});
        dragged.release(new int[]{6, 64, 6}, 50);
        dragged.press(ray, block, 100.0, 100);
        assertFalse(dragged.release(block, 100).magic(), "si el primero arrastró, no era un clic");
    }

    @Test
    void aClickInTheAirWithoutABoxDoesNothing() {
        BoxSelect select = new BoxSelect();
        assertNull(select.press(from(EYE, new Vec3(0, 200, 0)), null, 100.0, 0));
        assertFalse(select.active());
    }
}
