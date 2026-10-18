package com.komainu.moxia.color;

import com.komainu.moxia.region.RegionEdits;
import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.RegionOperation;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.texture.ShadeField;
import com.komainu.moxia.texture.TextureCommandParser;
import com.komainu.moxia.texture.TextureContext;
import com.komainu.moxia.texture.TextureOperation;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@code /komaedit shade}: recolorea la superficie de la selección según la luz con una paleta de oscuro a claro.
 * Corre en el worker sobre la foto; la luz, la máscara y la paleta escrita ya vienen resueltas del hilo del servidor.
 *
 * <p>Solo cambian los bloques enteros sólidos y las piezas con forma (escaleras, losas, muros…), que conservan su
 * forma y pasan a la pieza del material nuevo; si ese material no tiene la pieza, toman la del más cercano de la
 * paleta que sí la tenga, y si ninguno la tiene se quedan. El cristal, las hojas, las flores o las antorchas no se
 * tocan. El reparto por la paleta y el tramado son los de {@link TextureOperation}.</p>
 */
public final class ShadeOperation implements RegionOperation {
    private final RegionMasks.Bound mask;
    private final List<Pattern> palette;
    private final double pivot;
    private final boolean forceRamp;
    private final int rampSize;
    private final ShadeLight light;
    private final Shading shading;
    private final BlockColorCatalog catalog;
    private final TextureContext.Settings settings;
    private final Consumer<String> notice;

    /** Fuerzas del sombreado: oclusión ambiental y su alcance, sombras proyectadas y tramado, de 0 a 1. */
    public record Shading(double occlusion, int occlusionRadius, double shadows, double dither) {
    }

    /**
     * @param palette paleta ya ordenada de oscuro a claro, o {@code null} para sacarla de la selección
     * @param pivot   sitio del material base si la paleta escrita es una rampa ({@code NaN} si no)
     * @param notice  avisos para el jugador (se llama desde el worker)
     */
    public ShadeOperation(RegionMasks.Bound mask, List<Pattern> palette, double pivot, boolean forceRamp,
                          int rampSize, ShadeLight light, Shading shading, BlockColorCatalog catalog,
                          TextureContext.Settings settings, Consumer<String> notice) {
        this.mask = mask;
        this.palette = palette == null ? null : List.copyOf(palette);
        this.pivot = pivot;
        this.forceRamp = forceRamp;
        this.rampSize = rampSize;
        this.light = light;
        this.shading = shading;
        this.catalog = catalog;
        this.settings = settings;
        this.notice = notice;
    }

    /** Margen de lectura: el que pide la luz (oclusión y normales). */
    public int padding() {
        return field(TextureContext.Filter.ALL).padding();
    }

    @Override
    public void apply(RegionSnapshot snapshot, RegionEdits edits) {
        mask.bind(snapshot);
        List<Pattern> chosen = palette;
        double median = pivot;
        TextureContext.Filter target = (x, y, z) -> recolorable(snapshot, x, y, z);
        if (chosen == null) {
            ShadePalette fromSelection = ShadePalette.fromCounts(count(snapshot, catalog, mask::test,
                    edits::checkCancelled), catalog, rampSize, forceRamp);
            chosen = patterns(fromSelection.ids());
            median = fromSelection.pivot();
            target = (x, y, z) -> recolorable(snapshot, x, y, z)
                    && fromSelection.targets().contains(catalog.base(snapshot.blockId(x, y, z)));
            notice.accept((fromSelection.ramp() ? "rampa de " : "paleta de la selección: ")
                    + fromSelection.describe() + " (de oscuro a claro)");
        }
        TextureCommandParser.Request request = new TextureCommandParser.Request(TextureCommandParser.Mode.ADVANCED,
                mask.source(), "", field(target).withPivot(median), true, shading.dither(), 0.0, false, 0,
                settings.seed());
        new TextureOperation(request, mask, chosen, settings, notice).apply(snapshot, edits);
    }

    private ShadeField field(TextureContext.Filter target) {
        return light.towards() != null
                ? ShadeField.sun(light.towards(), shading.occlusion(), shading.occlusionRadius(), shading.shadows(),
                target)
                : ShadeField.spot(light.point(), shading.occlusion(), shading.occlusionRadius(), shading.shadows(),
                target);
    }

    /** Bloques enteros sólidos y piezas con forma; lo demás (cristal, plantas, antorchas…) se queda. */
    static boolean recolorable(RegionSnapshot snapshot, int x, int y, int z) {
        return snapshot.isSolid(x, y, z) || ShapeFamilies.shaped(snapshot.blockId(x, y, z));
    }

    /** Bloques de superficie de cada material entero dentro de la selección y de la máscara. */
    static Map<String, Integer> count(RegionSnapshot snapshot, BlockColorCatalog catalog, TextureContext.Filter mask,
                                      Runnable checkCancelled) {
        Map<String, Integer> counts = new HashMap<>();
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    checkCancelled.run();
                    if (!snapshot.selected(x, y, z) || snapshot.isAir(x, y, z) || !recolorable(snapshot, x, y, z)
                            || snapshot.exposedFaces(x, y, z) == 0 || !mask.test(x, y, z)) {
                        continue;
                    }
                    counts.merge(catalog.base(snapshot.blockId(x, y, z)), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private static List<Pattern> patterns(List<String> ids) {
        List<Pattern> patterns = new ArrayList<>(ids.size());
        for (String id : ids) {
            BlockType type = BlockTypes.get(id);
            if (type == null) {
                throw new IllegalStateException("WorldEdit no conoce el bloque " + id);
            }
            patterns.add(type.getDefaultState());
        }
        return patterns;
    }
}
