package com.komainu.moxia.rebuild;

import com.komainu.moxia.edit.PackedBlockPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Que un reinicio no deje la obra a medias: el archivo de recuperación vuelve tal cual, y tras una caída en cualquier
 * momento (antes de vaciar, a medio levantar, deshaciendo, ya acabada) la obra queda terminada o, si se cancelaba,
 * como estaba; lo que alguien cambió a mano se respeta.
 */
class RebuildRecoveryTest {
    private static final UUID OWNER = UUID.fromString("6b4b2a4e-5d0a-4b8e-9a1f-2f4d1c9e7a10");
    private static final long UNLIMITED = Long.MAX_VALUE / 4;

    @TempDir
    Path folder;

    private static RebuildRecovery.Snapshot snapshot(RebuildFixtures.Fixture fixture) {
        return RebuildRecovery.Snapshot.of("minecraft:overworld", OWNER, "Reconstrucción de la casa",
                fixture.model(), fixture.palette());
    }

    private static RebuildRecovery.Snapshot roundTrip(RebuildRecovery.Snapshot snapshot) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RebuildRecovery.write(snapshot, output);
        return RebuildRecovery.read(new ByteArrayInputStream(output.toByteArray()));
    }

    /** Los ids crudos de la paleta como los daría el juego al leer el archivo tras el reinicio. */
    private static int[] raw(RebuildFixtures.Fixture fixture) {
        int[] raw = new int[fixture.model().paletteSize()];
        for (int palette = 0; palette < raw.length; palette++) {
            raw[palette] = fixture.model().raw(palette);
        }
        return raw;
    }

    /** El servidor arranca con el mundo como quedó guardado y termina lo que encuentra en el archivo. */
    private static RebuildRecovery.Run recover(RebuildFixtures.Fixture fixture, RebuildFixtures.World saved,
                                               boolean restore) throws Exception {
        RebuildRecovery.Snapshot snapshot = roundTrip(snapshot(fixture));
        RebuildRecovery.Run run = new RebuildRecovery.Run(snapshot, raw(fixture), restore);
        RebuildFixtures.Clock clock = new RebuildFixtures.Clock();
        while (!run.done()) {
            run.step(saved, 64, clock.now + 50_000L, clock);
            saved.flush();
        }
        return run;
    }

    /** El mundo tal como se guardó en el disco cuando se cayó el servidor. */
    private static RebuildFixtures.World saved(RebuildFixtures.Rig rig) {
        RebuildFixtures.World copy = new RebuildFixtures.World(rig.fixture.model(), new RebuildFixtures.Clock());
        copy.blocks.putAll(rig.world.blocks);
        return copy;
    }

    @Test
    void theFileComesBackExactly() throws IOException {
        RebuildFixtures.Fixture paste = RebuildFixtures.paste(RebuildFixtures.house(),
                new RebuildFixtures.Build().fill(-2, -1, -2, 16, 1, 16, "dirt"));
        List<String> palette = new ArrayList<>(paste.palette());
        // Texto con lo que rompería un lector ingenuo: barras, comillas, saltos, tildes y un bloque con datos.
        palette.set(1, "minecraft:oak_sign[rotation=0]{front_text:{messages:['\"Año | nuevo\"','\"¿qué\\ntal?\"']}}");
        RebuildRecovery.Snapshot original = RebuildRecovery.Snapshot.of("kyobble:survival", OWNER, "Reconstrucción ñ",
                paste.model(), palette);
        RebuildRecovery.Snapshot read = roundTrip(original);
        assertEquals("kyobble:survival", read.dimension());
        assertEquals(OWNER, read.owner());
        assertEquals("Reconstrucción ñ", read.description());
        assertEquals(palette, read.palette());
        assertArrayEquals(original.positions(), read.positions());
        assertArrayEquals(original.target(), read.target());
        assertArrayEquals(original.before(), read.before());
        assertFalse(read.sameBefore());
        RebuildRecovery.Snapshot selection = roundTrip(snapshot(RebuildFixtures.selection(RebuildFixtures.house())));
        assertTrue(selection.sameBefore(), "la selección no guarda dos veces lo mismo");
    }

    @Test
    void aDamagedFileIsRejectedInsteadOfGuessed() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RebuildRecovery.write(snapshot(RebuildFixtures.selection(RebuildFixtures.house())), output);
        byte[] bytes = output.toByteArray();
        byte[] cut = java.util.Arrays.copyOf(bytes, bytes.length / 2);
        assertThrows(IOException.class, () -> RebuildRecovery.read(new ByteArrayInputStream(cut)));
        ByteArrayOutputStream other = new ByteArrayOutputStream();
        new PlacementLog(4).write(other);
        assertThrows(IOException.class, () -> RebuildRecovery.read(new ByteArrayInputStream(other.toByteArray())));
    }

    @Test
    void itSurvivesOnDiskBesideTheWorld() throws IOException {
        RebuildRecovery.Snapshot snapshot = snapshot(RebuildFixtures.selection(RebuildFixtures.house()));
        Path file = folder.resolve("rebuilds").resolve("casa.bin");
        Files.createDirectories(file.getParent());
        try (OutputStream output = Files.newOutputStream(file)) {
            RebuildRecovery.write(snapshot, output);
        }
        try (InputStream input = Files.newInputStream(file)) {
            RebuildRecovery.Snapshot read = RebuildRecovery.read(input);
            assertEquals(snapshot.size(), read.size());
            assertArrayEquals(snapshot.target(), read.target());
        }
    }

    @Test
    void aCrashHalfWayThroughTheSelectionLeavesTheWholeBuild() throws Exception {
        RebuildFixtures.Fixture house = RebuildFixtures.selection(RebuildFixtures.house());
        for (int ticks : new int[]{0, 1, 5, 30, 80}) {
            RebuildFixtures.Rig rig = new RebuildFixtures.Rig(house, RebuildOrder.BUILDER,
                    RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(6));
            for (int tick = 0; tick < ticks; tick++) {
                rig.tick(UNLIMITED);
            }
            RebuildFixtures.World saved = saved(rig);
            assertEquals(ticks == 0, saved.showsTarget(), "caída tras " + ticks + " ticks");
            RebuildRecovery.Run run = recover(house, saved, false);
            assertTrue(saved.showsTarget(), "tras " + ticks + " ticks, la casa vuelve a estar entera");
            assertEquals(0, run.kept());
            if (ticks == 0) {
                assertEquals(0, run.written(), "lo que ya está bien no se escribe");
            }
        }
    }

    @Test
    void aCrashHalfWayThroughAPasteFinishesThePaste() throws Exception {
        RebuildFixtures.Fixture paste = RebuildFixtures.paste(RebuildFixtures.house(),
                new RebuildFixtures.Build().fill(-2, -1, -2, 16, 2, 16, "dirt"));
        for (int ticks : new int[]{0, 1, 12, 400}) {
            RebuildFixtures.Rig rig = new RebuildFixtures.Rig(paste, RebuildOrder.LAYERS,
                    RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(12));
            for (int tick = 0; tick < ticks; tick++) {
                rig.tick(UNLIMITED);
            }
            RebuildFixtures.World saved = saved(rig);
            recover(paste, saved, false);
            assertTrue(saved.showsTarget(), "tras " + ticks + " ticks la casa queda puesta y la tierra vaciada");
        }
    }

    @Test
    void aCrashWhileCancellingLeavesEverythingAsItWas() throws Exception {
        RebuildFixtures.Fixture paste = RebuildFixtures.paste(RebuildFixtures.house(),
                new RebuildFixtures.Build().fill(-2, -1, -2, 16, 2, 16, "dirt"));
        RebuildFixtures.Rig rig = new RebuildFixtures.Rig(paste, RebuildOrder.BUILDER,
                RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(10));
        for (int tick = 0; tick < 20; tick++) {
            rig.tick(UNLIMITED);
        }
        rig.run.cancel();
        // Se cae a mitad de deshacer: una parte ya vuelta a la tierra, otra aún con la casa.
        rig.run.place(300, UNLIMITED);
        rig.world.flush();
        RebuildFixtures.World saved = saved(rig);
        assertFalse(saved.showsBefore());
        recover(paste, saved, true);
        assertTrue(saved.showsBefore(), "la marca de deshacer manda: la tierra como estaba");
    }

    @Test
    void whatSomeoneBuiltBeforeTheCrashIsKept() throws Exception {
        RebuildFixtures.Fixture house = RebuildFixtures.selection(RebuildFixtures.house());
        RebuildFixtures.Rig rig = new RebuildFixtures.Rig(house, RebuildOrder.LAYERS,
                RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(4));
        rig.tick(UNLIMITED);
        // En el sitio ya vacío de un tablón de la pared alguien pone un bloque suyo, y se cae el servidor.
        long position = PackedBlockPlan.pack(3, 2, 5);
        assertEquals(RebuildFixtures.AIR_RAW, rig.world.at(position));
        rig.world.blocks.put(position, 4242);
        RebuildFixtures.World saved = saved(rig);
        RebuildRecovery.Run run = recover(house, saved, false);
        assertEquals(4242, saved.at(position), "lo suyo se queda");
        assertEquals(1, run.kept());
        saved.blocks.remove(position);
        saved.blocks.put(position, house.model().raw(house.model().target(entryAt(house, position))));
        assertTrue(saved.showsTarget(), "y todo lo demás, la obra");
    }

    private static int entryAt(RebuildFixtures.Fixture fixture, long position) {
        for (int entry = 0; entry < fixture.model().size(); entry++) {
            if (fixture.model().position(entry) == position) {
                return entry;
            }
        }
        throw new AssertionError("no está en la obra");
    }

    @Test
    void recoveryRespectsItsDeadline() throws Exception {
        RebuildFixtures.Fixture house = RebuildFixtures.selection(RebuildFixtures.house());
        RebuildRecovery.Run run = new RebuildRecovery.Run(snapshot(house), raw(house), false);
        RebuildFixtures.Clock clock = new RebuildFixtures.Clock();
        RebuildFixtures.World world = new RebuildFixtures.World(house.model(), clock);
        int processed = run.step(world, 1_000_000, clock.now + 20_000L, clock);
        assertTrue(processed > 0 && processed < house.model().size(), "repartido: " + processed);
        assertTrue(clock.now <= 20_000L + 3_000L);
        assertEquals(10, run.step(world, 10, UNLIMITED, clock), "y el máximo de entradas");
    }
}
