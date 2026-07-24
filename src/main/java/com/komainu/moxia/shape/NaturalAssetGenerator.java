package com.komainu.moxia.shape;

import com.komainu.moxia.path.PathSnapshot;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

public final class NaturalAssetGenerator {
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private NaturalAssetGenerator() {
    }

    public static NaturalAssetGeometry generate(PathSnapshot snapshot, ShapeParameters parameters) {
        if (!parameters.shape().isAsset()) {
            throw new IllegalArgumentException("La geometría solicitada no es un asset natural");
        }
        if (snapshot.points().isEmpty()) {
            throw new IllegalArgumentException("El asset necesita P1");
        }

        VoxelCanvas primary = new VoxelCanvas(ShapeGenerator.MAX_GENERATED_BLOCKS);
        VoxelCanvas accent = new VoxelCanvas(ShapeGenerator.MAX_GENERATED_BLOCKS);
        BlockVector3 anchor = snapshot.points().getFirst();
        Vec3 base = new Vec3(anchor.x() + 0.5, anchor.y(), anchor.z() + 0.5);
        Vec3 preferred = preferredDirection(snapshot, parameters);
        long seed = seed(anchor, parameters);

        switch (parameters.shape()) {
            case EPIC_TREE -> epicTree(primary, accent, base, preferred, parameters, seed);
            case GIANT_BOULDER -> giantBoulder(primary, accent, base, preferred, parameters, seed);
            case FALLEN_LOG -> fallenLog(primary, accent, base, preferred, parameters, seed);
            case ANCIENT_ROOTS -> ancientRoots(primary, accent, base, preferred, parameters, seed);
            default -> throw new IllegalArgumentException("Asset natural no compatible");
        }

        Set<BlockVector3> primaryBlocks = new HashSet<>(primary.blocks());
        Set<BlockVector3> accentBlocks = new HashSet<>(accent.blocks());
        accentBlocks.removeAll(primaryBlocks);
        primaryBlocks.addAll(accentBlocks);
        if (primaryBlocks.size() > ShapeGenerator.MAX_GENERATED_BLOCKS) {
            throw new GeometryLimitException(ShapeGenerator.MAX_GENERATED_BLOCKS);
        }
        return new NaturalAssetGeometry(primaryBlocks, accentBlocks);
    }

