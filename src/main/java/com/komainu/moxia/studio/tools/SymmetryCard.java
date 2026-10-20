package com.komainu.moxia.studio.tools;

import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.symmetry.SymmetryMode;

import java.util.List;
import java.util.Set;

/**
 * {@code /komaedit symmetry}: modo, brazos del radial, centro y encendido. El estado de verdad lo tiene el servidor
 * (llega con el estado de herramientas); la tarjeta solo arma las órdenes.
 */
public final class SymmetryCard extends ToolCard {
    public static final String REFLECTIONAL = KomaPermissions.BUILD + ".symmetry.reflectional";
    public static final String ROTATIONAL = KomaPermissions.BUILD + ".symmetry.rotational";
    public static final String WORLDEDIT = KomaPermissions.BUILD + ".symmetry.worldedit";
    public static final List<String> MODES = List.of("x", "z", "xz", "y", "xy", "zy", "xzy", "radial");

    SymmetryCard() {
        super("symmetry", ToolFamily.SYMMETRY, ToolTarget.MODE,
                List.of("symmetry", "simetria", "simetría", "sym", "espejo", "mirror", "radial", "reflejo"),
                List.of(
                        new ToolParam.Choice("mode", MODES, "x", ToolParam.Choice.Style.BUTTONS, "", null),
                        when(SymmetryCard::radial, number("arms", SymmetryMode.MIN_ARMS, SymmetryMode.MAX_ARMS, 1, 2,
                                SymmetryMode.DEFAULT_ARMS)),
                        when(SymmetryCard::radial, toggle("vertical", false))
                ));
    }

    private static boolean radial(ToolValues values) {
        return values.choice("mode").equals("radial");
    }

    /** El modo como lo lee la orden: {@code xz}, {@code radial 8} o {@code radial 8 y}. */
    public static String modeArgument(ToolValues values) {
        if (!radial(values)) {
            return values.choice("mode");
        }
        return "radial " + values.formatted("arms") + (values.toggle("vertical") ? " y" : "");
    }

    /** Activa con el modo elegido (si ya estaba activa, solo cambia el modo). */
    @Override
    public String command(ToolValues values, boolean preview) {
        return "komaedit symmetry on " + modeArgument(values);
    }

    @Override
    public boolean previewable() {
        return false;
    }

    @Override
    public List<String> permissions(ToolValues values) {
        return List.of(radial(values) ? ROTATIONAL : REFLECTIONAL);
    }

    @Override
    public Set<String> allPermissions() {
        return new java.util.LinkedHashSet<>(List.of(REFLECTIONAL, ROTATIONAL, WORLDEDIT));
    }

    /** Apagar no necesita el permiso del modo: vale con tener alguno de simetría. */
    @Override
    public List<ToolAction> actions() {
        return List.of(
                new ToolAction("symmetry_off", List.of(), false, values -> "komaedit symmetry off"),
                new ToolAction("center_here", List.of(), false, values -> "komaedit symmetry center here"),
                new ToolAction("center_half", List.of(), false, values -> "komaedit symmetry center here half"),
                new ToolAction("center_selection", List.of(), false,
                        values -> "komaedit symmetry center selection"));
    }

    /** La orden del interruptor «Reflejar WorldEdit y KomaEdit». */
    public static String worldEditCommand(boolean on) {
        return "komaedit symmetry worldedit " + (on ? "on" : "off");
    }

    /** Valores de la tarjeta con el modo que tiene el servidor ({@code xz}, {@code radial}…) y sus brazos. */
    public ToolValues fromServer(ToolValues values, String modeId, int arms) {
        if (modeId == null || modeId.isBlank()) {
            return values;
        }
        String normalized = modeId.strip().toLowerCase(java.util.Locale.ROOT);
        boolean vertical = false;
        if (normalized.startsWith("radial")) {
            vertical = normalized.endsWith("y");
            normalized = "radial";
        }
        if (!MODES.contains(normalized)) {
            return values;
        }
        ToolValues result = values.with("mode", normalized).with("arms", arms);
        return normalized.equals("radial") ? result.with("vertical", vertical) : result;
    }
}
