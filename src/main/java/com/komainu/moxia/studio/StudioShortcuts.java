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
            new Shortcut("palette"),
            new Shortcut("search"),
            new Shortcut("build"),
            new Shortcut("confirm_preview"),
            new Shortcut("cancel_job"),
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
            new Shortcut("favorite"),
            new Shortcut("world_click"),
            new Shortcut("world_drag_point"),
            new Shortcut("world_drag_segment"),
            new Shortcut("world_select"),
            new Shortcut("world_face"),
            new Shortcut("world_magic"),
            new Shortcut("world_paint"),
            new Shortcut("world_eyedropper"),
            new Shortcut("world_camera")
    );

    private StudioShortcuts() {
    }
}
