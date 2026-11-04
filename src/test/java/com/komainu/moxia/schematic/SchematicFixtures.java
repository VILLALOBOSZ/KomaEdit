package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Esquemas hechos por las propias pruebas: paletas del tamaño que se pida, bloques al azar con semilla fija. */
final class SchematicFixtures {
    private static final String[] INSTRUMENTS = {"harp", "basedrum", "snare", "hat", "bass", "flute", "bell",
            "guitar", "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling"};

    private SchematicFixtures() {
    }

    /** {@code count} estados distintos: el primero aire, luego piedra y bloques musicales con sus propiedades. */
    static List<String> palette(int count) {
        List<String> states = new ArrayList<>();
        states.add(SchematicData.AIR);
        if (count > 1) {
            states.add("minecraft:stone");
        }
        for (int index = 0; states.size() < count; index++) {
            states.add("minecraft:note_block[instrument=" + INSTRUMENTS[(index / 50) % INSTRUMENTS.length]
                    + ",note=" + index % 25 + ",powered=" + ((index / 25) % 2 == 0) + "]");
        }
        return states;
    }

    /** Un esquema con esa paleta repartida al azar (todos los estados salen al menos una vez si caben). */
    static SchematicData random(int sizeX, int sizeY, int sizeZ, int paletteSize, long seed) {
        List<String> palette = palette(paletteSize);
        int[] blocks = new int[sizeX * sizeY * sizeZ];
        SplittableRandom random = new SplittableRandom(seed);
        for (int index = 0; index < blocks.length; index++) {
            blocks[index] = index < paletteSize ? index : random.nextInt(paletteSize);
        }
        return new SchematicData(sizeX, sizeY, sizeZ, palette, blocks, List.of(), List.of(), 3955, -2, 0, 3,
                new SchematicData.Info("prueba", "Alicia", "una prueba", 1_000L, 2_000L, null, List.of()));
    }

    /** Un cofre con un objeto con nombre, un cartel escrito y un soporte de armadura. */
    static SchematicData furnished() {
        List<String> palette = List.of(SchematicData.AIR, "minecraft:stone",
                "minecraft:chest[facing=north,type=single,waterlogged=false]",
                "minecraft:oak_sign[rotation=4,waterlogged=false]");
        int[] blocks = new int[4 * 3 * 2];
        blocks[0] = 1;
        blocks[1] = 2;
        blocks[2 + 4] = 3;
        NbtCompound chest = new NbtCompound();
        NbtList items = new NbtList();
        NbtCompound item = new NbtCompound();
        item.putString("id", "minecraft:diamond");
        item.putInt("count", 3);
        item.putByte("Slot", (byte) 4);
        items.add(item);
        chest.put("Items", items);
        NbtCompound sign = new NbtCompound();
        NbtCompound front = new NbtCompound();
        NbtList messages = new NbtList();
        for (String line : new String[]{"\"Hola\"", "\"\"", "\"\"", "\"\""}) {
            messages.add(NbtString.of(line));
        }
        front.put("messages", messages);
        sign.put("front_text", front);
        NbtCompound stand = new NbtCompound();
        NbtList rotation = new NbtList();
        rotation.add(NbtFloat.of(90F));
        rotation.add(NbtFloat.of(0F));
        stand.put("Rotation", rotation);
        stand.putBoolean("ShowArms", true);
        return new SchematicData(4, 3, 2, palette, blocks,
                List.of(new SchematicData.BlockEntity(1, 0, 0, "minecraft:chest", chest),
                        new SchematicData.BlockEntity(2, 0, 1, "minecraft:sign", sign)),
                List.of(new SchematicData.Entity(2.5, 1.0, 0.5, "minecraft:armor_stand", stand)),
                3955, 0, 0, 0, new SchematicData.Info("salón", "Bruno", "", 5L, 6L, null, List.of()));
    }
}
