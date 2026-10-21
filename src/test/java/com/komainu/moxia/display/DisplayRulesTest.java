package com.komainu.moxia.display;

import com.komainu.moxia.gizmo.GizmoKeys;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.komainu.moxia.display.QuatTest.assertVec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que decide el servidor (límites, dueños, grupos, selección) y la lectura de órdenes y ajustes. */
class DisplayRulesTest {
    private static final UUID ALEX = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SAM = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Vec3 PLAYER = new Vec3(0, 65, 0);

    private static DisplayPose block(Vec3 center, Vec3 scale) {
        return DisplayPose.centered(center, Quat.IDENTITY, scale, new Vec3(0.5, 0.5, 0.5));
    }

    // ---------------------------------------------------------------- límites

    @Test
    void theServerRefusesWhatIsTooBigTooFarOrBroken() {
        DisplayLimits limits = DisplayLimits.DEFAULT;
        Vec3 near = new Vec3(3, 65, 3);
        assertEquals(Optional.empty(), limits.checkPose(block(near, DisplayPose.ONE), near, PLAYER));
        assertTrue(limits.checkPose(block(near, new Vec3(17, 1, 1)), near, PLAYER).orElseThrow().contains("16"));
        assertTrue(limits.checkPose(block(near, new Vec3(0.001, 1, 1)), near, PLAYER).isPresent());
        Vec3 far = new Vec3(60, 65, 0);
        assertTrue(limits.checkPose(block(far, DisplayPose.ONE), far, PLAYER).orElseThrow().contains("48"));
        DisplayPose pose = block(near, DisplayPose.ONE);
        DisplayPose offset = new DisplayPose(pose.position(), 0, 0, new Vec3(9, 0, 0), pose.left(), pose.scale(),
                pose.right(), pose.billboard());
        assertTrue(limits.checkPose(offset, near, PLAYER).orElseThrow().contains("entidad"),
                "un modelo lejos de su entidad saltaría las zonas protegidas");
        assertTrue(limits.checkPose(pose.moved(new Vec3(Double.NaN, 0, 0)), near, PLAYER).isPresent());
        // El centro también cuenta: una entidad cerca con el modelo lejos no pasa.
        assertTrue(limits.checkPose(pose, far, PLAYER).isPresent());
    }

    @Test
    void countsPerChunkPerPlayerAndSelection() {
        DisplayLimits limits = new DisplayLimits(4, 10, 48, 16, 8, 3, 64, 20);
        assertEquals(Optional.empty(), limits.checkChunk(3, 1));
        assertTrue(limits.checkChunk(3, 2).isPresent());
        assertEquals(Optional.empty(), limits.checkPlayer(9, 1));
        assertTrue(limits.checkPlayer(10, 1).isPresent());
        assertEquals(Optional.empty(), new DisplayLimits(4, 0, 48, 16, 8, 3, 64, 20).checkPlayer(1_000_000, 1),
                "0 es sin límite");
        assertTrue(limits.checkSelection(4).isPresent());
        assertTrue(limits.checkText("").isPresent());
        assertTrue(limits.checkText("x".repeat(65)).isPresent());
        assertEquals(Optional.empty(), limits.checkText("Bienvenidos"));
        assertThrows(IllegalArgumentException.class, () -> new DisplayLimits(0, 10, 48, 16, 8, 3, 64, 20));
        assertThrows(IllegalArgumentException.class, () -> new DisplayLimits(4, 10, 2, 16, 8, 3, 64, 20));
    }

