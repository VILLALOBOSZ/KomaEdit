package com.komainu.moxia.shape.tube;

import com.komainu.moxia.FullTests;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TubeFormulaTest {
    @Test
    void operatorsFollowUsualPrecedence() {
        assertEquals(7.0, value("1 + 2 * 3"));
        assertEquals(9.0, value("(1 + 2) * 3"));
        assertEquals(-4.0, value("-2^2"), "el menos agrupa menos que la potencia");
        assertEquals(512.0, value("2^3^2"), "la potencia asocia por la derecha");
        assertEquals(0.5, value("2^-1"));
        assertEquals(2.5, value("10 / 4"));
        assertEquals(1.0, value("7 % 3"));
        assertEquals(-1.0, value("-7 % 3"), "% conserva el signo del dividendo, como en WorldEdit");
        assertEquals(2.0, value("mod(-7, 3)"), "mod() siempre es positivo con divisor positivo");
        assertEquals(0.001, value("1e-3"));
        assertEquals(0.5, value(".5"));
    }

    @Test
    void comparisonsLogicAndTernaryReturnOneOrZero() {
        assertEquals(1.0, value("2 > 1"));
        assertEquals(0.0, value("2 < 1"));
        assertEquals(1.0, value("2 >= 2 && 3 <= 3"));
        assertEquals(1.0, value("1 == 1 && 1 != 2"));
        assertEquals(1.0, value("0 || 0.2"));
        assertEquals(0.0, value("!3"));
        assertEquals(1.0, value("!0"));
        assertEquals(1.0, value("1 + 1 > 1 ? 1 : 0"), "la comparación agrupa menos que la suma");
        assertEquals(3.0, value("0 ? 1 : 0 ? 2 : 3"), "el ternario asocia por la derecha");
    }

    @Test
    void functionsAndConstants() {
        assertEquals(1.0, value("min(3, 1, 2)"));
        assertEquals(3.0, value("max(3, 1, 2)"));
        assertEquals(5.0, value("hypot(3, 4)"));
        assertEquals(0.75, value("frac(-0.25)"));
        assertEquals(2.0, value("clamp(5, 0, 2)"));
        assertEquals(Math.PI / 4, value("atan2(1, 1)"), 1.0E-12);
        assertEquals(0.0, value("sin(pi)"), 1.0E-12);
        assertEquals(1.0, value("ln(e)"), 1.0E-12);
        assertEquals(8.0, value("pow(2, 3)"));
        assertEquals(3.0, value("floor(3.7) + ceil(-0.5) + round(0.4) + sign(-2) + abs(-1) + sqrt(0)"));
    }

    @Test
    void tubeVariablesComeFromTheEvaluator() {
        TubeFormula.Runner runner = TubeFormula.compile("x + 10 * y + 100 * z + 1000 * t").runner(1L, 6);
        assertEquals(4321.0, runner.evaluate(1, 2, 3, 4), 1.0E-9);
        TubeFormula.Runner polar = TubeFormula.compile("r * 100 + a").runner(1L, 6);
        assertEquals(100.0 + Math.atan2(0.8, 0.6), polar.evaluate(0.6, 0.8, 0, 0), 1.0E-9);
        assertEquals(8.0, TubeFormula.compile("n").runner(1L, 8).evaluate(0, 0, 0, 0));
    }

    @Test
    void definitionsAreEvaluatedInOrderAndMayShadowHelpers() {
        TubeFormula.Runner runner = TubeFormula.compile("R = 0.2; w = x * 2; w + R").runner(1L, 6);
        assertEquals(2.2, runner.evaluate(1, 0, 0, 0), 1.0E-12);
        assertEquals(0.2, runner.evaluate(0, 0, 0, 0), 1.0E-12);
        assertEquals(2.0, value("k = 1; k = k + 1; k"));
        // Igual que en ezEdits, r puede ser una constante propia en vez del radio.
        assertEquals(0.1, TubeFormula.compile("r = 0.1; r").runner(1L, 6).evaluate(0.6, 0.8, 0, 0), 1.0E-12);
        assertEquals(1.0, value("1;"), "un punto y coma final se admite");
        assertEquals(0.3, value("R = 0.2; r = 0.1; R + r"), 1.0E-12, "los nombres distinguen mayúsculas");
    }

    @Test
    void ezEditsStyleFormulaCompilesAndEvaluates() {
        // El ejemplo de la wiki de ezEdits: R y r son variables distintas.
        TubeFormula formula = TubeFormula.compile(
                "R=0.2;r=0.1;w=0.7;s=0.5;sqrt((abs(x)-w)^2+y^2)<R||sqrt(((z+1)%s-r)^2+y^2)<r&&abs(x)<w");
        TubeFormula.Runner runner = formula.runner(1L, 6);
        assertEquals(1.0, runner.evaluate(0.7, 0.0, 0.0, 0.0), "dentro de la barra lateral");
        assertEquals(0.0, runner.evaluate(0.0, 0.9, 0.0, 0.0), "fuera de todo");
    }

    @Test
    void noiseIsDeterministicPerSeed() {
        TubeFormula formula = TubeFormula.compile("noise(x * 3, y * 3, z)");
        double first = formula.runner(42L, 6).evaluate(0.3, -0.2, 1.7, 0);
        double second = formula.runner(42L, 6).evaluate(0.3, -0.2, 1.7, 0);
        assertEquals(first, second);
        assertTrue(first >= 0.0 && first <= 1.0);
    }

    @Test
    void invalidFormulasAreRejectedWithSpanishMessages() {
        assertMessage("", "vacía");
        assertMessage("x".repeat(TubeFormula.MAX_LENGTH + 1), "demasiado larga");
        assertMessage("stone", "Variable desconocida «stone»");
        assertMessage("foo(1)", "Función desconocida «foo»");
        assertMessage("sin(1, 2)", "«sin» espera 1 argumento");
        assertMessage("(x + 1", "Falta «)»");
        assertMessage("x = 1", "No se puede redefinir «x»");
        assertMessage("k = 1", "Falta «;»");
        assertMessage("x = 1; x", "No se puede redefinir");
        assertMessage("r < 1 = 2", "Usa «==»");
        assertMessage("while (1) 1", "no admiten bucles");
        assertMessage("# x", "Carácter inesperado «#»");
        assertMessage("x # y", "Sobra «# y»");
        assertMessage("x + ", "termina antes de tiempo");
        assertMessage("x; y", "Solo se puede definir");
        assertMessage("sin", "es una función");
        assertMessage("X > 0", "las mayúsculas cuentan, escribe «x»");
        assertMessage("SIN(x)", "escribe «sin»");
        assertMessage("WHILE (1) 1", "no admiten bucles");
        assertMessage("(".repeat(45) + "1" + ")".repeat(45), "demasiado anidada");
        assertMessage("-".repeat(60) + "1", "demasiado anidada");
    }

    @Test
    void errorsPointAtTheFailingPosition() {
        TubeFormula.FormulaException error = assertThrows(TubeFormula.FormulaException.class,
                () -> TubeFormula.compile("r < 1 && foo"));
        assertEquals(9, error.position());
        assertTrue(error.getMessage().startsWith("Fórmula, posición 10:"), error.getMessage());
    }

    @Test
    void radialBoundIsConservative() {
        double full = Math.sqrt(2.0);
        assertEquals(1.0, bound("r <= 1 && x > 0"));
        assertEquals(0.5, bound("(r < 0.5) * 3"));
        assertEquals(0.8, bound("0.8 - r"));
        assertEquals(0.6, bound("R = 0.6; R > r ? 1 : 0"));
        assertEquals(0.6, bound("radio = 0.6; r < radio"));
        assertEquals(0.7, bound("k = r < 0.7; k ? 1 : 0"));
        assertEquals(0.9, bound("r <= (x > 0 ? 0.9 : 0.4)"));
        assertEquals(0.0, bound("0"));
        assertEquals(full, bound("1"));
        assertEquals(full, bound("r > 0.5"));
        assertEquals(full, bound("r <= 1 || x > 0"));
        assertEquals(full, bound("r <= 3"), "nunca más allá de la esquina del cuadrado");
        assertEquals(full, bound("r = 0.1; r < 2"), "r redefinida ya no es el radio");
        for (TubeFormulaPreset preset : TubeFormulaPreset.values()) {
            double expected = preset == TubeFormulaPreset.RAILS ? full : 1.0;
            assertEquals(expected, preset.formula().radialBound(), preset.id());
        }
    }

    @Test
    void everyPresetCompiles() {
        for (TubeFormulaPreset preset : TubeFormulaPreset.values()) {
            TubeFormula formula = preset.formula();
            assertTrue(preset.source().length() <= TubeFormula.MAX_LENGTH, preset.id());
            double inside = 0;
            TubeFormula.Runner runner = formula.runner(1L, 6);
            for (int step = 0; step < 400; step++) {
                double value = runner.evaluate(Math.cos(step) * 0.9, Math.sin(step * 1.3) * 0.9, step * 0.05, 0.5);
                inside += value > 0 ? 1 : 0;
                assertTrue(Double.isNaN(value) || value <= 1.0, preset.id() + " debe dar material en (0, 1]");
            }
            assertTrue(inside > 0, preset.id() + " no coloca nada");
            assertEquals(preset, TubeFormulaPreset.byId(preset.displayName()));
        }
    }

    @Test
    void evaluationIsCheapEnoughForMillionsOfSamples() {
        TubeFormula.Runner runner = TubeFormulaPreset.LATTICE.formula().runner(1L, 6);
        // Medir solo vale en la pasada entera; en cada build basta con que evalúe.
        int samples = FullTests.ENABLED ? 2_000_000 : 20_000;
        double sink = 0;
        // Calentamiento para que el JIT compile el árbol antes de medir.
        for (int index = 0; index < samples; index++) {
            sink += runner.evaluate((index % 200) / 100.0 - 1.0, ((index / 200) % 200) / 100.0 - 1.0, index * 1.0E-4, 0);
        }
        long start = System.nanoTime();
        for (int index = 0; index < samples; index++) {
            sink += runner.evaluate((index % 200) / 100.0 - 1.0, ((index / 200) % 200) / 100.0 - 1.0, index * 1.0E-4, 0);
        }
        double nanos = (System.nanoTime() - start) / (double) samples;
        assertTrue(sink >= 0);
        // En un portátil ronda los 60 ns; el margen cubre máquinas de CI lentas.
        FullTests.assertFasterThan(nanos, 1_000, "evaluar la celosía cuesta " + nanos + " ns por punto");
    }

    private static double bound(String source) {
        return TubeFormula.compile(source).radialBound();
    }

    private static double value(String source) {
        return TubeFormula.compile(source).runner(1L, 6).evaluate(0, 0, 0, 0);
    }

    private static void assertMessage(String source, String fragment) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> TubeFormula.compile(source));
        assertTrue(error.getMessage().contains(fragment), "«" + source + "» → " + error.getMessage());
    }
}
