package com.komainu.moxia.studio.layout;

import java.util.Arrays;

/**
 * Reparto de la pantalla del Studio, calculado sin Minecraft para poder probarlo a cualquier resolución
 * y escala de GUI.
 *
 * <p>Todo el Studio vive en un único panel opaco pegado a la derecha: barra de herramientas arriba (con el nombre de
 * la etapa), navegación de etapas por grupos a la izquierda del panel, contenido desplazable y un pie fijo con sobre
 * qué se trabaja, el estado y el botón Construir. El resto de la pantalla queda para el mundo: abajo a la izquierda
 * se dibuja el chat y arriba a la derecha el marcador del servidor, que el panel tapa sin transparentarse.</p>
 *
 * <p>En pantallas bajas (menos de {@value #COMPACT_HEIGHT} píxeles escalados de alto, como 854×480 con GUI 2 o
 * 1080p con GUI 4) el reparto es compacto: barra y pie más bajos, navegación solo con iconos y el contenido sin
 * descripciones.</p>
 */
public final class StudioLayout {
    public static final int TOOLBAR_HEIGHT = 24;
    public static final int COMPACT_TOOLBAR_HEIGHT = 22;
    public static final int FOOTER_HEIGHT = 54;
    public static final int COMPACT_FOOTER_HEIGHT = 40;
    /** La cabecera de la etapa va en la barra de herramientas: el contenido empieza justo debajo. */
    public static final int HEADER_HEIGHT = 0;
    public static final int PADDING = 10;
    public static final int COMPACT_PADDING = 7;
    public static final int NAV_LABEL_WIDTH = 112;
    public static final int NAV_ICON_WIDTH = 26;
    /** Con menos sitio para el contenido, la navegación pasa a iconos (el contenido es lo que se usa). */
    public static final int MIN_CONTENT_WIDTH = 220;
    public static final int MIN_PANEL_WIDTH = 300;
    /** Por debajo de este ancho escalado (GUI 4 en 1080p) el panel encoge y la navegación queda en iconos. */
    public static final int NARROW_SCREEN = 600;
    /** Por debajo de este alto escalado el Studio pasa a compacto. */
    public static final int COMPACT_HEIGHT = 300;
    public static final int NARROW_PANEL_WIDTH = 230;
    public static final int MAX_PANEL_WIDTH = 470;
    public static final int DEFAULT_PANEL_WIDTH = 350;
    public static final int TOOL_BUTTON = 20;
    public static final int TOOL_GAP = 3;
    public static final int NAV_GAP = 2;
    /** Con muchas etapas en poco alto el hueco entre ellas baja a 1 px antes de encoger más las filas. */
    public static final int TIGHT_NAV_GAP = 1;
    /** El alto de un icono: por debajo ya no se ve. */
    public static final int MIN_NAV_ROW = 12;
    /** Por debajo de este alto de fila se gana sitio quitando hueco entre etapas. */
    public static final int COMFORT_NAV_ROW = 14;
    public static final int MAX_NAV_ROW = 20;
    /** Alto del título de un grupo de etapas («CREAR») con nombres; con iconos, una raya. */
    public static final int GROUP_CAPTION = 13;
    public static final int GROUP_DIVIDER = 5;
    public static final int BUILD_BUTTON_HEIGHT = 18;
    /** Ancho del chat vanilla con escala 1 (320) más sus márgenes. */
    public static final int CHAT_WIDTH = 330;
    /** El chat sin abrir ocupa unas diez líneas por encima de la barra de objetos. */
    public static final int CHAT_HEIGHT = 92;
    public static final int CHAT_BOTTOM_MARGIN = 40;

    public enum NavMode {
        /** Icono, nombre y tecla. */
        LABELS,
        /** Solo icono; el nombre y la tecla salen en el tooltip. Para paneles estrechos. */
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

    /** Si el Studio va en compacto a este tamaño de pantalla escalada. */
    public static boolean compact(int screenWidth, int screenHeight) {
        return screenHeight < COMPACT_HEIGHT;
    }

