package com.komainu.moxia.nature;

import com.komainu.moxia.shape.Vec3;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.BooleanSupplier;

/**
 * Hace crecer el esqueleto de un árbol según la arquitectura de su especie y lo convierte en bloques con
 * {@link TreeBuilder}. Es Java puro: lee el terreno por {@link NatureTerrain} (para apoyar el tronco y agarrar las
 * raíces) y todo lo aleatorio sale de la semilla, así que la misma semilla da siempre el mismo árbol.
 */
public final class TreeGrower {
    private static final double TAU = Math.PI * 2.0;
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private TreeGrower() {
    }

    /**
     * @param x,y,z celda de la base: {@code y} es la primera celda libre sobre el suelo
     */
    public static NatureVoxels grow(TreeSettings settings, NatureTerrain terrain, Bounds bounds, int x, int y, int z,
                                    long seed, int limit, BooleanSupplier cancelled) {
        TreeSkeleton skeleton = skeleton(settings, terrain, x, y, z, seed, cancelled);
        return TreeBuilder.build(skeleton, settings, terrain, bounds, y, seed, limit, cancelled);
    }

    /** Radio horizontal que puede llegar a ocupar el árbol desde su base, para saber cuánto capturar. */
    public static int reach(TreeSettings settings) {
        TreeSpecies species = settings.species();
        double height = settings.height();
        double crown = height * species.reach() * Math.max(1.0, settings.width());
        double lean = height * Math.sin(Math.toRadians(Math.min(75.0, settings.lean() + 14.0)));
        double roots = trunkRadius(settings) * species.form().rootLength * 1.4 + 3.0;
        return (int) Math.ceil(Math.max(crown + lean, roots) + 4.0);
    }

    static double trunkRadius(TreeSettings settings) {
        TreeSpecies.Form form = settings.species().form();
        return Math.clamp(Math.max(form.trunkMinimum, form.trunkRadius * settings.height()) * settings.thickness(),
                0.5, 12.0);
    }

    static TreeSkeleton skeleton(TreeSettings settings, NatureTerrain terrain, int x, int y, int z, long seed,
                                 BooleanSupplier cancelled) {
        Context context = new Context(settings, terrain, x, y, z, seed);
        switch (settings.species().form().architecture()) {
            case BROADLEAF -> broadleaf(context, cancelled);
            case CONIFER -> conifer(context);
            case ACACIA -> acacia(context);
            case PALM -> palm(context);
            case BAOBAB -> baobab(context);
        }
        roots(context);
        return context.skeleton;
    }

    // ── Frondosas ──────────────────────────────────────────────────────────────────────────────────────────

    private static void broadleaf(Context context, BooleanSupplier cancelled) {
        TreeSpecies.Form form = context.form;
        double height = context.height;
        double step = Math.clamp(height / 20.0, 0.9, 1.8);
        List<Vec3> trunk = context.trunkPath(form.leader * height, step);
        SpaceColonization.Graph graph = new SpaceColonization.Graph();
        double sprout = form.crownBase * height * 0.92;
        int previous = -1;
        for (Vec3 point : trunk) {
            previous = graph.add(point.x(), point.y(), point.z(), previous, point.y() - context.base.y() >= sprout);
        }
        int trunkNodes = graph.count;

        Vec3 center = along(trunk, form.crownCenter * height, step);
        double width = form.crownWidth * height * context.settings.width();
        double depth = width * (0.85 + context.random.nextDouble() * 0.3);
        double tall = form.crownHeight * height;
        double[][] points = attractors(context, center, width, tall, depth, form.envelope,
                (int) Math.round(form.lobes * Math.clamp(context.settings.branches(), 0.6, 1.6)));
        context.skeleton.crownRadius = Math.max(width, depth);
        context.skeleton.axisX = center.x();
        context.skeleton.axisZ = center.z();
        SpaceColonization.Settings growth = new SpaceColonization.Settings(
                step,
                form.kill * Math.pow(height / 18.0, 0.35),
                form.tropism * 0.6,
                form.twist,
                0.14 * context.wander,
                center.x(),
                center.z(),
                16_000
        );
        SpaceColonization.grow(graph, points[0], points[1], points[2], growth, context.random, cancelled);

        double[] radius = pipeRadii(graph, context.trunkRadius);
        for (int node = 0; node < trunkNodes; node++) {
            double along = node * step;
            radius[node] = Math.max(radius[node], 0.5) * (1.0 - 0.12 * along / Math.max(1.0, form.leader * height))
                    * flare(context, along);
        }
        boolean foliage = context.settings.leafMaterial() != null && form.clump > 0.0;
        chains(context, graph, radius, step, foliage, form.broken);
        if (foliage) {
            double size = form.clump * Math.pow(height / 18.0, 0.3) * (0.8 + 0.2 * context.settings.density());
            for (int node = trunkNodes; node < graph.count; node++) {
                if (graph.leaf(node)) {
                    context.clump(new Vec3(graph.x[node], graph.y[node] + 0.6, graph.z[node]),
                            size * (0.82 + context.random.nextDouble() * 0.36), form.flatten);
                }
            }
            sideClumps(context, trunk, step);
        }
    }

