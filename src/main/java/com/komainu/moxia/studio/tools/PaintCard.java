package com.komainu.moxia.studio.tools;

import com.komainu.moxia.brush.paint.FootprintLibrary;
import com.komainu.moxia.brush.paint.PaintConfig;
import com.komainu.moxia.brush.paint.PaintPalettes;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.studio.ValueUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * «Pintar»: recolorea la superficie sin cambiar ninguna altura, con el pincel ({@code /komaedit pincel paint}) o sobre
 * la selección ({@code /komaedit pintar}), con los mismos ajustes. Reúne lo que eran «Pintar terreno» (el pincel de
 * pintura) y «Pintar superficie» ({@code terreno pintar}), que ahora es el modo Natural.
 *
 * <p>Cómo se reparte la paleta: por la pendiente, la altura, un ruido, la huella (del centro al borde) o Natural (las
 * reglas de una paleta de superficie: hierba en lo llano, piedra en las paredes, nieve arriba, arena en la orilla y
 * tierra debajo). A la vista, lo de empezar; el resto, en «Más ajustes». De serie pinta hierba en lo llano y piedra
 * en lo empinado.</p>
 */
public final class PaintCard extends ToolCard {
    public static final String NODE = KomaPermissions.BUILD + ".brush.paint";
    /** «Pintar superficie» de antes: la paleta de superficie por reglas sobre la selección. */
    public static final String SURFACE_NODE = KomaPermissions.BUILD + ".terrain.paint";
    /** La opción de la galería que usa la paleta escrita a mano. */
    public static final String CUSTOM = "propia";
    /** El modo que repinta con una paleta de superficie (lo que hacía «Pintar superficie»). */
    public static final String NATURAL = "natural";

    PaintCard() {
        super("paint", ToolTarget.SELECTION,
                List.of("pintar", "paint", "pintura", "pincel", "brush", "degradado", "gradient", "color", "terreno",
                        "terrain", "superficie", "surface", "natural", "hierba", "piedra", "paleta", "axiom"),
                List.of(
                        use(ToolUse.BRUSH, ToolUse.SELECTION),
                        new ToolParam.Choice("gradient", List.of("pendiente", "altura", "ruido", "huella", NATURAL),
                                PaintConfig.DEFAULT_GRADIENT.id(), ToolParam.Choice.Style.BUTTONS, "", null),
                        when(PaintCard::byPalette, new ToolParam.Choice("paint_palette", palettes(),
                                PaintPalettes.DEFAULT, ToolParam.Choice.Style.GALLERY, "", null)),
                        when(values -> byPalette(values) && values.choice("paint_palette").equals(CUSTOM),
                                new ToolParam.Text("custom_palette", "##Grayscale", 128, false, null)),
                        when(PaintCard::natural, TerrainCards.palette()),
                        when(PaintCard::footprint, new ToolParam.Choice("stamp", FootprintLibrary.builtIn().names(),
                                FootprintLibrary.CIRCLE, ToolParam.Choice.Style.GALLERY, "", null)),
                        only(ToolUse.BRUSH, number("radius", 1, PaintConfig.MAX_RADIUS, 1, 4,
                                PaintConfig.DEFAULT_RADIUS).unit(ValueUnit.BLOCKS)),
                        when(PaintCard::byPalette, number("strength", 1, 100, 5, 25, PaintConfig.DEFAULT_STRENGTH)
                                .unit(ValueUnit.PERCENT_POINTS)),
                        when(PaintCard::byPalette, seed()),
                        only(ToolUse.BRUSH, when(PaintCard::byPalette, toggle("lock_seed", false))),
                        only(ToolUse.BRUSH, when(PaintCard::byPalette, number("dab_spacing", 5, 200, 5, 25,
                                PaintConfig.DEFAULT_SPACING).unit(ValueUnit.PERCENT_POINTS))),
                        when(PaintCard::footprint, number("hardness", 0, 100, 5, 25, PaintConfig.DEFAULT_HARDNESS)
                                .unit(ValueUnit.PERCENT_POINTS)),
                        when(PaintCard::byPalette, number("variation", 0, 100, 5, 25, PaintConfig.DEFAULT_JITTER)
                                .unit(ValueUnit.PERCENT_POINTS)),
                        when(mode("pendiente"), number("slope_low", 0, 89, 1, 5, PaintConfig.DEFAULT_SLOPE_LOW)
                                .unit(ValueUnit.DEGREES)),
                        when(mode("pendiente"), number("slope_high", 1, 90, 1, 5, PaintConfig.DEFAULT_SLOPE_HIGH)
                                .unit(ValueUnit.DEGREES)),
                        when(mode("altura"), auto("height_low", -64, 320, 1, 8, 0, 60).unit(ValueUnit.Y)),
                        when(mode("altura"), auto("height_high", -64, 320, 1, 8, 0, 120).unit(ValueUnit.Y)),
                        when(mode("ruido"), number("noise_size", 2, 128, 1, 8, PaintConfig.DEFAULT_NOISE_SIZE)
                                .unit(ValueUnit.BLOCKS)),
                        when(PaintCard::byPalette, number("layers", 1, 8, 1, 2, 1).unit(ValueUnit.LAYERS)),
                        when(PaintCard::byPalette, toggle("surface_only", true)),
                        when(PaintCard::byPalette, toggle("shaped", true)),
                        when(PaintCard::byPalette, toggle("any_block", false)),
                        when(PaintCard::byPalette, new ToolParam.Text("only_blocks", "", 128, false, null)),
                        mask(),
                        only(ToolUse.BRUSH, when(PaintCard::byPalette, toggle("brush_preview", false)))
                ));
    }

