package com.komainu.moxia.client.utility;

import com.komainu.moxia.notes.NoteRequest;
import com.komainu.moxia.notes.NoteSketch;
import com.komainu.moxia.notes.WorldNote;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * La lista de notas de esta dimensión ({@code /kegui notas}): poner una en el bloque que miras y, de cada una,
 * saltar, resolver (o reabrir) y borrar, con filtros. Todo lo hace el servidor con {@code /komaedit nota}.
 */
public final class NotesScreen extends Screen {
    private static final int ROW_WIDTH = 480;
    private static final int MAX_LENGTH = 200;
    private final Screen parent;
    private TextFieldWidget text;
    private UtilityList list;
    private State state = State.OPEN;
    private Whose whose = Whose.ALL;
    private boolean team;

    /** Qué notas enseñar según su estado. */
    enum State {
        OPEN, ALL, RESOLVED;

        Text label() {
            return Text.translatable("gui.komaedit.notes.filter." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** Y según de quién. */
    enum Whose {
        ALL, MINE, TEAM;

        Text label() {
            return Text.translatable("gui.komaedit.notes.whose." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public NotesScreen(Screen parent) {
        super(Text.translatable("gui.komaedit.notes.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int width = Math.min(ROW_WIDTH, this.width - 20);
        int left = (this.width - width) / 2;
        String typed = text == null ? "" : text.getText();
        int field = width - 3 * (UtilityList.BUTTON_WIDTH + 2) - 30;
        text = new TextFieldWidget(textRenderer, left, 30, field, 20, Text.translatable("gui.komaedit.notes.text"));
        text.setMaxLength(MAX_LENGTH);
        text.setText(typed);
        text.setPlaceholder(Text.translatable("gui.komaedit.notes.text"));
        addDrawableChild(text);
        CyclingButtonWidget<Boolean> teamToggle = addDrawableChild(CyclingButtonWidget.onOffBuilder(team)
                .tooltip(ignored -> net.minecraft.client.gui.tooltip.Tooltip.of(
                        Text.translatable("gui.komaedit.notes.team.help")))
                .build(left + field + 2, 30, UtilityList.BUTTON_WIDTH + 30, 20,
                        Text.translatable("gui.komaedit.notes.team"), (button, value) -> team = value));
        teamToggle.active = ClientNotes.canShare();
        ButtonWidget add = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.komaedit.notes.add"),
                        button -> add())
                .dimensions(left + width - 2 * (UtilityList.BUTTON_WIDTH + 2) + 2, 30,
                        2 * UtilityList.BUTTON_WIDTH + 2, 20)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("gui.komaedit.notes.add.help")))
                .build());
        add.active = ClientNotes.canWrite() || ClientNotes.canShare();
        addDrawableChild(CyclingButtonWidget.builder(State::label).values(State.values()).initially(state)
                .build(left, 54, 150, 20, Text.translatable("gui.komaedit.notes.filter"), (button, value) -> {
                    state = value;
                    refresh();
                }));
        addDrawableChild(CyclingButtonWidget.builder(Whose::label).values(Whose.values()).initially(whose)
                .build(left + 154, 54, 150, 20, Text.translatable("gui.komaedit.notes.whose"), (button, value) -> {
                    whose = value;
                    refresh();
                }));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(!ClientNotes.hidden())
                .build(left + width - 150, 54, 150, 20, Text.translatable("gui.komaedit.notes.show"),
                        (button, value) -> ClientNotes.hidden(!value)));
        list = addDrawableChild(new UtilityList(client, this.width, this.height - 116, 80, width));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
                .dimensions(this.width / 2 - 75, this.height - 28, 150, 20).build());
        refresh();
        ClientNotes.onChange(() -> {
            if (client != null && client.currentScreen == this) {
                refresh();
            }
        });
    }

    private void add() {
        ClientPlayerEntity player = client == null ? null : client.player;
        String value = text.getText().strip();
        if (player == null || value.isEmpty()) {
            return;
        }
        player.networkHandler.sendChatCommand("komaedit nota poner " + NoteRequest.quote(value)
                + (team ? " -e" : ""));
        text.setText("");
    }

    private void refresh() {
        if (list == null || client == null || client.player == null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        List<WorldNote> notes = new ArrayList<>();
        for (WorldNote note : ClientNotes.notes()) {
            boolean stateMatches = switch (state) {
                case OPEN -> !note.resolved();
                case RESOLVED -> note.resolved();
                case ALL -> true;
            };
            boolean whoseMatches = switch (whose) {
                case MINE -> note.authorId().equals(player.getUuid());
                case TEAM -> note.team();
                case ALL -> true;
            };
            if (stateMatches && whoseMatches) {
                notes.add(note);
            }
        }
        notes.sort(Comparator.comparingDouble(note -> distanceSquared(note, player)));
        long now = System.currentTimeMillis();
        List<UtilityList.Row> rows = new ArrayList<>();
        for (WorldNote note : notes) {
            List<ButtonWidget> buttons = new ArrayList<>();
            buttons.add(UtilityList.button(Text.translatable("gui.komaedit.notes.go"), null, button -> {
                player.networkHandler.sendChatCommand("komaedit nota ir " + note.id());
                close();
            }));
            buttons.add(UtilityList.button(Text.translatable(note.resolved() ? "gui.komaedit.notes.reopen"
                    : "gui.komaedit.notes.resolve"), null, button -> player.networkHandler.sendChatCommand(
                    "komaedit nota " + (note.resolved() ? "reabrir " : "resolver ") + note.id())));
            if (note.authorId().equals(player.getUuid()) || note.team() && ClientNotes.manages()) {
                buttons.add(UtilityList.button(Text.translatable("gui.komaedit.notes.delete"), null,
                        button -> player.networkHandler.sendChatCommand("komaedit nota borrar " + note.id())));
            }
            String detail = NoteSketch.meta(note, ZoneId.systemDefault(), now, ClientWords.INSTANCE) + " · "
                    + ClientWords.INSTANCE.tr("gui.komaedit.views.distance",
                    Math.round(Math.sqrt(distanceSquared(note, player))));
            rows.add(new UtilityList.Row(note.icon().pixels(), note.color(), Text.literal(note.text()),
                    Text.literal(detail), buttons));
        }
        list.set(rows);
    }

    private static double distanceSquared(WorldNote note, ClientPlayerEntity player) {
        double dx = note.labelX() - player.getX();
        double dy = note.labelY() - player.getY();
        double dz = note.labelZ() - player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        String hint = !ClientNotes.known() ? ClientWords.INSTANCE.tr("gui.komaedit.notes.no_server")
                : ClientNotes.notes().isEmpty() ? ClientWords.INSTANCE.tr("gui.komaedit.notes.empty")
                : ClientWords.INSTANCE.tr("gui.komaedit.notes.hint");
        context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, height - 42, 0xB0B0B0);
    }

    @Override
    public void removed() {
        ClientNotes.onChange(null);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