    /** Puntos de atracción de la copa, agrupados en bultos para que la silueta no sea una bola. */
    private static double[][] attractors(Context context, Vec3 center, double width, double tall, double depth,
                                         TreeSpecies.Envelope envelope, int lobes) {
        double fraction = switch (envelope) {
            case ELLIPSOID -> 1.0;
            case DOME -> 0.82;
            case UMBRELLA -> 0.7;
        };
        double volume = 4.0 / 3.0 * Math.PI * width * tall * depth * fraction;
        int count = (int) Math.clamp(Math.round(volume * context.form.attractors * context.settings.branches()),
                12L, 5_000L);
        SplittableRandom random = context.random;
        List<double[]> centers = new ArrayList<>();
        for (int lobe = 0; lobe < lobes; lobe++) {
            for (int attempt = 0; attempt < 60; attempt++) {
                double[] point = ball(random);
                double length = Math.sqrt(point[0] * point[0] + point[1] * point[1] + point[2] * point[2]);
                // Bultos hacia el borde y separados entre sí: así quedan huecos que dejan ver las ramas.
                if (length > 0.42 && length < 0.85 && inside(envelope, point) && apart(centers, point, 0.58)) {
                    centers.add(point);
                    break;
                }
            }
        }
        double[] xs = new double[count];
        double[] ys = new double[count];
        double[] zs = new double[count];
        int made = 0;
        for (int index = 0; index < count; index++) {
            for (int attempt = 0; attempt < 40; attempt++) {
                double[] point = ball(random);
                if (!centers.isEmpty() && random.nextDouble() < context.form.lobeShare) {
                    double[] lobe = centers.get(random.nextInt(centers.size()));
                    point[0] = lobe[0] + point[0] * 0.32;
                    point[1] = lobe[1] + point[1] * 0.32;
                    point[2] = lobe[2] + point[2] * 0.32;
                }
                if (inside(envelope, point)) {
                    xs[made] = center.x() + point[0] * width;
                    ys[made] = center.y() + point[1] * tall;
                    zs[made] = center.z() + point[2] * depth;
                    made++;
                    break;
                }
            }
        }
        return new double[][]{java.util.Arrays.copyOf(xs, made), java.util.Arrays.copyOf(ys, made),
                java.util.Arrays.copyOf(zs, made)};
    }

    private static boolean apart(List<double[]> centers, double[] point, double distance) {
        for (double[] center : centers) {
            double dx = center[0] - point[0];
            double dy = center[1] - point[1];
            double dz = center[2] - point[2];
            if (dx * dx + dy * dy + dz * dz < distance * distance) {
                return false;
            }
        }
        return true;
    }

    private static boolean inside(TreeSpecies.Envelope envelope, double[] point) {
        double squared = point[0] * point[0] + point[1] * point[1] + point[2] * point[2];
        if (squared > 1.0) {
            return false;
        }
        return switch (envelope) {
            case ELLIPSOID -> true;
            case DOME -> point[1] >= -0.35;
            case UMBRELLA -> point[1] >= -0.4 && point[1] <= 0.8;
        };
    }

    private static double[] ball(SplittableRandom random) {
        while (true) {
            double x = random.nextDouble() * 2.0 - 1.0;
            double y = random.nextDouble() * 2.0 - 1.0;
            double z = random.nextDouble() * 2.0 - 1.0;
            if (x * x + y * y + z * z <= 1.0) {
                return new double[]{x, y, z};
            }
        }
    }

    /**
     * Modelo de tuberías (Shinozaki): el grosor de una rama sale de las que sostiene, r^e = Σ r_hija^e. Se escala
     * para que la base mida el radio del tronco.
     */
    static double[] pipeRadii(SpaceColonization.Graph graph, double trunkRadius) {
        double exponent = 2.1;
        double[] radius = new double[graph.count];
        double[] sum = new double[graph.count];
        for (int node = graph.count - 1; node >= 0; node--) {
            double value = graph.leaf(node) ? 1.0 : Math.pow(sum[node], 1.0 / exponent);
            radius[node] = value;
            int parent = graph.parent[node];
            if (parent >= 0) {
                sum[parent] += Math.pow(value, exponent);
            }
        }
        double scale = trunkRadius / radius[0];
        for (int node = 0; node < graph.count; node++) {
            radius[node] *= scale;
        }
        return radius;
    }

