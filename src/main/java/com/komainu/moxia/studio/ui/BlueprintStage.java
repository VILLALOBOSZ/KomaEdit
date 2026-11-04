package com.komainu.moxia.studio.ui;

import com.komainu.moxia.blueprint.BlueprintSearch;
import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.blueprint.BlueprintTags;
import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.tools.ui.BrushSync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.komainu.moxia.studio.ui.StudioTheme.*;

/**
 * Etapa «Planos» del Studio: la biblioteca de planos en una cuadrícula de miniaturas, con buscador por nombre y
 * etiqueta y filtro de propios o del servidor. Encima de la cuadrícula, siempre en el mismo sitio, el plano elegido
 * con Cargar y Estampar; Colocar (con vista previa) es el botón del pie. Las miniaturas se piden solo para las
 * tarjetas que se ven. Las acciones son las órdenes de siempre ({@code /komaedit blueprint load|place|export},
 * {@code /komaedit pincel stamp}). Debajo, «Importar archivo…» enseña los esquemas de la carpeta {@code schematics} del
 * jugador (la de Litematica) y sube el elegido para importarlo.
 */
public final class BlueprintStage {
    public static final String SEARCH_KEY = "blueprint_search";
    private static final List<String> SCOPES = List.of("all", "mine", "server");
    private static final int[] RADII = {0, 4, 8, 12, 16, 24};
    private static final int MIN_CARD = 60;
    private static final int CAPTION = 20;
    private static final int MAX_TAG_CHIPS = 10;
    /** Formatos de «Exportar», en el orden de la rueda: Litematica, estructura y WorldEdit. */
    private static final List<String> EXPORT_FORMATS = List.of("litematic", "nbt", "schem");
    /** Archivos de la carpeta schematics que se enseñan (los más nuevos). */
    private static final int MAX_LOCAL_FILES = 8;

    private String query = "";
    private boolean editing;
    private int scope;
    private String selected = "";
    private int radius;
    private int exportFormat;
    /** La lista de «Importar archivo…» abierta. */
    private boolean importing;
    /** El pincel de estampar que salió de aquí sigue a la etapa: el plano elegido y el radio. */
    private final BrushSync brush = new BrushSync();
    /** Lo que se estampó: una referencia de plano o una etiqueta ({@code #ruinas}). */
    private String stamped;

