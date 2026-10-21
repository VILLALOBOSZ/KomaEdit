package com.komainu.moxia.display;

import com.komainu.moxia.KomaEditMod;
import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.change.Change;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Una edición de displays dentro del historial de WorldEdit: {@code //undo} y {@code //redo} la deshacen y la rehacen
 * en su sitio, junto con los bloques. WorldEdit no guarda entidades que no haya tocado él, así que el historial es el
 * nuestro ({@link DisplayHistory}) envuelto en un {@link Change}. Nunca lanza: si algo falla lo dice en el registro y
 * sigue con el resto del //undo.
 */
public final class DisplayChange implements Change {
    private final MinecraftServer server;
    private final RegistryKey<World> world;
    private final DisplayHistory<NbtCompound> history;
    private final UUID player;

    public DisplayChange(MinecraftServer server, RegistryKey<World> world, DisplayHistory<NbtCompound> history,
                         UUID player) {
        this.server = server;
        this.world = world;
        this.history = history;
        this.player = player;
    }

    public DisplayHistory<NbtCompound> history() {
        return history;
    }

    @Override
    public void undo(UndoContext context) {
        run(true);
    }

    @Override
    public void redo(UndoContext context) {
        run(false);
    }

    private void run(boolean undo) {
        if (server.isOnThread()) {
            apply(undo);
        } else {
            server.execute(() -> apply(undo));
        }
    }

    private void apply(boolean undo) {
        try {
            DisplayService.replay(server, world, history, player, undo);
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.warn("KomaEdit could not {} a display edit", undo ? "undo" : "redo", exception);
        }
    }
}
