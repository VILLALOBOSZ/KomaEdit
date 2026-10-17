package com.komainu.moxia.placement;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.IntFunction;

/**
 * Renders isométricos de comprobación fuera del juego. Solo se ejecuta con {@code KOMAEDIT_PLACEMENT_RENDER=<carpeta>}:
 * deja una hoja con todas las formas, otra con las icosferas, la alineación con los ejes de colores, una dispersión
 * sobre colinas y varios arrays.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_PLACEMENT_RENDER", matches = ".+")
class PlacementRenderTest {
    private static final Color[] PALETTE = {
            new Color(0xC8A26B), new Color(0x6E9A4C), new Color(0xB04A3C), new Color(0x4A6FB0),
            new Color(0xE0C040), new Color(0x8A5AB0), new Color(0x50B0A0), new Color(0xD07A30)
    };

    private Path folder() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_PLACEMENT_RENDER"));
        Files.createDirectories(folder);
        return folder;
    }

    @Test
    void rendersEveryShape() throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        String[] shapes = {"Cone", "Crystal", "Crystal(Sides:4,Extrusion:0.2)", "Cuboid", "Curl", "Cylinder",
                "Ellipsoid", "Fur", "Heart", "Jellybean", "Leaf", "Lemon", "Onion", "Polygon(Sides:5)", "Pyramid",
                "Pyramid(Sides:3)", "Supersphere(Exponent:4)", "Supersphere(Exponent:0.7)", "Tetrahedron",
                "Torus(Thickness:0.4)", "=abs(x)+abs(z)<1-abs(y)*0.6&&sin(y*9)>-0.6"};
        for (String shape : shapes) {
            ProceduralStructure structure = StructureSpec.parse("Sh(S:" + shape + ",P:stone)").procedural(1);
            Placement placement = new Placement(structure, new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY,
                    structure.blocksPerUnit(), 3L);
            PlacementVoxelizer.Result result = PlacementVoxelizer.voxelize(List.of(placement),
                    PlacementVoxelizer.Options.defaults());
            images.add(render(colors(result, index -> PALETTE[0]), 6));
            labels.add((shape.startsWith("=") ? "=fórmula" : shape) + " · " + result.size());
        }
        // Expresión y forma texturizada con paleta.
        ProceduralStructure gradient = StructureSpec.parse("TS(S:Ellipsoid,T:=y*.5+.5,P:\"a,b,c,d,e\")")
                .procedural(5);
        images.add(render(colors(PlacementVoxelizer.voxelize(List.of(new Placement(gradient, new Vec3(0.5, 0.5, 0.5),
                Mat3.IDENTITY, gradient.blocksPerUnit(), 1L)), PlacementVoxelizer.Options.defaults()),
                index -> PALETTE[index % PALETTE.length]), 6));
        labels.add("TS Ellipsoid T:=y");
        ProceduralStructure blob = StructureSpec.parse("Ex(E:\"=1.2-(x*x+y*y+z*z)*1.6+sin(x*7)*0.15\",P:\"a,b,c\")")
                .procedural(3);
        images.add(render(colors(PlacementVoxelizer.voxelize(List.of(new Placement(blob, new Vec3(0.5, 0.5, 0.5),
                Mat3.IDENTITY, blob.blocksPerUnit(), 1L)), PlacementVoxelizer.Options.defaults()),
                index -> PALETTE[(index + 2) % PALETTE.length]), 6));
        labels.add("Expression");
        // Losas y escaleras en una esfera.
        ProceduralStructure ball = StructureSpec.parse("Sh(S:Ellipsoid,P:stone)").procedural(1);
        PlacementVoxelizer.Result smooth = PlacementVoxelizer.voxelize(List.of(new Placement(ball,
                new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, new Vec3(6, 6, 6), 1L)),
                PlacementVoxelizer.Options.defaults().withSmooth(SmoothMode.STAIRS, 0.5));
        images.add(render(colors(smooth, index -> PALETTE[0]), 9, smooth.pieces()));
        labels.add("-w stairs · " + smooth.pieces().size() + " piezas");
        ImageIO.write(sheet(images, labels, 6), "png", folder().resolve("colocacion_formas.png").toFile());
    }

    @Test
    void rendersIcospheres() throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (double randomness : new double[]{0.0, 0.5, 1.0}) {
            for (int subdivisions = 0; subdivisions <= 3; subdivisions++) {
                ProceduralStructure rock = StructureSpec.parse("Ic(P:stone,R:" + randomness + ",S:" + subdivisions
                        + ")").procedural(1);
                PlacementVoxelizer.Result result = PlacementVoxelizer.voxelize(List.of(new Placement(rock,
                        new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, rock.blocksPerUnit(), 9L)),
                        PlacementVoxelizer.Options.defaults());
                images.add(render(colors(result, index -> new Color(0x9A9A9A)), 6));
                labels.add("R:" + randomness + " S:" + subdivisions);
            }
        }
        for (double platform : new double[]{0.3, 0.6}) {
            ProceduralStructure rock = StructureSpec.parse("Ic(P:stone,R:0.6,S:1,PL:" + platform + ")").procedural(1);
            PlacementVoxelizer.Result result = PlacementVoxelizer.voxelize(List.of(new Placement(rock,
                    new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, new Vec3(14, 8, 10), 4L)),
                    PlacementVoxelizer.Options.defaults());
            images.add(render(colors(result, index -> new Color(0x8C8270)), 6));
            labels.add("PL:" + platform + " -s 28,16,20");
        }
        ImageIO.write(sheet(images, labels, 4), "png", folder().resolve("colocacion_icosferas.png").toFile());
    }

    /** Ejes de colores como en la documentación de ezEdits: rojo +x, verde +y, azul +z. */
    private static VoxelStructure axes() {
        VoxelStructure.Builder builder = VoxelStructure.builder(8, 8, 8, 0, 0, 0);
        builder.set(0, 0, 0, 3);
        for (int step = 1; step < 8; step++) {
            builder.set(step, 0, 0, 0);
            builder.set(0, step, 0, 1);
            builder.set(0, 0, step, 2);
        }
        return builder.build(false, 1, 0, "ejes");
    }

    @Test
    void rendersAlignments() throws IOException {
        Color[] axisColors = {new Color(0xD03030), new Color(0x30B040), new Color(0x3050D0), Color.WHITE};
        String[][] cases = {
                {"C", "C"}, {"C(D:south)", "C"}, {"C(D:(0,1,1))", "C"}, {"C(D:(1,1,0))", "C"},
                {"C(D:(-1,2,-1))", "C(D:west)"}, {"C(D:(-1,2,-1))", "C(D:west) -j MULTIPLES_45"},
                {"C", "C -k x -c 90"}, {"C", "C(D:(1,0,1))"}
        };
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String[] alignment : cases) {
            PlacementSettings settings = PlacementSettings.parse(PlacementSettings.Kind.PLACE,
                    "Cl " + alignment[0] + " " + alignment[1]);
            PlacementBuilder builder = new PlacementBuilder(settings, List.of(axes()), PlacementBuilder.Offsets.NONE);
            Placement placement = builder.build(new Vec3(0.5, 0.5, 0.5), Alignment.Context.at(new Vec3(0.5, 0.5, 0.5),
                    new Vec3(0, 0, 0), new Vec3(1, 0, 0), 1L), 1.0);
            PlacementVoxelizer.Result result = PlacementVoxelizer.voxelize(List.of(placement),
                    PlacementVoxelizer.Options.defaults());
            Long2ObjectOpenHashMap<Color> colors = colors(result, index -> axisColors[index]);
            frame(colors, 9);
            images.add(render(colors, 8));
            labels.add(alignment[0] + " " + alignment[1]);
        }
        ImageIO.write(sheet(images, labels, 4), "png", folder().resolve("colocacion_alineacion.png").toFile());
    }

    /** Aristas grises de un cubo de referencia para ver la orientación. */
    private static void frame(Long2ObjectOpenHashMap<Color> colors, int size) {
        Color grid = new Color(0xD8D8D8);
        for (int step = -size; step <= size; step++) {
            for (int[] edge : new int[][]{{step, -size, -size}, {-size, -size, step}}) {
                long key = PackedBlockPlan.pack(edge[0], edge[1], edge[2]);
                colors.putIfAbsent(key, grid);
            }
        }
    }

    /** Colinas de piedra con hierba encima. */
    private static RegionSnapshot hills(int size) {
        RegionSnapshot.StateInfo grass = new RegionSnapshot.StateInfo(1, null, "minecraft:grass_block", false, false,
                true);
        RegionSnapshot.StateInfo stone = new RegionSnapshot.StateInfo(2, null, "minecraft:stone", false, false, true);
        RegionSnapshot.Builder builder = RegionSnapshot.builder(-3, -3, -3, size + 6, 40, size + 6)
                .state(grass).state(stone);
        for (int x = -3; x < size + 3; x++) {
            for (int z = -3; z < size + 3; z++) {
                int height = height(x, z);
                for (int y = -3; y < 37; y++) {
                    boolean inside = x >= 0 && x < size && z >= 0 && z < size && y >= 0 && y < 34;
                    int id = y > height ? 0 : y == height ? 1 : 2;
                    builder.set(x, y, z, id, inside);
                }
            }
        }
        return builder.build();
    }

    private static int height(int x, int z) {
        return (int) Math.round(6 + 4 * Math.sin(x * 0.11) + 3 * Math.cos(z * 0.13 + x * 0.05)
                + 6 * Math.exp(-((x - 40) * (x - 40) + (z - 30) * (z - 30)) / 120.0));
    }

    private static Long2ObjectOpenHashMap<Color> terrainColors(RegionSnapshot snapshot, int size) {
        Long2ObjectOpenHashMap<Color> colors = new Long2ObjectOpenHashMap<>();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int top = height(x, z);
                for (int y = 0; y <= top; y++) {
                    colors.put(PackedBlockPlan.pack(x, y, z), y == top ? new Color(0x5E9E3A) : new Color(0x7D7D7D));
                }
            }
        }
        return colors;
    }

    private static VoxelStructure tree() {
        VoxelStructure.Builder builder = VoxelStructure.builder(7, 9, 7, -3, 0, -3);
        for (int y = 0; y < 6; y++) {
            builder.set(0, y, 0, 0);
        }
        for (int x = -3; x <= 3; x++) {
            for (int y = 4; y <= 8; y++) {
                for (int z = -3; z <= 3; z++) {
                    double distance = x * x + (y - 6) * (y - 6) * 1.4 + z * z;
                    if (distance <= 9.5 && !(x == 0 && z == 0 && y < 6)) {
                        builder.set(x, y, z, 1);
                    }
                }
            }
        }
        return builder.build(false, 1, 0, "árbol");
    }

    @Test
    void rendersScatter() throws IOException {
        int size = 80;
        RegionSnapshot snapshot = hills(size);
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        String[][] scenes = {
                {"Cl C R -n 0.6 -u 0 -d up -e 0.7 -i 5", "árboles al azar (-u 0)"},
                {"Cl C R -n 0.6 -u 20 -d up -e 0.7 -i 5", "árboles uniformes (-u 20)"},
                {"Cl C R -n 0.6 -u 20 -d up -e 0.7 -i 5 -o 0.6,1.6 -f XZ", "con -o 0.6,1.6 -f XZ"},
                {"Sh(S:Cone,P:stone,O:BOTTOM) S C -s 5,14,5 -n 0.4 -i 9", "conos SurfaceNormal"},
                {"Ic(P:stone,R:0.7,S:1,O:BOTTOM) C R -s 7,5,7 -n 0.25 -o 0.5,1.5 -i 3", "rocas (Icosphere)"},
                {"Sh(S:Fur,P:grass,O:BOTTOM) C 1%C,3%R -s 4,6,4 -n 2 -i 4", "pelo con 1%C,3%R"},
        };
        for (String[] scene : scenes) {
            PlacementSettings settings = PlacementSettings.parse(PlacementSettings.Kind.SCATTER, scene[0]);
            StructureSpec spec = StructureSpec.parse(settings.structure());
            Structure structure = spec.procedural() ? spec.procedural(1) : tree();
            ScatterPlanner.Result planned = ScatterPlanner.plan(snapshot, new ScatterPlanner.Settings(
                    settings.scatter().directions(), settings.scatter().threshold(), settings.scatter().density(),
                    settings.scatter().iterations(), settings.seed()), null);
            PlacementBuilder builder = new PlacementBuilder(settings, List.of(structure), spec.offsets());
            List<Placement> placements = new ArrayList<>();
            int index = 0;
            for (ScatterPlanner.Point point : planned.points()) {
                placements.add(builder.build(point.anchorCenter(), new Alignment.Context(point.anchorCenter(),
                        new Vec3(40, 60, 40), DirectionExpr.Context.NONE, point.normal(), null, null,
                        new Vec3(0, 0, 0), new Vec3(0, 0, 0), new SplittableRandom(settings.seed() * 31 + index++),
                        null), 1.0));
            }
            PlacementVoxelizer.Result result = PlacementVoxelizer.voxelize(placements,
                    PlacementVoxelizer.Options.defaults());
            Long2ObjectOpenHashMap<Color> colors = terrainColors(snapshot, size);
            boolean procedural = spec.procedural();
            Color structureColor = scene[1].startsWith("pelo") ? new Color(0x9BD35A) : new Color(0xB0A090);
            colors.putAll(colors(result, palette -> procedural ? structureColor
                    : palette == 0 ? new Color(0x6B4A2B) : new Color(0x2F7A2F)));
            images.add(render(colors, 3));
            labels.add(scene[1] + " · " + placements.size());
        }
        ImageIO.write(sheet(images, labels, 3), "png", folder().resolve("colocacion_dispersion.png").toFile());
    }

    /** Arco de 5×6×1: dos pilares y un dintel. */
    private static VoxelStructure arch() {
        VoxelStructure.Builder builder = VoxelStructure.builder(1, 6, 5, 0, 0, -2);
        for (int y = 0; y < 6; y++) {
            builder.set(0, y, -2, 0);
            builder.set(0, y, 2, 0);
        }
        for (int z = -1; z <= 1; z++) {
            builder.set(0, 5, z, 1);
        }
        builder.set(0, 4, -1, 1);
        builder.set(0, 4, 1, 1);
        return builder.build(false, 1, 0, "arco");
    }

    @Test
    void rendersArrays() throws IOException {
        List<Vec3> curve = List.of(new Vec3(0.5, 0.5, 0.5), new Vec3(22.5, 0.5, 22.5), new Vec3(48.5, 6.5, 8.5));
        String[][] scenes = {
                {"Cl C Tangential -g 2", "arcos: C + Tangential, -g 2"},
                {"Cl C Tangential -g 0 -q 0.6,1.8", "-q 0.6,1.8"},
                {"Cl Tangential C -g 1", "Tangential C"},
                {"Sh(S:Cone,P:x) Orthogonal Tangential -s 4,10,4 -g 1", "conos Orthogonal (consistent)"},
                {"Sh(S:Cone,P:x) O(A:90) Tangential -s 4,10,4 -g 1", "O(A:90): a la izquierda"},
                {"Sh(S:Torus(Thickness:0.3),P:x) C Tangential -s 3,8,8 -g -2 -k x -c 90 -r X -i 2",
                        "cadena de toros"},
                {"Ic(P:x,R:0.6) C R -s 4,4,4 -g -1 -y 1 -o 0.7,1.4 -i 8", "rocas con -y 1"},
                {"Cl C Tangential -g 2 -p 0.8:0:0", "-p 0.8:0:0 (tenso)"},
                {"Sh(S:Cone,P:x) Orthogonal Tangential -s 4,10,4 -g 1 -n upright", "-n upright"},
        };
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String[] scene : scenes) {
            PlacementSettings settings = PlacementSettings.parse(PlacementSettings.Kind.ARRAY, scene[0]);
            StructureSpec spec = StructureSpec.parse(settings.structure());
            Structure structure = spec.procedural() ? spec.procedural(1) : arch();
            PlacementPath path = PlacementPath.create(curve, false, settings.array().tension(),
                    settings.array().bias(), settings.array().continuity(), settings.array().normalMode());
            List<Placement> placements = ArrayPlanner.plan(path, new PlacementBuilder(settings, List.of(structure),
                    spec.offsets()), new Vec3(0, 20, 0), DirectionExpr.Context.NONE, null);
            PlacementVoxelizer.Result result = PlacementVoxelizer.voxelize(placements,
                    PlacementVoxelizer.Options.defaults());
            Long2ObjectOpenHashMap<Color> colors = colors(result, palette -> palette == 0 ? new Color(0xC8B48A)
                    : new Color(0xA05A3A));
            for (double distance = 0; distance <= path.length(); distance += 0.5) {
                Vec3 point = path.at(distance).position();
                colors.putIfAbsent(PackedBlockPlan.pack((int) Math.floor(point.x()), (int) Math.floor(point.y()) - 1,
                        (int) Math.floor(point.z())), new Color(0x404040));
            }
            images.add(render(colors, 5));
            labels.add(scene[1] + " · " + placements.size());
        }
        ImageIO.write(sheet(images, labels, 3), "png", folder().resolve("colocacion_array.png").toFile());
    }

    // ------------------------------------------------------------------ Dibujo

    private static Long2ObjectOpenHashMap<Color> colors(PlacementVoxelizer.Result result, IntFunction<Color> palette) {
        Long2ObjectOpenHashMap<Color> colors = new Long2ObjectOpenHashMap<>();
        for (Long2LongMap.Entry entry : Long2LongMaps.fastIterable(result.cells())) {
            int index = PlacementVoxelizer.Result.palette(entry.getLongValue());
            if (index >= 0) {
                colors.put(entry.getLongKey(), palette.apply(index));
            }
        }
        return colors;
    }

    private static BufferedImage render(Long2ObjectOpenHashMap<Color> voxels, int scale) {
        return render(voxels, scale, new Long2ByteOpenHashMap());
    }

    /** Isométrica vista desde +x, +y, +z, pintando de atrás adelante; las piezas se dibujan como medio bloque. */
    private static BufferedImage render(Long2ObjectOpenHashMap<Color> voxels, int scale, Long2ByteOpenHashMap pieces) {
        long[] keys = voxels.keySet().toLongArray();
        java.util.Arrays.sort(keys);
        long[] ordered = java.util.Arrays.stream(keys).boxed()
                .sorted(java.util.Comparator.comparingInt(key -> PackedBlockPlan.unpackX(key)
                        + PackedBlockPlan.unpackY(key) + PackedBlockPlan.unpackZ(key)))
                .mapToLong(Long::longValue).toArray();
        double cos = Math.cos(Math.PI / 6) * scale;
        double sin = Math.sin(Math.PI / 6) * scale;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (long key : ordered) {
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            for (int corner = 0; corner < 8; corner++) {
                double[] point = project(x + (corner & 1), y + ((corner >> 1) & 1), z + ((corner >> 2) & 1), cos, sin,
                        scale);
                minX = Math.min(minX, point[0]);
                minY = Math.min(minY, point[1]);
                maxX = Math.max(maxX, point[0]);
                maxY = Math.max(maxY, point[1]);
            }
        }
        if (ordered.length == 0) {
            minX = minY = 0;
            maxX = maxY = 10;
        }
        int width = (int) Math.ceil(maxX - minX) + 8;
        int height = (int) Math.ceil(maxY - minY) + 8;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xF4F1EA));
        graphics.fillRect(0, 0, width, height);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double offsetX = 4 - minX;
        double offsetY = 4 - minY;
        for (long key : ordered) {
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            Color color = voxels.get(key);
            byte piece = pieces.get(key);
            double bottom = 0;
            double top = 1;
            if (piece != SmoothPiece.FULL && SmoothPiece.isSlab(piece)) {
                bottom = SmoothPiece.isTop(piece) ? 0.5 : 0;
                top = SmoothPiece.isTop(piece) ? 1 : 0.5;
            } else if (piece != SmoothPiece.FULL) {
                color = color.darker();
            }
            boolean full = piece == SmoothPiece.FULL;
            if (!full || !voxels.containsKey(PackedBlockPlan.pack(x, y + 1, z))) {
                face(graphics, color, 1.0, offsetX, offsetY, cos, sin, scale,
                        new double[][]{{x, y + top, z}, {x + 1, y + top, z}, {x + 1, y + top, z + 1}, {x, y + top, z + 1}});
            }
            if (!full || !voxels.containsKey(PackedBlockPlan.pack(x + 1, y, z))) {
                face(graphics, color, 0.78, offsetX, offsetY, cos, sin, scale, new double[][]{{x + 1, y + bottom, z},
                        {x + 1, y + top, z}, {x + 1, y + top, z + 1}, {x + 1, y + bottom, z + 1}});
            }
            if (!full || !voxels.containsKey(PackedBlockPlan.pack(x, y, z + 1))) {
                face(graphics, color, 0.6, offsetX, offsetY, cos, sin, scale, new double[][]{{x, y + bottom, z + 1},
                        {x + 1, y + bottom, z + 1}, {x + 1, y + top, z + 1}, {x, y + top, z + 1}});
            }
        }
        graphics.dispose();
        return image;
    }

    private static void face(Graphics2D graphics, Color color, double shade, double offsetX, double offsetY,
                             double cos, double sin, int scale, double[][] corners) {
        Polygon polygon = new Polygon();
        for (double[] corner : corners) {
            double[] point = project(corner[0], corner[1], corner[2], cos, sin, scale);
            polygon.addPoint((int) Math.round(point[0] + offsetX), (int) Math.round(point[1] + offsetY));
        }
        graphics.setColor(new Color((int) (color.getRed() * shade), (int) (color.getGreen() * shade),
                (int) (color.getBlue() * shade)));
        graphics.fillPolygon(polygon);
        if (scale >= 5) {
            graphics.setColor(new Color(0, 0, 0, 40));
            graphics.drawPolygon(polygon);
        }
    }

    private static double[] project(double x, double y, double z, double cos, double sin, int scale) {
        return new double[]{(x - z) * cos, (x + z) * sin - y * scale};
    }

    private static BufferedImage sheet(List<BufferedImage> images, List<String> labels, int columns) {
        int cellWidth = 0;
        int cellHeight = 0;
        for (BufferedImage image : images) {
            cellWidth = Math.max(cellWidth, image.getWidth());
            cellHeight = Math.max(cellHeight, image.getHeight());
        }
        cellHeight += 22;
        int rows = (images.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(cellWidth * columns, cellHeight * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(0xF4F1EA));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        for (int index = 0; index < images.size(); index++) {
            int x = index % columns * cellWidth;
            int y = index / columns * cellHeight;
            BufferedImage image = images.get(index);
            graphics.drawImage(image, x + (cellWidth - image.getWidth()) / 2, y + 20, null);
            graphics.setColor(new Color(0x303030));
            graphics.drawString(labels.get(index), x + 6, y + 15);
            graphics.setColor(new Color(0xCCCCCC));
            graphics.drawRect(x, y, cellWidth - 1, cellHeight - 1);
        }
        graphics.dispose();
        return sheet;
    }
}
