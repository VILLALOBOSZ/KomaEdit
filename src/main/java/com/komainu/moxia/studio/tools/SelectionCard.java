package com.komainu.moxia.studio.tools;

import com.komainu.moxia.selection.BrushShape;
import com.komainu.moxia.selection.LassoSettings;
import com.komainu.moxia.selection.SelectionToolService;
import com.komainu.moxia.studio.ValueUnit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Seleccionar a mano, como en Axiom: con el ratón en el mundo, una caja, un lazo o un pincel (lo elige
 * {@link #TOOL}); Mayús suma, Ctrl resta y Alt interseca con lo que ya había. Construir aplica la operación elegida a la
 * selección (ampliar, reducir, invertir, pasar a caja o quitar) y «Deshacer trazo» vuelve atrás un trazo.
 */
public final class SelectionCard extends ToolCard {
    /*
     * Los ids de los ajustes llevan el prefijo de la tarjeta: la etiqueta (gui.komaedit.tools.param.<id>) y las
     * opciones (gui.komaedit.tools.option.<id>.<opción>) se comparten entre tarjetas, y «superficie» o «forma» ya
     * significan otra cosa en otras.
     */
    public static final String TOOL = "select_tool";
    public static final String DEPTH = "select_depth";
    public static final String LAYERS = "select_layers";
    public static final String RADIUS = "select_radius";
    public static final String SHAPE = "select_shape";
    public static final String NON_SOLID = "select_non_solid";
    public static final String OPERATION = "select_operation";
    public static final String AMOUNT = "select_amount";
    public static final String SURFACE = "select_surface";
    public static final List<String> TOOLS = List.of("box", "lasso", "brush");
    public static final List<String> OPERATIONS = List.of("grow", "shrink", "complement", "box", "clear");

    SelectionCard() {
        super("select", ToolTarget.SELECTION,
                List.of("select", "seleccionar", "selección", "seleccion", "lasso", "lazo", "freehand", "mano alzada",
                        "pincel de selección", "invertir", "ampliar", "reducir", "grow", "shrink", "axiom"),
                List.of(
                        new ToolParam.Choice(TOOL, TOOLS, "lasso", ToolParam.Choice.Style.BUTTONS, "", null),
                        when(SelectionCard::lasso, choice(DEPTH, ToolParam.Choice.Style.BUTTONS, "surface",
                                "layers", "through")),
                        when(values -> lasso(values) && values.choice(DEPTH).equals("layers"),
                                number(LAYERS, 1, LassoSettings.MAX_LAYERS, 1, 4, 3).unit(ValueUnit.BLOCKS)),
                        when(SelectionCard::brush, number(RADIUS, 0, BrushShape.MAX_RADIUS, 1, 2, 2)
                                .unit(ValueUnit.BLOCKS)),
                        when(values -> brush(values) || resizing(values), choice(SHAPE,
                                ToolParam.Choice.Style.BUTTONS, "sphere", "cube", "octahedron")),
                        when(values -> !values.choice(TOOL).equals("box"), toggle(NON_SOLID, false)),
                        new ToolParam.Choice(OPERATION, OPERATIONS, "grow", ToolParam.Choice.Style.BUTTONS, "",
                                null),
                        when(SelectionCard::resizing, number(AMOUNT, 1, 16, 1, 2, 1).unit(ValueUnit.BLOCKS)),
                        when(values -> resizing(values) || values.choice(OPERATION).equals("complement"),
                                toggle(SURFACE, true))
                ));
    }

    private static boolean lasso(ToolValues values) {
        return values.choice(TOOL).equals("lasso");
    }

    private static boolean brush(ToolValues values) {
        return values.choice(TOOL).equals("brush");
    }

    private static boolean resizing(ToolValues values) {
        String operation = values.choice(OPERATION);
        return operation.equals("grow") || operation.equals("shrink");
    }

    /** Lo que necesita la pantalla para el lazo y el pincel: la herramienta del ratón y sus ajustes. */
    public record Settings(String tool, LassoSettings lasso, int radius, BrushShape shape, boolean nonSolid) {
        public boolean box() {
            return tool.equals("box");
        }

        public boolean lassoTool() {
            return tool.equals("lasso");
        }

        public boolean brushTool() {
            return tool.equals("brush");
        }
    }

    public static Settings settings(ToolValues values) {
        boolean nonSolid = values.toggle(NON_SOLID);
        LassoSettings lasso = new LassoSettings(LassoSettings.Depth.byId(values.choice(DEPTH)),
                values.number(LAYERS, 3), nonSolid);
        return new Settings(values.choice(TOOL), lasso, values.number(RADIUS, 2),
                BrushShape.byId(values.choice(SHAPE)), nonSolid);
    }

    @Override
    public List<String> essentials() {
        return List.of(TOOL, DEPTH, LAYERS, RADIUS, OPERATION, AMOUNT);
    }

    /** Construir: la operación elegida sobre la selección de ahora. */
    @Override
    public String command(ToolValues values, boolean preview) {
        String flags = (values.toggle(SURFACE) ? " -s" : "") + (values.toggle(NON_SOLID) ? " -n" : "");
        return switch (values.choice(OPERATION)) {
            case "grow", "shrink" -> "komaedit sel " + values.choice(OPERATION) + " " + values.formatted(AMOUNT)
                    + " " + values.choice(SHAPE) + flags;
            case "complement" -> "komaedit sel complement" + flags;
            case "box" -> "komaedit sel box";
            default -> "komaedit sel clear";
        };
    }

    @Override
    public boolean previewable() {
        return false;
    }

    @Override
    public List<String> permissions(ToolValues values) {
        return List.of(switch (values.choice(OPERATION)) {
            case "grow" -> SelectionToolService.GROW;
            case "shrink" -> SelectionToolService.SHRINK;
            case "complement" -> SelectionToolService.COMPLEMENT;
            case "box" -> SelectionToolService.BOX;
            default -> SelectionToolService.CLEAR;
        });
    }

    @Override
    public Set<String> allPermissions() {
        return new LinkedHashSet<>(List.of(SelectionToolService.LASSO, SelectionToolService.BRUSH,
                SelectionToolService.GROW, SelectionToolService.SHRINK, SelectionToolService.COMPLEMENT,
                SelectionToolService.BOX, SelectionToolService.CLEAR, SelectionToolService.UNDO));
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(new ToolAction("select_undo", List.of(SelectionToolService.UNDO), false,
                values -> "komaedit sel undo"));
    }
}
