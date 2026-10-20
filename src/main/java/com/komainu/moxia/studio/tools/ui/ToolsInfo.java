package com.komainu.moxia.studio.tools.ui;

import java.util.List;
import java.util.Map;

/**
 * Lo que la etapa Herramientas sabe del servidor y del cliente aparte de los permisos: fuentes propias, presets
 * guardados, simetría, capacidades, biomas del registro y la última lista de materiales. Todo puede faltar.
 *
 * @param selection tamaño de la selección de WorldEdit (x, y, z), o vacío
 */
public record ToolsInfo(
        boolean known,
        int[] selection,
        List<String> fonts,
        Map<String, String> treePresets,
        Map<String, String> rockPresets,
        Symmetry symmetry,
        Capabilities capabilities,
        List<String> biomes,
        Materials materials
) {
    public static final ToolsInfo EMPTY = new ToolsInfo(false, new int[0], List.of(), Map.of(), Map.of(),
            Symmetry.OFF, null, List.of(), null);

    public ToolsInfo {
        selection = selection == null ? new int[0] : selection.clone();
        fonts = List.copyOf(fonts);
        treePresets = Map.copyOf(treePresets);
        rockPresets = Map.copyOf(rockPresets);
        symmetry = symmetry == null ? Symmetry.OFF : symmetry;
        biomes = List.copyOf(biomes);
    }

    public boolean hasSelection() {
        return selection.length == 3;
    }

    /** Simetría del servidor: modo como lo escribe el chat ({@code radial 8 y}) y centro descrito. */
    public record Symmetry(boolean active, String mode, int arms, String center, boolean worldEdit,
                           boolean elsewhere) {
        public static final Symmetry OFF = new Symmetry(false, "x", 4, "", false, false);
    }

    /**
     * Capacidades como las manda el servidor al menú rápido. Máscaras con el bit de cada
     * {@code BuildCapability}.
     */
    public record Capabilities(int enabled, int permitted, boolean modeAllowed, boolean flySpeedAllowed,
                               boolean replaceFamily, boolean extrudeByState, int extrudeLimit, int angelDistance,
                               int reachDistance, int flySpeed, int maxReach, int maxLayerBlocks, int maxFlySpeed) {
    }

    /** Resultado de la última lista de materiales. */
    public record Materials(long blocks, int types, long items, long slots, long chests, List<Line> lines, int hidden,
                            String clipboard) {
        public Materials {
            lines = List.copyOf(lines);
        }

        public record Line(String item, long amount, int maxStack) {
            public long stacks() {
                return maxStack <= 0 ? 0 : amount / maxStack;
            }

            public long remainder() {
                return maxStack <= 0 ? amount : amount % maxStack;
            }
        }
    }
}
