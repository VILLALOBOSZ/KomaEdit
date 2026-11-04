package com.komainu.moxia.edit;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El centro de un portapapeles o de una selección ({@code place -p} con centro, {@code scatter -h Active(P:false)})
 * se calculaba con {@code divide(2)} de WorldEdit, que trunca hacia cero: con un lado par, el mismo portapapeles caía
 * un bloque más allá si se había copiado en coordenadas negativas.
 */
class BlockBoundsTest {
    @Test
    void theMiddleDoesNotDependOnWhereTheBoxIs() {
        for (int size = 1; size <= 6; size++) {
            BlockVector3 reference = BlockBounds.middle(BlockVector3.ZERO, BlockVector3.at(size - 1, size - 1,
                    size - 1));
            for (int shift = -40; shift <= 40; shift += 7) {
                BlockVector3 minimum = BlockVector3.at(shift, shift - 64, -shift);
                BlockVector3 maximum = minimum.add(size - 1, size - 1, size - 1);
                assertEquals(reference.add(minimum), BlockBounds.middle(minimum, maximum),
                        "lado " + size + " desde " + minimum);
            }
        }
        assertEquals(BlockVector3.at(-9, -64, 8), BlockBounds.middle(BlockVector3.at(-10, -64, 7),
                BlockVector3.at(-7, -63, 10)), "con lado par, el de abajo también en negativo");
    }

    @Test
    void noBoxMiddleTruncatesTowardsZero() throws IOException {
        Path root = Path.of("src/main/java/com/komainu/moxia");
        List<String> truncating = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains(".getMaximumPoint()).divide(2)")) {
                    truncating.add(root.relativize(file).toString().replace('\\', '/'));
                }
            }
        }
        assertEquals(List.of(), truncating, "usad BlockBounds.middle");
    }
}