    /**
     * Parte el grafo en tubos: cada uno sigue a su hija más gruesa y las demás empiezan tubo nuevo en el nodo donde
     * salen. Con {@code broken} algunas ramas se quedan partidas (el árbol muerto).
     */
    private static void chains(Context context, SpaceColonization.Graph graph, double[] radius, double step,
                               boolean foliage, double broken) {
        ArrayDeque<int[]> starts = new ArrayDeque<>();
        starts.add(new int[]{-1, 0});
        boolean trunk = true;
        while (!starts.isEmpty()) {
            int[] start = starts.poll();
            IntArrayList chain = new IntArrayList();
            if (start[0] >= 0) {
                chain.add(start[0]);
            }
            int current = start[1];
            chain.add(current);
            int cut = Integer.MAX_VALUE;
            if (!trunk && broken > 0.0 && context.random.nextDouble() < broken) {
                cut = 2 + context.random.nextInt(4);
            }
            while (chain.size() < cut) {
                int heaviest = -1;
                for (int child = graph.firstChild[current]; child >= 0; child = graph.nextSibling[child]) {
                    if (heaviest < 0 || radius[child] > radius[heaviest]) {
                        heaviest = child;
                    }
                }
                if (heaviest < 0) {
                    break;
                }
                for (int child = graph.firstChild[current]; child >= 0; child = graph.nextSibling[child]) {
                    if (child != heaviest) {
                        starts.add(new int[]{current, child});
                    }
                }
                chain.add(heaviest);
                current = heaviest;
            }
            if (!trunk && foliage && chain.size() <= 2 && radius[chain.getInt(chain.size() - 1)] <= 0.6) {
                // Ramita de un paso: la tapan las hojas y solo ensuciaría el borde de la copa.
                continue;
            }
            List<Vec3> points = new ArrayList<>();
            List<Double> radii = new ArrayList<>();
            if (trunk) {
                points.add(context.base.subtract(new Vec3(0.0, context.plantDepth, 0.0)));
                radii.add(radius[0] * 1.02);
            }
            int stride = trunk ? 1 : Math.max(1, (int) Math.round(2.0 / step));
            for (int index = 0; index < chain.size(); index++) {
                if (index % stride != 0 && index != chain.size() - 1) {
                    continue;
                }
                int node = chain.getInt(index);
                points.add(new Vec3(graph.x[node], graph.y[node], graph.z[node]));
                // La primera muestra de una rama está dentro de su madre: lleva el grosor de la rama, no el de ella.
                int sized = !trunk && index == 0 && chain.size() > 1 ? chain.getInt(1) : node;
                radii.add(radius[sized]);
            }
            context.skeleton.limb(points, unbox(radii));
            trunk = false;
        }
    }

    /** Ramitas con hojas a lo largo del tronco por debajo de la copa (la jungla). */
    private static void sideClumps(Context context, List<Vec3> trunk, double step) {
        TreeSpecies.Form form = context.form;
        for (int index = 0; index < form.sideClumps; index++) {
            double along = context.height * (0.28 + context.random.nextDouble() * 0.36);
            Vec3 start = along(trunk, along, step);
            double angle = context.random.nextDouble() * TAU;
            double length = context.trunkRadius + 1.4 + context.random.nextDouble() * 1.6;
            Vec3 end = start.add(direction(angle, 0.45).multiply(length));
            context.skeleton.limb(List.of(start, end), new double[]{0.7, 0.5});
            context.clump(end.add(new Vec3(0.0, 0.4, 0.0)), 1.6 + context.random.nextDouble() * 0.6, 0.75);
        }
    }

    // ── Conífera ───────────────────────────────────────────────────────────────────────────────────────────

