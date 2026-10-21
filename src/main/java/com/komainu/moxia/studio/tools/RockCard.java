package com.komainu.moxia.studio.tools;

import com.komainu.moxia.studio.ValueUnit;
import com.komainu.moxia.nature.RockSettings;
import com.komainu.moxia.nature.RockType;
import com.komainu.moxia.permission.KomaPermissions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@code /komaedit rock}: tipo, tamaño, forma, sombreado, musgo, semilla y presets guardados. */
public final class RockCard extends ToolCard {
    public static final String NODE = KomaPermissions.BUILD + ".rock";
    public static final String BRUSH_NODE = KomaPermissions.BUILD + ".brush.rock";

    RockCard() {
        super("rock", ToolTarget.PATH_OR_LOOK,
                List.of("rock", "roca", "piedra", "boulder", "crag", "spire", "penasco", "aguja", "menhir", "stone",
                        "pincel", "brush"),
                List.of(
                        use(ToolUse.CLICK, ToolUse.BRUSH),
                        new ToolParam.Choice("type", Arrays.stream(RockType.values()).map(RockType::id).toList(),
                                "boulder", ToolParam.Choice.Style.GALLERY, "", null),
                        auto("size", 10, (int) (RockSettings.MAX_SIZE * 10), 5, 20, 1, 40).unit(ValueUnit.BLOCKS),
                        auto("aspect", 20, 600, 5, 25, 2, 75).unit(ValueUnit.PERCENT),
                        auto("elongation", 100, 400, 5, 25, 2, 130).unit(ValueUnit.MULTIPLIER),
                        auto("roughness", 0, 100, 5, 25, 2, 45).unit(ValueUnit.PERCENT),
                        auto("cracks", 0, 100, 5, 25, 2, 40).unit(ValueUnit.PERCENT),
                        auto("facets", 0, 32, 1, 4, 0, 9),
                        auto("sink", 0, 90, 5, 10, 2, 25).unit(ValueUnit.PERCENT),
                        number("lean", 0, 60, 1, 5, 0).unit(ValueUnit.DEGREES),
                        seed(),
                        new ToolParam.Text("palette", "", 256, false, null),
                        decimal("moss", 0, 100, 5, 25, 0, 2).unit(ValueUnit.PERCENT),
                        auto("variation", 0, 100, 5, 25, 2, 15).unit(ValueUnit.PERCENT),
                        mask()
                ));
    }


    @Override
    public List<String> essentials() {
        return List.of(ToolUse.PARAM, "type", "size", "moss");
    }

    String settings(ToolValues values) {
        return new CommandLine(RockType.byName(values.choice("type")).id())
                .word(values.formatted("size"))
                .flag("-a", values.formatted("aspect"))
                .flag("-e", values.formatted("elongation"))
                .flag("-r", values.formatted("roughness"))
                .flag("-g", values.formatted("cracks"))
                .flag("-c", values.formatted("facets"))
                .flag("-h", values.formatted("sink"))
                .changed(values, "lean", "-i")
                .flag("-s", values.formatted("seed"))
                .flag("-b", values.text("palette"))
                .changed(values, "moss", "-u")
                .flag("-m", values.text("mask"))
                .flag("-x", values.formatted("variation"))
                .build();
    }

    @Override
    public String command(ToolValues values, boolean preview) {
        if (use(values) == ToolUse.BRUSH) {
            return "komaedit pincel rock " + settings(values);
        }
        return new CommandLine("komaedit rock " + settings(values)).flag(previewFlag(), preview).build();
    }

    @Override
    public List<String> permissions(ToolValues values) {
        return List.of(use(values) == ToolUse.BRUSH ? BRUSH_NODE : NODE);
    }

    @Override
    public java.util.Set<String> allPermissions() {
        return new java.util.LinkedHashSet<>(List.of(NODE, BRUSH_NODE));
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(
                new ToolAction("save_preset", List.of(NODE), true,
                        values -> "komaedit rock save " + NAME_PLACEHOLDER + " " + settings(values) + " -f"));
    }

    @Override
    public List<ToolPreset> presets() {
        return List.of(
                ToolPreset.of("pebble", "type", "boulder", "size", 20),
                ToolPreset.of("big_crag", "type", "crag", "size", 120, "elongation", 200),
                ToolPreset.of("menhir", "type", "spire", "size", 30, "aspect", 500),
                ToolPreset.of("mossy", "moss", 60));
    }

    @Override
    public String autoHint(String paramId, ToolValues values) {
        RockType type = RockType.byName(values.choice("type"));
        return switch (paramId) {
            case "size" -> format(type.size());
            case "aspect" -> format(type.aspect());
            case "elongation" -> format(type.elongation());
            case "roughness" -> format(type.roughness());
            case "cracks" -> format(type.cracks());
            case "facets" -> Integer.toString(type.facets());
            case "sink" -> format(type.sink());
            case "palette" -> RockSettings.DEFAULT_PALETTE;
            case "variation" -> "0.15";
            default -> null;
        };
    }

    @Override
    public List<ToolProblem> validate(ToolValues values) {
        List<ToolProblem> problems = new ArrayList<>();
        noSpaces(values, "palette", problems);
        noSpaces(values, "mask", problems);
        return problems;
    }

    @Override
    public boolean savedPresets() {
        return true;
    }

    @Override
    public ToolValues parsePreset(String text) {
        RockSettings settings = RockSettings.parse(text);
        RockType type = settings.type();
        ToolValues values = defaults().with("type", type.id());
        values = values.with("size", same(settings.size(), type.size()) ? null : scale(values, "size", settings.size()))
                .with("aspect", same(settings.aspect(), type.aspect()) ? null
                        : scale(values, "aspect", settings.aspect()))
                .with("elongation", same(settings.elongation(), type.elongation()) ? null
                        : scale(values, "elongation", settings.elongation()))
                .with("roughness", same(settings.roughness(), type.roughness()) ? null
                        : scale(values, "roughness", settings.roughness()))
                .with("cracks", same(settings.cracks(), type.cracks()) ? null
                        : scale(values, "cracks", settings.cracks()))
                .with("facets", settings.facets() == type.facets() ? null : settings.facets())
                .with("sink", same(settings.sink(), type.sink()) ? null : scale(values, "sink", settings.sink()))
                .with("lean", (int) Math.round(settings.lean()))
                .with("seed", settings.seed().isPresent() ? (int) Math.floorMod(settings.seed().getAsLong(),
                        1_000_000_000L) : null)
                .with("palette", settings.palette() == null ? "" : settings.palette())
                .with("moss", scale(values, "moss", settings.mossAmount()))
                .with("mask", settings.mask() == null ? "" : settings.mask())
                .with("variation", Double.isNaN(settings.variation()) ? null
                        : scale(values, "variation", settings.variation()));
        return values;
    }

    private static boolean same(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    private static int scale(ToolValues values, String id, double value) {
        return ((ToolParam.Number) values.param(id)).scale(value);
    }

    private static String format(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
