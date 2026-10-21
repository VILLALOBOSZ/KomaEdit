package com.komainu.moxia.display;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lo que cambió una edición de displays, para deshacerla y rehacerla. Cada {@link Record} guarda un display entero
 * antes y después ({@code S} es su foto: en el juego, su NBT completo con el UUID); {@code before} nulo es que se creó,
 * {@code after} nulo, que se borró. Va dentro del historial de WorldEdit ({@link DisplayChange}), así que
 * {@code //undo} y {@code //redo} deshacen y rehacen los displays junto con los bloques, en el mismo orden.
 *
 * <p>Deshacer un borrado vuelve a crear el display con su UUID de siempre, así las demás ediciones que lo nombran
 * siguen valiendo. Si ya existe uno con ese UUID (se deshizo dos veces por otro camino) no se duplica.</p>
 *
 * @param <S> la foto de un display
 */
public final class DisplayHistory<S> {
    public record Record<S>(UUID id, S before, S after) {
        public Record {
            if (id == null || before == null && after == null) {
                throw new IllegalArgumentException("Un cambio necesita el display y algún estado");
            }
        }

        public boolean created() {
            return before == null;
        }

        public boolean deleted() {
            return after == null;
        }
    }

    /** El mundo visto desde el historial. */
    public interface World<S> {
        boolean exists(UUID id);

        /** Lo crea con la foto (y su UUID). */
        boolean spawn(S state);

        boolean remove(UUID id);

        /** Pone la foto a uno que existe. */
        boolean apply(UUID id, S state);
    }

    /** Cuántos cambios salieron bien y cuántos no (el display ya no estaba, su chunk no está cargado…). */
    public record Outcome(int applied, int missed) {
    }

    private final List<Record<S>> records = new ArrayList<>();

    public DisplayHistory<S> created(UUID id, S after) {
        records.add(new Record<>(id, null, after));
        return this;
    }

    public DisplayHistory<S> deleted(UUID id, S before) {
        records.add(new Record<>(id, before, null));
        return this;
    }

    public DisplayHistory<S> changed(UUID id, S before, S after) {
        records.add(new Record<>(id, before, after));
        return this;
    }

    public List<Record<S>> records() {
        return List.copyOf(records);
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }

    public int size() {
        return records.size();
    }

    /** Lo deja como estaba antes, de la última a la primera. */
    public Outcome undo(World<S> world) {
        int applied = 0;
        int missed = 0;
        for (int index = records.size() - 1; index >= 0; index--) {
            if (restore(world, records.get(index), true)) {
                applied++;
            } else {
                missed++;
            }
        }
        return new Outcome(applied, missed);
    }

    /** Lo vuelve a hacer, de la primera a la última. */
    public Outcome redo(World<S> world) {
        int applied = 0;
        int missed = 0;
        for (Record<S> record : records) {
            if (restore(world, record, false)) {
                applied++;
            } else {
                missed++;
            }
        }
        return new Outcome(applied, missed);
    }

    private static <S> boolean restore(World<S> world, Record<S> record, boolean backwards) {
        S target = backwards ? record.before() : record.after();
        if (target == null) {
            return world.exists(record.id()) && world.remove(record.id());
        }
        if (!world.exists(record.id())) {
            S source = backwards ? record.after() : record.before();
            // Cambiado y luego desaparecido: no se resucita con un estado que nadie borró.
            return source == null && world.spawn(target);
        }
        if (backwards ? record.after() == null : record.before() == null) {
            // Ya existe (deshecho dos veces): no se duplica.
            return false;
        }
        return world.apply(record.id(), target);
    }
}
