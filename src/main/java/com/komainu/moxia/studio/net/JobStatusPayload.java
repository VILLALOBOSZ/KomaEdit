package com.komainu.moxia.studio.net;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.progress.ProgressLine;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * La misma línea de la bossbar de progreso, con datos en lugar de texto, para que el pie del Studio enseñe la barra
 * y los botones Confirmar, Descartar y Cancelar. Sale cada pocos ticks mientras hay algo en marcha y una vez más,
 * vacía, al terminar.
 *
 * @param phase   ordinal de {@link ProgressLine.Phase}, o -1 si no hay nada en marcha
 * @param changes cambios de la vista previa que espera (fase de espera), o 0
 */
public record JobStatusPayload(int phase, String title, long done, long total, long elapsedNanos, long changes)
        implements CustomPayload {
    public static final Id<JobStatusPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "job_status"));
    public static final PacketCodec<RegistryByteBuf, JobStatusPayload> CODEC = PacketCodec.ofStatic(
            JobStatusPayload::write, JobStatusPayload::read);
    public static final JobStatusPayload IDLE = new JobStatusPayload(-1, "", 0L, 0L, 0L, 0L);

    public JobStatusPayload {
        title = title == null ? "" : title;
    }

    public static JobStatusPayload of(ProgressLine line, long changes) {
        return new JobStatusPayload(line.phase().ordinal(), line.title(), line.done(), line.total(),
                line.elapsedNanos(), changes);
    }

    public boolean active() {
        return phase >= 0 && phase < ProgressLine.Phase.values().length;
    }

    public ProgressLine.Phase phaseValue() {
        return active() ? ProgressLine.Phase.values()[phase] : null;
    }

    /** La línea como la lleva la bossbar, para reutilizar sus cuentas (porcentaje, ritmo, tiempo restante). */
    public ProgressLine line() {
        return active() ? new ProgressLine(title, phaseValue(), done, total, elapsedNanos, null) : null;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, JobStatusPayload payload) {
        buffer.writeVarInt(payload.phase() + 1);
        if (!payload.active()) {
            return;
        }
        buffer.writeString(payload.title(), 128);
        buffer.writeVarLong(payload.done());
        buffer.writeVarLong(payload.total());
        buffer.writeVarLong(payload.elapsedNanos());
        buffer.writeVarLong(payload.changes());
    }

    private static JobStatusPayload read(RegistryByteBuf buffer) {
        int phase = buffer.readVarInt() - 1;
        if (phase < 0 || phase >= ProgressLine.Phase.values().length) {
            return IDLE;
        }
        return new JobStatusPayload(phase, buffer.readString(128), buffer.readVarLong(), buffer.readVarLong(),
                buffer.readVarLong(), buffer.readVarLong());
    }
}
