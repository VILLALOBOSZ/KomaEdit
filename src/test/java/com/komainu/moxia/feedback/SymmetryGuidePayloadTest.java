package com.komainu.moxia.feedback;

import com.komainu.moxia.symmetry.SymmetryMode;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El cliente dibuja un brazo de la guía radial por cada uno que diga el servidor, en cada fotograma: con dos mil
 * millones, el fotograma no acababa nunca.
 */
class SymmetryGuidePayloadTest {
    private static SymmetryGuidePayload withArms(int arms) {
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        buffer.writeBoolean(true);
        buffer.writeByte(1);
        buffer.writeVarInt(arms);
        buffer.writeInt(0);
        buffer.writeInt(64);
        buffer.writeInt(0);
        return SymmetryGuidePayload.CODEC.decode(buffer);
    }

    @Test
    void theArmsComeWithinWhatExists() {
        assertEquals(SymmetryMode.MAX_ARMS, withArms(Integer.MAX_VALUE).arms());
        assertEquals(0, withArms(-5).arms());
        assertEquals(6, withArms(6).arms(), "lo normal llega igual");
    }
}