    /** Zona del chat del HUD vanilla (sin abrir), abajo a la izquierda. */
    public static Rect chatZone(int screenWidth, int screenHeight) {
        int top = Math.max(0, screenHeight - CHAT_BOTTOM_MARGIN - CHAT_HEIGHT);
        return new Rect(0, top, Math.min(screenWidth, CHAT_WIDTH), screenHeight - CHAT_BOTTOM_MARGIN - top);
    }

    /** Un solo grupo de {@code stages} etapas. */
    public static Frame compute(int screenWidth, int screenHeight, int preferredPanelWidth, int stages) {
        return compute(screenWidth, screenHeight, preferredPanelWidth, new int[]{stages});
    }

    /** @param groups cuántas etapas tiene cada grupo de la navegación, en orden */
    public static Frame compute(int screenWidth, int screenHeight, int preferredPanelWidth, int[] groups) {
        boolean compact = compact(screenWidth, screenHeight);
        int panelWidth = clampPanelWidth(screenWidth, preferredPanelWidth);
        int toolbarHeight = compact ? COMPACT_TOOLBAR_HEIGHT : TOOLBAR_HEIGHT;
        int footerHeight = compact ? COMPACT_FOOTER_HEIGHT : FOOTER_HEIGHT;
        Rect screen = new Rect(0, 0, screenWidth, screenHeight);
        Rect panel = new Rect(screenWidth - panelWidth, 0, panelWidth, screenHeight);
        Rect toolbar = new Rect(panel.x(), 0, panelWidth, toolbarHeight);
        Rect footer = new Rect(panel.x(), screenHeight - footerHeight, panelWidth, footerHeight);
        int bodyTop = toolbar.bottom();
        int bodyHeight = Math.max(0, footer.y() - bodyTop);
        NavMode mode = !compact && panelWidth - NAV_LABEL_WIDTH >= MIN_CONTENT_WIDTH ? NavMode.LABELS : NavMode.ICONS;
        int navWidth = mode == NavMode.LABELS ? NAV_LABEL_WIDTH : NAV_ICON_WIDTH;
        Rect nav = new Rect(panel.x(), bodyTop, navWidth, bodyHeight);
        Rect content = new Rect(nav.right(), bodyTop, panelWidth - navWidth, bodyHeight);
        Rect header = new Rect(content.x(), content.y(), content.width(), 0);
        int[] sizes = groups.length == 0 ? new int[]{1} : groups.clone();
        int count = Math.max(1, Arrays.stream(sizes).sum());
        int captions = mode == NavMode.LABELS ? sizes.length * GROUP_CAPTION : (sizes.length - 1) * GROUP_DIVIDER;
        int navGap = NAV_GAP;
        int available = bodyHeight - 8 - captions;
        int navRow = (available - navGap * (count - 1)) / count;
        if (navRow < COMFORT_NAV_ROW) {
            navGap = TIGHT_NAV_GAP;
            navRow = (available - navGap * (count - 1)) / count;
        }
        navRow = Math.clamp(navRow, MIN_NAV_ROW, MAX_NAV_ROW);
        Rect handle = new Rect(panel.x() - 3, 0, 6, screenHeight);
        Rect world = new Rect(0, 0, panel.x(), screenHeight);
        return new Frame(screen, panel, toolbar, nav, mode, navRow, navGap, count, sizes, compact, content, header,
                content, footer, handle, world, chatZone(screenWidth, screenHeight));
    }

