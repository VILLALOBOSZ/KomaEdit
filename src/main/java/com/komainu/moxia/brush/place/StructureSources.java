package com.komainu.moxia.brush.place;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registro de estructuras que entiende el pincel de colocación, por nombre y abreviatura sin distinguir
 * mayúsculas. De serie: {@code Clipboard}/{@code Cl}. Otras partes del mod añaden las suyas con {@link #register}.
 */
public final class StructureSources {
    private static final Map<String, Function<StructureSpec, StructureSource>> FACTORIES = new ConcurrentHashMap<>();
    private static final List<String> NAMES = new ArrayList<>();

    static {
        register(ClipboardStructure::parse, "Clipboard", "Cl", "Portapapeles");
    }

    private StructureSources() {
    }

    public static synchronized void register(Function<StructureSpec, StructureSource> factory, String... names) {
        if (names.length == 0) {
            throw new IllegalArgumentException("La estructura necesita un nombre");
        }
        NAMES.add(names[0]);
        for (String name : names) {
            FACTORIES.put(name.toLowerCase(java.util.Locale.ROOT), factory);
        }
    }

    public static StructureSource parse(String input) {
        StructureSpec spec = StructureSpec.parse(input);
        Function<StructureSpec, StructureSource> factory = FACTORIES.get(spec.name().toLowerCase(java.util.Locale.ROOT));
        if (factory == null) {
            throw new IllegalArgumentException("Estructura desconocida: " + spec.name() + " (" + String.join(", ",
                    names()) + ")");
        }
        return factory.apply(spec);
    }

    public static synchronized List<String> names() {
        return List.copyOf(NAMES);
    }
}
