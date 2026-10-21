package com.komainu.moxia.share;

import com.google.gson.JsonObject;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.recipe.RecipeStore;

import java.util.List;
import java.util.UUID;

/**
 * Recetas del Studio. Datos: el mismo JSON que se guarda, con su propia {@code version} (las de antes se migran al
 * leerlas). La fórmula de la figura Expresión pasa por el compilador de {@code TubeFormula}, que solo conoce números,
 * variables de la figura y funciones matemáticas; los materiales, por la comprobación de bloques.
 */
public final class RecipeKind implements ShareKind {
    /** Dónde están las recetas; en el juego, {@link RecipeStore}. */
    public interface Store {
        Store GAME = new Store() {
            @Override
            public List<String> names(UUID player) {
                return RecipeStore.list(player).stream().map(ProceduralRecipe::name).toList();
            }

            @Override
            public ProceduralRecipe load(UUID player, String name) {
                return RecipeStore.load(player, name);
            }

            @Override
            public boolean exists(UUID player, String name) {
                return RecipeStore.exists(player, name);
            }

            @Override
            public void save(UUID player, ProceduralRecipe recipe) {
                RecipeStore.save(player, recipe);
            }
        };

        List<String> names(UUID player);

        ProceduralRecipe load(UUID player, String name);

        boolean exists(UUID player, String name);

        void save(UUID player, ProceduralRecipe recipe);
    }

    private final Store store;

    public RecipeKind(Store store) {
        this.store = store;
    }

    /** Una receta de un {@code .krecipe} de antes. */
    public static SharePack.Item legacyItem(JsonObject recipe) {
        String name = ShareCodec.string(recipe, "name");
        return new SharePack.Item("recipe", name.isEmpty() ? "receta" : name, 1, recipe);
    }

    @Override
    public String id() {
        return "recipe";
    }

    @Override
    public List<String> aliases() {
        return List.of("receta", "recetas", "recipes");
    }

    @Override
    public String singular() {
        return "receta";
    }

    @Override
    public String plural() {
        return "recetas";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String permission() {
        return "komaedit.recipe";
    }

    @Override
    public List<String> names(ShareTarget target) {
        return store.names(target.id());
    }

    @Override
    public SharePack.Item export(ShareTarget target, String name) {
        ProceduralRecipe recipe = store.load(target.id(), normalizeName(name));
        return new SharePack.Item(id(), recipe.name(), version(), RecipeStore.toJson(recipe));
    }

    @Override
    public SharePack.Item check(SharePack.Item item, ShareCheck check) {
        if (item.version() > version()) {
            throw new IllegalArgumentException("Es de una versión más nueva de KomaEdit");
        }
        JsonObject data = item.data();
        int format;
        try {
            format = data.get("version").getAsInt();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("A la receta le falta su versión");
        }
        if (format < 1 || format > ProceduralRecipe.FORMAT_VERSION) {
            throw new IllegalArgumentException("Receta de una versión de KomaEdit que no se conoce (" + format + ")");
        }
        String name = normalizeName(item.name());
        data.addProperty("name", name);
        for (String key : new String[]{"pattern", "accentPattern"}) {
            if (data.has(key)) {
                String text = ShareCodec.string(data, key);
                data.addProperty(key, check.pattern(key.equals("pattern") ? "El material" : "El material secundario",
                        text));
            }
        }
        if (data.has("formula")) {
            check.formula(ShareCodec.string(data, "formula"));
        }
        ProceduralRecipe recipe = RecipeStore.fromJson(data, format);
        return new SharePack.Item(id(), name, version(), RecipeStore.toJson(recipe));
    }

    @Override
    public String normalizeName(String name) {
        if (name == null || !ProceduralRecipe.VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("El nombre debe usar 1–32 letras, números, _ o -");
        }
        return name;
    }

    @Override
    public boolean exists(ShareTarget target, String name) {
        return store.exists(target.id(), name);
    }

    @Override
    public String save(ShareTarget target, SharePack.Item item, String name, boolean replace) {
        boolean existed = store.exists(target.id(), name);
        if (existed && !replace) {
            throw new IllegalArgumentException("Ya tienes una receta «" + name + "»");
        }
        JsonObject data = item.data();
        data.addProperty("name", name);
        store.save(target.id(), RecipeStore.fromJson(data, data.get("version").getAsInt()));
        return existed ? "se sustituyó" : "se guardó";
    }
}