    private static void epicTree(
            VoxelCanvas primary,
            VoxelCanvas accent,
            Vec3 base,
            Vec3 preferred,
            ShapeParameters parameters,
            long seed
    ) {
        double height = Math.clamp(parameters.options().get("height", 54.0), 16.0, 160.0);
        double crownRadius = Math.clamp(parameters.radius(), 6.0, 48.0);
        int detail = parameters.spacing();
        double gnarled = parameters.thickness();
        double openAngle = Math.toRadians(parameters.options().get(
                "assetopenangle",
                Math.toDegrees(Math.atan2(preferred.z(), preferred.x()))
        ));
        double openStrength = Math.clamp(parameters.options().get("assetopenstrength", 0.0), 0.0, 1.0);
        Vec3 openDirection = horizontal(openAngle);
        Vec3 side = new Vec3(-openDirection.z(), 0.0, openDirection.x());
        double trunkRadius = Math.max(2.0, crownRadius * 0.17 + gnarled * 0.65);
        int trunkSamples = Math.max(24, (int) Math.ceil(height * 1.35));
        List<Vec3> trunkCenters = new ArrayList<>(trunkSamples + 1);

        for (int sample = 0; sample <= trunkSamples; sample++) {
            checkCancelled(sample);
            double progress = sample / (double) trunkSamples;
            double y = height * progress;
            double lean = height * (0.035 + openStrength * 0.105) * Math.pow(progress, 1.72);
            double sway = Math.sin(progress * Math.PI * (1.35 + detail * 0.06) + noise01(seed, sample, 7, 13))
                    * crownRadius * 0.075 * Math.pow(progress, 1.25);
            double crossSway = Math.sin(progress * Math.PI * 2.35 + noise01(seed, 19, sample, 3) * 4.0)
                    * crownRadius * 0.035 * progress;
            Vec3 center = base
                    .add(UP.multiply(y))
                    .add(openDirection.multiply(lean + crossSway))
                    .add(side.multiply(sway));
            trunkCenters.add(center);
            double taper = interpolate(trunkRadius, Math.max(1.15, trunkRadius * 0.22), Math.pow(progress, 0.72));
            double pulse = 1.0 + Math.sin(progress * Math.PI * (4.0 + detail * 0.35)) * 0.055 * gnarled;
            primary.brush(center, brushSize(taper * pulse));
        }

        int rootCount = 7 + detail;
        for (int root = 0; root < rootCount; root++) {
            double angle = Math.PI * 2.0 * root / rootCount + signedNoise(seed, root, 31, 5) * 0.28;
            double length = crownRadius * interpolate(0.72, 1.32, noise01(seed, root, 41, 2));
            double width = trunkRadius * interpolate(0.72, 1.18, noise01(seed, root, 43, 9));
            Vec3 direction = horizontal(angle);
            Vec3 perpendicular = new Vec3(-direction.z(), 0.0, direction.x());
            List<Vec3> curve = List.of(
                    base.add(UP.multiply(1.0)),
                    base.add(direction.multiply(length * 0.24)).add(UP.multiply(0.8 + width * 0.32)),
                    base.add(direction.multiply(length * 0.62))
                            .add(perpendicular.multiply(signedNoise(seed, root, 47, 6) * length * 0.10))
                            .add(UP.multiply(-0.2)),
                    base.add(direction.multiply(length))
                            .add(perpendicular.multiply(signedNoise(seed, root, 53, 8) * length * 0.14))
                            .add(UP.multiply(-1.2))
            );
            taperedCurve(primary, curve, width, Math.max(0.65, width * 0.16));
        }

        int branchCount = 7 + detail * 2;
        List<Vec3> tips = new ArrayList<>(branchCount);
        for (int branch = 0; branch < branchCount; branch++) {
            checkCancelled(branch);
            double verticalProgress = interpolate(0.30, 0.91, branch / (double) Math.max(1, branchCount - 1));
            verticalProgress += signedNoise(seed, branch, 61, 4) * 0.025;
            int trunkIndex = Math.clamp((int) Math.round(verticalProgress * trunkSamples), 0, trunkSamples);
            Vec3 start = trunkCenters.get(trunkIndex);
            double distributedAngle = parameters.rollDegrees() * Math.PI / 180.0
                    + branch * GOLDEN_ANGLE
                    + signedNoise(seed, branch, 67, 11) * 0.34;
            double attraction = openStrength * interpolate(0.48, 0.82, noise01(seed, branch, 71, 6));
            double angle = distributedAngle + wrappedAngle(openAngle - distributedAngle) * attraction;
            Vec3 direction = horizontal(angle);
            Vec3 lateral = new Vec3(-direction.z(), 0.0, direction.x());
            double length = crownRadius
                    * interpolate(0.68, 1.30, noise01(seed, branch, 73, 14))
                    * interpolate(1.06, 0.72, verticalProgress);
            double rise = height * interpolate(0.035, 0.125, noise01(seed, branch, 79, 3));
            double branchRadius = Math.max(0.9, trunkRadius * interpolate(0.48, 0.21, verticalProgress));
            Vec3 firstControl = start
                    .add(direction.multiply(length * 0.28))
                    .add(UP.multiply(rise * 0.15));
            Vec3 secondControl = start
                    .add(direction.multiply(length * 0.72))
                    .add(lateral.multiply(signedNoise(seed, branch, 83, 17) * length * 0.18))
                    .add(UP.multiply(rise * 0.54));
            Vec3 tip = start
                    .add(direction.multiply(length))
                    .add(lateral.multiply(signedNoise(seed, branch, 89, 5) * length * 0.12))
                    .add(UP.multiply(rise));
            taperedBezier(primary, start, firstControl, secondControl, tip, branchRadius, 0.62);
            tips.add(tip);

            for (int twig = -1; twig <= 1; twig += 2) {
                Vec3 twigDirection = rotateHorizontal(direction, twig * interpolate(0.30, 0.62,
                        noise01(seed, branch, twig, 97)));
                Vec3 twigTip = tip
                        .add(twigDirection.multiply(crownRadius * interpolate(0.20, 0.37,
                                noise01(seed, branch, twig, 101))))
                        .add(UP.multiply(crownRadius * interpolate(0.04, 0.16,
                                noise01(seed, branch, twig, 103))));
                taperedCurve(primary, List.of(tip, tip.lerp(twigTip, 0.58).add(UP.multiply(1.0)), twigTip),
                        Math.max(0.75, branchRadius * 0.55), 0.45);
                addEllipsoid(accent, twigTip, crownRadius * 0.31, crownRadius * 0.23,
                        crownRadius * 0.29, seed + branch * 131L + twig * 17L, 0.14);
            }

            addEllipsoid(accent, tip, crownRadius * 0.42, crownRadius * 0.29,
                    crownRadius * 0.38, seed + branch * 149L, 0.15);
            if (detail >= 6 && noise01(seed, branch, 107, 2) > 0.70) {
                int hanging = Math.max(2, (int) Math.round(crownRadius * interpolate(0.18, 0.38,
                        noise01(seed, branch, 109, 8))));
                accent.line(tip.add(direction.multiply(crownRadius * 0.12)), tip.add(UP.multiply(-hanging)), 1);
            }
        }

        Vec3 crownCenter = trunkCenters.get(Math.clamp((int) (trunkSamples * 0.84), 0, trunkSamples))
                .add(openDirection.multiply(crownRadius * 0.18))
                .add(UP.multiply(crownRadius * 0.18));
        foliageCluster(accent, crownCenter, crownRadius * 0.72, crownRadius * 0.52,
                crownRadius * 0.66, seed ^ 0x51C7A31DL, detail);
        Vec3 top = trunkCenters.getLast().add(UP.multiply(crownRadius * 0.18));
        addEllipsoid(accent, top, crownRadius * 0.47, crownRadius * 0.58,
                crownRadius * 0.44, seed ^ 0x2A184BC9L, 0.13);

        int crownSatellites = 2 + detail / 3;
        for (int satellite = 0; satellite < crownSatellites; satellite++) {
            double angle = openAngle + satellite * GOLDEN_ANGLE + signedNoise(seed, satellite, 113, 7) * 0.45;
            double distance = crownRadius * interpolate(0.28, 0.62, noise01(seed, satellite, 127, 3));
            Vec3 center = crownCenter
                    .add(horizontal(angle).multiply(distance))
                    .add(UP.multiply(crownRadius * signedNoise(seed, satellite, 131, 11) * 0.24));
            addEllipsoid(accent, center, crownRadius * 0.38, crownRadius * 0.31,
                    crownRadius * 0.36, seed + satellite * 173L, 0.15);
        }
    }

