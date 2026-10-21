package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.brush.ez.EzBrushType;
import com.komainu.moxia.nature.RockSettings;
import com.komainu.moxia.nature.TreeSettings;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ToolParam;
import com.komainu.moxia.studio.tools.ToolUse;
import com.komainu.moxia.studio.tools.TouchUpCard;
import com.komainu.moxia.studio.tools.ToolValues;
import com.komainu.moxia.studio.ui.FakeStudioHost;
import com.komainu.moxia.studio.ui.StudioDriver;
import com.komainu.moxia.studio.ui.StudioStage;
import com.komainu.moxia.studio.ui.StudioView;
import com.komainu.moxia.terrain.HeightmapBrush;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El pincel que hizo el Studio sigue a su tarjeta: cada cambio de cada ajuste le llega con
 * {@code pincel actualizar}, el servidor lo entiende y el pincel usa el valor nuevo. Nunca toca otro objeto.
 */
class BrushSyncTest {
    private static final ToolContext ALL = new ToolContext(true, ToolCatalog.permissionNodes(), true, 512L, 0, false);

    @BeforeAll
    static void thumbnails() {
        // Las miniaturas, al pedirlas: sin hilos que sigan pintando cuando empiece otra prueba.
        com.komainu.moxia.studio.ui.ChoiceThumbnails.setBlocking(true);
    }

    @Test
    void followsOnlyTheBrushItMadeWhileItIsInTheHand() {
        BrushSync sync = new BrushSync();
        assertNull(sync.tick("komaedit pincel tree oak", "pincel:a", 0), "sin pincel hecho, nada");
        sync.linked("tree", "brush", "komaedit pincel tree oak", 0);
        assertNull(sync.tick("komaedit pincel tree oak 20", null, 10), "el servidor aún no ha marcado la mano");
        assertNull(sync.tick("komaedit pincel tree oak 20", "pincel:a", 20));
        assertEquals("pincel:a", sync.tool());
        assertNull(sync.tick("komaedit pincel tree oak 20", "pincel:a", 20 + BrushSync.DELAY_MILLIS - 1),
                "espera a que se deje de tocar");
        assertEquals("komaedit pincel actualizar tree oak 20",
                sync.tick("komaedit pincel tree oak 20", "pincel:a", 20 + BrushSync.DELAY_MILLIS));
        assertNull(sync.tick("komaedit pincel tree oak 20", "pincel:a", 5_000), "no repite");
        // Con otra cosa en la mano no manda nada; al volver a coger el pincel, lo pendiente llega.
        assertNull(sync.tick("komaedit pincel tree oak 21", "pincel:otro", 6_000));
        assertNull(sync.tick("komaedit pincel tree oak 21", null, 7_000));
        assertEquals("komaedit pincel actualizar tree oak 21", sync.tick("komaedit pincel tree oak 21", "pincel:a",
                8_000));
        // Algo mal escrito: el pincel se queda como estaba.
        assertNull(sync.tick(null, "pincel:a", 9_000));
    }

    /**
     * Cada orden suma un segundo al antispam de Minecraft, que echa a quien no es OP al pasar de diez: pulsar «+» con
     * calma (un clic cada 350 ms durante 10 s) no puede mandar más de una orden por segundo, y el último valor llega.
     */
    @Test
    void neverSendsMoreThanOneUpdatePerSecond() {
        BrushSync sync = new BrushSync();
        sync.linked("tree", "brush", "komaedit pincel tree oak", 0);
        sync.tick("komaedit pincel tree oak", "pincel:a", 10);
        List<Long> sentAt = new java.util.ArrayList<>();
        String last = null;
        String command = "komaedit pincel tree oak";
        for (long now = 50; now <= 12_000; now += 50) {
            if (now <= 10_000 && now % 350 == 0) {
                command = "komaedit pincel tree oak " + (20 + now / 350);
            }
            String send = sync.tick(command, "pincel:a", now);
            if (send != null) {
                sentAt.add(now);
                last = send;
            }
        }
        for (int index = 1; index < sentAt.size(); index++) {
            assertTrue(sentAt.get(index) - sentAt.get(index - 1) >= BrushSync.MIN_INTERVAL_MILLIS, sentAt.toString());
        }
        assertTrue(sentAt.size() >= 5 && sentAt.size() <= 12, sentAt.toString());
        assertEquals(BrushSync.update(command), last, "el último cambio llega al pincel");
    }

    @Test
    void aSuperwandOrNothingInTheHandIsNeverTaken() {
        BrushSync sync = new BrushSync();
        sync.linked("tree", "brush", "komaedit pincel tree oak", 0);
        assertNull(sync.tick("komaedit pincel tree oak 20", "superwand:x", 100));
        assertNull(sync.tool());
        assertNull(sync.tick("komaedit pincel tree oak 20", null, BrushSync.ADOPT_MILLIS + 1));
        assertNull(sync.card(), "nunca llegó a la mano: se olvida");
    }

