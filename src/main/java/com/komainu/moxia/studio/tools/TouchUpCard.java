package com.komainu.moxia.studio.tools;

import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.studio.ValueUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * «Retocar»: los arreglos sobre lo que ya hay, como modos de una sola tarjeta y de una sola orden
 * ({@code /komaedit retocar <modo>}): nevar, drenar, inundar, dejar caer, desinfestar, fijar las hojas, anegar,
 * encerar y cambiar el bioma. Antes eran cinco tarjetas (nieve, drenar, inundar, gravedad y bioma) y los cuatro
 * {@code tweak} solo tenían orden.
 *
 * <p>Sobre la selección o con el pincel ({@code /komaedit pincel retocar <radio> <modo>}), con los mismos ajustes;
 * el bioma con el pincel es el pincel de biomas de siempre ({@code pincel biome}).</p>
 */
public final class TouchUpCard extends ToolCard {
    /** El ajuste del modo: qué arreglo se hace. */
    public static final String MODE = "touch";
    public static final String SNOW = "nieve";
    public static final String DRAIN = "drenar";
    public static final String FLOOD = "inundar";
    public static final String GRAVITY = "gravedad";
    public static final String DEINFEST = "desinfestar";
    public static final String LEAVES = "hojas";
    public static final String WATERLOG = "anegar";
    public static final String WAX = "encerar";
    public static final String BIOME = "bioma";
    /** Los modos, en el orden de sus botones. */
    public static final List<String> MODES = List.of(SNOW, DRAIN, FLOOD, GRAVITY, DEINFEST, LEAVES, WATERLOG, WAX,
            BIOME);

    public static final String SNOW_NODE = KomaPermissions.BUILD + ".snow";
    public static final String DRAIN_NODE = KomaPermissions.BUILD + ".drain";
    public static final String FLOOD_NODE = KomaPermissions.BUILD + ".waterlog";
    public static final String GRAVITY_NODE = KomaPermissions.BUILD + ".gravity";
    public static final String BIOME_NODE = KomaPermissions.BUILD + ".biome";
    public static final String BIOME_BRUSH_NODE = KomaPermissions.BUILD + ".brush.biome";
    /** El pincel que repite una orden de la selección alrededor de cada clic ({@code /komaedit pincel zona}). */
    public static final String ZONE_NODE = KomaPermissions.BUILD + ".brush.zone";
    /** Los modos de {@code tweak} y su nombre en la orden de antes ({@code /komaedit tweak deinfest}). */
    static final Map<String, String> TWEAKS = Map.of(DEINFEST, "deinfest", LEAVES, "persist", WATERLOG, "waterlog",
            WAX, "wax");
    /** Lo que se ve con {@code -m}: los {@code tweak} no tienen máscara. */
    private static final Set<String> MASKED = Set.of(SNOW, DRAIN, FLOOD, GRAVITY, BIOME);
    private static final Pattern BIOME_ID = Pattern.compile("[a-z0-9_.\\-]+(:[a-z0-9_./\\-]+)?");

