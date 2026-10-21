package com.komainu.moxia.studio.tools;

import com.komainu.moxia.display.DisplayLook;
import com.komainu.moxia.display.DisplayPose;
import com.komainu.moxia.display.DisplayService;
import com.komainu.moxia.display.DisplayText;
import com.komainu.moxia.studio.ValueUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * «Decorar (displays)»: crear un display de bloque, de objeto o de texto contra el bloque que se mira y editarlo con
 * el gizmo. Construir manda {@code /komaedit display block|item|text}; los botones abren el editor, mandan los pasos
 * al cliente ({@code /kedisplay ajustes}, sin pasar por el servidor) y aplican los ajustes avanzados a lo elegido.
 */
public final class DisplayCard extends ToolCard {
    public static final String PERMISSION = DisplayService.PERMISSION;
    /** Pasos de mover de la tarjeta y cómo los lee {@code /kedisplay}. */
    static final Map<String, String> MOVE_STEPS = Map.of("bloque", "1", "cuarto", "1/4", "pixel", "1/16",
            "libre", "libre");
    static final List<String> COLORS = List.of("blanco", "amarillo", "dorado", "rojo", "verde", "azul", "celeste",
            "rosa", "gris", "negro");

    DisplayCard() {
        super("display", ToolFamily.DECORATE, ToolTarget.LOOK,
                List.of("display", "displays", "decorar", "decoracion", "decoración", "block display", "item display",
                        "text display", "holograma", "cartel", "maceta", "axiom", "entidad", "gizmo"),
                List.of(
                        choice("kind", ToolParam.Choice.Style.BUTTONS, "block", "item", "text"),
                        when(DisplayCard::block, new ToolParam.Block("block", null, null)),
                        when(DisplayCard::item, choice("item_mode", ToolParam.Choice.Style.BUTTONS, "fixed", "none",
                                "gui", "ground", "head")),
                        when(DisplayCard::text, new ToolParam.Text("display_text", "Bienvenidos", 256, true, null)),
                        when(DisplayCard::text, new ToolParam.Choice("text_color", COLORS, "blanco",
                                ToolParam.Choice.Style.CYCLE, "", null)),
                        when(DisplayCard::text, choice("background", ToolParam.Choice.Style.BUTTONS, "serie", "no",
                                "negro")),
                        when(DisplayCard::text, toggle("text_shadow", false)),
                        when(DisplayCard::text, choice("align", ToolParam.Choice.Style.BUTTONS, "centro",
                                "izquierda", "derecha")),
                        choice("move_step", ToolParam.Choice.Style.BUTTONS, "pixel", "cuarto", "bloque", "libre"),
                        choice("angle_step", ToolParam.Choice.Style.BUTTONS, "15", "5", "libre"),
                        toggle("local", false),
                        choice("brightness", ToolParam.Choice.Style.CYCLE, "auto", "15", "12", "9", "6", "3", "0"),
                        decimal("shadow_radius", 0, 640, 5, 10, 0, 1).unit(ValueUnit.BLOCKS),
                        decimal("shadow_strength", 0, 100, 5, 25, 100, 2).unit(ValueUnit.PERCENT),
                        decimal("view_range", 5, 1_600, 25, 100, 100, 2).unit(ValueUnit.MULTIPLIER),
                        choice("billboard", ToolParam.Choice.Style.BUTTONS, "fixed", "vertical", "horizontal",
                                "center"),
                        toggle("glow", false),
                        when(values -> values.toggle("glow"), new ToolParam.Choice("glow_color", COLORS, "blanco",
                                ToolParam.Choice.Style.CYCLE, "", null))
                ));
    }

    private static boolean block(ToolValues values) {
        return values.choice("kind").equals("block");
    }

    private static boolean item(ToolValues values) {
        return values.choice("kind").equals("item");
    }

    private static boolean text(ToolValues values) {
        return values.choice("kind").equals("text");
    }

