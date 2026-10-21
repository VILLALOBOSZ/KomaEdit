package com.komainu.moxia.terrain;

import java.util.List;
import java.util.Locale;

/**
 * Las herramientas de terreno de principio a fin sobre una {@link TerrainView}: leer columnas, cambiar el mapa de
 * alturas y escribir los bloques. No tocan Minecraft; el servicio del juego les da la foto y recoge los bloques, y
 * las pruebas les dan un mundo de texto.
 */
public final class TerrainJobs {
    /** Pasadas térmicas que siguen a las gotas en la erosión completa. */
    public static final int FULL_THERMAL_PASSES = 5;
    /** Grados que la pasada térmica de la erosión completa suma al ángulo de reposo. */
    public static final double FULL_THERMAL_EXTRA_DEGREES = 8.0;

    private TerrainJobs() {
    }

    /** Lo que ha pasado, para el mensaje del chat. */
    public record Summary(int columns, String detail) {
    }

    /** Un clic del pincel de relieve en el centro del bloque {@code (x, z)}. */
    public static Summary stamp(TerrainView view, StampBrush.Settings settings, int x, int z, SurfacePalette palette,
                                TerrainWriter.BlockSink sink, Runnable checkCancelled) {
        int reach = settings.reach();
        TerrainColumns columns = TerrainColumns.read(view, x - reach, z - reach, x + reach, z + reach, checkCancelled);
        HeightField field = columns.heights();
        StampBrush.apply(field, x + 0.5, z + 0.5, settings, checkCancelled);
        // La cumbre relativa de la paleta va sobre lo que levanta el sello (de la altura del clic a la del sello):
        // la nieve sale en lo alto de la montaña estampada, y no en el borde de un hoyo.
        SurfacePalette resolved = palette.forRange(settings.baseY(), settings.baseY() + settings.amplitude());
        int changed = TerrainWriter.write(columns, field, null, resolved, false, sink, checkCancelled);
        return new Summary(changed, settings.stamp().name());
    }

    /** Rampa entre dos bloques {x, y, z}. */
    public static Summary ramp(TerrainView view, int[] start, int[] end, RampPlanner.Settings settings,
                               SurfacePalette palette, TerrainWriter.BlockSink sink, Runnable checkCancelled) {
        TerrainColumns columns = TerrainColumns.read(view, checkCancelled);
        HeightField field = columns.heights();
        double[] from = {start[0] + 0.5, start[1], start[2] + 0.5};
        double[] to = {end[0] + 0.5, end[1], end[2] + 0.5};
        RampPlanner.apply(field, from, to, settings, checkCancelled);
        int changed = TerrainWriter.write(columns, field, null, palette, false, sink, checkCancelled);
        double length = Math.hypot(to[0] - from[0], to[2] - from[2]);
        return new Summary(changed, String.format(Locale.ROOT, "%s de %.0f bloques, de Y %d a Y %d",
                settings.section().displayName(), length, start[1], end[1]));
    }

    /** Río o cañón a lo largo de la trayectoria muestreada. */
    public static Summary river(TerrainView view, List<double[]> samples, RiverPlanner.Settings settings,
                                SurfacePalette palette, TerrainWriter.BlockSink sink, Runnable checkCancelled) {
        TerrainColumns columns = TerrainColumns.read(view, checkCancelled);
        HeightField field = columns.heights();
        RiverPlanner.Result result = RiverPlanner.plan(field, samples, settings, checkCancelled);
        TerrainWriter.Fluids fluids = settings.fluid() == RiverPlanner.Fluid.NONE ? null : result;
        int changed = TerrainWriter.write(columns, field, fluids, palette, false, sink, checkCancelled);
        String flow = String.format(Locale.ROOT, "el fondo baja de Y %.0f a Y %.0f%s", result.startBed(),
                result.endBed(), result.reversed() ? " (al revés que los puntos, hacia lo más bajo)" : "");
        return new Summary(changed, flow);
    }

    public enum Erosion {
        HYDRAULIC("hydraulic", "hidráulica"),
        THERMAL("thermal", "térmica"),
        FULL("full", "completa");

        private final String id;
        private final String displayName;

        Erosion(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public static Erosion byId(String input) {
            return switch (input.toLowerCase(Locale.ROOT).replace("á", "a").replace("é", "e")) {
                case "hydraulic", "hidraulica", "agua", "gotas" -> HYDRAULIC;
                case "thermal", "termica", "talud" -> THERMAL;
                case "full", "completa", "ambas", "both" -> FULL;
                default -> throw new IllegalArgumentException("Erosión desconocida: " + input
                        + " (hidraulica, termica o completa)");
            };
        }
    }

    /**
     * Erosión física sobre lo seleccionado.
     *
     * @param iterations gotas por columna (hidráulica y completa) o pasadas (térmica)
     * @param strength   fuerza (1 normal)
     * @param talus      ángulo de reposo de la térmica
     */
    public static Summary erode(TerrainView view, Erosion mode, double iterations, double strength, double talus,
                                long seed, SurfacePalette palette, TerrainWriter.BlockSink sink,
                                Runnable checkCancelled) {
        TerrainColumns columns = TerrainColumns.read(view, checkCancelled);
        HeightField field = columns.heights();
        StringBuilder detail = new StringBuilder();
        if (mode != Erosion.THERMAL) {
            HydraulicErosion.Settings settings = HydraulicErosion.Settings.of(field.editableColumns(), iterations,
                    strength, seed);
            HydraulicErosion.Report report = HydraulicErosion.run(field, settings, checkCancelled);
            detail.append(String.format(Locale.ROOT, "%d gotas, %.0f bloques arrastrados", report.droplets(),
                    report.eroded()));
        }
        if (mode != Erosion.HYDRAULIC) {
            // Tras las gotas, una pasada térmica corta asienta las laderas que hayan quedado colgadas.
            int passes = mode == Erosion.FULL ? FULL_THERMAL_PASSES : (int) Math.round(iterations);
            // En la completa solo se desmoronan las paredes que dejan las cárcavas, sin borrarlas.
            double angle = mode == Erosion.FULL ? Math.min(85.0, talus + FULL_THERMAL_EXTRA_DEGREES) : talus;
            double moved = ThermalErosion.run(field, new ThermalErosion.Settings(Math.clamp(passes, 1, 2_000), angle,
                    Math.min(2.0, strength)), checkCancelled);
            if (!detail.isEmpty()) {
                detail.append(" · ");
            }
            detail.append(String.format(Locale.ROOT, "%.0f bloques resbalados por encima de %.0f°", moved, angle));
        }
        int changed = TerrainWriter.write(columns, field, null, palette, false, sink, checkCancelled);
        return new Summary(changed, detail.toString());
    }

    /** Repinta la superficie de lo seleccionado con la paleta, sin cambiar alturas. */
    public static Summary paint(TerrainView view, SurfacePalette palette, TerrainWriter.BlockSink sink,
                                Runnable checkCancelled) {
        TerrainColumns columns = TerrainColumns.read(view, checkCancelled);
        HeightField field = columns.heights();
        TerrainWriter.write(columns, field, null, palette, true, sink, checkCancelled);
        return new Summary(field.editableColumns(), palette.source());
    }
}