    @Test
    void everyOptionOfEveryBrushReachesTheBrushInTheHand() {
        for (String kind : List.of("tree", "rock", "biome", "stamp")) {
            ToolCard card = ToolCatalog.byId(kind.equals("biome") ? "touchup" : kind);
            StudioDriver studio = studio();
            ToolsPanel panel = studio.view.tools();
            panel.select(card, studio.host);
            // Con el interruptor en el pincel; el bioma es un modo de Retocar.
            ToolValues start0 = panel.values(card).with(ToolUse.PARAM, ToolUse.BRUSH.id());
            panel.setValues(card, kind.equals("biome") ? start0.with(TouchUpCard.MODE, TouchUpCard.BIOME) : start0);
            studio.render();
            studio.click("Al pincel");
            String created = studio.host.last();
            assertTrue(created.startsWith("komaedit pincel "), created);
            String action = "";
            studio.host.heldTool = "pincel:k3";
            studio.advance(10);
            int sent = studio.host.sent.size();
            int start = sent;
            String previous = created;
            for (ToolParam param : card.params()) {
                ToolValues before = panel.values(card);
                ToolValues next = changed(card, param, before);
                if (next == null || !param.visible(before)) {
                    continue;
                }
                panel.setValues(card, next);
                studio.advance(10);
                studio.advance(BrushSync.MIN_INTERVAL_MILLIS);
                String expected = ToolsPanel.brushCommand(card, action, next);
                if (expected.equals(previous)) {
                    // Un ajuste que no pinta nada con estos valores (los bloques de las raíces, sin raíces).
                    assertEquals(sent, studio.host.sent.size(), card.id() + "." + param.id());
                    continue;
                }
                sent++;
                assertEquals(sent, studio.host.sent.size(), card.id() + "." + param.id() + " llega al pincel");
                assertEquals(BrushSync.update(expected), studio.host.last(), card.id() + "." + param.id());
                assertBrushUses(kind, card, param, next, expected);
                previous = expected;
            }
            assertTrue(sent - start >= 3, card.id() + ": " + (sent - start) + " cambios llegaron al pincel");
        }
    }

    @Test
    void turningOffThePreviewOnClickReachesTheBrush() {
        StudioDriver studio = studio();
        ToolCard stamp = ToolCatalog.byId("stamp");
        ToolsPanel panel = studio.view.tools();
        panel.select(stamp, studio.host);
        panel.setValues(stamp, panel.values(stamp).with("brush_preview", true));
        studio.render();
        studio.click("Al pincel");
        assertTrue(studio.host.last().endsWith(" -p"), studio.host.last());
        studio.host.heldTool = "pincel:k3";
        studio.advance(10);
        studio.click("MÁS AJUSTES");
        studio.click("Vista previa en cada clic");
        assertFalse(panel.values(stamp).toggle("brush_preview"));
        studio.advance(10);
        studio.advance(BrushSync.MIN_INTERVAL_MILLIS);
        String update = studio.host.last();
        assertTrue(update.startsWith("komaedit pincel actualizar heightmap "), update);
        assertFalse(EzBrushType.HEIGHTMAP.options(arguments(update, "komaedit pincel actualizar heightmap "))
                .has('p'), "el pincel deja de hacer vista previa en cada clic");
        // Y aunque se vaya a otra herramienta, los cambios del sello siguen llegando.
        panel.select(ToolCatalog.byId("tree"), studio.host);
        panel.setValues(stamp, panel.values(stamp).with("radius", 20));
        studio.advance(10);
        studio.advance(BrushSync.MIN_INTERVAL_MILLIS);
        assertTrue(studio.host.last().startsWith("komaedit pincel actualizar heightmap montana 20 15 "),
                studio.host.last());
    }