    private static void conifer(Context context) {
        TreeSpecies.Form form = context.form;
        double height = context.height;
        List<Vec3> trunk = context.trunkPath(height, 1.0);
        double radius = context.trunkRadius;
        List<Double> trunkRadii = new ArrayList<>();
        List<Vec3> trunkPoints = new ArrayList<>();
        trunkPoints.add(context.base.subtract(new Vec3(0.0, context.plantDepth, 0.0)));
        trunkRadii.add(radius * (1.0 + form.flare));
        for (int index = 0; index < trunk.size(); index++) {
            double t = index / (double) (trunk.size() - 1);
            trunkPoints.add(trunk.get(index));
            trunkRadii.add(Math.max(0.5, radius * Math.pow(1.0 - t, 0.85) * flare(context, index)));
        }
        context.skeleton.limb(trunkPoints, unbox(trunkRadii));
        boolean foliage = context.settings.leafMaterial() != null;
        double crown = form.crownWidth * height * context.settings.width();
        context.skeleton.crownRadius = crown;
        context.skeleton.axisX = context.base.x();
        context.skeleton.axisZ = context.base.z();
        double bottom = form.crownBase * height;
        double top = height - 1.6;
        double y = bottom;
        int whorl = 0;
        double scale = Math.pow(height / 24.0, 0.3);
        double leafy = 0.8 + 0.2 * context.settings.density();
        while (y < top) {
            double t = (y - bottom) / Math.max(1.0, top - bottom);
            Vec3 start = along(trunk, y, 1.0);
            double length = crown * Math.pow(1.0 - t, 0.9) * (0.85 + context.random.nextDouble() * 0.3) + 0.8;
            int count = (int) Math.clamp(Math.round((5 + context.random.nextInt(4)) * context.settings.branches()),
                    0L, 14L);
            double twist = whorl * 2.399 + context.random.nextDouble() * 0.4;
            for (int branch = 0; branch < count; branch++) {
                double angle = twist + branch * TAU / count + (context.random.nextDouble() - 0.5) * 0.45;
                double reach = length * (0.8 + context.random.nextDouble() * 0.35);
                double droop = Math.toRadians(-(4.0 + 14.0 * (1.0 - t)) + context.random.nextDouble() * 6.0);
                Vec3 out = direction(angle, droop);
                Vec3 middle = start.add(out.multiply(reach * 0.5));
                Vec3 end = start.add(out.multiply(reach)).add(new Vec3(0.0, reach * 0.06, 0.0));
                double thick = reach > 4.0 ? Math.min(radius * 0.5, 0.75) : 0.5;
                // La madera se queda dentro de las hojas: acaba antes que ellas.
                Vec3 woodEnd = middle.lerp(end, foliage ? 0.7 : 1.0);
                context.skeleton.limb(List.of(start, middle, woodEnd), new double[]{Math.max(0.5, thick), 0.5, 0.5});
                if (foliage) {
                    for (double s = 0.1; s <= 1.0001; s += 0.7 / Math.max(1.0, reach)) {
                        Vec3 point = s <= 0.5 ? start.lerp(middle, s / 0.5) : middle.lerp(end, (s - 0.5) / 0.5);
                        context.clump(point.subtract(new Vec3(0.0, 0.1, 0.0)),
                                (1.4 + 1.0 * (1.0 - s)) * form.clump / 1.6 * leafy, 0.72);
                    }
                }
            }
            if (foliage) {
                // Cada piso es un cono: ancho abajo, pegado al tronco arriba.
                context.clump(start.add(new Vec3(0.0, 0.6, 0.0)), (0.62 * length + 0.8) * leafy, 0.62);
                context.clump(start.add(new Vec3(0.0, 1.5, 0.0)), (0.38 * length + 0.7) * leafy, 0.7);
            }
            y += (1.3 + context.random.nextDouble() * 0.7) * scale;
            whorl++;
        }
        if (foliage) {
            for (double s = Math.max(bottom, height - 4.0); s <= height + 0.6; s += 0.5) {
                context.clump(along(trunk, s, 1.0), Math.max(0.6, 1.5 - 0.25 * (s - (height - 4.0))), 1.4);
            }
        }
    }

    // ── Acacia ─────────────────────────────────────────────────────────────────────────────────────────────

    private static void acacia(Context context) {
        TreeSpecies.Form form = context.form;
        double height = context.height;
        double fork = height * (0.36 + context.random.nextDouble() * 0.1);
        List<Vec3> trunk = context.trunkPath(fork, 1.0);
        double radius = context.trunkRadius;
        context.skeleton.limb(withBase(context, trunk), radii(context, trunk, radius,
                radius * 0.85));
        Vec3 top = trunk.getLast();
        context.skeleton.axisX = top.x();
        context.skeleton.axisZ = top.z();
        context.skeleton.crownRadius = form.crownWidth * height;
        int leaders = 2 + (context.random.nextDouble() < 0.55 * context.settings.branches() ? 1 : 0)
                + (context.settings.branches() > 1.6 ? 1 : 0);
        double base = context.random.nextDouble() * TAU;
        boolean foliage = context.settings.leafMaterial() != null;
        double pad = form.clump * 1.15 * Math.pow(height / 14.0, 0.6) * context.settings.width();
        for (int leader = 0; leader < leaders; leader++) {
            double angle = base + leader * TAU / leaders + (context.random.nextDouble() - 0.5) * 0.9;
            double tilt = Math.toRadians(35.0 + context.random.nextDouble() * 20.0);
            double length = (height - fork) * (1.05 + context.random.nextDouble() * 0.35);
            Vec3 out = direction(angle, Math.PI / 2.0 - tilt);
            Vec3 middle = top.add(out.multiply(length * 0.55));
            Vec3 end = middle.add(direction(angle, Math.PI / 2.0 - tilt * 0.55).multiply(length * 0.5));
            context.skeleton.limb(List.of(top, middle, end), new double[]{radius * 0.75, radius * 0.55, 0.6});
            if (foliage) {
                padAt(context, end, pad, form.flatten);
            }
            if (context.random.nextDouble() < 0.5) {
                double side = angle + (context.random.nextBoolean() ? 0.7 : -0.7);
                Vec3 branchEnd = middle.add(direction(side, Math.toRadians(30.0)).multiply(length * 0.55));
                context.skeleton.limb(List.of(middle, branchEnd), new double[]{radius * 0.45, 0.5});
                if (foliage) {
                    padAt(context, branchEnd, pad * 0.72, form.flatten);
                }
            }
        }
    }

