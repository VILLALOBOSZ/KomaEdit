package com.komainu.moxia.blueprint;

import com.komainu.moxia.placement.ParamText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Una lista de planos para estampar o dispersar: nombres ({@code roca_1}, {@code servidor/ruina}) y etiquetas
 * ({@code #arbustos}), separados por comas o {@code |}, cada uno con un peso opcional ({@code roca_1:3}). Una
 * etiqueta reparte su peso entre todos sus planos.
 */
public record BlueprintSelection(List<Pick> picks) {
    public static final int MAX_PICKS = 32;

    /** @param tag si {@code value} es una etiqueta en vez de un plano */
    public record Pick(String value, boolean tag, boolean sharedOnly, double weight) {
    }

    /** Un plano elegido y su peso final. */
    public record Weighted(BlueprintLibrary.Entry entry, double weight) {
    }

    public static BlueprintSelection parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Falta la lista de planos (nombres o #etiqueta)");
        }
        List<Pick> picks = new ArrayList<>();
        for (String raw : ParamText.unquote(text).split("[,|]")) {
            String part = raw.trim();
            if (part.isEmpty()) {
                continue;
            }
            double weight = 1.0;
            int colon = part.lastIndexOf(':');
            if (colon > 0 && colon < part.length() - 1 && isNumber(part.substring(colon + 1))) {
                weight = ParamText.number(part.substring(colon + 1), 0.001, 1_000, "El peso de " + part);
                part = part.substring(0, colon).trim();
            }
            if (part.startsWith("#")) {
                picks.add(new Pick(BlueprintTags.normalize(part), true, false, weight));
            } else {
                BlueprintRef ref = BlueprintRef.parse(part);
                picks.add(new Pick(ref.name(), false, ref.sharedOnly(), weight));
            }
        }
        if (picks.isEmpty()) {
            throw new IllegalArgumentException("Falta la lista de planos (nombres o #etiqueta)");
        }
        if (picks.size() > MAX_PICKS) {
            throw new IllegalArgumentException("Como mucho " + MAX_PICKS + " planos o etiquetas en la lista");
        }
        return new BlueprintSelection(List.copyOf(picks));
    }

    /**
     * Los planos de la lista con sus pesos. Un plano que sale por nombre y por etiqueta suma los dos pesos.
     *
     * @param limit planos distintos como mucho
     */
    public List<Weighted> resolve(BlueprintLibrary library, UUID owner, int limit) {
        Map<String, Weighted> chosen = new LinkedHashMap<>();
        for (Pick pick : picks) {
            List<BlueprintLibrary.Entry> entries;
            if (pick.tag()) {
                entries = library.tagged(owner, pick.value());
                if (entries.isEmpty()) {
                    throw new IllegalArgumentException("Ningún plano tiene la etiqueta #" + pick.value());
                }
            } else {
                entries = List.of(library.require(owner, new BlueprintRef(pick.value(), pick.sharedOnly())));
            }
            double each = pick.weight() / entries.size();
            for (BlueprintLibrary.Entry entry : entries) {
                chosen.merge(entry.reference(), new Weighted(entry, each),
                        (old, added) -> new Weighted(old.entry(), old.weight() + added.weight()));
            }
        }
        if (chosen.size() > limit) {
            throw new IllegalArgumentException("La lista abarca " + chosen.size() + " planos; el máximo es " + limit);
        }
        return List.copyOf(chosen.values());
    }

    private static boolean isNumber(String value) {
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
