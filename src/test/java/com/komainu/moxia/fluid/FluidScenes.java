package com.komainu.moxia.fluid;

import com.komainu.moxia.utility.TestWorld;

import java.util.ArrayList;
import java.util.List;

/** Escenas de texto para las herramientas de agua y cómo aplicarles lo escrito. */
final class FluidScenes {
    static final Runnable NOTHING = () -> {
    };

    private FluidScenes() {
    }

    /**
     * Una piscina de ladrillo: suelo de piedra hasta y=0 en todo el mundo (x, z de -6 a 12) y paredes de y=1 a y=4
     * alrededor de un hueco de 5×5 (x, z de 1 a 5). Fuera, aire hasta arriba (y=9).
     */
    static TestWorld pool() {
        TestWorld world = new TestWorld(-6, -6, -6, 19, 16, 19);
        world.fill(-6, -6, -6, 12, 0, 12, "stone");
        world.fill(0, 1, 0, 6, 4, 6, "stone_bricks");
        world.fill(1, 1, 1, 5, 4, 5, "air");
        world.selectInside(0);
        return world;
    }

    /** Un acantilado: hasta y=10 para x ≤ 10 y suelo en y=0 para x > 10 (y de -3 a 14, x de 0 a 30, z de -10 a 20). */
    static TestWorld cliff() {
        TestWorld world = new TestWorld(0, -3, -10, 31, 18, 31);
        world.fill(0, -3, -10, 30, 0, 20, "stone");
        world.fill(0, 1, -10, 10, 9, 20, "stone");
        world.fill(0, 10, -10, 10, 10, 20, "grass_block[snowy=false]");
        world.selectInside(0);
        return world;
    }

    /** El texto de bloque de lo escrito: el líquido con su nivel, aire, anegado o un bloque de la foto. */
    static String text(TestWorld world, FluidKind fluid, int code, int x, int y, int z) {
        if (code == FluidWrites.AIR) {
            return TestWorld.AIR;
        }
        if (code == FluidWrites.WATERLOG) {
            return TestWorld.withProperty(world.at(x, y, z), "waterlogged", "true");
        }
        if (FluidWrites.isBlock(code)) {
            return world.state(FluidWrites.rawId(code));
        }
        return switch (fluid) {
            case WATER -> "minecraft:water[level=" + code + "]";
            case LAVA -> "minecraft:lava[level=" + code + "]";
            case POWDER_SNOW -> "minecraft:powder_snow";
        };
    }

    /** Aplica lo escrito al mundo (todo de una vez, con los estados de la foto de antes). */
    static void apply(TestWorld world, FluidWrites writes) {
        List<Object[]> pending = new ArrayList<>();
        writes.forEach((x, y, z, code) -> pending.add(new Object[]{x, y, z,
                text(world, writes.fluid(), code, x, y, z)}));
        for (Object[] write : pending) {
            world.set((int) write[0], (int) write[1], (int) write[2], (String) write[3]);
        }
    }

    /** Lo que escribe un relleno, como lo convierte el servidor. */
    static FluidWrites writes(FloodFill.Result result, FluidKind fluid) {
        FluidWrites writes = new FluidWrites(fluid);
        result.write(new FloodFill.Sink() {
            @Override
            public void source(int x, int y, int z) {
                writes.fluid(x, y, z, 0);
            }

            @Override
            public void waterlog(int x, int y, int z) {
                writes.waterlog(x, y, z);
            }
        });
        return writes;
    }

    static boolean water(TestWorld world, int x, int y, int z) {
        return world.id(x, y, z).equals("minecraft:water");
    }
}
