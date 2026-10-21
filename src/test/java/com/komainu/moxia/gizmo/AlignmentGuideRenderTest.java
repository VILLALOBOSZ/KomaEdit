package com.komainu.moxia.gizmo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.brush.ez.AlignmentGuideDisplays;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.placement.Alignment;
import com.komainu.moxia.placement.AlignmentGuide;
import com.komainu.moxia.placement.DirectionExpr;
import com.komainu.moxia.placement.Placement;
import com.komainu.moxia.placement.PlacementBuilder;
import com.komainu.moxia.placement.PlacementService;
import com.komainu.moxia.placement.PlacementSettings;
import com.komainu.moxia.placement.PlacementVoxelizer;
import com.komainu.moxia.placement.ScatterPlanner;
import com.komainu.moxia.placement.VoxelStructure;
import com.komainu.moxia.shape.Vec3;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Renders de comprobación del visualizador de la alineación de los pinceles de colocación (solo con
 * {@code KOMAEDIT_GUIDE_RENDER} apuntando a una carpeta; con {@code KOMAEDIT_MC_JAR}, con las texturas del juego).
 * Usan el mismo código que el juego: el sitio y la orientación ({@link AlignmentGuide#probe},
 * {@link AlignmentGuide#compute}), las flechas del cliente ({@link AlignmentGuide#shape}) con su tamaño de pantalla y
 * las barras de los clientes sin el mod ({@link AlignmentGuideDisplays}). Donde la orientación vale, se ve además, en
 * translúcido, lo que colocaría el pincel de verdad: unos ejes de vóxeles (rojo +x, verde +y, azul +z) puestos con
 * {@link PlacementBuilder}, que deben caer sobre las flechas.
 */
class AlignmentGuideRenderTest {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 760;
    private static final double FOV = 70.0;
    private static final int GRASS = 1;
    private static final int DIRT = 2;
    private static final int STONE = 3;
    private static final int[] AXIS_COLORS = {0xE5484D, 0x46B358, 0x3E8BFF, 0xF0F0F0};

    /** Una escena: el pincel, el bloque y la cara que se miran, desde dónde y lo que ya se marcó. */
    private record Scene(String name, PlacementSettings.Kind kind, String input, BlockVector3 surface, Vec3 face,
                         Vec3 eye, PlacementService.BrushTarget pending, List<BlockVector3> stroke, Vec3 boundAim,
                         boolean displays, String note) {
    }

    @Test
    void alignmentGuide() throws IOException {
        String output = System.getenv("KOMAEDIT_GUIDE_RENDER");
        Assumptions.assumeTrue(output != null && !output.isBlank(), "KOMAEDIT_GUIDE_RENDER sin definir");
        Path folder = Path.of(output);
        Files.createDirectories(folder);
        JsonObject spanish = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/komaedit/lang/es_es.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        Long2IntOpenHashMap world = terrain();
        ScatterPlanner.AirTest air = (x, y, z) -> !world.containsKey(PackedBlockPlan.pack(x, y, z));
        Vec3 up = new Vec3(0, 1, 0);
        BlockVector3 slope = BlockVector3.at(8, 68, -1);
        BlockVector3 flat = BlockVector3.at(-4, 63, 2);
        PlacementService.BrushTarget marked = new PlacementService.BrushTarget(flat.add(0, 1, 0), flat, up,
                List.of(), null);
        List<Scene> scenes = List.of(
                new Scene("1_superficie_en_la_ladera", PlacementSettings.Kind.PLACE,
                        "Sh(S:Cone,P:stone) SurfaceNormal Constant", slope, up, new Vec3(-3.5, 74.6, 10.5), null,
                        List.of(), null, false, "La primaria sigue la ladera bajo el punto de mira"),
                new Scene("2_ejemplo_de_la_wiki", PlacementSettings.Kind.PLACE,
                        "Clipboard C(D:(-1,2,-1)) C(D:west)", flat, up, new Vec3(-1.5, 68.6, 14.5), null, List.of(),
                        null, false, "A trazos, la secundaria pedida (oeste); en rojo, la perpendicular más parecida"),
                new Scene("3_viewdiff_esperando", PlacementSettings.Kind.PLACE, "Clipboard SurfaceNormal ViewDiff",
                        flat, up, new Vec3(6.5, 70.6, 11.5), null, List.of(), null, false,
                        "Antes del clic derecho solo se sabe la primaria"),
                new Scene("4_viewdiff_hacia_donde_miras", PlacementSettings.Kind.PLACE,
                        "Clipboard SurfaceNormal ViewDiff", BlockVector3.at(3, 63, -5), up, new Vec3(6.5, 71.6, 12.5),
                        marked, List.of(), null, false, "Tras el clic derecho, la secundaria apunta a donde miras"),
                new Scene("5_misma_recta", PlacementSettings.Kind.PLACE, "Clipboard C(D:up) C(D:up)", flat, up,
                        new Vec3(6.5, 70.6, 11.5), null, List.of(), null, false,
                        "Primaria y secundaria en la misma recta: la colocación fallaría"),
                new Scene("6_array_siguiente_tramo", PlacementSettings.Kind.ARRAY, "Clipboard Tangential C(D:up)",
                        BlockVector3.at(3, 63, -3), up, new Vec3(-1.5, 72.6, 13.5), null,
                        List.of(BlockVector3.at(-9, 63, 7), flat), null, false,
                        "En array, a lo largo del tramo que añadiría el siguiente punto"),
                new Scene("7_mirada_fija_al_configurar", PlacementSettings.Kind.PLACE,
                        "Clipboard C(D:aim) C(D:up)", flat, up, new Vec3(8.5, 69.6, 4.5), null, List.of(),
                        new Vec3(0, 0, -1), false,
                        "C(D:aim) se fijó al configurar mirando al norte: aunque mires al suelo, sigue al norte"),
                new Scene("8_sin_el_mod_block_displays", PlacementSettings.Kind.PLACE,
                        "Sh(S:Cone,P:stone) SurfaceNormal Constant", slope, up, new Vec3(-3.5, 74.6, 10.5), null,
                        List.of(), null, true, "Clientes sin KomaEdit: barras de hormigón que solo ve quien pincela"),
                new Scene("9_lejos_mismo_tamano", PlacementSettings.Kind.PLACE,
                        "Clipboard C(D:(-1,2,-1)) C(D:west)", flat, up, new Vec3(1.5, 80.6, 50.5), null, List.of(),
                        null, false, "Lejos, las flechas ocupan lo mismo en pantalla"));
        List<String> report = new ArrayList<>();
        for (Scene scene : scenes) {
            report.add(render(folder, scene, world, air, spanish));
        }
        report.add("Texturas de Minecraft: " + (System.getenv("KOMAEDIT_MC_JAR") == null ? "no" : "sí"));
        Files.writeString(folder.resolve("alineacion.txt"), String.join("\n", report) + "\n");
    }

    private static String render(Path folder, Scene scene, Long2IntOpenHashMap world, ScatterPlanner.AirTest air,
                                 JsonObject spanish) throws IOException {
        PlacementSettings settings = PlacementSettings.parse(scene.kind(), scene.input(), true);
        if (scene.boundAim() != null) {
            settings = settings.bindAim(new DirectionExpr.Context(scene.boundAim().normalized()));
        }
        Vec3 target = new Vec3(scene.surface().x() + 0.5, scene.surface().y() + 1.0, scene.surface().z() + 0.5);
        Vec3 aim = target.subtract(scene.eye()).normalized();
        AlignmentGuide.Probe probe = AlignmentGuide.probe(settings, scene.surface(), scene.face(), scene.eye(), aim,
                scene.pending(), scene.stroke(), air);
        AlignmentGuide.Result result = AlignmentGuide.compute(settings, probe, 7L);
        // Como en el juego: el punto de mira está en el bloque que se mira, no en el centro de las flechas.
        Vec3 look = target.subtract(scene.eye());
        double yaw = Math.toDegrees(Math.atan2(-look.x(), look.z()));
        double pitch = Math.toDegrees(-Math.asin(look.y() / look.length()));
        double length = GizmoMath.screenSize(result.origin().distance(scene.eye()), FOV, AlignmentGuide.SCREEN_FRACTION);
        try (PerspectiveRaster raster = new PerspectiveRaster(WIDTH, HEIGHT, scene.eye(), yaw, pitch, FOV)) {
            for (Long2IntMap.Entry entry : world.long2IntEntrySet()) {
                long packed = entry.getLongKey();
                block(raster, entry.getIntValue(), PackedBlockPlan.unpackX(packed), PackedBlockPlan.unpackY(packed),
                        PackedBlockPlan.unpackZ(packed), exposed(world, packed));
            }
            for (int index = 0; index < scene.stroke().size(); index++) {
                BlockVector3 point = scene.stroke().get(index);
                marker(raster, point, 0xFFCA28);
                BlockVector3 next = index + 1 < scene.stroke().size() ? scene.stroke().get(index + 1)
                        : scene.surface();
                raster.depthLine(new Vec3(point.x() + 0.5, point.y() + 1.2, point.z() + 0.5),
                        new Vec3(next.x() + 0.5, next.y() + 1.2, next.z() + 0.5),
                        index + 1 < scene.stroke().size() ? 0xFFFFCA28 : 0x99FFCA28, 3.0);
            }
            if (scene.pending() != null) {
                marker(raster, scene.pending().surface(), 0x66BB6A);
            }
            // La colocación de comprobación no existe en el juego: con block displays (que sí tienen profundidad)
            // los taparía, así que ahí no se pinta.
            if (result.state() == AlignmentGuide.State.READY && !scene.displays()) {
                ghost(raster, settings, probe, 7L);
            }
            if (scene.displays()) {
                displays(raster, result, AlignmentGuideDisplays.length(result.origin().distance(scene.eye())));
            } else {
                raster.gizmo(AlignmentGuide.shape(result, length), 1.0);
                for (AlignmentGuide.Label label : AlignmentGuide.labels(result, length)) {
                    raster.label(spanish.get("gui.komaedit.guide." + label.key()).getAsString(), label.at(),
                            label.argb() & 0xFFFFFF);
                }
            }
            raster.actionBar("/komaedit pincel " + scene.kind().id() + " " + scene.input());
            raster.label(scene.note(), scene.eye().add(new Vec3(look.normalized().x() * 4.0,
                    look.normalized().y() * 4.0 + 1.55, look.normalized().z() * 4.0)), 0xFFFFFF);
            raster.write(folder.resolve("alineacion_" + scene.name() + ".png"));
        }
        double pixels = length / result.origin().distance(scene.eye()) * (HEIGHT / 2.0)
                / Math.tan(Math.toRadians(FOV / 2.0));
        return String.format(Locale.ROOT, "%s: %s%s · flecha de %.2f bloques a %.1f (≈ %.0f px)%s", scene.name(),
                result.state(), result.random() ? " (al azar)" : "", length, result.origin().distance(scene.eye()),
                pixels, result.state() == AlignmentGuide.State.READY
                        ? String.format(Locale.ROOT, " · +y %s · +x %s", short3(result.y()), short3(result.x())) : "");
    }

    private static String short3(Vec3 vector) {
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", vector.x(), vector.y(), vector.z());
    }

    // ---------------------------------------------------------------- la colocación de verdad

    /** Ejes de vóxeles de colores, como en la documentación de ezEdits: rojo +x, verde +y, azul +z. */
    private static VoxelStructure axes() {
        VoxelStructure.Builder builder = VoxelStructure.builder(7, 7, 7, 0, 0, 0);
        builder.set(0, 0, 0, 3);
        for (int step = 1; step < 7; step++) {
            builder.set(step, 0, 0, 0);
            builder.set(0, step, 0, 1);
            builder.set(0, 0, step, 2);
        }
        return builder.build(false, 1, 0, "ejes");
    }

    /** Lo que colocaría el clic, translúcido: debe caer sobre las flechas. */
    private static void ghost(PerspectiveRaster raster, PlacementSettings settings, AlignmentGuide.Probe probe,
                              long seed) {
        Alignment.Context context = new Alignment.Context(probe.origin(), probe.eye(),
                new DirectionExpr.Context(probe.aim()), probe.normal(), probe.tangent(), probe.pathNormal(),
                new Vec3(0, 0, 0), new Vec3(0, 0, 0), new SplittableRandom(seed), probe.viewDiff());
        Placement placement = new PlacementBuilder(settings, List.of(axes()), PlacementBuilder.Offsets.NONE)
                .build(probe.origin(), context, 1.0);
        PlacementVoxelizer.Result voxels = PlacementVoxelizer.voxelize(List.of(placement),
                PlacementVoxelizer.Options.defaults());
        Long2IntOpenHashMap cells = new Long2IntOpenHashMap();
        for (Long2LongMap.Entry entry : Long2LongMaps.fastIterable(voxels.cells())) {
            int index = PlacementVoxelizer.Result.palette(entry.getLongValue());
            if (index >= 0) {
                cells.put(entry.getLongKey(), AXIS_COLORS[index]);
            }
        }
        double[] layer = raster.layer();
        for (double pass : new double[]{-1.0, 0.38}) {
            for (Long2IntMap.Entry entry : cells.long2IntEntrySet()) {
                long packed = entry.getLongKey();
                int x = PackedBlockPlan.unpackX(packed);
                int y = PackedBlockPlan.unpackY(packed);
                int z = PackedBlockPlan.unpackZ(packed);
                int[] flat = flat(entry.getIntValue());
                int mask = exposed(cells, packed);
                for (int face = 0; face < 6; face++) {
                    if ((mask & 1 << face) != 0) {
                        raster.face(x, y, z, x + 1, y + 1, z + 1, face, flat, PerspectiveRaster.SHADE[face], pass,
                                layer);
                    }
                }
            }
            if (pass < 0) {
                raster.mergeDepth(layer.clone());
            }
        }
    }

    // ---------------------------------------------------------------- block displays

    /** Las barras de los clientes sin el mod, con la misma transformación que se manda al juego. */
    private static void displays(PerspectiveRaster raster, AlignmentGuide.Result result, double length) {
        for (AlignmentGuideDisplays.Beam beam : AlignmentGuideDisplays.beams(result, length)) {
            AlignmentGuideDisplays.Transform transform = AlignmentGuideDisplays.transform(beam, result.origin());
            int rgb = concrete(beam.argb());
            for (int face = 0; face < 6; face++) {
                Vec3[] corners = PerspectiveRaster.corners(0, 0, 0, 1, 1, 1, face);
                Vec3[] placed = new Vec3[4];
                for (int corner = 0; corner < 4; corner++) {
                    Vector3f point = new Vector3f((float) corners[corner].x(), (float) corners[corner].y(),
                            (float) corners[corner].z()).mul(transform.scale());
                    transform.rotation().transform(point);
                    point.add(transform.translation());
                    placed[corner] = result.origin().add(new Vec3(point.x, point.y, point.z));
                }
                raster.quad(placed, rgb, PerspectiveRaster.SHADE[face]);
            }
        }
    }

    /** El color del hormigón de cada barra (el cristal rojo de la secundaria pedida, algo más claro). */
    private static int concrete(int argb) {
        if (argb == AlignmentGuide.HINT) {
            return 0xC95A5A;
        }
        return switch (argb & 0xFFFFFF) {
            case GizmoGeometry.GREEN & 0xFFFFFF -> 0x5EA918;
            case GizmoGeometry.RED & 0xFFFFFF -> 0x8E2121;
            case GizmoGeometry.BLUE & 0xFFFFFF -> 0x2C2E8F;
            case GizmoGeometry.HIGHLIGHT & 0xFFFFFF -> 0xF1AF15;
            default -> 0xCFD5D6;
        };
    }

    // ---------------------------------------------------------------- escena

    /** Un llano de hierba con una ladera de piedra a 45° que sube hacia el este. */
    private static Long2IntOpenHashMap terrain() {
        Long2IntOpenHashMap world = new Long2IntOpenHashMap();
        for (int x = -16; x <= 24; x++) {
            for (int z = -16; z <= 16; z++) {
                world.put(PackedBlockPlan.pack(x, 63, z), GRASS);
                world.put(PackedBlockPlan.pack(x, 62, z), DIRT);
            }
        }
        for (int x = 4; x <= 13; x++) {
            for (int z = -6; z <= 3; z++) {
                for (int y = 64; y <= 63 + (x - 3); y++) {
                    world.put(PackedBlockPlan.pack(x, y, z), STONE);
                }
            }
        }
        return world;
    }

    private static void marker(PerspectiveRaster raster, BlockVector3 point, int rgb) {
        int[] flat = flat(rgb);
        double x = point.x() + 0.3;
        double y = point.y() + 1.0;
        double z = point.z() + 0.3;
        for (int face = 0; face < 6; face++) {
            raster.face(x, y, z, x + 0.4, y + 0.4, z + 0.4, face, flat, PerspectiveRaster.SHADE[face], 1.0, null);
        }
    }

    private static void block(PerspectiveRaster raster, int block, int x, int y, int z, int mask) {
        for (int face = 0; face < 6; face++) {
            if ((mask & 1 << face) == 0) {
                continue;
            }
            int[] texture = switch (block) {
                case GRASS -> face == 3 ? raster.texture("grass_block_top", 0x7DB04E, 0x7DB860)
                        : face == 2 ? raster.texture("dirt", 0x866043, 0xFFFFFF)
                        : raster.texture("grass_block_side", 0x866043, 0xFFFFFF);
                case DIRT -> raster.texture("dirt", 0x866043, 0xFFFFFF);
                default -> raster.texture("stone", 0x7D7D7D, 0xFFFFFF);
            };
            raster.face(x, y, z, x + 1, y + 1, z + 1, face, texture, PerspectiveRaster.SHADE[face], 1.0, null);
        }
    }

    private static int[] flat(int rgb) {
        int[] texture = new int[256];
        Arrays.fill(texture, 0xFF000000 | rgb);
        return texture;
    }

    private static int exposed(Long2IntMap occupied, long packed) {
        int x = PackedBlockPlan.unpackX(packed);
        int y = PackedBlockPlan.unpackY(packed);
        int z = PackedBlockPlan.unpackZ(packed);
        int[][] offsets = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
        int mask = 0;
        for (int face = 0; face < 6; face++) {
            if (!occupied.containsKey(PackedBlockPlan.pack(x + offsets[face][0], y + offsets[face][1],
                    z + offsets[face][2]))) {
                mask |= 1 << face;
            }
        }
        return mask;
    }
}