    /**
     * El reparto de un fotograma.
     *
     * @param viewport zona desplazable del contenido
     * @param world    lo que queda para ver el mundo a la izquierda del panel
     * @param groups   cuántas etapas tiene cada grupo de la navegación
     */
    public record Frame(
            Rect screen,
            Rect panel,
            Rect toolbar,
            Rect nav,
            NavMode navMode,
            int navRowHeight,
            int navGap,
            int stages,
            int[] groups,
            boolean compact,
            Rect content,
            Rect header,
            Rect viewport,
            Rect footer,
            Rect resizeHandle,
            Rect world,
            Rect chatZone
    ) {
        public int padding() {
            return compact ? COMPACT_PADDING : PADDING;
        }

        /** Grupo de la etapa {@code index}. */
        public int groupOf(int index) {
            int start = 0;
            for (int group = 0; group < groups.length; group++) {
                if (index < start + groups[group]) {
                    return group;
                }
                start += groups[group];
            }
            return groups.length - 1;
        }

        /** Dónde va el título (con nombres) o la raya (con iconos) que abre el grupo {@code group}. */
        public Rect groupCaption(int group) {
            int first = 0;
            for (int previous = 0; previous < group; previous++) {
                first += groups[previous];
            }
            Rect item = navItem(Math.min(first, stages - 1));
            int height = navMode == NavMode.LABELS ? GROUP_CAPTION : GROUP_DIVIDER;
            return new Rect(nav.x() + 3, item.y() - height, nav.width() - 6, height);
        }

        /** Fila de la etapa {@code index} en la navegación. */
        public Rect navItem(int index) {
            int group = groupOf(index);
            int captions = navMode == NavMode.LABELS ? (group + 1) * GROUP_CAPTION : group * GROUP_DIVIDER;
            int y = nav.y() + 4 + captions + index * (navRowHeight + navGap);
            return new Rect(nav.x() + 3, y, nav.width() - 6, navRowHeight);
        }

        /** Botón {@code index} de la barra de herramientas, contando desde la derecha. */
        public Rect toolButton(int index) {
            int x = toolbar.right() - padding() + TOOL_GAP - (index + 1) * (TOOL_BUTTON + TOOL_GAP);
            return new Rect(x, toolbar.y() + (toolbar.height() - TOOL_BUTTON) / 2, TOOL_BUTTON, TOOL_BUTTON);
        }

        /** Ancho útil del contenido, ya sin márgenes. */
        public int innerWidth() {
            return viewport.width() - padding() * 2;
        }

        public int innerLeft() {
            return viewport.x() + padding();
        }

        /** Primera línea del pie: sobre qué se trabaja a la izquierda, la estimación a la derecha. */
        public Rect footerSummary() {
            int pad = padding();
            return new Rect(footer.x() + pad, footer.y() + 5, footer.width() - pad * 2, 9);
        }

        /** Segunda línea del pie: lo último que pasó o lo que está en marcha. En compacto es la misma línea. */
        public Rect footerStatus() {
            if (compact) {
                return footerSummary();
            }
            int pad = padding();
            return new Rect(footer.x() + pad, footer.y() + 17, footer.width() - pad * 2, 9);
        }

        public Rect buildButton() {
            int pad = padding();
            return new Rect(footer.x() + pad, footer.bottom() - BUILD_BUTTON_HEIGHT - (compact ? 4 : 5),
                    footer.width() - pad * 2, BUILD_BUTTON_HEIGHT);
        }

        /** Barra fina de progreso bajo el estado, mientras hay algo en marcha. */
        public Rect footerProgress() {
            Rect status = footerStatus();
            return new Rect(status.x(), status.bottom() + 2, status.width(), 2);
        }

        /**
         * El botón Construir partido en dos (Confirmar y Descartar la vista previa, o Construir y Cancelar): el
         * segundo, a la derecha, mide {@code secondaryWidth} sin pasar de la mitad.
         */
        public Rect[] footerButtons(int secondaryWidth) {
            Rect button = buildButton();
            int secondary = Math.clamp(secondaryWidth, 24, button.width() / 2);
            Rect right = new Rect(button.right() - secondary, button.y(), secondary, button.height());
            Rect left = new Rect(button.x(), button.y(), button.width() - secondary - TOOL_GAP, button.height());
            return new Rect[]{left, right};
        }

        /** El botón Construir con el selector de vista previa a su izquierda: {selector, construir}. */
        public Rect[] buildWithPreview() {
            Rect button = buildButton();
            Rect preview = new Rect(button.x(), button.y(), button.height() + 4, button.height());
            Rect build = new Rect(preview.right() + TOOL_GAP, button.y(), button.right() - preview.right() - TOOL_GAP,
                    button.height());
            return new Rect[]{preview, build};
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Frame that && screen.equals(that.screen) && panel.equals(that.panel)
                    && Arrays.equals(groups, that.groups) && compact == that.compact;
        }

        @Override
        public int hashCode() {
            return screen.hashCode() * 31 + panel.hashCode() + Arrays.hashCode(groups);
        }

        @Override
        public String toString() {
            return "Frame[" + screen + ", panel " + panel + ", " + navMode + (compact ? ", compacto" : "") + "]";
        }
    }
}
