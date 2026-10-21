package com.komainu.moxia.brush.paint;

import com.komainu.moxia.brush.ez.BlockFilter;
import com.komainu.moxia.brush.ez.BrushMask;
import com.komainu.moxia.brush.ez.BrushMasks;
import com.komainu.moxia.brush.ez.BrushNoise;
import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.noise.SavedNoises;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.komainu.moxia.terrain.TerrainService;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * El pincel de pintura de un jugador ya configurado: la paleta resuelta, la huella medida, las máscaras y los ruidos
 * hechos. Todo eso se prepara una vez en el hilo del servidor al elegir el pincel (o al cambiarlo desde el Studio):
 * cada clic solo calcula. La semilla y la cuenta de toques sí cambian con el uso.
 */
public final class PaintBrush {
    /** Semillas que se enseñan y se escriben: de 0 a 999 999 999, como en el Studio. */
    public static final int SEEDS = 1_000_000_000;
    private static final long JITTER_SALT = 0x4A49_5454L;
    /** Los sellos vienen del catálogo del servidor (los de serie y los PNG de la carpeta). */
    private static final FootprintLibrary SERVER_FOOTPRINTS = FootprintLibrary.of(new FootprintLibrary.StampSource() {
        @Override
        public List<String> names() {
            return TerrainService.catalog().names();
        }

        @Override
        public com.komainu.moxia.terrain.HeightStamp load(String name) throws IOException {
            return TerrainService.catalog().load(name);
        }
    });

    private final PaintConfig config;
    private final PaintPalette palette;
    private final PaintFootprint footprint;
    private final BrushMask blocks;
    private final BrushMask fixedMask;
    private final NoiseSpec noise;
    private final NoiseSpec jitter;
    private long seed;
    private boolean locked;
    private long counter;
    private BrushNoise noiseField;
    private BrushNoise jitterField;

    private PaintBrush(PaintConfig config, PaintPalette palette, PaintFootprint footprint, BrushMask blocks,
                       BrushMask fixedMask, NoiseSpec noise, NoiseSpec jitter, long seed) {
        this.config = config;
        this.palette = palette;
        this.footprint = footprint;
        this.blocks = blocks;
        this.fixedMask = fixedMask;
        this.noise = noise;
        this.jitter = jitter;
        this.locked = config.seedLocked();
        seed(seed);
    }

    /** Lee las opciones y lo prepara todo; lanza {@link IllegalArgumentException} con lo que esté mal escrito. */
    public static PaintBrush create(ServerPlayerEntity player, BrushOptions options) {
        return create(player, options, false);
    }

    /** Igual, para pintar la selección ({@code /komaedit pintar}): sin radio ({@link PaintConfig#parse}). */
    public static PaintBrush create(ServerPlayerEntity player, BrushOptions options, boolean region) {
        PaintConfig config = PaintConfig.parse(options, region);
        PaintPalette palette = PaintPalette.resolve(player, config.palette());
        PaintFootprint footprint = footprints().get(config.footprint());
        BrushMask blocks = config.blocks() == null ? null : BlockFilter.parse(config.blocks());
        // Que una máscara mal escrita falle al elegir el pincel y no en el primer clic.
        BrushMask fixedMask = null;
        if (config.mask() != null) {
            BrushMasks.parse(player, config.mask());
            fixedMask = BrushMasks.fixed(config.mask());
        }
        NoiseSpec noise = config.noise() != null ? SavedNoises.parse(player.getUuid(), config.noise())
                : PaintConfig.defaultNoise(config.noiseSize());
        long seed = config.seed() != null ? config.seed() : randomSeed();
        return new PaintBrush(config, palette, footprint, blocks, fixedMask, noise,
                PaintConfig.jitterNoise(config.radius()), seed);
    }

    /** La biblioteca de huellas del servidor. */
    public static FootprintLibrary footprints() {
        return SERVER_FOOTPRINTS;
    }

    static long randomSeed() {
        return ThreadLocalRandom.current().nextInt(SEEDS);
    }

    /**
     * Al cambiar los ajustes desde el Studio sin semilla escrita, el pincel nuevo sigue con la semilla y la cuenta
     * del anterior: el dibujo no cambia por mover otro ajuste.
     */
    public PaintBrush adopt(PaintBrush previous) {
        if (previous != null && config.seed() == null) {
            seed(previous.seed);
            counter = previous.counter;
        }
        return this;
    }

    public PaintConfig config() {
        return config;
    }

    public PaintPalette palette() {
        return palette;
    }

    public PaintFootprint footprint() {
        return footprint;
    }

    public long seed() {
        return seed;
    }

    public boolean locked() {
        return locked;
    }

    /** 🎲: una semilla nueva al azar. */
    public long reroll() {
        long next = randomSeed();
        if (next == seed) {
            next = (next + 1) % SEEDS;
        }
        seed(next);
        return next;
    }

