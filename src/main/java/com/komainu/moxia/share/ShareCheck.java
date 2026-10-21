package com.komainu.moxia.share;

import com.komainu.moxia.gradient.BlockSpec;
import com.komainu.moxia.shape.tube.TubeFormula;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que necesita un {@link ShareKind} para comprobar un elemento recibido: los bloques del servidor, los límites y
 * dónde apuntar los avisos. Los bloques que faltan se cambian por el más parecido ({@link BlockSubstitutes}) y cada
 * cambio se apunta una vez por elemento.
 */
public final class ShareCheck {
    private final BlockCatalog blocks;
    private final ShareLimits limits;
    private final Map<String, String> substitutes = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, String> replaced = new LinkedHashMap<>();

    public ShareCheck(BlockCatalog blocks, ShareLimits limits) {
        this.blocks = blocks;
        this.limits = limits;
    }

    public BlockCatalog blocks() {
        return blocks;
    }

    public ShareLimits limits() {
        return limits;
    }

    /** Empieza un elemento: los avisos y los cambios de bloque se cuentan por elemento. */
    public void begin() {
        warnings.clear();
        replaced.clear();
    }

    /** Los avisos del elemento, con los bloques cambiados juntos en uno. */
    public List<String> warnings() {
        List<String> result = new ArrayList<>();
        if (!replaced.isEmpty()) {
            List<String> pairs = new ArrayList<>();
            replaced.forEach((from, to) -> pairs.add(from + " → " + brief(to)));
            result.add("Bloques que no hay en este servidor: " + String.join(", ", pairs));
        }
        result.addAll(warnings);
        return List.copyOf(result);
    }

    /** Los cambios de bloque del elemento (el que falta → el que se pone). */
    public Map<String, String> replaced() {
        return Map.copyOf(replaced);
    }

    public void warn(String message) {
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    /**
     * Un bloque con sus propiedades ({@code oak_stairs[facing=east]}) listo para este servidor: el mismo, el más
     * parecido si no existe (con las propiedades que también tenga) o aire.
     *
     * @return el bloque en forma {@code espacio:id[propiedades]}
     */
    public String block(String spec) {
        BlockSpec parsed = BlockSpec.parse(spec);
        String id = parsed.id();
        String target = id;
        if (BlockSubstitutes.restricted(id)) {
            target = BlockSubstitutes.AIR;
            warn("Bloques de administración cambiados por aire: " + brief(id));
        } else if (!blocks.has(id)) {
            target = substitutes.computeIfAbsent(id, missing -> BlockSubstitutes.best(missing, blocks));
            replaced.put(brief(id), target);
        }
        List<String> dropped = new ArrayList<>();
        Map<String, String> properties = BlockSubstitutes.keepValid(target, parsed.properties(), blocks,
                target.equals(id) ? dropped : null);
        if (!dropped.isEmpty()) {
            warn("Propiedades que " + brief(id) + " no tiene aquí: " + String.join(", ", dropped));
        }
        return new BlockSpec(target, properties).toString();
    }

    /** Un patrón o una máscara de WorldEdit con sus bloques ya comprobados. */
    public String pattern(String what, String text) {
        String value = ShareText.setting(what, text, 1024);
        return PatternBlocks.rewrite(value, this::block);
    }

    /** Una fórmula: pasa por el compilador de {@link TubeFormula} (sin variables ni funciones peligrosas) o lanza. */
    public String formula(String source) {
        TubeFormula.compile(source);
        return source.strip();
    }

    private static String brief(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
