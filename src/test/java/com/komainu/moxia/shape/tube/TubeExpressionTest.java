package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeExpression;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TubeExpressionTest {
    private static final int LIMIT = 1_500_000;

    @Test
    void circleFormulaMatchesTheCircleProfile() {
        SampledPath path = diagonal(40);
        Set<BlockVector3> formula = TubeFigures.generate(path, expression(7, "r <= 1", Map.of()), LIMIT);
        Set<BlockVector3> profile = TubeFigures.generate(path,
                new ShapeParameters(PathShape.SWEEP, 7, 6, 1, 0, ShapeOptions.EMPTY), LIMIT);
        assertEquals(profile, formula, "r <= 1 es el mismo tubo que el perfil círculo");
    }

    @Test
    void formulaStaysInsideTheSectionSquare() {
        SampledPath path = straight(30);
        // Una fórmula siempre verdadera llena el cuadrado [-1, 1] de la sección, no más.
        Set<BlockVector3> blocks = TubeFigures.generate(path, expression(6, "1", Map.of("quality", 0.0)), LIMIT);
        // El eje pasa por el centro de los bloques, así que el lado son 13 bloques (de -6 a 6).
        for (BlockVector3 block : blocks) {
            assertTrue(Math.abs(block.y()) <= 6 && Math.abs(block.z()) <= 6,
                    "fuera del cuadrado de la sección: " + block);
        }
        assertEquals(13 * 13 * 30, blocks.size(), 13 * 13 * 2, "prisma cuadrado de lado 2 radios");
    }

    @Test
    void presetsAreDeterministicAndDifferent() {
        SampledPath path = diagonal(48);
        Set<BlockVector3> previous = null;
        for (TubeFormulaPreset preset : TubeFormulaPreset.values()) {
            ShapeParameters parameters = expression(7, "", Map.of("preset", (double) preset.ordinal()));
            Set<BlockVector3> first = TubeFigures.generate(path, parameters, LIMIT);
            Set<BlockVector3> second = TubeFigures.generate(path, parameters, LIMIT);
            assertTrue(first.size() > 300, preset.id() + " generó muy pocos bloques: " + first.size());
            assertEquals(first, second, preset.id() + " debe ser estable entre holograma y Build");
            if (previous != null) {
                assertNotEquals(previous, first, preset.id());
            }
            previous = first;
        }
    }

    @Test
    void windowsCutHolesInTheWall() {
        SampledPath path = straight(48);
        Set<BlockVector3> windows = TubeFigures.generate(path,
                expression(8, "", Map.of("preset", (double) TubeFormulaPreset.WINDOWS.ordinal())), LIMIT);
        Set<BlockVector3> wall = TubeFigures.generate(path, expression(8, "r > 0.72 && r <= 1 ? r : 0", Map.of()),
                LIMIT);
        assertTrue(wall.containsAll(windows), "las ventanas solo quitan bloques de la pared");
        assertTrue(windows.size() < wall.size() * 0.9, windows.size() + " de " + wall.size());
    }

    @Test
    void repetitionsComeFromSidesOrSpacing() {
        SampledPath path = straight(40);
        Set<BlockVector3> four = TubeFigures.generate(path, expression(8, "abs(sin(a * n / 2)) > 0.5", Map.of(
                "sides", 4.0)), LIMIT);
        Set<BlockVector3> eight = TubeFigures.generate(path, expression(8, "abs(sin(a * n / 2)) > 0.5", Map.of(
                "sides", 8.0)), LIMIT);
        assertNotEquals(four, eight, "n debe cambiar la figura");
    }

    @Test
    void progressVariableReachesTheWholePath() {
        SampledPath path = straight(40);
        // Solo la primera mitad del recorrido: t < 0.5.
        Set<BlockVector3> blocks = TubeFigures.generate(path, expression(5, "r <= 1 && t < 0.5", Map.of()), LIMIT);
        int maximum = blocks.stream().mapToInt(BlockVector3::x).max().orElseThrow();
        assertEquals(20, maximum, 1);
    }

    @Test
    void materialProbeReturnsTheFormulaValue() {
        SampledPath path = straight(30);
        ShapeParameters parameters = expression(8, "r <= 1 ? (r < 0.5 ? 0.25 : 0.75) : 0", Map.of());
        TubeFigures.MaterialProbe probe = TubeFigures.materialProbe(path, parameters, TubeCurve.NONE);
        assertEquals(0.25, probe.value(BlockVector3.at(15, 0, 0), -1), 1.0E-9, "núcleo");
        assertEquals(0.75, probe.value(BlockVector3.at(15, 6, 0), -1), 1.0E-9, "corteza");
    }

    @Test
    void invalidFormulasNeverReachTheVoxelizer() {
        SampledPath path = straight(10);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TubeFigures.generate(path, expression(5, "r < 1 &&", Map.of()), LIMIT));
        assertTrue(error.getMessage().contains("Fórmula"), error.getMessage());
        // NaN o infinito no rompen nada: NaN queda fuera y lo infinito cuenta como material 1.
        assertTrue(TubeFigures.generate(path, expression(5, "sqrt(-1)", Map.of()), LIMIT).isEmpty());
        assertFalse(TubeFigures.generate(path, expression(5, "r <= 1 ? 1 / 0 : 0", Map.of()), LIMIT).isEmpty());
    }

    @Test
    void commandParsesFormulaPatternAndFlags() {
        TubeCommandParser.Result plain = TubeCommandParser.parseExpression("8", "r < 1 && abs(x) > 0.2", "dirt");
        assertEquals("dirt", plain.pattern());
        assertEquals("r < 1 && abs(x) > 0.2", plain.parameters().options().formula());
        assertEquals(PathShape.EXPRESSION, plain.parameters().shape());
        assertEquals(8, plain.parameters().radius());

        TubeCommandParser.Result withPattern = TubeCommandParser.parseExpression("8",
                "-e round -t 90 60%stone,40%andesite r<1&&sin(z)>0", "dirt");
        assertEquals("60%stone,40%andesite", withPattern.pattern());
        assertEquals("r<1&&sin(z)>0", withPattern.parameters().options().formula());
        assertEquals(TubeSettings.EndMode.ROUND.ordinal(), withPattern.parameters().options().get("end", -1), 0.0);
        assertEquals(90.0, withPattern.parameters().options().get("twist", 0), 0.0);

        TubeCommandParser.Result preset = TubeCommandParser.parseExpression("2,10,2", "stone ventanas8", "dirt");
        assertEquals("stone", preset.pattern());
        assertEquals("", preset.parameters().options().formula());
        assertEquals(TubeFormulaPreset.WINDOWS.ordinal(), preset.parameters().options().get("preset", -1), 0.0);
        assertEquals(8.0, preset.parameters().options().get("sides", 0), 0.0);
        assertEquals(10, preset.parameters().radius());

        TubeCommandParser.Result alias = TubeCommandParser.parseExpression("6", "celosía", "dirt");
        assertEquals(TubeFormulaPreset.LATTICE.ordinal(), alias.parameters().options().get("preset", -1), 0.0);
    }

    @Test
    void commandRejectsBadInputWithClearMessages() {
        assertCommandError("", "Falta la fórmula");
        assertCommandError("-e round", "Falta la fórmula");
        assertCommandError("stone r < 1 &&", "termina antes de tiempo");
        assertCommandError("r < 1 && foo", "Variable desconocida «foo»");
        assertCommandError("r < 1 -e round", "las banderas");
        assertCommandError("ventanas99", "entre 2 y 32");
        assertCommandError("-e", "Falta el valor de -e");
        assertCommandError("-z 3 r<1", "Bandera desconocida");
    }

    @Test
    void shapeExpressionAcceptsPresetNames() {
        ShapeExpression expression = ShapeExpression.parse("expression(preset:lattice,end:round)");
        assertEquals(PathShape.EXPRESSION, expression.shape());
        assertEquals(TubeFormulaPreset.LATTICE.ordinal(), expression.options().get("preset", -1), 0.0);
    }

    @Test
    void expressionCostStaysReasonable() {
        SampledPath path = diagonal(240);
        ShapeParameters parameters = expression(10, "", Map.of("preset", (double) TubeFormulaPreset.LATTICE.ordinal()));
        TubeFigures.generate(path, parameters, LIMIT);
        long start = System.nanoTime();
        Set<BlockVector3> blocks = TubeFigures.generate(path, parameters, LIMIT);
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(blocks.size() > 10_000, "celosía con muy pocos bloques: " + blocks.size());
        assertTrue(elapsed < 8_000, "demasiado lento: " + elapsed + " ms para " + blocks.size() + " bloques");
    }

    private static void assertCommandError(String input, String fragment) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TubeCommandParser.parseExpression("6", input, "stone"));
        assertTrue(error.getMessage().contains(fragment), "«" + input + "» → " + error.getMessage());
    }

    private static ShapeParameters expression(int radius, String formula, Map<String, Double> options) {
        return new ShapeParameters(PathShape.EXPRESSION, radius, 6, 1, 0,
                new ShapeOptions(new HashMap<>(options), formula));
    }

    private static SampledPath straight(int length) {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(length, 0, 0)), false);
    }

    private static SampledPath diagonal(int length) {
        return SampledPath.createSmooth(List.of(
                BlockVector3.at(0, 0, 0),
                BlockVector3.at(length / 2, length / 4, length / 2),
                BlockVector3.at(length, length / 2, length)
        ), false);
    }
}
