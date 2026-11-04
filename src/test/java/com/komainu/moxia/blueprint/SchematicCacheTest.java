package com.komainu.moxia.blueprint;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Cada clic de un pincel de colocación con {@code Schematic(N:…)} leía y descomprimía del disco cada archivo que casaba
 * (hasta 32), en el hilo del servidor. Se leen una vez por archivo y versión, con la caché de los planos.
 */
class SchematicCacheTest {
    private static Clipboard clipboard() {
        CuboidRegion region = new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(3, 3, 3));
        return (Clipboard) Proxy.newProxyInstance(Clipboard.class.getClassLoader(), new Class<?>[]{Clipboard.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getRegion" -> region;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void aSchematicIsReadOncePerVersionOfTheFile(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("roble.schem");
        Files.write(file, new byte[]{1, 2, 3});
        AtomicInteger reads = new AtomicInteger();
        BlueprintService.SchematicReader reader = () -> {
            reads.incrementAndGet();
            return clipboard();
        };
        Clipboard first = BlueprintService.schematic(file, reader);
        for (int click = 0; click < 20; click++) {
            assertSame(first, BlueprintService.schematic(file, reader));
        }
        assertEquals(1, reads.get(), "se leía del disco en cada clic");
        Files.write(file, new byte[]{1, 2, 3, 4});
        BlueprintService.schematic(file, reader);
        assertEquals(2, reads.get(), "si el archivo cambia, se lee otra vez");
    }
}
