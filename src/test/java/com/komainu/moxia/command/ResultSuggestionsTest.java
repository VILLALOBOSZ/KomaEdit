package com.komainu.moxia.command;

import com.komainu.moxia.gradient.GradientPalette;
import com.komainu.moxia.gradient.PaletteSpec;
import com.komainu.moxia.placement.DirectionExpr;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.worldedit.ez.EzMaskParser;
import com.komainu.moxia.worldedit.ez.EzPatternParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.sk89q.worldedit.extension.input.ParserContext;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El {@code =} de ezEdits en el chat: paletas desplegadas y direcciones calculadas, como sugerencia de Brigadier. */
class ResultSuggestionsTest {
    private static final Map<String, String> MINE = Map.of("mia", "stone,##Grayscale(1:2)", "vacia", "nada[");
    private static final DirectionExpr.Context SOUTH = new DirectionExpr.Context(new Vec3(0, 0, 1));

    private static Optional<String> complete(String token) {
        return ResultSuggestions.complete(token, MINE::get, SOUTH);
    }

    private static String grayscale() {
        return String.join(",", GradientPalette.byId("grayscale").patterns().split(";"));
    }

    @Test
    void expandsPresetsSavedPalettesAndModifiers() {
        assertEquals(Optional.of(grayscale()), complete("##Grayscale="));
        List<String> gray = PaletteSpec.parse("##Grayscale");
        assertEquals(Optional.of(String.join(",", PaletteSpec.parse("-##Grayscale(1:3)"))),
                complete("-##Grayscale(1:3)="));
        assertEquals(3, complete("-##Grayscale(1:3)=").orElseThrow().split(",").length);
        assertEquals(Optional.of("stone," + gray.get(0) + "," + gray.get(1)), complete("#mia="));
        assertEquals(Optional.of("gold_block,gold_block,diamond_block"), complete("gold_block*2,diamond_block="));
        assertEquals(Optional.of("oak_stairs[facing=east],stone"), complete("minecraft:oak_stairs[facing=east],stone="));
    }

    @Test
    void keepsWhatComesBeforeThePalette() {
        String magma = String.join(",", PaletteSpec.parse("##Magma"));
        assertEquals(Optional.of("Sh(S:Cone,P:" + magma), complete("Sh(S:Cone,P:##Magma="));
        assertEquals(Optional.of("#palette[" + grayscale()), complete("#palette[##Grayscale="));
    }

    @Test
    void evaluatesDirections() {
        // El paréntesis de C( aún no se ha cerrado: lo cierra quien escribe.
        assertEquals(Optional.of("C(D:(1,0.5,-1)"), complete("C(D:east-z+(0,0.5,0)="));
        assertEquals(Optional.of("Constant(Direction:(0,0,1)"), complete("Constant(Direction:aim="));
        assertEquals(Optional.of("C(D:(-1,0,0)"), complete("C(D:right="), "mirando al sur, la derecha es el oeste");
    }

    @Test
    void leavesEverythingElseAlone() {
        assertEquals(Optional.empty(), complete("##Grayscale"));
        assertEquals(Optional.empty(), complete("="));
        assertEquals(Optional.empty(), complete("#nope="));
        assertEquals(Optional.empty(), complete("#vacia="));
        assertEquals(Optional.empty(), ResultSuggestions.complete("#mia=", null, SOUTH), "sin jugador, sin #mia");
        assertEquals(Optional.empty(), complete("50%stone="));
    }

    @Test
    void everyExpansionReadsBackAsTheSamePalette() {
        for (GradientPalette palette : GradientPalette.values()) {
            String token = "##" + palette.displayName();
            String expanded = complete(token + "=").orElseThrow();
            assertEquals(PaletteSpec.parse(token), PaletteSpec.parse(expanded), token);
        }
    }

    @Test
    void brigadierOffersItForTheLastWord() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("p")
                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("args",
                                StringArgumentType.greedyString())
                        .suggests(ResultSuggestions.PROVIDER)
                        .executes(context -> 1)));
        String line = "p gradient ##Grayscale=";
        Suggestions suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(line, null)).join();

        assertEquals(1, suggestions.getList().size());
        assertEquals(grayscale(), suggestions.getList().getFirst().getText());
        assertEquals(line.indexOf("##"), suggestions.getRange().getStart(), "sustituye solo la última palabra");
        assertEquals(line.length(), suggestions.getRange().getEnd());

        Suggestions none = dispatcher.getCompletionSuggestions(dispatcher.parse("p gradient ##Grayscale", null))
                .join();
        assertTrue(none.isEmpty());
    }

    @Test
    void worldEditMasksAndPatternsOfferItToo() {
        ParserContext context = new ParserContext();
        assertTrue(new EzPatternParser(null).getSuggestions("#palette[##Grayscale=", context)
                .anyMatch(("#palette[" + grayscale())::equals));
        assertTrue(new EzMaskParser(null).getSuggestions("#fuzzypalette[##Grayscale=", context)
                .anyMatch(("#fuzzypalette[" + grayscale())::equals));
    }

    @Test
    void theRealCommandsUseIt() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        MoxiaCommands.register(dispatcher);
        CommandNode<ServerCommandSource> palette = dispatcher.getRoot().getChild("komaedit").getChild("palette");
        assertNotNull(palette);
        for (String sub : List.of("save", "print", "place", "swap")) {
            CommandNode<ServerCommandSource> node = palette.getChild(sub).getChild("args");
            assertSame(ResultSuggestions.PROVIDER,
                    assertInstanceOf(ArgumentCommandNode.class, node).getCustomSuggestions(), sub);
        }
        assertSame(ResultSuggestions.PROVIDER, assertInstanceOf(ArgumentCommandNode.class,
                palette.getChild("encode").getChild("palette")).getCustomSuggestions());
    }
}
