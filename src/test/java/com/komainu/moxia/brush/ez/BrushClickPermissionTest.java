package com.komainu.moxia.brush.ez;

import com.komainu.moxia.permission.PermissionNodes;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cada clic con un pincel pide el permiso de ese pincel, no solo alguno de la familia: a quien le bajan de paquete
 * con el pincel de dispersar en la mano, deja de funcionarle ya y no al volver a entrar.
 */
class BrushClickPermissionTest {
    @Test
    void eachBrushAsksForItsOwnNodeOnEveryClick() {
        Predicate<String> placeOnly = Set.of("komaedit.use", "komaedit.build.brush.place")::contains;
        assertFalse(PermissionNodes.allows(EzBrushManager.permission(EzBrushType.SCATTER), placeOnly));
        assertTrue(PermissionNodes.allows(EzBrushManager.permission(EzBrushType.PLACE), placeOnly));
        for (EzBrushType type : EzBrushType.values()) {
            assertNotNull(PermissionNodes.find(EzBrushManager.permission(type)),
                    "el nodo de " + type.id() + " está registrado");
        }
    }
}
