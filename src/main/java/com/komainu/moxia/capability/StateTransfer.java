package com.komainu.moxia.capability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reemplazo conservando propiedades. Cada propiedad que comparten el bloque viejo y el nuevo (y cuyo valor admite
 * el nuevo) pasa tal cual: escalera por escalera conserva {@code facing}, {@code half} y {@code shape}; losa por losa,
 * {@code type}; tronco por tronco, {@code axis}; y cualquier bloque anegable conserva {@code waterlogged}.
 * Lo que el nuevo no comparte sale de su estado de colocación normal.
 */
public final class StateTransfer {
    private StateTransfer() {
    }

    /**
     * Propiedades del estado nuevo.
     *
     * @param old      propiedades del bloque reemplazado
     * @param allowed  propiedades del bloque nuevo con sus valores posibles
     * @param placed   estado de colocación del bloque nuevo (de partida)
     */
    public static Map<String, String> transfer(
            Map<String, String> old,
            Map<String, List<String>> allowed,
            Map<String, String> placed
    ) {
        Map<String, String> result = new LinkedHashMap<>(placed);
        for (Map.Entry<String, String> entry : old.entrySet()) {
            List<String> values = allowed.get(entry.getKey());
            if (values != null && values.contains(entry.getValue())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /** ¿Tienen los dos bloques las mismas propiedades (la «misma forma»)? */
    public static boolean sameShape(Map<String, String> old, Map<String, List<String>> allowed) {
        return old.keySet().equals(allowed.keySet());
    }

    /**
     * ¿Es un bloque de dos partes? Reemplazar una sola mitad de una puerta, una planta alta o una cama deja la otra
     * huérfana, así que no se reemplazan.
     */
    public static boolean multiBlock(Map<String, List<String>> allowed) {
        List<String> half = allowed.get("half");
        if (half != null && half.contains("upper") && half.contains("lower")) {
            return true;
        }
        List<String> part = allowed.get("part");
        return part != null && part.contains("head") && part.contains("foot");
    }
}
