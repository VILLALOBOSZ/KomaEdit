package com.komainu.moxia.worldedit;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /**
     * Girar copia el portapapeles entero en el hilo del servidor, y las herramientas miraban el tamaño después: con un
     * {@code //copy} de millones de bloques y un {@code //rotate}, cada clic del pincel de colocación (o el kitbash, de
     * 50 000 como mucho) copiaba los millones antes de decir que no cabían. El tamaño se mira antes, con la caja ya
     * girada y sin girar nada.
     */
    @Test
    void theSizeIsCheckedBeforeRotatingAnything() {
        List<Transform> applied = new ArrayList<>();
        Clipboard clipboard = boxed(applied, BlockVector3.at(0, 0, 0), BlockVector3.at(9, 1, 3));
        ClipboardHolder holder = new ClipboardHolder(clipboard);
        holder.setTransform(new AffineTransform().rotateY(90));
        long[] seen = {-1};
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> PendingClipboard.of(holder, volume -> {
                    seen[0] = volume;
                    throw new IllegalArgumentException("demasiado grande");
                }));
        assertEquals("demasiado grande", refused.getMessage());
        assertTrue(applied.isEmpty(), "se giró (y copió) antes de mirar el tamaño");
        assertEquals(80, seen[0], "la caja girada, del mismo volumen");
        Region turned = PendingClipboard.region(holder);
        assertEquals(BlockVector3.at(4, 2, 10), turned.getMaximumPoint().subtract(turned.getMinimumPoint())
                .add(1, 1, 1), "girar 90° cambia el largo por el ancho");
        holder.setTransform(new AffineTransform().rotateY(45));
        assertTrue(PendingClipboard.region(holder).getVolume() > 80, "girada 45° ocupa más caja");
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

    private static Clipboard boxed(List<Transform> applied, BlockVector3 minimum, BlockVector3 maximum) {
        CuboidRegion region = new CuboidRegion(minimum, maximum);
        return (Clipboard) Proxy.newProxyInstance(Clipboard.class.getClassLoader(), new Class<?>[]{Clipboard.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getRegion" -> region;
                    case "getOrigin" -> minimum;
                    case "transform" -> {
                        applied.add((Transform) arguments[0]);
                        yield proxy;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "portapapeles de prueba";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
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