    /** Las paletas de serie del pincel y, al final, la escrita a mano. */
    static List<String> palettes() {
        List<String> options = new ArrayList<>(PaintPalettes.descriptions().keySet());
        options.add(CUSTOM);
        return List.copyOf(options);
    }

    private static Predicate<ToolValues> mode(String gradient) {
        return values -> values.choice("gradient").equals(gradient);
    }

    /** El modo Natural: una paleta de superficie por reglas en vez de un degradado. */
    static boolean natural(ToolValues values) {
        return values.choice("gradient").equals(NATURAL);
    }

    /** Los modos que reparten una paleta de tonos (todos menos Natural). */
    static boolean byPalette(ToolValues values) {
        return !natural(values);
    }

    /**
     * Si la forma de la huella cuenta: en el pincel, siempre (es la forma del toque); en la selección, solo con el
     * modo Huella, donde la huella se estira hasta cubrir la selección y reparte la paleta del centro al borde.
     */
    static boolean footprint(ToolValues values) {
        if (natural(values)) {
            return false;
        }
        return !values.choice(ToolUse.PARAM).equals(ToolUse.SELECTION.id()) || values.choice("gradient")
                .equals("huella");
    }

    /** Cómo se reparte la paleta: lo primero, porque decide qué paleta y qué ajustes salen. */
    @Override
    public String modeParam() {
        return "gradient";
    }

    /** La forma del pincel se guarda como {@code stamp} (comparte las opciones con los sellos), pero es la huella. */
    @Override
    public String labelKey(ToolParam param) {
        return param.id().equals("stamp") ? "gui.komaedit.tools.param.footprint" : super.labelKey(param);
    }

    @Override
    public List<String> essentials() {
        // Lo de empezar: cómo se reparte, qué colores, qué forma, el tamaño, la fuerza y la semilla.
        return List.of(ToolUse.PARAM, "gradient", "paint_palette", "custom_palette", "palette", "stamp", "radius",
                "strength", "seed");
    }

    /** Dos galerías: las paletas en dos filas y las huellas en una (con ‹ ›), para que lo demás quepa a la vista. */
    @Override
    public int galleryRows(String paramId) {
        return paramId.equals("stamp") ? 1 : 2;
    }

    /** La paleta de tonos que va en la orden: la de la galería o la escrita. */
    static String palette(ToolValues values) {
        String chosen = values.choice("paint_palette");
        if (!chosen.equals(CUSTOM)) {
            return chosen;
        }
        String typed = values.text("custom_palette").strip();
        return typed.isEmpty() ? PaintPalettes.DEFAULT : typed;
    }

