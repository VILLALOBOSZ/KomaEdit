package com.komainu.moxia.client.reference;

import com.komainu.moxia.reference.ReferenceFiles;
import com.komainu.moxia.reference.ReferenceGrid;
import com.komainu.moxia.reference.ReferenceImage;
import com.komainu.moxia.reference.ReferenceSet;
import com.komainu.moxia.reference.ScreenFrame;
import com.komainu.moxia.studio.ui.StudioTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Las imágenes de referencia ({@code /keref}, Ctrl+Mayús+I o la tarjeta del Studio). A la izquierda, las imágenes de
 * {@code config/komaedit/references/} con su miniatura: un clic la abre. A la derecha, las abiertas en este mundo y
 * los ajustes de la elegida: modo, opacidad, filtrado, rejilla y, según el modo, «a través» y colocar las ventanas, o
 * profundidad, traerla delante, tumbarla y moverla con el gizmo. Se pueden arrastrar imágenes PNG o JPG a la ventana
 * del juego para copiarlas a la carpeta y abrirlas.
 */
public final class ReferenceScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int ROW = 22;
    private static final int THUMB = 44;
    private static final int CELL_WIDTH = 52;
    private static final int CELL_HEIGHT = 58;
    private static final int CHIP = 24;
    private static final int[] GRID_PIXELS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 32, 40, 48, 64, 80, 96, 128,
            192, 256, 512};
    private static final List<Integer> MAJORS = List.of(0, 4, 5, 8, 10, 16);
    private static final long STATUS_MILLIS = 6_000L;
    /** La elegida se recuerda entre aperturas de la pantalla. */
    private static String selected;

    private final Screen parent;
    private List<String> files = List.of();
    private int scroll;
    private String status = "";
    private boolean statusError;
    private long statusAt;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int listTop;
    private int listBottom;
    private int chipsTop;
    private SettingsList settings;
    private String settingsFor;

    public ReferenceScreen(Screen parent) {
        super(Text.translatable("gui.komaedit.reference.title"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- montaje

    @Override
    protected void init() {
        files = ReferenceFiles.list(ReferenceClient.folder());
        ReferenceTextures.refresh();
        leftWidth = Math.clamp((int) (width * 0.42), 140, 280);
        rightX = MARGIN + leftWidth + 10;
        rightWidth = width - rightX - MARGIN;
        listTop = 36;
        listBottom = height - 34;
        chipsTop = 38;
        ReferenceSet set = ReferenceClient.set();
        if (selected == null || set.byId(selected).isEmpty()) {
            selected = set.references().isEmpty() ? null : set.references().getLast().id();
        }
        int bottom = height - 26;
        int third = (leftWidth - 8) / 3;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.komaedit.reference.folder"), button -> {
            Path folder = ReferenceClient.folder();
            try {
                Files.createDirectories(folder);
            } catch (java.io.IOException ignored) {
                // Si no se puede crear, el sistema dirá que no existe.
            }
            Util.getOperatingSystem().open(folder);
        }).dimensions(MARGIN, bottom, third, 20)
                .tooltip(Tooltip.of(Text.translatable("gui.komaedit.reference.folder.tooltip"))).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.komaedit.reference.reload"), button -> {
            ReferenceTextures.refresh();
            files = ReferenceFiles.list(ReferenceClient.folder());
        }).dimensions(MARGIN + third + 4, bottom, third, 20)
                .tooltip(Tooltip.of(Text.translatable("gui.komaedit.reference.reload.tooltip"))).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
                .dimensions(MARGIN + 2 * (third + 4), bottom, third, 20).build());
        addDrawableChild(CyclingButtonWidget.onOffBuilder(set.shown())
                .tooltip(value -> Tooltip.of(Text.translatable("gui.komaedit.reference.shown_all.tooltip")))
                .build(width - MARGIN - 120, 6, 120, 20, Text.translatable("gui.komaedit.reference.shown_all"),
                        (button, value) -> ReferenceClient.update(ReferenceClient.set().withShown(value))));
        Optional<ReferenceImage> chosen = chosen();
        // Al rehacer la pantalla por un cambio de la misma, la lista sigue donde estaba.
        double keep = settings != null && selected != null && selected.equals(settingsFor)
                ? settings.getScrollAmount() : 0.0;
        settings = null;
        settingsFor = selected;
        if (chosen.isPresent()) {
            int top = settingsTop();
            settings = new SettingsList(rightX, top, rightWidth, Math.max(ROW, height - top - 6));
            settings(chosen.get(), settings);
            addDrawableChild(settings);
            settings.setScrollAmount(keep);
        }
    }

    private Optional<ReferenceImage> chosen() {
        return selected == null ? Optional.empty() : ReferenceClient.set().byId(selected);
    }

    /**
     * Las filas de ajustes de la elegida, de dos en dos, en una lista que se desplaza con la rueda (así caben en
     * cualquier tamaño de pantalla): modo y visible, opacidad y filtro, la rejilla y, según el modo, lo de la ventana o
     * lo del plano en el mundo.
     */
    private void settings(ReferenceImage reference, SettingsList list) {
        list.row(CyclingButtonWidget.<ReferenceImage.Mode>builder(mode ->
                                Text.translatable("gui.komaedit.reference.mode." + mode.id()))
                        .values(ReferenceImage.Mode.values())
                        .initially(reference.mode())
                        .tooltip(mode -> Tooltip.of(Text.translatable("gui.komaedit.reference.mode.tooltip")))
                        .build(0, 0, 100, 20, Text.translatable("gui.komaedit.reference.mode"), (button, mode) -> {
                            change(current -> current.withMode(mode));
                            clearAndInit();
                        }),
                CyclingButtonWidget.onOffBuilder(reference.visible())
                        .tooltip(value -> Tooltip.of(Text.translatable("gui.komaedit.reference.visible.tooltip")))
                        .build(0, 0, 100, 20, Text.translatable("gui.komaedit.reference.visible"),
                                (button, value) -> change(current -> current.withVisible(value))));
        list.row(slider((reference.opacity() - ReferenceImage.MIN_OPACITY)
                                / (ReferenceImage.MAX_OPACITY - ReferenceImage.MIN_OPACITY), 0,
                        value -> Text.translatable("gui.komaedit.reference.opacity", Math.round(opacity(value) * 100.0)),
                        value -> change(current -> current.withOpacity(opacity(value)))),
                CyclingButtonWidget.<Boolean>builder(smooth -> Text.translatable(smooth
                                ? "gui.komaedit.reference.filter.smooth" : "gui.komaedit.reference.filter.sharp"))
                        .values(Boolean.FALSE, Boolean.TRUE)
                        .initially(reference.smooth())
                        .tooltip(value -> Tooltip.of(Text.translatable("gui.komaedit.reference.filter.tooltip")))
                        .build(0, 0, 100, 20, Text.translatable("gui.komaedit.reference.filter"),
                                (button, value) -> change(current -> current.withSmooth(value))));
        ReferenceGrid grid = reference.grid();
        list.row(CyclingButtonWidget.onOffBuilder(grid.enabled())
                        .tooltip(value -> Tooltip.of(Text.translatable("gui.komaedit.reference.grid.tooltip")))
                        .build(0, 0, 100, 20, Text.translatable("gui.komaedit.reference.grid"), (button, value) -> {
                            change(current -> current.withGrid(current.grid().withEnabled(value)));
                            clearAndInit();
                        }),
                slider(gridPosition(grid.pixels()), GRID_PIXELS.length - 1,
                        value -> Text.translatable("gui.komaedit.reference.grid.pixels", gridPixels(value)),
                        value -> change(current -> current.withGrid(current.grid().withPixels(gridPixels(value))
                                .withEnabled(true)))));
        if (grid.enabled()) {
            list.row(CyclingButtonWidget.<ReferenceGrid.Color>builder(color ->
                                    Text.translatable("gui.komaedit.reference.grid.color." + color.id()))
                            .values(ReferenceGrid.Color.values())
                            .initially(grid.color())
                            .build(0, 0, 100, 20, Text.translatable("gui.komaedit.reference.grid.color"),
                                    (button, value) -> change(current -> current.withGrid(current.grid()
                                            .withColor(value)))),
                    CyclingButtonWidget.<Integer>builder(major -> major == 0
                                    ? Text.translatable("gui.komaedit.reference.grid.major.none")
                                    : Text.translatable("gui.komaedit.reference.grid.major.every", major))
                            .values(MAJORS)
                            .initially(MAJORS.contains(grid.major()) ? grid.major() : 0)
                            .tooltip(value -> Tooltip.of(Text.translatable("gui.komaedit.reference.grid.major.tooltip")))
                            .build(0, 0, 100, 20, Text.translatable("gui.komaedit.reference.grid.major"),
                                    (button, value) -> change(current -> current.withGrid(current.grid()
                                            .withMajor(value)))));
            if (grid.pixels() > 1) {
                int last = grid.pixels() - 1;
                list.row(offset("x", grid.offsetX(), last), offset("y", grid.offsetY(), last));
            }
        }
        if (reference.mode() == ReferenceImage.Mode.SCREEN) {
            list.row(CyclingButtonWidget.onOffBuilder(reference.screen().clickThrough())
                            .tooltip(value -> Tooltip.of(Text.translatable("gui.komaedit.reference.through.tooltip")))
                            .build(0, 0, 100, 20, Text.translatable("gui.komaedit.reference.through"),
                                    (button, value) -> change(current -> current.withScreen(current.screen()
                                            .withClickThrough(value)))),
                    button("arrange", () -> {
                        change(current -> current.withVisible(true));
                        ReferenceClient.update(ReferenceClient.set().withShown(true));
                        if (client != null) {
                            client.setScreen(new ReferenceArrangeScreen(this));
                        }
                    }));
            list.row(button("reset", () -> change(current -> current.withScreen(ScreenFrame.DEFAULT))),
                    remove(reference));
        } else {
            list.row(CyclingButtonWidget.onOffBuilder(reference.world().depth())
                            .tooltip(value -> Tooltip.of(Text.translatable("gui.komaedit.reference.depth.tooltip")))
                            .build(0, 0, 100, 20, Text.translatable("gui.komaedit.reference.depth"),
                                    (button, value) -> change(current -> current.withWorld(current.world()
                                            .withDepth(value)))),
                    button("cell_block", () -> {
                        change(current -> current.withGrid(current.grid().withEnabled(true)).cellPerBlock());
                        clearAndInit();
                    }));
            list.row(button("bring", () -> change(current -> current.withWorld(ReferenceClient.inFront(
                            current.world())))),
                    button("floor", () -> change(current -> current.withWorld(ReferenceClient.onFloor(
                            current.world())))));
            list.row(button("gizmo", () -> ReferenceClient.edit(reference.id())), remove(reference));
        }
    }

    private ButtonWidget button(String id, Runnable action) {
        return ButtonWidget.builder(Text.translatable("gui.komaedit.reference." + id), button -> action.run())
                .dimensions(0, 0, 100, 20)
                .tooltip(Tooltip.of(Text.translatable("gui.komaedit.reference." + id + ".tooltip")))
                .build();
    }

    private ButtonWidget remove(ReferenceImage reference) {
        return button("remove", () -> {
            ReferenceClient.update(ReferenceClient.set().remove(reference.id()));
            selected = null;
            clearAndInit();
        });
    }

    /** Dónde empieza la primera columna o fila de la rejilla, de 0 a {@code last} píxeles. */
    private SliderWidget offset(String axis, int value, int last) {
        SliderWidget slider = slider(value / (double) last, last,
                position -> Text.translatable(axis.equals("x") ? "gui.komaedit.reference.grid.offset_x"
                        : "gui.komaedit.reference.grid.offset_y", (int) Math.round(position * last)),
                position -> change(current -> {
                    int moved = (int) Math.round(position * last);
                    ReferenceGrid grid = current.grid();
                    return current.withGrid(axis.equals("x") ? grid.withOffset(moved, grid.offsetY())
                            : grid.withOffset(grid.offsetX(), moved));
                }));
        slider.setTooltip(Tooltip.of(Text.translatable("gui.komaedit.reference.grid.offset.tooltip")));
        return slider;
    }

    /** Donde empiezan los ajustes: bajo las fichas (una o dos filas) y el resumen de dos líneas. */
    private int settingsTop() {
        return chipsTop + chipRows() * (CHIP + 3) + 24;
    }

    private int chipsPerRow() {
        return Math.max(1, (rightWidth + 3) / (CHIP + 3));
    }

    private int chipRows() {
        int count = Math.max(1, ReferenceClient.set().references().size());
        return (count + chipsPerRow() - 1) / chipsPerRow();
    }

    private static double opacity(double value) {
        return ReferenceImage.MIN_OPACITY + value * (ReferenceImage.MAX_OPACITY - ReferenceImage.MIN_OPACITY);
    }

    private static double gridPosition(int pixels) {
        int best = 0;
        for (int index = 0; index < GRID_PIXELS.length; index++) {
            if (Math.abs(GRID_PIXELS[index] - pixels) < Math.abs(GRID_PIXELS[best] - pixels)) {
                best = index;
            }
        }
        return best / (double) (GRID_PIXELS.length - 1);
    }

    private static int gridPixels(double value) {
        return GRID_PIXELS[(int) Math.round(value * (GRID_PIXELS.length - 1))];
    }

    private static void change(UnaryOperator<ReferenceImage> change) {
        if (selected == null) {
            return;
        }
        ReferenceClient.set().byId(selected).ifPresent(reference -> ReferenceClient.update(change.apply(reference)));
    }

    /**
     * Un deslizador. Con {@code steps} mayor que cero salta entre esas posiciones (los valores de la rejilla); si no,
     * es continuo (la opacidad).
     */
    private static SliderWidget slider(double initial, int steps, java.util.function.DoubleFunction<Text> label,
                                       java.util.function.DoubleConsumer apply) {
        return new SliderWidget(0, 0, 100, 20, Text.empty(), Math.clamp(initial, 0.0, 1.0)) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(label.apply(snapped()));
            }

            @Override
            protected void applyValue() {
                apply.accept(snapped());
            }

            private double snapped() {
                return steps > 0 ? Math.round(value * steps) / (double) steps : value;
            }
        };
    }

    // ---------------------------------------------------------------- dibujo

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(textRenderer, title, MARGIN, 12, StudioTheme.TEXT_STRONG);
        folder(context, mouseX, mouseY);
        opened(context, mouseX, mouseY);
        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < STATUS_MILLIS) {
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status, width - 2 * MARGIN), MARGIN,
                    height - 37 + 2, statusError ? StudioTheme.DANGER : StudioTheme.SUCCESS);
        }
    }

    private void folder(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.komaedit.reference.files", files.size()),
                MARGIN, listTop - 11, StudioTheme.TEXT_SECONDARY);
        context.fill(MARGIN, listTop, MARGIN + leftWidth, listBottom - 4, StudioTheme.fade(StudioTheme.PANEL, 0.7));
        if (files.isEmpty()) {
            context.drawTextWrapped(textRenderer, Text.translatable("gui.komaedit.reference.empty"), MARGIN + 6,
                    listTop + 8, leftWidth - 12, StudioTheme.TEXT_SECONDARY);
            return;
        }
        int columns = columns();
        scroll = Math.clamp(scroll, 0, maxScroll());
        context.enableScissor(MARGIN, listTop, MARGIN + leftWidth, listBottom - 4);
        String hovered = null;
        for (int index = 0; index < files.size(); index++) {
            int x = MARGIN + 4 + index % columns * CELL_WIDTH;
            int y = listTop + 4 + index / columns * CELL_HEIGHT - scroll;
            if (y + CELL_HEIGHT < listTop || y > listBottom) {
                continue;
            }
            String file = files.get(index);
            boolean over = mouseX >= x && mouseX < x + CELL_WIDTH - 4 && mouseY >= y && mouseY < y + CELL_HEIGHT - 4
                    && mouseY >= listTop && mouseY < listBottom - 4;
            context.fill(x, y, x + CELL_WIDTH - 4, y + CELL_HEIGHT - 4, over ? StudioTheme.CARD_HOVER
                    : StudioTheme.CARD);
            thumbnail(context, file, x + 2, y + 2, THUMB, THUMB);
            String name = textRenderer.trimToWidth(ReferenceImage.displayName(file), CELL_WIDTH - 6);
            context.drawText(textRenderer, name, x + 2, y + THUMB + 3, StudioTheme.TEXT, false);
            if (over) {
                hovered = file;
            }
        }
        context.disableScissor();
        if (hovered != null) {
            context.drawTooltip(textRenderer, List.of(Text.literal(hovered),
                    Text.translatable("gui.komaedit.reference.open_hint")), mouseX, mouseY);
        }
    }

    /** La miniatura dentro de la caja sin deformarla, o un aviso mientras llega o si no se puede leer. */
    private void thumbnail(DrawContext context, String file, int x, int y, int boxWidth, int boxHeight) {
        context.fill(x, y, x + boxWidth, y + boxHeight, StudioTheme.THUMBNAIL);
        Optional<ReferenceTextures.Texture> texture = ReferenceTextures.thumbnail(file);
        if (texture.isEmpty()) {
            String error = ReferenceTextures.error(file);
            context.drawCenteredTextWithShadow(textRenderer, error == null ? "…" : "!", x + boxWidth / 2,
                    y + boxHeight / 2 - 4, error == null ? StudioTheme.MUTED : StudioTheme.DANGER);
            return;
        }
        double scale = Math.min(boxWidth / (double) texture.get().width(), boxHeight / (double) texture.get().height());
        int drawWidth = Math.max(1, (int) Math.round(texture.get().width() * scale));
        int drawHeight = Math.max(1, (int) Math.round(texture.get().height() * scale));
        texture.get().filter(Math.max(texture.get().width(), texture.get().height()) > ReferenceImage.SHARP_UP_TO);
        context.drawTexture(texture.get().id(), x + (boxWidth - drawWidth) / 2, y + (boxHeight - drawHeight) / 2,
                drawWidth, drawHeight, 0.0F, 0.0F, 1, 1, 1, 1);
    }

    private void opened(DrawContext context, int mouseX, int mouseY) {
        ReferenceSet set = ReferenceClient.set();
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.komaedit.reference.opened",
                set.references().size(), ReferenceSet.MAX), rightX, chipsTop - 11, StudioTheme.TEXT_SECONDARY);
        if (set.references().isEmpty()) {
            context.drawTextWrapped(textRenderer, Text.translatable("gui.komaedit.reference.none"), rightX,
                    chipsTop + 4, rightWidth, StudioTheme.TEXT_SECONDARY);
            return;
        }
        ReferenceImage tip = null;
        for (int index = 0; index < set.references().size(); index++) {
            ReferenceImage reference = set.references().get(index);
            int x = chipX(index);
            int y = chipY(index);
            boolean chosen = reference.id().equals(selected);
            context.fill(x - 1, y - 1, x + CHIP + 1, y + CHIP + 1, chosen ? StudioTheme.ACCENT : StudioTheme.OUTLINE);
            thumbnail(context, reference.file(), x, y, CHIP, CHIP);
            if (!reference.visible() || !set.shown()) {
                context.fill(x, y, x + CHIP, y + CHIP, StudioTheme.SCRIM);
            }
            int badge = reference.mode() == ReferenceImage.Mode.WORLD ? StudioTheme.SUCCESS : StudioTheme.ACCENT_TEXT;
            context.fill(x + CHIP - 5, y + CHIP - 5, x + CHIP - 1, y + CHIP - 1, badge);
            if (mouseX >= x && mouseX < x + CHIP && mouseY >= y && mouseY < y + CHIP) {
                tip = reference;
            }
        }
        Optional<ReferenceImage> chosen = chosen();
        if (chosen.isPresent()) {
            int y = chipsTop + chipRows() * (CHIP + 3) + 1;
            ReferenceImage reference = chosen.get();
            String name = reference.file();
            String error = ReferenceTextures.error(reference.file());
            context.drawText(textRenderer, textRenderer.trimToWidth(name, rightWidth), rightX, y,
                    StudioTheme.TEXT_STRONG, false);
            Text summary = error == null ? ReferenceTexts.summary(reference)
                    : Text.translatable("gui.komaedit.reference.unreadable", error);
            context.drawText(textRenderer, textRenderer.trimToWidth(summary, rightWidth).getString(), rightX,
                    y + 10, error == null ? StudioTheme.TEXT_SECONDARY : StudioTheme.DANGER, false);
        }
        if (tip != null) {
            context.drawTooltip(textRenderer, List.of(Text.literal(tip.displayName()),
                    Text.translatable("gui.komaedit.reference.mode." + tip.mode().id())
                            .append(tip.visible() ? Text.empty()
                                    : Text.literal(" · ").append(Text.translatable("gui.komaedit.reference.hidden_one")))),
                    mouseX, mouseY);
        }
    }

    private int chipX(int index) {
        return rightX + 1 + index % chipsPerRow() * (CHIP + 3);
    }

    private int chipY(int index) {
        return chipsTop + 1 + index / chipsPerRow() * (CHIP + 3);
    }

    private int columns() {
        return Math.max(1, (leftWidth - 4) / CELL_WIDTH);
    }

    private int maxScroll() {
        int rows = (files.size() + columns() - 1) / columns();
        return Math.max(0, rows * CELL_HEIGHT + 8 - (listBottom - 4 - listTop));
    }

    // ---------------------------------------------------------------- ratón y archivos

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        ReferenceSet set = ReferenceClient.set();
        for (int index = 0; index < set.references().size(); index++) {
            int x = chipX(index);
            int y = chipY(index);
            if (mouseX >= x && mouseX < x + CHIP && mouseY >= y && mouseY < y + CHIP) {
                selected = set.references().get(index).id();
                clearAndInit();
                return true;
            }
        }
        if (mouseX >= MARGIN && mouseX < MARGIN + leftWidth && mouseY >= listTop && mouseY < listBottom - 4) {
            int columns = columns();
            int column = (int) (mouseX - MARGIN - 4) / CELL_WIDTH;
            int row = (int) (mouseY - listTop - 4 + scroll) / CELL_HEIGHT;
            int index = row * columns + column;
            if (column >= 0 && column < columns && index >= 0 && index < files.size()) {
                openFile(files.get(index));
                return true;
            }
        }
        return false;
    }

    private void openFile(String file) {
        Optional<String> id = ReferenceClient.open(file, this::problem);
        if (id.isPresent()) {
            selected = id.get();
            status(Text.translatable("gui.komaedit.reference.opened_one", ReferenceImage.displayName(file))
                    .getString(), false);
            clearAndInit();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX < MARGIN + leftWidth && mouseY >= listTop && mouseY < listBottom) {
            scroll = Math.clamp(scroll - (int) Math.round(vertical * CELL_HEIGHT / 2.0), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    /** Imágenes arrastradas a la ventana del juego: se copian a la carpeta y se abren. */
    @Override
    public void filesDragged(List<Path> paths) {
        int added = 0;
        List<String> problems = new ArrayList<>();
        for (Path path : paths) {
            try {
                String name = ReferenceFiles.importFile(ReferenceClient.folder(), path);
                Optional<String> id = ReferenceClient.open(name, problems::add);
                if (id.isPresent()) {
                    selected = id.get();
                    added++;
                }
            } catch (IllegalArgumentException exception) {
                problems.add(exception.getMessage());
            }
        }
        files = ReferenceFiles.list(ReferenceClient.folder());
        if (!problems.isEmpty()) {
            problem(problems.getFirst());
        } else if (added > 0) {
            status(Text.translatable("gui.komaedit.reference.dropped", added).getString(), false);
        }
        clearAndInit();
    }

    private void problem(String message) {
        status(message, true);
    }

    private void status(String message, boolean error) {
        status = message == null ? "" : message;
        statusError = error;
        statusAt = System.currentTimeMillis();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        ReferenceClient.save();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    /** Los ajustes en filas de dos controles, con la rueda y la barra de vanilla y sin su fondo. */
    private final class SettingsList extends ElementListWidget<SettingsList.Row> {
        SettingsList(int x, int y, int width, int height) {
            super(MinecraftClient.getInstance(), width, height, y, ROW);
            setX(x);
        }

        void row(ClickableWidget left, ClickableWidget right) {
            addEntry(new Row(right == null ? List.of(left) : List.of(left, right)));
        }

        @Override
        public int getRowWidth() {
            return width - 8;
        }

        @Override
        public int getRowLeft() {
            return getX();
        }

        @Override
        protected int getScrollbarX() {
            return getX() + width - 6;
        }

        @Override
        protected void drawMenuListBackground(DrawContext context) {
            // Sin el fondo de vanilla: va sobre el del Studio.
        }

        @Override
        protected void drawHeaderAndFooterSeparators(DrawContext context) {
            // Ni sus rayas de arriba y abajo.
        }

        final class Row extends ElementListWidget.Entry<Row> {
            private final List<ClickableWidget> widgets;

            Row(List<ClickableWidget> widgets) {
                this.widgets = widgets;
            }

            @Override
            public List<? extends Element> children() {
                return widgets;
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return widgets;
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float delta) {
                int column = (entryWidth - 4) / 2;
                for (int position = 0; position < widgets.size(); position++) {
                    ClickableWidget widget = widgets.get(position);
                    widget.setX(x + position * (column + 4));
                    widget.setY(y);
                    widget.setWidth(column);
                    widget.render(context, mouseX, mouseY, delta);
                }
            }
        }
    }
}
