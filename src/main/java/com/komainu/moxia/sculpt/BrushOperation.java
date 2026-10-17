package com.komainu.moxia.sculpt;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.sk89q.worldedit.math.BlockVector3;

/**
 * Una pasada de pincel ya configurada (posición, clic, paleta…). {@link SculptManager#run} captura la zona que pide
 * en el hilo del servidor, llama a {@link #plan} en un worker y deja el resultado en vista previa o lo aplica con
 * {@code BatchEditService}, que lo registra como una sola operación de //undo.
 *
 * <p>Las implementaciones son inmutables: todo lo que dependa del mundo o de WorldEdit (paletas, portapapeles)
 * se resuelve antes, en el hilo del servidor.</p>
 */
public interface BrushOperation {
    /** Nombre para mensajes, vista previa e historial ({@code Degradado}, {@code Pétalos}…). */
    String displayName();

    /** Color RGB del holograma de la vista previa. */
    default int previewColor() {
        return 0xE7E7E7;
    }

    /** Zona a capturar antes de calcular, o {@code null} si el pincel no necesita leer el mundo. */
    Capture capture();

    /** Se ejecuta en un worker; la foto es {@code null} si {@link #capture()} lo era. */
    PackedBlockPlan plan(WorldSnapshot snapshot);

    record Capture(BlockVector3 center, int horizontalRadius, int verticalRadius) {
    }
}
