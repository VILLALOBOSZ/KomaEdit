package com.komainu.moxia.capability;

import java.util.Set;

/**
 * Qué hace cada clic con las capacidades activas. El cliente lo usa para decidir si intercepta el clic y qué
 * intención manda al servidor; lo que no le toca a ninguna capacidad sigue el camino normal de Minecraft.
 *
 * <p>Clic derecho, por orden: «Arrastrar» se queda con todos los clics derechos; con la mano vacía, «Retocar»;
 * con el mismo bloque que el apuntado, «Extruir»; con otro bloque, «Reemplazar»; con un bloque o un cubo de agua
 * o lava, «Sin actualizaciones» coloca en la cara y «Colocar en el aire» coloca mirando al aire.</p>
 *
 * <p>Clic izquierdo: con un arrastre a medias lo cancela; con el mismo bloque que el apuntado y «Extruir», encoge
 * la capa; con «Sin actualizaciones», rompe sin avisar a los vecinos.</p>
 */
public final class ClickRouting {
    /** Lo que se tiene en la mano principal. */
    public enum Held {
        EMPTY,
        /** Un bloque colocable. */
        BLOCK,
        /** Un cubo de agua o de lava. */
        FLUID,
        /** Una herramienta de KomaEdit (pincel o supervarita): sus clics son suyos. */
        TOOL,
        OTHER
    }

    /** A qué apunta la mira. */
    public enum Target {
        BLOCK,
        AIR,
        ENTITY
    }

    /** Intenciones que viajan al servidor. {@link #NONE} deja el clic a Minecraft. */
    public enum Intent {
        NONE,
        SMEAR_START,
        SMEAR_END,
        SMEAR_CANCEL,
        TWEAK,
        TWEAK_PROPERTY,
        EXTRUDE,
        SHRINK,
        REPLACE,
        PLACE,
        ANGEL,
        BREAK
    }

    /**
     * Situación del clic.
     *
     * @param sameBlock    el bloque de la mano es el mismo que el apuntado
     * @param smearPending hay un arrastre con el origen fijado
     * @param offhandEmpty la mano secundaria está vacía (la F de Retocar solo se usa con las dos vacías)
     */
    public record Context(
            Set<BuildCapability> enabled,
            Held held,
            Target target,
            boolean sameBlock,
            boolean smearPending,
            boolean offhandEmpty
    ) {
        public Context {
            enabled = Set.copyOf(enabled);
        }

        boolean on(BuildCapability capability) {
            return enabled.contains(capability);
        }
    }

    private ClickRouting() {
    }

    public static Intent use(Context context) {
        if (context.held() == Held.TOOL) {
            return Intent.NONE;
        }
        if (context.on(BuildCapability.SMEAR)) {
            if (context.smearPending()) {
                return context.target() == Target.ENTITY ? Intent.NONE : Intent.SMEAR_END;
            }
            if (context.target() == Target.BLOCK) {
                return Intent.SMEAR_START;
            }
        }
        boolean placeable = context.held() == Held.BLOCK || context.held() == Held.FLUID;
        if (context.target() == Target.BLOCK) {
            if (context.held() == Held.EMPTY) {
                return context.on(BuildCapability.TWEAK) ? Intent.TWEAK : Intent.NONE;
            }
            if (context.held() == Held.BLOCK && context.sameBlock() && context.on(BuildCapability.EXTRUDE)) {
                return Intent.EXTRUDE;
            }
            if (context.held() == Held.BLOCK && !context.sameBlock() && context.on(BuildCapability.REPLACE)) {
                return Intent.REPLACE;
            }
            if (placeable && context.on(BuildCapability.NO_UPDATES)) {
                return Intent.PLACE;
            }
            return Intent.NONE;
        }
        if (context.target() == Target.AIR && placeable && context.on(BuildCapability.ANGEL)) {
            return Intent.ANGEL;
        }
        return Intent.NONE;
    }

    public static Intent attack(Context context) {
        if (context.held() == Held.TOOL) {
            return Intent.NONE;
        }
        if (context.smearPending()) {
            return Intent.SMEAR_CANCEL;
        }
        if (context.target() != Target.BLOCK) {
            return Intent.NONE;
        }
        if (context.held() == Held.BLOCK && context.sameBlock() && context.on(BuildCapability.EXTRUDE)) {
            return Intent.SHRINK;
        }
        return context.on(BuildCapability.NO_UPDATES) ? Intent.BREAK : Intent.NONE;
    }

    /** Tecla de cambiar de mano (F): con «Retocar», las dos manos vacías y un bloque delante, cambia de propiedad. */
    public static Intent swap(Context context) {
        return context.on(BuildCapability.TWEAK) && context.held() == Held.EMPTY && context.offhandEmpty()
                && context.target() == Target.BLOCK ? Intent.TWEAK_PROPERTY : Intent.NONE;
    }

    /** ¿Se hace solo al pulsar (no se repite al mantener el botón)? Las acciones de varios bloques y los retoques. */
    public static boolean pressOnly(Intent intent) {
        return switch (intent) {
            case SMEAR_START, SMEAR_END, SMEAR_CANCEL, TWEAK, TWEAK_PROPERTY, EXTRUDE, SHRINK -> true;
            default -> false;
        };
    }

    /** ¿Cambia varios bloques de una vez? Esas acciones van al historial de WorldEdit. */
    public static boolean multiBlock(Intent intent) {
        return intent == Intent.EXTRUDE || intent == Intent.SHRINK || intent == Intent.SMEAR_END;
    }
}
