package com.komainu.moxia.rebuild;

import com.sk89q.worldedit.MaxChangedBlocksException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Que un reinicio no deje una obra a medias. Antes de vaciar nada, la reconstrucción guarda en disco lo que tiene que
 * quedar y lo que había ({@link Snapshot}); al terminar o cancelar, se borra. Si el servidor se para con normalidad,
 * la reconstrucción se termina al momento antes de guardar el mundo; si se cae, al volver a arrancar se encuentra el
 * archivo y {@link Run} deja la obra acabada (o, si se estaba cancelando, como estaba antes).
 *
 * <p>Tras una caída, cada chunk está como se guardó por última vez: puede que aún con todo lo de antes, vaciado o a
 * medio levantar. Por eso la regla es por posición: si tiene lo que debe quedar, nada; si está vacía o tiene lo que
 * había antes, se escribe lo que debe quedar; si tiene otra cosa, alguien la cambió y se deja. Java puro: el archivo
 * es un flujo y el mundo llega por {@link RebuildRun.World}.</p>
 */
public final class RebuildRecovery {
    private static final int MAGIC = 0x4B455242;
    private static final int VERSION = 1;

    private RebuildRecovery() {
    }

    /**
     * Lo guardado de una reconstrucción.
     *
     * @param dimension   id del mundo ({@code minecraft:overworld})
     * @param palette     cada entrada como texto (estado y datos); la 0 es el aire
     * @param before      lo de antes por posición; el mismo array que {@code target} si es lo mismo
     */
    public record Snapshot(String dimension, UUID owner, String description, List<String> palette, long[] positions,
                           int[] target, int[] before) {
        public Snapshot {
            palette = List.copyOf(palette);
            if (positions.length != target.length || positions.length != before.length) {
                throw new IllegalArgumentException("Posiciones y bloques no cuadran");
            }
        }

        /** Del modelo y el texto de su paleta (en el orden de la paleta). */
        public static Snapshot of(String dimension, UUID owner, String description, RebuildModel model,
                                  List<String> palette) {
            if (palette.size() != model.paletteSize()) {
                throw new IllegalArgumentException("La paleta no es la del modelo");
            }
            long[] positions = new long[model.size()];
            int[] target = new int[model.size()];
            int[] before = model.sameBefore() ? target : new int[model.size()];
            for (int entry = 0; entry < model.size(); entry++) {
                positions[entry] = model.position(entry);
                target[entry] = model.target(entry);
                if (!model.sameBefore()) {
                    before[entry] = model.before(entry);
                }
            }
            return new Snapshot(dimension, owner, description, palette, positions, target, before);
        }

        public boolean sameBefore() {
            return before == target;
        }

        public int size() {
            return positions.length;
        }
    }

    public static void write(Snapshot snapshot, OutputStream output) throws IOException {
        GZIPOutputStream gzip = new GZIPOutputStream(output);
        DataOutputStream data = new DataOutputStream(new BufferedOutputStream(gzip, 1 << 16));
        data.writeInt(MAGIC);
        data.writeInt(VERSION);
        text(data, snapshot.dimension());
        data.writeLong(snapshot.owner().getMostSignificantBits());
        data.writeLong(snapshot.owner().getLeastSignificantBits());
        text(data, snapshot.description());
        data.writeBoolean(snapshot.sameBefore());
        data.writeInt(snapshot.palette().size());
        for (String entry : snapshot.palette()) {
            text(data, entry);
        }
        data.writeInt(snapshot.size());
        for (int entry = 0; entry < snapshot.size(); entry++) {
            data.writeLong(snapshot.positions()[entry]);
            data.writeInt(snapshot.target()[entry]);
            if (!snapshot.sameBefore()) {
                data.writeInt(snapshot.before()[entry]);
            }
        }
        data.flush();
        gzip.finish();
    }

