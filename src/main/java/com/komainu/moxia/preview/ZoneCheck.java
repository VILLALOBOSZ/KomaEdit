package com.komainu.moxia.preview;

import com.komainu.moxia.edit.PackedBlockPlan;

/**
 * Comprueba, repartido entre ticks, si el mundo sigue como estaba al hacer la vista previa: compara el estado de
 * cada posición vigilada con el que se leyó entonces. Se para en la primera diferencia.
 */
public final class ZoneCheck {
    /** Lectura del mundo en el hilo del servidor: id crudo del estado de bloque. */
    @FunctionalInterface
    public interface World {
        int rawId(int x, int y, int z);
    }

    /** Posiciones vigiladas y el estado que tenían. */
    public interface Watch {
        int size();

        /** Posición empaquetada como en {@link PackedBlockPlan#pack}. */
        long position(int index);

        /** Id crudo esperado, o {@link PackedBlockPlan#NO_EXPECTED_STATE} para no comprobarla. */
        int expected(int index);

        Watch EMPTY = new Watch() {
            @Override
            public int size() {
                return 0;
            }

            @Override
            public long position(int index) {
                throw new IndexOutOfBoundsException(index);
            }

            @Override
            public int expected(int index) {
                throw new IndexOutOfBoundsException(index);
            }
        };

        /** Las posiciones de un plan con sus estados esperados (las escrituras condicionales). */
        static Watch of(PackedBlockPlan plan) {
            return new Watch() {
                @Override
                public int size() {
                    return plan.size();
                }

                @Override
                public long position(int index) {
                    return plan.packedPosition(index);
                }

                @Override
                public int expected(int index) {
                    return plan.expectedStateId(index);
                }
            };
        }

        /** Primero estas posiciones y después las de {@code next}. */
        default Watch then(Watch next) {
            Watch first = this;
            if (next == null || next.size() == 0) {
                return first;
            }
            if (first.size() == 0) {
                return next;
            }
            return new Watch() {
                @Override
                public int size() {
                    return first.size() + next.size();
                }

                @Override
                public long position(int index) {
                    return index < first.size() ? first.position(index) : next.position(index - first.size());
                }

                @Override
                public int expected(int index) {
                    return index < first.size() ? first.expected(index) : next.expected(index - first.size());
                }
            };
        }
    }

    private final Watch watch;
    private int cursor;
    private boolean changed;
    private long mismatch;

    public ZoneCheck(Watch watch) {
        this.watch = watch == null ? Watch.EMPTY : watch;
    }

    /**
     * Avanza como mucho {@code budget} comprobaciones.
     *
     * @return {@code true} cuando ha terminado (sin diferencias o con la primera encontrada)
     */
    public boolean step(World world, int budget) {
        int checked = 0;
        while (cursor < watch.size() && checked < budget && !changed()) {
            int expected = watch.expected(cursor);
            if (expected != PackedBlockPlan.NO_EXPECTED_STATE) {
                long packed = watch.position(cursor);
                int actual = world.rawId(PackedBlockPlan.unpackX(packed), PackedBlockPlan.unpackY(packed),
                        PackedBlockPlan.unpackZ(packed));
                if (actual != expected) {
                    changed = true;
                    mismatch = packed;
                }
            }
            cursor++;
            checked++;
        }
        return done();
    }

    public boolean done() {
        return changed() || cursor >= watch.size();
    }

    public boolean changed() {
        return changed;
    }

    /** Primera posición distinta, empaquetada; solo si {@link #changed()}. */
    public long mismatch() {
        if (!changed()) {
            throw new IllegalStateException("La zona no ha cambiado");
        }
        return mismatch;
    }

    public int checked() {
        return cursor;
    }

    public int total() {
        return watch.size();
    }
}
