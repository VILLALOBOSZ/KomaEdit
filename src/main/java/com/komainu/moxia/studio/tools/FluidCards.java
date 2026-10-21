package com.komainu.moxia.studio.tools;

import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.studio.ValueUnit;

import java.util.ArrayList;
import java.util.List;

/** Agua y lava: rellenar un hueco cerrado, la bola de fluido, cascadas y ríos cortos y quitar flujos sueltos. */
public final class FluidCards {
    public static final String FILL_NODE = KomaPermissions.BUILD + ".fluid.fill";
    public static final String BALL_NODE = KomaPermissions.BUILD + ".fluid.ball";
    public static final String FALL_NODE = KomaPermissions.BUILD + ".fluid.fall";
    public static final String CLEAN_NODE = KomaPermissions.BUILD + ".fluid.clean";

    private FluidCards() {
    }

    /** {@code /komaedit fluido rellenar}: llena el hueco del clic si está cerrado; si no, señala por dónde se va. */
    public static final class Fill extends ToolCard {
        Fill() {
            super("fluid_fill", ToolFamily.UTILITIES, ToolTarget.LOOK,
                    List.of("rellenar", "fill", "floodfill", "llenar", "agua", "water", "lava", "lago", "lake",
                            "piscina", "pool", "estanque", "pond", "hueco", "fluido"),
                    List.of(
                            choice("fluid", ToolParam.Choice.Style.BUTTONS, "agua", "lava"),
                            choice("fill_level", ToolParam.Choice.Style.BUTTONS, "clic", "borde", "fijo"),
                            when(values -> values.choice("fill_level").equals("clic"),
                                    number("fill_offset", -16, 16, 1, 4, 0).unit(ValueUnit.BLOCKS)),
                            when(values -> values.choice("fill_level").equals("fijo"),
                                    number("level_y", -2_048, 2_047, 1, 8, 62).unit(ValueUnit.Y)),
                            toggle("waterlog", false),
                            toggle("spread_sides", true),
                            toggle("spread_down", true),
                            toggle("corners", false),
                            number("reach", 4, 128, 4, 16, 32).unit(ValueUnit.BLOCKS),
                            number("limit", 100, 1_000_000, 1_000, 10_000, 50_000).unit(ValueUnit.BLOCKS)
                    ));
        }

        @Override
        public List<String> essentials() {
            return List.of("fluid", "fill_level", "fill_offset", "level_y");
        }

        /** El nivel como lo lee la orden: nada (el del clic), {@code clic+2}, {@code borde} o una altura. */
        static String level(ToolValues values) {
            return switch (values.choice("fill_level")) {
                case "borde" -> "borde";
                case "fijo" -> values.formatted("level_y");
                default -> {
                    int offset = values.number("fill_offset", 0);
                    yield offset == 0 ? null : offset > 0 ? "clic+" + offset : "clic" + offset;
                }
            };
        }

        /** Las direcciones de {@code -d}, o {@code null} si son las de serie (de lado y hacia abajo). */
        static String directions(ToolValues values) {
            String letters = (values.toggle("spread_sides") ? "h" : "") + (values.toggle("spread_down") ? "b" : "")
                    + (values.toggle("corners") ? "e" : "");
            return letters.equals("hb") ? null : letters;
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            return new CommandLine("komaedit fluido rellenar " + values.choice("fluid"))
                    .word(level(values))
                    .flag("-d", directions(values))
                    .changed(values, "limit", "-l")
                    .changed(values, "reach", "-r")
                    .flag("-w", values.toggle("waterlog") && values.choice("fluid").equals("agua"))
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(FILL_NODE);
        }

        @Override
        public List<ToolPreset> presets() {
            return List.of(
                    ToolPreset.of("fill_brim", "fluid", "agua", "fill_level", "borde", "waterlog", true),
                    ToolPreset.of("fill_layer", "fill_level", "clic", "spread_down", false),
                    ToolPreset.of("fill_lava", "fluid", "lava", "fill_level", "borde"));
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            if (!values.toggle("spread_sides") && !values.toggle("spread_down")) {
                problems.add(ToolProblem.error("fluid_directions"));
            }
            return problems;
        }
    }

