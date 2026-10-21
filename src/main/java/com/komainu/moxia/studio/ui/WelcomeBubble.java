package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.WelcomeTour;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.layout.StudioLayout;

import java.util.ArrayList;
import java.util.List;

import static com.komainu.moxia.studio.ui.StudioTheme.*;

/**
 * Lo que se ve de la bienvenida guiada ({@link WelcomeTour}): la tarjeta de entrada y la del final, en medio del mundo,
 * y en cada golpe una burbuja corta junto a lo que hay que tocar, que queda rodeado por un borde con una luz que le da
 * la vuelta despacio. La burbuja va sobre el mundo, a la izquierda del panel y a la altura del control, para no tapar
 * ningún control; lo que se hace en el mundo (un clic en el suelo), arriba y pegada al panel. Si el mundo no deja
 * sitio (panel muy ancho en una pantalla pequeña), va sobre el panel, debajo o encima del control.
 */
final class WelcomeBubble {
    /** El grupo de los controles de la burbuja: sus clics no cuentan como hacer el paso. */
    static final String GROUP = "welcome";
    static final int WIDTH = 172;
    static final int CARD_WIDTH = 216;
    /** Lo que tarda la luz del borde en dar la vuelta entera al control. */
    static final long LAP_MILLIS = 1_800L;
    private static final int PAD = 7;
    private static final int LIGHT = 18;
    /** Con menos mundo que esto a la izquierda del panel, la burbuja va sobre el panel. */
    private static final int MIN_ROOM = 120;

    /** Lo que hacen los botones; la vista los ata a la bienvenida y manda lo que pidan. */
    record Actions(Runnable start, Runnable next, Runnable apply, Runnable discard, Runnable skipStep,
                   Runnable finish) {
    }

    private WelcomeBubble() {
    }

    /**
     * Dibuja la bienvenida sobre todo lo demás (menos las ventanas). {@code target} es lo que se ve del control al que
     * señala el golpe, o {@code null} si es el mundo. Devuelve dónde quedó, para que el pie y la leyenda no tomen lo
     * que hay debajo por el mundo.
     */
    static Rect draw(StudioUi ui, StudioLayout.Frame frame, StudioHost host, WelcomeTour tour, Rect target, long now,
                     Actions actions) {
        if (tour.beat().kind() == WelcomeTour.Kind.CARD) {
            return card(ui, frame, host, tour, actions);
        }
        // Lo que hay que pulsar lleva el borde con su luz; en los golpes de mirar, la burbuja solo lo señala.
        if (target != null && tour.beat().kind() == WelcomeTour.Kind.ACTION) {
            highlight(ui, target, now);
        }
        return bubble(ui, frame, host, tour, target, actions);
    }

    /** La entrada («Empezar» o «Ahora no») y el final («Terminar»), en medio del mundo. */
    private static Rect card(StudioUi ui, StudioLayout.Frame frame, StudioHost host, WelcomeTour tour,
                             Actions actions) {
        Rect panel = frame.panel();
        int room = panel.x() - 16;
        boolean beside = room >= MIN_ROOM + 30;
        int width = beside ? Math.min(CARD_WIDTH, room) : Math.min(CARD_WIDTH, panel.width() - 12);
        boolean intro = tour.beat() == WelcomeTour.Beat.INTRO;
        String title = host.tr(intro ? "gui.komaedit.welcome.intro.title" : "gui.komaedit.welcome.done.title");
        List<String> body = ui.wrap(host.tr(tour.textKey()), width - 2 * PAD - 2);
        int height = PAD + 13 + body.size() * StudioUi.LINE + 7 + 16 + PAD;
        int x = beside ? (panel.x() - width) / 2 : panel.x() + 6;
        int y = beside ? Math.max(4, (frame.screen().height() - height) / 2) : frame.viewport().y() + 6;
        Rect box = new Rect(x, y, width, height);
        background(ui, box);
        int left = box.x() + PAD + 2;
        int inner = width - 2 * PAD - 2;
        ui.bold(title, left, box.y() + PAD, inner, TEXT_STRONG);
        int lineY = box.y() + PAD + 13;
        for (String line : body) {
            ui.label(line, left, lineY, inner, TEXT);
            lineY += StudioUi.LINE;
        }
        Rect row = new Rect(left, box.bottom() - PAD - 16, inner, 16);
        if (intro) {
            String start = host.tr("gui.komaedit.welcome.start");
            String later = host.tr("gui.komaedit.welcome.later");
            int startWidth = Math.min(inner / 2, ui.width(start) + 20);
            int laterWidth = Math.min(inner - startWidth - 4, ui.width(later) + 16);
            ui.button(new Rect(row.x(), row.y(), startWidth, row.height()), start, StudioUi.Tone.PRIMARY)
                    .group(GROUP).tooltip(start + "\n" + host.tr("gui.komaedit.welcome.start.help"))
                    .onClick(actions.start());
            ui.button(new Rect(row.x() + startWidth + 4, row.y(), laterWidth, row.height()), later,
                            StudioUi.Tone.SECONDARY)
                    .group(GROUP).tooltip(later + "\n" + host.tr("gui.komaedit.welcome.later.help"))
                    .onClick(actions.finish());
        } else {
            String finish = host.tr("gui.komaedit.welcome.finish");
            ui.button(new Rect(row.x(), row.y(), Math.min(inner, ui.width(finish) + 24), row.height()), finish,
                    StudioUi.Tone.PRIMARY).group(GROUP).onClick(actions.finish());
        }
        return box;
    }

