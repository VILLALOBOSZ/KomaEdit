package com.komainu.moxia.preview;

/** Si un plan pasa por la vista previa antes de colocarse. */
public final class PreviewPolicy {
    private PreviewPolicy() {
    }

    /**
     * @param blocks      cambios del plan
     * @param alwaysAbove umbral de «previsualizar siempre» del jugador (0 = apagado)
     */
    public static boolean shouldPreview(EditRequest request, int blocks, int alwaysAbove) {
        return switch (request.kind()) {
            case PREVIEW -> true;
            case DEFAULT -> alwaysAbove > 0 && blocks >= alwaysAbove;
            case CONFIRMED, TOOL -> false;
        };
    }
}
