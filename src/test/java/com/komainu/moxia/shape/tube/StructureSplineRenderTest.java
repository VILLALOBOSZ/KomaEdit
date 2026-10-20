package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.smooth.PieceRender;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders de {@code //ezspline structure} fuera del juego: la misma estructura doblada sin sección, con radios por
 * fotogramas, con torsión ({@code -t}) y con giro ({@code -r}). Solo se ejecuta con
 * {@code KOMAEDIT_SPLINE_RENDER=<carpeta>}; en el CI no hace nada.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_SPLINE_RENDER", matches = ".+")
class StructureSplineRenderTest {
    private static final int[] COLORS = {0xC0392B, 0x2E86C1, 0xF4D03F};
    private static final int LIMIT = 1_500_000;

    @Test
    void rendersSectionsAlongACurve() throws IOException {
        // Sección en cruz: tablón rojo a un lado, azul al otro y poste amarillo; así se ve el giro.
        TubeBender.Template cross = new TubeBender.Template();
        for (int x = 0; x < 6; x++) {
            for (int z = -3; z <= 3; z++) {
                cross.add(x, 0, z, z < 0 ? 0 : z > 0 ? 1 : 2);
            }
            for (int y = -3; y <= 3; y++) {
                if (y != 0) {
                    cross.add(x, y, 0, 2);
                }
            }
        }
        double inherent = 3.5;
        TubeBender.Settings repeat = new TubeBender.Settings(TubeBender.Mode.REPEAT, TubeBender.Axis.X,
                TubeSettings.NormalMode.CONSISTENT, 0, 0, 0.0, 0.0, 0.0);
        TubeBender.Settings stretch = new TubeBender.Settings(TubeBender.Mode.STRETCH, TubeBender.Axis.X,
                TubeSettings.NormalMode.CONSISTENT, 0, 0, 0.0, 0.0, 0.0);
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        record Variant(String label, TubeBender.Settings settings, TubeBender.Section section) {
        }
        for (Variant variant : List.of(
                new Variant("sin radios (a su tamaño)", repeat, TubeBender.Section.NONE),
                new Variant("3,0.5:9,3", repeat, TubeBender.Section.of("3,0.5:9,3", inherent, 1.0, 0, 0,
                        Double.NaN)),
                new Variant("-z · 2,8", stretch, TubeBender.Section.of("2,8", inherent, 1.0, 0, 0, Double.NaN)),
                new Variant("-t 45", repeat, TubeBender.Section.of(null, inherent, 1.0, 45, 0, Double.NaN)),
                new Variant("-r 45", repeat, TubeBender.Section.of(null, inherent, 1.0, 0, 45, Double.NaN)),
                new Variant("-z -r 0,180 · 4,6,4", stretch, TubeBender.Section.of("4,6,4", inherent, 1.0, 0, 0,
                        180)))) {
            long start = System.nanoTime();
            Long2IntOpenHashMap bent = TubeBender.bend(path(), cross, variant.settings(), variant.section(), LIMIT);
            long millis = (System.nanoTime() - start) / 1_000_000;
            Long2ObjectOpenHashMap<PieceRender.Cell> cells = new Long2ObjectOpenHashMap<>();
            for (Long2IntMap.Entry entry : bent.long2IntEntrySet()) {
                cells.put(entry.getLongKey(), new PieceRender.Cell(SmoothPiece.FULL,
                        COLORS[TubeBender.paletteIndex(entry.getIntValue())]));
            }
            images.add(PieceRender.render(cells, 6));
            labels.add("structure " + variant.label() + " · " + bent.size() + " bloques · " + millis + " ms");
        }
        Path folder = Path.of(System.getenv("KOMAEDIT_SPLINE_RENDER"));
        Files.createDirectories(folder);
        ImageIO.write(PieceRender.sheet(images, labels, 2), "png", folder.resolve("ezspline_structure.png").toFile());
    }

    private static SampledPath path() {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(24, 6, 10),
                BlockVector3.at(48, 0, 0), BlockVector3.at(70, 8, 14)), false);
    }
}
