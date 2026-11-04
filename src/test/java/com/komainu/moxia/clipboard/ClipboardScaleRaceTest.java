package com.komainu.moxia.clipboard;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Escalar o girar un portapapeles grande tarda unos segundos en un worker. Lo que salga solo sustituye al portapapeles
 * que se leyó: un {@code //copy} o un {@code //rotate} hecho mientras tanto se quedaba pisado por el resultado del de
 * antes (y «restaurar» devolvía el viejo).
 */
class ClipboardScaleRaceTest {
    private static ClipboardHolder holder() {
        return new ClipboardHolder(new BlockArrayClipboard(new CuboidRegion(BlockVector3.at(0, 0, 0),
                BlockVector3.at(1, 1, 1))));
    }

    @Test
    void theResultReplacesOnlyTheClipboardThatWasRead() {
        LocalSession session = new LocalSession();
        ClipboardHolder read = holder();
        session.setClipboard(read);
        Transform transform = read.getTransform();
        assertTrue(ClipboardScaleService.stillCurrent(session, read, transform));

        session.setClipboard(holder());
        assertFalse(ClipboardScaleService.stillCurrent(session, read, transform), "un //copy mientras tanto");

        session.setClipboard(read);
        read.setTransform(new AffineTransform().rotateY(90));
        assertFalse(ClipboardScaleService.stillCurrent(session, read, transform), "un //rotate mientras tanto");

        session.setClipboard(null);
        assertFalse(ClipboardScaleService.stillCurrent(session, read, transform), "un //clearclipboard");
    }
}
