package com.komainu.moxia.studio.tools;

import com.komainu.moxia.studio.ValueUnit;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.roof.RoofSettings;
import com.komainu.moxia.roof.RoofType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** {@code /komaedit roof}: tipo, material, pendiente, alero, buhardillas y el resto de banderas, sobre la selección. */
public final class RoofCard extends ToolCard {
    public static final String NODE = KomaPermissions.BUILD + ".roof";
    private static final Set<String> TALL = Set.of("dome", "cone", "spire", "pagoda");

    RoofCard() {
        super("roof", ToolFamily.ROOFS, ToolTarget.SELECTION,
                List.of("roof", "tejado", "techo", "gable", "hip", "dosaguas", "cupula", "mansarda", "tejas"),
                List.of(
                        new ToolParam.Choice("type", Arrays.stream(RoofType.values()).map(RoofType::id).toList(),
                                "gable", ToolParam.Choice.Style.GALLERY, "", null),
                        block("material", "spruce_planks"),
                        when(type(type -> type != RoofType.FLAT), choice("slope", ToolParam.Choice.Style.CYCLE,
                                "auto", "1:1", "1:2", "2:1", "1:3", "3:1", "30", "45", "60")),
                        auto("overhang", 0, RoofSettings.MAX_OVERHANG, 1, 2, 0, 1).unit(ValueUnit.BLOCKS),
                        number("thickness", 1, RoofSettings.MAX_THICKNESS, 1, 1, 1).unit(ValueUnit.BLOCKS),
                        when(type(type -> type != RoofType.FLAT), block("ridge", null)),
                        when(type(RoofType::allowsDormers), number("dormers", 0, RoofSettings.MAX_DORMERS, 1, 2, 0)),
                        when(values -> type(values).allowsDormers() && values.number("dormers", 0) > 0,
                                block("window", null)),
                        when(type(type -> !TALL.contains(type.id())), block("wall", null)),
                        when(type(RoofType::hasGables), toggle("open", false)),
                        when(type(type -> type == RoofType.SHED), choice("side", ToolParam.Choice.Style.BUTTONS,
                                "auto", "n", "s", "e", "o")),
                        when(type(type -> type == RoofType.SPIRE), auto("sides", 3, 32, 1, 4, 0, 8)),
                        when(type(type -> TALL.contains(type.id())), auto("height", 1, 128, 1, 5, 0, 8).unit(ValueUnit.BLOCKS)),
                        when(type(type -> type == RoofType.FLAT), auto("parapet", 0, 6, 1, 1, 0, 1).unit(ValueUnit.BLOCKS)),
                        when(type(type -> type == RoofType.MANSARD || type == RoofType.GABLET),
                                auto("break", 1, 32, 1, 2, 0, 2).unit(ValueUnit.BLOCKS)),
                        when(type(type -> type == RoofType.GABLE || type == RoofType.GABLET),
                                choice("axis", ToolParam.Choice.Style.BUTTONS, "auto", "x", "z")),
                        toggle("detect", false),
                        when(type(type -> type != RoofType.FLAT), block("stairs", null)),
                        when(type(type -> type != RoofType.FLAT), block("slabs", null)),
                        toggle("replace", false)
                ));
    }


    @Override

    public List<String> essentials() {

        return List.of("type", "material", "slope", "overhang");

    }

    private static Predicate<ToolValues> type(Predicate<RoofType> test) {
        return values -> test.test(type(values));
    }

    static RoofType type(ToolValues values) {
        return RoofType.byId(values.choice("type"));
    }

    @Override
    public String command(ToolValues values, boolean preview) {
        CommandLine line = new CommandLine("komaedit roof " + type(values).id()).word(values.block("material"));
        String slope = shown(values, "slope") ? values.choice("slope") : "auto";
        if (!slope.equals("auto")) {
            line.flag("-p", slope);
        }
        line.flag("-a", values.formatted("overhang")).changed(values, "thickness", "-g");
        flag(line, values, "ridge", "-c");
        if (shown(values, "dormers")) {
            line.changed(values, "dormers", "-b");
        }
        flag(line, values, "window", "-ventana");
        flag(line, values, "wall", "-m");
        line.flag("-abierto", shown(values, "open") && values.toggle("open"));
        if (shown(values, "side") && !values.choice("side").equals("auto")) {
            line.flag("-lado", values.choice("side"));
        }
        for (String[] number : new String[][]{{"sides", "-lados"}, {"height", "-h"}, {"parapet", "-pretil"},
                {"break", "-q"}}) {
            if (shown(values, number[0])) {
                line.flag(number[1], values.formatted(number[0]));
            }
        }
        if (shown(values, "axis") && !values.choice("axis").equals("auto")) {
            line.flag("-eje", values.choice("axis"));
        }
        line.flag("-d", values.toggle("detect"));
        flag(line, values, "stairs", "-escaleras");
        flag(line, values, "slabs", "-losas");
        return line.flag("-r", values.toggle("replace")).flag(previewFlag(), preview).build();
    }

    private static boolean shown(ToolValues values, String id) {
        return values.param(id).visible(values);
    }

    private static void flag(CommandLine line, ToolValues values, String id, String flag) {
        if (shown(values, id)) {
            line.flag(flag, values.block(id));
        }
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
    public List<ToolPreset> presets() {
        return List.of(
                ToolPreset.of("steep", "slope", "2:1"),
                ToolPreset.of("gentle", "slope", "1:2"),
                ToolPreset.of("deep_eaves", "overhang", 2, "thickness", 2),
                ToolPreset.of("with_dormers", "type", "gable", "dormers", 2));
    }

    @Override
    public String autoHint(String paramId, ToolValues values) {
        RoofType type = type(values);
        return switch (paramId) {
            case "overhang" -> type == RoofType.FLAT || type == RoofType.DOME ? "0" : "1";
            case "sides" -> "8";
            case "parapet" -> "1";
            case "window" -> "glass_pane";
            default -> null;
        };
    }

    @Override
    public List<ToolProblem> validate(ToolValues values) {
        List<ToolProblem> problems = new ArrayList<>();
        if (values.block("material") == null) {
            problems.add(ToolProblem.error("material"));
        }
        for (String id : List.of("material", "ridge", "window", "wall", "stairs", "slabs")) {
            noSpaces(values, id, problems);
        }
        return problems;
    }
}
