package com.komainu.moxia.client.utility;

import com.komainu.moxia.client.world.SketchRenderer;
import com.komainu.moxia.feedback.WorldSketch;
import com.komainu.moxia.notes.NotePayloads;
import com.komainu.moxia.notes.NoteSketch;
import com.komainu.moxia.notes.WorldNote;
import com.komainu.moxia.shape.Vec3;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Las notas en el cliente: las de su dimensión que el servidor le deja ver, dibujadas con {@link NoteSketch} hasta su
 * distancia. Se pueden ocultar ({@code /kegui notas ocultar}) y enseñar también las resueltas.
 */
public final class ClientNotes {
    private static final Map<String, WorldNote> NOTES = new LinkedHashMap<>();
    private static String dimension = "";
    private static boolean canWrite;
    private static boolean canShare;
    private static boolean seesTeam;
    private static boolean manages;
    private static boolean hidden;
    private static boolean showResolved;
    private static boolean known;
    private static KeyBinding listKey;
    private static WorldSketch sketch = WorldSketch.EMPTY;
    private static boolean broken;
    private static Runnable onChange = () -> {
    };

    private ClientNotes() {
    }

    public static void register() {
        listKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.komaedit.notes", InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(), "category.komaedit"));
        ClientPlayNetworking.registerGlobalReceiver(NotePayloads.NoteSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(NotePayloads.NoteRemovePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    NOTES.remove(payload.id());
                    onChange.run();
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            NOTES.clear();
            known = false;
            sketch = WorldSketch.EMPTY;
        }));
        ClientTickEvents.END_CLIENT_TICK.register(ClientNotes::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(ClientNotes::renderWorld);
        WorldRenderEvents.LAST.register(ClientNotes::renderGhost);
    }

    private static void receive(NotePayloads.NoteSyncPayload payload) {
        if (payload.reset()) {
            NOTES.clear();
            dimension = payload.dimension();
        }
        if (payload.dimension().equals(dimension)) {
            payload.notes().forEach(note -> NOTES.put(note.id(), note));
        }
        canWrite = payload.canWrite();
        canShare = payload.canShare();
        seesTeam = payload.seesTeam();
        manages = payload.manages();
        known = true;
        onChange.run();
    }

    private static void tick(MinecraftClient client) {
        while (listKey.wasPressed()) {
            if (client.currentScreen == null && client.player != null) {
                client.setScreen(new NotesScreen(null));
            }
        }
        if (hidden || client.player == null || NOTES.isEmpty()) {
            sketch = WorldSketch.EMPTY;
            return;
        }
        var position = client.gameRenderer.getCamera().getPos();
        Vec3 camera = new Vec3(position.x, position.y, position.z);
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        WorldSketch.Builder builder = WorldSketch.builder();
        for (WorldNote note : NOTES.values()) {
            builder.add(NoteSketch.of(note, camera, zone, now, showResolved, ClientWords.INSTANCE));
        }
        sketch = builder.build();
    }

    private static void renderWorld(WorldRenderContext context) {
        if (!broken && !hidden) {
            broken = !SketchRenderer.safely(() -> SketchRenderer.drawWorld(context, sketch), "notes");
        }
    }

    private static void renderGhost(WorldRenderContext context) {
        if (!broken && !hidden) {
            broken = !SketchRenderer.safely(() -> SketchRenderer.drawGhost(context, sketch), "notes");
        }
    }

    static void onChange(Runnable listener) {
        onChange = listener == null ? () -> {
        } : listener;
    }

    public static List<WorldNote> notes() {
        return new ArrayList<>(NOTES.values());
    }

    /** Si el servidor ya dijo algo (tiene KomaEdit y mandó las notas). */
    public static boolean known() {
        return known;
    }

    public static boolean canWrite() {
        return canWrite;
    }

    public static boolean canShare() {
        return canShare;
    }

    public static boolean seesTeam() {
        return seesTeam;
    }

    public static boolean manages() {
        return manages;
    }

    public static boolean hidden() {
        return hidden;
    }

    public static void hidden(boolean value) {
        hidden = value;
    }

    public static boolean showResolved() {
        return showResolved;
    }

    public static void showResolved(boolean value) {
        showResolved = value;
    }
}
