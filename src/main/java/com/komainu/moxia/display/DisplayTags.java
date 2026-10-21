package com.komainu.moxia.display;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Lo que KomaEdit apunta en las etiquetas de cada display ({@code Tags} del NBT, las de {@code /tag}): que lo creó
 * KomaEdit, quién y a qué grupo pertenece. Van en el propio display, así sobreviven a reinicios, se copian al duplicar
 * y vuelven con un //undo. Lógica pura sobre las etiquetas.
 */
public final class DisplayTags {
    /** Creado con el editor de KomaEdit. */
    public static final String MARK = "komaedit.display";
    public static final String OWNER_PREFIX = "komaedit.owner.";
    public static final String GROUP_PREFIX = "komaedit.group.";

    private DisplayTags() {
    }

    public static String owner(UUID player) {
        return OWNER_PREFIX + player.toString().toLowerCase(Locale.ROOT);
    }

    /** El dueño apuntado, si lo hay. */
    public static Optional<UUID> ownerOf(Collection<String> tags) {
        for (String tag : tags) {
            if (tag.startsWith(OWNER_PREFIX)) {
                try {
                    return Optional.of(UUID.fromString(tag.substring(OWNER_PREFIX.length())));
                } catch (IllegalArgumentException ignored) {
                    // Etiqueta escrita a mano: no cuenta.
                }
            }
        }
        return Optional.empty();
    }

    /** El grupo del display, si tiene. */
    public static Optional<String> groupOf(Collection<String> tags) {
        for (String tag : tags) {
            if (tag.startsWith(GROUP_PREFIX) && tag.length() > GROUP_PREFIX.length()) {
                return Optional.of(tag.substring(GROUP_PREFIX.length()));
            }
        }
        return Optional.empty();
    }

    public static String group(String id) {
        return GROUP_PREFIX + id;
    }

    /** Un id de grupo nuevo: ocho cifras hexadecimales. */
    public static String newGroup(RandomGenerator random) {
        return String.format(Locale.ROOT, "%08x", random.nextInt());
    }

    /**
     * ¿Puede tocarlo el jugador? Los suyos siempre; los de otros (o los que no creó KomaEdit) solo con el permiso de
     * editar los de los demás.
     */
    public static boolean editable(Collection<String> tags, UUID player, boolean others) {
        if (others) {
            return true;
        }
        return ownerOf(tags).map(player::equals).orElse(false);
    }
}
