package com.komainu.moxia.shape.tube;

import java.util.List;
import java.util.Locale;

/**
 * Fórmulas de serie de la figura {@code expression}. El Studio las recorre con una fila de ciclo y el comando
 * las acepta por nombre; el índice se guarda como la opción numérica {@code preset}.
 *
 * <p>Todas usan {@code n} (lados / puntas) como número de repeticiones alrededor y devuelven un material en
 * (0, 1] pensado para el modo «Patrón figura»: el valor separa piezas (raíl y traviesa, banda y núcleo…).</p>
 */
public enum TubeFormulaPreset {
    WINDOWS("windows", "Ventanas", "Tubo con n ventanas por vuelta y suelo corrido",
            "pared = r > 0.72 && r <= 1; "
                    + "hueco = mod(z, 2.4) < 1.3 && abs(frac(a * n / (2 * pi)) - 0.5) < 0.28 && y > -0.35; "
                    + "pared && !hueco ? r : 0",
            "ventanas", "ventana", "window"),
    RAILS("rails", "Raíles", "Dos raíles sobre traviesas, como una vía",
            "riel = hypot(abs(x) - 0.5, y + 0.55) < 0.15; "
                    + "traviesa = abs(y + 0.78) < 0.1 && abs(x) < 0.85 && mod(z, 0.55) < 0.25; "
                    + "riel ? 1 : traviesa ? 0.4 : 0",
            "railes", "rieles", "via", "rail", "track"),
    STRIPES("stripes", "Espiral", "Núcleo macizo con n franjas en espiral en relieve",
            "banda = frac(a * n / (2 * pi) + z / 3) < 0.5; "
                    + "r <= (banda ? 1 : 0.85) ? (banda ? 0.85 : 0.35) : 0",
            "espiral", "franjas", "spiral", "helix"),
    LATTICE("lattice", "Celosía", "Piel de barras cruzadas en diagonal, n por vuelta",
            "u = abs(frac(a * n / (2 * pi) + z / 2) - 0.5) > 0.38; "
                    + "v = abs(frac(a * n / (2 * pi) - z / 2) - 0.5) > 0.38; "
                    + "r <= 1 && r >= 0.8 && (u || v) ? (u ? 1 : 0.5) : 0",
            "celosia", "rejilla", "grid", "mesh"),
    RIBS("ribs", "Costillas", "Anillos cada 6/n radios unidos por una quilla",
            "anillo = r <= 1 && r >= 0.78 && mod(z, 6 / n) < 0.3; "
                    + "quilla = abs(x) < 0.18 && y < -0.62 && r <= 1; "
                    + "anillo ? 1 : quilla ? 0.45 : 0",
            "costillas", "anillos", "rings");

    private final String id;
    private final String displayName;
    private final String description;
    private final String source;
    private final String[] aliases;
    private TubeFormula compiled;

    TubeFormulaPreset(String id, String displayName, String description, String source, String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.source = source;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String source() {
        return source;
    }

    /** Las fórmulas de serie se compilan una vez; el árbol es inmutable y se comparte entre hilos. */
    public synchronized TubeFormula formula() {
        if (compiled == null) {
            compiled = TubeFormula.compile(source);
        }
        return compiled;
    }

    public static TubeFormulaPreset byIndex(int index) {
        TubeFormulaPreset[] values = values();
        return values[Math.floorMod(index, values.length)];
    }

    /** Busca por id o alias (sin tildes ni mayúsculas); {@code null} si no es una fórmula de serie. */
    public static TubeFormulaPreset find(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
        for (TubeFormulaPreset preset : values()) {
            if (preset.id.equals(normalized) || List.of(preset.aliases).contains(normalized)) {
                return preset;
            }
        }
        return null;
    }

    public static TubeFormulaPreset byId(String input) {
        TubeFormulaPreset preset = find(input);
        if (preset == null) {
            throw new IllegalArgumentException("Fórmula de serie desconocida: " + input + " (" + ids() + ")");
        }
        return preset;
    }

    public static String ids() {
        StringBuilder builder = new StringBuilder();
        for (TubeFormulaPreset preset : values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(preset.id);
        }
        return builder.toString();
    }
}
