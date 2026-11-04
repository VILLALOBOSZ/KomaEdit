package com.komainu.moxia.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedDefinitionsTest {
    @TempDir
    Path directory;

    @Test
    void savesListsAndDeletesAcrossInstances() {
        Path file = directory.resolve("palettes").resolve("palettes.json");
        NamedDefinitions store = new NamedDefinitions(file, "paleta");

        assertFalse(store.put("Hielo", "blue_ice;packed_ice", false));
        assertFalse(store.put("#roca", "stone;andesite", false));

        NamedDefinitions reopened = new NamedDefinitions(file, "paleta");
        assertEquals(List.of("hielo", "roca"), List.copyOf(reopened.all().keySet()));
        assertEquals(Optional.of("blue_ice;packed_ice"), reopened.get("HIELO"));
        assertTrue(reopened.delete("hielo"));
        assertFalse(reopened.delete("hielo"));
        assertEquals(Optional.empty(), store.get("hielo"));
        assertTrue(Files.isRegularFile(file));
    }

    /**
     * Lo leído de cada archivo se guarda para no volver a leerlo, pero no después de irse su jugador: con cientos de
     * jugadores, o mundo tras mundo en un solo jugador, se iba acumulando mientras durara el proceso.
     */
    @Test
    void whatWasReadForAPlayerIsForgottenWhenTheyLeave() {
        Path ana = directory.resolve("players").resolve("ana");
        Path luis = directory.resolve("players").resolve("luis");
        NamedDefinitions anas = new NamedDefinitions(ana.resolve("palettes.json"), "paleta");
        NamedDefinitions luiss = new NamedDefinitions(luis.resolve("noises.json"), "ruido");
        anas.put("hielo", "blue_ice", false);
        luiss.put("nubes", "Pe(f:0.02)", false);
        assertEquals(Optional.of("blue_ice"), anas.get("hielo"));
        assertEquals(Optional.of("Pe(f:0.02)"), luiss.get("nubes"));
        assertTrue(NamedDefinitions.remembers(ana.resolve("palettes.json")));

        NamedDefinitions.forgetUnder(ana);
        assertFalse(NamedDefinitions.remembers(ana.resolve("palettes.json")), "lo de quien se fue sigue en memoria");
        assertTrue(NamedDefinitions.remembers(luis.resolve("noises.json")), "lo de los demás se queda");
        assertEquals(Optional.of("blue_ice"), anas.get("hielo"), "al volver se lee otra vez");
        NamedDefinitions.forgetAll();
        assertFalse(NamedDefinitions.remembers(luis.resolve("noises.json")));
    }

    @Test
    void refusesToOverwriteWithoutForce() {
        NamedDefinitions store = new NamedDefinitions(directory.resolve("noises.json"), "ruido");
        store.put("nubes", "Pe(f:0.02)", false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> store.put("nubes", "Si()", false));
        assertTrue(error.getMessage().contains("-f"));
        assertTrue(store.put("nubes", "Si()", true));
        assertEquals(Optional.of("Si()"), store.get("nubes"));
    }

    @Test
    void validatesNamesAndValues() {
        NamedDefinitions store = new NamedDefinitions(directory.resolve("x.json"), "paleta");
        assertThrows(IllegalArgumentException.class, () -> store.put("con espacio", "stone", false));
        assertThrows(IllegalArgumentException.class, () -> store.put("../fuera", "stone", false));
        assertThrows(IllegalArgumentException.class, () -> store.put("", "stone", false));
        assertThrows(IllegalArgumentException.class, () -> store.put("vacia", " ", false));
        assertEquals("roca_2", NamedDefinitions.normalizeName(" #Roca_2 "));
    }

    @Test
    void whatWasReadIsRememberedUntilTheFileChanges() throws Exception {
        Path file = directory.resolve("remembered.json");
        NamedDefinitions first = new NamedDefinitions(file, "paleta");
        first.put("mia", "stone;andesite", false);
        // Otra instancia (como en cada clic de un pincel) ve lo guardado sin volver a leer el archivo.
        NamedDefinitions second = new NamedDefinitions(file, "paleta");
        assertEquals(Optional.of("stone;andesite"), second.get("mia"));
        second.all().put("trampa", "x");
        assertEquals(Optional.empty(), first.get("trampa"), "lo que devuelve es una copia");
        first.put("mia", "stone;andesite;tuff", true);
        assertEquals(Optional.of("stone;andesite;tuff"), second.get("mia"), "lo guardado se ve al momento");
        // Cambiado a mano fuera del juego: otra fecha y otro tamaño, se vuelve a leer.
        Files.writeString(file, "{\"version\":1,\"entries\":{\"mia\":\"dirt\",\"otra\":\"sand\"}}");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()
                + 5_000L));
        assertEquals(Optional.of("dirt"), second.get("mia"));
        assertEquals(Optional.of("sand"), new NamedDefinitions(file, "paleta").get("otra"));
        Files.delete(file);
        assertTrue(second.all().isEmpty(), "sin archivo, vacío");
    }

    /**
     * Una prerelease guardó las paletas con un formato más nuevo (una con etiquetas, que esta versión no entiende) y
     * se vuelve a la estable: se usan las que entiende, pero guardar una paleta reescribía el archivo sin la de las
     * etiquetas y se perdía al volver a actualizar.
     */
    @Test
    void aFileFromANewerVersionIsUsedButNeverRewritten() throws Exception {
        Path file = directory.resolve("palettes.json");
        String newer = "{\"version\":2,\"entries\":{\"roca\":\"stone;andesite\","
                + "\"bosque\":{\"value\":\"moss_block;grass_block\",\"tags\":[\"verde\"]}}}";
        Files.writeString(file, newer);
        NamedDefinitions store = new NamedDefinitions(file, "paleta");
        assertEquals(Optional.of("stone;andesite"), store.get("roca"), "lo que entiende se usa");

        IllegalStateException saving = assertThrows(IllegalStateException.class,
                () -> store.put("hielo", "blue_ice", false));
        assertTrue(saving.getMessage().contains("versión más nueva"), saving.getMessage());
        assertThrows(IllegalStateException.class, () -> store.delete("roca"));
        assertEquals(newer, Files.readString(file), "el archivo sigue entero");
    }

    @Test
    void reportsUnreadableFiles() throws Exception {
        Path file = directory.resolve("broken.json");
        Files.writeString(file, "{not json");
        NamedDefinitions store = new NamedDefinitions(file, "paleta");

        assertThrows(IllegalStateException.class, store::all);
    }
}
