package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ui.ToolsPanel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las maquetas de la auditoría del cliente: las tarjetas cuyos textos salían cortados, en modo compacto (854×480 con
 * GUI 2) y a 1080p con GUI 3, desplazadas hasta la fila que lo enseñaba. Con
 * {@code KOMAEDIT_CLIENT_AUDIT_RENDER=<carpeta>} pinta los PNG (el antes y el después salen de la misma escena).
 */
class ClientAuditMockupsTest {
    /** Archivo, herramienta, ajustes de dos en dos y el texto (su principio) hasta el que se desplaza el panel. */
    private record Case(String file, String tool, String[] settings, String scrollTo) {
    }

    private static final List<Case> CASES = List.of(
            new Case("tejado", "roof", new String[0], "Muros"),
            new Case("inundar", "touchup", new String[]{"touch", "inundar"}, "Nivel"),
            new Case("drenar", "touchup", new String[]{"touch", "drenar"}, "Quitar"),
            new Case("displays", "display", new String[0], "Paso al"),
            new Case("texto", "text", new String[0], "Fuente"),
            new Case("superficie", "loft", new String[0], "Entre"),
            new Case("relieves", "landform", new String[0], "A lo largo"),
            new Case("sello", "stamp", new String[0], "Orientación"),
            new Case("pintar_pincel", "paint", new String[]{"use", "brush"}, "Pendiente del"),
            new Case("rio", "river", new String[0], "Seguir"),
            new Case("arbol", "tree", new String[]{"roots", "true"}, "Material"),
            new Case("reconstruir", "rebuild", new String[0], "Eje"),
            new Case("simetria", "symmetry", new String[0], "Sin centro"),
            new Case("roca", "rock", new String[0], "deepslate"));

    @Test
    void renderAuditMockups() throws IOException {
        String target = System.getenv("KOMAEDIT_CLIENT_AUDIT_RENDER");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "KOMAEDIT_CLIENT_AUDIT_RENDER sin definir");
        assertTrue(ImageCanvas.font().loaded(), "la fuente del juego tiene que estar en el classpath");
        ChoiceThumbnails.setBlocking(true);
        Path folder = Path.of(target);
        Files.createDirectories(folder);
        for (Case item : CASES) {
            for (int[] screen : new int[][]{{427, 240, 2}, {640, 360, 3}}) {
                StudioDriver driver = new StudioDriver("es_es", screen[0], screen[1]);
                driver.host.context = StudioMockupsTest.SELECTED;
                driver.host.info = StudioMockupsTest.info();
                driver.host.preferences.setExpanded(ToolsPanel.MORE_KEY + item.tool(), true);
                StudioMockupsTest.tools(driver, item.tool());
                var card = ToolCatalog.byId(item.tool());
                var values = driver.view.tools().values(card);
                for (int index = 0; index + 1 < item.settings().length; index += 2) {
                    String value = item.settings()[index + 1];
                    values = values.with(item.settings()[index],
                            value.equals("true") || value.equals("false") ? (Object) Boolean.valueOf(value) : value);
                }
                driver.view.tools().setValues(card, values);
                scrollTo(driver, item.scrollTo());
                String name = (screen[0] == 427 ? "854_gui2_" : "1080p_gui3_") + item.file() + "_es.png";
                ImageIO.write(driver.paint(screen[2], ui -> StudioMockupsTest.world(ui, screen[0], screen[1])),
                        "png", folder.resolve(name).toFile());
            }
        }
        // Lo que ve quien llega por primera vez a cada etapa sin nada hecho, y una figura con un punto puesto.
        java.util.Map<String, java.util.function.Consumer<StudioDriver>> stages = new java.util.LinkedHashMap<>();
        stages.put("planos_vacio", driver -> driver.view.selectStage(StudioStage.BLUEPRINTS, driver.host));
        stages.put("recetas_vacio", driver -> driver.view.selectStage(StudioStage.RECIPES, driver.host));
        stages.put("instancias_vacio", driver -> driver.view.selectStage(StudioStage.INSTANCES, driver.host));
        stages.put("trayectoria_vacia", driver -> driver.view.selectStage(StudioStage.PATH, driver.host));
        stages.put("figura_un_punto", driver -> {
            driver.host.points.add(new int[]{120, 64, -40});
            driver.view.selectStage(StudioStage.FIGURE, driver.host);
        });
        for (var stage : stages.entrySet()) {
            for (int[] screen : new int[][]{{427, 240, 2}, {640, 360, 3}}) {
                StudioDriver driver = new StudioDriver("es_es", screen[0], screen[1]);
                driver.host.context = StudioMockupsTest.SELECTED;
                driver.host.info = StudioMockupsTest.info();
                stage.getValue().accept(driver);
                driver.host.status = "";
                String name = (screen[0] == 427 ? "854_gui2_" : "1080p_gui3_") + stage.getKey() + "_es.png";
                ImageIO.write(driver.paint(screen[2], ui -> StudioMockupsTest.world(ui, screen[0], screen[1])),
                        "png", folder.resolve(name).toFile());
            }
        }
    }

    /** Pasa páginas hasta que se vea un texto que empieza por {@code prefix} y lo deja en el primer tercio. */
    static void scrollTo(StudioDriver driver, String prefix) {
        if (prefix == null) {
            return;
        }
        for (int page = 0; page < 40; page++) {
            driver.render();
            var viewport = driver.view.frame().viewport();
            var drawn = driver.canvas().texts().stream()
                    .filter(text -> text.text().startsWith(prefix) && text.y() >= viewport.y()
                            && text.y() < viewport.bottom() - 8)
                    .findFirst();
            if (drawn.isPresent() && drawn.get().y() < viewport.y() + viewport.height() * 2 / 3) {
                return;
            }
            int before = driver.view.scroll();
            driver.view.mouseScrolled(driver.view.frame().innerLeft() + 2, viewport.y() + 2, -1, driver.host);
            if (driver.view.scroll() == before) {
                return;
            }
        }
    }
}
