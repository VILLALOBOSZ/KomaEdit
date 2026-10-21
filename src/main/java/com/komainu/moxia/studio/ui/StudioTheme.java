package com.komainu.moxia.studio.ui;

/**
 * Las fichas de color del Studio: cada color que se pinta tiene un nombre por lo que hace (fondo, tarjeta, acento,
 * texto secundario…), nunca por cómo es. El tema elegido ({@link StudioPalette}) las rellena con {@link #use}, así que
 * cambiar de tema o retocar un color se hace en un solo sitio. Fuera de aquí y de {@link StudioPalette} no se escribe
 * ningún color a mano (lo vigila {@code StudioThemeTokensTest}).
 *
 * <p>Todo lo que se dibuja sobre el mundo es opaco: ni el marcador del servidor ni la lista de jugadores pueden
 * transparentarse por detrás del panel.</p>
 *
 * <p>El acento solo marca tres cosas: lo elegido, lo activo y la acción principal. Los títulos y las etiquetas van en
 * los tonos del texto, para que lo elegido se vea de un vistazo. Los estados se distinguen también por la forma: lo
 * elegido lleva relleno tintado y una marca de acento (barra o marco); lo que está bajo el ratón, un relleno más claro
 * (y borde en botones, pastillas y miniaturas); lo desactivado va hueco, solo con su contorno.</p>
 *
 * <p>Las fichas son campos y no constantes para que el tema se cambie en caliente; solo {@link #use} las escribe.</p>
 */
public final class StudioTheme {
    /** Las fichas, en el orden de la hoja de colores. Cada tema da un valor a todas. */
    public enum Token {
        /** Fondo del panel. */
        PANEL,
        /** Superficie: barra de arriba, navegación y pie, un punto más claros que el contenido. */
        SURFACE,
        /** Superficie elevada: tarjetas, filas y botones. */
        CARD,
        /** Tarjeta bajo el ratón. */
        CARD_HOVER,
        /** Tarjeta elegida, tintada con el acento. */
        CARD_ACTIVE,
        /** Campo de texto. */
        FIELD,
        /**
         * Fondo de las miniaturas: más claro y neutro que la tarjeta, para que se lean los bloques oscuros (abeto,
         * roble oscuro, pizarra profunda) y las hojas no se pierdan en un fondo de su mismo verde.
         */
        THUMBNAIL,
        /** Borde y líneas finas. */
        DIVIDER,
        /** Carril de las barras y del interruptor apagado. */
        TRACK,
        /** Borde de lo que está bajo el ratón. */
        OUTLINE,
        /** Borde del campo en el que se escribe. */
        FOCUS,
        /** Acento: lo elegido, lo activo y la acción principal. */
        ACCENT,
        /** La acción principal bajo el ratón. */
        ACCENT_HOVER,
        /** El acento como texto o icono sobre una superficie. */
        ACCENT_TEXT,
        /** Acento suave: pastilla elegida, barra de un valor sin cambiar, desplazamiento. */
        ACCENT_DIM,
        /** Texto e icono sobre un relleno de acento. */
        ON_ACCENT,
        /** Títulos y el nombre de lo elegido. */
        TEXT_STRONG,
        /** Texto. */
        TEXT,
        /** Texto secundario: etiquetas. */
        TEXT_SECONDARY,
        /** Notas, pistas y unidades. */
        MUTED,
        /** Lo que aún no se puede usar. */
        DISABLED,
        /** Éxito: hecho, colocando, simetría activa. */
        SUCCESS,
        /** Aviso: obra grande, en espera. */
        WARNING,
        /** Fondo de un aviso. */
        WARNING_DIM,
        /** Fondo de un aviso bajo el ratón. */
        WARNING_HOVER,
        /** Peligro: borrar, cancelar y errores. Nunca hace de acento. */
        DANGER,
        /** Fondo de un peligro. */
        DANGER_DIM,
        /** Favoritas y presets propios. */
        FAVORITE,
        /** Velo bajo las ventanas modales (biblioteca de bloques, paleta de órdenes y chuleta). */
        SCRIM
    }

    public static int PANEL;
    public static int SURFACE;
    public static int CARD;
    public static int CARD_HOVER;
    public static int CARD_ACTIVE;
    public static int FIELD;
    public static int THUMBNAIL;
    public static int DIVIDER;
    public static int TRACK;
    public static int OUTLINE;
    public static int FOCUS;

    public static int ACCENT;
    public static int ACCENT_HOVER;
    public static int ACCENT_TEXT;
    public static int ACCENT_DIM;
    public static int ON_ACCENT;

