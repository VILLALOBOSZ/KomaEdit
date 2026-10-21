package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.hotbar.HotbarBook;
import com.komainu.moxia.hotbar.HotbarItem;
import com.komainu.moxia.hotbar.HotbarRules;
import com.komainu.moxia.hotbar.ui.HotbarAction;
import com.komainu.moxia.hotbar.ui.HotbarDeck;
import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.tools.HotbarCard;
import com.komainu.moxia.studio.tools.ToolValues;
import com.komainu.moxia.studio.ui.StudioIcons;
import com.komainu.moxia.studio.ui.StudioUi;

import java.util.regex.Pattern;

import static com.komainu.moxia.studio.ui.StudioTheme.*;

/**
 * La parte de arriba de la tarjeta Barras rápidas: qué hace cambiar de barra con el modo de juego de ahora, guardar
 * la que se lleva con el nombre de la tarjeta, pegar un código y copiar todas, y la lista de barras con sus nueve
 * huecos (clic: usarla; clic derecho: copiar su código; la × la borra con un segundo clic). Todo va por el paquete de
 * las barras ({@link ToolsPanel.Host#hotbar}), no por el chat.
 */
final class HotbarsPanel {
    /** Lo que dura armado el segundo clic de borrar o sustituir. */
    static final long CONFIRM_MILLIS = 4_000L;
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final int ROW = 31;
    private static final int COMPACT_ROW = 20;
    private static final int SLOT = 17;

    private String armedDelete;
    private String armedOverwrite;
    private long armedAt;

