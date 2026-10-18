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
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("Ce(cD:m7)"));
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("Ce(cR:n,cN:Gabor)"));
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("Pe(wT:spiral)"));
        assertThrows(IllegalArgumentException.class, () -> NoiseSpec.parse("Pe(m:OR,l:1,u:1)"));
    }

    @Test
    void ezEditsExtrasStayBetweenZeroAndOneAndVary() {
        for (String input : new String[]{"Shard(f:0.05)", "Sh(f:0.05,h:1)", "Sh(f:0.05,h:0,fT:F,fO:3)",
                "Ce(f:0.08,cD:m1)", "Ce(f:0.08,cD:m4,cR:2sub)", "Ce(f:0.08,cD:m99,cR:edge)",
                "Ce(f:0.08,cD:r)", "Ce(f:0.08,cR:rounded)", "Ce(f:0.08,cR:r,fT:R,fO:3)",
                "Ce(cD:e,cR:n,cN:Pe,cF:.2,F:.1)", "Pe(f:0.05,wT:F,wA:20)", "Pe(f:0.05,wT:T,wA:20,wO:3)"}) {
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
    void noiseLookupGivesOneValuePerCell() {
        // Con la frecuencia de búsqueda alta, cada celda toma un valor y dentro de ella no cambia.
        NoiseSpec.NoiseField lookup = NoiseSpec.parse("Ce(F:0.1,cR:n,cN:Pe,cF:3.7,cJ:0)").build(5L);
        NoiseSpec.NoiseField cells = NoiseSpec.parse("Ce(F:0.1,cR:cell,cJ:0)").build(5L);
        double first = lookup.sample(1, 1, 1);
        assertEquals(first, lookup.sample(3, 2, 1), 1.0E-9);
        assertEquals(cells.sample(1, 1, 1), cells.sample(3, 2, 1), 1.0E-9);
        assertNotEquals(first, lookup.sample(41, 1, 1));
    }

    @Test
    void minkowskiDistanceGoesFromManhattanToChebyshev() {
        assertEquals(3.0, ExtendedNoise.minkowski(1, 1, 1, 1), 1.0E-9);
        assertEquals(Math.sqrt(2.0), ExtendedNoise.minkowski(1, 1, 0, 2), 1.0E-9);
        assertEquals(1.0, ExtendedNoise.minkowski(1, 1, 1, 99), 0.02);
        assertEquals(0.0, ExtendedNoise.minkowski(0, 0, 0, 4), 0.0);
    }

    @Test
    void shardSharpnessSoftensTheEdges() {
        NoiseSpec.NoiseField sharp = NoiseSpec.parse("Shard(f:0.1,h:1,s:4)").build(0L);
        NoiseSpec.NoiseField soft = NoiseSpec.parse("Shard(f:0.1,h:0,s:4)").build(0L);
        assertTrue(maximumJump(sharp) > maximumJump(soft), "la nitidez alta debe dejar saltos más bruscos");
    }

    private static double maximumJump(NoiseSpec.NoiseField field) {
        double jump = 0.0;
        double previous = field.sample(0, 0.5, 0.5);
        for (int index = 1; index < 2_000; index++) {
            double value = field.sample(index * 0.25, 0.5, 0.5);
            jump = Math.max(jump, Math.abs(value - previous));
            previous = value;
        }
        return jump;
    }
}
