package com.komainu.moxia.studio.layout;

/**
 * Reparto de la pantalla del Studio, calculado sin Minecraft para poder probarlo a cualquier resolución
 * y escala de GUI.
 *
 * <p>Todo el Studio vive en un único panel opaco pegado a la derecha: barra de herramientas arriba,
 * navegación de etapas a la izquierda del panel, contenido desplazable y un pie fijo con el resumen,
 * el estado y el botón Construir. El resto de la pantalla queda para el mundo: abajo a la izquierda
 * se dibuja el chat y arriba a la derecha el marcador del servidor, que el panel tapa sin transparentarse.
 */
public final class StudioLayout {
    public static final int TOOLBAR_HEIGHT = 26;
    public static final int FOOTER_HEIGHT = 62;
    public static final int HEADER_HEIGHT = 36;
    public static final int PADDING = 10;
    public static final int NAV_LABEL_WIDTH = 106;
    public static final int NAV_ICON_WIDTH = 26;
    public static final int MIN_CONTENT_WIDTH = 194;
    public static final int MIN_PANEL_WIDTH = 300;
    /** Por debajo de este ancho escalado (GUI 4 en 1080p) el panel encoge y la navegación queda en iconos. */
    public static final int NARROW_SCREEN = 600;
    public static final int NARROW_PANEL_WIDTH = 230;
    public static final int MAX_PANEL_WIDTH = 470;
    public static final int DEFAULT_PANEL_WIDTH = 350;
    public static final int TOOL_BUTTON = 20;
    public static final int TOOL_GAP = 3;
    public static final int NAV_GAP = 2;
    public static final int MIN_NAV_ROW = 14;
    public static final int MAX_NAV_ROW = 22;
    public static final int BUILD_BUTTON_HEIGHT = 20;
    /** Ancho del chat vanilla con escala 1 (320) más sus márgenes. */
    public static final int CHAT_WIDTH = 330;
    /** El chat sin abrir ocupa unas diez líneas por encima de la barra de objetos. */
    public static final int CHAT_HEIGHT = 92;
    public static final int CHAT_BOTTOM_MARGIN = 40;

    public enum NavMode {
        /** Icono y nombre; el número de atajo sale en el tooltip. */
        LABELS,
        /** Solo icono; el nombre sale en el tooltip. Para paneles estrechos. */
        ICONS
    }

    private StudioLayout() {
    }

    public static int minimumPanelWidth(int screenWidth) {
        int floor = screenWidth < NARROW_SCREEN ? NARROW_PANEL_WIDTH : MIN_PANEL_WIDTH;
        return Math.min(floor, Math.max(160, screenWidth * 2 / 3));
    }

    /** En pantallas estrechas el panel no pasa del mínimo: el mundo necesita sitio para colocar puntos. */
    public static int maximumPanelWidth(int screenWidth) {
        if (screenWidth < NARROW_SCREEN) {
            return minimumPanelWidth(screenWidth);
        }
        return Math.max(minimumPanelWidth(screenWidth), Math.min(MAX_PANEL_WIDTH, screenWidth / 2));
    }

    public static int clampPanelWidth(int screenWidth, int preferred) {
        return Math.clamp(preferred, minimumPanelWidth(screenWidth), maximumPanelWidth(screenWidth));
    }

    /** Zona del chat del HUD vanilla (sin abrir), abajo a la izquierda. */
    public static Rect chatZone(int screenWidth, int screenHeight) {
        int top = Math.max(0, screenHeight - CHAT_BOTTOM_MARGIN - CHAT_HEIGHT);
        return new Rect(0, top, Math.min(screenWidth, CHAT_WIDTH), screenHeight - CHAT_BOTTOM_MARGIN - top);
    }