    @Test
    void theConfigFileFillsWhatIsMissing() {
        DisplayLimits read = DisplayConfig.read("{\"format\": 1, \"maxPerChunk\": 32, \"maxScale\": 4.5}");
        assertEquals(32, read.maxPerChunk());
        assertEquals(4.5, read.maxScale());
        assertEquals(DisplayLimits.DEFAULT.maxPerPlayer(), read.maxPerPlayer());
        assertEquals(DisplayLimits.DEFAULT, DisplayConfig.read(DisplayConfig.toJson(DisplayLimits.DEFAULT)));
        assertThrows(IllegalArgumentException.class, () -> DisplayConfig.read("{\"format\": 2}"));
        assertThrows(IllegalArgumentException.class, () -> DisplayConfig.read("{\"maxPerChunk\": -1}"));
    }

    // ---------------------------------------------------------------- dueños, grupos y selección

    @Test
    void ownersAndGroupsLiveInTheTags() {
        Set<String> mine = Set.of(DisplayTags.MARK, DisplayTags.owner(ALEX), DisplayTags.group("0000abcd"));
        assertEquals(Optional.of(ALEX), DisplayTags.ownerOf(mine));
        assertEquals(Optional.of("0000abcd"), DisplayTags.groupOf(mine));
        assertTrue(DisplayTags.editable(mine, ALEX, false));
        assertFalse(DisplayTags.editable(mine, SAM, false), "el de otro, sin el permiso de los demás");
        assertTrue(DisplayTags.editable(mine, SAM, true));
        Set<String> vanilla = Set.of("cartel_del_spawn");
        assertFalse(DisplayTags.editable(vanilla, ALEX, false), "uno que no creó KomaEdit");
        assertEquals(Optional.empty(), DisplayTags.ownerOf(Set.of(DisplayTags.OWNER_PREFIX + "no-es-un-uuid")));
        assertEquals(8, DisplayTags.newGroup(new java.util.Random(1)).length());
    }

    @Test
    void clickingOneOfAGroupSelectsTheWholeGroup() {
        DisplaySelection selection = new DisplaySelection();
        UUID pot = UUID.randomUUID();
        UUID plant = UUID.randomUUID();
        UUID sign = UUID.randomUUID();
        assertTrue(selection.pick(plant, List.of(pot, plant), DisplaySelection.Mode.REPLACE));
        assertEquals(List.of(pot, plant), selection.ids());
        assertEquals(plant, selection.primary(), "el activo es el pulsado");
        selection.pick(sign, List.of(sign), DisplaySelection.Mode.TOGGLE);
        assertEquals(3, selection.size());
        assertEquals(sign, selection.primary());
        selection.pick(pot, List.of(pot, plant), DisplaySelection.Mode.TOGGLE);
        assertEquals(List.of(sign), selection.ids(), "Mayús+clic en el grupo lo quita entero");
        selection.pick(pot, List.of(pot), DisplaySelection.Mode.REPLACE);
        assertEquals(List.of(pot), selection.ids(), "Alt+clic: solo él");
        selection.retain(List.of());
        assertTrue(selection.isEmpty());
        assertNull(selection.primary());
        assertTrue(selection.pick(sign, List.of(), DisplaySelection.Mode.TOGGLE), "sin grupo, solo él");
        assertEquals(List.of(sign), selection.ids());
    }

    @Test
    void theLedgerCountsPerPlayerAndForgetsKilledDisplays() {
        DisplayLedger ledger = new DisplayLedger();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        ledger.add(first, ALEX, "minecraft:overworld", 0, 0);
        ledger.add(second, ALEX, "minecraft:overworld", 40, 40);
        ledger.add(other, SAM, "minecraft:overworld", 0, 0);
        assertEquals(2, ledger.count(ALEX));
        DisplayLedger.Lookup lookup = new DisplayLedger.Lookup() {
            @Override
            public boolean loaded(String world, int chunkX, int chunkZ) {
                return chunkX == 0;
            }

            @Override
            public boolean exists(String world, UUID display) {
                return false;
            }
        };
        assertEquals(1, ledger.prune(ALEX, lookup), "el del chunk cargado que ya no está");
        assertEquals(1, ledger.count(ALEX), "el del chunk sin cargar sigue contando");
        assertEquals(1, ledger.count(SAM), "los de los demás no se tocan");
        ledger.moved(second, "minecraft:the_nether", 1, 2);
        DisplayLedger back = DisplayLedger.fromJson(ledger.toJson());
        assertEquals(new DisplayLedger.Entry(ALEX, "minecraft:the_nether", 1, 2), back.get(second));
        assertEquals(1, back.count(SAM));
        assertTrue(ledger.dirty());
        ledger.saved();
        assertFalse(ledger.dirty());
        assertEquals(0, DisplayLedger.fromJson("{\"displays\":[{\"display\":\"roto\"}]}").count(entry -> true));
    }

