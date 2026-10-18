package com.komainu.moxia.capability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Capa conectada de «Extruir»: los bloques iguales al pulsado que se tocan por un lado en el plano de la cara y
 * tienen esa cara al descubierto. Extruir copia cada uno una posición hacia fuera; encoger los quita.
 */
public final class LayerFinder {
    /** Lo que la capa necesita saber del mundo. Fuera de lo cargado, nada coincide ni está libre. */
    public interface View {
        /** ¿Es este bloque igual al pulsado (mismo tipo o mismo estado, según la opción)? */
        boolean matches(int x, int y, int z);

        /** ¿Está libre (aire, agua, hierba…) para poner un bloque? */
        boolean free(int x, int y, int z);
    }

    /** La capa: posiciones en orden de búsqueda y si se cortó al llegar al límite. */
    public record Layer(List<Cell> cells, boolean truncated) {
        public Layer {
            cells = List.copyOf(cells);
        }

        public boolean isEmpty() {
            return cells.isEmpty();
        }
    }

    private LayerFinder() {
    }

    /**
     * Busca la capa a partir de {@code start} con la cara {@code normal} (dirección unitaria hacia fuera).
     *
     * @param limit bloques como mucho
     */
    public static Layer find(View view, Cell start, Cell normal, int limit) {
        if (!normal.isUnitAxis()) {
            throw new IllegalArgumentException("La cara tiene que ser una de las seis direcciones");
        }
        if (limit < 1 || !inLayer(view, start, normal)) {
            return new Layer(List.of(), false);
        }
        List<Cell> steps = planeSteps(normal.axis());
        List<Cell> cells = new ArrayList<>();
        Set<Cell> visited = new HashSet<>();
        ArrayDeque<Cell> pending = new ArrayDeque<>();
        pending.add(start);
        visited.add(start);
        boolean truncated = false;
        while (!pending.isEmpty()) {
            Cell current = pending.removeFirst();
            if (cells.size() >= limit) {
                truncated = true;
                break;
            }
            cells.add(current);
            for (Cell step : steps) {
                Cell next = current.add(step);
                if (visited.add(next) && inLayer(view, next, normal)) {
                    pending.addLast(next);
                }
            }
        }
        return new Layer(cells, truncated);
    }

    /** Dónde va cada copia al extruir: una posición hacia fuera de cada bloque de la capa. */
    public static List<Cell> extruded(Layer layer, Cell normal) {
        List<Cell> targets = new ArrayList<>(layer.cells().size());
        for (Cell cell : layer.cells()) {
            targets.add(cell.add(normal));
        }
        return targets;
    }

    /** Los cuatro pasos de vecino dentro del plano perpendicular a un eje. */
    static List<Cell> planeSteps(int axis) {
        List<Cell> steps = new ArrayList<>(4);
        for (int other = 0; other < 3; other++) {
            if (other == axis) {
                continue;
            }
            Cell unit = Cell.unit(other);
            steps.add(unit);
            steps.add(unit.scale(-1));
        }
        return steps;
    }

    private static boolean inLayer(View view, Cell cell, Cell normal) {
        Cell front = cell.add(normal);
        return view.matches(cell.x(), cell.y(), cell.z()) && view.free(front.x(), front.y(), front.z());
    }
}
