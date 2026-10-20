package com.komainu.moxia.client.gui.studio;

import com.komainu.moxia.blueprint.BlueprintSearch;
import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.blueprint.BlueprintTags;
import com.komainu.moxia.client.blueprint.ClientBlueprintCatalog;
import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.komainu.moxia.client.gui.studio.StudioTheme.ACCENT;
import static com.komainu.moxia.client.gui.studio.StudioTheme.CARD;
import static com.komainu.moxia.client.gui.studio.StudioTheme.CARD_ACTIVE;
import static com.komainu.moxia.client.gui.studio.StudioTheme.CARD_HOVER;
import static com.komainu.moxia.client.gui.studio.StudioTheme.DIVIDER;
import static com.komainu.moxia.client.gui.studio.StudioTheme.FAVORITE;
import static com.komainu.moxia.client.gui.studio.StudioTheme.FIELD;
import static com.komainu.moxia.client.gui.studio.StudioTheme.MUTED;
import static com.komainu.moxia.client.gui.studio.StudioTheme.TEXT;
import static com.komainu.moxia.client.gui.studio.StudioTheme.TEXT_SECONDARY;

/**
 * Etapa «Planos» del Studio: la biblioteca de planos en una cuadrícula de miniaturas, con buscador por nombre y
 * etiqueta, filtro de propios o del servidor, y los botones cargar, colocar y estampar. Las miniaturas se piden solo
 * para las tarjetas que se ven y llegan en trozos ({@link ClientBlueprintCatalog}). Las acciones son las órdenes de
 * siempre ({@code /komaedit blueprint load|place}, {@code /komaedit pincel stamp}).
 */
public final class BlueprintStage {
    /** Lo que la etapa necesita de la pantalla. */
    public interface Host {
        String tr(String key, Object... arguments);

        /** Ejecuta una orden sin barra. */
        void send(String command);

        /** Abre el chat con la orden escrita para completarla. */
        void suggest(String command);

        void status(String text);
    }

    public static final String SEARCH_KEY = "blueprint_search";
    private static final List<String> SCOPES = List.of("all", "mine", "server");
    private static final int[] RADII = {0, 4, 8, 12, 16, 24};
    private static final int MIN_CARD = 60;
    private static final int CAPTION = 20;
    private static final int MAX_TAG_CHIPS = 10;
    private static final int THUMBNAIL = 128;

    private String query = "";
    private boolean editing;
    private int scope;
    private String selected = "";
    private int radius;

    /** Al entrar en la etapa: catálogo al día. */
    public void opened() {
        ClientBlueprintCatalog.refresh(false);
    }

    public boolean editing() {
        return editing;
    }

    public void stopEditing() {
        editing = false;
    }

    public void focusSearch() {
        editing = true;
    }

    public boolean charTyped(char chr) {
        if (chr >= 32 && chr != 127 && query.length() < 48) {
            query += chr;
        }
        return true;
    }

