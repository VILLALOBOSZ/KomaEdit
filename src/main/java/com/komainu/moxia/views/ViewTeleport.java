package com.komainu.moxia.views;

/**
 * Lo que comprueba el servidor antes de llevar a alguien a una vista (o a una nota). El cliente propone dónde ir;
 * el servidor decide. Java puro: recibe el jugador, el mundo y las reglas ya leídos.
 *
 * <ul>
 *     <li>Permiso de teleporte ({@code komaedit.view.teleport}) y, para otra dimensión, {@code komaedit.view.dimension}.</li>
 *     <li>Solo en creativo o espectador si así lo dicen las reglas (salvo {@code komaedit.view.anymode}).</li>
 *     <li>Números de verdad, dentro del borde del mundo y de su altura (con margen por arriba para vistas aéreas).</li>
 *     <li>Distancia máxima, si las reglas ponen una, y una espera mínima entre saltos.</li>
 * </ul>
 */
public final class ViewTeleport {
    /** Bloques por encima del techo del mundo que se admiten (una vista desde el cielo). */
    public static final int SKY_MARGIN = 256;
    /** Bloques por debajo del suelo del mundo (una vista desde abajo en una cueva abierta). */
    public static final int FLOOR_MARGIN = 16;

    private ViewTeleport() {
    }

    /** Adónde quiere ir. */
    public record Target(String dimension, double x, double y, double z, float yaw, float pitch) {
        public static Target of(SavedView view) {
            return new Target(view.dimension(), view.x(), view.y(), view.z(), view.yaw(), view.pitch());
        }
    }

    /**
     * El jugador.
     *
     * @param builderMode     está en creativo o espectador
     * @param canTeleport     tiene {@code komaedit.view.teleport}
     * @param canChangeWorld  tiene {@code komaedit.view.dimension}
     * @param anyMode         tiene {@code komaedit.view.anymode} (salta en supervivencia)
     */
    public record Traveler(String dimension, double x, double y, double z, boolean builderMode, boolean canTeleport,
                           boolean canChangeWorld, boolean anyMode) {
    }

    /** El mundo de destino, ya buscado. {@code exists} a falso si la dimensión no existe en el servidor. */
    public record Destination(boolean exists, int bottomY, int topY, Border border) {
    }

    @FunctionalInterface
    public interface Border {
        boolean contains(double x, double z);
    }

    /**
     * Reglas del servidor ({@code config/komaedit/utilities.json}).
     *
     * @param maxDistance  bloques como mucho desde donde está (0, sin límite); no cuenta para otra dimensión
     * @param cooldownMillis espera entre dos saltos
     */
    public record Rules(boolean enabled, boolean creativeOnly, double maxDistance, long cooldownMillis) {
        public static final Rules DEFAULT = new Rules(true, true, 0.0, 400L);

        public Rules {
            if (maxDistance < 0 || !Double.isFinite(maxDistance) || cooldownMillis < 0) {
                throw new IllegalArgumentException("Reglas de teleporte no válidas");
            }
        }
    }

    /** Puede ir ({@code reason} vacío) o no, y por qué. */
    public record Verdict(boolean allowed, String reason, Target target) {
        static Verdict deny(String reason) {
            return new Verdict(false, reason, null);
        }
    }

    /**
     * @param lastJump cuándo saltó la última vez (milisegundos; {@code Long.MIN_VALUE} si nunca)
     */
    public static Verdict check(Target target, Traveler traveler, Destination destination, Rules rules, long now,
                                long lastJump) {
        if (!rules.enabled()) {
            return Verdict.deny("En este servidor no se puede saltar a las vistas");
        }
        if (!traveler.canTeleport()) {
            return Verdict.deny("Te falta el permiso komaedit.view.teleport");
        }
        if (rules.creativeOnly() && !traveler.builderMode() && !traveler.anyMode()) {
            return Verdict.deny("Solo puedes saltar a una vista en creativo o espectador");
        }
        if (!Double.isFinite(target.x()) || !Double.isFinite(target.y()) || !Double.isFinite(target.z())
                || !Float.isFinite(target.yaw()) || !Float.isFinite(target.pitch())) {
            return Verdict.deny("La vista tiene coordenadas no válidas");
        }
        boolean otherWorld = !target.dimension().equals(traveler.dimension());
        if (otherWorld && !traveler.canChangeWorld()) {
            return Verdict.deny("La vista está en otra dimensión (" + target.dimension()
                    + ") y te falta komaedit.view.dimension");
        }
        if (!destination.exists()) {
            return Verdict.deny("La dimensión " + target.dimension() + " no existe en este servidor");
        }
        if (target.y() < destination.bottomY() - FLOOR_MARGIN || target.y() > destination.topY() + SKY_MARGIN) {
            return Verdict.deny("La vista está fuera de la altura del mundo (y = " + Math.round(target.y()) + ")");
        }
        if (!destination.border().contains(target.x(), target.z())) {
            return Verdict.deny("La vista está fuera del borde del mundo");
        }
        if (!otherWorld && rules.maxDistance() > 0) {
            double dx = target.x() - traveler.x();
            double dy = target.y() - traveler.y();
            double dz = target.z() - traveler.z();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > rules.maxDistance()) {
                return Verdict.deny("La vista está a " + Math.round(distance) + " bloques; el máximo es "
                        + Math.round(rules.maxDistance()));
            }
        }
        if (lastJump != Long.MIN_VALUE && now - lastJump < rules.cooldownMillis()) {
            return Verdict.deny("Espera un momento entre saltos");
        }
        Target clean = new Target(target.dimension(), target.x(), target.y(), target.z(),
                SavedView.wrapYaw(target.yaw()), Math.clamp(target.pitch(), -90.0F, 90.0F));
        return new Verdict(true, "", clean);
    }
}
