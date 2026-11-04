package com.komainu.moxia.studio.net;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Una orden de KomaEdit que manda un botón del cliente (Construir, Estimar, Guardar preset, las acciones de Más
 * ajustes, las teclas de confirmar y repetir, las notas…) sin pasar por el chat. Cada orden de chat suma al antispam
 * de Minecraft, que echa a quien no es OP tras unas diez seguidas; este paquete no suma. El servidor la ejecuta como si
 * el jugador la escribiera, con sus permisos ({@link ButtonCommandService}), y solo si es de KomaEdit
 * ({@link ButtonCommands}).
 *
 * @param command la orden sin barra ({@code komaedit tree oak 20})
 */
public record ButtonCommandPayload(String command) implements CustomPayload {
    /** Lo más largo que se lleva, lo mismo que la vista previa en vivo; algo más largo va por el chat, como antes. */
    public static final int MAX_COMMAND = 8_192;
    public static final Id<ButtonCommandPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "button_command"));
    public static final PacketCodec<RegistryByteBuf, ButtonCommandPayload> CODEC = PacketCodec.ofStatic(
            (buffer, payload) -> buffer.writeString(payload.command(), MAX_COMMAND),
            buffer -> new ButtonCommandPayload(buffer.readString(MAX_COMMAND)));

    public ButtonCommandPayload {
        command = command == null ? "" : command;
        if (command.length() > MAX_COMMAND) {
            throw new IllegalArgumentException("Orden demasiado larga para el paquete de los botones");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
