package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.ui.ChoiceThumbnails;
import com.komainu.moxia.studio.ui.FakeStudioHost;
import com.komainu.moxia.studio.ui.ImageCanvas;
import com.komainu.moxia.studio.ui.StudioDriver;
import com.komainu.moxia.studio.ui.StudioKey;
import com.komainu.moxia.studio.ui.StudioStage;
import com.komainu.moxia.studio.ui.StudioView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    private static StudioDriver studio() {
        return studio(960, 1600);
    }

    private static StudioDriver studio(int width, int height) {
        ChoiceThumbnails.setBlocking(true);
        StudioDriver studio = new StudioDriver(new StudioView(), new FakeStudioHost("es_es", false), width, height);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 0, false);
        studio.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.OFF);
        studio.host.info = new ToolsInfo(true, new int[]{8, 5, 8}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of("minecraft:plains"), null);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("stamp"), studio.host);
        return studio;
    }

    @Test
    void theTileTooltipIsShortAndBelowTheTile() {
        StudioDriver studio = studio();
        Rect cell = studio.widget("Cumbre doble").bounds();
        studio.mouseX = cell.centerX();
        studio.mouseY = cell.centerY();
        List<ImageCanvas.Drawn> texts = new ArrayList<>();
        studio.render();
        texts.addAll(studio.canvas().texts());
        ImageCanvas.Drawn sentence = texts.stream().filter(drawn -> drawn.text().startsWith("Dos cumbres"))
                .findFirst().orElseThrow(() -> new AssertionError("sin tooltip: " + studio.texts()));
        assertTrue(sentence.y() > cell.bottom() || sentence.y() + 10 < cell.y(),
                "el tooltip va debajo (o encima) de la miniatura, no sobre su fila");
        Rect panel = studio.view.frame().panel();
        assertTrue(sentence.x() >= panel.x(), "dentro del panel");
        Rect footer = studio.view.frame().footer();
        for (ImageCanvas.Drawn drawn : texts) {
            if (drawn.text().startsWith("Clic: usar")) {
                assertTrue(drawn.y() >= footer.y(), "qué hacen los clics lo dice el pie, no el tooltip");
            }
        }
        assertTrue(studio.shows("Clic: usar · clic derecho: favorito"), "la ayuda de los clics está en el pie");
    }

    @Test
    void theFooterSaysTheStampTheModeAndTheSeed() {
        StudioDriver studio = studio();
        ToolsPanel panel = studio.view.tools();
        ToolCard card = ToolCatalog.byId("stamp");
        assertTrue(studio.shows("Montaña · Sumar · semilla al azar"), studio.texts().toString());
        assertFalse(studio.texts().stream().filter("Al pincel"::equals).count() > 1, "«Al pincel» una sola vez");
        panel.setValues(card, panel.values(card).with("stamp", "volcan").with("mode", "maximo")
                .with("lock_seed", true));
        assertTrue(studio.shows("Volcán · Máximo · semilla bloqueada"), studio.texts().toString());
        panel.setValues(card, panel.values(card).with("seed", 4217));
        assertTrue(studio.shows("semilla 4217 bloqueada"), studio.texts().toString());
        // En 854×480 el pie tiene una sola línea: el resumen, sin la ayuda de los clics encima.
        StudioDriver small = studio(427, 240);
        assertTrue(small.shows("Montaña"), small.texts().toString());
        assertFalse(small.shows("Clic: usar"), "en compacto no cabe la ayuda");
    }

    @Test
    void theStudioGalleryBrowsesSearchesAndMarksFavourites() {
        StudioDriver studio = studio();
        ToolsPanel panel = studio.view.tools();
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