    TouchUpCard() {
        super("touchup", ToolTarget.SELECTION,
                List.of("retocar", "retoques", "touchup", "tweak", "survival", "nieve", "snow", "nevar", "invierno",
                        "drenar", "drain", "secar", "vaciar", "agua", "water", "lava", "inundar", "waterlog", "flood",
                        "nivel", "gravedad", "gravity", "caer", "arena", "desinfestar", "deinfest", "hojas",
                        "leaves", "persist", "anegar", "encerar", "wax", "cera", "cobre", "bioma", "biome", "clima"),
                List.of(
                        use(ToolUse.SELECTION, ToolUse.BRUSH),
                        new ToolParam.Choice(MODE, MODES, SNOW, ToolParam.Choice.Style.BUTTONS, "", null),
                        only(ToolUse.BRUSH, number("radius", 1, 32, 1, 4, 8).unit(ValueUnit.BLOCKS)),
                        // Nevar.
                        in(SNOW, number("layers", 1, 8, 1, 2, 2).unit(ValueUnit.LAYERS)),
                        in(SNOW, toggle("plants", false)),
                        in(SNOW, number("drift", 0, 16, 1, 2, 2).unit(ValueUnit.BLOCKS)),
                        in(SNOW, decimal("fill", 0, 10, 1, 5, 10, 1).unit(ValueUnit.PERCENT)),
                        in(SNOW, number("shade", 0, 8, 1, 2, 2).unit(ValueUnit.LAYERS)),
                        in(SNOW, number("height", 0, 8, 1, 2, 0).unit(ValueUnit.LAYERS)),
                        // Drenar.
                        in(DRAIN, choice("fluid", ToolParam.Choice.Style.BUTTONS, "water", "lava", "all")),
                        in(DRAIN, toggle("keep_plants", false)),
                        // Inundar.
                        in(FLOOD, choice("level", ToolParam.Choice.Style.BUTTONS, "top", "feet", "fixed")),
                        in(FLOOD, when(values -> values.choice("level").equals("fixed"),
                                number("level_y", -2_048, 2_047, 1, 8, 62).unit(ValueUnit.Y))),
                        in(FLOOD, when(values -> values.choice("level").equals("feet"),
                                number("offset", -16, 16, 1, 4, 0).unit(ValueUnit.BLOCKS))),
                        in(FLOOD, toggle("blocks_only", false)),
                        // Dejar caer.
                        in(GRAVITY, toggle("unsupported", false)),
                        // Los retoques de supervivencia: encender o quitar.
                        in(LEAVES, choice("leaves", ToolParam.Choice.Style.BUTTONS, "on", "off")),
                        in(WATERLOG, choice("wet", ToolParam.Choice.Style.BUTTONS, "on", "off")),
                        in(WAX, choice("wax", ToolParam.Choice.Style.BUTTONS, "on", "off")),
                        // Cambiar el bioma.
                        in(BIOME, new ToolParam.Choice("biome", List.of(), "minecraft:plains",
                                ToolParam.Choice.Style.SEARCH, "biomes", null)),
                        in(BIOME, toggle("vertical", false)),
                        when(values -> MASKED.contains(values.choice(MODE)), mask())
                ));
    }

    /** El ajuste, visible solo en el modo {@code mode}. */
    private static ToolParam in(String mode, ToolParam param) {
        return when(values -> values.choice(MODE).equals(mode), param);
    }

    /** Qué retocar va primero: los presets y los ajustes que salen son los de ese modo. */
    @Override
    public String modeParam() {
        return MODE;
    }

    @Override
    public List<String> essentials() {
        return List.of(ToolUse.PARAM, MODE, "radius", "layers", "plants", "fluid", "keep_plants", "level", "level_y",
                "offset", "unsupported", "leaves", "wet", "wax", "biome");
    }

    /** Lo que va detrás de {@code retocar} (y de {@code pincel retocar <radio>}): el modo y sus ajustes. */
    static String settings(ToolValues values) {
        String mode = values.choice(MODE);
        CommandLine line = new CommandLine(mode);
        switch (mode) {
            case SNOW -> line.word(values.formatted("layers"))
                    .changed(values, "drift", "-r")
                    .changed(values, "fill", "-f")
                    .changed(values, "shade", "-s")
                    .changed(values, "height", "-h")
                    .flag("-p", values.toggle("plants"));
            case DRAIN -> line.word(values.choice("fluid")).flag("-k", values.toggle("keep_plants"));
            case FLOOD -> line.word(level(values)).flag("-b", values.toggle("blocks_only"));
            case GRAVITY -> line.flag("-a", values.toggle("unsupported"));
            case LEAVES -> line.word(values.choice("leaves"));
            case WATERLOG -> line.word(values.choice("wet"));
            case WAX -> line.word(values.choice("wax"));
            case BIOME -> line.word(biome(values)).flag("-v", values.toggle("vertical"));
            default -> {
            }
        }
        if (values.param("mask").visible(values)) {
            line.flag("-m", values.text("mask"));
        }
        return line.build();
    }

    /** El nivel tal como lo lee la orden: nada (lo alto de la selección o del clic), {@code ~}, {@code ~-2} o una Y. */
    static String level(ToolValues values) {
        return switch (values.choice("level")) {
            case "feet" -> {
                int offset = values.number("offset", 0);
                yield offset == 0 ? "~" : "~" + offset;
            }
            case "fixed" -> values.formatted("level_y");
            default -> null;
        };
    }

