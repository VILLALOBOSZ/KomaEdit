package com.komainu.moxia.hotbar;

import com.komainu.moxia.persistence.NamedDefinitions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Las barras guardadas de un jugador, en el orden en que salen en la tira y el menú, cuál está en uso y si la barra
 * que se deja se guarda sola al cambiar (como en Axiom). Es inmutable: cada cambio devuelve otra.
 *
 * <p>Límites: {@link #MAX_HOTBARS} barras, {@link #MAX_HOTBAR_BYTES} por barra y {@link #MAX_TOTAL_BYTES} entre todas
 * (medidos en JSON compacto). Caben de sobra los bloques; lo que pesa son los componentes (libros escritos, cajas de
 * shulker llenas), y el límite total mantiene el catálogo que se manda al cliente muy por debajo del tope de un
 * paquete de Minecraft.</p>
 *
 * @param active barra en uso, o {@code ""} si ninguna
 */
public record HotbarBook(List<SavedHotbar> hotbars, String active, boolean autosave) {
    public static final int MAX_HOTBARS = 64;
    public static final int MAX_HOTBAR_BYTES = 32 * 1024;
    public static final int MAX_TOTAL_BYTES = 512 * 1024;
    public static final HotbarBook EMPTY = new HotbarBook(List.of(), "", true);

    public HotbarBook {
        hotbars = List.copyOf(hotbars);
        Set<String> names = new HashSet<>();
        for (SavedHotbar hotbar : hotbars) {
            if (!names.add(hotbar.name())) {
                throw new IllegalArgumentException("Dos barras se llaman " + hotbar.name());
            }
        }
        active = active == null || !names.contains(active) ? "" : active;
    }

    /** La barra con ese nombre; vacío si no existe o el nombre no es válido. */
    public Optional<SavedHotbar> get(String name) {
        int index = indexOf(name);
        return index < 0 ? Optional.empty() : Optional.of(hotbars.get(index));
    }

    public SavedHotbar require(String name) {
        return get(name).orElseThrow(() -> new IllegalArgumentException("No tienes ninguna barra llamada "
                + key(name)));
    }

    /** Posición de la barra (desde 0), o -1 si no existe o el nombre no es válido. */
    public int indexOf(String name) {
        String key;
        try {
            key = key(name);
        } catch (IllegalArgumentException exception) {
            return -1;
        }
        for (int index = 0; index < hotbars.size(); index++) {
            if (hotbars.get(index).name().equals(key)) {
                return index;
            }
        }
        return -1;
    }

    public List<String> names() {
        return hotbars.stream().map(SavedHotbar::name).toList();
    }

    public boolean isEmpty() {
        return hotbars.isEmpty();
    }

    public int size() {
        return hotbars.size();
    }

    public Optional<SavedHotbar> activeHotbar() {
        return active.isEmpty() ? Optional.empty() : get(active);
    }

    /**
     * Guarda una barra: si ya existe una con ese nombre la sustituye en su sitio (solo con {@code overwrite}); si no,
     * va al final.
     */
    public HotbarBook saved(SavedHotbar hotbar, boolean overwrite) {
        int index = indexOf(hotbar.name());
        if (index >= 0 && !overwrite) {
            throw new IllegalArgumentException("Ya tienes una barra llamada " + hotbar.name()
                    + "; añade -f para sustituirla");
        }
        if (index < 0 && hotbars.size() >= MAX_HOTBARS) {
            throw new IllegalArgumentException("Has llegado al máximo de " + MAX_HOTBARS + " barras guardadas; "
                    + "borra alguna con /komaedit hotbar delete");
        }
        List<SavedHotbar> next = new ArrayList<>(hotbars);
        if (index >= 0) {
            next.set(index, hotbar);
        } else {
            next.add(hotbar);
        }
        return checked(new HotbarBook(next, active, autosave), hotbar);
    }

    /** La misma barra con otros huecos (lo que hace el guardado al cambiar). */
    public HotbarBook updated(SavedHotbar hotbar) {
        int index = indexOf(hotbar.name());
        if (index < 0) {
            return this;
        }
        List<SavedHotbar> next = new ArrayList<>(hotbars);
        next.set(index, hotbar);
        return checked(new HotbarBook(next, active, autosave), hotbar);
    }

    public HotbarBook without(String name) {
        int index = indexOf(name);
        if (index < 0) {
            throw new IllegalArgumentException("No tienes ninguna barra llamada " + key(name));
        }
        List<SavedHotbar> next = new ArrayList<>(hotbars);
        next.remove(index);
        return new HotbarBook(next, active, autosave);
    }

    public HotbarBook renamed(String from, String to) {
        SavedHotbar hotbar = require(from);
        String target = key(to);
        if (target.equals(hotbar.name())) {
            return this;
        }
        if (indexOf(target) >= 0) {
            throw new IllegalArgumentException("Ya tienes una barra llamada " + target);
        }
        List<SavedHotbar> next = new ArrayList<>(hotbars);
        next.set(indexOf(from), hotbar.withName(target));
        return new HotbarBook(next, active.equals(hotbar.name()) ? target : active, autosave);
    }

    /** La lleva a la posición {@code position} (desde 1; se ajusta a la lista). */
    public HotbarBook moved(String name, int position) {
        SavedHotbar hotbar = require(name);
        List<SavedHotbar> next = new ArrayList<>(hotbars);
        next.remove(hotbar);
        next.add(Math.clamp(position - 1L, 0, next.size()), hotbar);
        return new HotbarBook(next, active, autosave);
    }

    public HotbarBook withActive(String name) {
        return new HotbarBook(hotbars, name == null || name.isEmpty() ? "" : require(name).name(), autosave);
    }

    public HotbarBook withAutosave(boolean value) {
        return new HotbarBook(hotbars, active, value);
    }

    /**
     * La barra a {@code steps} puestos de {@code from}, dando la vuelta (la rueda de la tira). Sin {@code from} (o si
     * ya no existe) se empieza antes de la primera.
     */
    public String neighbour(String from, int steps) {
        if (hotbars.isEmpty()) {
            return "";
        }
        int start = from == null ? -1 : indexOf(from);
        if (start < 0) {
            start = steps > 0 ? -1 : 0;
        }
        return hotbars.get(Math.floorMod(start + steps, hotbars.size())).name();
    }

    /** {@code wanted} si está libre; si no, {@code wanted_2}, {@code wanted_3}… sin pasar de 32 letras. */
    public String freeName(String wanted) {
        String base = key(wanted);
        if (indexOf(base) < 0) {
            return base;
        }
        for (int number = 2; ; number++) {
            String suffix = "_" + number;
            String candidate = base.substring(0, Math.min(base.length(), 32 - suffix.length())) + suffix;
            if (indexOf(candidate) < 0) {
                return candidate;
            }
        }
    }

    /** Bytes de todas las barras en JSON compacto. */
    public int bytes() {
        int total = 0;
        for (SavedHotbar hotbar : hotbars) {
            total += HotbarJson.size(hotbar);
        }
        return total;
    }

    private static HotbarBook checked(HotbarBook book, SavedHotbar changed) {
        int size = HotbarJson.size(changed);
        if (size > MAX_HOTBAR_BYTES) {
            throw new IllegalArgumentException("La barra " + changed.name() + " ocupa " + kib(size)
                    + " y el máximo es " + kib(MAX_HOTBAR_BYTES) + " (pesan los objetos con mucho contenido, como "
                    + "libros o cajas llenas)");
        }
        int total = book.bytes();
        if (total > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("Tus barras guardadas ocuparían " + kib(total) + " y el máximo es "
                    + kib(MAX_TOTAL_BYTES) + "; borra alguna");
        }
        return book;
    }

    private static String kib(int bytes) {
        return Math.max(1, (bytes + 1023) / 1024) + " KiB";
    }

    private static String key(String name) {
        return NamedDefinitions.normalizeName(name);
    }
}
