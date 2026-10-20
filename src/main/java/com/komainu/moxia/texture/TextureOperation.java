package com.komainu.moxia.texture;

import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.RegionEdits;
import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.RegionOperation;
import com.komainu.moxia.region.RegionSnapshot;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Aplica un {@link TextureCommandParser.Request} sobre la foto: los modos con campo eligen la posición de la paleta
 * de cada bloque; {@code shift} y {@code blend} cambian los bloques que ya son de la paleta. Todo corre en el worker:
 * la máscara mira la foto y los patrones de la paleta ya vienen parseados del hilo del servidor.
 */
public final class TextureOperation implements RegionOperation {
    private final TextureCommandParser.Request request;
    private final RegionMasks.Bound mask;
    private final List<Pattern> palette;
    private final TextureContext.Settings settings;
    private final Consumer<String> notice;
    private final Map<String, BlockType> types = new HashMap<>();
    private final Map<String, String> ownVariants = new HashMap<>();

    /**
     * @param mask    máscara del comando ({@code null} en shift y blend)
     * @param palette un patrón por posición de la paleta
     * @param notice  avisos para el jugador (se llama desde el worker)
     */
    public TextureOperation(
            TextureCommandParser.Request request,
            RegionMasks.Bound mask,
            List<Pattern> palette,
            TextureContext.Settings settings,
            Consumer<String> notice
    ) {
        if (palette.size() < 2 && request.mode() != TextureCommandParser.Mode.SHIFT) {
            throw new IllegalArgumentException("La paleta necesita al menos dos bloques, por ejemplo ##Grayscale");
        }
        this.request = request;
        this.mask = mask;
        this.palette = List.copyOf(palette);
        this.settings = settings;
        this.notice = notice;
    }

    /** Margen que hay que capturar alrededor de la selección. */
    public int padding() {
        return switch (request.mode()) {
            case SHIFT -> 0;
            case BLEND -> (int) Math.ceil(Math.min(request.blendRadius() * 2.0, 8.0)) + 1;
            default -> Math.clamp(request.field().padding(), 0, 32);
        };
    }

    @Override
    public boolean needsLight() {
        return request.field() != null && request.field().needsLight();
    }

    @Override
    public void apply(RegionSnapshot snapshot, RegionEdits edits) {
        PaletteRemap remap = new PaletteRemap(materials(), id -> type(id) != null);
        switch (request.mode()) {
            case SHIFT -> shift(snapshot, edits, remap);
            case BLEND -> blend(snapshot, edits, remap);
            default -> texture(snapshot, edits, remap);
        }
    }

    private void texture(RegionSnapshot snapshot, RegionEdits edits, PaletteRemap remap) {
        mask.bind(snapshot);
        TextureContext context = new TextureContext(snapshot, mask::test, settings,
                () -> Thread.currentThread().isInterrupted());
        if (context.size() == 0) {
            throw new IllegalArgumentException("Ningún bloque de la selección pasa la máscara «" + mask.source() + "»");
        }
        double[] values = request.field().evaluate(context);
        int textured = 0;
        for (int index = 0; index < values.length; index++) {
            edits.checkCancelled();
            if (Double.isNaN(values[index])) {
                continue;
            }
            textured++;
            int x = context.x(index);
            int y = context.y(index);
            int z = context.z(index);
            int position = PaletteRemap.index(values[index], palette.size(), request.dither(), x, y, z);
            BlockState chosen = place(snapshot, remap, position, x, y, z, request.shaped());
            if (chosen != null) {
                edits.set(x, y, z, chosen);
            }
        }
        if (textured == 0) {
            throw new IllegalArgumentException("La selección no tiene bloques de superficie que texturizar: "
                    + "amplíala para que incluya el aire de alrededor");
        }
        if (context.surfaceCount() * 20L < context.size() && textured < context.size()) {
            notice.accept("solo " + context.surfaceCount() + " de " + ProgressLine.group(context.size())
                    + " bloques dan al aire; si falta superficie, amplía la selección");
        }
    }

