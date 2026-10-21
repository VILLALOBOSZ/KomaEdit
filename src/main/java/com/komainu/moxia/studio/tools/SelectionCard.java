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
 * {@code tool}); Mayús suma, Ctrl resta y Alt interseca con lo que ya había. Construir aplica la operación elegida a la
 * selección (ampliar, reducir, invertir, pasar a caja o quitar) y «Deshacer trazo» vuelve atrás un trazo.
 */
public final class SelectionCard extends ToolCard {
    public static final List<String> TOOLS = List.of("box", "lasso", "brush");
    public static final List<String> OPERATIONS = List.of("grow", "shrink", "complement", "box", "clear");

    SelectionCard() {
        super("select", ToolTarget.SELECTION,
                List.of("select", "seleccionar", "selección", "seleccion", "lasso", "lazo", "freehand", "mano alzada",
                        "pincel de selección", "invertir", "ampliar", "reducir", "grow", "shrink", "axiom"),
                List.of(
                        new ToolParam.Choice("tool", TOOLS, "lasso", ToolParam.Choice.Style.BUTTONS, "", null),
                        when(SelectionCard::lasso, choice("depth", ToolParam.Choice.Style.BUTTONS, "surface",
                                "layers", "through")),
                        when(values -> lasso(values) && values.choice("depth").equals("layers"),
                                number("layers", 1, LassoSettings.MAX_LAYERS, 1, 4, 3).unit(ValueUnit.BLOCKS)),
                        when(SelectionCard::brush, number("radius", 0, BrushShape.MAX_RADIUS, 1, 2, 2)
                                .unit(ValueUnit.BLOCKS)),
                        when(values -> brush(values) || resizing(values), choice("shape",
                                ToolParam.Choice.Style.BUTTONS, "sphere", "cube", "octahedron")),
                        when(values -> !values.choice("tool").equals("box"), toggle("non_solid", false)),
                        new ToolParam.Choice("operation", OPERATIONS, "grow", ToolParam.Choice.Style.BUTTONS, "",
                                null),
                        when(SelectionCard::resizing, number("amount", 1, 16, 1, 2, 1).unit(ValueUnit.BLOCKS)),
                        when(values -> resizing(values) || values.choice("operation").equals("complement"),
                                toggle("surface", true))
                ));
    }

    private static boolean lasso(ToolValues values) {
        return values.choice("tool").equals("lasso");
    }

    private static boolean brush(ToolValues values) {
        return values.choice("tool").equals("brush");
    }

    private static boolean resizing(ToolValues values) {
        String operation = values.choice("operation");
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
        boolean nonSolid = values.toggle("non_solid");
        LassoSettings lasso = new LassoSettings(LassoSettings.Depth.byId(values.choice("depth")),
                values.number("layers", 3), nonSolid);
        return new Settings(values.choice("tool"), lasso, values.number("radius", 2),
                BrushShape.byId(values.choice("shape")), nonSolid);
    }

    @Override
    public List<String> essentials() {
        return List.of("tool", "depth", "layers", "radius", "operation", "amount");
    }

    /** Construir: la operación elegida sobre la selección de ahora. */
    @Override
    public String command(ToolValues values, boolean preview) {
        String flags = (values.toggle("surface") ? " -s" : "") + (values.toggle("non_solid") ? " -n" : "");
        return switch (values.choice("operation")) {
            case "grow", "shrink" -> "komaedit sel " + values.choice("operation") + " " + values.formatted("amount")
                    + " " + values.choice("shape") + flags;
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
        return List.of(switch (values.choice("operation")) {
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
