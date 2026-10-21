package com.komainu.moxia.studio.live;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Lo que contesta el servidor a una petición de la vista previa en vivo ({@link LivePreviewRequestPayload}). Cuando
 * sale un fantasma, la respuesta llega justo antes que él: así el cliente sabe sin adivinar que es el del Studio y
 * que sustituye al de antes.
 *
 * @param outcome ordinal de {@link Outcome}
 * @param id      el fantasma ({@link Outcome#GHOST}) o la vista previa ({@link Outcome#PREVIEW}) que salió, o 0
 * @param refresh la petición rehacía la de antes (no era el Construir que abre la sesión)
 * @param message por qué no salió ({@link Outcome#FAILED}), o vacío
 */
public record LivePreviewResultPayload(int session, int seq, int outcome, int id, boolean refresh, String message)
        implements CustomPayload {
    public static final int MAX_MESSAGE = 256;
    public static final Id<LivePreviewResultPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID,
            "live_preview_result"));
    public static final PacketCodec<RegistryByteBuf, LivePreviewResultPayload> CODEC = PacketCodec.ofStatic(
            LivePreviewResultPayload::write, LivePreviewResultPayload::read);

    public enum Outcome {
        /** Salió un fantasma con gizmo ({@code id}). */
        GHOST,
        /** Salió una vista previa que espera a Confirmar ({@code id}: la del plan que dibuja el cliente, o 0). */
        PREVIEW,
        /** No se pudo: la orden falló o el jugador ya no puede ({@code message}). */
        FAILED,
        /** Se calculó y no salió nada (no había nada que cambiar): lo de antes sigue a la vista. */
        NOTHING
    }

    public LivePreviewResultPayload {
        message = message == null ? "" : message;
        if (message.length() > MAX_MESSAGE) {
            message = message.substring(0, MAX_MESSAGE - 1) + "…";
        }
    }

    public static LivePreviewResultPayload of(LivePreviewRequestPayload request, Outcome outcome, int id,
                                              String message) {
        return new LivePreviewResultPayload(request.session(), request.seq(), outcome.ordinal(), id,
                !request.first(), message);
    }

    public Outcome outcomeValue() {
        Outcome[] values = Outcome.values();
        return outcome >= 0 && outcome < values.length ? values[outcome] : Outcome.NOTHING;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, LivePreviewResultPayload payload) {
        buffer.writeVarInt(payload.session());
        buffer.writeVarInt(payload.seq());
        buffer.writeVarInt(payload.outcome());
        buffer.writeVarInt(payload.id());
        buffer.writeBoolean(payload.refresh());
        buffer.writeString(payload.message(), MAX_MESSAGE);
    }

    private static LivePreviewResultPayload read(RegistryByteBuf buffer) {
        return new LivePreviewResultPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readBoolean(), buffer.readString(MAX_MESSAGE));
    }
}
