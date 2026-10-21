package com.komainu.moxia.studio.live;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.recipe.RecipeCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Lo que pide la vista previa en vivo del Studio: el Construir del que parte ({@code first}) y cada cambio de ajuste
 * después. Va por su propio paquete y no como orden de chat: no suma al antispam de Minecraft, y el servidor se queda
 * solo con la última petición de cada jugador ({@link LiveQueue}) y la contesta ({@link LivePreviewResultPayload}).
 *
 * @param session la vista previa en vivo a la que pertenece: cada Construir abre una
 * @param seq     número de la petición dentro de la sesión (el Construir es la 0)
 * @param first   el Construir que abre la sesión: el servidor apunta dónde cae y las siguientes salen ahí, aunque el
 *                jugador ya mire a otro lado o haya cerrado el Studio
 * @param command la orden de la herramienta con su vista previa ({@code komaedit tree oak 20 -v}), sin barra; vacía
 *                si es una figura
 * @param recipe  la figura que construir, o {@code null} si es una orden
 */
public record LivePreviewRequestPayload(int session, int seq, boolean first, String command, ProceduralRecipe recipe)
        implements CustomPayload {
    /** Lo más largo que se acepta (un texto largo de la tarjeta Escribir texto cabe de sobra). */
    public static final int MAX_COMMAND = 8_192;
    public static final Id<LivePreviewRequestPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID,
            "live_preview_request"));
    public static final PacketCodec<RegistryByteBuf, LivePreviewRequestPayload> CODEC = PacketCodec.ofStatic(
            LivePreviewRequestPayload::write, LivePreviewRequestPayload::read);

    public LivePreviewRequestPayload {
        command = command == null ? "" : command.strip();
        if (command.length() > MAX_COMMAND) {
            throw new IllegalArgumentException("Orden de la vista previa en vivo demasiado larga");
        }
        if (recipe == null && command.isEmpty()) {
            throw new IllegalArgumentException("La vista previa en vivo no pide nada");
        }
    }

    public static LivePreviewRequestPayload command(int session, int seq, boolean first, String command) {
        return new LivePreviewRequestPayload(session, seq, first, command, null);
    }

    public static LivePreviewRequestPayload figure(int session, int seq, boolean first, ProceduralRecipe recipe) {
        return new LivePreviewRequestPayload(session, seq, first, "", recipe);
    }

    public boolean figure() {
        return recipe != null;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, LivePreviewRequestPayload payload) {
        buffer.writeVarInt(payload.session());
        buffer.writeVarInt(payload.seq());
        buffer.writeBoolean(payload.first());
        buffer.writeBoolean(payload.figure());
        if (payload.figure()) {
            RecipeCodec.write(buffer, payload.recipe());
        } else {
            buffer.writeString(payload.command(), MAX_COMMAND);
        }
    }

    private static LivePreviewRequestPayload read(RegistryByteBuf buffer) {
        int session = buffer.readVarInt();
        int seq = buffer.readVarInt();
        boolean first = buffer.readBoolean();
        if (buffer.readBoolean()) {
            return figure(session, seq, first, RecipeCodec.read(buffer));
        }
        return command(session, seq, first, buffer.readString(MAX_COMMAND));
    }
}
