package com.komainu.moxia.edit;

import com.sk89q.worldedit.world.block.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Estados de WorldEdit de mentira para las pruebas: sin Minecraft no se pueden crear de verdad, pero el plan solo los
 * guarda y los compara por identidad (WorldEdit no redefine {@code equals} ni {@code hashCode}), así que basta con
 * objetos distintos. No se puede llamar a ningún método suyo.
 */
public final class FakeStates {
    private FakeStates() {
    }

    public static BlockState fake() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Object unsafe = field.get(null);
            Method allocate = unsafeClass.getMethod("allocateInstance", Class.class);
            return (BlockState) allocate.invoke(unsafe, BlockState.class);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static BlockState[] fakes(int count) {
        BlockState[] states = new BlockState[count];
        for (int index = 0; index < count; index++) {
            states[index] = fake();
        }
        return states;
    }
}