    public static Snapshot read(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(new BufferedInputStream(new GZIPInputStream(input), 1 << 16));
        if (data.readInt() != MAGIC) {
            throw new IOException("No es un archivo de reconstrucción de KomaEdit");
        }
        int version = data.readInt();
        if (version != VERSION) {
            throw new IOException("Versión de archivo de reconstrucción desconocida: " + version);
        }
        String dimension = text(data);
        UUID owner = new UUID(data.readLong(), data.readLong());
        String description = text(data);
        boolean sameBefore = data.readBoolean();
        int paletteSize = count(data.readInt(), 1 << 24);
        List<String> palette = new ArrayList<>(paletteSize);
        for (int entry = 0; entry < paletteSize; entry++) {
            palette.add(text(data));
        }
        int size = count(data.readInt(), Integer.MAX_VALUE - 8);
        long[] positions = new long[size];
        int[] target = new int[size];
        int[] before = sameBefore ? target : new int[size];
        for (int entry = 0; entry < size; entry++) {
            positions[entry] = data.readLong();
            target[entry] = palette(data.readInt(), paletteSize);
            if (!sameBefore) {
                before[entry] = palette(data.readInt(), paletteSize);
            }
        }
        return new Snapshot(dimension, owner, description, palette, positions, target, before);
    }

    private static void text(DataOutputStream data, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        data.writeInt(bytes.length);
        data.write(bytes);
    }

    private static String text(DataInputStream data) throws IOException {
        int length = count(data.readInt(), 64 << 20);
        return new String(data.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static int count(int value, int max) throws IOException {
        if (value < 0 || value > max) {
            throw new IOException("Archivo de reconstrucción dañado");
        }
        return value;
    }

    private static int palette(int value, int size) throws IOException {
        if (value < 0 || value >= size) {
            throw new IOException("Archivo de reconstrucción dañado: bloque " + value + " fuera de la paleta");
        }
        return value;
    }

    /**
     * Deja la obra acabada (o, con {@code restore}, como estaba) posición a posición, con plazo, para repartirla entre
     * ticks si es grande. Lo que ya está bien no se escribe.
     */
    public static final class Run {
        private final Snapshot snapshot;
        private final int[] paletteRaw;
        private final boolean restore;
        private int next;
        private int written;
        private int kept;

        /**
         * @param paletteRaw id crudo de cada entrada de la paleta en este mundo, tal como se leyó del archivo
         * @param restore    dejar lo de antes en vez de la obra (se estaba cancelando)
         */
        public Run(Snapshot snapshot, int[] paletteRaw, boolean restore) {
            if (paletteRaw.length != snapshot.palette().size()) {
                throw new IllegalArgumentException("Faltan ids de la paleta");
            }
            this.snapshot = snapshot;
            this.paletteRaw = paletteRaw.clone();
            this.restore = restore;
        }

        public int step(RebuildRun.World world, int maxEntries, long deadline, LongSupplier clock)
                throws MaxChangedBlocksException {
            int processed = 0;
            while (next < snapshot.size() && processed < maxEntries && clock.getAsLong() < deadline) {
                int goal = restore ? snapshot.before()[next] : snapshot.target()[next];
                int other = restore ? snapshot.target()[next] : snapshot.before()[next];
                long position = snapshot.positions()[next];
                int current = world.rawAt(position);
                if (!matches(world, current, goal)) {
                    if (world.air(current) || matches(world, current, other)) {
                        world.write(position, goal);
                        written++;
                    } else {
                        kept++;
                    }
                }
                next++;
                processed++;
            }
            return processed;
        }

        private boolean matches(RebuildRun.World world, int current, int palette) {
            return palette == RebuildModel.AIR ? world.air(current) : current == paletteRaw[palette];
        }

        public boolean done() {
            return next >= snapshot.size();
        }

        public int processed() {
            return next;
        }

        public int written() {
            return written;
        }

        public int kept() {
            return kept;
        }

        public Snapshot snapshot() {
            return snapshot;
        }

        public boolean restore() {
            return restore;
        }
    }
}
