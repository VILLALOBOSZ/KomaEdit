package com.komainu.moxia.gizmo;

import com.komainu.moxia.display.Affine;
import com.komainu.moxia.display.DisplayDrag;
import com.komainu.moxia.display.DisplayEditorSettings;
import com.komainu.moxia.display.DisplayGizmo;
import com.komainu.moxia.display.DisplayPick;
import com.komainu.moxia.display.DisplayPose;
import com.komainu.moxia.display.DisplaySteps;
import com.komainu.moxia.display.DisplayVolume;
import com.komainu.moxia.display.Quat;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.studio.ui.FakeStudioHost;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders del editor de displays (solo con {@code KOMAEDIT_DISPLAY_RENDER} apuntando a una carpeta; con
 * {@code KOMAEDIT_MC_JAR}, con las texturas del juego): una mesa con un tiesto, una flor, un libro y un cartel, el
 * contorno de lo señalado y lo elegido, y el gizmo de mover, girar y escalar en global y en local. Usan el mismo código
 * que el cliente: el volumen ({@link DisplayVolume}), el rayo ({@link DisplayPick}), el arrastre ({@link DisplayDrag})
 * y la geometría del gizmo ({@link DisplayGizmo}) con su tamaño de pantalla. Las poses de cada render salen de
 * arrastres con rayos de verdad, y se comprueban.
 */
class DisplayEditorRenderTest {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 760;
    private static final double FOV = 70.0;
    private static final int SELECTED = 0xE05FD7FF;
    private static final int PRIMARY = 0xFFB8F0FF;
    private static final int HOVER = 0xFFFFD23F;
    private static final Vec3 HALF = new Vec3(0.5, 0.5, 0.5);
    private static final Vec3 TABLE = new Vec3(0.95, 65.2, 0.45);

    /** Un display de la escena: qué textura lleva y su volumen. */
    private record Prop(int id, String texture, int fallback, DisplayVolume volume, DisplayPose pose, String text) {
        Affine unit() {
            return volume.world(pose);
        }

        Prop with(DisplayPose value) {
            return new Prop(id, texture, fallback, volume, value, text);
        }

        DisplayDrag.Item item() {
            return new DisplayDrag.Item(id, pose, volume);
        }
    }

    private static final int POT = 1;
    private static final int POPPY = 2;
    private static final int BOOK = 3;
    private static final int SIGN = 4;

