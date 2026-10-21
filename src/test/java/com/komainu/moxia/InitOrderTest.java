package com.komainu.moxia;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que corre al arrancar, antes de que exista lo que necesita.
 *
 * <ul>
 *     <li><b>WorldEdit.</b> Fabric inicia los mods por orden alfabético de id: {@code komaedit} va antes que
 *     {@code worldedit}, y sus eventos también. WorldEdit no se da por iniciado hasta que registra sus órdenes y no
 *     llena sus registros hasta su propio {@code SERVER_STARTED}, que corre después del de KomaEdit. Tocar
 *     {@code BlockTypes} (o cualquier registro de WorldEdit) en {@code onInitialize}, al registrar las órdenes o en
 *     {@code SERVER_STARTING}/{@code SERVER_STARTED} tumba el arranque con «WorldEdit is not initialized yet» (pasó en la
 *     2.0.0 con una constante de {@code RebuildService}) o, peor, deja todas las constantes de {@code BlockTypes} a
 *     {@code null} para el resto del proceso.</li>
 *     <li><b>El cliente.</b> Fabric inicia los mods del cliente dentro del constructor de {@code MinecraftClient}: aún
 *     no hay opciones, ventana, texturas, fuente ni idiomas ({@code I18n} devuelve la clave).</li>
 * </ul>
 *
 * <p>Las pruebas no arrancan WorldEdit, así que esto lo vigila siguiendo el bytecode ({@link InitReach}).</p>
 */
class InitOrderTest {
    static final InitReach.MethodId SERVER_INIT = new InitReach.MethodId("com/komainu/moxia/KomaEditMod",
            "onInitialize", "()V");
    static final InitReach.MethodId CLIENT_INIT = new InitReach.MethodId("com/komainu/moxia/client/KomaEditClient",
            "onInitializeClient", "()V");
    private static final InitReach.MethodId FIXTURE = new InitReach.MethodId("com/komainu/moxia/InitReachFixtures",
            "onInitialize", "()V");
    private static final Set<String> WORLDEDIT_REGISTRIES = Set.of(
            "com/sk89q/worldedit/world/block/BlockTypes", "com/sk89q/worldedit/world/block/BlockCategories",
            "com/sk89q/worldedit/world/item/ItemTypes", "com/sk89q/worldedit/world/item/ItemCategories",
            "com/sk89q/worldedit/world/biome/BiomeTypes", "com/sk89q/worldedit/world/biome/BiomeCategories",
            "com/sk89q/worldedit/world/entity/EntityTypes", "com/sk89q/worldedit/world/fluid/FluidTypes",
            "com/sk89q/worldedit/world/fluid/FluidCategories", "com/sk89q/worldedit/world/gamemode/GameModes",
            "com/sk89q/worldedit/world/weather/WeatherTypes");
    private static final List<String> WORLDEDIT_ADAPTED = List.of("com/sk89q/worldedit/world/block/",
            "com/sk89q/worldedit/world/item/", "com/sk89q/worldedit/world/biome/", "com/sk89q/worldedit/world/entity/",
            "com/sk89q/worldedit/world/fluid/");

    /** Lo que necesita los registros de WorldEdit llenos. */
    static final InitReach.Sink WORLDEDIT = (opcode, owner, name, descriptor) -> {
        if (WORLDEDIT_REGISTRIES.contains(owner)) {
            return simple(owner) + "." + name;
        }
        if (owner.startsWith("com/sk89q/worldedit/") && name.equals("REGISTRY")) {
            return simple(owner) + ".REGISTRY";
        }
        Type returned = descriptor.startsWith("(") ? Type.getReturnType(descriptor) : null;
        if (owner.equals("com/sk89q/worldedit/fabric/FabricAdapter") && name.equals("adapt")
                && returned != null && returned.getSort() == Type.OBJECT
                && WORLDEDIT_ADAPTED.stream().anyMatch(returned.getInternalName()::startsWith)) {
            return "FabricAdapter.adapt → " + simple(returned.getInternalName());
        }
        if (owner.startsWith("com/sk89q/worldedit/") && name.equals("parseFromInput")) {
            return simple(owner) + ".parseFromInput";
        }
        if (owner.equals("com/sk89q/worldedit/extension/platform/PlatformManager") && name.equals("queryCapability")) {
            return "PlatformManager.queryCapability";
        }
        return null;
    };