    private static StudioDriver studio() {
        StudioDriver studio = new StudioDriver(new StudioView(), new FakeStudioHost("es_es", false), 960, 1600);
        studio.host.context = ALL;
        studio.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.OFF);
        studio.host.info = new ToolsInfo(true, new int[]{8, 5, 8}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of("minecraft:plains", "minecraft:cherry_grove"), null);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        return studio;
    }

    /** Otro valor válido para el ajuste, o {@code null} si no hay ninguno que probar. */
    private static ToolValues changed(ToolCard card, ToolParam param, ToolValues values) {
        if (param.id().equals(ToolUse.PARAM) || param.id().equals(TouchUpCard.MODE)) {
            // Otra manera de usarla u otro retoque ya no es el mismo pincel: se prueba aparte.
            return null;
        }
        return switch (param) {
            case ToolParam.Number number -> {
                Integer current = values.number(number.id());
                String hint = card.autoHint(number.id(), values);
                int base = current != null ? current : hint == null || hint.contains("%") ? number.defaultValue()
                        : (int) Math.round(Double.parseDouble(hint) * Math.pow(10, number.decimals()));
                int step = number.range().step();
                int value = base + step <= number.range().max() ? base + step : base - step;
                yield values.with(number.id(), value);
            }
            case ToolParam.Choice choice -> {
                List<String> options = choice.dynamic() ? List.of("minecraft:plains", "minecraft:cherry_grove")
                        : choice.options();
                int index = options.indexOf(values.choice(choice.id()));
                yield values.with(choice.id(), options.get((index + 1) % options.size()));
            }
            case ToolParam.Toggle toggle -> values.with(toggle.id(), !values.toggle(toggle.id()));
            case ToolParam.Block block -> values.with(block.id(), "minecraft:mossy_cobblestone");
            case ToolParam.Text text -> switch (text.id()) {
                case "mask" -> values.with("mask", "grass_block");
                case "palette" -> values.with("palette", "stone,andesite");
                // Un sello propio necesita su PNG en el servidor: se prueba solo la orden.
                case "custom" -> null;
                default -> values.with(text.id(), "algo");
            };
        };
    }

    /** Lo que entiende el servidor de la orden: el pincel lleva el valor nuevo. */
    private static void assertBrushUses(String kind, ToolCard card, ToolParam param, ToolValues values,
                                        String command) {
        String label = kind + "." + param.id();
        switch (kind) {
            case "tree" -> {
                String settings = arguments(command, "komaedit pincel tree ");
                TreeSettings.parse(settings);
                assertSameVisible(card, values, card.parsePreset(settings), label);
            }
            case "rock" -> {
                String settings = arguments(command, "komaedit pincel rock ");
                RockSettings.parse(settings);
                assertSameVisible(card, values, card.parsePreset(settings), label);
            }
            case "biome" -> {
                BrushOptions options = EzBrushType.BIOME.options(arguments(command, "komaedit pincel biome "));
                assertEquals(values.choice("biome").replace("minecraft:", ""), options.positional(0), label);
                assertEquals(values.formatted("radius"), options.positional(1), label);
                assertEquals(values.toggle("vertical"), options.has('v'), label);
                assertEquals(values.text("mask").isEmpty() ? null : values.text("mask"),
                        options.has('m') ? options.flag('m') : null, label);
            }
            default -> {
                // Con el analizador de opciones del pincel de relieve (el sello en sí lo lee del disco del servidor).
                BrushOptions options = EzBrushType.HEIGHTMAP.options(arguments(command, "komaedit pincel heightmap "));
                String typed = values.text("custom").strip();
                assertEquals(typed.isEmpty() ? values.choice("stamp") : typed, options.positional(0), label);
                assertEquals(values.formatted("radius"), options.positional(1), label);
                assertEquals(values.number("height") == null
                        ? Integer.toString(HeightmapBrush.naturalHeight(values.choice("stamp"),
                        values.number("radius")))
                        : values.formatted("height"), options.positional(2), label);
                assertEquals(values.choice("mode"), options.positional(3), label);
                assertEquals(values.choice("palette"), options.positional(4), label);
                assertEquals(values.number("intensity") == 100 ? null : values.formatted("intensity"),
                        options.has('i') ? options.flag('i') : null, label);
                assertEquals(values.number("variation") == 60 ? null : values.formatted("variation"),
                        options.has('x') ? options.flag('x') : null, label);
                assertEquals(values.formatted("seed"), options.has('s') ? options.flag('s') : null, label);
                assertEquals(values.toggle("lock_seed"), options.has('k'), label);
                assertEquals(values.choice("rotation").equals("mirada") ? null : values.choice("rotation"),
                        options.has('r') ? options.flag('r') : null, label);
                assertEquals(values.formatted("neutral"), options.has('c') ? options.flag('c') : null, label);
                assertEquals(values.text("mask").isEmpty() ? null : values.text("mask"),
                        options.has('m') ? options.flag('m') : null, label);
                assertEquals(values.toggle("brush_preview"), options.has('p'), label);
            }
        }
    }

    /** Lo que se ve en la tarjeta es lo que lleva el pincel (lo oculto, como las raíces sin raíces, no viaja). */
    private static void assertSameVisible(ToolCard card, ToolValues expected, ToolValues parsed, String label) {
        for (ToolParam param : card.params()) {
            // Cómo se usa no viaja en los ajustes del pincel: es el pincel mismo.
            if (param.visible(expected) && !param.id().equals(ToolUse.PARAM)) {
                assertEquals(expected.changed().get(param.id()), parsed.changed().get(param.id()),
                        label + " → " + param.id());
            }
        }
    }

    private static String arguments(String command, String prefix) {
        assertTrue(command.startsWith(prefix), command);
        return command.substring(prefix.length()).strip();
    }
}
