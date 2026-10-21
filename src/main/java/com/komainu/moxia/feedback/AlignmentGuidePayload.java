package com.komainu.moxia.feedback;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.placement.AlignmentGuide;
import com.komainu.moxia.shape.Vec3;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * El visualizador de la alineación del pincel de colocación en la mano ({@link AlignmentGuide}), para que el cliente
 * con KomaEdit dibuje las flechas. Las direcciones van redondeadas a milésimas y el centro a 1/16 de bloque: así un
 * pulso del ratón que no cambia nada no manda otro paquete. Solo se manda cuando cambia.
 *
 * @param guide la alineación que se ve, o {@code null} para quitarla
 */
public record AlignmentGuidePayload(AlignmentGuide.Result guide) implements CustomPayload {
    public static final Id<AlignmentGuidePayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "alignment_guide"));
    public static final PacketCodec<RegistryByteBuf, AlignmentGuidePayload> CODEC = PacketCodec.ofStatic(
            AlignmentGuidePayload::write,
            AlignmentGuidePayload::read
    );
    public static final AlignmentGuidePayload NONE = new AlignmentGuidePayload(null);

    /** La alineación redondeada, lista para mandar y comparar con la última enviada. */
    public static AlignmentGuidePayload of(AlignmentGuide.Result result) {
        if (result == null) {
            return NONE;
        }
        return new AlignmentGuidePayload(new AlignmentGuide.Result(result.state(), round(result.origin(), 16.0),
                round(result.x(), 1000.0), round(result.y(), 1000.0), round(result.z(), 1000.0),
                round(result.primary(), 1000.0), round(result.secondary(), 1000.0), result.random()));
    }

    public boolean visible() {
        return guide != null;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static Vec3 round(Vec3 vector, double steps) {
        return vector == null ? null : new Vec3(round(vector.x(), steps), round(vector.y(), steps),
                round(vector.z(), steps));
    }

    /** Redondeado y sin el −0 (un record compara −0 y 0 como distintos). */
    private static double round(double value, double steps) {
        return Math.rint(value * steps) / steps + 0.0;
    }

    private static void write(RegistryByteBuf buffer, AlignmentGuidePayload payload) {
        AlignmentGuide.Result guide = payload.guide();
        buffer.writeBoolean(guide != null);
        if (guide == null) {
            return;
        }
        buffer.writeByte(guide.state().ordinal());
        buffer.writeBoolean(guide.random());
        buffer.writeDouble(guide.origin().x());
        buffer.writeDouble(guide.origin().y());
        buffer.writeDouble(guide.origin().z());
        for (Vec3 vector : new Vec3[]{guide.x(), guide.y(), guide.z(), guide.primary(), guide.secondary()}) {
            buffer.writeBoolean(vector != null);
            if (vector != null) {
                buffer.writeFloat((float) vector.x());
                buffer.writeFloat((float) vector.y());
                buffer.writeFloat((float) vector.z());
            }
        }
    }

    private static AlignmentGuidePayload read(RegistryByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return NONE;
        }
        AlignmentGuide.State[] states = AlignmentGuide.State.values();
        AlignmentGuide.State state = states[Math.clamp(buffer.readByte(), 0, states.length - 1)];
        boolean random = buffer.readBoolean();
        Vec3 origin = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        Vec3[] vectors = new Vec3[5];
        for (int index = 0; index < vectors.length; index++) {
            if (buffer.readBoolean()) {
                vectors[index] = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
            }
        }
        return new AlignmentGuidePayload(new AlignmentGuide.Result(state, origin, vectors[0], vectors[1], vectors[2],
                vectors[3], vectors[4], random));
    }
}
