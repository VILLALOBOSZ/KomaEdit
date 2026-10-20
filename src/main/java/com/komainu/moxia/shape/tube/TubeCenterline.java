package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Eje del tubo muestreado cada {@value #STEP} bloques con todo lo que el espacio de tubo necesita por muestra:
 * posición, marco (tangente, arriba y lado), radio, coordenada longitudinal y giro.
 *
 * <p>La curva usa Kochanek–Bartels (tensión, sesgo y continuidad; con los tres a 0 es un Catmull-Rom) con la
 * corrección de tangentes por longitud de cuerda, que evita los bucles cuando los puntos están desigualmente
 * separados. Los marcos se transportan con el método de doble reflexión de Wang et&nbsp;al. (2008), que no
 * acumula giro espurio en curvas 3D.</p>
 */
final class TubeCenterline {
    static final double STEP = 0.25;
    private static final double MINIMUM_RADIUS = 0.5;

    final int count;
    final boolean closed;
    final double length;
    final double[] px;
    final double[] py;
    final double[] pz;
    final double[] tx;
    final double[] ty;
    final double[] tz;
    final double[] ux;
    final double[] uy;
    final double[] uz;
    final double[] sx;
    final double[] sy;
    final double[] sz;
    final double[] distance;
    final double[] radius;
    final double[] stretch;
    final double[] longitudinal;
    final double[] roll;
    final TubeSettings.NormalMode normalMode;
    private final SegmentTree tree;

    private TubeCenterline(List<Vec3> points, boolean closed, TubeSettings settings, TubeCurve curve) {
        this.closed = closed;
        this.normalMode = settings.normalMode();
        count = points.size();
        px = new double[count];
        py = new double[count];
        pz = new double[count];
        tx = new double[count];
        ty = new double[count];
        tz = new double[count];
        ux = new double[count];
        uy = new double[count];
        uz = new double[count];
        sx = new double[count];
        sy = new double[count];
        sz = new double[count];
        distance = new double[count];
        radius = new double[count];
        stretch = new double[count];
        longitudinal = new double[count];
        roll = new double[count];
        for (int index = 0; index < count; index++) {
            Vec3 point = points.get(index);
            px[index] = point.x();
            py[index] = point.y();
            pz[index] = point.z();
            if (index > 0) {
                distance[index] = distance[index - 1] + point.distance(points.get(index - 1));
            }
        }
        length = distance[count - 1];
        computeTangents();
        computeFrames();
        computeProfile(settings, curve);
        tree = new SegmentTree(px, py, pz);
    }

    static TubeCenterline create(SampledPath path, TubeSettings settings, TubeCurve curve) {
        List<Vec3> controls = distinct(path.controlPoints(), path.closed());
        int minimum = path.closed() ? 3 : 2;
        if (controls.size() < minimum) {
            throw new IllegalArgumentException("La trayectoria necesita al menos " + minimum + " puntos distintos");
        }
        List<Vec3> dense = kochanekBartels(controls, path.closed(), settings.tension(), settings.bias(),
                settings.continuity());
        List<Vec3> uniform = resample(dense);
        return new TubeCenterline(uniform, path.closed(), settings, curve);
    }

    /** Eje abierto por puntos de control libres (no centros de bloque), para las ramas de {@link TubeLimb}. */
    static TubeCenterline through(List<Vec3> points, TubeSettings settings) {
        List<Vec3> controls = distinct(points, false);
        if (controls.size() < 2) {
            throw new IllegalArgumentException("La rama necesita al menos 2 puntos distintos");
        }
        List<Vec3> dense = kochanekBartels(controls, false, settings.tension(), settings.bias(),
                settings.continuity());
        return new TubeCenterline(resample(dense), false, settings, TubeCurve.NONE);
    }

    double minimumRadius() {
        double minimum = Double.MAX_VALUE;
        for (double value : radius) {
            minimum = Math.min(minimum, value);
        }
        return minimum;
    }

    /** Localiza el punto del eje más cercano a {@code (x, y, z)} y rellena las coordenadas de tubo. */
    void locate(double x, double y, double z, TubePoint point) {
        tree.nearest(x, y, z, point);
        int segment = point.segment;
        double amount = point.amount;
        int next = segment + 1;
        double cx = px[segment] + (px[next] - px[segment]) * amount;
        double cy = py[segment] + (py[next] - py[segment]) * amount;
        double cz = pz[segment] + (pz[next] - pz[segment]) * amount;
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;

        double tangentX = tx[segment] + (tx[next] - tx[segment]) * amount;
        double tangentY = ty[segment] + (ty[next] - ty[segment]) * amount;
        double tangentZ = tz[segment] + (tz[next] - tz[segment]) * amount;
        double tangentLength = Math.sqrt(tangentX * tangentX + tangentY * tangentY + tangentZ * tangentZ);
        if (tangentLength < 1.0E-9) {
            tangentX = tx[segment];
            tangentY = ty[segment];
            tangentZ = tz[segment];
        } else {
            tangentX /= tangentLength;
            tangentY /= tangentLength;
            tangentZ /= tangentLength;
        }

        double upX = ux[segment] + (ux[next] - ux[segment]) * amount;
        double upY = uy[segment] + (uy[next] - uy[segment]) * amount;
        double upZ = uz[segment] + (uz[next] - uz[segment]) * amount;
        double sideX;
        double sideY;
        double sideZ;
        if (normalMode == TubeSettings.NormalMode.UPRIGHT) {
            sideX = sx[segment] + (sx[next] - sx[segment]) * amount;
            sideY = 0.0;
            sideZ = sz[segment] + (sz[next] - sz[segment]) * amount;
            double sideLength = Math.sqrt(sideX * sideX + sideZ * sideZ);
            sideX /= sideLength < 1.0E-9 ? 1.0 : sideLength;
            sideZ /= sideLength < 1.0E-9 ? 1.0 : sideLength;
            upX = 0.0;
            upY = 1.0;
            upZ = 0.0;
        } else {
            double along = upX * tangentX + upY * tangentY + upZ * tangentZ;
            upX -= tangentX * along;
            upY -= tangentY * along;
            upZ -= tangentZ * along;
            double upLength = Math.sqrt(upX * upX + upY * upY + upZ * upZ);
            if (upLength < 1.0E-9) {
                upX = ux[segment];
                upY = uy[segment];
                upZ = uz[segment];
            } else {
                upX /= upLength;
                upY /= upLength;
                upZ /= upLength;
            }
            sideX = upY * tangentZ - upZ * tangentY;
            sideY = upZ * tangentX - upX * tangentZ;
            sideZ = upX * tangentY - upY * tangentX;
        }

        double axial = dx * tangentX + dy * tangentY + dz * tangentZ;
        point.localX = dx * sideX + dy * sideY + dz * sideZ;
        point.localY = dx * upX + dy * upY + dz * upZ;
        point.distance = distance[segment] + (distance[next] - distance[segment]) * amount;
        point.radius = radius[segment] + (radius[next] - radius[segment]) * amount;
        point.stretch = stretch[segment] + (stretch[next] - stretch[segment]) * amount;
        point.longitudinal = longitudinal[segment] + (longitudinal[next] - longitudinal[segment]) * amount;
        point.roll = roll[segment] + (roll[next] - roll[segment]) * amount;
        point.progress = length <= 0.0 ? 0.0 : point.distance / length;
        point.beyond = 0.0;
        if (!closed) {
            if (segment == 0 && amount <= 0.0 && axial < 0.0) {
                point.beyond = -axial;
                point.longitudinal -= point.beyond / (point.radius * point.stretch);
            } else if (next == count - 1 && amount >= 1.0 && axial > 0.0) {
                point.beyond = axial;
                point.longitudinal += point.beyond / (point.radius * point.stretch);
            }
        }
    }

    private void computeTangents() {
        int last = count - 1;
        for (int index = 0; index < count; index++) {
            int before;
            int after;
            if (closed) {
                // La última muestra repite la primera: el vecino real de los extremos está al otro lado.
                before = index == 0 ? last - 1 : index - 1;
                after = index == last ? 1 : index + 1;
            } else {
                before = Math.max(0, index - 1);
                after = Math.min(last, index + 1);
            }
            double x = px[after] - px[before];
            double y = py[after] - py[before];
            double z = pz[after] - pz[before];
            double size = Math.sqrt(x * x + y * y + z * z);
            if (size < 1.0E-9) {
                if (index > 0) {
                    tx[index] = tx[index - 1];
                    ty[index] = ty[index - 1];
                    tz[index] = tz[index - 1];
                } else {
                    tx[index] = 1.0;
                }
                continue;
            }
            tx[index] = x / size;
            ty[index] = y / size;
            tz[index] = z / size;
        }
    }

    private void computeFrames() {
        double[] initial = initialUp(tx[0], ty[0], tz[0]);
        ux[0] = initial[0];
        uy[0] = initial[1];
        uz[0] = initial[2];
        for (int index = 1; index < count; index++) {
            transport(index - 1, index);
            if (normalMode == TubeSettings.NormalMode.HORIZONTAL) {
                leanTowardsHorizontal(index);
            }
        }
        if (closed && normalMode == TubeSettings.NormalMode.CONSISTENT) {
            closeHolonomy();
        }
        for (int index = 0; index < count; index++) {
            if (normalMode == TubeSettings.NormalMode.UPRIGHT) {
                // Lado horizontal = Y × T; en tramos verticales se conserva el lado anterior.
                double x = tz[index];
                double z = -tx[index];
                double size = Math.sqrt(x * x + z * z);
                if (size < 1.0E-3) {
                    if (index > 0) {
                        sx[index] = sx[index - 1];
                        sz[index] = sz[index - 1];
                    } else {
                        sx[index] = 1.0;
                    }
                } else {
                    sx[index] = x / size;
                    sz[index] = z / size;
                }
                ux[index] = 0.0;
                uy[index] = 1.0;
                uz[index] = 0.0;
                continue;
            }
            sx[index] = uy[index] * tz[index] - uz[index] * ty[index];
            sy[index] = uz[index] * tx[index] - ux[index] * tz[index];
            sz[index] = ux[index] * ty[index] - uy[index] * tx[index];
        }
    }

    /** Doble reflexión: transporta el vector "arriba" de {@code from} a {@code to} sin rotación espuria. */
    private void transport(int from, int to) {
        double v1x = px[to] - px[from];
        double v1y = py[to] - py[from];
        double v1z = pz[to] - pz[from];
        double c1 = v1x * v1x + v1y * v1y + v1z * v1z;
        double rx = ux[from];
        double ry = uy[from];
        double rz = uz[from];
        double tLx = tx[from];
        double tLy = ty[from];
        double tLz = tz[from];
        if (c1 > 1.0E-12) {
            double rDot = 2.0 / c1 * (v1x * rx + v1y * ry + v1z * rz);
            rx -= rDot * v1x;
            ry -= rDot * v1y;
            rz -= rDot * v1z;
            double tDot = 2.0 / c1 * (v1x * tLx + v1y * tLy + v1z * tLz);
            tLx -= tDot * v1x;
            tLy -= tDot * v1y;
            tLz -= tDot * v1z;
        }
        double v2x = tx[to] - tLx;
        double v2y = ty[to] - tLy;
        double v2z = tz[to] - tLz;
        double c2 = v2x * v2x + v2y * v2y + v2z * v2z;
        if (c2 > 1.0E-12) {
            double rDot = 2.0 / c2 * (v2x * rx + v2y * ry + v2z * rz);
            rx -= rDot * v2x;
            ry -= rDot * v2y;
            rz -= rDot * v2z;
        }
        double along = rx * tx[to] + ry * ty[to] + rz * tz[to];
        rx -= along * tx[to];
        ry -= along * ty[to];
        rz -= along * tz[to];
        double size = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (size < 1.0E-9) {
            double[] fallback = initialUp(tx[to], ty[to], tz[to]);
            ux[to] = fallback[0];
            uy[to] = fallback[1];
            uz[to] = fallback[2];
            return;
        }
        ux[to] = rx / size;
        uy[to] = ry / size;
        uz[to] = rz / size;
    }

    /**
     * Modo horizontal: la cara superior intenta mirar al cielo. Cuando el tramo se vuelve vertical el "arriba"
     * del mundo deja de estar definido y se mantiene el marco transportado, sin saltos.
     */
    private void leanTowardsHorizontal(int index) {
        double t = ty[index];
        double hx = -t * tx[index];
        double hy = 1.0 - t * ty[index];
        double hz = -t * tz[index];
        double horizontal = Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (horizontal < 1.0E-6) {
            return;
        }
        hx /= horizontal;
        hy /= horizontal;
        hz /= horizontal;
        double weight = smoothStep((horizontal - 0.08) / 0.22);
        rotateUpTowards(index, hx, hy, hz, weight);
    }

    /** Reparte el desfase de cierre de un lazo a lo largo de todo el recorrido para que el marco empalme. */
    private void closeHolonomy() {
        int last = count - 1;
        double crossX = uy[last] * uz[0] - uz[last] * uy[0];
        double crossY = uz[last] * ux[0] - ux[last] * uz[0];
        double crossZ = ux[last] * uy[0] - uy[last] * ux[0];
        double sine = crossX * tx[0] + crossY * ty[0] + crossZ * tz[0];
        double cosine = ux[last] * ux[0] + uy[last] * uy[0] + uz[last] * uz[0];
        double mismatch = Math.atan2(sine, cosine);
        if (Math.abs(mismatch) < 1.0E-9 || length <= 0.0) {
            return;
        }
        for (int index = 0; index < count; index++) {
            rotateUp(index, mismatch * distance[index] / length);
        }
    }

    private void rotateUpTowards(int index, double targetX, double targetY, double targetZ, double weight) {
        double crossX = uy[index] * targetZ - uz[index] * targetY;
        double crossY = uz[index] * targetX - ux[index] * targetZ;
        double crossZ = ux[index] * targetY - uy[index] * targetX;
        double sine = crossX * tx[index] + crossY * ty[index] + crossZ * tz[index];
        double cosine = ux[index] * targetX + uy[index] * targetY + uz[index] * targetZ;
        rotateUp(index, Math.atan2(sine, cosine) * weight);
    }

    private void rotateUp(int index, double angle) {
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        double crossX = ty[index] * uz[index] - tz[index] * uy[index];
        double crossY = tz[index] * ux[index] - tx[index] * uz[index];
        double crossZ = tx[index] * uy[index] - ty[index] * ux[index];
        double x = ux[index] * cosine + crossX * sine;
        double y = uy[index] * cosine + crossY * sine;
        double z = uz[index] * cosine + crossZ * sine;
        double size = Math.sqrt(x * x + y * y + z * z);
        ux[index] = x / size;
        uy[index] = y / size;
        uz[index] = z / size;
    }

    private void computeProfile(TubeSettings settings, TubeCurve curve) {
        double startRoll = Math.toRadians(settings.startRollDegrees());
        double endRoll = Math.toRadians(settings.endRollDegrees());
        double twistPerDiameter = Math.toRadians(settings.twistPerDiameter());
        for (int index = 0; index < count; index++) {
            double progress = length <= 0.0 ? 0.0 : distance[index] / length;
            double eased = curve.ease(progress);
            radius[index] = Math.max(MINIMUM_RADIUS,
                    settings.radius() * settings.radii().at(progress) * curve.scale(eased));
            stretch[index] = settings.stretch() * curve.stretch(eased);
            if (index > 0) {
                double step = distance[index] - distance[index - 1];
                double previous = 1.0 / (radius[index - 1] * stretch[index - 1]);
                double current = 1.0 / (radius[index] * stretch[index]);
                longitudinal[index] = longitudinal[index - 1] + step * 0.5 * (previous + current);
            }
            double base = settings.rollRange()
                    ? startRoll + (endRoll - startRoll) * progress
                    : startRoll + twistPerDiameter * longitudinal[index] * 0.5;
            roll[index] = base + Math.toRadians(curve.twistDegrees()) * eased;
        }
    }

    private static double[] initialUp(double x, double y, double z) {
        double upX = -y * x;
        double upY = 1.0 - y * y;
        double upZ = -y * z;
        double size = Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        if (size < 1.0E-3) {
            // Tangente vertical: el lado de referencia es el eje X del mundo.
            upX = 1.0 - x * x;
            upY = -x * y;
            upZ = -x * z;
            size = Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        }
        return new double[]{upX / size, upY / size, upZ / size};
    }

    private static List<Vec3> distinct(List<Vec3> controls, boolean closed) {
        List<Vec3> result = new ArrayList<>(controls.size());
        for (Vec3 control : controls) {
            if (result.isEmpty() || result.getLast().distance(control) > 1.0E-6) {
                result.add(control);
            }
        }
        if (closed && result.size() > 1 && result.getFirst().distance(result.getLast()) < 1.0E-6) {
            result.removeLast();
        }
        return result;
    }

    /** Curva densa (≈8 muestras por bloque) con tangentes de Kochanek–Bartels y corrección por cuerda. */
    static List<Vec3> kochanekBartels(
            List<Vec3> controls,
            boolean closed,
            double tension,
            double bias,
            double continuity
    ) {
        int size = controls.size();
        int segments = closed ? size : size - 1;
        List<Vec3> dense = new ArrayList<>();
        for (int segment = 0; segment < segments; segment++) {
            Vec3 previous = control(controls, segment - 1, closed);
            Vec3 start = control(controls, segment, closed);
            Vec3 end = control(controls, segment + 1, closed);
            Vec3 following = control(controls, segment + 2, closed);
            double before = previous.distance(start);
            double chord = start.distance(end);
            double after = end.distance(following);

            Vec3 outgoing = start.subtract(previous).multiply((1 - tension) * (1 + bias) * (1 - continuity) * 0.5)
                    .add(end.subtract(start).multiply((1 - tension) * (1 - bias) * (1 + continuity) * 0.5))
                    .multiply(2.0 * chord / Math.max(1.0E-9, before + chord));
            Vec3 incoming = end.subtract(start).multiply((1 - tension) * (1 + bias) * (1 + continuity) * 0.5)
                    .add(following.subtract(end).multiply((1 - tension) * (1 - bias) * (1 - continuity) * 0.5))
                    .multiply(2.0 * chord / Math.max(1.0E-9, chord + after));

            int steps = Math.clamp((int) Math.ceil(chord * 8.0), 8, 8_192);
            for (int step = 0; step < steps; step++) {
                dense.add(hermite(start, outgoing, end, incoming, step / (double) steps));
            }
        }
        dense.add(closed ? controls.getFirst() : controls.getLast());
        return dense;
    }

    private static Vec3 control(List<Vec3> controls, int index, boolean closed) {
        int size = controls.size();
        if (closed) {
            return controls.get(Math.floorMod(index, size));
        }
        if (index < 0) {
            return controls.getFirst().multiply(2.0).subtract(controls.get(1));
        }
        if (index >= size) {
            return controls.getLast().multiply(2.0).subtract(controls.get(size - 2));
        }
        return controls.get(index);
    }

    private static Vec3 hermite(Vec3 start, Vec3 startTangent, Vec3 end, Vec3 endTangent, double amount) {
        double squared = amount * amount;
        double cubed = squared * amount;
        return start.multiply(2 * cubed - 3 * squared + 1)
                .add(startTangent.multiply(cubed - 2 * squared + amount))
                .add(end.multiply(-2 * cubed + 3 * squared))
                .add(endTangent.multiply(cubed - squared));
    }

    /** Reparte las muestras a distancia de arco constante para que índice y longitud vayan de la mano. */
    private static List<Vec3> resample(List<Vec3> dense) {
        double[] cumulative = new double[dense.size()];
        for (int index = 1; index < dense.size(); index++) {
            cumulative[index] = cumulative[index - 1] + dense.get(index).distance(dense.get(index - 1));
        }
        double total = cumulative[cumulative.length - 1];
        if (total < 0.1) {
            throw new IllegalArgumentException("La trayectoria es demasiado corta");
        }
        if (total > SampledPath.MAX_LENGTH) {
            throw new IllegalArgumentException("La trayectoria supera el límite de 4096 bloques de longitud");
        }
        int intervals = Math.max(2, (int) Math.ceil(total / STEP));
        List<Vec3> result = new ArrayList<>(intervals + 1);
        int cursor = 0;
        for (int sample = 0; sample <= intervals; sample++) {
            double target = total * sample / intervals;
            while (cursor < cumulative.length - 2 && cumulative[cursor + 1] < target) {
                cursor++;
            }
            double span = cumulative[cursor + 1] - cumulative[cursor];
            double amount = span < 1.0E-12 ? 0.0 : Math.clamp((target - cumulative[cursor]) / span, 0.0, 1.0);
            result.add(dense.get(cursor).lerp(dense.get(cursor + 1), amount));
        }
        return result;
    }

    private static double smoothStep(double value) {
        double clamped = Math.clamp(value, 0.0, 1.0);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    /** Resultado mutable de una consulta; se reutiliza por hilo para no crear objetos por bloque. */
    static final class TubePoint {
        int segment;
        double amount;
        double distanceSquared;
        double localX;
        double localY;
        double distance;
        double radius;
        double stretch;
        double longitudinal;
        double roll;
        double progress;
        double beyond;
        private final int[] stack = new int[128];
        private double candidateAmount;
    }

    /**
     * Árbol de esferas envolventes sobre los segmentos del eje. Las consultas parten del último segmento
     * encontrado: entre bloques vecinos casi nunca cambia, así que la poda corta el árbol casi entero.
     */
    static final class SegmentTree {
        private static final int LEAF_SEGMENTS = 8;
        private final double[] px;
        private final double[] py;
        private final double[] pz;
        private final int segments;
        private final int[] first;
        private final int[] last;
        private final int[] left;
        private final int[] right;
        private final double[] centerX;
        private final double[] centerY;
        private final double[] centerZ;
        private final double[] reach;
        private int nodes;

        SegmentTree(double[] px, double[] py, double[] pz) {
            this.px = px;
            this.py = py;
            this.pz = pz;
            segments = px.length - 1;
            // Las hojas tienen entre LEAF/2 y LEAF segmentos: nunca hay más de `segments` nodos.
            int capacity = Math.max(4, segments + 4);
            first = new int[capacity];
            last = new int[capacity];
            left = new int[capacity];
            right = new int[capacity];
            centerX = new double[capacity];
            centerY = new double[capacity];
            centerZ = new double[capacity];
            reach = new double[capacity];
            build(0, segments);
        }

        private int build(int start, int end) {
            int node = nodes++;
            first[node] = start;
            last[node] = end;
            double minimumX = Double.MAX_VALUE;
            double minimumY = Double.MAX_VALUE;
            double minimumZ = Double.MAX_VALUE;
            double maximumX = -Double.MAX_VALUE;
            double maximumY = -Double.MAX_VALUE;
            double maximumZ = -Double.MAX_VALUE;
            for (int index = start; index <= end; index++) {
                minimumX = Math.min(minimumX, px[index]);
                minimumY = Math.min(minimumY, py[index]);
                minimumZ = Math.min(minimumZ, pz[index]);
                maximumX = Math.max(maximumX, px[index]);
                maximumY = Math.max(maximumY, py[index]);
                maximumZ = Math.max(maximumZ, pz[index]);
            }
            double x = (minimumX + maximumX) * 0.5;
            double y = (minimumY + maximumY) * 0.5;
            double z = (minimumZ + maximumZ) * 0.5;
            double farthest = 0.0;
            for (int index = start; index <= end; index++) {
                double dx = px[index] - x;
                double dy = py[index] - y;
                double dz = pz[index] - z;
                farthest = Math.max(farthest, dx * dx + dy * dy + dz * dz);
            }
            centerX[node] = x;
            centerY[node] = y;
            centerZ[node] = z;
            reach[node] = Math.sqrt(farthest);
            if (end - start <= LEAF_SEGMENTS) {
                left[node] = -1;
                right[node] = -1;
                return node;
            }
            int middle = (start + end) >>> 1;
            left[node] = build(start, middle);
            right[node] = build(middle, end);
            return node;
        }

        void nearest(double x, double y, double z, TubePoint point) {
            int[] stack = point.stack;
            int hint = Math.clamp(point.segment, 0, segments - 1);
            double best = segmentDistance(hint, x, y, z, point);
            int bestSegment = hint;
            double bestAmount = point.candidateAmount;
            int size = 0;
            stack[size++] = 0;
            while (size > 0) {
                int node = stack[--size];
                double dx = x - centerX[node];
                double dy = y - centerY[node];
                double dz = z - centerZ[node];
                double gap = Math.sqrt(dx * dx + dy * dy + dz * dz) - reach[node];
                if (gap > 0.0 && gap * gap >= best) {
                    continue;
                }
                if (left[node] < 0) {
                    for (int segment = first[node]; segment < last[node]; segment++) {
                        double candidate = segmentDistance(segment, x, y, z, point);
                        if (candidate < best) {
                            best = candidate;
                            bestSegment = segment;
                            bestAmount = point.candidateAmount;
                        }
                    }
                    continue;
                }
                int near = left[node];
                int far = right[node];
                if (centerDistance(far, x, y, z) < centerDistance(near, x, y, z)) {
                    near = right[node];
                    far = left[node];
                }
                if (size + 2 > stack.length) {
                    throw new IllegalStateException("Árbol de segmentos demasiado profundo");
                }
                stack[size++] = far;
                stack[size++] = near;
            }
            point.segment = bestSegment;
            point.amount = bestAmount;
            point.distanceSquared = best;
        }

        private double segmentDistance(int segment, double x, double y, double z, TubePoint point) {
            int next = segment + 1;
            double abX = px[next] - px[segment];
            double abY = py[next] - py[segment];
            double abZ = pz[next] - pz[segment];
            double apX = x - px[segment];
            double apY = y - py[segment];
            double apZ = z - pz[segment];
            double squared = abX * abX + abY * abY + abZ * abZ;
            double amount = squared < 1.0E-12
                    ? 0.0
                    : Math.clamp((apX * abX + apY * abY + apZ * abZ) / squared, 0.0, 1.0);
            point.candidateAmount = amount;
            double dx = apX - abX * amount;
            double dy = apY - abY * amount;
            double dz = apZ - abZ * amount;
            return dx * dx + dy * dy + dz * dz;
        }

        private double centerDistance(int node, double x, double y, double z) {
            double dx = x - centerX[node];
            double dy = y - centerY[node];
            double dz = z - centerZ[node];
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
