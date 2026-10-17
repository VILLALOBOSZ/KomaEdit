package com.komainu.moxia.ivy;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Paletas de la hiedra: qué hojas lleva, cuánto florece y qué cuelga de los techos. */
public enum IvyStyle {
    HIEDRA("hiedra", "Hiedra", new String[]{"ivy"},
            0.16, IvyMaterial.OAK_LEAVES, IvyMaterial.AZALEA_LEAVES, 0.25, 0.0, 0.0, 0.0, false, 0.0, 0.0, 1.0),
    FLORIDO("florido", "Florido", new String[]{"flores", "flowery", "bloom"},
            0.30, IvyMaterial.AZALEA_LEAVES, IvyMaterial.AZALEA_LEAVES, 0.0, 0.5, 0.0, 0.0, true, 0.6, 0.03, 0.8),
    SELVATICO("selvatico", "Selvático", new String[]{"selva", "jungle"},
            0.40, IvyMaterial.JUNGLE_LEAVES, IvyMaterial.AZALEA_LEAVES, 0.35, 0.08, 0.0, 0.0, true, 0.15, 0.0, 1.6),
    CUEVA("cueva", "Cueva", new String[]{"cave", "gruta"},
            0.06, IvyMaterial.AZALEA_LEAVES, IvyMaterial.AZALEA_LEAVES, 0.0, 0.0, 0.45, 0.4, true, 0.5, 0.06, 1.3);

    private final String id;
    private final String displayName;
    private final String[] aliases;
    private final double leafiness;
    private final IvyMaterial leaves;
    private final IvyMaterial secondaryLeaves;
    private final double secondaryShare;
    private final double flowerShare;
    private final double lichenShare;
    private final double floorLichenShare;
    private final boolean ceilingStrands;
    private final double berries;
    private final double sporeBlossoms;
    private final double hangMultiplier;

    IvyStyle(
            String id,
            String displayName,
            String[] aliases,
            double leafiness,
            IvyMaterial leaves,
            IvyMaterial secondaryLeaves,
            double secondaryShare,
            double flowerShare,
            double lichenShare,
            double floorLichenShare,
            boolean ceilingStrands,
            double berries,
            double sporeBlossoms,
            double hangMultiplier
    ) {
        this.id = id;
        this.displayName = displayName;
        this.aliases = aliases;
        this.leafiness = leafiness;
        this.leaves = leaves;
        this.secondaryLeaves = secondaryLeaves;
        this.secondaryShare = secondaryShare;
        this.flowerShare = flowerShare;
        this.lichenShare = lichenShare;
        this.floorLichenShare = floorLichenShare;
        this.ceilingStrands = ceilingStrands;
        this.berries = berries;
        this.sporeBlossoms = sporeBlossoms;
        this.hangMultiplier = hangMultiplier;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Parte de las celdas de la hiedra que se vuelven mata de hojas (de 0 a 1, antes del ruido). */
    public double leafiness() {
        return leafiness;
    }

    public IvyMaterial leaves() {
        return leaves;
    }

    public IvyMaterial secondaryLeaves() {
        return secondaryLeaves;
    }

    public double secondaryShare() {
        return secondaryShare;
    }

    /** Parte de las hojas que florecen (azalea florida). */
    public double flowerShare() {
        return flowerShare;
    }

    /** Parte de las caras de muro y techo que son liquen brillante en vez de enredadera. */
    public double lichenShare() {
        return lichenShare;
    }

    public double floorLichenShare() {
        return floorLichenShare;
    }

    /** De los techos cuelgan tiras de bayas brillantes; si no, solo una enredadera pegada arriba. */
    public boolean ceilingStrands() {
        return ceilingStrands;
    }

    public double berries() {
        return berries;
    }

    public double sporeBlossoms() {
        return sporeBlossoms;
    }

    public double hangMultiplier() {
        return hangMultiplier;
    }

    public static IvyStyle byId(String input) {
        String normalized = normalize(input);
        return Arrays.stream(values())
                .filter(style -> style.id.equals(normalized)
                        || Arrays.stream(style.aliases).anyMatch(normalized::equals))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estilo de hiedra desconocido: " + input
                        + " (usa " + ids() + ")"));
    }

    public static String ids() {
        return Arrays.stream(values()).map(IvyStyle::id).collect(Collectors.joining(", "));
    }

    private static String normalize(String input) {
        String lower = input.strip().toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