    @Test
    void displayEditor() throws IOException {
        String output = System.getenv("KOMAEDIT_DISPLAY_RENDER");
        Assumptions.assumeTrue(output != null && !output.isBlank(), "KOMAEDIT_DISPLAY_RENDER sin definir");
        Path folder = Path.of(output);
        Files.createDirectories(folder);
        Map<String, String> es = FakeStudioHost.load("es_es");
        List<Prop> props = props();
        Prop pot = find(props, POT);
        Vec3 eye = new Vec3(-1.1, 66.35, 2.7);
        List<String> report = new ArrayList<>();

        // 1. Señalar con el punto de mira: el tiesto no tiene caja de colisión, el rayo va contra su volumen.
        GizmoMath.Ray crosshair = new GizmoMath.Ray(eye, pot.pose().world(HALF).subtract(eye));
        Optional<DisplayPick.Hit> hit = DisplayPick.pick(crosshair, candidates(props), 64);
        assertEquals(POT, hit.orElseThrow().id(), "el punto de mira elige el tiesto");
        render(folder, "1_senalar_sin_caja", es, props, List.of(), POT, null, DisplayGizmo.Handle.NONE, null, eye,
                hud(es, List.of(), DisplayEditorSettings.DEFAULT, null), report);

        // 2. Elegido, gizmo de mover en global, la flecha X bajo el puntero.
        DisplayEditorSettings settings = DisplayEditorSettings.DEFAULT;
        DisplayGizmo move = gizmo(pot, eye, settings);
        Vec3 onArrow = pot.pose().world(HALF).add(new Vec3(move.size() * 0.6, 0, 0));
        GizmoMath.Ray atArrow = new GizmoMath.Ray(eye, onArrow.subtract(eye));
        assertEquals(DisplayGizmo.Handle.X, move.pick(atArrow));
        render(folder, "2_mover_flecha_x", es, props, List.of(POT), -1, move, DisplayGizmo.Handle.X, null, eye,
                hud(es, List.of(POT), settings, null), report);

        // 3. Arrastrando la flecha X 0.3 bloques: la esquina cae en la rejilla de 1/16.
        DisplayDrag drag = DisplayDrag.begin(move, DisplayGizmo.Handle.X, atArrow, List.of(pot.item()), 0,
                settings.steps(), true);
        assertNotNull(drag);
        Vec3 further = onArrow.add(new Vec3(0.3, 0, 0));
        drag.update(new GizmoMath.Ray(eye, further.subtract(eye)), false);
        DisplayDrag.Item moved = drag.current().getFirst();
        double corner = moved.minimum().x() * 16.0;
        assertEquals(Math.rint(corner), corner, 1.0E-9, "la esquina en un dieciseisavo");
        List<Prop> afterMove = replace(props, moved);
        render(folder, "3_mover_arrastrando", es, afterMove, List.of(POT), -1, gizmo(find(afterMove, POT), eye,
                settings), DisplayGizmo.Handle.X, drag, eye, hud(es, List.of(POT), settings, drag), report);

        // 4. Girar el libro con el anillo Y: 50° de ratón son 45° con el paso de 15°.
        Prop book = find(props, BOOK);
        DisplayEditorSettings rotate = settings.withTool(DisplayGizmo.Tool.ROTATE);
        DisplayGizmo rings = gizmo(book, eye, rotate);
        Vec3 bookCenter = book.pose().world(book.volume().center());
        double radius = 0.62 * rings.size();
        // Se agarra a 20° (no en +Z, donde se cruza con el anillo X) y se lleva a 70°.
        Vec3 ringStart = onRing(bookCenter, rings, radius, 20);
        GizmoMath.Ray atRing = new GizmoMath.Ray(eye, ringStart.subtract(eye));
        assertEquals(DisplayGizmo.Handle.Y, rings.pick(atRing));
        DisplayDrag turn = DisplayDrag.begin(rings, DisplayGizmo.Handle.Y, atRing, List.of(book.item()), 0,
                rotate.steps(), true);
        assertNotNull(turn);
        turn.update(new GizmoMath.Ray(eye, onRing(bookCenter, rings, radius, 70).subtract(eye)), false);
        assertEquals(45.0, Math.toDegrees(turn.turned()), 1.0E-6);
        List<Prop> afterTurn = replace(props, turn.current().getFirst());
        render(folder, "4_girar_anillo_y", es, afterTurn, List.of(BOOK), -1, rings, DisplayGizmo.Handle.Y, turn, eye,
                hud(es, List.of(BOOK), rotate, turn), report);

        // 5. En local, el gizmo sigue los ejes del libro girado.
        DisplayEditorSettings local = settings.withLocal(true);
        Prop turnedBook = find(afterTurn, BOOK);
        DisplayGizmo localMove = gizmo(turnedBook, eye, local);
        assertTrue(localMove.frame().angleTo(Quat.IDENTITY) > Math.toRadians(40));
        render(folder, "5_mover_en_local", es, afterTurn, List.of(BOOK), -1, localMove, DisplayGizmo.Handle.Z, null,
                eye, hud(es, List.of(BOOK), local, null), report);

        // 6. Escalar el tiesto en Y con su cubo: a la mitad de alto, sobre su centro.
        DisplayEditorSettings scale = settings.withTool(DisplayGizmo.Tool.SCALE);
        DisplayGizmo cubes = gizmo(pot, eye, scale);
        Vec3 potCenter = pot.pose().world(HALF);
        Vec3 tip = potCenter.add(cubes.axis(1).multiply(cubes.size()));
        GizmoMath.Ray atCube = new GizmoMath.Ray(eye, tip.subtract(eye));
        assertEquals(DisplayGizmo.Handle.Y, cubes.pick(atCube));
        DisplayDrag stretch = DisplayDrag.begin(cubes, DisplayGizmo.Handle.Y, atCube, List.of(pot.item()), 0,
                scale.steps(), true);
        assertNotNull(stretch);
        Vec3 higher = potCenter.add(cubes.axis(1).multiply(cubes.size() * 1.6));
        stretch.update(new GizmoMath.Ray(eye, higher.subtract(eye)), false);
        DisplayDrag.Item tall = stretch.current().getFirst();
        assertTrue(tall.pose().scale().y() > pot.pose().scale().y());
        assertEquals(0.0, tall.center().distance(potCenter), 1.0E-9, "escala sobre su centro");
        render(folder, "6_escalar_eje_y", es, replace(props, tall), List.of(POT), -1,
                gizmo(find(replace(props, tall), POT), eye, scale), DisplayGizmo.Handle.Y, stretch, eye,
                hud(es, List.of(POT), scale, stretch), report);

        // 7. El tiesto y la flor agrupados: giran juntos alrededor del tiesto (el activo).
        Prop poppy = find(props, POPPY);
        DisplayGizmo group = gizmo(pot, eye, rotate);
        double groupRadius = 0.62 * group.size();
        GizmoMath.Ray atGroupRing = new GizmoMath.Ray(eye, onRing(potCenter, group, groupRadius, 20)
                .subtract(eye));
        assertEquals(DisplayGizmo.Handle.Y, group.pick(atGroupRing));
        DisplayDrag together = DisplayDrag.begin(group, DisplayGizmo.Handle.Y, atGroupRing,
                List.of(pot.item(), poppy.item()), 0, rotate.steps(), true);
        assertNotNull(together);
        for (int degrees = 40; degrees <= 110; degrees += 10) {
            together.update(new GizmoMath.Ray(eye, onRing(potCenter, group, groupRadius, degrees).subtract(eye)),
                    false);
        }
        assertEquals(90.0, Math.toDegrees(together.turned()), 1.0E-6);
        List<Prop> afterGroup = props;
        for (DisplayDrag.Item item : together.current()) {
            afterGroup = replace(afterGroup, item);
        }
        render(folder, "7_grupo_tiesto_y_flor", es, afterGroup, List.of(POT, POPPY), -1, group,
                DisplayGizmo.Handle.Y, together, eye, hud(es, List.of(POT, POPPY), rotate, together), report);

        // 8. El cartel de la pared, con el gizmo en local (mirando hacia fuera).
        Prop sign = find(props, SIGN);
        Vec3 signEye = new Vec3(1.9, 66.1, 1.3);
        render(folder, "8_cartel_en_la_pared", es, props, List.of(SIGN), -1, gizmo(sign, signEye, local),
                DisplayGizmo.Handle.NONE, null, signEye, hud(es, List.of(SIGN), local, null), report);

        // 9. Lejos: el gizmo ocupa lo mismo en pantalla.
        Vec3 far = potCenter.add(eye.subtract(potCenter).multiply(3.0));
        render(folder, "9_lejos_mismo_tamano", es, props, List.of(POT), -1, gizmo(pot, far, settings),
                DisplayGizmo.Handle.NONE, null, far, hud(es, List.of(POT), settings, null), report);
        report.add("Texturas de Minecraft: " + (System.getenv("KOMAEDIT_MC_JAR") == null ? "no" : "sí"));
        Files.writeString(folder.resolve("displays.txt"), String.join("\n", report) + "\n");
    }

