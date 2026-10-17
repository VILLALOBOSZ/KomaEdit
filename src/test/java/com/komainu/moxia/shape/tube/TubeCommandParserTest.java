package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeExpression;
import com.komainu.moxia.shape.ShapeOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TubeCommandParserTest {
    @Test
    void parsesProfileRadiiPatternAndFlags() {
        TubeCommandParser.Result result = TubeCommandParser.parse(
                PathShape.SWEEP,
                "star7",
                "2,0.3:12,2",
                "polished_andesite -e round -n horizontal -t 45 -r 30,210 -p 0.5:-0.2:0 -hollow 2",
                "stone"
        );
        ShapeOptions options = result.parameters().options();
        assertEquals("polished_andesite", result.pattern());
        assertEquals(12, result.parameters().radius());
        assertEquals(30, result.parameters().rollDegrees());
        assertEquals(TubeSettings.TubeProfile.STAR.ordinal(), options.get("profile", -1), 0.0);
        assertEquals(7.0, options.get("sides", -1), 0.0);
        assertEquals(TubeSettings.EndMode.ROUND.ordinal(), options.get("end", -1), 0.0);
        assertEquals(TubeSettings.NormalMode.HORIZONTAL.ordinal(), options.get("normal", -1), 0.0);
        assertEquals(45.0, options.get("twist", 0), 0.0);
        assertEquals(210.0, options.get("rollend", 0), 0.0);
        assertEquals(0.5, options.get("tension", 0), 0.0);
        assertEquals(-0.2, options.get("bias", 0), 0.0);
        assertEquals(2.0, options.get("wall", 0), 0.0);
        assertEquals(0.3, options.get("p1", 0), 1.0E-9);

        TubeSettings settings = TubeSettings.from(result.parameters());
        assertTrue(settings.hollow());
        assertTrue(settings.rollRange());
        assertEquals(7, settings.sides());
        assertEquals(1.0, settings.radii().at(0.3), 1.0E-9);
    }

    @Test
    void usesTheDefaultPatternAndUnquotedRadii() {
        TubeCommandParser.Result result = TubeCommandParser.parse(PathShape.BRAID, null, "4_9_4", "-k 4", "oak_log");
        assertEquals("oak_log", result.pattern());
        assertEquals(9, result.parameters().radius());
        assertEquals(4, TubeSettings.from(result.parameters()).strands());
    }

    @Test
    void rejectsUnknownFlagsAndBadValues() {
        assertThrows(IllegalArgumentException.class,
                () -> TubeCommandParser.parse(PathShape.SWEEP, "circle", "8", "-z 3", "stone"));
        assertThrows(IllegalArgumentException.class,
                () -> TubeCommandParser.parse(PathShape.SWEEP, "circle", "8", "-e sideways", "stone"));
        assertThrows(IllegalArgumentException.class,
                () -> TubeCommandParser.parse(PathShape.SWEEP, "hexagon", "8", "", "stone"));
        assertThrows(IllegalArgumentException.class,
                () -> TubeCommandParser.parse(PathShape.SWEEP, "circle", "8", "stone dirt", "stone"));
        assertThrows(IllegalArgumentException.class,
                () -> TubeCommandParser.parse(PathShape.CHAINLINK, null, "8", "", "stone"));
    }

    @Test
    void shapeExpressionsAcceptSymbolicTubeValues() {
        ShapeExpression expression = ShapeExpression.parse("sweep(profile:rope,end:spike,normal:upright,hollow:true)");
        assertEquals(PathShape.SWEEP, expression.shape());
        TubeSettings settings = TubeSettings.from(expression.shape().defaults().withOptions(expression.options()));
        assertEquals(TubeSettings.TubeProfile.ROPE, settings.profile());
        assertEquals(TubeSettings.EndMode.SPIKE, settings.endMode());
        assertEquals(TubeSettings.NormalMode.UPRIGHT, settings.normalMode());
        assertTrue(settings.hollow());
        assertEquals(90.0, settings.twistPerDiameter(), 0.0, "la cuerda trae su torsión por defecto");
        assertFalse(settings.filled());
    }
}
