package com.komainu.moxia.share;

import com.google.gson.JsonObject;
import com.komainu.moxia.persistence.NamedDefinitions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Lo común de lo que se guarda como texto con nombre en {@link NamedDefinitions} (paletas, ruidos, presets de árbol y
 * de roca): cada tipo solo dice cómo pasa su texto al paquete y cómo lo comprueba al volver.
 */
abstract class NamedKind implements ShareKind {
    private final Function<ShareTarget, NamedDefinitions> store;

    NamedKind(Function<ShareTarget, NamedDefinitions> store) {
        this.store = store;
    }

    /** Lo guardado ({@code a;b;c}) en los datos del paquete. */
    abstract JsonObject data(String value);

    /**
     * Los datos de un elemento recibido, comprobados, en el texto que se guarda.
     *
     * @throws IllegalArgumentException si no valen
     */
    abstract String value(JsonObject data, ShareCheck check);

    @Override
    public int version() {
        return 1;
    }

    @Override
    public List<String> names(ShareTarget target) {
        return new ArrayList<>(store.apply(target).all().keySet());
    }

    @Override
    public SharePack.Item export(ShareTarget target, String name) {
        String key = normalizeName(name);
        String value = store.apply(target).get(key).orElseThrow(() -> new IllegalArgumentException(
                "«" + key + "» no está entre tus " + plural()));
        return new SharePack.Item(id(), key, version(), data(value));
    }

    @Override
    public SharePack.Item check(SharePack.Item item, ShareCheck check) {
        if (item.version() > version()) {
            throw new IllegalArgumentException("Es de una versión más nueva de KomaEdit");
        }
        String value = value(item.data(), check);
        if (value.length() > NamedDefinitions.MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Pasa de " + NamedDefinitions.MAX_VALUE_LENGTH + " caracteres");
        }
        return new SharePack.Item(id(), normalizeName(item.name()), version(), data(value));
    }

    @Override
    public String normalizeName(String name) {
        return NamedDefinitions.normalizeName(name);
    }

    @Override
    public boolean exists(ShareTarget target, String name) {
        return store.apply(target).get(name).isPresent();
    }

    @Override
    public String save(ShareTarget target, SharePack.Item item, String name, boolean replace) {
        boolean replaced = store.apply(target).put(name, text(item.data()), replace);
        return replaced ? "se sustituyó" : "se guardó";
    }

    /** El texto que se guarda a partir de los datos ya comprobados. */
    abstract String text(JsonObject data);

    static String string(JsonObject data, String key) {
        return ShareCodec.string(data, key);
    }
}
