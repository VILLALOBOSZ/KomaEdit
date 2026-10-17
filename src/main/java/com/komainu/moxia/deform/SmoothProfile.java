package com.komainu.moxia.deform;

import com.komainu.moxia.shape.smooth.PieceFit;
import com.komainu.moxia.shape.smooth.SmoothMode;

import java.util.Locale;

/**
 * Perfil de piezas de la bandera {@code -w}, con la notación de ezEdits: {@code Slabs}, {@code SlabsAndStairs}
 * ({@code SAS}), y la cobertura entre paréntesis, {@code Slabs(C:0.7)}. También valen los nombres de KomaEdit
 * ({@code slabs}, {@code stairs}, {@code losas}, {@code escaleras}).
 */
public record SmoothProfile(SmoothMode mode, double coverage) {
    public static final SmoothProfile NONE = new SmoothProfile(SmoothMode.NONE, PieceFit.DEFAULT_COVERAGE);

    public SmoothProfile {
        if (!(coverage >= 0.0 && coverage <= 1.0)) {
            throw new IllegalArgumentException("La cobertura va de 0 a 1");
        }
    }

    public boolean active() {
        return mode != SmoothMode.NONE;
    }

    public static SmoothProfile parse(String input) {
        if (input == null || input.isBlank()) {
            return NONE;
        }
        String text = input.trim();
        String name = text;
        double coverage = PieceFit.DEFAULT_COVERAGE;
        int open = text.indexOf('(');
        if (open >= 0) {
            if (!text.endsWith(")")) {
                throw new IllegalArgumentException("Falta el paréntesis de cierre en el perfil " + text);
            }
            name = text.substring(0, open);
            String body = text.substring(open + 1, text.length() - 1).trim();
            for (String entry : body.isEmpty() ? new String[0] : body.split(",")) {
                String[] pair = entry.split(":", 2);
                String key = pair[0].trim().toLowerCase(Locale.ROOT);
                if (pair.length != 2) {
                    throw new IllegalArgumentException("Parámetro de perfil inválido: " + entry.trim());
                }
                if (key.equals("c") || key.equals("coverage") || key.equals("cobertura")) {
                    try {
                        coverage = Double.parseDouble(pair[1].trim());
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("La cobertura no es un número: " + pair[1].trim());
                    }
                } else {
                    throw new IllegalArgumentException("El perfil solo admite la cobertura (C:0.5); los materiales "
                            + "de cada pieza salen del bloque original");
                }
            }
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        SmoothMode mode = switch (normalized) {
            case "slabsandstairs2d", "sas2d", "ssw", "slabsstairswalls" -> SmoothMode.STAIRS;
            case "layers", "capas" -> throw new IllegalArgumentException(
                    "El perfil Layers (capas de nieve) no está disponible; usa Slabs o SlabsAndStairs");
            default -> SmoothMode.byId(normalized);
        };
        return new SmoothProfile(mode, Math.clamp(coverage, 0.0, 1.0));
    }

    @Override
    public String toString() {
        return active() ? mode.id() + "(C:" + coverage + ")" : "none";
    }
}
