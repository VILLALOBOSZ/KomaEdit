package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.tube.TubeSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementSettingsTest {
    @Test
    void readsStructureAlignmentsAndCommonFlags() {
        PlacementSettings settings = PlacementSettings.parse(PlacementSettings.Kind.PLACE,
                "Sh(S:Cone,P:\"dirt,stone\") PlayerRelative C(D:(-1,2,-1)) -s 12,36,12 -o 0.5,2 -k x -c 90 "
                        + "-f XZ -r Y -a -j MULTIPLES_45 -x -w stairs:0.7 -i 42");
        assertEquals("Sh(S:Cone,P:\"dirt,stone\")", settings.structure());
        assertInstanceOf(Alignment.PlayerRelative.class, settings.primary());
        assertInstanceOf(Alignment.Constant.class, settings.secondary());
        assertEquals(new Vec3(12, 36, 12), settings.dimensions());
        assertEquals(0.5, settings.minimumScale());
        assertEquals(2.0, settings.maximumScale());
        assertEquals(90.0, settings.orientationAngle());
        assertArrayEquals(new boolean[]{true, false, true}, settings.flips());
        assertEquals(1, settings.rotationAxis());
        assertTrue(settings.placeAir());
        assertTrue(settings.perturb());
        assertEquals(AlignmentFrame.Snap.MULTIPLES_45, settings.snap());
        assertEquals(SmoothMode.STAIRS, settings.smoothMode());
        assertEquals(0.7, settings.coverage());
        assertEquals(42L, settings.seed());
        assertEquals(false, settings.randomSeed());
    }

    @Test
    void defaultsMatchEzEdits() {
        PlacementSettings scatter = PlacementSettings.parse(PlacementSettings.Kind.SCATTER, "Clipboard");
        assertNull(scatter.dimensions());
        assertEquals(2.0, scatter.scatter().density());
        assertEquals(15, scatter.scatter().iterations());
        assertEquals(0.5, scatter.scatter().threshold());
        assertTrue(scatter.randomSeed());
        assertInstanceOf(Alignment.Constant.class, scatter.primary());
        PlacementSettings array = PlacementSettings.parse(PlacementSettings.Kind.ARRAY, "Cl");
        assertEquals(0.0, array.array().gap());
        assertTrue(Double.isNaN(array.array().maxOffset()));
        assertEquals(TubeSettings.NormalMode.CONSISTENT, array.array().normalMode());
    }

    @Test
    void scatterFlags() {
        PlacementSettings settings = PlacementSettings.parse(PlacementSettings.Kind.SCATTER,
                "Cl S R -h Box(D:\"60,30,60\") -d west,up -e 0.6 -m \"!red_wool\" -n 0.5% -u 0 -l pink_wool -t");
        PlacementSettings.Scatter scatter = settings.scatter();
        assertEquals("Box(D:\"60,30,60\")", scatter.region());
        assertEquals(List.of(new Vec3(-1, 0, 0), new Vec3(0, 1, 0)), scatter.directions());
        assertEquals(0.6, scatter.threshold());
        assertEquals("!red_wool", scatter.mask());
        assertEquals(0.5, scatter.density());
        assertEquals(0, scatter.iterations());
        assertEquals("pink_wool", scatter.cover());
        assertTrue(scatter.trim());
        assertTrue(settings.needsNormal());
    }

    @Test
    void arrayFlagsAndTheSharedN() {
        PlacementSettings settings = PlacementSettings.parse(PlacementSettings.Kind.ARRAY,
                "Cl T O(A:90) -g -3 -y 1 -q 1.5,0.5,5 -p 0.2:-0.1:0 -n upright -b");
        PlacementSettings.Array array = settings.array();
        assertEquals(-3.0, array.gap());
        assertEquals(1.0, array.maxOffset());
        assertEquals(1.5, array.progression().at(0.0), 1.0E-9);
        assertEquals(5.0, array.progression().at(1.0), 1.0E-9);
        assertEquals(0.5, array.progression().at(0.5), 1.0E-9);
        assertEquals(0.2, array.tension());
        assertEquals(-0.1, array.bias());
        assertEquals(TubeSettings.NormalMode.UPRIGHT, array.normalMode());
        assertTrue(array.snapToSurface());
        assertInstanceOf(Alignment.Tangential.class, settings.primary());
        assertInstanceOf(Alignment.Orthogonal.class, settings.secondary());
    }

    @Test
    void flagsOutsideTheirCommandFail() {
        assertThrows(IllegalArgumentException.class,
                () -> PlacementSettings.parse(PlacementSettings.Kind.PLACE, "Cl -g 2"));
        assertThrows(IllegalArgumentException.class,
                () -> PlacementSettings.parse(PlacementSettings.Kind.ARRAY, "Cl -m stone"));
        assertThrows(IllegalArgumentException.class,
                () -> PlacementSettings.parse(PlacementSettings.Kind.PLACE, "Cl -n 2"));
        assertThrows(IllegalArgumentException.class,
                () -> PlacementSettings.parse(PlacementSettings.Kind.SCATTER, "Cl Tangential"));
        assertThrows(IllegalArgumentException.class,
                () -> PlacementSettings.parse(PlacementSettings.Kind.PLACE, "Cl ViewDiff"));
        assertThrows(IllegalArgumentException.class,
                () -> PlacementSettings.parse(PlacementSettings.Kind.PLACE, "Cl C C C"));
        assertThrows(IllegalArgumentException.class,
                () -> PlacementSettings.parse(PlacementSettings.Kind.PLACE, "-s 3"));
        assertThrows(IllegalArgumentException.class,
                () -> PlacementSettings.parse(PlacementSettings.Kind.PLACE, "Cl -s"));
        assertThrows(IllegalArgumentException.class,
                () -> PlacementSettings.parse(PlacementSettings.Kind.SCATTER, "Cl -n 120"));
    }

    @Test
    void structuresParseWithLongShortAndSpanishNames() {
        StructureSpec clipboard = StructureSpec.parse("Cl(O:C,PM:smooth,FB:2,R:90,RO:(0,1,0),WO:(0,0,2),OJ:0.5)");
        assertEquals(StructureSpec.Type.CLIPBOARD, clipboard.type());
        assertTrue(clipboard.centered());
        assertTrue(clipboard.smooth());
        assertEquals(2.0, clipboard.fillBias());
        assertEquals(90.0, clipboard.rotation());
        assertEquals(new Vec3(0, 1, 0), clipboard.offsets().relative());
        assertEquals(new Vec3(0, 0, 2), clipboard.offsets().world());
        assertEquals(0.5, clipboard.offsets().jitter());
        assertEquals("tree_.*", StructureSpec.parse("Sc(N:tree_.*)").filename());
        assertEquals("sponge.3", StructureSpec.parse("Schematic(Filename:%p/casa,Format:sponge.3)").format());
        StructureSpec shape = StructureSpec.parse("Shape(Shape:Torus(Thickness:0.4),Pattern:57)");
        assertEquals("57", shape.pattern());
        assertTrue(shape.procedural());
        assertTrue(shape.procedural(1).usesPattern());
        StructureSpec bottom = StructureSpec.parse("forma(S:Cono,P:stone,O:BOTTOM)");
        assertEquals(new Vec3(-1, 0, -1), bottom.procedural(1).min());
        StructureSpec rock = StructureSpec.parse("Ic(P:\"dirt,stone\",R:0.8,S:2,PL:0.3)");
        assertEquals(new ProceduralStructure.IcosphereSettings(0.8, 2, 0.3), rock.icosphere());
        assertEquals("##Magma", StructureSpec.parse("Ex(E:\"=x*x+y*y+z*z<0.5\",P:##Magma)")
                .palette());
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Sh(S:Cone)"));
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Cl(O:BOTTOM)"));
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Cl(Banana:1)"));
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Ic(P:stone,S:9)"));
        assertThrows(IllegalArgumentException.class, () -> StructureSpec.parse("Nube"));
    }

    @Test
    void wordsKeepParenthesesAndQuotesTogether() {
        assertEquals(List.of("Sh(S:Torus(Thickness:0.4),P:\"a, b\")", "C(D:(1, 2, 3))", "-s", "1,2,3"),
                ParamText.words("Sh(S:Torus(Thickness:0.4),P:\"a, b\")  C(D:(1, 2, 3)) -s 1,2,3"));
        ParamText.Call call = ParamText.call("Ex(E:\"=a ? b : c\",P:'x,y')");
        assertEquals("=a ? b : c", call.get("e"));
        assertEquals("x,y", call.get("p"));
        assertThrows(IllegalArgumentException.class, () -> ParamText.words("Sh(P:\"open"));
    }

    @Test
    void smoothBlocksProfileUsesTheSharedNotation() {
        PlacementSettings settings = PlacementSettings.parse(PlacementSettings.Kind.SCATTER,
                "Clipboard -w SSW(C:0.3,Wall:cobblestone,Slab:oak)");
        assertEquals(SmoothMode.WALLS, settings.smoothMode());
        assertEquals(0.3, settings.coverage());
        assertEquals("cobblestone", settings.smooth().materials().get(
                com.komainu.moxia.shape.smooth.PieceVariant.WALL));
        assertEquals(SmoothMode.LAYERS, PlacementSettings.parse(PlacementSettings.Kind.ARRAY, "Clipboard -w Layers")
                .smoothMode());
        assertEquals(SmoothMode.NONE, PlacementSettings.parse(PlacementSettings.Kind.PLACE, "Clipboard").smoothMode());
        assertThrows(IllegalArgumentException.class,
                () -> PlacementSettings.parse(PlacementSettings.Kind.PLACE, "Clipboard -w Layers(Slab:oak)"));
    }

    @Test
    void viewDiffOnlyWorksInThePlacementBrush() {
        PlacementSettings brush = PlacementSettings.parse(PlacementSettings.Kind.PLACE, "Cl SurfaceNormal ViewDiff",
                true);
        assertTrue(brush.needsViewDiff());
        assertTrue(brush.needsNormal());
        assertThrows(IllegalArgumentException.class,
                () -> PlacementSettings.parse(PlacementSettings.Kind.SCATTER, "Cl C ViewDiff", true));
        assertThrows(IllegalArgumentException.class,
                () -> PlacementSettings.parse(PlacementSettings.Kind.ARRAY, "Cl V C", true));
        PlacementSettings scatter = PlacementSettings.parse(PlacementSettings.Kind.SCATTER,
                "Sh(S:Cone,P:stone) S R -h Ellipsoid(D:\"20,10,20\") -n 3", true);
        assertEquals("Ellipsoid(D:\"20,10,20\")", scatter.scatter().region());
    }
}
