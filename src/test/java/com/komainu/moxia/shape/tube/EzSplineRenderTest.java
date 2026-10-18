package com.komainu.moxia.shape.tube;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeParameters;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders isométricos de comprobación de {@code //ezspline} fuera del juego. Solo se ejecuta con
 * {@code KOMAEDIT_SPLINE_RENDER=<carpeta>}: deja un PNG por ejemplo, coloreado por el valor implícito de la figura
 * (del alma, oscuro, a la superficie, claro) y un {@code tiempos.txt} con bloques y milisegundos.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_SPLINE_RENDER", matches = ".+")
class EzSplineRenderTest {
    private static final Color[] GRADIENT = {
            new Color(0x2B2D42), new Color(0x4F6D7A), new Color(0x56A3A6), new Color(0xA8D5BA),
            new Color(0xF4E285), new Color(0xF4A259), new Color(0xBC4B51)
    };

    @Test
    void rendersEveryExample() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_SPLINE_RENDER"));
        Files.createDirectories(folder);
        SampledPath path = SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(28, 10, 6),
                BlockVector3.at(56, 4, 30), BlockVector3.at(84, 14, 34)), false);
        String[] examples = {
                "basic clay 6", "2d Square clay 6", "2d Diamond clay 6", "2d RoundedSquare clay 6",
                "2d SuperCircle(Exponent:0.5) clay 8", "2d SC(E:99) clay 6", "2d CC(Count:1) clay 6",
                "2d CC(Count:5) clay 8", "2d CC(C:5,F:true) clay 8", "rope clay 7", "2d Strands(Count:4,Size:0.3) clay 8",
                "2d Pipe clay 8", "2d Polygon(Sides:3) clay 8", "2d Polygon(S:6,D:0.3) clay 8",
                "2d Re(Y1:-0.2,Y2:0.2) clay 8 -r 0,180", "2d Re(x1:0.6,y1:0.6,x2:1.0,y2:1.0) clay 10",
                "2d Star(Sides:5,Depth:0.6) clay 8", "2d Flower(Count:6,Depth:0.4) clay 8",
                "2d Polygon(S:4) clay 7 -t 90",
                "3d Beads clay 6", "3d Cubes clay 6", "3d Cubes(Gap:0.0) clay 6", "3d Cu(G:2.0) clay 6",
                "3d Braids clay 7", "3d Braids(Thickness:0.2) clay 7", "3d Chainlink clay 7",
                "3d Chainlink(M:99,N:99,Extrusion:0.6) clay 7", "3d Chainlink(M:1,N:1,E:0.7,G:-0.2,T:1.2) clay 8",
                "3d Chainlink(M:2,N:2,E:0,G:1) clay 8", "3d Chainlink(P:FIRST) red_terracotta 7",
                "3d Fishnet clay 8", "3d Fishnet(Spacing:2.0) clay 8", "3d Fi(S:2.0,D:0.5,W:0.5) clay 8",
                "3d Honeycomb clay 10", "3d Ho(C:0.4,W:0.1,D:0.3) clay 10", "3d Oscillate clay 7",
                "3d Oscillate(Depth:0.6,Interval:1.5) clay 7", "3d Rings clay 8",
                "3d Rings(E:0,T:1,G:0,M:2,N:2) clay 6", "3d Ri(E:0.5,T:0.3,G:0.5,M:6,N:4) clay 8",
                "3d Scales clay 10", "3d Sc(c:8,d:1.2) clay 12,7", "3d Noodles clay 10",
                "3d Noodles(A:6,T:6,F:1) clay 10",
                "noise ##Grayscale 8", "noise x 4,12 Ce(F:1.5,fO:1,cR:sub,M:OR,U:-.6) 0.6 -t 90",
                "noise x 10 Perlin(F:3) 0.3", "noise x 9 -i r=sqrt(x*x+y*y);(r<1&&n>0.5)*max(n,0.01)",
                "expression x 7 -t 90 R=0.2;r=0.1;w=0.7;s=0.5;sqrt((abs(x)-w)^2+y^2)<R||sqrt(((z+1)%s-r)^2+y^2)<r"
                        + "&&abs(x)<w",
                "expression a,b,c,d,e 6 -o ((z%2)>1.5?5:2*(x>0)+(y>0))+0.001",
                "basic clay 6 -q EXACT", "3d Chainlink clay 7 -q EXACT"
        };
        StringBuilder timings = new StringBuilder();
        for (String example : examples) {
            EzSpline.Request request = EzSpline.parse(example, null);
            if (request.indexed()) {
                request = request.withPaletteSize(5);
            }
            ShapeParameters parameters = request.parameters();
            long start = System.nanoTime();
            Set<BlockVector3> blocks = TubeFigures.generate(path, parameters, 1_500_000);
            long millis = (System.nanoTime() - start) / 1_000_000;
            timings.append(example).append(" -> ").append(blocks.size()).append(" bloques, ").append(millis)
                    .append(" ms\n");
            TubeFigures.MaterialProbe probe = TubeFigures.materialProbe(path, parameters, TubeCurve.NONE);
            Map<Long, Double> values = new HashMap<>();
            for (BlockVector3 block : blocks) {
                values.put(PackedBlockPlan.pack(block.x(), block.y(), block.z()), probe.value(block, 1.0));
            }
            String name = example.replaceAll("[^a-zA-Z0-9]+", "_");
            if (name.length() > 80) {
                name = name.substring(0, 80);
            }
            ImageIO.write(iso(values, example + " · " + blocks.size() + " bloques · " + millis + " ms"), "png",
                    folder.resolve(name + ".png").toFile());
        }
        Files.writeString(folder.resolve("tiempos.txt"), timings.toString());
    }

    private record Face(Polygon polygon, Color color, double depth) {
    }

    private static BufferedImage iso(Map<Long, Double> cells, String title) {
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (long key : cells.keySet()) {
            int[] point = {PackedBlockPlan.unpackX(key), PackedBlockPlan.unpackY(key), PackedBlockPlan.unpackZ(key)};
            for (int axis = 0; axis < 3; axis++) {
                bounds[axis] = Math.min(bounds[axis], point[axis]);
                bounds[axis + 3] = Math.max(bounds[axis + 3], point[axis]);
            }
        }
        double scale = Math.clamp(900.0 / Math.max(8, (bounds[3] - bounds[0]) + (bounds[5] - bounds[2])), 2.0, 14.0);
        List<Face> faces = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : cells.entrySet()) {
            long key = entry.getKey();
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            Color base = color(entry.getValue());
            double depth = x + y + z;
            if (!cells.containsKey(PackedBlockPlan.pack(x, y + 1, z))) {
                faces.add(new Face(quad(scale, x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1),
                        base, depth + 0.3));
            }
            if (!cells.containsKey(PackedBlockPlan.pack(x + 1, y, z))) {
                faces.add(new Face(quad(scale, x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x + 1, y, z + 1),
                        shade(base, 0.78), depth + 0.2));
            }
            if (!cells.containsKey(PackedBlockPlan.pack(x, y, z + 1))) {
                faces.add(new Face(quad(scale, x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1),
                        shade(base, 0.6), depth + 0.2));
            }
        }
        faces.sort(Comparator.comparingDouble(Face::depth));
        int minX = 0;
        int minY = 0;
        int maxX = 10;
        int maxY = 10;
        if (!faces.isEmpty()) {
            minX = Integer.MAX_VALUE;
            minY = Integer.MAX_VALUE;
            maxX = Integer.MIN_VALUE;
            maxY = Integer.MIN_VALUE;
            for (Face face : faces) {
                var box = face.polygon().getBounds();
                minX = Math.min(minX, box.x);
                minY = Math.min(minY, box.y);
                maxX = Math.max(maxX, box.x + box.width);
                maxY = Math.max(maxY, box.y + box.height);
            }
        }
        BufferedImage image = new BufferedImage(maxX - minX + 40, maxY - minY + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(0xE9EEF1));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.translate(20 - minX, 40 - minY);
        for (Face face : faces) {
            graphics.setColor(face.color());
            graphics.fillPolygon(face.polygon());
            graphics.setColor(shade(face.color(), 0.85));
            graphics.drawPolygon(face.polygon());
        }
        graphics.translate(minX - 20, minY - 40);
        graphics.setColor(Color.BLACK);
        graphics.drawString(title, 8, 16);
        graphics.dispose();
        return image;
    }

    private static Color color(double value) {
        double position = Math.clamp(value, 0.0, 1.0) * (GRADIENT.length - 1);
        int index = Math.min(GRADIENT.length - 2, (int) position);
        double amount = position - index;
        Color from = GRADIENT[index];
        Color to = GRADIENT[index + 1];
        return new Color((int) (from.getRed() + (to.getRed() - from.getRed()) * amount),
                (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }

    private static Color shade(Color color, double factor) {
        return new Color((int) (color.getRed() * factor), (int) (color.getGreen() * factor),
                (int) (color.getBlue() * factor));
    }

    private static Polygon quad(double scale, double... corners) {
        Polygon polygon = new Polygon();
        for (int index = 0; index < corners.length; index += 3) {
            double x = corners[index];
            double y = corners[index + 1];
            double z = corners[index + 2];
            polygon.addPoint((int) Math.round((x - z) * scale * 0.866), (int) Math.round(((x + z) * 0.5 - y) * scale));
        }
        return polygon;
    }
}
