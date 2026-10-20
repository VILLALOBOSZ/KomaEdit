package com.komainu.moxia.studio.tools;

import com.komainu.moxia.permission.KomaPermissions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** {@code /komaedit loft}: superficie entre los puntos de la trayectoria o entre recorridos guardados. */
public final class LoftCard extends ToolCard {
    public static final String NODE = KomaPermissions.BUILD + ".loft";
    private static final Pattern CURVE = Pattern.compile("[a-z0-9_\\-]{1,32}");

    LoftCard() {
        super("loft", ToolFamily.ROOFS, ToolTarget.PATH_OR_SELECTION,
                List.of("loft", "lona", "toldo", "vela", "awning", "canopy", "sail", "puente colgante"),
                List.of(
                        block("material", "white_wool"),
                        choice("interpolation", ToolParam.Choice.Style.BUTTONS, "catmull", "lineal", "catenaria"),
                        when(values -> values.choice("interpolation").equals("catenaria"),
                                auto("sag", 0, 64, 1, 4, 0, 3)),
                        number("thickness", 1, 8, 1, 1, 1),
                        choice("profile", ToolParam.Choice.Style.CYCLE, "none", "SlabsAndStairs", "Slabs", "SSW"),
                        toggle("closed", false),
                        new ToolParam.Text("curves", "", 160, false, null),
                        when(values -> values.text("curves").isBlank(), number("count", 2, 6, 1, 1, 2))
                ));
    }

    private static List<String> curves(ToolValues values) {
        String text = values.text("curves").strip();
        return text.isEmpty() ? List.of() : List.of(text.split("\\s+"));
    }

    @Override
    public String command(ToolValues values, boolean preview) {
        CommandLine line = new CommandLine("komaedit loft");
        curves(values).forEach(line::word);
        line.word(values.block("material"));
        if (!values.isDefault("interpolation")) {
            line.flag("-i", values.choice("interpolation"));
        }
        if (values.param("sag").visible(values)) {
            line.flag("-comba", values.formatted("sag"));
        }
        line.changed(values, "thickness", "-g");
        if (!values.choice("profile").equals("none")) {
            line.flag("-w", values.choice("profile"));
        }
        line.flag("-cerrar", values.toggle("closed"));
        if (values.param("count").visible(values)) {
            line.changed(values, "count", "-n");
        }
        return line.flag(previewFlag(), preview).build();
    }

    @Override
    public List<String> permissions(ToolValues values) {
        return List.of(NODE);
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(new ToolAction("estimate", List.of(NODE), false, values -> command(values, false) + " -e"));
    }

    @Override
    public String autoHint(String paramId, ToolValues values) {
        return paramId.equals("sag") ? "15%" : null;
    }

    @Override
    public ToolProblem requirement(ToolValues values, ToolContext context) {
        if (!curves(values).isEmpty() || !context.known()) {
            return null;
        }
        int needed = 2 * values.number("count", 2);
        if (context.pathPoints() < needed && !context.hasSelection()) {
            return ToolProblem.error("loft_points", needed);
        }
        return null;
    }

    @Override
    public List<ToolProblem> validate(ToolValues values) {
        List<ToolProblem> problems = new ArrayList<>();
        if (values.block("material") == null) {
            problems.add(ToolProblem.error("material"));
        }
        noSpaces(values, "material", problems);
        List<String> curves = curves(values);
        for (String curve : curves) {
            if (!CURVE.matcher(curve).matches() && !curve.equals("actual")) {
                problems.add(ToolProblem.error("curve_name", curve));
            }
        }
        if (curves.size() == 1 || curves.size() > 6) {
            problems.add(ToolProblem.error("curve_count"));
        }
        return problems;
    }
}
