package com.komainu.moxia.studio;

import com.komainu.moxia.recipe.RecipeExtensions;
import com.komainu.moxia.recipe.SocketPlacement;
import com.komainu.moxia.recipe.SurfaceMode;
import com.komainu.moxia.shape.PathShape;

import java.util.Arrays;
import java.util.Locale;

/**
 * Las variaciones de una figura a lo largo del camino: lo que antes era la etapa Modificadores, con nombres que dicen
 * lo que hacen. Cada una se enciende con un valor que se nota a la primera y se apaga dejando la figura como era; por
 * debajo siguen siendo los números de {@link RecipeExtensions}, así que las recetas de antes se leen igual.
 *
 * <p>Fuera del Studio quedaron la curva de reparto de la escala (pasa a «Cómo cambia», en Más ajustes) y la limpieza
 * (Cerrar huecos y Quitar bloques sueltos, también en Más ajustes): no cambian la forma, solo la rematan.</p>
 *
 * <p>Textos: {@code gui.komaedit.variation.<id>} (nombre) y {@code ….effect} (qué le pasa a la figura).</p>
 */
public enum FigureVariation {
    /** Más fina (o más gruesa) hacia el final, con sus piezas más juntas o más separadas. */
    TAPER("taper"),
    /** La figura gira sobre sí misma a lo largo del camino. */
    TWIST("twist"),
    /** Cada pocos tramos sale otra figura encima (anillos en una cuerda). */
    ACCENT("accent"),
    /** Los puntos bajan al suelo (o suben al techo) y la figura sigue el relieve. */
    GROUND("ground"),
    /** Lo copiado con {@code //copy} se repite a lo largo del camino (farolas, postes). */
    COPIED("copied");

    /** El tamaño al final con el que se enciende Afinar. */
    public static final int TAPER_END = 40;
    /** Las vueltas con las que se enciende Retorcer: una entera. */
    public static final int TWIST_DEGREES = 360;

    private final String id;

    FigureVariation(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String nameKey() {
        return "gui.komaedit.variation." + id;
    }

    public String effectKey() {
        return nameKey() + ".effect";
    }

    /** Si la variación está puesta en la receta. */
    public boolean on(RecipeExtensions extensions) {
        return switch (this) {
            case TAPER -> extensions.startScalePercent() != 100 || extensions.endScalePercent() != 100
                    || extensions.startSpacingPercent() != 100 || extensions.endSpacingPercent() != 100;
            case TWIST -> extensions.twistDegrees() != 0;
            case ACCENT -> extensions.rhythmEnabled();
            case GROUND -> extensions.surfaceMode() != SurfaceMode.NONE;
            case COPIED -> extensions.socketsEnabled();
        };
    }

    /**
     * La receta con la variación puesta y un valor que se ve a la primera. Si ya estaba puesta, no cambia nada (no se
     * pierde lo que el jugador ajustó).
     */
    public RecipeExtensions enable(RecipeExtensions extensions, PathShape shape) {
        if (on(extensions)) {
            return extensions;
        }
        return switch (this) {
            case TAPER -> extensions.withCurve(100, TAPER_END, 100, 100, extensions.twistDegrees(),
                    extensions.easing());
            case TWIST -> extensions.withCurve(extensions.startScalePercent(), extensions.endScalePercent(),
                    extensions.startSpacingPercent(), extensions.endSpacingPercent(), TWIST_DEGREES,
                    extensions.easing());
            case ACCENT -> extensions.withRhythm(true, accentFor(shape, extensions.accentShape()),
                    extensions.rhythmInterval());
            case GROUND -> extensions.withSurface(SurfaceMode.FLOOR, extensions.conformRadius(),
                    extensions.surfaceOffset());
            case COPIED -> extensions.withSockets(true, extensions.socketPlacement() == SocketPlacement.ENDPOINTS
                    ? SocketPlacement.INTERVAL : extensions.socketPlacement(), extensions.socketInterval());
        };
    }

    /** La receta sin la variación: vuelve a lo de serie lo suyo y nada más. */
    public RecipeExtensions disable(RecipeExtensions extensions) {
        RecipeExtensions defaults = RecipeExtensions.DEFAULT;
        return switch (this) {
            case TAPER -> extensions.withCurve(100, 100, 100, 100, extensions.twistDegrees(), extensions.easing());
            case TWIST -> extensions.withCurve(extensions.startScalePercent(), extensions.endScalePercent(),
                    extensions.startSpacingPercent(), extensions.endSpacingPercent(), 0, extensions.easing());
            case ACCENT -> extensions.withRhythm(false, extensions.accentShape(), extensions.rhythmInterval());
            case GROUND -> extensions.withSurface(SurfaceMode.NONE, defaults.conformRadius(), defaults.surfaceOffset());
            case COPIED -> extensions.withSockets(false, extensions.socketPlacement(), extensions.socketInterval());
        };
    }

    public RecipeExtensions toggle(RecipeExtensions extensions, PathShape shape) {
        return on(extensions) ? disable(extensions) : enable(extensions, shape);
    }

    /**
     * Si el holograma del Studio la enseña al momento. Lo copiado necesita el portapapeles del servidor y «lo más
     * cercano» busca en todas direcciones: esas se ven en el fantasma al construir.
     */
    public boolean immediate(RecipeExtensions extensions) {
        return switch (this) {
            case COPIED -> false;
            case GROUND -> extensions.surfaceMode() != SurfaceMode.NEAREST;
            default -> true;
        };
    }

    /**
     * Si la variación sirve en esa figura. Las estructuras (pilares, arcos, puentes, torii) se levantan entre dos
     * puntos con su propio diseño: afinarlas, retorcerlas o intercalarles otra figura las rompe, así que solo se
     * pueden pegar al terreno. Una variación que ya lleva la receta se enseña siempre, para poder quitarla.
     */
    public boolean fits(PathShape shape, RecipeExtensions extensions) {
        return this == GROUND || on(extensions) || ShapeCategory.of(shape) != ShapeCategory.STRUCTURES;
    }

    /** Cuántas variaciones lleva la receta. */
    public static long active(RecipeExtensions extensions) {
        return Arrays.stream(values()).filter(variation -> variation.on(extensions)).count();
    }

    /** La figura que se intercala: la de la receta si es otra, o unos anillos (cuentas sobre anillos). */
    static PathShape accentFor(PathShape shape, PathShape current) {
        if (current != shape) {
            return current;
        }
        return shape == PathShape.RINGS ? PathShape.BEADS : PathShape.RINGS;
    }

    public static FigureVariation byId(String id) {
        for (FigureVariation variation : values()) {
            if (variation.id.equals(id.toLowerCase(Locale.ROOT))) {
                return variation;
            }
        }
        return null;
    }
}