    public static Frame compute(int screenWidth, int screenHeight, int preferredPanelWidth, int stages) {
        int panelWidth = clampPanelWidth(screenWidth, preferredPanelWidth);
        Rect screen = new Rect(0, 0, screenWidth, screenHeight);
        Rect panel = new Rect(screenWidth - panelWidth, 0, panelWidth, screenHeight);
        Rect toolbar = new Rect(panel.x(), 0, panelWidth, TOOLBAR_HEIGHT);
        Rect footer = new Rect(panel.x(), screenHeight - FOOTER_HEIGHT, panelWidth, FOOTER_HEIGHT);
        int bodyTop = toolbar.bottom();
        int bodyHeight = Math.max(0, footer.y() - bodyTop);
        NavMode mode = panelWidth - NAV_LABEL_WIDTH >= MIN_CONTENT_WIDTH ? NavMode.LABELS : NavMode.ICONS;
        int navWidth = mode == NavMode.LABELS ? NAV_LABEL_WIDTH : NAV_ICON_WIDTH;
        Rect nav = new Rect(panel.x(), bodyTop, navWidth, bodyHeight);
        Rect content = new Rect(nav.right(), bodyTop, panelWidth - navWidth, bodyHeight);
        Rect header = new Rect(content.x(), content.y(), content.width(), Math.min(HEADER_HEIGHT, bodyHeight));
        Rect viewport = new Rect(content.x(), header.bottom(), content.width(), content.bottom() - header.bottom());
        int navRow = Math.clamp((bodyHeight - 8 - NAV_GAP * (stages - 1)) / Math.max(1, stages),
                MIN_NAV_ROW, MAX_NAV_ROW);
        Rect handle = new Rect(panel.x() - 3, 0, 6, screenHeight);
        Rect world = new Rect(0, 0, panel.x(), screenHeight);
        return new Frame(screen, panel, toolbar, nav, mode, navRow, stages, content, header, viewport, footer,
                handle, world, chatZone(screenWidth, screenHeight));
    }

    /**
     * El reparto de un fotograma.
     *
     * @param viewport zona desplazable del contenido, bajo la cabecera de la etapa
     * @param world    lo que queda para ver el mundo a la izquierda del panel
     */
    public record Frame(
            Rect screen,
            Rect panel,
            Rect toolbar,
            Rect nav,
            NavMode navMode,
            int navRowHeight,
            int stages,
            Rect content,
            Rect header,
            Rect viewport,
            Rect footer,
            Rect resizeHandle,
            Rect world,
            Rect chatZone
    ) {
        /** Fila de la etapa {@code index} en la navegación. */
        public Rect navItem(int index) {
            int y = nav.y() + 6 + index * (navRowHeight + NAV_GAP);
            return new Rect(nav.x() + 3, y, nav.width() - 6, navRowHeight);
        }

        /** Botón {@code index} de la barra de herramientas, contando desde la derecha. */
        public Rect toolButton(int index) {
            int x = toolbar.right() - PADDING + TOOL_GAP - (index + 1) * (TOOL_BUTTON + TOOL_GAP);
            return new Rect(x, toolbar.y() + (toolbar.height() - TOOL_BUTTON) / 2, TOOL_BUTTON, TOOL_BUTTON);
        }

        /** Ancho útil del contenido, ya sin márgenes. */
        public int innerWidth() {
            return viewport.width() - PADDING * 2;
        }

        public int innerLeft() {
            return viewport.x() + PADDING;
        }

        /** Primera línea del pie: figura y material a la izquierda, estimación a la derecha. */
        public Rect footerSummary() {
            return new Rect(footer.x() + PADDING, footer.y() + 8, footer.width() - PADDING * 2, 9);
        }

        /** Segunda línea del pie: lo último que pasó. */
        public Rect footerStatus() {
            return new Rect(footer.x() + PADDING, footer.y() + 21, footer.width() - PADDING * 2, 9);
        }

        public Rect buildButton() {
            return new Rect(footer.x() + PADDING, footer.bottom() - BUILD_BUTTON_HEIGHT - 8,
                    footer.width() - PADDING * 2, BUILD_BUTTON_HEIGHT);
        }
    }
}