    private static void giantBoulder(
            VoxelCanvas primary,
            VoxelCanvas accent,
            Vec3 base,
            Vec3 preferred,
            ShapeParameters parameters,
            long seed
    ) {
        double radius = Math.clamp(parameters.radius(), 4.0, 52.0);
        double height = Math.clamp(parameters.options().get("height", 26.0), 5.0, 110.0);
        int detail = parameters.spacing();
        double relief = parameters.thickness();
        Vec3 side = new Vec3(-preferred.z(), 0.0, preferred.x());
        Vec3 center = base
                .add(UP.multiply(height * 0.44))
                .add(preferred.multiply(signedNoise(seed, 3, 7, 11) * radius * 0.12));
        double firstRadius = radius * interpolate(0.86, 1.24, noise01(seed, 5, 13, 2));
        double secondRadius = radius * interpolate(0.68, 1.02, noise01(seed, 17, 3, 19));
        addRockVolume(primary, accent, center, preferred, side, firstRadius, height * 0.56, secondRadius,
                seed, detail, relief, true);

        int satellites = Math.max(1, detail - 2);
        for (int satellite = 0; satellite < satellites; satellite++) {
            double angle = satellite * GOLDEN_ANGLE + noise01(seed, satellite, 23, 29) * 0.8;
            double distance = radius * interpolate(0.78, 1.38, noise01(seed, satellite, 31, 37));
            double scale = interpolate(0.16, 0.34, noise01(seed, satellite, 41, 43));
            Vec3 satelliteCenter = base
                    .add(horizontal(angle).multiply(distance))
                    .add(UP.multiply(height * scale * 0.36 - 0.35));
            Vec3 satelliteForward = horizontal(angle + signedNoise(seed, satellite, 47, 53) * 0.8);
            Vec3 satelliteSide = new Vec3(-satelliteForward.z(), 0.0, satelliteForward.x());
            addRockVolume(primary, accent, satelliteCenter, satelliteForward, satelliteSide,
                    radius * scale, height * scale, radius * scale * 0.82,
                    seed + satellite * 257L, detail, relief, false);
        }
    }

