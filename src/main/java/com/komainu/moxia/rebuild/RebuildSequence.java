package com.komainu.moxia.rebuild;

import it.unimi.dsi.fastutil.ints.IntArrays;

import java.util.SplittableRandom;

/**
 * El orden de aparición de una reconstrucción: qué entrada del modelo se pone en cada paso y a qué grupo pertenece
 * (capa, rodaja del eje, onda o fase del constructor), para las pausas entre grupos y la barra de progreso. Cada
 * bloque de la obra sale exactamente una vez; el aire no es un paso. Java puro.
 */
public final class RebuildSequence {
    private final RebuildOrder order;
    private final int[] steps;
    private final int[] groups;
    private final boolean fellBack;
    private final RebuildAxis axis;

    private RebuildSequence(RebuildOrder order, int[] steps, int[] groups, boolean fellBack, RebuildAxis axis) {
        this.order = order;
        this.steps = steps;
        this.groups = groups;
        this.fellBack = fellBack;
        this.axis = axis;
    }

    /**
     * Opciones de los órdenes que las usan.
     *
     * @param axis      eje de {@link RebuildOrder#AXIS}
     * @param origin    centro de las ondas, o {@code null} para el centro de la base de la obra
     * @param seed      semilla de {@link RebuildOrder#RANDOM}
     */
    public record Options(RebuildAxis axis, int[] origin, long seed) {
        public static final Options DEFAULT = new Options(RebuildAxis.X_POSITIVE, null, 0L);

        public Options {
            axis = axis == null ? RebuildAxis.X_POSITIVE : axis;
            if (origin != null && origin.length != 3) {
                throw new IllegalArgumentException("El centro son tres coordenadas");
            }
            origin = origin == null ? null : origin.clone();
        }
    }

    public static RebuildSequence of(RebuildModel model, RebuildOrder order, Options options) {
        Options settings = options == null ? Options.DEFAULT : options;
        int[] steps = buildable(model);
        return switch (order) {
            case LAYERS -> scan(model, steps, RebuildOrder.LAYERS, RebuildAxis.Y_POSITIVE);
            case AXIS -> scan(model, steps, RebuildOrder.AXIS, settings.axis());
            case WAVES -> waves(model, steps, settings.origin());
            case RANDOM -> random(steps, settings.seed());
            case BUILDER -> builder(model, steps);
            case HISTORY -> history(model, steps);
        };
    }

    /** El orden que se usa de verdad: el pedido o, si era el historial y no hay, {@link RebuildOrder#LAYERS}. */
    public RebuildOrder order() {
        return order;
    }

    /** Si se pidió el historial y ningún bloque de la obra estaba apuntado. */
    public boolean fellBack() {
        return fellBack;
    }

    public int size() {
        return steps.length;
    }

    /** La entrada del modelo que se pone en el paso {@code step}. */
    public int entry(int step) {
        return steps[step];
    }

    public int group(int step) {
        return groups[step];
    }

    /** Si el paso empieza un grupo nuevo (capa, fase…): ahí va la pausa entre capas. */
    public boolean startsGroup(int step) {
        return step > 0 && groups[step] != groups[step - 1];
    }

    /** El grupo en palabras, para la barra («capa 64», «paredes»…); vacío si el orden no tiene grupos. */
    public String groupLabel(int step) {
        if (step < 0 || step >= steps.length) {
            return "";
        }
        int group = groups[step];
        return switch (order) {
            case LAYERS -> "capa " + group;
            case AXIS -> axis.id().replace("-", "") + " " + group;
            case WAVES -> "onda " + group;
            case BUILDER -> BuildPhase.values()[group].label();
            case HISTORY -> group == 0 ? "lo de antes del historial" : "historial";
            case RANDOM -> "";
        };
    }

    // ------------------------------------------------------------------ Órdenes

    private static int[] buildable(RebuildModel model) {
        int[] steps = new int[model.buildable()];
        int next = 0;
        for (int entry = 0; entry < model.size(); entry++) {
            if (model.target(entry) != RebuildModel.AIR) {
                steps[next++] = entry;
            }
        }
        return steps;
    }

    /** Por rodajas del eje y, dentro de cada una, por filas en zigzag. */
    private static RebuildSequence scan(RebuildModel model, int[] steps, RebuildOrder order, RebuildAxis axis) {
        long[] keys = new long[model.size()];
        int[] groups = new int[model.size()];
        for (int entry : steps) {
            keys[entry] = scanKey(model, entry, axis);
            groups[entry] = coordinate(model, entry, axis.axis());
        }
        sort(steps, keys, null);
        return new RebuildSequence(order, steps, gather(steps, groups), false, axis);
    }

    /**
     * Clave de un barrido: la rodaja (en el sentido del eje), la fila (de abajo arriba, o en Z si el eje es Y) y la
     * columna. Es un zigzag seguido, como una impresora 3D: cada fila va al revés que la anterior y cada rodaja
     * recorre sus filas al revés que la anterior, así que el barrido nunca salta de un lado a otro.
     */
    static long scanKey(RebuildModel model, int entry, RebuildAxis axis) {
        int along = axis.axis();
        int rowAxis = along == 1 ? 2 : 1;
        int columnAxis = along == 0 ? 2 : 0;
        long slice = axis.negative() ? max(model, along) - coordinate(model, entry, along)
                : coordinate(model, entry, along) - min(model, along);
        long rows = (long) max(model, rowAxis) - min(model, rowAxis) + 1;
        long row = coordinate(model, entry, rowAxis) - min(model, rowAxis);
        if ((slice & 1L) == 1L) {
            row = rows - 1 - row;
        }
        long column = coordinate(model, entry, columnAxis) - min(model, columnAxis);
        if (((slice * rows + row) & 1L) == 1L) {
            column = max(model, columnAxis) - min(model, columnAxis) - column;
        }
        return slice << 42 | row << 21 | column;
    }

