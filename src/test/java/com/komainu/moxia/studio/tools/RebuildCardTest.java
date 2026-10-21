package com.komainu.moxia.studio.tools;

import com.komainu.moxia.blueprint.BlueprintPermissions;
import com.komainu.moxia.rebuild.RebuildAxis;
import com.komainu.moxia.rebuild.RebuildOrder;
import com.komainu.moxia.rebuild.RebuildSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La tarjeta «Reconstrucción animada»: la orden que manda la lee el servidor con los mismos ajustes. */
class RebuildCardTest {
    private static final ToolCard CARD = ToolCatalog.byId("rebuild");
    private static final ToolContext EVERYTHING = new ToolContext(true, ToolCatalog.permissionNodes(), true, 1_000L,
            4, true);
    private static final ToolContext NO_SELECTION = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L,
            0, true);

    /** Lo que lee el servidor de la orden, sin la parte de qué se levanta. */
    private static RebuildSettings read(String command) {
        String rest = command.substring("komaedit rebuild".length()).strip();
        if (rest.startsWith("portapapeles")) {
            rest = rest.substring("portapapeles".length());
        } else if (rest.startsWith("plano ")) {
            rest = rest.substring("plano ".length()).strip();
            rest = rest.contains(" ") ? rest.substring(rest.indexOf(' ')) : "";
        }
        return RebuildSettings.parse(rest, new int[]{0, 64, 0});
    }

    @Test
    void theDefaultsAreTheShortestCommand() {
        assertEquals("komaedit rebuild", CARD.command(CARD.defaults(), false));
        assertFalse(CARD.previewable());
        assertEquals(CARD.command(CARD.defaults(), false), CARD.command(CARD.defaults(), true), "sin -v");
        assertEquals(RebuildOrder.BUILDER, read(CARD.command(CARD.defaults(), false)).order());
    }

    @Test
    void theServerReadsBackWhatTheCardSet() {
        ToolValues values = CARD.defaults()
                .with("order", "axis").with("sweep", "nz")
                .with("blocks_per_tick", 15)
                .with("layer_pause", 15).with("same_delay", 2).with("other_delay", 5).with("lead_in", 30)
                .with("particles", true).with("sound", true);
        String command = CARD.command(values, false);
        assertEquals("komaedit rebuild eje -e -z -b 1.5 -p 1.5 -i 0.2 -d 0.5 -w 3 -f -n", command);
        RebuildSettings settings = read(command);
        assertEquals(RebuildOrder.AXIS, settings.order());
        assertEquals(RebuildAxis.Z_NEGATIVE, settings.axis());
        assertEquals(1.5, settings.pace().blocksPerTick());
        assertEquals(30.0, settings.pace().layerPause());
        assertEquals(4.0, settings.pace().sameDelay(), 1e-9);
        assertEquals(10.0, settings.pace().otherDelay(), 1e-9);
        assertEquals(60.0, settings.pace().leadIn());
        assertTrue(settings.particles() && settings.sound());
    }

    @Test
    void aDurationReplacesTheBlocksPerTick() {
        String command = CARD.command(CARD.defaults().with("duration", 45).with("blocks_per_tick", 90), false);
        assertEquals("komaedit rebuild -t 45", command);
        assertEquals(900.0, read(command).pace().durationTicks());
    }

    @Test
    void clipboardAndBlueprintSayWhatRisesAndCanSkipAir() {
        ToolValues clipboard = CARD.defaults().with("source", "clipboard").with("order", "layers")
                .with("skip_air", true);
        assertEquals("komaedit rebuild portapapeles capas -a", CARD.command(clipboard, false));
        assertTrue(read(CARD.command(clipboard, false)).skipAir());
        ToolValues blueprint = CARD.defaults().with("source", "blueprint").with("blueprint", "casa_roble")
                .with("order", "random").with("seed", 77);
        assertEquals("komaedit rebuild plano casa_roble azar -s 77", CARD.command(blueprint, false));
        assertEquals(77L, read(CARD.command(blueprint, false)).seed());
        // -a solo tiene sentido fuera de la selección.
        assertEquals("komaedit rebuild", CARD.command(CARD.defaults().with("skip_air", true), false));
    }

    @Test
    void everyOrderWritesAWordTheServerKnows() {
        for (String order : List.of("builder", "layers", "waves", "axis", "random", "history")) {
            RebuildSettings settings = read(CARD.command(CARD.defaults().with("order", order), false));
            assertEquals(order, switch (settings.order()) {
                case BUILDER -> "builder";
                case LAYERS -> "layers";
                case WAVES -> "waves";
                case AXIS -> "axis";
                case RANDOM -> "random";
                case HISTORY -> "history";
            });
        }
    }

    @Test
    void theSelectionIsOnlyNeededToRebuildTheSelection() {
        assertEquals("no_selection", CARD.requirement(CARD.defaults(), NO_SELECTION).id());
        assertNull(CARD.requirement(CARD.defaults().with("source", "clipboard"), NO_SELECTION));
        assertNull(CARD.requirement(CARD.defaults().with("source", "blueprint").with("blueprint", "x"),
                NO_SELECTION));
    }

    @Test
    void aBlueprintNeedsItsNameAndItsPermission() {
        ToolValues empty = CARD.defaults().with("source", "blueprint");
        assertTrue(CARD.problems(empty, EVERYTHING).stream().anyMatch(problem -> problem.id().equals("blueprint_name")));
        assertTrue(CARD.blocked(empty, EVERYTHING));
        assertTrue(CARD.blocked(empty.with("blueprint", "mi casa"), EVERYTHING), "sin espacios");
        assertFalse(CARD.blocked(empty.with("blueprint", "servidor/castillo"), EVERYTHING));
        assertEquals(List.of(RebuildCard.NODE, BlueprintPermissions.LOAD), CARD.permissions(empty));
        assertEquals(List.of(RebuildCard.NODE), CARD.permissions(CARD.defaults()));
        ToolContext rebuildOnly = new ToolContext(true, Set.of(RebuildCard.NODE), true, 10L, 0, true);
        assertFalse(CARD.blocked(CARD.defaults(), rebuildOnly));
        assertTrue(CARD.blocked(empty.with("blueprint", "castillo"), rebuildOnly), "sin permiso de planos");
    }

    @Test
    void theButtonsControlTheRunningRebuild() {
        assertEquals(List.of("komaedit rebuild pausar", "komaedit rebuild seguir", "komaedit rebuild acelerar",
                "komaedit rebuild terminar", "komaedit rebuild cancelar"),
                CARD.actions().stream().map(action -> action.command().apply(CARD.defaults())).toList());
    }

    @Test
    void presetsChangeHowItRisesNotWhat() {
        ToolValues blueprint = CARD.defaults().with("source", "blueprint").with("blueprint", "torre");
        for (ToolPreset preset : CARD.presets()) {
            ToolValues applied = CARD.apply(blueprint, preset);
            assertEquals("blueprint", applied.choice("source"), preset.id());
            assertEquals("torre", applied.text("blueprint"), preset.id());
            read(CARD.command(applied, false));
        }
    }
}
