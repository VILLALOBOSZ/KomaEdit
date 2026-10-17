package com.komainu.moxia.region;

/**
 * Una operación sobre la selección. Se ejecuta en un worker sobre una foto inmutable: no puede tocar el mundo,
 * solo leer {@link RegionSnapshot} y escribir en {@link RegionEdits}. Dentro de bucles largos conviene llamar a
 * {@link RegionEdits#checkCancelled()}.
 */
@FunctionalInterface
public interface RegionOperation {
    void apply(RegionSnapshot snapshot, RegionEdits edits);

    /** Si la operación necesita los niveles de luz en la foto; leerlos hace la captura algo más lenta. */
    default boolean needsLight() {
        return false;
    }
}
