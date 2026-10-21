package com.komainu.moxia.share;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * El jugador que exporta o importa: dónde están sus cosas, su nombre (el autor de lo que exporta) y sus permisos.
 *
 * @param permissions dice si tiene un permiso (en las pruebas, lo que haga falta)
 */
public record ShareTarget(UUID id, String name, Predicate<String> permissions) {
    public boolean can(String permission) {
        return permissions.test(permission);
    }
}
