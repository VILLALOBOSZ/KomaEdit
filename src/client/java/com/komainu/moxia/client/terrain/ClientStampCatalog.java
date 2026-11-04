package com.komainu.moxia.client.terrain;

import com.komainu.moxia.studio.tools.ui.OwnStampShelf;
import com.komainu.moxia.studio.ui.ChoiceThumbnails;
import com.komainu.moxia.studio.ui.StudioImage;
import com.komainu.moxia.terrain.OwnStampIndex;
import com.komainu.moxia.terrain.OwnStampSummary;
import com.komainu.moxia.terrain.OwnStamps;
import com.komainu.moxia.terrain.StampPayloads;
import com.komainu.moxia.terrain.stamp.StampThumbnails;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Los sellos propios del servidor en el cliente: el último catálogo y el relieve en pequeño de los que ya llegaron,
 * del que sale la miniatura con los colores de su categoría (como las de serie). Cada miniatura se pide una vez por
 * sesión; si un sello cambia, cambia su huella y se pide la nueva. Se vacía al salir del servidor. Todo pasa en el
 * hilo del cliente.
 */
public final class ClientStampCatalog {
    private static final long REFRESH_MILLIS = 3_000L;
    private static final int MAX_PREVIEWS = 256;

    private static List<OwnStampSummary> stamps = List.of();
    private static boolean received;
    private static boolean canCapture;
    private static long refreshedAt;
    private static final Map<String, StudioImage> IMAGES = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();

    public static final OwnStampShelf SHELF = new OwnStampShelf() {
        @Override
        public boolean received() {
            return received;
        }

        @Override
        public List<OwnStampSummary> list() {
            return stamps;
        }

        @Override
        public StudioImage thumbnail(OwnStampSummary stamp) {
            StudioImage image = IMAGES.get(stamp.hash());
            if (image == null && REQUESTED.add(stamp.hash())
                    && ClientPlayNetworking.canSend(StampPayloads.Request.ID)) {
                ClientPlayNetworking.send(new StampPayloads.Request(StampPayloads.Request.PREVIEWS,
                        List.of(stamp.hash())));
            }
            return image;
        }

        @Override
        public boolean canCapture() {
            return canCapture;
        }

        @Override
        public void refresh(boolean force) {
            long now = System.currentTimeMillis();
            if (!force && now - refreshedAt < REFRESH_MILLIS) {
                return;
            }
            refreshedAt = now;
            if (ClientPlayNetworking.canSend(StampPayloads.Request.ID)) {
                ClientPlayNetworking.send(new StampPayloads.Request(StampPayloads.Request.CATALOG, List.of()));
            }
        }
    };

    private ClientStampCatalog() {
    }

    public static void update(StampPayloads.Catalog catalog) {
        stamps = catalog.stamps();
        canCapture = catalog.canCapture();
        received = true;
        List<com.komainu.moxia.terrain.stamp.StampInfo> infos = new ArrayList<>();
        catalog.stamps().forEach(stamp -> infos.add(stamp.info()));
        // La tarjeta saca de aquí la altura natural y la paleta automática de cada sello propio.
        OwnStampIndex.replace(infos);
    }

    /** El relieve en pequeño de un sello: su miniatura, pintada como las de serie. */
    public static void receive(OwnStamps.Preview preview) {
        OwnStampSummary stamp = null;
        for (OwnStampSummary candidate : stamps) {
            if (candidate.hash().equals(preview.hash())) {
                stamp = candidate;
            }
        }
        if (stamp == null) {
            return;
        }
        if (IMAGES.size() >= MAX_PREVIEWS) {
            IMAGES.clear();
            REQUESTED.clear();
        }
        OwnStampSummary chosen = stamp;
        int side = ChoiceThumbnails.SIZE;
        int[] pixels = StampThumbnails.argb(preview.stamp(chosen.name()), chosen.category(), chosen.heightRatio(),
                side);
        IMAGES.put(preview.hash(), StudioImage.of("own_stamp:" + preview.hash(), side, side, () -> pixels));
    }

    public static void clear() {
        stamps = List.of();
        received = false;
        canCapture = false;
        refreshedAt = 0L;
        IMAGES.clear();
        REQUESTED.clear();
        OwnStampIndex.clear();
    }
}
