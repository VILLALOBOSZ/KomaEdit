package com.komainu.moxia.studio.tools;

import com.komainu.moxia.permission.KomaPermissions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Utilidades de obra: texto, lista de materiales, gravedad, nieve, drenar, inundar y biomas. */
public final class UtilityCards {
    public static final String TEXT_NODE = KomaPermissions.BUILD + ".text";
    public static final String ANALYZE_NODE = "komaedit.analyze";
    public static final String GRAVITY_NODE = KomaPermissions.BUILD + ".gravity";
    public static final String SNOW_NODE = KomaPermissions.BUILD + ".snow";
    public static final String DRAIN_NODE = KomaPermissions.BUILD + ".drain";
    public static final String WATERLOG_NODE = KomaPermissions.BUILD + ".waterlog";
    public static final String BIOME_NODE = KomaPermissions.BUILD + ".biome";
    public static final String BIOME_BRUSH_NODE = KomaPermissions.BUILD + ".brush.biome";
    /** Fuentes de serie de {@code /komaedit text}; las propias del servidor llegan con su estado. */
    public static final List<String> BUILTIN_FONTS = List.of("sans", "fina", "mono");
    public static final int MAX_TEXT = 256;
    public static final int MAX_LINES = 16;
    private static final Pattern BIOME = Pattern.compile("[a-z0-9_.\\-]+(:[a-z0-9_./\\-]+)?");
    private static final Pattern FONT = Pattern.compile("[a-z0-9_.\\-]{1,64}");

    private UtilityCards() {
    }

    /** {@code /komaedit text}: texto, material, fuente, tamaño, grosor, borde, orientación y piezas. */
    public static final class Text extends ToolCard {
        Text() {
            super("text", ToolFamily.UTILITIES, ToolTarget.LOOK,
                    List.of("text", "texto", "letras", "letter", "rotulo", "cartel", "fuente", "font", "sign"),
                    List.of(
                            new ToolParam.Text("text", "KomaEdit", MAX_TEXT, true, null),
                            block("material", "stone"),
                            new ToolParam.Choice("font", BUILTIN_FONTS, "sans", ToolParam.Choice.Style.BUTTONS,
                                    "fonts", null),
                            number("size", 1, 256, 1, 4, 8),
                            number("depth", 1, 64, 1, 2, 1),
                            number("border", 0, 16, 1, 2, 0),
                            when(values -> values.number("border", 0) > 0, block("border_material", null)),
                            choice("orientation", ToolParam.Choice.Style.BUTTONS, "auto", "standing", "flat"),
                            choice("align", ToolParam.Choice.Style.BUTTONS, "centro", "izquierda", "derecha"),
                            decimal("spacing", -80, 320, 5, 10, 0, 1),
                            decimal("line_spacing", 5, 40, 1, 5, 10, 1),
                            choice("profile", ToolParam.Choice.Style.CYCLE, "none", "SlabsAndStairs", "Slabs", "SSW"),
                            mask()
                    ));
        }

        /** El texto entre comillas, con las comillas de dentro escapadas como pide la orden. */
        static String quoted(String text) {
            return "\"" + text.replace("\"", "\\\"") + "\"";
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            CommandLine line = new CommandLine("komaedit text " + quoted(values.text("text")))
                    .word(values.block("material"));
            if (!values.choice("font").equals("sans")) {
                line.flag("-f", values.choice("font"));
            }
            line.changed(values, "size", "-s").changed(values, "depth", "-d").changed(values, "border", "-b");
            if (values.param("border_material").visible(values)) {
                line.flag("-o", values.block("border_material"));
            }
            switch (values.choice("orientation")) {
                case "standing" -> line.flag("-v", true);
                case "flat" -> line.flag("-h", true);
                default -> {
                }
            }
            if (!values.choice("align").equals("centro")) {
                line.flag("-a", values.choice("align"));
            }
            line.changed(values, "spacing", "-k").changed(values, "line_spacing", "-l");
            if (!values.choice("profile").equals("none")) {
                line.flag("-w", values.choice("profile"));
            }
            return line.flag("-m", values.text("mask")).flag(previewFlag(), preview).build();
        }