    private void shift(RegionSnapshot snapshot, RegionEdits edits, PaletteRemap remap) {
        int shift = request.shift();
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    edits.checkCancelled();
                    if (!snapshot.selected(x, y, z) || snapshot.isAir(x, y, z)) {
                        continue;
                    }
                    int current = remap.indexOf(snapshot.blockId(x, y, z), shift > 0 ? 1 : -1);
                    if (current < 0) {
                        continue;
                    }
                    int target = Math.clamp(current + (long) shift, 0, remap.size() - 1);
                    if (target == current) {
                        continue;
                    }
                    BlockState chosen = place(snapshot, remap, target, x, y, z, true);
                    if (chosen != null) {
                        edits.set(x, y, z, chosen);
                    }
                }
            }
        }
    }

    private void blend(RegionSnapshot snapshot, RegionEdits edits, PaletteRemap remap) {
        TextureContext context = new TextureContext(snapshot,
                (x, y, z) -> remap.indexOf(snapshot.blockId(x, y, z), 0) >= 0
                        && (request.shaped() || PaletteRemap.shapeOf(snapshot.blockId(x, y, z)) == null),
                settings, () -> Thread.currentThread().isInterrupted());
        if (context.size() == 0) {
            throw new IllegalArgumentException("En la selección no hay bloques de la paleta que mezclar");
        }
        double[] values = context.empty();
        double last = Math.max(1, remap.size() - 1);
        for (int index = 0; index < values.length; index++) {
            if (request.fullBlend() || context.surface(index)) {
                values[index] = remap.indexOf(snapshot.blockId(context.x(index), context.y(index), context.z(index)),
                        0) / last;
            }
        }
        double[] blended = request.fullBlend()
                ? context.smooth(values, request.blendRadius(), false)
                : context.smoothSurface(values, request.blendRadius());
        for (int index = 0; index < blended.length; index++) {
            edits.checkCancelled();
            if (Double.isNaN(blended[index])) {
                continue;
            }
            int x = context.x(index);
            int y = context.y(index);
            int z = context.z(index);
            int position = PaletteRemap.index(blended[index], remap.size(), request.dither(), x, y, z);
            if (position == remap.indexOf(snapshot.blockId(x, y, z), 0)) {
                continue;
            }
            BlockState chosen = place(snapshot, remap, position, x, y, z, true);
            if (chosen != null) {
                edits.set(x, y, z, chosen);
            }
        }
    }

    /**
     * Bloque de la posición {@code index} de la paleta para ese sitio. Si el bloque que hay tiene forma y se pide
     * conservarla, se busca la pieza del material (o del más cercano de la paleta que la tenga) con las propiedades
     * del bloque actual; {@code null} si no hay ninguna.
     */
    private BlockState place(RegionSnapshot snapshot, PaletteRemap remap, int index, int x, int y, int z,
                             boolean keepShape) {
        BlockState chosen = palette.get(index).applyBlock(BlockVector3.at(x, y, z)).toImmutableState();
        String shape = keepShape ? PaletteRemap.shapeOf(snapshot.blockId(x, y, z)) : null;
        if (shape == null) {
            return chosen;
        }
        BlockState current = snapshot.state(x, y, z);
        String chosenId = chosen.getBlockType().id();
        if (chosenId.endsWith(shape)) {
            return copyProperties(current, chosen);
        }
        String own = ownVariants.computeIfAbsent(chosenId + shape, key -> {
            String variant = new PaletteRemap(List.of(chosenId), id -> type(id) != null).variant(0, shape);
            return variant == null ? "" : variant;
        });
        if (own.isEmpty()) {
            own = null;
        }
        if (own == null) {
            int nearest = remap.nearestWithShape(index, shape, 1);
            own = nearest < 0 ? null : remap.variant(nearest, shape);
        }
        BlockType type = own == null ? null : type(own);
        return type == null ? null : copyProperties(current, type.getDefaultState());
    }

    private List<String> materials() {
        return palette.stream()
                .map(pattern -> pattern.applyBlock(BlockVector3.ZERO).getBlockType().id())
                .toList();
    }

    private BlockType type(String id) {
        if (!types.containsKey(id)) {
            types.put(id, BlockTypes.get(id));
        }
        return types.get(id);
    }

    /** Copia al bloque nuevo las propiedades que comparta con el actual (orientación, mitad, anegado…). */
    @SuppressWarnings("unchecked")
    static BlockState copyProperties(BlockState from, BlockState to) {
        if (from == null) {
            return to;
        }
        BlockState result = to;
        for (Map.Entry<Property<?>, Object> entry : from.getStates().entrySet()) {
            Property<Object> property = (Property<Object>) result.getBlockType().getPropertyMap()
                    .get(entry.getKey().getName());
            if (property != null && property.getValues().contains(entry.getValue())) {
                result = result.with(property, entry.getValue());
            }
        }
        return result;
    }
}
