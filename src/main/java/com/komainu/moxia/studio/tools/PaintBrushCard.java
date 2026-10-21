package com.komainu.moxia.studio.tools;

import com.komainu.moxia.brush.paint.FootprintLibrary;
import com.komainu.moxia.brush.paint.PaintConfig;
import com.komainu.moxia.brush.paint.PaintPalettes;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.studio.ValueUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /komaedit pincel paint}: «Pintar terreno». A la vista, lo que hace falta para empezar (la paleta con su
 * muestra, la huella, el degradado, el tamaño, la fuerza y la semilla); el resto, en «Más ajustes». De serie pinta
 * hierba en lo llano y piedra en lo empinado.
 */
public final class PaintBrushCard extends ToolCard {
    public static final String NODE = KomaPermissions.BUILD + ".brush.paint";
    /** La opción de la galería que usa la paleta escrita a mano. */
    public static final String CUSTOM = "propia";

    PaintBrushCard() {
        super("paintbrush", ToolFamily.TERRAIN, ToolTarget.HAND,
                List.of("pintar", "paint", "pintura", "pincel", "brush", "degradado", "gradient", "color", "terreno",
                        "terrain", "superficie", "hierba", "piedra", "axiom"),
                List.of(
                        new ToolParam.Choice("paint_palette", palettes(), PaintPalettes.DEFAULT,
                                ToolParam.Choice.Style.GALLERY, "", null),
                        when(values -> values.choice("paint_palette").equals(CUSTOM),
                                new ToolParam.Text("custom_palette", "##Grayscale", 128, false, null)),
                        new ToolParam.Choice("stamp", FootprintLibrary.builtIn().names(), FootprintLibrary.CIRCLE,
                                ToolParam.Choice.Style.GALLERY, "", null),
                        new ToolParam.Choice("gradient", List.of("pendiente", "altura", "ruido", "huella"),
                                PaintConfig.DEFAULT_GRADIENT.id(), ToolParam.Choice.Style.BUTTONS, "", null),
                        number("radius", 1, PaintConfig.MAX_RADIUS, 1, 4, PaintConfig.DEFAULT_RADIUS)
                                .unit(ValueUnit.BLOCKS),
                        number("strength", 1, 100, 5, 25, PaintConfig.DEFAULT_STRENGTH)
                                .unit(ValueUnit.PERCENT_POINTS),
                        seed(),
                        toggle("lock_seed", false),
                        number("dab_spacing", 5, 200, 5, 25, PaintConfig.DEFAULT_SPACING)
                                .unit(ValueUnit.PERCENT_POINTS),
                        number("hardness", 0, 100, 5, 25, PaintConfig.DEFAULT_HARDNESS)
                                .unit(ValueUnit.PERCENT_POINTS),
                        number("variation", 0, 100, 5, 25, PaintConfig.DEFAULT_JITTER).unit(ValueUnit.PERCENT_POINTS),
                        when(values -> values.choice("gradient").equals("pendiente"),
                                number("slope_low", 0, 89, 1, 5, PaintConfig.DEFAULT_SLOPE_LOW)
                                        .unit(ValueUnit.DEGREES)),
                        when(values -> values.choice("gradient").equals("pendiente"),
                                number("slope_high", 1, 90, 1, 5, PaintConfig.DEFAULT_SLOPE_HIGH)
                                        .unit(ValueUnit.DEGREES)),
                        when(values -> values.choice("gradient").equals("altura"),
                                auto("height_low", -64, 320, 1, 8, 0, 60).unit(ValueUnit.Y)),
                        when(values -> values.choice("gradient").equals("altura"),
                                auto("height_high", -64, 320, 1, 8, 0, 120).unit(ValueUnit.Y)),
                        when(values -> values.choice("gradient").equals("ruido"),
                                number("noise_size", 2, 128, 1, 8, PaintConfig.DEFAULT_NOISE_SIZE)
                                        .unit(ValueUnit.BLOCKS)),
                        number("layers", 1, 8, 1, 2, 1).unit(ValueUnit.LAYERS),
                        toggle("surface_only", true),
                        toggle("shaped", true),
                        toggle("any_block", false),
                        new ToolParam.Text("only_blocks", "", 128, false, null),
                        mask(),
                        toggle("brush_preview", false)
                ));
    }

    /** Las paletas de serie del pincel y, al final, la escrita a mano. */
    static List<String> palettes() {
        List<String> options = new ArrayList<>(PaintPalettes.descriptions().keySet());
        options.add(CUSTOM);
        return List.copyOf(options);
    }

    @Override
    public List<String> essentials() {
        // Lo de empezar: qué colores, qué forma, cómo se reparten, el tamaño, la fuerza y la semilla. Bloquearla, en Más
        // ajustes.
        return List.of("paint_palette", "custom_palette", "stamp", "gradient", "radius", "strength", "seed");
    }

    /** Dos galerías: las paletas en dos filas y las huellas en una (con ‹ ›), para que lo demás quepa a la vista. */
    @Override
    public int galleryRows(String paramId) {
        return paramId.equals("stamp") ? 1 : 2;
    }

    /** La paleta que va en la orden: la de la galería o la escrita. */
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
        CommandLine line = new CommandLine("komaedit pincel paint " + palette(values))
                .word(values.formatted("radius"))
                .word(values.choice("gradient"))
                .word(values.formatted("strength"));
        if (!values.choice("stamp").equals(FootprintLibrary.CIRCLE)) {
            line.flag("-h", values.choice("stamp"));
        }
        line.flag("-s", values.formatted("seed"))
                .flag("-k", values.toggle("lock_seed"))
                .changed(values, "dab_spacing", "-e")
                .changed(values, "hardness", "-u")
                .changed(values, "variation", "-j");
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
        return line.changed(values, "layers", "-d")
                .flag("-t", !values.toggle("surface_only"))
                .flag("-c", !values.toggle("shaped"))
                .flag("-x", values.toggle("any_block"))
                .flag("-b", values.text("only_blocks"))
                .flag("-m", values.text("mask"))
                .flag("-p", values.toggle("brush_preview"))
                .build();
    }

    /** {@code desde..hasta}; si coinciden, el final sube uno (el pincel no admite una franja vacía). */
    private static String range(int from, int to) {
        int low = Math.min(from, to);
        int high = Math.max(from, to);
        return low + ".." + (high == low ? high + 1 : high);
    }

    @Override
    public boolean previewable() {
        return false;
    }

    @Override
    public List<String> permissions(ToolValues values) {
        return List.of(NODE);
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
        noSpaces(values, "only_blocks", problems);
        noSpaces(values, "mask", problems);
        return problems;
    }
}
