package com.komainu.moxia.gradient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reglas de {@code /komaedit palette swap}: el bloque n de la paleta de origen pasa a ser el bloque n de la de destino.
 *
 * @param fuzzy      el origen encaja por tipo de bloque, sin mirar las propiedades ({@code -f})
 * @param copyStates copia las propiedades del bloque original sobre el de destino cuando las admite ({@code -b})
 */
public record PaletteSwap(List<Rule> rules, boolean fuzzy, boolean copyStates) {
    public PaletteSwap {
        rules = List.copyOf(rules);
    }

    /**
     * @param ignoreAir no cambia el aire aunque esté en la paleta de origen ({@code -a})
     * @param stretch   estira el destino para que cubra todo el origen ({@code -s}); sin él, lo que sobra del origen
     *                  se queda como está
     */
    public static PaletteSwap of(
            List<String> source,
            List<String> target,
            boolean ignoreAir,
            boolean stretch,
            boolean fuzzy,
            boolean copyStates
    ) {
        if (source.isEmpty() || target.isEmpty()) {
            throw new IllegalArgumentException("Las dos paletas necesitan al menos un bloque");
        }
        List<BlockSpec> from = source.stream().map(BlockSpec::parse).toList();
        List<BlockSpec> to = target.stream().map(BlockSpec::parse).toList();
        List<Rule> rules = new ArrayList<>();
        for (int index = 0; index < from.size(); index++) {
            BlockSpec block = from.get(index);
            if (ignoreAir && block.isAir()) {
                continue;
            }
            int targetIndex = stretch ? (int) ((long) index * to.size() / from.size()) : index;
            if (targetIndex >= to.size()) {
                continue;
            }
            rules.add(new Rule(block, to.get(targetIndex)));
        }
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("Ningún bloque de la paleta de origen tiene pareja en la de destino");
        }
        return new PaletteSwap(rules, fuzzy, copyStates);
    }

    /** La regla que se aplica a un estado, o {@code null} si no cambia. Gana la primera que encaje. */
    public Rule ruleFor(String stateId, Map<String, String> stateProperties) {
        for (Rule rule : rules) {
            if (rule.source().matches(stateId, stateProperties, !fuzzy)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Propiedades que se piden para el bloque nuevo: las escritas en el destino y, con {@code -b}, las del bloque
     * original que el destino no fija. Quien construye el estado descarta las que el bloque nuevo no tenga.
     */
    public Map<String, String> targetProperties(Rule rule, Map<String, String> stateProperties) {
        Map<String, String> result = new LinkedHashMap<>();
        if (copyStates) {
            result.putAll(stateProperties);
        }
        result.putAll(rule.target().properties());
        return result;
    }

    public record Rule(BlockSpec source, BlockSpec target) {
    }
}
