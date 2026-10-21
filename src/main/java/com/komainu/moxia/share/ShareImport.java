package com.komainu.moxia.share;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Una importación en dos pasos: {@link #prepare} comprueba cada elemento y mira qué choca con lo que ya tiene el
 * jugador (para enseñarlo antes de tocar nada) y {@link #apply} lo guarda con la política elegida para los choques.
 * Es lógica pura sobre los {@link ShareKind}: las pruebas la usan con almacenes en carpetas temporales.
 */
public final class ShareImport {
    /** Qué hacer con lo que ya existe. */
    public enum Policy {
        /** Guardarlo con otro nombre ({@code torre_2}). */
        RENAME("renombrar", "rename"),
        /** Sustituir lo que había. */
        REPLACE("reemplazar", "replace"),
        /** Dejar lo que había y no importar ese. */
        SKIP("saltar", "skip");

        private final String id;
        private final String english;

        Policy(String id, String english) {
            this.id = id;
            this.english = english;
        }

        public String id() {
            return id;
        }

        public static Policy byName(String name) {
            String value = name == null ? "" : name.toLowerCase(Locale.ROOT);
            for (Policy policy : values()) {
                if (policy.id.equals(value) || policy.english.equals(value)) {
                    return policy;
                }
            }
            throw new IllegalArgumentException("Usa renombrar, reemplazar o saltar");
        }
    }

    public enum Status {
        /** Se puede importar tal cual. */
        NEW,
        /** Ya tiene algo con ese nombre (o el paquete lo repite). */
        CONFLICT,
        /** No pasa la comprobación: no se importa. */
        INVALID,
        /** Tipo que esta versión de KomaEdit no conoce. */
        UNKNOWN,
        /** Le falta el permiso para guardar ese tipo. */
        DENIED;

        public boolean importable() {
            return this == NEW || this == CONFLICT;
        }
    }

    /**
     * Un elemento del paquete ya comprobado.
     *
     * @param item     el elemento listo para guardar (o el recibido, si no vale)
     * @param kind     su tipo, o {@code null} si no se conoce
     * @param name     su nombre normalizado
     * @param problem  por qué no se importa o con qué choca
     * @param warnings lo que se cambió para que valga aquí (bloques, biomas…)
     */
    public record Entry(SharePack.Item item, ShareKind kind, Status status, String name, String problem,
                        List<String> warnings) {
        public Entry {
            warnings = List.copyOf(warnings);
        }

        public String label() {
            return (kind == null ? item.kind() : kind.singular()) + " «" + name + "»";
        }
    }

    /** Qué pasó con cada elemento al aplicar. */
    public record Outcome(Entry entry, String savedAs, boolean saved, String message) {
    }

    private final SharePack pack;
    private final List<Entry> entries;

    private ShareImport(SharePack pack, List<Entry> entries) {
        this.pack = pack;
        this.entries = List.copyOf(entries);
    }

    /** Comprueba el paquete sin guardar nada. */
    public static ShareImport prepare(SharePack pack, ShareTarget target, Function<String, ShareKind> kinds,
                                      ShareCheck check) {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SharePack.Item item : pack.items()) {
            String shown = ShareText.display(item.name(), ShareCodec.MAX_ITEM_NAME);
            ShareKind kind = kinds.apply(item.kind());
            if (kind == null) {
                entries.add(new Entry(item, null, Status.UNKNOWN, shown,
                        "tipo «" + item.kind() + "» que esta versión de KomaEdit no conoce", List.of()));
                continue;
            }
            if (!target.can(kind.permission())) {
                entries.add(new Entry(item, kind, Status.DENIED, shown, "te falta el permiso " + kind.permission(),
                        List.of()));
                continue;
            }
            check.begin();
            SharePack.Item checked;
            try {
                checked = kind.check(item, check);
            } catch (IllegalArgumentException exception) {
                entries.add(new Entry(item, kind, Status.INVALID, shown, message(exception), check.warnings()));
                continue;
            } catch (RuntimeException exception) {
                entries.add(new Entry(item, kind, Status.INVALID, shown, "datos que no se entienden",
                        check.warnings()));
                continue;
            }
            String name = checked.name();
            boolean exists;
            try {
                exists = kind.exists(target, name);
            } catch (RuntimeException exception) {
                entries.add(new Entry(checked, kind, Status.INVALID, name, "no se pudo mirar lo que tienes: "
                        + message(exception), check.warnings()));
                continue;
            }
            boolean repeated = !seen.add(kind.id() + ":" + name.toLowerCase(Locale.ROOT));
            Status status = exists || repeated ? Status.CONFLICT : Status.NEW;
            String problem = repeated ? "el paquete lo trae dos veces" : exists ? "ya tienes " + kind.singular()
                    + " con ese nombre" : "";
            entries.add(new Entry(checked, kind, status, name, problem, check.warnings()));
        }
        return new ShareImport(pack, entries);
    }

    public SharePack pack() {
        return pack;
    }

    public List<Entry> entries() {
        return entries;
    }

    public long count(Status status) {
        return entries.stream().filter(entry -> entry.status() == status).count();
    }

    /** ¿Hay algo que importar? */
    public boolean importable() {
        return entries.stream().anyMatch(entry -> entry.status().importable());
    }

    /** Guarda lo que se puede importar; lo que choca, según {@code policy}. */
    public List<Outcome> apply(ShareTarget target, Policy policy) {
        List<Outcome> outcomes = new ArrayList<>();
        Set<String> written = new HashSet<>();
        for (Entry entry : entries) {
            if (!entry.status().importable()) {
                outcomes.add(new Outcome(entry, "", false, entry.problem()));
                continue;
            }
            ShareKind kind = entry.kind();
            String name = entry.name();
            boolean replace = false;
            String key = kind.id() + ":" + name.toLowerCase(Locale.ROOT);
            // Lo que ya escribió esta misma importación no se pisa: un repetido del paquete siempre se renombra.
            boolean clash = entry.status() == Status.CONFLICT || written.contains(key);
            if (clash) {
                Policy effective = written.contains(key) && policy == Policy.REPLACE ? Policy.RENAME : policy;
                switch (effective) {
                    case SKIP -> {
                        outcomes.add(new Outcome(entry, "", false, "se dejó el que tenías"));
                        continue;
                    }
                    case REPLACE -> replace = true;
                    case RENAME -> {
                        try {
                            name = freeName(kind, target, name, written);
                        } catch (RuntimeException exception) {
                            outcomes.add(new Outcome(entry, "", false, message(exception)));
                            continue;
                        }
                    }
                }
            }
            try {
                String message = kind.save(target, entry.item().withName(name), name, replace);
                written.add(kind.id() + ":" + name.toLowerCase(Locale.ROOT));
                outcomes.add(new Outcome(entry, name, true, message));
            } catch (RuntimeException exception) {
                outcomes.add(new Outcome(entry, "", false, message(exception)));
            }
        }
        return outcomes;
    }

    /** {@code torre} → {@code torre_2}, {@code torre_3}… el primero libre que quepa en el nombre. */
    static String freeName(ShareKind kind, ShareTarget target, String name, Set<String> written) {
        for (int suffix = 2; suffix < 1000; suffix++) {
            String tail = "_" + suffix;
            String base = name.length() + tail.length() > 32 ? name.substring(0, 32 - tail.length()) : name;
            String candidate = kind.normalizeName(base + tail);
            if (!written.contains(kind.id() + ":" + candidate.toLowerCase(Locale.ROOT))
                    && !kind.exists(target, candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No hay un nombre libre para «" + name + "»");
    }

    private static String message(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "no se pudo" : message;
    }
}
