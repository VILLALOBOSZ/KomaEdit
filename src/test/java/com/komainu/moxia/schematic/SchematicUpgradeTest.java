package com.komainu.moxia.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Esquemas de versiones anteriores puestos al día: con un DataFixer de mentira (qué se le pasa y qué se hace con lo
 * que devuelve) y con el de Minecraft de verdad (la hierba de 1.20.2 y los carteles de antes de 1.20).
 */
class SchematicUpgradeTest {
    /** Cambia {@code grass} por {@code short_grass} y {@code Sign} por {@code minecraft:sign}; falla con la lava. */
    private static final SchematicUpgrade.Fixer FAKE = new SchematicUpgrade.Fixer() {
        @Override
        public int current() {
            return 3955;
        }

        @Override
        public NbtCompound fix(SchematicUpgrade.Kind kind, NbtCompound tag, int from) {
            NbtCompound fixed = tag.copy();
            switch (kind) {
                case BLOCK_STATE -> {
                    if (tag.getString("Name").equals("minecraft:lava")) {
                        throw new IllegalStateException("no sé");
                    }
                    if (tag.getString("Name").equals("minecraft:grass")) {
                        fixed.putString("Name", "minecraft:short_grass");
                    }
                }
                case BLOCK_ENTITY -> {
                    assertTrue(tag.contains("x"), "el DataFixer recibe la posición");
                    if (tag.getString("id").equals("Sign")) {
                        fixed.putString("id", "minecraft:sign");
                        fixed.putBoolean("is_waxed", false);
                    }
                }
                case ENTITY -> fixed.putString("id", "minecraft:" + tag.getString("id").toLowerCase());
            }
            return fixed;
        }
    };

    @Test
    void oldDataIsFixedAndFailuresStayAsTheyWere() {
        SchematicData old = new SchematicData(2, 1, 1, List.of("minecraft:grass", "minecraft:lava"),
                new int[]{0, 1}, List.of(new SchematicData.BlockEntity(0, 0, 0, "Sign", new NbtCompound())),
                List.of(new SchematicData.Entity(0.5, 0, 0.5, "Pig", new NbtCompound())), 1976, 0, 0, 0, null);
        List<String> warnings = new ArrayList<>();
        SchematicData fixed = SchematicUpgrade.upgrade(old, FAKE, warnings);
        assertEquals(3955, fixed.dataVersion());
        assertEquals(List.of("minecraft:short_grass", "minecraft:lava"), fixed.palette());
        assertEquals("minecraft:sign", fixed.blockEntities().getFirst().id());
        assertFalse(fixed.blockEntities().getFirst().data().contains("x"), "la posición no se queda en los datos");
        assertTrue(fixed.blockEntities().getFirst().data().contains("is_waxed"));
        assertEquals("minecraft:pig", fixed.entities().getFirst().id());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("1 datos"), warnings.toString());
    }

    @Test
    void currentDataIsLeftAloneAndNewerDataIsWarned() {
        SchematicData current = SchematicFixtures.random(2, 2, 2, 3, 1);
        List<String> warnings = new ArrayList<>();
        assertSame(current, SchematicUpgrade.upgrade(current, FAKE, warnings));
        assertTrue(warnings.isEmpty());
        SchematicData newer = current.withData(current.palette(), List.of(), List.of(), 9999);
        assertSame(newer, SchematicUpgrade.upgrade(newer, FAKE, warnings));
        assertTrue(warnings.getFirst().contains("más nueva"), warnings.toString());
    }

    @Test
    void vanillaFixerUpdatesGrassAndOldSigns() {
        SharedConstants.createGameVersion();
        SchematicUpgrade.Fixer vanilla = SchematicUpgrade.vanilla();
        assertEquals(3955, vanilla.current(), "Minecraft 1.21.1");
        NbtCompound sign = new NbtCompound();
        for (int line = 1; line <= 4; line++) {
            sign.putString("Text" + line, line == 1 ? "{\"text\":\"Hola\"}" : "\"\"");
        }
        // 1.19.4 (3337): la hierba aún se llamaba grass y los carteles tenían Text1…Text4.
        SchematicData old = new SchematicData(2, 1, 1,
                List.of("minecraft:grass", "minecraft:oak_sign[rotation=0,waterlogged=false]"),
                new int[]{0, 1}, List.of(new SchematicData.BlockEntity(1, 0, 0, "minecraft:sign", sign)), List.of(),
                3337, 0, 0, 0, null);
        List<String> warnings = new ArrayList<>();
        SchematicData fixed = SchematicUpgrade.upgrade(old, vanilla, warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals("minecraft:short_grass", fixed.palette().getFirst());
        NbtCompound data = fixed.blockEntities().getFirst().data();
        NbtList messages = data.getCompound("front_text").getList("messages", NbtElement.STRING_TYPE);
        assertEquals(4, messages.size(), data.toString());
        assertTrue(messages.getString(0).contains("Hola"), messages.toString());
    }
}
