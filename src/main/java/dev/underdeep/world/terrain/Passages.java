package dev.underdeep.world.terrain;

import dev.underdeep.world.noise.Hashing;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 10 secondary cave network of a cavern layer: winding connecting caves between the great chambers of
 * {@link CavernGraph}. A finer grid of cells (half the chamber grid) each owns an <em>anchor</em> (sometimes an
 * intermediate chamber), winding passages to its east/south (and occasionally diagonal) neighbours and sometimes a
 * dead-end side branch ending in an alcove. Passages come in three size classes (side passages, ordinary connecting
 * caves, major corridors) whose width and height vary along their length, and their centre lines meander.
 * <p>
 * Everything is a pure function of (seed, cell), evaluated in the same warped coordinates as the chamber graph, so
 * passages agree across chunk borders whatever the generation order. The terrain model treats a passage exactly like
 * a chamber tunnel: it adds open height above the layer's floor, so floors stay continuous with the caverns.
 * Thread-safe: immutable except for a thread-local cache.
 */
final class Passages {
    /** A winding passage: polyline with per-vertex half-width and height. */
    static final class Edge {
        final double[] px;
        final double[] pz;
        final double[] w;
        final double[] h;
        final double minX, minZ, maxX, maxZ;

        Edge(double[] px, double[] pz, double[] w, double[] h) {
            this.px = px;
            this.pz = pz;
            this.w = w;
            this.h = h;
            double a = Double.MAX_VALUE, b = Double.MAX_VALUE, c = -Double.MAX_VALUE, d = -Double.MAX_VALUE;
            for (int i = 0; i < px.length; i++) {
                a = Math.min(a, px[i] - w[i]);
                b = Math.min(b, pz[i] - w[i]);
                c = Math.max(c, px[i] + w[i]);
                d = Math.max(d, pz[i] + w[i]);
            }
            minX = a;
            minZ = b;
            maxX = c;
            maxZ = d;
        }
    }

    /** A cell's anchor (optionally an intermediate chamber) and the passages it owns. */
    static final class Cell {
        final int cx, cz;
        final double ax, az;
        /** Intermediate chamber half-axis length (0 = none), height, orientation and aspect. */
        final double chamberR, chamberH, cos, sin, axis;
        final Edge[] edges;

        Cell(int cx, int cz, double ax, double az, double chamberR, double chamberH, double cos, double sin, double axis, Edge[] edges) {
            this.cx = cx;
            this.cz = cz;
            this.ax = ax;
            this.az = az;
            this.chamberR = chamberR;
            this.chamberH = chamberH;
            this.cos = cos;
            this.sin = sin;
            this.axis = axis;
            this.edges = edges;
        }
    }

    private final long seed;
    private final int cell;
    private final double scale;
    private final double frequency;
    private final ThreadLocal<Cell[]> cache = ThreadLocal.withInitial(() -> new Cell[1024]);

    /**
     * @param frequency multiplier of the passage probabilities (config {@code caves.passageFrequency}; 0 disables)
     */
    Passages(long layerSeed, CavernLayer layer, double frequency) {
        this.seed = Hashing.hash(layerSeed, Hashing.salt("passages"));
        this.cell = layer.graphCell / 2;
        this.scale = layer == CavernLayer.DEEP ? 1.2 : 1.0;
        this.frequency = frequency;
    }

    int cellSize() {
        return cell;
    }

    Cell cell(int cx, int cz) {
        Cell[] c = cache.get();
        int slot = (int) (Hashing.mix(((long) cx << 32) ^ (cz & 0xFFFFFFFFL) ^ 0x9A55L) & 1023);
        Cell e = c[slot];
        if (e != null && e.cx == cx && e.cz == cz) {
            return e;
        }
        e = compute(cx, cz);
        c[slot] = e;
        return e;
    }

    double anchorX(int cx, int cz) {
        return (cx + 0.2 + 0.6 * Hashing.unit(Hashing.hash(seed, cx, cz, 1))) * cell;
    }

    double anchorZ(int cx, int cz) {
        return (cz + 0.2 + 0.6 * Hashing.unit(Hashing.hash(seed, cx, cz, 2))) * cell;
    }

