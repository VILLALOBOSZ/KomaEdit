package com.komainu.moxia.worldedit;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code //copy}, {@code //rotate 90} y {@code /komaedit place Clipboard} colocaban el portapapeles sin girar: las
 * herramientas leían el portapapeles crudo de la sesión y se saltaban el giro pendiente, que sí aplican
 * {@code paste}, escalar, los planos y la reconstrucción.
 */
class PendingClipboardTest {
    /** Lugares que pueden leer el portapapeles sin el giro pendiente, con el porqué. */
    private static final Map<String, String> RAW_ALLOWED = Map.of(
            "worldedit/ez/EzMaskParser.java", "#clipblocks solo mira qué bloques contiene el portapapeles");

    @Test
    void appliesThePendingRotateAndFlip() {
        List<Transform> applied = new ArrayList<>();
        Clipboard turned = fake(applied, null);
        Clipboard clipboard = fake(applied, turned);
        ClipboardHolder holder = new ClipboardHolder(clipboard);
        assertSame(clipboard, PendingClipboard.of(holder), "sin giro pendiente, el mismo portapapeles");
        assertTrue(applied.isEmpty());
        AffineTransform rotate = new AffineTransform().rotateY(90);
        holder.setTransform(rotate);
        assertSame(turned, PendingClipboard.of(holder), "con //rotate, el portapapeles girado");
        assertEquals(List.of(rotate), applied);
        holder.setTransform(new AffineTransform().scale(-1, 1, 1));
        assertSame(turned, PendingClipboard.of(holder), "con //flip, el portapapeles volteado");
    }

    /** Nadie lee {@code session.getClipboard().getClipboard()} a pelo: todo pasa por {@link PendingClipboard}. */
    @Test
    void toolsReadTheClipboardWithItsPendingTransform() throws IOException {
        Path root = Path.of("src/main/java/com/komainu/moxia");
        List<String> raw = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                String source = Files.readString(file);
                if (source.contains("getClipboard().getClipboard()") && !RAW_ALLOWED.containsKey(relative)) {
                    raw.add(relative);
                }
            }
        }
        assertEquals(List.of(), raw, "leen el portapapeles sin su //rotate y //flip: usad WorldEditService.clipboard");
        assertTrue(Files.readString(root.resolve("worldedit/WorldEditService.java"))
                .contains("PendingClipboard.of(context(player).session().getClipboard())"));
    }

    private static Clipboard fake(List<Transform> applied, Clipboard result) {
        return (Clipboard) Proxy.newProxyInstance(Clipboard.class.getClassLoader(), new Class<?>[]{Clipboard.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "transform" -> {
                            applied.add((Transform) arguments[0]);
                            return result;
                        }
                        case "hashCode" -> {
                            return System.identityHashCode(proxy);
                        }
                        case "equals" -> {
                            return proxy == arguments[0];
                        }
                        case "toString" -> {
                            return "portapapeles de prueba";
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
