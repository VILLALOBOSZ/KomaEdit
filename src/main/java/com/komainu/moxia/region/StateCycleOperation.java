package com.komainu.moxia.region;

import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.List;

/**
 * {@code statecycle}: avanza una propiedad de estado al valor siguiente en cada bloque de la máscara que la tenga
 * ({@code facing}: north → south → west → east → north…, en el orden de valores del bloque), como
 * {@code //ezstatecycle} de ezEdits. Con pasos negativos va hacia atrás.
 */
public final class StateCycleOperation implements RegionOperation {
    private final RegionMasks.Bound mask;
    private final String property;
    private final int steps;

    public StateCycleOperation(RegionMasks.Bound mask, String property, int steps) {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("Falta la propiedad que ciclar, por ejemplo facing o half");
        }
        if (steps == 0) {
            throw new IllegalArgumentException("Avanzar 0 pasos no cambia nada");
        }
        this.mask = mask;
        this.property = property.trim();
        this.steps = steps;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void apply(RegionSnapshot snapshot, RegionEdits edits) {
        mask.bind(snapshot);
        int found = 0;
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    edits.checkCancelled();
                    if (!snapshot.selected(x, y, z) || snapshot.isAir(x, y, z)) {
                        continue;
                    }
                    BlockState state = snapshot.state(x, y, z);
                    Property<Object> target = (Property<Object>) state.getBlockType().getPropertyMap().get(property);
                    if (target == null || !mask.test(x, y, z)) {
                        continue;
                    }
                    found++;
                    List<Object> values = target.getValues();
                    int current = values.indexOf(state.getState(target));
                    Object next = values.get(RegionRules.cycle(values.size(), Math.max(0, current), steps));
                    edits.set(x, y, z, state.with(target, next));
                }
            }
        }
        if (found == 0) {
            throw new IllegalArgumentException("Ningún bloque de la máscara tiene la propiedad «" + property + "»");
        }
    }
}
