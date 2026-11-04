package com.komainu.moxia.studio.ui;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.studio.FigureVariation;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolParam;
import com.komainu.moxia.studio.tools.ToolUse;
import com.komainu.moxia.studio.tools.ToolValues;
import com.komainu.moxia.studio.tools.ui.ToolsPanel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ningún texto del Studio sale cortado con «…»: cada tarjeta de herramienta en cada uno de sus modos y maneras de
 * usarse, y las demás etapas, en las cuatro pantallas de referencia (854×480 con GUI 2, 1080p con GUI 4, 3 y 2), en
 * español y en inglés, desplazando el panel de arriba abajo con «Más ajustes» abierto. Una etiqueta o un valor que no
 * caben junto a los demás pasan a una fila de dos líneas; un aviso, a las líneas que haga falta.
 *
 * <p>Se cortan a propósito, con el texto entero en la ayuda: los nombres de las barras guardadas (los pone el
 * jugador), qué hace cada variación de la figura (dos líneas bajo su dibujo) y lo escrito en un campo de texto.</p>
 */
class StudioTextsFitTest {
    private static final int[][] SCREENS = {{427, 240}, {480, 270}, {640, 360}, {960, 540}};
    private static final List<String> LANGUAGES = List.of("es_es", "en_us");

    @BeforeAll
    static void thumbnails() {
        ChoiceThumbnails.setBlocking(true);
    }

    @Test
    void noToolCardCutsALabelOrAValue() {
        for (String language : LANGUAGES) {
            Map<String, Set<String>> cut = new TreeMap<>();
            for (int[] screen : SCREENS) {
                for (ToolCard card : ToolCatalog.ALL) {
                    for (ToolValues values : variants(card)) {
                        StudioDriver studio = studio(language, screen);
                        studio.host.preferences.setExpanded(ToolsPanel.MORE_KEY + card.id(), true);
                        studio.view.selectStage(StudioStage.TOOLS, studio.host);
                        studio.view.tools().select(card, studio.host);
                        studio.view.tools().setValues(card, values);
                        // Los nombres de las barras guardadas los pone el jugador: la ayuda de la fila lo dice entero.
                        Predicate<String> allowed = card.id().equals("hotbars") ? text -> true : text -> false;
                        collect(studio, screen, card.id(), allowed, cut);
                    }
                }
            }
            assertEquals(Map.of(), cut, language + ": textos cortados (texto → pantalla y tarjeta)");
        }
    }

    @Test
    void noOtherStageCutsItsTexts() {
        for (String language : LANGUAGES) {
            Map<String, Set<String>> cut = new TreeMap<>();
            FakeStudioHost names = new FakeStudioHost(language);
            List<String> effects = new ArrayList<>();
            for (FigureVariation variation : FigureVariation.values()) {
                effects.add(names.tr(variation.effectKey()));
            }
            // Qué hace cada variación va en dos líneas bajo su dibujo (y entero en la ayuda); la fórmula de serie de
            // la figura de expresión es lo escrito en su campo.
            Predicate<String> allowed = text -> effects.stream().anyMatch(effect -> effect.contains(text))
                    || text.startsWith("pared = ");
            for (int[] screen : SCREENS) {
                Map<String, Consumer<StudioDriver>> setups = new java.util.LinkedHashMap<>();
                for (PathShape shape : PathShape.values()) {
                    setups.put("figura " + shape.id(), driver -> {
                        StudioMockupsTest.path(driver, 3);
                        driver.view.figure().selectShape(driver.host, shape);
                        driver.view.selectStage(StudioStage.FIGURE, driver.host);
                    });
                }
                setups.put("lista de figuras", driver -> {
                    StudioMockupsTest.path(driver, 3);
                    driver.view.selectStage(StudioStage.FIGURE, driver.host);
                    driver.view.figure().setBrowsing(true);
                });
                setups.put("figuras sin puntos", driver -> driver.view.selectStage(StudioStage.FIGURE, driver.host));
                setups.put("trayectoria", driver -> {
                    StudioMockupsTest.path(driver, 4);
                    driver.view.selectStage(StudioStage.PATH, driver.host);
                });
                setups.put("trayectoria vacía", driver -> driver.view.selectStage(StudioStage.PATH, driver.host));
                setups.put("color", driver -> driver.view.selectStage(StudioStage.COLOR, driver.host));
                setups.put("planos", StudioMockupsTest::blueprints);
                setups.put("planos vacíos", driver -> driver.view.selectStage(StudioStage.BLUEPRINTS, driver.host));
                setups.put("recetas", driver -> driver.view.selectStage(StudioStage.RECIPES, driver.host));
                setups.put("instancias", driver -> driver.view.selectStage(StudioStage.INSTANCES, driver.host));
                setups.put("herramientas", driver -> StudioMockupsTest.tools(driver, null));
                for (Map.Entry<String, Consumer<StudioDriver>> setup : setups.entrySet()) {
                    StudioDriver studio = studio(language, screen);
                    setup.getValue().accept(studio);
                    collect(studio, screen, setup.getKey(), allowed, cut);
                }
            }
            assertEquals(Map.of(), cut, language + ": textos cortados (texto → pantalla y etapa)");
        }
    }

    /** Los valores de serie y uno por cada opción del modo, de la manera de usarla y de los botones cortos. */
    static List<ToolValues> variants(ToolCard card) {
        List<ToolValues> variants = new ArrayList<>();
        ToolValues base = card.defaults();
        variants.add(base);
        String mode = card.modeParam();
        for (ToolParam param : card.params()) {
            if (param instanceof ToolParam.Choice choice && !choice.dynamic()
                    && (param.id().equals(mode) || param.id().equals("use")
                    || choice.style() == ToolParam.Choice.Style.BUTTONS && choice.options().size() <= 6)) {
                for (String option : choice.options()) {
                    variants.add(base.with(param.id(), option));
                }
            }
        }
        for (ToolUse use : card.uses()) {
            variants.add(base.with("use", use.id()));
        }
        return variants;
    }

    private static StudioDriver studio(String language, int[] screen) {
        StudioDriver studio = new StudioDriver(language, screen[0], screen[1]);
        studio.host.context = StudioMockupsTest.SELECTED;
        studio.host.info = StudioMockupsTest.info();
        return studio;
    }

    /** Pinta página a página y apunta los textos acabados en «…» que no son así en los idiomas. */
    private static void collect(StudioDriver studio, int[] screen, String where, Predicate<String> allowed,
                                Map<String, Set<String>> cut) {
        Set<String> legit = new TreeSet<>();
        studio.host.translations().values().stream().filter(value -> value.endsWith("…")).forEach(legit::add);
        for (int page = 0; page < 30; page++) {
            studio.render();
            for (ImageCanvas.Drawn drawn : studio.canvas().texts()) {
                String text = drawn.text();
                if (text.endsWith("…") && !legit.contains(text)
                        && !allowed.test(text.substring(0, text.length() - 1).strip())) {
                    cut.computeIfAbsent(text, key -> new TreeSet<>()).add(screen[0] + "×" + screen[1] + " " + where);
                }
            }
            int before = studio.view.scroll();
            studio.key(StudioKey.of(StudioKey.Code.PAGE_DOWN));
            if (studio.view.scroll() == before) {
                return;
            }
        }
    }
}
