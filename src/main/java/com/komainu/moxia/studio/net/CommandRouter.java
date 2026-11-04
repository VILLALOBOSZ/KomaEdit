package com.komainu.moxia.studio.net;

/**
 * Por dónde sale cada orden que manda el cliente por su cuenta (los botones del Studio, sus pantallas y las teclas de
 * KomaEdit). Es la decisión de {@code client.ButtonCommandClient}, en Java puro para que las pruebas pasen por ella:
 *
 * <ol>
 *     <li>Las del propio cliente ({@code kegui}, {@code kedisplay}) van al chat, donde Fabric las atiende sin mandar
 *     nada al servidor.</li>
 *     <li>Las de KomaEdit van por el paquete de los botones ({@link ButtonCommandPayload}) si el servidor lo tiene:
 *     no suman al antispam.</li>
 *     <li>Lo demás (un servidor sin el paquete, o una orden que no cabe en él) va por el chat como siempre, salvo que
 *     Minecraft fuera a echar al jugador por spam ({@link ChatSpamGuard}): entonces se queda sin mandar y se le avisa
 *     de cuánto esperar.</li>
 * </ol>
 */
public final class CommandRouter {
    public enum Way {
        /** Al chat, pero la atiende el cliente: no llega al servidor. */
        LOCAL,
        PACKET,
        CHAT,
        /** No se mandó: por el chat Minecraft echaría al jugador. */
        HELD
    }

    /** Lo que hace falta del juego. */
    public interface Link {
        /** Si el servidor tiene el paquete de los botones. */
        boolean packet();

        /** Si la orden la atiende el propio cliente. */
        boolean local(String command);

        /** Si a este jugador Minecraft no lo echa por spam (OP o anfitrión de la partida). */
        boolean exempt();

        void sendPacket(String command);

        void sendChat(String command);
    }

    private final ChatSpamGuard guard = new ChatSpamGuard();

    public Way send(String command, Link link, long nanos) {
        if (link.local(command)) {
            link.sendChat(command);
            return Way.LOCAL;
        }
        if (link.packet() && ButtonCommands.problem(command).isEmpty()) {
            link.sendPacket(command);
            return Way.PACKET;
        }
        if (!link.exempt() && !guard.allow(nanos)) {
            return Way.HELD;
        }
        link.sendChat(command);
        return Way.CHAT;
    }

    /** Cuánto esperar para la siguiente por el chat, para decírselo al jugador. */
    public int waitSeconds(long nanos) {
        return guard.waitSeconds(nanos);
    }

    /** Al entrar en un servidor (su contador empieza de cero). */
    public void reset() {
        guard.reset();
    }
}
