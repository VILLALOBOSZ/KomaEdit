package com.komainu.moxia.gizmo;

import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostSessionTest {
    private static final BlockBounds LOCAL = new BlockBounds(-2, -3, -1, 2, 3, 1);
    private static final GhostSession.Rules ALL = orientation -> true;
    private static final GhostSession.Rules UPRIGHT = Orientation::upright;
    private static final GhostSession.Ground NO_GROUND = transform -> OptionalInt.empty();

    private static GhostSession session() {
        return new GhostSession(new GhostSession.Info(9, "Árbol", LOCAL, 120, 4, GhostTransform.at(10, 70, -5),
                GhostOptions.DEFAULT));
    }

    @Test
    void movesRotatesAndConfirmsOnlyTheTransform() {
        GhostSession session = session();
        assertTrue(session.active());
        assertTrue(session.nudge(3, 0, -1, NO_GROUND));
        assertTrue(session.rotate(Orientation.Y, 1, ALL, NO_GROUND));
        assertArrayEquals(new int[]{3, 0, -1}, session.offset());
        // Girado, el ancho X (5) y el largo Z (3) se cambian.
        assertArrayEquals(new int[]{3, 7, 5}, session.size());
        Optional<GhostSession.Confirm> confirm = session.confirm(false);
        assertTrue(confirm.isPresent());
        assertEquals(9, confirm.get().id());
        assertEquals(new GhostTransform(Orientation.turn(Orientation.Y, 1), 13, 70, -6), confirm.get().transform());
        assertFalse(confirm.get().copy());
        assertEquals(GhostSession.Phase.PLACING, session.phase());
        // Esperando al servidor no se mueve ni se confirma dos veces.
        assertFalse(session.nudge(1, 0, 0, NO_GROUND));
        assertTrue(session.confirm(false).isEmpty());
        session.placed(false);
        assertEquals(GhostSession.Phase.CLOSED, session.phase());
        assertFalse(session.open());
    }

    @Test
    void aRecalculatedObjectKeepsWhereThePlayerLeftIt() {
        GhostSession before = session();
        before.nudge(4, 1, -2, NO_GROUND);
        before.rotate(Orientation.Y, 1, ALL, NO_GROUND);
        before.toggleKeepExisting();
        // El árbol más alto: el servidor lo calcula en el mismo sitio y su pivote (el centro de la caja) sube.
        GhostSession after = new GhostSession(new GhostSession.Info(10, "Árbol", new BlockBounds(-2, -5, -1, 2, 5, 1),
                160, 4, GhostTransform.at(10, 72, -5), GhostOptions.DEFAULT));
        after.follow(before, ALL);
        assertArrayEquals(new int[]{4, 1, -2}, after.offset(), "movido lo mismo que el de antes");
        assertEquals(new GhostTransform(Orientation.turn(Orientation.Y, 1), 14, 73, -7), after.transform());
        assertTrue(after.options().keepExisting());
        // Un giro que el objeto nuevo no admite (unas escaleras tumbadas) se quita; lo movido se queda.
        GhostSession tipped = session();
        tipped.rotate(Orientation.X, 1, ALL, NO_GROUND);
        GhostSession stairs = session();
        stairs.follow(tipped, UPRIGHT);
        assertEquals(Orientation.IDENTITY, stairs.transform().orientation());
    }

    @Test
    void aRejectionLetsItMoveAgainWithTheReason() {
        GhostSession session = session();
        session.confirm(false);
        session.rejected("Zona protegida");
        assertEquals(GhostSession.Phase.MOVING, session.phase());
        assertEquals("Zona protegida", session.message());
        assertTrue(session.nudge(0, 1, 0, NO_GROUND));
        assertEquals("", session.message());
    }

    @Test
    void copiesKeepTheGhostAndOnlyOneIsOnTheWay() {
        GhostSession session = session();
        Optional<GhostSession.Confirm> first = session.confirm(true);
        assertTrue(first.isPresent() && first.get().copy());
        assertEquals(GhostSession.Phase.MOVING, session.phase());
        assertTrue(session.copyPending());
        assertTrue(session.confirm(true).isEmpty(), "una copia cada vez");
        assertTrue(session.nudge(5, 0, 0, NO_GROUND), "se sigue moviendo mientras se coloca");
        session.placed(true);
        assertEquals(1, session.copies());
        Optional<GhostSession.Confirm> second = session.confirm(true);
        assertEquals(15, second.orElseThrow().transform().x());
        session.rejected("Ya se está colocando");
        assertFalse(session.copyPending());
        assertTrue(session.active());
    }

    @Test
    void cancelClosesItOnce() {
        GhostSession session = session();
        assertTrue(session.cancel());
        assertFalse(session.cancel());
        assertTrue(session.confirm(false).isEmpty());
        assertFalse(session.nudge(1, 0, 0, NO_GROUND));
    }

    @Test
    void draggingAnArrowMovesBlockByBlockAlongItsAxis() {
        GhostSession session = session();
        GizmoLayout layout = new GizmoLayout(new Vec3(10.5, 70.5, -4.5), 4.0, false, true, false);
        Vec3 eye = new Vec3(14, 80, 6);
        assertTrue(session.beginDrag(GizmoHandle.MOVE_X, toward(eye, new Vec3(12.5, 70.5, -4.5)), layout));
        assertEquals(GizmoHandle.MOVE_X, session.dragged());
        assertEquals(GhostSession.Phase.DRAGGING, session.phase());
        // Lo que no sea la flecha no mueve durante el arrastre.
        assertFalse(session.nudge(1, 0, 0, NO_GROUND));
        assertTrue(session.drag(toward(eye, new Vec3(15.9, 70.5, -4.5)), ALL, NO_GROUND));
        assertEquals(13, session.transform().x(), "3,4 bloques → 3");
        assertFalse(session.drag(toward(eye, new Vec3(15.95, 70.5, -4.5)), ALL, NO_GROUND));
        assertTrue(session.drag(toward(eye, new Vec3(16.6, 70.5, -4.5)), ALL, NO_GROUND));
        assertEquals(14, session.transform().x());
        assertEquals(70, session.transform().y());
        session.endDrag(ALL, NO_GROUND);
        assertEquals(GhostSession.Phase.MOVING, session.phase());
        assertEquals(GizmoHandle.NONE, session.dragged());
    }

    @Test
    void draggingTheCubeSlidesItOverTheGround() {
        GhostSession session = session();
        GizmoLayout layout = new GizmoLayout(new Vec3(10.5, 70.5, -4.5), 4.0, false, true, false);
        Vec3 eye = new Vec3(10, 85, 6);
        assertTrue(session.beginDrag(GizmoHandle.MOVE_PLANE, toward(eye, new Vec3(10.5, 70.5, -4.5)), layout));
        session.drag(toward(eye, new Vec3(13.6, 70.5, -2.2)), ALL, NO_GROUND);
        assertEquals(new GhostTransform(Orientation.IDENTITY, 13, 70, -3), session.transform());
    }

    @Test
    void draggingARingTurnsByQuartersAndAClickTurnsOnce() {
        GhostSession session = session();
        Vec3 center = new Vec3(10.5, 70.5, -4.5);
        GizmoLayout layout = new GizmoLayout(center, 4.0, true, true, true);
        Vec3 eye = new Vec3(10.5, 90, -4.5);
        double radius = GizmoLayout.RING_RADIUS * 4.0;
        // Desde el este hacia el sur (visto desde arriba), poco a poco.
        assertTrue(session.beginDrag(GizmoHandle.RING_Y, toward(eye, center.add(new Vec3(radius, 0, 0))), layout));
        for (int degrees = 10; degrees <= 100; degrees += 10) {
            double angle = Math.toRadians(degrees);
            session.drag(toward(eye, center.add(new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius))),
                    ALL, NO_GROUND);
        }
        assertEquals(Orientation.turn(Orientation.Y, 1), session.transform().orientation());
        session.endDrag(ALL, NO_GROUND);
        assertEquals(Orientation.turn(Orientation.Y, 1), session.transform().orientation(), "arrastrado: sin giro extra");
        // Un clic sin arrastrar en un anillo gira un cuarto de vuelta.
        assertTrue(session.beginDrag(GizmoHandle.RING_Y, toward(eye, center.add(new Vec3(radius, 0, 0))), layout));
        session.endDrag(ALL, NO_GROUND);
        assertEquals(Orientation.turn(Orientation.Y, 2), session.transform().orientation());
    }

    @Test
    void doesNotTipWhatCannotBeTipped() {
        GhostSession session = session();
        assertFalse(session.rotate(Orientation.X, 1, UPRIGHT, NO_GROUND));
        assertEquals(Orientation.IDENTITY, session.transform().orientation());
        assertTrue(session.rotate(Orientation.X, 2, UPRIGHT, NO_GROUND), "boca abajo sí");
        assertTrue(session.flip(Orientation.Z, UPRIGHT, NO_GROUND));
        assertTrue(session.transform().orientation().mirrored());
    }

    @Test
    void groundSnappingFollowsHorizontalMovesAndAVerticalMoveTurnsItOff() {
        GhostSession session = session();
        // El suelo está 4 bloques por debajo de donde se calculó, y 2 más abajo al este de x = 12.
        GhostSession.Ground ground = transform -> OptionalInt.of(transform.x() > 12 ? -6 : -4);
        session.toggleGround(ground);
        assertTrue(session.options().ground());
        assertEquals(66, session.transform().y());
        session.nudge(5, 0, 0, transform -> OptionalInt.of(transform.y() == 66 ? -2 : 0));
        assertEquals(64, session.transform().y());
        session.nudge(0, 3, 0, ground);
        assertFalse(session.options().ground());
        assertEquals(67, session.transform().y());
        GhostOptions options = session.toggleKeepExisting();
        assertTrue(options.keepExisting());
        assertFalse(session.togglePasteAir().pasteAir());
    }

    @Test
    void placesAgainstTheFaceThatIsLookedAt() {
        // Encima de un bloque: la base justo encima, centrada.
        GhostTransform top = GhostSession.against(GhostTransform.at(0, 0, 0), LOCAL, 50, 63, 20, 3);
        BlockBounds bounds = top.bounds(LOCAL);
        assertEquals(64, bounds.minimumY());
        assertEquals(50, Math.floorDiv(bounds.minimumX() + bounds.maximumX(), 2));
        assertEquals(20, Math.floorDiv(bounds.minimumZ() + bounds.maximumZ(), 2));
        // Contra la cara este de una pared: pegado a ella, con la base a la altura del bloque mirado.
        GhostTransform side = GhostSession.against(GhostTransform.at(0, 0, 0), LOCAL, 50, 70, 20, 1);
        assertEquals(51, side.bounds(LOCAL).minimumX());
        assertEquals(70, side.bounds(LOCAL).minimumY());
        // Debajo de un techo: colgando.
        GhostTransform below = GhostSession.against(GhostTransform.at(0, 0, 0), LOCAL, 50, 90, 20, 2);
        assertEquals(89, below.bounds(LOCAL).maximumY());
        GhostSession session = session();
        assertTrue(session.placeAgainst(50, 63, 20, 3, NO_GROUND));
        assertEquals(top, session.transform());
    }

    private static GizmoMath.Ray toward(Vec3 eye, Vec3 target) {
        return new GizmoMath.Ray(eye, target.subtract(eye));
    }
}
