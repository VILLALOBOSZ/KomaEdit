package com.komainu.moxia;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * En qué orden reciben el clic y las teclas las partes del cliente que se los quitan a Minecraft. Lee las fuentes del
 * cliente, como {@link DefaultKeysTest}: sin el juego no hay ratón ni teclado que pulsar.
 *
 * <p>Lo de un momento con piezas bajo el punto de mira (las flechas del fantasma, los displays y su gizmo, la imagen
 * de referencia que se edita) va antes que los modos que se quedan puestos (la regla, el pincel de la mano y las
 * capacidades), como ya pasaba con el clic derecho. Antes, la regla iba en una fase anterior a todo y el pincel y las
 * capacidades se apuntaban antes que el fantasma: con la regla o una capacidad puesta no se agarraban las flechas del
 * gizmo, y con una capacidad que rompe, un clic en un display rompía el bloque de detrás.</p>
 */
class ClientInputOrderTest {
    private static final Path CLIENT = Path.of("src", "client", "java", "com", "komainu", "moxia", "client");

    @Test
    void theLeftClickGoesFirstToWhatIsUnderTheCrosshairThenToTheModesThatStay() {
        List<String> registered = new ArrayList<>();
        for (Path file : clientSources()) {
            if (read(file).contains("ClientPreAttackCallback.EVENT.register")) {
                registered.add(file.getFileName().toString());
            }
        }
        assertEquals(List.of("LeftClick.java"), registered,
                "un solo sitio reparte el clic izquierdo, en un orden que se lee de arriba abajo");
        String source = read(CLIENT.resolve("LeftClick.java"));
        assertInOrder(source, "GhostClient.onAttack(", "DisplayEditorClient.onAttack(", "ReferenceClient.onAttack(",
                "RulerClient.onAttack(", "BrushToolControls.onAttack(", "CapabilityControls.onAttack(",
                "DisplayEditorClient.onAttackEmpty(");
    }

    /**
     * La regla leía Ctrl+C y Supr mirando el teclado en cada tick, sin quitárselas a nadie: con la regla puesta, Ctrl+C
     * con un display elegido copiaba el display y además pisaba el portapapeles con las medidas, y Supr con un fantasma
     * lo quitaba y además borraba los puntos de la regla. Ahora van por {@code KeyboardMixin}, detrás del fantasma, los
     * displays y las imágenes de referencia: la tecla es de quien la toma primero.
     */
    @Test
    void theRulerKeysAreOnlyItsOwnWhenNobodyBeforeTookThem() {
        String ruler = read(CLIENT.resolve("utility").resolve("RulerClient.java"));
        assertTrue(!ruler.contains("GLFW.GLFW_KEY_C)") && !ruler.contains("isKeyPressed(window, GLFW.GLFW_KEY_DELETE"),
                "la regla no mira el teclado por su cuenta");
        String mixin = read(CLIENT.resolve("mixin").resolve("KeyboardMixin.java"));
        assertInOrder(mixin, "DisplayEditorClient.onKey(", "GhostClient.onKey(", "ReferenceClient.onKey(",
                "RulerClient.onKey(");
    }

    /**
     * Dentro del Studio el jugador se mueve con las teclas de Controles, no con W, A, S, D, espacio, Mayús y Ctrl fijas:
     * quien las cambió (ESDF, flechas…) no podía moverse con el Studio abierto.
     */
    @Test
    void insideTheStudioThePlayerMovesWithTheKeysOfControls() {
        String studio = read(CLIENT.resolve("gui").resolve("ProceduralStudioScreen.java"));
        Matcher fixed = Pattern.compile("setPressed\\([^;]*GLFW\\.GLFW_KEY_[A-Z_]+").matcher(studio);
        List<String> found = new ArrayList<>();
        while (fixed.find()) {
            found.add(fixed.group());
        }
        assertEquals(List.of(), found);
        for (String key : List.of("forwardKey", "backKey", "leftKey", "rightKey", "jumpKey", "sneakKey", "sprintKey")) {
            assertTrue(studio.contains("pressed(window, client.options." + key + ")"), key);
        }
    }

    private static void assertInOrder(String source, String... calls) {
        int last = -1;
        for (String call : calls) {
            int at = source.indexOf(call, last + 1);
            assertTrue(at > last, call + " va detrás de lo anterior: " + String.join(" → ", calls));
            last = at;
        }
    }

    private static List<Path> clientSources() {
        try (Stream<Path> files = Files.walk(CLIENT)) {
            return files.filter(file -> file.toString().endsWith(".java")).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
