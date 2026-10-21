package com.komainu.moxia.studio;

import com.komainu.moxia.studio.WelcomeTour.Beat;
import com.komainu.moxia.studio.WelcomeTour.Step;
import com.komainu.moxia.studio.tools.TerrainCards;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.TreeCard;
import com.komainu.moxia.studio.tools.ui.JobStrip;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La máquina de estados de la bienvenida, sin el Studio: cada golpe avanza solo con lo que hace el jugador, se puede
 * saltar, se salta sola sin permiso, en supervivencia solo enseña y se puede volver a lanzar.
 */
class WelcomeTourTest {
    private static final ToolContext ALL = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L, 0, false);

    /** Lo que ve la vista; la prueba lo cambia como lo haría el jugador. */
    private static final class World {
        boolean creative = true;
        ToolContext context = ALL;
        boolean tools;
        String tool = "";
        Integer seed;
        boolean peaks;
        String ghost;
        boolean ghostWaiting;
        boolean previewWaiting;
        boolean historyOpen;
        int history;
        int points;

        WelcomeTour.Situation now() {
            return new WelcomeTour.Situation(creative, context, tools, tool, seed, peaks, ghost, ghostWaiting,
                    previewWaiting, historyOpen, history, points);
        }
    }

    private final WelcomeTour tour = new WelcomeTour();
    private final World world = new World();

    private void see() {
        tour.observe(world.now());
    }

    private void pick(String id) {
        world.tools = true;
        world.tool = id;
        see();
    }

    private void begin() {
        tour.offer();
        see();
        assertEquals(Beat.INTRO, tour.beat());
        tour.start();
        see();
    }

    /** Del árbol plantado al fantasma colocado: deja la bienvenida en el sello. */
    private void plantAndPlace() {
        pick(WelcomeTour.TREE);
        tour.sent("komaedit tree oak -v");
        world.ghost = "120, 64, -40 · giro 0° · 7×12×7";
        see();
        world.ghost = "123, 64, -42 · giro 0° · 7×12×7";
        see();
        tour.clicked(null, WelcomeTour.GHOST_PLACE_ANCHOR);
        world.ghostWaiting = true;
        see();
        world.ghost = null;
        world.ghostWaiting = false;
        see();
    }

    @Test
    void everyBeatMovesOnOnlyWithTheRealAction() {
        begin();
        assertTrue(tour.doing());
        assertEquals(List.of(Step.TREE, Step.PLACE, Step.STAMP, Step.PAINT, Step.UNDO), tour.steps());
        assertEquals(Beat.TREE_PICK, tour.beat());
        assertEquals(1, tour.stepNumber());
        see();
        assertEquals(Beat.TREE_PICK, tour.beat(), "sin elegir nada no avanza");
        pick("roof");
        assertEquals(Beat.TREE_PICK, tour.beat(), "otra herramienta no vale");
        pick(WelcomeTour.TREE);
        assertEquals(Beat.TREE_CLICK, tour.beat());

        world.ghost = "un fantasma de antes";
        see();
        assertEquals(Beat.TREE_CLICK, tour.beat(), "un fantasma que no salió del árbol no cuenta");
        world.ghost = null;
        tour.sent("komaedit tree oak -v");
        see();
        assertEquals(Beat.TREE_CLICK, tour.beat(), "hasta que el fantasma no sale, no se ha plantado");
        world.ghost = "120, 64, -40 · giro 0° · 7×12×7";
        see();
        assertEquals(Beat.GHOST_MOVE, tour.beat());
        assertEquals(2, tour.stepNumber());
        see();
        assertEquals(Beat.GHOST_MOVE, tour.beat(), "sin moverlo no avanza");
        world.ghost = "123, 64, -42 · giro 0° · 7×12×7";
        see();
        assertEquals(Beat.GHOST_PLACE, tour.beat());
        tour.clicked(null, WelcomeTour.GHOST_PLACE_ANCHOR);
        world.ghostWaiting = true;
        see();
        assertEquals(Beat.GHOST_PLACE, tour.beat(), "mientras el servidor lo coloca, se espera");
        world.ghost = null;
        world.ghostWaiting = false;
        see();
        assertEquals(Beat.STAMP_PICK, tour.beat());

        pick(WelcomeTour.STAMP);
        assertEquals(Beat.STAMP_CHOOSE, tour.beat());
        tour.clicked("tools:stamp:page", null);
        assertEquals(Beat.STAMP_CHOOSE, tour.beat(), "pasar de página no es elegir");
        tour.clicked(WelcomeTour.STAMP_GROUP, "stamp:montana");
        assertEquals(Beat.STAMP_BRUSH, tour.beat());
        tour.sent("komaedit pincel actualizar heightmap montana 12 24 sumar auto -p");
        assertEquals(Beat.STAMP_BRUSH, tour.beat(), "poner al día el pincel no es cogerlo");
        tour.sent("komaedit pincel heightmap montana 12 24 sumar auto -p");
        assertEquals(Beat.STAMP_CLICK, tour.beat());
        tour.brushUsed();
        assertEquals(Beat.STAMP_SEED, tour.beat());
        see();
        assertEquals(Beat.STAMP_SEED, tour.beat());
        world.seed = 4217;
        see();
        assertEquals(Beat.STAMP_AGAIN, tour.beat());
        tour.brushUsed();
        assertEquals(Beat.STAMP_APPLY, tour.beat());
        assertTrue(tour.offersApply());
        assertTrue(tour.drain().isEmpty(), "la vista previa no se aplica sola");
        tour.apply();
        assertEquals(List.of(WelcomeTour.APPLY), tour.drain());
        assertEquals(Beat.PAINT_PICK, tour.beat());

        pick(WelcomeTour.PAINT);
        assertEquals(Beat.PAINT_PRESET, tour.beat());
        world.peaks = true;
        see();
        assertEquals(Beat.PAINT_BRUSH, tour.beat());
        tour.sent("komaedit pincel paint cumbres 16 altura 60 -h montana -p");
        assertEquals(Beat.PAINT_CLICK, tour.beat());
        tour.brushUsed();
        assertEquals(Beat.PAINT_APPLY, tour.beat());
        tour.discard();
        assertEquals(List.of(WelcomeTour.DISCARD), tour.drain());
        assertEquals(Beat.PAINT_CLICK, tour.beat(), "descartar pide otro clic");
        assertEquals("gui.komaedit.welcome.notice.discarded", tour.notice().key());
        tour.brushUsed();
        assertNull(tour.notice(), "el aviso se va al hacer el golpe");
        tour.apply();
        assertEquals(List.of(WelcomeTour.APPLY), tour.drain());
        assertEquals(Beat.UNDO_OPEN, tour.beat());

        tour.undone();
        assertEquals(Beat.DONE, tour.beat(), "deshacer con Ctrl+Z también vale");
        assertFalse(tour.running());
        assertTrue(tour.active());
        tour.finish();
        assertFalse(tour.active());
        assertTrue(tour.drain().isEmpty(), "al terminar no queda nada que mandar");
    }

    @Test
    void theHistoryHasToBeOpenToUndoFromIt() {
        begin();
        plantAndPlace();
        tour.skipStep();
        tour.skipStep();
        assertEquals(Beat.UNDO_OPEN, tour.beat());
        world.history = 3;
        world.historyOpen = true;
        see();
        assertEquals(Beat.UNDO_DO, tour.beat());
        assertFalse(tour.offersNext(), "hay cosas que deshacer: hay que deshacerlas");
        world.history = 0;
        see();
        assertTrue(tour.offersNext(), "sin nada que deshacer, se puede seguir");
        assertTrue(tour.textKey().endsWith(".empty"));
        world.historyOpen = false;
        see();
        assertEquals(Beat.UNDO_OPEN, tour.beat(), "si se cierra la lista, se vuelve a abrir");
        world.historyOpen = true;
        world.history = 3;
        see();
        tour.undone();
        assertEquals(Beat.DONE, tour.beat());
    }

    @Test
    void goingToAnotherToolWaitsAndThenResumesWhereItWas() {
        begin();
        plantAndPlace();
        pick(WelcomeTour.STAMP);
        tour.clicked(WelcomeTour.STAMP_GROUP, "stamp:volcan");
        assertEquals(Beat.STAMP_BRUSH, tour.beat());
        world.tool = "";
        see();
        assertEquals(Beat.STAMP_PICK, tour.beat(), "con la cuadrícula abierta, primero que vuelva al sello");
        pick(WelcomeTour.STAMP);
        assertEquals(Beat.STAMP_BRUSH, tour.beat(), "sigue donde iba, sin volver a elegir sello");
    }

    @Test
    void aGhostRemovedWithoutPlacingAsksToPlantAgain() {
        begin();
        pick(WelcomeTour.TREE);
        tour.sent("komaedit tree oak -v");
        world.ghost = "120, 64, -40";
        see();
        assertEquals(Beat.GHOST_MOVE, tour.beat());
        world.ghost = null;
        see();
        assertEquals(Beat.TREE_CLICK, tour.beat());
        assertEquals("gui.komaedit.welcome.notice.cancelled", tour.notice().key());
        world.ghost = "120, 64, -40";
        see();
        assertEquals(Beat.TREE_CLICK, tour.beat(), "hay que volver a plantarlo, no basta con que salga un fantasma");
        tour.sent("komaedit tree oak -v");
        see();
        assertEquals(Beat.GHOST_MOVE, tour.beat());
    }

    @Test
    void aGhostTheServerRejectsIsNotPlaced() {
        begin();
        pick(WelcomeTour.TREE);
        tour.sent("komaedit tree oak -v");
        world.ghost = "88, 300, 15";
        see();
        world.ghost = "88, 310, 15";
        see();
        assertEquals(Beat.GHOST_PLACE, tour.beat());
        tour.clicked(null, WelcomeTour.GHOST_PLACE_ANCHOR);
        world.ghostWaiting = true;
        see();
        // El servidor no lo coloca (se sale del mundo): vuelve a estar quieto, sin confirmar.
        world.ghostWaiting = false;
        see();
        world.ghost = null;
        see();
        assertEquals(Beat.TREE_CLICK, tour.beat(), "cancelarlo después no cuenta como colocado");
    }

    @Test
    void aBlockPreviewWithoutGizmoIsConfirmedFromTheFooter() {
        begin();
        pick(WelcomeTour.TREE);
        tour.sent("komaedit tree oak -v");
        world.previewWaiting = true;
        see();
        assertEquals(Beat.GHOST_PLACE, tour.beat(), "la vista previa de bloques no se mueve: se confirma");
        assertTrue(tour.textKey().endsWith(".preview"));
        assertEquals(List.of(WelcomeTour.GHOST_PLACE_ANCHOR, "job:confirm"), tour.anchors());
        tour.sent(JobStrip.CONFIRM);
        world.previewWaiting = false;
        see();
        assertEquals(Beat.STAMP_PICK, tour.beat());

        WelcomeTour discarded = new WelcomeTour();
        discarded.offer();
        discarded.observe(world.now());
        discarded.start();
        world.tools = true;
        world.tool = WelcomeTour.TREE;
        discarded.observe(world.now());
        discarded.sent("komaedit tree oak -v");
        world.previewWaiting = true;
        discarded.observe(world.now());
        discarded.sent(JobStrip.CANCEL);
        world.previewWaiting = false;
        discarded.observe(world.now());
        assertEquals(Beat.TREE_CLICK, discarded.beat(), "descartada, a plantar otra vez");
    }

    @Test
    void withPathPointsTheTreeGoesOnThemFromTheFooter() {
        world.points = 3;
        begin();
        pick(WelcomeTour.TREE);
        assertEquals(Beat.TREE_CLICK, tour.beat());
        assertEquals(List.of("footer:build"), tour.anchors());
        assertTrue(tour.textKey().endsWith(".path"));
    }

    @Test
    void skippingAStepGoesToTheNextOneAndSkippingAllDiscardsWhatWasLeft() {
        begin();
        tour.skipStep();
        assertEquals(Beat.STAMP_PICK, tour.beat(), "sin árbol no hay nada que colocar: se saltan los dos");
        pick(WelcomeTour.STAMP);
        tour.clicked(WelcomeTour.STAMP_GROUP, null);
        tour.sent("komaedit pincel heightmap montana 12 24 sumar auto -p");
        tour.brushUsed();
        assertEquals(Beat.STAMP_SEED, tour.beat());
        tour.skipStep();
        assertEquals(List.of(WelcomeTour.DISCARD), tour.drain(), "la montaña a medias se descarta");
        assertEquals(Beat.PAINT_PICK, tour.beat());
        pick(WelcomeTour.PAINT);
        world.peaks = true;
        see();
        tour.sent("komaedit pincel paint cumbres 16 altura 60 -p");
        tour.brushUsed();
        assertEquals(Beat.PAINT_APPLY, tour.beat());
        tour.finish();
        assertFalse(tour.active());
        assertEquals(List.of(WelcomeTour.DISCARD), tour.drain(), "saltar la bienvenida no deja nada a medias");
    }

    @Test
    void aStepWithoutPermissionSkipsItselfAndSaysSo() {
        Set<String> granted = new HashSet<>(ToolCatalog.permissionNodes());
        granted.remove(TerrainCards.STAMP_NODE);
        world.context = new ToolContext(true, granted, false, 0L, 0, false);
        begin();
        assertTrue(tour.doing(), "con permiso para el árbol se hace de verdad");
        plantAndPlace();
        assertEquals(Beat.PAINT_PICK, tour.beat(), "el sello se salta");
        assertEquals("gui.komaedit.welcome.notice.skipped", tour.notice().key());
        assertEquals(List.of("gui.komaedit.tools.stamp"), tour.notice().names());
        assertEquals(List.of(Step.TREE, Step.PLACE, Step.PAINT, Step.UNDO), tour.steps());
        assertEquals(3, tour.stepNumber());

        granted.add(TerrainCards.STAMP_NODE);
        granted.remove(TreeCard.NODE);
        world.context = new ToolContext(true, granted, false, 0L, 0, false);
        WelcomeTour noTree = new WelcomeTour();
        noTree.offer();
        noTree.observe(world.now());
        noTree.start();
        assertEquals(Beat.STAMP_PICK, noTree.beat(), "sin árbol se saltan plantar y colocar");
        assertEquals(List.of("gui.komaedit.tools.tree"), noTree.notice().names(), "una sola vez");
        assertEquals(1, noTree.stepNumber());
    }

    @Test
    void inSurvivalOrWithoutAnyBuildingPermissionItOnlyShows() {
        world.creative = false;
        tour.offer();
        see();
        assertTrue(tour.wouldShow());
        assertTrue(tour.textKey().endsWith(".show"), "la entrada ya lo dice");
        tour.start();
        see();
        assertTrue(tour.showing());
        assertFalse(tour.doing());
        assertEquals(List.of(Step.TREE, Step.STAMP, Step.PAINT, Step.UNDO), tour.steps());
        pick(WelcomeTour.TREE);
        assertEquals(Beat.TREE_LOOK, tour.beat(), "elegir sí se hace: no toca el mundo");
        tour.sent("komaedit tree oak -v");
        world.ghost = "120, 64, -40";
        see();
        assertEquals(Beat.TREE_LOOK, tour.beat(), "plantar no forma parte: solo se mira");
        world.ghost = null;
        assertTrue(tour.offersNext());
        tour.next();
        pick(WelcomeTour.STAMP);
        tour.clicked(WelcomeTour.STAMP_GROUP, null);
        assertEquals(Beat.STAMP_SEED, tour.beat(), "ni coger el pincel ni estampar");
        world.seed = 12;
        see();
        assertEquals(Beat.STAMP_LOOK, tour.beat());
        tour.brushUsed();
        tour.apply();
        assertEquals(Beat.STAMP_LOOK, tour.beat(), "aquí no hay nada que aplicar");
        tour.next();
        pick(WelcomeTour.PAINT);
        world.peaks = true;
        see();
        assertEquals(Beat.PAINT_LOOK, tour.beat());
        tour.next();
        world.historyOpen = true;
        see();
        assertEquals(Beat.UNDO_LOOK, tour.beat());
        tour.next();
        assertEquals(Beat.DONE, tour.beat());
        assertTrue(tour.textKey().endsWith(".show"));
        assertTrue(tour.drain().isEmpty(), "en supervivencia la bienvenida no manda nada");

        WelcomeTour noPermissions = new WelcomeTour();
        world.creative = true;
        world.context = new ToolContext(true, Set.of(), false, 0L, 0, false);
        noPermissions.offer();
        noPermissions.observe(world.now());
        noPermissions.start();
        assertTrue(noPermissions.showing(), "sin ningún permiso de construir, también solo enseña");
        assertEquals(Beat.TREE_PICK, noPermissions.beat(), "y no se salta ningún paso: los enseña todos");

        world.context = ToolContext.UNKNOWN;
        WelcomeTour unknown = new WelcomeTour();
        unknown.offer();
        unknown.observe(world.now());
        assertFalse(unknown.wouldShow(), "con un servidor que no contesta, se hace de verdad (decide el servidor)");
    }

    @Test
    void itCanBeRelaunchedFromTheStartAtAnyMoment() {
        begin();
        tour.finish();
        assertFalse(tour.active());
        tour.offer();
        assertEquals(Beat.INTRO, tour.beat(), "desde la ayuda o la orden vuelve a la entrada");
        tour.start();
        assertEquals(Beat.TREE_PICK, tour.beat());
        tour.skipStep();
        pick(WelcomeTour.STAMP);
        tour.clicked(WelcomeTour.STAMP_GROUP, null);
        tour.sent("komaedit pincel heightmap montana 12 24 sumar auto -p");
        tour.brushUsed();
        tour.offer();
        assertEquals(List.of(WelcomeTour.DISCARD), tour.drain(), "relanzarla a medias descarta la vista previa");
        assertEquals(Beat.INTRO, tour.beat());
        assertEquals(0, tour.stepNumber());
        assertTrue(tour.takeStarted());
        assertFalse(tour.takeStarted(), "cada golpe se prepara una sola vez");
    }
}
