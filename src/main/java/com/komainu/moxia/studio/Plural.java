package com.komainu.moxia.studio;

/**
 * Singular y plural de los textos con un número delante («1 punto», «3 puntos»): con 1 se usa la clave con
 * {@code .one} (que existe en los dos idiomas: lo comprueba {@code StudioTranslationsTest}), como ya hacían las
 * unidades ({@link ValueUnit}). Sin esto salían «Faltan 1 puntos» o «Construir · faltan 1 puntos».
 */
public final class Plural {
    private Plural() {
    }

    /** La clave para {@code count}: {@code key.one} con 1 y {@code key} con lo demás. */
    public static String key(String key, long count) {
        return count == 1 ? key + ".one" : key;
    }
}