    static InitReach reach() {
        return new InitReach(List.of(Path.of(System.getProperty("komaedit.mainClasses", "build/classes/java/main")),
                Path.of(System.getProperty("komaedit.clientClasses", "build/classes/java/client"))));
    }

    /**
     * Lo que el cliente aún no tiene al iniciar los mods: Fabric los inicia dentro del constructor de
     * {@code MinecraftClient}, antes de las opciones, la ventana, las texturas, la fuente y los idiomas.
     */
    static final InitReach.Sink CLIENT_NOT_READY = (opcode, owner, name, descriptor) -> {
        if (owner.equals("net/minecraft/client/MinecraftClient") && opcode != Opcodes.INVOKESTATIC
                && !name.equals("runDirectory")) {
            return "MinecraftClient." + name;
        }
        if (owner.equals("net/minecraft/client/resource/language/I18n") || owner.equals("net/minecraft/util/Language")
                || owner.startsWith("com/mojang/blaze3d/") || owner.startsWith("net/minecraft/client/gl/")
                || owner.equals("net/minecraft/client/texture/NativeImageBackedTexture")
                || owner.equals("net/minecraft/client/texture/TextureManager")) {
            return simple(owner) + "." + name;
        }
        return WORLDEDIT.check(opcode, owner, name, descriptor);
    };

    @Test
    void nothingLoadedAtInitTouchesWorldEditRegistries() {
        InitReach reach = reach();
        InitReach.Phase init = reach.follow(List.of(SERVER_INIT), null);
        assertEquals(List.of(), init.findings(WORLDEDIT).stream().map(Object::toString).toList(),
                "registros de WorldEdit en lo que corre en onInitialize: hazlo perezoso o llévalo a cuando se use");
    }

    @Test
    void theClientInitUsesNothingTheClientHasNotBuiltYet() {
        InitReach.Phase init = reach().follow(List.of(CLIENT_INIT), null);
        // Si dejara de seguir el cliente, la prueba pasaría sin mirar nada.
        assertTrue(init.methods().size() > 50, "solo " + init.methods().size() + " métodos desde onInitializeClient");
        assertEquals(List.of(), init.findings(CLIENT_NOT_READY).stream().map(Object::toString).toList(),
                "en onInitializeClient aún no hay ventana, opciones, texturas, fuente ni idiomas (ni WorldEdit):"
                        + " llévalo a cuando se use o a ClientLifecycleEvents.CLIENT_STARTED");
    }

    @Test
    void theAnalyzerSeesTheCrashOf200AndTheOneAtServerStart() {
        InitReach reach = reach();
        InitReach.Phase init = reach.follow(List.of(FIXTURE), null);
        assertEquals(List.of("BlockTypes.AIR en InitReachFixtures$Lane.<clinit>"),
                init.findings(WORLDEDIT).stream().map(finding -> finding.what() + " en " + finding.where()).toList());
        InitReach.Phase early = reach.follow(init.later(), init);
        assertEquals(List.of("BlockTypes.STONE en InitReachFixtures$Recovery.open"),
                early.findings(WORLDEDIT).stream().map(finding -> finding.what() + " en " + finding.where()).toList(),
                "lo que se manda con server.execute corre en un tick, con WorldEdit listo: no cuenta");
    }

    @Test
    void nothingBeforeWorldEditIsReadyTouchesItsRegistries() {
        InitReach reach = reach();
        InitReach.Phase init = reach.follow(List.of(SERVER_INIT), null);
        InitReach.Phase early = reach.follow(init.later(), init);
        assertEquals(List.of(), early.findings(WORLDEDIT).stream().map(Object::toString).toList(),
                "registros de WorldEdit al registrar las órdenes o en SERVER_STARTING/SERVER_STARTED de KomaEdit, que"
                        + " corren antes de que WorldEdit los llene: espera al primer tick o a PlatformReadyEvent");
    }

    private static String simple(String internal) {
        return internal.substring(internal.lastIndexOf('/') + 1);
    }
}
