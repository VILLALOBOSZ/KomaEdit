package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.brush.ez.EzBrushType;
import com.komainu.moxia.brush.paint.FootprintLibrary;
import com.komainu.moxia.brush.paint.PaintConfig;
import com.komainu.moxia.brush.paint.PaintGradient;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.tools.PaintBrushCard;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ToolParam;
import com.komainu.moxia.studio.tools.ToolPreset;
import com.komainu.moxia.studio.tools.ToolValues;
import com.komainu.moxia.studio.ui.ChoiceThumbnails;
import com.komainu.moxia.studio.ui.FakeStudioHost;
import com.komainu.moxia.studio.ui.StudioDriver;
import com.komainu.moxia.studio.ui.StudioStage;
import com.komainu.moxia.studio.ui.StudioView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La tarjeta «Pintar terreno»: lo que manda Construir lo entiende el pincel, cada ajuste llega al pincel de la mano
 * con {@code pincel actualizar} y las galerías tienen sus miniaturas (la huella y la muestra del degradado).
 */
class PaintBrushSyncTest {
    private static final ToolContext ALL = new ToolContext(true, ToolCatalog.permissionNodes(), true, 512L, 0, false);
    private static final String PREFIX = "komaedit pincel paint ";

    @BeforeAll
    static void thumbnails() {
        ChoiceThumbnails.setBlocking(true);
    }

    @Test
    void theDefaultCardPaintsGrassToStoneBySlopeWithNothingToLearn() {
        ToolCard card = ToolCatalog.byId("paintbrush");
        assertNotNull(card, "la tarjeta está en el catálogo, junto al sello de relieve");
        List<String> ids = ToolCatalog.ALL.stream().map(ToolCard::id).toList();
        assertEquals(ids.indexOf("stamp") + 1, ids.indexOf("paintbrush"));
        String command = card.command(card.defaults(), false);
        assertEquals("komaedit pincel paint hierba_piedra 8 pendiente 60", command);
        PaintConfig config = parse(command);
        assertEquals(PaintConfig.defaults(), config, "lo de serie de la tarjeta es lo de serie del pincel");
        assertEquals(List.of("paint_palette", "custom_palette", "stamp", "gradient", "radius", "strength", "seed"),
                card.essentials(), "a la vista solo lo de empezar; bloquear la semilla, en Más ajustes");
        assertTrue(card.problems(card.defaults(), ALL).isEmpty());
    }

    @Test
    void everyPresetAndGalleryOptionBuildsABrushTheServerUnderstands() {
        ToolCard card = ToolCatalog.byId("paintbrush");
        for (ToolPreset preset : card.presets()) {
            parse(card.command(card.apply(card.defaults(), preset), false));
        }
        ToolParam.Choice palettes = (ToolParam.Choice) card.defaults().param("paint_palette");
        for (String option : palettes.options()) {
            ToolValues values = card.defaults().with("paint_palette", option);
            assertEquals(option.equals(PaintBrushCard.CUSTOM) ? "##Grayscale" : option,
                    parse(card.command(values, false)).palette(), option);
            assertNotNull(ChoiceThumbnails.get("paintbrush", "paint_palette", option), "muestra de " + option);
        }
        ToolParam.Choice stamps = (ToolParam.Choice) card.defaults().param("stamp");
        assertEquals(FootprintLibrary.builtIn().names(), stamps.options());
        for (String option : stamps.options()) {
            assertEquals(option, parse(card.command(card.defaults().with("stamp", option), false)).footprint());
            assertNotNull(ChoiceThumbnails.get("paintbrush", "stamp", option), "miniatura de " + option);
        }
        for (String gradient : List.of("pendiente", "altura", "ruido", "huella")) {
            assertEquals(PaintGradient.byId(gradient),
                    parse(card.command(card.defaults().with("gradient", gradient), false)).gradient());
        }
    }

    @Test
    void everyOptionReachesThePaintBrushInTheHand() {
        ToolCard card = ToolCatalog.byId("paintbrush");
        StudioDriver studio = studio();
        ToolsPanel panel = studio.view.tools();
        panel.select(card, studio.host);
        studio.render();
        studio.click("Al pincel");
        String created = studio.host.last();
        assertTrue(created.startsWith(PREFIX), created);
        studio.host.heldTool = "pincel:p1";
        studio.advance(10);
        int sent = studio.host.sent.size();
        int start = sent;
        String previous = created;
        // Primero la paleta propia (para que su texto cuente) y los modos que enseñan sus ajustes.
        for (String gradient : List.of("pendiente", "altura", "ruido")) {
            panel.setValues(card, panel.values(card).with("gradient", gradient).with("paint_palette",
                    PaintBrushCard.CUSTOM));
            studio.advance(10);
            studio.advance(BrushSync.DELAY_MILLIS);
            for (ToolParam param : card.params()) {
                ToolValues before = panel.values(card);
                ToolValues next = changed(card, param, before);
                if (next == null || next.equals(before)) {
                    continue;
                }
                panel.setValues(card, next);
                studio.advance(10);
                studio.advance(BrushSync.DELAY_MILLIS);
                String expected = ToolsPanel.brushCommand(card, "", next);
                if (expected.equals(previous)) {
                    continue;
                }
                sent = studio.host.sent.size();
                assertEquals(BrushSync.update(expected), studio.host.last(), param.id() + " llega al pincel");
                assertBrushUses(next, expected, param.id());
                previous = expected;
            }
        }
        assertTrue(sent - start >= 20, (sent - start) + " cambios llegaron al pincel");
    }

