package com.komainu.moxia.notes;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Una nota en el mundo: texto anclado a un bloque o a un punto, con color, icono y la distancia desde la que se ve.
 * Las guarda el servidor; las privadas solo las recibe su autor y las del equipo, quien tiene permiso para verlas.
 *
 * @param id        seis letras o números, único en el mundo
 * @param block     anclada a un bloque ({@code x/y/z} son su esquina); si no, a un punto
 * @param side      la cara del bloque en la que se puso (como {@code Direction}: 0 abajo, 1 arriba, 2 norte, 3 sur,
 *                  4 oeste, 5 este): el rótulo sale de esa cara hacia fuera
 * @param distance  bloques desde los que se ve en el mundo
 * @param team      del equipo (si no, privada)
 * @param created   cuándo se puso (milisegundos desde 1970)
 * @param resolvedAt cuándo se resolvió (0 si sigue abierta)
 */
public record WorldNote(String id, String text, int color, NoteIcon icon, String dimension, double x, double y,
                        double z, boolean block, int side, int distance, boolean team, UUID authorId, String author,
                        long created, String resolvedBy, long resolvedAt) {
    public static final int DOWN = 0;
    public static final int UP = 1;
    private static final String[] SIDES = {"down", "up", "north", "south", "west", "east"};
    private static final int[][] NORMALS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    /** Lo que sale el rótulo de una cara lateral, y lo que sube o baja de la de arriba o la de abajo. */
    private static final double OUT = 1.1;

    public WorldNote {
        text = text == null ? "" : text;
        author = author == null ? "" : author;
        resolvedBy = resolvedBy == null ? "" : resolvedBy;
        icon = icon == null ? NoteIcon.NOTA : icon;
        color &= 0xFFFFFF;
        side = side >= 0 && side < SIDES.length ? side : UP;
    }

    public boolean resolved() {
        return resolvedAt > 0;
    }

    /** La normal de la cara en la que se puso: {x, y, z}. */
    public int[] normal() {
        return NORMALS[side].clone();
    }

    /** Si se puso en una cara lateral (una pared). */
    public boolean onWall() {
        return block && side > UP;
    }

    /**
     * Donde se dibuja el rótulo: encima del punto; en un bloque, fuera de la cara en la que se puso (encima si fue la
     * de arriba, delante si fue una pared).
     */
    public double labelX() {
        return block ? x + 0.5 + NORMALS[side][0] * OUT : x;
    }

    public double labelY() {
        if (!block) {
            return y + 0.6;
        }
        return switch (side) {
            case UP -> y + 0.5 + OUT;
            case DOWN -> y + 0.5 - OUT;
            default -> y + 0.5;
        };
    }

    public double labelZ() {
        return block ? z + 0.5 + NORMALS[side][2] * OUT : z;
    }

    public static String sideName(int side) {
        return side >= 0 && side < SIDES.length ? SIDES[side] : SIDES[UP];
    }

    public static int sideByName(String name) {
        for (int index = 0; index < SIDES.length; index++) {
            if (SIDES[index].equals(name)) {
                return index;
            }
        }
        return UP;
    }

    public WorldNote resolve(String by, long now) {
        return new WorldNote(id, text, color, icon, dimension, x, y, z, block, side, distance, team, authorId,
                author, created, by, Math.max(1L, now));
    }

    public WorldNote reopen() {
        return new WorldNote(id, text, color, icon, dimension, x, y, z, block, side, distance, team, authorId,
                author, created, "", 0L);
    }

    /** ¿La ve este jugador? Las privadas, solo su autor; las del equipo, quien puede ver las del equipo. */
    public boolean visibleTo(UUID viewer, boolean seesTeam) {
        return authorId.equals(viewer) || team && seesTeam;
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("text", text);
        json.addProperty("color", NoteColor.name(color));
        json.addProperty("icon", icon.id());
        json.addProperty("dimension", dimension);
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        json.addProperty("block", block);
        if (block && side != UP) {
            json.addProperty("side", sideName(side));
        }
        json.addProperty("distance", distance);
        json.addProperty("team", team);
        json.addProperty("authorId", authorId.toString());
        json.addProperty("author", author);
        json.addProperty("created", created);
        if (resolved()) {
            json.addProperty("resolvedBy", resolvedBy);
            json.addProperty("resolvedAt", resolvedAt);
        }
        return json;
    }

    static WorldNote fromJson(JsonObject json) {
        double x = json.get("x").getAsDouble();
        double y = json.get("y").getAsDouble();
        double z = json.get("z").getAsDouble();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Nota con coordenadas no válidas");
        }
        String id = json.get("id").getAsString();
        if (!NoteIds.valid(id)) {
            throw new IllegalArgumentException("Nota con id no válido: " + id);
        }
        return new WorldNote(id, json.get("text").getAsString(),
                json.has("color") ? NoteColor.parse(json.get("color").getAsString()) : NoteColor.AMARILLO.rgb(),
                json.has("icon") ? NoteIcon.byId(json.get("icon").getAsString()) : NoteIcon.NOTA,
                json.get("dimension").getAsString(), x, y, z,
                json.has("block") && json.get("block").getAsBoolean(),
                json.has("side") ? sideByName(json.get("side").getAsString()) : UP,
                json.has("distance") ? json.get("distance").getAsInt() : NoteLimits.DEFAULT_DISTANCE,
                json.has("team") && json.get("team").getAsBoolean(),
                UUID.fromString(json.get("authorId").getAsString()),
                json.has("author") ? json.get("author").getAsString() : "",
                json.has("created") ? json.get("created").getAsLong() : 0L,
                json.has("resolvedBy") ? json.get("resolvedBy").getAsString() : "",
                json.has("resolvedAt") ? json.get("resolvedAt").getAsLong() : 0L);
    }
}
