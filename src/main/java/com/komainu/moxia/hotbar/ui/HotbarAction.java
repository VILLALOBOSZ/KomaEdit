package com.komainu.moxia.hotbar.ui;

/**
 * Lo que la tarjeta del Studio pide hacer con las barras guardadas. Va por el paquete de las barras y no por el chat:
 * cada orden de chat suma al antispam de Minecraft y cambiar de barra se hace muchas veces seguidas.
 */
public enum HotbarAction {
    /** Cambiar a la barra. */
    APPLY,
    /** Guardar la barra que se lleva con ese nombre (sin pisar otra). */
    SAVE,
    /** Guardar la que se lleva sustituyendo la que se llame igual. */
    SAVE_OVERWRITE,
    DELETE,
    /** Copiar el código de la barra (o de todas, con el nombre vacío) al portapapeles. */
    EXPORT,
    /** Importar el código {@code kh1:} que haya en el portapapeles. */
    IMPORT_CLIPBOARD,
    /** Volver a la barra de antes del último cambio. */
    UNDO,
    AUTOSAVE_ON,
    AUTOSAVE_OFF
}
