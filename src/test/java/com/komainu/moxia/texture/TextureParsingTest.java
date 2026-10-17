package com.komainu.moxia.texture;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureParsingTest {
    @Test
    void simpleModesReadPositionalArgumentsLikeEzEdits() {
        TextureCommandParser.Request ambient = TextureCommandParser.parse("ambient",
                "#existing ##Grayscale 2 0.2 0.3 -w", 1L);
        assertEquals("#existing", ambient.mask());
        assertEquals("##Grayscale", ambient.palette());
        assertTrue(ambient.shaped());
        assertEquals(new TextureFields.Ambient(2, 0.2, 0.3), ambient.field());

        TextureCommandParser.Request defaults = TextureCommandParser.parse("ambiente", "* stone,andesite", 1L);
        assertEquals(new TextureFields.Ambient(3, 0.0, 0.0), defaults.field());
        assertFalse(defaults.shaped());
        assertEquals(0.0, defaults.dither());

        TextureCommandParser.Request axis = TextureCommandParser.parse("axisgradient", "#existing -##Grayscale x -r", 1L);
        assertEquals("-##Grayscale", axis.palette(), "una paleta invertida no es una bandera");
        assertEquals(new TextureFields.AxisGradient(0, true), axis.field());
    }

    @Test
    void advancedEqualsTheSimpleMode() {
        TextureField simple = TextureCommandParser.parse("ambient", "#existing ##Grayscale 2 0.2 0.3", 1L).field();
        TextureField advanced = TextureCommandParser.parse("advanced",
                "#existing ##Grayscale Ambient(Radius:2,Brightness:0.2,Contrast:0.3)", 1L).field();
        assertEquals(simple, advanced);
        TextureField spaced = TextureCommandParser.parse("advanced",
                "#existing ##Grayscale Ambient(Radius:2, Brightness:0.2, Contrast:0.3)", 1L).field();
        assertEquals(simple, spaced, "los espacios dentro de los paréntesis no parten el argumento");
    }

    @Test
    void seedsAndFlagsWithValues() {
        TextureCommandParser.Request cells = TextureCommandParser.parse("cells", "#existing ##Magma 20 0 0.5 -s 42 -r 9",
                99L);
        assertEquals(new TextureFields.Cells(20, 0.0, 0.5, 42L, 9), cells.field());
        assertEquals(42L, cells.seed());
        TextureCommandParser.Request random = TextureCommandParser.parse("cells", "#existing ##Magma 20 -s -1", 99L);
        assertEquals(99L, random.seed(), "-s -1 usa la semilla al azar del comando");

        TextureCommandParser.Request flow = TextureCommandParser.parse("flow",
                "#existing ##Grayscale 0.8 20 0.4 2 @@ridged(Freq:0.12) -m -g down -f", 5L);
        TextureFields.Flow field = assertInstanceOf(TextureFields.Flow.class, flow.field());
        assertEquals(0.8, field.exposure());
        assertEquals(20, field.iterations());
        assertEquals(0.5, field.momentum(), "-m sin valor pesa 0,5");
        assertTrue(field.gravity()[1] < 0);
        assertTrue(field.fill());

        TextureCommandParser.Request flowWeights = TextureCommandParser.parse("flow", "#existing ##Grayscale -m 0.8", 5L);
        assertEquals(0.8, ((TextureFields.Flow) flowWeights.field()).momentum());
        assertNull(((TextureFields.Flow) flowWeights.field()).gravity());
    }

    @Test
    void lightsAndRegionModes() {
        TextureFields.Light point = assertInstanceOf(TextureFields.Light.class, TextureCommandParser.parse("pointlight",
                "#existing ##Grayscale 30 2 10,80 -l 1,64,-3 -o 0.5 -r 2 -f", 1L).field());
        assertArrayEquals(new double[]{1, 64, -3}, point.position());
        assertEquals(30, point.falloffRange());
        assertFalse(point.falloff());
        assertEquals(10, point.from());
        assertEquals(80, point.to());
        assertEquals(0.5, point.occlusion());

        TextureFields.Light sun = assertInstanceOf(TextureFields.Light.class, TextureCommandParser.parse("sunlight",
                "#existing ##Grayscale 1 0,120 -l east", 1L).field());
        assertArrayEquals(new double[]{1, 0, 0}, sun.direction());

        TextureCommandParser.Request blend = TextureCommandParser.parse("blend", "##Grayscale 1.5 -v -w", 1L);
        assertEquals(1.5, blend.blendRadius());
        assertTrue(blend.fullBlend());
        assertEquals(0.5, blend.dither(), "blend mezcla con tramado por defecto");
        assertNull(blend.mask());

        TextureCommandParser.Request shift = TextureCommandParser.parse("shift", "##Grayscale -2", 1L);
        assertEquals(-2, shift.shift());

        assertEquals(new TextureFields.BlockLight(true, true, false),
                TextureCommandParser.parse("blocklight", "#existing ##Grayscale -v -s", 1L).field());
    }

    @Test
    void parserReportsMistakes() {
        assertThrows(IllegalArgumentException.class, () -> TextureCommandParser.parse("glow", "* stone", 1L));
        assertThrows(IllegalArgumentException.class, () -> TextureCommandParser.parse("ambient", "#existing", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> TextureCommandParser.parse("ambient", "#existing ##Grayscale 2 0 0 7", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> TextureCommandParser.parse("ambient", "#existing ##Grayscale -q", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> TextureCommandParser.parse("shift", "##Grayscale 0", 1L));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> TextureCommandParser.parse("advanced", "* ##Grayscale Add(T1:Ambient)", 1L));
        assertTrue(missing.getMessage().contains("T2"), missing.getMessage());
    }

    @Test
    void textureSpecBuildsNestedCombinations() {
        TextureField average = TextureSpec.parse("WeightedAverage(T1:Sun(),T2:Ambient())", 3L);
        TextureFields.Combine combine = assertInstanceOf(TextureFields.Combine.class, average);
        assertEquals(TextureFields.Operation.WEIGHTED_AVERAGE, combine.operation());
        assertInstanceOf(TextureFields.Light.class, combine.first());
        assertEquals(new TextureFields.Ambient(3, 0, 0), combine.second());

        TextureFields.Smooth blend = assertInstanceOf(TextureFields.Smooth.class,
                TextureSpec.parse("Blend(T:Flow(Noise:@@ridged(Freq:0.12)),Radius:0.7)", 3L));
        assertEquals(0.7, blend.radius());
        assertInstanceOf(TextureFields.Flow.class, blend.texture());

        TextureFields.Combine darken = assertInstanceOf(TextureFields.Combine.class,
                TextureSpec.parse("Darken(T1:Noise(Noise:@@smoothcells(freq:0.5)),T2:Flow)", 3L));
        assertTrue(((TextureFields.Flow) darken.second()).fill(), "en una combinación el flujo rellena sus huecos");

        assertInstanceOf(TextureFields.Adjust.class, TextureSpec.parse("Adjust(T:Pointlight,Contrast:0.5)", 3L));
        assertInstanceOf(TextureFields.Invert.class, TextureSpec.parse("invert(t:curvature(r:2))", 3L));
        assertEquals(5, TextureSpec.parse("Add(T1:Ambient(Radius:4),T2:Curvature)", 3L).padding() - 5);
    }

    @Test
    void textureSpecRejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> TextureSpec.parse("Ambient(Radius:2", 1L));
        assertThrows(IllegalArgumentException.class, () -> TextureSpec.parse("Ambient(Size:2)", 1L));
        assertThrows(IllegalArgumentException.class, () -> TextureSpec.parse("Sparkle()", 1L));
        assertThrows(IllegalArgumentException.class, () -> TextureSpec.parse("Invert()", 1L));
        String deep = "Invert(T:".repeat(12) + "Ambient" + ")".repeat(12);
        assertThrows(IllegalArgumentException.class, () -> TextureSpec.parse(deep, 1L));
    }

    @Test
    void noisePresetsAndIntervals() {
        assertEquals("Perlin(fT:R,fO:4,f:0.05,Freq:0.12)", TextureSpec.noise("@@ridged(Freq:0.12)").toString());
        assertEquals("perlin", TextureSpec.noise("@@perlin").toString());
        assertEquals("Ce(f:0.2)", TextureSpec.noise("Ce(f:0.2)").toString());
        assertArrayEquals(new double[]{0, 90}, TextureSpec.interval("0,90"));
        assertArrayEquals(new double[]{10, 120}, TextureSpec.interval("10..120"));
        assertThrows(IllegalArgumentException.class, () -> TextureSpec.interval("90,10"));
        assertThrows(IllegalArgumentException.class, () -> TextureSpec.interval("0,200"));
    }

    @Test
    void tokensKeepGroupsAndQuotes() {
        assertEquals(List.of("a", "Add(T1:Ambient(), T2:Sun())", "b c", "-w"),
                TextureCommandParser.Tokens.split("a Add(T1:Ambient(), T2:Sun()) \"b c\" -w"));
    }

    @Test
    void vectorsReadNamesAndNumbers() {
        assertArrayEquals(new double[]{0, -1, 0}, Vectors.direction("down"));
        assertArrayEquals(new double[]{0, 0, -1}, Vectors.direction("norte"));
        double[] diagonal = Vectors.direction("1;1;0");
        assertEquals(Math.sqrt(0.5), diagonal[0], 1.0E-9);
        assertThrows(IllegalArgumentException.class, () -> Vectors.direction("0;0;0"));
        assertThrows(IllegalArgumentException.class, () -> Vectors.direction("sideways"));
    }
}
