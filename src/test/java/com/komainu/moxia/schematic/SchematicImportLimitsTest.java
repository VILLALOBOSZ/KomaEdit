package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Los topes de {@link SchematicLimits} en los dos caminos que no pasan por los códecs: el {@code sponge.3} de esta
 * versión (o de una más nueva), que se queda tal cual, y el {@code .schematic} de MCEdit, que convierte WorldEdit.
 */
class SchematicImportLimitsTest {
    private static final SchematicUpgrade.Fixer FIXER = new SchematicUpgrade.Fixer() {
        @Override
        public int current() {
            return 3955;
        }

        @Override
        public NbtCompound fix(SchematicUpgrade.Kind kind, NbtCompound tag, int from) {
            return tag;
        }
    };

    private static byte[] gzip(NbtCompound root) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, output);
        return output.toByteArray();
    }

    /** 1×1×1 de aire, de una versión de Minecraft que aún no existe, con tantas entidades como se pida. */
    private static byte[] futureSponge(int entities) throws IOException {
        NbtCompound body = new NbtCompound();
        body.putInt("Version", 3);
        body.putInt("DataVersion", 99_999);
        body.putShort("Width", (short) 1);
        body.putShort("Height", (short) 1);
        body.putShort("Length", (short) 1);
        NbtCompound palette = new NbtCompound();
        palette.putInt("minecraft:air", 0);
        NbtCompound blocks = new NbtCompound();
        blocks.put("Palette", palette);
        blocks.putByteArray("Data", new byte[]{0});
        body.put("Blocks", blocks);
        NbtList list = new NbtList();
        for (int index = 0; index < entities; index++) {
            NbtCompound entity = new NbtCompound();
            entity.putString("Id", "minecraft:armor_stand");
            NbtList pos = new NbtList();
            pos.add(NbtDouble.of(0.5));
            pos.add(NbtDouble.of(0.0));
            pos.add(NbtDouble.of(0.5));
            entity.put("Pos", pos);
            list.add(entity);
        }
        body.put("Entities", list);
        NbtCompound root = new NbtCompound();
        root.put("Schematic", body);
        return gzip(root);
    }

    @Test
    void aSpongeFromANewerVersionStillHasItsLimits() throws IOException {
        byte[] crowded = futureSponge(SchematicLimits.DEFAULT.maxEntities() + 1);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> SchematicImport.toSponge(crowded, "", SchematicLimits.DEFAULT, FIXER, null));
        assertTrue(refused.getMessage().contains("entidades"), refused.getMessage());
        // Uno normal sigue pasando tal cual.
        byte[] fine = futureSponge(3);
        assertFalse(SchematicImport.toSponge(fine, "", SchematicLimits.DEFAULT, FIXER, null).warnings().isEmpty(),
                "avisa de que es de una versión más nueva");
    }

    private static byte[] mcedit(short width, short height, short length, int blocks) throws IOException {
        NbtCompound root = new NbtCompound();
        root.putShort("Width", width);
        root.putShort("Height", height);
        root.putShort("Length", length);
        root.putString("Materials", "Alpha");
        root.putByteArray("Blocks", new byte[blocks]);
        root.putByteArray("Data", new byte[blocks]);
        return gzip(root);
    }

    /** Unos cien bytes que dicen medir 32767 × 32767: WorldEdit reservaba la caja entera antes de leer nada. */
    @Test
    void aLegacySchematicThatLiesAboutItsSizeNeverReachesWorldEdit() throws IOException {
        byte[] bomb = mcedit((short) 32_767, (short) 32_767, (short) 1, 0);
        assertTrue(bomb.length < 200, bomb.length + " bytes");
        SchematicImport.Legacy never = schematic -> fail("WorldEdit no tenía que leerlo");
        assertThrows(IllegalArgumentException.class,
                () -> SchematicImport.toSponge(bomb, "", SchematicLimits.DEFAULT, FIXER, never));
        byte[] lies = mcedit((short) 64, (short) 64, (short) 64, 10);
        assertThrows(IllegalArgumentException.class,
                () -> SchematicImport.toSponge(lies, "", SchematicLimits.DEFAULT, FIXER, never));
        byte[] negative = mcedit((short) -1, (short) 2, (short) 2, 4);
        assertThrows(IllegalArgumentException.class,
                () -> SchematicImport.toSponge(negative, "", SchematicLimits.DEFAULT, FIXER, never));
    }

    @Test
    void anHonestLegacySchematicStillGoesToWorldEdit() throws IOException {
        byte[] small = mcedit((short) 2, (short) 1, (short) 2, 4);
        IllegalArgumentException reached = assertThrows(IllegalArgumentException.class,
                () -> SchematicImport.toSponge(small, "", SchematicLimits.DEFAULT, FIXER, schematic -> {
                    throw new IOException("WorldEdit lo lee");
                }));
        assertTrue(reached.getMessage().contains("WorldEdit lo lee"), reached.getMessage());
    }
}
