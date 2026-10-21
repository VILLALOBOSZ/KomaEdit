package com.komainu.moxia.studio.tools;

import com.komainu.moxia.blueprint.BlueprintPermissions;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.studio.ValueUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /komaedit rebuild}: la reconstrucción animada de la selección, el portapapeles o un plano, con su orden y su
 * ritmo. Los botones de la tarjeta la pausan, la reanudan, la aceleran, la llevan al final o la cancelan.
 */
public final class RebuildCard extends ToolCard {
    public static final String NODE = KomaPermissions.REBUILD;
    /** Opción de la tarjeta → palabra de la orden. */
    private static final Map<String, String> ORDERS = Map.of("builder", "constructor", "layers", "capas", "waves",
            "ondas", "axis", "eje", "random", "azar", "history", "historial");
    private static final Map<String, String> AXES = Map.of("x", "x", "nx", "-x", "y", "y", "ny", "-y", "z", "z",
            "nz", "-z");

    RebuildCard() {
        super("rebuild", ToolTarget.SELECTION,
                List.of("rebuild", "reconstruir", "reconstruccion", "animar", "animacion", "timelapse", "video",
                        "levantar", "obra", "axiom", "animated"),
                List.of(
                        choice("source", ToolParam.Choice.Style.BUTTONS, "selection", "clipboard", "blueprint"),
                        when(RebuildCard::fromBlueprint, new ToolParam.Text("blueprint", "", 64, false, null)),
                        choice("order", ToolParam.Choice.Style.BUTTONS, "builder", "layers", "waves", "axis",
                                "random", "history"),
                        when(values -> values.choice("order").equals("axis"),
                                choice("sweep", ToolParam.Choice.Style.BUTTONS, "x", "nx", "y", "ny", "z", "nz")),
                        decimal("blocks_per_tick", 1, 2_560, 5, 40, 40, 1).unit(ValueUnit.BLOCKS_PER_TICK),
                        auto("duration", 1, 3_600, 1, 10, 0, 30).unit(ValueUnit.SECONDS),
                        decimal("layer_pause", 0, 600, 1, 10, 0, 1).unit(ValueUnit.SECONDS),
                        decimal("same_delay", 0, 100, 1, 5, 0, 1).unit(ValueUnit.SECONDS),
                        decimal("other_delay", 0, 100, 1, 5, 0, 1).unit(ValueUnit.SECONDS),
                        decimal("lead_in", 0, 100, 5, 10, 10, 1).unit(ValueUnit.SECONDS),
                        toggle("particles", false),
                        toggle("sound", false),
                        when(values -> !values.choice("source").equals("selection"), toggle("skip_air", false)),
                        when(values -> values.choice("order").equals("random"), seed())
                ));
    }

    private static boolean fromBlueprint(ToolValues values) {
        return values.choice("source").equals("blueprint");
    }

    @Override
    public List<String> essentials() {
        return List.of("source", "blueprint", "order", "sweep", "blocks_per_tick", "duration");
    }

    @Override
    public String command(ToolValues values, boolean preview) {
        CommandLine line = new CommandLine("komaedit rebuild");
        switch (values.choice("source")) {
            case "clipboard" -> line.word("portapapeles");
            case "blueprint" -> line.word("plano").word(values.text("blueprint"));
            default -> {
            }
        }
        String order = values.choice("order");
        if (!order.equals("builder")) {
            line.word(ORDERS.get(order));
        }
        if (order.equals("axis") && !values.choice("sweep").equals("x")) {
            line.flag("-e", AXES.get(values.choice("sweep")));
        }
        if (values.number("duration") != null) {
            line.flag("-t", values.formatted("duration"));
        } else {
            line.changed(values, "blocks_per_tick", "-b");
        }
        line.changed(values, "layer_pause", "-p")
                .changed(values, "same_delay", "-i")
                .changed(values, "other_delay", "-d")
                .changed(values, "lead_in", "-w")
                .flag("-f", values.toggle("particles"))
                .flag("-n", values.toggle("sound"));
        if (values.param("skip_air").visible(values)) {
            line.flag("-a", values.toggle("skip_air"));
        }
        if (order.equals("random") && values.number("seed") != null) {
            line.flag("-s", values.formatted("seed"));
        }
        return line.build();
    }

    /** No coloca por el plan común: no hay vista previa, se ve levantarse. */
    @Override
    public boolean previewable() {
        return false;
    }

    @Override
    public List<String> permissions(ToolValues values) {
        return fromBlueprint(values) ? List.of(NODE, BlueprintPermissions.LOAD) : List.of(NODE);
    }

    @Override
    public Set<String> allPermissions() {
        Set<String> nodes = super.allPermissions();
        nodes.add(BlueprintPermissions.LOAD);
        return nodes;
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(
                control("rebuild_pause", "komaedit rebuild pausar"),
                control("rebuild_resume", "komaedit rebuild seguir"),
                control("rebuild_faster", "komaedit rebuild acelerar"),
                control("rebuild_finish", "komaedit rebuild terminar"),
                control("rebuild_cancel", "komaedit rebuild cancelar"));
    }

    private static ToolAction control(String id, String command) {
        return new ToolAction(id, List.of(NODE), false, values -> command);
    }

    @Override
    public List<ToolPreset> presets() {
        return List.of(
                ToolPreset.of("timelapse", "order", "builder", "duration", 60, "layer_pause", 5, "particles", true),
                ToolPreset.of("printer", "order", "layers", "blocks_per_tick", 20),
                ToolPreset.of("magic", "order", "waves", "blocks_per_tick", 80, "particles", true, "sound", true),
                ToolPreset.of("quick", "order", "builder", "blocks_per_tick", 320));
    }

    /** Un preset cambia cómo se levanta, no qué: el origen y el plano se quedan. */
    @Override
    protected boolean keptByPresets(ToolParam param) {
        return param.id().equals("source") || param.id().equals("blueprint") || super.keptByPresets(param);
    }

    /** La selección solo hace falta si se reconstruye la selección. */
    @Override
    public ToolProblem requirement(ToolValues values, ToolContext context) {
        return values.choice("source").equals("selection") ? super.requirement(values, context) : null;
    }

    @Override
    public List<ToolProblem> validate(ToolValues values) {
        List<ToolProblem> problems = new ArrayList<>();
        if (fromBlueprint(values) && values.text("blueprint").isBlank()) {
            problems.add(ToolProblem.error("blueprint_name"));
        }
        noSpaces(values, "blueprint", problems);
        return problems;
    }
}