    private static StudioDriver studio() {
        StudioDriver studio = new StudioDriver(new StudioView(), new FakeStudioHost("es_es", false), 960, 1600);
        studio.host.context = ALL;
        studio.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.OFF);
        studio.host.info = new ToolsInfo(true, new int[]{8, 5, 8}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of("minecraft:plains"), null);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        return studio;
    }

    /** Otro valor válido para el ajuste, o {@code null} si no hay ninguno que probar. */
    private static ToolValues changed(ToolCard card, ToolParam param, ToolValues values) {
        return switch (param) {
            case ToolParam.Number number -> {
                Integer current = values.number(number.id());
                int base = current != null ? current : number.defaultValue();
                int step = number.range().step();
                yield values.with(number.id(), base + step <= number.range().max() ? base + step : base - step);
            }
            case ToolParam.Choice choice -> {
                if (choice.id().equals("gradient") || choice.id().equals("paint_palette")) {
                    yield null;
                }
                int index = choice.options().indexOf(values.choice(choice.id()));
                yield values.with(choice.id(), choice.options().get((index + 1) % choice.options().size()));
            }
            case ToolParam.Toggle toggle -> values.with(toggle.id(), !values.toggle(toggle.id()));
            case ToolParam.Block block -> values.with(block.id(), "minecraft:stone");
            case ToolParam.Text text -> switch (text.id()) {
                case "custom_palette" -> values.with("custom_palette", "##Magma");
                case "only_blocks" -> values.with("only_blocks", "grass_block,dirt");
                case "mask" -> values.with("mask", "#solid");
                default -> values.with(text.id(), "algo");
            };
        };
    }

    /** Lo que entiende el pincel de la orden: lleva el valor nuevo de cada ajuste que se ve. */
    private static void assertBrushUses(ToolValues values, String command, String label) {
        PaintConfig config = parse(command);
        String palette = values.choice("paint_palette").equals(PaintBrushCard.CUSTOM)
                ? values.text("custom_palette").strip() : values.choice("paint_palette");
        assertEquals(palette, config.palette(), label);
        assertEquals((int) values.number("radius"), config.radius(), label);
        assertEquals(values.number("strength") / 100.0, config.strength(), 1.0E-9, label);
        assertEquals(PaintGradient.byId(values.choice("gradient")), config.gradient(), label);
        assertEquals(values.choice("stamp"), config.footprint(), label);
        Integer seed = values.number("seed");
        assertEquals(seed == null ? null : seed.longValue(), config.seed(), label);
        assertEquals(values.toggle("lock_seed"), config.seedLocked(), label);
        assertEquals(values.number("dab_spacing") / 100.0, config.spacing(), 1.0E-9, label);
        assertEquals(values.number("hardness") / 100.0, config.hardness(), 1.0E-9, label);
        assertEquals(values.number("variation") / 100.0, config.jitter(), 1.0E-9, label);
        boolean slope = values.choice("gradient").equals("pendiente");
        assertEquals(slope ? Math.min(values.number("slope_low"), values.number("slope_high"))
                : PaintConfig.DEFAULT_SLOPE_LOW, config.slopeLow(), 1.0E-9, label);
        boolean heights = values.choice("gradient").equals("altura") && values.number("height_low") != null
                && values.number("height_high") != null;
        assertEquals(heights ? Math.min(values.number("height_low"), values.number("height_high")) : Double.NaN,
                config.heightLow(), 1.0E-9, label);
        assertEquals(values.choice("gradient").equals("ruido") ? (int) values.number("noise_size")
                : PaintConfig.DEFAULT_NOISE_SIZE, config.noiseSize(), label);
        assertEquals((int) values.number("layers"), config.layers(), label);
        assertEquals(values.toggle("surface_only"), config.surfaceOnly(), label);
        assertEquals(values.toggle("shaped"), config.shaped(), label);
        assertEquals(values.toggle("any_block"), config.everything(), label);
        assertEquals(blank(values.text("only_blocks")), config.blocks(), label);
        assertEquals(blank(values.text("mask")), config.mask(), label);
        assertEquals(values.toggle("brush_preview"), config.preview(), label);
    }

    private static String blank(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static PaintConfig parse(String command) {
        String arguments = command.startsWith(PREFIX) ? command.substring(PREFIX.length())
                : command.substring(("komaedit pincel actualizar paint ").length());
        return PaintConfig.parse(EzBrushType.PAINT.options(arguments));
    }
}
