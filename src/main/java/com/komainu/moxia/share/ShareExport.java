package com.komainu.moxia.share;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Lo que pide {@code /komaedit share export}: las cosas ({@code paleta:rocas plano:torre}, {@code ruido:*} para todos
 * los ruidos), el nombre del paquete ({@code -n}) y si va a un archivo ({@code -f}). Monta el paquete con lo que
 * necesita cada cosa ({@link ShareKind#requires}) y sin repetir nada.
 */
public record ShareExport(List<String> refs, String name, boolean toFile) {
    public ShareExport {
        refs = List.copyOf(refs);
        name = name == null ? "" : name;
    }

    /** Lee las palabras de la orden. */
    public static ShareExport parse(String text) {
        List<String> refs = new ArrayList<>();
        String name = "";
        boolean file = false;
        List<String> tokens = tokens(text == null ? "" : text);
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            switch (token.toLowerCase(Locale.ROOT)) {
                case "-f", "--file", "--archivo" -> file = true;
                case "-n", "--name", "--nombre" -> {
                    if (index + 1 >= tokens.size()) {
                        throw new IllegalArgumentException("Falta el nombre del paquete después de -n");
                    }
                    name = ShareText.display(tokens.get(++index), ShareCodec.MAX_NAME);
                }
                default -> {
                    if (token.startsWith("-")) {
                        throw new IllegalArgumentException("Bandera desconocida " + token + ": usa -f (archivo) o -n"
                                + " (nombre del paquete)");
                    }
                    refs.add(token);
                }
            }
        }
        if (refs.isEmpty()) {
            throw new IllegalArgumentException("Di qué compartir: tipo:nombre, por ejemplo paleta:rocas plano:torre");
        }
        return new ShareExport(refs, name, file);
    }

    /**
     * Monta el paquete.
     *
     * @param modVersion versión de KomaEdit, para enseñarla al importar
     */
    public SharePack build(ShareTarget target, Function<String, ShareKind> kinds, String modVersion, long now,
                           ShareLimits limits) {
        Map<String, SharePack.Item> items = new LinkedHashMap<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String text : refs) {
            ShareRef ref = parseRef(text, kinds);
            List<String> names = ref.all() ? ref.kind().names(target) : List.of(ref.name());
            if (ref.all() && names.isEmpty()) {
                throw new IllegalArgumentException("No tienes " + ref.kind().plural() + " que compartir");
            }
            for (String name : names) {
                add(ref.kind(), name, target, kinds, items, visited, limits, 0);
            }
        }
        String packName = !name.isEmpty() ? name : items.size() == 1 ? items.values().iterator().next().name()
                : "Paquete de " + target.name();
        return SharePack.of(ShareText.display(packName, ShareCodec.MAX_NAME),
                ShareText.display(target.name(), ShareCodec.MAX_AUTHOR), modVersion, now,
                new ArrayList<>(items.values()));
    }

    private static ShareRef parseRef(String text, Function<String, ShareKind> kinds) {
        int colon = text.indexOf(':');
        if (colon <= 0 || colon == text.length() - 1) {
            throw new IllegalArgumentException("Escribe tipo:nombre («" + text + "»), por ejemplo paleta:rocas");
        }
        ShareKind kind = kinds.apply(text.substring(0, colon).toLowerCase(Locale.ROOT));
        if (kind == null) {
            throw new IllegalArgumentException("No se puede compartir «" + text.substring(0, colon) + "»; tipos: "
                    + String.join(", ", ShareKinds.names()));
        }
        return new ShareRef(kind, text.substring(colon + 1));
    }

    private static void add(ShareKind kind, String name, ShareTarget target, Function<String, ShareKind> kinds,
                            Map<String, SharePack.Item> items, Set<String> visited, ShareLimits limits, int depth) {
        String key = kind.id() + ":" + name.toLowerCase(Locale.ROOT);
        if (!visited.add(key) || depth > 4) {
            return;
        }
        SharePack.Item item = kind.export(target, name);
        String itemKey = kind.id() + ":" + item.name().toLowerCase(Locale.ROOT);
        if (items.containsKey(itemKey)) {
            return;
        }
        if (items.size() >= limits.maxItems()) {
            throw new IllegalArgumentException("Un paquete lleva como mucho " + limits.maxItems() + " cosas");
        }
        items.put(itemKey, item);
        for (ShareRef required : kind.requires(target, name)) {
            add(required.kind(), required.name(), target, kinds, items, visited, limits, depth + 1);
        }
    }

    /** «2 paletas y 1 plano». */
    public static String summary(List<SharePack.Item> items, Function<String, ShareKind> kinds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SharePack.Item item : items) {
            counts.merge(item.kind(), 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        counts.forEach((id, count) -> {
            ShareKind kind = kinds.apply(id);
            String noun = kind == null ? id : count == 1 ? kind.singular() : kind.plural();
            parts.add(count + " " + noun);
        });
        if (parts.size() <= 1) {
            return parts.isEmpty() ? "nada" : parts.getFirst();
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " y " + parts.getLast();
    }

    /** Palabras separadas por espacios; lo que va entre comillas es una sola. */
    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"') {
                quoted = !quoted;
            } else if (!quoted && Character.isWhitespace(character)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Faltan las comillas de cierre");
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
