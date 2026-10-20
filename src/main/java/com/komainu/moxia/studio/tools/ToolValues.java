package com.komainu.moxia.studio.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Los valores de los ajustes de una tarjeta. Inmutable: cada cambio da otro objeto, así el historial de ajustes del
 * Studio puede guardarlos tal cual. Un número {@code auto} sin valor vale {@code null}; un bloque opcional sin
 * elegir, también.
 */
public final class ToolValues {
    private final Map<String, ToolParam> params;
    private final Map<String, Object> values;

    private ToolValues(Map<String, ToolParam> params, Map<String, Object> values) {
        this.params = params;
        this.values = values;
    }

    /** Los valores de serie de {@code params}. */
    public static ToolValues defaults(List<ToolParam> params) {
        Map<String, ToolParam> byId = new LinkedHashMap<>();
        Map<String, Object> values = new LinkedHashMap<>();
        for (ToolParam param : params) {
            if (byId.put(param.id(), param) != null) {
                throw new IllegalArgumentException("ajuste repetido: " + param.id());
            }
            values.put(param.id(), defaultOf(param));
        }
        return new ToolValues(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(values));
    }

    private static Object defaultOf(ToolParam param) {
        return switch (param) {
            case ToolParam.Number number -> number.auto() ? null : number.defaultValue();
            case ToolParam.Choice choice -> choice.defaultValue();
            case ToolParam.Toggle toggle -> toggle.defaultValue();
            case ToolParam.Block block -> block.defaultValue();
            case ToolParam.Text text -> text.defaultValue();
        };
    }

    public ToolParam param(String id) {
        ToolParam param = params.get(id);
        if (param == null) {
            throw new IllegalArgumentException("ajuste desconocido: " + id);
        }
        return param;
    }

    /** Número escalado, o {@code null} si está en automático. */
    public Integer number(String id) {
        return (Integer) values.get(checked(id, ToolParam.Number.class));
    }

    /** Número escalado o, en automático, {@code fallback}. */
    public int number(String id, int fallback) {
        Integer value = number(id);
        return value == null ? fallback : value;
    }

    /** Número real (sin escalar), o {@code null} en automático. */
    public Double decimal(String id) {
        Integer value = number(id);
        return value == null ? null : ((ToolParam.Number) param(id)).decimal(value);
    }

    /** Número tal como va en la orden ({@code 1.25}), o {@code null} en automático. */
    public String formatted(String id) {
        Integer value = number(id);
        return value == null ? null : ((ToolParam.Number) param(id)).format(value);
    }

    public String choice(String id) {
        return (String) values.get(checked(id, ToolParam.Choice.class));
    }

    public boolean toggle(String id) {
        return (Boolean) values.get(checked(id, ToolParam.Toggle.class));
    }

    /** Bloque elegido, o {@code null} si es de serie. */
    public String block(String id) {
        return (String) values.get(checked(id, ToolParam.Block.class));
    }

    public String text(String id) {
        return (String) values.get(checked(id, ToolParam.Text.class));
    }

    /** Si el ajuste tiene el valor de serie. */
    public boolean isDefault(String id) {
        return Objects.equals(values.get(id), defaultOf(param(id)));
    }

    /**
     * Otro valor para {@code id}. Los números se ajustan a su rango ({@code null} solo si admite automático); las
     * opciones fijas tienen que existir; los textos se recortan a su largo.
     */
    public ToolValues with(String id, Object value) {
        ToolParam param = param(id);
        Object accepted = switch (param) {
            case ToolParam.Number number -> {
                if (value == null) {
                    yield number.auto() ? null : number.defaultValue();
                }
                yield number.range().clamp(((java.lang.Number) value).intValue());
            }
            case ToolParam.Choice choice -> {
                String option = value == null ? choice.defaultValue() : value.toString();
                if (!choice.dynamic() && !choice.options().contains(option)) {
                    throw new IllegalArgumentException("opción desconocida de " + id + ": " + option);
                }
                yield option;
            }
            case ToolParam.Toggle toggle -> value == null ? toggle.defaultValue() : (Boolean) value;
            case ToolParam.Block block -> value == null || value.toString().isBlank() ? block.defaultValue()
                    : value.toString().strip();
            case ToolParam.Text text -> {
                String typed = value == null ? "" : value.toString();
                yield typed.length() > text.maxLength() ? typed.substring(0, text.maxLength()) : typed;
            }
        };
        if (Objects.equals(values.get(id), accepted)) {
            return this;
        }
        Map<String, Object> next = new LinkedHashMap<>(values);
        next.put(id, accepted);
        return new ToolValues(params, Collections.unmodifiableMap(next));
    }

    /** Vuelve {@code id} a su valor de serie. */
    public ToolValues reset(String id) {
        Map<String, Object> next = new LinkedHashMap<>(values);
        next.put(id, defaultOf(param(id)));
        return new ToolValues(params, Collections.unmodifiableMap(next));
    }

    /** Los valores que no son los de serie, para guardarlos (números como enteros escalados). */
    public Map<String, Object> changed() {
        Map<String, Object> changed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!isDefault(entry.getKey()) && entry.getValue() != null) {
                changed.put(entry.getKey(), entry.getValue());
            }
        }
        return changed;
    }

    /**
     * Los valores de serie con lo guardado encima. Lo que ya no existe, no es del tipo del ajuste o no se acepta
     * (una opción que se quitó) se ignora: un archivo de otra versión nunca rompe la tarjeta.
     */
    public ToolValues restore(Map<String, ?> stored) {
        ToolValues result = this;
        for (Map.Entry<String, ?> entry : stored.entrySet()) {
            ToolParam param = params.get(entry.getKey());
            Object value = entry.getValue();
            if (param == null || value == null) {
                continue;
            }
            boolean fits = switch (param) {
                case ToolParam.Number ignored -> value instanceof java.lang.Number;
                case ToolParam.Toggle ignored -> value instanceof Boolean;
                default -> value instanceof String;
            };
            if (!fits) {
                continue;
            }
            try {
                result = result.with(entry.getKey(), value);
            } catch (IllegalArgumentException ignored) {
                // Una opción que ya no existe: se queda la de serie.
            }
        }
        return result;
    }

    private String checked(String id, Class<? extends ToolParam> type) {
        ToolParam param = param(id);
        if (!type.isInstance(param)) {
            throw new IllegalArgumentException(id + " no es " + type.getSimpleName());
        }
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ToolValues that && values.equals(that.values) && params.keySet()
                .equals(that.params.keySet());
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
