package com.komainu.moxia.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewSettingsTest {
    @Test
    void preferenceRoundTripsAndRejectsNonsense() {
        assertEquals(50_000, PreviewPreferences.decode(PreviewPreferences.encode(50_000)));
        assertEquals(0, PreviewPreferences.decode(PreviewPreferences.encode(0)));
        assertEquals(0, PreviewPreferences.decode("{\"format\":1}"), "sin valor: apagada");
        assertThrows(IllegalArgumentException.class, () -> PreviewPreferences.decode("{\"format\":2}"));
        assertThrows(IllegalArgumentException.class, () -> PreviewPreferences.encode(-1));
        assertThrows(IllegalArgumentException.class,
                () -> PreviewPreferences.encode(PreviewPreferences.MAX_THRESHOLD + 1));
    }

    @Test
    void policyPreviewsOnFlagOrAboveThePlayersThreshold() {
        EditRequest flagged = EditRequest.command("komaedit smooth", true);
        EditRequest plain = EditRequest.command("komaedit smooth", false);
        assertTrue(PreviewPolicy.shouldPreview(flagged, 1, 0), "-v siempre");
        assertFalse(PreviewPolicy.shouldPreview(plain, 1_000_000, 0), "sin preferencia, nunca");
        assertTrue(PreviewPolicy.shouldPreview(plain, 50_000, 50_000));
        assertFalse(PreviewPolicy.shouldPreview(plain, 49_999, 50_000));
        assertTrue(PreviewPolicy.shouldPreview(EditRequest.DEFAULT, 60_000, 50_000), "también sin orden conocida");
        assertFalse(PreviewPolicy.shouldPreview(EditRequest.TOOL, 1_000_000, 1), "los pinceles, nunca");
        assertFalse(PreviewPolicy.shouldPreview(EditRequest.CONFIRMED, 1_000_000, 1), "lo confirmado no repite");
        assertTrue(EditRequest.command("komaedit x", false).asPreview().preview());
    }
}