    private static void fallenLog(
            VoxelCanvas primary,
            VoxelCanvas accent,
            Vec3 base,
            Vec3 preferred,
            ShapeParameters parameters,
            long seed
    ) {
        double radius = Math.clamp(parameters.radius(), 2.0, 24.0);
        double length = Math.clamp(parameters.options().get("height", 44.0), 10.0, 180.0);
        int decay = parameters.spacing();
        double relief = parameters.thickness();
        Vec3 side = new Vec3(-preferred.z(), 0.0, preferred.x());
        int samples = Math.max(16, (int) Math.ceil(length * 1.35));
        double baseHeight = radius * 0.82;

        for (int sample = 0; sample <= samples; sample++) {
            checkCancelled(sample);
            double progress = sample / (double) samples;
            double along = length * (progress - 0.08);
            double curve = Math.sin(progress * Math.PI * 1.35 + signedNoise(seed, 2, 5, 7))
                    * radius * 0.22;
            double lift = baseHeight
                    + Math.sin(progress * Math.PI) * radius * 0.16
                    + Math.sin(progress * Math.PI * 3.0) * radius * 0.045 * relief;
            Vec3 center = base
                    .add(preferred.multiply(along))
                    .add(side.multiply(curve))
                    .add(UP.multiply(lift));
            double localRadius = radius * interpolate(1.15, 0.72, Math.pow(progress, 0.82));
            addLogSection(primary, accent, center, preferred, side, localRadius, progress, seed, sample, decay);
        }

        int rootCount = 6 + decay / 2;
        Vec3 rootCenter = base.add(preferred.multiply(-length * 0.07)).add(UP.multiply(radius * 0.35));
        for (int root = 0; root < rootCount; root++) {
            double angle = Math.PI * 2.0 * root / rootCount + signedNoise(seed, root, 59, 61) * 0.38;
            Vec3 direction = horizontal(angle);
            double rootLength = radius * interpolate(1.25, 2.35, noise01(seed, root, 67, 71));
            Vec3 end = rootCenter
                    .add(direction.multiply(rootLength))
                    .add(preferred.multiply(-radius * interpolate(0.3, 0.9, noise01(seed, root, 73, 79))))
                    .add(UP.multiply(-radius * 0.38));
            taperedCurve(primary, List.of(
                    rootCenter,
                    rootCenter.lerp(end, 0.45).add(UP.multiply(radius * 0.28)),
                    end
            ), radius * 0.48, 0.55);
        }

        int stubs = 2 + decay / 2;
        for (int stub = 0; stub < stubs; stub++) {
            double progress = interpolate(0.18, 0.78, (stub + 1.0) / (stubs + 1.0));
            Vec3 center = base
                    .add(preferred.multiply(length * progress))
                    .add(side.multiply(Math.sin(progress * Math.PI * 1.35) * radius * 0.22))
                    .add(UP.multiply(baseHeight));
            int sign = (stub & 1) == 0 ? 1 : -1;
            Vec3 end = center
                    .add(side.multiply(sign * radius * interpolate(1.2, 2.0, noise01(seed, stub, 83, 89))))
                    .add(UP.multiply(radius * interpolate(0.35, 0.92, noise01(seed, stub, 97, 101))));
            taperedCurve(primary, List.of(center, center.lerp(end, 0.55).add(UP.multiply(radius * 0.3)), end),
                    radius * 0.36, 0.5);
            if (decay >= 5) {
                foliageCluster(accent, end, radius * 0.42, radius * 0.22, radius * 0.40,
                        seed + stub * 313L, decay);
            }
        }

        int growths = 3 + decay;
        for (int growth = 0; growth < growths; growth++) {
            double progress = interpolate(0.08, 0.91, noise01(seed, growth, 103, 107));
            double sideOffset = signedNoise(seed, growth, 109, 113) * radius * 0.62;
            Vec3 growthCenter = base
                    .add(preferred.multiply(length * progress))
                    .add(side.multiply(sideOffset))
                    .add(UP.multiply(baseHeight + radius * interpolate(0.55, 0.92,
                            noise01(seed, growth, 127, 131))));
            addEllipsoid(accent, growthCenter, radius * 0.22, Math.max(0.8, radius * 0.10),
                    radius * 0.22, seed + growth * 347L, 0.08);
        }
    }

