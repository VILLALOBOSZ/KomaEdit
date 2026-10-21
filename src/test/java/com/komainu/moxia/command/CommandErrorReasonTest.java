package com.komainu.moxia.command;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que se le dice al jugador cuando una orden falla: el texto de los errores de uso tal cual, y de un fallo
 * inesperado una frase en español sin el texto de Java (antes salía «For input string: "99999999999"» con
 * {@code /komaedit color similar 99999999999}, «Index 0 out of bounds for length 0» con
 * {@code /komaedit shape cone stone , 5} o «nullpointerexception» si la excepción no traía texto).
 */
class CommandErrorReasonTest {
    @Test
    void usageErrorsKeepTheirText() {
        assertEquals("Falta el radio", EzCommandSupport.reason(new IllegalArgumentException("Falta el radio")));
        assertEquals("Ya hay una edición en marcha",
                EzCommandSupport.reason(new IllegalStateException("Ya hay una edición en marcha")));
        assertEquals("Falta el radio", EzCommandSupport.reason(new CompletionException(
                new IllegalArgumentException("Falta el radio"))), "lo que llega de otro hilo, con su causa");
        assertEquals("Falta el radio", EzCommandSupport.reason(new ExecutionException(
                new IllegalArgumentException("Falta el radio"))));
    }

    @Test
    void unexpectedFailuresNeverShowJava() {
        for (Throwable failure : new Throwable[]{
                new NumberFormatException("For input string: \"99999999999\""),
                new ArrayIndexOutOfBoundsException("Index 0 out of bounds for length 0"),
                new NullPointerException(),
                new NullPointerException("Cannot invoke \"String.length()\" because \"text\" is null"),
                new IllegalArgumentException(),
                new CompletionException(new ArithmeticException("/ by zero")),
                new RuntimeException(new UnsupportedOperationException())}) {
            String reason = EzCommandSupport.reason(failure);
            assertFalse(reason.isBlank(), failure.toString());
            assertFalse(reason.toLowerCase(java.util.Locale.ROOT).contains("exception"), reason);
            assertFalse(reason.contains("For input string") || reason.contains("out of bounds")
                    || reason.contains("Cannot invoke") || reason.contains("/ by zero"), reason);
            assertTrue(reason.contains("registro del servidor"), "dice dónde está el detalle: " + reason);
        }
    }
}
