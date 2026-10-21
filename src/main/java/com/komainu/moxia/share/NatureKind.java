package com.komainu.moxia.share;

import com.google.gson.JsonObject;
import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.nature.NaturePresets;
import com.komainu.moxia.nature.RockSettings;
import com.komainu.moxia.nature.TreeSettings;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.NamedDefinitions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Presets de árbol y de roca ({@code /komaedit tree save}, {@code rock save}). Datos: {@code {"t": "spruce 24 -g 1.5"}},
 * el mismo texto que se guarda. Al importarlo se cambian los bloques de los materiales que no existan aquí, la
 * máscara ({@code -m}) no puede llevar expresiones de WorldEdit y el texto se lee con el lector de la orden, sin
 * presets de nadie (un preset no puede apuntar a otro).
 */
public final class NatureKind extends NamedKind {
    private final String id;
    private final List<String> aliases;
    private final String singular;
    private final String plural;
    private final String permission;
    /** Banderas que llevan un bloque o un patrón. */
    private final Set<Character> materials;
    private final Consumer<String> parser;

    private NatureKind(Function<ShareTarget, NamedDefinitions> store, String id, List<String> aliases,
                       String singular, String plural, String permission, Set<Character> materials,
                       Consumer<String> parser) {
        super(store);
        this.id = id;
        this.aliases = aliases;
        this.singular = singular;
        this.plural = plural;
        this.permission = permission;
        this.materials = materials;
        this.parser = parser;
    }

    public static NatureKind tree(Function<ShareTarget, NamedDefinitions> store) {
        return new NatureKind(store, "tree", List.of("arbol", "árbol", "arboles", "trees"), "preset de árbol",
                "presets de árbol", KomaPermissions.BUILD + ".tree", Set.of('t', 'h', 'r'), TreeSettings::parse);
    }

    public static NatureKind rock(Function<ShareTarget, NamedDefinitions> store) {
        return new NatureKind(store, "rock", List.of("roca", "rocas", "rocks"), "preset de roca", "presets de roca",
                KomaPermissions.BUILD + ".rock", Set.of('b', 'u'), RockSettings::parse);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }

    @Override
    public String singular() {
        return singular;
    }

    @Override
    public String plural() {
        return plural;
    }

    @Override
    public String permission() {
        return permission;
    }

    @Override
    JsonObject data(String value) {
        JsonObject data = new JsonObject();
        data.addProperty("t", value);
        return data;
    }

    @Override
    String value(JsonObject data, ShareCheck check) {
        String text = ShareText.setting("El preset", string(data, "t"), 1024);
        List<String> tokens = tokens(text);
        if (!tokens.isEmpty() && tokens.getFirst().startsWith("#")) {
            throw new IllegalArgumentException("Apunta a otro preset (" + tokens.getFirst() + ")");
        }
        List<String> result = new ArrayList<>(tokens.size());
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            result.add(token);
            Character flag = token.length() == 2 && token.charAt(0) == '-' ? Character.toLowerCase(token.charAt(1))
                    : null;
            if (flag == null || index + 1 >= tokens.size() || !materials.contains(flag) && flag != 'm') {
                continue;
            }
            String value = tokens.get(++index);
            boolean quoted = value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"");
            String raw = quoted ? value.substring(1, value.length() - 1) : value;
            if (NaturePresets.disabled(raw) || BrushOptions.isNumber(raw)) {
                result.add(value);
                continue;
            }
            String checked = check.pattern(flag == 'm' ? "La máscara (-m)" : "El material (-" + flag + ")", raw);
            result.add(quoted ? "\"" + checked + "\"" : checked);
        }
        String rewritten = String.join(" ", result);
        parser.accept(rewritten);
        return rewritten;
    }

    @Override
    String text(JsonObject data) {
        return string(data, "t");
    }

    /** Palabras separadas por espacios, sin partir lo que va entre comillas, corchetes o paréntesis. */
    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"') {
                quoted = !quoted;
            } else if (!quoted && (character == '(' || character == '[')) {
                depth++;
            } else if (!quoted && (character == ')' || character == ']') && depth > 0) {
                depth--;
            } else if (!quoted && depth == 0 && Character.isWhitespace(character)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(character);
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