    private static void ancientRoots(
            VoxelCanvas primary,
            VoxelCanvas accent,
            Vec3 base,
            Vec3 preferred,
            ShapeParameters parameters,
            long seed
    ) {
        double radius = Math.clamp(parameters.radius(), 5.0, 44.0);
        double height = Math.clamp(parameters.options().get("height", 34.0), 10.0, 120.0);
        int detail = parameters.spacing();
        double relief = parameters.thickness();
        Vec3 side = new Vec3(-preferred.z(), 0.0, preferred.x());

        int stumpSamples = Math.max(12, (int) Math.ceil(height * 0.72));
        for (int sample = 0; sample <= stumpSamples; sample++) {
            double progress = sample / (double) stumpSamples;
            Vec3 center = base
                    .add(UP.multiply(height * 0.62 * progress))
                    .add(preferred.multiply(Math.sin(progress * Math.PI * 1.6) * radius * 0.12))
                    .add(side.multiply(Math.sin(progress * Math.PI * 2.2 + 0.8) * radius * 0.10));
            double localRadius = interpolate(radius * 0.38, radius * 0.10, Math.pow(progress, 0.72));
            primary.brush(center, brushSize(localRadius));
        }

        int rootCount = 9 + detail;
        for (int root = 0; root < rootCount; root++) {
            checkCancelled(root);
            double angle = root * GOLDEN_ANGLE + signedNoise(seed, root, 137, 139) * 0.36;
            Vec3 direction = horizontal(angle);
            Vec3 tangent = new Vec3(-direction.z(), 0.0, direction.x());
            double rootLength = radius * interpolate(1.05, 1.92, noise01(seed, root, 149, 151));
            double rise = height * interpolate(0.08, 0.38, noise01(seed, root, 157, 163));
            Vec3 start = base.add(UP.multiply(radius * 0.16));
            Vec3 first = start
                    .add(direction.multiply(rootLength * 0.24))
                    .add(UP.multiply(rise));
            Vec3 second = start
                    .add(direction.multiply(rootLength * 0.62))
                    .add(tangent.multiply(signedNoise(seed, root, 167, 173) * rootLength * 0.22))
                    .add(UP.multiply(rise * 0.38));
            Vec3 end = start
                    .add(direction.multiply(rootLength))
                    .add(tangent.multiply(signedNoise(seed, root, 179, 181) * rootLength * 0.16))
                    .add(UP.multiply(-0.8));
            double rootRadius = radius * interpolate(0.16, 0.29, noise01(seed, root, 191, 193))
                    + relief * 0.35;
            taperedBezier(primary, start, first, second, end, rootRadius, 0.55);

            if ((root & 1) == 0) {
                Vec3 mossCenter = first.lerp(second, 0.54).add(UP.multiply(rootRadius * 0.55));
                addEllipsoid(accent, mossCenter, rootRadius * 1.25, rootRadius * 0.32,
                        rootRadius * 0.90, seed + root * 389L, 0.16);
            }
            if (detail >= 6 && noise01(seed, root, 197, 199) > 0.62) {
                Vec3 hangingStart = first.lerp(second, 0.66);
                accent.line(hangingStart, hangingStart.add(UP.multiply(-height * interpolate(0.10, 0.25,
                        noise01(seed, root, 211, 223)))), 1);
            }
        }

        int arches = 2 + detail / 2;
        for (int arch = 0; arch < arches; arch++) {
            double angle = Math.atan2(preferred.z(), preferred.x()) + arch * Math.PI * 2.0 / arches
                    + signedNoise(seed, arch, 227, 229) * 0.42;
            Vec3 direction = horizontal(angle);
            Vec3 tangent = new Vec3(-direction.z(), 0.0, direction.x());
            double reach = radius * interpolate(0.68, 1.22, noise01(seed, arch, 233, 239));
            Vec3 start = base.add(direction.multiply(-reach * 0.32)).add(UP.multiply(radius * 0.18));
            Vec3 end = base.add(direction.multiply(reach)).add(UP.multiply(-0.4));
            Vec3 first = start.add(direction.multiply(reach * 0.34))
                    .add(tangent.multiply(signedNoise(seed, arch, 241, 251) * radius * 0.24))
                    .add(UP.multiply(height * interpolate(0.42, 0.74, noise01(seed, arch, 257, 263))));
            Vec3 second = end.add(direction.multiply(-reach * 0.28))
                    .add(tangent.multiply(signedNoise(seed, arch, 269, 271) * radius * 0.24))
                    .add(UP.multiply(height * interpolate(0.32, 0.62, noise01(seed, arch, 277, 281))));
            taperedBezier(primary, start, first, second, end,
                    radius * interpolate(0.13, 0.22, noise01(seed, arch, 283, 293)), 0.65);
        }

        int crowns = 3 + detail / 2;
        for (int crown = 0; crown < crowns; crown++) {
            double angle = crown * GOLDEN_ANGLE + noise01(seed, crown, 307, 311);
            Vec3 center = base
                    .add(horizontal(angle).multiply(radius * interpolate(0.18, 0.52,
                            noise01(seed, crown, 313, 317))))
                    .add(UP.multiply(height * interpolate(0.40, 0.67,
                            noise01(seed, crown, 331, 337))));
            foliageCluster(accent, center, radius * 0.31, radius * 0.22, radius * 0.29,
                    seed + crown * 419L, detail);
        }
    }