        /** {@code -v} es «de pie» en el texto: la vista previa va con la bandera larga. */
        @Override
        public String previewFlag() {
            return "--preview";
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(TEXT_NODE);
        }

        @Override
        public List<ToolPreset> presets() {
            return List.of(
                    ToolPreset.of("sign", "size", 6, "depth", 1, "font", "sans", "orientation", "standing"),
                    ToolPreset.of("monument", "size", 16, "depth", 3, "border", 1, "profile", "SlabsAndStairs"),
                    ToolPreset.of("floor", "orientation", "flat", "size", 10),
                    ToolPreset.of("mono", "font", "mono"));
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            String text = values.text("text");
            if (text.isBlank()) {
                problems.add(ToolProblem.error("text_empty"));
            } else if (text.split("\\\\n", -1).length > MAX_LINES) {
                problems.add(ToolProblem.error("text_lines", MAX_LINES));
            }
            if (values.block("material") == null) {
                problems.add(ToolProblem.error("material"));
            }
            if (!FONT.matcher(values.choice("font")).matches()) {
                problems.add(ToolProblem.error("font", values.choice("font")));
            }
            noSpaces(values, "material", problems);
            noSpaces(values, "border_material", problems);
            noSpaces(values, "mask", problems);
            return problems;
        }
    }

    /** {@code /komaedit analyze}: cuenta la selección; la lista llega a la tarjeta. */
    public static final class Analyze extends ToolCard {
        Analyze() {
            super("analyze", ToolFamily.UTILITIES, ToolTarget.SELECTION,
                    List.of("analyze", "analizar", "materiales", "materials", "lista", "coste", "survival", "contar",
                            "count"),
                    List.of(number("lines", 1, 100, 1, 10, 10), mask()));
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            return new CommandLine("komaedit analyze")
                    .changed(values, "lines", "-n")
                    .flag("-m", values.text("mask"))
                    .build();
        }

        @Override
        public boolean previewable() {
            return false;
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(ANALYZE_NODE);
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            noSpaces(values, "mask", problems);
            return problems;
        }
    }

    /** {@code /komaedit gravity}: deja caer los bloques de la selección por columnas. */
    public static final class Gravity extends ToolCard {
        Gravity() {
            super("gravity", ToolFamily.UTILITIES, ToolTarget.SELECTION,
                    List.of("gravity", "gravedad", "caer", "fall", "arena", "sand"),
                    List.of(toggle("unsupported", false), mask()));
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            return new CommandLine("komaedit gravity")
                    .flag("-a", values.toggle("unsupported"))
                    .flag("-m", values.text("mask"))
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(GRAVITY_NODE);
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            noSpaces(values, "mask", problems);
            return problems;
        }
    }

    /** {@code /komaedit snow}: capas de nieve que siguen la pendiente. */
    public static final class Snow extends ToolCard {
        Snow() {
            super("snow", ToolFamily.UTILITIES, ToolTarget.SELECTION,
                    List.of("snow", "nieve", "nevar", "invierno", "winter"),
                    List.of(
                            number("layers", 1, 8, 1, 2, 2),
                            number("radius", 0, 16, 1, 2, 2),
                            decimal("fill", 0, 10, 1, 5, 10, 1),
                            number("shade", 0, 8, 1, 2, 2),
                            number("height", 0, 8, 1, 2, 0),
                            toggle("plants", false),
                            mask()
                    ));
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            return new CommandLine("komaedit snow " + values.formatted("layers"))
                    .changed(values, "radius", "-r")
                    .changed(values, "fill", "-f")
                    .changed(values, "shade", "-s")
                    .changed(values, "height", "-h")
                    .flag("-p", values.toggle("plants"))
                    .flag("-m", values.text("mask"))
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(SNOW_NODE);
        }