    // ---------------------------------------------------------------- escena

    private static List<Prop> props() {
        List<Prop> props = new ArrayList<>();
        // Un tiesto de terracota de 6/16 sobre la mesa.
        props.add(new Prop(POT, "terracotta", 0xFF9C5A3C, DisplayVolume.block(),
                DisplayPose.centered(new Vec3(0.5, 65 + 0.1875, 0.5), Quat.IDENTITY, new Vec3(0.375, 0.375, 0.375),
                        HALF), null));
        // Una amapola (objeto plano) de medio bloque encima, girada 30°.
        DisplayVolume flat = DisplayVolume.item(true);
        props.add(new Prop(POPPY, "poppy", 0xFFD02020, flat, DisplayPose.centered(new Vec3(0.5, 65.375 + 0.25, 0.5),
                Quat.axis(1, Math.toRadians(30)), new Vec3(0.5, 0.5, 0.5), flat.center()), null));
        // Un libro tumbado sobre la otra mitad de la mesa, girado 20°.
        props.add(new Prop(BOOK, "red_wool", 0xFFA02020, DisplayVolume.block(),
                DisplayPose.centered(new Vec3(1.5, 65 + 0.0625, 0.45), Quat.axis(1, Math.toRadians(20)),
                        new Vec3(0.3125, 0.125, 0.4375), HALF), null));
        // Un cartel en la pared del fondo, mirando al sur.
        DisplayVolume text = DisplayVolume.text(34, 1);
        props.add(new Prop(SIGN, "black_concrete", 0xFF2A2A2A, text, DisplayPose.centered(
                new Vec3(1.0, 65.6, -1 + 1.0 / 64.0), Quat.IDENTITY, new Vec3(1.5, 1.5, 1.5), text.center()),
                "Tienda"));
        return props;
    }

