package com.komainu.moxia.capability;

import java.util.ArrayList;
import java.util.List;

/**
 * «Arrastrar»: el primer clic fija el origen (un bloque o, con Mayús, la fila de bloques iguales que lo cruza) y el
 * eje, que es el de la cara pulsada; el segundo estira el origen por ese eje hasta donde se mira. Las copias solo
 * van a posiciones libres.
 */
public final class SmearPlanner {
    /** Por debajo de este seno entre la mirada y el eje se miran casi paralelos y no se usa el punto más cercano. */
    private static final double PARALLEL_SINE = 0.2;

    /** Una copia: del bloque de origen {@code from} a la posición {@code to}. */
    public record Copy(Cell from, Cell to) {
    }

    private SmearPlanner() {
    }

    /**
     * Fila de origen: la tira recta más larga de bloques iguales que pasa por {@code start} en uno de los dos ejes
     * del plano de la cara (con empate, el primero de X, Y, Z). Incluye {@code start}.
     */
    public static List<Cell> row(LayerFinder.View view, Cell start, Cell normal, int limit) {
        if (!normal.isUnitAxis()) {
            throw new IllegalArgumentException("La cara tiene que ser una de las seis direcciones");
        }
        List<Cell> best = List.of(start);
        for (int axis = 0; axis < 3; axis++) {
            if (axis == normal.axis()) {
                continue;
            }
            Cell unit = Cell.unit(axis);
            List<Cell> run = new ArrayList<>();
            run.add(start);
            for (int sign = -1; sign <= 1; sign += 2) {
                Cell step = unit.scale(sign);
                Cell cursor = start.add(step);
                while (run.size() < limit && view.matches(cursor.x(), cursor.y(), cursor.z())) {
                    run.add(cursor);
                    cursor = cursor.add(step);
                }
            }
            if (run.size() > best.size()) {
                best = run;
            }
        }
        return List.copyOf(best);
    }

    /**
     * Cuánto se estira (con signo, en bloques sobre {@code axis}) según hacia dónde se mira.
     *
     * <p>Se toma el punto del eje que pasa por el centro de {@code anchor} más cercano al rayo de la mirada, así se
     * puede estirar hacia el aire. Si la mirada va casi paralela al eje, ese punto no sirve y se proyecta sobre el
     * eje el punto de la mirada a {@code fallbackDistance} (el bloque mirado o el alcance).</p>
     *
     * @param eye  posición de los ojos
     * @param look dirección de la mirada (no hace falta que sea unitaria)
     * @param axis dirección unitaria del eje (la cara pulsada)
     * @param max  longitud máxima en valor absoluto
     */
    public static int length(double[] eye, double[] look, Cell anchor, Cell axis, double fallbackDistance, int max) {
        double[] direction = normalize(look);
        double[] center = {anchor.x() + 0.5, anchor.y() + 0.5, anchor.z() + 0.5};
        double[] unit = {axis.x(), axis.y(), axis.z()};
        double[] toEye = {center[0] - eye[0], center[1] - eye[1], center[2] - eye[2]};
        double alignment = dot(unit, direction);
        double sine = Math.sqrt(Math.max(0.0, 1.0 - alignment * alignment));
        double along;
        if (sine >= PARALLEL_SINE) {
            double d = dot(unit, toEye);
            double e = dot(direction, toEye);
            double denominator = 1.0 - alignment * alignment;
            double onLine = (alignment * e - d) / denominator;
            double onRay = (e - alignment * d) / denominator;
            if (onRay < 0.0) {
                return 0;
            }
            along = onLine;
        } else {
            double[] point = {
                    eye[0] + direction[0] * fallbackDistance,
                    eye[1] + direction[1] * fallbackDistance,
                    eye[2] + direction[2] * fallbackDistance
            };
            along = (point[0] - center[0]) * unit[0] + (point[1] - center[1]) * unit[1]
                    + (point[2] - center[2]) * unit[2];
        }
        long rounded = Math.round(along);
        return (int) Math.clamp(rounded, -max, max);
    }

    /**
     * Comprueba el final que manda el cliente: tiene que estar sobre el eje de {@code anchor} y a no más de
     * {@code max} bloques.
     *
     * @return la longitud con signo
     */
    public static int validateEnd(Cell anchor, Cell axis, Cell end, int max) {
        Cell delta = end.subtract(anchor);
        int along = delta.component(axis.axis()) * axis.component(axis.axis());
        if (!delta.equals(axis.scale(along))) {
            throw new IllegalArgumentException("El final tiene que estar en el eje del origen");
        }
        if (along == 0) {
            throw new IllegalArgumentException("Mira más lejos por el eje para estirar");
        }
        if (Math.abs(along) > max) {
            throw new IllegalArgumentException("Como mucho se estiran " + max + " bloques");
        }
        return along;
    }

    /** Las copias de estirar {@code source} {@code length} bloques por {@code axis} (con signo). */
    public static List<Copy> copies(List<Cell> source, Cell axis, int length) {
        List<Copy> copies = new ArrayList<>(source.size() * Math.abs(length));
        int sign = Integer.signum(length);
        for (int step = 1; step <= Math.abs(length); step++) {
            for (Cell from : source) {
                copies.add(new Copy(from, from.offset(axis, step * sign)));
            }
        }
        return copies;
    }

    private static double[] normalize(double[] vector) {
        double length = Math.sqrt(dot(vector, vector));
        if (length < 1.0E-9) {
            throw new IllegalArgumentException("Dirección de mirada nula");
        }
        return new double[]{vector[0] / length, vector[1] / length, vector[2] / length};
    }

    private static double dot(double[] first, double[] second) {
        return first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
    }
}
