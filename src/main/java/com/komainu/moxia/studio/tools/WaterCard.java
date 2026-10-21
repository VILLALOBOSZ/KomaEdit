package com.komainu.moxia.studio.tools;

import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.studio.ValueUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * «Poner agua o lava»: lo que pone líquido donde haces clic, como modos de una tarjeta y de una orden
 * ({@code /komaedit fluido <modo>}): rellenar un hueco cerrado, la bola de fluido y la cascada (con puntos, un río
 * corto). Quitar los flujos sueltos es un arreglo de lo que ya hay: va en {@link TouchUpCard}.
 */
public final class WaterCard extends ToolCard {
    /** El ajuste del modo: qué se pone. */
    public static final String MODE = "pour";
    public static final String FILL = "rellenar";
    public static final String BALL = "bola";
    public static final String FALL = "cascada";
    /** Los modos, en el orden de sus botones. */
    public static final List<String> MODES = List.of(FILL, BALL, FALL);

    public static final String FILL_NODE = KomaPermissions.BUILD + ".fluid.fill";
    public static final String BALL_NODE = KomaPermissions.BUILD + ".fluid.ball";
    public static final String FALL_NODE = KomaPermissions.BUILD + ".fluid.fall";
    /** Los modos que eligen agua o lava (la bola puede ser también de nieve en polvo). */
    private static final Set<String> LIQUID = Set.of(FILL, FALL);

    WaterCard() {
        super("water", ToolTarget.LOOK,
                List.of("agua", "water", "lava", "fluido", "fluid", "rellenar", "fill", "floodfill", "llenar", "lago",
                        "lake", "piscina", "pool", "estanque", "pond", "hueco", "bola", "ball", "fluidball", "esfera",
                        "nieve", "powder", "cascada", "waterfall", "caida", "salto", "rio", "arroyo", "stream",
                        "poza"),
                List.of(
                        new ToolParam.Choice(MODE, MODES, FILL, ToolParam.Choice.Style.BUTTONS, "", null),
                        when(values -> LIQUID.contains(values.choice(MODE)),
                                choice("liquid", ToolParam.Choice.Style.BUTTONS, "agua", "lava")),
                        // Rellenar.
                        in(FILL, choice("fill_level", ToolParam.Choice.Style.BUTTONS, "clic", "borde", "fijo")),
                        in(FILL, when(values -> values.choice("fill_level").equals("clic"),
                                number("fill_offset", -16, 16, 1, 4, 0).unit(ValueUnit.BLOCKS))),
                        in(FILL, when(values -> values.choice("fill_level").equals("fijo"),
                                number("level_y", -2_048, 2_047, 1, 8, 62).unit(ValueUnit.Y))),
                        in(FILL, toggle("waterlog", false)),
                        in(FILL, toggle("spread_sides", true)),
                        in(FILL, toggle("spread_down", true)),
                        in(FILL, toggle("corners", false)),
                        in(FILL, number("reach", 4, 128, 4, 16, 32).unit(ValueUnit.BLOCKS)),
                        in(FILL, number("limit", 100, 1_000_000, 1_000, 10_000, 50_000).unit(ValueUnit.BLOCKS)),
                        // La bola.
                        in(BALL, choice("fluid_kind", ToolParam.Choice.Style.BUTTONS, "agua", "lava", "nieve")),
                        in(BALL, decimal("radius", 5, 320, 5, 10, 40, 1).unit(ValueUnit.BLOCKS)),
                        in(BALL, toggle("close_edges", true)),
                        in(BALL, toggle("air_only", true)),
                        in(BALL, toggle("sources_only", false)),
                        // La cascada.
                        in(FALL, toggle("pool", true)),
                        in(FALL, when(values -> values.toggle("pool"),
                                number("pool_radius", 0, 4, 1, 1, 2).unit(ValueUnit.BLOCKS))),
                        in(FALL, number("width", 1, 7, 2, 2, 1).unit(ValueUnit.BLOCKS)),
                        in(FALL, toggle("banks", true)),
                        in(FALL, number("margin", 0, 16, 1, 4, 2).unit(ValueUnit.BLOCKS))
                ));
    }

    /** El ajuste, visible solo en el modo {@code mode}. */
    private static ToolParam in(String mode, ToolParam param) {
        return when(values -> values.choice(MODE).equals(mode), param);
    }

