package com.komainu.moxia.clipboard;

import com.komainu.moxia.symmetry.StateTransform;
import com.komainu.moxia.symmetry.SymmetryTransform;

import java.util.Collection;
import java.util.Map;

/**
 * Gira los estados de bloque con la estructura: escaleras, troncos, vallas, raíles, carteles… Usa la misma lógica
 * pura que la simetría ({@link StateTransform}); las direcciones se redondean al cuarto de vuelta más cercano y la
 * {@code rotation} de 16 pasos de carteles, estandartes y cabezas sigue el ángulo exacto.
 */
public final class ClipboardStates {
    /** Décimas de grado: el giro se guarda como fracción de vuelta exacta. */
    private static final int STEPS = 3600;

    private ClipboardStates() {
    }

    /** El giro de {@code degrees} grados (positivo: de este a sur) como transformación de la simetría. */
    public static SymmetryTransform rotation(double degrees) {
        double normalized = ((degrees % 360.0) + 360.0) % 360.0;
        int turn = Math.floorMod((int) Math.round(normalized * STEPS / 360.0), STEPS);
        return new SymmetryTransform(false, false, false, turn, STEPS);
    }

    /**
     * @param state   valor de cada propiedad
     * @param allowed valores que admite cada propiedad del bloque
     */
    public static Map<String, String> rotate(Map<String, String> state,
                                             Map<String, ? extends Collection<String>> allowed, double degrees) {
        return StateTransform.apply(state, allowed, rotation(degrees));
    }
}
