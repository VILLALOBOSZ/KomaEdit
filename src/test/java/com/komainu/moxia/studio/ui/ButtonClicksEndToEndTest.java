package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.net.ButtonCommandService;
import com.komainu.moxia.studio.net.ButtonCommands;
import com.komainu.moxia.studio.net.CommandRouter;
import com.komainu.moxia.studio.net.VanillaAntispam;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un builder sin OP pulsando el Studio deprisa, de punta a punta y sin Minecraft. El Studio es el de verdad
 * ({@link StudioView} con {@link FakeStudioHost}); lo que manda sale por la misma decisión que en el juego
 * ({@link CommandRouter}, la de {@code ButtonCommandClient}); el servidor de prueba atiende el paquete de los botones
 * con lo del servidor de verdad ({@link ButtonCommandService#admit}) y el chat con el antispam de Minecraft
 * ({@link VanillaAntispam}). Antes todo iba por el chat y al undécimo clic seguido el jugador salía expulsado.
 */
class ButtonClicksEndToEndTest {
    private static final long CLICK_GAP = 80_000_000L;
    private static final long START = 4_000_000_000L;
    private static int players;

    /** El servidor de prueba y lo que le llega. */
    private static final class Server implements CommandRouter.Link {
        final boolean packet;
        final UUID player = new UUID(7L, ++players);
        final VanillaAntispam chat = new VanillaAntispam(20, START);
        final List<String> ran = new ArrayList<>();
        final List<String> told = new ArrayList<>();
        long now = START;

        Server(boolean packet) {
            this.packet = packet;
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
            return false;
        }

        @Override
        public void sendPacket(String command) {
            ButtonCommandService.Admission admission = ButtonCommandService.admit(player, command, now);
            if (admission.run()) {
                ran.add(command);
            } else if (admission.message() != null) {
                told.add(admission.message());
            }
        }

        @Override
        public void sendChat(String command) {
            chat.command(now);
            if (!chat.kicked()) {
                ran.add(command);
            }
        }
    }

    /**
     * Treinta árboles plantados uno detrás de otro, cambiando de especie entre medias.
     *
     * @param before como antes del paquete de los botones: todo por el chat
     */
    private static List<CommandRouter.Way> thirtyTrees(Server server, boolean before) {
        StudioDriver studio = new StudioDriver("es_es", 960, 540);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L, 0, false);
        // Construir con su vista previa, que se manda como orden (la vista previa en vivo ya va por su paquete).
        studio.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.BUILD);
        CommandRouter router = new CommandRouter();
        List<CommandRouter.Way> ways = new ArrayList<>();
        studio.host.onSend = before ? command -> {
            server.sendChat(command);
            ways.add(CommandRouter.Way.CHAT);
        } : command -> ways.add(router.send(command, server, server.now));
        studio.click("Herramientas");
        studio.press("tool:tree");
        ways.clear();
        List<String> species = List.of("Abeto", "Abedul", "Roble");
        for (int tree = 0; tree < 30; tree++) {
            studio.click(species.get(tree % species.size()));
            studio.click("Plantar");
            server.now += CLICK_GAP;
            studio.host.now += CLICK_GAP / 1_000_000L;
        }
        return ways;
    }

    @Test
    void beforeTheFixTheEleventhClickGotTheBuilderKicked() {
        Server server = new Server(false);
        thirtyTrees(server, true);
        assertEquals(11, server.chat.kickedAt(), "el fallo del #74, que la prueba sabe ver");
    }

    @Test
    void thirtyClicksInARowGetNoKickAndNoWarning() {
        Server server = new Server(true);
        List<CommandRouter.Way> ways = thirtyTrees(server, false);
        assertEquals(30, ways.size(), "un envío por clic en Plantar: " + ways);
        assertTrue(ways.stream().allMatch(way -> way == CommandRouter.Way.PACKET), ways.toString());
        assertEquals(0, server.chat.commands(), "nada por el chat: el antispam no se entera");
        assertFalse(server.chat.kicked());
        assertEquals(List.of(), server.told, "ni avisos de ir deprisa");
        assertEquals(30, server.ran.size());
        assertTrue(server.ran.stream().allMatch(command -> command.startsWith("komaedit tree ")),
                server.ran.toString());
    }

    @Test
    void withAnOlderServerTheClicksThatWouldGetAKickWaitInstead() {
        Server server = new Server(false);
        List<CommandRouter.Way> ways = thirtyTrees(server, false);
        assertEquals(30, ways.size());
        assertFalse(server.chat.kicked(), "antispam al máximo: " + server.chat.max());
        long held = ways.stream().filter(way -> way == CommandRouter.Way.HELD).count();
        assertTrue(held > 0, "con 30 clics tan seguidos alguno espera");
        assertEquals(30 - held, server.ran.size(), "lo demás llega por el chat, como antes");
    }
}
