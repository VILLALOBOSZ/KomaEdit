package com.komainu.moxia.client.reference;

import com.komainu.moxia.reference.ReferenceFormat;
import com.komainu.moxia.reference.ReferenceImage;
import com.komainu.moxia.reference.ReferenceRotation;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** Los textos de una referencia: medidas, postura y el resumen de la lista. */
final class ReferenceTexts {
    private ReferenceTexts() {
    }

    static String blocks(double value) {
        return ReferenceFormat.blocks(value);
    }

    /** «de pie, se lee mirando al norte», «tumbada, se lee mirando a 37°»… */
    static MutableText orientation(ReferenceRotation rotation) {
        String cardinal = ReferenceFormat.cardinal(rotation);
        Text direction = cardinal == null
                ? Text.literal(ReferenceFormat.blocks(rotation.readerYaw()) + "°")
                : Text.translatable("gui.komaedit.reference.cardinal." + cardinal);
        String pose = ReferenceFormat.pose(rotation);
        Text posture = pose.equals("tilted")
                ? Text.translatable("gui.komaedit.reference.pose.tilted", ReferenceFormat.blocks(rotation.tilt()))
                : Text.translatable("gui.komaedit.reference.pose." + pose);
        return Text.translatable("gui.komaedit.reference.orientation", posture, direction);
    }

    /** La línea de la lista: medidas en píxeles y, en el mundo, en bloques y su postura. */
    static MutableText summary(ReferenceImage reference) {
        MutableText text = Text.translatable("gui.komaedit.reference.pixels", reference.pixelWidth(),
                reference.pixelHeight());
        if (reference.mode() == ReferenceImage.Mode.WORLD) {
            text.append(" · ").append(Text.translatable("gui.komaedit.reference.size_blocks",
                    blocks(reference.world().widthBlocks(reference.pixelWidth())),
                    blocks(reference.world().heightBlocks(reference.pixelHeight()))))
                    .append(" · ").append(orientation(reference.world().rotation()));
        }
        if (reference.grid().enabled()) {
            text.append(" · ").append(Text.translatable("gui.komaedit.reference.cells",
                    reference.grid().columns(reference.pixelWidth()), reference.grid().rows(reference.pixelHeight())));
        }
        return text;
    }
}
