package com.komainu.moxia.share;

import com.komainu.moxia.blueprint.BlueprintService;
import com.komainu.moxia.gradient.SavedPalettes;
import com.komainu.moxia.nature.NaturePresets;
import com.komainu.moxia.noise.SavedNoises;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Los tipos que se pueden compartir. Los de serie se registran solos; otro módulo añade el suyo al arrancar con
 * {@link #register} (por ejemplo, las barras guardadas: {@code ShareKinds.register(new HotbarKind(…))}).
 */
public final class ShareKinds {
    private static final Registry REGISTRY = new Registry();

    static {
        REGISTRY.register(new PaletteKind(target -> SavedPalettes.store(target.id())));
        REGISTRY.register(new NoiseKind(target -> SavedNoises.store(target.id())));
        REGISTRY.register(NatureKind.tree(target -> NaturePresets.trees(target.id())));
        REGISTRY.register(NatureKind.rock(target -> NaturePresets.rocks(target.id())));
        REGISTRY.register(new SculptKind(SculptKind.Store.GAME));
        REGISTRY.register(new RecipeKind(RecipeKind.Store.GAME));
        REGISTRY.register(new BlueprintKind(BlueprintService::library, BlueprintService::importShared));
    }

    private ShareKinds() {
    }

    /** Añade un tipo. Su id y sus alias no pueden coincidir con los de otro. */
    public static void register(ShareKind kind) {
        REGISTRY.register(kind);
    }

    /** El tipo por su id o un alias ({@code paleta}), o {@code null}. */
    public static ShareKind find(String name) {
        return REGISTRY.find(name);
    }

    public static List<ShareKind> all() {
        return REGISTRY.all();
    }

    /** Los ids, para los mensajes. */
    public static List<String> names() {
        return all().stream().map(ShareKind::id).toList();
    }

    /** Un registro de tipos. El del mod es único; las pruebas montan el suyo. */
    public static final class Registry {
        private final Map<String, ShareKind> byName = new LinkedHashMap<>();
        private final List<ShareKind> kinds = new ArrayList<>();

        public synchronized void register(ShareKind kind) {
            List<String> names = new ArrayList<>();
            names.add(kind.id());
            kind.aliases().forEach(alias -> names.add(alias.toLowerCase(Locale.ROOT)));
            for (String name : names) {
                if (byName.containsKey(name)) {
                    throw new IllegalArgumentException("Ya hay un tipo compartible llamado " + name);
                }
            }
            names.forEach(name -> byName.put(name, kind));
            kinds.add(kind);
        }

        public synchronized ShareKind find(String name) {
            return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
        }

        public synchronized List<ShareKind> all() {
            return List.copyOf(kinds);
        }
    }
}
