package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Constant(D:aim)} en un pincel: como en ezEdits, la mirada se toma al configurarlo y no en cada clic, salvo con
 * {@code -mirada clic}. {@code Aim} sigue siendo la mirada de cada clic.
 */
class AimBindingTest {
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** La dirección de una alineación en un clic mirando hacia {@code aim}. */
    private static Vec3 atClick(Alignment alignment, Vec3 aim) {
        return alignment.direction(Alignment.Context.at(new Vec3(0, 0, 0), new Vec3(0, 2, 0), aim, 1L));
    }

    private static void assertClose(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-9, "x de " + actual);
        assertEquals(expected.y(), actual.y(), 1.0E-9, "y de " + actual);
        assertEquals(expected.z(), actual.z(), 1.0E-9, "z de " + actual);
    }

    @Test
    void constantAimIsTheLookWhenTheBrushIsBound() {
        PlacementSettings bound = PlacementSettings.parse(PlacementSettings.Kind.PLACE, "Clipboard C(D:aim) "
                + "C(D:left)", true).bindAim(new DirectionExpr.Context(SOUTH));

        // Se configuró mirando al sur; después se hace clic mirando al este y hacia arriba.
        assertClose(SOUTH, atClick(bound.primary(), EAST));
        assertClose(SOUTH, atClick(bound.primary(), UP));
        // La izquierda de quien mira al sur es el este, y se queda así.
        assertClose(EAST, atClick(bound.secondary(), new Vec3(-1, 0, 0)));
        assertFalse(bound.aimEachClick());
    }

    @Test
    void aimEachClickKeepsTheOldBehaviour() {
        PlacementSettings settings = PlacementSettings.parse(PlacementSettings.Kind.PLACE,
                "Clipboard C(D:aim) Constant -mirada clic", true);
        PlacementSettings bound = settings.bindAim(new DirectionExpr.Context(SOUTH));

        assertTrue(settings.aimEachClick());
        assertSame(settings, bound);
        assertClose(EAST, atClick(bound.primary(), EAST));
        assertTrue(PlacementSettings.parse(PlacementSettings.Kind.SCATTER, "Cl C(D:aim) -aim click", true)
                .aimEachClick());
        assertFalse(PlacementSettings.parse(PlacementSettings.Kind.ARRAY, "Cl Tangential C(D:aim) -mirada fija",
                true).aimEachClick());
    }

    @Test
    void aimModeStaysTheLookOfEachClick() {
        PlacementSettings bound = PlacementSettings.parse(PlacementSettings.Kind.PLACE, "Clipboard Aim Constant",
                true).bindAim(new DirectionExpr.Context(SOUTH));

        assertClose(EAST, atClick(bound.primary(), EAST));
        assertClose(UP, atClick(bound.primary(), UP));
    }

    @Test
    void mixedAlignmentsAndOrientationAxisBindTheirConstants() {
        PlacementSettings bound = PlacementSettings.parse(PlacementSettings.Kind.PLACE,
                "Clipboard 3%C(D:aim),1%C(D:up) Constant -k aim -c 90", true).bindAim(new DirectionExpr.Context(SOUTH));

        Vec3 mixed = atClick(bound.primary(), EAST);
        assertEquals(0.0, mixed.x(), 1.0E-9);
        assertEquals(1.0, mixed.y(), 1.0E-9);
        assertEquals(3.0, mixed.z(), 1.0E-9);
        assertClose(SOUTH, bound.orientationAxis().evaluate(new DirectionExpr.Context(EAST)));
    }

    @Test
    void fixedDirectionsAreUntouched() {
        Alignment constant = Alignment.parse("C(D:(1,2,0))", true);
        assertSame(constant, constant.bind(new DirectionExpr.Context(SOUTH)));
        DirectionExpr east = DirectionExpr.parse("east");
        assertSame(east, east.bind(new DirectionExpr.Context(SOUTH)));
    }

    @Test
    void theSwitchOnlyMakesSenseInBrushes() {
        assertThrows(IllegalArgumentException.class, () -> PlacementSettings.parse(PlacementSettings.Kind.PLACE,
                "Clipboard C(D:aim) -mirada clic"));
        assertThrows(IllegalArgumentException.class, () -> PlacementSettings.parse(PlacementSettings.Kind.PLACE,
                "Clipboard -mirada a veces", true));
    }
}