    /**
     * Copa plana de acacia: ramitas en abanico bajo la capa (así ninguna hoja queda lejos de la madera), una capa
     * principal y otra más pequeña desplazada encima.
     */
    private static void padAt(Context context, Vec3 end, double size, double flatten) {
        double spread = size * (0.85 + context.random.nextDouble() * 0.3);
        int twigs = 4 + context.random.nextInt(3);
        double turn = context.random.nextDouble() * TAU;
        for (int twig = 0; twig < twigs; twig++) {
            double angle = turn + twig * TAU / twigs + (context.random.nextDouble() - 0.5) * 0.6;
            Vec3 tip = end.add(direction(angle, Math.toRadians(12.0)).multiply(spread * 0.62));
            context.skeleton.limb(List.of(end, tip), new double[]{0.5, 0.5});
            context.clump(tip.add(new Vec3(0.0, 0.8, 0.0)), spread * 0.5, flatten * 1.6);
        }
        context.clump(end.add(new Vec3(0.0, 0.9, 0.0)), spread, flatten);
        double angle = context.random.nextDouble() * TAU;
        context.clump(end.add(new Vec3(Math.cos(angle) * size * 0.4, 1.6, Math.sin(angle) * size * 0.4)),
                size * 0.6, flatten * 1.2);
    }

    // ── Palmera ────────────────────────────────────────────────────────────────────────────────────────────

    private static void palm(Context context) {
        TreeSpecies.Form form = context.form;
        double height = context.height;
        List<Vec3> trunk = context.trunkPath(height, 1.0);
        double radius = context.trunkRadius;
        context.skeleton.limb(withBase(context, trunk),
                radii(context, trunk, radius, radius * 0.8));
        Vec3 top = trunk.getLast();
        Vec3 heading = top.subtract(trunk.get(trunk.size() - 2)).normalized();
        Vec3 crown = top.add(heading.multiply(1.0));
        context.skeleton.limb(List.of(top, crown), new double[]{radius * 1.1, radius * 1.15});
        context.skeleton.axisX = crown.x();
        context.skeleton.axisZ = crown.z();
        if (context.settings.leafMaterial() == null) {
            return;
        }
        int fronds = (int) Math.clamp(Math.round((6 + context.random.nextInt(3))
                * Math.max(0.5, context.settings.branches())), 4L, 14L);
        double length = Math.clamp(height * 0.52, 4.5, 11.0) * context.settings.width();
        context.skeleton.crownRadius = length;
        double base = context.random.nextDouble() * TAU;
        double leafy = 0.7 + 0.3 * context.settings.density();
        for (int frond = 0; frond < fronds; frond++) {
            double angle = base + frond * TAU / fronds + (context.random.nextDouble() - 0.5) * 0.5;
            double rise = Math.toRadians(15.0 + context.random.nextDouble() * 30.0);
            // Arco: sube un poco y cae hasta acabar entre un tercio y tres cuartos del largo por debajo.
            double fall = Math.sin(rise) + 0.5 + context.random.nextDouble() * 0.35;
            Vec3 out = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            Vec3 side = new Vec3(-Math.sin(angle), 0.0, Math.cos(angle));
            List<Vec3> spine = new ArrayList<>();
            int samples = (int) Math.ceil(length * 3.0);
            for (int sample = 0; sample <= samples; sample++) {
                double s = sample / (double) samples;
                spine.add(crown.add(out.multiply(length * s * (1.0 - 0.3 * s)))
                        .add(UP.multiply(length * (Math.sin(rise) * s - fall * s * s))));
            }
            context.skeleton.fronds.add(spine);
            for (int sample = 3; sample < samples; sample += 2) {
                double s = sample / (double) samples;
                double span = 1.25 * Math.pow(Math.sin(Math.PI * Math.min(1.0, s * 1.1)), 0.6) * leafy;
                if (span < 0.6) {
                    continue;
                }
                Vec3 root = spine.get(sample);
                for (int sign = -1; sign <= 1; sign += 2) {
                    Vec3 tip = root.add(side.multiply(span * sign * 0.85)).add(out.multiply(span * 0.25))
                            .add(UP.multiply(-1.1 * span));
                    context.skeleton.fronds.add(List.of(root, tip));
                }
            }
        }
        // Corazón de hojas sobre la corona.
        context.clump(crown.add(new Vec3(0.0, 0.5, 0.0)), 1.0, 0.9);
    }