    // ---------------------------------------------------------------- textos y ajustes

    @Test
    void textFlagsAndFormatting() {
        DisplayText text = DisplayText.parse("-c dorado -f no -s -a izq -w 120 &lTienda\\n&rabierta && cerrada");
        assertEquals(0xFFAA00, text.color());
        assertEquals(DisplayText.Background.NONE, text.background());
        assertEquals(0, text.backgroundArgb());
        assertTrue(text.shadow());
        assertEquals(DisplayText.Alignment.LEFT, text.alignment());
        assertEquals(120, text.lineWidth());
        assertEquals("Tienda\nabierta & cerrada", text.plain());
        assertTrue(text.spans().getFirst().bold());
        assertFalse(text.spans().getLast().bold(), "&r quita el formato");
        // Sombra 1, alineación izquierda 8; con fondo propio no lleva el de serie (4).
        assertEquals(1 | 8, text.flags());
        DisplayText plain = DisplayText.parse("Hola");
        assertEquals(DisplayText.DEFAULT_COLOR, plain.color());
        assertEquals(4, plain.flags(), "de serie: el fondo de Minecraft");
        assertEquals(0x40FF0000, DisplayText.parse("-f #ff0000 Hola").backgroundArgb());
        assertEquals(0x80123456, DisplayText.parse("-f #80123456 Hola").backgroundArgb());
        assertEquals(0xFF5555, DisplayText.parse("&cRojo").spans().getFirst().color());
        assertEquals("-5 grados", DisplayText.parse("-5 grados").plain(), "un número no es una bandera");
        assertThrows(IllegalArgumentException.class, () -> DisplayText.parse("-c"));
        assertThrows(IllegalArgumentException.class, () -> DisplayText.parse("-x hola"));
        assertThrows(IllegalArgumentException.class, () -> DisplayText.parse("-c chartreuse hola"));
        assertThrows(IllegalArgumentException.class, () -> DisplayText.parse("-w 5 hola"));
        assertThrows(IllegalArgumentException.class, () -> DisplayText.parse("-s"));
    }

    @Test
    void advancedSettingsReadAndWriteTheSame() {
        DisplayLook look = DisplayLook.parse("brillo=15 sombra=0.5 fuerza=0.8 vista=2 billboard=center contorno=on "
                + "color=#ff8800");
        assertEquals(15, look.brightness());
        assertEquals(0.5F, look.shadowRadius());
        assertEquals(DisplayPose.Billboard.CENTER, look.billboard());
        assertTrue(look.glow());
        assertEquals(0xFF8800, look.glowColor());
        assertEquals(look, DisplayLook.parse(look.format()));
        assertEquals(DisplayLook.DEFAULT, DisplayLook.parse(""));
        assertEquals(DisplayLook.DEFAULT, DisplayLook.parse(DisplayLook.DEFAULT.format()));
        assertNull(DisplayLook.parse("brillo=auto").brightness());
        assertThrows(IllegalArgumentException.class, () -> DisplayLook.parse("brillo=16"));
        assertThrows(IllegalArgumentException.class, () -> DisplayLook.parse("sombra"));
        assertThrows(IllegalArgumentException.class, () -> DisplayLook.parse("vista=0"));
        assertThrows(IllegalArgumentException.class, () -> DisplayLook.parse("luz=3"));
        assertEquals(DisplayPose.Billboard.VERTICAL, DisplayPose.Billboard.parse("v"));
        assertThrows(IllegalArgumentException.class, () -> DisplayPose.Billboard.parse("diagonal"));
    }

