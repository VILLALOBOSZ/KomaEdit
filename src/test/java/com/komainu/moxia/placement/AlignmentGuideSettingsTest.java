package com.komainu.moxia.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@code visualisations.json}: lo que falta toma el valor de serie y lo que no vale se rechaza. */
class AlignmentGuideSettingsTest {
    @Test
    void missingFieldsTakeTheDefaults() {
        assertEquals(AlignmentGuideSettings.DEFAULT, AlignmentGuideSettings.parse("{}"));
        assertEquals(AlignmentGuideSettings.DEFAULT, AlignmentGuideSettings.parse("{\"format\":1,\"alignment\":{}}"));
        AlignmentGuideSettings off = AlignmentGuideSettings.parse("{\"alignment\":{\"enabled\":false}}");
        assertFalse(off.enabled());
        assertEquals(4, off.updateTicks());
    }

    @Test
    void roundTripsAndRejectsSillyRates() {
        AlignmentGuideSettings custom = new AlignmentGuideSettings(true, 10, false);
        assertEquals(custom, AlignmentGuideSettings.parse(custom.toJson()));
        assertEquals(AlignmentGuideSettings.DEFAULT, AlignmentGuideSettings.parse(AlignmentGuideSettings.DEFAULT
                .toJson()));
        assertThrows(IllegalArgumentException.class,
                () -> AlignmentGuideSettings.parse("{\"alignment\":{\"updateTicks\":0}}"));
        assertThrows(IllegalArgumentException.class, () -> new AlignmentGuideSettings(true, 41, true));
    }
}