    // ── Baobab ─────────────────────────────────────────────────────────────────────────────────────────────

    private static void baobab(Context context) {
        TreeSpecies.Form form = context.form;
        double height = context.height;
        double length = height * 0.78;
        List<Vec3> trunk = context.trunkPath(length, 1.0);
        double radius = context.trunkRadius;
        double[] positions = {0.0, 0.12, 0.4, 0.7, 0.9, 1.0};
        double[] factors = {1.18, 1.0, 1.06, 0.86, 0.62, 0.5};
        List<Vec3> points = new ArrayList<>();
        List<Double> radii = new ArrayList<>();
        points.add(context.base.subtract(new Vec3(0.0, context.plantDepth, 0.0)));
        radii.add(radius * 1.15);
        for (int index = 0; index < trunk.size(); index++) {
            double t = index / (double) (trunk.size() - 1);
            points.add(trunk.get(index));
            radii.add(radius * interpolate(positions, factors, t));
        }
        context.skeleton.limb(points, unbox(radii));
        context.skeleton.axisX = trunk.getLast().x();
        context.skeleton.axisZ = trunk.getLast().z();
        boolean foliage = context.settings.leafMaterial() != null;
        int branches = (int) Math.clamp(Math.round((6 + context.random.nextInt(4)) * context.settings.branches()),
                1L, 16L);
        double reach = height * 0.3 * context.settings.width();
        context.skeleton.crownRadius = reach + radius;
        double base = context.random.nextDouble() * TAU;
        double clump = form.clump * (0.8 + 0.2 * context.settings.density());
        for (int branch = 0; branch < branches; branch++) {
            double angle = base + branch * TAU / branches + (context.random.nextDouble() - 0.5) * 0.6;
            double rise = Math.toRadians(25.0 + context.random.nextDouble() * 35.0);
            Vec3 start = along(trunk, length * (0.84 + context.random.nextDouble() * 0.16), 1.0);
            double size = reach * (0.75 + context.random.nextDouble() * 0.45);
            Vec3 out = direction(angle, rise);
            Vec3 middle = start.add(out.multiply(size * 0.5)).add(new Vec3(0.0, size * 0.08, 0.0));
            Vec3 end = start.add(out.multiply(size)).add(new Vec3(0.0, size * 0.18, 0.0));
            double thick = Math.max(0.7, radius * 0.32);
            context.skeleton.limb(List.of(start, middle, end), new double[]{thick, thick * 0.75, thick * 0.55});
            int twigs = 2 + context.random.nextInt(2);
            for (int twig = 0; twig < twigs; twig++) {
                double spread = angle + (twig - (twigs - 1) * 0.5) * 0.75 + (context.random.nextDouble() - 0.5) * 0.3;
                Vec3 tip = end.add(direction(spread, rise + 0.25).multiply(size * (0.35 + 0.2
                        * context.random.nextDouble())));
                context.skeleton.limb(List.of(end, tip), new double[]{Math.max(0.5, thick * 0.5), 0.5});
                if (foliage) {
                    context.clump(tip.add(new Vec3(0.0, 0.4, 0.0)), clump * (0.8 + context.random.nextDouble() * 0.4),
                            form.flatten);
                }
            }
        }
    }

    // ── Raíces ─────────────────────────────────────────────────────────────────────────────────────────────

