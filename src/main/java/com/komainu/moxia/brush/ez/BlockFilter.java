package com.komainu.moxia.brush.ez;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Máscara sencilla de los pinceles sobre la foto capturada: lista separada por comas de ids de bloque y de las
 * clases {@code #fullblock} (cubo entero), {@code #solid} (cubo, losa o escalera) y {@code #existing} (cualquier
 * cosa que no sea aire ni líquido). Un {@code !} delante excluye. Si solo hay exclusiones, se parte de
 * {@code #existing}.
 *
 * <p>Ejemplos: {@code #fullblock}, {@code grass_block,moss_block}, {@code #fullblock,!sand,!gravel}. Lo que no sea
 * una lista así lo lee WorldEdit ({@link BrushMasks}).</p>
 */
public final class BlockFilter implements BrushMask {
    public static final BlockFilter FULL_BLOCK = parse("#fullblock");

    private final String source;
    private final List<Term> include;
    private final List<Term> exclude;
    /** Los ids de cada lado, para no recorrerlos uno a uno en cada bloque (una paleta larga como máscara). */
    private final Set<String> includeIds;
    private final Set<String> excludeIds;

    private BlockFilter(String source, List<Term> include, List<Term> exclude) {
        this.source = source;
        this.include = include;
        this.exclude = exclude;
        this.includeIds = ids(include);
        this.excludeIds = ids(exclude);
    }

    private static Set<String> ids(List<Term> terms) {
        Set<String> ids = new HashSet<>();
        for (Term term : terms) {
            if (term.kind == Kind.ID) {
                ids.add(term.id);
            }
        }
        return Set.copyOf(ids);
    }

    public static BlockFilter parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("La máscara está vacía");
        }
        List<Term> include = new ArrayList<>();
        List<Term> exclude = new ArrayList<>();
        for (String raw : input.split(",")) {
            String text = raw.trim().toLowerCase(Locale.ROOT);
            if (text.isEmpty()) {
                continue;
            }
            boolean negated = text.startsWith("!");
            Term term = term(negated ? text.substring(1).trim() : text);
            (negated ? exclude : include).add(term);
        }
        if (include.isEmpty() && exclude.isEmpty()) {
            throw new IllegalArgumentException("La máscara está vacía");
        }
        if (include.isEmpty()) {
            include.add(new Term(Kind.EXISTING, null));
        }
        return new BlockFilter(input.trim(), List.copyOf(include), List.copyOf(exclude));
    }

    private static Term term(String text) {
        return switch (text) {
            case "#fullblock", "#full", "#entero" -> new Term(Kind.FULL, null);
            case "#solid", "#solido" -> new Term(Kind.SOLID, null);
            case "#existing", "#existente", "#*" -> new Term(Kind.EXISTING, null);
            default -> {
                if (text.startsWith("#") || text.isEmpty()) {
                    throw new IllegalArgumentException("Máscara desconocida: " + text
                            + " (usa #fullblock, #solid, #existing o ids de bloque)");
                }
                yield new Term(Kind.ID, PaletteShift.normalize(text));
            }
        };
    }

    @Override
    public boolean matches(BrushWorld world, int x, int y, int z) {
        return matches(world.blockId(x, y, z), world.cell(x, y, z));
    }

    public boolean matches(String blockId, BrushWorld.Cell cell) {
        String id = includeIds.isEmpty() && excludeIds.isEmpty() ? null : PaletteShift.normalize(blockId);
        return any(include, includeIds, id, cell) && !any(exclude, excludeIds, id, cell);
    }

    private static boolean any(List<Term> terms, Set<String> ids, String id, BrushWorld.Cell cell) {
        if (id != null && ids.contains(id)) {
            return true;
        }
        for (Term term : terms) {
            boolean match = switch (term.kind) {
                case FULL -> cell == BrushWorld.Cell.FULL;
                case SOLID -> cell == BrushWorld.Cell.FULL || cell == BrushWorld.Cell.SHAPED;
                case EXISTING -> cell != BrushWorld.Cell.AIR && cell != BrushWorld.Cell.LIQUID;
                case ID -> false;
            };
            if (match) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return source;
    }

    private enum Kind {
        FULL, SOLID, EXISTING, ID
    }

    private record Term(Kind kind, String id) {
    }
}
