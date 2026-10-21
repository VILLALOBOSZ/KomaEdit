package com.komainu.moxia.studio.live;

import com.komainu.moxia.recipe.ProceduralRecipe;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que admite la red de la vista previa en vivo y cómo viaja. */
class LiveRequestsTest {
    @Test
    void onlyKomaEditOrdersWithTheirPreview() {
        assertTrue(LiveRequests.problem(LivePreviewRequestPayload.command(1, 1, false, "komaedit tree oak 20 -v"))
                .isEmpty());
        assertTrue(LiveRequests.problem(LivePreviewRequestPayload.command(1, 1, false,
                "komaedit text \"Hola\" stone --preview")).isEmpty());
        assertTrue(LiveRequests.problem(LivePreviewRequestPayload.command(1, 1, false, "komaedit tree oak 20"))
                .isPresent(), "sin -v colocaría: eso va por el chat");
        assertTrue(LiveRequests.problem(LivePreviewRequestPayload.command(1, 1, false, "op Visedzzz -v"))
                .isPresent(), "nada que no sea de KomaEdit");
        assertTrue(LiveRequests.problem(LivePreviewRequestPayload.figure(1, 1, false, ProceduralRecipe.defaults()))
                .isEmpty());
        assertThrows(IllegalArgumentException.class, () -> LivePreviewRequestPayload.command(1, 1, false, " "));
    }

    @Test
    void requestsAndAnswersSurviveTheTrip() {
        LivePreviewRequestPayload command = LivePreviewRequestPayload.command(3, 7, true, "komaedit rock boulder 5 -v");
        assertEquals(command, roundTrip(command));
        LivePreviewRequestPayload figure = LivePreviewRequestPayload.figure(3, 8, false, ProceduralRecipe.defaults());
        LivePreviewRequestPayload back = roundTrip(figure);
        assertEquals(figure.recipe().toString(), back.recipe().toString());
        assertEquals(8, back.seq());

        LivePreviewResultPayload answer = LivePreviewResultPayload.of(figure, LivePreviewResultPayload.Outcome.GHOST,
                42, "");
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        LivePreviewResultPayload.CODEC.encode(buffer, answer);
        LivePreviewResultPayload read = LivePreviewResultPayload.CODEC.decode(buffer);
        assertEquals(answer, read);
        assertTrue(read.refresh(), "la 8 rehacía la del Construir");
        assertEquals(LivePreviewResultPayload.Outcome.GHOST, read.outcomeValue());
    }

    private static LivePreviewRequestPayload roundTrip(LivePreviewRequestPayload payload) {
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        LivePreviewRequestPayload.CODEC.encode(buffer, payload);
        return LivePreviewRequestPayload.CODEC.decode(buffer);
    }
}
