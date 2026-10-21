package com.komainu.moxia.feedback;

import com.komainu.moxia.placement.AlignmentGuide;
import com.komainu.moxia.shape.Vec3;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** El paquete del visualizador: ida y vuelta, y el redondeo que evita mandar lo mismo dos veces. */
class AlignmentGuidePayloadTest {
    private static AlignmentGuidePayload roundTrip(AlignmentGuidePayload payload) {
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        AlignmentGuidePayload.CODEC.encode(buffer, payload);
        return AlignmentGuidePayload.CODEC.decode(buffer);
    }

    @Test
    void travelsWithEveryVectorOrWithout() {
        AlignmentGuide.Result ready = new AlignmentGuide.Result(AlignmentGuide.State.READY, new Vec3(10.5, 64.5, -3.5),
                new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(0.5, 0, 0.5),
                true);
        assertEquals(AlignmentGuidePayload.of(ready), roundTrip(AlignmentGuidePayload.of(ready)));

        AlignmentGuide.Result waiting = new AlignmentGuide.Result(AlignmentGuide.State.WAITING,
                new Vec3(0.5, 70.5, 0.5), null, null, null, new Vec3(0, 1, 0), null, false);
        AlignmentGuidePayload back = roundTrip(AlignmentGuidePayload.of(waiting));
        assertEquals(AlignmentGuide.State.WAITING, back.guide().state());
        assertNull(back.guide().x());
        assertNull(back.guide().secondary());

        assertFalse(roundTrip(AlignmentGuidePayload.NONE).visible());
        assertEquals(AlignmentGuidePayload.NONE, AlignmentGuidePayload.of(null));
    }

    @Test
    void tinyWobblesDoNotCountAsAChange() {
        AlignmentGuide.Result first = new AlignmentGuide.Result(AlignmentGuide.State.READY, new Vec3(0.5, 64.5, 0.5),
                new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(1, 0, 0),
                false);
        AlignmentGuide.Result wobble = new AlignmentGuide.Result(AlignmentGuide.State.READY,
                new Vec3(0.5001, 64.5, 0.5), new Vec3(1, 0.0001, 0), new Vec3(-0.0001, 1, 0), new Vec3(0, 0, 1),
                new Vec3(0, 1, 0), new Vec3(1, 0, 0), false);
        AlignmentGuide.Result turned = new AlignmentGuide.Result(AlignmentGuide.State.READY, new Vec3(0.5, 64.5, 0.5),
                new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                false);

        assertEquals(AlignmentGuidePayload.of(first), AlignmentGuidePayload.of(wobble));
        assertNotEquals(AlignmentGuidePayload.of(first), AlignmentGuidePayload.of(turned));
    }
}
