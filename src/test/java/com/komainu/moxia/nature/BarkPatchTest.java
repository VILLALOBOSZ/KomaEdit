package com.komainu.moxia.nature;

import com.komainu.moxia.edit.PackedBlockPlan;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Las manchas de la corteza miden 3 de alto en cualquier altura; alrededor de y = 0 medían 5 (de −2 a 2). */
class BarkPatchTest {
    @Test
    void barkPatchesAreThreeTallEverywhere() {
        Map<Long, Integer> rows = new HashMap<>();
        for (int y = -63; y <= 62; y++) {
            rows.merge(NatureMaterials.patch(PackedBlockPlan.pack(0, y, 0)), 1, Integer::sum);
        }
        rows.values().forEach(count -> assertEquals(3, count));
    }
}