    /** Raíces que salen de la base y siguen el terreno; al final se hunden en él. */
    private static void roots(Context context) {
        if (context.settings.rootMaterial() == null) {
            return;
        }
        TreeSpecies.Form form = context.form;
        int count = (int) Math.round(form.roots * (0.85 + context.random.nextDouble() * 0.3));
        if (count <= 0) {
            return;
        }
        double radius = context.trunkRadius;
        double base = context.random.nextDouble() * TAU;
        for (int root = 0; root < count; root++) {
            double angle = base + root * TAU / count + (context.random.nextDouble() - 0.5) * 0.7 * TAU / count;
            double start = Math.max(0.5, radius * (0.5 + context.random.nextDouble() * 0.2));
            double length = radius * form.rootLength * (0.7 + context.random.nextDouble() * 0.6) + 1.0;
            long seed = context.seed + root * 0x9E37_79B9L;
            List<Vec3> points = new ArrayList<>();
            List<Double> radii = new ArrayList<>();
            points.add(context.base.add(new Vec3(Math.cos(angle) * radius * 0.3, radius * 0.55 + 0.35,
                    Math.sin(angle) * radius * 0.3)));
            radii.add(start * 1.1);
            int steps = Math.max(2, (int) Math.ceil(length / 1.1));
            int ground = context.baseY - 1;
            for (int step = 1; step <= steps; step++) {
                double t = step / (double) steps;
                double distance = radius * 0.55 + t * length;
                double heading = angle + noise(step * 0.3, seed) * 0.6 * Math.max(0.4, context.wander);
                double x = context.base.x() + Math.cos(heading) * distance;
                double z = context.base.z() + Math.sin(heading) * distance;
                int surface = context.terrain.surface((int) Math.floor(x), (int) Math.floor(z), ground + 3,
                        ground - 6);
                ground = surface == Integer.MIN_VALUE ? ground - 1 : surface;
                double size = 0.5 + (start - 0.5) * Math.pow(1.0 - t, 1.3);
                points.add(new Vec3(x, ground + 1.0 + size * 0.25 - 1.4 * t * t, z));
                radii.add(size);
            }
            context.skeleton.roots.add(new TreeSkeleton.Limb(points, unbox(radii)));
        }
    }

    // ── Utilidades ─────────────────────────────────────────────────────────────────────────────────────────

    /** Ensanchamiento de la base del tronco a {@code along} bloques del suelo. */
    private static double flare(Context context, double along) {
        return 1.0 + context.form.flare * Math.exp(-along / (0.8 * context.trunkRadius + 0.6));
    }

    private static List<Vec3> withBase(Context context, List<Vec3> trunk) {
        List<Vec3> points = new ArrayList<>(trunk.size() + 1);
        points.add(context.base.subtract(new Vec3(0.0, context.plantDepth, 0.0)));
        points.addAll(trunk);
        return points;
    }

    /** Radios de un tronco con su punto enterrado delante: de {@code bottom} a {@code top} con la base ensanchada. */
    private static double[] radii(Context context, List<Vec3> trunk, double bottom, double top) {
        double[] radii = new double[trunk.size() + 1];
        radii[0] = bottom * (1.0 + context.form.flare);
        for (int index = 0; index < trunk.size(); index++) {
            double t = index / (double) Math.max(1, trunk.size() - 1);
            radii[index + 1] = Math.max(0.5, (bottom + (top - bottom) * t) * flare(context, index));
        }
        return radii;
    }

    /** Punto a {@code distance} bloques a lo largo de un recorrido de paso {@code step}; más allá, lo prolonga. */
    static Vec3 along(List<Vec3> path, double distance, double step) {
        double index = Math.max(0.0, distance / step);
        int last = path.size() - 1;
        if (index >= last) {
            Vec3 heading = path.get(last).subtract(path.get(last - 1));
            return path.get(last).add(heading.multiply(index - last));
        }
        int lower = (int) Math.floor(index);
        return path.get(lower).lerp(path.get(lower + 1), index - lower);
    }

    /** Dirección con rumbo {@code angle} (0 = este) y elevación {@code elevation} sobre la horizontal. */
    static Vec3 direction(double angle, double elevation) {
        double horizontal = Math.cos(elevation);
        return new Vec3(Math.cos(angle) * horizontal, Math.sin(elevation), Math.sin(angle) * horizontal);
    }

    private static double interpolate(double[] positions, double[] values, double t) {
        for (int index = 1; index < positions.length; index++) {
            if (t <= positions[index]) {
                double amount = (t - positions[index - 1]) / (positions[index] - positions[index - 1]);
                double smooth = amount * amount * (3.0 - 2.0 * amount);
                return values[index - 1] + (values[index] - values[index - 1]) * smooth;
            }
        }
        return values[values.length - 1];
    }

