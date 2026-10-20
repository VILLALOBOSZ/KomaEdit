package com.komainu.moxia.studio;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeParameters;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioControlsTest {
    private static final NumericRange RANGE = NumericRange.of(1, 128, 1);

    @Test
    void stepsClampAndUseTheBigStep() {
        assertEquals(6, RANGE.stepped(5, 1, false));
        assertEquals(10, RANGE.stepped(5, 1, true));
        assertEquals(1, RANGE.stepped(3, -1, true));
        assertEquals(128, RANGE.stepped(128, 1, false));
        NumericRange roll = new NumericRange(-180, 180, 5, 45);
        assertEquals(-45, roll.stepped(0, -1, true));
    }

    @Test
    void parsesTypedValues() {
        assertEquals(OptionalInt.of(40), RANGE.parse(" 40 "));
        assertEquals(OptionalInt.of(128), RANGE.parse("9999"));
        assertEquals(OptionalInt.of(1), RANGE.parse("−3"));
        assertTrue(RANGE.parse("").isEmpty());
        assertTrue(RANGE.parse("-").isEmpty());
        assertTrue(RANGE.parse("12a").isEmpty());
        assertTrue(RANGE.parse("99999999999").isEmpty());
    }

    @Test
    void dragsInWholeStepsAndShiftGoesFine() {
        NumericRange short1 = NumericRange.of(1, 32, 1);
        assertEquals(10, short1.dragged(10, 5, false));
        assertEquals(12, short1.dragged(10, 2 * NumericRange.PIXELS_PER_STEP + 1, false));
        assertEquals(9, short1.dragged(10, -NumericRange.FINE_PIXELS_PER_STEP, true));
        assertEquals(10, short1.dragged(10, NumericRange.PIXELS_PER_STEP, true), "fino: hace falta más recorrido");
        assertTrue(RANGE.dragged(10, NumericRange.DRAG_SPAN, false) >= 127, "un rango largo cabe en un arrastre");
        assertEquals(0.0, RANGE.fraction(1));
        assertEquals(1.0, RANGE.fraction(500));
    }

    @Test
    void longRangesCoverTheirWholeSpanInOneDrag() {
        NumericRange extrude = new NumericRange(1, 65_536, 16, 256);
        assertTrue(extrude.dragged(1, NumericRange.DRAG_SPAN, false) >= 65_500, "un arrastre largo llega al final");
        assertTrue(extrude.dragged(1, NumericRange.DRAG_SPAN / 2.0, false) > 30_000);
        assertEquals(1 + 16 * 2, extrude.dragged(1, 2 * NumericRange.FINE_PIXELS_PER_STEP, true));
    }

    @Test
    void presetsAndVariantsStayInsideValidParameters() {
        for (PathShape shape : PathShape.values()) {
            for (ShapeVariants.Preset preset : ShapeVariants.Preset.values()) {
                assertValid(shape, ShapeVariants.preset(shape, preset));
            }
            for (int seed = 1; seed <= 200; seed++) {
                assertValid(shape, ShapeVariants.variant(shape, seed));
            }
        }
        assertEquals(ShapeVariants.defaults(PathShape.COIL),
                ShapeVariants.preset(PathShape.COIL, ShapeVariants.Preset.DEFAULT));
    }

    @Test
    void variantsAreDeterministicAndVaried() {
        assertEquals(ShapeVariants.variant(PathShape.SCALES, 4821), ShapeVariants.variant(PathShape.SCALES, 4821));
        assertNotEquals(ShapeVariants.variant(PathShape.SCALES, 4821), ShapeVariants.variant(PathShape.COIL, 4821));
        Set<ShapeVariants.Proportions> distinct = new HashSet<>();
        for (int seed = 1; seed <= 50; seed++) {
            distinct.add(ShapeVariants.variant(PathShape.SCALES, seed));
        }
        assertTrue(distinct.size() > 40);
    }

    @Test
    void largeBuildsNeedASecondPressWithTheSameCount() {
        BuildEstimate estimate = new BuildEstimate();
        assertTrue(estimate.confirm(10_000, 0));
        assertTrue(estimate.confirm(-1, 0));
        assertFalse(estimate.confirm(400_000, 1_000));
        assertTrue(estimate.armed(2_000));
        assertTrue(estimate.confirm(400_000, 2_000));
        assertFalse(estimate.armed(2_000));

        assertFalse(estimate.confirm(400_000, 10_000));
        assertFalse(estimate.confirm(400_000, 10_000 + BuildEstimate.CONFIRM_MILLIS + 1));
        assertFalse(estimate.confirm(500_000, 16_000));
        assertTrue(estimate.confirm(500_000, 17_000));
    }

    @Test
    void classifiesAndFormatsCounts() {
        assertEquals(BuildEstimate.Level.UNKNOWN, BuildEstimate.level(-1));
        assertEquals(BuildEstimate.Level.NORMAL, BuildEstimate.level(1_000));
        assertEquals(BuildEstimate.Level.LARGE, BuildEstimate.level(BuildEstimate.LARGE));
        assertEquals(BuildEstimate.Level.OVER_DEFAULT_LIMIT, BuildEstimate.level(2_000_000));
        assertEquals("950", BuildEstimate.compact(950));
        assertEquals("12,3 k", BuildEstimate.compact(12_345));
        assertEquals("250 k", BuildEstimate.compact(250_000));
        assertEquals("1,23 M", BuildEstimate.compact(1_234_567));
    }

    @Test
    void shortcutsAreUniqueAndTranslated() {
        Set<String> keys = new HashSet<>();
        for (StudioShortcuts.Shortcut shortcut : StudioShortcuts.ALL) {
            assertTrue(keys.add(shortcut.id()), shortcut.id());
            assertTrue(shortcut.translationKey().startsWith("gui.komaedit.shortcut."));
        }
    }

    private static void assertValid(PathShape shape, ShapeVariants.Proportions proportions) {
        assertDoesNotThrow(() -> new ShapeParameters(shape, proportions.radius(), proportions.spacing(),
                proportions.thickness(), proportions.rollDegrees()), shape.name());
        assertTrue(ShapeVariants.HEIGHT.clamp(proportions.height()) == proportions.height());
    }
}
