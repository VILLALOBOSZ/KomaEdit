package com.komainu.moxia.studio.net;

import net.minecraft.util.StringHelper;

import java.util.Optional;
import java.util.Set;

/**
 * Lo que lleva el paquete de los botones ({@link ButtonCommandPayload}): una orden de KomaEdit, sin barra, de hasta
 * {@value ButtonCommandPayload#MAX_COMMAND} caracteres y con los caracteres que admite el chat. Ni más ni menos: lo
 * demás (las órdenes de Minecraft o de otros mods) no tiene botones de KomaEdit y, si alguien lo manda por aquí, el
 * servidor no lo ejecuta. Java puro, para el cliente (que decide por dónde va cada orden) y el servidor (que lo
 * vuelve a mirar).
 */
public final class ButtonCommands {
    /** {@code /komaedit} y sus alias. */
    public static final Set<String> ROOTS = Set.of("komaedit", "ke", "moxia", "mx");

    private ButtonCommands() {
    }

    /** Por qué la orden no va por el paquete, o vacío si va. */
    public static Optional<String> problem(String command) {
        if (command == null || command.isBlank()) {
            return Optional.of("la orden está vacía");
        }
        if (command.length() > ButtonCommandPayload.MAX_COMMAND) {
            return Optional.of("la orden es demasiado larga");
        }
        for (int index = 0; index < command.length(); index++) {
            // Lo mismo que mira Minecraft en una orden de chat (que además echa a quien lo manda).
            if (!StringHelper.isValidChar(command.charAt(index))) {
                return Optional.of("la orden lleva caracteres que el chat no admite");
            }
        }
        if (!ROOTS.contains(root(command))) {
            return Optional.of("solo se aceptan órdenes de KomaEdit");
        }
        return Optional.empty();
    }

    /** Lo que se contesta a una orden que no cabe en la pila del hilo del servidor ({@link #withinStack}). */
    public static final String TOO_DEEP = "la orden está demasiado anidada (miles de «!» o de paréntesis): "
            + "escríbela más sencilla";

    /**
     * Ejecuta {@code run} y dice si cupo en la pila. Por el paquete de los botones y por el de la vista previa en vivo
     * llegan órdenes de hasta {@value ButtonCommandPayload#MAX_COMMAND} caracteres (por el chat, de 256): una máscara
     * de WorldEdit con miles de {@code !} o una expresión con miles de paréntesis agota la pila al leerse, y en el hilo
     * del servidor eso lo tumbaba entero.
     */
    public static boolean withinStack(Runnable run) {
        try {
            run.run();
            return true;
        } catch (StackOverflowError error) {
            return false;
        }
    }

    /** La primera palabra de la orden, tal cual (las órdenes distinguen mayúsculas). */
    public static String root(String command) {
        int space = command.indexOf(' ');
        return space < 0 ? command : command.substring(0, space);
    }
}
