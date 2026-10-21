package com.komainu.moxia.studio.live;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Lo que el servidor admite por la red de la vista previa en vivo: solo órdenes de KomaEdit que piden su vista previa
 * ({@code -v} o {@code --preview}) y figuras. Cualquier otra cosa se escribe en el chat, con su antispam de siempre.
 * Lógica pura sobre la petición.
 */
public final class LiveRequests {
    private static final Set<String> ROOTS = Set.of("komaedit", "ke", "moxia", "mx");
    private static final Set<String> PREVIEW_FLAGS = Set.of("-v", "--preview");

    private LiveRequests() {
    }

    /** Por qué no se admite la petición, o vacío si se puede ejecutar. */
    public static Optional<String> problem(LivePreviewRequestPayload request) {
        if (request.figure()) {
            return Optional.empty();
        }
        List<String> words = List.of(request.command().toLowerCase(Locale.ROOT).split("\\s+"));
        if (words.isEmpty() || !ROOTS.contains(words.getFirst())) {
            return Optional.of("la vista previa en vivo solo rehace órdenes de KomaEdit");
        }
        if (words.stream().noneMatch(PREVIEW_FLAGS::contains)) {
            return Optional.of("la vista previa en vivo solo rehace órdenes con vista previa (-v)");
        }
        return Optional.empty();
    }
}
