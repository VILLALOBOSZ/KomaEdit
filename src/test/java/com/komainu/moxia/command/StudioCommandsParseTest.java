package com.komainu.moxia.command;

import com.komainu.moxia.studio.tools.ToolAction;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ToolParam;
import com.komainu.moxia.studio.tools.ToolPreset;
import com.komainu.moxia.studio.tools.ToolValues;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Todo lo que puede mandar una tarjeta del Studio (Construir, la vista previa, cada botón y lo que manda al elegirla),
 * con los valores de serie, con cada modo y cada opción, con sus presets y con los números en sus extremos, lo acepta
 * el árbol real de {@code /komaedit}: Brigadier lo lee entero y llega a una orden. Lo que la tarjeta no deja construir
 * (un nombre vacío, direcciones que no casan) no se manda y no se mira.
 *
 * <p>Los permisos del árbol se abren aquí (no hay jugador): lo que se comprueba es la sintaxis. Las opciones de
 * detrás de cada orden las leen los analizadores de cada herramienta, que prueban {@code ToolCardsTest} y las de cada
 * tarjeta.</p>
 */
class StudioCommandsParseTest {
    private static final ToolContext EVERYTHING = new ToolContext(true, ToolCatalog.permissionNodes(), true, 1_000L,
            4, false);
    /** Las órdenes que atiende el propio cliente ({@code kegui}, {@code kedisplay}): no pasan por el servidor. */
    private static final List<String> CLIENT = List.of("kegui regla ", "kegui vista ", "kegui vistas",
            "kegui notas", "kegui referencias", "kedisplay ajustes ");

    private static CommandDispatcher<ServerCommandSource> dispatcher;

    @BeforeAll
    static void registerEverything() throws ReflectiveOperationException {
        dispatcher = new CommandDispatcher<>();
        MoxiaCommands.register(dispatcher);
        Field requirement = CommandNode.class.getDeclaredField("requirement");
        requirement.setAccessible(true);
        open(dispatcher.getRoot(), requirement, new IdentityHashMap<>());
    }

    /** Sin jugador no hay permisos que mirar: todo nodo se puede usar. */
    private static void open(CommandNode<ServerCommandSource> node, Field requirement,
                             Map<CommandNode<?>, Boolean> seen) throws IllegalAccessException {
        if (seen.put(node, true) != null) {
            return;
        }
        Predicate<ServerCommandSource> anyone = source -> true;
        requirement.set(node, anyone);
        for (CommandNode<ServerCommandSource> child : node.getChildren()) {
            open(child, requirement, seen);
        }
        if (node.getRedirect() != null) {
            open(node.getRedirect(), requirement, seen);
        }
    }

    @Test
    void everythingACardSendsIsReadWholeByTheServer() {
        List<String> refused = new ArrayList<>();
        int checked = 0;
        for (ToolCard card : ToolCatalog.ALL) {
            for (String command : commands(card)) {
                if (CLIENT.stream().anyMatch(command::startsWith)) {
                    continue;
                }
                checked++;
                ParseResults<ServerCommandSource> parse = dispatcher.parse(command, null);
                if (parse.getReader().canRead() || parse.getContext().getCommand() == null) {
                    refused.add(card.id() + ": «" + command + "» " + parse.getExceptions().values());
                }
            }
        }
        assertEquals(List.of(), refused);
        assertTrue(checked > 1_000, "se han mirado " + checked + " órdenes");
    }

    @Test
    void whatTheClientAnswersStartsWithItsOwnCommands() {
        for (ToolCard card : ToolCatalog.ALL) {
            for (String command : commands(card)) {
                assertTrue(command.startsWith("komaedit ") || CLIENT.stream().anyMatch(command::startsWith),
                        card.id() + ": «" + command + "»");
            }
        }
    }

    /** Todas las órdenes distintas que puede mandar la tarjeta con valores que deja construir. */
    private static Set<String> commands(ToolCard card) {
        Set<String> commands = new LinkedHashSet<>();
        if (card.selectCommand() != null) {
            commands.add(card.selectCommand());
        }
        for (ToolValues values : variants(card)) {
            if (card.blocked(values, EVERYTHING)) {
                continue;
            }
            commands.add(card.command(values, false));
            if (card.previewable(values)) {
                commands.add(card.command(values, true));
            }
            for (ToolAction action : card.actions(values)) {
                commands.add(ToolCard.withName(action.command().apply(values), "prueba"));
            }
        }
        return commands;
    }

    /** De serie; cada combinación de modos y opciones (hasta unos cientos); sus presets; y los extremos. */
    private static List<ToolValues> variants(ToolCard card) {
        List<ToolValues> modes = new ArrayList<>(List.of(card.defaults()));
        for (ToolParam param : card.params()) {
            if (param instanceof ToolParam.Choice choice && !choice.dynamic()
                    && modes.size() * choice.options().size() <= 400) {
                List<ToolValues> next = new ArrayList<>();
                for (ToolValues mode : modes) {
                    for (String option : choice.options()) {
                        next.add(mode.with(choice.id(), option));
                    }
                }
                modes = next;
            }
        }
        List<ToolValues> variants = new ArrayList<>(modes);
        for (ToolValues mode : modes) {
            for (ToolPreset preset : card.presets(mode)) {
                variants.add(card.apply(mode, preset));
            }
            ToolValues low = mode;
            ToolValues high = mode;
            for (ToolParam param : card.params()) {
                switch (param) {
                    case ToolParam.Number number -> {
                        low = low.with(number.id(), number.range().min());
                        high = high.with(number.id(), number.range().max());
                    }
                    case ToolParam.Toggle toggle -> high = high.with(toggle.id(), !toggle.defaultValue());
                    case ToolParam.Text text when text.required() ->
                            high = high.with(text.id(), "a".repeat(Math.min(text.maxLength(), 32)));
                    default -> {
                    }
                }
            }
            variants.add(low);
            variants.add(high);
        }
        return variants;
    }
}