    /** La burbuja de un golpe: en qué paso va, qué hacer y por qué, sus botones y, abajo, saltar. */
    private static Rect bubble(StudioUi ui, StudioLayout.Frame frame, StudioHost host, WelcomeTour tour, Rect target,
                               Actions actions) {
        Rect panel = frame.panel();
        Rect screen = frame.screen();
        int room = panel.x() - 14;
        boolean beside = room >= MIN_ROOM;
        int width = beside ? Math.min(WIDTH, room) : Math.min(WIDTH + 24, panel.width() - 12);
        int inner = width - 2 * PAD - 2;
        List<String> notice = new ArrayList<>();
        if (tour.notice() != null) {
            List<String> names = tour.notice().names().stream().map(host::tr).toList();
            notice = ui.wrap(host.tr(tour.notice().key(), String.join(", ", names)), inner);
        }
        List<String> body = ui.wrap(host.tr(tour.textKey()), inner);
        boolean buttons = tour.offersApply() || tour.offersNext();
        int height = PAD + 10 + 12 + notice.size() * StudioUi.LINE + (notice.isEmpty() ? 0 : 2)
                + body.size() * StudioUi.LINE + (buttons ? 5 + 15 : 0) + 6 + 9 + PAD - 2;
        int x;
        int y;
        if (beside) {
            x = panel.x() - 8 - width;
            y = target == null ? 6 : Math.clamp(target.centerY() - height / 2, 4, screen.height() - height - 4);
        } else {
            x = panel.x() + 6;
            if (target == null) {
                y = frame.viewport().y() + 4;
            } else if (target.bottom() + 6 + height <= screen.height() - 4) {
                y = target.bottom() + 6;
            } else {
                y = Math.max(4, target.y() - 6 - height);
            }
        }
        Rect box = new Rect(x, y, width, height);
        background(ui, box);
        if (beside && target != null) {
            tail(ui, box, target.centerY());
        }
        int left = box.x() + PAD + 2;
        int lineY = box.y() + PAD - 1;
        List<WelcomeTour.Step> steps = tour.steps();
        int dotsWidth = dots(ui, box.right() - PAD, lineY + 2, steps, tour.stepNumber());
        ui.label(host.tr("gui.komaedit.welcome.progress", tour.stepNumber(), steps.size()), left, lineY,
                inner - dotsWidth - 4, MUTED);
        lineY += 10;
        ui.bold(host.tr(tour.step().titleKey()), left, lineY, inner, TEXT_STRONG);
        lineY += 12;
        for (String line : notice) {
            ui.label(line, left, lineY, inner, WARNING);
            lineY += StudioUi.LINE;
        }
        lineY += notice.isEmpty() ? 0 : 2;
        for (String line : body) {
            ui.label(line, left, lineY, inner, TEXT);
            lineY += StudioUi.LINE;
        }
        if (buttons) {
            lineY += 5;
            if (tour.offersApply()) {
                String apply = host.tr("gui.komaedit.welcome.apply");
                String discard = host.tr("gui.komaedit.welcome.discard");
                int applyWidth = Math.min(inner / 2, ui.width(apply) + 20);
                int discardWidth = Math.min(inner - applyWidth - 4, ui.width(discard) + 16);
                ui.button(new Rect(left, lineY, applyWidth, 15), apply, StudioUi.Tone.PRIMARY).group(GROUP)
                        .tooltip(apply + "\n" + host.tr("gui.komaedit.welcome.apply.help")).onClick(actions.apply());
                ui.button(new Rect(left + applyWidth + 4, lineY, discardWidth, 15), discard, StudioUi.Tone.SECONDARY)
                        .group(GROUP).tooltip(discard + "\n" + host.tr("gui.komaedit.welcome.discard.help"))
                        .onClick(actions.discard());
            } else {
                String next = host.tr("gui.komaedit.welcome.next");
                ui.button(new Rect(left, lineY, Math.min(inner, ui.width(next) + 24), 15), next,
                        StudioUi.Tone.PRIMARY).group(GROUP).onClick(actions.next());
            }
            lineY += 15;
        }
        lineY += 6;
        int linkX = link(ui, host.tr("gui.komaedit.welcome.skip_step"), left, lineY, left + inner,
                host.tr("gui.komaedit.welcome.skip_step.help"), actions.skipStep());
        if (linkX < left + inner - 20) {
            ui.label("·", linkX + 3, lineY, MUTED);
            link(ui, host.tr("gui.komaedit.welcome.skip_all"), linkX + 10, lineY, left + inner,
                    host.tr("gui.komaedit.welcome.skip_all.help"), actions.finish());
        }
        return box;
    }

