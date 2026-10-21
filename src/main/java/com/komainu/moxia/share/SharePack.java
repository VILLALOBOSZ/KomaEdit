package com.komainu.moxia.share;

import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;

/**
 * Un paquete de KomaEdit: lo que un builder se lleva de un servidor a otro (paletas, ruidos, presets, recetas y
 * planos). Solo son datos: nada de lo que trae se ejecuta, y al importarlo cada elemento pasa por su
 * {@link ShareKind#check}. {@link ShareCodec} lo convierte en un código de texto o en un archivo {@code .kpack}.
 *
 * @param format     versión del formato del paquete ({@link #FORMAT}); los anteriores se siguen leyendo
 * @param name       nombre del paquete, solo para enseñarlo
 * @param author     quién lo exportó, tal y como se llamaba entonces
 * @param modVersion versión de KomaEdit que lo exportó, solo para enseñarla
 * @param created    cuándo se exportó (milisegundos desde 1970), o 0 si no se sabe
 */
public record SharePack(int format, String name, String author, String modVersion, long created, List<Item> items) {
    /** Versión del formato que escribe esta versión del mod. */
    public static final int FORMAT = 1;

    public SharePack {
        name = name == null ? "" : name;
        author = author == null ? "" : author;
        modVersion = modVersion == null ? "" : modVersion;
        items = List.copyOf(items);
    }

    /** Un paquete nuevo en el formato actual. */
    public static SharePack of(String name, String author, String modVersion, long created, List<Item> items) {
        return new SharePack(FORMAT, name, author, modVersion, created, items);
    }

    public SharePack withItems(List<Item> value) {
        return new SharePack(format, name, author, modVersion, created, value);
    }

    /** Bytes de los adjuntos (los esquemas de los planos). */
    public long blobBytes() {
        long total = 0L;
        for (Item item : items) {
            total += item.blob().length;
        }
        return total;
    }

    /**
     * Un elemento del paquete.
     *
     * @param kind    id del tipo ({@code palette}, {@code noise}, {@code tree}…; ver {@link ShareKinds})
     * @param name    nombre con el que se guardó
     * @param version versión de los datos de ese tipo, para que el tipo los migre si cambian
     * @param data    los ajustes, en el JSON propio del tipo
     * @param blob    un adjunto binario (el esquema de un plano), o vacío
     */
    public record Item(String kind, String name, int version, JsonObject data, byte[] blob) {
        public Item {
            kind = kind == null ? "" : kind;
            name = name == null ? "" : name;
            data = data == null ? new JsonObject() : data.deepCopy();
            blob = blob == null ? new byte[0] : blob.clone();
        }

        public Item(String kind, String name, int version, JsonObject data) {
            this(kind, name, version, data, null);
        }

        @Override
        public JsonObject data() {
            return data.deepCopy();
        }

        @Override
        public byte[] blob() {
            return blob.clone();
        }

        public boolean hasBlob() {
            return blob.length > 0;
        }

        /** {@code paleta rocas}: para los mensajes. */
        public String label() {
            ShareKind found = ShareKinds.find(kind);
            return (found == null ? kind : found.singular()) + " «" + name + "»";
        }

        public Item withName(String value) {
            return new Item(kind, value, version, data, blob);
        }

        public Item withData(int newVersion, JsonObject value) {
            return new Item(kind, name, newVersion, value, blob);
        }

        public Item withBlob(byte[] value) {
            return new Item(kind, name, version, data, value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Item item && kind.equals(item.kind) && name.equals(item.name)
                    && version == item.version && data.equals(item.data) && Arrays.equals(blob, item.blob);
        }

        @Override
        public int hashCode() {
            return ((kind.hashCode() * 31 + name.hashCode()) * 31 + version) * 31 + Arrays.hashCode(blob);
        }

        @Override
        public String toString() {
            return "Item[" + kind + ":" + name + " v" + version + " " + data + (blob.length > 0
                    ? " +" + blob.length + " B" : "") + "]";
        }
    }
}