    static String biome(ToolValues values) {
        String id = values.choice("biome");
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    @Override
    public String command(ToolValues values, boolean preview) {
        String mode = values.choice(MODE);
        if (use(values) == ToolUse.BRUSH) {
            if (mode.equals(BIOME)) {
                // El pincel de biomas de siempre: pinta una esfera y el clic izquierdo coge el bioma del bloque.
                return new CommandLine("komaedit pincel biome " + biome(values))
                        .word(values.formatted("radius"))
                        .flag("-v", values.toggle("vertical"))
                        .flag("-m", values.text("mask"))
                        .build();
            }
            return "komaedit pincel retocar " + values.formatted("radius") + " " + settings(values);
        }
        return new CommandLine("komaedit retocar " + settings(values))
                .flag(previewFlag(), preview && previewable(values))
                .build();
    }

    /** El botón dice lo que hace el modo elegido («Nevar», «Drenar»…); con el pincel, «Al pincel». */
    @Override
    public String buildKey(ToolValues values) {
        return use(values) == ToolUse.BRUSH ? BRUSH_BUILD_KEY : nameKey() + ".build." + values.choice(MODE);
    }

    /** El cambio de bioma no pasa por el motor de bloques: no hay vista previa. */
    @Override
    public boolean previewable(ToolValues values) {
        return super.previewable(values) && !values.choice(MODE).equals(BIOME);
    }

    @Override
    public List<String> permissions(ToolValues values) {
        String mode = values.choice(MODE);
        boolean brush = use(values) == ToolUse.BRUSH;
        if (mode.equals(BIOME)) {
            return List.of(brush ? BIOME_BRUSH_NODE : BIOME_NODE);
        }
        String node = node(mode);
        return brush ? List.of(ZONE_NODE, node) : List.of(node);
    }

    /** El nodo de cada modo (el de la orden de antes). */
    static String node(String mode) {
        return switch (mode) {
            case SNOW -> SNOW_NODE;
            case DRAIN -> DRAIN_NODE;
            case FLOOD -> FLOOD_NODE;
            case GRAVITY -> GRAVITY_NODE;
            case BIOME -> BIOME_NODE;
            default -> KomaPermissions.BUILD + ".tweak." + TWEAKS.get(mode);
        };
    }

    @Override
    public java.util.Set<String> allPermissions() {
        java.util.Set<String> nodes = new java.util.LinkedHashSet<>();
        MODES.forEach(mode -> nodes.add(node(mode)));
        nodes.add(BIOME_BRUSH_NODE);
        nodes.add(ZONE_NODE);
        return nodes;
    }

    @Override
    public List<ToolPreset> presets() {
        return List.of(
                ToolPreset.of("dusting", MODE, SNOW, "layers", 1, "drift", 0, "shade", 0),
                ToolPreset.of("drifts", MODE, SNOW, "layers", 3, "drift", 4, "shade", 4, "plants", true),
                ToolPreset.of("peaks", MODE, SNOW, "layers", 1, "height", 6),
                ToolPreset.of("dry_all", MODE, DRAIN, "fluid", "all"),
                ToolPreset.of("sea_level", MODE, FLOOD, "level", "fixed", "level_y", 62));
    }

    /** Los presets del modo elegido: los de nieve no tienen sentido mientras se drena. */
    @Override
    public List<ToolPreset> presets(ToolValues values) {
        String mode = values.choice(MODE);
        return presets().stream().filter(preset -> mode.equals(preset.values().get(MODE))).toList();
    }

    @Override
    public List<ToolProblem> validate(ToolValues values) {
        List<ToolProblem> problems = new ArrayList<>();
        if (values.choice(MODE).equals(BIOME) && !BIOME_ID.matcher(values.choice("biome")).matches()) {
            problems.add(ToolProblem.error("biome", values.choice("biome")));
        }
        if (values.param("mask").visible(values)) {
            noSpaces(values, "mask", problems);
        }
        return problems;
    }
}
