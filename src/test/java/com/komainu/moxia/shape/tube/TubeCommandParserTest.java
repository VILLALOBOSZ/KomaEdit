package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeExpression;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.smooth.SmoothMode;
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
    void parsesSmoothBlockFlags() {
        TubeSettings stairs = TubeSettings.from(TubeCommandParser.parse(
                PathShape.SWEEP, "circle", "8", "stone -w stairs -c 0.7", "stone").parameters());
        assertEquals(SmoothMode.STAIRS, stairs.smoothMode());
        assertEquals(0.7, stairs.coverage(), 1.0E-9);

        TubeSettings slabs = TubeSettings.from(TubeCommandParser.parse(
                PathShape.BRAID, null, "6", "-w slabs", "oak_log").parameters());
        assertEquals(SmoothMode.SLABS, slabs.smoothMode());
        assertEquals(0.5, slabs.coverage(), 0.0, "cobertura por defecto");

        TubeSettings implied = TubeSettings.from(TubeCommandParser.parse(
                PathShape.NOISE_TUBE, null, "6", "-c 0.3", "stone").parameters());
        assertEquals(SmoothMode.STAIRS, implied.smoothMode(), "-c sin -w activa losas y escaleras");

        TubeSettings plain = TubeSettings.from(TubeCommandParser.parse(
                PathShape.SWEEP, "circle", "8", "", "stone").parameters());
        assertEquals(SmoothMode.NONE, plain.smoothMode());

        assertThrows(IllegalArgumentException.class,
                () -> TubeCommandParser.parse(PathShape.SWEEP, "circle", "8", "-w triangles", "stone"));
        assertThrows(IllegalArgumentException.class,
                () -> TubeCommandParser.parse(PathShape.SWEEP, "circle", "8", "-w stairs -c 1.5", "stone"));

        ShapeExpression expression = ShapeExpression.parse("braid(smooth:slabsandstairs,cobertura:0.8)");
        TubeSettings fromExpression = TubeSettings.from(
                expression.shape().defaults().withOptions(expression.options()));
        assertEquals(SmoothMode.STAIRS, fromExpression.smoothMode());
        assertEquals(0.8, fromExpression.coverage(), 1.0E-9);
    }

    @Test
    void parsesFullSmoothBlockProfiles() {
        TubeCommandParser.Result result = TubeCommandParser.parse(PathShape.SWEEP, "circle", "8",
                "stone -w SlabsAndStairs(C:0.7,Slab:oak)", "stone");
        TubeSettings settings = TubeSettings.from(result.parameters());
        assertEquals(SmoothMode.STAIRS, settings.smoothMode());
        assertEquals(0.7, settings.coverage(), 1.0E-9);
        assertEquals("oak", result.materials().get(com.komainu.moxia.shape.smooth.PieceVariant.SLAB));
        assertEquals("stone", result.pattern());

        // Con espacios tras las comas, el perfil sigue siendo una sola bandera.
        TubeCommandParser.Result spaced = TubeCommandParser.parse(PathShape.SWEEP, "circle", "8",
                "-w SlabsAndStairs(Slab:oak, Stair:spruce) granite", "stone");
        assertEquals("granite", spaced.pattern());
        assertEquals("spruce", spaced.materials().get(com.komainu.moxia.shape.smooth.PieceVariant.STAIR));

        for (SmoothMode mode : SmoothMode.values()) {
            TubeSettings named = TubeSettings.from(TubeCommandParser.parse(PathShape.SWEEP, "circle", "8",
                    "-w " + mode.ezName(), "stone").parameters());
            assertEquals(mode, named.smoothMode(), mode.ezName());
        }
        // -c antes de -w no se pierde si el perfil no fija su cobertura.
        TubeSettings order = TubeSettings.from(TubeCommandParser.parse(PathShape.SWEEP, "circle", "8",
                "-c 0.9 -w Layers", "stone").parameters());
        assertEquals(SmoothMode.LAYERS, order.smoothMode());
        assertEquals(0.9, order.coverage(), 1.0E-9);
        assertTrue(new TubeCommandParser.Result(result.parameters(), "stone").materials().isEmpty(),
                "el constructor antiguo de Result sigue valiendo");
        assertThrows(IllegalArgumentException.class, () -> TubeCommandParser.parse(PathShape.SWEEP, "circle", "8",
                "-w Slabs(Stair:oak)", "stone"));
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