    /** El fondo de la burbuja o la tarjeta, con la barra de acento de las ventanas del Studio; se traga sus clics. */
    private static void background(StudioUi ui, Rect box) {
        ui.fill(box, PANEL);
        ui.border(box, OUTLINE);
        ui.fill(new Rect(box.x(), box.y(), 2, box.height()), ACCENT);
        ui.register(box).group(GROUP).onClick(() -> {
        });
    }

    /** Un enlace de texto pequeño (saltar): apagado, y claro bajo el ratón. Devuelve dónde acaba. */
    private static int link(StudioUi ui, String text, int x, int y, int right, String tooltip, Runnable action) {
        int width = Math.min(ui.width(text), right - x);
        Rect zone = new Rect(x - 1, y - 2, width + 2, 11);
        boolean hovered = ui.hovered(zone);
        ui.label(text, x, y, width, hovered ? TEXT : MUTED);
        if (hovered) {
            ui.fill(new Rect(x, y + 8, width, 1), TEXT);
        }
        ui.register(zone).group(GROUP).tooltip(text + "\n" + tooltip).onClick(action);
        return x + width;
    }

    /** La punta de la burbuja hacia el control, en su borde derecho. */
    private static void tail(StudioUi ui, Rect box, int toward) {
        int middle = Math.clamp(toward, box.y() + 6, box.bottom() - 7);
        for (int column = 0; column < 4; column++) {
            ui.fill(new Rect(box.right() + column, middle - 3 + column, 1, 7 - 2 * column), OUTLINE);
        }
    }

    /** Un punto por paso, a la izquierda de {@code right}: el de ahora alargado, los hechos llenos. Devuelve su ancho. */
    private static int dots(StudioUi ui, int right, int y, List<WelcomeTour.Step> steps, int current) {
        int width = 0;
        for (int index = 0; index < steps.size(); index++) {
            width += (index + 1 == current ? 8 : 4) + (index > 0 ? 3 : 0);
        }
        int x = right - width;
        for (int index = 0; index < steps.size(); index++) {
            int number = index + 1;
            Rect dot = new Rect(x, y, number == current ? 8 : 4, 4);
            if (number <= current) {
                ui.fill(dot, number == current ? ACCENT : ACCENT_TEXT);
            } else {
                ui.border(dot, OUTLINE);
            }
            x += dot.width() + 3;
        }
        return width;
    }

    /**
     * El borde que señala el control: dos líneas de acento a su alrededor y una luz corta que le da la vuelta en
     * {@value #LAP_MILLIS} ms. Se ve dónde está sin parpadear.
     */
    static void highlight(StudioUi ui, Rect target, long now) {
        Rect ring = new Rect(target.x() - 2, target.y() - 2, target.width() + 4, target.height() + 4);
        ui.border(ring, ACCENT);
        ui.border(ring.inset(1, 1), fade(ACCENT, 0.35));
        int across = ring.width() - 1;
        int down = ring.height() - 1;
        int perimeter = 2 * (across + down);
        if (perimeter <= 0) {
            return;
        }
        int head = (int) (Math.floorMod(now, LAP_MILLIS) * perimeter / LAP_MILLIS);
        for (int step = 0; step < Math.min(LIGHT, perimeter); step++) {
            int along = Math.floorMod(head - step, perimeter);
            int px;
            int py;
            if (along < across) {
                px = ring.x() + along;
                py = ring.y();
            } else if (along < across + down) {
                px = ring.right() - 1;
                py = ring.y() + along - across;
            } else if (along < 2 * across + down) {
                px = ring.right() - 1 - (along - across - down);
                py = ring.bottom() - 1;
            } else {
                px = ring.x();
                py = ring.bottom() - 1 - (along - 2 * across - down);
            }
            ui.fill(new Rect(px, py, 1, 1), FOCUS);
        }
    }
}
