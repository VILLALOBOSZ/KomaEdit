package com.komainu.moxia.studio.ui;

import com.komainu.moxia.blueprint.BlueprintThumbnail;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.recipe.RecipeExtensions;
import com.komainu.moxia.recipe.RecipeGeometryEngine;
import com.komainu.moxia.recipe.SocketPlacement;
import com.komainu.moxia.recipe.SurfaceConform;
import com.komainu.moxia.recipe.SurfaceMode;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.tube.TubeSettings;
import com.komainu.moxia.studio.FigureVariation;
import com.sk89q.worldedit.math.BlockVector3;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * El dibujo de cada variación de la figura: un tubo de ejemplo con la variación puesta, sacado del mismo generador que
 * construye ({@link RecipeGeometryEngine}, y {@link SurfaceConform} sobre unas lomas para «Pegar al terreno»), en el
 * color del material de la figura. Enseña de un vistazo qué hace cada una; lo que hace en la figura del jugador se ve
 * en su holograma al encenderla.
 *
 * <p>Se pintan una vez, en segundo plano, como las miniaturas de las herramientas.</p>
 */
public final class VariationThumbnails {
    /** Lado en píxeles de cada dibujo. */
    public static final int SIZE = 64;
    private static final int LENGTH = 30;
    private static final int GRASS = 0x6E9A3E;
    private static final int DIRT = 0x7A5A3A;
    private static final int POST = 0x4A4A50;
    private static final int LANTERN = 0xF2C04A;

    private VariationThumbnails() {
    }

    /** El dibujo de {@code variation} en el color de {@code material} (un id de bloque), o {@code null} si se pinta. */
    public static StudioImage get(FigureVariation variation, String material) {
        int rgb = ChoiceThumbnails.color(material.contains(":") ? material : "minecraft:" + material);
        String key = "variation/" + variation.id() + "/" + Integer.toHexString(rgb);
        return ChoiceThumbnails.cached(key, () -> {
            int[] pixels = pixels(grid(variation, rgb));
            return StudioImage.of(key, SIZE, SIZE, () -> pixels);
        });
    }

    /** La rejilla de voxels del dibujo (para las pruebas). */
    static BlueprintThumbnail.Grid grid(FigureVariation variation, int rgb) {
        ProceduralRecipe tube = tube(variation);
        RecipeExtensions extensions = variation.enable(RecipeExtensions.DEFAULT, tube.shape());
        if (variation == FigureVariation.COPIED) {
            // El portapapeles es del servidor: aquí, unas farolas a lo largo del tubo, que es lo que suele copiarse.
            extensions = extensions.withSockets(false, SocketPlacement.INTERVAL, extensions.socketInterval());
        }
        PathSnapshot path = path(variation == FigureVariation.GROUND);
        if (variation == FigureVariation.GROUND) {
            path = SurfaceConform.project(HILLS, path, extensions);
        }
        Set<BlockVector3> blocks = RecipeGeometryEngine.generate(path, tube.withExtensions(extensions)).blocks();
        List<int[]> voxels = new ArrayList<>(blocks.size() + 512);
        for (BlockVector3 block : blocks) {
            voxels.add(new int[]{block.x(), block.y(), block.z(), rgb});
        }
        if (variation == FigureVariation.GROUND) {
            for (int x = -3; x <= LENGTH + 3; x++) {
                for (int z = -4; z <= 4; z++) {
                    int top = hill(x, z);
                    for (int y = top - 1; y <= top; y++) {
                        voxels.add(new int[]{x, y, z, y == top ? GRASS : DIRT});
                    }
                }
            }
        }
        if (variation == FigureVariation.COPIED) {
            for (int x = 0; x <= LENGTH; x += 10) {
                for (int y = 0; y < 5; y++) {
                    voxels.add(new int[]{x, y - 2, 4, POST});
                }
                voxels.add(new int[]{x, 3, 4, LANTERN});
                voxels.add(new int[]{x, 3, 3, POST});
            }
        }
        return grid(voxels);
    }

    /** Un tubo de estrella (se ve bien retorcido), o una cuerda fina si lleva otra figura intercalada. */
    private static ProceduralRecipe tube(FigureVariation variation) {
        ProceduralRecipe base = ProceduralRecipe.defaults().withShapeDefaults(PathShape.SWEEP);
        TubeSettings.TubeProfile profile = variation == FigureVariation.TWIST ? TubeSettings.TubeProfile.STAR
                : TubeSettings.TubeProfile.CIRCLE;
        int radius = switch (variation) {
            case ACCENT -> 1;
            case COPIED, GROUND -> 2;
            default -> 4;
        };
        ProceduralRecipe sized = base.copy(base.name(), base.shape(), "stone", radius, base.spacing(),
                base.thickness(), base.rollDegrees(), base.height(), "", base.gradientAxis(), base.dither(),
                base.reverse());
        return sized.withOptions(Map.of("profile", (double) profile.ordinal()));
    }

    /** Un camino recto de {@value #LENGTH} bloques; para el terreno, en el aire y con más puntos. */
    private static PathSnapshot path(boolean air) {
        List<BlockVector3> points = new ArrayList<>();
        int count = air ? 5 : 2;
        for (int index = 0; index < count; index++) {
            points.add(BlockVector3.at(index * LENGTH / (count - 1), air ? 9 : 0, 0));
        }
        return new PathSnapshot(points, false, "studio-thumbnail");
    }

    /** Unas lomas suaves: el suelo baja y sube a lo largo del camino. */
    static int hill(int x, int z) {
        return (int) Math.round(2.5 + 2.5 * Math.sin(x / 4.8) - Math.abs(z) * 0.15);
    }

    private static final SurfaceConform.Terrain HILLS = new SurfaceConform.Terrain() {
        @Override
        public boolean solid(int x, int y, int z) {
            return y <= hill(x, z);
        }

        @Override
        public boolean empty(int x, int y, int z) {
            return y > hill(x, z);
        }
    };

    private static BlueprintThumbnail.Grid grid(List<int[]> voxels) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] voxel : voxels) {
            minX = Math.min(minX, voxel[0]);
            minY = Math.min(minY, voxel[1]);
            minZ = Math.min(minZ, voxel[2]);
            maxX = Math.max(maxX, voxel[0]);
            maxY = Math.max(maxY, voxel[1]);
            maxZ = Math.max(maxZ, voxel[2]);
        }
        BlueprintThumbnail.Grid grid = new BlueprintThumbnail.Grid(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        for (int[] voxel : voxels) {
            grid.set(voxel[0] - minX, voxel[1] - minY, voxel[2] - minZ, voxel[3]);
        }
        return grid;
    }

    private static int[] pixels(BlueprintThumbnail.Grid grid) {
        BufferedImage image = BlueprintThumbnail.render(grid, SIZE);
        return image.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE);
    }
}
