package com.komainu.moxia.rebuild;

import com.komainu.moxia.edit.PackedBlockPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los archivos de una reconstrucción tras una caída: los cofres vuelven con lo que llevaban al quitarlos (no con lo
 * leído al empezar), cancelar está en el disco antes de deshacer nada y lo que se ordenaba cuando paró el servidor no
 * se levanta al arrancar.
 */
class RebuildFilesTest {
    private static final UUID OWNER = UUID.fromString("6b4b2a4e-5d0a-4b8e-9a1f-2f4d1c9e7a10");
    private static final String FULL_CHEST = "minecraft:chest[facing=north]{Items:[{Slot:0b,id:\"minecraft:diamond\","
            + "count:64}]}";
    private static final String EMPTIED_CHEST = "minecraft:chest[facing=north]{Items:[]}";

    @TempDir
    Path folder;

    /** Un cofre lleno de diamantes y una piedra; la paleta como la escribe el juego, con los datos del cofre. */
    private record Obra(RebuildFixtures.Fixture fixture, List<String> palette, int chest, int stone) {
        RebuildRecovery.Snapshot snapshot() {
            return RebuildRecovery.Snapshot.of("minecraft:overworld", OWNER, "Reconstrucción de la selección",
                    fixture.model(), palette);
        }
    }

    private static Obra obra() {
        RebuildFixtures.Fixture fixture = RebuildFixtures.selection(new RebuildFixtures.Build()
                .set(0, 0, 0, "chest[facing=north]").set(1, 0, 0, "stone"));
        int chest = fixture.model().target(entryAt(fixture, PackedBlockPlan.pack(0, 0, 0)));
        int stone = fixture.model().target(entryAt(fixture, PackedBlockPlan.pack(1, 0, 0)));
        List<String> palette = new ArrayList<>(fixture.palette());
        palette.set(chest, FULL_CHEST);
        return new Obra(fixture, palette, chest, stone);
    }

    private static int entryAt(RebuildFixtures.Fixture fixture, long position) {
        for (int entry = 0; entry < fixture.model().size(); entry++) {
            if (fixture.model().position(entry) == position) {
                return entry;
            }
        }
        throw new AssertionError("no está en la obra");
    }

    private RebuildFiles files() {
        return new RebuildFiles(folder.resolve("rebuilds").resolve("4f1c0d3e-obra.bin"));
    }

    private List<Path> left() throws IOException {
        Path rebuilds = folder.resolve("rebuilds");
        if (!Files.isDirectory(rebuilds)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(rebuilds)) {
            return paths.toList();
        }
    }

    /**
     * Alguien saca los diamantes del cofre entre la lectura y el vaciado; el cofre se quita (apuntado con lo que lleva
     * entonces) y se cae el servidor antes de volver a ponerlo. Al arrancar tiene que volver vacío: con lo leído al
     * empezar, los diamantes estarían a la vez en el inventario y en el cofre.
     */
    @Test
    void aChestComesBackWithWhatItHadWhenItWasTaken() throws IOException {
        Obra obra = obra();
        RebuildFiles files = files();
        assertTrue(files.write(obra.snapshot()));
        files.took(obra.chest(), EMPTIED_CHEST);
        // La caída: el proceso muere sin cerrar nada.
        RebuildFiles.Loaded loaded = RebuildFiles.read(files.snapshot());
        assertEquals(EMPTIED_CHEST, loaded.snapshot().palette().get(obra.chest()));
        assertEquals(obra.palette().get(obra.stone()), loaded.snapshot().palette().get(obra.stone()),
                "lo que no se apuntó, como se leyó");
        assertEquals(1, loaded.taken());
        assertTrue(loaded.snapshot().sameBefore(), "la selección sigue siendo una sola paleta");
        assertFalse(loaded.restore());
        files.close();

        RebuildFiles untouched = new RebuildFiles(folder.resolve("rebuilds").resolve("otra.bin"));
        untouched.write(obra.snapshot());
        assertEquals(FULL_CHEST, RebuildFiles.read(untouched.snapshot()).snapshot().palette().get(obra.chest()),
                "sin nada quitado, lo de la lectura");
    }

    /** La caída llega mientras se apunta: lo cortado no cuenta (el bloque aún no se había quitado). */
    @Test
    void aNoteCutByTheCrashIsIgnored() throws IOException {
        Obra obra = obra();
        RebuildFiles files = files();
        files.write(obra.snapshot());
        files.took(obra.chest(), EMPTIED_CHEST);
        files.took(obra.chest(), "minecraft:chest[facing=north]{Items:[{Slot:0b,id:\"minecraft:dirt\",count:1}]}");
        files.close();
        Path taken = RebuildFiles.taken(files.snapshot());
        byte[] bytes = Files.readAllBytes(taken);
        Files.write(taken, java.util.Arrays.copyOf(bytes, bytes.length - 5));
        RebuildFiles.Loaded loaded = RebuildFiles.read(files.snapshot());
        assertEquals(EMPTIED_CHEST, loaded.snapshot().palette().get(obra.chest()));
        assertEquals(1, loaded.taken());

        Files.write(taken, new byte[]{1, 2, 3});
        assertEquals(FULL_CHEST, RebuildFiles.read(files.snapshot()).snapshot().palette().get(obra.chest()),
                "un apunte que no es nuestro no cambia nada");
    }

    /** Cancelar se apunta antes de deshacer nada: si se cae deshaciendo, al arrancar se sigue deshaciendo. */
    @Test
    void cancellingIsOnDiskBeforeAnythingIsUndone() throws IOException {
        RebuildFiles files = files();
        files.write(obra().snapshot());
        files.markRestore();
        assertTrue(RebuildFiles.read(files.snapshot()).restore());
    }

    /**
     * El servidor se para mientras se ordena una obra enorme (más que la espera al parar): nada se tocó, y el archivo
     * que el hilo de la reconstrucción escribe después no puede quedarse para levantarla en el próximo arranque.
     */
    @Test
    void whatWasStillBeingOrderedWhenTheServerStoppedIsNotBuiltOnRestart() throws IOException {
        RebuildFiles files = files();
        files.abandon();
        assertFalse(files.write(obra().snapshot()));
        assertEquals(List.of(), left());

        RebuildFiles written = new RebuildFiles(folder.resolve("rebuilds").resolve("antes.bin"));
        assertTrue(written.write(obra().snapshot()));
        written.abandon();
        assertEquals(List.of(), left(), "y si ya estaba escrito, se borra al abandonarlo");
    }

    /** Al acabar no queda nada: ni la foto, ni su copia, ni lo apuntado, ni la marca de deshacer. */
    @Test
    void finishingLeavesNothingBehind() throws IOException {
        Obra obra = obra();
        RebuildFiles files = files();
        files.write(obra.snapshot());
        files.write(obra.snapshot());
        files.took(obra.chest(), EMPTIED_CHEST);
        files.markRestore();
        assertTrue(Files.exists(com.komainu.moxia.persistence.AtomicStorage.backup(files.snapshot())));
        files.delete();
        assertEquals(List.of(), left());
    }
}
