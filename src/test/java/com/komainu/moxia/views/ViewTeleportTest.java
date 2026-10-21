package com.komainu.moxia.views;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que el servidor comprueba antes de llevar a alguien a una vista: el cliente propone, el servidor decide. */
class ViewTeleportTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final ViewTeleport.Destination WORLD = new ViewTeleport.Destination(true, -64, 320,
            (x, z) -> Math.abs(x) <= 1000 && Math.abs(z) <= 1000);
    private static final ViewTeleport.Rules RULES = ViewTeleport.Rules.DEFAULT;
    private static final long NOW = 10_000L;
    private static final long NEVER = Long.MIN_VALUE;

    private static ViewTeleport.Traveler builder() {
        return new ViewTeleport.Traveler(OVERWORLD, 0, 70, 0, true, true, false, false);
    }

    private static ViewTeleport.Target to(double x, double y, double z) {
        return new ViewTeleport.Target(OVERWORLD, x, y, z, 45.0F, 30.0F);
    }

    private static ViewTeleport.Verdict check(ViewTeleport.Target target, ViewTeleport.Traveler traveler) {
        return ViewTeleport.check(target, traveler, WORLD, RULES, NOW, NEVER);
    }

    @Test
    void aBuilderInCreativeCanJumpInsideTheWorld() {
        ViewTeleport.Verdict verdict = check(to(120.5, 90, -40), builder());
        assertTrue(verdict.allowed(), verdict.reason());
        assertEquals(120.5, verdict.target().x());
        // Ángulos fuera de rango se arreglan en lugar de rechazarse.
        ViewTeleport.Verdict wrapped = check(new ViewTeleport.Target(OVERWORLD, 0, 80, 0, 450.0F, -200.0F),
                builder());
        assertTrue(wrapped.allowed());
        assertEquals(90.0F, wrapped.target().yaw());
        assertEquals(-90.0F, wrapped.target().pitch());
    }

    @Test
    void permissionAndGameModeAreRequired() {
        ViewTeleport.Traveler noPermission = new ViewTeleport.Traveler(OVERWORLD, 0, 70, 0, true, false, true, true);
        assertFalse(check(to(1, 70, 1), noPermission).allowed());
        assertTrue(check(to(1, 70, 1), noPermission).reason().contains(ViewPermissions.TELEPORT));
        ViewTeleport.Traveler survival = new ViewTeleport.Traveler(OVERWORLD, 0, 70, 0, false, true, false, false);
        assertFalse(check(to(1, 70, 1), survival).allowed(), "en supervivencia no, de serie");
        ViewTeleport.Traveler admin = new ViewTeleport.Traveler(OVERWORLD, 0, 70, 0, false, true, false, true);
        assertTrue(check(to(1, 70, 1), admin).allowed(), "con komaedit.view.anymode sí");
        ViewTeleport.Rules anyMode = new ViewTeleport.Rules(true, false, 0, 0);
        assertTrue(ViewTeleport.check(to(1, 70, 1), survival, WORLD, anyMode, NOW, NEVER).allowed(),
                "si el servidor no pide creativo, vale");
        ViewTeleport.Rules off = new ViewTeleport.Rules(false, true, 0, 0);
        assertFalse(ViewTeleport.check(to(1, 70, 1), builder(), WORLD, off, NOW, NEVER).allowed());
    }

    @Test
    void anotherDimensionNeedsItsOwnPermissionAndMustExist() {
        ViewTeleport.Target nether = new ViewTeleport.Target(NETHER, 10, 64, 10, 0, 0);
        assertFalse(check(nether, builder()).allowed());
        assertTrue(check(nether, builder()).reason().contains(ViewPermissions.DIMENSION));
        ViewTeleport.Traveler traveler = new ViewTeleport.Traveler(OVERWORLD, 0, 70, 0, true, true, true, false);
        assertTrue(check(nether, traveler).allowed());
        ViewTeleport.Destination missing = new ViewTeleport.Destination(false, 0, 0, (x, z) -> false);
        ViewTeleport.Verdict verdict = ViewTeleport.check(new ViewTeleport.Target("mod:sueños", 0, 0, 0, 0, 0),
                traveler, missing, RULES, NOW, NEVER);
        assertFalse(verdict.allowed());
        assertTrue(verdict.reason().contains("no existe"));
    }

    @Test
    void outsideTheWorldIsRefused() {
        assertFalse(check(to(5000, 70, 0), builder()).allowed(), "fuera del borde");
        assertFalse(check(to(0, -64 - ViewTeleport.FLOOR_MARGIN - 1, 0), builder()).allowed(), "bajo el suelo");
        assertFalse(check(to(0, 320 + ViewTeleport.SKY_MARGIN + 1, 0), builder()).allowed(), "demasiado alto");
        assertTrue(check(to(0, 320 + 100, 0), builder()).allowed(), "una vista desde el cielo sí");
        assertFalse(check(to(Double.NaN, 70, 0), builder()).allowed());
        assertFalse(check(to(0, Double.POSITIVE_INFINITY, 0), builder()).allowed());
        assertFalse(check(new ViewTeleport.Target(OVERWORLD, 0, 70, 0, Float.NaN, 0), builder()).allowed());
    }

    @Test
    void distanceAndCooldownFollowTheRules() {
        ViewTeleport.Rules near = new ViewTeleport.Rules(true, true, 100, 500);
        assertTrue(ViewTeleport.check(to(60, 70, 60), builder(), WORLD, near, NOW, NEVER).allowed());
        ViewTeleport.Verdict far = ViewTeleport.check(to(90, 70, 90), builder(), WORLD, near, NOW, NEVER);
        assertFalse(far.allowed());
        assertTrue(far.reason().contains("127"), far.reason());
        assertFalse(ViewTeleport.check(to(1, 70, 1), builder(), WORLD, near, NOW, NOW - 100).allowed(),
                "espera entre saltos");
        assertTrue(ViewTeleport.check(to(1, 70, 1), builder(), WORLD, near, NOW, NOW - 500).allowed());
        // La distancia no cuenta al cambiar de dimensión.
        ViewTeleport.Traveler traveler = new ViewTeleport.Traveler(OVERWORLD, 0, 70, 0, true, true, true, false);
        assertTrue(ViewTeleport.check(new ViewTeleport.Target(NETHER, 900, 70, 900, 0, 0), traveler, WORLD, near,
                NOW, NEVER).allowed());
    }
}
