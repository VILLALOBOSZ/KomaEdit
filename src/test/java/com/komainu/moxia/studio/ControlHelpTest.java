package com.komainu.moxia.studio;

import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolParam;
import com.komainu.moxia.studio.tools.ui.ToolsPanel;
import com.komainu.moxia.studio.ui.FakeStudioHost;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cada control se explica solo: dice qué le pasa al objeto (no qué parámetro es ni su bandera) y, en los números, sus
 * extremos en palabras. Así no puede entrar un control nuevo sin su explicación.
 */
class ControlHelpTest {
    /** Números que no tienen extremos con sentido: una semilla, un rumbo en grados o una altura absoluta. */
    private static final Set<String> WITHOUT_ENDS = Set.of("seed", "heading", "level_y");

    @Test
    void endsAreReadFromLowBarHigh() {
        ControlHelp help = ControlHelp.of("Lo alto que sale", "bajo|alto");
        assertTrue(help.hasEnds());
        assertEquals("bajo", help.low());
        assertEquals("de bajo a alto", help.ends("de %s a %s"));
        assertEquals("Lo alto que sale\nde bajo a alto", help.brief("de %s a %s"));
        ControlHelp none = ControlHelp.of("Gira", "gui.komaedit.tools.tree.ends.heading");
        assertFalse(none.hasEnds(), "sin traducción, la clave tal cual: no hay extremos");
        assertEquals("Gira", none.brief("de %s a %s"));
        assertFalse(ControlHelp.of("x", "|alto").hasEnds(), "un extremo solo no vale");
    }

    @Test
    void everyToolSettingSaysWhatItDoesToTheObject() {
        for (String language : List.of("es_es", "en_us")) {
            Map<String, String> lang = FakeStudioHost.load(language);
            List<String> problems = new ArrayList<>();
            for (ToolCard card : ToolCatalog.ALL) {
                for (ToolParam param : card.params()) {
                    String help = lang.get(card.helpKey(param));
                    String label = lang.get(param.labelKey());
                    if (help == null || help.isBlank()) {
                        problems.add(card.id() + "/" + param.id() + ": sin ayuda");
                        continue;
                    }
                    String effect = ToolsPanel.withoutCommand(help);
                    if (effect.equalsIgnoreCase(label) || effect.equalsIgnoreCase(label + ".")) {
                        problems.add(card.id() + "/" + param.id() + ": la ayuda repite la etiqueta");
                    }
                    if (!(param instanceof ToolParam.Number) || WITHOUT_ENDS.contains(param.id())) {
                        continue;
                    }
                    if (help.matches(".*\\(-[\\p{L}\\d_]+\\).*")) {
                        problems.add(card.id() + "/" + param.id() + ": la ayuda de un número nombra su bandera");
                    }
                    String ends = lang.get(card.endsKey(param));
                    if (!ControlHelp.of(effect, ends).hasEnds()) {
                        problems.add(card.id() + "/" + param.id() + ": sin extremos en palabras");
                    }
                }
            }
            assertTrue(problems.isEmpty(), language + ":\n" + String.join("\n", problems));
        }
    }

    @Test
    void everyFigureNumberHasItsEffectAndEnds() {
        List<String> controls = List.of("radius", "spacing", "thickness", "height", "dither", "start_scale",
                "end_scale", "start_spacing", "end_spacing", "twist", "rhythm_interval", "conform_radius",
                "surface_offset", "socket_interval");
        for (String language : List.of("es_es", "en_us")) {
            Map<String, String> lang = FakeStudioHost.load(language);
            for (String control : controls) {
                assertTrue(lang.containsKey("gui.komaedit.help." + control), language + " " + control);
                assertTrue(ControlHelp.of("", lang.get("gui.komaedit.ends." + control)).hasEnds(),
                        language + " " + control + ": sin extremos");
            }
            assertTrue(lang.get("gui.komaedit.ends.format").contains("%s"));
        }
    }

    /**
     * Lo esencial arriba: como mucho cuatro controles a la vista antes de Más ajustes. Lo que se elige viendo (las
     * galerías de miniaturas y los buscadores) no cuenta: no son líneas ni números que haya que entender. Tampoco el
     * interruptor «Pincel / Selección»: dice cómo se usa la herramienta, no qué sale.
     */
    @Test
    void lessIsInSightEachToolShowsAtMostFourControlsBeforeMoreSettings() {
        for (ToolCard card : ToolCatalog.ALL) {
            long essentials = card.params().stream()
                    .filter(param -> card.essential(param) && param.visible(card.defaults()))
                    .filter(param -> !(param instanceof ToolParam.Choice choice
                            && (choice.style() == ToolParam.Choice.Style.GALLERY
                            || choice.style() == ToolParam.Choice.Style.SEARCH
                            || choice.style() == ToolParam.Choice.Style.SWITCH)))
                    .count();
            assertTrue(essentials <= 4, card.id() + " enseña " + essentials + " controles de entrada");
        }
    }
}
