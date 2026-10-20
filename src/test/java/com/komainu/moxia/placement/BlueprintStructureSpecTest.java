package com.komainu.moxia.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** La estructura {@code Blueprint(…)} ({@code Bp}) de place, scatter y array. */
class BlueprintStructureSpecTest {
    @Test
    void namesAndTagsBecomeOneListAndTheBaseIsTheDefaultOrigin() {
        StructureSpec spec = StructureSpec.parse("Bp(N:roca_1|roca_2:3,T:rocas|#musgo)");
        assertEquals(StructureSpec.Type.BLUEPRINT, spec.type());
        assertEquals("roca_1|roca_2:3|#rocas|#musgo", spec.blueprints());
        assertEquals(StructureSpec.Origin.BOTTOM, spec.origin());
        assertFalse(spec.procedural());
        assertEquals(StructureSpec.Origin.CENTER, StructureSpec.parse("Blueprint(T:rocas,O:C)").origin());
        assertEquals(StructureSpec.Origin.INHERENT, StructureSpec.parse("plano(N:servidor/casa,O:I,R:90)").origin());
        assertEquals(90.0, StructureSpec.parse("Bp(N:casa,R:90)").rotation());
    }

    @Test
    void theOtherStructuresKeepTheirOrigin() {
        assertEquals(StructureSpec.Origin.INHERENT, StructureSpec.parse("Clipboard").origin());
        assertEquals(StructureSpec.Origin.CENTER, StructureSpec.parse("Cl(O:C)").origin());
        assertEquals(StructureSpec.Origin.INHERENT, StructureSpec.parse("Sc(N:arbol_.*)").origin());
    }

    @Test
    void mistakesAreReportedWhenParsing() {
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Bp"));
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Bp(O:C)"));
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Bp(N:../fuera)"));
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Bp(T:con/barra)"));
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Bp(N:casa,O:arriba)"));
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Bp(N:casa,P:stone)"));
    }

    @Test
    void placementSettingsAcceptBlueprintsLikeAnyStructure() {
        PlacementSettings settings = PlacementSettings.parse(PlacementSettings.Kind.SCATTER,
                "Bp(T:rocas) SurfaceNormal Random -n 1 -r Y -f XZ");
        assertEquals("Bp(T:rocas)", settings.structure());
    }
}
