package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.studio.ShapeSearch;
import com.komainu.moxia.terrain.StampLibrary;
import com.komainu.moxia.terrain.stamp.StampCategory;
import com.komainu.moxia.terrain.stamp.StampInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Qué enseña la galería de sellos del pincel de relieve: una pestaña por categoría, las favoritas (si hay alguna) y,
 * con algo escrito en el buscador, los sellos de cualquier categoría cuyo nombre, descripción o categoría encajen.
 */
public final class StampGallery {
    /** La pestaña de las favoritas. */
    public static final String FAVORITES = "favoritos";

    private StampGallery() {
    }

    /** Las pestañas en orden: favoritas (si hay alguna) y las categorías. */
    public static List<String> tabs(Collection<String> favorites) {
        List<String> tabs = new ArrayList<>();
        if (favorites.stream().anyMatch(StampLibrary::has)) {
            tabs.add(FAVORITES);
        }
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

    /**
     * Los sellos que se ven.
     *
     * @param query     lo escrito en el buscador (vacío: la pestaña)
     * @param tab       la pestaña: el id de una categoría o {@link #FAVORITES}
     * @param favorites los favoritos del jugador
     * @param text      el nombre y la descripción traducidos de un sello, para buscar en el idioma del juego
     */
    public static List<String> visible(String query, String tab, Collection<String> favorites,
                                       Function<String, String> text) {
        List<String> result = new ArrayList<>();
        String wanted = ShapeSearch.normalize(query);
        if (!wanted.isEmpty()) {
            for (StampInfo info : StampLibrary.all()) {
                String haystack = ShapeSearch.normalize(info.name().replace('_', ' ') + " " + text.apply(info.name())
                        + " " + info.category().displayName() + " " + info.description());
                boolean all = true;
                for (String word : wanted.split(" ")) {
                    all &= word.isEmpty() || haystack.contains(word);
                }
                if (all) {
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
            return result;
        }
        for (StampInfo info : StampLibrary.all()) {
            if (info.category().id().equals(tab)) {
                result.add(info.name());
            }
        }
        return result;
    }
}