    @Test
    void stepsSnapAndParse() {
        assertEquals(0.5625, DisplaySteps.snap(0.55, 1.0 / 16.0), 1.0E-12);
        assertEquals(0.75, DisplaySteps.snap(0.8, 0.25), 1.0E-12);
        assertEquals(0.123, DisplaySteps.snap(0.123, 0.0), 1.0E-12, "sin paso, tal cual");
        DisplaySteps steps = DisplaySteps.DEFAULT;
        assertEquals(Math.toRadians(45), steps.snapAngle(Math.toRadians(50), false), 1.0E-12);
        assertEquals(Math.toRadians(50), steps.snapAngle(Math.toRadians(50), true), 1.0E-12);
        assertEquals(1.0 / 16.0, steps.snapScale(0.001, false, DisplayDrag.MIN_SCALE), 1.0E-12);
        assertEquals(DisplayDrag.MIN_SCALE, steps.withMove(DisplaySteps.Move.FREE).snapScale(0.001, false,
                DisplayDrag.MIN_SCALE), 1.0E-12);
        assertEquals(DisplaySteps.Move.QUARTER, DisplaySteps.Move.parse("1/4"));
        assertEquals(DisplaySteps.Move.FREE, DisplaySteps.Move.parse("libre"));
        assertEquals(DisplaySteps.Angle.DEG5, DisplaySteps.Angle.parse("5°"));
        assertEquals(DisplaySteps.Move.ONE, DisplaySteps.Move.FREE.next(), "Ctrl+5 da la vuelta");
        assertThrows(IllegalArgumentException.class, () -> DisplaySteps.Move.parse("1/3"));
    }

    @Test
    void editorSettingsSurviveTheirFile() {
        DisplayEditorSettings settings = DisplayEditorSettings.DEFAULT.withTool(DisplayGizmo.Tool.ROTATE)
                .withLocal(true).withSteps(new DisplaySteps(DisplaySteps.Move.QUARTER, DisplaySteps.Angle.DEG5));
        assertEquals(settings, DisplayEditorSettings.parse(settings.toJson()));
        assertEquals(DisplayEditorSettings.DEFAULT, DisplayEditorSettings.parse("{\"tool\":\"bailar\"}"),
                "lo que no se entiende queda de serie");
    }

    @Test
    void defaultKeysAreCtrlNumbersAndCustomOnesReplaceThem() {
        DisplayKeys keys = new DisplayKeys();
        assertEquals(DisplayKeys.Action.MOVE, keys.match('1', GizmoKeys.MOD_CONTROL).orElseThrow().action());
        assertEquals(DisplayKeys.Action.SCALE, keys.match('3', GizmoKeys.MOD_CONTROL).orElseThrow().action());
        assertTrue(keys.match('1', 0).isEmpty(), "el 1 solo sigue siendo de la barra rápida");
        assertEquals(DisplayKeys.Action.DELETE, keys.match(GizmoKeys.KEY_DELETE, 0).orElseThrow().action());
        assertEquals(DisplayKeys.Action.DELETE, keys.match(GizmoKeys.KEY_BACKSPACE, 0).orElseThrow().action());
        DisplayKeys.Press ungroup = keys.match('G', GizmoKeys.MOD_CONTROL | GizmoKeys.MOD_SHIFT).orElseThrow();
        assertEquals(DisplayKeys.Action.GROUP, ungroup.action());
        assertTrue(ungroup.shift());
        keys.bind(DisplayKeys.Action.ROTATE, 'R');
        assertEquals(DisplayKeys.Action.ROTATE, keys.match('R', 0).orElseThrow().action());
        assertTrue(keys.match('2', GizmoKeys.MOD_CONTROL).isEmpty(), "con tecla propia, el atajo de serie no");
        assertTrue(DisplayKeys.Action.FORWARD.repeats());
        assertFalse(DisplayKeys.Action.DELETE.repeats());
        assertTrue(DisplayKeys.Action.STEP.idle());
    }

