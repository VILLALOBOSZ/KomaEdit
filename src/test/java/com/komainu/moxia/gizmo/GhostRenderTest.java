package com.komainu.moxia.gizmo;

import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.preview.PreviewDisplays;
import com.komainu.moxia.preview.PreviewOutline;
import com.komainu.moxia.shape.Vec3;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders de comprobación del fantasma con gizmo (solo con {@code KOMAEDIT_GIZMO_RENDER} apuntando a una carpeta; con
 * {@code KOMAEDIT_MC_JAR}, con las texturas del juego). Usan el mismo código que el cliente: la transformación, los
 * estados girados ({@link GhostPlacement}), el contorno de la vista previa ({@link PreviewOutline}) y la geometría del
 * gizmo ({@link GizmoGeometry}) con su tamaño de pantalla ({@link GizmoLayout#around}).
 */
class GhostRenderTest {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 760;
    private static final double FOV = 70.0;
    private static final int GHOST_TINT = 0xD9EBFF;
    private static final int OUTLINE = 0xF27FD7FF;
    private static final int BOX = 0xC07FD7FF;

    @Test
    void ghostWithGizmo() throws IOException {
        String output = System.getenv("KOMAEDIT_GIZMO_RENDER");
        Assumptions.assumeTrue(output != null && !output.isBlank(), "KOMAEDIT_GIZMO_RENDER sin definir");
        Path folder = Path.of(output);
        Files.createDirectories(folder);
        FakeStates states = new FakeStates();
        Long2IntOpenHashMap world = terrain(states);
        GhostModel shrine = shrine(states);
        GhostModel totem = totem(states);
        Vec3 eye = new Vec3(-6.5, 73.0, 12.5);
        GhostTransform start = shrine.start();
        List<String> report = new ArrayList<>();

        render(folder, "1_al_aparecer", states, world, shrine, start, eye, GizmoHandle.NONE, GizmoHandle.NONE,
                GhostOptions.DEFAULT, report);
        render(folder, "2_flecha_x_resaltada", states, world, shrine, start, eye, GizmoHandle.MOVE_X,
                GizmoHandle.NONE, GhostOptions.DEFAULT, report);
        render(folder, "3_arrastrando_x", states, world, shrine, start.moved(6, 0, 0), eye.add(new Vec3(3, 0, 0)),
                GizmoHandle.MOVE_X, GizmoHandle.MOVE_X, GhostOptions.DEFAULT, report);
        render(folder, "4_anillo_y_girado_90", states, world, shrine, start.turned(Orientation.Y, 1), eye,
                GizmoHandle.RING_Y, GizmoHandle.RING_Y, GhostOptions.DEFAULT, report);
        render(folder, "5_volteado_en_z", states, world, shrine, start.mirrored(Orientation.Z), eye, GizmoHandle.NONE,
                GizmoHandle.NONE, GhostOptions.DEFAULT, report);
        Vec3 pivot = new Vec3(start.x() + 0.5, start.y() + 0.5, start.z() + 0.5);
        Vec3 far = pivot.add(eye.subtract(pivot).multiply(3.0));
        render(folder, "6_lejos_mismo_tamano", states, world, shrine, start, far, GizmoHandle.NONE, GizmoHandle.NONE,
                GhostOptions.DEFAULT, report);
        GhostTransform tipped = totem.start().moved(-2, 0, 0).turned(Orientation.X, 1);
        render(folder, "7_tumbado_anillos_xyz", states, world, totem, tipped, new Vec3(-9.5, 72.0, 9.5),
                GizmoHandle.RING_X, GizmoHandle.NONE, GhostOptions.DEFAULT, report);
        // Sobre la loma de piedra, apoyado en el suelo: la columna más alta manda.
        GhostTransform hill = start.moved(10, 4, -10);
        GroundSnap.Footprint footprint = GroundSnap.footprint(GhostPlacement.orientPositions(shrine.positions(),
                hill.orientation()));
        OptionalInt rest = GroundSnap.rest(footprint, hill, (x, y, z) -> {
            int state = world.get(PackedBlockPlan.pack(x, y, z));
            return state != 0 && world.containsKey(PackedBlockPlan.pack(x, y, z));
        });
        assertTrue(rest.isPresent());
        render(folder, "8_apoyado_en_la_loma", states, world, shrine, hill.moved(0, rest.getAsInt(), 0),
                new Vec3(1.5, 77.0, 3.5), GizmoHandle.MOVE_PLANE, GizmoHandle.NONE,
                GhostOptions.DEFAULT.withGround(true), report);
        report.add("Texturas de Minecraft: " + (System.getenv("KOMAEDIT_MC_JAR") == null ? "no" : "sí"));
        Files.writeString(folder.resolve("gizmo.txt"), String.join("\n", report) + "\n");
    }

    private static void render(Path folder, String name, FakeStates states, Long2IntOpenHashMap world,
                               GhostModel model, GhostTransform transform, Vec3 eye, GizmoHandle hovered,
                               GizmoHandle dragged, GhostOptions options, List<String> report) throws IOException {
        Vec3 pivot = new Vec3(transform.x() + 0.5, transform.y() + 0.5, transform.z() + 0.5);
        Vec3 look = pivot.subtract(eye);
        double yaw = Math.toDegrees(Math.atan2(-look.x(), look.z()));
        double pitch = Math.toDegrees(-Math.asin(look.y() / look.length()));
        try (PerspectiveRaster raster = new PerspectiveRaster(WIDTH, HEIGHT, eye, yaw, pitch, FOV)) {
            // El mundo.
            for (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry entry : world.long2IntEntrySet()) {
                long packed = entry.getLongKey();
                block(raster, states, entry.getIntValue(), PackedBlockPlan.unpackX(packed),
                        PackedBlockPlan.unpackY(packed), PackedBlockPlan.unpackZ(packed),
                        exposed(world.keySet(), packed), 0xFFFFFF, 1.0, null);
            }
            // La caja del objeto, con prueba de profundidad contra el mundo.
            BlockBounds bounds = transform.bounds(model.bounds());
            box(raster, bounds);
            // El fantasma: el mismo cálculo que el cliente y que el servidor al confirmar.
            GhostPlacement.Blocks shown = GhostPlacement.shown(model, transform, states);
            LongOpenHashSet occupied = new LongOpenHashSet(shown.positions());
            double[] layer = raster.layer();
            for (double pass : new double[]{-1.0, 0.5}) {
                for (int index = 0; index < shown.size(); index++) {
                    long packed = shown.positions()[index];
                    block(raster, states, shown.states()[index], PackedBlockPlan.unpackX(packed),
                            PackedBlockPlan.unpackY(packed), PackedBlockPlan.unpackZ(packed),
                            exposed(occupied, packed), GHOST_TINT, pass, layer);
                }
                if (pass < 0) {
                    double[] copy = layer.clone();
                    raster.mergeDepth(copy);
                }
            }
            outline(raster, shown, occupied);
            dimensions(raster, bounds, eye);
            // El gizmo, encima de todo y del mismo tamaño en pantalla a cualquier distancia.
            boolean tipX = admits(states, model, transform.turned(Orientation.X, 1));
            boolean tipZ = admits(states, model, transform.turned(Orientation.Z, 1));
            GizmoLayout around = GizmoLayout.around(transform.x(), transform.y(), transform.z(), eye, FOV, false);
            GizmoLayout layout = new GizmoLayout(around.center(), around.size(), tipX, true, tipZ);
            raster.gizmo(GizmoGeometry.build(layout, hovered, dragged), 1.0);
            raster.actionBar(hud(model, transform, options));
            raster.write(folder.resolve("gizmo_" + name + ".png"));
            double pixels = around.size() / eye.distance(around.center()) * (HEIGHT / 2.0)
                    / Math.tan(Math.toRadians(FOV / 2.0));
            report.add(String.format(Locale.ROOT, "%s: %s · flecha de %.2f bloques a %.1f de la cámara (≈ %.0f px)",
                    name, hud(model, transform, options), around.size(), eye.distance(around.center()), pixels));
        }
    }

    /** ¿Admite el objeto esa orientación? Como {@code GhostStates.admitsAll} en el juego. */
    private static boolean admits(FakeStates states, GhostModel model, GhostTransform transform) {
        for (int state : model.palette()) {
            if (!states.admits(state, transform.orientation())) {
                return false;
            }
        }
        return true;
    }

    /** La línea de la action bar, como la escribe el cliente (en español). */
    private static String hud(GhostModel model, GhostTransform transform, GhostOptions options) {
        BlockBounds oriented = transform.oriented(model.bounds());
        int dx = transform.x() - model.pivotX();
        int dy = transform.y() - model.pivotY();
        int dz = transform.z() - model.pivotZ();
        Orientation orientation = transform.orientation();
        StringBuilder rotation = new StringBuilder("giro " + orientation.yawQuarters() * 90 + "°");
        if (orientation.mirrored()) {
            rotation.append(" volteado");
        }
        if (orientation.upsideDown()) {
            rotation.append(" boca abajo");
        } else if (!orientation.upright()) {
            rotation.append(" tumbado");
        }
        String moved = dx == 0 && dy == 0 && dz == 0 ? "" : " (" + signed(dx) + ", " + signed(dy) + ", " + signed(dz)
                + ")";
        return "KE · " + transform.x() + ", " + transform.y() + ", " + transform.z() + moved + " · " + rotation
                + " · " + (oriented.maximumX() - oriented.minimumX() + 1) + "×"
                + (oriented.maximumY() - oriented.minimumY() + 1) + "×"
                + (oriented.maximumZ() - oriented.minimumZ() + 1)
                + (options.ground() ? " · Apoyar en el suelo" : "")
                + " · Intro o clic derecho coloca · Ctrl+clic: copia · Esc cancela";
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    // ---------------------------------------------------------------- escena

    private static Long2IntOpenHashMap terrain(FakeStates states) {
        Long2IntOpenHashMap world = new Long2IntOpenHashMap();
        int grass = states.state("grass_block");
        int dirt = states.state("dirt");
        int stone = states.state("stone");
        for (int x = -16; x <= 26; x++) {
            for (int z = -22; z <= 16; z++) {
                world.put(PackedBlockPlan.pack(x, 63, z), grass);
                world.put(PackedBlockPlan.pack(x, 62, z), dirt);
            }
        }
        // Una loma de piedra al noreste.
        for (int x = 8; x <= 16; x++) {
            for (int z = -12; z <= -4; z++) {
                int height = 3 - Math.max(Math.abs(x - 12), Math.abs(z + 8)) / 2;
                for (int y = 64; y < 64 + height; y++) {
                    world.put(PackedBlockPlan.pack(x, y, z), stone);
                }
            }
        }
        return world;
    }

    /** Un templete: suelo de ladrillo, cuatro troncos, tejado de tablas, escalones al sur y un dispensador. */
    private static GhostModel shrine(FakeStates states) {
        GhostModel.Builder builder = GhostModel.builder(128);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                builder.add(x, 64, z, states.state("stone_bricks"), false, PackedBlockPlan.NO_CAPTURE);
                builder.add(x, 68, z, states.state("oak_planks"), false, PackedBlockPlan.NO_CAPTURE);
                if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
                    for (int y = 65; y <= 67; y++) {
                        builder.add(x, y, z, states.state("oak_log", "axis", "y"), false, PackedBlockPlan.NO_CAPTURE);
                    }
                }
            }
        }
        for (int x = 1; x <= 3; x++) {
            builder.add(x, 64, 5, states.state("oak_stairs", "facing", "north", "half", "bottom"), false,
                    PackedBlockPlan.NO_CAPTURE);
        }
        builder.add(2, 69, 2, states.state("dispenser", "facing", "south"), false, PackedBlockPlan.NO_CAPTURE);
        return builder.build();
    }

    /** Una cruz que se puede tumbar: troncos, brazos en X, pilar de cuarzo, dispensador y seta. */
    private static GhostModel totem(FakeStates states) {
        GhostModel.Builder builder = GhostModel.builder(32);
        for (int y = 64; y <= 69; y++) {
            builder.add(0, y, 0, states.state(y == 66 ? "quartz_pillar" : "oak_log", "axis", "y"), false,
                    PackedBlockPlan.NO_CAPTURE);
        }
        for (int x = -2; x <= 2; x++) {
            if (x != 0) {
                builder.add(x, 68, 0, states.state("oak_log", "axis", "x"), false, PackedBlockPlan.NO_CAPTURE);
            }
        }
        builder.add(0, 70, 0, states.state("dispenser", "facing", "up"), false, PackedBlockPlan.NO_CAPTURE);
        builder.add(0, 71, 0, states.state("brown_mushroom_block", "up", "true", "down", "false", "north", "false",
                "south", "false", "west", "false", "east", "false"), false, PackedBlockPlan.NO_CAPTURE);
        return builder.build();
    }

    // ---------------------------------------------------------------- bloques

    private static int exposed(it.unimi.dsi.fastutil.longs.LongSet occupied, long packed) {
        int x = PackedBlockPlan.unpackX(packed);
        int y = PackedBlockPlan.unpackY(packed);
        int z = PackedBlockPlan.unpackZ(packed);
        int[][] offsets = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
        int mask = 0;
        for (int face = 0; face < 6; face++) {
            if (!occupied.contains(PackedBlockPlan.pack(x + offsets[face][0], y + offsets[face][1],
                    z + offsets[face][2]))) {
                mask |= 1 << face;
            }
        }
        return mask;
    }

    /** Un bloque con su forma (las escaleras, dos cajas) y sus texturas según su estado. */
    private static void block(PerspectiveRaster raster, FakeStates states, int state, int x, int y, int z, int mask,
                              int tint, double alpha, double[] layer) {
        String block = states.block(state);
        if (block.equals("oak_stairs")) {
            int[] planks = raster.texture("oak_planks", 0xB8945F, tint);
            boolean top = states.value(state, "half").equals("top");
            double lowY0 = top ? y + 0.5 : y;
            box(raster, x, lowY0, z, x + 1, lowY0 + 0.5, z + 1, planks, alpha, layer, 0b111111);
            double[] step = switch (states.value(state, "facing")) {
                case "north" -> new double[]{x, z, x + 1, z + 0.5};
                case "south" -> new double[]{x, z + 0.5, x + 1, z + 1};
                case "west" -> new double[]{x, z, x + 0.5, z + 1};
                default -> new double[]{x + 0.5, z, x + 1, z + 1};
            };
            double highY0 = top ? y : y + 0.5;
            box(raster, step[0], highY0, step[1], step[2], highY0 + 0.5, step[3], planks, alpha, layer, 0b111111);
            return;
        }
        for (int face = 0; face < 6; face++) {
            if ((mask & 1 << face) == 0) {
                continue;
            }
            int[] texture = texture(raster, states, state, face, tint);
            raster.face(x, y, z, x + 1, y + 1, z + 1, face, texture, PerspectiveRaster.SHADE[face], alpha, layer);
        }
    }

    private static void box(PerspectiveRaster raster, double x0, double y0, double z0, double x1, double y1,
                            double z1, int[] texture, double alpha, double[] layer, int mask) {
        for (int face = 0; face < 6; face++) {
            if ((mask & 1 << face) != 0) {
                raster.face(x0, y0, z0, x1, y1, z1, face, texture, PerspectiveRaster.SHADE[face], alpha, layer);
            }
        }
    }

    private static final String[] SIDES = {"west", "east", "down", "up", "north", "south"};

    private static int[] texture(PerspectiveRaster raster, FakeStates states, int state, int face, int tint) {
        String block = states.block(state);
        int axisOfFace = face >> 1;
        return switch (block) {
            case "grass_block" -> face == 3 ? raster.texture("grass_block_top", 0x7DB04E, multiply(0x7DB860, tint))
                    : face == 2 ? raster.texture("dirt", 0x866043, tint)
                    : raster.texture("grass_block_side", 0x866043, tint);
            case "oak_log", "quartz_pillar" -> {
                int axis = states.value(state, "axis").charAt(0) - 'x';
                boolean end = axis == axisOfFace;
                String name = block.equals("oak_log") ? end ? "oak_log_top" : "oak_log"
                        : end ? "quartz_pillar_top" : "quartz_pillar";
                yield raster.texture(name, block.equals("oak_log") ? end ? 0xB08A55 : 0x6B5131 : 0xEAE4DC, tint);
            }
            case "dispenser" -> {
                String facing = states.value(state, "facing");
                boolean front = SIDES[face].equals(facing);
                boolean vertical = facing.equals("up") || facing.equals("down");
                String name = front ? vertical ? "dispenser_front_vertical" : "dispenser_front"
                        : !vertical && (face == 2 || face == 3) ? "furnace_top" : "furnace_side";
                yield raster.texture(name, front ? 0x4A4A4A : 0x7A7A7A, tint);
            }
            case "brown_mushroom_block" -> states.value(state, SIDES[face]).equals("true")
                    ? raster.texture("brown_mushroom_block", 0x95704F, tint)
                    : raster.texture("mushroom_block_inside", 0xCAAB78, tint);
            case "stone_bricks" -> raster.texture("stone_bricks", 0x7A7A7A, tint);
            case "oak_planks" -> raster.texture("oak_planks", 0xB8945F, tint);
            case "dirt" -> raster.texture("dirt", 0x866043, tint);
            default -> raster.texture("stone", 0x7D7D7D, tint);
        };
    }

    private static int multiply(int first, int second) {
        int r = (first >> 16 & 0xFF) * (second >> 16 & 0xFF) / 255;
        int g = (first >> 8 & 0xFF) * (second >> 8 & 0xFF) / 255;
        int b = (first & 0xFF) * (second & 0xFF) / 255;
        return r << 16 | g << 8 | b;
    }

    // ---------------------------------------------------------------- contorno, caja y medidas

    private static void outline(PerspectiveRaster raster, GhostPlacement.Blocks shown, LongOpenHashSet occupied) {
        byte[] kinds = new byte[shown.size()];
        byte[] faces = new byte[shown.size()];
        for (int index = 0; index < shown.size(); index++) {
            kinds[index] = (byte) PreviewDisplays.Kind.REPLACE.ordinal();
            faces[index] = (byte) exposed(occupied, shown.positions()[index]);
        }
        for (PreviewOutline.Section section : PreviewOutline.build(shown.positions(), kinds, faces)) {
            for (int edge : section.edges()) {
                double x = section.x() * 16 + PreviewOutline.localX(edge);
                double y = section.y() * 16 + PreviewOutline.localY(edge);
                double z = section.z() * 16 + PreviewOutline.localZ(edge);
                int axis = PreviewOutline.axis(edge);
                int length = PreviewOutline.length(edge);
                Vec3 from = new Vec3(x, y, z);
                Vec3 to = from.add(GizmoMath.unit(axis).multiply(length));
                raster.depthLine(from, to, OUTLINE, 2.0);
            }
        }
    }

    private static void box(PerspectiveRaster raster, BlockBounds bounds) {
        double x0 = bounds.minimumX() - 0.02;
        double y0 = bounds.minimumY() - 0.02;
        double z0 = bounds.minimumZ() - 0.02;
        double x1 = bounds.maximumX() + 1.02;
        double y1 = bounds.maximumY() + 1.02;
        double z1 = bounds.maximumZ() + 1.02;
        double[][] corners = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0},
                {x1, y1, z1}, {x0, y1, z1}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6},
                {3, 7}};
        for (int[] edge : edges) {
            double[] a = corners[edge[0]];
            double[] b = corners[edge[1]];
            raster.depthLine(new Vec3(a[0], a[1], a[2]), new Vec3(b[0], b[1], b[2]), BOX, 1.6);
        }
    }

    /** Las medidas en las tres aristas más cercanas, como {@code WorldLabels.dimensions}. */
    private static void dimensions(PerspectiveRaster raster, BlockBounds bounds, Vec3 eye) {
        double minX = bounds.minimumX();
        double minY = bounds.minimumY();
        double minZ = bounds.minimumZ();
        double maxX = bounds.maximumX() + 1.0;
        double maxY = bounds.maximumY() + 1.0;
        double maxZ = bounds.maximumZ() + 1.0;
        double nearX = Math.abs(eye.x() - minX) < Math.abs(eye.x() - maxX) ? minX : maxX;
        double nearY = Math.abs(eye.y() - minY) < Math.abs(eye.y() - maxY) ? minY : maxY;
        double nearZ = Math.abs(eye.z() - minZ) < Math.abs(eye.z() - maxZ) ? minZ : maxZ;
        int color = BOX & 0xFFFFFF;
        raster.label(Integer.toString((int) (maxX - minX)), new Vec3((minX + maxX) / 2.0, nearY, nearZ), color);
        raster.label(Integer.toString((int) (maxY - minY)), new Vec3(nearX, (minY + maxY) / 2.0, nearZ), color);
        raster.label(Integer.toString((int) (maxZ - minZ)), new Vec3(nearX, nearY, (minZ + maxZ) / 2.0), color);
    }
}
