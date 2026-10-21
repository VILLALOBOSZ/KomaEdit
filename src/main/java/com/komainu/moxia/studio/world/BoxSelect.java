package com.komainu.moxia.studio.world;

import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.shape.Vec3;

/**
 * Seleccionar con el ratón en el mundo con el Studio abierto, como la Box Select de Axiom: se pulsa en un bloque y se
 * arrastra hasta otro (la caja va de uno a otro), y luego se tira de cualquier cara de la caja para alargarla o
 * acortarla por ese lado. Doble clic en un bloque, sin arrastrar, pide la selección mágica (lo pegado del mismo
 * bloque). Lógica pura: la pantalla le da los rayos del cursor y los bloques a los que apunta, y manda a WorldEdit la
 * caja que sale.
 */
public final class BoxSelect {
    /** Lo que separa dos clics para que sean uno doble. */
    public static final long DOUBLE_CLICK_MILLIS = 350L;

    /** Una caja de bloques, con las esquinas incluidas. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("caja al revés");
            }
        }

        /** La caja entre dos bloques, en cualquier orden. */
        public static Box between(int[] first, int[] second) {
            return new Box(Math.min(first[0], second[0]), Math.min(first[1], second[1]), Math.min(first[2], second[2]),
                    Math.max(first[0], second[0]), Math.max(first[1], second[1]), Math.max(first[2], second[2]));
        }

        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeY() {
            return maxY - minY + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }

        public long volume() {
            return (long) sizeX() * sizeY() * sizeZ();
        }

        public int[] min() {
            return new int[]{minX, minY, minZ};
        }

        public int[] max() {
            return new int[]{maxX, maxY, maxZ};
        }

        /** El borde de la caja en el lado {@code face} (la coordenada del bloque de ese lado). */
        int edge(Face face) {
            return switch (face) {
                case WEST -> minX;
                case EAST -> maxX;
                case DOWN -> minY;
                case UP -> maxY;
                case NORTH -> minZ;
                case SOUTH -> maxZ;
            };
        }

        /** La caja con el lado {@code face} movido a {@code value}, sin pasar del lado contrario. */
        Box withEdge(Face face, int value) {
            return switch (face) {
                case WEST -> new Box(Math.min(value, maxX), minY, minZ, maxX, maxY, maxZ);
                case EAST -> new Box(minX, minY, minZ, Math.max(value, minX), maxY, maxZ);
                case DOWN -> new Box(minX, Math.min(value, maxY), minZ, maxX, maxY, maxZ);
                case UP -> new Box(minX, minY, minZ, maxX, Math.max(value, minY), maxZ);
                case NORTH -> new Box(minX, minY, Math.min(value, maxZ), maxX, maxY, maxZ);
                case SOUTH -> new Box(minX, minY, minZ, maxX, maxY, Math.max(value, minZ));
            };
        }

