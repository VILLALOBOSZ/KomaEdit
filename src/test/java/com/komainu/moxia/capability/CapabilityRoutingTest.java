package com.komainu.moxia.capability;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.komainu.moxia.capability.ClickRouting.Held;
import static com.komainu.moxia.capability.ClickRouting.Intent;
import static com.komainu.moxia.capability.ClickRouting.Target;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityRoutingTest {
    private static ClickRouting.Context context(Set<BuildCapability> on, Held held, Target target,
                                                boolean sameBlock, boolean smearPending) {
        return new ClickRouting.Context(on, held, target, sameBlock, smearPending, true);
    }

    @Test
    void nothingActiveLeavesEveryClickToMinecraft() {
        Set<BuildCapability> none = Set.of();
        for (Held held : Held.values()) {
            for (Target target : Target.values()) {
                assertEquals(Intent.NONE, ClickRouting.use(context(none, held, target, false, false)));
                assertEquals(Intent.NONE, ClickRouting.attack(context(none, held, target, false, false)));
            }
        }
    }

    @Test
    void komaEditToolsKeepTheirOwnClicks() {
        Set<BuildCapability> all = EnumSet.allOf(BuildCapability.class);
        assertEquals(Intent.NONE, ClickRouting.use(context(all, Held.TOOL, Target.BLOCK, false, true)));
        assertEquals(Intent.NONE, ClickRouting.attack(context(all, Held.TOOL, Target.BLOCK, false, true)));
    }

    @Test
    void rightClickFollowsTheDocumentedOrder() {
        Set<BuildCapability> builder = EnumSet.of(BuildCapability.REPLACE, BuildCapability.TWEAK,
                BuildCapability.EXTRUDE, BuildCapability.NO_UPDATES, BuildCapability.ANGEL);
        assertEquals(Intent.TWEAK, ClickRouting.use(context(builder, Held.EMPTY, Target.BLOCK, false, false)));
        assertEquals(Intent.EXTRUDE, ClickRouting.use(context(builder, Held.BLOCK, Target.BLOCK, true, false)));
        assertEquals(Intent.REPLACE, ClickRouting.use(context(builder, Held.BLOCK, Target.BLOCK, false, false)));
        assertEquals(Intent.PLACE, ClickRouting.use(context(builder, Held.FLUID, Target.BLOCK, false, false)));
        assertEquals(Intent.ANGEL, ClickRouting.use(context(builder, Held.BLOCK, Target.AIR, false, false)));
        assertEquals(Intent.ANGEL, ClickRouting.use(context(builder, Held.FLUID, Target.AIR, false, false)));
        assertEquals(Intent.NONE, ClickRouting.use(context(builder, Held.OTHER, Target.BLOCK, false, false)));
        assertEquals(Intent.NONE, ClickRouting.use(context(builder, Held.EMPTY, Target.AIR, false, false)));
        assertEquals(Intent.NONE, ClickRouting.use(context(builder, Held.BLOCK, Target.ENTITY, false, false)));
    }

    @Test
    void sameBlockWithoutExtrudeFallsToPlacement() {
        Set<BuildCapability> replace = EnumSet.of(BuildCapability.REPLACE);
        assertEquals(Intent.NONE, ClickRouting.use(context(replace, Held.BLOCK, Target.BLOCK, true, false)));
        Set<BuildCapability> quiet = EnumSet.of(BuildCapability.REPLACE, BuildCapability.NO_UPDATES);
        assertEquals(Intent.PLACE, ClickRouting.use(context(quiet, Held.BLOCK, Target.BLOCK, true, false)));
    }

    @Test
    void smearTakesEveryRightClickWhileActive() {
        Set<BuildCapability> smear = EnumSet.of(BuildCapability.SMEAR, BuildCapability.REPLACE);
        assertEquals(Intent.SMEAR_START, ClickRouting.use(context(smear, Held.BLOCK, Target.BLOCK, false, false)));
        assertEquals(Intent.SMEAR_END, ClickRouting.use(context(smear, Held.EMPTY, Target.AIR, false, true)));
        assertEquals(Intent.SMEAR_END, ClickRouting.use(context(smear, Held.BLOCK, Target.BLOCK, false, true)));
        assertEquals(Intent.NONE, ClickRouting.use(context(smear, Held.EMPTY, Target.AIR, false, false)));
        assertEquals(Intent.SMEAR_CANCEL, ClickRouting.attack(context(smear, Held.EMPTY, Target.AIR, false, true)));
    }

    @Test
    void leftClickShrinksOrBreaksQuietly() {
        Set<BuildCapability> on = EnumSet.of(BuildCapability.EXTRUDE, BuildCapability.NO_UPDATES);
        assertEquals(Intent.SHRINK, ClickRouting.attack(context(on, Held.BLOCK, Target.BLOCK, true, false)));
        assertEquals(Intent.BREAK, ClickRouting.attack(context(on, Held.EMPTY, Target.BLOCK, false, false)));
        assertEquals(Intent.NONE, ClickRouting.attack(context(on, Held.EMPTY, Target.AIR, false, false)));
        Set<BuildCapability> extrude = EnumSet.of(BuildCapability.EXTRUDE);
        assertEquals(Intent.NONE, ClickRouting.attack(context(extrude, Held.EMPTY, Target.BLOCK, false, false)));
    }

    @Test
    void swapKeyPicksTheTweakPropertyOnlyWithBothHandsEmpty() {
        Set<BuildCapability> tweak = EnumSet.of(BuildCapability.TWEAK);
        assertEquals(Intent.TWEAK_PROPERTY, ClickRouting.swap(context(tweak, Held.EMPTY, Target.BLOCK, false,
                false)));
        assertEquals(Intent.NONE, ClickRouting.swap(new ClickRouting.Context(tweak, Held.EMPTY, Target.BLOCK,
                false, false, false)));
        assertEquals(Intent.NONE, ClickRouting.swap(context(tweak, Held.BLOCK, Target.BLOCK, false, false)));
        assertEquals(Intent.NONE, ClickRouting.swap(context(Set.of(), Held.EMPTY, Target.BLOCK, false, false)));
    }

    @Test
    void multiBlockActionsArePressOnly() {
        for (Intent intent : Intent.values()) {
            if (ClickRouting.multiBlock(intent)) {
                assertTrue(ClickRouting.pressOnly(intent), intent.name());
            }
        }
        assertFalse(ClickRouting.pressOnly(Intent.REPLACE));
        assertFalse(ClickRouting.pressOnly(Intent.ANGEL));
    }

    @Test
    void namesAliasesAndMasksRoundTrip() {
        assertEquals(BuildCapability.NO_UPDATES, BuildCapability.byName("sin_actualizar"));
        assertEquals(BuildCapability.FAST_PLACE, BuildCapability.byName("FastPlace"));
        assertEquals(BuildCapability.SMEAR, BuildCapability.byName("arrastrar"));
        assertNull(BuildCapability.byName("bulldozer"));
        Set<BuildCapability> some = EnumSet.of(BuildCapability.REPLACE, BuildCapability.REACH);
        assertEquals(some, CapabilitySettings.fromMask(CapabilitySettings.mask(some)));
    }

    @Test
    void settingsSurviveSavingAndIgnoreJunk() {
        CapabilitySettings settings = CapabilitySettings.DEFAULT
                .with(BuildCapability.EXTRUDE, true)
                .with(BuildCapability.ANGEL, true)
                .withReplaceFamily(true)
                .withExtrudeLimit(900)
                .withAngelDistance(7)
                .withFlySpeed(35)
                .withTweakProperty("half");
        assertEquals(settings, CapabilitySettings.fromJson(settings.toJson()));
        CapabilitySettings junk = CapabilitySettings.fromJson(JsonParser.parseString(
                "{\"enabled\":[\"replace\",\"teleport\",3],\"extrudeLimit\":\"lots\",\"angelDistance\":900}"
        ).getAsJsonObject());
        assertEquals(Set.of(BuildCapability.REPLACE), junk.enabled());
        assertEquals(CapabilitySettings.DEFAULT.extrudeLimit(), junk.extrudeLimit());
        assertEquals(64, junk.angelDistance());
        assertThrows(IllegalArgumentException.class, () -> CapabilitySettings.fromJson(
                JsonParser.parseString("{\"format\":99}").getAsJsonObject()));
        assertEquals(CapabilitySettings.DEFAULT.with(BuildCapability.TWEAK, true).with(BuildCapability.TWEAK, false),
                CapabilitySettings.DEFAULT);
    }

    @Test
    void serverRulesGateByPermissionModeReachAndRate() {
        assertTrue(CapabilityRules.denial(BuildCapability.REPLACE, true, false, true).isEmpty());
        assertTrue(CapabilityRules.denial(BuildCapability.REPLACE, false, true, true).isEmpty());
        assertTrue(CapabilityRules.denial(BuildCapability.REPLACE, false, false, true).orElseThrow()
                .contains("creativo"));
        assertTrue(CapabilityRules.denial(BuildCapability.REPLACE, true, true, false).orElseThrow()
                .contains("komaedit.capability.replace"));

        CapabilityLimits limits = new CapabilityLimits(32, 4096, 64, 100, 2);
        assertEquals(32, CapabilityRules.reach(60, limits));
        assertEquals(27.0, CapabilityRules.reachBonus(32, limits, 5.0), 1.0E-9);
        assertEquals(0.0, CapabilityRules.reachBonus(5, limits, 6.0), 1.0E-9);
        assertEquals(5, CapabilityRules.angelDistance(12, 5.0, limits));
        assertEquals(1, CapabilityRules.angelDistance(0, 5.0, limits));
        assertEquals(100, CapabilityRules.flySpeed(500, limits));

        Cell block = new Cell(10, 64, 0);
        assertTrue(CapabilityRules.withinReach(5.5, 65.0, 0.5, block, 4.5));
        assertFalse(CapabilityRules.withinReach(0.5, 65.0, 0.5, block, 4.5));
        assertTrue(CapabilityRules.withinReach(0.5, 65.0, 0.5, block, 20.0));

        CapabilityRules.Throttle throttle = new CapabilityRules.Throttle();
        assertTrue(throttle.allow(100, false, 2));
        assertTrue(throttle.allow(100, false, 2));
        assertFalse(throttle.allow(100, false, 2));
        assertTrue(throttle.allow(101, false, 2));
        assertTrue(throttle.allow(101, true, 2));
        assertFalse(throttle.allow(103, true, 2));
        assertTrue(throttle.allow(105, true, 2));
        assertThrows(IllegalArgumentException.class, () -> new CapabilityLimits(80, 1, 1, 10, 1));
    }
}
