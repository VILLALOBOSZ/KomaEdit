package com.komainu.moxia.studio.tools;

import com.komainu.moxia.studio.ValueUnit;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.terrain.HeightmapBrush;
import com.komainu.moxia.terrain.LandformPlanner;
import com.komainu.moxia.terrain.LandformRequest;
import com.komainu.moxia.terrain.RiverPlanner;
import com.komainu.moxia.terrain.StampLibrary;
import com.komainu.moxia.terrain.SurfacePalette;
import com.komainu.moxia.terrain.stamp.StampCategory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Terraformado: estampar relieve (con el pincel o sobre la selección), rampa, río o cañón y erosión (sobre la
 * selección o con el pincel). Pintar la superficie es {@link PaintCard}.
 */
public final class TerrainCards {
    public static final String STAMP_NODE = KomaPermissions.BUILD + ".brush.heightmap";
    public static final String RAMP_NODE = KomaPermissions.BUILD + ".terrain.ramp";
    public static final String RIVER_NODE = KomaPermissions.BUILD + ".terrain.river";
    public static final String CANYON_NODE = KomaPermissions.BUILD + ".terrain.canyon";
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
            super("stamp", ToolTarget.SELECTION,
                    List.of("heightmap", "sello", "relieve", "stamp", "estampar", "montana", "monte", "colina",
                            "crater", "volcan", "duna", "isla", "acantilado", "meseta", "terreno", "terrain", "brush",
                            "pincel"),
                    List.of(
                            use(ToolUse.BRUSH, ToolUse.SELECTION),
                            new ToolParam.Choice("stamp", List.copyOf(StampLibrary.descriptions().keySet()),
                                    "montana", ToolParam.Choice.Style.GALLERY, "", null),
                            new ToolParam.Text("custom", "", 64, false, null),
                            only(ToolUse.BRUSH, number("radius", 1, 32, 1, 4, 12).unit(ValueUnit.BLOCKS)),
                            auto("height", 0, 96, 1, 5, 0, 12).unit(ValueUnit.BLOCKS),
                            choice("mode", ToolParam.Choice.Style.BUTTONS, "sumar", "restar", "maximo", "aplanar",
                                    "suavizar"),
                            autoPalette(),
                            number("variation", 0, 100, 5, 25, DEFAULT_VARIATION).unit(ValueUnit.PERCENT_POINTS),
                            only(ToolUse.BRUSH, toggle("lock_seed", false)),
                            seed(),
                            number("intensity", 1, 100, 5, 25, 100).unit(ValueUnit.PERCENT_POINTS),
                            choice("rotation", ToolParam.Choice.Style.CYCLE, "mirada", "azar", "0", "45", "90", "135",
                                    "180", "225", "270", "315"),
                            auto("neutral", 0, 100, 5, 25, 2, 0).unit(ValueUnit.PERCENT),
                            only(ToolUse.BRUSH, toggle("brush_preview", false)),
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
            // La paleta y la variación ya funcionan de serie (la de la categoría y un 60 %): van en «Más ajustes».
            return List.of(ToolUse.PARAM, "stamp", "radius", "height", "mode");
        }

        /** El sello que se usa: el propio escrito o el elegido en la galería. */
        static String stamp(ToolValues values) {
            String custom = values.text("custom").strip();
            return custom.isEmpty() ? values.choice("stamp") : custom;
        }

        /**
         * La altura que se manda: la escrita o, en automático, la natural del sello para ese radio. Sobre la
         * selección, en automático no se manda: el servidor la saca del tamaño de la selección.
         */
        static String height(ToolValues values) {
            if (values.number("height") != null) {
                return values.formatted("height");
            }
            if (values.choice(ToolUse.PARAM).equals(ToolUse.SELECTION.id())) {
                return null;
            }
            return Integer.toString(HeightmapBrush.naturalHeight(stamp(values), values.number("radius", 12)));
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            boolean brush = use(values) == ToolUse.BRUSH;
            CommandLine line = new CommandLine((brush ? "komaedit pincel heightmap " : "komaedit relieve ")
                    + stamp(values))
                    .word(brush ? values.formatted("radius") : null)
                    .word(height(values))
                    .word(values.choice("mode"))
                    .word(values.choice("palette"))
                    .changed(values, "intensity", "-i");
            if (!values.choice("rotation").equals("mirada")) {
                line.flag("-r", values.choice("rotation"));
            }
            line.changed(values, "variation", "-x")
                    .flag("-s", values.formatted("seed"));
            if (brush) {
                line.flag("-k", values.toggle("lock_seed"));
            }
            line.flag("-c", values.formatted("neutral"))
                    .flag("-m", values.text("mask"));
            return brush ? line.flag("-p", values.toggle("brush_preview")).build()
                    : line.flag(previewFlag(), preview).build();
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
                return use(values) == ToolUse.BRUSH ? height(values.with("height", null)) : null;
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
            super("ramp", ToolTarget.PATH_OR_SELECTION,
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
            super("river", ToolTarget.PATH_OR_SELECTION,
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

    /**
     * {@code /komaedit terreno cordillera|costa|valle}: un relieve que sigue la trayectoria. Sin trayectoria vale la
     * selección (de pos1 a pos2), como el río; sin ninguna de las dos, el botón se apaga y dice qué falta.
     */
    public static final class Landform extends ToolCard {
        public static final String RANGE_NODE = KomaPermissions.BUILD + ".terrain.range";
        public static final String COAST_NODE = KomaPermissions.BUILD + ".terrain.coast";
        public static final String VALLEY_NODE = KomaPermissions.BUILD + ".terrain.valley";
        static final List<String> FAMILIES = families();

        Landform() {
            super("landform", ToolTarget.PATH_OR_SELECTION,
                    List.of("cordillera", "sierra", "montanas", "costa", "acantilado", "litoral", "valle", "canon",
                            "cañón", "glaciar", "trayectoria", "range", "coast", "cliff", "valley", "terreno",
                            "terrain"),
                    List.of(
                            choice("landform", ToolParam.Choice.Style.BUTTONS, "cordillera", "costa", "valle"),
                            auto("width", LandformRequest.MIN_WIDTH, LandformRequest.MAX_WIDTH, 2, 10, 0, 100)
                                    .unit(ValueUnit.BLOCKS),
                            when(values -> !valley(values), auto("height", 2, 160, 1, 5, 0, 40)
                                    .unit(ValueUnit.BLOCKS)),
                            when(Landform::valley, auto("depth", 2, 160, 1, 5, 0, 24).unit(ValueUnit.BLOCKS)),
                            when(Landform::valley, choice("section", ToolParam.Choice.Style.BUTTONS, "v", "u",
                                    "canon")),
                            when(values -> values.choice("landform").equals("costa"),
                                    choice("land_side", ToolParam.Choice.Style.CYCLE, "auto", "izquierda", "derecha")),
                            choice("along", ToolParam.Choice.Style.CYCLE, "auto", "campana", "plano", "sube",
                                    "baja"),
                            number("variation", 0, 100, 5, 25, LandformRequest.DEFAULT_VARIATION)
                                    .unit(ValueUnit.PERCENT_POINTS),
                            seed(),
                            new ToolParam.Choice("family", FAMILIES, LandformRequest.AUTO,
                                    ToolParam.Choice.Style.CYCLE, "", null),
                            choice("mode", ToolParam.Choice.Style.BUTTONS, "auto", "sumar", "maximo", "aplanar"),
                            number("erosion", 0, 100, 5, 25, LandformRequest.DEFAULT_EROSION)
                                    .unit(ValueUnit.PERCENT_POINTS),
                            when(values -> !values.choice("landform").equals("cordillera"),
                                    choice("water", ToolParam.Choice.Style.CYCLE, "auto", "agua", "seco")),
                            landformPalette(),
                            mask()
                    ));
        }

        private static List<String> families() {
            List<String> options = new ArrayList<>(List.of(LandformRequest.AUTO, LandformRequest.NONE));
            for (StampCategory category : StampCategory.values()) {
                options.add(category.id());
            }
            return List.copyOf(options);
        }

        private static ToolParam.Choice landformPalette() {
            List<String> options = new ArrayList<>();
            options.add(LandformRequest.AUTO);
            options.addAll(palettes());
            return new ToolParam.Choice("palette", options, LandformRequest.AUTO, ToolParam.Choice.Style.CYCLE, "",
                    null);
        }

        static boolean valley(ToolValues values) {
            return values.choice("landform").equals("valle");
        }

        static LandformPlanner.Kind kind(ToolValues values) {
            return LandformPlanner.Kind.byId(values.choice("landform"));
        }

        @Override
        public List<String> essentials() {
            return List.of("landform", "width", "height", "depth", "section", "land_side");
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            LandformPlanner.Kind kind = kind(values);
            String heightParam = valley(values) ? "depth" : "height";
            CommandLine line = new CommandLine("komaedit terreno " + kind.id());
            Integer width = values.number("width");
            Integer height = values.number(heightParam);
            if (width != null || height != null) {
                // Son posicionales: con la altura hay que escribir también el ancho.
                line.word(width != null ? values.formatted("width")
                        : Integer.toString(LandformRequest.defaultWidth(kind)));
                line.word(height != null ? values.formatted(heightParam) : null);
            }
            if (!values.choice("mode").equals("auto")) {
                line.word(values.choice("mode"));
            }
            if (valley(values)) {
                line.word(values.choice("section"));
            }
            if (!values.choice("palette").equals(LandformRequest.AUTO)) {
                line.word(values.choice("palette"));
            }
            if (!values.choice("along").equals("auto")) {
                line.flag("-p", values.choice("along"));
            }
            if (values.param("land_side").visible(values) && !values.choice("land_side").equals("auto")) {
                line.flag("-l", values.choice("land_side"));
            }
            if (values.param("water").visible(values) && !values.choice("water").equals("auto")) {
                line.flag("-f", values.choice("water"));
            }
            if (!values.choice("family").equals(LandformRequest.AUTO)) {
                line.flag("-h", values.choice("family"));
            }
            return line.changed(values, "variation", "-x")
                    .changed(values, "erosion", "-r")
                    .flag("-s", values.formatted("seed"))
                    .flag("-m", values.text("mask"))
                    .flag(previewFlag(), preview)
                    .build();
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(switch (kind(values)) {
                case RANGE -> RANGE_NODE;
                case COAST -> COAST_NODE;
                case VALLEY -> VALLEY_NODE;
            });
        }

        @Override
        public java.util.Set<String> allPermissions() {
            return new java.util.LinkedHashSet<>(List.of(RANGE_NODE, COAST_NODE, VALLEY_NODE));
        }

        @Override
        public List<ToolAction> actions() {
            return List.of(new ToolAction("estimate", List.of(), false, values -> command(values, false) + " -e"));
        }

        @Override
        public List<ToolPreset> presets() {
            return List.of(
                    ToolPreset.of("high_range", "landform", "cordillera", "width", 140, "height", 60),
                    ToolPreset.of("foothills", "landform", "cordillera", "width", 90, "height", 18, "variation", 45,
                            "erosion", 70),
                    ToolPreset.of("cliffs", "landform", "costa", "width", 60, "height", 30),
                    ToolPreset.of("glacial_valley", "landform", "valle", "section", "u", "width", 100, "depth", 32),
                    ToolPreset.of("canyon", "landform", "valle", "section", "canon", "width", 70, "depth", 36));
        }

        @Override
        public String autoHint(String paramId, ToolValues values) {
            LandformPlanner.Kind kind = kind(values);
            return switch (paramId) {
                case "width" -> Integer.toString(LandformRequest.defaultWidth(kind));
                case "height", "depth" -> Integer.toString(LandformRequest.defaultHeight(kind));
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
            super("erosion", ToolTarget.SELECTION,
                    List.of("erosion", "erosión", "erode", "erosionar", "hidraulica", "termica", "lluvia",
                            "terreno", "terrain", "pincel", "brush"),
                    List.of(
                            use(ToolUse.SELECTION, ToolUse.BRUSH),
                            choice("kind", ToolParam.Choice.Style.BUTTONS, "hidraulica", "termica", "completa"),
                            only(ToolUse.BRUSH, number("radius", 2, 32, 1, 4, 10).unit(ValueUnit.BLOCKS)),
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
            return List.of(ToolUse.PARAM, "kind", "radius", "iterations", "strength");
        }

        /**
         * Sobre la selección, la orden de siempre; con el pincel, la misma orden repetida en un cilindro alrededor de
         * cada clic ({@code /komaedit pincel zona}).
         */
        @Override
        public String command(ToolValues values, boolean preview) {
            if (use(values) == ToolUse.BRUSH) {
                return "komaedit pincel zona " + values.formatted("radius") + " " + selectionCommand(values, false)
                        .substring("komaedit ".length());
            }
            return selectionCommand(values, preview);
        }

        private String selectionCommand(ToolValues values, boolean preview) {
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
            List<String> nodes = new ArrayList<>();
            if (use(values) == ToolUse.BRUSH) {
                nodes.add(TouchUpCard.ZONE_NODE);
            }
            switch (values.choice("kind")) {
                case "hidraulica" -> nodes.add(HYDRAULIC_NODE);
                case "termica" -> nodes.add(THERMAL_NODE);
                default -> nodes.addAll(List.of(HYDRAULIC_NODE, THERMAL_NODE));
            }
            return nodes;
        }

        @Override
        public java.util.Set<String> allPermissions() {
            return new java.util.LinkedHashSet<>(List.of(HYDRAULIC_NODE, THERMAL_NODE, TouchUpCard.ZONE_NODE));
        }

        @Override
        public List<ToolAction> actions() {
            return List.of(new ToolAction("estimate", List.of(), false, values -> command(values, false) + " -e"));
        }

        /** Estimar solo sobre la selección: el pincel no sabe cuánto va a tocar hasta el clic. */
        @Override
        public List<ToolAction> actions(ToolValues values) {
            return use(values) == ToolUse.BRUSH ? List.of() : actions();
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
}
