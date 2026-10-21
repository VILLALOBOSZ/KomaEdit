package com.komainu.moxia.studio.tools;

import com.komainu.moxia.studio.ShapeSearch;
import com.komainu.moxia.studio.ui.StudioIcons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Las tarjetas de la etapa Herramientas y su buscador (el mismo criterio que el de figuras: sin mayúsculas ni tildes,
 * cada palabra tiene que aparecer y el nombre pesa más que la descripción).
 *
 * <p><b>Añadir una tarjeta</b> es una línea en {@link #ENTRIES}: su categoría (el grupo de la cuadrícula), la
 * tarjeta y su icono, que no puede repetirse. Salen agrupadas por categoría, en el orden de {@link ToolCategory}, y
 * dentro de cada una en el orden en que se registran. Los textos van en los idiomas (ver {@link ToolCard}); las
 * pruebas sacan la lista de aquí, así que la tarjeta nueva se comprueba con las demás sin tocarlas.</p>
 */
public final class ToolCatalog {
    /** Una tarjeta registrada: en qué grupo sale y con qué icono. */
    public record Entry(ToolCategory category, ToolCard card, StudioIcons.Icon icon) {
    }

    public static final List<Entry> ENTRIES = List.of(
            entry(ToolCategory.NATURE, new TreeCard(), StudioIcons.Icon.TREE),
            entry(ToolCategory.NATURE, new RockCard(), StudioIcons.Icon.ROCK),
            entry(ToolCategory.BUILDINGS, new RoofCard(), StudioIcons.Icon.ROOF),
            entry(ToolCategory.BUILDINGS, new LoftCard(), StudioIcons.Icon.LOFT),
            entry(ToolCategory.BUILDINGS, new UtilityCards.Text(), StudioIcons.Icon.TEXT),
            entry(ToolCategory.TERRAIN, new TerrainCards.Stamp(), StudioIcons.Icon.STAMP),
            entry(ToolCategory.TERRAIN, new PaintCard(), StudioIcons.Icon.PAINT_BRUSH),
            entry(ToolCategory.TERRAIN, new TerrainCards.Ramp(), StudioIcons.Icon.RAMP),
            entry(ToolCategory.TERRAIN, new TerrainCards.River(), StudioIcons.Icon.RIVER),
            entry(ToolCategory.TERRAIN, new TerrainCards.Erosion(), StudioIcons.Icon.EROSION),
            entry(ToolCategory.UTILITIES, new TouchUpCard(), StudioIcons.Icon.TOUCH_UP),
            entry(ToolCategory.UTILITIES, new WaterCard(), StudioIcons.Icon.WATER),
            entry(ToolCategory.UTILITIES, new UtilityCards.Analyze(), StudioIcons.Icon.ANALYZE),
            entry(ToolCategory.MODES, new SymmetryCard(), StudioIcons.Icon.SYMMETRY),
            entry(ToolCategory.MODES, new CapabilityCard(), StudioIcons.Icon.CAPABILITIES)
    );

    /** Todas las tarjetas, por categoría y en el orden en que se registran. */
    public static final List<ToolCard> ALL = ENTRIES.stream()
            .sorted(Comparator.comparingInt(entry -> entry.category().ordinal()))
            .map(Entry::card)
            .toList();

    private ToolCatalog() {
    }

    private static Entry entry(ToolCategory category, ToolCard card, StudioIcons.Icon icon) {
        return new Entry(category, card, icon);
    }

    /**
     * La tarjeta con ese id, o {@code null}. Los ids de antes de unir herramientas ({@code paintbrush},
     * {@code snow}…) llevan a la tarjeta que las reúne ({@link ToolMigration}).
     */
    public static ToolCard byId(String id) {
        String current = ToolMigration.id(id);
        for (ToolCard card : ALL) {
            if (card.id().equals(current)) {
                return card;
            }
        }
        return null;
    }

    private static Entry entryOf(ToolCard card) {
        for (Entry entry : ENTRIES) {
            if (entry.card() == card) {
                return entry;
            }
        }
        throw new IllegalArgumentException("tarjeta sin registrar: " + card.id());
    }

    /** El grupo de la cuadrícula en el que sale la tarjeta. */
    public static ToolCategory categoryOf(ToolCard card) {
        return entryOf(card).category();
    }

    public static StudioIcons.Icon icon(ToolCard card) {
        return entryOf(card).icon();
    }

    /** Las tarjetas de una categoría, en el orden en que se registran. */
    public static List<ToolCard> category(ToolCategory category) {
        return ALL.stream().filter(card -> categoryOf(card) == category).toList();
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
    public static int score(ToolCard card, String query, String name, String description, String category) {
        String normalized = ShapeSearch.normalize(query);
        if (normalized.isEmpty()) {
            return 1;
        }
        String localized = ShapeSearch.normalize(name);
        String id = ShapeSearch.normalize(card.id());
        List<String> keywords = card.keywords().stream().map(ShapeSearch::normalize).toList();
        String categoryName = ShapeSearch.normalize(category);
        String text = ShapeSearch.normalize(description);
        int total = 0;
        for (String token : normalized.split(" ")) {
            int best = Math.max(field(localized, token, 100), field(id, token, 90));
            for (String keyword : keywords) {
                best = Math.max(best, field(keyword, token, 70));
            }
            best = Math.max(best, field(categoryName, token, 40));
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
                                        Function<ToolCategory, String> category) {
        List<Scored> scored = new ArrayList<>();
        for (int index = 0; index < cards.size(); index++) {
            ToolCard card = cards.get(index);
            int value = score(card, query, name.apply(card), description.apply(card),
                    category.apply(categoryOf(card)));
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