    private static double[] unbox(List<Double> values) {
        double[] result = new double[values.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    /** Ruido de valor 1D suave en [-1, 1], con dos octavas. */
    static double noise(double t, long seed) {
        return valueNoise(t, seed) * 0.7 + valueNoise(t * 2.1 + 17.3, seed ^ 0x5DEE_CE66DL) * 0.3;
    }

    private static double valueNoise(double t, long seed) {
        long cell = (long) Math.floor(t);
        double fraction = t - cell;
        double smooth = fraction * fraction * (3.0 - 2.0 * fraction);
        double first = hash(cell, seed);
        return first + (hash(cell + 1, seed) - first) * smooth;
    }

    static double hash(long value, long seed) {
        long mixed = mix(value * 0x9E37_79B9_7F4A_7C15L + seed);
        return (mixed >>> 11) * 0x1.0p-53 * 2.0 - 1.0;
    }

    static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return z ^ (z >>> 31);
    }

    /** Estado de un crecimiento. */
    private static final class Context {
        final TreeSettings settings;
        final TreeSpecies.Form form;
        final NatureTerrain terrain;
        final SplittableRandom random;
        final long seed;
        final double height;
        final double trunkRadius;
        final Vec3 base;
        final int baseY;
        final double lean;
        final double azimuth;
        final double wander;
        final int plantDepth;
        final TreeSkeleton skeleton = new TreeSkeleton();

        Context(TreeSettings settings, NatureTerrain terrain, int x, int y, int z, long seed) {
            this.settings = settings;
            this.form = settings.species().form();
            this.terrain = terrain;
            this.seed = seed;
            this.random = new SplittableRandom(seed);
            this.height = settings.height();
            this.trunkRadius = TreeGrower.trunkRadius(settings);
            this.base = new Vec3(x + 0.5, y, z + 0.5);
            this.baseY = y;
            this.lean = Math.toRadians(settings.lean());
            this.azimuth = Double.isNaN(settings.azimuth()) ? random.nextDouble() * TAU
                    : Math.toRadians(settings.azimuth());
            this.wander = form.wander * settings.curvature();
            this.plantDepth = plantDepth(terrain, x, y, z, trunkRadius);
        }

        /**
         * Hasta dónde se entierra el tronco: dos bloques bajo la base y, en una ladera, lo que haga falta para que
         * el lado de abajo no quede al aire.
         */
        private static int plantDepth(NatureTerrain terrain, int x, int y, int z, double radius) {
            int reach = (int) Math.ceil(radius + 0.5);
            int lowest = y - 1;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (dx * dx + dz * dz > (radius + 0.5) * (radius + 0.5)) {
                        continue;
                    }
                    int surface = terrain.surface(x + dx, z + dz, y + 2, y - 8);
                    lowest = Math.min(lowest, surface == Integer.MIN_VALUE ? y - 8 : surface);
                }
            }
            return Math.clamp(y - (lowest + 1) + 2, 2, 9);
        }

        void clump(Vec3 center, double radius, double flatten) {
            if (radius > 0.4) {
                skeleton.clumps.add(new TreeSkeleton.Clump(center, radius, flatten));
            }
        }

        /** Recorrido del tronco desde la base, con su inclinación y su ruido, en pasos de {@code step} bloques. */
        List<Vec3> trunkPath(double length, double step) {
            List<Vec3> points = new ArrayList<>();
            Vec3 point = base;
            points.add(point);
            int steps = Math.max(2, (int) Math.ceil(length / step));
            double leanX = Math.cos(azimuth);
            double leanZ = Math.sin(azimuth);
            double phase = random.nextDouble() * TAU;
            long noiseSeed = seed ^ 0x7AB1_E5L;
            for (int index = 1; index <= steps; index++) {
                double t = index / (double) steps;
                double travelled = t * length;
                double bend = switch (form.leanStyle) {
                    case STRAIGHT -> lean;
                    case RECOVER -> lean * (1.0 - 0.75 * t);
                    case BANANA -> Math.min(1.35, lean * (0.35 + 1.6 * t * t));
                    case SERPENT -> lean + 0.42 * Math.sin(phase + t * TAU * 0.9) * Math.min(1.0, wander);
                };
                double sideways = form.leanStyle == TreeSpecies.LeanStyle.SERPENT
                        ? 0.35 * Math.cos(phase + t * TAU * 1.1) * Math.min(1.0, wander) : 0.0;
                double wanderX = noise(travelled * 0.11, noiseSeed) * wander * 0.22;
                double wanderZ = noise(travelled * 0.11, noiseSeed + 1) * wander * 0.22;
                double dx = Math.sin(bend) * leanX - sideways * leanZ + wanderX;
                double dz = Math.sin(bend) * leanZ + sideways * leanX + wanderZ;
                double dy = Math.cos(bend);
                double norm = Math.sqrt(dx * dx + dy * dy + dz * dz);
                point = point.add(new Vec3(dx / norm * step, dy / norm * step, dz / norm * step));
                points.add(point);
            }
            return points;
        }
    }
}