    private Cell compute(int cx, int cz) {
        double ax = anchorX(cx, cz);
        double az = anchorZ(cx, cz);
        double cr = 0, ch = 0, cos = 1, sin = 0, axis = 1;
        if (Hashing.unit(Hashing.hash(seed, cx, cz, 3)) < 0.33 * Math.min(1, frequency)) {
            double u = Hashing.unit(Hashing.hash(seed, cx, cz, 4));
            cr = (10 + 14 * u * u) * scale;
            ch = 9 + 11 * Hashing.unit(Hashing.hash(seed, cx, cz, 5));
            double ang = Hashing.unit(Hashing.hash(seed, cx, cz, 6)) * Math.PI;
            cos = Math.cos(ang);
            sin = Math.sin(ang);
            axis = 0.55 + 0.45 * Hashing.unit(Hashing.hash(seed, cx, cz, 7));
        }
        List<Edge> edges = new ArrayList<>(4);
        if (Hashing.unit(Hashing.hash(seed, cx, cz, 10)) < 0.62 * frequency) {
            edges.add(edge(ax, az, anchorX(cx + 1, cz), anchorZ(cx + 1, cz), Hashing.hash(seed, cx, cz, 11), false));
        }
        if (Hashing.unit(Hashing.hash(seed, cx, cz, 12)) < 0.62 * frequency) {
            edges.add(edge(ax, az, anchorX(cx, cz + 1), anchorZ(cx, cz + 1), Hashing.hash(seed, cx, cz, 13), false));
        }
        if (Hashing.unit(Hashing.hash(seed, cx, cz, 14)) < 0.16 * frequency) {
            edges.add(edge(ax, az, anchorX(cx + 1, cz + 1), anchorZ(cx + 1, cz + 1), Hashing.hash(seed, cx, cz, 15), false));
        }
        if (Hashing.unit(Hashing.hash(seed, cx, cz, 16)) < 0.35 * frequency) {
            // dead-end side branch ending in a small alcove
            double ang = Hashing.unit(Hashing.hash(seed, cx, cz, 17)) * Math.PI * 2;
            double len = 24 + 36 * Hashing.unit(Hashing.hash(seed, cx, cz, 18));
            edges.add(edge(ax, az, ax + Math.cos(ang) * len, az + Math.sin(ang) * len, Hashing.hash(seed, cx, cz, 19), true));
        }
        return new Cell(cx, cz, ax, az, cr, ch, cos, sin, axis, edges.toArray(new Edge[0]));
    }

    private Edge edge(double ax, double az, double bx, double bz, long h, boolean spur) {
        double dx = bx - ax;
        double dz = bz - az;
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        double nx = -dz / len;
        double nz = dx / len;
        int k = Math.max(4, (int) (len / 14));
        double cls = Hashing.unit(h);
        double wBase;
        double hBase;
        if (spur || cls < 0.35) { // side passage
            wBase = 1.9 + 1.2 * Hashing.unit(Hashing.mix(h + 1));
            hBase = 6.5 + 2 * Hashing.unit(Hashing.mix(h + 2));
        } else if (cls < 0.86) { // ordinary connecting cave
            wBase = 3.2 + 2.8 * Hashing.unit(Hashing.mix(h + 1));
            hBase = 7.5 + 4.5 * Hashing.unit(Hashing.mix(h + 2));
        } else { // major corridor
            wBase = 6.5 + 5.5 * Hashing.unit(Hashing.mix(h + 1));
            hBase = 11 + 9 * Hashing.unit(Hashing.mix(h + 2));
        }
        wBase *= scale;
        double amp = len * (0.10 + 0.14 * Hashing.unit(Hashing.mix(h + 3)));
        double p1 = Hashing.unit(Hashing.mix(h + 4)) * Math.PI * 2;
        double p2 = Hashing.unit(Hashing.mix(h + 5)) * Math.PI * 2;
        double f2 = 1.6 + 1.4 * Hashing.unit(Hashing.mix(h + 6));
        double[] px = new double[k + 1];
        double[] pz = new double[k + 1];
        double[] w = new double[k + 1];
        double[] hh = new double[k + 1];
        for (int i = 0; i <= k; i++) {
            double t = (double) i / k;
            double env = spur ? Math.sin(Math.PI * t * 0.5) : Math.sin(Math.PI * t);
            double off = amp * env * (0.65 * Math.sin(Math.PI * 2 * t + p1) + 0.35 * Math.sin(Math.PI * 2 * f2 * t + p2));
            px[i] = ax + dx * t + nx * off;
            pz[i] = az + dz * t + nz * off;
            long hv = Hashing.mix(h + 100 + i);
            w[i] = wBase * (0.62 + 0.76 * Hashing.unit(hv));
            hh[i] = hBase * (0.75 + 0.5 * Hashing.unit(Hashing.mix(hv)));
        }
        if (spur) { // alcove at the end
            w[k] *= 1.7;
            hh[k] *= 1.25;
        }
        return new Edge(px, pz, w, hh);
    }

