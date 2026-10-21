package com.komainu.moxia.hotbar;

/**
 * Qué hace el servidor al cambiar de barra, sin Minecraft.
 *
 * <ul>
 *     <li><b>Creativo</b>: pone la barra tal cual. En creativo cualquiera puede sacar los objetos que quiera del
 *     inventario creativo, así que no da nada nuevo.</li>
 *     <li>Fuera de creativo con {@link #GIVE_PERMISSION} (los operadores lo tienen, como todos los nodos): también la
 *     pone tal cual, pero eso crea objetos, y la tira y el menú lo avisan.</li>
 *     <li><b>Supervivencia y aventura</b>: solo reordena. Busca cada objeto de la barra en el inventario del jugador
 *     y lo cambia de sitio ({@link HotbarReorder}); lo que no lleva se queda como estaba. Nunca crea ni borra
 *     objetos: el inventario tiene exactamente lo mismo antes y después.</li>
 *     <li><b>Espectador</b> o sin {@link #PERMISSION}: no se cambia.</li>
 * </ul>
 */
public final class HotbarRules {
    public static final String PERMISSION = "komaedit.hotbar";
    /** Poner barras como en creativo en cualquier modo: crea objetos, así que es de administrador. */
    public static final String GIVE_PERMISSION = "komaedit.admin.hotbar.give";

    public enum Mode {
        /** Pone los nueve huecos tal cual (creativo). */
        SET,
        /** Como {@link #SET}, fuera de creativo y por {@link #GIVE_PERMISSION}: crea objetos, y se avisa. */
        GIVE,
        /** Mueve lo que el jugador ya lleva. */
        REORDER,
        /** No hace nada. */
        DENIED
    }

    /** Lo decidido y, si no se puede, por qué. */
    public record Decision(Mode mode, String reason) {
        public boolean allowed() {
            return mode != Mode.DENIED;
        }

        /** Pone la barra tal cual ({@link Mode#SET} o {@link Mode#GIVE}). */
        public boolean sets() {
            return mode == Mode.SET || mode == Mode.GIVE;
        }
    }

    private HotbarRules() {
    }

    /**
     * @param permitted tiene {@link #PERMISSION}
     * @param creative  está en creativo
     * @param spectator está en espectador
     * @param give      tiene {@link #GIVE_PERMISSION}
     */
    public static Decision decide(boolean permitted, boolean creative, boolean spectator, boolean give) {
        if (!permitted) {
            return new Decision(Mode.DENIED, "Te falta el permiso " + PERMISSION);
        }
        if (creative) {
            return new Decision(Mode.SET, null);
        }
        if (spectator) {
            return new Decision(Mode.DENIED, "En espectador no hay barra que cambiar");
        }
        if (give) {
            return new Decision(Mode.GIVE, null);
        }
        return new Decision(Mode.REORDER, null);
    }

    /** La pila tal como se pone en creativo: sin pasar de lo que admite ese objeto. */
    public static HotbarItem clamp(HotbarItem item, int maxCount) {
        if (item.isEmpty()) {
            return item;
        }
        return item.withCount(Math.clamp(item.count(), 1, Math.max(1, maxCount)));
    }
}