    // ---------------------------------------------------------------- colocar

    @Test
    void aNewBlockFillsTheCellNextToTheFace() {
        DisplayPose onTop = DisplayPlacement.place(DisplayKind.BLOCK, 4, 64, -2, 3, new Vec3(4.3, 65, -1.6),
                DisplayVolume.block(), 0);
        assertVec(new Vec3(4.5, 65.5, -1.5), onTop.world(new Vec3(0.5, 0.5, 0.5)), 1.0E-12);
        assertVec(new Vec3(4.5, 65.5, -1.5), onTop.position(), 1.0E-12, "la entidad en su centro");
        DisplayPose west = DisplayPlacement.place(DisplayKind.BLOCK, 4, 64, -2, 0, new Vec3(4, 64.5, -1.5),
                DisplayVolume.block(), 0);
        assertVec(new Vec3(3.5, 64.5, -1.5), west.world(new Vec3(0.5, 0.5, 0.5)), 1.0E-12);
    }

    @Test
    void aSignOnAWallFacesOutAndSitsOnThePointLookedAt() {
        DisplayVolume sign = DisplayVolume.text(40, 2);
        // La cara sur (+Z) del bloque (0, 64, 0), mirada en (0.53, 64.71, 1).
        DisplayPose pose = DisplayPlacement.place(DisplayKind.TEXT, 0, 64, 0, 5, new Vec3(0.53, 64.71, 1), sign,
                180);
        Vec3 front = pose.frame().axisVector(2);
        assertVec(new Vec3(0, 0, 1), front, 1.0E-9);
        Vec3 center = pose.world(sign.center());
        assertEquals(0.5, center.x(), 1.0E-9, "ajustado a 1/16: 0.53 → 0.5");
        assertEquals(64.6875, center.y(), 1.0E-9, "64.71 → 64 + 11/16");
        assertEquals(1 + DisplayPlacement.WALL_GAP, center.z(), 1.0E-9, "pegado a la pared, sin tocarla");
        DisplayPose standing = DisplayPlacement.place(DisplayKind.TEXT, 0, 64, 0, 3, new Vec3(0.5, 65, 0.5), sign,
                0);
        assertEquals(65 + DisplayPlacement.WALL_GAP, standing.world(new Vec3(sign.center().x(), 0, 0)).y(),
                1.0E-9, "de pie, la base sobre el punto");
        assertVec(new Vec3(0, 0, -1), standing.frame().axisVector(2), 1.0E-9,
                "mirando al jugador, que mira al sur");
    }

    @Test
    void anItemLooksAtThePlayerInQuarterTurns() {
        Quat facing = DisplayPlacement.facingPlayer(100, 90);
        // Guiñada 100 ≈ mirando al oeste: el frente mira al este.
        assertVec(new Vec3(1, 0, 0), facing.axisVector(2), 1.0E-9);
        assertVec(new Vec3(0, 0, 1), DisplayPlacement.facing(new Vec3(0, 0, 1)).axisVector(2), 1.0E-9);
        assertVec(new Vec3(-1, 0, 0), DisplayPlacement.facing(new Vec3(-1, 0, 0)).axisVector(2), 1.0E-9);
        assertThrows(IllegalArgumentException.class, () -> DisplayPlacement.normal(6));
        assertEquals(DisplayKind.TEXT, DisplayKind.parse("texto"));
        assertEquals(DisplayKind.ITEM, DisplayKind.parse("item"));
        assertThrows(IllegalArgumentException.class, () -> DisplayKind.parse("pez"));
    }
}
