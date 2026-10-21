package com.komainu.moxia.utility;

import com.komainu.moxia.region.RegionSnapshot;

import java.util.Set;

/**
 * Lo que necesitan saber la gravedad, la nieve y el agua de cada estado de la foto, por su id crudo. En el servidor
 * lo responde Minecraft ({@link MinecraftKinds}); en los tests, {@link #fromText} a partir del id y las
 * propiedades.
 */
public interface BlockKinds extends GravityEngine.Kinds {
    /** Agua sin bloque: fuente, corriente o columna de burbujas. */
    boolean water(int rawId);

    /** Fuente (nivel 0) de agua o lava. */
    boolean source(int rawId);

    /** Plantas que solo viven dentro del agua: algas y praderas marinas. */
    boolean waterOnly(int rawId);

    /** Si una capa de nieve puede estar encima (cara de arriba entera, sin hielo ni barrera). */
    boolean supportsSnow(int rawId);

    /** Capas de una capa de nieve (1–8); 0 si no lo es. */
    int snowLayers(int rawId);

    /** Si tiene la propiedad {@code snowy} (bloque de hierba, micelio, podsol). */
    boolean snowy(int rawId);

    /** Planta pequeña que la nieve puede tapar ({@code -p}): hierba, helechos, arbustos secos. */
    boolean smallPlant(int rawId);

    /**
     * Lo que el agua o la lava ocupan llevándoselo por delante: el aire, la hierba, las flores, las antorchas… La
     * misma regla que {@code FlowableFluid.canFill} de Minecraft para un bloque sin líquido: no vale lo que se
     * anega, las puertas, carteles, escaleras de mano, cañas, portales ni lo que frena el paso.
     */
    boolean washable(int rawId);

    /**
     * La propiedad {@code level} de un bloque de agua o de lava (0 fuente, 1–7 corriente, 8–15 cayendo), o -1 si el
     * bloque no es agua ni lava (las columnas de burbujas y lo anegado cuentan como fuentes de agua sin nivel).
     */
    int fluidLevel(int rawId);

    @Override
    default boolean fluid(int rawId) {
        return water(rawId) || lava(rawId);
    }

    Set<String> WATER_ONLY = Set.of("minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass",
            "minecraft:tall_seagrass");
    Set<String> SMALL_PLANTS = Set.of("minecraft:short_grass", "minecraft:grass", "minecraft:fern",
            "minecraft:dead_bush");
    /** Algunos bloques con datos, para la versión de texto. */
    Set<String> BLOCK_ENTITIES = Set.of("minecraft:chest", "minecraft:barrel", "minecraft:oak_sign",
            "minecraft:furnace", "minecraft:spawner");
    /** Bloques donde la nieve no se sostiene aunque su cara de arriba sea entera. */
    Set<String> NO_SNOW = Set.of("minecraft:ice", "minecraft:packed_ice", "minecraft:barrier");
    /** Lo que se lleva el agua en la versión de texto (además del aire). */
    Set<String> WASHABLE = Set.of("minecraft:short_grass", "minecraft:grass", "minecraft:fern",
            "minecraft:dead_bush", "minecraft:dandelion", "minecraft:poppy", "minecraft:torch", "minecraft:snow",
            "minecraft:vine");

    /**
     * Versión de texto, sin Minecraft: se guía por el id y las propiedades de la foto. {@code fullTop} dice qué
     * estados (id y propiedades) tienen la cara de arriba entera.
     */
    static BlockKinds fromText(RegionSnapshot snapshot, java.util.function.BiPredicate<String, String> fullTop) {
        return new BlockKinds() {
            private String id(int rawId) {
                return snapshot.stateInfo(rawId).blockId();
            }

            private String property(int rawId, String key) {
                String properties = snapshot.stateInfo(rawId).properties();
                if (properties == null) {
                    return null;
                }
                for (String pair : properties.split(",")) {
                    int equals = pair.indexOf('=');
                    if (equals > 0 && pair.substring(0, equals).equals(key)) {
                        return pair.substring(equals + 1);
                    }
                }
                return null;
            }

            @Override
            public boolean water(int rawId) {
                return id(rawId).equals("minecraft:water") || id(rawId).equals("minecraft:bubble_column");
            }

            @Override
            public boolean lava(int rawId) {
                return id(rawId).equals("minecraft:lava");
            }

            @Override
            public boolean source(int rawId) {
                String level = property(rawId, "level");
                return level == null || level.equals("0");
            }

            @Override
            public boolean waterOnly(int rawId) {
                return WATER_ONLY.contains(id(rawId));
            }

            @Override
            public boolean waterloggable(int rawId) {
                return property(rawId, "waterlogged") != null;
            }

            @Override
            public boolean waterlogged(int rawId) {
                return "true".equals(property(rawId, "waterlogged"));
            }

            @Override
            public boolean supportsSnow(int rawId) {
                String id = id(rawId);
                if (NO_SNOW.contains(id)) {
                    return false;
                }
                if (id.equals("minecraft:snow")) {
                    return "8".equals(property(rawId, "layers"));
                }
                return id.equals("minecraft:honey_block") || id.equals("minecraft:soul_sand")
                        || id.equals("minecraft:mud") || fullTop.test(id, snapshot.stateInfo(rawId).properties());
            }

            @Override
            public int snowLayers(int rawId) {
                if (!id(rawId).equals("minecraft:snow")) {
                    return 0;
                }
                String layers = property(rawId, "layers");
                return layers == null ? 1 : Integer.parseInt(layers);
            }

            @Override
            public boolean snowy(int rawId) {
                return property(rawId, "snowy") != null;
            }

            @Override
            public boolean smallPlant(int rawId) {
                return SMALL_PLANTS.contains(id(rawId));
            }

            @Override
            public boolean blockEntity(int rawId) {
                return BLOCK_ENTITIES.contains(id(rawId));
            }

            @Override
            public boolean washable(int rawId) {
                return snapshot.stateInfo(rawId).air() || WASHABLE.contains(id(rawId));
            }

            @Override
            public int fluidLevel(int rawId) {
                if (!id(rawId).equals("minecraft:water") && !id(rawId).equals("minecraft:lava")) {
                    return -1;
                }
                String level = property(rawId, "level");
                return level == null ? 0 : Integer.parseInt(level);
            }
        };
    }
}
