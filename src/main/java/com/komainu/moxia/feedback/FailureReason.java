package com.komainu.moxia.feedback;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEditException;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

/**
 * El motivo de un fallo, para el jugador. Los errores de uso (un número fuera de rango, una orden mal escrita, un
 * límite de WorldEdit) dicen lo que pasó tal cual; lo inesperado (un {@code NullPointerException}, un índice fuera de
 * rango, un fichero que no se pudo leer) no enseña ni su texto en inglés ni el nombre de su clase: dice que avise a un
 * admin y apunta el detalle en el registro. Es lo mismo que hacen las órdenes ({@code EzCommandSupport.reason}) para
 * los servicios que avisan por su cuenta.
 */
public final class FailureReason {
    /** Lo que lee el jugador cuando el fallo no es suyo. */
    public static final String UNEXPECTED =
            "Fallo inesperado de KomaEdit: avisa a un admin (el detalle está en el registro del servidor)";

    /** Lo mismo cuando el fallo es del propio cliente (la vista previa, una imagen de referencia). */
    public static final String UNEXPECTED_CLIENT =
            "Fallo inesperado de KomaEdit (el detalle está en el registro del juego)";

    private FailureReason() {
    }

    /** Para lo que falla en el cliente: el detalle va al registro del juego. */
    public static String client(Throwable throwable) {
        String reason = of(throwable, (text, failure) -> com.komainu.moxia.KomaEditMod.LOGGER.warn(text, failure));
        return reason.equals(UNEXPECTED) ? UNEXPECTED_CLIENT : reason;
    }

    /** El motivo legible; lo inesperado va al registro del servidor con su traza. */
    public static String of(Throwable throwable) {
        return of(throwable, (text, failure) -> com.komainu.moxia.KomaEditMod.LOGGER.error(text, failure));
    }

    /** Como {@link #of(Throwable)}, apuntando lo inesperado con {@code log} (texto y fallo). */
    public static String of(Throwable throwable, BiConsumer<String, Throwable> log) {
        if (throwable == null) {
            return UNEXPECTED;
        }
        Throwable current = unwrap(throwable);
        if (usage(current) && !blank(current.getMessage())) {
            return current.getMessage().strip();
        }
        // Los de WorldEdit llevan su texto en otro sitio (traducible): los que más salen, dichos aquí.
        if (current instanceof MaxChangedBlocksException limit) {
            return "WorldEdit llegó a su límite de " + limit.getBlockLimit() + " bloques por operación · //limit lo"
                    + " sube";
        }
        if (current instanceof IncompleteRegionException) {
            return "Primero haz una selección de WorldEdit";
        }
        log.accept("Unexpected failure in KomaEdit", throwable);
        if (current instanceof WorldEditException) {
            return "WorldEdit no lo permitió (el detalle está en el registro del servidor)";
        }
        return UNEXPECTED;
    }

    /** Si el fallo es un error de uso que el jugador puede leer (y arreglar). */
    public static boolean usage(Throwable throwable) {
        Throwable current = unwrap(throwable);
        return current instanceof IllegalArgumentException && !(current instanceof NumberFormatException)
                || current instanceof IllegalStateException || current instanceof WorldEditException;
    }

    /** Quita los envoltorios de otro hilo y los que no dicen nada (o solo repiten su causa con su clase). */
    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current
                && (current instanceof CompletionException || current instanceof ExecutionException
                || current instanceof InvocationTargetException || blank(current.getMessage())
                || current.getMessage().equals(current.getCause().toString()))) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