    /** Qué se pone va primero: los presets y los ajustes que salen son los de ese modo. */
    @Override
    public String modeParam() {
        return MODE;
    }

    /** La cascada sigue los puntos de la trayectoria si los hay; el relleno y la bola, el clic. */
    @Override
    protected ToolTarget modeTarget(ToolValues values) {
        return values.choice(MODE).equals(FALL) ? ToolTarget.PATH_OR_LOOK : ToolTarget.LOOK;
    }

    @Override
    public List<String> essentials() {
        return List.of(MODE, "liquid", "fill_level", "fill_offset", "level_y", "fluid_kind", "radius", "close_edges",
                "pool", "pool_radius");
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
        CommandLine line = switch (values.choice(MODE)) {
            case BALL -> new CommandLine("komaedit fluido bola " + values.choice("fluid_kind"))
                    .word(values.formatted("radius"))
                    .flag("-s", values.toggle("sources_only"))
                    .flag("-a", values.toggle("air_only"))
                    .flag("-c", values.toggle("close_edges"));
            case FALL -> {
                CommandLine fall = new CommandLine("komaedit fluido cascada " + values.choice("liquid"));
                if (values.toggle("pool")) {
                    fall.changed(values, "pool_radius", "-o");
                } else {
                    fall.flag("-n", true);
                }
                yield fall.changed(values, "width", "-a")
                        .flag("-b", !values.toggle("banks"))
                        .changed(values, "margin", "-r");
            }
            default -> new CommandLine("komaedit fluido rellenar " + values.choice("liquid"))
                    .word(level(values))
                    .flag("-d", directions(values))
                    .changed(values, "limit", "-l")
                    .changed(values, "reach", "-r")
                    .flag("-w", values.toggle("waterlog") && values.choice("liquid").equals("agua"));
        };
        return line.flag(previewFlag(), preview).build();
    }

    /** El botón dice lo que hace el modo elegido («Rellenar», «Poner bola», «Hacer cascada»). */
    @Override
    public String buildKey(ToolValues values) {
        return nameKey() + ".build." + values.choice(MODE);
    }

    @Override
    public List<String> permissions(ToolValues values) {
        return List.of(node(values.choice(MODE)));
    }

    static String node(String mode) {
        return switch (mode) {
            case BALL -> BALL_NODE;
            case FALL -> FALL_NODE;
            default -> FILL_NODE;
        };
    }

    @Override
    public Set<String> allPermissions() {
        return new java.util.LinkedHashSet<>(List.of(FILL_NODE, BALL_NODE, FALL_NODE));
    }

    @Override
    public List<ToolPreset> presets() {
        return List.of(
                ToolPreset.of("fill_brim", MODE, FILL, "liquid", "agua", "fill_level", "borde", "waterlog", true),
                ToolPreset.of("fill_layer", MODE, FILL, "fill_level", "clic", "spread_down", false),
                ToolPreset.of("fill_lava", MODE, FILL, "liquid", "lava", "fill_level", "borde"),
                ToolPreset.of("ball_pond", MODE, BALL, "air_only", true, "close_edges", true, "sources_only", false),
                ToolPreset.of("ball_carve", MODE, BALL, "air_only", false, "close_edges", true, "sources_only", false),
                ToolPreset.of("ball_snow", MODE, BALL, "fluid_kind", "nieve", "air_only", true),
                ToolPreset.of("fall_spring", MODE, FALL, "width", 1, "pool", true, "pool_radius", 1),
                ToolPreset.of("fall_wide", MODE, FALL, "width", 3, "pool", true, "pool_radius", 3),
                ToolPreset.of("fall_lava", MODE, FALL, "liquid", "lava", "pool", true, "pool_radius", 1));
    }

    /** Los presets del modo elegido: los de la bola no tienen sentido mientras se rellena. */
    @Override
    public List<ToolPreset> presets(ToolValues values) {
        String mode = values.choice(MODE);
        return presets().stream().filter(preset -> mode.equals(preset.values().get(MODE))).toList();
    }

    @Override
    public List<ToolProblem> validate(ToolValues values) {
        List<ToolProblem> problems = new ArrayList<>();
        if (values.choice(MODE).equals(FILL) && !values.toggle("spread_sides") && !values.toggle("spread_down")) {
            problems.add(ToolProblem.error("fluid_directions"));
        }
        return problems;
    }
}
