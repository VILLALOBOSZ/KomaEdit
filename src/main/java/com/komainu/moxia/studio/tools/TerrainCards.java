package com.komainu.moxia.studio.tools;

import com.komainu.moxia.studio.ValueUnit;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.terrain.HeightmapBrush;
import com.komainu.moxia.terrain.RiverPlanner;
import com.komainu.moxia.terrain.StampLibrary;
import com.komainu.moxia.terrain.SurfacePalette;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Terraformado: pincel de relieve con sellos, rampa, río o cañón, erosión y pintar la superficie. */
public final class TerrainCards {
    public static final String STAMP_NODE = KomaPermissions.BUILD + ".brush.heightmap";
    public static final String RAMP_NODE = KomaPermissions.BUILD + ".terrain.ramp";
    public static final String RIVER_NODE = KomaPermissions.BUILD + ".terrain.river";
    public static final String CANYON_NODE = KomaPermissions.BUILD + ".terrain.canyon";
    public static final String PAINT_NODE = KomaPermissions.BUILD + ".terrain.paint";
    public static final String HYDRAULIC_NODE = KomaPermissions.BUILD + ".terrain.erode.hydraulic";
    public static final String THERMAL_NODE = KomaPermissions.BUILD + ".terrain.erode.thermal";
    private static final Pattern FILE_NAME = Pattern.compile("[a-z0-9_\\-]{1,64}");

    private TerrainCards() {
    }

    /** Paletas de superficie de serie, en el orden de la documentación. */
    static List<String> palettes() {
        return List.copyOf(SurfacePalette.presets().keySet());
    }

