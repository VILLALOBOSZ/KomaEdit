package com.komainu.moxia.sculpt;

import com.sk89q.worldedit.world.block.BlockState;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public record BrushMask(
        boolean ignoreAir,
        boolean ignoreLiquids,
        Set<String> whitelist,
        Set<String> blacklist,
        Set<String> replaceOnly
) {
    public static final BrushMask DEFAULT = new BrushMask(false, true, Set.of(), Set.of(), Set.of());

    public BrushMask {
        whitelist = normalize(whitelist);
        blacklist = normalize(blacklist);
        replaceOnly = normalize(replaceOnly);
    }

    public boolean allows(BlockState state, boolean fillingAir) {
        var material = state.getBlockType().getMaterial();
        if (!fillingAir && ignoreAir && material.isAir()) {
            return false;
        }
        if (ignoreLiquids && material.isLiquid()) {
            return false;
        }
        String id = state.getBlockType().id();
        if (!whitelist.isEmpty() && !whitelist.contains(id)) {
            return false;
        }
        if (blacklist.contains(id)) {
            return false;
        }
        return replaceOnly.isEmpty() || replaceOnly.contains(id);
    }

    public BrushMask withIgnoreAir(boolean value) {
        return new BrushMask(value, ignoreLiquids, whitelist, blacklist, replaceOnly);
    }

    public BrushMask withIgnoreLiquids(boolean value) {
        return new BrushMask(ignoreAir, value, whitelist, blacklist, replaceOnly);
    }

    public BrushMask withWhitelist(Set<String> value) {
        return new BrushMask(ignoreAir, ignoreLiquids, value, blacklist, replaceOnly);
    }

    public BrushMask withBlacklist(Set<String> value) {
        return new BrushMask(ignoreAir, ignoreLiquids, whitelist, value, replaceOnly);
    }

    public BrushMask withReplaceOnly(Set<String> value) {
        return new BrushMask(ignoreAir, ignoreLiquids, whitelist, blacklist, value);
    }

    private static Set<String> normalize(Set<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String id = value.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            normalized.add(id.contains(":") ? id : "minecraft:" + id);
        }
        return Set.copyOf(normalized);
    }
}
