package com.komainu.moxia.rebuild;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Reparte los bloques de la obra en las fases de {@link BuildPhase}, como los pondría un constructor:
 *
 * <ol>
 *     <li><b>Cimientos:</b> lo macizo del suelo de la obra. Se busca de abajo arriba la última capa seguida que cubre
 *     al menos el {@value #GROUND_COVER_PERCENT} % de las columnas de la obra (el suelo, el terreno de debajo); la
 *     primera capa cuenta siempre.</li>
 *     <li><b>Estructura:</b> troncos y pilares por encima de los cimientos.</li>
 *     <li><b>Paredes:</b> el resto de cubos enteros, y los cristales finos y barrotes que las rellenan.</li>
 *     <li><b>Tejado:</b> escaleras y losas sin nada macizo encima en su columna (las de dentro, bajo techo, son
 *     detalles).</li>
 *     <li><b>Detalles:</b> todo lo demás: vallas, puertas, antorchas, faroles, alfombras, muebles, líquidos.</li>
 *     <li><b>Vegetación:</b> hojas, flores, hierba, enredaderas y cultivos.</li>
 * </ol>
 *
 * Solo mira la forma de cada bloque ({@link BlockTraits}) y dónde está. Java puro.
 */
public final class BuilderPhases {
    static final int GROUND_COVER_PERCENT = 60;

    private BuilderPhases() {
    }

    /** La fase de cada entrada del modelo (las de aire quedan en {@link BuildPhase#FOUNDATION}, no se usan). */
    public static BuildPhase[] classify(RebuildModel model) {
        BuildPhase[] phases = new BuildPhase[model.size()];
        int ground = groundLevel(model);
        // Lo más alto con algo macizo (cubo, escalera, losa o panel) en cada columna, para saber qué está a cubierto.
        Long2IntOpenHashMap coverTop = new Long2IntOpenHashMap();
        coverTop.defaultReturnValue(Integer.MIN_VALUE);
        for (int entry = 0; entry < model.size(); entry++) {
            if (covers(model.traits(model.target(entry)))) {
                long column = column(model.x(entry), model.z(entry));
                coverTop.put(column, Math.max(coverTop.get(column), model.y(entry)));
            }
        }
        for (int entry = 0; entry < model.size(); entry++) {
            int palette = model.target(entry);
            BlockTraits traits = model.traits(palette);
            int y = model.y(entry);
            BuildPhase phase;
            if (traits.air()) {
                phase = BuildPhase.FOUNDATION;
            } else if (traits.vegetation()) {
                phase = BuildPhase.VEGETATION;
            } else if (y <= ground && !traits.fluid() && traits.shape() != BlockTraits.Shape.OTHER) {
                phase = BuildPhase.FOUNDATION;
            } else if (traits.pillar()) {
                phase = BuildPhase.STRUCTURE;
            } else if (traits.solid() || traits.shape() == BlockTraits.Shape.PANE) {
                phase = BuildPhase.WALLS;
            } else if ((traits.shape() == BlockTraits.Shape.STAIRS || traits.shape() == BlockTraits.Shape.SLAB)
                    && coverTop.get(column(model.x(entry), model.z(entry))) <= y) {
                phase = BuildPhase.ROOF;
            } else {
                phase = BuildPhase.DETAILS;
            }
            phases[entry] = phase;
        }
        return phases;
    }

    /**
     * La altura del suelo de la obra: la última capa, desde abajo y sin saltos, que cubre al menos el
     * {@value #GROUND_COVER_PERCENT} % de sus columnas con bloques macizos. Como mínimo, la primera capa.
     */
    static int groundLevel(RebuildModel model) {
        if (model.buildable() == 0) {
            return model.minY();
        }
        LongOpenHashSet columns = new LongOpenHashSet();
        int height = model.maxY() - model.minY() + 1;
        int[] solidPerLayer = new int[height];
        for (int entry = 0; entry < model.size(); entry++) {
            BlockTraits traits = model.traits(model.target(entry));
            if (traits.air()) {
                continue;
            }
            columns.add(column(model.x(entry), model.z(entry)));
            if (traits.solid()) {
                solidPerLayer[model.y(entry) - model.minY()]++;
            }
        }
        int ground = model.minY();
        for (int layer = 0; layer < height; layer++) {
            if (solidPerLayer[layer] * 100L < (long) columns.size() * GROUND_COVER_PERCENT) {
                break;
            }
            ground = model.minY() + layer;
        }
        return ground;
    }

    private static boolean covers(BlockTraits traits) {
        return !traits.air() && !traits.vegetation() && !traits.fluid() && traits.shape() != BlockTraits.Shape.OTHER;
    }

    private static long column(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }
}