    static ToolParam.Choice palette() {
        return new ToolParam.Choice("palette", palettes(), SurfacePalette.DEFAULT, ToolParam.Choice.Style.CYCLE, "",
                null);
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /**
     * {@code /komaedit pincel heightmap}: la biblioteca de sellos (galería por categorías con buscador y favoritos),
     * radio, altura natural del sello, modo de mezcla, paleta de su categoría y la variación de cada clic.
     */
    public static final class Stamp extends ToolCard {
        /** Variación de serie (por ciento). */
        static final int DEFAULT_VARIATION = 60;

        Stamp() {
            super("stamp", ToolFamily.TERRAIN, ToolTarget.HAND,
                    List.of("heightmap", "sello", "relieve", "stamp", "montana", "monte", "colina", "crater",
                            "volcan", "duna", "isla", "canon", "acantilado", "mesa", "terreno", "terrain", "brush",
                            "pincel"),
                    List.of(
                            new ToolParam.Choice("stamp", List.copyOf(StampLibrary.descriptions().keySet()),
                                    "montana", ToolParam.Choice.Style.GALLERY, "", null),
                            new ToolParam.Text("custom", "", 64, false, null),
                            number("radius", 1, 32, 1, 4, 12).unit(ValueUnit.BLOCKS),
                            auto("height", 0, 96, 1, 5, 0, 12).unit(ValueUnit.BLOCKS),
                            choice("mode", ToolParam.Choice.Style.BUTTONS, "sumar", "restar", "maximo", "aplanar",
                                    "suavizar"),
                            autoPalette(),
                            number("variation", 0, 100, 5, 25, DEFAULT_VARIATION).unit(ValueUnit.PERCENT_POINTS),
                            toggle("lock_seed", false),
                            seed(),
                            number("intensity", 1, 100, 5, 25, 100).unit(ValueUnit.PERCENT_POINTS),
                            choice("rotation", ToolParam.Choice.Style.CYCLE, "mirada", "azar", "0", "45", "90", "135",
                                    "180", "225", "270", "315"),
                            auto("neutral", 0, 100, 5, 25, 2, 0).unit(ValueUnit.PERCENT),
                            toggle("brush_preview", false),
                            mask()
                    ));
        }

        /** La paleta: en automático la de la categoría del sello, o cualquiera de serie. */
        private static ToolParam.Choice autoPalette() {
            List<String> options = new ArrayList<>();
            options.add(HeightmapBrush.AUTO_PALETTE);
            options.addAll(palettes());
            return new ToolParam.Choice("palette", options, HeightmapBrush.AUTO_PALETTE, ToolParam.Choice.Style.CYCLE,
                    "", null);
        }

        @Override
        public List<String> essentials() {
            return List.of("stamp", "radius", "height", "mode", "palette", "variation", "lock_seed");
        }

        /** El sello que se usa: el propio escrito o el elegido en la galería. */
        static String stamp(ToolValues values) {
            String custom = values.text("custom").strip();
            return custom.isEmpty() ? values.choice("stamp") : custom;
        }

        /** La altura que se manda: la escrita o, en automático, la natural del sello para ese radio. */
        static String height(ToolValues values) {
            return values.number("height") != null ? values.formatted("height")
                    : Integer.toString(HeightmapBrush.naturalHeight(stamp(values), values.number("radius", 12)));
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            CommandLine line = new CommandLine("komaedit pincel heightmap " + stamp(values))
                    .word(values.formatted("radius"))
                    .word(height(values))
                    .word(values.choice("mode"))
                    .word(values.choice("palette"))
                    .changed(values, "intensity", "-i");
            if (!values.choice("rotation").equals("mirada")) {
                line.flag("-r", values.choice("rotation"));
            }
            return line.changed(values, "variation", "-x")
                    .flag("-s", values.formatted("seed"))
                    .flag("-k", values.toggle("lock_seed"))
                    .flag("-c", values.formatted("neutral"))
                    .flag("-m", values.text("mask"))
                    .flag("-p", values.toggle("brush_preview"))
                    .build();
        }

        @Override
        public boolean previewable() {
            return false;
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(STAMP_NODE);
        }

        /** Un preset con sello quita el sello propio escrito: si no, la orden seguiría usando ese. */
        @Override
        public ToolValues apply(ToolValues values, ToolPreset preset) {
            ToolValues applied = super.apply(values, preset);
            return preset.values().containsKey("stamp") ? applied.with("custom", "") : applied;
        }

        @Override
        public List<ToolPreset> presets() {
            return List.of(
                    ToolPreset.of("mountain", "stamp", "montana", "radius", 24, "rotation", "azar"),
                    ToolPreset.of("range", "stamp", "cordillera", "radius", 30),
                    ToolPreset.of("hills", "stamp", "lomas", "radius", 22, "mode", "maximo"),
                    ToolPreset.of("dunes", "stamp", "dunas", "radius", 24),
                    ToolPreset.of("canyon", "stamp", "canon", "radius", 26),
                    ToolPreset.of("crater", "stamp", "crater", "radius", 16));
        }

        @Override
        public String autoHint(String paramId, ToolValues values) {
            if (paramId.equals("neutral") && StampLibrary.has(values.choice("stamp"))) {
                return format(StampLibrary.info(values.choice("stamp")).neutral());
            }
            if (paramId.equals("height")) {
                return height(values.with("height", null));
            }
            return null;
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            String custom = values.text("custom").strip();
            if (!custom.isEmpty() && !FILE_NAME.matcher(custom).matches()) {
                problems.add(ToolProblem.error("stamp_name", custom));
            }
            noSpaces(values, "mask", problems);
            return problems;
        }
    }

    /** {@code /komaedit terreno rampa}: del primer al último punto. */
    public static final class Ramp extends ToolCard {
        Ramp() {
            super("ramp", ToolFamily.TERRAIN, ToolTarget.PATH_OR_SELECTION,
                    List.of("rampa", "ramp", "cuesta", "pendiente", "terraplen", "slope", "terreno", "terrain"),
                    List.of(
                            choice("profile", ToolParam.Choice.Style.BUTTONS, "plano", "cono"),
                            number("width", 1, 64, 1, 4, 5).unit(ValueUnit.BLOCKS),
                            when(values -> values.choice("profile").equals("plano"),
                                    number("transition", 0, 64, 1, 4, 6).unit(ValueUnit.BLOCKS)),
                            palette(),
                            when(values -> values.choice("profile").equals("cono"),
                                    decimal("talus", 1, 80, 1, 5, 10, 1).unit(ValueUnit.RATIO)),
                            toggle("smooth", false),
                            mask()
                    ));
        }

        @Override
        public List<String> essentials() {
            return List.of("profile", "width", "palette");
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            CommandLine line = new CommandLine("komaedit terreno rampa " + values.choice("profile"))
                    .word(values.formatted("width"));
            if (values.param("transition").visible(values)) {
                line.word(values.formatted("transition"));
            }
            line.word(values.choice("palette"));
            if (values.param("talus").visible(values)) {
                line.changed(values, "talus", "-t");
            }
            return line.flag("-u", values.toggle("smooth"))
                    .flag("-m", values.text("mask"))
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(RAMP_NODE);
        }

        @Override
        public List<ToolAction> actions() {
            return List.of(new ToolAction("estimate", List.of(RAMP_NODE), false,
                    values -> command(values, false) + " -e"));
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            noSpaces(values, "mask", problems);
            return problems;
        }
    }

    /** {@code /komaedit terreno rio|arroyo|canon|barranco}: cauce a lo largo de la trayectoria. */
    public static final class River extends ToolCard {
        River() {
            super("river", ToolFamily.TERRAIN, ToolTarget.PATH_OR_SELECTION,
                    List.of("rio", "río", "river", "arroyo", "stream", "canon", "cañon", "cañón", "canyon",
                            "barranco", "gully", "cauce", "foso", "agua", "terreno", "terrain"),
                    List.of(
                            choice("kind", ToolParam.Choice.Style.BUTTONS, "rio", "arroyo", "canon", "barranco"),
                            auto("width", 1, 64, 1, 2, 0, 5).unit(ValueUnit.BLOCKS),
                            auto("depth", 0, 96, 1, 2, 0, 3).unit(ValueUnit.BLOCKS),
                            palette(),
                            auto("bank", 5, 320, 5, 20, 1, 40).unit(ValueUnit.BLOCKS),
                            auto("walls", 1, 80, 1, 5, 1, 10).unit(ValueUnit.RATIO),
                            auto("transition", 0, 64, 1, 4, 0, 6).unit(ValueUnit.BLOCKS),
                            choice("fluid", ToolParam.Choice.Style.BUTTONS, "auto", "agua", "lava", "seco"),
                            when(values -> !values.choice("fluid").equals("seco")
                                            && !(values.choice("fluid").equals("auto")
                                            && values.choice("kind").equals("barranco")),
                                    auto("level", 0, 96, 1, 2, 0, 2).unit(ValueUnit.BLOCKS)),
                            auto("smoothing", 0, 256, 1, 8, 0, 6).unit(ValueUnit.BLOCKS),
                            toggle("follow", false),
                            toggle("keep_order", false),
                            mask()
                    ));
        }

        @Override
        public List<String> essentials() {
            return List.of("kind", "width", "depth", "fluid");
        }

        static RiverPlanner.Settings preset(ToolValues values) {
            return RiverPlanner.preset(values.choice("kind"));
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            CommandLine line = new CommandLine("komaedit terreno " + values.choice("kind"));
            Integer width = values.number("width");
            Integer depth = values.number("depth");
            if (width != null || depth != null) {
                // Son posicionales: con la profundidad hay que escribir también el ancho.
                RiverPlanner.Settings base = preset(values);
                line.word(width != null ? values.formatted("width") : format(base.halfWidth() * 2.0));
                line.word(depth != null ? values.formatted("depth") : null);
            }
            line.word(values.choice("palette"))
                    .flag("-o", values.formatted("bank"))
                    .flag("-w", values.formatted("walls"))
                    .flag("-t", values.formatted("transition"));
            if (!values.choice("fluid").equals("auto")) {
                line.flag("-f", values.choice("fluid"));
            }
            if (values.param("level").visible(values)) {
                line.flag("-n", values.formatted("level"));
            }
            return line.flag("-z", values.formatted("smoothing"))
                    .flag("-y", values.toggle("follow"))
                    .flag("-k", values.toggle("keep_order"))
                    .flag("-m", values.text("mask"))
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> permissions(ToolValues values) {
            String kind = values.choice("kind");
            return List.of(kind.equals("canon") || kind.equals("barranco") ? CANYON_NODE : RIVER_NODE);
        }

        @Override
        public java.util.Set<String> allPermissions() {
            return new java.util.LinkedHashSet<>(List.of(RIVER_NODE, CANYON_NODE));
        }

        @Override
        public List<ToolAction> actions() {
            return List.of(new ToolAction("estimate", List.of(), false, values -> command(values, false) + " -e"));
        }

        @Override
        public String autoHint(String paramId, ToolValues values) {
            RiverPlanner.Settings base = preset(values);
            return switch (paramId) {
                case "width" -> format(base.halfWidth() * 2.0);
                case "depth" -> format(base.depth());
                case "bank" -> format(base.bank());
                case "walls" -> format(base.wallSlope());
                case "transition" -> format(base.transition());
                case "level" -> format(base.level());
                case "smoothing" -> format(base.smoothing());
                default -> null;
            };
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            noSpaces(values, "mask", problems);
            return problems;
        }
    }

    /** {@code /komaedit terreno erosion}: hidráulica, térmica o completa sobre la selección. */
    public static final class Erosion extends ToolCard {
        Erosion() {
            super("erosion", ToolFamily.TERRAIN, ToolTarget.SELECTION,
                    List.of("erosion", "erosión", "erode", "erosionar", "hidraulica", "termica", "lluvia",
                            "terreno", "terrain"),
                    List.of(
                            choice("kind", ToolParam.Choice.Style.BUTTONS, "hidraulica", "termica", "completa"),
                            auto("iterations", 1, 2_000, 1, 10, 0, 3).unit(ValueUnit.TIMES),
                            decimal("strength", 1, 80, 1, 5, 10, 1).unit(ValueUnit.MULTIPLIER),
                            when(values -> !values.choice("kind").equals("hidraulica"), auto("angle", 5, 85, 1, 5, 0,
                                    40).unit(ValueUnit.DEGREES)),
                            seed(),
                            palette(),
                            mask()
                    ));
        }

        @Override
        public List<String> essentials() {
            return List.of("kind", "iterations", "strength");
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            CommandLine line = new CommandLine("komaedit terreno erosion " + values.choice("kind"));
            Integer iterations = values.number("iterations");
            boolean strength = !values.isDefault("strength");
            if (iterations != null || strength) {
                line.word(iterations != null ? values.formatted("iterations") : autoHint("iterations", values));
                line.word(strength ? values.formatted("strength") : null);
            }
            line.word(values.choice("palette"));
            if (values.param("angle").visible(values)) {
                line.flag("-a", values.formatted("angle"));
            }
            return line.flag("-s", values.formatted("seed"))
                    .flag("-m", values.text("mask"))
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return switch (values.choice("kind")) {
                case "hidraulica" -> List.of(HYDRAULIC_NODE);
                case "termica" -> List.of(THERMAL_NODE);
                default -> List.of(HYDRAULIC_NODE, THERMAL_NODE);
            };
        }

        @Override
        public java.util.Set<String> allPermissions() {
            return new java.util.LinkedHashSet<>(List.of(HYDRAULIC_NODE, THERMAL_NODE));
        }

        @Override
        public List<ToolAction> actions() {
            return List.of(new ToolAction("estimate", List.of(), false, values -> command(values, false) + " -e"));
        }

        @Override
        public String autoHint(String paramId, ToolValues values) {
            return switch (paramId) {
                case "iterations" -> values.choice("kind").equals("termica") ? "40" : "3";
                case "angle" -> "40";
                default -> null;
            };
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            boolean thermal = values.choice("kind").equals("termica");
            if (!thermal && values.number("iterations", 0) > 64) {
                problems.add(ToolProblem.error("drops", 64));
            }
            if (thermal && values.decimal("strength") > 2.0) {
                problems.add(ToolProblem.error("thermal_strength", "2"));
            }
            noSpaces(values, "mask", problems);
            return problems;
        }
    }

    /** {@code /komaedit terreno pintar}: repinta la superficie de la selección sin cambiar alturas. */
    public static final class Paint extends ToolCard {
        Paint() {
            super("paint", ToolFamily.TERRAIN, ToolTarget.SELECTION,
                    List.of("pintar", "paint", "superficie", "surface", "paleta", "hierba", "terreno", "terrain"),
                    List.of(palette(), mask()));
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            return new CommandLine("komaedit terreno pintar " + values.choice("palette"))
                    .flag("-m", values.text("mask"))
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(PAINT_NODE);
        }

        @Override
        public List<ToolAction> actions() {
            return List.of(new ToolAction("estimate", List.of(PAINT_NODE), false,
                    values -> command(values, false) + " -e"));
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            noSpaces(values, "mask", problems);
            return problems;
        }
    }
}
