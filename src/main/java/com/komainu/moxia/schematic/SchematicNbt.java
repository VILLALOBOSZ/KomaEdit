package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.NbtSizeValidationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Leer y escribir el NBT de un esquema. Al leer, el contador de tamaño de Minecraft corta antes de reservar memoria:
 * un archivo que se descomprime en gigas (una bomba gzip) o un NBT anidado sin fin se rechazan con un mensaje.
 */
public final class SchematicNbt {
    private SchematicNbt() {
    }

    /** ¿Empieza como un gzip? */
    public static boolean gzip(byte[] data) {
        return data.length >= 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B;
    }

    /** El compuesto raíz de un NBT con gzip (o sin comprimir), dentro de los límites. */
    public static NbtCompound read(byte[] data, SchematicLimits limits) {
        NbtSizeTracker tracker = new NbtSizeTracker(limits.maxNbtBytes(), limits.maxDepth());
        try {
            if (gzip(data)) {
                return NbtIo.readCompressed(new ByteArrayInputStream(data), tracker);
            }
            return NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(data)), tracker);
        } catch (NbtSizeValidationException exception) {
            throw new IllegalArgumentException("El archivo es demasiado grande al descomprimirlo (más de "
                    + limits.maxNbtBytes() / (1024 * 1024) + " MiB) o está anidado de más");
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("No es un esquema o está dañado (no se puede leer su NBT)");
        }
    }

    /** El NBT con gzip, como lo escriben Minecraft, WorldEdit y Litematica. */
    public static byte[] write(NbtCompound root) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(root, output);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return output.toByteArray();
    }

    // ------------------------------------------------------------------ Lectura tolerante

    static int[] vector(NbtCompound tag, String key) {
        if (tag.contains(key, NbtElement.INT_ARRAY_TYPE)) {
            int[] values = tag.getIntArray(key);
            return values.length == 3 ? values : null;
        }
        if (tag.contains(key, NbtElement.LIST_TYPE)) {
            NbtList list = tag.getList(key, NbtElement.INT_TYPE);
            return list.size() == 3 ? new int[]{list.getInt(0), list.getInt(1), list.getInt(2)} : null;
        }
        if (tag.contains(key, NbtElement.COMPOUND_TYPE)) {
            NbtCompound xyz = tag.getCompound(key);
            if (xyz.contains("x", NbtElement.NUMBER_TYPE) && xyz.contains("y", NbtElement.NUMBER_TYPE)
                    && xyz.contains("z", NbtElement.NUMBER_TYPE)) {
                return new int[]{xyz.getInt("x"), xyz.getInt("y"), xyz.getInt("z")};
            }
        }
        return null;
    }

    static double[] position(NbtCompound tag, String key) {
        NbtList list = tag.getList(key, NbtElement.DOUBLE_TYPE);
        if (list.size() != 3) {
            return null;
        }
        double[] values = {list.getDouble(0), list.getDouble(1), list.getDouble(2)};
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return null;
            }
        }
        return values;
    }

    static NbtList doubles(double x, double y, double z) {
        NbtList list = new NbtList();
        list.add(NbtDouble.of(x));
        list.add(NbtDouble.of(y));
        list.add(NbtDouble.of(z));
        return list;
    }

    static NbtList ints(int x, int y, int z) {
        NbtList list = new NbtList();
        list.add(NbtInt.of(x));
        list.add(NbtInt.of(y));
        list.add(NbtInt.of(z));
        return list;
    }

    static NbtCompound xyz(int x, int y, int z) {
        NbtCompound tag = new NbtCompound();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    /** Un texto corto para enseñarlo: sin saltos ni caracteres de control. */
    static String text(NbtCompound tag, String key, int max) {
        if (!tag.contains(key, NbtElement.STRING_TYPE)) {
            return "";
        }
        String value = tag.getString(key);
        StringBuilder clean = new StringBuilder(Math.min(value.length(), max));
        for (int index = 0; index < value.length() && clean.length() < max; index++) {
            char character = value.charAt(index);
            clean.append(Character.isISOControl(character) ? ' ' : character);
        }
        return clean.toString().strip();
    }
}