    /** El punto del anillo Y a {@code degrees} grados de +Z hacia +X. */
    private static Vec3 onRing(Vec3 center, DisplayGizmo gizmo, double radius, double degrees) {
        double angle = Math.toRadians(degrees);
        return center.add(gizmo.axis(2).multiply(Math.cos(angle) * radius))
                .add(gizmo.axis(0).multiply(Math.sin(angle) * radius));
    }

    private static Prop find(List<Prop> props, int id) {
        return props.stream().filter(prop -> prop.id() == id).findFirst().orElseThrow();
    }

    private static List<Prop> replace(List<Prop> props, DisplayDrag.Item item) {
        List<Prop> result = new ArrayList<>();
        for (Prop prop : props) {
            result.add(prop.id() == item.id() ? prop.with(item.pose()) : prop);
        }
        return result;
    }

    private static List<DisplayPick.Candidate> candidates(List<Prop> props) {
        return props.stream().map(prop -> new DisplayPick.Candidate(prop.id(), prop.unit())).toList();
    }

    private static DisplayGizmo gizmo(Prop prop, Vec3 eye, DisplayEditorSettings settings) {
        DisplayPose pose = prop.pose().folded();
        return DisplayGizmo.around(pose.world(prop.volume().center()), eye, FOV, settings.frame(pose.frame()),
                settings.tool());
    }

    // ---------------------------------------------------------------- dibujo

    private static void render(Path folder, String name, Map<String, String> es, List<Prop> props,
                               List<Integer> selected, int hovered, DisplayGizmo gizmo, DisplayGizmo.Handle handle,
                               DisplayDrag drag, Vec3 eye, String hud, List<String> report) throws IOException {
        // La cámara mira siempre al mismo sitio de la mesa (o al cartel): así se ve lo que se movió.
        Prop sign = find(props, SIGN);
        Vec3 target = selected.contains(SIGN) ? sign.pose().world(sign.volume().center())
                : hovered >= 0 ? find(props, hovered).pose().world(HALF) : TABLE;
        Vec3 look = target.subtract(eye);
        double yaw = Math.toDegrees(Math.atan2(-look.x(), look.z()));
        double pitch = Math.toDegrees(-Math.asin(look.y() / look.length()));
        try (PerspectiveRaster raster = new PerspectiveRaster(WIDTH, HEIGHT, eye, yaw, pitch, FOV)) {
            world(raster);
            for (Prop prop : props) {
                prop(raster, prop);
            }
            List<GizmoGeometry.Line> lines = new ArrayList<>();
            List<GizmoGeometry.Triangle> triangles = new ArrayList<>();
            for (int index = 0; index < selected.size(); index++) {
                boolean primary = index == 0;
                lines.addAll(DisplayGizmo.outline(find(props, selected.get(index)).unit(), primary ? PRIMARY : SELECTED,
                        primary ? 2.5F : 2.0F));
            }
            if (hovered >= 0) {
                lines.addAll(DisplayGizmo.outline(find(props, hovered).unit(), HOVER, 2.0F));
            }
            if (gizmo != null) {
                GizmoGeometry.Shape shape = gizmo.shape(handle, drag == null ? DisplayGizmo.Handle.NONE : drag.handle());
                lines.addAll(shape.lines());
                triangles.addAll(shape.triangles());
                if (drag != null && drag.tool() == DisplayGizmo.Tool.ROTATE) {
                    lines.addAll(drag.gizmo().arc(drag.handle().axis(), drag.angleStart(),
                            drag.angleStart() + drag.turned(), GizmoGeometry.HIGHLIGHT));
                }
            }
            raster.gizmo(new GizmoGeometry.Shape(lines, triangles), 1.0);
            for (Prop prop : props) {
                if (prop.text() != null) {
                    raster.label(prop.text(), prop.pose().world(prop.volume().center()), 0xFFFFFF);
                }
            }
            raster.actionBar(hud);
            raster.write(folder.resolve("display_" + name + ".png"));
            String size = gizmo == null ? "" : String.format(Locale.ROOT, " · gizmo de %.2f bloques a %.1f",
                    gizmo.size(), eye.distance(gizmo.center()));
            report.add(name + ": " + hud + size);
        }
    }

