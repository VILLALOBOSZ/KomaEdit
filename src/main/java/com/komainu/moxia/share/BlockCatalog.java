package com.komainu.moxia.share;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Los bloques que existen en este servidor, para comprobar lo que trae un paquete de otro (con otros mods o de otra
 * versión). En el juego sale del registro de bloques; en las pruebas, de una lista.
 */
public interface BlockCatalog {
    /** ¿Existe el bloque? El id lleva espacio de nombres ({@code minecraft:stone}). */
    boolean has(String id);

    /** Todos los ids, para buscar el más parecido a uno que falta. */
    Collection<String> ids();

    /** Las propiedades del bloque con sus valores posibles; vacío si no tiene o no existe. */
    Map<String, List<String>> properties(String id);

    /** ¿Existe el bioma? (los esquemas de los planos llevan los suyos) */
    default boolean hasBiome(String id) {
        return id.startsWith("minecraft:");
    }

    /** Un catálogo fijo: id → propiedades. */
    static BlockCatalog of(Map<String, Map<String, List<String>>> blocks) {
        Map<String, Map<String, List<String>>> copy = new LinkedHashMap<>(blocks);
        return new BlockCatalog() {
            @Override
            public boolean has(String id) {
                return copy.containsKey(id);
            }

            @Override
            public Collection<String> ids() {
                return copy.keySet();
            }

            @Override
            public Map<String, List<String>> properties(String id) {
                return copy.getOrDefault(id, Map.of());
            }
        };
    }
}
