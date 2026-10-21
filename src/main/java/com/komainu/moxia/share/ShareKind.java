package com.komainu.moxia.share;

import java.util.List;

/**
 * Un tipo de cosa que se puede compartir (paletas, ruidos, presets de árbol…). Cada tipo sabe leer lo que guardó un
 * jugador, comprobar lo que llega de fuera y guardarlo. Se registra en {@link ShareKinds}; un tipo nuevo (las barras
 * guardadas, por ejemplo) solo necesita implementar esto y registrarse al arrancar el mod.
 *
 * <p>{@link #check} es lo único que ve un elemento recibido antes de guardarlo y debe tratarlo como datos de un
 * desconocido: comprobar tamaños, nombres, bloques ({@link ShareCheck#block}, {@link ShareCheck#pattern}) y fórmulas
 * ({@link ShareCheck#formula}), y no aceptar nada que ejecute órdenes o lea archivos.</p>
 */
public interface ShareKind {
    /** Id en los paquetes ({@code palette}); no puede cambiar. */
    String id();

    /** Otros nombres en la orden ({@code paleta}). */
    List<String> aliases();

    /** «paleta», para los mensajes. */
    String singular();

    /** «paletas». */
    String plural();

    /** Versión de los datos de este tipo que escribe {@link #export}. */
    int version();

    /** Permiso para guardar lo importado de este tipo. Para exportar basta con tenerlo guardado. */
    String permission();

    /** Lo guardado por el jugador, por nombre. */
    List<String> names(ShareTarget target);

    /** Lee lo que guardó el jugador con ese nombre. */
    SharePack.Item export(ShareTarget target, String name);

    /**
     * Comprueba y normaliza un elemento recibido: lo devuelve listo para {@link #save} (con los bloques que faltan ya
     * sustituidos) o lanza {@link IllegalArgumentException} con el motivo. Los avisos van a {@code check}.
     */
    SharePack.Item check(SharePack.Item item, ShareCheck check);

    /** El nombre en la forma de este tipo ({@code Rocas} → {@code rocas}), o lanza si no vale. */
    String normalizeName(String name);

    boolean exists(ShareTarget target, String name);

    /**
     * Guarda un elemento ya comprobado.
     *
     * @param replace si puede sustituir uno que ya existe
     * @return qué se hizo, para el mensaje («guardada», «guardando…»)
     */
    String save(ShareTarget target, SharePack.Item item, String name, boolean replace);

    /** Otros elementos que necesita este para funcionar en otro servidor (una paleta que usa un preset). */
    default List<ShareRef> requires(ShareTarget target, String name) {
        return List.of();
    }
}
