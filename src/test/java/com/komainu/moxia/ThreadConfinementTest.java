package com.komainu.moxia;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La regla central de {@code docs/ARCHITECTURE.md}: ninguna lectura ni escritura de mundo en un worker. Se sigue el
 * bytecode ({@link InitReach#submissions}) desde todo lo que se manda a otro hilo (los ejecutores, {@code Thread},
 * {@code CompletableFuture.*Async} y {@code AsyncEngine.submit}, también lo que entrega: corre en el worker) y no debe
 * llegar al mundo, a un jugador, al servidor ni a una sesión de WorldEdit; para volver al hilo del servidor está
 * {@code server.execute}, y para el del cliente, {@code client.execute}. En el cliente, lo mismo con su mundo y con
 * OpenGL.
 */
class ThreadConfinementTest {
    private static final Set<String> WORLDS = Set.of("net/minecraft/world/World", "net/minecraft/server/world/ServerWorld",
            "net/minecraft/world/WorldAccess", "net/minecraft/world/WorldView", "net/minecraft/world/BlockView",
            "net/minecraft/world/ServerWorldAccess", "net/minecraft/world/chunk/WorldChunk",
            "net/minecraft/world/chunk/Chunk", "net/minecraft/server/world/ServerChunkManager",
            "net/minecraft/client/world/ClientWorld");
    /** Lo de un mundo que no cambia: su nombre y sus alturas. */
    private static final Set<String> FIXED_WORLD = Set.of("getRegistryKey()Lnet/minecraft/registry/RegistryKey;",
            "isClient()Z", "getBottomY()I", "getTopY()I", "getHeight()I");
    /** Lo del servidor que vale desde otro hilo: mandar algo a su hilo y saber dónde guarda. */
    private static final Set<String> SERVER_SAFE = Set.of("execute", "send", "submit", "isOnThread", "getSavePath");
    private static final Set<String> CLIENT_SAFE = Set.of("execute", "send", "submit", "isOnThread", "getInstance",
            "getBlockColors", "getBlockRenderManager", "getBakedModelManager");
    private static final Set<String> WORLDEDIT = Set.of("com/sk89q/worldedit/EditSession",
            "com/sk89q/worldedit/LocalSession", "com/sk89q/worldedit/fabric/FabricWorld",
            "com/sk89q/worldedit/fabric/FabricPlayer", "com/sk89q/worldedit/world/World");

    /** Lo que solo se toca desde el hilo del servidor (o del cliente). */
    static final InitReach.Sink OWN_THREAD = (opcode, owner, name, descriptor) -> {
        String simple = owner.substring(owner.lastIndexOf('/') + 1) + "." + name;
        if (WORLDS.contains(owner) && !FIXED_WORLD.contains(name + descriptor)) {
            return simple;
        }
        if ((owner.startsWith("net/minecraft/entity/") || owner.equals("net/minecraft/server/network/ServerPlayerEntity"))
                && !Set.of("getUuid", "getUuidAsString", "getName", "getGameProfile").contains(name)) {
            return simple;
        }
        if (owner.equals("net/minecraft/server/MinecraftServer") && !SERVER_SAFE.contains(name)
                || owner.equals("net/minecraft/server/PlayerManager")) {
            return simple;
        }
        if (WORLDEDIT.contains(owner) || owner.startsWith("com/sk89q/worldedit/session/")) {
            return simple;
        }
        if (owner.startsWith("com/sk89q/worldedit/") && name.equals("parseFromInput")) {
            return simple;
        }
        if (owner.equals("net/minecraft/client/MinecraftClient") && !CLIENT_SAFE.contains(name)) {
            return simple;
        }
        if (owner.startsWith("com/mojang/blaze3d/") || owner.startsWith("net/minecraft/client/gl/")
                || owner.equals("net/minecraft/client/texture/NativeImageBackedTexture")
                || owner.equals("net/minecraft/client/texture/TextureManager")) {
            return simple;
        }
        return null;
    };

    @Test
    void nothingSentToAnotherThreadTouchesTheWorld() {
        InitReach reach = InitOrderTest.reach();
        Map<InitReach.MethodId, InitReach.MethodId> submitted = reach.submissions();
        // Si dejara de encontrarlas, la prueba pasaría sin mirar nada.
        assertTrue(submitted.size() > 40, "solo " + submitted.size() + " cosas mandadas a otros hilos");
        assertTrue(submitted.keySet().stream().anyMatch(entry -> entry.owner().endsWith("/RegionOperationService")),
                "no ve lo que RegionOperationService manda a AsyncEngine");
        InitReach.Phase threads = reach.threads(submitted);
        assertEquals(List.of(), threads.findings(OWN_THREAD).stream().map(Object::toString).toList(),
                "esto corre en otro hilo: lee lo que necesites antes (WorldSnapshot, RegionSnapshot) y vuelve con"
                        + " server.execute para tocar el mundo");
    }

    @Test
    void theAnalyzerSeesAWorkerReadingTheWorld() {
        InitReach reach = InitOrderTest.reach();
        InitReach.Phase threads = reach.threads(reach.submissions(List.of("com/komainu/moxia/InitReachFixtures")));
        List<InitReach.Finding> findings = threads.findings(OWN_THREAD);
        assertEquals(List.of("ServerWorld.getBlockState"), findings.stream().map(InitReach.Finding::what).toList(),
                "el que vuelve con server.execute no cuenta");
        assertTrue(findings.getFirst().path().contains("InitReachFixtures.submit"), findings.toString());
    }
}
