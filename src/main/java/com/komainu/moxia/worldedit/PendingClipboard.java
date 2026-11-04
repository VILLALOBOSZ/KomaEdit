package com.komainu.moxia.worldedit;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.CombinedTransform;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;

import java.util.function.LongConsumer;

/**
 * El portapapeles como lo pegaría {@code //paste}: con su {@code //rotate} y {@code //flip} pendientes ya aplicados.
 * Es lo que usan todas las herramientas que leen el portapapeles ({@code place}, {@code scatter}, {@code array}, sus
 * pinceles, {@code bend}, kitbash, el pegado del editor…), igual que {@code paste}, escalar, los planos y la
 * reconstrucción.
 */
public final class PendingClipboard {
    private PendingClipboard() {
    }

    /**
     * Como {@link #of}, pero {@code check} mira antes el tamaño de la caja que ocupará (y lanza si no cabe): girar
     * copia el portapapeles entero en el hilo del servidor, y quien llamaba solo miraba el tamaño después. Con
     * {@code //copy} de millones de bloques y un {@code //rotate}, cada clic del pincel de colocación (o el kitbash,
     * de 50 000 como mucho) copiaba los millones antes de decir que no cabían.
     */
    public static Clipboard of(ClipboardHolder holder, LongConsumer check) {
        check.accept(region(holder).getVolume());
        return of(holder);
    }

    /**
     * La caja que ocupará el portapapeles con su giro pendiente, sin girarlo: las ocho esquinas giradas alrededor de su
     * origen, como hace WorldEdit al girarlo.
     */
    public static Region region(ClipboardHolder holder) {
        Clipboard clipboard = holder.getClipboard();
        Transform transform = holder.getTransform();
        Region region = clipboard.getRegion();
        if (transform == null || transform.isIdentity()) {
            return region;
        }
        Vector3 minimum = region.getMinimumPoint().toVector3();
        Vector3 maximum = region.getMaximumPoint().toVector3();
        Transform around = new CombinedTransform(new AffineTransform().translate(clipboard.getOrigin().multiply(-1)),
                transform, new AffineTransform().translate(clipboard.getOrigin()));
        Vector3[] corners = {minimum, maximum, minimum.withX(maximum.x()), minimum.withY(maximum.y()),
                minimum.withZ(maximum.z()), maximum.withX(minimum.x()), maximum.withY(minimum.y()),
                maximum.withZ(minimum.z())};
        Vector3 low = around.apply(corners[0]);
        Vector3 high = low;
        for (int index = 1; index < corners.length; index++) {
            Vector3 corner = around.apply(corners[index]);
            low = low.getMinimum(corner);
            high = high.getMaximum(corner);
        }
        return new CuboidRegion(low.floor().toBlockPoint(), high.ceil().toBlockPoint());
    }

    public static Clipboard of(ClipboardHolder holder) {
        Clipboard clipboard = holder.getClipboard();
        Transform transform = holder.getTransform();
        if (transform == null || transform.isIdentity()) {
            return clipboard;
        }
        try {
            return clipboard.transform(transform);
        } catch (WorldEditException exception) {
            throw new IllegalArgumentException("No se pudo girar el portapapeles: " + exception.getMessage(),
                    exception);
        }
    }
}