    /** {@code /komaedit fluido bola}: una esfera de agua, lava o nieve en polvo en el clic. */
    public static final class Ball extends ToolCard {
        Ball() {
            super("fluid_ball", ToolFamily.UTILITIES, ToolTarget.LOOK,
                    List.of("bola", "ball", "fluidball", "esfera", "sphere", "agua", "water", "lava", "nieve",
                            "powder", "polvo", "fluido"),
                    List.of(
                            choice("fluid_kind", ToolParam.Choice.Style.BUTTONS, "agua", "lava", "nieve"),
                            decimal("radius", 5, 320, 5, 10, 40, 1).unit(ValueUnit.BLOCKS),
                            toggle("air_only", true),
                            toggle("close_edges", true),
                            toggle("sources_only", false)
                    ));
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            return new CommandLine("komaedit fluido bola " + values.choice("fluid_kind"))
                    .word(values.formatted("radius"))
                    .flag("-s", values.toggle("sources_only"))
                    .flag("-a", values.toggle("air_only"))
                    .flag("-c", values.toggle("close_edges"))
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> essentials() {
            return List.of("fluid_kind", "radius", "air_only", "close_edges");
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(BALL_NODE);
        }

        @Override
        public List<ToolPreset> presets() {
            return List.of(
                    ToolPreset.of("ball_pond", "air_only", true, "close_edges", true, "sources_only", false),
                    ToolPreset.of("ball_carve", "air_only", false, "close_edges", true, "sources_only", false),
                    ToolPreset.of("ball_snow", "fluid_kind", "nieve", "air_only", true));
        }
    }

    /** {@code /komaedit fluido cascada}: desde el borde que se pulsa o, con puntos, un río corto con sus saltos. */
    public static final class Waterfall extends ToolCard {
        Waterfall() {
            super("waterfall", ToolFamily.UTILITIES, ToolTarget.PATH_OR_LOOK,
                    List.of("cascada", "waterfall", "caida", "caída", "salto", "rio", "río", "arroyo", "stream",
                            "river", "agua", "water", "lava", "poza", "fluido"),
                    List.of(
                            choice("fluid", ToolParam.Choice.Style.BUTTONS, "agua", "lava"),
                            toggle("pool", true),
                            when(values -> values.toggle("pool"),
                                    number("pool_radius", 0, 4, 1, 1, 2).unit(ValueUnit.BLOCKS)),
                            number("width", 1, 7, 2, 2, 1).unit(ValueUnit.BLOCKS),
                            toggle("banks", true),
                            number("margin", 0, 16, 1, 4, 2).unit(ValueUnit.BLOCKS)
                    ));
        }

        @Override
        public List<String> essentials() {
            return List.of("fluid", "pool", "pool_radius", "width");
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            CommandLine line = new CommandLine("komaedit fluido cascada " + values.choice("fluid"));
            if (values.toggle("pool")) {
                line.changed(values, "pool_radius", "-o");
            } else {
                line.flag("-n", true);
            }
            return line.changed(values, "width", "-a")
                    .flag("-b", !values.toggle("banks"))
                    .changed(values, "margin", "-r")
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(FALL_NODE);
        }

        @Override
        public List<ToolPreset> presets() {
            return List.of(
                    ToolPreset.of("fall_spring", "width", 1, "pool", true, "pool_radius", 1),
                    ToolPreset.of("fall_wide", "width", 3, "pool", true, "pool_radius", 3),
                    ToolPreset.of("fall_lava", "fluid", "lava", "pool", true, "pool_radius", 1));
        }
    }

    /** {@code /komaedit fluido limpiar}: quita de la selección las corrientes que no salen de ninguna fuente. */
    public static final class Loose extends ToolCard {
        Loose() {
            super("loose_flows", ToolFamily.UTILITIES, ToolTarget.SELECTION,
                    List.of("sueltos", "limpiar", "clean", "flujos", "flows", "corrientes", "agua", "water", "lava",
                            "fluido", "derrame"),
                    List.of(choice("loose_target", ToolParam.Choice.Style.BUTTONS, "todo", "agua", "lava"), mask()));
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            return new CommandLine("komaedit fluido limpiar " + values.choice("loose_target"))
                    .flag("-m", values.text("mask"))
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(CLEAN_NODE);
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            noSpaces(values, "mask", problems);
            return problems;
        }
    }
}