    public boolean keyPressed(int keyCode, boolean control) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (query.isEmpty()) {
                editing = false;
            } else {
                query = "";
            }
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            editing = false;
            List<BlueprintSummary> shown = visible();
            if (!shown.isEmpty() && shown.stream().noneMatch(blueprint -> blueprint.reference().equals(selected))) {
                selected = shown.getFirst().reference();
            }
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = control ? "" : query.substring(0, query.length() - 1);
        }
        return true;
    }

    public void draw(StudioUi ui, Column column, Host host) {
        if (!ClientBlueprintCatalog.received()) {
            ClientBlueprintCatalog.refresh(false);
            ui.paragraph(column, host.tr("gui.komaedit.blueprint.loading"), MUTED, 2);
            return;
        }
        ui.field(column.next(), query, host.tr("gui.komaedit.blueprint.search"), editing, false)
                .key(SEARCH_KEY)
                .tooltip(host.tr("gui.komaedit.help.blueprint.search"))
                .onClick(() -> editing = true);
        // El filtro necesita sitio para «Servidor»; Actualizar cabe en el tercio que queda.
        Rect filterRow = column.next();
        int refreshWidth = Math.max(60, filterRow.width() / 3);
        Rect[] filters = {new Rect(filterRow.x(), filterRow.y(), filterRow.width() - refreshWidth - Column.GAP,
                filterRow.height()), new Rect(filterRow.right() - refreshWidth, filterRow.y(), refreshWidth,
                filterRow.height())};
        ui.cycle(filters[0], host.tr("gui.komaedit.blueprint.scope"),
                host.tr("gui.komaedit.blueprint.scope." + SCOPES.get(scope)),
                direction -> scope = Math.floorMod(scope + direction, SCOPES.size()))
                .tooltip(host.tr("gui.komaedit.help.blueprint.scope"));
        ui.button(filters[1], host.tr("gui.komaedit.blueprint.refresh"), StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.help.blueprint.refresh"))
                .onClick(() -> {
                    ClientBlueprintCatalog.refresh(true);
                    host.status(host.tr("gui.komaedit.status.blueprint.refresh"));
                });
        tagChips(ui, column, host);

        List<BlueprintSummary> shown = visible();
        BlueprintSummary current = shown.stream().filter(blueprint -> blueprint.reference().equals(selected))
                .findFirst().orElse(null);
        actions(ui, column, host, current);

        ui.section(column, host.tr("gui.komaedit.blueprint.library", shown.size()));
        if (shown.isEmpty()) {
            ui.paragraph(column, host.tr(ClientBlueprintCatalog.blueprints().isEmpty()
                    ? "gui.komaedit.blueprint.empty" : "gui.komaedit.blueprint.no_match"), MUTED, 0);
            if (ClientBlueprintCatalog.canSave()) {
                ui.button(column.next(), host.tr("gui.komaedit.blueprint.save_clipboard"), StudioUi.Tone.SECONDARY)
                        .tooltip(host.tr("gui.komaedit.help.blueprint.save_clipboard"))
                        .onClick(() -> host.suggest("/komaedit blueprint save "));
            }
            return;
        }
        grid(ui, column, host, shown);
    }

    /** Los planos del catálogo que pasan el buscador y el filtro, primero los propios y por nombre. */
    List<BlueprintSummary> visible() {
        BlueprintSearch search;
        try {
            search = BlueprintSearch.parse(query);
        } catch (IllegalArgumentException exception) {
            // Mientras se escribe una etiqueta a medias («#») no se filtra por ella.
            search = BlueprintSearch.parse(query.replaceAll("#[^\\s]*$", ""));
        }
        BlueprintSearch filter = search;
        String which = SCOPES.get(scope);
        return ClientBlueprintCatalog.blueprints().stream()
                .filter(blueprint -> which.equals("all") || which.equals("mine") == !blueprint.shared())
                .filter(blueprint -> blueprint.matches(filter))
                .sorted(Comparator.comparing(BlueprintSummary::shared).thenComparing(BlueprintSummary::name))
                .toList();
    }

    /** Las etiquetas más usadas como pastillas: una pulsación la añade o la quita del buscador. */
    private void tagChips(StudioUi ui, Column column, Host host) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BlueprintSummary blueprint : ClientBlueprintCatalog.blueprints()) {
            blueprint.tags().forEach(tag -> counts.merge(tag, 1, Integer::sum));
        }
        if (counts.isEmpty()) {
            return;
        }
        List<String> tags = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_TAG_CHIPS).map(Map.Entry::getKey).toList();
        Set<String> active = activeTags();
        int x = column.x();
        Rect row = column.next(14);
        for (String tag : tags) {
            String label = "#" + tag;
            int width = ui.chipWidth(label);
            if (x + width > column.x() + column.width()) {
                row = column.next(14);
                x = column.x();
            }
            boolean on = active.contains(tag);
            ui.chip(new Rect(x, row.y(), width, 14), label, on, TEXT_SECONDARY)
                    .tooltip(host.tr("gui.komaedit.help.blueprint.tag", tag, counts.get(tag)))
                    .onClick(() -> toggleTag(tag));
            x += width + 3;
        }
    }

    private Set<String> activeTags() {
        Set<String> active = new LinkedHashSet<>();
        for (String word : query.trim().split("\\s+")) {
            if (word.startsWith("#") && word.length() > 1) {
                try {
                    active.add(BlueprintTags.normalize(word));
                } catch (IllegalArgumentException ignored) {
                    // Etiqueta a medio escribir.
                }
            }
        }
        return active;
    }

    private void toggleTag(String tag) {
        List<String> words = new ArrayList<>();
        boolean removed = false;
        for (String word : query.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (word.equalsIgnoreCase("#" + tag)) {
                removed = true;
            } else {
                words.add(word);
            }
        }
        if (!removed) {
            words.add("#" + tag);
        }
        query = String.join(" ", words);
    }

    /** El plano elegido y sus botones; el pincel puede estampar ese plano o la etiqueta del filtro. */
    private void actions(StudioUi ui, Column column, Host host, BlueprintSummary current) {
        ui.section(column, current == null ? host.tr("gui.komaedit.blueprint.selected_none")
                : current.reference());
        if (current == null) {
            ui.paragraph(column, host.tr("gui.komaedit.blueprint.pick"), MUTED, 2);
            return;
        }
        ui.paragraph(column, host.tr("gui.komaedit.blueprint.details", current.describeSize(), current.blocks(),
                current.author()) + (current.tags().isEmpty() ? "" : " · #" + String.join(" #", current.tags())),
                TEXT_SECONDARY, 2);
        Rect[] buttons = column.row(3, StudioUi.ROW);
        String reference = current.reference();
        ui.button(buttons[0], host.tr("gui.komaedit.blueprint.load"), StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.help.blueprint.load"))
                .onClick(() -> {
                    host.send("komaedit blueprint load " + reference);
                    host.status(host.tr("gui.komaedit.status.blueprint.load", reference));
                });
        boolean place = ClientBlueprintCatalog.canPlace();
        ui.button(buttons[1], host.tr("gui.komaedit.blueprint.place"),
                        place ? StudioUi.Tone.PRIMARY : StudioUi.Tone.DISABLED)
                .tooltip(host.tr(place ? "gui.komaedit.help.blueprint.place" : "gui.komaedit.help.blueprint.denied"))
                .onClick(() -> {
                    if (place) {
                        host.send("komaedit blueprint place " + reference + " -v");
                        host.status(host.tr("gui.komaedit.status.blueprint.place", reference));
                    }
                });
        boolean stamp = ClientBlueprintCatalog.canStamp();
        ui.button(buttons[2], host.tr("gui.komaedit.blueprint.stamp"),
                        stamp ? StudioUi.Tone.SECONDARY : StudioUi.Tone.DISABLED)
                .tooltip(host.tr(stamp ? "gui.komaedit.help.blueprint.stamp" : "gui.komaedit.help.blueprint.denied"))
                .onClick(() -> {
                    if (stamp) {
                        stamp(host, reference);
                    }
                });
        if (stamp) {
            ui.cycle(column.next(), host.tr("gui.komaedit.blueprint.radius"), Integer.toString(RADII[radius]),
                    direction -> radius = Math.floorMod(radius + direction, RADII.length))
                    .tooltip(host.tr("gui.komaedit.help.blueprint.radius"));
            Set<String> tags = activeTags();
            if (tags.size() == 1) {
                String tag = "#" + tags.iterator().next();
                ui.button(column.next(), host.tr("gui.komaedit.blueprint.stamp_tag", tag), StudioUi.Tone.SECONDARY)
                        .tooltip(host.tr("gui.komaedit.help.blueprint.stamp_tag", tag))
                        .onClick(() -> stamp(host, tag));
            }
        }
        if (current.mine() && !current.shared() && ClientBlueprintCatalog.canShare()) {
            ui.button(column.next(), host.tr("gui.komaedit.blueprint.share"), StudioUi.Tone.SECONDARY)
                    .tooltip(host.tr("gui.komaedit.help.blueprint.share"))
                    .onClick(() -> host.send("komaedit blueprint share " + current.name()));
        }
    }

    private void stamp(Host host, String what) {
        host.send("komaedit pincel stamp " + what + (RADII[radius] > 0 ? " " + RADII[radius] : ""));
        host.status(host.tr("gui.komaedit.status.blueprint.stamp", what));
    }

    /** Cuadrícula de tarjetas: miniatura arriba y nombre y tamaño debajo. */
    private void grid(StudioUi ui, Column column, Host host, List<BlueprintSummary> shown) {
        int columns = Math.max(2, (column.width() + Column.GAP) / (MIN_CARD + Column.GAP));
        int cardWidth = (column.width() - (columns - 1) * Column.GAP) / columns;
        List<Rect> cells = column.grid(shown.size(), columns, cardWidth + CAPTION);
        List<String> wanted = new ArrayList<>();
        for (int index = 0; index < cells.size(); index++) {
            BlueprintSummary blueprint = shown.get(index);
            Rect cell = cells.get(index);
            if (!ui.visible(cell)) {
                continue;
            }
            boolean chosen = blueprint.reference().equals(selected);
            boolean hovered = ui.hovered(cell);
            ui.fill(cell, chosen ? CARD_ACTIVE : hovered ? CARD_HOVER : CARD);
            Rect image = new Rect(cell.x() + 3, cell.y() + 3, cell.width() - 6, cell.width() - 6);
            ui.fill(image, FIELD);
            Identifier texture = ClientBlueprintCatalog.thumbnail(blueprint.thumbnail());
            if (texture != null) {
                RenderSystem.enableBlend();
                ui.context().drawTexture(texture, image.x(), image.y(), image.width(), image.height(), 0, 0,
                        THUMBNAIL, THUMBNAIL, THUMBNAIL, THUMBNAIL);
                RenderSystem.disableBlend();
            } else {
                wanted.add(blueprint.thumbnail());
                ui.centered("…", image, MUTED);
            }
            if (blueprint.shared()) {
                // Estrella: compartido con el servidor.
                ui.fill(new Rect(image.right() - 7, image.y() + 2, 5, 5), FAVORITE);
            }
            if (chosen) {
                ui.border(cell, ACCENT);
            } else if (hovered) {
                ui.border(cell, DIVIDER);
            }
            ui.label(blueprint.name(), cell.x() + 4, image.bottom() + 3, cell.width() - 8, TEXT);
            ui.label(blueprint.describeSize(), cell.x() + 4, image.bottom() + 12, cell.width() - 8, MUTED);
            ui.register(cell)
                    .tooltip(blueprint.reference() + "\n" + host.tr("gui.komaedit.blueprint.details",
                            blueprint.describeSize(), blueprint.blocks(), blueprint.author())
                            + (blueprint.tags().isEmpty() ? "" : "\n#" + String.join(" #", blueprint.tags())))
                    .onClick(() -> selected = blueprint.reference());
        }
        ClientBlueprintCatalog.request(wanted);
    }
}