    private static void addRockVolume(
            VoxelCanvas primary,
            VoxelCanvas accent,
            Vec3 center,
            Vec3 firstAxis,
            Vec3 secondAxis,
            double firstRadius,
            double verticalRadius,
            double secondRadius,
            long seed,
            int detail,
            double relief,
            boolean fissures
    ) {
        int firstLimit = (int) Math.ceil(firstRadius + 1.0);
        int verticalLimit = (int) Math.ceil(verticalRadius + 1.0);
        int secondLimit = (int) Math.ceil(secondRadius + 1.0);
        int processed = 0;
        for (int first = -firstLimit; first <= firstLimit; first++) {
            for (int vertical = -verticalLimit; vertical <= verticalLimit; vertical++) {
                for (int second = -secondLimit; second <= secondLimit; second++) {
                    checkCancelled(processed++);
                    double normalizedFirst = first / Math.max(1.0, firstRadius);
                    double normalizedVertical = vertical / Math.max(1.0, verticalRadius);
                    double normalizedSecond = second / Math.max(1.0, secondRadius);
                    double form = Math.pow(Math.abs(normalizedFirst), 2.35)
                            + Math.pow(Math.abs(normalizedVertical), 2.15)
                            + Math.pow(Math.abs(normalizedSecond), 2.35);
                    double broadNoise = signedNoise(seed, first / 3, vertical / 3, second / 3) * 0.16;
                    double detailNoise = signedNoise(seed ^ 0x6A09E667F3BCC909L, first, vertical, second)
                            * (0.035 + detail * 0.008);
                    if (form > 1.0 + broadNoise + detailNoise) {
                        continue;
                    }
                    double surfaceDepth = 1.0 - form;
                    double fracture = Math.abs(Math.sin(
                            first * 0.19 + second * 0.13 + vertical * (0.11 + detail * 0.006)
                                    + signedNoise(seed, first / 5, vertical / 4, second / 5) * 1.7
                    ));
                    if (fissures && surfaceDepth < 0.18 && fracture < 0.035 + detail * 0.004) {
                        continue;
                    }
                    Vec3 point = center
                            .add(firstAxis.multiply(first))
                            .add(UP.multiply(vertical))
                            .add(secondAxis.multiply(second));
                    boolean upperMoss = normalizedVertical > 0.18
                            && surfaceDepth < 0.28
                            && noise01(seed ^ 0xBB67AE8584CAA73BL, first, vertical, second)
                            > 0.48 - detail * 0.025;
                    double band = Math.abs(fract((vertical + signedNoise(seed, first, 17, second) * 2.0)
                            / Math.max(2.5, 7.5 - relief)) - 0.5);
                    boolean stratum = surfaceDepth < 0.16 && band < 0.055 + relief * 0.012;
                    (upperMoss || stratum ? accent : primary).add(point);
                }
            }
        }
    }