    @Override
    public String command(ToolValues values, boolean preview) {
        boolean brush = use(values) == ToolUse.BRUSH;
        if (natural(values)) {
            // La superficie natural: en la selección, la orden de siempre; con el pincel, en cada clic.
            CommandLine line = brush
                    ? new CommandLine("komaedit pincel paint " + values.choice("palette"))
                    .word(values.formatted("radius")).word(NATURAL)
                    : new CommandLine("komaedit pintar " + values.choice("palette")).word(NATURAL);
            return line.flag("-m", values.text("mask")).flag(previewFlag(), preview && !brush).build();
        }
        CommandLine line = new CommandLine(brush ? "komaedit pincel paint " + palette(values)
                : "komaedit pintar " + palette(values));
        if (brush) {
            line.word(values.formatted("radius"));
        }
        line.word(values.choice("gradient")).word(values.formatted("strength"));
        if (values.param("stamp").visible(values) && !values.choice("stamp").equals(FootprintLibrary.CIRCLE)) {
            line.flag("-h", values.choice("stamp"));
        }
        line.flag("-s", values.formatted("seed"));
        if (brush) {
            line.flag("-k", values.toggle("lock_seed")).changed(values, "dab_spacing", "-e");
        }
        if (values.param("hardness").visible(values)) {
            line.changed(values, "hardness", "-u");
        }
        line.changed(values, "variation", "-j");
        if (values.param("slope_low").visible(values)
                && !(values.isDefault("slope_low") && values.isDefault("slope_high"))) {
            line.flag("-a", range(values.number("slope_low", PaintConfig.DEFAULT_SLOPE_LOW),
                    values.number("slope_high", PaintConfig.DEFAULT_SLOPE_HIGH)));
        }
        Integer low = values.number("height_low");
        Integer high = values.number("height_high");
        if (values.param("height_low").visible(values) && low != null && high != null) {
            line.flag("-y", range(low, high));
        }
        if (values.param("noise_size").visible(values)) {
            line.changed(values, "noise_size", "-z");
        }
        line.changed(values, "layers", "-d")
                .flag("-t", !values.toggle("surface_only"))
                .flag("-c", !values.toggle("shaped"))
                .flag("-x", values.toggle("any_block"))
                .flag("-b", values.text("only_blocks"))
                .flag("-m", values.text("mask"));
        if (brush) {
            return line.flag("-p", values.toggle("brush_preview")).build();
        }
        return line.flag(previewFlag(), preview).build();
    }

    /** {@code desde..hasta}; si coinciden, el final sube uno (el pincel no admite una franja vacía). */
    private static String range(int from, int to) {
        int low = Math.min(from, to);
        int high = Math.max(from, to);
        return low + ".." + (high == low ? high + 1 : high);
    }

    /**
     * El pincel de pintura; la superficie natural sobre la selección, la de «Pintar superficie»; y con el pincel, las
     * dos cosas: el pincel de zona que repite esa orden en cada clic.
     */
    @Override
    public List<String> permissions(ToolValues values) {
        if (!natural(values)) {
            return List.of(NODE);
        }
        return use(values) == ToolUse.SELECTION ? List.of(SURFACE_NODE)
                : List.of(NODE, TouchUpCard.ZONE_NODE, SURFACE_NODE);
    }

    @Override
    public java.util.Set<String> allPermissions() {
        return new java.util.LinkedHashSet<>(List.of(NODE, SURFACE_NODE, TouchUpCard.ZONE_NODE));
    }

    /** Estimar, como hacía «Pintar superficie»: solo la superficie natural sobre la selección lo sabe hacer. */
    @Override
    public List<ToolAction> actions(ToolValues values) {
        if (!natural(values) || use(values) != ToolUse.SELECTION) {
            return List.of();
        }
        return List.of(new ToolAction("estimate", List.of(SURFACE_NODE), false,
                current -> command(current, false) + " -e"));
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(new ToolAction("estimate", List.of(SURFACE_NODE), false, values -> command(values, false)
                + " -e"));
    }

    @Override
    public List<ToolPreset> presets() {
        return List.of(
                ToolPreset.of("paint_hillside", "paint_palette", "hierba_piedra", "gradient", "pendiente"),
                ToolPreset.of("paint_peaks", "paint_palette", "cumbres", "gradient", "altura", "stamp", "montana",
                        "radius", 16),
                ToolPreset.of("paint_patches", "paint_palette", "musgo", "gradient", "ruido", "noise_size", 8,
                        "strength", 60),
                ToolPreset.of("paint_desert", "paint_palette", "desierto", "gradient", "pendiente", "stamp", "dunas",
                        "radius", 14));
    }

    @Override
    public List<ToolProblem> validate(ToolValues values) {
        List<ToolProblem> problems = new ArrayList<>();
        if (values.param("custom_palette").visible(values)) {
            noSpaces(values, "custom_palette", problems);
        }
        if (values.param("only_blocks").visible(values)) {
            noSpaces(values, "only_blocks", problems);
        }
        noSpaces(values, "mask", problems);
        return problems;
    }
}