    /** Debajo del Nombre (que pinta la tarjeta antes): guardar la que se lleva, la lista y los códigos. */
    void draw(StudioUi ui, Column column, ToolsPanel.Host host, ToolValues values, boolean compact) {
        HotbarDeck deck = host.hotbars();
        if (deck == null) {
            ui.paragraph(column, host.tr("gui.komaedit.tools.hotbars.unavailable"), MUTED, 0);
            ui.section(column, host.tr("gui.komaedit.tools.hotbars.create"));
            return;
        }
        String name = HotbarCard.name(values);
        ui.iconButton(column.next(StudioUi.SMALL_ROW), StudioIcons.Icon.HOTBARS,
                        host.tr("gui.komaedit.tools.hotbars.save"), StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.tools.hotbars.save.help", name))
                .onClick(() -> save(host, deck, name));
        String mode = switch (deck.mode()) {
            case REORDER -> host.tr("gui.komaedit.tools.hotbars.survival");
            case GIVE -> host.tr("gui.komaedit.tools.hotbars.give");
            case DENIED -> deck.reason() == null ? host.tr("gui.komaedit.hotbar.denied") : deck.reason();
            case SET -> null;
        };
        if (mode != null) {
            ui.paragraph(column, mode, deck.mode() == HotbarRules.Mode.DENIED ? DANGER
                    : WARNING, compact ? 2 : 0);
        }
        ui.section(column, host.tr("gui.komaedit.tools.hotbars.list"),
                deck.size() + "/" + HotbarBook.MAX_HOTBARS);
        if (deck.isEmpty()) {
            ui.paragraph(column, host.tr("gui.komaedit.tools.hotbars.empty"), MUTED, 0);
        }
        for (int index = 0; index < deck.size(); index++) {
            drawRow(ui, column, host, deck, index, compact);
        }
        Rect[] buttons = column.row(2, StudioUi.SMALL_ROW);
        ui.button(buttons[0], host.tr("gui.komaedit.hotbar.menu.paste"), StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.tools.hotbars.paste.help"))
                .onClick(() -> host.hotbar(HotbarAction.IMPORT_CLIPBOARD, ""));
        ui.button(buttons[1], host.tr("gui.komaedit.hotbar.menu.copy_all"),
                        deck.isEmpty() ? StudioUi.Tone.DISABLED : StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.tools.hotbars.copy_all.help"))
                .onClick(() -> {
                    if (!deck.isEmpty()) {
                        host.hotbar(HotbarAction.EXPORT, "");
                    }
                });
        boolean autosave = host.hotbarAutosave();
        ui.toggle(column.next(), host.tr("gui.komaedit.tools.hotbars.autosave"), autosave)
                .tooltip(host.tr("gui.komaedit.tools.hotbars.autosave.help"))
                .onClick(() -> host.hotbar(autosave ? HotbarAction.AUTOSAVE_OFF : HotbarAction.AUTOSAVE_ON, ""));
        ui.iconButton(column.next(StudioUi.SMALL_ROW), StudioIcons.Icon.UNDO,
                        host.tr("gui.komaedit.tools.hotbars.undo"), StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.tools.hotbars.undo.help"))
                .onClick(() -> host.hotbar(HotbarAction.UNDO, ""));
        ui.section(column, host.tr("gui.komaedit.tools.hotbars.create"));
    }

    private void drawRow(StudioUi ui, Column column, ToolsPanel.Host host, HotbarDeck deck, int index,
                         boolean compact) {
        HotbarDeck.Row row = deck.row(index);
        Rect rect = column.next(compact ? COMPACT_ROW : ROW);
        if (!ui.visible(rect)) {
            return;
        }
        boolean active = index == deck.active();
        boolean hovered = ui.hovered(rect);
        ui.fill(rect, active ? CARD_ACTIVE : hovered ? CARD_HOVER : CARD);
        if (active) {
            ui.fill(rect.left(2), ACCENT);
        }
        Rect delete = new Rect(rect.right() - 16, rect.y() + 2, 14, 14);
        boolean armed = row.name().equals(armedDelete) && host.now() - armedAt < CONFIRM_MILLIS;
        int textRight = hovered || armed ? delete.x() - 4 : rect.right() - 4;
        if (compact) {
            ui.item(row.icon(), rect.x() + 4, rect.y() + 2);
            ui.label(label(host, row.name(), active), rect.x() + 24, rect.y() + 6, textRight - rect.x() - 24,
                    active ? TEXT_STRONG : TEXT);
        } else {
            ui.label(label(host, row.name(), active), rect.x() + 6, rect.y() + 3, textRight - rect.x() - 6,
                    active ? TEXT_STRONG : TEXT);
            int fit = Math.min(row.slots().size(), (rect.width() - 10) / SLOT);
            for (int slot = 0; slot < fit; slot++) {
                HotbarItem item = row.slots().get(slot);
                int x = rect.x() + 5 + slot * SLOT;
                ui.fill(new Rect(x - 1, rect.y() + 13, 17, 17), CARD_HOVER);
                if (!item.isEmpty()) {
                    ui.item(item.id(), x, rect.y() + 13);
                }
            }
        }
        ui.register(rect)
                .tooltip(row.name() + "\n" + host.tr(active ? "gui.komaedit.hotbar.menu.in_use"
                        : "gui.komaedit.tools.hotbars.row.help"))
                .onClick(() -> host.hotbar(HotbarAction.APPLY, row.name()))
                .onReset(() -> host.hotbar(HotbarAction.EXPORT, row.name()));
        if (hovered || armed) {
            ui.icon(StudioIcons.Icon.CLOSE, delete.x() + 1, delete.y() + 1, armed ? DANGER : MUTED);
            ui.register(delete)
                    .tooltip(host.tr(armed ? "gui.komaedit.tools.hotbars.delete.confirm"
                            : "gui.komaedit.tools.hotbars.delete", row.name()))
                    .onClick(() -> {
                        if (row.name().equals(armedDelete) && host.now() - armedAt < CONFIRM_MILLIS) {
                            armedDelete = null;
                            host.hotbar(HotbarAction.DELETE, row.name());
                        } else {
                            armedDelete = row.name();
                            armedAt = host.now();
                            host.status(host.tr("gui.komaedit.tools.hotbars.delete.confirm", row.name()));
                        }
                    });
        }
    }

    private static String label(ToolsPanel.Host host, String name, boolean active) {
        return active ? name + " · " + host.tr("gui.komaedit.tools.hotbars.in_use") : name;
    }

    /** Guardar la que se lleva: con un nombre que ya existe, el segundo clic la sustituye. */
    private void save(ToolsPanel.Host host, HotbarDeck deck, String name) {
        if (!NAME.matcher(name).matches()) {
            host.status(host.tr("gui.komaedit.hotbar.status.bad_name"));
            return;
        }
        boolean exists = deck.indexOf(name) >= 0;
        if (exists && !(name.equals(armedOverwrite) && host.now() - armedAt < CONFIRM_MILLIS)) {
            armedOverwrite = name;
            armedAt = host.now();
            host.status(host.tr("gui.komaedit.hotbar.status.confirm_overwrite", name));
            return;
        }
        armedOverwrite = null;
        host.hotbar(exists ? HotbarAction.SAVE_OVERWRITE : HotbarAction.SAVE, name);
        host.status(host.tr("gui.komaedit.hotbar.status.saved", name));
    }
}
