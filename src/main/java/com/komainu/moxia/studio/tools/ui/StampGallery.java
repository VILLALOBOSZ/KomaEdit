package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.studio.ShapeSearch;
import com.komainu.moxia.terrain.OwnStampSummary;
import com.komainu.moxia.terrain.StampLibrary;
import com.komainu.moxia.terrain.stamp.StampCategory;
import com.komainu.moxia.terrain.stamp.StampInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Qué enseña la galería de sellos del pincel de relieve: las favoritas (si hay alguna), los propios del servidor
 * («Míos»: los capturados del mundo y los PNG de la carpeta), una pestaña por categoría y, con algo escrito en el
 * buscador, los sellos de cualquier pestaña cuyo nombre, descripción, categoría o autor encajen.
 */
public final class StampGallery {
    /** La pestaña de las favoritas. */
    public static final String FAVORITES = "favoritos";
    /** La pestaña de los sellos propios. */
    public static final String MINE = "mios";

    private StampGallery() {
    }

    /** Las pestañas en orden: favoritas (si hay alguna), «Míos» y las categorías. */
    public static List<String> tabs(Collection<String> favorites) {
        return tabs(favorites, List.of());
    }

    public static List<String> tabs(Collection<String> favorites, List<OwnStampSummary> own) {
        List<String> tabs = new ArrayList<>();
        if (favorites.stream().anyMatch(name -> StampLibrary.has(name) || isOwn(name, own))) {
            tabs.add(FAVORITES);
        }
        tabs.add(MINE);
        for (StampCategory category : StampCategory.values()) {
            tabs.add(category.id());
        }
        return tabs;
    }

    /** La pestaña donde está un sello (la primera categoría si no es de serie). */
    public static String tabOf(String stamp) {
        StampInfo info = StampLibrary.info(stamp);
        return info == null ? StampCategory.values()[0].id() : info.category().id();
    }

    /** La pestaña de lo elegido: «Míos» si es un sello propio. */
    public static String tabOf(String stamp, String custom, List<OwnStampSummary> own) {
        return custom != null && isOwn(custom, own) ? MINE : tabOf(stamp);
    }

    public static boolean isOwn(String name, List<OwnStampSummary> own) {
        for (OwnStampSummary stamp : own) {
            if (stamp.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> visible(String query, String tab, Collection<String> favorites,
                                       Function<String, String> text) {
        return visible(query, tab, favorites, text, List.of());
    }

    /**
     * Los sellos que se ven.
     *
     * @param query     lo escrito en el buscador (vacío: la pestaña)
     * @param tab       la pestaña: el id de una categoría, {@link #FAVORITES} o {@link #MINE}
     * @param favorites los favoritos del jugador
     * @param text      el nombre y la descripción traducidos de un sello de serie, para buscar en el idioma del juego
     * @param own       los sellos propios, en el orden del catálogo (el más nuevo primero)
     */
    public static List<String> visible(String query, String tab, Collection<String> favorites,
                                       Function<String, String> text, List<OwnStampSummary> own) {
        List<String> result = new ArrayList<>();
        String wanted = ShapeSearch.normalize(query);
        if (!wanted.isEmpty()) {
            for (OwnStampSummary stamp : own) {
                if (matches(wanted, stamp.name().replace('_', ' ') + " " + stamp.description() + " "
                        + stamp.author() + " " + stamp.category().displayName() + " mios propios")) {
                    result.add(stamp.name());
                }
            }
            for (StampInfo info : StampLibrary.all()) {
                if (matches(wanted, info.name().replace('_', ' ') + " " + text.apply(info.name()) + " "
                        + info.category().displayName() + " " + info.description())) {
                    result.add(info.name());
                }
            }
            return result;
        }
        if (FAVORITES.equals(tab)) {
            for (StampInfo info : StampLibrary.all()) {
                if (favorites.contains(info.name())) {
                    result.add(info.name());
                }
            }
            for (OwnStampSummary stamp : own) {
                if (favorites.contains(stamp.name())) {
                    result.add(stamp.name());
                }
            }
            return result;
        }
        if (MINE.equals(tab)) {
            own.forEach(stamp -> result.add(stamp.name()));
            return result;
        }
        for (StampInfo info : StampLibrary.all()) {
            if (info.category().id().equals(tab)) {
                result.add(info.name());
            }
        }
        return result;
    }

    private static boolean matches(String wanted, String text) {
        String haystack = ShapeSearch.normalize(text);
        for (String word : wanted.split(" ")) {
            if (!word.isEmpty() && !haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }
}
