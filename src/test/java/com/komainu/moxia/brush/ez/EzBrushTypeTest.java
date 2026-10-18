package com.komainu.moxia.brush.ez;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EzBrushTypeTest {
    @Test
    void placementBrushesHaveTheirEzEditsNamesAndAbbreviations() {
        assertEquals(EzBrushType.SCATTER, EzBrushType.byId("scatter"));
        assertEquals(EzBrushType.SCATTER, EzBrushType.byId("sc"));
        assertEquals(EzBrushType.ARRAY, EzBrushType.byId("ar"));
        assertEquals(EzBrushType.ARRAY, EzBrushType.byId("hilera"));
        assertEquals(EzBrushType.PLACE, EzBrushType.byId("pl"));
        assertTrue(EzBrushType.SCATTER.placement());
        assertFalse(EzBrushType.GRADIENT.placement());
        assertThrows(IllegalArgumentException.class, () -> EzBrushType.byId("symmetry"));
    }

    @Test
    void onlyAnExplicitPreviewFlagKeepsTheHologramPlacement() {
        assertTrue(EzBrush.legacyPreview("Cl C C -p"));
        assertTrue(EzBrush.legacyPreview("Cl -P"));
        assertFalse(EzBrush.legacyPreview("Cl C C -s 5"));
        assertFalse(EzBrush.legacyPreview("Sh(S:Cone,P:stone) -o 0.5,2"));
    }
}
