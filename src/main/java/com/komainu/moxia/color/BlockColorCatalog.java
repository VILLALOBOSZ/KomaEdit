package com.komainu.moxia.color;

import com.komainu.moxia.shape.smooth.SmoothMaterials;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Todos los bloques con color conocido, en OKLab, y las búsquedas por color: los más parecidos a un color y la
 * lista de candidatos con un filtro. Es inmutable y Java puro; en el juego lo monta
 * {@link BlockColorCatalogs#get()} a partir del registro, y en los tests se monta a mano.
 *
 * <p>Los colores medidos salen de la tabla de texturas de smoothblocks ({@code assets/komaedit/smooth/colors.txt},
 * generada con {@code tools/smooth_colors.py}); los bloques que no están en ella (los de mods) usan su color de
 * mapa.</p>
 */
public final class BlockColorCatalog {
    /** Bloques técnicos o trampas que nunca se proponen aunque tengan objeto y color. */
    private static final Set<String> HIDDEN = Set.of(
            "minecraft:barrier", "minecraft:light", "minecraft:structure_void", "minecraft:structure_block",
            "minecraft:jigsaw", "minecraft:command_block", "minecraft:chain_command_block",
            "minecraft:repeating_command_block", "minecraft:petrified_oak_slab"
    );
    private static volatile Map<String, Measured> measured;

    private final List<BlockColor> blocks;
    private final Map<String, BlockColor> byId;

    private BlockColorCatalog(List<BlockColor> blocks) {
        this.blocks = List.copyOf(blocks);
        Map<String, BlockColor> map = new LinkedHashMap<>();
        for (BlockColor block : this.blocks) {
            map.put(block.id(), block);
        }
        this.byId = Map.copyOf(map);
    }

    public static BlockColorCatalog of(Collection<BlockColor> blocks) {
        List<BlockColor> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparing(BlockColor::id));
        return new BlockColorCatalog(sorted);
    }

    /**
     * Lo medido de un bloque vanilla en la tabla de texturas.
     *
     * @param uniform   la misma textura en las seis caras
     * @param patterned mena, esmaltada, máquina, bloque de función o textura con mucho dibujo
     */
    public record Measured(int rgb, boolean uniform, boolean patterned) {
    }

    /** Tabla de colores medidos de las texturas vanilla, {@code id → color y banderas}. */
    public static Map<String, Measured> measured() {
        Map<String, Measured> table = measured;
        if (table == null) {
            try (InputStream stream = BlockColorCatalog.class.getResourceAsStream(SmoothMaterials.COLORS)) {
                table = stream == null ? Map.of() : read(stream);
            } catch (IOException exception) {
                throw new IllegalStateException("No se pudo leer la tabla de colores de los bloques", exception);
            }
            measured = table;
        }
        return table;
    }

    static Map<String, Measured> read(InputStream stream) throws IOException {
        Map<String, Measured> result = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 4 && !parts[0].startsWith("#")) {
                int rgb = (Integer.parseInt(parts[1]) & 0xFF) << 16 | (Integer.parseInt(parts[2]) & 0xFF) << 8
                        | Integer.parseInt(parts[3]) & 0xFF;
                boolean uniform = parts.length > 4 && parts[4].indexOf('u') >= 0;
                boolean patterned = parts.length > 4 && parts[4].indexOf('p') >= 0;
                result.put(parts[0], new Measured(rgb, uniform, patterned));
            }
        }
        return Map.copyOf(result);
    }

    public int size() {
        return blocks.size();
    }

    public List<BlockColor> all() {
        return blocks;
    }

    /** El bloque, con o sin {@code minecraft:}; {@code null} si no tiene color conocido. */
    public BlockColor get(String id) {
        return byId.get(normalize(id));
    }

    public BlockColor require(String id) {
        BlockColor block = get(id);
        if (block == null) {
            throw new IllegalArgumentException("No conozco el color de «" + id + "»: escribe un bloque con textura"
                    + " o un color como #3A7FD2");
        }
        return block;
    }

    /** ¿Es un bloque entero con color conocido? */
    public boolean fullBlock(String id) {
        BlockColor block = get(id);
        return block != null && block.has(BlockColor.CUBE);
    }

    /** El material entero de una pieza ({@code stone_brick_stairs} → {@code stone_bricks}). */
    public String base(String id) {
        return ShapeFamilies.base(normalize(id), this::fullBlock);
    }

    /** Se puede proponer: no es un bloque técnico ni una trampa (piedra infestada). */
    public static boolean pickable(String id) {
        return !HIDDEN.contains(id) && !id.contains(":infested_");
    }

    /**
     * Bloques que pasan el filtro, sin repetir aspecto: de los que tienen exactamente el mismo color (la losa, la
     * escalera y la valla de los tablones de roble) se queda uno, el que ocupa el bloque entero y, a igualdad, el
     * de nombre más corto.
     */
    public List<BlockColor> candidates(ColorFilter filter) {
        Map<Integer, BlockColor> byColor = new LinkedHashMap<>();
        for (BlockColor block : blocks) {
            if (!filter.test(block) || !pickable(block.id())) {
                continue;
            }
            byColor.merge(block.rgb(), block, BlockColorCatalog::representative);
        }
        return List.copyOf(byColor.values());
    }

    /** Coincidencia de una búsqueda: el bloque y su distancia OKLab al color buscado. */
    public record Match(BlockColor block, double distance) {
    }

    /**
     * Los {@code count} bloques de color más parecido, del más cercano al más lejano.
     *
     * @param exclude identificadores que no se devuelven (por ejemplo, el propio bloque de referencia)
     */
    public List<Match> similar(double[] lab, int count, ColorFilter filter, Set<String> exclude) {
        Set<Integer> excludedColors = new HashSet<>();
        for (String id : exclude) {
            BlockColor block = get(id);
            if (block != null) {
                excludedColors.add(block.rgb());
            }
        }
        List<Match> matches = new ArrayList<>();
        for (BlockColor block : candidates(filter)) {
            if (exclude.contains(block.id()) || excludedColors.contains(block.rgb())) {
                continue;
            }
            matches.add(new Match(block, OkLab.distance(lab, block.lab())));
        }
        matches.sort(Comparator.comparingDouble(Match::distance).thenComparing(match -> match.block().id()));
        return List.copyOf(matches.subList(0, Math.min(Math.max(0, count), matches.size())));
    }

    /** Ordena de oscuro a claro; a igual luminosidad, por nombre para que el orden sea estable. */
    public static List<BlockColor> byLightness(Collection<BlockColor> blocks) {
        List<BlockColor> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingDouble(BlockColor::l).thenComparing(BlockColor::id));
        return sorted;
    }

    static String normalize(String id) {
        String text = id.trim().toLowerCase(Locale.ROOT);
        return text.indexOf(':') < 0 ? "minecraft:" + text : text;
    }

    private static BlockColor representative(BlockColor first, BlockColor second) {
        boolean firstCube = first.has(BlockColor.CUBE);
        if (firstCube != second.has(BlockColor.CUBE)) {
            return firstCube ? first : second;
        }
        if (first.id().length() != second.id().length()) {
            return first.id().length() < second.id().length() ? first : second;
        }
        return first.id().compareTo(second.id()) <= 0 ? first : second;
    }
}
