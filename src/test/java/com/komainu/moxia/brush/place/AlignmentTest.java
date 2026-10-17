package com.komainu.moxia.brush.place;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlignmentTest {
    private static final double[] UP = {0, 1, 0};
    private static final double[] EAST = {1, 0, 0};
    private static final Alignment.Context CONTEXT = new Alignment.Context(0.2, -0.9, 0.3, 0, 0, -1, 1, 5, 2, 7);

    @Test
    void defaultIsIdentity() {
        int[] basis = Alignment.basis(UP, EAST, false);
        assertArrayEquals(new int[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, basis);
    }

    @Test
    void primarySouthKeepsEastAndSendsSouthDown() {
        // El ejemplo de la wiki: arriba al sur y delante al este; el antiguo sur (azul) apunta abajo.
        int[] basis = Alignment.basis(new double[]{0, 0, 1}, EAST, false);
        assertEquals(1, Alignment.applyZ(basis, 0, 1, 0), "el arriba de la estructura mira al sur");
        assertEquals(1, Alignment.applyX(basis, 1, 0, 0), "el delante sigue al este");
        assertEquals(-1, Alignment.applyY(basis, 0, 0, 1), "el sur local acaba hacia abajo");
    }

    @Test
    void secondaryIsMadePerpendicular() {
        // Primaria (-1,2,-1) se ajusta a arriba; la secundaria oeste ya es perpendicular.
        int[] basis = Alignment.basis(new double[]{-1, 2, -1}, new double[]{-1, 0, 0}, false);
        assertEquals(-1, Alignment.applyX(basis, 1, 0, 0));
        assertEquals(1, Alignment.applyY(basis, 0, 1, 0));
        int[] tilted = Alignment.basis(UP, new double[]{0.2, 0.9, -0.6}, false);
        assertEquals(-1, Alignment.applyZ(tilted, 1, 0, 0), "lo que sobra hacia arriba se descarta: queda el norte");
    }

    @Test
    void collinearNeedsPerturb() {
        assertThrows(IllegalArgumentException.class, () -> Alignment.basis(UP, new double[]{0, 3, 0}, false));
        int[] basis = Alignment.basis(UP, new double[]{0, 3, 0}, true);
        assertEquals(1, Alignment.applyY(basis, 0, 1, 0));
    }

    @Test
    void basisStaysARotation() {
        double[][] directions = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (double[] primary : directions) {
            for (double[] secondary : directions) {
                if (Math.abs(primary[0] * secondary[0] + primary[1] * secondary[1] + primary[2] * secondary[2]) > 0.5) {
                    continue;
                }
                int[] m = Alignment.basis(primary, secondary, false);
                int determinant = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6])
                        + m[2] * (m[3] * m[7] - m[4] * m[6]);
                assertEquals(1, determinant, "sin espejos");
            }
        }
    }

    @Test
    void quarterTurnsAroundUp() {
        int[] once = Alignment.rotateAroundUp(Alignment.basis(UP, EAST, false), 1);
        assertEquals(1, Alignment.applyZ(once, 1, 0, 0), "90°: el este pasa al sur (horario visto desde arriba)");
        int[] full = Alignment.rotateAroundUp(Alignment.basis(UP, EAST, false), 4);
        assertArrayEquals(new int[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, full);
        assertEquals(1, ClipboardStructure.rotation("90"));
        assertEquals(3, ClipboardStructure.rotation("-90"));
        assertThrows(IllegalArgumentException.class, () -> ClipboardStructure.rotation("45"));
    }

    @Test
    void parsesModesAndDirections() {
        assertEquals(Alignment.Mode.constant(0, 1, 0), Alignment.parse(null, true, null));
        assertEquals(Alignment.Mode.constant(1, 0, 0), Alignment.parse("C", false, null));
        assertEquals(Alignment.Mode.constant(-1, 2, -1), Alignment.parse("Constant(Direction:(-1,2,-1))", true, null));
        assertEquals(Alignment.Mode.constant(0, 0, -1), Alignment.parse("C(D:north)", true, null));
        assertEquals(-0.5, Alignment.parse("C(D:-aim)", true, new double[]{0.5, 0, 0}).x(), 1.0E-9);
        assertEquals(Alignment.Kind.SURFACE_NORMAL, Alignment.parse("SurfaceNormal", true, null).kind());
        assertEquals(Alignment.Kind.PLAYER_RELATIVE, Alignment.parse("P", true, null).kind());
        assertThrows(IllegalArgumentException.class, () -> Alignment.parse("ViewDiff", true, null));
        assertThrows(IllegalArgumentException.class, () -> Alignment.direction("(0,0,0)", null));
    }

    @Test
    void resolvesEachKind() {
        assertArrayEquals(new double[]{0, 0, -1}, Alignment.resolve(Alignment.Mode.of(Alignment.Kind.SURFACE_NORMAL),
                CONTEXT, 1), 1.0E-9);
        assertArrayEquals(new double[]{1, 5, 2}, Alignment.resolve(Alignment.Mode.of(Alignment.Kind.PLAYER_RELATIVE),
                CONTEXT, 1), 1.0E-9);
        double[] upward = Alignment.resolve(Alignment.Mode.of(Alignment.Kind.UPWARD), CONTEXT, 1);
        double dot = upward[0] * 0.2 + upward[1] * -0.9 + upward[2] * 0.3;
        assertEquals(0.0, dot, 1.0E-9, "Upward es perpendicular a la mirada");
        double[] random = Alignment.resolve(Alignment.Mode.of(Alignment.Kind.RANDOM), CONTEXT, 1);
        assertArrayEquals(random, Alignment.resolve(Alignment.Mode.of(Alignment.Kind.RANDOM), CONTEXT, 1),
                "la misma semilla da la misma dirección");
    }

    @Test
    void structureSpecSplitsNestedOptions() {
        StructureSpec spec = StructureSpec.parse("Cl(O:C, R:90, X:\"a,b\", D:(1,2,3))");
        assertEquals("Cl", spec.name());
        assertEquals("C", spec.option("origin", "o"));
        assertEquals("90", spec.option("R"));
        assertEquals("a,b", spec.option("x"));
        assertEquals("(1,2,3)", spec.option("d"));
        assertNull(spec.option("pastemethod", "pm"));
        assertEquals("Clipboard", StructureSpec.parse("Clipboard").name());
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Cl(O:C"));
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Cl(nada)"));
        assertThrows(IllegalArgumentException.class, () -> StructureSources.parse("Schematic(N:arbol)"),
                "sin el motor de colocación, solo el portapapeles");
        assertEquals(true, StructureSources.parse("Cl(O:C,R:180)") instanceof ClipboardStructure clipboard
                && clipboard.center() && clipboard.quarterTurns() == 2);
    }
}
