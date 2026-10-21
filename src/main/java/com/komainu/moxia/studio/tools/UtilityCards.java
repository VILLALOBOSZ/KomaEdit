package com.komainu.moxia.studio.tools;

import com.komainu.moxia.studio.ValueUnit;
import com.komainu.moxia.permission.KomaPermissions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utilidades de obra: texto en bloques y lista de materiales. Nieve, drenar, inundar, gravedad y biomas son modos de
 * {@link TouchUpCard}.
 */
public final class UtilityCards {
    public static final String TEXT_NODE = KomaPermissions.BUILD + ".text";
    public static final String ANALYZE_NODE = "komaedit.analyze";
    /** Fuentes de serie de {@code /komaedit text}; las propias del servidor llegan con su estado. */
    public static final List<String> BUILTIN_FONTS = List.of("sans", "fina", "mono");
    public static final int MAX_TEXT = 256;
    public static final int MAX_LINES = 16;
    private static final Pattern FONT = Pattern.compile("[a-z0-9_.\\-]{1,64}");

    private UtilityCards() {
    }

    /** {@code /komaedit text}: texto, material, fuente, tamaño, grosor, borde, orientación y piezas. */
    public static final class Text extends ToolCard {
        Text() {
            super("text", ToolTarget.LOOK,
                    List.of("text", "texto", "escribir", "letras", "letter", "rotulo", "cartel", "fuente", "font",
                            "sign"),
                    List.of(
                            new ToolParam.Text("text", "KomaEdit", MAX_TEXT, true, null),
                            block("material", "stone"),
                            new ToolParam.Choice("font", BUILTIN_FONTS, "sans", ToolParam.Choice.Style.BUTTONS,
                                    "fonts", null),
                            number("size", 1, 256, 1, 4, 8).unit(ValueUnit.BLOCKS),
                            number("thickness", 1, 64, 1, 2, 1).unit(ValueUnit.BLOCKS),
                            number("border", 0, 16, 1, 2, 0).unit(ValueUnit.BLOCKS),
                            when(values -> values.number("border", 0) > 0, block("border_material", null)),
                            choice("orientation", ToolParam.Choice.Style.BUTTONS, "auto", "standing", "flat"),
                            choice("align", ToolParam.Choice.Style.BUTTONS, "centro", "izquierda", "derecha"),
                            decimal("spacing", -80, 320, 5, 10, 0, 1),
                            decimal("line_spacing", 5, 40, 1, 5, 10, 1).unit(ValueUnit.MULTIPLIER),
                            choice("profile", ToolParam.Choice.Style.CYCLE, "none", "SlabsAndStairs", "Slabs", "SSW"),
                            mask()
                    ));
        }

        @Override
        public List<String> essentials() {
            return List.of("text", "material", "font", "size");
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
            line.changed(values, "size", "-s").changed(values, "thickness", "-d").changed(values, "border", "-b");
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
                    ToolPreset.of("sign", "size", 6, "thickness", 1, "font", "sans", "orientation", "standing"),
                    ToolPreset.of("monument", "size", 16, "thickness", 3, "border", 1, "profile", "SlabsAndStairs"),
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
            super("analyze", ToolTarget.SELECTION,
                    List.of("analyze", "analizar", "materiales", "materials", "lista", "coste", "contar", "count"),
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
}
