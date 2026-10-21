package com.komainu.moxia.studio.ui;

import com.komainu.moxia.share.ShareImport;
import com.komainu.moxia.share.ShareOfferPayload;
import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.komainu.moxia.studio.ui.StudioTheme.*;

/**
 * La ventana «Importar paquete» del Studio: lo que trae un paquete, cada cosa con su estado (nueva, choca, no entra),
 * y los botones para importarlo. Contesta con las órdenes de siempre ({@code /komaedit share accept …} o
 * {@code cancel}), así que el servidor vuelve a comprobarlo todo al guardar.
 */
public final class ShareDialog {
    private static final int ROW = 22;
    private static final int MAX_ROWS = 8;

    private ShareOfferPayload offer;
    private int offset;

    public boolean isOpen() {
        return offer != null;
    }

    public void open(ShareOfferPayload value) {
        offer = value;
        offset = 0;
    }

    public void close() {
        offer = null;
    }

    private boolean importable() {
        return offer.lines().stream().anyMatch(line -> line.status() == ShareImport.Status.NEW.ordinal()
                || line.status() == ShareImport.Status.CONFLICT.ordinal());
    }

    /** Los botones: el primero es el que hace Intro. */
    private List<Choice> choices(StudioHost host) {
        List<Choice> choices = new ArrayList<>();
        if (importable()) {
            if (offer.conflicts() == 0) {
                choices.add(new Choice(host.tr("gui.komaedit.share.accept"), "renombrar", StudioUi.Tone.PRIMARY,
                        host.tr("gui.komaedit.help.share.accept")));
            } else {
                choices.add(new Choice(host.tr("gui.komaedit.share.rename"), "renombrar", StudioUi.Tone.PRIMARY,
                        host.tr("gui.komaedit.help.share.rename")));
                choices.add(new Choice(host.tr("gui.komaedit.share.replace"), "reemplazar", StudioUi.Tone.WARNING,
                        host.tr("gui.komaedit.help.share.replace")));
                choices.add(new Choice(host.tr("gui.komaedit.share.skip"), "saltar", StudioUi.Tone.SECONDARY,
                        host.tr("gui.komaedit.help.share.skip")));
            }
            choices.add(new Choice(host.tr("gui.komaedit.share.cancel"), "", StudioUi.Tone.SECONDARY,
                    host.tr("gui.komaedit.help.share.cancel")));
        } else {
            choices.add(new Choice(host.tr("gui.komaedit.share.close"), "", StudioUi.Tone.SECONDARY, ""));
        }
        return choices;
    }

    private record Choice(String label, String policy, StudioUi.Tone tone, String help) {
    }

    private void choose(Choice choice, StudioHost host) {
        if (!choice.policy().isEmpty()) {
            host.send("komaedit share accept " + choice.policy());
            host.status(host.tr("gui.komaedit.status.share.importing", offer.name()));
        } else if (importable()) {
            host.send("komaedit share cancel");
        }
        close();
    }

