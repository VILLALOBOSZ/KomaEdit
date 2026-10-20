package com.komainu.moxia.studio.tools;

import com.komainu.moxia.nature.TreeSettings;
import com.komainu.moxia.nature.TreeSpecies;
import com.komainu.moxia.permission.KomaPermissions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@code /komaedit tree}: especie, altura, forma, materiales, semilla y presets guardados. */
public final class TreeCard extends ToolCard {
    public static final String NODE = KomaPermissions.BUILD + ".tree";
    public static final String BRUSH_NODE = KomaPermissions.BUILD + ".brush.tree";

    TreeCard() {
        super("tree", ToolFamily.NATURE, ToolTarget.PATH_OR_LOOK,
                List.of("tree", "arbol", "árbol", "bosque", "roble", "abeto", "palmera", "forest", "plant"),
                List.of(
                        new ToolParam.Choice("species", Arrays.stream(TreeSpecies.values()).map(TreeSpecies::id)
                                .toList(), "oak", ToolParam.Choice.Style.BUTTONS, "", null),
                        auto("height", TreeSettings.MIN_HEIGHT, TreeSettings.MAX_HEIGHT, 1, 5, 0, 18),
                        decimal("thickness", 25, 400, 5, 25, 100, 2),
                        decimal("branches", 0, 300, 10, 50, 100, 2),
                        decimal("density", 0, 200, 10, 50, 100, 2),
                        decimal("crown", 30, 300, 10, 50, 100, 2),
                        auto("lean", 0, 75, 1, 5, 0, 0),
                        auto("heading", 0, 345, 15, 45, 0, 0),
                        decimal("curvature", 0, 400, 10, 50, 100, 2),
                        seed(),
                        block("trunk", null),
                        block("leaves", null),
                        toggle("roots", true),
                        when(values -> values.toggle("roots"), block("root_block", null)),
                        auto("variation", 0, 100, 5, 25, 2, 15),
                        mask()
                ));
    }

    /** Lo que va detrás de {@code tree} (y de {@code pincel tree} y {@code tree save <nombre>}). */
    String settings(ToolValues values) {
        TreeSpecies species = TreeSpecies.byName(values.choice("species"));
        CommandLine line = new CommandLine(species.id());
        line.word(values.formatted("height"))
                .changed(values, "thickness", "-g")
                .changed(values, "branches", "-b")
                .changed(values, "density", "-d")
                .changed(values, "crown", "-w");
        Integer lean = values.number("lean");
        Integer heading = values.number("heading");
        if (lean != null || heading != null) {
            int degrees = lean != null ? lean : (int) Math.round(species.form().defaultLean());
            line.flag("-i", degrees + (heading != null ? "," + heading : ""));
        }
        line.changed(values, "curvature", "-c")
                .flag("-s", values.formatted("seed"))
                .flag("-t", values.block("trunk"))
                .flag("-h", values.block("leaves"))
                .flag("-r", values.toggle("roots") ? values.block("root_block") : "no")
                .flag("-m", values.text("mask"))
                .flag("-x", values.formatted("variation"));
        return line.build();
    }

    @Override
    public String command(ToolValues values, boolean preview) {
        return new CommandLine("komaedit tree " + settings(values)).flag(previewFlag(), preview).build();
    }

    @Override
    public List<String> permissions(ToolValues values) {
        return List.of(NODE);
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(
                new ToolAction("brush", List.of(BRUSH_NODE), false,
                        values -> "komaedit pincel tree " + settings(values)),
                new ToolAction("save_preset", List.of(NODE), true,
                        values -> "komaedit tree save " + NAME_PLACEHOLDER + " " + settings(values) + " -f"));
    }

    @Override
    public List<ToolPreset> presets() {
        return List.of(
                ToolPreset.of("young", "height", 9, "thickness", 70, "crown", 80, "branches", 80),
                ToolPreset.of("old", "thickness", 160, "crown", 130, "branches", 130, "curvature", 160),
                ToolPreset.of("windswept", "lean", 25, "curvature", 150, "crown", 90),
                ToolPreset.of("bare", "density", 0));
    }

    @Override
    public String autoHint(String paramId, ToolValues values) {
        TreeSpecies species = TreeSpecies.byName(values.choice("species"));
        return switch (paramId) {
            case "height" -> Integer.toString(species.defaultHeight());
            case "lean" -> Long.toString(Math.round(species.form().defaultLean()));
            case "trunk" -> species.trunk();
            case "leaves" -> species.leaves();
            case "root_block" -> values.block("trunk") == null ? species.trunk() : values.block("trunk");
            case "variation" -> "0.15";
            default -> null;
        };
    }

    @Override
    public List<ToolProblem> validate(ToolValues values) {
        List<ToolProblem> problems = new ArrayList<>();
        for (String id : List.of("trunk", "leaves", "root_block")) {
            noSpaces(values, id, problems);
        }
        noSpaces(values, "mask", problems);
        return problems;
    }

    @Override
    public boolean savedPresets() {
        return true;
    }

    /** Un preset guardado ({@code oak 22 -g 1.2 -h …}) en los valores de la tarjeta. */
    @Override
    public ToolValues parsePreset(String text) {
        TreeSettings settings = TreeSettings.parse(text);
        TreeSpecies species = settings.species();
        ToolValues values = defaults().with("species", species.id())
                .with("height", settings.height() == species.defaultHeight() ? null : settings.height())
                .with("thickness", scale(defaults(), "thickness", settings.thickness()))
                .with("branches", scale(defaults(), "branches", settings.branches()))
                .with("density", scale(defaults(), "density", settings.density()))
                .with("crown", scale(defaults(), "crown", settings.width()))
                .with("lean", Math.abs(settings.lean() - species.form().defaultLean()) < 0.5 ? null
                        : (int) Math.round(settings.lean()))
                .with("heading", Double.isNaN(settings.azimuth()) ? null : (int) Math.round(settings.azimuth()))
                .with("curvature", scale(defaults(), "curvature", settings.curvature()))
                .with("seed", settings.seed().isPresent() ? (int) Math.floorMod(settings.seed().getAsLong(),
                        1_000_000_000L) : null)
                .with("trunk", settings.trunk())
                .with("leaves", settings.leaves())
                .with("mask", settings.mask() == null ? "" : settings.mask())
                .with("variation", Double.isNaN(settings.variation()) ? null
                        : scale(defaults(), "variation", settings.variation()));
        String roots = settings.roots();
        boolean none = roots != null && settings.rootMaterial() == null;
        return values.with("roots", !none).with("root_block", none ? null : roots);
    }

    static int scale(ToolValues values, String id, double value) {
        return ((ToolParam.Number) values.param(id)).scale(value);
    }
}
