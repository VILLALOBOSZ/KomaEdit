package com.komainu.moxia.noise;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseSpecTest {
    @Test
    void parsesEzEditsNotationWithLongAndShortNames() {
        assertEquals(NoiseSpec.Type.VALUE, NoiseSpec.parse("Value(Seed:123,Frequency:0.04)").type());
        assertEquals(NoiseSpec.Type.CELLULAR, NoiseSpec.parse("Ce(cD:e,cR:sub,F:.1)").type());
        assertEquals(NoiseSpec.Type.PERLIN, NoiseSpec.parse("pe").type());
        assertEquals(NoiseSpec.Type.OPEN_SIMPLEX_2, NoiseSpec.parse("Si(fT:R,fO:4,fL:2,fG:0.5)").type());
        assertEquals(NoiseSpec.Type.OPEN_SIMPLEX_2S, NoiseSpec.parse("Sm(wT:S,wA:30,wF:0.02,wC:I)").type());
        assertEquals(NoiseSpec.Type.VALUE_CUBIC, NoiseSpec.parse("VC(x:2,y:0.5,z:1,i:true)").type());
    }

    @Test
    void defaultMappingStaysBetweenZeroAndOneAndVaries() {
        for (String input : new String[]{"Perlin(f:0.05)", "Si(f:0.05,fT:F,fO:3)", "Va(f:0.1)", "Ce(f:0.08)",
                "Ce(f:0.08,cR:edge)", "Ce(f:0.08,cR:sq)", "White(f:1)", "Gabor(f:0.05,gF:3,gN:4)"}) {
            NoiseSpec.NoiseField field = NoiseSpec.parse(input).build(42L);
            double minimum = 1.0;
            double maximum = 0.0;
            for (int index = 0; index < 4_000; index++) {
                double value = field.sample(index * 1.7, index * 0.3, index * 2.9);
                assertTrue(value >= 0.0 && value <= 1.0, input + " fuera de [0,1]: " + value);
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
            }
            assertTrue(maximum - minimum > 0.2, input + " apenas varía: " + minimum + "–" + maximum);
        }
    }

    @Test
    void sameSeedSameNoiseAndExplicitSeedWins() {
        NoiseSpec spec = NoiseSpec.parse("Perlin(f:0.03)");
        assertEquals(spec.build(7L).sample(10, 20, 30), spec.build(7L).sample(10, 20, 30), 0.0);
        assertNotEquals(spec.build(7L).sample(10, 20, 30), spec.build(8L).sample(10, 20, 30));
        NoiseSpec seeded = NoiseSpec.parse("Perlin(f:0.03,s:99)");
        assertEquals(seeded.build(1L).sample(5, 6, 7), seeded.build(2L).sample(5, 6, 7), 0.0);
    }

    @Test
    void overrideMappingAndInversionFollowEzEdits() {
        NoiseSpec.NoiseField plain = NoiseSpec.parse("Pe(f:0.05,s:3)").build(0L);
        NoiseSpec.NoiseField inverted = NoiseSpec.parse("Pe(f:0.05,s:3,i:true)").build(0L);
        NoiseSpec.NoiseField none = NoiseSpec.parse("Pe(f:0.05,s:3,m:no)").build(0L);
        NoiseSpec.NoiseField override = NoiseSpec.parse("Pe(f:0.05,s:3,m:OR,l:-0.5,u:0.5,Mn:0,Mx:1)").build(0L);
        for (int index = 0; index < 200; index++) {
            double x = index * 3.1;
            double raw = none.sample(x, 1, 2);
            assertEquals(1.0 - plain.sample(x, 1, 2), inverted.sample(x, 1, 2), 1.0E-6);
            assertEquals(Math.clamp(raw + 0.5, 0.0, 1.0), override.sample(x, 1, 2), 1.0E-6);
        }
    }

    @Test
    void rejectsUnknownTypesKeysAndValues() {
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("Plasma()"));
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("Perlin(q:1)"));
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("Perlin(f:abc)"));
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("Perlin(f:1"));
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("Ce(cD:m99)"));
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("Shard(h:0.5)"));
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("Pe(m:OR,l:1,u:1)"));
    }
}
