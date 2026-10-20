package com.komainu.moxia.studio.ui;

/**
 * Colores del Studio. Todo lo que se dibuja sobre el mundo es opaco: ni el marcador del servidor ni la lista
 * de jugadores pueden transparentarse por detrás del panel.
 *
 * <p>El acento (rosa) solo marca tres cosas: lo elegido, lo activo y la acción principal. Los títulos y las
 * etiquetas van en grises, para que lo elegido se vea de un vistazo.</p>
 */
public final class StudioTheme {
    /** Fondo del panel. */
    public static final int PANEL = 0xFF101117;
    /** Barra de herramientas, navegación y pie: un punto más claros que el contenido. */
    public static final int SURFACE = 0xFF16171F;
    public static final int CARD = 0xFF20222B;
    public static final int CARD_HOVER = 0xFF2B2E39;
    public static final int CARD_ACTIVE = 0xFF3A1E29;
    public static final int FIELD = 0xFF0B0C10;
    public static final int DIVIDER = 0xFF2A2C36;
    public static final int TRACK = 0xFF2E303B;
    /** Borde de un control bajo el ratón. */
    public static final int OUTLINE = 0xFF4A4C58;

    public static final int ACCENT = 0xFFE24B6A;
    public static final int ACCENT_HOVER = 0xFFF06684;
    public static final int ACCENT_DIM = 0xFF7D2438;
    /** Títulos de sección: el acento aclarado, solo para los avisos de la cabecera de una herramienta. */
    public static final int SECTION = 0xFFE88EA2;

    public static final int TEXT = 0xFFF4F0F3;
    public static final int TEXT_SECONDARY = 0xFFBDB8C2;
    public static final int MUTED = 0xFF8B8794;
    public static final int DISABLED = 0xFF5E5B66;
    public static final int WHITE = 0xFFFFFFFF;

    public static final int SUCCESS = 0xFF67DFA3;
    public static final int WARNING = 0xFFF4C86A;
    public static final int WARNING_DIM = 0xFF54421A;
    public static final int DANGER = 0xFFFF6B6B;
    public static final int DANGER_DIM = 0xFF4A1F24;
    public static final int FAVORITE = 0xFFF2C14B;

    /** Velo bajo las ventanas modales (biblioteca de bloques, paleta de órdenes y chuleta). */
    public static final int SCRIM = 0xC0000000;

    private StudioTheme() {
    }

    /** {@code argb} con su alfa multiplicado por {@code alpha} (0–1). */
    public static int fade(int argb, double alpha) {
        int a = (int) Math.round((argb >>> 24) * Math.clamp(alpha, 0.0, 1.0));
        return a << 24 | argb & 0xFFFFFF;
    }
}
