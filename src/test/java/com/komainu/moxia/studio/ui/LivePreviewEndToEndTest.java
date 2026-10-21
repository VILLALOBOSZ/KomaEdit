package com.komainu.moxia.studio.ui;

import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La vista previa en vivo de punta a punta ({@link LiveLoop}): un ajuste que cambia en el Studio llega al servidor,
 * se calcula, vuelve al cliente y cambia lo que se ve. Una prueba por cada tipo de vista previa: una tarjeta que
 * coloca un objeto con fantasma, una figura y una tarjeta que trabaja sobre la selección.
 *
 * <p>Lo que se pide en cada una: lo nuevo se ve antes de {@value #REDO_MILLIS} ms desde el último cambio; el fantasma
 * sigue donde se dejó y con su giro; nunca hay un tick sin nada a la vista; de varios cambios seguidos solo se calcula
 * el último; y nada va por el chat, así que el antispam de Minecraft no se entera.</p>
 */
class LivePreviewEndToEndTest {
    /** Desde el último cambio hasta verlo: el retardo de la vista en vivo, el cálculo de prueba y la red. */
    static final long REDO_MILLIS = 600L;

    @Test
    void aGhostCardIsRedoneWhereItWasLeft() {
        LiveLoop loop = new LiveLoop(true);
        StudioDriver studio = loop.studio;
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("tree"), studio.host);
        studio.render();
        studio.click("Plantar");
        loop.run(400);
        String planted = loop.client.last().ghost();
        assertNotNull(planted, "sale el fantasma del árbol");
        assertTrue(loop.client.link.fromStudio());

        // El jugador lo mueve y lo gira con el gizmo, y después gira la cámara: ya no mira a donde lo plantó.
        loop.client.move(4, 0, -3);
        loop.client.turn();
        var where = loop.client.session.transform();
        loop.server.crosshair = "300,70,300";
        loop.run(200);

        // Tres cambios seguidos de la altura: solo cuenta el último.
        int computed = loop.server.computed.size();
        studio.wheelValue("Altura", 1);
        loop.run(50);
        studio.wheelValue("Altura", 1);
        loop.run(50);
        studio.wheelValue("Altura", 1);
        long changed = loop.now();
        String wanted = studio.view.tools().buildCommand(studio.host, false);
        long shownAt = runUntil(loop, () -> wanted.equals(loop.client.last().ghost()), 2_000);
        assertTrue(shownAt - changed <= REDO_MILLIS, "rehecho en " + (shownAt - changed) + " ms");
        assertEquals(where, loop.client.last().where(), "donde se dejó y con su giro, mire donde mire el jugador");
        assertEquals(List.of(wanted), loop.server.computed.subList(computed, loop.server.computed.size()),
                "las intermedias no se calculan");
        assertAlwaysVisible(loop, true);
        assertEquals(List.of(), loop.server.chatCommands, "nada por el chat: el antispam no cuenta");

        // Con el Studio cerrado y vuelto a abrir (el clic en el mundo se olvida), sigue en su sitio.
        loop.closeStudio();
        loop.run(300);
        loop.openStudio();
        studio.render();
        studio.wheelValue("Grosor", 1);
        String thicker = studio.view.tools().buildCommand(studio.host, false);
        runUntil(loop, () -> thicker.equals(loop.client.last().ghost()), 2_000);
        assertEquals(where, loop.client.last().where());
    }

    @Test
    void theFigureGhostFollowsTheFigure() {
        LiveLoop loop = new LiveLoop(true);
        StudioDriver studio = loop.studio;
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 2, false);
        studio.host.points = new ArrayList<>(List.of(new int[]{0, 64, 0}, new int[]{10, 64, 0}));
        studio.host.previewBlocks = 900;
        studio.view.selectStage(StudioStage.FIGURE, studio.host);
        studio.render();
        studio.click("Construir");
        loop.run(400);
        assertNotNull(loop.client.last().ghost(), "sale el fantasma de la figura");
        loop.client.move(0, 2, 5);
        var where = loop.client.session.transform();

        int computed = loop.server.computed.size();
        widen(studio);
        loop.run(50);
        widen(studio);
        long changed = loop.now();
        String wanted = "figura radio " + studio.view.figure().draft().radius();
        long shownAt = runUntil(loop, () -> wanted.equals(loop.client.last().ghost()), 2_000);
        assertTrue(shownAt - changed <= REDO_MILLIS, "rehecha en " + (shownAt - changed) + " ms");
        assertEquals(where, loop.client.last().where(), "la figura nueva sale donde se movió la de antes");
        assertEquals(List.of(wanted), loop.server.computed.subList(computed, loop.server.computed.size()));
        assertAlwaysVisible(loop, true);
        assertEquals(List.of(), loop.server.chatCommands);

        // Grande (más que el límite de serie): no se rehace y el pie lo dice.
        studio.host.previewBlocks = 300_000;
        widen(studio);
        loop.run(1_500);
        assertEquals(wanted, loop.client.last().ghost());
        loop.client.place();
        loop.run(100);
        studio.render();
        assertTrue(studio.texts().stream().anyMatch(text -> text.contains("En vivo en pausa")), studio.texts()
                .toString());
    }

    @Test
    void aSelectionCardIsRedoneWithoutFlickering() {
        LiveLoop loop = new LiveLoop(true);
        StudioDriver studio = loop.studio;
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 120_000L, 0, false);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("snow"), studio.host);
        studio.render();
        studio.click("Nevar");
        loop.run(500);
        assertEquals("komaedit retocar nieve 2", loop.client.last().preview(), "la vista previa que espera");

        int computed = loop.server.computed.size();
        for (int change = 0; change < 3; change++) {
            studio.wheelValue("Capas", 1);
            loop.run(50);
        }
        long changed = loop.now();
        String wanted = studio.view.tools().buildCommand(studio.host, false);
        assertEquals("komaedit retocar nieve 5", wanted);
        long shownAt = runUntil(loop, () -> wanted.equals(loop.client.last().preview()), 2_000);
        assertTrue(shownAt - changed <= REDO_MILLIS + LiveLoop.READ_MILLIS, "rehecha en " + (shownAt - changed)
                + " ms");
        assertEquals(List.of(wanted), loop.server.computed.subList(computed, loop.server.computed.size()));
        assertAlwaysVisible(loop, false);
        assertEquals(List.of(), loop.server.chatCommands);

        // Cambios mientras aún se lee la selección de la anterior: esperan, no se rechazan.
        studio.wheelValue("Capas", -1);
        loop.run(300);
        studio.wheelValue("Capas", -1);
        String back = studio.view.tools().buildCommand(studio.host, false);
        runUntil(loop, () -> back.equals(loop.client.last().preview()), 2_000);
        assertTrue(loop.client.seen.stream().noneMatch(seen -> seen.preview() == null
                && seen.at() > shownAt), "sin parpadeo");
        assertEquals(List.of(), loop.server.refused, "ninguna rechazada por «ya hay una operación leyendo»");
        assertTrue(loop.server.maxWaiting <= 1, "en la cola espera una como mucho");
    }

    @Test
    void aSlowServerOnlyComputesTheLastChangeAndNeverPilesUp() {
        // Un servidor cargado: cada árbol tarda más de un segundo. El Studio manda cada cambio al dejar de tocar,
        // también mientras calcula; el servidor termina el que tiene entre manos y después hace solo el último.
        LiveLoop loop = new LiveLoop(true, 1_200L);
        StudioDriver studio = loop.studio;
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("tree"), studio.host);
        studio.render();
        studio.click("Plantar");
        runUntil(loop, () -> loop.client.last().ghost() != null, 3_000);
        int computed = loop.server.computed.size();
        List<String> asked = new ArrayList<>();
        for (int change = 0; change < 4; change++) {
            studio.wheelValue("Altura", 1);
            asked.add(studio.view.tools().buildCommand(studio.host, false));
            loop.run(300);
        }
        String last = asked.getLast();
        runUntil(loop, () -> last.equals(loop.client.last().ghost()), 5_000);
        assertEquals(List.of(asked.getFirst(), last), loop.server.computed.subList(computed,
                loop.server.computed.size()), "termina el que calculaba y hace solo el último");
        assertTrue(loop.server.maxWaiting <= 1);
        assertAlwaysVisible(loop, true);
    }

    @Test
    void anOlderServerStillGetsItThroughTheChatWithoutTheKick() {
        LiveLoop loop = new LiveLoop(false);
        StudioDriver studio = loop.studio;
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("tree"), studio.host);
        studio.render();
        studio.click("Plantar");
        loop.run(400);
        loop.client.move(2, 0, 0);
        var where = loop.client.session.transform();
        // Un minuto tocando la altura sin parar, un cambio cada 400 ms.
        for (int change = 0; change < 150; change++) {
            studio.wheelValue("Altura", change % 2 == 0 ? 1 : -1);
            loop.run(400);
        }
        loop.run(2_000);
        assertEquals(studio.view.tools().buildCommand(studio.host, false), loop.client.last().ghost());
        assertEquals(where, loop.client.last().where());
        assertTrue(loop.server.maxSpam <= LiveLoop.SPAM_KICK, "antispam al máximo: " + loop.server.maxSpam);
        assertAlwaysVisible(loop, true);
    }

    /** Desde que apareció lo primero, en ningún tick deja de verse el fantasma o la vista previa. */
    private static void assertAlwaysVisible(LiveLoop loop, boolean ghost) {
        boolean started = false;
        for (LiveLoop.Seen seen : loop.client.seen) {
            boolean visible = ghost ? seen.ghost() != null : seen.preview() != null;
            if (started) {
                assertTrue(visible, "a los " + seen.at() + " ms no se veía nada: " + loop.client.seen);
            }
            started |= visible;
        }
        assertTrue(started);
    }

    /** Pasa ticks hasta que se cumpla {@code done}; devuelve la hora en que se cumplió. */
    private static long runUntil(LiveLoop loop, java.util.function.BooleanSupplier done, long limit) {
        long start = loop.now();
        while (!done.getAsBoolean()) {
            assertTrue(loop.now() - start <= limit, "no llegó en " + limit + " ms: " + loop.client.last());
            loop.step();
        }
        return loop.now();
    }

    private static void widen(StudioDriver studio) {
        ProceduralRecipe draft = studio.view.figure().draft();
        studio.view.figure().setDraft(draft.copy(draft.name(), draft.shape(), draft.pattern(), draft.radius() + 1,
                draft.spacing(), draft.thickness(), draft.rollDegrees(), draft.height(), draft.paletteId(),
                draft.gradientAxis(), draft.dither(), draft.reverse()));
    }
}
