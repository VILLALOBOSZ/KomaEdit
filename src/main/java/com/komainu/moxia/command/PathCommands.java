package com.komainu.moxia.command;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.path.PathEditing;
import com.komainu.moxia.path.PathGenerators;
import com.komainu.moxia.path.PathHistory;
import com.komainu.moxia.path.PathHologramSync;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.persistence.PathStore;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit path …}: capturar, editar, generar y guardar trayectorias. Toda la lógica de puntos vive en
 * {@link PathEditing} y {@link PathGenerators}; aquí solo se leen los argumentos y se responde al jugador.
 */
public final class PathCommands {
    private static final String ROOT = "/komaedit path ";
    private static final int COORDINATE = 30_000_000;

    private static final SuggestionProvider<ServerCommandSource> INDEX_SUGGESTIONS = (context, builder) -> {
        if (context.getSource().getEntity() instanceof ServerPlayerEntity player) {
            List<BlockVector3> points = PathManager.saved(player.getUuid()).points();
            for (int index = 0; index < points.size(); index++) {
                BlockVector3 point = points.get(index);
                builder.suggest(index + 1, Text.literal(point.x() + ", " + point.y() + ", " + point.z()));
            }
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> NAME_SUGGESTIONS = (context, builder) -> {
        if (context.getSource().getEntity() instanceof ServerPlayerEntity player) {
            try {
                PathStore.names(player.getUuid()).forEach(builder::suggest);
            } catch (RuntimeException ignored) {
                // Sin proyecto enlazado todavía: no hay nombres que sugerir.
            }
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> BULGE_SUGGESTIONS = (context, builder) -> {
        builder.suggest("down", Text.literal("Cuelga hacia abajo, como una cuerda"));
        builder.suggest("up", Text.literal("Se curva hacia arriba, como un arco"));
        builder.suggest("left", Text.literal("Hacia la izquierda yendo del primer punto al último"));
        builder.suggest("right", Text.literal("Hacia la derecha yendo del primer punto al último"));
        return builder.buildFuture();
    };

    private PathCommands() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> command() {
        LiteralArgumentBuilder<ServerCommandSource> path = literal("path");
        generators(path);
        editing(path);
        return path
                .then(literal("add").executes(PathCommands::addTarget))
                .then(literal("point").then(coordinates(PathCommands::addExact)))
                .then(literal("here").executes(PathCommands::addHere))
                .then(literal("selection").executes(PathCommands::useSelection))
                .then(literal("undo").executes(PathCommands::undo))
                .then(literal("redo").executes(PathCommands::redo))
                .then(literal("pop").executes(PathCommands::pop))
                .then(literal("clear").executes(PathCommands::clear))
                .then(literal("close")
                        .executes(context -> edit(context, "Lazo", state -> new PathHistory.State(
                                state.points(), !state.closed())))
                        .then(literal("on").executes(context -> setClosed(context, true)))
                        .then(literal("off").executes(context -> setClosed(context, false))))
                .then(literal("open").executes(context -> setClosed(context, false)))
                .then(literal("save").then(argument("path_name", StringArgumentType.word())
                        .suggests(NAME_SUGGESTIONS).executes(PathCommands::save)))
                .then(literal("load").then(argument("path_name", StringArgumentType.word())
                        .suggests(NAME_SUGGESTIONS).executes(PathCommands::load)))
                .then(literal("delete").then(argument("path_name", StringArgumentType.word())
                        .suggests(NAME_SUGGESTIONS).executes(PathCommands::delete)))
                .then(literal("list").executes(PathCommands::list))
                .then(literal("mode")
                        .then(literal("toggle").executes(PathCommands::toggleCapture))
                        .then(literal("on").executes(context -> setCapture(context, true)))
                        .then(literal("off").executes(context -> setCapture(context, false))))
                .then(literal("points").executes(PathCommands::points))
                .then(literal("status").executes(PathCommands::status))
                .then(literal("help").executes(context -> HelpCommands.show(context, "path")))
                .executes(PathCommands::status);
    }

    /** Une todos los subcomandos de edición en un solo nodo para que {@link #command()} se lea de un vistazo. */
    private static LiteralArgumentBuilder<ServerCommandSource> editing(LiteralArgumentBuilder<ServerCommandSource> path) {
        return path.then(literal("insert").then(index("path_index")
                        .executes(context -> edit(context, "Punto insertado", state -> new PathHistory.State(
                                PathEditing.insertMidpoint(state.points(), index(context), state.closed()),
                                state.closed())))
                        .then(literal("here").executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            BlockVector3 point = vector(player);
                            return edit(context, "Punto insertado", state -> new PathHistory.State(
                                    PathEditing.insert(state.points(), index(context) + 1, point), state.closed()));
                        }))))
                .then(literal("remove").then(index("path_index").executes(context -> edit(
                        context, "Punto " + (index(context) + 1) + " quitado", state -> new PathHistory.State(
                                PathEditing.remove(state.points(), index(context)), state.closed())))))
                .then(literal("set").then(index("path_index")
                        .then(literal("here").executes(context -> {
                            BlockVector3 point = vector(context.getSource().getPlayerOrThrow());
                            return edit(context, "Punto " + (index(context) + 1) + " movido", state ->
                                    new PathHistory.State(PathEditing.set(state.points(), index(context), point),
                                            state.closed()));
                        }))
                        .then(coordinates(context -> {
                            BlockVector3 point = exact(context);
                            return edit(context, "Punto " + (index(context) + 1) + " movido", state ->
                                    new PathHistory.State(PathEditing.set(state.points(), index(context), point),
                                            state.closed()));
                        }))))
                .then(literal("shift")
                        .then(literal("here").executes(context -> {
                            BlockVector3 target = vector(context.getSource().getPlayerOrThrow());
                            return edit(context, "Recorrido movido", state -> {
                                requirePoints(state, 1);
                                BlockVector3 delta = target.subtract(state.points().getFirst());
                                return new PathHistory.State(PathEditing.translate(
                                        state.points(), delta.x(), delta.y(), delta.z()), state.closed());
                            });
                        }))
                        .then(argument("dx", IntegerArgumentType.integer(-COORDINATE, COORDINATE))
                                .then(argument("dy", IntegerArgumentType.integer(-4096, 4096))
                                        .then(argument("dz", IntegerArgumentType.integer(-COORDINATE, COORDINATE))
                                                .executes(context -> edit(context, "Recorrido movido", state ->
                                                        new PathHistory.State(PathEditing.translate(
                                                                state.points(),
                                                                IntegerArgumentType.getInteger(context, "dx"),
                                                                IntegerArgumentType.getInteger(context, "dy"),
                                                                IntegerArgumentType.getInteger(context, "dz")
                                                        ), state.closed())))))))
                .then(literal("reverse").executes(context -> edit(context, "Sentido invertido", state ->
                        new PathHistory.State(PathEditing.reverse(state.points()), state.closed()))))
                .then(literal("mirror").then(argument("axis", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("x", Text.literal("Refleja este–oeste"));
                            builder.suggest("z", Text.literal("Refleja norte–sur"));
                            builder.suggest("y", Text.literal("Pone el recorrido boca abajo"));
                            return builder.buildFuture();
                        })
                        .executes(context -> mirror(context, false))
                        .then(literal("append").executes(context -> mirror(context, true)))))
                .then(literal("rotate").then(argument("degrees", IntegerArgumentType.integer(-270, 270))
                        .suggests((context, builder) -> {
                            builder.suggest(90, Text.literal("Un cuarto de vuelta"));
                            builder.suggest(180, Text.literal("Media vuelta"));
                            builder.suggest(270, Text.literal("Tres cuartos de vuelta"));
                            return builder.buildFuture();
                        })
                        .executes(context -> edit(context, "Recorrido girado", state -> new PathHistory.State(
                                PathEditing.rotateY(state.points(), IntegerArgumentType.getInteger(context, "degrees")),
                                state.closed())))))
                .then(literal("snap").then(argument("step", IntegerArgumentType.integer(1, 64))
                        .executes(context -> snap(context, false))
                        .then(literal("all").executes(context -> snap(context, true)))))
                .then(literal("smooth")
                        .executes(context -> smooth(context, 0.5, 1))
                        .then(argument("strength", DoubleArgumentType.doubleArg(0.05, 1.0))
                                .executes(context -> smooth(context, DoubleArgumentType.getDouble(context, "strength"), 1))
                                .then(argument("passes", IntegerArgumentType.integer(1, 8))
                                        .executes(context -> smooth(
                                                context,
                                                DoubleArgumentType.getDouble(context, "strength"),
                                                IntegerArgumentType.getInteger(context, "passes")
                                        )))))
                .then(literal("simplify")
                        .executes(context -> simplify(context, 1.0))
                        .then(argument("tolerance", DoubleArgumentType.doubleArg(0.0, 64.0))
                                .executes(context -> simplify(context, DoubleArgumentType.getDouble(context, "tolerance")))))
                .then(literal("subdivide").executes(context -> edit(context, "Tramos subdivididos", state ->
                        new PathHistory.State(PathEditing.subdivide(state.points(), state.closed(), PathManager.MAX_POINTS),
                                state.closed()))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> generators(LiteralArgumentBuilder<ServerCommandSource> path) {
        return path.then(literal("circle").then(argument("path_radius", IntegerArgumentType.integer(2, 128))
                        .executes(context -> polygon(context, 16, 0))
                        .then(argument("points", IntegerArgumentType.integer(8, PathManager.MAX_POINTS))
                                .executes(context -> polygon(context, IntegerArgumentType.getInteger(context, "points"), 0)))))
                .then(literal("polygon").then(argument("path_radius", IntegerArgumentType.integer(2, 128))
                        .then(argument("sides", IntegerArgumentType.integer(3, PathManager.MAX_POINTS))
                                .executes(context -> polygon(context, IntegerArgumentType.getInteger(context, "sides"), 0))
                                .then(argument("start_angle", IntegerArgumentType.integer(-360, 360))
                                        .executes(context -> polygon(
                                                context,
                                                IntegerArgumentType.getInteger(context, "sides"),
                                                IntegerArgumentType.getInteger(context, "start_angle")
                                        ))))))
                .then(literal("spiral").then(argument("start_radius", DoubleArgumentType.doubleArg(0.0, 256.0))
                        .then(argument("end_radius", DoubleArgumentType.doubleArg(0.0, 256.0))
                                .then(argument("turns", DoubleArgumentType.doubleArg(0.25, 16.0))
                                        .executes(context -> spiral(context, radius(context, "start_radius"),
                                                radius(context, "end_radius"), 0.0, null))
                                        .then(argument("height", IntegerArgumentType.integer(-512, 512))
                                                .executes(context -> spiral(context, radius(context, "start_radius"),
                                                        radius(context, "end_radius"), height(context), null))
                                                .then(pointCount().executes(context -> spiral(
                                                        context,
                                                        radius(context, "start_radius"),
                                                        radius(context, "end_radius"),
                                                        height(context),
                                                        IntegerArgumentType.getInteger(context, "points")
                                                ))))))))
                .then(literal("helix").then(argument("path_radius", DoubleArgumentType.doubleArg(1.0, 256.0))
                        .then(argument("turns", DoubleArgumentType.doubleArg(0.25, 16.0))
                                .then(argument("height", IntegerArgumentType.integer(-512, 512))
                                        .executes(context -> spiral(context, radius(context, "path_radius"),
                                                radius(context, "path_radius"), height(context), null))
                                        .then(pointCount().executes(context -> spiral(
                                                context,
                                                radius(context, "path_radius"),
                                                radius(context, "path_radius"),
                                                height(context),
                                                IntegerArgumentType.getInteger(context, "points")
                                        )))))))
                .then(twoPointCurve("arc", "arc_height", false))
                .then(twoPointCurve("catenary", "sag", true));
    }

    /**
     * {@code arc|catenary <cantidad> [down|up|left|right] [puntos]} entre el primer y el último punto del recorrido
     * (o los dos puntos de la selección de WorldEdit si no hay recorrido).
     */
    private static LiteralArgumentBuilder<ServerCommandSource> twoPointCurve(String name, String amount, boolean catenary) {
        String defaultBulge = catenary ? "down" : "up";
        RequiredArgumentBuilder<ServerCommandSource, Double> size = argument(amount, DoubleArgumentType.doubleArg(0.5, 512.0))
                .executes(context -> curve(context, catenary, amount, defaultBulge, 16));
        RequiredArgumentBuilder<ServerCommandSource, String> bulge = argument("direction", StringArgumentType.word())
                .suggests(BULGE_SUGGESTIONS)
                .executes(context -> curve(context, catenary, amount, StringArgumentType.getString(context, "direction"), 16));
        bulge.then(pointCount().executes(context -> curve(
                context,
                catenary,
                amount,
                StringArgumentType.getString(context, "direction"),
                IntegerArgumentType.getInteger(context, "points")
        )));
        size.then(bulge);
        return literal(name).then(size);
    }

    private static int curve(
            CommandContext<ServerCommandSource> context,
            boolean catenary,
            String amountArgument,
            String direction,
            int points
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            PathSnapshot ends = WorldEditService.resolvePath(player);
            BlockVector3 start = ends.points().getFirst();
            BlockVector3 end = ends.points().getLast();
            double amount = DoubleArgumentType.getDouble(context, amountArgument);
            PathGenerators.Bulge bulge = PathGenerators.Bulge.byId(direction);
            List<BlockVector3> generated = catenary
                    ? PathGenerators.catenary(start, end, amount, bulge, points)
                    : PathGenerators.arc(start, end, amount, bulge, points);
            return replaced(context, player, generated, false, (catenary ? "Catenaria" : "Arco")
                    + " de " + format(start) + " a " + format(end) + " · " + (catenary ? "comba " : "altura ")
                    + trim(amount) + " · " + bulge.id());
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Marca dos puntos con P o completa //pos1 y //pos2 para los extremos");
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int polygon(CommandContext<ServerCommandSource> context, int sides, int startAngle)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int radius = IntegerArgumentType.getInteger(context, "path_radius");
        try {
            List<BlockVector3> generated = PathGenerators.polygon(vector(player), radius, sides, startAngle);
            return replaced(context, player, generated, true,
                    (sides >= 8 ? "Círculo" : "Polígono") + " creado: radio " + radius + " · " + generated.size() + " puntos");
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int spiral(
            CommandContext<ServerCommandSource> context,
            double startRadius,
            double endRadius,
            double height,
            Integer requestedPoints
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        double turns = DoubleArgumentType.getDouble(context, "turns");
        int points = requestedPoints == null
                ? PathGenerators.suggestedPoints(turns, PathManager.MAX_POINTS)
                : requestedPoints;
        try {
            List<BlockVector3> generated = PathGenerators.spiral(vector(player), startRadius, endRadius, turns, height, points);
            String kind = startRadius == endRadius ? "Hélice" : "Espiral";
            return replaced(context, player, generated, false, kind + " creada: " + trim(turns) + " vueltas · "
                    + generated.size() + " puntos" + (height == 0 ? "" : " · altura " + trim(height)));
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int mirror(CommandContext<ServerCommandSource> context, boolean append) throws CommandSyntaxException {
        try {
            char axis = PathEditing.axis(StringArgumentType.getString(context, "axis"));
            return edit(context, append ? "Reflejo añadido" : "Recorrido reflejado", state -> new PathHistory.State(
                    PathEditing.mirror(state.points(), axis, append), state.closed()));
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int snap(CommandContext<ServerCommandSource> context, boolean vertical) throws CommandSyntaxException {
        int step = IntegerArgumentType.getInteger(context, "step");
        return edit(context, "Ajustado a rejilla de " + step, state -> new PathHistory.State(
                PathEditing.snap(state.points(), step, vertical), state.closed()));
    }

    private static int smooth(CommandContext<ServerCommandSource> context, double strength, int passes)
            throws CommandSyntaxException {
        return edit(context, "Recorrido suavizado", state -> new PathHistory.State(
                PathEditing.relax(state.points(), state.closed(), strength, passes), state.closed()));
    }

    private static int simplify(CommandContext<ServerCommandSource> context, double tolerance)
            throws CommandSyntaxException {
        int before = PathManager.saved(context.getSource().getPlayerOrThrow().getUuid()).points().size();
        return edit(context, "Simplificado (" + before + " antes)", state -> new PathHistory.State(
                PathEditing.simplify(state.points(), tolerance), state.closed()));
    }

    private static int setClosed(CommandContext<ServerCommandSource> context, boolean closed) throws CommandSyntaxException {
        return edit(context, closed ? "Lazo cerrado" : "Lazo abierto", state ->
                new PathHistory.State(state.points(), closed));
    }

    /** Aplica una edición al recorrido actual, la anota en el historial y responde con el estado nuevo. */
    private static int edit(
            CommandContext<ServerCommandSource> context,
            String label,
            UnaryOperator<PathHistory.State> operation
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            PathHistory.State state = PathManager.update(player.getUuid(), operation);
            PathHologramSync.sync(player);
            context.getSource().sendFeedback(() -> prefix().append(Text.literal(label + " · " + describe(state))
                    .formatted(Formatting.AQUA)).append(Text.literal("  ")).append(undoButton()), false);
            return Math.max(1, state.points().size());
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int replaced(
            CommandContext<ServerCommandSource> context,
            ServerPlayerEntity player,
            List<BlockVector3> points,
            boolean closed,
            String message
    ) {
        PathManager.replace(player.getUuid(), points, closed);
        PathHologramSync.sync(player);
        context.getSource().sendFeedback(() -> prefix().append(Text.literal(message).formatted(Formatting.AQUA))
                .append(Text.literal("  ")).append(undoButton()), false);
        return points.size();
    }

    private static int addTarget(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            return pointAdded(context.getSource(), player, PathManager.addTarget(player));
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int addExact(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            return pointAdded(context.getSource(), player, PathManager.add(player.getUuid(), exact(context)));
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int addHere(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            return pointAdded(context.getSource(), player, PathManager.addHere(player));
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int useSelection(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            PathSnapshot selection = WorldEditService.selectionPath(player);
            return replaced(context, player, selection.points(), false,
                    "Trayectoria importada: " + selection.points().size() + " puntos de WorldEdit");
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "La selección de WorldEdit está incompleta");
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int undo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Optional<PathHistory.State> state = PathManager.undo(player.getUuid());
        if (state.isEmpty()) {
            return fail(context.getSource(), "No hay cambios de puntos que deshacer");
        }
        PathHologramSync.sync(player);
        context.getSource().sendFeedback(() -> prefix().append(Text.literal("Deshecho · " + describe(state.get()))
                .formatted(Formatting.AQUA)).append(Text.literal("  ")).append(button(
                "[rehacer]", ROOT + "redo", Formatting.GREEN, "Vuelve a aplicar el cambio", true)), false);
        return Math.max(1, state.get().points().size());
    }

    private static int redo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Optional<PathHistory.State> state = PathManager.redo(player.getUuid());
        if (state.isEmpty()) {
            return fail(context.getSource(), "No hay cambios de puntos que rehacer");
        }
        PathHologramSync.sync(player);
        context.getSource().sendFeedback(() -> prefix().append(Text.literal("Rehecho · " + describe(state.get()))
                .formatted(Formatting.AQUA)).append(Text.literal("  ")).append(undoButton()), false);
        return Math.max(1, state.get().points().size());
    }

    private static int pop(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (!PathManager.removeLast(player.getUuid())) {
            return fail(context.getSource(), "La trayectoria no tiene puntos");
        }
        PathHologramSync.sync(player);
        return status(context);
    }

    private static int clear(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        PathManager.clear(player.getUuid());
        PathHologramSync.sync(player);
        context.getSource().sendFeedback(() -> prefix().append(Text.literal("Trayectoria eliminada")
                .formatted(Formatting.YELLOW)).append(Text.literal("  ")).append(undoButton()), false);
        return 1;
    }

    private static int save(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            String name = PathStore.normalize(StringArgumentType.getString(context, "path_name"));
            PathSnapshot path = PathManager.saved(player.getUuid());
            if (path.points().isEmpty()) {
                return fail(context.getSource(), "La trayectoria está vacía: no hay nada que guardar");
            }
            boolean existed = PathStore.exists(player.getUuid(), name);
            PathStore.save(player.getUuid(), new PathStore.Saved(name, dimension(player), path.closed(), path.points()));
            context.getSource().sendFeedback(() -> prefix().append(Text.literal(
                    "Recorrido " + (existed ? "sobrescrito" : "guardado") + ": " + name + " · "
                            + describe(new PathHistory.State(path.points(), path.closed()))
            ).formatted(Formatting.GREEN)), false);
            return path.points().size();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int load(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String typed = StringArgumentType.getString(context, "path_name");
        try {
            PathStore.Saved saved = PathStore.load(player.getUuid(), typed);
            String here = dimension(player);
            String warning = saved.dimension().isEmpty() || saved.dimension().equals(here)
                    ? ""
                    : " · ojo: se guardó en " + saved.dimension();
            return replaced(context, player, saved.points(), saved.closed(), "Recorrido cargado: "
                    + PathStore.normalize(typed) + " · " + describe(new PathHistory.State(saved.points(), saved.closed()))
                    + warning);
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), withNameHint(player, exception.getMessage(), typed));
        } catch (IllegalStateException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int delete(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String typed = StringArgumentType.getString(context, "path_name");
        try {
            if (!PathStore.delete(player.getUuid(), typed)) {
                return fail(context.getSource(), withNameHint(player,
                        "No tienes ningún recorrido llamado " + PathStore.normalize(typed), typed));
            }
            context.getSource().sendFeedback(() -> prefix().append(Text.literal(
                    "Recorrido borrado: " + PathStore.normalize(typed)).formatted(Formatting.YELLOW)), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int list(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            List<String> names = PathStore.names(player.getUuid());
            if (names.isEmpty()) {
                context.getSource().sendFeedback(() -> prefix().append(Text.literal(
                        "No tienes recorridos guardados · /komaedit path save <nombre>").formatted(Formatting.GRAY)), false);
                return 0;
            }
            MutableText line = prefix().append(Text.literal("Recorridos (" + names.size() + "): ").formatted(Formatting.AQUA));
            for (int index = 0; index < names.size(); index++) {
                if (index > 0) {
                    line.append(Text.literal(", ").formatted(Formatting.GRAY));
                }
                line.append(button(names.get(index), ROOT + "load " + names.get(index), Formatting.WHITE,
                        "Clic: cargar " + names.get(index), true));
            }
            context.getSource().sendFeedback(() -> line, false);
            return names.size();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int points(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        PathSnapshot path = PathManager.saved(player.getUuid());
        if (path.points().isEmpty()) {
            return fail(context.getSource(), "La trayectoria no tiene puntos · /komaedit path add");
        }
        context.getSource().sendFeedback(() -> prefix().append(Text.literal(
                describe(new PathHistory.State(path.points(), path.closed()))).formatted(Formatting.AQUA)), false);
        for (int index = 0; index < path.points().size(); index++) {
            BlockVector3 point = path.points().get(index);
            int number = index + 1;
            MutableText line = Text.literal(String.format(Locale.ROOT, " %02d  %s  ", number, format(point)))
                    .formatted(Formatting.WHITE);
            line.append(button("[+]", ROOT + "insert " + number, Formatting.GREEN,
                    "Inserta un punto entre el " + number + " y el siguiente", true));
            line.append(Text.literal(" "));
            line.append(button("[−]", ROOT + "remove " + number, Formatting.RED, "Quita el punto " + number, true));
            line.append(Text.literal(" "));
            line.append(button("[aquí]", ROOT + "set " + number + " here", Formatting.YELLOW,
                    "Lleva el punto " + number + " a tu posición", true));
            line.append(Text.literal(" "));
            line.append(button("[editar]", ROOT + "set " + number + " " + point.x() + " " + point.y() + " " + point.z(),
                    Formatting.GRAY, "Escribe las coordenadas nuevas en el chat", false));
            context.getSource().sendFeedback(() -> line, false);
        }
        return path.points().size();
    }

    static int status(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        PathSnapshot path = PathManager.saved(player.getUuid());
        int[] steps = PathManager.historySteps(player.getUuid());
        context.getSource().sendFeedback(() -> prefix().append(Text.literal(
                describe(new PathHistory.State(path.points(), path.closed()))
                        + (PathManager.isCaptureMode(player.getUuid()) ? " · captura activa" : "")
                        + (path.points().size() < 2 ? " · se usará la selección de WorldEdit" : "")
                        + (steps[0] + steps[1] == 0 ? "" : " · deshacer " + steps[0] + " / rehacer " + steps[1])
        ).formatted(Formatting.AQUA)), false);
        context.getSource().sendFeedback(() -> Text.literal("  ")
                .append(button("[puntos]", ROOT + "points", Formatting.WHITE, "Lista los puntos con acciones", true))
                .append(Text.literal(" "))
                .append(undoButton())
                .append(Text.literal(" "))
                .append(button("[invertir]", ROOT + "reverse", Formatting.WHITE, "Recorre los puntos al revés", true))
                .append(Text.literal(" "))
                .append(button(path.closed() ? "[abrir]" : "[cerrar]", ROOT + (path.closed() ? "open" : "close on"),
                        Formatting.WHITE, path.closed() ? "Abre el lazo" : "Une el último punto con el primero", true))
                .append(Text.literal(" "))
                .append(button("[guardar]", ROOT + "save ", Formatting.GREEN, "Escribe un nombre para guardarlo", false))
                .append(Text.literal(" "))
                .append(button("[ayuda]", "/komaedit help path", Formatting.GRAY, "Todos los comandos de trayectoria", true)),
                false);
        return path.points().size();
    }

    private static int pointAdded(ServerCommandSource source, ServerPlayerEntity player, BlockVector3 point) {
        PathSnapshot path = PathManager.saved(player.getUuid());
        PathHologramSync.sync(player);
        source.sendFeedback(() -> prefix().append(Text.literal(
                "Punto " + path.points().size() + ": " + format(point)).formatted(Formatting.AQUA)), false);
        return path.points().size();
    }

    private static int toggleCapture(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        return captureChanged(context.getSource(), player, PathManager.toggleCaptureMode(player.getUuid()));
    }

    private static int setCapture(CommandContext<ServerCommandSource> context, boolean active) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        PathManager.setCaptureMode(player.getUuid(), active);
        return captureChanged(context.getSource(), player, active);
    }

    private static int captureChanged(ServerCommandSource source, ServerPlayerEntity player, boolean active) {
        PathHologramSync.sync(player);
        source.sendFeedback(() -> prefix().append(Text.literal(active
                ? "Captura de trayectoria activa · clic derecho para añadir puntos"
                : "Captura desactivada · los puntos se conservaron"
        ).formatted(active ? Formatting.AQUA : Formatting.YELLOW)), false);
        return active ? 1 : 0;
    }

    /** «12 puntos · abierta · 86 bloques de largo». */
    static String describe(PathHistory.State state) {
        int size = state.points().size();
        String text = size + (size == 1 ? " punto" : " puntos") + " · " + (state.closed() ? "cerrada" : "abierta");
        if (size >= 2) {
            text += " · " + Math.round(PathEditing.length(state.points(), state.closed())) + " bloques de largo";
        }
        return text;
    }

    private static String withNameHint(ServerPlayerEntity player, String message, String typed) {
        try {
            return DidYouMean.hint(message, typed, PathStore.names(player.getUuid()));
        } catch (RuntimeException ignored) {
            return message;
        }
    }

    private static void requirePoints(PathHistory.State state, int minimum) {
        if (state.points().size() < minimum) {
            throw new IllegalArgumentException("La trayectoria no tiene puntos");
        }
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Integer> index(String name) {
        return argument(name, IntegerArgumentType.integer(1, PathManager.MAX_POINTS)).suggests(INDEX_SUGGESTIONS);
    }

    private static int index(CommandContext<ServerCommandSource> context) {
        return IntegerArgumentType.getInteger(context, "path_index") - 1;
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Integer> pointCount() {
        return argument("points", IntegerArgumentType.integer(3, PathManager.MAX_POINTS));
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Integer> coordinates(
            com.mojang.brigadier.Command<ServerCommandSource> command
    ) {
        return argument("point_x", IntegerArgumentType.integer(-COORDINATE, COORDINATE))
                .then(argument("point_y", IntegerArgumentType.integer(-2048, 2048))
                        .then(argument("point_z", IntegerArgumentType.integer(-COORDINATE, COORDINATE))
                                .executes(command)));
    }

    private static BlockVector3 exact(CommandContext<ServerCommandSource> context) {
        return BlockVector3.at(
                IntegerArgumentType.getInteger(context, "point_x"),
                IntegerArgumentType.getInteger(context, "point_y"),
                IntegerArgumentType.getInteger(context, "point_z")
        );
    }

    private static BlockVector3 vector(ServerPlayerEntity player) {
        var position = player.getBlockPos();
        return BlockVector3.at(position.getX(), position.getY(), position.getZ());
    }

    private static double radius(CommandContext<ServerCommandSource> context, String name) {
        return DoubleArgumentType.getDouble(context, name);
    }

    private static double height(CommandContext<ServerCommandSource> context) {
        return IntegerArgumentType.getInteger(context, "height");
    }

    private static String dimension(ServerPlayerEntity player) {
        return player.getServerWorld().getRegistryKey().getValue().toString();
    }

    private static String format(BlockVector3 point) {
        return point.x() + ", " + point.y() + ", " + point.z();
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static MutableText undoButton() {
        return button("[deshacer]", ROOT + "undo", Formatting.GOLD, "Deshace el último cambio de puntos", true);
    }

    /** Botón de chat: {@code run} lo ejecuta al hacer clic; si no, lo deja escrito para completarlo. */
    static MutableText button(String label, String command, Formatting color, String hover, boolean run) {
        return Text.literal(label).styled(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(run ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND,
                        command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hover + "\n" + command)
                        .formatted(Formatting.GRAY))));
    }

    private static MutableText prefix() {
        return Feedback.prefix();
    }

    private static int fail(ServerCommandSource source, String message) {
        return EzCommandSupport.fail(source, message);
    }

    /** Para el listado de ayuda: nombres de los subcomandos. */
    static List<String> subcommands() {
        return new ArrayList<>(List.of("add", "point", "here", "selection", "circle", "polygon", "spiral", "helix",
                "arc", "catenary", "undo", "redo", "pop", "clear", "close", "open", "insert", "remove", "set", "shift",
                "reverse", "mirror", "rotate", "snap", "smooth", "simplify", "subdivide", "save", "load", "delete",
                "list", "mode", "points", "status", "help"));
    }
}
