package com.komainu.moxia.edit;

import java.util.Objects;

/**
 * Lo que guarda un bloque (lo de un cofre, un horno o una tolva, el libro de un atril, el disco de un tocadiscos)
 * cuando se escribe encima. WorldEdit vacía siempre el contenedor antes de escribir ({@code FabricWorld.setBlock}),
 * así que un estado sin datos lo deja vacío. Si lo que se escribe es el mismo bloque, calculado sobre lo que hay (un
 * cambio de estado: anegarlo, girarlo, retocarlo) y no trae datos propios, se le ponen los del bloque. Java puro.
 */
public final class KeptData {
    private KeptData() {
    }

    /**
     * @param plannedHasData  lo que se escribe trae sus propios datos (pegar, el gizmo, la reconstrucción)
     * @param fromWhatIsThere se calculó sobre lo que hay ahí (el plan esperaba ese estado, o una edición a mano)
     * @param currentHasData  el bloque de ahí tiene entidad de bloque
     */
    public static <B> boolean keeps(boolean plannedHasData, boolean fromWhatIsThere, boolean currentHasData,
                                    B plannedBlock, B currentBlock) {
        return !plannedHasData && fromWhatIsThere && currentHasData && Objects.equals(plannedBlock, currentBlock);
    }

    /**
     * Una copia de la simetría que cae sobre el mismo contenedor conserva lo suyo, traiga datos o no: llevarle los del
     * original duplicaría objetos (retocar un cofre con la simetría de WorldEdit puesta).
     *
     * @param currentHasData el contenedor donde cae la copia tiene datos
     */
    public static <B> boolean copyKeeps(boolean currentHasData, B copiedBlock, B currentBlock) {
        return currentHasData && Objects.equals(copiedBlock, currentBlock);
    }
}
