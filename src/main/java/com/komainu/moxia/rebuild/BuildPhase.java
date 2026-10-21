package com.komainu.moxia.rebuild;

/** Las fases del orden «como un constructor», en el orden en que se levantan. */
public enum BuildPhase {
    FOUNDATION("cimientos"),
    STRUCTURE("estructura"),
    WALLS("paredes"),
    ROOF("tejado"),
    DETAILS("detalles"),
    VEGETATION("vegetación");

    private final String label;

    BuildPhase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
