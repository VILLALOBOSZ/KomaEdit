package com.komainu.moxia.display;

import com.komainu.moxia.shape.Vec3;

/**
 * Lo que ocupa el modelo de un display en sus propias coordenadas, como la transformación que lleva el cubo unidad
 * [0, 1]³ a esa caja (girada si hace falta). Los displays no tienen caja de colisión: con esto se eligen con un rayo
 * ({@link DisplayPick}), se dibuja su contorno y se sabe dónde está su centro para girarlos sobre él.
 *
 * <ul>
 *     <li><b>Bloque</b>: el cubo [0, 1]³, como lo dibuja Minecraft.</li>
 *     <li><b>Objeto</b>: el modelo del objeto, centrado en el origen, con la transformación de su modo de vista
 *     ({@code fixed}, {@code gui}…) y el medio giro en Y que le da el display. Los objetos planos son una lámina de
 *     1/16.</li>
 *     <li><b>Texto</b>: el cartel con su fondo, que crece hacia arriba desde el origen (1/40 de bloque por píxel de la
 *     fuente, 10 píxeles por línea).</li>
 * </ul>
 */
public record DisplayVolume(Affine unit) {
    /** Grosor que se da a un texto (es plano) para poder apuntarle de canto. */
    public static final double TEXT_THICKNESS = 0.02;
    /** Lo que mide un píxel de la fuente en un display de texto. */
    public static final double TEXT_PIXEL = 0.025;
    public static final int TEXT_LINE_PIXELS = 10;
    /** Ancho medio de un carácter de la fuente de Minecraft, para estimar un texto sin la fuente (el servidor). */
    public static final int AVERAGE_CHARACTER_PIXELS = 6;
    private static final Vec3 HALF = new Vec3(0.5, 0.5, 0.5);

    public static DisplayVolume block() {
        return new DisplayVolume(Affine.IDENTITY);
    }

    /**
     * El modelo de un objeto con su transformación de vista (la del JSON del modelo para ese modo).
     *
     * @param rotationDegrees giro en X, Y y Z, en grados (Minecraft lo aplica con {@code rotationXYZ})
     * @param translation     traslación en bloques
     * @param flat            objeto plano (una lámina de 1/16 en el centro del cubo, como los de {@code generated})
     */
    public static DisplayVolume item(Vec3 rotationDegrees, Vec3 translation, Vec3 scale, boolean flat) {
        Affine base = flat
                ? Affine.translation(new Vec3(0, 0, 7.5 / 16.0)).multiply(Affine.scale(new Vec3(1, 1, 1.0 / 16.0)))
                : Affine.IDENTITY;
        Affine model = Affine.rotation(Quat.axis(1, Math.PI))
                .multiply(Affine.translation(translation))
                .multiply(Affine.rotation(Quat.xyz(Math.toRadians(rotationDegrees.x()),
                        Math.toRadians(rotationDegrees.y()), Math.toRadians(rotationDegrees.z()))))
                .multiply(Affine.scale(scale))
                .multiply(Affine.translation(HALF.multiply(-1.0)))
                .multiply(base);
        return new DisplayVolume(model);
    }

    /** Un objeto sin transformación de vista (el modo {@code none}): el cubo centrado en el origen. */
    public static DisplayVolume item(boolean flat) {
        return item(DisplayPose.ZERO, DisplayPose.ZERO, DisplayPose.ONE, flat);
    }

    /**
     * Un texto de {@code widthPixels} de ancho (la línea más larga) y {@code lines} líneas, como lo dibuja Minecraft:
     * centrado en X y de abajo arriba desde el origen, con el margen de 1 píxel del fondo.
     */
    public static DisplayVolume text(int widthPixels, int lines) {
        int width = Math.max(1, widthPixels);
        int height = TEXT_LINE_PIXELS * Math.max(1, lines);
        double minimumX = -width / 2.0 * TEXT_PIXEL;
        double maximumX = (width / 2.0 + 1.0) * TEXT_PIXEL;
        double maximumY = (height + 1.0) * TEXT_PIXEL;
        return box(new Vec3(minimumX, 0.0, -TEXT_THICKNESS / 2.0), new Vec3(maximumX, maximumY,
                TEXT_THICKNESS / 2.0));
    }

    /**
     * Un texto estimado sin la fuente (en el servidor): {@link #AVERAGE_CHARACTER_PIXELS} por carácter, partido en
     * líneas de {@code lineWidth} píxeles como mucho.
     */
    public static DisplayVolume estimatedText(String text, int lineWidth) {
        int widest = 0;
        int lines = 0;
        int maximum = Math.max(AVERAGE_CHARACTER_PIXELS, lineWidth);
        for (String line : (text == null ? "" : text).split("\n", -1)) {
            int pixels = line.length() * AVERAGE_CHARACTER_PIXELS;
            int wrapped = Math.max(1, (pixels + maximum - 1) / maximum);
            lines += wrapped;
            widest = Math.max(widest, Math.min(pixels, maximum));
        }
        return text(widest, lines);
    }

    /** Una caja alineada con los ejes del modelo. */
    public static DisplayVolume box(Vec3 minimum, Vec3 maximum) {
        return new DisplayVolume(Affine.translation(minimum).multiply(Affine.scale(maximum.subtract(minimum))));
    }

    /** El centro del modelo, en sus coordenadas: sobre él se gira y se escala. */
    public Vec3 center() {
        return unit.apply(HALF);
    }

    /** Del cubo unidad al mundo, con la pose del display (fijo). */
    public Affine world(DisplayPose pose) {
        return pose.model().multiply(unit);
    }

    /** Del cubo unidad al mundo, con la cámara que lo mira (modos billboard). */
    public Affine world(DisplayPose pose, double cameraYaw, double cameraPitch) {
        return pose.model(cameraYaw, cameraPitch).multiply(unit);
    }

    /** Lo que mide la caja en sus tres ejes, en bloques del mundo con esta pose. */
    public Vec3 size(DisplayPose pose) {
        return world(pose).columnLengths();
    }
}
