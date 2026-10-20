package com.komainu.moxia.studio.ui;

import com.komainu.moxia.blueprint.BlueprintThumbnail;
import com.komainu.moxia.color.BlockColorCatalog;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.gradient.PaletteSpec;
import com.komainu.moxia.nature.Bounds;
import com.komainu.moxia.nature.NatureTerrain;
import com.komainu.moxia.nature.NatureVoxels;
import com.komainu.moxia.nature.RockSettings;
import com.komainu.moxia.nature.RockShaper;
import com.komainu.moxia.nature.TreeGrower;
import com.komainu.moxia.nature.TreeSettings;
import com.komainu.moxia.nature.TreeSpecies;
import com.komainu.moxia.roof.Footprint;
import com.komainu.moxia.roof.RoofGenerator;
import com.komainu.moxia.roof.RoofResult;
import com.komainu.moxia.roof.RoofSettings;
import com.komainu.moxia.roof.RoofWorld;
import com.komainu.moxia.studio.tools.ui.StampPreview;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Miniaturas de las opciones que se eligen viéndolas: cada especie de árbol, cada tipo de tejado y de roca y cada
 * sello de relieve. Salen de los mismos generadores que construyen (un árbol de verdad sobre suelo llano, el tejado
 * de una casa de 9 × 7) pintados con la miniatura isométrica de los planos, así que enseñan lo que se va a obtener.
 *
 * <p>Se calculan una vez, en segundo plano: mientras tanto la casilla enseña su icono. Las pruebas y las maquetas las
 * piden ya hechas con {@link #setBlocking}.</p>
 */
public final class ChoiceThumbnails {
    /** Lado en píxeles de cada miniatura. */
    public static final int SIZE = 96;
    private static final Map<String, StudioImage> READY = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit studio thumbnails");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final int FOLIAGE = 0x6AA136;
    private static final int SPRUCE = 0x5E8F5E;
    private static final int BIRCH = 0x80A755;
    /** Colores del mundo que no salen de la tabla de bloques: el suelo bajo árboles y rocas y el cristal. */
    private static final int DIRT = 0x7A5A3A;
    private static final int GRASS = 0x6E9A3E;
    private static final int GLASS = 0xA9C9D9;
    /** Un bloque sin color medido. */
    private static final int UNMEASURED = 0x8A8A8A;
    private static final Set<String> TINTED = Set.of("minecraft:oak_leaves", "minecraft:jungle_leaves",
            "minecraft:acacia_leaves", "minecraft:dark_oak_leaves", "minecraft:mangrove_leaves", "minecraft:vine");
    private static volatile boolean blocking;

    private ChoiceThumbnails() {
    }

    /** Con {@code true} las miniaturas se calculan al pedirlas (pruebas y maquetas). */
    public static void setBlocking(boolean value) {
        blocking = value;
    }

    /** Si la opción tiene miniatura: árboles, tejados, rocas y sellos. */
    public static boolean supports(String card, String param) {
        return switch (card + "/" + param) {
            case "tree/species", "roof/type", "rock/type", "stamp/stamp" -> true;
            default -> false;
        };
    }

    /** La miniatura de una opción, o {@code null} si no tiene o aún se está pintando. */
    public static StudioImage get(String card, String param, String option) {
        if (!supports(card, param)) {
            return null;
        }
        String key = "choice/" + card + "/" + param + "/" + option;
        StudioImage ready = READY.get(key);
        if (ready != null || PENDING.contains(key)) {
            return ready;
        }
        if (blocking) {
            return READY.computeIfAbsent(key, ignored -> render(key, card, option));
        }
        PENDING.add(key);
        WORKER.execute(() -> {
            try {
                READY.put(key, render(key, card, option));
            } catch (RuntimeException exception) {
                // Una opción que no se puede pintar se queda con su icono.
            } finally {
                PENDING.remove(key);
            }
        });
        return null;
    }

    private static StudioImage render(String key, String card, String option) {
        int[] pixels = switch (card) {
            case "tree" -> voxels(tree(option));
            case "roof" -> voxels(roof(option));
            case "rock" -> voxels(rock(option));
            case "stamp" -> StampPreview.argb(option, SIZE);
            default -> new int[0];
        };
        return StudioImage.of(key, SIZE, SIZE, () -> pixels);
    }

    private static int[] voxels(BlueprintThumbnail.Grid grid) {
        BufferedImage image = BlueprintThumbnail.render(grid, SIZE);
        return image.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE);
    }

    // ── Árboles ─────────────────────────────────────────────────────────────────────────────────────────

    private static BlueprintThumbnail.Grid tree(String species) {
        TreeSettings settings = TreeSettings.parse(species).withSeed(7L);
        NatureTerrain flat = (x, y, z) -> y < 0 ? NatureTerrain.Kind.SOIL : NatureTerrain.Kind.FREE;
        int reach = TreeGrower.reach(settings);
        Bounds bounds = Bounds.around(0, 0, 0, reach, 4, settings.height() + 16);
        NatureVoxels voxels = TreeGrower.grow(settings, flat, bounds, 0, 0, 0, 7L, 400_000, () -> false);
        TreeSpecies kind = TreeSpecies.byName(species);
        int wood = color(first(settings.trunkMaterial(), kind.trunk()));
        String leafId = first(settings.leafMaterial(), kind.leaves());
        int leaves = foliage(leafId);
        Box box = Box.of(voxels.sortedKeys());
        BlueprintThumbnail.Grid grid = box.grid(1);
        for (long key : voxels.sortedKeys()) {
            NatureVoxels.Role role = NatureVoxels.role(voxels.info(key));
            int rgb = role.foliage() ? shade(leaves, NatureVoxels.shade(voxels.info(key))) : wood;
            box.set(grid, key, rgb, 1);
        }
        box.ground(grid, DIRT);
        return grid;
    }

    private static String first(String palette, String fallback) {
        if (palette == null || palette.isBlank()) {
            return id(fallback);
        }
        List<String> parsed = PaletteSpec.parse(palette);
        return parsed.isEmpty() ? id(fallback) : id(parsed.getFirst());
    }

    private static int foliage(String id) {
        int base = color(id);
        if (TINTED.contains(id)) {
            return multiply(base, FOLIAGE);
        }
        if (id.equals("minecraft:spruce_leaves")) {
            return multiply(base, SPRUCE);
        }
        if (id.equals("minecraft:birch_leaves")) {
            return multiply(base, BIRCH);
        }
        return base;
    }

    // ── Rocas ───────────────────────────────────────────────────────────────────────────────────────────

    private static BlueprintThumbnail.Grid rock(String type) {
        RockSettings settings = RockSettings.parse(type).withSeed(5L);
        NatureTerrain flat = (x, y, z) -> y < 0 ? NatureTerrain.Kind.SOIL : NatureTerrain.Kind.FREE;
        int reach = RockShaper.reach(settings);
        Bounds bounds = Bounds.around(0, 0, 0, reach, 8, (int) Math.ceil(RockShaper.height(settings)) + 8);
        NatureVoxels voxels = RockShaper.shape(settings, flat, bounds, 0, 0, 0, 5L, 400_000, () -> false);
        List<String> palette = PaletteSpec.parse(settings.paletteMaterial());
        int moss = color(id(settings.moss() == null ? "moss_block" : settings.moss()));
        Box box = Box.of(voxels.sortedKeys());
        BlueprintThumbnail.Grid grid = box.grid(1);
        for (long key : voxels.sortedKeys()) {
            int info = voxels.info(key);
            int rgb = NatureVoxels.role(info) == NatureVoxels.Role.MOSS ? moss
                    : color(id(palette.get(Math.min(palette.size() - 1, NatureVoxels.shade(info) * palette.size()
                    / 256))));
            box.set(grid, key, rgb, 1);
        }
        box.ground(grid, GRASS);
        return grid;
    }

    // ── Tejados ─────────────────────────────────────────────────────────────────────────────────────────

    private static BlueprintThumbnail.Grid roof(String type) {
        boolean round = type.equals("dome") || type.equals("cone") || type.equals("spire");
        Footprint footprint = round ? Footprint.rectangle(0, 0, 6, 6, 0) : Footprint.rectangle(0, 0, 8, 6, 0);
        RoofResult result = RoofGenerator.generate(footprint, RoofSettings.parse(type + " spruce_planks"),
                RoofWorld.AIR);
        int wallHeight = 4;
        long[] positions = result.positions();
        Box box = Box.of(positions).include(footprint.minX(), -wallHeight, footprint.minZ())
                .include(footprint.maxX(), -1, footprint.maxZ());
        BlueprintThumbnail.Grid grid = box.grid(0);
        int roofColor = color("minecraft:spruce_planks");
        int ridge = darker(roofColor, 0.82);
        int gable = color("minecraft:oak_planks");
        for (int index = 0; index < positions.length; index++) {
            byte material = result.materials()[index];
            int rgb = switch (material) {
                case RoofResult.RIDGE -> ridge;
                case RoofResult.WALL -> gable;
                case RoofResult.WINDOW_X, RoofResult.WINDOW_Z -> GLASS;
                default -> roofColor;
            };
            box.set(grid, positions[index], rgb, 0);
        }
        int wall = color("minecraft:white_terracotta");
        int beam = color("minecraft:stripped_spruce_log");
        for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
            for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                if (!footprint.isBoundary(x, z)) {
                    continue;
                }
                boolean corner = (x == footprint.minX() || x == footprint.maxX())
                        && (z == footprint.minZ() || z == footprint.maxZ());
                for (int y = -wallHeight; y < 0; y++) {
                    boolean window = !corner && y == -2 && (x + z) % 3 == 0;
                    box.set(grid, x, y, z, corner ? beam : window ? GLASS : wall);
                }
            }
        }
        return grid;
    }

    // ── Colores ─────────────────────────────────────────────────────────────────────────────────────────

    private static String id(String value) {
        String trimmed = value.strip();
        int bracket = trimmed.indexOf('[');
        trimmed = bracket > 0 ? trimmed.substring(0, bracket) : trimmed;
        int percent = trimmed.indexOf('%');
        trimmed = percent >= 0 ? trimmed.substring(percent + 1) : trimmed;
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    /** Color medio de la textura del bloque (la tabla de colores medidos), o gris si no está. */
    static int color(String id) {
        BlockColorCatalog.Measured measured = BlockColorCatalog.measured().get(id);
        return measured == null ? UNMEASURED : measured.rgb() & 0xFFFFFF;
    }

    private static int multiply(int base, int tint) {
        int red = ((base >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int green = ((base >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int blue = (base & 0xFF) * (tint & 0xFF) / 255;
        return red << 16 | green << 8 | blue;
    }

    private static int darker(int rgb, double factor) {
        int red = (int) (((rgb >> 16) & 0xFF) * factor);
        int green = (int) (((rgb >> 8) & 0xFF) * factor);
        int blue = (int) ((rgb & 0xFF) * factor);
        return red << 16 | green << 8 | blue;
    }

    /** Las hojas más hondas, un poco más oscuras: la copa se lee con volumen. */
    private static int shade(int rgb, int shade) {
        return darker(rgb, 0.78 + 0.22 * Math.clamp(shade / 255.0, 0.0, 1.0));
    }

    /** La caja que ocupan unas posiciones empaquetadas, para pasarlas a una rejilla que empieza en cero. */
    private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Box of(long[] keys) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (long key : keys) {
                int x = PackedBlockPlan.unpackX(key);
                int y = PackedBlockPlan.unpackY(key);
                int z = PackedBlockPlan.unpackZ(key);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
            return keys.length == 0 ? new Box(0, 0, 0, 0, 0, 0) : new Box(minX, minY, minZ, maxX, maxY, maxZ);
        }

        Box include(int x, int y, int z) {
            return new Box(Math.min(minX, x), Math.min(minY, y), Math.min(minZ, z), Math.max(maxX, x),
                    Math.max(maxY, y), Math.max(maxZ, z));
        }

        /** Rejilla con {@code ground} capas de suelo debajo. */
        BlueprintThumbnail.Grid grid(int ground) {
            return new BlueprintThumbnail.Grid(maxX - minX + 1, maxY - minY + 1 + ground, maxZ - minZ + 1);
        }

        void set(BlueprintThumbnail.Grid grid, long key, int rgb, int ground) {
            set(grid, PackedBlockPlan.unpackX(key), PackedBlockPlan.unpackY(key) + ground,
                    PackedBlockPlan.unpackZ(key), rgb);
        }

        void set(BlueprintThumbnail.Grid grid, int x, int y, int z, int rgb) {
            int gx = x - minX;
            int gy = y - minY;
            int gz = z - minZ;
            if (gx >= 0 && gy >= 0 && gz >= 0 && gx < grid.sizeX() && gy < grid.sizeY() && gz < grid.sizeZ()) {
                grid.set(gx, gy, gz, rgb);
            }
        }

        /** Una capa de suelo bajo lo que toca el fondo, para que el árbol o la roca no floten. */
        void ground(BlueprintThumbnail.Grid grid, int rgb) {
            for (int x = 0; x < grid.sizeX(); x++) {
                for (int z = 0; z < grid.sizeZ(); z++) {
                    if (grid.color(x, 1, z) >= 0 || grid.color(x, 2, z) >= 0) {
                        grid.set(x, 0, z, rgb);
                    }
                }
            }
        }
    }
}
