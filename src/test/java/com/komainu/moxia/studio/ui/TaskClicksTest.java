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
 * Cuántos clics cuestan las tareas típicas, pulsando el Studio como un jugador (las cifras van en el PR, junto a las
 * de antes). Se cuentan clics de ratón en el panel y en el mundo; no hace falta desplazar en ninguna. Las herramientas
 * de la cuadrícula se pulsan por su icono ({@link StudioDriver#press}): con el nombre en dos líneas, su texto entero
 * solo está en la ayuda, que también lleva la estrella.
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
        studio.press("tool:tree");
        studio.click("Abeto");
        studio.click("Plantar");
        assertEquals("komaedit tree spruce -v", studio.host.last(), "con la vista previa en vivo de serie");
        assertEquals(4, studio.clicks(), "la primera vez");
        // La siguiente vez el Studio abre en la etapa y con la herramienta de la última vez.
        StudioDriver again = new StudioDriver(new StudioView(), studio.host, 960, 540);
        again.render();
        again.click("Abedul");
        again.click("Plantar");
        assertEquals("komaedit tree birch -v", again.host.last());
        assertEquals(2, again.clicks(), "con la herramienta recordada");
    }

    @Test
    void roofOverTheSelectedHouse() {
        StudioDriver studio = fresh(true, 0);
        studio.click("Herramientas");
        studio.press("tool:roof");
        studio.click("Cuatro");
        studio.click("Construir tejado");
        assertEquals("komaedit roof hip spruce_planks -v", studio.host.last());
        assertEquals(4, studio.clicks(), "con tipo a cuatro aguas");
        // Cambiar el material por el que se mira: un clic (el cuentagotas).
        studio.resetCounts();
        studio.host.lookedAt = "minecraft:deepslate_tiles";
        studio.click("Usar el bloque que miras");
        studio.click("Construir tejado");
        assertEquals("komaedit roof hip deepslate_tiles -v", studio.host.last());
        assertEquals(2, studio.clicks());
    }

    /**
     * El tejado sin selección hecha. Antes había que cerrar el Studio y marcar //pos1 y //pos2 (o la varita) y volver;
     * ahora se arrastra la caja en el mundo con el Studio abierto: un arrastre de esquina a esquina de los muros.
     */
    @Test
    void roofWithoutASelectionYet() {
        StudioDriver studio = fresh(false, 0);
        studio.click("Herramientas");
        studio.press("tool:roof");
        studio.click("Cuatro");
        assertEquals(StudioView.WorldAction.SELECT, studio.view.worldAction(studio.host), "arrastrar selecciona");
        // El arrastre en el mundo, como lo hace la pantalla: de una esquina de los muros a la otra.
        com.komainu.moxia.studio.world.BoxSelect box = new com.komainu.moxia.studio.world.BoxSelect();
        com.komainu.moxia.shape.Vec3 eye = new com.komainu.moxia.shape.Vec3(-8.5, 80.5, -8.5);
        box.press(new com.komainu.moxia.gizmo.GizmoMath.Ray(eye, new com.komainu.moxia.shape.Vec3(0.5, 69.5, 0.5)
                .subtract(eye)), new int[]{0, 64, 0}, 100.0, 0);
        box.drag(new com.komainu.moxia.gizmo.GizmoMath.Ray(eye, new com.komainu.moxia.shape.Vec3(9.5, 69.5, 11.5)
                .subtract(eye)), new int[]{9, 69, 11});
        var selection = box.release(new int[]{9, 69, 11}, 100).box();
        assertEquals(10, selection.sizeX());
        assertEquals(6, selection.sizeY());
        assertEquals(12, selection.sizeZ());
        // El servidor la recibe y el Studio la ve.
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, selection.volume(), 0, false);
        studio.host.info = new ToolsInfo(true, new int[]{selection.sizeX(), selection.sizeY(), selection.sizeZ()},
                List.of(), Map.of(), Map.of(), ToolsInfo.Symmetry.OFF, null, List.of(), null);
        studio.click("Construir tejado");
        assertEquals("komaedit roof hip spruce_planks -v", studio.host.last());
        assertEquals(4, studio.clicks(), "herramienta, tejado, tipo y construir, más un arrastre en el mundo");
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
        studio.click("Excavar río");
        studio.click("Tallar cauce");
        assertEquals("komaedit terreno rio natural -v", studio.host.last());
        assertEquals(7, studio.clicks(), "etapa, cuatro puntos, «Usar con: Río» y construir");
    }

    /**
     * Una trenza sobre tres puntos, retorcida una vuelta y más fina al final. Antes: Trayectoria, tres puntos,
     * Figuras, elegir la trenza (2), Modificadores, arrastrar la torsión y la escala final y Construir: 9 clics y 2
     * arrastres. Ahora los puntos se ponen desde Figuras y cada variación se enciende de un clic con un valor que se
     * ve.
     */
    @Test
    void makeAFigureAndVaryIt() {
        StudioDriver studio = fresh(false, 0);
        assertEquals(StudioStage.FIGURE, studio.view.stage(), "el Studio abre en Figuras");
        for (int index = 0; index < 3; index++) {
            // En Figuras, un clic en el mundo pone un punto, como en Trayectoria.
            assertEquals(StudioView.Click.WORLD, studio.clickAt(100 + index * 40, 300));
            assertEquals(StudioView.WorldAction.POINTS, studio.view.worldAction(studio.host));
            studio.host.points.add(new int[]{index * 8, 64, 0});
        }
        studio.click("Escamas");
        studio.click("Trenza");
        studio.click("Retorcer");
        studio.click("Afinar");
        studio.click("Construir");
        var extensions = studio.view.figure().draft().extensions();
        assertEquals(360, extensions.twistDegrees());
        assertEquals(40, extensions.endScalePercent());
        assertEquals(8, studio.clicks(), "tres puntos, figura (2), dos variaciones y construir");
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

    // ── Las tareas de las herramientas que se unieron ────────────────────────────────────────────────────

    /** Pintar una ladera con el pincel (antes «Pintar terreno»): lo de serie ya es hierba en lo llano y piedra arriba. */
    @Test
    void paintAHillsideWithTheBrush() {
        StudioDriver studio = fresh(false, 0);
        studio.click("Herramientas");
        studio.press("tool:paint");
        studio.click("Al pincel");
        assertEquals("komaedit pincel paint hierba_piedra 8 pendiente 60", studio.host.last());
        assertEquals(3, studio.clicks());
    }

    /** Repintar la superficie de la selección con la paleta natural (antes «Pintar superficie»). */
    @Test
    void repaintTheSelectedSurfaceTheNaturalWay() {
        StudioDriver studio = fresh(true, 0);
        studio.click("Herramientas");
        studio.press("tool:paint");
        studio.press("use:selection");
        studio.click("Natural", "tools:gradient");
        studio.click("Pintar la selección");
        assertEquals("komaedit pintar natural natural -v", studio.host.last());
        assertEquals(5, studio.clicks(), "la superficie natural es un modo de Pintar, sobre la selección");
    }

    /** Pintar la selección por pendiente, como el pincel: antes no se podía (solo el pincel). */
    @Test
    void paintTheSelectionBySlope() {
        StudioDriver studio = fresh(true, 0);
        studio.click("Herramientas");
        studio.press("tool:paint");
        studio.press("use:selection");
        studio.click("Pintar la selección");
        assertEquals("komaedit pintar hierba_piedra pendiente 60 -v", studio.host.last());
        assertEquals(4, studio.clicks());
    }

    /** Nevar la selección (antes «Nieve»): el modo de serie de Retocar. */
    @Test
    void snowOverTheSelection() {
        StudioDriver studio = fresh(true, 0);
        studio.click("Herramientas");
        studio.press("tool:touchup");
        studio.click("Nevar");
        assertEquals("komaedit retocar nieve 2 -v", studio.host.last());
        assertEquals(3, studio.clicks());
    }

    /** Drenar el agua de la selección (antes «Drenar»): un clic más, el del modo. */
    @Test
    void drainTheSelection() {
        StudioDriver studio = fresh(true, 0);
        studio.click("Herramientas");
        studio.press("tool:touchup");
        studio.click("Drenar", "tools:touch");
        studio.click("Drenar");
        assertEquals("komaedit retocar drenar water -v", studio.host.last());
        assertEquals(4, studio.clicks());
        // La siguiente vez (los ajustes se guardan al cerrar el Studio) Retocar se acuerda del modo.
        studio.view.tools().saveSoon(studio.host, true);
        StudioDriver again = new StudioDriver(new StudioView(), studio.host, 960, 540);
        again.render();
        again.click("Drenar");
        assertEquals(1, again.clicks(), "con el modo recordado");
    }

    /** Drenar con el pincel, alrededor de cada clic: antes solo había orden de selección. */
    @Test
    void drainWithTheBrush() {
        StudioDriver studio = fresh(false, 0);
        studio.click("Herramientas");
        studio.press("tool:touchup");
        studio.click("Drenar", "tools:touch");
        studio.press("use:brush");
        studio.click("Al pincel");
        assertEquals("komaedit pincel retocar 8 drenar water", studio.host.last());
        assertEquals(5, studio.clicks());
    }

    /** El pincel de biomas (antes Bioma → Más ajustes → desplazar → Al pincel): un modo de Retocar. */
    @Test
    void changeTheBiomeWithTheBrush() {
        StudioDriver studio = fresh(false, 0);
        studio.click("Herramientas");
        studio.press("tool:touchup");
        studio.click("Cambiar bioma", "tools:touch");
        studio.press("use:brush");
        studio.click("Al pincel");
        assertEquals("komaedit pincel biome plains 8", studio.host.last());
        assertEquals(5, studio.clicks(), "sin desplazar");
    }

    /** Estampar un sello que cubra la selección: antes solo con el pincel. */
    @Test
    void stampTerrainOverTheSelection() {
        StudioDriver studio = fresh(true, 0);
        studio.click("Herramientas");
        studio.press("tool:stamp");
        studio.press("use:selection");
        studio.click("Estampar en la selección");
        assertEquals("komaedit relieve montana sumar auto -v", studio.host.last());
        assertEquals(4, studio.clicks());
    }

    /** Un árbol en cada clic con el pincel: antes «Al pincel» estaba plegado en «Más ajustes». */
    @Test
    void plantTreesWithTheBrush() {
        StudioDriver studio = fresh(false, 0);
        studio.click("Herramientas");
        studio.press("tool:tree");
        studio.press("use:brush");
        studio.click("Al pincel");
        assertEquals("komaedit pincel tree oak", studio.host.last());
        assertEquals(4, studio.clicks());
    }

    /** Erosionar con el pincel: antes solo sobre la selección. */
    @Test
    void erodeWithTheBrush() {
        StudioDriver studio = fresh(false, 0);
        studio.click("Herramientas");
        studio.press("tool:erosion");
        studio.press("use:brush");
        studio.click("Al pincel");
        assertEquals("komaedit pincel zona 10 terreno erosion hidraulica natural", studio.host.last());
        assertEquals(4, studio.clicks());
    }
}