        /** El centro de la cara {@code face}, en coordenadas del mundo (el borde exterior del bloque). */
        Vec3 faceCenter(Face face) {
            double x = (minX + maxX + 1) / 2.0;
            double y = (minY + maxY + 1) / 2.0;
            double z = (minZ + maxZ + 1) / 2.0;
            return switch (face) {
                case WEST -> new Vec3(minX, y, z);
                case EAST -> new Vec3(maxX + 1.0, y, z);
                case DOWN -> new Vec3(x, minY, z);
                case UP -> new Vec3(x, maxY + 1.0, z);
                case NORTH -> new Vec3(x, y, minZ);
                case SOUTH -> new Vec3(x, y, maxZ + 1.0);
            };
        }
    }

    /** Las seis caras, con su eje (0 x, 1 y, 2 z) y hacia dónde mira cada una. */
    public enum Face {
        WEST(0, -1), EAST(0, 1), DOWN(1, -1), UP(1, 1), NORTH(2, -1), SOUTH(2, 1);

        private final int axis;
        private final int sign;

        Face(int axis, int sign) {
            this.axis = axis;
            this.sign = sign;
        }

        public int axis() {
            return axis;
        }

        public int sign() {
            return sign;
        }

        static Face of(int axis, int sign) {
            for (Face face : values()) {
                if (face.axis == axis && face.sign == sign) {
                    return face;
                }
            }
            throw new IllegalArgumentException("cara " + axis + "/" + sign);
        }
    }

    private enum State {
        IDLE, DRAWING, PULLING
    }

    private Box box;
    private State state = State.IDLE;
    private int[] anchor;
    private Face face;
    private Box pulledFrom;
    private double grip;
    private boolean moved;
    private long lastClick = Long.MIN_VALUE / 2;
    private int[] lastBlock;

    /** La selección que tiene ahora el jugador (la que dice el servidor), o {@code null}. */
    public void known(Box current) {
        if (state == State.IDLE) {
            box = current;
        }
    }

    public Box box() {
        return box;
    }

    public boolean active() {
        return state != State.IDLE;
    }

    /** La cara de la que se tira ahora, o {@code null}. */
    public Face pulling() {
        return state == State.PULLING ? face : null;
    }

    /**
     * La cara de la selección por la que entra el rayo (la que quedaría agarrada al pulsar), o {@code null}. Una cara
     * más lejos que {@code reach} (el terreno que se ve delante) no cuenta: está tapada.
     */
    public Face faceUnder(GizmoMath.Ray ray, double reach) {
        if (box == null) {
            return null;
        }
        Hit hit = entry(ray, box);
        return hit == null || hit.distance() > reach + 0.01 ? null : hit.face();
    }

    /**
     * Pulsar en el mundo. Sobre una cara de la selección, se empieza a tirar de ella; si no, sobre {@code block} (el
     * bloque al que apunta el cursor) empieza una caja nueva de un bloque.
     *
     * @param reach a qué distancia está {@code block} (o el alcance si no hay bloque): lo de más allá está tapado
     * @param now   para reconocer el segundo clic de un doble clic
     *
     * @return la caja de ahora, o {@code null} si el clic no hizo nada (al aire, sin selección que agarrar)
     */
    public Box press(GizmoMath.Ray ray, int[] block, double reach, long now) {
        moved = false;
        // El segundo clic de un doble clic cae sobre la caja de un bloque que dejó el primero: es un clic, no tirar.
        boolean again = block != null && lastBlock != null && java.util.Arrays.equals(block, lastBlock)
                && now - lastClick <= DOUBLE_CLICK_MILLIS;
        Face under = again ? null : faceUnder(ray, reach);
        if (under != null) {
            GizmoMath.AxisHit hit = GizmoMath.axis(ray, box.faceCenter(under), under.axis());
            if (hit != null) {
                state = State.PULLING;
                face = under;
                pulledFrom = box;
                grip = hit.along();
                return box;
            }
        }
        if (block == null) {
            return null;
        }
        state = State.DRAWING;
        anchor = block.clone();
        box = Box.between(anchor, anchor);
        return box;
    }

    /**
     * Arrastrar con el botón pulsado. Dibujando, la caja va del bloque pulsado al de ahora; tirando de una cara, esa
     * cara sigue al cursor a lo largo de su eje (de bloque en bloque).
     *
     * @return la caja nueva si cambió, o {@code null}
     */
    public Box drag(GizmoMath.Ray ray, int[] block) {
        Box next = switch (state) {
            case IDLE -> null;
            case DRAWING -> block == null ? null : Box.between(anchor, block);
            case PULLING -> {
                GizmoMath.AxisHit hit = GizmoMath.axis(ray, pulledFrom.faceCenter(face), face.axis());
                yield hit == null ? null : pulledFrom.withEdge(face, pulledFrom.edge(face)
                        + GizmoMath.steps(hit.along() - grip));
            }
        };
        if (next == null || next.equals(box)) {
            return null;
        }
        moved = true;
        box = next;
        return box;
    }

    /**
     * Soltar. Devuelve lo que hay que hacer: la caja que queda, o que el clic fue doble sobre el mismo bloque sin
     * arrastrar (selección mágica de {@code block}).
     */
    public Result release(int[] block, long now) {
        State was = state;
        state = State.IDLE;
        boolean click = !moved && was == State.DRAWING;
        boolean twice = click && block != null && lastBlock != null && java.util.Arrays.equals(block, lastBlock)
                && now - lastClick <= DOUBLE_CLICK_MILLIS;
        if (click) {
            lastClick = twice ? Long.MIN_VALUE / 2 : now;
            lastBlock = twice ? null : block == null ? null : block.clone();
        }
        return new Result(box, twice);
    }

    /**
     * Al soltar: la caja que queda y si fue un doble clic (entonces se pide la selección mágica del bloque).
     */
    public record Result(Box box, boolean magic) {
    }

    /** Por dónde entra un rayo en la caja y a qué distancia. */
    record Hit(Face face, double distance) {
    }

    /** La cara por la que entra el rayo en la caja; si el rayo sale de dentro, la cara por la que sale. */
    static Hit entry(GizmoMath.Ray ray, Box box) {
        double[] origin = {ray.origin().x(), ray.origin().y(), ray.origin().z()};
        double[] direction = {ray.direction().x(), ray.direction().y(), ray.direction().z()};
        double[] low = {box.minX(), box.minY(), box.minZ()};
        double[] high = {box.maxX() + 1.0, box.maxY() + 1.0, box.maxZ() + 1.0};
        double near = Double.NEGATIVE_INFINITY;
        double far = Double.POSITIVE_INFINITY;
        int nearAxis = -1;
        int farAxis = -1;
        int nearSign = 0;
        int farSign = 0;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < 1.0E-9) {
                if (origin[axis] < low[axis] || origin[axis] > high[axis]) {
                    return null;
                }
                continue;
            }
            double toLow = (low[axis] - origin[axis]) / direction[axis];
            double toHigh = (high[axis] - origin[axis]) / direction[axis];
            // Entra por el lado bajo si va hacia arriba en ese eje, y sale por el alto.
            boolean rising = direction[axis] > 0;
            double enter = rising ? toLow : toHigh;
            double exit = rising ? toHigh : toLow;
            if (enter > near) {
                near = enter;
                nearAxis = axis;
                nearSign = rising ? -1 : 1;
            }
            if (exit < far) {
                far = exit;
                farAxis = axis;
                farSign = rising ? 1 : -1;
            }
        }
        if (near > far || far < 0.0) {
            return null;
        }
        if (near >= 0.0 && nearAxis >= 0) {
            return new Hit(Face.of(nearAxis, nearSign), near);
        }
        return farAxis >= 0 ? new Hit(Face.of(farAxis, farSign), far) : null;
    }
}