    private static void addLogSection(
            VoxelCanvas primary,
            VoxelCanvas accent,
            Vec3 center,
            Vec3 forward,
            Vec3 side,
            double radius,
            double progress,
            long seed,
            int sample,
            int decay
    ) {
        int limit = (int) Math.ceil(radius + 1.0);
        boolean brokenEnd = progress > 0.78;
        for (int lateral = -limit; lateral <= limit; lateral++) {
            for (int vertical = -limit; vertical <= limit; vertical++) {
                double normalized = (lateral * lateral + vertical * vertical) / Math.max(1.0, radius * radius);
                double edgeNoise = signedNoise(seed, sample, lateral, vertical) * (0.08 + decay * 0.008);
                if (normalized > 1.0 + edgeNoise) {
                    continue;
                }
                double hollowRadius = radius * Math.clamp((progress - 0.78) / 0.22, 0.0, 1.0) * 0.48;
                if (brokenEnd && lateral * lateral + vertical * vertical < hollowRadius * hollowRadius
                        && noise01(seed, sample, lateral * 3, vertical * 5) > 0.08) {
                    continue;
                }
                Vec3 point = center.add(side.multiply(lateral)).add(UP.multiply(vertical));
                boolean surface = normalized > 0.70;
                boolean moss = surface
                        && vertical > radius * 0.18
                        && noise01(seed ^ 0x3C6EF372FE94F82BL, sample / 2, lateral, vertical)
                        > 0.66 - decay * 0.035;
                (moss ? accent : primary).add(point);
            }
        }
    }

    private static void foliageCluster(
            VoxelCanvas canvas,
            Vec3 center,
            double firstRadius,
            double verticalRadius,
            double secondRadius,
            long seed,
            int detail
    ) {
        addEllipsoid(canvas, center, firstRadius, verticalRadius, secondRadius, seed,
                0.10 + detail * 0.008);
        int lobes = 1 + detail / 3;
        for (int lobe = 0; lobe < lobes; lobe++) {
            double angle = lobe * GOLDEN_ANGLE + noise01(seed, lobe, 347, 349);
            double distance = firstRadius * interpolate(0.24, 0.52, noise01(seed, lobe, 353, 359));
            Vec3 lobeCenter = center
                    .add(horizontal(angle).multiply(distance))
                    .add(UP.multiply(verticalRadius * signedNoise(seed, lobe, 367, 373) * 0.38));
            double scale = interpolate(0.42, 0.68, noise01(seed, lobe, 379, 383));
            addEllipsoid(canvas, lobeCenter, firstRadius * scale, verticalRadius * scale,
                    secondRadius * scale, seed + lobe * 431L, 0.13);
        }
    }

    private static void addEllipsoid(
            VoxelCanvas canvas,
            Vec3 center,
            double xRadius,
            double yRadius,
            double zRadius,
            long seed,
            double erosion
    ) {
        int xLimit = (int) Math.ceil(xRadius + 1.0);
        int yLimit = (int) Math.ceil(yRadius + 1.0);
        int zLimit = (int) Math.ceil(zRadius + 1.0);
        int processed = 0;
        for (int x = -xLimit; x <= xLimit; x++) {
            for (int y = -yLimit; y <= yLimit; y++) {
                for (int z = -zLimit; z <= zLimit; z++) {
                    checkCancelled(processed++);
                    double normalized = square(x / Math.max(0.75, xRadius))
                            + square(y / Math.max(0.75, yRadius))
                            + square(z / Math.max(0.75, zRadius));
                    double edge = signedNoise(seed, x / 2, y / 2, z / 2) * erosion
                            + signedNoise(seed ^ 0xA54FF53A5F1D36F1L, x, y, z) * erosion * 0.35;
                    if (normalized <= 1.0 + edge) {
                        canvas.add(center.add(new Vec3(x, y, z)));
                    }
                }
            }
        }
    }