    /** Adds passages and intermediate chambers at warped position (qx, qz) to the accumulator. */
    void evaluate(double qx, double qz, CavernGraph.Openness acc) {
        if (frequency <= 0) {
            return;
        }
        int bx = Math.floorDiv((int) Math.floor(qx), cell);
        int bz = Math.floorDiv((int) Math.floor(qz), cell);
        double height = acc.height;
        double tunnel = acc.tunnel;
        double chamber = acc.chamber;
        for (int dx = -2; dx <= 1; dx++) {
            for (int dz = -2; dz <= 1; dz++) {
                Cell c = cell(bx + dx, bz + dz);
                if (c.chamberR > 0 && dx >= -1 && dz >= -1) {
                    double ddx = qx - c.ax;
                    double ddz = qz - c.az;
                    if (Math.abs(ddx) < c.chamberR && Math.abs(ddz) < c.chamberR) {
                        double u = ddx * c.cos + ddz * c.sin;
                        double v = (-ddx * c.sin + ddz * c.cos) / c.axis;
                        double norm = Math.sqrt(u * u + v * v) / c.chamberR;
                        if (norm < 1) {
                            double s = 1 - norm * norm;
                            height = Math.max(height, c.chamberH * Math.min(1, Math.pow(s, 0.42) * 1.08));
                            chamber = Math.max(chamber, s * 0.6);
                        }
                    }
                }
                for (Edge e : c.edges) {
                    if (qx < e.minX || qx > e.maxX || qz < e.minZ || qz > e.maxZ) {
                        continue;
                    }
                    for (int i = 0; i < e.px.length - 1; i++) {
                        double ax = e.px[i], az = e.pz[i], bxx = e.px[i + 1], bzz = e.pz[i + 1];
                        double wmax = Math.max(e.w[i], e.w[i + 1]);
                        if (qx < Math.min(ax, bxx) - wmax || qx > Math.max(ax, bxx) + wmax
                                || qz < Math.min(az, bzz) - wmax || qz > Math.max(az, bzz) + wmax) {
                            continue;
                        }
                        double vx = bxx - ax, vz = bzz - az;
                        double len2 = vx * vx + vz * vz;
                        double t = len2 > 0 ? ((qx - ax) * vx + (qz - az) * vz) / len2 : 0;
                        t = Math.max(0, Math.min(1, t));
                        double ex = ax + vx * t - qx, ez = az + vz * t - qz;
                        double w = e.w[i] + (e.w[i + 1] - e.w[i]) * t;
                        double norm = Math.sqrt(ex * ex + ez * ez) / w;
                        if (norm < 1) {
                            double s = 1 - norm * norm;
                            double hh = e.h[i] + (e.h[i + 1] - e.h[i]) * t;
                            height = Math.max(height, hh * Math.min(1, Math.sqrt(s) * 1.3));
                            tunnel = Math.max(tunnel, s);
                        }
                    }
                }
            }
        }
        acc.height = height;
        acc.tunnel = tunnel;
        acc.chamber = chamber;
    }

    /** Anchors (and chamber flag) in a block rectangle of warped space, for diagnostics. */
    List<double[]> anchorsIn(int minX, int minZ, int maxX, int maxZ) {
        List<double[]> out = new ArrayList<>();
        for (int cx = Math.floorDiv(minX, cell); cx <= Math.floorDiv(maxX, cell); cx++) {
            for (int cz = Math.floorDiv(minZ, cell); cz <= Math.floorDiv(maxZ, cell); cz++) {
                Cell c = cell(cx, cz);
                out.add(new double[]{c.ax, c.az, c.chamberR, c.edges.length});
            }
        }
        return out;
    }
}
