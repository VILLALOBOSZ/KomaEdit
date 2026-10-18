package com.komainu.moxia.preview;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewFlagsTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** Fuente de prueba: solo sabe qué jugador ejecuta. */
    private record Source(UUID player) {
    }

    /** Lo que vio cada orden al ejecutarse. */
    private record Seen(String command, String value, EditRequest request) {
    }

    private static LiteralArgumentBuilder<Source> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<Source, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private final List<Seen> seen = new ArrayList<>();
    private CommandDispatcher<Source> dispatcher;

    @BeforeEach
    void setUp() {
        EditRequests.clearAll();
        dispatcher = new CommandDispatcher<>();
        LiteralCommandNode<Source> root = dispatcher.register(literal("komaedit")
                .then(literal("build")
                        .then(argument("shape", StringArgumentType.word())
                                .executes(context -> record("build", StringArgumentType.getString(context, "shape")))
                                .then(argument("scale", IntegerArgumentType.integer(-64, 64))
                                        .executes(context -> record("build", StringArgumentType.getString(context,
                                                "shape") + " " + IntegerArgumentType.getInteger(context, "scale"))))))
                .then(literal("texture")
                        .executes(context -> record("texture", ""))
                        .then(argument("text", StringArgumentType.greedyString())
                                .executes(context -> record("texture", StringArgumentType.getString(context,
                                        "text")))))
                .then(literal("deform")
                        .then(argument("mode", StringArgumentType.word())
                                .then(argument("arguments", StringArgumentType.greedyString())
                                        .executes(context -> record("deform", StringArgumentType.getString(context,
                                                "mode") + " " + StringArgumentType.getString(context,
                                                "arguments"))))))
                .then(literal("help").executes(context -> record("help", ""))));
        dispatcher.register(literal("ke").redirect(root));
        int wrapped = PreviewFlags.install(dispatcher, root, new PreviewFlags.Hooks<>() {
            @Override
            public UUID player(Source source) {
                return source.player();
            }

            @Override
            public boolean eligible(List<String> path) {
                return !path.getFirst().equals("help");
            }

            @Override
            public boolean shortFlagTaken(List<String> path) {
                return path.size() > 1 && path.get(0).equals("deform") && path.get(1).equals("noise");
            }
        });
        assertEquals(5, wrapped, "build (2 nodos), texture (2) y deform; help se queda fuera");
    }

    @AfterEach
    void tearDown() {
        EditRequests.clearAll();
    }

    private int record(String command, String value) {
        seen.add(new Seen(command, value, EditRequests.current()));
        return 1;
    }

    private Seen run(String input) throws CommandSyntaxException {
        seen.clear();
        dispatcher.execute(input, new Source(PLAYER));
        assertEquals(1, seen.size(), "la orden se ejecuta una sola vez: " + input);
        return seen.getFirst();
    }

    @Test
    void shortAndLongFlagsAfterFixedArgumentsAskForPreview() throws CommandSyntaxException {
        Seen plain = run("komaedit build sphere 8");
        assertEquals("sphere 8", plain.value());
        assertEquals(EditRequest.Kind.DEFAULT, plain.request().kind(), "una orden normal lleva su petición");
        assertEquals("komaedit build sphere 8", plain.request().command());

        Seen flagged = run("komaedit build sphere 8 -v");
        assertEquals("sphere 8", flagged.value());
        assertTrue(flagged.request().preview());
        assertEquals("komaedit build sphere 8", flagged.request().command(), "se recalcula sin la bandera");
        assertEquals("build", flagged.request().label());

        assertTrue(run("komaedit build cube --preview").request().preview());
        assertNull(EditRequests.current(), "la petición solo vive mientras corre la orden");
    }

    @Test
    void flagInsideFreeTextIsStrippedAnywhere() throws CommandSyntaxException {
        Seen middle = run("komaedit texture rockify -v stone,andesite 3");
        assertEquals("rockify stone,andesite 3", middle.value(), "la orden no ve la bandera");
        assertTrue(middle.request().preview());
        assertEquals("komaedit texture rockify stone,andesite 3", middle.request().command());

        Seen alias = run("ke texture --preview rockify");
        assertEquals("rockify", alias.value());
        assertTrue(alias.request().preview(), "también por los alias");

        Seen first = run("komaedit texture -v rockify");
        assertEquals("rockify", first.value(), "la bandera también vale como primera palabra del texto");
        assertTrue(first.request().preview());

        Seen only = run("komaedit texture -v");
        assertEquals("", only.value(), "solo la bandera: la orden corre sin argumentos");
        assertTrue(only.request().preview());
    }

    @Test
    void shortFlagIsLeftAloneWhereItAlreadyMeansSomethingElse() throws CommandSyntaxException {
        Seen vertical = run("komaedit deform noise Perlin 3 -v");
        assertEquals("noise Perlin 3 -v", vertical.value(), "deform noise -v es «vertical» en ezEdits");
        assertFalse(vertical.request().preview());

        Seen both = run("komaedit deform noise Perlin 3 -v --preview");
        assertEquals("noise Perlin 3 -v", both.value());
        assertTrue(both.request().preview(), "--preview vale en todas partes");

        Seen other = run("komaedit deform rotate 90 -v");
        assertEquals("rotate 90", other.value(), "en otros modos -v es la vista previa");
        assertTrue(other.request().preview());
    }

    @Test
    void ineligibleCommandsGetNoFlag() throws CommandSyntaxException {
        assertNull(run("komaedit help").request(), "help no se envuelve");
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("komaedit help -v", new Source(PLAYER)));
    }

    @Test
    void previewCommandStaysArmedOnceForChainsThatLoseIt() throws CommandSyntaxException {
        run("komaedit build sphere -v");
        assertTrue(EditRequests.peek(PLAYER).preview(), "queda armada tras la orden");
        assertTrue(EditRequests.resolve(PLAYER).preview());
        assertEquals(EditRequest.Kind.DEFAULT, EditRequests.resolve(PLAYER).kind(), "se usa una sola vez");

        run("komaedit build sphere -v");
        run("komaedit build sphere");
        assertEquals(EditRequest.Kind.DEFAULT, EditRequests.peek(PLAYER).kind(), "otra orden la desarma");
    }

    @Test
    void stripKeepsTheRestOfTheTextUntouched() {
        assertEquals(new PreviewFlags.Stripped("a  b", true), PreviewFlags.strip("a -v  b", true));
        assertEquals(new PreviewFlags.Stripped("a b c", true), PreviewFlags.strip("-v a b --preview c -v", true));
        assertEquals(new PreviewFlags.Stripped("a -v", true), PreviewFlags.strip("a -v --preview", false));
        assertEquals(new PreviewFlags.Stripped("-vx a", false), PreviewFlags.strip("-vx a", true));
        assertEquals(new PreviewFlags.Stripped("a -V", false), PreviewFlags.strip("a -V", true));
        assertEquals(new PreviewFlags.Stripped("", false), PreviewFlags.strip(null, true));
        assertEquals("komaedit build", PreviewFlags.dropLastToken("komaedit build  -v "));
        assertEquals("texture", EditRequest.command("/komaedit texture x", false).label());
        assertEquals("komaedit texture x", EditRequest.command("/komaedit texture x", false).command());
    }

    @Test
    void requestsTravelThroughHandOffAndExpire() {
        long[] now = {0L};
        EditRequests.clock = () -> now[0];
        try {
            EditRequest preview = EditRequest.command("komaedit texture x", true);
            EditRequests.handOff(PLAYER, preview);
            assertSame(preview, EditRequests.peek(PLAYER));
            assertSame(preview, EditRequests.resolve(PLAYER));
            assertEquals(EditRequest.Kind.DEFAULT, EditRequests.resolve(PLAYER).kind(), "el relevo se consume");

            EditRequests.handOff(PLAYER, preview);
            now[0] = EditRequests.HANDOFF_NANOS + 1;
            assertEquals(EditRequest.Kind.DEFAULT, EditRequests.resolve(PLAYER).kind(), "un relevo viejo no vale");

            EditRequest outer = EditRequests.within(EditRequest.TOOL, () ->
                    EditRequests.within(EditRequest.CONFIRMED, EditRequests::current));
            assertSame(EditRequest.TOOL, outer, "dentro de una petición activa no se sustituye");
            assertSame(EditRequest.CONFIRMED, EditRequests.within(EditRequest.CONFIRMED,
                    () -> EditRequests.resolve(PLAYER)), "la del hilo manda sobre el relevo");
        } finally {
            EditRequests.clock = System::nanoTime;
        }
    }
}
