package com.komainu.moxia.notes;

import com.komainu.moxia.feedback.LangWords;
import com.komainu.moxia.feedback.WorldSketch;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.views.ViewTeleport;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cómo se ve una nota en el mundo y adónde lleva «ir»: renglones, distancia, apagado, autor y fecha. */
class NoteSketchTest {
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final long OCTOBER_21 = LocalDateTime.of(2026, 10, 21, 18, 30).toInstant(ZoneOffset.ofHours(2))
            .toEpochMilli();

    private static WorldNote note(String text, int distance, boolean team, long resolvedAt) {
        return new WorldNote("abc234", text, NoteColor.ROJO.rgb(), NoteIcon.AVISO, "minecraft:overworld", 10, 64, 10,
                true, WorldNote.UP, distance, team, UUID.randomUUID(), "Ana", OCTOBER_21, resolvedAt > 0 ? "Bea" : "",
                resolvedAt);
    }

    @Test
    void textIsWrappedByWordsAndCut() {
        assertEquals(List.of("Falta el tejado del ala norte", "y la chimenea"),
                NoteSketch.wrap("Falta el tejado del ala norte y la chimenea", 30, 4));
        assertEquals(List.of("abcdefghij", "klmno"), NoteSketch.wrap("abcdefghijklmno", 10, 4),
                "una palabra larga se corta");
        List<String> cut = NoteSketch.wrap("uno dos tres cuatro cinco seis siete ocho", 9, 2);
        assertEquals(2, cut.size());
        assertTrue(cut.getLast().endsWith("..."), cut.toString());
        assertTrue(cut.getLast().length() <= 9, cut.toString());
    }

    @Test
    void aNoteIsSeenUpToItsDistanceAndFadesNearTheEnd() {
        WorldNote note = note("Revisar", 40, false, 0);
        Vec3 label = new Vec3(note.labelX(), note.labelY(), note.labelZ());
        assertEquals(List.of(), NoteSketch.of(note, label.add(new Vec3(41, 0, 0)), MADRID, OCTOBER_21, false,
                LangWords.spanish()).labels(), "más allá de su distancia no se ve");
        WorldSketch near = NoteSketch.of(note, label.add(new Vec3(10, 0, 0)), MADRID, OCTOBER_21, false,
                LangWords.spanish());
        WorldSketch.Label shown = near.labels().getFirst();
        assertEquals(1.0, shown.alpha());
        assertEquals(NoteIcon.AVISO.pixels(), shown.icon());
        assertEquals(NoteColor.ROJO.rgb(), shown.iconRgb());
        assertEquals("Revisar", shown.rows().getFirst().text());
        assertEquals("Ana · 21/10", shown.rows().getLast().text(), "cerca, el autor y la fecha");
        assertTrue(near.lines().size() > 1, "el palito y el contorno del bloque");
        WorldSketch.Label fading = NoteSketch.of(note, label.add(new Vec3(36, 0, 0)), MADRID, OCTOBER_21, false,
                LangWords.spanish()).labels().getFirst();
        assertEquals(0.5, fading.alpha(), 1.0E-9, "a 36 de 40 va por la mitad");
        assertEquals(1, fading.rows().size(), "lejos, solo el texto");
    }

    @Test
    void resolvedNotesAreHiddenUnlessAsked() {
        WorldNote resolved = note("Hecho", 40, true, OCTOBER_21 + 1000);
        Vec3 camera = new Vec3(14, 66, 14);
        assertEquals(List.of(), NoteSketch.of(resolved, camera, MADRID, OCTOBER_21, false, LangWords.spanish())
                .labels());
        WorldSketch.Label shown = NoteSketch.of(resolved, camera, MADRID, OCTOBER_21, true, LangWords.spanish())
                .labels().getFirst();
        assertEquals(NoteIcon.TAREA.pixels(), shown.icon(), "con la marca de hecho");
        assertEquals("Ana · 21/10 · equipo · resuelta", shown.rows().getLast().text());
    }

    @Test
    void datesFromAnotherYearCarryTheYear() {
        long lastYear = LocalDateTime.of(2025, 3, 2, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
        assertEquals("02/03/2025", NoteSketch.date(lastYear, MADRID, OCTOBER_21));
        assertEquals("21/10", NoteSketch.date(OCTOBER_21, MADRID, OCTOBER_21));
    }

    @Test
    void aNoteOnAWallComesOutOfTheFaceItWasPutOn() {
        // En la cara oeste (4) del bloque (10, 64, 10): el rótulo delante de la pared, a media altura.
        WorldNote wall = new WorldNote("wal234", "Ventana aquí", NoteColor.AZUL.rgb(), NoteIcon.AQUI,
                "minecraft:overworld", 10, 64, 10, true, 4, 48, true, UUID.randomUUID(), "Ana", OCTOBER_21, "", 0);
        assertTrue(wall.onWall());
        assertEquals(10.5 - 1.1, wall.labelX(), 1.0E-9);
        assertEquals(64.5, wall.labelY(), 1.0E-9);
        assertEquals(10.5, wall.labelZ(), 1.0E-9);
        WorldSketch sketch = NoteSketch.of(wall, new Vec3(2, 65, 10), MADRID, OCTOBER_21, false, LangWords.spanish());
        WorldSketch.Line stem = sketch.lines().getFirst();
        assertEquals(10.0 - 0.01, stem.from().x(), 1.0E-9, "el palito sale de la cara oeste");
        assertTrue(stem.to().x() < stem.from().x());
        assertTrue(sketch.labels().getFirst().solid(), "fondo sólido: una nota delante tapa a la de detrás");
        // Ir a ella deja delante de la pared aunque se llegue por detrás.
        ViewTeleport.Target target = NoteJump.target(wall, "minecraft:overworld", 40, 10);
        assertTrue(target.x() < wall.labelX(), "al oeste, delante de la cara");
        assertEquals(-90.0F, target.yaw(), 1.0E-3F, "mirando al este, a la pared");
        // La cara de arriba, la de serie, va encima del bloque.
        assertEquals(65.6, note("x", 40, false, 0).labelY(), 1.0E-9);
        assertEquals(WorldNote.UP, WorldNote.sideByName("no existe"));
        assertEquals("west", WorldNote.sideName(4));
    }

    @Test
    void goingToANoteStandsAFewStepsAwayLookingAtIt() {
        WorldNote note = note("Aquí", 40, false, 0);
        // El jugador llega desde el este: queda al este del rótulo, mirando al oeste.
        ViewTeleport.Target target = NoteJump.target(note, "minecraft:overworld", 50, 10.5);
        assertEquals(note.labelX() + NoteJump.BACK, target.x(), 1.0E-9);
        assertEquals(note.labelZ(), target.z(), 1.0E-9);
        assertEquals(90.0F, target.yaw(), 1.0E-3F, "guiñada 90 = mirando al oeste");
        assertTrue(target.pitch() > -10 && target.pitch() < 10, "más o menos a la altura de los ojos");
        // Desde otra dimensión, desde el sur y mirando al norte.
        ViewTeleport.Target fromNether = NoteJump.target(note, "minecraft:the_nether", 0, 0);
        assertEquals("minecraft:overworld", fromNether.dimension());
        assertEquals(note.labelZ() + NoteJump.BACK, fromNether.z(), 1.0E-9);
        assertEquals(180.0F, Math.abs(fromNether.yaw()), 1.0E-3F);
    }
}
