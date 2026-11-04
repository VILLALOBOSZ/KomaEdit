package com.komainu.moxia.gizmo;

import com.komainu.moxia.placement.Mat3;
import com.komainu.moxia.placement.PlacementStates;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Antes y después del giro de estados en {@code place}/{@code scatter}/{@code array}: un circuito de raíles colocado
 * con un cuarto de vuelta y con un volteo, visto desde arriba. Antes ({@code BlockTransformExtent}) la forma de cada
 * raíl se quedaba como estaba y el circuito salía roto; ahora va por {@link OrientedStates}. Solo con
 * {@code KOMAEDIT_PLACEMENT_RENDER=<carpeta>}.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_PLACEMENT_RENDER", matches = ".+")
class PlacementStatesRenderTest {
    private static final int CELL = 26;
    private static final List<String> RAIL = List.of("north_south", "east_west", "ascending_east", "ascending_west",
            "ascending_north", "ascending_south", "south_east", "south_west", "north_west", "north_east");

    @Test
    void railLoopTurnedAndFlipped() throws IOException {
        // Circuito de 7 × 5 con una rama: x hacia el este, z hacia el sur.
        Map<int[], String> loop = new LinkedHashMap<>();
        int width = 7;
        int depth = 5;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                boolean edgeX = x == 0 || x == width - 1;
                boolean edgeZ = z == 0 || z == depth - 1;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                String shape;
                if (edgeX && edgeZ) {
                    shape = (z == 0 ? "south" : "north") + "_" + (x == 0 ? "east" : "west");
                } else {
                    shape = edgeX ? "north_south" : "east_west";
                }
                loop.put(new int[]{x, z}, shape);
            }
        }
        Mat3[] matrices = {Mat3.rotation(new Vec3(0, 1, 0), 90), Mat3.diagonal(-1, 1, 1)};
        String[] names = {"giro de 90°", "volteo en X"};
        int panel = 9 * CELL;
        BufferedImage image = new BufferedImage(panel * 2 + 30, panel * 2 + 70, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(0x1E1F22));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        graphics.setColor(Color.WHITE);
        graphics.drawString("Antes (BlockTransformExtent)", 10, 20);
        graphics.drawString("Después (OrientedStates)", panel + 20, 20);
        for (int row = 0; row < 2; row++) {
            Orientation orientation = PlacementStates.exact(matrices[row]);
            Map<String, List<String>> allowed = Map.of("shape", RAIL);
            for (int column = 0; column < 2; column++) {
                Map<Long, String> placed = new LinkedHashMap<>();
                for (Map.Entry<int[], String> rail : loop.entrySet()) {
                    Vec3 world = matrices[row].apply(rail.getKey()[0] - 3, 0, rail.getKey()[1] - 2);
                    String shape = rail.getValue();
                    if (column == 1) {
                        Map<String, String> state = new LinkedHashMap<>();
                        state.put("shape", shape);
                        shape = OrientedStates.apply(state, allowed, orientation).get("shape");
                    }
                    placed.put(key((int) Math.round(world.x()), (int) Math.round(world.z())), shape);
                }
                int left = 10 + column * (panel + 10);
                int top = 32 + row * (panel + 20);
                draw(graphics, placed, left, top);
                graphics.setColor(new Color(0xB8BCC4));
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                graphics.drawString(names[row] + " · " + broken(placed) + " raíles sueltos", left, top + panel + 14);
            }
        }
        graphics.dispose();
        Path folder = Path.of(System.getenv("KOMAEDIT_PLACEMENT_RENDER"));
        Files.createDirectories(folder);
        ImageIO.write(image, "png", folder.resolve("rieles_girados_antes_despues.png").toFile());
    }

    private static void draw(Graphics2D graphics, Map<Long, String> placed, int left, int top) {
        graphics.setColor(new Color(0x2B2D31));
        graphics.fillRect(left, top, 9 * CELL, 9 * CELL);
        for (Map.Entry<Long, String> rail : placed.entrySet()) {
            int x = (int) (rail.getKey() >> 32);
            int z = (int) (long) rail.getKey();
            int centerX = left + (x + 4) * CELL + CELL / 2;
            int centerZ = top + (z + 4) * CELL + CELL / 2;
            boolean connected = connected(placed, x, z);
            graphics.setColor(new Color(0x3A3C42));
            graphics.fillRect(centerX - CELL / 2 + 1, centerZ - CELL / 2 + 1, CELL - 2, CELL - 2);
            graphics.setColor(connected ? new Color(0x7FD08A) : new Color(0xE0605A));
            graphics.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int[] end : ends(rail.getValue())) {
                graphics.drawLine(centerX, centerZ, centerX + end[0] * CELL / 2, centerZ + end[1] * CELL / 2);
            }
        }
    }

    private static int broken(Map<Long, String> placed) {
        int count = 0;
        for (Long key : placed.keySet()) {
            if (!connected(placed, (int) (key >> 32), (int) (long) key)) {
                count++;
            }
        }
        return count;
    }

    /** ¿Lleva cada extremo a un raíl vecino que también apunta aquí? */
    private static boolean connected(Map<Long, String> placed, int x, int z) {
        for (int[] end : ends(placed.get(key(x, z)))) {
            String neighbour = placed.get(key(x + end[0], z + end[1]));
            if (neighbour == null) {
                return false;
            }
            boolean back = false;
            for (int[] other : ends(neighbour)) {
                back |= other[0] == -end[0] && other[1] == -end[1];
            }
            if (!back) {
                return false;
            }
        }
        return true;
    }

    private static int[][] ends(String shape) {
        String flat = shape.replace("ascending_east", "east_west").replace("ascending_west", "east_west")
                .replace("ascending_north", "north_south").replace("ascending_south", "north_south");
        String[] parts = flat.split("_");
        return new int[][]{offset(parts[0]), offset(parts[1])};
    }

    private static int[] offset(String direction) {
        return switch (direction) {
            case "north" -> new int[]{0, -1};
            case "south" -> new int[]{0, 1};
            case "west" -> new int[]{-1, 0};
            default -> new int[]{1, 0};
        };
    }

    private static long key(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }
}
