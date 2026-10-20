package com.komainu.moxia.gizmo;

/**
 * Cómo se coloca el objeto, a la vista mientras se mueve:
 *
 * @param keepExisting conservar lo que hay: solo se escribe donde ahora hay aire
 * @param pasteAir     pegar el aire del objeto (el de un portapapeles copiado con aire o el de {@code place -a}),
 *                     que borra lo que haya en esas posiciones
 * @param ground       apoyar en el suelo: al moverlo en horizontal baja (o sube) hasta descansar sobre el terreno.
 *                     Lo decide el cliente: el servidor recibe la posición ya apoyada.
 */
public record GhostOptions(boolean keepExisting, boolean pasteAir, boolean ground) {
    public static final GhostOptions DEFAULT = new GhostOptions(false, true, false);
    private static final int KEEP = 1;
    private static final int AIR = 2;
    private static final int GROUND = 4;

    public GhostOptions withKeepExisting(boolean value) {
        return new GhostOptions(value, pasteAir, ground);
    }

    public GhostOptions withPasteAir(boolean value) {
        return new GhostOptions(keepExisting, value, ground);
    }

    public GhostOptions withGround(boolean value) {
        return new GhostOptions(keepExisting, pasteAir, value);
    }

    /** Las tres opciones en un número, para la red. */
    public int bits() {
        return (keepExisting ? KEEP : 0) | (pasteAir ? AIR : 0) | (ground ? GROUND : 0);
    }

    public static GhostOptions fromBits(int bits) {
        return new GhostOptions((bits & KEEP) != 0, (bits & AIR) != 0, (bits & GROUND) != 0);
    }
}