        @Override
        public List<ToolPreset> presets() {
            return List.of(
                    ToolPreset.of("dusting", "layers", 1, "radius", 0, "shade", 0),
                    ToolPreset.of("drifts", "layers", 3, "radius", 4, "shade", 4, "plants", true),
                    ToolPreset.of("peaks", "layers", 1, "height", 6));
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            noSpaces(values, "mask", problems);
            return problems;
        }
    }

    /** {@code /komaedit drain}: quita agua, lava o las dos y desanega. */
    public static final class Drain extends ToolCard {
        Drain() {
            super("drain", ToolFamily.UTILITIES, ToolTarget.SELECTION,
                    List.of("drain", "drenar", "secar", "agua", "water", "lava", "vaciar"),
                    List.of(choice("mode", ToolParam.Choice.Style.BUTTONS, "water", "lava", "all"),
                            toggle("keep_plants", false), mask()));
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            return new CommandLine("komaedit drain " + values.choice("mode"))
                    .flag("-k", values.toggle("keep_plants"))
                    .flag("-m", values.text("mask"))
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(DRAIN_NODE);
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            noSpaces(values, "mask", problems);
            return problems;
        }
    }

    /** {@code /komaedit waterlog}: inunda por debajo de un nivel. */
    public static final class Waterlog extends ToolCard {
        Waterlog() {
            super("waterlog", ToolFamily.UTILITIES, ToolTarget.SELECTION,
                    List.of("waterlog", "inundar", "anegar", "flood", "agua", "water", "nivel"),
                    List.of(
                            choice("level", ToolParam.Choice.Style.BUTTONS, "top", "feet", "fixed"),
                            when(values -> values.choice("level").equals("fixed"),
                                    number("level_y", -2_048, 2_047, 1, 8, 62)),
                            when(values -> values.choice("level").equals("feet"),
                                    number("offset", -16, 16, 1, 4, 0)),
                            toggle("blocks_only", false),
                            mask()
                    ));
        }

        /** El nivel tal como lo lee la orden: nada (lo alto de la selección), {@code ~}, {@code ~-2} o una altura. */
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

        @Override
        public String command(ToolValues values, boolean preview) {
            return new CommandLine("komaedit waterlog")
                    .word(level(values))
                    .flag("-b", values.toggle("blocks_only"))
                    .flag("-m", values.text("mask"))
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(WATERLOG_NODE);
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            noSpaces(values, "mask", problems);
            return problems;
        }
    }

    /** {@code /komaedit biome}: pinta el bioma de la selección; también lo lleva al pincel. */
    public static final class Biome extends ToolCard {
        Biome() {
            super("biome", ToolFamily.UTILITIES, ToolTarget.SELECTION,
                    List.of("biome", "bioma", "clima", "color hierba", "grass color"),
                    List.of(
                            new ToolParam.Choice("biome", List.of(), "minecraft:plains", ToolParam.Choice.Style.SEARCH,
                                    "biomes", null),
                            toggle("vertical", false),
                            number("radius", 1, 32, 1, 4, 8),
                            mask()
                    ));
        }

        private static String biome(ToolValues values) {
            String id = values.choice("biome");
            return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            return new CommandLine("komaedit biome " + biome(values))
                    .flag("-v", values.toggle("vertical"))
                    .flag("-m", values.text("mask"))
                    .build();
        }

        /** El cambio de bioma no pasa por el motor de bloques: no hay vista previa. */
        @Override
        public boolean previewable() {
            return false;
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(BIOME_NODE);
        }

        @Override
        public List<ToolAction> actions() {
            return List.of(new ToolAction("brush", List.of(BIOME_BRUSH_NODE), false,
                    values -> new CommandLine("komaedit pincel biome " + biome(values))
                            .word(values.formatted("radius"))
                            .flag("-v", values.toggle("vertical"))
                            .flag("-m", values.text("mask"))
                            .build()));
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            if (!BIOME.matcher(values.choice("biome")).matches()) {
                problems.add(ToolProblem.error("biome", values.choice("biome")));
            }
            noSpaces(values, "mask", problems);
            return problems;
        }
    }
}
