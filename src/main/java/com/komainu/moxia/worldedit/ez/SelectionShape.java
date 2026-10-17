package com.komainu.moxia.worldedit.ez;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Una selección de WorldEdit reducida a datos: tipo, puntos en orden, radios y alturas. Sirve para guardarla, moverla,
 * invertirla o quitarle el último punto sin depender del mundo; {@code SelectionBridge} la convierte en el selector
 * de WorldEdit y al revés.
 *
 * <ul>
 *     <li>{@code CUBOID} y {@code EXTEND}: {@code points} son pos1 y pos2.</li>
 *     <li>{@code POLYGON}: los vértices en planta (la Y no cuenta) y la altura de {@code minY} a {@code maxY}.</li>
 *     <li>{@code CONVEX}: los vértices en el orden en que se marcaron.</li>
 *     <li>{@code ELLIPSOID} y {@code SPHERE}: el centro y los radios.</li>
 *     <li>{@code CYLINDER}: el centro (su Y no cuenta), los radios X y Z y la altura.</li>
 * </ul>
 *
 * @param world nombre del mundo donde se marcó; puede ser {@code null}
 */
public record SelectionShape(
        Kind kind,
        List<BlockVector3> points,
        double radiusX,
        double radiusY,
        double radiusZ,
        int minY,
        int maxY,
        String world
) {
    private static final int FORMAT_VERSION = 1;
    public static final int MAX_POINTS = 1_024;

    public SelectionShape {
        points = List.copyOf(points);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("La selección no tiene puntos");
        }
        if (points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("La selección tiene demasiados puntos");
        }
        if (minY > maxY) {
            int swap = minY;
            minY = maxY;
            maxY = swap;
        }
    }

    public enum Kind {
        CUBOID, EXTEND, POLYGON, CONVEX, ELLIPSOID, SPHERE, CYLINDER;

        public boolean isRound() {
            return this == ELLIPSOID || this == SPHERE || this == CYLINDER;
        }

        public boolean isPointList() {
            return this == POLYGON || this == CONVEX;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Anchor {
        POS1, POS2, CENTER;

        public static Anchor byName(String input) {
            return switch (input == null ? "pos1" : input.trim().toLowerCase(Locale.ROOT)) {
                case "pos1", "1", "primero", "first" -> POS1;
                case "pos2", "2", "ultimo", "último", "last" -> POS2;
                case "center", "centre", "centro", "c" -> CENTER;
                default -> throw new IllegalArgumentException("Punto desconocido: " + input + " (pos1, pos2, center)");
            };
        }
    }

    public static SelectionShape cuboid(BlockVector3 first, BlockVector3 second, String world) {
        return new SelectionShape(Kind.CUBOID, List.of(first, second), 0, 0, 0, 0, 0, world);
    }

    public BlockVector3 minimum() {
        return switch (kind) {
            case ELLIPSOID, SPHERE -> center().subtract(radiusBlocks(radiusX), radiusBlocks(radiusY),
                    radiusBlocks(radiusZ));
            case CYLINDER -> BlockVector3.at(center().x() - radiusBlocks(radiusX), minY,
                    center().z() - radiusBlocks(radiusZ));
            case POLYGON -> withY(pointsMinimum(), minY);
            default -> pointsMinimum();
        };
    }

    public BlockVector3 maximum() {
        return switch (kind) {
            case ELLIPSOID, SPHERE -> center().add(radiusBlocks(radiusX), radiusBlocks(radiusY),
                    radiusBlocks(radiusZ));
            case CYLINDER -> BlockVector3.at(center().x() + radiusBlocks(radiusX), maxY,
                    center().z() + radiusBlocks(radiusZ));
            case POLYGON -> withY(pointsMaximum(), maxY);
            default -> pointsMaximum();
        };
    }

    /** Tamaño de la caja que envuelve la selección, en bloques por eje. */
    public BlockVector3 size() {
        return maximum().subtract(minimum()).add(1, 1, 1);
    }

    public BlockVector3 center() {
        if (kind.isRound()) {
            BlockVector3 center = points.getFirst();
            return kind == Kind.CYLINDER ? withY(center, Math.floorDiv(minY + maxY, 2)) : center;
        }
        BlockVector3 minimum = minimum();
        BlockVector3 maximum = maximum();
        return BlockVector3.at(Math.floorDiv(minimum.x() + maximum.x(), 2),
                Math.floorDiv(minimum.y() + maximum.y(), 2), Math.floorDiv(minimum.z() + maximum.z(), 2));
    }

    public BlockVector3 anchor(Anchor anchor) {
        if (kind.isRound()) {
            return center();
        }
        return switch (anchor) {
            case POS1 -> kind == Kind.POLYGON ? withY(points.getFirst(), minY) : points.getFirst();
            case POS2 -> kind == Kind.POLYGON ? withY(points.getLast(), minY) : points.getLast();
            case CENTER -> center();
        };
    }

    public SelectionShape translate(BlockVector3 delta) {
        List<BlockVector3> moved = new ArrayList<>(points.size());
        for (BlockVector3 point : points) {
            moved.add(point.add(delta));
        }
        return new SelectionShape(kind, moved, radiusX, radiusY, radiusZ, minY + delta.y(), maxY + delta.y(), world);
    }

    /** Lleva el punto elegido a {@code target}; el resto se mueve igual. */
    public SelectionShape moveTo(Anchor anchor, BlockVector3 target) {
        return translate(target.subtract(anchor(anchor)));
    }

    /**
     * {@code //next}: desplaza la selección su propio tamaño en una dirección, más {@code gap} bloques de hueco.
     */
    public SelectionShape next(BlockVector3 direction, int gap) {
        BlockVector3 size = size();
        BlockVector3 step = BlockVector3.at(
                direction.x() * (size.x() + gap),
                direction.y() * (size.y() + gap),
                direction.z() * (size.z() + gap));
        return translate(step);
    }

    /** Invierte el orden de los puntos (en un cubo intercambia pos1 y pos2). */
    public SelectionShape inverted() {
        if (kind.isRound()) {
            return this;
        }
        List<BlockVector3> reversed = new ArrayList<>(points);
        Collections.reverse(reversed);
        return new SelectionShape(kind, reversed, radiusX, radiusY, radiusZ, minY, maxY, world);
    }

    /** Quita el último punto secundario de una selección convex o poly. */
    public SelectionShape withoutLastPoint() {
        if (!kind.isPointList()) {
            throw new IllegalArgumentException("Solo se puede quitar el último punto de una selección convex o poly");
        }
        if (points.size() <= 1) {
            throw new IllegalArgumentException("La selección ya solo tiene su primer punto");
        }
        return new SelectionShape(kind, points.subList(0, points.size() - 1), radiusX, radiusY, radiusZ, minY, maxY,
                world);
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("type", kind.id());
        if (world != null) {
            root.addProperty("world", world);
        }
        JsonArray array = new JsonArray();
        for (BlockVector3 point : points) {
            JsonArray coordinates = new JsonArray();
            coordinates.add(point.x());
            coordinates.add(point.y());
            coordinates.add(point.z());
            array.add(coordinates);
        }
        root.add("points", array);
        if (kind.isRound()) {
            root.addProperty("radiusX", radiusX);
            root.addProperty("radiusY", radiusY);
            root.addProperty("radiusZ", radiusZ);
        }
        if (kind == Kind.POLYGON || kind == Kind.CYLINDER) {
            root.addProperty("minY", minY);
            root.addProperty("maxY", maxY);
        }
        return root;
    }

    public static SelectionShape fromJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.get("version").getAsInt() != FORMAT_VERSION) {
                throw new IllegalArgumentException("Versión de selección no compatible");
            }
            Kind kind = Kind.valueOf(root.get("type").getAsString().toUpperCase(Locale.ROOT));
            List<BlockVector3> points = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("points")) {
                JsonArray coordinates = element.getAsJsonArray();
                points.add(BlockVector3.at(coordinates.get(0).getAsInt(), coordinates.get(1).getAsInt(),
                        coordinates.get(2).getAsInt()));
            }
            return new SelectionShape(kind, points,
                    number(root, "radiusX"), number(root, "radiusY"), number(root, "radiusZ"),
                    (int) number(root, "minY"), (int) number(root, "maxY"),
                    root.has("world") ? root.get("world").getAsString() : null);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("La selección guardada está dañada");
        }
    }

    public String describe() {
        BlockVector3 size = size();
        return kind.id() + " " + size.x() + "×" + size.y() + "×" + size.z();
    }

    private static double number(JsonObject root, String key) {
        return root.has(key) ? root.get(key).getAsDouble() : 0.0;
    }

    private static int radiusBlocks(double radius) {
        return (int) Math.round(radius);
    }

    private BlockVector3 pointsMinimum() {
        BlockVector3 result = points.getFirst();
        for (BlockVector3 point : points) {
            result = result.getMinimum(point);
        }
        return result;
    }

    private BlockVector3 pointsMaximum() {
        BlockVector3 result = points.getFirst();
        for (BlockVector3 point : points) {
            result = result.getMaximum(point);
        }
        return result;
    }

    private static BlockVector3 withY(BlockVector3 point, int y) {
        return BlockVector3.at(point.x(), y, point.z());
    }
}
