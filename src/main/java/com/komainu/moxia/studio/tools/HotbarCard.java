package com.komainu.moxia.studio.tools;

import com.komainu.moxia.hotbar.HotbarRules;
import com.komainu.moxia.terrain.SurfacePalette;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Barras guardadas en el Studio: arriba la lista de las del jugador (clic para usarla, guardar la que lleva, pegar y
 * copiar códigos; lo dibuja {@code HotbarsPanel} y va por el paquete de las barras) y debajo «Crear barra», que
 * manda {@code /komaedit hotbar <fuente> <nombre> …} para hacer una desde una paleta, un degradado de color, la
 * selección, un sello de relieve o un tejado.
 */
public final class HotbarCard extends ToolCard {
    public static final List<String> SOURCES = List.of("palette", "gradient", "selection", "stamp", "roof");
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    HotbarCard() {
        super("hotbars", ToolTarget.MODE,
                List.of("hotbar", "hotbars", "barra", "barras", "barra rapida", "swapper", "axiom", "inventario",
                        "guardar barra", "paleta", "degradado"),
                List.of(
                        new ToolParam.Text("name", "nueva", 32, true, null),
                        choice("source", ToolParam.Choice.Style.BUTTONS, SOURCES.toArray(String[]::new)),
                        when(values -> values.choice("source").equals("palette"),
                                new ToolParam.Text("palette", "##Magma", 256, true, null)),
                        when(values -> values.choice("source").equals("gradient"), block("from", "deepslate")),
                        when(values -> values.choice("source").equals("gradient"), block("to", "calcite")),
                        when(values -> values.choice("source").equals("stamp"),
                                new ToolParam.Choice("surface", List.copyOf(SurfacePalette.presets().keySet()),
                                        SurfacePalette.DEFAULT, ToolParam.Choice.Style.CYCLE, "", null)),
                        when(values -> values.choice("source").equals("roof"), block("material", "deepslate_tiles")),
                        toggle("load", true),
                        toggle("overwrite", false)
                ));
    }

    @Override
    public List<String> essentials() {
        return List.of("name", "source", "palette", "from", "to", "surface", "material");
    }

    @Override
    public String command(ToolValues values, boolean preview) {
        String source = values.choice("source");
        CommandLine line = new CommandLine("komaedit hotbar " + source + " " + name(values));
        switch (source) {
            case "palette" -> line.word(values.text("palette"));
            case "gradient" -> line.word(values.block("from")).word(values.block("to"));
            case "stamp" -> line.word(values.choice("surface"));
            case "roof" -> line.word(values.block("material"));
            default -> {
            }
        }
        return line.flag("-l", values.toggle("load")).flag("-f", values.toggle("overwrite")).build();
    }

    /** El nombre como lo guarda el servidor. */
    public static String name(ToolValues values) {
        String name = values.text("name").strip().toLowerCase(Locale.ROOT);
        return name.startsWith("#") ? name.substring(1) : name;
    }

    @Override
    public boolean previewable() {
        return false;
    }

    @Override
    public List<String> permissions(ToolValues values) {
        return List.of(HotbarRules.PERMISSION);
    }

    @Override
    public ToolProblem requirement(ToolValues values, ToolContext context) {
        return values.choice("source").equals("selection") && context.known() && !context.hasSelection()
                ? ToolProblem.error("no_selection") : null;
    }

    @Override
    public List<ToolProblem> validate(ToolValues values) {
        List<ToolProblem> problems = new ArrayList<>();
        if (!NAME.matcher(name(values)).matches()) {
            problems.add(ToolProblem.error("hotbar_name"));
        }
        if (values.param("palette").visible(values) && values.text("palette").isBlank()) {
            problems.add(ToolProblem.error("hotbar_palette"));
        }
        for (String block : List.of("from", "to", "material")) {
            if (values.param(block).visible(values)) {
                if (values.block(block) == null) {
                    problems.add(ToolProblem.error("material"));
                } else {
                    noSpaces(values, block, problems);
                }
            }
        }
        return problems;
    }
}
