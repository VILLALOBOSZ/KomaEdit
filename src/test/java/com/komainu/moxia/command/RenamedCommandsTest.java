package com.komainu.moxia.command;

import com.komainu.moxia.brush.ez.EzBrushType;
import com.komainu.moxia.region.TweakRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las órdenes de antes de unir herramientas siguen valiendo: cada una lleva al mismo modo de la orden nueva, con los
 * mismos argumentos, y avisa con un texto corto de cómo se llama ahora.
 */
class RenamedCommandsTest {
    @Test
    void theOldNamesAreModesOfRetocar() {
        Map<String, RetouchCommands.Mode> old = Map.ofEntries(
                Map.entry("snow", RetouchCommands.Mode.SNOW), Map.entry("nieve", RetouchCommands.Mode.SNOW),
                Map.entry("drain", RetouchCommands.Mode.DRAIN), Map.entry("drenar", RetouchCommands.Mode.DRAIN),
                Map.entry("inundar", RetouchCommands.Mode.FLOOD), Map.entry("gravity", RetouchCommands.Mode.GRAVITY),
                Map.entry("gravedad", RetouchCommands.Mode.GRAVITY), Map.entry("biome", RetouchCommands.Mode.BIOME),
                Map.entry("bioma", RetouchCommands.Mode.BIOME),
                // Los de tweak, con sus nombres de antes (y retocar waterlog sigue siendo anegar, como era).
                Map.entry("deinfest", RetouchCommands.Mode.DEINFEST),
                Map.entry("persist", RetouchCommands.Mode.LEAVES), Map.entry("persistir", RetouchCommands.Mode.LEAVES),
                Map.entry("waterlog", RetouchCommands.Mode.WATERLOG), Map.entry("wax", RetouchCommands.Mode.WAX),
                Map.entry("Encerar", RetouchCommands.Mode.WAX), Map.entry("desinfestar", RetouchCommands.Mode.DEINFEST));
        old.forEach((name, mode) -> assertEquals(mode, RetouchCommands.Mode.find(name), name));
        assertNull(RetouchCommands.Mode.find("volar"));
        for (RetouchCommands.Mode mode : RetouchCommands.Mode.values()) {
            assertEquals(mode, RetouchCommands.Mode.find(mode.id()));
        }
        assertEquals(TweakRules.Tweak.PERSIST, RetouchCommands.Mode.LEAVES.tweak());
        assertNull(RetouchCommands.Mode.SNOW.tweak());
        // El on/off de los tweak va en una sola palabra.
        assertEquals("off", RetouchCommands.state(" off "));
        assertNull(RetouchCommands.state(""));
    }

    @Test
    void theNoticeSaysTheNewNameAndThatTheOldOneStillWorks() {
        assertEquals("/komaedit snow ahora se llama /komaedit retocar nieve; la de antes sigue valiendo",
                Renamed.message("/komaedit snow", "/komaedit retocar nieve"));
    }

    @Test
    void naturalIsTheOldSurfacePaintAndTheRestIsTheBrushOnTheSelection() {
        // pintar <paleta> natural es terreno pintar <paleta>: se le quita la palabra del modo, nada más.
        assertEquals("pradera -m grass_block", SelectionBrushCommands.withoutNatural("pradera natural -m grass_block"));
        assertEquals("natural", SelectionBrushCommands.withoutNatural("natural natural"),
                "la paleta de serie se llama igual que el modo");
        assertEquals("", SelectionBrushCommands.withoutNatural("superficie"));
        assertNull(SelectionBrushCommands.withoutNatural("hierba_piedra pendiente 60"));
        assertNull(SelectionBrushCommands.withoutNatural("cumbres -m natural"), "una máscara no es el modo");
    }

    @Test
    void retocarWithTheBrushIsTheZoneBrushWithRetocar() {
        assertEquals(new BrushCommands.Resolved(EzBrushType.ZONE, "8 retocar drenar water"),
                BrushCommands.resolve("retocar", "8 drenar water"));
        assertEquals(new BrushCommands.Resolved(EzBrushType.ZONE, "8 retocar nieve 2"),
                BrushCommands.resolve("retocar", "nieve 2"), "sin radio, el de serie");
        assertEquals(new BrushCommands.Resolved(EzBrushType.PAINT, "cumbres 8"),
                BrushCommands.resolve("pintar", "cumbres 8"));
        assertEquals(new BrushCommands.Resolved(EzBrushType.SCULPT, "soldar 8"),
                BrushCommands.resolve("soldar", "8"), "el atajo del modelado de siempre");
        assertEquals(EzBrushType.ZONE, EzBrushType.byId("zona"));
    }

    @Test
    void theBiomeModeOfRetocarHasNoUniversalPreview() {
        assertFalse(PreviewScope.eligible(List.of("retocar", "bioma", "plains -v")));
        assertTrue(PreviewScope.eligible(List.of("retocar", "nieve", "2")));
        assertTrue(PreviewScope.eligible(List.of("pintar", "cumbres altura")));
        assertTrue(PreviewScope.eligible(List.of("relieve", "montana")));
        assertFalse(PreviewScope.eligible(List.of("pincel", "zona", "6 retocar nieve")));
    }
}
