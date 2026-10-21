package com.komainu.moxia.studio.tools;

import com.komainu.moxia.display.DisplayLook;
import com.komainu.moxia.display.DisplayPose;
import com.komainu.moxia.display.DisplaySteps;
import com.komainu.moxia.display.DisplayText;
import com.komainu.moxia.permission.PermissionNodes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La tarjeta «Decorar (displays)»: cada orden que manda la leen los mismos analizadores que el servidor. */
class DisplayCardTest {
    private static final ToolContext EVERYTHING = new ToolContext(true, ToolCatalog.permissionNodes(), true, 1_000L,
            4, false);

    private static DisplayCard card() {
        return (DisplayCard) ToolCatalog.byId("display");
    }

    @Test
    void aBlockFromTheHandOrTheLibrary() {
        DisplayCard card = card();
        assertEquals("komaedit display block", card.command(card.defaults(), false), "vacío: el de la mano");
        ToolValues values = card.defaults().with("block", "minecraft:flower_pot");
        assertEquals("komaedit display block minecraft:flower_pot", card.command(values, false));
        assertFalse(card.blocked(values, EVERYTHING));
        assertTrue(card.blocked(card.defaults().with("block", "oak log"), EVERYTHING), "sin espacios");
    }

    @Test
    void anItemWithItsView() {
        DisplayCard card = card();
        ToolValues values = card.defaults().with("kind", "item").with("item_mode", "ground");
        assertEquals("komaedit display item ground", card.command(values, false));
        assertTrue(values.param("item_mode").visible(values));
        assertFalse(values.param("block").visible(values));
    }

    @Test
    void aTextReadsBackWithTheServerParser() {
        DisplayCard card = card();
        // En el Studio el salto de línea se escribe \n, como en la orden.
        ToolValues values = card.defaults().with("kind", "text").with("display_text", "Tienda\\nabierta")
                .with("text_color", "dorado").with("background", "no").with("text_shadow", true)
                .with("align", "izquierda");
        String command = card.command(values, false);
        assertTrue(command.startsWith("komaedit display text "), command);
        DisplayText text = DisplayText.parse(command.substring("komaedit display text ".length()));
        assertEquals("Tienda\nabierta", text.plain());
        assertEquals(0xFFAA00, text.color());
        assertEquals(DisplayText.Background.NONE, text.background());
        assertTrue(text.shadow());
        assertEquals(DisplayText.Alignment.LEFT, text.alignment());
        ToolValues empty = values.with("display_text", "");
        assertTrue(card.blocked(empty, EVERYTHING), "sin texto no se puede poner");
        ToolValues plain = card.defaults().with("kind", "text");
        assertEquals("komaedit display text Bienvenidos", card.command(plain, false));
    }

    @Test
    void theAdvancedSettingsGoTogether() {
        DisplayCard card = card();
        ToolValues values = card.defaults().with("brightness", "12").with("shadow_radius", 15)
                .with("shadow_strength", 60).with("view_range", 250).with("billboard", "vertical")
                .with("glow", true).with("glow_color", "celeste");
        ToolAction look = card.actions().stream().filter(action -> action.id().equals("display_look")).findFirst()
                .orElseThrow();
        String command = look.command().apply(values);
        assertTrue(command.startsWith("komaedit display set all "), command);
        DisplayLook read = DisplayLook.parse(command.substring("komaedit display set all ".length()));
        assertEquals(12, read.brightness());
        assertEquals(1.5F, read.shadowRadius(), 1.0E-6);
        assertEquals(0.6F, read.shadowStrength(), 1.0E-6);
        assertEquals(2.5F, read.viewRange(), 1.0E-6);
        assertEquals(DisplayPose.Billboard.VERTICAL, read.billboard());
        assertTrue(read.glow());
        assertEquals(0x55FFFF, read.glowColor());
        DisplayLook defaults = DisplayCard.look(card.defaults());
        assertEquals(DisplayLook.DEFAULT, defaults, "la tarjeta de serie deja los displays como Minecraft");
    }

    @Test
    void theStepsGoToTheClientEditor() {
        DisplayCard card = card();
        String command = DisplayCard.stepsCommand(card.defaults().with("move_step", "cuarto")
                .with("angle_step", "5").with("local", true));
        assertEquals("kedisplay ajustes 1/4 5 local", command);
        String[] words = command.substring("kedisplay ajustes ".length()).split(" ");
        assertEquals(DisplaySteps.Move.QUARTER, DisplaySteps.Move.parse(words[0]));
        assertEquals(DisplaySteps.Angle.DEG5, DisplaySteps.Angle.parse(words[1]));
        for (String option : List.of("pixel", "cuarto", "bloque", "libre")) {
            assertNotNull(DisplaySteps.Move.parse(DisplayCard.MOVE_STEPS.get(option)), option);
        }
    }

    @Test
    void itAsksForItsOwnPermission() {
        DisplayCard card = card();
        assertEquals(List.of(DisplayCard.PERMISSION), card.permissions(card.defaults()));
        assertEquals(PermissionNodes.Pack.INTERMEDIATE, PermissionNodes.find(DisplayCard.PERMISSION).pack());
        assertEquals(PermissionNodes.Pack.ADVANCED, PermissionNodes.find("komaedit.build.display_others").pack());
        assertFalse(PermissionNodes.allows("komaedit.build.display_others",
                node -> node.equals(DisplayCard.PERMISSION)), "editar los de otros no cuelga de display");
        assertTrue(PermissionNodes.allows(DisplayCard.PERMISSION,
                node -> node.equals(PermissionNodes.Pack.INTERMEDIATE.node())));
        assertFalse(card.previewable());
        assertEquals(ToolCategory.BUILDINGS, ToolCatalog.categoryOf(card));
        assertEquals("komaedit display edit on", card.selectCommand(), "elegirla abre el editor");
    }
}
