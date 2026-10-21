package com.komainu.moxia.hotbar.ui;

/** Textos traducidos de la tira y el menú ({@code gui.komaedit.hotbar.*}); en el juego, los de Minecraft. */
@FunctionalInterface
public interface HotbarTexts {
    String tr(String key, Object... arguments);
}
