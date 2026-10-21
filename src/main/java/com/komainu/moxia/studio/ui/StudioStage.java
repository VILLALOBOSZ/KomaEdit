package com.komainu.moxia.studio.ui;

import java.util.Arrays;
import java.util.Locale;

/**
 * Las etapas del Studio, en el orden de la navegación y repartidas en tres grupos con sentido:
 *
 * <ul>
 *     <li><b>Crear</b>: qué se construye y dónde (herramientas, trayectoria y figuras).</li>
 *     <li><b>Ajustar</b>: cómo queda lo que ya hay (color).</li>
 *     <li><b>Biblioteca</b>: lo guardado (planos, recetas e instancias).</li>
 * </ul>
 *
 * Cada etapa tiene su tecla, del 1 al 7. Las antiguas «Estructuras» son ahora una familia de Figuras; Envejecer
 * salió del Studio (queda la orden {@code /komaedit weather}). Modificadores y Material solo valían para la figura y
 * viven dentro de Figuras (sus variaciones y su material).
 */
public enum StudioStage {
    TOOLS("tools", Group.CREATE, StudioIcons.Icon.TOOLS),
    PATH("path", Group.CREATE, StudioIcons.Icon.PATH),
    FIGURE("figure", Group.CREATE, StudioIcons.Icon.FIGURE),
    COLOR("color", Group.ADJUST, StudioIcons.Icon.COLOR),
    BLUEPRINTS("blueprints", Group.LIBRARY, StudioIcons.Icon.BLUEPRINTS),
    RECIPES("recipes", Group.LIBRARY, StudioIcons.Icon.RECIPES),
    INSTANCES("instances", Group.LIBRARY, StudioIcons.Icon.INSTANCES);

    /** Grupos de la navegación. */
    public enum Group {
        CREATE, ADJUST, LIBRARY;

        public String translationKey() {
            return "gui.komaedit.group." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final String id;
    private final Group group;
    private final StudioIcons.Icon icon;

    StudioStage(String id, Group group, StudioIcons.Icon icon) {
        this.id = id;
        this.group = group;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    public Group group() {
        return group;
    }

    public StudioIcons.Icon icon() {
        return icon;
    }

    public String titleKey() {
        return "gui.komaedit.stage." + id;
    }

    public String subtitleKey() {
        return titleKey() + ".subtitle";
    }

    /** La tecla de la etapa: «1» … «9» y «0» para la décima. */
    public char shortcut() {
        int index = ordinal();
        return index < 9 ? (char) ('1' + index) : '0';
    }

    /** La etapa de una tecla numérica, o {@code null}. */
    public static StudioStage byShortcut(char key) {
        for (StudioStage stage : values()) {
            if (stage.shortcut() == key) {
                return stage;
            }
        }
        return null;
    }

    /**
     * La etapa con ese id, o {@code null}. Los de antes abren donde quedó lo suyo: «structures», «modifiers» y
     * «material», Figuras.
     */
    public static StudioStage byId(String id) {
        if ("structures".equals(id) || "modifiers".equals(id) || "material".equals(id)) {
            return FIGURE;
        }
        for (StudioStage stage : values()) {
            if (stage.id.equals(id)) {
                return stage;
            }
        }
        return null;
    }

    /** Cuántas etapas tiene cada grupo, en orden: lo que necesita el reparto de la navegación. */
    public static int[] groupSizes() {
        return Arrays.stream(Group.values())
                .mapToInt(group -> (int) Arrays.stream(values()).filter(stage -> stage.group == group).count())
                .toArray();
    }
}
