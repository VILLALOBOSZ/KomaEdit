package com.komainu.moxia.studio.ui;

import com.komainu.moxia.blueprint.BlueprintRef;
import com.komainu.moxia.blueprint.BlueprintScope;
import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cuántos clics cuestan cuatro tareas típicas, pulsando el Studio como un jugador (las cifras van en el PR, junto a
 * las de antes). Se cuentan clics de ratón en el panel y en el mundo; no hace falta desplazar en ninguna.
 */
class TaskClicksTest {
    @BeforeAll
    static void thumbnails() {
        ChoiceThumbnails.setBlocking(true);
    }

    private static StudioDriver fresh(boolean selection, int points) {
        StudioDriver studio = new StudioDriver("es_es", 960, 540);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), selection, selection ? 720L : 0L,
                points, false);
        studio.host.info = new ToolsInfo(true, selection ? new int[]{10, 6, 12} : new int[0], List.of(), Map.of(),
                Map.of(), ToolsInfo.Symmetry.OFF, null, List.of(), null);
        studio.render();
        return studio;
    }

    @Test
    void plantASpruceWhereYouLook() {
        StudioDriver studio = fresh(false, 0);
        studio.click("Herramientas");
        studio.click("Árbol");
        studio.click("Abeto");
        studio.click("Plantar");
        assertEquals("komaedit tree spruce", studio.host.last());
        assertEquals(4, studio.clicks(), "la primera vez");
        // La siguiente vez el Studio abre en la etapa y con la herramienta de la última vez.
        StudioDriver again = new StudioDriver(new StudioView(), studio.host, 960, 540);
        again.render();
        again.click("Abedul");
        again.click("Plantar");
        assertEquals("komaedit tree birch", again.host.last());
        assertEquals(2, again.clicks(), "con la herramienta recordada");
    }

    @Test
    void roofOverTheSelectedHouse() {
        StudioDriver studio = fresh(true, 0);
        studio.click("Herramientas");
        studio.click("Tejado");
        studio.click("Cuatro");
        studio.click("Construir tejado");
        assertEquals("komaedit roof hip spruce_planks", studio.host.last());
        assertEquals(4, studio.clicks(), "con tipo a cuatro aguas");
        // Cambiar el material por el que se mira: un clic (el cuentagotas).
        studio.resetCounts();
        studio.host.lookedAt = "minecraft:deepslate_tiles";
        studio.click("Usar el bloque que miras");
        studio.click("Construir tejado");
        assertEquals("komaedit roof hip deepslate_tiles", studio.host.last());
        assertEquals(2, studio.clicks());
    }

    @Test
    void aRiverAlongAPath() {
        StudioDriver studio = fresh(false, 0);
        studio.click("Trayectoria");
        for (int index = 0; index < 4; index++) {
            // Cada punto es un clic en el mundo (a la izquierda del panel); el juego lo convierte en un nodo.
            studio.clickAt(100 + index * 30, 300);
            studio.host.points.add(new int[]{index * 6, 64, 0});
        }
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L, 4, false);
        studio.click("Río o cañón");
        studio.click("Tallar cauce");
        assertEquals("komaedit terreno rio natural", studio.host.last());
        assertEquals(7, studio.clicks(), "etapa, cuatro puntos, «Usar con: Río» y construir");
    }

    @Test
    void stampABlueprint() {
        StudioDriver studio = fresh(false, 0);
        studio.host.blueprints.list.add(new BlueprintSummary(BlueprintRef.display("casita", BlueprintScope.SHARED),
                "casita", true, false, "Villalobos", 0L, 9, 9, 7, 342, List.of("aldea"), "casita"));
        studio.click("Planos");
        studio.click("casita");
        studio.click("Estampar");
        assertEquals("komaedit pincel stamp " + BlueprintRef.display("casita", BlueprintScope.SHARED),
                studio.host.last());
        assertEquals(3, studio.clicks());
    }
}
