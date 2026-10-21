package com.komainu.moxia.command;

import com.komainu.moxia.preview.PreviewFlags;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de humo sin el juego: monta el árbol completo de {@code /komaedit} con {@link MoxiaCommands#register} en un
 * {@link CommandDispatcher} de Brigadier y comprueba que, tras fusionar ramas hechas en paralelo, ninguna orden se
 * registra dos veces, que los alias no se pisan y que la ayuda y la vista previa cubren lo que deben.
 */
class CommandTreeSmokeTest {
    private static final Set<String> FLAGS = Set.of(PreviewFlags.SHORT, PreviewFlags.LONG);
    private static final Pattern WORD = Pattern.compile("[a-z0-9_]+");

    private static RecordingDispatcher dispatcher;
    private static CommandNode<ServerCommandSource> komaedit;

    /**
     * Brigadier fusiona en silencio dos hijos con el mismo nombre: el segundo registro pisa la orden del primero y
     * se queda con sus permisos. Este nodo raíz apunta cada {@code addChild} que repite un nombre (salvo los de la
     * bandera {@code -v}, que reemplazan la orden a propósito).
     */
    private static final class RecordingNode extends LiteralCommandNode<ServerCommandSource> {
        private final List<String> duplicates = new ArrayList<>();

        private RecordingNode(LiteralCommandNode<ServerCommandSource> built) {
            super(built.getLiteral(), built.getCommand(), built.getRequirement(), built.getRedirect(),
                    built.getRedirectModifier(), built.isFork());
        }

        @Override
        public void addChild(CommandNode<ServerCommandSource> node) {
            if (getChild(node.getName()) != null && !fromPreviewFlags()) {
                duplicates.add(node.getName());
            }
            super.addChild(node);
        }

        private static boolean fromPreviewFlags() {
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(frames -> frames.anyMatch(frame -> frame.getDeclaringClass() == PreviewFlags.class));
        }
    }

    /** Da a las órdenes un nodo {@code komaedit} que apunta lo que se registra dos veces. */
    private static final class RecordingDispatcher extends CommandDispatcher<ServerCommandSource> {
        private final List<String> roots = new ArrayList<>();
        private RecordingNode komaedit;

        @Override
        public LiteralCommandNode<ServerCommandSource> register(LiteralArgumentBuilder<ServerCommandSource> command) {
            roots.add(command.getLiteral());
            if (komaedit == null && command.getLiteral().equals("komaedit")) {
                LiteralCommandNode<ServerCommandSource> built = command.build();
                komaedit = new RecordingNode(built);
                for (CommandNode<ServerCommandSource> child : built.getChildren()) {
                    komaedit.addChild(child);
                }
                getRoot().addChild(komaedit);
                return komaedit;
            }
            return super.register(command);
        }
    }

    @BeforeAll
    static void registerEverything() {
        dispatcher = new RecordingDispatcher();
        MoxiaCommands.register(dispatcher);
        komaedit = dispatcher.getRoot().getChild("komaedit");
        assertNotNull(komaedit);
    }

    /** Las órdenes de primer nivel, sin las banderas de vista previa. */
    private static List<String> commands() {
        List<String> names = new ArrayList<>();
        for (CommandNode<ServerCommandSource> child : komaedit.getChildren()) {
            if (child instanceof LiteralCommandNode<?> && !FLAGS.contains(child.getName())) {
                names.add(child.getName());
            }
        }
        return names;
    }

    @Test
    void noCommandIsRegisteredTwice() {
        assertSame(dispatcher.komaedit, komaedit, "el nodo raíz es el que apunta los registros");
        assertEquals(List.of(), dispatcher.komaedit.duplicates, "órdenes registradas dos veces bajo /komaedit");
        for (String alias : List.of("ke", "moxia", "mx")) {
            assertEquals(1, dispatcher.roots.stream().filter(alias::equals).count(), "/" + alias + " una sola vez");
        }
        assertTrue(commands().size() > 100, "el árbol está entero: " + commands().size() + " órdenes");
    }

    @Test
    void shortAliasesRedirectToTheWholeTree() {
        for (String alias : List.of("ke", "moxia", "mx")) {
            CommandNode<ServerCommandSource> node = dispatcher.getRoot().getChild(alias);
            assertNotNull(node, "/" + alias);
            assertSame(komaedit, node.getRedirect(), "/" + alias + " lleva a /komaedit");
        }
        assertSame(komaedit.getChild("path"), komaedit.getChild("ruta").getRedirect(), "ruta es path");
    }

    @Test
    void namesAreLowercaseWordsWithoutClashingWithTheFlags() {
        for (String name : commands()) {
            assertTrue(WORD.matcher(name).matches(), "nombre de orden raro: «" + name + "»");
        }
        walk(komaedit, "komaedit", (path, node) -> {
            if (node instanceof LiteralCommandNode<?> literal && FLAGS.contains(literal.getLiteral())) {
                assertTrue(node.getChildren().isEmpty(), path + ": la bandera -v no lleva nada detrás");
                assertNotNull(node.getCommand(), path + ": la bandera -v ejecuta la orden");
            }
        });
    }

    /** Cada orden de primer nivel sale en {@code /komaedit help}: como tema, alias de tema o entrada. */
    @Test
    void everyCommandHasAHelpTopic() {
        Set<String> missing = new TreeSet<>();
        Set<String> clashing = new TreeSet<>();
        for (String name : commands()) {
            if (name.equals("help") || name.equals("ayuda")) {
                continue;
            }
            List<String> documenting = HelpTopics.TOPICS.stream()
                    .filter(topic -> topic.entries().stream().anyMatch(entry -> mentions(entry.syntax(), name)))
                    .map(HelpTopics.Topic::id)
                    .toList();
            var byName = HelpTopics.find(name);
            if (byName.isEmpty() && documenting.isEmpty()) {
                missing.add(name);
            } else if (byName.isPresent() && !documenting.isEmpty() && !documenting.contains(byName.get().id())) {
                // «help <orden>» tiene que abrir el tema donde está esa orden.
                clashing.add(name + " → " + byName.get().id() + " " + documenting);
            }
        }
        assertEquals(Set.of(), missing, "órdenes sin tema en HelpTopics");
        assertEquals(Set.of(), clashing, "alias de ayuda que llevan a otro tema que el de la orden");
    }

    /** Y al revés: la ayuda solo enseña órdenes que existen, con sus subórdenes fijas. */
    @Test
    void helpOnlyShowsRegisteredCommands() {
        List<String> wrong = new ArrayList<>();
        HelpTopics.TOPICS.forEach(topic -> topic.entries().forEach(entry -> {
            String problem = resolve(entry.syntax());
            if (problem != null) {
                wrong.add(topic.id() + ": " + entry.syntax() + " → " + problem);
            }
        }));
        assertEquals(List.of(), wrong, "entradas de la ayuda que no llevan a ninguna orden");
    }

    /**
     * Las órdenes se ocultan del autocompletado a quien no puede usarlas (lo dice la documentación de permisos): cada
     * una pide su nodo con {@code requires}, no solo al ejecutarse. Quedan abiertas a quien tenga
     * {@code komaedit.use} las de ayuda, ajustes propios, vista previa y estado, que no tocan el mundo.
     */
    @Test
    void everyCommandIsHiddenFromWhoCannotUseIt() {
        Set<String> open = Set.of("help", "ayuda", "menu", "quick", "gui", "tutorial", "bienvenida", "shapes",
                "info", "wiki", "quiet", "silencio", "permisos", "permissions", "confirm", "confirmar", "cancel",
                "cancelar", "preview", "vistaprevia", "repeat", "repetir", "job", "project", "trozo", "part");
        var anyone = LiteralArgumentBuilder.<ServerCommandSource>literal("x").getRequirement();
        Set<String> unguarded = new TreeSet<>();
        for (CommandNode<ServerCommandSource> child : komaedit.getChildren()) {
            if (child instanceof LiteralCommandNode<?> && !FLAGS.contains(child.getName())
                    && !open.contains(child.getName()) && child.getRequirement() == anyone) {
                unguarded.add(child.getName());
            }
        }
        assertEquals(Set.of(), unguarded, "órdenes que salen en el autocompletado sin pedir su permiso");
    }

    /** Las órdenes de gestión no llevan la bandera {@code -v}: no colocan bloques o ya usan {@code -v}. */
    @Test
    void managementCommandsDoNotTakeThePreviewFlag() {
        for (String family : List.of("help", "preview", "cancel", "confirm", "symmetry", "simetria", "sym",
                "capability", "capacidad", "quiet", "silencio", "wiki", "info", "permissions", "color", "colour",
                "analyze", "analizar", "materiales", "biome", "bioma", "gradient", "palette", "noise", "sel", "job",
                "project", "reload")) {
            CommandNode<ServerCommandSource> node = komaedit.getChild(family);
            assertNotNull(node, family);
            walk(node, family, (path, child) -> assertFalse(FLAGS.contains(child.getName()),
                    path + " no debería llevar -v"));
        }
        for (String family : List.of("tree", "rock")) {
            for (String management : List.of("save", "list", "delete", "cancel")) {
                CommandNode<ServerCommandSource> node = komaedit.getChild(family).getChild(management);
                assertNotNull(node, family + " " + management);
                walk(node, family + " " + management, (path, child) -> assertFalse(FLAGS.contains(child.getName()),
                        path + " no debería llevar -v"));
            }
        }
        CommandNode<ServerCommandSource> fonts = komaedit.getChild("text").getChild("fonts");
        assertTrue(fonts.getChildren().isEmpty(), "text fonts no lleva -v");
    }

    @Test
    void editingCommandsTakeThePreviewFlag() {
        // Los de texto libre la recogen dentro del texto; los demás, como literal al final.
        for (String family : List.of("build", "tweak", "retocar")) {
            assertTrue(hasFlag(komaedit.getChild(family)), family + " admite -v");
        }
        for (String family : List.of("roof", "loft", "tree", "rock", "terrain", "text", "image", "shade", "snow",
                "gravity", "drain", "waterlog", "texture", "deform", "place", "noisegen", "pillar", "torii", "bend", "smoothblocks")) {
            assertTrue(greedy(komaedit.getChild(family)), family + " recoge -v en su texto");
        }
    }

    // ---------------------------------------------------------------- utilidades

    private interface Visitor {
        void visit(String path, CommandNode<ServerCommandSource> node);
    }

    private static void walk(CommandNode<ServerCommandSource> node, String path, Visitor visitor) {
        for (CommandNode<ServerCommandSource> child : node.getChildren()) {
            String childPath = path + " " + child.getUsageText();
            visitor.visit(childPath, child);
            if (child.getRedirect() == null) {
                walk(child, childPath, visitor);
            }
        }
    }

    private static boolean hasFlag(CommandNode<ServerCommandSource> node) {
        boolean[] found = {false};
        walk(node, node.getName(), (path, child) -> found[0] |= FLAGS.contains(child.getName()));
        return found[0];
    }

    private static boolean greedy(CommandNode<ServerCommandSource> node) {
        boolean[] found = {false};
        walk(node, node.getName(), (path, child) -> found[0] |= child instanceof ArgumentCommandNode<?, ?> argument
                && argument.getType() instanceof com.mojang.brigadier.arguments.StringArgumentType type
                && type.getType() == com.mojang.brigadier.arguments.StringArgumentType.StringType.GREEDY_PHRASE
                && child.getCommand() != null);
        return found[0];
    }

    private static boolean mentions(String syntax, String name) {
        String rest = syntax.substring("/komaedit ".length());
        String first = rest.split(" ", 2)[0];
        for (String alternative : first.split("\\|")) {
            if (alternative.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sigue la sintaxis de una entrada por el árbol mientras sean palabras fijas.
     *
     * @return {@code null} si lleva a una orden, o qué palabra no existe
     */
    private static String resolve(String syntax) {
        String[] words = syntax.substring("/komaedit ".length()).split(" ");
        List<CommandNode<ServerCommandSource>> current = List.of(komaedit);
        for (int index = 0; index < words.length; index++) {
            String word = words[index].toLowerCase(Locale.ROOT);
            String[] alternatives = word.split("\\|");
            boolean plain = true;
            for (String alternative : alternatives) {
                plain &= WORD.matcher(alternative).matches();
            }
            if (!plain || FLAGS.contains(word)) {
                return null;
            }
            List<CommandNode<ServerCommandSource>> next = new ArrayList<>();
            for (CommandNode<ServerCommandSource> node : current) {
                CommandNode<ServerCommandSource> target = node.getRedirect() != null ? node.getRedirect() : node;
                boolean takesArgument = target.getChildren().stream()
                        .anyMatch(child -> child instanceof ArgumentCommandNode<?, ?>);
                for (String alternative : alternatives) {
                    CommandNode<ServerCommandSource> child = target.getChild(alternative);
                    if (child instanceof LiteralCommandNode<?>) {
                        next.add(child);
                    } else if (!takesArgument) {
                        return "«" + alternative + "» no existe";
                    }
                }
                if (takesArgument && next.isEmpty()) {
                    // Una palabra que no es subcomando la recoge el argumento (un nombre, un bloque…).
                    return null;
                }
            }
            if (next.isEmpty()) {
                return index == 0 ? "«" + word + "» no es una orden" : null;
            }
            current = next;
        }
        return null;
    }
}