    /**
     * Lo que se ve sin abrir «Más ajustes»: qué crear y el paso de mover del gizmo. El fondo, la sombra y la
     * alineación del texto, el giro, el marco y los ajustes avanzados (brillo, sombra, distancia, billboard y
     * contorno) quedan plegados.
     */
    @Override
    public List<String> essentials() {
        return List.of("kind", "block", "item_mode", "display_text", "text_color", "move_step");
    }

    /** Crea el display contra el bloque mirado: el de la mano (o el elegido), el objeto de la mano o el texto. */
    @Override
    public String command(ToolValues values, boolean preview) {
        return switch (values.choice("kind")) {
            case "item" -> "komaedit display item " + values.choice("item_mode");
            case "text" -> {
                CommandLine line = new CommandLine("komaedit display text");
                if (!values.choice("text_color").equals("blanco")) {
                    line.flag("-c", values.choice("text_color"));
                }
                if (!values.choice("background").equals("serie")) {
                    line.flag("-f", values.choice("background"));
                }
                line.flag("-s", values.toggle("text_shadow"));
                if (!values.choice("align").equals("centro")) {
                    line.flag("-a", values.choice("align"));
                }
                yield line.word(values.text("display_text").replace("\n", "\\n")).build();
            }
            default -> new CommandLine("komaedit display block").word(values.block("block")).build();
        };
    }

    @Override
    public boolean previewable() {
        return false;
    }

    @Override
    public List<String> permissions(ToolValues values) {
        return List.of(PERMISSION);
    }

    /** Al elegir la tarjeta se abre el editor: un clic en un display lo elige y el gizmo lo mueve. */
    @Override
    public String selectCommand() {
        return "komaedit display edit on";
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(
                new ToolAction("display_edit", List.of(), false, values -> "komaedit display edit off"),
                new ToolAction("display_steps", List.of(), false, DisplayCard::stepsCommand),
                new ToolAction("display_look", List.of(), false, DisplayCard::lookCommand),
                new ToolAction("display_duplicate", List.of(), false, values -> "komaedit display duplicate"),
                new ToolAction("display_group", List.of(), false, values -> "komaedit display group"),
                new ToolAction("display_ungroup", List.of(), false, values -> "komaedit display ungroup"),
                new ToolAction("display_reset", List.of(), false, values -> "komaedit display reset"),
                new ToolAction("display_delete", List.of(), false, values -> "komaedit display delete"));
    }

    /** Los pasos y el marco van al cliente: {@code /kedisplay ajustes 1/16 15 global}. */
    static String stepsCommand(ToolValues values) {
        return "kedisplay ajustes " + MOVE_STEPS.getOrDefault(values.choice("move_step"), "1/16") + " "
                + values.choice("angle_step") + " " + (values.toggle("local") ? "local" : "global");
    }

    /** Los ajustes avanzados a lo elegido, todos a la vez. */
    static String lookCommand(ToolValues values) {
        return "komaedit display set all " + look(values).format();
    }

    static DisplayLook look(ToolValues values) {
        String light = values.choice("brightness");
        Integer brightness = light.equals("auto") ? null : Integer.valueOf(light);
        int color = values.toggle("glow") ? DisplayText.color(values.choice("glow_color")) : -1;
        return new DisplayLook(brightness, (float) (values.number("shadow_radius", 0) / 10.0),
                (float) (values.number("shadow_strength", 100) / 100.0),
                (float) (values.number("view_range", 100) / 100.0),
                DisplayPose.Billboard.parse(values.choice("billboard")), values.toggle("glow"), color);
    }

    @Override
    public List<ToolProblem> validate(ToolValues values) {
        List<ToolProblem> problems = new ArrayList<>();
        if (text(values) && values.text("display_text").isBlank()) {
            problems.add(ToolProblem.error("text_empty"));
        }
        if (block(values)) {
            noSpaces(values, "block", problems);
        }
        return problems;
    }
}
