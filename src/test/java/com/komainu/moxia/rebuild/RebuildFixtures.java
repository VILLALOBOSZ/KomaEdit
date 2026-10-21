package com.komainu.moxia.rebuild;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.LongSupplier;

/**
 * Obras y un mundo de mentira para las pruebas de la reconstrucción. Cada bloque es su texto
 * ({@code minecraft:oak_stairs[facing=north]}) y su id crudo es {@code 1000 + entrada de la paleta}; el aire es 0.
 */
final class RebuildFixtures {
    static final int AIR_RAW = 0;
    static final int RAW_OFFSET = 1000;

    private RebuildFixtures() {
    }

    /** Una obra: los bloques por posición, en el orden en que se añadieron. */
    static final class Build {
        final Map<Long, String> blocks = new LinkedHashMap<>();

        Build set(int x, int y, int z, String state) {
            blocks.put(PackedBlockPlan.pack(x, y, z), state.contains(":") ? state : "minecraft:" + state);
            return this;
        }

        Build fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        set(x, y, z, state);
                    }
                }
            }
            return this;
        }

        String at(int x, int y, int z) {
            return blocks.get(PackedBlockPlan.pack(x, y, z));
        }
    }

    /** Un modelo con su paleta en texto (la 0 es el aire). */
    record Fixture(RebuildModel model, List<String> palette) {
        String state(int entry) {
            return palette.get(model.target(entry));
        }

        String before(int entry) {
            return palette.get(model.before(entry));
        }
    }

    /** Reconstruir la obra tal como está (la selección): antes y después son lo mismo. */
    static Fixture selection(Build build) {
        return selection(build, null);
    }

    static Fixture selection(Build build, Map<Long, Long> history) {
        RebuildModel.Builder builder = RebuildModel.builder(true).airRaw(AIR_RAW);
        Palette palette = new Palette(builder);
        for (Map.Entry<Long, String> block : build.blocks.entrySet()) {
            int index = palette.of(block.getValue());
            long rank = history == null ? RebuildModel.NO_HISTORY
                    : history.getOrDefault(block.getKey(), RebuildModel.NO_HISTORY);
            builder.add(block.getKey(), index, index, rank);
        }
        return new Fixture(builder.build(), palette.names);
    }

    /** Pegar la obra sobre lo que hay en {@code ground} (el portapapeles): lo de antes es lo del mundo. */
    static Fixture paste(Build build, Build ground) {
        RebuildModel.Builder builder = RebuildModel.builder(false).airRaw(AIR_RAW);
        Palette palette = new Palette(builder);
        Map<Long, int[]> entries = new LinkedHashMap<>();
        for (Map.Entry<Long, String> block : build.blocks.entrySet()) {
            entries.put(block.getKey(), new int[]{palette.of(block.getValue()), RebuildModel.AIR});
        }
        for (Map.Entry<Long, String> block : ground.blocks.entrySet()) {
            entries.computeIfAbsent(block.getKey(), key -> new int[]{RebuildModel.AIR, RebuildModel.AIR})[1] =
                    palette.of(block.getValue());
        }
        for (Map.Entry<Long, int[]> entry : entries.entrySet()) {
            builder.add(entry.getKey(), entry.getValue()[0], entry.getValue()[1], RebuildModel.NO_HISTORY);
        }
        return new Fixture(builder.build(), palette.names);
    }

    private static final class Palette {
        private final RebuildModel.Builder builder;
        private final Object2IntOpenHashMap<String> indices = new Object2IntOpenHashMap<>();
        private final List<String> names = new ArrayList<>(List.of("minecraft:air"));

        private Palette(RebuildModel.Builder builder) {
            this.builder = builder;
            indices.defaultReturnValue(-1);
        }

        int of(String state) {
            BlockTraits traits = BlockTraits.guess(state);
            if (traits.air()) {
                return RebuildModel.AIR;
            }
            int known = indices.getInt(state);
            if (known >= 0) {
                return known;
            }
            int index = builder.palette(RAW_OFFSET + builder.paletteSize(), traits);
            indices.put(state, index);
            names.add(state);
            return index;
        }
    }

    /** Una casa de madera con su parcela, como las de un servidor: para el orden «como un constructor». */
    static Build house() {
        Build build = new Build();
        // La parcela: hierba con el suelo de la casa en piedra y un camino hasta la puerta.
        build.fill(0, 0, 0, 14, 0, 14, "grass_block");
        build.fill(3, 0, 4, 11, 0, 10, "cobblestone");
        build.fill(7, 0, 11, 7, 0, 14, "dirt_path");
        // Pilares de las esquinas y vigas de arriba.
        for (int[] corner : new int[][]{{3, 4}, {11, 4}, {3, 10}, {11, 10}}) {
            build.fill(corner[0], 1, corner[1], corner[0], 4, corner[1], "oak_log[axis=y]");
        }
        // Paredes con ventanas y la puerta, con una viga arriba.
        build.fill(4, 1, 4, 10, 3, 4, "oak_planks");
        build.fill(4, 1, 10, 10, 3, 10, "oak_planks");
        build.fill(3, 1, 5, 3, 3, 9, "oak_planks");
        build.fill(11, 1, 5, 11, 3, 9, "oak_planks");
        build.fill(4, 4, 4, 10, 4, 4, "stripped_spruce_log[axis=x]");
        build.fill(4, 4, 10, 10, 4, 10, "stripped_spruce_log[axis=x]");
        build.fill(3, 4, 5, 3, 4, 9, "stripped_spruce_log[axis=z]");
        build.fill(11, 4, 5, 11, 4, 9, "stripped_spruce_log[axis=z]");
        for (int x : new int[]{5, 9}) {
            build.fill(x, 2, 4, x, 3, 4, "glass_pane[east=true,west=true]");
            build.fill(x, 2, 10, x, 3, 10, "glass_pane[east=true,west=true]");
        }
        build.fill(3, 2, 7, 3, 3, 7, "glass_pane[north=true,south=true]");
        build.fill(11, 2, 7, 11, 3, 7, "glass_pane[north=true,south=true]");
        build.set(7, 1, 10, "oak_door[facing=south,half=lower]");
        build.set(7, 2, 10, "oak_door[facing=south,half=upper]");
        // Hastiales bajo el tejado.
        for (int x : new int[]{3, 11}) {
            build.fill(x, 5, 5, x, 5, 9, "oak_planks");
            build.fill(x, 6, 6, x, 6, 8, "oak_planks");
            build.set(x, 7, 7, "oak_planks");
        }
        // Tejado a dos aguas con alero y caballete.
        int[][] slopes = {{3, 4, 0}, {4, 5, 0}, {5, 6, 0}, {6, 7, 0}, {8, 7, 1}, {9, 6, 1}, {10, 5, 1}, {11, 4, 1}};
        for (int[] slope : slopes) {
            String facing = slope[2] == 0 ? "south" : "north";
            build.fill(2, slope[1], slope[0], 12, slope[1], slope[0], "spruce_stairs[facing=" + facing + "]");
        }
        build.fill(2, 8, 7, 12, 8, 7, "spruce_slab[type=bottom]");
        build.fill(2, 7, 7, 2, 7, 7, "spruce_slab[type=top]");
        build.fill(12, 7, 7, 12, 7, 7, "spruce_slab[type=top]");
        // Chimenea.
        build.fill(10, 1, 8, 10, 9, 8, "cobblestone");
        // Detalles: antorchas, farol, valla, muebles y alfombra.
        build.set(6, 2, 11, "wall_torch[facing=south]");
        build.set(8, 2, 11, "wall_torch[facing=south]");
        build.fill(1, 1, 13, 5, 1, 13, "oak_fence");
        build.fill(9, 1, 13, 13, 1, 13, "oak_fence");
        build.set(4, 1, 9, "chest[facing=south]");
        build.fill(6, 1, 6, 8, 1, 8, "red_carpet");
        build.set(7, 4, 7, "lantern[hanging=true]");
        // Vegetación: flores, hierba y un árbol en la esquina.
        SplittableRandom random = new SplittableRandom(7);
        String[] plants = {"poppy", "dandelion", "cornflower", "short_grass", "short_grass", "oxeye_daisy"};
        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 14; z++) {
                boolean house = x >= 2 && x <= 12 && z >= 3 && z <= 11;
                boolean path = x == 7 && z >= 11;
                boolean fence = z == 13 && (x >= 1 && x <= 5 || x >= 9 && x <= 13);
                if (!house && !path && !fence && random.nextInt(5) == 0) {
                    build.set(x, 1, z, plants[random.nextInt(plants.length)]);
                }
            }
        }
        build.fill(1, 1, 12, 1, 4, 12, "oak_log[axis=y]");
        for (int x = -1; x <= 3; x++) {
            for (int y = 3; y <= 6; y++) {
                for (int z = 10; z <= 14; z++) {
                    int dx = x - 1;
                    int dy = y - 4;
                    int dz = z - 12;
                    boolean trunk = x == 1 && z == 12 && y <= 4;
                    if (!trunk && dx * dx + dy * dy * 2 + dz * dz <= 5 && build.at(x, y, z) == null) {
                        build.set(x, y, z, "oak_leaves[persistent=true]");
                    }
                }
            }
        }
        return build;
    }

    /** Reloj que solo avanza cuando algo gasta (cada lectura o escritura del mundo), como en el resto de pruebas. */
    static final class Clock implements LongSupplier {
        long now;

        void spend(long nanos) {
            now += nanos;
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }

    /**
     * Un mundo que se porta como la sesión de WorldEdit: lo escrito se queda en un búfer hasta {@link #flush} y
     * hasta entonces leer la posición da lo de antes. Cada lectura cuesta {@code read} y cada escritura
     * {@code write} nanosegundos del reloj.
     */
    static final class World implements RebuildRun.World {
        final Long2IntOpenHashMap blocks = new Long2IntOpenHashMap();
        final Long2IntLinkedOpenHashMap buffer = new Long2IntLinkedOpenHashMap();
        final RebuildModel model;
        final Clock clock;
        long read = 1_000L;
        long write = 2_000L;
        int writes;

        World(RebuildModel model, Clock clock) {
            this.model = model;
            this.clock = clock;
        }

        /** Pone en el mundo lo que había antes de empezar. */
        World withBefore() {
            for (int entry = 0; entry < model.size(); entry++) {
                int before = model.before(entry);
                if (before != RebuildModel.AIR) {
                    blocks.put(model.position(entry), model.raw(before));
                }
            }
            return this;
        }

        World set(int x, int y, int z, int raw) {
            blocks.put(PackedBlockPlan.pack(x, y, z), raw);
            return this;
        }

        int at(long position) {
            return blocks.get(position);
        }

        @Override
        public int rawAt(long position) {
            clock.spend(read);
            return blocks.get(position);
        }

        @Override
        public boolean air(int raw) {
            return raw == AIR_RAW;
        }

        @Override
        public void write(long position, int palette) {
            clock.spend(write);
            writes++;
            buffer.put(position, palette == RebuildModel.AIR ? AIR_RAW : model.raw(palette));
        }

        void flush() {
            for (var entry : buffer.long2IntEntrySet()) {
                if (entry.getIntValue() == AIR_RAW) {
                    blocks.remove(entry.getLongKey());
                } else {
                    blocks.put(entry.getLongKey(), entry.getIntValue());
                }
            }
            buffer.clear();
        }

        /** Si el mundo tiene, en cada posición del modelo, lo de la obra. */
        boolean showsTarget() {
            for (int entry = 0; entry < model.size(); entry++) {
                int target = model.target(entry);
                int expected = target == RebuildModel.AIR ? AIR_RAW : model.raw(target);
                if (blocks.get(model.position(entry)) != expected) {
                    return false;
                }
            }
            return true;
        }

        /** Si el mundo tiene, en cada posición del modelo, lo de antes. */
        boolean showsBefore() {
            for (int entry = 0; entry < model.size(); entry++) {
                int before = model.before(entry);
                int expected = before == RebuildModel.AIR ? AIR_RAW : model.raw(before);
                if (blocks.get(model.position(entry)) != expected) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Una reconstrucción lista con su mundo y su reloj. */
    static final class Rig {
        final Fixture fixture;
        final Clock clock = new Clock();
        final World world;
        final RebuildRun run;

        Rig(Fixture fixture, RebuildOrder order, RebuildPace pace) {
            this(fixture, order, RebuildSequence.Options.DEFAULT, pace);
        }

        Rig(Fixture fixture, RebuildOrder order, RebuildSequence.Options options, RebuildPace pace) {
            this.fixture = fixture;
            this.world = new World(fixture.model(), clock).withBefore();
            RebuildSequence sequence = RebuildSequence.of(fixture.model(), order, options);
            this.run = new RebuildRun(fixture.model(), sequence, RebuildTimeline.of(fixture.model(), sequence, pace),
                    world, clock);
        }

        /** Un tick de servidor con {@code budget} nanosegundos: como el carril, varias ventanas hasta el plazo. */
        int tick(long budget) throws Exception {
            run.tick();
            long deadline = clock.now + budget;
            int total = 0;
            while (clock.now < deadline && run.hasWorkNow()) {
                int placed = run.place(4_096, deadline);
                world.flush();
                total += placed;
                if (placed == 0) {
                    break;
                }
            }
            return total;
        }

        /** Ticks sin límite de presupuesto hasta que acabe (o {@code max} ticks). */
        int runToEnd(int max) throws Exception {
            int ticks = 0;
            while (!run.done() && ticks < max) {
                tick(Long.MAX_VALUE / 4);
                ticks++;
            }
            return ticks;
        }
    }
}
