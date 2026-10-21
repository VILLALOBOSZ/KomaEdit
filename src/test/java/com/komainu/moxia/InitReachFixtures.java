package com.komainu.moxia;

import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.ExecutorService;

/**
 * Casos de mentira para comprobar que {@link InitReach} ve lo que debe; nunca se ejecutan. Si el analizador dejara de
 * verlos, las pruebas de arranque e hilos pasarían sin mirar nada.
 */
final class InitReachFixtures {
    private InitReachFixtures() {
    }

    /** Como la 2.0.0: una clase que se carga al iniciar y tiene una constante con {@code BlockTypes}. */
    static void onInitialize() {
        Lane.register();
        ServerLifecycleEvents.SERVER_STARTED.register(Recovery::open);
        // Lo que se manda al hilo del servidor corre en un tick, con WorldEdit ya listo.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(Later::read));
    }

    /** Un worker que lee el mundo, y otro que vuelve antes al hilo del servidor. */
    static void submit(ExecutorService executor, ServerWorld world, MinecraftServer server) {
        executor.execute(() -> world.getBlockState(BlockPos.ORIGIN));
        executor.execute(() -> server.execute(() -> world.getBlockState(BlockPos.ORIGIN)));
    }

    static final class Lane {
        private static final BaseBlock AIR = BlockTypes.AIR.getDefaultState().toBaseBlock();

        static void register() {
        }
    }

    static final class Recovery {
        static void open(MinecraftServer server) {
            BlockTypes.STONE.getDefaultState();
        }
    }

    static final class Later {
        static void read() {
            BlockTypes.DIRT.getDefaultState();
        }
    }
}
