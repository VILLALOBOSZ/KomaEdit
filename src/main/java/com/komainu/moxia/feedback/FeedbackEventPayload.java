package com.komainu.moxia.feedback;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Aviso de un momento con sonido: confirmar una vista previa, terminar una edición larga o fallar. El cliente decide
 * si suena (y a qué volumen) según sus ajustes; sin el mod no llega nada.
 */
public record FeedbackEventPayload(Kind kind) implements CustomPayload {
    public static final Id<FeedbackEventPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "feedback_event"));
    public static final PacketCodec<RegistryByteBuf, FeedbackEventPayload> CODEC = PacketCodec.ofStatic(
            (buffer, payload) -> buffer.writeByte(payload.kind().ordinal()),
            buffer -> new FeedbackEventPayload(Kind.of(buffer.readByte()))
    );

    public enum Kind {
        CONFIRM,
        DONE,
        FAIL;

        static Kind of(int ordinal) {
            Kind[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FAIL;
        }
    }

    public FeedbackEventPayload {
        if (kind == null) {
            throw new IllegalArgumentException("Falta el tipo de aviso");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
