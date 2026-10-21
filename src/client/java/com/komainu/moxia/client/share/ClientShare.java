package com.komainu.moxia.client.share;

import com.komainu.moxia.share.ShareCodePayload;
import com.komainu.moxia.share.ShareImportPayload;
import com.komainu.moxia.share.ShareOfferPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.Optional;

/**
 * Lo que llega del servidor al compartir: el código (que va al portapapeles solo si el jugador acaba de pulsar
 * «Compartir» en el Studio; un servidor no puede escribir en el portapapeles cuando quiera) y lo que trae un paquete
 * que se va a importar, para la ventana del Studio (solo si se pegó en el Studio).
 */
public final class ClientShare {
    /** Lo que se espera el código después de pulsar «Compartir». */
    private static final long EXPECT_MILLIS = 30_000L;
    /** Lo que deja escribir el chat en una orden. */
    private static final int CHAT_LIMIT = 256;

    private static long expectingUntil;
    private static long offerExpectedUntil;
    private static ShareCodePayload result;
    private static ShareOfferPayload offer;

    private ClientShare() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ShareCodePayload.ID, (payload, context) ->
                context.client().execute(() -> received(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(ShareOfferPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    // Solo la de un «Pegar código» del Studio: lo importado desde el chat se contesta en el chat.
                    if (System.currentTimeMillis() <= offerExpectedUntil) {
                        offerExpectedUntil = 0L;
                        offer = payload;
                    }
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientShare::clear));
    }

    /** El Studio va a pedir un código: el que llegue en un rato se copia al portapapeles. */
    public static void expect() {
        expectingUntil = System.currentTimeMillis() + EXPECT_MILLIS;
    }

    private static void received(MinecraftClient client, ShareCodePayload payload) {
        if (System.currentTimeMillis() > expectingUntil) {
            // Exportado desde el chat: allí tiene su botón de copiar.
            return;
        }
        expectingUntil = 0L;
        if (!payload.code().isEmpty()) {
            client.keyboard.setClipboard(payload.code());
        }
        result = payload;
    }

    /**
     * «Pegar código»: lo manda por un paquete (cabe mucho más que en el chat) o, si el servidor no lo tiene, por el
     * chat cuando cabe.
     *
     * @return si se pudo mandar
     */
    public static boolean importCode(MinecraftClient client, String code) {
        offerExpectedUntil = System.currentTimeMillis() + EXPECT_MILLIS;
        if (ClientPlayNetworking.canSend(ShareImportPayload.ID) && code.length() <= ShareImportPayload.MAX_CODE) {
            ClientPlayNetworking.send(new ShareImportPayload(code));
            return true;
        }
        String command = "komaedit share import " + code;
        if (command.length() < CHAT_LIMIT && client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatCommand(command);
            return true;
        }
        offerExpectedUntil = 0L;
        return false;
    }

    public static Optional<ShareCodePayload> takeResult() {
        ShareCodePayload current = result;
        result = null;
        return Optional.ofNullable(current);
    }

    public static Optional<ShareOfferPayload> takeOffer() {
        ShareOfferPayload current = offer;
        offer = null;
        return Optional.ofNullable(current);
    }

    private static void clear() {
        expectingUntil = 0L;
        offerExpectedUntil = 0L;
        result = null;
        offer = null;
    }
}