    /** El suelo, la mesa y la pared del fondo. */
    private static void world(PerspectiveRaster raster) {
        int[] grassTop = raster.texture("grass_block_top", 0xFF6AA84F, 0x7CBD6B);
        int[] grassSide = raster.texture("grass_block_side", 0xFF7B9E4F, 0xFFFFFF);
        int[] planks = raster.texture("oak_planks", 0xFFB08A55, 0xFFFFFF);
        int[] bricks = raster.texture("stone_bricks", 0xFF8A8A8A, 0xFFFFFF);
        for (int x = -6; x <= 7; x++) {
            for (int z = -6; z <= 6; z++) {
                cube(raster, x, 63, z, grassTop, grassSide);
            }
        }
        for (int x = -2; x <= 4; x++) {
            for (int y = 64; y <= 66; y++) {
                cube(raster, x, y, -2, bricks, bricks);
            }
        }
        cube(raster, 0, 64, 0, planks, planks);
        cube(raster, 1, 64, 0, planks, planks);
    }

    private static void cube(PerspectiveRaster raster, int x, int y, int z, int[] top, int[] side) {
        for (int face = 0; face < 6; face++) {
            raster.face(x, y, z, x + 1, y + 1, z + 1, face, face == 3 ? top : side, PerspectiveRaster.SHADE[face],
                    1.0, null);
        }
    }

    /** Un display: las seis caras de su volumen girado y escalado, con su textura. */
    private static void prop(PerspectiveRaster raster, Prop prop) {
        int[] texture = raster.texture(prop.texture(), prop.fallback(), 0xFFFFFF);
        Affine unit = prop.unit();
        // Un objeto plano (la amapola) solo enseña sus dos caras grandes: sus cantos son de un píxel.
        boolean flat = prop.id() == POPPY;
        for (int face = flat ? 4 : 0; face < 6; face++) {
            Vec3[] corners = PerspectiveRaster.corners(0, 0, 0, 1, 1, 1, face);
            Vec3[] world = new Vec3[4];
            for (int corner = 0; corner < 4; corner++) {
                world[corner] = unit.apply(corners[corner]);
            }
            raster.quad(world, texture, PerspectiveRaster.SHADE[face]);
        }
    }

    /** La barra de acción con los textos del juego, como la escribe el cliente. */
    private static String hud(Map<String, String> es, List<Integer> selected, DisplayEditorSettings settings,
                              DisplayDrag drag) {
        StringBuilder line = new StringBuilder("KE · ").append(es.get("gui.komaedit.display.hud.title")).append(" · ");
        if (selected.isEmpty()) {
            return line.append(es.get("gui.komaedit.display.hud.empty")).append(" · ")
                    .append(es.get("gui.komaedit.display.hud.hint_empty")).toString();
        }
        line.append(String.format(es.get("gui.komaedit.display.hud.selected"), selected.size()))
                .append(" · ").append(es.get("gui.komaedit.display.tool." + settings.tool().id()))
                .append(" · ").append(es.get(settings.local() || settings.tool() == DisplayGizmo.Tool.SCALE
                        ? "gui.komaedit.display.local" : "gui.komaedit.display.global"))
                .append(" · ").append(String.format(es.get("gui.komaedit.display.hud.steps"),
                        settings.steps().move().id(), settings.steps().angle() == DisplaySteps.Angle.FREE ? "libre"
                                : settings.steps().angle().id() + "°"));
        if (drag != null) {
            DisplayDrag.Item active = drag.current().getFirst();
            line.append(" · ").append(switch (drag.tool()) {
                case MOVE -> String.format(Locale.ROOT, "%.4g, %.4g, %.4g", active.center().x(), active.center().y(),
                        active.center().z());
                case ROTATE -> String.format(Locale.ROOT, "%+.1f°", Math.toDegrees(drag.turned()));
                case SCALE -> String.format(Locale.ROOT, "%.4g × %.4g × %.4g", active.pose().scale().x(),
                        active.pose().scale().y(), active.pose().scale().z());
            });
            return line.toString();
        }
        return line.append(" · ").append(es.get("gui.komaedit.display.hud.hint")).toString();
    }
}
