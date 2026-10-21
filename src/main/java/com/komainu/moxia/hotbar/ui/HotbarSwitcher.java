package com.komainu.moxia.hotbar.ui;

/**
 * La tecla de las barras (Alt izquierdo de serie, como en Axiom), sin Minecraft.
 *
 * <ul>
 *     <li><b>Mantenida</b>: al poco aparece la tira con las barras; la rueda elige otra (abajo, la siguiente) y al
 *     soltar se cambia a la elegida. Si se vuelve a la que estaba en uso, no se hace nada.</li>
 *     <li><b>Tocada</b> (soltada antes de {@link #TAP_TICKS} sin mover la rueda ni usar otra tecla o el ratón):
 *     abre el menú circular.</li>
 *     <li>Si la ventana pierde el foco con la tecla pulsada (Alt+Tab), al volver no pasa nada.</li>
 * </ul>
 */
public final class HotbarSwitcher {
    /** Un cuarto de segundo: soltar antes es un toque. */
    public static final int TAP_TICKS = 5;
    /** La tira sale tras estos ticks mantenida (o con la rueda): un toque no la enseña. */
    public static final int SHOW_TICKS = 4;

    public enum Outcome {
        NONE,
        APPLY,
        OPEN_MENU
    }

    /** Lo que hay que hacer al soltar: cambiar a la barra {@code index}, abrir el menú o nada. */
    public record Result(Outcome outcome, int index) {
        static final Result NONE = new Result(Outcome.NONE, -1);
    }

    private boolean held;
    private int heldTicks;
    private boolean scrolled;
    private boolean modifier;
    private int selected = -1;
    private int count;

    /** La tecla se ha pulsado. */
    public void press(int count, int active) {
        held = true;
        heldTicks = 0;
        scrolled = false;
        modifier = false;
        this.count = Math.max(0, count);
        selected = active >= 0 && active < this.count ? active : -1;
    }

    public void tick() {
        if (held) {
            heldTicks++;
        }
    }

    public boolean held() {
        return held;
    }

    public boolean visible() {
        return held && (scrolled || heldTicks >= SHOW_TICKS && !modifier);
    }

    /** Barra elegida en la tira, o -1. */
    public int selected() {
        return selected;
    }

    /**
     * La rueda con la tecla mantenida.
     *
     * @param amount lo que da Minecraft: positivo hacia arriba
     * @return {@code true} si la rueda era de la tira (y no debe cambiar de casilla)
     */
    public boolean scroll(double amount) {
        if (!held || amount == 0) {
            return held;
        }
        scrolled = true;
        if (count == 0) {
            return true;
        }
        int step = amount < 0 ? 1 : -1;
        selected = selected < 0 ? (step > 0 ? 0 : count - 1) : Math.floorMod(selected + step, count);
        return true;
    }

    /**
     * Con la tecla pulsada se usó otra tecla o el ratón (Alt+clic en un display, Ctrl+Alt+R del fantasma, arrastrar
     * sin pasos): era un modificador. Al soltarla no se abre el menú y la tira no sale si no se mueve la rueda.
     */
    public void usedAsModifier() {
        if (held) {
            modifier = true;
        }
    }

    /** Las barras cambiaron con la tecla pulsada (llegó otro catálogo): se conserva la elegida si sigue. */
    public void resize(int newCount, int newSelected) {
        count = Math.max(0, newCount);
        selected = newSelected >= 0 && newSelected < count ? newSelected : -1;
    }

    /**
     * La tecla se ha soltado.
     *
     * @param focused la ventana tiene el foco (si no, se soltó por un Alt+Tab)
     * @param active  barra en uso ahora
     */
    public Result release(boolean focused, int active) {
        if (!held) {
            return Result.NONE;
        }
        held = false;
        if (!focused) {
            return Result.NONE;
        }
        if (!scrolled && !modifier && heldTicks < TAP_TICKS) {
            return new Result(Outcome.OPEN_MENU, -1);
        }
        if (scrolled && selected >= 0 && selected != active) {
            return new Result(Outcome.APPLY, selected);
        }
        return Result.NONE;
    }

    /** Se abrió otra pantalla o se perdió la conexión: la tira se va sin hacer nada. */
    public void cancel() {
        held = false;
        scrolled = false;
    }
}
