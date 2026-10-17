package com.komainu.moxia.noise;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ruidos de serie y guardados. */
class NoiseLibraryTest {
    @Test
    void everyPresetParsesAndStaysBetweenZeroAndOne() {
        for (Map.Entry<String, String> preset : NoiseSpec.PRESETS.entrySet()) {
            NoiseSpec spec = NoiseSpec.parse(preset.getKey());
            assertEquals(preset.getValue(), spec.toString());
            NoiseSpec.NoiseField field = spec.build(7L);
            for (int index = 0; index < 500; index++) {
                double value = field.sample(index * 1.3, index * 0.7, index * 2.1);
                assertTrue(value >= 0.0 && value <= 1.0, preset.getKey() + ": " + value);
            }
        }
    }

    @Test
    void savedNoisesResolveByNameOrHash() {
        Map<String, String> saved = Map.of("nubes", "Pe(f:0.02,fT:F)");

        assertEquals("Pe(f:0.02,fT:F)", NoiseSpec.parse("nubes", saved::get).toString());
        assertEquals("Pe(f:0.02,fT:F)", NoiseSpec.parse("#Nubes", saved::get).toString());
        assertEquals(NoiseSpec.Type.PERLIN, NoiseSpec.parse("Perlin", saved::get).type());
        assertEquals(NoiseSpec.PRESETS.get("ridged"), NoiseSpec.parse("ridged", saved::get).toString());
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("lluvia", saved::get));
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("#nubes"));
    }

    @Test
    void reservedNamesCoverTypesAndPresets() {
        assertTrue(NoiseSpec.isReservedName("Perlin"));
        assertTrue(NoiseSpec.isReservedName("ce"));
        assertTrue(NoiseSpec.isReservedName("voronoiedge"));
        assertFalse(NoiseSpec.isReservedName("mis_nubes"));
    }

    @Test
    void parametersKeepTheirWrittenOrder() {
        NoiseSpec spec = NoiseSpec.parse("Ce(F:0.1,cR:edge,cD:e)");
        assertEquals(List.of("frequency", "cellreturn", "celldistance"), List.copyOf(spec.parameters().keySet()));
        assertEquals("edge", spec.parameters().get("cellreturn"));
    }
}
