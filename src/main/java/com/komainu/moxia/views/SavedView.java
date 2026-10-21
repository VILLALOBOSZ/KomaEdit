package com.komainu.moxia.views;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Una vista guardada: dónde estaba la cámara y hacia dónde miraba, con un nombre. Las privadas viven en el cliente
 * (una lista por mundo o servidor); las compartidas, en el servidor, con su autor.
 *
 * @param dimension la dimensión ({@code minecraft:overworld}…)
 * @param pinned    «fija»: la tecla de la vista fija lleva a ella y, al volver a pulsarla, a donde estabas
 * @param created   cuándo se guardó (o se compartió), en milisegundos desde 1970
 * @param author    quién la compartió (vacío en las privadas)
 * @param authorId  su UUID ({@code null} en las privadas)
 */
public record SavedView(String name, String dimension, double x, double y, double z, float yaw, float pitch,
                        boolean pinned, long created, String author, UUID authorId) {
    public SavedView {
        name = ViewNames.clean(name);
        author = author == null ? "" : author;
        yaw = wrapYaw(yaw);
        pitch = Math.clamp(pitch, -90.0F, 90.0F);
    }

    public static SavedView here(String name, String dimension, double x, double y, double z, float yaw, float pitch,
                                 long now) {
        return new SavedView(name, dimension, x, y, z, yaw, pitch, false, now, "", null);
    }

    public SavedView withPinned(boolean value) {
        return new SavedView(name, dimension, x, y, z, yaw, pitch, value, created, author, authorId);
    }

    public SavedView withName(String value) {
        return new SavedView(value, dimension, x, y, z, yaw, pitch, pinned, created, author, authorId);
    }

    /** La misma vista compartida por {@code author}. */
    public SavedView sharedBy(String author, UUID authorId, long now) {
        return new SavedView(name, dimension, x, y, z, yaw, pitch, false, now, author, authorId);
    }

    public boolean shared() {
        return authorId != null;
    }

    /** Distancia al cuadrado a un punto de la misma dimensión. */
    public double distanceSquared(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Guiñada entre -180 y 180, como la guarda Minecraft. */
    public static float wrapYaw(float yaw) {
        if (!Float.isFinite(yaw)) {
            return 0.0F;
        }
        float wrapped = yaw % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("dimension", dimension);
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        json.addProperty("yaw", yaw);
        json.addProperty("pitch", pitch);
        if (pinned) {
            json.addProperty("pinned", true);
        }
        json.addProperty("created", created);
        if (authorId != null) {
            json.addProperty("author", author);
            json.addProperty("authorId", authorId.toString());
        }
        return json;
    }

    /** Lee una vista; lanza si le falta algo o algo no vale (quien lee la salta y sigue). */
    static SavedView fromJson(JsonObject json) {
        double x = json.get("x").getAsDouble();
        double y = json.get("y").getAsDouble();
        double z = json.get("z").getAsDouble();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Vista con coordenadas no válidas");
        }
        String name = json.get("name").getAsString();
        ViewNames.validate(name);
        String dimension = json.get("dimension").getAsString();
        ViewNames.validateDimension(dimension);
        return new SavedView(name, dimension, x, y, z,
                json.has("yaw") ? json.get("yaw").getAsFloat() : 0.0F,
                json.has("pitch") ? json.get("pitch").getAsFloat() : 0.0F,
                json.has("pinned") && json.get("pinned").getAsBoolean(),
                json.has("created") ? json.get("created").getAsLong() : 0L,
                json.has("author") ? json.get("author").getAsString() : "",
                json.has("authorId") ? UUID.fromString(json.get("authorId").getAsString()) : null);
    }
}
