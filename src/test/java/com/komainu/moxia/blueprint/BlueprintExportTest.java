package com.komainu.moxia.blueprint;

import com.komainu.moxia.schematic.SchematicFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que se escribe en {@code /komaedit blueprint export}: de dónde, en qué formato, con qué nombre y adónde. */
class BlueprintExportTest {
    @Test
    void litematicIsTheDefaultAndTheBlueprintNamesTheFile() {
        BlueprintExport.Request request = BlueprintExport.Request.parse("servidor/torre");
        assertEquals(BlueprintExport.Source.BLUEPRINT, request.source());
        assertEquals("servidor/torre", request.blueprint());
        assertEquals(SchematicFormat.LITEMATIC, request.format());
        assertEquals("torre.litematic", request.fileName());
        assertFalse(request.server());
    }

    @Test
    void formatNameAndServerGoInAnyOrder() {
        BlueprintExport.Request request = BlueprintExport.Request.parse("portapapeles -servidor puente_norte nbt");
        assertEquals(BlueprintExport.Source.CLIPBOARD, request.source());
        assertTrue(request.server());
        assertEquals("puente_norte.nbt", request.fileName());
        assertThrows(IllegalArgumentException.class, () -> BlueprintExport.Request.parse("seleccion a b"),
                "un nombre de dos palabras no vale: sobra una");
        BlueprintExport.Request structure = BlueprintExport.Request.parse("selección estructura iglú");
        assertEquals(BlueprintExport.Source.SELECTION, structure.source());
        assertEquals("iglu.nbt", structure.fileName());
        assertEquals("portapapeles.schem", BlueprintExport.Request.parse("clipboard schem").fileName());
        assertEquals("seleccion.litematic", BlueprintExport.Request.parse("seleccion").fileName());
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> BlueprintExport.Request.parse("  "));
        assertTrue(empty.getMessage().contains("portapapeles"), empty.getMessage());
    }
}
