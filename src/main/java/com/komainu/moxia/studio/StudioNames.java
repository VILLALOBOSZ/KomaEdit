package com.komainu.moxia.studio;

import com.komainu.moxia.recipe.CurveEasing;
import com.komainu.moxia.recipe.MaterialMetric;
import com.komainu.moxia.recipe.SocketPlacement;
import com.komainu.moxia.recipe.SurfaceMode;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.tube.TubeEngine;
import com.komainu.moxia.shape.tube.TubeFormulaPreset;
import com.komainu.moxia.shape.tube.TubeSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Claves de traducción de lo que el Studio enseña de la lógica: figuras, modos, fórmulas, climas y las
 * etiquetas de los controles de proporciones, que cambian de nombre según la figura.
 *
 * <p>Los {@code displayName()} de los enums siguen en español porque los usan los comandos y el chat; la
 * pantalla siempre pasa por estas claves para salir en el idioma del cliente.</p>
 */
public final class StudioNames {
    private StudioNames() {
    }

    public static String shape(PathShape shape) {
        return "komaedit.shape." + shape.id();
    }

    public static String shapeDescription(PathShape shape) {
        return shape(shape) + ".description";
    }

    public static String surface(SurfaceMode mode) {
        return "komaedit.surface." + mode.id();
    }

    public static String easing(CurveEasing easing) {
        return "komaedit.easing." + easing.id();
    }

    public static String metric(MaterialMetric metric) {
        return "komaedit.metric." + metric.id();
    }

    public static String metricHelp(MaterialMetric metric) {
        return metric(metric) + ".help";
    }

    public static String socket(SocketPlacement placement) {
        return "komaedit.socket." + placement.id();
    }

    public static String smooth(SmoothMode mode) {
        return "komaedit.smooth." + mode.id();
    }

    public static String engine(TubeEngine engine) {
        return "komaedit.engine." + engine.id();
    }

    public static String formula(TubeFormulaPreset preset) {
        return "komaedit.formula." + preset.id();
    }

    public static String formulaDescription(TubeFormulaPreset preset) {
        return formula(preset) + ".description";
    }

    public static String profile(TubeSettings.TubeProfile profile) {
        return "komaedit.profile." + profile.id();
    }

    public static String end(TubeSettings.EndMode mode) {
        return "komaedit.end." + mode.id();
    }

    public static String normal(TubeSettings.NormalMode mode) {
        return "komaedit.normal." + mode.id();
    }

    /** Primer control de proporciones: el tamaño principal, que cada figura llama a su manera. */
    public static String radiusLabel(PathShape shape) {
        return param(switch (shape) {
            case SWEEP, BRAID, NOISE_TUBE, EXPRESSION -> "radius";
            case LIGHTNING -> "amplitude";
            case WEB -> "elasticity";
            case CLOUDS -> "size";
            case TORII -> "girth";
            case PILLAR, PILLAR_ARCHES -> "width";
            default -> "scale";
        });
    }

    public static String spacingLabel(PathShape shape) {
        return param(switch (shape) {
            case SWEEP -> "sides";
            case EXPRESSION -> "repetitions";
            case BRAID -> "braid_pitch";
            case NOISE_TUBE -> "frequency";
            case LIGHTNING -> "branching";
            case WEB -> "thread_gap";
            case CLOUDS -> "organicity";
            case TORII, PILLAR, PILLAR_ARCHES -> "detail";
            default -> "spacing";
        });
    }

    public static String thicknessLabel(PathShape shape) {
        return param(switch (shape) {
            case SWEEP, EXPRESSION -> "wall";
            case BRAID -> "strand";
            case NOISE_TUBE -> "depth";
            case LIGHTNING -> "core";
            case TORII, PILLAR, PILLAR_ARCHES -> "relief";
            case CLOUDS -> "density";
            default -> "thickness";
        });
    }

    public static String rollLabel(PathShape shape) {
        return param(switch (shape) {
            case LIGHTNING -> "chaos";
            case WEB -> "relief_3d";
            case TORII -> "curvature";
            default -> "roll";
        });
    }

    /** La medida secundaria: separación de las nubes o altura de las estructuras. */
    public static String heightLabel(PathShape shape) {
        return param(shape == PathShape.CLOUDS ? "separation" : "height");
    }

    private static String param(String name) {
        return "gui.komaedit.param." + name;
    }

    /** Todas las claves de esta clase, para comprobar que ambos idiomas las tienen. */
    public static List<String> allKeys() {
        List<String> keys = new ArrayList<>();
        for (PathShape shape : PathShape.values()) {
            keys.add(shape(shape));
            keys.add(shapeDescription(shape));
            keys.add(radiusLabel(shape));
            keys.add(spacingLabel(shape));
            keys.add(thicknessLabel(shape));
            keys.add(rollLabel(shape));
            keys.add(heightLabel(shape));
        }
        for (SurfaceMode mode : SurfaceMode.values()) {
            keys.add(surface(mode));
        }
        for (CurveEasing easing : CurveEasing.values()) {
            keys.add(easing(easing));
        }
        for (MaterialMetric metric : MaterialMetric.values()) {
            keys.add(metric(metric));
            keys.add(metricHelp(metric));
        }
        for (SocketPlacement placement : SocketPlacement.values()) {
            keys.add(socket(placement));
        }
        for (SmoothMode mode : SmoothMode.values()) {
            keys.add(smooth(mode));
        }
        for (TubeEngine engine : TubeEngine.values()) {
            keys.add(engine(engine));
        }
        for (TubeFormulaPreset preset : TubeFormulaPreset.values()) {
            keys.add(formula(preset));
            keys.add(formulaDescription(preset));
        }
        for (TubeSettings.TubeProfile profile : TubeSettings.TubeProfile.values()) {
            keys.add(profile(profile));
        }
        for (TubeSettings.EndMode mode : TubeSettings.EndMode.values()) {
            keys.add(end(mode));
        }
        for (TubeSettings.NormalMode mode : TubeSettings.NormalMode.values()) {
            keys.add(normal(mode));
        }
        return keys;
    }
}
