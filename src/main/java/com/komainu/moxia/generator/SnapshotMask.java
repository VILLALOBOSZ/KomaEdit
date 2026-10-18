package com.komainu.moxia.generator;

import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.RegionSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Lista de bloques de la máscara de los generadores ({@code -m}), la de antes de que {@code -m} admitiera cualquier
 * máscara de WorldEdit. Se evalúa sobre la foto de la región, sin WorldEdit. Admite:
 *
 * <ul>
 *     <li>bloques con o sin espacio de nombres ({@code grass_block}, {@code minecraft:stone}) y, si se quiere, con
 *     algunas propiedades ({@code oak_log[axis=y]});</li>
 *     <li>{@code #solid}, {@code #existing}, {@code #air} y {@code #liquid};</li>
 *     <li>{@code !} delante niega una entrada; las entradas se separan con comas.</li>
 * </ul>
 *
 * <p>Cumple la máscara un bloque que encaja con alguna entrada positiva (si las hay) y con ninguna negada. Todo lo
 * que no sea una lista así lo lee WorldEdit ({@link #LISTS} devuelve {@code null}).</p>
 */
public final class SnapshotMask {
    private static final Set<String> CLASSES = Set.of("#solid", "#existing", "#air", "#liquid");

    /** La lista para {@link RegionMasks#parse}: {@code null} si el texto no es una lista de esta sintaxis. */
    public static final RegionMasks.ListSyntax LISTS = text -> {
        if (!RegionMasks.plainList(text, CLASSES)) {
            return null;
        }
        SnapshotMask mask = parse(text);
        return (Predicate<RegionSnapshot.StateInfo>) mask::test;
    };

    private final List<Entry> positive;
    private final List<Entry> negative;

    private SnapshotMask(List<Entry> positive, List<Entry> negative) {
        this.positive = positive;
        this.negative = negative;
    }

    public static SnapshotMask parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("La máscara está vacía");
        }
        List<Entry> positive = new ArrayList<>();
        List<Entry> negative = new ArrayList<>();
        for (String raw : split(text.trim())) {
            String token = raw.trim();
            boolean negated = token.startsWith("!");
            if (negated) {
                token = token.substring(1).trim();
            }
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Hay una entrada vacía en la máscara");
            }
            (negated ? negative : positive).add(entry(token));
        }
        return new SnapshotMask(List.copyOf(positive), List.copyOf(negative));
    }

    private static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '[') {
                depth++;
            } else if (character == ']') {
                depth--;
            } else if ((character == ',' || character == ';') && depth == 0) {
                parts.add(text.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    private static Entry entry(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "#solid":
                return new Entry(Kind.SOLID, null, Set.of());
            case "#existing":
                return new Entry(Kind.EXISTING, null, Set.of());
            case "#air":
                return new Entry(Kind.AIR, null, Set.of());
            case "#liquid":
                return new Entry(Kind.LIQUID, null, Set.of());
            default:
                break;
        }
        if (lower.startsWith("#") || lower.startsWith("%") || lower.startsWith("$") || lower.startsWith("=")
                || lower.startsWith(">") || lower.startsWith("<") || lower.startsWith("^")) {
            throw new IllegalArgumentException("La máscara «" + token + "» no se puede usar aquí: los generadores"
                    + " admiten bloques, #solid, #existing, #air, #liquid y ! para negar");
        }
        String id = lower;
        Set<String> properties = Set.of();
        int bracket = lower.indexOf('[');
        if (bracket >= 0) {
            if (!lower.endsWith("]")) {
                throw new IllegalArgumentException("Falta el ] de cierre en la máscara «" + token + "»");
            }
            id = lower.substring(0, bracket);
            properties = new HashSet<>(Arrays.asList(lower.substring(bracket + 1, lower.length() - 1).split(",")));
            properties.removeIf(String::isBlank);
        }
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        return new Entry(Kind.BLOCK, id, Set.copyOf(properties));
    }

    public boolean test(RegionSnapshot.StateInfo info) {
        for (Entry entry : negative) {
            if (entry.matches(info)) {
                return false;
            }
        }
        if (positive.isEmpty()) {
            return true;
        }
        for (Entry entry : positive) {
            if (entry.matches(info)) {
                return true;
            }
        }
        return false;
    }

    private enum Kind {
        BLOCK, SOLID, EXISTING, AIR, LIQUID
    }

    private record Entry(Kind kind, String blockId, Set<String> properties) {
        boolean matches(RegionSnapshot.StateInfo info) {
            return switch (kind) {
                case SOLID -> info.solid();
                case EXISTING -> !info.air();
                case AIR -> info.air();
                case LIQUID -> info.liquid();
                case BLOCK -> blockId.equals(info.blockId()) && hasProperties(info.properties());
            };
        }

        private boolean hasProperties(String text) {
            if (properties.isEmpty()) {
                return true;
            }
            Set<String> present = new HashSet<>(Arrays.asList(text.toLowerCase(Locale.ROOT).split(",")));
            return present.containsAll(properties);
        }
    }
}
