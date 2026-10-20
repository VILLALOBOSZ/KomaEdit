package com.komainu.moxia.capability;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * «Reemplazar familia»: cambia el material y conserva la forma. Con planchas de abeto en la mano, una escalera de
 * roble pasa a escalera de abeto, una losa de roble a losa de abeto y las planchas de roble a planchas de abeto.
 *
 * <p>Las familias son las de Minecraft (madera, piedra, ladrillos…): un bloque base y sus variantes (escaleras,
 * losa, muro, valla, puerta…). Trabaja con ids en texto; el servidor la llena con {@code BlockFamilies}.</p>
 */
public final class BlockFamilyIndex {
    /** Variante del bloque base de la familia. */
    public static final String BASE = "base";
    /** Variantes de textura: un bloque puede ser la base de su familia y una de estas en otra. */
    private static final Set<String> TEXTURE_VARIANTS = Set.of("polished", "chiseled", "cut", "cracked", "mosaic");

    /** Una familia: id, bloque base y variantes por nombre ({@code stairs}, {@code slab}…). */
    public record Family(String id, String base, Map<String, String> variants) {
        public Family {
            variants = Map.copyOf(variants);
        }

        public String block(String variant) {
            return BASE.equals(variant) ? base : variants.get(variant);
        }
    }

    private record Membership(Family family, String variant) {
    }

    private final Map<String, Membership> asTarget = new HashMap<>();
    private final Map<String, Family> asHeld = new HashMap<>();

    public BlockFamilyIndex(List<Family> families) {
        Map<String, Membership> base = new LinkedHashMap<>();
        Map<String, Membership> texture = new LinkedHashMap<>();
        Map<String, Membership> shape = new LinkedHashMap<>();
        Map<String, Family> heldAsVariant = new HashMap<>();
        for (Family family : families) {
            base.putIfAbsent(family.base(), new Membership(family, BASE));
            asHeld.putIfAbsent(family.base(), family);
            for (Map.Entry<String, String> variant : family.variants().entrySet()) {
                Membership membership = new Membership(family, variant.getKey());
                (TEXTURE_VARIANTS.contains(variant.getKey()) ? texture : shape)
                        .putIfAbsent(variant.getValue(), membership);
                heldAsVariant.putIfAbsent(variant.getValue(), family);
            }
        }
        // Para el bloque apuntado manda la forma: una variante de forma gana a una de textura y esta a la base.
        asTarget.putAll(base);
        asTarget.putAll(texture);
        asTarget.putAll(shape);
        // Para el de la mano manda el material: la familia de la que es base y, si no, aquella en la que aparece.
        heldAsVariant.forEach(asHeld::putIfAbsent);
    }

    /**
     * Bloque que sale al reemplazar {@code target} con {@code held} en la mano, o vacío si no hay equivalente (y
     * entonces se reemplaza por el bloque de la mano tal cual).
     */
    public Optional<String> swap(String target, String held) {
        Membership membership = asTarget.get(target);
        Family family = asHeld.get(held);
        if (membership == null || family == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(family.block(membership.variant()));
    }

    /** Variante del bloque como bloque apuntado ({@code stairs}, {@code base}…), o {@code null}. */
    public String variantOf(String block) {
        Membership membership = asTarget.get(block);
        return membership == null ? null : membership.variant();
    }

    public boolean isEmpty() {
        return asTarget.isEmpty();
    }
}
