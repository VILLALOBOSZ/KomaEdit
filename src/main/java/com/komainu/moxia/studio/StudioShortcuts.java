package com.komainu.moxia.studio;

import java.util.List;

/**
 * Atajos de teclado del Studio, en el orden en que los enseña la chuleta (F1 o ?).
 * La pantalla los atiende en su {@code keyPressed}; esta lista es la referencia visible y documentada.
 * Las teclas también se traducen («Mayús» / «Shift»).
 */
public final class StudioShortcuts {
    public record Shortcut(String id) {
        public String keysKey() {
            return "gui.komaedit.shortcut." + id + ".keys";
        }

        public String translationKey() {
            return "gui.komaedit.shortcut." + id;
        }
    }

    public static final List<Shortcut> ALL = List.of(
            new Shortcut("stage"),
            new Shortcut("next_stage"),
            new Shortcut("search"),
            new Shortcut("build"),
            new Shortcut("hologram"),
            new Shortcut("param_undo"),
            new Shortcut("world_undo"),
            new Shortcut("variant"),
            new Shortcut("save"),
            new Shortcut("clear"),
            new Shortcut("scroll"),
            new Shortcut("help"),
            new Shortcut("close"),
            new Shortcut("wheel"),
            new Shortcut("big_step"),
            new Shortcut("drag"),
            new Shortcut("reset"),
            new Shortcut("favorite")
    );

    private StudioShortcuts() {
    }
}
