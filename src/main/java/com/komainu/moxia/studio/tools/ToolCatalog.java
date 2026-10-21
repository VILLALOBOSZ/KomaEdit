package com.komainu.moxia.studio.tools;

import com.komainu.moxia.studio.ShapeSearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Las tarjetas de la etapa Herramientas, en el orden en que salen, y su buscador (el mismo criterio que el de
 * figuras: sin mayúsculas ni tildes, cada palabra tiene que aparecer y el nombre pesa más que la descripción).
 */
public final class ToolCatalog {
    public static final List<ToolCard> ALL = List.of(
            new TreeCard(),
            new RockCard(),
            new RoofCard(),
            new LoftCard(),
            new TerrainCards.Stamp(),
            new PaintBrushCard(),
            new TerrainCards.Ramp(),
            new TerrainCards.River(),
            new TerrainCards.Erosion(),
            new TerrainCards.Paint(),
            new UtilityCards.Text(),
            new UtilityCards.Analyze(),
            new UtilityCards.Gravity(),
            new UtilityCards.Snow(),
            new UtilityCards.Drain(),
            new UtilityCards.Waterlog(),
            new UtilityCards.Biome(),
            new ReferenceCard(),
            new SymmetryCard(),
            new CapabilityCard()
    );

    private ToolCatalog() {
    }

    /** La tarjeta con ese id, o {@code null}. */
    public static ToolCard byId(String id) {
        for (ToolCard card : ALL) {
            if (card.id().equals(id)) {
                return card;
            }
        }
        return null;
    }

    public static List<ToolCard> family(ToolFamily family) {
        return ALL.stream().filter(card -> card.family() == family).toList();
    }

    /** Todos los nodos que pueden pedir las tarjetas: lo que el servidor contesta al abrir el Studio. */
    public static Set<String> permissionNodes() {
        Set<String> nodes = new LinkedHashSet<>();
        for (ToolCard card : ALL) {
            nodes.addAll(card.allPermissions());
        }
        return nodes;
    }

    /** Puntuación de una tarjeta: 0 si alguna palabra no aparece; una búsqueda vacía puntúa 1. */
    public static int score(ToolCard card, String query, String name, String description, String family) {
        String normalized = ShapeSearch.normalize(query);
        if (normalized.isEmpty()) {
            return 1;
        }
        String localized = ShapeSearch.normalize(name);
        String id = ShapeSearch.normalize(card.id());
        List<String> keywords = card.keywords().stream().map(ShapeSearch::normalize).toList();
        String familyName = ShapeSearch.normalize(family);
        String text = ShapeSearch.normalize(description);
        int total = 0;
        for (String token : normalized.split(" ")) {
            int best = Math.max(field(localized, token, 100), field(id, token, 90));
            for (String keyword : keywords) {
                best = Math.max(best, field(keyword, token, 70));
            }
            best = Math.max(best, field(familyName, token, 40));
            best = Math.max(best, field(text, token, 20));
            if (best == 0) {
                return 0;
            }
            total += best;
        }
        return total;
    }

    /** Las tarjetas que encajan, de mejor a peor; a igual puntuación, en el orden del catálogo. */
    public static List<ToolCard> search(List<ToolCard> cards, String query, Function<ToolCard, String> name,
                                        Function<ToolCard, String> description,
                                        Function<ToolFamily, String> family) {
        List<Scored> scored = new ArrayList<>();
        for (int index = 0; index < cards.size(); index++) {
            ToolCard card = cards.get(index);
            int value = score(card, query, name.apply(card), description.apply(card), family.apply(card.family()));
            if (value > 0) {
                scored.add(new Scored(card, value, index));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparingInt(Scored::order));
        return scored.stream().map(Scored::card).toList();
    }

    private static int field(String field, String token, int weight) {
        if (field.isEmpty()) {
            return 0;
        }
        if (field.equals(token)) {
            return weight + 30;
        }
        if (field.startsWith(token)) {
            return weight + 20;
        }
        if (field.contains(" " + token)) {
            return weight + 10;
        }
        return field.contains(token) ? weight : 0;
    }

    private record Scored(ToolCard card, int score, int order) {
    }
}
