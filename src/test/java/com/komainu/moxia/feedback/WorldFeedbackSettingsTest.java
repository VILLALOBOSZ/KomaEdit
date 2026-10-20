package com.komainu.moxia.feedback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldFeedbackSettingsTest {
    @Test
    void defaultsAreDiscreetAndRespectWorldEditCui() {
        WorldFeedbackSettings plain = WorldFeedbackSettings.defaults(false);
        assertEquals(WorldFeedbackSettings.PreviewStyle.GHOST, plain.previewStyle());
        assertTrue(plain.previewOpacity() < 0.6, "translúcida de serie");
        assertTrue(plain.fadeNear());
        assertTrue(plain.selection());
        assertTrue(plain.soundVolume() < 0.5, "sonidos bajos de serie");
        assertFalse(WorldFeedbackSettings.defaults(true).selection(), "con WorldEditCUI no se dibuja dos veces");
    }

    @Test
    void roundTripKeepsEverything() {
        WorldFeedbackSettings changed = WorldFeedbackSettings.defaults(false)
                .withPreviewStyle(WorldFeedbackSettings.PreviewStyle.OUTLINE)
                .withPreviewOpacity(0.7)
                .withFadeNear(false)
                .withSelection(false)
                .withDimensions(false)
                .withPathNumbers(false)
                .withBrushReticle(false)
                .withSymmetryGuides(false)
                .withSounds(false)
                .withSoundVolume(0.8);
        assertEquals(changed, WorldFeedbackSettings.parse(changed.toJson(), WorldFeedbackSettings.defaults(false)));
    }

    @Test
    void brokenOrPartialFilesFallBackToDefaults() {
        WorldFeedbackSettings defaults = WorldFeedbackSettings.defaults(false);
        assertEquals(defaults, WorldFeedbackSettings.parse("{roto", defaults));
        assertEquals(defaults, WorldFeedbackSettings.parse("[]", defaults));
        assertEquals(defaults, WorldFeedbackSettings.parse(null, defaults));
        WorldFeedbackSettings partial = WorldFeedbackSettings.parse(
                "{\"preview\":{\"style\":\"outline\",\"opacity\":\"mucha\"},\"sounds\":{\"enabled\":false}}",
                defaults);
        assertEquals(WorldFeedbackSettings.PreviewStyle.OUTLINE, partial.previewStyle());
        assertEquals(defaults.previewOpacity(), partial.previewOpacity());
        assertFalse(partial.sounds());
        assertEquals(defaults.selection(), partial.selection());
    }

    @Test
    void valuesAreClamped() {
        WorldFeedbackSettings settings = WorldFeedbackSettings.parse(
                "{\"preview\":{\"opacity\":5},\"sounds\":{\"volume\":-2}}", WorldFeedbackSettings.defaults(false));
        assertEquals(WorldFeedbackSettings.MAX_OPACITY, settings.previewOpacity());
        assertEquals(0.0, settings.soundVolume());
    }

    @Test
    void nearbyPreviewFadesSmoothly() {
        WorldFeedbackSettings settings = WorldFeedbackSettings.defaults(false);
        double previous = settings.opacityAt(0.0);
        assertEquals(settings.previewOpacity() * 0.25, previous, 1.0E-9, "encima: la cuarta parte");
        for (double distance = 0.5; distance <= 12.0; distance += 0.5) {
            double current = settings.opacityAt(distance);
            assertTrue(current >= previous, "no sube al acercarse");
            previous = current;
        }
        assertEquals(settings.previewOpacity(), settings.opacityAt(12.0), 1.0E-9);
        assertEquals(settings.previewOpacity(), settings.withFadeNear(false).opacityAt(0.0), 1.0E-9);
    }
}
