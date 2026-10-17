package com.komainu.moxia.shape;

import java.util.Arrays;
import java.util.Locale;

public enum PathShape {
    CHAINLINK("chainlink", "Cadena", "Eslabones 3D alternados y entrelazados", 4, 5, 1,
            "chain", "cadena", "ch"),
    SCALES("scales", "Escamas", "Escamas 3D curvas, sólidas y solapadas", 12, 8, 2,
            "scale", "escamas", "escama", "dragon", "dragon_scales", "sc"),
    SPIKED_SCALES("spiked_scales", "Escamas espinadas", "Rombos separados con una espina elevada y núcleo sólido", 12, 5, 2,
            "spines", "spikes", "espinas", "scales_gap", "scalegap", "gapped_scales", "gappedscales", "escamas_gap", "rombo_scales"),
    BEADS("beads", "Cuentas", "Volúmenes redondeados espaciados sobre la trayectoria", 3, 5, 1,
            "beats", "cuentas", "be"),
    COIL("coil", "Espiral", "Hélice continua tipo resorte, cable o raíz", 7, 5, 2,
            "espiral", "resorte", "spring", "helix"),
    LIGHTNING("lightning", "Rayos", "Descarga 3D quebrada con ramificaciones deterministas; cerrada forma una corona hueca", 7, 5, 1,
            "rayos", "rayo", "bolt", "electric", "electricidad", "energy_ring"),
    CROWNS("crowns", "Coronas", "Bucles florales dentados que alternan su rotación", 9, 5, 2,
            "crown", "corona", "coronas", "flores", "flowers", "petals"),
    HONEYCOMB("honeycomb", "Panal", "Celdas hexagonales tridimensionales conectadas", 5, 5, 1,
            "panal", "honey", "ho"),
    FISHNET("fishnet", "Red", "Malla de hilos helicoidales cruzados que envuelve el recorrido", 6, 5, 1,
            "net", "red", "malla", "fi"),
    OSCILLATE("oscillate", "Oscilar", "Cinta volumétrica que ondula y gira", 4, 5, 1,
            "oscilar", "onda", "os"),
    RIB_LOOPS("ribloops", "Costillas", "Arcos abiertos repetidos con una espina longitudinal", 9, 5, 2,
            "ribs", "costillas", "costillar_loop", "arcos"),
    RINGS("rings", "Anillos", "Toros 3D configurables alrededor del recorrido", 5, 5, 1,
            "anillos", "ring", "ri"),
    TILES("tiles", "Cuadros 3D", "Placas cuadradas inclinadas que envuelven el recorrido", 12, 5, 2,
            "tile", "cuadros", "cuadros3d", "square_tiles", "placas"),
    HEX_ARMOR("hexarmor", "Armadura hex", "Paneles hexagonales elevados sobre una piel tubular", 12, 5, 2,
            "hex_armour", "hexagonal", "hextexture", "hex_textura"),
    VORONOI("voronoi", "Celdas Voronoi", "Malla orgánica de celdas irregulares con profundidad", 11, 5, 2,
            "voronoialized", "voronoialize", "cells", "celulas"),
    KNOTS("knots", "Nudos", "Nudos toroidales alternados para superficies biomecánicas", 7, 5, 2,
            "knot", "nudos", "biomech"),
    NOODLES("noodles", "Noodles", "Manojo de tubos orgánicos cruzados y texturizados", 9, 5, 2,
            "noodle", "fideos", "tentacles", "tentaculos"),
    MANDALA("mandala", "Mandala radial", "Rosetón 3D orientable con pétalos, anillos, filigrana y relieve concéntrico", 24, 6, 2,
            "mandalas", "rosette", "roseton", "radial", "sacred_geometry"),
    CLOUDS("clouds", "Nube orgánica", "Volumen nuboso natural con masas, lóbulos, coronas y estelas erosionadas", 18, 6, 2,
            "cloud", "nube", "nubes", "organic_cloud", "epic_cloud"),
    WEB("web", "Telaraña", "Puente fino entre nudos gruesos o telaraña clásica de radios rectos y arcos cóncavos", 8, 4, 1,
            "spiderweb", "cobweb", "telarana", "telaraña", "red_aracnida"),
    SWEEP("sweep", "Perfil 2D", "Perfil 2D (círculo, estrella, cuerda…) barrido en espacio de tubo, estanco y sin puentes", 6, 6, 1,
            "perfil", "profile", "tube", "tubo", "spline", "ezspline", "basic"),
    BRAID("braid", "Trenza", "Hebras en ocho que se cruzan por encima y por debajo a lo largo del recorrido", 6, 4, 2,
            "braids", "trenza", "trenzas", "br"),
    NOISE_TUBE("noisetube", "Tubo erosionado", "Tubo macizo cuya corteza muerde un ruido 3D que sigue la curva", 8, 4, 2,
            "noise_tube", "noise", "erosion", "erosionado", "rock", "roca", "ruido"),
    PILLAR("pillar", "Pilar monumental", "Columna épica entre P1 y P2 con base, fuste, nervaduras y capitel", 10, 6, 2,
            "pilar", "column", "columna", "epic_pillar", "monumental_pillar"),
    PILLAR_ARCHES("pillararches", "Pilares arqueados", "Dos pilares monumentales levantados desde P1 y P2 y unidos mediante un arco ornamentado", 9, 6, 2,
            "pillar_arches", "arched_pillars", "arcade", "pilares", "arcos_pilares"),
    TORII("torii", "Torii monumental", "Portal japonés gigante con pilares inclinados, vigas curvas, ménsulas y placa central", 9, 6, 2,
            "japanese_gate", "japanesegate", "arco_japones", "portal_japones", "torii_gate"),
    ARCH_BRIDGE("archbridge", "Puente arqueado", "Puente transitable con arco, pretiles y nervaduras", 9, 5, 2,
            "bridge", "puente", "puente_arqueado", "archedbridge");

    private final String id;
    private final String displayName;
    private final String description;
    private final int defaultRadius;
    private final int defaultSpacing;
    private final int defaultThickness;
    private final String[] aliases;

    PathShape(
            String id,
            String displayName,
            String description,
            int defaultRadius,
            int defaultSpacing,
            int defaultThickness,
            String... aliases
    ) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.defaultRadius = defaultRadius;
        this.defaultSpacing = defaultSpacing;
        this.defaultThickness = defaultThickness;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public ShapeParameters defaults() {
        return new ShapeParameters(this, defaultRadius, defaultSpacing, defaultThickness, 0);
    }

    public boolean usesHeight() {
        return this == ARCH_BRIDGE || this == PILLAR_ARCHES || this == TORII;
    }

    public boolean usesSecondaryMeasure() {
        return usesHeight() || this == CLOUDS;
    }

    public int defaultHeight() {
        if (this == PILLAR_ARCHES) {
            return 48;
        }
        if (this == CLOUDS) {
            return 12;
        }
        if (this == TORII) {
            return 96;
        }
        return 18;
    }

    public boolean isFigure() {
        return false;
    }

    public static PathShape byId(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(shape -> shape.id.equals(normalized)
                        || Arrays.asList(shape.aliases).contains(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Forma 3D desconocida: " + input));
    }
}
