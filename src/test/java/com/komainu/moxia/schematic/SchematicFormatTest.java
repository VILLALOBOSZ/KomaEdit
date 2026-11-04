package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El formato se elige por nombre, por extensión o por lo que hay dentro, sin que el jugador tenga que saberlo. */
class SchematicFormatTest {
    @Test
    void namesAndExtensions() {
        assertEquals(Optional.of(SchematicFormat.LITEMATIC), SchematicFormat.byName("Litematica"));
        assertEquals(Optional.of(SchematicFormat.STRUCTURE), SchematicFormat.byName("estructura"));
        assertEquals(Optional.of(SchematicFormat.STRUCTURE), SchematicFormat.byName(".nbt"));
        assertEquals(Optional.of(SchematicFormat.SPONGE), SchematicFormat.byName("schem"));
        assertTrue(SchematicFormat.byName("png").isEmpty());
        assertEquals(Optional.of(SchematicFormat.LITEMATIC), SchematicFormat.byFileName("Casa Grande.LITEMATIC"));
        assertEquals(Optional.of(SchematicFormat.STRUCTURE), SchematicFormat.byFileName("igloo.nbt"));
        assertTrue(SchematicFormat.byFileName("viejo.schematic").isEmpty());
        assertTrue(SchematicFormat.schematicFile("viejo.schematic"));
        assertFalse(SchematicFormat.schematicFile("paquete.kpack"));
    }

    @Test
    void detectedByContent() {
        SchematicData data = SchematicFixtures.random(2, 2, 2, 3, 1);
        for (SchematicFormat format : SchematicFormat.values()) {
            byte[] file = SchematicIo.write(data, format, "x");
            assertTrue(SchematicNbt.gzip(file));
            assertEquals(format, SchematicIo.read(file, SchematicLimits.DEFAULT, null).format());
        }
        NbtCompound legacy = new NbtCompound();
        legacy.putShort("Width", (short) 1);
        legacy.putByteArray("Blocks", new byte[1]);
        IllegalArgumentException old = assertThrows(IllegalArgumentException.class,
                () -> SchematicFormat.detect(legacy));
        assertTrue(old.getMessage().contains("MCEdit"), old.getMessage());
        IllegalArgumentException region = assertThrows(IllegalArgumentException.class,
                () -> SchematicIo.read(SchematicIo.toSponge(data), SchematicLimits.DEFAULT, "tejado"));
        assertTrue(region.getMessage().contains("regiones"), region.getMessage());
    }
}
