package com.komainu.moxia.ivy;

import java.util.List;

/**
 * Hiedra calculada.
 *
 * @param blocks       bloques ordenados de arriba abajo, el orden en que conviene colocarlos (lo que cuelga se
 *                     apoya en lo de encima)
 * @param tips         puntas que llegaron a crecer, ramas incluidas
 * @param seedsUsed    semillas que encontraron una superficie donde agarrarse
 * @param seedsSkipped semillas sin ninguna superficie cerca
 * @param truncated    se paró al llegar al límite de bloques
 */
public record IvyResult(List<IvyBlock> blocks, int tips, int seedsUsed, int seedsSkipped, boolean truncated) {
    public IvyResult {
        blocks = List.copyOf(blocks);
    }

    public long count(IvyMaterial material) {
        return blocks.stream().filter(block -> block.material() == material).count();
    }
}