    /** Bloquea o libera la semilla; devuelve cómo queda. */
    public boolean toggleLock() {
        locked = !locked;
        return locked;
    }

    private void seed(long value) {
        seed = value;
        BrushNoise field = BrushNoise.of(noise, 1.0, value);
        // El de serie se estira; uno escrito con -n se usa tal cual.
        noiseField = config.noise() == null ? PaintConfig.contrast(field) : field;
        jitterField = PaintConfig.contrast(BrushNoise.of(jitter, 1.0, value ^ JITTER_SALT));
    }

    /** Un trazo nuevo con la semilla de ahora (libre: sigue la cuenta de toques del pincel). */
    public PaintStroke stroke() {
        return new PaintStroke(config.radius(), config.spacing(), seed, locked, counter);
    }

    /** Lo que avanzó la cuenta de toques en un trazo con la semilla libre. */
    public void advance(PaintStroke stroke) {
        if (!locked) {
            counter = Math.max(counter, stroke.counter());
        }
    }

    /** Bloques por encima y por debajo del clic que mira cada toque. */
    public int vertical() {
        return Math.clamp(config.radius() + 4, 8, 28);
    }

    /** Los ajustes del pintor; {@code heightLow} {@code NaN} para medir la franja de alturas en este trazo. */
    public PaintPainter.Settings settings(double heightLow, double heightHigh, boolean invert) {
        return settings(heightLow, heightHigh, invert, footprint, config.hardness(), vertical());
    }

    /** Los ajustes del pintor con otra huella, dureza y alcance vertical (la selección entera es un toque). */
    public PaintPainter.Settings settings(double heightLow, double heightHigh, boolean invert,
                                          PaintFootprint footprint, double hardness, int vertical) {
        return new PaintPainter.Settings(palette.size(), config.gradient(), footprint, config.strength(),
                hardness, config.jitter(), config.layers(), config.surfaceOnly(), config.shaped(),
                vertical, heightLow, heightHigh, config.slopeLow(), config.slopeHigh(), noiseField, jitterField,
                invert);
    }

    /** La {@code -m} de este trazo: la ya leída o, si es de WorldEdit, leída ahora (en el hilo del servidor). */
    public BrushMask mask(ServerPlayerEntity player) {
        return config.mask() == null ? null : BrushMasks.forClick(fixedMask, player, config.mask());
    }

    /** Lo que pinta una pasada sobre la foto: cada bloque que cambia con lo que había y lo que pone. */
    public Planned plan(WorldSnapshot snapshot, List<PaintPainter.Dab> dabs, BrushMask mask,
                        PaintPainter.Settings settings, PaintPainter.Memory memory, Runnable checkCancelled) {
        if (mask != null) {
            mask.bind(snapshot);
        }
        boolean natural = config.blocks() == null && config.mask() == null && !config.everything();
        SnapshotPaintWorld world = new SnapshotPaintWorld(snapshot, palette, blocks, mask, natural);
        Planned.Builder builder = new Planned.Builder();
        PaintPainter.Result result = PaintPainter.paint(world, dabs, settings, memory, (x, y, z, index) -> {
            BlockState existing = world.state(x, y, z);
            BlockState next = palette.place(index, existing);
            if (next != null && !next.equals(existing)) {
                builder.add(x, y, z, existing, next);
            }
        }, checkCancelled);
        return builder.build(result.heightLow(), result.heightHigh());
    }

    /** Los controles, para el chat. */
    public String controls() {
        return "clic derecho (o mantenerlo y arrastrar): pinta " + config.describe() + " · clic izquierdo: con la"
                + " paleta al revés · F: semilla nueva · Mayús+F: " + (locked ? "libera" : "bloquea")
                + " la semilla (" + seed + ")";
    }

    /**
     * Lo calculado de una pasada: posiciones, lo que había (para no pisar lo que cambie mientras tanto) y lo que se
     * pone, más la franja de alturas que se usó.
     */
    public record Planned(long[] positions, BlockState[] expected, BlockState[] states, double heightLow,
                          double heightHigh) {
        public int size() {
            return positions.length;
        }

        static final class Builder {
            private long[] positions = new long[256];
            private BlockState[] expected = new BlockState[256];
            private BlockState[] states = new BlockState[256];
            private int size;

            void add(int x, int y, int z, BlockState before, BlockState after) {
                if (size == positions.length) {
                    int grown = positions.length * 2;
                    positions = java.util.Arrays.copyOf(positions, grown);
                    expected = java.util.Arrays.copyOf(expected, grown);
                    states = java.util.Arrays.copyOf(states, grown);
                }
                positions[size] = PaintPainter.pack(x, y, z);
                expected[size] = before;
                states[size] = after;
                size++;
            }

            Planned build(double heightLow, double heightHigh) {
                return new Planned(java.util.Arrays.copyOf(positions, size), java.util.Arrays.copyOf(expected, size),
                        java.util.Arrays.copyOf(states, size), heightLow, heightHigh);
            }
        }
    }
}
