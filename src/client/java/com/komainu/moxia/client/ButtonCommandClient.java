package com.komainu.moxia.client;

import com.komainu.moxia.studio.net.ButtonCommandPayload;
import com.komainu.moxia.studio.net.ButtonCommands;
import com.komainu.moxia.studio.net.CommandRouter;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * La única salida de órdenes del cliente al servidor: los botones del Studio, sus pantallas (notas, vistas) y las
 * teclas de KomaEdit mandan por aquí, y {@link CommandRouter} decide el camino. Con un servidor que tiene el paquete de
 * los botones van por él y el antispam del chat no se entera; las del propio cliente van al chat, que Fabric atiende
 * sin red; con un servidor anterior van por el chat como antes, frenando antes de que Minecraft eche al jugador.
 * {@code ChatCommandsAuditTest} no deja escribir órdenes en el chat desde otro sitio.
 */
public final class ButtonCommandClient {
    private static final CommandRouter ROUTER = new CommandRouter();

    private ButtonCommandClient() {
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ROUTER.reset());
    }

    /** Manda la orden; si hubo que frenarla, lo dice en la barra de acción. */
    public static boolean send(MinecraftClient client, String command) {
        return send(client, command, text -> {
            if (client.player != null) {
                client.player.sendMessage(text, true);
            }
        });
    }

    /**
     * @param held a quién decirle que no salió (con un servidor anterior, por no pasarse del antispam) y cuánto esperar
     * @return si salió
     */
    public static boolean send(MinecraftClient client, String command, Consumer<Text> held) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null || player.networkHandler == null || command == null || command.isBlank()) {
            return false;
        }
        long now = System.nanoTime();
        CommandRouter.Way way = ROUTER.send(command, new CommandRouter.Link() {
            @Override
            public boolean packet() {
                return ClientPlayNetworking.canSend(ButtonCommandPayload.ID);
            }

            @Override
            public boolean local(String line) {
                CommandDispatcher<FabricClientCommandSource> local = ClientCommandManager.getActiveDispatcher();
                return local != null && local.getRoot().getChild(ButtonCommands.root(line)) != null;
            }

            @Override
            public boolean exempt() {
                // Minecraft no echa al anfitrión de la partida ni a los operadores (nivel 1 o más).
                return client.isIntegratedServerRunning() || player.hasPermissionLevel(1);
            }

            @Override
            public void sendPacket(String line) {
                ClientPlayNetworking.send(new ButtonCommandPayload(line));
            }

            @Override
            public void sendChat(String line) {
                player.networkHandler.sendChatCommand(line);
            }
        }, now);
        if (way == CommandRouter.Way.HELD) {
            held.accept(Text.translatable("hud.komaedit.chat_wait", ROUTER.waitSeconds(now)));
            return false;
        }
        return true;
    }
}
