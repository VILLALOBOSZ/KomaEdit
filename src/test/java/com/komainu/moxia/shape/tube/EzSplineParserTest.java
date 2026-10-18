package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeOptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sintaxis de {@code //ezspline}: los ejemplos de la wiki pública de ezEdits y su traducción. */
class EzSplineParserTest {
    /** Ejemplos tal cual aparecen en la wiki (sin el {@code //ezsp} del principio). */
    private static final List<String> WIKI = List.of(
            // Parámetros comunes
            "rope clay 5", "rope clay 10", "rope clay 1,12", "rope clay 1,12,1", "rope clay 1,0.1:12,1",
            "rope clay 1,0.9:12,1", "rope clay 2,10,2,12,2,10,2", "polygon clay 10 4 -p 0:0:0",
            "polygon clay 10 4 -p 0:-1:0", "polygon clay 10 4 -p -1:0:0", "polygon clay 10 4 -p 0:1:0",
            "rope clay 10 -q BALANCED", "rope clay 10 -q FAST", "beads clay 10 -q 1", "beads clay 10 -q 7",
            "2d Re(Y1:-0.2,Y2:0.2) clay 10 -r 0", "2d Re(Y1:-0.2,Y2:0.2) clay 10 -r 180",
            "3d Chainlink clay 10 -s 0.2", "3d Chainlink clay 10 -s 4", "2d Polygon(S:4) clay 10 -t 0",
            "2d Polygon(S:4) clay 10 -t 90",
            "expression black,red,blue,white,yellow 5 -o -n CONSISTENT ((z%2)>1.5?5:2*(x>0)+(y>0))+0.001",
            "expression black,red,blue,white,yellow 5 -o -n HORIZONTAL ((z%2)>1.5?5:2*(x>0)+(y>0))+0.001",
            "expression black,red,blue,white,yellow 5 -o -n UPRIGHT ((z%2)>1.5?5:2*(x>0)+(y>0))+0.001",
            "rope ice 15 6 -e FLAT", "rope ice 15 6 -e SOFT", "rope ice 15 6 -e SPIKE", "rope ice 15 6 -e ROUND",
            "rope ice 15 6 -e CUBE", "2d square ice 15 -e FLAT", "2d square ice 15 -e CUBE", "rope clay 10 -h",
            // Formas 2D
            "2d Circle clay 10", "basic clay 10", "2d Square clay 10", "2d Diamond clay 10",
            "2d RoundedSquare clay 10", "2d SuperCircle(Exponent:0.5) clay 15", "2d SuperCircle(Exponent:99.0) clay 15",
            "2d CC(Count:1) clay 15", "2d CC(Count:10) clay 15", "2d CirclesCircle -t 90 clay 10",
            "2d Strands(Count:4,Size:0.3) clay 10", "2d Pipe(Inner:0.6) clay 10", "2d Polygon(Sides:3) clay 15",
            "2d Re(x1:-1.0,y1:-1.0,x2:-0.6,y2:1.0) clay 12", "2d Re(x1:-0.2,y1:-1.0,x2:0.2,y2:1.0) clay 12",
            "2d Re(x1:-1.0,y1:-0.2,x2:0.2,y2:0.2) clay 12", "2d Re(x1:0.6,y1:-1.0,x2:1.0,y2:0.2) clay 12",
            "2d Re(x1:0.6,y1:0.6,x2:1.0,y2:1.0) clay 12", "2d Star(Sides:6,Depth:0.7) clay 12",
            "2d Flower(Count:7,Depth:0.3) clay 12",
            // Formas 3D
            "3d Beads clay 10", "3d Cubes clay 7", "3d Cubes(Gap:0.0) clay 7", "3d Cubes(Gap:1.0) clay 7",
            "3d Cu(G:2.0) clay 7", "3d Braids clay 7", "3d Braids(Thickness:0.2) clay 7", "3d Br(G:1) clay 7",
            "3d Chainlink clay 10", "3d Chainlink(M:99,N:99,Extrusion:0.6) clay 10",
            "3d Chainlink(M:1,N:1,E:0.7,G:-0.2,T:1.2) clay 11", "3d Chainlink(M:2,N:2,E:0,G:1) clay 11",
            "3d Chainlink(P:FIRST) red_terracotta 10", "3d Chainlink(P:SECOND) blue_wool 10",
            "3d Fishnet clay 10", "3d Fishnet(Spacing:2.0) clay 10", "3d Fishnet(S:2.0,Depth:1.0,Width:0.3) clay 10",
            "3d Fi(S:2.0,D:0.5,W:0.5) clay 10", "3d Honeycomb(CellSize:0.3,WireWidth:0.1,Depth:0.3) clay 12",
            "3d Oscillate clay 10", "3d Oscillate(Depth:0.6) clay 10", "3d Oscillate(Depth:0.6,Interval:1.5) clay 10",
            "3d Os(D:0.2,I:1.5) clay 10", "3d Rings(E:0,T:1,G:0,M:2,N:2) clay 10", "3d Sc(c:8,d:1.2) clay 22,9",
            "3d Noodles(Amount:8,Density:60%,Tangle:2,Seed:7) clay 12",
            // Avanzadas
            "noise ##Grayscale 10",
            "noise -##Magma 5,25 Ce(F:1.5,fO:1,cR:sub,M:OR,U:-.6) 0.6 -t 90",
            "noise ##Brown 20,12 Ce(f:4,cr:sub,cj:.8,m:or,u:-.5,l:-1.3) 0.2 -s 6 -t 20",
            "noise -##GlowBlue(6:16) 20,12 Ce(f:2,cr:sub,m:or,u:-.55,l:-0.551,z:0.1) 0.9 -t 200",
            "noise -w Slabs ##GrayCold(4:11),waxed_weathered_cut_copper 20 Ce(f:1.5,cr:sub,m:or,u:-0.85,l:-.5,"
                    + "y:0.3) -n UPRIGHT -i (x*x+n+y*y<1&&y<0)*(y+0.97)",
            "noise -w Stairs light_gray_stained_glass 20,15,25 Ce(f:1.4,z:.3,m:or,l:-1,u:-0.5) 3 -t 600 "
                    + "-i t=0.2;r=sqrt(x*x+y*y);m=1-abs(2*r-t-1)/abs(t-1);n<m&&r<1",
            "expression clay 10 -t 90 R=0.2;r=0.1;w=0.7;s=0.5;sqrt((abs(x)-w)^2+y^2)<R||sqrt(((z+1)%s-r)^2+y^2)<r"
                    + "&&abs(x)<w",
            "structure TS(P:##GlowPurple,S:Heart,T:=(z+y)*.4+.5) 12"
    );

    @Test
    void everyWikiExampleParses() {
        List<String> failures = new ArrayList<>();
        for (String example : WIKI) {
            try {
                EzSpline.Request request = EzSpline.parse(example, null);
                if (request.kind() != EzSpline.Kind.HELP && request.kind() != EzSpline.Kind.STRUCTURE) {
                    // La figura tiene que poder construirse: opciones dentro de rango y fórmula compilada.
                    TubeSettings.from(request.parameters());
                }
            } catch (RuntimeException exception) {
                failures.add(example + " → " + exception.getMessage());
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
        for (String prefixed : List.of("2d", "3d", "basic")) {
            assertTrue(EzSpline.parse(prefixed + " -h", null).kind() == EzSpline.Kind.HELP);
        }
        assertEquals(EzSpline.Kind.HELP, EzSpline.parse("", null).kind());
    }

    @Test
    void chainlinkTakesLongAndShortNames() {
        ShapeOptions longNames = options("3d Chainlink(Extrusion:0.4,Thickness:1.2,Gap:0.1,MajorExponent:5,"
                + "MinorExponent:2,Place:FIRST) clay 10");
        ShapeOptions shortNames = options("3d ch(e:0.4,t:1.2,g:0.1,m:5,n:2,p:first) clay 10");
        assertEquals(longNames.values(), shortNames.values());
        assertEquals(EzShapes.Form.CHAINLINK.option(), longNames.get(EzShapes.FORM, 0));
        assertEquals(0.4, longNames.get("extrusion", 0));
        assertEquals(1.2, longNames.get("thickness", 0));
        assertEquals(5.0, longNames.get("majorexponent", 0));
        assertEquals(1.0, longNames.get("place", -1));
        assertEquals(2.0, options("3d Chainlink(P:SECOND) clay 10").get("place", -1));
        assertEquals(PathShape.SWEEP, parameters("3d Chainlink clay 10").shape());
    }

    @Test
    void twoDimensionalShapesMapToProfiles() {
        assertProfile("2d Circle clay 10", TubeSettings.TubeProfile.CIRCLE);
        assertProfile("basic clay 10", TubeSettings.TubeProfile.CIRCLE);
        assertProfile("2d Sq clay 10", TubeSettings.TubeProfile.SQUARE);
        assertProfile("2d di clay 10", TubeSettings.TubeProfile.DIAMOND);
        assertProfile("2d RS clay 10", TubeSettings.TubeProfile.ROUNDED);
        assertEquals(4.0, settings("2d RS clay 10").exponent());
        TubeSettings superCircle = settings("2d SC(E:0.5) clay 10");
        assertEquals(TubeSettings.TubeProfile.ROUNDED, superCircle.profile());
        assertEquals(0.5, superCircle.exponent());
        assertEquals(2.0, settings("2d SuperCircle clay 10").exponent(), "SuperCircle sin exponente es un círculo");
        TubeSettings polygon = settings("2d Po(S:7) clay 10");
        assertEquals(TubeSettings.TubeProfile.POLYGON, polygon.profile());
        assertEquals(7, polygon.sides());
        assertEquals(5, settings("2d Polygon clay 10").sides());
        assertProfile("2d Polygon(S:6,D:0.3) clay 10", TubeSettings.TubeProfile.STAR);
        TubeSettings star = settings("2d St(S:6,D:0.7) clay 10");
        assertEquals(TubeSettings.TubeProfile.STAR, star.profile());
        assertEquals(0.7, star.depth(), 1.0E-9);
        assertEquals(0.5, settings("2d Star clay 10").depth(), 1.0E-9);
        assertProfile("2d Star(D:0) clay 10", TubeSettings.TubeProfile.POLYGON);
        TubeSettings flower = settings("2d Fl(C:7,D:0.3) clay 10");
        assertEquals(TubeSettings.TubeProfile.FLOWER, flower.profile());
        assertEquals(7, flower.sides());
        assertEquals(7, settings("2d Flower(S:7) clay 10").sides(), "la wiki abrevia Count de Flower como S");
        ShapeOptions rectangle = options("2d Re(x1:-0.2,y1:-1.0,x2:0.2,y2:1.0) clay 12");
        assertEquals(TubeSettings.TubeProfile.RECTANGLE.ordinal(), rectangle.get("profile", -1));
        assertEquals(-0.2, rectangle.get("x1", 0));
        assertEquals(1.0, rectangle.get("y2", 0));
        ShapeOptions pipe = options("2d Pi(I:0.6) clay 12");
        assertEquals(TubeSettings.TubeProfile.CIRCLE.ordinal(), pipe.get("profile", -1));
        assertEquals(0.6, pipe.get("inner", 0));
        assertEquals(0.8, options("2d Pipe clay 12").get("inner", 0));
        ShapeOptions strands = options("2d Sr(C:4,S:0.3) clay 12");
        assertEquals(4.0, strands.get("sides", 0));
        assertEquals(0.3, strands.get("strandsize", 0));
    }

    @Test
    void ropeAndCirclesCircleKeepTheirTwist() {
        TubeSettings rope = settings("rope clay 10");
        assertEquals(TubeSettings.TubeProfile.ROPE, rope.profile());
        assertEquals(3, rope.sides());
        assertEquals(90.0, rope.twistPerDiameter(), "rope es 2d CirclesCircle -t 90");
        assertEquals(0.0, settings("2d CirclesCircle clay 10").twistPerDiameter());
        assertEquals(90.0, settings("2d CirclesCircle -t 90 clay 10").twistPerDiameter(),
                "la bandera puede ir antes del patrón");
        assertEquals(45.0, settings("rope clay 10 -t 45").twistPerDiameter());
        assertEquals(1, settings("2d CC(Count:1) clay 15").sides());
        TubeSettings filled = settings("2d CC(C:5,F:true) clay 15");
        assertEquals(5, filled.sides());
        assertTrue(filled.filled());
        assertEquals(6, settings("rope ice 15 6 -e ROUND").sides(), "sintaxis antigua: rope <patrón> <radio> <n>");
        assertEquals(TubeSettings.EndMode.ROUND, settings("rope ice 15 6 -e ROUND").endMode());
        assertEquals(4, settings("polygon clay 10 4").sides(), "sintaxis antigua: polygon <patrón> <radio> <lados>");
    }

    @Test
    void commonFlagsReachTheTube() {
        TubeSettings settings = settings("2d Square clay 10 -p 0.5:-1:0.25 -q EXACT -r 30,120 -s 2 -n UPRIGHT"
                + " -e SPIKE -w stairs -c 0.7");
        assertEquals(0.5, settings.tension());
        assertEquals(-1.0, settings.bias());
        assertEquals(0.25, settings.continuity());
        assertEquals(TubeSettings.Quality.EXACT, settings.quality());
        assertEquals(30.0, settings.startRollDegrees(), 1.0E-9);
        assertEquals(120.0, settings.endRollDegrees(), 1.0E-9);
        assertTrue(settings.rollRange());
        assertEquals(2.0, settings.stretch());
        assertEquals(TubeSettings.NormalMode.UPRIGHT, settings.normalMode());
        assertEquals(TubeSettings.EndMode.SPIKE, settings.endMode());
        assertEquals(0.7, settings.coverage());
        // Los nombres no distinguen mayúsculas, y la calidad antigua por muestras se traduce.
        assertEquals(TubeSettings.Quality.FAST, settings("rope clay 10 -q fast").quality());
        assertEquals(TubeSettings.Quality.HIGH, settings("beads clay 10 -q high").quality());
        assertEquals(TubeSettings.Quality.FAST, settings("beads clay 10 -q 1").quality());
        assertEquals(TubeSettings.Quality.BALANCED, settings("beads clay 10 -q 2").quality());
        assertEquals(TubeSettings.Quality.EXACT, settings("beads clay 10 -q 7").quality());
        assertEquals(TubeSettings.Quality.BALANCED, settings("beads clay 10").quality());
        assertEquals(TubeSettings.NormalMode.CONSISTENT, settings("rope clay 10").normalMode());
        assertEquals(TubeSettings.EndMode.FLAT, settings("rope clay 10").endMode());
    }

    @Test
    void radiiFollowTheWikiRules() {
        TubeSettings seven = settings("rope clay 2,10,2,12,2,10,2");
        assertEquals(12.0, seven.radius());
        assertEquals(2.0 / 12.0, seven.radii().at(0.0), 1.0E-9);
        assertEquals(1.0, seven.radii().at(0.5), 1.0E-9);
        TubeSettings shifted = settings("rope clay 1,0.1:12,1");
        assertEquals(1.0, shifted.radii().at(0.1), 1.0E-9);
        TubeSettings fraction = settings("rope clay 6.9");
        assertEquals(6.9, fraction.radius() * fraction.radii().at(0.5), 1.0E-9);
        assertThrows(IllegalArgumentException.class, () -> EzSpline.parse("rope clay 0.5:10,2", null),
                "la primera posición es siempre 0");
        assertThrows(IllegalArgumentException.class, () -> EzSpline.parse("rope clay 5,0.6:10,15,0.4:20,5", null),
                "las posiciones deben crecer");
    }

    @Test
    void threeDimensionalShapesCarryTheirParameters() {
        ShapeOptions cubes = options("3d Cu(G:2.0) clay 7");
        assertEquals(EzShapes.Form.CUBES.option(), cubes.get(EzShapes.FORM, 0));
        assertEquals(2.0, cubes.get("gap", 0));
        ShapeOptions fishnet = options("3d Fi(S:2.0,D:0.5,W:0.5) clay 10");
        assertEquals(2.0, fishnet.get("spacing", 0));
        assertEquals(0.5, fishnet.get("depth", 0));
        assertEquals(0.5, fishnet.get("width", 0));
        ShapeOptions honeycomb = options("3d Ho(C:0.3,W:0.1,D:0.4) clay 10");
        assertEquals(0.3, honeycomb.get("cellsize", 0));
        assertEquals(0.1, honeycomb.get("wirewidth", 0));
        ShapeOptions oscillate = options("3d Os(D:0.2,I:1.5) clay 10");
        assertEquals(1.5, oscillate.get("interval", 0));
        ShapeOptions scales = options("3d Sc(c:8,d:1.2,H:1.1,V:1.3,M:2,N:10,A:20) clay 22,9");
        assertEquals(EzShapes.Form.SCALES.option(), scales.get(EzShapes.FORM, 0));
        assertEquals(8.0, scales.get("columns", 0));
        assertEquals(1.2, scales.get("depthmultiplier", 0));
        assertEquals(20.0, scales.get("tileangle", 0));
        assertFalse(scales.has("angle"), "el ángulo de las escamas no gira el tubo");
        ShapeOptions noodles = options("3d No(A:8,D:60%,F:1,T:2,W:0.9,S:42) clay 12");
        assertEquals(8.0, noodles.get("amount", 0));
        assertEquals(0.6, noodles.get("density", 0), 1.0E-9);
        assertEquals(42.0, noodles.get("noodleseed", 0));
        assertEquals(0.5, options("3d Noodles(Density:50) clay 12").get("density", 0), 1.0E-9);
        ShapeOptions rings = options("3d Ri(E:0,T:1,G:0,M:2,N:2) clay 10");
        assertEquals(EzShapes.Form.RINGS.option(), rings.get(EzShapes.FORM, 0));
        assertEquals(1.0, rings.get("thickness", 0));
        assertEquals(EzShapes.Form.BEADS.option(), options("3d Be clay 10").get(EzShapes.FORM, 0));
        // Braids es la trenza de KomaEdit.
        assertEquals(PathShape.BRAID, parameters("3d Braids(Thickness:0.2) clay 7").shape());
        assertEquals(0.12, settings("3d Braids(Thickness:0.2) clay 7").strandThickness(), 1.0E-9);
    }

    @Test
    void noiseSplineReadsNoiseDepthAndCut() {
        EzSpline.Request plain = EzSpline.parse("noise ##Grayscale 10", null);
        assertEquals(EzSpline.Kind.PALETTE, plain.kind());
        assertEquals("##Grayscale", plain.material());
        assertEquals(EzShapes.Form.NOISE.option(), plain.parameters().options().get(EzShapes.FORM, 0));
        assertEquals(EzSpline.DEFAULT_NOISE_DEPTH, plain.parameters().options().get("noisedepth", 0));
        assertEquals(EzSpline.DEFAULT_NOISE, plain.parameters().options().formula());
        EzSpline.Request full = EzSpline.parse("noise -##Magma 5,25 Ce(F:1.5,fO:1,cR:sub,M:OR,U:-.6) 0.6 -t 90", null);
        assertEquals("-##Magma", full.material());
        assertEquals(0.6, full.parameters().options().get("noisedepth", 0));
        assertTrue(full.parameters().options().formula().startsWith("Ce("));
        assertEquals(90.0, TubeSettings.from(full.parameters()).twistPerDiameter());
        EzSpline.Request depthOnly = EzSpline.parse("noise stone 10 0.3", null);
        assertEquals(0.3, depthOnly.parameters().options().get("noisedepth", 0));
        EzSpline.Request cut = EzSpline.parse("noise stone 10 -i r=sqrt(x*x+y*y);(r<1&&n>0.5)*max(n,0.01)", null);
        assertTrue(cut.parameters().options().formula().contains("\n(r<1&&n>0.5)") || cut.parameters().options()
                .formula().contains("\nr=sqrt"));
        assertThrows(IllegalArgumentException.class, () -> EzSpline.parse("noise stone 10 -i foo<1", null),
                "la expresión de corte se comprueba al escribirla");
        assertThrows(IllegalArgumentException.class, () -> EzSpline.parse("2d Circle stone 10 -i n>0", null),
                "-i solo existe en el spline de ruido");
    }

    @Test
    void expressionSplineFlags() {
        EzSpline.Request request = EzSpline.parse("expression black,red,blue,white,yellow 5 -o -z -n UPRIGHT "
                + "((z%2)>1.5?5:2*(x>0)+(y>0))+0.001", null);
        assertEquals(EzSpline.Kind.PALETTE, request.kind());
        assertTrue(request.indexed());
        assertEquals(PathShape.EXPRESSION, request.parameters().shape());
        assertEquals("((z%2)>1.5?5:2*(x>0)+(y>0))+0.001", request.parameters().options().formula());
        assertEquals(1.0, request.parameters().options().get("znormal", 0));
        assertEquals(5.0, request.withPaletteSize(5).parameters().options().get("paletteentries", 0));
        assertEquals(TubeSettings.NormalMode.UPRIGHT, TubeSettings.from(request.parameters()).normalMode());
        EzSpline.Request spaced = EzSpline.parse("expr stone 8 -t 30 r < 1 && abs(x) > 0.3", null);
        assertEquals("r < 1 && abs(x) > 0.3", spaced.parameters().options().formula());
        assertFalse(spaced.indexed());
        assertThrows(IllegalArgumentException.class, () -> EzSpline.parse("expression stone 8", null));
    }

    @Test
    void structureSplineFlags() {
        EzSpline.Request request = EzSpline.parse("structure TS(P:##GlowPurple,S:Heart,T:=(z+y)*.4+.5) 12", null);
        assertEquals(EzSpline.Kind.STRUCTURE, request.kind());
        assertEquals("TS(P:##GlowPurple,S:Heart,T:=(z+y)*.4+.5)", request.structure().source());
        assertEquals(12.0, request.structure().radius());
        assertEquals(TubeBender.Mode.REPEAT, request.structure().bend().mode());
        EzSpline.Request stretched = EzSpline.parse("structure Clipboard -z -s 2 -n UPRIGHT -p 0:0.5:0", null);
        assertTrue(Double.isNaN(stretched.structure().radius()), "sin radio, a su tamaño");
        assertEquals(TubeBender.Mode.STRETCH, stretched.structure().bend().mode());
        assertEquals(2.0, stretched.structure().stretch());
        assertEquals(TubeSettings.NormalMode.UPRIGHT, stretched.structure().bend().normalMode());
        assertEquals(0.5, stretched.structure().bend().bias());
        assertThrows(IllegalArgumentException.class, () -> EzSpline.parse("structure Clipboard 4 -t 90", null));
        assertThrows(IllegalArgumentException.class, () -> EzSpline.parse("structure Clipboard 2,8", null));
    }

    @Test
    void errorsNameTheProblem() {
        assertMessage("2d Hexagon clay 10", "Forma 2D desconocida");
        assertMessage("3d Chainlink(Q:1) clay 10", "no tiene el parámetro «Q»");
        assertMessage("3d Chainlink(T:9) clay 10", "Thickness debe estar entre");
        assertMessage("3d Chainlink(P:THIRD) clay 10", "FIRST, SECOND o BOTH");
        assertMessage("3d Beads(G:1) clay 10", "no admite parámetros");
        assertMessage("2d Circle clay", "Uso: //ezspline 2d");
        assertMessage("2d Circle clay 10 -x 3", "Bandera desconocida");
        assertMessage("2d Circle clay 10 -q ULTRA", "Calidad desconocida");
        assertMessage("warp clay 10", "Subcomando de spline desconocido");
        assertMessage("2d SC(E:2 clay 10", "paréntesis");
    }

    private static void assertMessage(String input, String fragment) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> EzSpline.parse(input, null), input);
        assertTrue(exception.getMessage().contains(fragment), input + " → " + exception.getMessage());
    }

    private static void assertProfile(String input, TubeSettings.TubeProfile profile) {
        assertEquals(profile, settings(input).profile(), input);
    }

    private static com.komainu.moxia.shape.ShapeParameters parameters(String input) {
        return EzSpline.parse(input, null).parameters();
    }

    private static ShapeOptions options(String input) {
        return parameters(input).options();
    }

    private static TubeSettings settings(String input) {
        return TubeSettings.from(parameters(input));
    }
}
