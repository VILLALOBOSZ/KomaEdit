package com.komainu.moxia.share;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternBlocksTest {
    @Test
    void findsEveryBlockAndLeavesTheRestAlone() {
        List<String> seen = new ArrayList<>();
        String result = PatternBlocks.rewrite("60%stone,40%mod:fir_stairs[facing=east,half=top] #clipboard ##wool "
                + "!air >grass_block $plains %50 *oak_log ^spruce_planks", block -> {
            seen.add(block);
            return block.toUpperCase();
        });
        assertEquals(List.of("stone", "mod:fir_stairs[facing=east,half=top]", "air", "grass_block", "oak_log",
                "spruce_planks"), seen);
        assertEquals("60%STONE,40%MOD:FIR_STAIRS[FACING=EAST,HALF=TOP] #clipboard ##wool !AIR >GRASS_BLOCK "
                + "$plains %50 *OAK_LOG ^SPRUCE_PLANKS", result);
    }

    @Test
    void worldEditExpressionsCannotBeImported() {
        ShareCodecTest.assertMessage("expresiones", () -> PatternBlocks.rewrite("stone =y<64", block -> block));
        ShareCodecTest.assertMessage("expresiones", () -> PatternBlocks.rewrite("!=queryRel(0,1,0,0,0)",
                block -> block));
    }

    @Test
    void checkRewritesMissingBlocksInsideAPattern() {
        ShareCheck check = ShareFixtures.check();
        check.begin();
        String pattern = check.pattern("El patrón", "50%create:andesite_casing,50%stone");
        assertEquals("50%minecraft:andesite,50%minecraft:stone", pattern);
        assertTrue(check.warnings().getFirst().contains("create:andesite_casing → andesite"));
        ShareCodecTest.assertMessage("no permitidos", () -> check.pattern("El patrón", "stone§4"));
        ShareCodecTest.assertMessage("1024", () -> check.pattern("El patrón", "stone,".repeat(300)));
    }
}