    public void draw(StudioUi ui, Rect screen, StudioHost host) {
        List<ShareOfferPayload.Line> lines = offer.lines();
        int rows = Math.min(MAX_ROWS, lines.size());
        int width = Math.min(screen.width() - 32, 340);
        List<Choice> choices = choices(host);
        int[] widths = choices.stream().mapToInt(choice -> ui.chipWidth(choice.label()) + 8).toArray();
        List<Rect> placed = Column.flow(widths, 16, 0, 0, width - 20);
        int buttonsHeight = placed.stream().mapToInt(Rect::bottom).max().orElse(16);
        int height = 36 + rows * ROW + 6 + buttonsHeight + 8;
        Rect modal = new Rect(screen.x() + (screen.width() - width) / 2,
                screen.y() + Math.max(8, (screen.height() - height) / 2), width, height);
        ui.fill(screen, SCRIM);
        ui.register(screen);
        ui.enableHover();
        ui.fill(modal, PANEL);
        ui.border(modal, DIVIDER);
        ui.fill(modal.left(2), ACCENT);
        ui.register(modal);
        String name = offer.name().isEmpty() ? host.tr("gui.komaedit.share.unnamed") : offer.name();
        ui.bold(host.tr("gui.komaedit.share.title", name), modal.x() + 10, modal.y() + 8, modal.width() - 20,
                TEXT_STRONG);
        String from = offer.author().isEmpty() ? "" : host.tr("gui.komaedit.share.from", offer.author());
        if (!offer.modVersion().isEmpty()) {
            from = (from.isEmpty() ? "" : from + " · ") + "KomaEdit " + offer.modVersion();
        }
        ui.label(from.isEmpty() ? host.tr("gui.komaedit.share.count", lines.size())
                        : from + " · " + host.tr("gui.komaedit.share.count", lines.size()),
                modal.x() + 10, modal.y() + 21, modal.width() - 20, MUTED);
        offset = Math.clamp(offset, 0, Math.max(0, lines.size() - rows));
        int y = modal.y() + 36;
        for (int index = offset; index < offset + rows; index++) {
            ShareOfferPayload.Line line = lines.get(index);
            Rect row = new Rect(modal.x() + 6, y, modal.width() - 12, ROW - 2);
            ui.fill(row, CARD);
            int color = color(line.status());
            ui.fill(row.left(2), color);
            ui.label(mark(line.status()) + " " + line.label(), row.x() + 7, row.y() + 2, row.width() - 12, color);
            String detail = line.detail().isEmpty() ? host.tr("gui.komaedit.share.status." + status(line.status()))
                    : line.detail();
            ui.label(detail, row.x() + 7, row.y() + 11, row.width() - 12, MUTED);
            ui.register(row).tooltip(line.label() + "\n" + host.tr("gui.komaedit.share.status."
                    + status(line.status())) + (line.detail().isEmpty() ? "" : "\n" + line.detail()))
                    .onScroll(direction -> offset += direction);
            y += ROW;
        }
        if (lines.size() > rows) {
            ui.rightAligned((offset + 1) + "–" + (offset + rows) + " / " + lines.size(), modal.right() - 8,
                    modal.y() + 21, MUTED);
        }
        int top = y + 6;
        for (int index = 0; index < choices.size(); index++) {
            Choice choice = choices.get(index);
            Rect cell = placed.get(index);
            Rect button = new Rect(modal.x() + 10 + cell.x(), top + cell.y(), cell.width(), cell.height());
            ui.button(button, choice.label(), choice.tone())
                    .tooltip(choice.help().isEmpty() ? choice.label() : choice.label() + "\n" + choice.help())
                    .onClick(() -> choose(choice, host));
        }
    }

    /** Esc cancela, Intro hace el primer botón y las flechas mueven la lista. */
    public boolean key(StudioKey key, StudioHost host) {
        switch (key.code()) {
            case ESCAPE -> {
                List<Choice> choices = choices(host);
                choose(choices.getLast(), host);
            }
            case ENTER -> choose(choices(host).getFirst(), host);
            case UP -> offset--;
            case DOWN -> offset++;
            default -> {
                return false;
            }
        }
        return true;
    }

    public void scroll(int direction) {
        offset += direction;
    }

    private static String status(int ordinal) {
        ShareImport.Status[] values = ShareImport.Status.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal].name().toLowerCase(Locale.ROOT)
                : "invalid";
    }

    private static int color(int ordinal) {
        return switch (status(ordinal)) {
            case "new" -> SUCCESS;
            case "conflict" -> WARNING;
            default -> DANGER;
        };
    }

    private static String mark(int ordinal) {
        return switch (status(ordinal)) {
            case "new" -> "+";
            case "conflict" -> "!";
            case "unknown" -> "?";
            default -> "×";
        };
    }
}
