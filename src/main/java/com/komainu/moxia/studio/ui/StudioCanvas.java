package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.layout.Rect;

/**
 * Lo mínimo que el Studio necesita para pintarse: rectángulos, texto con la fuente del juego, iconos de objeto,
 * imágenes y recortes. En el juego lo pone el {@code DrawContext} del cliente; en las pruebas y en las maquetas, una
 * imagen en memoria con la misma fuente bitmap. Todo lo demás ({@link StudioUi}, el marco, las etapas) se dibuja
 * encima con el mismo código en los dos sitios, así que una maqueta enseña lo que verá el jugador.
 *
 * <p>Las coordenadas son píxeles de la GUI ya escalados, como en las pantallas de Minecraft.</p>
 */
public interface StudioCanvas {
    /** Ancho de {@code text} con la fuente del juego. */
    int width(String text);

    /** Ancho de {@code text} en negrita (un píxel más por letra en la fuente del juego). */
    int boldWidth(String text);

    void text(String text, int x, int y, int argb);

    void bold(String text, int x, int y, int argb);

    void fill(Rect rect, int argb);

    /** Icono de 16×16 del objeto (o del objeto del bloque) {@code id}; una barrera si no existe. */
    void item(String id, int x, int y);

    /** Pinta {@code image} estirada a {@code target}, con su transparencia. */
    void image(StudioImage image, Rect target);

    /** A partir de aquí solo se pinta dentro de {@code clip} (dentro también del recorte anterior). */
    void pushClip(Rect clip);

    void popClip();

    /**
     * Lo que se pinte hasta {@link #endOverlay} queda por encima de todo lo anterior, iconos de objeto incluidos
     * (ventanas modales y tooltips).
     */
    void beginOverlay();

    void endOverlay();
}