    /** Al entrar en la etapa: catálogo al día. */
    public void opened(StudioHost host) {
        host.blueprints().refresh(false);
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

    /** El plano elegido (su referencia), o vacío. */
    public String selected() {
        return selected;
    }

    public void select(String reference) {
        selected = reference == null ? "" : reference;
    }

    public boolean charTyped(char chr) {
        if (chr >= 32 && chr != 127 && query.length() < 48) {
            query += chr;
        }
        return true;
    }

    public boolean keyPressed(StudioKey key, StudioHost host) {
        switch (key.code()) {
            case ESCAPE -> {
                if (query.isEmpty()) {
                    editing = false;
                } else {
                    query = "";
                }
            }
            case ENTER -> {
                editing = false;
                List<BlueprintSummary> shown = visible(host);
                if (!shown.isEmpty() && shown.stream().noneMatch(blueprint -> blueprint.reference().equals(selected))) {
                    selected = shown.getFirst().reference();
                }
            }
            case BACKSPACE -> {
                if (!query.isEmpty()) {
                    query = key.control() ? "" : query.substring(0, query.length() - 1);
                }
            }
            default -> {
            }
        }
        return true;
    }

    public void draw(StudioUi ui, Column column, StudioHost host, boolean compact) {
        StudioHost.Blueprints catalog = host.blueprints();
        if (!catalog.received()) {
            catalog.refresh(false);
            ui.paragraph(column, host.tr("gui.komaedit.blueprint.loading"), MUTED, 2);
            return;
        }
        List<BlueprintSummary> shown = visible(host);
        Rect searchRow = column.next(18);
        int scopeWidth = Math.max(64, searchRow.width() / 3);
        Rect search = searchRow.withoutRight(scopeWidth, Column.GAP);
        Rect scopeButton = searchRow.rightPart(scopeWidth);
        ui.searchField(search, query, host.tr("gui.komaedit.blueprint.search"), editing, shown.size(),
                        () -> query = "")
                .key(SEARCH_KEY)
                .tooltip(host.tr("gui.komaedit.help.blueprint.search"))
                .onClick(() -> editing = true);
        ui.button(scopeButton, host.tr("gui.komaedit.blueprint.scope." + SCOPES.get(scope)), StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.blueprint.scope") + "\n" + host.tr("gui.komaedit.help.blueprint.scope"))
                .onScroll(direction -> scope = Math.floorMod(scope + direction, SCOPES.size()))
                .onClick(() -> scope = Math.floorMod(scope + 1, SCOPES.size()));
        if (!compact) {
            tagChips(ui, column, host);
        }
        BlueprintSummary current = shown.stream().filter(blueprint -> blueprint.reference().equals(selected))
                .findFirst().orElse(null);
        // «Al servidor» solo sale en los planos propios: su sitio cuenta si hay alguno, para que la tarjeta no salte.
        boolean shareable = host.blueprints().canShare()
                && shown.stream().anyMatch(blueprint -> blueprint.mine() && !blueprint.shared());
        actions(ui, column, host, current, compact, shareable);
        pasteCode(ui, column, host);
        importFile(ui, column, host);

        ui.section(column, host.tr("gui.komaedit.blueprint.library", shown.size()));
        if (shown.isEmpty()) {
            ui.paragraph(column, host.tr(catalog.list().isEmpty()
                    ? "gui.komaedit.blueprint.empty" : "gui.komaedit.blueprint.no_match"), MUTED, 0);
            if (catalog.canSave()) {
                ui.button(column.next(), host.tr("gui.komaedit.blueprint.save_clipboard"), StudioUi.Tone.SECONDARY)
                        .tooltip(host.tr("gui.komaedit.help.blueprint.save_clipboard"))
                        .onClick(() -> host.suggest("/" + "komaedit blueprint save "));
            }
            return;
        }
        grid(ui, column, host, shown);
    }

    /** Los planos del catálogo que pasan el buscador y el filtro, primero los propios y por nombre. */
    public List<BlueprintSummary> visible(StudioHost host) {
        BlueprintSearch search;
        try {
            search = BlueprintSearch.parse(query);
        } catch (IllegalArgumentException exception) {
            // Mientras se escribe una etiqueta a medias («#») no se filtra por ella.
            search = BlueprintSearch.parse(query.replaceAll("#[^\\s]*$", ""));
        }
        BlueprintSearch filter = search;
        String which = SCOPES.get(scope);
        return host.blueprints().list().stream()
                .filter(blueprint -> which.equals("all") || which.equals("mine") == !blueprint.shared())
                .filter(blueprint -> blueprint.matches(filter))
                .sorted(Comparator.comparing(BlueprintSummary::shared).thenComparing(BlueprintSummary::name))
                .toList();
    }

    /** Las etiquetas más usadas como pastillas (una fila): una pulsación la añade o la quita del buscador. */
    private void tagChips(StudioUi ui, Column column, StudioHost host) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BlueprintSummary blueprint : host.blueprints().list()) {
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
                break;
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

    /**
     * El plano elegido en una tarjeta de alto fijo (así la cuadrícula no salta al elegir): nombre, tamaño, Cargar y
     * Estampar con su radio. Sin plano elegido dice cómo elegir. Si los botones no caben en una fila, la tarjeta
     * crece para la segunda; el alto se calcula con el radio más largo y con Compartir, para que no salte nunca.
     */
    private void actions(StudioUi ui, Column column, StudioHost host, BlueprintSummary current, boolean compact,
                         boolean shareable) {
        int widest = 0;
        for (int value : RADII) {
            widest = Math.max(widest, ui.chipWidth(radiusText(host, value)));
        }
        int[] worst = {ui.chipWidth(host.tr("gui.komaedit.blueprint.stamp")) + 8, widest,
                ui.chipWidth(host.tr("gui.komaedit.blueprint.load")),
                shareable ? ui.chipWidth(host.tr("gui.komaedit.blueprint.share")) : 0,
                ui.chipWidth(host.tr("gui.komaedit.share.button")), exportWidth(ui, host)};
        int extra = (buttonRows(worst, column.width() - 12) - 1) * BUTTON_PITCH;
        Rect card = column.next((compact ? 30 : 42) + extra);
        ui.fill(card, FIELD);
        Set<String> tags = activeTags();
        String tag = tags.size() == 1 ? "#" + tags.iterator().next() : null;
        boolean stamp = host.blueprints().canStamp();
        if (current == null) {
            ui.label(host.tr("gui.komaedit.blueprint.selected_none"), card.x() + 8, card.y() + 5, card.width() - 16,
                    TEXT_SECONDARY);
            if (tag != null && stamp) {
                // Con una etiqueta en el buscador, el pincel puede estampar uno al azar de esa etiqueta.
                String label = host.tr("gui.komaedit.blueprint.stamp_tag", tag);
                Rect button = new Rect(card.x() + 6, card.bottom() - 17, Math.min(card.width() - 12,
                        ui.chipWidth(label) + 8), 14);
                ui.button(button, label, StudioUi.Tone.SECONDARY)
                        .tooltip(label + "\n" + host.tr("gui.komaedit.help.blueprint.stamp_tag", tag))
                        .onClick(() -> stamp(host, tag));
            } else {
                ui.label(host.tr("gui.komaedit.blueprint.pick"), card.x() + 8, card.y() + 17, card.width() - 16,
                        MUTED);
            }
            return;
        }
        ui.fill(card.left(2), ACCENT);
        String details = host.tr("gui.komaedit.blueprint.details", current.describeSize(), current.blocks(),
                current.author());
        if (compact) {
            // En poco alto, el nombre y el tamaño en una línea; el resto, en el tooltip.
            String size = current.describeSize();
            int sizeWidth = ui.width(size) + 8;
            ui.bold(current.name(), card.x() + 8, card.y() + 3, card.width() - 16 - sizeWidth, TEXT_STRONG);
            ui.rightAligned(size, card.right() - 6, card.y() + 3, MUTED);
        } else {
            ui.bold(current.name(), card.x() + 8, card.y() + 4, card.width() - 16, TEXT_STRONG);
            ui.label(details, card.x() + 8, card.y() + 14, card.width() - 16, MUTED);
        }
        ui.register(new Rect(card.x(), card.y(), card.width(), 22))
                .tooltip(current.reference() + "\n" + details
                        + (current.tags().isEmpty() ? "" : "\n#" + String.join(" #", current.tags())));
        StudioHost.Blueprints catalog = host.blueprints();
        String reference = current.reference();
        Rect buttons = new Rect(card.x() + 6, card.bottom() - 17 - extra, card.width() - 12, 14);
        boolean share = current.mine() && !current.shared() && catalog.canShare();
        String radiusText = radiusText(host, RADII[radius]);
        int[] widths = {ui.chipWidth(host.tr("gui.komaedit.blueprint.stamp")) + 8,
                ui.chipWidth(radiusText), ui.chipWidth(host.tr("gui.komaedit.blueprint.load")),
                share ? ui.chipWidth(host.tr("gui.komaedit.blueprint.share")) : 0,
                ui.chipWidth(host.tr("gui.komaedit.share.button")), exportWidth(ui, host)};
        List<Rect> cells = Column.flow(widths, 14, buttons.x(), buttons.y(), buttons.width());
        ui.button(cells.get(0), host.tr("gui.komaedit.blueprint.stamp"),
                        stamp ? StudioUi.Tone.SECONDARY : StudioUi.Tone.DISABLED)
                .tooltip(host.tr("gui.komaedit.blueprint.stamp") + "\n" + host.tr(stamp
                        ? "gui.komaedit.help.blueprint.stamp" : "gui.komaedit.help.blueprint.denied"))
                .onClick(() -> {
                    if (stamp) {
                        stamp(host, reference);
                    }
                });
        ui.chip(cells.get(1), radiusText, false, TEXT_SECONDARY)
                .tooltip(host.tr("gui.komaedit.blueprint.radius") + "\n" + host.tr("gui.komaedit.help.blueprint.radius"))
                .onScroll(direction -> radius = Math.floorMod(radius + direction, RADII.length))
                .onClick(() -> radius = Math.floorMod(radius + 1, RADII.length))
                .onReset(() -> radius = 0);
        ui.button(cells.get(2), host.tr("gui.komaedit.blueprint.load"), StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.blueprint.load") + "\n" + host.tr("gui.komaedit.help.blueprint.load"))
                .onClick(() -> {
                    host.send("komaedit blueprint load " + reference);
                    host.status(host.tr("gui.komaedit.status.blueprint.load", reference));
                });
        if (share) {
            ui.button(cells.get(3), host.tr("gui.komaedit.blueprint.share"), StudioUi.Tone.SECONDARY)
                    .tooltip(host.tr("gui.komaedit.blueprint.share") + "\n"
                            + host.tr("gui.komaedit.help.blueprint.share"))
                    .onClick(() -> host.send("komaedit blueprint share " + current.name()));
        }
        ui.button(cells.get(4), host.tr("gui.komaedit.share.button"), StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.share.button") + "\n" + host.tr("gui.komaedit.help.share.button"))
                .onClick(() -> {
                    host.shareExport("plano:" + reference);
                    host.status(host.tr("gui.komaedit.status.share.preparing", reference));
                });
        // «Exportar» y, pegado, el formato a la vista: ‹ .litematic › cambia con un clic o la rueda. Van en un solo
        // hueco del reparto para que nunca queden en filas distintas.
        String format = EXPORT_FORMATS.get(exportFormat);
        int formatWidth = widestFormat(ui, host);
        Rect exportCell = cells.get(5);
        ui.button(exportCell.withoutRight(formatWidth, Column.GAP), host.tr("gui.komaedit.blueprint.export"),
                        StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.blueprint.export") + "\n"
                        + host.tr("gui.komaedit.help.blueprint.export", format))
                .onClick(() -> {
                    String command = "komaedit blueprint export " + reference + " " + format;
                    host.blueprints().expectDownload(command);
                    host.send(command);
                    host.status(host.tr("gui.komaedit.status.blueprint.export", reference, format));
                });
        ui.chip(exportCell.rightPart(formatWidth), host.tr("gui.komaedit.blueprint.export_format", format), false,
                        TEXT_SECONDARY)
                .tooltip(host.tr("gui.komaedit.blueprint.export_format_name") + "\n"
                        + host.tr("gui.komaedit.help.blueprint.export_format"))
                .onScroll(direction -> exportFormat = Math.floorMod(exportFormat + direction, EXPORT_FORMATS.size()))
                .onClick(() -> exportFormat = Math.floorMod(exportFormat + 1, EXPORT_FORMATS.size()))
                .onReset(() -> exportFormat = 0);
    }

    /** «Exportar» con su selector de formato al lado, en un solo hueco. */
    private static int exportWidth(StudioUi ui, StudioHost host) {
        return ui.chipWidth(host.tr("gui.komaedit.blueprint.export")) + Column.GAP + widestFormat(ui, host);
    }

    /** El selector de formato con el nombre más largo, para que la tarjeta no salte al cambiarlo. */
    private static int widestFormat(StudioUi ui, StudioHost host) {
        int widest = 0;
        for (String format : EXPORT_FORMATS) {
            widest = Math.max(widest, ui.chipWidth(host.tr("gui.komaedit.blueprint.export_format", format)));
        }
        return widest;
    }

    /**
     * «Importar archivo…»: abre la lista de los esquemas de la carpeta {@code schematics} del jugador, los más nuevos
     * primero. Un clic sube el archivo y el servidor enseña lo que trae (la ventana de importar) antes de guardarlo.
     */
    private void importFile(StudioUi ui, Column column, StudioHost host) {
        StudioHost.Blueprints catalog = host.blueprints();
        boolean can = catalog.canUpload();
        String label = host.tr(importing ? "gui.komaedit.blueprint.import_close" : "gui.komaedit.blueprint.import_file");
        ui.button(column.next(16), label, can ? StudioUi.Tone.SECONDARY : StudioUi.Tone.DISABLED)
                .tooltip(host.tr("gui.komaedit.blueprint.import_file") + "\n" + host.tr(can
                        ? "gui.komaedit.help.blueprint.import_file" : "gui.komaedit.help.blueprint.upload_denied"))
                .onClick(() -> importing = can && !importing);
        if (!importing || !can) {
            return;
        }
        ui.section(column, host.tr("gui.komaedit.blueprint.import_from"));
        List<StudioHost.LocalFile> files = catalog.localFiles();
        if (files.isEmpty()) {
            ui.paragraph(column, host.tr("gui.komaedit.blueprint.import_none"), MUTED, 0);
            return;
        }
        for (StudioHost.LocalFile file : files.subList(0, Math.min(MAX_LOCAL_FILES, files.size()))) {
            Rect row = column.next(14);
            String size = host.tr("gui.komaedit.blueprint.import_size", Math.max(1, (file.bytes() + 1023) / 1024));
            ui.fill(row, ui.hovered(row) ? CARD_HOVER : CARD);
            ui.label(file.path(), row.x() + 6, row.y() + 3, row.width() - ui.width(size) - 18, TEXT);
            ui.rightAligned(size, row.right() - 6, row.y() + 3, MUTED);
            ui.register(row)
                    .tooltip(file.path() + " · " + size + "\n" + host.tr("gui.komaedit.help.blueprint.import_pick"))
                    .onClick(() -> {
                        boolean started = catalog.upload(file.path());
                        host.status(host.tr(started ? "gui.komaedit.status.blueprint.uploading"
                                : "gui.komaedit.status.blueprint.upload_busy", file.name()));
                        if (started) {
                            importing = false;
                        }
                    });
        }
    }

    /** «Pegar código»: lo que llega de otro servidor (paletas, presets, planos…), enseñando antes qué trae. */
    private static void pasteCode(StudioUi ui, Column column, StudioHost host) {
        ui.button(column.next(16), host.tr("gui.komaedit.share.paste"), StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.share.paste") + "\n" + host.tr("gui.komaedit.help.share.paste"))
                .onClick(() -> {
                    String code = host.clipboard().strip();
                    if (!StudioView.shareCode(code)) {
                        host.status(host.tr("gui.komaedit.status.share.no_code"));
                        return;
                    }
                    host.status(host.tr(host.shareImport(code) ? "gui.komaedit.status.share.reading"
                            : "gui.komaedit.status.share.unsupported"));
                });
    }

    private static final int BUTTON_PITCH = 14 + Column.GAP;

    private static String radiusText(StudioHost host, int value) {
        return host.tr("gui.komaedit.blueprint.radius_short", value == 0 ? host.tr("gui.komaedit.tools.auto")
                : Integer.toString(value));
    }

    /** Cuántas filas ocupan unos botones de estos anchos en {@code width}. */
    private static int buttonRows(int[] widths, int width) {
        int bottom = Column.flow(widths, 14, 0, 0, width).stream().mapToInt(Rect::bottom).max().orElse(14);
        return Math.max(1, (bottom + Column.GAP) / BUTTON_PITCH);
    }

    /**
     * El nombre de un plano en dos líneas como mucho, partiendo tras «_», «-» o un espacio (los nombres son de
     * archivo); solo si ni así cabe, se corta con «…».
     */
    static List<String> nameLines(StudioUi ui, String name, int width) {
        if (ui.width(name) <= width) {
            return List.of(name);
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (character == '_' || character == '-' || character == ' ') {
                parts.add(name.substring(start, index + 1));
                start = index + 1;
            }
        }
        if (start < name.length()) {
            parts.add(name.substring(start));
        }
        StringBuilder first = new StringBuilder();
        int used = 0;
        while (used < parts.size() && ui.width(first + parts.get(used)) <= width) {
            first.append(parts.get(used++));
        }
        if (first.isEmpty() || used == parts.size()) {
            return List.of(ui.fit(name, width));
        }
        return List.of(first.toString().stripTrailing(), ui.fit(String.join("", parts.subList(used, parts.size())),
                width));
    }

    /** Colocar el plano elegido con vista previa: el botón del pie en esta etapa. */
    public void place(StudioHost host) {
        BlueprintSummary current = visible(host).stream()
                .filter(blueprint -> blueprint.reference().equals(selected)).findFirst().orElse(null);
        if (current == null) {
            host.status(host.tr("gui.komaedit.blueprint.pick"));
            return;
        }
        if (!host.blueprints().canPlace()) {
            host.status(host.tr("gui.komaedit.help.blueprint.denied"));
            return;
        }
        host.send("komaedit blueprint place " + current.reference() + " -v");
        host.status(host.tr("gui.komaedit.status.blueprint.place", current.reference()));
    }

    /** El nombre del plano elegido (sin el prefijo de los del servidor), o vacío. */
    public String selectedName(StudioHost host) {
        return host.blueprints().list().stream().filter(blueprint -> blueprint.reference().equals(selected))
                .map(BlueprintSummary::name).findFirst().orElse("");
    }

    public String placeLabel(StudioHost host) {
        return selected.isEmpty() ? host.tr("gui.komaedit.blueprint.pick_short") : host.tr("gui.komaedit.blueprint.place");
    }

    public StudioUi.Tone placeTone(StudioHost host) {
        return selected.isEmpty() || !host.blueprints().canPlace() ? StudioUi.Tone.DISABLED : StudioUi.Tone.PRIMARY;
    }

    private void stamp(StudioHost host, String what) {
        String command = stampCommand(what);
        host.send(command);
        host.status(host.tr("gui.komaedit.status.blueprint.stamp", what));
        stamped = what;
        brush.linked("blueprints", "", command, host.now());
    }

    private String stampCommand(String what) {
        return "komaedit pincel stamp " + what + (RADII[radius] > 0 ? " " + RADII[radius] : "");
    }

    /**
     * Lo que estamparía ahora el pincel según la etapa: el plano elegido (o, si se estampó una etiqueta, la etiqueta
     * del buscador) con el radio elegido.
     */
    String stampNow() {
        if (stamped == null) {
            return null;
        }
        if (stamped.startsWith("#")) {
            Set<String> tags = activeTags();
            return stampCommand(tags.size() == 1 ? "#" + tags.iterator().next() : stamped);
        }
        return stampCommand(selected.isEmpty() ? stamped : selected);
    }

    /**
     * Pasa al pincel de estampar de la mano los cambios de la etapa (otro plano, otro radio) 0,3 s después del último,
     * mientras ese pincel siga en la mano; nunca toca otro objeto ({@link BrushSync}).
     */
    public void syncBrush(StudioHost host) {
        if (brush.card() == null) {
            return;
        }
        String send = brush.tick(stampNow(), host.heldTool(), host.now());
        if (send != null) {
            host.send(send);
            host.status(host.tr("gui.komaedit.tools.status.brush_synced", host.tr("gui.komaedit.blueprint.stamp")));
        }
    }

    /** El pincel que se sigue, para las pruebas. */
    public BrushSync brush() {
        return brush;
    }

    /** Cuadrícula de tarjetas: miniatura arriba y nombre y tamaño debajo. */
    private void grid(StudioUi ui, Column column, StudioHost host, List<BlueprintSummary> shown) {
        int columns = Math.max(2, (column.width() + Column.GAP) / (MIN_CARD + Column.GAP));
        int cardWidth = (column.width() - (columns - 1) * Column.GAP) / columns;
        // Si algún nombre necesita dos líneas, todas las tarjetas ganan una (la cuadrícula sigue alineada).
        boolean tall = shown.stream().anyMatch(blueprint -> nameLines(ui, blueprint.name(), cardWidth - 8).size() > 1);
        List<Rect> cells = column.grid(shown.size(), columns, cardWidth + CAPTION + (tall ? StudioUi.TILE_LINE : 0));
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
            ui.fill(image, THUMBNAIL);
            StudioImage texture = host.blueprints().thumbnail(blueprint.thumbnail());
            if (texture != null) {
                ui.image(texture, image);
            } else {
                wanted.add(blueprint.thumbnail());
                ui.centered("…", image, MUTED);
            }
            if (blueprint.shared()) {
                ui.icon(StudioIcons.Icon.STAR, image.right() - 13, image.y() + 1, FAVORITE);
            }
            if (chosen) {
                ui.border(cell, ACCENT);
                ui.border(cell.inset(1, 1), ACCENT);
            } else if (hovered) {
                ui.border(cell, OUTLINE);
            }
            int y = image.bottom() + 3;
            for (String line : nameLines(ui, blueprint.name(), cell.width() - 8)) {
                ui.label(line, cell.x() + 4, y, cell.width() - 8, chosen ? TEXT_STRONG : TEXT);
                y += StudioUi.TILE_LINE;
            }
            ui.label(blueprint.describeSize(), cell.x() + 4, y, cell.width() - 8, MUTED);
            ui.register(cell)
                    .tooltip(blueprint.name() + "\n" + host.tr("gui.komaedit.blueprint.details",
                            blueprint.describeSize(), blueprint.blocks(), blueprint.author())
                            + (blueprint.tags().isEmpty() ? "" : "\n#" + String.join(" #", blueprint.tags())))
                    .onClick(() -> selected = blueprint.reference());
        }
        host.blueprints().request(wanted);
    }
}
