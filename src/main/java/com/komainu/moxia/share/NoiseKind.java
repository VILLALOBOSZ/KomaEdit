package com.komainu.moxia.share;

import com.google.gson.JsonObject;
import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.persistence.NamedDefinitions;

import java.util.List;
import java.util.function.Function;

/**
 * Ruidos guardados ({@code /komaedit noise save}). Datos: {@code {"t": "Perlin(f:0.02)"}}. Solo son números y nombres
 * de ruido: se comprueban con el mismo lector que la orden, sin ruidos guardados de nadie.
 */
public final class NoiseKind extends NamedKind {
    public NoiseKind(Function<ShareTarget, NamedDefinitions> store) {
        super(store);
    }

    @Override
    public String id() {
        return "noise";
    }

    @Override
    public List<String> aliases() {
        return List.of("ruido", "ruidos", "noises");
    }

    @Override
    public String singular() {
        return "ruido";
    }

    @Override
    public String plural() {
        return "ruidos";
    }

    @Override
    public String permission() {
        return "komaedit.noise.save";
    }

    @Override
    public String normalizeName(String name) {
        String key = super.normalizeName(name);
        if (NoiseSpec.isReservedName(key)) {
            throw new IllegalArgumentException("«" + key + "» es un ruido de serie: elige otro nombre");
        }
        return key;
    }

    @Override
    JsonObject data(String value) {
        JsonObject data = new JsonObject();
        data.addProperty("t", value);
        return data;
    }

    @Override
    String value(JsonObject data, ShareCheck check) {
        String text = ShareText.setting("El ruido", string(data, "t"), NamedDefinitions.MAX_VALUE_LENGTH);
        return NoiseSpec.parse(text, null).toString();
    }

    @Override
    String text(JsonObject data) {
        return string(data, "t");
    }
}
