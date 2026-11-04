package com.komainu.moxia.permission;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las órdenes cuelgan de {@code /komaedit}, que pide acceso a KomaEdit ({@code komaedit.use} o un paquete). Lo que
 * llega por paquete del cliente tiene que pedirlo también: si un administrador le quita {@code komaedit.use} a alguien
 * que aún hereda {@code komaedit.build} de un grupo, las órdenes desaparecen, y antes un cliente modificado seguía
 * usando capacidades, displays, el lazo, las vistas, las barras o importando paquetes.
 */
class PacketAccessTest {
    private static final Path MAIN = Path.of("src", "main", "java", "com", "komainu", "moxia");
    /** Archivos que registran un receptor y dónde se mira el acceso, si no es en el mismo. */
    private static final Map<String, String> CHECKED_IN = Map.of(
            "display/DisplayNetwork.java", "display/DisplayService.java",
            "gizmo/GhostNetwork.java", "gizmo/GhostService.java",
            "selection/SelectionNetwork.java", "selection/SelectionToolService.java",
            "KomaEditMod.java", "editor/EditorActionService.java");
    /** Sin acceso propio, con el porqué. */
    private static final Map<String, String> EXEMPT = Map.of(
            "studio/net/ButtonCommandService.java", "ejecuta órdenes, y el árbol de /komaedit pide el acceso",
            "studio/net/StudioToolsNetwork.java", "solo contesta qué permisos tiene y guarda el bloque pulsado");

    @Test
    void everyPacketFromTheClientAsksForKomaEditAccess() throws IOException {
        List<String> missing = new ArrayList<>();
        int receivers = 0;
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (!source.contains("ServerPlayNetworking.registerGlobalReceiver(")) {
                    continue;
                }
                receivers++;
                String name = MAIN.relativize(file).toString().replace('\\', '/');
                if (EXEMPT.containsKey(name)) {
                    continue;
                }
                String checked = Files.readString(MAIN.resolve(CHECKED_IN.getOrDefault(name, name)));
                if (!checked.contains("KomaPermissions.canAccess(")) {
                    missing.add(name);
                }
            }
        }
        assertTrue(receivers >= 15, receivers + " archivos con receptores: ¿se movieron las fuentes?");
        assertEquals(List.of(), missing, "receptores que no piden acceso a KomaEdit");
        String recipes = Files.readString(MAIN.resolve("recipe/RecipeActionService.java"));
        assertTrue(recipes.contains("KomaPermissions.canAccess("), "las recetas, que también llegan por KomaEditMod");
    }
}