    public static int TEXT_STRONG;
    public static int TEXT;
    public static int TEXT_SECONDARY;
    public static int MUTED;
    public static int DISABLED;

    public static int SUCCESS;
    public static int WARNING;
    public static int WARNING_DIM;
    public static int WARNING_HOVER;
    public static int DANGER;
    public static int DANGER_DIM;
    public static int FAVORITE;

    public static int SCRIM;

    // ── En el mundo ─────────────────────────────────────────────────────────────────────────────────────

    /*
     * Lo que se dibuja con líneas en el mundo: el contorno de la selección y sus caras al cogerlas con el ratón del
     * Studio, la caja de la vista previa, el radio del pincel y la simetría. No cambia con el tema, a propósito: el
     * fondo es el mundo y no el panel, así que valen colores que se lean sobre cielo, agua, hierba y piedra (un acento
     * turquesa o salvia se perdería), y los ejes son los de siempre, rojo X, verde Y y azul Z. El ámbar de las caras
     * es el de la caja de la vista previa: en el mundo dice «esto se mueve», no «aviso».
     */
    public static final int WORLD_SELECTION = 0xE6F2F2FF;
    public static final int WORLD_PREVIEW_BOX = 0xF0FFD84A;
    public static final int WORLD_RETICLE = 0xD9FFFFFF;
    /** La cara de la selección bajo el ratón del Studio y la que se está arrastrando. */
    public static final int WORLD_FACE_HOVER = 0xCCFFE082;
    public static final int WORLD_FACE_GRABBED = 0xFFFFCA28;
    public static final int WORLD_PLANE_X = 0xEF5350;
    public static final int WORLD_PLANE_Z = 0x42A5F5;
    public static final int WORLD_PLANE_Y = 0x66BB6A;
    public static final int WORLD_AXIS = 0xFFCA28;

    private static StudioPalette current;

    static {
        use(StudioPalette.DEFAULT);
    }

    private StudioTheme() {
    }

    /** El tema puesto. */
    public static StudioPalette current() {
        return current;
    }

    /** Pone {@code palette} (el de serie si es {@code null}); lo siguiente que se pinte ya sale con sus colores. */
    public static void use(StudioPalette palette) {
        StudioPalette chosen = palette == null ? StudioPalette.DEFAULT : palette;
        if (chosen == current) {
            return;
        }
        current = chosen;
        PANEL = chosen.color(Token.PANEL);
        SURFACE = chosen.color(Token.SURFACE);
        CARD = chosen.color(Token.CARD);
        CARD_HOVER = chosen.color(Token.CARD_HOVER);
        CARD_ACTIVE = chosen.color(Token.CARD_ACTIVE);
        FIELD = chosen.color(Token.FIELD);
        THUMBNAIL = chosen.color(Token.THUMBNAIL);
        DIVIDER = chosen.color(Token.DIVIDER);
        TRACK = chosen.color(Token.TRACK);
        OUTLINE = chosen.color(Token.OUTLINE);
        FOCUS = chosen.color(Token.FOCUS);
        ACCENT = chosen.color(Token.ACCENT);
        ACCENT_HOVER = chosen.color(Token.ACCENT_HOVER);
        ACCENT_TEXT = chosen.color(Token.ACCENT_TEXT);
        ACCENT_DIM = chosen.color(Token.ACCENT_DIM);
        ON_ACCENT = chosen.color(Token.ON_ACCENT);
        TEXT_STRONG = chosen.color(Token.TEXT_STRONG);
        TEXT = chosen.color(Token.TEXT);
        TEXT_SECONDARY = chosen.color(Token.TEXT_SECONDARY);
        MUTED = chosen.color(Token.MUTED);
        DISABLED = chosen.color(Token.DISABLED);
        SUCCESS = chosen.color(Token.SUCCESS);
        WARNING = chosen.color(Token.WARNING);
        WARNING_DIM = chosen.color(Token.WARNING_DIM);
        WARNING_HOVER = chosen.color(Token.WARNING_HOVER);
        DANGER = chosen.color(Token.DANGER);
        DANGER_DIM = chosen.color(Token.DANGER_DIM);
        FAVORITE = chosen.color(Token.FAVORITE);
        SCRIM = chosen.color(Token.SCRIM);
    }

    /** {@code argb} con su alfa multiplicado por {@code alpha} (0–1). */
    public static int fade(int argb, double alpha) {
        int a = (int) Math.round((argb >>> 24) * Math.clamp(alpha, 0.0, 1.0));
        return a << 24 | argb & 0xFFFFFF;
    }
}
