package com.komainu.moxia.brush.paint;

import com.komainu.moxia.brush.ez.BrushNoise;

import java.util.ArrayList;
import java.util.List;

/**
 * Un trazo del pincel de pintura: los puntos por donde pasa la mirada mientras se mantiene el clic, convertidos en
 * toques ({@link PaintPainter.Dab}) a un espaciado fijo. Cada toque lleva su giro y su escala; el trazo, su sal.
 *
 * <p>Con la semilla libre, cada toque gira al azar y cambia un poco de tamaño (±{@value #SCALE_VARIATION}·100 %),
 * siguiendo una cuenta que no se reinicia entre trazos: cada clic sale distinto. Con la semilla bloqueada, todos los
 * toques llevan el giro y la escala de la semilla, y la cuenta y la sal empiezan de cero en cada trazo: el mismo trazo
 * sobre el mismo terreno pinta siempre lo mismo. Lógica pura, con pruebas.</p>
 */
public final class PaintStroke {
    /** Cuánto varía como mucho el tamaño de la huella entre toques con la semilla libre. */
    public static final double SCALE_VARIATION = 0.15;
    private static final long ANGLE_SALT = 0x414E_474CL;
    private static final long SCALE_SALT = 0x5343_414CL;
    private static final long DITHER_SALT = 0x5452_414DL;

    private final double radius;
    private final double spacing;
    private final long seed;
    private final boolean locked;
    private final long salt;
    private long counter;
    private boolean started;
    private double lastX;
    private double lastY;
    private double lastZ;
    /** Lo recorrido desde el último toque. */
    private double travelled;

    /**
     * @param spacing distancia entre toques en radios ({@code 0.3} = un toque cada 30 % del radio)
     * @param counter primer número de la cuenta de toques (con la semilla libre, el del pincel)
     */
    public PaintStroke(double radius, double spacing, long seed, boolean locked, long counter) {
        this.radius = radius;
        this.spacing = Math.max(1.0, radius * spacing);
        this.seed = seed;
        this.locked = locked;
        this.counter = locked ? 0 : counter;
        this.salt = mix(seed ^ DITHER_SALT, this.counter);
    }

    /**
     * La sal del trazo (la trama y lo que se cubre): con la semilla libre cambia en cada trazo; bloqueada, es la de
     * la semilla.
     */
    public long salt() {
        return salt;
    }

    /** Toques dados hasta ahora (con la semilla libre, sumados a la cuenta de partida). */
    public long counter() {
        return counter;
    }

    /** Bloques entre toques. */
    public double spacing() {
        return spacing;
    }

    /**
     * Un punto más del trazo (el centro del bloque mirado). El primero da un toque ahí mismo; los demás, los que
     * quepan a lo largo del tramo desde el anterior.
     */
    public List<PaintPainter.Dab> add(double x, double y, double z) {
        List<PaintPainter.Dab> dabs = new ArrayList<>();
        if (!started) {
            started = true;
            lastX = x;
            lastY = y;
            lastZ = z;
            travelled = 0.0;
            dabs.add(dab(x, y, z));
            return dabs;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-9) {
            return dabs;
        }
        double along = spacing - travelled;
        while (along <= length) {
            double t = along / length;
            dabs.add(dab(lastX + dx * t, lastY + dy * t, lastZ + dz * t));
            along += spacing;
        }
        travelled = length - (along - spacing);
        lastX = x;
        lastY = y;
        lastZ = z;
        return dabs;
    }

    private PaintPainter.Dab dab(double x, double y, double z) {
        long index = counter++;
        double angle;
        double scale;
        if (locked) {
            angle = BrushNoise.unit(0, 0, 0, seed ^ ANGLE_SALT) * Math.PI * 2.0;
            scale = 1.0;
        } else {
            angle = BrushNoise.unit((int) index, (int) (index >>> 32), 1, seed ^ ANGLE_SALT) * Math.PI * 2.0;
            scale = 1.0 + (BrushNoise.unit((int) index, (int) (index >>> 32), 2, seed ^ SCALE_SALT) * 2.0 - 1.0)
                    * SCALE_VARIATION;
        }
        return new PaintPainter.Dab(x, y, z, Math.max(0.5, radius * scale), angle);
    }

    static long mix(long seed, long index) {
        long value = seed + index * 0x9E37_79B9_7F4A_7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return value ^ (value >>> 31);
    }
}
