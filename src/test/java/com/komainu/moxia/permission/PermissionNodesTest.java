package com.komainu.moxia.permission;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionNodesTest {
    private static Predicate<String> holding(String... nodes) {
        return Set.of(nodes)::contains;
    }

    @Test
    void broadPermissionsKeepWorkingAsBefore() {
        Predicate<String> builder = holding("komaedit.use", "komaedit.build");
        assertTrue(PermissionNodes.allows("komaedit.build.deform.noise", builder));
        assertTrue(PermissionNodes.allows("komaedit.build.texture.ambient", builder));
        assertTrue(PermissionNodes.allows("komaedit.build.pillar", builder));
        assertFalse(PermissionNodes.allows("komaedit.select.save", builder));
        // Las paletas y los ruidos guardados siguen colgando de komaedit.use.
        assertTrue(PermissionNodes.allows("komaedit.palette.save", holding("komaedit.use")));
        assertTrue(PermissionNodes.allows("komaedit.noise.print", holding("moxia.use")));
        assertTrue(PermissionNodes.allows("komaedit.select.catenary", holding("komaedit.select")));
    }

    @Test
    void fineNodesAndTheirParentsGrantOnlyTheirFamily() {
        Predicate<String> deformer = holding("komaedit.build.deform");
        assertTrue(PermissionNodes.allows("komaedit.build.deform.voxelize", deformer));
        assertFalse(PermissionNodes.allows("komaedit.build.surface.rockify", deformer));
        assertTrue(PermissionNodes.allowsAny("komaedit.build.deform", deformer));
        assertFalse(PermissionNodes.allowsAny("komaedit.build.texture", deformer));
        Predicate<String> oneMode = holding("komaedit.build.texture.noise");
        assertTrue(PermissionNodes.allowsAny("komaedit.build.texture", oneMode));
        assertFalse(PermissionNodes.allows("komaedit.build.texture.ambient", oneMode));
    }

    @Test
    void packsAreCumulativeExceptAdmin() {
        Predicate<String> basic = holding("komaedit.permpack.basic");
        assertTrue(PermissionNodes.allows("komaedit.build.shape.torus", basic));
        assertTrue(PermissionNodes.allows("komaedit.select.superwand", basic));
        assertFalse(PermissionNodes.allows("komaedit.build.texture.ambient", basic));
        // El paquete básico incluye komaedit.use, pero no lo que antes colgaba de él (eso es intermedio).
        assertFalse(PermissionNodes.allows("komaedit.palette.save", basic));
        assertTrue(PermissionNodes.anyPack(basic));

        Predicate<String> intermediate = holding("komaedit.permpack.intermediate");
        assertTrue(PermissionNodes.allows("komaedit.build.shape.torus", intermediate));
        assertTrue(PermissionNodes.allows("komaedit.build.texture.ambient", intermediate));
        assertTrue(PermissionNodes.allows("komaedit.palette.save", intermediate));
        assertFalse(PermissionNodes.allows("komaedit.build.scatter", intermediate));
        assertFalse(PermissionNodes.allows("komaedit.build.pillar", intermediate));

        Predicate<String> advanced = holding("komaedit.permpack.advanced");
        assertTrue(PermissionNodes.allows("komaedit.build.scatter", advanced));
        assertTrue(PermissionNodes.allows("komaedit.build.pillar", advanced));
        assertFalse(PermissionNodes.allows("komaedit.admin.reload", advanced));
        assertFalse(PermissionNodes.allows("komaedit.limit.unrestricted", advanced));

        Predicate<String> admin = holding("komaedit.permpack.admin");
        assertTrue(PermissionNodes.allows("komaedit.admin.reload", admin));
        assertFalse(PermissionNodes.allows("komaedit.build.shape.cone", admin));
    }

    @Test
    void nobodyGetsAnythingWithoutPermissions() {
        Predicate<String> nothing = node -> false;
        for (PermissionNodes.Node node : PermissionNodes.all()) {
            assertFalse(PermissionNodes.allows(node.node(), nothing), node.node());
        }
        assertFalse(PermissionNodes.anyPack(nothing));
    }

    @Test
    void everyNodeIsUniqueAndDescribed() {
        Set<String> seen = new HashSet<>();
        List<PermissionNodes.Node> nodes = PermissionNodes.all();
        for (PermissionNodes.Node node : nodes) {
            assertTrue(seen.add(node.node()), "repetido: " + node.node());
            assertTrue(node.node().startsWith("komaedit."), node.node());
            assertFalse(node.description().isBlank(), node.node());
        }
        assertNotNull(PermissionNodes.find("komaedit.build.brush.scatter"));
        assertEquals(PermissionNodes.Pack.ADVANCED, PermissionNodes.find("komaedit.build.array").pack());
    }
}
