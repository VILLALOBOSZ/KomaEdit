package com.komainu.moxia.studio.net;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Por dónde sale cada orden del cliente, con un servidor nuevo, con uno anterior y con las órdenes del propio cliente.
 */
class CommandRouterTest {
    private static final long NOW = 2_000_000_000L;

    /** El juego de prueba: apunta lo que sale por cada lado. */
    static final class FakeLink implements CommandRouter.Link {
        final boolean packet;
        final boolean exempt;
        final List<String> packets = new ArrayList<>();
        final List<String> chat = new ArrayList<>();

        FakeLink(boolean packet, boolean exempt) {
            this.packet = packet;
            this.exempt = exempt;
        }

        @Override
        public boolean packet() {
            return packet;
        }

        @Override
        public boolean local(String command) {
            return Set.of("kegui", "kedisplay").contains(ButtonCommands.root(command));
        }

        @Override
        public boolean exempt() {
            return exempt;
        }

        @Override
        public void sendPacket(String command) {
            packets.add(command);
        }

        @Override
        public void sendChat(String command) {
            chat.add(command);
        }
    }

    @Test
    void withANewServerKomaEditGoesByThePacketAndNeverByTheChat() {
        CommandRouter router = new CommandRouter();
        FakeLink link = new FakeLink(true, false);
        for (int click = 0; click < 100; click++) {
            assertEquals(CommandRouter.Way.PACKET, router.send("komaedit path here", link, NOW));
        }
        assertEquals(100, link.packets.size());
        assertEquals(List.of(), link.chat);
    }

    @Test
    void theClientsOwnCommandsStayInTheClientAndDoNotCount() {
        CommandRouter router = new CommandRouter();
        FakeLink link = new FakeLink(false, false);
        for (int click = 0; click < 100; click++) {
            assertEquals(CommandRouter.Way.LOCAL, router.send("kegui regla on", link, NOW));
        }
        assertEquals(CommandRouter.Way.CHAT, router.send("komaedit path here", link, NOW),
                "Fabric las atiende sin red: no suman al antispam del servidor");
    }

    @Test
    void withAnOlderServerItGoesByTheChatUntilMinecraftWouldKickThePlayer() {
        CommandRouter router = new CommandRouter();
        FakeLink link = new FakeLink(false, false);
        List<CommandRouter.Way> ways = new ArrayList<>();
        for (int click = 0; click < 10; click++) {
            ways.add(router.send("komaedit path here", link, NOW));
        }
        assertEquals(List.of(CommandRouter.Way.CHAT, CommandRouter.Way.CHAT, CommandRouter.Way.CHAT,
                CommandRouter.Way.CHAT, CommandRouter.Way.CHAT, CommandRouter.Way.CHAT, CommandRouter.Way.CHAT,
                CommandRouter.Way.CHAT, CommandRouter.Way.HELD, CommandRouter.Way.HELD), ways);
        assertEquals(8, link.chat.size(), "lo frenado no sale");
    }

    @Test
    void operatorsAndTheHostAreNeverKickedSoTheyAreNeverHeld() {
        CommandRouter router = new CommandRouter();
        FakeLink link = new FakeLink(false, true);
        for (int click = 0; click < 50; click++) {
            assertEquals(CommandRouter.Way.CHAT, router.send("komaedit path here", link, NOW));
        }
    }

    @Test
    void whatThePacketDoesNotCarryGoesByTheChatAsBefore() {
        CommandRouter router = new CommandRouter();
        FakeLink link = new FakeLink(true, false);
        String huge = "komaedit text " + "a".repeat(ButtonCommandPayload.MAX_COMMAND);
        assertEquals(CommandRouter.Way.CHAT, router.send(huge, link, NOW));
        assertEquals(CommandRouter.Way.CHAT, router.send("worldedit version", link, NOW));
        assertEquals(List.of(), link.packets);
    }
}