    private static RebuildSequence waves(RebuildModel model, int[] steps, int[] origin) {
        int centerX = origin != null ? origin[0] : Math.floorDiv(model.minX() + model.maxX(), 2);
        int centerY = origin != null ? origin[1] : model.minY();
        int centerZ = origin != null ? origin[2] : Math.floorDiv(model.minZ() + model.maxZ(), 2);
        long[] distance = new long[model.size()];
        long[] tie = new long[model.size()];
        int[] groups = new int[model.size()];
        for (int entry : steps) {
            long dx = model.x(entry) - centerX;
            long dy = model.y(entry) - centerY;
            long dz = model.z(entry) - centerZ;
            distance[entry] = dx * dx + dy * dy + dz * dz;
            tie[entry] = scanKey(model, entry, RebuildAxis.Y_POSITIVE);
            groups[entry] = (int) Math.floor(Math.sqrt(distance[entry]));
        }
        sort(steps, distance, tie);
        return new RebuildSequence(RebuildOrder.WAVES, steps, gather(steps, groups), false, RebuildAxis.Y_POSITIVE);
    }

    private static RebuildSequence random(int[] steps, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        for (int index = steps.length - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            int swap = steps[index];
            steps[index] = steps[other];
            steps[other] = swap;
        }
        return new RebuildSequence(RebuildOrder.RANDOM, steps, new int[steps.length], false, RebuildAxis.Y_POSITIVE);
    }

    private static RebuildSequence builder(RebuildModel model, int[] steps) {
        BuildPhase[] phases = BuilderPhases.classify(model);
        long[] phase = new long[model.size()];
        long[] scan = new long[model.size()];
        int[] groups = new int[model.size()];
        for (int entry : steps) {
            phase[entry] = phases[entry].ordinal();
            scan[entry] = scanKey(model, entry, RebuildAxis.Y_POSITIVE);
            groups[entry] = phases[entry].ordinal();
        }
        sort(steps, phase, scan);
        return new RebuildSequence(RebuildOrder.BUILDER, steps, gather(steps, groups), false, RebuildAxis.Y_POSITIVE);
    }

    /**
     * Primero lo que no está en el historial (ya estaba antes de que se apuntara nada), de abajo arriba; después lo
     * apuntado, del más antiguo al más reciente. Sin nada apuntado, por capas.
     */
    private static RebuildSequence history(RebuildModel model, int[] steps) {
        boolean any = false;
        for (int entry : steps) {
            if (model.history(entry) != RebuildModel.NO_HISTORY) {
                any = true;
                break;
            }
        }
        if (!any) {
            RebuildSequence layers = scan(model, steps, RebuildOrder.LAYERS, RebuildAxis.Y_POSITIVE);
            return new RebuildSequence(RebuildOrder.LAYERS, layers.steps, layers.groups, true, RebuildAxis.Y_POSITIVE);
        }
        long[] known = new long[model.size()];
        long[] rank = new long[model.size()];
        int[] groups = new int[model.size()];
        for (int entry : steps) {
            long history = model.history(entry);
            known[entry] = history == RebuildModel.NO_HISTORY ? 0 : 1;
            rank[entry] = history == RebuildModel.NO_HISTORY ? scanKey(model, entry, RebuildAxis.Y_POSITIVE) : history;
            groups[entry] = (int) known[entry];
        }
        sort(steps, known, rank);
        return new RebuildSequence(RebuildOrder.HISTORY, steps, gather(steps, groups), false, RebuildAxis.Y_POSITIVE);
    }

    // ------------------------------------------------------------------ Utilidades

    /** Ordena los pasos por {@code primary}, luego {@code secondary} y, a igualdad, por entrada (estable). */
    private static void sort(int[] steps, long[] primary, long[] secondary) {
        IntArrays.quickSort(steps, (left, right) -> {
            int compared = Long.compare(primary[left], primary[right]);
            if (compared == 0 && secondary != null) {
                compared = Long.compare(secondary[left], secondary[right]);
            }
            return compared != 0 ? compared : Integer.compare(left, right);
        });
    }

    /** De «grupo de cada entrada» a «grupo de cada paso». */
    private static int[] gather(int[] steps, int[] groupOfEntry) {
        int[] groups = new int[steps.length];
        for (int step = 0; step < steps.length; step++) {
            groups[step] = groupOfEntry[steps[step]];
        }
        return groups;
    }

    private static int coordinate(RebuildModel model, int entry, int axis) {
        return switch (axis) {
            case 0 -> model.x(entry);
            case 1 -> model.y(entry);
            default -> model.z(entry);
        };
    }

    private static int min(RebuildModel model, int axis) {
        return switch (axis) {
            case 0 -> model.minX();
            case 1 -> model.minY();
            default -> model.minZ();
        };
    }

    private static int max(RebuildModel model, int axis) {
        return switch (axis) {
            case 0 -> model.maxX();
            case 1 -> model.maxY();
            default -> model.maxZ();
        };
    }
}
