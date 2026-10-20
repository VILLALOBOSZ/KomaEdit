package com.komainu.moxia.blueprint;

import com.komainu.moxia.persistence.NamedDefinitions;

import java.util.Locale;

/**
 * Cómo se nombra un plano en las órdenes: {@code roca_grande} busca primero entre los tuyos y después entre los
 * compartidos del servidor; {@code servidor/roca_grande} (o {@code server/…}) va directo a los compartidos.
 *
 * @param sharedOnly solo entre los compartidos del servidor
 */
public record BlueprintRef(String name, boolean sharedOnly) {
    public static final String SHARED_PREFIX = "servidor/";
    private static final String[] PREFIXES = {"servidor/", "server/", "shared/", "compartido/"};

    public static BlueprintRef parse(String input) {
        String text = input == null ? "" : input.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String prefix : PREFIXES) {
            if (lower.startsWith(prefix)) {
                return new BlueprintRef(name(text.substring(prefix.length())), true);
            }
        }
        if (text.startsWith("#")) {
            throw new IllegalArgumentException("«" + text + "» es una etiqueta; aquí va el nombre de un plano");
        }
        return new BlueprintRef(name(text), false);
    }

    /** Nombre válido de plano: de 1 a 32 letras, números, {@code _} o {@code -} (no al principio), en minúsculas. */
    public static String name(String input) {
        if (input != null && input.trim().startsWith("#")) {
            throw new IllegalArgumentException("El nombre de un plano no empieza por #");
        }
        String name = NamedDefinitions.normalizeName(input);
        if (name.startsWith("-")) {
            // Sería una bandera: -v, -p…
            throw new IllegalArgumentException("El nombre de un plano no puede empezar por -");
        }
        return name;
    }

    /** Cómo se escribe un plano para que no haya dudas: con {@code servidor/} si es compartido. */
    public static String display(String name, BlueprintScope scope) {
        return scope == BlueprintScope.SHARED ? SHARED_PREFIX + name : name;
    }
}
