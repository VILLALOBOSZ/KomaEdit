package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.ui.ChoiceThumbnails;
import com.komainu.moxia.studio.ui.FakeStudioHost;
import com.komainu.moxia.studio.ui.StudioDriver;
import com.komainu.moxia.studio.ui.StudioKey;
import com.komainu.moxia.studio.ui.StudioStage;
import com.komainu.moxia.studio.ui.StudioView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La galería de sellos del Studio: por categorías, con buscador y favoritos. */
class StampGalleryTest {
    private static final Function<String, String> NO_TRANSLATION = name -> "";

    @Test
    void tabsAreTheCategoriesAndFavouritesOnceThereAreSome() {
        assertEquals(14, StampGallery.tabs(List.of()).size());
        assertEquals(StampGallery.FAVORITES, StampGallery.tabs(List.of("pico")).getFirst());
        assertEquals(14, StampGallery.tabs(List.of("no_es_de_serie")).size(), "un favorito que ya no existe no cuenta");
        assertEquals("volcanes", StampGallery.tabOf("cono_ceniza"));
        assertEquals(List.of("duna", "dunas", "duna_estrella", "duna_lineal", "duna_parabolica", "barjanes"),
                StampGallery.visible("", "dunas", List.of(), NO_TRANSLATION));
        assertEquals(List.of("pico", "isla"), StampGallery.visible("", StampGallery.FAVORITES, List.of("isla", "pico"),
                NO_TRANSLATION), "las favoritas en el orden de la biblioteca");
    }

    @Test
    void searchingLooksInEveryCategoryByNameDescriptionOrCategory() {
        List<String> volcanic = StampGallery.visible("volcán", "montanas", List.of(), NO_TRANSLATION);
        assertTrue(volcanic.containsAll(List.of("volcan", "volcan_escudo", "cono_ceniza", "colada")), volcanic.toString());
        assertTrue(StampGallery.visible("isla caldera", "", List.of(), NO_TRANSLATION).contains("isla_media_luna"));
        assertTrue(StampGallery.visible("seif", "", List.of(), NO_TRANSLATION).contains("duna_lineal"));
        assertTrue(StampGallery.visible("zzz", "", List.of(), NO_TRANSLATION).isEmpty());
        assertEquals(List.of("tor"), StampGallery.visible("x", "", List.of(),
                name -> name.equals("tor") ? "x" : ""), "también en el idioma del juego");
    }

    @Test
    void theStudioGalleryBrowsesSearchesAndMarksFavourites() {
        ChoiceThumbnails.setBlocking(true);
        StudioDriver studio = new StudioDriver(new StudioView(), new FakeStudioHost("es_es", false), 960, 1600);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 0, false);
        studio.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.OFF);
        studio.host.info = new ToolsInfo(true, new int[]{8, 5, 8}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of("minecraft:plains"), null);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        ToolsPanel panel = studio.view.tools();
        panel.select(ToolCatalog.byId("stamp"), studio.host);
        assertTrue(studio.has("Montañas"), "empieza en la categoría del sello elegido");
        // Los nombres largos van en dos líneas bajo la miniatura.
        assertTrue(studio.shows("Cumbre") && studio.has("Espolón"), studio.texts().toString());
        assertFalse(studio.shows("escudo"));

        studio.clickValue("Categoría");
        assertTrue(studio.has("Cordilleras") && studio.shows("paralelas"), studio.texts().toString());

        studio.clickKey("tools:stamp_search");
        studio.type("volc");
        assertTrue(studio.shows("escudo") && studio.shows("ceniza"), studio.texts().toString());
        assertFalse(studio.has("Categoría"), "buscando se busca en todas");
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertEquals("volcan", panel.values(ToolCatalog.byId("stamp")).choice("stamp"), "Intro elige el primero");
        assertTrue(studio.has("Volcanes"), "y vuelve a su categoría");

        studio.reset("Cono de ceniza");
        assertTrue(studio.host.preferences.isStampFavorite("cono_ceniza"), "clic derecho: favorito");
        assertEquals(StampGallery.FAVORITES, StampGallery.tabs(studio.host.preferences.stampFavorites()).getFirst());
        assertTrue(StudioPreferences.fromJson(studio.host.preferences.toJson()).isStampFavorite("cono_ceniza"),
                "los favoritos se guardan");
        studio.reset("Cono de ceniza");
        assertFalse(studio.host.preferences.isStampFavorite("cono_ceniza"), "otra vez: se quita");
    }
}
