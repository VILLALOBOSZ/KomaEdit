package com.komainu.moxia.placement;

import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.gizmo.GizmoLayout;
import com.komainu.moxia.shape.Vec3;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * El visualizador de la alineación de los pinceles de colocación, como el de ezEdits: en el bloque que se mira, hacia
 * dónde quedará el +y de la estructura (verde, la primaria), su +x (rojo, sale de la secundaria) y su +z (azul). Hace
 * el mismo cálculo que la colocación ({@link AlignmentFrame} con el {@code -j} y el {@code -x} del pincel), así que lo
 * que se ve es lo que sale. Si la secundaria pedida no es perpendicular a la primaria, se ve además tenue y a trazos:
 * el +x es la perpendicular más parecida a ella.
 *
 * <p>Lógica pura: el servidor la calcula con el pincel en la mano y la dibuja el cliente con KomaEdit (o, en los
 * clientes sin el mod, unos block displays que solo ve quien tiene el pincel).</p>
 */
public final class AlignmentGuide {
    /** La secundaria pedida, tenue, cuando no coincide con el +x final. */
    public static final int HINT = 0xCC000000 | GizmoGeometry.RED & 0xFFFFFF;
    /** El centro cuando la orientación no vale (primaria nula o en la misma recta que la secundaria). */
    public static final int INVALID_MARK = GizmoGeometry.HIGHLIGHT;
    /** Parte de la altura de la pantalla que ocupa la flecha verde. */
    public static final double SCREEN_FRACTION = 0.14;
    /** Por debajo de este ángulo entre la secundaria pedida y el +x final no se dibuja la pedida. */
    private static final double HINT_MIN_DEGREES = 3.0;
    private static final float WIDTH = 3.0F;
    private static final float THIN = 2.0F;
    private static final float DASH = 2.5F;
    /** La flecha azul es más corta: es la que menos importa (sale sola de las otras dos). */
    private static final double Z_LENGTH = 0.6;
    private static final Vec3 ZERO = new Vec3(0, 0, 0);
    private static final Vec3 UP = Alignment.UP;

    private AlignmentGuide() {
    }

    public enum State {
        /** Se conocen las dos direcciones: se ve la orientación completa. */
        READY,
        /** {@code ViewDiff} sin el clic derecho que marca el sitio: solo se ve lo que ya se sabe. */
        WAITING,
        /** La primaria es nula o cae en la misma recta que la secundaria (sin {@code -x}): la colocación fallaría. */
        INVALID
    }

    /**
     * Lo que hay en el sitio de la colocación de ejemplo: lo mismo que recibe la alineación al colocar.
     *
     * @param origin     el centro del bloque donde caería el origen de la estructura
     * @param normal     la normal de la superficie (o la de la cara mirada)
     * @param viewDiff   la dirección del segundo clic de {@code ViewDiff}, o {@code null} si aún no la hay
     * @param tangent    en {@code array}, la dirección del tramo; {@code null} en los demás
     * @param pathNormal en {@code array}, la perpendicular al tramo que hace de «arriba»
     */
    public record Probe(Vec3 origin, Vec3 eye, Vec3 aim, Vec3 normal, Vec3 viewDiff, Vec3 tangent,
                        Vec3 pathNormal) {
    }

    /**
     * @param x         el +x de la estructura en el mundo (solo en {@link State#READY}; si no, {@code null})
     * @param y         su +y: la primaria, con el ajuste {@code -j}
     * @param z         su +z
     * @param primary   la primaria pedida, normalizada, o {@code null} si aún no se sabe
     * @param secondary la secundaria pedida (antes de hacerla perpendicular), normalizada, o {@code null}
     * @param random    si alguna alineación es al azar: cada colocación sale distinta y lo dibujado es un ejemplo
     */
    public record Result(State state, Vec3 origin, Vec3 x, Vec3 y, Vec3 z, Vec3 primary, Vec3 secondary,
                         boolean random) {
    }

    /** Un texto junto a una flecha; {@code key} es el final de la clave de idioma {@code gui.komaedit.guide.*}. */
    public record Label(String key, Vec3 at, int argb) {
    }

    /**
     * La alineación en el sitio de {@code probe}, con la semilla {@code seed} para lo que vaya al azar (siempre la
     * misma: así un {@code Random} enseña un ejemplo quieto en vez de saltar).
     */
    public static Result compute(PlacementSettings settings, Probe probe, long seed) {
        Alignment.Context context = new Alignment.Context(probe.origin(), probe.eye(),
                new DirectionExpr.Context(probe.aim()), probe.normal(), probe.tangent(), probe.pathNormal(), ZERO,
                ZERO, new SplittableRandom(seed), probe.viewDiff());
        Vec3 primary = direction(settings.primary(), context);
        Vec3 secondary = direction(settings.secondary(), context);
        boolean random = random(settings.primary()) || random(settings.secondary());
        if (primary == null || secondary == null) {
            return new Result(State.WAITING, probe.origin(), null, null, null, primary, secondary, random);
        }
        try {
            Mat3 frame = AlignmentFrame.frame(primary, secondary, settings.snap(), settings.perturb());
            return new Result(State.READY, probe.origin(), frame.column(0), frame.column(1), frame.column(2),
                    primary, secondary, random);
        } catch (IllegalArgumentException exception) {
            return new Result(State.INVALID, probe.origin(), null, null, null, primary, secondary, random);
        }
    }

