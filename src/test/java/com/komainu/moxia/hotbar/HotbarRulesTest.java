package com.komainu.moxia.hotbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que el servidor deja hacer al cambiar de barra según el modo de juego y los permisos. */
class HotbarRulesTest {
    @Test
    void creativePutsTheHotbarAsItIs() {
        assertEquals(HotbarRules.Mode.SET, HotbarRules.decide(true, true, false, false).mode());
    }

    @Test
    void survivalOnlyReordersWhatThePlayerHas() {
        HotbarRules.Decision decision = HotbarRules.decide(true, false, false, false);
        assertEquals(HotbarRules.Mode.REORDER, decision.mode());
        assertTrue(decision.allowed());
    }

    @Test
    void theGivePermissionWorksLikeCreativeOutsideCreativeButSaysSo() {
        HotbarRules.Decision give = HotbarRules.decide(true, false, false, true);
        assertEquals(HotbarRules.Mode.GIVE, give.mode(), "un modo propio, para avisar de que crea objetos");
        assertTrue(give.sets());
        assertEquals(HotbarRules.Mode.SET, HotbarRules.decide(true, true, false, true).mode(),
                "en creativo es lo de siempre");
        assertFalse(HotbarRules.decide(true, false, false, false).sets());
    }

    @Test
    void spectatorsAndPlayersWithoutPermissionCannotSwitch() {
        HotbarRules.Decision spectator = HotbarRules.decide(true, false, true, false);
        assertEquals(HotbarRules.Mode.DENIED, spectator.mode());
        assertFalse(spectator.allowed());
        assertTrue(spectator.reason().contains("espectador"));

        HotbarRules.Decision withoutPermission = HotbarRules.decide(false, true, false, true);
        assertEquals(HotbarRules.Mode.DENIED, withoutPermission.mode(), "ni en creativo ni con el permiso de dar");
        assertTrue(withoutPermission.reason().contains(HotbarRules.PERMISSION));
    }

    @Test
    void creativeStacksNeverPassTheItemLimit() {
        assertEquals(16, HotbarRules.clamp(HotbarItem.of("ender_pearl", 64), 16).count());
        assertEquals(1, HotbarRules.clamp(HotbarItem.of("diamond_sword", 5), 1).count());
        assertEquals(64, HotbarRules.clamp(HotbarItem.of("stone", 64), 64).count());
        assertTrue(HotbarRules.clamp(HotbarItem.EMPTY, 64).isEmpty());
        assertEquals(99, HotbarItem.of("stone", 500).count(), "nunca más de 99 en un hueco");
    }
}
