package com.komainu.moxia.image;

import com.komainu.moxia.permission.PermissionNodes;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagePermissionsTest {
    private static Predicate<String> holding(String... nodes) {
        return Set.of(nodes)::contains;
    }

    @Test
    void imagesAreAdvancedAndUrlsAreForAdmins() {
        assertEquals(PermissionNodes.Pack.ADVANCED, PermissionNodes.find("komaedit.build.image").pack());
        assertEquals("ezedits.stainedglass.image", PermissionNodes.find("komaedit.build.image").ezEdits());
        assertEquals(PermissionNodes.Pack.ADMIN, PermissionNodes.find(ImageUrlPolicy.ADMIN_NODE).pack());

        assertFalse(PermissionNodes.allows("komaedit.build.image", holding("komaedit.permpack.intermediate")));
        assertTrue(PermissionNodes.allows("komaedit.build.image", holding("komaedit.permpack.advanced")));
        assertTrue(PermissionNodes.allows("komaedit.build.image", holding("komaedit.build")));
        assertTrue(PermissionNodes.allows("komaedit.build.image", holding("komaedit.build.image")));

        // Construir no da las URL: ni el permiso amplio, ni el de la orden, ni el paquete avanzado.
        for (String node : new String[]{"komaedit.build", "komaedit.build.image", "komaedit.permpack.advanced",
                "komaedit.use"}) {
            assertFalse(PermissionNodes.allows(ImageUrlPolicy.ADMIN_NODE, holding(node)), node);
        }
        assertTrue(PermissionNodes.allows(ImageUrlPolicy.ADMIN_NODE, holding("komaedit.permpack.admin")));
        assertTrue(PermissionNodes.allows(ImageUrlPolicy.ADMIN_NODE, holding("komaedit.admin")));
    }
}