    private static void taperedBezier(
            VoxelCanvas canvas,
            Vec3 start,
            Vec3 firstControl,
            Vec3 secondControl,
            Vec3 end,
            double startRadius,
            double endRadius
    ) {
        double estimatedLength = start.distance(firstControl)
                + firstControl.distance(secondControl)
                + secondControl.distance(end);
        int samples = Math.max(6, (int) Math.ceil(estimatedLength * 1.25));
        for (int sample = 0; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            double inverse = 1.0 - progress;
            Vec3 point = start.multiply(inverse * inverse * inverse)
                    .add(firstControl.multiply(3.0 * inverse * inverse * progress))
                    .add(secondControl.multiply(3.0 * inverse * progress * progress))
                    .add(end.multiply(progress * progress * progress));
            double radius = interpolate(startRadius, endRadius, Math.pow(progress, 0.82));
            canvas.brush(point, brushSize(radius));
        }
    }

    private static void taperedCurve(VoxelCanvas canvas, List<Vec3> controls, double startRadius, double endRadius) {
        double total = 0.0;
        for (int index = 1; index < controls.size(); index++) {
            total += controls.get(index - 1).distance(controls.get(index));
        }
        int globalSample = 0;
        for (int index = 1; index < controls.size(); index++) {
            Vec3 start = controls.get(index - 1);
            Vec3 end = controls.get(index);
            double segment = start.distance(end);
            int samples = Math.max(2, (int) Math.ceil(segment * 1.35));
            for (int sample = 0; sample <= samples; sample++) {
                double segmentProgress = sample / (double) samples;
                double progress = total < 1.0E-6
                        ? 0.0
                        : (globalSample + segmentProgress * samples)
                        / Math.max(1.0, total * 1.35);
                canvas.brush(start.lerp(end, segmentProgress),
                        brushSize(interpolate(startRadius, endRadius, Math.clamp(progress, 0.0, 1.0))));
            }
            globalSample += samples;
        }
    }

    private static Vec3 preferredDirection(PathSnapshot snapshot, ShapeParameters parameters) {
        if (snapshot.points().size() >= 2) {
            BlockVector3 first = snapshot.points().get(0);
            BlockVector3 second = snapshot.points().get(1);
            Vec3 direction = new Vec3(second.x() - first.x(), 0.0, second.z() - first.z()).normalized();
            if (direction.length() > 0.1) {
                return direction;
            }
        }
        return horizontal(Math.toRadians(parameters.rollDegrees()));
    }

    private static Vec3 horizontal(double angle) {
        return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
    }

    private static Vec3 rotateHorizontal(Vec3 direction, double angle) {
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        return new Vec3(
                direction.x() * cosine - direction.z() * sine,
                0.0,
                direction.x() * sine + direction.z() * cosine
        ).normalized();
    }

    private static int brushSize(double radius) {
        return Math.max(1, (int) Math.ceil(radius + 1.0));
    }

    private static double wrappedAngle(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    private static double interpolate(double first, double second, double progress) {
        return first + (second - first) * progress;
    }

    private static double square(double value) {
        return value * value;
    }

    private static double fract(double value) {
        return value - Math.floor(value);
    }

    private static long seed(BlockVector3 anchor, ShapeParameters parameters) {
        long value = 0x9E3779B97F4A7C15L;
        value ^= mix(anchor.x() * 0x632BE59BD9B4E019L);
        value ^= mix(anchor.y() * 0xC6BC279692B5CC83L);
        value ^= mix(anchor.z() * 0xD1B54A32D192ED03L);
        value ^= mix(parameters.shape().ordinal() * 0x94D049BB133111EBL);
        value ^= mix(parameters.radius() * 31L + parameters.spacing() * 131L
                + parameters.thickness() * 521L + parameters.rollDegrees() * 17L);
        return mix(value);
    }

    private static double noise01(long seed, int x, int y, int z) {
        long value = seed;
        value ^= mix(x * 0x9E3779B97F4A7C15L);
        value ^= mix(y * 0xC2B2AE3D27D4EB4FL);
        value ^= mix(z * 0x165667B19E3779F9L);
        long mixed = mix(value);
        return (mixed >>> 11) * 0x1.0p-53;
    }

    private static double signedNoise(long seed, int x, int y, int z) {
        return noise01(seed, x, y, z) * 2.0 - 1.0;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static void checkCancelled(int processed) {
        if ((processed & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }
}
