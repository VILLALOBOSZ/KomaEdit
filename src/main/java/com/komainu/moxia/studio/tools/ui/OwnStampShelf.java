package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.studio.ui.StudioImage;
import com.komainu.moxia.terrain.OwnStampSummary;

import java.util.List;

/**
 * Los sellos propios del servidor para la galería de Estampar relieve (la pestaña «Míos»): el último catálogo, sus
 * miniaturas y si el jugador puede capturar más. En el juego lo da el cliente con lo que manda el servidor; en las
 * pruebas y las maquetas, uno de mentira.
 */
public interface OwnStampShelf {
    /** Si ya llegó algún catálogo (si no, la pestaña dice que está cargando). */
    boolean received();

    List<OwnStampSummary> list();

    /** La miniatura del sello, o {@code null} mientras llega (la pide si hace falta). */
    StudioImage thumbnail(OwnStampSummary stamp);

    boolean canCapture();

    /** Pide el catálogo; si se pidió hace nada y no se fuerza, no repite. */
    void refresh(boolean force);

    /** El sello propio con ese nombre, o {@code null}. */
    default OwnStampSummary find(String name) {
        for (OwnStampSummary stamp : list()) {
            if (stamp.name().equals(name)) {
                return stamp;
            }
        }
        return null;
    }
}
