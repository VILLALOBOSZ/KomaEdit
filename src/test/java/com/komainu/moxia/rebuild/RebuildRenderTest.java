package com.komainu.moxia.rebuild;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.smooth.PieceRender;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La casa de las pruebas levantándose fuera del juego: la reconstrucción corre tick a tick con el reloj de mentira y
 * cada fotograma es el mundo en ese momento. Escribe {@code constructor.gif} (la animación «como un constructor»),
 * {@code constructor_tira.png} (el final de cada fase) y {@code ordenes.png} (los órdenes a un cuarto, la mitad y
 * tres cuartos). Solo con {@code KOMAEDIT_REBUILD_RENDER=<carpeta>}; en el CI no hace nada.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_REBUILD_RENDER", matches = ".+")
class RebuildRenderTest {
    private static final int SCALE = 9;
    private static final long UNLIMITED = Long.MAX_VALUE / 4;
    /** El terreno de alrededor, que no es parte de la obra: da suelo a los fotogramas vacíos. */
    private static final int GROUND = 0x7A5C3E;
    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("grass_block", 0x6DA544), Map.entry("cobblestone", 0x8C8C8C), Map.entry("dirt_path", 0xA88F55),
            Map.entry("oak_log", 0x6B5231), Map.entry("stripped_spruce_log", 0xA07D4C),
            Map.entry("oak_planks", 0xC29A63), Map.entry("glass_pane", 0xB7E1EE), Map.entry("oak_door", 0x8E6B3E),
            Map.entry("spruce_stairs", 0x5C4029), Map.entry("spruce_slab", 0x4E3622),
            Map.entry("wall_torch", 0xF6C443), Map.entry("oak_fence", 0xA8875A), Map.entry("chest", 0x9B6B2F),
            Map.entry("red_carpet", 0xB02E26), Map.entry("lantern", 0x3F3F48), Map.entry("poppy", 0xD3302A),
            Map.entry("dandelion", 0xF2D531), Map.entry("cornflower", 0x4E6ED8), Map.entry("short_grass", 0x5E9E3A),
            Map.entry("oxeye_daisy", 0xEDEDE0), Map.entry("oak_leaves", 0x4E8F33));

    private static Path folder() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_REBUILD_RENDER"));
        Files.createDirectories(folder);
        return folder;
    }

    @Test
    void theHouseRisesLikeABuilder() throws Exception {
        RebuildFixtures.Fixture house = RebuildFixtures.selection(RebuildFixtures.house());
        RebuildPace pace = RebuildPace.DEFAULT.withDuration(30 * 20).withLayerPause(20).withLeadIn(20);
        RebuildFixtures.Rig rig = new RebuildFixtures.Rig(house, RebuildOrder.BUILDER, pace);
        Long2ObjectOpenHashMap<PieceRender.Cell> frame = cells(rig, true);
        List<BufferedImage> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        List<BufferedImage> strip = new ArrayList<>();
        List<String> stripLabels = new ArrayList<>();
        strip.add(PieceRender.render(cells(rig, false), SCALE, frame));
        stripLabels.add("antes: la casa en la selección");
        String group = "";
        int tick = 0;
        while (!rig.run.done()) {
            String before = rig.run.phase() == RebuildRun.Phase.BUILDING && rig.run.step() > 0 ? rig.run.groupLabel()
                    : "";
            rig.tick(UNLIMITED);
            tick++;
            String now = rig.run.phase() == RebuildRun.Phase.BUILDING ? rig.run.groupLabel() : "";
            if (tick == 1) {
                strip.add(PieceRender.render(cells(rig, false), SCALE, frame));
                stripLabels.add("vaciada (1 s de espera)");
            }
            if (!before.isEmpty() && !before.equals(now) || rig.run.done() && !group.isEmpty()) {
                // Acaba de terminar una fase: va a la tira.
                strip.add(PieceRender.render(cells(rig, false), SCALE, frame));
                stripLabels.add(rig.run.done() ? group + " (acabada)" : before);
            }
            if (!now.isEmpty()) {
                group = now;
            }
            if (tick % 6 == 0 || rig.run.done()) {
                frames.add(captioned(PieceRender.render(cells(rig, false), SCALE, frame), rig, tick));
                delays.add(rig.run.done() ? 300 : 10);
            }
            assertTrue(tick < 2_000, "la animación tiene que acabar");
        }
        assertTrue(rig.world.showsTarget(), "la casa acaba entera");
        Path folder = folder();
        gif(frames, delays, folder.resolve("constructor.gif"));
        ImageIO.write(PieceRender.sheet(strip, stripLabels, 4), "png", folder.resolve("constructor_tira.png").toFile());
        assertTrue(tick >= 600 && tick <= 660, "unos 30 s de animación más la espera: " + tick + " ticks");
    }

    @Test
    void everyOrderAtAQuarterHalfAndThreeQuarters() throws Exception {
        RebuildFixtures.Fixture house = RebuildFixtures.selection(RebuildFixtures.house());
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        RebuildFixtures.Rig sizing = new RebuildFixtures.Rig(house, RebuildOrder.LAYERS, RebuildPace.DEFAULT);
        Long2ObjectOpenHashMap<PieceRender.Cell> frame = cells(sizing, true);
        for (RebuildOrder order : List.of(RebuildOrder.LAYERS, RebuildOrder.WAVES, RebuildOrder.AXIS,
                RebuildOrder.RANDOM, RebuildOrder.BUILDER)) {
            RebuildSequence.Options options = new RebuildSequence.Options(RebuildAxis.Z_POSITIVE, null, 3L);
            RebuildFixtures.Rig rig = new RebuildFixtures.Rig(house, order, options,
                    RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(1));
            int total = house.model().buildable();
            for (int quarter = 1; quarter <= 3; quarter++) {
                while (rig.run.step() < total * quarter / 4) {
                    rig.tick(UNLIMITED);
                }
                images.add(PieceRender.render(cells(rig, false), SCALE, frame));
                labels.add(order.id() + (order == RebuildOrder.AXIS ? " (z)" : "") + " · " + quarter * 25 + " %");
            }
        }
        ImageIO.write(PieceRender.sheet(images, labels, 3), "png", folder().resolve("ordenes.png").toFile());
    }

    /** El mundo de la prueba como celdas del render, con el terreno de alrededor; {@code all}: la obra entera. */
    private static Long2ObjectOpenHashMap<PieceRender.Cell> cells(RebuildFixtures.Rig rig, boolean all) {
        Long2ObjectOpenHashMap<PieceRender.Cell> cells = new Long2ObjectOpenHashMap<>();
        for (int x = -2; x <= 16; x++) {
            for (int z = -2; z <= 16; z++) {
                cells.put(PackedBlockPlan.pack(x, -1, z), new PieceRender.Cell(SmoothPiece.FULL, GROUND));
            }
        }
        RebuildModel model = rig.fixture.model();
        for (int entry = 0; entry < model.size(); entry++) {
            long position = model.position(entry);
            int raw = all ? model.raw(model.target(entry)) : rig.world.at(position);
            if (raw == RebuildFixtures.AIR_RAW) {
                continue;
            }
            cells.put(position, cell(rig.fixture.palette().get(raw - RebuildFixtures.RAW_OFFSET)));
        }
        return cells;
    }

    private static PieceRender.Cell cell(String state) {
        String type = state.substring(state.indexOf(':') + 1).replaceAll("\\[.*", "");
        int color = COLORS.getOrDefault(type, 0xFF00FF);
        if (type.endsWith("_stairs")) {
            int facing = switch (property(state, "facing")) {
                case "south" -> SmoothPiece.SOUTH;
                case "west" -> SmoothPiece.WEST;
                case "north" -> SmoothPiece.NORTH;
                default -> SmoothPiece.EAST;
            };
            return new PieceRender.Cell(SmoothPiece.stairs(facing, false, SmoothPiece.STRAIGHT), color);
        }
        if (type.endsWith("_slab")) {
            return new PieceRender.Cell(SmoothPiece.slab(property(state, "type").equals("top")), color);
        }
        if (type.equals("glass_pane")) {
            int connections = 0;
            for (String[] side : new String[][]{{"south", "0"}, {"west", "1"}, {"north", "2"}, {"east", "3"}}) {
                if (property(state, side[0]).equals("true")) {
                    connections |= 1 << Integer.parseInt(side[1]);
                }
            }
            return new PieceRender.Cell(SmoothPiece.pane(connections), color);
        }
        if (type.endsWith("_door")) {
            return new PieceRender.Cell(SmoothPiece.pane(1 << SmoothPiece.EAST | 1 << SmoothPiece.WEST), color);
        }
        if (type.endsWith("_fence") || type.equals("lantern")) {
            return new PieceRender.Cell(SmoothPiece.wall(), color);
        }
        if (type.endsWith("_carpet")) {
            return new PieceRender.Cell(SmoothPiece.layers(1), color);
        }
        if (type.equals("wall_torch") || BlockTraits.guess(state).vegetation() && !type.endsWith("_leaves")) {
            return new PieceRender.Cell(SmoothPiece.pane(0), color);
        }
        return new PieceRender.Cell(SmoothPiece.FULL, color);
    }

    private static String property(String state, String key) {
        int start = state.indexOf(key + "=");
        if (start < 0) {
            return "";
        }
        int end = start + key.length() + 1;
        int stop = end;
        while (stop < state.length() && state.charAt(stop) != ',' && state.charAt(stop) != ']') {
            stop++;
        }
        return state.substring(end, stop);
    }

    /** El fotograma con una franja abajo: fase, grupo, avance y tiempo. */
    private static BufferedImage captioned(BufferedImage image, RebuildFixtures.Rig rig, int tick) {
        BufferedImage framed = new BufferedImage(image.getWidth(), image.getHeight() + 26, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = framed.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.setColor(new Color(0x2B2F36));
        graphics.fillRect(0, image.getHeight(), framed.getWidth(), 26);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        int percent = (int) (100L * rig.run.step() / Math.max(1, rig.run.sequence().size()));
        String phase = rig.run.done() ? "terminada"
                : rig.run.phase() == RebuildRun.Phase.BUILDING && rig.run.step() == 0 ? "espera con la obra vacía"
                : rig.run.groupLabel();
        String text = String.format(Locale.ROOT, "como un constructor · %s · %d %% · %.1f s", phase, percent,
                tick / 20.0).replace('.', ',');
        graphics.setColor(new Color(0xF2F2F2));
        graphics.drawString(text, 8, image.getHeight() + 18);
        // La barra de avance, fina, en el borde de arriba de la franja.
        graphics.setColor(new Color(0x6CC24A));
        graphics.fillRect(0, image.getHeight(), framed.getWidth() * percent / 100, 3);
        graphics.dispose();
        return framed;
    }

    /** GIF en bucle; {@code delays} en centésimas de segundo. */
    private static void gif(List<BufferedImage> frames, List<Integer> delays, Path file) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersBySuffix("gif").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int index = 0; index < frames.size(); index++) {
                BufferedImage frame = frames.get(index);
                IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame),
                        null);
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = child(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", Integer.toString(delays.get(index)));
                control.setAttribute("transparentColorIndex", "0");
                if (index == 0) {
                    IIOMetadataNode extensions = child(root, "ApplicationExtensions");
                    IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0});
                    extensions.appendChild(loop);
                }
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame, null, metadata), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) node;
            }
        }
        IIOMetadataNode created = new IIOMetadataNode(name);
        root.appendChild(created);
        return created;
    }
}
