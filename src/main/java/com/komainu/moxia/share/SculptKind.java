package com.komainu.moxia.share;

import com.google.gson.JsonObject;
import com.komainu.moxia.persistence.SculptPresetStore;
import com.komainu.moxia.sculpt.BrushMask;
import com.komainu.moxia.sculpt.SculptProfile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Presets de escultura ({@code /komaedit sculpt preset save}). Datos: el mismo JSON que se guarda en
 * {@code config/komaedit/presets}. Al importarlo se comprueban los números con sus rangos, el patrón y los bloques de
 * la máscara.
 */
public final class SculptKind implements ShareKind {
    /** Dónde están los presets; en el juego, {@link SculptPresetStore}. */
    public interface Store {
        Store GAME = new Store() {
            @Override
            public List<String> names(UUID player) {
                return SculptPresetStore.list(player);
            }

            @Override
            public SculptProfile load(UUID player, String name) {
                return SculptPresetStore.load(player, name);
            }

            @Override
            public boolean exists(UUID player, String name) {
                return SculptPresetStore.exists(player, name);
            }

            @Override
            public void save(UUID player, String name, SculptProfile profile) {
                SculptPresetStore.save(player, name, profile);
            }
        };

        List<String> names(UUID player);

        SculptProfile load(UUID player, String name);

        boolean exists(UUID player, String name);

        void save(UUID player, String name, SculptProfile profile);
    }

    private static final int MAX_MASK_BLOCKS = 256;

    private final Store store;

    public SculptKind(Store store) {
        this.store = store;
    }

    @Override
    public String id() {
        return "sculpt";
    }

    @Override
    public List<String> aliases() {
        return List.of("escultura", "esculpir", "sculpts");
    }

    @Override
    public String singular() {
        return "preset de escultura";
    }

    @Override
    public String plural() {
        return "presets de escultura";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String permission() {
        return "komaedit.build.brush.sculpt";
    }

    @Override
    public List<String> names(ShareTarget target) {
        return store.names(target.id());
    }

    @Override
    public SharePack.Item export(ShareTarget target, String name) {
        String key = normalizeName(name);
        return new SharePack.Item(id(), key, version(), SculptPresetStore.toJson(store.load(target.id(), key)));
    }

    @Override
    public SharePack.Item check(SharePack.Item item, ShareCheck check) {
        if (item.version() > version()) {
            throw new IllegalArgumentException("Es de una versión más nueva de KomaEdit");
        }
        SculptProfile profile;
        try {
            profile = SculptPresetStore.fromJson(item.data());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Le faltan ajustes o no tienen el tipo que toca");
        }
        BrushMask mask = profile.settings().mask();
        BrushMask checked = new BrushMask(mask.ignoreAir(), mask.ignoreLiquids(), blocks(mask.whitelist(), check),
                blocks(mask.blacklist(), check), blocks(mask.replaceOnly(), check));
        SculptProfile result = new SculptProfile(profile.settings().withMask(checked),
                check.pattern("El patrón", profile.pattern()));
        return new SharePack.Item(id(), normalizeName(item.name()), version(), SculptPresetStore.toJson(result));
    }

    private static Set<String> blocks(Set<String> ids, ShareCheck check) {
        if (ids.size() > MAX_MASK_BLOCKS) {
            throw new IllegalArgumentException("La máscara pasa de " + MAX_MASK_BLOCKS + " bloques");
        }
        Set<String> result = new LinkedHashSet<>();
        for (String id : ids) {
            String block = check.block(ShareText.setting("Un bloque de la máscara", id, 128));
            int bracket = block.indexOf('[');
            result.add(bracket < 0 ? block : block.substring(0, bracket));
        }
        return result;
    }

    @Override
    public String normalizeName(String name) {
        return SculptPresetStore.validName(name);
    }

    @Override
    public boolean exists(ShareTarget target, String name) {
        return store.exists(target.id(), name);
    }

    @Override
    public String save(ShareTarget target, SharePack.Item item, String name, boolean replace) {
        boolean existed = store.exists(target.id(), name);
        if (existed && !replace) {
            throw new IllegalArgumentException("Ya tienes un preset de escultura «" + name + "»");
        }
        JsonObject data = item.data();
        store.save(target.id(), name, SculptPresetStore.fromJson(data));
        return existed ? "se sustituyó" : "se guardó";
    }
}