    /** La dirección de una alineación, normalizada; {@code null} si aún le falta un clic o el trazo. */
    private static Vec3 direction(Alignment alignment, Alignment.Context context) {
        try {
            Vec3 direction = alignment.direction(context);
            return direction.length() < 1.0E-9 ? ZERO : direction.normalized();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static boolean random(Alignment alignment) {
        return alignment instanceof Alignment.Random || alignment instanceof Alignment.Mixed mixed
                && mixed.entries().stream().anyMatch(entry -> random(entry.alignment()));
    }

    /**
     * El sitio de la colocación de ejemplo de un pincel, como lo calcula {@link PlacementService} al hacer clic.
     *
     * @param surface el bloque que se mira
     * @param face    la normal de la cara que se mira
     * @param pending en {@code ViewDiff}, la colocación marcada con el clic derecho que espera su dirección
     * @param stroke  en {@code array}, los puntos del trazo marcados hasta ahora
     * @param air     para la normal de la superficie; {@code null} usa la de la cara
     */
    public static Probe probe(PlacementSettings settings, BlockVector3 surface, Vec3 face, Vec3 eye, Vec3 aim,
                              PlacementService.BrushTarget pending, List<BlockVector3> stroke,
                              ScatterPlanner.AirTest air) {
        switch (settings.kind()) {
            case ARRAY -> {
                // Los puntos del trazo son los bloques mirados y las estructuras van encima, en el aire. El ejemplo
                // va en el tramo que añadiría el siguiente punto.
                Vec3 origin = center(surface.add(0, 1, 0));
                Vec3 tangent = tangent(surface, stroke, aim);
                Vec3 pathNormal = UP.subtract(tangent.multiply(UP.dot(tangent)));
                if (pathNormal.length() < 1.0E-6) {
                    pathNormal = new DirectionExpr.Context(aim).forward();
                }
                return new Probe(origin, eye, aim, UP, null, tangent, pathNormal.normalized());
            }
            case SCATTER -> {
                // La dispersión siempre usa la normal de la superficie de cada punto.
                Vec3 normal = air == null ? face : ScatterPlanner.normal(air, surface.x(), surface.y(), surface.z());
                return new Probe(center(surface.add(step(face))), eye, aim, normal, null, null, null);
            }
            default -> {
                if (settings.needsViewDiff() && pending != null) {
                    // Ya hay clic derecho: el ejemplo va allí y el sitio mirado hace de segundo clic.
                    Vec3 direction = new Vec3(surface.x() - pending.surface().x(),
                            surface.y() - pending.surface().y(), surface.z() - pending.surface().z());
                    Vec3 normal = settings.needsNormal() && air != null
                            ? ScatterPlanner.normal(air, pending.surface().x(), pending.surface().y(),
                            pending.surface().z())
                            : pending.faceNormal();
                    return new Probe(center(pending.anchor()), eye, aim, normal,
                            direction.length() < 1.0E-6 ? null : direction.normalized(), null, null);
                }
                Vec3 normal = settings.needsNormal() && air != null
                        ? ScatterPlanner.normal(air, surface.x(), surface.y(), surface.z())
                        : face;
                return new Probe(center(surface.add(step(face))), eye, aim, normal, null, null, null);
            }
        }
    }

    private static Vec3 tangent(BlockVector3 surface, List<BlockVector3> stroke, Vec3 aim) {
        if (!stroke.isEmpty()) {
            BlockVector3 last = stroke.getLast();
            Vec3 next = new Vec3(surface.x() - last.x(), surface.y() - last.y(), surface.z() - last.z());
            if (next.length() > 1.0E-6) {
                return next.normalized();
            }
            if (stroke.size() > 1) {
                BlockVector3 previous = stroke.get(stroke.size() - 2);
                Vec3 before = new Vec3(last.x() - previous.x(), last.y() - previous.y(), last.z() - previous.z());
                if (before.length() > 1.0E-6) {
                    return before.normalized();
                }
            }
        }
        return new DirectionExpr.Context(aim).forward();
    }

    private static BlockVector3 step(Vec3 face) {
        return BlockVector3.at(Math.round(face.x()), Math.round(face.y()), Math.round(face.z()));
    }

    private static Vec3 center(BlockVector3 block) {
        return new Vec3(block.x() + 0.5, block.y() + 0.5, block.z() + 0.5);
    }

    // ---------------------------------------------------------------- dibujo

    /** Las flechas, con la verde de {@code length} bloques. Lo dibujan igual el cliente y las pruebas. */
    public static GizmoGeometry.Shape shape(Result result, double length) {
        List<GizmoGeometry.Line> lines = new ArrayList<>();
        List<GizmoGeometry.Triangle> triangles = new ArrayList<>();
        Vec3 origin = result.origin();
        if (result.state() == State.READY) {
            if (requested(result)) {
                dashed(lines, origin, result.secondary(), length, HINT);
            }
            arrow(lines, triangles, origin, result.z(), length * Z_LENGTH, GizmoGeometry.BLUE, THIN);
            arrow(lines, triangles, origin, result.x(), length, GizmoGeometry.RED, WIDTH);
            arrow(lines, triangles, origin, result.y(), length, GizmoGeometry.GREEN, WIDTH);
        } else {
            if (result.secondary() != null && result.secondary().length() > 0.5) {
                arrow(lines, triangles, origin, result.secondary(), length, HINT, THIN);
            }
            if (result.primary() != null && result.primary().length() > 0.5) {
                arrow(lines, triangles, origin, result.primary(), length, GizmoGeometry.GREEN, WIDTH);
            }
        }
        int center = result.state() == State.INVALID ? INVALID_MARK : GizmoGeometry.CENTER;
        GizmoGeometry.cube(triangles, origin, GizmoLayout.CUBE * length, center);
        return new GizmoGeometry.Shape(List.copyOf(lines), List.copyOf(triangles));
    }

    /**
     * Los nombres de las flechas, un poco más allá de cada punta, y lo que falta o falla bajo el centro. Las claves:
     * {@code primary}, {@code secondary}, {@code requested} (la secundaria pedida, a trazos), {@code waiting},
     * {@code invalid} y {@code random}.
     */
    public static List<Label> labels(Result result, double length) {
        List<Label> labels = new ArrayList<>();
        Vec3 origin = result.origin();
        Vec3 primary = result.state() == State.READY ? result.y() : result.primary();
        Vec3 secondary = result.state() == State.READY ? result.x() : result.secondary();
        if (primary != null && primary.length() > 0.5) {
            labels.add(new Label("primary", origin.add(primary.normalized().multiply(length * 1.18)),
                    GizmoGeometry.GREEN));
        }
        if (secondary != null && secondary.length() > 0.5) {
            // Si va por la misma recta que la primaria, su nombre más allá: si no, se pisarían.
            boolean sameLine = primary != null && primary.length() > 0.5
                    && angle(primary, secondary) < HINT_MIN_DEGREES * 3;
            labels.add(new Label("secondary", origin.add(secondary.normalized()
                    .multiply(length * (sameLine ? 1.5 : 1.18))), GizmoGeometry.RED));
        }
        if (result.state() == State.READY && requested(result)) {
            labels.add(new Label("requested", origin.add(result.secondary().normalized().multiply(length * 1.12)),
                    HINT));
        }
        Vec3 below = origin.subtract(new Vec3(0, length * 0.32, 0));
        switch (result.state()) {
            case WAITING -> labels.add(new Label("waiting", below, GizmoGeometry.HIGHLIGHT));
            case INVALID -> labels.add(new Label("invalid", below, GizmoGeometry.HIGHLIGHT));
            default -> {
                if (result.random()) {
                    labels.add(new Label("random", below, GizmoGeometry.CENTER));
                }
            }
        }
        return labels;
    }

    /** Si la secundaria pedida se aparta del +x final lo bastante como para dibujarla aparte. */
    private static boolean requested(Result result) {
        Vec3 hint = result.secondary();
        return hint != null && result.x() != null && hint.length() > 0.5
                && angle(hint, result.x()) > HINT_MIN_DEGREES;
    }

    /** El ángulo entre dos direcciones, en grados. */
    static double angle(Vec3 first, Vec3 second) {
        double cosine = first.normalized().dot(second.normalized());
        return Math.toDegrees(Math.acos(Math.clamp(cosine, -1.0, 1.0)));
    }

    private static void arrow(List<GizmoGeometry.Line> lines, List<GizmoGeometry.Triangle> triangles, Vec3 origin,
                              Vec3 direction, double length, int color, float width) {
        Vec3 unit = direction.normalized();
        Vec3[] basis = GizmoGeometry.perpendiculars(unit);
        GizmoGeometry.arrow(lines, triangles, origin.add(unit.multiply(GizmoLayout.ARROW_START * length)),
                origin.add(unit.multiply(GizmoLayout.TIP_START * length)), origin.add(unit.multiply(length)),
                GizmoLayout.TIP_RADIUS * length, basis[0], basis[1], color, width);
    }

    /** Una línea a trazos, para lo que no es la orientación final sino lo pedido. */
    private static void dashed(List<GizmoGeometry.Line> lines, Vec3 origin, Vec3 direction, double length,
                               int color) {
        Vec3 unit = direction.normalized();
        int dashes = 7;
        double step = length / dashes;
        for (int dash = 0; dash < dashes; dash++) {
            double from = GizmoLayout.ARROW_START * length + dash * step;
            double to = Math.min(length, from + step * 0.55);
            if (from >= length) {
                break;
            }
            lines.add(new GizmoGeometry.Line(origin.add(unit.multiply(from)), origin.add(unit.multiply(to)), color,
                    DASH));
        }
    }
}
