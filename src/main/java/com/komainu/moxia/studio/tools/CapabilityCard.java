package com.komainu.moxia.studio.tools;

import com.komainu.moxia.capability.BuildCapability;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Capacidades de construcción a mano (como el menú rápido de la Z), con un interruptor por capacidad y sus números.
 * Cada cambio manda la orden {@code /komaedit capability …}; el estado vuelve del servidor con el mismo paquete que
 * usa el menú rápido.
 */
public final class CapabilityCard extends ToolCard {
    CapabilityCard() {
        super("capabilities", ToolFamily.CAPABILITIES, ToolTarget.MODE,
                List.of("capability", "capacidad", "capacidades", "axiom", "builder", "reemplazar", "replace",
                        "extruir", "extrude", "alcance", "reach", "angel", "aire", "rapida", "fastplace",
                        "sin actualizaciones", "noupdates", "vuelo", "fly"),
                List.of(
                        number("angel", 1, 64, 1, 4, 4),
                        number("reach", 5, 64, 1, 4, 24),
                        number("extrude", 1, 65_536, 16, 256, 256),
                        decimal("flyspeed", 5, 200, 5, 10, 10, 1)
                ));
    }

    /** {@code komaedit capability <id> on|off}. */
    public static String toggleCommand(BuildCapability capability, boolean on) {
        return "komaedit capability " + capability.id() + " " + (on ? "on" : "off");
    }

    /**
     * Número de una capacidad con {@code option}: {@code distance} (Colocar en el aire), {@code reach} (alcance) o
     * {@code limit} (bloques por capa al extruir). {@code capability extrude <n>} no sirve para más de 64.
     */
    public static String distanceCommand(BuildCapability capability, int value) {
        String option = switch (capability) {
            case ANGEL -> "distance";
            case REACH -> "reach";
            case EXTRUDE -> "limit";
            default -> throw new IllegalArgumentException(capability.id() + " no tiene número");
        };
        return optionCommand(option, Integer.toString(value));
    }

    /** {@code komaedit capability option family on}. */
    public static String optionCommand(String option, String value) {
        return "komaedit capability option " + option + " " + value;
    }

    /** La velocidad de vuelo va en la orden como multiplicador ({@code 2.5}). */
    public static String flySpeedCommand(ToolValues values) {
        return optionCommand("flyspeed", values.formatted("flyspeed"));
    }

    /** Sin Construir: los interruptores mandan cada uno su orden. Construir lista el estado en el chat. */
    @Override
    public String command(ToolValues values, boolean preview) {
        return "komaedit capability";
    }

    @Override
    public boolean previewable() {
        return false;
    }

    @Override
    public List<String> permissions(ToolValues values) {
        return List.of();
    }

    @Override
    public Set<String> allPermissions() {
        Set<String> nodes = new LinkedHashSet<>();
        Arrays.stream(BuildCapability.values()).map(BuildCapability::permission).forEach(nodes::add);
        nodes.add(BuildCapability.FLY_SPEED_PERMISSION);
        return nodes;
    }
}
